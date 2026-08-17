package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
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

public class OpticBlastAbility extends Ability {

    public OpticBlastAbility() {
        super("cyclops_optic_blast", AbilityType.INSTANT, AbilitySlot.LMB);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 8);
        config.set("damage", 2.0f);      // 1 kalp
        config.set("range", 40.0);
        config.set("knockback", 0.6);
        config.set("beamRadius", 0.3f);
        // Yuzeye carpinca acilan minik patlama
        config.set("breakRadius", 1.15);
        config.set("breakMaxBlocks", 4);
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        float damage = getConfig().getFloat("damage", 2.0f);
        double range = getConfig().getDouble("range", 40.0);
        double knockback = getConfig().getDouble("knockback", 0.6);
        float beamRadius = getConfig().getFloat("beamRadius", 0.3f);

        Vec3 eyePos = RaycastSystem.getEyeOrigin(player);
        Vec3 lookDir = RaycastSystem.getLookDirection(player);
        Vec3 beamOrigin = eyePos.add(lookDir.scale(0.3));

        RaycastResult result = RaycastSystem.cast(
                player.level(), player, beamOrigin, lookDir, range, beamRadius, false,
                e -> e instanceof LivingEntity && e != player
        );

        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                BeamSyncPacket.flash(beamOrigin, result.getHitPosition(), 0.4f, 2));

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.5f);

        if (result.didHitEntity()) {
            for (RaycastResult.EntityHit hit : result.getEntityHits()) {
                if (hit.getEntity() instanceof LivingEntity target) {
                    target.hurt(player.damageSources().playerAttack(player), damage);
                    Vec3 knockDir = result.getDirection().normalize();
                    target.setDeltaMovement(target.getDeltaMovement().add(
                            knockDir.x * knockback, 0.15, knockDir.z * knockback));
                    target.hurtMarked = true;
                }
            }
        } else if (result.didHitBlock()) {
            // Kimseye denk gelmediyse vurdugu yuzeyi minik patlamayla oy
            SurfaceBreaker.chip((ServerLevel) player.level(), player,
                    result.getHitPosition(),
                    getConfig().getDouble("breakRadius", 1.15),
                    getConfig().getInt("breakMaxBlocks", 4));
        }
    }
}
