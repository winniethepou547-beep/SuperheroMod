package com.FIRNI.superheromod.mixin;

import com.FIRNI.superheromod.client.render.iceman.FrostShell;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Right after a living body's model has its own animation (setupAnim, including what subclasses like the zombie's add
 * after HumanoidModel's), the deep freeze may hold its limbs (Iceman's FrostShell.pose). require = 0: if this ever fails
 * to match, the game still starts (HumanoidModelMixin covers players and plain humanoids).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER),
            require = 0)
    private void superheromod$frozenPose(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (((LivingEntityRenderer<?, ?>) (Object) this).getModel() instanceof HumanoidModel<?> m) FrostShell.pose(m, entity);
    }
}
