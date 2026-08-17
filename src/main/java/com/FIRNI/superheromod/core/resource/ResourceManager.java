package com.FIRNI.superheromod.core.resource;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class ResourceManager {

    private static final Map<UUID, Map<String, ResourceBar>> playerResources = new HashMap<>();

    public static void register(UUID playerId, String resourceId, ResourceBar bar) {
        playerResources.computeIfAbsent(playerId, k -> new HashMap<>()).put(resourceId, bar);
    }

    public static void remove(UUID playerId) {
        playerResources.remove(playerId);
    }

    public static ResourceBar get(UUID playerId, String resourceId) {
        Map<String, ResourceBar> bars = playerResources.get(playerId);
        if (bars == null) return null;
        return bars.get(resourceId);
    }

    public static Map<String, ResourceBar> getAll(UUID playerId) {
        return playerResources.getOrDefault(playerId, Collections.emptyMap());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Map<String, ResourceBar> bars : playerResources.values()) {
            for (ResourceBar bar : bars.values()) {
                bar.tick();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        remove(event.getEntity().getUUID());
    }
}
