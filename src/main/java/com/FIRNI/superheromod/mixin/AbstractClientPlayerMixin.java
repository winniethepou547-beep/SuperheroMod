package com.FIRNI.superheromod.mixin;

import com.FIRNI.superheromod.client.render.HeroSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kahraman kusanildiginda oyuncunun skinini karakterin skiniyle degistirir.
 *
 * Neden mixin: skin dokusu PlayerRenderer'in her katmani tarafindan
 * getSkinTextureLocation() uzerinden okunuyor (govde, sleeve, bizim
 * BendableArmLayer dahil). Tek noktadan degistirmek hepsini birden dogru
 * yapar; her katmani ayri ayri ezmeye calismak kacak birakirdi.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void superheromod$heroSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;

        ResourceLocation heroSkin = HeroSkins.skinFor(self);
        if (heroSkin != null) {
            cir.setReturnValue(heroSkin);
        }
    }
}
