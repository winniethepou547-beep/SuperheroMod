package com.FIRNI.superheromod.client.render.entity;

import com.FIRNI.superheromod.heroes.sandman.SandSoldierEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Kum askerinin cizicisi. Normal ve dev asker AYNI ciziciyi kullanir; fark
 * sadece olcek. Boylece dev icin ayri model/cizici cogaltmak gerekmiyor.
 *
 * Doku olarak vanilla kum blogunun kendi dosyasi kullaniliyor; ayri bir entity
 * dokusu uretmeye gerek kalmiyor ve asker tam olarak dunyadaki kumla ayni
 * malzemeden gorunuyor.
 */
public class SandSoldierRenderer<T extends SandSoldierEntity>
        extends MobRenderer<T, SandSoldierModel<T>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/block/sand.png");

    private final float baseScale;

    public SandSoldierRenderer(EntityRendererProvider.Context context,
                               float baseScale, float shadowSize) {
        super(context, new SandSoldierModel<>(context.bakeLayer(SandSoldierModel.LAYER)), shadowSize);
        this.baseScale = baseScale;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(T entity, PoseStack pose, float partialTick) {
        if (baseScale != 1.0f) {
            pose.scale(baseScale, baseScale, baseScale);
        }

        // Dagilirken asker cokerek kuculur
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
