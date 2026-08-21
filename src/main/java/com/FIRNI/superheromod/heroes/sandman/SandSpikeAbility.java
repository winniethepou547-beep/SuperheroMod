package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.combat.raycast.RaycastResult;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * SAND SPIKE — V tusu. Nisan alinan zeminden yukselen dev kum dikeni.
 *
 * Sag tik Sand Grasp'e verilince yetenek slotsuz kalmisti; V'ye tasindi.
 *
 * Etkiler: hasar, yukari firlatma, geri itme ve kisa sureli alan reddi.
 *
 * Hedef zemin sunucuda raycast ile bulunur; diken {@link SandSpikeController}
 * icinde yasar. Yetenegin kendisi sadece hedefi secip dikeni baslatir.
 */
public class SandSpikeAbility extends Ability {

    public SandSpikeAbility() {
        super("sandman_sand_spike", AbilityType.INSTANT, AbilitySlot.SKILL_V);
    }

    @Override
    protected void initConfig(AbilityConfig config) {
        config.set("cooldownTicks", 60);
        config.set("damage", 5.0f);          // 2.5 kalp
        config.set("range", 18.0);           // nisan mesafesi
        config.set("radius", 1.7);           // dikenin vurus yaricapi
        config.set("knockUp", 0.85);
        config.set("knockback", 0.35);
        config.set("height", 3.0);
    }

    @Override
    public boolean canActivate(ServerPlayer player) {
        if (!super.canActivate(player)) return false;

        // Colossus dali KALDIRILDI: yetenek artik sag tikta degil V'de.
        // Colossus'un kaya firlatmasi sag tikta kaliyor ve orayi
        // SandGraspAbility yonetiyor.
        return findGround(player) != null;
    }

    @Override
    protected void onActivate(ServerPlayer player) {
        Vec3 ground = findGround(player);
        if (ground == null) return;
        SandSpikeController.spawn(player, ground, getConfig());
    }

    /**
     * Crosshair'in gosterdigi zemin noktasi.
     *
     * Isin bir canliya denk gelirse dikeni onun icinden degil AYAGININ
     * altindan cikariyoruz; boylece diken hep zeminden yukseliyor.
     */
    private static Vec3 findGround(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        double range = getConfigRange(player);

        Vec3 eye = RaycastSystem.getEyeOrigin(player);
        Vec3 look = RaycastSystem.getLookDirection(player);

        RaycastResult result = RaycastSystem.cast(
                level, player, eye, look, range, 0.6f, false,
                e -> e instanceof LivingEntity && e != player);

        Vec3 point = result.getHitPosition();

        if (result.didHitEntity() && !result.getEntityHits().isEmpty()) {
            point = result.getEntityHits().get(0).getEntity().position();
        }

        return SandSpikeController.groundUnder(level, point);
    }

    /** Yetenek ornegi elimizde olmadigi icin menzil sabit okunuyor. */
    private static double getConfigRange(ServerPlayer player) {
        Ability ability = AbilityManager.getAbility(player.getUUID(), AbilitySlot.SKILL_V);
        return ability == null ? 18.0 : ability.getConfig().getDouble("range", 18.0);
    }
}
