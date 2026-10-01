package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.core.cinematic.*;
import net.minecraft.world.phys.Vec3;
import static com.FIRNI.superheromod.core.cinematic.ActorPose.*;
import static com.FIRNI.superheromod.core.cinematic.CinematicActorTrack.Binding.*;

/** Choreography rehearsal, not the final damaging finisher. No real summons or terrain edits. */
public final class SandArmyPreview {
    private static final CinematicAnimationClip COUNTER=CinematicAnimationClip.bundled("counter_cross");
    private static final com.FIRNI.superheromod.core.cinematic.GltfPoseClip COUNTER_ELBOW=
            com.FIRNI.superheromod.core.cinematic.GltfPoseClip.bundled("counter_elbow");
    private static final CinematicAnimationClip BACKHAND=CinematicAnimationClip.bundled("evade_backhand");
    private static final CinematicAnimationClip STRUCK=CinematicAnimationClip.bundled("struck_back");
    private static final CinematicAnimationClip ADVANCE=CinematicAnimationClip.bundled("advance_cycle");
    public static final String ID="sand_army_preview";
    public static void register() { CinematicRegistry.register(create()); }
    public static CinematicDefinition create() {
        int firstContact=44+(int)COUNTER.marker("contact");
        int secondContact=72+(int)BACKHAND.marker("contact");
        var b=CinematicDefinition.builder(ID).letterbox(false).stageSpan(8).isolatedStage()
                .atmosphere(8,30,0x63594C)
                .shot(Shot.of(34).cut().breath(.08f).fov(60)
                        .move(new Vec3(5,2.5,-.4),new Vec3(4,2,.35)).lookAtMidpoint(1.1).build())
                .shot(Shot.of(30).cut().breath(.1f).fov(62)
                        .move(new Vec3(0,1.4,1.65),new Vec3(-.3,1.5,1.7)).lookAtTarget(1).build())
                .shot(Shot.of(30).cut().breath(.1f).fov(62)
                        .move(new Vec3(0,1.3,.35),new Vec3(.3,1.5,.3)).lookAtTarget(1).build())
                .shot(Shot.of(46).cut().breath(.08f).fov(68)
                        .curve(new Vec3(4,2,1.5),new Vec3(4,2.5,1.6),new Vec3(2,3.5,1.7),new Vec3(1,3.8,1.65))
                        .lookAtTarget(.8).build())
                .shot(Shot.of(44).cut().breath(.05f).fov(65)
                        .curve(new Vec3(-3,.6,.7),new Vec3(-4,1,.6),new Vec3(-4,2,.6),new Vec3(-5,2.8,.7))
                        .lookAtTarget(1.6).build())
                .shot(Shot.of(26).cut().breath(.05f).fov(48)
                        .move(new Vec3(-2.4,1.5,.3),new Vec3(-1.7,1.6,.35)).lookAtAttacker(1.4).build())
                .shot(Shot.of(30).cut().breath(.08f).fov(68)
                        .curve(new Vec3(5,2,.3),new Vec3(5,3,.5),new Vec3(4,4,1.1),new Vec3(3.8,4.5,1.3))
                        .lookAtFixed(new Vec3(0,1.7,1)).build())
                .shot(Shot.of(30).transition(.32f).breath(.05f).fov(62,72)
                        .move(new Vec3(3.8,4.5,1.3),new Vec3(5.5,4.2,1.45)).lookAtTarget(.8).build())
                .shot(Shot.of(60).transition(.12f).breath(.05f).fov(65,58)
                        .curve(new Vec3(5.5,4.2,1.45),new Vec3(5,3,1.4),new Vec3(4,1.7,1.4),new Vec3(3.8,1.6,1.4))
                        .lookAtTarget(.5).build())
                .impact(new CinematicImpact(firstContact,7,.55f,9,1))
                .impact(new CinematicImpact(secondContact,8,.7f,10,-1))
                .impact(new CinematicImpact(137,10,1.1f,8,1))
                .impact(new CinematicImpact(210,12,1.4f,6,-1))
                .impact(new CinematicImpact(260,12,2.4f,10,1))
                .impact(new CinematicImpact(281,18,3f,5,-1))
                .setPiece(new CinematicSetPiece(new Vec3(0,3.8,1),1.7f,225,245,260,295));
        ActorPose calm=ActorPose.of();
        ActorPose command=ActorPose.of().j(RIGHT_UPPER_ARM,-75,-12,12).j(RIGHT_LOWER_ARM,-40,0,0);
        ActorPose lift=ActorPose.of().j(RIGHT_UPPER_ARM,-135,-8,10).j(RIGHT_LOWER_ARM,-22,0,0);
        b.actor(CinematicActorTrack.of("sandman",ATTACKER)
                .key(0,Vec3.ZERO,0,1,calm).key(32,Vec3.ZERO,0,1,command)
                .key(148,Vec3.ZERO,0,1,command).key(182,new Vec3(0,0,.12),0,1,command)
                .key(215,new Vec3(0,0,.12),0,1,lift)
                .key(245,new Vec3(0,0,.12),0,1,lift.copy().j(RIGHT_LOWER_ARM,-65,0,0))
                .key(260,new Vec3(0,0,.12),0,1,command)
                .key(290,new Vec3(0,0,.12),0,1,calm).key(300,new Vec3(0,0,.12),0,1,calm).build());
        ActorPose guard=ActorPose.of().j(RIGHT_UPPER_ARM,-65,0,30).j(RIGHT_LOWER_ARM,-65,0,0)
                .j(LEFT_UPPER_ARM,-45,0,-30).j(LEFT_LOWER_ARM,-75,0,0).j(HEAD,0,20,0);
        ActorPose recoil=guard.copy().body(-10,-18).j(CHEST,-18,20,0);
        ActorPose counter=guard.copy().body(0,8).j(CHEST,0,-38,0)
                .j(RIGHT_UPPER_ARM,-92,-35,10).j(RIGHT_LOWER_ARM,-4,0,0);
        ActorPose evade=guard.copy().crouch(.28f).body(-22,18)
                .j(RIGHT_UPPER_LEG,-35,0,0).j(RIGHT_LOWER_LEG,60,0,0);
        ActorPose backhand=guard.copy().body(0,12).j(CHEST,0,45,0)
                .j(LEFT_UPPER_ARM,-80,65,-10).j(LEFT_LOWER_ARM,-8,0,0);
        ActorPose pinned=ActorPose.of().crouch(.25f).body(0,12)
                .j(RIGHT_UPPER_ARM,-40,0,68).j(LEFT_UPPER_ARM,-40,0,-68)
                .j(RIGHT_LOWER_ARM,-65,0,0).j(LEFT_LOWER_ARM,-65,0,0)
                .j(RIGHT_UPPER_LEG,-28,0,0).j(RIGHT_LOWER_LEG,50,0,0)
                .j(LEFT_UPPER_LEG,-22,0,0).j(LEFT_LOWER_LEG,40,0,0);
        b.actor(CinematicActorTrack.of("opponent",TARGET)
                .model("defense")
                .rigClip(new com.FIRNI.superheromod.core.cinematic.RigClipPlacement("defense_counters",34,108,1,5,false))
                .key(0,new Vec3(0,0,1),180,1,calm).key(40,new Vec3(0,0,1),270,1,guard)
                .key(51,new Vec3(-.16,0,1.02),265,1,guard.copy().j(CHEST,0,25,0))
                .key(58,new Vec3(.12,0,1),270,1,counter,Easing.SURGE,0)
                .key(66,new Vec3(.12,0,1),270,1,counter)
                .key(76,new Vec3(0,0,1),180,1,guard)
                .key(84,new Vec3(.3,0,1.02),110,1,evade)
                .key(89,new Vec3(.05,0,1),90,1,backhand,Easing.SURGE,0)
                .key(96,new Vec3(.05,0,1),90,1,backhand)
                .key(108,new Vec3(0,0,1),180,1,guard)
                .key(119,new Vec3(0,0,1.08),180,1,recoil)
                .key(129,new Vec3(0,0,1.04),180,1,guard)
                .key(137,new Vec3(0,0,1.1),170,1,recoil.copy().body(12,-20))
                .key(154,new Vec3(0,0,1),180,1,pinned)
                .key(188,new Vec3(0,0,1),180,1,pinned)
                .key(210,new Vec3(0,1.6,1),180,1,pinned)
                .key(225,new Vec3(0,3,1),180,1,pinned)
                .key(260,new Vec3(0,3,1),180,1,pinned)
                .key(268,new Vec3(0,2.5,1),190,1,recoil.copy().body(12,-25))
                .key(276,new Vec3(0,1.1,1.05),195,1,recoil.copy().body(6,-65))
                .key(281,new Vec3(0,.2,1.12),195,1,calm.copy().body(0,-90))
                .key(285,new Vec3(0,.12,1.12),195,1,calm.copy().body(0,-90))
                .key(330,new Vec3(0,.12,1.12),195,1,calm.copy().body(0,-90))
                .build());
        b.contact(new CinematicHandContact("opponent","soldier_0",true,new Vec3(0,-.05,-.1),firstContact-4,firstContact+4));
        b.contact(new CinematicHandContact("opponent","soldier_1",false,new Vec3(0,-.05,-.1),secondContact-4,secondContact+4));
        for(int i=0;i<10;i++) {
            double angle=i==1 ? Math.PI : 2*Math.PI*i/10;
            Vec3 start=new Vec3(Math.cos(angle)*4,0,1+Math.sin(angle)*.55);
            Vec3 ring=new Vec3(Math.cos(angle)*.72,0,1+Math.sin(angle)*.09);
            int arrival=new int[]{12,35,92,92,100,100,105,108,110,112}[i];
            double tier=(i/3)*.55;
            Vec3 mound=new Vec3(ring.x*(1-tier*.23),tier,1+(ring.z-1)*(1-tier*.23));
            float yaw=(float)Math.toDegrees(Math.atan2(Math.cos(angle),-Math.sin(angle)));
            var track=CinematicActorTrack.of("soldier_"+i,SAND)
                    .key(0,start,yaw,0,calm)
                    .key(arrival-10,start,yaw,0,calm)
                    .key(arrival,start,yaw,1,run(1));
            track.layer(new CinematicClipLayer(ADVANCE,arrival,arrival+36,.9f+(i%4)*.07f,3,4,true,false));
            track.layer(new CinematicClipLayer(new CrowdMotionClip(i),arrival,225,1,5,8,false,true));
            for(int step=1;step<=6;step++) {
                float progress=step/6f;
                track.key(arrival+step*6,start.lerp(ring,progress),yaw,1,run(step%2==0?1:-1),Easing.LINEAR,0);
            }
            // The defender actually wins these exchanges: the attackers leave the fight permanently.
            if(i<2) {
                int hit=i==0?firstContact:secondContact;
                Vec3 thrown=ring.add(Math.cos(angle)*3,0,Math.sin(angle)*.38);
                ActorPose windup=run(1).j(RIGHT_UPPER_ARM,-130,-30,20).j(RIGHT_LOWER_ARM,-65,0,0);
                ActorPose strike=windup.copy().j(RIGHT_UPPER_ARM,-85,20,0).j(RIGHT_LOWER_ARM,-5,0,0);
                track.key(hit-4,ring,yaw,1,windup)
                        .key(hit,ring,yaw,1,strike,Easing.SURGE,0)
                        .key(hit+15,thrown,yaw+100,1,strike.copy().body(35,-80),Easing.OUT,.9f)
                        .key(hit+23,thrown,yaw+100,1,strike.copy().body(35,-90))
                        .key(hit+42,thrown.add(0,.3,0),yaw+100,1,strike.copy().body(35,-90))
                        .dissolve(hit+8,hit+42)
                        .key(330,thrown,yaw+100,1,strike);
                track.layer(new CinematicClipLayer(STRUCK,hit,hit+23,1,0,0,false,false));
                b.actor(track.build());
                continue;
            }
            ActorPose grab=ActorPose.of().body(0,12).j(RIGHT_UPPER_ARM,-85,0,8)
                    .j(LEFT_UPPER_ARM,-85,0,-8).j(RIGHT_LOWER_ARM,-20,0,0)
                    .j(LEFT_LOWER_ARM,-20,0,0).j(HEAD,12,0,0);
            track.key(146+i,ring,yaw,1,grab);
            for(int punch=0;punch<3;punch++) {
                int at=157+(i%3)+punch*6;
                boolean right=(i+punch)%2==0;
                ActorPose jab=grab.copy().j(right?RIGHT_UPPER_ARM:LEFT_UPPER_ARM,-98,right?-12:12,0)
                        .j(right?RIGHT_LOWER_ARM:LEFT_LOWER_ARM,-6-(i%3)*3,0,0).j(CHEST,0,right?-10-i%5:10+i%5,0);
                track.key(at,ring,yaw,1,jab,Easing.SURGE,0)
                        .key(at+3+(i%2),ring,yaw,1,grab);
            }
            track.key(180,mound,yaw,1,grab.copy().j(RIGHT_UPPER_LEG,-45,0,0).j(RIGHT_LOWER_LEG,65,0,0),Easing.LINEAR,.65f)
                    .key(188,mound,yaw,1,grab).key(225,mound.add(0,3,0),yaw,1,grab)
                    .key(245,new Vec3(0,3.8,1),yaw+70,1,grab)
                    .dissolve(225,245)
                    .key(300,new Vec3(0,3.8,1),yaw+70,0,grab);
            b.actor(track.build());
            for(boolean right:new boolean[]{true,false}) {
                b.contact(new CinematicHandContact("soldier_"+i,"opponent",right,
                        new Vec3(-Math.cos(angle)*.23, -.05+(right?-.07:.04),Math.sin(angle)*.14),
                        146+i,158));
            }
        }
        return b.build();
    }
    private static ActorPose run(int side) {
        return ActorPose.of().body(0,10).j(RIGHT_UPPER_ARM,side*38,0,8)
                .j(LEFT_UPPER_ARM,-side*38,0,-8).j(RIGHT_LOWER_ARM,-40,0,0)
                .j(LEFT_LOWER_ARM,-40,0,0).j(RIGHT_UPPER_LEG,-side*35,0,0)
                .j(LEFT_UPPER_LEG,side*35,0,0).j(RIGHT_LOWER_LEG,side>0?8:45,0,0)
                .j(LEFT_LOWER_LEG,side<0?8:45,0,0);
    }
}
