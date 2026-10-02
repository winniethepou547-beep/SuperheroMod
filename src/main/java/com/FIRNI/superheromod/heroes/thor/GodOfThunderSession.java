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

    @Override public String film() { return ID; }
    @Override public int total() { return ULT_TOTAL; }
    /** Both stay held for the whole film; at its last tick they are put where the film left them. */
    @Override public int release() { return ULT_TOTAL; }
    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        if (age != ULT_CRASH && age != ULT_TOTAL) return;
        Vec3 anchor = p.position(), right = forward.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 line = target.position().subtract(anchor);
        double distance = Math.sqrt(line.x * line.x + line.z * line.z);
        Vec3 crater = anchor.add(forward.scale(distance + ULT_KNOCK));
        crater = new Vec3(crater.x, target.getY(), crater.z);
        if (age == ULT_CRASH) {
            // The target is driven into the ground from the sky.
            if (target.isAlive()) {
                target.invulnerableTime = 0;
                target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * ULT_DAMAGE_SHARE);
            }
            ThorController.fx(p, FX_ULT_IMPACT, crater, Vec3.ZERO, 7);
            var level = p.serverLevel();
            level.playSound(null, crater.x, crater.y, crater.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, .55f);
            level.playSound(null, crater.x, crater.y, crater.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.4f, .7f);
            return;
        }
        // The end: Thor where he landed, the target in its crater.
        Vec3 land = anchor.add(forward.scale(distance - ULT_LAND_SHORT)).add(right.scale(ULT_LAND_X));
        float face = (float) Math.toDegrees(Math.atan2(-(crater.x - land.x), crater.z - land.z));
        p.connection.teleport(land.x, crater.y, land.z, face, 0);
        p.fallDistance = 0;
        if (target.isAlive()) {
            if (target instanceof ServerPlayer other) other.connection.teleport(crater.x, crater.y, crater.z, other.getYRot(), 0);
            else target.teleportTo(crater.x, crater.y, crater.z);
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
