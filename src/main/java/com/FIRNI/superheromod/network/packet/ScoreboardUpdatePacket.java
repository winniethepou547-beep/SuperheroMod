package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: Mac sirasinda skorboard'u gunceller. Her iki oyuncunun ismini
 * ve kill sayisini tasir, istemcide ScoreboardOverlay'i besler.
 */
public class ScoreboardUpdatePacket {

    private final String playerName;
    private final int playerKills;
    private final String opponentName;
    private final int opponentKills;

    public ScoreboardUpdatePacket(String playerName, int playerKills, String opponentName, int opponentKills) {
        this.playerName = playerName;
        this.playerKills = playerKills;
        this.opponentName = opponentName;
        this.opponentKills = opponentKills;
    }

    public static void encode(ScoreboardUpdatePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.playerName, 64);
        buf.writeInt(packet.playerKills);
        buf.writeUtf(packet.opponentName, 64);
        buf.writeInt(packet.opponentKills);
    }

    public static ScoreboardUpdatePacket decode(FriendlyByteBuf buf) {
        return new ScoreboardUpdatePacket(buf.readUtf(64), buf.readInt(), buf.readUtf(64), buf.readInt());
    }

    public static void handle(ScoreboardUpdatePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientPacketHandler.updateScoreboard(
                packet.playerName, packet.playerKills,
                packet.opponentName, packet.opponentKills));
        ctx.setPacketHandled(true);
    }
}
