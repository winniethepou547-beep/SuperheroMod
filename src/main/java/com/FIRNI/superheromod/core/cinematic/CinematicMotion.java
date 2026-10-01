package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;

/** Pure, seekable root motion. Visual choreography never moves the gameplay entity. */
public final class CinematicMotion {
    private CinematicMotion() {}
    public static Vec3 target(CinematicDefinition def, StageFrame stage, Vec3 initial, float time) {
        Vec3 position=def.targetAnchor==null?initial:stage.toWorldSpan(def.targetAnchor);
        Vec3 destination=null,launchFrom=null,launchTo=null;
        float speed=0,arc=0,start=0,duration=1,cursor=0;
        for(Beat beat:def.beats) {
            if(beat.tick>time)break;
            position=advance(position,destination,speed,launchFrom,launchTo,arc,start,duration,cursor,beat.tick);
            cursor=beat.tick;
            switch(beat.action) {
                case ACTOR_MOVE_TARGET -> { destination=stage.toWorldSpan(beat.localA);speed=beat.param1;launchFrom=null; }
                case LAUNCH_TARGET -> {
                    launchFrom=stage.toWorldSpan(beat.localA);launchTo=stage.toWorldSpan(beat.localB);
                    start=beat.tick;duration=Math.max(1,beat.param2);arc=beat.param1;destination=null;
                    position=launchFrom;
                }
                case FREEZE -> { destination=null;launchFrom=null; }
                default -> {}
            }
        }
        return advance(position,destination,speed,launchFrom,launchTo,arc,start,duration,cursor,time);
    }
    private static Vec3 advance(Vec3 position,Vec3 dest,float speed,Vec3 from,Vec3 to,
                                float arc,float start,float duration,float previous,float now) {
        if(from!=null) {
            double p=Math.max(0,Math.min(1,(now-start)/duration));
            return from.lerp(to,p).add(0,Math.sin(p*Math.PI)*arc,0);
        }
        if(dest==null)return position;
        Vec3 delta=dest.subtract(position);
        return position.add(delta.normalize().scale(Math.min(delta.length(),Math.max(0,now-previous)*speed)));
    }
}
