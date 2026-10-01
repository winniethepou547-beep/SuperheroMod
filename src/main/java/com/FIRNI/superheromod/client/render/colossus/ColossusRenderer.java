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
            new ResourceLocation(SuperheroMod.MODID, "textures/entity/colossus_crystal.png");

    /**
     * Kristal rengi — referanstaki gibi AKKOR turuncu/amber.
     *
     * Ametist dokusu mor; renk carpaniyla amber'e cevriliyor. Ayri bir doku
     * dosyasi uretmeye gerek kalmiyor ve kristalin kristal dokusu korunuyor.
     */
    private static final float CRYSTAL_R = 1.00f;
    private static final float CRYSTAL_G = 0.52f;
    private static final float CRYSTAL_B = 0.16f;

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
    private record Follow(float yaw, float age) {}
    private static final java.util.Map<java.util.UUID, Follow> massYaw =
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
        if (mc.level == null) { massYaw.clear(); return; }
        massYaw.keySet().removeIf(id -> {
            Player p = mc.level.getPlayerByUUID(id);
            return p == null || !ClientColossusData.isColossus(p);
        });

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
        if(progress<.43f)drawGathering(pose,buffer,cam,player,partial,progress);
        if (growth <= 0.01f) return;

        Vec3 pos = player.getPosition(partial);
        float yaw = Mth.rotLerp(partial, player.yRotO, player.getYRot());
        float age = player.tickCount + partial;

        // Alt kum kutlesi torsoyu GECIKMELI takip eder — agirlik hissi
        Follow previous = massYaw.get(player.getUUID());
        float smoothed = yaw;
        if (previous != null && age >= previous.age() && age - previous.age() < 20f) {
            float elapsedTicks = age - previous.age();
            // Same damping at 30/60/144 FPS; repeated renders at the same time do not advance it.
            float response = (float) (1.0 - Math.pow(0.94, elapsedTicks * 3.0));
            smoothed = previous.yaw() + Mth.wrapDegrees(yaw - previous.yaw()) * response;
        }
        massYaw.put(player.getUUID(), new Follow(smoothed, age));

        ColossusModel colossus = model();
        // One shared mesh is drawn for every player. Never inherit another player's action pose.
        colossus.root().getAllParts().forEach(ModelPart::resetPose);
        colossus.sword.visible = false;
        colossus.mace.visible = true;
        var action=ClientColossusActions.of(player.getUUID());
        boolean right=ClientColossusData.crystalState(player,ColossusCrystal.RIGHT_SHOULDER.ordinal())<3;
        var shared=com.FIRNI.superheromod.heroes.sandman.ColossusPose.evaluate(age,yaw,smoothed,growth,
                action==null?null:new com.FIRNI.superheromod.heroes.sandman.ColossusPose.Action(action.type,action.ticks,action.duration),partial,right);
        com.FIRNI.superheromod.heroes.sandman.ColossusPose.form(shared,progress);
        applyPose(colossus.lowerMass,shared.lowerMass);
        applyPose(colossus.torso,shared.torso);
        applyPose(colossus.head,shared.head);
        applyPose(colossus.rightArm,shared.rightArm);
        applyPose(colossus.leftArm,shared.leftArm);
        applyPose(colossus.rightForearm,shared.rightForearm);
        applyPose(colossus.leftForearm,shared.leftForearm);
        applyPose(colossus.mace,shared.mace);
        applyPose(colossus.sword,shared.sword);
        colossus.rightArm.getChild("right_crags").visible=!shared.rightArm.skipDraw;

        pose.pushPose();
        pose.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);

        // Olusma sirasinda kutle yerden yukselir
        pose.scale(growth, growth, growth);

        pose.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));

        // Entity modelleri bas asagi cizilir; cevirdikten sonra ayak hizasini
        // oyuncunun konumuna oturtuyoruz
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -FOOT_OFFSET, 0.0f);

        int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(player.level(), player.blockPosition());

        VertexConsumer sand = new com.FIRNI.superheromod.client.render.util.SandTextureConsumer(
                buffer.getBuffer(RenderType.entityCutoutNoCull(SAND)));
        boolean leftMace = !right && colossus.mace.visible;
        if (leftMace) colossus.mace.visible = false;
        colossus.root().render(pose, sand, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        if (leftMace) {
            // Reuse the same mesh under the surviving arm; do not leave a second fist on the broken side.
            pose.pushPose();
            colossus.root().getChild("root").translateAndRotate(pose);
            colossus.torso.translateAndRotate(pose);
            colossus.leftArm.translateAndRotate(pose);
            colossus.leftForearm.translateAndRotate(pose);
            colossus.mace.visible = true;
            colossus.mace.render(pose, sand, light, OverlayTexture.NO_OVERLAY, 1f,1f,1f,1f);
            pose.popPose();
        }

        pose.popPose();

        // Kristaller ancak son asamada belirir
        if (ColossusForm.crystalsActive(progress)) {
            drawCrystals(pose, buffer, cam, player, pos, yaw, growth, light, shared);
        }
    }

    /**
     * Bacaksiz devin hareket dili.
     *
     * Yurume yok; agirlik su uc seyden okunuyor: nefes, kol salinimi ve alt
     * kutlenin gecikmeli donusu.
     */
    /**
     * Kristaller.
     *
     * KRISTAL KONUMU vucut hareketini TAKIP EDIYOR. Onceden konumlar
     * devin tabanina gore sabitti ve kristaller govdeden bagimsiz havada
     * asili duruyordu; kol salinsa bile omuzdaki kristal kimildamiyordu.
     * Artik omuz kristalleri kolun donusunden, govde kristalleri de nefes
     * hareketinden pay aliyor.
     *
     * Kaydirma modelin ANIMASYON degerlerinden turetiliyor, ayri bir
     * kopya animasyondan degil -- ikisi ayri hesaplansaydi zamanla
     * birbirinden kayarlardi.
     */
    private static void applyPose(ModelPart m,com.FIRNI.superheromod.heroes.sandman.ColossusPose.Part p) {
        m.x+=p.x;m.y+=p.y;m.z+=p.z;m.xRot=p.xRot;m.yRot=p.yRot;m.zRot=p.zRot;
        m.xScale=p.xScale;m.yScale=p.yScale;m.zScale=p.zScale;m.visible=p.visible;m.skipDraw=p.skipDraw;
    }

    private static void drawGathering(PoseStack pose,MultiBufferSource buffer,Vec3 camera,
                                      Player player,float partial,float progress) {
        if(progress<=0 || player.position().distanceToSqr(camera)>80*80)return;
        var mc=Minecraft.getInstance();
        Vec3 origin=player.getPosition(partial);
        int light=net.minecraft.client.renderer.LevelRenderer.getLightColor(player.level(),player.blockPosition().above());
        for(int i=0;i<28;i++) {
            float t=Mth.clamp((progress-i%5*.014f)/.34f,0,1);
            float smooth=t*t*(3-2*t);
            double angle=i*2.39996 + smooth*.35;
            double radius=(5.5+(i%4)*.7)*(1-smooth)+.8;
            float scale=(.32f+(i%4)*.095f)*(1-Mth.clamp((t-.85f)/.15f,0,1));
            if(scale<.005)continue;
            pose.pushPose();
            pose.translate(origin.x-camera.x+Math.cos(angle)*radius,
                    origin.y-camera.y+.15+smooth*(1+i%9*.85),origin.z-camera.z+Math.sin(angle)*radius);
            pose.mulPose(Axis.ZP.rotationDegrees(i*31+smooth*60));
            pose.mulPose(Axis.YP.rotationDegrees(i*57));
            pose.scale(scale,scale*1.4f,scale);
            pose.translate(-.5,-.5,-.5);
            mc.getBlockRenderer().renderSingleBlock(net.minecraft.world.level.block.Blocks.SAND.defaultBlockState(),
                    pose,buffer,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private static void drawCrystals(PoseStack pose, MultiBufferSource buffer,
                                     Vec3 cam, Player player, Vec3 pos,
                                     float yaw, float growth, int light,
                                     com.FIRNI.superheromod.heroes.sandman.ColossusPose shared) {
        VertexConsumer crystal = buffer.getBuffer(RenderType.entityCutoutNoCull(CRYSTAL));

        for (ColossusCrystal type : ColossusCrystal.values()) {
            int state = ClientColossusData.crystalState(player, type.ordinal());
            if (state>=3) continue;

            pose.pushPose();
            pose.mulPoseMatrix(type.socket(pos.subtract(cam),yaw,growth,shared));
            float scale=(float)(type.radius*1.15);
            pose.scale(scale,scale,scale);

            // ISIK: cevrenin isigi kullaniliyor, FULL_BRIGHT DEGIL.
            // Tam parlaklikta kristaller geceleyin karanlikta yanan
            // lambalar gibi duruyordu ve devin geri kalaniyla ayni
            // dunyada gorunmuyorlardi.
            CrystalFacets.render(pose,crystal,light,state,type.ordinal());

            pose.popPose();
        }
    }

    /**
     * Kristalin vucut hareketinden aldigi kayma (blok).
     *
     * Modelin ANIMASYON degerleri okunuyor; kristal icin ayri bir hareket
     * hesaplansaydi zamanla govdeden kayardi.
     *
     * Kaymalar KUCUK tutuluyor. Sunucudaki isabet kontrolu kristalleri
     * sabit konumda ariyor; gorsel oradan cok uzaklasirsa oyuncu gordugu
     * yere nisan alip iskalar. Animasyonun genligi zaten birkac derece,
     * yani gorsel ile isabet alani ic ice kaliyor.
     */
}
