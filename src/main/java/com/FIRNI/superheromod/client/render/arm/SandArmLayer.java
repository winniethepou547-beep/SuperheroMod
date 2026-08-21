package com.FIRNI.superheromod.client.render.arm;

import com.FIRNI.superheromod.client.render.ClientSandArmData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * UZAYAN KUM KOLU.
 *
 * Kol OYUNCU MODELININ KATMANI olarak ciziliyor, dunya uzayinda degil.
 * Bu fark belirleyici: omuz donusumunun (translateAndRotate) icine
 * girildigi icin ek parcalar kolun tam ucundan devam ediyor ve oyuncu
 * hareket etse de bedenden kopmuyor.
 *
 * Onceki surum dunya koordinatlariyla kum bloklari ciziyordu; sonuc ne
 * kola yapisikti ne de kola benziyordu.
 *
 * Parcalar oyuncunun KENDI SKININDEN aliniyor -- ayni on kol dilimi
 * tekrarlanarak. Boylece uzayan kisim oyuncunun teni/giysisiyle ayni
 * renkte cikiyor ve gercekten "kolun devami" gibi okunuyor.
 */
public class SandArmLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Vanilla kol 12 piksel; ek parcalar bunun ucundan basliyor. */
    private static final float ARM_LENGTH_PX = 10.0f;
    /** 1 blok = 16 piksel. */
    private static final float PX_PER_BLOCK = 16.0f;
    /** Cok uzun kolda parca sayisi patlamasin. */
    private static final int MAX_SEGMENTS = 26;

    private final ModelPart wideSeg, wideFist, wideSleeveSeg, wideSleeveFist;
    private final ModelPart slimSeg, slimFist, slimSleeveSeg, slimSleeveFist;

    public SandArmLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);

        ModelPart wide = SandArm.createLayer(false, false).bakeRoot();
        ModelPart wideSleeve = SandArm.createLayer(false, true).bakeRoot();
        ModelPart slim = SandArm.createLayer(true, false).bakeRoot();
        ModelPart slimSleeve = SandArm.createLayer(true, true).bakeRoot();

        this.wideSeg = wide.getChild("segment");
        this.wideFist = wide.getChild("fist");
        this.wideSleeveSeg = wideSleeve.getChild("segment");
        this.wideSleeveFist = wideSleeve.getChild("fist");

        this.slimSeg = slim.getChild("segment");
        this.slimFist = slim.getChild("fist");
        this.slimSleeveSeg = slimSleeve.getChild("segment");
        this.slimSleeveFist = slimSleeve.getChild("fist");
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        float lengthBlocks = ClientSandArmData.lengthOf(player.getId());
        if (lengthBlocks <= 0.02f) return;

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        if (!model.rightArm.visible) return;

        boolean slim = "slim".equals(player.getModelName());

        ModelPart seg = slim ? slimSeg : wideSeg;
        ModelPart fist = slim ? slimFist : wideFist;

        VertexConsumer body = buffer.getBuffer(
                RenderType.entityTranslucent(player.getSkinTextureLocation()));

        poseStack.pushPose();

        // Omuz uzayina gir. Konum ve donus KOLUN KENDISINDEN geliyor;
        // bu yuzden ek parcalar kolun ucunda dogru acida duruyor.
        model.rightArm.translateAndRotate(poseStack);

        // Model uzayinda kol asagi (+Y) dogru uzuyor. Kol one bakarken
        // (HeroArmPose xRot'u -90'a getiriyor) bu yon dunyada ileri oluyor.
        float totalPx = lengthBlocks * PX_PER_BLOCK;
        int segments = Math.min(MAX_SEGMENTS,
                (int) Math.ceil(totalPx / SandArm.SEGMENT_H));

        boolean sleeve = model.rightSleeve.visible;
        ModelPart sSeg = slim ? slimSleeveSeg : wideSleeveSeg;
        ModelPart sFist = slim ? slimSleeveFist : wideSleeveFist;

        for (int i = 0; i < segments; i++) {
            float offset = ARM_LENGTH_PX + i * SandArm.SEGMENT_H;

            // Son bogum tasmasin: kalan uzunluk kadar kisaltiliyor,
            // yoksa kol hedeflenenden uzun gorunuyor
            float remaining = totalPx - i * SandArm.SEGMENT_H;
            float scale = Math.min(1f, remaining / SandArm.SEGMENT_H);
            if (scale <= 0.01f) break;

            poseStack.pushPose();
            poseStack.translate(0.0f, offset / 16.0f, 0.0f);
            poseStack.scale(1.0f, scale, 1.0f);

            seg.render(poseStack, body, packedLight, OverlayTexture.NO_OVERLAY);
            if (sleeve) sSeg.render(poseStack, body, packedLight, OverlayTexture.NO_OVERLAY);

            poseStack.popPose();
        }

        // YUMRUK kolun tam ucunda
        poseStack.pushPose();
        poseStack.translate(0.0f, (ARM_LENGTH_PX + totalPx) / 16.0f, 0.0f);
        fist.render(poseStack, body, packedLight, OverlayTexture.NO_OVERLAY);
        if (sleeve) sFist.render(poseStack, body, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        poseStack.popPose();
    }
}
