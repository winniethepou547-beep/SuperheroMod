package com.FIRNI.superheromod.client.render.colossus;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * THE SAND COLOSSUS AS PACKED SAND — pure shape data, no Minecraft classes.
 *
 * Every bone is a dark, damp CORE (the packed inside of the dune) covered in SLABS and CLUMPS. A slab is
 * not a flat box: its outer face is smaller than its inner one and every corner is nudged, so each piece
 * reads as a chunk with depth. The slabs leave narrow gaps where the dark core shows through: those are
 * the creases. Corners turned toward the core are darker than corners facing out (cheap ambient occlusion),
 * and the whole giant gets darker and warmer toward the ground.
 *
 * Coordinates are authored in model pixels exactly like the old rig (16 px = 1 block, y DOWN, -z = front,
 * -x = his right) and stored in blocks. The joints are the gameplay joints of ColossusPose/ColossusCrystal
 * (shoulders at (+-28, 46), elbows 26 px further, the hand point 26 px down the forearm), so hits, the slam
 * point and the crystal sockets stay exactly where the server expects them.
 *
 * Built once at class load; ColossusBody reads the flat arrays every frame without allocating.
 */
public final class ColossusShape {
    private ColossusShape() {}

    // ---- bones (each drawn with its own matrix, see ColossusBody.skeleton)
    public static final int LOWER = 0, TORSO = 1, HEAD = 2, R_ARM = 3, L_ARM = 4, R_FORE = 5, L_FORE = 6,
            FIST = 7, R_HAND = 8, L_HAND = 9, SWORD = 10, R_CRAGS = 11, L_CRAGS = 12, BONES = 13;
    public static final int PLATE = 0, CORE = 1, GLOW = 2;

    /** Corner indices of the six faces (corner bit 0 = +x, bit 1 = +y, bit 2 = +z), each a closed loop: -x, +x, -y, +y, -z, +z. */
    public static final int[][] FACES = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
    private static final int NX = 0, PX = 1, NY = 2, PY = 3, NZ = 4, PZ = 5, NONE = -1;

    // ---- sand colours, multiplied onto the vanilla sand texture
    private static final float[] LIGHT = {1.00f, .98f, .93f}, MID = {.93f, .88f, .78f}, DUSK = {.82f, .75f, .62f},
            CRAG = {.88f, .81f, .67f}, DAMP = {.66f, .57f, .44f}, EDGE = {1.00f, 1.00f, .96f}, EYE = {1.00f, .62f, .22f};

    // ---- the flat arrays
    public static final int N;
    public static final byte[] BONE, KIND, MASK, FILL;
    /** Centre and corners in blocks, bone space. */
    public static final float[] CENTER, CORNER, RADIUS;
    /** Face normals (unit, bone space), face uvs (4 corners each) and per-corner colour (rgb). */
    public static final float[] NORMAL, UV, SHADE;
    /** Formation: progress at which the clump lands and how long its flight is (progress units); cores fill over [ARRIVE - FLIGHT, ARRIVE]. */
    public static final float[] ARRIVE, FLIGHT;
    /** Dissolve: 0 = tears away first, 1 = last (fraction of the collapse); cores pour away over [DROP, DROP + DRAIN]. */
    public static final float[] DROP, DRAIN;
    public static final float[] SEED;
    /** Cores: local y range (blocks) the fill runs over. */
    public static final float[] YMIN, YMAX;

    /** Where loose sand keeps trickling off while he stands: bone + point (blocks). */
    public static final int EMITTERS;
    public static final byte[] EMIT_BONE;
    public static final float[] EMIT_POS;
    /** The two eye slits (HEAD bone, blocks). */
    public static final float[] EYES = {-5 / 16f, -3.6f / 16f, -8.9f / 16f, 5 / 16f, -3.6f / 16f, -8.9f / 16f};
    /** The palm centre of each open hand (forearm space, blocks): dust when the palms slam into the ground. */
    public static final float[] PALM = {0, 30 / 16f, 2 / 16f};

    /** Rest offsets of each bone in root space (px), for ordering the build and the collapse. */
    private static final float[][] REST = {{0, 0, 0}, {0, 0, 0}, {0, 28, 0}, {-28, 46, 0}, {28, 46, 0}, {-36, 72, 0}, {36, 72, 0},
            {-36, 98, 0}, {-36, 72, 0}, {36, 72, 0}, {-28, 46, 0}, {-28, 46, 0}, {28, 46, 0}};
    /** Each bone's core axis (px, bone space: two points), for the occlusion shading. */
    private static final float[][] AXIS = {{0, 92, 0, 0, 160, 0}, {0, 30, 0, 0, 104, 0}, {0, -20, 1, 0, 2, 1},
            {-10, -6, 0, -9, 26, 0}, {10, -6, 0, 9, 26, 0}, {0, 0, 0, 0, 24, 0}, {0, 0, 0, 0, 24, 0},
            {0, -3, 0, 0, 12, 0}, {0, 24, 0, 0, 42, 1}, {0, 24, 0, 0, 42, 1}, {0, -4, 0, 0, 118, 0},
            {-10, -6, 0, -9, 26, 0}, {10, -6, 0, 9, 26, 0}};

    private static final class Clump {
        int bone, kind, out, seed;
        boolean solid;
        float inset, jitter;
        float[] c, h, tint;
        Quaternionf q;
        final float[] p = new float[24];
    }

    private static final List<Clump> list = new ArrayList<>();
    private static final List<float[]> emit = new ArrayList<>();
    private static int seeds = 1;

    static {
        torso();
        head();
        arm(false);
        arm(true);
        hands();
        fist();
        sword();
        lower();
        emitters();

        N = list.size();
        BONE = new byte[N]; KIND = new byte[N]; MASK = new byte[N]; FILL = new byte[N];
        CENTER = new float[N * 3]; CORNER = new float[N * 24]; RADIUS = new float[N];
        NORMAL = new float[N * 18]; UV = new float[N * 48]; SHADE = new float[N * 24];
        ARRIVE = new float[N]; FLIGHT = new float[N]; DROP = new float[N]; DRAIN = new float[N]; SEED = new float[N];
        YMIN = new float[N]; YMAX = new float[N];
        for (int i = 0; i < N; i++) bake(i, list.get(i));
        EMITTERS = emit.size();
        EMIT_BONE = new byte[EMITTERS];
        EMIT_POS = new float[EMITTERS * 3];
        for (int e = 0; e < EMITTERS; e++) {
            float[] v = emit.get(e);
            EMIT_BONE[e] = (byte) v[0];
            EMIT_POS[e * 3] = v[1] / 16; EMIT_POS[e * 3 + 1] = v[2] / 16; EMIT_POS[e * 3 + 2] = v[3] / 16;
        }
        list.clear();
        emit.clear();
    }

    // =====================================================================================================
    // AUTHORING (pixels)
    // =====================================================================================================

    /** The chest, back and belly. Broad, V-shaped: wide lats under the arms, a narrow waist sinking into the dune. */
    private static void torso() {
        int b = TORSO;
        core(b, -24, 32, -13, 24, 70, 13);
        core(b, -19, 66, -11.5f, 19, 92, 11.5f);
        core(b, -15, 88, -10.5f, 15, 112, 10.5f);
        core(b, -9, 22, -7.5f, 9, 40, 7.5f);
        for (int s = -1; s <= 1; s += 2) {
            int side = s < 0 ? NX : PX;
            // Trapezius: high, heavy slopes beside the neck, so the head sits down between the shoulders.
            slab(b, s * 14.5f, 29, 1, 11.5f, 5.5f, 10, 0, 0, s * .42f, NY, .2f, LIGHT);
            slab(b, s * 8.5f, 24.5f, 2, 4.6f, 4.6f, 7, 0, 0, s * .3f, NY, .25f, MID);
            // Pectorals: a big upper slab and a smaller lower one; the gap at the sternum carries the chest crystal.
            slab(b, s * 12.6f, 44, -15.2f, 11.6f, 7.2f, 3.4f, 0, s * -.14f, s * -.04f, NZ, .2f, LIGHT);
            slab(b, s * 11.8f, 56, -14.4f, 10.4f, 5, 3, .08f, s * -.10f, 0, NZ, .24f, MID);
            drip(b, s * 15, 61, -15.2f, 2.2f, 5);
            drip(b, s * 7.5f, 60.5f, -14.8f, 1.7f, 3.6f);
            // Collarbones bridging to the shoulders.
            slab(b, s * 13, 36.5f, -12, 10, 2.4f, 2.6f, 0, 0, s * .16f, NZ, .2f, EDGE);
            // Lats: the widest part of the torso, under the arms.
            slab(b, s * 24.5f, 47, 3, 3.8f, 12, 9, 0, 0, s * -.05f, side, .2f, MID);
            drip(b, s * 25.5f, 59.5f, 4, 2.3f, 5.5f);
            // Flanks: two big slanted clumps.
            slab(b, s * 20.5f, 63, -5, 3.6f, 7.5f, 6.5f, .1f, s * .45f, s * -.1f, side, .25f, MID);
            slab(b, s * 18.2f, 80, -3, 3.4f, 7, 7.6f, -.08f, s * .3f, s * -.08f, side, .25f, DUSK);
            // Upper belly: two slabs angled toward each other.
            slab(b, s * 8.2f, 68.5f, -12.7f, 7.8f, 5.6f, 2.8f, .05f, s * -.08f, s * .06f, NZ, .26f, MID);
            // Back: upper, middle (the back crystal sits between these two), lower.
            slab(b, s * 11.5f, 43, 14.6f, 10.5f, 8, 3, -.05f, s * .12f, 0, PZ, .2f, LIGHT);
            slab(b, s * 10, 59, 13.6f, 8.6f, 6, 3, 0, s * .08f, 0, PZ, .22f, MID);
            slab(b, s * 9, 75, 12.6f, 8, 7, 2.6f, .06f, 0, 0, PZ, .24f, DUSK);
            // Hips: the sash of sand where the torso sinks into the dune.
            slab(b, s * 14.8f, 99, 0, 2.6f, 7, 10, 0, 0, s * .06f, side, .2f, DUSK);
            slab(b, s * 11, 104, -9.6f, 5, 5, 2.4f, 0, s * -.4f, 0, NZ, .3f, DUSK);
            slab(b, s * 11, 104, 9.6f, 5, 5, 2.4f, 0, s * .4f, 0, PZ, .3f, DUSK);
        }
        slab(b, 0, 80, -12.3f, 13, 5.4f, 2.6f, .04f, 0, 0, NZ, .25f, MID);
        slab(b, 0, 91.5f, -11.6f, 11.5f, 4.6f, 2.3f, 0, 0, 0, NZ, .25f, DUSK);
        slab(b, 0, 100, -11.4f, 10, 5, 2.2f, 0, 0, 0, NZ, .2f, DUSK);
        slab(b, 0, 91, 11.6f, 12, 6, 2.2f, 0, 0, 0, PZ, .2f, DUSK);
        slab(b, 0, 103, 10.8f, 9, 5, 2.2f, 0, 0, 0, PZ, .2f, DUSK);
        slab(b, 0, 32, -8.4f, 6.5f, 5, 2, 0, 0, 0, NZ, .25f, MID);
        // A few clods of sand stuck on the big slabs: the surface should never look machined.
        clods(b, 26);
    }

    /** A brute's head that reads from far away: a rounded skull, one heavy brow with the crystal in it, deep dark sockets, a jaw. */
    private static void head() {
        int b = HEAD;
        core(b, -10, -25, -9, 10, 4, 9.5f);
        coreY(b, -8.6f, -24, -8.6f, 8.6f, 2, 8.6f, (float) (Math.PI / 4));
        // A rounded dome: a crown slab and four slabs sloping off it.
        slab(b, 0, -25.6f, .5f, 8, 3, 7.6f, 0, .1f, 0, NY, .3f, LIGHT);
        slab(b, 0, -21.5f, -6.6f, 9.4f, 3.2f, 4, -.5f, 0, 0, NY, .3f, MID);
        slab(b, 0, -21.5f, 7.8f, 9.4f, 3.2f, 4, .5f, 0, 0, NY, .3f, MID);
        slab(b, 0, -12, 10.4f, 9.8f, 9.5f, 2.4f, .06f, 0, 0, PZ, .22f, MID);
        slab(b, 0, -15, -10.4f, 8.8f, 4.4f, 2, -.22f, 0, 0, NZ, .25f, LIGHT);
        // The brow: one heavy ledge across; the head crystal grows from its middle. Its ends drop: a frown.
        slab(b, 0, -8.6f, -11.8f, 11, 2.2f, 2.8f, 0, 0, 0, NZ, .18f, EDGE);
        for (int s = -1; s <= 1; s += 2) {
            int side = s < 0 ? NX : PX;
            slab(b, s * 11.2f, -13, 0, 2.2f, 8, 8.6f, 0, 0, s * -.05f, side, .22f, MID);
            slab(b, s * 8.4f, -21.5f, .5f, 3.6f, 3.2f, 8.4f, 0, 0, s * -.55f, NY, .3f, LIGHT);
            slab(b, s * 9.4f, -7.4f, -10.4f, 3, 1.8f, 2.2f, 0, s * .2f, s * .35f, NZ, .25f, LIGHT);
            // Cheekbones, and the sides of the jaw.
            slab(b, s * 7.6f, -.4f, -10, 3.4f, 2.2f, 2.2f, 0, s * .25f, 0, NZ, .3f, LIGHT);
            slab(b, s * 9, 2.6f, -1.5f, 2.4f, 4, 7, 0, 0, s * .1f, side, .25f, DUSK);
        }
        slab(b, 0, -2.8f, -11, 2, 3.8f, 2, .12f, 0, 0, NZ, .3f, MID);
        // Jaw and chin, jutting forward.
        slab(b, 0, 5, -4.5f, 9.4f, 3.2f, 7, -.06f, 0, 0, PY, .2f, DUSK);
        slab(b, 0, 4.4f, -10.8f, 5.6f, 3, 1.8f, 0, 0, 0, NZ, .3f, MID);
        drip(b, 0, 7.2f, -9.6f, 2, 3.4f);
        // Glowing slits deep in the sockets (lit from inside, see ColossusFx).
        for (int s = -1; s <= 1; s += 2) {
            Clump c = clump(b, s * 5.2f, -3.8f, -9.3f, 2.4f, .8f, .3f, null, NONE, 0, 0, EYE);
            c.kind = GLOW;
            c.solid = true;
        }
    }

    /**
     * One arm, authored for his right (-x) and mirrored: a massive layered shoulder with jagged crags on top,
     * a thick upper arm, and a forearm that widens to the wrist like a gorilla's.
     */
    private static void arm(boolean left) {
        int arm = left ? L_ARM : R_ARM, crag = left ? L_CRAGS : R_CRAGS, fore = left ? L_FORE : R_FORE;
        float m = left ? -1 : 1;
        // ---- shoulder and upper arm (arm space)
        mcore(arm, m, -22, -13, -12, 2, 8, 12);
        mcore(arm, m, -17, 6, -9, -1, 28, 9);
        mslab(arm, m, -10, -12.5f, 0, 13.2f, 3.6f, 13, 0, 0, -.25f, NY, .2f, LIGHT);
        mslab(arm, m, -21.5f, -3, 0, 3, 9, 12.4f, 0, 0, -.2f, NX, .2f, MID);
        mslab(arm, m, -20.5f, 7.5f, .5f, 3, 6, 10.5f, 0, 0, -.08f, NX, .22f, DUSK);
        // Front of the shoulder, open in the middle: the shoulder crystal sits in that socket.
        mslab(arm, m, -9.5f, -8, -12.8f, 10, 3, 2.4f, 0, 0, 0, NZ, .25f, LIGHT);
        mslab(arm, m, -18.6f, 3, -11.6f, 3, 8, 2.6f, 0, -.2f, 0, NZ, .25f, MID);
        mslab(arm, m, -.4f, 3, -12, 2.4f, 7, 2.4f, 0, .15f, 0, NZ, .3f, DUSK);
        mslab(arm, m, -9.5f, 0, 12.8f, 10, 9, 2.6f, 0, 0, 0, PZ, .2f, MID);
        // Upper arm: biceps, triceps, the outer and inner sides.
        mslab(arm, m, -9, 16, -9.6f, 7, 8.6f, 2.6f, -.05f, 0, 0, NZ, .26f, LIGHT);
        mslab(arm, m, -9, 15, 9.6f, 7.4f, 10, 2.6f, .05f, 0, 0, PZ, .24f, MID);
        mslab(arm, m, -17.2f, 17, 0, 2.6f, 8.6f, 7.5f, 0, 0, -.06f, NX, .25f, MID);
        mslab(arm, m, -1.2f, 17, 0, 2, 8, 6.5f, 0, 0, 0, PX, .28f, DUSK);
        // ---- crags (hidden while the arm is a blade)
        mslab(crag, m, -15, -17, -2, 4, 8, 5, .1f, 0, -.6f, NY, .55f, CRAG);
        mslab(crag, m, -22, -10, 4.5f, 3.2f, 6.5f, 4.2f, -.2f, .2f, -.95f, NY, .55f, CRAG);
        mslab(crag, m, -8, -17.5f, 7.5f, 3.4f, 6, 3.8f, .35f, 0, -.36f, NY, .55f, CRAG);
        mslab(crag, m, -19, -14, -8.5f, 2.6f, 5, 3, -.38f, 0, -.8f, NY, .5f, DUSK);
        mdrip(arm, m, -21, 13, -3, 2.2f, 5);
        mdrip(arm, m, -11, 25.5f, 9, 1.8f, 4);
        // ---- forearm (forearm space, the hand point at y = 26)
        mcore(fore, m, -9, -1, -9, 9, 25, 9);
        mslab(fore, m, 0, 1.5f, 6.2f, 7, 5, 4.4f, -.2f, 0, 0, PZ, .3f, MID);
        mslab(fore, m, -11.2f, 14, 0, 3, 10, 9.6f, 0, 0, .12f, NX, .22f, LIGHT);
        mslab(fore, m, 11.2f, 14, 0, 3, 10, 9.6f, 0, 0, -.12f, PX, .22f, MID);
        mslab(fore, m, 0, 14, -11.2f, 9.2f, 10, 3, .1f, 0, 0, NZ, .22f, LIGHT);
        mslab(fore, m, 0, 15, 11.2f, 9.6f, 9, 3, -.1f, 0, 0, PZ, .22f, MID);
        mslab(fore, m, 0, 23.6f, 0, 11.6f, 2.4f, 11.6f, 0, 0, 0, PY, .1f, DUSK);
        mslab(fore, m, -13.6f, 9, 1, 2.6f, 7, 5, .15f, .2f, -.35f, NX, .5f, CRAG);
        mdrip(fore, m, -11, 24, 5, 1.8f, 4);
        clods(arm, 7);
        clods(fore, 5);
        mdrip(fore, m, 6, 26, 10, 1.6f, 3.4f);
    }

    /** The open hands (forearm space): a thick palm and heavy, slightly curled fingers, the palm on +z. */
    private static void hands() {
        for (int side = 0; side < 2; side++) {
            int b = side == 0 ? R_HAND : L_HAND;
            float m = side == 0 ? 1 : -1;
            mcore(b, m, -10, 24, -7, 10, 34, 7);
            mslab(b, m, 0, 29, -7.6f, 10.8f, 5, 2.4f, 0, 0, 0, NZ, .2f, LIGHT);
            mslab(b, m, 0, 29.5f, 7.6f, 10.4f, 4.6f, 2.4f, 0, 0, 0, PZ, .2f, MID);
            for (int k = 0; k < 4; k++) {
                float x = -8.1f + k * 5.4f, len = k == 1 || k == 2 ? 1 : .86f;
                mslab(b, m, x, 37.5f, .6f, 2.5f, 3.6f * len, 3.1f, .1f, 0, 0, PY, .25f, k % 2 == 0 ? MID : LIGHT);
                Clump tip = mslab(b, m, x, 37.5f + 6.4f * len, 2.8f, 2.2f, 3 * len, 2.6f, .38f, 0, 0, PY, .35f, MID);
                tip.solid = true;
            }
            // Thumb on the inner side.
            mslab(b, m, 12, 27.5f, 3, 2.5f, 5.2f, 3, .3f, 0, .45f, PX, .3f, MID);
        }
    }

    /** The heavy fist (mace bone; origin at the hand point): knuckles at the bottom, curled fingers in front. */
    private static void fist() {
        int b = FIST;
        core(b, -12, -5, -12, 12, 15, 12);
        slab(b, 0, -5, 0, 11.6f, 2.2f, 11.6f, 0, 0, 0, NY, .15f, DUSK);
        for (int k = 0; k < 4; k++) {
            float x = -8.7f + k * 5.8f;
            slab(b, x, 15.6f, -3, 2.9f, 3.2f, 6.6f, 0, 0, 0, PY, .32f, k % 2 == 0 ? LIGHT : MID);
            slab(b, x, 7.5f, -12.6f, 2.8f, 5.6f, 2.6f, -.08f, 0, 0, NZ, .3f, k % 2 == 0 ? MID : LIGHT);
        }
        slab(b, 12.6f, 4.5f, -6, 2.6f, 6.4f, 4, 0, 0, .25f, PX, .3f, LIGHT);
        slab(b, 0, 3, 12.6f, 11, 7.4f, 2.4f, .06f, 0, 0, PZ, .22f, MID);
        slab(b, -12.6f, 5, 2, 2.4f, 8, 9, 0, 0, 0, NX, .22f, MID);
        slab(b, 12.4f, 8, 6, 2.2f, 5, 5, 0, 0, 0, PX, .25f, DUSK);
        drip(b, -5, 18.4f, 3, 1.8f, 3.6f);
        drip(b, 7, 18.4f, -6, 1.5f, 3);
    }

    /** The R blade (sword bone, arm space; 120 px long, grows with yScale): the shoulder turned into one long edge. */
    private static void sword() {
        int b = SWORD;
        core(b, -12, -9, -10, 12, 20, 10);
        slab(b, 0, -9.5f, 0, 12.6f, 3, 10.6f, 0, 0, 0, NY, .2f, LIGHT);
        slab(b, -12.6f, 4, 0, 2.6f, 11, 9.6f, 0, 0, 0, NX, .2f, MID);
        slab(b, 12.6f, 4, 0, 2.6f, 11, 9.6f, 0, 0, 0, PX, .2f, MID);
        slab(b, 0, 5, -10.6f, 10, 11, 2.4f, 0, 0, 0, NZ, .22f, LIGHT);
        slab(b, 0, 5, 10.6f, 10, 11, 2.4f, 0, 0, 0, PZ, .22f, DUSK);
        // The blade: overlapping segments thinning to the point, a lighter packed edge on both sides.
        int segs = 9;
        for (int k = 0; k < segs; k++) {
            float f = k / (float) (segs - 1);
            float y = 24 + k * 11.2f, hx = 10 - 7.4f * f, hz = 5.6f - 4 * f, bend = 3.5f * f * f;
            Clump c = slab(b, bend, y, 0, hx, 7, hz, 0, 0, -.05f * f, NONE, 0, k % 2 == 0 ? MID : DUSK);
            c.solid = true;
            Clump e1 = slab(b, bend - hx - .6f, y, 0, 1.3f, 6.6f, hz * .55f, 0, 0, -.05f * f, NX, .3f, EDGE);
            Clump e2 = slab(b, bend + hx + .6f, y, 0, 1.3f, 6.6f, hz * .55f, 0, 0, -.05f * f, PX, .3f, EDGE);
            e1.solid = e2.solid = true;
        }
        Clump tip = slab(b, 3.8f, 115, 0, 2.6f, 5.2f, 1.6f, 0, 0, 0, PY, .8f, EDGE);
        tip.solid = true;
    }

    /**
     * The dune he rises out of instead of legs: a stepped skirt of overlapping slabs in four tiers, each tier
     * turned a little further round so the bands read as sand spiralling down, clods at its foot, spill on the ground.
     */
    private static void lower() {
        int b = LOWER;
        float o = (float) (Math.PI / 4);
        // The packed inside: octagonal sections following the flare of the strata, kept just inside them.
        for (int k = 0; k < 5; k++) {
            float y0 = 90 + k * 14, y1 = k == 4 ? 160 : y0 + 15;
            float a = .72f * (radius((y0 + y1) / 2) - 3.4f);
            core(b, -a, y0, -a, a, y1, a);
            coreY(b, -a, y0, -a, a, y1, a, o);
        }
        // Strata: rings of wide, low slabs, flaring out toward the ground like a dune's foot, each ring turned
        // half a slab from the one above so the joints never line up.
        int tiers = 6;
        for (int t = 0; t < tiers; t++) {
            float f = t / (float) (tiers - 1);
            float y = 97 + f * 55, r = radius(y), hh = 5.8f + 1.2f * f;
            int n = 8 + t * 2;
            for (int k = 0; k < n; k++) {
                float a = (float) ((k + .5f * (t % 2)) * Math.PI * 2 / n + t * .21f);
                float w = (float) (Math.PI * r / n) * 1.06f;
                Quaternionf q = new Quaternionf().rotationY(a).rotateX(.12f + .32f * f).rotateZ((hash(t * 31 + k) - .5f) * .12f);
                float[] tint = (k * 7 + t) % 3 == 0 ? LIGHT : (k * 7 + t) % 3 == 1 ? MID : DUSK;
                clump(b, (float) Math.sin(a) * r, y, (float) Math.cos(a) * r, w, hh, 3.2f, q, PZ, .16f, .1f, tint);
            }
        }
        clods(b, 20);
        // Clods at the foot of the dune.
        for (int k = 0; k < 10; k++) {
            float a = (float) (k * Math.PI * 2 / 10 + .3f), r = 33.5f + 3 * hash(k * 7 + 3);
            Quaternionf q = new Quaternionf().rotationY(a + (hash(k + 1) - .5f)).rotateX(.18f + .2f * hash(k)).rotateZ((hash(k + 9) - .5f) * .4f);
            clump(b, (float) Math.sin(a) * r, 153 - 2 * hash(k + 2), (float) Math.cos(a) * r * .9f,
                    4.2f + 1.4f * hash(k + 4), 5.4f + 2 * hash(k + 5), 4, q, NY, .38f, .16f, k % 2 == 0 ? CRAG : MID);
        }
        // Spill: low flattened lumps spreading onto the ground round the base.
        for (int k = 0; k < 16; k++) {
            float a = (float) (k * Math.PI * 2 / 16 + .1f), r = 37 + 6 * hash(k * 5 + 1);
            Quaternionf q = new Quaternionf().rotationY(a + .3f);
            clump(b, (float) Math.sin(a) * r, 157.6f, (float) Math.cos(a) * r * .9f,
                    5 + 2 * hash(k + 11), 2.6f, 3.6f + hash(k + 12), q, NY, .45f, .2f, k % 2 == 0 ? DUSK : MID);
        }
    }

    /** Radius of the dune's strata at root height y (px). */
    private static float radius(float y) {
        float f = clamp((y - 97) / 55f);
        return 16.5f + 16.5f * (float) Math.pow(f, 1.5);
    }

    /** Points along the undersides where loose sand trickles off. */
    private static void emitters() {
        for (int side = 0; side < 2; side++) {
            float m = side == 0 ? 1 : -1;
            int arm = side == 0 ? R_ARM : L_ARM, fore = side == 0 ? R_FORE : L_FORE, hand = side == 0 ? R_HAND : L_HAND;
            emit.add(new float[]{arm, -22 * m, 13, -2});
            emit.add(new float[]{arm, -12 * m, 21, 11});
            emit.add(new float[]{fore, -12 * m, 23, 4});
            emit.add(new float[]{fore, 4 * m, 26, 11});
            emit.add(new float[]{hand, -3 * m, 48, 4});
            emit.add(new float[]{TORSO, 15 * m, 61, -16});
            emit.add(new float[]{TORSO, 25 * m, 58, 6});
            emit.add(new float[]{TORSO, 9 * m, 82, 14});
            emit.add(new float[]{LOWER, 15 * m, 107, -9});
        }
        emit.add(new float[]{FIST, -4, 19, -4});
        emit.add(new float[]{FIST, 6, 18, 6});
        emit.add(new float[]{HEAD, 0, 7.5f, -9});
    }

    // =====================================================================================================
    // building blocks
    // =====================================================================================================

    private static Clump slab(int bone, float x, float y, float z, float hx, float hy, float hz,
                              float rx, float ry, float rz, int out, float inset, float[] tint) {
        Quaternionf q = rx == 0 && ry == 0 && rz == 0 ? null : new Quaternionf().rotationZYX(rz, ry, rx);
        return clump(bone, x, y, z, hx, hy, hz, q, out, inset, .12f, tint);
    }

    /** A slab authored on his right, mirrored to the left when m = -1 (x and the out side flip, the turn mirrors). */
    private static Clump mslab(int bone, float m, float x, float y, float z, float hx, float hy, float hz,
                               float rx, float ry, float rz, int out, float inset, float[] tint) {
        if (m < 0) {
            out = out == NX ? PX : out == PX ? NX : out;
            return slab(bone, -x, y, z, hx, hy, hz, rx, -ry, -rz, out, inset, tint);
        }
        return slab(bone, x, y, z, hx, hy, hz, rx, ry, rz, out, inset, tint);
    }

    /**
     * Small clods stuck on the outer faces of the bone's bigger slabs so far: a few px each, turned at random,
     * near the slab's edges where loose sand catches.
     */
    private static void clods(int bone, int count) {
        List<Clump> big = new ArrayList<>();
        for (Clump c : list)
            if (c.bone == bone && c.kind == PLATE && c.out != NONE && c.h[0] * c.h[1] * c.h[2] > 60) big.add(c);
        if (big.isEmpty()) return;
        Vector3f v = new Vector3f();
        for (int k = 0; k < count; k++) {
            int seed = seeds * 13 + k;
            Clump host = big.get((int) (hash(seed) * big.size()) % big.size());
            int axis = host.out >> 1;
            float sign = (host.out & 1) == 0 ? -1 : 1;
            float[] local = new float[3];
            for (int d = 0; d < 3; d++) {
                if (d == axis) local[d] = sign * host.h[d] * .95f;
                else {
                    // Toward the edges: |u| in .35 .. .85 of the half size.
                    float u = .35f + .5f * hash(seed * 3 + d);
                    local[d] = (hash(seed * 5 + d) < .5f ? -u : u) * host.h[d] * (1 - host.inset * .6f);
                }
            }
            v.set(local[0], local[1], local[2]);
            if (host.q != null) host.q.transform(v);
            float size = 1.3f + 1.4f * hash(seed * 7);
            Quaternionf q = new Quaternionf().rotationZYX(hash(seed * 11) * 3, hash(seed * 17) * 3, hash(seed * 19) * 3);
            Clump c = clump(bone, host.c[0] + v.x, host.c[1] + v.y, host.c[2] + v.z, size, size * (.7f + .5f * hash(seed * 23)), size,
                    q, NONE, 0, .3f, hash(seed * 29) < .5f ? LIGHT : MID);
            c.solid = true;
        }
    }

    /** Sand sagging off an underside: a lump that narrows downward from (x, y, z), len long. */
    private static void drip(int bone, float x, float y, float z, float w, float len) {
        int k = seeds;
        Quaternionf q = new Quaternionf().rotationZYX((hash(k * 3) - .5f) * .3f, hash(k * 5) * 1.5f, (hash(k * 7) - .5f) * .3f);
        clump(bone, x, y + len / 2, z, w, len / 2, w * .85f, q, PY, .72f, .1f, MID);
    }

    private static void mdrip(int bone, float m, float x, float y, float z, float w, float len) {
        drip(bone, x * m, y, z, w, len);
    }

    private static void core(int bone, float x0, float y0, float z0, float x1, float y1, float z1) {
        coreY(bone, x0, y0, z0, x1, y1, z1, 0);
    }

    private static void mcore(int bone, float m, float x0, float y0, float z0, float x1, float y1, float z1) {
        if (m < 0) core(bone, -x1, y0, z0, -x0, y1, z1);
        else core(bone, x0, y0, z0, x1, y1, z1);
    }

    private static void coreY(int bone, float x0, float y0, float z0, float x1, float y1, float z1, float turn) {
        Clump c = clump(bone, (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, (x1 - x0) / 2, (y1 - y0) / 2, (z1 - z0) / 2,
                turn == 0 ? null : new Quaternionf().rotationY(turn), NONE, 0, 0, DAMP);
        c.kind = CORE;
        c.solid = true;
    }

    private static Clump clump(int bone, float x, float y, float z, float hx, float hy, float hz, Quaternionf q,
                               int out, float inset, float jitter, float[] tint) {
        Clump c = new Clump();
        c.bone = bone; c.c = new float[]{x, y, z}; c.h = new float[]{hx, hy, hz}; c.q = q;
        c.out = out; c.inset = inset; c.jitter = jitter; c.tint = tint; c.seed = seeds++;
        Vector3f v = new Vector3f();
        float hmin = Math.min(hx, Math.min(hy, hz));
        for (int k = 0; k < 8; k++) {
            float px = (k & 1) != 0 ? hx : -hx, py = (k & 2) != 0 ? hy : -hy, pz = (k & 4) != 0 ? hz : -hz;
            // The outer face is smaller than the inner one: a chunk with sloped sides, not a tile.
            boolean onOut = switch (out) {
                case NX -> (k & 1) == 0; case PX -> (k & 1) != 0;
                case NY -> (k & 2) == 0; case PY -> (k & 2) != 0;
                case NZ -> (k & 4) == 0; case PZ -> (k & 4) != 0;
                default -> false;
            };
            if (onOut) {
                float keep = 1 - inset * (.75f + .5f * hash(c.seed * 13 + k));
                if (out != NX && out != PX) px *= keep;
                if (out != NY && out != PY) py *= keep;
                if (out != NZ && out != PZ) pz *= keep;
            }
            float j = jitter * Math.max(1.2f, hmin);
            px += (hash(c.seed * 31 + k * 3) - .5f) * j;
            py += (hash(c.seed * 31 + k * 3 + 1) - .5f) * j;
            pz += (hash(c.seed * 31 + k * 3 + 2) - .5f) * j;
            v.set(px, py, pz);
            if (q != null) q.transform(v);
            c.p[k * 3] = v.x + x; c.p[k * 3 + 1] = v.y + y; c.p[k * 3 + 2] = v.z + z;
        }
        list.add(c);
        return c;
    }

    // =====================================================================================================
    // baking into the flat arrays
    // =====================================================================================================

    private static void bake(int i, Clump c) {
        BONE[i] = (byte) c.bone;
        KIND[i] = (byte) c.kind;
        SEED[i] = hash(c.seed * 17 + 5);
        float cx = c.c[0] / 16, cy = c.c[1] / 16, cz = c.c[2] / 16;
        CENTER[i * 3] = cx; CENTER[i * 3 + 1] = cy; CENTER[i * 3 + 2] = cz;
        float r = 0, ymin = Float.MAX_VALUE, ymax = -Float.MAX_VALUE;
        for (int k = 0; k < 8; k++) {
            float x = c.p[k * 3] / 16, y = c.p[k * 3 + 1] / 16, z = c.p[k * 3 + 2] / 16;
            CORNER[i * 24 + k * 3] = x; CORNER[i * 24 + k * 3 + 1] = y; CORNER[i * 24 + k * 3 + 2] = z;
            r = Math.max(r, (x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz));
            ymin = Math.min(ymin, y); ymax = Math.max(ymax, y);
        }
        RADIUS[i] = (float) Math.sqrt(r);
        YMIN[i] = ymin; YMAX[i] = ymax;
        // Faces: normals pointing away from the centre, uvs planar per face (1.25 sand tiles per block,
        // shifted per clump so neighbouring clumps never line up).
        for (int f = 0; f < 6; f++) {
            int[] fc = FACES[f];
            float[] a = corner(i, fc[0]), b = corner(i, fc[1]), d = corner(i, fc[2]), e = corner(i, fc[3]);
            float d1x = d[0] - a[0], d1y = d[1] - a[1], d1z = d[2] - a[2], d2x = e[0] - b[0], d2y = e[1] - b[1], d2z = e[2] - b[2];
            float nx = d1y * d2z - d1z * d2y, ny = d1z * d2x - d1x * d2z, nz = d1x * d2y - d1y * d2x;
            float fx = (a[0] + b[0] + d[0] + e[0]) / 4 - cx, fy = (a[1] + b[1] + d[1] + e[1]) / 4 - cy, fz = (a[2] + b[2] + d[2] + e[2]) / 4 - cz;
            if (nx * fx + ny * fy + nz * fz < 0) { nx = -nx; ny = -ny; nz = -nz; }
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-6f) { nx = 0; ny = -1; nz = 0; len = 1; }
            NORMAL[i * 18 + f * 3] = nx / len; NORMAL[i * 18 + f * 3 + 1] = ny / len; NORMAL[i * 18 + f * 3 + 2] = nz / len;
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (ul < 1e-6f) { ux = 1; uy = 0; uz = 0; ul = 1; }
            ux /= ul; uy /= ul; uz /= ul;
            // v axis: the face normal crossed with u.
            float vx = (ny * uz - nz * uy) / len, vy = (nz * ux - nx * uz) / len, vz = (nx * uy - ny * ux) / len;
            float ou = hash(c.seed * 7 + f), ov = hash(c.seed * 11 + f);
            for (int k = 0; k < 4; k++) {
                float[] p = corner(i, fc[k]);
                float px = p[0] - a[0], py = p[1] - a[1], pz = p[2] - a[2];
                UV[i * 48 + f * 8 + k * 2] = ou + (px * ux + py * uy + pz * uz) * 1.25f;
                UV[i * 48 + f * 8 + k * 2 + 1] = ov + (px * vx + py * vy + pz * vz) * 1.25f;
            }
        }
        // The inner face is pressed into the core: not drawn while the clump sits on the body.
        int mask = 63;
        if (!c.solid && c.out != NONE) mask &= ~(1 << (c.out ^ 1));
        MASK[i] = (byte) mask;
        // Colour: tint, a little variation, darker and warmer toward the ground, corners toward the core darker.
        float[] rest = REST[c.bone];
        float rootY = (rest[1] + c.c[1]);
        float ground = 1 - .15f * clamp(rootY / 160f), warm = 1 - .07f * clamp(rootY / 160f);
        float vary = .94f + .1f * hash(c.seed * 3 + 1);
        float[] ax = AXIS[c.bone];
        float ox, oy, oz;
        {
            float sx = ax[3] - ax[0], sy = ax[4] - ax[1], sz = ax[5] - ax[2];
            float t = clamp(((c.c[0] - ax[0]) * sx + (c.c[1] - ax[1]) * sy + (c.c[2] - ax[2]) * sz) / (sx * sx + sy * sy + sz * sz));
            ox = c.c[0] - (ax[0] + sx * t); oy = c.c[1] - (ax[1] + sy * t); oz = c.c[2] - (ax[2] + sz * t);
            float ol = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (ol < 1e-4f) { ox = 0; oy = -1; oz = 0; } else { ox /= ol; oy /= ol; oz /= ol; }
        }
        float reach = 1e-4f;
        for (int k = 0; k < 8; k++) reach = Math.max(reach, Math.abs((c.p[k * 3] - c.c[0]) * ox + (c.p[k * 3 + 1] - c.c[1]) * oy + (c.p[k * 3 + 2] - c.c[2]) * oz));
        for (int k = 0; k < 8; k++) {
            float ao;
            if (c.kind == PLATE) {
                float d = ((c.p[k * 3] - c.c[0]) * ox + (c.p[k * 3 + 1] - c.c[1]) * oy + (c.p[k * 3 + 2] - c.c[2]) * oz) / reach;
                ao = .70f + .30f * smooth(clamp(d * .5f + .5f));
                // Sand on top of a ledge is lighter than sand under it.
                float up = -(c.p[k * 3 + 1] - c.c[1]) / Math.max(1, c.h[1]);
                ao *= 1 + .06f * clamp(up, -1, 1);
            } else ao = c.kind == CORE ? .92f : 1;
            float g = c.kind == GLOW ? 1 : ground * vary * ao;
            SHADE[i * 24 + k * 3] = Math.min(1, c.tint[0] * g);
            SHADE[i * 24 + k * 3 + 1] = Math.min(1, c.tint[1] * g);
            SHADE[i * 24 + k * 3 + 2] = Math.min(1, c.tint[2] * g * (c.kind == GLOW ? 1 : warm));
        }
        timing(i, c, rootY);
    }

    /** When each clump lands in the build (bottom up: dune, torso, arms, hands, head) and when it tears away in the collapse (top down). */
    private static void timing(int i, Clump c, float rootY) {
        float local = c.c[1], j = (hash(c.seed * 23 + 7) - .5f);
        float a, d;
        switch (c.bone) {
            case LOWER -> { a = mix(.05f, .19f, (160 - rootY) / 70); d = mix(.55f, .9f, (rootY - 90) / 70); }
            case TORSO -> { a = mix(.15f, .30f, (112 - rootY) / 86); d = mix(.24f, .62f, (rootY - 22) / 90); }
            case R_ARM, L_ARM, R_CRAGS, L_CRAGS -> { a = mix(.24f, .33f, (local + 18) / 46); d = mix(.18f, .38f, 1 - (local + 18) / 46); }
            case R_FORE, L_FORE -> { a = mix(.29f, .36f, local / 26); d = mix(.12f, .28f, 1 - local / 26); }
            case R_HAND, L_HAND -> { a = mix(.33f, .40f, (local - 24) / 22); d = mix(.06f, .2f, 1 - (local - 24) / 22); }
            case FIST -> { a = mix(.33f, .40f, (local + 5) / 22); d = mix(.06f, .2f, 1 - (local + 5) / 22); }
            case HEAD -> { a = mix(.32f, .41f, (6 - local) / 30); d = mix(0, .12f, (local + 24) / 30); }
            default -> { a = 0; d = .3f; }
        }
        if (c.kind == CORE) {
            // The packed inside fills a little ahead of the slabs that land on it, as ONE rising front per bone:
            // each section's window is the part of the bone's stage its own height range covers.
            float[] st = switch (c.bone) {
                case LOWER -> new float[]{.04f, .18f, 160, 90};
                case TORSO -> new float[]{.13f, .29f, 112, 22};
                case HEAD -> new float[]{.31f, .39f, 4, -25};
                case R_ARM, L_ARM -> new float[]{.23f, .32f, -13, 28};
                case R_FORE, L_FORE -> new float[]{.28f, .35f, -1, 25};
                case SWORD -> new float[]{0, 0, 0, 1};
                default -> new float[]{.32f, .39f, -5, 34};
            };
            boolean upright = c.bone == LOWER || c.bone == TORSO || c.bone == HEAD;
            float off = upright ? REST[c.bone][1] : 0;
            float first = upright ? c.h[1] + c.c[1] + off : c.c[1] - c.h[1], last = upright ? c.c[1] - c.h[1] + off : c.c[1] + c.h[1];
            float w0 = mix(st[0], st[1], (first - st[2]) / (st[3] - st[2])), w1 = mix(st[0], st[1], (last - st[2]) / (st[3] - st[2]));
            ARRIVE[i] = Math.max(w0 + .005f, w1);
            FLIGHT[i] = ARRIVE[i] - w0;
            // Upright parts fill from the ground up; limbs from the shoulder out.
            FILL[i] = (byte) (upright ? 1 : -1);
            // ...and pour away the other way round, again as one front per bone, with the slabs falling off it.
            float[] dr = switch (c.bone) {
                case LOWER -> new float[]{.56f, .95f};
                case TORSO -> new float[]{.26f, .66f};
                case HEAD -> new float[]{.02f, .16f};
                case R_ARM, L_ARM -> new float[]{.2f, .4f};
                case R_FORE, L_FORE -> new float[]{.14f, .3f};
                case SWORD -> new float[]{.2f, .4f};
                default -> new float[]{.08f, .22f};
            };
            float d0 = mix(dr[0], dr[1], (last - st[3]) / (st[2] - st[3])), d1 = mix(dr[0], dr[1], (first - st[3]) / (st[2] - st[3]));
            DROP[i] = d0;
            DRAIN[i] = Math.max(.02f, d1 - d0);
        } else {
            ARRIVE[i] = c.bone == SWORD ? 0 : a + j * .024f;
            FLIGHT[i] = .085f + .05f * hash(c.seed * 29 + 3);
            DROP[i] = clamp(d + j * .06f);
        }
    }

    // =====================================================================================================

    private static float[] corner(int i, int k) {
        return new float[]{CORNER[i * 24 + k * 3], CORNER[i * 24 + k * 3 + 1], CORNER[i * 24 + k * 3 + 2]};
    }

    static float hash(int n) {
        double x = Math.sin(n * 12.9898 + 78.233) * 43758.5453;
        return (float) (x - Math.floor(x));
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * clamp(t); }
    private static float clamp(float v) { return v < 0 ? 0 : Math.min(v, 1); }
    private static float clamp(float v, float a, float b) { return v < a ? a : Math.min(v, b); }
    private static float smooth(float t) { return t * t * (3 - 2 * t); }
}
