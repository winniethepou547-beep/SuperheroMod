package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanConfig.f;

/**
 * The electric gauntlets on the server (gadget G_SHOCK; actions SHOCK_EQUIP, SHOCK_UNEQUIP, SHOCK_PUNCH): R puts them on
 * (they lock on, charge, the fists clap together at SHOCK_CLAP) or takes them off (the last discharge at DISCHARGE_AT);
 * worn, left click is a heavy electric boxing combo instead of the rapid punches: eight different blows in turn (right
 * straight, left cross, right uppercut, left hook, wide right hook, low left hook to the body, right shovel to the body,
 * left overhand), each one PREP → ACCELERATION → IMPACT → RECOVERY, clicks buffered. A blow that lands is decided here:
 * damage (half without charge), a short electric stagger, a short hit-stop (the blow is held STOP ticks longer, the
 * clients hold the pose), and a big share of the charge (State.energy). Idling drains it slowly; it never recharges while
 * worn: empty, they come off by themselves (as soon as the blow in hand is done) and the gadget waits SHOCK_EMPTY_COOLDOWN;
 * put on again, they are fully charged.
 * <p>
 * Sync: State.shock / State.energy are in the state packet; the rest rides on State.combo while they are worn (owned
 * here then): the blow count in the low byte, COMBO_HIT (the current blow landed: the clients hold the pose for the
 * hit-stop), COMBO_OFF (the electricity is off), COMBO_DRY (the current blow was thrown without charge).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BatmanShock {
    private BatmanShock() {}

    // ------------------------------------------------------------------ effects (BatmanFxPacket kinds 40..49)
    /**
     * The clap (pos = between his fists, power 1 charged / 0 dead); a blow that landed (pos = contact, dir = the blow's
     * way, power = blow + 100 if dry, entity = the one hit); a blow that missed (pos = before the fist, same power);
     * the charge ran out; the charge is back; the last discharge as they come off. id = Batman for all of them.
     */
    public static final int FX_SHOCK_CLAP = 40, FX_SHOCK_HIT = 41, FX_SHOCK_MISS = 42, FX_SHOCK_EMPTY = 43, FX_SHOCK_READY = 44,
            FX_SHOCK_DISCHARGE = 45;

    // ------------------------------------------------------------------ what State.combo carries while they are worn
    public static final int COMBO_COUNT = 255, COMBO_HIT = 256, COMBO_OFF = 512, COMBO_DRY = 1024;

    // ------------------------------------------------------------------ the blows
    public static final int BLOWS = 8;
    public static final int S_STRAIGHT = 0, S_CROSS = 1, S_UPPER = 2, S_HOOK = 3, S_WIDE = 4, S_LOW = 5, S_BODY = 6, S_OVER = 7;
    /** Each blow's length and the tick it lands on (heavier blows load longer), the hand (0 right), heavy or not, damage share. */
    public static final int[] BLOW_TICKS = {12, 12, 15, 15, 14, 13, 13, 15};
    public static final int[] BLOW_HIT = {5, 5, 7, 7, 7, 6, 6, 7};
    public static final int[] BLOW_SIDE = {0, 1, 0, 1, 0, 1, 0, 1};
    public static final boolean[] BLOW_HEAVY = {false, false, true, true, true, false, false, true};
    public static final float[] BLOW_POWER = {1f, 1f, 1.2f, 1.15f, 1.1f, 1f, 1.05f, 1.15f};
    /** The hit-stop: a blow that lands is held this many ticks longer (the heavy ones a little more). */
    public static final int STOP = 2, STOP_HEAVY = 3;
    /** A click within CHAIN ticks of the end of a blow carries on to the next blow; a longer pause starts again from the first. */
    public static final int CHAIN = 12;
    /** Coming off: the last discharge. */
    public static final int DISCHARGE_AT = 6;
    /** How far a blow reaches beyond his normal punches. */
    static final double EXTRA_REACH = .4;

    public static int blow(int combo) { return (Math.max(0, combo) & COMBO_COUNT) % BLOWS; }
    public static boolean landed(int combo) { return combo > 0 && (combo & COMBO_HIT) != 0; }
    public static boolean off(int combo) { return combo > 0 && (combo & COMBO_OFF) != 0; }
    public static boolean dry(int combo) { return combo > 0 && (combo & COMBO_DRY) != 0; }
    public static int stop(int blow) { return BLOW_HEAVY[blow] ? STOP_HEAVY : STOP; }
    /** Ticks the current blow lasts (with the hit-stop when it landed). */
    public static int length(int combo) { int b = blow(combo); return BLOW_TICKS[b] + (landed(combo) ? stop(b) : 0); }

    /** What the State does not hold: the electricity off, ticks since the last blow, whether this blow was charged, combo written by us. */
    private static final class Extra { boolean off, charged, dirty, spent; int rest; }
    private static final Map<UUID, Extra> EXTRA = new HashMap<>();
    private static Extra extra(ServerPlayer p) { return EXTRA.computeIfAbsent(p.getUUID(), id -> new Extra()); }

    // ------------------------------------------------------------------ R: on / off
    /** R with the gauntlets picked: on or off (true = done, the cooldown is spent). */
    static boolean toggle(ServerPlayer p, BatmanController.State s) {
        if (BatmanController.busy(s)) return false;
        Extra x = extra(p);
        s.queued = false;
        if (s.shock) {
            s.shock = false;
            BatmanController.set(s, SHOCK_UNEQUIP);
            BatmanController.sound(p, ModSounds.BATMAN_SHOCK_UNEQUIP.get(), 1f, 1f);
            BatmanController.sound(p, SoundEvents.ARMOR_EQUIP_IRON, .45f, 1.3f);
        } else {
            s.shock = true;
            // Put on again: fully charged.
            s.energy = 1; x.off = false; x.spent = false;
            s.combo = 0;
            s.lastBlowEnd = -1000;
            x.dirty = true; x.rest = 0;
            BatmanController.set(s, SHOCK_EQUIP);
            BatmanController.sound(p, ModSounds.BATMAN_SHOCK_EQUIP.get(), 1f, 1f);
            BatmanController.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE, .7f, 1.15f);
        }
        return true;
    }

    // ------------------------------------------------------------------ left click: the heavy electric boxing combo
    /** Left click while they are worn (or going on: then nothing, he does not attack before the clap). */
    static void click(ServerPlayer p, BatmanController.State s) {
        if (s.action == SHOCK_PUNCH) { s.queued = true; return; }
        if (!s.shock || s.action == SHOCK_EQUIP || BatmanController.busy(s) || s.charging) return;
        long now = p.level().getGameTime();
        int count = Math.max(0, s.combo) & COMBO_COUNT;
        start(p, s, s.combo >= 0 && now - s.lastBlowEnd <= CHAIN ? count + 1 : 0);
    }
    private static void start(ServerPlayer p, BatmanController.State s, int count) {
        Extra x = extra(p);
        x.charged = !x.off && s.energy > 0;
        x.rest = 0; x.dirty = true;
        s.combo = (count & COMBO_COUNT) | (x.off ? COMBO_OFF : 0) | (x.charged ? 0 : COMBO_DRY);
        s.queued = false;
        BatmanController.set(s, SHOCK_PUNCH);
        int b = blow(s.combo);
        // A heavy step into the one in front: the blow carries him in.
        LivingEntity t = BatmanController.inFront(p, 5.5, .55);
        if (t != null) {
            Vec3 to = t.position().subtract(p.position());
            double d = Math.sqrt(to.x * to.x + to.z * to.z);
            if (d > 2.2) {
                Vec3 dir = new Vec3(to.x / d, 0, to.z / d);
                double v = Math.min(.6, (d - 1.8) * .26);
                p.setDeltaMovement(dir.x * v, p.getDeltaMovement().y, dir.z * v);
                p.hurtMarked = true;
            }
        }
        float pitch = (BLOW_HEAVY[b] ? .82f : .95f) + p.getRandom().nextFloat() * .12f;
        BatmanController.sound(p, ModSounds.FX_WHOOSH_HEAVY.get(), .4f, pitch + .1f);
        if (x.charged) BatmanController.sound(p, ModSounds.BATMAN_SHOCK_SWING.get(), .55f, pitch);
    }
    /** Every tick of SHOCK_EQUIP, SHOCK_UNEQUIP and SHOCK_PUNCH. */
    static void tick(ServerPlayer p, BatmanController.State s) {
        switch (s.action) {
            case SHOCK_EQUIP -> {
                // The locks: small clicks the clients see as the plates seating.
                if (s.age == 4 || s.age == 8) BatmanController.sound(p, SoundEvents.ARMOR_EQUIP_CHAIN, .5f, 1.5f + s.age * .03f);
                if (s.age == SHOCK_CLAP) clap(p, s);
            }
            case SHOCK_UNEQUIP -> {
                if (s.age == DISCHARGE_AT) {
                    boolean live = !extra(p).off && s.energy > 0;
                    BatmanController.fx(p, FX_SHOCK_DISCHARGE, between(p), p.getLookAngle(), live ? 1 : 0, p.getId(), p.getId());
                }
                if (s.age >= SHOCK_UNEQUIP_TICKS) { s.combo = -1; extra(p).dirty = false; }
            }
            case SHOCK_PUNCH -> punchTick(p, s);
            default -> {}
        }
    }
    private static void punchTick(ServerPlayer p, BatmanController.State s) {
        int b = blow(s.combo);
        if (s.age == BLOW_HIT[b]) land(p, s, b);
        if (s.age >= length(s.combo)) {
            s.lastBlowEnd = p.level().getGameTime();
            if (s.queued && s.shock) start(p, s, (Math.max(0, s.combo) & COMBO_COUNT) + 1);
            else { s.combo &= ~(COMBO_HIT | COMBO_DRY); s.queued = false; BatmanController.set(s, IDLE); }
        }
    }
    /** The blow reaches full extension: on a body the electric hit, otherwise the miss. */
    private static void land(ServerPlayer p, BatmanController.State s, int b) {
        Extra x = extra(p);
        boolean charged = x.charged && !x.off && s.energy > 0;
        Vec3 look = BatmanController.flat(p.getLookAngle());
        int side = BLOW_SIDE[b];
        float code = b + (charged ? 0 : 100);
        LivingEntity t = BatmanController.inFront(p, BatmanConfig.PUNCH_REACH.get() + EXTRA_REACH, .5);
        if (t == null) {
            // The miss: a crack of electricity in the air before the fist, a little pressure; almost no charge spent.
            Vec3 at = BatmanController.hand(p, side).add(look.scale(.85)).add(0, b == S_LOW || b == S_BODY ? -.45 : b == S_UPPER ? .1 : 0, 0);
            BatmanController.fx(p, FX_SHOCK_MISS, at, look, code, -1, p.getId());
            if (charged) {
                BatmanController.at(p, at, ModSounds.BATMAN_SHOCK_MISS.get(), .6f, .9f + p.getRandom().nextFloat() * .2f);
                drain(p, s, x, .008f);
            }
            return;
        }
        Vec3 dir = BatmanController.flat(t.position().subtract(p.position()));
        float damage = f(BatmanConfig.SHOCK_DAMAGE) * BLOW_POWER[b] * (charged ? 1 : .5f);
        BatmanController.hurt(p, t, damage);
        // Knocked back by the weight of it: hooks carry them sideways, the uppercut lifts, the overhand drives down.
        double knock = BatmanConfig.PUNCH_KNOCK.get() * (BLOW_HEAVY[b] ? 1.7 : 1.15) * (charged ? 1.15 : .9);
        Vec3 across = new Vec3(-dir.z, 0, dir.x).scale(b == S_HOOK ? -.22 : b == S_WIDE ? .22 : 0);
        Vec3 m = t.getDeltaMovement();
        double vy = b == S_UPPER ? Math.max(m.y, .5) : b == S_OVER ? Math.min(m.y, -.08) : m.y;
        t.setDeltaMovement(m.x * .3 + dir.x * knock + across.x, vy, m.z * .3 + dir.z * knock + across.z);
        t.hurtMarked = true;
        if (charged) {
            // The electric stagger: a jolt through them, slowed a moment, a mob forgets its step. Short: this is a damage move.
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BLOW_HEAVY[b] ? 12 : 8, 2, false, false, false));
            if (t instanceof Mob mob) mob.getNavigation().stop();
            drain(p, s, x, f(BatmanConfig.SHOCK_ENERGY_PER_HIT));
        }
        s.combo |= COMBO_HIT;
        // Where the fist met them: the near side of their body, at the height the blow was aimed.
        double h = t.getBbHeight(), y = b == S_LOW || b == S_BODY ? .45 : b == S_UPPER ? .82 : .74;
        Vec3 at = t.position().add(0, h * y, 0).subtract(dir.scale(t.getBbWidth() * .5));
        BatmanController.fx(p, FX_SHOCK_HIT, at, look, code, t.getId(), p.getId());
        float pitch = .9f + p.getRandom().nextFloat() * .2f;
        BatmanController.at(p, at, ModSounds.BATMAN_PUNCH.get(), .9f, pitch - .12f);
        BatmanController.at(p, at, SoundEvents.PLAYER_ATTACK_STRONG, .6f, .8f);
        if (charged) {
            BatmanController.at(p, at, ModSounds.BATMAN_SHOCK_HIT.get(), BLOW_HEAVY[b] ? 1.25f : 1.05f, pitch);
            BatmanController.at(p, at, ModSounds.FX_ELECTRIC_ZAP.get(), .45f, 1.2f + p.getRandom().nextFloat() * .3f);
        }
        if (BLOW_HEAVY[b]) BatmanController.at(p, at, ModSounds.FX_IMPACT_HEAVY.get(), charged ? .6f : .45f, 1.25f);
    }
    /** The fists clap together: charged (or, with the charge gone, a dead clank). */
    private static void clap(ServerPlayer p, BatmanController.State s) {
        boolean live = !extra(p).off && s.energy > 0;
        Vec3 at = between(p);
        BatmanController.fx(p, FX_SHOCK_CLAP, at, p.getLookAngle(), live ? 1 : 0, p.getId(), p.getId());
        if (live) {
            BatmanController.at(p, at, ModSounds.BATMAN_SHOCK_CLAP.get(), 1.3f, 1f);
            BatmanController.at(p, at, SoundEvents.ANVIL_LAND, .18f, 1.9f);
            BatmanController.at(p, at, ModSounds.FX_ELECTRIC_ZAP.get(), .6f, .85f);
        } else {
            BatmanController.at(p, at, SoundEvents.ARMOR_EQUIP_NETHERITE, .9f, .75f);
            BatmanController.at(p, at, ModSounds.BATMAN_PUNCH.get(), .5f, .7f);
        }
    }
    /** About where his fists meet before his chest. */
    private static Vec3 between(ServerPlayer p) {
        return p.getEyePosition().add(0, -.55, 0).add(BatmanController.flat(p.getLookAngle()).scale(.55));
    }

    // ------------------------------------------------------------------ the charge
    private static void drain(ServerPlayer p, BatmanController.State s, Extra x, float amount) {
        if (x.off) return;
        s.energy = Math.max(0, s.energy - amount);
        if (s.energy <= 1e-4f) empty(p, s, x);
    }
    /** The charge is gone: the electricity goes out (they stay on his hands). */
    private static void empty(ServerPlayer p, BatmanController.State s, Extra x) {
        x.off = true; x.rest = 0;
        s.energy = 0;
        if (s.combo >= 0) s.combo |= COMBO_OFF;
        BatmanController.fx(p, FX_SHOCK_EMPTY, between(p), Vec3.ZERO, 0, p.getId(), p.getId());
        BatmanController.sound(p, ModSounds.BATMAN_SHOCK_EMPTY.get(), .9f, 1f);
        BatmanController.sound(p, SoundEvents.FIRE_EXTINGUISH, .25f, 1.7f);
        x.spent = true;
        BatmanController.tell(p, "Elektrikli Muşta: enerji bitti, çıkarılıyor");
    }

    /**
     * Every tick for every Batman: while worn the charge drains slowly (not while they lock on) and never comes back;
     * empty, they come off by themselves once the blow in hand is done, and the gadget cools down. The flags on
     * State.combo follow. Dead: they come off.
     */
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!BatmanController.isHero(p)) { EXTRA.remove(p.getUUID()); return; }
        BatmanController.State s = BatmanController.peek(p);
        if (s == null) return;
        Extra x = extra(p);
        if (!p.isAlive()) {
            if (s.shock) s.shock = false;
            if (x.dirty) { s.combo = -1; x.dirty = false; }
            return;
        }
        if (s.shock) {
            if (s.action != SHOCK_PUNCH) x.rest++;
            if (!x.off) {
                if (s.action != SHOCK_EQUIP) s.energy = Math.max(0, s.energy - 1f / (float) (BatmanConfig.SHOCK_DRAIN_SECONDS.get() * 20));
                if (s.energy <= 1e-4f) empty(p, s, x);
            }
            if (x.spent && s.action != SHOCK_PUNCH && !BatmanController.busy(s)) {
                // Spent: off they come, and the gadget waits.
                x.spent = false;
                s.shock = false;
                s.queued = false;
                BatmanController.set(s, SHOCK_UNEQUIP);
                BatmanController.sound(p, ModSounds.BATMAN_SHOCK_UNEQUIP.get(), 1f, .9f);
                BatmanController.sound(p, SoundEvents.ARMOR_EQUIP_IRON, .45f, 1.2f);
                s.cooldowns[G_SHOCK] = (int) Math.round(BatmanConfig.SHOCK_EMPTY_COOLDOWN.get() * 20);
                return;
            }
            s.combo = (Math.max(0, s.combo) & ~COMBO_OFF) | (x.off ? COMBO_OFF : 0);
            x.dirty = true;
        } else if (x.dirty && s.action != SHOCK_UNEQUIP) {
            // Taken off by something else than the unequip move (interrupted): his normal punches get their counter back.
            s.combo = -1;
            x.dirty = false;
        }
    }
    /** Back from death: off his hands, charged again. */
    @SubscribeEvent public static void respawned(PlayerEvent.Clone e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        BatmanController.State s = BatmanController.peek(p);
        Extra x = EXTRA.get(p.getUUID());
        if (x != null) { x.off = false; x.rest = 0; }
        if (s == null || !e.isWasDeath()) return;
        s.shock = false;
        s.energy = 1;
        if (x != null && x.dirty) { s.combo = -1; x.dirty = false; }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { EXTRA.remove(e.getEntity().getUUID()); }
}
