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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * Batman's Batmobile (gadget G_BATMOBILE; Arkham Knight design, drawn by BatmobileRenderer). What it does beyond
 * arriving is still to be decided: R calls it and it comes in fast from behind him, brakes, drifts sideways and parks
 * beside him (ARRIVE ticks); it waits there; R again sends it off (LEAVE ticks, accelerating away, then gone). It follows
 * the ground, never collides, cannot be hurt and is never saved with the world.
 */
public final class BatmobileEntity extends Entity {
    public static final int ARRIVING = 0, PARKED = 1, LEAVING = 2;
    public static final int ARRIVE = 44, LEAVE = 46;

    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE_AT = SynchedEntityData.defineId(BatmobileEntity.class, EntityDataSerializers.INT);

    // server: the path
    private UUID ownerId;
    private Vec3 from = Vec3.ZERO, park = Vec3.ZERO;
    private float heading, parkYaw;
    // client: the wheels' turn (radians), from how far it has rolled
    public float spin, spinO;

    public BatmobileEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override protected void defineSynchedData() {
        entityData.define(OWNER, -1);
        entityData.define(PHASE, ARRIVING);
        entityData.define(PHASE_AT, 0);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isInvulnerable() { return true; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    public int ownerId() { return entityData.get(OWNER); }
    public int phase() { return entityData.get(PHASE); }
    /** Ticks into the current phase (partial included on the client). */
    public float phaseAge(float partial) { return (float) (level().getGameTime() - entityData.get(PHASE_AT)) + partial; }
    public boolean leaving() { return phase() == LEAVING; }

    /** Server: set up before it is added: it starts at from heading along heading (yaw), parks at park facing parkYaw. */
    void setup(ServerPlayer owner, Vec3 from, Vec3 park, float heading, float parkYaw) {
        this.ownerId = owner.getUUID();
        this.from = from; this.park = park; this.heading = heading; this.parkYaw = parkYaw;
        entityData.set(OWNER, owner.getId());
        entityData.set(PHASE, ARRIVING);
        entityData.set(PHASE_AT, (int) level().getGameTime());
        moveTo(from.x, ground(from), from.z, heading, 0);
    }
    /** Server: drive off. */
    void leave() {
        if (phase() == LEAVING) return;
        heading = getYRot();
        from = position();
        entityData.set(PHASE, LEAVING);
        entityData.set(PHASE_AT, (int) level().getGameTime());
        sound(ModSounds.BATMAN_CANNON_CHARGE.get(), 1.2f, .55f);
        sound(ModSounds.FX_WHOOSH_HEAVY.get(), 1f, .6f);
    }

    private static float ease(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private double ground(Vec3 at) {
        return level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
    }
    private void sound(SoundEvent s, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), s, SoundSource.NEUTRAL, volume, pitch);
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            spinO = spin;
            Vec3 d = position().subtract(xo, yo, zo);
            Vec3 fwd = new Vec3(-Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(getYRot() * Mth.DEG_TO_RAD));
            spin += (float) (d.dot(fwd) / .62);
            return;
        }
        ServerPlayer owner = ownerId == null ? null : (ServerPlayer) level().getPlayerByUUID(ownerId);
        if (owner == null || !owner.isAlive() || !BatmanController.isHero(owner) || owner.level() != level()) {
            if (phase() != LEAVING) leave();
        }
        int age = (int) (level().getGameTime() - entityData.get(PHASE_AT));
        ServerLevel server = (ServerLevel) level();
        Vec3 fwd = new Vec3(-Mth.sin(heading * Mth.DEG_TO_RAD), 0, Mth.cos(heading * Mth.DEG_TO_RAD));
        switch (phase()) {
            case ARRIVING -> {
                float u = Mth.clamp(age / (float) ARRIVE, 0, 1);
                // Fast in, hard on the brakes; the last third slides sideways into the spot.
                double s = 1 - Math.pow(1 - u, 2.2);
                Vec3 at = from.lerp(park, s);
                float drift = ease((u - .62f) / .38f);
                float yaw = heading + Mth.wrapDegrees(parkYaw - heading) * drift;
                setPos(at.x, Mth.lerp(.5, getY(), ground(at)), at.z);
                setYRot(yaw);
                // The turbine burning on the way in; smoke off the tyres in the slide.
                Vec3 back = position().subtract(fwd.scale(3.1)).add(0, .9, 0);
                if (u < .75f) server.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 2, .08, .08, .08, .01);
                if (drift > 0 && drift < 1) {
                    Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                    for (int k = -1; k <= 1; k += 2) {
                        Vec3 w = position().add(side.scale(k * 1.6)).subtract(fwd.scale(2));
                        server.sendParticles(ParticleTypes.LARGE_SMOKE, w.x, w.y + .2, w.z, 2, .2, .05, .2, .01);
                        server.sendParticles(ParticleTypes.CLOUD, w.x, w.y + .1, w.z, 1, .3, .02, .3, .02);
                    }
                }
                if (age == 18) sound(ModSounds.FX_WHOOSH_HEAVY.get(), 1.3f, .55f);
                if (age == (int) (ARRIVE * .62f)) sound(SoundEvents.GRAVEL_BREAK, 1.2f, .5f);
                if (age >= ARRIVE) {
                    entityData.set(PHASE, PARKED);
                    entityData.set(PHASE_AT, (int) level().getGameTime());
                    sound(ModSounds.FX_IMPACT_HEAVY.get(), .6f, .55f);
                    sound(SoundEvents.PISTON_CONTRACT, .5f, .5f);
                }
            }
            case PARKED -> {
                setPos(getX(), Mth.lerp(.3, getY(), ground(position())), getZ());
                // The turbine ticking over: a faint shimmer of heat now and then.
                if (age % 8 == 0) {
                    Vec3 back = position().subtract(new Vec3(-Mth.sin(getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(getYRot() * Mth.DEG_TO_RAD)).scale(3.15)).add(0, .9, 0);
                    server.sendParticles(ParticleTypes.SMOKE, back.x, back.y, back.z, 1, .05, .05, .05, .005);
                }
            }
            default -> {
                // Off it goes, faster and faster, then gone.
                double d = .022 * age * age;
                Vec3 at = from.add(fwd.scale(d));
                setPos(at.x, Mth.lerp(.5, getY(), ground(at)), at.z);
                setYRot(heading);
                Vec3 back = position().subtract(fwd.scale(3.1)).add(0, .9, 0);
                server.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 3, .1, .1, .1, .02);
                if (age >= LEAVE) discard();
            }
        }
    }
}
