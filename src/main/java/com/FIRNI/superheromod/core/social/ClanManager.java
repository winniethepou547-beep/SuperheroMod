package com.FIRNI.superheromod.core.social;

import com.FIRNI.superheromod.core.matchmaking.ActiveMatch;
import com.FIRNI.superheromod.core.matchmaking.MatchManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClanManager {

    private static final Map<UUID, Clan> PLAYER_CLAN = new ConcurrentHashMap<>();
    private static final Map<String, Clan> CLAN_BY_NAME = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PENDING_INVITES = new ConcurrentHashMap<>();

    public static Clan getClan(UUID playerId) {
        return PLAYER_CLAN.get(playerId);
    }

    public static Clan create(ServerPlayer leader, String name) {
        if (PLAYER_CLAN.containsKey(leader.getUUID())) return null;

        String lower = name.toLowerCase(Locale.ROOT);
        if (CLAN_BY_NAME.containsKey(lower)) return null;

        Clan clan = new Clan(name, leader.getUUID());
        PLAYER_CLAN.put(leader.getUUID(), clan);
        CLAN_BY_NAME.put(lower, clan);
        return clan;
    }

    public static boolean invite(ServerPlayer inviter, ServerPlayer target) {
        Clan clan = PLAYER_CLAN.get(inviter.getUUID());
        if (clan == null) {
            inviter.sendSystemMessage(Component.literal("§cBir klanda degilsin!"));
            return false;
        }
        if (!clan.isLeader(inviter.getUUID())) {
            inviter.sendSystemMessage(Component.literal("§cSadece lider davet gonderebilir!"));
            return false;
        }
        if (clan.isFull()) {
            inviter.sendSystemMessage(Component.literal("§cKlan dolu! (6/6)"));
            return false;
        }
        if (PLAYER_CLAN.containsKey(target.getUUID())) {
            inviter.sendSystemMessage(Component.literal("§cO oyuncu zaten bir klanda!"));
            return false;
        }

        PENDING_INVITES.put(target.getUUID(), clan.getName().toLowerCase(Locale.ROOT));
        inviter.sendSystemMessage(Component.literal(
                "§a" + target.getName().getString() + " §foyuncusuna klan daveti gonderildi."));
        target.sendSystemMessage(Component.literal(
                "§a" + inviter.getName().getString() + " §fseni §6" + clan.getName()
                        + " §fklanina davet etti! §e/clan accept §fveya §c/clan deny"));
        return true;
    }

    public static boolean accept(ServerPlayer player) {
        String clanKey = PENDING_INVITES.remove(player.getUUID());
        if (clanKey == null) {
            player.sendSystemMessage(Component.literal("§cBekleyen klan daveti yok!"));
            return false;
        }
        Clan clan = CLAN_BY_NAME.get(clanKey);
        if (clan == null || clan.isFull()) {
            player.sendSystemMessage(Component.literal("§cKlan artik musait degil."));
            return false;
        }

        clan.addMember(player.getUUID());
        PLAYER_CLAN.put(player.getUUID(), clan);

        broadcastToClan(clan, player.getServer(),
                "§a" + player.getName().getString() + " klana katildi! (" + clan.getSize() + "/6)");
        return true;
    }

    public static boolean deny(ServerPlayer player) {
        String clanKey = PENDING_INVITES.remove(player.getUUID());
        if (clanKey == null) {
            player.sendSystemMessage(Component.literal("§cBekleyen klan daveti yok!"));
            return false;
        }
        player.sendSystemMessage(Component.literal("§eKlan daveti reddedildi."));
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        Clan clan = PLAYER_CLAN.remove(player.getUUID());
        if (clan == null) {
            player.sendSystemMessage(Component.literal("§cBir klanda degilsin!"));
            return false;
        }
        if (clan.isLeader(player.getUUID()) && clan.getSize() > 1) {
            player.sendSystemMessage(Component.literal("§cLider olarak ayrilamazsin! Once /clan promote ile lider devret veya /clan disband yap."));
            PLAYER_CLAN.put(player.getUUID(), clan);
            return false;
        }

        clan.removeMember(player.getUUID());
        player.sendSystemMessage(Component.literal("§eKlandan ayrildin."));

        if (clan.isEmpty()) {
            CLAN_BY_NAME.remove(clan.getName().toLowerCase(Locale.ROOT));
        } else {
            broadcastToClan(clan, player.getServer(),
                    "§c" + player.getName().getString() + " klandan ayrildi.");
        }
        return true;
    }

    public static boolean kick(ServerPlayer leader, ServerPlayer target) {
        Clan clan = PLAYER_CLAN.get(leader.getUUID());
        if (clan == null || !clan.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cSadece lider oyuncu atabilir!"));
            return false;
        }
        if (!clan.isMember(target.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cO oyuncu klaninda degil!"));
            return false;
        }
        if (target.getUUID().equals(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cKendini atamazsin!"));
            return false;
        }

        clan.removeMember(target.getUUID());
        PLAYER_CLAN.remove(target.getUUID());
        target.sendSystemMessage(Component.literal("§cKlandan atildin!"));
        broadcastToClan(clan, leader.getServer(),
                "§c" + target.getName().getString() + " klandan atildi.");
        return true;
    }

    public static boolean promote(ServerPlayer leader, ServerPlayer target) {
        Clan clan = PLAYER_CLAN.get(leader.getUUID());
        if (clan == null || !clan.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cSadece lider liderlik devredebilir!"));
            return false;
        }
        if (!clan.isMember(target.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cO oyuncu klaninda degil!"));
            return false;
        }
        clan.promote(target.getUUID());
        broadcastToClan(clan, leader.getServer(),
                "§e" + target.getName().getString() + " yeni klan lideri oldu!");
        return true;
    }

    public static boolean disband(ServerPlayer leader) {
        Clan clan = PLAYER_CLAN.get(leader.getUUID());
        if (clan == null || !clan.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cSadece lider klani dagitabilir!"));
            return false;
        }

        broadcastToClan(clan, leader.getServer(), "§c§l" + clan.getName() + " §cklan dagildi!");
        for (UUID member : clan.getMembers()) {
            PLAYER_CLAN.remove(member);
        }
        CLAN_BY_NAME.remove(clan.getName().toLowerCase(Locale.ROOT));
        return true;
    }

    public static void showStatus(ServerPlayer player) {
        Clan clan = PLAYER_CLAN.get(player.getUUID());
        if (clan == null) {
            player.sendSystemMessage(Component.literal("§cBir klanda degilsin! §e/clan create <isim>"));
            return;
        }

        MinecraftServer server = player.getServer();

        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§6§lKLAN: §f" + clan.getName()
                + " §7(" + clan.getSize() + "/6)"));
        player.sendSystemMessage(Component.literal("§6§m                              "));

        for (UUID memberId : clan.getMembers()) {
            ServerPlayer member = server.getPlayerList().getPlayer(memberId);
            String name = member != null ? member.getName().getString() : memberId.toString().substring(0, 8);
            String role = clan.isLeader(memberId) ? "§c[Lider] " : "§7";
            String status = getPlayerStatus(memberId, server);

            player.sendSystemMessage(Component.literal(role + name + " §7- " + status));
        }
        player.sendSystemMessage(Component.literal("§6§m                              "));
    }

    public static void clanChat(ServerPlayer sender, String message) {
        Clan clan = PLAYER_CLAN.get(sender.getUUID());
        if (clan == null) {
            sender.sendSystemMessage(Component.literal("§cBir klanda degilsin!"));
            return;
        }
        broadcastToClan(clan, sender.getServer(),
                "§7[§6Klan§7] §f" + sender.getName().getString() + ": §7" + message);
    }

    private static String getPlayerStatus(UUID playerId, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return "§8Offline";

        ActiveMatch match = MatchManager.getMatchOf(playerId);
        if (match != null) {
            return "§c⚔ PvP Macta";
        }

        return "§aOnline";
    }

    public static void cleanup(UUID playerId) {
        Clan clan = PLAYER_CLAN.get(playerId);
        if (clan != null && clan.isLeader(playerId) && clan.getSize() <= 1) {
            PLAYER_CLAN.remove(playerId);
            clan.removeMember(playerId);
            if (clan.isEmpty()) {
                CLAN_BY_NAME.remove(clan.getName().toLowerCase(Locale.ROOT));
            }
        }
        PENDING_INVITES.remove(playerId);
    }

    private static void broadcastToClan(Clan clan, MinecraftServer server, String message) {
        for (UUID member : clan.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }
}
