package com.FIRNI.superheromod.core.matchmaking;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MatchHistory {

    private static final int MAX_HISTORY = 10;
    private static final Map<UUID, List<MatchRecord>> HISTORY = new ConcurrentHashMap<>();

    public static void record(UUID playerId, MatchRecord record) {
        HISTORY.computeIfAbsent(playerId, k -> new ArrayList<>());
        List<MatchRecord> list = HISTORY.get(playerId);
        list.add(0, record);
        if (list.size() > MAX_HISTORY) {
            list.remove(list.size() - 1);
        }
    }

    public static List<MatchRecord> getHistory(UUID playerId) {
        return HISTORY.getOrDefault(playerId, Collections.emptyList());
    }

    public record MatchRecord(String mode, String opponent, boolean won, int ratingChange, long timestamp) {}
}
