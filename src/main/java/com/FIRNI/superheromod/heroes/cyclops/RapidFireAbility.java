package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.ability.*;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optic Burst (R) — sol tikin projectile hali. Hitscan degil; 10 mermilik
 * seri atis, mermiler gercekten yol alir.
 */
public class RapidFireAbility extends Ability {

    public RapidFireAbility() {
        super("cyclops_rapid_fire", AbilityType.INSTANT, AbilitySlot.SKILL_E);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 60);
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        RapidFireController.startBurst(player);
    }
}
