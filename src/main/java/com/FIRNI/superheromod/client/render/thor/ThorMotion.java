package com.FIRNI.superheromod.client.render.thor;

import static com.FIRNI.superheromod.heroes.thor.ThorAction.*;

/**
 * Thor's body language: every action as a pose over time. Pure math (no game classes), so a
 * check can sample it. Angles are radians in the model's own frame (+y down, -z front, -x is his
 * right): arm x negative raises the arm forward, arm y positive swings it to his right, right arm
 * z positive opens it outward (left arm: negative). Torso yaw positive turns his chest to his
 * right; torso and body pitch positive lean forward; knees positive fold the shin back.
 * Lengths (crouch, rise) are model pixels.
 */
public final class ThorMotion {
    public static final class Pose {
        public float bodyPitch, bodyRoll, rise, crouch;
        public float torsoYaw, torsoPitch, torsoRoll, headPitch, headYaw;
        public float rArmX, rArmY, rArmZ, rElbow, lArmX, lArmY, lArmZ, lElbow;
        public float rLegX, rLegZ, rKnee, lLegX, lLegZ, lKnee;
        /** Mjolnir in the hand: tilt about the hand's x axis, then twist about the forearm. */
        public float wristX, wristY;
        /** 0 = no blur ring, 1 = spinning beside the head (flight), 2 = whirling in front (guard). */
        public float spinRing, spinMode;
        public float eyes, mouth, aura;
        /** Left hand on the handle too (overhead two-handed). */
        public float twoHands;

        public Pose copy() { Pose p = new Pose(); p.set(this); return p; }
        public void set(Pose o) {
            bodyPitch = o.bodyPitch; bodyRoll = o.bodyRoll; rise = o.rise; crouch = o.crouch;
            torsoYaw = o.torsoYaw; torsoPitch = o.torsoPitch; torsoRoll = o.torsoRoll; headPitch = o.headPitch; headYaw = o.headYaw;
            rArmX = o.rArmX; rArmY = o.rArmY; rArmZ = o.rArmZ; rElbow = o.rElbow; lArmX = o.lArmX; lArmY = o.lArmY; lArmZ = o.lArmZ; lElbow = o.lElbow;
            rLegX = o.rLegX; rLegZ = o.rLegZ; rKnee = o.rKnee; lLegX = o.lLegX; lLegZ = o.lLegZ; lKnee = o.lKnee;
            wristX = o.wristX; wristY = o.wristY; spinRing = o.spinRing; spinMode = o.spinMode;
            eyes = o.eyes; mouth = o.mouth; aura = o.aura; twoHands = o.twoHands;
        }
        /** Blend toward another pose by k (0..1). Spin angles are not blended here (see ThorLayer). */
        public void toward(Pose o, float k) {
            bodyPitch += (o.bodyPitch - bodyPitch) * k; bodyRoll += (o.bodyRoll - bodyRoll) * k; rise += (o.rise - rise) * k; crouch += (o.crouch - crouch) * k;
            torsoYaw += (o.torsoYaw - torsoYaw) * k; torsoPitch += (o.torsoPitch - torsoPitch) * k; torsoRoll += (o.torsoRoll - torsoRoll) * k;
            headPitch += (o.headPitch - headPitch) * k; headYaw += (o.headYaw - headYaw) * k;
            rArmX += (o.rArmX - rArmX) * k; rArmY += (o.rArmY - rArmY) * k; rArmZ += (o.rArmZ - rArmZ) * k; rElbow += (o.rElbow - rElbow) * k;
            lArmX += (o.lArmX - lArmX) * k; lArmY += (o.lArmY - lArmY) * k; lArmZ += (o.lArmZ - lArmZ) * k; lElbow += (o.lElbow - lElbow) * k;
            rLegX += (o.rLegX - rLegX) * k; rLegZ += (o.rLegZ - rLegZ) * k; rKnee += (o.rKnee - rKnee) * k;
            lLegX += (o.lLegX - lLegX) * k; lLegZ += (o.lLegZ - lLegZ) * k; lKnee += (o.lKnee - lKnee) * k;
            spinRing += (o.spinRing - spinRing) * k; spinMode = o.spinMode;
            eyes += (o.eyes - eyes) * k; mouth += (o.mouth - mouth) * k; aura += (o.aura - aura) * k; twoHands += (o.twoHands - twoHands) * k;
        }
    }

    /** What the pose depends on besides the action and its clock. */
    public record Input(int action, float t, boolean flying, boolean hammerOut, boolean powered, boolean combat,
                        float speed, float idleTime, boolean grounded) {}

    private ThorMotion() {}

    // ------------------------------------------------------------------ helpers
    static float ease(float t) { t = clamp(t); return t * t * (3 - 2 * t); }
    static float clamp(float t) { return t < 0 ? 0 : t > 1 ? 1 : t; }
    /** Eased progress of t through [a, b]. */
    static float k(float t, float a, float b) { return ease((t - a) / (b - a)); }
    static float lerp(float a, float b, float k) { return a + (b - a) * k; }
    /** Snappy ease-out for strikes: most of the travel early, settling at the end. */
    static float snap(float t, float a, float b) { float x = clamp((t - a) / (b - a)); return 1 - (1 - x) * (1 - x) * (1 - x); }

    // ------------------------------------------------------------------ stances
    /** Relaxed: feet shoulder-width, chest a little forward, Mjolnir hanging in the right hand. */
    public static Pose idle(float time) {
        Pose p = new Pose();
        float breathe = (float) Math.sin(time * .08) * .025f;
        p.torsoPitch = .05f + breathe; p.headPitch = -.06f - breathe;
        p.rLegZ = .07f; p.lLegZ = -.07f;
        p.rArmX = .04f - breathe; p.rArmZ = .14f; p.rElbow = .16f; p.wristX = .12f;
        p.lArmX = .02f - breathe; p.lArmZ = -.11f; p.lElbow = .22f;
        // Every so often: settles the hammer in his grip, rolls a shoulder, glances aside.
        float cycle = time % 220;
        float adjust = (float) Math.sin(Math.PI * clamp((cycle - 60) / 18f));
        p.wristX += .55f * adjust; p.rElbow += .35f * adjust; p.rArmX -= .12f * adjust;
        float shrug = (float) Math.sin(Math.PI * clamp((cycle - 120) / 14f));
        p.torsoRoll = .05f * shrug; p.lArmZ -= .06f * shrug;
        float glance = (float) Math.sin(Math.PI * clamp((cycle - 160) / 40f));
        p.headYaw = .35f * glance;
        return p;
    }
    /** Combat: feet apart, knees bent, Mjolnir forward, the free hand open, square to the enemy. */
    public static Pose combat(float time) {
        Pose p = new Pose();
        float breathe = (float) Math.sin(time * .14) * .03f;
        p.crouch = 1.2f + breathe * 8; p.torsoPitch = .14f; p.headPitch = -.12f;
        p.rLegZ = .17f; p.lLegZ = -.17f; p.rLegX = .12f; p.lLegX = -.22f; p.rKnee = .32f; p.lKnee = .28f;
        p.torsoYaw = -.12f;
        p.rArmX = -.62f + breathe; p.rArmZ = .18f; p.rArmY = .1f; p.rElbow = .95f; p.wristX = -.55f;
        p.lArmX = -.5f - breathe; p.lArmZ = -.32f; p.lArmY = -.15f; p.lElbow = .7f;
        return p;
    }
    /** Flying: leaning into the flight, Mjolnir whirling beside his head, legs trailing. */
    public static Pose flight(float time, float speed) {
        Pose p = new Pose();
        float fast = clamp(speed / 1.4f);
        p.bodyPitch = .25f + 1.0f * fast;
        p.headPitch = -.2f - .75f * fast;
        p.rArmX = -2.65f; p.rArmZ = .42f; p.rArmY = -.15f; p.rElbow = .95f;
        p.wristX = time * 1.4f; p.spinRing = 1; p.spinMode = 1;
        p.lArmX = .35f + .3f * fast; p.lArmZ = -.55f; p.lElbow = .3f;
        float sway = (float) Math.sin(time * .12) * .06f;
        p.rLegX = .3f + .2f * fast + sway; p.lLegX = .12f + .15f * fast - sway; p.rKnee = .45f; p.lKnee = .2f;
        p.rLegZ = .05f; p.lLegZ = -.05f;
        return p;
    }

    // ------------------------------------------------------------------ actions
    public static Pose sample(Input in) {
        float t = in.t();
        Pose base = in.flying() ? flight(in.idleTime(), in.speed()) : in.combat() ? combat(in.idleTime()) : idle(in.idleTime());
        if (in.hammerOut() && !in.flying()) openHand(base, in.idleTime());
        Pose p = switch (in.action()) {
            case SWING_RIGHT -> swingRight(base.copy(), t);
            case SWING_LEFT -> swingLeft(base.copy(), t);
            case UPPERCUT -> uppercut(base.copy(), t);
            case THROW -> throwing(base.copy(), t);
            case CATCH -> caught(base.copy(), t);
            case TAKEOFF -> takeoff(base.copy(), t);
            case GUARD -> guard(base.copy(), t);
            case COUNTER -> counter(base.copy(), t);
            case WAKANDA -> wakanda(base.copy(), t);
            case ULTIMATE -> ultimate(base.copy(), t);
            default -> base;
        };
        if (in.flying() && in.action() != TAKEOFF && in.action() != ULTIMATE) {
            // In the air the legs keep trailing whatever the arms are doing.
            Pose f = flight(in.idleTime(), in.speed());
            p.bodyPitch = f.bodyPitch; p.rLegX = f.rLegX; p.lLegX = f.lLegX; p.rKnee = f.rKnee; p.lKnee = f.lKnee; p.crouch = 0;
        }
        if (in.powered()) p.eyes = Math.max(p.eyes, .75f);
        return p;
    }
    /** Hammer away: the right hand open and a little forward, ready for it to come back. */
    private static void openHand(Pose p, float time) {
        p.rArmX = -.45f; p.rArmZ = .2f; p.rElbow = .55f; p.rArmY = .05f;
    }

    /** 1st hit: wind to the left, then a big flat sweep from left to right with the whole body. */
    static Pose swingRight(Pose p, float t) {
        float wind = k(t, 0, 4), strike = snap(t, 4, SWING_HIT + 1), settle = k(t, SWING_HIT + 1, SWING_TICKS);
        // Wind-up: chest turns left, the arm reaches across, weight on the left leg.
        p.torsoYaw = lerp(p.torsoYaw, -.65f, wind); p.torsoPitch = lerp(p.torsoPitch, .2f, wind);
        p.rArmX = lerp(p.rArmX, -1.25f, wind); p.rArmY = lerp(p.rArmY, -.95f, wind); p.rArmZ = lerp(p.rArmZ, -.15f, wind);
        p.rElbow = lerp(p.rElbow, 1.05f, wind); p.wristX = lerp(p.wristX, -.35f, wind);
        p.lArmX = lerp(p.lArmX, -.3f, wind); p.lArmZ = lerp(p.lArmZ, -.55f, wind);
        p.crouch = lerp(p.crouch, 2f, wind); p.lKnee = lerp(p.lKnee, .5f, wind);
        // Strike: shoulders, chest, arm and hips all go round together; the arm straightens.
        p.torsoYaw = lerp(p.torsoYaw, .78f, strike); p.torsoRoll = lerp(0, -.08f, strike);
        p.rArmX = lerp(p.rArmX, -1.48f, strike); p.rArmY = lerp(p.rArmY, 1.05f, strike); p.rArmZ = lerp(p.rArmZ, .55f, strike);
        p.rElbow = lerp(p.rElbow, .1f, strike); p.wristX = lerp(p.wristX, -.05f, strike);
        p.lArmX = lerp(p.lArmX, -.6f, strike); p.lArmY = lerp(p.lArmY, -.6f, strike); p.lArmZ = lerp(p.lArmZ, -.75f, strike);
        p.rLegX = lerp(p.rLegX, .25f, strike); p.lLegX = lerp(p.lLegX, -.35f, strike); p.rKnee = lerp(p.rKnee, .45f, strike);
        // Follow-through: momentum carries the arm on and down; it is NOT reset (the next hit starts here).
        p.torsoYaw = lerp(p.torsoYaw, .9f, settle); p.rArmY = lerp(p.rArmY, 1.3f, settle); p.rArmX = lerp(p.rArmX, -1.1f, settle);
        p.rElbow = lerp(p.rElbow, .4f, settle);
        p.aura = strike * (1 - settle) * .6f;
        return p;
    }
    /** 2nd hit: from where the first ended, the body turns back and the hammer sweeps right to left. */
    static Pose swingLeft(Pose p, float t) {
        float wind = k(t, 0, 3), strike = snap(t, 3, SWING_HIT + 1), settle = k(t, SWING_HIT + 1, SWING_TICKS);
        // Starts from the first hit's follow-through: chest right, arm out right, elbow cocking.
        p.torsoYaw = lerp(.9f, .95f, wind); p.torsoPitch = .2f;
        p.rArmX = lerp(-1.1f, -1.3f, wind); p.rArmY = lerp(1.3f, 1.35f, wind); p.rArmZ = lerp(.55f, .3f, wind);
        p.rElbow = lerp(.4f, .85f, wind); p.wristX = -.25f;
        p.lArmX = -.6f; p.lArmY = -.6f; p.lArmZ = -.75f;
        p.crouch = 2; p.rKnee = .5f; p.lKnee = .35f; p.rLegX = .25f; p.lLegX = -.35f;
        // Backhand: chest whips left, the arm crosses in front of the chest.
        p.torsoYaw = lerp(p.torsoYaw, -.75f, strike); p.torsoRoll = lerp(0, .08f, strike);
        p.rArmX = lerp(p.rArmX, -1.5f, strike); p.rArmY = lerp(p.rArmY, -1.05f, strike); p.rArmZ = lerp(p.rArmZ, -.2f, strike);
        p.rElbow = lerp(p.rElbow, .2f, strike); p.wristX = lerp(p.wristX, 0, strike);
        p.lArmX = lerp(p.lArmX, -.2f, strike); p.lArmY = lerp(p.lArmY, .2f, strike); p.lArmZ = lerp(p.lArmZ, -.35f, strike);
        p.rLegX = lerp(p.rLegX, -.25f, strike); p.lLegX = lerp(p.lLegX, .25f, strike);
        p.torsoYaw = lerp(p.torsoYaw, -.9f, settle); p.rArmY = lerp(p.rArmY, -1.35f, settle); p.rArmX = lerp(p.rArmX, -1.15f, settle);
        p.rElbow = lerp(p.rElbow, .55f, settle);
        p.aura = strike * (1 - settle) * .6f;
        return p;
    }
    /** 3rd hit: knees bend, the hammer drops low, then everything drives up and he comes off the ground. */
    static Pose uppercut(Pose p, float t) {
        float load = k(t, 0, 6), drive = snap(t, 6, UPPER_HIT), settle = k(t, UPPER_HIT + 2, UPPER_TICKS);
        p.torsoYaw = lerp(p.torsoYaw, .15f, load);
        p.crouch = lerp(p.crouch, 4f, load); p.torsoPitch = lerp(p.torsoPitch, .5f, load); p.headPitch = lerp(p.headPitch, -.35f, load);
        p.rKnee = lerp(p.rKnee, 1.0f, load); p.lKnee = lerp(p.lKnee, .9f, load); p.rLegX = lerp(p.rLegX, -.55f, load); p.lLegX = lerp(p.lLegX, -.3f, load);
        p.rArmX = lerp(p.rArmX, .65f, load); p.rArmY = lerp(p.rArmY, .1f, load); p.rArmZ = lerp(p.rArmZ, .3f, load);
        p.rElbow = lerp(p.rElbow, .2f, load); p.wristX = lerp(p.wristX, .35f, load);
        p.lArmX = lerp(p.lArmX, -.75f, load); p.lArmZ = lerp(p.lArmZ, -.4f, load); p.lElbow = lerp(p.lElbow, .9f, load);
        // The drive: legs straighten, he lifts off a little, the hammer comes up past his face.
        p.crouch = lerp(p.crouch, -.5f, drive); p.rise = 3.5f * drive * (1 - settle);
        p.torsoPitch = lerp(p.torsoPitch, -.28f, drive); p.headPitch = lerp(p.headPitch, -.45f, drive);
        p.rKnee = lerp(p.rKnee, .1f, drive); p.lKnee = lerp(p.lKnee, .45f, drive); p.rLegX = lerp(p.rLegX, .1f, drive); p.lLegX = lerp(p.lLegX, -.4f, drive);
        p.rArmX = lerp(p.rArmX, -2.95f, drive); p.rArmZ = lerp(p.rArmZ, .12f, drive); p.rElbow = lerp(p.rElbow, .15f, drive);
        p.wristX = lerp(p.wristX, -.25f, drive);
        p.lArmX = lerp(p.lArmX, .35f, drive); p.lArmZ = lerp(p.lArmZ, -.6f, drive);
        p.aura = drive * (1 - settle);
        p.eyes = drive * (1 - settle);
        return p;
    }
    /** Throw: chest turns, the arm draws back over the shoulder, then whips through fully extended. */
    static Pose throwing(Pose p, float t) {
        float draw = k(t, 0, THROW_WINDUP - 1), whip = snap(t, THROW_WINDUP - 1, THROW_WINDUP + 1), settle = k(t, THROW_WINDUP + 2, THROW_WINDUP + 6);
        p.torsoYaw = lerp(p.torsoYaw, .55f, draw); p.torsoPitch = lerp(p.torsoPitch, -.1f, draw);
        p.rArmX = lerp(p.rArmX, -3.15f, draw); p.rArmZ = lerp(p.rArmZ, .35f, draw); p.rElbow = lerp(p.rElbow, 1.5f, draw); p.wristX = lerp(p.wristX, -.6f, draw);
        p.lArmX = lerp(p.lArmX, -1.45f, draw); p.lArmY = lerp(p.lArmY, .3f, draw); p.lArmZ = lerp(p.lArmZ, -.1f, draw); p.lElbow = lerp(p.lElbow, .15f, draw);
        p.lLegX = lerp(p.lLegX, -.45f, draw); p.rLegX = lerp(p.rLegX, .3f, draw); p.crouch = lerp(p.crouch, 1.5f, draw);
        p.torsoYaw = lerp(p.torsoYaw, -.35f, whip); p.torsoPitch = lerp(p.torsoPitch, .35f, whip);
        p.rArmX = lerp(p.rArmX, -1.55f, whip); p.rArmZ = lerp(p.rArmZ, .08f, whip); p.rElbow = lerp(p.rElbow, 0, whip); p.rArmY = lerp(p.rArmY, -.1f, whip);
        p.lArmX = lerp(p.lArmX, .2f, whip); p.lArmZ = lerp(p.lArmZ, -.45f, whip);
        p.lKnee = lerp(p.lKnee, .5f, whip);
        p.rArmX = lerp(p.rArmX, -.9f, settle); p.rElbow = lerp(p.rElbow, .4f, settle); p.torsoYaw = lerp(p.torsoYaw, -.1f, settle);
        return p;
    }
    /** The hammer smacks into the open hand: the arm is knocked back, then settles. */
    static Pose caught(Pose p, float t) {
        float hit = 1 - k(t, 0, CATCH_TICKS);
        p.rArmX = lerp(p.rArmX, -.75f, hit); p.rElbow = lerp(p.rElbow, 1.2f, hit); p.torsoYaw += .25f * hit;
        p.torsoPitch -= .08f * hit;
        p.eyes = hit * .8f;
        return p;
    }
    /** Shift on the ground: the arm comes up beside his head and Mjolnir winds up to a whirl. */
    static Pose takeoff(Pose p, float t) {
        float raise = k(t, 0, 5), crouch = (float) Math.sin(Math.PI * clamp((t - 6) / (TAKEOFF_TICKS - 6)));
        p.rArmX = lerp(p.rArmX, -2.65f, raise); p.rArmZ = lerp(p.rArmZ, .42f, raise); p.rArmY = lerp(p.rArmY, -.15f, raise);
        p.rElbow = lerp(p.rElbow, .95f, raise);
        // Spin speed climbs from a lazy turn to a blur: angle is the integral of that speed.
        p.wristX = .25f * t + .045f * t * t;
        p.spinRing = k(t, 4, TAKEOFF_TICKS); p.spinMode = 1;
        p.lArmX = lerp(p.lArmX, .25f, raise); p.lArmZ = lerp(p.lArmZ, -.6f, raise);
        p.crouch = 3.5f * crouch; p.rKnee = .9f * crouch; p.lKnee = .9f * crouch; p.torsoPitch = .25f * crouch; p.headPitch = -.3f;
        p.eyes = .5f * raise;
        return p;
    }
    /** E: feet planted, leaning in, Mjolnir whirling in front of him like a shield of metal and light. */
    static Pose guard(Pose p, float t) {
        float in = k(t, 0, 3);
        p.crouch = lerp(p.crouch, 2.2f, in); p.torsoPitch = lerp(p.torsoPitch, .22f, in); p.headPitch = lerp(p.headPitch, -.25f, in);
        p.rLegZ = .2f; p.lLegZ = -.2f; p.rLegX = .2f; p.lLegX = -.35f; p.rKnee = .5f; p.lKnee = .45f;
        p.torsoYaw = lerp(p.torsoYaw, .1f, in);
        p.rArmX = lerp(p.rArmX, -1.4f, in); p.rArmY = lerp(p.rArmY, -.25f, in); p.rArmZ = lerp(p.rArmZ, .05f, in); p.rElbow = lerp(p.rElbow, .35f, in);
        p.wristX = -1.5708f;
        p.wristY = .8f * t + .07f * Math.min(t, 8) * Math.min(t, 8);
        p.spinRing = k(t, 1, 5); p.spinMode = 2;
        p.lArmX = lerp(p.lArmX, -.85f, in); p.lArmZ = lerp(p.lArmZ, -.15f, in); p.lElbow = lerp(p.lElbow, 1.35f, in);
        return p;
    }
    /** Perfect parry: the whirl stops dead and the hammer is driven back into the attacker. */
    static Pose counter(Pose p, float t) {
        Pose g = guard(p.copy(), 8);
        float cock = k(t, 0, 2), strike = snap(t, 2, 5), settle = k(t, 6, COUNTER_TICKS);
        g.wristY = 0; g.spinRing = 0;
        g.wristX = lerp(-1.5708f, -.1f, cock);
        g.torsoYaw = lerp(.1f, .7f, cock); g.rArmY = lerp(-.25f, 1.1f, cock); g.rElbow = lerp(.35f, 1.0f, cock);
        g.torsoYaw = lerp(g.torsoYaw, -.55f, strike); g.rArmY = lerp(g.rArmY, -.6f, strike); g.rElbow = lerp(g.rElbow, .05f, strike);
        g.rArmX = lerp(g.rArmX, -1.5f, strike);
        g.torsoYaw = lerp(g.torsoYaw, .1f, settle); g.rArmY = lerp(g.rArmY, -.25f, settle);
        g.aura = strike * (1 - settle); g.eyes = 1 - settle;
        return g;
    }

    /** Overhead with both hands, head back, chest open: the moment before he brings it down. */
    static void overhead(Pose p, float k) {
        p.rArmX = lerp(p.rArmX, -3.0f, k); p.rArmZ = lerp(p.rArmZ, .18f, k); p.rArmY = lerp(p.rArmY, 0, k); p.rElbow = lerp(p.rElbow, .55f, k);
        p.lArmX = lerp(p.lArmX, -3.0f, k); p.lArmZ = lerp(p.lArmZ, -.18f, k); p.lArmY = lerp(p.lArmY, 0, k); p.lElbow = lerp(p.lElbow, .55f, k);
        p.wristX = lerp(p.wristX, -.9f, k); p.twoHands = k;
        p.torsoPitch = lerp(p.torsoPitch, -.28f, k); p.headPitch = lerp(p.headPitch, -.35f, k);
    }
    /** Both hands bring the hammer down in front of him. */
    static void slam(Pose p, float k) {
        p.rArmX = lerp(p.rArmX, -1.05f, k); p.lArmX = lerp(p.lArmX, -1.05f, k); p.rElbow = lerp(p.rElbow, .1f, k); p.lElbow = lerp(p.lElbow, .1f, k);
        p.rArmZ = lerp(p.rArmZ, -.12f, k); p.lArmZ = lerp(p.lArmZ, .12f, k);
        p.wristX = lerp(p.wristX, -.2f, k);
        p.torsoPitch = lerp(p.torsoPitch, .55f, k); p.headPitch = lerp(p.headPitch, -.3f, k);
    }
    /** The landing: one knee down, Mjolnir pressed into the ground with both hands, head up. */
    static void kneel(Pose p, float k) {
        p.crouch = lerp(p.crouch, 7.5f, k);
        p.lLegX = lerp(p.lLegX, -1.25f, k); p.lKnee = lerp(p.lKnee, 1.45f, k); p.lLegZ = lerp(p.lLegZ, -.1f, k);
        p.rLegX = lerp(p.rLegX, .35f, k); p.rKnee = lerp(p.rKnee, 1.75f, k); p.rLegZ = lerp(p.rLegZ, .12f, k);
        p.torsoPitch = lerp(p.torsoPitch, .5f, k); p.headPitch = lerp(p.headPitch, -.45f, k);
        p.rArmX = lerp(p.rArmX, -.8f, k); p.rArmZ = lerp(p.rArmZ, -.05f, k); p.rElbow = lerp(p.rElbow, .25f, k);
        p.lArmX = lerp(p.lArmX, -.75f, k); p.lArmZ = lerp(p.lArmZ, .2f, k); p.lElbow = lerp(p.lElbow, .45f, k);
        p.wristX = lerp(p.wristX, -.25f, k); p.twoHands = k * .8f;
    }

    /** R: bowed head, eyes light, up into the air, the cry, both hands up, the dive, the kneel. */
    static Pose wakanda(Pose p, float t) {
        Pose base = idle(0);
        p.set(base);
        p.rLegZ = .16f; p.lLegZ = -.16f;
        if (t >= LANDED) {
            float s = t - LANDED;
            slam(p, 1); overhead(p, 0);
            kneel(p, snap(s, 0, 2));
            float up = k(s, WK_FREEZE + 14, WK_FREEZE + WK_KNEEL);
            Pose stand = combat(t);
            p.toward(stand, up);
            p.eyes = 1 - k(s, WK_FREEZE + 8, WK_FREEZE + WK_KNEEL);
            p.aura = (s < WK_FREEZE ? .4f : 1) * (1 - k(s, WK_FREEZE, WK_FREEZE + 16));
            p.twoHands *= 1 - up;
            return p;
        }
        float bow = 1 - k(t, 4, WK_LOOK_UP + 4);
        p.headPitch = lerp(-.3f, .5f, bow); p.torsoPitch = .12f;
        p.eyes = k(t, WK_LOOK_UP, WK_LOOK_UP + 8);
        float lift = k(t, WK_LOOK_UP, WK_RISE);
        p.rArmX = lerp(p.rArmX, -.55f, lift); p.rElbow = lerp(p.rElbow, .85f, lift);
        p.crouch = 2.5f * (float) Math.sin(Math.PI * clamp((t - WK_RISE + 6) / 8f)); p.rKnee = p.lKnee = p.crouch * .25f;
        // God of Thunder: chest out, shoulders open, Mjolnir up at his side.
        float god = k(t, WK_RISE, WK_RISE + 8);
        p.torsoPitch = lerp(p.torsoPitch, -.22f, god); p.headPitch = lerp(p.headPitch, -.38f, god);
        p.rArmX = lerp(p.rArmX, -2.45f, god); p.rArmZ = lerp(p.rArmZ, .75f, god); p.rElbow = lerp(p.rElbow, .45f, god);
        p.lArmX = lerp(p.lArmX, -.25f, god); p.lArmZ = lerp(p.lArmZ, -.7f, god); p.lElbow = lerp(p.lElbow, .25f, god);
        p.rKnee = lerp(p.rKnee, .35f, god); p.lKnee = lerp(p.lKnee, .15f, god); p.rLegX = lerp(p.rLegX, .2f, god);
        // The battle cry: head thrown back, arms flung wide, the whole body shaking.
        float cry = k(t, WK_SHOUT - 2, WK_SHOUT + 2) * (1 - k(t, WK_DIVE - 10, WK_DIVE - 6));
        float shake = (float) Math.sin(t * 2.7) * .04f * cry;
        p.headPitch = lerp(p.headPitch, -.6f, cry) + shake; p.mouth = cry;
        p.lArmZ = lerp(p.lArmZ, -1.15f, cry) + shake; p.rArmZ = lerp(p.rArmZ, 1.0f, cry) - shake; p.rArmX = lerp(p.rArmX, -2.65f, cry);
        p.aura = Math.max(god * .5f, cry);
        overhead(p, k(t, WK_DIVE - 8, WK_DIVE - 1));
        float dive = snap(t, WK_DIVE, WK_DIVE + 6);
        slam(p, dive);
        p.rKnee = lerp(p.rKnee, .9f, dive); p.lKnee = lerp(p.lKnee, .7f, dive); p.rLegX = lerp(p.rLegX, -.4f, dive); p.lLegX = lerp(p.lLegX, -.2f, dive);
        if (t >= WK_DIVE) p.aura = 1;
        return p;
    }

    /** X: the film's choreography for Thor himself (the camera is the film's business). */
    static Pose ultimate(Pose p, float t) {
        p.set(idle(t * .5f));
        p.rLegZ = .12f; p.lLegZ = -.12f;
        // Quiet: still, head a little bowed.
        p.headPitch = lerp(.25f, -.12f, k(t, ULT_CLOSE + 6, ULT_EYE_SPARK));
        p.eyes = t >= ULT_EYE_SPARK && t < ULT_EYE_SPARK + 2 ? .6f : k(t, ULT_EYES, ULT_EYES + 4);
        // Mjolnir to the sky.
        float raise = k(t, ULT_RAISE, ULT_RAISE + 14);
        p.rArmX = lerp(p.rArmX, -3.05f, raise); p.rArmZ = lerp(p.rArmZ, .12f, raise); p.rElbow = lerp(p.rElbow, .05f, raise); p.wristX = lerp(p.wristX, 0, raise);
        p.headPitch = lerp(p.headPitch, -.55f, raise); p.torsoPitch = lerp(p.torsoPitch, -.12f, raise);
        p.lArmZ = lerp(p.lArmZ, -.35f, raise);
        // The bolt hits him: he takes it, braced.
        float struck = k(t, ULT_STRIKE, ULT_STRIKE + 3) * (1 - k(t, ULT_POWER, ULT_POWER + 10));
        p.crouch = 2.5f * struck; p.rKnee = p.lKnee = .5f * struck;
        // Power: wide, low, chest out, then the cry.
        float power = k(t, ULT_POWER, ULT_POWER + 10);
        p.crouch = lerp(p.crouch, 2.2f, power); p.rLegZ = lerp(p.rLegZ, .25f, power); p.lLegZ = lerp(p.lLegZ, -.25f, power);
        p.rKnee = lerp(p.rKnee, .45f, power); p.lKnee = lerp(p.lKnee, .45f, power);
        p.rArmX = lerp(p.rArmX, -2.4f, power); p.rArmZ = lerp(p.rArmZ, .85f, power); p.rElbow = lerp(p.rElbow, .5f, power);
        p.lArmX = lerp(p.lArmX, -.4f, power); p.lArmZ = lerp(p.lArmZ, -.9f, power); p.lElbow = lerp(p.lElbow, .5f, power);
        p.torsoPitch = lerp(p.torsoPitch, -.2f, power); p.headPitch = lerp(p.headPitch, -.2f, power);
        float cry = k(t, ULT_SHOUT, ULT_SHOUT + 4) * (1 - k(t, ULT_BOOM, ULT_BOOM + 6));
        float shake = (float) Math.sin(t * 2.9) * .05f * cry;
        p.headPitch = lerp(p.headPitch, -.65f, cry) + shake; p.mouth = cry; p.lArmZ = lerp(p.lArmZ, -1.2f, cry) + shake; p.rArmZ += shake;
        p.aura = Math.max(k(t, ULT_STRIKE, ULT_STRIKE + 2), 0) * (t < ULT_IMPACT + 20 ? 1 : 1 - k(t, ULT_IMPACT + 20, ULT_TOTAL));
        // Facing the enemy: Mjolnir comes down to point at them, then up again.
        float point = k(t, ULT_TARGET + 4, ULT_TARGET + 14) * (1 - k(t, ULT_FLIGHT - 10, ULT_FLIGHT));
        p.rArmX = lerp(p.rArmX, -1.55f, point); p.rArmZ = lerp(p.rArmZ, .1f, point); p.rElbow = lerp(p.rElbow, .05f, point); p.wristX = lerp(p.wristX, -.1f, point);
        p.headPitch = lerp(p.headPitch, -.05f, point); p.torsoPitch = lerp(p.torsoPitch, .1f, point);
        // The whirl and the leap.
        if (t >= ULT_FLIGHT && t < ULT_HOVER) {
            Pose spin = takeoff(p.copy(), Math.min(TAKEOFF_TICKS, t - ULT_FLIGHT));
            if (t >= ULT_LAUNCH) { spin = flight(t, 1.4f); spin.wristX = t * 1.5f; spin.headPitch = -.6f; }
            spin.eyes = 1; spin.aura = 1;
            p.set(spin);
        }
        if (t >= ULT_HOVER && t < ULT_IMPACT) {
            p.set(idle(0)); p.eyes = 1; p.aura = 1;
            p.rLegX = .25f; p.lLegX = -.15f; p.rKnee = .55f; p.lKnee = .35f;
            overhead(p, k(t, ULT_HOVER, ULT_HOVER + 6));
            slam(p, snap(t, ULT_PLUNGE, ULT_IMPACT));
        }
        if (t >= ULT_IMPACT) {
            p.set(idle(t)); p.eyes = 1;
            slam(p, 1); kneel(p, snap(t, ULT_IMPACT, ULT_IMPACT + 2));
            float up = k(t, ULT_IMPACT + 12, ULT_IMPACT + 22);
            p.toward(idle(t), up);
            // Mjolnir onto his shoulder.
            float shoulder = k(t, ULT_IMPACT + 18, ULT_IMPACT + 26);
            p.rArmX = lerp(p.rArmX, -1.05f, shoulder); p.rArmZ = lerp(p.rArmZ, .05f, shoulder); p.rElbow = lerp(p.rElbow, 2.3f, shoulder);
            p.wristX = lerp(p.wristX, -1.1f, shoulder); p.twoHands = p.twoHands * (1 - up);
            p.aura = .6f * (1 - k(t, ULT_IMPACT + 10, ULT_TOTAL));
        }
        return p;
    }

    /**
     * Where the film shows his body, relative to where he really stands (stage space: x right, y up,
     * z toward the target at distance land): still until the leap, an arc up and over, a hang, the plunge.
     */
    public static double[] ultimateOffset(float t, double land) {
        if (t < ULT_LAUNCH || t >= ULT_IMPACT) return new double[]{0, 0, 0};
        double height = 8.5;
        if (t < ULT_HOVER) {
            float k = clamp((t - ULT_LAUNCH) / (ULT_HOVER - ULT_LAUNCH));
            double up = 1 - Math.pow(1 - k, 3), along = ease(k);
            return new double[]{0, height * up, land * along};
        }
        if (t < ULT_PLUNGE) return new double[]{0, height + .15 * Math.sin((t - ULT_HOVER) * .3), land};
        float k = clamp((t - ULT_PLUNGE) / (ULT_IMPACT - ULT_PLUNGE));
        return new double[]{0, height * (1 - k * k), land};
    }
}
