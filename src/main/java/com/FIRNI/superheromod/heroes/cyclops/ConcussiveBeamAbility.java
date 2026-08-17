package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.core.resource.ResourceBar;
import com.FIRNI.superheromod.core.resource.ResourceManager;
import com.FIRNI.superheromod.core.resource.ResourceType;
import com.FIRNI.superheromod.core.world.SurfaceBreaker;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BeamSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

public class ConcussiveBeamAbility extends Ability {

    public static final String HEAT_RESOURCE_ID = "cyclops_beam_heat";

    public ConcussiveBeamAbility() {
        super("cyclops_concussive_beam", AbilityType.CHANNELED, AbilitySlot.RMB);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 0);
        config.set("damage", 1.5f);
        config.set("range", 30.0);
        config.set("beamRadius", 0.4f);
        config.set("knockbackHorizontal", 0.35);
        config.set("knockbackVertical", 0.05);
        config.set("knockbackMaxVelocity", 1.8);
        config.set("damageInterval", 5);
        config.set("soundInterval", 10);
        config.set("maxHeat", 100.0f);
        config.set("heatRate", 0.8f);
        config.set("coolRate", 1.0f);
        config.set("coolDelayTicks", 20);
        config.set("reactivationThreshold", 0.0f);
        config.set("overheatLockTicks", 40);
        config.set("beamForwardOffset", 0.3);
        config.set("maxBounces", 2);
        config.set("maxChains", 3);
        // Sekerken degdigi yuzeyleri minik patlamayla oyar
        config.set("breakInterval", 5);
        config.set("breakRadius", 1.35);
        config.set("breakMaxBlocks", 7);
        config.set("hoverGravityMultiplier", 0.55);
        config.set("hoverMaxFallSpeed", -0.4);
        config.set("hoverAirControl", 0.04);
        config.set("hoverMaxAirSpeed", 0.25);
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;
        ResourceBar heat = ResourceManager.get(player.getUUID(), HEAT_RESOURCE_ID);
        return heat == null || heat.canUse();
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        ensureHeatBar(player);
    }

    @Override
    protected void onTick(ServerPlayer player, int ticksActive) {
        ResourceBar heat = ResourceManager.get(player.getUUID(), HEAT_RESOURCE_ID);
        if (heat == null) return;

        heat.increase();

        if (!heat.canUse()) {
            forceStop(player);
            return;
        }

        AbilityConfig cfg = getConfig();
        double range = cfg.getDouble("range", 30.0);
        float beamRadius = cfg.getFloat("beamRadius", 0.4f);
        float damage = cfg.getFloat("damage", 1.5f);
        double kbH = cfg.getDouble("knockbackHorizontal", 0.35);
        double kbV = cfg.getDouble("knockbackVertical", 0.05);
        double kbMax = cfg.getDouble("knockbackMaxVelocity", 1.8);
        int dmgInterval = cfg.getInt("damageInterval", 5);
        int sndInterval = cfg.getInt("soundInterval", 10);
        double fwdOffset = cfg.getDouble("beamForwardOffset", 0.3);

        Vec3 eyePos = RaycastSystem.getEyeOrigin(player);
        Vec3 lookDir = RaycastSystem.getLookDirection(player);
        Vec3 beamOrigin = eyePos.add(lookDir.scale(fwdOffset));

        ServerLevel level = (ServerLevel) player.level();

        // Yuzeyden seker, canliya carparsa 10 blok icindeki en yakina kilitlenir
        BeamChainBuilder.Chain chain = BeamChainBuilder.build(
                player, beamOrigin, lookDir, range, beamRadius,
                cfg.getInt("maxBounces", 2), cfg.getInt("maxChains", 3));

        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                BeamSyncPacket.channel(player.getUUID(), chain.path, 1.0f));

        Vec3 endPoint = chain.path.get(chain.path.size() - 1);

        if (ticksActive % 4 == 0) {
            CyclopsBeamRenderer.renderOriginFlash(level, beamOrigin);
        }
        if (ticksActive % 5 == 0) {
            CyclopsBeamRenderer.renderBeamParticles(level, beamOrigin, endPoint);
        }
        if (ticksActive % 2 == 0) {
            CyclopsBeamRenderer.renderBeamBubbles(level, beamOrigin, endPoint, 1.0f);
        }
        if (chain.hits.isEmpty()) {
            CyclopsBeamRenderer.renderImpact(level, endPoint);
        }

        // Isinin degdigi (ve sektigi) her yuzeyi minik patlamayla kir.
        // Her tick kirmak duvarlari aninda yok ediyordu; araliga baglandi.
        int breakInterval = cfg.getInt("breakInterval", 5);
        if (ticksActive % breakInterval == 0) {
            double breakRadius = cfg.getDouble("breakRadius", 1.35);
            int breakMax = cfg.getInt("breakMaxBlocks", 7);
            for (Vec3 surface : chain.surfaces) {
                SurfaceBreaker.chip(level, player, surface, breakRadius, breakMax);
            }
        }

        if (ticksActive % sndInterval == 0) {
            level.playSound(null, player.blockPosition(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6f, 1.8f);
        }

        if (!player.onGround()) {
            applyHover(player, cfg);
        }

        for (LivingEntity target : chain.hits) {
            if (ticksActive % dmgInterval == 0) {
                target.hurt(player.damageSources().playerAttack(player), damage);
            }
            Vec3 push = target.position().subtract(player.position()).normalize();
            applySmoothedKnockback(target, push, kbH, kbV, kbMax);
            CyclopsBeamRenderer.renderImpact(level, target.getEyePosition());
        }
    }

    private void applySmoothedKnockback(LivingEntity target, Vec3 pushDir,
                                        double horizontal, double vertical, double maxVel) {
        Vec3 currentVel = target.getDeltaMovement();
        double newX = currentVel.x + pushDir.x * horizontal;
        double newY = currentVel.y + vertical;
        double newZ = currentVel.z + pushDir.z * horizontal;

        double hSpeed = Math.sqrt(newX * newX + newZ * newZ);
        if (hSpeed > maxVel) {
            double scale = maxVel / hSpeed;
            newX *= scale;
            newZ *= scale;
        }

        target.setDeltaMovement(newX, Math.min(newY, maxVel * 0.5), newZ);
        target.hurtMarked = true;
    }

    @Override
    protected void onChannelStop(ServerPlayer player) {
        // Isi degeri CyclopsHeatSync tarafindan her tick gonderiliyor;
        // burada sifirlamak bara "0'a dus sonra geri zipla" bugunu yaratiyordu.
        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                BeamSyncPacket.stop(player.getUUID()));
    }

    @Override
    protected void onFinish(ServerPlayer player) {
        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                BeamSyncPacket.stop(player.getUUID()));
    }

    private void ensureHeatBar(ServerPlayer player) {
        ResourceBar existing = ResourceManager.get(player.getUUID(), HEAT_RESOURCE_ID);
        if (existing != null) return;
        AbilityConfig cfg = getConfig();
        ResourceBar heat = new ResourceBar(ResourceType.HEAT, "Optic Heat",
                cfg.getFloat("maxHeat", 100.0f), cfg.getFloat("heatRate", 0.8f),
                cfg.getFloat("coolRate", 1.0f), cfg.getInt("coolDelayTicks", 20),
                cfg.getFloat("reactivationThreshold", 0.0f));
        heat.setOverheatLockTicks(cfg.getInt("overheatLockTicks", 40));
        ResourceManager.register(player.getUUID(), HEAT_RESOURCE_ID, heat);
    }

    private void applyHover(ServerPlayer player, AbilityConfig cfg) {
        double gravMult = cfg.getDouble("hoverGravityMultiplier", 0.55);
        double maxFall = cfg.getDouble("hoverMaxFallSpeed", -0.4);
        double airControl = cfg.getDouble("hoverAirControl", 0.04);
        double maxAirSpeed = cfg.getDouble("hoverMaxAirSpeed", 0.25);

        Vec3 vel = player.getDeltaMovement();
        double newY = vel.y < 0 ? Math.max(vel.y * gravMult, maxFall) : vel.y;
        double newX = vel.x;
        double newZ = vel.z;

        float strafe = player.xxa;
        float forward = player.zza;
        if (Math.abs(strafe) > 0.1f || Math.abs(forward) > 0.1f) {
            Vec3 look = player.getLookAngle();
            Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
            Vec3 fwd = new Vec3(look.x, 0, look.z).normalize();
            newX += fwd.x * forward * airControl + side.x * strafe * airControl;
            newZ += fwd.z * forward * airControl + side.z * strafe * airControl;
            double hSpeed = Math.sqrt(newX * newX + newZ * newZ);
            if (hSpeed > maxAirSpeed) {
                double scale = maxAirSpeed / hSpeed;
                newX *= scale;
                newZ *= scale;
            }
        }

        player.setDeltaMovement(newX, newY, newZ);
        player.hurtMarked = true;
        player.fallDistance = 0f;
    }
}
