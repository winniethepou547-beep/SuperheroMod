package com.FIRNI.superheromod.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Bir yuzeye minik patlama acar: carpma noktasinin cevresindeki bloklar
 * kirilir, yerine patlama partikulu ve kirilma sesi gelir.
 *
 * Vanilla Explosion kullanilmiyor — o hem esyalari yakar, hem entity'leri
 * savurur, hem de deligi kure seklinde acar. Burada sadece YUZEY oyuluyor.
 */
public final class SurfaceBreaker {

    private SurfaceBreaker() {}

    /**
     * @param point     carpma noktasi (dunya koordinati)
     * @param radius    kirilma yaricapi (blok)
     * @param maxBlocks tek seferde kirilacak azami blok
     */
    public static void chip(ServerLevel level, Player source, Vec3 point,
                            double radius, int maxBlocks) {
        BlockPos center = BlockPos.containing(point);
        int r = (int) Math.ceil(radius);
        int broken = 0;

        outer:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (broken >= maxBlocks) break outer;

                    BlockPos pos = center.offset(dx, dy, dz);
                    Vec3 blockCenter = new Vec3(
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (blockCenter.distanceTo(point) > radius) continue;

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    // Kirilmaz blok (bedrock, barrier vb.) atlanir
                    if (state.getDestroySpeed(level, pos) < 0) continue;

                    level.destroyBlock(pos, false, source);
                    broken++;
                }
            }
        }

        if (broken == 0) return;

        level.sendParticles(ParticleTypes.EXPLOSION,
                point.x, point.y, point.z, 1, 0.1, 0.1, 0.1, 0.0);
        level.sendParticles(ParticleTypes.SMALL_FLAME,
                point.x, point.y, point.z, 6, 0.25, 0.25, 0.25, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                point.x, point.y, point.z, 5, 0.3, 0.3, 0.3, 0.01);

        level.playSound(null, BlockPos.containing(point),
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.45f, 1.7f);
    }

    /** Bos hava mi — kirilacak bir sey var mi diye hizli bakis. */
    public static boolean hasSolid(ServerLevel level, Vec3 point) {
        BlockState state = level.getBlockState(BlockPos.containing(point));
        return !state.isAir() && state.getBlock() != Blocks.AIR;
    }
}
