package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

/** Translucent entity fire must follow opaque mobs, otherwise later mobs paint over its alpha. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class GhostDeferredFire {
    private record Lobe(Matrix4f view,Vec3 center,Vec3 right,Vec3 up,double width,double height,float seed,float alpha,float heat,boolean halo) {}
    private static final List<Lobe> QUEUE=new ArrayList<>();
    private static boolean collecting;
    public static void volume(VertexConsumer v,PoseStack p,Vec3 c,Vec3 r,Vec3 u,double w,double h,float seed,float alpha) {
        volume(v,p,c,r,u,w,h,seed,alpha,1);
    }
    public static void volume(VertexConsumer v,PoseStack p,Vec3 c,Vec3 r,Vec3 u,double w,double h,float seed,float alpha,float heat) {
        if(collecting) {
            if(QUEUE.size()<4096)QUEUE.add(new Lobe(new Matrix4f(RenderSystem.getModelViewMatrix()).mul(p.last().pose()),c,r,u,w,h,seed,alpha,heat,false));
        } else GhostFireMaterial.volume(v,p,c,r,u,w,h,seed,alpha,heat);
    }
    /** Bloom halo, deferred like the flames it surrounds. */
    public static void glow(VertexConsumer v,PoseStack p,Vec3 c,Vec3 r,Vec3 u,double size,float alpha) {
        if(collecting) {
            if(QUEUE.size()<4096)QUEUE.add(new Lobe(new Matrix4f(RenderSystem.getModelViewMatrix()).mul(p.last().pose()),c,r,u,size,size,0,alpha,1,true));
        } else GhostFireMaterial.glow(v,p,c,r,u,size,alpha);
    }
    @SubscribeEvent public static void stage(RenderLevelStageEvent e) {
        if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY){QUEUE.clear();collecting=true;}
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        collecting=false;if(QUEUE.isEmpty())return;
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();
        buffers.endBatch(GhostFireMaterial.TYPE);
        var mv=RenderSystem.getModelViewStack();mv.pushPose();mv.last().pose().identity();RenderSystem.applyModelViewMatrix();
        try {
            var v=buffers.getBuffer(GhostFireMaterial.TYPE);
            for(var l:QUEUE) {
                PoseStack p=new PoseStack();p.mulPoseMatrix(l.view);
                if(l.halo)GhostFireMaterial.glow(v,p,l.center,l.right,l.up,l.width,l.alpha);
                else GhostFireMaterial.volume(v,p,l.center,l.right,l.up,l.width,l.height,l.seed,l.alpha,l.heat);
            }
            buffers.endBatch(GhostFireMaterial.TYPE);
        } finally {QUEUE.clear();mv.popPose();RenderSystem.applyModelViewMatrix();}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){QUEUE.clear();collecting=false;}
}
