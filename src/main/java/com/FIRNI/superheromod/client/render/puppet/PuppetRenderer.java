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

    private static final java.util.Map<String,ImportedPuppetModel> models=new java.util.HashMap<>();
    private static boolean drawingProxy;
    @SubscribeEvent public static void onLiving(net.minecraftforge.client.event.RenderLivingEvent.Pre<?,?> event) {
        if(!drawingProxy && isHidden(event.getEntity().getUUID()))event.setCanceled(true);
    }

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

    public static void preload(com.FIRNI.superheromod.core.cinematic.CinematicDefinition definition) {
        var prefixes = new java.util.HashSet<String>();
        prefixes.add("puppet");
        for (var track : definition.actorTracks) prefixes.add(track.modelPrefix);
        for (String prefix : prefixes) for (String suffix : new String[]{"_wide", "_slim"})
            models.computeIfAbsent(prefix + suffix, ImportedPuppetModel::new).warmUp(null);
        for (var track : definition.actorTracks) for (String suffix : new String[]{"_wide", "_slim"})
            models.get(track.modelPrefix + suffix).warmUp(track.rigClip);
    }

    private static ImportedPuppetModel model(CinematicPuppet puppet) {
        String prefix=puppet.track==null?"puppet":puppet.track.modelPrefix;
        return models.computeIfAbsent(prefix+(puppet.slim?"_slim":"_wide"),ImportedPuppetModel::new);
    }

    public static void prepareRigPose(CinematicPuppet puppet) {
        if(puppet.sourceEntity==null)model(puppet).preparePose(puppet);
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
                if (!puppet.visible || puppet.scale < 0.001f) continue;
                draw(pose, buffer, cam, puppet);
            }
        }

        buffer.endBatch();
    }

    /** Draws a puppet that is not part of the world scene (a film stage); its position is already in pose space. */
    public static void drawStaged(PoseStack pose, MultiBufferSource buffer, CinematicPuppet puppet) {
        if (puppet.visible && puppet.scale >= 0.001f) draw(pose, buffer, Vec3.ZERO, puppet);
    }

    private static void draw(PoseStack pose, MultiBufferSource buffer,
                             Vec3 cam, CinematicPuppet puppet) {
        if(puppet.sourceEntity!=null) {
            drawMob(pose,buffer,cam,puppet);
            return;
        }
        ImportedPuppetModel model = model(puppet);
        model.apply(puppet);

        pose.pushPose();
        pose.translate(
                puppet.position.x - cam.x,
                puppet.position.y - cam.y,
                puppet.position.z - cam.z);

        // Model uzayi ters: entity modelleri bas asagi cizilir ve ayak
        // hizasi 1.5 blok yukaridadir
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - puppet.yaw));
        pose.scale(puppet.scale, puppet.scale, puppet.scale);
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

    private static void drawMob(PoseStack pose,MultiBufferSource buffer,Vec3 cam,CinematicPuppet puppet) {
        pose.pushPose();
        try {
            pose.translate(puppet.position.x-cam.x,puppet.position.y-cam.y,puppet.position.z-cam.z);
            pose.scale(puppet.scale,puppet.scale,puppet.scale);
            pose.mulPose(Axis.ZP.rotationDegrees(puppet.pose.bodyRoll));
            pose.mulPose(Axis.XP.rotationDegrees(puppet.pose.bodyPitch));
            drawingProxy=true;
            var entity=puppet.sourceEntity;
            var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            renderer.render(entity,puppet.yaw,Minecraft.getInstance().getFrameTime(),pose,buffer,
                    LightTexture.pack(puppet.lightLevel,puppet.lightLevel));
        } finally { drawingProxy=false;pose.popPose(); }
    }
}
