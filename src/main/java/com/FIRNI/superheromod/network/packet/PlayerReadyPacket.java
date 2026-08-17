package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.core.matchmaking.ReadyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayerReadyPacket {

    public static void encode(PlayerReadyPacket pkt, FriendlyByteBuf buf) {
    }

    public static PlayerReadyPacket decode(FriendlyByteBuf buf) {
        return new PlayerReadyPacket();
    }

    public static void handle(PlayerReadyPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                ReadyManager.setReady(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
