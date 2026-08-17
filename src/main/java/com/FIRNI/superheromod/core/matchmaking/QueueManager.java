package com.FIRNI.superheromod.core.matchmaking;

import com.FIRNI.superheromod.core.progression.ProgressionEvents;
import com.FIRNI.superheromod.core.progression.RankTier;
import com.FIRNI.superheromod.core.util.ServerScheduler;
import com.FIRNI.superheromod.core.world.PvpMapLocations;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.MatchFoundPacket;
import com.FIRNI.superheromod.network.packet.ShowVsScreenPacket;
import com.FIRNI.superheromod.network.packet.StartMapVotePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mod basina bekleyen oyuncu kuyruklari. Yeterli oyuncu birikince maci baslatir:
 * MatchFoundPacket -> (gecikme) StartMapVotePacket -> (gecikme) isinlama + mesaj.
 * Gercek dovus/kazanma mantigi yok (Faz E), akis "mac basliyor" mesajinda bitiyor.
 */
public final class QueueManager {

    private static final int MATCH_FOUND_DELAY_TICKS = 200; // 10sn, MatchFoundScreen countdown suresiyle eslesir
    private static final int MAP_VOTE_DELAY_TICKS = 160;   // ~8sn, MapVoteScreen'in SWEEP+PAUSE+WINNER+EXTRA suresiyle eslesir

    private static final Map<MatchMode, List<ServerPlayer>> QUEUES = new EnumMap<>(MatchMode.class);

    static {
        for (MatchMode mode : MatchMode.values()) {
            QUEUES.put(mode, new ArrayList<>());
        }
    }

    private QueueManager() {
    }

    public static void enqueue(ServerPlayer player, MatchMode mode) {
        dequeue(player);

        List<ServerPlayer> queue = QUEUES.get(mode);
        queue.add(player);
        player.sendSystemMessage(Component.literal(mode.getDisplayName() + " kuyruguna girdin. Bekleyen: "
                + queue.size() + "/" + mode.getRequiredPlayers()));

        if (queue.size() >= mode.getRequiredPlayers()) {
            List<ServerPlayer> participants = new ArrayList<>(queue.subList(0, mode.getRequiredPlayers()));
            queue.removeAll(participants);
            formMatch(mode, participants);
        }
    }

    public static void dequeue(ServerPlayer player) {
        for (List<ServerPlayer> queue : QUEUES.values()) {
            queue.remove(player);
        }
    }

    /** OP-only /superhero testmatch komutu icin: tek oyuncuyla tum zinciri tetikler. */
    public static void startTestMatch(ServerPlayer player, MatchMode mode) {
        dequeue(player);
        formMatch(mode, List.of(player));
    }

    private static void formMatch(MatchMode mode, List<ServerPlayer> participants) {
        List<UUID> participantIds = participants.stream().map(ServerPlayer::getUUID).collect(Collectors.toList());
        MinecraftServer server = participants.get(0).getServer();

        for (ServerPlayer player : participants) {
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MatchFoundPacket(mode));
        }

        int winningIndex = MapRegistry.pickRandomIndex();

        ServerScheduler.schedule(MATCH_FOUND_DELAY_TICKS, () -> {
            for (ServerPlayer player : resolveOnline(server, participantIds)) {
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StartMapVotePacket(winningIndex));
            }

            ServerScheduler.schedule(MAP_VOTE_DELAY_TICKS, () -> {
                GameMap map = MapRegistry.MAPS.get(winningIndex);
                ServerLevel arenaLevel = server.getLevel(PvpMapLocations.DIMENSION);

                // Takim A ve B olarak ayir (1v1: birer kisi, 2v2: ikiservicemember kisi)
                List<UUID> teamA;
                List<UUID> teamB;
                if (participantIds.size() >= 2) {
                    int half = participantIds.size() / 2;
                    teamA = participantIds.subList(0, half);
                    teamB = participantIds.subList(half, participantIds.size());
                } else {
                    // Solo test: kendisiyle "mac"
                    teamA = participantIds;
                    teamB = List.of();
                }

                // Aktif maci kaydet
                ActiveMatch match = MatchManager.registerMatch(mode, map, teamA, teamB);

                // Teleport and show VS screen with Ready system
                ReadyManager.createSession(match.getMatchId(), match, server);

                for (ServerPlayer player : resolveOnline(server, participantIds)) {
                    if (arenaLevel != null) {
                        player.teleportTo(arenaLevel, map.spawn().getX() + 0.5, map.spawn().getY(), map.spawn().getZ() + 0.5,
                                player.getYRot(), player.getXRot());
                    }
                    player.sendSystemMessage(Component.literal("Mac basliyor: " + map.displayName() + "!"));

                    UUID opponentId = match.getOpponent(player.getUUID());
                    String opponentName = "?";
                    if (opponentId != null) {
                        ServerPlayer opponent = server.getPlayerList().getPlayer(opponentId);
                        if (opponent != null) opponentName = opponent.getName().getString();
                    }

                    String modeName = mode == MatchMode.ONE_V_ONE ? "1v1" : "2v2";

                    int[] playerStats = player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY)
                            .map(d -> new int[]{d.getPvpRating(), d.getWins(), d.getLosses()}).orElse(new int[]{1000, 0, 0});
                    int playerRating = playerStats[0];
                    int playerWins = playerStats[1];
                    int playerLosses = playerStats[2];
                    String playerRank = RankTier.fromRating(playerRating).getColoredName();

                    int oppRating = 1000;
                    int oppWins = 0;
                    int oppLosses = 0;
                    String oppRank = RankTier.fromRating(1000).getColoredName();
                    if (opponentId != null) {
                        ServerPlayer opp = server.getPlayerList().getPlayer(opponentId);
                        if (opp != null) {
                            int[] oppStats = opp.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY)
                                    .map(d -> new int[]{d.getPvpRating(), d.getWins(), d.getLosses()}).orElse(new int[]{1000, 0, 0});
                            oppRating = oppStats[0];
                            oppWins = oppStats[1];
                            oppLosses = oppStats[2];
                            oppRank = RankTier.fromRating(oppRating).getColoredName();
                        }
                    }

                    ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new ShowVsScreenPacket(player.getName().getString(), opponentName, modeName,
                                    playerRank, playerRating, playerWins, playerLosses,
                                    oppRank, oppRating, oppWins, oppLosses));
                }
            });
        });
    }

    private static List<ServerPlayer> resolveOnline(MinecraftServer server, List<UUID> ids) {
        List<ServerPlayer> result = new ArrayList<>();
        for (UUID id : ids) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                result.add(player);
            }
        }
        return result;
    }
}
