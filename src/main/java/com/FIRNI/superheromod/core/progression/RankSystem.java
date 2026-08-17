package com.FIRNI.superheromod.core.progression;

import net.minecraft.server.level.ServerPlayer;

public class RankSystem {

    public static void addRank(ServerPlayer player, int amount) {
        player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data -> data.addPvpRating(amount));
    }

    public static int getRank(ServerPlayer player) {
        return player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY)
                .map(PlayerProgressionData::getPvpRating)
                .orElse(1000);
    }
}
