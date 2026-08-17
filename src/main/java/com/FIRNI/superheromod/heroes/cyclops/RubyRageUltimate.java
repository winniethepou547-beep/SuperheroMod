package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.ability.*;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ruby Rage — Cyclops ultisi (Q).
 * Basildiginda nisan moduna girer; asil sekans CyclopsUltimateController'da yurur.
 */
public class RubyRageUltimate extends Ability {

    public RubyRageUltimate() {
        super("cyclops_ruby_rage", AbilityType.INSTANT, AbilitySlot.ULTIMATE);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 600); // 30 saniye
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;
        return !CyclopsUltimateController.isActive(player.getUUID());
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        CyclopsUltimateController.beginAiming(player);
    }
}
