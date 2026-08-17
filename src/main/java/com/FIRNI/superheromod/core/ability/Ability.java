package com.FIRNI.superheromod.core.ability;

import net.minecraft.server.level.ServerPlayer;

public abstract class Ability {

    private final String id;
    private final AbilityType type;
    private final AbilitySlot slot;
    private final AbilityConfig config;

    private AbilityState state = AbilityState.IDLE;
    private int activeTicks = 0;
    private int cooldownTicksRemaining = 0;

    public Ability(String id, AbilityType type, AbilitySlot slot) {
        this.id = id;
        this.type = type;
        this.slot = slot;
        this.config = new AbilityConfig();
        initConfig(this.config);
    }

    protected abstract void initConfig(AbilityConfig config);

    public boolean canActivate(ServerPlayer player) {
        if (state != AbilityState.IDLE) return false;
        if (cooldownTicksRemaining > 0) return false;
        return true;
    }

    public void activate(ServerPlayer player) {
        if (!canActivate(player)) return;
        activeTicks = 0;

        switch (type) {
            case INSTANT -> {
                state = AbilityState.ACTIVE;
                onActivate(player);
                onFinish(player);
                startCooldown();
            }
            case CHANNELED -> {
                state = AbilityState.CHANNELING;
                onActivate(player);
            }
            case TOGGLE -> {
                state = AbilityState.ACTIVE;
                onActivate(player);
            }
            case PASSIVE -> {}
        }
    }

    public void deactivate(ServerPlayer player) {
        if (type == AbilityType.CHANNELED && state == AbilityState.CHANNELING) {
            onChannelStop(player);
            onFinish(player);
            startCooldown();
        } else if (type == AbilityType.TOGGLE && state == AbilityState.ACTIVE) {
            onFinish(player);
            startCooldown();
        }
    }

    public void tick(ServerPlayer player) {
        if (cooldownTicksRemaining > 0) {
            cooldownTicksRemaining--;
            if (cooldownTicksRemaining <= 0) {
                state = AbilityState.IDLE;
            }
        }

        if (state == AbilityState.CHANNELING || state == AbilityState.ACTIVE) {
            activeTicks++;
            onTick(player, activeTicks);

            int maxDuration = config.getInt("maxDurationTicks", 0);
            if (maxDuration > 0 && activeTicks >= maxDuration) {
                forceStop(player);
            }
        }
    }

    public void forceStop(ServerPlayer player) {
        if (state == AbilityState.CHANNELING || state == AbilityState.ACTIVE) {
            onChannelStop(player);
            onFinish(player);
            startCooldown();
        }
    }

    public void cancel(ServerPlayer player) {
        if (state == AbilityState.CHANNELING || state == AbilityState.ACTIVE) {
            state = AbilityState.CANCELLED;
            onCancel(player);
            startCooldown();
        }
    }

    private void startCooldown() {
        int cd = config.getInt("cooldownTicks", 0);
        if (cd > 0) {
            cooldownTicksRemaining = cd;
            state = AbilityState.COOLDOWN;
        } else {
            state = AbilityState.IDLE;
        }
    }

    protected abstract void onActivate(ServerPlayer player);

    protected void onTick(ServerPlayer player, int ticksActive) {}

    protected void onChannelStop(ServerPlayer player) {}

    protected void onFinish(ServerPlayer player) {}

    protected void onCancel(ServerPlayer player) {}

    // --- Getters ---

    public String getId() { return id; }
    public AbilityType getType() { return type; }
    public AbilitySlot getSlot() { return slot; }
    public AbilityConfig getConfig() { return config; }
    public AbilityState getState() { return state; }
    public int getActiveTicks() { return activeTicks; }
    public int getCooldownTicksRemaining() { return cooldownTicksRemaining; }
    public int getCooldownTicks() { return config.getInt("cooldownTicks", 0); }

    public boolean isActive() {
        return state == AbilityState.ACTIVE || state == AbilityState.CHANNELING;
    }

    public void setState(AbilityState state) { this.state = state; }
    public void setCooldownTicksRemaining(int ticks) { this.cooldownTicksRemaining = ticks; }
}
