package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Chained player -> server: presses of the break-free key since the last packet. */
public record GhostEscapePacket(int presses) {
    public static void encode(GhostEscapePacket p, FriendlyByteBuf b) { b.writeByte(p.presses); }
    public static GhostEscapePacket decode(FriendlyByteBuf b) { return new GhostEscapePacket(b.readUnsignedByte()); }
    public static void handle(GhostEscapePacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> {
            var sender = c.get().getSender();
            if (sender != null) com.FIRNI.superheromod.heroes.ghostrider.GhostChainController.struggle(sender, p.presses);
        });
        c.get().setPacketHandled(true);
    }
}
