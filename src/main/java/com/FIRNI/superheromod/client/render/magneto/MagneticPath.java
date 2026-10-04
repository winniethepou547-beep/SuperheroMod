package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.client.render.film.Film;
import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.core.cinematic.ActorPose.*;
import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.*;

/**
 * MAGNETIC EXECUTION as numbers: where everything is at film time t, pure functions only (MagneticStage draws them,
 * MagneticExecutionFilm frames them). Stage space: Magneto starts at the origin facing +z, the target stands at
 * T0 facing him; +x is the left of the frame when looking down +z. The giant X stands behind the target, centred on
 * CROSS: pillar A runs from Magneto's top right to his bottom left (the stroke of his right hand), pillar B from his
 * top left to his bottom right; both are drawn as SEGMENTS pieces each so they can bend round the target and crush
 * into a ball. Forty-four pieces of metal orbit him; the first LAUNCHED of them fly to the target and lock round its
 * wrists, ankles and chest.
 */
public final class MagneticPath {
    private MagneticPath() {}

    // ------------------------------------------------------------------ the stage
    public static final Vec3 T0 = new Vec3(0, 0, 9);
    /** The centre of the X, and where the target hangs pinned (its feet) and the ball it ends as. */
    public static final Vec3 CROSS = new Vec3(0, 5.0, 11.8);
    public static final Vec3 PIN = new Vec3(0, 3.9, 10.6);
    public static final float HALF = 7.6f, THICK = 1.1f, BEND = .8f;
    public static final int SEGMENTS = 8;
    /** Pillar axes (unit, top end at -axis) and how far each stands behind the cross centre. */
    static final Vec3[] AXIS = {new Vec3(.7071, -.7071, 0), new Vec3(-.7071, -.7071, 0)};
    static final float[] DEPTH = {-.3f, .3f};
    /** Where the ball is thrown: far up into the storm behind the X. */
    public static final Vec3 FAR = new Vec3(-26, 46, 210);
    public static final float HURL_TIME = 36;
    /** Magneto's walk toward the camera: speed (blocks/tick) and the turn before it. */
    public static final float WALK_SPEED = .065f;

    public static float clamp(float x) { return Mth.clamp(x, 0, 1); }
    public static float ease(float x) { x = clamp(x); return x * x * (3 - 2 * x); }
    static float k(float t, float a, float b) { return ease((t - a) / (b - a)); }
    static float hash(int n) { double x = Math.sin(n * 12.9898 + 78.233) * 43758.5453; return (float) (x - Math.floor(x)); }
    static float noise(float x) { return Mth.sin(x * 1.7f) * .5f + Mth.sin(x * 3.1f + 1.3f) * .3f + Mth.sin(x * 7.3f + 4.1f) * .2f; }
    public static float window(float t, float in, float out, float soft) { return Math.min(ease((t - in) / soft), ease((out - t) / soft)); }

    // ------------------------------------------------------------------ Magneto
    /** How far he has walked toward the camera (blocks), easing into the stride. */
    public static float walked(float t) {
        float tau = t - ULT_WALK;
        if (tau <= 0) return 0;
        return tau < 12 ? WALK_SPEED * tau * tau / 24 : WALK_SPEED * (tau - 6);
    }
    public static Vec3 magneto(float t) { return new Vec3(0, 0, -walked(t)); }
    /** Which way he faces (radians; 0 = +z, PI = toward the camera at -z): the slow turn of his back on them. */
    public static float yaw(float t) { return Mth.PI * ease((t - ULT_TURN) / (ULT_WALK - ULT_TURN)); }

    private static final Track GESTURES = gestures();
    private static final Pose REST = MagnetoMotion.stance(0);

    /** His whole body at t (time = the free clock for breathing). */
    public static Pose pose(float t, float time) {
        Pose p = GESTURES.sample(t);
        p.layer(MagnetoMotion.stance(time), REST, 1);
        // The walk underneath: long, unhurried strides; the right arm keeps still while it works.
        float d = walked(t), amount = clamp((t - ULT_WALK) / 12f);
        float turning = window(t, ULT_TURN + 2, ULT_WALK + 2, 4) * .45f;
        float phase = d * 2.66f + yaw(t) * 1.6f;
        float stride = Math.max(amount, turning);
        if (stride > .01f) {
            float cos = Mth.cos(phase), sin = Mth.sin(phase), still = 1 - window(t, ULT_FIST - 8, ULT_FLICK + 26, 6);
            for (int side = 0; side < 2; side++) {
                float sg = side == 0 ? 1 : -1;
                p.legAdd(side, LEG_X, sg * cos * .5f * stride).legAdd(side, KNEE, Math.max(0, sg * sin) * .55f * stride);
                p.armAdd(side, ARM_X, -sg * cos * .16f * stride * (side == 0 ? still : 1));
            }
            p.add(LIFT, .3f * Math.abs(sin) * stride).add(CHEST_YAW, -.05f * cos * stride);
        }
        return p;
    }
    /** Where his head looks (yaw, pitch; radians relative to the body). */
    public static float[] look(float t) {
        float up = -.12f * window(t, ULT_RAISE, ULT_LAUNCH + 6, 10) - .08f * window(t, ULT_PIN, ULT_TURN, 8);
        float down = .1f * window(t, 40, 58, 6);
        return new float[]{0, up + down};
    }

    private static Pose arms(Pose base, float rx, float rz, float ry, float elbow, float wrist, float curl, float fwd, float upSh) {
        Pose p = base.copy();
        p.arm(0, ARM_X, rx).arm(0, ARM_Z, rz).arm(0, ARM_Y, ry).arm(0, ELBOW, elbow).arm(0, WRIST_X, wrist).arm(0, CURL, curl).arm(0, SH_FWD, fwd).arm(0, SH_UP, upSh);
        return p;
    }
    private static Pose left(Pose p, float rx, float rz, float ry, float elbow, float wrist, float curl, float fwd, float upSh) {
        p.arm(1, ARM_X, rx).arm(1, ARM_Z, rz).arm(1, ARM_Y, ry).arm(1, ELBOW, elbow).arm(1, WRIST_X, wrist).arm(1, CURL, curl).arm(1, SH_FWD, fwd).arm(1, SH_UP, upSh);
        return p;
    }
    /**
     * Every gesture, one key pose after another (a held track: what rests stays put). Calm and minimal: small
     * movements of one hand at first, the slow raise, the push that sends the metal, both hands drawing apart, the X
     * drawn in two strokes, the push that pins them, the turn, the hand rising and closing into a fist, the flick.
     */
    private static Track gestures() {
        Pose rest = MagnetoMotion.stance(0);
        Pose stir = arms(rest, -.5f, .2f, 0, .95f, .3f, .3f, .3f, 0).add(CHEST_YAW, -.04f);
        Pose turnWrist = stir.copy().arm(0, WRIST_Z, .35f).arm(0, CURL, .15f).arm(0, ARM_X, -.58f);
        Pose sink = stir.copy().arm(0, ARM_X, -.4f).arm(0, CURL, .45f).arm(0, WRIST_X, .45f);
        Pose open = stir.copy().arm(0, CURL, .05f).arm(0, WRIST_X, -.1f);
        Pose high = arms(rest, -2.45f, .35f, .1f, .25f, -.45f, .12f, .4f, 1.0f).add(CHEST_PITCH, -.08f).add(SPINE_PITCH, -.03f);
        Pose highest = high.copy().arm(0, ARM_X, -2.6f).arm(0, CURL, .05f);
        Pose push = arms(rest, -1.55f, -.05f, 0, .05f, .35f, 0, 1.3f, .1f).add(CHEST_YAW, -.1f).add(SPINE_PITCH, .03f);
        Pose spread = left(arms(rest, -.47f, 1.68f, 0, .15f, -.2f, .8f, 1.0f, .12f), -.47f, 1.68f, 0, .15f, -.2f, .8f, 1.0f, .12f).add(CHEST_PITCH, -.06f);
        Pose wide = left(arms(rest, -.25f, 1.83f, .05f, .1f, -.25f, .9f, .9f, .66f), -.25f, 1.83f, .05f, .1f, -.25f, .9f, .9f, .66f).add(CHEST_PITCH, -.1f);
        // The X: both hands up and out, then the right sweeps down across to his left, then the left across to his right
        // (angles solved for where the hands should be: a raised arm goes outward with a negative side angle).
        Pose ready = left(arms(rest, -2.66f, -.63f, 0, .08f, -.2f, .15f, 1.2f, 1.2f), -2.66f, -.63f, 0, .08f, -.2f, .15f, 1.2f, 1.2f).add(CHEST_PITCH, -.08f);
        Pose rightDown = ready.copy().add(CHEST_YAW, .14f);
        rightDown.arm(0, ARM_X, -.51f).arm(0, ARM_Z, -.92f).arm(0, ARM_Y, -.13f).arm(0, ELBOW, .08f).arm(0, SH_FWD, 1.2f).arm(0, SH_UP, 0).arm(0, CURL, .25f);
        Pose bothDown = rightDown.copy().add(CHEST_YAW, -.26f);
        bothDown.arm(1, ARM_X, -.51f).arm(1, ARM_Z, -.92f).arm(1, ARM_Y, -.13f).arm(1, ELBOW, .08f).arm(1, SH_FWD, 1.2f).arm(1, SH_UP, 0).arm(1, CURL, .25f);
        Pose after = left(arms(rest, -.6f, -.25f, -.1f, .3f, .1f, .35f, .5f, 0), -.6f, -.25f, -.1f, .3f, .1f, .35f, .5f, 0);
        Pose pinPush = arms(after, -1.5f, .05f, 0, .15f, .5f, .05f, 1.2f, .1f).add(SPINE_PITCH, .02f);
        Pose chin = rest.copy().add(HEAD_PITCH, -.06f);
        // The fist: the right hand raised before his shoulder, open; closing slowly; the flick, out and away; down again.
        Pose hand = arms(rest, -1.65f, .3f, .05f, 1.55f, -.3f, .08f, .5f, .3f);
        Pose fist = hand.copy().arm(0, CURL, 1).arm(0, ELBOW, 1.65f).arm(0, WRIST_X, -.15f);
        Pose flick = arms(rest, -.95f, .75f, .45f, .4f, .55f, 0, .3f, .2f).add(CHEST_YAW, .05f);
        Pose flickOut = flick.copy().arm(0, ARM_X, -.75f).arm(0, ARM_Z, .6f);
        Pose twitch = rest.copy().arm(0, CURL, .6f).arm(0, WRIST_X, .25f);
        return new Track(true).key(0, rest).key(16, rest).key(24, stir).key(33, turnWrist).key(42, sink).key(52, open)
                .key(ULT_RAISE, open).key(ULT_RAISE + 26, high).key(ULT_LAUNCH - 3, highest).key(ULT_LAUNCH + 1, push).key(ULT_PULL - 4, push)
                .key(ULT_PULL + 10, spread).key(ULT_XCUT - 8, wide).key(ULT_XCUT - 2, ready).key(ULT_XCUT + 1, ready)
                .key(ULT_XCUT + 7, rightDown).key(ULT_XCUT + 9, rightDown).key(ULT_XCUT + 14, bothDown).key(ULT_SLAM + 2, bothDown)
                .key(ULT_PIN - 4, after).key(ULT_PIN + 3, pinPush).key(ULT_PIN + 12, pinPush).key(ULT_PIN + 24, chin)
                .key(ULT_FIST, rest).key(ULT_FIST + 12, hand).key(ULT_FLICK - 6, hand).key(ULT_FLICK - 1, fist)
                .key(ULT_FLICK + 3, flick).key(ULT_FLICK + 9, flickOut).key(ULT_FLICK + 30, rest)
                .key(ULT_HURL - 4, rest).key(ULT_HURL, twitch).key(ULT_HURL + 6, rest).key(ULT_TOTAL, rest);
    }
    /** How hard he is working the metal (0..1): the glow round his hands. */
    public static float effort(float t) {
        return Math.max(Math.max(.6f * window(t, ULT_RAISE + 10, ULT_LAUNCH + 4, 10), window(t, ULT_PULL, ULT_XCUT, 8)),
                Math.max(window(t, ULT_XCUT - 2, ULT_SLAM + 4, 3), Math.max(.7f * window(t, ULT_PIN - 2, ULT_PIN + 14, 4), window(t, ULT_FLICK - 6, ULT_BALL, 4))));
    }
    /** The cape: blown by the storm all through, a little more when he walks into it. */
    public static MagnetoBody.Cloth cloth(float t) {
        float gust = .18f + .1f * noise(t * .05f) + .06f * Mth.sin(t * .3f);
        return new MagnetoBody.Cloth(gust + .15f * clamp((t - ULT_WALK) / 20), .05f * Mth.cos(t * .3f), .25f + .2f * noise(t * .03f + 4), 0);
    }

    // ------------------------------------------------------------------ the orbiting metal
    public static final int PIECES = 44, LAUNCHED = 22;
    /** The orbits' speed through time: stirring with his hand, racing when he raises it, slow while he walks. */
    static float spin(float t) {
        float s = .6f + .35f * window(t, 20, 30, 4) + .45f * window(t, 30, 38, 3) + .3f * window(t, 46, 54, 3);
        s += 3.6f * k(t, ULT_RAISE, ULT_LAUNCH) * (1 - k(t, ULT_LAUNCH, ULT_LAUNCH + 40));
        s += 2.2f * window(t, ULT_XCUT - 2, ULT_SLAM + 6, 4);
        s -= .35f * k(t, ULT_TURN, ULT_WALK);
        s *= 1 - .9f * window(t, ULT_FIST + 8, ULT_FLICK, 6);
        s += 2.6f * window(t, ULT_FLICK, ULT_FLICK + 10, 2);
        return Math.max(.08f, s);
    }
    private static final float[] TURNED = new float[ULT_TOTAL + 2];
    static {
        float sum = 0;
        for (int i = 0; i < TURNED.length; i++) { TURNED[i] = sum; sum += spin(i + .5f); }
    }
    /** How far every orbit has turned by t (the integral of spin). */
    static float turned(float t) {
        if (t <= 0) return t * spin(0);
        int i = Math.min(TURNED.length - 2, (int) t);
        return TURNED[i] + (TURNED[i + 1] - TURNED[i]) * (t - i);
    }
    /** One orbiting piece's place round him (stage space). */
    public static Vec3 orbit(int i, float t) {
        float r = 1.5f + 2.8f * hash(i * 7 + 1), h = .35f + 3.0f * hash(i * 5 + 2), w = (.022f + .03f * hash(i * 3 + 3)) * (i % 3 == 0 ? -1 : 1);
        float tilt = (hash(i * 11 + 4) - .5f) * .7f, phase = hash(i * 13 + 5) * Mth.TWO_PI;
        float tight = 1 - .18f * k(t, ULT_RAISE, ULT_LAUNCH) - .3f * window(t, ULT_FIST + 6, ULT_FLICK, 6);
        r *= tight;
        h += .4f * k(t, ULT_RAISE, ULT_LAUNCH) * (1 - k(t, ULT_LAUNCH, ULT_PULL));
        float a = phase + w * turned(t) * 1.6f;
        float x = Mth.cos(a) * r, z = Mth.sin(a) * r;
        float y = h + z * tilt + .15f * Mth.sin(t * .05f + i);
        // The fist: they tremble where they hang.
        float shake = window(t, ULT_FIST + 14, ULT_FLICK, 3) * .03f;
        Vec3 m = magneto(t);
        return new Vec3(m.x + x + shake * noise(t * 3 + i), y + shake * noise(t * 3.3f + i * 2), m.z + z);
    }
    /** How a piece is turned (radians about x and z): tumbling slowly as it goes round. */
    public static float tumble(int i, float t) { return hash(i * 17) * 6.28f + turned(t) * (.02f + .03f * hash(i * 19)); }

    // ------------------------------------------------------------------ the target
    private static final FilmCast.Track TARGET = targetTrack();
    public static ActorPose targetPose(float t) {
        ActorPose p = TARGET.sample(t);
        float hold = window(t, ULT_PULL + 22, ULT_CRUSH + 6, 6), fade = 1 - .6f * k(t, ULT_PIN + 30, ULT_CRUSH);
        if (hold > 0) {
            // Straining against the iron: the chest heaves, the head turns, the limbs jerk in their clamps.
            float s = hold * fade;
            p.rot[CHEST][1] += (float) Math.toRadians(9 * noise(t * .21f) * s);
            p.rot[HEAD][1] += (float) Math.toRadians(22 * noise(t * .17f + 3) * s);
            p.rot[HEAD][0] += (float) Math.toRadians(8 * noise(t * .23f + 7) * s);
            p.rot[RIGHT_UPPER_ARM][2] += (float) Math.toRadians(5 * noise(t * .5f + 1) * s);
            p.rot[LEFT_UPPER_ARM][2] -= (float) Math.toRadians(5 * noise(t * .5f + 9) * s);
            p.rot[RIGHT_LOWER_LEG][0] += (float) Math.toRadians(10 * Math.max(0, noise(t * .4f + 2)) * s);
            p.rot[LEFT_LOWER_LEG][0] += (float) Math.toRadians(10 * Math.max(0, noise(t * .4f + 5)) * s);
        }
        return p;
    }
    private static FilmCast.Track targetTrack() {
        ActorPose ready = of().j(RIGHT_UPPER_ARM, -24, 0, 12).j(RIGHT_LOWER_ARM, -48, 0, 0).j(LEFT_UPPER_ARM, -22, 0, -12).j(LEFT_LOWER_ARM, -44, 0, 0)
                .j(RIGHT_UPPER_LEG, -10, 0, 6).j(RIGHT_LOWER_LEG, 14, 0, 0).j(LEFT_UPPER_LEG, 8, 0, -6).j(LEFT_LOWER_LEG, 8, 0, 0).crouch(.06f);
        ActorPose wary = of().j(CHEST, -6, 0, 0).j(HEAD, 6, 0, 0).j(RIGHT_UPPER_ARM, -62, 0, 18).j(RIGHT_LOWER_ARM, -80, 0, 0).j(LEFT_UPPER_ARM, -58, 0, -16)
                .j(LEFT_LOWER_ARM, -84, 0, 0).j(RIGHT_UPPER_LEG, -16, 0, 7).j(RIGHT_LOWER_LEG, 22, 0, 0).j(LEFT_UPPER_LEG, 14, 0, -7).j(LEFT_LOWER_LEG, 12, 0, 0).crouch(.12f);
        ActorPose struck = of().j(CHEST, -14, 0, 0).j(HEAD, -16, 0, 0).j(RIGHT_UPPER_ARM, -10, 0, 70).j(RIGHT_LOWER_ARM, -20, 0, 0).j(LEFT_UPPER_ARM, -14, 0, -66)
                .j(LEFT_LOWER_ARM, -24, 0, 0).j(RIGHT_UPPER_LEG, 6, 0, 16).j(RIGHT_LOWER_LEG, 18, 0, 0).j(LEFT_UPPER_LEG, 4, 0, -16).j(LEFT_LOWER_LEG, 16, 0, 0).crouch(.08f);
        ActorPose x = of().j(CHEST, -8, 0, 0).j(HEAD, -10, 0, 0).j(RIGHT_UPPER_ARM, 0, 0, 135).j(RIGHT_LOWER_ARM, -6, 0, 0).j(LEFT_UPPER_ARM, 0, 0, -135)
                .j(LEFT_LOWER_ARM, -6, 0, 0).j(RIGHT_UPPER_LEG, 0, 0, 42).j(RIGHT_LOWER_LEG, 4, 0, 0).j(LEFT_UPPER_LEG, 0, 0, -42).j(LEFT_LOWER_LEG, 4, 0, 0);
        ActorPose half = of().j(CHEST, -12, 0, 0).j(HEAD, -12, 0, 0).j(RIGHT_UPPER_ARM, -20, 0, 100).j(RIGHT_LOWER_ARM, -30, 0, 0).j(LEFT_UPPER_ARM, -24, 0, -96)
                .j(LEFT_LOWER_ARM, -34, 0, 0).j(RIGHT_UPPER_LEG, -6, 0, 26).j(RIGHT_LOWER_LEG, 20, 0, 0).j(LEFT_UPPER_LEG, -4, 0, -24).j(LEFT_LOWER_LEG, 22, 0, 0);
        ActorPose slammed = x.copy().j(CHEST, 6, 0, 0).j(HEAD, 18, 0, 0);
        ActorPose spent = x.copy().j(CHEST, 2, 0, 0).j(HEAD, 26, 8, 0).j(RIGHT_LOWER_ARM, -14, 0, 0).j(LEFT_LOWER_ARM, -12, 0, 0);
        return new FilmCast.Track().key(0, 0, ready).key(ULT_RAISE + 12, 14, wary).key(ULT_LAUNCH + 9, 3, struck, impactChain())
                .key(ULT_PULL + 12, 12, half, launchChain()).key(ULT_PULL + 30, 14, x).key(ULT_SLAM + 2, 2, x.copy().j(HEAD, -22, 0, 0))
                .key(ULT_SLAM + 10, 8, x).key(ULT_PIN + 9, 2, slammed, impactChain()).key(ULT_PIN + 24, 12, x).key(ULT_TURN + 40, 50, spent);
    }
    /** Where its feet are: standing, a step back, jolted, lifted, hanging, yanked back onto the X, pinned. */
    public static Vec3 targetFeet(float t) {
        double z = T0.z + .35 * k(t, ULT_RAISE + 6, ULT_RAISE + 20);
        double y = 0;
        // Each clamp lands with a jolt.
        for (int limb = 0; limb < 5; limb++) {
            float at = arrival(limb * 4), d = t - at;
            if (d > 0 && d < 8) z += .07 * Math.sin(d / 8 * Math.PI) * (1 - d / 8);
        }
        float lift = k(t, ULT_PULL + 4, ULT_PULL + 32);
        y += 1.6 * lift + .06 * Math.sin(t * .13) * lift;
        float slam = t - ULT_SLAM;
        if (slam > 0 && slam < 10) y -= .12 * Math.sin(slam / 10 * Math.PI);
        // Yanked back onto the X: accelerating, then the blow.
        float pull = clamp((t - ULT_PIN) / 9f);
        pull = pull * pull * pull;
        Vec3 free = new Vec3(0, y, z);
        Vec3 at = free.lerp(PIN, pull);
        float hit = t - ULT_PIN - 9;
        if (hit > 0 && hit < 8) at = at.add(0, 0, .12 * Math.sin(hit / 8 * Math.PI) * (1 - hit / 8));
        return at;
    }
    public static boolean targetShown(float t) { return t < ULT_CRUSH + 15; }
    /** Its four limb ends and its chest (0 right hand, 1 left hand, 2 right ankle, 3 left ankle, 4 chest), stage space. */
    public static Vec3 anchor(int limb, float t) {
        Vec3 f = targetFeet(t);
        float x = k(t, ULT_PULL + 4, ULT_PULL + 32);
        // It faces -z, so its right side is +x.
        double sx = limb == 4 ? 0 : (limb % 2 == 0 ? 1 : -1);
        double ox, oy;
        if (limb < 2) { ox = Mth.lerp(x, .36f, .8f); oy = Mth.lerp(x, .72f, 1.86f); }
        else if (limb < 4) { ox = Mth.lerp(x, .13f, .6f); oy = Mth.lerp(x, .07f, .22f); }
        else { ox = 0; oy = 1.12; }
        return f.add(sx * ox, oy, -.05);
    }

    // ------------------------------------------------------------------ the pieces sent at the target
    /** When launched piece j lands on the target. */
    public static float arrival(int j) { return ULT_LAUNCH + j * .45f + 9; }
    /** Which part of the target piece j is sent to (four to each limb, the last six round the chest). */
    public static int anchorOf(int j) { return j < 16 ? j / 4 : 4; }
    /** Launched piece j at t: still orbiting, in flight, or locked spinning round its limb (null once it is in the ball). */
    public static Vec3 launched(int j, float t) {
        float leave = ULT_LAUNCH + j * .45f, land = arrival(j);
        if (t < leave) return orbit(j, t);
        Vec3 lock = clampPoint(j, Math.max(t, land));
        if (t < land) {
            float u = (t - leave) / (land - leave), e = u * u * (3 - 2 * u) * .4f + u * .6f;
            Vec3 from = orbit(j, leave), mid = from.lerp(lock, .5).add(0, 1.4 + hash(j) * 1.2, 0);
            Vec3 a = from.lerp(mid, e), b = mid.lerp(lock, e);
            return a.lerp(b, e);
        }
        return toBall(lock, j, t);
    }
    /** Where a locked piece circles its limb. */
    static Vec3 clampPoint(int j, float t) {
        int limb = anchorOf(j);
        Vec3 c = anchor(limb, t);
        float r = limb == 4 ? .55f : .3f, a = hash(j * 3) * 6.28f + (t - arrival(j)) * (.15f + .35f * (float) Math.exp(-(t - arrival(j)) / 20f));
        float tilt = (hash(j * 5) - .5f) * 1.2f;
        return c.add(Mth.cos(a) * r, Mth.sin(a) * r * tilt + (limb == 4 ? (j - 18) * .12 : 0), Mth.sin(a) * r);
    }
    /** Anything drawn into the crush goes into the ball with it. */
    static Vec3 toBall(Vec3 p, int seed, float t) {
        float in = k(t, ULT_CRUSH + 4, ULT_BALL);
        if (in <= 0) return p;
        Vec3 c = ball(t);
        Vec3 dir = new Vec3(hash(seed * 7) - .5, hash(seed * 11) - .5, hash(seed * 13) - .5);
        dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : dir.normalize();
        return p.lerp(c.add(dir.scale(.75)), in);
    }

    // ------------------------------------------------------------------ the pillars
    /** One segment of a pillar at t: centre, axis (unit), the side it faces (unit), its scale and crumple; null = not there. */
    public record Segment(Vec3 centre, Vec3 axis, Vec3 side, float scale, float crumple) {}
    /** Pillar p (0 = A, right hand; 1 = B, left hand) drives in along its own axis from high in the storm. */
    public static float strokeStart(int p) { return p == 0 ? ULT_XCUT + 1 : ULT_XCUT + 4; }
    /** How far up its own axis it still is (blocks), 0 once it is in the ground. */
    public static float slide(int p, float t) {
        float u = clamp((t - strokeStart(p)) / (ULT_SLAM - strokeStart(p)));
        return 46 * (1 - u) * (1 - u);
    }
    public static boolean pillarShown(int p, float t) { return t >= strokeStart(p) && t < ULT_STAGE_END; }
    /** The centre the arms bend round and the ball forms at. */
    public static Vec3 core(float t) {
        if (t < ULT_BALL) return new Vec3(PIN.x, CROSS.y, CROSS.z - 1 / BEND);
        return ball(t);
    }
    public static Segment segment(int p, int k, float t) {
        if (!pillarShown(p, t)) return null;
        Vec3 axis = AXIS[p];
        float s = -HALF + 2 * HALF * (k + .5f) / SEGMENTS, len = Math.abs(s), sign = Math.signum(s);
        float attract = k(t, ULT_CRUSH, ULT_CRUSH + 8);
        Vec3 centre = CROSS.add(0, 0, DEPTH[p] * (1 - attract)).add(axis.scale(-slide(p, t)));
        float bend = BEND * k(t, ULT_CRUSH + 2, ULT_CRUSH + 16);
        Vec3 u = axis.scale(sign), w = new Vec3(0, 0, -1);
        Vec3 pos, tangent;
        if (bend < 1e-3f) { pos = centre.add(u.scale(len)); tangent = u; }
        else {
            float a = bend * len;
            pos = centre.add(u.scale(Mth.sin(a) / bend)).add(w.scale((1 - Mth.cos(a)) / bend));
            tangent = u.scale(Mth.cos(a)).add(w.scale(Mth.sin(a)));
        }
        // Compressed into the ball, then carried with it.
        float squeeze = k(t, ULT_CRUSH + 14, ULT_BALL);
        Vec3 o = new Vec3(PIN.x, CROSS.y, CROSS.z - 1 / BEND);
        pos = o.add(pos.subtract(o).scale(1 - .62f * squeeze));
        if (t >= ULT_BALL) pos = pos.subtract(o).add(ball(t));
        Vec3 side = tangent.cross(new Vec3(0, 0, 1));
        if (side.lengthSqr() < 1e-6) side = tangent.cross(new Vec3(1, 0, 0));
        side = side.normalize();
        return new Segment(pos, tangent.normalize().scale(sign), side, 1 - .42f * squeeze, squeeze * (.6f + .9f * hash(p * 31 + k)) * (k % 2 == 0 ? 1 : -1));
    }
    /** The two lower ends, where they bit into the ground. */
    public static Vec3 foot(int p) { return CROSS.add(0, 0, DEPTH[p]).add(AXIS[p].scale(HALF)); }
    /** How hot the pillars' edges glow (0..1). */
    public static float heat(float t) {
        float fall = .55f * window(t, ULT_XCUT + 1, ULT_SLAM, 2);
        float slam = t >= ULT_SLAM ? .45f + .55f * (float) Math.exp(-(t - ULT_SLAM) / 10f) : 0;
        float crush = .45f * window(t, ULT_CRUSH, ULT_BALL + 20, 4);
        return Mth.clamp(fall + slam + crush, 0, 1) * (t > ULT_HURL + HURL_TIME ? 0 : 1);
    }

    // ------------------------------------------------------------------ the ball
    public static Vec3 ball(float t) {
        Vec3 o = new Vec3(PIN.x, CROSS.y, CROSS.z - 1 / BEND);
        if (t < ULT_HURL) {
            float shake = window(t, ULT_BALL, ULT_HURL, 2) * .05f;
            return o.add(shake * noise(t * 4), shake * noise(t * 4.4f + 2) + .3 * k(t, ULT_BALL, ULT_HURL), 0);
        }
        float x = clamp((t - ULT_HURL) / HURL_TIME), s = 1 - (float) Math.pow(1 - x, 2.2);
        Vec3 from = o.add(0, .3, 0);
        return from.lerp(FAR, s).add(0, 9 * Math.sin(s * Math.PI), 0);
    }
    public static boolean ballFlying(float t) { return t >= ULT_HURL && t < ULT_HURL + HURL_TIME; }

    // ------------------------------------------------------------------ the storm
    /** A lightning strike: when, where (stage space), its seed, power, and whether it comes down close (drawn in 3D). */
    public record Strike(float time, Vec3 at, int seed, float power, boolean near) {}
    public static final Strike[] STRIKES = {
            new Strike(6, new Vec3(70, 0, 120), 3, .7f, false), new Strike(29, new Vec3(-95, 0, 60), 7, .55f, false),
            new Strike(48, new Vec3(30, 0, -110), 11, .5f, false), new Strike(ULT_RAISE, new Vec3(-24, 0, 34), 13, 1, true),
            new Strike(84, new Vec3(80, 0, 70), 17, .6f, false), new Strike(121, new Vec3(-60, 0, 130), 19, .65f, false),
            new Strike(143, new Vec3(110, 0, -40), 23, .5f, false), new Strike(ULT_SLAM, new Vec3(0, 0, CROSS.z + 1.5), 29, 1.3f, true),
            new Strike(204, new Vec3(-80, 0, -90), 31, .6f, false), new Strike(231, new Vec3(50, 0, 140), 37, .7f, false),
            new Strike(259, new Vec3(-110, 0, 20), 41, .55f, false), new Strike(289, new Vec3(90, 0, 100), 43, .6f, false),
            new Strike(ULT_FLICK, new Vec3(-30, 0, 70), 47, .8f, false), new Strike(331, new Vec3(70, 0, -80), 53, .5f, false),
            new Strike(ULT_HURL + HURL_TIME, FAR.multiply(1, 0, 1), 59, 1.1f, false), new Strike(395, new Vec3(-60, 0, 150), 61, .6f, false)};
    /** A flash's light over time: a bright crack, a dip, a second flicker, then dying away. */
    public static float flicker(float tau) {
        if (tau < 0) return 0;
        if (tau < 1.5f) return 1;
        if (tau < 3) return .3f;
        if (tau < 4.5f) return .8f;
        return (float) Math.exp(-(tau - 4.5f) / 3);
    }
    /** The strongest strike now (or null). */
    public static Strike strike(float t) {
        Strike best = null; float light = .02f;
        for (Strike s : STRIKES) { float l = s.power() * flicker(t - s.time()); if (l > light) { light = l; best = s; } }
        return best;
    }
    public static float flash(float t) {
        float f = 0;
        for (Strike s : STRIKES) f = Math.max(f, s.power() * flicker(t - s.time()));
        return f;
    }

    // ------------------------------------------------------------------ the camera
    private record Shot(float start, float end, Vec3 fromA, Vec3 fromB, Vec3 atA, Vec3 atB, float fovA, float fovB, boolean follow) {}
    private static Shot shot(float s, float e, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float f0, float f1) { return new Shot(s, e, a, b, c, d, f0, f1, false); }
    private static Shot follow(float s, float e, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float f0, float f1) { return new Shot(s, e, a, b, c, d, f0, f1, true); }
    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }
    /**
     * The shots. Slow and steady: the wide wasteland, his hand among the metal, the raise from below, over his shoulder
     * as the metal flies, the target torn open, the X drawn, the pillars driven in from behind him, the pin, the image
     * of the X on its mound, the turn, the walk toward the lens (follow = positions relative to where he has walked),
     * the fist, the crush far behind him, the ball thrown into the storm, his face.
     */
    private static final Shot[] SHOTS = {
            shot(0, 40, v(-15, 5.5, -11), v(-12, 4.4, -8.2), v(0, 2.6, 5), v(0, 2.3, 4.2), 54, 50),
            shot(40, ULT_RAISE, v(-1.95, 1.35, 1.75), v(-1.6, 1.3, 1.4), v(-.3, 1.15, .1), v(-.32, 1.2, .1), 40, 38),
            shot(ULT_RAISE, ULT_LAUNCH, v(2.6, .45, 3.4), v(-1.9, .6, 3.8), v(0, 2.0, 0), v(0, 2.5, 0), 52, 46),
            shot(ULT_LAUNCH, ULT_PULL, v(-1.35, 2.3, -2.8), v(-1.0, 2.15, -2.0), v(0, 1.4, 9), v(0, 1.5, 9), 50, 56),
            shot(ULT_PULL, ULT_XCUT - 4, v(-2.7, 1.8, 5.3), v(-2.1, 2.7, 5.9), v(0, 1.4, 9.3), v(0, 2.7, 9.3), 52, 48),
            shot(ULT_XCUT - 4, ULT_XCUT + 8, v(.55, 1.75, 3.3), v(.5, 1.8, 3.1), v(0, 1.7, 0), v(0, 1.65, 0), 52, 50),
            shot(ULT_XCUT + 8, ULT_PIN, v(1.6, .8, -5.6), v(1.25, .9, -5.0), v(0, 5.2, 11), v(0, 4.6, 11), 66, 62),
            shot(ULT_PIN, ULT_PIN + 18, v(-4.6, 3.5, 6.0), v(-4.0, 4.2, 6.8), v(0, 4.6, 10.6), v(0, 4.9, 10.6), 50, 48),
            shot(ULT_PIN + 18, ULT_TURN, v(1.7, .9, -9.2), v(1.6, 1.0, -8.5), v(0, 4.4, 11), v(0, 4.6, 11), 46, 44),
            shot(ULT_TURN, ULT_WALK, v(3.4, 1.9, 2.2), v(.75, 1.75, -3.6), v(0, 1.75, 0), v(0, 2.6, 6), 48, 44),
            follow(ULT_WALK, ULT_FIST, v(.75, 1.75, -3.6), v(.7, 1.8, -3.1), v(0, 2.6, 6), v(0, 2.5, 5), 44, 42),
            follow(ULT_FIST, ULT_FLICK, v(1.25, 2.0, -1.7), v(1.15, 2.02, -1.5), v(.45, 1.95, -.15), v(.45, 2.0, -.1), 38, 36),
            follow(ULT_FLICK, ULT_HURL + 2, v(-1.1, 1.6, -6.2), v(-1.0, 1.65, -5.8), v(.2, 3.0, 13), v(.2, 3.4, 13), 34, 32),
            follow(ULT_HURL + 2, 378, v(.95, 1.35, -4.4), v(.9, 1.4, -4.0), v(0, 5, 14), v(-1.5, 12, 30), 50, 54),
            follow(378, ULT_STAGE_END, v(.3, 1.95, -2.3), v(.22, 1.92, -1.4), v(0, 1.85, 0), v(0, 1.85, 0), 44, 40)};
    public static Film.View view(float t) {
        Shot s = SHOTS[SHOTS.length - 1];
        for (Shot shot : SHOTS) if (t < shot.end()) { s = shot; break; }
        float u = ease((t - s.start()) / (s.end() - s.start()));
        Vec3 from = s.fromA().lerp(s.fromB(), u), at = s.atA().lerp(s.atB(), u);
        if (s.follow()) { Vec3 m = magneto(t); from = from.add(m); at = at.add(m.x, 0, m.z * (s.atA().z > 8 ? 0 : 1)); }
        // A slow breathing drift so no shot is ever dead still.
        from = from.add(.03 * noise(t * .05f), .02 * noise(t * .043f + 3), 0);
        return new Film.View(from, at, Mth.lerp(u, s.fovA(), s.fovB()), 0);
    }
    /** Shakes: the slam, the pin, the crush, the throw; small ones for the clamps landing. */
    public static float[] kick(float t) {
        float yaw = 0, pitch = 0, roll = 0, fov = 0;
        float[][] hits = {{ULT_SLAM, 3.2f}, {ULT_PIN + 9, 1.4f}, {ULT_CRUSH + 6, .9f}, {ULT_BALL, 1.2f}, {ULT_HURL, .7f}, {arrival(0), .35f}, {arrival(8), .35f}};
        for (float[] h : hits) {
            float d = t - h[0];
            if (d < 0 || d > 22) continue;
            float a = h[1] * (float) Math.exp(-d / 5f);
            yaw += a * noise(d * 1.9f + h[0]) * 1.3f;
            pitch += a * (noise(d * 2.3f + h[0] * 2) * 1.1f - (d < 2 ? .8f : 0));
            roll += a * noise(d * 1.7f + h[0] * 3) * .8f;
            fov += d < 3 ? h[1] * 1.6f * (1 - d / 3) : 0;
        }
        return yaw == 0 && pitch == 0 && roll == 0 && fov == 0 ? null : new float[]{yaw, pitch, roll, fov};
    }
}
