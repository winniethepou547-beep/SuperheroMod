package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.sandman.ColossusCrystal;
import com.FIRNI.superheromod.heroes.sandman.ColossusPose;
import com.FIRNI.superheromod.network.packet.ColossusActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Soft translucent sand haze, seven quads per cut. No cloud particles or network spam. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class ColossusCutMistRenderer {
    private record Cloud(Vec3 pos,double born) {}
    private static final List<Cloud> clouds=new ArrayList<>();
    private static final Map<UUID,ClientColossusActions.Action> seen=new HashMap<>();
    private static final Map<UUID,Integer> sample=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private static final ResourceLocation TEXTURE=new ResourceLocation(SuperheroMod.MODID,"textures/entity/sand_mist.png");
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=lastLevel){clouds.clear();seen.clear();sample.clear();lastLevel=mc.level;}
        if(mc.level==null){clouds.clear();seen.clear();sample.clear();return;}
        double now=mc.level.getGameTime()+mc.getFrameTime();
        seen.keySet().removeIf(id->mc.level.getPlayerByUUID(id)==null);
        sample.keySet().retainAll(seen.keySet());
        for(var player:mc.level.players()) {
            var action=ClientColossusActions.of(player.getUUID());
            if(action==null || action.type!=ColossusActionPacket.SWORD_STAB)continue;
            UUID id=player.getUUID();
            if(seen.get(id)!=action){seen.put(id,action);sample.put(id,16);}
            for(int t=sample.get(id)+2;t<=Math.min(30,action.ticks);t+=2) {
                var pose=ColossusPose.evaluate(player.tickCount,player.getYRot(),player.getYRot(),1,
                        new ColossusPose.Action(action.type,t,44),0,true);
                Vec3 tip=ColossusCrystal.swordTip(player.position(),player.getYRot(),pose);
                if(clouds.size()<96)clouds.add(new Cloud(new Vec3(tip.x,player.getY()+.45,tip.z),now-(action.ticks-t)));
                sample.put(id,t);
            }
        }
        clouds.removeIf(c->now-c.born()>28 || now<c.born());
        var stack=event.getPoseStack();var camera=event.getCamera();var cam=camera.getPosition();
        var buffers=mc.renderBuffers().bufferSource();var type=RenderType.entityTranslucent(TEXTURE);
        for(var cloud:clouds) {
            if(cloud.pos().distanceToSqr(cam)>4096)continue;
            float age=(float)(now-cloud.born()),fade=(float)Math.sin(Math.PI*age/28)*.65f;
            float size=1.3f+age*.055f;
            stack.pushPose();
            stack.translate(cloud.pos().x-cam.x,cloud.pos().y-cam.y+age*.015,cloud.pos().z-cam.z);
            stack.mulPose(camera.rotation());
            var m=stack.last().pose();var n=stack.last().normal();
            var out=buffers.getBuffer(type);
            int light=LevelRenderer.getLightColor(mc.level,BlockPos.containing(cloud.pos()));
            float[][] corners={{-1,-.55f,0,1},{1,-.55f,1,1},{1,.55f,1,0},{-1,.55f,0,0}};
            for(var v:corners)out.vertex(m,v[0]*size,v[1]*size,0).color(1,1,1,fade).uv(v[2],v[3])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n,0,0,1).endVertex();
            stack.popPose();
        }
        if(!clouds.isEmpty())buffers.endBatch(type);
    }
}
