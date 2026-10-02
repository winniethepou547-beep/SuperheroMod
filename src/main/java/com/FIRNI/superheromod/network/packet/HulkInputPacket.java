package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Hulk's space bar: pressed (start charging the leap) or let go (leap). The server times the charge itself. */
public record HulkInputPacket(boolean down) {
    public static void encode(HulkInputPacket p, FriendlyByteBuf b) { b.writeBoolean(p.down); }
    public static HulkInputPacket decode(FriendlyByteBuf b) { return new HulkInputPacket(b.readBoolean()); }
    public static void handle(HulkInputPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> {
            var player = c.get().getSender();
            if (player != null) com.FIRNI.superheromod.heroes.hulk.HulkController.jump(player, p.down());
        });
        c.get().setPacketHandled(true);
    }
}
