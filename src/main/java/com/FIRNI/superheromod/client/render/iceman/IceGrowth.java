package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.iceman.IceMesh.Ctx;
import com.FIRNI.superheromod.client.render.iceman.IceMesh.Mat;
import net.minecraft.util.Mth;

/**
 * The shapes of REAL ice as extreme cold grows it (the user's Days of Future Past reference), the one geometry every
 * Iceman effect builds its ice from. Never a perfect cube, tube, ramp or a single clean point: each crystal is an uneven
 * prism of 5 to 7 sides (every side its own width), leaning, its top broken into a stepped ring of facets that closes to
 * a point off its axis; crystals interlock in CLUSTERS of different sizes, angles and growth times round a squat base
 * chunk; a SLAB is an irregular cross-section (a flat walkable top with ragged lips, bulging broken sides, a keel)
 * for paths; a CHIP is a broken-off piece. Everything takes a growth amount (0 nothing .. 1 grown): crystals widen
 * from a nucleus and push out, so a formation grows instead of appearing.
 * <p>
 * Formation timing: {@link #grow(float, float, float)} turns one formation clock (0..1+) into a crystal's own growth
 * with its delay and duration, eased; give every crystal its own (seeded) delay so they never grow in step.
 * Colour: blue ice (the material given), milky frost on some upper facets, white only as highlights (the material's
 * gloss and the frosted tips).
 * <p>
 * Seeds make every shape repeatable frame to frame (no flicker) and different from its neighbours. No allocation per
 * call (scratch arrays: render thread only).
 */
public final class IceGrowth {
    private IceGrowth() {}

    /** 0..1 from a seed and a salt (stable). */
    public static float h(int seed, int salt) {
        int x = seed * 0x27d4eb2d + salt * 0x165667b1;
        x ^= x >>> 15; x *= 0x2c1b3c6d; x ^= x >>> 12; x *= 0x297a2d39; x ^= x >>> 15;
        return (x & 0xffffff) / (float) 0x1000000;
    }
    /** A crystal's own growth from the formation's clock t: nothing before delay, then eased out over dur (0..1). */
    public static float grow(float t, float delay, float dur) {
        float k = Mth.clamp((t - delay) / Math.max(1e-4f, dur), 0, 1);
        float o = 1 - k;
        return 1 - o * o * o;
    }

    // ------------------------------------------------------------------ scratch
    private static final int MAXN = 7;
    private static final float[] R0 = new float[MAXN * 3], R1 = new float[MAXN * 3], R2 = new float[MAXN * 3], F = new float[6];

    /** Two unit vectors at right angles to (dx, dy, dz) (unit), into F[0..2] and F[3..5], turned by twist about it. */
    private static void frame(float dx, float dy, float dz, float twist) {
        float ax = Math.abs(dy) < .9f ? 0 : 1, ay = Math.abs(dy) < .9f ? 1 : 0;
        // u = d x a
        float ux = dy * 0 - dz * ay, uy = dz * ax - dx * 0, uz = dx * ay - dy * ax;
        float ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        // v = d x u
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        float cs = Mth.cos(twist), sn = Mth.sin(twist);
        F[0] = ux * cs + vx * sn; F[1] = uy * cs + vy * sn; F[2] = uz * cs + vz * sn;
        F[3] = vx * cs - ux * sn; F[4] = vy * cs - uy * sn; F[5] = vz * cs - uz * sn;
    }
    private static void ring(float[] out, int n, float cx, float cy, float cz, float r, float phase, int seed, int salt, float jitter) {
        for (int i = 0; i < n; i++) {
            float a = phase + Mth.TWO_PI * i / n + (h(seed, salt + i * 3) - .5f) * .5f / n * Mth.TWO_PI;
            float rr = r * (1 - jitter * .5f + jitter * h(seed, salt + i * 7 + 1));
            float cu = Mth.cos(a) * rr, sv = Mth.sin(a) * rr;
            out[i * 3] = cx + F[0] * cu + F[3] * sv;
            out[i * 3 + 1] = cy + F[1] * cu + F[4] * sv;
            out[i * 3 + 2] = cz + F[2] * cu + F[5] * sv;
        }
    }
    private static void band(Ctx c, float[] a, float[] b, int n, Mat mat, Mat alt, int seed, float alpha) {
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Mat m = alt != null && h(seed, 300 + i) < .35f ? alt : mat;
            IceMesh.quad(c, a[i * 3], a[i * 3 + 1], a[i * 3 + 2], a[j * 3], a[j * 3 + 1], a[j * 3 + 2],
                    b[j * 3], b[j * 3 + 1], b[j * 3 + 2], b[i * 3], b[i * 3 + 1], b[i * 3 + 2], m, alpha);
        }
    }

    // ------------------------------------------------------------------ the crystal
    /**
     * One crystal of ice grown g (0..1) from its base (bx, by, bz) along (dx, dy, dz): length len, half width r when
     * grown. An uneven prism (5..7 sides, each its own width), leaning, a stepped broken top of milky facets closing to
     * a point off the axis; the base closed (it is usually buried in the ice it grows from).
     */
    public static void crystal(Ctx c, double bx, double by, double bz, double dx, double dy, double dz, float len, float r, int seed, Mat mat, float g, float alpha) {
        if (g <= .01f || alpha <= .01f || len <= 1e-4f || r <= 1e-4f) return;
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-6f) return;
        float ex = (float) dx / dl, ey = (float) dy / dl, ez = (float) dz / dl;
        frame(ex, ey, ez, h(seed, 1) * Mth.TWO_PI);
        int n = 5 + (int) (h(seed, 2) * 2.99f);
        // It widens from a nucleus first, then pushes out.
        float L = len * (.18f + .82f * g), R = r * (.3f + .7f * Math.min(1, g * 1.5f));
        float x0 = (float) bx, y0 = (float) by, z0 = (float) bz;
        // The lean: the upper rings drift off the axis.
        float lu = (h(seed, 3) - .5f) * .5f * R, lv = (h(seed, 4) - .5f) * .5f * R;
        float h1 = L * (.42f + .2f * h(seed, 5)), h2 = L * (.7f + .12f * h(seed, 6));
        ring(R0, n, x0, y0, z0, R * (.82f + .14f * h(seed, 7)), 0, seed, 10, .35f);
        float k1 = .55f;
        ring(R1, n, x0 + ex * h1 + (F[0] * lu + F[3] * lv) * k1, y0 + ey * h1 + (F[1] * lu + F[4] * lv) * k1, z0 + ez * h1 + (F[2] * lu + F[5] * lv) * k1,
                R * (.95f + .2f * h(seed, 8)), 0, seed, 40, .4f);
        ring(R2, n, x0 + ex * h2 + F[0] * lu + F[3] * lv, y0 + ey * h2 + F[1] * lu + F[4] * lv, z0 + ez * h2 + F[2] * lu + F[5] * lv,
                R * (.5f + .2f * h(seed, 9)), Mth.PI / n, seed, 70, .5f);
        Mat frost = IceMesh.MILKY;
        band(c, R0, R1, n, mat, null, seed, alpha);
        band(c, R1, R2, n, mat, frost, seed + 17, alpha);
        // The top: a point well off the axis, at its own height; on some crystals snapped off instead (a slanted broken
        // face with a stub of a point standing on it): never a neat pencil tip.
        float off = 1.6f + 1.4f * h(seed, 12), top = L * (.86f + .14f * h(seed, 13));
        float ax = x0 + ex * top + F[0] * lu * off + F[3] * lv * off, ay = y0 + ey * top + F[1] * lu * off + F[4] * lv * off, az = z0 + ez * top + F[2] * lu * off + F[5] * lv * off;
        if (h(seed, 11) < .4f && R > len * .08f) {
            // The snapped top: each corner raised along the axis by its side of a slanted plane.
            float sx = Mth.cos(h(seed, 14) * Mth.TWO_PI), sy = Mth.sin(h(seed, 14) * Mth.TWO_PI), slope = (.15f + .25f * h(seed, 15)) * L;
            float cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < n; i++) {
                float px = R2[i * 3] - x0, py = R2[i * 3 + 1] - y0, pz = R2[i * 3 + 2] - z0;
                float u = (px * F[0] + py * F[1] + pz * F[2]) / Math.max(1e-5f, R), v = (px * F[3] + py * F[4] + pz * F[5]) / Math.max(1e-5f, R);
                float lift = slope * (.5f + .5f * (u * sx + v * sy)) * (.8f + .4f * h(seed, 600 + i));
                R1[i * 3] = R2[i * 3] + ex * lift; R1[i * 3 + 1] = R2[i * 3 + 1] + ey * lift; R1[i * 3 + 2] = R2[i * 3 + 2] + ez * lift;
                cx += R1[i * 3]; cy += R1[i * 3 + 1]; cz += R1[i * 3 + 2];
            }
            band(c, R2, R1, n, mat, frost, seed + 29, alpha);
            cx /= n; cy /= n; cz /= n;
            // The broken face (fresh, a little milky), and a stub of the point on it off centre.
            float kx = cx + (ax - cx) * .35f, ky = cy + (ay - cy) * .35f, kz = cz + (az - cz) * .35f;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                IceMesh.tri(c, R1[i * 3], R1[i * 3 + 1], R1[i * 3 + 2], R1[j * 3], R1[j * 3 + 1], R1[j * 3 + 2], kx, ky, kz, h(seed, 500 + i) < .6f ? frost : IceMesh.FRESH, alpha);
            }
        } else for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            IceMesh.tri(c, R2[i * 3], R2[i * 3 + 1], R2[i * 3 + 2], R2[j * 3], R2[j * 3 + 1], R2[j * 3 + 2], ax, ay, az, h(seed, 500 + i) < .5f ? frost : mat, alpha);
        }
        // The base, closed.
        float mx = x0 - ex * R * .3f, my = y0 - ey * R * .3f, mz = z0 - ez * R * .3f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            IceMesh.tri(c, R0[j * 3], R0[j * 3 + 1], R0[j * 3 + 2], R0[i * 3], R0[i * 3 + 1], R0[i * 3 + 2], mx, my, mz, mat, alpha);
        }
    }

    // ------------------------------------------------------------------ the cluster
    /**
     * A natural cluster of ice crystals grown out of a point (x, y, z) toward up (unit): a squat base chunk, one big
     * crystal (size tall), and count - 1 more round it, each its own size (some tall, some short), lean (some steep, some
     * nearly flat), turn and growth time; t is the formation's clock (0 nothing .. about 1 all grown; the last crystals
     * finish a little after 1). Size in the stack's units.
     */
    public static void cluster(Ctx c, double x, double y, double z, double upx, double upy, double upz, float size, int seed, int count, Mat mat, float t, float alpha) {
        if (t <= 0 || size <= 1e-4f || alpha <= .01f) return;
        float ul = (float) Math.sqrt(upx * upx + upy * upy + upz * upz);
        if (ul < 1e-6f) return;
        float ux = (float) upx / ul, uy = (float) upy / ul, uz = (float) upz / ul;
        // The base: a low lump of ice the crystals grow out of.
        crystal(c, x, y, z, ux + (h(seed, 1) - .5f) * .3f, uy, uz + (h(seed, 2) - .5f) * .3f, size * (.32f + .1f * h(seed, 3)), size * (.36f + .1f * h(seed, 4)),
                seed * 31 + 1, mat, grow(t, 0, .3f), alpha);
        // The big one, a little off upright.
        float tx = (h(seed, 5) - .5f) * .35f, tz = (h(seed, 6) - .5f) * .35f;
        crystal(c, x, y, z, ux + tx, uy, uz + tz, size * (1f + .3f * h(seed, 7)), size * (.2f + .07f * h(seed, 8)), seed * 31 + 2, mat, grow(t, .04f, .5f), alpha);
        // The rest round it.
        frame(ux, uy, uz, h(seed, 9) * Mth.TWO_PI);
        float px = F[0], py = F[1], pz = F[2], qx = F[3], qy = F[4], qz = F[5];
        for (int i = 2; i < count; i++) {
            float az = Mth.TWO_PI * i / Math.max(1, count - 2) + (h(seed, 20 + i) - .5f) * 1.6f;
            float tilt = .22f + .85f * h(seed, 40 + i);
            float hx = px * Mth.cos(az) + qx * Mth.sin(az), hy = py * Mth.cos(az) + qy * Mth.sin(az), hz = pz * Mth.cos(az) + qz * Mth.sin(az);
            float ct = Mth.cos(tilt), st = Mth.sin(tilt);
            float off = size * (.08f + .3f * h(seed, 60 + i));
            float len = size * (.28f + .62f * h(seed, 80 + i)) * (1 - .3f * tilt);
            float r = len * (.2f + .12f * h(seed, 100 + i));
            float delay = .06f + .5f * h(seed, 120 + i), dur = .25f + .35f * h(seed, 140 + i);
            crystal(c, x + hx * off, y + hy * off, z + hz * off, ux * ct + hx * st, uy * ct + hy * st, uz * ct + hz * st, len, r, seed * 31 + 3 + i,
                    h(seed, 160 + i) < .2f ? IceMesh.GLACIER : mat, grow(t, delay, dur), alpha);
        }
    }

    // ------------------------------------------------------------------ the slab (paths, walls)
    /** Points in a slab's cross-section (round it, the top first). */
    public static final int SECTION = 10;
    /**
     * A cross-section of a slab of ice at (x, y, z): its top surface's middle there, across along side (unit), up along
     * up (unit): a flat walkable top (only a few hundredths uneven) between two ragged lips, broken bulging sides, an
     * uneven keel thick below; width w, thickness th. The walkable top is the same for every seed; the rest wanders with
     * it. Into out (SECTION * 3 floats); grown g (0..1) thickens it from a thin skin.
     */
    public static float[] section(double x, double y, double z, double sx, double sy, double sz, double upx, double upy, double upz,
                                  float w, float th, int seed, float g, float[] out) {
        float hw = w * .5f, t = th * (.25f + .75f * g);
        // (across, up) pairs round the section, the top first, left to right.
        float lipL = .03f + .07f * h(seed, 1), lipR = .03f + .07f * h(seed, 2);
        float[] p = SEC;
        p[0] = -hw; p[1] = lipL * g;
        p[2] = -hw * .45f; p[3] = .015f * (h(seed, 3) - .5f);
        p[4] = 0; p[5] = 0;
        p[6] = hw * .45f; p[7] = .015f * (h(seed, 4) - .5f);
        p[8] = hw; p[9] = lipR * g;
        p[10] = hw * (1.02f + .14f * h(seed, 5)); p[11] = -t * (.38f + .2f * h(seed, 6));
        p[12] = hw * (.45f + .25f * h(seed, 7)); p[13] = -t * (.92f + .2f * h(seed, 8));
        p[14] = (h(seed, 9) - .5f) * hw * .3f; p[15] = -t * (1.12f + .3f * h(seed, 10));
        p[16] = -hw * (.45f + .25f * h(seed, 11)); p[17] = -t * (.9f + .22f * h(seed, 12));
        p[18] = -hw * (1.02f + .14f * h(seed, 13)); p[19] = -t * (.36f + .22f * h(seed, 14));
        for (int i = 0; i < SECTION; i++) {
            float a = p[i * 2], b = p[i * 2 + 1];
            out[i * 3] = (float) (x + sx * a + upx * b);
            out[i * 3 + 1] = (float) (y + sy * a + upy * b);
            out[i * 3 + 2] = (float) (z + sz * a + upz * b);
        }
        return out;
    }
    private static final float[] SEC = new float[SECTION * 2];
    /** The stretch of slab between two sections (as made by {@link #section}), the ends closed when asked. */
    public static void slab(Ctx c, float[] a, float[] b, Mat mat, int seed, float alpha, boolean closeA, boolean closeB) {
        for (int i = 0; i < SECTION; i++) {
            int j = (i + 1) % SECTION;
            // The underside and sides mix in deeper or milkier ice; the top stays the walkable blue.
            Mat m = i < 4 ? mat : h(seed, i) < .25f ? IceMesh.GLACIER : h(seed, i + 50) < .2f ? IceMesh.MILKY : mat;
            IceMesh.quad(c, a[i * 3], a[i * 3 + 1], a[i * 3 + 2], a[j * 3], a[j * 3 + 1], a[j * 3 + 2],
                    b[j * 3], b[j * 3 + 1], b[j * 3 + 2], b[i * 3], b[i * 3 + 1], b[i * 3 + 2], m, alpha);
        }
        if (closeA) IceMesh.cap(c, a, mat, alpha, true);
        if (closeB) IceMesh.cap(c, b, mat, alpha, false);
    }

    // ------------------------------------------------------------------ the chip (broken-off ice)
    /**
     * A broken-off piece of ice at (x, y, z) turned by the frame fr (u, v, w axes, 9 floats, as IceParticles.AX): an
     * uneven double point, longer along u, every corner its own; size = its length.
     */
    public static void chip(Ctx c, double x, double y, double z, float[] fr, float size, int seed, Mat mat, float alpha) {
        if (size <= 1e-4f || alpha <= .01f) return;
        float a = size * (.5f + .2f * h(seed, 1)), b = size * (.42f + .2f * h(seed, 2));
        float wv = size * (.22f + .14f * h(seed, 3)), ww = size * (.18f + .14f * h(seed, 4));
        float sh = (h(seed, 5) - .5f) * size * .25f;
        // The ring round its middle (4 corners, each its own), the two points.
        float[] r = CH;
        float[] o = {wv, 0, 0, ww, -wv * (.7f + .3f * h(seed, 6)), 0, 0, -ww * (.75f + .3f * h(seed, 7))};
        for (int i = 0; i < 4; i++) {
            float ov = o[i * 2], ow = o[i * 2 + 1], ou = (h(seed, 10 + i) - .5f) * size * .2f;
            r[i * 3] = (float) x + fr[0] * ou + fr[3] * ov + fr[6] * ow;
            r[i * 3 + 1] = (float) y + fr[1] * ou + fr[4] * ov + fr[7] * ow;
            r[i * 3 + 2] = (float) z + fr[2] * ou + fr[5] * ov + fr[8] * ow;
        }
        float tx = (float) x + fr[0] * a + fr[3] * sh, ty = (float) y + fr[1] * a + fr[4] * sh, tz = (float) z + fr[2] * a + fr[5] * sh;
        float bx = (float) x - fr[0] * b - fr[6] * sh, by = (float) y - fr[1] * b - fr[7] * sh, bz = (float) z - fr[2] * b - fr[8] * sh;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            IceMesh.tri(c, r[i * 3], r[i * 3 + 1], r[i * 3 + 2], r[j * 3], r[j * 3 + 1], r[j * 3 + 2], tx, ty, tz, i == 0 ? IceMesh.MILKY : mat, alpha);
            IceMesh.tri(c, r[j * 3], r[j * 3 + 1], r[j * 3 + 2], r[i * 3], r[i * 3 + 1], r[i * 3 + 2], bx, by, bz, mat, alpha);
        }
    }
    private static final float[] CH = new float[12];
}
