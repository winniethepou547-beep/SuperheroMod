package com.FIRNI.superheromod.heroes.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.core.film.FilmSessions;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import com.FIRNI.superheromod.network.packet.IcemanStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Iceman on the server: decides every move, keeps his state and hands the subsystems their ticks (IcemanWeapons the
 * mace / spear / sword, IcemanBrush the stream and the sculptures, IcemanSlide the two slides, IcemanShell the shell,
 * IcemanGround shattered ground; IcemanFrost every body's frost meter). His body's own motion in the slides is steered
 * by his own client (IcemanClient) from the synced action and clock, as with Batman's roll; the server places the hits.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class IcemanController {
    static final class State {
        int action = IDLE, age;
        final int[] cooldowns = new int[COOLDOWNS];
        // the weapon
        int weapon = W_MACE; boolean weaponOut, wheel;
        int combo = -1; long lastSwingEnd = -1000; boolean queued;
        boolean lmb; float charge;
        /** The weapon to form as soon as he is free (picked while busy). */
        boolean formPending;
        /** The sword held: where it stands planted in the ground (null when not), where he kneels, its id. */
        Vec3 plant, kneel; int plantId;
        // the brush
        boolean rmb; int brushTarget = -1, brushAge; IcemanBrush.Sculpture sculpture;
        // the slides
        float slideYaw, dashYaw; int slideAge; boolean dashHit; long noFallUntil;
        /** Let go of the slide in the air: no fall damage until he is down again. */
        boolean airFall;
        final Map<Integer, Long> slideTouched = new HashMap<>();
        // the shell
        float shellHp;
        /** Set by the test command: the next tick breaks / bursts the shell. */
        boolean testBreak;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    static int nextId = 1;

    private IcemanController() {}

    public static boolean isHero(Entity e) { return e instanceof ServerPlayer && ID.equals(AbilityManager.getCharacterId(e.getUUID())); }
    static State peek(ServerPlayer p) { return STATES.get(p.getUUID()); }
    static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), id -> new State()); }
    static void set(State s, int action) { s.action = action; s.age = 0; }
    static void tell(ServerPlayer p, String text) { p.displayClientMessage(Component.literal("§b" + text), true); }
    static String seconds(int ticks) { return String.format(Locale.ROOT, "%.1f sn", ticks / 20f); }

    /** In the middle of something that nothing else may start over. */
    static boolean busy(State s) {
        return s.action == STRIKE || s.action == CHARGE || s.action == RELEASE || s.action == DASH || s.action == GROUND
                || shell(s) || s.action == SLIDE;
    }
    static boolean shell(State s) { return s.action == SHELL_FORM || s.action == SHELL || s.action == SHELL_BREAK || s.action == SHELL_BURST; }
    /** Free to start something new (standing, the wheel open, the weapon forming). */
    static boolean free(State s) { return s.action == IDLE || s.action == WHEEL || s.action == FORM || s.action == SLIDE_END; }

    // ------------------------------------------------------------------ keys
    public static void press(ServerPlayer p, AbilitySlot slot, boolean down) {
        if (!isHero(p)) return;
        State s = state(p);
        if (FilmSessions.busy(p.getUUID())) return;
        int was = s.action, wasAge = s.age; float wasCharge = s.charge;
        switch (slot) {
            case LMB -> { s.lmb = down; if (down) IcemanWeapons.press(p, s); else IcemanWeapons.release(p, s); }
            case RMB -> { s.rmb = down; if (down) { IcemanBrush.press(p, s); conflict(p, s, was, wasAge, wasCharge, BRUSH); } else IcemanBrush.release(p, s); }
            case ULTIMATE -> { if (down) { IcemanShell.press(p, s); conflict(p, s, was, wasAge, wasCharge, SHELL_FORM); } }
            case SKILL_E -> { if (down) { IcemanGround.press(p, s); conflict(p, s, was, wasAge, wasCharge, GROUND); } }
            case SKILL_X -> { if (down) tell(p, "Iceman X: ultimate henüz tasarlanmadı"); }
            default -> {}
        }
    }
    /** What his own client reports: the wheel, the weapon picked, the slides. */
    public static void input(ServerPlayer p, int kind, int value, float amount, Vec3 at) {
        if (!isHero(p)) return;
        State s = state(p);
        if (FilmSessions.busy(p.getUUID())) return;
        switch (kind) {
            case IN_WHEEL_OPEN -> { s.wheel = true; if (s.action == IDLE) set(s, WHEEL); }
            case IN_WHEEL_CLOSE -> { s.wheel = false; if (s.action == WHEEL) set(s, IDLE); }
            case IN_WEAPON_SELECT -> IcemanWeapons.select(p, s, Mth.clamp(value, 0, WEAPONS - 1));
            case IN_SLIDE_ON -> { int was = s.action, wasAge = s.age; float wasCharge = s.charge; IcemanSlide.start(p, s); conflict(p, s, was, wasAge, wasCharge, SLIDE); }
            case IN_SLIDE_OFF -> IcemanSlide.stop(p, s);
            case IN_DASH -> { int was = s.action, wasAge = s.age; float wasCharge = s.charge; IcemanSlide.dash(p, s, amount); conflict(p, s, was, wasAge, wasCharge, DASH); }
            default -> {}
        }
    }
    /**
     * A power that cannot be used with a weapon in hand (the slides, the shell, shattered ground, the brush) has just
     * started (the action is now `started`, fresh): the weapon in his hand breaks. Opening the wheel never does this.
     */
    private static void conflict(ServerPlayer p, State s, int was, int wasAge, float wasCharge, int started) {
        if (s.action != started || s.age != 0 || was == started && wasAge == 0) return;
        IcemanWeapons.abandon(p, s, was, wasCharge);
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!isHero(p)) {
            State gone = STATES.remove(p.getUUID());
            if (gone != null) { IcemanBrush.clear(p, gone); IcemanWeapons.pin(p, false); send(p, new State()); }
            return;
        }
        State s = state(p);
        s.age++;
        for (int i = 0; i < s.cooldowns.length; i++) if (s.cooldowns[i] > 0) s.cooldowns[i]--;
        if (!p.isAlive()) {
            if (s.action != IDLE) { IcemanBrush.release(p, s); set(s, IDLE); }
            s.lmb = s.rmb = false; s.charge = 0;
        }
        switch (s.action) {
            case FORM, STRIKE, CHARGE, RELEASE -> IcemanWeapons.tick(p, s);
            case BRUSH -> IcemanBrush.tick(p, s);
            case SLIDE, DASH, SLIDE_END -> IcemanSlide.tick(p, s);
            case SHELL_FORM, SHELL, SHELL_BREAK, SHELL_BURST -> IcemanShell.tick(p, s);
            case GROUND -> IcemanGround.tick(p, s);
            case WHEEL -> { if (!s.wheel) set(s, IDLE); }
            default -> {}
        }
        // The planted sword lives only while the hold does (anything that cut it short breaks it where it stands).
        if (s.plant != null && !(s.action == CHARGE && s.weapon == W_SWORD)) IcemanWeapons.unplant(p, s);
        IcemanWeapons.pin(p, s.plant != null);
        // A weapon picked while busy forms as soon as he is free.
        if (s.formPending && s.action == IDLE && s.cooldowns[CD_WEAPON] <= 0) IcemanWeapons.form(p, s);
        if (p.level().getGameTime() < s.noFallUntil || s.airFall) p.fallDistance = 0;
        if (s.airFall && s.action != SLIDE && (p.onGround() || p.isInWater())) s.airFall = false;
        send(p, s);
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        IcemanBrush.tickSculptures();
        IcemanSlide.tickTracks();
        IcemanWeapons.tickSpears();
        IcemanGround.tickCracks();
        IcemanTest.tick(e.getServer());
    }

    // ------------------------------------------------------------------ being hit, falling
    /** The shell takes every blow in his place. */
    @SubscribeEvent public static void attacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (IcemanShell.absorb(p, s, e.getSource(), e.getAmount())) e.setCanceled(true);
    }
    @SubscribeEvent public static void fall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && (p.level().getGameTime() < s.noFallUntil || s.airFall || s.action == SLIDE || s.action == DASH)) { e.setDistance(0); e.setCanceled(true); }
    }
    /** His left click is his own; the vanilla punch would hit twice. */
    @SubscribeEvent public static void plainAttack(AttackEntityEvent e) { if (isHero(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void respawned(PlayerEvent.Clone e) {
        State s = STATES.get(e.getEntity().getUUID());
        if (s == null) return;
        s.lmb = s.rmb = false; s.charge = 0; s.weaponOut = false; s.brushTarget = -1; s.sculpture = null; s.plant = s.kneel = null; set(s, IDLE);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        State s = STATES.remove(e.getEntity().getUUID());
        if (s != null && e.getEntity() instanceof ServerPlayer p) IcemanBrush.clear(p, s);
    }

    // ------------------------------------------------------------------ helpers
    static boolean targetable(ServerPlayer p, LivingEntity t) {
        return t != p && t.isAlive() && !t.isSpectator() && !(t instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && (IcemanConfig.FRIENDLY_FIRE.get() || !(t instanceof Player other && p.isAlliedTo(other)));
    }
    /**
     * A blow from Iceman: on someone deep frozen it breaks the ice and does more; then the damage, then the frost.
     * Returns whether the body took it.
     */
    static boolean hurt(ServerPlayer p, LivingEntity t, float damage, float frost) {
        float bonus = IcemanFrost.shatter(t);
        boolean shattered = bonus > 1;
        boolean took = false;
        if (damage > 0) {
            t.invulnerableTime = 0;
            took = t.hurt(p.damageSources().playerAttack(p), damage * bonus);
        }
        if (!shattered) IcemanFrost.add(p, t, frost, false);
        return took || shattered;
    }
    static Vec3 flat(Vec3 v) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }
    static Vec3 facing(ServerPlayer p) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }
    /** Everyone in front of him to hit: in reach, inside the cone (cos), nearest first. */
    static List<LivingEntity> inFront(ServerPlayer p, double reach, double cos) {
        Vec3 eye = p.getEyePosition(), look = flat(p.getLookAngle());
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach + 1), t -> targetable(p, t))) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            Vec3 fl = new Vec3(to.x, 0, to.z);
            double d = fl.length() - t.getBbWidth() * .5;
            if (d > reach || Math.abs(to.y) > 2.8) continue;
            double along = fl.lengthSqr() < 1e-6 ? 1 : fl.normalize().dot(look);
            if (along < cos && d > .6) continue;
            out.add(t);
        }
        out.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        return out;
    }
    /** Everyone round a point within a radius (a sphere squashed a little in height). */
    static List<LivingEntity> around(ServerPlayer p, Vec3 at, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1), t -> targetable(p, t))) {
            Vec3 c = t.getBoundingBox().getCenter();
            double dx = c.x - at.x, dz = c.z - at.z, dy = (c.y - at.y) * .7;
            if (Math.sqrt(dx * dx + dz * dz + dy * dy) - t.getBbWidth() * .5 <= radius) out.add(t);
        }
        return out;
    }
    /** The body he is looking at (within range, the ray thickened a little), or null. */
    static LivingEntity aimed(ServerPlayer p, double range, double thick) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        LivingEntity best = null;
        double bestD = 1e9;
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, stop).inflate(thick + 1), t -> targetable(p, t))) {
            var hit = t.getBoundingBox().inflate(thick).clip(eye, stop);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceToSqr(eye);
            if (d < bestD) { bestD = d; best = t; }
        }
        return best;
    }
    /** Where his aim meets a block (or the end of the range). */
    static Vec3 aimPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
    }
    /** The top of the ground under (or just over) a point, or null when there is none near. */
    static Vec3 ground(ServerLevel level, Vec3 at, int down) {
        BlockPos base = BlockPos.containing(at);
        for (int dy = 2; dy >= -down; dy--) {
            BlockPos pos = base.above(dy);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty())
                return new Vec3(at.x, pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), at.z);
        }
        return null;
    }
    /** About where his hand is (side 0 right), for things leaving it. */
    static Vec3 hand(ServerPlayer p, int side) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(side == 0 ? .4 : -.4);
        return p.getEyePosition().add(0, -.4, 0).add(right).add(p.getLookAngle().scale(.5));
    }

    // ------------------------------------------------------------------ sync
    static void send(ServerPlayer p, State s) {
        int flags = (s.wheel ? F_WHEEL : 0) | (s.weaponOut ? F_WEAPON : 0) | (shell(s) && s.action != SHELL_BREAK ? F_SHELL : 0)
                | (s.sculpture != null ? F_SCULPTING : 0) | (s.action == SLIDE ? F_SLIDING : 0);
        float shellK = s.shellHp <= 0 ? 0 : s.shellHp / (float) (double) IcemanConfig.SHELL_HEALTH.get();
        float slideLeft = s.action == SLIDE ? 1 - s.slideAge / (float) (IcemanConfig.SLIDE_SECONDS.get() * 20) : 0;
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new IcemanStatePacket(p.getId(), s.action, s.age, flags, s.weapon, Math.max(0, s.combo), s.charge, s.brushTarget,
                        shellK, s.sculpture == null ? 0 : s.sculpture.id, slideLeft, s.cooldowns.clone()));
    }
    /** An effect on or about him (everyone who sees him, and he). */
    static void fx(ServerPlayer p, int kind, Vec3 pos, Vec3 dir, float power, int entity, int id) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new IcemanFxPacket(kind, pos, dir, power, entity, id));
    }
    /** An effect at a place (a sculpture, a spear, the cracks): everyone near that place sees it (and he does). */
    static void fxAt(ServerPlayer owner, Vec3 at, int kind, Vec3 pos, Vec3 dir, float power, int entity, int id) {
        var packet = new IcemanFxPacket(kind, pos, dir, power, entity, id);
        ModNetworking.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 128, owner.level().dimension())), packet);
        if (owner.position().distanceTo(at) > 128) ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner), packet);
    }
    /** An effect for one player only (the icy lens on their screen). */
    static void fxTo(ServerPlayer to, int kind, Vec3 pos, Vec3 dir, float power, int entity, int id) {
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), new IcemanFxPacket(kind, pos, dir, power, entity, id));
    }
    static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
    static void at(Entity e, Vec3 at, SoundEvent sound, float volume, float pitch) {
        e.level().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
    static EntityHitResult entityOnPath(ServerPlayer p, Vec3 from, Vec3 to, double inflate) {
        return ProjectileUtil.getEntityHitResult(p.level(), p, from, to, new AABB(from, to).inflate(inflate), e -> e instanceof LivingEntity l && targetable(p, l));
    }
}
