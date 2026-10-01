package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.core.cinematic.CinematicDirector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record CinematicReadyPacket(UUID session, boolean success) {
    public static void encode(CinematicReadyPacket p, FriendlyByteBuf b) { b.writeUUID(p.session); b.writeBoolean(p.success); }
    public static CinematicReadyPacket decode(FriendlyByteBuf b) { return new CinematicReadyPacket(b.readUUID(), b.readBoolean()); }
    public static void handle(CinematicReadyPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var sender = context.getSender();
            if (sender != null) CinematicDirector.ready(sender.getUUID(), p.session, p.success);
        });
        context.setPacketHandled(true);
    }
}
