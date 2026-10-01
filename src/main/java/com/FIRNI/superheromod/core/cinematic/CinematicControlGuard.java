package com.FIRNI.superheromod.core.cinematic;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=SuperheroMod.MODID)
public final class CinematicControlGuard {
    @SubscribeEvent public static void attack(AttackEntityEvent event) {
        if(CinematicDirector.isBusy(event.getEntity().getUUID()))event.setCanceled(true);
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickItem event) {
        if(CinematicDirector.isBusy(event.getEntity().getUUID()))event.setCanceled(true);
    }
    @SubscribeEvent public static void block(PlayerInteractEvent.RightClickBlock event) {
        if(CinematicDirector.isBusy(event.getEntity().getUUID()))event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        CinematicDirector.abort(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        CinematicDirector.abort(event.getEntity().getUUID());
    }
}
