package com.FIRNI.superheromod.heroes.ghostrider;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Analytic two-bone contacts in player model space; no frame-dependent spring on the grips. */
public final class GhostRidingArms {
    public static final float LEAN=.46f;
    public static final float GRIP_X=4.4f, GRIP_Y=19, GRIP_Z=-6;
    public record Arm(Vec3 shoulder,Vec3 elbow,Vec3 hand) {}
    public static Arm solve(int side,float pitch,float roll) {
        var rot=new Matrix4f().rotateX((float)Math.toRadians(-pitch)).rotateZ((float)Math.toRadians(roll));
        var grip=rot.transformPosition(new Vector3f(-side*GRIP_X/16,GRIP_Y/16,GRIP_Z/16));
        var seat=rot.transformPosition(new Vector3f(0,(HellCycleRig.SEAT_Y+1)/16,HellCycleRig.SEAT_Z/16));
        double scale=HellCycleRig.PLAYER_SCALE;
        Vec3 target=new Vec3(-(grip.x-seat.x)/scale,.75-(grip.y-seat.y)/scale,(grip.z-seat.z)/scale);
        Vec3 shoulder=new Vec3(side*.375,.75-.625*Math.cos(LEAN),-.625*Math.sin(LEAN));
        return solve(shoulder,target,new Vec3(side,.2,.2));
    }
    public static Arm solve(Vec3 shoulder,Vec3 target,Vec3 pole) {
        Vec3 delta=target.subtract(shoulder);
        double distance=Math.max(1e-6,delta.length());
        Vec3 axis=delta.scale(1/distance);
        double stretch=Math.max(1,distance/.60+.001);
        double upper=.25*stretch,lower=.35*stretch;
        double along=(upper*upper-lower*lower+distance*distance)/(2*distance);
        double height=Math.sqrt(Math.max(0,upper*upper-along*along));
        Vec3 bend=pole.subtract(axis.scale(pole.dot(axis)));
        if(bend.lengthSqr()<1e-8)bend=axis.cross(new Vec3(0,0,1));
        if(bend.lengthSqr()<1e-8)bend=axis.cross(new Vec3(1,0,0));
        Vec3 elbow=shoulder.add(axis.scale(along)).add(bend.normalize().scale(height));
        return new Arm(shoulder,elbow,target);
    }
}
