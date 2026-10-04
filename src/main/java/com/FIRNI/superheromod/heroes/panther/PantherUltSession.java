package com.FIRNI.superheromod.heroes.panther;

import com.FIRNI.superheromod.core.film.FilmSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * THE FINAL PURSUIT (X), what happens in the world while the film plays: he and the one he aimed it at (the driver
 * of the car in the film) are held where they stood, he cannot be hurt (see PantherController.attacked); when the
 * film's release goes off, the real kinetic blast goes off round him (others near are thrown outward); when the car
 * comes down in the film the driver takes the crash; when it ends they are thrown back from him, dazed.
 */
public final class PantherUltSession implements FilmSessions.Script {
    public static final String ID = "panther:final_pursuit";
    public static final PantherUltSession INSTANCE = new PantherUltSession();

    public static boolean start(ServerPlayer player, LivingEntity target) { return FilmSessions.start(player, target, INSTANCE); }

    @Override public String film() { return ID; }
    @Override public int total() { return ULT_TOTAL; }
    @Override public int release() { return ULT_TOTAL; }
    @Override public boolean outlivesTarget() { return true; }
    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        if (age == ULT_BOOM) PantherController.ultBlast(p, target);
        if (target == null || !target.isAlive()) return;
        if (age == ULT_CRASH) {
            float share = (float) PantherConfig.ULT_TARGET_DAMAGE.get().doubleValue();
            if (share > 0) {
                target.invulnerableTime = 0;
                target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * share);
            }
            p.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, .8f, .7f);
        }
        if (age == ULT_TOTAL - 1) {
            // Back in the world: thrown away from him, reeling.
            Vec3 away = target.position().subtract(p.position());
            away = away.horizontalDistanceSqr() < 1e-4 ? forward : new Vec3(away.x, 0, away.z).normalize();
            double knock = PantherConfig.ULT_KNOCK.get();
            target.setDeltaMovement(away.x * knock * .6, .45, away.z * knock * .6);
            target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, true));
        }
    }
}
