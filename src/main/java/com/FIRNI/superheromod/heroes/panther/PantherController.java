package com.FIRNI.superheromod.heroes.panther;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.PantherFxPacket;
import com.FIRNI.superheromod.network.packet.PantherStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;
import static com.FIRNI.superheromod.heroes.panther.PantherConfig.f;

/**
 * Black Panther on the server: decides everything (hits, damage, the paths of his pounce, flip and spin,
 * who is thrown and how they slide, the stored energy, the reflex dodges). His own client steers his
 * body along the same paths (PantherPath) and every client animates from the synced state.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class PantherController {
    private static final class State {
        int action = IDLE, age, flags;
        final int[] cooldowns = new int[COOLDOWNS];
        /** The claw combo: which strike comes next, a click waiting, when the last strike ended, the button held. */
        int combo; boolean queued, held; long lastStrike, heldSince;
        /** SHIFT: still down (a tap pounces, a hold crouches); the camouflage's ticks left; the second jump used. */
        boolean shiftHeld, jumped; int camoLeft; long loadAt = -100;
        /** Who his strikes have marked, and until when (level time). */
        final Map<Integer, Long> marks = new HashMap<>();
        /** The stored kinetic energy, and how much went into the release on its way out. */
        float energy, released;
        int reflexLeft, dodges; long lastDodge = -100;
        /** The last hit he took: ticks since, how hard (0 small, 1 medium, 2 large), from where (degrees, relative to his facing). */
        int hurtAge = 100, hurtPower; float hurtYaw;
        /** Where the last attacker was, relative to his facing (degrees): his body keeps turning back to them. */
        float threatYaw;
        long lastCombat = -1000;
        /** The move in progress: its target, and the points of its path (see PantherPath). */
        LivingEntity target;
        Vec3 from = Vec3.ZERO, dir = new Vec3(0, 0, 1), mid = Vec3.ZERO, to = Vec3.ZERO, land = Vec3.ZERO, apex = Vec3.ZERO;
        double reach;
        final Set<Integer> spinHits = new HashSet<>();
    }
    /** Someone he threw: flying, then (once they come down) sliding along the ground. */
    private static final class Thrown {
        final LivingEntity body; final ServerPlayer by; int age, slideLeft; boolean sliding, airborne; Vec3 flight = Vec3.ZERO, slide = Vec3.ZERO; final int scrape;
        Thrown(LivingEntity body, ServerPlayer by, int scrape) { this.body = body; this.by = by; this.scrape = scrape; }
    }
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<Integer, Thrown> THROWN = new HashMap<>();
    private static final UUID SPRINT = UUID.fromString("3c9e5b14-7d2a-4f61-a8b0-5e1f2c9d7a44");

    private PantherController() {}

    public static boolean isHero(Entity e) { return e instanceof ServerPlayer && ID.equals(AbilityManager.getCharacterId(e.getUUID())); }
    private static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), id -> new State()); }
    private static void set(State s, int action) {
        // Out of the frenzy by any road (a dash, a pounce): its cooldown still applies.
        if (s.action == FRENZY && action != FRENZY) s.cooldowns[CD_FRENZY] = Math.max(s.cooldowns[CD_FRENZY], PantherConfig.FRENZY_COOLDOWN.get());
        s.action = action; s.age = 0;
    }
    private static void tell(ServerPlayer p, String text) { p.displayClientMessage(Component.literal("§d" + text), true); }
    private static boolean ready(ServerPlayer p, State s, int slot, String name) {
        if (s.cooldowns[slot] <= 0) return true;
        tell(p, name + ": " + String.format(Locale.ROOT, "%.1f", s.cooldowns[slot] / 20f) + " sn");
        return false;
    }
    /** In the middle of a move that nothing else may start over. */
    private static boolean busy(State s) {
        return critical(s.action) || s.action == POUNCE_LOAD || s.action == POUNCE_LAND || s.action == POUNCE_MISS || s.action == SPIN_LAND
                || s.action == RELEASE_RECOVER || s.action == CROSS || s.action == SNEAK;
    }
    private static void sprint(ServerPlayer p, boolean on) {
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier has = speed.getModifier(SPRINT);
        double want = PantherConfig.SPRINT_BONUS.get();
        if (on && (has == null || has.getAmount() != want)) {
            if (has != null) speed.removeModifier(SPRINT);
            speed.addTransientModifier(new AttributeModifier(SPRINT, "Panther sprint", want, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!on && has != null) speed.removeModifier(SPRINT);
    }

    // ------------------------------------------------------------------ keys
    public static void press(ServerPlayer p, AbilitySlot slot, boolean down) {
        if (!isHero(p)) return;
        State s = state(p);
        if (slot == AbilitySlot.LMB) {
            if (down && !s.held) s.heldSince = p.level().getGameTime();
            s.held = down;
            if (down) claw(p, s);
            return;
        }
        if (slot == AbilitySlot.SHIFT) { if (down) shiftDown(p, s); else shiftUp(p, s); return; }
        if (!down) return;
        switch (slot) {
            case RMB -> dash(p, s);
            case ULTIMATE -> spin(p, s);
            case SKILL_V -> release(p, s);
            case SKILL_E -> reflex(p, s);
            case SKILL_X -> tell(p, "Black Panther'ın ultisi yakında");
            default -> {}
        }
    }

    /** What his own client reports: the second jump in the air. */
    public static void input(ServerPlayer p, int kind) {
        if (!isHero(p)) return;
        State s = state(p);
        if (kind == INPUT_POUNCE) { tapped(p, s); return; }
        if (kind != INPUT_DOUBLE_JUMP) return;
        if (s.jumped || p.onGround() || p.onClimbable() || p.isFallFlying() || busy(s) && s.action != POUNCE_MISS) return;
        s.jumped = true;
        p.fallDistance = 0;
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new PantherFxPacket(FX_DOUBLE_JUMP, p.position(), Vec3.ZERO, 1, p.getId()));
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .35f, 1.5f);
        sound(p, SoundEvents.WOOL_STEP, .7f, .7f);
    }

    // ------------------------------------------------------------------ left click: the claws and the frenzy
    private static int chainAt(int action) { return action == CLAW_DOUBLE ? DOUBLE_CHAIN : action == CLAW_UPPER ? UPPER_CHAIN : CLAW_CHAIN; }
    private static int hitAt(int action) { return action == CLAW_DOUBLE ? DOUBLE_HIT : action == CLAW_UPPER ? UPPER_HIT : CLAW_HIT; }
    private static void claw(ServerPlayer p, State s) {
        if (busy(s) || s.action == FRENZY) return;
        // Mid-strike: remember the click; the next strike flows out of this one's follow-through.
        if (combo(s.action) && s.age < chainAt(s.action)) { s.queued = true; return; }
        nextStrike(p, s);
    }
    /** The button held: the berserk frenzy, straight out of whatever strike he is in. */
    private static void frenzy(ServerPlayer p, State s) {
        set(s, FRENZY);
        s.flags = 0;
        s.queued = false;
        s.target = null;
        s.lastCombat = p.level().getGameTime();
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .6f, 1.9f);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .5f, 1.4f);
    }
    private static void nextStrike(ServerPlayer p, State s) {
        s.queued = false;
        long now = p.level().getGameTime();
        if (!combo(s.action) && now - s.lastStrike > COMBO_RESET) s.combo = 0;
        int step = s.combo;
        s.combo = (step + 1) % 4;
        set(s, step == 0 ? CLAW_RIGHT : step == 1 ? CLAW_LEFT : step == 2 ? CLAW_DOUBLE : CLAW_UPPER);
        s.lastCombat = now;
        // The air parting: a higher, quicker note for the single claws, deeper for the double and the uppercut.
        float[] pitch = {1.9f, 1.7f, 1.35f, 1.1f};
        hand(p, step == 1 ? 1 : step == 0 ? -1 : 0, SoundEvents.PLAYER_ATTACK_SWEEP, step >= 2 ? .6f : .4f, pitch[step] + (p.getRandom().nextFloat() - .5f) * .1f);
    }
    /**
     * Everyone in the area in front of him a strike sweeps: within reach on the flat, inside the arc either side
     * of where he faces (not where the crosshair is), from his knees to over his head.
     */
    private static List<LivingEntity> area(ServerPlayer p, double reach, double arcDegrees) {
        return area(p, reach, arcDegrees, Vec3.directionFromRotation(0, p.getYRot()));
    }
    private static List<LivingEntity> area(ServerPlayer p, double reach, double arcDegrees, Vec3 facing) {
        double cos = Math.cos(Math.toRadians(arcDegrees));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : targets(p, p.getBoundingBox().inflate(reach + 1, 1.5, reach + 1))) {
            Vec3 to = t.position().subtract(p.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            double d = flat.length();
            if (d > reach + t.getBbWidth() * .5) continue;
            if (d > .4 && flat.normalize().dot(facing) < cos) continue;
            if (t.getBoundingBox().maxY < p.getY() - .6 || t.getBoundingBox().minY > p.getY() + 2.9) continue;
            out.add(t);
        }
        return out;
    }
    private static void clawHit(ServerPlayer p, State s) {
        int a = s.action;
        double arc = PantherConfig.CLAW_ARC.get() * (a == CLAW_DOUBLE ? 1.2 : a == CLAW_UPPER ? .85 : 1);
        List<LivingEntity> hit = area(p, PantherConfig.CLAW_REACH.get(), arc);
        if (hit.isEmpty()) return;
        float damage = a == CLAW_DOUBLE ? f(PantherConfig.DOUBLE_DAMAGE) : a == CLAW_UPPER ? f(PantherConfig.UPPER_DAMAGE) : f(PantherConfig.CLAW_DAMAGE);
        Vec3 look = p.getLookAngle(), flat = Vec3.directionFromRotation(0, p.getYRot());
        float knock = f(PantherConfig.CLAW_KNOCK);
        for (LivingEntity t : hit) {
            hurt(p, t, damage);
            Vec3 at = t.getBoundingBox().getCenter();
            if (a == CLAW_UPPER) {
                // Lifted off their feet by the upward claws.
                t.setDeltaMovement(flat.x * knock * .6, f(PantherConfig.UPPER_LIFT), flat.z * knock * .6);
                t.hurtMarked = true;
                fx(p, FX_UPPER, at, flat, 1, t.getId());
            } else {
                // Pushed a little the way the claws went (across for the singles, straight back for the double).
                Vec3 across = new Vec3(-flat.z, 0, flat.x).scale(a == CLAW_RIGHT ? -.35 : a == CLAW_LEFT ? .35 : 0);
                Vec3 push = flat.add(across).normalize().scale(knock * (a == CLAW_DOUBLE ? 1.6 : 1));
                t.push(push.x, .05, push.z);
                t.hurtMarked = true;
                fx(p, FX_CLAW_HIT, at, look, a == CLAW_DOUBLE ? 2 : a == CLAW_RIGHT ? 1 : -1, t.getId());
            }
            // The claws themselves: a bright metallic bite.
            p.level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .45f, a == CLAW_DOUBLE ? 1.5f : 1.85f);
        }
        if (a == CLAW_UPPER) { sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .9f, 1.15f); sound(p, SoundEvents.PLAYER_ATTACK_CRIT, .8f, 1.3f); }
        else sound(p, SoundEvents.PLAYER_ATTACK_STRONG, .7f, a == CLAW_DOUBLE ? 1.1f : 1.4f);
        s.lastCombat = p.level().getGameTime();
    }
    /** The berserk frenzy: a wild slash every FRENZY_STRIKE ticks, hands alternating, everything in front of him cut. */
    private static void frenzyTick(ServerPlayer p, State s) {
        int strike = s.age / FRENZY_STRIKE, phase = s.age % FRENZY_STRIKE;
        s.flags = strike % 2;
        // It ends when he lets go, or when he runs out of breath.
        if (!s.held || s.age >= FRENZY_MAX) {
            s.cooldowns[CD_FRENZY] = PantherConfig.FRENZY_COOLDOWN.get();
            s.combo = 0; s.lastStrike = p.level().getGameTime();
            set(s, IDLE);
            return;
        }
        if (phase == 0) hand(p, s.flags == 0 ? -1 : 1, SoundEvents.PLAYER_ATTACK_SWEEP, .35f, 1.6f + .3f * Math.min(1, s.age / 30f) + p.getRandom().nextFloat() * .15f);
        if (phase == FRENZY_HIT) {
            for (LivingEntity t : area(p, PantherConfig.FRENZY_RANGE.get(), PantherConfig.CLAW_ARC.get() * 1.1)) {
                hurt(p, t, f(PantherConfig.FRENZY_DAMAGE));
                t.setDeltaMovement(t.getDeltaMovement().multiply(.3, 1, .3));
                t.hurtMarked = true;
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 2, false, false));
                Vec3 at = t.getBoundingBox().getCenter();
                fx(p, FX_CLAW_HIT, at, p.getLookAngle(), s.flags == 0 ? 1.5f : -1.5f, t.getId());
                p.level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .3f, 1.7f + p.getRandom().nextFloat() * .3f);
                s.target = t;
            }
            s.lastCombat = p.level().getGameTime();
        }
    }

    // ------------------------------------------------------------------ right click: the marked dash
    private static void dash(ServerPlayer p, State s) {
        if (busy(s) && s.action != SNEAK) return;
        long now = p.level().getGameTime();
        // The marked target he is looking most toward (in reach, in sight).
        LivingEntity best = null;
        double bestScore = -9, range = PantherConfig.DASH_RANGE.get();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        for (var mark : s.marks.entrySet()) {
            if (mark.getValue() <= now || !(p.level().getEntity(mark.getKey()) instanceof LivingEntity t) || !t.isAlive()) continue;
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range) continue;
            if (p.level().clip(new ClipContext(eye, t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            double score = to.normalize().dot(look) * 2 - d / range;
            if (score > bestScore) { bestScore = score; best = t; }
        }
        if (best == null) { tell(p, "Pençe Atılışı: işaretli hedef yok (önce vurarak işaretle)"); return; }
        if (!ready(p, s, CD_DASH, "Pençe Atılışı")) return;
        s.cooldowns[CD_DASH] = PantherConfig.DASH_COOLDOWN.get();
        s.target = best;
        s.from = p.position();
        Vec3 d = best.position().subtract(s.from);
        Vec3 flat = new Vec3(d.x, 0, d.z);
        s.dir = flat.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
        Vec3 stop = best.position().subtract(s.dir.scale(best.getBbWidth() * .5 + .75));
        s.to = ground(p.serverLevel(), new Vec3(stop.x, Math.max(stop.y, s.from.y) + .5, stop.z));
        s.reach = s.from.distanceTo(s.to);
        set(s, DASH);
        s.lastCombat = now;
        fx(p, FX_DASH, s.from, s.dir, 1, p.getId());
        sound(p, SoundEvents.TRIDENT_RIPTIDE_1, .7f, 1.8f);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .6f, 1.2f);
    }
    /** He arrives: the crossed claws thrown open outward through the target (and anyone right beside them). */
    private static void crossHit(ServerPlayer p, State s) {
        LivingEntity main = s.target;
        boolean any = false;
        // Facing the way he dashed (his body turned along it, whatever the view did), and always the one he went for.
        List<LivingEntity> hit = area(p, PantherConfig.CLAW_REACH.get() + .4, 95, s.dir);
        if (main != null && main.isAlive() && !hit.contains(main) && main.position().distanceTo(p.position()) < PantherConfig.CLAW_REACH.get() + 1.5) hit.add(main);
        for (LivingEntity t : hit) {
            boolean marked = t == main;
            hurt(p, t, marked ? f(PantherConfig.DASH_DAMAGE) : f(PantherConfig.DASH_DAMAGE) * .5f);
            Vec3 out = new Vec3(t.getX() - p.getX(), 0, t.getZ() - p.getZ());
            out = out.lengthSqr() < 1e-4 ? s.dir : out.normalize();
            t.setDeltaMovement(out.x * .55, .22, out.z * .55);
            t.hurtMarked = true;
            fx(p, FX_CLAW_HIT, t.getBoundingBox().getCenter(), out, 2, t.getId());
            any = true;
        }
        // The dash spends the mark it went for.
        if (main != null) s.marks.remove(main.getId());
        fx(p, FX_CROSS, p.position().add(0, 1.1, 0), s.dir, any ? 1 : .6f, p.getId());
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .9f);
        if (any) { sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1.1f); sound(p, SoundEvents.TRIDENT_HIT, .8f, 1.3f); }
    }

    // ------------------------------------------------------------------ marks
    /** His strike leaves the purple claw scratch on them, renewed with every hit. */
    private static void mark(ServerPlayer p, LivingEntity t) {
        State s = STATES.get(p.getUUID());
        if (s == null || !t.isAlive()) return;
        s.marks.put(t.getId(), p.level().getGameTime() + MARK_TICKS);
        fx(p, FX_MARK, t.position(), Vec3.ZERO, MARK_TICKS, t.getId());
    }

    // ------------------------------------------------------------------ SHIFT: Panther Pounce
    /** SHIFT down: the load (a blink of compression that is also the start of a crouch). */
    private static void shiftDown(ServerPlayer p, State s) {
        s.shiftHeld = true;
        if (busy(s)) return;
        if (s.cooldowns[CD_POUNCE] > 0) { set(s, SNEAK); return; }
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        s.dir = flat.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
        s.reach = 0;
        s.target = null;
        set(s, POUNCE_LOAD);
        s.loadAt = p.level().getGameTime();
        s.lastCombat = s.loadAt;
    }
    /**
     * SHIFT up: out of a crouch he simply stands. Whether it was a tap (a pounce) his own client decides, by its
     * own clock, and says so (INPUT_POUNCE), so both sides always agree, whatever the ping.
     */
    private static void shiftUp(ServerPlayer p, State s) {
        s.shiftHeld = false;
        if (s.action == SNEAK || s.action == POUNCE_LOAD) {
            if (s.action == SNEAK && s.age < TAP_TICKS && s.cooldowns[CD_POUNCE] > 0) ready(p, s, CD_POUNCE, "Panter Atılışı");
            set(s, IDLE);
        }
    }
    /** His client says the SHIFT was a tap: the pounce, from where he stands, the way he looked when he pressed it. */
    private static void tapped(ServerPlayer p, State s) {
        long now = p.level().getGameTime();
        boolean loading = s.action == POUNCE_LOAD || s.action == SNEAK && s.age < TAP_TICKS + 4;
        boolean justLeft = (s.action == IDLE || combo(s.action) || s.action == FRENZY) && now - s.loadAt <= TAP_TICKS + 8;
        if (!(loading || justLeft) || s.cooldowns[CD_POUNCE] > 0) return;
        s.cooldowns[CD_POUNCE] = PantherConfig.POUNCE_COOLDOWN.get();
        s.target = null;
        pounceStart(p, s);
        fx(p, FX_POUNCE, p.position(), s.dir, 1, p.getId());
        sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, .5f, 1.8f);
        sound(p, SoundEvents.TRIDENT_RIPTIDE_1, .5f, 1.7f);
    }
    /** The camouflage: the suit bends the light round him; any hit breaks it. */
    private static void camo(ServerPlayer p, State s, boolean on, boolean broken) {
        if (on) {
            s.camoLeft = PantherConfig.CAMO_DURATION.get();
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, s.camoLeft + 2, 0, false, false, false));
            fx(p, FX_CAMO, p.position(), Vec3.ZERO, 1, p.getId());
            sound(p, SoundEvents.ILLUSIONER_PREPARE_MIRROR, .6f, 1.6f);
            sound(p, SoundEvents.BEACON_POWER_SELECT, .3f, 1.9f);
            return;
        }
        if (s.camoLeft <= 0) return;
        s.camoLeft = 0;
        s.cooldowns[CD_CAMO] = PantherConfig.CAMO_COOLDOWN.get();
        var effect = p.getEffect(MobEffects.INVISIBILITY);
        if (effect != null && effect.getDuration() <= PantherConfig.CAMO_DURATION.get() + 2) p.removeEffect(MobEffects.INVISIBILITY);
        fx(p, FX_CAMO, p.position(), Vec3.ZERO, broken ? -1 : 0, p.getId());
        if (broken) { sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, .8f, 1.4f); sound(p, SoundEvents.AMETHYST_BLOCK_BREAK, .7f, 1.6f); }
        else sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, .5f, .9f);
    }
    /** The leap leaves from where he really is once the load is over (he may have still been running when he pressed). */
    private static void pounceStart(ServerPlayer p, State s) {
        s.from = p.position();
        // As far as he can go before a wall: checked at the knees and the head.
        double max = PantherConfig.POUNCE_DISTANCE.get();
        s.reach = max;
        for (double h : new double[]{.4, 1.5}) {
            Vec3 a = s.from.add(0, h, 0), b = a.add(s.dir.scale(max + .4));
            var hit = p.level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) s.reach = Math.min(s.reach, Math.max(0, hit.getLocation().distanceTo(a) - .55));
        }
        set(s, POUNCE);
    }
    private static void pounceTick(ServerPlayer p, State s) {
        double speed = PantherConfig.POUNCE_SPEED.get();
        // A little ahead of the server's own clock: his client is already there (it launched on the key, not on this packet).
        Vec3 before = PantherPath.pounce(s.from, s.dir, speed, s.reach, s.age - 1), now = PantherPath.pounce(s.from, s.dir, speed, s.reach, s.age + 1.2);
        // Anyone the leap passes through: the first along the way is hit.
        AABB sweep = p.getBoundingBox().move(before.subtract(p.position())).minmax(p.getBoundingBox().move(now.subtract(p.position()))).inflate(.35);
        LivingEntity first = null;
        double firstAlong = Double.MAX_VALUE;
        for (LivingEntity t : targets(p, sweep)) {
            double along = t.position().subtract(s.from).dot(s.dir);
            if (along < firstAlong) { firstAlong = along; first = t; }
        }
        if (first != null) { contact(p, s, first); return; }
        if (speed * s.age >= s.reach) {
            set(s, POUNCE_MISS);
            sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .5f, .7f);
        }
    }
    /** The pounce lands on someone: the hit, then the flip over them is planned. */
    private static void contact(ServerPlayer p, State s, LivingEntity t) {
        s.target = t;
        Vec3 feet = t.position();
        Vec3 d = new Vec3(feet.x - s.from.x, 0, feet.z - s.from.z);
        if (d.lengthSqr() > 1e-4) s.dir = d.normalize();
        double half = t.getBbWidth() * .5;
        Vec3 contact = feet.subtract(s.dir.scale(half + .55));
        contact = new Vec3(contact.x, Math.max(p.getY(), feet.y), contact.z);
        // Where the flip ends: in the air behind them; if that is inside a wall, beside them; failing that, over them.
        Vec3 to = null;
        Vec3 side = new Vec3(-s.dir.z, 0, s.dir.x);
        for (Vec3 off : new Vec3[]{s.dir.scale(half + 1.3), s.dir.scale(half + 1.3).add(side.scale(.9)), s.dir.scale(half + 1.3).subtract(side.scale(.9)), s.dir.scale(half + .7)}) {
            Vec3 at = feet.add(off).add(0, .9, 0);
            if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) { to = at; break; }
        }
        if (to == null) to = feet.add(0, t.getBbHeight() + .3, 0);
        s.mid = feet; s.to = to;
        s.apex = PantherPath.apex(contact, feet, t.getBbHeight(), PantherConfig.FLIP_HEIGHT.get(), to);
        s.from = contact;
        s.land = ground(p.serverLevel(), to.add(s.dir.scale(.6)));   // the landing ends right here (PantherPath.land)
        hurt(p, t, f(PantherConfig.CONTACT_DAMAGE));
        hold(t);
        Vec3 at = t.getBoundingBox().getCenter();
        fx(p, FX_CONTACT, at, s.dir, 1, t.getId());
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1f, .8f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, .5f, .6f);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .8f, .6f);
        set(s, POUNCE_FLIP);
    }
    /** The target held where they are while he goes over them. */
    private static void hold(LivingEntity t) {
        Vec3 v = t.getDeltaMovement();
        t.setDeltaMovement(0, Math.min(0, v.y), 0);
        t.hurtMarked = true;
        t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 9, false, false));
    }
    private static void kickHit(ServerPlayer p, State s) {
        LivingEntity t = s.target;
        if (t == null || !t.isAlive() || t.position().distanceTo(s.to) > 4.5) return;
        hurt(p, t, f(PantherConfig.KICK_DAMAGE));
        // Out the way the kick went: from where he hangs through them, and up.
        Vec3 into = t.getBoundingBox().getCenter().subtract(PantherPath.kick(s.to, s.dir, KICK_HIT, KICK_HIT, KICK_TICKS));
        Vec3 flat = new Vec3(into.x, 0, into.z);
        flat = flat.lengthSqr() < 1e-4 ? s.dir.scale(-1) : flat.normalize();
        double lift = PantherConfig.KICK_LIFT.get() + Math.max(0, into.normalize().y) * .3;
        throwBody(p, t, flat.scale(PantherConfig.KICK_SPEED.get()).add(0, lift, 0), PantherConfig.SCRAPE_TICKS.get());
        Vec3 at = t.getBoundingBox().getCenter();
        fx(p, FX_KICK, at, flat, 1, t.getId());
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, .7f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, .35f, 1.7f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_RIPTIDE_2, SoundSource.PLAYERS, .6f, 1.3f);
    }

    // ------------------------------------------------------------------ Q: the spinning triple kick
    private static void spin(ServerPlayer p, State s) {
        if (busy(s) || !ready(p, s, CD_SPIN, "Dönen Tekme")) return;
        s.cooldowns[CD_SPIN] = PantherConfig.SPIN_COOLDOWN.get();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        s.dir = flat.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
        s.spinHits.clear();
        set(s, SPIN_LOAD);
        s.lastCombat = p.level().getGameTime();
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .4f, .8f);
    }
    private static void spinStart(ServerPlayer p, State s) {
        s.from = p.position();
        // Not further than the first wall.
        double max = PantherConfig.SPIN_DISTANCE.get();
        s.reach = max;
        Vec3 a = s.from.add(0, 1, 0);
        var hit = p.level().clip(new ClipContext(a, a.add(s.dir.scale(max + .5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.MISS) s.reach = Math.max(0, hit.getLocation().distanceTo(a) - .6);
        set(s, SPIN);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .7f, .7f);
    }
    private static void spinKick(ServerPlayer p, State s, int index) {
        boolean last = index == SPIN_KICKS.length - 1;
        Vec3 at = PantherPath.spin(s.from, s.dir, s.reach, PantherConfig.SPIN_HEIGHT.get(), s.age, SPIN_TICKS).add(0, .9, 0);
        double reach = PantherConfig.SPIN_REACH.get();
        boolean any = false;
        for (LivingEntity t : targets(p, new AABB(at, at).inflate(reach, 2.2, reach))) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(at);
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.length() > reach + t.getBbWidth() * .5 || flat.lengthSqr() > .25 && flat.normalize().dot(s.dir) < -.1) continue;
            any = true;
            hurt(p, t, last ? f(PantherConfig.SPIN_FINAL_DAMAGE) : f(PantherConfig.SPIN_DAMAGE));
            Vec3 out = flat.lengthSqr() < 1e-4 ? s.dir : flat.normalize().add(s.dir).normalize();
            if (last) throwBody(p, t, out.scale(PantherConfig.SPIN_KNOCK.get()).add(0, .38, 0), Math.max(4, PantherConfig.SCRAPE_TICKS.get() / 2));
            else { t.setDeltaMovement(out.x * .18, Math.max(t.getDeltaMovement().y, .12), out.z * .18); t.hurtMarked = true; }
            fx(p, FX_SPIN_KICK, t.getBoundingBox().getCenter(), out, last ? 2 : 1, t.getId());
        }
        // The air the leg throws aside, a different note each kick; the third is the heaviest.
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, last ? .9f : .6f, last ? .75f : index == 0 ? 1.15f : 1f);
        if (any) {
            sound(p, last ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG, 1f, last ? .75f : 1.05f);
            if (last) sound(p, SoundEvents.GENERIC_EXPLODE, .3f, 1.6f);
        }
        fx(p, FX_SPIN_KICK, at, s.dir, last ? -2 : -1, -1);
    }

    // ------------------------------------------------------------------ E: the kinetic release
    private static void release(ServerPlayer p, State s) {
        if (busy(s)) return;
        if (s.energy < PantherConfig.RELEASE_MIN.get()) { tell(p, "Vibranyum Patlaması: takımda depolanmış enerji yok — hasar aldıkça dolar"); return; }
        if (!ready(p, s, CD_RELEASE, "Vibranyum Patlaması")) return;
        s.cooldowns[CD_RELEASE] = PantherConfig.RELEASE_COOLDOWN.get();
        s.released = s.energy;
        set(s, RELEASE_CHARGE);
        s.lastCombat = p.level().getGameTime();
        sound(p, SoundEvents.BEACON_POWER_SELECT, .8f, .6f);
        sound(p, SoundEvents.CONDUIT_AMBIENT_SHORT, 1f, 1.3f);
    }
    private static void burst(ServerPlayer p, State s) {
        float k = power(s);
        double radius = Mth.lerp(k, PantherConfig.RELEASE_RADIUS_MIN.get(), PantherConfig.RELEASE_RADIUS_MAX.get());
        float damage = (float) Mth.lerp(k, PantherConfig.RELEASE_DAMAGE_MIN.get(), PantherConfig.RELEASE_DAMAGE_MAX.get());
        double knock = Mth.lerp(k, PantherConfig.RELEASE_KNOCK_MIN.get(), PantherConfig.RELEASE_KNOCK_MAX.get());
        double lift = Mth.lerp(k, PantherConfig.RELEASE_LIFT_MIN.get(), PantherConfig.RELEASE_LIFT_MAX.get());
        Vec3 centre = p.position().add(0, 1.1, 0);
        for (LivingEntity t : targets(p, new AABB(centre, centre).inflate(radius))) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(centre);
            double d = to.length();
            if (d > radius + t.getBbWidth() * .5) continue;
            // Straight out from him, each by their own position: in front flies forward, behind flies back, above goes up too.
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() < 1e-4) flat = Vec3.directionFromRotation(0, p.getRandom().nextFloat() * 360);
            flat = flat.normalize();
            double fall = 1 - .45 * Math.min(1, d / radius);
            double up = lift + Math.max(0, to.normalize().y) * knock * .5;
            hurt(p, t, (float) (damage * (.6 + .4 * fall)));
            throwBody(p, t, flat.scale(knock * fall).add(0, up * (.7 + .3 * fall), 0), Math.max(3, (int) (PantherConfig.SCRAPE_TICKS.get() * .6 * fall)));
            fx(p, FX_LAUNCH, t.getBoundingBox().getCenter(), flat, k, t.getId());
        }
        // The sphere's size travels in dir.x (the clients draw it to the server's radius).
        fx(p, FX_RELEASE, p.position(), new Vec3(radius, 0, 0), Math.max(.05f, k), p.getId());
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, .5f + .5f * k, 1.35f - .25f * k);
        sound(p, SoundEvents.GENERIC_EXPLODE, .6f + .5f * k, .75f);
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, .6f);
        sound(p, SoundEvents.BEACON_DEACTIVATE, .7f, 1.4f);
        s.energy = 0;
    }
    /** How much of a full charge went into the release (0..1). */
    private static float power(State s) { return Mth.clamp(s.released / f(PantherConfig.ENERGY_MAX), 0, 1); }

    // ------------------------------------------------------------------ R: Panther Reflex
    private static void reflex(ServerPlayer p, State s) {
        if (s.reflexLeft > 0 || !ready(p, s, CD_REFLEX, "Panter Refleksi")) return;
        s.cooldowns[CD_REFLEX] = PantherConfig.REFLEX_COOLDOWN.get();
        s.reflexLeft = PantherConfig.REFLEX_DURATION.get();
        s.dodges = 0;
        fx(p, FX_REFLEX, p.position(), Vec3.ZERO, 1, p.getId());
        sound(p, SoundEvents.WARDEN_HEARTBEAT, .8f, 1.6f);
        sound(p, SoundEvents.BEACON_ACTIVATE, .35f, 1.9f);
    }
    /** An attack is coming: he slips it, away from where it comes from. */
    private static boolean dodge(ServerPlayer p, State s, DamageSource source) {
        Entity direct = source.getDirectEntity(), attacker = source.getEntity();
        if (direct == null) return false;
        boolean projectile = direct instanceof Projectile;
        if (projectile && !PantherConfig.DODGE_PROJECTILES.get()) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        long now = p.level().getGameTime();
        if (now - s.lastDodge < DODGE_GAP || s.dodges >= PantherConfig.REFLEX_MAX_DODGES.get()) return false;
        Vec3 from = direct.position().add(0, direct.getBbHeight() * .5, 0);
        Vec3 rel = from.subtract(p.position().add(0, p.getBbHeight() * .5, 0));
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        double fwd = rel.dot(forward), side = rel.dot(right);
        float angle = (float) Math.toDegrees(Math.atan2(side, fwd));   // + = from his right
        boolean high = projectile ? direct.getDeltaMovement().y < -.35 || rel.y > 1.2 : (attacker != null && attacker.getEyeY() > p.getEyeY() + .5);
        int type;
        if (Math.abs(angle) <= 35) {
            if (high) type = DODGE_CROUCH;
            else if (projectile) type = s.dodges % 2 == 0 ? DODGE_LEFT : DODGE_RIGHT;
            else type = switch (s.dodges % 3) { case 0 -> DODGE_BACK; case 1 -> DODGE_BACK_LEFT; default -> DODGE_BACK_RIGHT; };
        } else if (Math.abs(angle) <= 80) type = angle > 0 ? DODGE_BACK_LEFT : DODGE_BACK_RIGHT;
        else type = angle > 0 ? DODGE_LEFT : DODGE_RIGHT;
        s.lastDodge = now;
        s.dodges++;
        s.threatYaw = angle;
        s.lastCombat = now;
        fx(p, FX_DODGE, p.position(), rel.normalize(), type, p.getId());
        if (!critical(s.action) && s.action != POUNCE_LOAD) {
            double dist = PantherConfig.DODGE_DISTANCE.get() * .46;
            Vec3 step = switch (type) {
                case DODGE_LEFT -> right.scale(-dist);
                case DODGE_RIGHT -> right.scale(dist);
                case DODGE_BACK -> forward.scale(-dist * .9);
                case DODGE_BACK_LEFT -> forward.scale(-1).subtract(right).normalize().scale(dist);
                case DODGE_BACK_RIGHT -> forward.scale(-1).add(right).normalize().scale(dist);
                default -> forward.scale(-dist * .25);
            };
            p.setDeltaMovement(step.x, type == DODGE_CROUCH ? 0 : .12, step.z);
            p.hurtMarked = true;
            set(s, DODGE);
            s.flags = type;
        }
        sound(p, SoundEvents.PLAYER_ATTACK_NODAMAGE, .7f, 1.6f);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .2f, 2f);
        return true;
    }

    // ------------------------------------------------------------------ thrown bodies: flying, then scraping along the ground
    private static void throwBody(ServerPlayer p, LivingEntity t, Vec3 velocity, int scrape) {
        double resist = 1 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * .6;
        Vec3 v = velocity.multiply(resist, 1, resist);
        t.setDeltaMovement(v);
        t.hurtMarked = true;
        t.setOnGround(false);
        Thrown th = new Thrown(t, p, scrape);
        th.flight = v;
        THROWN.put(t.getId(), th);
    }
    private static void tickThrown() {
        if (THROWN.isEmpty()) return;
        for (Iterator<Thrown> it = THROWN.values().iterator(); it.hasNext(); ) {
            Thrown th = it.next();
            LivingEntity t = th.body;
            if (!t.isAlive() || t.isRemoved() || ++th.age > 80) { it.remove(); continue; }
            if (!th.sliding) {
                Vec3 v = t.getDeltaMovement();
                // (A player's "on the ground" comes from their own client and may lag the throw: wait until they have left it.)
                if (!t.onGround()) th.airborne = true;
                if (th.age > 2 && t.onGround() && (th.airborne || th.age > 12)) {
                    // Down: the speed it still carries turns into a slide along the ground.
                    th.sliding = true;
                    th.slideLeft = th.scrape;
                    Vec3 flat = new Vec3(th.flight.x, 0, th.flight.z);
                    double speed = Math.max(.45, Math.min(1.3, flat.length() * .75));
                    th.slide = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize().scale(speed);
                    if (th.slideLeft <= 0 || th.slide.lengthSqr() < 1e-4) { it.remove(); continue; }
                    ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> t), new PantherFxPacket(FX_SCRAPE, t.position(), th.slide, (float) speed, t.getId()));
                    BlockPos under = t.blockPosition().below();
                    var block = t.level().getBlockState(under);
                    var type = block.getSoundType(t.level(), under, t);
                    t.level().playSound(null, t.getX(), t.getY(), t.getZ(), type.getBreakSound(), SoundSource.PLAYERS, .9f, .7f);
                    t.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, .6f, .6f);
                } else th.flight = v;
                continue;
            }
            th.slide = th.slide.scale(PantherConfig.SCRAPE_KEEP.get());
            Vec3 v = t.getDeltaMovement();
            t.setDeltaMovement(th.slide.x, Math.min(v.y, 0), th.slide.z);
            t.hurtMarked = true;
            if (th.age % 2 == 0) {
                // The ground scraping under them, in its own voice (dirt, stone, sand, wood).
                BlockPos under = t.blockPosition().below();
                var type = t.level().getBlockState(under).getSoundType(t.level(), under, t);
                t.level().playSound(null, t.getX(), t.getY(), t.getZ(), type.getHitSound(), SoundSource.PLAYERS, .8f, .55f + (float) th.slide.length() * .4f);
            }
            if (--th.slideLeft <= 0 || th.slide.lengthSqr() < .0025) it.remove();
        }
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!isHero(p)) {
            sprint(p, false);
            State gone = STATES.remove(p.getUUID());
            if (gone != null) { if (gone.camoLeft > 0) p.removeEffect(MobEffects.INVISIBILITY); send(p, new State()); }
            return;
        }
        State s = state(p);
        s.age++;
        s.hurtAge = Math.min(100, s.hurtAge + 1);
        sprint(p, p.isSprinting() && !busy(s));
        for (int i = 0; i < s.cooldowns.length; i++) if (s.cooldowns[i] > 0) s.cooldowns[i]--;
        if (s.reflexLeft > 0) s.reflexLeft--;
        if (p.onGround()) s.jumped = false;
        if (s.camoLeft > 0 && --s.camoLeft <= 0) { s.camoLeft = 1; camo(p, s, false, false); }
        if (!s.marks.isEmpty()) { long now = p.level().getGameTime(); s.marks.values().removeIf(t -> t <= now); }
        // Held long enough: the berserk frenzy, out of any strike or the stance.
        if (s.held && s.action != FRENZY && (s.action == IDLE || combo(s.action)) && p.level().getGameTime() - s.heldSince >= FRENZY_HOLD
                && s.cooldowns[CD_FRENZY] <= 0) frenzy(p, s);
        if (s.action != IDLE && s.action != FRENZY && s.action != SNEAK && s.action != POUNCE_LOAD && s.action != DODGE && !clawing(s.action)) p.fallDistance = 0;
        switch (s.action) {
            case SNEAK -> {
                if (!s.shiftHeld) set(s, IDLE);
                else if (s.age == CAMO_CHARGE && s.camoLeft <= 0) {
                    if (s.cooldowns[CD_CAMO] <= 0) camo(p, s, true, false);
                    else ready(p, s, CD_CAMO, "Kamuflaj");
                }
            }
            case DASH -> {
                p.fallDistance = 0;
                if (s.age >= DASH_TICKS) { set(s, CROSS); }
            }
            case CROSS -> {
                if (s.age == CROSS_HIT) crossHit(p, s);
                if (s.age >= CROSS_TICKS) set(s, IDLE);
            }
            case CLAW_RIGHT, CLAW_LEFT, CLAW_DOUBLE, CLAW_UPPER -> {
                if (s.age == hitAt(s.action)) clawHit(p, s);
                // Holding the button is the same as clicking again and again.
                if (s.held && s.age >= chainAt(s.action)) s.queued = true;
                if (s.queued && s.age >= chainAt(s.action)) nextStrike(p, s);
                else if (s.age >= length(s.action)) { s.lastStrike = p.level().getGameTime(); if (s.action == CLAW_UPPER) s.combo = 0; set(s, IDLE); }
            }
            case FRENZY -> frenzyTick(p, s);
            case POUNCE_LOAD -> {
                // Still down past a tap: it was never a pounce, he is crouching.
                if (s.age >= TAP_TICKS) set(s, s.shiftHeld ? SNEAK : IDLE);
            }
            case POUNCE -> pounceTick(p, s);
            case POUNCE_FLIP -> {
                if (s.target != null && s.target.isAlive()) hold(s.target);
                if (s.age == FLIP_TICKS / 2) sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .5f, .55f);
                if (s.age >= FLIP_TICKS) set(s, POUNCE_KICK);
            }
            case POUNCE_KICK -> {
                if (s.age < KICK_HIT && s.target != null && s.target.isAlive()) hold(s.target);
                if (s.age == KICK_HIT) kickHit(p, s);
                if (s.age >= KICK_TICKS) set(s, POUNCE_LAND);
            }
            case POUNCE_LAND -> {
                if (s.age == (int) (LAND_TICKS * .45f)) { fx(p, FX_LAND, s.land, s.dir, 1, p.getId()); sound(p, SoundEvents.WOOL_STEP, .6f, .8f); }
                if (s.age >= LAND_TICKS) set(s, IDLE);
            }
            case POUNCE_MISS -> {
                if (s.age == 8) fx(p, FX_LAND, p.position(), s.dir, .6f, p.getId());
                if (s.age >= MISS_TICKS) set(s, IDLE);
            }
            case SPIN_LOAD -> { if (s.age >= SPIN_LOAD_TICKS) spinStart(p, s); }
            case SPIN -> {
                for (int i = 0; i < SPIN_KICKS.length; i++) if (s.age == SPIN_KICKS[i]) spinKick(p, s, i);
                if (s.age >= SPIN_TICKS) { set(s, SPIN_LAND); fx(p, FX_LAND, p.position(), s.dir, .8f, p.getId()); }
            }
            case SPIN_LAND -> { if (s.age >= SPIN_LAND_TICKS) set(s, IDLE); }
            case RELEASE_CHARGE -> {
                p.setDeltaMovement(p.getDeltaMovement().multiply(0, 1, 0));
                if (s.age == CHARGE_TICKS / 2) sound(p, SoundEvents.BEACON_ACTIVATE, .7f, 1.2f + power(s) * .4f);
                if (s.age >= CHARGE_TICKS) { burst(p, s); set(s, RELEASE); }
            }
            case RELEASE -> { if (s.age >= RELEASE_TICKS) set(s, RELEASE_RECOVER); }
            case RELEASE_RECOVER -> { if (s.age >= RECOVER_TICKS) set(s, IDLE); }
            case DODGE -> { if (s.age >= DODGE_TICKS) set(s, IDLE); }
            default -> { if (s.held && s.age > 1 && s.action == IDLE) claw(p, s); }
        }
        // While he is at it, the energy he holds slowly leaks away only after a long quiet.
        if (p.level().getGameTime() - s.lastCombat > 600 && s.energy > 0) s.energy = Math.max(0, s.energy - .05f);
        send(p, s);
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) { if (e.phase == TickEvent.Phase.END) tickThrown(); }

    // ------------------------------------------------------------------ being hit
    /** Panther Reflex: a dodgeable attack is slipped instead of taken. */
    @SubscribeEvent public static void attacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null || s.reflexLeft <= 0) return;
        if (dodge(p, s, e.getSource())) e.setCanceled(true);
    }
    /** A hit he takes: part of it goes into the suit as energy; his body reacts to it. Hits he deals may charge it too. */
    @SubscribeEvent public static void hurtEvent(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isHero(p)) {
            State s = state(p);
            float amount = e.getAmount();
            float before = s.energy;
            // A hit tears the camouflage: it glitches and falls away.
            if (s.camoLeft > 0) camo(p, s, false, true);
            s.energy = Math.min(f(PantherConfig.ENERGY_MAX), s.energy + amount * f(PantherConfig.ENERGY_PER_DAMAGE));
            s.hurtAge = 0;
            s.hurtPower = amount < 3 ? 0 : amount < 7 ? 1 : 2;
            Entity from = e.getSource().getDirectEntity() != null ? e.getSource().getDirectEntity() : e.getSource().getEntity();
            Vec3 rel = from == null ? Vec3.directionFromRotation(0, p.getYRot()) : from.position().subtract(p.position());
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            double fwd = rel.x * -Mth.sin(yaw) + rel.z * Mth.cos(yaw), side = rel.x * -Mth.cos(yaw) + rel.z * -Mth.sin(yaw);
            s.hurtYaw = (float) Math.toDegrees(Math.atan2(side, fwd));
            if (from != null) s.threatYaw = s.hurtYaw;
            s.lastCombat = p.level().getGameTime();
            double height = from == null ? .5 : Mth.clamp((from.getY() + from.getBbHeight() * .6 - p.getY()) / p.getBbHeight(), 0, 1);
            if (s.energy > before) fx(p, FX_ABSORB, p.position(), rel.lengthSqr() < 1e-6 ? Vec3.ZERO : rel.normalize(), (float) height, p.getId());
        }
        if (e.getSource().getEntity() instanceof ServerPlayer p && isHero(p) && e.getEntity() != p && PantherConfig.ENERGY_PER_HIT.get() > 0) {
            State s = state(p);
            s.energy = Math.min(f(PantherConfig.ENERGY_MAX), s.energy + e.getAmount() * f(PantherConfig.ENERGY_PER_HIT));
        }
    }
    /** He lands like a cat: falls hurt him far less. */
    @SubscribeEvent public static void fall(LivingFallEvent e) {
        if (!isHero(e.getEntity())) return;
        e.setDistance(Math.max(0, e.getDistance() - 6));
        e.setDamageMultiplier(e.getDamageMultiplier() * .5f);
    }
    /** His claws are his left click; the vanilla punch would hit twice. */
    @SubscribeEvent public static void plainAttack(AttackEntityEvent e) { if (isHero(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        State s = STATES.remove(e.getEntity().getUUID());
        if (s != null && s.camoLeft > 0) e.getEntity().removeEffect(MobEffects.INVISIBILITY);
    }

    // ------------------------------------------------------------------ helpers
    private static List<LivingEntity> targets(ServerPlayer p, AABB area) {
        boolean friendly = PantherConfig.FRIENDLY_FIRE.get();
        return p.level().getEntitiesOfClass(LivingEntity.class, area, t -> t != p && t.isAlive() && !t.isSpectator()
                && (friendly || !(t instanceof Player other && p.isAlliedTo(other))));
    }
    private static void hurt(ServerPlayer p, LivingEntity t, float damage) {
        t.invulnerableTime = 0;
        if (t.hurt(p.damageSources().playerAttack(p), damage)) mark(p, t);
    }
    /** The ground under this spot (a few blocks up or down). */
    private static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos base = BlockPos.containing(at);
        for (int dy = 1; dy >= -8; dy--) {
            BlockPos pos = base.above(dy);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty())
                return new Vec3(at.x, level.getBlockState(pos).getCollisionShape(level, pos).max(net.minecraft.core.Direction.Axis.Y) + pos.getY(), at.z);
        }
        return new Vec3(at.x, at.y - 2, at.z);
    }

    // ------------------------------------------------------------------ sync
    private static void send(ServerPlayer p, State s) {
        int target = s.target == null ? -1 : s.target.getId();
        float max = f(PantherConfig.ENERGY_MAX);
        int quiet = (int) Math.min(255, Math.max(0, p.level().getGameTime() - s.lastCombat));
        int reflexMax = PantherConfig.REFLEX_DURATION.get();
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new PantherStatePacket(p.getId(), s.action, s.age, s.flags, s.cooldowns.clone(),
                        Mth.clamp(s.energy / max, 0, 1), power(s), s.reflexLeft / (float) reflexMax, s.reflexLeft, s.camoLeft,
                        s.hurtAge, s.hurtPower, s.hurtYaw, s.threatYaw, quiet, target,
                        s.from, s.dir, s.apex, s.to, s.land, (float) s.reach,
                        f(PantherConfig.POUNCE_SPEED), f(PantherConfig.SPIN_HEIGHT)));
    }
    static void fx(ServerPlayer p, int kind, Vec3 pos, Vec3 dir, float power, int entity) { fxAll(p, kind, pos, dir, power, entity); }
    private static void fxAll(ServerPlayer p, int kind, Vec3 pos, Vec3 dir, float power, int entity) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new PantherFxPacket(kind, pos, dir, power, entity));
    }
    private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
    /** A sound from one of his hands (side -1 right, 1 left, 0 both): the claws alternate left and right in the ear. */
    private static void hand(ServerPlayer p, int side, SoundEvent sound, float volume, float pitch) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(-side * .7);
        Vec3 at = p.position().add(right).add(Vec3.directionFromRotation(0, p.getYRot()).scale(.5)).add(0, 1.2, 0);
        p.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
