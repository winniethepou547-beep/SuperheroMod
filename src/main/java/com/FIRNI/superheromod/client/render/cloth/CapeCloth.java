package com.FIRNI.superheromod.client.render.cloth;

import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A real cape: a grid of cloth points (COLS across, ROWS down) simulated per body in the WORLD, so it answers the body's
 * real movement: it trails behind a run, swings out on a turn and back when he stops, lifts when he falls, billows with
 * the air pressing on it, flutters at the hem. Position-based (Verlet): gravity, air (pressure along the cloth's normal,
 * a little skin drag, turbulence that grows with speed), damping; distance constraints across, down, diagonal (shear)
 * and skipping one (bend), a tether from each point to the top of its column so it never stretches; the top row pinned
 * to the shoulder line the body reports every frame; capsules for the torso, legs, arms and head that it drapes over and
 * never cuts through (the torso pushes it out behind, never in front), and the ground under it.
 * Fixed sub-steps of STEP ticks (the body's pins and capsules are interpolated across the frame), drawn interpolated
 * between the last two sub-steps and carried along with the body, so it is smooth at any frame rate.
 * <p>
 * Use: while drawing the body, record a {@link Frame} (pins, wing pins, feet, the facing, capsules) from the pose stack
 * (the points are taken wherever the stack puts them); then, back at the model root, call {@link #draw}. In a menu
 * (champion select stand-in, the inventory) there is no world motion: the cape is simulated in the model's own space
 * and just hangs.
 * <p>
 * The wing (Batman's glide): {@link Frame#spread} 0..1 moves the top row from the shoulders onto the arm line (wrist,
 * elbow, shoulder, back of the neck, shoulder, elbow, wrist), lengthens the cloth to reach from the wrists to the ankles,
 * and pulls the hem's corners to the ankles: a taut membrane whose trailing edge still flutters.
 */
public final class CapeCloth {
    public static final int COLS = 7, ROWS = 11, N = COLS * ROWS;
    public static final int MAX_CAPS = 12;
    /** One sub-step, in ticks. */
    static final double STEP = .25;
    /** Constraint iterations per sub-step. */
    static final int ITERATIONS = 4;

    // ------------------------------------------------------------------ the look
    /** How a cape looks and hangs. Sizes in blocks of the model (the body's scale is applied on top). */
    public static final class Style {
        /** Shoulder to hem, hanging. */
        public float length = 1.3f;
        /** The hem's width hanging (the top is as wide as the pins are apart). */
        public float bottomWidth = 1.05f;
        /** The hem's width in the wing (between the ankles). */
        public float wingBottom = .5f;
        /** The cloth's thickness (the inside is a separate darker face this far behind). */
        public float thickness = .03f;
        /** Heavier cloth flutters and lifts less (1 = a heavy cape). */
        public float weight = 1;
        /** In the wing: how far the cloth reaches past the arm line (1 = to the hands; more = a bigger wing) and how far
         *  apart its hem corners go compared with the ankles (1 = at the ankles). */
        public float wingReach = 1, wingFeet = 1;
        /** Depth of the bat-wing scallops along the hem (blocks; 0 = a straight hem). */
        public float scallop = 0;
        /** Depth of the vertical folds hanging (blocks; they pull out as the cloth goes taut). */
        public float folds = .035f;
        /** Outside, inside, and the edges. */
        public float[] outer = {.1f, .1f, .1f}, inner = {.05f, .05f, .05f}, edge = {.08f, .08f, .08f};
        public Style length(float l) { length = l; return this; }
        public Style bottom(float w) { bottomWidth = w; return this; }
        public Style wingBottom(float w) { wingBottom = w; return this; }
        public Style wingReach(float reach, float feet) { wingReach = reach; wingFeet = feet; return this; }
        public Style thickness(float t) { thickness = t; return this; }
        public Style weight(float w) { weight = w; return this; }
        public Style scallop(float s) { scallop = s; return this; }
        public Style folds(float f) { folds = f; return this; }
        public Style colours(float[] out, float[] in, float[] edges) { outer = out; inner = in; edge = edges; return this; }
    }

    // ------------------------------------------------------------------ what the body reports each frame
    /** Layout of a frame's numbers. */
    static final int PIN = 0, WING = PIN + COLS * 3, FOOT = WING + COLS * 3, FWD = FOOT + 6, CAPS = FWD + 3, IN = CAPS + MAX_CAPS * 7;

    /**
     * What the body tells its cape this frame, recorded while it is drawn: every point is taken from the pose stack as it
     * is at that moment (model pixels, so the same numbers as the body's boxes). Column 0 is his right, COLS-1 his left.
     */
    public static final class Frame {
        final float[] v = new float[IN];
        final boolean[] back = new boolean[MAX_CAPS];
        int caps;
        /** 0 = hanging from the shoulders, 1 = spread as a wing on the arms. */
        public float spread;
        boolean pins, wings, feet, facing;
        private final float[] marks = new float[12];
        private static final Vector4f T = new Vector4f();

        public void reset() { caps = 0; spread = 0; pins = wings = feet = facing = false; }
        private void put(PoseStack p, int at, float x, float y, float z) {
            T.set(x / 16, y / 16, z / 16, 1).mul(p.last().pose());
            v[at] = T.x; v[at + 1] = T.y; v[at + 2] = T.z;
        }
        /** The top of a column hanging (on the shoulder line). */
        public Frame pin(PoseStack p, int col, float x, float y, float z) { put(p, PIN + col * 3, x, y, z); pins = true; return this; }
        /** The top of a column in the wing (the arm line). */
        public Frame wing(PoseStack p, int col, float x, float y, float z) { put(p, WING + col * 3, x, y, z); wings = true; return this; }
        /** Where the hem's corner goes in the wing (side 0 right, 1 left): by the ankle. */
        public Frame foot(PoseStack p, int side, float x, float y, float z) { put(p, FOOT + side * 3, x, y, z); feet = true; return this; }
        /** The way his chest faces (its -z), from the chest's frame. */
        public Frame facing(PoseStack p) {
            T.set(0, 0, -1, 0).mul(p.last().pose());
            float l = (float) Math.sqrt(T.x * T.x + T.y * T.y + T.z * T.z);
            if (l < 1e-6f) return this;
            v[FWD] = T.x / l; v[FWD + 1] = T.y / l; v[FWD + 2] = T.z / l;
            facing = true;
            return this;
        }
        /** Remember a point (slot 0..3) to start a capsule from in another frame of the stack. */
        public Frame mark(int slot, PoseStack p, float x, float y, float z) {
            T.set(x / 16, y / 16, z / 16, 1).mul(p.last().pose());
            marks[slot * 3] = T.x; marks[slot * 3 + 1] = T.y; marks[slot * 3 + 2] = T.z;
            return this;
        }
        /** A capsule from a remembered point to a point here, radius in pixels; back: the cloth is always pushed out behind it. */
        public Frame capsule(int slot, PoseStack p, float x, float y, float z, float radius, boolean behind) {
            if (caps >= MAX_CAPS) return this;
            int at = CAPS + caps * 7;
            v[at] = marks[slot * 3]; v[at + 1] = marks[slot * 3 + 1]; v[at + 2] = marks[slot * 3 + 2];
            put(p, at + 3, x, y, z);
            v[at + 6] = radius / 16;
            back[caps++] = behind;
            return this;
        }
        /** A capsule between two points of the current frame of the stack. */
        public Frame capsule(PoseStack p, float ax, float ay, float az, float bx, float by, float bz, float radius, boolean behind) {
            mark(3, p, ax, ay, az);
            return capsule(3, p, bx, by, bz, radius, behind);
        }
    }

    // ------------------------------------------------------------------ the capes
    private static final Map<Integer, CapeCloth> CAPES = new HashMap<>();
    private static int calls;

    /** Drop every cape (logout, world change). */
    public static void clear() { CAPES.clear(); }
    /** Drop one body's capes (it changed hero, died...). */
    public static void forget(int entityId) { CAPES.remove(entityId); CAPES.remove(-1 - entityId); }

    // scratch (render thread only)
    private static final Matrix4f VIEW = new Matrix4f(), INV = new Matrix4f();
    private static final Matrix3f VIEW_N = new Matrix3f(), INV_ROT = new Matrix3f();
    private static final Vector4f V4 = new Vector4f();
    private static final Vector3f V3 = new Vector3f();
    private static final double[] SIM_IN = new double[IN];

    /**
     * Steps this body's cape up to now and draws it. The stack must be at the model root (where the body's draw started);
     * key tells capes apart (the entity id); partial is the frame's partial tick.
     */
    public static void draw(PoseStack p, MultiBufferSource b, int light, Entity e, float partial, int key, Frame f, Style s) {
        if (!f.pins || !f.facing) return;
        Matrix4f m0 = p.last().pose();
        // In the world, or in a menu? Where the model root lands in the world tells.
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        INV_ROT.set(RenderSystem.getInverseViewRotationMatrix());
        V4.set(0, 0, 0, 1).mul(m0);
        V3.set(V4.x, V4.y, V4.z);
        INV_ROT.transform(V3);
        Vec3 pos = e.getPosition(partial);
        double rx = cam.x + V3.x - pos.x, ry = cam.y + V3.y - pos.y - 1.4, rz = cam.z + V3.z - pos.z;
        boolean world = mc.level != null && rx * rx + ry * ry + rz * rz < 9;
        // The clock in double: a float game time loses the fractions of a tick in an old world.
        double now = (mc.level != null ? mc.level.getGameTime() : System.nanoTime() / 50_000_000L) + (double) partial;

        int id = world ? key : -1 - key;
        CapeCloth c = CAPES.get(id);
        if (c == null) { c = new CapeCloth(); CAPES.put(id, c); }
        if (++calls % 240 == 0) sweep(now);
        c.used = now;

        double scale;
        if (world) {
            // Render space -> world: the inverse view rotation, then the camera.
            for (int i = 0; i < IN; i += 3) {
                if (i >= CAPS) break;
                boolean dir = i == FWD;
                V3.set(f.v[i], f.v[i + 1], f.v[i + 2]);
                INV_ROT.transform(V3);
                SIM_IN[i] = V3.x + (dir ? 0 : cam.x); SIM_IN[i + 1] = V3.y + (dir ? 0 : cam.y); SIM_IN[i + 2] = V3.z + (dir ? 0 : cam.z);
            }
            for (int k = 0; k < f.caps; k++) {
                int at = CAPS + k * 7;
                for (int j = 0; j < 2; j++) {
                    V3.set(f.v[at + j * 3], f.v[at + j * 3 + 1], f.v[at + j * 3 + 2]);
                    INV_ROT.transform(V3);
                    SIM_IN[at + j * 3] = V3.x + cam.x; SIM_IN[at + j * 3 + 1] = V3.y + cam.y; SIM_IN[at + j * 3 + 2] = V3.z + cam.z;
                }
            }
            V4.set(1, 0, 0, 0).mul(m0);
            scale = Math.sqrt(V4.x * V4.x + V4.y * V4.y + V4.z * V4.z);
        } else {
            // Render space -> the model root's own space (blocks of the model, +y down): the inverse of the root matrix.
            INV.set(m0).invert();
            for (int i = 0; i < CAPS; i += 3) {
                V4.set(f.v[i], f.v[i + 1], f.v[i + 2], i == FWD ? 0 : 1).mul(INV);
                SIM_IN[i] = V4.x; SIM_IN[i + 1] = V4.y; SIM_IN[i + 2] = V4.z;
            }
            for (int k = 0; k < f.caps; k++) {
                int at = CAPS + k * 7;
                for (int j = 0; j < 2; j++) {
                    V4.set(f.v[at + j * 3], f.v[at + j * 3 + 1], f.v[at + j * 3 + 2], 1).mul(INV);
                    SIM_IN[at + j * 3] = V4.x; SIM_IN[at + j * 3 + 1] = V4.y; SIM_IN[at + j * 3 + 2] = V4.z;
                }
            }
            scale = 1;
        }
        double fl = Math.sqrt(SIM_IN[FWD] * SIM_IN[FWD] + SIM_IN[FWD + 1] * SIM_IN[FWD + 1] + SIM_IN[FWD + 2] * SIM_IN[FWD + 2]);
        if (fl > 1e-6) { SIM_IN[FWD] /= fl; SIM_IN[FWD + 1] /= fl; SIM_IN[FWD + 2] /= fl; }
        for (int k = 0; k < f.caps; k++) SIM_IN[CAPS + k * 7 + 6] = f.v[CAPS + k * 7 + 6] * scale;
        if (!f.wings) System.arraycopy(SIM_IN, PIN, SIM_IN, WING, COLS * 3);
        float spread = f.wings ? Math.max(0, Math.min(1, f.spread)) : 0;
        if (!f.feet) spread = Math.min(spread, 0);

        // Gravity and the ground, in the space it is simulated in.
        Sim sim = c.sim;
        if (world) {
            sim.gx = 0; sim.gy = -1; sim.gz = 0;
            sim.floor = e.onGround() ? -(pos.y + .015) : 1e9;
            double t = (now % 100000) * .004;
            sim.windX = .006 * Math.sin(t); sim.windY = 0; sim.windZ = .006 * Math.cos(t * .7);
        } else {
            sim.gx = 0; sim.gy = 1; sim.gz = 0;
            sim.floor = 1.5 - .01;
            sim.windX = sim.windY = sim.windZ = 0;
        }
        sim.scale = scale;
        sim.advance(SIM_IN, f.back, f.caps, spread, s, now);

        // Draw it: in the world through the view rotation (positions relative to the camera); in a menu through the root matrix.
        if (world) {
            VIEW_N.set(INV_ROT).invert();
            VIEW.identity().set(VIEW_N);
            c.render(b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), VIEW, VIEW_N, light, s, cam.x, cam.y, cam.z);
        } else {
            c.render(b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), m0, p.last().normal(), light, s, 0, 0, 0);
        }
    }

    private static void sweep(double now) {
        Iterator<Map.Entry<Integer, CapeCloth>> it = CAPES.entrySet().iterator();
        while (it.hasNext()) {
            double since = now - it.next().getValue().used;
            if (since > 100 || since < -100) it.remove();
        }
    }

    private final Sim sim = new Sim();
    private double used;

    // ------------------------------------------------------------------ drawing
    /** Sub-columns drawn per simulated column gap (the folds and the scallops live between the simulated points). */
    private static final int SUB = 4, DC = (COLS - 1) * SUB + 1, DN = DC * ROWS;
    private static final float[] RP = new float[N * 3], DP = new float[DN * 3], DNRM = new float[DN * 3], BASE_N = new float[DN * 3];

    private void render(VertexConsumer v, Matrix4f m, Matrix3f nm, int light, Style s, double ox, double oy, double oz) {
        // Where the points are this frame (between the last two sub-steps, carried along with the body).
        sim.interpolated(RP, ox, oy, oz);
        // The drawn grid: the simulated columns with SUB-1 more between each pair (linear across, smooth enough with the folds).
        for (int r = 0; r < ROWS; r++)
            for (int j = 0; j < DC; j++) {
                int c = Math.min(COLS - 2, j / SUB);
                float u = (j - c * SUB) / (float) SUB;
                int a = (r * COLS + c) * 3, bb = a + 3, d = (r * DC + j) * 3;
                for (int k = 0; k < 3; k++) DP[d + k] = RP[a + k] + (RP[bb + k] - RP[a + k]) * u;
            }
        normals(DP, BASE_N);
        // Which way is out (away from his back): the side the facing is not on.
        float fx = (float) sim.fx, fy = (float) sim.fy, fz = (float) sim.fz;
        double dot = 0;
        for (int i = 0; i < DN; i++) dot += BASE_N[i * 3] * fx + BASE_N[i * 3 + 1] * fy + BASE_N[i * 3 + 2] * fz;
        float flip = dot > 0 ? -1 : 1;
        // Folds: a gentle wave across, deeper toward the hem, pulled out as the cloth spreads; the hem's scallops.
        float spread = sim.spreadDrawn;
        float fold = (float) (s.folds * sim.scale) * (1 - .85f * spread);
        float rowLen = (float) (sim.lengthNow / (ROWS - 1));
        float scallop = (float) (s.scallop * sim.scale);
        for (int r = 0; r < ROWS; r++) {
            float f = r / (float) (ROWS - 1);
            float depth = fold * (.25f + .75f * f);
            for (int j = 0; j < DC; j++) {
                int d = (r * DC + j) * 3;
                float u = j / (float) (DC - 1);
                float w = depth * (float) Math.cos(u * Math.PI * 2 * 3.5 + f * .8) * (j == 0 || j == DC - 1 ? .3f : 1);
                if (r > 0) for (int k = 0; k < 3; k++) DP[d + k] += BASE_N[d + k] * flip * w;
                if (r == ROWS - 1 && scallop > 0 && rowLen > 1e-4f) {
                    float local = (j % SUB) / (float) SUB;
                    float raise = Math.min(.85f, scallop / rowLen) * (float) Math.sin(local * Math.PI);
                    int up = ((r - 1) * DC + j) * 3;
                    for (int k = 0; k < 3; k++) DP[d + k] += (DP[up + k] - DP[d + k]) * raise;
                }
            }
        }
        normals(DP, DNRM);
        float half = (float) (s.thickness * sim.scale) * .5f;
        float[] co = s.outer, ci = s.inner, ce = s.edge;
        // Both faces: the outside (lit, outward normal) and the inside (darker, facing his back).
        for (int r = 0; r < ROWS - 1; r++)
            for (int j = 0; j < DC - 1; j++) {
                int a = r * DC + j, bq = a + 1, cq = a + DC + 1, dq = a + DC;
                // A touch darker high up under the shoulders and in the folds' troughs.
                float shade = .82f + .18f * Math.min(1, r / 2f);
                quad(v, m, nm, light, a, bq, cq, dq, flip * half, flip, co[0] * shade, co[1] * shade, co[2] * shade);
                quad(v, m, nm, light, a, dq, cq, bq, -flip * half, -flip, ci[0] * shade, ci[1] * shade, ci[2] * shade);
            }
        // The edges: down both sides and along the hem, a band as thick as the cloth.
        for (int r = 0; r < ROWS - 1; r++) {
            rim(v, m, nm, light, r * DC, (r + 1) * DC, half * flip, ce, -1);
            rim(v, m, nm, light, (r + 1) * DC + DC - 1, r * DC + DC - 1, half * flip, ce, 1);
        }
        for (int j = 0; j < DC - 1; j++) rim(v, m, nm, light, (ROWS - 1) * DC + j + 1, (ROWS - 1) * DC + j, half * flip, ce, 0);
    }
    /** Normals of the drawn grid from its neighbours (unit, unoriented). */
    private static void normals(float[] p, float[] out) {
        for (int r = 0; r < ROWS; r++)
            for (int j = 0; j < DC; j++) {
                int l = (r * DC + Math.max(0, j - 1)) * 3, rr = (r * DC + Math.min(DC - 1, j + 1)) * 3;
                int u = (Math.max(0, r - 1) * DC + j) * 3, d = (Math.min(ROWS - 1, r + 1) * DC + j) * 3;
                float ax = p[rr] - p[l], ay = p[rr + 1] - p[l + 1], az = p[rr + 2] - p[l + 2];
                float bx = p[d] - p[u], by = p[d + 1] - p[u + 1], bz = p[d + 2] - p[u + 2];
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                int o = (r * DC + j) * 3;
                if (len < 1e-9f) { out[o] = 0; out[o + 1] = 0; out[o + 2] = 1; continue; }
                out[o] = nx / len; out[o + 1] = ny / len; out[o + 2] = nz / len;
            }
    }
    private static void quad(VertexConsumer v, Matrix4f m, Matrix3f nm, int light, int a, int b, int c, int d, float off, float nsign, float r, float g, float bl) {
        vertex(v, m, nm, light, a, off, nsign, r, g, bl);
        vertex(v, m, nm, light, b, off, nsign, r, g, bl);
        vertex(v, m, nm, light, c, off, nsign, r, g, bl);
        vertex(v, m, nm, light, d, off, nsign, r, g, bl);
    }
    private static void vertex(VertexConsumer v, Matrix4f m, Matrix3f nm, int light, int i, float off, float nsign, float r, float g, float b) {
        int o = i * 3;
        float nx = DNRM[o] * nsign, ny = DNRM[o + 1] * nsign, nz = DNRM[o + 2] * nsign;
        v.vertex(m, DP[o] + DNRM[o] * off, DP[o + 1] + DNRM[o + 1] * off, DP[o + 2] + DNRM[o + 2] * off)
                .color(Math.min(1, r), Math.min(1, g), Math.min(1, b), 1f).uv(.5f, .5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(nm, nx, ny, nz).endVertex();
    }
    private static void rimVertex(VertexConsumer v, Matrix4f m, Matrix3f nm, int light, int o, float off, float[] c, float ex, float ey, float ez) {
        v.vertex(m, DP[o] + DNRM[o] * off, DP[o + 1] + DNRM[o + 1] * off, DP[o + 2] + DNRM[o + 2] * off)
                .color(Math.min(1, c[0]), Math.min(1, c[1]), Math.min(1, c[2]), 1f).uv(.5f, .5f)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, ex, ey, ez).endVertex();
    }
    /** A band of the edge from point i to point j (outside to inside), its normal along the cloth outward. */
    private static void rim(VertexConsumer v, Matrix4f m, Matrix3f nm, int light, int i, int j, float half, float[] c, int kind) {
        int oi = i * 3, oj = j * 3;
        // Outward along the cloth: across the edge (sides) or down (hem).
        float ex, ey, ez;
        if (kind == 0) {
            int up = oi - DC * 3;
            ex = DP[oi] - DP[up]; ey = DP[oi + 1] - DP[up + 1]; ez = DP[oi + 2] - DP[up + 2];
        } else {
            int in = oi - kind * 3;
            ex = DP[oi] - DP[in]; ey = DP[oi + 1] - DP[in + 1]; ez = DP[oi + 2] - DP[in + 2];
        }
        float l = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (l < 1e-9f) return;
        ex /= l; ey /= l; ez /= l;
        rimVertex(v, m, nm, light, oi, half, c, ex, ey, ez);
        rimVertex(v, m, nm, light, oj, half, c, ex, ey, ez);
        rimVertex(v, m, nm, light, oj, -half, c, ex, ey, ez);
        rimVertex(v, m, nm, light, oi, -half, c, ex, ey, ez);
    }

    // ---- SIM BEGIN (plain Java: the solver, kept free of the game's classes so it can be checked on its own)
    /**
     * The cloth itself. Space-agnostic: positions in blocks in whatever space the inputs are (the world, or a menu
     * model's own space), gravity along (gx, gy, gz), the ground a plane: point . g <= floor... written as -(point . up).
     */
    static final class Sim {
        /** Positions now and one sub-step ago (the Verlet velocity and the drawing's interpolation). */
        final double[] x = new double[N * 3], o = new double[N * 3];
        /** The inputs at the last frame and this frame; the ones a sub-step uses (interpolated between them). */
        final double[] before = new double[IN], after = new double[IN], in = new double[IN];
        final boolean[] back = new boolean[MAX_CAPS];
        int caps;
        float spreadBefore, spreadAfter, spread, spreadDrawn;
        double gx, gy = -1, gz, floor = 1e9, windX, windY, windZ, scale = 1;
        double fx, fy, fz;
        double acc;
        double last = Double.NaN;
        boolean fresh = true;
        /** The middle of the pins at the last two sub-steps and this frame (to carry the drawn cloth along with the body). */
        double mox, moy, moz, mx, my, mz, mnx, mny, mnz;
        double lengthNow = 1.3;
        // per-step scratch
        private final double[] pins = new double[COLS * 3], restH = new double[ROWS], restV = new double[COLS], nrm = new double[N * 3];

        /** Gravity in blocks/tick^2 (a heavy cape at Minecraft speeds); quadratic air pressure; skin drag; damping per sub-step. */
        static final double GRAVITY = .045, PRESSURE = .85, SKIN = .06, DAMP = .992, MAX_MOVE = .6;

        /** Moves the cloth on to `now` with this frame's inputs. */
        void advance(double[] input, boolean[] behind, int count, float spreadNow, Style s, double now) {
            System.arraycopy(input, 0, after, 0, IN);
            System.arraycopy(behind, 0, back, 0, MAX_CAPS);
            caps = count;
            spreadAfter = spreadNow;
            fx = after[FWD]; fy = after[FWD + 1]; fz = after[FWD + 2];
            mnx = mnz = mny = 0;
            for (int c = 0; c < COLS; c++) {
                mnx += lerp(after[PIN + c * 3], after[WING + c * 3], spreadNow) / COLS;
                mny += lerp(after[PIN + c * 3 + 1], after[WING + c * 3 + 1], spreadNow) / COLS;
                mnz += lerp(after[PIN + c * 3 + 2], after[WING + c * 3 + 2], spreadNow) / COLS;
            }
            double dt = Double.isNaN(last) ? 0 : now - last;
            boolean jump = !fresh && (sq(mnx - mx, mny - my, mnz - mz) > sq(2.5 * scale, 0, 0));
            if (fresh || jump || dt > 20 || dt < -1 || bad()) {
                hang(s);
                last = now;
                return;
            }
            last = now;
            if (dt <= 0) { spreadDrawn = spreadAfter; return; }
            dt = Math.min(dt, 2.5);
            acc += dt;
            int steps = 0;
            while (acc >= STEP && steps < 12) {
                acc -= STEP;
                steps++;
                double k = Math.max(0, Math.min(1, 1 - acc / dt));
                for (int i = 0; i < IN; i++) in[i] = before[i] + (after[i] - before[i]) * k;
                spread = (float) (spreadBefore + (spreadAfter - spreadBefore) * k);
                step(s, now - acc);
            }
            if (acc > STEP) acc = STEP * .999;
            System.arraycopy(after, 0, before, 0, IN);
            spreadBefore = spreadAfter;
            spreadDrawn = spreadAfter;
        }
        private boolean bad() {
            for (int i = 0; i < N * 3; i++) if (!Double.isFinite(x[i])) return true;
            return false;
        }
        /** Starts over: hanging straight down from the pins, at rest. */
        void hang(Style s) {
            System.arraycopy(after, 0, before, 0, IN);
            System.arraycopy(after, 0, in, 0, IN);
            spread = spreadBefore = spreadDrawn = spreadAfter;
            rest(s);
            double gl = Math.sqrt(sq(gx, gy, gz));
            double dx = gx / gl, dy = gy / gl, dz = gz / gl;
            // A little behind him so the capsules push it the right way.
            double bx = -fx * .04, by = -fy * .04, bz = -fz * .04;
            for (int c = 0; c < COLS; c++) {
                double y = 0;
                for (int r = 0; r < ROWS; r++) {
                    int i = (r * COLS + c) * 3;
                    x[i] = pins[c * 3] + dx * y + bx * r; x[i + 1] = pins[c * 3 + 1] + dy * y + by * r; x[i + 2] = pins[c * 3 + 2] + dz * y + bz * r;
                    y += restV[c];
                }
            }
            for (int it = 0; it < 6; it++) collide();
            System.arraycopy(x, 0, o, 0, N * 3);
            mox = mx = mnx; moy = my = mny; moz = mz = mnz;
            acc = 0;
            fresh = false;
        }
        /** The pins for the current inputs, and the rest lengths across each row and down each column. */
        private void rest(Style s) {
            reach = s.wingReach; feetApart = s.wingFeet;
            for (int c = 0; c < COLS; c++)
                for (int k = 0; k < 3; k++) pins[c * 3 + k] = lerp(in[PIN + c * 3 + k], wing(c, k), spread);
            double span = 0;
            for (int c = 0; c < COLS - 1; c++) span += Math.sqrt(sq(pins[c * 3 + 3] - pins[c * 3], pins[c * 3 + 4] - pins[c * 3 + 1], pins[c * 3 + 5] - pins[c * 3 + 2]));
            double hem = lerp(s.bottomWidth, s.wingBottom, spread) * scale;
            for (int r = 0; r < ROWS; r++) {
                double f = r / (double) (ROWS - 1);
                restH[r] = (span + (hem - span) * Math.pow(f, spread > .5 ? 1 : .8)) / (COLS - 1);
            }
            double hang = s.length * scale;
            lengthNow = hang;
            if (spread > 1e-3) {
                // In the wing: long enough to reach from each point of the arm line to the ankles (a little slack).
                double ax = (in[FOOT] + in[FOOT + 3]) / 2, ay = (in[FOOT + 1] + in[FOOT + 4]) / 2, az = (in[FOOT + 2] + in[FOOT + 5]) / 2;
                double centre = Math.sqrt(sq(wing(3, 0) - ax, wing(3, 1) - ay, wing(3, 2) - az)) * 1.03;
                double right = Math.sqrt(sq(wing(0, 0) - foot(0, 0), wing(0, 1) - foot(0, 1), wing(0, 2) - foot(0, 2)));
                double left = Math.sqrt(sq(wing(6, 0) - foot(1, 0), wing(6, 1) - foot(1, 1), wing(6, 2) - foot(1, 2)));
                double side = (right + left) / 2 * 1.04;
                for (int c = 0; c < COLS; c++) {
                    double e = Math.abs(c - (COLS - 1) / 2.0) / ((COLS - 1) / 2.0);
                    double wing = centre + (side - centre) * e * e;
                    restV[c] = lerp(hang, wing, spread) / (ROWS - 1);
                }
                lengthNow = lerp(hang, centre, spread);
            } else for (int c = 0; c < COLS; c++) restV[c] = hang / (ROWS - 1);
        }

        /** One sub-step at clock t (ticks). */
        void step(Style s, double clock) {
            double h = STEP, t = clock % 4096;
            rest(s);
            double gl = Math.sqrt(sq(gx, gy, gz));
            double gax = gx / gl * GRAVITY, gay = gy / gl * GRAVITY, gaz = gz / gl * GRAVITY;
            double heavy = 1 / Math.max(.2, s.weight);
            // The body's speed (from the pins' middle): the turbulence grows with it.
            double cx = 0, cy = 0, cz = 0;
            for (int c = 0; c < COLS; c++) { cx += pins[c * 3] / COLS; cy += pins[c * 3 + 1] / COLS; cz += pins[c * 3 + 2] / COLS; }
            double bodySpeed = Math.sqrt(sq(cx - mx, cy - my, cz - mz)) / h;
            mox = mx; moy = my; moz = mz;
            mx = cx; my = cy; mz = cz;
            normals();
            for (int r = 1; r < ROWS; r++)
                for (int c = 0; c < COLS; c++) {
                    int i = (r * COLS + c) * 3;
                    double vx = (x[i] - o[i]) / h, vy = (x[i + 1] - o[i + 1]) / h, vz = (x[i + 2] - o[i + 2]) / h;
                    // The air against the cloth: still air (and a breeze) less the cloth's own speed, stirred by turbulence.
                    double edge = .35 + .65 * r / (ROWS - 1.0);
                    double amp = (.22 * bodySpeed + .006) * edge * heavy;
                    double ax = windX - vx + amp * Math.sin(t * .83 + c * 1.71 + r * .63),
                            ay = windY - vy + amp * Math.sin(t * 1.27 + r * 1.13 + c * .37 + 2.1),
                            az = windZ - vz + amp * Math.sin(t * .71 + c * .83 - r * .91 + 4.3);
                    double nx = nrm[i], ny = nrm[i + 1], nz = nrm[i + 2];
                    double vn = ax * nx + ay * ny + az * nz;
                    double press = PRESSURE * heavy * Math.abs(vn) * vn;
                    double fxa = gax + nx * press + SKIN * heavy * (ax - vn * nx);
                    double fya = gay + ny * press + SKIN * heavy * (ay - vn * ny);
                    double fza = gaz + nz * press + SKIN * heavy * (az - vn * nz);
                    double mvx = (x[i] - o[i]) * DAMP, mvy = (x[i + 1] - o[i + 1]) * DAMP, mvz = (x[i + 2] - o[i + 2]) * DAMP;
                    double ml = Math.sqrt(sq(mvx, mvy, mvz));
                    if (ml > MAX_MOVE * scale) { double k = MAX_MOVE * scale / ml; mvx *= k; mvy *= k; mvz *= k; }
                    o[i] = x[i]; o[i + 1] = x[i + 1]; o[i + 2] = x[i + 2];
                    x[i] += mvx + fxa * h * h; x[i + 1] += mvy + fya * h * h; x[i + 2] += mvz + fza * h * h;
                }
            // The top row goes where the body puts it.
            for (int c = 0; c < COLS; c++) {
                int i = c * 3;
                o[i] = x[i]; o[i + 1] = x[i + 1]; o[i + 2] = x[i + 2];
                x[i] = pins[c * 3]; x[i + 1] = pins[c * 3 + 1]; x[i + 2] = pins[c * 3 + 2];
            }
            for (int it = 0; it < ITERATIONS; it++) {
                constraints();
                tethers();
                if (spread > 1e-3) wingCorners();
                behind();
                collide();
            }
        }
        /** Per-point normals of the simulated grid (for the air). */
        private void normals() {
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++) {
                    int l = (r * COLS + Math.max(0, c - 1)) * 3, rr = (r * COLS + Math.min(COLS - 1, c + 1)) * 3;
                    int u = (Math.max(0, r - 1) * COLS + c) * 3, d = (Math.min(ROWS - 1, r + 1) * COLS + c) * 3;
                    double ax = x[rr] - x[l], ay = x[rr + 1] - x[l + 1], az = x[rr + 2] - x[l + 2];
                    double bx = x[d] - x[u], by = x[d + 1] - x[u + 1], bz = x[d + 2] - x[u + 2];
                    double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                    double len = Math.sqrt(sq(nx, ny, nz));
                    int i = (r * COLS + c) * 3;
                    if (len < 1e-12) { nrm[i] = nrm[i + 1] = nrm[i + 2] = 0; continue; }
                    nrm[i] = nx / len; nrm[i + 1] = ny / len; nrm[i + 2] = nz / len;
                }
        }
        private void constraints() {
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++) {
                    int i = r * COLS + c;
                    if (c < COLS - 1) link(i, i + 1, restH[r], 1);
                    if (r < ROWS - 1) {
                        link(i, i + COLS, restV[c], 1);
                        if (c < COLS - 1) {
                            double hh = (restH[r] + restH[r + 1]) * .5;
                            double d1 = Math.sqrt(hh * hh + restV[c] * restV[c]), d2 = Math.sqrt(hh * hh + restV[c + 1] * restV[c + 1]);
                            link(i, i + COLS + 1, d1, .55);
                            link(i + 1, i + COLS, d2, .55);
                        }
                    }
                    if (c < COLS - 2) link(i, i + 2, restH[r] * 2, .22);
                    if (r < ROWS - 2) link(i, i + 2 * COLS, restV[c] * 2, .22);
                }
        }
        /** Keeps two points rest apart (stiffness k); a point of the top row does not move. */
        private void link(int a, int b, double rest, double k) {
            int ia = a * 3, ib = b * 3;
            double dx = x[ib] - x[ia], dy = x[ib + 1] - x[ia + 1], dz = x[ib + 2] - x[ia + 2];
            double len = Math.sqrt(sq(dx, dy, dz));
            if (len < 1e-9) return;
            double diff = (len - rest) / len * k;
            boolean fa = a >= COLS, fb = b >= COLS;
            if (fa && fb) {
                diff *= .5;
                x[ia] += dx * diff; x[ia + 1] += dy * diff; x[ia + 2] += dz * diff;
                x[ib] -= dx * diff; x[ib + 1] -= dy * diff; x[ib + 2] -= dz * diff;
            } else if (fa) {
                x[ia] += dx * diff; x[ia + 1] += dy * diff; x[ia + 2] += dz * diff;
            } else if (fb) {
                x[ib] -= dx * diff; x[ib + 1] -= dy * diff; x[ib + 2] -= dz * diff;
            }
        }
        /** No point further from the top of its column than the cloth between them is long. */
        private void tethers() {
            for (int c = 0; c < COLS; c++) {
                double px = x[c * 3], py = x[c * 3 + 1], pz = x[c * 3 + 2];
                for (int r = 1; r < ROWS; r++) {
                    int i = (r * COLS + c) * 3;
                    double max = restV[c] * r * 1.02;
                    double dx = x[i] - px, dy = x[i + 1] - py, dz = x[i + 2] - pz;
                    double len = Math.sqrt(sq(dx, dy, dz));
                    if (len > max && len > 1e-9) {
                        double k = max / len;
                        x[i] = px + dx * k; x[i + 1] = py + dy * k; x[i + 2] = pz + dz * k;
                    }
                }
            }
        }
        /** In the wing the hem's corners are drawn to the ankles (and its middle a little), so the membrane is taut. */
        private void wingCorners() {
            pull((ROWS - 1) * COLS, foot(0, 0), foot(0, 1), foot(0, 2), .45 * spread);
            pull((ROWS - 1) * COLS + COLS - 1, foot(1, 0), foot(1, 1), foot(1, 2), .45 * spread);
            pull((ROWS - 1) * COLS + COLS / 2, (in[FOOT] + in[FOOT + 3]) / 2, (in[FOOT + 1] + in[FOOT + 4]) / 2, (in[FOOT + 2] + in[FOOT + 5]) / 2, .12 * spread);
        }
        /** The wing's top of a column (coordinate k): the arm line, stretched out from its middle by the style's reach. */
        private double wing(int col, int k) {
            double mid = in[WING + 9 + k];
            return mid + (in[WING + col * 3 + k] - mid) * reach;
        }
        /** Where a hem corner goes in the wing (side 0 right, 1 left; coordinate k): the ankle, spread out by the style. */
        private double foot(int side, int k) {
            double mid = (in[FOOT + k] + in[FOOT + 3 + k]) / 2;
            return mid + (in[FOOT + side * 3 + k] - mid) * feetApart;
        }
        private double reach = 1, feetApart = 1;
        private void pull(int p, double tx, double ty, double tz, double k) {
            int i = p * 3;
            x[i] += (tx - x[i]) * k; x[i + 1] += (ty - x[i + 1]) * k; x[i + 2] += (tz - x[i + 2]) * k;
        }
        /** The upper part of the cape stays behind the plane of his back (it never folds forward over the shoulders). */
        private void behind() {
            double px = 0, py = 0, pz = 0;
            for (int c = 0; c < COLS; c++) { px += x[c * 3] / COLS; py += x[c * 3 + 1] / COLS; pz += x[c * 3 + 2] / COLS; }
            double margin = (.02 + .5 * spread) * scale;
            for (int r = 1; r < 6; r++)
                for (int c = 0; c < COLS; c++) {
                    int i = (r * COLS + c) * 3;
                    double d = (x[i] - px) * fx + (x[i + 1] - py) * fy + (x[i + 2] - pz) * fz;
                    double allow = margin + .04 * scale * (r - 1);
                    if (d > allow) { double k = d - allow; x[i] -= fx * k; x[i + 1] -= fy * k; x[i + 2] -= fz * k; }
                }
        }
        /** Out of the body's capsules (the torso pushes it out behind), and above the ground. */
        void collide() {
            double thick = .025 * scale;
            double gl = Math.sqrt(sq(gx, gy, gz));
            double ux = -gx / gl, uy = -gy / gl, uz = -gz / gl;
            for (int p = COLS; p < N; p++) {
                int i = p * 3;
                for (int k = 0; k < caps; k++) {
                    int at = CAPS + k * 7;
                    double ax = in[at], ay = in[at + 1], az = in[at + 2], bx = in[at + 3], by = in[at + 4], bz = in[at + 5];
                    double rad = in[at + 6] + thick;
                    double sx = bx - ax, sy = by - ay, sz = bz - az;
                    double ss = sq(sx, sy, sz);
                    double u = ss < 1e-12 ? 0 : ((x[i] - ax) * sx + (x[i + 1] - ay) * sy + (x[i + 2] - az) * sz) / ss;
                    u = Math.max(0, Math.min(1, u));
                    double qx = ax + sx * u, qy = ay + sy * u, qz = az + sz * u;
                    double dx = x[i] - qx, dy = x[i + 1] - qy, dz = x[i + 2] - qz;
                    double d2 = sq(dx, dy, dz);
                    if (d2 >= rad * rad) continue;
                    double d = Math.sqrt(d2);
                    if (d < 1e-6) { dx = -fx; dy = -fy; dz = -fz; d = 1; }
                    dx /= d; dy /= d; dz /= d;
                    if (back[k]) {
                        // Never out the front of the torso: a point that got in front is sent round to the back.
                        double ahead = dx * fx + dy * fy + dz * fz;
                        if (ahead > 0) { dx -= 2 * ahead * fx; dy -= 2 * ahead * fy; dz -= 2 * ahead * fz; }
                    }
                    x[i] = qx + dx * rad; x[i + 1] = qy + dy * rad; x[i + 2] = qz + dz * rad;
                }
                // The ground: the height along "up" never below the floor.
                double hgt = x[i] * ux + x[i + 1] * uy + x[i + 2] * uz;
                double min = -floor;
                if (floor < 1e8 && hgt < min) {
                    double k = min - hgt;
                    x[i] += ux * k; x[i + 1] += uy * k; x[i + 2] += uz * k;
                    // Friction on the ground.
                    o[i] += (x[i] - o[i]) * .5; o[i + 1] += (x[i + 1] - o[i + 1]) * .5; o[i + 2] += (x[i + 2] - o[i + 2]) * .5;
                }
            }
        }
        /**
         * The points as drawn now (relative to an origin): between the last two sub-steps, moved along by how far the
         * body got since then, the top row exactly on this frame's pins.
         */
        void interpolated(float[] out, double ox, double oy, double oz) {
            double a = Math.max(0, Math.min(1, acc / STEP));
            double dx = mnx - (mox + (mx - mox) * a), dy = mny - (moy + (my - moy) * a), dz = mnz - (moz + (mz - moz) * a);
            for (int p = 0; p < N; p++) {
                int i = p * 3;
                if (p < COLS) {
                    out[i] = (float) (lerp(after[PIN + i], after[WING + i], spreadAfter) - ox);
                    out[i + 1] = (float) (lerp(after[PIN + i + 1], after[WING + i + 1], spreadAfter) - oy);
                    out[i + 2] = (float) (lerp(after[PIN + i + 2], after[WING + i + 2], spreadAfter) - oz);
                    continue;
                }
                out[i] = (float) (o[i] + (x[i] - o[i]) * a + dx - ox);
                out[i + 1] = (float) (o[i + 1] + (x[i + 1] - o[i + 1]) * a + dy - oy);
                out[i + 2] = (float) (o[i + 2] + (x[i + 2] - o[i + 2]) * a + dz - oz);
            }
        }
        static double lerp(double a, double b, double k) { return a + (b - a) * k; }
        static double sq(double x, double y, double z) { return x * x + y * y + z * z; }
    }
    // ---- SIM END
}
