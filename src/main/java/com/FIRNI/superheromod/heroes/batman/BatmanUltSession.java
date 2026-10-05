package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.film.FilmSessions;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanUltBeats.*;

/**
 * X: KARA ŞÖVALYE, what happens in the world while the film plays (the film itself is DarkKnightFilm on every client in
 * it, timed by BatmanUltBeats). Aimed at the one he looks at (in range, in front of him, in sight); he and they are held
 * where they stand (FilmSessions), he cannot be hurt meanwhile. When the Batarang pins them to the wall in the film they
 * take the blow (a share of their max health); when it ends they are back where they stood, knocked down on their back
 * and staggered. The film plays on to its end even if the blow kills them. Its cooldown is kept here (not in his
 * gadget slots), counted from the start of the film.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BatmanUltSession implements FilmSessions.Script {
    public static final BatmanUltSession INSTANCE = new BatmanUltSession();
    private static final String NAME = "Kara Şövalye";
    /** Per Batman: the game time the film is ready again. */
    private static final Map<UUID, Long> READY = new HashMap<>();
    /** How long they lie on their back when it hands back, and stay staggered after. */
    private static final int DOWN = 44;

    private BatmanUltSession() {}

    /** Ticks until he may use it again (0 = ready). */
    public static int cooldown(ServerPlayer p) {
        Long at = READY.get(p.getUUID());
        return at == null ? 0 : (int) Math.max(0, at - p.level().getGameTime());
    }

    /** X pressed. */
    static void start(ServerPlayer p, BatmanController.State s) {
        if (FilmSessions.busy(p.getUUID())) return;
        if (BatmanController.busy(s) || s.gliding || s.hook != 0) { BatmanController.tell(p, NAME + ": şimdi olmaz"); return; }
        int left = cooldown(p);
        if (left > 0) { BatmanController.tell(p, NAME + ": " + (left + 19) / 20 + " sn"); return; }
        LivingEntity target = aimed(p, BatmanConfig.ULT_RANGE.get());
        if (target == null) { BatmanController.tell(p, NAME + ": önünde hedef yok (birine bakarak bas)"); return; }
        if (!FilmSessions.start(p, target, INSTANCE)) return;
        READY.put(p.getUUID(), p.level().getGameTime() + BatmanConfig.ULT_COOLDOWN.get());
        // Whatever he was doing stops; the film has him now.
        s.aiming = false; s.charging = false; s.wheel = false; s.queued = false;
        if (s.action != IDLE) BatmanController.set(s, IDLE);
        if (target instanceof Mob mob) mob.getNavigation().stop();
    }

    /** The one he looks at: in range, close to his line of sight, nothing solid between. */
    private static LivingEntity aimed(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), t -> BatmanController.targetable(p, t))) {
            if (FilmSessions.busy(t.getUUID())) continue;
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range || d < .01) continue;
            double along = to.normalize().dot(look);
            if (along < .9) continue;
            if (p.level().clip(new ClipContext(eye, t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            double score = along * 2 - d / range;
            if (score > bestScore) { bestScore = score; best = t; }
        }
        return best;
    }

    // ------------------------------------------------------------------ the script
    @Override public String film() { return BatmanUltBeats.ID; }
    @Override public int total() { return TOTAL; }
    @Override public int release() { return TOTAL; }
    @Override public boolean outlivesTarget() { return true; }
    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        if (target == null || !target.isAlive()) return;
        if (age == WALL) {
            // The Batarang drives them into the wall.
            float share = (float) BatmanConfig.ULT_DAMAGE.get().doubleValue();
            if (share > 0) {
                target.invulnerableTime = 0;
                target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * share);
            }
            p.level().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.FX_IMPACT_METAL.get(), SoundSource.PLAYERS, .9f, .9f);
            p.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, .8f, .7f);
        }
        if (age == TOTAL - 1) {
            // Back in the world where they stood: on their back, staggered, slow to get up.
            Vec3 dir = target.position().subtract(p.position());
            dir = dir.horizontalDistanceSqr() < 1e-4 ? forward : new Vec3(dir.x, 0, dir.z).normalize();
            target.setDeltaMovement(dir.x * .25, .05, dir.z * .25);
            target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DOWN, 6, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP, DOWN, 128, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 70, 0, false, true));
            if (target instanceof Mob mob) mob.getNavigation().stop();
            BatmanStagger.apply(target, DOWN + STAGGER_TICKS * 2);
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                    new BatmanFxPacket(FX_DOWNED, target.position(), dir.scale(-1), DOWN, target.getId(), p.getId()));
            p.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, .9f, .6f);
        }
    }

    /** Nothing touches him while the film plays. */
    @SubscribeEvent public static void attacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && FilmSessions.playing(p.getUUID(), BatmanUltBeats.ID)) e.setCanceled(true);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { READY.clear(); }
}
