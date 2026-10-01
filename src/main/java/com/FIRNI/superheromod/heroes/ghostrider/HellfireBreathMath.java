package com.FIRNI.superheromod.heroes.ghostrider;

import net.minecraft.world.phys.Vec3;

public final class HellfireBreathMath {
    public static final double RANGE=18;
    public static double radius(double distance) { return .18+4.8*Math.pow(Math.min(1,Math.max(0,distance)/RANGE),.85); }
    /** Health points per half-second pulse, not hearts; diminishing returns with a safety cap. */
    public static float damage(int exposureTicks) {
        return damage(exposureTicks,false);
    }
    public static float damage(int exposureTicks,boolean alreadyBurning) {
        double base=.5+.8*Math.log1p(Math.max(0,exposureTicks-10)/20.0);
        return (float)Math.min(6,base*(alreadyBurning?1.8:1));
    }
    public static boolean contains(Vec3 origin,Vec3 direction,Vec3 point,double length,double padding) {
        Vec3 delta=point.subtract(origin);
        double along=delta.dot(direction);
        if(along<0 || along>length+padding)return false;
        return delta.subtract(direction.scale(along)).lengthSqr()<=Math.pow(radius(along)+padding,2);
    }
}
