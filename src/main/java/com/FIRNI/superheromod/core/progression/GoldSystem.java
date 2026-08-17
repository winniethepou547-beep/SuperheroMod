package com.FIRNI.superheromod.core.progression;

import net.minecraft.server.level.ServerPlayer;

/** Framework yardimcisi: mac odulu / PVE odulu gold vermek icin bunu cagirir. */
public class GoldSystem {

    public static void addGold(ServerPlayer player, int amount) {
        player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data -> data.addGold(amount));
    }

    public static int getGold(ServerPlayer player) {
        return player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY)
                .map(PlayerProgressionData::getGold)
                .orElse(0);
    }
}
