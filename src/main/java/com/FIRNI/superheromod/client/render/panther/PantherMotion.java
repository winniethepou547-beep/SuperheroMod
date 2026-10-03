package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.heroes.panther.PantherPath;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * Black Panther's body as numbers: every joint of every move, as pure functions of the action's clock.
 * A move is a track of key poses that the body passes through on a smooth curve (velocity carries on
 * through each key, so a strike flows out of its wind-up into its follow-through instead of stopping
 * at every pose). Everything starts from his stance: low, hunched forward, knees bent, one foot ahead,
 * arms open with the claws ready, alive with tiny movements.
 *
 * Signs (model space, as in PantherBody): positive pitch of the root, spine or chest leans forward;
 * positive yaw turns the front toward his right; positive roll leans toward his left; arm X negative
 * raises the arm forward, arm Z positive takes it out to the side, arm Y positive swings a raised arm
 * outward (all per side, mirror-symmetric); leg X negative lifts the leg forward, leg Z positive out to the side, leg Y
 * positive turns the toes out; knee and elbow positive bend; ankle positive points the toes; curl 0 =
 * claws spread, 1 = fist. Angles in radians, offsets in pixels (1/16 block).
 */
public final class PantherMotion {
    // ------------------------------------------------------------------ the pose
    public static final int LIFT = 0, ROOT_PITCH = 1, ROOT_YAW = 2, ROOT_ROLL = 3, SHIFT_X = 4, SHIFT_Z = 5,
            CROUCH = 6, PELVIS_YAW = 7, PELVIS_PITCH = 8, PELVIS_ROLL = 9,
            SPINE_YAW = 10, SPINE_PITCH = 11, SPINE_ROLL = 12, CHEST_YAW = 13, CHEST_PITCH = 14, CHEST_ROLL = 15,
            HEAD_YAW = 16, HEAD_PITCH = 17, HEAD_ROLL = 18, NECK = 19, PLANT = 20, EYES = 21;
    /** Per arm (add R or L): shoulder forward and up (px), arm X/Y/Z, elbow, wrist X/Z, curl. */
    public static final int SH_FWD = 0, SH_UP = 1, ARM_X = 2, ARM_Y = 3, ARM_Z = 4, ELBOW = 5, WRIST_X = 6, WRIST_Z = 7, CURL = 8;
    /** Per leg (add RL or LL): leg X/Y/Z, knee, ankle. */
    public static final int LEG_X = 0, LEG_Y = 1, LEG_Z = 2, KNEE = 3, ANKLE = 4;
    public static final int R = 22, L = 31, RL = 40, LL = 45, SIZE = 50;

    public static final class Pose {
        public final float[] v = new float[SIZE];
        public Pose copy() { Pose p = new Pose(); System.arraycopy(v, 0, p.v, 0, SIZE); return p; }
        public float get(int i) { return v[i]; }
        public Pose set(int i, float x) { v[i] = x; return this; }
        public Pose add(int i, float x) { v[i] += x; return this; }
        /** Set a value on one side's arm (side 0 right, 1 left). */
        public Pose arm(int side, int joint, float x) { v[(side == 0 ? R : L) + joint] = x; return this; }
        public Pose armAdd(int side, int joint, float x) { v[(side == 0 ? R : L) + joint] += x; return this; }
        public Pose leg(int side, int joint, float x) { v[(side == 0 ? RL : LL) + joint] = x; return this; }
        public Pose legAdd(int side, int joint, float x) { v[(side == 0 ? RL : LL) + joint] += x; return this; }
        public float arm(int side, int joint) { return v[(side == 0 ? R : L) + joint]; }
        public float leg(int side, int joint) { return v[(side == 0 ? RL : LL) + joint]; }
        /** Move every joint toward another pose by k. */
        public void toward(Pose o, float k) { for (int i = 0; i < SIZE; i++) v[i] += (o.v[i] - v[i]) * k; }
        /** Add another pose's difference from a base, scaled (an additive layer). */
        public void layer(Pose o, Pose base, float k) { for (int i = 0; i < SIZE; i++) v[i] += (o.v[i] - base.v[i]) * k; }
        /** The same pose with left and right swapped (a strike with the other hand). */
        public Pose mirror() {
            Pose m = copy();
            for (int j = 0; j < 9; j++) { m.v[R + j] = v[L + j]; m.v[L + j] = v[R + j]; }
            for (int j = 0; j < 5; j++) { m.v[RL + j] = v[LL + j]; m.v[LL + j] = v[RL + j]; }
            for (int i : new int[]{ROOT_YAW, ROOT_ROLL, PELVIS_YAW, PELVIS_ROLL, SPINE_YAW, SPINE_ROLL, CHEST_YAW, CHEST_ROLL, HEAD_YAW, HEAD_ROLL}) m.v[i] = -v[i];
            // Arm and leg Y and Z are already mirror-symmetric per side (the body flips them for the left), so swapping is enough.
            m.v[SHIFT_X] = -v[SHIFT_X];
            return m;
        }
    }

    // ------------------------------------------------------------------ tracks: key poses on a smooth curve
    private record Key(float t, Pose p) {}
    /** A move: key poses at times; sampled on a cubic curve through them (each key's speed set by its neighbours). */
    static final class Track {
        private final List<Key> keys = new ArrayList<>();
        Track key(float t, Pose p) { keys.add(new Key(t, p)); return this; }
        Pose sample(float t) {
            int n = keys.size();
            if (t <= keys.get(0).t) return keys.get(0).p.copy();
            if (t >= keys.get(n - 1).t) return keys.get(n - 1).p.copy();
            int i = 0;
            while (i < n - 2 && t > keys.get(i + 1).t) i++;
            Key a = keys.get(i), b = keys.get(i + 1);
            float h = b.t - a.t, s = (t - a.t) / h;
            float s2 = s * s, s3 = s2 * s;
            float h00 = 2 * s3 - 3 * s2 + 1, h10 = s3 - 2 * s2 + s, h01 = -2 * s3 + 3 * s2, h11 = s3 - s2;
            Pose out = new Pose();
            for (int j = 0; j < SIZE; j++) {
                float ma = tangent(i, j), mb = tangent(i + 1, j);
                out.v[j] = h00 * a.p.v[j] + h10 * h * ma + h01 * b.p.v[j] + h11 * h * mb;
            }
            return out;
        }
        /** The speed through a key: from the key before to the key after; the first and last keys start and end at rest. */
        private float tangent(int i, int j) {
            if (i <= 0 || i >= keys.size() - 1) return 0;
            Key p = keys.get(i - 1), q = keys.get(i + 1);
            return (q.p.v[j] - p.p.v[j]) / (q.t - p.t);
        }
    }

    private PantherMotion() {}

    public static float clamp(float x) { return Mth.clamp(x, 0, 1); }
    public static float ease(float x) { x = clamp(x); return x * x * (3 - 2 * x); }
    /** 0 before a, 1 after b, eased between. */
    public static float k(float t, float a, float b) { return ease((t - a) / (b - a)); }
    /** A fast start that settles (ease out). */
    public static float snap(float t, float a, float b) { float x = clamp((t - a) / (b - a)); return 1 - (1 - x) * (1 - x) * (1 - x); }
    private static float noise(float x) { return Mth.sin(x * 1.7f) * .5f + Mth.sin(x * 3.1f + 1.3f) * .3f + Mth.sin(x * 7.3f + 4.1f) * .2f; }

    // ------------------------------------------------------------------ the stance
    /**
     * Standing tall and easy, as on the poster: legs long and straight, the weight on the right leg, the left foot
     * a little ahead and out, the hips settled over it; the chest up, the shoulders broad; the arms hanging a
     * little away from the body, elbows soft, the hands half open with the claws showing. Alive with tiny
     * movements. combat (0..1) makes him a little readier (knees softer, hands up a touch); reflex (0..1) adds
     * the twitch-ready quickness (the guard itself is guard()).
     */
    public static Pose stance(float time, float combat, float reflex) {
        Pose p = new Pose();
        float c = combat;
        p.set(CROUCH, .9f * c).set(PELVIS_YAW, -.07f).set(PELVIS_PITCH, .02f).set(PELVIS_ROLL, .035f)
                .set(SPINE_PITCH, .02f + .08f * c).set(SPINE_ROLL, -.02f).set(CHEST_PITCH, .06f * c).set(CHEST_YAW, .05f)
                .set(HEAD_PITCH, -.02f - .08f * c).set(NECK, .2f + .2f * c).set(PLANT, 1).set(EYES, .1f);
        // The weight on the right leg (straight); the left a little forward, out and relaxed.
        p.leg(0, LEG_X, .04f).leg(0, LEG_Z, .05f).leg(0, LEG_Y, .1f).leg(0, KNEE, .02f + .12f * c);
        p.leg(1, LEG_X, -.13f).leg(1, LEG_Z, .07f).leg(1, LEG_Y, .16f).leg(1, KNEE, .1f + .12f * c);
        // Arms a little away from the body, elbows soft, hands half open, claws showing.
        for (int side = 0; side < 2; side++) {
            p.arm(side, SH_FWD, .2f + .4f * c).arm(side, ARM_X, -.06f - .34f * c).arm(side, ARM_Z, .27f + .04f * c).arm(side, ARM_Y, .05f)
                    .arm(side, ELBOW, .25f + .5f * c).arm(side, WRIST_X, -.05f).arm(side, WRIST_Z, .05f).arm(side, CURL, .55f - .15f * c);
        }
        // Alive: breathing, a slow drift of the weight, the head never quite still, the claws flexing now and then.
        float breath = Mth.sin(time * .1f);
        p.add(CHEST_PITCH, -.02f * breath).add(SPINE_PITCH, -.008f * breath);
        for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, .16f * breath).armAdd(side, ARM_Z, .012f * breath);
        float shift = noise(time * .019f);
        p.add(SHIFT_X, .2f * shift).add(PELVIS_ROLL, -.015f * shift);
        p.legAdd(1, KNEE, .03f * Math.max(0, shift));
        p.add(HEAD_YAW, .03f * noise(time * .025f + 4)).add(HEAD_PITCH, .018f * noise(time * .031f + 7));
        for (int side = 0; side < 2; side++) {
            float flex = Math.max(0, Mth.sin(time * .045f + side * 2.4f));
            flex = flex * flex * flex * flex * flex * flex;
            p.armAdd(side, CURL, -.25f * flex).armAdd(side, WRIST_X, -.06f * flex).armAdd(side, ELBOW, .03f * noise(time * .04f + side * 3));
        }
        if (reflex > .01f) {
            float q = reflex;
            p.add(HEAD_YAW, .04f * q * noise(time * .9f)).add(HEAD_PITCH, .03f * q * noise(time * .7f + 3));
            for (int side = 0; side < 2; side++) p.armAdd(side, WRIST_X, .07f * q * noise(time * 1.1f + side * 2)).armAdd(side, CURL, .06f * q * noise(time * 1.3f + side));
            p.add(EYES, .25f * q);
        }
        return p;
    }
    /**
     * The reflex guard (after Daredevil's deflecting stance): low and wide on the balls of the feet, forearms up
     * in front, the right one standing before the face, the left across the chest, claws out, the head down
     * behind them; the claws never still, flicking back and forth like a parry waiting to happen.
     */
    public static Pose guard(Pose base, float time, float weight) {
        if (weight <= .001f) return base;
        Pose g = base.copy();
        g.set(CROUCH, 3.2f).set(PELVIS_YAW, -.3f).set(SPINE_PITCH, .2f).set(CHEST_PITCH, .14f).set(CHEST_YAW, .2f).set(HEAD_PITCH, -.3f)
                .set(HEAD_YAW, .1f).set(NECK, .5f);
        g.leg(0, LEG_X, .3f).leg(0, LEG_Z, .2f).leg(0, KNEE, .25f).leg(0, ANKLE, .2f).leg(1, LEG_X, -.32f).leg(1, LEG_Z, .16f).leg(1, KNEE, .3f).leg(1, ANKLE, .25f);
        g.arm(0, SH_FWD, 1.2f).arm(0, ARM_X, -1.5f).arm(0, ARM_Z, -.12f).arm(0, ARM_Y, -.2f).arm(0, ELBOW, 1.8f).arm(0, WRIST_X, .25f).arm(0, CURL, .1f);
        g.arm(1, SH_FWD, 1.0f).arm(1, ARM_X, -1.05f).arm(1, ARM_Z, .08f).arm(1, ARM_Y, -.35f).arm(1, ELBOW, 1.65f).arm(1, WRIST_X, .2f).arm(1, CURL, .1f);
        // The claws flicking, the hands taking turns, quick and small.
        for (int side = 0; side < 2; side++) {
            float f = Mth.sin(time * 1.25f + side * Mth.PI);
            g.armAdd(side, WRIST_Z, .3f * f).armAdd(side, ARM_Y, .06f * f).armAdd(side, SH_UP, .2f * Math.abs(f));
        }
        g.add(SHIFT_X, .25f * Mth.sin(time * .3f)).add(CHEST_YAW, .04f * Mth.sin(time * .43f));
        Pose out = base.copy();
        out.toward(g, clamp(weight));
        return out;
    }
    /** The second jump: a tight front flip, knees to the chest, arms wrapped round them, opening out as it ends. */
    public static void flipJump(Pose p, float age) {
        if (age < 0 || age > 11) return;
        float k = ease(age / 9f), tuck = (float) Math.sin(Math.PI * clamp(age / 9f));
        p.add(ROOT_PITCH, Mth.TWO_PI * k).set(PLANT, 0).add(SPINE_PITCH, .4f * tuck).add(HEAD_PITCH, .3f * tuck);
        for (int side = 0; side < 2; side++) {
            p.leg(side, LEG_X, Mth.lerp(tuck, p.leg(side, LEG_X), -1.35f)).leg(side, KNEE, Mth.lerp(tuck, p.leg(side, KNEE), 1.9f))
                    .arm(side, ARM_X, Mth.lerp(tuck, p.arm(side, ARM_X), -1.2f)).arm(side, ELBOW, Mth.lerp(tuck, p.arm(side, ELBOW), 1.5f))
                    .arm(side, ARM_Z, Mth.lerp(tuck, p.arm(side, ARM_Z), .3f));
        }
    }

    // ------------------------------------------------------------------ sampling
    /** Inputs a move needs besides its clock. */
    public record Ctx(float time, float combat, float reflex, int flags, float threatYaw, float released) {}

    public static Pose sample(int action, float t, Ctx c) {
        Pose base = stance(c.time(), c.combat(), c.reflex());
        // While the reflex is on, the guard is his stance (and every dodge comes back to it).
        if (c.reflex() > .01f && (action == IDLE || action == DODGE)) base = guard(base, c.time(), c.reflex());
        Pose p = switch (action) {
            case CLAW_RIGHT -> clawSingle(base, false).sample(t);
            case CLAW_LEFT -> clawSingle(base, true).sample(t);
            case CLAW_DOUBLE -> clawDouble(base).sample(t);
            case CLAW_UPPER -> uppercut(base).sample(t);
            case FRENZY -> frenzy(base, t);
            case POUNCE_LOAD -> load(base).sample(t);
            case POUNCE -> pounce(base, t);
            case POUNCE_FLIP -> flip(base, t);
            case POUNCE_KICK -> kick(base).sample(t);
            case POUNCE_LAND -> land(base).sample(t);
            case POUNCE_MISS -> miss(base, t);
            case SPIN_LOAD -> spinLoad(base).sample(t);
            case SPIN -> spin(base, t);
            case SPIN_LAND -> spinLand(base).sample(t);
            case RELEASE_CHARGE -> charge(base, t, c.released());
            case RELEASE -> releasePose(base, t);
            case RELEASE_RECOVER -> recover(base).sample(t);
            case DODGE -> dodge(base, c.flags(), c.threatYaw()).sample(t);
            case SNEAK -> crouch(base, t, c.time());
            case DASH -> dash(base, t);
            case CROSS -> cross(base).sample(t);
            default -> base;
        };
        return p;
    }

    // ------------------------------------------------------------------ left click: the claws
    /**
     * One claw (the right; the left is its mirror): a blink of wind-up (the striking shoulder goes back, the
     * chest turns away, the weight sits on the back foot), the slash from high outside to low across the
     * body (shoulder, chest, hips and the back foot's push all driving it, the wrist snapping through), the
     * follow-through carrying the arm on across, then easing back into the stance. The head stays on the target.
     */
    static Track clawSingle(Pose base, boolean left) {
        Pose load = base.copy(), hit = base.copy(), through = base.copy(), settle = base.copy();
        // Wind-up: the right arm thrown far back, high and wide, the chest wrung round to his right, the weight
        // sinking onto the back leg, the left hand up in front, the left foot already stepping in.
        load.add(CHEST_YAW, .62f).add(SPINE_YAW, .16f).add(PELVIS_YAW, .16f).add(SHIFT_X, -.7f).add(CROUCH, 1.6f).add(SPINE_PITCH, .12f)
                .add(CHEST_ROLL, -.08f)
                .arm(0, SH_FWD, -1.1f).arm(0, SH_UP, .8f).arm(0, ARM_X, -2.45f).arm(0, ARM_Z, 1.35f).arm(0, ARM_Y, .55f).arm(0, ELBOW, 1.5f)
                .arm(0, WRIST_X, -.6f).arm(0, WRIST_Z, -.5f).arm(0, CURL, 0)
                .arm(1, ARM_X, -1.0f).arm(1, ELBOW, 1.35f).arm(1, ARM_Z, .2f).arm(1, SH_FWD, .6f)
                .leg(0, KNEE, .35f).leg(0, LEG_X, .3f).leg(1, LEG_X, -.35f).leg(1, KNEE, .35f).add(HEAD_YAW, -.6f);
        // The cut: one great diagonal from high outside to low across the body; chest, hips and the back foot drive it.
        hit.add(CHEST_YAW, -.72f).add(SPINE_YAW, -.22f).add(PELVIS_YAW, -.2f).add(SHIFT_X, .8f).add(SHIFT_Z, -1.1f).add(CROUCH, 2.5f)
                .add(SPINE_PITCH, .28f).add(CHEST_ROLL, .14f)
                .arm(0, SH_FWD, 2.3f).arm(0, ARM_X, -1.05f).arm(0, ARM_Z, -.6f).arm(0, ARM_Y, -.5f).arm(0, ELBOW, .22f)
                .arm(0, WRIST_X, .12f).arm(0, WRIST_Z, .55f).arm(0, CURL, 0)
                .arm(1, ARM_X, -.15f).arm(1, ARM_Z, .7f).arm(1, ELBOW, 1.4f).arm(1, SH_FWD, -.6f)
                .leg(1, LEG_X, -.52f).leg(1, KNEE, .58f).leg(0, LEG_X, .48f).leg(0, ANKLE, .5f).leg(0, KNEE, .22f).add(HEAD_YAW, .75f);
        // Follow-through: the arm carries on low and across, the body still turning.
        through.add(CHEST_YAW, -.85f).add(SPINE_YAW, -.24f).add(PELVIS_YAW, -.24f).add(SHIFT_X, .7f).add(SHIFT_Z, -.8f).add(CROUCH, 2.2f)
                .add(SPINE_PITCH, .24f)
                .arm(0, SH_FWD, 1.7f).arm(0, ARM_X, -.5f).arm(0, ARM_Z, -.8f).arm(0, ARM_Y, -.65f).arm(0, ELBOW, .5f)
                .arm(0, WRIST_X, .28f).arm(0, WRIST_Z, .3f).arm(0, CURL, .2f)
                .arm(1, ARM_X, -.3f).arm(1, ELBOW, 1.3f).leg(1, LEG_X, -.48f).leg(1, KNEE, .5f).leg(0, LEG_X, .4f).leg(0, ANKLE, .35f).add(HEAD_YAW, .85f);
        settle.add(CHEST_YAW, -.15f).add(CROUCH, .9f).arm(0, ARM_X, -.5f).arm(0, ELBOW, .7f).leg(1, LEG_X, -.25f);
        Track tr = new Track().key(0, base).key(1.4f, load).key(CLAW_HIT, hit).key(5.4f, through).key(7.6f, settle).key(CLAW_TICKS + 1.5f, base);
        return left ? mirrorKeys(tr, base) : tr;
    }
    /** A track with every pose mirrored except that it starts and ends in the real stance. */
    private static Track mirrorKeys(Track tr, Pose base) {
        Track m = new Track();
        int n = tr.keys.size();
        for (int i = 0; i < n; i++) {
            Key k = tr.keys.get(i);
            if (i == 0 || i == n - 1) { m.key(k.t, base); continue; }
            // Mirror the move's change from the stance, not the stance itself (his left foot stays ahead).
            Pose d = k.p.copy();
            Pose mirrored = base.copy();
            Pose delta = new Pose();
            for (int j = 0; j < SIZE; j++) delta.v[j] = d.v[j] - base.v[j];
            mirrored.layer(delta.mirror(), new Pose(), 1);
            m.key(k.t, mirrored);
        }
        return m;
    }
    /**
     * Both claws: the body drops for an instant (arms drawn back to the hips), then the shoulders drive both
     * hands forward together, the torso and hips going with them, a step in.
     */
    static Track clawDouble(Pose base) {
        Pose open = base.copy(), hit = base.copy(), through = base.copy();
        // Both arms flung up and wide, the chest opened, rising onto the toes...
        open.add(CROUCH, .6f).add(SPINE_PITCH, -.08f).add(CHEST_PITCH, -.2f).add(HEAD_PITCH, -.15f).add(LIFT, .5f);
        for (int side = 0; side < 2; side++)
            open.arm(side, SH_FWD, -1f).arm(side, SH_UP, 1f).arm(side, ARM_X, -2.2f).arm(side, ARM_Z, 1.25f).arm(side, ARM_Y, .45f).arm(side, ELBOW, 1.0f)
                    .arm(side, WRIST_X, -.6f).arm(side, CURL, 0);
        open.leg(0, ANKLE, .35f).leg(1, ANKLE, .35f);
        // ...then both claws crash down and in together, crossing in front, a long step in, the whole back behind it.
        hit.add(CROUCH, 3.2f).add(SPINE_PITCH, .42f).add(CHEST_PITCH, .16f).add(SHIFT_Z, -1.6f).add(HEAD_PITCH, -.3f);
        for (int side = 0; side < 2; side++)
            hit.arm(side, SH_FWD, 2.4f).arm(side, ARM_X, -1.0f).arm(side, ARM_Z, -.42f).arm(side, ARM_Y, -.3f).arm(side, ELBOW, .25f)
                    .arm(side, WRIST_X, .25f).arm(side, CURL, 0);
        hit.leg(1, LEG_X, -.6f).leg(1, KNEE, .65f).leg(0, LEG_X, .5f).leg(0, ANKLE, .55f).leg(0, KNEE, .25f);
        through.add(CROUCH, 2.6f).add(SPINE_PITCH, .36f).add(SHIFT_Z, -1.2f).add(CHEST_PITCH, .12f).add(HEAD_PITCH, -.26f);
        for (int side = 0; side < 2; side++)
            through.arm(side, SH_FWD, 1.7f).arm(side, ARM_X, -.6f).arm(side, ARM_Z, -.5f).arm(side, ELBOW, .45f).arm(side, WRIST_X, .35f).arm(side, CURL, .2f);
        through.leg(1, LEG_X, -.52f).leg(1, KNEE, .55f).leg(0, LEG_X, .44f);
        return new Track().key(0, base).key(2.0f, open).key(DOUBLE_HIT, hit).key(6.4f, through).key(DOUBLE_TICKS + 2f, base);
    }
    /**
     * The claw uppercut: the centre of gravity sinks, knees and hips load, the right arm goes down past the
     * knee; then everything uncoils upward, legs driving, the body rising onto the toes with the claws going
     * up past the face, then it comes back down into the stance.
     */
    static Track uppercut(Pose base) {
        Pose load = base.copy(), drive = base.copy(), top = base.copy(), down = base.copy();
        load.add(CROUCH, 5.8f).add(SPINE_PITCH, .5f).add(CHEST_YAW, .45f).add(PELVIS_YAW, .12f).add(HEAD_PITCH, -.45f).add(SHIFT_X, -.5f)
                .arm(0, SH_FWD, -.6f).arm(0, ARM_X, .55f).arm(0, ARM_Z, .35f).arm(0, ELBOW, .5f).arm(0, WRIST_X, -.5f).arm(0, CURL, 0)
                .arm(1, ARM_X, -1.0f).arm(1, ELBOW, 1.35f).arm(1, ARM_Z, .3f)
                .leg(0, KNEE, .7f).leg(1, KNEE, .75f).leg(0, LEG_X, .4f).leg(1, LEG_X, -.35f);
        drive.add(LIFT, 2.0f).add(SPINE_PITCH, -.12f).add(CHEST_PITCH, -.14f).add(CHEST_YAW, -.45f).add(PELVIS_YAW, -.12f)
                .add(HEAD_PITCH, -.55f).add(SHIFT_Z, -.9f).add(PLANT, -.6f)
                .arm(0, SH_FWD, 1.7f).arm(0, SH_UP, 1.5f).arm(0, ARM_X, -2.85f).arm(0, ARM_Z, .25f).arm(0, ARM_Y, -.2f).arm(0, ELBOW, .3f)
                .arm(0, WRIST_X, .35f).arm(0, CURL, 0)
                .arm(1, ARM_X, .35f).arm(1, ARM_Z, .7f).arm(1, ELBOW, .9f)
                .leg(0, LEG_X, .5f).leg(0, ANKLE, .95f).leg(1, ANKLE, .6f).leg(1, KNEE, .05f).leg(1, LEG_X, -.2f);
        top.add(LIFT, 2.4f).add(SPINE_PITCH, -.08f).add(CHEST_YAW, -.5f).add(HEAD_PITCH, -.5f).add(SHIFT_Z, -.7f).add(PLANT, -.7f)
                .arm(0, SH_UP, 1.7f).arm(0, ARM_X, -3.05f).arm(0, ARM_Z, .1f).arm(0, ELBOW, .45f).arm(0, WRIST_X, .5f).arm(0, CURL, .2f)
                .arm(1, ARM_X, .25f).arm(1, ARM_Z, .8f).arm(1, ELBOW, .8f)
                .leg(0, LEG_X, .45f).leg(0, ANKLE, .85f).leg(1, ANKLE, .5f);
        down.add(CROUCH, 1.8f).add(CHEST_YAW, -.12f).arm(0, ARM_X, -1.0f).arm(0, ELBOW, .8f).arm(0, SH_UP, .4f);
        return new Track().key(0, base).key(3.2f, load).key(UPPER_HIT, drive).key(8f, top).key(11f, down).key(UPPER_TICKS + 2f, base);
    }
    /**
     * The berserk frenzy (after Wolverine's): hunched over, both claws taking turns without a breath, each one a
     * wild diagonal from high outside down through the front and across, the other recoiling up behind it
     * like a pair of scissors; the chest and hips rocking side to side with every slash, the feet stamping
     * forward under him, the head down and dead on the target. Nothing like the measured combo.
     */
    static Pose frenzy(Pose base, float t) {
        Pose p = base.copy();
        float in = k(t, 0, 1.5f);
        float cycle = FRENZY_STRIKE * 2;
        p.add(CROUCH, 3.4f * in).add(SPINE_PITCH, .38f * in).add(CHEST_PITCH, .12f * in).add(HEAD_PITCH, -.4f * in).add(SHIFT_Z, -.7f * in);
        float rock = 0;
        for (int side = 0; side < 2; side++) {
            // Each hand's slash: down fast (the first 45% of its half), back up slower; the hands half a cycle apart.
            float u = ((t / cycle) + (side == 0 ? 0 : .5f)) % 1;
            float x = u < .45f ? u / .45f * .5f : .5f + (u - .45f) / .55f * .5f;
            float down = .5f - .5f * Mth.cos(Mth.TWO_PI * x);     // 0 high outside .. 1 low across
            p.arm(side, SH_FWD, Mth.lerp(in, p.arm(side, SH_FWD), -.6f + 2.6f * down))
                    .arm(side, SH_UP, Mth.lerp(in, p.arm(side, SH_UP), .9f * (1 - down)))
                    .arm(side, ARM_X, Mth.lerp(in, p.arm(side, ARM_X), -2.4f + 1.6f * down))
                    .arm(side, ARM_Z, Mth.lerp(in, p.arm(side, ARM_Z), 1.15f - 1.75f * down))
                    .arm(side, ARM_Y, Mth.lerp(in, p.arm(side, ARM_Y), .45f - .95f * down))
                    .arm(side, ELBOW, Mth.lerp(in, p.arm(side, ELBOW), 1.4f - 1.1f * down))
                    .arm(side, WRIST_Z, Mth.lerp(in, p.arm(side, WRIST_Z), -.4f + .9f * down))
                    .arm(side, WRIST_X, Mth.lerp(in, p.arm(side, WRIST_X), -.4f + .5f * down))
                    .arm(side, CURL, Mth.lerp(in, p.arm(side, CURL), 0));
            rock += (side == 0 ? -1 : 1) * down;
        }
        // The body rocks after the striking hand.
        p.add(CHEST_YAW, -.5f * rock * in).add(SPINE_YAW, -.15f * rock * in).add(PELVIS_YAW, -.12f * rock * in).add(CHEST_ROLL, .12f * rock * in)
                .add(HEAD_YAW, .45f * rock * in).add(SHIFT_X, .5f * rock * in);
        // The feet: stamping forward, one then the other.
        float step = Mth.sin(t * Mth.TWO_PI / cycle);
        p.leg(0, LEG_X, Mth.lerp(in, p.leg(0, LEG_X), .35f + .12f * step)).leg(1, LEG_X, Mth.lerp(in, p.leg(1, LEG_X), -.4f + .12f * step))
                .legAdd(0, KNEE, (.3f + .15f * Math.max(0, step)) * in).legAdd(1, KNEE, (.4f + .15f * Math.max(0, -step)) * in)
                .leg(0, LEG_Z, Mth.lerp(in, p.leg(0, LEG_Z), .18f)).leg(1, LEG_Z, Mth.lerp(in, p.leg(1, LEG_Z), .15f))
                .legAdd(0, ANKLE, .25f * in).legAdd(1, ANKLE, .25f * in);
        return p;
    }
    /**
     * The crouch (SHIFT held): down low like a cat about to spring, hips back, the back long, the head up,
     * the claws near the ground; it sinks a little more as the camouflage gathers.
     */
    static Pose crouch(Pose base, float t, float time) {
        Pose p = base.copy();
        float in = k(t, 0, 3), charge = clamp(t / CAMO_CHARGE);
        p.set(CROUCH, (6.6f + .8f * charge) * in + base.get(CROUCH) * (1 - in)).add(SPINE_PITCH, .55f * in).add(CHEST_PITCH, .12f * in)
                .add(PELVIS_PITCH, .15f * in).add(HEAD_PITCH, -.75f * in).add(NECK, .5f * in).set(PELVIS_YAW, -.1f).set(CHEST_YAW, .05f);
        p.leg(0, LEG_X, Mth.lerp(in, p.leg(0, LEG_X), .25f)).leg(0, LEG_Z, Mth.lerp(in, p.leg(0, LEG_Z), .22f))
                .leg(1, LEG_X, Mth.lerp(in, p.leg(1, LEG_X), -.2f)).leg(1, LEG_Z, Mth.lerp(in, p.leg(1, LEG_Z), .2f));
        for (int side = 0; side < 2; side++)
            p.arm(side, SH_FWD, Mth.lerp(in, p.arm(side, SH_FWD), 1f)).arm(side, ARM_X, Mth.lerp(in, p.arm(side, ARM_X), side == 0 ? -.5f : -.7f))
                    .arm(side, ARM_Z, Mth.lerp(in, p.arm(side, ARM_Z), .3f)).arm(side, ELBOW, Mth.lerp(in, p.arm(side, ELBOW), .6f))
                    .arm(side, CURL, Mth.lerp(in, p.arm(side, CURL), .3f)).arm(side, WRIST_X, Mth.lerp(in, p.arm(side, WRIST_X), -.4f));
        // Slow, deep breaths while he waits.
        p.add(CHEST_PITCH, -.025f * Mth.sin(time * .08f) * in);
        return p;
    }

    // ------------------------------------------------------------------ right click: the marked dash
    /** The dash: leaning hard into it, a long low stride, both arms crossed in an X in front of the chest, claws out. */
    static Pose dash(Pose base, float t) {
        Pose p = base.copy();
        float in = snap(t, 0, 1.2f);
        p.set(ROOT_PITCH, .5f * in).set(CROUCH, 2.2f * in).set(PLANT, 1 - .7f * in).set(SPINE_PITCH, .12f).set(CHEST_PITCH, .05f)
                .set(HEAD_PITCH, -.55f * in).set(PELVIS_YAW, 0).set(CHEST_YAW, 0).set(SHIFT_X, 0);
        for (int side = 0; side < 2; side++)
            p.arm(side, SH_FWD, 1.4f).arm(side, ARM_X, -1.35f).arm(side, ARM_Z, -.75f).arm(side, ARM_Y, -.3f).arm(side, ELBOW, 1.25f)
                    .arm(side, WRIST_X, .2f).arm(side, CURL, 0);
        float stride = Mth.sin(t * 1.4f) * .15f;
        p.leg(0, LEG_X, .7f + stride).leg(0, KNEE, .25f).leg(0, ANKLE, .75f).leg(1, LEG_X, -.65f - stride).leg(1, KNEE, .75f).leg(1, ANKLE, .2f);
        return p;
    }
    /**
     * The cross: out of the X the claws are thrown open outward and up on two diagonals, the chest bursting
     * forward through the gap; he skids to a stop in a long lunge, holds it a beat, and straightens.
     */
    static Track cross(Pose base) {
        Pose x = base.copy(), open = base.copy(), hold = base.copy();
        x.set(ROOT_PITCH, .4f).add(CROUCH, 2.2f).set(PLANT, .5f).set(HEAD_PITCH, -.5f);
        for (int side = 0; side < 2; side++)
            x.arm(side, SH_FWD, 1.5f).arm(side, ARM_X, -1.4f).arm(side, ARM_Z, -.8f).arm(side, ARM_Y, -.3f).arm(side, ELBOW, 1.2f).arm(side, CURL, 0);
        x.leg(0, LEG_X, .6f).leg(0, KNEE, .3f).leg(1, LEG_X, -.6f).leg(1, KNEE, .7f);
        open.set(ROOT_PITCH, .1f).add(CROUCH, 3.2f).set(PLANT, 1).add(SPINE_PITCH, .12f).add(CHEST_PITCH, -.25f).add(HEAD_PITCH, -.3f);
        for (int side = 0; side < 2; side++)
            open.arm(side, SH_FWD, -.8f).arm(side, SH_UP, .8f).arm(side, ARM_X, -1.65f).arm(side, ARM_Z, 1.45f).arm(side, ARM_Y, .5f).arm(side, ELBOW, .1f)
                    .arm(side, WRIST_X, -.5f).arm(side, CURL, 0);
        open.leg(1, LEG_X, -.72f).leg(1, KNEE, .9f).leg(0, LEG_X, .7f).leg(0, KNEE, .3f).leg(0, ANKLE, .3f);
        hold.add(CROUCH, 2.6f).add(SPINE_PITCH, .1f).add(CHEST_PITCH, -.12f);
        for (int side = 0; side < 2; side++)
            hold.arm(side, ARM_X, -1.2f).arm(side, ARM_Z, 1.2f).arm(side, ARM_Y, .35f).arm(side, ELBOW, .35f).arm(side, CURL, .1f);
        hold.leg(1, LEG_X, -.6f).leg(1, KNEE, .75f).leg(0, LEG_X, .6f);
        return new Track().key(0, x).key(1.6f, open).key(5f, hold).key(CROSS_TICKS + 2f, base);
    }

    // ------------------------------------------------------------------ SHIFT: the pounce
    /** The load: a blink of compression, the rear foot loaded, arms going back, claws opening. */
    static Track load(Pose base) {
        Pose l = base.copy();
        l.add(CROUCH, 5.2f).add(SPINE_PITCH, .55f).add(CHEST_PITCH, .1f).add(HEAD_PITCH, -.75f).add(PELVIS_YAW, .15f).add(CHEST_YAW, -.1f);
        for (int side = 0; side < 2; side++) l.arm(side, SH_FWD, -.6f).arm(side, ARM_X, .65f).arm(side, ARM_Z, .5f).arm(side, ELBOW, .7f).arm(side, CURL, 0).arm(side, WRIST_X, -.4f);
        l.leg(0, LEG_X, .55f).leg(0, KNEE, .9f).leg(0, ANKLE, .3f).leg(1, KNEE, .55f);
        return new Track().key(0, base).key(LOAD_TICKS + .5f, l);
    }
    /** The pounce itself: body nearly horizontal, legs stretched out behind, arms back and to the sides, claws open, head up. */
    static Pose pounce(Pose base, float t) {
        Pose p = base.copy();
        float in = snap(t, 0, 1.5f);
        p.set(ROOT_PITCH, 1.22f * in).set(CROUCH, Mth.lerp(in, 5, 0)).set(PLANT, 1 - in).set(SPINE_PITCH, .08f).set(CHEST_PITCH, -.05f)
                .set(HEAD_PITCH, -1.15f * in - .3f).set(PELVIS_YAW, 0).set(CHEST_YAW, 0).set(SHIFT_X, 0);
        float flutter = Mth.sin(t * 1.3f) * .05f;
        for (int side = 0; side < 2; side++) {
            p.arm(side, SH_FWD, -.3f).arm(side, ARM_X, .55f + flutter).arm(side, ARM_Z, .75f).arm(side, ARM_Y, 0).arm(side, ELBOW, .45f)
                    .arm(side, WRIST_X, -.3f).arm(side, CURL, 0);
            p.leg(side, LEG_X, .38f + (side == 0 ? .12f : -.05f)).leg(side, LEG_Z, .06f).leg(side, KNEE, side == 0 ? .25f : .6f).leg(side, ANKLE, .95f)
                    .leg(side, LEG_Y, 0);
        }
        return p;
    }
    /**
     * Over the target: the contact compresses him (knees come up, claws in), then a front flip with a half
     * twist about his own body, one leg rising first; at the top he is upside down over their head, the
     * head turned to keep them in sight; coming out of it the legs reach round to set up the kick.
     * (The half twist itself is the root yaw here; the turn of his facing is done by the layer.)
     */
    static Pose flip(Pose base, float t) {
        float k = clamp(t / FLIP_TICKS);
        Pose p = base.copy();
        float s = k + .35f * Mth.sin(Mth.TWO_PI * k) / Mth.TWO_PI;
        p.set(ROOT_PITCH, 1.0f + (Mth.TWO_PI - 1.0f) * s).set(ROOT_YAW, Mth.PI * ease(k * 1.15f - .1f)).set(PLANT, 0).set(CROUCH, 0)
                .set(PELVIS_YAW, 0).set(CHEST_YAW, 0).set(SHIFT_X, 0);
        float tuck = (float) Math.sin(Math.PI * clamp(k * 1.25f));
        p.set(SPINE_PITCH, .2f + .45f * tuck).set(CHEST_PITCH, .15f + .2f * tuck).set(HEAD_PITCH, -.2f + .5f * tuck)
                .set(HEAD_YAW, -.6f * (float) Math.sin(Math.PI * k));
        // One leg rises first, then both tuck, then they open out for the kick.
        p.leg(0, LEG_X, -.4f - 1.5f * tuck).leg(0, KNEE, .5f + 1.6f * tuck).leg(0, ANKLE, .5f);
        p.leg(1, LEG_X, -.1f - 1.2f * clamp((k - .1f) * 1.6f) * tuck).leg(1, KNEE, .3f + 1.7f * tuck).leg(1, ANKLE, .6f);
        for (int side = 0; side < 2; side++)
            p.arm(side, SH_FWD, .2f).arm(side, ARM_X, -.4f - .9f * tuck).arm(side, ARM_Z, .9f - .5f * tuck).arm(side, ELBOW, .6f + .9f * tuck)
                    .arm(side, CURL, .3f).arm(side, ARM_Y, 0);
        // The legs reposition for the kick as he comes round.
        float set = k(k, .78f, 1);
        p.leg(0, LEG_X, Mth.lerp(set, p.leg(0, LEG_X), -1.25f)).leg(0, KNEE, Mth.lerp(set, p.leg(0, KNEE), 2.0f))
                .leg(1, LEG_X, Mth.lerp(set, p.leg(1, LEG_X), .3f)).leg(1, KNEE, Mth.lerp(set, p.leg(1, KNEE), .7f));
        return p;
    }
    /**
     * The flying side kick, his right side to the target: the knee chambers, the hips turn over, the torso
     * leans away and twists back, the arms counterbalance; then the leg drives out heel first through the
     * target, and comes back in.
     */
    static Track kick(Pose base) {
        Pose air = base.copy(), chamber = base.copy(), hit = base.copy(), back = base.copy();
        air.set(PLANT, 0).set(CROUCH, 0).set(ROOT_PITCH, 0).set(ROOT_YAW, 0);
        air.set(PELVIS_YAW, -.4f).set(CHEST_YAW, .2f).set(SPINE_PITCH, .05f).set(HEAD_YAW, .25f).set(HEAD_PITCH, -.2f).set(SHIFT_X, 0)
                .leg(0, LEG_X, -1.25f).leg(0, KNEE, 2.0f).leg(0, LEG_Z, .2f).leg(0, ANKLE, .2f).leg(1, LEG_X, .3f).leg(1, KNEE, .7f).leg(1, ANKLE, .6f);
        for (int side = 0; side < 2; side++) air.arm(side, ARM_X, -.6f).arm(side, ARM_Z, .8f).arm(side, ELBOW, .9f).arm(side, CURL, .35f);
        chamber.set(PLANT, 0).set(CROUCH, 0);
        chamber.set(PELVIS_YAW, -1.25f).set(CHEST_YAW, .75f).set(SPINE_ROLL, .25f).set(SPINE_PITCH, .1f).set(HEAD_YAW, .55f).set(HEAD_PITCH, -.25f)
                .leg(0, LEG_X, -.35f).leg(0, LEG_Z, 1.05f).leg(0, KNEE, 2.2f).leg(0, LEG_Y, .3f).leg(0, ANKLE, -.2f)
                .leg(1, LEG_X, .2f).leg(1, KNEE, 1.1f).leg(1, ANKLE, .5f)
                .arm(0, ARM_X, -1.0f).arm(0, ARM_Z, .3f).arm(0, ELBOW, 1.6f).arm(0, CURL, .6f)
                .arm(1, ARM_X, .2f).arm(1, ARM_Z, 1.1f).arm(1, ELBOW, .5f).arm(1, CURL, .3f);
        hit.set(PLANT, 0).set(CROUCH, 0);
        hit.set(PELVIS_YAW, -1.4f).set(CHEST_YAW, .85f).set(SPINE_ROLL, .5f).set(SPINE_PITCH, -.05f).set(HEAD_YAW, .6f).set(HEAD_PITCH, -.1f)
                .leg(0, LEG_X, -.15f).leg(0, LEG_Z, 1.5f).leg(0, KNEE, .05f).leg(0, LEG_Y, .25f).leg(0, ANKLE, -.35f)
                .leg(1, LEG_X, .15f).leg(1, KNEE, .55f).leg(1, ANKLE, .6f)
                .arm(0, ARM_X, -.7f).arm(0, ARM_Z, -.1f).arm(0, ELBOW, 1.9f).arm(0, CURL, .7f)
                .arm(1, ARM_X, .5f).arm(1, ARM_Z, 1.35f).arm(1, ELBOW, .25f).arm(1, CURL, .2f);
        back.set(PLANT, 0).set(CROUCH, 0);
        back.set(PELVIS_YAW, -.9f).set(CHEST_YAW, .5f).set(SPINE_ROLL, .25f).set(HEAD_YAW, .4f)
                .leg(0, LEG_X, -.5f).leg(0, LEG_Z, .8f).leg(0, KNEE, 1.4f).leg(0, ANKLE, .2f).leg(1, LEG_X, .2f).leg(1, KNEE, .8f)
                .arm(0, ARM_X, -.7f).arm(0, ELBOW, 1.3f).arm(1, ARM_X, -.1f).arm(1, ARM_Z, 1f).arm(1, ELBOW, .6f);
        return new Track().key(0, air).key(1.8f, chamber).key(KICK_HIT, hit).key(4.6f, hit).key(KICK_TICKS + .5f, back);
    }
    /** Down from the kick: legs reach for the ground, it is met on bent knees, the body settles and rises into the stance. */
    static Track land(Pose base) {
        float touch = LAND_TICKS * .45f;
        Pose fall = base.copy(), meet = base.copy(), absorb = base.copy(), rise = base.copy();
        fall.set(PLANT, .2f).set(CROUCH, 0).set(PELVIS_YAW, -.4f).set(CHEST_YAW, .2f).set(SPINE_PITCH, .1f).set(HEAD_PITCH, -.3f)
                .leg(0, LEG_X, -.25f).leg(0, KNEE, .45f).leg(0, LEG_Z, .3f).leg(0, ANKLE, .45f).leg(1, LEG_X, .1f).leg(1, KNEE, .35f).leg(1, ANKLE, .45f);
        for (int side = 0; side < 2; side++) fall.arm(side, ARM_X, -.5f).arm(side, ARM_Z, 1.0f).arm(side, ELBOW, .5f);
        meet.set(PLANT, 1).add(CROUCH, 1.5f).set(SPINE_PITCH, .3f).set(HEAD_PITCH, -.5f)
                .leg(0, LEG_Z, .3f).leg(1, LEG_Z, .2f);
        for (int side = 0; side < 2; side++) meet.arm(side, ARM_X, -.6f).arm(side, ARM_Z, .9f).arm(side, ELBOW, .6f);
        absorb.add(CROUCH, 5.5f).add(SPINE_PITCH, .4f).add(CHEST_PITCH, .12f).add(HEAD_PITCH, -.5f).leg(0, LEG_Z, .32f).leg(1, LEG_Z, .22f);
        for (int side = 0; side < 2; side++) absorb.arm(side, ARM_X, -.75f).arm(side, ARM_Z, .75f).arm(side, ELBOW, .8f).arm(side, CURL, .3f);
        absorb.arm(0, ARM_X, .1f).arm(0, ARM_Z, .35f).arm(0, ELBOW, .4f);   // the right hand reaches down to the ground
        rise.add(CROUCH, 1.2f).add(SPINE_PITCH, .08f);
        return new Track().key(0, fall).key(touch, meet).key(touch + 1.8f, absorb).key(touch + 4f, rise).key(LAND_TICKS + 2.5f, base);
    }
    /** A pounce that found nobody: momentum carries him into a tucked front flip, he lands light and rises into the stance. */
    static Pose miss(Pose base, float t) {
        float flipEnd = 7, touch = 8;
        Pose p = base.copy();
        if (t < touch) {
            float k = clamp(t / flipEnd);
            float tuck = (float) Math.sin(Math.PI * k);
            p.set(ROOT_PITCH, 1.2f + (Mth.TWO_PI - 1.2f) * ease(k)).set(PLANT, 0).set(CROUCH, 0).set(PELVIS_YAW, 0).set(CHEST_YAW, 0)
                    .set(SPINE_PITCH, .3f + .5f * tuck).set(CHEST_PITCH, .2f + .2f * tuck).set(HEAD_PITCH, .3f * tuck - .3f);
            for (int side = 0; side < 2; side++) {
                p.leg(side, LEG_X, -.2f - 1.6f * tuck).leg(side, KNEE, .3f + 1.9f * tuck).leg(side, ANKLE, .5f).leg(side, LEG_Z, .08f);
                p.arm(side, ARM_X, -.5f - .9f * tuck).arm(side, ARM_Z, .7f - .3f * tuck).arm(side, ELBOW, .6f + 1.1f * tuck);
            }
            // Legs extend for the ground at the end of the flip.
            float reach = k(t, flipEnd - 1.5f, touch);
            for (int side = 0; side < 2; side++) p.leg(side, LEG_X, Mth.lerp(reach, p.leg(side, LEG_X), side == 0 ? .1f : -.25f))
                    .leg(side, KNEE, Mth.lerp(reach, p.leg(side, KNEE), .4f));
            return p;
        }
        Pose absorb = base.copy().add(CROUCH, 4.5f).add(SPINE_PITCH, .35f).add(HEAD_PITCH, -.45f);
        for (int side = 0; side < 2; side++) absorb.arm(side, ARM_Z, .8f).arm(side, ARM_X, -.6f);
        Track tr = new Track().key(touch, base.copy().set(PLANT, 1).add(CROUCH, 1)).key(touch + 1.2f, absorb).key(MISS_TICKS + 3, base);
        return tr.sample(t);
    }

    // ------------------------------------------------------------------ Q: the spinning triple kick
    /** The load: knees drop, the weight shifts, the arms swing across against the coming spin, head on the target. */
    static Track spinLoad(Pose base) {
        Pose l = base.copy();
        l.add(CROUCH, 4.2f).add(CHEST_YAW, .7f).add(SPINE_YAW, .2f).add(PELVIS_YAW, .3f).add(SPINE_PITCH, .2f).add(HEAD_YAW, -.8f).add(SHIFT_X, -.6f);
        for (int side = 0; side < 2; side++) l.arm(side, ARM_X, -.9f).arm(side, ARM_Z, side == 0 ? 1.0f : -.1f).arm(side, ELBOW, side == 0 ? .5f : 1.6f).arm(side, ARM_Y, side == 0 ? .3f : -.4f);
        l.leg(0, KNEE, .7f).leg(1, KNEE, .55f).leg(0, LEG_X, .3f);
        return new Track().key(0, base).key(SPIN_LOAD_TICKS + .3f, l);
    }
    /**
     * One continuous spin in the air (the turn itself is ROOT_YAW, from PantherPath.spinTurn): the body tilts
     * into it, the arms wrap in to spin faster and fling out at each kick; the right leg sweeps out through
     * the front, then the left half a turn later, then the right again, harder, never touching down between.
     * The head snaps round to spot the target each time.
     */
    static Pose spin(Pose base, float t) {
        Pose p = base.copy();
        float turn = PantherPath.spinTurn(t, SPIN_TICKS, SPIN_TURN) * Mth.DEG_TO_RAD;
        p.set(ROOT_YAW, -turn).set(ROOT_ROLL, .32f * k(t, 0, 3) * (1 - k(t, 14, SPIN_TICKS))).set(PLANT, 0).set(CROUCH, 0)
                .set(PELVIS_YAW, 0).set(CHEST_YAW, 0).set(SPINE_YAW, 0).set(SHIFT_X, 0).set(SPINE_PITCH, .08f).set(CHEST_PITCH, .05f);
        // Spotting: the head stays on the target as long as it can, then whips round.
        float facing = Mth.wrapDegrees(turn * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        p.set(HEAD_YAW, Mth.clamp(facing, -1.3f, 1.3f) * .75f).set(HEAD_PITCH, -.25f);
        // Each leg's kick: a bell around its kick time; the other leg tucked meanwhile.
        float r1 = bell(t, SPIN_KICKS[0], 2.2f), l2 = bell(t, SPIN_KICKS[1], 2.2f), r3 = bell(t, SPIN_KICKS[2], 2.4f);
        float right = Math.max(r1, r3 * 1.08f), left = l2;
        for (int side = 0; side < 2; side++) {
            float kick = side == 0 ? right : left, other = side == 0 ? left : right;
            float tucked = Math.max(.35f, Math.max(other, 1 - kick));
            p.leg(side, LEG_Z, .25f + 1.3f * kick).leg(side, LEG_X, -.5f * kick - .9f * tucked * (1 - kick)).leg(side, KNEE, 1.8f * (1 - kick) * tucked + .08f)
                    .leg(side, ANKLE, .7f * kick + .3f).leg(side, LEG_Y, .2f * kick);
        }
        // Arms: wrapped in across the chest between kicks, thrown out for balance at each.
        float out = Math.max(right, left);
        for (int side = 0; side < 2; side++)
            p.arm(side, ARM_X, Mth.lerp(out, -1.25f, -.4f)).arm(side, ARM_Z, Mth.lerp(out, -.35f, 1.2f)).arm(side, ELBOW, Mth.lerp(out, 1.7f, .45f))
                    .arm(side, CURL, .4f).arm(side, SH_FWD, Mth.lerp(out, 1.2f, -.2f));
        p.set(SPINE_ROLL, .25f * (right - left)).set(EYES, .25f);
        return p;
    }
    private static float bell(float t, float at, float width) { float x = (t - at) / width; return (float) Math.exp(-x * x * 2.2f); }
    /**
     * Down from the spin: a light landing on bent knees, the momentum carrying the turn on round (pivoting
     * on the landing, never unwinding) until he faces the front again, a whole number of turns later.
     */
    static Track spinLand(Pose base) {
        float end = -PantherPath.spinTurn(SPIN_TICKS, SPIN_TICKS, SPIN_TURN) * Mth.DEG_TO_RAD;
        float home = -Mth.TWO_PI * (float) Math.ceil(-end / Mth.TWO_PI - .01f);
        Pose meet = base.copy(), absorb = base.copy(), done = base.copy();
        meet.set(PLANT, 1).add(CROUCH, 1.5f).set(ROOT_YAW, end).add(SPINE_PITCH, .2f);
        for (int side = 0; side < 2; side++) meet.arm(side, ARM_Z, 1.0f).arm(side, ARM_X, -.5f).arm(side, ELBOW, .6f);
        absorb.add(CROUCH, 4.8f).add(SPINE_PITCH, .35f).set(ROOT_YAW, Mth.lerp(.8f, end, home)).add(HEAD_PITCH, -.4f);
        for (int side = 0; side < 2; side++) absorb.arm(side, ARM_Z, .7f).arm(side, ARM_X, -.7f);
        done.set(ROOT_YAW, home);
        return new Track().key(0, meet).key(3f, absorb).key(SPIN_LAND_TICKS + 2f, done);
    }

    // ------------------------------------------------------------------ E: the kinetic release
    /**
     * The charge: feet planted wide, knees bent, the torso still; then the arms open out to the sides, the
     * chest comes forward, the head lifts; a tremble rising with the energy; a last compression just before
     * the release.
     */
    static Pose charge(Pose base, float t, float power) {
        Pose plant = base.copy(), open = base.copy(), gather = base.copy();
        plant.add(CROUCH, 1.8f).set(PELVIS_YAW, 0).set(CHEST_YAW, 0).set(SPINE_YAW, 0).set(SPINE_PITCH, .12f).set(SHIFT_X, 0)
                .leg(0, LEG_Z, .3f).leg(1, LEG_Z, .3f).leg(0, LEG_X, .05f).leg(1, LEG_X, -.05f).leg(0, LEG_Y, .25f).leg(1, LEG_Y, .25f)
                .set(HEAD_YAW, 0);
        for (int side = 0; side < 2; side++) plant.arm(side, ARM_X, -.3f).arm(side, ARM_Z, .5f).arm(side, ELBOW, .9f).arm(side, CURL, .5f);
        open.add(CROUCH, 1.4f).set(PELVIS_YAW, 0).set(CHEST_YAW, 0).set(SPINE_YAW, 0).set(SHIFT_X, 0).set(SPINE_PITCH, -.08f).set(CHEST_PITCH, -.22f)
                .set(HEAD_PITCH, -.4f).set(HEAD_YAW, 0)
                .leg(0, LEG_Z, .32f).leg(1, LEG_Z, .32f).leg(0, LEG_X, .05f).leg(1, LEG_X, -.05f).leg(0, LEG_Y, .25f).leg(1, LEG_Y, .25f);
        for (int side = 0; side < 2; side++)
            open.arm(side, SH_FWD, -.8f).arm(side, ARM_X, -.35f).arm(side, ARM_Z, 1.25f).arm(side, ARM_Y, .35f).arm(side, ELBOW, .35f)
                    .arm(side, WRIST_X, -.5f).arm(side, CURL, 0);
        gather.add(CROUCH, 2.6f).set(PELVIS_YAW, 0).set(CHEST_YAW, 0).set(SPINE_YAW, 0).set(SHIFT_X, 0).set(SPINE_PITCH, .1f).set(CHEST_PITCH, .05f)
                .set(HEAD_PITCH, -.3f).set(HEAD_YAW, 0)
                .leg(0, LEG_Z, .32f).leg(1, LEG_Z, .32f).leg(0, LEG_Y, .25f).leg(1, LEG_Y, .25f);
        for (int side = 0; side < 2; side++)
            gather.arm(side, SH_FWD, .4f).arm(side, ARM_X, -.6f).arm(side, ARM_Z, .9f).arm(side, ELBOW, .9f).arm(side, CURL, .2f).arm(side, WRIST_X, -.2f);
        Pose p = new Track().key(0, base).key(3.5f, plant).key(10.5f, open).key(CHARGE_TICKS, gather).sample(t);
        // The tremble: the stored energy pushing out of him, stronger toward the release.
        float shake = k(t, 4, CHARGE_TICKS) * (.4f + .6f * power);
        float n = t * 4.1f;
        p.add(CHEST_PITCH, .015f * shake * Mth.sin(n)).add(HEAD_ROLL, .02f * shake * Mth.sin(n * 1.3f + 1));
        for (int side = 0; side < 2; side++) p.armAdd(side, WRIST_X, .06f * shake * Mth.sin(n * 1.7f + side)).armAdd(side, CURL, .05f * shake * Mth.sin(n * 2.3f + side));
        p.set(EYES, .2f + .8f * k(t, 2, CHARGE_TICKS));
        return p;
    }
    /** The release: thrown wide open, chest out, head up, everything pushed outward; then held a moment. */
    static Pose releasePose(Pose base, float t) {
        Pose p = base.copy();
        float out = snap(t, 0, 1.5f), relax = k(t, 3, RELEASE_TICKS);
        p.set(PELVIS_YAW, 0).set(CHEST_YAW, 0).set(SPINE_YAW, 0).set(SHIFT_X, 0).set(HEAD_YAW, 0)
                .set(CROUCH, 2.2f + .6f * relax).set(SPINE_PITCH, Mth.lerp(out, .1f, -.16f)).set(CHEST_PITCH, Mth.lerp(out, .05f, -.32f) + .1f * relax)
                .set(HEAD_PITCH, Mth.lerp(out, -.3f, -.6f) + .15f * relax).set(LIFT, .6f * out * (1 - relax));
        p.leg(0, LEG_Z, .35f).leg(1, LEG_Z, .35f).leg(0, LEG_Y, .25f).leg(1, LEG_Y, .25f).leg(0, LEG_X, .05f).leg(1, LEG_X, -.05f);
        for (int side = 0; side < 2; side++)
            p.arm(side, SH_FWD, -1.1f).arm(side, ARM_X, Mth.lerp(out, -.6f, .2f) - .2f * relax).arm(side, ARM_Z, Mth.lerp(out, .9f, 1.45f) - .1f * relax)
                    .arm(side, ARM_Y, .4f).arm(side, ELBOW, Mth.lerp(out, .9f, .12f)).arm(side, WRIST_X, -.7f * out).arm(side, CURL, 0);
        p.set(EYES, 1 - .6f * relax);
        return p;
    }
    /** Back down: the arms lower, the breath goes out of him, the stance returns. */
    static Track recover(Pose base) {
        Pose a = base.copy();
        a.set(PELVIS_YAW, -.05f).set(CHEST_YAW, 0).add(CROUCH, .8f).set(SPINE_PITCH, .26f).set(HEAD_PITCH, -.3f)
                .leg(0, LEG_Z, .3f).leg(1, LEG_Z, .3f);
        for (int side = 0; side < 2; side++) a.arm(side, ARM_X, -.15f).arm(side, ARM_Z, .9f).arm(side, ELBOW, .3f).arm(side, CURL, .3f);
        Pose b = base.copy().add(CROUCH, .4f);
        return new Track().key(0, a).key(6, b).key(RECOVER_TICKS + 2, base);
    }

    // ------------------------------------------------------------------ R: reflex dodges
    /**
     * A dodge: the smallest wind-up, an explosive slip away from the attack, a short recovery, and through
     * all of it the body turning back toward where the attack came from (pelvis, then chest, then head), so
     * he ends square to the attacker, ready.
     */
    static Track dodge(Pose base, int type, float threatYaw) {
        float face = Mth.clamp(threatYaw * Mth.DEG_TO_RAD, -1.3f, 1.3f);
        Pose prep = base.copy(), slip = base.copy(), hold = base.copy(), back = base.copy();
        prep.add(CROUCH, 1f);
        switch (type) {
            case DODGE_LEFT, DODGE_RIGHT -> {
                // A deep slip to the side, bending away like a reed; the near forearm sweeps out to parry, the far one guards the face.
                int s = type == DODGE_LEFT ? 1 : -1;     // +1: he goes to his left
                int near = s > 0 ? 1 : 0, far = 1 - near;
                slip.add(SPINE_ROLL, .72f * s).add(CHEST_ROLL, .3f * s).add(HEAD_ROLL, .3f * s).add(SHIFT_X, 3.4f * s).add(CROUCH, 3.6f)
                        .add(SPINE_PITCH, .12f).add(CHEST_YAW, -.2f * s);
                slip.leg(near, LEG_Z, .65f).leg(near, KNEE, .75f).leg(far, LEG_Z, .08f).leg(far, KNEE, .05f).leg(far, ANKLE, .6f)
                        .arm(far, SH_FWD, 1.3f).arm(far, ARM_X, -1.6f).arm(far, ARM_Z, -.25f).arm(far, ELBOW, 1.9f).arm(far, CURL, .1f)
                        .arm(near, ARM_X, -1.1f).arm(near, ARM_Z, 1.1f).arm(near, ARM_Y, .5f).arm(near, ELBOW, .35f).arm(near, WRIST_X, -.4f).arm(near, CURL, 0);
                hold.add(SPINE_ROLL, .35f * s).add(SHIFT_X, 2.2f * s).add(CROUCH, 2.6f).leg(near, LEG_Z, .45f).leg(near, KNEE, .5f);
            }
            case DODGE_BACK -> {
                // Bent right back under it, knees forward, arms out for balance, the chest and face away from the blow.
                slip.add(SPINE_PITCH, -.7f).add(CHEST_PITCH, -.32f).add(HEAD_PITCH, .4f).add(ROOT_PITCH, -.12f).add(SHIFT_Z, 2.6f).add(CROUCH, 2.8f);
                for (int side = 0; side < 2; side++)
                    slip.leg(side, LEG_X, -.35f).leg(side, KNEE, .95f).leg(side, ANKLE, -.2f).arm(side, ARM_X, -.5f).arm(side, ARM_Z, 1.15f).arm(side, ELBOW, .4f).arm(side, CURL, .1f);
                hold.add(SPINE_PITCH, -.25f).add(SHIFT_Z, 1.4f).add(CROUCH, 1.8f);
            }
            case DODGE_CROUCH -> {
                // Ducked under it, low as he goes, both forearms crossed over the head in an X.
                slip.add(CROUCH, 8.2f).add(SPINE_PITCH, .78f).add(CHEST_PITCH, .2f).add(HEAD_PITCH, .2f)
                        .leg(0, LEG_Z, .38f).leg(1, LEG_Z, .32f);
                for (int side = 0; side < 2; side++)
                    slip.arm(side, SH_FWD, .6f).arm(side, ARM_X, -2.65f).arm(side, ARM_Z, -.35f).arm(side, ELBOW, 1.2f).arm(side, WRIST_X, .3f).arm(side, CURL, .1f);
                hold.add(CROUCH, 6f).add(SPINE_PITCH, .55f);
                for (int side = 0; side < 2; side++) hold.arm(side, ARM_X, -2.0f).arm(side, ELBOW, 1.4f);
            }
            default -> {
                // A pivot: the body spins a quarter away from the blow and back, low, a forearm across.
                int s = type == DODGE_BACK_LEFT ? 1 : -1;
                int near = s > 0 ? 1 : 0, far = 1 - near;
                slip.add(ROOT_YAW, -.95f * s).add(SPINE_ROLL, .35f * s).add(SPINE_PITCH, .2f).add(SHIFT_Z, 1.8f).add(SHIFT_X, 2f * s).add(CROUCH, 3f);
                slip.leg(near, LEG_X, .45f).leg(near, LEG_Z, .45f).leg(near, KNEE, .6f).leg(far, ANKLE, .5f)
                        .arm(far, SH_FWD, 1.2f).arm(far, ARM_X, -1.5f).arm(far, ELBOW, 1.8f).arm(far, ARM_Z, -.2f)
                        .arm(near, ARM_Z, 1.0f).arm(near, ARM_X, -.6f).arm(near, ELBOW, .5f);
                hold.add(ROOT_YAW, -.3f * s).add(SPINE_ROLL, .15f * s).add(SHIFT_Z, 1f).add(SHIFT_X, 1f * s).add(CROUCH, 2f);
            }
        }
        // Square back up to the attacker: the pelvis first, then the chest, the head leading.
        prep.add(HEAD_YAW, face * .5f);
        slip.add(PELVIS_YAW, face * .3f).add(CHEST_YAW, face * .3f).add(HEAD_YAW, face * .35f).set(EYES, .55f);
        hold.add(PELVIS_YAW, face * .45f).add(CHEST_YAW, face * .35f).add(HEAD_YAW, face * .2f);
        back.add(PELVIS_YAW, face * .3f).add(CHEST_YAW, face * .2f).add(HEAD_YAW, face * .1f);
        return new Track().key(0, base).key(.6f, prep).key(2.2f, slip).key(4.2f, hold).key(DODGE_TICKS + 2.5f, back);
    }

    // ------------------------------------------------------------------ hit reactions (layered on top)
    /** A hit taken: a flinch of the shoulder (small), a recoil of the torso and a step (medium), a stumble (large). */
    public static void hurt(Pose p, int age, int power, float fromYaw) {
        if (age >= HURT_TICKS + 4) return;
        float x = age, w = (float) Math.exp(-x / (power == 0 ? 2.2f : power == 1 ? 3.2f : 4.2f)) * snap(x, 0, .8f);
        if (w < .01f) return;
        float a = fromYaw * Mth.DEG_TO_RAD;
        float front = Mth.cos(a), side = Mth.sin(a);    // side +: from his right
        float s = power == 0 ? .5f : power == 1 ? 1f : 1.6f;
        // Away from the blow: a hit from the front bends him back, from the right leans him left.
        p.add(SPINE_PITCH, -.22f * front * s * w).add(CHEST_PITCH, -.12f * front * s * w).add(HEAD_PITCH, .2f * front * s * w)
                .add(SPINE_ROLL, .25f * side * s * w).add(CHEST_ROLL, .12f * side * s * w).add(HEAD_ROLL, .1f * side * s * w)
                .add(CHEST_YAW, -.15f * side * s * w).add(SHIFT_Z, 1.2f * front * s * w).add(SHIFT_X, 1.2f * side * s * w);
        int near = side > 0 ? 0 : 1;
        p.armAdd(near, SH_UP, 1.2f * s * w).armAdd(near, ELBOW, .3f * s * w).armAdd(near, ARM_X, -.3f * s * w);
        if (power >= 1) p.add(CROUCH, 1.4f * s * w).legAdd(front > 0 ? 0 : 1, LEG_X, (front > 0 ? .35f : -.35f) * s * w);
        if (power >= 2) p.add(HEAD_YAW, .25f * side * w).legAdd(1 - near, KNEE, .4f * w);
    }
}
