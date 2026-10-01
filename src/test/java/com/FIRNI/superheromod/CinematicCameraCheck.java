package com.FIRNI.superheromod;

import com.FIRNI.superheromod.core.cinematic.*;
import net.minecraft.world.phys.Vec3;

final class CinematicCameraCheck {
    static void run() {
        var def = CinematicDefinition.builder("camera_test")
                .shot(Shot.of(10).at(0, 0, 0).breath(0).fov(40).roll(170)
                        .fog(2, 8).fogColor(0xff0000).build())
                .shot(Shot.of(10).at(10, 0, 0).breath(0).transition(.2f).fov(80).roll(-170)
                        .fog(6, 16).fogColor(0x0000ff).build())
                .shot(Shot.of(1).at(20, 0, 0).breath(0).transition(2).build())
                .shot(Shot.of(5).at(30, 0, 0).breath(0).transition(2).build())
                .shot(Shot.of(5).at(40, 0, 0).breath(0).cut().build()).build();
        var stage = StageFrame.of(Vec3.ZERO, new Vec3(0, 0, 1), 10);
        CinematicCameraSampler.ActorSource actors = tick ->
                new CinematicCameraSampler.Actors(Vec3.ZERO, new Vec3(tick, 0, 0));
        var middle = CinematicCameraSampler.sample(def, stage, 12, actors);
        near(middle.position().distanceTo(stage.toWorldSpan(new Vec3(5, 0, 0))), 0);
        near(middle.look().x, 11); // outgoing target sampled at tick 10, incoming at 12
        near(middle.fov(), 60); near(middle.roll(), 180); // shortest roll arc
        near(middle.fog().near(), 4); near(middle.fog().far(), 12);
        near(middle.fog().r(), .5); near(middle.fog().b(), .5);
        var boundary = CinematicCameraSampler.sample(def, stage, 10, actors);
        near(boundary.fov(), 40); near(boundary.look().x, 10);
        // A transition longer than its shot must finish before the next one starts.
        var shortEnd = CinematicCameraSampler.sample(def, stage, 21, actors);
        near(shortEnd.position().distanceTo(stage.toWorldSpan(new Vec3(20, 0, 0))), 0);
        var cut = CinematicCameraSampler.sample(def, stage, 26, actors);
        near(cut.position().distanceTo(stage.toWorldSpan(new Vec3(40, 0, 0))), 0);
        if (cut.fog().active() || cut.fog().r() != -1) throw new AssertionError("Stale fog after cut");
        for (int fps : new int[]{30, 60, 144}) {
            for (int frame = 0; frame < fps * 2; frame++)
                CinematicCameraSampler.sample(def, stage, frame * 20f / fps, actors);
            if (!middle.equals(CinematicCameraSampler.sample(def, stage, 12, actors)))
                throw new AssertionError("Camera depends on playback history");
        }
        for (float t : new float[]{10, 14, 20, 21, 26}) {
            if (t == 26) continue; // intentional cut
            var a = CinematicCameraSampler.sample(def, stage, t - .0001f, actors);
            var b = CinematicCameraSampler.sample(def, stage, t, actors);
            if (a.position().distanceTo(b.position()) > .005) throw new AssertionError("Camera boundary jump");
        }
        try {
            CinematicCameraSampler.sample(def, stage, Float.NaN, actors);
            throw new AssertionError("Invalid timeline accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Camera checks: boundary tracking, short transitions, lens, fog, seek and sampling history OK");
    }
    private static void near(double actual, double expected) {
        if (Math.abs(actual - expected) > 1e-5) throw new AssertionError(actual + " != " + expected);
    }
}
