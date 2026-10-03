package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.client.render.film.Film;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import static com.FIRNI.superheromod.client.render.panther.PursuitPath.*;
import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * The camera of THE FINAL PURSUIT, placed moment by moment. Inside the car it rides the car (its sway, its
 * thumps, its lean in the swerves show as the world tilting past the windows), with a hand-held drift on top:
 * the eye so close nothing else shows, drawing back to the driver's seat; across from the mirror; the gunman; the
 * shots; the empty seat and the face at the window; the holes in the roof; out alongside for the leap; down on the
 * roof for the claws and the tear; up after the gunman; behind for the charge. After the release, in the slow
 * motion: wide from the pavement, round him in the air, low for his landing; the whip to the car coming down; the
 * crash; behind him in the dust; the last slow move round to his shoulder. Kicks follow what happens and which way.
 */
public final class PursuitCamera {
    private PursuitCamera() {}

    /** The end of the stage (the world after it is filmed by the film's own shot). */
    public static Film.View view(float t) {
        if (t >= ULT_STAGE_END) return null;
        float tau = scene(t);
        if (t < ULT_MIRROR) return opening(t, tau);
        if (t < ULT_NPC) return inCar(tau, v(-.24, 1.21, .92), v(.40, .98, -.02).add(drift(t, .04)), 60, 0);
        if (t < ULT_GLANCE) return inCar(tau, v(-.36, 1.12, -.12), v(.42, 1.06, -.95).add(drift(t, .03)), 58, 0);
        if (t < ULT_RAISE) {
            Vec3 eye = pantherEye(tau);
            return worldView(car(tau), v(-.04, 1.08, .58), eye, 40, 0, t);
        }
        if (t < ULT_FIRE + 12) return inCar(tau, v(-.45, .98, .10), v(.85, 1.06, -.38).add(drift(t, .03)), 64, 0);
        if (t < ULT_FIRE + 26) {
            Vec3 chest = PursuitRig.chest(panther(tau).matrix(), PursuitMoves.panther(tau, t));
            return worldView(car(tau), v(.06, 1.0, .64), chest, 46, 0, t);
        }
        if (t < ULT_EXIT) return inCar(tau, v(-.45, .98, .10), v(.85, 1.06, -.30).add(drift(t, .03)), 64, 0);
        if (t < ULT_REVEAL) return inCar(tau, v(-.48, .96, .06), v(.80, 1.08, -.1).add(drift(t, .02)), 70, 0);
        if (t < ULT_ROOF_FIRE) {
            // The empty seat; the turn to the window: his face, upside to the glass, looking in.
            float k = PursuitPath.ease((t - ULT_REVEAL) / 8);
            Car car = car(tau);
            Vec3 face = PursuitRig.head(panther(tau).matrix(), PursuitMoves.panther(tau, t));
            Vec3 aim = car.world(.40, .78, 0).lerp(face, k);
            return new Film.View(car.world(-.35, .95, .02).add(shake(tau, .004f)), aim.add(drift(t, .015)), Mth.lerp(k, 66, 50), 0);
        }
        if (t < ULT_HOP + 2) return inCar(tau, v(0, .72, .26), v(0, 1.42, -.52).add(drift(t, .02)), 72, 0);
        if (t < ULT_COIL) {
            // A whip round to the rear screen: the car behind, and him landing on it.
            float k = PursuitPath.ease((t - (ULT_HOP + 2)) / 3);
            Vec3 aim = v(0, 1.42, -.52).lerp(v(0, 1.75, -6), k);
            return inCar(tau, v(0, .95, .3), aim, Mth.lerp(k, 72, 52), 0);
        }
        if (t < ULT_TOUCH) {
            // Out alongside, matching their speed: the coil, the leap, the landing ahead.
            float k = (t - ULT_COIL) / (ULT_TOUCH - ULT_COIL);
            Car car = car(tau);
            Vec3 pos = car.world(Mth.lerp(k, -3.4, -3.1), Mth.lerp(k, 1.7, 2.1), Mth.lerp(PursuitPath.ease(k), -4.4, -.9));
            Vec3 at = panther(tau).feet().add(0, .9, 0);
            return new Film.View(pos.add(shake(t, .01f)), at, 55, -2);
        }
        if (t < ULT_INSIDE) {
            Car car = car(tau);
            return new Film.View(car.world(-1.15, 1.75, .95).add(shake(t, .006f)), car.world(0, 1.72, -.38), 50, 0);
        }
        if (t < ULT_TURN) return inCar(tau, v(-.32, .80, .22), v(0, 1.95, -.45).add(drift(t, .02)), 66, 0);
        if (t < ULT_CLAW_R) {
            Car car = car(tau);
            return new Film.View(car.world(-1.4, 2.05, -1.3).add(shake(t, .006f)), car.world(0, 1.85, .15), 52, 0);
        }
        if (t < ULT_TEAR) {
            Car car = car(tau);
            return new Film.View(car.world(-.85, 1.85, -.72).add(shake(t, .005f)), car.world(0, 1.5, -.18), 44, 0);
        }
        if (t < ULT_REACH) {
            Car car = car(tau);
            return new Film.View(car.world(1.9, 2.2, -2.8).add(shake(t, .008f)), car.world(0, 1.75, -.35), 56, 1.5f);
        }
        if (t < ULT_CHARGE) {
            // The throw: on him, then up after the gunman into the sky, then back down to him.
            Car car = car(tau);
            Vec3 him = panther(tau).feet().add(0, 1.0, 0);
            Vec3 thug = gunman(tau).feet().add(0, .9, 0);
            float up = PursuitPath.window(t, ULT_THROW + 3, ULT_CHARGE - 3, 4);
            Vec3 aim = him.lerp(thug, up * .85);
            return new Film.View(car.world(-1.25, 1.25, -1.05).add(shake(t, .006f)), aim, Mth.lerp(up, 54, 62), 0);
        }
        if (t < ULT_BOOM + 2) {
            // Behind the car, low, looking up at him: a slow push in as the energy builds, slower still in the held breath.
            Car car = car(tau);
            float k = PursuitPath.ease((tau - ULT_CHARGE) / (ULT_HOLD - ULT_CHARGE)) * .85f + .15f * PursuitPath.clamp((tau - ULT_HOLD) / (BOOM - ULT_HOLD));
            Vec3 pos = car.world(Mth.lerp(k, .3, .18), Mth.lerp(k, 1.72, 1.95), Mth.lerp(k, -5.8, -3.5));
            Vec3 at = panther(Math.min(tau, BOOM - .01f)).feet().add(0, 1.15, 0);
            return new Film.View(pos.add(shake(t, .004f)), at, Mth.lerp(k, 50, 43), 0);
        }
        float d = tau - BOOM;
        if (t < 520) {
            // Wide from the pavement in the slow motion: the sphere, the nose digging in, the car going over, him flung toward us.
            Vec3 him = panther(tau).feet().add(0, .9, 0);
            Vec3 com = car(tau).world(COM);
            Vec3 pos = new Vec3(-13.0, 2.0 + .04 * d, him.z - 3.8);
            return new Film.View(pos, him.lerp(com, .45).add(0, .6, 0), Mth.lerp(PursuitPath.clamp(d / 17), 60, 54), 0);
        }
        if (t < 558) {
            // Round him in the air: the tuck, the twist, the legs coming down.
            Vec3 him = panther(tau).feet().add(0, .9, 0);
            float k = PursuitPath.ease((t - 520) / 38);
            double ang = Mth.lerp(k, 2.6, 4.2);
            Vec3 pos = him.add(Math.cos(ang) * 3.6, .4 + .3 * k, Math.sin(ang) * 3.6);
            return new Film.View(pos, him, 50, 0);
        }
        Vec3 rest = REST_FEET;
        if (t < WHIP) {
            Vec3 him = panther(tau).feet().add(0, .8, 0);
            return new Film.View(new Vec3(rest.x - 2.0, .45, rest.z + 4.0), him, 54, 0);
        }
        Vec3 crashCam = new Vec3(CRASH_POS.x - 4.4, 1.0, CRASH_POS.z + 9.2);
        if (t < CRASH_T) {
            // The whip to the car: it comes down end over end toward us, him small behind it.
            Vec3 com = car(tau).world(COM);
            Vec3 him = panther(tau).feet().add(0, .8, 0);
            float k = PursuitPath.ease((t - WHIP) / 4);
            Vec3 from = new Vec3(rest.x - 2.0, .45, rest.z + 4.0);
            Vec3 pos = from.lerp(crashCam, k);
            Vec3 aim = him.lerp(him.lerp(com, .62), k);
            return new Film.View(pos, aim, Mth.lerp(k, 54, 62), 0);
        }
        if (t < DUST) {
            float k = PursuitPath.ease((t - CRASH_T) / 40);
            Vec3 pos = crashCam.add(0, .3 * k, 1.6 * k);
            Vec3 aim = new Vec3(CRASH_POS.x - .8, 2.2 - .6 * k, CRASH_POS.z - 2);
            return new Film.View(pos, aim, Mth.lerp(k, 64, 60), 0);
        }
        Vec3 behind = new Vec3(rest.x - 1.7, .95, rest.z - 6.5);
        Vec3 wreck = new Vec3(CRASH_POS.x, 1.5, CRASH_POS.z + 2);
        if (t < PursuitMoves.RISE) {
            // Behind him in the dust: nothing at first, then the wind takes it aside and he is there.
            return new Film.View(behind.add(shake(t, .004f)), new Vec3(rest.x + 2.4, 1.6, rest.z + 12), 52, 0);
        }
        // The last move: round to his shoulder, him close, the burning car beyond.
        float k = PursuitPath.ease((t - PursuitMoves.RISE) / (ULT_STAGE_END - PursuitMoves.RISE));
        Vec3 end = new Vec3(rest.x - 2.5, 1.15, rest.z - 2.4);
        Vec3 pos = behind.lerp(end, k);
        Vec3 head = rest.add(0, 1.45, 0);
        Vec3 aim = new Vec3(rest.x + 2.4, 1.6, rest.z + 12).lerp(head.lerp(wreck, .38), k);
        return new Film.View(pos, aim, Mth.lerp(k, 52, 44), 0);
    }
    /** True while the camera is inside his car (the engine is heard through the cabin then, the wind outside). */
    public static boolean inside(float t) {
        return t < ULT_COIL || t >= ULT_INSIDE && t < ULT_TURN;
    }
    /** Film times of the late beats (from the warp: the landing, the crash). */
    public static final float WHIP, CRASH_T, DUST = 648;
    static {
        WHIP = Math.max(574, realSince(LAND) + 7);
        CRASH_T = realSince(CRASH);
    }

    /** The opening: the eye of the mask filling the frame, then the long slow draw back to the driver at the wheel. */
    private static Film.View opening(float t, float tau) {
        Place place = panther(tau);
        PantherMotion.Pose pose = PursuitMoves.panther(tau, t);
        Matrix4f body = place.matrix();
        Vec3 eye = PursuitRig.eye(body, pose), back = PursuitRig.point(body, pose, PursuitRig.HEAD, -1.65f, -5.25f, 0);
        Vec3 face = eye.subtract(back).normalize();
        Car car = car(tau);
        float k = (float) Math.pow(PursuitPath.ease(t / ULT_MIRROR), 1.35);
        Vec3 near = eye.add(face.scale(.36)).add(0, .01, 0);
        Vec3 far = car.world(-.02, 1.06, .80);
        Vec3 pos = near.lerp(far, k);
        Vec3 chest = PursuitRig.chest(body, pose), hands = PursuitRig.palm(body, pose, 0).lerp(PursuitRig.palm(body, pose, 1), .5);
        Vec3 aim = k < .5f ? eye.lerp(PursuitRig.head(body, pose), k * 2) : PursuitRig.head(body, pose).lerp(chest.lerp(hands, .45), (k - .5f) * 2);
        return new Film.View(pos.add(drift(t, .002f + .006f * k)), aim, Mth.lerp(k, 34, 58), 0);
    }
    private static Vec3 pantherEye(float tau) {
        return PursuitRig.eye(panther(tau).matrix(), PursuitMoves.panther(tau, tau));
    }
    /** A camera inside the car, riding it, with a hand-held drift. */
    private static Film.View inCar(float tau, Vec3 pos, Vec3 aim, float fov, float roll) {
        Car car = car(tau);
        return new Film.View(car.world(pos).add(shake(tau, .004f)), car.world(aim), fov, roll);
    }
    private static Film.View worldView(Car car, Vec3 pos, Vec3 aim, float fov, float roll, float t) {
        return new Film.View(car.world(pos).add(shake(t, .003f)), aim.add(drift(t, .004f)), fov, roll);
    }
    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }
    /** A slow hand-held wander. */
    private static Vec3 drift(float t, double a) {
        return new Vec3(a * (Math.sin(t * .071) + .5 * Math.sin(t * .17 + 1)), a * (Math.sin(t * .053 + 2) + .5 * Math.sin(t * .13)), a * Math.sin(t * .061 + 4));
    }
    /** A small, quick tremor (the car's engine and road through the camera's mount). */
    private static Vec3 shake(float t, double a) {
        return new Vec3(a * Math.sin(t * 2.3), a * Math.sin(t * 3.1 + 1), a * Math.sin(t * 2.7 + 2));
    }

    // ------------------------------------------------------------------ kicks: each one from a real event, its own way
    private record Kick(float at, float yaw, float pitch, float roll, float fov, float decay) {}
    private static final Kick[] KICKS;
    static {
        java.util.List<Kick> k = new java.util.ArrayList<>();
        for (Shot s : SHOTS) {
            if (s.time() >= ULT_EXIT && s.time() < ULT_INSIDE - 4) continue;
            k.add(new Kick(s.time(), s.time() < ULT_EXIT ? .45f : 0, s.time() < ULT_EXIT ? -.25f : .5f, .2f, 0, 1.6f));
        }
        k.add(new Kick(ULT_SMASH, .7f, -.3f, .4f, 0, 2));
        k.add(new Kick(ULT_HOP + 10, 0, .6f, 0, 0, 2.5f));
        k.add(new Kick(ULT_TOUCH, 0, 2.2f, -.6f, 2, 3));
        k.add(new Kick(ULT_CLAW_R, -.5f, 1.2f, .8f, 1, 2.2f));
        k.add(new Kick(ULT_CLAW_L, .5f, 1.2f, -.8f, 1, 2.2f));
        k.add(new Kick(ULT_TEAR + 6, .3f, .8f, .5f, 0, 2));
        k.add(new Kick(ULT_ROOF_FREE, -.6f, -2.4f, 1.6f, 3, 3));
        k.add(new Kick(ULT_THROW + 3, 0, -1.6f, 0, 2, 2.5f));
        KICKS = k.toArray(new Kick[0]);
    }
    /** {yaw, pitch, roll, fov} kicks at film time t. */
    public static float[] kick(float t) {
        float tau = scene(t);
        float yaw = 0, pitch = 0, roll = 0, fov = 0;
        for (Kick k : KICKS) {
            float d = tau - k.at();
            if (d < 0 || d > k.decay() * 6) continue;
            float e = (float) Math.exp(-d / k.decay()) * Math.min(1, d / .5f);
            float w = (float) Math.cos(d * 1.3f);
            yaw += k.yaw() * e * w; pitch += k.pitch() * e * w; roll += k.roll() * e * w; fov += k.fov() * e;
        }
        // The release: a hard blow outward from him, then a low rolling shudder through the slow motion.
        float d = tau - BOOM;
        if (d >= 0 && d < 40) {
            float e = (float) Math.exp(-d / 2.5f);
            pitch -= 5 * e * (float) Math.cos(d * 1.6f); roll += 4 * e * (float) Math.sin(d * 1.9f); fov += 12 * e;
            float low = PursuitPath.window(d, 2, 30, 6);
            pitch += .5f * low * (float) Math.sin(t * .9f); roll += .4f * low * (float) Math.sin(t * .7f + 1);
        }
        // His landing, the crash.
        float l = tau - BOOM - LAND;
        if (l >= 0 && l < 14) { float e = (float) Math.exp(-l / 2.2f); pitch += 3 * e * (float) Math.cos(l * 1.5f); fov += 6 * e; }
        float c = tau - BOOM - CRASH;
        if (c >= 0 && c < 20) { float e = (float) Math.exp(-c / 3); pitch -= 3.5f * e * (float) Math.cos(c * 1.4f); yaw += 2.5f * e * (float) Math.sin(c * 1.8f); fov += 8 * e; }
        return new float[]{yaw, pitch, roll, fov};
    }
}
