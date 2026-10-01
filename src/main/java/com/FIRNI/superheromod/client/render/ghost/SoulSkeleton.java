package com.FIRNI.superheromod.client.render.ghost;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A glowing skeleton figure (the soul), drawn additively so it reads as light, not bone. */
public final class SoulSkeleton {
    private static final ResourceLocation BONES = new ResourceLocation("minecraft", "textures/entity/skeleton/skeleton.png");
    private static HumanoidModel<LivingEntity> model;
    private SoulSkeleton() {}

    /**
     * @param agony 0 = standing, arms low; 1 = head thrown back, arms flung wide, shaking
     * @param brightness 0..1, fades the whole figure
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 at, float facing, float height, float t,
                            float agony, float r, float g, float b, float brightness, boolean halo) {
        if (model == null) model = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SKELETON));
        float jitter = (Mth.sin(t * 5.3f) * .06f + Mth.sin(t * 11.7f) * .03f) * agony;
        model.young = false;
        model.setAllVisible(true);
        model.head.xRot = -.08f - .7f * agony + jitter; model.head.yRot = jitter * 1.5f; model.head.zRot = 0;
        model.hat.copyFrom(model.head);
        model.body.xRot = -.12f * agony; model.body.yRot = 0;
        model.rightArm.xRot = -.1f - .2f * agony + jitter; model.rightArm.yRot = 0; model.rightArm.zRot = .12f + 1.2f * agony + jitter;
        model.leftArm.xRot = -.1f - .2f * agony - jitter; model.leftArm.yRot = 0; model.leftArm.zRot = -.12f - 1.2f * agony - jitter;
        model.rightLeg.xRot = .05f + .1f * agony + jitter; model.rightLeg.yRot = 0; model.rightLeg.zRot = .05f + .06f * agony;
        model.leftLeg.xRot = -.05f - .1f * agony - jitter; model.leftLeg.yRot = 0; model.leftLeg.zRot = -.05f - .06f * agony;
        float scale = height / 1.95f;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing));
        pose.scale(-scale, -scale, scale);
        pose.translate(0, -1.501, 0);
        int full = 15728880;
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(BONES)), full, OverlayTexture.NO_OVERLAY, r * brightness, g * brightness, b * brightness, 1);
        if (halo) {
            pose.scale(1.05f, 1.02f, 1.05f);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(BONES)), full, OverlayTexture.NO_OVERLAY, r * .7f * brightness, g * .35f * brightness, b * .2f * brightness, 1);
        }
        pose.popPose();
    }
}
