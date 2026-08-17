package com.FIRNI.superheromod.core.progression;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public class LevelSystem {

    public static final int MAX_LEVEL = 50;

    public static int xpForLevel(int level) {
        return (int) (100 * Math.pow(level, 1.5));
    }

    public static void addPveXp(ServerPlayer player, int amount) {
        player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data -> {
            data.addPveXp(amount);
            checkLevelUp(player, data);
        });
    }

    private static void checkLevelUp(ServerPlayer player, PlayerProgressionData data) {
        while (data.getLevel() < MAX_LEVEL) {
            int needed = xpForLevel(data.getLevel());
            if (data.getPveXp() < needed) break;
            data.setPveXp(data.getPveXp() - needed);
            data.setLevel(data.getLevel() + 1);
            onLevelUp(player, data.getLevel());
        }
    }

    private static void onLevelUp(ServerPlayer player, int newLevel) {
        player.sendSystemMessage(Component.literal(
                "§a§lLEVEL UP! §eSeviye " + newLevel + " oldun!"));
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static int getLevel(ServerPlayer player) {
        return player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY)
                .map(PlayerProgressionData::getLevel)
                .orElse(1);
    }

    public static int getPveXp(ServerPlayer player) {
        return player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY)
                .map(PlayerProgressionData::getPveXp)
                .orElse(0);
    }
}
