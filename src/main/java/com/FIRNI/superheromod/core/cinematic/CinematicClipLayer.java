package com.FIRNI.superheromod.core.cinematic;

/** Seekable action placement. Local clip time, blend weights and loops have no playback history. */
public record CinematicClipLayer(PoseClip clip, float start, float end, float speed,
                                 float blendIn, float blendOut, boolean loop, boolean additive) {
    public CinematicClipLayer {
        if(clip==null||!Float.isFinite(start+end+speed+blendIn+blendOut)||start<0||end<=start||speed<=0
                ||blendIn<0||blendOut<0||blendIn+blendOut>end-start)
            throw new IllegalArgumentException("Invalid animation layer");
    }
    public void apply(float tick, ActorPose pose) {
        if(tick<start||tick>end)return;
        float weight=1;
        if(blendIn>0)weight=Math.min(weight,(tick-start)/blendIn);
        if(blendOut>0)weight=Math.min(weight,(end-tick)/blendOut);
        weight=weight*weight*(3-2*weight);
        float local=(tick-start)*speed;
        if(loop)local%=clip.durationTicks();
        clip.apply(local,weight,additive,pose);
    }
}
