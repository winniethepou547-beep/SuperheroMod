package com.FIRNI.superheromod.heroes.batman;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * The remote takedown's paths, pure functions shared by the server and every client (so the Batmobile, its guns and
 * the effects line up everywhere without a packet per tick).
 * <p>
 * Batman: he dashes along d and meets them at T; he vaults over them in a front flip with a half twist and lands
 * BEHIND blocks past them, facing back along -d, his chest at their back. They are turned to face -d (their "front").
 * <p>
 * The Batmobile (its own clock c, ticks from its call): it comes in fast from far behind, braking (IN ticks), sweeps
 * round the front of them in a wide crescent at RADIUS, sliding sideways with its nose turned in (ARC ticks; its guns
 * fire FIRE_FROM..FIRE_TO), and boosts away along the end of the arc (gone at GONE).
 */
public final class TakedownPath {
    private TakedownPath() {}

    // ------------------------------------------------------------------ Batman
    /** How far past their feet he lands (along the dash). */
    public static final double BEHIND = .62;
    static float clamp(float x) { return Mth.clamp(x, 0, 1); }
    public static float ease(float x) { x = clamp(x); return x * x * (3 - 2 * x); }
    /** 0..1 through a span. */
    public static float k(float t, float a, float b) { return ease((t - a) / (b - a)); }
    public static Vec3 behind(Vec3 feet, Vec3 d) { return feet.add(d.scale(BEHIND)); }
    /** His feet during the flip, t ticks after the touch, from where he was (from) over them (feet, height h) to behind them. */
    public static Vec3 flip(Vec3 from, Vec3 feet, Vec3 d, double height, float t) {
        float u = clamp(t / TD_FLIP);
        Vec3 to = behind(feet, d);
        Vec3 at = from.lerp(to, ease(u));
        // Up over their head and down behind them.
        double top = feet.y + height + .35 - (from.y + to.y) / 2;
        return at.add(0, Math.max(.6, top) * Mth.sin(Mth.PI * u), 0);
    }
    /** How far his body has turned from facing d to facing -d (0..1): the half twist in the flip. */
    public static float twist(float t) { return k(t, TD_FLIP * .2f, TD_FLIP * .85f); }

    // ------------------------------------------------------------------ the Batmobile
    public static final double RADIUS = 7.5, RUN_IN = 36;
    public static final int IN = 16, ARC = 24, OUT = 22, GONE = IN + ARC + OUT, FIRE_FROM = IN + 3, FIRE_TO = IN + ARC - 2;
    /** The arc: from this angle round to that one (radians, in the plane of side and front; 90° = straight in front of them). */
    static final double FROM = Math.toRadians(205), TO = Math.toRadians(-25);
    /** How far in it turns its nose at the full slide (radians). */
    static final double DRIFT = Math.toRadians(38);
    /** The share of its run-in that is steady speed (the rest is braking): it enters the arc at about the arc's own speed. */
    static final double STEADY = .62;

    /** One run round them: their feet, their front (unit, flat), which way round (+1 / -1). */
    public record Run(Vec3 centre, Vec3 front, int side) {
        Vec3 sideways() { return new Vec3(-front.z, 0, front.x).scale(side); }
        /** On the circle at angle a. */
        Vec3 ring(double a) { return centre.add(sideways().scale(Math.cos(a) * RADIUS)).add(front.scale(Math.sin(a) * RADIUS)); }
        /** The way it travels on the circle at angle a (the angle runs down from FROM to TO). */
        Vec3 tangent(double a) { return sideways().scale(Math.sin(a)).subtract(front.scale(Math.cos(a))).normalize(); }

        /** Where it is (its middle on the ground, at the height of their feet) at its clock c. */
        public Vec3 pos(float c) {
            if (c < IN) {
                double u = clamp(c / IN);
                double s = STEADY * u + (1 - STEADY) * (1 - (1 - u) * (1 - u));
                Vec3 entry = ring(FROM);
                return entry.subtract(tangent(FROM).scale(RUN_IN * (1 - s)));
            }
            if (c < IN + ARC) return ring(Mth.lerp((c - IN) / ARC, FROM, TO));
            double e = c - IN - ARC, speed = RADIUS * Math.abs(TO - FROM) / ARC;
            return ring(TO).add(tangent(TO).scale(speed * e + .09 * e * e));
        }
        /** Which way it travels at its clock c (unit, flat). */
        public Vec3 heading(float c) {
            if (c < IN) return tangent(FROM);
            if (c < IN + ARC) return tangent(Mth.lerp((c - IN) / ARC, FROM, TO));
            return tangent(TO);
        }
        /** Which way its nose points: along its way, turned in toward them through the slide. */
        public Vec3 nose(float c) {
            Vec3 h = heading(c);
            float slide = slide(c);
            if (slide <= 0) return h;
            Vec3 in = centre.subtract(pos(c));
            in = new Vec3(in.x, 0, in.z).normalize();
            return h.scale(Math.cos(DRIFT * slide)).add(in.scale(Math.sin(DRIFT * slide))).normalize();
        }
        /** Its facing as a Minecraft yaw (degrees). */
        public float yaw(float c) { Vec3 n = nose(c); return (float) Math.toDegrees(Math.atan2(-n.x, n.z)); }
    }
    /** 0..1: how hard it is sliding (into the arc, through it, straightening out of it). */
    public static float slide(float c) { return Math.min(k(c, IN - 3, IN + 3), 1 - k(c, IN + ARC - 4, IN + ARC + 2)); }
    /** 0..1: the afterburner (out of the arc and away). */
    public static float boost(float c) { return k(c, IN + ARC - 1, IN + ARC + 3); }
    /** 0..1: how hard it is driving (the run in, the boost; easing while it slides). */
    public static float drive(float c) { return Math.max(1 - k(c, IN - 6, IN + 2), boost(c)) * .7f + .3f * slide(c); }
    /** A round leaves its guns at this tick of its clock (one a tick, the two guns taking turns). */
    public static boolean fires(int c) { return c >= FIRE_FROM && c < FIRE_TO; }
    /** Which gun (0 its right, 1 its left) fires at clock c, and where its muzzle is in the car's own space (+z the nose, +x its left). */
    public static int gun(int c) { return c & 1; }
    public static final float GUN_X = .78f, GUN_Y = 1.3f, GUN_Z = 2.3f;
    /** Which way round it goes for this Batman and this target (the same on every side of the network). */
    public static int side(int batman, int target) { return ((batman ^ target) & 1) == 0 ? 1 : -1; }
    /** A round's flight from the muzzle to the body (ticks). */
    public static final float ROUND_TICKS = 2;
    /** A pseudo-random 0..1 from a seed (the same everywhere). */
    public static double hash(double n) { double x = Math.sin(n * 12.9898) * 43758.5453; return x - Math.floor(x); }
}
