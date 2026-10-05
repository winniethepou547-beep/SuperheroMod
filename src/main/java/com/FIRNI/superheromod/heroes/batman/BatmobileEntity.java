package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Batman's Batmobile (Arkham Knight design, drawn by BatmobileRenderer) in the remote takedown: called by the tracker,
 * it comes in fast, sweeps round the front of the one Batman holds in a crescent slide with its guns raking them, and
 * boosts away. Its whole run is TakedownPath.Run on its own clock, worked out the same on the server and every client
 * from the synced centre, front and side (no position packets needed); the server alone fires for real (damage, sound).
 * It never collides, cannot be hurt and is never saved.
 */
public final class BatmobileEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> START = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIDE = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> CX = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CY = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CZ = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FRONT = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.FLOAT);

    // server: who it serves, who it fires at
    private ServerPlayer owner;
    private LivingEntity target;
    private float damagePerHit;
    // client: the wheels' turn (radians), from how far it has rolled
    public float spin, spinO;

    public BatmobileEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override protected void defineSynchedData() {
        entityData.define(OWNER, -1);
        entityData.define(TARGET, -1);
        entityData.define(START, 0);
        entityData.define(SIDE, 1);
        entityData.define(CX, 0f); entityData.define(CY, 0f); entityData.define(CZ, 0f);
        entityData.define(FRONT, 0f);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    public int ownerId() { return entityData.get(OWNER); }
    public int targetId() { return entityData.get(TARGET); }
    /** Its run (null until it has been set up and synced). */
    public TakedownPath.Run run() {
        if (entityData.get(START) == 0) return null;
        float f = entityData.get(FRONT);
        Vec3 front = new Vec3(-Mth.sin(f * Mth.DEG_TO_RAD), 0, Mth.cos(f * Mth.DEG_TO_RAD));
        return new TakedownPath.Run(new Vec3(entityData.get(CX), entityData.get(CY), entityData.get(CZ)), front, entityData.get(SIDE));
    }
    /** Its clock (ticks since it was called; partial included on the client). */
    public float clock(float partial) { return (float) (level().getGameTime() - entityData.get(START)) + partial; }

    /** Server: set up before it is added: the run round the target's feet, facing front (yaw), going round on side. */
    void setup(ServerPlayer owner, LivingEntity target, Vec3 centre, float frontYaw, int side, float damagePerHit) {
        this.owner = owner; this.target = target; this.damagePerHit = damagePerHit;
        entityData.set(OWNER, owner.getId());
        entityData.set(TARGET, target.getId());
        entityData.set(START, (int) level().getGameTime());
        entityData.set(SIDE, side);
        entityData.set(CX, (float) centre.x); entityData.set(CY, (float) centre.y); entityData.set(CZ, (float) centre.z);
        entityData.set(FRONT, frontYaw);
        TakedownPath.Run run = run();
        Vec3 at = run.pos(0);
        moveTo(at.x, centre.y, at.z, run.yaw(0), 0f);
    }

    /** The ground under a point of its run: the real ground if it is near their feet' height, else their feet' height. */
    private double ground(Vec3 at, double feet) {
        double y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        return Math.abs(y - feet) < 2.5 ? y : feet;
    }
    private void place(TakedownPath.Run run, float c) {
        Vec3 at = run.pos(c);
        setPos(at.x, Mth.lerp(.5, getY(), ground(at, run.centre().y)), at.z);
        setYRot(run.yaw(c));
    }
    private void sound(SoundEvent s, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), s, SoundSource.NEUTRAL, volume, pitch);
    }

    @Override public void tick() {
        super.tick();
        TakedownPath.Run run = run();
        if (run == null) return;
        float c = clock(0);
        if (level().isClientSide) {
            // Its own copy of the run, smooth between ticks (where it was a tick ago, where it is now).
            spinO = spin;
            place(run, c - 1);
            xo = getX(); yo = getY(); zo = getZ(); yRotO = getYRot();
            place(run, c);
            Vec3 moved = position().subtract(xo, yo, zo);
            Vec3 nose = run.nose(c);
            spin += (float) (moved.dot(nose) / .62);
            return;
        }
        place(run, c);
        int tick = (int) c;
        ServerLevel server = (ServerLevel) level();
        if (tick >= TakedownPath.GONE) { discard(); return; }
        if (tick == 1) sound(ModSounds.BATMAN_BM_ENGINE.get(), 2.2f, 1f);
        if (tick == TakedownPath.IN - 3) { sound(ModSounds.BATMAN_BM_DRIFT.get(), 2f, 1f); sound(ModSounds.FX_WHOOSH_HEAVY.get(), 1.4f, .6f); }
        if (tick == TakedownPath.IN + TakedownPath.ARC - 1) { sound(ModSounds.BATMAN_BM_BOOST.get(), 2.4f, 1f); sound(ModSounds.BATMAN_CANNON_CHARGE.get(), 1.2f, .5f); }
        // The slide: smoke off the back tyres (the clients draw their own as well).
        if (TakedownPath.slide(c) > .3f && tick % 2 == 0) {
            Vec3 nose = run.nose(c), left = new Vec3(nose.z, 0, -nose.x);
            for (int k = -1; k <= 1; k += 2) {
                Vec3 w = position().add(left.scale(k * 1.6)).subtract(nose.scale(2));
                server.sendParticles(ParticleTypes.LARGE_SMOKE, w.x, w.y + .2, w.z, 1, .2, .05, .2, .01);
            }
        }
        // The guns: a round a tick at the one Batman holds; every other one tells (the body takes it).
        if (TakedownPath.fires(tick)) {
            sound(ModSounds.BATMAN_BM_GUN.get(), 1.6f, .9f + .2f * random.nextFloat());
            boolean live = owner != null && target != null && target.isAlive() && !target.isRemoved() && owner.isAlive();
            if (live && (tick - TakedownPath.FIRE_FROM) % 2 == 1) {
                BatmanController.hurt(owner, target, damagePerHit);
                target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
                target.hurtMarked = true;
                level().playSound(null, target.getX(), target.getY() + 1, target.getZ(), ModSounds.FX_IMPACT_METAL.get(), SoundSource.PLAYERS, .7f, 1.2f + .3f * random.nextFloat());
            }
        }
    }
}
