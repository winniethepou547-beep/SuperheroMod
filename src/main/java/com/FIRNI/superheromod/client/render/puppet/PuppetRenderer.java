package com.FIRNI.superheromod.client.render.puppet;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Sahne aktorlerini cizer ve sinematik sirasinda GERCEK oyunculari gizler.
 *
 * Gercek oyuncunun gizlenmesi sart: aksi halde kukla ile gercek beden ust
 * uste binip iki karakter gorunur.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class PuppetRenderer {

    private static final List<CinematicPuppet> puppets = new ArrayList<>();

    private static PuppetModel wideModel;
    private static PuppetModel slimModel;

    private PuppetRenderer() {}

    // ------------------------------------------------------------------
    // Sahne yonetimi
    // ------------------------------------------------------------------

    public static void clear() {
        synchronized (puppets) {
            puppets.clear();
        }
    }

    public static void add(CinematicPuppet puppet) {
        synchronized (puppets) {
            puppets.add(puppet);
        }
    }

    public static List<CinematicPuppet> all() {
        return puppets;
    }

    public static boolean isHidden(java.util.UUID playerId) {
        synchronized (puppets) {
            for (CinematicPuppet p : puppets) {
                if (p.sourcePlayer.equals(playerId)) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Kayit
    // ------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(PuppetModel.LAYER, () -> PuppetModel.createLayer(false));
            event.registerLayerDefinition(PuppetModel.LAYER_SLIM, () -> PuppetModel.createLayer(true));
        }
    }

    private static PuppetModel model(boolean slim) {
        Minecraft mc = Minecraft.getInstance();

        if (slim) {
            if (slimModel == null) {
                ModelPart root = mc.getEntityModels().bakeLayer(PuppetModel.LAYER_SLIM);
                slimModel = new PuppetModel(root);
            }
            return slimModel;
        }

        if (wideModel == null) {
            ModelPart root = mc.getEntityModels().bakeLayer(PuppetModel.LAYER);
            wideModel = new PuppetModel(root);
        }
        return wideModel;
    }

    // ------------------------------------------------------------------
    // Cizim
    // ------------------------------------------------------------------

    /** Sinematikte gercek oyuncu cizilmez — yerine kukla var. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (puppets.isEmpty()) return;
        if (isHidden(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (puppets.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        synchronized (puppets) {
            for (CinematicPuppet puppet : puppets) {
                if (!puppet.visible) continue;
                draw(pose, buffer, cam, puppet);
            }
        }

        buffer.endBatch();
    }

    private static void draw(PoseStack pose, MultiBufferSource buffer,
                             Vec3 cam, CinematicPuppet puppet) {
        PuppetModel model = model(puppet.slim);
        model.apply(puppet.pose);

        pose.pushPose();
        pose.translate(
                puppet.position.x - cam.x,
                puppet.position.y - cam.y,
                puppet.position.z - cam.z);

        // Model uzayi ters: entity modelleri bas asagi cizilir ve ayak
        // hizasi 1.5 blok yukaridadir
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - puppet.yaw));
        if (puppet.pose.bodyRoll != 0f) {
            pose.mulPose(Axis.ZP.rotationDegrees(puppet.pose.bodyRoll));
        }
        if (puppet.pose.bodyPitch != 0f) {
            pose.mulPose(Axis.XP.rotationDegrees(puppet.pose.bodyPitch));
        }
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);

        // Isik kukla icin ELLE veriliyor: dunya karanlikken aktor parlak
        // kalabilsin diye. Gercek entity'de bu mumkun degil.
        int light = LightTexture.pack(puppet.lightLevel, puppet.lightLevel);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(puppet.skin));
        model.render(pose, consumer, light, OverlayTexture.NO_OVERLAY);

        pose.popPose();
    }
}
