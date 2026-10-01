package com.FIRNI.superheromod.heroes.thor;

/**
 * Everything Thor can be doing, shared by the server (which decides) and the clients (which
 * animate it from the synced clock), plus every tuning number in one place.
 */
public final class ThorAction {
    public static final String ID = "thor";

    // ------------------------------------------------------------------ actions
    public static final int IDLE = 0, SWING_RIGHT = 1, SWING_LEFT = 2, UPPERCUT = 3, THROW = 4, CATCH = 5,
            TAKEOFF = 6, GUARD = 7, COUNTER = 8, WAKANDA = 9, ULTIMATE = 10;
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

    // ------------------------------------------------------------------ flight (shift)
    /** Spin-up on the ground before lift-off. */
    public static final int TAKEOFF_TICKS = 14;
    public static final double FLY_SPEED = 1.05, FLY_SPRINT = 1.55, FLY_STEER = .22;

    // ------------------------------------------------------------------ guard (E)
    public static final int GUARD_MAX = 60, GUARD_PERFECT = 5, GUARD_COOLDOWN = 40, COUNTER_TICKS = 12;
    public static final float COUNTER_DAMAGE = 7;

    // ------------------------------------------------------------------ Wakanda strike (R)
    /** Head bowed, eyes light, rise, battle cry in the air, the dive, landing, kneel, stand. */
    public static final int WK_LOOK_UP = 10, WK_RISE = 18, WK_HOVER = 30, WK_SHOUT = 32, WK_DIVE = 50, WK_MAX_AIR = 90,
            WK_FREEZE = 4, WK_KNEEL = 26, WK_COOLDOWN = 200;
    public static final double WK_RISE_SPEED = 1.05, WK_DIVE_SPEED = 2.6, WK_RADIUS = 8;
    public static final float WK_DAMAGE = 14;

    // ------------------------------------------------------------------ God of Thunder (X)
    public static final int ULT_TOTAL = 330, ULT_IMPACT = 300, ULT_COOLDOWN = 900;
    public static final float ULT_DAMAGE_SHARE = .7f;
    /** Beats of the film, in ticks. */
    public static final int ULT_CLOSE = 40, ULT_EYE_SPARK = 62, ULT_EYES = 70, ULT_HAMMER = 80, ULT_RAISE = 100,
            ULT_SKY = 120, ULT_STRIKE = 150, ULT_POWER = 160, ULT_SHOUT = 172, ULT_BOOM = 196, ULT_TARGET = 200,
            ULT_FLIGHT = 240, ULT_LAUNCH = 250, ULT_HOVER = 280, ULT_PLUNGE = 296;

    // ------------------------------------------------------------------ effects (ThorFxPacket kinds)
    public static final int FX_SWING_HIT = 0, FX_UPPER = 1, FX_HAMMER_HIT = 2, FX_CATCH = 3, FX_CLANG = 4, FX_COUNTER = 5,
            FX_SKY_BOLT = 6, FX_CRACKS = 7, FX_TAKEOFF = 8, FX_SHOUT = 9, FX_ULT_IMPACT = 10, FX_RELEASE = 11;

    /** Wakanda: once he lands, the age restarts from here so every client knows when he touched down. */
    public static final int LANDED = 1000;

    private ThorAction() {}
}
