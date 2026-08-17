package com.FIRNI.superheromod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hangi oyuncunun hangi kahraman oldugunu istemci tarafinda tutar.
 * Skin giydirme ve poz sistemi buna gore calisir — sadece yerel oyuncu degil,
 * gorulen TUM oyuncular icin dogru sonuc verir.
 */
public final class ClientHeroRegistry {

    private static final Map<UUID, String> heroes = new ConcurrentHashMap<>();

    private ClientHeroRegistry() {}

    public static void set(UUID playerId, String characterId) {
        if (characterId == null || characterId.isEmpty()) {
            heroes.remove(playerId);
        } else {
            heroes.put(playerId, characterId);
        }
    }

    public static String get(UUID playerId) {
        return heroes.get(playerId);
    }

    public static boolean isHero(Player player) {
        return heroes.containsKey(player.getUUID());
    }

    /** Yerel oyuncu bir kahraman mi? */
    public static boolean isLocalHero() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && heroes.containsKey(mc.player.getUUID());
    }

    public static void clear() {
        heroes.clear();
    }
}
