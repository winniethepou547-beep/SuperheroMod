package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Something Magneto's own client did that the server must know about (jumping twice: into or out of flight). */
public record MagnetoInputPacket(int kind) {
    public static void encode(MagnetoInputPacket p, FriendlyByteBuf b) { b.writeByte(p.kind); }
    public static MagnetoInputPacket decode(FriendlyByteBuf b) { return new MagnetoInputPacket(b.readByte()); }
    public static void handle(MagnetoInputPacket p, Supplier<NetworkEvent.Context> c) {
        var sender = c.get().getSender();
        c.get().enqueueWork(() -> { if (sender != null) com.FIRNI.superheromod.heroes.magneto.MagnetoController.input(sender, p.kind()); });
        c.get().setPacketHandled(true);
    }
}
