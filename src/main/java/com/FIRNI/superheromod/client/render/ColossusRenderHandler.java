package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.sandman.ColossusCrystal;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Colossus formundaki oyuncuyu devasa gosterir.
 *
 * GECICI COZUM: su an oyuncunun kendi modeli olceklendiriliyor. Gercek
 * Colossus modeli (bacaksiz govde, alt kum kutlesi, dev topuz, kristaller)
 * ayri bir is; bu ara cozum "artik devsin" hissini hemen veriyor ve model
 * geldiginde sadece burasi degisecek.
 *
 * Olcek 10 blok / 1.8 blok oyuncu boyu ile hesaplaniyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ColossusRenderHandler {

    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float SCALE = ColossusCrystal.COLOSSUS_HEIGHT / PLAYER_HEIGHT;

    private ColossusRenderHandler() {}

    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (!ClientColossusData.isColossus(event.getEntity())) return;

        event.getPoseStack().pushPose();
        event.getPoseStack().scale(SCALE, SCALE, SCALE);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (!ClientColossusData.isColossus(event.getEntity())) return;

        event.getPoseStack().popPose();
    }
}
