package com.FIRNI.superheromod.heroes.panther;

/**
 * Everything Black Panther can be doing, shared by the server (which decides) and the clients (which
 * animate it from the synced clock), with every timing of his kit in one place. The gameplay numbers
 * a server owner may want to change (damage, distances, speeds, energy, cooldowns) are in PantherConfig.
 * Keys: left click claws (hold: frenzy), SHIFT pounce, Q the spinning triple kick, E the kinetic
 * release (the inventory key, like Thor's guard), R Panther Reflex, X kept free for a future ultimate.
 */
public final class PantherAction {
    public static final String ID = "black_panther";

    // ------------------------------------------------------------------ actions
    public static final int IDLE = 0,
            CLAW_RIGHT = 1, CLAW_LEFT = 2, CLAW_DOUBLE = 3, CLAW_UPPER = 4, FRENZY = 5,
            POUNCE_LOAD = 6, POUNCE = 7, POUNCE_FLIP = 8, POUNCE_KICK = 9, POUNCE_LAND = 10, POUNCE_MISS = 11,
            SPIN_LOAD = 12, SPIN = 13, SPIN_LAND = 14,
            RELEASE_CHARGE = 15, RELEASE = 16, RELEASE_RECOVER = 17,
            DODGE = 18;

    /** Which way a reflex dodge goes (sent as the state's flags while dodging). */
    public static final int DODGE_LEFT = 0, DODGE_RIGHT = 1, DODGE_BACK = 2, DODGE_CROUCH = 3, DODGE_BACK_LEFT = 4, DODGE_BACK_RIGHT = 5;

    // ------------------------------------------------------------------ cooldown slots
    public static final int CD_POUNCE = 0, CD_SPIN = 1, CD_RELEASE = 2, CD_REFLEX = 3, CD_FRENZY = 4, COOLDOWNS = 5;

    // ------------------------------------------------------------------ left click: Vibranium Claws
    /**
     * Right claw, left claw, both claws, then the claw uppercut. A click during a strike is remembered
     * and the next one starts as soon as this one may be broken off (CHAIN), out of its follow-through;
     * holding the button keeps clicking. After COMBO_RESET idle ticks the combo starts again.
     */
    public static final int CLAW_TICKS = 9, CLAW_HIT = 3, CLAW_CHAIN = 5,
            DOUBLE_TICKS = 10, DOUBLE_HIT = 4, DOUBLE_CHAIN = 6,
            UPPER_TICKS = 13, UPPER_HIT = 6, UPPER_CHAIN = 10, COMBO_RESET = 14;
    /** The frenzy: one strike every FRENZY_STRIKE ticks (hit FRENZY_HIT into it), alternating hands, for up to FRENZY_MAX ticks. */
    public static final int FRENZY_STRIKE = 4, FRENZY_HIT = 2, FRENZY_MAX = 60;

    // ------------------------------------------------------------------ SHIFT: Panther Pounce
    /**
     * The load (a blink), the pounce (flat and fast along his look), and on contact: the flip over the
     * target with a half twist so he comes down behind them facing their back, the kick, the landing.
     * A pounce that finds nobody ends in a rolling front flip and a light landing.
     */
    public static final int LOAD_TICKS = 2, POUNCE_MAX = 8, FLIP_TICKS = 8, KICK_TICKS = 7, KICK_HIT = 3, LAND_TICKS = 10, MISS_TICKS = 12;
    /** How long the hit-flash of the contact holds the moment (ticks, visual only). */
    public static final float CONTACT_HOLD = 1.6f;

    // ------------------------------------------------------------------ Q: the spinning triple kick
    /** The load, then one continuous spin in the air: right foot, left foot, right foot; the landing. */
    public static final int SPIN_LOAD_TICKS = 4, SPIN_TICKS = 17, SPIN_LAND_TICKS = 9;
    public static final int[] SPIN_KICKS = {4, 9, 14};
    /** How far round the body turns through the spin (degrees, starting side-on, the right leg leading). */
    public static final float SPIN_TURN = 540;

    // ------------------------------------------------------------------ E: Vibranium kinetic release
    public static final int CHARGE_TICKS = 14, RELEASE_TICKS = 8, RECOVER_TICKS = 14;

    // ------------------------------------------------------------------ R: Panther Reflex
    public static final int DODGE_TICKS = 6;
    /** Ticks between two dodges (a second one may start while the first is still finishing). */
    public static final int DODGE_GAP = 2;

    // ------------------------------------------------------------------ hit reactions (on him)
    public static final int HURT_TICKS = 10;

    // ------------------------------------------------------------------ effects
    public static final int FX_CLAW_HIT = 0, FX_POUNCE = 1, FX_CONTACT = 2, FX_KICK = 3, FX_LAUNCH = 4, FX_SCRAPE = 5,
            FX_SPIN_KICK = 6, FX_RELEASE = 7, FX_ABSORB = 8, FX_DODGE = 9, FX_REFLEX = 10, FX_LAND = 11, FX_UPPER = 12;

    private PantherAction() {}

    /** Ticks an action lasts (0 = until something ends it). */
    public static int length(int action) {
        return switch (action) {
            case CLAW_RIGHT, CLAW_LEFT -> CLAW_TICKS;
            case CLAW_DOUBLE -> DOUBLE_TICKS;
            case CLAW_UPPER -> UPPER_TICKS;
            case POUNCE_LOAD -> LOAD_TICKS;
            case POUNCE -> POUNCE_MAX;
            case POUNCE_FLIP -> FLIP_TICKS;
            case POUNCE_KICK -> KICK_TICKS;
            case POUNCE_LAND -> LAND_TICKS;
            case POUNCE_MISS -> MISS_TICKS;
            case SPIN_LOAD -> SPIN_LOAD_TICKS;
            case SPIN -> SPIN_TICKS;
            case SPIN_LAND -> SPIN_LAND_TICKS;
            case RELEASE_CHARGE -> CHARGE_TICKS;
            case RELEASE -> RELEASE_TICKS;
            case RELEASE_RECOVER -> RECOVER_TICKS;
            case DODGE -> DODGE_TICKS;
            default -> 0;
        };
    }
    /** Actions that may not be broken off by anything (a reflex still saves him, but without its dodge). */
    public static boolean critical(int action) {
        return action == POUNCE || action == POUNCE_FLIP || action == POUNCE_KICK || action == SPIN_LOAD || action == SPIN
                || action == RELEASE_CHARGE || action == RELEASE;
    }
    /** Strikes with the claws (the trails follow these). */
    public static boolean clawing(int action) {
        return action == CLAW_RIGHT || action == CLAW_LEFT || action == CLAW_DOUBLE || action == CLAW_UPPER || action == FRENZY;
    }
    /** Fast movement: the body leaves afterimages behind it. */
    public static boolean fast(int action) {
        return action == POUNCE || action == POUNCE_FLIP || action == POUNCE_KICK || action == SPIN || action == DODGE || action == FRENZY
                || action == POUNCE_MISS;
    }
}
