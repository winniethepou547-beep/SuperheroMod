package com.FIRNI.superheromod.core.world;

import com.FIRNI.superheromod.core.matchmaking.GameMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PvpMapBuilder {

    private PvpMapBuilder() {
    }

    private static final Map<String, BlockPos> MAP_ID_TO_CENTER = Map.of(
            "sehir", PvpMapLocations.CITY_CENTER,
            "liman", PvpMapLocations.HARBOR_CENTER,
            "kanyon", PvpMapLocations.CANYON_CENTER
    );

    private static final Map<String, BlockState> MAP_ID_TO_FLOOR = Map.of(
            "sehir", Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
            "liman", Blocks.CYAN_CONCRETE.defaultBlockState()
    );

    public static List<Runnable> buildSingleMapJobs(ServerLevel level, GameMap map) {
        if ("kanyon".equals(map.id())) {
            return CanyonTerrainGenerator.generateJobs(level, PvpMapLocations.CANYON_CENTER);
        }
        // sehir: imported map, skip auto-build
        if ("sehir".equals(map.id())) {
            return List.of();
        }

        List<Runnable> jobs = new ArrayList<>();
        BlockPos center = MAP_ID_TO_CENTER.get(map.id());
        BlockState floor = MAP_ID_TO_FLOOR.get(map.id());
        if (center != null && floor != null) {
            queueFlatMap(jobs, level, center, floor);
        }
        return jobs;
    }

    public static List<Runnable> buildAllJobs(ServerLevel level) {
        List<Runnable> jobs = new ArrayList<>();
        // sehir: imported map, skip auto-build
        queueFlatMap(jobs, level, PvpMapLocations.HARBOR_CENTER, Blocks.CYAN_CONCRETE.defaultBlockState());
        jobs.addAll(CanyonTerrainGenerator.generateJobs(level, PvpMapLocations.CANYON_CENTER));
        return jobs;
    }

    private static void queueFlatMap(List<Runnable> jobs, ServerLevel level, BlockPos center, BlockState floorBlock) {
        int radius = PvpMapLocations.MAP_RADIUS;
        queueFloor(jobs, level, center, radius, floorBlock);
        queueBarrierWalls(jobs, level, center, radius, PvpMapLocations.WALL_HEIGHT);
    }

    private static void queueFloor(List<Runnable> jobs, ServerLevel level, BlockPos center, int radius, BlockState floorBlock) {
        for (int x = -radius; x <= radius; x++) {
            int fx = x;
            jobs.add(() -> {
                for (int z = -radius; z <= radius; z++) {
                    level.setBlock(center.offset(fx, 0, z), floorBlock, 2);
                }
            });
        }
    }

    private static void queueBarrierWalls(List<Runnable> jobs, ServerLevel level, BlockPos center, int radius, int height) {
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        for (int y = 1; y <= height; y++) {
            int fy = y;
            jobs.add(() -> {
                for (int x = -radius; x <= radius; x++) {
                    level.setBlock(center.offset(x, fy, -radius), barrier, 2);
                    level.setBlock(center.offset(x, fy, radius), barrier, 2);
                }
                for (int z = -radius; z <= radius; z++) {
                    level.setBlock(center.offset(-radius, fy, z), barrier, 2);
                    level.setBlock(center.offset(radius, fy, z), barrier, 2);
                }
            });
        }
    }
}
