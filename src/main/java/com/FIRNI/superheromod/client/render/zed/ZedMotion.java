package com.FIRNI.superheromod.client.render.zed;

import static com.FIRNI.superheromod.heroes.zed.ZedAction.*;

/**
 * Zed's body language as pure pose math: keen and economical. A loose, ready stance with one foot
 * ahead; slashes that snap across and are back in guard at once; a short, sharp throw; a low reach
 * to send his shadow; a crouch and a whirl for the Shadow Slash; a dead-still lock, a lunge that
 * vanishes into shadow, and a crouched strike behind the target. His shadows use the same poses.
 * Angles in radians; arm X negative raises the arm forward, arm Y negative turns a raised arm in
 * across the body, arm Z positive lifts the right arm out (negative the left).
 */
public final class ZedMotion {
    public static final class Pose {
        public float crouch, bodyPitch, torsoYaw, torsoPitch, torsoRoll, headPitch, headYaw;
        public float rArmX, rArmY, rArmZ, rElbow, lArmX, lArmY, lArmZ, lElbow;
        public float rLegX, rLegZ, rKnee, lLegX, lLegZ, lKnee;
        /** 0 solid .. 1 dissolved into shadow (the Death Mark dash); blades glowing with the passive. */
        public float vanish, glow;

        public Pose right(float x, float y, float z, float elbow) { rArmX = x; rArmY = y; rArmZ = z; rElbow = elbow; return this; }
        public Pose left(float x, float y, float z, float elbow) { lArmX = x; lArmY = y; lArmZ = z; lElbow = elbow; return this; }
        public Pose legs(float rx, float rz, float rKnee, float lx, float lz, float lKnee) {
            rLegX = rx; rLegZ = rz; this.rKnee = rKnee; lLegX = lx; lLegZ = lz; this.lKnee = lKnee; return this;
        }
        public Pose copy() {
            Pose p = new Pose();
            p.crouch = crouch; p.bodyPitch = bodyPitch; p.torsoYaw = torsoYaw; p.torsoPitch = torsoPitch; p.torsoRoll = torsoRoll; p.headPitch = headPitch; p.headYaw = headYaw;
            p.rArmX = rArmX; p.rArmY = rArmY; p.rArmZ = rArmZ; p.rElbow = rElbow; p.lArmX = lArmX; p.lArmY = lArmY; p.lArmZ = lArmZ; p.lElbow = lElbow;
            p.rLegX = rLegX; p.rLegZ = rLegZ; p.rKnee = rKnee; p.lLegX = lLegX; p.lLegZ = lLegZ; p.lKnee = lKnee; p.vanish = vanish; p.glow = glow;
            return p;
        }
        public void toward(Pose o, float k) {
            crouch += (o.crouch - crouch) * k; bodyPitch += (o.bodyPitch - bodyPitch) * k; torsoYaw += (o.torsoYaw - torsoYaw) * k;
            torsoPitch += (o.torsoPitch - torsoPitch) * k; torsoRoll += (o.torsoRoll - torsoRoll) * k; headPitch += (o.headPitch - headPitch) * k; headYaw += (o.headYaw - headYaw) * k;
            rArmX += (o.rArmX - rArmX) * k; rArmY += (o.rArmY - rArmY) * k; rArmZ += (o.rArmZ - rArmZ) * k; rElbow += (o.rElbow - rElbow) * k;
            lArmX += (o.lArmX - lArmX) * k; lArmY += (o.lArmY - lArmY) * k; lArmZ += (o.lArmZ - lArmZ) * k; lElbow += (o.lElbow - lElbow) * k;
            rLegX += (o.rLegX - rLegX) * k; rLegZ += (o.rLegZ - rLegZ) * k; rKnee += (o.rKnee - rKnee) * k;
            lLegX += (o.lLegX - lLegX) * k; lLegZ += (o.lLegZ - lLegZ) * k; lKnee += (o.lKnee - lKnee) * k;
            vanish += (o.vanish - vanish) * k; glow += (o.glow - glow) * k;
        }
    }

    private ZedMotion() {}

    static float clamp(float t) { return t < 0 ? 0 : t > 1 ? 1 : t; }
    static float ease(float t) { t = clamp(t); return t * t * (3 - 2 * t); }
    static float k(float t, float a, float b) { return ease((t - a) / (b - a)); }
    /** Fast out of the start, eased into the end: a snap. */
    static float snap(float t, float a, float b) { float x = clamp((t - a) / (b - a)); return 1 - (1 - x) * (1 - x) * (1 - x); }
    static float lerp(float a, float b, float k) { return a + (b - a) * k; }

    /** Ready: upright but loose, left foot ahead, knees soft, hands low and ready at his sides. */
    public static Pose idle(float time) {
        Pose p = new Pose();
        float breathe = (float) Math.sin(time * .09) * .025f;
        p.crouch = .8f; p.torsoPitch = .08f + breathe; p.headPitch = -.05f;
        p.rArmX = -.12f - breathe; p.rArmZ = .14f; p.rElbow = .35f; p.lArmX = -.18f - breathe; p.lArmZ = -.14f; p.lElbow = .4f;
        p.rLegX = .16f; p.lLegX = -.2f; p.rKnee = .18f; p.lKnee = .14f; p.rLegZ = .05f; p.lLegZ = -.05f;
        p.torsoYaw = .08f;
        return p;
    }

    public static Pose sample(int action, float t, float time) {
        Pose p = idle(time);
        return switch (action) {
            case SLASH_RIGHT -> slash(p, t, true);
            case SLASH_LEFT -> slash(p, t, false);
            case THROW -> toss(p, t);
            case SHADOW_CAST -> cast(p, t);
            case SWAP, MARK_RETURN -> swap(p, t);
            case SPIN -> spin(p, t);
            case MARK_LOCK -> lock(p, t);
            case MARK_DASH, MARK_HIDDEN, ULTIMATE -> gone(p);
            case MARK_STRIKE -> strike(p, t);
            default -> p;
        };
    }

    /** One arm cuts flat across in front of him, the shoulders turning with it; then straight back to guard. */
    static Pose slash(Pose p, float t, boolean right) {
        float draw = k(t, 0, 1.5f), cut = snap(t, 1.5f, SLASH_HIT + .5f), back = k(t, SLASH_HIT + 1, SLASH_TICKS);
        float s = right ? 1 : -1;
        float armX = lerp(-1.35f, -1.45f, cut), armY = lerp(.9f, -1.0f, cut) * s, elbow = lerp(1.0f, .15f, cut);
        float w = draw * (1 - back);
        if (right) { p.rArmX = lerp(p.rArmX, armX, w); p.rArmY = lerp(p.rArmY, armY, w); p.rElbow = lerp(p.rElbow, elbow, w); }
        else { p.lArmX = lerp(p.lArmX, armX, w); p.lArmY = lerp(p.lArmY, armY, w); p.lElbow = lerp(p.lElbow, elbow, w); }
        p.torsoYaw += s * lerp(.35f, -.45f, cut) * w;
        p.crouch += 1.2f * w; p.torsoPitch += .1f * w;
        if (right) p.lArmX = lerp(p.lArmX, -.6f, w); else p.rArmX = lerp(p.rArmX, -.6f, w);
        return p;
    }
    /** The throw: shoulder back, the arm draws in, then snaps out at the target, the other arm out for balance. */
    static Pose toss(Pose p, float t) {
        float draw = k(t, 0, THROW_RELEASE - .5f), out = snap(t, THROW_RELEASE - .5f, THROW_RELEASE + 1), back = k(t, THROW_RELEASE + 2, THROW_TICKS);
        float w = 1 - back;
        p.torsoYaw = lerp(p.torsoYaw, lerp(.55f, -.4f, out), w);
        p.rArmX = lerp(p.rArmX, lerp(.2f, -1.55f, out), w * Math.max(draw, out)); p.rElbow = lerp(p.rElbow, lerp(1.6f, .05f, out), w * Math.max(draw, out));
        p.rArmY = lerp(p.rArmY, -.15f * out, w); p.rArmZ = lerp(p.rArmZ, lerp(.4f, .1f, out), w);
        p.lArmX = lerp(p.lArmX, -1.1f, w * draw); p.lArmZ = lerp(p.lArmZ, -.35f, w * draw); p.lElbow = lerp(p.lElbow, .2f, w * draw);
        p.rLegX = lerp(p.rLegX, .4f, w); p.lLegX = lerp(p.lLegX, -.45f, w); p.crouch += 1.5f * w;
        return p;
    }
    /** Sending the shadow: a low crouch, one hand reaching out along the ground. */
    static Pose cast(Pose p, float t) {
        float in = snap(t, 0, 2), back = k(t, 4, CAST_TICKS);
        float w = in * (1 - back);
        p.crouch += 3.5f * w; p.torsoPitch += .4f * w; p.headPitch -= .3f * w;
        p.rArmX = lerp(p.rArmX, -1.25f, w); p.rElbow = lerp(p.rElbow, .05f, w); p.rArmZ = lerp(p.rArmZ, .1f, w);
        p.lArmX = lerp(p.lArmX, .5f, w); p.lArmZ = lerp(p.lArmZ, -.4f, w);
        p.rLegX = lerp(p.rLegX, .5f, w); p.lLegX = lerp(p.lLegX, -.7f, w); p.lKnee = lerp(p.lKnee, .9f, w); p.rKnee = lerp(p.rKnee, .6f, w);
        return p;
    }
    /** Arriving out of a swap: landing low, then rising. */
    static Pose swap(Pose p, float t) {
        float w = 1 - k(t, 0, SWAP_TICKS + 2);
        p.crouch += 3 * w; p.torsoPitch += .3f * w; p.rArmZ += .5f * w; p.lArmZ -= .5f * w; p.rKnee += .5f * w; p.lKnee += .5f * w;
        return p;
    }
    /** Shadow Slash: drop low, arms flung out, a whole turn of the body cutting round. */
    static Pose spin(Pose p, float t) {
        float load = k(t, 0, SPIN_HIT - 1), turn = snap(t, SPIN_HIT - 1, SPIN_HIT + 3), back = k(t, SPIN_HIT + 4, SPIN_TICKS);
        float w = 1 - back;
        p.crouch += 3 * w; p.torsoPitch += .25f * w;
        float open = Math.max(load * .5f, turn);
        p.rArmZ = lerp(p.rArmZ, 1.35f, open * w); p.lArmZ = lerp(p.lArmZ, -1.35f, open * w);
        p.rArmX = lerp(p.rArmX, -.35f, open * w); p.lArmX = lerp(p.lArmX, -.35f, open * w); p.rElbow = lerp(p.rElbow, .1f, w); p.lElbow = lerp(p.lElbow, .1f, w);
        p.torsoYaw += (float) (Math.PI * 2 * turn) * w - .5f * load * (1 - turn);
        p.rLegZ += .2f * w; p.lLegZ -= .2f * w; p.rKnee += .5f * w; p.lKnee += .5f * w;
        return p;
    }
    /** The lock: dead still, square to the target, arms a little out, blades ready; he sinks into his own shadow. */
    static Pose lock(Pose p, float t) {
        float w = snap(t, 0, 2);
        p.crouch += 1.5f * w; p.torsoPitch += .15f * w; p.headPitch = lerp(p.headPitch, -.1f, w); p.torsoYaw = lerp(p.torsoYaw, 0, w);
        p.rArmZ = lerp(p.rArmZ, .5f, w); p.lArmZ = lerp(p.lArmZ, -.5f, w); p.rArmX = lerp(p.rArmX, .25f, w); p.lArmX = lerp(p.lArmX, .25f, w);
        p.glow = w;
        p.vanish = k(t, 1, LOCK_TICKS);
        return p;
    }
    /** The lunge his shadow copies run in: thrown forward, one arm back, one ahead. */
    public static Pose dash(float time) {
        Pose p = idle(time);
        p.bodyPitch = .55f; p.crouch = 2;
        p.rArmX = .9f; p.rElbow = .2f; p.lArmX = -1.2f; p.lElbow = .2f;
        p.rLegX = .9f; p.rKnee = .5f; p.lLegX = -.9f; p.lKnee = .2f;
        p.glow = 1;
        return p;
    }
    /** Not in the world at all (gone into shadow; or a film draws him). */
    static Pose gone(Pose p) { p.vanish = 1; return p; }
    /** Arriving behind them: crouched, blades crossed out, then up, calm. */
    static Pose strike(Pose p, float t) {
        float w = 1 - k(t, 3, STRIKE_TICKS);
        p.crouch += 4 * w; p.torsoPitch += .3f * w;
        p.rArmX = lerp(p.rArmX, -.9f, w); p.rArmY = lerp(p.rArmY, .7f, w); p.rElbow = lerp(p.rElbow, .2f, w);
        p.lArmX = lerp(p.lArmX, -.9f, w); p.lArmY = lerp(p.lArmY, -.7f, w); p.lElbow = lerp(p.lElbow, .2f, w);
        p.rKnee += .7f * w; p.lKnee += .9f * w; p.lLegX -= .4f * w;
        p.vanish = 1 - k(t, 0, 2);
        p.glow = w;
        return p;
    }
    /** A key pose built by hand (the films): crouch, lean; torso yaw, pitch, roll; head pitch. */
    public static Pose of(float crouch, float bodyPitch, float torsoYaw, float torsoPitch, float torsoRoll, float headPitch) {
        Pose p = new Pose();
        p.crouch = crouch; p.bodyPitch = bodyPitch; p.torsoYaw = torsoYaw; p.torsoPitch = torsoPitch; p.torsoRoll = torsoRoll; p.headPitch = headPitch;
        return p;
    }
}
