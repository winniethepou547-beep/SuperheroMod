package com.FIRNI.superheromod.core.region;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Kup seklinde (cuboid) bir alan. PVP/PVE giris bolgeleri bunu kullanir,
 * ileride maç arenalari da ayni sistemle tanimlanacak.
 */
public class Region {

    private final String name;
    private final RegionType type;
    private final ResourceLocation dimension;
    private final BlockPos min;
    private final BlockPos max;

    public Region(String name, RegionType type, ResourceLocation dimension, BlockPos corner1, BlockPos corner2) {
        this.name = name;
        this.type = type;
        this.dimension = dimension;
        this.min = new BlockPos(
                Math.min(corner1.getX(), corner2.getX()),
                Math.min(corner1.getY(), corner2.getY()),
                Math.min(corner1.getZ(), corner2.getZ()));
        this.max = new BlockPos(
                Math.max(corner1.getX(), corner2.getX()),
                Math.max(corner1.getY(), corner2.getY()),
                Math.max(corner1.getZ(), corner2.getZ()));
    }

    public boolean contains(ResourceLocation playerDimension, BlockPos pos) {
        if (!dimension.equals(playerDimension)) return false;
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public String getName() {
        return name;
    }

    public RegionType getType() {
        return type;
    }

    public ResourceLocation getDimension() {
        return dimension;
    }

    public BlockPos getMin() {
        return min;
    }

    public BlockPos getMax() {
        return max;
    }
}
