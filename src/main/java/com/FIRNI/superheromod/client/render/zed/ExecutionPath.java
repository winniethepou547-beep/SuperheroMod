package com.FIRNI.superheromod.client.render.zed;

import com.FIRNI.superheromod.client.render.film.Film;
import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.core.cinematic.ActorPose.*;
import static com.FIRNI.superheromod.heroes.zed.ZedAction.*;

/**
 * SHADOW EXECUTION, the master timeline: where every body is, how it stands and what the camera does,
 * as pure functions of the film clock, so the camera, the bodies, the shadow, the light and the sound
 * all read from the same beats (ZedAction.ULT_*).
 *
 * Laid out round the victim: "victim frame" is x to the side (Zed's right at the start), y up, z along
 * the line from Zed to the victim, the victim's feet at 0. stage(...) turns it into the film's stage.
 *
 * The fight: the victim swings a bare-handed hook; Zed slips under it (head and shoulders first, then
 * the hips, the legs last), compresses, and explodes diagonally round their flank, cutting as he
 * passes; lands behind them, springs over the top in a twisting flip and cuts down across their
 * shoulders; lands in front, and makes one last flat-out pass beside them, past the camera, into
 * shadow. Then the shadow circles them, the ground under them turns to a pool, soldiers pull
 * themselves out of the dark one by one, eyes lighting, a held breath, every shadow collapses onto
 * them, red light bursts inside the storm, it all sinks back into the ground and leaves them lying
 * there; Zed reforms out of the last knot of shadow, a few blocks off, perfectly still.
 */
public final class ExecutionPath {
    // ------------------------------------------------------------------ small tools
    static float clamp(float x) { return x < 0 ? 0 : x > 1 ? 1 : x; }
    static float ease(float x) { x = clamp(x); return x * x * (3 - 2 * x); }
    static float easeOut(float x) { x = clamp(x); return 1 - (1 - x) * (1 - x) * (1 - x); }
    static float easeIn(float x) { x = clamp(x); return x * x * x; }
    /** Slow start, fast through the middle, a slight overshoot, settling. */
    static float backInOut(float x) {
        x = clamp(x);
        float c = 1.2f * 1.525f;
        return x < .5f ? (float) (Math.pow(2 * x, 2) * ((c + 1) * 2 * x - c)) / 2
                : (float) (Math.pow(2 * x - 2, 2) * ((c + 1) * (x * 2 - 2) + c) + 2) / 2;
    }
    static float lerp(float a, float b, float k) { return a + (b - a) * k; }
    static float window(float t, float in0, float in1, float out0, float out1) { return Math.min(ease((t - in0) / (in1 - in0)), 1 - ease((t - out0) / (out1 - out0))); }
    static float hash(float n) { double v = Math.sin(n * 12.9898) * 43758.5453; return (float) (v - Math.floor(v)); }
    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }

    /** Points over time, passed through smoothly (Catmull-Rom between keys). */
    static final class Track3 {
        private final List<Float> times = new ArrayList<>();
        private final List<Vec3> points = new ArrayList<>();
        Track3 k(float t, Vec3 p) { times.add(t); points.add(p); return this; }
        Track3 k(float t, double x, double y, double z) { return k(t, v(x, y, z)); }
        Vec3 at(float t) {
            int n = times.size();
            if (t <= times.get(0)) return points.get(0);
            if (t >= times.get(n - 1)) return points.get(n - 1);
            int i = 0;
            while (i < n - 2 && t >= times.get(i + 1)) i++;
            float u = (t - times.get(i)) / Math.max(.001f, times.get(i + 1) - times.get(i));
            Vec3 p0 = points.get(Math.max(0, i - 1)), p1 = points.get(i), p2 = points.get(i + 1), p3 = points.get(Math.min(n - 1, i + 2));
            float u2 = u * u, u3 = u2 * u;
            return p1.scale(2).add(p2.subtract(p0).scale(u)).add(p0.scale(2).subtract(p1.scale(5)).add(p2.scale(4)).subtract(p3).scale(u2))
                    .add(p1.scale(3).subtract(p0).subtract(p2.scale(3)).add(p3).scale(u3)).scale(.5);
        }
    }
    /** A number over time, passed through smoothly. */
    static final class Track1 {
        private final Track3 inner = new Track3();
        Track1 k(float t, double value) { inner.k(t, value, 0, 0); return this; }
        float at(float t) { return (float) inner.at(t).x; }
    }
    /** Zed's key poses; the head leads, the shoulders and arms follow, the hips and legs come last. */
    static final class PoseTrack {
        private final List<Float> times = new ArrayList<>();
        private final List<ZedMotion.Pose> poses = new ArrayList<>();
        PoseTrack k(float t, ZedMotion.Pose p) { times.add(t); poses.add(p); return this; }
        private ZedMotion.Pose plain(float t) {
            int n = times.size();
            if (t <= times.get(0)) return poses.get(0).copy();
            if (t >= times.get(n - 1)) return poses.get(n - 1).copy();
            int i = 0;
            while (i < n - 2 && t >= times.get(i + 1)) i++;
            ZedMotion.Pose p = poses.get(i).copy();
            p.toward(poses.get(i + 1), ease((t - times.get(i)) / Math.max(.001f, times.get(i + 1) - times.get(i))));
            return p;
        }
        ZedMotion.Pose at(float t) {
            ZedMotion.Pose head = plain(t), torso = plain(t - .8f), arms = plain(t - .6f), legs = plain(t - 1.6f);
            ZedMotion.Pose o = torso;
            o.headPitch = head.headPitch; o.headYaw = head.headYaw;
            o.rArmX = arms.rArmX; o.rArmY = arms.rArmY; o.rArmZ = arms.rArmZ; o.rElbow = arms.rElbow;
            o.lArmX = arms.lArmX; o.lArmY = arms.lArmY; o.lArmZ = arms.lArmZ; o.lElbow = arms.lElbow;
            o.crouch = legs.crouch; o.bodyPitch = legs.bodyPitch;
            o.rLegX = legs.rLegX; o.rLegZ = legs.rLegZ; o.rKnee = legs.rKnee; o.lLegX = legs.lLegX; o.lLegZ = legs.lLegZ; o.lKnee = legs.lKnee;
            return o;
        }
    }
    private static ZedMotion.Pose pose(float crouch, float bodyPitch, float torsoYaw, float torsoPitch, float torsoRoll, float headPitch) {
        return ZedMotion.of(crouch, bodyPitch, torsoYaw, torsoPitch, torsoRoll, headPitch);
    }

    // ------------------------------------------------------------------ the layout
    /** The victim's feet on the stage; Zed's starting place (victim frame). */
    public final Vec3 victim, start;
    /** He starts further off than the duel spacing: a shadow step brings him in. */
    public final boolean step;
    public final Vec3 reveal = v(ULT_REVEAL_X, 0, ULT_REVEAL_Z);

    private final Track3 zedPath = new Track3();
    private final Track1 zedYaw = new Track1(), zedPitch = new Track1(), zedRoll = new Track1();
    private final PoseTrack zedPoses = new PoseTrack();
    private final Track3 victimPath = new Track3();
    private final Track1 victimYaw = new Track1();
    private final FilmCast.Track victimPoses;

    public ExecutionPath(double d, double dy) {
        victim = v(0, dy, d);
        start = v(0, -dy, -d);
        Vec3 duel = v(0, 0, -ULT_SPACING);
        step = start.distanceTo(duel) > .3;

        // ---------------- Zed's path through the fight
        zedPath.k(0, start);
        if (step) zedPath.k(2, start).k(8, duel);
        zedPath.k(10, duel).k(ULT_DODGE, duel)
                .k(22, .25, 0, -2.9).k(ULT_LOW, .55, 0, -2.7).k(ULT_BURST, .72, 0, -2.45)
                .k(31, 1.25, .55, -1.15).k(ULT_CUT1, 1.1, .95, -.2).k(36, 1.2, .75, 1.0)
                .k(ULT_LAND1, 1.4, 0, 2.0).k(42, 1.65, 0, 2.45).k(ULT_LEAP2, 1.55, 0, 2.35)
                .k(47, .9, 1.6, 1.3).k(ULT_CUT2, .05, 2.35, .25).k(53, -.95, 1.5, -.95)
                .k(ULT_LAND2, -1.7, 0, -1.8).k(58, -1.95, 0, -2.05).k(ULT_DASH3, -1.8, 0, -1.9)
                .k(62, -1.2, .1, -1.0).k(ULT_CUT3, -.62, .2, 0).k(66, -.7, .12, 1.5)
                .k(69, -.9, 0, 3.4).k(72, -1.0, 0, 4.4).k(78, -1.1, .05, 4.9).k(ULT_GONE, -1.2, .2, 5.2).k(ULT_EYES_OUT, -1.3, .45, 5.4);
        // Which way he faces (degrees from the line; + is toward x), how his whole body tips in the air.
        zedYaw.k(0, 0).k(ULT_DODGE, 0).k(22, -10).k(ULT_LOW, -35).k(ULT_BURST, -25).k(31, 18).k(ULT_CUT1, 5).k(36, -40)
                .k(ULT_LAND1, -145).k(ULT_LEAP2, -146).k(47, -160).k(ULT_CUT2, -185).k(53, -240).k(ULT_LAND2, -290).k(ULT_DASH3, -317)
                .k(62, -325).k(ULT_CUT3, -335).k(66, -350).k(69, -360).k(72, -350).k(78, -300).k(ULT_GONE, -205).k(ULT_EYES_OUT, -195);
        zedPitch.k(0, 0).k(ULT_DODGE, 0).k(ULT_LOW, .15).k(ULT_BURST, .2).k(31, .55).k(ULT_CUT1, .7).k(36, .9).k(ULT_LAND1, .2).k(42, .1)
                .k(ULT_LEAP2, .35).k(46, .9).k(48, 2.0).k(ULT_CUT2, 3.3).k(52, 4.6).k(54, 5.7).k(ULT_LAND2, 6.43).k(58, 6.33)
                .k(ULT_DASH3, 6.53).k(62, 7.15).k(ULT_CUT3, 7.2).k(66, 7.0).k(69, 6.6).k(72, 6.3).k(ULT_GONE, 6.28);
        zedRoll.k(0, 0).k(ULT_DODGE, 0).k(22, .15).k(ULT_LOW, .25).k(ULT_BURST, .2).k(31, -.3).k(ULT_CUT1, -.45).k(36, -.6)
                .k(ULT_LAND1, -.1).k(42, 0).k(ULT_LEAP2, 0).k(ULT_CUT2, .2).k(ULT_LAND2, 0).k(ULT_DASH3, 0).k(62, .25)
                .k(ULT_CUT3, .4).k(66, .3).k(69, .1).k(72, 0);

        // ---------------- Zed's body: compression, direction change, explosive extension
        ZedMotion.Pose ready = pose(1.4f, .05f, .15f, .14f, 0, -.05f).right(-.55f, .2f, .25f, 1f).left(-.35f, -.1f, -.3f, .8f).legs(.3f, .06f, .35f, -.35f, -.06f, .3f);
        ZedMotion.Pose escape = pose(2.2f, .05f, -.4f, .3f, .3f, -.25f).right(-.3f, .3f, .5f, 1.1f).left(-.7f, -.2f, -.4f, .9f).legs(.2f, .08f, .6f, -.35f, -.1f, .5f);
        ZedMotion.Pose compressed = pose(6.5f, .2f, -.85f, .6f, .35f, -.35f).right(.45f, .2f, .55f, 1.25f).left(-1.05f, -.2f, -.6f, .5f).legs(-.85f, .1f, 1.95f, .55f, -.3f, 1.25f);
        ZedMotion.Pose loaded = pose(5.6f, .25f, -1.05f, .75f, .2f, -.4f).right(.75f, .55f, .45f, 1.4f).left(-1.25f, -.3f, -.5f, .35f).legs(-.75f, .1f, 1.75f, .45f, -.25f, 1.1f);
        ZedMotion.Pose extend = pose(-.4f, .1f, .35f, .2f, -.2f, .2f).right(-1.6f, -.4f, .35f, .5f).left(.85f, 0, -.5f, .3f).legs(.9f, .1f, .35f, -.65f, -.1f, 1f);
        ZedMotion.Pose cut1 = pose(.5f, 0, .75f, .1f, -.3f, .15f).right(-1.45f, -1.25f, .2f, .1f).left(.6f, .2f, -.9f, .4f).legs(-.6f, .15f, 1.6f, -.9f, -.15f, 1.8f);
        ZedMotion.Pose follow1 = pose(.8f, 0, 1.05f, .25f, -.2f, .1f).right(-1.1f, -1.65f, -.25f, .2f).left(-.4f, .2f, -1.2f, .3f).legs(-.7f, .2f, 1.7f, -1f, -.2f, 1.9f);
        ZedMotion.Pose land = pose(6, .15f, .3f, .6f, 0, -.2f).right(-.3f, 0, 1.2f, .3f).left(-.3f, 0, -1.2f, .3f).legs(-.4f, .4f, 1.8f, .2f, -.4f, 1.6f);
        ZedMotion.Pose load2 = pose(6.6f, .2f, 0, .85f, 0, -.45f).right(.9f, 0, .5f, .6f).left(.9f, 0, -.5f, .6f).legs(-.6f, .2f, 2f, -.5f, -.2f, 2f);
        ZedMotion.Pose tuck = pose(1, 0, -.2f, .3f, 0, .2f).right(-2.6f, -.2f, .3f, .4f).left(-.5f, 0, -.6f, .8f).legs(-1.4f, .1f, 2.2f, -1.3f, -.1f, 2.3f);
        ZedMotion.Pose cut2 = pose(.8f, 0, .2f, .6f, 0, .3f).right(-.55f, -.3f, .25f, .15f).left(.4f, 0, -.8f, .4f).legs(-1.2f, .1f, 2f, -1f, -.1f, 1.9f);
        ZedMotion.Pose open = pose(.5f, 0, 0, .2f, 0, 0).right(-.6f, 0, 1f, .3f).left(-.6f, 0, -1f, .3f).legs(-.5f, .2f, 1f, -.3f, -.2f, .9f);
        ZedMotion.Pose land2 = pose(6.2f, .15f, -.2f, .6f, 0, -.2f).right(-.2f, 0, 1.25f, .3f).left(-.5f, 0, -1.1f, .4f).legs(-.5f, .4f, 1.9f, .3f, -.4f, 1.5f);
        ZedMotion.Pose load3 = pose(5, .3f, -.5f, .9f, 0, -.5f).right(.9f, .3f, .3f, .5f).left(-1.3f, -.2f, -.3f, .3f).legs(.7f, .1f, 1.3f, -1f, -.1f, 1.6f);
        ZedMotion.Pose dash3 = pose(2, .35f, -.3f, .5f, 0, -.3f).right(-1f, .2f, .9f, .2f).left(1f, 0, -.4f, .2f).legs(1.1f, .1f, .4f, -1.1f, -.1f, .9f);
        ZedMotion.Pose cut3 = pose(2, .3f, .75f, .4f, 0, -.2f).right(-1.5f, -.95f, .6f, .1f).left(1.1f, 0, -.5f, .2f).legs(1f, .1f, .5f, -1f, -.1f, 1f);
        ZedMotion.Pose follow3 = pose(3.2f, .2f, 1.25f, .5f, .4f, -.1f).right(.6f, -.3f, .95f, .2f).left(-.4f, .3f, -.9f, .4f).legs(.7f, .2f, 1.4f, -1.2f, -.2f, .4f);
        ZedMotion.Pose still = pose(1.4f, 0, .2f, .1f, 0, 0).right(-.2f, 0, .5f, .3f).left(-.2f, 0, -.5f, .3f).legs(.15f, .1f, .3f, -.2f, -.1f, .3f);
        zedPoses.k(0, ready).k(ULT_DODGE, ready).k(22, escape).k(ULT_LOW, compressed).k(ULT_BURST, loaded).k(31, extend).k(ULT_CUT1, cut1).k(36, follow1)
                .k(ULT_LAND1, land).k(42, land).k(ULT_LEAP2, load2).k(47, tuck).k(ULT_CUT2, cut2).k(53, open).k(ULT_LAND2, land2).k(58, land2)
                .k(ULT_DASH3, load3).k(62, dash3).k(ULT_CUT3, cut3).k(69, follow3).k(76, still);

        // ---------------- the victim: a guard, a hook, three cuts, the dark, the fall
        victimPath.k(0, 0, 0, 0).k(ULT_CUT1, 0, 0, 0).k(38, -.25, 0, .05).k(ULT_CUT2, -.25, 0, .1).k(ULT_CUT3, -.25, 0, .15)
                .k(70, .15, 0, .4).k(96, .1, 0, .35).k(ULT_ATTACK + 4, .1, 0, .35).k(226, .1, .12, .5);
        victimYaw.k(0, 0).k(ULT_CUT1, 0).k(37, 18).k(44, 10).k(ULT_CUT2, 0).k(ULT_CUT3, 0).k(69, -40).k(80, -30).k(96, -12)
                .k(ULT_ATTACK, -12).k(226, -15);
        ActorPose guard = of().j(CHEST, 8, 0, 0).j(HEAD, 2, 0, 0).j(RIGHT_UPPER_ARM, -58, 0, 14).j(RIGHT_LOWER_ARM, -88, 0, 0)
                .j(LEFT_UPPER_ARM, -62, 0, -14).j(LEFT_LOWER_ARM, -90, 0, 0).j(RIGHT_UPPER_LEG, -12, 0, 5).j(RIGHT_LOWER_LEG, 14, 0, 0)
                .j(LEFT_UPPER_LEG, 14, 0, -5).j(LEFT_LOWER_LEG, 8, 0, 0).crouch(.08f);
        // The hook: the right shoulder loads back, then the whole body turns through it.
        ActorPose windup = guard.copy().j(CHEST, 4, 32, 0).j(HEAD, 0, 10, 0).j(RIGHT_UPPER_ARM, -62, 12, 52).j(RIGHT_LOWER_ARM, -95, 0, 0)
                .j(RIGHT_UPPER_LEG, -20, 0, 6).j(LEFT_UPPER_LEG, 18, 0, -5).crouch(.12f);
        ActorPose swing = guard.copy().j(CHEST, 12, -46, 0).j(HEAD, 4, -15, 0).j(RIGHT_UPPER_ARM, -92, -48, 18).j(RIGHT_LOWER_ARM, -38, 0, 0)
                .j(LEFT_UPPER_ARM, -40, 0, -25).j(LEFT_LOWER_ARM, -80, 0, 0).crouch(.12f);
        ActorPose overreach = swing.copy().j(CHEST, 18, -60, -6).j(HEAD, 2, -10, 0).j(RIGHT_UPPER_ARM, -84, -62, 10).j(RIGHT_LOWER_ARM, -15, 0, 0)
                .j(LEFT_UPPER_LEG, -18, 0, -5).j(LEFT_LOWER_LEG, 26, 0, 0).crouch(.15f);
        // Cut across the left flank: the shoulders give first, the head after, the hips last.
        ActorPose hit1 = of().j(CHEST, -6, 22, -24).j(HEAD, -10, 30, -26).j(HIPS, 0, -8, 10)
                .j(RIGHT_UPPER_ARM, -40, 20, 30).j(RIGHT_LOWER_ARM, -30, 0, 0).j(LEFT_UPPER_ARM, -20, 0, -65).j(LEFT_LOWER_ARM, -25, 0, 0)
                .j(RIGHT_UPPER_LEG, -6, 0, 10).j(RIGHT_LOWER_LEG, 15, 0, 0).j(LEFT_UPPER_LEG, 6, 0, -4).j(LEFT_LOWER_LEG, 10, 0, 0).crouch(.15f);
        // Looking over the shoulder for him, the guard half back up.
        ActorPose search = guard.copy().j(CHEST, 6, -22, 0).j(HEAD, 0, -55, 0).j(RIGHT_UPPER_ARM, -45, -10, 25).j(RIGHT_LOWER_ARM, -70, 0, 0).crouch(.12f);
        // From above and behind: folded forward, knees giving.
        ActorPose hit2 = of().j(CHEST, 36, 0, 0).j(HEAD, 34, 0, 0).j(HIPS, -6, 0, 0).j(RIGHT_UPPER_ARM, 10, 0, 22).j(RIGHT_LOWER_ARM, -20, 0, 0)
                .j(LEFT_UPPER_ARM, 10, 0, -22).j(LEFT_LOWER_ARM, -24, 0, 0).j(RIGHT_UPPER_LEG, -22, 0, 6).j(RIGHT_LOWER_LEG, 40, 0, 0)
                .j(LEFT_UPPER_LEG, -16, 0, -6).j(LEFT_LOWER_LEG, 35, 0, 0).crouch(.32f);
        ActorPose stagger = of().j(CHEST, 18, 0, 0).j(HEAD, 6, 0, 0).j(RIGHT_UPPER_ARM, -20, 0, 18).j(RIGHT_LOWER_ARM, -40, 0, 0)
                .j(LEFT_UPPER_ARM, -24, 0, -18).j(LEFT_LOWER_ARM, -45, 0, 0).j(RIGHT_UPPER_LEG, -14, 0, 6).j(RIGHT_LOWER_LEG, 24, 0, 0)
                .j(LEFT_UPPER_LEG, 6, 0, -6).j(LEFT_LOWER_LEG, 16, 0, 0).crouch(.22f);
        ActorPose brace = stagger.copy().j(RIGHT_UPPER_ARM, -60, 0, 20).j(RIGHT_LOWER_ARM, -80, 0, 0).j(LEFT_UPPER_ARM, -64, 0, -20).j(LEFT_LOWER_ARM, -84, 0, 0);
        // The last pass: spun round, doubled up.
        ActorPose hit3 = of().j(CHEST, 26, -42, 22).j(HEAD, 10, -45, 25).j(HIPS, 0, -15, -10).j(RIGHT_UPPER_ARM, -30, 0, 70).j(RIGHT_LOWER_ARM, -20, 0, 0)
                .j(LEFT_UPPER_ARM, -60, 40, -30).j(LEFT_LOWER_ARM, -40, 0, 0).j(RIGHT_UPPER_LEG, -18, 0, 10).j(RIGHT_LOWER_LEG, 30, 0, 0)
                .j(LEFT_UPPER_LEG, 10, 0, -6).j(LEFT_LOWER_LEG, 18, 0, 0).crouch(.26f);
        ActorPose reel = of().j(CHEST, -18, 10, -8).j(HEAD, -14, 14, -12).j(RIGHT_UPPER_ARM, 8, 0, 30).j(RIGHT_LOWER_ARM, -20, 0, 0)
                .j(LEFT_UPPER_ARM, 4, 0, -26).j(LEFT_LOWER_ARM, -24, 0, 0).j(RIGHT_UPPER_LEG, -20, 0, 6).j(RIGHT_LOWER_LEG, 34, 0, 0)
                .j(LEFT_UPPER_LEG, 12, 0, -6).j(LEFT_LOWER_LEG, 10, 0, 0).crouch(.16f);
        // Alone in the dark: hunched, hands half up, looking for where it comes from.
        ActorPose dazed = of().j(CHEST, 14, 0, 0).j(HEAD, 4, 0, 0).j(RIGHT_UPPER_ARM, -42, 0, 18).j(RIGHT_LOWER_ARM, -62, 0, 0)
                .j(LEFT_UPPER_ARM, -46, 0, -18).j(LEFT_LOWER_ARM, -66, 0, 0).j(RIGHT_UPPER_LEG, -16, 0, 7).j(RIGHT_LOWER_LEG, 22, 0, 0)
                .j(LEFT_UPPER_LEG, 12, 0, -7).j(LEFT_LOWER_LEG, 14, 0, 0).crouch(.16f);
        ActorPose fear = of().j(CHEST, 20, 0, 0).j(HEAD, 14, 0, 0).j(RIGHT_UPPER_ARM, -82, -60, 0).j(RIGHT_LOWER_ARM, -72, 0, 0)
                .j(LEFT_UPPER_ARM, -88, 60, 0).j(LEFT_LOWER_ARM, -70, 0, 0).j(LEFT_UPPER_LEG, -30, 0, -6).j(LEFT_LOWER_LEG, 40, 0, 0)
                .j(RIGHT_UPPER_LEG, 20, 0, 6).j(RIGHT_LOWER_LEG, 24, 0, 0).crouch(.24f);
        ActorPose struck = of().j(CHEST, -40, 0, 10).j(HEAD, -32, 0, 8).j(HIPS, 12, 0, 0).j(RIGHT_UPPER_ARM, 40, 0, 50).j(RIGHT_LOWER_ARM, -10, 0, 0)
                .j(LEFT_UPPER_ARM, 44, 0, -54).j(LEFT_LOWER_ARM, -10, 0, 0).j(RIGHT_UPPER_LEG, -10, 0, 8).j(RIGHT_LOWER_LEG, 26, 0, 0)
                .j(LEFT_UPPER_LEG, -6, 0, -8).j(LEFT_LOWER_LEG, 22, 0, 0).crouch(.12f);
        ActorPose falling = of().j(CHEST, -20, 0, 0).j(HEAD, 10, 0, 0).j(RIGHT_UPPER_ARM, -120, 0, 30).j(RIGHT_LOWER_ARM, -20, 0, 0)
                .j(LEFT_UPPER_ARM, -110, 0, -35).j(LEFT_LOWER_ARM, -25, 0, 0).j(RIGHT_UPPER_LEG, -30, 0, 8).j(RIGHT_LOWER_LEG, 30, 0, 0)
                .j(LEFT_UPPER_LEG, -24, 0, -8).j(LEFT_LOWER_LEG, 40, 0, 0).body(0, 50);
        // On their back: one arm thrown out, the other across the chest, a knee up, the head turned.
        ActorPose down = of().j(HEAD, 6, 28, 0).j(RIGHT_UPPER_ARM, -12, 0, 70).j(RIGHT_LOWER_ARM, -25, 0, 0).j(LEFT_UPPER_ARM, -40, 30, -20)
                .j(LEFT_LOWER_ARM, -70, 0, 0).j(RIGHT_UPPER_LEG, -8, 0, 10).j(LEFT_UPPER_LEG, -38, 0, -6).j(LEFT_LOWER_LEG, 62, 0, 0).body(0, 88);
        victimPoses = new FilmCast.Track().key(0, 0, guard).key(ULT_WINDUP, 4, guard).key(20, 7, windup, noticeChain())
                .key(ULT_SWING, 3, swing, launchChain()).key(28, 4, overreach)
                .key(ULT_CUT1 + 2, 2.5f, hit1, impactChain()).key(44, 8, search, noticeChain())
                .key(ULT_CUT2 + 2, 2.5f, hit2, impactChain()).key(58, 6, stagger).key(63, 3, brace)
                .key(ULT_CUT3 + 2, 2.5f, hit3, impactChain()).key(76, 9, reel).key(96, 18, dazed)
                .key(165, 25, fear, noticeChain()).key(ULT_ATTACK + 2, 4, fear)
                .key(ULT_ATTACK + 4, 2, struck, impactChain()).key(222, 6, falling, launchChain()).key(228, 5, down, impactChain());
    }

    // ------------------------------------------------------------------ Zed
    /** Victim frame to stage. */
    public Vec3 stage(Vec3 local) { return victim.add(local); }
    /** Zed's feet (victim frame). */
    public Vec3 zed(float t) {
        if (t >= ULT_EYES_OUT && t < 264) return zedPath.at(ULT_EYES_OUT);
        if (t >= 264) return reveal;
        return zedPath.at(t);
    }
    public Vec3 zedVelocity(float t) { return zed(t + .5f).subtract(zed(t - .5f)); }
    /** Facing, degrees from the line (0 = toward the victim at the start). */
    public float zedYaw(float t) {
        if (t >= 264) return (float) Math.toDegrees(Math.atan2(-reveal.x, -reveal.z));
        return zedYaw.at(t);
    }
    /** His whole body tipping forward (radians, about his middle) and leaning sideways. */
    public float zedPitch(float t) { return t >= 264 ? 0 : zedPitch.at(t); }
    public float zedRoll(float t) { return t >= 264 ? 0 : zedRoll.at(t); }
    public ZedMotion.Pose zedPose(float t, float time) {
        if (t >= 264) {
            ZedMotion.Pose p = ZedMotion.idle(time);
            p.crouch = .9f; p.rArmX = -.1f; p.lArmX = -.12f; p.torsoYaw = 0;
            return p;
        }
        return zedPoses.at(t);
    }
    /** How he is drawn: 1 his solid self; between 0 and 1 a see-through shadow of that density; -1 not at all. */
    public float zedSolid(float t) {
        if (step && t < 10) {
            if (t < 3) return .85f * (1 - t / 3f);
            if (t < 7) return 0;
            return t < 9.5f ? .85f * (t - 7) / 2.5f : 1;
        }
        if (t < ULT_FADE) return 1;
        if (t < ULT_GONE) return .85f * (1 - ease((t - ULT_FADE) / (ULT_GONE - ULT_FADE)));
        if (t < ULT_EYES_OUT) return 0;
        if (t < ULT_REFORM) return -1;
        if (t < ULT_SOLID) return .9f * ease((t - ULT_REFORM - 6) / (ULT_SOLID - ULT_REFORM - 6));
        return 1;
    }
    /** How lit his eyes are. */
    public float zedEyes(float t) {
        if (t < ULT_GONE) return t >= ULT_FADE ? 1.25f : 1;
        if (t < ULT_EYES_OUT) return 1.25f * (1 - ease((t - ULT_GONE) / (ULT_EYES_OUT - ULT_GONE)));
        if (t < ULT_REFORM) return 0;
        return 1.35f * ease((t - ULT_REFORM) / 4) - .25f * ease((t - ULT_SOLID) / 12);
    }
    /** Once solid again, how far the light has come back to his armour (0 dark .. 1 as the world lights him). */
    public float zedLight(float t) { return t < ULT_SOLID ? 1 : ease((t - ULT_SOLID) / 12); }
    /** Stretched along his motion as he loses solidity. */
    public float zedStretch(float t) { return 1 + .45f * window(t, ULT_FADE, ULT_FADE + 8, ULT_GONE, ULT_EYES_OUT); }

    // ------------------------------------------------------------------ the victim
    public Vec3 victimAt(float t) { return victimPath.at(t); }
    /** Their facing, degrees added to "facing Zed's start". */
    public float victimYaw(float t) {
        float yaw = victimYaw.at(t);
        // In the dark they turn to every sound.
        if (t > 96 && t < ULT_ATTACK) yaw += (float) (Math.sin(t * .045) * 26 + Math.sin(t * .11 + 1) * 9) * window(t, 96, 110, 196, 206);
        return yaw;
    }
    public ActorPose victimPose(float t) {
        ActorPose p = victimPoses.sample(t).copy();
        if (t > 96 && t < ULT_ATTACK) {
            float w = window(t, 96, 110, 196, 206);
            p.rot[HEAD][1] += (float) Math.toRadians(Math.sin(t * .07 + .5) * 38 * w);
            p.rot[HEAD][0] += (float) Math.toRadians(Math.sin(t * .05) * 8 * w);
            p.rot[CHEST][1] += (float) Math.toRadians(Math.sin(t * .05 + 1.2) * 10 * w);
            // Trembling as the soldiers rise.
            float shiver = window(t, 150, 170, 200, 210);
            p.rot[RIGHT_LOWER_ARM][0] += (float) Math.sin(t * 2.3) * .05f * shiver;
            p.rot[LEFT_LOWER_ARM][0] += (float) Math.sin(t * 2.7 + 1) * .05f * shiver;
        }
        return p;
    }
    /** The victim's chest (victim frame), for the camera and the flash. */
    public Vec3 victimChest(float t) {
        Vec3 at = victimAt(t);
        return t < 222 ? at.add(0, 1.15, 0) : at.add(0, .35, .9);
    }

    // ------------------------------------------------------------------ the shadow soldiers (the last one is Zed himself)
    public static final int SOLDIERS = 7, REAL = 6;
    private static final int RISE = 0, FORM = 1, PARTIAL = 2, HIM = 3;
    private static final Vec3[] HOME = {v(1.9, 0, -1.35), v(-1.6, 0, 1.75), v(.45, 1.3, 2.35), v(-2.25, 0, -.85), v(1.55, 0, 1.95), v(-.45, 0, -2.35), v(2.35, 0, .55)};
    private static final int[] KIND = {RISE, FORM, FORM, RISE, RISE, PARTIAL, HIM};
    private static final float[] FORMS = {124, 132, 140, 148, 156, 164, 172};
    private static final float[] EYES = {137, 146, 152, 159, 166, 162, 176};
    private static final float[] ATTACK = {ULT_ATTACK, ULT_ATTACK + 2, ULT_ATTACK + 1, ULT_ATTACK + 1, ULT_ATTACK + 2, ULT_ATTACK + 3, ULT_ATTACK + 1};

    public float soldierFormStart(int i) { return FORMS[i]; }
    /** Where soldier i stands (victim frame): home, a slow approach, the lunge in, the storm round them, then still. */
    public Vec3 soldier(int i, float t) {
        Vec3 home = HOME[i];
        Vec3 in = v(-home.x, 0, -home.z).normalize();
        // Those mid-stride creep closer while the others form.
        double creep = (i == 3 || i == 4) ? .45 * ease((t - 170) / 38) : .12 * ease((t - 180) / 30);
        Vec3 at = home.add(in.scale(creep));
        float a = ATTACK[i];
        if (t < a) return at;
        Vec3 close = in.scale(-.55).add(0, home.y > .5 ? .4 : 0, 0);
        if (t < a + 4) return at.lerp(close, easeIn((t - a) / 4));
        // Whirling round them inside the storm, cutting.
        float spin = Math.min(t, ULT_FLASH) - a - 4;
        double angle = Math.atan2(close.z, close.x) + spin * .32 * (i % 2 == 0 ? 1 : -1);
        double radius = .62 + .28 * Math.sin(spin * .4 + i);
        Vec3 whirl = v(Math.cos(angle) * radius, close.y + .12 * Math.sin(spin * .5 + i), Math.sin(angle) * radius);
        return close.lerp(whirl, ease(spin / 3));
    }
    public float soldierYaw(int i, float t) {
        Vec3 at = soldier(i, t);
        // Facing in at the victim, always.
        return (float) Math.toDegrees(Math.atan2(-at.x, -at.z));
    }
    /** Under the ground while rising out of it, sinking back into it at the end (blocks). */
    public float soldierSink(int i, float t) {
        float s = 0;
        if (KIND[i] == RISE) s = -1.9f * (1 - easeOut((t - FORMS[i]) / 18));
        if (t > ULT_COLLAPSE) s -= 1.9f * easeIn((t - ULT_COLLAPSE - i * .8f) / 22);
        return s;
    }
    /** Is it there at all. */
    public boolean soldierShown(int i, float t) { return t >= FORMS[i] - 2 && t < ULT_COLLAPSE + 26 && (i != REAL || t < ULT_COLLAPSE + 24); }
    /** Opacity of its torso, head, arms and legs: formed torso first and legs last; dissolving head first. */
    public void soldierParts(int i, float t, float[] out) {
        float f = t - FORMS[i];
        switch (KIND[i]) {
            case RISE, HIM -> { float a = ease(f / 6); out[0] = out[1] = out[2] = out[3] = a; }
            case PARTIAL -> { out[0] = .5f * ease(f / 10); out[1] = .55f * ease((f - 6) / 8); out[2] = .4f * ease((f - 4) / 10); out[3] = .12f * ease((f - 10) / 10); }
            default -> { out[0] = ease(f / 8); out[2] = ease((f - 5) / 8); out[1] = ease((f - 8) / 7); out[3] = ease((f - 12) / 10); }
        }
        if (t > ULT_COLLAPSE) {
            float d = t - ULT_COLLAPSE - i * .8f;
            out[1] *= 1 - ease(d / 8);
            out[2] *= 1 - ease((d - 2) / 10);
            out[0] *= 1 - ease((d - 4) / 12);
            out[3] *= 1 - ease((d - 8) / 12);
        }
    }
    /** Its eyes: dark, then lit with a flare, out again one after another when the energy is spent. */
    public float soldierEyes(int i, float t) {
        float e = t < EYES[i] ? 0 : 1 + .9f * (1 - ease((t - EYES[i]) / 5));
        if (i == REAL) e *= 1.25f;
        // Hidden for a moment by the smoke passing across them, here and there.
        if (t > EYES[i] + 8 && t < ULT_PAUSE && hash((float) Math.floor(t / 7) + i * 13) < .18f) e *= .15f;
        if (t > ULT_FLASH + 6) e *= 1 - ease((t - ULT_FLASH - 6 - i * 1.3f) / 4);
        return e;
    }
    /** Zed himself among them: solid (if barely lit) once formed, a shadow while forming and dissolving. */
    public boolean soldierSolid(int i, float t) { return i == REAL && t >= 186 && t < ULT_COLLAPSE; }
    public ZedMotion.Pose soldierPose(int i, float t, float time) {
        ZedMotion.Pose p = ZedMotion.idle(time + i * 11);
        // Its stance.
        switch (i) {
            case 0 -> { p.crouch = 6; p.torsoPitch = .55f; p.right(-.6f, .2f, .3f, 1f).left(-.5f, -.2f, -.3f, 1f).legs(-.7f, .15f, 1.8f, .4f, -.2f, 1.3f); }
            case 1 -> { p.torsoPitch = .55f; p.headPitch = -.25f; p.right(-1.2f, .2f, .2f, .5f).left(-1.1f, -.2f, -.2f, .5f); }
            case 2 -> { p.right(-.3f, 0, 1.1f, .2f).left(-.3f, 0, -1.1f, .2f).legs(.5f, .2f, .6f, .3f, -.2f, .9f); }
            case 3, 4 -> { p.crouch = 1.5f; p.bodyPitch = .2f; p.right(-.8f, .2f, .4f, .8f).left(.4f, 0, -.3f, .4f).legs(.6f, .05f, .4f, -.5f, -.05f, .5f); }
            case REAL -> { p.crouch = .9f; p.right(-.15f, 0, .3f, .3f).left(-.15f, 0, -.3f, .3f); }
            default -> p.right(-.4f, .2f, .3f, .9f).left(-.4f, -.2f, -.3f, .9f);
        }
        // Forming: arms hang limp until they are there.
        float[] parts = new float[4];
        soldierParts(i, Math.min(t, ULT_COLLAPSE), parts);
        if (KIND[i] != RISE && KIND[i] != HIM && parts[2] < 1) {
            float limp = 1 - parts[2];
            p.rArmX = lerp(p.rArmX, .1f, limp); p.lArmX = lerp(p.lArmX, .1f, limp); p.rArmZ = lerp(p.rArmZ, .1f, limp); p.lArmZ = lerp(p.lArmZ, -.1f, limp);
        }
        // Rising out of the ground: pulling itself up, hunched.
        if (KIND[i] == RISE) { float up = 1 - easeOut((t - FORMS[i]) / 18); p.torsoPitch += .6f * up; p.headPitch += .4f * up; }
        float a = ATTACK[i];
        if (t >= a - 2 && t < ULT_FLASH + 4) {
            // The lunge: thrown forward, the blade thrust in.
            float k = ease((t - a + 2) / 4);
            ZedMotion.Pose lunge = p.copy();
            lunge.bodyPitch = .5f; lunge.crouch = 2.5f; lunge.right(-1.5f, -.2f, .2f, .1f).left(.8f, 0, -.4f, .3f).legs(1f, .1f, .4f, -1f, -.1f, 1f);
            if (t > a + 4) {
                // Then cutting, arm after arm, as they whirl.
                float s = (t - a) * 1.3f + i;
                lunge.rArmY = (float) Math.sin(s) * 1.2f; lunge.lArmY = (float) Math.sin(s + 2) * 1.2f;
                lunge.lArmX = -1.2f; lunge.torsoYaw = (float) Math.sin(s * .5f) * .8f;
            }
            p.toward(lunge, k);
        }
        if (t >= ULT_FLASH + 4) {
            // The energy spent: arms fall, heads drop.
            ZedMotion.Pose spent = p.copy();
            spent.right(.05f, 0, .15f, .2f).left(.05f, 0, -.15f, .2f); spent.headPitch = .5f; spent.torsoPitch = .4f; spent.bodyPitch = 0; spent.crouch = 2;
            p.toward(spent, ease((t - ULT_FLASH - 4) / 6));
        }
        return p;
    }

    // ------------------------------------------------------------------ the shadow streams circling the victim
    public static final int STREAMS = 7;
    /** Stream i's head (victim frame): an uneven, predatory orbit, some low along the ground, some high, tightening. */
    public Vec3 stream(int i, float t) {
        float h = hash(i * 3.1f + .7f);
        double dir = i % 3 == 2 ? -1 : 1;
        double angle = i * Math.PI * 2 / STREAMS + h * 2 + (t - ULT_CIRCLE) * (.07 + .035 * h) * dir + .3 * Math.sin(t * .03 + i);
        double tighten = 1 - .35 * ease((t - 120) / 80f) - .55 * ease((t - ULT_ATTACK) / 8f);
        double radius = (1.7 + 2.1 * hash(i * 5.3f)) * tighten + .5 * Math.sin(t * .05 + i * 1.7);
        double height = .25 + 2.6 * hash(i * 7.9f) + .9 * Math.sin(t * .07 + i * 2.1);
        // Some dive to the ground and pull up again.
        if (i % 3 == 0) height = Math.max(.1, height - 1.6 * Math.max(0, Math.sin(t * .06 + i)));
        height = Math.max(.1, lerp((float) height, 1.0f, ease((t - ULT_ATTACK) / 8f)));
        return v(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
    }

    // ------------------------------------------------------------------ the contacts
    public static final float[] CUTS = {ULT_CUT1, ULT_CUT2, ULT_CUT3};
    private static final Vec3[] CUT_AT = {v(.32, 1.15, -.05), v(0, 1.62, .12), v(-.3, 1.0, 0)};
    public Vec3 cutPoint(int i) { return CUT_AT[i].add(victimAt(CUTS[i])); }
    public Vec3 cutDirection(int i) { return zedVelocity(CUTS[i]).normalize(); }

    // ------------------------------------------------------------------ the camera
    private Vec3 zedChest(float t) { return zed(t).add(0, 1.05, 0); }
    private static Vec3 orbit(double degrees, double radius, double height) {
        double a = Math.toRadians(degrees);
        return v(Math.cos(a) * radius, height, Math.sin(a) * radius);
    }
    /** The camera at film time t (victim frame), with the kick it is given at that moment folded in. */
    public Film.View camera(float t) {
        Vec3 vc = v(0, 1.05, 0);
        Vec3 pos, aim;
        float fov, roll = 0;
        if (t < 19) {
            // A readable duel: both of them side on, a slow push in.
            float k = ease(t / 19);
            pos = v(4.4, 1.6, -1.2).lerp(v(3.7, 1.45, -1.5), k);
            aim = v(0, 1.15, -1.6).lerp(v(0, 1.1, -1.7), k);
            fov = lerp(52, 50, k);
        } else if (t < 29) {
            // Close and a little low on the slip: his head goes first and the fist passes over it.
            float k = ease((t - 19) / 10);
            pos = v(2.3, .85, -1.85).add(-.1 * k, -.05 * k, 0);
            aim = zedChest(t - 1.5f).add(0, .25, 0).scale(.7).add(v(0, 1.4, -1.6).scale(.3));
            fov = 47; roll = -2 * k;
        } else if (t < 44) {
            // Low, looking up: he passes right by the lens; the camera swings round after him, late, overshooting.
            float u = (t - 29) / 14f;
            pos = orbit(lerp(-45, 38, backInOut(u)), 3.1, lerp(.38f, .5f, u));
            aim = vc.add(0, .2, 0).lerp(zedChest(t - 2.5f), .55);
            fov = 64; roll = -4 + 7 * backInOut(u);
        } else if (t < 58) {
            // From low beside them: he flips over the top against the sky.
            float k = ease((t - 44) / 14);
            pos = v(-2.7, .5, .7).lerp(v(-2.9, .45, .2), k);
            aim = vc.add(0, .5, 0).lerp(zedChest(t - 2), .6);
            fov = 66; roll = lerp(3, -2, k);
        } else if (t < 98) {
            // On his exit line: he comes flat out past them, past the lens, and comes apart into shadow
            // in the foreground; then the camera looks back to the victim, alone.
            float back = ease((t - 76) / 22);
            pos = v(-.15, 1.0, 6.5).lerp(v(.7, 1.5, 7.4), back);
            float w = t < 76 ? .45f * ease((t - 62) / 5) : t < 86 ? lerp(.45f, .9f, ease((t - 76) / 8)) : .9f * (1 - ease((t - 86) / 12));
            Vec3 him = zedChest(Math.min(t, ULT_EYES_OUT) - 2).add(0, t > 76 ? .55 : 0, 0);
            aim = vc.lerp(him, w);
            fov = lerp(58, 54, back); roll = 2 * ease((t - 64) / 10) * (1 - back);
        } else if (t < 142) {
            // Wide, then closer, round them: the shadow circling, the ground going dark.
            float k = ease((t - 98) / 44);
            pos = orbit(lerp(100, 165, k) + 4 * Math.sin(t * .05), lerp(7, 4.6f, k), lerp(2.7f, 1.8f, k));
            aim = vc.add(.35 * Math.sin(t * .03), -.1, 0);
            fov = lerp(56, 50, k); roll = (float) Math.sin(t * .04) * .8f;
        } else if (t < 172) {
            // Close on a soldier pulling itself together in the smoke, the victim behind it; then on to another.
            float k = ease((t - 142) / 30);
            pos = v(1.9, 2.3, 4.2).lerp(v(1.6, 2.35, 3.8), k);
            Vec3 a = soldier(2, t).add(0, 1.35, 0).scale(.75).add(vc.scale(.25)), b = soldier(4, t).add(0, 1.1, 0).scale(.5).add(vc.scale(.5));
            aim = a.lerp(b, ease((t - 158) / 12));
            fov = lerp(44, 41, k);
        } else if (t < 208) {
            // Back and round: they are surrounded. The camera comes to rest for the held breath.
            float k = easeOut((t - 172) / 28);
            pos = orbit(lerp(60, -35, k), lerp(3.8f, 5.2f, k), lerp(2.0f, 2.5f, k));
            aim = vc.add(0, .15, 0);
            fov = 50;
        } else if (t < 240) {
            // Every shadow collapses in, and so does the camera; then the storm, and the flash inside it.
            float push = ease((t - 211) / 8), drift = clamp((t - 208) / 32);
            pos = orbit(lerp(-35, -52, drift), lerp(5.2f, 2.7f, push) - .2 * clamp((t - 219) / 21), lerp(2.5f, 1.4f, push));
            aim = vc.add(0, .1, 0);
            fov = 54 + 8 * window(t, 211, 214, 216, 220) - 2 * ease((t - 220) / 10);
            double kickBack = .3 * Math.exp(-Math.max(0, t - ULT_FLASH) / 4) * (t >= ULT_FLASH ? Math.sin((t - ULT_FLASH) * .6 + 1.2) : 0);
            pos = pos.add(pos.subtract(aim).normalize().scale(kickBack));
        } else if (t < 278) {
            // It sinks back into the ground; the camera rises to look down at what is left.
            float k = ease((t - 240) / 30);
            pos = v(2.6, 1.7, -2.3).lerp(v(3.1, 3.5, -2.9), k);
            aim = vc.lerp(v(.1, .25, 1.2), ease((t - 240) / 20));
            fov = lerp(50, 46, k);
        } else {
            // Low, behind the fallen victim's head: far off, a knot of shadow becomes Zed. Hold, still.
            float k = ease((t - 278) / 58);
            pos = v(.95, .75, 3.9).lerp(v(.82, .72, 3.55), k);
            aim = reveal.add(0, 1.1, 0).scale(.8).add(v(.1, .3, 1.4).scale(.2));
            fov = lerp(46, 44, k);
        }
        return new Film.View(stage(pos), stage(aim), fov, roll);
    }

    /** Event-driven kicks: yaw, pitch, roll, field of view (degrees). Only where something happens. */
    public float[] kick(float t) {
        float[] k = new float[4];
        // Small, quick ones on the cuts and landings.
        damped(k, 0, t, ULT_CUT1, .6f, 1.9f, 2.5f); damped(k, 1, t, ULT_CUT1, .4f, 2.3f, 2.5f);
        damped(k, 1, t, ULT_CUT2, .8f, 2f, 3); damped(k, 2, t, ULT_CUT2, .5f, 2.2f, 3);
        damped(k, 1, t, ULT_LAND1, .3f, 2.5f, 2); damped(k, 1, t, ULT_LAND2, .3f, 2.5f, 2);
        damped(k, 0, t, ULT_CUT3, .9f, 1.8f, 3);
        // He passes right by the lens.
        damped(k, 0, t, 67, 1.6f, 1.5f, 3); damped(k, 2, t, 67, 1.2f, 1.7f, 3);
        if (t >= 67) k[3] += 4 * (float) Math.exp(-(t - 67) / 3);
        // The first soldier coming out of the ground; the shadows lunging in.
        damped(k, 1, t, ULT_RISE, .35f, .9f, 6);
        for (int i = 0; i < 3; i++) damped(k, i % 2, t, ULT_ATTACK + i * 1.5f, .5f, 2f, 2.5f);
        // The flash: one deep, slow kick that dies away.
        damped(k, 1, t, ULT_FLASH, 3.2f, .55f, 7); damped(k, 0, t, ULT_FLASH, 1.4f, .45f, 7); damped(k, 2, t, ULT_FLASH, 2f, .5f, 7);
        if (t >= ULT_FLASH) k[3] += -6 * (float) (Math.exp(-(t - ULT_FLASH) / 2.5) * Math.cos((t - ULT_FLASH) * .5));
        return k;
    }
    private static void damped(float[] out, int axis, float t, float at, float amplitude, float frequency, float decay) {
        if (t < at) return;
        float s = t - at;
        out[axis] += amplitude * (float) (Math.exp(-s / decay) * Math.sin(s * frequency));
    }

    /** How strongly the red light bursts inside the storm (0..1). */
    public static float flash(float t) {
        if (t < ULT_FLASH - 3) return 0;
        if (t < ULT_FLASH) return ease((t - ULT_FLASH + 3) / 3);
        return (float) Math.exp(-(t - ULT_FLASH) / 3.2);
    }
    /** The pool of shadow on the ground under them (radius, blocks). */
    public static float poolRadius(float t) {
        if (t < ULT_POOL) return 0;
        if (t < ULT_COLLAPSE) return 3.4f * easeOut((t - ULT_POOL) / 50) + .6f * ease((t - 160) / 76);
        // It takes the shadow back: spreads a little, then draws in to nothing.
        return (4f + .6f * easeOut((t - ULT_COLLAPSE) / 12)) * (1 - easeIn((t - ULT_COLLAPSE - 8) / 40));
    }
}
