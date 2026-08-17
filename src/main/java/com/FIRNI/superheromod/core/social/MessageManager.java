package com.FIRNI.superheromod.core.social;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MessageManager {

    private static final Map<UUID, UUID> LAST_MESSAGED = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> IGNORED = new ConcurrentHashMap<>();

    public static void setLastMessaged(UUID sender, UUID receiver) {
        LAST_MESSAGED.put(sender, receiver);
        LAST_MESSAGED.put(receiver, sender);
    }

    public static UUID getLastMessaged(UUID playerId) {
        return LAST_MESSAGED.get(playerId);
    }

    public static void ignore(UUID player, UUID target) {
        IGNORED.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(target);
    }

    public static void unignore(UUID player, UUID target) {
        Set<UUID> set = IGNORED.get(player);
        if (set != null) set.remove(target);
    }

    public static boolean isIgnored(UUID player, UUID sender) {
        Set<UUID> set = IGNORED.get(player);
        return set != null && set.contains(sender);
    }

    public static Set<UUID> getIgnoreList(UUID player) {
        return IGNORED.getOrDefault(player, Set.of());
    }

    public static void cleanup(UUID playerId) {
        LAST_MESSAGED.remove(playerId);
    }
}
