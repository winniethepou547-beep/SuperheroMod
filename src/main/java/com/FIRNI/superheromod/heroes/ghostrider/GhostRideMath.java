package com.FIRNI.superheromod.heroes.ghostrider;

import net.minecraft.world.phys.Vec3;

/** Shared tuning, separated from entity state so movement bounds can be checked offline. */
public final class GhostRideMath {
    public static final double MAX_SPEED=3.0;
    /** Air control: yaw acceleration and per-tick damping (deg/tick), nose-down bias in degrees. */
    public static final float AIR_SPIN_ACCEL=1.4f, AIR_SPIN_DAMPING=.9f, AIR_NOSE_DROP=9;
    /** Fraction of the heading the travel path catches up per tick at top speed (lower slides more). */
    public static final float HIGH_SPEED_GRIP=.2f;
    public static double accelerate(double speed,float throttle) {
        return speed+((throttle>0?MAX_SPEED:throttle<0?-.3:.06)-speed)*(throttle>0?.025:.12);
    }
    public static float turnRate(double speed) {return (float)(7.5-Math.min(1,Math.abs(speed)/MAX_SPEED)*1.5);}
    public static Vec3 wheelieLaunch(float yaw,float angle,double speed) {
        double pitch=Math.toRadians(Math.max(0,Math.min(45,angle))),heading=Math.toRadians(yaw);
        return new Vec3(-Math.sin(heading)*Math.cos(pitch),Math.sin(pitch),Math.cos(heading)*Math.cos(pitch)).scale(Math.max(1.25,Math.min(2.8,Math.abs(speed)+.55)));
    }
    public static float chainDamage(int combo,int heat) {return (3+combo)*(heat>=4?1.6f:1)+heat*.45f;}
    public static float punchDamage(int heat) {return 6*(heat>=4?1.4f:1)+heat*.3f;}
    public static Vec3 pullDestination(Vec3 bikePosition,float yaw) {
        return bikePosition.add(Vec3.directionFromRotation(0,yaw).scale(12));
    }
}
