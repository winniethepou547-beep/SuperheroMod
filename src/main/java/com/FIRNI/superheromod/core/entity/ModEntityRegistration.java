package com.FIRNI.superheromod.core.entity;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.entity.SandSoldierModel;
import com.FIRNI.superheromod.client.render.entity.SandSoldierRenderer;
import com.FIRNI.superheromod.heroes.sandman.SandSoldierEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Entity ozellikleri (sunucu) ve cizici/model kayitlari (istemci). */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityRegistration {

    private ModEntityRegistration() {}

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SAND_SOLDIER.get(), SandSoldierEntity.createAttributes().build());
    }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Client {
        private Client() {}

        @SubscribeEvent
        public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(SandSoldierModel.LAYER, SandSoldierModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.SAND_SOLDIER.get(), SandSoldierRenderer::new);
        }
    }
}
