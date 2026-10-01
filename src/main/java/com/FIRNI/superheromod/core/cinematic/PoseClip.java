package com.FIRNI.superheromod.core.cinematic;

/** A continuously sampled animation source; the player does not depend on its file format. */
public interface PoseClip {
    float durationTicks();
    void apply(float tick, float weight, boolean additive, ActorPose pose);
}
