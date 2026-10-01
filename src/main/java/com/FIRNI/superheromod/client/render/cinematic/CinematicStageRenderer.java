package com.FIRNI.superheromod.client.render.cinematic;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Visual-only studio above world geometry. Gameplay actors never leave their saved positions. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class CinematicStageRenderer {
    private static final ResourceLocation SAND=new ResourceLocation("minecraft","block/sand");
    @SubscribeEvent public static void hideHand(RenderHandEvent event) {
        if(CinematicClient.shouldDrive())event.setCanceled(true);
    }
    @SubscribeEvent public static void hideHud(RenderGuiOverlayEvent.Pre event) {
        if(CinematicClient.shouldDrive())event.setCanceled(true);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_SKY)return;
        var frame=CinematicClient.visualFrame();
        if(frame==null||!frame.definition().isolatedStage)return;
        var mc=Minecraft.getInstance();var stack=event.getPoseStack();
        var offset=frame.stage().origin().subtract(event.getCamera().getPosition());
        var buffers=mc.renderBuffers().bufferSource();
        var type=RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        var texture=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SAND);
        stack.pushPose();
        try {
            stack.translate(offset.x,offset.y,offset.z);
            var m=stack.last().pose();var n=stack.last().normal();var out=buffers.getBuffer(type);
            // Tiled floor has real depth. The distant enclosing surfaces disappear into scene fog.
            for(int x=-48;x<48;x+=4)for(int z=-48;z<48;z+=4) {
                float[][] points={{x,-.025f,z},{x,-.025f,z+4},{x+4,-.025f,z+4},{x+4,-.025f,z}};
                for(int i=0;i<4;i++)out.vertex(m,points[i][0],points[i][1],points[i][2])
                        .color(.48f,.43f,.35f,1).uv(i<2?texture.getU0():texture.getU1(),i==0||i==3?texture.getV0():texture.getV1())
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n,0,1,0).endVertex();
            }
            float[][][] walls={{{-48,-1,-48},{48,-1,-48},{48,48,-48},{-48,48,-48}},
                    {{48,-1,48},{-48,-1,48},{-48,48,48},{48,48,48}},
                    {{-48,-1,48},{-48,-1,-48},{-48,48,-48},{-48,48,48}},
                    {{48,-1,-48},{48,-1,48},{48,48,48},{48,48,-48}},
                    {{-48,48,-48},{48,48,-48},{48,48,48},{-48,48,48}}};
            for(var wall:walls)for(int i=0;i<4;i++)out.vertex(m,wall[i][0],wall[i][1],wall[i][2])
                    .color(.28f,.25f,.21f,1).uv(i<2?texture.getU0():texture.getU1(),i==0||i==3?texture.getV0():texture.getV1())
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n,0,-1,0).endVertex();
            buffers.endBatch(type);
        } finally {stack.popPose();}
    }
}
