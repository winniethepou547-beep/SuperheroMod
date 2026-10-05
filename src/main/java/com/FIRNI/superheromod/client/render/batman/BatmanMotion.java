package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import net.minecraft.util.Mth;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Batman's body as numbers, on the same joints and signs as Black Panther's (PantherMotion.Pose; the conventions are at
 * the top of PantherMotion). Heavy and economical, the Arkham way: an upright armoured stance, every blow driven by the
 * hips and the turn of the torso, a step in, a snap and a recoil; throws that whip from across the body; the grapnel
 * arm laid exactly along the line of sight; a tight combat roll; the strike's uppercut, leap, roundhouse and backflip
 * as one flowing sequence; the glide with the arms spread wide and back holding the wings.
 * Hand positions of the key poses were solved against a forward-kinematics copy of BatmanBody (fist at the chin for the
 * guard, at the face for the straights, the right hand at the left shoulder for the Batarang wind-up, both hands together
 * before the chest for the held throw, the hand on the ground for the mine...).
 */
public final class BatmanMotion {
    private BatmanMotion() {}

    private static float noise(float x) { return Mth.sin(x * 1.7f) * .5f + Mth.sin(x * 3.1f + 1.3f) * .3f + Mth.sin(x * 7.3f + 4.1f) * .2f; }
    private static float ease(float x) { return PantherMotion.ease(x); }
    private static float k(float t, float a, float b) { return PantherMotion.k(t, a, b); }

    // ------------------------------------------------------------------ the stance
    /** Standing: upright, a touch forward, shoulders rolled forward, arms held off the body by the lats, fists loosely closed; breathing. */
    public static Pose stance(float time) {
        Pose p = new Pose();
        p.set(PLANT, 1).set(SPINE_PITCH, .04f).set(CHEST_PITCH, -.03f).set(HEAD_PITCH, .03f).set(NECK, .3f);
        for (int side = 0; side < 2; side++) {
            p.leg(side, LEG_Z, .07f).leg(side, LEG_Y, .12f).leg(side, KNEE, .05f);
            p.arm(side, SH_FWD, .3f).arm(side, ARM_X, -.06f).arm(side, ARM_Z, .2f).arm(side, ELBOW, .3f).arm(side, CURL, .72f).arm(side, WRIST_X, -.05f);
        }
        float breath = Mth.sin(time * .075f);
        p.add(CHEST_PITCH, -.02f * breath).add(SPINE_PITCH, -.006f * breath);
        for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, .14f * breath).armAdd(side, ARM_Z, .012f * breath);
        float drift = noise(time * .017f);
        p.add(SHIFT_X, .15f * drift).add(PELVIS_ROLL, -.012f * drift);
        p.add(HEAD_YAW, .035f * noise(time * .021f + 3)).add(HEAD_PITCH, .015f * noise(time * .029f + 6));
        for (int side = 0; side < 2; side++) p.armAdd(side, CURL, .06f * noise(time * .05f + side * 2));
        return p;
    }
    /** Fists up: the left forward at the face, the right at the chin, knees soft, the left foot ahead, the body turned a little. */
    public static Pose guard(Pose base) {
        Pose g = base.copy();
        g.set(CROUCH, 1.3f).set(SPINE_PITCH, .12f).set(PELVIS_YAW, .22f).set(CHEST_YAW, .12f).add(HEAD_YAW, -.3f).set(HEAD_PITCH, -.05f);
        g.leg(1, LEG_X, -.28f).leg(0, LEG_X, .22f).leg(1, KNEE, .15f);
        g.arm(0, SH_FWD, .5f).arm(0, ARM_X, -1.13f).arm(0, ARM_Y, -.81f).arm(0, ARM_Z, .15f).arm(0, ELBOW, 1.87f).arm(0, CURL, 1).arm(0, WRIST_X, .1f);
        g.arm(1, SH_FWD, .9f).arm(1, ARM_X, -1.25f).arm(1, ARM_Y, -.24f).arm(1, ARM_Z, .1f).arm(1, ELBOW, 1.75f).arm(1, CURL, 1).arm(1, WRIST_X, .1f);
        return g;
    }

    // ------------------------------------------------------------------ sampling
    /** What a move needs besides its clock: the free clock, the punch count, Batarangs held, the roll's way. */
    public record Ctx(float time, int combo, int charge, boolean backRoll) {}

    /** A move's pose at its clock t over the base (the stance, glide or air pose under it). */
    public static Pose sample(int action, float t, Pose base, Ctx c) {
        return switch (action) {
            case PUNCH -> punch(base, blow(c.combo()), punchTicks(c.combo())).sample(t);
            case BATARANG -> batarang(base).sample(t);
            case BATARANG_CHARGE -> charge(base, t, c.charge(), c.time());
            case BATARANG_MULTI -> multi(base).sample(t);
            case GADGET_THROW -> gadget(base).sample(t);
            case MINE_PLACE -> mine(base).sample(t);
            case WHEEL -> wheel(base, c.time());
            case GRAPNEL_AIM, GRAPNEL_FIRE -> aimBody(base);
            case GRAPNEL_PULL -> pull(base, c.time());
            case GRAPNEL_YANK -> yank(base).sample(t);
            case GRAPNEL_STRIKE -> strike(base, t);
            case DODGE -> roll(base, t, c.backRoll());
            case CANNON -> BatmanCannonFx.pose(base, t, c.time());
            case SONIC -> BatmanSonicFx.pose(base, t, c.time());
            case SHOCK_EQUIP, SHOCK_UNEQUIP, SHOCK_PUNCH -> BatmanShockFx.pose(action, base, t, c.combo(), c.time());
            case REFLEX -> reflex(base, t, c.time());
            case TD_SIGNAL, TD_DASH, TD_HOLD, TD_MISS -> TakedownMotion.pose(action, base, t, c.time());
            default -> base;
        };
    }

    // ------------------------------------------------------------------ left click: the blows
    /**
     * One blow of the chain over len ticks, landing at PUNCH_HIT of it: a blink of load, the strike with the hips, the
     * torso and a step behind it, a short hold through the target, the recoil back into the guard.
     */
    static Track punch(Pose base, int blow, int len) {
        Pose guard = guard(base);
        Pose load = guard.copy(), hit = guard.copy(), through;
        float h = PUNCH_HIT * len;
        switch (blow) {
            case B_LEFT -> {
                load.add(CHEST_YAW, -.18f).add(CROUCH, .2f).arm(1, ARM_X, -1.0f).arm(1, ELBOW, 2.1f);
                hit.set(CHEST_YAW, .5f).set(SPINE_YAW, .12f).set(PELVIS_YAW, .3f).set(SHIFT_Z, -1.3f).set(SPINE_PITCH, .16f).set(CROUCH, 1.6f)
                        .add(HEAD_YAW, .2f);
                hit.arm(1, SH_FWD, 2.2f).arm(1, ARM_X, -2.3f).arm(1, ARM_Y, .66f).arm(1, ARM_Z, 0).arm(1, ELBOW, .06f).arm(1, WRIST_X, 0);
                hit.arm(0, ARM_X, -1.23f).arm(0, ARM_Y, -1.24f).arm(0, ELBOW, 1.72f);
                hit.leg(1, LEG_X, -.42f).leg(0, LEG_X, .3f).leg(0, ANKLE, .25f);
                through = hit.copy().add(CHEST_YAW, .06f);
                through.arm(1, ARM_X, -2.2f).arm(1, ELBOW, .15f);
            }
            case B_HOOK -> {
                load.set(CHEST_YAW, .45f).set(SPINE_YAW, .15f).set(PELVIS_YAW, .3f).set(CROUCH, 2.2f)
                        .arm(0, SH_FWD, -.4f).arm(0, ARM_X, -.2f).arm(0, ARM_Y, 0).arm(0, ARM_Z, 1.25f).arm(0, ELBOW, 1.6f);
                hit.set(CHEST_YAW, -.3f).set(SPINE_YAW, -.1f).set(PELVIS_YAW, -.15f).set(SHIFT_Z, -.8f).set(CROUCH, 1.8f).set(SPINE_ROLL, .06f)
                        .add(HEAD_YAW, .3f);
                hit.arm(0, SH_FWD, 1.2f).arm(0, SH_UP, .9f).arm(0, ARM_X, -.45f).arm(0, ARM_Y, 0).arm(0, ARM_Z, 1.85f).arm(0, ELBOW, 1.75f).arm(0, WRIST_X, 0);
                hit.leg(1, LEG_X, -.38f).leg(0, LEG_X, .28f).leg(0, ANKLE, .3f).leg(0, LEG_Y, .35f);
                through = hit.copy().set(CHEST_YAW, -.55f).set(SPINE_YAW, -.18f);
                through.arm(0, ARM_X, -.7f).arm(0, ARM_Z, 1.7f).arm(0, ELBOW, 1.9f);
            }
            case B_UPPER -> {
                load.set(CROUCH, 3.5f).set(SPINE_PITCH, .35f).set(CHEST_YAW, -.35f).set(PELVIS_YAW, -.1f).set(SPINE_ROLL, .12f)
                        .arm(1, SH_FWD, -.3f).arm(1, ARM_X, -.25f).arm(1, ARM_Y, 0).arm(1, ARM_Z, .25f).arm(1, ELBOW, 1.5f).arm(1, WRIST_X, -.3f);
                hit.set(CROUCH, .3f).set(LIFT, .6f).set(SPINE_PITCH, -.1f).set(CHEST_PITCH, -.12f).set(CHEST_YAW, .55f).set(PELVIS_YAW, .3f)
                        .set(SHIFT_Z, -1.0f).set(HEAD_PITCH, -.2f).set(PLANT, .85f).set(SPINE_ROLL, -.05f);
                hit.arm(1, SH_FWD, 1.6f).arm(1, SH_UP, 1.0f).arm(1, ARM_X, -1.06f).arm(1, ARM_Y, 0).arm(1, ARM_Z, 0).arm(1, ELBOW, 1.83f).arm(1, WRIST_X, .15f);
                hit.leg(1, LEG_X, -.3f).leg(0, ANKLE, .45f).leg(1, ANKLE, .25f);
                through = hit.copy().add(LIFT, .3f);
                through.arm(1, ARM_X, -1.45f).arm(1, ELBOW, 1.6f).arm(1, SH_UP, 1.3f);
            }
            case B_ELBOW -> {
                load.set(CHEST_YAW, .5f).set(SPINE_YAW, .12f).set(CROUCH, 1.8f)
                        .arm(0, SH_FWD, -.5f).arm(0, ARM_X, -.3f).arm(0, ARM_Y, 0).arm(0, ARM_Z, 1.6f).arm(0, ELBOW, 2.5f);
                hit.set(CHEST_YAW, -.45f).set(SPINE_YAW, -.15f).set(PELVIS_YAW, -.15f).set(SHIFT_Z, -1.4f).set(CROUCH, 1.9f).set(SPINE_PITCH, .18f)
                        .add(HEAD_YAW, .3f);
                hit.arm(0, SH_FWD, 1.6f).arm(0, SH_UP, .8f).arm(0, ARM_X, -1.2f).arm(0, ARM_Y, 0).arm(0, ARM_Z, 1.8f).arm(0, ELBOW, 2.55f).arm(0, WRIST_X, 0);
                hit.leg(1, LEG_X, -.45f).leg(0, LEG_X, .32f).leg(0, ANKLE, .3f);
                through = hit.copy().add(CHEST_YAW, -.12f);
            }
            default -> {
                // The right straight (and every second blow of the flurry).
                load.add(CHEST_YAW, .18f).add(CROUCH, .2f).arm(0, ARM_X, -.95f).arm(0, ELBOW, 2.1f);
                hit.set(CHEST_YAW, -.5f).set(SPINE_YAW, -.12f).set(PELVIS_YAW, -.05f).set(SHIFT_Z, -1.2f).set(SPINE_PITCH, .16f).set(CROUCH, 1.6f)
                        .add(HEAD_YAW, .25f);
                hit.arm(0, SH_FWD, 2.2f).arm(0, ARM_X, -2.24f).arm(0, ARM_Y, .2f).arm(0, ARM_Z, 0).arm(0, ELBOW, .06f).arm(0, WRIST_X, 0);
                hit.arm(1, ARM_X, -1.12f).arm(1, ARM_Y, -1.15f).arm(1, ELBOW, 1.9f);
                hit.leg(1, LEG_X, -.4f).leg(0, LEG_X, .3f).leg(0, ANKLE, .3f);
                through = hit.copy().add(CHEST_YAW, -.06f);
                through.arm(0, ARM_X, -2.15f).arm(0, ELBOW, .14f);
            }
        }
        return new Track().key(0, guard).key(h * .4f, load).key(h, hit).key(h + (len - h) * .3f, through).key(len, guard);
    }

    // ------------------------------------------------------------------ right click: Batarangs
    /** One Batarang: the right hand back at the left shoulder, then one flat whip out to the right-front, the wrist flicking open. */
    static Track batarang(Pose base) {
        Pose wind = base.copy().set(CHEST_YAW, -.45f).set(SPINE_YAW, -.12f).set(CROUCH, 1.2f).set(SPINE_PITCH, .1f).add(HEAD_YAW, .35f);
        wind.arm(0, SH_FWD, 1.0f).arm(0, ARM_X, -.96f).arm(0, ARM_Y, -1.07f).arm(0, ARM_Z, .18f).arm(0, ELBOW, 1.83f).arm(0, WRIST_X, .5f).arm(0, CURL, .85f);
        wind.arm(1, ARM_Z, .45f).arm(1, ARM_X, -.3f).arm(1, ELBOW, .7f);
        wind.leg(1, LEG_X, -.2f).leg(0, LEG_X, .15f);
        Pose out = base.copy().set(CHEST_YAW, .3f).set(SPINE_YAW, .08f).set(CROUCH, 1.0f).set(SHIFT_Z, -.8f).add(HEAD_YAW, -.2f);
        out.arm(0, SH_FWD, 1.4f).arm(0, ARM_X, -1.79f).arm(0, ARM_Y, -.21f).arm(0, ARM_Z, 0).arm(0, ELBOW, 0).arm(0, WRIST_X, -.25f).arm(0, CURL, .25f);
        out.arm(1, ARM_Z, .55f).arm(1, ARM_X, .15f).arm(1, ELBOW, .5f);
        out.leg(1, LEG_X, -.3f).leg(0, LEG_X, .2f);
        Pose follow = out.copy().set(CHEST_YAW, .42f);
        follow.arm(0, ARM_Y, .25f).arm(0, ARM_X, -1.6f).arm(0, WRIST_X, -.4f);
        return new Track().key(0, base).key(1.6f, wind).key(BATARANG_AT, out).key(BATARANG_AT + 1.6f, follow).key(BATARANG_TICKS + 2, base);
    }
    /** Holding the fan: both hands together before the chest, a crouch that tightens the longer he holds, a faint tremor of tension. */
    static Pose charge(Pose base, float t, int count, float time) {
        Pose hold = base.copy().set(CROUCH, 1.4f).set(SPINE_PITCH, .12f).add(HEAD_PITCH, -.08f);
        for (int side = 0; side < 2; side++)
            hold.arm(side, SH_FWD, .8f).arm(side, WRIST_X, .3f).arm(side, ARM_X, -.51f).arm(side, ARM_Y, -.75f).arm(side, ARM_Z, 0).arm(side, ELBOW, 1.98f).arm(side, CURL, .85f);
        hold.leg(1, LEG_X, -.22f).leg(0, LEG_X, .18f);
        float tension = PantherMotion.clamp((t + count * 2) / (BATARANG_STEP * BATARANG_MAX + 4f));
        hold.add(CROUCH, 1.3f * tension).add(CHEST_PITCH, .08f * tension).add(SPINE_PITCH, .05f * tension);
        float tremor = .012f * tension * Mth.sin(time * 2.3f);
        for (int side = 0; side < 2; side++) hold.armAdd(side, ARM_X, tremor).armAdd(side, SH_UP, .3f * tension);
        Pose out = base.copy();
        out.toward(hold, ease(t / 3f));
        return out;
    }
    /** Many at once: the hands crossed before the chest, then both arms flung out wide together, the chest opening. */
    static Track multi(Pose base) {
        Pose cross = base.copy().set(CROUCH, 1.8f).set(SPINE_PITCH, .2f).set(CHEST_PITCH, .1f).add(HEAD_PITCH, -.1f);
        for (int side = 0; side < 2; side++) cross.arm(side, SH_FWD, 1.2f).arm(side, WRIST_X, .4f).arm(side, CURL, .85f);
        cross.arm(0, ARM_X, -.91f).arm(0, ARM_Y, -1.35f).arm(0, ARM_Z, .12f).arm(0, ELBOW, 1.64f);
        cross.arm(1, ARM_X, -.98f).arm(1, ARM_Y, -1.29f).arm(1, ARM_Z, .06f).arm(1, ELBOW, 1.58f);
        Pose open = base.copy().set(CROUCH, 1.0f).set(CHEST_PITCH, -.1f).set(SPINE_PITCH, -.02f).add(HEAD_PITCH, -.05f).set(SHIFT_Z, -.6f);
        for (int side = 0; side < 2; side++)
            open.arm(side, SH_FWD, .6f).arm(side, ARM_X, -1.35f).arm(side, ARM_Y, .95f).arm(side, ARM_Z, 0).arm(side, ELBOW, .35f).arm(side, WRIST_X, -.3f).arm(side, CURL, .25f);
        Pose follow = open.copy();
        for (int side = 0; side < 2; side++) follow.arm(side, ARM_Y, 1.2f).arm(side, ARM_Z, .3f).arm(side, ARM_X, -1.2f);
        return new Track().key(0, base).key(2.2f, cross).key(MULTI_AT, open).key(MULTI_AT + 2, follow).key(MULTI_TICKS + 2, base);
    }

    // ------------------------------------------------------------------ R: gadgets
    /** A gadget: the pellet drawn back by the right ear, the left hand pointing at the spot, then a quick overhand lob. */
    static Track gadget(Pose base) {
        Pose back = base.copy().set(CHEST_YAW, .35f).set(SPINE_PITCH, -.05f).set(CROUCH, .8f).add(HEAD_YAW, -.3f);
        back.arm(0, SH_UP, .6f).arm(0, ARM_X, -1.37f).arm(0, ARM_Y, .3f).arm(0, ARM_Z, -.26f).arm(0, ELBOW, 2.51f).arm(0, CURL, .9f);
        back.arm(1, SH_FWD, .8f).arm(1, ARM_X, -1.25f).arm(1, ARM_Y, .1f).arm(1, ELBOW, .45f).arm(1, CURL, .4f);
        back.leg(1, LEG_X, -.25f).leg(0, LEG_X, .2f);
        Pose lob = base.copy().set(CHEST_YAW, -.3f).set(SPINE_PITCH, .15f).set(SHIFT_Z, -.8f).set(CROUCH, 1.0f).add(HEAD_YAW, .2f);
        lob.arm(0, SH_FWD, 1.2f).arm(0, ARM_X, -2.1f).arm(0, ARM_Y, .05f).arm(0, ARM_Z, 0).arm(0, ELBOW, .35f).arm(0, WRIST_X, .3f).arm(0, CURL, .2f);
        lob.arm(1, ARM_X, -.4f).arm(1, ARM_Z, .4f).arm(1, ELBOW, .8f);
        lob.leg(1, LEG_X, -.3f).leg(0, LEG_X, .25f).leg(0, ANKLE, .25f);
        Pose follow = lob.copy().set(CHEST_YAW, -.4f);
        follow.arm(0, ARM_X, -1.4f).arm(0, ELBOW, .3f);
        return new Track().key(0, base).key(2.2f, back).key(GADGET_AT, lob).key(GADGET_AT + 2, follow).key(GADGET_TICKS + 2, base);
    }
    /** The kneel: down on the right knee, the left foot planted ahead, the body over the left thigh, the right hand on the ground. */
    static Pose kneel(Pose base) {
        Pose k = base.copy().set(PLANT, 0).set(CROUCH, 0).set(LIFT, -5).set(SPINE_PITCH, .65f).set(CHEST_PITCH, .2f).set(HEAD_PITCH, -.45f).set(NECK, .6f);
        k.leg(0, LEG_X, .15f).leg(0, KNEE, 1.65f).leg(0, ANKLE, .6f).leg(0, LEG_Z, .05f);
        k.leg(1, LEG_X, -1.4f).leg(1, KNEE, 1.4f).leg(1, ANKLE, 0).leg(1, LEG_Z, .1f);
        k.arm(0, SH_FWD, 1.6f).arm(0, SH_UP, -.5f).arm(0, ARM_X, -.86f).arm(0, ARM_Y, -.42f).arm(0, ARM_Z, 0).arm(0, ELBOW, .25f).arm(0, CURL, .55f).arm(0, WRIST_X, .5f);
        k.arm(1, ARM_X, .19f).arm(1, ARM_Y, -.48f).arm(1, ELBOW, 1.92f).arm(1, CURL, .7f);
        return k;
    }
    static Track mine(Pose base) {
        Pose down = kneel(base);
        Pose set = down.copy().add(SPINE_PITCH, .05f);
        set.arm(0, ARM_X, -.9f).arm(0, ELBOW, .02f).arm(0, CURL, .3f).arm(0, WRIST_X, .7f);
        Pose up = down.copy();
        up.arm(0, ARM_X, -.6f).arm(0, ELBOW, .9f).arm(0, CURL, .7f);
        Pose half = base.copy();
        half.toward(down, .4f);
        return new Track().key(0, base).key(4, down).key(MINE_AT, set).key(MINE_AT + 2, up).key(MINE_TICKS - 2.5f, half).key(MINE_TICKS + 1, base);
    }
    /** The wheel open: the right hand at the belt, the head dipped toward it. */
    static Pose wheel(Pose base, float time) {
        Pose p = base.copy().add(HEAD_PITCH, .25f).add(CHEST_PITCH, .05f).add(CHEST_YAW, -.05f);
        p.arm(0, ARM_X, .01f).arm(0, ARM_Y, -.93f).arm(0, ARM_Z, .05f).arm(0, ELBOW, .87f).arm(0, CURL, .55f + .08f * Mth.sin(time * .3f)).arm(0, WRIST_X, .2f);
        return p;
    }

    // ------------------------------------------------------------------ E: the grapnel
    /** Aiming: the right shoulder forward, a firm stance; the left hand loosely up under the gun (the layer lays the right arm on the aim). */
    static Pose aimBody(Pose base) {
        Pose p = base.copy().set(CROUCH, .9f).set(CHEST_YAW, -.18f).set(SPINE_PITCH, .06f);
        p.leg(1, LEG_X, -.22f).leg(0, LEG_X, .16f);
        p.arm(1, SH_FWD, .9f).arm(1, ARM_X, -1.0f).arm(1, ARM_Y, -.5f).arm(1, ARM_Z, 0).arm(1, ELBOW, 1.3f).arm(1, CURL, .6f);
        p.arm(0, CURL, 1);
        return p;
    }
    /** The kick of the shot (0..1 over the first ticks after a shot, decaying). */
    public static float recoil(float t) { return t < 0 ? 0 : t < .8f ? t / .8f : (float) Math.exp(-(t - .8f) / 1.6f); }
    /** Pulled along the line: stretched out, the legs trailing together, the free arm back; the gun arm is laid on the line by the layer. */
    static Pose pull(Pose base, float time) {
        Pose p = base.copy().set(PLANT, 0).set(CROUCH, 0).set(SPINE_PITCH, -.12f).set(CHEST_PITCH, -.08f).set(HEAD_PITCH, 0).set(SHIFT_Z, 0);
        p.leg(0, LEG_X, .22f).leg(0, KNEE, .45f).leg(0, ANKLE, .65f).leg(1, LEG_X, .32f).leg(1, KNEE, .75f).leg(1, ANKLE, .7f);
        p.arm(1, SH_FWD, -.4f).arm(1, ARM_X, .45f).arm(1, ARM_Y, 0).arm(1, ARM_Z, .35f).arm(1, ELBOW, .5f).arm(1, CURL, .7f);
        p.arm(0, CURL, 1);
        // The wind tugging at the legs.
        for (int side = 0; side < 2; side++) p.legAdd(side, LEG_X, .06f * Mth.sin(time * .8f + side * 1.7f));
        return p;
    }
    /**
     * The yank: the right hand has fired (the layer keeps the gun on the aim), the left reaches out and grabs the line in
     * front, then hauls: the waist twisting into it (left shoulder back, right forward), knees bent, the weight thrown
     * back; strongest at YANK_DOWN; then back up into the stance.
     */
    static Track yank(Pose base) {
        Pose shot = aimBody(base);
        Pose grab = base.copy().set(CROUCH, 1.6f).set(SPINE_PITCH, .18f).set(CHEST_YAW, .1f);
        grab.arm(1, SH_FWD, 1.4f).arm(1, ARM_X, -1.14f).arm(1, ARM_Y, -.44f).arm(1, ARM_Z, 0).arm(1, ELBOW, 1.24f).arm(1, CURL, .3f);
        grab.leg(1, LEG_X, -.25f).leg(0, LEG_X, .18f);
        Pose take = grab.copy();
        take.arm(1, CURL, 1);
        Pose haul = base.copy().set(CROUCH, 3.2f).set(SPINE_PITCH, -.05f).set(CHEST_YAW, -.6f).set(SPINE_YAW, -.2f).set(PELVIS_YAW, -.15f).set(SHIFT_Z, 1.6f)
                .set(SPINE_ROLL, .06f).add(HEAD_YAW, .45f);
        haul.leg(1, LEG_X, -.38f).leg(1, KNEE, .2f).leg(0, LEG_X, .3f).leg(0, KNEE, .15f);
        haul.arm(1, SH_FWD, -1.0f).arm(1, ARM_X, .1f).arm(1, ARM_Y, -.3f).arm(1, ARM_Z, 0).arm(1, ELBOW, 1.86f).arm(1, CURL, 1);
        haul.arm(0, SH_FWD, 1.0f).arm(0, ARM_X, -1.2f).arm(0, ELBOW, .6f).arm(0, CURL, 1);
        Pose hold = haul.copy().set(CROUCH, 2.6f).set(SHIFT_Z, 1.1f).set(CHEST_YAW, -.45f);
        return new Track().key(0, shot).key(2.2f, grab).key(YANK_PULL, take).key(YANK_DOWN, haul).key(YANK_DOWN + 4, hold).key(YANK_TICKS, base);
    }

    /**
     * The grapnel strike: flying in with the right fist cocked, the landing load, the rising uppercut with the whole body
     * at STRIKE_UPPER, the spring after them at STRIKE_JUMP, the roundhouse at STRIKE_KICK, the backflip from STRIKE_FLIP
     * (the whole body turned backward about its middle, tucked), the landing crouch, the stance by STRIKE_TICKS.
     */
    static Pose strike(Pose base, float t) {
        Pose p = strikeTrack(base).sample(t);
        float flip = k(t, STRIKE_FLIP, STRIKE_FLIP + 9.5f);
        if (flip > 0 && flip < 1) {
            float tuck = Mth.sin(Mth.PI * flip);
            p.add(ROOT_PITCH, -Mth.TWO_PI * flip).set(PLANT, Mth.lerp(tuck, p.get(PLANT), 0)).add(SPINE_PITCH, .45f * tuck).add(HEAD_PITCH, .35f * tuck).add(LIFT, 2.5f * tuck);
            for (int side = 0; side < 2; side++) {
                p.leg(side, LEG_X, Mth.lerp(tuck, p.leg(side, LEG_X), -1.4f)).leg(side, KNEE, Mth.lerp(tuck, p.leg(side, KNEE), 2.05f))
                        .arm(side, ARM_X, Mth.lerp(tuck, p.arm(side, ARM_X), -1.15f)).arm(side, ELBOW, Mth.lerp(tuck, p.arm(side, ELBOW), 1.5f))
                        .arm(side, ARM_Z, Mth.lerp(tuck, p.arm(side, ARM_Z), .3f)).arm(side, ARM_Y, Mth.lerp(tuck, p.arm(side, ARM_Y), -.3f));
            }
        }
        return p;
    }
    private static Track strikeTrack(Pose base) {
        Pose fly = base.copy().set(PLANT, 0).set(ROOT_PITCH, .35f).set(SPINE_PITCH, .1f).set(HEAD_PITCH, -.35f);
        fly.leg(0, LEG_X, -.5f).leg(0, KNEE, 1.2f).leg(1, LEG_X, .2f).leg(1, KNEE, .8f).leg(0, ANKLE, .4f).leg(1, ANKLE, .5f);
        fly.arm(0, SH_FWD, -.6f).arm(0, ARM_X, .35f).arm(0, ARM_Z, .3f).arm(0, ELBOW, 1.8f).arm(0, CURL, 1);
        fly.arm(1, SH_FWD, .8f).arm(1, ARM_X, -1.2f).arm(1, ARM_Y, -.3f).arm(1, ELBOW, 1.6f).arm(1, CURL, 1);
        Pose load = base.copy().set(CROUCH, 3.6f).set(SPINE_PITCH, .4f).set(CHEST_YAW, .35f).set(HEAD_PITCH, -.4f).set(SHIFT_Z, -.6f);
        load.leg(1, LEG_X, -.35f).leg(0, LEG_X, .3f);
        load.arm(0, SH_FWD, -.6f).arm(0, ARM_X, .45f).arm(0, ARM_Z, .3f).arm(0, ELBOW, .6f).arm(0, CURL, 1).arm(0, WRIST_X, -.4f);
        load.arm(1, ARM_X, -1.1f).arm(1, ELBOW, 1.6f).arm(1, CURL, 1);
        Pose upper = base.copy().set(LIFT, 2.2f).set(PLANT, .3f).set(SPINE_PITCH, -.15f).set(CHEST_PITCH, -.15f).set(CHEST_YAW, -.5f).set(PELVIS_YAW, -.15f)
                .set(HEAD_PITCH, -.45f).set(SHIFT_Z, -1.2f);
        upper.arm(0, SH_FWD, 1.6f).arm(0, SH_UP, 1.5f).arm(0, ARM_X, -2.6f).arm(0, ARM_Y, -.2f).arm(0, ARM_Z, 0).arm(0, ELBOW, .9f).arm(0, WRIST_X, .2f).arm(0, CURL, 1);
        upper.arm(1, ARM_X, .3f).arm(1, ARM_Z, .6f).arm(1, ELBOW, .9f).arm(1, CURL, 1);
        upper.leg(0, ANKLE, .9f).leg(1, ANKLE, .6f).leg(0, LEG_X, .35f).leg(1, LEG_X, -.15f);
        Pose reach = upper.copy().add(LIFT, .4f);
        reach.arm(0, ARM_X, -2.9f).arm(0, ELBOW, .5f);
        Pose spring = base.copy().set(CROUCH, 3.0f).set(SPINE_PITCH, .3f).set(HEAD_PITCH, -.6f);
        for (int side = 0; side < 2; side++) spring.arm(side, ARM_X, .5f).arm(side, ARM_Z, .3f).arm(side, ELBOW, .4f).arm(side, CURL, .9f);
        Pose rise = base.copy().set(PLANT, 0).set(SPINE_PITCH, -.05f).set(HEAD_PITCH, -.5f);
        rise.leg(0, LEG_X, -.9f).leg(0, KNEE, 1.6f).leg(1, LEG_X, -.5f).leg(1, KNEE, 1.3f).leg(0, ANKLE, .5f).leg(1, ANKLE, .5f);
        for (int side = 0; side < 2; side++) rise.arm(side, ARM_X, -2.2f).arm(side, ARM_Z, .5f).arm(side, ELBOW, .6f).arm(side, CURL, 1);
        Pose chamber = base.copy().set(PLANT, 0).set(PELVIS_YAW, -1.2f).set(CHEST_YAW, .7f).set(SPINE_ROLL, .25f).set(SPINE_PITCH, .1f)
                .set(HEAD_YAW, .5f).set(HEAD_PITCH, -.25f);
        chamber.leg(0, LEG_X, -.35f).leg(0, LEG_Z, 1.05f).leg(0, KNEE, 2.2f).leg(0, LEG_Y, .3f).leg(0, ANKLE, -.2f).leg(1, LEG_X, .2f).leg(1, KNEE, 1.1f).leg(1, ANKLE, .5f);
        chamber.arm(0, ARM_X, -1.0f).arm(0, ARM_Z, .3f).arm(0, ELBOW, 1.6f).arm(0, CURL, 1).arm(1, ARM_X, .2f).arm(1, ARM_Z, 1.1f).arm(1, ELBOW, .5f).arm(1, CURL, .6f);
        Pose kick = base.copy().set(PLANT, 0).set(PELVIS_YAW, -1.4f).set(CHEST_YAW, .85f).set(SPINE_ROLL, .45f).set(SPINE_PITCH, -.05f)
                .set(HEAD_YAW, .6f).set(HEAD_PITCH, -.1f);
        kick.leg(0, LEG_X, -.2f).leg(0, LEG_Z, 1.45f).leg(0, KNEE, .05f).leg(0, LEG_Y, .25f).leg(0, ANKLE, -.3f).leg(1, LEG_X, .15f).leg(1, KNEE, .6f).leg(1, ANKLE, .6f);
        kick.arm(0, ARM_X, -.6f).arm(0, ARM_Z, -.1f).arm(0, ELBOW, 1.8f).arm(0, CURL, 1).arm(1, ARM_X, .5f).arm(1, ARM_Z, 1.35f).arm(1, ELBOW, .25f).arm(1, CURL, .5f);
        Pose after = kick.copy().set(PELVIS_YAW, -.6f).set(CHEST_YAW, .3f).set(SPINE_ROLL, .1f);
        after.leg(0, LEG_Z, .5f).leg(0, KNEE, 1.2f).leg(0, LEG_X, -.6f);
        Pose reachDown = base.copy().set(PLANT, .3f).set(SPINE_PITCH, .15f).set(HEAD_PITCH, -.2f);
        reachDown.leg(0, LEG_X, -.35f).leg(0, KNEE, .5f).leg(1, LEG_X, -.1f).leg(1, KNEE, .4f);
        for (int side = 0; side < 2; side++) reachDown.arm(side, ARM_Z, .9f).arm(side, ARM_X, -.4f).arm(side, ELBOW, .5f).arm(side, CURL, .8f);
        Pose absorb = base.copy().set(CROUCH, 4.8f).set(SPINE_PITCH, .4f).set(HEAD_PITCH, -.35f);
        absorb.leg(1, LEG_X, -.3f).leg(0, LEG_X, .25f);
        absorb.arm(0, ARM_Z, .9f).arm(0, ARM_X, -.2f).arm(0, ELBOW, .6f).arm(1, ARM_X, -.9f).arm(1, ELBOW, .3f).arm(1, ARM_Z, .3f).arm(1, CURL, .4f);
        Pose rising = guard(base).copy().set(CROUCH, 2.0f);
        return new Track().key(0, fly).key(STRIKE_UPPER - 1.2f, load).key(STRIKE_UPPER, upper).key(STRIKE_UPPER + 1.6f, reach)
                .key(STRIKE_JUMP, spring).key(STRIKE_JUMP + 2.5f, rise).key(STRIKE_KICK - 2, chamber).key(STRIKE_KICK, kick).key(STRIKE_FLIP, after)
                .key(STRIKE_FLIP + 10.5f, reachDown).key(STRIKE_FLIP + 13, absorb).key(STRIKE_TICKS - 4, rising).key(STRIKE_TICKS, base);
    }

    // ------------------------------------------------------------------ CTRL: the roll
    /**
     * The combat roll, Elden Ring style (the layer turns the body to face along it): launched off the front foot into a
     * dive with the body stretched out forward and the arms reaching ahead (forward rolls), then one whole turn about the
     * roll axis over the shoulder close to the ground (backward for a roll straight back), up into the stance.
     */
    static Pose roll(Pose base, float t, boolean back) {
        float start = back ? .7f : DODGE_DIVE * .7f;
        float k = PantherMotion.clamp((t - start) / (DODGE_TICKS - start - 2.6f));
        float turn = .45f * k + .55f * ease(k);
        float tuck = Mth.sin(Mth.PI * PantherMotion.clamp((t - start + .5f) / (DODGE_TICKS - start - 1.1f)));
        float dive = k(t, 0, .9f) * (1 - k(t, DODGE_TICKS - 2.2f, DODGE_TICKS));
        Pose p = base.copy();
        p.add(CROUCH, 3.2f * dive * (1 - tuck)).add(SPINE_PITCH, .45f * dive);
        float a = k > 0 && k < 1 ? (back ? -1 : 1) * Mth.TWO_PI * turn : 0;
        p.add(ROOT_PITCH, a);
        // The body turns about the tucked ball's middle (ahead of the hips), held about 12 px off the ground, so it rolls
        // over the ground instead of swinging the head through it (checked against the box body).
        float dy0 = .3f, dz0 = -7.3f, ca = Mth.cos(a), sa = Mth.sin(a);
        float ry = dy0 * ca - dz0 * sa, rz = dy0 * sa + dz0 * ca;
        p.add(LIFT, tuck * (-.7f - (dy0 - ry))).add(SHIFT_Z, tuck * (dz0 - rz));
        p.set(PLANT, Mth.lerp(tuck, p.get(PLANT), 0))
                .add(SPINE_PITCH, .5f * tuck).add(HEAD_PITCH, .55f * tuck).set(NECK, Mth.lerp(tuck, p.get(NECK), .9f));
        for (int side = 0; side < 2; side++) {
            p.leg(side, LEG_X, Mth.lerp(tuck, p.leg(side, LEG_X), -1.45f + .15f * side)).leg(side, KNEE, Mth.lerp(tuck, p.leg(side, KNEE), 2.15f))
                    .leg(side, ANKLE, Mth.lerp(tuck, p.leg(side, ANKLE), .4f))
                    .arm(side, ARM_X, Mth.lerp(tuck, p.arm(side, ARM_X), -1.25f)).arm(side, ELBOW, Mth.lerp(tuck, p.arm(side, ELBOW), 1.7f))
                    .arm(side, ARM_Y, Mth.lerp(tuck, p.arm(side, ARM_Y), -.4f)).arm(side, ARM_Z, Mth.lerp(tuck, p.arm(side, ARM_Z), .15f))
                    .arm(side, CURL, Mth.lerp(tuck, p.arm(side, CURL), 1));
        }
        // The dive before the turn: stretched out forward off the front foot, the arms reaching ahead to take the ground.
        float reach = back ? 0 : k(t, 0, 1.4f) * (1 - k(t, start - .3f, start + 1.6f));
        if (reach > 0) {
            p.add(ROOT_PITCH, .8f * reach).add(SPINE_PITCH, -.15f * reach).add(HEAD_PITCH, -.45f * reach).add(LIFT, .5f * reach)
                    .set(PLANT, Mth.lerp(reach, p.get(PLANT), .2f));
            for (int side = 0; side < 2; side++) {
                p.arm(side, ARM_X, Mth.lerp(reach, p.arm(side, ARM_X), -2.55f)).arm(side, ELBOW, Mth.lerp(reach, p.arm(side, ELBOW), .35f))
                        .arm(side, ARM_Z, Mth.lerp(reach, p.arm(side, ARM_Z), .25f)).arm(side, CURL, Mth.lerp(reach, p.arm(side, CURL), .4f));
            }
            p.leg(1, LEG_X, Mth.lerp(reach, p.leg(1, LEG_X), -.35f)).leg(1, KNEE, Mth.lerp(reach, p.leg(1, KNEE), .5f))
                    .leg(0, LEG_X, Mth.lerp(reach, p.leg(0, LEG_X), .7f)).leg(0, KNEE, Mth.lerp(reach, p.leg(0, KNEE), .35f))
                    .leg(0, ANKLE, Mth.lerp(reach, p.leg(0, ANKLE), .6f));
        }
        // Coming up: a low crouch that settles into the stance.
        float up = k(t, DODGE_TICKS - 2.6f, DODGE_TICKS - 1.2f) * (1 - k(t, DODGE_TICKS - 1, DODGE_TICKS + 3));
        p.add(CROUCH, 2.6f * up).add(SPINE_PITCH, .2f * up);
        return p;
    }

    // ------------------------------------------------------------------ Q: the reflex block
    /** The window's stance: alert, a little lower, both forearms up before the chest with the gauntlets' spikes out. */
    static Pose reflex(Pose base, float t, float time) {
        Pose g = guard(base);
        g.add(CROUCH, .6f).add(SPINE_PITCH, .05f).add(HEAD_PITCH, -.04f);
        g.arm(0, ARM_X, -1.32f).arm(0, ARM_Z, .3f).arm(0, WRIST_X, .25f).arm(1, ARM_X, -1.42f).arm(1, ARM_Z, .25f).arm(1, WRIST_X, .25f);
        // Reading the room: the head and shoulders shift a little, never still.
        g.add(HEAD_YAW, .08f * Mth.sin(time * .35f)).add(CHEST_YAW, .04f * Mth.sin(time * .35f + 1));
        return g;
    }
    /**
     * The cape block's beats: the right hand reaches back to the cape's edge behind his right side and closes on it
     * (CAPE_GRIP), draws it round his right side and across his front to his left (by CAPE_ACROSS); it is held there
     * (at least CAPE_HOLD_MIN, and CAPE_KEEP after the last blow on it); then the hand draws it back from his left front
     * to his right and flings it back over his right side (let go at CAPE_LET_GO of that), back in the stance by CAPE_BACK.
     */
    public static final float CAPE_GRIP = 2.5f, CAPE_ACROSS = 6.5f, CAPE_HOLD_MIN = 6, CAPE_KEEP = 10, CAPE_LET_GO = 6.5f, CAPE_BACK = 11;
    /** How long a deflect's own move lasts (the cape: drawn across, held as long as given, thrown back). */
    public static float deflectLength(int kind, float hold) { return kind == BLOCK_CAPE ? CAPE_ACROSS + Math.max(CAPE_HOLD_MIN, hold) + CAPE_BACK : DEFLECT_TICKS; }
    public static float deflectLength(int kind) { return deflectLength(kind, CAPE_HOLD_MIN); }
    /** How firmly the right hand holds the cape's edge t ticks into a cape block held for hold ticks (0..1). */
    public static float capeGrab(float t, float hold) {
        float back = CAPE_ACROSS + Math.max(CAPE_HOLD_MIN, hold);
        return k(t, CAPE_GRIP - .5f, CAPE_GRIP + 1) * (1 - k(t, back + CAPE_LET_GO - .5f, back + CAPE_LET_GO + .8f));
    }
    /**
     * A deflect, over whatever the body is doing, t ticks after the block (fast in, a controlled contact, fast away):
     * the right gauntlet (the right shoulder draws back, the forearm crosses into the attack, the wrist turns the spikes
     * into it, then the arm throws it out to the side); the left (starting closer to the body with the elbow bent more,
     * coming in, then out); both crossed for an instant before the face, then the right arm shoves it aside; or the cape
     * (the right hand reaches back for its edge and sweeps it round in front, holds, lets it go).
     */
    static void deflect(Pose p, int kind, float t) { deflect(p, kind, t, CAPE_HOLD_MIN); }
    static void deflect(Pose p, int kind, float t, float hold) {
        if (kind == BLOCK_CAPE) { cape(p, t, Math.max(CAPE_HOLD_MIN, hold)); return; }
        float len = deflectLength(kind);
        if (t < 0 || t > len) return;
        float w = k(t, 0, 1.5f) * (1 - k(t, len - 3.5f, len));
        float sweep = k(t, 1.2f, 4f);
        Pose g = p.copy();
        switch (kind) {
            case BLOCK_RIGHT -> {
                g.add(CHEST_YAW, .22f - .45f * sweep).add(CROUCH, 1.2f).add(SPINE_PITCH, .08f).add(HEAD_YAW, -.25f);
                g.arm(0, SH_FWD, .6f + .4f * sweep).arm(0, ARM_X, Mth.lerp(sweep, -1.45f, -1.15f)).arm(0, ARM_Y, Mth.lerp(sweep, -.55f, .25f))
                        .arm(0, ARM_Z, Mth.lerp(sweep, .35f, 1.35f)).arm(0, ELBOW, Mth.lerp(sweep, 1.75f, 1.05f)).arm(0, WRIST_X, Mth.lerp(sweep, .35f, -.2f)).arm(0, CURL, 1);
                g.leg(0, LEG_X, .2f).leg(1, LEG_X, -.2f);
                // The whole body turned to his right into it, leaning that way, the right foot stepped out.
                g.add(PELVIS_YAW, .38f).add(SPINE_YAW, .12f).add(SPINE_ROLL, -.12f).add(HEAD_ROLL, .06f);
                g.leg(0, LEG_Z, .28f).leg(0, KNEE, .45f);
            }
            case BLOCK_LEFT -> {
                g.add(CHEST_YAW, -.18f + .4f * sweep).add(CROUCH, 1.4f).add(SPINE_PITCH, .1f).add(HEAD_YAW, .25f);
                g.arm(1, SH_FWD, .5f + .3f * sweep).arm(1, ARM_X, Mth.lerp(sweep, -1.3f, -1.05f)).arm(1, ARM_Y, Mth.lerp(sweep, -.95f, .1f))
                        .arm(1, ARM_Z, Mth.lerp(sweep, .15f, 1.25f)).arm(1, ELBOW, Mth.lerp(sweep, 2.15f, 1.2f)).arm(1, WRIST_X, Mth.lerp(sweep, .4f, -.15f)).arm(1, CURL, 1);
                g.leg(1, LEG_X, -.3f).leg(0, LEG_X, .15f);
                // The whole body turned to his left into it, leaning that way, the left foot stepped out.
                g.add(PELVIS_YAW, -.38f).add(SPINE_YAW, -.12f).add(SPINE_ROLL, .12f).add(HEAD_ROLL, -.06f);
                g.leg(1, LEG_Z, .28f).leg(1, KNEE, .45f);
            }
            case BLOCK_FRONT -> {
                float lock = 1 - sweep * .6f;
                g.add(CROUCH, 1.8f).add(SPINE_PITCH, .14f).add(HEAD_PITCH, .12f).add(CHEST_YAW, -.35f * sweep);
                for (int side = 0; side < 2; side++)
                    g.arm(side, SH_FWD, .9f).arm(side, ARM_X, -1.5f).arm(side, ARM_Y, -1.05f * lock).arm(side, ARM_Z, .1f).arm(side, ELBOW, 1.95f).arm(side, CURL, 1).arm(side, WRIST_X, .2f);
                g.arm(0, ARM_Z, Mth.lerp(sweep, .1f, 1.2f)).arm(0, ARM_Y, Mth.lerp(sweep, -1.05f, .2f)).arm(0, ELBOW, Mth.lerp(sweep, 1.95f, 1.15f));
            }
            case BLOCK_EVADE_R, BLOCK_EVADE_L -> {
                // Slipping the blow like a panther: the weight dropped onto one leg, the whole body swaying out and down
                // to that side, the head pulled away, the hands staying up in the guard; then back.
                float s = kind == BLOCK_EVADE_R ? 1 : -1;
                float sway = Mth.sin(Mth.PI * PantherMotion.clamp(t / (len - 1)));
                Pose gd = guard(p);
                g = gd;
                g.add(SHIFT_X, -s * 5.5f * sway).add(ROOT_ROLL, -s * .3f * sway).add(SPINE_ROLL, -s * .18f * sway).add(CHEST_ROLL, -s * .1f * sway)
                        .add(CROUCH, 2.6f * sway).add(SPINE_PITCH, .2f * sway).add(HEAD_ROLL, -s * .2f * sway).add(HEAD_YAW, s * .15f * sway)
                        .add(CHEST_YAW, s * .25f * sway);
                g.leg(kind == BLOCK_EVADE_R ? 0 : 1, LEG_Z, .3f * sway).leg(kind == BLOCK_EVADE_R ? 1 : 0, LEG_Z, -.05f);
            }
            default -> {}
        }
        p.toward(g, w);
    }

    /**
     * The cape block, over whatever the body is doing (the cloth itself follows his right hand: BatmanBody.capeGrab):
     * the right hand goes back to the edge of the cape behind his right hip and closes on it, then pulls it hard round
     * his right side and across his front to his left, the body turning left behind it, the head down behind the cloth,
     * the left forearm braced low across him; held; then the hand draws it back from his left front to his right and
     * flings it back over his right side, the body turning back; into the stance.
     */
    static void cape(Pose p, float t, float hold) {
        float back = CAPE_ACROSS + hold, len = back + CAPE_BACK;
        if (t < 0 || t > len) return;
        Pose start = p.copy();
        Pose reach = p.copy().add(CHEST_YAW, .35f).add(PELVIS_YAW, .12f).add(HEAD_YAW, .3f).add(CROUCH, .8f);
        reach.arm(0, SH_FWD, -.9f).arm(0, SH_UP, 0).arm(0, ARM_X, .65f).arm(0, ARM_Y, .2f).arm(0, ARM_Z, .45f).arm(0, ELBOW, .45f).arm(0, WRIST_X, .3f).arm(0, CURL, .15f);
        Pose grip = reach.copy();
        grip.arm(0, CURL, 1).arm(0, WRIST_X, .15f);
        Pose across = p.copy().add(CHEST_YAW, -.5f).add(PELVIS_YAW, -.22f).add(SPINE_PITCH, .12f).add(HEAD_PITCH, .14f).add(HEAD_YAW, -.1f).add(CROUCH, 1.6f);
        across.arm(0, SH_FWD, 1.4f).arm(0, SH_UP, .4f).arm(0, ARM_X, -1.55f).arm(0, ARM_Y, -1.25f).arm(0, ARM_Z, .1f).arm(0, ELBOW, .75f).arm(0, WRIST_X, -.1f).arm(0, CURL, 1);
        across.arm(1, SH_FWD, .6f).arm(1, ARM_X, -.75f).arm(1, ARM_Y, -.5f).arm(1, ARM_Z, .25f).arm(1, ELBOW, 1.45f).arm(1, CURL, 1);
        Pose held = across.copy().add(CROUCH, .3f);
        Pose drawBack = p.copy().add(CHEST_YAW, .1f).add(CROUCH, 1.0f);
        drawBack.arm(0, SH_FWD, .6f).arm(0, ARM_X, -1.2f).arm(0, ARM_Y, .2f).arm(0, ARM_Z, .75f).arm(0, ELBOW, .5f).arm(0, CURL, 1);
        Pose fling = p.copy().add(CHEST_YAW, .4f).add(PELVIS_YAW, .12f).add(HEAD_YAW, .15f);
        fling.arm(0, SH_FWD, -.8f).arm(0, ARM_X, .75f).arm(0, ARM_Y, .3f).arm(0, ARM_Z, .6f).arm(0, ELBOW, .3f).arm(0, WRIST_X, .4f).arm(0, CURL, .3f);
        Pose sample = new Track().key(0, start).key(CAPE_GRIP, reach).key(CAPE_GRIP + .8f, grip).key(CAPE_ACROSS, across).key(back, held)
                .key(back + 4.5f, drawBack).key(back + CAPE_LET_GO + .5f, fling).key(len, start).sample(t);
        p.toward(sample, 1);
    }

    // ------------------------------------------------------------------ the air, the glide
    /**
     * Gliding: the body tipped forward to nearly flat with the head up looking ahead, the arms spread wide and a little
     * back holding the wings, the legs together trailing, toes pointed. dive (0..1) tips him head-down with the arms
     * swept back along the body.
     */
    public static Pose glide(float time, float dive) {
        Pose p = new Pose();
        float d = PantherMotion.clamp(dive);
        p.set(PLANT, 0).set(ROOT_PITCH, 1.2f + .5f * d).set(SPINE_PITCH, -.08f).set(CHEST_PITCH, -.1f).set(HEAD_PITCH, -1.05f + .45f * d).set(NECK, .5f);
        for (int side = 0; side < 2; side++) {
            p.arm(side, SH_FWD, Mth.lerp(d, -.6f, -.2f)).arm(side, SH_UP, Mth.lerp(d, .6f, .2f)).arm(side, ARM_X, Mth.lerp(d, .3f, .55f))
                    .arm(side, ARM_Y, 0).arm(side, ARM_Z, Mth.lerp(d, 1.45f, .38f)).arm(side, ELBOW, Mth.lerp(d, .15f, .1f))
                    .arm(side, WRIST_X, -.1f).arm(side, CURL, .75f);
            p.leg(side, LEG_X, .08f).leg(side, LEG_Z, -.02f).leg(side, KNEE, .12f + .05f * side).leg(side, ANKLE, .7f);
        }
        // Alive in the air: the arms riding the gusts, the legs swaying a little.
        for (int side = 0; side < 2; side++) {
            p.armAdd(side, ARM_Z, .04f * Mth.sin(time * .21f + side * 1.9f) * (1 - d)).armAdd(side, ARM_X, .03f * Mth.sin(time * .17f + side));
            p.legAdd(side, LEG_X, .04f * Mth.sin(time * .13f + side * 2.2f));
        }
        p.add(ROOT_ROLL, .03f * Mth.sin(time * .07f));
        return p;
    }
    /** Off the ground (not gliding): the knees drawn up a little, the arms out for balance. weight 0..1. */
    public static void air(Pose p, float weight) {
        if (weight <= .001f) return;
        p.add(PLANT, -weight).legAdd(0, LEG_X, -.45f * weight).legAdd(0, KNEE, .95f * weight).legAdd(1, LEG_X, -.15f * weight).legAdd(1, KNEE, .6f * weight)
                .legAdd(0, ANKLE, .35f * weight).legAdd(1, ANKLE, .45f * weight).add(SPINE_PITCH, .1f * weight);
        for (int side = 0; side < 2; side++) p.armAdd(side, ARM_Z, .3f * weight).armAdd(side, ARM_X, -.15f * weight);
    }
    /** A landing taken on bent knees (since = ticks since touching down, power 0..1). */
    public static void land(Pose p, float since, float power) {
        if (since < 0 || since > 16) return;
        float w = (float) Math.exp(-since / 3.4f) * PantherMotion.snap(since, 0, .8f) * power;
        p.add(CROUCH, 5.0f * w).add(SPINE_PITCH, .28f * w).add(HEAD_PITCH, -.2f * w);
        for (int side = 0; side < 2; side++) p.armAdd(side, ARM_Z, .35f * w).armAdd(side, ELBOW, .3f * w);
    }

    /**
     * Walking and running under a move: heavy, measured strides with the arms swinging a little; running leans him in,
     * the knees drive, the arms pump with bent elbows, a bounce in every stride. legs / arms: how much of each to add.
     */
    public static void locomotion(Pose p, float walk, float amount, float run, float legs, float arms) {
        if (amount < .01f || legs + arms < .01f) return;
        float phase = walk * .6662f, sin = Mth.sin(phase), cos = Mth.cos(phase);
        float stroll = amount * (1 - run), sprint = amount * run;
        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? 1 : -1;
            float swing = sg * cos, lift = Math.max(0, sg * sin);
            p.legAdd(side, LEG_X, swing * (.5f * stroll + .95f * sprint) * legs)
                    .legAdd(side, KNEE, lift * (.5f * stroll + 1.35f * sprint) * legs)
                    .legAdd(side, ANKLE, Math.max(0, sg * cos) * .3f * sprint * legs);
            // The run (Arkham): fists closed, elbows bent hard, the arms driving forward and back close to the body.
            p.armAdd(side, ARM_X, (-swing * (.28f * stroll + 1.05f * sprint) - .25f * sprint) * arms)
                    .armAdd(side, ELBOW, (.12f * stroll + 1.35f * sprint + .25f * sprint * Math.max(0, -swing)) * arms)
                    .armAdd(side, ARM_Z, -.12f * sprint * arms).armAdd(side, CURL, .3f * sprint * arms);
        }
        p.add(PLANT, -.5f * sprint * legs).add(SPINE_PITCH, (.05f * stroll + .2f * sprint) * legs).add(ROOT_PITCH, .24f * sprint * legs)
                .add(HEAD_PITCH, -.2f * sprint * legs)
                .add(LIFT, (.25f * stroll + .8f * sprint) * Math.abs(sin) * legs)
                .add(CHEST_YAW, -(.05f * stroll + .12f * sprint) * cos * arms).add(PELVIS_YAW, (.04f * stroll + .09f * sprint) * cos * legs)
                .add(CHEST_ROLL, .02f * cos * stroll * arms).add(CROUCH, (.3f * stroll + .5f * sprint) * legs);
    }

    // ------------------------------------------------------------------ laying an arm on a direction
    private static final float[] M = new float[9], T = new float[9], MT = new float[9];
    /** Multiplies M by a rotation about x (0), y (1) or z (2) (M = M * R). */
    private static void mul(int axis, float a) {
        if (a == 0) return;
        float c = Mth.cos(a), s = Mth.sin(a);
        for (int i = 0; i < 9; i++) T[i] = 0;
        if (axis == 0) { T[0] = 1; T[4] = c; T[5] = -s; T[7] = s; T[8] = c; }
        else if (axis == 1) { T[0] = c; T[2] = s; T[4] = 1; T[6] = -s; T[8] = c; }
        else { T[0] = c; T[1] = -s; T[3] = s; T[4] = c; T[8] = 1; }
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) MT[i * 3 + j] = M[i * 3] * T[j] + M[i * 3 + 1] * T[3 + j] + M[i * 3 + 2] * T[6 + j];
        System.arraycopy(MT, 0, M, 0, 9);
    }
    /** The direction the look points in model space (+y down, -z front, -x his right), from the head's yaw and pitch. */
    public static float[] look(float yaw, float pitch) {
        float cp = Mth.cos(pitch);
        return new float[]{-cp * Mth.sin(yaw), Mth.sin(pitch), -cp * Mth.cos(yaw)};
    }
    /**
     * Lays one arm (side 0 right, 1 left) straight along a direction given in the model's root space, whatever the
     * root, pelvis, spine and chest are doing (their turns are taken out), blended in by weight; the elbow straight, the
     * fist closed on the grip.
     */
    public static void aim(Pose p, int side, float[] dir, float weight) {
        if (weight <= .001f) return;
        float[] v = p.v;
        M[0] = 1; M[1] = 0; M[2] = 0; M[3] = 0; M[4] = 1; M[5] = 0; M[6] = 0; M[7] = 0; M[8] = 1;
        mul(0, v[ROOT_PITCH]); mul(1, v[ROOT_YAW]); mul(2, v[ROOT_ROLL]);
        mul(1, v[PELVIS_YAW]); mul(0, v[PELVIS_PITCH]); mul(2, v[PELVIS_ROLL]);
        mul(1, v[SPINE_YAW]); mul(0, v[SPINE_PITCH]); mul(2, v[SPINE_ROLL]);
        mul(1, v[CHEST_YAW]); mul(0, v[CHEST_PITCH]); mul(2, v[CHEST_ROLL]);
        // Into the chest's frame: the transpose.
        float dx = M[0] * dir[0] + M[3] * dir[1] + M[6] * dir[2];
        float dy = M[1] * dir[0] + M[4] * dir[1] + M[7] * dir[2];
        float dz = M[2] * dir[0] + M[5] * dir[1] + M[8] * dir[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) return;
        dx /= len; dy /= len; dz /= len;
        float armX = -(float) Math.acos(Mth.clamp(dy, -1, 1));
        float armY = side == 0 ? (float) Math.atan2(-dx, -dz) : (float) Math.atan2(dx, -dz);
        // A straight-up or straight-down aim leaves the yaw free: keep the pose's.
        if (Math.abs(dy) > .995f) armY = p.arm(side, ARM_Y);
        float w = PantherMotion.clamp(weight);
        p.arm(side, ARM_X, Mth.lerp(w, p.arm(side, ARM_X), armX)).arm(side, ARM_Y, Mth.lerp(w, p.arm(side, ARM_Y), armY))
                .arm(side, ARM_Z, Mth.lerp(w, p.arm(side, ARM_Z), 0)).arm(side, ELBOW, Mth.lerp(w, p.arm(side, ELBOW), .06f))
                .arm(side, WRIST_X, Mth.lerp(w, p.arm(side, WRIST_X), 0)).arm(side, WRIST_Z, Mth.lerp(w, p.arm(side, WRIST_Z), 0))
                .arm(side, SH_FWD, Mth.lerp(w, p.arm(side, SH_FWD), 1.4f)).arm(side, CURL, Mth.lerp(w, p.arm(side, CURL), 1));
    }
}
