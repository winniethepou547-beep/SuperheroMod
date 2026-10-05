package com.FIRNI.superheromod.heroes.batman;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The flash grenade's flight, shared by the server (which decides) and the clients (which draw their own copy and are
 * corrected at every real bounce, FX_PELLET_BOUNCE). Thrown, it falls, bounces off whatever it meets with a little of
 * its speed, skids, rolls to a stop; the fuse starts the first time it touches the floor and it goes off FUSE later
 * (BatmanConfig.FLASH_FUSE_SECONDS). No damage at all: what it does is light, the white-out and the ringing.
 */
public final class BatmanFlash {
    private BatmanFlash() {}

    /** If it never finds a floor (thrown off a cliff), it goes off in the air after this many ticks. */
    public static final int MAX_AGE = 100;
    static final double GRAVITY = .05, DRAG = .99;
    /** What a bounce keeps of the speed into the surface, and along it; rolling on the floor keeps ROLL a tick. */
    static final double BOUNCE = .32, SKID = .6, ROLL = .72;
    /** Slower than this into the floor is lying on it (rolling), not a bounce. */
    static final double SETTLE = .12;

    /** One tick of it: where it is, its velocity, whether it is on the floor, how hard it struck (0 = no bounce), at rest. */
    public record Step(Vec3 pos, Vec3 vel, boolean floor, double impact, boolean rest) {}

    public static Step step(Level level, Entity ignore, Vec3 pos, Vec3 vel) {
        Vec3 next = pos.add(vel);
        BlockHitResult bh = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ignore));
        if (bh.getType() == HitResult.Type.MISS) return new Step(next, vel.add(0, -GRAVITY, 0).scale(DRAG), false, 0, false);
        Vec3 n = Vec3.atLowerCornerOf(bh.getDirection().getNormal());
        double into = -vel.dot(n);
        Vec3 along = vel.add(n.scale(into));
        boolean floor = n.y > .5;
        Vec3 at = bh.getLocation().add(n.scale(.07));
        if (floor && into < SETTLE) {
            Vec3 roll = along.scale(ROLL);
            boolean rest = roll.lengthSqr() < 1.5e-4;
            return new Step(at, rest ? Vec3.ZERO : roll, true, 0, rest);
        }
        return new Step(at, along.scale(SKID).add(n.scale(Math.max(0, into) * BOUNCE)), floor, Math.max(0, into), false);
    }
}
