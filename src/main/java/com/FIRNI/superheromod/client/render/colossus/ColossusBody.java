package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.heroes.sandman.ColossusPose;
import org.joml.Matrix4f;

import static com.FIRNI.superheromod.client.render.colossus.ColossusShape.*;

/**
 * DRAWS THE SAND COLOSSUS from {@link ColossusShape}: the bones follow the shared {@link ColossusPose} (the
 * same curves the server uses for hits and crystal sockets), and every clump knows where it is in the build
 * and in the collapse.
 *
 * BUILD: the packed cores fill their volumes from the ground up while the slabs fly in on spiralling sand
 * streams from the ground round his base, decelerate and snap into place with a small overshoot, in order:
 * dune, torso, shoulders, forearms, hands, head.
 * COLLAPSE: head first, then the arms, the torso and the dune last, clumps tear away and fall, tumbling, hit
 * the ground and sink away while the cores pour down.
 *
 * Pure maths (JOML + ColossusPose): the vertices go to a {@link Sink}, so the same code can be previewed
 * offline. Nothing here allocates per frame.
 */
public final class ColossusBody {
    private ColossusBody() {}

    /** How long the collapse runs (ticks): the last clump tears away at DROP_SPAN, falls, then sinks for SETTLE. */
    public static final float DROP_SPAN = 30, SETTLE = 18, COLLAPSE_TICKS = 70;
    private static final float GRAVITY = .042f;

    /** Where the vertices go: Minecraft's buffer in game, a triangle list in an offline preview. */
    public interface Sink {
        void vertex(float x, float y, float z, float r, float g, float b, float u, float v, float nx, float ny, float nz, boolean glow);
    }

    /** One colossus' bones in "foot space" (blocks, origin at his feet, world axes), and which ones are drawn. */
    public static final class Skeleton {
        public final Matrix4f[] bone = new Matrix4f[BONES];
        public final boolean[] visible = new boolean[BONES];
        private final Matrix4f root = new Matrix4f();

        public Skeleton() {
            for (int b = 0; b < BONES; b++) bone[b] = new Matrix4f();
        }

        /** Same transforms as ModelPart / ColossusCrystal.socket: translate (base + pose offset) / 16, rotate Z, Y, X, scale. */
        public void set(ColossusPose p, float yaw, boolean maceRight) {
            root.identity().rotateY((float) Math.toRadians(180 - yaw)).scale(-1, -1, 1).translate(0, -10, 0);
            part(bone[LOWER].set(root), p.lowerMass, 0, 0, 0);
            part(bone[TORSO].set(root), p.torso, 0, 0, 0);
            part(bone[HEAD].set(bone[TORSO]), p.head, 0, 28, 0);
            part(bone[R_ARM].set(bone[TORSO]), p.rightArm, -28, 46, 0);
            part(bone[L_ARM].set(bone[TORSO]), p.leftArm, 28, 46, 0);
            part(bone[R_FORE].set(bone[R_ARM]), p.rightForearm, -8, 26, 0);
            part(bone[L_FORE].set(bone[L_ARM]), p.leftForearm, 8, 26, 0);
            part(bone[FIST].set(bone[maceRight ? R_FORE : L_FORE]), p.mace, 0, 26, 0);
            // The fist is modelled as a right fist (thumb inward); on the left forearm it is mirrored.
            if (!maceRight) bone[FIST].scale(-1, 1, 1);
            bone[R_HAND].set(bone[R_FORE]);
            bone[L_HAND].set(bone[L_FORE]);
            part(bone[SWORD].set(bone[R_ARM]), p.sword, 0, 0, 0);
            bone[R_CRAGS].set(bone[R_ARM]);
            bone[L_CRAGS].set(bone[L_ARM]);

            boolean torso = p.torso.visible;
            boolean ra = torso && p.rightArm.visible, la = torso && p.leftArm.visible;
            boolean rf = ra && p.rightForearm.visible, lf = la && p.leftForearm.visible;
            boolean fist = p.mace.visible && (maceRight ? rf : lf);
            visible[LOWER] = p.lowerMass.visible && !p.lowerMass.skipDraw;
            visible[TORSO] = torso && !p.torso.skipDraw;
            visible[HEAD] = torso && p.head.visible && !p.head.skipDraw;
            visible[R_ARM] = visible[R_CRAGS] = ra && !p.rightArm.skipDraw;
            visible[L_ARM] = visible[L_CRAGS] = la && !p.leftArm.skipDraw;
            visible[R_FORE] = rf && !p.rightForearm.skipDraw;
            visible[L_FORE] = lf && !p.leftForearm.skipDraw;
            visible[FIST] = fist && !p.mace.skipDraw;
            visible[R_HAND] = rf && !(fist && maceRight);
            visible[L_HAND] = lf && !(fist && !maceRight);
            visible[SWORD] = ra && p.sword.visible && !p.sword.skipDraw && p.sword.yScale > .02f;
        }

        public void copy(Skeleton other) {
            for (int b = 0; b < BONES; b++) {
                bone[b].set(other.bone[b]);
                visible[b] = other.visible[b];
            }
        }

        private static void part(Matrix4f m, ColossusPose.Part p, float x, float y, float z) {
            m.translate((x + p.x) / 16, (y + p.y) / 16, (z + p.z) / 16).rotateZ(p.zRot).rotateY(p.yRot).rotateX(p.xRot)
                    .scale(p.xScale, p.yScale, p.zScale);
        }
    }

    /** Per-clump state of one colossus this frame, read by the effects (landing puffs, lift-off dust, falling trails). */
    public static final class Frame {
        public static final byte HIDDEN = 0, FLYING = 1, SET = 2, FALLING = 3, LANDED = 4;
        /** Where the clump belongs (foot space). */
        public final float[] target = new float[N * 3];
        /** Where it is now (foot space). */
        public final float[] at = new float[N * 3];
        public final byte[] state = new byte[N];
        /** 0..1 through the flight (build) or ticks since tearing away / landing (collapse). */
        public final float[] phase = new float[N];
    }

    private static final Matrix4f[] VIEW_BONE = new Matrix4f[BONES];
    private static final Matrix4f M = new Matrix4f(), D = new Matrix4f();
    private static final float[] P = new float[24];
    static {
        for (int b = 0; b < BONES; b++) VIEW_BONE[b] = new Matrix4f();
    }

    /**
     * Draws one colossus.
     *
     * @param view      foot space to the render pose (the camera rotation and translate(feet - camera))
     * @param progress  formation 0..1 (1 = standing)
     * @param collapse  ticks since the collapse began, or a negative number while he stands
     * @param skip      a bone not to draw (his own head while his camera is in it), or -1
     */
    public static void draw(Sink out, Skeleton sk, Matrix4f view, float progress, float collapse, Frame frame, int skip) {
        for (int b = 0; b < BONES; b++) if (sk.visible[b]) VIEW_BONE[b].set(view).mul(sk.bone[b]);
        boolean building = collapse < 0 && progress < 1;
        for (int i = 0; i < N; i++) {
            int b = BONE[i];
            if (!sk.visible[b]) { frame.state[i] = Frame.HIDDEN; continue; }
            Matrix4f bone = sk.bone[b];
            int c3 = i * 3;
            float cx = CENTER[c3], cy = CENTER[c3 + 1], cz = CENTER[c3 + 2];
            float tx = bone.m00() * cx + bone.m10() * cy + bone.m20() * cz + bone.m30();
            float ty = bone.m01() * cx + bone.m11() * cy + bone.m21() * cz + bone.m31();
            float tz = bone.m02() * cx + bone.m12() * cy + bone.m22() * cz + bone.m32();
            frame.target[c3] = tx; frame.target[c3 + 1] = ty; frame.target[c3 + 2] = tz;
            frame.at[c3] = tx; frame.at[c3 + 1] = ty; frame.at[c3 + 2] = tz;
            boolean drawn = b != skip;

            if (KIND[i] == CORE) {
                // The packed inside: fills its volume (or pours away) instead of flying.
                float fill = 1;
                if (collapse >= 0) fill = 1 - clamp((collapse - DROP[i] * DROP_SPAN) / (DRAIN[i] * DROP_SPAN));
                else if (building) fill = smooth(clamp((progress - (ARRIVE[i] - FLIGHT[i])) / Math.max(1e-4f, FLIGHT[i])));
                frame.state[i] = fill > 0 ? Frame.SET : Frame.HIDDEN;
                frame.phase[i] = fill;
                if (fill <= 0 || !drawn) continue;
                corners(i, fill);
                emit(out, VIEW_BONE[b], i, 63);
                continue;
            }

            if (collapse >= 0) {
                float k = collapse - DROP[i] * DROP_SPAN;
                if (k < 0) {
                    frame.state[i] = Frame.SET;
                    if (drawn) { corners(i, 1); emit(out, VIEW_BONE[b], i, MASK[i]); }
                    continue;
                }
                if (!fall(i, bone, tx, ty, tz, k, frame)) { frame.state[i] = Frame.HIDDEN; continue; }
                if (drawn) { D.set(view).mul(M); corners(i, 1); emit(out, D, i, 63); }
                continue;
            }

            if (building && b != SWORD) {
                float s = (progress - (ARRIVE[i] - FLIGHT[i])) / FLIGHT[i];
                if (s <= 0) { frame.state[i] = Frame.HIDDEN; continue; }
                if (s < 1) {
                    fly(i, bone, tx, ty, tz, s, frame);
                    frame.state[i] = Frame.FLYING;
                    frame.phase[i] = s;
                    if (drawn) { D.set(view).mul(M); corners(i, 1); emit(out, D, i, 63); }
                    continue;
                }
                frame.phase[i] = s;
            } else frame.phase[i] = 2;
            frame.state[i] = Frame.SET;
            if (drawn) { corners(i, 1); emit(out, VIEW_BONE[b], i, MASK[i]); }
        }
    }

    /**
     * The flight in: lifted off the ground some way round the base, carried round and up the vortex, slowing
     * as it closes in, a small overshoot and back (the snap). Leaves the clump's matrix (foot space) in M.
     */
    private static void fly(int i, Matrix4f bone, float tx, float ty, float tz, float s, Frame frame) {
        float seed = SEED[i], seed2 = ColossusShape.hash(i * 7 + 3);
        float rT = (float) Math.sqrt(tx * tx + tz * tz), aT = (float) Math.atan2(tz, tx);
        // It leaves from one of the stream mouths round the base, the one about half a turn round from where it
        // belongs, so every clump curls the same way round the body on its way in.
        int mouth = streamFor(aT);
        float a0 = mouthAngle(mouth);
        float swirl = (float) ((a0 - aT) % (Math.PI * 2));
        if (swirl < 0) swirl += (float) (Math.PI * 2);
        float r0 = STREAM_RADIUS + .9f * seed2;
        // Fast round the vortex at first, slowing as it arrives; up and in on an ease with a little overshoot at the end.
        float turn = (float) Math.pow(1 - s, 1.7);
        float in = smooth(s) + .07f * (float) Math.sin(Math.PI * clamp((s - .62f) / .38f));
        float a = aT + swirl * turn;
        float r = r0 + (rT - r0) * in;
        float h = .15f + (ty - .15f) * Math.min(1.04f, in * (1 + .3f * (1 - s)));
        float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
        frame.at[i * 3] = x; frame.at[i * 3 + 1] = h; frame.at[i * 3 + 2] = z;
        float tumble = (1 - s) * (1 - s) * (4 + 3 * seed);
        float scale = .42f + .58f * smooth(s) + .09f * (float) Math.sin(Math.PI * clamp((s - .72f) / .28f));
        float ax = ColossusShape.hash(i * 5 + 1) - .5f, ay = ColossusShape.hash(i * 5 + 2) - .5f, az = ColossusShape.hash(i * 5 + 4) - .5f;
        float al = (float) Math.sqrt(ax * ax + ay * ay + az * az) + 1e-4f;
        M.set(bone).setTranslation(x, h, z).rotate(tumble, ax / al, ay / al, az / al).scale(scale)
                .translate(-CENTER[i * 3], -CENTER[i * 3 + 1], -CENTER[i * 3 + 2]);
    }

    /** The sand streams of the build rise from this many mouths round his base, this far out (blocks, foot space). */
    public static final int STREAMS = 6;
    public static final float STREAM_RADIUS = 5.6f;

    public static float mouthAngle(int k) { return (float) (k * Math.PI * 2 / STREAMS + .3); }

    /** The mouth a clump bound for angle a leaves from: about half a turn round. */
    public static int streamFor(float a) {
        double step = Math.PI * 2 / STREAMS;
        int k = (int) Math.round((a + Math.PI * 1.1 - .3) / step);
        return ((k % STREAMS) + STREAMS) % STREAMS;
    }

    /**
     * A point on a stream's representative spiral (foot space) at u = 0 (mouth) .. 1 (the build front at
     * height top, radius inner), for the effects to draw the stream along. Writes x, y, z into out.
     */
    public static void streamPoint(int k, float u, float top, float inner, float[] out) {
        float a0 = mouthAngle(k);
        float a = a0 - (float) (Math.PI * 1.1) * (1 - (float) Math.pow(1 - u, 1.7));
        float in = smooth(u);
        float r = STREAM_RADIUS + (inner - STREAM_RADIUS) * in;
        out[0] = (float) Math.cos(a) * r;
        out[1] = .1f + (top - .1f) * Math.min(1, in * (1 + .3f * (1 - u)));
        out[2] = (float) Math.sin(a) * r;
    }

    /**
     * The fall in the collapse: thrown a little out and up, tumbling, onto the ground (y = 0 in foot space),
     * then sinking into it. Leaves the matrix in M; false once it is gone.
     */
    private static boolean fall(int i, Matrix4f bone, float tx, float ty, float tz, float k, Frame frame) {
        float seed = SEED[i], seed2 = ColossusShape.hash(i * 7 + 3);
        float rh = (float) Math.sqrt(tx * tx + tz * tz);
        float ox = rh < 1e-3f ? 0 : tx / rh, oz = rh < 1e-3f ? 0 : tz / rh;
        float out = .05f + .08f * seed, up = .025f + .05f * seed2;
        float vx = ox * out - oz * .015f, vz = oz * out + ox * .015f;
        float rest = RADIUS[i] * .45f;
        // Time to reach the ground: ty + up*t - g/2 t^2 = rest.
        float drop = Math.max(0, ty - rest);
        float hit = (up + (float) Math.sqrt(up * up + 2 * GRAVITY * drop)) / GRAVITY;
        float landed = k - hit, t = Math.min(k, hit);
        float x = tx + vx * t, z = tz + vz * t, y = ty + up * t - .5f * GRAVITY * t * t;
        float scale = 1 - .18f * Math.min(1, t / 20);
        if (landed > 0) {
            float sink = landed / SETTLE;
            if (sink >= 1) return false;
            y = rest - sink * RADIUS[i] * .9f;
            scale *= 1 - sink * .6f;
            frame.state[i] = Frame.LANDED;
            frame.phase[i] = landed;
        } else {
            frame.state[i] = Frame.FALLING;
            frame.phase[i] = k;
        }
        frame.at[i * 3] = x; frame.at[i * 3 + 1] = y; frame.at[i * 3 + 2] = z;
        float spin = t * (.06f + .1f * seed);
        float ax = ColossusShape.hash(i * 5 + 1) - .5f, ay = ColossusShape.hash(i * 5 + 2) - .5f, az = ColossusShape.hash(i * 5 + 4) - .5f;
        float al = (float) Math.sqrt(ax * ax + ay * ay + az * az) + 1e-4f;
        M.set(bone).setTranslation(x, y, z).rotate(spin, ax / al, ay / al, az / al).scale(scale)
                .translate(-CENTER[i * 3], -CENTER[i * 3 + 1], -CENTER[i * 3 + 2]);
        return true;
    }

    /** Loads the clump's corners into P; a core's corners are clamped to its fill level. */
    private static void corners(int i, float fill) {
        System.arraycopy(CORNER, i * 24, P, 0, 24);
        if (KIND[i] != CORE || fill >= 1) return;
        float lo = YMIN[i], hi = YMAX[i];
        if (FILL[i] > 0) {
            float top = hi - (hi - lo) * fill;
            for (int k = 0; k < 8; k++) if (P[k * 3 + 1] < top) P[k * 3 + 1] = top;
        } else {
            float end = lo + (hi - lo) * fill;
            for (int k = 0; k < 8; k++) if (P[k * 3 + 1] > end) P[k * 3 + 1] = end;
        }
    }

    /** Emits the faces in the mask, transformed by m (normals by its rotation, renormalised). */
    private static void emit(Sink out, Matrix4f m, int i, int mask) {
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02(), m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22(), m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        boolean glow = KIND[i] == GLOW;
        for (int f = 0; f < 6; f++) {
            if ((mask & (1 << f)) == 0) continue;
            int n = i * 18 + f * 3;
            float lx = NORMAL[n], ly = NORMAL[n + 1], lz = NORMAL[n + 2];
            float nx = m00 * lx + m10 * ly + m20 * lz, ny = m01 * lx + m11 * ly + m21 * lz, nz = m02 * lx + m12 * ly + m22 * lz;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-6f) continue;
            nx /= nl; ny /= nl; nz /= nl;
            int[] fc = FACES[f];
            for (int k = 0; k < 4; k++) {
                int c = fc[k], p = c * 3, s = i * 24 + c * 3, uv = i * 48 + f * 8 + k * 2;
                float x = P[p], y = P[p + 1], z = P[p + 2];
                out.vertex(m00 * x + m10 * y + m20 * z + m30, m01 * x + m11 * y + m21 * z + m31, m02 * x + m12 * y + m22 * z + m32,
                        SHADE[s], SHADE[s + 1], SHADE[s + 2], UV[uv], UV[uv + 1], nx, ny, nz, glow);
            }
        }
    }

    static float clamp(float v) { return v < 0 ? 0 : Math.min(v, 1); }
    static float smooth(float t) { t = clamp(t); return t * t * (3 - 2 * t); }
}
