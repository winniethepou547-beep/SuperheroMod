package com.FIRNI.superheromod.heroes.zed;

import com.FIRNI.superheromod.core.film.FilmSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.FIRNI.superheromod.heroes.zed.ZedAction.*;

/**
 * SHADOW EXECUTION (X), what happens in the world while the film plays. Both are held where they
 * stand; at the red flash inside the storm the victim takes most of its health; at the end Zed is
 * where the film shows him reforming (a few blocks off, facing the fallen victim) and the victim is
 * slow to get up. The film plays to its end even if the flash kills.
 */
public final class ZedExecutionSession implements FilmSessions.Script {
    public static final String ID = "zed:shadow_execution";
    public static final ZedExecutionSession INSTANCE = new ZedExecutionSession();
    /** Where each Zed's victim stood when it began (the film is laid out round it). */
    private static final Map<UUID, Vec3> VICTIM = new HashMap<>();

    public static boolean start(ServerPlayer player, LivingEntity target) {
        Vec3 at = target.position();
        if (!FilmSessions.start(player, target, INSTANCE)) return false;
        VICTIM.put(player.getUUID(), at);
        return true;
    }

    @Override public String film() { return ID; }
    @Override public int total() { return ULT_TOTAL; }
    @Override public int release() { return ULT_TOTAL; }
    @Override public boolean outlivesTarget() { return true; }

    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        if (age == ULT_FLASH) {
            if (target != null && target.isAlive()) {
                target.invulnerableTime = 0;
                target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * ULT_DAMAGE_SHARE);
            }
            Vec3 at = VICTIM.getOrDefault(p.getUUID(), p.position());
            p.level().playSound(null, at.x, at.y + 1, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, .7f);
            return;
        }
        if (age != ULT_TOTAL) return;
        Vec3 victim = VICTIM.remove(p.getUUID());
        if (victim == null) return;
        // He stands where the film reformed him, on the ground, facing them.
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 spot = ground(p.serverLevel(), victim.add(right.scale(ULT_REVEAL_X)).add(forward.scale(ULT_REVEAL_Z)), victim.y);
        if (spot == null || !p.level().noCollision(p, p.getBoundingBox().move(spot.subtract(p.position())))) spot = p.position();
        Vec3 to = victim.subtract(spot);
        p.connection.teleport(spot.x, spot.y, spot.z, (float) Math.toDegrees(Math.atan2(-to.x, to.z)), 0);
        p.fallDistance = 0;
        if (target != null && target.isAlive()) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0));
        }
    }

    /** Standing height near this spot (a few blocks up or down from the victim's), or null. */
    private static Vec3 ground(ServerLevel level, Vec3 at, double near) {
        BlockPos base = BlockPos.containing(at.x, near, at.z);
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = base.above(dy);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && level.getBlockState(pos.above(2)).getCollisionShape(level, pos.above(2)).isEmpty())
                return new Vec3(at.x, pos.getY() + 1, at.z);
        }
        return null;
    }
}
