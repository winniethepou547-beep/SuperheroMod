package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.core.matchmaking.MatchMode;
import com.FIRNI.superheromod.core.matchmaking.QueueManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S: oyuncu bir mod sectiginde (1v1/2v2) sunucuya bildirir, sunucu kuyruga alir. */
public class ModeSelectedPacket {

    private final MatchMode mode;

    public ModeSelectedPacket(MatchMode mode) {
        this.mode = mode;
    }

    public static void encode(ModeSelectedPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.mode);
    }

    public static ModeSelectedPacket decode(FriendlyByteBuf buf) {
        return new ModeSelectedPacket(buf.readEnum(MatchMode.class));
    }

    public static void handle(ModeSelectedPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                QueueManager.enqueue(player, packet.mode);
            }
        });
        ctx.setPacketHandled(true);
    }
}
