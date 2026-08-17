package com.FIRNI.superheromod.core.combat;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class CombatTagManager {

    private static final int COMBAT_DURATION_TICKS = 300; // 15 seconds
    private static final Map<UUID, Integer> COMBAT_TAGS = new ConcurrentHashMap<>();

    public static boolean isInCombat(UUID playerId) {
        return COMBAT_TAGS.containsKey(playerId);
    }

    public static void tag(ServerPlayer player) {
        boolean wasTagged = COMBAT_TAGS.containsKey(player.getUUID());
        COMBAT_TAGS.put(player.getUUID(), COMBAT_DURATION_TICKS);
        if (!wasTagged) {
            player.displayClientMessage(Component.literal("§c⚔ Savas moduna girdin! 15 saniye boyunca kacilamaz."), true);
        }
    }

    public static void remove(UUID playerId) {
        COMBAT_TAGS.remove(playerId);
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
                tag(victim);
                tag(attacker);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        COMBAT_TAGS.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                return true;
            }
            entry.setValue(remaining);
            return false;
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isInCombat(player.getUUID())) {
                player.kill();
                remove(player.getUUID());
            }
        }
    }
}
