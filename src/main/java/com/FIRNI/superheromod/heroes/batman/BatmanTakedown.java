package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.entity.ModEntities;
import com.FIRNI.superheromod.core.film.FilmSessions;
import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * The Batmobile remote takedown on the server: a skill shot, high risk, high reward.
 * R with the Batmobile picked: the call (TD_SIGNAL: hand at the ear, BEEP BEEP, confirmed), then the dash (TD_DASH:
 * both arms thrown open, then straight along where he looks; his own client moves him). Only a real touch counts: his
 * swept body against theirs, no lock-on, no homing, no help when close. A touch starts the hold (TD_HOLD): the flip over
 * them, the tracker on the back of their head, the rear lock; the Batmobile is called on the tracker and makes its run
 * round them firing (BatmobileEntity); he drives them face first into the ground. A miss (TD_MISS): he is carried on
 * past, off balance and open to attack, touches his ear and the call is cancelled. The cooldown is spent either way.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BatmanTakedown {
    private BatmanTakedown() {}

    /** One takedown going on (the server's side): the dash's way, where he was a tick ago, the one he holds and where. */
    private static final class Run {
        Vec3 dir = new Vec3(0, 0, 1), last;
        LivingEntity target; Vec3 feet;
    }
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    /** Held on the spot (turned to face `front`) until `until`: the one taken down. */
    private record Held(LivingEntity e, Vec3 at, Vec3 front, long until) {}
    private static final Map<Integer, Held> HELD = new HashMap<>();
    /** How much wider than a body the touch is checked (a little forgiving, never homing). */
    private static final double REACH = .3;

    public static boolean held(LivingEntity e) { return HELD.containsKey(e.getId()); }

    /** R with the Batmobile picked: the call starts (true = the cooldown is spent). */
    static boolean start(ServerPlayer p, BatmanController.State s) {
        if (!p.onGround() && !p.isInWater() && p.getDeltaMovement().y < -.6) { BatmanController.tell(p, "Batmobil: önce yere in"); return false; }
        RUNS.put(p.getUUID(), new Run());
        s.aiming = false; s.charging = false; s.gliding = false;
        BatmanController.set(s, TD_SIGNAL);
        BatmanController.sound(p, ModSounds.BATMAN_TD_SIGNAL.get(), .9f, 1f);
        return true;
    }

    /** Every tick of his TD_* actions. */
    static void tick(ServerPlayer p, BatmanController.State s) {
        Run r = RUNS.computeIfAbsent(p.getUUID(), id -> new Run());
        p.fallDistance = 0;
        switch (s.action) {
            case TD_SIGNAL -> {
                if (s.age >= TD_SIGNAL_TICKS) {
                    // The dash: straight along where he looks now, flat.
                    r.dir = BatmanController.flat(p.getLookAngle());
                    r.last = p.position();
                    BatmanController.set(s, TD_DASH);
                    BatmanController.fx(p, FX_TD, p.position(), r.dir, 0, -1, p.getId());
                    BatmanController.sound(p, ModSounds.BATMAN_CAPE.get(), .9f, 1.15f);
                }
            }
            case TD_DASH -> {
                Vec3 now = p.position();
                if (s.age > TD_WIND) {
                    LivingEntity hit = touched(p, r.last == null ? now : r.last, now);
                    if (hit != null) { begin(p, s, r, hit); return; }
                }
                if (s.age == TD_WIND) BatmanController.sound(p, ModSounds.FX_WHOOSH_HEAVY.get(), 1f, 1.1f);
                r.last = now;
                if (s.age >= TD_DASH_TICKS) {
                    BatmanController.set(s, TD_MISS);
                    BatmanController.fx(p, FX_TD, p.position(), r.dir, 2, -1, p.getId());
                }
            }
            case TD_HOLD -> hold(p, s, r);
            case TD_MISS -> {
                if (s.age == TD_ABORT) BatmanController.sound(p, ModSounds.BATMAN_TD_ABORT.get(), .9f, 1f);
                if (s.age >= TD_MISS_TICKS) RUNS.remove(p.getUUID());
            }
            default -> {}
        }
    }

    /** The first body his swept body really touched between last tick and now (nearest along the way), or null. */
    private static LivingEntity touched(ServerPlayer p, Vec3 from, Vec3 to) {
        Vec3 a = from.add(0, .9, 0), b = to.add(0, .9, 0);
        if (a.distanceToSqr(b) < 1e-4) b = a.add(0, 0, 1e-3);
        AABB sweep = new AABB(a, b).inflate(1.5);
        LivingEntity best = null;
        double bestD = 1e9;
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, sweep, t -> BatmanController.targetable(p, t) && !(t instanceof ArmorStand))) {
            if (FilmSessions.busy(t.getUUID()) || held(t)) continue;
            // His body (.3 wide each way, 1.8 tall) swept along the segment against theirs: their box grown by his size.
            AABB box = t.getBoundingBox().inflate(.3 + REACH, .9, .3 + REACH);
            Optional<Vec3> on = box.clip(a, b);
            if (on.isEmpty() && !box.contains(b)) continue;
            double d = on.map(v -> v.distanceToSqr(a)).orElse(a.distanceToSqr(b));
            if (d < bestD) { bestD = d; best = t; }
        }
        return best;
    }

    /** A touch: the hold begins (the flip, the tracker, the lock, the car, the takedown). */
    private static void begin(ServerPlayer p, BatmanController.State s, Run r, LivingEntity t) {
        r.target = t;
        r.feet = t.position();
        BatmanController.set(s, TD_HOLD);
        s.noFall = true;
        Vec3 front = r.dir.scale(-1);
        HELD.put(t.getId(), new Held(t, r.feet, front, p.level().getGameTime() + TD_DOWN + 1));
        hold(t, r.feet, front);
        if (t instanceof Mob mob) { mob.setTarget(null); mob.getNavigation().stop(); }
        BatmanController.fx(p, FX_TD, r.feet, r.dir, 1, t.getId(), p.getId());
        BatmanController.at(p, t.position(), ModSounds.FX_WHOOSH_HEAVY.get(), 1.1f, .8f);
        BatmanController.at(p, t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, .7f, .7f);
    }

    private static void hold(ServerPlayer p, BatmanController.State s, Run r) {
        LivingEntity t = r.target;
        boolean live = t != null && t.isAlive() && !t.isRemoved() && t.level() == p.level();
        if (!live) { end(p, s); return; }
        int age = s.age;
        if (age == TD_TRACK) BatmanController.at(p, t.getEyePosition(), ModSounds.BATMAN_TD_TRACKER.get(), 1f, 1f);
        if (age == TD_LOCK) {
            BatmanController.at(p, t.position(), ModSounds.BATMAN_TD_LOCK.get(), 1f, 1f);
            BatmanController.at(p, t.position(), SoundEvents.ARMOR_EQUIP_IRON, .7f, .7f);
        }
        if (age == TD_CAR) call(p, t, r);
        if (age == TD_PUSH + 2) BatmanController.at(p, t.position(), ModSounds.FX_WHOOSH_LIGHT.get(), .9f, .7f);
        if (age == TD_IMPACT) {
            float dmg = BatmanConfig.f(BatmanConfig.TAKEDOWN_DAMAGE) * .4f;
            BatmanController.hurt(p, t, dmg);
            t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
            t.hurtMarked = true;
            BatmanController.at(p, t.position(), ModSounds.FX_IMPACT_HEAVY.get(), 1.4f, .7f);
            BatmanController.at(p, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .7f);
            BatmanController.at(p, t.position(), SoundEvents.GRAVEL_BREAK, 1.2f, .6f);
        }
        if (age >= TD_HOLD_TICKS) {
            if (t.isAlive()) BatmanStagger.apply(t, TD_DOWN - TD_HOLD_TICKS + STAGGER_TICKS);
            end(p, s);
        }
    }
    /** The tracker calls the car: it starts far off and comes round the front of them. */
    private static void call(ServerPlayer p, LivingEntity t, Run r) {
        BatmobileEntity car = ModEntities.BATMOBILE.get().create(p.level());
        if (car == null) return;
        Vec3 front = r.dir.scale(-1);
        float frontYaw = (float) Math.toDegrees(Math.atan2(-front.x, front.z));
        int hits = 0;
        for (int c = TakedownPath.FIRE_FROM; c < TakedownPath.FIRE_TO; c++) if ((c - TakedownPath.FIRE_FROM) % 2 == 1) hits++;
        float perHit = BatmanConfig.f(BatmanConfig.TAKEDOWN_DAMAGE) * .6f / Math.max(1, hits);
        car.setup(p, t, r.feet, frontYaw, TakedownPath.side(p.getId(), t.getId()), perHit);
        p.level().addFreshEntity(car);
    }
    private static void end(ServerPlayer p, BatmanController.State s) {
        RUNS.remove(p.getUUID());
        s.cooldowns[CD_BATMOBILE] = Math.max(s.cooldowns[CD_BATMOBILE], BatmanConfig.CD_BATMOBILE.get());
        BatmanController.set(s, IDLE);
    }
    /** Whatever he was doing of this is over (a hit that stopped him, death, the film). */
    static void cancel(ServerPlayer p) { RUNS.remove(p.getUUID()); }

    /** Held: still, no jumping, turned to face front (players are put back if they slip away). */
    private static void hold(LivingEntity t, Vec3 at, Vec3 front) {
        Vec3 m = t.getDeltaMovement();
        t.setDeltaMovement(0, Math.min(m.y, 0), 0);
        t.hurtMarked = true;
        if (t.tickCount % 10 == 0 || !t.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 7, false, false, false));
            t.addEffect(new MobEffectInstance(MobEffects.JUMP, 14, 128, false, false, false));
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-front.x, front.z));
        if (t instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setYRot(yaw); mob.yBodyRot = yaw; mob.yHeadRot = yaw;
        }
        Vec3 p = t.position();
        double dx = p.x - at.x, dz = p.z - at.z;
        if (dx * dx + dz * dz > .35 * .35) {
            if (t instanceof ServerPlayer sp) sp.teleportTo(at.x, p.y, at.z);
            else t.teleportTo(at.x, p.y, at.z);
        }
    }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || HELD.isEmpty()) return;
        for (Iterator<Held> it = HELD.values().iterator(); it.hasNext(); ) {
            Held h = it.next();
            if (h.e().isRemoved() || !h.e().isAlive() || h.e().level().getGameTime() > h.until()) {
                it.remove();
                if (!h.e().isRemoved()) { h.e().removeEffect(MobEffects.MOVEMENT_SLOWDOWN); h.e().removeEffect(MobEffects.JUMP); }
                continue;
            }
            hold(h.e(), h.at(), h.front());
        }
    }
    @SubscribeEvent public static void died(LivingDeathEvent e) { HELD.remove(e.getEntity().getId()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { RUNS.remove(e.getEntity().getUUID()); HELD.remove(e.getEntity().getId()); }
}
