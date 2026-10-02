package com.FIRNI.superheromod.client.render.hulk;

import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.core.cinematic.ActorPose.*;
import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * ONE PUNCH, the choreography: where the two bodies are on the film's plain and how the target takes
 * it. Stage space as the film camera uses it: +z is the line of the punch (toward the mountain), y up;
 * looking down +z, +x is on the left of the frame, so Hulk stands a little to +x (left) and his target
 * a little to -x (right), as in the reference shot.
 */
public final class RagePath {
    public static final Vec3 HULK_HOME = new Vec3(.45, 0, 0), TARGET_HOME = new Vec3(-.3, 0, 3.1), TARGET_REST = new Vec3(-3.4, 0, 17);
    /** The mountain the punch splits: its near face, depth, height and half width (blocks). */
    public static final double MOUNTAIN_Z = 125, MOUNTAIN_DEPTH = 60, MOUNTAIN_HEIGHT = 82, MOUNTAIN_HALF = 165;

    private RagePath() {}

    static float clamp(float t) { return HulkMotion.clamp(t); }
    static float ease(float t) { return HulkMotion.ease(t); }
    static double hash(double n) { double x = Math.sin(n * 12.9898) * 43758.5453; return x - Math.floor(x); }

    /** How long before punch i lands Hulk starts shifting to the spot he throws it from. */
    private static float move(int i) {
        float gap = i == 0 ? 8 : ULT_HITS[i] - ULT_HITS[i - 1];
        return Math.max(1, Math.min(4, gap * .8f));
    }
    /** Where Hulk stands for punch i: half a step to one side or the other, sometimes closer in. */
    private static Vec3 station(int i) {
        if (i < 0) return HULK_HOME;
        double side = ULT_RIGHT[i] ? 1 : -1, reach = i < 5 ? .55 : 1;
        return HULK_HOME.add(side * (.2 + .35 * hash(i * 7.13)) * reach, 0, (-.1 + .55 * hash(i * 3.71)) * reach);
    }

    /** Hulk's feet. */
    public static Vec3 hulk(float t) {
        if (t < ULT_FIRST) return HULK_HOME;
        if (t < ULT_STOP + 2) {
            int i = 0;
            while (i + 1 < ULT_HITS.length && t >= ULT_HITS[i + 1] - move(i + 1)) i++;
            float k = ease((t - (ULT_HITS[i] - move(i))) / move(i));
            return station(i - 1).lerp(station(i), k);
        }
        Vec3 last = station(ULT_HITS.length - 1);
        if (t < ULT_WINDUP) return last.lerp(HULK_HOME, ease((t - ULT_STOP - 2) / (ULT_CLEAR - ULT_STOP)));
        // The wind-up: a wide stance a little back; then the whole body thrown forward into the punch.
        Vec3 set = HULK_HOME.add(.12 * ease((t - ULT_WINDUP) / 8), 0, -.35 * ease((t - ULT_WINDUP) / 8));
        float drive = HulkMotion.snap(t, ULT_PUNCH - 3, ULT_PUNCH);
        return set.add(0, 0, 1.15 * drive);
    }

    /** Sum of the recent blows from one hand, each fading over a few ticks: how hard the target is rocking that way. */
    private static float jolt(float t, boolean right) {
        float sum = 0;
        for (int i = 0; i < ULT_HITS.length; i++) {
            float since = t - ULT_HITS[i];
            if (since < 0 || since > 16 || ULT_RIGHT[i] != right) continue;
            sum += ULT_POWER[i] * (float) Math.exp(-since / 3.5);
        }
        return Math.min(1.6f, sum);
    }

    /** The target's feet. */
    public static Vec3 target(float t) {
        float r = jolt(t, true), l = jolt(t, false);
        // A right hand comes in from Hulk's right (-x) and shoves them toward +x, a left the other way;
        // the whole barrage drives them back.
        float back = .9f * clamp((t - ULT_FIRST) / (ULT_STOP - ULT_FIRST));
        Vec3 held = TARGET_HOME.add(.22 * (r - l), 0, back + .12 * (r + l));
        if (t < ULT_PUNCH) return held;
        float u = clamp((t - ULT_PUNCH) / 36f), along = 1 - (1 - u) * (1 - u) * (1 - u);
        Vec3 from = TARGET_HOME.add(0, 0, .9);
        Vec3 flat = from.lerp(TARGET_REST, along);
        double up = u < 1 ? 2.4 * Math.sin(Math.PI * u) * (1 - .35 * u) : 0;
        // A short skid after they come down.
        return flat.add(0, up, 0);
    }
    public static float targetYaw(float t) {
        float r = jolt(t, true), l = jolt(t, false);
        return 180 + (r - l) * 14;
    }

    // ------------------------------------------------------------------ how the target takes it
    private static final FilmCast.Track TARGET = track();

    public static ActorPose targetPose(float t) {
        ActorPose p = TARGET.sample(t).copy();
        if (t < ULT_PUNCH) {
            float r = jolt(t, true), l = jolt(t, false);
            // Each blow twists them away from the hand that threw it and knocks the head round.
            p.rot[CHEST][1] += .38f * (r - l); p.rot[HEAD][1] += .55f * (r - l);
            p.rot[CHEST][0] -= .22f * (r + l); p.rot[HEAD][0] -= .3f * (r + l);
            p.bodyRoll += (r - l) * 9;
            p.crouch += .05f * (r + l);
            p.rot[RIGHT_UPPER_ARM][0] += .25f * (r + l); p.rot[LEFT_UPPER_ARM][0] += .25f * (r + l);
            // Shaking on their feet after it stops.
            if (t > ULT_STOP && t < ULT_WINDUP + 20) {
                float s = (float) Math.sin(t * 2.3) * .05f;
                p.rot[CHEST][2] += s; p.rot[HEAD][2] -= s;
            }
        } else if (t < ULT_PUNCH + 36) {
            // Tumbling through the air, head over heels, coming down flat on their back.
            float u = clamp((t - ULT_PUNCH) / 36f);
            p.bodyPitch = 45 + 403 * (1 - (1 - u) * (1 - u));
        } else if (t < ULT_WIDE - 16) p.bodyPitch = 88;
        return p;
    }
    private static FilmCast.Track track() {
        ActorPose ready = of().j(CHEST, 6, 0, 0).j(RIGHT_UPPER_ARM, -30, 0, 12).j(RIGHT_LOWER_ARM, -60, 0, 0)
                .j(LEFT_UPPER_ARM, -32, 0, -12).j(LEFT_LOWER_ARM, -64, 0, 0).j(RIGHT_UPPER_LEG, -8, 0, 5).j(LEFT_UPPER_LEG, 10, 0, -5).crouch(.06f);
        // Arms crossed in an X before the face, braced.
        ActorPose guard = of().j(CHEST, 12, 0, 0).j(HEAD, 10, 0, 0).j(RIGHT_UPPER_ARM, -100, 28, 12).j(RIGHT_LOWER_ARM, -72, 0, 0)
                .j(LEFT_UPPER_ARM, -100, -28, -12).j(LEFT_LOWER_ARM, -72, 0, 0).j(RIGHT_UPPER_LEG, -14, 0, 7).j(LEFT_UPPER_LEG, 16, 0, -7)
                .j(LEFT_LOWER_LEG, 18, 0, 0).j(RIGHT_LOWER_LEG, 8, 0, 0).crouch(.14f);
        // Beaten: the guard is gone, hunched over, head hanging.
        ActorPose battered = of().j(CHEST, 30, 0, 0).j(HEAD, 22, 0, 0).j(RIGHT_UPPER_ARM, -18, 0, 16).j(RIGHT_LOWER_ARM, -25, 0, 0)
                .j(LEFT_UPPER_ARM, -10, 0, -18).j(LEFT_LOWER_ARM, -20, 0, 0).j(RIGHT_UPPER_LEG, -18, 0, 8).j(LEFT_UPPER_LEG, 14, 0, -6)
                .j(RIGHT_LOWER_LEG, 22, 0, 0).j(LEFT_LOWER_LEG, 26, 0, 0).crouch(.28f).body(7, 0);
        // A last, shaky guard as he winds up.
        ActorPose shaky = of().j(CHEST, 16, 0, 0).j(HEAD, 6, 0, 0).j(RIGHT_UPPER_ARM, -82, 22, 10).j(RIGHT_LOWER_ARM, -62, 0, 0)
                .j(LEFT_UPPER_ARM, -80, -22, -10).j(LEFT_LOWER_ARM, -60, 0, 0).j(RIGHT_UPPER_LEG, -16, 0, 7).j(LEFT_UPPER_LEG, 14, 0, -7)
                .j(LEFT_LOWER_LEG, 16, 0, 0).crouch(.18f);
        // Struck: thrown backwards, arms and legs flung out ahead of the body.
        ActorPose blown = of().j(CHEST, -32, 0, 0).j(HEAD, -38, 0, 0).j(RIGHT_UPPER_ARM, -150, 0, 30).j(RIGHT_LOWER_ARM, -15, 0, 0)
                .j(LEFT_UPPER_ARM, -145, 0, -30).j(LEFT_LOWER_ARM, -15, 0, 0).j(RIGHT_UPPER_LEG, -55, 0, 10).j(RIGHT_LOWER_LEG, 30, 0, 0)
                .j(LEFT_UPPER_LEG, -40, 0, -10).j(LEFT_LOWER_LEG, 40, 0, 0).body(0, 45);
        // Down on their back after the skid.
        ActorPose down = of().j(HEAD, 8, 20, 0).j(RIGHT_UPPER_ARM, -10, 0, 75).j(RIGHT_LOWER_ARM, -20, 0, 0).j(LEFT_UPPER_ARM, -5, 0, -70)
                .j(LEFT_LOWER_ARM, -15, 0, 0).j(RIGHT_UPPER_LEG, -6, 0, 10).j(LEFT_UPPER_LEG, -14, 0, -8).j(LEFT_LOWER_LEG, 30, 0, 0).body(0, 88);
        // Propped up on their arms, staring at what is behind them.
        ActorPose propped = of().j(CHEST, 20, 0, 0).j(HEAD, -10, 25, 0).j(RIGHT_UPPER_ARM, 35, 0, 18).j(RIGHT_LOWER_ARM, -10, 0, 0)
                .j(LEFT_UPPER_ARM, 30, 0, -18).j(LEFT_LOWER_ARM, -10, 0, 0).j(RIGHT_UPPER_LEG, -70, 0, 8).j(RIGHT_LOWER_LEG, 30, 0, 0)
                .j(LEFT_UPPER_LEG, -60, 0, -8).j(LEFT_LOWER_LEG, 50, 0, 0).body(0, 55);
        return new FilmCast.Track().key(0, 0, ready).key(ULT_FIRST + 4, 5, guard, noticeChain())
                .key(ULT_STOP + 2, 6, battered, impactChain()).key(ULT_WINDUP + 4, 14, shaky, noticeChain())
                .key(ULT_PUNCH + 1, 2, blown, launchChain()).key(ULT_PUNCH + 34, 3, blown)
                .key(ULT_PUNCH + 37, 3, down, impactChain()).key(ULT_WIDE + 6, 22, propped);
    }
}
