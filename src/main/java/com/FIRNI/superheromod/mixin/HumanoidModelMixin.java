package com.FIRNI.superheromod.mixin;

import com.FIRNI.superheromod.client.render.HeroArmPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kahraman pozunu setupAnim BITTIKTEN sonra uygular.
 *
 * RenderPlayerEvent.Pre icinde aci ayarlamak ise yaramiyor: Minecraft o
 * olaydan sonra setupAnim() cagirip tum degerleri yeniden hesapliyor.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void superheromod$applyHeroPose(LivingEntity entity, float limbSwing,
                                            float limbSwingAmount, float ageInTicks,
                                            float netHeadYaw, float headPitch,
                                            CallbackInfo ci) {
        if ((Object) this instanceof PlayerModel<?> model) {
            HeroArmPose.apply(model, entity, ageInTicks);
        }
    }
}
