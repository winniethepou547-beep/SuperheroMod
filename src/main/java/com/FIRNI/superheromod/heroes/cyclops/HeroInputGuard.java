package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ulti Q tusunda; Q ayni zamanda vanilla "esya at" tusu. Bir karaktere atanmis
 * oyuncularda esya dusurmeyi engelleyerek cakismayi kaldirir.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class HeroInputGuard {

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        var player = event.getPlayer();
        if (player == null) return;
        if (AbilityManager.getCharacterId(player.getUUID()) == null) return;

        event.setCanceled(true);
        // Iptal edilen esya yere dusmesin, envantere geri konsun
        player.getInventory().add(event.getEntity().getItem());
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        var player = event.getEntity();
        if (player == null) return;
        if (AbilityManager.getCharacterId(player.getUUID()) == null) return;

        event.setCanceled(true);
    }
}
