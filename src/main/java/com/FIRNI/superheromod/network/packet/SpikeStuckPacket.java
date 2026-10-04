package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Server -> the player with Magneto's spike in them: still stuck, how far out it is (0..1), and when it came out the immunity (ticks). */
public record SpikeStuckPacket(boolean stuck, float progress, int immune) {
    public static void encode(SpikeStuckPacket p, FriendlyByteBuf b) { b.writeBoolean(p.stuck); b.writeFloat(p.progress); b.writeVarInt(p.immune); }
    public static SpikeStuckPacket decode(FriendlyByteBuf b) { return new SpikeStuckPacket(b.readBoolean(), b.readFloat(), b.readVarInt()); }
    public static void handle(SpikeStuckPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.FIRNI.superheromod.client.render.magneto.SpikeClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
