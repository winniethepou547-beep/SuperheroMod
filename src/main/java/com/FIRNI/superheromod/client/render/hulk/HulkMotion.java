package com.FIRNI.superheromod.client.render.hulk;

import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * Banner's and Hulk's body language: every action as a pose over time, plus how far the body has
 * grown (size 0 = Banner, 1 = Hulk). Pure math. Same conventions as Thor's: radians in the model
 * frame (+y down, -z front, -x is his right); arm x negative raises the arm forward, arm y positive
 * swings it to his right, right arm z positive opens it outward (left arm: negative); torso yaw
 * positive turns the chest to his right; pitch positive leans forward; knees positive fold back.
 * Crouch and rise are model pixels (before Hulk's extra scale).
 */
public final class HulkMotion {
    public static final class Pose {
        public float size, bodyPitch, rise, crouch;
        public float torsoYaw, torsoPitch, torsoRoll, headPitch, headYaw;
        public float rArmX, rArmY, rArmZ, rElbow, lArmX, lArmY, lArmZ, lElbow;
        public float rLegX, rLegZ, rKnee, lLegX, lLegZ, lKnee;
        /** Fists clenched (0 open hand .. 1 fist), roaring mouth, gamma glow, rock held between the hands. */
        public float fists, mouth, gamma, rock, tremble;

        public Pose copy() { Pose p = new Pose(); p.set(this); return p; }
        public void set(Pose o) {
            size = o.size; bodyPitch = o.bodyPitch; rise = o.rise; crouch = o.crouch;
            torsoYaw = o.torsoYaw; torsoPitch = o.torsoPitch; torsoRoll = o.torsoRoll; headPitch = o.headPitch; headYaw = o.headYaw;
            rArmX = o.rArmX; rArmY = o.rArmY; rArmZ = o.rArmZ; rElbow = o.rElbow; lArmX = o.lArmX; lArmY = o.lArmY; lArmZ = o.lArmZ; lElbow = o.lElbow;
            rLegX = o.rLegX; rLegZ = o.rLegZ; rKnee = o.rKnee; lLegX = o.lLegX; lLegZ = o.lLegZ; lKnee = o.lKnee;
            fists = o.fists; mouth = o.mouth; gamma = o.gamma; rock = o.rock; tremble = o.tremble;
        }
        public void toward(Pose o, float k) {
            size += (o.size - size) * k; bodyPitch += (o.bodyPitch - bodyPitch) * k; rise += (o.rise - rise) * k; crouch += (o.crouch - crouch) * k;
            torsoYaw += (o.torsoYaw - torsoYaw) * k; torsoPitch += (o.torsoPitch - torsoPitch) * k; torsoRoll += (o.torsoRoll - torsoRoll) * k;
            headPitch += (o.headPitch - headPitch) * k; headYaw += (o.headYaw - headYaw) * k;
            rArmX += (o.rArmX - rArmX) * k; rArmY += (o.rArmY - rArmY) * k; rArmZ += (o.rArmZ - rArmZ) * k; rElbow += (o.rElbow - rElbow) * k;
            lArmX += (o.lArmX - lArmX) * k; lArmY += (o.lArmY - lArmY) * k; lArmZ += (o.lArmZ - lArmZ) * k; lElbow += (o.lElbow - lElbow) * k;
            rLegX += (o.rLegX - rLegX) * k; rLegZ += (o.rLegZ - rLegZ) * k; rKnee += (o.rKnee - rKnee) * k;
            lLegX += (o.lLegX - lLegX) * k; lLegZ += (o.lLegZ - lLegZ) * k; lKnee += (o.lKnee - lKnee) * k;
            fists += (o.fists - fists) * k; mouth += (o.mouth - mouth) * k; gamma += (o.gamma - gamma) * k; rock = o.rock; tremble += (o.tremble - tremble) * k;
        }
    }

    /** smash: the film's smash tick (it depends on the server's delay setting). */
    public record Input(int action, float t, boolean hulk, float charge, float time, boolean grounded, float fallSpeed, float lookPitch, int smash) {}

    private HulkMotion() {}

    static float clamp(float t) { return t < 0 ? 0 : t > 1 ? 1 : t; }
    static float ease(float t) { t = clamp(t); return t * t * (3 - 2 * t); }
    static float k(float t, float a, float b) { return ease((t - a) / (b - a)); }
    static float snap(float t, float a, float b) { float x = clamp((t - a) / (b - a)); return 1 - (1 - x) * (1 - x) * (1 - x); }
    static float lerp(float a, float b, float k) { return a + (b - a) * k; }

    // ------------------------------------------------------------------ stances
    /** Bruce Banner: upright, a little round-shouldered, hands loose. */
    public static Pose banner(float time) {
        Pose p = new Pose();
        float breathe = (float) Math.sin(time * .08) * .02f;
        p.torsoPitch = .06f + breathe; p.headPitch = -.04f;
        p.rArmZ = .07f; p.lArmZ = -.07f; p.rElbow = .15f; p.lElbow = .15f; p.rArmX = .02f - breathe; p.lArmX = .02f - breathe;
        p.rLegZ = .03f; p.lLegZ = -.03f;
        return p;
    }
    /** Hulk: hunched, shoulders forward, arms hanging wide off the lats, fists half closed, heavy breathing. */
    public static Pose hulk(float time) {
        Pose p = new Pose();
        p.size = 1;
        float breathe = (float) Math.sin(time * .1) * .035f;
        p.crouch = 1 + breathe * 10; p.torsoPitch = .26f + breathe; p.headPitch = -.18f;
        p.rArmZ = .28f; p.lArmZ = -.28f; p.rArmX = -.08f - breathe; p.lArmX = -.08f - breathe; p.rElbow = .35f; p.lElbow = .35f;
        p.rArmY = .12f; p.lArmY = -.12f;
        p.rLegZ = .13f; p.lLegZ = -.13f; p.rKnee = .12f; p.lKnee = .12f;
        p.fists = .7f;
        return p;
    }

    public static Pose sample(Input in) {
        float t = in.t();
        Pose base = in.hulk() ? hulk(in.time()) : banner(in.time());
        Pose p = switch (in.action()) {
            case TRANSFORM -> transform(t, in.time());
            case REVERT -> revert(t, in.time());
            case PUNCH_RIGHT -> punch(base.copy(), t, true);
            case PUNCH_LEFT -> punch(base.copy(), t, false);
            case PUNCH_CHARGE -> charging(base.copy(), t, in.charge());
            case PUNCH_RELEASE -> release(base.copy(), t, in.charge());
            case GUARD -> guard(base.copy(), t);
            case THUNDERCLAP -> clap(base.copy(), t);
            case POUND -> pound(base.copy(), t);
            case LEAP_CHARGE -> t < LEAP_TAP ? base : squat(base.copy(), t - LEAP_TAP, in.charge());
            case LEAP -> air(base.copy(), t, in.fallSpeed());
            case LANDING -> landing(base.copy(), t);
            case ROCK -> rock(base.copy(), t);
            case ULTIMATE -> ultimate(t, in.time(), in.smash());
            default -> base;
        };
        if (p.tremble > 0) {
            float s = p.tremble * .05f;
            p.torsoRoll += (float) Math.sin(in.time() * 3.7) * s; p.rArmX += (float) Math.sin(in.time() * 4.3) * s; p.lArmX += (float) Math.sin(in.time() * 3.9 + 1) * s;
        }
        return p;
    }

    // ------------------------------------------------------------------ the change
    /** Banner doubles over clutching his head; the body swells, fists clench, shoulders spread; the roar. */
    static Pose transform(float t, float time) {
        Pose p = banner(time);
        float clutch = k(t, 0, 7) * (1 - k(t, GROW_START + 6, GROW_START + 14));
        p.torsoPitch = lerp(p.torsoPitch, .65f, clutch); p.headPitch = lerp(p.headPitch, .35f, clutch);
        p.rArmX = lerp(p.rArmX, -2.5f, clutch); p.lArmX = lerp(p.lArmX, -2.5f, clutch); p.rElbow = lerp(p.rElbow, 2.1f, clutch); p.lElbow = lerp(p.lElbow, 2.1f, clutch);
        p.rArmZ = lerp(p.rArmZ, .4f, clutch); p.lArmZ = lerp(p.lArmZ, -.4f, clutch);
        p.rKnee = p.lKnee = .45f * clutch; p.crouch = 2 * clutch;
        p.tremble = k(t, 2, 8);
        float grow = k(t, GROW_START, GROW_END);
        p.size = grow;
        Pose h = hulk(time);
        // Fists clench and drive down, shoulders spread, chest heaves up.
        float clench = k(t, GROW_START + 4, GROW_END - 4);
        h.rArmX = .15f; h.lArmX = .15f; h.rArmZ = .55f; h.lArmZ = -.55f; h.rElbow = .8f; h.lElbow = .8f; h.fists = 1;
        h.torsoPitch = -.05f; h.crouch = 2.5f; h.rKnee = h.lKnee = .35f;
        p.toward(h, clench);
        p.size = grow;
        // The roar: head thrown back, arms out and flexed.
        float roar = k(t, GROW_END - 2, GROW_END + 3) * (1 - k(t, TRANSFORM_TICKS - 4, TRANSFORM_TICKS));
        p.headPitch = lerp(p.headPitch, -.7f, roar); p.mouth = roar; p.torsoPitch = lerp(p.torsoPitch, -.25f, roar);
        p.rArmZ = lerp(p.rArmZ, 1.05f, roar); p.lArmZ = lerp(p.lArmZ, -1.05f, roar); p.rElbow = lerp(p.rElbow, 1.7f, roar); p.lElbow = lerp(p.lElbow, 1.7f, roar);
        p.rArmX = lerp(p.rArmX, -.3f, roar); p.lArmX = lerp(p.lArmX, -.3f, roar);
        p.gamma = Math.max(grow * .7f, roar);
        p.tremble = Math.max(p.tremble * (1 - grow), roar * .6f);
        p.fists = Math.max(clench, roar);
        return p;
    }
    /** The anger drains: the body shrinks, he sags, hands on his knees, then straightens as Banner. */
    static Pose revert(float t, float time) {
        Pose h = hulk(time);
        float shrink = k(t, 4, 22);
        Pose sag = banner(time);
        sag.torsoPitch = .6f; sag.rArmX = -.75f; sag.lArmX = -.75f; sag.rElbow = .2f; sag.lElbow = .2f; sag.crouch = 2.5f; sag.rKnee = sag.lKnee = .5f;
        sag.headPitch = .3f;
        h.toward(sag, k(t, 2, 14));
        h.size = 1 - shrink;
        h.toward(banner(time), k(t, 22, REVERT_TICKS));
        h.size = 1 - shrink;
        return h;
    }

    // ------------------------------------------------------------------ punches
    /** A straight punch: the shoulder draws back, then the whole side drives through. */
    static Pose punch(Pose p, float t, boolean right) {
        float wind = k(t, 0, 2), strike = snap(t, 2, PUNCH_HIT), back = k(t, PUNCH_HIT + 1, PUNCH_TICKS);
        float side = right ? 1 : -1;
        float yaw = lerp(lerp(0, -.45f * side, wind), .5f * side, strike);
        p.torsoYaw = lerp(yaw, 0, back); p.torsoPitch = lerp(p.torsoPitch, .35f, strike * (1 - back));
        float ax = lerp(lerp(p.rArmX, -.5f, wind), -1.6f, strike), el = lerp(lerp(p.rElbow, 1.9f, wind), .05f, strike);
        if (right) { p.rArmX = lerp(ax, p.rArmX, back); p.rElbow = lerp(el, p.rElbow, back); p.rArmZ = lerp(p.rArmZ, .05f, strike * (1 - back)); p.rArmY = lerp(p.rArmY, -.15f, strike * (1 - back)); }
        else { p.lArmX = lerp(ax, p.lArmX, back); p.lElbow = lerp(el, p.lElbow, back); p.lArmZ = lerp(p.lArmZ, -.05f, strike * (1 - back)); p.lArmY = lerp(p.lArmY, .15f, strike * (1 - back)); }
        // The other arm comes up to guard.
        if (right) { p.lArmX = lerp(p.lArmX, -1.1f, wind * (1 - back)); p.lElbow = lerp(p.lElbow, 1.6f, wind * (1 - back)); }
        else { p.rArmX = lerp(p.rArmX, -1.1f, wind * (1 - back)); p.rElbow = lerp(p.rElbow, 1.6f, wind * (1 - back)); }
        if (right) { p.lLegX = lerp(p.lLegX, -.35f, strike * (1 - back)); p.rLegX = lerp(p.rLegX, .2f, strike * (1 - back)); }
        else { p.rLegX = lerp(p.rLegX, -.35f, strike * (1 - back)); p.lLegX = lerp(p.lLegX, .2f, strike * (1 - back)); }
        p.fists = 1;
        return p;
    }
    /** Left button held: the right fist pulled far back by the hip, gamma gathering in it, the body coiling. */
    static Pose charging(Pose p, float t, float charge) {
        float in = k(t, 0, 5);
        p.torsoYaw = lerp(p.torsoYaw, -.75f, in); p.torsoPitch = lerp(p.torsoPitch, .2f, in);
        p.rArmX = lerp(p.rArmX, .55f, in); p.rArmZ = lerp(p.rArmZ, .35f, in); p.rElbow = lerp(p.rElbow, 1.9f, in);
        p.lArmX = lerp(p.lArmX, -1.3f, in); p.lArmY = lerp(p.lArmY, .3f, in); p.lElbow = lerp(p.lElbow, .5f, in);
        p.crouch = lerp(p.crouch, 3, in); p.lLegX = lerp(p.lLegX, -.5f, in); p.rLegX = lerp(p.rLegX, .35f, in); p.lKnee = .5f; p.rKnee = .35f;
        p.fists = 1; p.gamma = charge; p.tremble = charge * charge;
        return p;
    }
    /** Let go: everything behind one enormous straight right. */
    static Pose release(Pose p, float t, float charge) {
        Pose c = charging(p.copy(), 10, charge);
        float strike = snap(t, 0, RELEASE_HIT), back = k(t, RELEASE_HIT + 4, RELEASE_TICKS);
        c.torsoYaw = lerp(-.75f, .85f, strike); c.torsoPitch = lerp(.2f, .45f, strike);
        c.rArmX = lerp(.55f, -1.6f, strike); c.rArmZ = lerp(.35f, .05f, strike); c.rArmY = lerp(0, -.25f, strike); c.rElbow = lerp(1.9f, 0, strike);
        c.lArmX = lerp(-1.3f, .3f, strike); c.lArmY = 0; c.lArmZ = lerp(c.lArmZ, -.6f, strike);
        c.lLegX = -.7f; c.lKnee = .7f; c.rLegX = .45f; c.crouch = 3.5f;
        c.gamma = charge * (1 - back); c.tremble = 0;
        c.toward(p, back);
        return c;
    }
    /** Right button: forearms up in front of the face, chin down, braced low. */
    static Pose guard(Pose p, float t) {
        float in = snap(t, 0, 3);
        p.rArmX = lerp(p.rArmX, -1.55f, in); p.rArmY = lerp(p.rArmY, -.55f, in); p.rArmZ = lerp(p.rArmZ, 0, in); p.rElbow = lerp(p.rElbow, 1.75f, in);
        p.lArmX = lerp(p.lArmX, -1.6f, in); p.lArmY = lerp(p.lArmY, .55f, in); p.lArmZ = lerp(p.lArmZ, 0, in); p.lElbow = lerp(p.lElbow, 1.8f, in);
        p.torsoPitch = lerp(p.torsoPitch, .32f, in); p.headPitch = lerp(p.headPitch, .1f, in); p.crouch = lerp(p.crouch, 2.5f, in);
        p.rLegX = .2f; p.lLegX = -.3f; p.rKnee = .35f; p.lKnee = .4f; p.fists = 1;
        return p;
    }

    // ------------------------------------------------------------------ abilities
    /** Thunderclap: down onto one knee, arms flung wide and back, then the hands slam together in front. */
    static Pose clap(Pose p, float t) {
        float kneel = k(t, 0, 7), spread = k(t, 2, CLAP_HIT - 3), slam = snap(t, CLAP_HIT - 3, CLAP_HIT), up = k(t, CLAP_HIT + 9, CLAP_TICKS);
        p.crouch = lerp(p.crouch, 6.5f, kneel);
        p.lLegX = lerp(p.lLegX, -1.25f, kneel); p.lKnee = lerp(p.lKnee, 1.5f, kneel);
        p.rLegX = lerp(p.rLegX, .45f, kneel); p.rKnee = lerp(p.rKnee, 1.75f, kneel);
        p.torsoPitch = lerp(p.torsoPitch, .3f, kneel); p.headPitch = lerp(p.headPitch, -.3f, kneel);
        p.rArmX = lerp(p.rArmX, -1.25f, spread); p.rArmZ = lerp(p.rArmZ, 1.45f, spread); p.rElbow = lerp(p.rElbow, .2f, spread);
        p.lArmX = lerp(p.lArmX, -1.25f, spread); p.lArmZ = lerp(p.lArmZ, -1.45f, spread); p.lElbow = lerp(p.lElbow, .2f, spread);
        p.torsoPitch = lerp(p.torsoPitch, -.1f, spread);
        // The clap: both arms whip in, palms meet in front of the chest.
        p.rArmZ = lerp(p.rArmZ, 0, slam); p.rArmY = lerp(p.rArmY, -.62f, slam); p.rArmX = lerp(p.rArmX, -1.5f, slam);
        p.lArmZ = lerp(p.lArmZ, 0, slam); p.lArmY = lerp(p.lArmY, .62f, slam); p.lArmX = lerp(p.lArmX, -1.5f, slam);
        p.torsoPitch = lerp(p.torsoPitch, .35f, slam);
        p.fists = 1 - spread * (1 - slam) * .9f - slam * .9f;
        p.gamma = slam * (1 - k(t, CLAP_HIT, CLAP_HIT + 8)) * .8f;
        p.toward(hulk(t), up);
        return p;
    }
    /** Ground pound: both fists high over his head, then hammered down into the ground in front. */
    static Pose pound(Pose p, float t) {
        float raise = k(t, 0, POUND_HIT - 3), slam = snap(t, POUND_HIT - 3, POUND_HIT), up = k(t, POUND_HIT + 8, POUND_TICKS);
        p.crouch = lerp(p.crouch, 2.5f, raise); p.torsoPitch = lerp(p.torsoPitch, -.2f, raise); p.headPitch = lerp(p.headPitch, -.3f, raise);
        p.rArmX = lerp(p.rArmX, -2.9f, raise); p.rArmZ = lerp(p.rArmZ, .1f, raise); p.rArmY = lerp(p.rArmY, -.2f, raise); p.rElbow = lerp(p.rElbow, .9f, raise);
        p.lArmX = lerp(p.lArmX, -2.9f, raise); p.lArmZ = lerp(p.lArmZ, -.1f, raise); p.lArmY = lerp(p.lArmY, .2f, raise); p.lElbow = lerp(p.lElbow, .9f, raise);
        p.crouch = lerp(p.crouch, 6.5f, slam); p.torsoPitch = lerp(p.torsoPitch, .85f, slam); p.headPitch = lerp(p.headPitch, -.6f, slam);
        p.rArmX = lerp(p.rArmX, -.75f, slam); p.lArmX = lerp(p.lArmX, -.75f, slam); p.rElbow = lerp(p.rElbow, .05f, slam); p.lElbow = lerp(p.lElbow, .05f, slam);
        p.rKnee = lerp(p.rKnee, 1.2f, slam); p.lKnee = lerp(p.lKnee, 1.2f, slam); p.rLegX = lerp(p.rLegX, -.5f, slam); p.lLegX = lerp(p.lLegX, -.5f, slam);
        p.fists = 1; p.gamma = slam * (1 - up) * .5f;
        p.toward(hulk(t), up);
        return p;
    }
    /** Space held: a deep squat, arms swung back, the whole body loading up. */
    static Pose squat(Pose p, float t, float charge) {
        float in = k(t, 0, 4);
        p.crouch = lerp(p.crouch, 2 + 5 * charge, in); p.torsoPitch = lerp(p.torsoPitch, .45f + .2f * charge, in); p.headPitch = lerp(p.headPitch, -.5f, in);
        p.rArmX = lerp(p.rArmX, .75f, in); p.lArmX = lerp(p.lArmX, .75f, in); p.rElbow = lerp(p.rElbow, .4f, in); p.lElbow = lerp(p.lElbow, .4f, in);
        p.rArmZ = lerp(p.rArmZ, .4f, in); p.lArmZ = lerp(p.lArmZ, -.4f, in);
        p.fists = 1; p.tremble = charge > .95f ? .6f : 0; p.gamma = charge * .4f;
        return p;
    }
    /** In the air: thrown upward with arms flung back, then over the top the arms come up for the landing. */
    static Pose air(Pose p, float t, float fallSpeed) {
        float launch = snap(t, 0, 3), falling = clamp(fallSpeed / .8f);
        p.crouch = 0; p.torsoPitch = lerp(.1f, .35f, falling); p.headPitch = -.4f;
        p.rArmX = lerp(.9f, -2.3f, falling); p.lArmX = lerp(.9f, -2.3f, falling); p.rArmZ = lerp(.6f, .5f, falling); p.lArmZ = lerp(-.6f, -.5f, falling);
        p.rElbow = lerp(.3f, .9f, falling); p.lElbow = lerp(.3f, .9f, falling);
        p.rLegX = lerp(.3f, -.4f, falling); p.lLegX = lerp(.1f, -.2f, falling); p.rKnee = lerp(.8f, .5f, falling); p.lKnee = lerp(.5f, .4f, falling);
        p.fists = 1;
        return p;
    }
    /** Touchdown: driven deep into a crouch, one fist slammed into the ground, then up. */
    static Pose landing(Pose p, float t) {
        float hit = 1 - k(t, 3, LANDING_TICKS);
        p.crouch = lerp(p.crouch, 7, hit); p.torsoPitch = lerp(p.torsoPitch, .8f, hit);
        p.rArmX = lerp(p.rArmX, -.75f, hit); p.rElbow = lerp(p.rElbow, .05f, hit); p.rArmZ = lerp(p.rArmZ, .15f, hit);
        p.lArmX = lerp(p.lArmX, .4f, hit); p.lArmZ = lerp(p.lArmZ, -.9f, hit);
        p.rKnee = lerp(p.rKnee, 1.4f, hit); p.lKnee = lerp(p.lKnee, 1.3f, hit); p.rLegX = lerp(p.rLegX, -.6f, hit); p.lLegX = lerp(p.lLegX, -.4f, hit);
        p.headPitch = lerp(p.headPitch, -.5f, hit);
        return p;
    }
    /** Rock: crouch and dig both hands into the ground, tear it up, heave it overhead, hurl it. */
    static Pose rock(Pose p, float t) {
        float down = k(t, 0, ROCK_GRAB - 2), tear = k(t, ROCK_GRAB, ROCK_LIFT), throwing = snap(t, ROCK_LIFT + 2, ROCK_THROW), back = k(t, ROCK_THROW + 2, ROCK_TICKS);
        p.crouch = lerp(p.crouch, 6, down); p.torsoPitch = lerp(p.torsoPitch, .9f, down); p.headPitch = lerp(p.headPitch, -.6f, down);
        p.rArmX = lerp(p.rArmX, -.65f, down); p.rArmY = lerp(p.rArmY, -.35f, down); p.rElbow = lerp(p.rElbow, .2f, down); p.rArmZ = lerp(p.rArmZ, .1f, down);
        p.lArmX = lerp(p.lArmX, -.65f, down); p.lArmY = lerp(p.lArmY, .35f, down); p.lElbow = lerp(p.lElbow, .2f, down); p.lArmZ = lerp(p.lArmZ, -.1f, down);
        p.rKnee = lerp(p.rKnee, 1.1f, down); p.lKnee = lerp(p.lKnee, 1.1f, down); p.rLegX = lerp(p.rLegX, -.5f, down); p.lLegX = lerp(p.lLegX, -.5f, down);
        // Up it comes, all the way over the head.
        p.crouch = lerp(p.crouch, 1, tear); p.torsoPitch = lerp(p.torsoPitch, -.25f, tear); p.headPitch = lerp(p.headPitch, -.5f, tear);
        p.rArmX = lerp(p.rArmX, -3.0f, tear); p.lArmX = lerp(p.lArmX, -3.0f, tear); p.rElbow = lerp(p.rElbow, 1.1f, tear); p.lElbow = lerp(p.lElbow, 1.1f, tear);
        p.rArmY = lerp(p.rArmY, -.25f, tear); p.lArmY = lerp(p.lArmY, .25f, tear);
        p.rKnee = lerp(p.rKnee, .3f, tear); p.lKnee = lerp(p.lKnee, .3f, tear); p.rLegX = lerp(p.rLegX, .1f, tear); p.lLegX = lerp(p.lLegX, -.3f, tear);
        // And out: the arms whip over and forward.
        p.rArmX = lerp(p.rArmX, -1.4f, throwing); p.lArmX = lerp(p.lArmX, -1.4f, throwing); p.rElbow = lerp(p.rElbow, .1f, throwing); p.lElbow = lerp(p.lElbow, .1f, throwing);
        p.torsoPitch = lerp(p.torsoPitch, .45f, throwing); p.lLegX = lerp(p.lLegX, -.6f, throwing); p.lKnee = lerp(p.lKnee, .6f, throwing);
        p.rock = t >= ROCK_GRAB - 1 && t < ROCK_THROW ? 1 : 0;
        p.fists = .2f;
        p.toward(hulk(t), back);
        return p;
    }

    // ------------------------------------------------------------------ Gamma Rage
    /** Hulk's own performance in the film; where his body is comes from RagePath. */
    static Pose ultimate(float t, float time, int smash) {
        Pose p = hulk(time);
        if (t < ULT_ROAR) {
            // Gamma gathers: fists clenched, braced, trembling harder and harder.
            float g = k(t, 0, ULT_ROAR);
            p.rArmX = .15f; p.lArmX = .15f; p.rArmZ = .6f; p.lArmZ = -.6f; p.rElbow = .9f; p.lElbow = .9f; p.crouch = 3; p.rKnee = p.lKnee = .4f;
            p.torsoPitch = .15f; p.headPitch = .1f; p.fists = 1; p.gamma = g; p.tremble = g;
            return p;
        }
        if (t < ULT_LEAP) {
            float roar = k(t, ULT_ROAR, ULT_ROAR + 4);
            p.headPitch = lerp(.1f, -.75f, roar); p.mouth = roar; p.torsoPitch = lerp(.15f, -.3f, roar);
            p.rArmZ = 1.1f; p.lArmZ = -1.1f; p.rArmX = -.4f; p.lArmX = -.4f; p.rElbow = 1.7f; p.lElbow = 1.7f; p.crouch = 2;
            p.fists = 1; p.gamma = 1; p.tremble = .7f;
            Pose crouch = squat(hulk(time), 10, 1);
            p.toward(crouch, k(t, ULT_LEAP - 6, ULT_LEAP));
            return p;
        }
        if (t < ULT_GRAB) {
            Pose a = air(hulk(time), 5, t > (ULT_LEAP + ULT_GRAB) / 2f ? .6f : 0);
            // Reaching out for them as he comes down on them.
            float reach = k(t, ULT_GRAB - 6, ULT_GRAB);
            a.rArmX = lerp(a.rArmX, -1.7f, reach); a.rElbow = lerp(a.rElbow, .3f, reach); a.rArmZ = lerp(a.rArmZ, .2f, reach);
            a.gamma = .8f;
            return a;
        }
        if (t < ULT_SKY) {
            // One arm up, the target hanging from his fist, kicking.
            float lift = k(t, ULT_GRAB, ULT_GRAB + 8);
            p.rArmX = lerp(-1.7f, -2.6f, lift); p.rArmZ = .2f; p.rElbow = lerp(.3f, .25f, lift);
            p.lArmX = -.3f; p.lArmZ = -.7f; p.lElbow = .6f;
            p.torsoPitch = -.1f; p.headPitch = -.5f; p.crouch = 2 * (1 - lift);
            p.gamma = .8f; p.fists = 1;
            p.toward(squat(p.copy(), 10, 1), k(t, ULT_SKY - 5, ULT_SKY) * .5f);
            p.rArmX = lerp(-1.7f, -2.6f, lift);
            return p;
        }
        if (t < ULT_HOLD) {
            Pose a = air(hulk(time), 5, 0);
            a.rArmX = -2.9f; a.rArmZ = .15f; a.rElbow = .2f;
            a.headPitch = -.7f; a.gamma = .9f;
            return a;
        }
        if (t < ULT_THROW) {
            // Both hands on them, held up in front of his face; the roar into them.
            float both = k(t, ULT_HOLD, ULT_HOLD + 6), roar = k(t, ULT_HOLD + 10, ULT_HOLD + 14) * (1 - k(t, ULT_THROW - 6, ULT_THROW));
            p.crouch = 0; p.rLegX = .3f; p.lLegX = -.1f; p.rKnee = .7f; p.lKnee = .5f;
            p.rArmX = lerp(-2.9f, -1.75f, both); p.rArmY = lerp(0, -.45f, both); p.rElbow = lerp(.2f, .9f, both); p.rArmZ = lerp(.15f, 0, both);
            p.lArmX = lerp(-.3f, -1.75f, both); p.lArmY = lerp(0, .45f, both); p.lElbow = lerp(.6f, .9f, both); p.lArmZ = lerp(-.7f, 0, both);
            p.torsoPitch = lerp(-.1f, .25f, roar); p.headPitch = lerp(-.3f, .15f, roar); p.mouth = roar; p.tremble = roar;
            p.gamma = .9f + .1f * roar;
            return p;
        }
        if (t < ULT_CRASH + 10) {
            // The throw: both arms whip down, flinging them at the ground.
            float fling = snap(t, ULT_THROW, ULT_THROW + 5);
            p.crouch = 0; p.rLegX = .4f; p.lLegX = .1f; p.rKnee = .9f; p.lKnee = .7f;
            p.rArmX = lerp(-1.75f, .3f, fling); p.lArmX = lerp(-1.75f, .3f, fling); p.rArmY = lerp(-.45f, 0, fling); p.lArmY = lerp(.45f, 0, fling);
            p.rElbow = lerp(.9f, .2f, fling); p.lElbow = lerp(.9f, .2f, fling); p.rArmZ = .3f; p.lArmZ = -.3f;
            p.torsoPitch = lerp(.25f, .6f, fling); p.headPitch = -.6f; p.gamma = .8f;
            return p;
        }
        if (t < smash) {
            // Hanging up there, then both fists raised high overhead for the dive.
            float raise = k(t, ULT_RAISE, ULT_RAISE + 8);
            p.crouch = 0; p.rLegX = .3f; p.lLegX = .1f; p.rKnee = .6f; p.lKnee = .4f;
            p.rArmX = lerp(.3f, -3.0f, raise); p.lArmX = lerp(.3f, -3.0f, raise); p.rArmY = lerp(0, -.25f, raise); p.lArmY = lerp(0, .25f, raise);
            p.rElbow = lerp(.2f, .5f, raise); p.lElbow = lerp(.2f, .5f, raise); p.rArmZ = lerp(.3f, .05f, raise); p.lArmZ = lerp(-.3f, -.05f, raise);
            p.torsoPitch = lerp(.3f, -.3f, raise); p.headPitch = lerp(-.6f, .1f, raise); p.mouth = raise * .7f;
            p.gamma = .8f + .2f * raise; p.fists = 1;
            return p;
        }
        if (t < smash + 30) {
            // The smash: both fists driven into the ground, kneeling in the crater.
            float hit = snap(t, smash, smash + 2);
            p.crouch = 8 * hit; p.torsoPitch = .9f * hit; p.headPitch = -.5f;
            p.rArmX = -.7f; p.lArmX = -.7f; p.rElbow = .05f; p.lElbow = .05f; p.rArmY = -.25f; p.lArmY = .25f;
            p.rKnee = 1.4f; p.lKnee = 1.5f; p.rLegX = -.6f; p.lLegX = .3f;
            p.gamma = 1 - k(t, smash + 4, smash + 30) * .7f; p.fists = 1;
            return p;
        }
        // Up out of the dust: the power stance, a last roar.
        float rise = k(t, smash + 30, smash + 42), roar = k(t, smash + 42, smash + 48);
        p.rArmZ = lerp(.28f, .9f, rise); p.lArmZ = lerp(-.28f, -.9f, rise); p.rElbow = lerp(.35f, 1.4f, rise); p.lElbow = lerp(.35f, 1.4f, rise);
        p.torsoPitch = lerp(.26f, -.15f, rise); p.headPitch = lerp(-.18f, -.55f, roar); p.mouth = roar;
        p.crouch = 2 * rise; p.rKnee = p.lKnee = .3f; p.fists = 1; p.gamma = .3f + .3f * roar;
        return p;
    }
}
