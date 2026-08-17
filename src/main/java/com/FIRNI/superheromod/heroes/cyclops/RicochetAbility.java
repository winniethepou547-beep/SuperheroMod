package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BeamSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Seeker Ricochet (C) — duz uzun bir lazer atar. Carpma noktasinin 10 blok
 * yaricapindaki canlilara otomatik kilitlenir; isin oraya dogru uzayip
 * dallanir. Birden fazla hedef varsa hedef sayisi kadar dala ayrilir.
 * Arkasinda kalan iz 2 saniyede yavasca kaybolur.
 */
public class RicochetAbility extends Ability {

    public RicochetAbility() {
        super("cyclops_ricochet", AbilityType.INSTANT, AbilitySlot.SKILL_C);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 140);
        config.set("baseDamage", 8.0f);
        config.set("range", 50.0);
        config.set("beamRadius", 0.35f);
        config.set("knockback", 1.0);
        config.set("maxBranches", 4);
        config.set("branchFalloff", 0.75f);
        config.set("trailTicks", 40);   // 2 saniye
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        AbilityConfig cfg = getConfig();
        float baseDamage = cfg.getFloat("baseDamage", 8.0f);
        double range = cfg.getDouble("range", 50.0);
        float beamRadius = cfg.getFloat("beamRadius", 0.35f);
        double knockback = cfg.getDouble("knockback", 1.0);
        int maxBranches = cfg.getInt("maxBranches", 4);
        float branchFalloff = cfg.getFloat("branchFalloff", 0.75f);
        int trailTicks = cfg.getInt("trailTicks", 40);

        ServerLevel level = (ServerLevel) player.level();

        level.playSound(null, player.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2f, 0.8f);

        Vec3 origin = RaycastSystem.getEyeOrigin(player);
        Vec3 direction = RaycastSystem.getLookDirection(player);

        // 1) Duz ana isin
        RaycastResult primary = RaycastSystem.cast(
                level, player, origin, direction, range, beamRadius, false,
                e -> e instanceof LivingEntity && e != player);

        Vec3 impact = primary.getHitPosition();

        Set<UUID> hitSet = new HashSet<>();
        List<Vec3> mainPath = new ArrayList<>();
        mainPath.add(origin);
        mainPath.add(impact);

        ModNetworking.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                BeamSyncPacket.flashPath(mainPath, 0.5f, trailTicks, true));

        CyclopsBeamRenderer.renderOriginFlash(level, origin);
        CyclopsBeamRenderer.renderImpact(level, impact);

        // Ana isin dogrudan birine denk geldiyse once ona vur
        for (RaycastResult.EntityHit hit : primary.getEntityHits()) {
            if (hit.getEntity() instanceof LivingEntity le && hitSet.add(le.getUUID())) {
                damage(player, le, baseDamage, direction, knockback);
            }
        }

        // 2) Carpma noktasinin 10 blok icindeki canlilara dallan
        List<LivingEntity> targets = BeamChainBuilder.findAllNear(
                level, player, impact, hitSet, maxBranches);

        if (targets.isEmpty()) return;

        level.playSound(null, player.blockPosition(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9f, 1.7f);

        for (LivingEntity target : targets) {
            if (!hitSet.add(target.getUUID())) continue;

            Vec3 targetPos = target.getEyePosition();

            List<Vec3> branch = new ArrayList<>();
            branch.add(impact);
            branch.add(targetPos);

            ModNetworking.CHANNEL.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    BeamSyncPacket.flashPath(branch, 0.45f, trailTicks, true));

            Vec3 branchDir = targetPos.subtract(impact).normalize();
            damage(player, target, baseDamage * branchFalloff, branchDir, knockback);

            CyclopsBeamRenderer.renderImpact(level, targetPos);
        }
    }

    private void damage(ServerPlayer player, LivingEntity target,
                        float amount, Vec3 dir, double knockback) {
        target.hurt(player.damageSources().playerAttack(player), amount);
        target.setDeltaMovement(target.getDeltaMovement().add(
                dir.x * knockback, 0.2, dir.z * knockback));
        target.hurtMarked = true;
    }
}
