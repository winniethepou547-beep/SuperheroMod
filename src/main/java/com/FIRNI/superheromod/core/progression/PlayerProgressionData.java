package com.FIRNI.superheromod.core.progression;

public interface PlayerProgressionData {

    // === Economy ===
    int getGold();
    void setGold(int gold);
    void addGold(int amount);

    // === PvP ===
    int getPvpRating();
    void setPvpRating(int rating);
    void addPvpRating(int amount);

    int getPvpXp();
    void setPvpXp(int xp);
    void addPvpXp(int amount);

    int getWins();
    void setWins(int wins);

    int getLosses();
    void setLosses(int losses);

    int getKills();
    void setKills(int kills);

    int getDeaths();
    void setDeaths(int deaths);

    int getArenaTokens();
    void setArenaTokens(int tokens);
    void addArenaTokens(int amount);

    int getPeakRating();
    void setPeakRating(int peak);

    // === PvE ===
    int getLevel();
    void setLevel(int level);

    int getPveXp();
    void setPveXp(int xp);
    void addPveXp(int amount);

    @Deprecated default int getRank() { return getPvpRating(); }
    @Deprecated default void setRank(int rank) { setPvpRating(rank); }
    @Deprecated default void addRank(int amount) { addPvpRating(amount); }
    @Deprecated default int getXp() { return getPvpXp(); }
    @Deprecated default void setXp(int xp) { setPvpXp(xp); }
    @Deprecated default void addXp(int amount) { addPvpXp(amount); }
}
