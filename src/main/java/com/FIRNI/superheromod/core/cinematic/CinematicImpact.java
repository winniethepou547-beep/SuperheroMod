package com.FIRNI.superheromod.core.cinematic;

/** Authored hit response shared by camera and foreground. All timing is in scene ticks. */
public record CinematicImpact(int tick,int duration,float strength,float frequency,int direction) {
    public CinematicImpact {
        if(tick<0||duration<2||duration>60||!Float.isFinite(strength)||strength<0||strength>4
                ||!Float.isFinite(frequency)||frequency<1||frequency>20||Math.abs(direction)!=1)
            throw new IllegalArgumentException("Invalid impact cue");
    }
    public float envelope(float time) {
        float age=time-tick;
        if(age<=0||age>=duration)return 0;
        float attack=Math.min(1,age/.6f),decay=1-age/duration;
        return attack*decay*decay;
    }
    public float oscillation(float time) {
        return (float)Math.sin((time-tick)/20.0*frequency*Math.PI*2)*envelope(time)*strength;
    }
    public float flash(float time) {
        float age=time-tick;
        return age>=0&&age<1.5f?(1-age/1.5f)*Math.min(.14f,strength*.04f):0;
    }
}
