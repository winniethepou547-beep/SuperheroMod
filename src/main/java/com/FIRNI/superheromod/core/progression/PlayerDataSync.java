package com.FIRNI.superheromod.core.progression;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.matchmaking.MatchHistory;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.MatchHistorySyncPacket;
import com.FIRNI.superheromod.network.packet.PlayerDataSyncPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.stream.Collectors;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class PlayerDataSync {

    private static final int SYNC_INTERVAL_TICKS = 40; // 2 seconds

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (sp.tickCount % SYNC_INTERVAL_TICKS != 0) return;

        syncToClient(sp);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncToClient(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            syncToClient(sp);
        }
    }

    public static void syncToClient(ServerPlayer player) {
        player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data -> {
            RankTier tier = RankTier.fromRating(data.getPvpRating());
            int xpNeeded = LevelSystem.xpForLevel(data.getLevel());

            PlayerDataSyncPacket pkt = new PlayerDataSyncPacket(
                    data.getGold(), data.getLevel(), data.getPveXp(), xpNeeded,
                    data.getPvpRating(), data.getPeakRating(), data.getWins(), data.getLosses(),
                    data.getKills(), data.getDeaths(), data.getPvpXp(), data.getArenaTokens(),
                    tier.getDisplayName(), tier.getColorCode()
            );
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt);
        });

        // Sync match history
        var history = MatchHistory.getHistory(player.getUUID());
        var entries = history.stream()
                .map(r -> new MatchHistorySyncPacket.Entry(r.mode(), r.opponent(), r.won(), r.ratingChange(), r.timestamp()))
                .collect(Collectors.toList());
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new MatchHistorySyncPacket(entries));
    }
}
