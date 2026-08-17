package com.FIRNI.superheromod.core.world;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class HubProtectionHandler {

    private static final int HUB_RADIUS = ArenaLocations.PLATFORM_RADIUS + 10;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (victim.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof Player)) return;

        if (isInHub(victim.blockPosition(), victim.level())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntitySpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof Mob)) return;

        if (isInHub(entity.blockPosition(), entity.level())) {
            event.setCanceled(true);
        }
    }

    private static boolean isInHub(BlockPos pos, Level level) {
        if (level.dimension() != Level.OVERWORLD) return false;
        BlockPos hub = ArenaLocations.HUB_CENTER;
        return Math.abs(pos.getX() - hub.getX()) <= HUB_RADIUS
                && Math.abs(pos.getZ() - hub.getZ()) <= HUB_RADIUS;
    }
}
