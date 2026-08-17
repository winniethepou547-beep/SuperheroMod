package com.FIRNI.superheromod.core.combat.raycast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class RaycastSystem {

    public static Vec3 getEyeOrigin(Entity entity) {
        return entity.getEyePosition(1.0f);
    }

    public static Vec3 getLookDirection(Entity entity) {
        return entity.getLookAngle();
    }

    public static RaycastResult cast(Level level, Entity caster, double maxDistance,
                                     float beamRadius, boolean piercing,
                                     Predicate<Entity> entityFilter) {
        Vec3 origin = getEyeOrigin(caster);
        Vec3 direction = getLookDirection(caster);
        return cast(level, caster, origin, direction, maxDistance, beamRadius, piercing, entityFilter);
    }

    public static RaycastResult cast(Level level, Entity caster, Vec3 origin, Vec3 direction,
                                     double maxDistance, float beamRadius, boolean piercing,
                                     Predicate<Entity> entityFilter) {
        Vec3 end = origin.add(direction.scale(maxDistance));

        BlockHitResult blockHit = level.clip(new ClipContext(
                origin, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                caster
        ));

        double blockDist = maxDistance;
        boolean hitBlock = false;
        BlockPos blockPos = null;
        Direction blockFace = null;

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            blockDist = blockHit.getLocation().distanceTo(origin);
            hitBlock = true;
            blockPos = blockHit.getBlockPos();
            blockFace = blockHit.getDirection();
        }

        List<RaycastResult.EntityHit> entityHits = new ArrayList<>();
        double searchDist = hitBlock ? blockDist : maxDistance;

        AABB searchBox = caster.getBoundingBox()
                .expandTowards(direction.scale(searchDist))
                .inflate(beamRadius + 1.0);

        for (Entity entity : level.getEntities(caster, searchBox)) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entityFilter != null && !entityFilter.test(entity)) continue;

            AABB entityBox = entity.getBoundingBox().inflate(beamRadius);
            var optional = entityBox.clip(origin, end);

            if (optional.isPresent()) {
                Vec3 hitPos = optional.get();
                double dist = origin.distanceTo(hitPos);
                if (dist <= searchDist) {
                    entityHits.add(new RaycastResult.EntityHit(entity, hitPos, dist));
                    if (!piercing) break;
                }
            }
        }

        entityHits.sort(Comparator.comparingDouble(RaycastResult.EntityHit::getDistance));

        Vec3 finalHitPos;
        double finalDist;

        if (!entityHits.isEmpty() && !piercing) {
            RaycastResult.EntityHit closest = entityHits.get(0);
            if (closest.getDistance() < blockDist) {
                finalHitPos = closest.getHitPos();
                finalDist = closest.getDistance();
                hitBlock = false;
                blockPos = null;
                blockFace = null;
            } else {
                finalHitPos = blockHit.getLocation();
                finalDist = blockDist;
            }
        } else if (hitBlock) {
            finalHitPos = blockHit.getLocation();
            finalDist = blockDist;
        } else {
            finalHitPos = end;
            finalDist = maxDistance;
        }

        return new RaycastResult(origin, direction, finalHitPos, finalDist,
                hitBlock, blockPos, blockFace, entityHits);
    }

    // Ricochet: reflect direction using surface normal
    public static Vec3 reflect(Vec3 direction, Direction face) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        return direction.subtract(normal.scale(2.0 * direction.dot(normal))).normalize();
    }
}
