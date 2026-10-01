package com.FIRNI.superheromod.heroes.thor;

import com.FIRNI.superheromod.core.film.FilmSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.heroes.thor.ThorAction.*;

/**
 * GOD OF THUNDER (X), what happens in the world while the film plays. Both are held face to
 * face; at the impact Thor really lands where the film shows him (just short of the target),
 * the target takes most of its health and is thrown out of the crater, and the cracks and
 * lightning appear on the real ground.
 */
public final class GodOfThunderSession implements FilmSessions.Script {
    public static final String ID = "thor:god_of_thunder";
    public static final GodOfThunderSession INSTANCE = new GodOfThunderSession();
    private static final double RANGE = 20;

    public static boolean start(ServerPlayer player, LivingEntity target) { return FilmSessions.start(player, target, INSTANCE); }

    /** Where the hammer comes down: this far short of the target, along the line between them. */
    public static final double LAND_SHORT = 1.3;

    @Override public String film() { return ID; }
    @Override public int total() { return ULT_TOTAL; }
    @Override public int release() { return ULT_IMPACT; }
    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        var level = p.serverLevel();
        if (age == ULT_STRIKE) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.5f, .8f);
            ThorController.fx(p, FX_SKY_BOLT, p.position().add(0, 1.2, 0), new Vec3(0, 1, 0), 2f);
        }
        if (age != ULT_IMPACT) return;
        // The film flew him over the target; the real Thor arrives at the same spot.
        Vec3 line = target.position().subtract(p.position());
        Vec3 flat = new Vec3(line.x, 0, line.z);
        Vec3 land = flat.lengthSqr() < 1e-4 ? p.position() : p.position().add(flat.scale(Math.max(0, 1 - LAND_SHORT / flat.length())));
        land = new Vec3(land.x, target.getY(), land.z);
        p.connection.teleport(land.x, land.y, land.z, p.getYRot(), 0);
        p.fallDistance = 0;
        if (target.isAlive()) {
            target.invulnerableTime = 0;
            target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * ULT_DAMAGE_SHARE);
        }
        ThorController.impact(p, land, 11, 10, true);
        if (target.isAlive()) {
            Vec3 away = flat.lengthSqr() < 1e-4 ? forward : flat.normalize();
            target.setDeltaMovement(away.scale(1.6).add(0, .9, 0));
            target.hurtMarked = true;
        }
    }

    /** The best target in front of him (the same rule Cyclops' X uses). */
    public static LivingEntity findTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1f), look = player.getLookAngle();
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(RANGE),
                e -> e != player && e.isAlive() && !e.isSpectator() && !FilmSessions.busy(e.getUUID()))) {
            Vec3 to = e.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist > RANGE || dist < .5) continue;
            double dot = to.normalize().dot(look);
            if (dot < .8) continue;
            double score = dot * 2 - dist / RANGE;
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }
}
