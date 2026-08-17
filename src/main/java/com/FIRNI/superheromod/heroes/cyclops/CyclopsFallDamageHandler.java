package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SHIFT (Propulsion Burst) sonrasi ilk yere inisde dusme hasarini engeller.
 * Muafiyet tek seferlik: oyuncu yere degdigi anda tuketilir.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class CyclopsFallDamageHandler {

    private static final Set<UUID> fallImmune = ConcurrentHashMap.newKeySet();

    public static void grantFallImmunity(UUID playerId) {
        fallImmune.add(playerId);
    }

    public static void clearFallImmunity(UUID playerId) {
        fallImmune.remove(playerId);
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!fallImmune.remove(player.getUUID())) return;

        event.setCanceled(true);
        player.fallDistance = 0f;
    }
}
