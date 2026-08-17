package com.FIRNI.superheromod.client.render.entity;

import com.FIRNI.superheromod.heroes.sandman.SandSoldierEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Kum askerinin cizicisi.
 *
 * Doku olarak vanilla kum blogunun kendi dosyasi kullaniliyor; ayri bir entity
 * dokusu uretmeye gerek kalmiyor ve asker tam olarak dunyadaki kumla ayni
 * malzemeden gorunuyor.
 */
public class SandSoldierRenderer extends MobRenderer<SandSoldierEntity, SandSoldierModel> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/block/sand.png");

    public SandSoldierRenderer(EntityRendererProvider.Context context) {
        super(context, new SandSoldierModel(context.bakeLayer(SandSoldierModel.LAYER)), 0.4f);
    }

    @Override
    public ResourceLocation getTextureLocation(SandSoldierEntity entity) {
        return TEXTURE;
    }

    /** Dagilirken asker cokerek kuculur. */
    @Override
    protected void scale(SandSoldierEntity entity, PoseStack pose, float partialTick) {
        float crumble = entity.getCrumbleProgress();
        if (crumble <= 0f) return;

        float remaining = 1.0f - crumble;
        // Yatayda daha az, dikeyde daha cok kuculur — yere coken kum hissi
        pose.scale(
                Math.max(0.01f, 0.35f + remaining * 0.65f),
                Math.max(0.01f, remaining),
                Math.max(0.01f, 0.35f + remaining * 0.65f));
    }
}
