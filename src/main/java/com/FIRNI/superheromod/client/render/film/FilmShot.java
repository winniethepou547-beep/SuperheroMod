package com.FIRNI.superheromod.client.render.film;

import net.minecraft.world.phys.Vec3;

/**
 * One camera move, in the film's stage space (x right, y up, z forward from the stage origin).
 * Position and aim each follow a Catmull-Rom spline through their control points, so the
 * camera glides through every point with no corners. Shots cut into each other; whipIn adds
 * a whip-pan smear at the cut.
 */
public record FilmShot(float start, float end, Vec3[] path, Vec3[] look, float fovA, float fovB,
                       float rollA, float rollB, float shakeA, float shakeB, boolean whipIn) {
    public static Builder of(float start, float end) { return new Builder(start, end); }

    public Vec3 position(float k) { return spline(path, k); }
    public Vec3 aim(float k) { return spline(look, k); }

    /** Uniform Catmull-Rom through all points, end points held. */
    static Vec3 spline(Vec3[] p, float k) {
        if (p.length == 1) return p[0];
        float scaled = Math.max(0, Math.min(1, k)) * (p.length - 1);
        int i = Math.min(p.length - 2, (int) scaled);
        float t = scaled - i;
        Vec3 p0 = p[Math.max(0, i - 1)], p1 = p[i], p2 = p[i + 1], p3 = p[Math.min(p.length - 1, i + 2)];
        float t2 = t * t, t3 = t2 * t;
        return p1.scale(2)
                .add(p2.subtract(p0).scale(t))
                .add(p0.scale(2).subtract(p1.scale(5)).add(p2.scale(4)).subtract(p3).scale(t2))
                .add(p1.scale(3).subtract(p0).subtract(p2.scale(3)).add(p3).scale(t3))
                .scale(.5);
    }

    public static final class Builder {
        private final float start, end;
        private Vec3[] path = {Vec3.ZERO}, look = {new Vec3(0, 1.6, 1)};
        private float fovA = 60, fovB = 60, rollA, rollB, shakeA, shakeB;
        private boolean whip;
        private Builder(float start, float end) { this.start = start; this.end = end; }
        public Builder path(Vec3... points) { path = points; return this; }
        public Builder look(Vec3... points) { look = points; return this; }
        public Builder fov(float a, float b) { fovA = a; fovB = b; return this; }
        public Builder roll(float a, float b) { rollA = a; rollB = b; return this; }
        public Builder shake(float a, float b) { shakeA = a; shakeB = b; return this; }
        public Builder whip() { whip = true; return this; }
        public Builder whip(boolean on) { whip = on; return this; }
        public FilmShot build() { return new FilmShot(start, end, path, look, fovA, fovB, rollA, rollB, shakeA, shakeB, whip); }
    }
}
