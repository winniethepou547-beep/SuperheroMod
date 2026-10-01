package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.Ability;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.core.ability.AbilityType;
import net.minecraft.server.level.ServerPlayer;

/**
 * SAND SPEARS — R. Sand Body'nin yerini aldi.
 *
 * Onunde dikdortgen alan isaretlenir, alan aninda kuma doner ve
 * icindekiler yavaslar; bir saniye sonra yerden sarkitlar firlar.
 */
public class SandSpearFieldAbility extends Ability {

    public SandSpearFieldAbility() {
        super("sandman_sand_spears", AbilityType.INSTANT, AbilitySlot.SKILL_E);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 200);   // 10 saniye
        config.set("damage", 8.0f);         // 4 kalp
        config.set("length", 10.0);
        config.set("width", 5.0);
        config.set("startGap", 1.5);
        // Kum izi sarkitlardan uzun kaliyor: alan kontrolu yetenegin
        // asil degeri, sarkitlar tek seferlik vurus
        config.set("patchTicks", 220);
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;
        // Colossus formunda alan yetenekleri devre disi
        return !SandColossusController.isColossus(player.getUUID())
                || (!SandColossusController.isStaggered(player.getUUID())
                && !ColossusSwordController.isActive(player.getUUID())
                && !ColossusMaceController.isSwinging(player.getUUID())
                && !ColossusRockController.isThrowing(player.getUUID()));
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        if (SandColossusController.isColossus(player.getUUID())) {
            ColossusSwordController.start(player);
            return;
        }
        SandSpearFieldController.cast(player, getConfig());
    }
}
