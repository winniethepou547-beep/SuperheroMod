package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShowVsScreenPacket {

    private final String playerName;
    private final String opponentName;
    private final String modeName;
    private final String playerRank;
    private final int playerRating;
    private final int playerWins;
    private final int playerLosses;
    private final String opponentRank;
    private final int opponentRating;
    private final int opponentWins;
    private final int opponentLosses;

    public ShowVsScreenPacket(String playerName, String opponentName, String modeName,
                              String playerRank, int playerRating, int playerWins, int playerLosses,
                              String opponentRank, int opponentRating, int opponentWins, int opponentLosses) {
        this.playerName = playerName;
        this.opponentName = opponentName;
        this.modeName = modeName;
        this.playerRank = playerRank;
        this.playerRating = playerRating;
        this.playerWins = playerWins;
        this.playerLosses = playerLosses;
        this.opponentRank = opponentRank;
        this.opponentRating = opponentRating;
        this.opponentWins = opponentWins;
        this.opponentLosses = opponentLosses;
    }

    public static void encode(ShowVsScreenPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.playerName);
        buf.writeUtf(pkt.opponentName);
        buf.writeUtf(pkt.modeName);
        buf.writeUtf(pkt.playerRank);
        buf.writeInt(pkt.playerRating);
        buf.writeInt(pkt.playerWins);
        buf.writeInt(pkt.playerLosses);
        buf.writeUtf(pkt.opponentRank);
        buf.writeInt(pkt.opponentRating);
        buf.writeInt(pkt.opponentWins);
        buf.writeInt(pkt.opponentLosses);
    }

    public static ShowVsScreenPacket decode(FriendlyByteBuf buf) {
        return new ShowVsScreenPacket(
                buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt()
        );
    }

    public static void handle(ShowVsScreenPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientPacketHandler.showVsScreen(
                pkt.playerName, pkt.opponentName, pkt.modeName,
                pkt.playerRank, pkt.playerRating, pkt.playerWins, pkt.playerLosses,
                pkt.opponentRank, pkt.opponentRating, pkt.opponentWins, pkt.opponentLosses
        ));
        ctx.setPacketHandled(true);
    }
}
