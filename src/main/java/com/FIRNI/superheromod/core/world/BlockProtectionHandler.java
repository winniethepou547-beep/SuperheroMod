package com.FIRNI.superheromod.core.world;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BlockProtectionHandler {

    private BlockProtectionHandler() {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        // OP oyuncular her yerde kirabilir
        if (player.hasPermissions(2)) return;

        Level level = player.level();

        // pvp_arena boyutundaysa blok kirmaya izin ver (mac icinde)
        if (level.dimension().equals(PvpMapLocations.DIMENSION)) return;

        // Overworld'deyse: hub/pvp-lobby/pve alani icinde blok kirmayi engelle
        if (level.dimension().equals(Level.OVERWORLD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2)) return;

        Level level = player.level();

        if (level.dimension().equals(PvpMapLocations.DIMENSION)) return;

        if (level.dimension().equals(Level.OVERWORLD)) {
            event.setCanceled(true);
        }
    }
}
