package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.heroes.sandman.ColossusCrystal;
import org.joml.Matrix4f;

/**
 * What one colossus looked like on the last frame: where his feet were, his bones, each clump's state and
 * his crystal clusters (foot space). The body pass fills it, the effects pass and the crystal events read it.
 * A colossus that ends keeps its last copy as a RUIN that plays the collapse.
 */
final class ColossusVisual {
    /** Formation progress at which each crystal starts to push out (chest first, head last). */
    static final float[] CRYSTAL_START = {.880f, .895f, .905f, .915f, .930f};
    static final int CRYSTALS = ColossusCrystal.values().length;

    final ColossusBody.Skeleton skeleton = new ColossusBody.Skeleton();
    final ColossusBody.Frame frame = new ColossusBody.Frame();
    /** Each crystal cluster's matrix in foot space (turn, spin, swell and radius included). */
    final Matrix4f[] cluster = new Matrix4f[CRYSTALS];
    final int[] states = new int[CRYSTALS];

    /** Feet, absolute world position. */
    double x, y, z;
    float yaw;
    /** The dune follows the torso's turn a little late. */
    float massYaw, massAge = -1;
    float progress = 1;
    int light;
    /** Brightness of the world light here, 0.25 .. 1, for the dust (which is drawn unlit). */
    float lightK = 1;
    /** Game time the collapse began (ruins only), and the render clock of the last frame it was drawn. */
    double collapseStart = -1;
    float lastSeen = -1e9f;
    /** His own camera is inside his head (first person). */
    boolean ownHead;
    boolean drawn;

    ColossusVisual() {
        for (int c = 0; c < CRYSTALS; c++) cluster[c] = new Matrix4f();
    }

    /** Ticks since crystal c began to grow (large once he stands); negative before it appears. */
    float crystalAge(int c) {
        if (progress >= 1) return 1000;
        return (progress - CRYSTAL_START[c]) * com.FIRNI.superheromod.heroes.sandman.ColossusForm.FORM_TICKS;
    }
}
