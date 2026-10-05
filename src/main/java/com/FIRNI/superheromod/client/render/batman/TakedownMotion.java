package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import com.FIRNI.superheromod.heroes.batman.TakedownPath;
import net.minecraft.util.Mth;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Batman's body through the Batmobile remote takedown (signs as in PantherMotion; side 0 is his right):
 * the call (right hand up at the ear, the head tipped to it), the dash (both arms thrown wide, leaning in, then driving
 * forward with the arms open like a tackle: heavy and controlled, not acrobatic), the hold (a tucked front flip over
 * them, the right hand up to the back of their head for the tracker, both arms locked round their body from behind,
 * braced and rocked by their struggle and the rounds, the right hand up and down onto the back of their head, down on
 * one knee over them, up) and the miss (carried on, off balance, the hand back at the ear, a shake of the head).
 */
final class TakedownMotion {
    private TakedownMotion() {}

    static Pose pose(int action, Pose base, float t, float time) {
        return switch (action) {
            case TD_SIGNAL -> signal(base, t);
            case TD_DASH -> dash(base, t, time);
            case TD_HOLD -> hold(base, t, time);
            default -> miss(base, t, time);
        };
    }

    /** The right hand at the ear (the earpiece), the head tipped toward it. */
    static Pose ear(Pose base) {
        Pose p = base.copy().set(HEAD_ROLL, -.14f).set(HEAD_YAW, -.12f).set(HEAD_PITCH, .06f).set(CHEST_YAW, -.08f);
        p.arm(0, SH_UP, .6f).arm(0, ARM_X, -1.0f).arm(0, ARM_Z, .85f).arm(0, ARM_Y, .25f).arm(0, ELBOW, 2.45f).arm(0, WRIST_X, .3f).arm(0, CURL, .55f);
        return p;
    }
    private static Pose signal(Pose base, float t) {
        Pose p = base.copy();
        p.toward(ear(base), TakedownPath.k(t, 0, TD_EAR));
        // A small nod as the call is confirmed; the fingers press the earpiece on each beep.
        float nod = Mth.sin(Mth.PI * Mth.clamp((t - TD_CONFIRM) / 3f, 0, 1));
        p.add(HEAD_PITCH, .1f * nod);
        float press = Math.max(window(t, 4, 2), Math.max(window(t, 9, 2), window(t, TD_CONFIRM, 2)));
        p.armAdd(0, CURL, .3f * press).armAdd(0, WRIST_X, -.15f * press);
        // Into the dash's wind-up at the end.
        p.toward(wide(base), TakedownPath.k(t, TD_SIGNAL_TICKS - 3, TD_SIGNAL_TICKS + 1) * .6f);
        return p;
    }
    private static float window(float t, float at, float half) { return Math.max(0, 1 - Math.abs(t - at) / half); }

    /** Both arms thrown wide, crouched, leaning toward them. */
    private static Pose wide(Pose base) {
        Pose p = base.copy().set(CROUCH, 2.6f).set(SPINE_PITCH, .35f).set(ROOT_PITCH, .12f).set(HEAD_PITCH, -.3f);
        for (int side = 0; side < 2; side++) p.arm(side, ARM_X, -.45f).arm(side, ARM_Z, 1.35f).arm(side, ARM_Y, .2f).arm(side, ELBOW, .35f).arm(side, CURL, .25f);
        p.leg(0, LEG_X, -.5f).leg(0, KNEE, .9f).leg(1, LEG_X, .45f).leg(1, KNEE, .45f);
        return p;
    }
    /** Driving forward, shoulders first, the arms open and reaching like a tackle. */
    private static Pose charge(Pose base, float time) {
        Pose p = base.copy().set(PLANT, .4f).set(ROOT_PITCH, .42f).set(SPINE_PITCH, .22f).set(HEAD_PITCH, -.45f).set(CROUCH, 1.2f);
        for (int side = 0; side < 2; side++) p.arm(side, SH_FWD, .9f).arm(side, ARM_X, -.95f).arm(side, ARM_Z, 1.0f).arm(side, ARM_Y, .25f).arm(side, ELBOW, .55f).arm(side, CURL, .6f);
        float stride = Mth.sin(time * 1.7f);
        p.leg(0, LEG_X, -.75f * stride).leg(1, LEG_X, .75f * stride).leg(0, KNEE, .5f + .5f * Math.max(0, stride)).leg(1, KNEE, .5f + .5f * Math.max(0, -stride));
        return p;
    }
    private static Pose dash(Pose base, float t, float time) {
        Pose w = wide(base), c = charge(base, time);
        return new Track().key(0, wide(base).copy()).key(TD_WIND * .7f, w).key(TD_WIND + 1.2f, c).key(TD_DASH_TICKS + 6, c).sample(t);
    }

    // ------------------------------------------------------------------ the hold
    /** Both arms locked round their body from behind, braced. */
    static Pose lock(Pose base) {
        Pose p = base.copy().set(CROUCH, 1.6f).set(SPINE_PITCH, .18f).set(HEAD_PITCH, .05f);
        for (int side = 0; side < 2; side++) p.arm(side, SH_FWD, 1.3f).arm(side, ARM_X, -1.45f).arm(side, ARM_Z, -.4f).arm(side, ARM_Y, -.3f).arm(side, ELBOW, .95f).arm(side, CURL, 1);
        p.leg(0, LEG_X, .35f).leg(0, KNEE, .5f).leg(1, LEG_X, -.3f).leg(1, KNEE, .75f);
        return p;
    }
    private static Track holdTrack(Pose base) {
        Pose tuck = base.copy().set(PLANT, 0).set(SPINE_PITCH, .5f).set(HEAD_PITCH, .4f).set(LIFT, 1.5f);
        for (int side = 0; side < 2; side++) {
            tuck.leg(side, LEG_X, -1.4f).leg(side, KNEE, 2.05f);
            tuck.arm(side, ARM_X, -1.1f).arm(side, ELBOW, 1.5f).arm(side, ARM_Z, .3f).arm(side, CURL, .9f);
        }
        Pose land = base.copy().set(CROUCH, 4.2f).set(SPINE_PITCH, .4f).set(HEAD_PITCH, -.2f);
        land.arm(0, ARM_Z, .8f).arm(0, ARM_X, -.3f).arm(0, ELBOW, .5f).arm(1, ARM_Z, .8f).arm(1, ARM_X, -.3f).arm(1, ELBOW, .5f);
        Pose reach = base.copy().set(CROUCH, 1.2f).set(SPINE_PITCH, .12f).set(HEAD_PITCH, .1f);
        reach.arm(0, SH_FWD, 1f).arm(0, SH_UP, .5f).arm(0, ARM_X, -2.05f).arm(0, ARM_Z, .15f).arm(0, ELBOW, .6f).arm(0, WRIST_X, .45f).arm(0, CURL, .7f);
        reach.arm(1, ARM_X, -.9f).arm(1, ARM_Z, .3f).arm(1, ELBOW, 1.1f).arm(1, CURL, 1);
        Pose press = reach.copy();
        press.arm(0, WRIST_X, -.25f).arm(0, ARM_X, -1.95f);
        Pose lock = lock(base);
        Pose raise = lock.copy().set(SPINE_PITCH, .05f).set(HEAD_PITCH, -.1f);
        raise.arm(0, SH_UP, .8f).arm(0, ARM_X, -2.65f).arm(0, ARM_Z, .1f).arm(0, ARM_Y, 0).arm(0, ELBOW, 1.35f).arm(0, CURL, .6f).arm(0, SH_FWD, .6f);
        Pose drive = base.copy().set(CROUCH, 4.6f).set(SPINE_PITCH, .75f).set(HEAD_PITCH, -.35f).set(PLANT, .6f);
        drive.arm(0, SH_FWD, 1.4f).arm(0, ARM_X, -1.05f).arm(0, ARM_Z, .05f).arm(0, ELBOW, .25f).arm(0, WRIST_X, -.35f).arm(0, CURL, .7f);
        drive.arm(1, ARM_X, -.5f).arm(1, ARM_Z, .6f).arm(1, ELBOW, .6f).arm(1, CURL, .8f);
        drive.leg(0, LEG_X, -.6f).leg(0, KNEE, 1.3f).leg(1, LEG_X, .4f).leg(1, KNEE, 1.4f);
        Pose over = BatmanMotion.kneel(base);
        over.arm(0, SH_FWD, 1.2f).arm(0, ARM_X, -.9f).arm(0, ARM_Z, .1f).arm(0, ELBOW, .3f).arm(0, CURL, .8f);
        return new Track().key(0, tuck).key(TD_FLIP - 1, tuck).key(TD_FLIP + 1, land).key(TD_TRACK - 1.5f, reach).key(TD_TRACK, press)
                .key(TD_TRACK + 1.5f, reach).key(TD_LOCK, lock).key(TD_PUSH, lock).key(TD_PUSH + 3, raise).key(TD_FALL + 1, drive)
                .key(TD_IMPACT + 2, over).key(TD_HOLD_TICKS - 6, over).key(TD_HOLD_TICKS + 4, base);
    }
    private static Pose hold(Pose base, float t, float time) {
        Pose p = holdTrack(base).sample(t);
        // The front flip over them: one whole turn about his middle, tucked.
        float flip = TakedownPath.k(t, .5f, TD_FLIP - .8f);
        if (flip > 0 && flip < 1) p.add(ROOT_PITCH, Mth.TWO_PI * flip);
        // Holding on: rocked by their struggle and by the rounds hitting them.
        if (t > TD_LOCK && t < TD_PUSH + 2) {
            float rock = .07f * Mth.sin(time * .55f + 2) + .6f * jolt(t);
            p.add(SPINE_ROLL, rock).add(ROOT_ROLL, rock * .4f);
        }
        return p;
    }
    /** The rounds landing on the one he holds: a signed jolt (right, left, right...) at hold time t. */
    static float jolt(float t) {
        float c = t - TD_CAR, j = 0;
        for (int n = 0; n < TakedownPath.ROUNDS; n++) {
            float d = c - TakedownPath.roundAt(n) - TakedownPath.ROUND_TICKS;
            if (d < 0 || d > 6) continue;
            j += ((n / 2) % 2 == 0 ? 1 : -1) * .13f * (float) Math.exp(-d / 1.4f);
        }
        return Mth.clamp(j, -.3f, .3f);
    }

    // ------------------------------------------------------------------ the miss
    private static Pose miss(Pose base, float t, float time) {
        Pose stumble = base.copy().set(CROUCH, 3.2f).set(SPINE_PITCH, .45f).set(SPINE_ROLL, .15f).set(HEAD_PITCH, -.2f);
        stumble.arm(0, ARM_X, -.3f).arm(0, ARM_Z, 1.2f).arm(0, ELBOW, .4f).arm(1, ARM_X, -.6f).arm(1, ARM_Z, 1.0f).arm(1, ELBOW, .5f);
        stumble.leg(0, LEG_X, -.8f).leg(0, KNEE, 1.0f).leg(1, LEG_X, .5f).leg(1, KNEE, .5f);
        Pose ear = ear(base);
        Pose p = new Track().key(0, charge(base, time)).key(TD_SLIDE + 3, stumble).key(TD_MISS_EAR, BatmanMotion.guard(base)).key(TD_MISS_EAR + 3, ear)
                .key(TD_MISS_TICKS - 5, ear).key(TD_MISS_TICKS, base).sample(t);
        // The call cancelled: a short shake of the head.
        if (t > TD_ABORT && t < TD_ABORT + 8) p.add(HEAD_YAW, .18f * Mth.sin((t - TD_ABORT) * 1.6f) * (1 - (t - TD_ABORT) / 8));
        return p;
    }
}
