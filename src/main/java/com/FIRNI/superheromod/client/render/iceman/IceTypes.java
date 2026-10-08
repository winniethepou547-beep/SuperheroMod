package com.FIRNI.superheromod.client.render.iceman;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Iceman's own render type: the light added over his ice (gloss, eyes, glowing cracks): additive, both sides, no depth written. */
final class IceTypes extends RenderType {
    static final RenderType GLINT = create("iceman_glint", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false));

    private IceTypes() { super("unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {}); }
}
