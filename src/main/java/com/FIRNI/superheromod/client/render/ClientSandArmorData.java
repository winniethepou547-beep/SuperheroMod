package com.FIRNI.superheromod.client.render;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Istemcide oyuncularin kum zirh seviyeleri. Sunucu degistikce yolluyor. */
public final class ClientSandArmorData {

    private static final Map<UUID, Integer> levels = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> fills = new ConcurrentHashMap<>();

    private ClientSandArmorData() {}

    public static void set(UUID playerId, int level, float fill) {
        if (level <= 0) {
            levels.remove(playerId);
        } else {
            levels.put(playerId, level);
        }

        if (fill <= 0.001f) {
            fills.remove(playerId);
        } else {
            fills.put(playerId, fill);
        }
    }

    public static int get(Player player) {
        return levels.getOrDefault(player.getUUID(), 0);
    }

    /** Dikey barin doluluk orani (0..1). */
    public static float fill(Player player) {
        return fills.getOrDefault(player.getUUID(), 0f);
    }

    public static void clear() {
        levels.clear();
        fills.clear();
    }
}
