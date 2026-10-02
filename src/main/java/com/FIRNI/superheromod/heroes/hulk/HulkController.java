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
        boolean bounced; double fallSpeed; int airTicks;
        // ground pound wave
        boolean wave; Vec3 waveAt = Vec3.ZERO, waveDir = Vec3.ZERO; int waveStep; HulkBlocks.Budget waveBudget;
        final Set<Integer> waveHits = new HashSet<>();
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
                || s.action == ROCK || s.action == PUNCH_RELEASE || s.action == LEAP || s.action == LANDING;
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
        if (down) {
            if (!s.hulk || busy(s) || !p.onGround() || s.action == GUARD || s.action == PUNCH_CHARGE) return;
            if (s.cooldowns[CD_LEAP] > 0) return;
            set(s, LEAP_CHARGE);
        } else if (s.action == LEAP_CHARGE) leap(p, s);
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
            speed.addTransientModifier(new AttributeModifier(SPEED, "Hulk stride", .15, AttributeModifier.Operation.MULTIPLY_BASE));
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
    private static void punchHit(ServerPlayer p, State s) {
        Vec3 look = flat(p), chest = p.position().add(0, 1.3, 0);
        double reach = HulkConfig.get(HulkConfig.PUNCH_REACH);
        boolean any = false;
        for (LivingEntity t : targets(p, p.getBoundingBox().inflate(reach))) {
            Vec3 to = t.position().add(0, t.getBbHeight() * .5, 0).subtract(chest);
            if (to.length() > reach + t.getBbWidth() * .5 || to.normalize().dot(look) < .35) continue;
            hit(p, t, HulkConfig.get(HulkConfig.PUNCH_DAMAGE).floatValue(), look, HulkConfig.get(HulkConfig.PUNCH_KNOCKBACK), .25);
            fx(p, FX_PUNCH, t.position().add(0, t.getBbHeight() * .6, 0), look, 1, 0);
            any = true;
        }
        if (any) { sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .55f); sound(p, SoundEvents.IRON_GOLEM_ATTACK, .7f, .8f); return; }
        // Nothing alive in reach: small blocks in front of the fist give way.
        var hitBlock = p.level().clip(new ClipContext(chest, chest.add(look.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hitBlock.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hitBlock.getBlockPos();
            BlockState was = p.level().getBlockState(pos);
            var budget = new HulkBlocks.Budget();
            budget.left = Math.min(budget.left, 2);
            if (was.getDestroySpeed(p.level(), pos) <= 1.0f && HulkBlocks.breakBlock(p, pos, budget))
                fx(p, FX_BLOCK, Vec3.atCenterOf(pos), look, .6f, HulkBlocks.id(was));
            else fx(p, FX_PUNCH, hitBlock.getLocation(), look, .6f, HulkBlocks.id(was));
            sound(p, SoundEvents.STONE_HIT, 1f, .6f);
        }
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
    /** The charged punch: a shock wave rolls forward from the fist; it grows with the charge. */
    private static void chargedWave(ServerPlayer p, State s) {
        float c = Math.max(.15f, s.charge);
        Vec3 look = p.getLookAngle(), from = p.getEyePosition().add(0, -.4, 0);
        double range = 3 + (HulkConfig.get(HulkConfig.CHARGED_RANGE) - 3) * c, width = .9 + 1.8 * c;
        float damage = (float) (HulkConfig.get(HulkConfig.CHARGED_DAMAGE) * (.35 + .65 * c));
        fx(p, FX_CHARGED_WAVE, from.add(look.scale(1.2)), look, c, 0);
        sound(p, SoundEvents.GENERIC_EXPLODE, .6f + .6f * c, 1.3f - .5f * c);
        sound(p, SoundEvents.IRON_GOLEM_ATTACK, 1f, .5f);
        for (LivingEntity t : targets(p, new AABB(from, from.add(look.scale(range))).inflate(width))) {
            Vec3 to = t.position().add(0, t.getBbHeight() * .5, 0).subtract(from);
            double along = to.dot(look);
            if (along < 0 || along > range) continue;
            if (to.subtract(look.scale(along)).length() > width + t.getBbWidth() * .5) continue;
            boolean direct = along < 3;
            hit(p, t, damage * (direct ? 1 : .6f), look, HulkConfig.get(HulkConfig.CHARGED_KNOCKBACK) * (.4 + .6 * c), .35);
        }
        // Breaks what it meets: a limited tunnel of weak blocks along the wave.
        var budget = new HulkBlocks.Budget();
        budget.left = Math.min(budget.left, (int) (6 + 30 * c));
        for (double d = 1.5; d < range && !budget.spent(); d += .8) {
            Vec3 at = from.add(look.scale(d));
            for (int i = 0; i < 3 && !budget.spent(); i++) {
                BlockPos pos = BlockPos.containing(at.add((p.getRandom().nextDouble() - .5) * width, (p.getRandom().nextDouble() - .5) * width, (p.getRandom().nextDouble() - .5) * width));
                BlockState was = p.level().getBlockState(pos);
                if (was.isAir() || was.getDestroySpeed(p.level(), pos) > 1.2 + 1.8 * c) continue;
                if (HulkBlocks.breakBlock(p, pos, budget) && p.getRandom().nextFloat() < .5f) fx(p, FX_BLOCK, Vec3.atCenterOf(pos), look, .7f, HulkBlocks.id(was));
            }
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
    private static void clap(ServerPlayer p, State s) {
        Vec3 look = flat(p), hands = p.position().add(0, 1.0, 0).add(look.scale(1.1));
        double range = HulkConfig.get(HulkConfig.CLAP_RANGE);
        fx(p, FX_CLAP, hands, look, (float) range, 0);
        sound(p, SoundEvents.GENERIC_EXPLODE, 1.4f, 1.6f);
        sound(p, SoundEvents.IRON_GOLEM_ATTACK, 1.2f, .5f);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, .6f, 1.4f);
        for (LivingEntity t : targets(p, p.getBoundingBox().inflate(range))) {
            Vec3 to = t.position().add(0, t.getBbHeight() * .5, 0).subtract(hands);
            double d = to.length();
            if (d > range || new Vec3(to.x, 0, to.z).normalize().dot(look) < -.1) continue;
            // Walls stop the wave.
            if (p.level().clip(new ClipContext(hands, t.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            double fall = 1 - d / range;
            Vec3 away = new Vec3(to.x, 0, to.z).normalize();
            hit(p, t, (float) (HulkConfig.get(HulkConfig.CLAP_DAMAGE) * (.3 + .7 * fall)), away, HulkConfig.get(HulkConfig.CLAP_KNOCKBACK) * (.35 + .65 * fall), .35 * fall);
            int stun = (int) (HulkConfig.get(HulkConfig.CLAP_STUN) * (.4 + .6 * fall));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 3));
            t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, stun, 2));
        }
        // Grass, flowers and leaves in the wave's path are torn away.
        var budget = new HulkBlocks.Budget();
        BlockPos centre = p.blockPosition();
        int r = (int) Math.min(8, range * .7);
        for (int dx = -r; dx <= r && !budget.spent(); dx++) for (int dz = -r; dz <= r && !budget.spent(); dz++) for (int dy = -1; dy <= 3; dy++) {
            if (dx * dx + dz * dz > r * r) continue;
            Vec3 dir = new Vec3(dx, 0, dz);
            if (dir.lengthSqr() > 1 && dir.normalize().dot(look) < .1) continue;
            BlockPos pos = centre.offset(dx, dy, dz);
            BlockState was = p.level().getBlockState(pos);
            if (!HulkBlocks.light(was)) continue;
            if (HulkBlocks.breakBlock(p, pos, budget) && p.getRandom().nextFloat() < .35f) fx(p, FX_BLOCK, Vec3.atCenterOf(pos), dir.normalize(), .4f, HulkBlocks.id(was));
        }
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
        s.wave = true; s.waveStep = 0; s.waveHits.clear(); s.waveBudget = new HulkBlocks.Budget();
        s.waveDir = flat(p);
        s.waveAt = p.position().add(s.waveDir.scale(1.2));
        BlockState under = p.level().getBlockState(p.blockPosition().below());
        fx(p, FX_POUND_STEP, p.position().add(s.waveDir.scale(.9)), s.waveDir, 1.6f, HulkBlocks.id(under));
        sound(p, SoundEvents.GENERIC_EXPLODE, 1.2f, .7f);
        sound(p, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1f, .5f);
    }
    /** One block further along the ground: follows the surface up and down, opens the trench, throws up whoever is on it. */
    private static void waveStep(ServerPlayer p, State s) {
        s.waveStep++;
        Vec3 next = s.waveAt.add(s.waveDir);
        BlockPos ground = surface(p.serverLevel(), BlockPos.containing(next.x, s.waveAt.y, next.z), 3);
        if (ground == null || s.waveStep > HulkConfig.get(HulkConfig.POUND_RANGE)) { s.wave = false; return; }
        s.waveAt = new Vec3(next.x, ground.getY() + 1, next.z);
        BlockState top = p.level().getBlockState(ground);
        fx(p, FX_POUND_STEP, s.waveAt, s.waveDir, 1, HulkBlocks.id(top));
        if (s.waveStep % 2 == 0) p.level().playSound(null, s.waveAt.x, s.waveAt.y, s.waveAt.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 1f, .6f);
        // The trench: a shallow, gradually deepening cut along the line (only through earth and stone).
        int width = HulkConfig.get(HulkConfig.POUND_WIDTH), depth = HulkConfig.get(HulkConfig.POUND_DEPTH);
        if (width > 0 && depth > 0) {
            int deep = s.waveStep > 3 ? depth : 1;
            Vec3 side = s.waveDir.cross(new Vec3(0, 1, 0)).normalize();
            for (int w = -(width - 1) / 2; w <= width / 2; w++)
                for (int d = 0; d < deep; d++) {
                    BlockPos pos = BlockPos.containing(s.waveAt.x + side.x * w, ground.getY() - d, s.waveAt.z + side.z * w);
                    if (HulkBlocks.earth(p.level().getBlockState(pos))) HulkBlocks.breakBlock(p, pos, s.waveBudget);
                }
        }
        for (LivingEntity t : targets(p, new AABB(s.waveAt, s.waveAt).inflate(1.6, 2, 1.6))) {
            if (!s.waveHits.add(t.getId())) continue;
            hit(p, t, HulkConfig.get(HulkConfig.POUND_DAMAGE).floatValue(), s.waveDir, .5, HulkConfig.get(HulkConfig.POUND_LAUNCH));
        }
    }

    // ------------------------------------------------------------------ leap
    private static void leap(ServerPlayer p, State s) {
        s.charge = Math.min(1, s.age / (float) LEAP_CHARGE_MAX);
        if (!p.onGround()) { set(s, IDLE); return; }
        double power = HulkConfig.get(HulkConfig.LEAP_POWER);
        Vec3 dir = flat(p);
        double up = (.85 + .9 * s.charge) * power, ahead = (.45 + 1.45 * s.charge) * power;
        p.setDeltaMovement(dir.x * ahead, up, dir.z * ahead);
        p.hurtMarked = true;
        s.bounced = false; s.fallSpeed = 0; s.airTicks = 0;
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
        set(s, LANDING);
    }
    /** A shallow bowl: the top layer within the radius, a second layer near the middle. */
    static void crater(ServerPlayer p, BlockPos centre, int radius, int depth, HulkBlocks.Budget budget) {
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius + .3) continue;
            int deep = d < radius * .5 ? depth + 1 : depth;
            BlockPos top = surface(p.serverLevel(), centre.offset(dx, 0, dz), 2);
            if (top == null) continue;
            for (int i = 0; i < deep && !budget.spent(); i++) {
                BlockPos pos = top.below(i);
                if (HulkBlocks.earth(p.level().getBlockState(pos))) HulkBlocks.breakBlock(p, pos, budget);
            }
        }
    }

    // ------------------------------------------------------------------ rock
    private static void rock(ServerPlayer p, State s) {
        if (!needHulk(p, s) || busy(s) || s.action == PUNCH_CHARGE) return;
        if (!ready(p, s, CD_ROCK, "Kaya Fırlatma")) return;
        if (!p.onGround()) { tell(p, "Kaya sökmek için yere bas"); return; }
        BlockPos source = surface(p.serverLevel(), BlockPos.containing(p.position().add(flat(p).scale(1.4))), 2);
        BlockState st = source == null ? null : p.level().getBlockState(source);
        if (st == null || !HulkBlocks.earth(st) || st.hasBlockEntity()) { tell(p, "Önünde sökülecek taş ya da toprak yok"); return; }
        s.cooldowns[CD_ROCK] = HulkConfig.get(HulkConfig.ROCK_COOLDOWN);
        if (s.action == GUARD) endGuard(p, s);
        s.rockBlock = st; s.rockFrom = source;
        set(s, ROCK);
    }
    private static void launchRock(ServerPlayer p, State s) {
        s.rockFlying = true; s.rockTravel = 0; s.dirty = true;
        s.rockPos = p.position().add(0, 2.6, 0).add(flat(p).scale(.4));
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
            var c = t.getBoundingBox().inflate(.6).clip(from, to);
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
        if (direct != null) hit(p, direct, HulkConfig.get(HulkConfig.ROCK_DAMAGE).floatValue(), dir, 1.4, .4);
        double r = HulkConfig.get(HulkConfig.ROCK_RADIUS);
        for (LivingEntity t : targets(p, new AABB(at, at).inflate(r))) {
            if (t == direct) continue;
            double d = t.position().distanceTo(at);
            if (d > r) continue;
            hit(p, t, (float) (HulkConfig.get(HulkConfig.ROCK_SPLASH) * (1 - d / r * .6)), t.position().subtract(at), .7 * (1 - d / r), .3);
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
                if (!p.onGround()) set(s, IDLE);
                else if (!s.spaceDown) leap(p, s);
            }
            case LEAP -> {
                p.fallDistance = 0;
                double vy = p.getY() - p.yo;
                if (vy < 0) s.fallSpeed = Math.max(s.fallSpeed, -vy);
                s.airTicks++;
                if (s.airTicks > 3 && (p.onGround() || p.isInWater())) land(p, s);
                if (s.airTicks > 200) set(s, IDLE);
            }
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
                        var budget = new HulkBlocks.Budget();
                        budget.left = Math.min(budget.left, 1);
                        HulkBlocks.breakBlock(p, s.rockFrom, budget);
                    }
                }
                if (s.age == ROCK_THROW) launchRock(p, s);
                if (s.age >= ROCK_TICKS) set(s, IDLE);
            }
            case ULTIMATE -> { if (!FilmSessions.playing(p.getUUID(), HulkRageSession.ID)) set(s, IDLE); }
            default -> {}
        }
        if (s.wave) waveStep(p, s);
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

    /** Vanilla punches would double his damage: the punches above are his. */
    @SubscribeEvent public static void plainAttack(AttackEntityEvent e) { if (isHulk(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void fall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isHero(p)) return;
        State s = STATES.get(p.getUUID());
        if (s != null && (s.action == LEAP || s.action == LANDING || s.action == ULTIMATE)) e.setCanceled(true);
    }
    @SubscribeEvent public static void filmImmune(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isHero(p) && FilmSessions.playing(p.getUUID(), HulkRageSession.ID)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ sync
    private static void send(ServerPlayer p, State s) {
        int flags = (s.hulk ? FLAG_HULK : 0) | (s.rockFlying ? FLAG_ROCK_FLYING : 0) | (s.guardBroken > 0 ? FLAG_GUARD_BROKEN : 0);
        float charge = s.action == PUNCH_CHARGE ? Math.min(1, s.age / (float) CHARGE_MAX) : s.action == LEAP_CHARGE ? Math.min(1, s.age / (float) LEAP_CHARGE_MAX) : s.charge;
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
