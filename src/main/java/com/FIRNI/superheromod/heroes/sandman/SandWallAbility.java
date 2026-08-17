package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.*;
import net.minecraft.server.level.ServerPlayer;

/**
 * SAND WALL — SHIFT.
 *
 * Ilk basis hayali duvari acar (sol tik onaylar, sag tik iptal eder).
 * Duvar ayaktayken ikinci basis dikenleri cikarip duvari firlatir.
 *
 * Bekleme suresi yetenek bittiginde degil, duvar tamamen yok oldugunda
 * anlam kazandigi icin burada tutulmuyor; ayni anda tek duvar kurali
 * {@link SandWallController} tarafindan uygulaniyor.
 */
public class SandWallAbility extends Ability {

    public SandWallAbility() {
        super("sandman_sand_wall", AbilityType.INSTANT, AbilitySlot.SHIFT);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 40);
        config.set("width", 5.0f);
        config.set("height", 3.4f);
        config.set("depth", 0.8f);
        config.set("distance", 3.2);
        config.set("wallHealth", 60.0f);
        config.set("lifetimeTicks", 240);
        config.set("flyDamage", 7.0f);      // 3.5 kalp — dikenli duvar agir vurur
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        SandWallController.press(player, getConfig());
    }
}
