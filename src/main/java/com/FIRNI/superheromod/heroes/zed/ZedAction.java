package com.FIRNI.superheromod.heroes.zed;

/**
 * Everything Zed can be doing, shared by the server (which decides) and the clients (which animate
 * it from the synced clock), with every timing and tuning number of his kit in one place.
 * Keys: Q shuriken, F living shadow (his "W": W is the walk key), E shadow slash, R death mark,
 * left click quick slashes (the passive rides on them).
 */
public final class ZedAction {
    public static final String ID = "zed";

    // ------------------------------------------------------------------ actions (his own and his shadow's)
    public static final int IDLE = 0, SLASH_RIGHT = 1, SLASH_LEFT = 2, THROW = 3, SHADOW_CAST = 4, SWAP = 5, SPIN = 6,
            MARK_LOCK = 7, MARK_DASH = 8, MARK_STRIKE = 9, MARK_RETURN = 10;

    // ------------------------------------------------------------------ cooldown slots
    public static final int CD_Q = 0, CD_W = 1, CD_E = 2, CD_R = 3;

    // ------------------------------------------------------------------ left click: quick slashes (+ passive)
    public static final int SLASH_TICKS = 7, SLASH_HIT = 3;
    public static final double SLASH_REACH = 3.3;
    public static final float SLASH_DAMAGE = 6;
    /** Contempt for the Weak: below this share of their health a slash also cuts this share of their max health. */
    public static final float PASSIVE_BELOW = .5f, PASSIVE_SHARE = .08f;
    /** Ticks before the passive can trigger on the same target again. */
    public static final int PASSIVE_COOLDOWN = 200;

    // ------------------------------------------------------------------ Q: Razor Shuriken
    public static final int THROW_TICKS = 9, THROW_RELEASE = 3, Q_COOLDOWN = 80;
    public static final double SHURIKEN_SPEED = 1.7, SHURIKEN_RANGE = 19, SHURIKEN_RADIUS = .75;
    public static final float SHURIKEN_DAMAGE = 9, SHURIKEN_PIERCED = .6f, SHURIKEN_SECOND = .5f;

    // ------------------------------------------------------------------ W (on F): Living Shadow
    public static final int CAST_TICKS = 7, SHADOW_TRAVEL = 5, SHADOW_LIFE = 110, W_COOLDOWN = 200, SWAP_TICKS = 4;
    public static final double SHADOW_REACH = 9;
    /** Each E that hits a champion takes this much off the W cooldown. */
    public static final int E_REFUND = 30;

    // ------------------------------------------------------------------ E: Shadow Slash
    public static final int SPIN_TICKS = 10, SPIN_HIT = 3, E_COOLDOWN = 70, E_SLOW = 40;
    public static final double SPIN_RADIUS = 4.2;
    public static final float SPIN_DAMAGE = 7;

    // ------------------------------------------------------------------ R: Death Mark
    public static final int LOCK_TICKS = 5, DASH_TICKS = 5, STRIKE_TICKS = 10, MARK_LIFE = 60, R_SHADOW_LIFE = 140,
            R_COOLDOWN = 900, RETURN_TICKS = 4;
    public static final double R_RANGE = 13;
    public static final float MARK_HIT = 6, MARK_SHARE = .45f;

    // ------------------------------------------------------------------ effects
    public static final int FX_SHURIKEN = 0, FX_SHURIKEN_HIT = 1, FX_SPIN = 2, FX_SHADOW_CAST = 3, FX_SWAP = 4, FX_MARK_LOCK = 5,
            FX_MARK_DASH = 6, FX_MARK_APPLY = 7, FX_MARK_POP = 8, FX_SLASH_HIT = 9, FX_PASSIVE = 10, FX_RETURN = 11, FX_SHADOW_END = 12;

    private ZedAction() {}
}
