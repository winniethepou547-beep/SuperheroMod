package com.FIRNI.superheromod.client.render.iceman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Iceman's ice, the one material everything of his is made of (his body, the weapons, the sculptures, the slide's track,
 * the shell, the spikes, the frost on his enemies), so it all reads as the same ice.
 * <p>
 * Minecraft ice, after the user's reference (a blocky Minecraft Iceman of bright blue ice): solid, textured in pixels
 * (16 texels a block, 1 a model pixel) with real ice's structure (textures/entity/iceman/ice.png: a bright cyan-blue
 * body, deeper blue pockets, short white fracture streaks on the slant, trapped air bubbles; frost.png: whiter packed
 * ice and rime), mapped on whichever side a face looks to (so a block of it reads like a block of ice). The texture
 * carries the look; each face is lit softly from above the camera on top of Minecraft's own light, and the ice is wet:
 * a white gloss where the light glances off a face toward the eye and a little cold light on the outline, added on top
 * (additive), never a cyan crystal glow. Only forming and melting ice is see-through (the context's alpha).
 * Materials ({@link Mat}) tint it (deep, glacier, milky ice pick the whiter texture); CLOTH materials are plain colour.
 * His body has its own skin (skin.png, Minecraft box layout, {@link #skinBox}). Thin bright lines (glowing cracks) and
 * sparkles are additive light.
 * <p>
 * Everything is gathered while drawing and emitted at {@link Ctx#end()} texture by texture (ice, frost, skin, then the
 * light), so the buffers are never mixed. Use: {@code Ctx c = IceMesh.begin(pose, buffers, light)}, then (after any
 * change of the pose stack) {@code c.at(pose)}, the shapes, and {@code c.end()}. Coordinates are those of the pose
 * stack (pixels if the caller scaled by 1/16: the texture scale follows the stack's scale).
 */
public final class IceMesh {
    private IceMesh() {}

    public static final ResourceLocation ICE_TEX = new ResourceLocation("superheromod", "textures/entity/iceman/ice.png"),
            FROST_TEX = new ResourceLocation("superheromod", "textures/entity/iceman/frost.png"),
            SKIN_TEX = new ResourceLocation("superheromod", "textures/entity/iceman/skin.png");
    /** The ice, the frost and his skin (translucent, sorted, lit, depth-writing, both sides). */
    public static final RenderType ICE = RenderType.entityTranslucent(ICE_TEX), FROST_ICE = RenderType.entityTranslucent(FROST_TEX),
            SKIN = RenderType.entityTranslucent(SKIN_TEX);
    /** Light: the gloss, the eyes, the cracks (additive, full bright, both sides). */
    public static final RenderType GLINT = IceTypes.GLINT;
    private static final RenderType[] TYPES = {ICE, FROST_ICE, SKIN};
    static final int T_ICE = 0, T_FROST = 1, T_SKIN = 2;
    static final int FULL = 15728880;
    /** The size of the tiles and of the skin, in texels. */
    static final float TILE = 16, SKIN_SIZE = 64;
    /** Draws what is gathered in a buffer source for the ice (after the contexts' end()). */
    public static void endBatches(MultiBufferSource.BufferSource b) {
        for (RenderType t : TYPES) b.endBatch(t);
        b.endBatch(GLINT);
    }

    /**
     * A material. Ice (kind ICE): r, g, b tint the ice texture (CLEAR's colour = the texture as drawn; darker or bluer
     * deepens it); milky ice (milk >= .15, or a pale colour) takes the frost texture; clear / edge = its opacity seen
     * straight on / edge-on (1: it is NOT see-through; less only while forming or melting); rim = the cold light on its
     * outline; spec = how wet and glossy. Cloth (kind CLOTH): the suit's plain colour, a faint sheen.
     */
    public record Mat(float r, float g, float b, float clear, float edge, float milk, float rim, float spec, int kind) {
        public Mat(float r, float g, float b, float clear, float edge, float milk, float rim, float spec) { this(r, g, b, clear, edge, milk, rim, spec, ICE_KIND); }
        /** The same, more see-through (k < 1) or more solid (k > 1). */
        public Mat alpha(float k) { return new Mat(r, g, b, Math.min(1, clear * k), Math.min(1, edge * k), milk, rim, spec, kind); }
        public Mat tint(float tr, float tg, float tb) { return new Mat(r * tr, g * tg, b * tb, clear, edge, milk, rim, spec, kind); }
        /** Which texture it is drawn with. */
        int tex() { return kind == CLOTH_KIND || milk >= .15f || r >= .7f ? T_FROST : T_ICE; }
    }
    public static final int ICE_KIND = 0, CLOTH_KIND = 1;
    /** The ice texture's own colour, and the frost's (a material of this colour draws the texture as it is). */
    private static final float IR = .52f, IG = .82f, IB = 1f, FR = .85f, FG = .95f, FB = 1f;
    /** Plain ice: the bright blue body of the ice. */
    public static final Mat CLEAR = new Mat(.52f, .82f, 1f, .96f, 1f, .04f, .9f, .9f);
    /** Pale ice: whiter, frostier (highlights of the body, hair, plates). */
    public static final Mat MILKY = new Mat(.76f, .92f, 1f, .97f, 1f, .2f, .65f, .6f);
    /** The inside of a limb (rarely seen now the ice is solid). */
    public static final Mat CORE = new Mat(.76f, .92f, 1f, .97f, 1f, .2f, .5f, .4f);
    /** Rime, frost: white, matt. */
    public static final Mat FROST = new Mat(.9f, .97f, 1f, .92f, .96f, .55f, .3f, .3f);
    /** Deep ice: dark blue (eye sockets, shadows, old ice). */
    public static final Mat DEEP = new Mat(.2f, .42f, .78f, .98f, 1f, 0f, .6f, .8f);
    /** A fresh break: the inside, cleaner and brighter than the surface. */
    public static final Mat FRESH = new Mat(.82f, .97f, 1f, .92f, 1f, .3f, 1f, 1.2f);
    /** Glacier ice: thick, a deeper blue (the shell, the giant mace). */
    public static final Mat GLACIER = new Mat(.4f, .7f, .95f, .96f, 1f, .08f, .85f, .9f);
    /** The X-Men suit (the comic Iceman): black, red, the grey of the belt. */
    public static final Mat SUIT_BLACK = new Mat(.075f, .08f, .1f, 1, 1, 0, .2f, .35f, CLOTH_KIND),
            SUIT_RED = new Mat(.74f, .07f, .085f, 1, 1, 0, .15f, .3f, CLOTH_KIND),
            SUIT_GREY = new Mat(.26f, .27f, .31f, 1, 1, 0, .2f, .5f, CLOTH_KIND),
            SUIT_PALE = new Mat(.86f, .86f, .9f, 1, 1, 0, .1f, .4f, CLOTH_KIND);
    /** His skin's boxes: the texture as painted; ice parts wet and glossy, suit parts barely. */
    public static final Mat SKIN_ICE = new Mat(1, 1, 1, 1, 1, 0, .7f, .9f), SKIN_SUIT = new Mat(1, 1, 1, 1, 1, 0, .25f, .3f);

    // ------------------------------------------------------------------ the drawing context
    public static Ctx begin(PoseStack pose, MultiBufferSource buffers, int light) {
        Ctx c = new Ctx();
        c.buffers = buffers;
        c.light = light;
        c.at(pose);
        return c;
    }
    /** What one texture gathers: per vertex x, y, z, r, g, b, a, u, v, nx, ny, nz (in view space) and the light. */
    private static final class Batch {
        float[] f = new float[12 * 64];
        int[] l = new int[64];
        int n;
        void put(float x, float y, float z, float r, float g, float b, float a, float u, float v, float nx, float ny, float nz, int light) {
            if ((n + 1) * 12 > f.length) { f = java.util.Arrays.copyOf(f, f.length * 2); l = java.util.Arrays.copyOf(l, l.length * 2); }
            int i = n * 12;
            f[i] = x; f[i + 1] = y; f[i + 2] = z; f[i + 3] = r; f[i + 4] = g; f[i + 5] = b; f[i + 6] = a; f[i + 7] = u; f[i + 8] = v;
            f[i + 9] = nx; f[i + 10] = ny; f[i + 11] = nz;
            l[n++] = light;
        }
    }
    public static final class Ctx {
        MultiBufferSource buffers;
        Matrix4f m;
        Matrix3f n;
        public int light;
        /** Everything drawn is this much as opaque (fades), and this much brighter (a flash, 0 = as lit). */
        public float alpha = 1, flash;
        /** Time for the sparkle (ticks). */
        public float time;
        /** Full bright (lit by itself: the ice does not darken at night when false). */
        public boolean emissive;
        /** Texels per unit of the stack: worked out from its scale (16 a block) unless set here (> 0). */
        public float texel;
        /** Where the texture is pinned (in the stack's units): set it on a moving piece so its texture moves with it. */
        public float ox, oy, oz;
        float cx, cy, cz, ux, uy, uz, rx, ry, rz, autoTexel = 16;
        /** The face being drawn: its own shade (broken facets never all catch the light the same). */
        float facet = 1;
        private final Batch[] batches = {new Batch(), new Batch(), new Batch()};
        private float[] glints = new float[256];
        private int glintCount;

        /** Takes the pose stack's current transform (call after every push / translate / rotate). */
        public Ctx at(PoseStack pose) {
            m = new Matrix4f(pose.last().pose());
            n = new Matrix3f(pose.last().normal());
            Matrix4f inv = new Matrix4f(m).invert();
            Vector3f cam = inv.transformPosition(new Vector3f());
            cx = cam.x; cy = cam.y; cz = cam.z;
            // The camera's up in this space (the light comes from above the camera).
            Vector3f up = new Vector3f(n.m01(), n.m11(), n.m21());
            if (up.lengthSquared() < 1e-8f) up.set(0, 1, 0);
            up.normalize();
            ux = up.x; uy = up.y; uz = up.z;
            Vector3f right = new Vector3f(n.m00(), n.m10(), n.m20());
            if (right.lengthSquared() < 1e-8f) right.set(1, 0, 0);
            right.normalize();
            rx = right.x; ry = right.y; rz = right.z;
            // How long a unit of this space is (16 texels a block).
            float sc = Mth.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
            autoTexel = sc > 1e-6f ? 16 * sc : 16;
            return this;
        }
        /** Where the camera is in this space. */
        public Vec3 camera() { return new Vec3(cx, cy, cz); }
        /** Pins the texture to a point (a moving piece's own middle), in this space. */
        public Ctx origin(Vec3 at) { ox = (float) at.x; oy = (float) at.y; oz = (float) at.z; return this; }

        /** Emits what was gathered (the ice, the frost, the skin, then the light); call once the shapes are drawn. */
        public void end() {
            for (int t = 0; t < 3; t++) {
                Batch b = batches[t];
                if (b.n == 0) continue;
                VertexConsumer v = buffers.getBuffer(TYPES[t]);
                float[] f = b.f;
                for (int i = 0; i < b.n; i++) {
                    int k = i * 12;
                    v.vertex(f[k], f[k + 1], f[k + 2], f[k + 3], f[k + 4], f[k + 5], f[k + 6], f[k + 7], f[k + 8], OverlayTexture.NO_OVERLAY, b.l[i], f[k + 9], f[k + 10], f[k + 11]);
                }
                b.n = 0;
            }
            if (glintCount == 0) return;
            VertexConsumer v = buffers.getBuffer(GLINT);
            for (int i = 0; i < glintCount; i += 7)
                v.vertex(glints[i], glints[i + 1], glints[i + 2]).color(glints[i + 3], glints[i + 4], glints[i + 5], 1f).endVertex();
            glintCount = 0;
        }

        // ---- vertices
        private final Vector4f tp = new Vector4f();
        private final Vector3f tn = new Vector3f();
        /** The last face's corners in view space and their added light (for the gloss over it). */
        private final float[] qp = new float[12], qa = new float[4];
        private int qi;
        float texel() { return texel > 0 ? texel : autoTexel; }
        /**
         * One vertex of ice at (x, y, z) with its face's normal (unit), texture coordinates, material and an extra
         * opacity, on texture tex; keeps its light added on top for the face's gloss.
         */
        void vert(int tex, float x, float y, float z, float nx, float ny, float nz, float u, float v, Mat mat, float a) {
            float vx = cx - x, vy = cy - y, vz = cz - z;
            float len = Mth.sqrt(vx * vx + vy * vy + vz * vz);
            if (len < 1e-5f) len = 1e-5f;
            vx /= len; vy /= len; vz /= len;
            float dot = nx * vx + ny * vy + nz * vz;
            // The facet's normal turned to the eye (the shapes do not care which way their corners run; Minecraft's
            // light needs the side we see).
            float sn = dot < 0 ? -1 : 1;
            float fx = nx * sn, fy = ny * sn, fz = nz * sn;
            float rim = 1 - Math.abs(dot), rim2 = rim * rim;
            // The key light: above the camera, a little to its left, toward the scene.
            float lx = ux * .75f + vx * .5f - rx * .35f, ly = uy * .75f + vy * .5f - ry * .35f, lz = uz * .75f + vz * .5f - rz * .35f;
            float ll = Mth.sqrt(lx * lx + ly * ly + lz * lz);
            lx /= ll; ly /= ll; lz /= ll;
            float tone = Mth.clamp((fx * lx + fy * ly + fz * lz) * .5f + .5f, 0, 1);
            // The gloss: that light glancing off the face toward the eye (wet ice: a broad soft sheen and a hot core).
            float hx = lx + vx, hy = ly + vy, hz = lz + vz;
            float hl = Mth.sqrt(hx * hx + hy * hy + hz * hz);
            float nh = hl < 1e-5f ? 0 : Math.max(0, (fx * hx + fy * hy + fz * hz) / hl);
            float n2 = nh * nh, n4 = n2 * n2, n8 = n4 * n4;
            float gloss = (n8 * .35f + n8 * n8 * n4 * .9f) * mat.spec() * (.85f + .15f * Mth.sin(time * .35f + x * 2.1f + y * 1.3f + z * 1.7f));
            float r, g, b, add;
            if (mat.kind() == CLOTH_KIND) {
                float k = .7f + .3f * tone;
                r = mat.r() * k; g = mat.g() * k; b = mat.b() * k;
                add = gloss * .18f + rim2 * rim * mat.rim() * .06f;
            } else {
                // The texture is the ice; this only lights it softly and tints it.
                float k = .9f + .1f * smooth(.1f, .95f, tone);
                if (tex == T_SKIN) { r = mat.r(); g = mat.g(); b = mat.b(); }
                else if (tex == T_FROST) { r = Math.min(1, mat.r() / FR); g = Math.min(1, mat.g() / FG); b = Math.min(1, mat.b() / FB); }
                else { r = Math.min(1, mat.r() / IR); g = Math.min(1, mat.g() / IG); b = Math.min(1, mat.b() / IB); }
                if (tex != T_SKIN) { k *= facet; b = Math.min(1, b * (1 + (1 - facet) * .6f)); }
                r *= k; g *= k; b *= k;
                add = gloss * .55f + rim2 * rim * mat.rim() * .14f;
            }
            add += flash;
            float al = Mth.lerp(rim2, mat.clear(), mat.edge());
            al = Mth.clamp(al * a * alpha, 0, 1);
            if (al <= .003f) al = 0;
            tp.set(x, y, z, 1).mul(m);
            tn.set(fx, fy, fz).mul(n);
            batches[tex].put(tp.x, tp.y, tp.z, Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(b, 0, 1), al, u, v, tn.x, tn.y, tn.z, emissive ? FULL : light);
            qp[qi * 3] = tp.x; qp[qi * 3 + 1] = tp.y; qp[qi * 3 + 2] = tp.z;
            qa[qi] = add * al;
            qi = (qi + 1) & 3;
        }
        /** The gloss of the face just drawn (its four corners), added on top of it where there is any. */
        void gloss() {
            float top = Math.max(Math.max(qa[0], qa[1]), Math.max(qa[2], qa[3]));
            qi = 0;
            if (top < .015f) return;
            for (int i = 0; i < 4; i++) {
                float k = qa[i];
                raw(qp[i * 3], qp[i * 3 + 1], qp[i * 3 + 2], .82f * k, .93f * k, k);
            }
        }
        private static float smooth(float e0, float e1, float x) { float t = Mth.clamp((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t); }
        /** A light point (additive), in this space; gathered for end(). */
        void light(float x, float y, float z, float r, float g, float b) {
            tp.set(x, y, z, 1).mul(m);
            raw(tp.x, tp.y, tp.z, r, g, b);
        }
        private void raw(float x, float y, float z, float r, float g, float b) {
            if (glintCount + 7 > glints.length) glints = java.util.Arrays.copyOf(glints, glints.length * 2);
            glints[glintCount++] = x; glints[glintCount++] = y; glints[glintCount++] = z;
            glints[glintCount++] = Math.min(1, r); glints[glintCount++] = Math.min(1, g); glints[glintCount++] = Math.min(1, b);
            glintCount++;
        }
    }

    // ------------------------------------------------------------------ shapes
    private static final float[] N = new float[3];
    private static void normal(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz) {
        float ux = bx - ax, uy = by - ay, uz = bz - az, vx = cx - ax, vy = cy - ay, vz = cz - az;
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9f) { N[0] = 0; N[1] = 1; N[2] = 0; return; }
        N[0] = nx / l; N[1] = ny / l; N[2] = nz / l;
    }
    /** A flat four-cornered facet (its normal from its corners), the material's texture mapped on the side it faces. */
    public static void quad(Ctx c, float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz, float dx, float dy, float dz, Mat mat, float a) {
        normal(ax, ay, az, bx, by, bz, qx, qy, qz);
        float nx = N[0], ny = N[1], nz = N[2];
        int tex = mat.tex();
        if (mat.kind() == CLOTH_KIND) {
            // Plain colour: the frost texture's white texel.
            float w = .5f / TILE;
            c.vert(tex, ax, ay, az, nx, ny, nz, w, w, mat, a); c.vert(tex, bx, by, bz, nx, ny, nz, w, w, mat, a);
            c.vert(tex, qx, qy, qz, nx, ny, nz, w, w, mat, a); c.vert(tex, dx, dy, dz, nx, ny, nz, w, w, mat, a);
            c.gloss();
            return;
        }
        // Mapped like a block: from the side the face looks to (16 texels a block, 1 a pixel). Each face its own shade
        // (a little darker and bluer, or brighter, by which way it faces): broken facets, never one smooth surface.
        float k = c.texel() / TILE;
        float hn = (float) hash(nx * 17.3 + ny * 31.7 + nz * 7.9);
        c.facet = .86f + .18f * hn;
        float anx = Math.abs(nx), any = Math.abs(ny), anz = Math.abs(nz);
        int axis = any >= anx && any >= anz ? 1 : anx >= anz ? 0 : 2;
        c.vert(tex, ax, ay, az, nx, ny, nz, tu(c, axis, ax, az) * k, tv(c, axis, ay, az) * k, mat, a);
        c.vert(tex, bx, by, bz, nx, ny, nz, tu(c, axis, bx, bz) * k, tv(c, axis, by, bz) * k, mat, a);
        c.vert(tex, qx, qy, qz, nx, ny, nz, tu(c, axis, qx, qz) * k, tv(c, axis, qy, qz) * k, mat, a);
        c.vert(tex, dx, dy, dz, nx, ny, nz, tu(c, axis, dx, dz) * k, tv(c, axis, dy, dz) * k, mat, a);
        c.gloss();
        c.facet = 1;
    }
    private static float tu(Ctx c, int axis, float x, float z) { return axis == 0 ? z - c.oz : x - c.ox; }
    private static float tv(Ctx c, int axis, float y, float z) { return axis == 1 ? z - c.oz : y - c.oy; }

    /**
     * A box of his skin (skin.png): from (x0, y0, z0) to (x1, y1, z1) (model space: y down, -z his front, -x his right),
     * its texture the box at (u, v) of w x h x d texels in Minecraft's layout (top, bottom; right side, front, left side,
     * back), stretched over the box whatever its real size (a box may be grown over its texture, like a skin's second
     * layer). Texels with no alpha are not drawn.
     */
    public static void skinBox(Ctx c, float x0, float y0, float z0, float x1, float y1, float z1, int u, int v, int w, int h, int d, Mat mat, float a) {
        float s = 1 / SKIN_SIZE;
        float uL = u * s, uD = (u + d) * s, uW = (u + d + w) * s, uDW = (u + d + w + d) * s, uE = (u + 2 * d + 2 * w) * s, uWW = (u + d + 2 * w) * s;
        float vT = v * s, vD = (v + d) * s, vH = (v + d + h) * s;
        // Top (y0): front edge toward the front face. Bottom (y1).
        face(c, x0, y0, z0, uD, vD, x1, y0, z0, uW, vD, x1, y0, z1, uW, vT, x0, y0, z1, uD, vT, 0, -1, 0, mat, a);
        face(c, x0, y1, z1, uW, vT, x1, y1, z1, uWW, vT, x1, y1, z0, uWW, vD, x0, y1, z0, uW, vD, 0, 1, 0, mat, a);
        // His right (x0): back at the left of its texture, front at the right; the front (z0); his left (x1); the back (z1).
        face(c, x0, y0, z1, uL, vD, x0, y0, z0, uD, vD, x0, y1, z0, uD, vH, x0, y1, z1, uL, vH, -1, 0, 0, mat, a);
        face(c, x0, y0, z0, uD, vD, x1, y0, z0, uW, vD, x1, y1, z0, uW, vH, x0, y1, z0, uD, vH, 0, 0, -1, mat, a);
        face(c, x1, y0, z0, uW, vD, x1, y0, z1, uDW, vD, x1, y1, z1, uDW, vH, x1, y1, z0, uW, vH, 1, 0, 0, mat, a);
        face(c, x1, y0, z1, uDW, vD, x0, y0, z1, uE, vD, x0, y1, z1, uE, vH, x1, y1, z1, uDW, vH, 0, 0, 1, mat, a);
    }
    private static void face(Ctx c, float ax, float ay, float az, float au, float av, float bx, float by, float bz, float bu, float bv,
                             float qx, float qy, float qz, float qu, float qv, float dx, float dy, float dz, float du, float dv,
                             float nx, float ny, float nz, Mat mat, float a) {
        c.vert(T_SKIN, ax, ay, az, nx, ny, nz, au, av, mat, a); c.vert(T_SKIN, bx, by, bz, nx, ny, nz, bu, bv, mat, a);
        c.vert(T_SKIN, qx, qy, qz, nx, ny, nz, qu, qv, mat, a); c.vert(T_SKIN, dx, dy, dz, nx, ny, nz, du, dv, mat, a);
        c.gloss();
    }
    /** A three-cornered facet (as a quad with its last corner doubled). */
    public static void tri(Ctx c, float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz, Mat mat, float a) {
        quad(c, ax, ay, az, bx, by, bz, qx, qy, qz, qx, qy, qz, mat, a);
    }
    /** An axis-aligned box from (x0, y0, z0) to (x1, y1, z1). */
    public static void box(Ctx c, float x0, float y0, float z0, float x1, float y1, float z1, Mat mat, float a) {
        quad(c, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, mat, a);
        quad(c, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, mat, a);
        quad(c, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, mat, a);
        quad(c, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, mat, a);
        quad(c, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, mat, a);
        quad(c, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, mat, a);
    }
    /** A box centred on (x, y, z), of size (w, h, d). */
    public static void cube(Ctx c, float x, float y, float z, float w, float h, float d, Mat mat, float a) {
        box(c, x - w / 2, y - h / 2, z - d / 2, x + w / 2, y + h / 2, z + d / 2, mat, a);
    }
    public static void cube(Ctx c, float x, float y, float z, float w, float h, float d, Mat mat) { cube(c, x, y, z, w, h, d, mat, 1); }

    /**
     * The side of a faceted solid between two rings of points (each {x, y, z} n times, the same count, going round the
     * same way): one flat facet per pair of neighbours. capA / capB close the ends (a fan to the ring's middle).
     */
    public static void loft(Ctx c, float[] ra, float[] rb, Mat mat, float a, boolean capA, boolean capB) {
        int n = ra.length / 3;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            quad(c, ra[i * 3], ra[i * 3 + 1], ra[i * 3 + 2], ra[j * 3], ra[j * 3 + 1], ra[j * 3 + 2],
                    rb[j * 3], rb[j * 3 + 1], rb[j * 3 + 2], rb[i * 3], rb[i * 3 + 1], rb[i * 3 + 2], mat, a);
        }
        if (capA) cap(c, ra, mat, a, true);
        if (capB) cap(c, rb, mat, a, false);
    }
    /** Closes a ring: a fan from its middle (pushed out a little: a shallow facetted dome). */
    public static void cap(Ctx c, float[] ring, Mat mat, float a, boolean flip) {
        int n = ring.length / 3;
        float mx = 0, my = 0, mz = 0;
        for (int i = 0; i < n; i++) { mx += ring[i * 3]; my += ring[i * 3 + 1]; mz += ring[i * 3 + 2]; }
        mx /= n; my /= n; mz /= n;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            if (flip) tri(c, ring[j * 3], ring[j * 3 + 1], ring[j * 3 + 2], ring[i * 3], ring[i * 3 + 1], ring[i * 3 + 2], mx, my, mz, mat, a);
            else tri(c, ring[i * 3], ring[i * 3 + 1], ring[i * 3 + 2], ring[j * 3], ring[j * 3 + 1], ring[j * 3 + 2], mx, my, mz, mat, a);
        }
    }
    /** A ring of n points round (x, y, z) in the plane of the unit axes u and v, radii ru (along u) and rv (along v), jittered by seed (0 = none), turned by phase (radians). */
    public static float[] ring(float x, float y, float z, Vec3 u, Vec3 v, float ru, float rv, int n, float jitter, int seed, float phase) {
        float[] out = new float[n * 3];
        for (int i = 0; i < n; i++) {
            float ang = phase + Mth.TWO_PI * i / n;
            float j = jitter == 0 ? 1 : 1 + jitter * (float) (hash(seed * 31 + i * 7.13) - .5) * 2;
            float cu = Mth.cos(ang) * ru * j, sv = Mth.sin(ang) * rv * j;
            out[i * 3] = x + (float) (u.x * cu + v.x * sv);
            out[i * 3 + 1] = y + (float) (u.y * cu + v.y * sv);
            out[i * 3 + 2] = z + (float) (u.z * cu + v.z * sv);
        }
        return out;
    }
    /** A horizontal ring (in the x-z plane) at height y. */
    public static float[] hring(float x, float y, float z, float rx, float rz, int n, float jitter, int seed, float phase) {
        return ring(x, y, z, X, Z, rx, rz, n, jitter, seed, phase);
    }
    static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
    /** Two unit vectors at right angles to d (and to each other). */
    public static Vec3[] frame(Vec3 d) {
        Vec3 a = Math.abs(d.y) < .9 ? Y : X;
        Vec3 u = d.cross(a).normalize();
        Vec3 v = d.cross(u).normalize();
        return new Vec3[]{u, v};
    }

    /**
     * A tapered faceted limb from a (radius ra) to b (radius rb): a clear shell over a milky core, n facets round, each
     * ring a little uneven (seed). flatten squashes it front to back (1 = round).
     */
    public static void limb(Ctx c, Vec3 a, Vec3 b, float ra, float rb, int n, int seed, Mat shell, Mat core, float alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-8) return;
        Vec3[] f = frame(d.normalize());
        float[] r0 = ring((float) a.x, (float) a.y, (float) a.z, f[0], f[1], ra, ra, n, .12f, seed, seed * .7f);
        float[] r1 = ring((float) b.x, (float) b.y, (float) b.z, f[0], f[1], rb, rb, n, .12f, seed + 1, seed * .7f);
        if (core != null) {
            float[] c0 = ring((float) a.x, (float) a.y, (float) a.z, f[0], f[1], ra * .58f, ra * .58f, n, .2f, seed + 2, seed * .7f + .3f);
            float[] c1 = ring((float) b.x, (float) b.y, (float) b.z, f[0], f[1], rb * .58f, rb * .58f, n, .2f, seed + 3, seed * .7f + .3f);
            loft(c, c0, c1, core, alpha, true, true);
        }
        loft(c, r0, r1, shell, alpha, true, true);
    }

    /**
     * A crystal: a hexagonal (sides) prism from base along dir, closing to a point over its last third; radius r, length
     * len. Its axial edges are caught by light lines (glint, 0 = none).
     */
    public static void crystal(Ctx c, Vec3 base, Vec3 dir, float len, float r, int sides, int seed, Mat mat, float alpha, float glint) {
        if (len <= .001f || r <= .001f || alpha <= .003f) return;
        Vec3 d = dir.normalize();
        Vec3[] f = frame(d);
        float body = len * (.55f + .15f * (float) hash(seed * 3.1));
        Vec3 mid = base.add(d.scale(body)), tip = base.add(d.scale(len));
        float[] r0 = ring((float) base.x, (float) base.y, (float) base.z, f[0], f[1], r * .85f, r * .85f, sides, .15f, seed, seed);
        float[] r1 = ring((float) mid.x, (float) mid.y, (float) mid.z, f[0], f[1], r, r, sides, .15f, seed + 5, seed);
        loft(c, r0, r1, mat, alpha, true, false);
        // The point.
        float tx = (float) tip.x, ty = (float) tip.y, tz = (float) tip.z;
        for (int i = 0; i < sides; i++) {
            int j = (i + 1) % sides;
            tri(c, r1[i * 3], r1[i * 3 + 1], r1[i * 3 + 2], r1[j * 3], r1[j * 3 + 1], r1[j * 3 + 2], tx, ty, tz, mat, alpha);
        }
        if (glint > 0) {
            float w = Math.max(r * .07f, len * .012f);
            for (int i = 0; i < sides; i += 2)
                line(c, new Vec3(r1[i * 3], r1[i * 3 + 1], r1[i * 3 + 2]), tip, w, .55f * glint * alpha, .8f * glint * alpha, glint * alpha);
        }
    }
    /** An irregular shard (a flattened double point), centre at, long axis dir, size s; for breaks and debris. */
    public static void shard(Ctx c, Vec3 at, Vec3 dir, float s, int seed, Mat mat, float alpha) {
        if (s <= .001f || alpha <= .003f) return;
        Vec3 d = dir.lengthSqr() < 1e-8 ? Y : dir.normalize();
        Vec3[] f = frame(d);
        float a = s * (.8f + .5f * (float) hash(seed * 1.7)), b = s * (.45f + .4f * (float) hash(seed * 2.3)), w = s * (.25f + .3f * (float) hash(seed * 4.9));
        Vec3 top = at.add(d.scale(a)), bot = at.subtract(d.scale(b * 1.2f));
        Vec3[] mid = {at.add(f[0].scale(w)), at.add(f[1].scale(w * .55)), at.subtract(f[0].scale(w * .8)), at.subtract(f[1].scale(w * .6))};
        for (int i = 0; i < 4; i++) {
            Vec3 p = mid[i], q = mid[(i + 1) % 4];
            tri(c, (float) p.x, (float) p.y, (float) p.z, (float) q.x, (float) q.y, (float) q.z, (float) top.x, (float) top.y, (float) top.z, mat, alpha);
            tri(c, (float) q.x, (float) q.y, (float) q.z, (float) p.x, (float) p.y, (float) p.z, (float) bot.x, (float) bot.y, (float) bot.z, mat, alpha);
        }
    }

    /**
     * Ice grown along a path (the sculptures, the slide's track, icicles of any shape): rings round each point, turned
     * smoothly along the path (the frame carried from point to point), radius per point, n facets, uneven (seed).
     * Ends closed. Points and radii may be shorter than the arrays (count).
     */
    public static void tube(Ctx c, List<Vec3> path, float[] radius, int count, int n, int seed, Mat mat, float alpha) {
        if (count < 2) return;
        Vec3 t0 = path.get(1).subtract(path.get(0));
        if (t0.lengthSqr() < 1e-10) t0 = Y;
        Vec3[] f = frame(t0.normalize());
        Vec3 u = f[0];
        float[] prev = null;
        for (int i = 0; i < count; i++) {
            Vec3 p = path.get(i);
            Vec3 t = (i + 1 < count ? path.get(i + 1) : p).subtract(i > 0 ? path.get(i - 1) : p);
            if (t.lengthSqr() < 1e-10) t = t0; else t = t.normalize();
            // Carry the frame: take out of u its part along the new tangent.
            u = u.subtract(t.scale(u.dot(t)));
            if (u.lengthSqr() < 1e-8) u = frame(t)[0]; else u = u.normalize();
            Vec3 v = t.cross(u).normalize();
            float r = Math.max(.0005f, radius[i]);
            float[] ring = ring((float) p.x, (float) p.y, (float) p.z, u, v, r, r * .92f, n, .22f, seed + i * 13, 0);
            if (prev != null) loft(c, prev, ring, mat, alpha, i == 1, i == count - 1);
            prev = ring;
        }
    }

    /**
     * A crack in the ice glowing cyan (as in the reference art), from a to b on its surface; k = how bright.
     */
    public static void vein(Ctx c, Vec3 a, Vec3 b, float w, float k) {
        if (k <= .01f) return;
        // A glowing crack lying on the surface (the reference's cyan cracks), lifted a hair toward the eye so the solid
        // ice under it never hides it.
        Vec3 cam = new Vec3(c.cx, c.cy, c.cz);
        Vec3 ta = cam.subtract(a), tb = cam.subtract(b);
        if (ta.lengthSqr() < 1e-8 || tb.lengthSqr() < 1e-8) return;
        Vec3 la = a.add(ta.normalize().scale(w * 2.5)), lb = b.add(tb.normalize().scale(w * 2.5));
        line(c, la, lb, w, .25f * k, .75f * k, k);
    }

    // ------------------------------------------------------------------ light
    /** A thin bright line from a to b (additive; colour per end, alpha folded in), facing the camera; width w. */
    public static void line(Ctx c, Vec3 a, Vec3 b, float w, float r, float g, float bl) {
        line(c, a, b, w, r, g, bl, r, g, bl);
    }
    public static void line(Ctx c, Vec3 a, Vec3 b, float w, float r0, float g0, float b0, float r1, float g1, float b1) {
        float ax = (float) a.x, ay = (float) a.y, az = (float) a.z, bx = (float) b.x, by = (float) b.y, bz = (float) b.z;
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float vx = c.cx - ax, vy = c.cy - ay, vz = c.cz - az;
        float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-9f) return;
        sx *= w / sl; sy *= w / sl; sz *= w / sl;
        c.light(ax - sx, ay - sy, az - sz, r0, g0, b0); c.light(ax + sx, ay + sy, az + sz, r0, g0, b0);
        c.light(bx + sx, by + sy, bz + sz, r1, g1, b1); c.light(bx - sx, by - sy, bz - sz, r1, g1, b1);
    }
    /** A four-pointed sparkle at a point, facing the camera (size s, brightness k). */
    public static void sparkle(Ctx c, Vec3 at, float s, float k) {
        if (k <= .01f) return;
        Vec3 view = new Vec3(c.cx, c.cy, c.cz).subtract(at);
        if (view.lengthSqr() < 1e-8) return;
        Vec3[] f = frame(view.normalize());
        float r = .75f * k, g = .9f * k, b = k;
        for (int i = 0; i < 2; i++) {
            Vec3 u = i == 0 ? f[0] : f[1], v = i == 0 ? f[1] : f[0];
            Vec3 p0 = at.subtract(u.scale(s)), p1 = at.add(u.scale(s));
            Vec3 w0 = v.scale(s * .12);
            c.light((float) p0.x, (float) p0.y, (float) p0.z, 0, 0, 0);
            c.light((float) (at.x + w0.x), (float) (at.y + w0.y), (float) (at.z + w0.z), r, g, b);
            c.light((float) p1.x, (float) p1.y, (float) p1.z, 0, 0, 0);
            c.light((float) (at.x - w0.x), (float) (at.y - w0.y), (float) (at.z - w0.z), r, g, b);
        }
    }
    /** A soft glowing patch facing the camera (a glow on the eyes, a flash): r, g, b with brightness folded in. */
    public static void glow(Ctx c, Vec3 at, float s, float r, float g, float b) {
        Vec3 view = new Vec3(c.cx, c.cy, c.cz).subtract(at);
        if (view.lengthSqr() < 1e-8) return;
        Vec3[] f = frame(view.normalize());
        int n = 8;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            Vec3 p0 = at.add(f[0].scale(Mth.cos(a0) * s)).add(f[1].scale(Mth.sin(a0) * s));
            Vec3 p1 = at.add(f[0].scale(Mth.cos(a1) * s)).add(f[1].scale(Mth.sin(a1) * s));
            c.light((float) at.x, (float) at.y, (float) at.z, r, g, b);
            c.light((float) at.x, (float) at.y, (float) at.z, r, g, b);
            c.light((float) p1.x, (float) p1.y, (float) p1.z, 0, 0, 0);
            c.light((float) p0.x, (float) p0.y, (float) p0.z, 0, 0, 0);
        }
    }
    public static double hash(double n) { double x = Math.sin(n * 12.9898 + 78.233) * 43758.5453; return x - Math.floor(x); }
}
