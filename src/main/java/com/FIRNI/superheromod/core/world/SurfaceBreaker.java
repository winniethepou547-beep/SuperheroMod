package com.FIRNI.superheromod.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bir yuzeye minik patlama acar: carpma noktasinin cevresindeki birkac blok
 * kirilir, yerine patlama partikulu ve kisa bir patlama sesi gelir.
 *
 * Vanilla Explosion kullanilmiyor — o esyalari yakar, entity savurur ve
 * deligi kure seklinde acar. Burada sadece YUZEY oyuluyor.
 */
public final class SurfaceBreaker {

    private SurfaceBreaker() {}

    /**
     * Carpma noktasina en yakin bloklardan baslayarak kirar; boylece delik
     * dagilmak yerine vurulan yerde toplanir.
     *
     * @param point     carpma noktasi (dunya koordinati)
     * @param radius    kirilma yaricapi (blok)
     * @param maxBlocks tek seferde kirilacak azami blok
     */
    public static void chip(ServerLevel level, Player source, Vec3 point,
                            double radius, int maxBlocks) {
        BlockPos center = BlockPos.containing(point);
        int r = (int) Math.ceil(radius);

        List<BlockPos> candidates = new ArrayList<>();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    Vec3 blockCenter = new Vec3(
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (blockCenter.distanceTo(point) > radius) continue;

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    // Kirilmaz blok (bedrock, barrier vb.) atlanir
                    if (state.getDestroySpeed(level, pos) < 0) continue;

                    candidates.add(pos);
                }
            }
        }

        if (candidates.isEmpty()) return;

        // Carpma noktasina yakin olanlar once kirilsin
        candidates.sort(Comparator.comparingDouble(pos -> new Vec3(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).distanceTo(point)));

        int broken = 0;
        for (BlockPos pos : candidates) {
            if (broken >= maxBlocks) break;
            level.destroyBlock(pos, false, source);
            broken++;
        }

        if (broken == 0) return;

        level.sendParticles(ParticleTypes.EXPLOSION,
                point.x, point.y, point.z, 1, 0.12, 0.12, 0.12, 0.0);
        level.sendParticles(ParticleTypes.SMALL_FLAME,
                point.x, point.y, point.z, 7, 0.25, 0.25, 0.25, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                point.x, point.y, point.z, 6, 0.3, 0.3, 0.3, 0.015);

        level.playSound(null, BlockPos.containing(point),
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.5f, 1.8f);
    }
}
