package com.FIRNI.superheromod;

import com.FIRNI.superheromod.heroes.ghostrider.*;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

public final class GhostMotionCheck {
    public static void main(String[] args) {
        if(HellfireBreathMath.damage(10,false)>.5f)throw new AssertionError("First hit too strong");
        float previousDamage=0, previousIncrement=Float.MAX_VALUE;
        for(int ticks=10;ticks<=1200;ticks+=10) {
            float damage=HellfireBreathMath.damage(ticks,true);
            float increment=damage-previousDamage;
            if(damage<previousDamage || damage>6)throw new AssertionError("Invalid damage ramp");
            if(ticks>20 && increment>previousIncrement+.0001f)throw new AssertionError("Not logarithmic");
            if(damage<HellfireBreathMath.damage(ticks,false))throw new AssertionError("Burn bonus missing");
            previousDamage=damage;previousIncrement=increment;
        }
        Vec3 forward=new Vec3(0,0,1);
        if(!HellfireBreathMath.contains(Vec3.ZERO,forward,new Vec3(3,0,16),18,0)
            || HellfireBreathMath.contains(Vec3.ZERO,forward,new Vec3(3,0,1),18,0)
            || HellfireBreathMath.contains(Vec3.ZERO,forward,new Vec3(0,0,-1),18,0)
            || HellfireBreathMath.contains(Vec3.ZERO,forward,new Vec3(0,0,19),18,0))
            throw new AssertionError("Invalid widening flame cone");
        System.out.println("PASS: low initial damage, burning bonus, diminishing ramp and flame reach");
        for(int side:new int[]{-1,1}) for(float pitch:new float[]{0,-35,-70}) for(float roll:new float[]{-18,0,18}) {
            var arm=GhostRidingArms.solve(side,pitch,roll);
            if(!Double.isFinite(arm.elbow().length()))throw new AssertionError("Invalid riding elbow");
            var m=new Matrix4f().rotateX((float)java.lang.Math.toRadians(-pitch)).rotateZ((float)java.lang.Math.toRadians(roll));
            var grip=m.transformPosition(new Vector3f(-side*GhostRidingArms.GRIP_X/16,GhostRidingArms.GRIP_Y/16,GhostRidingArms.GRIP_Z/16));
            var seat=m.transformPosition(new Vector3f(0,14f/16,6f/16));
            double scale=HellCycleRig.PLAYER_SCALE;
            Vec3 hand=arm.hand();
            Vec3 actual=new Vec3(seat.x-hand.x*scale,seat.y+(.75-hand.y)*scale,seat.z+hand.z*scale);
            if(actual.distanceTo(new Vec3(grip.x,grip.y,grip.z))>1e-6)throw new AssertionError("Hand missed handlebar");
            if(pitch==0 && roll==0 && java.lang.Math.abs(arm.shoulder().distanceTo(arm.elbow())-.25)>.001)
                throw new AssertionError("Riding arm stretched on flat ground");
        }
        Vector3f flame=new Matrix4f().rotateX((float)(-java.lang.Math.PI/2)).transformDirection(new Vector3f(0,-1,0));
        if(flame.z<.99 || java.lang.Math.abs(flame.y)>.001)throw new AssertionError("Wheel fire not directed rearward");
        for(float yaw:new float[]{0,45,90,180,270}) for(float pitch:new float[]{0,-70}) for(float lean:new float[]{-18,0,18}) {
            Vector3f mesh=new Matrix4f().rotateY((float)java.lang.Math.toRadians(180-yaw))
                    .rotateX((float)java.lang.Math.toRadians(-pitch)).rotateZ((float)java.lang.Math.toRadians(lean))
                    .transformPosition(new Vector3f(0,14f/16,6f/16));
            Vec3 hips=HellCycleRig.riderFeet(yaw,pitch,lean).add(0,HellCycleRig.PELVIS_HEIGHT,0);
            if(hips.distanceTo(new Vec3(mesh.x,mesh.y+HellCycleRig.rearPivotLift(pitch),mesh.z))>1e-5)throw new AssertionError("Rider detached from seat");
        }
        for(int combo=0;combo<3;combo++) {
            Vec3 last=GhostComboMotion.tip(Vec3.ZERO,0,combo,0);
            for(float tick=.01f;tick<=GhostComboMotion.duration(combo);tick+=.01f) {
                Vec3 tip=GhostComboMotion.tip(Vec3.ZERO,0,combo,tick);
                if(!Double.isFinite(tip.length()) || tip.distanceTo(last)>.06*GhostComboMotion.REACH)throw new AssertionError("Discontinuous lash at "+tick);
                last=tip;
            }
            float impact=GhostComboMotion.impactTick(combo);
            var body=GhostComboMotion.pose(combo,impact);
            var arm=GhostComboMotion.arm(body,body.handSide());
            Vec3 renderedHand=GhostComboMotion.modelToWorld(Vec3.ZERO,0,arm.hand());
            Vec3 hand=GhostComboMotion.hand(Vec3.ZERO,0,GhostComboMotion.pose(combo,impact));
            if(renderedHand.distanceTo(hand)>1e-6)throw new AssertionError("Rendered arm and chain disagree");
            Vec3 tip=GhostComboMotion.tip(Vec3.ZERO,0,combo,impact);
            if(tip.z-hand.z<3)throw new AssertionError("Contact frame misses forward target");
            if(GhostComboMotion.point(hand,tip,combo,impact,0).distanceTo(hand)>1e-6
                    ||GhostComboMotion.point(hand,tip,combo,impact,1).distanceTo(tip)>1e-6)throw new AssertionError("Chain anchor drift");
            Vec3 turned=GhostComboMotion.tip(Vec3.ZERO,90,combo,impact);
            if(turned.distanceTo(tip.yRot((float)(-java.lang.Math.PI/2)))>1e-5)throw new AssertionError("Yaw mismatch");
        }
        for(int combo=0;combo<3;combo++) {
            float hit=GhostComboMotion.impactTick(combo);
            Vec3 ha=GhostComboMotion.hand(Vec3.ZERO,0,GhostComboMotion.pose(combo,hit-3));
            Vec3 hb=GhostComboMotion.hand(Vec3.ZERO,0,GhostComboMotion.pose(combo,hit+3));
            Vec3 ta=GhostComboMotion.tip(Vec3.ZERO,0,combo,hit-3);
            Vec3 tb=GhostComboMotion.tip(Vec3.ZERO,0,combo,hit+3);
            if(hb.y>=ha.y || tb.y>=ta.y)throw new AssertionError("Strike must descend");
            if(combo<2 && ((hb.x-ha.x)*(combo==0?1:-1)<=0 || (tb.x-ta.x)*(combo==0?1:-1)<=0))
                throw new AssertionError("Hand/lash diagonal direction mismatch");
            if(combo==2 && java.lang.Math.abs(tb.x-ta.x)>.1)throw new AssertionError("Overhead slash drifts sideways");
        }
        for(float tick=0;tick<80;tick+=.25f)for(int side:new int[]{-1,1}) {
            var arm=GhostComboMotion.chargeArm(tick,side);
            for(float yaw:new float[]{0,90,180}) {
                Vec3 grip=GhostComboMotion.modelToWorld(Vec3.ZERO,yaw,arm.hand());
                Vec3 anchor=GhostComboMotion.chargePoint(Vec3.ZERO,yaw,tick,side,0);
                if(grip.distanceTo(anchor)>1e-6)throw new AssertionError("Charge chain detached from hand");
            }
            if(arm.shoulder().distanceTo(arm.elbow())>.27 || arm.elbow().distanceTo(arm.hand())>.37)
                throw new AssertionError("Charge arm stretched");
        }
        if(GhostComboMotion.pose(1,8).handSide()!=1 || !GhostComboMotion.pose(2,10).twinChains())throw new AssertionError("Wrong combo hands");
        if(GhostComboMotion.chargePhase(41)-GhostComboMotion.chargePhase(40)<=GhostComboMotion.chargePhase(2)-GhostComboMotion.chargePhase(1))throw new AssertionError("Charge does not accelerate");
        for(int side:new int[]{-1,1}) {
            Vec3 a=GhostComboMotion.chargePoint(Vec3.ZERO,0,40,side,.3);
            Vec3 b=GhostComboMotion.chargePoint(Vec3.ZERO,0,40,side,.9);
            if(java.lang.Math.abs(a.x-b.x)>.001)throw new AssertionError("Ring not in a vertical side plane");
        }
        double speed=.25;
        for(int i=0;i<200;i++) {
            double next=GhostRideMath.accelerate(speed,1);
            if(next<speed || next>GhostRideMath.MAX_SPEED)throw new AssertionError("Unbounded acceleration");
            speed=next;
        }
        if(speed<2.1 || GhostRideMath.turnRate(speed)<6 || GhostRideMath.accelerate(speed,-1)>=speed)throw new AssertionError("Speed/steering/brake tuning invalid");
        for(float yaw:new float[]{0,90,180,270}) {
            Vec3 position=new Vec3(13,5,-7),target=GhostRideMath.pullDestination(position,yaw);
            if(java.lang.Math.abs(target.distanceTo(position)-12)>1e-5 || target.y!=position.y)throw new AssertionError("Mounted pull offset incorrect");
        }
        System.out.println("PASS: bounded acceleration, braking, sharp steering, moving 12-block pull destination");
        for(float angle:new float[]{0,15,30,45,90}) {
            Vec3 launch=GhostRideMath.wheelieLaunch(0,angle,1.5);
            double degrees=java.lang.Math.toDegrees(java.lang.Math.atan2(launch.y,launch.horizontalDistance()));
            if(java.lang.Math.abs(degrees-java.lang.Math.min(45,angle))>.001 || launch.z<=0)throw new AssertionError("Wheelie launch angle incorrect");
        }
        if(GhostRideMath.chainDamage(0,4)<=GhostRideMath.chainDamage(0,3)*1.3)throw new AssertionError("Ignited chain bonus missing");
        System.out.println("PASS: 45 degree wheelie limit, directional launch and ignited chain damage");
        var pull=GhostComboMotion.grapple(12,true);
        if(!pull.twoHanded() || java.lang.Math.abs(pull.torso())<.3)throw new AssertionError("Missing two-hand body pull");
        double grips=GhostComboMotion.arm(pull,-1).hand().distanceTo(GhostComboMotion.arm(pull,1).hand());
        if(grips<.1 || grips>.5)throw new AssertionError("Pull grips not on same chain");
        // Hand over hand: half a cycle later the other hand leads.
        var later=GhostComboMotion.grapple(12+GhostComboMotion.HAUL_PERIOD/2,true);
        double lead=GhostComboMotion.arm(pull,-1).hand().z-GhostComboMotion.arm(pull,1).hand().z;
        double swapped=GhostComboMotion.arm(later,-1).hand().z-GhostComboMotion.arm(later,1).hand().z;
        if(lead*swapped>=0)throw new AssertionError("Hands do not alternate while hauling");
        if(GhostComboMotion.pullStrength(5)<=GhostComboMotion.pullStrength(5+GhostComboMotion.HAUL_PERIOD/4f)*0)throw new AssertionError("Haul strength invalid");
        for(float tick=0;tick<=20;tick+=.5f) {
            for(var pose:new GhostComboMotion.Pose[]{GhostComboMotion.cast(tick),GhostComboMotion.slamThrow(tick),GhostComboMotion.slamLift(tick),GhostComboMotion.slamDown(tick),GhostComboMotion.penance(tick*20.5f,PenanceStare.GRAB,PenanceStare.LIFT,PenanceStare.CLIMAX,PenanceStare.CLIMAX_END,PenanceStare.TOTAL)})
                for(int side:new int[]{-1,1}) {
                    var arm=GhostComboMotion.arm(pose,side);
                    if(arm.shoulder().distanceTo(arm.elbow())>.27 || arm.elbow().distanceTo(arm.hand())>.37)
                        throw new AssertionError("Throw/slam arm stretched at "+tick+" side "+side+" grip "+pose.grip()+" lean "+pose.lean()+" upper "+arm.shoulder().distanceTo(arm.elbow()));
                }
        }
        if(GhostComboMotion.slamLift(GhostComboMotion.LIFT_TOTAL).grip().y>-.3)throw new AssertionError("Chains not hoisted overhead");
        System.out.println("PASS: diagonal directions, descending overhead, dual charge anchors and two-hand pull");
        var shot=com.FIRNI.superheromod.client.render.film.FilmShot.of(0,10)
                .path(new Vec3(0,0,0),new Vec3(1,2,0),new Vec3(3,2,1),new Vec3(4,0,2)).build();
        for(int i=0;i<4;i++)if(shot.position(i/3f).distanceTo(shot.path()[i])>1e-6)throw new AssertionError("Camera spline misses its control point "+i);
        Vec3 previous=shot.position(0);
        for(float k=.002f;k<=1;k+=.002f){Vec3 next=shot.position(k);if(next.distanceTo(previous)>.05)throw new AssertionError("Camera spline jumps at "+k);previous=next;}
        Vec3 last=PenanceStare.victimAt(Vec3.ZERO,30,new Vec3(2,0,3),0);
        for(float t=.05f;t<=PenanceStare.CLIMAX_END;t+=.05f){
            Vec3 at=PenanceStare.victimAt(Vec3.ZERO,30,new Vec3(2,0,3),t);
            if(at.distanceTo(last)>.2)throw new AssertionError("Penance choreography jumps at "+t);
            last=at;
        }
        System.out.println("PASS: film camera spline through every key, smooth Penance choreography");
        System.out.println("PASS: seat/mesh alignment, both handlebar contacts, rearward wheel fire, rendered hand/chain agreement, continuous lashes, contact reach, rotation covariance");
    }
}
