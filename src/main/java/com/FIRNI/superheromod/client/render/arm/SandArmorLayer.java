package com.FIRNI.superheromod.client.render.arm;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.ClientSandArmorData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Sandman'in uzerinde biriken kum zirhi.
 *
 * Dokumandaki katmanli yaklasim: tek bir "zirhli model" yok, normal modelin
 * uzerine seviye seviye ek geometri aciliyor.
 *
 *   Seviye 1  omuzlarda ekstra kum
 *   Seviye 2  gogus kalinlasir
 *   Seviye 3  kollar buyur
 *   Seviye 4  sirt ve kalca plakalari
 *   Seviye 5  belirgin agir govde (bacaklar ve boyun)
 *
 * Parcalar oyuncunun kendi model parcalarinin donusumunu miras aliyor
 * (translateAndRotate), boylece yururken/saldirirken zirh de dogru oynuyor.
 *
 * Doku vanilla kum blogu — ayri doku dosyasi gerekmiyor.
 */
public class SandArmorLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            new ResourceLocation(SuperheroMod.MODID, "sand_armor"), "main");

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/block/sand.png");

    private final ModelPart rightShoulder;
    private final ModelPart leftShoulder;
    private final ModelPart chest;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart back;
    private final ModelPart hips;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart neck;

    public SandArmorLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            ModelPart root) {
        super(parent);

        this.rightShoulder = root.getChild("right_shoulder");
        this.leftShoulder = root.getChild("left_shoulder");
        this.chest = root.getChild("chest");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.back = root.getChild("back");
        this.hips = root.getChild("hips");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.neck = root.getChild("neck");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // --- Seviye 1: omuzlar ---
        root.addOrReplaceChild("right_shoulder",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0f, -3.0f, -3.0f, 5, 4, 6),
                PartPose.ZERO);
        root.addOrReplaceChild("left_shoulder",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.0f, -3.0f, -3.0f, 5, 4, 6),
                PartPose.ZERO);

        // --- Seviye 2: gogus ---
        root.addOrReplaceChild("chest",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.5f, 1.0f, -3.5f, 9, 7, 2),
                PartPose.ZERO);

        // --- Seviye 3: kollar ---
        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.5f, 1.0f, -2.5f, 5, 8, 5),
                PartPose.ZERO);
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.5f, 1.0f, -2.5f, 5, 8, 5),
                PartPose.ZERO);

        // --- Seviye 4: sirt ve kalca ---
        root.addOrReplaceChild("back",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.0f, 1.5f, 1.8f, 6, 8, 2),
                PartPose.ZERO);
        root.addOrReplaceChild("hips",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5.0f, 8.5f, -3.0f, 10, 4, 6),
                PartPose.ZERO);

        // --- Seviye 5: bacaklar ve boyun ---
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.6f, 0.5f, -2.6f, 5, 7, 5),
                PartPose.ZERO);
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-2.4f, 0.5f, -2.6f, 5, 7, 5),
                PartPose.ZERO);
        root.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-3.0f, -1.5f, -3.0f, 6, 3, 6),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 16, 16);
    }

    // ------------------------------------------------------------------

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        int level = ClientSandArmorData.get(player);
        if (level <= 0) return;

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        VertexConsumer sand = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        // Seviye 1 — omuzlar
        drawOn(pose, model.rightArm, rightShoulder, sand, packedLight);
        drawOn(pose, model.leftArm, leftShoulder, sand, packedLight);

        if (level >= 2) {
            drawOn(pose, model.body, chest, sand, packedLight);
        }
        if (level >= 3) {
            drawOn(pose, model.rightArm, rightArm, sand, packedLight);
            drawOn(pose, model.leftArm, leftArm, sand, packedLight);
        }
        if (level >= 4) {
            drawOn(pose, model.body, back, sand, packedLight);
            drawOn(pose, model.body, hips, sand, packedLight);
        }
        if (level >= 5) {
            drawOn(pose, model.rightLeg, rightLeg, sand, packedLight);
            drawOn(pose, model.leftLeg, leftLeg, sand, packedLight);
            drawOn(pose, model.body, neck, sand, packedLight);
        }
    }

    /**
     * Zirh parcasini oyuncunun ilgili uzvunun uzayinda cizer.
     *
     * Parcayi kendi basina cizmek yerine once uzvun donusumune giriyoruz;
     * boylece kol sallanirken omuz zirhi de onunla birlikte oynuyor ve
     * ayri bir animasyon yazmak gerekmiyor.
     */
    private static void drawOn(PoseStack pose, ModelPart parent, ModelPart piece,
                               VertexConsumer buffer, int packedLight) {
        if (!parent.visible) return;

        pose.pushPose();
        parent.translateAndRotate(pose);
        piece.render(pose, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
