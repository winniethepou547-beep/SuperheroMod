package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import net.minecraft.util.Mth;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.*;

/**
 * Magneto's body as numbers, on the same joints and signs as Black Panther's (PantherMotion.Pose; the conventions are at
 * the top of PantherMotion). He moves little and with complete calm: an upright, regal stance; every cast is one
 * unhurried gesture of a hand, and the metal does the violence. In flight he floats with his arms opened wide, the legs
 * together and the toes pointed down, as in the painting he was made from.
 */
public final class MagnetoMotion {
    private MagnetoMotion() {}

    private static float noise(float x) { return Mth.sin(x * 1.7f) * .5f + Mth.sin(x * 3.1f + 1.3f) * .3f + Mth.sin(x * 7.3f + 4.1f) * .2f; }

    /** Standing: tall and straight, the chest up, the chin a touch raised, the arms easy at his sides, the hands half open. */
    public static Pose stance(float time) {
        Pose p = new Pose();
        p.set(PLANT, 1).set(SPINE_PITCH, -.03f).set(CHEST_PITCH, -.05f).set(HEAD_PITCH, -.08f).set(NECK, .1f).set(EYES, 0);
        p.leg(0, LEG_X, .02f).leg(0, LEG_Z, .04f).leg(1, LEG_X, -.06f).leg(1, LEG_Z, .05f).leg(1, LEG_Y, .1f).leg(1, KNEE, .06f);
        for (int side = 0; side < 2; side++)
            p.arm(side, ARM_X, -.05f).arm(side, ARM_Z, .14f).arm(side, ELBOW, .22f).arm(side, WRIST_X, -.05f).arm(side, CURL, .38f);
        float breath = Mth.sin(time * .08f);
        p.add(CHEST_PITCH, -.018f * breath);
        for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, .12f * breath);
        p.add(HEAD_YAW, .03f * noise(time * .02f)).add(HEAD_PITCH, .015f * noise(time * .027f + 3));
        return p;
    }
    /** Floating: arms opened wide and a little raised, palms open, legs together, toes down, a slow drift of the whole body. */
    public static Pose fly(float time, float speed) {
        Pose p = new Pose();
        float drift = Mth.sin(time * Mth.TWO_PI / BOB_CYCLE);
        p.set(PLANT, 0).set(SPINE_PITCH, -.04f + .25f * speed).set(CHEST_PITCH, -.08f).set(HEAD_PITCH, -.12f - .15f * speed).set(NECK, .1f)
                .set(ROOT_PITCH, .35f * speed);
        for (int side = 0; side < 2; side++) {
            p.arm(side, SH_FWD, -.3f).arm(side, ARM_X, -.32f + .2f * speed).arm(side, ARM_Z, 1.2f - .45f * speed).arm(side, ARM_Y, .25f)
                    .arm(side, ELBOW, .12f).arm(side, WRIST_X, -.25f).arm(side, CURL, .12f);
            p.leg(side, LEG_X, .04f + .05f * side).leg(side, LEG_Z, .02f).leg(side, KNEE, .08f + .06f * side).leg(side, ANKLE, .55f);
        }
        // Alive: the arms breathe up and down a little, the legs sway, the fingers stir.
        p.add(ROOT_ROLL, .025f * Mth.sin(time * .045f)).add(CHEST_PITCH, -.02f * drift);
        for (int side = 0; side < 2; side++) {
            p.armAdd(side, ARM_Z, .05f * Mth.sin(time * .06f + side * 1.3f)).armAdd(side, CURL, .08f * Mth.sin(time * .09f + side));
            p.legAdd(side, LEG_X, .03f * Mth.sin(time * .05f + side * 2));
        }
        return p;
    }

    /** A move's pose at its clock t over the base (the stance or the hover). */
    public static Pose sample(int action, float t, Pose base) {
        return switch (action) {
            case SHARD -> shard(base).sample(t);
            case BARRAGE -> barrage(base).sample(t);
            case GRAB -> grab(base).sample(t);
            case CONTROL -> control(base, t);
            case THROW -> throwIt(base).sample(t);
            case FIST_SUMMON -> summon(base).sample(t);
            case FIST -> fist(base, t);
            case SHIELD_RAISE -> raise(base).sample(t);
            case SHIELD -> shieldHold(base, t);
            case BURST -> burst(base).sample(t);
            default -> base;
        };
    }

    /** A flick of the right hand: from the side up and out toward the aim, the fingers opening, then back. */
    static Track shard(Pose base) {
        Pose cock = base.copy().add(CHEST_YAW, .15f);
        cock.arm(0, ARM_X, -.6f).arm(0, ARM_Z, .5f).arm(0, ELBOW, 1.1f).arm(0, CURL, .8f).arm(0, WRIST_X, -.4f);
        Pose flick = base.copy().add(CHEST_YAW, -.12f);
        flick.arm(0, SH_FWD, 1.2f).arm(0, ARM_X, -1.5f).arm(0, ARM_Z, -.05f).arm(0, ELBOW, .15f).arm(0, CURL, .05f).arm(0, WRIST_X, .25f);
        return new Track().key(0, base).key(1.2f, cock).key(SHARD_AT, flick).key(4.5f, flick.copy().arm(0, ARM_X, -1.35f)).key(SHARD_TICKS + 2, base);
    }
    /** The barrage: the right hand raised slowly overhead, palm up; then driven down and forward: the rods fall. */
    static Track barrage(Pose base) {
        Pose up = base.copy().add(CHEST_PITCH, -.12f).add(HEAD_PITCH, -.2f);
        up.arm(0, SH_UP, 1.2f).arm(0, ARM_X, -2.85f).arm(0, ARM_Z, .25f).arm(0, ELBOW, .2f).arm(0, WRIST_X, -.5f).arm(0, CURL, .25f);
        up.arm(1, ARM_Z, .4f).arm(1, ARM_X, -.2f).arm(1, CURL, .45f);
        Pose down = base.copy().add(CHEST_PITCH, .08f).add(SPINE_PITCH, .06f);
        down.arm(0, SH_FWD, 1.0f).arm(0, ARM_X, -1.15f).arm(0, ARM_Z, -.05f).arm(0, ELBOW, .1f).arm(0, WRIST_X, .45f).arm(0, CURL, .1f);
        down.arm(1, ARM_Z, .45f).arm(1, ARM_X, -.15f);
        return new Track().key(0, base).key(3.5f, up).key(BARRAGE_AT - .5f, up.copy().arm(0, ARM_X, -2.95f)).key(BARRAGE_AT + 1, down)
                .key(BARRAGE_TICKS - 2, down).key(BARRAGE_TICKS + 4, base);
    }
    /** Reaching for them: the right arm out toward them, the fingers closing like a claw round something unseen. */
    static Track grab(Pose base) {
        Pose reach = base.copy().add(CHEST_YAW, -.1f);
        reach.arm(0, SH_FWD, 1.4f).arm(0, ARM_X, -1.55f).arm(0, ARM_Z, -.05f).arm(0, ELBOW, .08f).arm(0, CURL, .1f).arm(0, WRIST_X, .1f);
        Pose take = reach.copy();
        take.arm(0, ELBOW, .45f).arm(0, CURL, .75f).arm(0, SH_FWD, .9f);
        return new Track().key(0, base).key(2.5f, reach).key(GRAB_TICKS - 1, take).key(GRAB_TICKS, take);
    }
    /** Holding them: the clawed hand out toward them, following them (the layer turns it with the aim), unmoved. */
    static Pose control(Pose base, float t) {
        Pose p = base.copy().add(CHEST_YAW, -.1f);
        p.arm(0, SH_FWD, 1.0f).arm(0, ARM_X, -1.5f).arm(0, ARM_Z, -.05f).arm(0, ELBOW, .4f).arm(0, CURL, .78f).arm(0, WRIST_X, .05f);
        // The effort of it shows only in the fingers.
        p.armAdd(0, CURL, .05f * Mth.sin(t * .9f));
        return p;
    }
    /** The throw: the clawed hand swept forward and open, flinging them. */
    static Track throwIt(Pose base) {
        Pose back = control(base, 0).add(CHEST_YAW, .2f);
        back.arm(0, ARM_X, -1.3f).arm(0, ARM_Z, .5f).arm(0, ELBOW, 1.0f);
        Pose fling = base.copy().add(CHEST_YAW, -.3f);
        fling.arm(0, SH_FWD, 1.4f).arm(0, ARM_X, -1.4f).arm(0, ARM_Z, -.45f).arm(0, ARM_Y, -.3f).arm(0, ELBOW, .05f).arm(0, CURL, 0);
        return new Track().key(0, back).key(2, fling).key(THROW_TICKS + 3, base);
    }
    /** The fist assembling: both hands rising before him, palms up, then closing slowly into fists. */
    static Track summon(Pose base) {
        Pose lift = base.copy().add(HEAD_PITCH, -.15f);
        for (int side = 0; side < 2; side++)
            lift.arm(side, SH_FWD, .8f).arm(side, ARM_X, -1.1f).arm(side, ARM_Z, .35f).arm(side, ELBOW, .6f).arm(side, WRIST_X, -.6f).arm(side, CURL, .1f);
        Pose close = lift.copy();
        for (int side = 0; side < 2; side++) close.arm(side, ARM_X, -1.35f).arm(side, CURL, 1);
        return new Track().key(0, base).key(6, lift).key(FIST_SUMMON_TICKS - 2, close).key(FIST_SUMMON_TICKS, fist(base, 0));
    }
    /** Driving the fist: the right fist held out toward it (the layer turns it with the aim; the punch is added there). */
    static Pose fist(Pose base, float t) {
        Pose p = base.copy().add(CHEST_YAW, -.08f);
        p.arm(0, SH_FWD, 1.0f).arm(0, ARM_X, -1.5f).arm(0, ARM_Z, -.05f).arm(0, ELBOW, .3f).arm(0, CURL, 1).arm(0, WRIST_X, 0);
        p.arm(1, ARM_Z, .3f).arm(1, CURL, .6f);
        return p;
    }
    /** The punch layered on the fist pose (age = ticks since the click): the arm driven down hard, then back up. */
    public static void punch(Pose p, float age) {
        if (age < 0 || age > PUNCH_TICKS + 2) return;
        float down = age < PUNCH_DOWN ? PantherMotion.ease(age / PUNCH_DOWN) : 1 - PantherMotion.ease((age - PUNCH_DOWN - PUNCH_HOLD) / PUNCH_BACK);
        p.armAdd(0, ARM_X, .8f * down).armAdd(0, SH_FWD, .4f * down).add(CHEST_PITCH, .1f * down).add(SPINE_PITCH, .05f * down);
    }
    /** The columns lifted: both arms rising from his sides, palms up, as if raising something very heavy without effort. */
    static Track raise(Pose base) {
        Pose low = base.copy();
        for (int side = 0; side < 2; side++) low.arm(side, ARM_Z, .5f).arm(side, ARM_X, -.3f).arm(side, ELBOW, .3f).arm(side, WRIST_X, -.7f).arm(side, CURL, .2f);
        Pose high = low.copy().add(HEAD_PITCH, -.12f);
        for (int side = 0; side < 2; side++) high.arm(side, ARM_Z, .95f).arm(side, ARM_X, -.75f).arm(side, ELBOW, .4f);
        return new Track().key(0, base).key(4, low).key(SHIELD_RAISE_TICKS - 1, high).key(SHIELD_RAISE_TICKS + 4, shieldHold(base, 0));
    }
    /** Behind the shield: the hands a little out from his sides, palms out, holding the iron where it stands. */
    static Pose shieldHold(Pose base, float t) {
        Pose p = base.copy();
        for (int side = 0; side < 2; side++)
            p.arm(side, ARM_Z, .55f + .02f * Mth.sin(t * .07f + side)).arm(side, ARM_X, -.35f).arm(side, ELBOW, .35f).arm(side, WRIST_X, -.5f).arm(side, CURL, .25f);
        return p;
    }
    /** The burst: both arms flung out wide at once, the chest opening; the columns explode outward. */
    static Track burst(Pose base) {
        Pose gather = base.copy().add(SPINE_PITCH, .12f).add(CHEST_PITCH, .1f);
        for (int side = 0; side < 2; side++) gather.arm(side, SH_FWD, 1.0f).arm(side, ARM_X, -.9f).arm(side, ARM_Z, -.35f).arm(side, ELBOW, 1.2f).arm(side, CURL, .9f);
        Pose out = base.copy().add(CHEST_PITCH, -.18f).add(HEAD_PITCH, -.15f);
        for (int side = 0; side < 2; side++)
            out.arm(side, SH_FWD, -.6f).arm(side, ARM_X, -.5f).arm(side, ARM_Z, 1.45f).arm(side, ARM_Y, .3f).arm(side, ELBOW, .05f).arm(side, WRIST_X, -.4f).arm(side, CURL, 0);
        return new Track().key(0, base).key(2.5f, gather).key(4, out).key(BURST_TICKS - 2, out).key(BURST_TICKS + 4, base);
    }
}
