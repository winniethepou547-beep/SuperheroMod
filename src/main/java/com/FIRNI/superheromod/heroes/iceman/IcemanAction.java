package com.FIRNI.superheromod.heroes.iceman;

/**
 * Everything Iceman can be doing (X-Men: Days of Future Past reference: a body of living ice), shared by the server
 * (which decides) and the clients (which animate it from the synced clock), with every animation-coupled timing in one
 * place. Gameplay numbers a server owner may want to change (damage, ranges, frost amounts, cooldowns, durations) are
 * in IcemanConfig.
 * <p>
 * Keys: left click swings the ice weapon in hand (two swings, right to left then left to right, then the weapon's own
 * finisher; held: the mace grows, the spear is drawn back for a throw, the sword is driven into the ground and pulls
 * everyone near toward it); right click held is
 * the cryogenic brush (on a body: a stream of cold that frosts them; into the air: ice sculpted along the mouse's path);
 * E held opens the Ice Armory wheel (mace, spear, sword; nothing else is on E); SHIFT held surfs on an ice slide that
 * grows under him; CTRL is the short sub-zero slide; Q the cryogenic shell (Q again bursts it); R shattered ground;
 * X is not designed yet.
 * <p>
 * Every enemy carries a FROST METER (0..100, IcemanFrost); at 100 they are DEEP FROZEN for a moment.
 */
public final class IcemanAction {
    public static final String ID = "iceman";

    // ------------------------------------------------------------------ actions (what his body does)
    public static final int IDLE = 0,
            /** RMB held: both hands raised, the brush (State.brushTarget: a body, or -1 sculpting into the air). */
            BRUSH = 1,
            /** SHIFT held: surfing on the ice slide (his own client steers). */
            SLIDE = 2,
            /** CTRL: the short sub-zero slide (his own client steers). */
            DASH = 3,
            /** The weapon picked forming in his hand out of crystals. */
            FORM = 4,
            /** A swing of the combo (State.combo 0, 1 = the two swings, 2 = the weapon's finisher). */
            STRIKE = 5,
            /** LMB held: the mace grows / the spear is drawn back / the sword is planted in the ground (State.charge 0..1). */
            CHARGE = 6,
            /** Let go after a hold: the giant mace's slam / the spear's throw / the planted sword cracking and shattering. */
            RELEASE = 7,
            /** Q: the shell forming (feet to head), holding, broken (he drops into the tired landing and rises), bursting. */
            SHELL_FORM = 8, SHELL = 9, SHELL_BREAK = 10, SHELL_BURST = 11,
            /** R: hands to the ground, the cracks run out to the target, the spikes burst up. */
            GROUND = 12,
            /** The weapon wheel is open (E held). */
            WHEEL = 13,
            /** SHIFT let go on the ground: the braking (a foot dragged, ice spray, the body turning) back into the stance. */
            SLIDE_END = 14;

    // ------------------------------------------------------------------ cooldown slots
    public static final int CD_DASH = 0, CD_SHELL = 1, CD_GROUND = 2, CD_SLIDE = 3, CD_WEAPON = 4, CD_BRUSH = 5, COOLDOWNS = 6;

    // ------------------------------------------------------------------ the Ice Armory (E wheel, clockwise from the top)
    public static final int W_MACE = 0, W_SPEAR = 1, W_SWORD = 2, WEAPONS = 3;
    public static final String[] WEAPON_NAMES = {"Buz Gürzü", "Buz Mızrağı", "Buz Kılıcı"};

    // ------------------------------------------------------------------ input from his own client (IcemanInputPacket)
    public static final int IN_WHEEL_OPEN = 1, IN_WHEEL_CLOSE = 2, IN_WEAPON_SELECT = 3, IN_SLIDE_ON = 4, IN_SLIDE_OFF = 5, IN_DASH = 6,
            /** His client's view of where the sculpting brush is (the server checks it is near his own aim). */
            IN_SCULPT_POINT = 7;

    // ------------------------------------------------------------------ state flags (synced)
    public static final int F_WHEEL = 1, F_WEAPON = 2, F_SHELL = 4, F_SCULPTING = 8, F_SLIDING = 16;

    // ------------------------------------------------------------------ the frost meter (IcemanFrost)
    public static final float FROST_MAX = 100;
    /** The stages the body and the screen show: 0 none, 1 from 1, 2 from 25, 3 from 50, 4 from 75, 5 deep frozen. */
    public static int stage(float frost, boolean deep) {
        if (deep) return 5;
        if (frost >= 75) return 4;
        if (frost >= 50) return 3;
        if (frost >= 25) return 2;
        return frost > .5f ? 1 : 0;
    }
    /** Deep freeze: how long the ice holds them (ticks), and how long they cannot be deep frozen again after. */
    public static final int DEEP_TICKS = 44, DEEP_IMMUNE = 80;
    /** The encasing closes over DEEP_CLOSE ticks; the breaking out takes DEEP_BREAK ticks. */
    public static final int DEEP_CLOSE = 5, DEEP_BREAK = 10;

    // ------------------------------------------------------------------ RMB: the brush
    /** Both hands come up over BRUSH_RAISE ticks before anything flows. */
    public static final int BRUSH_RAISE = 5;
    /** A sculpture keeps at most this many points along its path; a new point every SCULPT_STEP blocks of movement. */
    public static final int SCULPT_POINTS = 56;
    public static final float SCULPT_STEP = .38f;
    /** A sculpture's point grows: a line (0..LINE), thickening (..THICK), volume (..VOLUME), crystallising (..CRYSTAL), in ticks after it was laid. */
    public static final float SCULPT_LINE = 3, SCULPT_THICK = 8, SCULPT_VOLUME = 15, SCULPT_CRYSTAL = 26;
    /** The cracks run over it for SCULPT_CRACK ticks before it breaks apart. */
    public static final int SCULPT_CRACK = 14;

    // ------------------------------------------------------------------ SHIFT: the 3D ice slide (Days of Future Past)
    /**
     * SHIFT pressed: SLIDE_PREP ticks of getting ready (the weight down, knees bending, one arm forward, the other back,
     * frost under the feet, the ice growing out ahead) before he moves; then he speeds up (SLIDE_ACCEL a tick) to
     * SLIDE_SPEED along the mouse's way, gaining on the way down (SLIDE_GRAVITY times the slope), losing a little in sharp
     * turns (SLIDE_TURN_LOSS per radian). He never follows the look's pitch: SPACE held raises the track (its pitch climbs
     * SLIDE_PITCH_RATE a tick up to SLIDE_ASCENT_MAX radians); let go, it bends over a short crest and down again
     * (SLIDE_PITCH_FALL a tick down to -SLIDE_DESCENT_MAX) until it meets the ground and runs along it.
     */
    public static final int SLIDE_PREP = 7;
    public static final float SLIDE_SPEED = .82f, SLIDE_ACCEL = .032f, SLIDE_GRAVITY = .05f, SLIDE_TURN_LOSS = .9f;
    public static final float SLIDE_ASCENT_MAX = .72f, SLIDE_PITCH_RATE = .045f, SLIDE_PITCH_FALL = .03f, SLIDE_DESCENT_MAX = .6f;
    /** Kept for older callers: how much of the look's pitch the slide once followed (no longer used for climbing). */
    public static final float SLIDE_CLIMB = 0;
    /** The track stays SLIDE_TRACK_LIFE ticks, then dissolves from its oldest end (cracks, shards, frost) over SLIDE_TRACK_MELT. */
    public static final int SLIDE_TRACK_LIFE = 160, SLIDE_TRACK_MELT = 40;
    /** SHIFT let go on the ground: the braking (SLIDE_END) lasts SLIDE_END_TICKS. */
    public static final int SLIDE_END_TICKS = 14;
    /** The test autopilot (/iceman test shift...): what it does. */
    public static final int AUTO_SHIFT = 0, AUTO_UP = 1, AUTO_CANCEL = 2, AUTO_AIR = 3, AUTO_DESCEND = 4, AUTO_SPEED = 5, AUTO_SLOPE = 6;

    // ------------------------------------------------------------------ CTRL: the sub-zero slide
    public static final int DASH_TICKS = 13;
    public static final float DASH_DIST = 8.5f;
    /** It hits the first body it touches (from DASH_HIT_FROM on), once. */
    public static final int DASH_HIT_FROM = 2;

    // ------------------------------------------------------------------ LMB: the weapons
    /** A tap shorter than HOLD_TICKS swings; held longer it charges. */
    public static final int HOLD_TICKS = 6;
    /** The weapon forms in his hand (cold gathering, crystal nuclei, the crystals growing and joining into it). */
    public static final int FORM_TICKS = 14;
    /** A click within COMBO_CHAIN ticks of the end of a swing keeps the combo going. */
    public static final int COMBO_CHAIN = 12;
    /** Swings: [weapon][combo] length (ticks) and when it lands (ticks). Mace heavy, spear fast, sword between. */
    public static final int[][] SWING_TICKS = {{15, 15, 26}, {9, 9, 22}, {11, 11, 16}};
    public static final int[][] SWING_HIT = {{8, 8, 15}, {4, 4, 6}, {6, 6, 6}};
    /** The spear's flurry (its finisher): thrusts at these ticks. */
    public static final int[] SPEAR_FLURRY = {6, 9, 12, 14, 16, 18};
    /** The sword's finisher spin: hits everyone round him at SWING_HIT, the spin lasts its whole swing. */
    /** Holds: the mace grows to full over MACE_GROW ticks; the spear is fully drawn at SPEAR_DRAW; the sword stays planted at most SPIN_MAX ticks of the hold. */
    public static final int MACE_GROW = 60, SPEAR_DRAW = 28, SPIN_MAX = 90;
    /** The mace at full size is MACE_MAX times its plain size (about three times his height). */
    public static final float MACE_MAX = 4.2f;
    /** Releases: the giant mace's slam lands at MACE_SLAM_HIT of MACE_SLAM_TICKS; the spear leaves the hand at SPEAR_THROW_AT. */
    public static final int MACE_SLAM_TICKS = 28, MACE_SLAM_HIT = 14, SPEAR_THROW_TICKS = 12, SPEAR_THROW_AT = 4;
    /** The thrown spear stays stuck SPEAR_STUCK ticks (the spikes burst at once), cracking for the last SPEAR_CRACK. */
    public static final int SPEAR_STUCK = 46, SPEAR_CRACK = 14;
    /**
     * The sword held (counted in ticks of the hold, after HOLD_TICKS): the sword turns point down in his hand over
     * PLANT_TURN, comes up before him by PLANT_RAISE, and is driven into the ground at PLANT_AT while he drops into a
     * kneel; then it stays planted (the pull) until LMB is let go or SPIN_MAX.
     */
    public static final int PLANT_TURN = 4, PLANT_RAISE = 5, PLANT_AT = 9;
    /** Where the planted sword stands: PLANT_REACH blocks ahead of his feet; its grip PLANT_GRIP blocks over the ground. */
    public static final float PLANT_REACH = .56f, PLANT_GRIP = .67f;
    /** While planted: a blow every PLANT_HIT ticks to anyone dragged within PLANT_HURT blocks of the sword. */
    public static final int PLANT_HIT = 10;
    public static final float PLANT_HURT = 1.7f;
    /** Let go: the planted sword cracks for SWORD_CRACK_TICKS, then shatters; the release lasts SWORD_BREAK_TICKS (he rises). */
    public static final int SWORD_CRACK_TICKS = 5, SWORD_BREAK_TICKS = 22;
    /** A weapon broken in his hand (another weapon picked, another power used, the mace's handle after a slam) cracks this long before it falls apart. */
    public static final int WEAPON_CRACK_TICKS = 6;

    // ------------------------------------------------------------------ Q: the shell
    public static final int SHELL_FORM_TICKS = 18;
    /** The burst: the inner stress builds for BURST_STRESS ticks, then KRAAAK; the action lasts BURST_TICKS. */
    public static final int BURST_STRESS = 14, BURST_TICKS = 30;
    /** Broken: the fall into the tired hero landing (BREAK_LAND), held, then the slow rise; all BREAK_TICKS. */
    public static final int BREAK_LAND = 8, BREAK_RISE = 38, BREAK_TICKS = 58;

    // ------------------------------------------------------------------ R: shattered ground
    /** Hands to the ground at GROUND_DOWN; the cracks set off from his feet then; the action lasts GROUND_TICKS. */
    public static final int GROUND_DOWN = 7, GROUND_TICKS = 26;
    /** The cracks travel GROUND_SPEED blocks a tick; the spikes erupt over GROUND_ERUPT ticks where they arrive. */
    public static final float GROUND_SPEED = .85f;
    public static final int GROUND_ERUPT = 5, GROUND_SPIKE_LIFE = 50;

    // ------------------------------------------------------------------ effects (IcemanFxPacket kinds)
    public static final int FX_FROST = 1,           // a body's frost meter (power = meter, id = deep-freeze ticks left, dir.x = ticks since deep froze began or -1)
            FX_DEEP_FREEZE = 2,                     // a body froze solid (pos = feet, power = width, dir.y = height)
            FX_DEEP_BREAK = 3,                      // the ice round a body broke (power: 1 by itself, 2 shattered by a blow)
            FX_LENS = 4,                            // to the victim only: their screen frosts over (power = strength, id = ticks, entity = Iceman)
            FX_HIT = 5,                             // a weapon hit (power = weapon, id = combo / kind)
            FX_SHATTER = 6,                         // ice breaking apart at a place (power = size, dir = push, id = how: 0 plain, 1 weapon, 2 sculpture, 3 shell, 4 spike)
            FX_FORM = 7,                            // the weapon starts forming in his hand (power = weapon)
            FX_SCULPT_POINT = 8,                    // a sculpture's new point (id = sculpture, power = index, dir.x = thickness)
            FX_SCULPT_END = 9,                      // the sculpture let go (id; power = its lifetime ticks)
            FX_SCULPT_BREAK = 10,                   // a sculpture breaking up (id)
            FX_SLIDE_HIT = 11,                      // a body touched by the slide (entity)
            FX_DASH = 12,                           // the sub-zero slide began (power = yaw)
            FX_DASH_HIT = 13,                       // the sub-zero slide struck someone
            FX_SLAM = 14,                           // a mace slam (power = size, the third swing 1)
            FX_SPEAR = 15,                          // the spear thrown (pos, dir = velocity, power = charge, id)
            FX_SPEAR_STUCK = 16,                    // the spear stuck (pos, dir = way it flew, entity = body or -1, id)
            FX_SPEAR_SPIKES = 17,                   // the spikes bursting round the spear (pos, power = radius)
            FX_SPIN = 18,                           // the sword's finisher spin (power 0)
            FX_SWORD_BREAK = 19,                    // the planted sword cracks then shatters (pos = where it stands, dir = his facing, power = charge, id = 1 planted, 0 still in hand)
            FX_SHELL_HIT = 20,                      // the shell struck (power = damage, dir = from)
            FX_SHELL_BREAK = 21,                    // the shell broken to pieces
            FX_SHELL_BURST = 22,                    // the shell's burst
            FX_GROUND = 23,                         // shattered ground: the cracks set off (pos = from, dir = to, power = frost bonus, id)
            FX_GROUND_ERUPT = 24,                   // the spikes burst (pos, power = size, id)
            FX_BRUSH_FROST = 25,                    // a burst of frost on a body from the brush (entity)
            FX_AUTO_SLIDE = 26,                     // to Iceman only (the test): his client rides the slide by itself (id = AUTO_* mode)
            FX_SWORD_PLANT = 27,                    // the sword driven into the ground (pos = the ground point it stands in, dir = his facing, entity = Iceman, id)
            FX_WEAPON_BREAK = 28;                   // the weapon in his hand breaks (pos = hand, dir = push, power = size, entity = Iceman, id = weapon | how << 4: 0 whole, 1 the mace's handle left after a slam)
}
