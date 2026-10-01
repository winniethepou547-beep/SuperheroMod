package com.FIRNI.superheromod.core.cinematic;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Matches ModelPart's Rz * Ry * Rx convention, including the gimbal-lock limit. */
public final class JointRotations {
    private JointRotations() {}
    public static Vector3f toEuler(Quaternionf q,Vector3f out) {
        double length=Math.sqrt(q.x*q.x+q.y*q.y+q.z*q.z+q.w*q.w);
        if(!(length>0)||!Double.isFinite(length))throw new IllegalArgumentException("Invalid joint quaternion");
        double x=q.x/length,y=q.y/length,z=q.z/length,w=q.w/length;
        double sin=Math.max(-1,Math.min(1,2*(w*y-z*x)));
        double pitch=Math.asin(sin),roll,yaw;
        if(Math.abs(sin)>1-1e-7){roll=0;yaw=2*Math.atan2(z,w);}
        else {roll=Math.atan2(2*(w*x+y*z),1-2*(x*x+y*y));yaw=Math.atan2(2*(w*z+x*y),1-2*(y*y+z*z));}
        return out.set((float)roll,(float)pitch,(float)yaw);
    }
}
