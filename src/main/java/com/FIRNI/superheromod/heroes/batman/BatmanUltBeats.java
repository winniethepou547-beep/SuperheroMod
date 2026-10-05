package com.FIRNI.superheromod.heroes.batman;

/**
 * KARA ŞÖVALYE (Batman's X film): its beats in ticks, shared by the server session (which holds both, hurts the target
 * and hands them back) and the client film (KnightPath times everything off these). Read top to bottom it is the film.
 * <p>
 * Fear: the target alone under one street lamp in a dead yard, rain, everything else black. They look right: Batman is
 * crouched on a fire escape and melts into the dark; a shape glides over the roofs; left: his silhouette on a roof edge,
 * gone in a burst of smoke; behind: high on the warehouse, gone in the blink of an eye; front again: he stands at the
 * edge of the light, eyes burning, smoke, gone. They back away.
 * The grapnel bites their back out of the dark and rips them up into the air; he comes over them, a sticky bomb on
 * their chest, under them, both feet into them; the bomb goes off and throws them both high over the city. Up there,
 * in the dark, they reach the top and start to fall; he glides beside them. He calls the Batwing: two lights far off,
 * then it roars in over them, circles above and rakes them with its guns as they fall (faster and faster); it tears
 * past and they come down trailing smoke. On a roof, his back to us, he raises a hand, a
 * Batarang, one throw: it takes them out of the air and pins them to the warehouse wall. He turns, fires the grapnel
 * up and is gone. The camera holds on the empty roof and the pinned body under the wall lamp; silence.
 */
public final class BatmanUltBeats {
    private BatmanUltBeats() {}

    public static final String ID = "batman:dark_knight";

    // ---- the fear: four glimpses (appears, seen, gone) and a shape over the roofs
    public static final int G1 = 40, PASS = 58, G2 = 78, G3 = 104, G4 = 132;
    /** Ticks after a glimpse starts that the target's eyes find him, and that he is gone. */
    public static final int SEEN = 6, GONE = 8;
    /** They back away from the dark in front of them. */
    public static final int BACK = 142;
    // ---- the grapnel
    public static final int FIRE = 160, BITE = 164, YANK = 167, LEAP = 172;
    // ---- the sticky bomb and the kick
    public static final int PLANT = 182, APEX = 186, UNDER = 192, KICK = 198, BLAST = 201;
    // ---- high in the dark
    public static final int HIGH = 252;
    // ---- the Batwing (SCAN..SCANNED: its guns firing as it circles)
    public static final int CALL = 296, SIGNAL = 302, LIGHTS = 312, ARRIVE = 328, SCAN = 336, SCANNED = 392, PASS_BY = 398, DROP = 401;
    // ---- the Batarang
    public static final int ROOF = 414, RAISE = 422, THROW = 434, HIT = 444, WALL = 450;
    // ---- gone
    public static final int TURN = 462, GUN = 470, FIRE2 = 478, BITE2 = 482, HAUL = 484, CABLE = 494;
    /** The stage ends (fading to black before it), then a moment of the world to hand back gently. */
    public static final int STAGE_END = 560, TOTAL = 580;
}
