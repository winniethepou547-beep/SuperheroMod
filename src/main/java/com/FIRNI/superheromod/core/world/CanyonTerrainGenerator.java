package com.FIRNI.superheromod.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class CanyonTerrainGenerator {

    private CanyonTerrainGenerator() {}

    private static final long SEED = 48271L;
    private static final int RADIUS = PvpMapLocations.MAP_RADIUS;
    private static final int SIZE = 2 * RADIUS + 1;
    private static final int RIVER_Y = 52;
    private static final int WALL_MIN_Y = 30;
    private static final int WALL_MAX_Y = 200;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState DIORITE = Blocks.DIORITE.defaultBlockState();
    private static final BlockState GRANITE = Blocks.GRANITE.defaultBlockState();
    private static final BlockState COBBLESTONE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();
    private static final BlockState OAK_LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState SPRUCE_LOG = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState OAK_LEAVES = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState SPRUCE_LEAVES = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    private static final BlockState FERN = Blocks.FERN.defaultBlockState();
    private static final BlockState DANDELION = Blocks.DANDELION.defaultBlockState();
    private static final BlockState POPPY = Blocks.POPPY.defaultBlockState();
    private static final BlockState CORNFLOWER = Blocks.CORNFLOWER.defaultBlockState();
    private static final BlockState STONE_BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY_STONE_BRICKS = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState CRACKED_STONE_BRICKS = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();
    private static final BlockState MOSS_BLOCK = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();
    private static final BlockState PODZOL = Blocks.PODZOL.defaultBlockState();

    public static List<Runnable> generateJobs(ServerLevel level, BlockPos center) {
        List<Runnable> jobs = new ArrayList<>();
        int[][] heightMap = computeHeightMap();
        boolean[][] steepMap = computeSteepMap(heightMap);

        // Pass 1: Clear old flat map (Y=95-130)
        for (int rx = -RADIUS; rx <= RADIUS; rx++) {
            int fRx = rx;
            jobs.add(() -> {
                int wx = center.getX() + fRx;
                for (int rz = -RADIUS; rz <= RADIUS; rz++) {
                    int wz = center.getZ() + rz;
                    for (int y = 95; y <= 130; y++) {
                        level.setBlock(new BlockPos(wx, y, wz), AIR, 2);
                    }
                }
            });
        }

        // Pass 2: Build terrain columns
        for (int rx = -RADIUS; rx <= RADIUS; rx++) {
            int fRx = rx;
            jobs.add(() -> buildRow(level, center, fRx, heightMap, steepMap));
        }

        // Pass 3: Trees (split into batches)
        for (int batch = 0; batch < 6; batch++) {
            int b = batch;
            jobs.add(() -> placeTreeBatch(level, center, heightMap, steepMap, b));
        }

        // Pass 4: Vegetation
        for (int batch = 0; batch < 4; batch++) {
            int b = batch;
            jobs.add(() -> placeVegetationBatch(level, center, heightMap, steepMap, b));
        }

        // Pass 5: Ruins
        jobs.add(() -> placeRuins(level, center, heightMap));

        // Pass 6: Caves
        jobs.add(() -> carveCaves(level, center, heightMap));

        // Pass 7: Barrier walls (from WALL_MIN_Y to WALL_MAX_Y)
        for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) {
            int fy = y;
            jobs.add(() -> {
                for (int i = -RADIUS; i <= RADIUS; i++) {
                    level.setBlock(new BlockPos(center.getX() + i, fy, center.getZ() - RADIUS), BARRIER, 2);
                    level.setBlock(new BlockPos(center.getX() + i, fy, center.getZ() + RADIUS), BARRIER, 2);
                    level.setBlock(new BlockPos(center.getX() - RADIUS, fy, center.getZ() + i), BARRIER, 2);
                    level.setBlock(new BlockPos(center.getX() + RADIUS, fy, center.getZ() + i), BARRIER, 2);
                }
            });
        }

        // Pass 8: Spawn platform (ensure solid ground at center)
        jobs.add(() -> {
            int spawnY = heightMap[RADIUS][RADIUS];
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, spawnY, center.getZ() + dz);
                    level.setBlock(pos, STONE, 2);
                    level.setBlock(pos.above(), GRASS_BLOCK, 2);
                    level.setBlock(pos.above(2), AIR, 2);
                    level.setBlock(pos.above(3), AIR, 2);
                }
            }
        });

        return jobs;
    }

    // ==================== HEIGHT MAP ====================

    private static int[][] computeHeightMap() {
        int[][] hm = new int[SIZE][SIZE];
        for (int rx = -RADIUS; rx <= RADIUS; rx++) {
            for (int rz = -RADIUS; rz <= RADIUS; rz++) {
                hm[rx + RADIUS][rz + RADIUS] = (int) getTerrainHeight(rx, rz);
            }
        }
        return hm;
    }

    private static boolean[][] computeSteepMap(int[][] hm) {
        boolean[][] steep = new boolean[SIZE][SIZE];
        for (int x = 1; x < SIZE - 1; x++) {
            for (int z = 1; z < SIZE - 1; z++) {
                int h = hm[x][z];
                int maxDiff = Math.max(
                    Math.max(Math.abs(h - hm[x-1][z]), Math.abs(h - hm[x+1][z])),
                    Math.max(Math.abs(h - hm[x][z-1]), Math.abs(h - hm[x][z+1]))
                );
                steep[x][z] = maxDiff > 3;
            }
        }
        return steep;
    }

    private static double getTerrainHeight(int rx, int rz) {
        double nx = rx / 150.0;
        double nz = rz / 150.0;

        // Base terrain
        double base = fractalNoise(rx * 0.007, rz * 0.007, 5, 0.5, 2.0, SEED);
        double height = 88 + base * 22;

        // North: tall mountains (Z < -40 → nz < -0.27)
        double northFactor = smoothClamp((-nz - 0.2) * 2.5);
        double mountainNoise = fractalNoise(rx * 0.01, rz * 0.01, 5, 0.45, 2.2, SEED + 100);
        double ridgeNoise = Math.abs(fractalNoise(rx * 0.008, rz * 0.008, 3, 0.5, 2.0, SEED + 150));
        height += (mountainNoise * 55 + ridgeNoise * 25) * northFactor;

        // South: deep canyon (Z > 40 → nz > 0.27)
        double southFactor = smoothClamp((nz - 0.15) * 2.0);
        double canyonWiggle = fractalNoise(rx * 0.005, rz * 0.003, 2, 0.5, 2.0, SEED + 500) * 0.25;
        double trenchDist = Math.abs(nx + canyonWiggle);
        double trench = Math.max(0, 1 - trenchDist * 2.8);
        trench = trench * trench;
        height -= trench * 48 * southFactor;
        double canyonWalls = fractalNoise(rx * 0.015, rz * 0.015, 3, 0.5, 2.0, SEED + 550);
        height += Math.max(0, canyonWalls * 20) * southFactor * (1 - trench);

        // East: rocky plateaus (X > 40 → nx > 0.27)
        double eastFactor = smoothClamp((nx - 0.25) * 2.0);
        double plateauNoise = fractalNoise(rx * 0.012, rz * 0.012, 3, 0.35, 2.0, SEED + 200);
        double plateauStep = plateauNoise > 0.25 ? 1.0 : (plateauNoise > 0 ? plateauNoise / 0.25 : 0);
        height += plateauStep * 38 * eastFactor;

        // West: forest hills (X < -40 → nx < -0.27)
        double westFactor = smoothClamp((-nx - 0.25) * 2.0);
        double forestNoise = fractalNoise(rx * 0.011, rz * 0.011, 4, 0.5, 2.0, SEED + 300);
        height += forestNoise * 18 * westFactor;

        // Detail noise
        height += fractalNoise(rx * 0.03, rz * 0.03, 3, 0.5, 2.0, SEED + 400) * 5;
        height += fractalNoise(rx * 0.06, rz * 0.06, 2, 0.5, 2.0, SEED + 450) * 2;

        // Center plateau (smooth transition to ~100 within 15 blocks of center)
        double centerDist = Math.sqrt(nx * nx + nz * nz);
        if (centerDist < 0.12) {
            double centerFactor = 1.0 - centerDist / 0.12;
            centerFactor = centerFactor * centerFactor;
            height = height * (1.0 - centerFactor) + 100.0 * centerFactor;
        }

        // Edge falloff
        double edgeDist = Math.min(
            Math.min(RADIUS + rx, RADIUS - rx),
            Math.min(RADIUS + rz, RADIUS - rz)
        );
        if (edgeDist < 12) {
            double ef = edgeDist / 12.0;
            height = height * ef + 75 * (1 - ef);
        }

        return Math.max(38, Math.min(178, height));
    }

    // ==================== TERRAIN BUILDING ====================

    private static void buildRow(ServerLevel level, BlockPos center, int rx, int[][] hm, boolean[][] steep) {
        int wx = center.getX() + rx;
        int hmX = rx + RADIUS;

        for (int rz = -RADIUS; rz <= RADIUS; rz++) {
            int wz = center.getZ() + rz;
            int hmZ = rz + RADIUS;
            int h = hm[hmX][hmZ];
            boolean isSteep = steep[hmX][hmZ];
            double nx = rx / 150.0;
            double nz = rz / 150.0;
            boolean isAlpine = h > 140;
            boolean isCanyonFloor = nz > 0.2 && h < 65;

            // Build column from bottom to terrain height
            for (int y = WALL_MIN_Y + 2; y <= h; y++) {
                BlockState block;
                int depth = h - y;

                if (depth > 6) {
                    block = getDeepStone(rx, y, rz);
                } else if (depth > 3) {
                    block = getStone(rx, y, rz);
                } else if (depth > 0) {
                    if (isAlpine || isSteep || isCanyonFloor) {
                        block = getStone(rx, y, rz);
                    } else {
                        block = DIRT;
                    }
                } else {
                    // Surface block
                    if (isAlpine) {
                        block = hashBool(rx, rz, SEED + 900) ? STONE : TUFF;
                    } else if (isCanyonFloor) {
                        block = h <= RIVER_Y + 1 ? CLAY : COARSE_DIRT;
                    } else if (isSteep) {
                        block = hashBool(rx, rz, SEED + 910) ? STONE : COBBLESTONE;
                    } else {
                        // Forest region uses podzol sometimes
                        if (nx < -0.35 && hashBool(rx, rz, SEED + 920)) {
                            block = PODZOL;
                        } else {
                            block = GRASS_BLOCK;
                        }
                    }
                }
                level.setBlock(new BlockPos(wx, y, wz), block, 2);
            }

            // Water in canyon
            if (h < RIVER_Y) {
                for (int y = h + 1; y <= RIVER_Y; y++) {
                    level.setBlock(new BlockPos(wx, y, wz), WATER, 2);
                }
            }

            // Clear old blocks above terrain (up to old barrier height + margin)
            for (int y = Math.max(h + 1, RIVER_Y + 1); y <= 130; y++) {
                level.setBlock(new BlockPos(wx, y, wz), AIR, 2);
            }
        }
    }

    private static BlockState getDeepStone(int rx, int y, int rz) {
        double v = hashDouble(rx + y * 7, rz, SEED + 800);
        if (y < 50) {
            return v < 0.3 ? TUFF : (v < 0.6 ? STONE : ANDESITE);
        }
        if (v < 0.15) return GRANITE;
        if (v < 0.3) return DIORITE;
        if (v < 0.45) return ANDESITE;
        return STONE;
    }

    private static BlockState getStone(int rx, int y, int rz) {
        double v = hashDouble(rx, y + rz * 13, SEED + 810);
        if (v < 0.2) return ANDESITE;
        if (v < 0.35) return COBBLESTONE;
        if (v < 0.45) return MOSSY_COBBLE;
        return STONE;
    }

    // ==================== TREES ====================

    private static void placeTreeBatch(ServerLevel level, BlockPos center, int[][] hm, boolean[][] steep, int batch) {
        Random r = new Random(SEED + 7000 + batch);
        int rowsPerBatch = SIZE / 6;
        int startRow = batch * rowsPerBatch;
        int endRow = (batch == 5) ? SIZE : (batch + 1) * rowsPerBatch;

        for (int hmX = startRow; hmX < endRow; hmX++) {
            int rx = hmX - RADIUS;
            double nx = rx / 150.0;
            for (int hmZ = 0; hmZ < SIZE; hmZ++) {
                int rz = hmZ - RADIUS;
                double nz = rz / 150.0;
                int h = hm[hmX][hmZ];
                if (steep[hmX][hmZ]) continue;
                if (h > 145 || h < 55) continue;

                double treeDensity = getTreeDensity(nx, nz, h);
                if (r.nextDouble() > treeDensity) continue;

                // Check space
                if (hmX < 3 || hmX >= SIZE - 3 || hmZ < 3 || hmZ >= SIZE - 3) continue;
                // Check not too close to center spawn
                if (Math.abs(rx) < 6 && Math.abs(rz) < 6) continue;

                int wx = center.getX() + rx;
                int wz = center.getZ() + rz;

                boolean isSpruce = nz < -0.15 && h > 95;
                if (isSpruce) {
                    placeSpruceTree(level, wx, h + 1, wz, r);
                } else {
                    placeOakTree(level, wx, h + 1, wz, r);
                }
            }
        }
    }

    private static double getTreeDensity(double nx, double nz, int h) {
        // West forest: dense
        if (nx < -0.3) return 0.08;
        // North mountains: moderate spruce
        if (nz < -0.2 && h > 90 && h < 135) return 0.04;
        // Canyon rim: sparse
        if (nz > 0.15 && h > 70) return 0.015;
        // East plateau: sparse
        if (nx > 0.3) return 0.012;
        // Center: moderate
        return 0.025;
    }

    private static void placeOakTree(ServerLevel level, int x, int baseY, int z, Random r) {
        int trunkH = 4 + r.nextInt(3);
        for (int y = 0; y < trunkH; y++) {
            level.setBlock(new BlockPos(x, baseY + y, z), OAK_LOG, 2);
        }
        int canopyR = 2;
        int canopyStart = trunkH - 2;
        for (int dy = canopyStart; dy <= trunkH + 1; dy++) {
            int radius = dy <= trunkH - 1 ? canopyR : (canopyR - 1);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0 && dy < trunkH) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && r.nextBoolean()) continue;
                    level.setBlock(new BlockPos(x + dx, baseY + dy, z + dz), OAK_LEAVES, 2);
                }
            }
        }
    }

    private static void placeSpruceTree(ServerLevel level, int x, int baseY, int z, Random r) {
        int trunkH = 6 + r.nextInt(4);
        for (int y = 0; y < trunkH; y++) {
            level.setBlock(new BlockPos(x, baseY + y, z), SPRUCE_LOG, 2);
        }
        // Pyramid canopy
        for (int dy = 2; dy <= trunkH; dy++) {
            int layer = trunkH - dy;
            int radius = Math.min(3, 1 + layer / 2);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (Math.abs(dx) + Math.abs(dz) > radius + 1) continue;
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius) continue;
                    level.setBlock(new BlockPos(x + dx, baseY + dy, z + dz), SPRUCE_LEAVES, 2);
                }
            }
        }
        // Top
        level.setBlock(new BlockPos(x, baseY + trunkH, z), SPRUCE_LEAVES, 2);
    }

    // ==================== VEGETATION ====================

    private static void placeVegetationBatch(ServerLevel level, BlockPos center, int[][] hm, boolean[][] steep, int batch) {
        Random r = new Random(SEED + 8000 + batch);
        int rowsPerBatch = SIZE / 4;
        int startRow = batch * rowsPerBatch;
        int endRow = (batch == 3) ? SIZE : (batch + 1) * rowsPerBatch;

        BlockState[] plants = {FERN, DANDELION, POPPY, CORNFLOWER, FERN, FERN};

        for (int hmX = startRow; hmX < endRow; hmX++) {
            int rx = hmX - RADIUS;
            double nx = rx / 150.0;
            for (int hmZ = 0; hmZ < SIZE; hmZ++) {
                int rz = hmZ - RADIUS;
                double nz = rz / 150.0;
                int h = hm[hmX][hmZ];
                if (steep[hmX][hmZ]) continue;
                if (h > 142 || h < 55) continue;

                double vegDensity = getVegetationDensity(nx, nz, h);
                if (r.nextDouble() > vegDensity) continue;

                int wx = center.getX() + rx;
                int wz = center.getZ() + rz;
                BlockState plant = plants[r.nextInt(plants.length)];
                level.setBlock(new BlockPos(wx, h + 1, wz), plant, 2);
            }
        }
    }

    private static double getVegetationDensity(double nx, double nz, int h) {
        if (nx < -0.3) return 0.25;
        if (nz > 0.2 && h > 60 && h < 85) return 0.15;
        if (nx > 0.3) return 0.06;
        return 0.12;
    }

    // ==================== CAVES ====================

    private static void carveCaves(ServerLevel level, BlockPos center, int[][] hm) {
        Random r = new Random(SEED + 9000);

        // Place 8-12 caves at various locations around mountains and canyon walls
        int caveCount = 8 + r.nextInt(5);
        for (int i = 0; i < caveCount; i++) {
            int rx = r.nextInt(SIZE) - RADIUS;
            int rz = r.nextInt(SIZE) - RADIUS;
            int hmX = rx + RADIUS;
            int hmZ = rz + RADIUS;
            if (hmX < 5 || hmX >= SIZE - 5 || hmZ < 5 || hmZ >= SIZE - 5) continue;

            int surfaceH = hm[hmX][hmZ];
            if (surfaceH < 70) continue;

            int caveY = surfaceH - 3 - r.nextInt(8);
            if (caveY < 45) continue;

            // Determine direction (roughly toward center)
            double angleToCenter = Math.atan2(-rz, -rx);
            double angle = angleToCenter + (r.nextDouble() - 0.5) * 1.5;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);

            int length = 8 + r.nextInt(12);
            for (int step = 0; step < length; step++) {
                int cx = (int) (rx + dx * step);
                int cz = (int) (rz + dz * step);
                int cy = caveY + (int) (Math.sin(step * 0.3) * 2);

                // Carve 2x3 or 3x3 area
                int caveRadius = step < 2 ? 2 : (1 + r.nextInt(2));
                for (int ddx = -caveRadius; ddx <= caveRadius; ddx++) {
                    for (int ddz = -caveRadius; ddz <= caveRadius; ddz++) {
                        for (int ddy = 0; ddy < 3; ddy++) {
                            int bx = center.getX() + cx + ddx;
                            int by = cy + ddy;
                            int bz = center.getZ() + cz + ddz;
                            if (Math.abs(cx + ddx) < RADIUS && Math.abs(cz + ddz) < RADIUS) {
                                level.setBlock(new BlockPos(bx, by, bz), AIR, 2);
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== RUINS ====================

    private static void placeRuins(ServerLevel level, BlockPos center, int[][] hm) {
        Random r = new Random(SEED + 6000);

        // Ruin locations (predefined relative positions)
        int[][] ruinSpots = {
            {-80, 30}, {60, -70}, {90, 50}, {-40, -90}, {20, 80},
            {-110, -20}, {100, -40}
        };

        for (int[] spot : ruinSpots) {
            int rx = spot[0];
            int rz = spot[1];
            int hmX = rx + RADIUS;
            int hmZ = rz + RADIUS;
            if (hmX < 5 || hmX >= SIZE - 5 || hmZ < 5 || hmZ >= SIZE - 5) continue;

            int h = hm[hmX][hmZ];
            if (h < 55 || h > 145) continue;

            int wx = center.getX() + rx;
            int wz = center.getZ() + rz;

            int type = r.nextInt(3);
            switch (type) {
                case 0 -> placeRuinedWall(level, wx, h + 1, wz, r);
                case 1 -> placeRuinedTower(level, wx, h + 1, wz, r);
                case 2 -> placeRuinedHouse(level, wx, h + 1, wz, r);
            }
        }
    }

    private static void placeRuinedWall(ServerLevel level, int x, int baseY, int z, Random r) {
        int length = 5 + r.nextInt(6);
        int height = 3 + r.nextInt(3);
        boolean alongX = r.nextBoolean();

        for (int i = 0; i < length; i++) {
            int wallH = height - (r.nextInt(3) == 0 ? r.nextInt(2) : 0);
            for (int dy = 0; dy < wallH; dy++) {
                BlockState block = getRuinBlock(r);
                int bx = alongX ? x + i : x;
                int bz = alongX ? z : z + i;
                level.setBlock(new BlockPos(bx, baseY + dy, bz), block, 2);
            }
        }
    }

    private static void placeRuinedTower(ServerLevel level, int x, int baseY, int z, Random r) {
        int height = 4 + r.nextInt(4);
        for (int dy = 0; dy < height; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1 && dy > height - 2 && r.nextBoolean()) continue;
                    if (dx == 0 && dz == 0 && dy > 0 && dy < height - 1) continue;
                    BlockState block = getRuinBlock(r);
                    level.setBlock(new BlockPos(x + dx, baseY + dy, z + dz), block, 2);
                }
            }
        }
    }

    private static void placeRuinedHouse(ServerLevel level, int x, int baseY, int z, Random r) {
        // Floor
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (r.nextInt(4) == 0) continue;
                level.setBlock(new BlockPos(x + dx, baseY, z + dz), getRuinBlock(r), 2);
            }
        }
        // Partial walls
        for (int dy = 1; dy <= 2 + r.nextInt(2); dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean isEdge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (!isEdge) continue;
                    if (r.nextInt(3) == 0) continue;
                    level.setBlock(new BlockPos(x + dx, baseY + dy, z + dz), getRuinBlock(r), 2);
                }
            }
        }
    }

    private static BlockState getRuinBlock(Random r) {
        int v = r.nextInt(10);
        if (v < 3) return COBBLESTONE;
        if (v < 5) return MOSSY_COBBLE;
        if (v < 7) return STONE_BRICKS;
        if (v < 8) return MOSSY_STONE_BRICKS;
        if (v < 9) return CRACKED_STONE_BRICKS;
        return MOSS_BLOCK;
    }

    // ==================== NOISE FUNCTIONS ====================

    private static double hash(int x, int z, long seed) {
        long n = x * 73856093L ^ z * 19349663L ^ seed;
        n = (n << 13) ^ n;
        n = n * (n * n * 15731L + 789221L) + 1376312589L;
        return (n & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL;
    }

    private static boolean hashBool(int x, int z, long seed) {
        return hash(x, z, seed) > 0.5;
    }

    private static double hashDouble(int x, int z, long seed) {
        return hash(x, z, seed);
    }

    private static double smoothNoise(double x, double z, long seed) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);

        double v00 = hash(ix, iz, seed);
        double v10 = hash(ix + 1, iz, seed);
        double v01 = hash(ix, iz + 1, seed);
        double v11 = hash(ix + 1, iz + 1, seed);

        double v0 = v00 + (v10 - v00) * fx;
        double v1 = v01 + (v11 - v01) * fx;
        return v0 + (v1 - v0) * fz;
    }

    private static double fractalNoise(double x, double z, int octaves, double persistence, double lacunarity, long seed) {
        double total = 0;
        double amplitude = 1;
        double frequency = 1;
        double maxVal = 0;
        for (int i = 0; i < octaves; i++) {
            total += (smoothNoise(x * frequency, z * frequency, seed + i * 31337L) * 2 - 1) * amplitude;
            maxVal += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        return total / maxVal;
    }

    private static double smoothClamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
