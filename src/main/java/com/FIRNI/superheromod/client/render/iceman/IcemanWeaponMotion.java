package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Iceman's body with an ice weapon (the Ice Armory), as pure functions of the action's clock: the weapon forming in his
 * raised hand, the three weapons' swings (right to left, left to right, then each weapon's finisher), the holds (the mace
 * carried two-handed over his shoulder and braced lower as it grows, the spear drawn back Spartan style, the sword's
 * continuous spin) and the releases (the giant slam, the throw, the sword breaking out of the spin).
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
    private static final float[] S_DRAW0 = {CROUCH, 1.8f, CHEST_YAW, .6f, SPINE_YAW, .15f, PELVIS_YAW, .45f, SHIFT_Z, .7f, HEAD_YAW, -.55f, SPINE_PITCH, -.03f, LL + LEG_X, -.42f, LL + KNEE, .1f, RL + LEG_X, .22f, RL + KNEE, .45f, RL + ANKLE, .2f, R + ARM_X, -1.078f, R + ARM_Y, .66f, R + ARM_Z, 1.824f, R + ELBOW, 1.195f, R + WRIST_X, 1.165f, R + WRIST_Z, .168f, L + ARM_X, -.69f, L + ARM_Y, 1.131f, L + ARM_Z, .301f, L + ELBOW, 2.062f, L + WRIST_X, 1.499f, L + WRIST_Z, .048f, R + CURL, 1f, L + CURL, .5f};
    private static final float[] S_DRAW1 = {CROUCH, 3f, CHEST_YAW, .85f, SPINE_YAW, .22f, PELVIS_YAW, .55f, SHIFT_Z, 1.2f, HEAD_YAW, -.75f, SPINE_PITCH, -.08f, SPINE_ROLL, -.06f, LL + LEG_X, -.5f, LL + KNEE, .1f, RL + LEG_X, .28f, RL + KNEE, .7f, RL + ANKLE, .3f, R + ARM_X, -1.7f, R + ARM_Y, .577f, R + ARM_Z, 1.538f, R + ELBOW, .168f, R + WRIST_X, .241f, R + WRIST_Z, .035f, L + ARM_X, .9f, L + ARM_Y, .149f, L + ARM_Z, 1.979f, L + ELBOW, 1.114f, L + WRIST_X, .392f, L + WRIST_Z, -.484f, R + CURL, 1f, L + CURL, .5f};
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
    private static final float[] W_BREAK = {CROUCH, 3f, SPINE_PITCH, .25f, CHEST_PITCH, .1f, HEAD_PITCH, .2f, SHIFT_Z, .6f, LL + LEG_X, -.2f, LL + KNEE, .4f, RL + KNEE, .35f, R + ARM_X, -.35f, R + ARM_Y, .2f, R + ARM_Z, .55f, R + ELBOW, .9f, R + WRIST_X, -.3f, L + ARM_X, -.45f, L + ARM_Y, -.2f, L + ARM_Z, .5f, L + ELBOW, 1f, R + CURL, .3f, L + CURL, .4f};
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
            case RELEASE -> release(base, IcemanWeaponFx.stopped(c.entity(), action, t), w, c);
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
     * The spear's finisher (God of War): gathered low with the spear drawn back at his side, then six thrusts at
     * SPEAR_FLURRY, low, middle, high and again, the arm snapping out and back, the last one a deep lunge.
     */
    private static Pose flurry(Pose base, float t) {
        Track tr = new Track(true).key(0, on(base, S_READY0)).key(3.5f, on(base, S_GATHER));
        float[][] back = {S_BACK0, S_BACK1, S_BACK2}, out = {S_OUT0, S_OUT1, S_OUT2};
        float prev = 3.5f;
        for (int i = 0; i < SPEAR_FLURRY.length; i++) {
            float at = SPEAR_FLURRY[i];
            boolean last = i == SPEAR_FLURRY.length - 1;
            float pull = at - Math.min(1.4f, (at - prev) * .55f);
            Pose b = on(base, back[i % 3]);
            if (last) b.add(CROUCH, .6f).add(SHIFT_Z, .5f).add(CHEST_YAW, .12f);
            tr.key(pull, b).key(at, on(base, last ? S_LAST : out[i % 3]));
            prev = at;
        }
        int lastAt = SPEAR_FLURRY[SPEAR_FLURRY.length - 1];
        Pose hold = on(base, S_LAST).add(CROUCH, .3f).add(SHIFT_Z, .2f);
        return tr.key(lastAt + 1.6f, hold).key(SWING_TICKS[W_SPEAR][2], carry(base, W_SPEAR)).sample(t);
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
     * over his right shoulder, bracing lower as it grows; the spear drawn back (Spartan) with the left arm pointing ahead;
     * the sword spinning him round.
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
            default -> {
                Pose arms = on(base, W_SPIN);
                Pose p = new Track(true).key(0, ready).key(3, arms).sample(Math.min(held, 3));
                if (held > 3) p = arms;
                float ang = spinTurned(held), phi = -(ang % Mth.TWO_PI);
                p.set(ROOT_YAW, phi);
                // Leaning out against the turn a little, the hair-trigger of the sword's weight as it speeds up.
                float speed = spinSpeed(held) / (Mth.TWO_PI / SPIN_TURN);
                p.add(ROOT_ROLL, -.05f * speed).add(CROUCH, .5f * speed);
                stepping(p, phi);
                SPIN.put(c.entity(), new float[]{phi, spinSpeed(held), IcemanWeaponFx.gameTime()});
                if (SPIN.size() > 64) SPIN.clear();
                return p;
            }
        }
    }
    /** The spin's turn (radians, always growing) after held ticks: speeding up over SPIN_RAMP ticks to one turn every SPIN_TURN. */
    static float spinTurned(float held) {
        float max = Mth.TWO_PI / SPIN_TURN;
        if (held <= 0) return 0;
        if (held < SPIN_RAMP) { float x = held / SPIN_RAMP; return max * SPIN_RAMP * (x * x * x - x * x * x * x / 2); }
        return max * SPIN_RAMP * .5f + max * (held - SPIN_RAMP);
    }
    static float spinSpeed(float held) {
        float max = Mth.TWO_PI / SPIN_TURN;
        if (held <= 0) return 0;
        if (held < SPIN_RAMP) { float x = held / SPIN_RAMP; return max * (3 * x * x - 2 * x * x * x); }
        return max;
    }
    private static final float SPIN_RAMP = 12;
    /** The last spin angle and speed drawn per Iceman (the release carries on from exactly there), and when. */
    private static final Map<Integer, float[]> SPIN = new HashMap<>();
    /** The feet stepping round under a spin (the root turns the whole body; the legs shuffle with it). */
    private static void stepping(Pose p, float phi) {
        float s = Mth.sin(phi * 2), c = Mth.cos(phi * 2);
        p.legAdd(0, LEG_X, -.18f * Math.max(0, s)).legAdd(0, KNEE, .3f * Math.max(0, s)).legAdd(1, LEG_X, -.18f * Math.max(0, -s))
                .legAdd(1, KNEE, .3f * Math.max(0, -s)).add(LIFT, .25f * Math.abs(c));
    }

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
            default -> {
                // The spin runs out (from exactly where it was), the sword bursts, he staggers and comes back round to face ahead.
                float[] last = SPIN.get(c.entity());
                float phi0, omega0;
                if (last != null && IcemanWeaponFx.gameTime() - last[2] < 40) { phi0 = last[0]; omega0 = last[1]; }
                else { float held = k * SPIN_MAX; phi0 = -(spinTurned(held) % Mth.TWO_PI); omega0 = spinSpeed(held); }
                float td = 3, x = Math.min(t, td) / td;
                float phi = phi0 - omega0 * td * (x - x * x / 2);
                if (t > td) {
                    float stop = phi0 - omega0 * td / 2, target = Math.round(stop / Mth.TWO_PI) * Mth.TWO_PI;
                    phi = stop + (target - stop) * PantherMotion.ease((t - td) / 6);
                }
                Pose p = new Track(true).key(0, on(base, W_SPIN)).key(2, on(base, W_BREAK)).key(6, on(base, W_BREAK).add(CROUCH, -.8f).add(SPINE_PITCH, -.1f))
                        .key(SWORD_BREAK_TICKS, base).sample(t);
                p.set(ROOT_YAW, phi);
                float wob = (float) Math.exp(-t * .35f) * Mth.sin(t * 1.9f);
                p.add(ROOT_ROLL, .1f * wob).add(SHIFT_X, 1.2f * wob).add(HEAD_ROLL, -.08f * wob);
                p.legAdd(0, LEG_X, -.25f * Math.max(0, wob)).legAdd(1, LEG_X, -.25f * Math.max(0, -wob));
                return p;
            }
        }
    }
}
