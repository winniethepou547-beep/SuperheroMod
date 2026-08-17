package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.*;
import net.minecraft.server.level.ServerPlayer;

/**
 * SAND FIST — Sandman kolunu dev kum yumruguna cevirip agir tek vurus yapar.
 *
 * Bu bir kanal/beam saldirisi DEGIL: tek ve agir bir vurus. Yeteneginin
 * kendisi sadece tetikleyici; asil is {@link SandFistController} icindeki faz
 * makinesinde donuyor cunku hasarin animasyonun ORTASINDA, belirli bir impact
 * karesinde uygulanmasi gerekiyor. INSTANT yetenekler tek tickte bitip
 * kapandigi icin faz makinesi ayri tutuldu.
 */
public class SandFistAbility extends Ability {

    public SandFistAbility() {
        super("sandman_sand_fist", AbilityType.INSTANT, AbilitySlot.LMB);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 26);
        // Agir bruiser vurusu — Cyclops'un tek atisindan belirgin sekilde sert
        config.set("damage", 6.0f);          // 3 kalp
        config.set("range", 4.2);
        config.set("radius", 1.9);
        config.set("knockback", 1.15);
        config.set("knockbackVertical", 0.42);
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;
        // Ayni anda ikinci yumruk baslamasin
        return !SandFistController.isSwinging(player.getUUID());
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        SandFistController.start(player, getConfig());
    }
}
