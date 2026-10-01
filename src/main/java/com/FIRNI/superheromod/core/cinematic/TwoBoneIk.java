package com.FIRNI.superheromod.core.cinematic;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Two positive-Y bones, solved in shoulder space. Unreachable goals clamp without stretching. */
public final class TwoBoneIk {
    public record Solution(Quaternionf upper, Quaternionf lower, Vector3f endpoint) {}
    public static Solution solve(Vector3f target, Vector3f pole, float upperLength, float lowerLength) {
        if(!target.isFinite()||!pole.isFinite()||!Float.isFinite(upperLength)||!Float.isFinite(lowerLength)
                || upperLength<=0||lowerLength<=0)throw new IllegalArgumentException("Invalid IK input");
        float distance=target.length();
        Vector3f axis=distance>1e-6f?new Vector3f(target).div(distance):new Vector3f(0,1,0);
        float d=Math.max(Math.abs(upperLength-lowerLength)+1e-5f,
                Math.min(upperLength+lowerLength-1e-5f,distance));
        Vector3f side=new Vector3f(pole).sub(new Vector3f(axis).mul(pole.dot(axis)));
        if(side.lengthSquared()<1e-8f) {
            side.set(Math.abs(axis.x)<.8f?1:0,Math.abs(axis.x)<.8f?0:1,0);
            side.sub(new Vector3f(axis).mul(side.dot(axis)));
        }
        side.normalize();
        float along=(upperLength*upperLength-lowerLength*lowerLength+d*d)/(2*d);
        float height=(float)Math.sqrt(Math.max(0,upperLength*upperLength-along*along));
        Vector3f elbow=new Vector3f(axis).mul(along).add(side.mul(height));
        Vector3f endpoint=new Vector3f(axis).mul(d);
        Quaternionf upper=new Quaternionf().rotationTo(new Vector3f(0,1,0),new Vector3f(elbow).normalize());
        Vector3f lowerDir=new Vector3f(endpoint).sub(elbow).normalize();
        new Quaternionf(upper).conjugate().transform(lowerDir);
        Quaternionf lower=new Quaternionf().rotationTo(new Vector3f(0,1,0),lowerDir);
        return new Solution(upper,lower,endpoint);
    }
    private TwoBoneIk() {}
}
