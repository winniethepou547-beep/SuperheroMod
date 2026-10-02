package com.FIRNI.superheromod.heroes.thor;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.core.film.FilmSessions;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ThorFxPacket;
import com.FIRNI.superheromod.network.packet.ThorStatePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

import static com.FIRNI.superheromod.heroes.thor.ThorAction.*;

/**
 * Thor on the server: what each key does, when a swing connects, where the thrown hammer is,
 * the guard and its parry, and the Wakanda strike. Clients only animate what this decides;
 * the hammer launch and the strike's rise and dive are steered by Thor's own client.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class ThorController {
    private static final int HAND = 0, OUT = 1, HOLD = 2, RETURN = 3;

    private static final class State {
        int action = IDLE, age;
        int comboStep, sinceSwing = 999;
        boolean queued;
        boolean flying, safeFall;
        int hammer = HAND, hammerAge, throwCooldown;
        Vec3 hammerPos = Vec3.ZERO, hammerVel = Vec3.ZERO;
        final Set<Integer> hammerHits = new HashSet<>();
        boolean guardHeld;
        int guardCooldown, wakandaCooldown, ultimateCooldown, dashCooldown, beamCooldown;
        float charge;
        final Set<Integer> dashHits = new HashSet<>();
        /** The body stuck on the hammer's head during a launch (-1: none). */
        int carried = -1;
        int poweredTicks;
        boolean dirty = true;
    }
    private static final Map<UUID, State> STATES = new HashMap<>();

    private ThorController() {}

    public static boolean isThor(Entity e) { return e instanceof ServerPlayer && ID.equals(AbilityManager.getCharacterId(e.getUUID())); }
    private static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), id -> new State()); }
    private static void set(State s, int action) { s.action = action; s.age = 0; s.dirty = true; }
    private static boolean busy(State s) { return s.action == WAKANDA || s.action == ULTIMATE; }

    // ------------------------------------------------------------------ keys
    public static void press(ServerPlayer p, AbilitySlot slot) {
        State s = state(p);
        if (FilmSessions.busy(p.getUUID())) return;
        switch (slot) {
            case LMB -> swing(p, s);
            case RMB -> throwOrRecall(p, s);
            case SHIFT -> charge(p, s);
            case SKILL_F -> beam(p, s);
            case SKILL_V -> guard(p, s);
            case SKILL_E -> wakanda(p, s);
            case SKILL_X -> ultimate(p, s);
            default -> {}
        }
    }
    public static void release(ServerPlayer p, AbilitySlot slot) {
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (slot == AbilitySlot.SHIFT && s.action == CHARGE) launch(p, s);
        if (slot == AbilitySlot.SKILL_V) {
            s.guardHeld = false;
            if (s.action == GUARD) { set(s, IDLE); s.guardCooldown = GUARD_COOLDOWN; }
        }
    }

    private static void swing(ServerPlayer p, State s) {
        if (busy(s) || s.action == GUARD || s.action == COUNTER || s.action == CHARGE || s.action == DASH || s.action == BEAM) return;
        if (s.hammer != HAND) { recall(s); return; }
        if (s.action == SWING_RIGHT || s.action == SWING_LEFT) { if (s.age >= 3) s.queued = true; return; }
        if (s.action == UPPERCUT || s.action == THROW) return;
        // A click inside the grace after a swing carries on into the next one.
        int next = s.sinceSwing <= CHAIN_GRACE ? s.comboStep + 1 : 1;
        startSwing(p, s, next > 3 ? 1 : next);
    }
    private static void startSwing(ServerPlayer p, State s, int step) {
        s.comboStep = step; s.queued = false; s.sinceSwing = 999;
        set(s, step == 1 ? SWING_RIGHT : step == 2 ? SWING_LEFT : UPPERCUT);
        sound(p, step == 3 ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_SWEEP, .8f, step == 3 ? .7f : .85f + step * .08f);
    }

    private static void throwOrRecall(ServerPlayer p, State s) {
        if (s.hammer != HAND) { recall(s); return; }
        if (busy(s) || s.action == GUARD || s.throwCooldown > 0 || s.action == THROW || s.action == CHARGE || s.action == DASH || s.action == BEAM) return;
        set(s, THROW);
        s.comboStep = 0;
    }
    private static void recall(State s) {
        if (s.hammer == OUT || s.hammer == HOLD) { s.hammer = RETURN; s.hammerAge = 0; s.dirty = true; }
    }

    /** Shift held: Mjolnir whirls at his side, winding up; the longer, the further the launch. */
    private static void charge(ServerPlayer p, State s) {
        if (busy(s) || s.dashCooldown > 0 || s.action == CHARGE || s.action == DASH || s.action == GUARD || s.action == BEAM) return;
        if (s.hammer != HAND) { recall(s); return; }
        set(s, CHARGE);
        s.comboStep = 0;
        sound(p, SoundEvents.TRIDENT_RIPTIDE_1, .6f, 1.5f);
    }
    /** Shift let go: the hammer is thrown forward at arm's length and pulls him after it. */
    private static void launch(ServerPlayer p, State s) {
        s.charge = Math.min(1, s.age / (float) CHARGE_FULL);
        set(s, DASH);
        s.safeFall = true;
        s.dashHits.clear();
        s.carried = -1;
        fx(p, FX_TAKEOFF, p.position(), p.getLookAngle(), .5f + .5f * s.charge);
        sound(p, SoundEvents.TRIDENT_RIPTIDE_3, .7f + .4f * s.charge, 1.2f - .3f * s.charge);
        if (s.charge >= 1) sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, .5f, 1.6f);
    }
    /**
     * The first body the hammer meets is struck and stays stuck to its head: carried along in
     * front of him for the rest of the launch, then thrown off where it ends.
     */
    private static void dashHits(ServerPlayer p, State s) {
        Vec3 dir = p.getLookAngle();
        if (s.carried < 0) {
            for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.3).expandTowards(dir.scale(1.2)),
                    t -> t != p && t.isAlive() && !t.isSpectator() && !s.dashHits.contains(t.getId()))) {
                s.dashHits.add(t.getId());
                t.invulnerableTime = 0;
                t.hurt(p.damageSources().playerAttack(p), 3 + 4 * s.charge);
                fx(p, FX_HAMMER_HIT, t.position().add(0, t.getBbHeight() * .55, 0), dir, .6f + .4f * s.charge);
                sound(p, SoundEvents.ANVIL_LAND, .6f, 1.2f);
                sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .7f);
                s.carried = t.getId(); s.dirty = true;
                break;
            }
        }
        carry(p, s, false);
    }
    /** Keeps the carried body on the hammer's head; at the end of the launch flings it on. */
    private static void carry(ServerPlayer p, State s, boolean letGo) {
        if (s.carried < 0) return;
        Entity e = p.level().getEntity(s.carried);
        if (!(e instanceof LivingEntity t) || !t.isAlive()) { s.carried = -1; s.dirty = true; return; }
        Vec3 dir = p.getLookAngle();
        // The server hears where he is a tick late; lead by one tick of the launch so the body sits on
        // the hammer's head, not behind him.
        double lead = s.age <= dashTicks(s.charge) ? dashSpeed(s.charge) : 0;
        Vec3 at = p.position().add(dir.scale(CARRY_AHEAD + lead)).add(0, Math.max(-.6, dir.y * .4), 0);
        t.fallDistance = 0;
        if (letGo) {
            Vec3 fling = dir.scale(dashSpeed(s.charge) * CARRY_FLING).add(0, .35, 0);
            t.setDeltaMovement(fling);
            t.hurtMarked = true;
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), 2 + 3 * s.charge);
            fx(p, FX_HAMMER_HIT, t.position().add(0, t.getBbHeight() * .55, 0), dir, .8f);
            s.carried = -1; s.dirty = true;
            return;
        }
        float facing = p.getYRot() + 180;
        if (t instanceof ServerPlayer other) other.connection.teleport(at.x, at.y, at.z, facing, 0);
        else { t.teleportTo(at.x, at.y, at.z); t.setYRot(facing); t.yBodyRot = facing; t.yHeadRot = facing; }
        t.setDeltaMovement(p.getDeltaMovement());
    }

    /** F: hammer to the sky, a bolt comes down into it, then two seconds of lightning where he looks. */
    private static void beam(ServerPlayer p, State s) {
        if (busy(s) || s.beamCooldown > 0 || s.action == BEAM || s.action == GUARD || s.action == CHARGE || s.action == DASH) return;
        if (s.hammer != HAND) { recall(s); return; }
        set(s, BEAM);
        s.comboStep = 0;
        s.poweredTicks = BM_TOTAL;
        sound(p, SoundEvents.TRIDENT_RIPTIDE_2, .8f, .7f);
    }
    private static void tickBeam(ServerPlayer p, State s) {
        if (s.age == BM_SKY) {
            fx(p, FX_SKY_BOLT, p.position().add(0, 2.9, 0), new Vec3(0, 1, 0), .7f);
            sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 1.1f);
        }
        if (s.age >= BM_AIM && s.age < BM_END) {
            if ((s.age - BM_AIM) % 8 == 0) sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, .8f, .7f + p.getRandom().nextFloat() * .3f);
            if (s.age == BM_AIM) sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, .8f, 1.5f);
            if ((s.age - BM_AIM) % BM_HIT_EVERY == 0) beamHits(p);
        }
        if (s.age >= BM_TOTAL) { set(s, IDLE); s.beamCooldown = BM_COOLDOWN; }
    }
    private static void beamHits(ServerPlayer p) {
        Vec3 from = p.getEyePosition(), look = p.getLookAngle(), to = from.add(look.scale(BM_RANGE));
        var block = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        Vec3 end = to;
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(BM_RADIUS),
                t -> t != p && t.isAlive() && !t.isSpectator())) {
            if (t.getBoundingBox().inflate(BM_RADIUS).clip(from, end).isEmpty()) continue;
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), BM_DAMAGE);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
            fx(p, FX_BEAM_HIT, t.position().add(0, t.getBbHeight() * .55, 0), look, 1);
        }
    }

    private static void guard(ServerPlayer p, State s) {
        if (busy(s) || s.hammer != HAND || s.guardCooldown > 0 || s.action == GUARD || s.action == THROW || s.action == CHARGE || s.action == DASH || s.action == BEAM) return;
        set(s, GUARD);
        s.guardHeld = true;
        s.comboStep = 0;
        sound(p, SoundEvents.TRIDENT_RIPTIDE_2, .6f, 1.6f);
    }

    private static void wakanda(ServerPlayer p, State s) {
        if (busy(s) || s.wakandaCooldown > 0) return;
        if (s.hammer != HAND) { recall(s); return; }
        s.flying = false; s.safeFall = true; s.comboStep = 0;
        set(s, WAKANDA);
        sound(p, SoundEvents.BEACON_POWER_SELECT, .7f, .6f);
    }

    private static void ultimate(ServerPlayer p, State s) {
        if (busy(s) || s.ultimateCooldown > 0) return;
        if (s.hammer != HAND) { recall(s); return; }
        LivingEntity target = GodOfThunderSession.findTarget(p);
        if (target == null) { p.displayClientMessage(Component.literal("§bGOD OF THUNDER: menzilde hedef yok"), true); return; }
        s.flying = false; s.safeFall = true; s.comboStep = 0;
        if (GodOfThunderSession.start(p, target)) { set(s, ULTIMATE); s.ultimateCooldown = ULT_COOLDOWN; }
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!isThor(p)) {
            if (STATES.remove(p.getUUID()) != null) send(p, new State());
            return;
        }
        State s = state(p);
        s.age++;
        if (s.sinceSwing < 999) s.sinceSwing++;
        if (s.throwCooldown > 0) s.throwCooldown--;
        if (s.guardCooldown > 0) s.guardCooldown--;
        if (s.wakandaCooldown > 0) s.wakandaCooldown--;
        if (s.ultimateCooldown > 0) s.ultimateCooldown--;
        if (s.dashCooldown > 0) s.dashCooldown--;
        if (s.beamCooldown > 0) s.beamCooldown--;
        if (s.poweredTicks > 0) s.poweredTicks--;
        if (s.flying || s.safeFall) p.fallDistance = 0;
        if (s.safeFall && !s.flying && p.onGround() && s.action != WAKANDA && s.action != DASH) s.safeFall = false;

        switch (s.action) {
            case SWING_RIGHT, SWING_LEFT -> {
                if (s.age == SWING_HIT) hitArc(p, s, false);
                if (s.age >= SWING_TICKS) {
                    if (s.queued) startSwing(p, s, s.comboStep + 1);
                    else { set(s, IDLE); s.sinceSwing = 0; }
                }
            }
            case UPPERCUT -> {
                if (s.age == UPPER_HIT) hitArc(p, s, true);
                if (s.age >= UPPER_TICKS) { set(s, IDLE); s.comboStep = 0; }
            }
            case THROW -> {
                if (s.age == THROW_WINDUP) launchHammer(p, s);
                if (s.age >= THROW_WINDUP + 6) set(s, IDLE);
            }
            case CATCH -> { if (s.age >= CATCH_TICKS) set(s, IDLE); }
            case CHARGE -> {
                if (s.age == CHARGE_FULL) { fx(p, FX_CHARGED, p.position(), Vec3.ZERO, 1); sound(p, SoundEvents.BEACON_POWER_SELECT, .7f, 1.8f); s.poweredTicks = 999; }
                else if (s.age % 7 == 0) sound(p, SoundEvents.TRIDENT_RIPTIDE_1, .25f + .2f * Math.min(1, s.age / (float) CHARGE_FULL), 1.4f + .4f * Math.min(1, s.age / (float) CHARGE_FULL));
            }
            case DASH -> {
                p.fallDistance = 0;
                if (s.age <= dashTicks(s.charge)) dashHits(p, s);
                else if (s.carried >= 0) carry(p, s, true);
                if (s.age >= dashTicks(s.charge) + 3) { set(s, IDLE); s.dashCooldown = DASH_COOLDOWN; s.poweredTicks = 0; }
            }
            case BEAM -> tickBeam(p, s);
            case GUARD -> {
                if (!s.guardHeld || s.age >= GUARD_MAX) { set(s, IDLE); s.guardCooldown = GUARD_COOLDOWN; }
                else if (s.age % 6 == 0) sound(p, SoundEvents.TRIDENT_RIPTIDE_1, .25f, 1.9f);
            }
            case COUNTER -> { if (s.age >= COUNTER_TICKS) { if (s.guardHeld) { set(s, GUARD); s.age = GUARD_PERFECT + 1; } else { set(s, IDLE); s.guardCooldown = GUARD_COOLDOWN; } } }
            case WAKANDA -> tickWakanda(p, s);
            case ULTIMATE -> {
                s.poweredTicks = 40;
                if (!FilmSessions.playing(p.getUUID(), GodOfThunderSession.ID)) set(s, IDLE);
            }
            default -> {}
        }
        if (s.hammer != HAND) tickHammer(p, s);
        send(p, s);
    }

    // ------------------------------------------------------------------ combo hits
    private static void hitArc(ServerPlayer p, State s, boolean upper) {
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 chest = p.position().add(0, 1.1, 0);
        boolean any = false;
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SWING_REACH),
                t -> t != p && t.isAlive() && !t.isSpectator())) {
            Vec3 to = t.position().add(0, t.getBbHeight() * .5, 0).subtract(chest);
            double dist = to.length();
            if (dist > SWING_REACH + t.getBbWidth() * .5) continue;
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() > .04 && flat.normalize().dot(look) < SWING_CONE) continue;
            t.invulnerableTime = 0;
            if (!t.hurt(p.damageSources().playerAttack(p), upper ? UPPER_DAMAGE : SWING_DAMAGE)) continue;
            any = true;
            Vec3 at = t.position().add(0, t.getBbHeight() * .6, 0);
            if (upper) {
                t.setDeltaMovement(look.x * .25, UPPER_LAUNCH, look.z * .25);
                t.hurtMarked = true;
                fx(p, FX_UPPER, at, new Vec3(0, 1, 0), 1);
            } else {
                Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize().scale(s.action == SWING_RIGHT ? 1 : -1);
                t.knockback(.55, -(look.x + side.x * .6), -(look.z + side.z * .6));
                fx(p, FX_SWING_HIT, at, side, .8f);
            }
        }
        if (any) {
            sound(p, upper ? SoundEvents.ANVIL_LAND : SoundEvents.PLAYER_ATTACK_KNOCKBACK, upper ? .5f : .9f, upper ? 1.4f : .7f);
            if (upper) sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, .7f, 1.5f);
            s.poweredTicks = 30;
        }
    }

    // ------------------------------------------------------------------ the thrown hammer
    private static Vec3 hand(ServerPlayer p) {
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        return p.position().add(0, p.getBbHeight() * .62, 0).add(right.scale(.4)).add(look.scale(.15));
    }
    private static void launchHammer(ServerPlayer p, State s) {
        s.hammer = OUT; s.hammerAge = 0; s.hammerHits.clear(); s.dirty = true;
        s.hammerPos = p.getEyePosition().add(p.getLookAngle().scale(.8));
        s.hammerVel = p.getLookAngle().scale(THROW_SPEED);
        s.throwCooldown = THROW_COOLDOWN;
        fx(p, FX_RELEASE, s.hammerPos, p.getLookAngle(), 1);
        sound(p, SoundEvents.TRIDENT_THROW, 1f, .7f);
        sound(p, SoundEvents.TRIDENT_THUNDER, .25f, 1.8f);
    }
    private static void tickHammer(ServerPlayer p, State s) {
        s.hammerAge++;
        ServerLevel level = p.serverLevel();
        switch (s.hammer) {
            case OUT -> {
                Vec3 from = s.hammerPos, to = from.add(s.hammerVel);
                var block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
                LivingEntity hit = sweep(p, s, from, to);
                if (hit != null) {
                    Vec3 at = hit.position().add(0, hit.getBbHeight() * .55, 0);
                    hit.invulnerableTime = 0;
                    hit.hurt(p.damageSources().playerAttack(p), THROW_DAMAGE);
                    Vec3 dir = s.hammerVel.normalize();
                    hit.knockback(.9, -dir.x, -dir.z);
                    hit.setDeltaMovement(hit.getDeltaMovement().add(0, .25, 0));
                    hit.hurtMarked = true;
                    fx(p, FX_HAMMER_HIT, at, dir, 1);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .7f, 1.2f);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1f, 1.3f);
                    s.hammerPos = at.subtract(dir.scale(.4));
                    s.hammer = HOLD; s.hammerAge = 0; s.dirty = true;
                    s.poweredTicks = 30;
                } else if (block.getType() != HitResult.Type.MISS) {
                    s.hammerPos = to.subtract(s.hammerVel.normalize().scale(.3));
                    fx(p, FX_HAMMER_HIT, s.hammerPos, s.hammerVel.normalize(), .7f);
                    level.playSound(null, to.x, to.y, to.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .5f, 1.5f);
                    s.hammer = HOLD; s.hammerAge = 0; s.dirty = true;
                } else {
                    s.hammerPos = to;
                    if (s.hammerAge * THROW_SPEED >= THROW_RANGE) { s.hammer = HOLD; s.hammerAge = 0; }
                }
            }
            case HOLD -> {
                // A short hang in the air (or in the wall) before it answers the call.
                if (s.hammerAge >= THROW_RETURN_DELAY) { s.hammer = RETURN; s.hammerAge = 0; s.hammerHits.clear(); }
            }
            case RETURN -> {
                Vec3 goal = hand(p), to = goal.subtract(s.hammerPos);
                double speed = Math.min(RETURN_SPEED_MAX, .45 + s.hammerAge * .16);
                if (to.length() <= speed + .5 || s.hammerAge > 200) {
                    s.hammer = HAND; s.dirty = true;
                    set(s, CATCH);
                    fx(p, FX_CATCH, goal, Vec3.ZERO, 1);
                    sound(p, SoundEvents.TRIDENT_RETURN, 1f, .8f);
                    sound(p, SoundEvents.ANVIL_PLACE, .3f, 1.9f);
                    s.poweredTicks = 20;
                    return;
                }
                Vec3 next = s.hammerPos.add(to.normalize().scale(speed));
                LivingEntity hit = sweep(p, s, s.hammerPos, next);
                if (hit != null) {
                    hit.invulnerableTime = 0;
                    hit.hurt(p.damageSources().playerAttack(p), THROW_DAMAGE * .5f);
                    Vec3 dir = to.normalize();
                    hit.knockback(.5, -dir.x, -dir.z);
                    fx(p, FX_SWING_HIT, hit.position().add(0, hit.getBbHeight() * .55, 0), dir, .6f);
                }
                s.hammerPos = next;
            }
            default -> {}
        }
    }
    /** First living thing (not Thor, not hit before on this flight) on the segment. */
    private static LivingEntity sweep(ServerPlayer p, State s, Vec3 from, Vec3 to) {
        AABB area = new AABB(from, to).inflate(.6);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, area, t -> t != p && t.isAlive() && !t.isSpectator())) {
            if (s.hammerHits.contains(t.getId())) continue;
            var hit = t.getBoundingBox().inflate(.35).clip(from, to);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceToSqr(from);
            if (d < bestDist) { bestDist = d; best = t; }
        }
        if (best != null) s.hammerHits.add(best.getId());
        return best;
    }

    // ------------------------------------------------------------------ Wakanda strike
    private static void tickWakanda(ServerPlayer p, State s) {
        p.fallDistance = 0;
        if (s.age == 2) sound(p, SoundEvents.BEACON_AMBIENT, .8f, 1.6f);
        if (s.age == WK_LOOK_UP) { s.poweredTicks = 120; fx(p, FX_TAKEOFF, p.position(), new Vec3(0, 1, 0), .6f); }
        if (s.age == WK_RISE) sound(p, SoundEvents.TRIDENT_RIPTIDE_3, 1f, .8f);
        if (s.age == WK_SHOUT) {
            sound(p, SoundEvents.RAVAGER_ROAR, 1.2f, .75f);
            sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, .9f);
            fx(p, FX_SHOUT, p.position().add(0, 1.4, 0), Vec3.ZERO, 1);
            fx(p, FX_SKY_BOLT, p.position().add(0, 1.0, 0), new Vec3(0, 1, 0), 1.2f);
        }
        if (s.age < LANDED) {
            boolean down = s.age > WK_DIVE && (p.onGround() || p.isInWater());
            if (down || s.age >= WK_MAX_AIR) {
                s.age = LANDED; s.dirty = true;
                sound(p, SoundEvents.ANVIL_LAND, .9f, .5f);
            }
            return;
        }
        int since = s.age - LANDED;
        if (since == WK_FREEZE) impact(p, p.position(), WK_RADIUS, WK_DAMAGE, false);
        if (since >= WK_FREEZE + WK_KNEEL) { set(s, IDLE); s.wakandaCooldown = WK_COOLDOWN; s.safeFall = false; }
    }

    /** The hammer meets the ground: cracks run out from it, lightning comes up through them. */
    static void impact(ServerPlayer p, Vec3 at, double radius, float damage, boolean ultimate) {
        ServerLevel level = p.serverLevel();
        fx(p, ultimate ? FX_ULT_IMPACT : FX_CRACKS, at, Vec3.ZERO, (float) radius);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, .55f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.6f, .7f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.4f, .6f);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius, 4, radius),
                t -> t != p && t.isAlive() && !t.isSpectator())) {
            Vec3 away = t.position().subtract(at);
            double d = Math.sqrt(away.x * away.x + away.z * away.z);
            if (d > radius) continue;
            float falloff = (float) (1 - .6 * d / radius);
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), damage * falloff);
            Vec3 push = d < .1 ? new Vec3(0, 0, 0) : new Vec3(away.x / d, 0, away.z / d);
            t.setDeltaMovement(push.scale(1.1 * falloff).add(0, .55 + .35 * falloff, 0));
            t.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------ the guard
    @SubscribeEvent public static void attacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer thor) || !isThor(thor)) return;
        State s = STATES.get(thor.getUUID());
        if (s == null || (s.action != GUARD && s.action != COUNTER)) return;
        Entity direct = e.getSource().getDirectEntity(), source = e.getSource().getEntity();
        Entity from = direct != null ? direct : source;
        if (from == null) return;
        Vec3 look = Vec3.directionFromRotation(0, thor.getYRot());
        Vec3 to = from.position().subtract(thor.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.lengthSqr() > .01 && flat.normalize().dot(look) < .1) return;   // from behind: the guard does not cover it
        e.setCanceled(true);
        Vec3 shield = thor.position().add(0, 1.2, 0).add(look.scale(.8));
        if (direct instanceof Projectile projectile) {
            fx(thor, FX_CLANG, shield, look, .8f);
            sound(thor, SoundEvents.SHIELD_BLOCK, 1f, 1.4f);
            sound(thor, SoundEvents.ANVIL_PLACE, .4f, 2f);
            projectile.discard();
            return;
        }
        if (s.action == COUNTER) return;
        if (source instanceof LivingEntity attacker) {
            if (s.age <= GUARD_PERFECT) {
                // Perfect timing: the hammer turns against the blow and answers it with lightning.
                set(s, COUNTER);
                s.poweredTicks = 30;
                fx(thor, FX_COUNTER, attacker.position().add(0, attacker.getBbHeight() * .6, 0), look, 1);
                sound(thor, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1.2f);
                sound(thor, SoundEvents.ANVIL_LAND, .6f, 1.5f);
                attacker.invulnerableTime = 0;
                attacker.hurt(thor.damageSources().playerAttack(thor), COUNTER_DAMAGE);
                attacker.knockback(1.3, -look.x, -look.z);
                attacker.hurtMarked = true;
            } else {
                fx(thor, FX_CLANG, shield, look, 1);
                sound(thor, SoundEvents.ANVIL_PLACE, .6f, 1.6f);
                sound(thor, SoundEvents.SHIELD_BLOCK, .8f, 1.1f);
                attacker.knockback(.6, -look.x, -look.z);
                attacker.hurtMarked = true;
            }
        }
    }

    /** Thor's damage comes from the combo; the plain vanilla punch would double it. */
    @SubscribeEvent public static void plainAttack(AttackEntityEvent e) {
        if (isThor(e.getEntity())) e.setCanceled(true);
    }
    @SubscribeEvent public static void fall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isThor(p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && (s.flying || s.safeFall || s.action == WAKANDA || s.action == ULTIMATE)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ sync
    private static void send(ServerPlayer p, State s) {
        int flags = (s.flying ? FLAG_FLYING : 0) | (s.hammer != HAND ? FLAG_HAMMER_OUT : 0) | (s.poweredTicks > 0 ? FLAG_POWERED : 0);
        boolean quiet = s.action == IDLE && flags == 0;
        if (quiet && !s.dirty) return;
        s.dirty = false;
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new ThorStatePacket(p.getId(), s.action, s.age, flags, s.hammerPos, s.carried));
    }
    static void fx(ServerPlayer p, int kind, Vec3 pos, Vec3 dir, float power) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new ThorFxPacket(kind, pos, dir, power, p.getRandom().nextInt()));
    }
    private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { STATES.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { STATES.clear(); }
}
