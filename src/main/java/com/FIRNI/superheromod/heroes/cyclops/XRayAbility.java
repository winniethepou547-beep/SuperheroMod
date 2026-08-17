package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.ability.Ability;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.core.ability.AbilityType;
import com.FIRNI.superheromod.core.cinematic.CinematicDirector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * MAXIMUM POWER (X) — sinematik bitirme hamlesi.
 * Koreografi MaximumPowerCinematic'te, oynatma CinematicDirector'da.
 */
public class XRayAbility extends Ability {

    private static final double LOCK_RANGE = 18.0;

    public XRayAbility() {
        super("cyclops_maximum_power", AbilityType.INSTANT, AbilitySlot.SKILL_X);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 600);
    }

    /**
     * Hedef kontrolu burada: onActivate icinde iptal etmek ise yaramiyor,
     * cunku Ability.activate() sonrasinda startCooldown() cagriliyor.
     */
    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;
        if (CinematicDirector.isBusy(player.getUUID())) return false;

        if (findTarget(player) == null) {
            player.displayClientMessage(
                    Component.literal("§cMAXIMUM POWER: menzilde hedef yok"), true);
            return false;
        }
        return true;
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        LivingEntity target = findTarget(player);
        if (target == null) return;
        CinematicDirector.start(MaximumPowerCinematic.ID, player, target);
    }

    /** Bakis yonundeki en uygun hedef. */
    public static LivingEntity findTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getLookAngle();

        AABB box = player.getBoundingBox().inflate(LOCK_RANGE);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(
                LivingEntity.class, box, e -> e != player && e.isAlive());

        LivingEntity best = null;
        double bestScore = -1;

        for (LivingEntity e : candidates) {
            Vec3 to = e.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist > LOCK_RANGE || dist < 0.5) continue;

            double dot = to.normalize().dot(look);
            if (dot < 0.35) continue;

            double score = dot / (1.0 + dist * 0.05);
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }
}
