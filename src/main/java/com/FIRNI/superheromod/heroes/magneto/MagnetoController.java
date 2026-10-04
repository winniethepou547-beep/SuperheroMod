package com.FIRNI.superheromod.heroes.magneto;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.core.film.FilmSessions;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.MagnetoFxPacket;
import com.FIRNI.superheromod.network.packet.MagnetoStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
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

import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.*;
import static com.FIRNI.superheromod.heroes.magneto.MagnetoConfig.f;

/**
 * Magneto on the server: decides everything (his flight, the rods he drives down and where they strike, the one he
 * holds and drags through the air and what they are slammed into, the iron fist and its punches, the shield and the
 * pieces it bursts into, what they hit and where they stick). The metal itself is simulated here; every client draws
 * the same pieces from the effects it is told about (MagnetoFx) and the synced state (MagnetoClient).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class MagnetoController {
    private static final class State {
        int action = IDLE, age;
        final int[] cooldowns = new int[COOLDOWNS];
        boolean flying, grantedFly; int flightAge, groundTicks;
        int charges = -1, recharge;
        Vec3 barrageAt = Vec3.ZERO; int barrageLeft, barrageNext, barrageSeed;
        LivingEntity held; int holdAge, holdTicks, lastSlam = -100, thrownAge = -1; double holdDist; Vec3 commanded = Vec3.ZERO, lastPos = Vec3.ZERO;
        LivingEntity thrown;
        boolean fist; Vec3 fistPos = Vec3.ZERO, fistVel = Vec3.ZERO, punchFrom = Vec3.ZERO, punchAt = Vec3.ZERO; int fistAge, punchAge = -1, punchesLeft;
        boolean shield; int shieldAge;
    }
    /** A piece of metal in flight or stuck: a rod of the barrage, a shard, a piece of the burst. */
    private static final class Metal {
        final int id, kind; final ServerPlayer owner; Vec3 pos, vel, axis; boolean stuck; int age; final float half, gravity;
        final Set<Integer> hit = new HashSet<>();
        Metal(int id, int kind, ServerPlayer owner, Vec3 pos, Vec3 vel, Vec3 axis, float half, float gravity) {
            this.id = id; this.kind = kind; this.owner = owner; this.pos = pos; this.vel = vel; this.axis = axis; this.half = half; this.gravity = gravity;
        }
    }
    private static final int ROD = 0, SHARD_PIECE = 1, BURST_PIECE = 2;
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Metal> METAL = new ArrayList<>();
    private static int nextId = 1;

    private MagnetoController() {}

    public static boolean isHero(Entity e) { return e instanceof ServerPlayer && ID.equals(AbilityManager.getCharacterId(e.getUUID())); }
    private static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), id -> new State()); }
    private static void set(State s, int action) { s.action = action; s.age = 0; }
    private static void tell(ServerPlayer p, String text) { p.displayClientMessage(Component.literal("§c" + text), true); }
    private static boolean ready(ServerPlayer p, State s, int slot, String name) {
        if (s.cooldowns[slot] <= 0) return true;
        tell(p, name + ": " + String.format(Locale.ROOT, "%.1f", s.cooldowns[slot] / 20f) + " sn");
        return false;
    }
    /** In the middle of a cast nothing else may start over. */
    private static boolean casting(State s) { return s.action == BARRAGE || s.action == GRAB || s.action == FIST_SUMMON || s.action == SHIELD_RAISE || s.action == BURST; }

    // ------------------------------------------------------------------ keys
    public static void press(ServerPlayer p, AbilitySlot slot, boolean down) {
        if (!isHero(p) || !down) return;
        State s = state(p);
        if (FilmSessions.busy(p.getUUID())) return;
        switch (slot) {
            case LMB -> click(p, s);
            case RMB -> { if (s.held != null) release(p, s, false); }
            case SHIFT -> flight(p, s, !s.flying);
            case ULTIMATE -> barrage(p, s);
            case SKILL_V -> grab(p, s);
            case SKILL_E -> fist(p, s);
            case SKILL_F -> shield(p, s);
            case SKILL_X -> ultimate(p, s);
            default -> {}
        }
    }
    /** What his own client reports: jumping twice (into or out of flight). */
    public static void input(ServerPlayer p, int kind) {
        if (!isHero(p)) return;
        if (kind == INPUT_FLIGHT) { State s = state(p); flight(p, s, !s.flying); }
    }

    // ------------------------------------------------------------------ flight
    private static void flight(ServerPlayer p, State s, boolean on) {
        if (on == s.flying) return;
        s.flying = on;
        s.flightAge = 0;
        s.groundTicks = 0;
        // A server that forbids flying would throw him out for floating: let it know he may.
        boolean special = p.gameMode.getGameModeForPlayer() == GameType.CREATIVE || p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
        if (on && !special && !p.getAbilities().mayfly) { p.getAbilities().mayfly = true; s.grantedFly = true; p.onUpdateAbilities(); }
        if (!on && s.grantedFly) { s.grantedFly = false; p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities(); }
        p.fallDistance = 0;
        fx(p, on ? FX_LIFT : FX_LAND, p.position(), Vec3.ZERO, 1, p.getId(), 0);
        sound(p, on ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.ARMOR_EQUIP_IRON, .4f, on ? 1.4f : .8f);
        if (on) sound(p, SoundEvents.ELYTRA_FLYING, .25f, 1.6f);
    }

    // ------------------------------------------------------------------ left click: shard, punch, throw
    private static void click(ServerPlayer p, State s) {
        if (s.fist && s.action == FIST) { punch(p, s); return; }
        if (s.held != null && s.holdAge > SCRAP_FLY) { throwHeld(p, s); return; }
        if (casting(s) || s.cooldowns[CD_SHARD] > 0) return;
        s.cooldowns[CD_SHARD] = MagnetoConfig.SHARD_COOLDOWN.get();
        if (s.action == IDLE || s.action == SHARD) set(s, SHARD);
        Vec3 look = p.getLookAngle();
        Vec3 from = hand(p, 0);
        Vec3 aim = aimPoint(p, 60);
        Vec3 dir = aim.subtract(from).normalize();
        Metal m = new Metal(nextId++, SHARD_PIECE, p, from, dir.scale(MagnetoConfig.SHARD_SPEED.get()), dir, .25f, .015f);
        METAL.add(m);
        fx(p, FX_SHARD, from, m.vel, 0, p.getId(), m.id);
        sound(p, SoundEvents.TRIDENT_THROW, .5f, 1.7f);
        sound(p, SoundEvents.CHAIN_PLACE, .4f, 1.6f);
    }

    // ------------------------------------------------------------------ Q: Iron Barrage
    private static void barrage(ServerPlayer p, State s) {
        if (casting(s) || s.held != null) return;
        if (s.charges <= 0) { tell(p, "Demir Yağmuru: dolum " + String.format(Locale.ROOT, "%.1f", (MagnetoConfig.BARRAGE_RECHARGE.get() - s.recharge) / 20f) + " sn"); return; }
        s.charges--;
        s.barrageAt = aimPoint(p, MagnetoConfig.BARRAGE_RANGE.get());
        s.barrageLeft = MagnetoConfig.BARRAGE_RODS.get();
        s.barrageNext = BARRAGE_AT;
        s.barrageSeed = p.getRandom().nextInt(100000);
        if (!s.fist && s.action != CONTROL) set(s, BARRAGE);
        sound(p, SoundEvents.BEACON_ACTIVATE, .5f, 1.6f);
        sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE, .7f, .7f);
    }
    /** One rod: high over a spot near the target point, thrown down at a slant, turning as it falls. */
    private static void spawnRod(ServerPlayer p, State s, int index) {
        Random r = new Random(s.barrageSeed * 31L + index);
        double spread = MagnetoConfig.ROD_SPREAD.get(), h = MagnetoConfig.ROD_HEIGHT.get();
        double a = r.nextDouble() * Math.PI * 2, d = index == 0 ? 0 : spread * Math.sqrt(r.nextDouble());
        Vec3 land = s.barrageAt.add(Math.cos(a) * d, 0, Math.sin(a) * d);
        // The slant: it comes in from one side, as if hurled, not dropped.
        double sa = r.nextDouble() * Math.PI * 2, slant = h * (.18 + .22 * r.nextDouble());
        Vec3 start = land.add(Math.cos(sa) * slant, h, Math.sin(sa) * slant);
        // Driven down (a fast start) and still gathering speed under its own weight.
        double vy0 = .55, g = .12;
        double t = (-vy0 + Math.sqrt(vy0 * vy0 + 2 * g * h)) / g;
        Vec3 vel = new Vec3((land.x - start.x) / t, -vy0, (land.z - start.z) / t);
        Metal m = new Metal(nextId++, ROD, p, start, vel, vel.normalize(), 1.7f, (float) g);
        METAL.add(m);
        fx(p, FX_ROD, start, vel, (float) g, p.getId(), m.id);
        p.level().playSound(null, start.x, start.y, start.z, SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, .5f, .6f + .2f * r.nextFloat());
    }

    // ------------------------------------------------------------------ E: Metal Scrap Telekinesis
    private static void grab(ServerPlayer p, State s) {
        if (s.held != null) { release(p, s, false); return; }
        if (casting(s) || s.fist || !ready(p, s, CD_GRAB, "Hurda Telekinezisi")) return;
        LivingEntity t = aimed(p, MagnetoConfig.GRAB_RANGE.get(), .96);
        if (t == null) { tell(p, "Hurda Telekinezisi: bakışında hedef yok"); return; }
        s.held = t;
        s.holdAge = 0;
        s.holdTicks = MagnetoConfig.GRAB_TICKS.get();
        s.holdDist = Mth.clamp(t.getBoundingBox().getCenter().distanceTo(p.getEyePosition()), MagnetoConfig.HOLD_MIN.get(), MagnetoConfig.HOLD_MAX.get());
        s.lastPos = t.position();
        s.commanded = Vec3.ZERO;
        s.lastSlam = -100;
        set(s, GRAB);
        Vec3 dir = t.position().subtract(p.position());
        dir = new Vec3(dir.x, 0, dir.z).lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : new Vec3(dir.x, 0, dir.z).normalize();
        fx(p, FX_GRAB, t.position(), dir, MagnetoConfig.SCRAP_PIECES.get(), t.getId(), p.getRandom().nextInt(100000));
        sound(p, SoundEvents.CHAIN_BREAK, .8f, .6f);
        sound(p, SoundEvents.ARMOR_EQUIP_IRON, .8f, .6f);
        p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, .5f, 1.6f);
    }
    /** Holding them: wrapped first, then dragged where he aims, slammed into whatever is in the way. */
    private static void holdTick(ServerPlayer p, State s) {
        LivingEntity t = s.held;
        if (t == null) return;
        if (!t.isAlive() || t.isRemoved() || t.level() != p.level() || t.distanceTo(p) > MagnetoConfig.GRAB_RANGE.get() * 1.6) { release(p, s, false); return; }
        s.holdAge++;
        Vec3 centre = t.position().add(0, t.getBbHeight() * .5, 0);
        Vec3 moved = t.position().subtract(s.lastPos);
        double asked = s.commanded.length();
        // Slammed: it was told to move fast, but something stopped it.
        if (s.holdAge > SCRAP_FLY + 1 && asked > MagnetoConfig.SLAM_SPEED.get() && moved.length() < asked * .35 && s.holdAge - s.lastSlam > SLAM_GAP)
            slam(p, s, t, s.commanded, asked);
        s.lastPos = t.position();
        Vec3 v;
        if (s.holdAge <= SCRAP_FLY) {
            // The scrap flying in and closing round them: they are caught and lifted a little.
            v = new Vec3(0, .12 * (1 - s.holdAge / (double) SCRAP_FLY) + .02, 0);
        } else {
            if (s.action == GRAB && s.age >= GRAB_TICKS) set(s, CONTROL);
            Vec3 want = p.getEyePosition().add(p.getLookAngle().scale(s.holdDist));
            // Never below the ground: the aim can sweep them along it, slamming them, but not bury them.
            Vec3 to = want.subtract(centre);
            double max = MagnetoConfig.DRAG_SPEED.get();
            v = to.scale(.34);
            if (v.length() > max) v = v.normalize().scale(max);
            v = s.commanded.scale(.35).add(v.scale(.65));
        }
        s.commanded = v;
        t.setDeltaMovement(v.add(0, .08, 0));
        t.hurtMarked = true;
        t.fallDistance = 0;
        if (s.holdAge >= s.holdTicks) release(p, s, false);
    }
    private static void slam(ServerPlayer p, State s, LivingEntity t, Vec3 v, double speed) {
        s.lastSlam = s.holdAge;
        float damage = (float) (MagnetoConfig.SLAM_DAMAGE.get() * Mth.clamp(speed / .9, .4, 1.6));
        hurt(p, t, damage);
        Vec3 at = t.position().add(0, t.getBbHeight() * .5, 0);
        fx(p, FX_SLAM, at, v.normalize(), (float) Mth.clamp(speed, .2, 2), t.getId(), MagnetoConfig.SLAM_SHOCKWAVE.get() ? 1 : 0);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1f, .55f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, .45f, 1.3f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.PLAYERS, .8f, .7f);
        // A small shockwave: those right beside the slam are knocked back.
        if (MagnetoConfig.SLAM_SHOCKWAVE.get()) for (LivingEntity o : targets(p, new AABB(at, at).inflate(2.2))) {
            if (o == t) continue;
            Vec3 out = o.position().subtract(t.position());
            out = new Vec3(out.x, 0, out.z).lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(out.x, 0, out.z).normalize();
            o.push(out.x * .6, .25, out.z * .6);
            o.hurtMarked = true;
            hurt(p, o, damage * .3f);
        }
    }
    /** Left click while holding: they are hurled the way he looks. */
    private static void throwHeld(ServerPlayer p, State s) {
        LivingEntity t = s.held;
        Vec3 v = p.getLookAngle().scale(MagnetoConfig.THROW_SPEED.get());
        release(p, s, true);
        t.setDeltaMovement(v.add(0, .1, 0));
        t.hurtMarked = true;
        s.thrown = t;
        s.thrownAge = 0;
        s.commanded = v;
        s.lastPos = t.position();
        set(s, THROW);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .9f, .6f);
        sound(p, SoundEvents.TRIDENT_RIPTIDE_2, .7f, .8f);
    }
    /** Thrown: if they come to a sudden stop in the first second, that was a wall or the ground. */
    private static void thrownTick(ServerPlayer p, State s) {
        LivingEntity t = s.thrown;
        if (t == null) return;
        if (!t.isAlive() || ++s.thrownAge > 24) { s.thrown = null; return; }
        Vec3 moved = t.position().subtract(s.lastPos);
        double before = s.commanded.length();
        if (s.thrownAge > 1 && before > MagnetoConfig.SLAM_SPEED.get() && moved.length() < before * .3) {
            slam(p, s, t, s.commanded, before);
            s.thrown = null;
            return;
        }
        s.commanded = moved;
        s.lastPos = t.position();
    }
    private static void release(ServerPlayer p, State s, boolean thrown) {
        LivingEntity t = s.held;
        s.held = null;
        s.cooldowns[CD_GRAB] = MagnetoConfig.GRAB_COOLDOWN.get();
        if (s.action == GRAB || s.action == CONTROL) set(s, IDLE);
        if (t == null) return;
        if (!thrown) { t.setDeltaMovement(t.getDeltaMovement().multiply(.3, 0, .3).add(0, -.1, 0)); t.hurtMarked = true; }
        fx(p, FX_RELEASE, t.position().add(0, t.getBbHeight() * .5, 0), t.getDeltaMovement(), thrown ? 1 : 0, t.getId(), 0);
        p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, .7f, 1.2f);
    }

    // ------------------------------------------------------------------ R: Giant Iron Fist
    private static void fist(ServerPlayer p, State s) {
        if (s.fist) return;
        if (casting(s) || s.held != null || !ready(p, s, CD_FIST, "Dev Demir Yumruk")) return;
        s.fist = true;
        s.fistAge = 0;
        s.punchAge = -1;
        s.punchesLeft = MagnetoConfig.FIST_PUNCHES.get();
        s.fistPos = hover(p);
        s.fistVel = Vec3.ZERO;
        set(s, FIST_SUMMON);
        fx(p, FX_FIST_UP, s.fistPos, Vec3.ZERO, 1, p.getId(), 0);
        sound(p, SoundEvents.ANVIL_USE, .8f, .5f);
        p.level().playSound(null, s.fistPos.x, s.fistPos.y, s.fistPos.z, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1f, .5f);
    }
    /** Where the fist floats: over the ground where he aims. */
    private static Vec3 hover(ServerPlayer p) {
        return ground(p.serverLevel(), aimPoint(p, MagnetoConfig.FIST_RANGE.get())).add(0, MagnetoConfig.FIST_HEIGHT.get(), 0);
    }
    private static void fistTick(ServerPlayer p, State s) {
        if (!s.fist) return;
        s.fistAge++;
        if (s.action == FIST_SUMMON && s.age >= FIST_SUMMON_TICKS) set(s, FIST);
        if (s.punchAge >= 0) {
            int a = ++s.punchAge;
            Vec3 bottom = s.punchAt.add(0, 1.2, 0);
            if (a <= PUNCH_DOWN) {
                float k = a / (float) PUNCH_DOWN;
                s.fistPos = s.punchFrom.lerp(bottom, k * k);
                if (a == PUNCH_DOWN) impact(p, s);
            } else if (a <= PUNCH_DOWN + PUNCH_HOLD) {
                s.fistPos = bottom;
            } else if (a <= PUNCH_TICKS) {
                float k = (a - PUNCH_DOWN - PUNCH_HOLD) / (float) PUNCH_BACK;
                k = k * k * (3 - 2 * k);
                s.fistPos = bottom.lerp(hover(p), k);
            } else {
                s.punchAge = -1;
                s.fistVel = Vec3.ZERO;
                if (s.punchesLeft <= 0) { breakFist(p, s); return; }
            }
        } else {
            // Following the aim: a heavy spring, so it swings after the aim and settles.
            Vec3 want = hover(p);
            s.fistVel = s.fistVel.add(want.subtract(s.fistPos).scale(.18)).scale(.62);
            s.fistPos = s.fistPos.add(s.fistVel);
        }
        if (s.fistAge > MagnetoConfig.FIST_TIME.get() && s.punchAge < 0) breakFist(p, s);
    }
    private static void punch(ServerPlayer p, State s) {
        if (s.punchAge >= 0 || s.punchesLeft <= 0) return;
        s.punchAge = 0;
        s.punchesLeft--;
        s.punchFrom = s.fistPos;
        s.punchAt = ground(p.serverLevel(), s.fistPos.subtract(0, MagnetoConfig.FIST_HEIGHT.get() - .5, 0));
        p.level().playSound(null, s.fistPos.x, s.fistPos.y, s.fistPos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, .4f);
        p.level().playSound(null, s.fistPos.x, s.fistPos.y, s.fistPos.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, .4f, 1.8f);
    }
    /** The fist hits the ground: everyone round it hurt and thrown out, the ground shaken. */
    private static void impact(ServerPlayer p, State s) {
        Vec3 at = s.punchAt;
        double radius = MagnetoConfig.PUNCH_RADIUS.get(), knock = MagnetoConfig.PUNCH_KNOCK.get();
        float damage = f(MagnetoConfig.PUNCH_DAMAGE);
        for (LivingEntity t : targets(p, new AABB(at, at).inflate(radius, 3, radius))) {
            Vec3 to = t.position().subtract(at);
            double d = Math.sqrt(to.x * to.x + to.z * to.z);
            if (d > radius + t.getBbWidth() * .5) continue;
            double fall = 1 - .5 * Math.min(1, d / radius);
            hurt(p, t, (float) (damage * fall));
            Vec3 out = d < 1e-3 ? Vec3.directionFromRotation(0, p.getRandom().nextFloat() * 360) : new Vec3(to.x / d, 0, to.z / d);
            t.setDeltaMovement(out.x * knock * fall, .35 + .25 * fall, out.z * knock * fall);
            t.hurtMarked = true;
        }
        fx(p, FX_PUNCH, at, Vec3.ZERO, (float) radius, p.getId(), s.punchesLeft);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, .45f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, .9f, .7f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1f, .5f);
    }
    private static void breakFist(ServerPlayer p, State s) {
        s.fist = false;
        s.punchAge = -1;
        s.cooldowns[CD_FIST] = MagnetoConfig.FIST_COOLDOWN.get();
        if (s.action == FIST || s.action == FIST_SUMMON) set(s, IDLE);
        fx(p, FX_FIST_BREAK, s.fistPos, s.fistVel, 1, p.getId(), p.getRandom().nextInt(100000));
        p.level().playSound(null, s.fistPos.x, s.fistPos.y, s.fistPos.z, SoundEvents.ANVIL_DESTROY, SoundSource.PLAYERS, .9f, .6f);
        p.level().playSound(null, s.fistPos.x, s.fistPos.y, s.fistPos.z, SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1f, .5f);
    }

    // ------------------------------------------------------------------ F: Magnetic Iron Shield
    private static void shield(ServerPlayer p, State s) {
        if (s.shield) { burst(p, s); return; }
        if (casting(s) || !ready(p, s, CD_SHIELD, "Manyetik Demir Kalkan")) return;
        s.shield = true;
        s.shieldAge = 0;
        if (s.action == IDLE || s.action == SHARD) set(s, SHIELD_RAISE);
        fx(p, FX_COLUMNS, p.position(), Vec3.ZERO, (float) MagnetoConfig.SHIELD_RADIUS.get().doubleValue(), p.getId(), MagnetoConfig.SHIELD_COLUMNS.get());
        sound(p, SoundEvents.IRON_GOLEM_STEP, 1f, .5f);
        sound(p, SoundEvents.ANVIL_PLACE, .7f, .5f);
        sound(p, SoundEvents.PISTON_EXTEND, .8f, .5f);
    }
    /** Where a column stands (round him, turning slowly). */
    private static Vec3 column(ServerPlayer p, State s, int i, int n) {
        double a = Math.PI * 2 * i / n + s.shieldAge * .004;
        double r = MagnetoConfig.SHIELD_RADIUS.get();
        return p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
    }
    private static void shieldTick(ServerPlayer p, State s) {
        if (!s.shield) return;
        s.shieldAge++;
        if (s.action == SHIELD_RAISE && s.age >= SHIELD_RAISE_TICKS) set(s, SHIELD);
        if (s.shieldAge >= MagnetoConfig.SHIELD_MAX.get()) { burst(p, s); return; }
        if (!MagnetoConfig.SHIELD_BLOCKS_PROJECTILES.get() || s.shieldAge < SHIELD_RAISE_TICKS / 2) return;
        // Projectiles coming at him stop dead against the iron.
        double r = MagnetoConfig.SHIELD_RADIUS.get();
        Vec3 centre = p.position().add(0, 1, 0);
        for (Projectile pr : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 2.5, 2, r + 2.5), pr -> pr.getOwner() != p && pr.isAlive())) {
            Vec3 to = centre.subtract(pr.position());
            double flat = Math.sqrt(to.x * to.x + to.z * to.z);
            if (flat > r + .8 || flat < r - 1.2 || pr.getDeltaMovement().dot(to) <= 0) continue;
            fx(p, FX_BLOCK, pr.position(), pr.getDeltaMovement().normalize(), 1, p.getId(), 0);
            p.level().playSound(null, pr.getX(), pr.getY(), pr.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .4f, 1.8f);
            pr.discard();
        }
    }
    /** Again: the columns torn apart into pieces flung out in every direction. */
    private static void burst(ServerPlayer p, State s) {
        s.shield = false;
        s.cooldowns[CD_SHIELD] = MagnetoConfig.SHIELD_COOLDOWN.get();
        set(s, BURST);
        int n = MagnetoConfig.SHIELD_COLUMNS.get(), pieces = MagnetoConfig.BURST_PIECES.get();
        double speed = MagnetoConfig.BURST_SPEED.get();
        Random r = new Random(p.getRandom().nextLong());
        for (int i = 0; i < pieces; i++) {
            int col = i % n;
            Vec3 base = column(p, s, col, n);
            Vec3 out = base.subtract(p.position());
            out = new Vec3(out.x, 0, out.z).normalize();
            // Spread round the column's own direction, mostly level, some climbing.
            double turn = (r.nextDouble() - .5) * (Math.PI * 2 / n) * 1.2;
            out = new Vec3(out.x * Math.cos(turn) - out.z * Math.sin(turn), 0, out.x * Math.sin(turn) + out.z * Math.cos(turn));
            Vec3 vel = out.scale(speed * (.8 + .4 * r.nextDouble())).add(0, .05 + .25 * r.nextDouble(), 0);
            Vec3 pos = base.add(0, .4 + 2.6 * r.nextDouble(), 0);
            Metal m = new Metal(nextId++, BURST_PIECE, p, pos, vel, vel.normalize(), .35f, .035f);
            METAL.add(m);
            fx(p, FX_PIECE, pos, vel, .035f, p.getId(), m.id);
        }
        fx(p, FX_BURST, p.position(), Vec3.ZERO, (float) MagnetoConfig.SHIELD_RADIUS.get().doubleValue(), p.getId(), n);
        sound(p, SoundEvents.GENERIC_EXPLODE, 1f, .8f);
        sound(p, SoundEvents.ANVIL_DESTROY, 1f, .6f);
        sound(p, SoundEvents.CHAIN_BREAK, 1f, .5f);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, .4f, 1.6f);
    }

    // ------------------------------------------------------------------ X: Magnetic Execution
    private static void ultimate(ServerPlayer p, State s) {
        if (casting(s) || !ready(p, s, CD_ULT, "Manyetik İnfaz")) return;
        LivingEntity target = aimed(p, MagnetoConfig.ULT_RANGE.get(), .9);
        if (target == null) { tell(p, "Manyetik İnfaz: önünde hedef yok (birine bakarak bas)"); return; }
        if (!MagnetoUltSession.start(p, target)) return;
        s.cooldowns[CD_ULT] = MagnetoConfig.ULT_COOLDOWN.get();
        if (s.held != null) release(p, s, false);
        if (s.fist) breakFist(p, s);
        set(s, IDLE);
    }

    // ------------------------------------------------------------------ the metal in the air
    private static void tickMetal() {
        if (METAL.isEmpty()) return;
        for (Iterator<Metal> it = METAL.iterator(); it.hasNext(); ) {
            Metal m = it.next();
            m.age++;
            ServerPlayer p = m.owner;
            if (p == null || p.isRemoved()) { it.remove(); continue; }
            if (m.stuck) {
                int stay = m.kind == ROD ? ROD_STAY + ROD_SINK : SHARD_STAY + SHARD_FADE;
                if (m.age > stay) it.remove();
                continue;
            }
            if (m.age > (m.kind == ROD ? 80 : 60)) { it.remove(); continue; }
            Vec3 vel = m.vel.add(0, -m.gravity, 0);
            Vec3 tip = m.pos.add(m.axis.scale(m.half)), next = m.pos.add(vel), nextTip = next.add(vel.normalize().scale(m.half));
            // What it passes through first: a body (shards and pieces), then the ground or a wall.
            if (m.kind != ROD) {
                AABB sweep = new AABB(tip, nextTip).inflate(.4);
                EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, tip, nextTip, sweep,
                        e -> e instanceof LivingEntity l && targetable(p, l) && !m.hit.contains(e.getId()));
                if (eh != null && eh.getEntity() instanceof LivingEntity t) {
                    m.hit.add(t.getId());
                    hurt(p, t, m.kind == SHARD_PIECE ? f(MagnetoConfig.SHARD_DAMAGE) : f(MagnetoConfig.BURST_DAMAGE));
                    Vec3 push = vel.normalize();
                    t.push(push.x * .4, .12, push.z * .4);
                    t.hurtMarked = true;
                    fx(p, m.kind == SHARD_PIECE ? FX_SHARD_HIT : FX_PIECE_HIT, eh.getLocation(), push, -1, t.getId(), m.id);
                    p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .7f, 1.2f);
                    it.remove();
                    continue;
                }
            }
            BlockHitResult bh = p.level().clip(new ClipContext(tip, nextTip, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (bh.getType() != HitResult.Type.MISS) {
                Vec3 dir = vel.normalize();
                // Driven in: the tip buried well into the ground (a rod), a little way (a shard or a piece).
                double sink = m.kind == ROD ? 1.0 : .25;
                m.pos = bh.getLocation().subtract(dir.scale(m.half - sink));
                m.axis = dir;
                m.vel = Vec3.ZERO;
                m.stuck = true;
                m.age = 0;
                if (m.kind == ROD) rodHit(p, m, bh.getLocation());
                else {
                    fx(p, m.kind == SHARD_PIECE ? FX_SHARD_HIT : FX_PIECE_HIT, m.pos, dir, 1, -1, m.id);
                    if (m.id % 3 == 0) p.level().playSound(null, m.pos.x, m.pos.y, m.pos.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, .5f, 1.3f);
                }
                continue;
            }
            m.pos = next;
            m.vel = vel;
            if (m.kind == ROD) m.axis = vel.normalize();
        }
    }
    /** A rod strikes: a burst of earth and sparks, everyone near it hurt and thrown back. The ground is not broken. */
    private static void rodHit(ServerPlayer p, Metal m, Vec3 at) {
        double radius = MagnetoConfig.ROD_RADIUS.get();
        float damage = f(MagnetoConfig.ROD_DAMAGE);
        for (LivingEntity t : targets(p, new AABB(at, at).inflate(radius, 2.5, radius))) {
            Vec3 to = t.position().subtract(at);
            double d = Math.sqrt(to.x * to.x + to.z * to.z);
            if (d > radius + t.getBbWidth() * .5) continue;
            double fall = 1 - .55 * Math.min(1, d / radius);
            hurt(p, t, (float) (damage * fall));
            Vec3 out = d < 1e-3 ? new Vec3(1, 0, 0) : new Vec3(to.x / d, 0, to.z / d);
            t.push(out.x * .5 * fall, .3 * fall, out.z * .5 * fall);
            t.hurtMarked = true;
        }
        fx(p, FX_ROD_HIT, m.pos, m.axis, (float) radius, -1, m.id);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1f, .6f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, .5f, 1.5f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1f, .5f);
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!isHero(p)) {
            State gone = STATES.remove(p.getUUID());
            if (gone != null) {
                if (gone.grantedFly) { p.getAbilities().mayfly = false; p.getAbilities().flying = false; p.onUpdateAbilities(); }
                send(p, new State());
            }
            return;
        }
        State s = state(p);
        s.age++;
        for (int i = 0; i < s.cooldowns.length; i++) if (s.cooldowns[i] > 0) s.cooldowns[i]--;
        int max = MagnetoConfig.BARRAGE_CHARGES.get();
        if (s.charges < 0 || s.charges > max) s.charges = max;
        if (s.charges < max && ++s.recharge >= MagnetoConfig.BARRAGE_RECHARGE.get()) { s.charges++; s.recharge = 0; }
        if (s.charges >= max) s.recharge = 0;
        // Flight: the fall forgiven while he floats; set down when he has stood on the ground a moment.
        if (s.flying) {
            s.flightAge++;
            p.fallDistance = 0;
            s.groundTicks = p.onGround() ? s.groundTicks + 1 : 0;
            if (s.groundTicks > 8 && s.flightAge > 20) flight(p, s, false);
        }
        // The barrage's rods, one after another.
        if (s.barrageLeft > 0 && --s.barrageNext <= 0) {
            spawnRod(p, s, MagnetoConfig.BARRAGE_RODS.get() - s.barrageLeft);
            s.barrageLeft--;
            s.barrageNext = Math.max(1, MagnetoConfig.BARRAGE_GAP.get());
        }
        holdTick(p, s);
        thrownTick(p, s);
        fistTick(p, s);
        shieldTick(p, s);
        int len = length(s.action);
        if (len > 0 && s.age >= len && s.action != GRAB && s.action != FIST_SUMMON && s.action != SHIELD_RAISE)
            set(s, s.held != null ? CONTROL : s.fist ? FIST : s.shield ? SHIELD : IDLE);
        if (s.action == SHIELD && !s.shield) set(s, IDLE);
        send(p, s);
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) { if (e.phase == TickEvent.Phase.END) tickMetal(); }

    // ------------------------------------------------------------------ being hit
    /** The shield's columns take blows from outside them: projectiles stop, the rest is softened (see hurt). */
    @SubscribeEvent public static void attacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        if (FilmSessions.playing(p.getUUID(), MagnetoUltSession.ID)) { e.setCanceled(true); return; }
        State s = STATES.get(p.getUUID());
        if (s == null || !s.shield || s.shieldAge < SHIELD_RAISE_TICKS / 2) return;
        Entity direct = e.getSource().getDirectEntity();
        if (direct instanceof Projectile && MagnetoConfig.SHIELD_BLOCKS_PROJECTILES.get()) {
            e.setCanceled(true);
            fx(p, FX_BLOCK, direct.position(), direct.getDeltaMovement().normalize(), 1, p.getId(), 0);
        }
    }
    @SubscribeEvent public static void hurtEvent(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null || !s.shield || s.shieldAge < SHIELD_RAISE_TICKS / 2) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_ARMOR) || e.getSource().is(DamageTypeTags.IS_FALL)) return;
        Entity from = e.getSource().getDirectEntity() != null ? e.getSource().getDirectEntity() : e.getSource().getEntity();
        if (from == null) return;
        e.setAmount((float) (e.getAmount() * (1 - MagnetoConfig.SHIELD_REDUCTION.get())));
        Vec3 to = from.position().subtract(p.position());
        Vec3 dir = new Vec3(to.x, 0, to.z).lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : new Vec3(to.x, 0, to.z).normalize();
        fx(p, FX_BLOCK, p.position().add(dir.scale(MagnetoConfig.SHIELD_RADIUS.get())).add(0, 1.2, 0), dir.scale(-1), 1, p.getId(), 0);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, .4f, 1.6f);
    }
    /** He floats down: falls barely hurt him. */
    @SubscribeEvent public static void fall(LivingFallEvent e) {
        if (!isHero(e.getEntity())) return;
        e.setDistance(Math.max(0, e.getDistance() - 8));
        e.setDamageMultiplier(e.getDamageMultiplier() * .4f);
    }
    /** His left click is his own; the vanilla punch would hit twice. */
    @SubscribeEvent public static void plainAttack(AttackEntityEvent e) { if (isHero(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        State s = STATES.remove(e.getEntity().getUUID());
        if (s != null && s.grantedFly && e.getEntity() instanceof ServerPlayer p) { p.getAbilities().mayfly = false; p.getAbilities().flying = false; }
        METAL.removeIf(m -> m.owner == e.getEntity());
    }

    // ------------------------------------------------------------------ helpers
    private static boolean targetable(ServerPlayer p, LivingEntity t) {
        return t != p && t.isAlive() && !t.isSpectator() && (MagnetoConfig.FRIENDLY_FIRE.get() || !(t instanceof Player other && p.isAlliedTo(other)));
    }
    private static List<LivingEntity> targets(ServerPlayer p, AABB area) {
        return p.level().getEntitiesOfClass(LivingEntity.class, area, t -> targetable(p, t));
    }
    private static void hurt(ServerPlayer p, LivingEntity t, float damage) {
        if (damage <= 0) return;
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), damage);
    }
    /** Where he aims: the block or body his view meets, or the point at range along it. */
    private static Vec3 aimPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, eye, stop, new AABB(eye, stop).inflate(1), e -> e instanceof LivingEntity l && targetable(p, l));
        if (eh != null) return eh.getEntity().position();
        return stop;
    }
    /** Who he is looking at (the nearest living thing close to his line of sight, in sight, within range), or null. */
    private static LivingEntity aimed(ServerPlayer p, double range, double cone) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity t : targets(p, p.getBoundingBox().inflate(range))) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range || d < .01) continue;
            double along = to.normalize().dot(look);
            if (along < cone) continue;
            if (p.level().clip(new ClipContext(eye, t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            double score = along * 2 - d / range;
            if (score > bestScore) { bestScore = score; best = t; }
        }
        return best;
    }
    /** The ground under (or at) a point: the top of the first solid block within a few blocks down. */
    private static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos base = BlockPos.containing(at);
        for (int dy = 2; dy >= -24; dy--) {
            BlockPos pos = base.above(dy);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty())
                return new Vec3(at.x, pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), at.z);
        }
        return new Vec3(at.x, at.y - 2, at.z);
    }
    /** About where his hand is (side 0 right), for things leaving it. */
    private static Vec3 hand(ServerPlayer p, int side) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(side == 0 ? .38 : -.38);
        return p.getEyePosition().add(0, -.35, 0).add(right).add(p.getLookAngle().scale(.6));
    }

    // ------------------------------------------------------------------ sync
    private static void send(ServerPlayer p, State s) {
        int flags = (s.flying ? MagnetoStatePacket.FLYING : 0) | (s.shield ? MagnetoStatePacket.SHIELD : 0) | (s.fist ? MagnetoStatePacket.FIST : 0)
                | (s.held != null ? MagnetoStatePacket.HOLDING : 0);
        float recharge = s.charges >= MagnetoConfig.BARRAGE_CHARGES.get() ? 0 : s.recharge / (float) MagnetoConfig.BARRAGE_RECHARGE.get();
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new MagnetoStatePacket(p.getId(), s.action, s.age, flags, s.cooldowns.clone(), Math.max(0, s.charges), recharge, s.flightAge,
                        s.held == null ? -1 : s.held.getId(), s.holdAge, s.holdTicks, s.fistPos, s.fistAge, s.punchAge, s.punchesLeft, s.shieldAge));
    }
    static void fx(ServerPlayer p, int kind, Vec3 pos, Vec3 dir, float power, int entity, int id) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new MagnetoFxPacket(kind, pos, dir, power, entity, id));
    }
    private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
