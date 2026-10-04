package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Bound by Batman's grapnel line (after the right-click yank has dragged them in): the line stays wound round their
 * body, the claw clamped on it, and they cannot move from the spot (Ghost Rider's chains, Batman's way) until they break
 * free: a player by clicking left again and again (BIND_CLICKS), a mob by struggling for BIND_MOB_TICKS; nobody stays
 * longer than BIND_MAX_TICKS. Everyone's clients draw the coils (BatmanBound), a bound player's own client holds their
 * movement and turns their left clicks into tugs at the line.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BatmanBind {
    private static final class Bound {
        final LivingEntity e; final int batman; final Vec3 anchor; int age, clicks;
        Bound(LivingEntity e, int batman, Vec3 anchor) { this.e = e; this.batman = batman; this.anchor = anchor; }
    }
    private static final Map<Integer, Bound> BOUND = new HashMap<>();

    private BatmanBind() {}

    public static boolean bound(LivingEntity e) { return BOUND.containsKey(e.getId()); }

    /** The line wound round them where they lie. */
    static void bind(LivingEntity e, ServerPlayer batman) {
        Bound b = new Bound(e, batman.getId(), e.position());
        BOUND.put(e.getId(), b);
        if (e instanceof Mob mob) mob.getNavigation().stop();
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, .7f);
        e.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1f, .6f);
        send(b, left(b));
    }
    /** A bound player tugs at the line (left click). */
    static void click(ServerPlayer p) {
        Bound b = BOUND.get(p.getId());
        if (b == null) return;
        b.clicks++;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, .7f, .8f + p.getRandom().nextFloat() * .3f);
        if (b.clicks >= BatmanConfig.BIND_CLICKS.get()) free(b, true);
        else send(b, left(b));
    }
    private static int left(Bound b) {
        int max = BatmanConfig.BIND_MAX_TICKS.get();
        int need = b.e instanceof ServerPlayer ? max : Math.min(max, BatmanConfig.BIND_MOB_TICKS.get());
        return Math.max(1, need - b.age);
    }
    private static void free(Bound b, boolean broke) {
        BOUND.remove(b.e.getId());
        b.e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (broke) {
            b.e.level().playSound(null, b.e.getX(), b.e.getY(), b.e.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1f, .8f);
            b.e.level().playSound(null, b.e.getX(), b.e.getY(), b.e.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, .8f, 1.3f);
        }
        send(b, 0);
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || BOUND.isEmpty()) return;
        for (Bound b : new ArrayList<>(BOUND.values())) {
            LivingEntity t = b.e;
            if (t.isRemoved() || !t.isAlive()) { BOUND.remove(t.getId()); send(b, 0); continue; }
            b.age++;
            int max = BatmanConfig.BIND_MAX_TICKS.get();
            if (b.age >= max || (!(t instanceof ServerPlayer) && b.age >= BatmanConfig.BIND_MOB_TICKS.get())) { free(b, true); continue; }
            // Held on the spot: no walking, no jumping (a player's own client holds them too; this is the backstop).
            Vec3 m = t.getDeltaMovement();
            t.setDeltaMovement(0, Math.min(m.y, 0), 0);
            if (t instanceof Mob mob) mob.getNavigation().stop();
            if (b.age % 10 == 1) {
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 7, false, false, false));
                t.addEffect(new MobEffectInstance(MobEffects.JUMP, 14, 128, false, false, false));
            }
            Vec3 at = t.position();
            double dx = at.x - b.anchor.x, dz = at.z - b.anchor.z;
            if (dx * dx + dz * dz > 1.2 * 1.2) {
                if (t instanceof ServerPlayer sp) sp.teleportTo(b.anchor.x, at.y, b.anchor.z);
                else t.teleportTo(b.anchor.x, at.y, b.anchor.z);
            }
            if (b.age % 20 == 0) send(b, left(b));
        }
    }
    @SubscribeEvent public static void died(LivingDeathEvent e) { BOUND.remove(e.getEntity().getId()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { BOUND.remove(e.getEntity().getId()); }

    /** Everyone near sees the coils; the bound player's own client also gets the clicks counted (id = clicks so far). */
    private static void send(Bound b, int ticksLeft) {
        LivingEntity t = b.e;
        float progress = t instanceof ServerPlayer ? b.clicks / (float) BatmanConfig.BIND_CLICKS.get() : b.age / (float) BatmanConfig.BIND_MOB_TICKS.get();
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> t),
                new BatmanFxPacket(FX_BOUND, t.position(), new Vec3(progress, b.batman, 0), ticksLeft, t.getId(), b.batman));
    }
}
