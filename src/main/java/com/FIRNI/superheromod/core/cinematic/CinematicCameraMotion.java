package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;

/** Continuous camera offsets: identical at a timestamp, independent of render FPS. */
public final class CinematicCameraMotion {
    private CinematicCameraMotion() {}

    public static Vec3 offset(float tick, float shake, float breath) {
        double seconds = tick / 20.0;
        double s = shake * .045;
        double b = breath * .022;
        return new Vec3(
                wave(seconds, 7.3, 11.7, .2)*s + wave(seconds, .113, .053, 0)*2*b,
                wave(seconds, 8.1, 13.1, 2.1)*s + wave(seconds, .084, .154, 1)*1.6*b,
                wave(seconds, 6.7, 10.3, 4.3)*s + wave(seconds, .065, .138, 2)*2*b);
    }

    private static double wave(double t, double a, double b, double phase) {
        return (Math.sin(t*a*Math.PI*2+phase) + Math.sin(t*b*Math.PI*2+phase*.7))*.5;
    }
}
