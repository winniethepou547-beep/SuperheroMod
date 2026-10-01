package com.FIRNI.superheromod.client.render.cinematic;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.colossus.RockFacets;
import com.FIRNI.superheromod.core.cinematic.CinematicSetPiece;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Bounded, timestamp-driven geometry; seeking/aborting cannot leave spawned debris behind. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class CinematicSetPieceRenderer {
    private static final ResourceLocation SAND=new ResourceLocation("minecraft","block/sand");
    private static final ResourceLocation HAZE=new ResourceLocation(SuperheroMod.MODID,"textures/entity/sand_mist.png");
    private static final Vec3[] DIRECTIONS=directions(32);
    private static final float[][] CORNERS={{-1,-1,0,1},{1,-1,1,1},{1,1,1,0},{-1,1,0,0}};

    private static Vec3[] directions(int count) {
        Vec3[] values=new Vec3[count];
        for(int i=0;i<count;i++) {
            double y=1-2*(i+.5)/count, r=Math.sqrt(1-y*y), a=i*2.3999632297;
            values[i]=new Vec3(Math.cos(a)*r,y,Math.sin(a)*r);
        }
        return values;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var frame=CinematicClient.visualFrame();var mc=Minecraft.getInstance();
        if(frame==null || mc.level==null || frame.definition().setPieces.isEmpty())return;
        var buffers=mc.renderBuffers().bufferSource();
        var solid=RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        var mist=RenderType.entityTranslucent(HAZE);
        var sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(SAND);
        var stack=event.getPoseStack();var camera=event.getCamera();Vec3 cam=camera.getPosition();
        boolean drew=false;
        for(var piece:frame.definition().setPieces) {
            if(!piece.active(frame.tick()))continue;
            Vec3 center=frame.stage().toWorldSpan(piece.center());
            int light=net.minecraft.client.renderer.LightTexture.pack(12,15); // Match the actors in the isolated cinematic stage.
            stack.pushPose();
            try {
                stack.translate(center.x-cam.x,center.y-cam.y,center.z-cam.z);
                var matrix=stack.last().pose();var normal=stack.last().normal();
                var out=buffers.getBuffer(solid);
                float time=frame.tick(), formation=piece.formation(time), scatter=piece.scatter(time);
                if(time<piece.burst()) {
                    float size=piece.radius()*formation;
                    if(size>.001f) {
                        RockFacets.render(out,matrix,sprite,light,0,0,0,size,(time-piece.formStart())*.012f);
                        for(int i=0;i<24;i++) {
                            float growth=piece.spike(time,i);
                            if(growth>.001f) spike(out,matrix,normal,sprite,light,DIRECTIONS[i],
                                    size*.82,size*(.6+(i%4)*.14)*growth,size*.17*growth);
                        }
                    }
                } else {
                    // Outward burst with a gravity arc, ending at zero size. No persistent objects.
                    for(int i=0;i<DIRECTIONS.length;i++) {
                        Vec3 p=fragment(piece,scatter,i);
                        double radius=piece.radius()*(.12+(i%3)*.035)*(1-scatter);
                        if(radius>.001)RockFacets.render(out,matrix,sprite,light,p.x,p.y,p.z,radius,
                                i+scatter*(i%2==0?4:-4));
                    }
                }
                drew=true;
            } finally { stack.popPose(); }
            buffers.endBatch(solid);
            // Only the brief transformation/burst haze uses soft planes; the cage is solid geometry.
            float t=frame.tick(), form=piece.formation(t), scatter=piece.scatter(t);
            float opacity=t<piece.sealed()? (float)Math.sin(Math.PI*form)*.22f
                    :t>=piece.burst()? (float)Math.sin(Math.PI*Math.min(1,scatter*2))*(1-scatter)*.45f:0;
            if(opacity>.001f) for(int i=0;i<12;i++) {
                Vec3 offset=DIRECTIONS[i].scale(piece.radius()*(.7+scatter*2));
                stack.pushPose();
                try {
                    stack.translate(center.x+offset.x-cam.x,center.y+offset.y-cam.y,center.z+offset.z-cam.z);
                    stack.mulPose(camera.rotation());
                    var m=stack.last().pose();var n=stack.last().normal();var out=buffers.getBuffer(mist);
                    float size=piece.radius()*(.7f+scatter);
                    for(var v:CORNERS)out.vertex(m,v[0]*size,v[1]*size,0).color(1f,1f,1f,opacity)
                            .uv(v[2],v[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                            .normal(n,0,0,1).endVertex();
                } finally {stack.popPose();}
            }
        }
        if(drew)buffers.endBatch(mist);
    }

    private static Vec3 fragment(CinematicSetPiece piece,float t,int i) {
        return DIRECTIONS[i].scale(piece.radius()*(.75+3*t))
                .add(0,1.8*t-4*t*t,0);
    }

    private static void spike(VertexConsumer out,Matrix4f m,Matrix3f normal,TextureAtlasSprite texture,
                              int light,Vec3 axis,double baseRadius,double length,double width) {
        Vec3 tangent=axis.cross(Math.abs(axis.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize();
        Vec3 bitangent=axis.cross(tangent).normalize(), base=axis.scale(baseRadius), tip=axis.scale(baseRadius+length);
        Vec3[] corners={base.add(tangent.scale(width)),base.add(bitangent.scale(width)),
                base.subtract(tangent.scale(width)),base.subtract(bitangent.scale(width))};
        for(int side=0;side<4;side++) {
            Vec3 a=corners[side],b=corners[(side+1)%4];
            Vec3 n=b.subtract(a).cross(tip.subtract(a)).normalize();
            Vec3[] face={a,b,tip,tip};
            for(int j=0;j<4;j++) {
                Vec3 p=face[j];out.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(.93f,.9f,.84f,1)
                        .uv(j==0?texture.getU0():texture.getU1(),j<2?texture.getV1():texture.getV0())
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                        .normal(normal,(float)n.x,(float)n.y,(float)n.z).endVertex();
            }
        }
    }
}
