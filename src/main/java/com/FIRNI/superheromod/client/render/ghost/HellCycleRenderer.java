package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleRig;
import com.FIRNI.superheromod.heroes.ghostrider.GhostRidingArms;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Hell Cycle after the film: a long raked chopper with a bone spine and rib cage, spiked
 * fenders, twin flaming exhausts and wheels that are engulfed in hellfire. Model pixels,
 * -Z is the front. Seat and handlebar grips keep the shared rig coordinates.
 */
public final class HellCycleRenderer extends EntityRenderer<HellCycleEntity> {
    private static final float WHEEL_Y=6.8f, REAR_Z=13, FRONT_Z=-24, HEAD_Y=17.5f, HEAD_Z=-9;
    private final ModelPart tread=GhostMaterials.box(-2.2f,-1.4f,-1.4f,4.4f,2.8f,2.8f);
    private final ModelPart spoke=GhostMaterials.box(-.2f,-.3f,0,.4f,.6f,6);
    private final ModelPart hub=GhostMaterials.box(-1.6f,-1.6f,-1.6f,3.2f,3.2f,3.2f);
    private final ModelPart unit=GhostMaterials.box(-.5f,0,-.5f,1,1,1);
    private final ModelPart tank=GhostMaterials.box(-3.4f,-2.5f,-4.5f,6.8f,5,9);
    private final ModelPart seat=GhostMaterials.box(-3,-1,-4,6,2,8);
    private final ModelPart engine=GhostMaterials.box(-3,-3,-2.3f,6,6,4.6f);
    private final ModelPart fin=GhostMaterials.box(-3.5f,-.22f,-2.8f,7,.44f,5.6f);
    private final ModelPart vertebra=GhostMaterials.box(-1.1f,-.7f,-.9f,2.2f,1.4f,1.8f);
    private final ModelPart spike=GhostMaterials.box(-.35f,-2.4f,-.35f,.7f,2.4f,.7f);
    private final ModelPart link=GhostMaterials.box(-.42f,-.22f,-.13f,.84f,.44f,.26f);
    public HellCycleRenderer(EntityRendererProvider.Context c) { super(c); shadowRadius=1.2f; }
    @Override public ResourceLocation getTextureLocation(HellCycleEntity e) { return GhostMaterials.TEXTURE; }
    private void piece(ModelPart part,PoseStack p,MultiBufferSource b,int light,float x,float y,float z,float angle,float r,float g,float blue) {
        p.pushPose(); p.translate(x/16,y/16,z/16);p.mulPose(Axis.XP.rotationDegrees(angle));GhostMaterials.draw(part,p,b,light,r,g,blue);p.popPose();
    }
    /** A bar of the given thickness (pixels) between two pixel-space points. */
    private void rod(PoseStack p,MultiBufferSource b,int light,Vec3 from,Vec3 to,float thick,float r,float g,float blue) {
        Vec3 d=to.subtract(from);double len=d.length();if(len<1e-4)return;
        Vec3 axis=d.scale(1/len);
        p.pushPose();p.translate(from.x/16,from.y/16,from.z/16);
        p.mulPose(new Quaternionf().rotationTo(0,1,0,(float)axis.x,(float)axis.y,(float)axis.z));
        p.scale(thick,(float)len,thick);GhostMaterials.draw(unit,p,b,light,r,g,blue);p.popPose();
    }
    private void steel(PoseStack p,MultiBufferSource b,int l,Vec3 a,Vec3 c,float t){rod(p,b,l,a,c,t,.26f,.27f,.30f);}
    private void chrome(PoseStack p,MultiBufferSource b,int l,Vec3 a,Vec3 c,float t){rod(p,b,l,a,c,t,.62f,.63f,.67f);}
    @Override public void render(HellCycleEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light) {
        p.pushPose();p.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partial,e.yRotO,e.getYRot())));
        p.translate(0,HellCycleRig.rearPivotLift(Mth.lerp(partial,e.xRotO,e.getXRot())),0);
        p.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial,e.xRotO,e.getXRot())));
        p.mulPose(Axis.ZP.rotationDegrees(e.lean(partial)));
        float time=e.tickCount+partial, spin=Mth.lerp(partial,e.wheelSpinO,e.wheelSpin);
        float speed=Math.min(1,e.speed()/2f);
        for(int wheel=0;wheel<2;wheel++) wheel(p,b,light,wheel==0?FRONT_Z:REAR_Z,spin,time,speed,wheel==1);
        frame(p,b,light,time);
        piece(tank,p,b,light,0,14.5f,-3,-12,.07f,.065f,.06f);
        piece(seat,p,b,light,0,HellCycleRig.SEAT_Y,HellCycleRig.SEAT_Z,0,.06f,.045f,.035f);
        for(int i=-1;i<=1;i+=2) {
            piece(engine,p,b,light,0,8,i*3,i*25,.24f,.25f,.27f);
            for(int f=0;f<5;f++) piece(fin,p,b,light,0,7+f*.8f,i*3,i*25,.45f,.46f,.48f);
        }
        exhausts(p,b,light,time,speed);
        headlight(p,b,light);
        boolean stage=com.FIRNI.superheromod.client.render.film.FilmDirector.drawingStage();
        if(!stage)HellfireBreathRenderer.contact(e.getPosition(partial).subtract(Vec3.directionFromRotation(0,e.getYRot())),.6);
        // Burning track from the rear tyre while it is on the ground.
        if(!stage && Math.abs(e.getY()-e.yo)<.05)GhostFireTrail.mark(e.getId(),e.position().subtract(Vec3.directionFromRotation(0,e.getYRot()).scale(.95)).add(0,.02,0),speed);
        p.popPose();super.render(e,yaw,partial,p,b,light);
    }
    /** Spoked wheel inside a ring of rolling hellfire, with a plume trailing behind at speed. */
    private void wheel(PoseStack p,MultiBufferSource b,int light,float z,float spin,float time,float speed,boolean rear) {
        p.pushPose();p.translate(0,WHEEL_Y/16,z/16);
        p.pushPose();p.mulPose(Axis.XP.rotationDegrees(-spin));
        for(int i=0;i<20;i++){p.pushPose();p.mulPose(Axis.XP.rotationDegrees(i*18));p.translate(0,0,6/16f);GhostMaterials.draw(tread,p,b,light,.05f,.05f,.055f);p.popPose();}
        for(int i=0;i<10;i++){p.pushPose();p.mulPose(Axis.XP.rotationDegrees(i*36));GhostMaterials.draw(spoke,p,b,light,.55f,.57f,.6f);p.popPose();}
        GhostMaterials.draw(hub,p,b,light,.5f,.51f,.54f);
        p.popPose();
        // Flames ride the rim and lick backwards; the faster the bike, the longer they stream.
        for(int i=0;i<4;i++) {
            double a=Math.toRadians(i*90-spin);
            p.pushPose();
            p.translate(0,Math.sin(a)*6.6/16,Math.cos(a)*6.6/16);
            p.mulPose(Axis.XP.rotationDegrees(-90*speed));
            p.scale(.8f,.8f+speed*.6f,.8f);
            GhostMaterials.lightFlames(p,b,time+i*3.7f,.4f*speed,.6f);
            p.popPose();
        }
        p.pushPose();p.mulPose(Axis.XP.rotationDegrees(-90));p.translate(0,0,rear?.25:.1);
        p.scale(1f,.7f+speed*(rear?1.1f:.6f),.6f);GhostMaterials.lightFlames(p,b,time,.3f,1.1f);p.popPose();
        p.popPose();
    }
    private void frame(PoseStack p,MultiBufferSource b,int light,float time) {
        Vec3 head=new Vec3(0,HEAD_Y,HEAD_Z), seatBack=new Vec3(0,HellCycleRig.SEAT_Y,HellCycleRig.SEAT_Z+4);
        for(int side:new int[]{-1,1}) {
            // Long raked fork to the front axle.
            chrome(p,b,light,new Vec3(side*2.6,HEAD_Y+1,HEAD_Z),new Vec3(side*2.6,WHEEL_Y,FRONT_Z),1.0f);
            steel(p,b,light,new Vec3(side*2.6,HEAD_Y-2,HEAD_Z-1),new Vec3(side*2.6,HEAD_Y-1.5,HEAD_Z-2.5),1.6f);
            // Down tube, cradle and swingarm.
            steel(p,b,light,new Vec3(side*1.2,HEAD_Y-1,HEAD_Z+.5),new Vec3(side*2.2,5,1),1.0f);
            steel(p,b,light,new Vec3(side*2.2,5,1),new Vec3(side*3.4,WHEEL_Y,REAR_Z),1.0f);
            steel(p,b,light,new Vec3(side*2,HellCycleRig.SEAT_Y-1,HellCycleRig.SEAT_Z+3),new Vec3(side*3.4,WHEEL_Y,REAR_Z),.9f);
            // Ape-hanger bars up to the rider's grips.
            chrome(p,b,light,new Vec3(side*1.4,HEAD_Y+1.2,HEAD_Z+.5),new Vec3(side*GhostRidingArms.GRIP_X*.8,GhostRidingArms.GRIP_Y+.6,GhostRidingArms.GRIP_Z),.7f);
            chrome(p,b,light,new Vec3(side*GhostRidingArms.GRIP_X*.8,GhostRidingArms.GRIP_Y+.6,GhostRidingArms.GRIP_Z),
                    new Vec3(side*(GhostRidingArms.GRIP_X+1.6),GhostRidingArms.GRIP_Y,GhostRidingArms.GRIP_Z),.75f);
            // Rib cage along the tank.
            for(int r=0;r<5;r++) {
                double z=-6+r*2.2, curve=1-Math.abs(r-2)*.12;
                rod(p,b,light,new Vec3(side*1.2,16.8,z),new Vec3(side*3.9*curve,13,z+.6),.55f,.80f,.73f,.57f);
                rod(p,b,light,new Vec3(side*3.9*curve,13,z+.6),new Vec3(side*2.4,10.3,z+1.2),.5f,.74f,.66f,.50f);
            }
            // Draped chain along the frame.
            for(int i=0;i<10;i++) {
                double t=i/9.0;
                Vec3 at=new Vec3(side*4.2,11.2-Math.sin(t*Math.PI)*2.2,-5+t*12);
                p.pushPose();p.translate(at.x/16,at.y/16,at.z/16);p.mulPose(Axis.YP.rotationDegrees(90));
                if(i%2==1)p.mulPose(Axis.XP.rotationDegrees(90));
                GhostMaterials.draw(link,p,b,light,.4f,.41f,.44f);p.popPose();
            }
        }
        // Bone spine with spikes, steering head to seat.
        for(int i=0;i<9;i++) {
            Vec3 at=head.add(seatBack.subtract(head).scale(i/8.0)).add(0,1.2+Math.sin(i/8.0*Math.PI)*1.6,0);
            piece(vertebra,p,b,light,(float)at.x,(float)at.y,(float)at.z,0,.82f,.75f,.58f);
            if(i%2==0)piece(spike,p,b,light,(float)at.x,(float)at.y-.6f,(float)at.z,-20,.85f,.78f,.6f);
        }
        // Spiked fenders.
        for(int i=0;i<4;i++) {
            double a=Math.toRadians(40+i*28);
            piece(spike,p,b,light,0,(float)(WHEEL_Y+Math.sin(a)*8),(float)(REAR_Z+Math.cos(a)*8),(float)(-90+40+i*28),.7f,.66f,.6f);
            double f=Math.toRadians(70+i*22);
            piece(spike,p,b,light,0,(float)(WHEEL_Y+Math.sin(f)*7.8),(float)(FRONT_Z-Math.cos(f)*7.8),(float)(90-(70+i*22)),.7f,.66f,.6f);
        }
    }
    /** Twin pipes sweeping back past the rear wheel, spitting fire. */
    private void exhausts(PoseStack p,MultiBufferSource b,int light,float time,float speed) {
        for(int side:new int[]{-1,1}) {
            Vec3 start=new Vec3(side*3.2,8,1), bend=new Vec3(side*4.2,6,8), end=new Vec3(side*4.4,8.5,REAR_Z+7);
            chrome(p,b,light,start,bend,1.2f);chrome(p,b,light,bend,end,1.2f);
            p.pushPose();p.translate(end.x/16,end.y/16,end.z/16);p.mulPose(Axis.XP.rotationDegrees(-90));
            p.scale(.5f,.6f+speed*.9f,.5f);GhostMaterials.lightFlames(p,b,time*1.3f+side*5,.2f,.6f);p.popPose();
        }
    }
    private void headlight(PoseStack p,MultiBufferSource b,int light) {
        p.pushPose();p.translate(0,(HEAD_Y-1)/16,(HEAD_Z-3.2)/16);
        var v=b.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE));
        // Ring and recessed lens share a circular silhouette instead of a glowing engine cube.
        for(int band=0;band<2;band++)for(int i=0;i<24;i++) {
            double a=i*Math.PI/12,c=(i+1)*Math.PI/12;
            double outer=band==0?.185:.145,inner=band==0?.145:0;
            double[] radii={outer,outer,inner,inner}, angles={a,c,c,a};
            for(int j=0;j<4;j++)v.vertex(p.last().pose(),(float)(Math.cos(angles[j])*radii[j]),(float)(Math.sin(angles[j])*radii[j]),band==0?-.02f:0)
                .color(band==0?.045f:1,band==0?.045f:.46f,band==0?.05f:.065f,1)
                .uv(.5f,.5f).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(band==0?light:15728880).normal(p.last().normal(),0,0,-1).endVertex();
        }
        // Spikes fanning around the lamp.
        for(int i=0;i<6;i++){p.pushPose();p.mulPose(Axis.ZP.rotationDegrees(i*60+30));p.translate(0,-.17,0);
            GhostMaterials.draw(spike,p,b,light,.72f,.68f,.6f);p.popPose();}
        p.popPose();
    }
}
