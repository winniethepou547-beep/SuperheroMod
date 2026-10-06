package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import net.minecraft.util.Mth;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Iceman's body in the shell (Q) and in shattered ground (R), as key-pose tracks over the action's clock:
 * <ul>
 * <li>SHELL_FORM / SHELL: he folds in, compact: a deep crouch with the knees together, the forearms crossed before the
 * face, the head down behind them; breathing slowly inside the ice; a blow on the shell rocks him a little (more for a
 * heavier one, away from where it came: IcemanShellFx.flinch).</li>
 * <li>SHELL_BURST: the stress: he curls tighter and tighter, trembling faster, then at BURST_STRESS bursts open (arms
 * and chest flung wide, the head thrown back, a small hop) and settles back into his stance.</li>
 * <li>SHELL_BREAK: the shell gone, he drops out of the crouch into a tired hero landing (the right knee down, the right
 * hand flat on the ground, the left forearm on the raised knee, the head bowed, the chest heaving), holds it, then from
 * BREAK_TICKS - BREAK_RISE slowly pushes himself up to standing.</li>
 * <li>GROUND: a step and a gathering, then down on the right knee with both hands slammed flat on the ground at
 * GROUND_DOWN, held (pressing, the cold pouring into the ground) while the cracks run, then up again.</li>
 * </ul>
 */
public final class IcemanShellMotion {
    private IcemanShellMotion() {}

    private static float noise(float x) { return Mth.sin(x * 1.7f) * .5f + Mth.sin(x * 3.1f + 1.3f) * .3f + Mth.sin(x * 7.3f + 4.1f) * .2f; }

    /** The move's pose at its clock t over the base, or null to keep the base. */
    public static Pose sample(int action, float t, Pose base, IcemanMotion.Ctx c) {
        Pose p = switch (action) {
            case SHELL_FORM -> form(base, c.time()).sample(t);
            case SHELL -> compact(base, c.time());
            case SHELL_BURST -> burst(base, t, c.time());
            case SHELL_BREAK -> broken(base, t, c.time());
            case GROUND -> ground(base, t, c.time());
            default -> null;
        };
        if (p != null && (action == SHELL_FORM || action == SHELL)) flinch(p, c.entity());
        return p;
    }

    // ------------------------------------------------------------------ Q: the shell
    /**
     * Compact: down in a deep crouch, the knees drawn together, leaning over them; both forearms crossed before the face
     * (fists closed), the head bowed behind them; slow, deep breaths.
     */
    static Pose compact(Pose base, float time) {
        Pose p = base.copy().set(PLANT, 1).set(LIFT, 0).set(CROUCH, 8.2f).set(SHIFT_X, 0).set(SHIFT_Z, 2.0f)
                .set(PELVIS_YAW, 0).set(PELVIS_PITCH, .05f).set(PELVIS_ROLL, 0)
                .set(SPINE_YAW, 0).set(SPINE_PITCH, .2f).set(SPINE_ROLL, 0).set(CHEST_YAW, 0).set(CHEST_PITCH, .12f).set(CHEST_ROLL, 0)
                .set(HEAD_YAW, 0).set(HEAD_PITCH, .42f).set(HEAD_ROLL, 0).set(NECK, .25f);
        for (int side = 0; side < 2; side++) {
            p.leg(side, LEG_X, 0).leg(side, LEG_Y, .05f).leg(side, LEG_Z, -.07f).leg(side, KNEE, 0).leg(side, ANKLE, 0);
            p.arm(side, SH_FWD, 1.1f).arm(side, SH_UP, .4f).arm(side, CURL, 1).arm(side, WRIST_X, .25f).arm(side, WRIST_Z, 0);
        }
        // The right forearm in front of the left (crossed a little apart, so they do not cut into each other).
        p.arm(0, ARM_X, -1.6f).arm(0, ARM_Y, -.75f).arm(0, ARM_Z, .1f).arm(0, ELBOW, 1.35f);
        p.arm(1, ARM_X, -1.75f).arm(1, ARM_Y, -.7f).arm(1, ARM_Z, .06f).arm(1, ELBOW, 1.25f);
        float breath = Mth.sin(time * .055f);
        p.add(CHEST_PITCH, -.03f * breath).add(SPINE_PITCH, -.012f * breath).add(HEAD_PITCH, -.015f * breath);
        for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, .18f * breath);
        return p;
    }
    /** Folding in as the ice climbs: a breath drawn in (a small rise, the arms gathering), then down into the crouch. */
    static Track form(Pose base, float time) {
        Pose hold = compact(base, time);
        Pose gather = base.copy().set(CROUCH, .4f).set(SPINE_PITCH, -.04f).set(CHEST_PITCH, -.1f).add(HEAD_PITCH, -.1f);
        for (int side = 0; side < 2; side++)
            gather.arm(side, SH_FWD, .5f).arm(side, SH_UP, .5f).arm(side, ARM_X, -.6f).arm(side, ARM_Y, -.4f).arm(side, ARM_Z, .3f)
                    .arm(side, ELBOW, 1.2f).arm(side, CURL, .6f);
        Pose half = gather.copy();
        half.toward(hold, .65f);
        return new Track(true).key(0, base).key(2.2f, gather).key(4.5f, half).key(7, hold);
    }
    /** A blow on the shell rocks him away from it (IcemanShellFx keeps the blows). */
    private static void flinch(Pose p, int entity) {
        float[] f = IcemanShellFx.flinch(entity);
        if (f == null) return;
        // f = {back, side, strength}: a lean away from the blow, the head ducking in.
        p.add(ROOT_PITCH, -.16f * f[0] * f[2]).add(ROOT_ROLL, .14f * f[1] * f[2]).add(HEAD_PITCH, .12f * f[2]).add(CROUCH, .6f * f[2]);
        for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, .4f * f[2]).armAdd(side, ELBOW, .08f * f[2]);
    }

    /**
     * The burst: curling ever tighter round the stress (shoulders up, arms squeezed in, head down), trembling faster and
     * harder, then KRAAAK at BURST_STRESS: flung wide open, chest out, the head thrown back, a small hop off the ground;
     * he lands, the arms come down, back into the stance.
     */
    static Pose burst(Pose base, float t, float time) {
        Pose hold = compact(base, time);
        Pose tight = hold.copy().set(CROUCH, 8.9f).add(SPINE_PITCH, .14f).add(CHEST_PITCH, .1f).add(HEAD_PITCH, .2f).set(NECK, .9f);
        for (int side = 0; side < 2; side++) tight.arm(side, SH_UP, 1.0f).arm(side, SH_FWD, 1.3f).armAdd(side, ELBOW, .2f).armAdd(side, ARM_Y, -.12f);
        for (int side = 0; side < 2; side++) tight.leg(side, LEG_Z, -.1f);
        Pose squeeze = tight.copy().add(CROUCH, .3f).add(SPINE_PITCH, .05f);
        Pose open = base.copy().set(PLANT, 1).set(CROUCH, 1.2f).set(LIFT, 2.6f).set(SPINE_PITCH, -.18f).set(CHEST_PITCH, -.32f).set(HEAD_PITCH, -.6f).set(NECK, 0)
                .set(SHIFT_X, 0).set(SHIFT_Z, 0);
        for (int side = 0; side < 2; side++) {
            open.arm(side, SH_FWD, -.3f).arm(side, SH_UP, .6f).arm(side, ARM_X, -.45f).arm(side, ARM_Y, .35f).arm(side, ARM_Z, 1.45f)
                    .arm(side, ELBOW, .15f).arm(side, CURL, 0).arm(side, WRIST_X, -.35f).arm(side, WRIST_Z, -.2f);
            open.leg(side, LEG_Z, .22f).leg(side, LEG_X, side == 0 ? .1f : -.15f).leg(side, KNEE, .25f).leg(side, ANKLE, .3f);
        }
        Pose land = open.copy().set(LIFT, 0).set(CROUCH, 3.6f).set(SPINE_PITCH, .05f).set(CHEST_PITCH, -.12f).set(HEAD_PITCH, -.2f).set(NECK, .2f);
        for (int side = 0; side < 2; side++) land.arm(side, ARM_Z, 1.1f).arm(side, ARM_X, -.3f).arm(side, ELBOW, .35f).arm(side, CURL, .3f).leg(side, KNEE, 0).leg(side, ANKLE, 0);
        Pose settle = base.copy().set(CROUCH, 1.4f);
        for (int side = 0; side < 2; side++) settle.arm(side, ARM_Z, .45f).arm(side, ELBOW, .4f);
        Pose p = new Track(true).key(0, hold).key(BURST_STRESS - 2.5f, tight).key(BURST_STRESS, squeeze).key(BURST_STRESS + 1.6f, open)
                .key(BURST_STRESS + 4.5f, land).key(BURST_STRESS + 10, settle).key(BURST_TICKS, base).sample(t);
        // The trembling: faster and harder as the stress builds, gone the moment it bursts.
        float stress = k(t, 1, BURST_STRESS) * (1 - k(t, BURST_STRESS, BURST_STRESS + .8f));
        if (stress > 0) {
            float f = time * (1.6f + 2.2f * stress);
            p.add(ROOT_ROLL, .025f * stress * noise(f)).add(ROOT_PITCH, .02f * stress * noise(f + 7)).add(SHIFT_X, .25f * stress * noise(f * 1.3f + 3))
                    .add(HEAD_ROLL, .04f * stress * noise(f * 1.1f + 11));
            for (int side = 0; side < 2; side++) p.armAdd(side, ARM_X, .05f * stress * noise(f * 1.2f + side * 5)).armAdd(side, SH_UP, .3f * stress * noise(f + side * 9));
        }
        return p;
    }

    /**
     * Broken: the shell gone, he drops out of the crouch into the tired hero landing (BREAK_LAND): down on the right knee,
     * the left foot planted ahead, the right hand flat on the ground, the left forearm resting across the raised knee, the
     * head hanging; held, the chest heaving with the strain; then (from BREAK_TICKS - BREAK_RISE) the slow push up: the
     * hand leaves the ground, the weight comes over the front foot, the knee lifts, he straightens up, the head last.
     */
    static Pose broken(Pose base, float t, float time) {
        Pose hold = compact(base, time);
        Pose slump = hold.copy().set(CROUCH, 9.2f).add(SPINE_PITCH, .15f).add(HEAD_PITCH, .25f).set(ROOT_PITCH, .12f);
        for (int side = 0; side < 2; side++)
            slump.arm(side, ARM_X, -.55f).arm(side, ARM_Y, -.3f).arm(side, ARM_Z, .25f).arm(side, ELBOW, .9f).arm(side, CURL, .45f).arm(side, SH_UP, -.2f);
        Pose land = landing(base);
        Pose deep = land.copy().add(LIFT, -.3f).add(SPINE_PITCH, .08f).add(HEAD_PITCH, .1f);
        deep.armAdd(0, ELBOW, .25f).armAdd(0, SH_UP, -.3f);
        int rise = BREAK_TICKS - BREAK_RISE;
        Pose push = land.copy().add(LIFT, 1.2f).add(SPINE_PITCH, -.05f).set(SHIFT_Z, -1.2f).add(HEAD_PITCH, -.05f);
        push.arm(0, ELBOW, .05f).arm(0, SH_UP, .2f);
        Pose knee = base.copy().set(PLANT, 0).set(CROUCH, 0).set(LIFT, -2.2f).set(SPINE_PITCH, .55f).set(CHEST_PITCH, .2f).set(HEAD_PITCH, .2f).set(NECK, .6f).set(SHIFT_Z, -1.0f);
        knee.leg(0, LEG_X, .2f).leg(0, KNEE, 1.0f).leg(0, ANKLE, .65f).leg(0, LEG_Z, .05f);
        knee.leg(1, LEG_X, -.95f).leg(1, KNEE, 1.15f).leg(1, ANKLE, -.05f).leg(1, LEG_Z, .1f);
        // The hands to the front thigh, pushing on it.
        knee.arm(0, SH_FWD, 1.0f).arm(0, ARM_X, -.75f).arm(0, ARM_Y, -.45f).arm(0, ARM_Z, 0).arm(0, ELBOW, .55f).arm(0, CURL, .55f).arm(0, WRIST_X, .3f);
        knee.arm(1, SH_FWD, .8f).arm(1, ARM_X, -.6f).arm(1, ARM_Y, -.2f).arm(1, ARM_Z, .1f).arm(1, ELBOW, .6f).arm(1, CURL, .6f).arm(1, WRIST_X, .3f);
        Pose stoop = base.copy().set(CROUCH, 2.4f).set(SPINE_PITCH, .38f).set(CHEST_PITCH, .14f).set(HEAD_PITCH, .25f).set(NECK, .5f);
        for (int side = 0; side < 2; side++) stoop.arm(side, SH_FWD, .6f).arm(side, ARM_X, -.3f).arm(side, ARM_Z, .12f).arm(side, ELBOW, .45f).arm(side, CURL, .5f);
        Pose tall = base.copy().add(SPINE_PITCH, .06f).add(HEAD_PITCH, .12f);
        Pose p = new Track(true).key(0, hold).key(2.6f, slump).key(BREAK_LAND, land).key(BREAK_LAND + 2.5f, deep).key(rise, land)
                .key(rise + 9, push).key(rise + 19, knee).key(rise + 29, stoop).key(BREAK_TICKS - 3, tall).key(BREAK_TICKS, base).sample(t);
        // The strain: the chest heaving (hard at first, easing as he rises), the arm on the ground shaking as it pushes.
        float heave = k(t, BREAK_LAND - 1, BREAK_LAND + 3) * (1 - .75f * k(t, rise + 4, BREAK_TICKS));
        float breath = Mth.sin(time * .36f);
        p.add(CHEST_PITCH, -.07f * heave * breath).add(SPINE_PITCH, -.03f * heave * breath).add(HEAD_PITCH, .04f * heave * breath);
        for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, .55f * heave * breath);
        float shake = k(t, rise + 1, rise + 5) * (1 - k(t, rise + 14, rise + 19));
        if (shake > 0) p.armAdd(0, ARM_X, .03f * shake * noise(time * 2.3f)).add(CHEST_ROLL, .02f * shake * noise(time * 1.9f + 4));
        return p;
    }
    /** The tired hero landing itself. */
    static Pose landing(Pose base) {
        Pose p = base.copy().set(PLANT, 0).set(CROUCH, 0).set(LIFT, -4.8f).set(SHIFT_X, 0).set(SHIFT_Z, 0).set(ROOT_PITCH, 0)
                .set(PELVIS_YAW, -.06f).set(PELVIS_PITCH, 0).set(PELVIS_ROLL, 0)
                .set(SPINE_PITCH, .8f).set(SPINE_YAW, .05f).set(SPINE_ROLL, 0).set(CHEST_PITCH, .36f).set(CHEST_YAW, .1f).set(CHEST_ROLL, -.04f)
                .set(HEAD_PITCH, .45f).set(HEAD_YAW, 0).set(HEAD_ROLL, .05f).set(NECK, .75f);
        // Right knee down (the shin flat behind), left foot planted ahead.
        p.leg(0, LEG_X, .18f).leg(0, KNEE, 1.62f).leg(0, ANKLE, .55f).leg(0, LEG_Z, .06f).leg(0, LEG_Y, .1f);
        p.leg(1, LEG_X, -1.35f).leg(1, KNEE, 1.42f).leg(1, ANKLE, -.05f).leg(1, LEG_Z, .12f).leg(1, LEG_Y, .1f);
        // The right arm straight down to the ground, the hand flat (fingers spread); the left forearm across the left knee.
        p.arm(0, SH_FWD, 1.4f).arm(0, SH_UP, -.6f).arm(0, ARM_X, -1.25f).arm(0, ARM_Y, -.3f).arm(0, ARM_Z, .1f).arm(0, ELBOW, .05f).arm(0, CURL, .12f).arm(0, WRIST_X, -.35f).arm(0, WRIST_Z, 0);
        p.arm(1, SH_FWD, .9f).arm(1, SH_UP, -.1f).arm(1, ARM_X, -.2f).arm(1, ARM_Y, -.5f).arm(1, ARM_Z, .05f).arm(1, ELBOW, 1.55f).arm(1, CURL, .55f).arm(1, WRIST_X, .25f).arm(1, WRIST_Z, 0);
        return p;
    }

    // ------------------------------------------------------------------ R: shattered ground
    /**
     * A step forward with the cold gathering in both hands drawn up before the chest, then down: the right knee to the
     * ground, both hands slammed flat on it before him at GROUND_DOWN (a jolt through the body), pressing there while the
     * frost pours out of them, then up again.
     */
    static Pose ground(Pose base, float t, float time) {
        Pose gather = base.copy().set(CROUCH, 1.6f).set(SPINE_PITCH, -.04f).set(CHEST_PITCH, -.14f).add(HEAD_PITCH, -.05f);
        gather.leg(1, LEG_X, -.32f).leg(0, LEG_X, .18f);
        for (int side = 0; side < 2; side++)
            gather.arm(side, SH_FWD, .6f).arm(side, SH_UP, .7f).arm(side, ARM_X, -1.1f).arm(side, ARM_Y, -.15f).arm(side, ARM_Z, .35f)
                    .arm(side, ELBOW, 1.6f).arm(side, CURL, .2f).arm(side, WRIST_X, -.5f);
        Pose slam = base.copy().set(PLANT, 0).set(CROUCH, 0).set(LIFT, -4.6f).set(SHIFT_X, 0).set(SHIFT_Z, -.6f).set(ROOT_PITCH, 0)
                .set(PELVIS_YAW, 0).set(PELVIS_PITCH, 0).set(PELVIS_ROLL, 0)
                .set(SPINE_PITCH, .84f).set(SPINE_YAW, 0).set(CHEST_PITCH, .32f).set(CHEST_YAW, 0).set(HEAD_PITCH, -.75f).set(HEAD_YAW, 0).set(NECK, .3f);
        slam.leg(0, LEG_X, .18f).leg(0, KNEE, 1.62f).leg(0, ANKLE, .55f).leg(0, LEG_Z, .08f).leg(0, LEG_Y, .1f);
        slam.leg(1, LEG_X, -1.3f).leg(1, KNEE, 1.4f).leg(1, ANKLE, -.05f).leg(1, LEG_Z, .16f).leg(1, LEG_Y, .1f);
        for (int side = 0; side < 2; side++)
            slam.arm(side, SH_FWD, 1.5f).arm(side, SH_UP, -.3f).arm(side, ARM_X, -1.05f).arm(side, ARM_Y, -.1f).arm(side, ARM_Z, .16f)
                    .arm(side, ELBOW, .32f).arm(side, CURL, .08f).arm(side, WRIST_X, -.4f).arm(side, WRIST_Z, 0);
        Pose impact = slam.copy().add(LIFT, -.4f).add(SPINE_PITCH, .07f).add(CHEST_PITCH, .04f);
        for (int side = 0; side < 2; side++) impact.armAdd(side, ELBOW, .3f).armAdd(side, SH_UP, -.4f);
        Pose press = slam.copy().add(SPINE_PITCH, .03f);
        Pose rising = base.copy().set(CROUCH, 3.0f).set(SPINE_PITCH, .3f).set(CHEST_PITCH, .05f).set(HEAD_PITCH, -.2f);
        rising.leg(1, LEG_X, -.3f).leg(0, LEG_X, .15f);
        for (int side = 0; side < 2; side++) rising.arm(side, SH_FWD, .6f).arm(side, ARM_X, -.4f).arm(side, ARM_Z, .25f).arm(side, ELBOW, .5f).arm(side, CURL, .35f);
        Pose p = new Track(true).key(0, base).key(3.6f, gather).key(GROUND_DOWN, slam).key(GROUND_DOWN + 1.6f, impact).key(GROUND_TICKS - 9, press)
                .key(GROUND_TICKS - 4, rising).key(GROUND_TICKS, base).sample(t);
        // Pressing into the ground: the shoulders working as the cold pours out.
        float pour = k(t, GROUND_DOWN + 1, GROUND_DOWN + 3) * (1 - k(t, GROUND_TICKS - 9, GROUND_TICKS - 6));
        if (pour > 0) {
            for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, .25f * pour * noise(time * 1.4f + side * 3));
            p.add(CHEST_PITCH, .02f * pour * noise(time * 1.1f));
        }
        return p;
    }
}
