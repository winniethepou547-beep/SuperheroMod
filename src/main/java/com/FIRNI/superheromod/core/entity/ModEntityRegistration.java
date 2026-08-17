package com.FIRNI.superheromod.core.entity;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.entity.SandSoldierModel;
import com.FIRNI.superheromod.client.render.entity.SandSoldierRenderer;
import com.FIRNI.superheromod.heroes.sandman.GiantSandSoldierEntity;
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
        event.put(ModEntities.GIANT_SAND_SOLDIER.get(),
                GiantSandSoldierEntity.createGiantAttributes().build());
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
            event.registerEntityRenderer(ModEntities.SAND_SOLDIER.get(),
                    ctx -> new SandSoldierRenderer<>(ctx, 1.0f, 0.4f));
            // Dev asker ayni modeli kullanir, sadece olcegi buyuk
            event.registerEntityRenderer(ModEntities.GIANT_SAND_SOLDIER.get(),
                    ctx -> new SandSoldierRenderer<>(ctx, 2.1f, 0.95f));
        }
    }
}
