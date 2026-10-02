package com.FIRNI.superheromod.client.render.thor;

import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.core.cinematic.ActorPose.*;
import static com.FIRNI.superheromod.heroes.thor.ThorAction.*;

/**
 * Where the two bodies are during "God of Thunder — Aerial Punishment", in the film's stage space
 * (x to Thor's right, y up, z from Thor toward the target, who starts at distance d). One source
 * for the film's camera, the bodies drawn in the world and the effects that follow them.
 *
 *   Thor lunges in, two hits, the uppercut sends the target straight up; Thor whirls and flies
 *   after them, catches them at the top, the storm, the whiteout; he lets go, the target is
 *   driven into the ground, Thor glides down a few blocks away.
 */
public final class AerialPath {
    /** The target relative to Thor while he has them locked: a little above him and in front. */
    public static final Vec3 LOCK = new Vec3(0, .35, .6);
    private final double d;

    public AerialPath(double distance) { d = distance; }

    public double distance() { return d; }
    private static float ease(float t) { return ThorMotion.ease(t); }
    private static float clamp(float t) { return ThorMotion.clamp(t); }

    /** Where the target ends up: knocked back a little by the second hit, then straight down into it. */
    public Vec3 crater() { return new Vec3(0, 0, d + ULT_KNOCK); }
    public Vec3 landing() { return new Vec3(ULT_LAND_X, 0, d - ULT_LAND_SHORT); }

    /** Height of the lock while the two of them hang in the storm and start to fall. */
    private double lockY(float t) {
        double y = ULT_HEIGHT - .4;
        y -= 2.5 * ease((t - ULT_CATCH) / (ULT_LET_GO - ULT_CATCH));
        if (t > ULT_FADE) y -= 3.0 * Math.pow(clamp((t - ULT_FADE) / (ULT_LET_GO - ULT_FADE)), 2);
        return y;
    }

    /** The target's feet. */
    public Vec3 target(float t) {
        double x = 0, y = 0, z = d;
        // First hit: the body is shoved toward Thor's right and comes back.
        x += .28 * ease((t - ULT_HIT1) / 4f) * (1 - ease((t - ULT_HIT1 - 6) / 10f));
        // Second hit breaks the guard and drives them back.
        z += ULT_KNOCK * ease((t - ULT_HIT2) / 8f);
        if (t >= ULT_UPPER && t < ULT_APEX) {
            float k = clamp((t - ULT_UPPER) / (ULT_APEX - ULT_UPPER));
            y = ULT_HEIGHT * (1 - Math.pow(1 - k, 2.3));
        } else if (t >= ULT_APEX && t < ULT_CATCH) {
            // The top of the arc: it slows, stops, starts to drop.
            float k = (t - ULT_APEX) / (ULT_CATCH - ULT_APEX);
            y = ULT_HEIGHT - .4 * k * k;
        } else if (t >= ULT_CATCH && t < ULT_LET_GO) {
            y = lockY(t) + LOCK.y;
        } else if (t >= ULT_LET_GO && t < ULT_CRASH) {
            float k = clamp((t - ULT_LET_GO) / (ULT_CRASH - ULT_LET_GO));
            y = (lockY(ULT_LET_GO) + LOCK.y) * (1 - k * k);
        } else if (t >= ULT_CRASH) {
            y = -.25;
        }
        return new Vec3(x, y, z);
    }

    /** Thor's feet. */
    public Vec3 thor(float t) {
        Vec3 strike = new Vec3(0, 0, d - 1.75);
        if (t < ULT_RISE) return new Vec3(0, 0, (d - 1.75) * ease((t - ULT_LUNGE) / 10f));
        Vec3 lock = target(ULT_CATCH).subtract(LOCK);
        if (t < ULT_APEX) {
            // Thrown upward by the whirl: fast off the ground, still climbing hard as he comes up under them.
            float k = clamp((t - ULT_RISE) / (ULT_APEX - ULT_RISE));
            Vec3 under = new Vec3(0, ULT_HEIGHT - 3.2, lock.z);
            double up = Math.pow(k, 1.3);
            return new Vec3(0, under.y * up, strike.z + (under.z - strike.z) * ease(k));
        }
        if (t < ULT_CATCH) {
            float k = ease((t - ULT_APEX) / (ULT_CATCH - ULT_APEX));
            Vec3 under = new Vec3(0, ULT_HEIGHT - 3.2, lock.z);
            return under.lerp(lock, k);
        }
        if (t < ULT_LET_GO) return target(t).subtract(LOCK);
        // Let go: he brakes in the air and stays above, then glides down beside the crater.
        Vec3 release = target(ULT_LET_GO).subtract(LOCK);
        Vec3 land = landing();
        if (t >= ULT_LANDED) return land;
        float k = clamp((t - ULT_LET_GO) / (ULT_LANDED - ULT_LET_GO));
        double y = release.y + 1.2 * Math.sin(Math.PI * Math.min(1, k * 2.5)) * (1 - k);
        y *= 1 - ease((k - .2f) / .8f);
        double h = ease((k - .1f) / .7f);
        return new Vec3(release.x + (land.x - release.x) * h, Math.max(0, y), release.z + (land.z - release.z) * h);
    }

    /** Which way Thor faces, in Minecraft yaw degrees added to the stage's own facing. */
    public float thorYaw(float t) {
        if (t < ULT_LET_GO) return 0;
        Vec3 to = crater().subtract(landing());
        float face = (float) Math.toDegrees(Math.atan2(to.x, to.z));
        return face * ease((t - ULT_LET_GO - 20) / 30f);
    }

    // ------------------------------------------------------------------ the target's body language
    private static final FilmCast.Track TARGET = targetTrack();

    /**
     * How the target takes it: braced, bent sideways by the first hit, the guard up and broken by
     * the second, thrown up by the uppercut with the limbs trailing, limp in the lock, falling on
     * their back, and lying in the crater. Layered with shudders and flailing.
     */
    public static ActorPose targetPose(float t) {
        ActorPose p = TARGET.sample(t).copy();
        if (t >= ULT_UPPER && t < ULT_CATCH) {
            // Flailing on the way up, slowing as the climb runs out.
            float f = 1 - ThorMotion.clamp((t - ULT_UPPER) / (ULT_CATCH - ULT_UPPER)) * .6f;
            float a = (float) Math.sin(t * .55) * .35f * f, b = (float) Math.sin(t * .41 + 1.3) * .3f * f;
            p.rot[RIGHT_UPPER_ARM][0] += a; p.rot[LEFT_UPPER_ARM][0] -= b;
            p.rot[RIGHT_UPPER_LEG][0] += b * .6f; p.rot[LEFT_UPPER_LEG][0] -= a * .6f;
        }
        if (t >= ULT_CATCH && t < ULT_LET_GO) {
            // Held in the lock while the lightning runs through both of them.
            float s = t > ULT_EYEBOLT ? .07f : .03f;
            p.rot[CHEST][0] += (float) Math.sin(t * 3.3) * s; p.rot[HEAD][2] += (float) Math.sin(t * 4.1) * s * 1.5f;
            p.rot[RIGHT_LOWER_LEG][0] += (float) Math.sin(t * 2.7) * s * 2;
        }
        return p;
    }
    private static FilmCast.Track targetTrack() {
        ActorPose ready = of().j(CHEST, 6, 0, 0).j(RIGHT_UPPER_ARM, -26, 0, 12).j(RIGHT_LOWER_ARM, -55, 0, 0)
                .j(LEFT_UPPER_ARM, -30, 0, -12).j(LEFT_LOWER_ARM, -60, 0, 0).j(RIGHT_UPPER_LEG, -8, 0, 5).j(LEFT_UPPER_LEG, 10, 0, -5).crouch(.05f);
        ActorPose wary = ready.copy().j(CHEST, 12, 0, 0).j(HEAD, 8, 0, 0).j(RIGHT_UPPER_ARM, -45, 0, 16).j(LEFT_UPPER_ARM, -50, 0, -16)
                .j(RIGHT_UPPER_LEG, -18, 0, 6).j(RIGHT_LOWER_LEG, 22, 0, 0).j(LEFT_UPPER_LEG, 14, 0, -6).j(LEFT_LOWER_LEG, 12, 0, 0).crouch(.14f);
        // First hit: the side caves in, head snapped over, the arms thrown the other way; the feet stay down.
        ActorPose bent = of().j(CHEST, 4, -18, 26).j(HEAD, -6, -25, 30).j(HIPS, 0, 6, -10)
                .j(RIGHT_UPPER_ARM, -30, 0, 70).j(RIGHT_LOWER_ARM, -25, 0, 0).j(LEFT_UPPER_ARM, -15, 0, -35).j(LEFT_LOWER_ARM, -50, 0, 0)
                .j(RIGHT_UPPER_LEG, -10, 0, 12).j(LEFT_UPPER_LEG, 6, 0, -4).j(LEFT_LOWER_LEG, 18, 0, 0).crouch(.12f);
        // The guard: forearms crossed in front of the face, braced low.
        ActorPose block = of().j(CHEST, 22, 0, 0).j(HEAD, 16, 0, 0)
                .j(RIGHT_UPPER_ARM, -82, -64, 0).j(RIGHT_LOWER_ARM, -72, 0, 0).j(LEFT_UPPER_ARM, -88, 64, 0).j(LEFT_LOWER_ARM, -70, 0, 0)
                .j(LEFT_UPPER_LEG, -38, 0, -6).j(LEFT_LOWER_LEG, 48, 0, 0).j(RIGHT_UPPER_LEG, 24, 0, 6).j(RIGHT_LOWER_LEG, 26, 0, 0).crouch(.28f);
        // Broken: the arms torn open and back, chest and head thrown back.
        ActorPose broken = of().j(CHEST, -44, 0, 0).j(HEAD, -34, 0, 8).j(HIPS, 14, 0, 0).j(RIGHT_UPPER_ARM, 48, 0, 52).j(RIGHT_LOWER_ARM, -8, 0, 0)
                .j(LEFT_UPPER_ARM, 48, 0, -52).j(LEFT_LOWER_ARM, -8, 0, 0).j(RIGHT_UPPER_LEG, -14, 0, 8).j(RIGHT_LOWER_LEG, 28, 0, 0)
                .j(LEFT_UPPER_LEG, -8, 0, -8).j(LEFT_LOWER_LEG, 22, 0, 0).crouch(.10f);
        // Reeling: off balance, arms loose, a knee buckling.
        ActorPose reeling = of().j(CHEST, -22, 10, -8).j(HEAD, -18, 14, -12).j(RIGHT_UPPER_ARM, 8, 0, 30).j(RIGHT_LOWER_ARM, -20, 0, 0)
                .j(LEFT_UPPER_ARM, 4, 0, -26).j(LEFT_LOWER_ARM, -24, 0, 0).j(RIGHT_UPPER_LEG, -20, 0, 6).j(RIGHT_LOWER_LEG, 34, 0, 0)
                .j(LEFT_UPPER_LEG, 12, 0, -6).j(LEFT_LOWER_LEG, 10, 0, 0).crouch(.16f);
        // The uppercut lands under the jaw: head and chest thrown back, arms flung up, legs left behind.
        ActorPose launched = of().j(CHEST, -48, 0, 0).j(HEAD, -55, 0, 0).j(HIPS, 12, 0, 0)
                .j(RIGHT_UPPER_ARM, -150, 0, 38).j(RIGHT_LOWER_ARM, -20, 0, 0).j(LEFT_UPPER_ARM, -140, 0, -42).j(LEFT_LOWER_ARM, -26, 0, 0)
                .j(RIGHT_UPPER_LEG, 18, 0, 6).j(RIGHT_LOWER_LEG, 40, 0, 0).j(LEFT_UPPER_LEG, 8, 0, -6).j(LEFT_LOWER_LEG, 28, 0, 0).body(0, 8);
        ActorPose rising = launched.copy().j(CHEST, -30, 0, 0).j(HEAD, -30, 0, 0).j(RIGHT_UPPER_ARM, -120, 0, 60).j(LEFT_UPPER_ARM, -115, 0, -62)
                .j(RIGHT_UPPER_LEG, 24, 0, 10).j(LEFT_UPPER_LEG, 14, 0, -10).body(0, 14);
        // The top: limp, arms drifting out, head lolling.
        ActorPose apex = of().j(CHEST, -12, 0, 0).j(HEAD, -20, 0, 10).j(RIGHT_UPPER_ARM, -70, 0, 70).j(RIGHT_LOWER_ARM, -30, 0, 0)
                .j(LEFT_UPPER_ARM, -60, 0, -75).j(LEFT_LOWER_ARM, -35, 0, 0).j(RIGHT_UPPER_LEG, 20, 0, 8).j(RIGHT_LOWER_LEG, 30, 0, 0)
                .j(LEFT_UPPER_LEG, 10, 0, -8).j(LEFT_LOWER_LEG, 40, 0, 0);
        // Locked: arms pinned to the sides, head forced back, legs hanging.
        ActorPose locked = of().j(CHEST, -18, 0, 0).j(HEAD, -38, 0, 0).j(RIGHT_UPPER_ARM, 6, 0, 10).j(RIGHT_LOWER_ARM, -30, 0, 0)
                .j(LEFT_UPPER_ARM, 6, 0, -10).j(LEFT_LOWER_ARM, -30, 0, 0).j(RIGHT_UPPER_LEG, 16, 0, 4).j(RIGHT_LOWER_LEG, 26, 0, 0)
                .j(LEFT_UPPER_LEG, 6, 0, -4).j(LEFT_LOWER_LEG, 34, 0, 0);
        // Falling on their back, limbs up.
        ActorPose falling = of().j(CHEST, -20, 0, 0).j(HEAD, 10, 0, 0).j(RIGHT_UPPER_ARM, -150, 0, 30).j(RIGHT_LOWER_ARM, -20, 0, 0)
                .j(LEFT_UPPER_ARM, -150, 0, -30).j(LEFT_LOWER_ARM, -20, 0, 0).j(RIGHT_UPPER_LEG, -40, 0, 8).j(RIGHT_LOWER_LEG, 30, 0, 0)
                .j(LEFT_UPPER_LEG, -30, 0, -8).j(LEFT_LOWER_LEG, 40, 0, 0).body(0, 70);
        // In the crater: flat on their back, arms thrown out.
        ActorPose down = of().j(HEAD, 8, 20, 0).j(RIGHT_UPPER_ARM, -10, 0, 75).j(RIGHT_LOWER_ARM, -20, 0, 0).j(LEFT_UPPER_ARM, -5, 0, -70)
                .j(LEFT_LOWER_ARM, -15, 0, 0).j(RIGHT_UPPER_LEG, -6, 0, 10).j(LEFT_UPPER_LEG, -14, 0, -8).j(LEFT_LOWER_LEG, 30, 0, 0).body(0, 88);
        return new FilmCast.Track().key(0, 0, ready).key(ULT_LUNGE + 6, 8, wary, noticeChain())
                .key(ULT_HIT1 + 2, 3, bent, impactChain()).key(ULT_HIT1 + 10, 8, wary)
                .key(ULT_HIT2 - 4, 5, block, noticeChain()).key(ULT_HIT2 + 2, 3, broken, impactChain())
                .key(ULT_HIT2 + 12, 10, reeling).key(ULT_UPPER - 2, 6, reeling)
                .key(ULT_UPPER + 2, 3, launched, launchChain()).key(ULT_UPPER + 30, 25, rising)
                .key(ULT_APEX + 2, 10, apex).key(ULT_CATCH + 3, 4, locked, impactChain()).key(ULT_LET_GO - 2, 10, locked)
                .key(ULT_LET_GO + 10, 10, falling, launchChain()).key(ULT_CRASH, 5, falling).key(ULT_CRASH + 2, 2, down, impactChain());
    }
}
