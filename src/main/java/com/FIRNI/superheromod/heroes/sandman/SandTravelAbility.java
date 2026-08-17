package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * SAND TRAVEL — Sandman kum formuna gecip bir kum capasina akar.
 *
 * Gecerli capalar: ayakta duran Sand Wall'lar, aktif Sand Spike'lar ve
 * dunyadaki kum bloklari. Boylece coldeki her yer capa, kapali alanlarda ise
 * oyuncunun kendi kurdugu yapilar capa oluyor.
 *
 * Isinlanma TAMAMEN sunucuda: hedef secimi, mesafe ve zemin kontrolu burada
 * yapiliyor. Istemci sadece efektleri goruyor.
 */
public class SandTravelAbility extends Ability {

    public SandTravelAbility() {
        super("sandman_sand_travel", AbilityType.INSTANT, AbilitySlot.SKILL_F);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 70);
        config.set("range", 28.0);
        /** Nisan noktasina bu kadar yakin capalar aday sayilir. */
        config.set("anchorSnap", 6.0);
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;
        return findAnchor(player) != null;
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        Vec3 target = findAnchor(player);
        if (target == null) return;

        ServerLevel level = (ServerLevel) player.level();
        Vec3 from = player.position();

        BlockParticleOption sand =
                new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());

        // 1) Vucut dagilir
        level.sendParticles(sand, from.x, from.y + 1.0, from.z, 45, 0.45, 0.9, 0.45, 0.12);
        level.playSound(null, player.blockPosition(),
                SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.3f, 0.7f);

        // 2) Yerde akan kum izi — iki nokta arasi
        Vec3 delta = target.subtract(from);
        int steps = (int) Math.min(40, delta.length() * 1.5);
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            Vec3 p = from.add(delta.scale(t));
            level.sendParticles(sand, p.x, p.y + 0.25, p.z, 2, 0.18, 0.05, 0.18, 0.03);
        }

        // 3) Isinlanma — sunucu tarafinda
        player.teleportTo(target.x, target.y, target.z);
        player.fallDistance = 0f;

        // 4) Hedefte kum sutunu yukselir ve Sandman yeniden olusur
        level.sendParticles(sand, target.x, target.y + 1.0, target.z, 50, 0.45, 1.0, 0.45, 0.14);
        level.playSound(null, BlockPos.containing(target),
                SoundEvents.SAND_PLACE, SoundSource.PLAYERS, 1.4f, 0.6f);
    }

    /**
     * Nisan alinan yone en uygun capa.
     *
     * Once bakisin carptigi nokta bulunuyor, sonra o noktanin yakinindaki
     * capalardan en yakini seciliyor. Boylece "kabaca oraya bak, oraya git"
     * calisiyor; piksel hassasiyeti gerekmiyor.
     */
    private Vec3 findAnchor(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        double range = getConfig().getDouble("range", 28.0);
        double snap = getConfig().getDouble("anchorSnap", 6.0);

        Vec3 eye = RaycastSystem.getEyeOrigin(player);
        Vec3 look = RaycastSystem.getLookDirection(player);

        RaycastResult result = RaycastSystem.cast(
                level, player, eye, look, range, 0.5f, false,
                e -> e instanceof LivingEntity && e != player);

        Vec3 aim = result.getHitPosition();

        List<Vec3> anchors = new ArrayList<>();
        anchors.addAll(SandWallController.anchorPoints(level));
        anchors.addAll(SandSpikeController.anchorPoints(level));
        addSandBlocks(level, aim, snap, anchors);

        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;

        for (Vec3 anchor : anchors) {
            double toAim = anchor.distanceToSqr(aim);
            if (toAim > snap * snap) continue;
            // Menzil disina isinlanma yok
            if (anchor.distanceToSqr(player.position()) > range * range) continue;

            if (toAim < bestDist) {
                bestDist = toAim;
                best = anchor;
            }
        }

        return best == null ? null : standingSpot(level, best);
    }

    /** Nisan noktasinin cevresindeki kum bloklarinin ust yuzeyleri. */
    private static void addSandBlocks(ServerLevel level, Vec3 aim, double snap, List<Vec3> out) {
        BlockPos center = BlockPos.containing(aim);
        int r = (int) Math.ceil(snap);

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).is(BlockTags.SAND)) continue;
                    // Ustunde durulabilecek yer olmali
                    if (!level.getBlockState(pos.above()).isAir()) continue;

                    out.add(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
                }
            }
        }
    }

    /** Capanin ustunde bosluk varsa oraya, yoksa capanin kendisine cikilir. */
    private static Vec3 standingSpot(ServerLevel level, Vec3 anchor) {
        BlockPos pos = BlockPos.containing(anchor);

        for (int i = 0; i < 4; i++) {
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            }
            pos = pos.above();
        }
        return anchor;
    }
}
