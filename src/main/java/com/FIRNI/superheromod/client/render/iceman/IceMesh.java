package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
 * Layered: every shape is drawn translucent, its colour and opacity worked out per vertex from where the camera is
 * (a view-dependent, Fresnel-like rim): faces seen straight on are clear and let the inside show through (an inner
 * milky core drawn under a clear shell reads as depth, a slight refraction feel); faces seen edge-on go bright and
 * opaque, so every facet's outline glows like the crystalline rim of real ice; a specular glint from above the camera
 * catches the facets as they turn. Materials ({@link Mat}) set the tint, how clear and how milky it is. Thin bright lines
 * (inner cracks, crystal edges) and sparkles are additive light, gathered while drawing and emitted at {@link Ctx#end()}.
 * <p>
 * Use: {@code Ctx c = IceMesh.begin(pose, buffers, light)}, then (after any change of the pose stack) {@code c.at(pose)},
 * the shapes, and {@code c.end()}. Coordinates are those of the pose stack (pixels if the caller scaled by 1/16).
 */
public final class IceMesh {
    private IceMesh() {}

    /** The ice itself (translucent, sorted, lit, depth-writing). */
    public static final RenderType ICE = RenderType.entityTranslucent(GhostMaterials.TEXTURE);
    /** Light: the eyes, the cracks' and the edges' glints (additive, full bright). */
    public static final RenderType GLINT = RenderType.eyes(GhostMaterials.TEXTURE);
    static final int FULL = 15728880;

    /**
     * A kind of ice: its tint (r, g, b), its opacity seen straight on (clear) and edge-on (edge), how milky-white it is
     * (milk), how much its rim brightens (rim), how strong its glint is (spec).
     */
    public record Mat(float r, float g, float b, float clear, float edge, float milk, float rim, float spec) {
        /** The same ice, more see-through (k < 1) or more solid (k > 1). */
        public Mat alpha(float k) { return new Mat(r, g, b, Math.min(1, clear * k), Math.min(1, edge * k), milk, rim, spec); }
        public Mat tint(float tr, float tg, float tb) { return new Mat(r * tr, g * tg, b * tb, clear, edge, milk, rim, spec); }
    }
    /** Clear ice: blue-tinted, see-through, a bright rim. */
    public static final Mat CLEAR = new Mat(.58f, .82f, 1f, .16f, .9f, .06f, .95f, 1f);
    /** Milky ice: whiter, cloudier. */
    public static final Mat MILKY = new Mat(.80f, .90f, 1f, .45f, .92f, .5f, .75f, .7f);
    /** The dense core inside a limb: nearly white, nearly solid (seen through the clear shell over it). */
    public static final Mat CORE = new Mat(.84f, .92f, 1f, .5f, .85f, .7f, .45f, .35f);
    /** Rime, frost: white, rough, matt. */
    public static final Mat FROST = new Mat(.94f, .97f, 1f, .7f, .95f, .9f, .3f, .25f);
    /** Thick deep ice: darker blue (eye sockets, the shadows of the face, old ice). */
    public static final Mat DEEP = new Mat(.32f, .56f, .86f, .42f, .9f, 0f, .7f, .9f);
    /** A fresh break: the inside, cleaner and brighter than the surface. */
    public static final Mat FRESH = new Mat(.9f, .98f, 1f, .5f, 1f, .35f, 1f, 1.2f);
    /** Glacier ice: thick, cold, a deeper blue body (the shell, the giant mace). */
    public static final Mat GLACIER = new Mat(.5f, .74f, .97f, .45f, .95f, .2f, .85f, .9f);

    // ------------------------------------------------------------------ the drawing context
    public static Ctx begin(PoseStack pose, MultiBufferSource buffers, int light) {
        Ctx c = new Ctx();
        c.buffers = buffers;
        c.light = light;
        c.at(pose);
        return c;
    }
    public static final class Ctx {
        MultiBufferSource buffers;
        VertexConsumer ice;
        Matrix4f m;
        Matrix3f n;
        public int light;
        /** Everything drawn is this much as opaque (fades), and this much brighter (a flash, 0 = as lit). */
        public float alpha = 1, flash;
        /** Time for the sparkle (ticks). */
        public float time;
        /** Full bright (lit by itself: the ice does not darken at night when false). */
        public boolean emissive;
        float cx, cy, cz, ux, uy, uz;
        private float[] glints = new float[256];
        private int glintCount;

        /** Takes the pose stack's current transform (call after every push / translate / rotate). */
        public Ctx at(PoseStack pose) {
            m = new Matrix4f(pose.last().pose());
            n = new Matrix3f(pose.last().normal());
            Matrix4f inv = new Matrix4f(m).invert();
            Vector3f cam = inv.transformPosition(new Vector3f());
            cx = cam.x; cy = cam.y; cz = cam.z;
            // The camera's up in this space (the glint's light comes from above the camera).
            Vector3f up = new Vector3f(n.m01(), n.m11(), n.m21());
            if (up.lengthSquared() < 1e-8f) up.set(0, 1, 0);
            up.normalize();
            ux = up.x; uy = up.y; uz = up.z;
            if (ice == null) ice = buffers.getBuffer(ICE);
            return this;
        }
        /** Where the camera is in this space. */
        public Vec3 camera() { return new Vec3(cx, cy, cz); }

        /** Emits the gathered light (cracks, edges, sparkles, eyes); the context cannot draw ice after this. */
        public void end() {
            if (glintCount == 0) return;
            VertexConsumer v = buffers.getBuffer(GLINT);
            for (int i = 0; i < glintCount; i += 7)
                v.vertex(glints[i], glints[i + 1], glints[i + 2], glints[i + 3], glints[i + 4], glints[i + 5], 1, .5f, .5f, OverlayTexture.NO_OVERLAY, FULL, 0, 1, 0);
            glintCount = 0;
            ice = null;
        }

        // ---- vertices
        private final Vector4f tp = new Vector4f();
        private final Vector3f tn = new Vector3f();
        /** One vertex of ice at (x, y, z) with its face's normal (unit), material and an extra opacity. */
        void vert(float x, float y, float z, float nx, float ny, float nz, Mat mat, float a) {
            float vx = cx - x, vy = cy - y, vz = cz - z;
            float len = Mth.sqrt(vx * vx + vy * vy + vz * vz);
            if (len < 1e-5f) len = 1e-5f;
            vx /= len; vy /= len; vz /= len;
            float dot = nx * vx + ny * vy + nz * vz;
            float facing = Math.abs(dot);
            float rim = 1 - facing, rim2 = rim * rim;
            // The glint: light from above and behind the camera, reflected by the facet toward the eye.
            float lx = ux * .8f + vx * .6f, ly = uy * .8f + vy * .6f, lz = uz * .8f + vz * .6f;
            float hx = lx + vx, hy = ly + vy, hz = lz + vz;
            float hl = Mth.sqrt(hx * hx + hy * hy + hz * hz);
            float sn = dot < 0 ? -1 : 1;
            float nh = hl < 1e-5f ? 0 : Math.max(0, sn * (nx * hx + ny * hy + nz * hz) / hl);
            float spec = nh * nh; spec *= spec; spec *= spec; spec *= spec * nh;   // ^17
            spec *= mat.spec() * (.75f + .25f * Mth.sin(time * .35f + x * 2.1f + y * 1.3f + z * 1.7f));
            float white = Mth.clamp(mat.milk() * .55f + rim2 * mat.rim() * .65f + spec + flash, 0, 1);
            float r = Mth.lerp(white, mat.r(), 1) + spec * .3f, g = Mth.lerp(white, mat.g(), 1) + spec * .3f, b = Mth.lerp(white, mat.b(), 1) + spec * .2f;
            float al = Mth.lerp(rim2, mat.clear(), mat.edge()) + spec * .5f;
            al = Mth.clamp(al * a * alpha, 0, 1);
            if (al <= .003f) al = 0;
            tp.set(x, y, z, 1).mul(m);
            tn.set(nx, ny, nz).mul(n);
            ice.vertex(tp.x, tp.y, tp.z, Math.min(1, r), Math.min(1, g), Math.min(1, b), al, .5f, .5f, OverlayTexture.NO_OVERLAY, emissive ? FULL : light, tn.x, tn.y, tn.z);
        }
        /** A light point (additive), in this space; gathered for end(). */
        void light(float x, float y, float z, float r, float g, float b) {
            if (glintCount + 7 > glints.length) glints = java.util.Arrays.copyOf(glints, glints.length * 2);
            tp.set(x, y, z, 1).mul(m);
            glints[glintCount++] = tp.x; glints[glintCount++] = tp.y; glints[glintCount++] = tp.z;
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
    /** A flat four-cornered facet (its normal from its corners). */
    public static void quad(Ctx c, float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz, float dx, float dy, float dz, Mat mat, float a) {
        normal(ax, ay, az, bx, by, bz, qx, qy, qz);
        float nx = N[0], ny = N[1], nz = N[2];
        c.vert(ax, ay, az, nx, ny, nz, mat, a); c.vert(bx, by, bz, nx, ny, nz, mat, a);
        c.vert(qx, qy, qz, nx, ny, nz, mat, a); c.vert(dx, dy, dz, nx, ny, nz, mat, a);
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
     * A vein of white inside the ice (an inner crack, a frozen-in fracture): a thin quad facing the camera, drawn WITH the
     * ice (sorted among its faces, so it shows through the clear surface over it, unlike light, which the surface's depth
     * would hide). k = how bright / opaque.
     */
    public static void vein(Ctx c, Vec3 a, Vec3 b, float w, float k) {
        float ax = (float) a.x, ay = (float) a.y, az = (float) a.z, bx = (float) b.x, by = (float) b.y, bz = (float) b.z;
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float vx = c.cx - ax, vy = c.cy - ay, vz = c.cz - az;
        float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
        float vl = Mth.sqrt(vx * vx + vy * vy + vz * vz);
        if (sl < 1e-9f || vl < 1e-9f) return;
        sx *= w / sl; sy *= w / sl; sz *= w / sl;
        float nx = vx / vl, ny = vy / vl, nz = vz / vl;
        c.vert(ax - sx, ay - sy, az - sz, nx, ny, nz, VEIN, k); c.vert(ax + sx, ay + sy, az + sz, nx, ny, nz, VEIN, k);
        c.vert(bx + sx, by + sy, bz + sz, nx, ny, nz, VEIN, k * .8f); c.vert(bx - sx, by - sy, bz - sz, nx, ny, nz, VEIN, k * .8f);
    }
    private static final Mat VEIN = new Mat(.92f, .97f, 1f, .75f, .75f, .6f, 0, 0);

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
