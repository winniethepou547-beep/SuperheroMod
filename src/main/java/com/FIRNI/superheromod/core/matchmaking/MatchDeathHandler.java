package com.FIRNI.superheromod.core.matchmaking;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.world.PvpMapLocations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class MatchDeathHandler {

    private MatchDeathHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer deadPlayer)) return;

        ActiveMatch match = MatchManager.getMatchOf(deadPlayer.getUUID());
        if (match == null || match.isFinished()) return;

        event.setCanceled(true);

        deadPlayer.setHealth(deadPlayer.getMaxHealth());
        deadPlayer.getFoodData().setFoodLevel(20);

        // Oyuncuyu haritanin spawn noktasina geri isinla (void'e dusmesin)
        ServerLevel arenaLevel = deadPlayer.getServer().getLevel(PvpMapLocations.DIMENSION);
        if (arenaLevel != null) {
            deadPlayer.teleportTo(arenaLevel,
                    match.getMap().spawn().getX() + 0.5,
                    match.getMap().spawn().getY() + 1,
                    match.getMap().spawn().getZ() + 0.5,
                    deadPlayer.getYRot(), deadPlayer.getXRot());
        }

        DamageSource source = event.getSource();
        Entity attackerEntity = source.getEntity();
        ServerPlayer killer = (attackerEntity instanceof ServerPlayer sp) ? sp : null;

        MatchManager.onPlayerDeath(deadPlayer, killer, deadPlayer.getServer());
    }
}
