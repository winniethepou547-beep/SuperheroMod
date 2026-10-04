package com.FIRNI.superheromod.heroes.magneto;

/**
 * Everything Magneto can be doing, shared by the server (which decides) and the clients (which animate it from the
 * synced clock), with every timing of his kit in one place. The gameplay numbers a server owner may want to change
 * (damage, ranges, counts, cooldowns, speeds) are in MagnetoConfig.
 * Keys: SHIFT (or jump twice) lifts him into flight and sets him down again; while flying, jump rises and the sprint
 * key (CTRL) sinks. Left click flicks a shard of metal (it punches with the iron fist, throws the one he holds).
 * Q Iron Barrage (three charges), E Metal Scrap Telekinesis (the inventory key, like Thor's guard), R the Giant Iron
 * Fist (left click punches, five times), F the Magnetic Iron Shield (again: it bursts outward), X Magnetic Execution.
 */
public final class MagnetoAction {
    public static final String ID = "magneto";

    // ------------------------------------------------------------------ actions (what his body does)
    public static final int IDLE = 0, SHARD = 1, BARRAGE = 2, GRAB = 3, CONTROL = 4, THROW = 5, FIST_SUMMON = 6, FIST = 7,
            SHIELD_RAISE = 8, SHIELD = 9, BURST = 10;

    // ------------------------------------------------------------------ cooldown slots
    public static final int CD_SHARD = 0, CD_BARRAGE = 1, CD_GRAB = 2, CD_FIST = 3, CD_SHIELD = 4, CD_ULT = 5, COOLDOWNS = 6;

    // ------------------------------------------------------------------ flight
    /** Ticks to go from standing to full hover (the lift) and the slow bob of the hover (ticks per cycle). */
    public static final int LIFT_TICKS = 12, BOB_CYCLE = 64;

    // ------------------------------------------------------------------ left click: a shard of metal
    public static final int SHARD_TICKS = 7, SHARD_AT = 2;

    // ------------------------------------------------------------------ Q: Iron Barrage
    /** The cast (an arm raised, then driven down: the rods come), when the first rod appears, the gap between rods. */
    public static final int BARRAGE_TICKS = 12, BARRAGE_AT = 5, ROD_GAP = 2;
    /** How long a rod stays standing in the ground after it struck, and how long it takes to sink and go. */
    public static final int ROD_STAY = 120, ROD_SINK = 20;

    // ------------------------------------------------------------------ E: Metal Scrap Telekinesis
    /** The reach (the hand toward them), the scrap flying in from behind them to wrap them, then the hold. */
    public static final int GRAB_TICKS = 8, SCRAP_FLY = 9, THROW_TICKS = 8, SLAM_GAP = 6;

    // ------------------------------------------------------------------ R: Giant Iron Fist
    /** The fist assembling out of the scrap, a punch (down, the blow, back up), the fist falling apart at the end. */
    public static final int FIST_SUMMON_TICKS = 14, PUNCH_DOWN = 4, PUNCH_HOLD = 3, PUNCH_BACK = 9, FIST_BREAK = 16;
    public static final int PUNCH_TICKS = PUNCH_DOWN + PUNCH_HOLD + PUNCH_BACK;

    // ------------------------------------------------------------------ F: Magnetic Iron Shield
    /** The columns rising out of the ground round him, and the burst (his arms flung out, the columns torn apart). */
    public static final int SHIELD_RAISE_TICKS = 14, BURST_TICKS = 10;
    /** How long a shard of the burst stays stuck where it hit, and how long it takes to go. */
    public static final int SHARD_STAY = 100, SHARD_FADE = 20;

    // ------------------------------------------------------------------ X: Magnetic Execution (film beats, film ticks)
    /**
     * The film (MagneticExecutionFilm, ExecutionStage): a dead world under a red storm, skulls and wreckage, rain.
     *   OPEN    the wasteland in lightning; he stands at its heart, metal circling him in orbits, calm
     *   RAISE   one hand rises slowly; the orbits quicken
     *   LAUNCH  the metal hurls at the target, takes their arms and legs and pulls them apart into an X, lifts them
     *   XCUT    his hands cross down through the air in an X; two great iron pillars appear behind the target
     *   SLAM    the pillars come down crossed into a giant X, driven into the ground (sparks, water, lightning)
     *   PIN     the target is drawn onto the X and held there
     *   TURN    he turns his back on them; the camera behind him: him, and the X beyond
     *   WALK    he walks toward the camera; FIST: he raises a hand and closes it
     *   FLICK   a small flick of the hand, as if throwing away rubbish: the pillars fold together, crushing the target
     *           into a ball of crumpled metal, which flies off far into the storm; he never looks back
     */
    public static final int ULT_RAISE = 60, ULT_LAUNCH = 96, ULT_PULL = 112, ULT_XCUT = 150, ULT_SLAM = 166, ULT_PIN = 182, ULT_TURN = 214,
            ULT_WALK = 238, ULT_FIST = 282, ULT_FLICK = 306, ULT_CRUSH = 312, ULT_BALL = 336, ULT_HURL = 348;
    /** Where the film's own stage ends (a last moment in the real world hands the camera back) and its whole length. */
    public static final int ULT_STAGE_END = 400, ULT_TOTAL = 412;

    // ------------------------------------------------------------------ effects
    public static final int FX_SHARD = 0, FX_SHARD_HIT = 1, FX_ROD = 2, FX_ROD_HIT = 3, FX_GRAB = 4, FX_SLAM = 5, FX_RELEASE = 6,
            FX_FIST_UP = 7, FX_PUNCH = 8, FX_FIST_BREAK = 9, FX_COLUMNS = 10, FX_BLOCK = 11, FX_BURST = 12, FX_PIECE = 13, FX_PIECE_HIT = 14,
            FX_LIFT = 15, FX_LAND = 16;
    /** What his own client tells the server (MagnetoInputPacket). */
    public static final int INPUT_FLIGHT = 0;

    private MagnetoAction() {}

    /** Ticks an action lasts (0 = until something ends it). */
    public static int length(int action) {
        return switch (action) {
            case SHARD -> SHARD_TICKS;
            case BARRAGE -> BARRAGE_TICKS;
            case GRAB -> GRAB_TICKS;
            case THROW -> THROW_TICKS;
            case FIST_SUMMON -> FIST_SUMMON_TICKS;
            case SHIELD_RAISE -> SHIELD_RAISE_TICKS;
            case BURST -> BURST_TICKS;
            default -> 0;
        };
    }
}
