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
        // CHANNELED: tus BASILI TUTULDUKCA kol uzamis kaliyor, birakilinca
        // geri toplaniyor. INSTANT olsaydi surenin sonunu kod belirlerdi.
        super("sandman_sand_fist", AbilityType.CHANNELED, AbilitySlot.LMB);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        // Sarj + uzama toplam 35 tick; bekleme bunun ustune biniyor
        config.set("cooldownTicks", 20);
        config.set("damage", 6.0f);          // 3 kalp
        // Menzil uzun cunku vurus artik yakin dovus degil UZANAN kol
        config.set("range", 8.0);
        config.set("radius", 0.9);
        config.set("knockback", 0.9);
        config.set("knockbackVertical", 0.35);
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;

        // Colossus formunda sol tik TOPUZ vurusudur
        if (SandColossusController.isColossus(player.getUUID())) {
            return !ColossusMaceController.isSwinging(player.getUUID())
                    && !SandColossusController.isStaggered(player.getUUID());
        }

        // Ayni anda ikinci yumruk baslamasin
        return !SandFistController.isSwinging(player.getUUID());
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        // Dev formundayken kucuk kum yumrugu yerine dev topuz iner
        if (SandColossusController.isColossus(player.getUUID())) {
            ColossusMaceController.swing(player);
            return;
        }

        SandFistController.start(player, getConfig());
    }

    @Override
    protected void onChannelStop(ServerPlayer player) {
        SandFistController.release(player);
    }
}
