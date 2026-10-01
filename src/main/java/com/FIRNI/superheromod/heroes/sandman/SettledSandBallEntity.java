package com.FIRNI.superheromod.heroes.sandman;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** A single saved, stationary remnant per rock impact; no per-tick visual packets. */
public final class SettledSandBallEntity extends Entity {
    private float health = 12;
    public SettledSandBallEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        health = tag.contains("SandHealth") ? tag.getFloat("SandHealth") : 12;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putFloat("SandHealth", health); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
    @Override public boolean isPickable() { return true; }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || amount <= 0) return false;
        if (!level().isClientSide && (health -= amount) <= 0) {
            var server = (net.minecraft.server.level.ServerLevel) level();
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                    net.minecraft.core.particles.ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.SAND.defaultBlockState()),
                    getX(), getY()+1.5, getZ(), 32, 1.4, 1.2, 1.4, .08);
            discard();
        }
        return true;
    }
}
