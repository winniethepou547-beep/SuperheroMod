package com.FIRNI.superheromod.core.matchmaking;

import com.FIRNI.superheromod.core.progression.GoldSystem;
import com.FIRNI.superheromod.core.progression.ProgressionEvents;
import com.FIRNI.superheromod.core.progression.RankTier;
import com.FIRNI.superheromod.core.util.ServerScheduler;
import com.FIRNI.superheromod.core.world.ArenaLocations;
import com.FIRNI.superheromod.core.world.PvpMapLocations;
import com.FIRNI.superheromod.core.world.PvpMapBuilder;
import com.FIRNI.superheromod.core.world.ChunkedWorldEditor;
import com.FIRNI.superheromod.core.world.MapBlockTracker;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.MatchEndPacket;
import com.FIRNI.superheromod.network.packet.ScoreboardUpdatePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

public final class MatchManager {

    private static final Map<UUID, ActiveMatch> ACTIVE_MATCHES = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_MATCH = new HashMap<>();

    // Reward config
    private static final int WIN_GOLD = 100;
    private static final int LOSE_GOLD = 50;
    private static final int WIN_PVP_XP = 250;
    private static final int LOSE_PVP_XP = 100;
    private static final int WIN_RATING = 24;
    private static final int LOSE_RATING = 18;
    private static final int WIN_ARENA_TOKENS = 10;
    private static final int LOSE_ARENA_TOKENS = 3;

    private static final int RETURN_DELAY_TICKS = 60;
    private static final int ROUND_RESET_DELAY_TICKS = 60;

    private MatchManager() {
    }

    public static ActiveMatch registerMatch(MatchMode mode, GameMap map, List<UUID> teamA, List<UUID> teamB) {
        UUID matchId = UUID.randomUUID();
        ActiveMatch match = new ActiveMatch(matchId, mode, map, teamA, teamB);
        ACTIVE_MATCHES.put(matchId, match);
        for (UUID pid : match.getAllParticipants()) {
            PLAYER_TO_MATCH.put(pid, matchId);
        }
        MapBlockTracker.startTracking(matchId);
        return match;
    }

    public static ActiveMatch getMatchOf(UUID playerId) {
        UUID matchId = PLAYER_TO_MATCH.get(playerId);
        if (matchId == null) return null;
        return ACTIVE_MATCHES.get(matchId);
    }

    public static void onPlayerDeath(ServerPlayer deadPlayer, ServerPlayer killer, MinecraftServer server) {
        ActiveMatch match = getMatchOf(deadPlayer.getUUID());
        if (match == null || match.isFinished()) return;

        // Track kill/death stats
        if (killer != null && !killer.getUUID().equals(deadPlayer.getUUID())) {
            killer.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data ->
                    data.setKills(data.getKills() + 1));
        }
        deadPlayer.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data ->
                data.setDeaths(data.getDeaths() + 1));

        ActiveMatch.DeathResult result = match.recordDeath(deadPlayer.getUUID());
        broadcastScoreboard(match, server);

        switch (result) {
            case MATCH_WON -> endMatch(match, server);
            case ROUND_WON -> startNewRound(match, server);
            case CONTINUE -> deadPlayer.setGameMode(GameType.SPECTATOR);
        }
    }

    private static void startNewRound(ActiveMatch match, MinecraftServer server) {
        for (UUID pid : match.getAllParticipants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(pid);
            if (player == null) continue;
            player.sendSystemMessage(Component.literal(
                    "§eRound! Skor: §f" + match.getRoundsA() + " - " + match.getRoundsB()));
        }

        ServerLevel arenaLevel = server.getLevel(PvpMapLocations.DIMENSION);
        if (arenaLevel != null) {
            List<Runnable> restoreJobs = MapBlockTracker.restoreJobs(match.getMatchId(), arenaLevel);
            if (!restoreJobs.isEmpty()) {
                ChunkedWorldEditor.run(restoreJobs);
            }
        }

        ServerScheduler.schedule(ROUND_RESET_DELAY_TICKS, () -> {
            for (UUID pid : match.getAllParticipants()) {
                ServerPlayer player = server.getPlayerList().getPlayer(pid);
                if (player == null) continue;

                player.setGameMode(GameType.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.removeAllEffects();

                if (arenaLevel != null) {
                    player.teleportTo(arenaLevel,
                            match.getMap().spawn().getX() + 0.5,
                            match.getMap().spawn().getY() + 1,
                            match.getMap().spawn().getZ() + 0.5,
                            player.getYRot(), player.getXRot());
                }
            }
        });
    }

    private static void broadcastScoreboard(ActiveMatch match, MinecraftServer server) {
        for (UUID pid : match.getAllParticipants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(pid);
            if (player != null) {
                UUID opponentId = match.getOpponent(pid);
                String myName = player.getName().getString();
                int myScore = match.getMyScore(pid);
                String opponentName = "?";
                int opponentScore = match.getOpponentScore(pid);
                if (opponentId != null) {
                    ServerPlayer opponent = server.getPlayerList().getPlayer(opponentId);
                    opponentName = opponent != null ? opponent.getName().getString() : "?";
                }
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new ScoreboardUpdatePacket(myName, myScore, opponentName, opponentScore));
            }
        }
    }

    private static void endMatch(ActiveMatch match, MinecraftServer server) {
        List<UUID> winners = match.getWinningTeam();
        List<UUID> losers = match.getLosingTeam();
        List<UUID> participants = match.getAllParticipants();

        // Record match history
        String modeStr = match.getMode() == MatchMode.ONE_V_ONE ? "1v1" : "2v2";
        long now = System.currentTimeMillis();
        for (UUID pid : participants) {
            boolean isWinner = winners.contains(pid);
            int ratingChange = isWinner ? WIN_RATING : -LOSE_RATING;
            String opponentName = "?";
            UUID oppId = match.getOpponent(pid);
            if (oppId != null) {
                ServerPlayer opp = server.getPlayerList().getPlayer(oppId);
                if (opp != null) opponentName = opp.getName().getString();
            }
            MatchHistory.record(pid, new MatchHistory.MatchRecord(modeStr, opponentName, isWinner, ratingChange, now));
        }

        String winName = "?";
        if (!winners.isEmpty()) {
            ServerPlayer winPlayer = server.getPlayerList().getPlayer(winners.get(0));
            if (winPlayer != null) winName = winPlayer.getName().getString();
        }
        final String winnerDisplayName = winName;

        for (UUID pid : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(pid);
            if (player == null) continue;

            boolean isWinner = winners.contains(pid);

            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new MatchEndPacket(winnerDisplayName, isWinner));

            player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data -> {
                if (isWinner) {
                    data.setWins(data.getWins() + 1);
                    data.addGold(WIN_GOLD);
                    data.addPvpRating(WIN_RATING);
                    data.addPvpXp(WIN_PVP_XP);
                    data.addArenaTokens(WIN_ARENA_TOKENS);

                    RankTier tier = RankTier.fromRating(data.getPvpRating());
                    player.sendSystemMessage(Component.literal("§a§lTEBRIKLER! Maci kazandin!"));
                    player.sendSystemMessage(Component.literal(
                            "§6+" + WIN_GOLD + " Altin §7| §b+" + WIN_PVP_XP + " PvP XP §7| §e+" + WIN_RATING + " Rating §7| §d+" + WIN_ARENA_TOKENS + " Token"));
                    player.sendSystemMessage(Component.literal("§7Rank: " + tier.getColoredName()
                            + " §7(" + data.getPvpRating() + ")"));
                } else {
                    data.setLosses(data.getLosses() + 1);
                    data.addGold(LOSE_GOLD);
                    data.addPvpRating(-LOSE_RATING);
                    data.addPvpXp(LOSE_PVP_XP);
                    data.addArenaTokens(LOSE_ARENA_TOKENS);

                    RankTier tier = RankTier.fromRating(data.getPvpRating());
                    player.sendSystemMessage(Component.literal("§c" + winnerDisplayName + " maci kazandi."));
                    player.sendSystemMessage(Component.literal(
                            "§6+" + LOSE_GOLD + " Altin §7| §b+" + LOSE_PVP_XP + " PvP XP §7| §c-" + LOSE_RATING + " Rating §7| §d+" + LOSE_ARENA_TOKENS + " Token"));
                    player.sendSystemMessage(Component.literal("§7Rank: " + tier.getColoredName()
                            + " §7(" + data.getPvpRating() + ")"));
                }
            });
        }

        ServerLevel arenaLevel = server.getLevel(PvpMapLocations.DIMENSION);
        if (arenaLevel != null) {
            List<Runnable> restoreJobs = MapBlockTracker.restoreJobs(match.getMatchId(), arenaLevel);
            if (!restoreJobs.isEmpty()) {
                ChunkedWorldEditor.run(restoreJobs);
            }
        }
        MapBlockTracker.stopTracking(match.getMatchId());

        ServerScheduler.schedule(RETURN_DELAY_TICKS, () -> {
            for (UUID pid : participants) {
                ServerPlayer player = server.getPlayerList().getPlayer(pid);
                if (player == null) continue;

                player.setGameMode(GameType.SURVIVAL);
                var overworld = server.overworld();
                player.teleportTo(overworld,
                        ArenaLocations.HUB_SPAWN.getX() + 0.5,
                        ArenaLocations.HUB_SPAWN.getY(),
                        ArenaLocations.HUB_SPAWN.getZ() + 0.5,
                        player.getYRot(), player.getXRot());
                player.setHealth(player.getMaxHealth());
                player.getFoodData().setFoodLevel(20);
                player.removeAllEffects();
            }

            ACTIVE_MATCHES.remove(match.getMatchId());
            for (UUID pid : participants) {
                PLAYER_TO_MATCH.remove(pid);
            }
        });
    }

    public static void handleForfeit(ServerPlayer player, MinecraftServer server) {
        ActiveMatch match = getMatchOf(player.getUUID());
        if (match == null || match.isFinished()) {
            player.sendSystemMessage(Component.literal("§cBir macta degilsin!"));
            return;
        }

        ActiveMatch.ForfeitResult result = match.forfeit(player.getUUID());
        if (result == null) return;

        if (result.matchEnded()) {
            for (UUID pid : match.getAllParticipants()) {
                ServerPlayer p = server.getPlayerList().getPlayer(pid);
                if (p != null) {
                    p.sendSystemMessage(Component.literal(
                            "§c" + player.getName().getString() + " FF verdi! §7(" + result.votes() + "/" + result.needed() + ")"));
                }
            }
            endMatch(match, server);
        } else {
            for (UUID pid : match.getAllParticipants()) {
                ServerPlayer p = server.getPlayerList().getPlayer(pid);
                if (p != null) {
                    p.sendSystemMessage(Component.literal(
                            "§e" + player.getName().getString() + " FF vermek istiyor! §7(" + result.votes() + "/" + result.needed() + ")"));
                }
            }
        }
    }

    public static void clearAll() {
        ACTIVE_MATCHES.clear();
        PLAYER_TO_MATCH.clear();
    }
}
