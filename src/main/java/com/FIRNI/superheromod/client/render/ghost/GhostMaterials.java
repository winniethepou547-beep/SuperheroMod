package com.FIRNI.superheromod.client.render.ghost;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Small authored mesh pieces share one neutral material; color is not emissive unless specified. */
public final class GhostMaterials {
    public static final ResourceLocation TEXTURE = new ResourceLocation("superheromod", "textures/entity/ghost_material.png");
    public static ModelPart box(float x,float y,float z,float w,float h,float d) {
        MeshDefinition mesh=new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("piece",CubeListBuilder.create().texOffs(0,0).addBox(x,y,z,w,h,d),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,64).bakeRoot();
    }
    public static void draw(ModelPart part, PoseStack pose, MultiBufferSource buffers, int light, float r,float g,float b) {
        part.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),light,OverlayTexture.NO_OVERLAY,r,g,b,1);
    }
    /** Shared flow material on curved ribbons, with movement inertia in model space. */
    public static void flames(PoseStack pose, MultiBufferSource buffers, float time, float bendX,float bendZ, float scale) {
        plume(pose,buffers,time,bendX,bendZ,scale,false,false);
    }
    /**
     * Cheap flame for things that burn in numbers right in front of the camera (bike wheels,
     * exhausts): a few lobes and no halo, cores or flecks. Big translucent halos there were
     * what cost the frame rate at speed.
     */
    public static void lightFlames(PoseStack pose, MultiBufferSource buffers, float time, float bendZ, float scale) {
        plume(pose,buffers,time,0,bendZ,scale,false,true);
    }
    public static void headFlames(PoseStack pose,MultiBufferSource buffers,float time,float bendX,float bendZ) {
        plume(pose,buffers,time,bendX,bendZ,1,true,false);
    }
    private static double fract(double v){return v-Math.floor(v);}
    /**
     * Translucent shell of fire hugging the whole skull — jaw, cheeks, temples, back and crown —
     * so the head reads as engulfed rather than only burning on top. Thinner over the face.
     */
    private static void aura(VertexConsumer v,PoseStack pose,net.minecraft.world.phys.Vec3 right,net.minecraft.world.phys.Vec3 up,
                             float time,float bendX,float bendZ) {
        double[] heights={-.04,-.20,-.36,-.50}, radii={.25,.30,.30,.22};
        int[] counts={10,14,14,8};
        for(int ring=0;ring<heights.length;ring++)for(int i=0;i<counts[ring];i++) {
            double angle=(i+ring*.5)*Math.PI*2/counts[ring]+time*(ring%2==0?.025:-.02);
            double flicker=.78+.22*Math.sin(time*.63+i*1.9+ring*2.3);
            double lift=.035*Math.sin(time*.31+i*2.7+ring);
            var center=new net.minecraft.world.phys.Vec3(
                    Math.cos(angle)*radii[ring]+bendX*.015*(ring+1),
                    heights[ring]-lift,
                    Math.sin(angle)*radii[ring]+bendZ*.015*(ring+1));
            float alpha=(float)(.24*flicker);
            if(Math.sin(angle)<-.45 && ring>0 && ring<3)alpha*=.4f;
            GhostDeferredFire.volume(v,pose,center,right,up,.15*flicker,.22*flicker,(float)fract(i*.618+ring*.27),alpha,.6f);
        }
    }
    private static void plume(PoseStack pose,MultiBufferSource buffers,float time,float bendX,float bendZ,float scale,boolean crown,boolean lite) {
        if(!GhostFireMaterial.ready())return;
        var v=buffers.getBuffer(GhostFireMaterial.TYPE);
        var view=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        var inverse=new org.joml.Matrix3f(view).invert();
        var r=inverse.transform(new org.joml.Vector3f(1,0,0)).normalize();
        var u=inverse.transform(new org.joml.Vector3f(0,1,0)).normalize();
        var right=new net.minecraft.world.phys.Vec3(r.x,r.y,r.z);
        var up=new net.minecraft.world.phys.Vec3(u.x,u.y,u.z);
        // Same lobe life as the R breath: born at the surface, swelling, rising with buoyancy,
        // bent by movement wind, cooling from white-hot to soot. Model space: -Y is up.
        int count=crown?30:lite?5:16;
        double lifeTicks=crown?16:12;
        double rate=count/lifeTicks, newest=Math.floor(time*rate);
        for(int i=0;i<count;i++) {
            double index=newest-i, life=(time-index/rate)/lifeTicks;
            if(life<0 || life>=1)continue;
            double seed=fract(index*.6180339887), seed2=fract(index*.7548776662);
            double angle=seed*Math.PI*2;
            double baseY,ring;
            if(crown && seed2<.6) {ring=.235;baseY=-.13-seed2/.6*.33;}  // cheeks, temples and back of the skull
            else {ring=(crown?.15:.09)*seed2;baseY=crown?-.47:-.06;}     // crown of the head / flame base
            double spread=1-life*.55;
            double rise=Math.pow(life,1.15)*(crown?.85:.6);
            double sway=Math.sin(time*.23+seed*31+life*5)*.07*life;
            double wind=life*life*.11;
            var center=new net.minecraft.world.phys.Vec3(
                    (Math.cos(angle)*ring*spread+sway+bendX*wind)*scale,
                    (baseY-rise)*scale,
                    (Math.sin(angle)*ring*spread+bendZ*wind)*scale);
            double radius=(crown?.15:.13)*(.55+life*.9)*(1-life*.5)*(.8+.4*seed2)*scale;
            float alpha=(float)(Math.min(1,life/.1)*Math.pow(1-life,1.25)*.85);
            // Keep the face readable: thin out low lobes emitted in front of the eyes.
            if(crown && Math.sin(angle)<-.55 && baseY>-.42 && life<.45)alpha*=.3f;
            float heat=(float)(1-Math.pow(life,1.2)*.85);
            GhostDeferredFire.volume(v,pose,center,right,up,radius,radius*1.55,(float)seed,alpha,heat);
        }
        if(crown) {
            aura(v,pose,right,up,time,bendX,bendZ);
            // Bloom: the burning head glows into the air around it.
            double pulse=.9+.1*Math.sin(time*.7);
            GhostDeferredFire.glow(v,pose,new net.minecraft.world.phys.Vec3(0,-.38,0),right,up,.85*pulse,1f);
            GhostDeferredFire.glow(v,pose,new net.minecraft.world.phys.Vec3(bendX*.02,-.75,bendZ*.02),right,up,1.25*pulse,.6f);
        } else if(!lite)GhostDeferredFire.glow(v,pose,new net.minecraft.world.phys.Vec3(0,-.25*scale,0),right,up,.55*scale,.8f);
        if(lite)return;
        for(int i=0;i<2;i++) {
            double flicker=.85+.15*Math.sin(time*.9+i*2.7);
            var center=new net.minecraft.world.phys.Vec3((i-.5)*.1*scale,-(crown?.55:.16)*scale,0);
            GhostDeferredFire.volume(v,pose,center,right,up,.16*scale*flicker,.26*scale*flicker,i*.37f,.8f,1);
        }
        // Detached hot flecks follow the same wind; finite analytic lifetimes avoid particle buildup.
        for(int i=0;i<5;i++) {
            double life=(time*.024+i*.19)%1, angle=i*2.399;
            var center=new net.minecraft.world.phys.Vec3((Math.cos(angle)*.18+bendX*.1*life)*scale,
                    -((crown?.6:.25)+life*.95)*scale,(Math.sin(angle)*.18+bendZ*.1*life)*scale);
            GhostDeferredFire.volume(v,pose,center,right,up,.018*scale,.033*scale,i/5f,(float)((1-life)*.7));
        }
    }
}
