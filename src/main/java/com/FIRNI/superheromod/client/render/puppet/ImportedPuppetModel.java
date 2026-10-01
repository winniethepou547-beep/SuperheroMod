package com.FIRNI.superheromod.client.render.puppet;

import com.FIRNI.superheromod.core.cinematic.*;
import com.mojang.blaze3d.vertex.*;
import org.joml.Vector3f;

/** Uses the existing entity material batch; triangles are emitted as degenerate quads. */
final class ImportedPuppetModel {
    private static final int[][] GRAIN_FACES={{0,1,3,2},{4,6,7,5},{0,4,5,1},{2,3,7,6},{0,2,6,4},{1,5,7,3}};
    private CinematicPuppet current;
    private final GltfSkinnedModel asset;
    private final GltfSkinnedModel.Sample sample;
    private final Vector3f normal=new Vector3f(),edge=new Vector3f();
    ImportedPuppetModel(String name) {
        asset=GltfSkinnedModel.bundled(name);sample=asset.newSample();
    }
    void warmUp(RigClipPlacement clip) {
        if (clip != null && !asset.hasClip(clip.name()))
            throw new IllegalArgumentException("Missing cinematic clip: " + clip.name());
        if (clip == null) sample.apply(new ActorPose());
        else sample.apply(new ActorPose(), clip.name(), 0, 1, clip.loop());
    }
    void preparePose(CinematicPuppet puppet) {
        RigClipPlacement clip=puppet.track==null?null:puppet.track.rigClip;
        if(clip!=null)sample.preparePose(puppet.pose,clip.name(),clip.seconds(puppet.sample.timeline),clip.weight(puppet.sample.timeline),clip.loop());
    }
    void apply(CinematicPuppet puppet) {
        current=puppet;
        RigClipPlacement clip=puppet.track==null?null:puppet.track.rigClip;
        if(clip==null)sample.apply(puppet.pose);
        else sample.apply(puppet.pose,clip.name(),clip.seconds(puppet.sample.timeline),clip.weight(puppet.sample.timeline),clip.loop(),true);
    }
    void render(PoseStack stack,VertexConsumer buffer,int light,int overlay) {
        var pose=stack.last();
        float dissolve=0;
        if(current.track!=null && current.sample.timeline>=current.track.dissolveStart)
            dissolve=net.minecraft.util.Mth.clamp((current.sample.timeline-current.track.dissolveStart)/(current.track.dissolveEnd-current.track.dissolveStart),0,1);
        if(dissolve>=1)return;
        for(int triangle=0;triangle<asset.triangleCount();triangle++) {
            float fragmentStart=((triangle*73)%569)/569f*.82f;
            float surfaceAlpha=1-net.minecraft.util.Mth.clamp((dissolve-fragmentStart)/.18f,0,1);
            surfaceAlpha=surfaceAlpha*surfaceAlpha*(3-2*surfaceAlpha);
            if(surfaceAlpha<=0)continue;
            int a=asset.index(triangle*3),b=asset.index(triangle*3+1),c=asset.index(triangle*3+2);
            float uvCenter=(asset.u(a)+asset.u(b)+asset.u(c))*64/3;
            boolean leftArmUv=uvCenter>32;
            normal.set(sample.vertex(b)).sub(sample.vertex(a));edge.set(sample.vertex(c)).sub(sample.vertex(a));normal.cross(edge);
            if(normal.lengthSquared()<1e-12f)continue;normal.normalize();
            for(int k=0;k<4;k++) {
                int i=k==0?a:k==1?b:c;Vector3f p=sample.vertex(i);
                Vector3f shading=sample.normal(i);
                buffer.vertex(pose.pose(),p.x,p.y,p.z).color(current.tintR,current.tintG,current.tintB,surfaceAlpha).uv(textureU(i,leftArmUv),textureV(i))
                        .overlayCoords(overlay).uv2(light).normal(pose.normal(),shading.x,shading.y,shading.z).endVertex();
            }
        }
        if(dissolve>0) renderGrains(stack,buffer,light,overlay,dissolve);
    }
    private float textureU(int i,boolean arm) {
        float u=asset.u(i)*64,v=asset.v(i)*64;
        if(current.legacyZombieUv && v>=48) {u+=arm?8:-16;}
        return u/64;
    }
    private float textureV(int i) {
        float v=asset.v(i)*64;
        return (current.legacyZombieUv && v>=48?v-32:v)/64;
    }
    private void renderGrains(PoseStack stack,VertexConsumer out,int light,int overlay,float t) {
        var pose=stack.last();
        // Fixed seed and bounded 64 cubes: seekable debris, no spawned entities.
        for(int g=0;g<64;g++) {
            float start=(g%8)*.055f, age=Math.max(0,(t-start)/(1-start));
            if(age<=0)continue;
            Vector3f base=sample.vertex(asset.index((g*23)% (asset.triangleCount()*3)));
            double angle=g*2.399963;
            float x=base.x+(float)Math.cos(angle)*age*.95f;
            float y=base.y-age*(.7f+(g%5)*.12f);
            float z=base.z+(float)Math.sin(angle)*age*.95f;
            float size=(.014f+(g%3)*.006f)*(1-age);
            for(int f=0;f<6;f++)for(int k=0;k<4;k++){
                int c=GRAIN_FACES[f][k];
                out.vertex(pose.pose(),x+((c&1)==0?-size:size),y+((c&2)==0?-size:size),z+((c&4)==0?-size:size))
                    .color(current.tintR,current.tintG,current.tintB,1-age).uv(k<2?.25f:.5f,k%2==0?.25f:.5f)
                    .overlayCoords(overlay).uv2(light).normal(pose.normal(),0,1,0).endVertex();
            }
        }
    }
}
