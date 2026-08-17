package com.FIRNI.superheromod.core.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hangi oyuncunun hangi yetenegi ne zaman kullandigini takip eder.
 * Cooldown (bekleme suresi) dolmadan yetenek tekrar kullanilamaz.
 */
public class CooldownManager {

    // Her oyuncu (UUID) icin, her yetenek id'sinin son kullanildigi tick sayisi
    private static final Map<UUID, Map<String, Long>> lastUsedTick = new HashMap<>();

    public static boolean isOnCooldown(UUID playerId, Ability ability, long currentTick) {
        Map<String, Long> playerCooldowns = lastUsedTick.get(playerId);
        if (playerCooldowns == null) return false;

        Long lastUsed = playerCooldowns.get(ability.getId());
        if (lastUsed == null) return false;

        return (currentTick - lastUsed) < ability.getCooldownTicks();
    }

    public static void setUsed(UUID playerId, Ability ability, long currentTick) {
        lastUsedTick
                .computeIfAbsent(playerId, k -> new HashMap<>())
                .put(ability.getId(), currentTick);
    }

    public static long getRemainingTicks(UUID playerId, Ability ability, long currentTick) {
        Map<String, Long> playerCooldowns = lastUsedTick.get(playerId);
        if (playerCooldowns == null) return 0;

        Long lastUsed = playerCooldowns.get(ability.getId());
        if (lastUsed == null) return 0;

        long remaining = ability.getCooldownTicks() - (currentTick - lastUsed);
        return Math.max(0, remaining);
    }
}