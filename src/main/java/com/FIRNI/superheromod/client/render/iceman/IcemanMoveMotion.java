package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Iceman's body in the brush and the two slides (signs at the top of PantherMotion: positive pitch leans forward,
 * positive yaw turns his front to his right, positive roll leans him left, arm X negative raises the arm forward, arm Z
 * positive takes it out to the side, knee and elbow positive bend, leg X negative lifts the leg forward).
 * <ul>
 * <li>BRUSH on a body: both hands come up before him at shoulder height, palms out, fingers spread, a slight lean into
 * it, the weight braced on the front foot; the arms keep working (small alternating pushes, the fingers flexing) as if
 * pushing the cold out of them. Aimed with his look (not in his own view, where the camera already turns with it).</li>
 * <li>BRUSH sculpting: the right hand leads, reaching out and drawing in small circles; the left supports it, open,
 * nearer the chest; the right shoulder forward.</li>
 * <li>SLIDE: a surfer's crouch on the ice (left foot ahead, right foot behind, knees bent, the hips turned across), the
 * left arm forward, the right back for balance; lower with speed and when diving, leaning back a little when climbing,
 * banking into the turns with the arms countering, never quite still.</li>
 * <li>DASH: dropped very low into an aggressive slide, the right leg forward and bent, the left folded under and behind,
 * the left hand trailing on the ground, the right fist forward; up again at the end.</li>
 * </ul>
 * Every move is key poses on smooth curves (Track(true)); what changes mid-move (the brush on a body or sculpting, the
 * slide's lean) is eased per body, never snapped.
 */
public final class IcemanMoveMotion {
    private IcemanMoveMotion() {}

    /** The move's pose at its clock t over the base, or null to keep the base. */
    public static Pose sample(int action, float t, Pose base, IcemanMotion.Ctx c) {
        return switch (action) {
            case BRUSH -> brush(t, base, c);
            case SLIDE -> slide(t, base, c);
            case DASH -> dash(t, base, c);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ eased per body
    /** Per body: the brush's share of sculpting (0 on a body .. 1 sculpting), the slide's lean, and when last eased. */
    private static final class Eased { float sculpt = -1, lean, dip, time = -1; }
    private static final Map<Integer, Eased> EASED = new HashMap<>();
    private static Eased eased(IcemanMotion.Ctx c) {
        if (EASED.size() > 64) EASED.clear();
        return EASED.computeIfAbsent(c.entity(), id -> new Eased());
    }
    /** The ticks since this body was last eased (0..3; 0 the first time). */
    private static float step(Eased e, float time) {
        float dt = e.time < 0 ? 0 : Mth.clamp(time - e.time, 0, 3);
        e.time = time;
        return dt;
    }
    /** His own body seen from inside (his own view): the camera turns with the look already. */
    private static boolean firstPerson(int entity) {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == entity && mc.options.getCameraType().isFirstPerson();
    }

    // ------------------------------------------------------------------ RMB: the brush
    static Pose brush(float t, Pose base, IcemanMotion.Ctx c) {
        Eased e = eased(c);
        float dt = step(e, c.time());
        float want = c.brushBody() ? 0 : 1;
        if (e.sculpt < 0) e.sculpt = want;
        else e.sculpt += (want - e.sculpt) * (1 - (float) Math.exp(-dt * .3f));
        float aim = firstPerson(c.entity()) ? 0 : Mth.clamp(c.lookPitch(), -1.2f, 1.1f);
        Pose held = stream(base, c.time(), aim);
        if (e.sculpt > .001f) held.toward(sculpt(base, c.time(), aim), e.sculpt);
        // The hands come up over BRUSH_RAISE, pushing out a little past the hold as the cold leaves them.
        Pose push = held.copy();
        for (int side = 0; side < 2; side++) push.armAdd(side, SH_FWD, .6f).armAdd(side, ELBOW, -.12f).armAdd(side, CURL, -.05f);
        push.add(SPINE_PITCH, .05f);
        Pose gather = base.copy().add(CROUCH, .5f).add(SPINE_PITCH, .04f);
        for (int side = 0; side < 2; side++)
            gather.arm(side, SH_FWD, .1f).arm(side, ARM_X, -.75f + aim * .4f).arm(side, ARM_Z, .25f).arm(side, ELBOW, 1.3f).arm(side, CURL, .55f).arm(side, WRIST_Z, .2f);
        Pose p = t >= BRUSH_RAISE + 3 ? held
                : new Track(true).key(0, base).key(BRUSH_RAISE * .45f, gather).key(BRUSH_RAISE, push).key(BRUSH_RAISE + 3, held).sample(t);
        return work(p, t, c.time(), e.sculpt);
    }
    /** On a body: both hands before him, palms out, fingers spread, braced and leaning into it. */
    private static Pose stream(Pose base, float time, float aim) {
        Pose p = base.copy();
        p.add(CROUCH, 1.1f).set(SPINE_PITCH, .14f).set(CHEST_PITCH, .05f).set(HEAD_PITCH, -.05f).set(PELVIS_YAW, -.12f).set(CHEST_YAW, .06f).set(NECK, .45f);
        // Braced (added over the stance and the walk under it): the left foot ahead, the right behind.
        p.legAdd(1, LEG_X, -.24f).legAdd(1, KNEE, .2f).legAdd(0, LEG_X, .2f).legAdd(0, KNEE, .12f).legAdd(0, LEG_Y, .12f);
        for (int side = 0; side < 2; side++)
            p.arm(side, SH_FWD, 1.1f).arm(side, SH_UP, .3f).arm(side, ARM_X, -1.45f + aim * .85f).arm(side, ARM_Y, -.22f).arm(side, ARM_Z, .05f)
                    .arm(side, ELBOW, .28f).arm(side, WRIST_X, -.08f).arm(side, WRIST_Z, .85f).arm(side, CURL, .02f);
        return p;
    }
    /** Sculpting: the right hand leads (drawing), the left supports it. */
    private static Pose sculpt(Pose base, float time, float aim) {
        Pose p = base.copy();
        p.add(CROUCH, .6f).set(SPINE_PITCH, .1f).set(CHEST_YAW, -.2f).set(SPINE_YAW, -.06f).set(HEAD_PITCH, -.04f).set(HEAD_YAW, .1f).set(NECK, .4f);
        p.legAdd(0, LEG_X, -.18f).legAdd(0, KNEE, .15f).legAdd(1, LEG_X, .16f).legAdd(1, KNEE, .08f).legAdd(1, LEG_Y, .14f);
        p.arm(0, SH_FWD, 1.25f).arm(0, SH_UP, .2f).arm(0, ARM_X, -1.5f + aim * .9f).arm(0, ARM_Y, -.05f).arm(0, ARM_Z, .12f).arm(0, ELBOW, .15f)
                .arm(0, WRIST_X, -.25f).arm(0, WRIST_Z, .3f).arm(0, CURL, .42f);
        p.arm(1, SH_FWD, .6f).arm(1, ARM_X, -.95f + aim * .4f).arm(1, ARM_Y, -.45f).arm(1, ARM_Z, .05f).arm(1, ELBOW, 1.15f)
                .arm(1, WRIST_X, -.1f).arm(1, WRIST_Z, .6f).arm(1, CURL, .1f);
        return p;
    }
    /**
     * The arms working while it flows (added on top once the hands are up): on a body, small alternating pushes and the
     * fingers flexing; sculpting, the right hand drawing small circles and the left following a little.
     */
    private static Pose work(Pose p, float t, float time, float sculpt) {
        float on = PantherMotion.k(t, BRUSH_RAISE, BRUSH_RAISE + 4);
        if (on <= 0) return p;
        float body = 1 - sculpt;
        for (int side = 0; side < 2; side++) {
            float pulse = Mth.sin(time * .55f + side * Mth.PI), flex = .5f + .5f * Mth.sin(time * 1.1f + side * 2);
            p.armAdd(side, SH_FWD, .35f * pulse * on * body).armAdd(side, ELBOW, -.08f * pulse * on * body)
                    .armAdd(side, WRIST_Z, .08f * Mth.sin(time * .9f + side) * on).armAdd(side, CURL, .08f * flex * on);
        }
        float circle = time * .32f;
        p.armAdd(0, ARM_Y, .12f * Mth.sin(circle) * on * sculpt).armAdd(0, ARM_X, .1f * Mth.cos(circle) * on * sculpt)
                .armAdd(1, ARM_Y, .05f * Mth.sin(circle - .6f) * on * sculpt);
        p.add(SPINE_PITCH, .02f * Mth.sin(time * .21f) * on).add(CHEST_YAW, .03f * Mth.sin(circle) * on * sculpt);
        return p;
    }

    // ------------------------------------------------------------------ SHIFT: the ice slide
    /** Riding upright-ish (depth 0) and low and tucked (depth 1). */
    private static Pose ride(Pose base, float depth) {
        Pose hi = base.copy(), lo;
        hi.set(PLANT, 0).set(LIFT, -2.9f).set(CROUCH, 0).set(SHIFT_X, 0).set(SHIFT_Z, 0).set(ROOT_PITCH, 0).set(ROOT_ROLL, 0)
                .set(PELVIS_YAW, .45f).set(PELVIS_PITCH, .05f).set(PELVIS_ROLL, 0).set(SPINE_YAW, -.18f).set(SPINE_PITCH, .3f).set(SPINE_ROLL, 0)
                .set(CHEST_YAW, -.22f).set(CHEST_PITCH, .06f).set(HEAD_YAW, -.1f).set(HEAD_PITCH, -.36f).set(NECK, .55f);
        // Left foot ahead, right foot behind, both knees bent, the soles on the ice.
        hi.leg(1, LEG_X, -1.0f).leg(1, LEG_Z, .16f).leg(1, LEG_Y, -.1f).leg(1, KNEE, 1.2f).leg(1, ANKLE, -.2f);
        hi.leg(0, LEG_X, .45f).leg(0, LEG_Z, .2f).leg(0, LEG_Y, .35f).leg(0, KNEE, .45f).leg(0, ANKLE, -.55f);
        // The left arm forward, the right back for balance.
        hi.arm(1, SH_FWD, .6f).arm(1, SH_UP, .2f).arm(1, ARM_X, -1.2f).arm(1, ARM_Y, .1f).arm(1, ARM_Z, .35f).arm(1, ELBOW, .35f)
                .arm(1, WRIST_X, -.1f).arm(1, WRIST_Z, .2f).arm(1, CURL, .25f);
        hi.arm(0, SH_FWD, -.4f).arm(0, SH_UP, .1f).arm(0, ARM_X, .75f).arm(0, ARM_Y, 0).arm(0, ARM_Z, .55f).arm(0, ELBOW, .3f)
                .arm(0, WRIST_X, .2f).arm(0, WRIST_Z, .1f).arm(0, CURL, .35f);
        if (depth <= .001f) return hi;
        lo = hi.copy();
        lo.set(LIFT, -4.4f).set(SPINE_PITCH, .46f).set(CHEST_PITCH, .1f).set(HEAD_PITCH, -.52f).set(NECK, .7f);
        lo.leg(1, LEG_X, -1.2f).leg(1, KNEE, 1.65f).leg(1, ANKLE, -.35f);
        lo.leg(0, LEG_X, .6f).leg(0, KNEE, .5f).leg(0, ANKLE, -.6f);
        lo.arm(1, ARM_X, -1.0f).arm(1, ELBOW, .55f).arm(1, ARM_Z, .45f);
        lo.arm(0, ARM_X, .95f).arm(0, ARM_Z, .5f).arm(0, ELBOW, .25f);
        hi.toward(lo, Mth.clamp(depth, 0, 1));
        return hi;
    }
    static Pose slide(float t, Pose base, IcemanMotion.Ctx c) {
        Eased e = eased(c);
        float dt = step(e, c.time());
        float speed = Mth.clamp(c.speed() / SLIDE_SPEED, 0, 1);
        float pitch = Mth.clamp(c.lookPitch(), -1.2f, 1.2f);
        // Smoothed: the bank into the turn, how low he rides.
        float lean = Mth.clamp(c.turn() * 2.8f, -.45f, .45f);
        float dip = Mth.clamp(speed * .6f + Math.max(0, pitch) * .8f, 0, 1);
        float k = 1 - (float) Math.exp(-dt * .35f);
        e.lean += (lean - e.lean) * k;
        e.dip += (dip - e.dip) * k;
        Pose riding = ride(base, e.dip);
        // Dropping onto the ice: a quick load into a deep crouch with the arms gathered, then up into the ride.
        Pose load = riding.copy().add(LIFT, -1.6f).add(SPINE_PITCH, .15f);
        load.arm(1, ARM_X, -.7f).arm(1, ELBOW, 1.1f).arm(0, ARM_X, .3f).arm(0, ELBOW, .9f);
        Pose p = new Track(true).key(0, base).key(2.5f, load).key(7, riding).sample(Math.min(t, 7));
        if (t >= 7) p = riding;
        float on = PantherMotion.k(t, 3, 8);
        float b = e.lean * on;
        // Banking into the turn (leaning toward its inside), the arms countering it.
        p.add(ROOT_ROLL, -b).add(SPINE_ROLL, -.25f * b).add(HEAD_ROLL, .5f * b);
        p.armAdd(1, ARM_Z, .7f * Math.max(0, b)).armAdd(0, ARM_Z, .7f * Math.max(0, -b)).armAdd(1, ARM_X, -.2f * Math.abs(b));
        // Climbing leans him back a little on the ramp, diving tips him forward.
        p.add(ROOT_PITCH, .22f * pitch * on).add(HEAD_PITCH, -.15f * pitch * on);
        // Never quite still: balancing.
        float time = c.time();
        p.add(ROOT_ROLL, .025f * Mth.sin(time * .17f) * on).add(LIFT, .25f * Mth.sin(time * .31f) * on);
        p.armAdd(1, ARM_X, .06f * Mth.sin(time * .23f)).armAdd(0, ARM_Z, .06f * Mth.sin(time * .19f + 1)).armAdd(0, ARM_X, .05f * Mth.sin(time * .27f + 2));
        p.armAdd(1, CURL, .08f * Mth.sin(time * .4f)).armAdd(0, CURL, .08f * Mth.sin(time * .37f + 1));
        return p;
    }

    // ------------------------------------------------------------------ CTRL: the sub-zero slide
    static Pose dash(float t, Pose base, IcemanMotion.Ctx c) {
        Pose drop = base.copy().set(PLANT, 1).set(CROUCH, 5f).set(SPINE_PITCH, .55f).set(CHEST_YAW, -.25f).set(HEAD_PITCH, -.5f).set(NECK, .6f);
        drop.leg(0, LEG_X, -.35f).leg(1, LEG_X, .25f);
        drop.arm(0, SH_FWD, -.3f).arm(0, ARM_X, .4f).arm(0, ARM_Z, .2f).arm(0, ELBOW, 1.5f).arm(0, CURL, 1);
        drop.arm(1, SH_FWD, .4f).arm(1, ARM_X, -.7f).arm(1, ARM_Z, .4f).arm(1, ELBOW, .3f).arm(1, CURL, .2f);
        Pose low = base.copy().set(PLANT, 0).set(LIFT, -4.6f).set(CROUCH, 0).set(SHIFT_X, 0).set(SHIFT_Z, 0)
                .set(ROOT_PITCH, 0).set(PELVIS_YAW, 0).set(PELVIS_PITCH, 0).set(PELVIS_ROLL, 0)
                .set(SPINE_PITCH, .28f).set(SPINE_ROLL, .22f).set(CHEST_PITCH, .1f).set(CHEST_YAW, -.2f)
                .set(HEAD_PITCH, -.45f).set(HEAD_ROLL, -.15f).set(HEAD_YAW, .05f).set(NECK, .5f);
        // The right leg forward and bent, the left folded under him with the shin trailing.
        low.leg(0, LEG_X, -1.25f).leg(0, LEG_Z, .12f).leg(0, LEG_Y, .1f).leg(0, KNEE, .85f).leg(0, ANKLE, -.15f);
        low.leg(1, LEG_X, .25f).leg(1, LEG_Z, .25f).leg(1, LEG_Y, .2f).leg(1, KNEE, 1.35f).leg(1, ANKLE, .8f);
        // The left hand trailing on the ground beside him, the right fist forward.
        low.arm(1, SH_FWD, -.2f).arm(1, SH_UP, -.4f).arm(1, ARM_X, .35f).arm(1, ARM_Y, 0).arm(1, ARM_Z, .75f).arm(1, ELBOW, .15f)
                .arm(1, WRIST_X, -.5f).arm(1, WRIST_Z, .3f).arm(1, CURL, .15f);
        low.arm(0, SH_FWD, .9f).arm(0, ARM_X, -1.25f).arm(0, ARM_Y, -.25f).arm(0, ARM_Z, .1f).arm(0, ELBOW, 1.1f)
                .arm(0, WRIST_X, .1f).arm(0, WRIST_Z, 0).arm(0, CURL, .95f);
        Pose drag = low.copy().add(SPINE_ROLL, .06f).add(HEAD_PITCH, -.08f).add(LIFT, .3f);
        drag.arm(1, ARM_X, .5f).arm(1, ARM_Z, .8f).arm(0, ARM_X, -1.35f).arm(0, ELBOW, .95f);
        Pose rise = base.copy().set(PLANT, 1).set(CROUCH, 3.2f).set(SPINE_PITCH, .3f).set(HEAD_PITCH, -.25f).set(NECK, .45f);
        rise.leg(1, LEG_X, -.25f).leg(1, KNEE, .3f).leg(0, LEG_X, .15f);
        rise.arm(0, ARM_X, -.7f).arm(0, ARM_Z, .2f).arm(0, ELBOW, 1.3f).arm(0, CURL, 1);
        rise.arm(1, ARM_X, -.4f).arm(1, ARM_Z, .35f).arm(1, ELBOW, .9f).arm(1, CURL, .6f);
        Pose settle = base.copy().add(CROUCH, 1f).add(SPINE_PITCH, .08f);
        Pose p = new Track(true).key(0, base).key(1.0f, drop).key(2.6f, low).key(DASH_TICKS - 3.5f, drag).key(DASH_TICKS - 1, rise)
                .key(DASH_TICKS + .5f, settle).sample(t);
        // The ground rumbling under him while he slides.
        float sliding = PantherMotion.k(t, 2, 3) * (1 - PantherMotion.k(t, DASH_TICKS - 3, DASH_TICKS - 1.5f));
        if (sliding > 0) {
            float time = c.time();
            p.add(SPINE_PITCH, .02f * Mth.sin(time * 2.3f) * sliding).add(HEAD_ROLL, .02f * Mth.sin(time * 1.9f + 1) * sliding)
                    .armAdd(1, ARM_X, .05f * Mth.sin(time * 2.7f) * sliding).add(LIFT, .15f * Mth.sin(time * 3.1f) * sliding);
        }
        return p;
    }
}
