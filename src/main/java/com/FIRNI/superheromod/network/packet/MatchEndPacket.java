package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: Mac bitti. Kazananin ismini ve bu oyuncunun kazanip kazanmadigini tasir.
 * Istemcide skorboardu kapatir ve mac-sonu mesajini gosterir.
 */
public class MatchEndPacket {

    private final String winnerName;
    private final boolean youWon;

    public MatchEndPacket(String winnerName, boolean youWon) {
        this.winnerName = winnerName;
        this.youWon = youWon;
    }

    public static void encode(MatchEndPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.winnerName, 64);
        buf.writeBoolean(packet.youWon);
    }

    public static MatchEndPacket decode(FriendlyByteBuf buf) {
        return new MatchEndPacket(buf.readUtf(64), buf.readBoolean());
    }

    public static void handle(MatchEndPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientPacketHandler.showMatchEnd(packet.winnerName, packet.youWon));
        ctx.setPacketHandled(true);
    }
}
