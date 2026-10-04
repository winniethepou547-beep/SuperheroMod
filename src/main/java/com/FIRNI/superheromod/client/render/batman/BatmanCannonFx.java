package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * The wrist cannon as everyone sees it: the gauntlets opening into the WayneTech emitters, his body's stance and both
 * arms laid on his aim while it fires, the shots (flashes, tracers, impacts, the light they throw), cooling and closing.
 */
public final class BatmanCannonFx {
    private BatmanCannonFx() {}

    /** Effect kinds FX_CANNON_FIRST..FX_CANNON_LAST from the server. */
    static void receive(BatmanFxPacket p) {}
    /** The CANNON move's pose at its clock t over the base pose (BatmanMotion.sample). */
    static Pose pose(Pose base, float t, float time) { return base; }
    /** After his look is spread through the body: both arms laid on the aim (head yaw/pitch as the model has them). */
    static void arms(Pose pose, BatmanClient.State s, float t, float headYaw, float headPitch) {}
    /** How far the gauntlets are open for this Batman now, 0..1 (0 = plain gauntlets). */
    static float deployed(BatmanClient.State s, int action, float t) { return 0; }
    /** The opened emitter assembly on one forearm, in BatmanBody's forearm frame (the forearm along +y, px units /16). */
    static void gauntlet(PoseStack p, MultiBufferSource b, int light, int side, float amount) {}
}
