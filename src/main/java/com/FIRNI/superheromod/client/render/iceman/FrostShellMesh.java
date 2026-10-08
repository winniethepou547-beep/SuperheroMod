package com.FIRNI.superheromod.client.render.iceman;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The deep freeze's shell as geometry (no game state, so the offline preview draws it too): built from the body's pose
 * (its parts' boxes and matrices), grown vertex by vertex, drawn with Iceman's ice; the crack network over it; the
 * chunks it breaks into and their flight. FrostShell runs it in the game (when, the pose, particles, sounds); the look
 * is described there.
 */
public final class FrostShellMesh {
    private FrostShellMesh() {}

    /** Points round a ring, most rings along a part. */
    static final int RN = 8, MAXR = 5, PARTS = 6;
    /** Ticks a vertex takes to grow once the freeze reaches it; the speed (blocks a tick) it spreads from the contact point. */
    static final float GROW = 5, SPREAD = .17f;

    /*
     * The shell's materials: milky pale ice with clear blue pockets you can see a little into (the body shows through on
     * the chest and arms), heavier milky ice on the head, back and legs.
     */
    static final IceMesh.Mat SHELL = new IceMesh.Mat(.70f, .85f, .97f, .6f, .97f, .04f, .9f, .9f),
            THICK = new IceMesh.Mat(.64f, .8f, .96f, .8f, 1f, .06f, .85f, .85f),
            CHEST = new IceMesh.Mat(.74f, .87f, .98f, .48f, .95f, .03f, .95f, 1f),
            SPINE = new IceMesh.Mat(.76f, .9f, 1f, .78f, 1f, .05f, 1f, 1.1f);


    // ------------------------------------------------------------------ one frozen body
    static final class Shell {
        final int entity; final boolean human;
        Vec3 feet; final float width, height, start; final int total;
        /** The way the cold travelled (world, horizontal, unit): the ice starts on the side it came from. */
        final Vec3 way;
        float broke = -1; int how; Vec3 blow = Vec3.ZERO;
        // The held pose (degrees / radians as the model has them) and how far it follows the real one.
        final float[] held = new float[18]; boolean heldSet; float heldYaw = Float.NaN, lastPose = -1;
        // This frame's shell (feet-relative): per part, rings * RN points on the skin (base), at full growth (outer),
        // their growth delay (ticks) and the share of growth they have now.
        final int[] rings = new int[PARTS];
        final float[][] base = new float[PARTS][MAXR * RN * 3], outer = new float[PARTS][MAXR * RN * 3], nrm = new float[PARTS][MAXR * RN * 3];
        final float[][] delay = new float[PARTS][MAXR * RN], grown = new float[PARTS][MAXR * RN];
        /** Each part's end points (start cap, far cap) and axis (feet-relative). */
        final float[][] ends = new float[PARTS][9];
        boolean built;
        // The cracks (once they start) and the chunks (once it breaks).
        Cracks cracks; float crackStart = -1;
        List<Chunk> chunks; boolean landedHeard, hissed;
        final Random rng;
        Shell(int entity, boolean human, Vec3 feet, float width, float height, float start, int total, Vec3 way) {
            this.entity = entity; this.human = human; this.feet = feet; this.width = width; this.height = height;
            this.start = start; this.total = total; this.way = way; rng = new Random(entity * 7919L + (long) start);
        }
        float age(float now) { return now - start; }
        /** 0..1: how far the ice has taken hold (1 = locked). */
        float seize(float now) { return Mth.clamp(age(now) / DEEP_SEIZE, 0, 1); }
    }
    /** The point on the body the cold reaches first (feet-relative). */
    static Vec3 contact(Shell s) { return new Vec3(0, s.height * .58, 0).subtract(s.way.scale(s.width * .5)); }

    // ------------------------------------------------------------------ the shell's shape
    /** Humanoid parts: along the part from..to (model pixels, y down), ring places (0..1) and thickness (pixels) at each. */
    private static final float[][] H_RING = {{0, .48f, 1}, {0, .3f, .66f, 1}, {0, .3f, .55f, .8f, 1}, {0, .3f, .55f, .8f, 1}, {0, .32f, .58f, .8f, 1}, {0, .32f, .58f, .8f, 1}};
    private static final float[][] H_THICK = {{1.3f, 2.3f, 2.1f}, {2.4f, 1.5f, 1.4f, 1.9f}, {2.1f, 1.2f, .95f, 1.05f, 1.35f}, {2.1f, 1.2f, .95f, 1.05f, 1.35f}, {1.4f, 1.6f, 2f, 2.4f, 2.9f}, {1.4f, 1.6f, 2f, 2.4f, 2.9f}};
    private static final float[][] H_SPAN = {{0, -8}, {0, 12}, {-2, 10}, {-2, 10}, {0, 12}, {0, 12}};
    private static final float[] B_RING = {0, .34f, .67f, 1}, B_THICK = {1.6f, 1.5f, 1.6f, 2f};
    private static final Vector3f V = new Vector3f(), W = new Vector3f();
    private static final float[] PT = new float[3], NT = new float[3];

    /**
     * Builds the shell from the body's pose: each part's box (6 floats, its own space) and its matrix to feet-relative
     * world space (FrostBodies.parts: model pixels for a humanoid, blocks for the box layout), h = the body's height,
     * age = ticks since the freeze began.
     */
    static void build(Shell s, Matrix4f[] part, float[][] boxes, float h, float age) {
        float px = s.human ? 1 : Mth.clamp(h / 1.8f, .4f, 2.5f) / 16f;
        Vec3 c0 = contact(s);
        for (int p = 0; p < PARTS; p++) {
            float[] box = boxes[p];
            Matrix4f m = part[p];
            int axis; float from, to; float[] rp, th;
            if (s.human) { axis = 1; from = H_SPAN[p][0]; to = H_SPAN[p][1]; rp = H_RING[p]; th = H_THICK[p]; }
            else {
                float dx = box[3] - box[0], dy = box[4] - box[1], dz = box[5] - box[2];
                axis = dy >= dx && dy >= dz ? 1 : dx >= dz ? 0 : 2;
                from = box[axis]; to = box[axis + 3]; rp = B_RING; th = B_THICK;
            }
            int b = axis == 0 ? 1 : 0, c = axis == 2 ? 1 : 2;
            float cb = (box[b] + box[b + 3]) * .5f, cc = (box[c] + box[c + 3]) * .5f, hb = (box[b + 3] - box[b]) * .5f, hc = (box[c + 3] - box[c]) * .5f;
            int K = rp.length;
            s.rings[p] = K;
            for (int j = 0; j < K; j++) {
                float unit = s.human ? 1 : px;
                float along = Mth.lerp(rp[j], from, to) + (j > 0 && j < K - 1 ? (IceGrowth.h(s.entity, p * 97 + j) - .5f) * 1.2f * unit : 0);
                float turn = (IceGrowth.h(s.entity, p * 131 + j * 7) - .5f) * .6f;
                float tipK = j == 0 ? -1 : j == K - 1 ? 1 : 0;
                for (int i = 0; i < RN; i++) {
                    int v = j * RN + i, seed = s.entity * 31 + p * 1009 + v * 17;
                    float a = turn + Mth.TWO_PI * i / RN + (IceGrowth.h(seed, 1) - .5f) * .5f;
                    float db = Mth.cos(a), dc = Mth.sin(a);
                    float sc = Math.min(hb / Math.max(1e-4f, Math.abs(db)), hc / Math.max(1e-4f, Math.abs(dc)));
                    // On the skin (a hair inside it), and the way out: the round direction leaning to the box face.
                    // Every point a little up or down its limb (facets break into uneven shapes, never flat bands).
                    float stagger = j > 0 && j < K - 1 ? (IceGrowth.h(seed, 6) - .5f) * 1.8f * unit : 0;
                    PT[axis] = along + stagger; PT[b] = cb + db * sc * .97f; PT[c] = cc + dc * sc * .97f;
                    boolean faceB = hb / Math.max(1e-4f, Math.abs(db)) < hc / Math.max(1e-4f, Math.abs(dc));
                    NT[axis] = tipK * .45f * Math.signum(to - from); NT[b] = db + (faceB ? Math.signum(db) : 0); NT[c] = dc + (faceB ? 0 : Math.signum(dc));
                    // Each point its own thickness: uneven, with a ridge here and there (a crystal edge in the mass).
                    float t = th[Math.min(j, th.length - 1)] * (.5f + .95f * IceGrowth.h(seed, 2));
                    float ridge = IceGrowth.h(seed, 3);
                    if (ridge > .8f) t *= 1.6f + (ridge - .8f) * 4;
                    else if (ridge < .12f) t *= .55f;
                    V.set(PT[0], PT[1], PT[2]);
                    W.set(NT[0], NT[1], NT[2]).normalize();
                    float ox = V.x + W.x * t * unit, oy = V.y + W.y * t * unit, oz = V.z + W.z * t * unit;
                    m.transformPosition(V);
                    W.set(ox, oy, oz);
                    m.transformPosition(W);
                    int o = v * 3;
                    s.base[p][o] = V.x; s.base[p][o + 1] = V.y; s.base[p][o + 2] = V.z;
                    // The side the cold struck is thicker.
                    float ex = W.x - V.x, ey = W.y - V.y, ez = W.z - V.z;
                    float el = Mth.sqrt(ex * ex + ey * ey + ez * ez);
                    float nx = el > 1e-6f ? ex / el : 0, ny = el > 1e-6f ? ey / el : 1, nz = el > 1e-6f ? ez / el : 0;
                    float facing = -(float) (nx * s.way.x + nz * s.way.z);
                    float k = 1 + .25f * Math.max(0, facing);
                    s.outer[p][o] = V.x + ex * k; s.outer[p][o + 1] = Math.max(.01f, V.y + ey * k); s.outer[p][o + 2] = V.z + ez * k;
                    s.nrm[p][o] = nx; s.nrm[p][o + 1] = ny; s.nrm[p][o + 2] = nz;
                    // Its time: reached by the spread from the contact point, or climbing from the feet, whichever first.
                    float dx = V.x - (float) c0.x, dy = V.y - (float) c0.y, dz = V.z - (float) c0.z;
                    float fromHit = 1.2f + Mth.sqrt(dx * dx + dy * dy + dz * dz) / SPREAD;
                    float fromFeet = 3.5f + Math.max(0, V.y) / (SPREAD * 1.15f);
                    s.delay[p][v] = Math.min(fromHit, fromFeet) + (IceGrowth.h(seed, 4) - .5f) * 2.2f;
                    s.grown[p][v] = IceGrowth.grow(age, s.delay[p][v], GROW);
                }
            }
            // The ends: where each part's caps close (a little beyond its first and last rings) and its axis.
            float[] e = s.ends[p];
            PT[axis] = from; PT[b] = cb; PT[c] = cc; V.set(PT[0], PT[1], PT[2]); m.transformPosition(V);
            PT[axis] = to; W.set(0, 0, 0); PT[b] = cb; PT[c] = cc; W.set(PT[0], PT[1], PT[2]); m.transformPosition(W);
            e[0] = V.x; e[1] = V.y; e[2] = V.z; e[3] = W.x; e[4] = W.y; e[5] = W.z;
            float ax = W.x - V.x, ay = W.y - V.y, az = W.z - V.z, al = Mth.sqrt(ax * ax + ay * ay + az * az);
            e[6] = al > 1e-6f ? ax / al : 0; e[7] = al > 1e-6f ? ay / al : 1; e[8] = al > 1e-6f ? az / al : 0;
        }
        s.built = true;
    }
    /** A point of the shell as grown now (into PT). */
    static void at(Shell s, int p, int v) {
        float g = s.grown[p][v];
        int o = v * 3;
        float[] b = s.base[p], u = s.outer[p];
        PT[0] = b[o] + (u[o] - b[o]) * g; PT[1] = b[o + 1] + (u[o + 1] - b[o + 1]) * g; PT[2] = b[o + 2] + (u[o + 2] - b[o + 2]) * g;
    }
    static Vec3 vec(Shell s, int p, int v) { at(s, p, v); return new Vec3(PT[0], PT[1], PT[2]); }

    /** The whole shell as grown now, and what grows on it. */
    static void shell(IceMesh.Ctx c, Shell s, float now, boolean far) {
        float age = s.age(now);
        for (int p = 0; p < PARTS; p++) {
            int K = s.rings[p];
            for (int j = 0; j + 1 < K; j++) for (int i = 0; i < RN; i++) band(c, s, p, j, i, 1);
            cap(c, s, p, 0, 1);
            cap(c, s, p, K - 1, 1);
        }
        if (!s.human) { spines(c, s, age, far); return; }
        // Masses of crystals on the shoulders (heavier on the struck side), small detailed ones on the hands.
        for (int side = 0; side < 2; side++) {
            int v = side == 0 ? 0 : RN / 2;
            float g = IceGrowth.grow(age, s.delay[1][v] + 2, 9);
            float[] e = s.ends[2 + side];
            Vec3 sh = new Vec3(e[0], e[1], e[2]);
            Vec3 out = sh.subtract(new Vec3(s.ends[1][0], s.ends[1][1], s.ends[1][2])).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : out.normalize();
            float struck = (float) Math.max(0, -(out.x * s.way.x + out.z * s.way.z));
            float size = (.16f + .06f * struck) * Mth.clamp(s.height / 1.8f, .5f, 2f);
            IceGrowth.cluster(c, sh.x, sh.y + .06, sh.z, out.x * .7, 1, out.z * .7, size, s.entity * 13 + side, 5 + side, THICK, g * 1.2f, Math.min(1, g * 3));
            float[] a = s.ends[2 + side];
            float gh = IceGrowth.grow(age, s.delay[2 + side][(s.rings[2 + side] - 1) * RN] + 1, 8);
            IceGrowth.cluster(c, a[3], a[4], a[5], a[6], a[7], a[8], .09f * Mth.clamp(s.height / 1.8f, .5f, 2f), s.entity * 7 + side, 6, SHELL, gh * 1.25f, Math.min(1, gh * 3));
        }
        // Where the cold struck, the first and biggest crystals (a few clusters on that side of the torso and upper arms).
        for (int k = 0; k < 3; k++) {
            int p = k == 0 ? 1 : k == 1 ? 1 : 2 + (s.entity + k) % 2;
            int v = nearSide(s, p, 1 + k % 2, true);
            if (v < 0) continue;
            float g = IceGrowth.grow(age, s.delay[p][v] + 1, 7);
            Vec3 at = vec(s, p, v);
            int o = v * 3;
            IceGrowth.cluster(c, at.x, at.y, at.z, s.nrm[p][o], s.nrm[p][o + 1] + .25, s.nrm[p][o + 2], (.1f + .04f * k) * Mth.clamp(s.height / 1.8f, .5f, 2f),
                    s.entity * 3 + k, 4 + k, k == 0 ? THICK : SHELL, g * 1.2f, Math.min(1, g * 3));
        }
        if (!far) spines(c, s, age, far);
    }
    /** The quad of a ring band (part p, between rings j and j + 1, round from i), as grown, alpha k. */
    static void band(IceMesh.Ctx c, Shell s, int p, int j, int i, float k) {
        int i1 = (i + 1) % RN;
        int a = j * RN + i, b = j * RN + i1, q = (j + 1) * RN + i1, d = (j + 1) * RN + i;
        float g = (s.grown[p][a] + s.grown[p][b] + s.grown[p][q] + s.grown[p][d]) * .25f;
        if (g <= .02f) return;
        IceMesh.Mat mat = mat(s, p, a);
        at(s, p, a); float ax = PT[0], ay = PT[1], az = PT[2];
        at(s, p, b); float bx = PT[0], by = PT[1], bz = PT[2];
        at(s, p, q); float qx = PT[0], qy = PT[1], qz = PT[2];
        at(s, p, d);
        IceMesh.quad(c, ax, ay, az, bx, by, bz, qx, qy, qz, PT[0], PT[1], PT[2], mat, k * Mth.clamp(g * 3.5f, 0, 1));
    }
    /** The ice on a ring of part p: the chest (front of the torso) is clearer, head/back/legs heavier. */
    static IceMesh.Mat mat(Shell s, int p, int v) {
        if (!s.human) return IceGrowth.h(s.entity, p * 41 + v) < .3f ? THICK : SHELL;
        if (p == 0 || p >= 4) return THICK;
        if (p == 1) {
            // Front of the torso: -z of the model is his front; read the normal against the body's facing.
            float yaw = (Float.isNaN(s.heldYaw) ? 0 : s.heldYaw) * Mth.DEG_TO_RAD;
            float fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
            float front = s.nrm[p][v * 3] * fx + s.nrm[p][v * 3 + 2] * fz;
            return front > .45f ? CHEST : THICK;
        }
        return SHELL;
    }
    /** Closes a part's end ring j with a fan to a point a little beyond it (the head's crown higher, the feet flat). */
    static void cap(IceMesh.Ctx c, Shell s, int p, int j, float k) {
        float[] e = s.ends[p];
        boolean first = j == 0;
        float gsum = 0;
        float mx = 0, my = 0, mz = 0;
        for (int i = 0; i < RN; i++) { at(s, p, j * RN + i); mx += PT[0]; my += PT[1]; mz += PT[2]; gsum += s.grown[p][j * RN + i]; }
        float g = gsum / RN;
        if (g <= .02f) return;
        mx /= RN; my /= RN; mz /= RN;
        float push = (s.human && p == 0 && !first ? .16f : .05f) * g * Mth.clamp(s.height / 1.8f, .5f, 2f);
        float sg = first ? -1 : 1;
        float tx = mx + e[6] * push * sg, ty = my + e[7] * push * sg, tz = mz + e[8] * push * sg;
        if (ty < .01f) ty = .01f;
        // The crown: off centre, never a neat point.
        if (s.human && p == 0 && !first) { tx += (IceGrowth.h(s.entity, 5) - .5f) * .08f; tz += (IceGrowth.h(s.entity, 6) - .5f) * .08f; }
        for (int i = 0; i < RN; i++) {
            int a = j * RN + i, b = j * RN + (i + 1) % RN;
            at(s, p, a); float ax = PT[0], ay = PT[1], az = PT[2];
            at(s, p, b);
            IceMesh.tri(c, ax, ay, az, PT[0], PT[1], PT[2], tx, ty, tz, mat(s, p, a), k * Mth.clamp(g * 3.5f, 0, 1));
        }
    }
    /** A point on part p's ring j on the side the cold struck (near true) or away from it; -1 if none. */
    static int nearSide(Shell s, int p, int j, boolean near) {
        if (j >= s.rings[p]) return -1;
        int best = -1; float bd = near ? 1e9f : -1e9f;
        for (int i = 0; i < RN; i++) {
            int v = j * RN + i;
            float d = (float) (s.nrm[p][v * 3] * s.way.x + s.nrm[p][v * 3 + 2] * s.way.z);
            if (near ? d < bd : d > bd) { bd = d; best = v; }
        }
        return best;
    }

    /**
     * The small thin crystals pushed out on the side away from the cold (the reference's detail): scattered over the
     * shell facing away (behind the shoulders and arms, round the back, the legs, the sides), alone or two or three from
     * one thicker spot, each its own length (mostly small, a few longer), thickness, lean up, down or aside, and bend;
     * growing last. Never a row, never the same twice.
     */
    static void spines(IceMesh.Ctx c, Shell s, float age, boolean far) {
        Random r = new Random(s.entity * 131L + 7);
        float scale = Mth.clamp(s.height / 1.8f, .5f, 2f);
        int want = far ? 5 : 15, made = 0;
        for (int tries = 0; tries < 160 && made < want; tries++) {
            int p = r.nextInt(PARTS), K = s.rings[p];
            if (K < 2) continue;
            int j = r.nextInt(K), i = r.nextInt(RN), v = j * RN + i;
            float away = (float) (s.nrm[p][v * 3] * s.way.x + s.nrm[p][v * 3 + 2] * s.way.z);
            // Facing away from the cold, more often the further away; now and then one on a side.
            if (r.nextFloat() > Mth.clamp(away * 1.4f + .08f, 0, 1)) continue;
            int group = r.nextFloat() < .3f ? 2 + r.nextInt(2) : 1;
            float delay = DEEP_SEIZE - 3 + 9 * r.nextFloat();
            Vec3 at = vec(s, p, v);
            Vec3 n = new Vec3(s.nrm[p][v * 3], s.nrm[p][v * 3 + 1], s.nrm[p][v * 3 + 2]);
            Vec3[] fr = IceMesh.frame(n);
            for (int k = 0; k < group; k++) {
                made++;
                float lenK = k == 0 ? .2f + 1.2f * r.nextFloat() * r.nextFloat() : .15f + .5f * r.nextFloat();
                if (r.nextFloat() < .12f) lenK *= 1.6f;
                float rad = (.009f + .014f * r.nextFloat()) * scale * (.7f + .5f * lenK);
                float g = IceGrowth.grow(age, Math.max(delay + k * 1.5f, s.delay[p][v] + 3), 6);
                if (g <= .02f) continue;
                // Out of the surface, pushed a little the cold's way, leaning its own way (up, down, aside).
                float a1 = (r.nextFloat() - .5f) * 1.6f, a2 = (r.nextFloat() - .45f) * 1.3f;
                Vec3 d0 = n.add(s.way.scale(.25 + .3 * r.nextFloat())).add(fr[0].scale(a1)).add(fr[1].scale(a2 * .6)).add(0, a2 * .5, 0).normalize();
                // It curves as it goes: down (an icicle's weight) or aside.
                float bend = .2f + .7f * r.nextFloat();
                Vec3 side = fr[1].scale((r.nextFloat() - .5f) * 2);
                Vec3 d1 = d0.add(side.scale(bend * .6)).add(0, -bend * (r.nextFloat() < .6f ? .7 : -.2), 0).normalize();
                float L = (.1f + .32f * lenK) * scale * g;
                Vec3 base = at.add(fr[0].scale((r.nextFloat() - .5f) * .05)).add(fr[1].scale((r.nextFloat() - .5f) * .05));
                Vec3 p0 = base.subtract(d0.scale(rad * 1.5));
                Vec3 p1 = p0.add(d0.scale(L * .4));
                Vec3 p2 = p1.add(d0.add(d1).normalize().scale(L * .34));
                Vec3 p3 = p2.add(d1.scale(L * .26));
                SPINE_PATH.clear();
                SPINE_PATH.add(p0); SPINE_PATH.add(p1); SPINE_PATH.add(p2); SPINE_PATH.add(p3);
                float rr = rad * (.5f + .5f * g);
                SPINE_R[0] = rr; SPINE_R[1] = rr * (.6f + .2f * r.nextFloat()); SPINE_R[2] = rr * .36f; SPINE_R[3] = rr * .04f;
                IceMesh.tube(c, SPINE_PATH, SPINE_R, 4, 4 + r.nextInt(2), s.entity + made * 17, r.nextFloat() < .3f ? IceMesh.MILKY : SPINE, Math.min(1, g * 3));
            }
        }
        if (far) return;
        // Medium scale: short broken crystals lying out of the shell all round it (its outline is never smooth).
        for (int k = 0; k < 16; k++) {
            int p = r.nextInt(PARTS), K = s.rings[p];
            int v = r.nextInt(K * RN);
            float g = IceGrowth.grow(age, s.delay[p][v] + 2 + 3 * r.nextFloat(), 6);
            if (g <= .02f) continue;
            Vec3 at = vec(s, p, v);
            Vec3 n = new Vec3(s.nrm[p][v * 3], s.nrm[p][v * 3 + 1], s.nrm[p][v * 3 + 2]);
            Vec3[] fr = IceMesh.frame(n);
            Vec3 d = n.add(fr[0].scale((r.nextFloat() - .5f) * 1.6)).add(fr[1].scale((r.nextFloat() - .5f) * 1.6)).normalize();
            float len = (.05f + .07f * r.nextFloat()) * scale;
            IceGrowth.crystal(c, at.x - d.x * len * .3, at.y - d.y * len * .3, at.z - d.z * len * .3, d.x, d.y, d.z, len, len * (.3f + .2f * r.nextFloat()),
                    s.entity * 41 + k, r.nextFloat() < .35f ? IceMesh.MILKY : SHELL, g, Math.min(1, g * 3));
        }
    }
    private static final List<Vec3> SPINE_PATH = new ArrayList<>(4);
    private static final float[] SPINE_R = new float[4];

    /** Frost spreading over the ground from the feet and a few crystals where they are frozen to it; melts after the break. */
    static void ground(IceMesh.Ctx c, Shell s, float now) {
        float age = s.age(now);
        float melt = s.broke < 0 ? 1 : 1 - ease((now - s.broke - 6) / 40);
        float spread = ease((age - 2) / 14) * melt;
        if (spread <= .01f) return;
        float R = s.width * .5f + .12f;
        for (int i = 0; i < 7; i++) {
            float a = i * Mth.TWO_PI / 7 + IceGrowth.h(s.entity, 40 + i) * .7f, d = R * (.35f + .65f * IceGrowth.h(s.entity, 50 + i));
            float rad = (.1f + .09f * IceGrowth.h(s.entity, 60 + i)) * (.35f + .65f * spread);
            IceGrowth.crystal(c, Mth.cos(a) * d, .008, Mth.sin(a) * d, 0, 1, 0, .015f, rad, s.entity + i * 13, IceMesh.FROST, 1, .85f * spread);
        }
        if (!s.built) return;
        // At the feet: little crystals tying them to the ground.
        int[] legs = s.human ? new int[]{4, 5} : new int[]{2, 3, 4, 5};
        for (int leg : legs) {
            int last = (s.rings[leg] - 1) * RN;
            int v = s.human ? last : 0;
            float g = IceGrowth.grow(age, s.delay[leg][v] + 2, 7) * melt;
            if (g <= .02f) continue;
            for (int k = 0; k < 2; k++) {
                int vv = (s.human ? last : 0) + (k * 3 + leg) % RN;
                at(s, leg, vv);
                if (PT[1] > .25f) continue;
                Vec3 out = new Vec3(s.nrm[leg][vv * 3], 0, s.nrm[leg][vv * 3 + 2]);
                IceGrowth.crystal(c, PT[0], .0, PT[2], out.x * .6, 1, out.z * .6, .07f + .05f * IceGrowth.h(s.entity, leg * 9 + k), .025f, s.entity * 5 + leg * 3 + k,
                        k == 0 ? THICK : SHELL, g, Math.min(1, g * 3));
            }
        }
    }

    // ------------------------------------------------------------------ cracks
    /** The crack network over the shell: each point's time the crack reaches it and the point it came from. */
    static final class Cracks {
        final float[] time = new float[PARTS * MAXR * RN];
        final int[] from = new int[PARTS * MAXR * RN];
        final boolean[] drawn = new boolean[PARTS * MAXR * RN];
    }
    static int id(int p, int v) { return p * MAXR * RN + v; }
    /**
     * Starts the cracks: the first from where the blow landed (or a point of its own when it gives way by itself), more
     * from other points a little later, every one running over the shell (ring to ring, round, across the joints)
     * until they meet. fast: a blow (all in a few ticks).
     */
    static void startCracks(Shell s, float now, boolean fast) {
        Cracks k = new Cracks();
        Arrays.fill(k.time, Float.MAX_VALUE);
        Arrays.fill(k.from, -1);
        float speed = fast ? .45f : .055f;
        PriorityQueue<float[]> q = new PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
        // The first crack.
        Vec3 o = fast && s.blow.lengthSqr() > 1e-4 ? new Vec3(0, s.height * .55, 0).subtract(s.blow.scale(s.width * .5)) : contact(s);
        int first = nearest(s, o);
        k.time[first] = now;
        q.add(new float[]{now, first});
        // Secondary cracks from other points of the body, starting later.
        int more = fast ? 2 : 3;
        for (int n = 0; n < more; n++) {
            int p = 1 + s.rng.nextInt(PARTS - 1), v = s.rng.nextInt(s.rings[p] * RN), idx = id(p, v);
            float t = now + (fast ? 1 + n : 5 + n * 4);
            if (t < k.time[idx]) { k.time[idx] = t; q.add(new float[]{t, idx}); }
        }
        while (!q.isEmpty()) {
            float[] top = q.poll();
            int u = (int) top[1];
            if (top[0] > k.time[u]) continue;
            int p = u / (MAXR * RN), v = u % (MAXR * RN), j = v / RN, i = v % RN, K = s.rings[p];
            at(s, p, v);
            float ux = PT[0], uy = PT[1], uz = PT[2];
            int[] nb = {j * RN + (i + 1) % RN, j * RN + (i + RN - 1) % RN, j + 1 < K ? (j + 1) * RN + i : -1, j > 0 ? (j - 1) * RN + i : -1};
            for (int w : nb) if (w >= 0) relax(s, k, q, u, id(p, w), p, w, ux, uy, uz, speed);
            // Across the joints: the end rings of touching parts.
            if (j == 0 || j == K - 1) for (int p2 = 0; p2 < PARTS; p2++) {
                if (p2 == p) continue;
                int w = nearestOn(s, p2, ux, uy, uz, .2f);
                if (w >= 0) relax(s, k, q, u, id(p2, w), p2, w, ux, uy, uz, speed);
            }
        }
        // Which steps are drawn: the trunk of every crack, then about half its branches.
        for (int n = 0; n < k.from.length; n++)
            if (k.from[n] >= 0) k.drawn[n] = IceGrowth.h(s.entity, n * 7 + 3) < .58f || depth(k, n) < 3;
        s.cracks = k;
        s.crackStart = now;
    }
    static void relax(Shell s, Cracks k, PriorityQueue<float[]> q, int u, int w, int p, int v, float ux, float uy, float uz, float speed) {
        at(s, p, v);
        float dx = PT[0] - ux, dy = PT[1] - uy, dz = PT[2] - uz;
        float d = Mth.sqrt(dx * dx + dy * dy + dz * dz) * (.7f + .7f * IceGrowth.h(u * 31 + w, 9));
        float t = k.time[u] + d / speed;
        if (t < k.time[w]) { k.time[w] = t; k.from[w] = u; q.add(new float[]{t, w}); }
    }
    static int depth(Cracks k, int n) { int d = 0; while (k.from[n] >= 0 && d < 9) { n = k.from[n]; d++; } return d; }
    static int nearest(Shell s, Vec3 o) {
        int best = id(1, 0); double bd = Double.MAX_VALUE;
        for (int p = 0; p < PARTS; p++) for (int v = 0; v < s.rings[p] * RN; v++) {
            at(s, p, v);
            double dx = o.x - PT[0], dy = o.y - PT[1], dz = o.z - PT[2], d = dx * dx + dy * dy + dz * dz;
            if (d < bd) { bd = d; best = id(p, v); }
        }
        return best;
    }
    static int nearestOn(Shell s, int p, float x, float y, float z, float within) {
        int best = -1; float bd = within * within;
        int K = s.rings[p];
        for (int j : new int[]{0, K - 1}) for (int i = 0; i < RN; i++) {
            int v = j * RN + i;
            at(s, p, v);
            float dx = PT[0] - x, dy = PT[1] - y, dz = PT[2] - z, d = dx * dx + dy * dy + dz * dz;
            if (d < bd) { bd = d; best = v; }
        }
        return best;
    }
    /** The cracks as they run (bright lines on the shell, a spark at each running front); they go with the pieces. */
    static void cracks(IceMesh.Ctx c, Shell s, float now) {
        Cracks k = s.cracks;
        float fade = s.broke < 0 ? 1 : 1 - Mth.clamp((now - s.broke - 2) / 4, 0, 1);
        if (fade <= .01f) return;
        for (int n = 0; n < k.from.length; n++) {
            int u = k.from[n];
            if (u < 0 || !k.drawn[n]) continue;
            float t0 = k.time[u], t1 = k.time[n];
            if (now < t0) continue;
            float prog = t1 <= t0 ? 1 : Mth.clamp((now - t0) / (t1 - t0), 0, 1);
            int pu = u / (MAXR * RN), vu = u % (MAXR * RN), pn = n / (MAXR * RN), vn = n % (MAXR * RN);
            Vec3 a = lifted(s, pu, vu), b = lifted(s, pn, vn);
            Vec3 to = a.lerp(b, prog);
            float young = Mth.clamp((now - t0) / 6, 0, 1);
            IceMesh.vein(c, a, to, .005f + .004f * (1 - young), (.55f + .45f * (1 - young)) * fade);
            if (prog < 1 && IceGrowth.h(n, 5) < .5f) IceMesh.sparkle(c, to, .045f, .7f * fade);
        }
    }
    static Vec3 lifted(Shell s, int p, int v) {
        at(s, p, v);
        int o = v * 3;
        return new Vec3(PT[0] + s.nrm[p][o] * .012, PT[1] + s.nrm[p][o + 1] * .012, PT[2] + s.nrm[p][o + 2] * .012);
    }

    // ------------------------------------------------------------------ the pieces
    /**
     * A chunk of the shell: its points on the outside and on the skin side (relative to its middle, as it was when it
     * broke), when it lets go, how it flies (velocity, spin axis and rate), its size; landed = burst into chips.
     */
    static final class Chunk {
        Vec3 mid, vel, spin; float rate, letGo, size; int seed;
        float[] out, in; int cols, rows; IceMesh.Mat mat;
        boolean gone, chipped;
    }
    /** Cuts the shell into chunks along its rings: sectors of 2 or 3 points, one band or two (the big ones). */
    static List<Chunk> chunks(Shell s, float now) {
        List<Chunk> list = new ArrayList<>();
        Cracks k = s.cracks;
        for (int p = 0; p < PARTS; p++) {
            int K = s.rings[p];
            int j = 0;
            while (j + 1 < K) {
                boolean big = (p == 1 || p == 0 || p >= 4) && j + 2 < K && IceGrowth.h(s.entity, p * 17 + j) < .4f;
                int rows = big ? 3 : 2;
                int i = (int) (IceGrowth.h(s.entity, p * 23 + j) * RN);
                int left = RN;
                while (left > 0) {
                    int cols = Math.min(left, IceGrowth.h(s.entity, p * 29 + j * 5 + i) < .5f ? 3 : 2) + 1;
                    if (left - (cols - 1) == 1) cols++;
                    list.add(chunk(s, p, j, i, cols, rows, k, now));
                    left -= cols - 1;
                    i = (i + cols - 1) % RN;
                }
                j += rows - 1;
            }
            // The caps (the crown, the hands, the feet, the shoulders' ends) come off whole.
            list.add(capChunk(s, p, 0, k, now));
            list.add(capChunk(s, p, K - 1, k, now));
        }
        return list;
    }
    /** A cap as a chunk: its ring and its point (a grid of 2 rows, the second all the point), wrapping round. */
    static Chunk capChunk(Shell s, int p, int j, Cracks k, float now) {
        Chunk ch = chunk(s, p, j, 0, RN + 1, 1, k, now);
        // Rebuild as two rows: the ring (closed) and the point a little beyond it.
        float[] e = s.ends[p];
        float sg = j == 0 ? -1 : 1, push = (s.human && p == 0 && j != 0 ? .16f : .05f) * Mth.clamp(s.height / 1.8f, .5f, 2f);
        int cols = RN + 1;
        float[] out = new float[cols * 2 * 3], in = new float[cols * 2 * 3];
        System.arraycopy(ch.out, 0, out, 0, cols * 3);
        System.arraycopy(ch.in, 0, in, 0, cols * 3);
        float mx = 0, my = 0, mz = 0;
        for (int i = 0; i < RN; i++) { mx += ch.out[i * 3]; my += ch.out[i * 3 + 1]; mz += ch.out[i * 3 + 2]; }
        mx /= RN; my /= RN; mz /= RN;
        for (int i = 0; i < cols; i++) {
            int o = (cols + i) * 3;
            out[o] = mx + e[6] * push * sg; out[o + 1] = my + e[7] * push * sg; out[o + 2] = mz + e[8] * push * sg;
            in[o] = mx; in[o + 1] = my; in[o + 2] = mz;
        }
        ch.out = out; ch.in = in; ch.rows = 2;
        return ch;
    }
    static Chunk chunk(Shell s, int p, int j0, int i0, int cols, int rows, Cracks k, float now) {
        Chunk ch = new Chunk();
        ch.cols = cols; ch.rows = rows;
        ch.out = new float[cols * rows * 3]; ch.in = new float[cols * rows * 3];
        float mx = 0, my = 0, mz = 0, nx = 0, ny = 0, nz = 0, t = 0;
        for (int r = 0; r < rows; r++) for (int q = 0; q < cols; q++) {
            int v = (j0 + r) * RN + (i0 + q) % RN, o = (r * cols + q) * 3;
            at(s, p, v);
            ch.out[o] = PT[0]; ch.out[o + 1] = PT[1]; ch.out[o + 2] = PT[2];
            // The skin side: a little inside the outer face (a piece has thickness, a fresh break inside).
            ch.in[o] = s.base[p][v * 3] + (PT[0] - s.base[p][v * 3]) * .15f;
            ch.in[o + 1] = s.base[p][v * 3 + 1] + (PT[1] - s.base[p][v * 3 + 1]) * .15f;
            ch.in[o + 2] = s.base[p][v * 3 + 2] + (PT[2] - s.base[p][v * 3 + 2]) * .15f;
            mx += PT[0]; my += PT[1]; mz += PT[2];
            nx += s.nrm[p][v * 3]; ny += s.nrm[p][v * 3 + 1]; nz += s.nrm[p][v * 3 + 2];
            float tv = k == null ? now : k.time[id(p, v)];
            t = Math.max(t, Math.min(tv, now + 20));
        }
        int n = cols * rows;
        ch.mid = new Vec3(mx / n, my / n, mz / n);
        for (int i = 0; i < n; i++) {
            ch.out[i * 3] -= (float) ch.mid.x; ch.out[i * 3 + 1] -= (float) ch.mid.y; ch.out[i * 3 + 2] -= (float) ch.mid.z;
            ch.in[i * 3] -= (float) ch.mid.x; ch.in[i * 3 + 1] -= (float) ch.mid.y; ch.in[i * 3 + 2] -= (float) ch.mid.z;
        }
        Vec3 out = new Vec3(nx, ny * .4, nz);
        out = out.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : out.normalize();
        ch.seed = s.rng.nextInt(1 << 20);
        float h = s.rng.nextFloat();
        // When it lets go: once the cracks reached it all round, then a beat of its own (a few hold on longer).
        float hold = h < .15f ? 6 + 8 * s.rng.nextFloat() : 3 * s.rng.nextFloat();
        ch.letGo = Math.max(now, t) + hold * (s.how == 2 ? .5f : 1);
        // How it flies: off the body, a little up; a blow throws the pieces its way, more the nearer it struck.
        float push = s.how == 2 ? .1f : .04f;
        Vec3 blow = s.blow.scale(s.how == 2 ? .09 * (.5 + .5 * Math.max(0, out.dot(s.blow))) : 0);
        ch.vel = out.scale(push + .05f * s.rng.nextFloat()).add(blow).add(0, .03 + .05 * s.rng.nextFloat() - (ch.mid.y > s.height * .6 ? 0 : .02), 0);
        ch.spin = new Vec3(s.rng.nextGaussian(), s.rng.nextGaussian(), s.rng.nextGaussian()).normalize();
        ch.rate = (.08f + .22f * s.rng.nextFloat()) * (s.rng.nextBoolean() ? 1 : -1) * (s.how == 2 ? 1.5f : 1);
        float span = 0;
        for (int i = 0; i < n; i++) span = Math.max(span, Mth.sqrt(ch.out[i * 3] * ch.out[i * 3] + ch.out[i * 3 + 1] * ch.out[i * 3 + 1] + ch.out[i * 3 + 2] * ch.out[i * 3 + 2]));
        ch.size = span;
        ch.mat = mat(s, p, j0 * RN + i0);
        return ch;
    }
    /** Where a chunk's middle is t ticks after it let go, and its turn (angle). */
    static Vec3 flight(Chunk ch, float t) {
        if (t <= 0) return ch.mid;
        Vec3 p = ch.mid.add(ch.vel.scale(t)).add(0, -.5 * .06 * t * t, 0);
        return p.y < ch.size * .4 ? new Vec3(p.x, ch.size * .4, p.z) : p;
    }
    /** The pieces: each in place until it lets go, then turning as it flies; a fresh clean face where it broke. */
    static void pieces(IceMesh.Ctx c, Shell s, float now, boolean far) {
        if (s.chunks == null) return;
        Vec3 axis;
        for (Chunk ch : s.chunks) {
            if (ch.gone) continue;
            float t = now - ch.letGo;
            Vec3 mid = flight(ch, t);
            float ang = t > 0 ? ch.rate * t : 0;
            axis = ch.spin;
            c.origin(mid);
            int cols = ch.cols, rows = ch.rows;
            for (int r = 0; r + 1 < rows; r++) for (int q = 0; q + 1 < cols; q++) {
                int a = r * cols + q, b = a + 1, d = a + cols, e = d + 1;
                quad(c, ch.out, a, b, e, d, mid, axis, ang, ch.mat);
                quad(c, ch.in, d, e, b, a, mid, axis, ang, IceMesh.FRESH);
            }
            if (far) continue;
            // The broken edges round it (fresh ice).
            for (int q = 0; q + 1 < cols; q++) {
                edge(c, ch, q, q + 1, mid, axis, ang);
                edge(c, ch, (rows - 1) * cols + q + 1, (rows - 1) * cols + q, mid, axis, ang);
            }
            for (int r = 0; r + 1 < rows; r++) {
                edge(c, ch, (r + 1) * cols, r * cols, mid, axis, ang);
                edge(c, ch, r * cols + cols - 1, (r + 1) * cols + cols - 1, mid, axis, ang);
            }
        }
        c.ox = c.oy = c.oz = 0;
    }
    private static final float[] QA = new float[12];
    static void quad(IceMesh.Ctx c, float[] pts, int a, int b, int q, int d, Vec3 mid, Vec3 axis, float ang, IceMesh.Mat mat) {
        int[] ids = {a, b, q, d};
        for (int i = 0; i < 4; i++) turned(pts, ids[i], mid, axis, ang, i);
        IceMesh.quad(c, QA[0], QA[1], QA[2], QA[3], QA[4], QA[5], QA[6], QA[7], QA[8], QA[9], QA[10], QA[11], mat, 1);
    }
    static void edge(IceMesh.Ctx c, Chunk ch, int a, int b, Vec3 mid, Vec3 axis, float ang) {
        turned(ch.out, a, mid, axis, ang, 0); turned(ch.out, b, mid, axis, ang, 1);
        turned(ch.in, b, mid, axis, ang, 2); turned(ch.in, a, mid, axis, ang, 3);
        IceMesh.quad(c, QA[0], QA[1], QA[2], QA[3], QA[4], QA[5], QA[6], QA[7], QA[8], QA[9], QA[10], QA[11], IceMesh.FRESH, 1);
    }
    /** A chunk's point i turned by ang about axis round its middle, into QA slot k. */
    static void turned(float[] pts, int i, Vec3 mid, Vec3 axis, float ang, int k) {
        double x = pts[i * 3], y = pts[i * 3 + 1], z = pts[i * 3 + 2];
        if (ang != 0) {
            double cs = Math.cos(ang), sn = Math.sin(ang), ax = axis.x, ay = axis.y, az = axis.z;
            double dot = ax * x + ay * y + az * z;
            double cx = ay * z - az * y, cy = az * x - ax * z, cz = ax * y - ay * x;
            double rx = x * cs + cx * sn + ax * dot * (1 - cs), ry = y * cs + cy * sn + ay * dot * (1 - cs), rz = z * cs + cz * sn + az * dot * (1 - cs);
            x = rx; y = ry; z = rz;
        }
        QA[k * 3] = (float) (mid.x + x); QA[k * 3 + 1] = (float) (mid.y + y); QA[k * 3 + 2] = (float) (mid.z + z);
    }

    static float ease(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
}
