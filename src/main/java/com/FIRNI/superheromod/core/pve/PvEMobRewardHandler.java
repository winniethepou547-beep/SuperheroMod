package com.FIRNI.superheromod.core.pve;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.progression.GoldSystem;
import com.FIRNI.superheromod.core.progression.LevelSystem;
import com.FIRNI.superheromod.core.world.ArenaLocations;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class PvEMobRewardHandler {

    private static final int PVE_REGION_X = ArenaLocations.PVE_CENTER.getX();
    private static final int PVE_RADIUS = 500;

    private static final int BASE_MOB_GOLD = 5;
    private static final int BASE_MOB_XP = 15;

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Monster)) return;
        if (entity.level().isClientSide()) return;

        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        if (!isInPvEArea(entity.blockPosition(), entity.level())) return;

        int goldReward = BASE_MOB_GOLD + (int)(entity.getMaxHealth() / 5);
        int xpReward = BASE_MOB_XP + (int)(entity.getMaxHealth() / 3);

        GoldSystem.addGold(player, goldReward);
        LevelSystem.addPveXp(player, xpReward);

        player.displayClientMessage(Component.literal(
                "§7+" + goldReward + " §6Altin §7| §7+" + xpReward + " §bXP"), true);
    }

    private static boolean isInPvEArea(BlockPos pos, Level level) {
        if (level.dimension() != Level.OVERWORLD) return false;
        return Math.abs(pos.getX() - PVE_REGION_X) < PVE_RADIUS
                && Math.abs(pos.getZ()) < PVE_RADIUS;
    }
}
