package com.FIRNI.superheromod.core.world;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.entity.PlayerSummoned;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
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

    /**
     * Hub'da SADECE DOGAL mob dogusunu engeller.
     *
     * Onceki surum EntityJoinLevelEvent uzerinden her Mob'u kesiyordu ve
     * dogus SEBEBINI bilmedigi icin oyuncunun bilerek koydugu her seyi de
     * engelliyordu: dogus yumurtalari, komutla cagrilanlar, mod'un kendi
     * summonlari. Ustelik iptal edilen olay hicbir uyari yazmadigi icin
     * "hicbir sey olmuyor" gibi gorunuyordu.
     *
     * FinalizeSpawn dogus SEBEBINI tasiyor; artik sadece kendiliginden
     * olusan dogus turleri engelleniyor.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!isNaturalSpawn(event.getSpawnType())) return;

        Mob mob = event.getEntity();
        if (mob instanceof PlayerSummoned) return;

        if (isInHub(mob.blockPosition(), mob.level())) {
            event.setSpawnCancelled(true);
        }
    }

    /**
     * Kendiliginden olusan dogus turleri.
     *
     * Bunlarin disindaki her sey (SPAWN_EGG, COMMAND, MOB_SUMMONED,
     * DISPENSER, BUCKET, BREEDING...) oyuncunun BILEREK yaptigi bir islem —
     * hub'da da calismali.
     */
    private static boolean isNaturalSpawn(MobSpawnType type) {
        return type == MobSpawnType.NATURAL
                || type == MobSpawnType.CHUNK_GENERATION
                || type == MobSpawnType.SPAWNER
                || type == MobSpawnType.PATROL
                || type == MobSpawnType.REINFORCEMENT;
    }

    private static boolean isInHub(BlockPos pos, Level level) {
        if (level.dimension() != Level.OVERWORLD) return false;
        BlockPos hub = ArenaLocations.HUB_CENTER;
        return Math.abs(pos.getX() - hub.getX()) <= HUB_RADIUS
                && Math.abs(pos.getZ() - hub.getZ()) <= HUB_RADIUS;
    }
}
