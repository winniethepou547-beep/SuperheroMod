package com.FIRNI.superheromod.core.cinematic;

/** Places an embedded skeleton clip on the shared scene timeline. Times are ticks. */
public record RigClipPlacement(String name,float start,float end,float speed,float fade,boolean loop) {
    public RigClipPlacement {
        if(name==null||name.isBlank()||!Float.isFinite(start+end+speed+fade)||start<0||end<=start||speed<=0||fade<0||fade*2>end-start)
            throw new IllegalArgumentException("Invalid rig clip placement");
    }
    public float weight(float tick) {
        if(tick<start||tick>end)return 0;
        if(fade==0)return 1;
        float t=Math.min(1,Math.min((tick-start)/fade,(end-tick)/fade));return t*t*(3-2*t);
    }
    public float seconds(float tick) {return Math.max(0,tick-start)*speed/20;}
}
