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

        // Hareket varsa notr salinimin UZERINE biner
        ClientColossusActions.Action action =
                ClientColossusActions.of(player.getUUID());
        if (action != null) {
            boolean maceRight = ClientColossusActions.isMaceInRight(player.getUUID(),
                    ClientColossusData.crystalState(player,
                            ColossusCrystal.RIGHT_SHOULDER.ordinal()));
            applyAction(colossus, action, partial, maceRight);
        }

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
            drawCrystals(pose, buffer, cam, player, pos, yaw, growth, light, colossus);
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

    /**
     * SALDIRI ANIMASYONLARI.
     *
     * Zaman cizelgeleri sunucudaki faz sinirlariyla ayni tutuldu; aksi halde
     * darbe sesi ile kolun indigi an tutmuyor ve vurus sahte gorunuyor.
     */
    private static void applyAction(ColossusModel m, ClientColossusActions.Action action,
                                    float partial, boolean maceRight) {
        float t = action.progress(partial);

        if (ClientColossusActions.isMaceSwing(action)) {
            maceSwing(m, t, maceRight);
        } else if (ClientColossusActions.isRockThrow(action)) {
            rockThrow(m, t, maceRight);
        }
    }

    /**
     * TOPUZ VURUSU: kaldir -> TEPEDE BEKLE -> indir -> toparlan.
     *
     * Tepedeki bekleme bilerek var; topuz kesintisiz inerse darbe hafif
     * kaliyor. Agirlik hissini veren sey o duraklama.
     */
    private static void maceSwing(ColossusModel m, float t, boolean maceRight) {
        ModelPart arm = maceRight ? m.rightArm : m.leftArm;
        ModelPart other = maceRight ? m.leftArm : m.rightArm;

        float armX;
        float torsoLean;

        if (t < 0.31f) {
            // Kaldirma (0-10 tick)
            float p = ease(t / 0.31f);
            armX = lerp(p, 0f, -2.45f);
            torsoLean = lerp(p, 0f, -0.16f);
        } else if (t < 0.50f) {
            // Tepede bekleme (10-16 tick) — hafif titreme
            armX = -2.45f;
            torsoLean = -0.16f;
        } else if (t < 0.66f) {
            // Inis (16-21 tick) — hizli
            float p = (t - 0.50f) / 0.16f;
            p = p * p;   // hizlanarak insin
            armX = lerp(p, -2.45f, 1.05f);
            torsoLean = lerp(p, -0.16f, 0.34f);
        } else {
            // Toparlanma (21-32 tick)
            float p = ease((t - 0.66f) / 0.34f);
            armX = lerp(p, 1.05f, 0f);
            torsoLean = lerp(p, 0.34f, 0f);
        }

        arm.xRot = armX;
        arm.zRot += maceRight ? -0.18f : 0.18f;
        m.torso.xRot = torsoLean;
        m.head.xRot = 0.12f + torsoLean * 0.5f;

        // Diger kol dengeleme icin ters yone gider
        other.xRot = -armX * 0.22f;
    }

    /**
     * KAYA FIRLATMA: kolu geri cek -> savur -> toparlan.
     *
     * Govde de doner; sadece kol hareket ederse firlatma guclu gorunmuyor.
     */
    private static void rockThrow(ColossusModel m, float t, boolean maceRight) {
        // Kaya topuz TUTMAYAN elde
        ModelPart arm = maceRight ? m.leftArm : m.rightArm;
        float side = maceRight ? 1f : -1f;

        float armX;
        float twist;

        if (t < 0.43f) {
            // Geri cekme (0-9 tick)
            float p = ease(t / 0.43f);
            armX = lerp(p, 0f, 0.95f);
            twist = lerp(p, 0f, 0.34f * side);
        } else if (t < 0.60f) {
            // Savurma — cok hizli
            float p = (t - 0.43f) / 0.17f;
            p = p * p;
            armX = lerp(p, 0.95f, -2.10f);
            twist = lerp(p, 0.34f * side, -0.30f * side);
        } else {
            // Toparlanma
            float p = ease((t - 0.60f) / 0.40f);
            armX = lerp(p, -2.10f, 0f);
            twist = lerp(p, -0.30f * side, 0f);
        }

        arm.xRot = armX;
        arm.zRot += 0.20f * side;
        m.torso.yRot = twist;
        m.head.yRot = -twist * 0.4f;
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    /** Yumusak giris/cikis. */
    private static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

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
    private static void drawCrystals(PoseStack pose, MultiBufferSource buffer,
                                     Vec3 cam, Player player, Vec3 pos,
                                     float yaw, float growth, int light,
                                     ColossusModel model) {
        ColossusCrystalModel cm = crystalModel();
        VertexConsumer crystal = buffer.getBuffer(RenderType.entityTranslucent(CRYSTAL));

        for (ColossusCrystal type : ColossusCrystal.values()) {
            int state = ClientColossusData.crystalState(player, type.ordinal());
            if (!cm.prepare(state)) continue;

            Vec3 world = type.worldPosition(pos, yaw).add(bodyOffset(type, model, yaw));

            pose.pushPose();
            pose.translate(world.x - cam.x, world.y - cam.y, world.z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(180.0f - yaw));

            // Kristal boyutu ISABET YARICAPIYLA orantili: gorulen sey ile
            // vurulabilen alan tutmali, yoksa oyuncu nisan alamaz.
            // ModelPart zaten 16'ya boluyor, yani model ~1 blok yuksekliginde;
            // radius*2 blok olmasi icin olcek dogrudan radius*2.
            float scale = (float) (type.radius * 2.0) * growth;
            pose.scale(-scale, -scale, scale);

            // ISIK: cevrenin isigi kullaniliyor, FULL_BRIGHT DEGIL.
            // Tam parlaklikta kristaller geceleyin karanlikta yanan
            // lambalar gibi duruyordu ve devin geri kalaniyla ayni
            // dunyada gorunmuyorlardi.
            cm.root().render(pose, crystal, light,
                    OverlayTexture.NO_OVERLAY,
                    CRYSTAL_R, CRYSTAL_G, CRYSTAL_B, 1f);

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
    private static Vec3 bodyOffset(ColossusCrystal type, ColossusModel model, float yaw) {
        // Model uzayi 16'ya bolunuyor ve dev buyutulmus haliyle ciziliyor;
        // burada sadece ORAN gerekiyor, mutlak piksel degil.
        double breathe = model.torso.y / 16.0;

        return switch (type) {
            case RIGHT_SHOULDER -> armOffset(model.rightArm.xRot, model.rightArm.zRot, yaw)
                    .add(0, breathe, 0);
            case LEFT_SHOULDER -> armOffset(model.leftArm.xRot, -model.leftArm.zRot, yaw)
                    .add(0, breathe, 0);
            // Kafa govdeyle birlikte nefes aliyor, ayrica hafif one egilme
            case HEAD -> new Vec3(0, breathe * 1.15, 0);
            default -> new Vec3(0, breathe, 0);
        };
    }

    /**
     * Omuz kristalinin kol donusuyle savrulmasi.
     *
     * Kol omuzdan doner; omuzdaki kristal donus merkezine yakin oldugu
     * icin kucuk bir yay ciziyor. Yarıcap kolun uzunlugu degil OMUZ
     * kalinligi kadar.
     */
    private static Vec3 armOffset(float xRot, float zRot, float yaw) {
        double reach = 1.1;   // omuz yaricapi (blok)

        double forward = -Math.sin(xRot) * reach;
        double up = -Math.sin(zRot) * reach;

        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        // Ileri yonu devin baktigi yone cevir
        return new Vec3(-forward * sin, up, forward * cos);
    }
}
