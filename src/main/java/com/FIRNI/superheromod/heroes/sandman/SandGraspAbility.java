package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.Ability;
import com.FIRNI.superheromod.core.ability.AbilityConfig;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.core.ability.AbilityType;
import net.minecraft.server.level.ServerPlayer;

/**
 * SAND GRASP — RMB. Eski Sand Spike'in yerini aldi.
 *
 * Diken hedefi havaya atip UZAKLASTIRIYORDU; Sandman yakin dovus
 * karakteri oldugu icin bu kendi oyun planiyla celisiyordu. Yeni yetenek
 * dikdortgen bir alan cizer, uzak uctan cikan kum eli alandakileri
 * Sandman'a DOGRU ceker.
 *
 * Alan ve el {@link SandGraspController} icinde yasar.
 */
public class SandGraspAbility extends Ability {

    public SandGraspAbility() {
        super("sandman_sand_grasp", AbilityType.INSTANT, AbilitySlot.RMB);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 90);
        config.set("damage", 6.0f);      // 3 kalp
        config.set("length", 9.0);       // dikdortgenin uzunlugu
        config.set("width", 3.0);        // dikdortgenin genisligi
        config.set("startGap", 1.2);     // oyuncuyla alan arasindaki bosluk
        config.set("pull", 1.15);        // Sandman'a dogru cekis gucu
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;

        // Colossus formunda sag tik KAYA FIRLATMADIR
        if (SandColossusController.isColossus(player.getUUID())) {
            return !ColossusRockController.isThrowing(player.getUUID())
                    && !SandColossusController.isStaggered(player.getUUID());
        }

        return !SandGraspController.hasPreview(player.getUUID());
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        if (SandColossusController.isColossus(player.getUUID())) {
            ColossusRockController.throwRock(player);
            return;
        }

        SandGraspController.press(player, getConfig());
    }
}
