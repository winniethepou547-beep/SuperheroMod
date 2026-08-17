package com.FIRNI.superheromod.core.world;

import com.FIRNI.superheromod.core.region.Region;
import com.FIRNI.superheromod.core.region.RegionManager;
import com.FIRNI.superheromod.core.region.RegionType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hub + PVP + PVE duz meydanlarini insa eder ve aralarindaki portal bolgelerini kaydeder.
 * /superhero setup komutuyla tetiklenir, tekrar calistirilirsa ayni yerleri gunceller (idempotent).
 */
public final class HubBuilder {

    private HubBuilder() {
    }

    public static void setupWorld(ServerLevel level) {
        buildPlatform(level, ArenaLocations.HUB_CENTER, Blocks.COBBLESTONE.defaultBlockState());
        buildPlatform(level, ArenaLocations.PVP_CENTER, Blocks.STONE_BRICKS.defaultBlockState());
        buildPlatform(level, ArenaLocations.PVE_CENTER, Blocks.MOSSY_COBBLESTONE.defaultBlockState());

        level.setDefaultSpawnPos(ArenaLocations.HUB_SPAWN, 0f);

        // Buyuk PVP haritalari artik ozel bos/duz "pvp_arena" boyutunda (bkz. PvpMapLocations),
        // Overworld'e hic dokunmuyorlar - chunk uretimi/isiklandirma neredeyse bedava.
        ServerLevel arenaLevel = level.getServer().getLevel(PvpMapLocations.DIMENSION);
        if (arenaLevel == null) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("HATA: pvp_arena boyutu bulunamadi - dimension JSON dosyalarini kontrol et."), false);
        } else {
            // Tek tick'te degil, arka planda birkac saniyeye yayilarak insa edilir (bkz. ChunkedWorldEditor).
            ChunkedWorldEditor.run(PvpMapBuilder.buildAllJobs(arenaLevel), () ->
                    level.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("Buyuk PVP haritalari hazir!"), false));
        }

        ResourceLocation overworld = Level.OVERWORLD.location();

        registerRegion("hub_to_pvp_portal", RegionType.PVP_ENTRY, overworld,
                ArenaLocations.HUB_TO_PVP_PORTAL_MIN, ArenaLocations.HUB_TO_PVP_PORTAL_MAX);
        registerRegion("hub_to_pve_portal", RegionType.PVE_ENTRY, overworld,
                ArenaLocations.HUB_TO_PVE_PORTAL_MIN, ArenaLocations.HUB_TO_PVE_PORTAL_MAX);
        registerRegion("pvp_to_hub_portal", RegionType.MAIN_HALL_ENTRY, overworld,
                ArenaLocations.PVP_TO_HUB_PORTAL_MIN, ArenaLocations.PVP_TO_HUB_PORTAL_MAX);
        registerRegion("pve_to_hub_portal", RegionType.MAIN_HALL_ENTRY, overworld,
                ArenaLocations.PVE_TO_HUB_PORTAL_MIN, ArenaLocations.PVE_TO_HUB_PORTAL_MAX);
    }

    private static void registerRegion(String name, RegionType type, ResourceLocation dimension, BlockPos min, BlockPos max) {
        RegionManager.remove(name);
        RegionManager.register(new Region(name, type, dimension, min, max));
    }

    private static void buildPlatform(ServerLevel level, BlockPos center, BlockState floorBlock) {
        int radius = ArenaLocations.PLATFORM_RADIUS;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                level.setBlock(center.offset(x, 0, z), floorBlock, 2);
            }
        }
    }
}
