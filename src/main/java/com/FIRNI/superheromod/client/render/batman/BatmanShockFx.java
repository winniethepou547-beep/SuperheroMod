package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * The electric gauntlets as everyone sees them: the knuckles locking on and the clap, the boxer's stance and heavy
 * blows, the living cyan electricity and the light it throws, the target's shock, his energy bar.
 */
public final class BatmanShockFx {
    private BatmanShockFx() {}

    /** Effect kinds FX_SHOCK_FIRST..FX_SHOCK_LAST from the server. */
    static void receive(BatmanFxPacket p) {}
    /** SHOCK_EQUIP / SHOCK_UNEQUIP / SHOCK_PUNCH poses at clock t over the base (BatmanMotion.sample; combo = the blow). */
    static Pose pose(int action, Pose base, float t, int combo, float time) { return base; }
    /** The stance while they are worn, over the plain stance (BatmanLayer, before locomotion). */
    static Pose stance(Pose base, float time) { return base; }
    /** How much of the gauntlets shows on his hands now, 0..1 (BatmanLayer copies it into BatmanBody.SHOCK). */
    static float worn(BatmanClient.State s, int action, float t) { return 0; }
    /** The gauntlet on one hand, in BatmanBody.hand's frame (fist centre near (0, 1.5, 0) px; side 0 right), worn 0..1, charge 0..1. */
    static void knuckles(PoseStack p, MultiBufferSource b, int light, int side, float worn, float energy) {}
}
