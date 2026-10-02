package com.FIRNI.superheromod.heroes.thor;

/**
 * Everything Thor can be doing, shared by the server (which decides) and the clients (which
 * animate it from the synced clock), plus every tuning number in one place.
 */
public final class ThorAction {
    public static final String ID = "thor";

    // ------------------------------------------------------------------ actions
    public static final int IDLE = 0, SWING_RIGHT = 1, SWING_LEFT = 2, UPPERCUT = 3, THROW = 4, CATCH = 5,
            TAKEOFF = 6, GUARD = 7, COUNTER = 8, WAKANDA = 9, ULTIMATE = 10, CHARGE = 11, DASH = 12, BEAM = 13;
    /** Flags sent with the state. */
    public static final int FLAG_FLYING = 1, FLAG_HAMMER_OUT = 2, FLAG_POWERED = 4;

    // ------------------------------------------------------------------ combo (left click)
    /** Length of each swing, the tick it connects, and how long after it the next click still chains. */
    public static final int SWING_TICKS = 12, SWING_HIT = 6, UPPER_TICKS = 18, UPPER_HIT = 10, CHAIN_GRACE = 8;
    public static final double SWING_REACH = 3.6, SWING_CONE = .25;
    public static final float SWING_DAMAGE = 6, UPPER_DAMAGE = 8;
    /** How hard the third hit throws the target up. */
    public static final double UPPER_LAUNCH = 1.0;

    // ------------------------------------------------------------------ throw (right click)
    public static final int THROW_WINDUP = 6, THROW_RETURN_DELAY = 5, CATCH_TICKS = 10, THROW_COOLDOWN = 10;
    public static final double THROW_SPEED = 2.2, THROW_RANGE = 30, RETURN_SPEED_MAX = 2.6;
    public static final float THROW_DAMAGE = 8;

    // ------------------------------------------------------------------ hammer launch (shift: hold to spin, release to fly)
    /** Spin-up length of a whirl (also used by the film). */
    public static final int TAKEOFF_TICKS = 14;
    /** Ticks of holding for a full charge; the launch scales between the min and max below. */
    public static final int CHARGE_FULL = 30, DASH_MIN_TICKS = 6, DASH_MAX_TICKS = 14, DASH_COOLDOWN = 16;
    public static final double DASH_MIN_SPEED = 1.0, DASH_MAX_SPEED = 2.2;
    public static int dashTicks(float charge) { return Math.round(DASH_MIN_TICKS + (DASH_MAX_TICKS - DASH_MIN_TICKS) * charge); }
    public static double dashSpeed(float charge) { return DASH_MIN_SPEED + (DASH_MAX_SPEED - DASH_MIN_SPEED) * charge; }

    // ------------------------------------------------------------------ thunder beam (F)
    /** Hammer up to the sky (a bolt comes down into it), then pointed ahead: two seconds of lightning. */
    public static final int BM_SKY = 8, BM_AIM = 16, BM_END = 56, BM_TOTAL = 64, BM_COOLDOWN = 160, BM_HIT_EVERY = 4;
    public static final double BM_RANGE = 22, BM_RADIUS = .9;
    public static final float BM_DAMAGE = 1.5f;

    // ------------------------------------------------------------------ guard (E)
    public static final int GUARD_MAX = 60, GUARD_PERFECT = 5, GUARD_COOLDOWN = 40, COUNTER_TICKS = 12;
    public static final float COUNTER_DAMAGE = 7;

    // ------------------------------------------------------------------ Wakanda strike (R)
    /** Head bowed, eyes light, rise, battle cry in the air, the dive, landing, kneel, stand. */
    public static final int WK_LOOK_UP = 10, WK_RISE = 18, WK_HOVER = 30, WK_SHOUT = 32, WK_DIVE = 50, WK_MAX_AIR = 90,
            WK_FREEZE = 4, WK_KNEEL = 26, WK_COOLDOWN = 200;
    public static final double WK_RISE_SPEED = 1.05, WK_DIVE_SPEED = 2.6, WK_RADIUS = 8;
    public static final float WK_DAMAGE = 14;

    // ------------------------------------------------------------------ God of Thunder — Aerial Punishment (X)
    public static final int ULT_TOTAL = 362, ULT_COOLDOWN = 900;
    public static final float ULT_DAMAGE_SHARE = .7f;
    /** Beats of the film, in ticks. */
    public static final int ULT_LUNGE = 14, ULT_HIT1 = 31, ULT_HIT2 = 45, ULT_LOAD = 52, ULT_UPPER = 67, ULT_SPIN = 104,
            ULT_RISE = 124, ULT_TOSS = 146, ULT_VANISH = 166, ULT_APEX = 168, ULT_CATCH = 178, ULT_SCREAM = 192,
            ULT_EYEBOLT = 200, ULT_STORM = 206, ULT_WHITE = 226, ULT_BLAST = 248, ULT_FADE = 266, ULT_LET_GO = 276,
            ULT_RECALL = 292, ULT_CRASH = 308, ULT_LANDED = 340;
    /** The two opening hits play this much faster than the gameplay swings (1 = same speed). */
    public static final float ULT_SWING_PACE = .85f;
    /** How high the uppercut sends the target, where the hammer vanishes into the clouds above it, and where Thor lands. */
    public static final double ULT_HEIGHT = 22, ULT_CLOUDS = 33, ULT_LAND_X = 2.6, ULT_LAND_SHORT = 1.3, ULT_KNOCK = .55;

    // ------------------------------------------------------------------ Shift launch: whoever the hammer hits rides on it
    /** How far in front of Thor the carried body sits, and how hard it is thrown when the launch ends. */
    public static final double CARRY_AHEAD = 1.45, CARRY_FLING = .7;

    // ------------------------------------------------------------------ effects (ThorFxPacket kinds)
    public static final int FX_SWING_HIT = 0, FX_UPPER = 1, FX_HAMMER_HIT = 2, FX_CATCH = 3, FX_CLANG = 4, FX_COUNTER = 5,
            FX_SKY_BOLT = 6, FX_CRACKS = 7, FX_TAKEOFF = 8, FX_SHOUT = 9, FX_ULT_IMPACT = 10, FX_RELEASE = 11,
            FX_BEAM_HIT = 12, FX_CHARGED = 13;

    /** Wakanda: once he lands, the age restarts from here so every client knows when he touched down. */
    public static final int LANDED = 1000;

    private ThorAction() {}
}
