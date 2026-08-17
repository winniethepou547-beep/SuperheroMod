package com.FIRNI.superheromod.core.resource;

public class ResourceBar {

    private final ResourceType type;
    private final String displayName;

    private float currentValue = 0f;
    private float maxValue;
    private float increaseRate;
    private float decreaseRate;
    private int coolDelayTicks;
    private float reactivationThreshold;

    /** Overheat sonrasi yetenegin kilitli kalacagi sure (tick). 40 = 2 saniye. */
    private int overheatLockTicks = 40;
    private int overheatLockRemaining = 0;

    private boolean locked = false;
    private int coolDelayRemaining = 0;
    private boolean overheated = false;

    public ResourceBar(ResourceType type, String displayName,
                       float maxValue, float increaseRate, float decreaseRate,
                       int coolDelayTicks, float reactivationThreshold) {
        this.type = type;
        this.displayName = displayName;
        this.maxValue = maxValue;
        this.increaseRate = increaseRate;
        this.decreaseRate = decreaseRate;
        this.coolDelayTicks = coolDelayTicks;
        this.reactivationThreshold = reactivationThreshold;
    }

    public void increase() {
        if (locked) return;
        currentValue = Math.min(currentValue + increaseRate, maxValue);
        coolDelayRemaining = coolDelayTicks;

        if (currentValue >= maxValue) {
            overheated = true;
            locked = true;
            overheatLockRemaining = overheatLockTicks;
        }
    }

    public void tick() {
        // Overheat kilidi: sabit sureli cooldown, bitince bar tamamen sifirlanir
        if (overheated) {
            if (overheatLockRemaining > 0) {
                overheatLockRemaining--;
                if (overheatLockRemaining <= 0) {
                    reset();
                }
            }
            return;
        }

        if (currentValue <= 0f) return;

        if (coolDelayRemaining > 0) {
            coolDelayRemaining--;
            return;
        }

        currentValue = Math.max(currentValue - decreaseRate, 0f);
    }

    public void reset() {
        currentValue = 0f;
        locked = false;
        overheated = false;
        coolDelayRemaining = 0;
        overheatLockRemaining = 0;
    }

    public boolean canUse() {
        return !locked && currentValue < maxValue;
    }

    public float getPercent() {
        return maxValue > 0 ? currentValue / maxValue : 0f;
    }

    public HeatLevel getHeatLevel() {
        float pct = getPercent();
        if (overheated) return HeatLevel.OVERHEATED;
        if (pct >= 0.85f) return HeatLevel.CRITICAL;
        if (pct >= 0.65f) return HeatLevel.HOT;
        if (pct >= 0.40f) return HeatLevel.WARM;
        return HeatLevel.NORMAL;
    }

    // --- Getters ---

    public ResourceType getType() { return type; }
    public String getDisplayName() { return displayName; }
    public float getCurrentValue() { return currentValue; }
    public float getMaxValue() { return maxValue; }
    public boolean isLocked() { return locked; }
    public boolean isOverheated() { return overheated; }
    public float getIncreaseRate() { return increaseRate; }
    public float getDecreaseRate() { return decreaseRate; }
    public int getOverheatLockRemaining() { return overheatLockRemaining; }

    // --- Setters for config tuning ---

    public void setMaxValue(float v) { this.maxValue = v; }
    public void setIncreaseRate(float v) { this.increaseRate = v; }
    public void setDecreaseRate(float v) { this.decreaseRate = v; }
    public void setCoolDelayTicks(int v) { this.coolDelayTicks = v; }
    public void setReactivationThreshold(float v) { this.reactivationThreshold = v; }
    public void setOverheatLockTicks(int v) { this.overheatLockTicks = v; }

    public enum HeatLevel {
        NORMAL,
        WARM,
        HOT,
        CRITICAL,
        OVERHEATED
    }
}
