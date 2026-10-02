package com.FIRNI.superheromod.heroes.hulk;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.core.film.FilmSessions;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.HulkFxPacket;
import com.FIRNI.superheromod.network.packet.HulkStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * Hulk on the server: the change between Banner and Hulk, and every move, decided and checked here
 * (damage, blocks, cooldowns, movement). Clients only animate what this sends them.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class HulkController {
    /** Cooldown slots, as sent to the HUD. */
    public static final int CD_CLAP = 0, CD_POUND = 1, CD_ROCK = 2, CD_RAGE = 3, CD_CHARGED = 4, CD_LEAP = 5, CD_FORM = 6;
    private static final UUID HEALTH = UUID.fromString("6f0c1b8e-6d0a-4a50-9a4e-6b7a3c2e11a1"),
            ARMOR = UUID.fromString("6f0c1b8e-6d0a-4a50-9a4e-6b7a3c2e11a2"),
            STEADY = UUID.fromString("6f0c1b8e-6d0a-4a50-9a4e-6b7a3c2e11a3"),
            SPEED = UUID.fromString("6f0c1b8e-6d0a-4a50-9a4e-6b7a3c2e11a4"),
            GUARD_SLOW = UUID.fromString("6f0c1b8e-6d0a-4a50-9a4e-6b7a3c2e11a5");

    private static final class State {
        boolean hulk;
        int action = IDLE, age;
        boolean lmbDown, rmbDown, spaceDown;
        int lmbAge, punchSide;
        float charge, stamina = STAMINA_MAX;
        int guardBroken, staminaPause;
        final int[] cooldowns = new int[HulkStatePacket.COOLDOWNS];
        // leap
        boolean bounced, leaping; double fallSpeed; int airTicks;
        // charged punch: the shock wave travelling out from the fist, then bursting
        boolean punchWave; Vec3 pwFrom = Vec3.ZERO, pwDir = Vec3.ZERO; double pwDist, pwRange; float pwCharge; HulkBlocks.Budget pwBudget;
        final Set<Integer> pwHits = new HashSet<>();
        // ground pound wave
        boolean wave; Vec3 waveAt = Vec3.ZERO, waveDir = Vec3.ZERO; int waveStep, waveTimer; HulkBlocks.Budget waveBudget;
        double waveLeft, waveRight, waveDrift;
        /** Blocks deep in the split, cleared a moment after the top is thrown out (so it opens as the earth flies). */
        final java.util.ArrayDeque<BlockPos> splitPos = new java.util.ArrayDeque<>();
        final java.util.ArrayDeque<Integer> splitAt = new java.util.ArrayDeque<>();
        final Set<Integer> waveHits = new HashSet<>();
        // Thunderclap: the wave of air travelling out from his hands
        boolean clapping; Vec3 clapFrom = Vec3.ZERO, clapDir = Vec3.ZERO; int clapAge; double clapRange, clapReached; HulkBlocks.Budget clapBudget;
        final Set<Integer> clapHits = new HashSet<>();
        // rock
        BlockState rockBlock = Blocks.STONE.defaultBlockState(); BlockPos rockFrom; boolean rockFlying; Vec3 rockPos = Vec3.ZERO, rockVel = Vec3.ZERO; double rockTravel;
        boolean dirty = true;
    }
    private static final Map<UUID, State> STATES = new HashMap<>();

    private HulkController() {}

    public static boolean isHero(Entity e) { return e instanceof ServerPlayer && ID.equals(AbilityManager.getCharacterId(e.getUUID())); }
    public static boolean isHulk(Entity e) { State s = e == null ? null : STATES.get(e.getUUID()); return s != null && s.hulk; }
    private static State state(ServerPlayer p) { return STATES.computeIfAbsent(p.getUUID(), id -> new State()); }
    private static void set(State s, int action) { s.action = action; s.age = 0; s.dirty = true; }
    private static boolean busy(State s) {
        return s.action == TRANSFORM || s.action == REVERT || s.action == ULTIMATE || s.action == THUNDERCLAP || s.action == POUND
                || s.action == ROCK || s.action == PUNCH_RELEASE || s.action == LANDING;
    }
    private static void tell(ServerPlayer p, String text) { p.displayClientMessage(Component.literal("§a" + text), true); }
    private static String seconds(int ticks) { return String.format(Locale.ROOT, "%.1f", ticks / 20f); }
    private static boolean ready(ServerPlayer p, State s, int slot, String name) {
        if (s.cooldowns[slot] <= 0) return true;
        tell(p, name + ": " + seconds(s.cooldowns[slot]) + " sn bekle");
        return false;
    }
    private static boolean needHulk(ServerPlayer p, State s) {
        if (s.hulk && s.action != TRANSFORM) return true;
        if (!s.hulk) tell(p, "Bu güç sadece Hulk formunda kullanılabilir (G ile dönüş)");
        return false;
    }

    // ------------------------------------------------------------------ keys
    public static void press(ServerPlayer p, AbilitySlot slot) {
        State s = state(p);
        if (FilmSessions.busy(p.getUUID())) return;
        switch (slot) {
            case SKILL_G -> transform(p, s);
            case LMB -> { s.lmbDown = true; s.lmbAge = 0; }
            case RMB -> { s.rmbDown = true; if (s.hulk) guard(p, s); }
            case SKILL_E -> thunderclap(p, s);
            case SKILL_F -> pound(p, s);
            case SKILL_C -> rock(p, s);
            case SKILL_X -> rage(p, s);
            default -> {}
        }
    }
    public static void release(ServerPlayer p, AbilitySlot slot) {
        State s = STATES.get(p.getUUID());
        if (s == null) return;
        if (slot == AbilitySlot.LMB) {
            boolean wasDown = s.lmbDown;
            s.lmbDown = false;
            if (s.action == PUNCH_CHARGE) releasePunch(p, s);
            else if (wasDown && s.lmbAge < TAP_TICKS) jab(p, s);
        }
        if (slot == AbilitySlot.RMB) { s.rmbDown = false; if (s.action == GUARD) endGuard(p, s); }
    }
    /** Space bar, from HulkInputPacket: down starts the leap's charge, up leaps. */
    public static void jump(ServerPlayer p, boolean down) {
        if (!isHero(p)) return;
        State s = state(p);
        s.spaceDown = down;
        // Pressing starts the wind-up at once; the tick decides: let go quickly and it was an ordinary
        // jump (the client already jumped), hold on and it charges the leap.
        if (down) {
            if (!s.hulk || busy(s) || !p.onGround() || s.action == GUARD || s.action == PUNCH_CHARGE) return;
            if (s.cooldowns[CD_LEAP] > 0) return;
            set(s, LEAP_CHARGE);
        }
    }

    // ------------------------------------------------------------------ Banner <-> Hulk
    private static void transform(ServerPlayer p, State s) {
        if (busy(s) || s.action == GUARD || s.action == PUNCH_CHARGE || s.action == LEAP_CHARGE) return;
        if (!ready(p, s, CD_FORM, "Dönüşüm")) return;
        s.cooldowns[CD_FORM] = HulkConfig.get(HulkConfig.TRANSFORM_COOLDOWN) + TRANSFORM_TICKS;
        if (!s.hulk) {
            s.hulk = true;
            set(s, TRANSFORM);
            fx(p, FX_TRANSFORM, p.position(), Vec3.ZERO, 1, 0);
            sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.4f, .6f);
        } else {
            set(s, REVERT);
            attributes(p, false);
            fx(p, FX_REVERT, p.position(), Vec3.ZERO, 1, 0);
            sound(p, SoundEvents.PLAYER_BREATH, 1f, .8f);
        }
    }
    /** Hulk's body: much more health, thick skin, hard to move, a heavier, faster stride. */
    private static void attributes(ServerPlayer p, boolean on) {
        var health = p.getAttribute(Attributes.MAX_HEALTH);
        var armor = p.getAttribute(Attributes.ARMOR);
        var steady = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (health == null || armor == null || steady == null || speed == null) return;
        health.removeModifier(HEALTH); armor.removeModifier(ARMOR); steady.removeModifier(STEADY); speed.removeModifier(SPEED);
        if (on) {
            health.addTransientModifier(new AttributeModifier(HEALTH, "Hulk body", 20, AttributeModifier.Operation.ADDITION));
            armor.addTransientModifier(new AttributeModifier(ARMOR, "Hulk skin", 8, AttributeModifier.Operation.ADDITION));
            steady.addTransientModifier(new AttributeModifier(STEADY, "Hulk weight", .8, AttributeModifier.Operation.ADDITION));
            speed.addTransientModifier(new AttributeModifier(SPEED, "Hulk stride", 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
            p.heal(20);
        } else if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ------------------------------------------------------------------ punches
    private static void jab(ServerPlayer p, State s) {
        // As Banner the left click stays an ordinary punch.
        if (!s.hulk || s.action == TRANSFORM || busy(s) || s.action == GUARD || s.action == LEAP_CHARGE) return;
        if ((s.action == PUNCH_RIGHT || s.action == PUNCH_LEFT) && s.age < PUNCH_TICKS - 3) return;
        s.punchSide = 1 - s.punchSide;
        set(s, s.punchSide == 0 ? PUNCH_RIGHT : PUNCH_LEFT);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, .7f, .6f);
    }
    /**
     * A jab: a short, heavy blow from the hand that swings. Whatever stands in front of that side of
     * his body (right hand: front-right) is hit, no need to have the crosshair on it; it bursts where it
     * lands, shoving whoever stands close by; ground or a wall loses a fist-sized chunk.
     */
    private static void punchHit(ServerPlayer p, State s) {
        Vec3 look = p.getLookAngle(), ahead = flat(p), right = ahead.cross(new Vec3(0, 1, 0)).normalize();
        int side = s.action == PUNCH_RIGHT ? 1 : -1;
        Vec3 shoulder = p.position().add(0, 1.9, 0).add(right.scale(side * .8));
        double reach = HulkConfig.get(HulkConfig.PUNCH_REACH), radius = 1.15;
        Vec3 end = shoulder.add(look.scale(reach));
        float damage = HulkConfig.get(HulkConfig.PUNCH_DAMAGE).floatValue();
        LivingEntity struck = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity t : targets(p, new AABB(shoulder, end).inflate(radius + 1))) {
            Vec3 c = t.getBoundingBox().getCenter();
            double along = Math.max(0, Math.min(reach, c.subtract(shoulder).dot(look)));
            double off = c.distanceTo(shoulder.add(look.scale(along)));
            if (off > radius + t.getBbWidth() * .5 + Math.min(.8, t.getBbHeight() * .3)) continue;
            if (along < best) { best = along; struck = t; }
        }
        Vec3 burst = null;
        if (struck != null) {
            hit(p, struck, damage, ahead, HulkConfig.get(HulkConfig.PUNCH_KNOCKBACK), .3);
            burst = struck.getBoundingBox().getCenter().subtract(look.scale(struck.getBbWidth() * .5));
        } else {
            var hitBlock = p.level().clip(new ClipContext(shoulder, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hitBlock.getType() == HitResult.Type.BLOCK) {
                burst = hitBlock.getLocation();
                // A hole as big as his fist: everything weak enough within it is knocked out and thrown.
                var budget = new HulkBlocks.Budget();
                budget.left = Math.min(budget.left, 14);
                BlockPos centre = BlockPos.containing(burst.add(look.scale(.5)));
                double hardest = Math.min(HulkConfig.get(HulkConfig.MAX_HARDNESS), 3);
                for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
                    if (budget.spent()) break;
                    if (Vec3.atCenterOf(pos).distanceTo(burst) > 1.6) continue;
                    BlockState was = p.level().getBlockState(pos);
                    if (was.isAir() || was.getDestroySpeed(p.level(), pos) > hardest) continue;
                    Vec3 v = look.scale(.4 + p.getRandom().nextDouble() * .25).add(0, .2 + p.getRandom().nextDouble() * .2, 0);
                    HulkBlocks.launch(p, pos.immutable(), budget, v);
                }
            }
        }
        if (burst == null) return;
        // The small blast round the fist.
        for (LivingEntity t : targets(p, new AABB(burst, burst).inflate(2))) {
            if (t == struck || t.getBoundingBox().getCenter().distanceTo(burst) > 2) continue;
            hit(p, t, damage * .4f, t.position().subtract(burst), .6, .2);
        }
        fx(p, FX_PUNCH, burst, look, 1.4f, HulkBlocks.id(p.level().getBlockState(BlockPos.containing(burst).below())));
        p.level().playSound(null, burst.x, burst.y, burst.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, .45f, 1.7f);
        sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .55f);
        sound(p, SoundEvents.IRON_GOLEM_ATTACK, .8f, .8f);
    }
    private static void startCharge(ServerPlayer p, State s) {
        if (!s.hulk || busy(s) || s.action == GUARD || s.action == LEAP_CHARGE) return;
        if (s.cooldowns[CD_CHARGED] > 0) { ready(p, s, CD_CHARGED, "Yıkıcı Yumruk"); s.lmbDown = false; return; }
        set(s, PUNCH_CHARGE);
        sound(p, SoundEvents.WARDEN_HEARTBEAT, 1f, 1.2f);
    }
    private static void releasePunch(ServerPlayer p, State s) {
        s.charge = Math.min(1, s.age / (float) CHARGE_MAX);
        set(s, PUNCH_RELEASE);
        s.cooldowns[CD_CHARGED] = HulkConfig.get(HulkConfig.CHARGED_COOLDOWN);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .4f);
    }
    /**
     * The charged punch: the fist drives a shock wave out ahead (PUNCH_WAVE_SPEED blocks a tick). It
     * tears a tunnel through every block in its way that the rules let it break, throwing the pieces
     * on; the first body it meets is where it bursts (that body is blown about twenty blocks back), as
     * it does at a wall too hard to break or at the end of its reach: the real damage in an area, bodies
     * thrown out, the ground torn up and flung.
     */
    private static void chargedWave(ServerPlayer p, State s) {
        float c = Math.max(.15f, s.charge);
        Vec3 look = p.getLookAngle();
        s.punchWave = true; s.pwCharge = c; s.pwDist = 0; s.pwHits.clear(); s.pwDir = look;
        s.pwFrom = p.position().add(0, 1.95, 0).add(look.scale(1.6));
        s.pwRange = 5 + (HulkConfig.get(HulkConfig.CHARGED_RANGE) - 5) * c;
        s.pwBudget = new HulkBlocks.Budget();
        s.pwBudget.left = Math.max(s.pwBudget.left, 120 + (int) (380 * c));
        fx(p, FX_PUNCH_WAVE, s.pwFrom, look, c, (int) Math.round(s.pwRange * 10));
        sound(p, SoundEvents.GENERIC_EXPLODE, .6f + .5f * c, 1.4f - .4f * c);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, .5f + .5f * c, 1.3f);
        sound(p, SoundEvents.IRON_GOLEM_ATTACK, 1f, .5f);
        punchWaveStep(p, s);
    }
    private static void punchWaveStep(ServerPlayer p, State s) {
        double before = s.pwDist, width = .9 + 1.1 * s.pwCharge;
        s.pwDist = Math.min(s.pwRange, s.pwDist + PUNCH_WAVE_SPEED);
        double hardest = HulkConfig.get(HulkConfig.MAX_HARDNESS);
        for (double d = before; d <= s.pwDist + 1e-6; d += .5) {
            Vec3 at = s.pwFrom.add(s.pwDir.scale(d));
            // A body in the way: it takes the burst.
            for (LivingEntity t : targets(p, new AABB(at, at).inflate(width + 1))) {
                Vec3 to = t.getBoundingBox().getCenter().subtract(at);
                if (to.subtract(s.pwDir.scale(to.dot(s.pwDir))).length() > width + t.getBbWidth() * .5 || Math.abs(to.dot(s.pwDir)) > .8) continue;
                s.punchWave = false;
                // About twenty blocks back through the air.
                hit(p, t, HulkConfig.get(HulkConfig.CHARGED_DAMAGE).floatValue() * (.35f + .65f * s.pwCharge), s.pwDir, 1.2 + 1.3 * s.pwCharge, .4 + .25 * s.pwCharge);
                s.pwHits.add(t.getId());
                punchBlast(p, s, at);
                return;
            }
            // Blocks in the way: torn out, a third of them thrown on ahead, the rest crumbling.
            int r = (int) Math.ceil(width);
            BlockPos centre = BlockPos.containing(at);
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
                if (s.pwBudget.spent()) break;
                Vec3 mid = Vec3.atCenterOf(pos);
                Vec3 rel = mid.subtract(at);
                if (rel.subtract(s.pwDir.scale(rel.dot(s.pwDir))).length() > width || Math.abs(rel.dot(s.pwDir)) > .55) continue;
                BlockState was = p.level().getBlockState(pos);
                if (was.isAir() || !was.getFluidState().isEmpty() && was.canBeReplaced()) continue;
                float hardness = was.getDestroySpeed(p.level(), pos);
                // Too hard (or not allowed): the wave breaks on it.
                if ((hardness < 0 || hardness > hardest || !HulkBlocks.allowed(p, pos.immutable(), was)) && !was.canBeReplaced()) {
                    s.punchWave = false;
                    punchBlast(p, s, at.subtract(s.pwDir.scale(.6)));
                    return;
                }
                if (p.getRandom().nextFloat() < .35f) {
                    Vec3 v = s.pwDir.scale(.55 + .35 * p.getRandom().nextDouble()).add(rel.normalize().scale(.2)).add(0, .2, 0);
                    HulkBlocks.launch(p, pos.immutable(), s.pwBudget, v);
                } else HulkBlocks.breakBlock(p, pos.immutable(), s.pwBudget);
            }
        }
        if (s.pwDist >= s.pwRange) { s.punchWave = false; punchBlast(p, s, s.pwFrom.add(s.pwDir.scale(s.pwRange))); }
    }
    private static void punchBlast(ServerPlayer p, State s, Vec3 at) {
        float c = s.pwCharge;
        double radius = 2.8 + 2.8 * c;
        float damage = (float) (HulkConfig.get(HulkConfig.CHARGED_DAMAGE) * (.35 + .65 * c));
        BlockPos ground = surface(p.serverLevel(), BlockPos.containing(at), 4);
        fx(p, FX_BLAST, at, s.pwDir, c, ground == null ? HulkBlocks.id(p.level().getBlockState(BlockPos.containing(at))) : HulkBlocks.id(p.level().getBlockState(ground)));
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f + c, .7f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.2f, .5f);
        for (LivingEntity t : targets(p, new AABB(at, at).inflate(radius))) {
            if (s.pwHits.contains(t.getId())) continue;
            Vec3 to = t.getBoundingBox().getCenter().subtract(at);
            double d = to.length();
            if (d > radius) continue;
            double fall = 1 - d / radius * .6;
            Vec3 away = new Vec3(to.x, 0, to.z).lengthSqr() < .01 ? s.pwDir : new Vec3(to.x, 0, to.z).normalize().add(s.pwDir.scale(.6));
            hit(p, t, (float) (damage * fall), away, HulkConfig.get(HulkConfig.CHARGED_KNOCKBACK) * (.5 + .5 * c) * fall, .45 + .35 * c);
        }
        // A round hole blown out of whatever is there, the pieces flung outward.
        int r = (int) Math.round(1.5 + 1.8 * c);
        BlockPos centre = BlockPos.containing(at);
        double hardest = HulkConfig.get(HulkConfig.MAX_HARDNESS);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (s.pwBudget.spent()) break;
            double d = Vec3.atCenterOf(pos).distanceTo(at);
            if (d > r + .4) continue;
            BlockState was = p.level().getBlockState(pos);
            if (was.isAir() || was.getDestroySpeed(p.level(), pos) > hardest) continue;
            Vec3 out = Vec3.atCenterOf(pos).subtract(at).normalize();
            Vec3 v = out.scale(.45 + .35 * c).add(s.pwDir.scale(.2)).add(0, .35 + .25 * p.getRandom().nextDouble(), 0);
            HulkBlocks.launch(p, pos.immutable(), s.pwBudget, v);
        }
    }

    // ------------------------------------------------------------------ guard
    private static void guard(ServerPlayer p, State s) {
        if (!s.hulk || busy(s) || s.action == PUNCH_CHARGE || s.action == LEAP_CHARGE) return;
        if (s.guardBroken > 0) { tell(p, "Gard kırıldı: " + seconds(s.guardBroken) + " sn"); return; }
        if (s.stamina < 10) { tell(p, "Gard için güç yok"); return; }
        set(s, GUARD);
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(GUARD_SLOW) == null)
            speed.addTransientModifier(new AttributeModifier(GUARD_SLOW, "Hulk guard", -.55, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    private static void endGuard(ServerPlayer p, State s) {
        if (s.action == GUARD) set(s, IDLE);
        s.staminaPause = 20;
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(GUARD_SLOW);
    }
    @SubscribeEvent public static void guarded(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null || s.action != GUARD) return;
        var source = e.getSource();
        if (source.is(DamageTypes.FALL) && !HulkConfig.get(HulkConfig.GUARD_FALLS)) return;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) && !HulkConfig.get(HulkConfig.GUARD_EXPLOSIONS)) return;
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR) && !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return;
        Vec3 from = source.getSourcePosition();
        boolean front = true;
        if (from != null) {
            Vec3 to = from.subtract(p.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            front = flat.lengthSqr() < .01 || flat.normalize().dot(flat(p)) > .2;
        }
        double stop = front ? HulkConfig.get(HulkConfig.GUARD_FRONT) : HulkConfig.get(HulkConfig.GUARD_BACK);
        e.setAmount((float) (e.getAmount() * (1 - stop)));
        s.stamina = Math.max(0, s.stamina - e.getAmount() * 2);
        fx(p, FX_GUARD_HIT, p.position().add(0, 1.5, 0).add(flat(p).scale(.8)), flat(p), front ? 1 : .4f, 0);
        sound(p, SoundEvents.SHIELD_BLOCK, .9f, .6f);
    }

    // ------------------------------------------------------------------ Thunderclap
    private static void thunderclap(ServerPlayer p, State s) {
        if (!needHulk(p, s) || busy(s) || s.action == PUNCH_CHARGE) return;
        if (!ready(p, s, CD_CLAP, "Thunderclap")) return;
        s.cooldowns[CD_CLAP] = HulkConfig.get(HulkConfig.CLAP_COOLDOWN);
        if (s.action == GUARD) endGuard(p, s);
        set(s, THUNDERCLAP);
        sound(p, SoundEvents.RAVAGER_STEP, 1f, .5f);
    }
    /**
     * The clap: a wall of air leaves his hands and travels forward (CLAP_SPEED blocks a tick), wide
     * and growing. Whoever it reaches is blown off their feet the moment it reaches them; grass is
     * torn off to bare dirt, flowers and leaves are blown away along its path; walls stop it.
     */
    private static void clap(ServerPlayer p, State s) {
        Vec3 look = flat(p);
        double range = HulkConfig.get(HulkConfig.CLAP_RANGE);
        s.clapping = true; s.clapAge = 0; s.clapReached = 0; s.clapRange = range; s.clapHits.clear(); s.clapBudget = new HulkBlocks.Budget();
        s.clapDir = look; s.clapFrom = p.position().add(look.scale(1.2));
        // In the air the wave still runs along the ground below him (if the ground is near).
        if (!p.onGround()) {
            BlockPos below = surface(p.serverLevel(), BlockPos.containing(s.clapFrom.add(0, -4, 0)), 5);
            if (below != null) s.clapFrom = new Vec3(s.clapFrom.x, below.getY() + 1, s.clapFrom.z);
        }
        // The recoil: the clap throws him back several blocks.
        p.setDeltaMovement(p.getDeltaMovement().add(look.scale(-1.05)).add(0, p.onGround() ? .34 : .08, 0));
        p.hurtMarked = true;
        fx(p, FX_CLAP, s.clapFrom, look, (float) range, HulkBlocks.id(p.level().getBlockState(p.blockPosition().below())));
        sound(p, SoundEvents.GENERIC_EXPLODE, 1.6f, 1.5f);
        sound(p, SoundEvents.IRON_GOLEM_ATTACK, 1.3f, .5f);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1f, 1.2f);
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, .5f, 1.6f);
        clapStep(p, s);
    }
    private static void clapStep(ServerPlayer p, State s) {
        double before = s.clapReached;
        s.clapReached = Math.min(s.clapRange, 1.5 + CLAP_SPEED * s.clapAge);
        s.clapAge++;
        Vec3 look = s.clapDir, origin = s.clapFrom, chest = origin.add(0, 1.6, 0);
        double cos = Math.cos(Math.toRadians(CLAP_SPREAD));
        for (LivingEntity t : targets(p, new AABB(origin, origin).inflate(s.clapReached + 1, 4, s.clapReached + 1))) {
            if (s.clapHits.contains(t.getId())) continue;
            Vec3 to = t.position().subtract(origin);
            Vec3 flatTo = new Vec3(to.x, 0, to.z);
            double d = flatTo.length();
            if (d > s.clapReached + t.getBbWidth() * .5 || Math.abs(to.y) > 3.5) continue;
            // Right at his hands the wave is a half circle; further out it narrows to the cone ahead.
            if (d > 2.5 && flatTo.normalize().dot(look) < cos) continue;
            if (d <= 2.5 && d > .3 && flatTo.normalize().dot(look) < -.1) continue;
            // Walls stop the wave.
            if (p.level().clip(new ClipContext(chest, t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            s.clapHits.add(t.getId());
            double fall = 1 - Math.min(1, d / s.clapRange) * .7;
            Vec3 away = d < .3 ? look : flatTo.normalize().add(look).normalize();
            hit(p, t, (float) (HulkConfig.get(HulkConfig.CLAP_DAMAGE) * fall), away, HulkConfig.get(HulkConfig.CLAP_KNOCKBACK) * fall, .45 + .25 * fall);
            int stun = (int) (HulkConfig.get(HulkConfig.CLAP_STUN) * (.5 + .5 * fall));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 3));
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, stun, 2));
            t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, Math.max(20, stun), 0));
            fx(p, FX_WAVE_HIT, t.position().add(0, t.getBbHeight() * .5, 0), away, (float) (.7 + .6 * fall),
                    HulkBlocks.id(t.level().getBlockState(t.blockPosition().below())));
            p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1f, .5f);
        }
        // The band of ground the wave crossed this tick: grass torn to dirt, light growth blown away.
        BlockPos base = BlockPos.containing(origin);
        int r = (int) Math.ceil(s.clapReached);
        for (int dx = -r; dx <= r && !s.clapBudget.spent(); dx++) for (int dz = -r; dz <= r && !s.clapBudget.spent(); dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > s.clapReached || d <= before - .01) continue;
            if (d > 1 && new Vec3(dx, 0, dz).normalize().dot(look) < cos) continue;
            BlockPos column = surface(p.serverLevel(), base.offset(dx, 0, dz), 3);
            if (column == null) continue;
            Vec3 dir = new Vec3(dx, 0, dz).normalize();
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos pos = column.above(dy);
                BlockState was = p.level().getBlockState(pos);
                if (HulkBlocks.light(was) && HulkBlocks.breakBlock(p, pos, s.clapBudget) && p.getRandom().nextFloat() < .4f)
                    fx(p, FX_BLOCK, Vec3.atCenterOf(pos), dir, .5f, HulkBlocks.id(was));
            }
            BlockState top = p.level().getBlockState(column);
            // Here and there the ground itself is ripped up and blown forward.
            // More of it the further out the wave has run.
            if (HulkBlocks.earth(top) && p.getRandom().nextFloat() < .08f + .3f * (float) (d / s.clapRange)) {
                Vec3 v = dir.scale(.65 + .45 * p.getRandom().nextDouble()).add(0, .35 + .3 * p.getRandom().nextDouble(), 0);
                if (HulkBlocks.launch(p, column, s.clapBudget, v)) continue;
            }
            boolean turf = top.is(Blocks.GRASS_BLOCK) || top.is(Blocks.MYCELIUM) || top.is(Blocks.PODZOL);
            if (turf && p.getRandom().nextFloat() < .55f && HulkBlocks.change(p, column, Blocks.DIRT.defaultBlockState(), s.clapBudget)
                    && p.getRandom().nextFloat() < .5f)
                fx(p, FX_BLOCK, Vec3.atCenterOf(column.above()), dir, .5f, HulkBlocks.id(top));
        }
        if (s.clapReached >= s.clapRange) s.clapping = false;
    }

    // ------------------------------------------------------------------ ground pound
    private static void pound(ServerPlayer p, State s) {
        if (!needHulk(p, s) || busy(s) || s.action == PUNCH_CHARGE) return;
        if (!p.onGround()) { tell(p, "Yer Yumruğu için yere bas"); return; }
        if (!ready(p, s, CD_POUND, "Yer Yumruğu")) return;
        s.cooldowns[CD_POUND] = HulkConfig.get(HulkConfig.POUND_COOLDOWN);
        if (s.action == GUARD) endGuard(p, s);
        set(s, POUND);
        sound(p, SoundEvents.RAVAGER_ROAR, .5f, 1.4f);
    }
    private static void startWave(ServerPlayer p, State s) {
        s.wave = true; s.waveStep = 0; s.waveTimer = 0; s.waveHits.clear();
        s.waveDir = flat(p);
        int width = HulkConfig.get(HulkConfig.POUND_WIDTH), depth = HulkConfig.get(HulkConfig.POUND_DEPTH);
        s.waveBudget = new HulkBlocks.Budget();
        s.waveBudget.left = Math.max(s.waveBudget.left, width * depth * (int) (HulkConfig.get(HulkConfig.POUND_RANGE) + 2));
        // The split opens a few blocks in front of him, never under his own feet.
        Vec3 fists = p.position().add(s.waveDir.scale(POUND_START));
        s.waveAt = fists.subtract(s.waveDir);
        BlockState under = p.level().getBlockState(p.blockPosition().below());
        fx(p, FX_POUND_STEP, fists, s.waveDir, 2f, HulkBlocks.id(under));
        sound(p, SoundEvents.GENERIC_EXPLODE, 1.5f, .6f);
        sound(p, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.2f, .5f);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, .5f, .5f);
        // The blow shoves him back a little.
        p.setDeltaMovement(p.getDeltaMovement().add(s.waveDir.scale(-.35)).add(0, .08, 0));
        p.hurtMarked = true;
        for (LivingEntity t : targets(p, new AABB(fists, fists).inflate(3.2, 2.5, 3.2))) {
            Vec3 to = t.position().subtract(fists);
            if (to.x * to.x + to.z * to.z > 3.2 * 3.2) continue;
            s.waveHits.add(t.getId());
            hit(p, t, HulkConfig.get(HulkConfig.POUND_DAMAGE).floatValue(), new Vec3(to.x, 0, to.z).add(s.waveDir), .7, HulkConfig.get(HulkConfig.POUND_LAUNCH) * 1.1);
            fx(p, FX_WAVE_HIT, t.position().add(0, t.getBbHeight() * .5, 0), new Vec3(0, 1, 0), 1, HulkBlocks.id(under));
        }
    }
    /**
     * The split runs on along the ground, following it up and down: ragged (each side wanders in and out,
     * the line itself drifts), deepest along the middle. The top of it is thrown out up-left and up-right
     * at once; what lies under it gives way a moment later, a layer at a time, so it opens as the earth flies.
     */
    private static void waveStep(ServerPlayer p, State s) {
        s.waveStep++;
        var r = p.getRandom();
        Vec3 side = s.waveDir.cross(new Vec3(0, 1, 0)).normalize();
        double width = HulkConfig.get(HulkConfig.POUND_WIDTH);
        if (s.waveStep == 1) { s.waveLeft = width / 2; s.waveRight = width / 2; s.waveDrift = 0; }
        s.waveLeft = Mth.clamp(s.waveLeft + (r.nextDouble() - .5) * 1.4, 1.2, width / 2 + 1.6);
        s.waveRight = Mth.clamp(s.waveRight + (r.nextDouble() - .5) * 1.4, 1.2, width / 2 + 1.6);
        double driftBefore = s.waveDrift;
        s.waveDrift = Mth.clamp(s.waveDrift + (r.nextDouble() - .5) * .7, -1.6, 1.6);
        Vec3 next = s.waveAt.add(s.waveDir).add(side.scale(s.waveDrift - driftBefore));
        BlockPos ground = surface(p.serverLevel(), BlockPos.containing(next.x, s.waveAt.y, next.z), 3);
        if (ground == null || s.waveStep > HulkConfig.get(HulkConfig.POUND_RANGE)) { s.wave = false; return; }
        s.waveAt = new Vec3(next.x, ground.getY() + 1, next.z);
        BlockState top = p.level().getBlockState(ground);
        fx(p, FX_POUND_STEP, s.waveAt, s.waveDir, 1, HulkBlocks.id(top));
        if (s.waveStep % 2 == 0) p.level().playSound(null, s.waveAt.x, s.waveAt.y, s.waveAt.z, s.waveStep % 4 == 0 ? SoundEvents.GRAVEL_BREAK : SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.2f, .5f);
        int depth = HulkConfig.get(HulkConfig.POUND_DEPTH);
        if (width > 0 && depth > 0) {
            double ramp = Math.min(1, s.waveStep / 3.0);
            for (double offset = -Math.floor(s.waveLeft); offset <= Math.floor(s.waveRight); offset += 1) {
                double half = offset < 0 ? s.waveLeft : s.waveRight;
                double n = Math.abs(offset) / (half + .5);
                int deep = Math.max(1, (int) Math.round(depth * ramp * (1 - n * n * .75) * (.75 + .4 * r.nextDouble())));
                BlockPos column = BlockPos.containing(s.waveAt.x + side.x * offset, ground.getY(), s.waveAt.z + side.z * offset);
                BlockPos colTop = surface(p.serverLevel(), column, 2);
                if (colTop == null) continue;
                double sign = offset < -.01 ? -1 : offset > .01 ? 1 : (r.nextBoolean() ? 1 : -1);
                for (int d = 0; d < deep && !s.waveBudget.spent(); d++) {
                    BlockPos pos = colTop.below(d);
                    BlockState was = p.level().getBlockState(pos);
                    if (was.isAir()) continue;
                    if (d < 3) {
                        if (!HulkBlocks.earth(was)) break;
                        // The top is thrown out hard: up and away to its own side, the edges furthest.
                        Vec3 v = side.scale(sign * (.3 + .2 * r.nextDouble() + .07 * Math.abs(offset)))
                                .add(s.waveDir.scale(.08 * r.nextDouble())).add(0, .62 + .3 * r.nextDouble() - d * .1, 0);
                        HulkBlocks.launch(p, pos, s.waveBudget, v);
                    } else {
                        // Deeper down it gives way a little later, layer by layer.
                        s.splitPos.add(pos.immutable());
                        s.splitAt.add(p.tickCount + 1 + d / 2);
                    }
                }
            }
        }
        for (LivingEntity t : targets(p, new AABB(s.waveAt, s.waveAt).inflate(POUND_WIDTH_HIT + width * .3, 2.5, POUND_WIDTH_HIT + width * .3))) {
            if (!s.waveHits.add(t.getId())) continue;
            hit(p, t, HulkConfig.get(HulkConfig.POUND_DAMAGE).floatValue(), s.waveDir, .6, HulkConfig.get(HulkConfig.POUND_LAUNCH));
            fx(p, FX_WAVE_HIT, t.position().add(0, t.getBbHeight() * .5, 0), new Vec3(0, 1, 0), 1, HulkBlocks.id(top));
            p.level().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1f, .5f);
        }
    }
    /** The deep part of the split giving way, a little after the top was thrown out. */
    private static void splitTick(ServerPlayer p, State s) {
        while (!s.splitAt.isEmpty() && s.splitAt.peek() <= p.tickCount) {
            s.splitAt.poll();
            BlockPos pos = s.splitPos.poll();
            if (pos != null && !HulkBlocks.clear(p, pos, s.waveBudget)) continue;
        }
    }

    // ------------------------------------------------------------------ leap
    /** Off he goes, the way he is looking (up, ahead, or a flat bound when he looks down), faster the longer he held. */
    private static void leap(ServerPlayer p, State s) {
        s.charge = Math.min(1, Math.max(0, (s.age - LEAP_TAP) / (float) LEAP_CHARGE_MAX));
        if (!p.onGround()) { set(s, IDLE); return; }
        double power = HulkConfig.get(HulkConfig.LEAP_POWER);
        Vec3 look = p.getLookAngle();
        double speed = (1.5 + 2.05 * s.charge) * power;
        Vec3 v = look.scale(speed);
        // However flat he looks, he climbs at least to about 60% of the height a straight-up leap reaches.
        double minUp = .775 * Math.min(speed, 3.1 * power);
        if (v.y < minUp) v = new Vec3(v.x, minUp, v.z);
        if (v.y > 3.1 * power) v = new Vec3(v.x, 3.1 * power, v.z);
        p.setDeltaMovement(v);
        p.hurtMarked = true;
        s.bounced = false; s.fallSpeed = 0; s.airTicks = 0; s.leaping = true;
        s.cooldowns[CD_LEAP] = HulkConfig.get(HulkConfig.LEAP_COOLDOWN);
        set(s, LEAP);
        BlockState under = p.level().getBlockState(p.blockPosition().below());
        fx(p, FX_LANDING, p.position(), new Vec3(0, 1, 0), .3f + .4f * s.charge, HulkBlocks.id(under));
        sound(p, SoundEvents.RAVAGER_STEP, 1f, .6f);
        sound(p, SoundEvents.GENERIC_EXPLODE, .3f + .4f * s.charge, 1.6f);
    }
    /** Touching down: the harder the fall, the bigger the blow and the crater; then a short bounce. */
    private static void land(ServerPlayer p, State s) {
        float power = (float) Math.min(1, Math.max(.15, (s.fallSpeed - .3) / 1.6));
        BlockPos below = p.blockPosition().below();
        BlockState ground = p.level().getBlockState(below);
        fx(p, FX_LANDING, p.position(), new Vec3(0, 1, 0), power, HulkBlocks.id(ground));
        sound(p, SoundEvents.GENERIC_EXPLODE, .5f + .8f * power, 1.2f - .4f * power);
        sound(p, SoundEvents.ANVIL_LAND, .6f, .5f);
        double radius = HulkConfig.get(HulkConfig.LANDING_RADIUS) * (.4 + .6 * power);
        for (LivingEntity t : targets(p, p.getBoundingBox().inflate(radius, 2, radius))) {
            Vec3 to = t.position().subtract(p.position());
            double d = Math.sqrt(to.x * to.x + to.z * to.z);
            if (d > radius) continue;
            double fall = 1 - d / radius;
            hit(p, t, (float) (HulkConfig.get(HulkConfig.LANDING_DAMAGE) * power * (.4 + .6 * fall)), new Vec3(to.x, 0, to.z), .6 + .9 * power * fall, .3 + .4 * power * fall);
        }
        int crater = HulkConfig.get(HulkConfig.LANDING_CRATER);
        if (crater > 0 && power > .45f) crater(p, below, Math.max(1, Math.round(crater * power)), 1, new HulkBlocks.Budget());
        // The bounce: one short hop, never another.
        if (!s.bounced) {
            Vec3 dir = flat(p);
            p.setDeltaMovement(dir.x * .15, .32 + .18 * power, dir.z * .15);
            p.hurtMarked = true;
            s.bounced = true;
        }
        s.leaping = false;
        if (s.action == LEAP || s.action == IDLE) set(s, LANDING);
    }
    /** A shallow bowl: the top layer within the radius, a second layer near the middle; the earth is flung out of it. */
    static void crater(ServerPlayer p, BlockPos centre, int radius, int depth, HulkBlocks.Budget budget) { crater(p, centre, radius, depth, budget, 1); }
    static void crater(ServerPlayer p, BlockPos centre, int radius, int depth, HulkBlocks.Budget budget, double force) {
        var r = p.getRandom();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius + .3) continue;
            int deep = d < radius * .5 ? depth + 1 : depth;
            BlockPos top = surface(p.serverLevel(), centre.offset(dx, 0, dz), 2);
            if (top == null) continue;
            Vec3 out = d < .1 ? new Vec3(r.nextDouble() - .5, 0, r.nextDouble() - .5) : new Vec3(dx / d, 0, dz / d);
            for (int i = 0; i < deep && !budget.spent(); i++) {
                BlockPos pos = top.below(i);
                if (!HulkBlocks.earth(p.level().getBlockState(pos))) continue;
                // Out and up: the middle goes highest, the rim spills outward.
                double up = (.55 + .45 * (1 - d / (radius + 1)) + r.nextDouble() * .25) * force - i * .12;
                double side = (.14 + .1 * d / (radius + 1) + r.nextDouble() * .08) * force;
                HulkBlocks.launch(p, pos, budget, out.scale(side).add(0, Math.max(.2, up), 0));
            }
        }
    }

    // ------------------------------------------------------------------ rock
    private static void rock(ServerPlayer p, State s) {
        if (!needHulk(p, s) || busy(s) || s.action == PUNCH_CHARGE) return;
        if (!ready(p, s, CD_ROCK, "Kaya Fırlatma")) return;
        if (!p.onGround()) { tell(p, "Kaya sökmek için yere bas"); return; }
        BlockPos source = surface(p.serverLevel(), BlockPos.containing(p.position().add(flat(p).scale(ROCK_AHEAD))), 2);
        BlockState st = source == null ? null : p.level().getBlockState(source);
        if (st == null || !HulkBlocks.earth(st) || st.hasBlockEntity()) { tell(p, "Önünde sökülecek taş ya da toprak yok"); return; }
        s.cooldowns[CD_ROCK] = HulkConfig.get(HulkConfig.ROCK_COOLDOWN);
        if (s.action == GUARD) endGuard(p, s);
        s.rockBlock = st; s.rockFrom = source;
        set(s, ROCK);
    }
    private static void launchRock(ServerPlayer p, State s) {
        s.rockFlying = true; s.rockTravel = 0; s.dirty = true;
        s.rockPos = p.position().add(0, 3.6, 0).add(flat(p).scale(.6));
        Vec3 aim = p.getLookAngle().add(0, .08, 0).normalize();
        s.rockVel = aim.scale(HulkConfig.get(HulkConfig.ROCK_SPEED));
        sound(p, SoundEvents.IRON_GOLEM_ATTACK, 1f, .4f);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .4f);
    }
    private static void tickRock(ServerPlayer p, State s) {
        ServerLevel level = p.serverLevel();
        Vec3 from = s.rockPos, to = from.add(s.rockVel);
        s.rockVel = s.rockVel.add(0, -.035, 0).scale(.995);
        s.rockTravel += s.rockVel.length();
        // Never into unloaded ground or out of the world.
        if (!level.isLoaded(BlockPos.containing(to)) || to.y < level.getMinBuildHeight() || s.rockTravel > HulkConfig.get(HulkConfig.ROCK_RANGE)) {
            rockImpact(p, s, from, null); return;
        }
        var block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        LivingEntity direct = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity t : targets(p, new AABB(from, to).inflate(.9))) {
            var c = t.getBoundingBox().inflate(1.1).clip(from, to);
            if (c.isPresent() && c.get().distanceToSqr(from) < best) { best = c.get().distanceToSqr(from); direct = t; }
        }
        if (direct != null) { rockImpact(p, s, direct.position().add(0, direct.getBbHeight() * .5, 0), direct); return; }
        if (block.getType() != HitResult.Type.MISS) { rockImpact(p, s, to, null); return; }
        s.rockPos = to;
    }
    private static void rockImpact(ServerPlayer p, State s, Vec3 at, LivingEntity direct) {
        s.rockFlying = false; s.dirty = true;
        Vec3 dir = s.rockVel.normalize();
        fx(p, FX_ROCK_HIT, at, dir, 1, HulkBlocks.id(s.rockBlock));
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, .9f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.5f, .6f);
        if (direct != null) {
            hit(p, direct, HulkConfig.get(HulkConfig.ROCK_DAMAGE).floatValue(), dir, 1.4, .4);
            direct.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
        }
        double r = HulkConfig.get(HulkConfig.ROCK_RADIUS);
        for (LivingEntity t : targets(p, new AABB(at, at).inflate(r))) {
            if (t == direct) continue;
            double d = t.position().distanceTo(at);
            if (d > r) continue;
            hit(p, t, (float) (HulkConfig.get(HulkConfig.ROCK_SPLASH) * (1 - d / r * .6)), t.position().subtract(at), .7 * (1 - d / r), .3);
            // The shock leaves everyone near it staggering.
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (60 + 40 * (1 - d / r)), 2));
        }
        // Where it lands it tears up the ground round it (the boulder itself stays stuck there a while, drawn by the clients).
        BlockPos ground = surface(p.serverLevel(), BlockPos.containing(at), 3);
        if (ground != null) {
            var budget = new HulkBlocks.Budget();
            budget.left = Math.min(budget.left, 16);
            crater(p, ground, 2, 1, budget, .7);
        }
    }

    // ------------------------------------------------------------------ Gamma Rage
    private static void rage(ServerPlayer p, State s) {
        if (!needHulk(p, s) || busy(s)) return;
        if (!ready(p, s, CD_RAGE, "Gama Öfkesi")) return;
        LivingEntity target = HulkRageSession.findTarget(p);
        if (target == null) { tell(p, "Gama Öfkesi: menzilde hedef yok"); return; }
        if (s.action == GUARD) endGuard(p, s);
        if (HulkRageSession.start(p, target)) { set(s, ULTIMATE); s.cooldowns[CD_RAGE] = HulkConfig.get(HulkConfig.ULT_COOLDOWN); }
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (!isHero(p)) {
            State gone = STATES.remove(p.getUUID());
            if (gone != null) { attributes(p, false); endGuard(p, gone); send(p, new State()); }
            return;
        }
        State s = state(p);
        s.age++;
        for (int i = 0; i < s.cooldowns.length; i++) if (s.cooldowns[i] > 0) s.cooldowns[i]--;
        if (s.guardBroken > 0) s.guardBroken--;
        if (s.action != GUARD) {
            if (s.staminaPause > 0) s.staminaPause--;
            else s.stamina = Math.min(STAMINA_MAX, s.stamina + HulkConfig.get(HulkConfig.GUARD_REGEN).floatValue());
        }
        if (s.lmbDown) {
            s.lmbAge++;
            if (s.lmbAge == TAP_TICKS && s.hulk) startCharge(p, s);
        }
        switch (s.action) {
            case TRANSFORM -> {
                if (s.age == GROW_START) sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.6f, .5f);
                if (s.age == GROW_END - 4) attributes(p, true);
                if (s.age == GROW_END) { sound(p, SoundEvents.WARDEN_ROAR, 1.2f, .9f); sound(p, SoundEvents.RAVAGER_ROAR, 1f, .6f); fx(p, FX_TRANSFORM, p.position(), Vec3.ZERO, 2, 0); }
                if (s.age >= TRANSFORM_TICKS) set(s, IDLE);
            }
            case REVERT -> { if (s.age >= REVERT_TICKS) { s.hulk = false; set(s, IDLE); } }
            case PUNCH_RIGHT, PUNCH_LEFT -> {
                if (s.age == PUNCH_HIT) punchHit(p, s);
                if (s.age >= PUNCH_TICKS) set(s, IDLE);
            }
            case PUNCH_CHARGE -> {
                if (s.age == CHARGE_MAX) sound(p, SoundEvents.BEACON_POWER_SELECT, .6f, 1.6f);
                if (!s.lmbDown) releasePunch(p, s);
            }
            case PUNCH_RELEASE -> {
                if (s.age == RELEASE_HIT) chargedWave(p, s);
                if (s.age >= RELEASE_TICKS) set(s, IDLE);
            }
            case GUARD -> {
                s.stamina -= HulkConfig.get(HulkConfig.GUARD_DRAIN).floatValue();
                if (s.stamina <= 0) {
                    s.stamina = 0; s.guardBroken = 60;
                    tell(p, "Gard kırıldı!");
                    sound(p, SoundEvents.SHIELD_BREAK, 1f, .6f);
                    endGuard(p, s);
                } else if (!s.rmbDown) endGuard(p, s);
            }
            case THUNDERCLAP -> {
                if (s.age == CLAP_HIT) clap(p, s);
                if (s.age >= CLAP_TICKS) set(s, IDLE);
            }
            case POUND -> {
                if (s.age == POUND_HIT) startWave(p, s);
                if (s.age >= POUND_TICKS) set(s, IDLE);
            }
            case LEAP_CHARGE -> {
                if (s.age == LEAP_TAP) sound(p, SoundEvents.RAVAGER_STEP, .8f, .5f);
                if (s.age == LEAP_TAP + LEAP_CHARGE_MAX) sound(p, SoundEvents.BEACON_POWER_SELECT, .5f, 1.8f);
                if (!s.spaceDown) { if (s.age < LEAP_TAP) set(s, IDLE); else leap(p, s); }
                else if (!p.onGround() && s.age > 1) set(s, IDLE);
            }
            case LEAP -> { if (!s.leaping) set(s, IDLE); }
            case LANDING -> {
                p.fallDistance = 0;
                if (s.age >= LANDING_TICKS && p.onGround()) set(s, IDLE);
                if (s.age > 80) set(s, IDLE);
            }
            case ROCK -> {
                if (s.age == ROCK_GRAB) {
                    fx(p, FX_ROCK_PULL, Vec3.atCenterOf(s.rockFrom), Vec3.ZERO, 1, HulkBlocks.id(s.rockBlock));
                    sound(p, SoundEvents.STONE_BREAK, 1.4f, .5f);
                    if (HulkConfig.get(HulkConfig.ROCK_TAKES_BLOCK)) {
                        // A boulder's worth of ground comes up with it: the hole is torn, its edges thrown off.
                        var budget = new HulkBlocks.Budget();
                        budget.left = Math.min(budget.left, 10);
                        HulkBlocks.breakBlock(p, s.rockFrom, budget);
                        crater(p, s.rockFrom, 1, 1, budget, .55);
                    }
                }
                if (s.age == ROCK_THROW) launchRock(p, s);
                if (s.age >= ROCK_TICKS) set(s, IDLE);
            }
            case ULTIMATE -> { if (!FilmSessions.playing(p.getUUID(), HulkRageSession.ID)) set(s, IDLE); }
            default -> {}
        }
        // A leap goes on under whatever he does in the air (a Thunderclap, a punch); it ends when he lands.
        if (s.leaping) {
            p.fallDistance = 0;
            double vy = p.getY() - p.yo;
            if (vy < 0) s.fallSpeed = Math.max(s.fallSpeed, -vy);
            s.airTicks++;
            if (s.airTicks > 3 && (p.onGround() || p.isInWater())) land(p, s);
            else if (s.airTicks > 240) s.leaping = false;
        }
        if (s.wave && (s.waveTimer++ % POUND_STEP_TICKS == 0)) waveStep(p, s);
        if (!s.splitAt.isEmpty()) splitTick(p, s);
        if (s.clapping) clapStep(p, s);
        if (s.punchWave) punchWaveStep(p, s);
        if (s.rockFlying) tickRock(p, s);
        if (s.hulk) p.fallDistance = Math.min(p.fallDistance, 6);   // Hulk shrugs off ordinary falls
        send(p, s);
    }

    // ------------------------------------------------------------------ hitting things
    /** Everyone this Hulk may hurt in an area (not himself, not spectators, not allies unless friendly fire). */
    private static List<LivingEntity> targets(ServerPlayer p, AABB area) {
        return p.level().getEntitiesOfClass(LivingEntity.class, area, t -> t != p && t.isAlive() && !t.isSpectator()
                && !(t instanceof Player other && p.isAlliedTo(other) && !HulkConfig.get(HulkConfig.FRIENDLY_FIRE)));
    }
    /** Damage and a throw; heavy and large creatures (and knockback resistance) are moved less. */
    static void hit(ServerPlayer p, LivingEntity t, float damage, Vec3 dir, double push, double up) {
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), damage);
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        flat = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize();
        double resist = t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double size = t.getBbWidth() * t.getBbWidth() * t.getBbHeight();
        double heavy = 1 / (1 + HulkConfig.get(HulkConfig.HEAVY_RESISTANCE) * Math.max(0, size - .65) * .8);
        double k = Math.max(0, 1 - resist) * heavy;
        t.setDeltaMovement(t.getDeltaMovement().add(flat.scale(push * k)).add(0, up * k, 0));
        t.hurtMarked = true;
    }
    static Vec3 flat(ServerPlayer p) { return Vec3.directionFromRotation(0, p.getYRot()); }
    /** The top solid block at this column, looking a few blocks up and down; null if none. */
    static BlockPos surface(ServerLevel level, BlockPos at, int search) {
        for (int dy = search; dy >= -search - 1; dy--) {
            BlockPos pos = at.above(dy);
            if (!level.isLoaded(pos)) return null;
            BlockState here = level.getBlockState(pos), above = level.getBlockState(pos.above());
            if (here.isSolidRender(level, pos) && !above.isSolidRender(level, pos.above())) return pos;
        }
        return null;
    }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e) { if (e.phase == TickEvent.Phase.END) HulkBlocks.tickDebris(); }

    /** Vanilla punches would double his damage: the punches above are his. */
    @SubscribeEvent public static void plainAttack(AttackEntityEvent e) { if (isHulk(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void fall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && (s.leaping || s.action == LEAP || s.action == LANDING || s.action == ULTIMATE)) e.setCanceled(true);
    }
    @SubscribeEvent public static void filmImmune(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isHero(p) && FilmSessions.playing(p.getUUID(), HulkRageSession.ID)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ sync
    private static void send(ServerPlayer p, State s) {
        int flags = (s.hulk ? FLAG_HULK : 0) | (s.rockFlying ? FLAG_ROCK_FLYING : 0) | (s.guardBroken > 0 ? FLAG_GUARD_BROKEN : 0);
        float charge = s.action == PUNCH_CHARGE ? Math.min(1, s.age / (float) CHARGE_MAX) : s.action == LEAP_CHARGE ? Math.min(1, Math.max(0, (s.age - LEAP_TAP) / (float) LEAP_CHARGE_MAX)) : s.charge;
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new HulkStatePacket(p.getId(), s.action, s.age, flags, charge, s.stamina, s.rockPos, HulkBlocks.id(s.rockBlock),
                        s.cooldowns.clone(), HulkConfig.get(HulkConfig.ULT_DELAY)));
    }
    static void fx(ServerPlayer p, int kind, Vec3 pos, Vec3 dir, float power, int block) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new HulkFxPacket(kind, pos, dir, power, block));
    }
    private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && STATES.remove(p.getUUID()) != null) attributes(p, false);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        State s = STATES.get(e.getEntity().getUUID());
        if (s != null) { s.hulk = false; set(s, IDLE); s.stamina = STAMINA_MAX; }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { STATES.clear(); }
}
