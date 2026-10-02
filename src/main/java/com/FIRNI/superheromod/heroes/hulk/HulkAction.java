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
    public static final int FLAG_HULK = 1, FLAG_ROCK_FLYING = 2, FLAG_GUARD_BROKEN = 4;

    // ------------------------------------------------------------------ the change
    public static final int TRANSFORM_TICKS = 44, REVERT_TICKS = 30;
    /** When the body starts and stops growing inside the transformation. */
    public static final int GROW_START = 10, GROW_END = 32;

    // ------------------------------------------------------------------ punches (left click)
    /** A press shorter than this is a jab; longer starts the charged punch. */
    public static final int TAP_TICKS = 5, PUNCH_TICKS = 9, PUNCH_HIT = 4, CHARGE_MAX = 40, RELEASE_TICKS = 14, RELEASE_HIT = 3;

    // ------------------------------------------------------------------ guard (right click)
    public static final float STAMINA_MAX = 100;

    // ------------------------------------------------------------------ Thunderclap (R)
    public static final int CLAP_TICKS = 32, CLAP_HIT = 13;
    /** The Thunderclap's wall of air: blocks it travels per tick, and its half-width in degrees past his hands. */
    public static final double CLAP_SPEED = 1.7, CLAP_SPREAD = 38;

    // ------------------------------------------------------------------ ground pound (F)
    public static final int POUND_TICKS = 30, POUND_HIT = 11;
    /** How far to each side of the ground wave bodies are thrown up. */
    public static final double POUND_WIDTH_HIT = 2.6;

    // ------------------------------------------------------------------ leap (space)
    public static final int LEAP_CHARGE_MAX = 30, LANDING_TICKS = 16;

    // ------------------------------------------------------------------ rock (C)
    public static final int ROCK_GRAB = 10, ROCK_LIFT = 20, ROCK_THROW = 26, ROCK_TICKS = 34;

    // ------------------------------------------------------------------ Gamma Rage (X)
    /** Gather, leap at them, grab, up into the sky, the roar, the throw, their crash, the dive, the smash, the end. */
    public static final int ULT_GATHER = 0, ULT_ROAR = 26, ULT_LEAP = 42, ULT_GRAB = 60, ULT_SKY = 78, ULT_HOLD = 104,
            ULT_THROW = 150, ULT_CRASH = 172, ULT_RAISE = 182, ULT_SMASH_DEFAULT = 212, ULT_TOTAL_AFTER = 70;
    public static final double ULT_HEIGHT = 26;

    // ------------------------------------------------------------------ effects (HulkFxPacket kinds)
    public static final int FX_TRANSFORM = 0, FX_PUNCH = 1, FX_CHARGED_WAVE = 2, FX_CLAP = 3, FX_POUND_STEP = 4, FX_LANDING = 5,
            FX_ROCK_PULL = 6, FX_ROCK_HIT = 7, FX_ULT_CRASH = 8, FX_ULT_SMASH = 9, FX_GUARD_HIT = 10, FX_REVERT = 11, FX_BLOCK = 12, FX_WAVE_HIT = 13;

    private HulkAction() {}
}
