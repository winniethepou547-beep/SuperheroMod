package com.FIRNI.superheromod.core.progression;

public enum RankTier {
    BRONZE    ("Bronze",       "§7",  0),
    SILVER    ("Silver",       "§f",  1000),
    GOLD      ("Gold",         "§6",  1200),
    PLATINUM  ("Platinum",     "§b",  1400),
    DIAMOND   ("Diamond",      "§3",  1600),
    MASTER    ("Master",       "§5",  1800),
    GRANDMASTER("Grandmaster", "§c",  2000);

    private final String displayName;
    private final String colorCode;
    private final int minRating;

    RankTier(String displayName, String colorCode, int minRating) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.minRating = minRating;
    }

    public String getDisplayName() { return displayName; }
    public String getColorCode() { return colorCode; }
    public int getMinRating() { return minRating; }

    public String getColoredName() {
        return colorCode + displayName;
    }

    public static RankTier fromRating(int rating) {
        RankTier result = BRONZE;
        for (RankTier tier : values()) {
            if (rating >= tier.minRating) {
                result = tier;
            }
        }
        return result;
    }
}
