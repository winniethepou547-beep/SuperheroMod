package com.FIRNI.superheromod.client.render.colossus;

import org.joml.Matrix4f;

/**
 * THE COLOSSUS' CRYSTALS, alive: each weak point is a cluster of tapered six-sided amber shards.
 *
 *  - GROW: when the giant finishes forming, the shards push out of the sand one after another, each on an
 *    ease with a small overshoot (largest first).
 *  - BREATHE: the cluster turns very slowly round its own axis and swells and shrinks a few percent.
 *  - CATCH THE LIGHT: an additive copy of the facets brightens whichever facets face the viewer at the right
 *    angle (it changes as he turns, as the camera moves and as the cluster turns), with a slow twinkle on top;
 *    a soft glow sits inside the cluster and flickers once it is cracked.
 *  - SHATTER: a hit that cracks it throws off the shards it loses; breaking it bursts the rest
 *    ({@link Shards}, a fixed pool, analytic flight, no per-frame allocation).
 *
 * Pure maths: vertices go to {@link ColossusBody.Sink} (lit) or {@link Glow} (additive).
 */
public final class ColossusCrystals {
    private ColossusCrystals() {}

    /** Additive light: position (render space) and colour premultiplied by nothing, alpha separately. */
    public interface Glow {
        void vertex(float x, float y, float z, float r, float g, float b, float a);
    }

    /** Shards of one cluster in socket space (before scaling by the socket radius): direction xyz, length, width. */
    private static final float[][] SHARDS = {
            {0, -.35f, -1, 1, .22f}, {.85f, -.25f, -.45f, .78f, .19f},
            {-.8f, -.65f, -.35f, .92f, .18f}, {.25f, .65f, -.6f, .62f, .16f},
            {-.6f, .25f, -.65f, .58f, .15f}, {.1f, -1, -.1f, .55f, .14f}};
    /** Unit direction and the six radial unit vectors of each shard. */
    private static final float[] DIR = new float[18], RADIAL = new float[6 * 6 * 3];
    /** Precomputed outward normals of the 12 faces of each shard (fully grown). */
    private static final float[] NORMALS = new float[6 * 12 * 3];

    static {
        for (int j = 0; j < 6; j++) {
            float[] s = SHARDS[j];
            float l = (float) Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
            float dx = s[0] / l, dy = s[1] / l, dz = s[2] / l;
            DIR[j * 3] = dx; DIR[j * 3 + 1] = dy; DIR[j * 3 + 2] = dz;
            // u = dir x up, v = dir x u
            float ux = dy * 0 - dz * 1, uy = dz * 0 - dx * 0, uz = dx * 1 - dy * 0;
            float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (ul < 1e-4f) { ux = 1; uy = 0; uz = 0; ul = 1; }
            ux /= ul; uy /= ul; uz /= ul;
            float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3;
                float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                int o = (j * 6 + k) * 3;
                RADIAL[o] = ux * c + vx * sn; RADIAL[o + 1] = uy * c + vy * sn; RADIAL[o + 2] = uz * c + vz * sn;
            }
        }
        float[] q = new float[12];
        for (int j = 0; j < 6; j++) for (int f = 0; f < 12; f++) {
            face(j, f, 1, q);
            float ax = q[6] - q[0], ay = q[7] - q[1], az = q[8] - q[2], bx = q[9] - q[3], by = q[10] - q[4], bz = q[11] - q[5];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            // Outward: away from the shard's axis.
            float cx = (q[0] + q[3] + q[6] + q[9]) / 4, cy = (q[1] + q[4] + q[7] + q[10]) / 4, cz = (q[2] + q[5] + q[8] + q[11]) / 4;
            float along = cx * DIR[j * 3] + cy * DIR[j * 3 + 1] + cz * DIR[j * 3 + 2];
            float ox = cx - DIR[j * 3] * along, oy = cy - DIR[j * 3 + 1] * along, oz = cz - DIR[j * 3 + 2] * along;
            if (nx * ox + ny * oy + nz * oz < 0) { nx = -nx; ny = -ny; nz = -nz; }
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
            int o = (j * 12 + f) * 3;
            NORMALS[o] = nx / nl; NORMALS[o + 1] = ny / nl; NORMALS[o + 2] = nz / nl;
        }
    }

    /** Drawn size of a cluster relative to its hit radius (kept close so you aim at what you see). */
    public static final float SIZE = 1.3f;

    /** How many shards a cluster keeps at each damage state (0 whole, 1 cracked, 2 badly cracked). */
    public static int count(int damage) { return damage <= 0 ? 6 : damage == 1 ? 4 : damage == 2 ? 2 : 0; }

    /** Growth of shard j, age in ticks since its cluster began to grow: an ease out with a small overshoot. */
    public static float growth(int j, float age) {
        float t = ColossusBody.clamp((age - j * 1.4f) / 8f);
        if (t >= 1) return 1;
        float c1 = 1.5f, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    /** Cluster orientation per socket (each grows out of its own surface): the turn, then a slow spin about its axis. */
    public static void cluster(Matrix4f socket, int variant, float radius, float time, int damage, Matrix4f out) {
        out.set(socket);
        switch (variant) {
            case 4 -> out.rotateY((float) Math.PI);
            case 1 -> out.rotateY((float) Math.toRadians(48));
            case 2 -> out.rotateY((float) Math.toRadians(-52));
            case 3 -> out.rotateZ((float) Math.toRadians(32));
            default -> { }
        }
        float spin = time * .0075f + variant * 1.3f + .12f * (float) Math.sin(time * .021f + variant);
        float breathe = 1 + .035f * (float) Math.sin(time * .08f + variant * 1.7f);
        // A cracked crystal shivers.
        if (damage >= 1) breathe += (damage == 2 ? .025f : .012f) * (float) Math.sin(time * 2.3f + variant * 5);
        out.rotateZ(spin).scale(radius * breathe);
    }

    /** The lit body of a cluster, m = render pose x cluster. age = ticks since it began to grow. */
    public static void draw(ColossusBody.Sink out, Matrix4f m, int count, float age, float time) {
        float[] q = Q;
        for (int j = 0; j < count; j++) {
            float g = growth(j, age);
            if (g <= .01f) continue;
            for (int f = 0; f < 12; f++) {
                face(j, f, g, q);
                int o = (j * 12 + f) * 3;
                float nx = m.m00() * NORMALS[o] + m.m10() * NORMALS[o + 1] + m.m20() * NORMALS[o + 2];
                float ny = m.m01() * NORMALS[o] + m.m11() * NORMALS[o + 1] + m.m21() * NORMALS[o + 2];
                float nz = m.m02() * NORMALS[o] + m.m12() * NORMALS[o + 1] + m.m22() * NORMALS[o + 2];
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
                nx /= nl; ny /= nl; nz /= nl;
                // Facets alternate a little in tone; the ones turned to the viewer are brighter.
                float shade = .82f + (f % 3) * .06f + .12f * Math.max(0, nz);
                for (int k = 0; k < 4; k++) {
                    float x = q[k * 3], y = q[k * 3 + 1], z = q[k * 3 + 2];
                    out.vertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                            m.m02() * x + m.m12() * y + m.m22() * z + m.m32(), Math.min(1, shade), Math.min(1, shade * .96f), Math.min(1, shade * .9f),
                            k == 0 || k == 3 ? 0 : 1, k < 2 ? 1 : 0, nx, ny, nz, false);
                }
            }
        }
    }

    /**
     * The light the cluster catches: each facet adds amber-white light by how squarely it faces the viewer at
     * a glancing highlight (m is in view space, the camera looks down -z), plus a slow twinkle per facet.
     * Returns the brightest facet's centre in view space through peak (x, y, z, strength), for a star.
     */
    public static void glints(Glow out, Matrix4f m, int count, float age, float time, float strength, float[] peak) {
        float[] q = Q;
        peak[3] = 0;
        // Highlight half-vector in view space (light from above and to the side, viewer at +z).
        float hx = .32f, hy = .55f, hz = .77f;
        for (int j = 0; j < count; j++) {
            float g = growth(j, age);
            if (g <= .01f) continue;
            for (int f = 0; f < 12; f++) {
                face(j, f, g * 1.012f, q);
                int o = (j * 12 + f) * 3;
                float nx = m.m00() * NORMALS[o] + m.m10() * NORMALS[o + 1] + m.m20() * NORMALS[o + 2];
                float ny = m.m01() * NORMALS[o] + m.m11() * NORMALS[o + 1] + m.m21() * NORMALS[o + 2];
                float nz = m.m02() * NORMALS[o] + m.m12() * NORMALS[o + 1] + m.m22() * NORMALS[o + 2];
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
                float d = Math.max(0, (nx * hx + ny * hy + nz * hz) / nl);
                float spec = d * d; spec *= spec; spec *= spec; spec *= d * d;   // ^10
                float tw = (float) Math.sin(time * .19f + j * 1.7f + f * 2.9f);
                float twinkle = tw > .96f ? (tw - .96f) / .04f : 0;
                float a = strength * (.07f + .85f * spec + .55f * twinkle);
                if (a <= .01f) continue;
                float cx = 0, cy = 0, cz = 0;
                for (int k = 0; k < 4; k++) {
                    float x = q[k * 3], y = q[k * 3 + 1], z = q[k * 3 + 2];
                    float vx = m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), vy = m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                            vz = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
                    out.vertex(vx, vy, vz, 1, .80f, .50f, Math.min(1, a));
                    cx += vx; cy += vy; cz += vz;
                }
                if (a > peak[3] && f < 6) { peak[0] = cx / 4; peak[1] = cy / 4; peak[2] = cz / 4; peak[3] = a; }
            }
        }
    }

    /** The world position (render/foot space through m) of shard j's middle and its direction: for throwing it off. */
    public static void shardPoint(Matrix4f m, int j, float[] out) {
        float a = SHARDS[j][3] * .5f;
        float x = DIR[j * 3] * a, y = DIR[j * 3 + 1] * a, z = DIR[j * 3 + 2] * a;
        out[0] = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
        out[1] = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
        out[2] = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
        float dx = DIR[j * 3], dy = DIR[j * 3 + 1], dz = DIR[j * 3 + 2];
        float wx = m.m00() * dx + m.m10() * dy + m.m20() * dz, wy = m.m01() * dx + m.m11() * dy + m.m21() * dz, wz = m.m02() * dx + m.m12() * dy + m.m22() * dz;
        float l = (float) Math.sqrt(wx * wx + wy * wy + wz * wz) + 1e-6f;
        out[3] = wx / l; out[4] = wy / l; out[5] = wz / l;
        out[6] = SHARDS[j][3];
        out[7] = SHARDS[j][4];
    }

    private static final float[] Q = new float[12];

    /**
     * Quad f of shard j at growth g into q (4 corners xyz): faces 0..5 the lower band (base ring to the
     * widest ring), 6..11 the point (widest ring to the tip, the last corner repeated).
     */
    private static void face(int j, int f, float g, float[] q) {
        float[] s = SHARDS[j];
        float len = s[3] * g, w = s[4] * (.35f + .65f * Math.min(1, g));
        float dx = DIR[j * 3], dy = DIR[j * 3 + 1], dz = DIR[j * 3 + 2];
        int k = f % 6, k1 = (k + 1) % 6;
        int r0 = (j * 6 + k) * 3, r1 = (j * 6 + k1) * 3;
        if (f < 6) {
            float b = -.16f * Math.min(1, g), rb = w * .62f, mid = len * .6f;
            put(q, 0, dx * b + RADIAL[r0] * rb, dy * b + RADIAL[r0 + 1] * rb, dz * b + RADIAL[r0 + 2] * rb);
            put(q, 1, dx * b + RADIAL[r1] * rb, dy * b + RADIAL[r1 + 1] * rb, dz * b + RADIAL[r1 + 2] * rb);
            put(q, 2, dx * mid + RADIAL[r1] * w, dy * mid + RADIAL[r1 + 1] * w, dz * mid + RADIAL[r1 + 2] * w);
            put(q, 3, dx * mid + RADIAL[r0] * w, dy * mid + RADIAL[r0 + 1] * w, dz * mid + RADIAL[r0 + 2] * w);
        } else {
            float mid = len * .6f;
            put(q, 0, dx * mid + RADIAL[r0] * w, dy * mid + RADIAL[r0 + 1] * w, dz * mid + RADIAL[r0 + 2] * w);
            put(q, 1, dx * mid + RADIAL[r1] * w, dy * mid + RADIAL[r1 + 1] * w, dz * mid + RADIAL[r1 + 2] * w);
            put(q, 2, dx * len, dy * len, dz * len);
            put(q, 3, dx * len, dy * len, dz * len);
        }
    }

    private static void put(float[] q, int k, float x, float y, float z) { q[k * 3] = x; q[k * 3 + 1] = y; q[k * 3 + 2] = z; }

    // =====================================================================================================
    // SHATTERED SHARDS
    // =====================================================================================================

    /**
     * Flying crystal shards: a fixed ring buffer, each shard's flight computed from its launch (no state to
     * step, so it is smooth at any frame rate). They tumble, fall, lie on the ground a moment and shrink away.
     */
    public static final class Shards {
        public static final int CAPACITY = 160;
        public static final float LIFE = 46, GRAVITY = .045f;
        /** World launch point (double precision, absolute). */
        public final double[] x = new double[CAPACITY], y = new double[CAPACITY], z = new double[CAPACITY];
        public final float[] vx = new float[CAPACITY], vy = new float[CAPACITY], vz = new float[CAPACITY];
        public final float[] ground = new float[CAPACITY], born = new float[CAPACITY], len = new float[CAPACITY],
                width = new float[CAPACITY], spin = new float[CAPACITY], seed = new float[CAPACITY];
        private int next, live;

        public boolean empty() { return live == 0; }

        public void clear() { live = 0; for (int i = 0; i < CAPACITY; i++) born[i] = -1e9f; }

        public Shards() { clear(); }

        public void spawn(double px, double py, double pz, float dx, float dy, float dz, float speed, float length, float w,
                          double groundY, float now, int s) {
            int i = next;
            next = (next + 1) % CAPACITY;
            live = Math.min(CAPACITY, live + 1);
            x[i] = px; y[i] = py; z[i] = pz;
            float rx = ColossusShape.hash(s * 3 + 1) - .5f, ry = ColossusShape.hash(s * 3 + 2), rz = ColossusShape.hash(s * 3 + 3) - .5f;
            vx[i] = dx * speed + rx * .12f;
            vy[i] = dy * speed + .1f + ry * .12f;
            vz[i] = dz * speed + rz * .12f;
            ground[i] = (float) (groundY - py);
            born[i] = now; len[i] = length; width[i] = w;
            spin[i] = .25f + .35f * ColossusShape.hash(s * 7);
            seed[i] = ColossusShape.hash(s * 11 + 5);
        }

        /** Age of shard i, or a negative number if it is not flying. */
        public float age(int i, float now) {
            float a = now - born[i];
            return a < 0 || a > LIFE ? -1 : a;
        }

        /** Offset from the launch point at age a (relative xyz, ground contact handled) and its size factor into out (x, y, z, size, landed). */
        public void at(int i, float a, float[] out) {
            // Ground hit: y(t) = vy t - g/2 t^2 = ground (ground below 0).
            float g = ground[i];
            float hit = (vy[i] + (float) Math.sqrt(Math.max(0, vy[i] * vy[i] - 2 * GRAVITY * g))) / GRAVITY;
            float t = Math.min(a, hit);
            out[0] = vx[i] * t;
            out[1] = vy[i] * t - .5f * GRAVITY * t * t;
            out[2] = vz[i] * t;
            out[3] = 1 - ColossusBody.clamp((a - (LIFE - 14)) / 14f);
            out[4] = a > hit ? 1 : 0;
            out[5] = t;
        }

        /** The lit shard (a long six-sided double point) into the sink; m = render pose at the shard, axes already turned. */
        public static void draw(ColossusBody.Sink out, Matrix4f m) {
            for (int k = 0; k < 6; k++) {
                int r0 = k * 3, r1 = ((k + 1) % 6) * 3;
                for (int half = 0; half < 2; half++) {
                    float ty = half == 0 ? 1 : -.55f;
                    float ax = RING[r0], az = RING[r0 + 2], bx = RING[r1], bz = RING[r1 + 2];
                    // Normal: outward and toward the tip.
                    float nx0 = (ax + bx) * .5f, nz0 = (az + bz) * .5f, ny0 = half == 0 ? .35f : -.5f;
                    float nx = m.m00() * nx0 + m.m10() * ny0 + m.m20() * nz0, ny = m.m01() * nx0 + m.m11() * ny0 + m.m21() * nz0,
                            nz = m.m02() * nx0 + m.m12() * ny0 + m.m22() * nz0;
                    float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
                    nx /= nl; ny /= nl; nz /= nl;
                    float shade = .85f + .15f * (k % 2);
                    vertex(out, m, ax, 0, az, shade, 0, 1, nx, ny, nz);
                    vertex(out, m, bx, 0, bz, shade, 1, 1, nx, ny, nz);
                    vertex(out, m, 0, ty, 0, shade, 1, 0, nx, ny, nz);
                    vertex(out, m, 0, ty, 0, shade, 0, 0, nx, ny, nz);
                }
            }
        }

        private static final float[] RING = new float[18];
        static {
            for (int k = 0; k < 6; k++) {
                RING[k * 3] = (float) Math.cos(k * Math.PI / 3);
                RING[k * 3 + 2] = (float) Math.sin(k * Math.PI / 3);
            }
        }

        private static void vertex(ColossusBody.Sink out, Matrix4f m, float x, float y, float z, float shade, float u, float v,
                                   float nx, float ny, float nz) {
            out.vertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                    m.m02() * x + m.m12() * y + m.m22() * z + m.m32(), shade, shade * .95f, shade * .88f, u, v, nx, ny, nz, false);
        }
    }
}
