package com.FIRNI.superheromod.core.world;

import net.minecraft.core.BlockPos;

/**
 * Hub/PVP/PVE meydanlarinin koordinatlari icin tek kaynak.
 * HubBuilder platformlari buraya gore insa eder, RegionTickHandler
 * teleport hedeflerini buradan okur - iki yerde ayni sayi tekrar etmesin diye.
 */
public final class ArenaLocations {

    private ArenaLocations() {
    }

    public static final int PLATFORM_RADIUS = 12; // 25x25 duz platform
    public static final int FLOOR_Y = 100;

    public static final BlockPos HUB_CENTER = new BlockPos(0, FLOOR_Y, 0);
    public static final BlockPos PVP_CENTER = new BlockPos(1000, FLOOR_Y, 0);
    public static final BlockPos PVE_CENTER = new BlockPos(-1000, FLOOR_Y, 0);

    public static final BlockPos HUB_SPAWN = HUB_CENTER.above();
    public static final BlockPos PVP_SPAWN = PVP_CENTER.above();
    public static final BlockPos PVE_SPAWN = PVE_CENTER.above();

    // Portal bolgeleri (yurudugunde tetiklenen 3x3xN kutular)
    public static final BlockPos HUB_TO_PVP_PORTAL_MIN = HUB_CENTER.offset(10, 1, -1);
    public static final BlockPos HUB_TO_PVP_PORTAL_MAX = HUB_CENTER.offset(12, 3, 1);

    public static final BlockPos HUB_TO_PVE_PORTAL_MIN = HUB_CENTER.offset(-12, 1, -1);
    public static final BlockPos HUB_TO_PVE_PORTAL_MAX = HUB_CENTER.offset(-10, 3, 1);

    public static final BlockPos PVP_TO_HUB_PORTAL_MIN = PVP_CENTER.offset(-12, 1, -1);
    public static final BlockPos PVP_TO_HUB_PORTAL_MAX = PVP_CENTER.offset(-10, 3, 1);

    public static final BlockPos PVE_TO_HUB_PORTAL_MIN = PVE_CENTER.offset(10, 1, -1);
    public static final BlockPos PVE_TO_HUB_PORTAL_MAX = PVE_CENTER.offset(12, 3, 1);
}
