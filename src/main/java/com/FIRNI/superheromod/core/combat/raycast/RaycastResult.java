package com.FIRNI.superheromod.core.combat.raycast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public class RaycastResult {

    private final Vec3 origin;
    private final Vec3 direction;
    private final Vec3 hitPosition;
    private final double distance;

    private final boolean hitBlock;
    @Nullable private final BlockPos blockPos;
    @Nullable private final Direction blockFace;

    private final List<EntityHit> entityHits;

    public RaycastResult(Vec3 origin, Vec3 direction, Vec3 hitPosition, double distance,
                         boolean hitBlock, @Nullable BlockPos blockPos, @Nullable Direction blockFace,
                         List<EntityHit> entityHits) {
        this.origin = origin;
        this.direction = direction;
        this.hitPosition = hitPosition;
        this.distance = distance;
        this.hitBlock = hitBlock;
        this.blockPos = blockPos;
        this.blockFace = blockFace;
        this.entityHits = entityHits;
    }

    public Vec3 getOrigin() { return origin; }
    public Vec3 getDirection() { return direction; }
    public Vec3 getHitPosition() { return hitPosition; }
    public double getDistance() { return distance; }
    public boolean didHitBlock() { return hitBlock; }
    @Nullable public BlockPos getBlockPos() { return blockPos; }
    @Nullable public Direction getBlockFace() { return blockFace; }
    public List<EntityHit> getEntityHits() { return entityHits; }
    public boolean didHitEntity() { return !entityHits.isEmpty(); }

    public static class EntityHit {
        private final Entity entity;
        private final Vec3 hitPos;
        private final double distance;

        public EntityHit(Entity entity, Vec3 hitPos, double distance) {
            this.entity = entity;
            this.hitPos = hitPos;
            this.distance = distance;
        }

        public Entity getEntity() { return entity; }
        public Vec3 getHitPos() { return hitPos; }
        public double getDistance() { return distance; }
    }
}
