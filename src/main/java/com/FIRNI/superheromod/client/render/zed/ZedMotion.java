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

    /**
     * The fighting stance: side-on, left foot ahead and right foot back, a little wider than the shoulders,
     * knees soft; the right shoulder drawn back so he is narrow to the target; both forearms up close to
     * the body, blades forward; the weight drifting slowly between the feet, a small breath.
     */
    public static Pose idle(float time) {
        Pose p = new Pose();
        float breathe = (float) Math.sin(time * .09) * .025f, shift = (float) Math.sin(time * .045);
        p.crouch = 1.2f + .15f * shift; p.torsoPitch = .12f + breathe; p.torsoYaw = .3f; p.torsoRoll = .03f * shift; p.headPitch = -.05f;
        p.rArmX = -.45f - breathe; p.rArmY = .15f; p.rArmZ = .2f; p.rElbow = 1.1f;
        p.lArmX = -.7f - breathe; p.lArmY = -.2f; p.lArmZ = -.15f; p.lElbow = .9f;
        p.rLegX = .25f; p.lLegX = -.25f; p.rKnee = .35f + .05f * shift; p.lKnee = .3f - .05f * shift; p.rLegZ = .12f; p.lLegZ = -.1f;
        return p;
    }

    public static Pose sample(int action, float t, float time) {
        Pose p = idle(time);
        return switch (action) {
            case SLASH_RIGHT -> cutRight(p, t);
            case SLASH_LEFT -> cutLeft(p, t);
            case SLASH_FINISH -> finisher(p, t);
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

    /** The blades flare for the instant of a cut. */
    static float flare(float t, float hit) { return Math.min(k(t, hit - 2, hit - .5f), 1 - k(t, hit + .5f, hit + 3)); }
    /**
     * First cut, the rear (right) hand: the shoulder draws back a touch and the elbow folds, then shoulder,
     * elbow and wrist open in one quick flat arc across; the arm comes home round a low curve, not back
     * along the same line, and the body gives back part of its turn.
     */
    static Pose cutRight(Pose p, float t) {
        float load = k(t, 0, 1.2f), cut = snap(t, 1.2f, SLASH_HIT + .3f), home = k(t, SLASH_HIT + .5f, SLASH_TICKS);
        float w = 1 - home;
        float x = lerp(lerp(-.45f, -1.1f, load), -1.45f, cut), y = lerp(lerp(.15f, .95f, load), -1.05f, cut), el = lerp(lerp(1.1f, 1.3f, load), .15f, cut);
        // The wrist turns the blade over at the very end of the cut.
        float z = lerp(lerp(.2f, .35f, load), .15f, cut) - .25f * k(t, SLASH_HIT - .3f, SLASH_HIT + .4f);
        // Home round a low arc: the arm drops as it comes back.
        float dip = .55f * (float) Math.sin(Math.PI * home);
        p.rArmX = lerp(x, p.rArmX, home) + dip; p.rArmY = lerp(y, p.rArmY, home); p.rArmZ = lerp(z, p.rArmZ, home); p.rElbow = lerp(el, p.rElbow, home);
        p.torsoYaw = lerp(p.torsoYaw + .25f * load * (1 - cut) - .85f * cut, p.torsoYaw - .25f, home) ;
        p.torsoYaw = lerp(p.torsoYaw, .3f, home * home);
        p.headYaw = .4f * cut * w; p.crouch += .6f * cut * w; p.torsoPitch += .1f * cut * w;
        p.lArmX = lerp(p.lArmX, -.35f, w); p.lArmY = lerp(p.lArmY, .35f, w);
        p.glow = flare(t, SLASH_HIT);
        return p;
    }
    /**
     * Second cut, the front (left) hand, not a mirror of the first: it starts high and wide, the left
     * shoulder drawn back, and comes down across on a diagonal; the weight rolls onto the front foot.
     */
    static Pose cutLeft(Pose p, float t) {
        float load = k(t, 0, 1.3f), cut = snap(t, 1.3f, SLASH_HIT + .3f), home = k(t, SLASH_HIT + .5f, SLASH_TICKS);
        float w = 1 - home;
        float x = lerp(lerp(-.7f, -1.75f, load), -.85f, cut), y = lerp(lerp(-.2f, -.85f, load), 1.05f, cut), el = lerp(lerp(.9f, 1.2f, load), .2f, cut);
        float z = lerp(lerp(-.15f, -.45f, load), -.1f, cut) + .2f * k(t, SLASH_HIT - .3f, SLASH_HIT + .4f);
        float dip = .45f * (float) Math.sin(Math.PI * home);
        p.lArmX = lerp(x, p.lArmX, home) + dip; p.lArmY = lerp(y, p.lArmY, home); p.lArmZ = lerp(z, p.lArmZ, home); p.lElbow = lerp(el, p.lElbow, home);
        float turn = lerp(lerp(.3f, -.25f, load), .95f, cut);
        p.torsoYaw = lerp(turn, .3f, home);
        p.headYaw = -.35f * cut * w; p.torsoRoll += .14f * cut * w; p.crouch += .4f * cut * w;
        p.lLegX -= .12f * cut * w; p.lKnee += .2f * cut * w;
        p.rArmX = lerp(p.rArmX, -.3f, w); p.rArmY = lerp(p.rArmY, -.25f, w); p.rElbow = lerp(p.rElbow, 1.3f, w);
        p.glow = flare(t, SLASH_HIT);
        return p;
    }
    /**
     * The finisher: knees bend, the body coils away from the cut, the right blade goes back and up; then
     * hips, waist, shoulder and arm all let go together in one deep diagonal cut down across, the body
     * reaching after it over a lunging front leg.
     */
    static Pose finisher(Pose p, float t) {
        float load = k(t, 0, 2.8f), cut = snap(t, 2.8f, FINISH_HIT + .4f), home = k(t, FINISH_HIT + 1.5f, FINISH_TICKS);
        float w = 1 - home;
        p.crouch += (2.4f * load * (1 - cut) + 1.6f * cut) * w;
        p.torsoYaw = lerp(lerp(.3f, 1.0f, load), -.85f, cut);
        p.torsoYaw = lerp(p.torsoYaw, .3f, home);
        p.torsoPitch += (.25f * load * (1 - cut) + .45f * cut) * w;
        p.bodyPitch += .25f * cut * w;
        float x = lerp(lerp(-.45f, -2.4f, load), -.7f, cut), y = lerp(lerp(.15f, .6f, load), -1.2f, cut), el = lerp(lerp(1.1f, 1.5f, load), .1f, cut);
        p.rArmX = lerp(x, p.rArmX, home) + .5f * (float) Math.sin(Math.PI * home); p.rArmY = lerp(y, p.rArmY, home); p.rElbow = lerp(el, p.rElbow, home);
        p.rArmZ = lerp(lerp(.2f, .5f, load), .1f, cut) * w + p.rArmZ * home;
        // The other arm swings back for balance.
        p.lArmX = lerp(p.lArmX, lerp(-.9f, .65f, cut), w); p.lArmZ = lerp(p.lArmZ, -.45f, w); p.lElbow = lerp(p.lElbow, .4f, w);
        // Lunging onto the front leg.
        p.lLegX -= .35f * cut * w; p.lKnee += .45f * cut * w; p.rLegX += .25f * cut * w;
        p.headYaw = .5f * cut * w; p.headPitch += .15f * cut * w;
        p.glow = flare(t, FINISH_HIT) * 1.2f;
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
