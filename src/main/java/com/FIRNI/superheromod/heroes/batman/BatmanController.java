package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.core.film.FilmSessions;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import com.FIRNI.superheromod.network.packet.BatmanStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanConfig.f;

/**
 * Batman on the server: decides every move and simulates his things (Batarangs, gadget pellets, smoke clouds,
 * the grapnel hook). His body's own motion (the pull along the line, the strike's jump and backflip, the roll, the
 * glide) is steered by his own client from the synced action and clock (BatmanClient), as with Black Panther; the
 * server places the hits and moves the ones he hits.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BatmanController {
    static final class State {
        int action = IDLE, age;
        final int[] cooldowns = new int[COOLDOWNS];
        // punches
        int combo = -1; long lastBlowEnd = -1000; boolean queued;
        // Batarangs
        int batarangs = BATARANG_MAX, refill; boolean charging; int chargeAge, charge, throwCount;
        // gadgets
        int gadget = G_SMOKE, throwing; boolean wheel;
        // movement
        boolean gliding, noFall; int landed; float dodgeYaw;
        // grapnel: 0 none, 1 flying out, 2 attached, 3 reeling back (missed)
        boolean aiming; int hook; Vec3 hookPos, hookAt; LivingEntity hookEntity; int hookAge, pullAge, stall; double best;
        LivingEntity strikeTarget; Vec3 strikeDir = new Vec3(0, 0, 1);
        /** Right click fired the line (a yank on a body), the one being yanked. */
        boolean yankShot; LivingEntity yankTarget;
        /** The electric gauntlets worn (BatmanShock keeps the rest) and their charge 0..1. */
        boolean shock; float energy = 1;
        /** Q: the reflex window is open until this game time (BatmanReflex). */
        long reflexUntil = -1, lastDeflect = -100;
    }
    /** Someone pulled off their feet and dragged toward him. */
    private static final class Drag {
        final LivingEntity target; final Vec3 dir; int age;
        Drag(LivingEntity target, Vec3 dir) { this.target = target; this.dir = dir; }
    }
    private static final List<Drag> DRAGS = new ArrayList<>();
    /** A Batarang in flight. */
    private static final class Rang {
        final int id; final ServerPlayer owner; Vec3 pos; final Vec3 vel; int age;
        Rang(int id, ServerPlayer owner, Vec3 pos, Vec3 vel) { this.id = id; this.owner = owner; this.pos = pos; this.vel = vel; }
    }
    /** A gadget pellet in flight (smoke, flash or thermal). */
    private static final class Pellet {
        final int id, gadget; final ServerPlayer owner; Vec3 pos, vel; int age;
        Pellet(int id, int gadget, ServerPlayer owner, Vec3 pos, Vec3 vel) { this.id = id; this.gadget = gadget; this.owner = owner; this.pos = pos; this.vel = vel; }
    }
    /** A smoke cloud on the ground. */
    private static final class Cloud {
        final ServerPlayer owner; final Vec3 at; final double radius; final int life; int age;
        Cloud(ServerPlayer owner, Vec3 at, double radius, int life) { this.owner = owner; this.at = at; this.radius = radius; this.life = life; }
        boolean inside(Entity e) {
            Vec3 c = e.getBoundingBox().getCenter();
            double dx = c.x - at.x, dz = c.z - at.z, dy = (c.y - at.y) / .75;
            return dx * dx + dz * dz + Math.max(0, dy) * Math.max(0, dy) < radius * radius && dy > -1.5;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Rang> RANGS = new ArrayList<>();
    private static final List<Pellet> PELLETS = new ArrayList<>();
    private static final List<Cloud> CLOUDS = new ArrayList<>();
    /** Mobs dazed by a flash (until this game time they target nobody). */
    private static final Map<Integer, Long> DAZED = new HashMap<>();
    private static int nextId = 1;

    /** True while the X film's client side exists and is registered in FilmSessionClient (DarkKnightFilm). */
    static final boolean FILM_READY = true;

    private BatmanController() {}

    public static boolean isHero(Entity e) { return e instanceof ServerPlayer && ID.equals(AbilityManager.getCharacterId(e.getUUID())); }
    /** His state if he has one yet (null otherwise), for the event handlers of the other Batman classes. */
    static State peek(ServerPlayer p) { return STATES.get(p.getUUID()); }
    static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), id -> new State()); }
    static void set(State s, int action) { s.action = action; s.age = 0; }
    static void tell(ServerPlayer p, String text) { p.displayClientMessage(Component.literal("§7" + text), true); }
    /** In the middle of something nothing else may start over it. */
    static boolean busy(State s) {
        return s.action == GRAPNEL_FIRE || s.action == GRAPNEL_PULL || s.action == GRAPNEL_STRIKE || s.action == GRAPNEL_YANK || s.action == DODGE || s.action == SHOCK_EQUIP || s.action == SHOCK_UNEQUIP
                || s.action == CANNON || s.action == SONIC;
    }

    // ------------------------------------------------------------------ keys
    public static void press(ServerPlayer p, AbilitySlot slot, boolean down) {
        if (!isHero(p)) return;
        State s = state(p);
        if (FilmSessions.busy(p.getUUID())) return;
        if (slot == AbilitySlot.ULTIMATE && down) { BatmanReflex.start(p, s); return; }
        // X: KARA ŞÖVALYE (BatmanUltSession holds both, DarkKnightFilm plays it). FILM_READY stays as a switch: off, X only
        // says so (starting the session with no film registered would hold both players with nothing on screen).
        if (slot == AbilitySlot.SKILL_X && down) { if (FILM_READY) BatmanUltSession.start(p, s); else tell(p, "Batman sinematiği henüz hazır değil"); return; }
        if (slot == AbilitySlot.LMB && down) { if (s.aiming) fireGrapnel(p, s, false); else click(p, s); }
        if (slot == AbilitySlot.RMB) {
            if (down) { if (s.aiming) fireGrapnel(p, s, true); else startCharge(p, s); }
            else releaseCharge(p, s);
        }
    }
    /** What his own client reports: glide, roll, the wheel, the grapnel. */
    public static void input(ServerPlayer p, int kind, int value, float amount) {
        // A click of someone bound by his line (they need not be Batman).
        if (kind == IN_BREAK_FREE) { BatmanBind.click(p); return; }
        if (!isHero(p)) return;
        State s = state(p);
        if (FilmSessions.busy(p.getUUID())) return;
        switch (kind) {
            case IN_GLIDE_ON -> { if (!p.onGround() && s.action != GRAPNEL_PULL && s.action != GRAPNEL_STRIKE && !s.gliding) { s.gliding = true; sound(p, ModSounds.BATMAN_CAPE.get(), .7f, 1f); } }
            case IN_GLIDE_OFF -> s.gliding = false;
            case IN_DODGE -> dodge(p, s, amount);
            case IN_GADGET_SELECT -> {
                s.gadget = Mth.clamp(value, 0, GADGETS - 1);
                sound(p, SoundEvents.UI_BUTTON_CLICK.get(), .3f, 1.6f);
                // Picking another gadget takes the electric gauntlets off.
                if (s.gadget != G_SHOCK && s.shock) {
                    if (s.action == SHOCK_EQUIP) set(s, IDLE);
                    if (!BatmanShock.toggle(p, s)) { s.shock = false; s.combo = -1; }
                }
            }
            case IN_GADGET_USE -> useGadget(p, s);
            case IN_GRAPNEL_TOGGLE -> {
                // Pulled along the line: let go early, carried on by the pull with a hop up (his client throws him).
                if (s.action == GRAPNEL_PULL) letGo(p, s);
                else if (s.aiming) stopAiming(s);
                else aim(p, s);
            }
            case IN_WHEEL_OPEN -> { s.wheel = true; if (s.action == IDLE) set(s, WHEEL); }
            case IN_WHEEL_CLOSE -> { s.wheel = false; if (s.action == WHEEL) set(s, IDLE); }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ left click: the punches
    private static void click(ServerPlayer p, State s) {
        // The electric gauntlets on: the heavy electric boxing combo instead of the rapid punches.
        if (s.shock || s.action == SHOCK_EQUIP || s.action == SHOCK_PUNCH) { BatmanShock.click(p, s); return; }
        if (s.action == PUNCH) { s.queued = true; return; }
        if (busy(s) || s.charging) return;
        startBlow(p, s);
    }
    private static void startBlow(ServerPlayer p, State s) {
        long now = p.level().getGameTime();
        s.combo = s.combo >= 0 && now - s.lastBlowEnd <= PUNCH_CHAIN ? s.combo + 1 : 0;
        s.queued = false;
        set(s, PUNCH);
        // A step into the one in front, Arkham-style: the blow carries him in.
        LivingEntity t = inFront(p, 5.5, .55);
        if (t != null) {
            Vec3 to = t.position().subtract(p.position());
            double d = Math.sqrt(to.x * to.x + to.z * to.z);
            if (d > 2.1) {
                Vec3 dir = new Vec3(to.x / d, 0, to.z / d);
                double v = Math.min(.75, (d - 1.6) * .3);
                p.setDeltaMovement(dir.x * v, p.getDeltaMovement().y, dir.z * v);
                p.hurtMarked = true;
            }
        }
        sound(p, ModSounds.FX_WHOOSH_LIGHT.get(), .35f, 1.2f + Math.min(5, s.combo) * .06f);
    }
    private static void punchTick(ServerPlayer p, State s) {
        int len = punchTicks(s.combo), hit = Math.max(1, Math.round(len * PUNCH_HIT));
        if (s.age == hit) land(p, s);
        if (s.age >= len) {
            s.lastBlowEnd = p.level().getGameTime();
            if (s.queued) startBlow(p, s); else set(s, IDLE);
        }
    }
    private static void land(ServerPlayer p, State s) {
        LivingEntity t = inFront(p, BatmanConfig.PUNCH_REACH.get(), .5);
        if (t == null) return;
        boolean rapid = s.combo >= RAPID;
        float damage = rapid ? f(BatmanConfig.PUNCH_RAPID_DAMAGE) : f(BatmanConfig.PUNCH_DAMAGE) * (1 + .12f * s.combo);
        Vec3 dir = flat(t.position().subtract(p.position()));
        double knock = BatmanConfig.PUNCH_KNOCK.get() * (rapid ? .25 : blow(s.combo) == B_UPPER ? .5 : blow(s.combo) == B_ELBOW ? 2.2 : 1);
        hurt(p, t, damage);
        Vec3 m = t.getDeltaMovement();
        t.setDeltaMovement(m.x * .4 + dir.x * knock, blow(s.combo) == B_UPPER ? Math.max(m.y, .42) : m.y, m.z * .4 + dir.z * knock);
        t.hurtMarked = true;
        Vec3 at = t.getBoundingBox().getCenter().add(dir.scale(-t.getBbWidth() * .5)).add(0, .15, 0);
        fx(p, FX_PUNCH, at, dir, s.combo, t.getId(), p.getId());
        at(p, at, ModSounds.BATMAN_PUNCH.get(), rapid ? .7f : 1f, .9f + p.getRandom().nextFloat() * .2f + (rapid ? .15f : 0));
        at(p, at, rapid ? SoundEvents.PLAYER_ATTACK_WEAK : SoundEvents.PLAYER_ATTACK_STRONG, .6f, rapid ? 1.3f : .9f);
        if (blow(s.combo) == B_ELBOW || blow(s.combo) == B_UPPER) at(p, at, ModSounds.FX_IMPACT_HEAVY.get(), .6f, 1.3f);
    }

    // ------------------------------------------------------------------ right click: Batarangs
    private static void startCharge(ServerPlayer p, State s) {
        if (busy(s) || s.action == BATARANG || s.action == BATARANG_MULTI) return;
        if (s.batarangs <= 0) { tell(p, "Batarang kalmadı"); return; }
        s.charging = true; s.chargeAge = 0; s.charge = 1;
    }
    private static void chargeTick(ServerPlayer p, State s) {
        if (!s.charging) return;
        s.chargeAge++;
        int was = s.charge;
        s.charge = Mth.clamp(s.chargeAge / BATARANG_STEP, 1, Math.min(BATARANG_MAX, s.batarangs));
        // Held past a tap: both hands come together with the Batarangs fanned between the fingers.
        if (s.chargeAge == BATARANG_STEP && (s.action == IDLE || s.action == PUNCH || s.action == WHEEL)) set(s, BATARANG_CHARGE);
        if (s.charge > was) sound(p, SoundEvents.ARMOR_EQUIP_CHAIN, .4f, 1.4f + s.charge * .1f);
    }
    private static void releaseCharge(ServerPlayer p, State s) {
        if (!s.charging) return;
        s.charging = false;
        int k = s.chargeAge < BATARANG_STEP ? 1 : s.charge;
        if (busy(s)) return;
        s.throwCount = k;
        set(s, k <= 1 ? BATARANG : BATARANG_MULTI);
    }
    private static void throwRangs(ServerPlayer p, State s) {
        int count = Math.min(s.throwCount, s.batarangs);
        if (count <= 0) return;
        s.batarangs -= count;
        Vec3 aim = aimPoint(p, 60);
        for (int i = 0; i < count; i++) {
            int side = count == 1 ? 0 : i % 2;
            Vec3 from = hand(p, side);
            Vec3 dir = aim.subtract(from).normalize();
            // Fanned out a little round the aim, never quite on top of each other.
            float spread = count == 1 ? 0 : (i - (count - 1) / 2f) * 2.4f;
            dir = dir.yRot(spread * Mth.DEG_TO_RAD).add(0, (p.getRandom().nextFloat() - .5f) * .008f, 0).normalize();
            Rang r = new Rang(nextId++, p, from, dir.scale(BatmanConfig.BATARANG_SPEED.get()));
            RANGS.add(r);
            fx(p, FX_BATARANG, from, r.vel, count, p.getId(), r.id);
        }
        sound(p, ModSounds.BATMAN_BATARANG.get(), .8f, count > 1 ? .9f : 1.1f);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .35f, 1.8f);
    }

    // ------------------------------------------------------------------ R: gadgets
    private static void useGadget(ServerPlayer p, State s) {
        if (busy(s)) return;
        int g = s.gadget;
        if (s.cooldowns[g] > 0) { tell(p, GADGET_NAMES[g] + ": " + String.format(Locale.ROOT, "%.1f", s.cooldowns[g] / 20f) + " sn"); return; }
        // The two big ones run themselves (BatmanCannon, BatmanSonic); they say whether they started.
        if (g == G_CANNON) { s.aiming = false; s.charging = false; if (BatmanCannon.use(p, s)) s.cooldowns[g] = BatmanConfig.CD_CANNON.get(); return; }
        if (g == G_SONIC) { s.aiming = false; s.charging = false; if (BatmanSonic.use(p, s)) s.cooldowns[g] = BatmanConfig.CD_SONIC.get(); return; }
        if (g == G_SHOCK) { s.aiming = false; s.charging = false; if (BatmanShock.toggle(p, s)) s.cooldowns[g] = BatmanConfig.CD_SHOCK.get(); return; }
        s.cooldowns[g] = g == G_SMOKE ? BatmanConfig.CD_SMOKE.get() : BatmanConfig.CD_FLASH.get();
        s.throwing = g;
        s.aiming = false;
        set(s, GADGET_THROW);
        sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, .5f, 1.3f);
    }
    private static void throwPellet(ServerPlayer p, State s) {
        Vec3 from = hand(p, 0);
        Vec3 vel = p.getLookAngle().scale(1.15).add(0, .14, 0);
        Pellet pl = new Pellet(nextId++, s.throwing, p, from, vel);
        PELLETS.add(pl);
        fx(p, FX_GADGET, from, vel, s.throwing, p.getId(), pl.id);
        sound(p, ModSounds.FX_WHOOSH_LIGHT.get(), .5f, .9f);
    }
    private static void detonate(Pellet pl, Vec3 at) {
        ServerPlayer p = pl.owner;
        ServerLevel level = p.serverLevel();
        switch (pl.gadget) {
            case G_SMOKE -> {
                double r = BatmanConfig.SMOKE_RADIUS.get();
                int life = (int) (BatmanConfig.SMOKE_SECONDS.get() * 20);
                CLOUDS.add(new Cloud(p, at, r, life));
                fxAt(at, FX_SMOKE, at, new Vec3(life, 0, 0), (float) r, p.getId(), pl.id, p);
                level.playSound(null, at.x, at.y, at.z, ModSounds.BATMAN_SMOKE.get(), SoundSource.PLAYERS, 1.4f, 1f);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2f, .5f);
            }
            case G_FLASH -> {
                double r = BatmanConfig.FLASH_RADIUS.get();
                int blind = (int) (BatmanConfig.FLASH_SECONDS.get() * 20);
                fxAt(at, FX_FLASH, at, Vec3.ZERO, (float) r, -1, pl.id, p);
                level.playSound(null, at.x, at.y, at.z, ModSounds.BATMAN_FLASH.get(), SoundSource.PLAYERS, 1.6f, 1f);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.4f, 1.6f);
                for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r), t -> targetable(p, t))) {
                    double d = t.getEyePosition().distanceTo(at);
                    if (d > r) continue;
                    // Only those who can see it: a wall in between saves them.
                    if (level.clip(new ClipContext(at, t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t)).getType() != HitResult.Type.MISS) continue;
                    float k = (float) (1 - d / r * .5);
                    int ticks = (int) (blind * k);
                    t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false, true));
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, false, true));
                    if (t instanceof ServerPlayer victim)
                        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> victim), new BatmanFxPacket(FX_FLASH, at, Vec3.ZERO, k, victim.getId(), pl.id));
                    if (t instanceof Mob mob) { mob.setTarget(null); mob.getNavigation().stop(); DAZED.put(mob.getId(), level.getGameTime() + ticks); }
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ E: the grapnel
    private static void aim(ServerPlayer p, State s) {
        if (busy(s)) return;
        if (s.cooldowns[CD_GRAPNEL] > 0) { tell(p, "Kanca: " + String.format(Locale.ROOT, "%.1f", s.cooldowns[CD_GRAPNEL] / 20f) + " sn"); return; }
        s.aiming = true; s.charging = false;
        set(s, GRAPNEL_AIM);
        sound(p, SoundEvents.CROSSBOW_LOADING_END, .6f, 1.4f);
    }
    private static void stopAiming(State s) {
        s.aiming = false;
        if (s.action == GRAPNEL_AIM) set(s, IDLE);
    }
    /** Left click: the line pulls him (to a block) or carries him into the strike (a body); right click on a body: the yank. */
    private static void fireGrapnel(ServerPlayer p, State s, boolean yank) {
        if (!s.aiming) return;
        s.aiming = false;
        s.yankShot = yank;
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(BatmanConfig.GRAPNEL_RANGE.get()));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, eye, stop, new AABB(eye, stop).inflate(1),
                e -> e instanceof LivingEntity l && targetable(p, l));
        s.hookEntity = eh != null && eh.getEntity() instanceof LivingEntity l ? l : null;
        s.hookAt = s.hookEntity != null ? s.hookEntity.getBoundingBox().getCenter() : bh.getType() == HitResult.Type.MISS ? null : stop;
        s.hookPos = muzzle(p);
        s.hook = 1; s.hookAge = 0;
        // Missing everything: the hook flies to its end and is reeled back.
        if (s.hookAt == null) { s.hookAt = end; s.hook = -1; }
        set(s, GRAPNEL_FIRE);
        fx(p, FX_HOOK, s.hookPos, s.hookAt, s.hook < 0 ? 0 : 1, s.hookEntity == null ? -1 : s.hookEntity.getId(), p.getId());
        sound(p, ModSounds.BATMAN_GRAPNEL.get(), 1f, 1f);
        sound(p, SoundEvents.CROSSBOW_SHOOT, .6f, 1.3f);
    }
    private static void hookTick(ServerPlayer p, State s) {
        if (s.hook == 0 || s.hook == 4) return;
        s.hookAge++;
        boolean miss = s.hook < 0, back = s.hook == 3;
        Vec3 target = back ? muzzle(p) : s.hookEntity != null && s.hookEntity.isAlive() ? s.hookEntity.getBoundingBox().getCenter() : s.hookAt;
        if (s.hook == 1 || miss || back) {
            Vec3 to = target.subtract(s.hookPos);
            double d = to.length(), step = back ? 4 : HOOK_SPEED;
            if (d <= step || s.hookAge > 40) {
                s.hookPos = target;
                if (back) { endHook(p, s, false); set(s, IDLE); return; }
                if (miss) { s.hook = 3; return; }
                // A right-click shot on a body: the line wraps their legs and he hauls them down.
                if (s.yankShot && s.hookEntity != null) {
                    s.hook = 4; s.yankTarget = s.hookEntity;
                    set(s, GRAPNEL_YANK);
                    fx(p, FX_HOOK_HIT, s.hookPos, Vec3.ZERO, 2, s.hookEntity.getId(), p.getId());
                    at(p, s.hookPos, SoundEvents.CHAIN_HIT, 1f, .8f);
                    at(p, s.hookPos, SoundEvents.ARMOR_EQUIP_LEATHER, 1f, .6f);
                    return;
                }
                // Caught: the slack line snaps taut and pulls him in.
                s.hook = 2; s.pullAge = 0; s.stall = 0; s.best = 1e9; s.noFall = true; s.gliding = false;
                set(s, GRAPNEL_PULL);
                fx(p, FX_HOOK_HIT, s.hookPos, Vec3.ZERO, s.hookEntity != null ? 1 : 0, s.hookEntity == null ? -1 : s.hookEntity.getId(), p.getId());
                at(p, s.hookPos, s.hookEntity != null ? SoundEvents.TRIDENT_HIT : SoundEvents.CHAIN_HIT, 1f, 1.1f);
                sound(p, SoundEvents.CROSSBOW_QUICK_CHARGE_3, .6f, 1.5f);
            } else s.hookPos = s.hookPos.add(to.scale(step / d));
            return;
        }
        // Attached: he is pulled along the line (his own client flies him); here only the arrival is decided.
        if (s.hookEntity != null) {
            if (!s.hookEntity.isAlive()) { endHook(p, s, false); set(s, IDLE); return; }
            s.hookPos = s.hookEntity.getBoundingBox().getCenter();
        }
        s.pullAge++;
        p.fallDistance = 0;
        double d = p.position().add(0, 1, 0).distanceTo(s.hookPos);
        if (d < s.best - .05) { s.best = d; s.stall = 0; } else s.stall++;
        if (s.hookEntity != null && d < 2.4) {
            // On a body: the grapnel strike.
            s.strikeTarget = s.hookEntity;
            s.strikeDir = flat(s.hookEntity.position().subtract(p.position()));
            endHook(p, s, false);
            set(s, GRAPNEL_STRIKE);
            fx(p, FX_STRIKE, p.position(), s.strikeDir, 0, s.strikeTarget.getId(), p.getId());
            return;
        }
        if (d < 1.7 || s.pullAge > 70 || s.stall > 14) {
            endHook(p, s, true);
            set(s, IDLE);
            s.cooldowns[CD_GRAPNEL] = BatmanConfig.CD_GRAPNEL.get();
        }
    }
    /** E while pulled: the line is let go mid-flight; his client keeps the momentum and adds a hop up. */
    private static void letGo(ServerPlayer p, State s) {
        endHook(p, s, false);
        set(s, IDLE);
        s.noFall = true;
        s.cooldowns[CD_GRAPNEL] = BatmanConfig.CD_GRAPNEL.get();
        sound(p, ModSounds.BATMAN_CAPE.get(), .7f, 1.1f);
        sound(p, SoundEvents.CROSSBOW_LOADING_END, .5f, 1.6f);
    }
    private static void endHook(ServerPlayer p, State s, boolean arrived) {
        s.hook = 0; s.hookEntity = null;
        fx(p, FX_HOOK_END, s.hookPos == null ? p.position() : s.hookPos, Vec3.ZERO, arrived ? 1 : 0, -1, p.getId());
        if (arrived) sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, .7f, .8f);
    }
    /** The yank: the line round their legs, his left hand hauling it; they go down on their back and are dragged toward him. */
    private static void yankTick(ServerPlayer p, State s) {
        LivingEntity t = s.yankTarget;
        boolean live = t != null && t.isAlive() && targetable(p, t);
        if (live && s.hook == 4) s.hookPos = t.position().add(0, .25, 0);
        if (s.age == YANK_PULL) sound(p, SoundEvents.CROSSBOW_QUICK_CHARGE_1, .8f, .7f);
        if (s.age == YANK_DOWN && live) {
            Vec3 dir = flat(p.position().subtract(t.position()));
            hurt(p, t, 2);
            DRAGS.removeIf(d -> d.target == t);
            DRAGS.add(new Drag(t, dir));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DOWN_TICKS, 6, false, false, false));
            t.addEffect(new MobEffectInstance(MobEffects.JUMP, DOWN_TICKS, 128, false, false, false));
            if (t instanceof Mob mob) mob.getNavigation().stop();
            BatmanStagger.apply(t, DOWN_TICKS + STAGGER_TICKS);
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> t), new BatmanFxPacket(FX_DOWNED, t.position(), dir, DOWN_TICKS, t.getId(), p.getId()));
            at(p, t.position(), ModSounds.FX_IMPACT_HEAVY.get(), 1f, .7f);
            at(p, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, .9f, .7f);
            at(p, t.position(), SoundEvents.GRAVEL_BREAK, 1f, .6f);
        }
        if (s.age == YANK_DOWN + DRAG_TICKS && s.hook == 4) {
            // He lets go of the line; it stays wound round them (they cannot move until they break free).
            if (live) BatmanBind.bind(t, p);
            endHook(p, s, false);
            s.yankTarget = null;
        }
        if (s.age >= YANK_TICKS) {
            if (s.hook == 4) endHook(p, s, false);
            s.yankTarget = null;
            s.cooldowns[CD_GRAPNEL] = BatmanConfig.CD_GRAPNEL.get();
            set(s, IDLE);
        }
    }
    private static void tickDrags() {
        for (Iterator<Drag> it = DRAGS.iterator(); it.hasNext(); ) {
            Drag d = it.next();
            if (d.target.isRemoved() || !d.target.isAlive() || d.age >= DRAG_TICKS) { it.remove(); continue; }
            // Dragged along the ground on their back, slowing as the line goes slack.
            double v = DRAG_DIST / DRAG_TICKS * 1.9 * (1 - d.age / (double) DRAG_TICKS);
            Vec3 m = d.target.getDeltaMovement();
            d.target.setDeltaMovement(d.dir.x * v, Math.min(m.y, 0), d.dir.z * v);
            d.target.hurtMarked = true;
            d.age++;
        }
    }
    private static void strikeTick(ServerPlayer p, State s) {
        LivingEntity t = s.strikeTarget;
        p.fallDistance = 0;
        boolean live = t != null && t.isAlive() && targetable(p, t);
        if (s.age == STRIKE_UPPER && live) {
            hurt(p, t, f(BatmanConfig.STRIKE_UPPER_DAMAGE));
            t.setDeltaMovement(s.strikeDir.x * .12, 1.05, s.strikeDir.z * .12);
            t.hurtMarked = true;
            fx(p, FX_STRIKE, t.getBoundingBox().getCenter(), new Vec3(0, 1, 0), 1, t.getId(), p.getId());
            at(p, t.position(), ModSounds.BATMAN_PUNCH.get(), 1.2f, .8f);
            at(p, t.position(), ModSounds.FX_IMPACT_HEAVY.get(), 1f, 1f);
        }
        if (s.age == STRIKE_KICK && live) {
            // In the air, bouncing off each other: the sticky bomb goes on the back of their head.
            BatmanSticky.stick(p, t);
            hurt(p, t, f(BatmanConfig.STRIKE_KICK_DAMAGE));
            t.setDeltaMovement(s.strikeDir.x * 1.5, .32, s.strikeDir.z * 1.5);
            t.hurtMarked = true;
            fx(p, FX_STRIKE, t.getBoundingBox().getCenter(), s.strikeDir, 2, t.getId(), p.getId());
            at(p, t.position(), ModSounds.FX_IMPACT_HEAVY.get(), 1.3f, .8f);
            at(p, t.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .8f);
        }
        if (s.age >= STRIKE_TICKS) {
            s.strikeTarget = null;
            s.cooldowns[CD_GRAPNEL] = BatmanConfig.CD_GRAPNEL.get();
            set(s, IDLE);
        }
    }

    // ------------------------------------------------------------------ CTRL: the roll
    private static void dodge(ServerPlayer p, State s, float yaw) {
        if (s.action == GRAPNEL_PULL || s.action == GRAPNEL_STRIKE || s.action == GRAPNEL_FIRE || s.action == DODGE || s.action == CANNON) return;
        if (s.cooldowns[CD_DODGE] > 0) return;
        s.cooldowns[CD_DODGE] = Math.max(BatmanConfig.CD_DODGE.get(), DODGE_TICKS + 2);
        s.dodgeYaw = yaw; s.aiming = false; s.charging = false; s.gliding = false;
        set(s, DODGE);
        fx(p, FX_DODGE, p.position(), Vec3.ZERO, yaw, p.getId(), 0);
        sound(p, ModSounds.BATMAN_CAPE.get(), .6f, 1.25f);
        sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, .5f, .7f);
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!isHero(p)) {
            State gone = STATES.remove(p.getUUID());
            if (gone != null) { clearOwned(p); send(p, new State()); }
            return;
        }
        State s = state(p);
        s.age++;
        for (int i = 0; i < s.cooldowns.length; i++) if (s.cooldowns[i] > 0) s.cooldowns[i]--;
        // A Batarang back in the belt every so often.
        if (s.batarangs < BATARANG_MAX) {
            if (++s.refill >= BatmanConfig.BATARANG_REFILL_SECONDS.get() * 20) { s.batarangs++; s.refill = 0; }
        } else s.refill = 0;
        if (!p.isAlive()) { s.gliding = false; s.aiming = false; s.charging = false; s.hook = 0; s.hookEntity = null; if (s.action != IDLE) set(s, IDLE); }
        chargeTick(p, s);
        switch (s.action) {
            case PUNCH -> punchTick(p, s);
            case BATARANG -> { if (s.age == BATARANG_AT) throwRangs(p, s); }
            case BATARANG_MULTI -> { if (s.age == MULTI_AT) throwRangs(p, s); }
            case BATARANG_CHARGE -> { if (!s.charging) set(s, IDLE); }
            case GADGET_THROW -> { if (s.age == GADGET_AT) throwPellet(p, s); }
            case GRAPNEL_AIM -> { if (!s.aiming) set(s, IDLE); }
            case GRAPNEL_STRIKE -> strikeTick(p, s);
            case GRAPNEL_YANK -> yankTick(p, s);
            case WHEEL -> { if (!s.wheel) set(s, IDLE); }
            case CANNON -> BatmanCannon.tick(p, s);
            case SONIC -> BatmanSonic.tick(p, s);
            case SHOCK_EQUIP, SHOCK_UNEQUIP, SHOCK_PUNCH -> BatmanShock.tick(p, s);
            default -> {}
        }
        hookTick(p, s);
        if (s.action == GRAPNEL_FIRE && s.hook == 0) set(s, IDLE);
        // Glide: no fall damage while the cape holds him; over once he stands on something.
        boolean under = p.onGround() || !p.level().noCollision(p, p.getBoundingBox().move(0, -.2, 0)) || p.isInWater();
        if (s.gliding) {
            p.fallDistance = 0;
            if (under) { s.gliding = false; fx(p, FX_LAND, p.position(), Vec3.ZERO, 0, p.getId(), 0); }
        }
        // After a pull or a strike he lands without hurting himself.
        if (s.noFall) {
            p.fallDistance = 0;
            boolean moving = s.action == GRAPNEL_PULL || s.action == GRAPNEL_STRIKE || s.action == GRAPNEL_FIRE;
            s.landed = under && !moving ? s.landed + 1 : 0;
            if (s.landed > 6) s.noFall = false;
        }
        if (s.action == GRAPNEL_STRIKE || s.action == GRAPNEL_PULL) s.noFall = true;
        int len = length(s.action);
        if (len > 0 && s.age >= len && s.action != GRAPNEL_STRIKE && s.action != GRAPNEL_YANK) set(s, s.aiming ? GRAPNEL_AIM : IDLE);
        send(p, s);
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        tickRangs();
        tickPellets();
        tickClouds();
        tickDrags();
        if (!DAZED.isEmpty() && e.getServer().getTickCount() % 20 == 0) {
            long now = e.getServer().overworld().getGameTime();
            DAZED.values().removeIf(t -> t < now);
        }
    }
    private static void tickRangs() {
        for (Iterator<Rang> it = RANGS.iterator(); it.hasNext(); ) {
            Rang r = it.next();
            ServerPlayer p = r.owner;
            if (p.isRemoved() || ++r.age > 45) { it.remove(); continue; }
            Vec3 next = r.pos.add(r.vel);
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, r.pos, next, new AABB(r.pos, next).inflate(.55),
                    e -> e instanceof LivingEntity l && targetable(p, l));
            if (eh != null && eh.getEntity() instanceof LivingEntity t) {
                hurt(p, t, f(BatmanConfig.BATARANG_DAMAGE));
                Vec3 dir = r.vel.normalize();
                t.setDeltaMovement(t.getDeltaMovement().add(dir.x * .3, .08, dir.z * .3));
                t.hurtMarked = true;
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1, false, false, true));
                fx(p, FX_BATARANG_HIT, eh.getLocation(), dir, -1, t.getId(), r.id);
                at(p, eh.getLocation(), SoundEvents.TRIDENT_HIT, .7f, 1.6f);
                at(p, eh.getLocation(), ModSounds.FX_IMPACT_METAL.get(), .4f, 1.7f);
                it.remove();
                continue;
            }
            BlockHitResult bh = p.level().clip(new ClipContext(r.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (bh.getType() != HitResult.Type.MISS) {
                // Stuck in the wall, a while (the clients keep it there).
                fx(p, FX_BATARANG_HIT, bh.getLocation(), r.vel.normalize(), 1, -1, r.id);
                at(p, bh.getLocation(), SoundEvents.TRIDENT_HIT_GROUND, .5f, 1.8f);
                it.remove();
                continue;
            }
            r.pos = next;
        }
    }
    private static void tickPellets() {
        for (Iterator<Pellet> it = PELLETS.iterator(); it.hasNext(); ) {
            Pellet pl = it.next();
            ServerPlayer p = pl.owner;
            if (p.isRemoved() || ++pl.age > 80) { it.remove(); continue; }
            Vec3 next = pl.pos.add(pl.vel);
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, pl.pos, next, new AABB(pl.pos, next).inflate(.3),
                    e -> e instanceof LivingEntity l && targetable(p, l));
            BlockHitResult bh = p.level().clip(new ClipContext(pl.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (eh != null) { detonate(pl, eh.getLocation()); it.remove(); continue; }
            if (bh.getType() != HitResult.Type.MISS) { detonate(pl, bh.getLocation().add(0, .1, 0)); it.remove(); continue; }
            pl.pos = next;
            pl.vel = pl.vel.add(0, -.05, 0).scale(.99);
        }
    }
    private static void tickClouds() {
        for (Iterator<Cloud> it = CLOUDS.iterator(); it.hasNext(); ) {
            Cloud c = it.next();
            if (c.owner.isRemoved() || ++c.age > c.life) { it.remove(); continue; }
            if (c.age % 5 != 0) continue;
            double r = c.radius;
            for (LivingEntity t : c.owner.level().getEntitiesOfClass(LivingEntity.class, new AABB(c.at, c.at).inflate(r, Math.min(r, 8), r), t -> t != c.owner && t.isAlive())) {
                if (!c.inside(t)) continue;
                // Inside the smoke: blind, coughing, lost; he is not.
                if (t instanceof Player) t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, true));
                if (t instanceof Mob mob && mob.getTarget() == c.owner) { mob.setTarget(null); mob.getNavigation().stop(); }
                if (c.age % 20 == 0 && targetable(c.owner, t)) hurt(c.owner, t, f(BatmanConfig.SMOKE_DAMAGE));
            }
        }
    }
    // ------------------------------------------------------------------ being hit, falling, targeting
    /** The roll: untouchable through the middle of it. */
    @SubscribeEvent public static void attacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (s.action == DODGE && s.age >= DODGE_SAFE_FROM && s.age <= DODGE_SAFE_TO) { e.setCanceled(true); return; }
        if (BatmanReflex.block(p, s, e.getSource())) e.setCanceled(true);
    }
    @SubscribeEvent public static void fall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && (s.gliding || s.noFall)) { e.setDistance(0); e.setCanceled(true); }
    }
    /** Lost in the smoke or dazed by the flash: they cannot pick him (or anyone) as a target. */
    @SubscribeEvent public static void target(LivingChangeTargetEvent e) {
        LivingEntity next = e.getNewTarget();
        if (next == null) return;
        Long dazed = DAZED.get(e.getEntity().getId());
        if (dazed != null && dazed > e.getEntity().level().getGameTime()) { e.setCanceled(true); return; }
        if (next instanceof ServerPlayer p && isHero(p)) for (Cloud c : CLOUDS) if (c.owner == p && c.inside(p)) { e.setCanceled(true); return; }
    }
    /** His left click is his own; the vanilla punch would hit twice. */
    @SubscribeEvent public static void plainAttack(AttackEntityEvent e) { if (isHero(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void respawned(PlayerEvent.Clone e) {
        State s = STATES.get(e.getEntity().getUUID());
        if (s == null) return;
        s.gliding = false; s.aiming = false; s.charging = false; s.hook = 0; s.hookEntity = null; s.strikeTarget = null; s.noFall = false; set(s, IDLE);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        STATES.remove(e.getEntity().getUUID());
        if (e.getEntity() instanceof ServerPlayer p) clearOwned(p);
    }
    private static void clearOwned(ServerPlayer p) {
        RANGS.removeIf(r -> r.owner == p);
        PELLETS.removeIf(r -> r.owner == p);
        BatmanSticky.clear(p);
    }

    // ------------------------------------------------------------------ helpers
    static boolean targetable(ServerPlayer p, LivingEntity t) {
        return t != p && t.isAlive() && !t.isSpectator() && (BatmanConfig.FRIENDLY_FIRE.get() || !(t instanceof Player other && p.isAlliedTo(other)));
    }
    static void hurt(ServerPlayer p, LivingEntity t, float damage) {
        if (damage <= 0) return;
        // A staggered one takes a critical, and the stagger ends.
        if (BatmanStagger.consume(t)) {
            damage *= STAGGER_CRIT;
            fx(p, FX_CRIT, t.getBoundingBox().getCenter(), Vec3.ZERO, damage, t.getId(), p.getId());
            at(p, t.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, .9f);
            at(p, t.position(), ModSounds.FX_IMPACT_HEAVY.get(), .8f, 1.2f);
        }
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), damage);
    }
    static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }
    /** The one in front of him to hit: in reach, inside the cone (cos), the nearest to his line of sight first. */
    static LivingEntity inFront(ServerPlayer p, double reach, double cos) {
        Vec3 eye = p.getEyePosition(), look = flat(p.getLookAngle());
        LivingEntity best = null;
        double bestScore = -1e9;
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1), t -> targetable(p, t))) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            Vec3 fl = new Vec3(to.x, 0, to.z);
            double d = fl.length() - t.getBbWidth() * .5;
            if (d > reach || Math.abs(to.y) > 2.6) continue;
            double along = fl.lengthSqr() < 1e-6 ? 1 : fl.normalize().dot(look);
            if (along < cos) continue;
            double score = along * 2 - d / reach;
            if (score > bestScore) { bestScore = score; best = t; }
        }
        return best;
    }
    static Vec3 aimPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, eye, stop, new AABB(eye, stop).inflate(1), e -> e instanceof LivingEntity l && targetable(p, l));
        if (eh != null) return eh.getEntity().getBoundingBox().getCenter();
        return stop;
    }
    static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos base = BlockPos.containing(at);
        for (int dy = 1; dy >= -6; dy--) {
            BlockPos pos = base.above(dy);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty())
                return new Vec3(at.x, pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), at.z);
        }
        return at;
    }
    /** About where his hand is (side 0 right), for things leaving it. */
    static Vec3 hand(ServerPlayer p, int side) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(side == 0 ? .4 : -.4);
        return p.getEyePosition().add(0, -.35, 0).add(right).add(p.getLookAngle().scale(.55));
    }
    static Vec3 muzzle(ServerPlayer p) { return hand(p, 0).add(p.getLookAngle().scale(.35)).add(0, .1, 0); }

    // ------------------------------------------------------------------ sync
    private static void send(ServerPlayer p, State s) {
        int flags = (s.gliding ? 1 : 0) | (s.aiming ? 2 : 0) | (s.wheel ? 4 : 0) | (s.hook == 2 ? 8 : 0);
        float refill = s.batarangs >= BATARANG_MAX ? 0 : s.refill / (float) (BatmanConfig.BATARANG_REFILL_SECONDS.get() * 20);
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new BatmanStatePacket(p.getId(), s.action, s.age, flags, Math.max(0, s.combo), s.charging ? s.charge : 0, s.batarangs, refill,
                        s.gadget, s.dodgeYaw, s.hook != 0 ? s.hookPos : null, s.hookEntity == null ? -1 : s.hookEntity.getId(), s.cooldowns.clone(),
                        s.shock, s.energy, s.reflexUntil > p.level().getGameTime()));
    }
    static void fx(ServerPlayer p, int kind, Vec3 pos, Vec3 dir, float power, int entity, int id) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new BatmanFxPacket(kind, pos, dir, power, entity, id));
    }
    /** An effect at a place (a cloud, a mine): everyone near that place sees it (and he does). */
    static void fxAt(Vec3 at, int kind, Vec3 pos, Vec3 dir, float power, int entity, int id, ServerPlayer owner) {
        var packet = new BatmanFxPacket(kind, pos, dir, power, entity, id);
        ModNetworking.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 96, owner.level().dimension())), packet);
        if (owner.position().distanceTo(at) > 96) ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner), packet);
    }
    static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
    static void at(ServerPlayer p, Vec3 at, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
