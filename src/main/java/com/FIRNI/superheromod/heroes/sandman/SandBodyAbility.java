package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.*;
import net.minecraft.server.level.ServerPlayer;

/**
 * SAND BODY — R.
 *
 * Kisa sureligine kum formuna gecer. Savunma degeri yuksek ama tam
 * dokunulmazlik degil; patlama ve alan hasari yine tam vurur.
 */
public class SandBodyAbility extends Ability {

    public SandBodyAbility() {
        super("sandman_sand_body", AbilityType.INSTANT, AbilitySlot.SKILL_E);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 300);      // 15 saniye
        config.set("durationTicks", 100);      // 5 saniye
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;
        return !SandBodyController.isActive(player.getUUID());
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        SandBodyController.start(player, getConfig().getInt("durationTicks", 100));
    }
}
