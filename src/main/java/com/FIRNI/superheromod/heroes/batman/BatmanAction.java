package com.FIRNI.superheromod.heroes.batman;

/**
 * Everything Batman can be doing (Arkham games reference), shared by the server (which decides) and the clients (which
 * animate it from the synced clock), with every animation-coupled timing in one place. Gameplay numbers a server owner
 * may want to change (damage, ranges, counts, cooldowns, durations) are in BatmanConfig.
 * Keys: left click punches (the combo speeds up click after click into a rapid flurry); right click throws a Batarang
 * (held: up to five, more the longer it is held); R held opens the gadget wheel (smoke, flash, mine, wrist cannon,
 * sonic trap), R tapped uses the gadget picked; E takes out the grapnel gun (left click fires it at a block or a body,
 * E again puts it away; E while pulled along the line lets go with a hop up); CTRL rolls (a diving forward roll,
 * untouchable through it); SPACE held in the air spreads the cape and glides; SHIFT held runs. Thermal vision comes on
 * by itself in his own smoke.
 * No super powers: everything is equipment and fighting skill.
 */
public final class BatmanAction {
    public static final String ID = "batman";

    // ------------------------------------------------------------------ actions (what his body does)
    public static final int IDLE = 0, PUNCH = 1, BATARANG = 2, BATARANG_CHARGE = 3, BATARANG_MULTI = 4, GADGET_THROW = 5,
            MINE_PLACE = 6, GRAPNEL_AIM = 7, GRAPNEL_FIRE = 8, GRAPNEL_PULL = 9, GRAPNEL_STRIKE = 10, DODGE = 11, WHEEL = 12,
            /** Right click with the grapnel on a body: the line wraps their legs, his left hand hauls it in, they go down on their back. */
            GRAPNEL_YANK = 13,
            /** The dual wrist cannon (BatmanCannon): deploy, CANNON_FIRE ticks of aimed rapid fire, cool down and retract. */
            CANNON = 14,
            /** The sonic trap's remote (BatmanSonic): the remote raised, the red button pressed at SONIC_PRESS, lowered. */
            SONIC = 15;

    // ------------------------------------------------------------------ cooldown slots
    public static final int CD_SMOKE = 0, CD_FLASH = 1, CD_MINE = 2, CD_CANNON = 3, CD_SONIC = 4, CD_GRAPNEL = 5, CD_DODGE = 6, COOLDOWNS = 7;

    // ------------------------------------------------------------------ gadgets (the wheel, clockwise from the top)
    /** A gadget's cooldown slot is its own number (CD_SMOKE == G_SMOKE ...). */
    public static final int G_SMOKE = 0, G_FLASH = 1, G_MINE = 2, G_CANNON = 3, G_SONIC = 4, GADGETS = 5;
    public static final String[] GADGET_NAMES = {"Sis Bombası", "Flaş Bombası", "Mayın", "Bilek Topu", "Sonik Tuzak"};

    // ------------------------------------------------------------------ left click: the punches
    /**
     * The combo speeds up: the n-th blow in a row (0-based) lasts PUNCH_TICKS[min(n, last)] ticks and lands at
     * PUNCH_HIT of it; from RAPID on it is the flurry (alternating straights). A click within PUNCH_CHAIN ticks of the
     * end of a blow keeps the chain; a pause longer than that starts it again.
     */
    public static final int[] PUNCH_TICKS = {9, 8, 7, 6, 5, 4};
    public static final float PUNCH_HIT = .45f;
    public static final int RAPID = 5, PUNCH_CHAIN = 10;
    /** The blow the n-th punch of a chain is (for the pose): right straight, left straight, right hook, left uppercut, right elbow, then straights. */
    public static final int B_RIGHT = 0, B_LEFT = 1, B_HOOK = 2, B_UPPER = 3, B_ELBOW = 4, B_KNEE = 5;
    public static int blow(int n) {
        if (n >= RAPID) return n % 2 == 0 ? B_RIGHT : B_LEFT;
        return switch (n) { case 0 -> B_RIGHT; case 1 -> B_LEFT; case 2 -> B_HOOK; case 3 -> B_UPPER; default -> B_ELBOW; };
    }
    public static int punchTicks(int n) { return PUNCH_TICKS[Math.min(n, PUNCH_TICKS.length - 1)]; }

    // ------------------------------------------------------------------ right click: Batarangs
    /** Most he carries; one comes back every BATARANG_REFILL ticks. Held, one more every BATARANG_STEP ticks. */
    public static final int BATARANG_MAX = 5, BATARANG_REFILL = 40, BATARANG_STEP = 4;
    /** A single throw (the arm opens from across the body; released at BATARANG_AT); the many (both arms, at MULTI_AT). */
    public static final int BATARANG_TICKS = 9, BATARANG_AT = 3, MULTI_TICKS = 11, MULTI_AT = 4;

    // ------------------------------------------------------------------ R: gadgets
    /** R let go within TAP_TICKS uses the gadget; held longer opens the wheel. */
    public static final int TAP_TICKS = 5;
    public static final int GADGET_TICKS = 11, GADGET_AT = 4, MINE_TICKS = 14, MINE_AT = 7;
    /** A mine arms after MINE_ARM ticks. */
    public static final int MINE_ARM = 30;

    /** The wrist cannon: both gauntlets open (CANNON_DEPLOY), CANNON_FIRE ticks of fire, cooling and closing (CANNON_RETRACT). */
    public static final int CANNON_DEPLOY = 14, CANNON_FIRE = 80, CANNON_RETRACT = 18, CANNON_TICKS = CANNON_DEPLOY + CANNON_FIRE + CANNON_RETRACT;
    /** The sonic trap's remote: raised, the button pressed at SONIC_PRESS, lowered by SONIC_TICKS. */
    public static final int SONIC_PRESS = 9, SONIC_TICKS = 20;

    // ------------------------------------------------------------------ E: the grapnel
    /** The hook flies HOOK_SPEED blocks a tick; the slack line snaps taut over TAUT ticks; he is pulled at PULL_SPEED (accelerating from PULL_START). */
    public static final double HOOK_SPEED = 3.2, PULL_SPEED = 1.5, PULL_START = .4;
    public static final int TAUT = 3;
    /** Grapnel strike (the line on a body): reached, uppercut at STRIKE_UPPER, he jumps after them at STRIKE_JUMP, kick at STRIKE_KICK, backflip, lands by STRIKE_TICKS. */
    public static final int STRIKE_UPPER = 3, STRIKE_JUMP = 6, STRIKE_KICK = 14, STRIKE_FLIP = 16, STRIKE_TICKS = 34;
    /**
     * Grapnel yank (right click on a body): the line has wrapped their legs (tick 0); his left hand grabs the line and
     * hauls from YANK_PULL (the waist turning into it) and they are pulled off their feet at YANK_DOWN, then dragged
     * toward him (DRAG_TICKS, about DRAG_DIST blocks); they lie DOWN_TICKS in all before getting up; he recovers by YANK_TICKS.
     */
    public static final int YANK_PULL = 3, YANK_DOWN = 5, YANK_TICKS = 26, DRAG_TICKS = 18, DOWN_TICKS = 30;
    public static final double DRAG_DIST = 4.4;
    /** After the drag the line stays wound round them (BatmanBind): they cannot move until they break free. */
    /** Staggered (a stun): wobbling, slowed to 40 %, the next Batman blow lands as a critical (×STAGGER_CRIT). */
    public static final int STAGGER_TICKS = 30;
    /** After a stagger ends the daze mark stays over their head this long more (they move freely then). */
    public static final int DAZE_LINGER = 30;
    public static final float STAGGER_SLOW = .6f, STAGGER_CRIT = 1.5f;

    // ------------------------------------------------------------------ CTRL: the roll
    /**
     * The roll (Elden Ring style): a dive forward off one foot (DODGE_DIVE ticks in the air), over the shoulder along
     * the ground and up again by DODGE_TICKS; untouchable from DODGE_SAFE_FROM to DODGE_SAFE_TO; it covers DODGE_DIST blocks.
     */
    public static final int DODGE_TICKS = 16, DODGE_DIVE = 5, DODGE_SAFE_FROM = 1, DODGE_SAFE_TO = 12;
    public static final double DODGE_DIST = 7.0;

    // ------------------------------------------------------------------ SPACE: the glide
    /** Gliding: forward speed, sink speed, and how much a dive (looking down) speeds him up. */
    public static final double GLIDE_SPEED = .78, GLIDE_SINK = .045, GLIDE_DIVE = .9;
    /** Ticks for the cape to spread / fold. */
    public static final int CAPE_OPEN = 6;

    // ------------------------------------------------------------------ effects (BatmanFxPacket)
    public static final int FX_PUNCH = 0, FX_BATARANG = 1, FX_BATARANG_HIT = 2, FX_GADGET = 3, FX_SMOKE = 4, FX_FLASH = 5,
            FX_THERMAL = 6, FX_MINE = 7, FX_MINE_ARMED = 8, FX_MINE_BOOM = 9, FX_HOOK = 10, FX_HOOK_HIT = 11, FX_HOOK_END = 12,
            FX_STRIKE = 13, FX_DODGE = 14, FX_LAND = 15,
            /** Someone staggered (power = ticks), knocked down and dragged (dir = toward him, power = ticks), a critical hit. */
            FX_STAGGER = 16, FX_DOWNED = 17, FX_CRIT = 18,
            /** Bound by the grapnel line (entity = the bound one, power = ticks left, 0 = free; id = Batman). */
            FX_BOUND = 19;
    /** Effect kinds 20..29 belong to the wrist cannon (BatmanCannon / BatmanCannonFx), 30..39 to the sonic trap (BatmanSonic / BatmanSonicFx). */
    public static final int FX_CANNON_FIRST = 20, FX_CANNON_LAST = 29, FX_SONIC_FIRST = 30, FX_SONIC_LAST = 39;
    /** What his own client tells the server (BatmanInputPacket). */
    public static final int IN_GLIDE_ON = 0, IN_GLIDE_OFF = 1, IN_DODGE = 2, IN_GADGET_SELECT = 3, IN_GADGET_USE = 4,
            IN_GRAPNEL_TOGGLE = 5, IN_GRAPNEL_FIRE = 6, IN_WHEEL_OPEN = 7, IN_WHEEL_CLOSE = 8,
            /** A click of someone bound by his line, to break free (sent by anyone bound, Batman or not). */
            IN_BREAK_FREE = 9;

    private BatmanAction() {}

    /** Ticks an action lasts (0 = until something ends it). */
    public static int length(int action) {
        return switch (action) {
            case BATARANG -> BATARANG_TICKS;
            case BATARANG_MULTI -> MULTI_TICKS;
            case GADGET_THROW -> GADGET_TICKS;
            case MINE_PLACE -> MINE_TICKS;
            case GRAPNEL_STRIKE -> STRIKE_TICKS;
            case GRAPNEL_YANK -> YANK_TICKS;
            case DODGE -> DODGE_TICKS;
            case CANNON -> CANNON_TICKS;
            case SONIC -> SONIC_TICKS;
            default -> 0;
        };
    }
}
