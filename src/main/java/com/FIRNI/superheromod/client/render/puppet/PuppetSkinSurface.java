package com.FIRNI.superheromod.client.render.puppet;

import com.FIRNI.superheromod.core.cinematic.Skinning;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Continuous limb surfaces: no internal caps or separate elbow/knee cubes. */
final class PuppetSkinSurface {
    private record Vertex(Vector3f bind, float u, float v, float weight) {}
    private final List<Vertex> vertices = new ArrayList<>();
    private final Vector3f[] points = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f scratch = new Vector3f(), edge = new Vector3f(), normal = new Vector3f();

    PuppetSkinSurface(float x, float y, float z, int width, int height, int depth,
                      int u, int v, float joint, boolean reverse) {
        // One ring per pixel. Shared ring positions and weights keep the surface closed.
        for (int row = 0; row < height; row++) {
            float a = y + row, b = a + 1;
            face(x,a,z, x+width,a,z, x+width,b,z, x,b,z,
                    u+depth,v+depth+row,width,1, joint,reverse, 0,0,-1);
            face(x,a,z+depth, x+width,a,z+depth, x+width,b,z+depth, x,b,z+depth,
                    u+depth+width+depth,v+depth+row,width,1,joint,reverse,0,0,1);
            face(x,a,z, x,a,z+depth, x,b,z+depth, x,b,z,
                    u,v+depth+row,depth,1,joint,reverse,-1,0,0);
            face(x+width,a,z, x+width,a,z+depth, x+width,b,z+depth, x+width,b,z,
                    u+depth+width,v+depth+row,depth,1,joint,reverse,1,0,0);
        }
        face(x,y,z,x+width,y,z,x+width,y,z+depth,x,y,z+depth,
                u+depth,v,width,depth,joint,reverse,0,-1,0);
        face(x,y+height,z,x+width,y+height,z,x+width,y+height,z+depth,x,y+height,z+depth,
                u+depth+width,v,width,depth,joint,reverse,0,1,0);
    }

    private void face(float ax,float ay,float az,float bx,float by,float bz,
                      float cx,float cy,float cz,float dx,float dy,float dz,
                      float u,float v,float du,float dv,float joint,boolean reverse,
                      float nx,float ny,float nz) {
        Vector3f[] p={new Vector3f(ax,ay,az),new Vector3f(bx,by,bz),new Vector3f(cx,cy,cz),new Vector3f(dx,dy,dz)};
        boolean flip=new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0])).dot(nx,ny,nz)<0;
        float[] us={u,u+du,u+du,u}, vs={v,v,v+dv,v+dv};
        for(int i=0;i<4;i++) {
            int k=flip?3-i:i;
            float w=Skinning.weight(p[k].y,joint-2,joint+2);
            vertices.add(new Vertex(p[k].div(16),us[k]/64,vs[k]/64,reverse?1-w:w));
        }
    }

    void render(PoseStack stack, VertexConsumer buffer, Matrix4f first, Matrix4f second,
                int light, int overlay) {
        var pose=stack.last();
        for(int i=0;i<vertices.size();i+=4) {
            for(int k=0;k<4;k++) {
                Vertex vertex=vertices.get(i+k);
                Skinning.position(first,second,vertex.weight,vertex.bind,points[k],scratch);
            }
            normal.set(points[1]).sub(points[0]);
            edge.set(points[2]).sub(points[0]);
            normal.cross(edge);
            if(normal.lengthSquared()<1e-12f)continue;
            normal.normalize();
            for(int k=0;k<4;k++) {
                Vertex vertex=vertices.get(i+k); Vector3f p=points[k];
                buffer.vertex(pose.pose(),p.x,p.y,p.z).color(1f,1f,1f,1f)
                        .uv(vertex.u,vertex.v).overlayCoords(overlay).uv2(light)
                        .normal(pose.normal(),normal.x,normal.y,normal.z).endVertex();
            }
        }
    }
}
