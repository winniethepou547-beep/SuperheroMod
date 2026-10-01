package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.GhostChainPacket;
import com.FIRNI.superheromod.heroes.ghostrider.GhostComboMotion;
import com.FIRNI.superheromod.heroes.ghostrider.GhostChainController;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

/** Alternating oval mesh links, evaluated every render frame; particles never form the chain. */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class GhostChainRenderer {
    private record Sample(GhostChainPacket packet, Vec3 previous, long tick) {}
    private static final Map<Integer, Sample> DATA = new HashMap<>();
    private static final Map<Integer, ChainDynamics> CHAINS = new HashMap<>();
    private record TrailFrame(double tick, Vec3[] points, float heat) {}
    private static final Map<Integer,Deque<TrailFrame>> TRAILS=new HashMap<>();
    private static final Map<Integer,Vec3> SHIFT=new HashMap<>();
    private static double lastFrameClock;
    private static double groundUnder(Minecraft mc,Player player,Vec3 feet) {
        var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(feet.add(0,.5,0),feet.add(0,-3,0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        return hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getLocation().y:Double.NEGATIVE_INFINITY;
    }
    /** Light sparks where links scrape the ground, scaled by how fast they slide. */
    private static void sparks(Minecraft mc,ChainDynamics dynamics) {
        var random=mc.level.getRandom();
        dynamics.contacts((point,velocity)->{
            double speed=velocity.horizontalDistance();
            if(speed<.35 || random.nextFloat()>Math.min(.22,speed*.06))return;
            Vec3 kick=velocity.scale(.06).add((random.nextDouble()-.5)*.08,.06+random.nextDouble()*.12,(random.nextDouble()-.5)*.08);
            mc.level.addParticle(random.nextFloat()<.7f?net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK
                    :net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,point.x,point.y+.03,point.z,kick.x,kick.y,kick.z);
        });
    }
    private static final int MODE_PUNCH=GhostChainController.Mode.PUNCH.ordinal();
    private static final int TRAIL_POINTS=21, TRAIL_FIRST=6;
    private static final double TRAIL_INTERVAL=.25, TRAIL_LIFE=2.2;
    // Link half-length, half-width and wire radius in blocks.
    private static final double LINK_LENGTH=.088, LINK_WIDTH=.047, LINK_WIRE=.015, LINK_SPACING=.12;
    private static final Map<net.minecraft.client.model.PlayerModel<?>,boolean[]> HIDDEN = new WeakHashMap<>();
    public static boolean pose(net.minecraft.client.model.PlayerModel<?> model, Player player, float age) {
        restore(model);
        if (!"ghost_rider".equals(com.FIRNI.superheromod.client.ClientHeroRegistry.get(player.getUUID()))) return false;
        var parts=parts(model);boolean[] saved=new boolean[parts.size()*2];
        for(int i=0;i<parts.size();i++) {
            var part=parts.get(i);saved[i*2]=part.visible;saved[i*2+1]=part.skipDraw;
            part.visible=false;part.skipDraw=true;
        }
        HIDDEN.put(model,saved);
        model.body.xRot = model.body.yRot = model.body.zRot = 0;
        model.rightArm.x = -5; model.rightArm.z = 0;
        model.leftArm.x = 5; model.leftArm.z = 0;
        if (player.getVehicle() instanceof com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity bike) {
            model.body.xRot = .12f;
            model.rightArm.xRot = model.leftArm.xRot = -1.35f;
            model.rightArm.yRot = -.18f; model.leftArm.yRot = .18f;
            model.rightLeg.xRot = model.leftLeg.xRot = -1.1f;
            model.rightLeg.yRot = .22f; model.leftLeg.yRot = -.22f;
            return true;
        }
        Sample sample = DATA.get(player.getId());
        if (sample != null && sample.packet.mode() != 0) {
            var p = sample.packet;
            float tick = p.age() + age - (float)Math.floor(age);
            var motion = samplePose(p,tick);
            model.body.yRot=motion.torso(); model.body.xRot=motion.lean();
            model.rightArm.x=-5*Mth.cos(motion.torso()); model.rightArm.z=5*Mth.sin(motion.torso());
            model.leftArm.x=5*Mth.cos(motion.torso()); model.leftArm.z=-5*Mth.sin(motion.torso());
            model.rightArm.xRot=motion.armX(); model.rightArm.yRot=motion.armY()+motion.torso();
            model.rightArm.zRot=motion.armZ();
            model.leftArm.xRot=motion.guard(); model.leftArm.yRot=motion.torso()*.5f;
            model.rightLeg.xRot-=motion.weight()*.18f; model.leftLeg.xRot+=motion.weight()*.12f;
            model.rightSleeve.copyFrom(model.rightArm);
        }
        return true;
    }
    public static GhostComboMotion.Pose motion(Player player,float partial) {
        Sample s=DATA.get(player.getId());
        if(s==null || s.packet.mode()==0 || player.level().getGameTime()-s.tick>20)return null;
        return samplePose(s.packet,motionTick(s,partial));
    }
    private static GhostComboMotion.Pose samplePose(GhostChainPacket p,float tick) {
        if(p.mode()==8)return GhostComboMotion.pose(2,GhostComboMotion.impactTick(2)+2);
        if(p.mode()==7)return GhostComboMotion.chargePose(tick);
        if(p.mode()==6)return GhostComboMotion.pose(0,0);
        if(p.mode()==MODE_PUNCH)return GhostComboMotion.punch(tick,GhostChainController.PUNCH_CONTACT,GhostChainController.PUNCH_DURATION);
        if(p.mode()==5)return GhostComboMotion.reel(tick);
        return p.mode()==1?GhostComboMotion.pose(p.combo(),tick):GhostComboMotion.grapple(tick,p.mode()==4);
    }
    /** Hands yielding to the spinning chain's pull (model space), shared by the arm mesh and chain anchor. */
    public static Vec3 chargeShift(Player player,int side) {
        return SHIFT.getOrDefault(player.getId()*2+(side<0?0:1),Vec3.ZERO);
    }
    public static float chargeTick(Player player,float partial) {
        Sample s=DATA.get(player.getId());
        return s!=null && s.packet.mode()==7 && player.level().getGameTime()-s.tick<=20?motionTick(s,partial):-1;
    }
    private static float motionTick(Sample s,float partial) {
        var level=Minecraft.getInstance().level;
        return s.packet.age()+(level==null?0:Math.min(2,Math.max(0,level.getGameTime()-s.tick)))+partial;
    }
    public static float elbow(Player player,float partial) {
        Sample s=DATA.get(player.getId());
        if(s==null || s.packet.mode()==0)return -.12f;
        return s.packet.mode()==1 ? GhostComboMotion.pose(s.packet.combo(),s.packet.age()+partial).elbow() : -.15f;
    }
    private static List<net.minecraft.client.model.geom.ModelPart> parts(net.minecraft.client.model.PlayerModel<?> m) {
        return List.of(m.head,m.hat,m.body,m.jacket,m.rightArm,m.leftArm,m.rightSleeve,m.leftSleeve,m.rightLeg,m.leftLeg,m.rightPants,m.leftPants);
    }
    private static void restore(net.minecraft.client.model.PlayerModel<?> model) {
        boolean[] saved=HIDDEN.remove(model);if(saved==null)return;
        var parts=parts(model);
        for(int i=0;i<parts.size();i++){parts.get(i).visible=saved[i*2];parts.get(i).skipDraw=saved[i*2+1];}
    }
    @SubscribeEvent public static void afterPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Post e) {restore(e.getRenderer().getModel());}
    public static void receive(GhostChainPacket p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var old = DATA.get(p.player());
        if(old!=null && (old.packet.mode()!=p.mode() || old.packet.combo()!=p.combo())) {
            TRAILS.remove(p.player()*2);TRAILS.remove(p.player()*2+1);CHAINS.remove(p.player()*2);CHAINS.remove(p.player()*2+1);
            SHIFT.remove(p.player()*2);SHIFT.remove(p.player()*2+1);
        }
        DATA.put(p.player(), new Sample(p, old == null ? p.tip() : old.packet.tip(), level.getGameTime()));
        var self=Minecraft.getInstance().player;
        // Weight lands with the chain: a short kick on the swinging player's own camera.
        if(self!=null && self.getId()==p.player() && p.mode()==1 && p.age()==GhostComboMotion.impactTick(p.combo()))
            com.FIRNI.superheromod.client.render.ClientScreenShake.add(p.combo()==2?.34f:.17f);
        if(p.mode()==MODE_PUNCH && p.age()==GhostChainController.PUNCH_CONTACT
                && level.getEntity(p.player()) instanceof Player puncher) {
            Vec3 direction=p.tip().subtract(puncher.getEyePosition()).multiply(1,0,1);
            GhostSlamEffects.punch(p.tip(),direction.lengthSqr()<1e-4?puncher.getLookAngle():direction.normalize());
            com.FIRNI.superheromod.client.render.ClientScreenShake.addFromSource(p.tip(),.45f,4);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { DATA.clear(); CHAINS.clear(); TRAILS.clear(); SHIFT.clear(); }
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !e.getOverlay().id().getPath().equals("hotbar")) return;
        Sample s = DATA.get(mc.player.getId());
        if (s == null || mc.level.getGameTime() - s.tick > 20) return;
        if (!"ghost_rider".equals(com.FIRNI.superheromod.client.ClientHeroRegistry.get(mc.player.getUUID()))) return;
        var g=e.getGuiGraphics();int w=e.getWindow().getGuiScaledWidth(),h=e.getWindow().getGuiScaledHeight();
        int x=w/2-92,y=h-82;
        g.fill(x-7,y-7,x+191,y+33,0xb0101017);
        g.fill(x-7,y-7,x-5,y+33,0xffc7421f);
        g.drawString(mc.font,"HELLCHAIN",x,y,0xffead9bd,false);
        g.drawString(mc.font,s.packet.heat()>=4?"IGNITED":"HEAT",x+126,y,s.packet.heat()>=4?0xffff983d:0xff9398a0,false);
        for(int i=0;i<10;i++)g.fill(x+i*19,y+12,x+i*19+16,y+17,i<s.packet.heat()?0xffe95f27:0xff34343d);
        if(s.packet.mode()==7) {
            int percent=Math.min(100,s.packet.age()*100/40);
            g.fill(x,y+22,x+184,y+26,0xff34343d);g.fill(x,y+22,x+184*percent/100,y+26,0xffffc56a);
            g.drawCenteredString(mc.font,"RING LAUNCH  "+percent+"%",w/2,y-19,0xffffc56a);
        } else if(s.packet.mode()==3)g.drawCenteredString(mc.font,"RMB  PULL / RELEASE     LMB  RUSH",w/2,y-19,0xffeee1ce);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        DATA.entrySet().removeIf(v -> mc.level.getGameTime() - v.getValue().tick > 20);
        CHAINS.keySet().removeIf(id -> !DATA.containsKey(Math.floorDiv(id,2)));
        double clock=mc.level.getGameTime()+mc.getFrameTime();
        double frameDt=Math.max(0,Math.min(2,clock-lastFrameClock));lastFrameClock=clock;
        for(var history:TRAILS.values())while(!history.isEmpty() && clock-history.peekFirst().tick>TRAIL_LIFE)history.removeFirst();
        TRAILS.entrySet().removeIf(v->v.getValue().isEmpty());
        if (DATA.isEmpty()) return;
        PoseStack pose = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        pose.pushPose(); pose.translate(-cam.x, -cam.y, -cam.z);
        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().last().pose().identity(); RenderSystem.applyModelViewMatrix();
        RenderSystem.disableCull(); RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        List<Vec3> burning = new ArrayList<>();
        for (Sample sample : DATA.values()) {
            GhostChainPacket p = sample.packet;
            if(p.mode()==0 || p.mode()==6 || p.mode()==MODE_PUNCH) { CHAINS.remove(p.player()*2); CHAINS.remove(p.player()*2+1); continue; }
            if (!(mc.level.getEntity(p.player()) instanceof Player player)) continue;
            if (!"ghost_rider".equals(com.FIRNI.superheromod.client.ClientHeroRegistry.get(player.getUUID()))) continue;
            if (player.distanceToSqr(cam) > 96 * 96) continue;
            float partial = mc.getFrameTime();
            float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
            float motionTick=motionTick(sample,partial);
            if(p.mode()==8) {
                Vec3 center=sample.previous.lerp(p.tip(),partial);
                Vec3 forward=p.tip().subtract(sample.previous).normalize();if(forward.lengthSqr()<.01)forward=player.getLookAngle();
                Vec3 lateral=forward.cross(new Vec3(0,1,0)).normalize();if(lateral.lengthSqr()<.01)lateral=new Vec3(1,0,0);
                final Vec3 along=forward, across=lateral;
                for(int sign:new int[]{-1,1}) {
                    Vec3 ring=center.add(across.scale(sign*.75));
                    java.util.function.DoubleFunction<Vec3> curve=t->ring.add(along.scale(Math.cos(t*Math.PI*2+motionTick*.55)*1.2)).add(0,Math.sin(t*Math.PI*2+motionTick*.55)*1.2,0);
                    drawLinks(b,pose,curve,80,10,burning);captureTrail(p.player()*2+(sign<0?0:1),curve,clock,1);
                    for(int i=0;i<8;i++)HellfireBreathRenderer.contact(curve.apply(i/8.0),.35);
                }
                continue;
            }
            if(p.mode()==7) {
                Vec3 feet=player.getPosition(partial);
                double now=mc.level.getGameTime()+partial, ground=groundUnder(mc,player,feet);
                double spin=GhostComboMotion.chargeSpin(motionTick);
                for(int sideSign:new int[]{-1,1}) {
                    final int sign=sideSign, key=p.player()*2+(sign<0?0:1);
                    // The anchor uses the same hand shift the arm mesh was drawn with this frame.
                    Vec3 shift=SHIFT.getOrDefault(key,Vec3.ZERO);
                    ChainDynamics.Target ring=(t,time)->GhostComboMotion.chargePoint(feet,yaw,(float)(motionTick-(now-time)),sign,t,shift);
                    ChainDynamics dynamics=CHAINS.computeIfAbsent(key,id->new ChainDynamics());
                    dynamics.floor(ground);
                    dynamics.drive(ring,now,ChainDynamics.SPIN);
                    java.util.function.DoubleFunction<Vec3> curve=dynamics::point;
                    drawLinks(b,pose,curve,110,p.heat(),burning);
                    if(p.heat()>=4)for(int i=0;i<8;i++)HellfireBreathRenderer.contact(curve.apply(i/8.0),.3);
                    captureTrail(key,curve,clock,p.heat()/10f);
                    sparks(mc,dynamics);
                    // Reaction: the chain's pull drags the hands toward it, harder as the spin builds.
                    Vec3 pull=curve.apply(.08).subtract(curve.apply(0));
                    Vec3 wanted=Vec3.ZERO;
                    if(pull.lengthSqr()>1e-6) {
                        Vec3 local=pull.normalize().yRot((float)Math.toRadians(yaw));
                        wanted=new Vec3(local.x,-local.y,-local.z).scale(Math.min(.11,.02+.065*spin));
                    }
                    double blend=1-Math.exp(-frameDt*2.5);
                    SHIFT.put(key,shift.lerp(wanted,blend));
                }
                continue;
            }
            if(p.mode()==1) {
                Vec3 feet=player.getPosition(partial);
                boolean riding=player.getVehicle() instanceof com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity;
                double now=mc.level.getGameTime()+partial, ground=groundUnder(mc,player,feet);
                int[] sides=p.combo()==2?new int[]{-1,1}:new int[]{GhostComboMotion.pose(p.combo(),motionTick).handSide()};
                for(int slot=0;slot<sides.length;slot++) {
                    int handSide=sides[slot];
                    // Physics time -> choreography time; the hand is the only pinned link.
                    ChainDynamics.Target lash=(t,time)->comboShape(feet,yaw,p.combo(),handSide,riding,(float)(motionTick-(now-time)),t);
                    ChainDynamics dynamics=CHAINS.computeIfAbsent(p.player()*2+slot,id->new ChainDynamics());
                    dynamics.floor(ground);
                    dynamics.drive(lash,now);
                    sparks(mc,dynamics);
                    java.util.function.DoubleFunction<Vec3> curve=dynamics::point;
                    double length=0;Vec3 last=curve.apply(0);
                    for(int n=1;n<=12;n++){Vec3 next=curve.apply(n/12.0);length+=next.distanceTo(last);last=next;}
                    if(length<.04)continue;
                    if(motionTick>2 && motionTick<GhostComboMotion.duration(p.combo())-2)
                        captureTrail(p.player()*2+slot,curve,clock,p.heat()/10f);
                    drawLinks(b,pose,curve,Math.min(240,Math.max(1,(int)(length/LINK_SPACING))),p.heat(),burning);
                    if(p.heat()>=4)for(int i=0;i<8;i++)HellfireBreathRenderer.contact(curve.apply(i/8.0),.3);
                }
                continue;
            }
            Vec3 from = GhostComboMotion.hand(player.getPosition(partial),yaw,samplePose(p,motionTick));
            Vec3 to = sample.previous.lerp(p.tip(), partial);
            double length = from.distanceTo(to);
            if (length < 0.04) continue;
            int links = Math.min(240, Math.max(1, (int)(length / 0.10)));
            ChainDynamics dynamics=CHAINS.computeIfAbsent(p.player()*2, id -> new ChainDynamics());
            dynamics.update(from,to,mc.level.getGameTime()+partial);
            drawLinks(b,pose,dynamics::point,links,p.heat(),burning);
            if(p.heat()>=4)for(int i=0;i<8;i++)HellfireBreathRenderer.contact(dynamics.point(i/8.0),.3);
        }
        Tesselator.getInstance().end(); RenderSystem.enableCull();
        if(GhostFireMaterial.ready() && !TRAILS.isEmpty()) {
            var buffers=mc.renderBuffers().bufferSource();var ribbon=buffers.getBuffer(GhostFireMaterial.TYPE);
            // Swept surface between consecutive chain snapshots: a motion smear that is only
            // visible where the chain actually travels fast, strongest toward the tip.
            for(var history:TRAILS.values()) {
                TrailFrame previous=null;
                for(var frame:history) {
                    if(previous!=null)for(int n=TRAIL_FIRST;n<TRAIL_POINTS-1;n++) {
                        Vec3[] corners={previous.points[n],frame.points[n],frame.points[n+1],previous.points[n+1]};
                        double travel=frame.points[n+1].distanceTo(previous.points[n+1])/Math.max(.05,frame.tick-previous.tick);
                        float speed=(float)Math.min(1,Math.max(0,(travel-.6)/1.8));
                        float opacity=(float)Math.pow(Math.max(0,1-(clock-frame.tick)/TRAIL_LIFE),1.5)*(.5f+.25f*frame.heat)*speed;
                        if(opacity<.01f)continue;
                        for(int c=0;c<4;c++) {
                            Vec3 pos=corners[c];
                            GhostFireMaterial.vertex(ribbon,pose,pos.x,pos.y,pos.z,(float)Math.max(.001,Math.min(.999,1-(clock-(c==0 || c==3?previous.tick:frame.tick))/TRAIL_LIFE)),
                                    (n-TRAIL_FIRST+(c>=2?1:0))/(float)(TRAIL_POINTS-1-TRAIL_FIRST),frame.heat<.4f?.46f:0,.37f,1,opacity);
                        }
                    }
                    previous=frame;
                }
            }
            buffers.endBatch(GhostFireMaterial.TYPE);
        }
        if(!burning.isEmpty()) {
            var buffers=mc.renderBuffers().bufferSource();
            for(Vec3 flame:burning) {
                pose.pushPose();pose.translate(flame.x,flame.y,flame.z);pose.scale(1,-1,1);
                GhostMaterials.flames(pose,buffers,mc.level.getGameTime()+mc.getFrameTime(),0,.4f,.22f);
                pose.popPose();
            }
            buffers.endBatch(GhostFireMaterial.TYPE);
        }
        RenderSystem.getModelViewStack().popPose(); RenderSystem.applyModelViewMatrix(); pose.popPose();
    }
    private static Vec3 toPoint(Sample sample,Player player,float partial,float yaw,float tick) {
        return sample.packet.mode()==1?GhostComboMotion.tip(player.getPosition(partial),yaw,sample.packet.combo(),tick)
                :sample.previous.lerp(sample.packet.tip(),partial);
    }
    private static void captureTrail(int key,java.util.function.DoubleFunction<Vec3> curve,double clock,float heat) {
        var history=TRAILS.computeIfAbsent(key,id->new ArrayDeque<>());
        if(history.isEmpty() || clock-history.peekLast().tick>=TRAIL_INTERVAL) {
            Vec3[] points=new Vec3[TRAIL_POINTS];for(int n=0;n<points.length;n++)points[n]=curve.apply(n/(double)(TRAIL_POINTS-1));
            history.addLast(new TrailFrame(clock,points,heat));while(history.size()>12)history.removeFirst();
        }
    }
    private static void drawLinks(BufferBuilder b,PoseStack pose,java.util.function.DoubleFunction<Vec3> curve,
                                  int links,int heat,List<Vec3> burning) {
        for(int i=0;i<links;i++) {
            double t=(i+.5)/links;
            Vec3 center=curve.apply(t);
            if(heat>=4 && i%15==0 && burning.size()<8)burning.add(center);
            Vec3 axis=curve.apply(Math.min(1,t+.005)).subtract(curve.apply(Math.max(0,t-.005))).normalize();
            if(axis.lengthSqr()<.01)continue;
            Vec3 side=axis.cross(new Vec3(0,1,0));if(side.lengthSqr()<.01)side=axis.cross(new Vec3(1,0,0));
            side=side.normalize();Vec3 width=i%2==0?side:axis.cross(side).normalize();Vec3 normal=axis.cross(width);
            for(int a=0;a<10;a++) {
                double u=a*Math.PI/5,v=(a+1)*Math.PI/5;
                Vec3 c1=center.add(axis.scale(Math.cos(u)*LINK_LENGTH)).add(width.scale(Math.sin(u)*LINK_WIDTH));
                Vec3 c2=center.add(axis.scale(Math.cos(v)*LINK_LENGTH)).add(width.scale(Math.sin(v)*LINK_WIDTH));
                Vec3 r1=axis.scale(Math.cos(u)).add(width.scale(Math.sin(u))).normalize();
                Vec3 r2=axis.scale(Math.cos(v)).add(width.scale(Math.sin(v))).normalize();
                for(int face=0;face<4;face++) {
                    double f=face*Math.PI/2,g=(face+1)*Math.PI/2;
                    vertex(b,pose.last().pose(),c1.add(r1.scale(Math.cos(f)*LINK_WIRE)).add(normal.scale(Math.sin(f)*LINK_WIRE)),heat,face);
                    vertex(b,pose.last().pose(),c2.add(r2.scale(Math.cos(f)*LINK_WIRE)).add(normal.scale(Math.sin(f)*LINK_WIRE)),heat,face);
                    vertex(b,pose.last().pose(),c2.add(r2.scale(Math.cos(g)*LINK_WIRE)).add(normal.scale(Math.sin(g)*LINK_WIRE)),heat,face);
                    vertex(b,pose.last().pose(),c1.add(r1.scale(Math.cos(g)*LINK_WIRE)).add(normal.scale(Math.sin(g)*LINK_WIRE)),heat,face);
                }
            }
        }
    }
    /** Authored lash shape at a given choreography tick; only a spring target for the physics chain. */
    private static Vec3 comboShape(Vec3 feet,float yaw,int combo,int side,boolean riding,float tick,double t) {
        Vec3 hand=GhostComboMotion.modelToWorld(feet,yaw,GhostComboMotion.arm(GhostComboMotion.pose(combo,tick),side).hand());
        if(t<=0)return hand;
        Vec3 tip=GhostComboMotion.tip(feet,yaw,combo,tick,side);
        if(riding)tip=hand.add(tip.subtract(hand).scale(1.65));
        return GhostComboMotion.point(hand,tip,combo,tick,t);
    }
    private static void vertex(BufferBuilder b, Matrix4f m, Vec3 p, int heat, int face) {
        float h = heat / 10f, shade = 0.65f + face * 0.1f;
        b.vertex(m, (float)p.x, (float)p.y, (float)p.z).color((0.12f + 0.88f*h)*shade, (0.13f + 0.2f*h)*shade, (0.15f*(1-h))*shade, 1f).endVertex();
    }
}
