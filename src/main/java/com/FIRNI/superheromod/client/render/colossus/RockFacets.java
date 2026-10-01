package com.FIRNI.superheromod.client.render.colossus;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Cached irregular ellipsoid mesh: broad fracture planes, one continuous closed rock. */
public final class RockFacets {
    private static final Vector3f[][] FACES=build();
    private static Vector3f[][] build(){
        var faces=new java.util.ArrayList<Vector3f[]>();
        Vector3f[][] rings=new Vector3f[5][10];
        for(int r=0;r<5;r++)for(int i=0;i<10;i++){
            double lat=-Math.PI/2+(r+1)*Math.PI/6, a=i*Math.PI/5;
            float variation=(float)(.94+.06*Math.sin(i*2.7+r*1.9));
            rings[r][i]=new Vector3f((float)(Math.cos(lat)*Math.cos(a))*variation,
                    (float)Math.sin(lat)*.88f,(float)(Math.cos(lat)*Math.sin(a))*variation);
        }
        for(int i=0;i<10;i++){
            int next=(i+1)%10;
            Vector3f bottom=new Vector3f(0,-.93f,0),top=new Vector3f(.08f,.98f,-.04f);
            faces.add(new Vector3f[]{bottom,rings[0][i],rings[0][next],bottom});
            for(int r=0;r<4;r++)faces.add(new Vector3f[]{rings[r][i],rings[r+1][i],rings[r+1][next],rings[r][next]});
            faces.add(new Vector3f[]{rings[4][i],top,rings[4][next],rings[4][next]});
        }
        return faces.toArray(Vector3f[][]::new);
    }
    public static void render(VertexConsumer out,Matrix4f matrix,TextureAtlasSprite texture,int light,
                              double x,double y,double z,double radius,float spin){
        Matrix4f m=new Matrix4f(matrix).translate((float)x,(float)y,(float)z)
                .rotateY(spin).rotateX(spin*.31f).scale((float)radius);
        Matrix3f normal=m.normal(new Matrix3f());
        for(int i=0;i<FACES.length;i++){
            Vector3f[] f=FACES[i];
            Vector3f n=new Vector3f(f[1]).sub(f[0]).cross(new Vector3f(f[2]).sub(f[0])).normalize();
            float shade=.87f+(i%4)*.035f;
            for(int v=0;v<4;v++){
                Vector3f p=f[v];
                out.vertex(m,p.x,p.y,p.z).color(shade,shade,shade,1)
                        .uv(v==0||v==3?texture.getU0():texture.getU1(),v<2?texture.getV0():texture.getV1())
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal,n.x,n.y,n.z).endVertex();
            }
        }
    }
}
