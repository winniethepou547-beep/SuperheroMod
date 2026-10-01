package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;

/** target: entity id the chain is bound to, or -1. */
public record GhostChainPacket(int player, int mode, int age, int combo, int heat, Vec3 tip, int target) {
    public static void encode(GhostChainPacket p, FriendlyByteBuf b) {
        b.writeInt(p.player); b.writeByte(p.mode); b.writeInt(p.age); b.writeByte(p.combo); b.writeByte(p.heat);
        b.writeDouble(p.tip.x); b.writeDouble(p.tip.y); b.writeDouble(p.tip.z);
        b.writeInt(p.target);
    }
    public static GhostChainPacket decode(FriendlyByteBuf b) {
        return new GhostChainPacket(b.readInt(), b.readUnsignedByte(), b.readInt(), b.readUnsignedByte(), b.readUnsignedByte(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readInt());
    }
    public static void handle(GhostChainPacket p, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.FIRNI.superheromod.client.render.ghost.GhostChainRenderer.receive(p)));
        context.get().setPacketHandled(true);
    }
}
