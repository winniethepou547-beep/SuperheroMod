package com.FIRNI.superheromod.heroes.magneto;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.MagnetoFxPacket;
import com.FIRNI.superheromod.network.packet.SpikeStuckPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.*;

/**
 * Magneto's left-click spike once it is in a body: it stays in (drawn on them for everyone), slows them, and comes out
 * when they mash left click (a player) or after a few seconds (a mob). Whoever pulled one out is immune for a while:
 * spikes still hurt and push them, but no longer stick.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class MagnetoSpike {
    private static final class Stuck {
        final LivingEntity target; final int id; final float yawRel, pitch, height; float progress; int age;
        Stuck(LivingEntity target, int id, float yawRel, float pitch, float height) {
            this.target = target; this.id = id; this.yawRel = yawRel; this.pitch = pitch; this.height = height;
        }
    }
    private static final Map<Integer, Stuck> STUCK = new HashMap<>();
    private static final Map<UUID, Integer> IMMUNE = new HashMap<>();
    private static int clock;

    private MagnetoSpike() {}

    /** A spike in this body right now (its left clicks pull at it instead of attacking). */
    public static boolean stuck(LivingEntity e) { return STUCK.containsKey(e.getId()); }

    /** The spike reaches a body: pushed back, slowed, and (unless they pulled one out lately) it stays in. True when it stuck. */
    static boolean hit(LivingEntity t, Vec3 dir, Vec3 at, int id) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        // About knockbackBlocks along the ground: a short hop that slides out.
        double kb = Math.max(0, 1 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        double v = MagnetoConfig.SPIKE_KNOCKBACK.get() * .18 * kb;
        Vec3 m = t.getDeltaMovement();
        t.setDeltaMovement(m.x * .2 + flat.x * v, Math.max(m.y, .2 * kb), m.z * .2 + flat.z * v);
        t.hurtMarked = true;
        slow(t, 40);
        Integer until = IMMUNE.get(t.getUUID());
        if (until != null && until > clock || STUCK.containsKey(t.getId())) return false;
        float height = (float) Mth.clamp(at.y - t.getY(), t.getBbHeight() * .4, t.getBbHeight() * .78);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1, 1)));
        Stuck s = new Stuck(t, id, Mth.wrapDegrees(yaw - t.yBodyRot), pitch, height);
        STUCK.put(t.getId(), s);
        fx(s, FX_IMPALE, 0);
        sound(t, SoundEvents.TRIDENT_HIT, 1f, .8f);
        sound(t, ModSounds.MAGNETO_ROD_IMPALE.get(), .8f, 1.5f);
        sound(t, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, .7f, .7f);
        if (t instanceof ServerPlayer sp) send(sp, true, 0, 0);
        return true;
    }

    /** The one it is stuck in pulled at it: presses since their last packet. */
    public static void pull(ServerPlayer p, int presses) {
        Stuck s = STUCK.get(p.getId());
        if (s == null || presses <= 0) return;
        s.progress += Math.min(3, presses) / (float) MagnetoConfig.SPIKE_CLICKS.get();
        if (s.progress >= 1) { out(s); return; }
        fx(s, FX_IMPALE_PULL, s.progress);
        sound(p, SoundEvents.CHAIN_HIT, .6f, .7f + s.progress * .8f);
        if (p.getRandom().nextInt(3) == 0) sound(p, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, .35f, 1.2f);
        send(p, true, s.progress, 0);
    }

    private static void out(Stuck s) {
        STUCK.remove(s.target.getId());
        int immune = (int) (MagnetoConfig.SPIKE_IMMUNITY.get() * 20);
        if (immune > 0) IMMUNE.put(s.target.getUUID(), clock + immune);
        fx(s, FX_IMPALE_OUT, 1);
        sound(s.target, SoundEvents.TRIDENT_RETURN, .8f, 1.3f);
        sound(s.target, ModSounds.MAGNETO_METAL_SHING.get(), .7f, .8f);
        sound(s.target, SoundEvents.CHAIN_BREAK, .8f, .9f);
        s.target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (s.target instanceof ServerPlayer sp) send(sp, false, 1, immune);
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        clock++;
        if (!IMMUNE.isEmpty() && clock % 20 == 0) IMMUNE.values().removeIf(u -> u <= clock);
        if (STUCK.isEmpty()) return;
        float decay = .06f / MagnetoConfig.SPIKE_CLICKS.get();
        for (Stuck s : new ArrayList<>(STUCK.values())) {
            LivingEntity t = s.target;
            if (t.isRemoved() || !t.isAlive()) { STUCK.remove(t.getId()); if (!t.isRemoved()) fx(s, FX_IMPALE_OUT, 0); continue; }
            s.age++;
            if (s.age % 10 == 0) slow(t, 20);
            if (t instanceof Player) {
                // It slides back in a little between tugs: one click now and then will not do.
                s.progress = Math.max(0, s.progress - decay);
                if (s.age > MagnetoConfig.SPIKE_MAX_STUCK.get() * 20) s.progress = 1;
            } else s.progress += 1f / (float) (MagnetoConfig.SPIKE_MOB_STUCK.get() * 20);
            if (s.progress >= 1) { out(s); continue; }
            if (t instanceof ServerPlayer sp) send(sp, true, s.progress, 0);
            // Anyone who came into range since gets it too.
            if (s.age % 20 == 0) fx(s, FX_IMPALE, s.progress);
        }
    }
    /** Its own punch is held back too (the client already turns the clicks into tugs). */
    @SubscribeEvent public static void attack(net.minecraftforge.event.entity.player.AttackEntityEvent e) { if (stuck(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void died(LivingDeathEvent e) { STUCK.remove(e.getEntity().getId()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { STUCK.remove(e.getEntity().getId()); }
    @SubscribeEvent public static void respawned(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) send(sp, false, 0, 0);
    }

    // ------------------------------------------------------------------ helpers
    private static void slow(LivingEntity t, int ticks) {
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, MagnetoConfig.SPIKE_SLOW.get(), false, false, true));
    }
    /** Where it sits in the body (its centre on the line it came in on) and which way it points, in the world now. */
    private static Vec3 dir(Stuck s) { return Vec3.directionFromRotation(s.pitch, s.yawRel + s.target.yBodyRot); }
    private static void fx(Stuck s, int kind, float power) {
        Vec3 at = s.target.position().add(0, s.height, 0);
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> s.target),
                new MagnetoFxPacket(kind, at, dir(s), power, s.target.getId(), s.id));
    }
    private static void send(ServerPlayer p, boolean stuck, float progress, int immune) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SpikeStuckPacket(stuck, progress, immune));
    }
    private static void sound(LivingEntity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY() + at.getBbHeight() * .6, at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
