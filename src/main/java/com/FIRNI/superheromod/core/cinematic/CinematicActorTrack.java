package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import java.util.*;

/** Immutable, seekable choreography. Extras are visual rigs, never gameplay entities. */
public final class CinematicActorTrack {
    public enum Binding { ATTACKER, TARGET, SAND }
    private record Key(int tick, Vec3 position, float yaw, float scale, ActorPose pose,
                       Easing motionEase, float arcHeight) {}
    public final String role;
    public final Binding binding;
    public final RigClipPlacement rigClip;
    public final String modelPrefix;
    public final float dissolveStart, dissolveEnd;
    private final List<Key> keys;
    private final List<CinematicClipLayer> layers;
    public static final class Sample {
        public Vec3 position = Vec3.ZERO;
        public float yaw, scale;
        public float timeline;
        public final ActorPose pose = new ActorPose();
    }
    private CinematicActorTrack(Builder b) {
        dissolveStart=b.dissolveStart; dissolveEnd=b.dissolveEnd;
        role=b.role; binding=b.binding;rigClip=b.rigClip;modelPrefix=b.modelPrefix;
        keys=List.copyOf(b.keys);
        layers=List.copyOf(b.layers);
        if(keys.isEmpty()) throw new IllegalArgumentException("Empty actor track: " + role);
    }
    public void sample(float time, Sample out) {
        if(!Float.isFinite(time)) throw new IllegalArgumentException("Nonfinite scene time");
        out.timeline=time;
        Key a=keys.get(0), b=a;
        for(Key key:keys) {
            if(key.tick<=time) { a=key; b=key; }
            else { b=key; break; }
        }
        float t=a==b?0:Mth.clamp((time-a.tick)/(b.tick-a.tick),0,1);
        float eased=b.motionEase==null?t*t*(3-2*t):b.motionEase.apply(t);
        out.position=a.position.lerp(b.position,eased).add(0,4*b.arcHeight*t*(1-t),0);
        out.yaw=a.yaw+Mth.wrapDegrees(b.yaw-a.yaw)*eased;
        out.scale=Mth.lerp(eased,a.scale,b.scale);
        ActorPose.lerp(a.pose,b.pose,t,null,out.pose);
        for(var layer:layers)layer.apply(time,out.pose);
    }
    public static Builder of(String role, Binding binding) { return new Builder(role,binding); }
    public static final class Builder {
        private final String role;
        private final Binding binding;
        private RigClipPlacement rigClip;
        private String modelPrefix="puppet";
        private float dissolveStart=Float.POSITIVE_INFINITY, dissolveEnd=Float.POSITIVE_INFINITY;
        public Builder dissolve(float start,float end) {
            if(!Float.isFinite(start)||!Float.isFinite(end)||start<0||end<=start)throw new IllegalArgumentException("Invalid dissolution");
            dissolveStart=start;dissolveEnd=end;return this;
        }
        private final List<Key> keys=new ArrayList<>();
        private final List<CinematicClipLayer> layers=new ArrayList<>();
        private Builder(String role, Binding binding) {
            if(role==null||role.isBlank()) throw new IllegalArgumentException("Missing actor role");
            this.role=role; this.binding=Objects.requireNonNull(binding);
        }
        public Builder key(int tick, Vec3 position, float yaw, float scale, ActorPose pose) {
            return key(tick,position,yaw,scale,pose,null,0);
        }
        /** Easing and arc apply to the incoming segment, independently of joint pose interpolation. */
        public Builder key(int tick, Vec3 position, float yaw, float scale, ActorPose pose,
                           Easing motionEase,float arcHeight) {
            if(tick<0 || (!keys.isEmpty()&&tick<=keys.get(keys.size()-1).tick)
                    || position==null || !Double.isFinite(position.x)||!Double.isFinite(position.y)
                    ||!Double.isFinite(position.z)||!Float.isFinite(yaw)||!Float.isFinite(scale)||scale<0
                    ||!Float.isFinite(arcHeight)||arcHeight<0)
                throw new IllegalArgumentException("Invalid or unordered key: "+role);
            Objects.requireNonNull(pose);
            for(float[] joint:pose.rot) for(float angle:joint)
                if(!Float.isFinite(angle)) throw new IllegalArgumentException("Nonfinite joint");
            if(!Float.isFinite(pose.crouch+pose.bodyPitch+pose.bodyRoll))
                throw new IllegalArgumentException("Nonfinite root pose");
            keys.add(new Key(tick,position,yaw,scale,pose.copy(),motionEase,arcHeight)); return this;
        }
        public CinematicActorTrack build() { return new CinematicActorTrack(this); }
        public Builder rigClip(RigClipPlacement clip) {rigClip=Objects.requireNonNull(clip);return this;}
        public Builder model(String prefix) {
            if(prefix==null||!prefix.matches("[a-z0-9_]+"))throw new IllegalArgumentException("Invalid rig model prefix");
            modelPrefix=prefix;return this;
        }
        public Builder layer(CinematicClipLayer layer) {
            if(layers.size()>=32)throw new IllegalArgumentException("Actor animation layer budget exceeded");
            layers.add(Objects.requireNonNull(layer));return this;
        }
    }
}
