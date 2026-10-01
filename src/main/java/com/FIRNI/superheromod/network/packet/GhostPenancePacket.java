package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Penance Stare progress. Carries the stage (rider anchor, facing, victim start) so every
 * client can choreograph both actors per frame instead of following 20 Hz server snaps.
 */
public record GhostPenancePacket(int rider, int victim, int age, boolean active, float yaw, Vec3 anchor, Vec3 victimStart) {
    public static void encode(GhostPenancePacket p, FriendlyByteBuf b) {
        b.writeInt(p.rider); b.writeInt(p.victim); b.writeShort(p.age); b.writeBoolean(p.active); b.writeFloat(p.yaw);
        b.writeDouble(p.anchor.x); b.writeDouble(p.anchor.y); b.writeDouble(p.anchor.z);
        b.writeDouble(p.victimStart.x); b.writeDouble(p.victimStart.y); b.writeDouble(p.victimStart.z);
    }
    public static GhostPenancePacket decode(FriendlyByteBuf b) {
        return new GhostPenancePacket(b.readInt(), b.readInt(), b.readShort(), b.readBoolean(), b.readFloat(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()));
    }
    public static void handle(GhostPenancePacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.FIRNI.superheromod.client.render.ghost.PenanceClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
