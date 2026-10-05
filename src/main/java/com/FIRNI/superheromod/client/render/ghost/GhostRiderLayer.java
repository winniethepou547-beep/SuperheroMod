package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.phys.Vec3;
import com.FIRNI.superheromod.heroes.ghostrider.GhostComboMotion;
import com.FIRNI.superheromod.heroes.ghostrider.GhostRidingArms;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import java.util.*;

/** Anatomical skull silhouette with open jaw, recessed eye sockets and individual teeth. */
public final class GhostRiderLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    // Skull, head pixels (-Y up, -Z front). Mirrored pieces are authored on the right (-X) side.
    private final ModelPart skullCap=GhostMaterials.box(-2.9f,-8.95f,-2.9f,5.8f,1.0f,5.9f);
    private final ModelPart cranium=GhostMaterials.box(-3.45f,-8.1f,-3.2f,6.9f,3.45f,6.6f);
    private final ModelPart occiput=GhostMaterials.box(-2.95f,-7.5f,3.3f,5.9f,3.4f,.85f);
    private final ModelPart parietal=GhostMaterials.box(-3.85f,-7.55f,-2.2f,.45f,3.0f,4.7f);
    private final ModelPart forehead=GhostMaterials.box(-3.1f,-7.95f,-3.65f,6.2f,2.15f,.5f);
    private final ModelPart brow=GhostMaterials.box(-3.35f,-5.85f,-3.95f,2.95f,.85f,.95f);
    private final ModelPart glabella=GhostMaterials.box(-.45f,-5.95f,-3.9f,.9f,1.05f,.6f);
    private final ModelPart socket=GhostMaterials.box(-2.95f,-5.05f,-3.25f,2.35f,1.95f,.35f);
    private final ModelPart socketWall=GhostMaterials.box(-3.2f,-5.0f,-3.75f,.35f,1.85f,.6f);
    private final ModelPart eye=GhostMaterials.box(-2.2f,-4.55f,-3.38f,.85f,.7f,.15f);
    private final ModelPart temple=GhostMaterials.box(-3.55f,-5.6f,-2.6f,.35f,1.9f,2.1f);
    private final ModelPart cheek=GhostMaterials.box(-3.7f,-3.4f,-3.45f,1.65f,1.05f,2.9f);
    private final ModelPart zygoma=GhostMaterials.box(-3.75f,-3.2f,-.6f,.4f,.7f,2.1f);
    private final ModelPart maxilla=GhostMaterials.box(-2.0f,-3.3f,-3.75f,4.0f,1.25f,1.7f);
    private final ModelPart nasal=GhostMaterials.box(-.6f,-4.5f,-3.82f,1.2f,1.65f,.45f);
    private final ModelPart nasalBridge=GhostMaterials.box(-.3f,-5.05f,-3.95f,.6f,.7f,.4f);
    private final ModelPart upperTooth=GhostMaterials.box(-.24f,-2.15f,-3.78f,.48f,.95f,.5f);
    private final ModelPart ramus=GhostMaterials.box(-3.05f,-2.95f,-1.5f,.65f,2.3f,1.5f);
    private final ModelPart mandible=GhostMaterials.box(-2.7f,-1.15f,-3.35f,5.4f,1.0f,3.5f);
    private final ModelPart chin=GhostMaterials.box(-1.3f,-.85f,-3.72f,2.6f,.95f,.7f);
    private final ModelPart lowerTooth=GhostMaterials.box(-.22f,-1.6f,-3.45f,.44f,.6f,.45f);
    private final ModelPart crack=GhostMaterials.box(-.08f,-8.45f,-3.28f,.16f,2.7f,.12f);
    // Outfit details: raised collar, bandolier chain links, buckle, hem, gloves, boot straps.
    private final ModelPart collarBack=GhostMaterials.box(-3.3f,-1.5f,1.35f,6.6f,1.9f,.8f);
    private final ModelPart collarSide=GhostMaterials.box(-3.75f,-1.3f,-1.9f,.65f,1.7f,3.3f);
    private final ModelPart link=GhostMaterials.box(-.42f,-.22f,-.13f,.84f,.44f,.26f);
    private final ModelPart buckle=GhostMaterials.box(-.85f,9.8f,-2.75f,1.7f,1.4f,.4f);
    private final ModelPart hem=GhostMaterials.box(-4.3f,11.15f,-2.4f,8.6f,.95f,4.8f);
    private final ModelPart glove=GhostMaterials.box(-1.1f,-1.0f,-1.1f,2.2f,2.0f,2.2f);
    private final ModelPart strap=GhostMaterials.box(-2.3f,3.4f,-2.9f,4.6f,.55f,5.2f);
    private final ModelPart torso=GhostMaterials.box(-4.2f,-.1f,-2.25f,8.4f,12.25f,4.5f);
    private final ModelPart arm=GhostMaterials.box(-2,-2,-2.1f,4,6.2f,4.2f);
    private final ModelPart forearm=GhostMaterials.box(-1.95f,0,-2.05f,3.9f,6.2f,4.1f);
    private final ModelPart leg=GhostMaterials.box(-2.1f,0,-2.1f,4.2f,6.2f,4.2f);
    private final ModelPart shin=GhostMaterials.box(-2.05f,0,-2.05f,4.1f,6,4.1f);
    private final ModelPart boot=GhostMaterials.box(-2.2f,2,-2.8f,4.4f,4.2f,5);
    private final ModelPart zipper=GhostMaterials.box(-.15f,.5f,-2.5f,.3f,10,.35f);
    private final ModelPart belt=GhostMaterials.box(-4.3f,10,-2.5f,8.6f,1,5);
    private final ModelPart lapel=GhostMaterials.box(-3.1f,0,-2.65f,2,4,.5f);
    private final ModelPart stud=GhostMaterials.box(-.4f,-.4f,-.4f,.8f,.8f,.8f);
    /** Set by a film while it draws this rider seated on a stage-only Hell Cycle. */
    public static HellCycleEntity filmBike;
    /** Set by a film to pose this rider on its stage (overrides the gameplay pose). */
    public static GhostComboMotion.Pose filmPose;
    private record Wind(Vec3 value, float time) {}
    private final Map<UUID, Wind> wind = new HashMap<>();
    public GhostRiderLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> p) { super(p); }
    private void draw(ModelPart part,PoseStack p,MultiBufferSource b,int l,float r,float g,float blue) { GhostMaterials.draw(part,p,b,l,r,g,blue); }
    @Override public void render(PoseStack p,MultiBufferSource b,int light,AbstractClientPlayer e,float walk,float amount,float partial,float time,float yaw,float pitch) {
        if (!"ghost_rider".equals(ClientHeroRegistry.get(e.getUUID())) || e.isInvisible()) return;
        var m=getParentModel();
        boolean riding=filmBike!=null || e.getVehicle() instanceof com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity;
        var motion=filmBike!=null?null:filmPose!=null?filmPose:GhostChainRenderer.motion(e,partial);
        float breath=riding?0:HellfireBreathRenderer.strength(e.getId(),partial);
        float brace=breath*(.16f+.012f*(float)Math.sin(time*.7f));
        boolean mounting=!riding && !e.onGround() && !e.level().getEntitiesOfClass(HellCycleEntity.class,e.getBoundingBox().inflate(7),
                bike->bike.phase()==HellCycleEntity.APPROACH).isEmpty();
        p.pushPose();
        if(riding) ridingTorso(p);
        else if(breath>0) p.mulPose(Axis.XP.rotation(-brace));
        else if(motion!=null) { p.mulPose(Axis.YP.rotation(motion.torso()));p.mulPose(Axis.XP.rotation(motion.lean())); }
        else m.body.translateAndRotate(p);
        draw(torso,p,b,light,.065f,.065f,.073f);draw(zipper,p,b,light,.62f,.64f,.67f);draw(belt,p,b,light,.035f,.03f,.025f);
        for(int side:new int[]{-1,1}) {p.pushPose();p.scale(side,1,1);p.mulPose(Axis.ZP.rotationDegrees(-18));draw(lapel,p,b,light,.14f,.14f,.15f);p.popPose();}
        outfit(p,b,light);
        p.popPose();
        float mount=mountBlend(e.getUUID(),mounting,time);
        for(int side:new int[]{-1,1}) {
            GhostRidingArms.Arm target=null;
            if(riding && motion==null) {
                var bike=filmBike!=null?filmBike:(HellCycleEntity)e.getVehicle();
                target=GhostRidingArms.solve(side,Mth.lerp(partial,bike.xRotO,bike.getXRot()),bike.lean(partial));
            } else if(motion!=null && motion.grip()!=null && breath<=0) {
                float charge=GhostChainRenderer.chargeTick(e,partial);
                target=charge>=0?GhostComboMotion.chargeArm(charge,side,GhostChainRenderer.chargeShift(e,side)):GhostComboMotion.arm(motion,side);
            } else if(motion==null && breath<=0) target=idleArm(m,side);
            if(target!=null) {
                // Any jump in where the arm should be (a swing ending, the chain changing hands,
                // getting on the bike) is eased over instead of snapping.
                var contacts=easeArm(e.getUUID(),side,target,time);
                segment(arm,p,b,light,contacts.shoulder(),contacts.elbow(),.25f);
                segment(forearm,p,b,light,contacts.elbow(),contacts.hand(),.35f);
                glove(p,b,light,contacts.hand());
            } else {
                p.pushPose();
                // Draw from the SAME rig as the chain anchor, after all vanilla/layer pose changes.
                if(breath>0) {
                    p.mulPose(Axis.XP.rotation(-brace));
                    p.translate(side*.36,.125,0);
                    p.mulPose(Axis.ZP.rotation(side*.32f*breath));
                    p.mulPose(Axis.XP.rotation(-.95f*breath));
                    p.mulPose(Axis.YP.rotation(-side*.25f*breath));
                } else if(motion!=null) {
                    var transform=GhostComboMotion.armMatrix(motion,side);
                    p.mulPoseMatrix(transform);p.last().normal().mul(new org.joml.Matrix3f(transform));
                }
                else { (side<0?m.rightArm:m.leftArm).translateAndRotate(p);p.translate(side*.0625f,0,0); }
                draw(arm,p,b,light,.065f,.06f,.06f);
                for(int i=0;i<3;i++) {p.pushPose();p.translate((i-1)*.075,-.13,-.135);draw(stud,p,b,light,.7f,.72f,.75f);p.popPose();}
                p.translate(0,.25,0);p.mulPose(Axis.XP.rotation(breath>0?-1.35f*breath:(side<0?(motion==null?-.12f:motion.elbow()):-.25f)));
                draw(forearm,p,b,light,.055f,.05f,.05f);
                p.translate(0,.36,0);draw(glove,p,b,light,.03f,.028f,.028f);p.popPose();
            }
            p.pushPose();(side<0?m.rightLeg:m.leftLeg).translateAndRotate(p);
            float stance=motion==null || riding?0:motion.weight();
            // Hips counter the shoulders: legs plant and twist against the upper body.
            if(motion!=null && !riding)p.mulPose(Axis.YP.rotation(-motion.torso()*.35f));
            // Leaping onto the arriving Hell Cycle: the right leg swings up and over the seat while
            // the left tucks, both easing in and out instead of popping.
            if(mount>0) {
                if(side<0){p.mulPose(Axis.ZP.rotation(-.65f*mount));p.mulPose(Axis.XP.rotation(-.75f*mount));}
                else p.mulPose(Axis.XP.rotation(-.3f*mount));
            }
            p.mulPose(Axis.ZP.rotation(side*.14f*stance));
            p.mulPose(Axis.XP.rotation(-.18f*stance));
            draw(leg,p,b,light,.055f,.055f,.065f);
            p.translate(0,.375,0);p.mulPose(Axis.XP.rotation(riding?1.15f:(!e.onGround()?.5f:Math.max(0,(float)Math.sin(walk*.66+(side<0?0:Math.PI)))*amount*.5f)));
            p.mulPose(Axis.XP.rotation(.26f*stance));
            draw(shin,p,b,light,.05f,.05f,.06f);draw(boot,p,b,light,.025f,.025f,.027f);
            draw(strap,p,b,light,.5f,.51f,.54f);p.popPose();
        }
        p.pushPose();
        // The head rides on the torso: it turns and leans with every swing, haul and throw.
        if(riding)ridingTorso(p);
        else if(breath>0)p.mulPose(Axis.XP.rotation(-brace));
        else if(motion!=null){p.mulPose(Axis.YP.rotation(motion.torso()));p.mulPose(Axis.XP.rotation(motion.lean()));}
        m.head.translateAndRotate(p);
        int skullLight=LightTexture.pack(Math.max(10,LightTexture.block(light)),LightTexture.sky(light));
        skull(p,b,skullLight,time,Math.max(breath,PenanceClient.jaw(e,partial)));
        Vec3 velocity=new Vec3(e.getX()-e.xo,e.getY()-e.yo,e.getZ()-e.zo);
        double a=Math.toRadians(e.yBodyRot);
        Vec3 local=new Vec3(-(velocity.x*Math.cos(a)+velocity.z*Math.sin(a))*18,0,(velocity.z*Math.cos(a)-velocity.x*Math.sin(a))*18);
        if(wind.size()>128)wind.clear();
        Wind previous=wind.getOrDefault(e.getUUID(),new Wind(Vec3.ZERO,time));
        double blend=1-Math.exp(-Math.max(0,Math.min(2,time-previous.time))*0.35);
        Vec3 lag=previous.value.lerp(local,blend);
        wind.put(e.getUUID(),new Wind(lag,time));
        HellfireBreathRenderer.contact(e.getEyePosition(),.45);
        GhostMaterials.headFlames(p,b,time,(float)Math.max(-8,Math.min(8,lag.x)),(float)Math.max(-8,Math.min(8,lag.z)));
        p.popPose();
    }
    /** Burnt, heavy-browed skull: deep black sockets with embers at the bottom, open jaw. */
    private void skull(PoseStack p,MultiBufferSource b,int light,float time,float breath) {
        float[] bone={.86f,.79f,.62f}, aged={.72f,.62f,.45f}, soot={.2f,.14f,.09f}, hollow={.03f,.018f,.01f}, tooth={.94f,.88f,.72f};
        draw(skullCap,p,b,light,bone[0],bone[1],bone[2]);
        draw(cranium,p,b,light,bone[0],bone[1],bone[2]);
        draw(occiput,p,b,light,aged[0],aged[1],aged[2]);
        draw(forehead,p,b,light,bone[0]*1.04f,bone[1]*1.04f,bone[2]*1.04f);
        draw(glabella,p,b,light,bone[0],bone[1],bone[2]);
        draw(maxilla,p,b,light,aged[0],aged[1],aged[2]);
        draw(nasal,p,b,light,hollow[0],hollow[1],hollow[2]);
        draw(nasalBridge,p,b,light,bone[0],bone[1],bone[2]);
        draw(crack,p,b,light,soot[0],soot[1],soot[2]);
        for(int side:new int[]{-1,1}) {
            p.pushPose();p.scale(side,1,1);
            draw(parietal,p,b,light,aged[0],aged[1],aged[2]);
            draw(brow,p,b,light,.93f,.86f,.68f);
            draw(socket,p,b,light,hollow[0],hollow[1],hollow[2]);
            draw(socketWall,p,b,light,soot[0],soot[1],soot[2]);
            draw(temple,p,b,light,soot[0],soot[1],soot[2]);
            draw(cheek,p,b,light,bone[0],bone[1],bone[2]);
            draw(zygoma,p,b,light,aged[0],aged[1],aged[2]);
            draw(ramus,p,b,light,aged[0],aged[1],aged[2]);
            // Ember deep in the socket, flickering.
            float ember=.75f+.25f*(float)Math.sin(time*.9+side);
            draw(eye,p,b,15728880,1,.30f*ember,.02f);
            p.popPose();
        }
        for(int i=0;i<8;i++) {
            double x=(i-3.5)*.033, back=Math.abs(i-3.5)>2.5?.012:0;
            p.pushPose();p.translate(x,0,back);draw(upperTooth,p,b,light,tooth[0],tooth[1],tooth[2]);p.popPose();
        }
        p.pushPose();p.translate(0,.012+Math.sin(time*.12)*.006+breath*.075,0);
        draw(mandible,p,b,light,aged[0],aged[1],aged[2]);draw(chin,p,b,light,bone[0],bone[1],bone[2]);
        for(int i=0;i<7;i++){p.pushPose();p.translate((i-3)*.032,0,0);draw(lowerTooth,p,b,light,tooth[0],tooth[1],tooth[2]);p.popPose();}
        p.popPose();
    }
    /** Raised collar, a chain bandolier over the right shoulder, buckle and jacket hem. */
    private void outfit(PoseStack p,MultiBufferSource b,int light) {
        draw(collarBack,p,b,light,.06f,.06f,.066f);
        for(int side:new int[]{-1,1}){p.pushPose();p.scale(side,1,1);draw(collarSide,p,b,light,.06f,.06f,.066f);p.popPose();}
        draw(buckle,p,b,light,.62f,.63f,.66f);
        draw(hem,p,b,light,.045f,.045f,.05f);
        // Front diagonal, over the shoulder, back diagonal: links alternate orientation.
        Vec3[][] runs={{new Vec3(-3.5,.6,-2.5),new Vec3(3.9,10.4,-2.5)},{new Vec3(-3.7,-.25,-2.3),new Vec3(-3.7,-.25,2.3)},
                {new Vec3(-3.5,.6,2.5),new Vec3(3.9,10.4,2.5)}};
        int n=0;
        for(Vec3[] run:runs) {
            Vec3 delta=run[1].subtract(run[0]);
            int count=(int)Math.ceil(delta.length()/.62);
            Vec3 axis=delta.normalize();
            for(int i=0;i<=count;i++,n++) {
                Vec3 at=run[0].add(delta.scale(i/(double)count));
                p.pushPose();p.translate(at.x/16,at.y/16,at.z/16);
                p.mulPose(new Quaternionf().rotationTo(1,0,0,(float)axis.x,(float)axis.y,(float)axis.z));
                if(n%2==1)p.mulPose(Axis.XP.rotationDegrees(90));
                draw(link,p,b,light,.42f,.43f,.46f);p.popPose();
            }
        }
    }
    private void glove(PoseStack p,MultiBufferSource b,int light,Vec3 hand) {
        p.pushPose();p.translate(hand.x,hand.y,hand.z);draw(glove,p,b,light,.03f,.028f,.028f);p.popPose();
    }
    // ------------------------------------------------------------------ smooth transitions
    private record Drawn(GhostRidingArms.Arm arm,GhostRidingArms.Arm target,float time) {}
    private record Blend(GhostRidingArms.Arm from,float start) {}
    private final Map<String,Drawn> drawn=new HashMap<>();
    private final Map<String,Blend> blends=new HashMap<>();
    private final Map<UUID,float[]> mounts=new HashMap<>();
    private static final float ARM_BLEND=6;
    /** The idle arm, from the vanilla walking pose, solved on the same two-bone rig as every attack. */
    private static GhostRidingArms.Arm idleArm(PlayerModel<?> m,int side) {
        var part=side<0?m.rightArm:m.leftArm;
        var matrix=new org.joml.Matrix4f().translate(part.x/16,part.y/16,part.z/16)
                .rotateZYX(part.zRot,part.yRot,part.xRot).translate(side*.0625f,0,0);
        var hand=matrix.transformPosition(new org.joml.Vector3f(0,.58f,-.04f));
        return GhostRidingArms.solve(new Vec3(side*.375,.125,0),new Vec3(hand.x,hand.y,hand.z),new Vec3(side,.35,.15));
    }
    private GhostRidingArms.Arm easeArm(UUID id,int side,GhostRidingArms.Arm target,float time) {
        // Film stages pose the rider directly; easing there would drag world poses into the shot.
        if(com.FIRNI.superheromod.client.render.film.FilmDirector.drawingStage())return target;
        String key=id+(side<0?"R":"L");
        Drawn last=drawn.get(key);
        if(last!=null) {
            float dt=Math.max(0,time-last.time);
            // The TARGET jumping faster than any authored motion means the pose source changed: blend from where the arm
            // is drawn now. (Comparing against the drawn arm instead restarted the blend every frame while it caught up,
            // so in quick moves the arm hardly moved at all.)
            if(dt<2 && last.target.hand().distanceTo(target.hand())>.12+.5*dt)blends.put(key,new Blend(last.arm,time));
        }
        Blend blend=blends.get(key);
        GhostRidingArms.Arm shown=target;
        if(blend!=null) {
            float k=Mth.clamp((time-blend.start)/ARM_BLEND,0,1);
            if(k>=1)blends.remove(key);
            else {
                float s=k*k*(3-2*k);
                Vec3 hand=blend.from.hand().lerp(target.hand(),s);
                shown=GhostRidingArms.solve(target.shoulder(),hand,new Vec3(side,.35,.15));
            }
        }
        if(drawn.size()>256){drawn.clear();blends.clear();}
        drawn.put(key,new Drawn(shown,target,time));
        return shown;
    }
    private float mountBlend(UUID id,boolean mounting,float time) {
        float[] state=mounts.computeIfAbsent(id,k->new float[]{0,time});
        float dt=Mth.clamp(time-state[1],0,4);
        state[0]+=((mounting?1:0)-state[0])*(1-(float)Math.exp(-dt*.4));
        state[1]=time;
        if(mounts.size()>128)mounts.clear();
        return state[0];
    }
    private static void ridingTorso(PoseStack p) {
        p.translate(0,.75,0);p.mulPose(Axis.XP.rotation(GhostRidingArms.LEAN));p.translate(0,-.75,0);
    }
    private void segment(ModelPart mesh,PoseStack p,MultiBufferSource b,int light,Vec3 from,Vec3 to,float length) {
        Vec3 delta=to.subtract(from);double d=delta.length();if(d<1e-6)return;
        Vec3 axis=delta.scale(1/d);
        p.pushPose();p.translate(from.x,from.y,from.z);
        p.mulPose(new Quaternionf().rotationTo(0,1,0,(float)axis.x,(float)axis.y,(float)axis.z));
        p.scale(1,(float)(d/length),1);draw(mesh,p,b,light,.065f,.06f,.06f);p.popPose();
    }
}
