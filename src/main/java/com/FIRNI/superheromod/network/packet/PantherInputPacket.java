package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Something Black Panther's own client did that the server must know about (his double jump). */
public record PantherInputPacket(int kind) {
    public static void encode(PantherInputPacket p, FriendlyByteBuf b) { b.writeByte(p.kind); }
    public static PantherInputPacket decode(FriendlyByteBuf b) { return new PantherInputPacket(b.readByte()); }
    public static void handle(PantherInputPacket p, Supplier<NetworkEvent.Context> c) {
        var sender = c.get().getSender();
        c.get().enqueueWork(() -> { if (sender != null) com.FIRNI.superheromod.heroes.panther.PantherController.input(sender, p.kind()); });
        c.get().setPacketHandled(true);
    }
}
