package com.FIRNI.superheromod.core.cinematic;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored joint channels, sampled continuously. Independent timings preserve overlapping action. */
public final class CinematicAnimationClip implements PoseClip {
    @Override public float durationTicks() { return duration; }
    private static final Map<String,Integer> JOINTS = Map.ofEntries(
            Map.entry("head",0),Map.entry("chest",1),Map.entry("hips",2),
            Map.entry("right_upper_arm",3),Map.entry("right_lower_arm",4),
            Map.entry("left_upper_arm",5),Map.entry("left_lower_arm",6),
            Map.entry("right_upper_leg",7),Map.entry("right_lower_leg",8),
            Map.entry("left_upper_leg",9),Map.entry("left_lower_leg",10));
    public final String id;
    public final float duration;
    private final List<Channel> channels;
    private final Map<String,Float> markers;

    private CinematicAnimationClip(String id, float duration, List<Channel> channels, Map<String,Float> markers) {
        this.id=id;this.duration=duration;this.channels=List.copyOf(channels);this.markers=Map.copyOf(markers);
    }

    public static CinematicAnimationClip bundled(String name) {
        if(!name.matches("[a-z0-9_]+"))throw new IllegalArgumentException("Invalid clip name");
        String path="/assets/superheromod/cinematics/clips/"+name+".json";
        try(var stream=CinematicAnimationClip.class.getResourceAsStream(path)) {
            if(stream==null)throw new IllegalArgumentException("Missing animation clip: "+path);
            return read(name,new InputStreamReader(stream,StandardCharsets.UTF_8));
        } catch(IOException e) {throw new IllegalArgumentException("Cannot read clip: "+path,e);}
    }

    public static CinematicAnimationClip read(String id, Reader reader) {
        JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
        if(json.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unknown clip schema: "+id);
        float duration=number(json.get("duration"));
        if(duration<=0||duration>12000)throw new IllegalArgumentException("Invalid clip duration: "+id);
        var markers=new HashMap<String,Float>();
        if(json.has("markers"))for(var entry:json.getAsJsonObject("markers").entrySet()) {
            float tick=number(entry.getValue());
            if(tick<0||tick>duration)throw new IllegalArgumentException("Marker outside clip");
            markers.put(entry.getKey(),tick);
        }
        var channels=new ArrayList<Channel>();
        for(var entry:json.getAsJsonObject("channels").entrySet()) {
            String[] path=entry.getKey().split("\\.");
            if(path.length!=2)throw new IllegalArgumentException("Invalid channel: "+entry.getKey());
            int joint=path[0].equals("body")?-1:JOINTS.getOrDefault(path[0],-2);
            int axis=joint==-1?switch(path[1]){case "crouch"->0;case "roll"->1;case "pitch"->2;default->-1;}
                    :switch(path[1]){case "x"->0;case "y"->1;case "z"->2;default->-1;};
            if(joint==-2||axis<0)throw new IllegalArgumentException("Unknown channel: "+entry.getKey());
            var keys=entry.getValue().getAsJsonArray();
            if(keys.size()<2||keys.size()>1024)throw new IllegalArgumentException("2..1024 keys required");
            float[] times=new float[keys.size()],values=new float[keys.size()];
            for(int i=0;i<keys.size();i++) {
                var key=keys.get(i).getAsJsonArray();
                if(key.size()!=2)throw new IllegalArgumentException("Keys are [tick,value]");
                times[i]=number(key.get(0));values[i]=number(key.get(1));
                if(times[i]<0||times[i]>duration||(i>0&&times[i]<=times[i-1]))
                    throw new IllegalArgumentException("Unordered channel: "+entry.getKey());
                if(joint>=0)values[i]=(float)Math.toRadians(values[i]);
            }
            channels.add(new Channel(joint,axis,times,values));
        }
        if(channels.isEmpty()||channels.size()>36)throw new IllegalArgumentException("Invalid channel count");
        return new CinematicAnimationClip(id,duration,channels,markers);
    }

    public float marker(String name) {
        Float value=markers.get(name);
        if(value==null)throw new IllegalArgumentException("Missing marker "+name+" in "+id);
        return value;
    }

    /** Only authored channels are touched, allowing upper-body actions over locomotion. */
    public void apply(float tick, float weight, boolean additive, ActorPose pose) {
        if(!Float.isFinite(tick)||!Float.isFinite(weight))throw new IllegalArgumentException("Invalid clip sample");
        weight=Math.max(0,Math.min(1,weight));
        for(Channel c:channels) {
            float value=c.sample(tick);
            float base=c.joint>=0?pose.rot[c.joint][c.axis]:switch(c.axis){case 0->pose.crouch;case 1->pose.bodyRoll;default->pose.bodyPitch;};
            float result=additive?base+value*weight:base+(value-base)*weight;
            if(c.joint>=0)pose.rot[c.joint][c.axis]=result;
            else switch(c.axis){case 0->pose.crouch=result;case 1->pose.bodyRoll=result;case 2->pose.bodyPitch=result;}
        }
    }

    private static float number(JsonElement value) {
        float f=value.getAsFloat();
        if(!Float.isFinite(f))throw new IllegalArgumentException("Nonfinite animation value");
        return f;
    }

    /** Monotone cubic Hermite: continuous velocity, no overshoot or stop at every intermediate key. */
    private static final class Channel {
        final int joint,axis;
        final float[] time,value,slope;
        Channel(int joint,int axis,float[] time,float[] value) {
            this.joint=joint;this.axis=axis;this.time=time;this.value=value;
            slope=new float[time.length];
            float[] delta=new float[time.length-1];
            for(int i=0;i<delta.length;i++)delta[i]=(value[i+1]-value[i])/(time[i+1]-time[i]);
            slope[0]=delta[0];slope[slope.length-1]=delta[delta.length-1];
            for(int i=1;i<slope.length-1;i++) {
                float a=delta[i-1],b=delta[i];
                if(a*b>0) {
                    float h0=time[i]-time[i-1],h1=time[i+1]-time[i];
                    float w0=2*h1+h0,w1=h1+2*h0;
                    slope[i]=(w0+w1)/(w0/a+w1/b);
                }
            }
            for(int i=0;i<delta.length;i++) {
                if(delta[i]==0){slope[i]=slope[i+1]=0;continue;}
                float a=slope[i]/delta[i],b=slope[i+1]/delta[i],length=a*a+b*b;
                if(length>9) {float scale=3/(float)Math.sqrt(length);slope[i]=scale*a*delta[i];slope[i+1]=scale*b*delta[i];}
            }
            if(value[0]==value[value.length-1])slope[0]=slope[slope.length-1]=0;
        }
        float sample(float tick) {
            if(tick<=time[0])return value[0];
            int last=time.length-1;
            if(tick>=time[last])return value[last];
            int found=Arrays.binarySearch(time,tick);
            if(found>=0)return value[found];
            int i=-found-2;
            float h=time[i+1]-time[i],t=(tick-time[i])/h,t2=t*t,t3=t2*t;
            return (2*t3-3*t2+1)*value[i]+(t3-2*t2+t)*h*slope[i]
                    +(-2*t3+3*t2)*value[i+1]+(t3-t2)*h*slope[i+1];
        }
    }
}
