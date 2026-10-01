package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;

/** Target point is in the target rig's chest space, measured in blocks. */
public record CinematicHandContact(String sourceRole,String targetRole,boolean rightHand,
                                   Vec3 targetPoint,int start,int end) {
    public CinematicHandContact {
        if(sourceRole==null||targetRole==null||sourceRole.equals(targetRole)||targetPoint==null
                ||!Double.isFinite(targetPoint.lengthSqr())||start<0||end<=start)
            throw new IllegalArgumentException("Invalid hand contact");
    }
    public float weight(float tick) {
        float ramp=Math.min(8,(end-start)*.5f);
        float t=Math.max(0,Math.min(1,Math.min((tick-start)/ramp,(end-tick)/ramp)));
        return t*t*(3-2*t);
    }
}
