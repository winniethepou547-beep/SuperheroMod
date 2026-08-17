package com.FIRNI.superheromod.core.progression;

public class PlayerProgressionCapability implements PlayerProgressionData {

    private int gold = 0;

    // PvP
    private int pvpRating = 1000;
    private int pvpXp = 0;
    private int wins = 0;
    private int losses = 0;
    private int kills = 0;
    private int deaths = 0;
    private int arenaTokens = 0;
    private int peakRating = 1000;

    // PvE
    private int level = 1;
    private int pveXp = 0;

    // === Economy ===

    @Override public int getGold() { return gold; }
    @Override public void setGold(int gold) { this.gold = Math.max(0, gold); }
    @Override public void addGold(int amount) { this.gold = Math.max(0, this.gold + amount); }

    // === PvP ===

    @Override public int getPvpRating() { return pvpRating; }
    @Override public void setPvpRating(int rating) { this.pvpRating = Math.max(0, rating); }
    @Override public void addPvpRating(int amount) {
        this.pvpRating = Math.max(0, this.pvpRating + amount);
        if (this.pvpRating > this.peakRating) this.peakRating = this.pvpRating;
    }

    @Override public int getPvpXp() { return pvpXp; }
    @Override public void setPvpXp(int xp) { this.pvpXp = Math.max(0, xp); }
    @Override public void addPvpXp(int amount) { this.pvpXp = Math.max(0, this.pvpXp + amount); }

    @Override public int getWins() { return wins; }
    @Override public void setWins(int wins) { this.wins = wins; }

    @Override public int getLosses() { return losses; }
    @Override public void setLosses(int losses) { this.losses = losses; }

    @Override public int getKills() { return kills; }
    @Override public void setKills(int kills) { this.kills = kills; }

    @Override public int getDeaths() { return deaths; }
    @Override public void setDeaths(int deaths) { this.deaths = deaths; }

    @Override public int getArenaTokens() { return arenaTokens; }
    @Override public void setArenaTokens(int tokens) { this.arenaTokens = Math.max(0, tokens); }
    @Override public void addArenaTokens(int amount) { this.arenaTokens = Math.max(0, this.arenaTokens + amount); }

    @Override public int getPeakRating() { return peakRating; }
    @Override public void setPeakRating(int peak) { this.peakRating = peak; }

    // === PvE ===

    @Override public int getLevel() { return level; }
    @Override public void setLevel(int level) { this.level = Math.max(1, level); }

    @Override public int getPveXp() { return pveXp; }
    @Override public void setPveXp(int xp) { this.pveXp = Math.max(0, xp); }
    @Override public void addPveXp(int amount) { this.pveXp = Math.max(0, this.pveXp + amount); }
}
