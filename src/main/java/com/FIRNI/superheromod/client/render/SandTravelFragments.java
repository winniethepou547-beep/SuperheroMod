package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.sandman.SandTravelController;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded, deterministic sand fragments; no particle or entity per fragment. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class SandTravelFragments {
    private static final int COUNT=24;
    private static final BlockState SAND=Blocks.SAND.defaultBlockState();
    private static final float[] ANGLES=new float[COUNT], HEIGHTS=new float[COUNT], SIZES=new float[COUNT];
    static {
        for(int i=0;i<COUNT;i++) {
            ANGLES[i]=i*2.39996323f;
            HEIGHTS[i]=((i*7)%COUNT+.5f)/COUNT;
            SIZES[i]=.09f+(i%4)*.025f;
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance(); if(mc.level==null)return;
        var pose=event.getPoseStack(); var camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();
        float partial=event.getPartialTick();
        for(var player:mc.level.players()) {
            Long start=SandTravelAnimation.started(player.getUUID()); if(start==null)continue;
            float age=mc.level.getGameTime()-start+partial;
            if(age<0 || age>=SandTravelController.END_TICK)continue;
            if(player==mc.player && mc.options.getCameraType().isFirstPerson())continue;
            var position=player.getPosition(partial);
            if(position.distanceToSqr(camera)>48*48)continue;
            float height=SandTravelAnimation.height(age);
            float amount=1-height;
            if(amount<.01f)continue;
            // Time and shape are shared with the player's deformation, not frame counters.
            float turn=age*.16f;
            int light=LevelRenderer.getLightColor(mc.level,BlockPos.containing(position.add(0,.5,0)));
            pose.pushPose();
            try {
                pose.translate(position.x-camera.x,position.y-camera.y,position.z-camera.z);
                for(int i=0;i<COUNT;i++) {
                    double angle=ANGLES[i]+turn;
                    double radius=.27+amount*.3+(i%3)*.035;
                    double y=.04+HEIGHTS[i]*player.getBbHeight()*height;
                    float size=SIZES[i]*(float)Math.sqrt(amount);
                    pose.pushPose();
                    pose.translate(Math.cos(angle)*radius,y,Math.sin(angle)*radius);
                    pose.mulPose(Axis.YP.rotation((float)angle));
                    pose.mulPose(Axis.ZP.rotation(amount*.4f*((i%2)*2-1)));
                    pose.scale(size,size*(1+(i%3)*.22f),size);
                    pose.translate(-.5,0,-.5);
                    mc.getBlockRenderer().renderSingleBlock(SAND,pose,buffers,light,OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            } finally { pose.popPose(); }
        }
        // Existing render buffers are flushed by LevelRenderer; no global endBatch per player.
    }
}
