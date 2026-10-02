package com.FIRNI.superheromod.heroes.zed;

/**
 * Everything Zed can be doing, shared by the server (which decides) and the clients (which animate
 * it from the synced clock), with every timing and tuning number of his kit in one place.
 * Keys: Q shuriken, F living shadow (his "W": W is the walk key), E shadow slash, R death mark,
 * X the Shadow Execution film, left click quick slashes (the passive rides on them).
 */
public final class ZedAction {
    public static final String ID = "zed";

    // ------------------------------------------------------------------ actions (his own and his shadow's)
    public static final int IDLE = 0, SLASH_RIGHT = 1, SLASH_LEFT = 2, THROW = 3, SHADOW_CAST = 4, SWAP = 5, SPIN = 6,
            MARK_LOCK = 7, MARK_DASH = 8, MARK_STRIKE = 9, MARK_RETURN = 10, MARK_HIDDEN = 11, ULTIMATE = 12, SLASH_FINISH = 13;

    // ------------------------------------------------------------------ cooldown slots
    public static final int CD_Q = 0, CD_W = 1, CD_E = 2, CD_R = 3, CD_X = 4;

    // ------------------------------------------------------------------ left click: a three-cut combo (+ passive)
    /**
     * Right-hand cut, left-hand reverse cut, then a deeper diagonal finisher. A click during a cut is
     * remembered and the next cut starts as soon as this one may be broken off (CHAIN), out of its
     * recovery rather than from a standing start. The combo starts over after COMBO_RESET idle ticks.
     */
    public static final int SLASH_TICKS = 8, SLASH_HIT = 3, SLASH_CHAIN = 5,
            FINISH_TICKS = 11, FINISH_HIT = 5, FINISH_CHAIN = 8, COMBO_RESET = 14;
    public static final double SLASH_REACH = 3.3, FINISH_REACH = 3.7;
    public static final float SLASH_DAMAGE = 6, FINISH_DAMAGE = 9, SLASH_KNOCK = .22f, FINISH_KNOCK = .5f;
    /** Bleeding (left-click cuts and shurikens): stacks up to BLEED_MAX, each stack this much every BLEED_EVERY ticks. */
    public static final int BLEED_TICKS = 80, BLEED_EVERY = 20, BLEED_MAX = 3;
    public static final float BLEED_DAMAGE = 1;
    /** Contempt for the Weak: below this share of their health a slash also cuts this share of their max health. */
    public static final float PASSIVE_BELOW = .5f, PASSIVE_SHARE = .08f;
    /** Ticks before the passive can trigger on the same target again. */
    public static final int PASSIVE_COOLDOWN = 200;

    // ------------------------------------------------------------------ Q: Razor Shuriken
    public static final int THROW_TICKS = 9, THROW_RELEASE = 3, Q_COOLDOWN = 80;
    public static final double SHURIKEN_SPEED = 1.7, SHURIKEN_RANGE = 19, SHURIKEN_RADIUS = 1.0;
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
    /**
     * He sinks into shadow (LOCK), two shadow copies of him run to the target and into it (DASH), the X
     * burns on the target while he is gone from the world (HIDDEN, 1.5 s), then he steps out behind
     * them and the X bursts (STRIKE). The R shadow waits where he started; R again returns to it.
     */
    public static final int LOCK_TICKS = 5, DASH_TICKS = 10, HIDDEN_TICKS = 30, STRIKE_TICKS = 10, MARK_LIFE = HIDDEN_TICKS,
            R_SHADOW_LIFE = 140, R_COOLDOWN = 900, RETURN_TICKS = 4;
    public static final double R_RANGE = 13;
    /** The copies going in; the burst: a base, a share of their max health, and a share of what he dealt while it burned. */
    public static final float MARK_HIT = 4, MARK_POP = 8, MARK_POP_SHARE = .15f, MARK_SHARE = .45f;

    // ------------------------------------------------------------------ X: Shadow Execution (film, see ShadowExecutionFilm)
    public static final int ULT_COOLDOWN = 1400;
    public static final double ULT_RANGE = 12;
    /** Share of the target's max health taken at the red flash. */
    public static final float ULT_DAMAGE_SHARE = .7f;
    /** The duel spacing: he faces the target from this far before the victim swings. */
    public static final double ULT_SPACING = 3;
    /** Where he stands again at the end, from the victim: x to the side (his right at the start), z along the line. */
    public static final double ULT_REVEAL_X = -2.4, ULT_REVEAL_Z = -3.4;
    public static final int
            ULT_STEP_END = 9,        // if he started further away, a shadow step brings him in
            ULT_WINDUP = 12,         // the victim draws back a bare-handed hook
            ULT_DODGE = 18,          // his head and shoulders start to slip it
            ULT_SWING = 24,          // the fist goes over where his head was
            ULT_LOW = 25,            // fully compressed under it
            ULT_BURST = 28,          // the stored compression releases
            ULT_CUT1 = 33,           // first contact: his blade across their flank, passing round them
            ULT_LAND1 = 39,
            ULT_LEAP2 = 44,
            ULT_CUT2 = 50,           // over the top: the blade down across their shoulders
            ULT_LAND2 = 56,
            ULT_DASH3 = 60,
            ULT_CUT3 = 64,           // the final fast pass
            ULT_FADE = 70,           // his form starts to lose its solidity
            ULT_GONE = 84,           // only the two red eyes are left in the moving shadow
            ULT_EYES_OUT = 92,
            ULT_CIRCLE = 88,         // the shadow starts circling the victim
            ULT_POOL = 100,          // the ground under them darkens into a pool
            ULT_RISE = 124,          // the first soldier starts to pull itself out of the dark
            ULT_PAUSE = 198,         // a held breath: everything slows
            ULT_ATTACK = 212,        // every shadow collapses onto the victim
            ULT_STORM = 220,
            ULT_FLASH = 236,         // red light bursting inside the storm
            ULT_COLLAPSE = 242,      // the shadow sinks back into the ground
            ULT_REVEAL = 252,
            ULT_REFORM = 280,        // two red eyes in a knot of shadow, at a distance
            ULT_SOLID = 300,         // his armour readable again
            ULT_TOTAL = 336;

    // ------------------------------------------------------------------ effects
    public static final int FX_SHURIKEN = 0, FX_SHURIKEN_HIT = 1, FX_SPIN = 2, FX_SHADOW_CAST = 3, FX_SWAP = 4, FX_MARK_LOCK = 5,
            FX_MARK_DASH = 6, FX_MARK_APPLY = 7, FX_MARK_POP = 8, FX_SLASH_HIT = 9, FX_PASSIVE = 10, FX_RETURN = 11, FX_SHADOW_END = 12,
            FX_MARK_VANISH = 13, FX_MARK_ARRIVE = 14;

    private ZedAction() {}
}
