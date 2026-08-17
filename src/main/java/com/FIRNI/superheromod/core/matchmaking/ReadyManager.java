package com.FIRNI.superheromod.core.matchmaking;

import com.FIRNI.superheromod.core.util.ServerScheduler;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.MatchStartingPacket;
import com.FIRNI.superheromod.network.packet.ScoreboardUpdatePacket;
import com.FIRNI.superheromod.network.packet.VsReadyUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReadyManager {

    private static final Map<UUID, ReadySession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_SESSION = new ConcurrentHashMap<>();

    public static void createSession(UUID sessionId, ActiveMatch match, MinecraftServer server) {
        ReadySession session = new ReadySession(sessionId, match, server);
        SESSIONS.put(sessionId, session);
        for (UUID pid : match.getAllParticipants()) {
            PLAYER_TO_SESSION.put(pid, sessionId);
        }
    }

    public static void setReady(ServerPlayer player) {
        UUID sessionId = PLAYER_TO_SESSION.get(player.getUUID());
        if (sessionId == null) return;
        ReadySession session = SESSIONS.get(sessionId);
        if (session == null) return;

        session.readyPlayers.add(player.getUUID());
        broadcastReadyState(session);

        if (session.isAllReady()) {
            startMatch(session);
        }
    }

    private static void broadcastReadyState(ReadySession session) {
        ActiveMatch match = session.match;
        MinecraftServer server = session.server;

        for (UUID pid : match.getAllParticipants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(pid);
            if (player == null) continue;

            boolean myReady = session.readyPlayers.contains(pid);
            UUID oppId = match.getOpponent(pid);
            boolean oppReady = oppId != null && session.readyPlayers.contains(oppId);

            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new VsReadyUpdatePacket(myReady, oppReady));
        }
    }

    private static void startMatch(ReadySession session) {
        SESSIONS.remove(session.sessionId);
        for (UUID pid : session.match.getAllParticipants()) {
            PLAYER_TO_SESSION.remove(pid);
        }

        ActiveMatch match = session.match;
        MinecraftServer server = session.server;

        for (UUID pid : match.getAllParticipants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(pid);
            if (player == null) continue;

            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MatchStartingPacket());

            UUID opponentId = match.getOpponent(pid);
            String opponentName = "?";
            if (opponentId != null) {
                ServerPlayer opponent = server.getPlayerList().getPlayer(opponentId);
                if (opponent != null) opponentName = opponent.getName().getString();
            }
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ScoreboardUpdatePacket(player.getName().getString(), 0, opponentName, 0));
        }
    }

    private static class ReadySession {
        final UUID sessionId;
        final ActiveMatch match;
        final MinecraftServer server;
        final Set<UUID> readyPlayers = ConcurrentHashMap.newKeySet();

        ReadySession(UUID sessionId, ActiveMatch match, MinecraftServer server) {
            this.sessionId = sessionId;
            this.match = match;
            this.server = server;
        }

        boolean isAllReady() {
            for (UUID pid : match.getAllParticipants()) {
                if (!readyPlayers.contains(pid)) return false;
            }
            return true;
        }
    }
}
