package com.FIRNI.superheromod.heroes.zed;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ZedFxPacket;
import com.FIRNI.superheromod.network.packet.ZedStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

import static com.FIRNI.superheromod.heroes.zed.ZedAction.*;

/**
 * Zed on the server: decides everything (hits, damage, cooldowns, where the shadows stand, the mark).
 * His W shadow is a second fighter: it throws its own shuriken when he throws his and cuts its own
 * ring when he cuts his. The R shadow is separate: it only waits where he started the Death Mark for
 * him to come back. Clients animate from the synced state and draw the effects they are told about.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class ZedController {
    private static final class Shadow {
        boolean alive; Vec3 pos = Vec3.ZERO, from = Vec3.ZERO; float yaw; int age, left, action = IDLE, actionAge;
    }
    private static final class Shuriken {
        Vec3 pos, dir; double travelled; final boolean fromShadow; final int cast; final Set<Integer> hits = new HashSet<>();
        Shuriken(Vec3 pos, Vec3 dir, boolean fromShadow, int cast) { this.pos = pos; this.dir = dir; this.fromShadow = fromShadow; this.cast = cast; }
    }
    private static final class State {
        int action = IDLE, age, side;
        final int[] cooldowns = new int[ZedStatePacket.COOLDOWNS];
        final Shadow w = new Shadow(), r = new Shadow();
        final List<Shuriken> stars = new ArrayList<>();
        /** Who each Q cast has already hit (a second shuriken on the same body does less). */
        final Map<Integer, Set<Integer>> castHits = new HashMap<>();
        int casts;
        final Map<Integer, Long> passiveReady = new HashMap<>();
        int markTarget = -1, markLeft; float markStored;
        LivingEntity dashTarget; Vec3 dashFrom = Vec3.ZERO, dashTo = Vec3.ZERO;
    }
    private static final Map<UUID, State> STATES = new HashMap<>();

    private ZedController() {}

    public static boolean isHero(Entity e) { return e instanceof ServerPlayer && ID.equals(AbilityManager.getCharacterId(e.getUUID())); }
    private static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), id -> new State()); }
    private static void set(State s, int action) { s.action = action; s.age = 0; }
    private static boolean busy(State s) {
        return s.action == MARK_LOCK || s.action == MARK_DASH || s.action == SWAP || s.action == MARK_RETURN;
    }
    private static void tell(ServerPlayer p, String text) { p.displayClientMessage(Component.literal("§c" + text), true); }
    private static boolean ready(ServerPlayer p, State s, int slot, String name) {
        if (s.cooldowns[slot] <= 0) return true;
        tell(p, name + ": " + String.format(Locale.ROOT, "%.1f", s.cooldowns[slot] / 20f) + " sn");
        return false;
    }

    // ------------------------------------------------------------------ keys
    public static void press(ServerPlayer p, AbilitySlot slot) {
        if (!isHero(p)) return;
        State s = state(p);
        switch (slot) {
            case LMB -> slash(p, s);
            case ULTIMATE -> shuriken(p, s);
            case SKILL_F -> shadow(p, s);
            case SKILL_V -> spin(p, s);
            case SKILL_E -> deathMark(p, s);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ left click: quick slashes and the passive
    private static void slash(ServerPlayer p, State s) {
        if (busy(s)) return;
        if ((s.action == SLASH_RIGHT || s.action == SLASH_LEFT) && s.age < SLASH_TICKS - 2) return;
        s.side = 1 - s.side;
        set(s, s.side == 0 ? SLASH_RIGHT : SLASH_LEFT);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .6f, 1.5f);
    }
    private static void slashHit(ServerPlayer p, State s) {
        Vec3 look = p.getLookAngle(), eye = p.getEyePosition();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity t : targets(p, p.getBoundingBox().inflate(SLASH_REACH + 1))) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > SLASH_REACH + t.getBbWidth() * .5 || to.normalize().dot(look) < .55) continue;
            if (d < bestDist) { bestDist = d; best = t; }
        }
        if (best == null) return;
        hurt(p, best, SLASH_DAMAGE);
        Vec3 at = best.getBoundingBox().getCenter();
        fx(p, FX_SLASH_HIT, at, look, s.side == 0 ? 1 : -1, best.getId());
        sound(p, SoundEvents.PLAYER_ATTACK_STRONG, .8f, 1.4f);
        // Contempt for the Weak: the wounded are cut deeper (once in a while per target).
        long now = p.level().getGameTime();
        if (best.isAlive() && best.getHealth() < best.getMaxHealth() * PASSIVE_BELOW && s.passiveReady.getOrDefault(best.getId(), 0L) <= now) {
            s.passiveReady.put(best.getId(), now + PASSIVE_COOLDOWN);
            best.invulnerableTime = 0;
            best.hurt(p.damageSources().indirectMagic(p, p), best.getMaxHealth() * PASSIVE_SHARE);
            fx(p, FX_PASSIVE, at, look, 1, best.getId());
            p.level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1f, .6f);
        }
    }

    // ------------------------------------------------------------------ Q: Razor Shuriken
    private static void shuriken(ServerPlayer p, State s) {
        if (busy(s) || !ready(p, s, CD_Q, "Shuriken")) return;
        s.cooldowns[CD_Q] = Q_COOLDOWN;
        set(s, THROW);
        if (s.w.alive) { s.w.action = THROW; s.w.actionAge = 0; }
        sound(p, SoundEvents.TRIDENT_THROW, .7f, 1.6f);
    }
    /** Where both shurikens are aimed: the first body along his look, or the point at full range. */
    private static Vec3 aimPoint(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(SHURIKEN_RANGE));
        var block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        LivingEntity best = null;
        double bestAlong = Double.MAX_VALUE;
        Vec3 look = p.getLookAngle();
        for (LivingEntity t : targets(p, new AABB(eye, end).inflate(1.5))) {
            Vec3 to = t.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(look);
            if (along < 0 || along > SHURIKEN_RANGE || to.subtract(look.scale(along)).length() > 1.2 + t.getBbWidth() * .5) continue;
            if (along < bestAlong) { bestAlong = along; best = t; }
        }
        return best != null ? best.getBoundingBox().getCenter() : end;
    }
    private static void release(ServerPlayer p, State s) {
        Vec3 aim = aimPoint(p);
        int cast = ++s.casts;
        s.castHits.put(cast, new HashSet<>());
        Vec3 hand = p.getEyePosition().add(0, -.25, 0).add(p.getLookAngle().scale(.6));
        Vec3 dir = aim.subtract(hand).normalize();
        s.stars.add(new Shuriken(hand, dir, false, cast));
        fx(p, FX_SHURIKEN, hand, dir, 0, -1);
        if (s.w.alive) {
            // The shadow throws its own, from where it stands, at the same mark: two angles on one target.
            Vec3 shand = s.w.pos.add(0, 1.4, 0);
            Vec3 sdir = aim.subtract(shand).normalize();
            s.w.yaw = (float) Math.toDegrees(Math.atan2(-sdir.x, sdir.z));
            s.stars.add(new Shuriken(shand, sdir, true, cast));
            fx(p, FX_SHURIKEN, shand, sdir, 1, -1);
        }
    }
    private static void tickStars(ServerPlayer p, State s) {
        for (Iterator<Shuriken> it = s.stars.iterator(); it.hasNext(); ) {
            Shuriken k = it.next();
            Vec3 next = k.pos.add(k.dir.scale(SHURIKEN_SPEED));
            var block = p.level().clip(new ClipContext(k.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            boolean wall = block.getType() != HitResult.Type.MISS;
            if (wall) next = block.getLocation();
            Set<Integer> already = s.castHits.getOrDefault(k.cast, new HashSet<>());
            for (LivingEntity t : targets(p, new AABB(k.pos, next).inflate(SHURIKEN_RADIUS))) {
                if (k.hits.contains(t.getId())) continue;
                if (t.getBoundingBox().inflate(SHURIKEN_RADIUS).clip(k.pos, next).isEmpty() && !t.getBoundingBox().inflate(SHURIKEN_RADIUS).contains(k.pos)) continue;
                // Full damage on the first body, less on those behind it, half from the second shuriken of the same throw.
                float damage = SHURIKEN_DAMAGE * (k.hits.isEmpty() ? 1 : SHURIKEN_PIERCED) * (already.contains(t.getId()) ? SHURIKEN_SECOND : 1);
                k.hits.add(t.getId());
                already.add(t.getId());
                hurt(p, t, damage);
                fx(p, FX_SHURIKEN_HIT, t.getBoundingBox().getCenter(), k.dir, k.fromShadow ? 1 : 0, t.getId());
                p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, .9f, 1.5f);
            }
            k.travelled += k.pos.distanceTo(next);
            k.pos = next;
            if (wall || k.travelled >= SHURIKEN_RANGE) {
                if (wall) fx(p, FX_SHURIKEN_HIT, next, k.dir, k.fromShadow ? 1 : 0, -1);
                it.remove();
            }
        }
        if (s.stars.isEmpty()) s.castHits.clear();
    }

    // ------------------------------------------------------------------ W (on F): Living Shadow
    private static void shadow(ServerPlayer p, State s) {
        if (busy(s)) return;
        if (s.w.alive) { if (s.w.age >= SHADOW_TRAVEL) swap(p, s, s.w, false); return; }
        if (!ready(p, s, CD_W, "Canlı Gölge")) return;
        s.cooldowns[CD_W] = W_COOLDOWN;
        Vec3 flat = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 from = p.position(), start = from.add(0, 1, 0);
        // As far as he sends it, stopping short of walls, standing on the ground there.
        var hit = p.level().clip(new ClipContext(start, start.add(flat.scale(SHADOW_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = hit.getType() == HitResult.Type.MISS ? SHADOW_REACH : Math.max(0, hit.getLocation().distanceTo(start) - .6);
        Vec3 to = ground(p.serverLevel(), from.add(flat.scale(reach)));
        s.w.alive = true; s.w.from = from; s.w.pos = from; s.w.age = 0; s.w.left = SHADOW_LIFE; s.w.yaw = p.getYRot(); s.w.action = IDLE; s.w.actionAge = 0;
        s.w.pos = to;
        set(s, SHADOW_CAST);
        fx(p, FX_SHADOW_CAST, from, to.subtract(from), 1, -1);
        sound(p, SoundEvents.ENDERMAN_TELEPORT, .5f, .5f);
        sound(p, SoundEvents.WITHER_SHOOT, .25f, 1.8f);
    }
    /** Zed and a shadow trade places in a blink. */
    private static void swap(ServerPlayer p, State s, Shadow sh, boolean returning) {
        Vec3 here = p.position(), there = sh.pos;
        if (!p.level().noCollision(p, p.getBoundingBox().move(there.subtract(here)))) { tell(p, "Gölgenin yeri kapalı"); return; }
        float yaw = p.getYRot();
        p.connection.teleport(there.x, there.y, there.z, yaw, p.getXRot());
        p.fallDistance = 0;
        fx(p, returning ? FX_RETURN : FX_SWAP, here, there.subtract(here), 1, -1);
        sound(p, SoundEvents.ENDERMAN_TELEPORT, .8f, .7f);
        sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, .8f, .8f);
        if (returning) sh.alive = false;
        else { sh.pos = here; sh.yaw = yaw; }
        set(s, returning ? MARK_RETURN : SWAP);
    }

    // ------------------------------------------------------------------ E: Shadow Slash
    private static void spin(ServerPlayer p, State s) {
        if (busy(s) || !ready(p, s, CD_E, "Gölge Darbesi")) return;
        s.cooldowns[CD_E] = E_COOLDOWN;
        set(s, SPIN);
        if (s.w.alive) { s.w.action = SPIN; s.w.actionAge = 0; }
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .9f);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .7f, 1.4f);
    }
    private static void spinHit(ServerPlayer p, State s) {
        Set<Integer> hit = new HashSet<>();
        boolean champion = false;
        Vec3 c = p.position().add(0, 1, 0);
        fx(p, FX_SPIN, p.position(), Vec3.ZERO, 1, -1);
        for (LivingEntity t : targets(p, new AABB(c, c).inflate(SPIN_RADIUS, 2, SPIN_RADIUS))) {
            if (t.position().distanceTo(p.position()) > SPIN_RADIUS + t.getBbWidth() * .5) continue;
            hit.add(t.getId());
            hurt(p, t, SPIN_DAMAGE);
            fx(p, FX_SLASH_HIT, t.getBoundingBox().getCenter(), t.position().subtract(p.position()), 1, t.getId());
            champion |= t instanceof Player;
        }
        if (s.w.alive) {
            // The shadow's ring: it cuts those near it too (once each per cast), and slows everyone it touches.
            fx(p, FX_SPIN, s.w.pos, Vec3.ZERO, .6f, -1);
            Vec3 sc = s.w.pos.add(0, 1, 0);
            for (LivingEntity t : targets(p, new AABB(sc, sc).inflate(SPIN_RADIUS, 2, SPIN_RADIUS))) {
                if (t.position().distanceTo(s.w.pos) > SPIN_RADIUS + t.getBbWidth() * .5) continue;
                if (hit.add(t.getId())) {
                    hurt(p, t, SPIN_DAMAGE);
                    fx(p, FX_SLASH_HIT, t.getBoundingBox().getCenter(), t.position().subtract(s.w.pos), .6f, t.getId());
                    champion |= t instanceof Player;
                }
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, E_SLOW, 1));
            }
        }
        if (champion) s.cooldowns[CD_W] = Math.max(0, s.cooldowns[CD_W] - E_REFUND);
        sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1f, .9f);
    }

    // ------------------------------------------------------------------ R: Death Mark
    private static void deathMark(ServerPlayer p, State s) {
        if (busy(s)) return;
        if (s.r.alive) { swap(p, s, s.r, true); return; }
        if (!ready(p, s, CD_R, "Ölüm İşareti")) return;
        LivingEntity target = findTarget(p);
        if (target == null) { tell(p, "Ölüm İşareti: menzilde hedef yok"); return; }
        s.cooldowns[CD_R] = R_COOLDOWN;
        s.dashTarget = target;
        s.dashFrom = p.position();
        set(s, MARK_LOCK);
        fx(p, FX_MARK_LOCK, target.position(), Vec3.ZERO, 1, target.getId());
        sound(p, SoundEvents.WARDEN_HEARTBEAT, 1f, 1.4f);
        sound(p, SoundEvents.ENDERMAN_STARE, .4f, 1.6f);
    }
    private static LivingEntity findTarget(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = -1;
        for (LivingEntity e : targets(p, new AABB(eye, eye).inflate(R_RANGE))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length(), dot = to.normalize().dot(look);
            if (d > R_RANGE || dot < .8) continue;
            if (p.level().clip(new ClipContext(eye, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            double score = dot * 2 - d / R_RANGE;
            if (score > bestScore) { bestScore = score; best = e; }
        }
        return best;
    }
    /** Behind the target (or beside, if behind is blocked), facing them. */
    private static Vec3 behind(ServerPlayer p, LivingEntity t) {
        Vec3 away = t.position().subtract(p.position());
        away = new Vec3(away.x, 0, away.z).lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : new Vec3(away.x, 0, away.z).normalize();
        Vec3 side = away.cross(new Vec3(0, 1, 0));
        double gap = t.getBbWidth() * .5 + .9;
        for (Vec3 off : new Vec3[]{away.scale(gap), side.scale(gap), side.scale(-gap), away.scale(-gap)}) {
            Vec3 at = t.position().add(off);
            if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) return at;
        }
        return t.position();
    }
    private static void strike(ServerPlayer p, State s) {
        LivingEntity t = s.dashTarget;
        if (t == null || !t.isAlive()) { set(s, IDLE); return; }
        Vec3 to = t.position().subtract(s.dashTo);
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        p.connection.teleport(s.dashTo.x, s.dashTo.y, s.dashTo.z, yaw, 0);
        p.fallDistance = 0;
        // The R shadow waits where he started.
        s.r.alive = true; s.r.pos = s.dashFrom; s.r.yaw = yaw + 180; s.r.left = R_SHADOW_LIFE; s.r.age = 0;
        s.markTarget = t.getId(); s.markLeft = MARK_LIFE; s.markStored = 0;
        hurt(p, t, MARK_HIT);
        fx(p, FX_MARK_APPLY, t.position().add(0, t.getBbHeight() * .55, 0), Vec3.ZERO, 1, t.getId());
        sound(p, SoundEvents.TRIDENT_RIPTIDE_1, .9f, 1.5f);
        sound(p, SoundEvents.WITHER_SHOOT, .3f, 1.9f);
        set(s, MARK_STRIKE);
    }
    private static void tickMark(ServerPlayer p, State s) {
        if (s.markTarget < 0) return;
        Entity e = p.level().getEntity(s.markTarget);
        if (!(e instanceof LivingEntity t) || !t.isAlive()) { s.markTarget = -1; return; }
        if (s.markLeft == 12) p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2f, .8f);
        if (--s.markLeft > 0) return;
        // The seal triggers: part of everything he did to them while it was on them, all at once.
        float damage = 4 + s.markStored * MARK_SHARE;
        s.markTarget = -1;
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().indirectMagic(p, p), damage);
        Vec3 at = t.position().add(0, t.getBbHeight() * .55, 0);
        fx(p, FX_MARK_POP, at, Vec3.ZERO, Math.min(1, damage / 20f), t.getId());
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, .6f, 1.6f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, .5f, 1.8f);
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!isHero(p)) {
            if (STATES.remove(p.getUUID()) != null) send(p, new State());
            return;
        }
        State s = state(p);
        s.age++;
        for (int i = 0; i < s.cooldowns.length; i++) if (s.cooldowns[i] > 0) s.cooldowns[i]--;
        switch (s.action) {
            case SLASH_RIGHT, SLASH_LEFT -> { if (s.age == SLASH_HIT) slashHit(p, s); if (s.age >= SLASH_TICKS) set(s, IDLE); }
            case THROW -> { if (s.age == THROW_RELEASE) release(p, s); if (s.age >= THROW_TICKS) set(s, IDLE); }
            case SHADOW_CAST -> { if (s.age >= CAST_TICKS) set(s, IDLE); }
            case SWAP, MARK_RETURN -> { if (s.age >= SWAP_TICKS) set(s, IDLE); }
            case SPIN -> { if (s.age == SPIN_HIT) spinHit(p, s); if (s.age >= SPIN_TICKS) set(s, IDLE); }
            case MARK_LOCK -> {
                if (s.dashTarget == null || !s.dashTarget.isAlive()) { set(s, IDLE); break; }
                if (s.age >= LOCK_TICKS) {
                    s.dashTo = behind(p, s.dashTarget);
                    fx(p, FX_MARK_DASH, s.dashFrom, s.dashTo.subtract(s.dashFrom), 1, s.dashTarget.getId());
                    sound(p, SoundEvents.TRIDENT_RIPTIDE_3, .8f, 1.6f);
                    set(s, MARK_DASH);
                }
            }
            case MARK_DASH -> { if (s.age >= DASH_TICKS) strike(p, s); }
            case MARK_STRIKE -> { if (s.age >= STRIKE_TICKS) set(s, IDLE); }
            default -> {}
        }
        tickShadow(p, s, s.w, false);
        tickShadow(p, s, s.r, true);
        if (!s.stars.isEmpty()) tickStars(p, s);
        tickMark(p, s);
        send(p, s);
    }
    private static void tickShadow(ServerPlayer p, State s, Shadow sh, boolean rShadow) {
        if (!sh.alive) return;
        sh.age++;
        sh.actionAge++;
        if (sh.action == THROW && sh.actionAge >= THROW_TICKS || sh.action == SPIN && sh.actionAge >= SPIN_TICKS) { sh.action = IDLE; sh.actionAge = 0; }
        if (--sh.left <= 0) {
            sh.alive = false;
            fx(p, FX_SHADOW_END, sh.pos, Vec3.ZERO, rShadow ? 0 : 1, -1);
        }
    }

    // ------------------------------------------------------------------ hitting
    private static List<LivingEntity> targets(ServerPlayer p, AABB area) {
        return p.level().getEntitiesOfClass(LivingEntity.class, area, t -> t != p && t.isAlive() && !t.isSpectator()
                && !(t instanceof Player other && p.isAlliedTo(other)));
    }
    private static void hurt(ServerPlayer p, LivingEntity t, float damage) {
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), damage);
    }
    /** The ground at this spot (a few blocks up or down), so a shadow never hangs in the air or sinks in. */
    private static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockPos base = BlockPos.containing(at);
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos pos = base.above(dy);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty())
                return new Vec3(at.x, pos.getY() + 1, at.z);
        }
        return at;
    }

    /** While the mark is on someone, his damage to them is stored for the pop. */
    @SubscribeEvent public static void stored(LivingHurtEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null || s.markTarget != e.getEntity().getId() || e.getSource().is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)) return;
        s.markStored += e.getAmount();
    }
    /** Untouchable for the blink of the Death Mark dash. */
    @SubscribeEvent public static void untouchable(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && (s.action == MARK_LOCK || s.action == MARK_DASH)) e.setCanceled(true);
    }
    /** His slashes are his left click; the vanilla punch would hit twice. */
    @SubscribeEvent public static void plainAttack(AttackEntityEvent e) { if (isHero(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { STATES.remove(e.getEntity().getUUID()); }

    // ------------------------------------------------------------------ sync
    private static void send(ServerPlayer p, State s) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new ZedStatePacket(p.getId(), s.action, s.age, s.side, s.cooldowns.clone(),
                        s.w.alive, s.w.pos, s.w.yaw, s.w.action, s.w.actionAge, s.w.left,
                        s.r.alive, s.r.pos, s.r.yaw, s.r.left, s.markTarget, s.markLeft, s.markStored));
    }
    static void fx(ServerPlayer p, int kind, Vec3 pos, Vec3 dir, float power, int entity) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new ZedFxPacket(kind, pos, dir, power, entity));
    }
    private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
