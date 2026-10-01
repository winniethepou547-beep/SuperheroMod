package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.heroes.sandman.SettledSandBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

public final class SettledSandBallRenderer extends EntityRenderer<SettledSandBallEntity> {
    public SettledSandBallRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius=1.8f; }
    @Override public ResourceLocation getTextureLocation(SettledSandBallEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
    @Override public void render(SettledSandBallEntity entity, float yaw, float partial,
                                 PoseStack pose, MultiBufferSource buffers, int light) {
        var texture = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(new ResourceLocation("minecraft", "block/sand"));
        RockFacets.render(buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)),
                pose.last().pose(), texture, light, 0, 2.046, 0, 2.2, 0);
        super.render(entity,yaw,partial,pose,buffers,light);
    }
}
