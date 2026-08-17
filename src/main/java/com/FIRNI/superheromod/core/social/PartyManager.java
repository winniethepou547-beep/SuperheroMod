package com.FIRNI.superheromod.core.social;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {

    private static final Map<UUID, Party> PLAYER_PARTY = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PENDING_INVITES = new ConcurrentHashMap<>();

    public static Party getParty(UUID playerId) {
        return PLAYER_PARTY.get(playerId);
    }

    public static Party createParty(ServerPlayer leader) {
        if (PLAYER_PARTY.containsKey(leader.getUUID())) return null;
        Party party = new Party(leader.getUUID());
        PLAYER_PARTY.put(leader.getUUID(), party);
        return party;
    }

    public static boolean invite(ServerPlayer inviter, ServerPlayer target) {
        Party party = PLAYER_PARTY.get(inviter.getUUID());
        if (party == null) {
            party = createParty(inviter);
            inviter.sendSystemMessage(Component.literal("§aParti olusturuldu!"));
        }
        if (!party.isLeader(inviter.getUUID())) {
            inviter.sendSystemMessage(Component.literal("§cSadece lider davet gonderebilir!"));
            return false;
        }
        if (party.isFull()) {
            inviter.sendSystemMessage(Component.literal("§cParti dolu!"));
            return false;
        }
        if (PLAYER_PARTY.containsKey(target.getUUID())) {
            inviter.sendSystemMessage(Component.literal("§cO oyuncu zaten bir partide!"));
            return false;
        }

        PENDING_INVITES.put(target.getUUID(), inviter.getUUID());
        inviter.sendSystemMessage(Component.literal(
                "§a" + target.getName().getString() + " §foyuncusuna davet gonderildi."));
        target.sendSystemMessage(Component.literal(
                "§a" + inviter.getName().getString() + " §fseni partiye davet etti! §e/party accept §fveya §c/party deny"));
        return true;
    }

    public static boolean accept(ServerPlayer player) {
        UUID inviterId = PENDING_INVITES.remove(player.getUUID());
        if (inviterId == null) {
            player.sendSystemMessage(Component.literal("§cBekleyen davet yok!"));
            return false;
        }

        Party party = PLAYER_PARTY.get(inviterId);
        if (party == null || party.isFull()) {
            player.sendSystemMessage(Component.literal("§cParti artik musait degil."));
            return false;
        }

        party.addMember(player.getUUID());
        PLAYER_PARTY.put(player.getUUID(), party);

        broadcastToParty(party, player.getServer(),
                "§a" + player.getName().getString() + " partiye katildi! (" + party.getSize() + "/4)");
        return true;
    }

    public static boolean deny(ServerPlayer player) {
        UUID inviterId = PENDING_INVITES.remove(player.getUUID());
        if (inviterId == null) {
            player.sendSystemMessage(Component.literal("§cBekleyen davet yok!"));
            return false;
        }
        player.sendSystemMessage(Component.literal("§eDavet reddedildi."));

        ServerPlayer inviter = player.getServer().getPlayerList().getPlayer(inviterId);
        if (inviter != null) {
            inviter.sendSystemMessage(Component.literal(
                    "§c" + player.getName().getString() + " daveti reddetti."));
        }
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        Party party = PLAYER_PARTY.remove(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.literal("§cBir partide degilsin!"));
            return false;
        }

        party.removeMember(player.getUUID());
        player.sendSystemMessage(Component.literal("§ePartiden ayrildin."));

        if (party.isEmpty()) return true;

        broadcastToParty(party, player.getServer(),
                "§c" + player.getName().getString() + " partiden ayrildi.");
        return true;
    }

    public static boolean kick(ServerPlayer leader, ServerPlayer target) {
        Party party = PLAYER_PARTY.get(leader.getUUID());
        if (party == null || !party.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cSadece lider oyuncu atabilir!"));
            return false;
        }
        if (!party.isMember(target.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cO oyuncu partinde degil!"));
            return false;
        }

        party.removeMember(target.getUUID());
        PLAYER_PARTY.remove(target.getUUID());
        target.sendSystemMessage(Component.literal("§cPartiden atildin!"));
        broadcastToParty(party, leader.getServer(),
                "§c" + target.getName().getString() + " partiden atildi.");
        return true;
    }

    public static boolean promote(ServerPlayer leader, ServerPlayer target) {
        Party party = PLAYER_PARTY.get(leader.getUUID());
        if (party == null || !party.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cSadece lider liderlik devredebilir!"));
            return false;
        }
        if (!party.isMember(target.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cO oyuncu partinde degil!"));
            return false;
        }
        party.promote(target.getUUID());
        broadcastToParty(party, leader.getServer(),
                "§e" + target.getName().getString() + " yeni lider oldu!");
        return true;
    }

    public static boolean disband(ServerPlayer leader) {
        Party party = PLAYER_PARTY.get(leader.getUUID());
        if (party == null || !party.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.literal("§cSadece lider partiyi dagitabilir!"));
            return false;
        }

        broadcastToParty(party, leader.getServer(), "§cParti dagildi!");
        for (UUID member : party.getMembers()) {
            PLAYER_PARTY.remove(member);
        }
        return true;
    }

    public static void partyChat(ServerPlayer sender, String message) {
        Party party = PLAYER_PARTY.get(sender.getUUID());
        if (party == null) {
            sender.sendSystemMessage(Component.literal("§cBir partide degilsin!"));
            return;
        }
        broadcastToParty(party, sender.getServer(),
                "§7[§dParti§7] §f" + sender.getName().getString() + ": §7" + message);
    }

    public static void cleanup(UUID playerId) {
        Party party = PLAYER_PARTY.remove(playerId);
        if (party != null) {
            party.removeMember(playerId);
        }
        PENDING_INVITES.remove(playerId);
    }

    private static void broadcastToParty(Party party, MinecraftServer server, String message) {
        for (UUID member : party.getMembers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }
}
