package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * The sonic trap as everyone sees and hears it: the remote in his hand and the press of its red button, the two
 * emitters rising out of the ground, turning to the target and blasting it, the target's shaking view and swamped
 * hearing, the emitters breaking or sinking back into the ground.
 */
public final class BatmanSonicFx {
    private BatmanSonicFx() {}

    /** Effect kinds FX_SONIC_FIRST..FX_SONIC_LAST from the server. */
    static void receive(BatmanFxPacket p) {}
    /** The SONIC move's pose at its clock t over the base pose (BatmanMotion.sample). */
    static Pose pose(Pose base, float t, float time) { return base; }
    /** The remote's button press for this Batman now (0..1), or -1 when the remote is not in his left hand. */
    static float remote(BatmanClient.State s, int action, float t) { return -1; }
    /** The remote in the hand (BatmanBody.held's hand frame; side s = -1 right, 1 left; press 0..1). */
    static void drawRemote(PoseStack p, VertexConsumer v, int light, int s, float press) {}
}
