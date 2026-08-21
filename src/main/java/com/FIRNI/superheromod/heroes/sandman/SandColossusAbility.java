package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.*;
import net.minecraft.server.level.ServerPlayer;

/**
 * SAND COLOSSUS — Sandman'in ultisi (Q).
 *
 * Sandman'in vucudu parcalanir ve 10 blokluk, BACAKSIZ bir kum devine
 * donusur. Alt kismi surekli akan kum kutlesidir.
 *
 * Cok yuksek dayaniklilik, ama vucuttaki KRISTALLER 2 kat hasar alir —
 * rakip sadece can eritmek yerine devin neresini kiracagini secmek zorunda.
 * Detaylar {@link SandColossusController} ve {@link ColossusCrystal} icinde.
 */
public class SandColossusAbility extends Ability {

    public SandColossusAbility() {
        super("sandman_sand_colossus", AbilityType.INSTANT, AbilitySlot.ULTIMATE);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 900);   // 45 saniye
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;
        return !SandColossusController.isColossus(player.getUUID());
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        SandColossusController.start(player);
    }
}
