package com.FIRNI.superheromod.heroes.ghostrider;

import net.minecraft.world.phys.Vec3;

/** Model coordinates shared by the seat mesh and passenger attachment. */
public final class HellCycleRig {
    public static final float SEAT_Y=13, SEAT_Z=6;
    public static final double PLAYER_SCALE=.9375;
    public static final double PELVIS_HEIGHT=(1.501-.75)*PLAYER_SCALE;
    public static double rearPivotLift(float pitch) {return Math.max(0,Math.sin(Math.toRadians(-pitch)))*13/16;}
    public static Vec3 seat(float yaw,float pitch,float lean) {
        var v=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw))
                .rotateX((float)Math.toRadians(-pitch)).rotateZ((float)Math.toRadians(lean))
                .transformPosition(new org.joml.Vector3f(0,(SEAT_Y+1)/16,SEAT_Z/16));
        return new Vec3(v.x,v.y+rearPivotLift(pitch),v.z);
    }
    public static Vec3 riderFeet(float yaw,float pitch,float lean) {
        return seat(yaw,pitch,lean).subtract(0,PELVIS_HEIGHT,0);
    }
}
