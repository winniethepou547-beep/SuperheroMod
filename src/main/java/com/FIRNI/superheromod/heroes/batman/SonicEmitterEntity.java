package com.FIRNI.superheromod.heroes.batman;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * One of the sonic trap's two emitters (BatmanSonic): a heavy WayneTech device that comes up out of the ground, locks,
 * turns its sonic chamber on the target and pulses it for SONIC_SECONDS, then cools down and sinks back into the earth
 * (or, shot to pieces first, shorts out and blows apart). It never moves and is never saved; it can be hit (melee and
 * projectiles) by anyone but Batman and, without friendly fire, his allies.
 * <p>
 * Everything it looks like follows from the synced numbers alone (who placed it, the target, the game time it was placed,
 * how long it stays active, when it broke, its health), so every client draws the same rise, aim and retraction; the
 * pulses (aimed and decided here) go out as BatmanFxPacket FX_SONIC_PULSE. Its clock: age = ticks since it was placed.
 */
public final class SonicEmitterEntity extends Entity {
    // ------------------------------------------------------------------ the timeline (ticks of age)
    /**
     * PREP: the ground stirs (tremor, soil and stones moving), breaking open at BREAK_AT. RISE: the folded device comes up
     * slow, then the mast drives the chamber up (medium), then the chamber unfolds and the legs slam down (fast), locked at
     * RISEN. DEPLOY: the rings spin up and the core lights. ACTIVE_FROM .. +active: aiming and pulsing. COOL: the light dies,
     * the mechanism slows. RETRACT: folded, the mast down, sunk into the ground. Broken: FAIL ticks of shorting out, then the blast.
     */
    public static final int PREP = 12, BREAK_AT = 6, RISE = 22, RISEN = PREP + RISE, DEPLOY = 6, ACTIVE_FROM = RISEN + DEPLOY,
            COOL = 12, RETRACT = 26, FAIL = 14;
    /** Ticks between two pulses of one emitter (the two take turns, half a period apart). */
    public static final int PULSE_EVERY = 6;
    /** Height of the chamber's pitch axis over the ground when deployed, and from it to the chamber's front face (blocks). */
    public static final double HEAD_Y = 1.5, MUZZLE = .34;

    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(SonicEmitterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(SonicEmitterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BORN = SynchedEntityData.defineId(SonicEmitterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTIVE = SynchedEntityData.defineId(SonicEmitterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BROKEN = SynchedEntityData.defineId(SonicEmitterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIDE = SynchedEntityData.defineId(SonicEmitterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(SonicEmitterEntity.class, EntityDataSerializers.FLOAT);
    /** "Not set" for BORN and BROKEN. */
    public static final int NONE = Integer.MIN_VALUE;

    /** Client: the emitters this client knows (BatmanSonicFx runs their sounds, dust and light from it, and drops removed ones). */
    public static final Set<SonicEmitterEntity> CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

    // server
    private ServerPlayer owner;
    private LivingEntity target;
    // client: the chamber's aim (world yaw in degrees as entity yaw, elevation in radians, up positive), smoothed
    public float aimYaw, aimYawO, aimPitch, aimPitchO;
    private float yawSpeed, pitchSpeed;
    private boolean aimed;
    /** Client: game time of the last pulse fired (the chamber's recoil), set by BatmanSonicFx. */
    public float pulsedAt = -100;

    public SonicEmitterEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override protected void defineSynchedData() {
        entityData.define(OWNER, -1);
        entityData.define(TARGET, -1);
        entityData.define(BORN, NONE);
        entityData.define(ACTIVE, 80);
        entityData.define(BROKEN, NONE);
        entityData.define(SIDE, 1);
        entityData.define(HEALTH, 8f);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    /** A trap for one fight: never written to the world. */
    @Override public boolean shouldBeSaved() { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    /** Server: set up before it is added to the level. side 1 = his right, -1 = his left. */
    void setup(ServerPlayer owner, LivingEntity target, int side, int activeTicks, float health) {
        this.owner = owner;
        this.target = target;
        entityData.set(OWNER, owner.getId());
        entityData.set(TARGET, target.getId());
        entityData.set(BORN, (int) owner.level().getGameTime());
        entityData.set(ACTIVE, Math.max(1, activeTicks));
        entityData.set(SIDE, side);
        entityData.set(HEALTH, health);
    }

    // ------------------------------------------------------------------ what everyone reads
    public int ownerId() { return entityData.get(OWNER); }
    public int targetId() { return entityData.get(TARGET); }
    public int side() { return entityData.get(SIDE); }
    public float health() { return entityData.get(HEALTH); }
    public int activeTicks() { return entityData.get(ACTIVE); }
    public boolean placed() { return entityData.get(BORN) != NONE; }
    /** Ticks since it was placed (smooth with partial on the client); -1 before the data has arrived. */
    public float age(float partial) {
        int born = entityData.get(BORN);
        if (born == NONE) return -1;
        return Math.max(0, (int) level().getGameTime() - born) + partial;
    }
    public int age() { return (int) age(0); }
    /** Ticks since it broke, or -1 when whole. */
    public float brokenAge(float partial) {
        int at = entityData.get(BROKEN);
        if (at == NONE) return -1;
        return Math.max(0, (int) level().getGameTime() - at) + partial;
    }
    public boolean broken() { return entityData.get(BROKEN) != NONE; }
    /** Age at which it stops pulsing, the cooling starts. */
    public int activeEnd() { return ACTIVE_FROM + activeTicks(); }
    public int retractFrom() { return activeEnd() + COOL; }
    public int gone() { return retractFrom() + RETRACT; }
    public boolean active(float age) { return !broken() && age >= ACTIVE_FROM && age < activeEnd(); }
    /** The chamber's pitch axis when deployed (world). */
    public Vec3 head() { return position().add(0, HEAD_Y, 0); }
    /** The middle of the chamber's front face for an aim (yaw in entity degrees, elevation in radians). */
    public Vec3 muzzle(float yaw, float pitch) { return head().add(direction(yaw, pitch).scale(MUZZLE)); }
    public static Vec3 direction(float yaw, float pitch) {
        float y = yaw * Mth.DEG_TO_RAD, c = Mth.cos(pitch);
        return new Vec3(-Mth.sin(y) * c, Mth.sin(pitch), Mth.cos(y) * c);
    }

    // ------------------------------------------------------------------ being hit
    @Override public boolean isPickable() {
        // Only once it is up out of the ground (until it starts sinking back), and not while it is blowing apart.
        float age = age(0);
        return isAlive() && !broken() && age >= PREP + RISE / 2f && age < retractFrom() + RETRACT / 2f;
    }
    @Override public boolean isAttackable() { return true; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity e) {}
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isInvulnerableTo(source) || amount <= 0 || !isPickable()) return false;
        Entity by = source.getEntity();
        if (by != null && friendly(by)) return false;
        float hp = health() - amount;
        entityData.set(HEALTH, Math.max(0, hp));
        Vec3 at = by == null ? head() : head().add(by.getEyePosition().subtract(head()).normalize().scale(.45));
        BatmanSonic.damaged(this, at, hp <= 0);
        if (hp <= 0) entityData.set(BROKEN, (int) level().getGameTime());
        return true;
    }
    /** Batman's own hits never count, nor (without friendly fire) his allies'. */
    private boolean friendly(Entity by) {
        if (by.getId() == ownerId()) return true;
        if (owner != null && by == owner) return true;
        return !BatmanConfig.FRIENDLY_FIRE.get() && owner != null && by instanceof Player other && owner.isAlliedTo(other);
    }

    // ------------------------------------------------------------------ every tick
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) { clientTick(); return; }
        if (!placed()) { discard(); return; }
        int age = age();
        if (broken()) {
            // Shorting out, the blast at FAIL (drawn by the clients); gone a moment after so every client sees it.
            if (brokenAge(0) >= FAIL + 3) discard();
            return;
        }
        if (age >= gone()) { discard(); return; }
        // Batman gone (logged out, died, not Batman any more): it stops and sinks back.
        if (owner == null || owner.isRemoved() || !owner.isAlive() || owner.level() != level() || !BatmanController.isHero(owner)) { stop(age); return; }
        if (age < ACTIVE_FROM || age >= activeEnd()) return;
        if (target == null || !target.isAlive() || target.isRemoved() || target.level() != level()
                || target.distanceTo(this) > BatmanConfig.SONIC_RANGE.get() * 1.5 || !BatmanController.targetable(owner, target)) { stop(age); return; }
        // Pulses: the two emitters take turns, half a period apart.
        int phase = (age - ACTIVE_FROM + (side() > 0 ? 0 : PULSE_EVERY / 2)) % PULSE_EVERY;
        if (phase == 0) pulse();
    }
    /** Cuts the active time short: the cooling starts now (or, still rising, as soon as it is up). */
    private void stop(int age) {
        int active = Math.max(0, age - ACTIVE_FROM);
        if (active < activeTicks()) entityData.set(ACTIVE, active);
    }
    /** One pulse at the target: straight from the chamber; a wall in between takes it instead. No damage. */
    private void pulse() {
        Vec3 head = head(), aim = target.getBoundingBox().getCenter();
        Vec3 dir = aim.subtract(head);
        if (dir.lengthSqr() < 1e-4) return;
        dir = dir.normalize();
        Vec3 from = head.add(dir.scale(MUZZLE));
        var bh = level().clip(new ClipContext(from, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean hit = bh.getType() == HitResult.Type.MISS;
        Vec3 impact = hit ? aim : bh.getLocation();
        if (hit) BatmanSonic.blast(target);
        BatmanSonic.send(this, BatmanSonic.FX_PULSE, from, impact, hit ? 1 : 0, hit ? target.getId() : -1);
    }

    /** Client: smooth, damped aim at the target (a heavy mechanism, not a snap), held level while folded or cooling. */
    private void clientTick() {
        CLIENT.add(this);
        aimYawO = aimYaw;
        aimPitchO = aimPitch;
        if (!aimed) { aimYaw = aimYawO = getYRot(); aimed = true; }
        float age = age(0);
        if (age < 0 || broken()) { yawSpeed *= .5f; pitchSpeed *= .5f; return; }
        float wantYaw = aimYaw, wantPitch = 0;
        Entity t = level().getEntity(targetId());
        boolean tracking = age >= RISEN - 2 && age < activeEnd() + 2;
        if (t != null && tracking) {
            Vec3 d = t.getBoundingBox().getCenter().subtract(head());
            double flat = Math.sqrt(d.x * d.x + d.z * d.z);
            if (flat > .2) wantYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            wantPitch = (float) Mth.clamp(Math.atan2(d.y, Math.max(.2, flat)), -.6, .9);
        }
        // A spring with a lot of damping: it swings onto the aim, overshoots a hair and settles; it keeps tracking.
        float stiff = age < ACTIVE_FROM ? .1f : .2f;
        yawSpeed = yawSpeed * .6f + Mth.wrapDegrees(wantYaw - aimYaw) * stiff;
        pitchSpeed = pitchSpeed * .6f + (wantPitch - aimPitch) * stiff;
        yawSpeed = Mth.clamp(yawSpeed, -14, 14);
        aimYaw += yawSpeed;
        aimPitch += pitchSpeed;
    }
}
