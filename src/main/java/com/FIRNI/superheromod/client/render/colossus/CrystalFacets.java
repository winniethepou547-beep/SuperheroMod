package com.FIRNI.superheromod.client.render.colossus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** A bounded mineral cluster with actual tapered facets, rather than stacked cube tips. */
public final class CrystalFacets {
    private static final float[][] SHARDS={
        {0,-.35f,-1,1,.22f},{.85f,-.25f,-.45f,.78f,.19f},
        {-.8f,-.65f,-.35f,.92f,.18f},{.25f,.65f,-.6f,.62f,.16f},
        {-.6f,.25f,-.65f,.58f,.15f},{.1f,-1,-.1f,.55f,.14f}};
    public static void render(PoseStack pose,VertexConsumer out,int light,int damage,int variant) {
        int count=damage==0?6:damage==1?4:2;
        pose.pushPose();
        // Each socket grows out of its own surface, not toward global up.
        if(variant==4)pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        else if(variant==1)pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(48));
        else if(variant==2)pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-52));
        else if(variant==3)pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(32));
        for(int j=0;j<count;j++) {
            float[] s=SHARDS[j];
            Vector3f dir=new Vector3f(s[0],s[1],s[2]).normalize();
            Vector3f u=new Vector3f(dir).cross(0,1,0).normalize();
            Vector3f v=new Vector3f(dir).cross(u).normalize();
            Vector3f base=new Vector3f(dir).mul(-.16f);
            Vector3f mid=new Vector3f(dir).mul(s[3]*.60f);
            Vector3f tip=new Vector3f(dir).mul(s[3]);
            for(int side=0;side<6;side++) {
                Vector3f r0=radial(u,v,side),r1=radial(u,v,side+1);
                Vector3f a=new Vector3f(base).fma(s[4]*.62f,r0),b=new Vector3f(base).fma(s[4]*.62f,r1);
                Vector3f c=new Vector3f(mid).fma(s[4],r1),d=new Vector3f(mid).fma(s[4],r0);
                face(pose,out,light,a,b,c,d,side);
                face(pose,out,light,d,c,tip,tip,side);
            }
        }
        pose.popPose();
    }
    private static Vector3f radial(Vector3f u,Vector3f v,int side) {
        double a=side*Math.PI/3;
        return new Vector3f(u).mul((float)Math.cos(a)).fma((float)Math.sin(a),v);
    }
    private static void face(PoseStack p,VertexConsumer o,int light,Vector3f a,Vector3f b,Vector3f c,Vector3f d,int side) {
        Vector3f n=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
        float shade=.78f+(side%3)*.10f;
        vertex(p,o,light,a,n,0,1,shade);vertex(p,o,light,b,n,1,1,shade);
        vertex(p,o,light,c,n,1,0,shade);vertex(p,o,light,d,n,0,0,shade);
    }
    private static void vertex(PoseStack p,VertexConsumer o,int light,Vector3f v,Vector3f n,float u,float t,float shade) {
        o.vertex(p.last().pose(),v.x,v.y,v.z).color(shade,shade,shade,1).uv(u,t)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(p.last().normal(),n.x,n.y,n.z).endVertex();
    }
}
