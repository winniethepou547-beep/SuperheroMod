package com.FIRNI.superheromod.client.render.arm;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bukulebilir kol katmanini oyuncu render'ina ekler (normal ve slim model). */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BendableArmRegistration {

    private BendableArmRegistration() {}

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new BendableArmLayer(renderer));
                renderer.addLayer(new SandArmorLayer(renderer,
                        event.getEntityModels().bakeLayer(SandArmorLayer.LAYER)));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SandArmorLayer.LAYER, SandArmorLayer::createLayer);
    }
}
