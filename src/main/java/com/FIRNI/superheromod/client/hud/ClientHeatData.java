package com.FIRNI.superheromod.client.hud;

public class ClientHeatData {

    private static float current = 0f;
    private static float max = 100f;
    private static boolean overheated = false;
    private static boolean beamActive = false;

    public static void update(float current, float max, boolean overheated, boolean active) {
        ClientHeatData.current = current;
        ClientHeatData.max = max;
        ClientHeatData.overheated = overheated;
        ClientHeatData.beamActive = active;
    }

    public static float getPercent() {
        return max > 0 ? current / max : 0f;
    }

    public static float getCurrent() { return current; }
    public static float getMax() { return max; }
    public static boolean isOverheated() { return overheated; }
    public static boolean isBeamActive() { return beamActive; }
    public static boolean hasHeat() { return current > 0.01f; }
}
