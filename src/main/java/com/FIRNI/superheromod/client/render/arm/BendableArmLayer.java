package com.FIRNI.superheromod.client.render.arm;

import com.FIRNI.superheromod.client.render.HeroArmPose;
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
 * Bukulebilir sag kolu cizer.
 *
 * Vanilla kol parcasi HeroArmPose tarafindan skipDraw ile cizimden cikarilir;
 * omuz DONUSUMU yine vanilla parcadan alinir (translateAndRotate), uzerine
 * ust kol + on kol ayri ayri cizilir. On kol dirsekten bagimsiz doner.
 */
public class BendableArmLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private final ModelPart wideUpper, wideFore, wideSleeveUpper, wideSleeveFore;
    private final ModelPart slimUpper, slimFore, slimSleeveUpper, slimSleeveFore;

    public BendableArmLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);

        ModelPart wide = BendableArm.createLayer(false, false).bakeRoot();
        ModelPart wideSleeve = BendableArm.createLayer(false, true).bakeRoot();
        ModelPart slim = BendableArm.createLayer(true, false).bakeRoot();
        ModelPart slimSleeve = BendableArm.createLayer(true, true).bakeRoot();

        this.wideUpper = wide.getChild("upper");
        this.wideFore = wide.getChild("fore");
        this.wideSleeveUpper = wideSleeve.getChild("upper");
        this.wideSleeveFore = wideSleeve.getChild("fore");

        this.slimUpper = slim.getChild("upper");
        this.slimFore = slim.getChild("fore");
        this.slimSleeveUpper = slimSleeve.getChild("upper");
        this.slimSleeveFore = slimSleeve.getChild("fore");
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        float bend = HeroArmPose.getElbowBend(player);
        if (bend <= 0.001f) return;   // bukulme yok -> vanilla kol cizilsin

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        if (!model.rightArm.visible) return;

        boolean slim = "slim".equals(player.getModelName());

        ModelPart upper = slim ? slimUpper : wideUpper;
        ModelPart fore = slim ? slimFore : wideFore;

        fore.xRot = -bend;

        VertexConsumer body = buffer.getBuffer(
                RenderType.entityTranslucent(player.getSkinTextureLocation()));

        poseStack.pushPose();
        // Omuz uzayina gir — konum ve donus vanilla koldan geliyor
        model.rightArm.translateAndRotate(poseStack);

        upper.render(poseStack, body, packedLight, OverlayTexture.NO_OVERLAY);
        fore.render(poseStack, body, packedLight, OverlayTexture.NO_OVERLAY);

        // Dis katman (sleeve) — sadece gorunuyorsa
        if (model.rightSleeve.visible) {
            ModelPart sUpper = slim ? slimSleeveUpper : wideSleeveUpper;
            ModelPart sFore = slim ? slimSleeveFore : wideSleeveFore;
            sFore.xRot = -bend;

            sUpper.render(poseStack, body, packedLight, OverlayTexture.NO_OVERLAY);
            sFore.render(poseStack, body, packedLight, OverlayTexture.NO_OVERLAY);
        }

        poseStack.popPose();
    }
}
