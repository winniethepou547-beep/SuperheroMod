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
        /** The wrist cocked sideways (in the swing's plane when the arm is out): lag before the blow, snap through it. */
        public float wristZ;
        /** 0 = no whirl; 2 = a wheel facing the way he faces (guard, charge); 3 = a wheel at his side, edge-on to the front. */
        public float spinRing, spinMode;
        public float eyes, mouth, aura;
        /** Left hand on the handle too (overhead two-handed). */
        public float twoHands;
        /** 1 while Mjolnir is not in his hand at all (thrown away in the film). */
        public float noHammer;
        /** 1 = the hammer is held dead upright whatever the arm does (standing stances). */
        public float upright;
        /** While held upright: the handle leaned over sideways (radians, positive leans the top to his left). */
        public float tilt;
        /** The hold's handle angle above level (radians): about 30 degrees at rest, straight up in a launch. */
        public float holdRise = .55f;
        /** During a swing: the hammer turned out as a lever, head leading (see ThorLayer.lever). */
        public float lever;

        public Pose copy() { Pose p = new Pose(); p.set(this); return p; }
        public void set(Pose o) {
            bodyPitch = o.bodyPitch; bodyRoll = o.bodyRoll; rise = o.rise; crouch = o.crouch;
            torsoYaw = o.torsoYaw; torsoPitch = o.torsoPitch; torsoRoll = o.torsoRoll; headPitch = o.headPitch; headYaw = o.headYaw;
            rArmX = o.rArmX; rArmY = o.rArmY; rArmZ = o.rArmZ; rElbow = o.rElbow; lArmX = o.lArmX; lArmY = o.lArmY; lArmZ = o.lArmZ; lElbow = o.lElbow;
            rLegX = o.rLegX; rLegZ = o.rLegZ; rKnee = o.rKnee; lLegX = o.lLegX; lLegZ = o.lLegZ; lKnee = o.lKnee;
            wristX = o.wristX; wristY = o.wristY; wristZ = o.wristZ; spinRing = o.spinRing; spinMode = o.spinMode;
            eyes = o.eyes; mouth = o.mouth; aura = o.aura; twoHands = o.twoHands; noHammer = o.noHammer; upright = o.upright; tilt = o.tilt; lever = o.lever; holdRise = o.holdRise;
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
            spinRing += (o.spinRing - spinRing) * k; spinMode = o.spinMode; wristZ += (o.wristZ - wristZ) * k;
            eyes += (o.eyes - eyes) * k; mouth += (o.mouth - mouth) * k; aura += (o.aura - aura) * k; twoHands += (o.twoHands - twoHands) * k;
            noHammer = o.noHammer; upright += (o.upright - upright) * k; tilt += (o.tilt - tilt) * k; lever += (o.lever - lever) * k; holdRise += (o.holdRise - holdRise) * k;
        }
    }

    /** What the pose depends on besides the action and its clock. */
    /** charge: how wound-up the hammer launch is (0..1); lookPitch: where he looks, radians, negative is up. */
    public record Input(int action, float t, boolean flying, boolean hammerOut, boolean powered, boolean combat,
                        float speed, float idleTime, boolean grounded, float charge, float lookPitch) {}

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
        // Mjolnir held up off the ground: forearm forward, the handle rising out of the fist, head on top.
        p.rArmX = -.05f - breathe; p.rArmZ = .14f; p.rArmY = .05f; p.rElbow = .3f; p.wristX = -1.79f; p.upright = 1; p.holdRise = .55f;
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
        p.crouch = .35f + breathe * 3; p.torsoPitch = .1f; p.headPitch = -.1f;
        p.rLegZ = .14f; p.lLegZ = -.14f; p.rLegX = .08f; p.lLegX = -.14f; p.rKnee = .1f; p.lKnee = .08f;
        p.torsoYaw = -.12f;
        p.rArmX = -.25f + breathe; p.rArmZ = .16f; p.rArmY = .1f; p.rElbow = .45f; p.wristX = -1.57f; p.upright = 1; p.holdRise = .6f;
        p.lArmX = -.5f - breathe; p.lArmZ = -.32f; p.lArmY = -.15f; p.lElbow = .7f;
        return p;
    }
    /** Flying: leaning into the flight, Mjolnir whirling beside his head, legs trailing. */
    public static Pose flight(float time, float speed) {
        Pose p = new Pose();
        float fast = clamp(speed / 1.4f);
        p.bodyPitch = .25f + 1.0f * fast;
        p.headPitch = -.2f - .75f * fast;
        p.rArmX = -.55f; p.rArmZ = 1.3f; p.rArmY = 0; p.rElbow = .12f;
        p.wristX = -1.5708f; p.wristY = time * 2.4f; p.spinRing = 1; p.spinMode = 3;
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
            case CHARGE -> charging(base.copy(), t);
            case DASH -> dash(base.copy(), t, in.charge(), in.lookPitch());
            case BEAM -> beam(base.copy(), t, in.lookPitch());
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
        // A still stance holds the hammer dead upright; the swings hand it back to that hold themselves
        // as they finish; every other action steers it with the wrist.
        int act = in.action();
        if (act != IDLE && act != CATCH && act != SWING_RIGHT && act != SWING_LEFT && act != UPPERCUT && act != DASH) p.upright = 0;
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
        p.rElbow = lerp(p.rElbow, 1.05f, wind); p.wristX = lerp(p.wristX, -.1f, wind);
        // The wrist cocks back: the hammer lies flat and trails behind the hand.
        p.wristZ = lerp(0, -.95f, wind);
        p.lArmX = lerp(p.lArmX, -.3f, wind); p.lArmZ = lerp(p.lArmZ, -.55f, wind);
        p.crouch = lerp(p.crouch, 2f, wind); p.lKnee = lerp(p.lKnee, .5f, wind);
        // Strike: shoulders, chest, arm and hips all go round together; the arm straightens.
        p.torsoYaw = lerp(p.torsoYaw, .78f, strike); p.torsoRoll = lerp(0, -.08f, strike);
        p.rArmX = lerp(p.rArmX, -1.48f, strike); p.rArmY = lerp(p.rArmY, 1.05f, strike); p.rArmZ = lerp(p.rArmZ, .55f, strike);
        p.rElbow = lerp(p.rElbow, .1f, strike); p.wristX = lerp(p.wristX, 0, strike);
        // ...and snaps through at the blow, the head flat and leading.
        p.wristZ = lerp(p.wristZ, .35f, strike);
        p.lArmX = lerp(p.lArmX, -.6f, strike); p.lArmY = lerp(p.lArmY, -.6f, strike); p.lArmZ = lerp(p.lArmZ, -.75f, strike);
        p.rLegX = lerp(p.rLegX, .25f, strike); p.lLegX = lerp(p.lLegX, -.35f, strike); p.rKnee = lerp(p.rKnee, .45f, strike);
        // Follow-through: momentum carries the arm on and down; it is NOT reset (the next hit starts here).
        p.torsoYaw = lerp(p.torsoYaw, .9f, settle); p.rArmY = lerp(p.rArmY, 1.3f, settle); p.rArmX = lerp(p.rArmX, -1.1f, settle);
        p.rElbow = lerp(p.rElbow, .4f, settle);
        // Right after the blow the wrist gathers the hammer back up to its upright hold.
        p.wristZ = lerp(p.wristZ, 0, settle);
        // As the swing starts the hammer turns out in the fist: handle pointing away from him and 45 degrees
        // up, the head leading, so the head is what lands. Gathered back upright after the blow.
        p.upright = settle; p.tilt = 0; p.lever = wind * (1 - settle);
        p.aura = strike * (1 - settle) * .6f;
        return p;
    }
    /** 2nd hit: from where the first ended, the body turns back and the hammer sweeps right to left. */
    static Pose swingLeft(Pose p, float t) {
        float wind = k(t, 0, 3), strike = snap(t, 3, SWING_HIT + 1), settle = k(t, SWING_HIT + 1, SWING_TICKS);
        // Starts from the first hit's follow-through: chest right, arm out right, elbow cocking.
        p.torsoYaw = lerp(.9f, .95f, wind); p.torsoPitch = .2f;
        // The arm stays long, drawn back out to his right at shoulder height (a bent elbow here would bring
        // the hammer in over his head).
        p.rArmX = lerp(-1.1f, -1.4f, wind); p.rArmY = lerp(1.3f, 1.55f, wind); p.rArmZ = lerp(.55f, .45f, wind);
        p.rElbow = lerp(.4f, .2f, wind); p.wristX = -.1f;
        // Wrist cocked the other way: the hammer trails out to his right, flat.
        p.wristZ = lerp(0, .95f, wind);
        p.lArmX = -.6f; p.lArmY = -.6f; p.lArmZ = -.75f;
        p.crouch = 2; p.rKnee = .5f; p.lKnee = .35f; p.rLegX = .25f; p.lLegX = -.35f;
        // Backhand: chest whips left, the arm crosses in front of the chest.
        p.torsoYaw = lerp(p.torsoYaw, -.75f, strike); p.torsoRoll = lerp(0, .08f, strike);
        p.rArmX = lerp(p.rArmX, -1.5f, strike); p.rArmY = lerp(p.rArmY, -1.05f, strike); p.rArmZ = lerp(p.rArmZ, -.2f, strike);
        p.rElbow = lerp(p.rElbow, .1f, strike); p.wristX = lerp(p.wristX, 0, strike);
        p.wristZ = lerp(p.wristZ, -.35f, strike);
        p.lArmX = lerp(p.lArmX, -.2f, strike); p.lArmY = lerp(p.lArmY, .2f, strike); p.lArmZ = lerp(p.lArmZ, -.35f, strike);
        p.rLegX = lerp(p.rLegX, -.25f, strike); p.lLegX = lerp(p.lLegX, .25f, strike);
        p.torsoYaw = lerp(p.torsoYaw, -.9f, settle); p.rArmY = lerp(p.rArmY, -1.35f, settle); p.rArmX = lerp(p.rArmX, -1.15f, settle);
        p.rElbow = lerp(p.rElbow, .55f, settle);
        p.wristZ = lerp(p.wristZ, 0, settle);
        // The same lever on the way back.
        p.upright = settle; p.tilt = 0; p.lever = wind * (1 - settle);
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
        p.rElbow = lerp(p.rElbow, .2f, load); p.wristX = lerp(p.wristX, .75f, load);   // cocked: the head hangs back and down
        p.lArmX = lerp(p.lArmX, -.75f, load); p.lArmZ = lerp(p.lArmZ, -.4f, load); p.lElbow = lerp(p.lElbow, .9f, load);
        // The drive: legs straighten, he lifts off a little, the hammer comes up past his face.
        p.crouch = lerp(p.crouch, -.5f, drive); p.rise = 3.5f * drive * (1 - settle);
        p.torsoPitch = lerp(p.torsoPitch, -.28f, drive); p.headPitch = lerp(p.headPitch, -.45f, drive);
        p.rKnee = lerp(p.rKnee, .1f, drive); p.lKnee = lerp(p.lKnee, .45f, drive); p.rLegX = lerp(p.rLegX, .1f, drive); p.lLegX = lerp(p.lLegX, -.4f, drive);
        p.rArmX = lerp(p.rArmX, -2.95f, drive); p.rArmZ = lerp(p.rArmZ, .12f, drive); p.rElbow = lerp(p.rElbow, .15f, drive);
        p.wristX = lerp(p.wristX, -.6f, drive);   // the wrist snaps it up through the blow
        p.lArmX = lerp(p.lArmX, .35f, drive); p.lArmZ = lerp(p.lArmZ, -.6f, drive);
        p.upright = settle;   // gathered back upright as soon as it has landed
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
        // The arm goes out to his side; Mjolnir turns about it, so it whirls in a wheel beside him, clear of his body.
        p.rArmX = lerp(p.rArmX, -.55f, raise); p.rArmZ = lerp(p.rArmZ, 1.3f, raise); p.rArmY = lerp(p.rArmY, 0, raise);
        p.rElbow = lerp(p.rElbow, .12f, raise);
        // Spin speed climbs from a lazy turn to a blur: angle is the integral of that speed.
        p.wristX = -1.5708f; p.wristY = .25f * t + .08f * t * t;
        p.spinRing = k(t, 4, TAKEOFF_TICKS); p.spinMode = 3;
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
        // The fist comes in to the middle of his chest: the wheel turns dead in front of him, like a shield.
        p.rArmX = lerp(p.rArmX, -1.35f, in); p.rArmY = lerp(p.rArmY, -.55f, in); p.rArmZ = lerp(p.rArmZ, 0, in); p.rElbow = lerp(p.rElbow, .3f, in);
        p.wristX = -1.5708f;
        p.wristY = spin(t, 2.9f, 3);
        p.spinRing = k(t, .5f, 3); p.spinMode = 2;
        p.lArmX = lerp(p.lArmX, -.85f, in); p.lArmZ = lerp(p.lArmZ, -.15f, in); p.lElbow = lerp(p.lElbow, 1.35f, in);
        return p;
    }
    /** Angle of a whirl that reaches full speed (rad/tick) over rampTicks, smoothly. */
    static float spin(float t, float speed, float rampTicks) {
        if (t <= rampTicks) return speed * t * t / (2 * rampTicks);
        return speed * (t - rampTicks / 2);
    }

    /** Shift held: Mjolnir whirling close in front of him, faster and faster. */
    static Pose charging(Pose p, float t) {
        float in = k(t, 0, 4), c = clamp(t / CHARGE_FULL);
        p.crouch = lerp(p.crouch, 1.8f + 1.2f * c, in); p.torsoPitch = lerp(p.torsoPitch, .15f, in);
        p.torsoYaw = lerp(p.torsoYaw, .3f, in); p.headPitch = lerp(p.headPitch, -.15f, in);
        p.rLegZ = .2f; p.lLegZ = -.2f; p.rLegX = .25f; p.lLegX = -.35f; p.rKnee = .45f; p.lKnee = .5f;
        // Arm a little out to his right side: the wheel turns beside him, edge-on to the front, close to his body.
        p.rArmX = lerp(p.rArmX, -.4f, in); p.rArmY = lerp(p.rArmY, 0, in); p.rArmZ = lerp(p.rArmZ, .6f, in); p.rElbow = lerp(p.rElbow, .2f, in);
        p.lArmX = lerp(p.lArmX, .25f, in); p.lArmZ = lerp(p.lArmZ, -.45f, in); p.lElbow = lerp(p.lElbow, .5f, in);
        p.wristX = -1.5708f;
        // Speeds up with the charge; at full charge it holds the top speed.
        float full = CHARGE_FULL;
        p.wristY = t <= full ? 1.2f * t + 1.7f * t * t / (2 * full) : 1.2f * full + 1.7f * full / 2 + 2.9f * (t - full);
        p.spinRing = k(t, 1, 5); p.spinMode = 3;
        p.eyes = c * c * .8f;
        return p;
    }
    /** Let go: the hammer flung out at arm's length, head first, and he rides it where he looks. */
    static Pose dash(Pose p, float t, float charge, float lookPitch) {
        float out = snap(t, 0, 2), end = k(t, dashTicks(charge) - 2, dashTicks(charge) + 3);
        Pose f = new Pose();
        f.bodyPitch = Math.max(.15f, Math.min(2.6f, 1.45f + lookPitch));
        f.headPitch = -.25f;
        f.rArmX = -3.0f; f.rArmZ = .08f; f.rArmY = 0; f.rElbow = .05f; f.wristX = 0;
        f.lArmX = .3f; f.lArmZ = -.2f; f.lElbow = .25f;
        f.rLegX = .18f; f.lLegX = .05f; f.rKnee = .3f; f.lKnee = .12f; f.rLegZ = .04f; f.lLegZ = -.04f;
        f.eyes = .4f + .6f * charge; f.aura = charge;
        p.toward(f, out * (1 - end));
        // The hammer straight out in line with the arm, head first (toward() leaves the wrist alone).
        float hold = out * (1 - end);
        p.wristX = lerp(p.wristX, 0, hold); p.wristY = lerp(p.wristY, 0, hold); p.wristZ = lerp(p.wristZ, 0, hold);
        // Mjolnir in line with the outstretched arm, head first, pulling him along (as in the comics).
        p.upright = 1 - hold;
        return p;
    }
    /** F: Mjolnir straight up to the sky, then levelled at what he looks at while the lightning pours out. */
    static Pose beam(Pose p, float t, float lookPitch) {
        float up = k(t, 0, 8), aim = snap(t, BM_AIM - 5, BM_AIM), done = k(t, BM_END, BM_TOTAL);
        p.rLegZ = lerp(p.rLegZ, .24f, up); p.lLegZ = lerp(p.lLegZ, -.24f, up); p.crouch = lerp(p.crouch, .8f, up);
        p.rArmX = lerp(p.rArmX, -3.1f, up); p.rArmZ = lerp(p.rArmZ, .1f, up); p.rArmY = lerp(p.rArmY, 0, up); p.rElbow = lerp(p.rElbow, .05f, up); p.wristX = lerp(p.wristX, 0, up);
        p.lArmX = lerp(p.lArmX, .08f, up); p.lArmZ = lerp(p.lArmZ, -.4f, up); p.lElbow = lerp(p.lElbow, .3f, up);
        p.torsoPitch = lerp(p.torsoPitch, -.18f, up); p.headPitch = lerp(p.headPitch, -.65f, up);
        p.eyes = up;
        // Levelled at the target; the left hand comes over to steady the right arm against the recoil.
        float tremble = t >= BM_AIM && t < BM_END ? (float) Math.sin(t * 3.1) * .035f + (float) Math.sin(t * 7.3) * .02f : 0;
        p.rArmX = lerp(p.rArmX, -1.57f + lookPitch + tremble, aim); p.rArmY = lerp(p.rArmY, .05f, aim);
        p.lArmX = lerp(p.lArmX, -1.25f + lookPitch * .8f, aim); p.lArmY = lerp(p.lArmY, -.45f, aim); p.lArmZ = lerp(p.lArmZ, .05f, aim); p.lElbow = lerp(p.lElbow, .85f, aim);
        p.torsoPitch = lerp(p.torsoPitch, .12f, aim); p.headPitch = lerp(p.headPitch, 0, aim);
        p.crouch = lerp(p.crouch, 1.8f, aim); p.rLegX = lerp(p.rLegX, .25f, aim); p.lLegX = lerp(p.lLegX, -.4f, aim);
        p.rKnee = lerp(p.rKnee, .5f, aim); p.lKnee = lerp(p.lKnee, .45f, aim);
        p.torsoYaw = lerp(p.torsoYaw, tremble * 2, aim);
        p.aura = Math.max(up * .5f, aim) * (1 - done);
        Pose rest = combat(t);
        p.toward(rest, done);
        p.eyes *= 1 - done * .7f;
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

    /**
     * X, "Aerial Punishment": Thor's own performance (where his body is comes from AerialPath).
     * Lunge, the two hits and the uppercut at the film's slower tempo; the whirl and the flight up;
     * the lock and the cry; letting go; the glide down and the soft landing.
     */
    static Pose ultimate(Pose p, float t) {
        p.set(combat(t));
        float s1 = ULT_HIT1 - SWING_HIT / ULT_SWING_PACE, s2 = ULT_HIT2 - SWING_HIT / ULT_SWING_PACE;
        if (t >= ULT_LUNGE && t < s1) {
            // Closing the distance: low, driving strides, the hammer drawn back.
            float k = clamp((t - ULT_LUNGE) / (s1 - ULT_LUNGE)), stride = (float) Math.sin(k * Math.PI * 2.2);
            p.bodyPitch = .35f * (float) Math.sin(Math.PI * k);
            p.rLegX = -.7f * stride; p.lLegX = .7f * stride; p.rKnee = .5f + .4f * Math.max(0, stride); p.lKnee = .5f + .4f * Math.max(0, -stride);
            p.rArmX = .35f; p.rElbow = .6f; p.wristX = -.6f; p.lArmX = -.6f * stride;
        }
        // The two hits, back to back: the second grows straight out of the first one's follow-through.
        if (t >= s1 && t < s2) return swingRight(p, (t - s1) * ULT_SWING_PACE);
        if (t >= s2 && t < ULT_LOAD) return swingLeft(p, (t - s2) * ULT_SWING_PACE);
        if (t >= ULT_LOAD && t < ULT_UPPER + 16) {
            Pose u = uppercut(p, Math.min(UPPER_TICKS, (t - ULT_LOAD) * UPPER_HIT / (float) (ULT_UPPER - ULT_LOAD)));
            u.headPitch = lerp(u.headPitch, -.95f, k(t, ULT_UPPER + 4, ULT_UPPER + 16));
            return u;
        }
        if (t >= ULT_UPPER + 16 && t < ULT_SPIN) {
            // Watching them go up.
            p.headPitch = -1.0f; p.torsoPitch = -.15f; p.eyes = k(t, ULT_UPPER + 16, ULT_SPIN) * .7f;
            return p;
        }
        if (t >= ULT_SPIN && t < ULT_RISE) {
            Pose w = takeoff(p, (t - ULT_SPIN) * TAKEOFF_TICKS / (float) (ULT_RISE - ULT_SPIN));
            w.headPitch = -.7f; w.eyes = .8f; w.aura = .3f;
            // The body draws back a little before the whirl throws him up.
            w.torsoPitch = lerp(w.torsoPitch, -.2f, k(t, ULT_RISE - 6, ULT_RISE));
            return w;
        }
        if (t >= ULT_RISE && t < ULT_CATCH) {
            Pose f = new Pose();
            f.bodyPitch = .2f; f.headPitch = -.6f;
            f.rArmX = -.7f; f.rArmZ = 1.3f; f.rElbow = .12f; f.wristX = -1.5708f; f.wristY = spin(t - ULT_RISE, 2.4f, 1) + 20; f.spinRing = 1; f.spinMode = 3;
            f.lArmX = .5f; f.lArmZ = -.45f; f.lElbow = .35f;
            float sway = (float) Math.sin(t * .4) * .08f;
            f.rLegX = .35f + sway; f.lLegX = .15f - sway; f.rKnee = .55f; f.lKnee = .3f;
            f.eyes = .9f; f.aura = .5f;
            // The throw: the arm whips straight up and lets Mjolnir go on into the storm.
            float whip = snap(t, ULT_TOSS - 4, ULT_TOSS);
            f.rArmX = lerp(f.rArmX, -3.1f, whip); f.rArmZ = lerp(f.rArmZ, .05f, whip); f.rElbow = lerp(f.rElbow, 0, whip);
            f.wristX = lerp(f.wristX, 0, whip); f.wristY *= 1 - whip;
            f.spinRing *= 1 - whip;
            if (t >= ULT_TOSS) { f.noHammer = 1; f.wristX = 0; }
            // Empty-handed now: both arms reach up for them, then open wide to take hold.
            float reach = k(t, ULT_TOSS + 4, ULT_APEX - 2);
            f.rArmX = lerp(f.rArmX, -2.3f, reach); f.rArmZ = lerp(f.rArmZ, .25f, reach); f.rElbow = lerp(f.rElbow, .25f, reach);
            f.lArmX = lerp(f.lArmX, -2.3f, reach); f.lArmZ = lerp(f.lArmZ, -.25f, reach); f.lElbow = lerp(f.lElbow, .25f, reach);
            float open = k(t, ULT_APEX - 2, ULT_CATCH - 2);
            f.rArmX = lerp(f.rArmX, -1.5f, open); f.rArmZ = lerp(f.rArmZ, 1.25f, open);
            f.lArmX = lerp(f.lArmX, -1.6f, open); f.lArmZ = lerp(f.lArmZ, -1.25f, open);
            f.headPitch = lerp(f.headPitch, -.3f, open);
            return f;
        }
        if (t >= ULT_CATCH && t < ULT_LET_GO) {
            Pose g = new Pose();
            g.noHammer = 1;
            // The lock: chest to chest, both arms round the body. The upper arms point forward rolled on
            // their side, so the elbows fold the forearms in flat behind the target's back; the left arm
            // rides higher, across the shoulders. They close in and squeeze.
            float close = snap(t, ULT_CATCH, ULT_CATCH + 4);
            float squeeze = (float) Math.sin(Math.PI * clamp((t - ULT_CATCH - 2) / 8f)) * .18f;
            g.rArmX = -1.45f; g.rArmZ = lerp(1.25f, 1.52f, close); g.rElbow = lerp(.3f, 1.25f, close) + squeeze;
            g.lArmX = lerp(-1.6f, -1.95f, close); g.lArmZ = lerp(-1.25f, -1.52f, close); g.lElbow = lerp(.3f, 1.2f, close) + squeeze;
            g.torsoPitch = -.08f; g.rKnee = .55f; g.lKnee = .4f; g.rLegX = .2f; g.lLegX = -.1f;
            g.headPitch = .1f;
            g.eyes = .9f; g.aura = .5f;
            // The cry: the whole body tightens, head thrown back, the arms crush tighter.
            float cry = k(t, ULT_SCREAM, ULT_SCREAM + 6);
            float shake = (float) Math.sin(t * 2.9) * .04f * cry;
            g.headPitch = lerp(g.headPitch, -.8f, cry) + shake; g.mouth = cry; g.torsoPitch = lerp(g.torsoPitch, -.3f, cry);
            g.rElbow += .1f * cry; g.lElbow += .1f * cry;
            g.rKnee = lerp(g.rKnee, .8f, cry); g.lKnee = lerp(g.lKnee, .7f, cry);
            g.eyes = Math.max(g.eyes, cry); g.aura = Math.max(g.aura, cry);
            return g;
        }
        if (t >= ULT_LET_GO && t < ULT_LANDED) {
            // Arms thrown open as he lets go, then the glide: upright, hammer at his side, legs hanging.
            Pose g = new Pose();
            float open = 1 - k(t, ULT_LET_GO + 4, ULT_LET_GO + 16);
            g.rArmX = lerp(-.35f, -1.0f, open); g.rArmZ = lerp(.25f, 1.0f, open); g.rElbow = lerp(.5f, .2f, open); g.wristX = lerp(-1.3f, -.2f, open);
            g.lArmX = lerp(-.1f, -1.0f, open); g.lArmZ = lerp(-.35f, -1.0f, open); g.lElbow = .3f;
            g.headPitch = lerp(.35f, -.3f, open); g.rKnee = .35f; g.lKnee = .2f; g.rLegX = .15f; g.lLegX = -.05f;
            g.eyes = 1; g.aura = .5f * open + .2f;
            // The hand goes up and Mjolnir comes back down out of the storm into it.
            float call = k(t, ULT_RECALL - AerialPath.RECALL_FALL, ULT_RECALL - 6) * (1 - k(t, ULT_RECALL + 2, ULT_RECALL + 12));
            g.rArmX = lerp(g.rArmX, -3.05f, call); g.rArmZ = lerp(g.rArmZ, .1f, call); g.rElbow = lerp(g.rElbow, .1f, call); g.wristX = lerp(g.wristX, 0, call);
            g.headPitch = lerp(g.headPitch, -.6f, call * (t < ULT_RECALL ? 1 : 0));
            float jolt = (float) Math.sin(Math.PI * clamp((t - ULT_RECALL) / 5f));
            g.rElbow += .6f * jolt; g.torsoPitch += .06f * jolt;
            g.noHammer = t < ULT_RECALL ? 1 : 0;
            // The landing: the knees take him.
            float touch = (float) Math.sin(Math.PI * clamp((t - (ULT_LANDED - 4)) / 10f));
            g.crouch = 3.5f * touch; g.rKnee += .7f * touch; g.lKnee += .8f * touch; g.torsoPitch = .2f * touch;
            return g;
        }
        if (t >= ULT_LANDED) {
            Pose g = combat(t);
            float settle = k(t, ULT_LANDED, ULT_LANDED + 14);
            g.toward(idle(t), settle);
            g.headPitch = .1f;
            g.eyes = 1 - .7f * k(t, ULT_LANDED + 6, ULT_TOTAL); g.aura = .3f * (1 - settle);
            return g;
        }
        return p;
    }
}
