package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.client.input.AbilityKeyHandler;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.heroes.ghostrider.HellfireBreathMath;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Continuous flow-shaded flame lobes and cooling surface soot. No atlas repetition or flame entities. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID,value=Dist.CLIENT)
public final class HellfireBreathRenderer {
    private static final class Sample {
        int age;float length;long received,stopped=-1;
        Vec3 origin, direction;
        int scorchCursor;
        /** Where and which way the mouth pointed over the last puff lifetime: lobes keep their birth heading. */
        ArrayDeque<Emit> emits=new ArrayDeque<>();
        Sample(int age,float length,long received){this.age=age;this.length=length;this.received=received;}
    }
    private record Emit(double time,Vec3 origin,Vec3 direction,double length) {}
    private record Scorch(Vec3 point,long time) {}
    private static final Map<net.minecraft.core.BlockPos,Scorch> SCORCH=new LinkedHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static final Map<Integer,Sample> ACTIVE=new HashMap<>();
    private static boolean holding;
    private static long contactTick=-1;
    private static int contacts;
    /** Bounded surface probes shared by every Ghost Rider flame, never burn through floors. */
    public static void contact(Vec3 point,double reach) {
        var mc=Minecraft.getInstance();if(mc.level==null || mc.player==null)return;
        long now=mc.level.getGameTime();if(now!=contactTick){contactTick=now;contacts=0;}
        if(contacts++>=64 || point.distanceToSqr(mc.player.position())>4096)return;
        var hit=mc.level.clip(new ClipContext(point.add(0,.05,0),point.add(0,-reach,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
        if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getDirection()!=net.minecraft.core.Direction.UP)return;
        var block=hit.getBlockPos();SCORCH.put(block,new Scorch(new Vec3(block.getX()+.5,hit.getLocation().y+.008,block.getZ()+.5),now));
        while(SCORCH.size()>512)SCORCH.remove(SCORCH.keySet().iterator().next());
    }
    public static void receive(HellfireBreathPacket p) {
        var level=Minecraft.getInstance().level;if(level==null)return;
        if(!p.active()) {var s=ACTIVE.get(p.player());if(s!=null && s.stopped<0)s.stopped=level.getGameTime();}
        else {
            Sample previous=ACTIVE.get(p.player());
            Sample next=new Sample(p.age(),p.length(),level.getGameTime());
            if(previous!=null && previous.stopped<0){next.origin=previous.origin;next.direction=previous.direction;next.scorchCursor=previous.scorchCursor;next.emits=previous.emits;}
            ACTIVE.put(p.player(),next);
        }
    }
    public static boolean active(int id) {
        var level=Minecraft.getInstance().level;var s=ACTIVE.get(id);
        return level!=null && s!=null && s.stopped<0 && level.getGameTime()-s.received<12;
    }
    public static float strength(int id,float partial) {
        var level=Minecraft.getInstance().level;var s=ACTIVE.get(id);
        if(level==null || s==null)return 0;
        float fade=s.stopped<0?1:Math.max(0,1-(level.getGameTime()-s.stopped+partial)/9f);
        return Math.min(1,(s.age+partial)/6f)*fade;
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e) {
        if(!active(e.getEntity().getId()))return;
        var input=e.getInput();input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;
        input.up=input.down=input.left=input.right=false;
        e.getEntity().setDeltaMovement(Vec3.ZERO);
    }
    @SubscribeEvent public static void input(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(world!=mc.level){ACTIVE.clear();SCORCH.clear();world=mc.level;holding=false;}
        boolean down=mc.player!=null && mc.screen==null && mc.isWindowActive()
                && "ghost_rider".equals(ClientHeroRegistry.get(mc.player.getUUID())) && AbilityKeyHandler.KEY_RAPID_FIRE.isDown();
        if(mc.player!=null && ((down && (!holding || mc.player.tickCount%8==0)) || (!down && holding)))
            ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(AbilitySlot.SKILL_E,down));
        holding=down;
        if(mc.level!=null && mc.level.getGameTime()%5==0)scorchGround(mc);
    }
    private static void scorchGround(Minecraft mc) {
        long now=mc.level.getGameTime();
        SCORCH.entrySet().removeIf(v->now-v.getValue().time>900);
        for(var entry:ACTIVE.entrySet()) {
            Sample s=entry.getValue();if(s.stopped>=0 || s.length<1)continue;
            if(!(mc.level.getEntity(entry.getKey()) instanceof LivingEntity actor)
                    || mc.player==null || actor.distanceToSqr(mc.player)>64*64)continue;
            Vec3 direction=actor.getViewVector(1);
            Vec3 origin=actor.getEyePosition().add(0,-.16,0).add(direction.scale(.32));
            Vec3 side=direction.cross(new Vec3(0,1,0)).normalize();
            // Fill a continuous footprint, at most 64 ground rays per update, amortized across ticks.
            for(int probe=0;probe<64;probe++) {
                int cell=(s.scorchCursor++)%198;
                double distance=1+cell/11, lateral=cell%11-5;
                double radius=HellfireBreathMath.radius(distance);
                if(distance>s.length || Math.abs(lateral)>radius*.92)continue;
                Vec3 center=origin.add(direction.scale(distance)).add(side.scale(lateral));
                var hit=mc.level.clip(new ClipContext(center,center.add(0,-radius-.4,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
                if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK || hit.getDirection()!=net.minecraft.core.Direction.UP)continue;
                Vec3 point=hit.getLocation();
                var sight=mc.level.clip(new ClipContext(origin,point.add(0,.025,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
                if(sight.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
                var block=hit.getBlockPos();
                SCORCH.put(block,new Scorch(new Vec3(block.getX()+.5,point.y+.008,block.getZ()+.5),now));
            }
        }
        while(SCORCH.size()>512)SCORCH.remove(SCORCH.keySet().iterator().next());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {ACTIVE.clear();SCORCH.clear();holding=false;world=null;}
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        long now=mc.level.getGameTime();
        for(Sample s:ACTIVE.values())if(s.stopped<0 && now-s.received>12)s.stopped=now;
        ACTIVE.entrySet().removeIf(v->v.getValue().stopped>=0 && now-v.getValue().stopped>PUFF_LIFE+1);
        if((ACTIVE.isEmpty() && SCORCH.isEmpty()) || !GhostFireMaterial.ready())return;
        var p=e.getPoseStack();Vec3 camera=e.getCamera().getPosition();
        p.pushPose();p.translate(-camera.x,-camera.y,-camera.z);
        RenderSystem.getModelViewStack().pushPose();RenderSystem.getModelViewStack().last().pose().identity();RenderSystem.applyModelViewMatrix();
        var buffers=mc.renderBuffers().bufferSource();var type=GhostFireMaterial.TYPE;
        try {
            var out=buffers.getBuffer(type);
            float partial=mc.getFrameTime();
            for(var ground:SCORCH.entrySet()) {
                Scorch mark=ground.getValue();
                if(mark.point.distanceToSqr(camera)>64*64)continue;
                float opacity=.85f*Math.min(1,Math.max(0,(900-(now-mark.time))/160f));
                float heat=Math.max(0,1-(now-mark.time+partial)/90f);
                Vec3 pos=mark.point;
                // Shared world UVs and joined coverage: internal block seams disappear.
                for(int ix=0;ix<2;ix++) for(int iz=0;iz<2;iz++) {
                    int[] cx={ix,ix,ix+1,ix+1},cz={iz,iz+1,iz+1,iz};
                    for(int corner=0;corner<4;corner++) {
                        double dx=(cx[corner]-1)*.5,dz=(cz[corner]-1)*.5;
                        Vec3 point=pos.add(dx,0,dz);
                        float coverage=coverage(ground.getKey(),cx[corner],cz[corner],pos.y);
                        surface(out,p,point,(float)point.x,(float)point.z,0,heat,opacity*coverage);
                    }
                }
            }
            for(var entry:ACTIVE.entrySet()) {
                if(!(mc.level.getEntity(entry.getKey()) instanceof LivingEntity actor) || actor.distanceToSqr(camera)>96*96)continue;
                Sample s=entry.getValue();
                Vec3 direction=s.stopped>=0 && s.direction!=null?s.direction:actor.getViewVector(partial);
                Vec3 from=s.stopped>=0 && s.origin!=null?s.origin:actor.getEyePosition(partial).add(0,-.16,0).add(direction.scale(.32));
                float age=s.age+(mc.level.getGameTime()-s.received)+partial;
                // Scorch reach still ramps up; the visible lobes travel on their own and stop at walls.
                double ramp=s.stopped>=0?s.length:Math.min(HellfireBreathMath.RANGE,Math.max(0,age-3)*2.25);
                double reach=HellfireBreathMath.RANGE*1.1;
                var hit=mc.level.clip(new ClipContext(from,from.add(direction.scale(reach)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
                double length=Math.min(reach,hit.getLocation().distanceTo(from));
                if(s.stopped<0) {
                    s.origin=from;s.direction=direction;s.length=(float)Math.min(ramp,length);
                    if(!s.emits.isEmpty() && age<s.emits.peekLast().time-1)s.emits.clear();
                    if(s.emits.isEmpty() || age-s.emits.peekLast().time>=.2)
                        s.emits.addLast(new Emit(age,from,direction,length));
                }
                while(s.emits.size()>1 && age-s.emits.peekFirst().time>PUFF_LIFE+1)s.emits.removeFirst();
                if(s.emits.size()>96)s.emits.removeFirst();
                var cameraRotation=e.getCamera().rotation();
                var vr=new org.joml.Vector3f(1,0,0).rotate(cameraRotation);
                var vu=new org.joml.Vector3f(0,1,0).rotate(cameraRotation);
                Vec3 viewRight=new Vec3(vr.x,vr.y,vr.z),viewUp=new Vec3(vu.x,vu.y,vu.z);
                float stopAge=s.stopped<0?Float.MAX_VALUE:s.age+(s.stopped-s.received);
                stream(out,p,s.emits,viewRight,viewUp,age,stopAge,
                        actor.distanceToSqr(camera)>40*40?20:40,entry.getKey());
            }
            buffers.endBatch(type);
        } finally {
            RenderSystem.getModelViewStack().popPose();RenderSystem.applyModelViewMatrix();p.popPose();
        }
    }
    private static final double PUFF_LIFE=13, GOLDEN=.6180339887;
    private static double fract(double v){return v-Math.floor(v);}
    /**
     * Continuous jet of flame lobes. Each lobe is born at the mouth, decelerates with drag,
     * swells, rises with buoyancy, wanders with turbulence and cools from white-hot to soot.
     * A wall stops the travel and spreads the lobes sideways. Releasing the key stops new
     * lobes; the ones already in the air finish their lives.
     */
    private static void stream(VertexConsumer out,PoseStack p,Deque<Emit> emits,
                               Vec3 viewRight,Vec3 viewUp,float age,float stopAge,int count,int key) {
        if(emits.isEmpty())return;
        double spawnRate=count/PUFF_LIFE;
        double newest=Math.floor(age*spawnRate);
        for(int i=0;i<count;i++) {
            double index=newest-i, birth=index/spawnRate;
            if(birth>stopAge || birth<0)continue;
            double life=(age-birth)/PUFF_LIFE;
            if(life<0 || life>=1)continue;
            Emit emit=emits.peekFirst();
            for(Emit candidate:emits){if(candidate.time>birth)break;emit=candidate;}
            Vec3 from=emit.origin,direction=emit.direction;double length=emit.length;
            Vec3 side=direction.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=direction.cross(new Vec3(1,0,0));
            side=side.normalize();Vec3 up=side.cross(direction).normalize();
            double seed=fract(index*GOLDEN+key*.137);
            double reach=HellfireBreathMath.RANGE*(.92+.16*seed);
            double travel=reach*(1-Math.pow(1-life,2.2));
            double splash=Math.max(0,travel-length);
            double d=Math.min(travel,length);
            double radius=HellfireBreathMath.radius(d)*(.55+.55*life)*(.85+.3*seed)+splash*.35;
            double swirl=age*.21+seed*40;
            Vec3 center=from.add(direction.scale(d))
                    .add(side.scale((Math.sin(swirl+life*5)*.28*life+(seed-.5)*.5*life)*radius+Math.signum(seed-.5)*splash*.6))
                    .add(up.scale(Math.cos(swirl*.8+life*4)*.22*life*radius))
                    .add(0,life*life*1.4+splash*.25,0);
            float heat=(float)(1-Math.pow(life,1.3)*.92);
            float alpha=(float)(Math.min(1,life/.06)*Math.pow(1-life,1.2)*.86);
            GhostFireMaterial.volume(out,p,center,viewRight,viewUp,radius,radius*1.12,(float)seed,alpha,heat);
            if(i%3==0 && heat>.45)GhostFireMaterial.glow(out,p,center,viewRight,viewUp,radius*2.4,alpha*heat);
        }
        // Hot, tight core right at the mouth while the breath is live.
        Emit mouth=emits.peekLast();
        // The jet lights its surroundings along its length.
        if(age<stopAge)for(double d:new double[]{1.5,5,9,14})
            if(d<mouth.length)GhostLights.request(mouth.origin.add(mouth.direction.scale(d)),15);
        if(age<stopAge)for(int i=0;i<3;i++) {
            double d=.25+i*.35, flicker=.85+.15*Math.sin(age*1.7+i*2.1);
            GhostFireMaterial.volume(out,p,mouth.origin.add(mouth.direction.scale(d)),viewRight,viewUp,
                    (.16+i*.09)*flicker,(.2+i*.1)*flicker,(float)fract(i*GOLDEN+age*.05),.9f,1);
        }
    }
    private static float coverage(net.minecraft.core.BlockPos key,int x,int z,double height) {
        if(x==1 && z==1)return 1;
        int count=0,total=0;
        for(int dx=(x==0?-1:0);dx<=(x==2?1:0);dx++)
            for(int dz=(z==0?-1:0);dz<=(z==2?1:0);dz++) {
                total++;var mark=SCORCH.get(key.offset(dx,0,dz));
                if(mark!=null && Math.abs(mark.point.y-height)<.05)count++;
            }
        return count==total?1:.04f;
    }
    private static void surface(VertexConsumer v,PoseStack p,Vec3 pos,float u,float texV,float seed,float heat,float alpha) {
        GhostFireMaterial.vertex(v,p,pos.x,pos.y,pos.z,u,texV,1,seed,heat,alpha);
    }
}
