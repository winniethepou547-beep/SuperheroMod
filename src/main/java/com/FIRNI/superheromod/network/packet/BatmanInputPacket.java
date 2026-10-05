package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Something Batman's own client did that the server must know (glide on/off, a roll and its direction, the gadget wheel, the grapnel). */
public record BatmanInputPacket(int kind, int value, float amount) {
    public static void encode(BatmanInputPacket p, FriendlyByteBuf b) { b.writeByte(p.kind); b.writeVarInt(p.value); b.writeFloat(p.amount); }
    public static BatmanInputPacket decode(FriendlyByteBuf b) { return new BatmanInputPacket(b.readByte(), b.readVarInt(), b.readFloat()); }
    public static void handle(BatmanInputPacket p, Supplier<NetworkEvent.Context> c) {
        var sender = c.get().getSender();
        c.get().enqueueWork(() -> { if (sender != null) com.FIRNI.superheromod.heroes.batman.BatmanController.input(sender, p.kind(), p.value(), p.amount()); });
        c.get().setPacketHandled(true);
    }
}
