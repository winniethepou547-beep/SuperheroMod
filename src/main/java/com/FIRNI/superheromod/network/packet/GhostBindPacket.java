package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Server -> chained player: you are bound, and how close you are to breaking free (0..1). */
public record GhostBindPacket(boolean bound, float progress) {
    public static void encode(GhostBindPacket p, FriendlyByteBuf b) { b.writeBoolean(p.bound); b.writeFloat(p.progress); }
    public static GhostBindPacket decode(FriendlyByteBuf b) { return new GhostBindPacket(b.readBoolean(), b.readFloat()); }
    public static void handle(GhostBindPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.FIRNI.superheromod.client.render.ghost.GhostBindClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
