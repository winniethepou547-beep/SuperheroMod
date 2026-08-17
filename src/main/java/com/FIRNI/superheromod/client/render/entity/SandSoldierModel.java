package com.FIRNI.superheromod.client.render.entity;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.sandman.SandSoldierEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Kum askerinin modeli.
 *
 * Insan oranlarindan esinleniyor ama birebir Steve degil: omuzlar daha genis,
 * uzuvlar daha kalin ve govdeye kum yumrulari eklenmis. Yumrular ana parcalarin
 * COCUGU olarak ekleniyor, boylece animasyonda kendiliginden takip ediyorlar.
 *
 * Doku olarak vanilla kum blogu kullaniliyor; bu yuzden atlas 16x16 ve tum
 * yuzler ayni bolgeden orneklem aliyor. Kum dokusu duzgun bir desen olmadigi
 * icin bu tekrar goze batmiyor ve ayri bir doku dosyasina gerek kalmiyor.
 */
public class SandSoldierModel extends EntityModel<SandSoldierEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(SuperheroMod.MODID, "sand_soldier"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    /** Olusmanin ilk asamasindaki yer kum yigini. */
    private final ModelPart mound;

    public SandSoldierModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.mound = root.getChild("mound");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // --- Kafa ---
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8),
                PartPose.ZERO);
        // Kafanin uzerinde duzensiz kum yumrulari
        head.addOrReplaceChild("head_chunk_a",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.0f, -10.0f, -2.0f, 4, 2, 4),
                PartPose.ZERO);
        head.addOrReplaceChild("head_chunk_b",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(2.0f, -7.0f, -5.0f, 3, 3, 2),
                PartPose.ZERO);

        // --- Govde: Steve'den daha genis ve kalin ---
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.5f, 0.0f, -2.5f, 9, 12, 5),
                PartPose.ZERO);
        body.addOrReplaceChild("chest_chunk",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.0f, 1.0f, -4.0f, 6, 5, 2),
                PartPose.ZERO);
        body.addOrReplaceChild("back_chunk",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.0f, 4.0f, 2.5f, 4, 6, 2),
                PartPose.ZERO);

        // --- Kollar: kalin, omuzda kum yumrusu ---
        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -2.0f, -2.5f, 5, 12, 5),
                PartPose.offset(-5.5f, 2.0f, 0.0f));
        rightArm.addOrReplaceChild("right_shoulder",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.0f, -3.5f, -3.5f, 7, 4, 7),
                PartPose.ZERO);

        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.0f, -2.0f, -2.5f, 5, 12, 5),
                PartPose.offset(5.5f, 2.0f, 0.0f));
        leftArm.addOrReplaceChild("left_shoulder",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.0f, -3.5f, -3.5f, 7, 4, 7),
                PartPose.ZERO);

        // --- Bacaklar ---
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.5f, 0.0f, -2.5f, 5, 12, 5),
                PartPose.offset(-2.2f, 12.0f, 0.0f));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.5f, 0.0f, -2.5f, 5, 12, 5),
                PartPose.offset(2.2f, 12.0f, 0.0f));

        // --- Yer kum yigini (sadece olusmanin basinda) ---
        root.addOrReplaceChild("mound",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-6.0f, 0.0f, -6.0f, 12, 3, 12),
                PartPose.offset(0.0f, 21.0f, 0.0f));

        return LayerDefinition.create(mesh, 16, 16);
    }

    // ------------------------------------------------------------------

    @Override
    public void setupAnim(SandSoldierEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {

        float progress = entity.getSpawnProgress();

        // --- Normal duruş ve yuruyus ---
        head.xRot = headPitch * ((float) Math.PI / 180f);
        head.yRot = netHeadYaw * ((float) Math.PI / 180f);

        rightArm.xRot = Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * 1.4f * limbSwingAmount;
        leftArm.xRot = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        rightLeg.xRot = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        leftLeg.xRot = Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * 1.4f * limbSwingAmount;

        rightArm.zRot = 0.08f;
        leftArm.zRot = -0.08f;
        body.xRot = 0f;

        // Kum kutlesi oldugu icin hafif nefes/oturma hareketi
        float breathe = Mth.sin(ageInTicks * 0.06f) * 0.02f;
        body.y = breathe;

        // --- Saldiri savurmasi ---
        float attack = entity.getAttackAnim(0f);
        if (attack > 0f) {
            float swing = Mth.sin(attack * (float) Math.PI);
            rightArm.xRot = -1.9f * swing;
            rightArm.yRot = -0.35f * swing;
        }

        applySpawnStages(progress);
    }

    /**
     * Dokumandaki 8 asamali olusma.
     *
     * Bacak ve govdede sadece olcek degil KONUM da kaydiriliyor: yalnizca
     * yScale kucultulseydi uzuvlar pivotlarindan asagi/yukari sarkardi.
     * Bacaklarin tabani yerde, govdenin tabani kalcada sabit tutuluyor;
     * boylece asker gercekten yerden yukseliyormus gibi duruyor.
     */
    private void applySpawnStages(float progress) {
        boolean complete = progress >= 1.0f;

        // 8. asama: yigin kaybolur
        mound.visible = !complete;
        if (!complete) {
            float moundFade = 1.0f - Mth.clamp((progress - 0.75f) / 0.25f, 0f, 1f);
            float moundGrow = Mth.clamp(progress / 0.125f, 0f, 1f);
            float scale = Math.max(0.01f, moundGrow * moundFade);
            mound.xScale = scale;
            mound.zScale = scale;
            mound.yScale = Math.max(0.01f, moundFade);
        }

        if (complete) {
            resetScales();
            return;
        }

        // 2-3. asama: ayaklar, sonra bacaklar yukselir
        float legs = stage(progress, 0.125f, 0.375f);
        setLimb(rightLeg, legs, -2.2f, 12.0f, 12.0f);
        setLimb(leftLeg, legs, 2.2f, 12.0f, 12.0f);

        // 4. asama: govde olusur — tabani kalcada sabit
        float torso = stage(progress, 0.375f, 0.5f);
        body.visible = torso > 0f;
        body.yScale = Math.max(0.01f, torso);
        body.xScale = Mth.lerp(torso, 0.6f, 1.0f);
        body.zScale = Mth.lerp(torso, 0.6f, 1.0f);
        body.y = 12.0f - 12.0f * torso;

        // 5. asama: kollar
        float arms = stage(progress, 0.5f, 0.625f);
        setLimb(rightArm, arms, -5.5f, 2.0f, 0.0f);
        setLimb(leftArm, arms, 5.5f, 2.0f, 0.0f);

        // 6-7. asama: kafa, sonra yuz detaylari
        float headStage = stage(progress, 0.625f, 0.875f);
        head.visible = headStage > 0f;
        float headScale = Math.max(0.01f, headStage);
        head.xScale = headScale;
        head.yScale = headScale;
        head.zScale = headScale;
        head.y = 12.0f - 12.0f * torso;   // govdeyle birlikte yukselir
    }

    /**
     * Uzvu buyutur ve tabanini sabit tutar.
     *
     * @param anchorDrop uzvun tam boydayken pivotunun asagi kayacagi miktar
     */
    private void setLimb(ModelPart part, float amount, float x, float baseY, float anchorDrop) {
        part.visible = amount > 0f;
        float scale = Math.max(0.01f, amount);

        part.xScale = scale;
        part.yScale = scale;
        part.zScale = scale;
        part.x = x;
        part.y = baseY + anchorDrop * (1.0f - amount);
    }

    private void resetScales() {
        for (ModelPart part : new ModelPart[]{head, body, rightArm, leftArm, rightLeg, leftLeg}) {
            part.visible = true;
            part.xScale = 1f;
            part.yScale = 1f;
            part.zScale = 1f;
        }
        head.y = 0f;
        rightArm.x = -5.5f;
        rightArm.y = 2f;
        leftArm.x = 5.5f;
        leftArm.y = 2f;
        rightLeg.x = -2.2f;
        rightLeg.y = 12f;
        leftLeg.x = 2.2f;
        leftLeg.y = 12f;
    }

    /** progress'in [from, to] araligindaki 0..1 karsiligi. */
    private static float stage(float progress, float from, float to) {
        return Mth.clamp((progress - from) / (to - from), 0f, 1f);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight,
                               int packedOverlay, float r, float g, float b, float a) {
        root.render(pose, buffer, packedLight, packedOverlay, r, g, b, a);
    }
}
