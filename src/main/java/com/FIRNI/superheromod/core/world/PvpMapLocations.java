package com.FIRNI.superheromod.core.world;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Buyuk PVP mac haritalarinin (300x300) koordinatlari. Uctan uca ucus/kacis
 * yetenekleri icin genis tutuldu, gorunmez (Barrier) duvarlarla sinirlanacak.
 *
 * Ozel, bombos/duz "pvp_arena" boyutunda yasarlar (bkz. data/superheromod/dimension) -
 * normal Overworld arazisiyle hicbir iliskileri yok, bu yuzden chunk uretimi/isiklandirma
 * neredeyse bedava (Overworld'deki uzak koordinatlarda kurulunca ciddi lag'e sebep oluyordu).
 */
public final class PvpMapLocations {

    private PvpMapLocations() {
    }

    public static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(SuperheroMod.MODID, "pvp_arena"));

    public static final int MAP_RADIUS = 150; // 300x300
    public static final int WALL_HEIGHT = 24; // ucus icin yeterli
    public static final int FLOOR_Y = 100;

    // Izole bir boyutun icindeler, uzak koordinatlara gerek yok - cakisma riski sifir.
    public static final BlockPos CITY_CENTER = new BlockPos(0, FLOOR_Y, 0);
    public static final BlockPos HARBOR_CENTER = new BlockPos(1000, FLOOR_Y, 0);
    public static final BlockPos CANYON_CENTER = new BlockPos(-1000, FLOOR_Y, 0);

    public static final BlockPos CITY_SPAWN = CITY_CENTER.above();
    public static final BlockPos HARBOR_SPAWN = HARBOR_CENTER.above();
    public static final BlockPos CANYON_SPAWN = CANYON_CENTER.above();
}
