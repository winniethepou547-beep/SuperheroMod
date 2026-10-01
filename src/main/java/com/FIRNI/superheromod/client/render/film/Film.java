package com.FIRNI.superheromod.client.render.film;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/**
 * A film every participant watches from the same synced clock. It is cut into segments; each
 * segment is filmed either in the game world (shots in stage space around origin/forward) or
 * on a virtual stage the film draws itself, independent of the world, terrain or place.
 */
public interface Film {
    /** Draws a virtual stage. Positions are in stage space; the camera sits in the same space. */
    interface Scene {
        /** Opaque background gradient, top and bottom colour (RGB). */
        int skyTop();
        int skyBottom();
        void render(FilmContext ctx);
        /** Procedural backdrop for this moment (local ticks, film ticks), or null for the gradient. */
        default FilmBackdrop.Params backdrop(float local, float time) { return null; }
    }
    /** One segment: its shots, the scene (null = game world) and the colour it fades through on entry. */
    record Segment(float start, float end, FilmShot[] shots, Scene scene, int fadeColor, float fadeTicks) {}
    record Cue(float time, SoundEvent sound, float volume, float pitch) {}

    Vec3 origin();
    Vec3 forward();
    float duration();
    Segment[] segments();
    /** Current film time in ticks (monotonic, frame-interpolated). */
    float time(float partial);
    boolean active();
    default float[] impacts() { return new float[0]; }
    default Cue[] cues() { return new Cue[0]; }
    /** ARGB wash over the frame at time t. */
    default int grade(float t) { return 0; }
    /** The film's own on-screen text (drawn over the picture, under the impact frames). */
    default void overlay(GuiGraphics g, float t, int width, int height) {}
    default float blendIn() { return 6; }
    default float blendOut() { return 10; }

    default Vec3 right() { return forward().cross(new Vec3(0, 1, 0)).normalize(); }
    default Vec3 toWorld(Vec3 local) {
        return origin().add(right().scale(local.x)).add(0, local.y, 0).add(forward().scale(local.z));
    }
}
