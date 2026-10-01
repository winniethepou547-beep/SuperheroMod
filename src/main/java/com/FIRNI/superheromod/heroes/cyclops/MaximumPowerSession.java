package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.film.FilmSessions;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ShockwavePacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * MAXIMUM POWER (X), what happens in the world while the film plays: both are held face to face,
 * the blast lands when the film's explosion goes off in the fog, and when the film hands back
 * to the game the target is hurled away down the line of the beam.
 */
public final class MaximumPowerSession implements FilmSessions.Script {
    public static final String ID = "cyclops:maximum_power";
    /** The far explosion (damage), the hand-back to the world (launch), the end of the film. */
    public static final int BLAST = 366, RELEASE = 392, TOTAL = 420;
    /** Share of the target's maximum health the blast takes. */
    private static final float DAMAGE_SHARE = .85f;
    public static final MaximumPowerSession INSTANCE = new MaximumPowerSession();

    public static boolean start(ServerPlayer player, LivingEntity target) { return FilmSessions.start(player, target, INSTANCE); }

    @Override public String film() { return ID; }
    @Override public int total() { return TOTAL; }
    @Override public int release() { return RELEASE; }
    @Override public void tick(ServerPlayer p, LivingEntity target, int age, Vec3 forward) {
        var level = p.serverLevel();
        if (age == BLAST && target.isAlive()) {
            target.invulnerableTime = 0;
            target.hurt(p.damageSources().playerAttack(p), target.getMaxHealth() * DAMAGE_SHARE);
        }
        if (age == RELEASE) {
            Vec3 at = target.position();
            if (target.isAlive()) {
                target.setDeltaMovement(forward.scale(2.4).add(0, .75, 0));
                target.hurtMarked = true;
            }
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.6f, .7f);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, .8f, 1.3f);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1, at.z, 40, .6, .6, .6, .12);
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new ShockwavePacket(at, 5, 16, .8f));
        }
    }
}
