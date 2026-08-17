package com.FIRNI.superheromod.client;

public final class ClientPlayerData {

    public static int gold;
    public static int level;
    public static int pveXp;
    public static int xpNeeded;
    public static int pvpRating;
    public static int peakRating;
    public static int wins;
    public static int losses;
    public static int kills;
    public static int deaths;
    public static int pvpXp;
    public static int arenaTokens;
    public static String rankName = "Bronze";
    public static String rankColor = "§7";

    private ClientPlayerData() {}

    public static void update(int gold, int level, int pveXp, int xpNeeded,
                              int pvpRating, int peakRating, int wins, int losses,
                              int kills, int deaths, int pvpXp, int arenaTokens,
                              String rankName, String rankColor) {
        ClientPlayerData.gold = gold;
        ClientPlayerData.level = level;
        ClientPlayerData.pveXp = pveXp;
        ClientPlayerData.xpNeeded = xpNeeded;
        ClientPlayerData.pvpRating = pvpRating;
        ClientPlayerData.peakRating = peakRating;
        ClientPlayerData.wins = wins;
        ClientPlayerData.losses = losses;
        ClientPlayerData.kills = kills;
        ClientPlayerData.deaths = deaths;
        ClientPlayerData.pvpXp = pvpXp;
        ClientPlayerData.arenaTokens = arenaTokens;
        ClientPlayerData.rankName = rankName;
        ClientPlayerData.rankColor = rankColor;
    }

    public static String getKd() {
        if (deaths == 0) return "-";
        return String.format("%.2f", (double) kills / deaths);
    }
}
