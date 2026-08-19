package com.FIRNI.superheromod.client.render.cinematic;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * SINEMATIK POST-PROCESSING — sadece sinematik oynarken devrede.
 *
 * Neden sadece sinematikte: shader kalitesinde bir goruntu her karede pahali.
 * Ama sinematik kisa (birkac saniye) ve nadir, o yuzden orada harcamak
 * oynanis performansina HIC dokunmuyor. Sinematik bitince kapaniyor.
 *
 * Minecraft'in kendi post-chain sistemi kullaniliyor — vanilla bunu spectator
 * modda creeper/orumcek gorusu icin kullaniyor. Yani ekstra kutuphane yok.
 *
 * Hata olursa sessizce devre disi kalir: efekt yuklenemezse sinematik yine
 * oynar, sadece renk katmani olmaz. Gorsel bir ek yuzunden oyunun cokmesi
 * kabul edilemez.
 */
public final class CinematicPostFx {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation EFFECT =
            new ResourceLocation(SuperheroMod.MODID, "shaders/post/cinematic.json");

    private static boolean applied = false;
    /** Bir kez basarisiz olduysa her karede tekrar denemeyelim. */
    private static boolean failed = false;

    private CinematicPostFx() {}

    public static void enable() {
        if (applied || failed) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.level == null) return;

        try {
            mc.gameRenderer.loadEffect(EFFECT);
            applied = true;
        } catch (Exception e) {
            failed = true;
            LOGGER.warn("Sinematik post-fx yuklenemedi, renk katmani olmadan devam ediliyor", e);
        }
    }

    public static void disable() {
        if (!applied) return;
        applied = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;

        try {
            mc.gameRenderer.shutdownEffect();
        } catch (Exception e) {
            LOGGER.warn("Sinematik post-fx kapatilamadi", e);
        }
    }

    public static boolean isApplied() {
        return applied;
    }
}
