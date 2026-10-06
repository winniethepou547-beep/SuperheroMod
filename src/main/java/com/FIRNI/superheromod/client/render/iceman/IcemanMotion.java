package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import net.minecraft.util.Mth;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Iceman's body as numbers: his stance and the moves, as pure functions of the action's clock (PantherMotion's pose and
 * key-pose tracks; signs at the top of PantherMotion: positive pitch leans forward, positive yaw turns his front to his
 * right, positive roll leans him left, arm X negative raises the arm forward, arm Z positive takes it out to the side,
 * knee and elbow positive bend, curl 0 open hand .. 1 fist). The walk, the air and the landings are BatmanMotion's.
 * The moves live with their effects: the brush and the slides in IcemanMoveMotion, the weapons in IcemanWeaponMotion,
 * the shell and shattered ground in IcemanShellMotion.
 */
public final class IcemanMotion {
    private IcemanMotion() {}

    private static float noise(float x) { return Mth.sin(x * 1.7f) * .5f + Mth.sin(x * 3.1f + 1.3f) * .3f + Mth.sin(x * 7.3f + 4.1f) * .2f; }

    /**
     * What a move needs besides its clock: the free clock (ticks), the weapon picked, the combo's swing, the hold's
     * charge (0..1), whether the weapon is in hand, the brush on a body (true) or sculpting (false), the look's pitch
     * (radians, positive down: the slide climbing or diving), how fast his way is turning (radians a tick, positive to
     * his right: the slide banks into it), his speed (blocks a tick), the entity.
     */
    public record Ctx(float time, int weapon, int combo, float charge, boolean weaponOut, boolean brushBody, float lookPitch, float turn, float speed, int entity) {}

    /**
     * Standing: easy and confident, light on his feet, the chest open, the hands loose with the fingers a little apart
     * (the cold always about them); breathing, the weight drifting.
     */
    public static Pose stance(float time) {
        Pose p = new Pose();
        p.set(PLANT, 1).set(SPINE_PITCH, .03f).set(CHEST_PITCH, -.04f).set(HEAD_PITCH, .02f).set(NECK, .25f).set(PELVIS_YAW, -.05f).set(CHEST_YAW, .04f);
        p.leg(0, LEG_Z, .06f).leg(0, LEG_Y, .1f).leg(0, KNEE, .04f);
        p.leg(1, LEG_X, -.1f).leg(1, LEG_Z, .08f).leg(1, LEG_Y, .16f).leg(1, KNEE, .1f);
        for (int side = 0; side < 2; side++)
            p.arm(side, SH_FWD, .2f).arm(side, ARM_X, -.05f).arm(side, ARM_Z, .16f).arm(side, ELBOW, .22f).arm(side, CURL, .38f).arm(side, WRIST_X, -.04f);
        float breath = Mth.sin(time * .08f);
        p.add(CHEST_PITCH, -.02f * breath).add(SPINE_PITCH, -.006f * breath);
        for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, .12f * breath).armAdd(side, ARM_Z, .01f * breath);
        float drift = noise(time * .016f);
        p.add(SHIFT_X, .15f * drift).add(PELVIS_ROLL, -.012f * drift);
        p.add(HEAD_YAW, .04f * noise(time * .02f + 3)).add(HEAD_PITCH, .015f * noise(time * .028f + 6));
        // The fingers never quite still (flexing with the cold).
        for (int side = 0; side < 2; side++) p.armAdd(side, CURL, .1f * noise(time * .06f + side * 2.3f));
        return p;
    }
    /** The weapon in hand: the right arm a little forward, carrying it ready. */
    public static void armed(Pose p, int weapon) {
        switch (weapon) {
            case W_MACE -> p.arm(0, ARM_X, -.25f).arm(0, ARM_Z, .22f).arm(0, ELBOW, .6f).arm(0, CURL, 1).arm(0, WRIST_X, .35f);
            case W_SPEAR -> p.arm(0, ARM_X, -.35f).arm(0, ARM_Z, .12f).arm(0, ELBOW, .9f).arm(0, CURL, 1).arm(0, WRIST_X, .9f).arm(0, SH_FWD, .4f);
            default -> p.arm(0, ARM_X, -.3f).arm(0, ARM_Z, .2f).arm(0, ELBOW, .7f).arm(0, CURL, 1).arm(0, WRIST_X, .5f);
        }
    }

    /** A move's pose at its clock t over the base (the stance with the walk under it). */
    public static Pose sample(int action, float t, Pose base, Ctx c) {
        Pose out = switch (action) {
            case BRUSH, SLIDE, DASH -> IcemanMoveMotion.sample(action, t, base, c);
            case FORM, STRIKE, CHARGE, RELEASE -> IcemanWeaponMotion.sample(action, t, base, c);
            case SHELL_FORM, SHELL, SHELL_BREAK, SHELL_BURST, GROUND -> IcemanShellMotion.sample(action, t, base, c);
            case WHEEL -> wheel(base, c.time());
            default -> null;
        };
        return out == null ? base.copy() : out;
    }
    /** The wheel open: the right hand raised a little before him, fingers open, looking at it. */
    static Pose wheel(Pose base, float time) {
        Pose p = base.copy();
        p.arm(0, ARM_X, -.9f).arm(0, ARM_Y, -.3f).arm(0, ARM_Z, .1f).arm(0, ELBOW, 1.3f).arm(0, CURL, .2f + .1f * Mth.sin(time * .2f)).arm(0, WRIST_X, -.2f);
        p.add(HEAD_PITCH, .15f).add(HEAD_YAW, -.12f);
        return p;
    }
    static float k(float t, float a, float b) { return PantherMotion.k(t, a, b); }
}
