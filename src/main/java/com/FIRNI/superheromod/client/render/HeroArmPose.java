package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.client.ClientHeroState;
import com.FIRNI.superheromod.client.hud.ClientUltimateState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kahraman kol pozlari. HumanoidModelMixin tarafindan setupAnim SONRASINDA
 * cagrilir; boylece vanilla animasyonu bu degerleri ezemez.
 *
 * DIRSEK: Vanilla kol tek parca oldugu icin kendi kendine bukulemez. Cozum
 * BendableArmLayer'da: vanilla kol cizimden cikarilir (skipDraw), yerine
 * ust kol + on kol ayri cizilir. Burada sadece bukulme MIKTARI uretiliyor.
 */
public final class HeroArmPose {

    /** Oyuncu basina el kaldirma miktari (0 = idle, 1 = visor'da). */
    private static final Map<UUID, Float> raise = new HashMap<>();
    /** Oyuncu basina dirsek bukulme aci si (radyan). */
    private static final Map<UUID, Float> elbow = new HashMap<>();

    private static final float RAISE_SPEED = 0.28f;
    private static final float LOWER_SPEED = 0.11f;

    // --- Omuz acilari ---
    // Kol duz cubuk gibi durmasin diye omuzdan hafif one/yana aciliyor.
    // Buyuk deger kollari one dikip zombi pozu yapiyor — kucuk tut.
    private static final float IDLE_ARM_X = -0.16f;
    private static final float IDLE_ARM_Z = 0.17f;
    private static final float IDLE_ELBOW_IN = -0.13f;

    // --- Dirsek bukulmesi (radyan) ---
    /** Boşta: on kol iceri/yukari dogru hafif kirik. */
    private static final float IDLE_BEND = 0.55f;
    /** Lazer atarken: el visor'a gelsin diye belirgin kirik. */
    private static final float FIRE_BEND = 1.75f;

    // --- Ates ederken omuz ---
    private static final float FIRE_ARM_X = -1.15f;
    private static final float FIRE_ARM_Y = -0.42f;
    private static final float FIRE_ARM_Z = 0.22f;

    private HeroArmPose() {}

    /** BendableArmLayer bunu okur; 0 ise vanilla kol cizilir. */
    public static float getElbowBend(Player player) {
        return elbow.getOrDefault(player.getUUID(), 0f);
    }

    public static void apply(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        if (!(entity instanceof Player player)) return;

        UUID id = player.getUUID();

        if (!isHero(player)) {
            elbow.remove(id);
            return;
        }

        // --- Ulti hazirligi: kollar gogus onunde capraz ---
        if (ClientUltimateState.isWindup(id)) {
            model.rightArm.xRot = -1.35f;
            model.rightArm.yRot = 0f;
            model.rightArm.zRot = 1.15f;

            model.leftArm.xRot = -1.35f;
            model.leftArm.yRot = 0f;
            model.leftArm.zRot = -1.15f;

            copySleeves(model);
            raise.put(id, 0f);
            elbow.put(id, 1.2f);
            applySkipDraw(model, true);
            return;
        }

        float r = raise.getOrDefault(id, 0f);
        r = isFiring(player) ? Math.min(1f, r + RAISE_SPEED)
                             : Math.max(0f, r - LOWER_SPEED);
        raise.put(id, r);

        // Dirsek: idle kiriktan ates pozuna yumusakca gecer
        elbow.put(id, Mth.lerp(r, IDLE_BEND, FIRE_BEND));
        applySkipDraw(model, true);

        // Yururken/kosarken vanilla kol sallanmasinin buyuk kismi korunur
        float breathe = Mth.sin(ageInTicks * 0.055f) * 0.03f;

        // --- SOL KOL: vanilla kalir, sadece hafif duruş ofseti ---
        model.leftArm.xRot = model.leftArm.xRot * 0.7f + IDLE_ARM_X + breathe;
        model.leftArm.zRot = -IDLE_ARM_Z;
        model.leftArm.yRot = -IDLE_ELBOW_IN;

        // --- SAG KOL (omuz): idle -> ates pozu ---
        float idleX = model.rightArm.xRot * 0.7f + IDLE_ARM_X + breathe;
        model.rightArm.xRot = Mth.lerp(r, idleX, FIRE_ARM_X);
        model.rightArm.yRot = Mth.lerp(r, IDLE_ELBOW_IN, FIRE_ARM_Y);
        model.rightArm.zRot = Mth.lerp(r, IDLE_ARM_Z, FIRE_ARM_Z);

        copySleeves(model);
    }

    /**
     * Bukulebilir kol devredeyse vanilla sag kol kutusu cizilmez; omuz
     * donusumu icin parca yine kullanilir. Sol kol vanilla kalir.
     */
    private static void applySkipDraw(PlayerModel<?> model, boolean bending) {
        model.rightArm.skipDraw = bending;
        model.rightSleeve.skipDraw = bending;
    }

    private static void copySleeves(PlayerModel<?> model) {
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        // copyFrom skipDraw'i tasimaz; tekrar uygula
        model.rightSleeve.skipDraw = model.rightArm.skipDraw;
    }

    private static boolean isHero(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(player.getUUID())) {
            return ClientHeroState.isHero();
        }
        return ClientBeamData.getChannelBeams().containsKey(player.getUUID());
    }

    private static boolean isFiring(Player player) {
        var channel = ClientBeamData.getChannelBeams().get(player.getUUID());
        if (channel != null && channel.fadeAlpha > 0) return true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(player.getUUID())) {
            if (mc.screen != null) return false;
            return mc.options.keyAttack.isDown() || mc.options.keyUse.isDown();
        }
        return false;
    }
}
