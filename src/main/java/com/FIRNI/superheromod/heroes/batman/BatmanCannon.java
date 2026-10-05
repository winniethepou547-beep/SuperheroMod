package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanConfig.f;

/**
 * The WayneTech dual wrist cannon on the server (gadget G_CANNON, action CANNON): both gauntlets open, four seconds of
 * aimed rapid fire from the wrists that follows his crosshair, then they cool and close. Effects go out as
 * BatmanFxPacket kinds FX_CANNON_FIRST..FX_CANNON_LAST (drawn by BatmanCannonFx).
 * <p>
 * Not a firearm: short bolts of yellow energy that knock people about and wear them down (a small damage per hit, a cap
 * per target per volley), push harder the lighter they are armoured, and slow them as the hits pile up. Every shot is
 * aimed afresh from his eyes along his look, scattered a little round the crosshair; the bolts are drawn leaving his
 * wrists. One packet per tick carries up to two shots (where they ended and what they hit); the clients play each shot's
 * flash, sound and camera tick together from it.
 * <p>
 * Timeline (ticks from the start, the action clock): the gauntlets open over CANNON_DEPLOY (the left a little after the
 * right); then, counted from the start of fire: charging to FIRST_AT, the first bright pair of shots at FIRST_AT, the
 * stream builds to full rate by STEADY_AT, slows from SLOW_AT, the final heavy discharge from both hands at FINAL_AT;
 * after CANNON_FIRE the emitters cool (FX_COOL) and from RETRACT_AT into the retract phase the parts close (FX_RETRACT).
 */
public final class BatmanCannon {
    private BatmanCannon() {}

    // ------------------------------------------------------------------ effect kinds (BatmanFxPacket.kind)
    /** Deploy began; fire is charging; shots (one or two); fire over, cooling; the parts begin to close. */
    public static final int FX_DEPLOY = FX_CANNON_FIRST, FX_CHARGE = FX_CANNON_FIRST + 1, FX_SHOT = FX_CANNON_FIRST + 2,
            FX_COOL = FX_CANNON_FIRST + 3, FX_RETRACT = FX_CANNON_FIRST + 4;
    /**
     * A shot packet: pos = where the first shot ended, dir = where the second ended minus the first, entity = the body
     * the first hit (or -1), id = the Batman. power holds the flags as a whole number: SHOT_TWO (two shots), SHOT_LEFT
     * (the first left the left hand; the second always leaves the other), the first and second shots' hit kinds
     * (HIT_* at bits HIT_A and HIT_B), SHOT_FIRST (the opening pair), SHOT_FINAL (the last heavy pair).
     */
    public static final int SHOT_TWO = 1, SHOT_LEFT = 2, HIT_A = 2, HIT_B = 4, SHOT_FIRST = 64, SHOT_FINAL = 128;
    public static final int HIT_NONE = 0, HIT_BLOCK = 1, HIT_BODY = 2, HIT_ARMOUR = 3;
    public static int hitA(int flags) { return flags >> HIT_A & 3; }
    public static int hitB(int flags) { return flags >> HIT_B & 3; }

    // ------------------------------------------------------------------ the beats of the fire (ticks from the start of fire)
    /** 0..CHARGE_TICKS charging, the first bright pair at FIRST_AT, full rate from STEADY_AT, slowing from SLOW_AT, the final discharge at FINAL_AT. */
    public static final int CHARGE_TICKS = 3, FIRST_AT = 3, STEADY_AT = 6, SLOW_AT = CANNON_FIRE - 6, FINAL_AT = CANNON_FIRE - 1;
    /** Into the retract phase: the emitters keep glowing GLOW_HOLD ticks; the parts begin to close at RETRACT_AT. */
    public static final int GLOW_HOLD = 3, RETRACT_AT = 6;

    /** Armour points from which a target counts as armoured (more sparks, ricochets, less push). */
    static final int ARMOURED = 10;
    /** The damage of a run of hits is dealt together every FLUSH ticks (a hurt sound and flash a few times a second, not every shot). */
    static final int FLUSH = 4;
    /** The final discharge: damage and push of each of its two shots, times a normal shot's; how long it staggers. */
    static final float FINAL_DAMAGE = 3, FINAL_PUSH = 4;
    static final int FINAL_STAGGER = 20;
    /** Horizontal speed the pushes may build up to (blocks/tick), and a little more on the final discharge. */
    static final double PUSH_CAP = .55, FINAL_CAP = .95;
    /** Hits within one FLUSH from which the target is slowed harder. */
    static final int SLOW_HITS = 4;

    /** One volley of one Batman. */
    private static final class Fire {
        double owedShots; int side;
        /** Per target: damage owed since the last flush, hits since then, damage dealt this volley; finished by the final discharge. */
        final Map<LivingEntity, float[]> owed = new LinkedHashMap<>();
        final Set<LivingEntity> finalHit = new HashSet<>();
    }
    private static final Map<UUID, Fire> FIRES = new HashMap<>();

    /** R with the cannon picked: starts it (true) or says why not (false: no cooldown is spent). */
    static boolean use(ServerPlayer p, BatmanController.State s) {
        if (s.gliding) { BatmanController.tell(p, "Süzülürken bilek topu kullanılamaz"); return false; }
        BatmanController.set(s, CANNON);
        FIRES.put(p.getUUID(), new Fire());
        BatmanController.fx(p, FX_DEPLOY, p.position(), Vec3.ZERO, 0, -1, p.getId());
        // The servos, the plates sliding, the locks; a little armour clink under it.
        BatmanController.sound(p, ModSounds.BATMAN_CANNON_DEPLOY.get(), 1f, 1f);
        BatmanController.sound(p, SoundEvents.ARMOR_EQUIP_IRON, .5f, 1.6f);
        return true;
    }

    /** Every tick of the CANNON action (s.age = ticks since it began). */
    static void tick(ServerPlayer p, BatmanController.State s) {
        Fire f = FIRES.computeIfAbsent(p.getUUID(), k -> new Fire());
        int fire = s.age - CANNON_DEPLOY;
        // The two gauntlets lock one after the other.
        if (s.age == CANNON_DEPLOY - 4) BatmanController.sound(p, SoundEvents.IRON_TRAPDOOR_CLOSE, .35f, 1.9f);
        if (s.age == CANNON_DEPLOY - 1) BatmanController.sound(p, SoundEvents.IRON_TRAPDOOR_CLOSE, .3f, 2f);
        if (fire == 0) {
            BatmanController.fx(p, FX_CHARGE, p.position(), Vec3.ZERO, 0, -1, p.getId());
            BatmanController.sound(p, ModSounds.BATMAN_CANNON_CHARGE.get(), .9f, 1f);
            BatmanController.sound(p, SoundEvents.BEACON_POWER_SELECT, .25f, 2f);
        }
        if (fire >= FIRST_AT && fire <= FINAL_AT) shoot(p, f, fire);
        if (fire > 0 && (fire % FLUSH == 0 || fire == FINAL_AT)) flush(p, f);
        if (fire == CANNON_FIRE) {
            BatmanController.fx(p, FX_COOL, p.position(), Vec3.ZERO, 0, -1, p.getId());
            BatmanController.sound(p, ModSounds.BATMAN_CANNON_STOP.get(), .9f, 1f);
            BatmanController.sound(p, SoundEvents.FIRE_EXTINGUISH, .25f, 1.8f);
        }
        if (fire == CANNON_FIRE + RETRACT_AT) {
            BatmanController.fx(p, FX_RETRACT, p.position(), Vec3.ZERO, 0, -1, p.getId());
            BatmanController.sound(p, ModSounds.BATMAN_CANNON_RETRACT.get(), .9f, 1f);
            BatmanController.sound(p, SoundEvents.ARMOR_EQUIP_IRON, .4f, 1.3f);
        }
        if (s.age >= CANNON_TICKS - 1) { flush(p, f); FIRES.remove(p.getUUID()); }
    }

    // ------------------------------------------------------------------ the shots
    private record Shot(Vec3 end, int hit, LivingEntity body) {}

    /** This tick's shots: the opening pair, the stream at the configured rate (building up, slowing at the end), the final pair. */
    private static void shoot(ServerPlayer p, Fire f, int fire) {
        int count, flags = 0;
        if (fire == FIRST_AT) { count = 2; flags = SHOT_FIRST; f.side = 0; }
        else if (fire == FINAL_AT) { count = 2; flags = SHOT_FINAL; f.side = 0; }
        else {
            double rate = BatmanConfig.CANNON_SHOTS_PER_SECOND.get() / 20.0;
            double k = fire < STEADY_AT ? .5 : fire >= SLOW_AT ? 1 - .75 * (fire - SLOW_AT) / (double) (FINAL_AT - SLOW_AT) : 1;
            f.owedShots += rate * k;
            count = Math.min(4, (int) f.owedShots);
            f.owedShots -= count;
        }
        if (count <= 0) return;
        boolean last = (flags & SHOT_FINAL) != 0;
        List<Shot> shots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) shots.add(fireOne(p, f, last));
        // Two shots to a packet; the first of each pair leaves the hand whose turn it is (the turn passes on), the second the other.
        for (int i = 0; i < shots.size(); i += 2) {
            Shot a = shots.get(i), b = i + 1 < shots.size() ? shots.get(i + 1) : null;
            int bits = flags | (f.side == 1 ? SHOT_LEFT : 0) | a.hit << HIT_A;
            if (b != null) bits |= SHOT_TWO | b.hit << HIT_B;
            f.side = 1 - f.side;
            Vec3 second = b == null ? Vec3.ZERO : b.end.subtract(a.end);
            BatmanController.fx(p, FX_SHOT, a.end, second, bits, a.body == null ? (b == null || b.body == null ? -1 : b.body.getId()) : a.body.getId(), p.getId());
        }
    }

    /** One shot from his eyes along his look (scattered a little): the first block or body on the line takes it. */
    private static Shot fireOne(ServerPlayer p, Fire f, boolean last) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        double spread = Math.toRadians(BatmanConfig.CANNON_SPREAD.get()) * (last ? .3 : 1);
        double gx = Mth.clamp(p.getRandom().nextGaussian(), -2.2, 2.2) * spread * .6, gy = Mth.clamp(p.getRandom().nextGaussian(), -2.2, 2.2) * spread * .6;
        Vec3 dir = look.add(right.scale(gx)).add(up.scale(gy)).normalize();
        Vec3 end = eye.add(dir.scale(BatmanConfig.CANNON_RANGE.get()));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, p, eye, stop, new AABB(eye, stop).inflate(1),
                e -> e instanceof LivingEntity l && BatmanController.targetable(p, l));
        if (eh != null && eh.getEntity() instanceof LivingEntity t) {
            boolean armoured = t.getArmorValue() >= ARMOURED;
            hit(p, f, t, dir, last);
            return new Shot(eh.getLocation(), armoured ? HIT_ARMOUR : HIT_BODY, t);
        }
        if (bh.getType() != HitResult.Type.MISS) {
            // A few of the block hits are heard on the server too (the block's own sound, quietly).
            if (p.getRandom().nextInt(3) == 0) {
                var state = level.getBlockState(bh.getBlockPos());
                BatmanController.at(p, stop, state.getSoundType().getHitSound(), .35f, 1.3f + p.getRandom().nextFloat() * .3f);
            }
            return new Shot(stop, HIT_BLOCK, null);
        }
        return new Shot(stop, HIT_NONE, null);
    }

    /** A hit on a body: the damage is owed until the next flush; the push adds up at once (less the more armour they wear). */
    private static void hit(ServerPlayer p, Fire f, LivingEntity t, Vec3 dir, boolean last) {
        float[] owed = f.owed.computeIfAbsent(t, k -> new float[3]);
        owed[0] += f(BatmanConfig.CANNON_DAMAGE) * (last ? FINAL_DAMAGE : 1);
        owed[1]++;
        if (last) f.finalHit.add(t);
        double resist = Math.max(0, 1 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) / (1 + t.getArmorValue() / 8.0);
        double push = BatmanConfig.CANNON_KNOCK.get() * resist * (last ? FINAL_PUSH : 1);
        Vec3 flat = BatmanController.flat(dir), m = t.getDeltaMovement();
        double nx = m.x + flat.x * push, nz = m.z + flat.z * push, h = Math.sqrt(nx * nx + nz * nz), cap = last ? FINAL_CAP : PUSH_CAP;
        if (h > cap) { nx *= cap / h; nz *= cap / h; }
        t.setDeltaMovement(nx, last && t.onGround() ? Math.max(m.y, .16 * resist) : m.y, nz);
        t.hurtMarked = true;
    }

    /**
     * The owed damage dealt in one go (up to the volley's cap per target); the game's own knockback of the hurt is taken
     * back out (the pushes above are the cannon's own); the more hits in the run, the slower they get; the final
     * discharge staggers.
     */
    private static void flush(ServerPlayer p, Fire f) {
        float cap = f(BatmanConfig.CANNON_MAX_DAMAGE);
        for (var en : f.owed.entrySet()) {
            LivingEntity t = en.getKey();
            float[] o = en.getValue();
            if (o[1] <= 0) continue;
            if (t.isAlive() && BatmanController.targetable(p, t)) {
                float dmg = Math.min(o[0], Math.max(0, cap - o[2]));
                if (dmg > 0) {
                    Vec3 v = t.getDeltaMovement();
                    BatmanController.hurt(p, t, dmg);
                    t.setDeltaMovement(v);
                    t.hurtMarked = true;
                    o[2] += dmg;
                }
                boolean armoured = t.getArmorValue() >= ARMOURED;
                int amp = o[1] >= SLOW_HITS && !armoured ? 1 : 0;
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, amp, false, false, true));
                if (f.finalHit.remove(t)) BatmanStagger.apply(t, FINAL_STAGGER);
            }
            o[0] = 0; o[1] = 0;
        }
        f.owed.keySet().removeIf(t -> !t.isAlive() || t.isRemoved());
    }
}
