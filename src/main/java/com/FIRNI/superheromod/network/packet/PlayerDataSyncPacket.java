package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.ClientPlayerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PlayerDataSyncPacket {

    private final int gold;
    private final int level;
    private final int pveXp;
    private final int xpNeeded;
    private final int pvpRating;
    private final int peakRating;
    private final int wins;
    private final int losses;
    private final int kills;
    private final int deaths;
    private final int pvpXp;
    private final int arenaTokens;
    private final String rankName;
    private final String rankColor;

    public PlayerDataSyncPacket(int gold, int level, int pveXp, int xpNeeded,
                                int pvpRating, int peakRating, int wins, int losses,
                                int kills, int deaths, int pvpXp, int arenaTokens,
                                String rankName, String rankColor) {
        this.gold = gold;
        this.level = level;
        this.pveXp = pveXp;
        this.xpNeeded = xpNeeded;
        this.pvpRating = pvpRating;
        this.peakRating = peakRating;
        this.wins = wins;
        this.losses = losses;
        this.kills = kills;
        this.deaths = deaths;
        this.pvpXp = pvpXp;
        this.arenaTokens = arenaTokens;
        this.rankName = rankName;
        this.rankColor = rankColor;
    }

    public static void encode(PlayerDataSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.gold);
        buf.writeInt(pkt.level);
        buf.writeInt(pkt.pveXp);
        buf.writeInt(pkt.xpNeeded);
        buf.writeInt(pkt.pvpRating);
        buf.writeInt(pkt.peakRating);
        buf.writeInt(pkt.wins);
        buf.writeInt(pkt.losses);
        buf.writeInt(pkt.kills);
        buf.writeInt(pkt.deaths);
        buf.writeInt(pkt.pvpXp);
        buf.writeInt(pkt.arenaTokens);
        buf.writeUtf(pkt.rankName);
        buf.writeUtf(pkt.rankColor);
    }

    public static PlayerDataSyncPacket decode(FriendlyByteBuf buf) {
        return new PlayerDataSyncPacket(
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readUtf(), buf.readUtf()
        );
    }

    public static void handle(PlayerDataSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientPlayerData.update(
                pkt.gold, pkt.level, pkt.pveXp, pkt.xpNeeded,
                pkt.pvpRating, pkt.peakRating, pkt.wins, pkt.losses,
                pkt.kills, pkt.deaths, pkt.pvpXp, pkt.arenaTokens,
                pkt.rankName, pkt.rankColor
        ));
        ctx.setPacketHandled(true);
    }
}
