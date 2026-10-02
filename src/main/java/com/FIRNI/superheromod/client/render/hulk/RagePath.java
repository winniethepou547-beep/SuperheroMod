package com.FIRNI.superheromod.client.render.hulk;

import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.core.cinematic.ActorPose.*;
import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * Where the two bodies are during GAMMA RAGE, in the film's stage space (x to Hulk's right, y up,
 * z from Hulk toward the target, who stands at distance d). One source for the camera, the bodies
 * drawn in the world and the effects. The target crashes first; Hulk's smash lands `smash` ticks
 * into the film, a few blocks from where he started the dive, never on top of them.
 */
public final class RagePath {
    private final double d;
    private final int smash;
    private final Vec3 start;

    public RagePath(double distance, int smash) { this(distance, smash, Vec3.ZERO); }
    /** start: where the gather and the roar happen; set further back than his real spot so the leap onto the target is a long one. */
    public RagePath(double distance, int smash, Vec3 start) { this.d = distance; this.smash = smash; this.start = start; }
    public Vec3 start() { return start; }
    public double distance() { return d; }
    public int smash() { return smash; }
    public int total() { return smash + ULT_TOTAL_AFTER; }
    private static float k(float t, float a, float b) { return HulkMotion.clamp((t - a) / (b - a)); }
    private static float ease(float x) { return HulkMotion.ease(x); }

    /** Where the target lies after the crash (the real crater). */
    public Vec3 crater() { return new Vec3(0, 0, d); }
    /** Where Hulk stands in the crater after the smash (beside the target, not on them). */
    public Vec3 smashSpot() { return new Vec3(0, 0, d - 1.6); }

    public Vec3 hulk(float t) {
        Vec3 grab = new Vec3(0, 0, d - 1.3);
        if (t < ULT_LEAP) return start;
        if (t < ULT_GRAB) {
            // One huge bound from far off, coming down on them.
            float x = k(t, ULT_LEAP, ULT_GRAB);
            double far = grab.z - start.z;
            return new Vec3(0, start.y * (1 - ease(x)) + (3 + .4 * far) * Math.sin(Math.PI * x), start.z + far * ease(x));
        }
        if (t < ULT_SKY) return grab;
        double H = ULT_HEIGHT;
        if (t < ULT_HOLD) {
            float x = k(t, ULT_SKY, ULT_HOLD);
            return grab.add(0, H * (1 - Math.pow(1 - x, 2.5)), 0);
        }
        if (t < ULT_RAISE) return grab.add(0, H + .25 * Math.sin((t - ULT_HOLD) * .2), 0);
        int dive = smash - 14;
        if (t < dive) return grab.add(0, H + 1.6 * ease(k(t, ULT_RAISE, dive)), 0);
        if (t < smash) {
            float x = k(t, dive, smash);
            Vec3 top = grab.add(0, H + 1.6, 0), end = smashSpot();
            return new Vec3(0, top.y * (1 - x * x), top.z + (end.z - top.z) * x);
        }
        return smashSpot();
    }

    /** The target's feet. */
    public Vec3 target(float t) {
        if (t < ULT_GRAB) return new Vec3(0, 0, d);
        Vec3 h = hulk(t);
        if (t < ULT_SKY) {
            float lift = ease(k(t, ULT_GRAB, ULT_GRAB + 8));
            return h.add(.75, 2.3 * lift, .9 - .3 * lift);
        }
        if (t < ULT_HOLD) return h.add(.5, 2.9, .35);
        if (t < ULT_THROW + 3) {
            float both = ease(k(t, ULT_HOLD, ULT_HOLD + 6));
            Vec3 overhead = h.add(.5, 2.9, .35), face = h.add(0, 1.6, 1.15);
            return overhead.lerp(face, both);
        }
        if (t < ULT_CRASH) {
            Vec3 from = hulk(ULT_THROW + 3).add(0, 1.6, 1.15);
            float x = k(t, ULT_THROW + 3, ULT_CRASH);
            return new Vec3(0, from.y * (1 - x * x), from.z + (d - from.z) * x);
        }
        return new Vec3(0, -.15, d);
    }

    // ------------------------------------------------------------------ the target's body language
    private static final FilmCast.Track TARGET = track();

    public static ActorPose targetPose(float t) {
        ActorPose p = TARGET.sample(t).copy();
        // Kicking and struggling while he holds them.
        if (t >= ULT_GRAB && t < ULT_THROW) {
            float s = (float) Math.sin(t * .9), c = (float) Math.sin(t * .7 + 1.3);
            p.rot[RIGHT_UPPER_LEG][0] += .45f * s; p.rot[LEFT_UPPER_LEG][0] -= .45f * s;
            p.rot[RIGHT_LOWER_LEG][0] += .3f * Math.max(0, c); p.rot[LEFT_LOWER_LEG][0] += .3f * Math.max(0, -c);
            p.rot[RIGHT_UPPER_ARM][0] += .2f * c; p.rot[LEFT_UPPER_ARM][0] -= .2f * c;
        }
        if (t >= ULT_THROW && t < ULT_CRASH) {
            float s = (float) Math.sin(t * .8);
            p.rot[RIGHT_UPPER_ARM][2] += .3f * s; p.rot[LEFT_UPPER_ARM][2] -= .3f * s;
        }
        return p;
    }
    private static FilmCast.Track track() {
        ActorPose ready = of().j(CHEST, 6, 0, 0).j(RIGHT_UPPER_ARM, -26, 0, 12).j(RIGHT_LOWER_ARM, -55, 0, 0)
                .j(LEFT_UPPER_ARM, -30, 0, -12).j(LEFT_LOWER_ARM, -60, 0, 0).j(RIGHT_UPPER_LEG, -8, 0, 5).j(LEFT_UPPER_LEG, 10, 0, -5).crouch(.05f);
        // He roars; they flinch back, arms up.
        ActorPose flinch = of().j(CHEST, -14, 0, 0).j(HEAD, -10, 0, 0).j(RIGHT_UPPER_ARM, -80, -40, 10).j(RIGHT_LOWER_ARM, -70, 0, 0)
                .j(LEFT_UPPER_ARM, -85, 40, -10).j(LEFT_LOWER_ARM, -70, 0, 0).j(RIGHT_UPPER_LEG, 18, 0, 6).j(LEFT_UPPER_LEG, -12, 0, -6)
                .j(LEFT_LOWER_LEG, 20, 0, 0).crouch(.1f);
        // Caught by the chest and hoisted: arms clawing at his fist, legs dangling.
        ActorPose caught = of().j(CHEST, -10, 0, 0).j(HEAD, -20, 0, 0).j(RIGHT_UPPER_ARM, -120, -30, 20).j(RIGHT_LOWER_ARM, -60, 0, 0)
                .j(LEFT_UPPER_ARM, -115, 30, -20).j(LEFT_LOWER_ARM, -60, 0, 0).j(RIGHT_UPPER_LEG, 10, 0, 8).j(RIGHT_LOWER_LEG, 30, 0, 0)
                .j(LEFT_UPPER_LEG, -5, 0, -8).j(LEFT_LOWER_LEG, 40, 0, 0);
        // Held in front of his face: pushing at his hands, head turned away from the roar.
        ActorPose held = of().j(CHEST, 10, 0, 0).j(HEAD, -25, 25, 0).j(RIGHT_UPPER_ARM, -95, -20, 10).j(RIGHT_LOWER_ARM, -30, 0, 0)
                .j(LEFT_UPPER_ARM, -95, 20, -10).j(LEFT_LOWER_ARM, -30, 0, 0).j(RIGHT_UPPER_LEG, 15, 0, 6).j(RIGHT_LOWER_LEG, 35, 0, 0)
                .j(LEFT_UPPER_LEG, 5, 0, -6).j(LEFT_LOWER_LEG, 45, 0, 0);
        // Flung at the ground: tipped over backwards, limbs thrown up.
        ActorPose thrown = of().j(CHEST, -30, 0, 0).j(HEAD, -30, 0, 0).j(RIGHT_UPPER_ARM, -160, 0, 30).j(RIGHT_LOWER_ARM, -15, 0, 0)
                .j(LEFT_UPPER_ARM, -155, 0, -30).j(LEFT_LOWER_ARM, -15, 0, 0).j(RIGHT_UPPER_LEG, -50, 0, 10).j(RIGHT_LOWER_LEG, 30, 0, 0)
                .j(LEFT_UPPER_LEG, -35, 0, -10).j(LEFT_LOWER_LEG, 40, 0, 0).body(0, 75);
        // In the crater, flat on their back.
        ActorPose down = of().j(HEAD, 8, 20, 0).j(RIGHT_UPPER_ARM, -10, 0, 75).j(RIGHT_LOWER_ARM, -20, 0, 0).j(LEFT_UPPER_ARM, -5, 0, -70)
                .j(LEFT_LOWER_ARM, -15, 0, 0).j(RIGHT_UPPER_LEG, -6, 0, 10).j(LEFT_UPPER_LEG, -14, 0, -8).j(LEFT_LOWER_LEG, 30, 0, 0).body(0, 88);
        ActorPose shaken = down.copy().j(HEAD, -15, 30, 0).j(RIGHT_UPPER_ARM, -40, 0, 60).j(RIGHT_LOWER_LEG, 50, 0, 0);
        return new FilmCast.Track().key(0, 0, ready).key(ULT_ROAR + 4, 6, flinch, noticeChain())
                .key(ULT_GRAB, 3, caught, impactChain()).key(ULT_HOLD + 6, 8, held, noticeChain())
                .key(ULT_THROW + 4, 4, thrown, launchChain()).key(ULT_CRASH, 6, thrown)
                .key(ULT_CRASH + 2, 2, down, impactChain()).key(ULT_CRASH + 30, 14, shaken).key(ULT_SMASH_DEFAULT + 2, 4, down, impactChain());
    }
}
