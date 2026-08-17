package com.FIRNI.superheromod.core.matchmaking;

public enum MatchMode {
    ONE_V_ONE("1v1", 2),
    TWO_V_TWO("2v2", 4);

    private final String displayName;
    private final int requiredPlayers;

    MatchMode(String displayName, int requiredPlayers) {
        this.displayName = displayName;
        this.requiredPlayers = requiredPlayers;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRequiredPlayers() {
        return requiredPlayers;
    }
}
