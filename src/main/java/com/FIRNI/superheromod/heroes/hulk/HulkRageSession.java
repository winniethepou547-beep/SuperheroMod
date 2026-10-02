package com.FIRNI.superheromod.heroes.hulk;

import com.FIRNI.superheromod.core.film.FilmSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * ONE PUNCH (X), what happens in the world while the film plays. Both are held where they stand (so
 * neither can slip away, and nobody ends up inside blocks or in unloaded ground); the film draws the
 * fight on its own open plain. The barrage wears the target down punch by punch (its share of their
 * health spread over every blow), the last punch takes the main share, and when the film hands back the
 * target is blown away down the line of the punch. If the target dies or leaves, FilmSessions ends it safely.
 */
public final class HulkRageSession implements FilmSessions.Script {
    public static final String ID = "hulk:gamma_rage";
    public static final HulkRageSession INSTANCE = new HulkRageSession();

    public static boolean start(ServerPlayer player, LivingEntity target) { return FilmSessions.start(player, target, INSTANCE); }

    @Override public String film() { return ID; }
    @Override public int total() { return ULT_TOTAL; }
    @Override public int release() { return ULT_TOTAL; }
    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        var level = p.serverLevel();
        Vec3 at = target.position();
        for (int i = 0; i < ULT_HITS.length; i++) {
            if (age != (int) Math.ceil(ULT_HITS[i])) continue;
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * HulkConfig.get(HulkConfig.ULT_CRASH_SHARE).floatValue() / ULT_HITS.length);
            }
            // Those outside the film still hear it: heavy single blows first, then a hammering roll.
            if (i < 5 || i % 3 == 0) level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, .9f, .5f + .1f * (i % 4));
        }
        if (age == ULT_PUNCH) {
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * HulkConfig.get(HulkConfig.ULT_SMASH_SHARE).floatValue());
            }
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2f, .5f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5f, .5f);
        }
        if (age == ULT_TOTAL && target.isAlive()) {
            // Handed back: blown away down the line of the punch.
            target.setDeltaMovement(forward.x * 1.7, .5, forward.z * 1.7);
            target.hurtMarked = true;
            target.fallDistance = 0;
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
