package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Iceman's body with an ice weapon (the Ice Armory), as pure functions of the action's clock: the weapon forming in his
 * raised hand, the three weapons' swings (right to left, left to right, then each weapon's finisher), the holds (the mace
 * carried two-handed over his shoulder and braced lower as it grows, the spear drawn back for the throw with the front
 * arm pointing at the target, the sword turned point down and driven into the ground while he drops to one knee over
 * it) and the releases (the giant slam, the throw, the planted sword cracking and shattering under his hands, he rises).
 * <p>
 * Where both hands must stay on one weapon the arms are solved every frame (IcemanWeaponReach): the spear's flurry is
 * a two-handed stance (the rear hand drives each thrust along the shaft's line, the front hand guides it and the shaft
 * slides through it), and both hands grip the planted sword's hilt where the server planted it (the body turns to face
 * it).
 * <p>
 * Every key pose is a list of (joint, value) pairs set over the stance. The arm angles were solved offline (forward
 * kinematics of IcemanBody's arm, fitted so the fist and the weapon's long axis land where each moment needs them: the
 * mace's head sweeping across in front of him, the spear's point along the thrust, the sword's edge along the cut), so
 * the weapon really travels the arc the hit is placed on. The mace (heavy) winds up long and follows through with its
 * weight; the spear (fast) stabs; the sword (between) cuts. A press goes straight into a short ready wind-up (CHARGE
 * under HOLD_TICKS) that the swing starts from, so a tap never hitches.
 */
public final class IcemanWeaponMotion {
    private IcemanWeaponMotion() {}

    // ------------------------------------------------------------------ key poses (arm angles solved for the weapon's place)
    private static final float[] M_READY0 = {CROUCH, 1.2f, CHEST_YAW, .2f, SPINE_YAW, .05f, R + ARM_X, .122f, R + ARM_Y, .02f, R + ARM_Z, .085f, R + ELBOW, 1.777f, R + WRIST_X, .691f, R + WRIST_Z, -.025f, L + ARM_X, -.55f, L + ARM_Y, -.2f, L + ARM_Z, .3f, L + ELBOW, 1.3f, L + WRIST_X, 0f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] M_WIND0 = {CROUCH, 1.9f, CHEST_YAW, .75f, SPINE_YAW, .22f, PELVIS_YAW, .25f, SHIFT_Z, .9f, SPINE_PITCH, -.04f, HEAD_YAW, -.35f, RL + LEG_X, .15f, RL + KNEE, .35f, LL + LEG_X, -.3f, R + ARM_X, -1.342f, R + ARM_Y, -.485f, R + ARM_Z, -.878f, R + ELBOW, .769f, R + WRIST_X, .733f, R + WRIST_Z, -.038f, L + ARM_X, -1f, L + ARM_Y, -.5f, L + ARM_Z, .1f, L + ELBOW, 1.1f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] M_HIT0 = {CROUCH, 2.5f, CHEST_YAW, -.45f, SPINE_YAW, -.15f, PELVIS_YAW, -.2f, SHIFT_Z, -1.3f, SPINE_PITCH, .2f, HEAD_YAW, .3f, LL + LEG_X, -.4f, LL + KNEE, .45f, RL + LEG_X, .3f, RL + ANKLE, .3f, R + ARM_X, .519f, R + ARM_Y, .66f, R + ARM_Z, .19f, R + ELBOW, 2.427f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.2f, L + ARM_X, -.3f, L + ARM_Y, .2f, L + ARM_Z, .7f, L + ELBOW, .8f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] M_THRU0 = {CROUCH, 2.3f, CHEST_YAW, -.85f, SPINE_YAW, -.25f, PELVIS_YAW, -.3f, SHIFT_Z, -1f, SPINE_PITCH, .22f, HEAD_YAW, .4f, LL + LEG_X, -.4f, LL + KNEE, .45f, RL + LEG_X, .3f, RL + ANKLE, .3f, R + ARM_X, .027f, R + ARM_Y, .015f, R + ARM_Z, -.079f, R + ELBOW, 1.63f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.185f, L + ARM_X, .2f, L + ARM_Y, 0f, L + ARM_Z, .9f, L + ELBOW, .6f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] M_READY1 = {CROUCH, 1.2f, CHEST_YAW, -.2f, SPINE_YAW, -.05f, R + ARM_X, .443f, R + ARM_Y, -.167f, R + ARM_Z, .093f, R + ELBOW, 2.163f, R + WRIST_X, .852f, R + WRIST_Z, -.08f, L + ARM_X, -.55f, L + ARM_Y, -.2f, L + ARM_Z, .3f, L + ELBOW, 1.3f, L + WRIST_X, 0f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] M_WIND1 = {CROUCH, 1.9f, CHEST_YAW, -.75f, SPINE_YAW, -.22f, PELVIS_YAW, -.2f, SHIFT_Z, .7f, HEAD_YAW, .35f, LL + LEG_X, -.25f, LL + KNEE, .3f, RL + LEG_X, .1f, R + ARM_X, -1.646f, R + ARM_Y, 1.158f, R + ARM_Z, 1.135f, R + ELBOW, 1.746f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.2f, L + ARM_X, -.2f, L + ARM_Y, 0f, L + ARM_Z, .6f, L + ELBOW, .9f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] M_HIT1 = {CROUCH, 2.4f, CHEST_YAW, .4f, SPINE_YAW, .12f, PELVIS_YAW, .15f, SHIFT_Z, -1.1f, SPINE_PITCH, .18f, HEAD_YAW, -.25f, LL + LEG_X, -.35f, LL + KNEE, .4f, RL + LEG_X, .28f, RL + ANKLE, .3f, R + ARM_X, -1.325f, R + ARM_Y, -.284f, R + ARM_Z, .702f, R + ELBOW, .822f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.18f, L + ARM_X, .3f, L + ARM_Y, 0f, L + ARM_Z, .5f, L + ELBOW, .5f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] M_THRU1 = {CROUCH, 2.2f, CHEST_YAW, .8f, SPINE_YAW, .22f, PELVIS_YAW, .25f, SHIFT_Z, -.8f, SPINE_PITCH, .2f, HEAD_YAW, -.35f, LL + LEG_X, -.35f, LL + KNEE, .4f, RL + LEG_X, .28f, RL + ANKLE, .3f, R + ARM_X, -1.234f, R + ARM_Y, .224f, R + ARM_Z, -1f, R + ELBOW, .036f, R + WRIST_X, .925f, R + WRIST_Z, -.403f, L + ARM_X, -.6f, L + ARM_Y, -.3f, L + ARM_Z, .2f, L + ELBOW, 1.2f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] M_LIFT2 = {CROUCH, 2f, CHEST_YAW, .1f, SPINE_PITCH, .05f, LL + LEG_X, -.2f, R + ARM_X, -.51f, R + ARM_Y, -.366f, R + ARM_Z, -.049f, R + ELBOW, 2.282f, R + WRIST_X, 1.361f, R + WRIST_Z, -.11f, L + ARM_X, -.458f, L + ARM_Y, -.964f, L + ARM_Z, -.195f, L + ELBOW, .87f, L + WRIST_X, -.008f, L + WRIST_Z, -.133f, R + CURL, 1f, L + CURL, 1f};
    private static final float[] M_RAISE2 = {CROUCH, .2f, LIFT, .9f, SPINE_PITCH, -.22f, CHEST_PITCH, -.2f, HEAD_PITCH, -.2f, SHIFT_Z, .5f, LL + LEG_X, -.3f, LL + ANKLE, .3f, RL + ANKLE, .4f, R + ARM_X, -2.324f, R + ARM_Y, -.243f, R + ARM_Z, .243f, R + ELBOW, .157f, R + WRIST_X, .143f, R + WRIST_Z, .007f, L + ARM_X, -2.027f, L + ARM_Y, -.415f, L + ARM_Z, .375f, L + ELBOW, .038f, L + WRIST_X, -.237f, L + WRIST_Z, .011f, R + CURL, 1f, L + CURL, 1f};
    private static final float[] M_SLAM2 = {CROUCH, 5.2f, SPINE_PITCH, .5f, CHEST_PITCH, .28f, HEAD_PITCH, -.35f, SHIFT_Z, -2.2f, LL + LEG_X, -.55f, LL + KNEE, .7f, RL + LEG_X, .45f, RL + KNEE, .4f, RL + ANKLE, .35f, R + ARM_X, .136f, R + ARM_Y, -.157f, R + ARM_Z, -.722f, R + ELBOW, 2.09f, R + WRIST_X, 1.75f, R + WRIST_Z, -.912f, L + ARM_X, .9f, L + ARM_Y, -.237f, L + ARM_Z, -.88f, L + ELBOW, 1.781f, L + WRIST_X, .734f, L + WRIST_Z, -1.2f, R + CURL, 1f, L + CURL, 1f};
    private static final float[] M_RISE2 = {CROUCH, 3f, SPINE_PITCH, .25f, CHEST_PITCH, .1f, HEAD_PITCH, -.1f, SHIFT_Z, -1f, LL + LEG_X, -.35f, LL + KNEE, .4f, RL + LEG_X, .25f, R + ARM_X, .294f, R + ARM_Y, .041f, R + ARM_Z, -.145f, R + ELBOW, 2.15f, R + WRIST_X, 1.305f, R + WRIST_Z, -.339f, L + ARM_X, -.3f, L + ARM_Y, 0f, L + ARM_Z, .35f, L + ELBOW, .6f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] M_SHOULDER0 = {CROUCH, 1.6f, CHEST_YAW, .3f, SPINE_YAW, .1f, PELVIS_YAW, .1f, HEAD_YAW, -.15f, LL + LEG_X, -.2f, LL + LEG_Z, .15f, RL + LEG_Z, .12f, R + ARM_X, -.697f, R + ARM_Y, -.367f, R + ARM_Z, -.476f, R + ELBOW, 1.857f, R + WRIST_X, .582f, R + WRIST_Z, -.009f, L + ARM_X, -1.429f, L + ARM_Y, -.528f, L + ARM_Z, .855f, L + ELBOW, .897f, L + WRIST_X, .322f, L + WRIST_Z, -.075f, R + CURL, 1f, L + CURL, 1f};
    private static final float[] M_SHOULDER1 = {CROUCH, 4.6f, CHEST_YAW, .4f, SPINE_YAW, .12f, PELVIS_YAW, .15f, SPINE_PITCH, .25f, CHEST_PITCH, .1f, HEAD_PITCH, -.25f, HEAD_YAW, -.2f, LL + LEG_X, -.3f, LL + LEG_Z, .3f, RL + LEG_Z, .28f, LL + KNEE, .3f, RL + KNEE, .3f, R + ARM_X, -.094f, R + ARM_Y, -.493f, R + ARM_Z, -.812f, R + ELBOW, 2.44f, R + WRIST_X, .286f, R + WRIST_Z, .139f, L + ARM_X, -1.461f, L + ARM_Y, -.23f, L + ARM_Z, 1.322f, L + ELBOW, 1.582f, L + WRIST_X, .864f, L + WRIST_Z, -.1f, R + CURL, 1f, L + CURL, 1f};
    private static final float[] M_HEAVE = {CROUCH, .5f, LIFT, 1.2f, SPINE_PITCH, -.3f, CHEST_PITCH, -.2f, HEAD_PITCH, -.3f, SHIFT_Z, .6f, LL + LEG_X, -.3f, LL + ANKLE, .35f, RL + ANKLE, .45f, R + ARM_X, -2.145f, R + ARM_Y, -.376f, R + ARM_Z, -.079f, R + ELBOW, .019f, R + WRIST_X, .671f, R + WRIST_Z, .022f, L + ARM_X, -1.524f, L + ARM_Y, -.663f, L + ARM_Z, -.002f, L + ELBOW, .338f, L + WRIST_X, .38f, L + WRIST_Z, -.07f, R + CURL, 1f, L + CURL, 1f};
    private static final float[] M_BIG_SMALL = {CROUCH, 5.6f, SPINE_PITCH, .55f, CHEST_PITCH, .3f, HEAD_PITCH, -.4f, SHIFT_Z, -2.4f, LL + LEG_X, -.6f, LL + KNEE, .75f, RL + LEG_X, .5f, RL + KNEE, .45f, RL + ANKLE, .4f, R + ARM_X, .072f, R + ARM_Y, -.22f, R + ARM_Z, -.704f, R + ELBOW, 2.099f, R + WRIST_X, 1.75f, R + WRIST_Z, -.782f, L + ARM_X, .9f, L + ARM_Y, -.297f, L + ARM_Z, -.865f, L + ELBOW, 1.848f, L + WRIST_X, .739f, L + WRIST_Z, -.972f, R + CURL, 1f, L + CURL, 1f};
    private static final float[] M_BIG_FULL = {CROUCH, 5.6f, SPINE_PITCH, .55f, CHEST_PITCH, .3f, HEAD_PITCH, -.4f, SHIFT_Z, -2.4f, LL + LEG_X, -.6f, LL + KNEE, .75f, RL + LEG_X, .5f, RL + KNEE, .45f, RL + ANKLE, .4f, R + ARM_X, .021f, R + ARM_Y, -.549f, R + ARM_Z, -.564f, R + ELBOW, 2.31f, R + WRIST_X, 1.577f, R + WRIST_Z, -.415f, L + ARM_X, .766f, L + ARM_Y, -.674f, L + ARM_Z, -.744f, L + ELBOW, 1.765f, L + WRIST_X, .42f, L + WRIST_Z, -.487f, R + CURL, 1f, L + CURL, 1f};
    private static final float[] S_READY0 = {CROUCH, 1.4f, CHEST_YAW, .25f, SPINE_YAW, .05f, R + ARM_X, .252f, R + ARM_Y, -.128f, R + ARM_Z, .315f, R + ELBOW, 1.909f, R + WRIST_X, 1.499f, R + WRIST_Z, -.011f, L + ARM_X, -.7f, L + ARM_Y, -.25f, L + ARM_Z, .3f, L + ELBOW, 1.4f, L + WRIST_X, 0f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_COCK0 = {CROUCH, 1.8f, CHEST_YAW, .5f, SPINE_YAW, .12f, PELVIS_YAW, .15f, SHIFT_Z, .6f, LL + LEG_X, -.25f, R + ARM_X, .071f, R + ARM_Y, -.406f, R + ARM_Z, .676f, R + ELBOW, 2.046f, R + WRIST_X, 1.592f, R + WRIST_Z, .067f, L + ARM_X, -.7f, L + ARM_Y, -.25f, L + ARM_Z, .3f, L + ELBOW, 1.4f, L + WRIST_X, 0f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_HIT0 = {CROUCH, 2.2f, CHEST_YAW, -.35f, SPINE_YAW, -.1f, PELVIS_YAW, -.1f, SHIFT_Z, -1.4f, SPINE_PITCH, .14f, HEAD_YAW, .2f, LL + LEG_X, -.45f, LL + KNEE, .5f, RL + LEG_X, .32f, RL + ANKLE, .35f, R + ARM_X, -.186f, R + ARM_Y, .373f, R + ARM_Z, .235f, R + ELBOW, 1.962f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.2f, L + ARM_X, -.2f, L + ARM_Y, .1f, L + ARM_Z, .55f, L + ELBOW, .9f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_THRU0 = {CROUCH, 2f, CHEST_YAW, -.5f, SPINE_YAW, -.14f, PELVIS_YAW, -.15f, SHIFT_Z, -1.1f, SPINE_PITCH, .14f, HEAD_YAW, .25f, LL + LEG_X, -.45f, LL + KNEE, .5f, RL + LEG_X, .32f, RL + ANKLE, .35f, R + ARM_X, .168f, R + ARM_Y, .289f, R + ARM_Z, .224f, R + ELBOW, 2.163f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.2f, L + ARM_X, -.1f, L + ARM_Y, .1f, L + ARM_Z, .6f, L + ELBOW, .8f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_READY1 = {CROUCH, 1.4f, CHEST_YAW, -.05f, R + ARM_X, .59f, R + ARM_Y, .02f, R + ARM_Z, .052f, R + ELBOW, 2.225f, R + WRIST_X, 1.528f, R + WRIST_Z, -.08f, L + ARM_X, -.7f, L + ARM_Y, -.25f, L + ARM_Z, .3f, L + ELBOW, 1.4f, L + WRIST_X, 0f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_COCK1 = {CROUCH, 1.8f, CHEST_YAW, -.4f, SPINE_YAW, -.1f, PELVIS_YAW, -.1f, SHIFT_Z, .5f, LL + LEG_X, -.25f, R + ARM_X, .9f, R + ARM_Y, .211f, R + ARM_Z, -.943f, R + ELBOW, 1.878f, R + WRIST_X, .733f, R + WRIST_Z, 1.2f, L + ARM_X, -.7f, L + ARM_Y, -.25f, L + ARM_Z, .3f, L + ELBOW, 1.4f, L + WRIST_X, 0f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_HIT1 = {CROUCH, 2.2f, CHEST_YAW, .32f, SPINE_YAW, .1f, PELVIS_YAW, .1f, SHIFT_Z, -1.3f, SPINE_PITCH, .14f, HEAD_YAW, -.2f, LL + LEG_X, -.45f, LL + KNEE, .5f, RL + LEG_X, .32f, RL + ANKLE, .35f, R + ARM_X, -1.388f, R + ARM_Y, -.25f, R + ARM_Z, -.208f, R + ELBOW, .305f, R + WRIST_X, 1.616f, R + WRIST_Z, -.172f, L + ARM_X, -.5f, L + ARM_Y, -.2f, L + ARM_Z, .4f, L + ELBOW, 1.3f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_THRU1 = {CROUCH, 2f, CHEST_YAW, .48f, SPINE_YAW, .14f, PELVIS_YAW, .15f, SHIFT_Z, -1f, SPINE_PITCH, .14f, HEAD_YAW, -.25f, LL + LEG_X, -.45f, LL + KNEE, .5f, RL + LEG_X, .32f, RL + ANKLE, .35f, R + ARM_X, -1.377f, R + ARM_Y, -.184f, R + ARM_Z, -.374f, R + ELBOW, .051f, R + WRIST_X, 1.375f, R + WRIST_Z, -.105f, L + ARM_X, -.5f, L + ARM_Y, -.2f, L + ARM_Z, .4f, L + ELBOW, 1.3f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_GATHER = {CROUCH, 3.2f, CHEST_YAW, .45f, SPINE_YAW, .12f, PELVIS_YAW, .15f, SPINE_PITCH, .12f, SHIFT_Z, .6f, HEAD_YAW, -.2f, LL + LEG_X, -.4f, LL + KNEE, .4f, LL + LEG_Z, .12f, RL + LEG_X, .3f, RL + KNEE, .3f, RL + LEG_Z, .12f, RL + ANKLE, .25f, R + ARM_X, .604f, R + ARM_Y, -.413f, R + ARM_Z, .731f, R + ELBOW, 2.321f, R + WRIST_X, 1.167f, R + WRIST_Z, .027f, L + ARM_X, -1.1f, L + ARM_Y, -.3f, L + ARM_Z, .25f, L + ELBOW, .7f, L + WRIST_X, -.2f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_BACK0 = {CROUCH, 3f, CHEST_YAW, .32f, SPINE_YAW, .08f, PELVIS_YAW, .1f, SPINE_PITCH, .1f, SHIFT_Z, .2f, HEAD_YAW, -.12f, LL + LEG_X, -.4f, LL + KNEE, .4f, LL + LEG_Z, .12f, RL + LEG_X, .3f, RL + KNEE, .3f, RL + LEG_Z, .12f, RL + ANKLE, .25f, R + ARM_X, .512f, R + ARM_Y, -.428f, R + ARM_Z, .48f, R + ELBOW, 2.129f, R + WRIST_X, 1.453f, R + WRIST_Z, -.004f, L + ARM_X, -1f, L + ARM_Y, -.3f, L + ARM_Z, .25f, L + ELBOW, .8f, L + WRIST_X, -.2f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_OUT0 = {CROUCH, 3.2f, CHEST_YAW, -.3f, SPINE_YAW, -.08f, PELVIS_YAW, -.05f, SPINE_PITCH, .2f, SHIFT_Z, -1f, HEAD_YAW, .15f, LL + LEG_X, -.4f, LL + KNEE, .4f, LL + LEG_Z, .12f, RL + LEG_X, .3f, RL + KNEE, .3f, RL + LEG_Z, .12f, RL + ANKLE, .25f, R + ARM_X, -.233f, R + ARM_Y, .435f, R + ARM_Z, -.006f, R + ELBOW, 1.692f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.2f, L + ARM_X, -.6f, L + ARM_Y, -.2f, L + ARM_Z, .3f, L + ELBOW, 1f, L + WRIST_X, -.1f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_BACK1 = {CROUCH, 3f, CHEST_YAW, .32f, SPINE_YAW, .08f, PELVIS_YAW, .1f, SPINE_PITCH, .1f, SHIFT_Z, .2f, HEAD_YAW, -.12f, LL + LEG_X, -.4f, LL + KNEE, .4f, LL + LEG_Z, .12f, RL + LEG_X, .3f, RL + KNEE, .3f, RL + LEG_Z, .12f, RL + ANKLE, .25f, R + ARM_X, .55f, R + ARM_Y, -.384f, R + ARM_Z, .462f, R + ELBOW, 2.5f, R + WRIST_X, 1.697f, R + WRIST_Z, 1.06f, L + ARM_X, -1f, L + ARM_Y, -.3f, L + ARM_Z, .25f, L + ELBOW, .8f, L + WRIST_X, -.2f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_OUT1 = {CROUCH, 3.2f, CHEST_YAW, -.3f, SPINE_YAW, -.08f, PELVIS_YAW, -.05f, SPINE_PITCH, .2f, SHIFT_Z, -1f, HEAD_YAW, .15f, LL + LEG_X, -.4f, LL + KNEE, .4f, LL + LEG_Z, .12f, RL + LEG_X, .3f, RL + KNEE, .3f, RL + LEG_Z, .12f, RL + ANKLE, .25f, R + ARM_X, -.587f, R + ARM_Y, .359f, R + ARM_Z, -.577f, R + ELBOW, 1.956f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.2f, L + ARM_X, -.6f, L + ARM_Y, -.2f, L + ARM_Z, .3f, L + ELBOW, 1f, L + WRIST_X, -.1f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_BACK2 = {CROUCH, 3f, CHEST_YAW, .32f, SPINE_YAW, .08f, PELVIS_YAW, .1f, SPINE_PITCH, .1f, SHIFT_Z, .2f, HEAD_YAW, -.12f, LL + LEG_X, -.4f, LL + KNEE, .4f, LL + LEG_Z, .12f, RL + LEG_X, .3f, RL + KNEE, .3f, RL + LEG_Z, .12f, RL + ANKLE, .25f, R + ARM_X, .256f, R + ARM_Y, -.025f, R + ARM_Z, 1.168f, R + ELBOW, 2.5f, R + WRIST_X, 1.75f, R + WRIST_Z, 1.2f, L + ARM_X, -1f, L + ARM_Y, -.3f, L + ARM_Z, .25f, L + ELBOW, .8f, L + WRIST_X, -.2f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_OUT2 = {CROUCH, 3.2f, CHEST_YAW, -.3f, SPINE_YAW, -.08f, PELVIS_YAW, -.05f, SPINE_PITCH, .2f, SHIFT_Z, -1f, HEAD_YAW, .15f, LL + LEG_X, -.4f, LL + KNEE, .4f, LL + LEG_Z, .12f, RL + LEG_X, .3f, RL + KNEE, .3f, RL + LEG_Z, .12f, RL + ANKLE, .25f, R + ARM_X, -.972f, R + ARM_Y, -.172f, R + ARM_Z, -1f, R + ELBOW, 1.813f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.2f, L + ARM_X, -.6f, L + ARM_Y, -.2f, L + ARM_Z, .3f, L + ELBOW, 1f, L + WRIST_X, -.1f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_LAST = {CROUCH, 4.4f, CHEST_YAW, -.45f, SPINE_YAW, -.12f, PELVIS_YAW, -.1f, SPINE_PITCH, .3f, SHIFT_Z, -2.8f, HEAD_YAW, .2f, HEAD_PITCH, -.15f, LL + LEG_X, -.7f, LL + KNEE, .8f, RL + LEG_X, .5f, RL + KNEE, .2f, RL + ANKLE, .45f, R + ARM_X, -1.169f, R + ARM_Y, .108f, R + ARM_Z, -1f, R + ELBOW, 1.723f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.2f, L + ARM_X, .4f, L + ARM_Y, .2f, L + ARM_Z, .4f, L + ELBOW, .3f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_DRAW0 = {CROUCH, 1.8f, CHEST_YAW, .6f, SPINE_YAW, .15f, PELVIS_YAW, .45f, SHIFT_Z, .7f, HEAD_YAW, -.55f, SPINE_PITCH, -.03f, LL + LEG_X, -.42f, LL + KNEE, .1f, RL + LEG_X, .22f, RL + KNEE, .45f, RL + ANKLE, .2f, R + ARM_X, -1.078f, R + ARM_Y, .66f, R + ARM_Z, 1.824f, R + ELBOW, 1.195f, R + WRIST_X, 1.165f, R + WRIST_Z, .168f, L + ARM_X, -.544f, L + ARM_Y, 1.048f, L + ARM_Z, .385f, L + ELBOW, 1.376f, L + WRIST_X, -.243f, L + WRIST_Z, .456f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_DRAW1 = {CROUCH, 3f, CHEST_YAW, .85f, SPINE_YAW, .22f, PELVIS_YAW, .55f, SHIFT_Z, 1.2f, HEAD_YAW, -.75f, SPINE_PITCH, -.08f, SPINE_ROLL, -.06f, LL + LEG_X, -.5f, LL + KNEE, .1f, RL + LEG_X, .28f, RL + KNEE, .7f, RL + ANKLE, .3f, R + ARM_X, -1.7f, R + ARM_Y, .577f, R + ARM_Z, 1.538f, R + ELBOW, .168f, R + WRIST_X, .241f, R + WRIST_Z, .035f, L + ARM_X, .388f, L + ARM_Y, .01f, L + ARM_Z, 1.649f, L + ELBOW, .817f, L + WRIST_X, .422f, L + WRIST_Z, 0f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_STEP = {CROUCH, 2.4f, CHEST_YAW, .3f, SPINE_YAW, .08f, PELVIS_YAW, .15f, SHIFT_Z, -.2f, HEAD_YAW, -.25f, LL + LEG_X, -.5f, LL + KNEE, .3f, RL + LEG_X, .3f, RL + KNEE, .4f, R + ARM_X, -.543f, R + ARM_Y, .731f, R + ARM_Z, 2.1f, R + ELBOW, .964f, R + WRIST_X, 1.089f, R + WRIST_Z, 1.145f, L + ARM_X, -1.2f, L + ARM_Y, -.3f, L + ARM_Z, .1f, L + ELBOW, .4f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_LOOSE = {CROUCH, 2.8f, CHEST_YAW, .05f, PELVIS_YAW, -.05f, SPINE_PITCH, .22f, SHIFT_Z, -1.4f, HEAD_YAW, .1f, LL + LEG_X, -.55f, LL + KNEE, .55f, RL + LEG_X, .4f, RL + ANKLE, .45f, R + ARM_X, -.7f, R + ARM_Y, .5f, R + ARM_Z, 2f, R + ELBOW, .7f, R + WRIST_X, 1.4f, R + WRIST_Z, 1.15f, L + ARM_X, .3f, L + ARM_Y, .1f, L + ARM_Z, .4f, L + ELBOW, .5f, R + CURL, .6f, L + CURL, .5f};
    private static final float[] S_FOLLOW = {CROUCH, 3.2f, CHEST_YAW, -.75f, SPINE_YAW, -.2f, PELVIS_YAW, -.3f, SPINE_PITCH, .35f, SHIFT_Z, -1.5f, HEAD_YAW, .4f, LL + LEG_X, -.55f, LL + KNEE, .55f, RL + LEG_X, .45f, RL + ANKLE, .5f, R + ARM_X, -.9f, R + ARM_Y, .3f, R + ARM_Z, -.4f, R + ELBOW, .5f, R + WRIST_X, .2f, L + ARM_X, .4f, L + ARM_Y, .1f, L + ARM_Z, .5f, L + ELBOW, .5f, R + CURL, .4f, L + CURL, .5f};
    private static final float[] W_READY0 = {CROUCH, 1.3f, CHEST_YAW, .2f, SPINE_YAW, .05f, R + ARM_X, .034f, R + ARM_Y, .043f, R + ARM_Z, .112f, R + ELBOW, 1.72f, R + WRIST_X, 1.027f, R + WRIST_Z, -.022f, L + ARM_X, -.6f, L + ARM_Y, -.2f, L + ARM_Z, .3f, L + ELBOW, 1.3f, L + WRIST_X, 0f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] W_WIND0 = {CROUCH, 1.8f, CHEST_YAW, .7f, SPINE_YAW, .2f, PELVIS_YAW, .2f, SHIFT_Z, .7f, HEAD_YAW, -.3f, RL + KNEE, .3f, LL + LEG_X, -.28f, R + ARM_X, -1.316f, R + ARM_Y, -.595f, R + ARM_Z, -1f, R + ELBOW, .933f, R + WRIST_X, .807f, R + WRIST_Z, -.058f, L + ARM_X, -1f, L + ARM_Y, -.5f, L + ARM_Z, .1f, L + ELBOW, 1.1f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] W_HIT0 = {CROUCH, 2f, CHEST_YAW, -.45f, SPINE_YAW, -.12f, PELVIS_YAW, -.15f, SHIFT_Z, -1.1f, SPINE_PITCH, .15f, HEAD_YAW, .25f, LL + LEG_X, -.4f, LL + KNEE, .45f, RL + LEG_X, .3f, RL + ANKLE, .3f, R + ARM_X, -.014f, R + ARM_Y, .478f, R + ARM_Z, -.171f, R + ELBOW, 2.059f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.2f, L + ARM_X, -.3f, L + ARM_Y, .2f, L + ARM_Z, .7f, L + ELBOW, .8f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] W_THRU0 = {CROUCH, 1.9f, CHEST_YAW, -.8f, SPINE_YAW, -.22f, PELVIS_YAW, -.25f, SHIFT_Z, -.9f, SPINE_PITCH, .18f, HEAD_YAW, .35f, LL + LEG_X, -.4f, LL + KNEE, .45f, RL + LEG_X, .3f, RL + ANKLE, .3f, R + ARM_X, -.164f, R + ARM_Y, .073f, R + ARM_Z, -.276f, R + ELBOW, 1.277f, R + WRIST_X, 1.75f, R + WRIST_Z, -.718f, L + ARM_X, .2f, L + ARM_Y, 0f, L + ARM_Z, .9f, L + ELBOW, .6f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] W_READY1 = {CROUCH, 1.3f, CHEST_YAW, -.15f, R + ARM_X, .274f, R + ARM_Y, -.072f, R + ARM_Z, .069f, R + ELBOW, 2.089f, R + WRIST_X, 1.15f, R + WRIST_Z, -.059f, L + ARM_X, -.6f, L + ARM_Y, -.2f, L + ARM_Z, .3f, L + ELBOW, 1.3f, L + WRIST_X, 0f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] W_WIND1 = {CROUCH, 1.8f, CHEST_YAW, -.65f, SPINE_YAW, -.18f, PELVIS_YAW, -.15f, SHIFT_Z, .5f, HEAD_YAW, .3f, LL + LEG_X, -.25f, LL + KNEE, .3f, R + ARM_X, -1.377f, R + ARM_Y, .81f, R + ARM_Z, 1.09f, R + ELBOW, 2.114f, R + WRIST_X, 1.75f, R + WRIST_Z, -1.2f, L + ARM_X, -.2f, L + ARM_Y, 0f, L + ARM_Z, .6f, L + ELBOW, .9f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] W_HIT1 = {CROUCH, 2f, CHEST_YAW, .4f, SPINE_YAW, .12f, PELVIS_YAW, .12f, SHIFT_Z, -1f, SPINE_PITCH, .14f, HEAD_YAW, -.25f, LL + LEG_X, -.4f, LL + KNEE, .45f, RL + LEG_X, .3f, RL + ANKLE, .3f, R + ARM_X, -1.302f, R + ARM_Y, -.409f, R + ARM_Z, -.176f, R + ELBOW, .486f, R + WRIST_X, 1.578f, R + WRIST_Z, .527f, L + ARM_X, .3f, L + ARM_Y, 0f, L + ARM_Z, .5f, L + ELBOW, .5f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] W_THRU1 = {CROUCH, 1.9f, CHEST_YAW, .8f, SPINE_YAW, .22f, PELVIS_YAW, .22f, SHIFT_Z, -.8f, SPINE_PITCH, .15f, HEAD_YAW, -.35f, LL + LEG_X, -.4f, LL + KNEE, .45f, RL + LEG_X, .3f, RL + ANKLE, .3f, R + ARM_X, -1.455f, R + ARM_Y, -.088f, R + ARM_Z, -1f, R + ELBOW, .112f, R + WRIST_X, .877f, R + WRIST_Z, -.209f, L + ARM_X, -.6f, L + ARM_Y, -.3f, L + ARM_Z, .2f, L + ELBOW, 1.2f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] W_SPIN = {CROUCH, 2f, SPINE_PITCH, .08f, HEAD_PITCH, .05f, LL + LEG_Z, .18f, RL + LEG_Z, .18f, LL + KNEE, .2f, RL + KNEE, .2f, R + ARM_X, 0f, R + ARM_Y, 0f, R + ARM_Z, 1.5f, R + ELBOW, .15f, R + WRIST_X, 1.25f, R + WRIST_Z, 0f, L + ARM_X, 0f, L + ARM_Y, 0f, L + ARM_Z, 1.45f, L + ELBOW, .2f, L + WRIST_X, 0f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] W_COIL = {CROUCH, 2.4f, SPINE_PITCH, .12f, CHEST_YAW, .45f, SPINE_YAW, .15f, LL + LEG_Z, .15f, RL + LEG_Z, .15f, LL + KNEE, .3f, RL + KNEE, .25f, R + ARM_X, -.129f, R + ARM_Y, .854f, R + ARM_Z, 1.604f, R + ELBOW, 0f, R + WRIST_X, 1.75f, R + WRIST_Z, -.94f, L + ARM_X, -.438f, L + ARM_Y, .535f, L + ARM_Z, .51f, L + ELBOW, 2.411f, L + WRIST_X, 1.636f, L + WRIST_Z, .358f, R + CURL, 1f, L + CURL, .5f};
    // The spear's flurry, a two-handed stance: the torso retracted (spear drawn back at the right hip, left side and
    // shoulder forward) and thrust (shoulders squared, the weight driven onto the front leg); the arms are solved.
    private static final float[] FL_BACK = {CROUCH, 2.6f, PELVIS_YAW, .35f, SPINE_YAW, .1f, CHEST_YAW, .32f, SPINE_PITCH, .08f, CHEST_PITCH, 0f, SHIFT_Z, .4f, HEAD_YAW, -.5f, LL + LEG_X, -.5f, LL + KNEE, .35f, LL + LEG_Z, .1f, RL + LEG_X, .4f, RL + KNEE, .15f, RL + ANKLE, .3f, RL + LEG_Z, .1f, R + CURL, 1f, L + CURL, 1f};
    private static final float[] FL_OUT = {CROUCH, 3.2f, PELVIS_YAW, .2f, SPINE_YAW, .05f, CHEST_YAW, 0f, SPINE_PITCH, .2f, CHEST_PITCH, .06f, SHIFT_Z, -1f, HEAD_YAW, -.25f, LL + LEG_X, -.6f, LL + KNEE, .5f, LL + LEG_Z, .1f, RL + LEG_X, .5f, RL + KNEE, .15f, RL + ANKLE, .3f, RL + LEG_Z, .1f, R + CURL, 1f, L + CURL, 1f};
    private static final float[] FL_SEED_R0 = {.658f, -.878f, -.072f, 1.482f, .752f, -.624f}, FL_SEED_R1 = {.237f, -.439f, -.32f, 1.664f, 1.249f, -.436f},
            FL_SEED_L0 = {-.399f, .539f, -.886f, 1.14f, .806f, .122f}, FL_SEED_L1 = {-.487f, -.014f, -.878f, .753f, .769f, -.141f};
    /** Each thrust's aim (up positive): low, middle, high, and again. */
    private static final float[] FL_AIM = {-.14f, -.02f, .1f, -.1f, .04f, -.04f};
    // The sword driven into the ground: the body raising it point down, the kneel over it (right knee down, left foot
    // forward); the arms are solved onto its hilt.
    private static final float[] K_RAISE = {CROUCH, 2.5f, LIFT, .3f, SHIFT_Z, .5f, SPINE_PITCH, -.05f, CHEST_PITCH, -.1f, HEAD_PITCH, .15f, CHEST_YAW, 0f, PELVIS_YAW, 0f, SPINE_YAW, 0f, LL + LEG_X, -.45f, LL + KNEE, .1f, RL + LEG_X, .25f, RL + KNEE, .3f, R + CURL, 1f, L + CURL, .9f};
    private static final float[] K_KNEEL = {CROUCH, 6f, SHIFT_Z, 1.5f, SPINE_PITCH, .3f, CHEST_PITCH, .1f, HEAD_PITCH, -.2f, CHEST_YAW, 0f, PELVIS_YAW, 0f, SPINE_YAW, 0f, RL + LEG_X, 1.05f, RL + KNEE, -.12f, RL + ANKLE, .5f, RL + LEG_Z, .08f, RL + LEG_Y, .05f, LL + LEG_X, -.55f, LL + KNEE, -.62f, LL + LEG_Z, .12f, LL + LEG_Y, .15f, R + CURL, 1f, L + CURL, .9f};
    private static final float[] K_SEED_RAISE_R = {-.983f, -.521f, -.086f, .919f, .503f, -.743f}, K_SEED_RAISE_L = {-1.744f, -.524f, -.088f, .223f, .569f, -.72f},
            K_SEED_KNEEL_R = {-1.233f, -.527f, .25f, .553f, -.252f, -.686f}, K_SEED_KNEEL_L = {-1.372f, -.526f, .248f, 1.179f, .512f, -.694f};
    /** The shatter under his hands: the arms thrown up and open, the body jolted back. */
    private static final float[] K_RECOIL = {CROUCH, 5f, SHIFT_Z, 2.2f, SPINE_PITCH, .05f, CHEST_PITCH, -.12f, HEAD_PITCH, .05f, RL + LEG_X, 1.0f, RL + KNEE, -.1f, RL + ANKLE, .5f, LL + LEG_X, -.5f, LL + KNEE, -.55f, LL + LEG_Z, .12f,
            R + ARM_X, -1.1f, R + ARM_Y, -.3f, R + ARM_Z, .75f, R + ELBOW, 1.5f, R + WRIST_X, -.4f, R + CURL, .2f, L + ARM_X, -1.0f, L + ARM_Y, -.3f, L + ARM_Z, .8f, L + ELBOW, 1.4f, L + WRIST_X, -.4f, L + CURL, .2f};
    /** Getting up from the knee. */
    private static final float[] K_RISE = {CROUCH, 2.4f, SHIFT_Z, .8f, SPINE_PITCH, .12f, LL + LEG_X, -.3f, LL + KNEE, .2f, RL + LEG_X, .3f, RL + KNEE, .35f, R + ARM_X, -.4f, R + ARM_Z, .35f, R + ELBOW, .8f, R + CURL, .4f, L + ARM_X, -.3f, L + ARM_Z, .35f, L + ELBOW, .7f, L + CURL, .4f};
    /** The grip of the planted sword in model pixels when nothing better is known: straight ahead, at PLANT_REACH and PLANT_GRIP. */
    static final float GRIP_Z = -PLANT_REACH * 16 / .9375f, GRIP_Y = 24 - PLANT_GRIP * 16 / .9375f;
    private static final float[] F_RAISE = {HEAD_PITCH, .3f, HEAD_YAW, -.2f, CHEST_YAW, .12f, R + ARM_X, -.482f, R + ARM_Y, -.041f, R + ARM_Z, .036f, R + ELBOW, 1.593f, R + WRIST_X, 1.406f, R + WRIST_Z, -.045f, R + CURL, .25f, L + CURL, .38f};
    private static final float[] F_GROW = {HEAD_PITCH, .22f, HEAD_YAW, -.15f, CHEST_YAW, .1f, CROUCH, .5f, R + ARM_X, -.274f, R + ARM_Y, .035f, R + ARM_Z, .013f, R + ELBOW, 1.76f, R + WRIST_X, 1.262f, R + WRIST_Z, -.027f, R + CURL, .6f, L + CURL, .38f};

    /** A key: the base with these joints set. */
    private static Pose on(Pose base, float[] k) {
        Pose p = base.copy();
        for (int i = 0; i + 1 < k.length; i += 2) p.v[(int) k[i]] = k[i + 1];
        return p;
    }
    /** a and b mixed by w (0 a .. 1 b), both over the base. */
    private static Pose mix(Pose base, float[] a, float[] b, float w) {
        Pose p = on(base, a);
        p.toward(on(base, b), Mth.clamp(w, 0, 1));
        return p;
    }
    /** The weapon carried ready in his right hand (the stance's carry). */
    static Pose carry(Pose base, int weapon) {
        Pose p = base.copy();
        IcemanMotion.armed(p, weapon);
        return p;
    }
    private static float[] ready(int weapon, int combo) {
        return switch (weapon) {
            case W_MACE -> combo == 1 ? M_READY1 : M_READY0;
            case W_SPEAR -> combo == 1 ? S_READY1 : S_READY0;
            default -> combo == 1 ? W_READY1 : W_READY0;
        };
    }

    // ------------------------------------------------------------------ sampling
    /** The move's pose at its clock t over the base, or null to keep the base. */
    public static Pose sample(int action, float t, Pose base, IcemanMotion.Ctx c) {
        int w = Mth.clamp(c.weapon(), 0, WEAPONS - 1);
        return switch (action) {
            case FORM -> form(base, t, w);
            case STRIKE -> strike(base, IcemanWeaponFx.stopped(c.entity(), action, t), w, Mth.clamp(c.combo(), 0, 2));
            case CHARGE -> charge(base, t, w, c);
            case RELEASE -> w == W_SWORD ? unplant(base, t, c) : release(base, IcemanWeaponFx.stopped(c.entity(), action, t), w, c);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ forming
    /**
     * The weapon forming: the right hand comes up a little before him, palm up and the fingers open while the cold
     * gathers into it, closing round the grip as the weapon completes; he looks at it. Then the carry.
     */
    static Pose form(Pose base, float t, int weapon) {
        Pose raise = on(base, F_RAISE), grow = on(base, F_GROW);
        float breath = Mth.sin(t * .9f) * .03f;
        grow.armAdd(0, CURL, breath);
        return new Track(true).key(0, base).key(3, raise).key(7, grow).key(FORM_TICKS, carry(base, weapon)).sample(t);
    }

    // ------------------------------------------------------------------ the swings
    static Pose strike(Pose base, float t, int w, int combo) {
        return switch (w) {
            case W_MACE -> combo == 2 ? maceSlam(base, t) : swing(base, t, w, combo == 0 ? M_READY0 : M_READY1, combo == 0 ? M_WIND0 : M_WIND1,
                    combo == 0 ? M_HIT0 : M_HIT1, combo == 0 ? M_THRU0 : M_THRU1, 4.6f, 10.6f);
            case W_SPEAR -> combo == 2 ? flurry(base, t) : swing(base, t, w, combo == 0 ? S_READY0 : S_READY1, combo == 0 ? S_COCK0 : S_COCK1,
                    combo == 0 ? S_HIT0 : S_HIT1, combo == 0 ? S_THRU0 : S_THRU1, 2.1f, 5.8f);
            default -> combo == 2 ? swordSpin(base, t) : swing(base, t, w, combo == 0 ? W_READY0 : W_READY1, combo == 0 ? W_WIND0 : W_WIND1,
                    combo == 0 ? W_HIT0 : W_HIT1, combo == 0 ? W_THRU0 : W_THRU1, 3.2f, 8.3f);
        };
    }
    /** One of the two swings: ready, wind-up, the hit at SWING_HIT (still moving: it carries on through), the follow-through, the carry. */
    private static Pose swing(Pose base, float t, int w, float[] ready, float[] wind, float[] hit, float[] through, float windAt, float throughAt) {
        int c = ready == M_READY1 || ready == S_READY1 || ready == W_READY1 ? 1 : 0;
        int len = SWING_TICKS[w][c], at = SWING_HIT[w][c];
        return new Track(true).key(0, on(base, ready)).key(windAt, on(base, wind)).key(at, on(base, hit)).key(throughAt, on(base, through))
                .key(len, carry(base, w)).sample(t);
    }
    /**
     * The mace's finisher: both hands on the haft, lifted, raised high over and behind his head (up on his toes, leaning
     * back), then down with the whole body into the ground ahead at SWING_HIT, crouched deep over it while it bursts, and up.
     */
    private static Pose maceSlam(Pose base, float t) {
        Pose slam = on(base, M_SLAM2), dig = slam.copy().add(CROUCH, .7f).add(SPINE_PITCH, .06f).add(SHIFT_Z, -.3f);
        dig.armAdd(0, ARM_X, .08f).armAdd(1, ARM_X, .08f);
        Pose settled = slam.copy().add(CROUCH, .4f).add(SPINE_PITCH, .02f);
        int hit = SWING_HIT[W_MACE][2], len = SWING_TICKS[W_MACE][2];
        Pose p = new Track(true).key(0, on(base, M_READY0)).key(4.5f, on(base, M_LIFT2)).key(10, on(base, M_RAISE2)).key(hit, slam).key(hit + 1.2f, dig)
                .key(hit + 5, settled).key(len - 2.5f, on(base, M_RISE2)).key(len, carry(base, W_MACE)).sample(t);
        // The shock of the impact through the body.
        float jolt = t > hit ? (float) Math.exp(-(t - hit) * .7f) * Mth.sin((t - hit) * 2.6f) : 0;
        return p.add(CROUCH, .5f * jolt).add(CHEST_PITCH, .04f * jolt);
    }
    /**
     * The spear's finisher: a real two-handed spear stance. He gathers into it (the spear drawn back at his right hip, held
     * a little across the body, the left hand forward on the shaft, left side leading), then thrust and retract once for
     * every SPEAR_FLURRY tick (low, middle, high, and again): the rear hand drives the spear along its own line, the front
     * hand guides it (the shaft slides through it), the shoulders turn square, the hips follow, the weight goes onto the
     * front leg and comes back; the last thrust is a deep lunge held a moment. Both hands stay on the shaft (solved).
     */
    private static Pose flurry(Pose base, float t) {
        int lastAt = SPEAR_FLURRY[SPEAR_FLURRY.length - 1], len = SWING_TICKS[W_SPEAR][2];
        float gather = 3.5f, settle = lastAt + 2.2f;
        if (t < gather) return new Track(true).key(0, on(base, S_READY0)).key(gather, flurryStance(base, gather)).sample(t);
        if (t > settle) return new Track(true).key(settle, flurryStance(base, settle)).key(len, carry(base, W_SPEAR)).sample(t);
        return flurryStance(base, t);
    }
    /** How far out the spear is at t (0 drawn back .. 1 thrust out; the last one a little further). */
    static float extension(float t) {
        int n = SPEAR_FLURRY.length;
        float out = 0;
        for (int i = 0; i < n; i++) {
            float at = SPEAR_FLURRY[i], prev = i == 0 ? 3.5f : SPEAR_FLURRY[i - 1], next = i == n - 1 ? at + 6 : SPEAR_FLURRY[i + 1];
            boolean last = i == n - 1;
            float rise = Math.min(1.3f, (at - prev) * .55f), fall = Math.min(1.3f, (next - at) * .5f), hold = last ? 1.4f : .2f, top = last ? 1.15f : 1;
            float v;
            if (t < at - rise || t > at + hold + fall) v = 0;
            else if (t <= at) { float x = (t - (at - rise)) / rise; v = top * x * x * (3 - 2 * x); }
            else if (t <= at + hold) v = top;
            else v = top * (1 - PantherMotion.ease((t - at - hold) / fall));
            out = Math.max(out, v);
        }
        return out;
    }
    /** The thrusts' aim at t, gliding from one thrust's to the next. */
    private static float aim(float t) {
        int n = SPEAR_FLURRY.length;
        if (t <= SPEAR_FLURRY[0]) return FL_AIM[0];
        for (int i = 0; i < n - 1; i++)
            if (t <= SPEAR_FLURRY[i + 1]) return Mth.lerp(PantherMotion.ease((t - SPEAR_FLURRY[i]) / (SPEAR_FLURRY[i + 1] - SPEAR_FLURRY[i])), FL_AIM[i], FL_AIM[i + 1]);
        return FL_AIM[n - 1];
    }
    private static final Matrix4f CH = new Matrix4f(), FI = new Matrix4f();
    private static final Vector3f TGT = new Vector3f(), AX = new Vector3f(), AX2 = new Vector3f(), FG = new Vector3f();
    private static final float[] SEED = new float[6];
    private static float[] seed(float[] a, float[] b, float k) {
        for (int i = 0; i < 6; i++) SEED[i] = Mth.lerp(k, a[i], b[i]);
        return SEED;
    }
    /** The stance at t: the torso by the extension, the rear hand on its line, the front hand solved onto the real shaft. */
    private static Pose flurryStance(Pose base, float t) {
        float ext = extension(t), e = Mth.clamp(ext, 0, 1), pitch = aim(t);
        Pose p = mix(base, FL_BACK, FL_OUT, ext);
        // A low thrust bends the knees, a high one lifts the chest.
        p.add(CROUCH, -pitch * 3 * e).add(CHEST_PITCH, -pitch * .3f * e).add(HEAD_PITCH, -pitch * .5f);
        IcemanWeaponReach.chest(p, CH);
        AX.set(.1f, -.06f - pitch, -1).normalize();
        TGT.set(-3.0f + .4f * ext, 12.0f - .8f * ext + pitch * 4, 3.5f - 8.0f * ext);
        IcemanWeaponReach.reach(p, 0, CH, TGT, AX, 4, null, 0, seed(FL_SEED_R0, FL_SEED_R1, e));
        // The front hand on the shaft as it really is (the rear hand's frame: the weapon's long axis is its -z).
        IcemanWeaponReach.fist(p, 0, CH, FI);
        AX2.set(-FI.m20(), -FI.m21(), -FI.m22()).normalize();
        float dist = 11 - 6 * ext;
        TGT.set(FI.m30() + AX2.x * dist, FI.m31() + AX2.y * dist, FI.m32() + AX2.z * dist);
        IcemanWeaponReach.reach(p, 1, CH, TGT, AX2, 3, null, 0, seed(FL_SEED_L0, FL_SEED_L1, e));
        return p;
    }
    /** The sword's finisher: a coil to the right, then one fast whole turn to his left with the sword out level, settling. */
    private static Pose swordSpin(Pose base, float t) {
        Pose coil = on(base, W_COIL).set(ROOT_YAW, .45f);
        Pose a = on(base, W_SPIN).set(ROOT_YAW, -.5f), b = on(base, W_SPIN).set(ROOT_YAW, -Mth.PI), c = on(base, W_SPIN).set(ROOT_YAW, -Mth.TWO_PI - .3f);
        Pose settle = on(base, W_SPIN).set(ROOT_YAW, -Mth.TWO_PI).add(CROUCH, .4f);
        Pose end = carry(base, W_SWORD).set(ROOT_YAW, -Mth.TWO_PI);
        int hit = SWING_HIT[W_SWORD][2], len = SWING_TICKS[W_SWORD][2];
        Pose p = new Track(true).key(0, on(base, W_READY0)).key(2.4f, coil).key(hit - 1.6f, a).key(hit, b).key(hit + 3.6f, c).key(hit + 6, settle)
                .key(len, end).sample(t);
        stepping(p, p.v[ROOT_YAW]);
        return p;
    }

    // ------------------------------------------------------------------ holds
    /**
     * A press: a short ready wind-up (the swing to come starts from it). Held past HOLD_TICKS: the mace carried two-handed
     * over his right shoulder, bracing lower as it grows; the spear drawn back for the throw (the rear shoulder back, the
     * torso twisted, the front arm pointing at the target); the sword driven into the ground (plant).
     */
    static Pose charge(Pose base, float t, int w, IcemanMotion.Ctx c) {
        Pose ready = on(base, ready(w, IcemanWeaponFx.nextCombo(c.entity())));
        if (t < HOLD_TICKS) return new Track(true).key(0, carry(base, w)).key(HOLD_TICKS - 1.5f, ready).sample(t);
        float held = t - HOLD_TICKS;
        switch (w) {
            case W_MACE -> {
                float k = Mth.clamp(held / MACE_GROW, 0, 1);
                Pose sh = mix(base, M_SHOULDER0, M_SHOULDER1, PantherMotion.ease(k));
                Pose p = new Track(true).key(0, ready).key(6, sh).sample(Math.min(held, 6));
                if (held > 6) p = sh;
                // The strain: a tremble in the arms and the chest that grows with the weight.
                float s = (.012f + .035f * k) * PantherMotion.k(held, 4, 10);
                float n1 = Mth.sin(t * 2.3f) + .6f * Mth.sin(t * 3.7f + 1), n2 = Mth.sin(t * 2.9f + 2) + .5f * Mth.sin(t * 4.3f);
                p.add(CHEST_PITCH, s * .5f * n1).add(CROUCH, s * 3 * n2).armAdd(0, ARM_X, s * n2).armAdd(1, ARM_X, s * n1).add(HEAD_PITCH, s * .4f * n2);
                return p;
            }
            case W_SPEAR -> {
                float k = Mth.clamp(held / SPEAR_DRAW, 0, 1);
                Pose draw = mix(base, S_DRAW0, S_DRAW1, PantherMotion.ease(k));
                Pose p = new Track(true).key(0, ready).key(5, draw).sample(Math.min(held, 5));
                if (held > 5) p = draw;
                float full = PantherMotion.k(held, SPEAR_DRAW - 2, SPEAR_DRAW + 4) * .025f;
                return p.armAdd(0, ARM_X, full * Mth.sin(t * 3.1f)).add(CHEST_YAW, full * .5f * Mth.sin(t * 2.3f));
            }
            default -> { return plant(base, held, ready, c); }
        }
    }

    // ------------------------------------------------------------------ the planted sword
    /**
     * The sword held: the sword turns point down in his hand (IcemanArmory.TURN, IcemanWeaponFx.hold) while both hands
     * bring it up before his chest (by PLANT_RAISE), then he drives it straight down into the ground (faster and faster)
     * as he drops onto his right knee, the impact at PLANT_AT going through him; then he kneels over it, both hands on its
     * hilt, breathing. The hilt is where the server planted it (or where it will be): the body turns to face it, the
     * arms are solved onto it every frame.
     */
    static Pose plant(Pose base, float held, Pose ready, IcemanMotion.Ctx c) {
        Vector3f grip = grip(c.entity(), held < PLANT_AT, true, GRIPV);
        float face = Mth.clamp((float) Math.atan2(-grip.x, -grip.z), -1.2f, 1.2f);
        if (held < PLANT_RAISE) return new Track(true).key(0, ready).key(PLANT_RAISE, raisePose(base, grip, face)).sample(held);
        Pose impact = on(base, K_KNEEL).add(CROUCH, .9f).add(SPINE_PITCH, .14f).add(CHEST_PITCH, .05f).add(SHIFT_Z, -.4f);
        Pose p = new Track(true).key(PLANT_RAISE, on(base, K_RAISE)).key(PLANT_AT, impact).key(PLANT_AT + 4.5f, on(base, K_KNEEL)).sample(held);
        p.v[ROOT_YAW] += face;
        // Kneeling over it: breathing, the weight never quite still (the hands stay on the hilt: solved after).
        float k = PantherMotion.k(held, PLANT_AT + 3, PLANT_AT + 8), tt = held - PLANT_AT;
        p.add(CHEST_PITCH, k * .025f * Mth.sin(tt * .1f)).add(SPINE_PITCH, k * .012f * Mth.sin(tt * .1f + .7f)).add(HEAD_PITCH, k * .03f * Mth.sin(tt * .045f));
        // The drive: from the raised hilt straight down onto the planted one, accelerating to the impact.
        float x = Mth.clamp((held - PLANT_RAISE) / (PLANT_AT - PLANT_RAISE), 0, 1), drive = x * x * (1.6f - .6f * x);
        hilt(p, grip, face, drive);
        return p;
    }
    /** The body raising the sword (its arms solved onto the hilt held before his chest). */
    private static Pose raisePose(Pose base, Vector3f grip, float face) {
        Pose p = on(base, K_RAISE);
        p.v[ROOT_YAW] += face;
        hilt(p, grip, face, 0);
        return p;
    }
    private static final Vector3f GRIPV = new Vector3f(), HT = new Vector3f(), UP = new Vector3f(), YR = new Vector3f(), YL = new Vector3f();
    /**
     * Both hands on the hilt: drive 0 = held up before his chest (above where it will stand), 1 = on the planted hilt
     * (grip in model pixels). The right hand under the crossguard, the left over the pommel, thumbs up, forearms in
     * from the sides (elbows out).
     */
    private static void hilt(Pose p, Vector3f grip, float face, float drive) {
        IcemanWeaponReach.chest(p, CH);
        float c = Mth.cos(face), s = Mth.sin(face);
        // Raised: 9.5 px higher, 3 px nearer him (turned with his face toward it).
        float k = 1 - drive;
        HT.set(grip.x + 3 * s * k, grip.y - 9.5f * k, grip.z + 3 * c * k);
        UP.set(0, -1, 0);
        YR.set(1, 0, -.3f).normalize().rotateY(face);
        YL.set(-1, 0, -.3f).normalize().rotateY(face);
        IcemanWeaponReach.reach(p, 0, CH, HT, UP, 3, YR, .8f, seed(K_SEED_RAISE_R, K_SEED_KNEEL_R, drive));
        HT.y -= 4;
        IcemanWeaponReach.reach(p, 1, CH, HT, UP, 3, YL, .8f, seed(K_SEED_RAISE_L, K_SEED_KNEEL_L, drive));
    }
    private static final Matrix4f ID = new Matrix4f();
    /**
     * Both hands on the planted hilt in a frame of his own (his own view: the chest frame levelled and facing the sword),
     * the grip given in that frame (pixels, +y down, -z ahead).
     */
    static void hiltLocal(Pose p, Vector3f grip) {
        UP.set(0, -1, 0);
        YR.set(1, 0, -.3f).normalize();
        YL.set(-1, 0, -.3f).normalize();
        HT.set(grip);
        IcemanWeaponReach.reach(p, 0, ID.identity(), HT, UP, 3, YR, .8f, K_SEED_KNEEL_R);
        HT.y -= 4;
        IcemanWeaponReach.reach(p, 1, ID, HT, UP, 3, YL, .8f, K_SEED_KNEEL_L);
    }
    /**
     * Where the planted sword's grip is in this Iceman's model space (pixels): the server's point once it is planted
     * (standingOnly: only while it stands, else also while its pieces fly), before that (predict) where it will go
     * (ahead of where he looks, on the ground); straight ahead when nothing is known.
     */
    static Vector3f grip(int entity, boolean predict, boolean standingOnly, Vector3f out) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entity);
        out.set(0, GRIP_Y, GRIP_Z);
        if (!(e instanceof LivingEntity living)) return out;
        float partial = mc.getFrameTime();
        Vec3 pos = e.getPosition(partial);
        float bodyYaw = Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot);
        Vec3 at = IcemanWeaponFx.plantAt(entity, standingOnly);
        if (at == null) {
            if (!predict) return out;
            float yaw = Mth.lerp(partial, e.yRotO, e.getYRot()) * Mth.DEG_TO_RAD;
            double x = pos.x - Mth.sin(yaw) * PLANT_REACH, z = pos.z + Mth.cos(yaw) * PLANT_REACH;
            at = new Vec3(x, IcemanSpearFx.groundY(mc.level, x, pos.y + .6, z), z);
            // Not below his feet by more than a step (a ledge ahead: the sword stands at his own level).
            if (at.y < pos.y - 1.2 || at.y > pos.y + 1.2) at = new Vec3(x, pos.y, z);
        }
        IcemanWeaponReach.toModel(pos, bodyYaw, at.add(0, PLANT_GRIP, 0), out);
        // Never out of his reach (pushed, or the server's point a little off): pulled in toward him.
        float d = Mth.sqrt(out.x * out.x + out.z * out.z), max = 14;
        if (d > max) { out.x *= max / d; out.z *= max / d; }
        return out;
    }

    /** The feet stepping round under a spin (the root turns the whole body; the legs shuffle with it). */
    private static void stepping(Pose p, float phi) {
        float s = Mth.sin(phi * 2), c = Mth.cos(phi * 2);
        p.legAdd(0, LEG_X, -.18f * Math.max(0, s)).legAdd(0, KNEE, .3f * Math.max(0, s)).legAdd(1, LEG_X, -.18f * Math.max(0, -s))
                .legAdd(1, KNEE, .3f * Math.max(0, -s)).add(LIFT, .25f * Math.abs(c));
    }
    static void clear() {}

    // ------------------------------------------------------------------ releases
    static Pose release(Pose base, float t, int w, IcemanMotion.Ctx c) {
        float k = Mth.clamp(c.charge(), 0, 1);
        switch (w) {
            case W_MACE -> {
                // The heave of the giant mace straight up overhead, then down with everything into the ground at MACE_SLAM_HIT.
                Pose from = mix(base, M_SHOULDER0, M_SHOULDER1, PantherMotion.ease(k));
                Pose big = mix(base, M_BIG_SMALL, M_BIG_FULL, k);
                Pose dig = big.copy().add(CROUCH, .9f).add(SPINE_PITCH, .08f).add(SHIFT_Z, -.4f);
                dig.armAdd(0, ARM_X, .1f).armAdd(1, ARM_X, .1f);
                Pose settled = big.copy().add(CROUCH, .5f);
                Pose up = base.copy().add(CROUCH, 1.5f).add(SPINE_PITCH, .1f);
                Pose p = new Track(true).key(0, from).key(7, on(base, M_HEAVE)).key(MACE_SLAM_HIT, big).key(MACE_SLAM_HIT + 1.3f, dig)
                        .key(MACE_SLAM_HIT + 7, settled).key(MACE_SLAM_TICKS - 3, up).key(MACE_SLAM_TICKS, base).sample(t);
                float jolt = t > MACE_SLAM_HIT ? (float) Math.exp(-(t - MACE_SLAM_HIT) * .5f) * Mth.sin((t - MACE_SLAM_HIT) * 2.2f) * (.6f + k) : 0;
                return p.add(CROUCH, .6f * jolt).add(CHEST_PITCH, .05f * jolt).add(ROOT_ROLL, .015f * jolt);
            }
            case W_SPEAR -> {
                // The step in, the whip of the arm (the spear leaves the hand at SPEAR_THROW_AT), the follow-through.
                Pose from = mix(base, S_DRAW0, S_DRAW1, PantherMotion.ease(k));
                return new Track(true).key(0, from).key(2.2f, on(base, S_STEP)).key(SPEAR_THROW_AT, on(base, S_LOOSE)).key(7, on(base, S_FOLLOW))
                        .key(SPEAR_THROW_TICKS, base).sample(t);
            }
            default -> { return base.copy(); }
        }
    }

    /**
     * The planted sword let go: for SWORD_CRACK_TICKS his hands stay on the hilt while it cracks (a tremor through the
     * arms), then it shatters under them: the arms are thrown up and open, the body jolts back; he gets up off the knee
     * and comes back to the stance.
     */
    static Pose unplant(Pose base, float t, IcemanMotion.Ctx c) {
        Vector3f grip = grip(c.entity(), false, false, GRIPV);
        float face = Mth.clamp((float) Math.atan2(-grip.x, -grip.z), -1.2f, 1.2f);
        float crack = SWORD_CRACK_TICKS;
        if (t <= crack) return kneelHands(base, grip, face, t);
        Pose recoil = on(base, K_RECOIL).set(ROOT_YAW, face * .8f), rise = on(base, K_RISE).set(ROOT_YAW, face * .3f);
        return new Track(true).key(crack, kneelHands(base, grip, face, crack)).key(crack + 2.5f, recoil).key(crack + 5, recoil.copy().add(CROUCH, -.6f))
                .key(SWORD_BREAK_TICKS - 5, rise).key(SWORD_BREAK_TICKS, base).sample(t);
    }
    /** Kneeling with both hands on the hilt, trembling as it cracks (t ticks into the release). */
    private static Pose kneelHands(Pose base, Vector3f grip, float face, float t) {
        Pose p = on(base, K_KNEEL);
        p.v[ROOT_YAW] += face;
        float shake = .012f * Mth.clamp(t / 2, 0, 1);
        p.add(CHEST_PITCH, shake * Mth.sin(t * 5.1f)).add(SPINE_ROLL, shake * Mth.sin(t * 6.3f + 1));
        hilt(p, grip, face, 1);
        return p;
    }
}
