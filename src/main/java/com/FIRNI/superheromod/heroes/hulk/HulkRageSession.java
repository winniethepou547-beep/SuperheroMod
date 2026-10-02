package com.FIRNI.superheromod.heroes.hulk;

import com.FIRNI.superheromod.core.film.FilmSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * GAMMA RAGE (X), what happens in the world while the film plays. Both are held where they stand
 * (so neither can slip away, and nobody ends up inside blocks or in unloaded ground); the film draws
 * the leap, the grab, the throw and the dive. The target's crash and Hulk's smash land on the real
 * ground where the target stood: first the crash (some damage, dust), then, after the configured
 * delay, the two-handed smash (the main damage and a real but limited crater). At the end Hulk stands
 * at the crater's edge and control returns. If the target dies or leaves, FilmSessions ends it safely.
 */
public final class HulkRageSession implements FilmSessions.Script {
    public static final String ID = "hulk:gamma_rage";
    public static final HulkRageSession INSTANCE = new HulkRageSession();

    public static boolean start(ServerPlayer player, LivingEntity target) { return FilmSessions.start(player, target, INSTANCE); }

    public static int smashTick() { return ULT_CRASH + HulkConfig.get(HulkConfig.ULT_DELAY); }
    public static int totalTicks() { return smashTick() + ULT_TOTAL_AFTER; }

    @Override public String film() { return ID; }
    @Override public int total() { return totalTicks(); }
    @Override public int release() { return totalTicks(); }
    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        var level = p.serverLevel();
        Vec3 crater = target.position();
        if (age == ULT_ROAR) level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.4f, .8f);
        if (age == ULT_CRASH) {
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * HulkConfig.get(HulkConfig.ULT_CRASH_SHARE).floatValue());
            }
            HulkController.fx(p, FX_ULT_CRASH, crater, forward, 1, HulkBlocks.id(level.getBlockState(BlockPos.containing(crater).below())));
            level.playSound(null, crater.x, crater.y, crater.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.4f, .8f);
            level.playSound(null, crater.x, crater.y, crater.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1f, .5f);
        }
        if (age == smashTick()) {
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * HulkConfig.get(HulkConfig.ULT_SMASH_SHARE).floatValue());
            }
            int radius = HulkConfig.get(HulkConfig.ULT_CRATER);
            BlockPos ground = BlockPos.containing(crater).below();
            int block = HulkBlocks.id(level.getBlockState(ground));
            if (radius > 0) HulkController.crater(p, ground, radius, 2, new HulkBlocks.Budget());
            HulkController.fx(p, FX_ULT_SMASH, crater, forward, 1, block);
            level.playSound(null, crater.x, crater.y, crater.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2f, .5f);
            level.playSound(null, crater.x, crater.y, crater.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, .6f);
            level.playSound(null, crater.x, crater.y, crater.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.4f, .4f);
            // Anyone else standing near is thrown out of the blast.
            for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(crater, crater).inflate(9, 4, 9),
                    t -> t != p && t != target && t.isAlive() && !t.isSpectator()
                            && !(t instanceof Player other && p.isAlliedTo(other) && !HulkConfig.get(HulkConfig.FRIENDLY_FIRE)))) {
                double d = t.position().distanceTo(crater);
                if (d > 9) continue;
                HulkController.hit(p, t, (float) (12 * (1 - d / 9)), t.position().subtract(crater), 1.6 * (1 - d / 9), .6 * (1 - d / 9));
            }
        }
        if (age == totalTicks()) {
            // Hulk stands at the crater's edge, on its floor, facing the target.
            Vec3 edge = crater.subtract(forward.scale(1.6));
            BlockPos floor = HulkController.surface(level, BlockPos.containing(edge.x, crater.y, edge.z), 3);
            double y = floor == null ? crater.y : floor.getY() + 1;
            p.connection.teleport(edge.x, y, edge.z, p.getYRot(), 0);
            p.fallDistance = 0;
            if (target.isAlive()) {
                BlockPos under = HulkController.surface(level, BlockPos.containing(crater), 3);
                double ty = under == null ? crater.y : under.getY() + 1;
                if (target instanceof ServerPlayer other) other.connection.teleport(crater.x, ty, crater.z, other.getYRot(), 0);
                else target.teleportTo(crater.x, ty, crater.z);
                target.fallDistance = 0;
            }
        }
    }

    /** The best target in front of him, in range. */
    public static LivingEntity findTarget(ServerPlayer player) {
        double range = HulkConfig.get(HulkConfig.ULT_RANGE);
        Vec3 eye = player.getEyePosition(1f), look = player.getLookAngle();
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range),
                e -> e != player && e.isAlive() && !e.isSpectator() && !FilmSessions.busy(e.getUUID())
                        && !(e instanceof Player other && player.isAlliedTo(other) && !HulkConfig.get(HulkConfig.FRIENDLY_FIRE)))) {
            Vec3 to = e.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist > range || dist < .5) continue;
            double dot = to.normalize().dot(look);
            if (dot < .75) continue;
            double score = dot * 2 - dist / range;
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }
}
