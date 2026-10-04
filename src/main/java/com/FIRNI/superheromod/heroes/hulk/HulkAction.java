package com.FIRNI.superheromod.heroes.hulk;

/**
 * Everything Hulk (and Bruce Banner) can be doing, shared by the server (which decides) and the
 * clients (which animate it from the synced clock), with the beats of each move in ticks.
 * Numbers a server owner may want to change live in HulkConfig instead.
 */
public final class HulkAction {
    public static final String ID = "hulk";

    // ------------------------------------------------------------------ actions
    public static final int IDLE = 0, TRANSFORM = 1, REVERT = 2, PUNCH_RIGHT = 3, PUNCH_LEFT = 4, PUNCH_CHARGE = 5,
            PUNCH_RELEASE = 6, GUARD = 7, THUNDERCLAP = 8, POUND = 9, LEAP_CHARGE = 10, LEAP = 11, LANDING = 12,
            ROCK = 13, ULTIMATE = 14;
    /** Flags sent with the state. */
    public static final int FLAG_HULK = 1, FLAG_ROCK_FLYING = 2, FLAG_GUARD_BROKEN = 4, FLAG_ROCK_HELD = 8;

    // ------------------------------------------------------------------ the change
    public static final int TRANSFORM_TICKS = 44, REVERT_TICKS = 30;
    /** When the body starts and stops growing inside the transformation. */
    public static final int GROW_START = 10, GROW_END = 32;

    // ------------------------------------------------------------------ punches (left click)
    /** A press shorter than this is a jab; longer starts the charged punch. */
    public static final int TAP_TICKS = 5, PUNCH_TICKS = 9, PUNCH_HIT = 4, CHARGE_MAX = 18, RELEASE_TICKS = 14, RELEASE_HIT = 3;
    /** The charged punch's shock wave: blocks it travels per tick before it bursts. */
    public static final double PUNCH_WAVE_SPEED = 2.4;

    // ------------------------------------------------------------------ guard (right click)
    public static final float STAMINA_MAX = 100;

    // ------------------------------------------------------------------ Thunderclap (R)
    public static final int CLAP_TICKS = 32, CLAP_HIT = 13;
    /** The Thunderclap's wall of air: blocks it travels per tick, and its half-width in degrees past his hands. */
    public static final double CLAP_SPEED = 1.7, CLAP_SPREAD = 42;

    // ------------------------------------------------------------------ ground pound (F)
    public static final int POUND_TICKS = 30, POUND_HIT = 11;
    /** How far to each side of the ground wave bodies are thrown up. */
    public static final double POUND_WIDTH_HIT = 2.6;
    /** Where the ground splits open (blocks in front of him), and ticks per block it runs. */
    public static final double POUND_START = 2.8;
    public static final int POUND_STEP_TICKS = 1;

    // ------------------------------------------------------------------ leap (space)
    /** Space held shorter than LEAP_TAP is an ordinary jump; held longer it charges (full after LEAP_CHARGE_MAX more). */
    public static final int LEAP_TAP = 4, LEAP_CHARGE_MAX = 10, LANDING_TICKS = 16;

    // ------------------------------------------------------------------ rock (C)
    public static final int ROCK_GRAB = 10, ROCK_LIFT = 20, ROCK_THROW = 26, ROCK_TICKS = 34;
    /** Longest he carries the rock overhead before he hurls it anyway (ticks). */
    public static final int ROCK_CARRY_MAX = 400;
    /** How far in front of him the boulder is torn out (so the hole never opens under his feet). */
    public static final double ROCK_AHEAD = 2.9;

    // ------------------------------------------------------------------ ONE PUNCH (X)
    /**
     * The film's beats, in ticks: the stare; five opening punches; the barrage speeding up to a blur;
     * the sudden stop in the dust; the dust clearing on him standing upright; the wind-up; the freeze;
     * the punch and its black-red impact frames; the air current racing out; the mountain revealed
     * split open; the wide shot; the arm lowered; the end.
     */
    public static final int ULT_FIRST = 36, ULT_BARRAGE = 100, ULT_STOP = 172, ULT_CLEAR = 188, ULT_WINDUP = 222, ULT_FREEZE = 244,
            ULT_PUNCH = 254, ULT_IMPACT_END = 268, ULT_REVEAL = 338, ULT_WIDE = 392, ULT_LOWER = 440, ULT_TOTAL = 480;
    /** Every punch before the last one: when it lands, which hand (true = right), how hard (1 = full). */
    public static final float[] ULT_HITS, ULT_POWER;
    public static final boolean[] ULT_RIGHT;
    static {
        java.util.List<float[]> hits = new java.util.ArrayList<>();
        // Five distinct punches, each one readable: right, left, right (short), left (turning), right (hard).
        float[][] opening = {{ULT_FIRST + 10, 1, 1}, {ULT_FIRST + 22, 0, 1}, {ULT_FIRST + 31, 1, .8f}, {ULT_FIRST + 42, 0, 1}, {ULT_FIRST + 56, 1, 1.3f}};
        hits.addAll(java.util.Arrays.asList(opening));
        // Then faster and faster until the arms are a blur.
        float t = ULT_BARRAGE, gap = 5.5f;
        boolean right = false;
        while (t < ULT_STOP - 1.5f) {
            hits.add(new float[]{t, right ? 1 : 0, Math.max(.45f, gap / 6f)});
            right = !right;
            t += gap;
            gap = Math.max(1.1f, gap * .9f);
        }
        // The last heavy one that stops it all.
        hits.add(new float[]{ULT_STOP, 1, 1.4f});
        ULT_HITS = new float[hits.size()]; ULT_POWER = new float[hits.size()]; ULT_RIGHT = new boolean[hits.size()];
        for (int i = 0; i < hits.size(); i++) { ULT_HITS[i] = hits.get(i)[0]; ULT_RIGHT[i] = hits.get(i)[1] > .5f; ULT_POWER[i] = hits.get(i)[2]; }
    }

    // ------------------------------------------------------------------ effects (HulkFxPacket kinds)
    public static final int FX_TRANSFORM = 0, FX_PUNCH = 1, FX_CHARGED_WAVE = 2, FX_CLAP = 3, FX_POUND_STEP = 4, FX_LANDING = 5,
            FX_ROCK_PULL = 6, FX_ROCK_HIT = 7, FX_ULT_CRASH = 8, FX_ULT_SMASH = 9, FX_GUARD_HIT = 10, FX_REVERT = 11, FX_BLOCK = 12, FX_WAVE_HIT = 13, FX_BLAST = 14, FX_PUNCH_WAVE = 15;

    private HulkAction() {}
}
