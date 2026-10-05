package com.FIRNI.superheromod.mixin;

import com.FIRNI.superheromod.client.render.batman.BatmanVision;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The outline's colour for what Batman sees (orange thermal heat, red in his smoke); client only. */
@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void superheromod$batmanColour(CallbackInfoReturnable<Integer> cir) {
        int c = BatmanVision.outlineColor((Entity) (Object) this);
        if (c >= 0) cir.setReturnValue(c);
    }
}
