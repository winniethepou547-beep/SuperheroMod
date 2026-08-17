package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: oyuncu mac haritasina isinlandi, istemciye "3 2 1 FIGHT" gecis sayacini baslat der. */
public class MatchStartingPacket {

    public static void encode(MatchStartingPacket packet, FriendlyByteBuf buf) {
    }

    public static MatchStartingPacket decode(FriendlyByteBuf buf) {
        return new MatchStartingPacket();
    }

    public static void handle(MatchStartingPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(ClientPacketHandler::startFightCountdown);
        ctx.setPacketHandled(true);
    }
}
