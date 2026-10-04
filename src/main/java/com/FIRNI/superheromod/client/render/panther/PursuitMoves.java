package com.FIRNI.superheromod.client.render.panther;

import net.minecraft.util.Mth;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * Every pose of THE FINAL PURSUIT, for Panther and for the gunman, as functions of scene time: key poses on
 * PantherMotion's smooth curve (velocity carried through each key, so nothing stops dead or snaps), with the
 * car's forces, the bullets' jolts and the breathing layered over them. No vanilla move anywhere: the driving
 * hands follow the wheel, the exit out of the window, the hang over the roof's edge, the cat-like hop back, the
 * coil and the leap, the landing that becomes a grip that becomes the stance, the claws going in one hand after
 * the other, the tear with the whole back behind it, the throw, the charge, the release, the tuck and twist in
 * the air, the three-point landing, the rise.
 */
public final class PursuitMoves {
    private PursuitMoves() {}

    // ------------------------------------------------------------------ Panther's key poses
    /** At the wheel: settled back in the seat, thighs forward, both hands on the wheel at a quarter to three. */
    static Pose seat() {
        Pose p = new Pose();
        p.set(PLANT, 0).set(SPINE_PITCH, -.30f).set(CHEST_PITCH, -.06f).set(NECK, .35f).set(HEAD_PITCH, .28f).set(EYES, .1f);
        for (int side = 0; side < 2; side++) {
            p.leg(side, LEG_X, -1.45f).leg(side, KNEE, 1.3f).leg(side, LEG_Z, .07f).leg(side, LEG_Y, .08f).leg(side, ANKLE, -.12f);
            p.arm(side, SH_FWD, 1.3f).arm(side, ARM_X, -1.10f).arm(side, ARM_Z, -.35f).arm(side, ARM_Y, 0).arm(side, ELBOW, .15f)
                    .arm(side, WRIST_X, -.35f).arm(side, WRIST_Z, .2f).arm(side, CURL, .92f);
        }
        p.leg(0, LEG_X, -1.52f).leg(0, KNEE, 1.18f);
        return p;
    }
    /** Low on a moving roof: wide feet, bent knees, weight low, arms out for balance, claws ready. */
    static Pose roofStance() {
        Pose p = new Pose();
        p.set(PLANT, 1).set(CROUCH, 5).set(SPINE_PITCH, .38f).set(CHEST_PITCH, .1f).set(HEAD_PITCH, -.25f).set(NECK, .45f).set(EYES, .3f);
        for (int side = 0; side < 2; side++) {
            p.leg(side, LEG_Z, .38f).leg(side, LEG_Y, .25f);
            p.arm(side, SH_FWD, .5f).arm(side, ARM_X, -.45f).arm(side, ARM_Z, .75f).arm(side, ELBOW, .7f).arm(side, CURL, .25f);
        }
        p.leg(0, LEG_X, .12f).leg(1, LEG_X, -.18f);
        return p;
    }
    /** Coiled on the car behind: as low as he goes, the head up, the hands down by the roof. */
    static Pose coil() {
        Pose p = new Pose();
        p.set(PLANT, 1).set(CROUCH, 9.3f).set(SPINE_PITCH, .78f).set(CHEST_PITCH, .2f).set(HEAD_PITCH, -.5f).set(NECK, .7f).set(EYES, .4f);
        for (int side = 0; side < 2; side++) {
            p.leg(side, LEG_Z, .3f).leg(side, LEG_Y, .2f);
            p.arm(side, SH_FWD, 1f).arm(side, ARM_X, -.55f).arm(side, ARM_Z, .22f).arm(side, ELBOW, .3f).arm(side, CURL, .3f).arm(side, WRIST_X, .3f);
        }
        p.leg(0, LEG_X, .2f).leg(1, LEG_X, -.25f);
        return p;
    }
    /** The charge stance on the roof, arms open: feet wide, knees soft, chest forward, head a little up. */
    static Pose open() {
        Pose p = new Pose();
        p.set(PLANT, 1).set(CROUCH, 2.4f).set(SPINE_PITCH, -.06f).set(CHEST_PITCH, -.22f).set(HEAD_PITCH, -.36f).set(NECK, .2f).set(EYES, .8f);
        for (int side = 0; side < 2; side++) {
            p.leg(side, LEG_Z, .36f).leg(side, LEG_Y, .25f);
            p.arm(side, SH_FWD, -.8f).arm(side, ARM_X, -.35f).arm(side, ARM_Z, 1.25f).arm(side, ARM_Y, .35f).arm(side, ELBOW, .35f)
                    .arm(side, WRIST_X, -.5f).arm(side, CURL, 0);
        }
        p.leg(0, LEG_X, .06f).leg(1, LEG_X, -.06f);
        return p;
    }
    /** Three points down: deep on bent knees, the right hand flat on the road, the left arm back, the head down. */
    static Pose threePoint() {
        Pose p = new Pose();
        p.set(PLANT, 1).set(CROUCH, 9.5f).set(SPINE_PITCH, .9f).set(CHEST_PITCH, .25f).set(HEAD_PITCH, .45f).set(NECK, .5f).set(EYES, .5f);
        p.leg(0, LEG_X, -.28f).leg(0, LEG_Z, .22f).leg(0, LEG_Y, .15f).leg(1, LEG_X, .48f).leg(1, KNEE, .55f).leg(1, LEG_Z, .18f).leg(1, ANKLE, -.4f);
        p.arm(0, SH_FWD, 1.2f).arm(0, ARM_X, -.55f).arm(0, ARM_Z, .12f).arm(0, ELBOW, .15f).arm(0, WRIST_X, .6f).arm(0, CURL, .3f);
        p.arm(1, SH_FWD, -.4f).arm(1, ARM_X, .95f).arm(1, ARM_Z, .55f).arm(1, ELBOW, .25f).arm(1, CURL, .12f);
        return p;
    }
    /** The ready stance it ends on: a little forward, shoulders low, knees soft, hands open, claws ready. */
    static Pose ready(float time) {
        Pose p = stance(time, 1, 0);
        p.add(SPINE_PITCH, .12f).add(CROUCH, .9f).add(HEAD_PITCH, -.05f);
        for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, -1.0f).armAdd(side, ARM_Z, .12f).arm(side, CURL, .2f).armAdd(side, ELBOW, .15f);
        return p;
    }

    private static Track coilLeap, landing, turnClaw, tear, throwIt, charge, flight;
    private static void build() {
        if (coilLeap != null) return;
        Pose c = coil();
        Pose riding = riding();
        Pose rockBack = c.copy().add(SHIFT_Z, 1.2f).add(SPINE_PITCH, -.1f);
        Pose load = c.copy().add(SHIFT_Z, 1.6f).set(CROUCH, 9.5f).set(SPINE_PITCH, .85f);
        for (int side = 0; side < 2; side++) load.arm(side, ARM_X, .7f).arm(side, ARM_Z, .35f).arm(side, ELBOW, .4f).arm(side, CURL, .15f);
        Pose launch = new Pose();
        launch.set(PLANT, .3f).set(ROOT_PITCH, .65f).set(SPINE_PITCH, .2f).set(HEAD_PITCH, -.6f).set(NECK, .6f).set(EYES, .5f);
        for (int side = 0; side < 2; side++) {
            launch.leg(side, LEG_X, .5f).leg(side, KNEE, .25f).leg(side, ANKLE, .7f).leg(side, LEG_Z, .12f);
            launch.arm(side, ARM_X, -1.5f).arm(side, ARM_Z, .8f).arm(side, ELBOW, .4f).arm(side, CURL, .1f);
        }
        Pose soar = launch.copy().set(ROOT_PITCH, .5f).set(PLANT, 0).set(HEAD_PITCH, -.5f);
        for (int side = 0; side < 2; side++) {
            soar.arm(side, ARM_X, -.6f).arm(side, ARM_Z, 1.25f).arm(side, ELBOW, .3f).arm(side, SH_FWD, -.3f);
            soar.leg(side, LEG_X, -.4f).leg(side, KNEE, 1.2f).leg(side, ANKLE, .4f);
        }
        soar.leg(1, LEG_X, .1f).leg(1, KNEE, .7f);
        Pose reach = soar.copy().set(ROOT_PITCH, .2f).set(SPINE_PITCH, .3f).set(HEAD_PITCH, -.3f);
        for (int side = 0; side < 2; side++) {
            reach.leg(side, LEG_X, -1.1f).leg(side, KNEE, .9f).leg(side, ANKLE, .1f).leg(side, LEG_Z, .3f);
            reach.arm(side, ARM_X, -1.0f).arm(side, ARM_Z, .9f).arm(side, ELBOW, .5f);
        }
        coilLeap = new Track(true).key(ULT_COIL - 6, riding).key(ULT_COIL, c).key(ULT_COIL + 5, rockBack).key(ULT_COIL + 10, c.copy().add(SHIFT_Z, -.4f))
                .key(ULT_LEAP - 1, load).key(ULT_LEAP + 2, launch).key(ULT_LEAP + 8, soar).key(ULT_TOUCH - 3, reach);

        Pose touch = roofStance().copy().set(CROUCH, 2).set(SPINE_PITCH, .35f);
        for (int side = 0; side < 2; side++) touch.arm(side, ARM_X, -.8f).arm(side, ARM_Z, 1.0f).arm(side, ELBOW, .4f);
        Pose absorb = roofStance().copy().set(CROUCH, 9.5f).set(SPINE_PITCH, .75f).set(CHEST_PITCH, .2f).set(HEAD_PITCH, -.4f);
        absorb.arm(0, SH_FWD, 1.2f).arm(0, ARM_X, -.35f).arm(0, ARM_Z, .2f).arm(0, ELBOW, .15f).arm(0, WRIST_X, .45f).arm(0, CURL, .55f);
        absorb.arm(1, ARM_X, .5f).arm(1, ARM_Z, .9f).arm(1, ELBOW, .3f).arm(1, CURL, .2f);
        Pose rising = roofStance().copy().set(CROUCH, 6.5f).set(SPINE_PITCH, .5f);
        landing = new Track(true).key(ULT_TOUCH - 3, reach).key(ULT_TOUCH, touch).key(ULT_TOUCH + 2.2f, absorb).key(ULT_TOUCH + 6, absorb.copy().add(SHIFT_Z, -.5f))
                .key(ULT_TOUCH + 10, rising).key(ULT_INSIDE, roofStance());

        Pose rs = roofStance();
        Pose stepR = rs.copy().set(CROUCH, 6);
        stepR.leg(0, LEG_X, -.6f).leg(0, KNEE, .9f).leg(0, ANKLE, .3f).set(PLANT, .7f);
        Pose stepL = rs.copy().set(CROUCH, 6);
        stepL.leg(1, LEG_X, -.6f).leg(1, KNEE, .9f).leg(1, ANKLE, .3f).set(PLANT, .7f);
        Pose low = rs.copy().set(CROUCH, 8.5f).set(SPINE_PITCH, .65f).set(CHEST_PITCH, .2f).set(HEAD_PITCH, -.2f);
        Pose windR = low.copy();
        windR.arm(0, ARM_X, -2.4f).arm(0, ARM_Z, .3f).arm(0, ELBOW, .9f).arm(0, CURL, 0).arm(0, SH_UP, 1.2f);
        windR.arm(1, ARM_X, -.7f).arm(1, ARM_Z, .4f).arm(1, ELBOW, .4f).arm(1, CURL, .1f);
        windR.set(CHEST_YAW, -.25f);
        Pose inR = low.copy().set(CROUCH, 9).set(SPINE_PITCH, .72f).set(CHEST_PITCH, .42f).set(CHEST_YAW, .15f);
        inR.arm(0, SH_FWD, 1.3f).arm(0, ARM_X, -.15f).arm(0, ARM_Z, -.2f).arm(0, ELBOW, 1.3f).arm(0, WRIST_X, -.3f).arm(0, CURL, .25f);
        inR.arm(1, ARM_X, -.8f).arm(1, ARM_Z, .5f).arm(1, ELBOW, .5f).arm(1, CURL, .1f);
        Pose windL = inR.copy().set(CHEST_YAW, .25f);
        windL.arm(1, ARM_X, -2.4f).arm(1, ARM_Z, .3f).arm(1, ELBOW, .9f).arm(1, CURL, 0).arm(1, SH_UP, 1.2f);
        Pose inL = inR.copy().set(CHEST_YAW, 0);
        inL.arm(1, SH_FWD, 1.3f).arm(1, ARM_X, -.15f).arm(1, ARM_Z, -.2f).arm(1, ELBOW, 1.3f).arm(1, WRIST_X, -.3f).arm(1, CURL, .25f);
        Pose press = inL.copy().set(CROUCH, 9.5f).set(SPINE_PITCH, .85f).set(HEAD_PITCH, -.2f);
        for (int side = 0; side < 2; side++) press.arm(side, SH_UP, -1.2f).arm(side, CURL, .6f);
        turnClaw = new Track(true).key(ULT_TURN, rs).key(ULT_TURN + 3, stepR).key(ULT_TURN + 7, stepL).key(ULT_TURN + 12, rs).key(ULT_CLAW_R - 6, low)
                .key(ULT_CLAW_R - 2, windR).key(ULT_CLAW_R, inR).key(ULT_CLAW_R + 3, inR.copy().add(CROUCH, .3f)).key(ULT_CLAW_L - 3, windL)
                .key(ULT_CLAW_L, inL).key(ULT_PRESS, press);

        Pose heave = press.copy().set(CROUCH, 7).set(SPINE_PITCH, .55f).set(HEAD_PITCH, -.4f);
        for (int side = 0; side < 2; side++) heave.arm(side, ARM_X, -.9f).arm(side, ELBOW, 1.1f).arm(side, CURL, .95f).arm(side, SH_UP, 0);
        Pose haul = heave.copy().set(CROUCH, 3.5f).set(SPINE_PITCH, .1f).set(CHEST_PITCH, -.1f);
        for (int side = 0; side < 2; side++) haul.arm(side, ARM_X, -1.5f).arm(side, ELBOW, 1.5f).arm(side, SH_UP, 1.0f);
        Pose rip = haul.copy().set(CROUCH, 2).set(SPINE_PITCH, -.25f).set(CHEST_PITCH, -.15f).set(HEAD_PITCH, -.5f).set(EYES, .6f);
        for (int side = 0; side < 2; side++) rip.arm(side, ARM_X, -2.7f).arm(side, ARM_Z, .55f).arm(side, ELBOW, .4f).arm(side, CURL, .2f);
        Pose settle = rs.copy().set(CROUCH, 5.5f);
        tear = new Track(true).key(ULT_PRESS, press).key(ULT_TEAR, press.copy().add(CROUCH, -.4f)).key(ULT_TEAR + 4, heave).key(ULT_TEAR + 8, haul)
                .key(ULT_ROOF_FREE, rip).key(ULT_ROOF_FREE + 7, settle);

        Pose bend = rs.copy().set(CROUCH, 8.5f).set(SPINE_PITCH, .9f).set(CHEST_PITCH, .35f).set(HEAD_PITCH, .1f);
        for (int side = 0; side < 2; side++) bend.arm(side, SH_FWD, 1.4f).arm(side, ARM_X, -1.3f).arm(side, ARM_Z, .1f).arm(side, ELBOW, .4f).arm(side, CURL, .2f);
        Pose grip = bend.copy();
        for (int side = 0; side < 2; side++) grip.arm(side, CURL, 1);
        Pose loaded = grip.copy().set(CROUCH, 9).set(SPINE_PITCH, 1.0f);
        Pose rise = loaded.copy().set(CROUCH, 1).set(SPINE_PITCH, -.15f).set(CHEST_PITCH, -.1f).set(HEAD_PITCH, -.5f);
        for (int side = 0; side < 2; side++) rise.arm(side, ARM_X, -2.2f).arm(side, ELBOW, .6f);
        Pose fling = rise.copy().set(LIFT, 1.5f).set(HEAD_PITCH, -.7f).set(EYES, .6f);
        for (int side = 0; side < 2; side++) fling.arm(side, ARM_X, -2.95f).arm(side, ARM_Z, .4f).arm(side, ELBOW, .2f).arm(side, CURL, 0);
        Pose after = fling.copy().set(LIFT, 0).set(CROUCH, 3).set(HEAD_PITCH, -.4f);
        for (int side = 0; side < 2; side++) after.arm(side, ARM_X, -1.6f).arm(side, ARM_Z, .8f).arm(side, ELBOW, .5f);
        throwIt = new Track(true).key(ULT_ROOF_FREE + 7, settle).key(ULT_REACH, settle).key(ULT_REACH + 4, bend).key(ULT_THROW - 1, grip).key(ULT_THROW, loaded)
                .key(ULT_THROW + 3, rise).key(ULT_THROW + 5, fling).key(ULT_THROW + 12, after);

        Pose plant = open().copy().set(CROUCH, 2.8f).set(CHEST_PITCH, .05f).set(SPINE_PITCH, .1f).set(HEAD_PITCH, -.25f).set(EYES, .4f);
        for (int side = 0; side < 2; side++) plant.arm(side, SH_FWD, .2f).arm(side, ARM_X, -.3f).arm(side, ARM_Z, .5f).arm(side, ELBOW, .9f).arm(side, CURL, .5f).arm(side, ARM_Y, 0).arm(side, WRIST_X, 0);
        Pose opened = open();
        Pose heavy = open().copy().add(CROUCH, .9f).set(HEAD_PITCH, -.3f).set(EYES, 1);
        for (int side = 0; side < 2; side++) heavy.armAdd(side, ARM_Z, -.1f).armAdd(side, ELBOW, .1f).arm(side, CURL, .15f);
        charge = new Track(true).key(ULT_THROW + 12, after).key(ULT_CHARGE, plant).key(ULT_CHARGE + 14, plant.copy().add(CROUCH, .3f))
                .key(ULT_CHARGE + 30, opened).key(ULT_HOLD, opened.copy().set(EYES, .95f)).key(PursuitPath.BOOM, heavy);

        // The flight: thrown open by the release, flung up and back; a tuck; the twist; open again; the legs reach for the road.
        Pose blown = new Pose();
        blown.set(PLANT, 0).set(SPINE_PITCH, -.3f).set(CHEST_PITCH, -.3f).set(HEAD_PITCH, -.6f).set(EYES, 1);
        for (int side = 0; side < 2; side++) {
            blown.arm(side, SH_FWD, -1.1f).arm(side, ARM_X, .2f).arm(side, ARM_Z, 1.45f).arm(side, ARM_Y, .4f).arm(side, ELBOW, .12f).arm(side, CURL, 0);
            blown.leg(side, LEG_X, .2f).leg(side, KNEE, .3f).leg(side, LEG_Z, .3f);
        }
        Pose arch = blown.copy().set(ROOT_PITCH, -.75f).set(SPINE_PITCH, -.35f);
        Pose tucked = new Pose();
        tucked.set(PLANT, 0).set(ROOT_PITCH, -2.4f).set(ROOT_YAW, .4f).set(SPINE_PITCH, .6f).set(CHEST_PITCH, .25f).set(HEAD_PITCH, .3f).set(EYES, .7f);
        for (int side = 0; side < 2; side++) {
            tucked.leg(side, LEG_X, -1.7f).leg(side, KNEE, 2.2f).leg(side, ANKLE, .4f).leg(side, LEG_Z, .12f);
            tucked.arm(side, ARM_X, -1.2f).arm(side, ARM_Z, .1f).arm(side, ELBOW, 1.6f).arm(side, CURL, .5f);
        }
        Pose turning = tucked.copy().set(ROOT_PITCH, -4.4f).set(ROOT_YAW, 2.4f);
        Pose opening = new Pose();
        opening.set(PLANT, 0).set(ROOT_PITCH, -Mth.TWO_PI).set(ROOT_YAW, Mth.PI).set(SPINE_PITCH, .2f).set(HEAD_PITCH, .15f).set(EYES, .6f);
        for (int side = 0; side < 2; side++) {
            opening.leg(side, LEG_X, -.2f).leg(side, KNEE, .45f).leg(side, ANKLE, .25f).leg(side, LEG_Z, .25f);
            opening.arm(side, ARM_X, -.5f).arm(side, ARM_Z, 1.1f).arm(side, ELBOW, .4f).arm(side, CURL, .2f);
        }
        Pose reaching = opening.copy().set(SPINE_PITCH, .35f).set(HEAD_PITCH, .25f);
        for (int side = 0; side < 2; side++) reaching.leg(side, LEG_X, -.4f).leg(side, KNEE, .6f).arm(side, ARM_X, -.2f).arm(side, ARM_Z, .9f);
        float b = PursuitPath.BOOM, land = PursuitPath.LAND;
        flight = new Track(true).key(b, heavy).key(b + 1.2f, blown).key(b + 5, arch).key(b + 13, tucked).key(b + 20, turning)
                .key(b + 26, opening).key(b + land - 2.5f, reaching).key(b + land, reaching);
    }

    /** Panther's pose at scene time tau (time = the free-running clock for breathing). */
    public static Pose panther(float tau, float time) {
        build();
        Pose p;
        if (tau < ULT_COIL - 6) p = onSuv(tau, time);
        else if (tau < ULT_TOUCH - 3) p = coilLeap.sample(tau);
        else if (tau < ULT_INSIDE) p = landing.sample(tau);
        else if (tau < ULT_TURN) p = balance(roofStance(), tau, time).add(SHIFT_X, -2.2f * PursuitPath.dodge(tau));
        else if (tau < ULT_PRESS) p = turnClaw.sample(tau);
        else if (tau < ULT_ROOF_FREE + 7) p = tear.sample(tau);
        else if (tau < ULT_THROW + 12) p = throwIt.sample(tau);
        else if (tau < PursuitPath.BOOM) p = charging(tau, time);
        else if (tau < PursuitPath.BOOM + PursuitPath.LAND) p = flight.sample(tau);
        else p = landed(tau - PursuitPath.BOOM, time);
        // The shots that hit him: each one knocks the part it strikes back hard (the force goes into the suit, not
        // through him: a jolt, then he rides it out and settles); the eyes flare with the energy.
        for (PursuitPath.Shot s : PursuitPath.SHOTS) {
            if (s.hits() != 0) continue;
            float d = tau - s.time();
            if (d < 0 || d > 10) continue;
            float j = (float) Math.exp(-d / 2.0f) * Math.min(1, d / .35f);
            int near = s.side() > 0 ? 1 : 0;
            switch (s.region()) {
                case PantherBody.CHEST -> p.add(CHEST_PITCH, -.38f * j).add(SPINE_PITCH, -.16f * j).add(HEAD_PITCH, -.2f * j).add(SHIFT_Z, 1.6f * j)
                        .armAdd(0, ARM_Z, .25f * j).armAdd(1, ARM_Z, .25f * j);
                case PantherBody.SHOULDER, PantherBody.UPPER_ARM -> p.add(CHEST_YAW, (near == 1 ? .38f : -.38f) * j).add(SPINE_ROLL, (near == 1 ? -.12f : .12f) * j)
                        .armAdd(near, ARM_Z, .3f * j).armAdd(near, SH_FWD, -1.2f * j).armAdd(near, ARM_X, .3f * j);
                case PantherBody.RIBS -> p.add(SPINE_ROLL, (near == 1 ? -.32f : .32f) * j).add(CHEST_YAW, .14f * j).add(SHIFT_X, (near == 1 ? -1.2f : 1.2f) * j);
                case PantherBody.SHIN, PantherBody.THIGH -> p.add(CROUCH, 2.2f * j).add(SPINE_PITCH, .12f * j).legAdd(near, LEG_X, .35f * j).legAdd(near, KNEE, .4f * j);
                default -> {}
            }
            p.add(EYES, .45f * j);
        }
        return p;
    }
    /**
     * Riding the SUV's roof: crouched low, feet wide, knees taking every bump, swaying into its swerves; he lifts his head
     * to the car ahead when the driver sees him in the mirror; when the shooting starts he raises his guard and lets it
     * come, the hits jolting him, then lowers it and looks ahead, ready to jump.
     */
    private static Pose onSuv(float tau, float time) {
        Pose p = riding();
        float lateral = (float) (PursuitPath.driveX(tau - 5) - 2 * PursuitPath.driveX(tau - 6) + PursuitPath.driveX(tau - 7));
        p.add(SPINE_ROLL, 2.2f * lateral).add(PELVIS_ROLL, -1.1f * lateral);
        p.add(CROUCH, .4f * (float) Math.sin(tau * 2.1)).add(SHIFT_X, .35f * (float) Math.sin(tau * .31)).add(CHEST_PITCH, -.015f * Mth.sin(time * .14f));
        for (int side = 0; side < 2; side++) p.armAdd(side, ARM_Z, .07f * (float) Math.sin(tau * .37 + side * 2));
        // Seen: the head comes up, the eyes on the car ahead.
        float seen = PursuitPath.ease((tau - ULT_BEHIND) / 8);
        p.add(HEAD_PITCH, -.25f * seen).add(SPINE_PITCH, -.08f * seen).add(EYES, .3f * seen);
        // The guard: the right forearm up across the face while the gunman fires, lowered after the last shot.
        float guard = PursuitPath.window(tau, ULT_LEAN + 2, PursuitPath.LAST_BACK_SHOT + 6, 4);
        p.arm(0, SH_FWD, Mth.lerp(guard, p.arm(0, SH_FWD), 1.1f)).arm(0, ARM_X, Mth.lerp(guard, p.arm(0, ARM_X), -1.55f))
                .arm(0, ARM_Z, Mth.lerp(guard, p.arm(0, ARM_Z), -.25f)).arm(0, ELBOW, Mth.lerp(guard, p.arm(0, ELBOW), 1.9f))
                .arm(0, WRIST_X, Mth.lerp(guard, p.arm(0, WRIST_X), .2f)).arm(0, CURL, Mth.lerp(guard, p.arm(0, CURL), .2f));
        p.add(HEAD_PITCH, .12f * guard).add(CHEST_YAW, .1f * guard);
        return p;
    }
    /** Low on the SUV's roof: the riding crouch. */
    static Pose riding() {
        Pose p = roofStance().copy().set(CROUCH, 6.5f).set(SPINE_PITCH, .5f).set(HEAD_PITCH, -.1f);
        for (int side = 0; side < 2; side++) p.arm(side, ARM_X, -.55f).arm(side, ARM_Z, .85f).arm(side, ELBOW, .5f).arm(side, CURL, .3f);
        return p;
    }

    /**
     * The driver (the player the film is aimed at): hands on the wheel, the body riding the road a beat behind the car.
     * The eyes go to the mirror; at every shot he flinches; the thing landing on the roof makes him duck and look up; he
     * shies away from the bullets coming up beside him and from the claws through the roof; with the roof gone he cowers,
     * one hand up, staring up at Panther, still steering; at the release he braces, and is thrown about with the car.
     */
    public static Pose driver(float tau, float time) {
        Pose p = seat();
        float wheel = steering(tau);
        float fear = PursuitPath.ease((tau - ULT_TOUCH) / 6);
        // Panic on the wheel once the thing is on his roof.
        wheel += fear * .25f * (float) Math.sin(tau * .41) * (tau < PursuitPath.BOOM ? 1 : 0);
        p.armAdd(0, ARM_X, -.45f * wheel).armAdd(1, ARM_X, .45f * wheel).armAdd(0, ELBOW, .2f * wheel).armAdd(1, ELBOW, -.2f * wheel);
        float bob = (float) Math.sin(tau * 3.7 - .9) * .5f + (float) Math.sin(tau * 2.4 - 1.4) * .5f;
        p.add(HEAD_PITCH, .015f * bob).add(SPINE_PITCH, .006f * bob);
        float lateral = (float) (PursuitPath.driveX(tau + 1) - 2 * PursuitPath.driveX(tau) + PursuitPath.driveX(tau - 1));
        p.add(SPINE_ROLL, -1.6f * lateral).add(HEAD_ROLL, 1.0f * lateral);
        p.add(CHEST_PITCH, -.015f * Mth.sin(time * .1f) * (1 + 2 * fear));
        // The mirror (up and to his right): first a glance, then he keeps looking back at it.
        float mirror = Math.max(PursuitPath.window(tau, ULT_GLANCE, ULT_BEHIND + 26, 3), .6f * PursuitPath.window(tau, ULT_RAISE + 2, ULT_COIL + 6, 4));
        p.add(HEAD_YAW, .5f * mirror).add(HEAD_PITCH, -.22f * mirror);
        // The shots behind him: each a flinch of the shoulders and head.
        for (PursuitPath.Shot s : PursuitPath.SHOTS) {
            float d = tau - s.time();
            if (d < 0 || d > 6) continue;
            float j = (float) Math.exp(-d / 1.3f) * Math.min(1, d / .3f);
            p.add(SPINE_PITCH, .1f * j).add(HEAD_PITCH, .08f * j);
            for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, .8f * j);
        }
        // The thud on the roof: a duck, the head snapping up to look.
        float thud = PursuitPath.spring(tau - ULT_TOUCH, 2.5f, .7f);
        float up = PursuitPath.window(tau, ULT_TOUCH + 2, ULT_ROOF_FIRE + 2, 4) + PursuitPath.window(tau, ULT_TEAR, PursuitPath.BOOM, 5);
        p.add(SPINE_PITCH, .25f * Math.max(0, thud)).add(HEAD_PITCH, -.65f * Math.min(1, up)).add(NECK, -.2f * Math.min(1, up));
        // Cowering from the bullets coming up beside him and from the claws: hunched forward and away (to his left, the door).
        float cower = Math.max(PursuitPath.window(tau, ULT_ROOF_FIRE, ULT_TURN, 3) * .6f, PursuitPath.window(tau, ULT_CLAW_R - 1, ULT_TEAR + 2, 2));
        p.add(SPINE_PITCH, .3f * cower).add(SPINE_ROLL, .25f * cower).add(HEAD_ROLL, -.15f * cower);
        // The roof gone: his right hand up over his head, still steering with the left, staring up.
        float shield = PursuitPath.window(tau, ULT_TEAR + 4, PursuitPath.BOOM, 5);
        p.arm(0, ARM_X, Mth.lerp(shield, p.arm(0, ARM_X), -2.5f)).arm(0, ARM_Z, Mth.lerp(shield, p.arm(0, ARM_Z), .2f))
                .arm(0, ELBOW, Mth.lerp(shield, p.arm(0, ELBOW), 1.6f)).arm(0, CURL, Mth.lerp(shield, p.arm(0, CURL), .3f))
                .arm(0, SH_FWD, Mth.lerp(shield, p.arm(0, SH_FWD), .4f));
        // The release: braced, arms out against the cabin, then thrown about.
        float d = tau - PursuitPath.BOOM;
        if (d >= 0) {
            float brace = PursuitPath.ease(d / 2);
            for (int side = 0; side < 2; side++) {
                p.arm(side, ARM_X, Mth.lerp(brace, p.arm(side, ARM_X), -2.2f + .5f * Mth.sin(d * .6f + side * 2)))
                        .arm(side, ARM_Z, Mth.lerp(brace, p.arm(side, ARM_Z), .9f)).arm(side, ELBOW, Mth.lerp(brace, p.arm(side, ELBOW), .5f))
                        .arm(side, CURL, Mth.lerp(brace, p.arm(side, CURL), .1f));
            }
            p.add(HEAD_PITCH, .3f * brace * Mth.sin(d * .5f)).add(SPINE_ROLL, .2f * brace * Mth.sin(d * .37f));
        }
        return p;
    }
    /** How far the wheel is turned (radians of the wheel / the hands), from the car's steering. */
    static float steering(float tau) {
        double xd = PursuitPath.driveX(tau + 1.5f) - PursuitPath.driveX(tau - 1.5f);
        return Mth.clamp((float) (xd * 2.2), -.9f, .9f);
    }
    /** On the roof between moves: leaning into the car's swerves, knees taking the road, the leg hits. */
    private static Pose balance(Pose base, float tau, float time) {
        Pose p = base.copy();
        float lateral = (float) (PursuitPath.driveX(tau + 1) - 2 * PursuitPath.driveX(tau) + PursuitPath.driveX(tau - 1));
        p.add(SPINE_ROLL, 2.4f * lateral).add(PELVIS_ROLL, -1.2f * lateral);
        p.add(CROUCH, .35f * (float) Math.sin(tau * 2.3)).add(SHIFT_X, .3f * (float) Math.sin(tau * .37));
        p.add(CHEST_PITCH, -.012f * Mth.sin(time * .14f));
        for (int side = 0; side < 2; side++) p.armAdd(side, ARM_Z, .06f * (float) Math.sin(tau * .41 + side * 2));
        return p;
    }
    /** The charge: the stance, the arms opening, a tremble that grows; the held breath at its height. */
    private static Pose charging(float tau, float time) {
        Pose p = charge.sample(tau);
        float shake = PursuitPath.ease((tau - (ULT_CHARGE + 10)) / 40);
        float n = tau * 4.1f;
        p.add(CHEST_PITCH, .02f * shake * Mth.sin(n)).add(HEAD_ROLL, .025f * shake * Mth.sin(n * 1.3f + 1));
        for (int side = 0; side < 2; side++) p.armAdd(side, WRIST_X, .07f * shake * Mth.sin(n * 1.7f + side)).armAdd(side, CURL, .06f * shake * Mth.sin(n * 2.3f + side));
        p.add(CHEST_PITCH, -.02f * Mth.sin(time * .2f));
        return p;
    }
    /** Down on the road: the impact into the three-point landing, the skid, held, breathing; then he rises, looks off, the claws go in. */
    private static Pose landed(float d, float time) {
        Pose p = threePoint();
        float since = d - PursuitPath.LAND;
        float hit = PursuitPath.spring(since, 2.2f, .8f);
        p.add(SPINE_PITCH, .15f * hit).add(HEAD_PITCH, .1f * hit);
        // Skidding: leaning back against it, the hand and claws dragging.
        float skid = PursuitPath.window(since, 0, PursuitPath.SKID, 1.5f);
        p.add(SPINE_PITCH, -.2f * skid).armAdd(0, ARM_X, .15f * skid);
        float breath = Mth.sin(time * .16f);
        p.add(SPINE_PITCH, .02f * breath).add(CHEST_PITCH, -.02f * breath);
        float t = PursuitPath.real(PursuitPath.BOOM + d);
        float rise = PursuitPath.ease((t - RISE) / 22);
        if (rise > 0) {
            Pose r = ready(time);
            p.toward(r, rise);
        }
        // He looks off across the road, a little away from us.
        float look = PursuitPath.ease((t - (RISE + 22)) / 10);
        p.add(HEAD_YAW, .55f * look).add(CHEST_YAW, .18f * look).add(SPINE_YAW, .05f * look);
        p.set(EYES, Mth.lerp(PursuitPath.ease((t - (RISE + 30)) / 18), p.get(EYES), .15f));
        // The half twist in the air left him facing down the road: keep it (the body's own facing is the other way).
        p.set(ROOT_YAW, Mth.PI).set(ROOT_PITCH, 0);
        return p;
    }
    /** Film times of the end: he rises; the claws go in; the energy leaves the suit. */
    public static final float RISE = 694, CLAWS_IN = 726, DRAIN = 722;
    /** How far out the claws are (1 out, 0 in). */
    public static float claws(float tau) {
        if (tau < PursuitPath.BOOM) return 1;
        float t = PursuitPath.real(tau);
        return 1 - PursuitPath.ease((t - CLAWS_IN) / 8);
    }

    // ------------------------------------------------------------------ the gunman
    /** The gunman (left-handed: the gun hand is the outer one when he leans out of the left window), braced in the back. */
    static Pose gunSeat() {
        Pose p = new Pose();
        p.set(PLANT, 0).set(SPINE_PITCH, .06f).set(HEAD_PITCH, .04f).set(NECK, .3f);
        for (int side = 0; side < 2; side++) p.leg(side, LEG_X, -1.42f).leg(side, KNEE, 1.38f).leg(side, LEG_Z, .12f).leg(side, ANKLE, -.05f);
        p.arm(1, ARM_X, -.55f).arm(1, ARM_Z, -.05f).arm(1, ELBOW, 1.1f).arm(1, WRIST_X, -.3f).arm(1, CURL, .9f);
        p.arm(0, ARM_X, -.8f).arm(0, ARM_Z, .1f).arm(0, ELBOW, .7f).arm(0, CURL, .7f);
        return p;
    }
    private static Track gunTrack;
    /** Leaning out of the rear left window, turned to face back, the gun hand out along the car at the SUV behind. */
    static Pose gunLean() {
        Pose lean = gunSeat().copy().set(PELVIS_ROLL, .1f).set(SPINE_ROLL, .28f).set(CHEST_ROLL, .12f).set(SPINE_YAW, -.45f).set(CHEST_YAW, -.75f)
                .set(HEAD_YAW, -.55f).set(HEAD_ROLL, -.3f).set(HEAD_PITCH, .05f);
        lean.arm(1, SH_FWD, LEAN_ARM[6]).arm(1, ARM_X, LEAN_ARM[0]).arm(1, ARM_Y, LEAN_ARM[1]).arm(1, ARM_Z, LEAN_ARM[2]).arm(1, ELBOW, LEAN_ARM[5])
                .arm(1, WRIST_X, LEAN_ARM[3]).arm(1, WRIST_Z, LEAN_ARM[4]).arm(1, CURL, .9f);
        lean.arm(0, ARM_X, -.9f).arm(0, ARM_Z, -.2f).arm(0, ELBOW, 1.4f).arm(0, CURL, .9f);
        return lean;
    }
    /** The gun arm of the lean (arm X, Y, Z, wrist X, Z, elbow, shoulder forward): found so the barrel points back along the car at the SUV. */
    static final float[] LEAN_ARM = {-1.756f, .026f, -.4f, -.9f, .755f, 1.2f, -.5f};
    private static void buildGun() {
        if (gunTrack != null) return;
        Pose seat = gunSeat();
        Pose rack = seat.copy().set(HEAD_PITCH, .4f).set(SPINE_PITCH, .15f);
        rack.arm(1, ARM_X, -1.0f).arm(1, ELBOW, 1.5f).arm(1, ARM_Z, -.2f);
        rack.arm(0, ARM_X, -1.0f).arm(0, ARM_Z, -.35f).arm(0, ELBOW, 1.5f).arm(0, CURL, .8f);
        Pose racked = rack.copy();
        racked.arm(0, ELBOW, 1.95f).arm(0, ARM_X, -.8f);
        Pose ready = rack.copy().set(HEAD_PITCH, .02f);
        // Looking back over his left shoulder through the rear window at the SUV.
        Pose look = ready.copy().set(CHEST_YAW, -.5f).set(SPINE_YAW, -.15f).set(HEAD_YAW, -1.25f).set(HEAD_PITCH, -.05f);
        Pose raise = look.copy();
        raise.arm(1, ARM_X, -1.9f).arm(1, ARM_Z, .1f).arm(1, ELBOW, 1.9f).arm(1, WRIST_X, -.4f);
        // The butt of the gun driven out through the rear side window.
        Pose wind = raise.copy().set(CHEST_YAW, .1f).set(HEAD_YAW, -.9f);
        wind.arm(1, ARM_X, -.9f).arm(1, ARM_Z, -.3f).arm(1, ELBOW, 2.1f);
        Pose smash = wind.copy().set(CHEST_YAW, -.55f).set(SPINE_ROLL, .3f);
        smash.arm(1, ARM_X, -.7f).arm(1, ARM_Z, 1.45f).arm(1, ELBOW, .25f);
        Pose lean = gunLean();
        Pose ducked = seat.copy().set(SPINE_PITCH, .35f).set(HEAD_PITCH, .3f);
        Pose up = seat.copy().set(HEAD_PITCH, -.95f).set(SPINE_PITCH, -.05f).set(NECK, -.2f);
        up.arm(1, ARM_X, -1.6f).arm(1, ARM_Z, 0).arm(1, ELBOW, .4f).arm(1, WRIST_X, -1.2f);
        up.arm(0, ARM_X, -1.5f).arm(0, ARM_Z, -.3f).arm(0, ELBOW, .9f).arm(0, CURL, .8f);
        Pose cower = up.copy().set(SPINE_PITCH, .45f).set(HEAD_PITCH, -.7f);
        cower.arm(1, ARM_X, -1.4f).arm(1, ELBOW, .7f);
        Pose exposed = up.copy().set(HEAD_PITCH, -1.1f).set(SPINE_PITCH, -.15f);
        exposed.arm(1, ARM_X, -2.2f).arm(1, ELBOW, .3f).arm(1, WRIST_X, -.6f);
        Pose hauled = exposed.copy().set(HEAD_PITCH, -.8f);
        for (int side = 0; side < 2; side++) {
            hauled.arm(side, ARM_X, -2.8f).arm(side, ARM_Z, .6f).arm(side, ELBOW, .5f).arm(side, CURL, .3f);
            hauled.leg(side, LEG_X, -.6f).leg(side, KNEE, .6f);
        }
        float last = PursuitPath.LAST_BACK_SHOT;
        gunTrack = new Track(true).key(ULT_NPC + 2, seat).key(ULT_NPC + 6, rack).key(ULT_NPC + 9, racked).key(ULT_NPC + 12, rack).key(ULT_GLANCE, ready)
                .key(ULT_BEHIND + 3, look).key(ULT_RAISE - 2, look.copy().set(HEAD_YAW, -1.15f)).key(ULT_RAISE + 2, raise)
                .key(ULT_SMASH - 2, wind).key(ULT_SMASH, smash).key(ULT_LEAN + 1, smash.copy().set(SPINE_ROLL, .45f))
                .key(ULT_FIRE - 2, lean).key(last + 4, lean).key(last + 11, ducked).key(ULT_TOUCH, ducked)
                .key(ULT_TOUCH + 4, up.copy().set(HEAD_PITCH, -.75f).set(HEAD_YAW, .2f)).key(ULT_ROOF_FIRE - 2, up)
                .key(ULT_ROOF_FIRE + 18, up.copy().set(HEAD_YAW, .3f)).key(ULT_ROOF_FIRE + 36, up)
                .key(ULT_TURN + 6, cower).key(ULT_TEAR, cower.copy().set(HEAD_PITCH, -.9f)).key(ULT_ROOF_FREE + 2, exposed)
                .key(ULT_THROW - 3, exposed.copy().set(HEAD_PITCH, -1.15f)).key(ULT_THROW + 2, hauled);
    }
    /** The gunman's pose at scene time tau: bracing in the back, the slide racked, the window smashed, leaning out and firing; up at the roof; hauled out; flung. */
    public static Pose gunman(float tau) {
        buildGun();
        Pose p = tau < ULT_NPC + 2 ? gunSeat() : gunTrack.sample(Math.min(tau, PursuitPath.THROWN));
        if (tau < PursuitPath.THROWN) {
            // Thrown about by the car: rolling into its swerves a beat late.
            float lateral = (float) (PursuitPath.driveX(tau) - 2 * PursuitPath.driveX(tau - 1) + PursuitPath.driveX(tau - 2));
            p.add(SPINE_ROLL, -2.6f * lateral).add(HEAD_ROLL, 1.2f * lateral);
            float bob = (float) Math.sin(tau * 3.7 - 1.3);
            p.add(HEAD_PITCH, .02f * bob);
            // Each shot kicks the gun up and the arm back.
            for (PursuitPath.Shot s : PursuitPath.SHOTS) {
                float d = tau - s.time();
                if (d < 0 || d > 5) continue;
                float r = (float) Math.exp(-d / 1.1f);
                p.armAdd(1, ARM_X, -.28f * r).armAdd(1, WRIST_X, -.5f * r).armAdd(1, ELBOW, .2f * r).armAdd(1, SH_FWD, -.6f * r).add(CHEST_PITCH, -.05f * r);
            }
            // The claws going through the roof over his head make him flinch.
            for (float hit : new float[]{ULT_CLAW_R, ULT_CLAW_L}) {
                float d = tau - hit;
                if (d >= 0 && d < 6) p.add(SPINE_PITCH, .2f * (float) Math.exp(-d / 1.5f)).add(HEAD_PITCH, .2f * (float) Math.exp(-d / 1.5f));
            }
            return p;
        }
        // Flung: tumbling over backwards, arms and legs flailing.
        float s = tau - PursuitPath.THROWN;
        p.set(ROOT_PITCH, -.22f * s).set(ROOT_ROLL, .05f * s);
        for (int side = 0; side < 2; side++) {
            float f = Mth.sin(s * .8f + side * 2.1f);
            p.arm(side, ARM_X, -2f + f).arm(side, ARM_Z, .8f + .3f * f).arm(side, ELBOW, .6f + .4f * f);
            p.leg(side, LEG_X, -.8f + .6f * Mth.sin(s * .7f + side * Mth.PI)).leg(side, KNEE, .9f + .5f * f);
        }
        return p;
    }
}
