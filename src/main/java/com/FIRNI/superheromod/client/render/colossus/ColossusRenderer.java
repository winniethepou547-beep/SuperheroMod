package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.ClientColossusData;
import com.FIRNI.superheromod.heroes.sandman.ColossusCrystal;
import com.FIRNI.superheromod.heroes.sandman.ColossusForm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * SAND COLOSSUS'U CIZER — oyuncunun yerine.
 *
 * Oyuncunun kendi modeli tamamen gizleniyor; yerine bacaksiz dev, kristalleri
 * ve topuzuyla birlikte ciziliyor.
 *
 * ANIMASYON burada, ayri bir dosyada degil: dev bacaksiz oldugu icin
 * "yurume" animasyonu yok. Hareket hissi UC seyden geliyor —
 *   1) torso yavasca yukari/asagi suzulur (nefes)
 *   2) kollar agirliga gore salinir
 *   3) torso once doner, alt kum kutlesi birkac kare SONRA takip eder
 * Ucuncusu karakteri agir hissettiren asil sey.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ColossusRenderer {

    private static final ResourceLocation SAND =
            new ResourceLocation("minecraft", "textures/block/sand.png");
    /** Kristal icin ayri doku — kumdan belirgin farkli gorunmeli. */
    private static final ResourceLocation CRYSTAL =
            new ResourceLocation("minecraft", "textures/block/amethyst_block.png");

    /**
     * Modelin ayak hizasi (blok).
     *
     * DIKKAT: ModelPart kup koordinatlarini KENDI ICINDE 16'ya boluyor, yani
     * burada ayrica 1/16 uygulanmamali. Onceki surumde ikinci kez bolundugu
     * icin model hem 16 kat kucuk kaliyor hem de yerin 10 blok altina
     * gomuluyordu — bu yuzden hic gorunmuyordu.
     */
    private static final float FOOT_OFFSET = ColossusCrystal.COLOSSUS_HEIGHT;

    private static ColossusModel model;
    private static ColossusCrystalModel crystalModel;

    /** Alt kum kutlesinin gecikmeli donusu — oyuncu basina. */
    private static final java.util.Map<java.util.UUID, Float> massYaw =
            new java.util.concurrent.ConcurrentHashMap<>();

    private ColossusRenderer() {}

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(ColossusModel.LAYER, ColossusModel::createLayer);
            event.registerLayerDefinition(ColossusCrystalModel.LAYER,
                    ColossusCrystalModel::createLayer);
        }
    }

    private static ColossusModel model() {
        if (model == null) {
            ModelPart root = Minecraft.getInstance().getEntityModels()
                    .bakeLayer(ColossusModel.LAYER);
            model = new ColossusModel(root);
        }
        return model;
    }

    private static ColossusCrystalModel crystalModel() {
        if (crystalModel == null) {
            ModelPart root = Minecraft.getInstance().getEntityModels()
                    .bakeLayer(ColossusCrystalModel.LAYER);
            crystalModel = new ColossusCrystalModel(root);
        }
        return crystalModel;
    }

    // ------------------------------------------------------------------

    /** Colossus formundaki oyuncunun kendi modeli cizilmez. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (ClientColossusData.isColossus(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float partial = mc.getFrameTime();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        boolean any = false;
        for (Player player : mc.level.players()) {
            if (!ClientColossusData.isColossus(player)) continue;
            draw(pose, buffer, cam, player, partial);
            any = true;
        }

        if (any) buffer.endBatch();
    }

    private static void draw(PoseStack pose, MultiBufferSource buffer,
                             Vec3 cam, Player player, float partial) {
        float progress = ClientColossusData.formProgress(player);
        float growth = ColossusForm.growth(progress);
        if (growth <= 0.01f) return;

        Vec3 pos = player.getPosition(partial);
        float yaw = Mth.rotLerp(partial, player.yRotO, player.getYRot());
        float age = player.tickCount + partial;

        // Alt kum kutlesi torsoyu GECIKMELI takip eder — agirlik hissi
        float smoothed = massYaw.getOrDefault(player.getUUID(), yaw);
        smoothed = smoothed + Mth.wrapDegrees(yaw - smoothed) * 0.06f;
        massYaw.put(player.getUUID(), smoothed);

        ColossusModel colossus = model();
        animate(colossus, age, yaw, smoothed, growth);

        pose.pushPose();
        pose.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);

        // Olusma sirasinda kutle yerden yukselir
        pose.scale(growth, growth, growth);

        pose.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));

        // Entity modelleri bas asagi cizilir; cevirdikten sonra ayak hizasini
        // oyuncunun konumuna oturtuyoruz
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -FOOT_OFFSET, 0.0f);

        int light = LightTexture.pack(14, 14);

        VertexConsumer sand = buffer.getBuffer(RenderType.entityCutoutNoCull(SAND));
        colossus.root().render(pose, sand, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);

        pose.popPose();

        // Kristaller ancak son asamada belirir
        if (ColossusForm.crystalsActive(progress)) {
            drawCrystals(pose, buffer, cam, player, pos, yaw, growth, light);
        }
    }

    /**
     * Bacaksiz devin hareket dili.
     *
     * Yurume yok; agirlik su uc seyden okunuyor: nefes, kol salinimi ve alt
     * kutlenin gecikmeli donusu.
     */
    private static void animate(ColossusModel m, float age, float yaw,
                                float massYawSmoothed, float growth) {
        // 1) Nefes — torso cok yavas yukari/asagi suzulur
        float breathe = Mth.sin(age * 0.045f);
        m.torso.y = breathe * 1.6f;
        m.head.xRot = 0.12f + breathe * 0.03f;   // kafa hafif one egik

        // 2) Kollar agirliga gore salinir, ikisi ayni fazda DEGIL
        m.rightArm.xRot = Mth.sin(age * 0.040f) * 0.07f;
        m.rightArm.zRot = 0.10f + Mth.sin(age * 0.031f) * 0.025f;
        m.leftArm.xRot = Mth.sin(age * 0.036f + 1.4f) * 0.07f;
        m.leftArm.zRot = -0.12f + Mth.sin(age * 0.028f) * 0.025f;

        // 3) Alt kutle gecikmeli doner — torso once, kum sonra
        m.lowerMass.yRot = (float) Math.toRadians(Mth.wrapDegrees(massYawSmoothed - yaw));
        // Kutle nefesle birlikte hafifce yayilir
        float spread = 1.0f + breathe * 0.02f;
        m.lowerMass.xScale = spread;
        m.lowerMass.zScale = spread;

        // Topuz elde hafifce sallanir
        m.mace.xRot = Mth.sin(age * 0.033f) * 0.05f;
    }

    private static void drawCrystals(PoseStack pose, MultiBufferSource buffer,
                                     Vec3 cam, Player player, Vec3 pos,
                                     float yaw, float growth, int light) {
        ColossusCrystalModel cm = crystalModel();
        VertexConsumer crystal = buffer.getBuffer(RenderType.entityTranslucent(CRYSTAL));

        for (ColossusCrystal type : ColossusCrystal.values()) {
            int state = ClientColossusData.crystalState(player, type.ordinal());
            if (!cm.prepare(state)) continue;

            Vec3 world = type.worldPosition(pos, yaw);

            pose.pushPose();
            pose.translate(world.x - cam.x, world.y - cam.y, world.z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));

            // Kristal boyutu ISABET YARICAPIYLA orantili: gorulen sey ile
            // vurulabilen alan tutmali, yoksa oyuncu nisan alamaz.
            // ModelPart zaten 16'ya boluyor, yani model ~1 blok yuksekliginde;
            // radius*2 blok olmasi icin olcek dogrudan radius*2.
            float scale = (float) (type.radius * 2.0) * growth;
            pose.scale(-scale, -scale, scale);

            cm.root().render(pose, crystal, light, OverlayTexture.NO_OVERLAY,
                    1f, 1f, 1f, 1f);

            pose.popPose();
        }
    }
}
