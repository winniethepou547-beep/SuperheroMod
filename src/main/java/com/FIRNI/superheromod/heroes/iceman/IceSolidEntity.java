package com.FIRNI.superheromod.heroes.iceman;

import com.FIRNI.superheromod.core.entity.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * The body of the ice Iceman leaves standing: an invisible, upright box (no rotation) that the game itself treats as
 * solid, like a boat's or a shulker's. Players and mobs cannot walk through it, they can stand and walk on top of it,
 * arrows glance off it; a punch does nothing to it. The ice you SEE is drawn by the clients (IcemanTrack, the brush
 * sculptures); this box only gives it its weight. Used by the slide's track (IcemanSlide) and the brush's sculptures
 * (IcemanBrush), a row of these per piece of ice.
 * <p>
 * Players move on their own clients, so every client must have the same box: its size and whether it is solid yet are
 * synced (W, H, SOLID), the position comes with the spawn packet and never changes.
 * <p>
 * A box that comes up where a body already is would trap or throw them, so it starts soft and only turns solid once no
 * living body is within its grace margin (Iceman riding the track he lays under his own feet: it hardens as soon as he
 * is past it, and from then on it is solid for him too). It never moves, is never saved, and goes away by itself when
 * its time is up or its Iceman is gone, even if the code that made it forgot it.
 */
public final class IceSolidEntity extends Entity {
    /** The longest any box may stand (ticks), whatever its maker asked for. */
    public static final int MAX_LIFE = 20 * 150;

    private static final EntityDataAccessor<Float> W = SynchedEntityData.defineId(IceSolidEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> H = SynchedEntityData.defineId(IceSolidEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> SOLID = SynchedEntityData.defineId(IceSolidEntity.class, EntityDataSerializers.BOOLEAN);
    /** Who keeps it from hardening: any living body there that is not a spectator. */
    private static final Predicate<LivingEntity> IN_THE_WAY = e -> e.isAlive() && !e.isSpectator();

    /** The synced data is there (getDimensions may be asked before it is, while the entity is being built). */
    private boolean ready;
    // server
    private UUID owner;
    private int age, life = 20 * 30;
    private double grace = .35;

    public IceSolidEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override protected void defineSynchedData() {
        entityData.define(W, 1f);
        entityData.define(H, .35f);
        entityData.define(SOLID, false);
        ready = true;
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    /** Ice for one fight: never written to the world. */
    @Override public boolean shouldBeSaved() { return false; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    // ------------------------------------------------------------------ its size (the same on every client)
    @Override public EntityDimensions getDimensions(Pose pose) {
        if (!ready) return super.getDimensions(pose);
        return EntityDimensions.fixed(Math.max(.05f, entityData.get(W)), Math.max(.05f, entityData.get(H)));
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (W.equals(key) || H.equals(key)) refreshDimensions();
    }

    // ------------------------------------------------------------------ solid
    public boolean solid() { return entityData.get(SOLID); }
    /** The game's own movement treats its box as a wall and a floor for everyone, both sides (once it has hardened). */
    @Override public boolean canBeCollidedWith() { return isAlive() && solid(); }
    /** Arrows and the like hit it (and, not hurting it, glance off); the crosshair stops on it. */
    @Override public boolean isPickable() { return isAlive() && solid(); }
    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity e) {}
    @Override public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }
    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean skipAttackInteraction(Entity by) { return true; }
    @Override public boolean displayFireAnimation() { return false; }
    @Override public boolean canChangeDimensions() { return false; }

    // ------------------------------------------------------------------ made by the server
    /**
     * A new box, its bottom middle at `bottom`, `width` square and `height` tall, standing `life` ticks. It hardens at
     * once unless a living body is within `grace` of it; then as soon as none is. Null if it could not be made.
     */
    static IceSolidEntity spawn(ServerPlayer owner, Vec3 bottom, float width, float height, int life, double grace) {
        if (!(owner.level() instanceof ServerLevel level)) return null;
        IceSolidEntity e = ModEntities.ICE_SOLID.get().create(level);
        if (e == null) return null;
        e.owner = owner.getUUID();
        e.life = Math.max(1, Math.min(MAX_LIFE, life));
        e.grace = Math.max(0, grace);
        e.entityData.set(W, width);
        e.entityData.set(H, height);
        e.moveTo(bottom.x, bottom.y, bottom.z, 0, 0);
        e.setDeltaMovement(Vec3.ZERO);
        e.harden();
        level.addFreshEntity(e);
        return e;
    }
    /** From now on it stands `ticks` more (never past MAX_LIFE in all); 0 or less removes it. */
    void setLife(int ticks) {
        if (ticks <= 0) { gone(); return; }
        life = Math.min(MAX_LIFE, age + ticks);
    }
    /** Taken away now (no-op if it already is). */
    void gone() { if (!isRemoved()) discard(); }
    /** Whether a body's box is inside this one (a body caught in ice laid where it stood). */
    boolean holds(AABB body) { return !isRemoved() && getBoundingBox().intersects(body); }

    /** Hardens if no living body is near enough to be caught by it. */
    private void harden() {
        if (solid()) return;
        AABB near = getBoundingBox().inflate(grace);
        if (level().getEntitiesOfClass(LivingEntity.class, near, IN_THE_WAY).isEmpty()) entityData.set(SOLID, true);
    }

    @Override public void tick() {
        // Nothing of the usual entity tick (fire, water, portals, falling out of the world): it only stands there.
        firstTick = false;
        if (level().isClientSide) return;
        if (++age >= life || owner == null) { discard(); return; }
        if (age % 20 == 0 && !ownerHere()) { discard(); return; }
        if (!solid()) harden();
    }
    /** Its Iceman is still on the server and still Iceman. */
    private boolean ownerHere() {
        if (!(level() instanceof ServerLevel level)) return false;
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
        return p != null && IcemanController.isHero(p);
    }
}
