package com.FIRNI.superheromod.heroes.magneto;

import com.FIRNI.superheromod.core.film.FilmSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.*;

/**
 * MAGNETIC EXECUTION (X), what happens in the world while the film plays: he and the one he aimed it at are held where
 * they stood (he cannot be hurt, see MagnetoController.attacked); when the pillars crush them in the film they take the
 * blow; when it ends they are flung away from him, dazed.
 */
public final class MagnetoUltSession implements FilmSessions.Script {
    public static final String ID = "magneto:magnetic_execution";
    public static final MagnetoUltSession INSTANCE = new MagnetoUltSession();

    public static boolean start(ServerPlayer player, LivingEntity target) { return FilmSessions.start(player, target, INSTANCE); }

    @Override public String film() { return ID; }
    @Override public int total() { return ULT_TOTAL; }
    @Override public int release() { return ULT_TOTAL; }
    @Override public boolean outlivesTarget() { return true; }
    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        if (target == null || !target.isAlive()) return;
        if (age == ULT_CRUSH + 4) {
            float share = (float) MagnetoConfig.ULT_DAMAGE.get().doubleValue();
            if (share > 0) {
                target.invulnerableTime = 0;
                target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * share);
            }
            p.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1f, .5f);
        }
        if (age == ULT_TOTAL - 1) {
            Vec3 away = target.position().subtract(p.position());
            away = away.horizontalDistanceSqr() < 1e-4 ? forward : new Vec3(away.x, 0, away.z).normalize();
            double knock = MagnetoConfig.ULT_KNOCK.get();
            target.setDeltaMovement(away.x * knock * .6, .5, away.z * knock * .6);
            target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, true));
        }
    }
}
