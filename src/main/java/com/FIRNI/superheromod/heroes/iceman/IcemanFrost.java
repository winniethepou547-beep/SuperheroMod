package com.FIRNI.superheromod.heroes.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The frost meter every enemy carries (0..100), on the server. Iceman's cold adds to it; it wears off a while after the
 * last cold touched them. It slows them more the higher it is; at 100 they are DEEP FROZEN: held still in a block of ice
 * for DEEP_TICKS (no moving, no attacking), then the ice breaks by itself (or at once under a blow, which then does
 * DEEP_SHATTER_BONUS times its damage) and they cannot be deep frozen again for DEEP_IMMUNE ticks.
 * The meter is sent to everyone who sees the body (FX_FROST) so every client draws the frost on it, and the one frozen
 * sees it on their own screen.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class IcemanFrost {
    static final class Frost {
        final LivingEntity body;
        float value, sent = -1;
        long lastAdd;
        /** Ticks the deep freeze still holds (0: not frozen), when it began, until when it cannot happen again. */
        int deep; long deepAt = -1, immuneUntil;
        int sentStage = -1, sinceSent;
        /** Where the blow that shatters the ice came from (for the cracks' start). */
        Vec3 blowFrom;
        float slowApplied = -1;
        Frost(LivingEntity body) { this.body = body; }
    }
    private static final Map<Integer, Frost> FROST = new HashMap<>();
    private static final UUID SLOW_ID = UUID.fromString("6a1c5e0e-58c3-4c1e-9b8e-1ce0a1ce0a11");

    private IcemanFrost() {}

    public static float get(LivingEntity t) { Frost f = FROST.get(t.getId()); return f == null ? 0 : f.value; }
    public static boolean deep(LivingEntity t) { Frost f = FROST.get(t.getId()); return f != null && f.deep > 0; }

    /**
     * Adds cold to a body (from Iceman p; null for a test). Iceman himself is never frosted (unless test). True when this
     * froze them solid.
     */
    public static boolean add(ServerPlayer p, LivingEntity t, float amount, boolean test) {
        if (amount <= 0 || !t.isAlive()) return false;
        if (!test && IcemanController.isHero(t)) return false;
        Frost f = FROST.computeIfAbsent(t.getId(), id -> new Frost(t));
        long now = t.level().getGameTime();
        f.lastAdd = now;
        if (f.deep > 0) return false;
        f.value = Math.min(FROST_MAX, f.value + amount);
        if (f.value >= FROST_MAX) {
            if (now < f.immuneUntil) { f.value = FROST_MAX - .5f; return false; }
            freeze(f, now, p);
            return true;
        }
        return false;
    }
    /** Sets the meter outright (the test command): 100 freezes them solid at once. */
    public static void set(LivingEntity t, float value) {
        Frost f = FROST.computeIfAbsent(t.getId(), id -> new Frost(t));
        long now = t.level().getGameTime();
        f.immuneUntil = 0; f.lastAdd = now;
        if (f.deep > 0 && value < FROST_MAX) { f.deep = 0; f.deepAt = -1; }
        f.value = Mth.clamp(value, 0, FROST_MAX);
        if (f.value >= FROST_MAX && f.deep <= 0) freeze(f, now, null);
        f.sinceSent = 999;
    }
    /**
     * Frozen solid by the cold from `from` (an Iceman; null: a test, the cold comes at their face). The ice takes hold
     * over DEEP_SEIZE ticks (they slow to a stop meanwhile), then holds for the configured time.
     */
    private static void freeze(Frost f, long now, LivingEntity from) {
        LivingEntity t = f.body;
        f.value = FROST_MAX;
        f.deep = DEEP_SEIZE + (int) Math.round(IcemanConfig.DEEP_FREEZE_SECONDS.get() * 20);
        f.deepAt = now;
        if (t instanceof Mob mob) { mob.setTarget(null); mob.getNavigation().stop(); }
        // Which way the cold travelled (sent so the ice grows from the side it came from).
        Vec3 way = from != null ? t.position().subtract(from.position()) : t.getLookAngle().scale(-1);
        way = new Vec3(way.x, 0, way.z);
        way = way.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : way.normalize();
        ServerLevel level = (ServerLevel) t.level();
        Vec3 at = t.position();
        level.playSound(null, at.x, at.y + 1, at.z, ModSounds.ICEMAN_DEEP_FREEZE.get(), SoundSource.PLAYERS, 1.3f, 1f);
        level.playSound(null, at.x, at.y + 1, at.z, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, .9f, .6f);
        send(f, FX_DEEP_FREEZE, at, new Vec3(way.x, t.getBbHeight(), way.z), t.getBbWidth(), f.deep);
        f.sinceSent = 999;
    }
    /**
     * A blow on someone deep frozen: the ice breaks round them (with a big burst) and the blow counts more. Returns the
     * damage multiplier (1 when they were not frozen).
     */
    public static float shatter(LivingEntity t, LivingEntity by) {
        Frost f = FROST.get(t.getId());
        if (f == null || f.deep <= 0) return 1;
        f.blowFrom = by == null ? null : by.position();
        thaw(f, 2);
        return IcemanConfig.f(IcemanConfig.DEEP_SHATTER_BONUS);
    }
    private static void thaw(Frost f, int how) {
        LivingEntity t = f.body;
        long now = t.level().getGameTime();
        f.deep = 0; f.deepAt = -1;
        f.immuneUntil = now + DEEP_IMMUNE;
        f.value = how == 2 ? 30 : 45;
        f.lastAdd = now;
        ServerLevel level = (ServerLevel) t.level();
        Vec3 at = t.position();
        level.playSound(null, at.x, at.y + 1, at.z, ModSounds.ICEMAN_SHATTER.get(), SoundSource.PLAYERS, how == 2 ? 1.5f : 1.1f, how == 2 ? .85f : 1.05f);
        level.playSound(null, at.x, at.y + 1, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, .9f, .6f);
        // The way the blow travelled (the cracks start where it landed); none when it broke by itself.
        Vec3 way = f.blowFrom == null ? Vec3.ZERO : new Vec3(at.x - f.blowFrom.x, 0, at.z - f.blowFrom.z);
        way = way.lengthSqr() < 1e-6 ? Vec3.ZERO : way.normalize();
        f.blowFrom = null;
        send(f, FX_DEEP_BREAK, at, new Vec3(way.x, t.getBbHeight(), way.z), how, 0);
        f.sinceSent = 999;
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || FROST.isEmpty()) return;
        float decay = IcemanConfig.f(IcemanConfig.FROST_DECAY) / 20f;
        int delay = (int) Math.round(IcemanConfig.FROST_DECAY_DELAY.get() * 20);
        for (Iterator<Frost> it = FROST.values().iterator(); it.hasNext(); ) {
            Frost f = it.next();
            LivingEntity t = f.body;
            if (t.isRemoved() || !t.isAlive()) { slow(f, 0); if (f.value > 0 || f.deep > 0) { f.value = 0; f.deep = 0; sync(f, true); } it.remove(); continue; }
            long now = t.level().getGameTime();
            if (f.deep > 0) {
                long age = now - f.deepAt;
                if (age < DEEP_SEIZE) {
                    // The ice taking hold: they still move, slower and slower (a player's own input is faded out by
                    // their client; a mob's drift is wound down here).
                    float k = (age + 1) / (float) DEEP_SEIZE;
                    if (!(t instanceof ServerPlayer)) {
                        Vec3 v = t.getDeltaMovement();
                        t.setDeltaMovement(v.x * (1 - k), Math.min(v.y, v.y * (1 - k * .5f)), v.z * (1 - k));
                    }
                } else {
                    // Held in the ice: no moving (falls straight down if in the air), no turning to anyone.
                    t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
                    if (t instanceof ServerPlayer) t.hurtMarked = true;
                }
                if (t instanceof Mob mob) { mob.setTarget(null); mob.getNavigation().stop(); }
                if (--f.deep <= 0) thaw(f, 1);
            } else if (f.value > 0 && now - f.lastAdd > delay) {
                f.value = Math.max(0, f.value - decay);
            }
            slow(f, f.deep > 0 ? Math.max(f.value / FROST_MAX, Math.min(1, (now - f.deepAt + 1) / (float) DEEP_SEIZE)) : f.value / FROST_MAX);
            sync(f, false);
            if (f.value <= 0 && f.deep <= 0 && f.sent <= 0 && now > f.immuneUntil) it.remove();
        }
    }
    /** Slows them by the share k of the most the frost may slow. */
    private static void slow(Frost f, float k) {
        float want = Math.round(k * IcemanConfig.f(IcemanConfig.FROST_SLOW) * 20) / 20f;
        if (want == f.slowApplied) return;
        f.slowApplied = want;
        var speed = f.body.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SLOW_ID);
        if (want > 0) speed.addTransientModifier(new AttributeModifier(SLOW_ID, "iceman_frost", -want, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    /** Tells everyone who sees the body its meter when it changed enough (or its stage did), and every second meanwhile. */
    private static void sync(Frost f, boolean force) {
        int stage = stage(f.value, f.deep > 0);
        f.sinceSent++;
        boolean changed = Math.abs(f.value - f.sent) >= 1.5f || stage != f.sentStage || (f.value <= 0 && f.sent > 0);
        if (!force && !(changed && f.sinceSent >= 2) && f.sinceSent < 20) return;
        if (!force && f.value <= 0 && f.sent <= 0 && f.deep <= 0) return;
        f.sent = f.value; f.sentStage = stage; f.sinceSent = 0;
        long now = f.body.level().getGameTime();
        send(f, FX_FROST, f.body.position(), new Vec3(f.deepAt < 0 ? -1 : now - f.deepAt, 0, 0), f.value, f.deep);
    }
    private static void send(Frost f, int kind, Vec3 pos, Vec3 dir, float power, int id) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> f.body), new IcemanFxPacket(kind, pos, dir, power, f.body.getId(), id));
    }

    // ------------------------------------------------------------------ the frozen cannot fight
    @SubscribeEvent(priority = EventPriority.HIGH) public static void attacked(LivingAttackEvent e) {
        if (e.getSource().getEntity() instanceof LivingEntity attacker && deep(attacker)) e.setCanceled(true);
    }
    @SubscribeEvent public static void playerAttack(AttackEntityEvent e) {
        if (deep(e.getEntity())) e.setCanceled(true);
    }
    @SubscribeEvent public static void died(LivingDeathEvent e) {
        Frost f = FROST.remove(e.getEntity().getId());
        if (f != null) { slow(f, 0); f.value = 0; f.deep = 0; sync(f, true); }
    }
}
