package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.heroes.iceman.IceSolidEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws nothing: IceSolidEntity is only the weight of ice that IcemanTrack and the brush sculptures already draw (no
 * shadow, no name, no fire; never even reaches render).
 */
public final class IceSolidRenderer extends EntityRenderer<IceSolidEntity> {
    public IceSolidRenderer(EntityRendererProvider.Context ctx) { super(ctx); shadowRadius = 0; }
    @Override public ResourceLocation getTextureLocation(IceSolidEntity e) { return TextureAtlas.LOCATION_BLOCKS; }
    @Override public boolean shouldRender(IceSolidEntity e, Frustum frustum, double x, double y, double z) { return false; }
}
