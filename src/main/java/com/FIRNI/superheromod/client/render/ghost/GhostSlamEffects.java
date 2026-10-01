package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Short-lived ground tongues and branching cracks, independent from the R scorch marks. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class GhostSlamEffects {
    private record Impact(Vec3 position,long tick,boolean grounded) {}
    private record Punch(Vec3 position,Vec3 direction,long tick) {}
    private static final List<Impact> IMPACTS=new ArrayList<>();
    private static final List<Punch> PUNCHES=new ArrayList<>();
    private static final float PUNCH_LIFE=9;
    /** Small air shock where a fist lands: a ring facing the punch, a flash and a puff of hellfire. */
    public static void punch(Vec3 pos,Vec3 direction) {
        var level=Minecraft.getInstance().level;if(level==null)return;
        if(PUNCHES.size()>=8)PUNCHES.remove(0);
        PUNCHES.add(new Punch(pos,direction,level.getGameTime()));
        var random=level.getRandom();
        for(int i=0;i<10;i++) {
            Vec3 spray=direction.scale(.12+random.nextDouble()*.18).add((random.nextDouble()-.5)*.25,random.nextDouble()*.15,(random.nextDouble()-.5)*.25);
            level.addParticle(i<4?net.minecraft.core.particles.ParticleTypes.FLAME:net.minecraft.core.particles.ParticleTypes.POOF,pos.x,pos.y,pos.z,spray.x,spray.y,spray.z);
        }
    }
    public static void add(Vec3 pos) {
        var level=Minecraft.getInstance().level;if(level==null)return;
        if(IMPACTS.size()>=24)IMPACTS.remove(0);
        var floor=level.clip(new net.minecraft.world.level.ClipContext(pos.add(0,.08,0),pos.add(0,-.12,0),
            net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,Minecraft.getInstance().player));
        IMPACTS.add(new Impact(pos,level.getGameTime(),floor.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK && floor.getDirection()==net.minecraft.core.Direction.UP));
        HellfireBreathRenderer.contact(pos.add(0,.05,0),.2);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){IMPACTS.clear();PUNCHES.clear();}
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !GhostFireMaterial.ready())return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        long now=mc.level.getGameTime();IMPACTS.removeIf(i->now-i.tick>55 || now<i.tick);
        PUNCHES.removeIf(i->now-i.tick>PUNCH_LIFE || now<i.tick);
        if(IMPACTS.isEmpty() && PUNCHES.isEmpty())return;
        PoseStack p=e.getPoseStack();p.pushPose();var cam=e.getCamera().getPosition();p.translate(-cam.x,-cam.y,-cam.z);
        var mv=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();mv.pushPose();mv.last().pose().identity();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        var buffers=mc.renderBuffers().bufferSource();var v=buffers.getBuffer(GhostFireMaterial.TYPE);
        var rotation=e.getCamera().rotation();
        var r=new org.joml.Vector3f(1,0,0).rotate(rotation);var u=new org.joml.Vector3f(0,1,0).rotate(rotation);
        Vec3 right=new Vec3(r.x,r.y,r.z),up=new Vec3(u.x,u.y,u.z);
        for(var impact:IMPACTS) {
            float age=now-impact.tick+e.getPartialTick(),fade=Math.max(0,1-age/28);
            for(int n=0;n<9 && fade>0;n++) {
                double angle=n*2.399, radius=.18+Math.sqrt(n)*.28;
                Vec3 pos=impact.position.add(Math.cos(angle)*radius,.2+Math.sin(age*.2+n)*.035,Math.sin(angle)*radius);
                GhostFireMaterial.volume(v,p,pos,right,up,.22,.32+fade*.22,n/9f,fade*.8f);
            }
            if(!impact.grounded)continue;
            // Thin branching fissures fade after the fire. No permanent world damage.
            float alpha=Math.min(1,age/2)*Math.max(0,1-age/55)*.7f;
            for(int n=0;n<7;n++) {
                double angle=n*6.283/7;Vec3 last=impact.position.add(0,.014,0);
                for(int k=1;k<=4;k++) {
                    double a=angle+Math.sin(n*7+k*2)*.22;
                    Vec3 next=impact.position.add(Math.cos(a)*k*.27,.014,Math.sin(a)*k*.27);
                    Vec3 width=next.subtract(last).cross(new Vec3(0,1,0)).normalize().scale(.014);
                    Vec3[] q={last.add(width),next.add(width),next.subtract(width),last.subtract(width)};
                    for(int c=0;c<4;c++)GhostFireMaterial.vertex(v,p,q[c].x,q[c].y,q[c].z,c<2?0:1,c==0||c==3?0:1,.8f,0,0,alpha);
                    last=next;
                }
            }
        }
        for(var punch:PUNCHES) {
            float age=now-punch.tick+e.getPartialTick(), life=Math.min(1,age/PUNCH_LIFE);
            float grow=1-(1-life)*(1-life)*(1-life);
            Vec3 normal=punch.direction;
            Vec3 a=normal.cross(new Vec3(0,1,0));if(a.lengthSqr()<.01)a=normal.cross(new Vec3(1,0,0));
            a=a.normalize();Vec3 b=normal.cross(a).normalize();
            // The shock travels slightly forward with the blow while it expands.
            Vec3 center=punch.position.add(normal.scale(.15+grow*.6));
            double radius=.25+grow*2.1, band=.28*(1-life)+.06;
            float alpha=(1-life)*(1-life)*.95f;
            for(int ring=0;ring<2;ring++) {
                double rr=radius*(ring==0?1:.62),w=band*(ring==0?1:.7);
                for(int n=0;n<40;n++) {
                    double u0=n*Math.PI*2/40,u1=(n+1)*Math.PI*2/40;
                    Vec3 d0=a.scale(Math.cos(u0)).add(b.scale(Math.sin(u0))),d1=a.scale(Math.cos(u1)).add(b.scale(Math.sin(u1)));
                    Vec3[] q={center.add(d0.scale(rr-w)),center.add(d0.scale(rr+w)).add(normal.scale(-w*.8)),
                              center.add(d1.scale(rr+w)).add(normal.scale(-w*.8)),center.add(d1.scale(rr-w))};
                    float[] us={0,1,1,0};
                    for(int c=0;c<4;c++)GhostFireMaterial.vertex(v,p,q[c].x,q[c].y,q[c].z,us[c],.5f,.46f,0,1,alpha*(ring==0?1:.6f));
                }
            }
            // Hot core flash and a short-lived burst of flame lobes.
            float flash=Math.max(0,1-age/4);
            if(flash>0)GhostFireMaterial.volume(v,p,punch.position,right,up,.55+grow*.4,.55+grow*.4,.3f,flash*.9f);
            for(int n=0;n<6;n++) {
                double angle=n*Math.PI/3+.4;
                Vec3 out=a.scale(Math.cos(angle)).add(b.scale(Math.sin(angle)));
                Vec3 pos=punch.position.add(out.scale(grow*.9)).add(normal.scale(grow*.5)).add(0,grow*.3,0);
                GhostFireMaterial.volume(v,p,pos,right,up,.2+.15*grow,.26+.2*grow,n/6f,(1-life)*.75f);
            }
        }
        buffers.endBatch(GhostFireMaterial.TYPE);mv.popPose();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();p.popPose();
    }
}
