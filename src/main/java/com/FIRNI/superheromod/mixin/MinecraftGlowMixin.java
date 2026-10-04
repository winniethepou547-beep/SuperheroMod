package com.FIRNI.superheromod.mixin;

import com.FIRNI.superheromod.client.render.batman.BatmanVision;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Batman's thermal sensor and smoke: the ones he should see are drawn with the game's outline, through walls, on his screen only. */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void superheromod$batmanSees(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (BatmanVision.outline(entity)) cir.setReturnValue(true);
    }
}
