package com.FIRNI.superheromod.core.cinematic;

/** Small continuous secondary motion, seeded per actor rather than per frame. */
public final class CrowdMotionClip implements PoseClip {
    private final int seed;
    public CrowdMotionClip(int seed) { this.seed=seed; }
    public float durationTicks() { return 400; }
    public void apply(float tick,float weight,boolean additive,ActorPose pose) {
        float phase=seed*2.39996f;
        float sway=(float)Math.sin(tick*(.12f+(seed%3)*.013f)+phase);
        float breath=(float)Math.sin(tick*.18f+phase*.7f);
        pose.rot[ActorPose.CHEST][2]+=sway*.035f*weight;
        pose.rot[ActorPose.CHEST][1]+=(float)Math.sin(tick*.095f+phase)*.045f*weight;
        pose.rot[ActorPose.HEAD][1]-=sway*.055f*weight;
        pose.rot[ActorPose.RIGHT_LOWER_ARM][0]+=breath*.055f*weight;
        pose.rot[ActorPose.LEFT_LOWER_ARM][0]-=breath*.045f*weight;
        pose.crouch+=(.5f+.5f*breath)*.018f*weight;
    }
}
