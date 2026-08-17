package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BeamSyncPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

public class OpticAscentAbility extends Ability {

    public OpticAscentAbility() {
        super("cyclops_optic_ascent", AbilityType.INSTANT, AbilitySlot.SKILL_F);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 120);
        config.set("launchForce", 1.3);
        config.set("aoeRadius", 4.0);
        config.set("aoeKnockback", 0.9);
        config.set("aoeDamage", 4.0f);
        config.set("downBeamLength", 12.0);
        config.set("beamTicks", 7);
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        AbilityConfig cfg = getConfig();
        double launchForce = cfg.getDouble("launchForce", 1.3);
        double aoeRadius = cfg.getDouble("aoeRadius", 4.0);
        double aoeKb = cfg.getDouble("aoeKnockback", 0.9);
        float aoeDmg = cfg.getFloat("aoeDamage", 4.0f);
        double maxLen = cfg.getDouble("downBeamLength", 12.0);
        int beamTicks = cfg.getInt("beamTicks", 7);

        ServerLevel level = (ServerLevel) player.level();
        Vec3 pos = player.position();

        level.playSound(null, player.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0f, 0.6f);

        // Isin gozden cikip yere carpsin. Duz asagi olursa oyuncunun govdesinin
        // icinde kalip gorunmuyordu; hafif one kaydirip yere kadar raycast ediyoruz.
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 look = player.getLookAngle();
        Vec3 fwdFlat = new Vec3(look.x, 0, look.z);
        if (fwdFlat.lengthSqr() < 1.0E-4) fwdFlat = new Vec3(0, 0, 1);
        fwdFlat = fwdFlat.normalize();

        Vec3 beamOrigin = eyePos.add(fwdFlat.scale(0.35));
        Vec3 down = new Vec3(0, -1, 0);

        RaycastResult ground = RaycastSystem.cast(
                level, player, beamOrigin, down, maxLen, 0.4f, false,
                e -> e instanceof LivingEntity && e != player);

        Vec3 beamEnd = ground.getHitPosition();

        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                BeamSyncPacket.flash(beamOrigin, beamEnd, 1.0f, beamTicks));

        CyclopsBeamRenderer.renderOriginFlash(level, beamOrigin);
        CyclopsBeamRenderer.renderBeamParticles(level, beamOrigin, beamEnd);
        CyclopsBeamRenderer.renderImpact(level, beamEnd);

        Vec3 currentVel = player.getDeltaMovement();
        player.setDeltaMovement(currentVel.x * 0.3, launchForce, currentVel.z * 0.3);
        player.hurtMarked = true;

        level.sendParticles(ParticleTypes.SMOKE,
                pos.x, pos.y, pos.z,
                15, 0.6, 0.2, 0.6, 0.05);

        AABB aoe = player.getBoundingBox().inflate(aoeRadius);
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, aoe,
                e -> e != player && e.distanceTo(player) <= aoeRadius);

        for (LivingEntity target : nearby) {
            target.hurt(player.damageSources().playerAttack(player), aoeDmg);
            Vec3 pushDir = target.position().subtract(player.position()).normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(
                    pushDir.x * aoeKb, 0.3, pushDir.z * aoeKb));
            target.hurtMarked = true;
        }
    }
}
