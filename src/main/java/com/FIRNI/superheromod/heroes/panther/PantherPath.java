package com.FIRNI.superheromod.heroes.panther;

import net.minecraft.world.phys.Vec3;

/**
 * Where Black Panther's body is through his moves, as pure functions of the action's clock, so the
 * server (which hits with it) and his own client (which steers his body along it) agree exactly.
 * The points a move needs are decided on the server when it starts and sent with his state:
 *   from  = where he left the ground, dir = the way he went (flat, unit);
 *   mid   = the target's feet at contact (the flip passes over it);
 *   to    = the point in the air behind the target where the flip ends and he kicks;
 *   land  = the ground where he comes down.
 */
public final class PantherPath {
    private PantherPath() {}

    private static double ease(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    /** The pounce: low and flat along dir, a small hop in the middle so it reads as a leap, not a glide. */
    public static Vec3 pounce(Vec3 from, Vec3 dir, double speed, double reach, double t) {
        double along = Math.min(reach, speed * Math.max(0, t));
        double k = reach <= 0 ? 0 : along / reach;
        return from.add(dir.scale(along)).add(0, .55 * Math.sin(Math.PI * Math.min(1, k * 1.4)) * Math.min(1, reach / 5), 0);
    }

    /**
     * The flip over the target: from the contact point up over their head (apex FLIP_HEIGHT above it)
     * and down to the point behind them. Quicker off the mark, a touch slower over the top so the eye
     * can follow the body passing over.
     */
    public static Vec3 flip(Vec3 contact, Vec3 apex, Vec3 to, double k) {
        k = Math.max(0, Math.min(1, k));
        // Fast off the mark and into the end, slower over the top (speed 1 + .35 cos 2πk).
        double s = k + .35 * Math.sin(2 * Math.PI * k) / (2 * Math.PI);
        Vec3 control = apex.scale(2).subtract(contact.add(to).scale(.5));
        double a = (1 - s) * (1 - s), b = 2 * s * (1 - s), c = s * s;
        return contact.scale(a).add(control.scale(b)).add(to.scale(c));
    }
    /** The highest point of the flip: above the target's head. */
    public static Vec3 apex(Vec3 contact, Vec3 targetFeet, double targetHeight, double over, Vec3 to) {
        Vec3 mid = contact.add(to).scale(.5);
        return new Vec3(mid.x * .3 + targetFeet.x * .7, targetFeet.y + targetHeight + over, mid.z * .3 + targetFeet.z * .7);
    }

    /** The kick: he hangs at the end of the flip, drifting a little into the kick and back out of it. */
    public static Vec3 kick(Vec3 to, Vec3 dir, double t, int hit, int ticks) {
        double into = ease(t / hit) * (1 - ease((t - hit) / (ticks - hit)));
        double sink = .12 * Math.max(0, t) / ticks;
        // dir points the way he pounced: the target is behind him now, so into the kick is -dir.
        return to.add(dir.scale(-.35 * into)).add(0, -sink, 0);
    }

    /** Coming down from the kick to the ground, carried a little further on, knees taking it. */
    public static Vec3 land(Vec3 to, Vec3 dir, Vec3 ground, double t, int ticks, int kickTicks, int kickHit) {
        Vec3 start = kick(to, dir, kickTicks, kickHit, kickTicks);
        double fall = Math.max(0, Math.min(1, t / (ticks * .45)));
        Vec3 end = ground.add(dir.scale(.6));
        double y = start.y + (end.y - start.y) * fall * fall;
        double h = ease(Math.min(1, t / (ticks * .45)));
        return new Vec3(start.x + (end.x - start.x) * h, y, start.z + (end.z - start.z) * h);
    }

    /** The spinning triple kick: up and forward in one arc, the spin carrying him on. */
    public static Vec3 spin(Vec3 from, Vec3 dir, double distance, double height, double t, int ticks) {
        double k = Math.max(0, Math.min(1, t / ticks));
        double forward = ease(k * .85 + .15 * k * k);
        double up = 4 * k * (1 - k);
        // Up quickly, a long hang through the middle kick, down at the end.
        up = Math.pow(up, .7);
        return from.add(dir.scale(distance * forward)).add(0, height * up, 0);
    }

    /**
     * Body yaw offset through the spin (degrees; +90 turns his right side to the front): it starts turned a
     * little away and goes round one and a half turns, so the right leg sweeps through the front at the
     * first kick, the left half a turn later, the right again at the third.
     */
    public static float spinTurn(double t, int ticks, float total) {
        double k = Math.max(0, Math.min(1, t / ticks));
        // Steady through the kicks (the momentum is continuous), easing out at the very end.
        double s = k < .85 ? k / .85 * .92 : .92 + .08 * ease((k - .85) / .15);
        return (float) (-45 + total * s);
    }
}
