package com.FIRNI.superheromod.core.cinematic;

import com.google.gson.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Strict animation-only glTF adapter for the named, identity-rest puppet rig.
 * Meshes/materials and arbitrary skeleton retargeting are deliberately not imported here.
 */
public final class GltfPoseClip implements PoseClip {
    private static final List<String> NAMES=List.of("head","chest","hips","right_upper_arm",
            "right_lower_arm","left_upper_arm","left_lower_arm","right_upper_leg",
            "right_lower_leg","left_upper_leg","left_lower_leg");
    private record Channel(int joint,float[] times,Quaternionf[] values,boolean step) {}
    private final List<Channel> channels;
    private final float duration;
    private record Scratch(Quaternionf sample,Quaternionf base,Quaternionf delta,Vector3f angles) {}
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(()->
            new Scratch(new Quaternionf(),new Quaternionf(),new Quaternionf(),new Vector3f()));
    private GltfPoseClip(List<Channel> channels,float duration) { this.channels=List.copyOf(channels);this.duration=duration; }
    @Override public float durationTicks() { return duration; }

    public static GltfPoseClip bundled(String name) {
        if(!name.matches("[a-z0-9_]+"))throw new IllegalArgumentException("Invalid glTF name");
        String path="/assets/superheromod/cinematics/gltf/"+name+".gltf";
        try(var stream=GltfPoseClip.class.getResourceAsStream(path)) {
            if(stream==null)throw new IllegalArgumentException("Missing glTF: "+path);
            return read(new InputStreamReader(stream,StandardCharsets.UTF_8));
        } catch(IOException e) {throw new IllegalArgumentException("Cannot read "+path,e);}
    }

    public static GltfPoseClip read(Reader reader) {
        JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
        if(!json.getAsJsonObject("asset").get("version").getAsString().equals("2.0"))fail("glTF 2.0 required");
        if(!json.has("extras")||!json.getAsJsonObject("extras").has("puppetProfile")
                ||!json.getAsJsonObject("extras").get("puppetProfile").getAsString().equals("identity-rest-v1"))
            fail("Explicit identity-rest-v1 puppet profile required; arbitrary Blender rigs need retargeting");
        if(json.has("extensionsRequired")&&!json.getAsJsonArray("extensionsRequired").isEmpty())fail("Required glTF extensions unsupported");
        if(json.has("meshes")||json.has("skins"))fail("This adapter imports animations only, not meshes/skins");
        JsonArray buffers=json.getAsJsonArray("buffers");
        if(buffers.size()!=1)fail("One embedded buffer required");
        JsonObject buffer=buffers.get(0).getAsJsonObject();
        String uri=buffer.get("uri").getAsString(),prefix="data:application/octet-stream;base64,";
        if(!uri.startsWith(prefix)||uri.length()>8_000_000)fail("Expected bounded embedded base64 buffer");
        byte[] bytes=Base64.getDecoder().decode(uri.substring(prefix.length()));
        if(buffer.get("byteLength").getAsInt()!=bytes.length)fail("Buffer length mismatch");
        JsonArray nodes=json.getAsJsonArray("nodes"),animations=json.getAsJsonArray("animations");
        if(nodes.size()>128||animations.size()!=1)fail("One animation and at most 128 nodes required");
        JsonObject animation=animations.get(0).getAsJsonObject();
        var result=new ArrayList<Channel>(); var seen=new HashSet<Integer>(); float duration=0;
        for(JsonElement item:animation.getAsJsonArray("channels")) {
            JsonObject c=item.getAsJsonObject(),target=c.getAsJsonObject("target");
            if(!target.get("path").getAsString().equals("rotation"))fail("Only joint rotation channels supported");
            JsonObject node=nodes.get(target.get("node").getAsInt()).getAsJsonObject();
            int joint=NAMES.indexOf(node.get("name").getAsString());
            if(joint<0||!seen.add(joint))fail("Unknown or duplicate animated joint");
            if(node.has("matrix"))fail("Matrix rest transforms unsupported");
            if(node.has("rotation")) {
                JsonArray q=node.getAsJsonArray("rotation");
                if(q.size()!=4||q.get(0).getAsFloat()!=0||q.get(1).getAsFloat()!=0||q.get(2).getAsFloat()!=0||q.get(3).getAsFloat()!=1)
                    fail("Nonidentity rest rotation needs retargeting");
            }
            JsonObject sampler=animation.getAsJsonArray("samplers").get(c.get("sampler").getAsInt()).getAsJsonObject();
            String mode=sampler.has("interpolation")?sampler.get("interpolation").getAsString():"LINEAR";
            if(!mode.equals("LINEAR")&&!mode.equals("STEP"))fail("Interpolation unsupported: "+mode+"; export sampled LINEAR");
            float[] times=accessor(json,bytes,sampler.get("input").getAsInt(),1);
            float[] values=accessor(json,bytes,sampler.get("output").getAsInt(),4);
            if(times.length*4!=values.length)fail("Key counts mismatch");
            Quaternionf[] rotations=new Quaternionf[times.length];
            for(int i=0;i<times.length;i++) {
                if(times[i]<0||times[i]>600||(i>0&&times[i]<=times[i-1]))fail("Unordered/invalid seconds");
                rotations[i]=new Quaternionf(values[i*4],values[i*4+1],values[i*4+2],values[i*4+3]);
                if(Math.abs(rotations[i].lengthSquared()-1)>.01f)fail("Unit quaternion required");
                rotations[i].normalize();
            }
            duration=Math.max(duration,times[times.length-1]*20);
            result.add(new Channel(joint,times,rotations,mode.equals("STEP")));
        }
        if(result.isEmpty()||duration<=0)fail("Empty animation");
        return new GltfPoseClip(result,duration);
    }

    private static float[] accessor(JsonObject json,byte[] bytes,int index,int components) {
        JsonObject a=json.getAsJsonArray("accessors").get(index).getAsJsonObject();
        if(a.has("sparse")||(a.has("normalized")&&a.get("normalized").getAsBoolean())||a.get("componentType").getAsInt()!=5126
                ||!a.get("type").getAsString().equals(components==1?"SCALAR":"VEC4"))fail("Only dense FLOAT accessors supported");
        int count=a.get("count").getAsInt(); if(count<1||count>4096)fail("Accessor count exceeds budget");
        JsonObject view=json.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
        if(view.get("buffer").getAsInt()!=0)fail("Invalid buffer index");
        int start=view.has("byteOffset")?view.get("byteOffset").getAsInt():0;
        int offset=a.has("byteOffset")?a.get("byteOffset").getAsInt():0;
        int stride=view.has("byteStride")?view.get("byteStride").getAsInt():components*4;
        int length=view.get("byteLength").getAsInt();
        long end=(long)offset+(long)(count-1)*stride+components*4;
        if(start<0||offset<0||length<0||stride<components*4||stride%4!=0||offset%4!=0||start%4!=0
                ||end>length||(long)start+length>bytes.length)fail("Accessor outside buffer view");
        var data=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);float[] out=new float[count*components];
        for(int i=0;i<count;i++)for(int c=0;c<components;c++) {
            float value=data.getFloat(start+offset+i*stride+c*4);
            if(!Float.isFinite(value))fail("Nonfinite accessor value");out[i*components+c]=value;
        }
        return out;
    }

    @Override public void apply(float tick,float weight,boolean additive,ActorPose pose) {
        if(!Float.isFinite(tick)||!Float.isFinite(weight))fail("Invalid sample");
        float seconds=Math.max(0,tick)/20;weight=Math.max(0,Math.min(1,weight));
        Scratch scratch=SCRATCH.get();
        Quaternionf sample=scratch.sample,base=scratch.base;Vector3f angles=scratch.angles;
        for(Channel channel:channels) {
            int low=0,high=channel.times.length-1;
            while(low<high) {int mid=(low+high+1)>>>1;if(channel.times[mid]<=seconds)low=mid;else high=mid-1;}
            int next=Math.min(low+1,channel.times.length-1);
            float t=next==low||channel.step?0:Math.max(0,Math.min(1,(seconds-channel.times[low])/(channel.times[next]-channel.times[low])));
            sample.set(channel.values[low]).slerp(channel.values[next],t);
            float[] r=pose.rot[channel.joint];base.rotationZYX(r[2],r[1],r[0]);
            if(additive)base.mul(scratch.delta.identity().slerp(sample,weight));else base.slerp(sample,weight);
            JointRotations.toEuler(base,angles);r[0]=angles.x;r[1]=angles.y;r[2]=angles.z;
        }
    }
    private static void fail(String message) {throw new IllegalArgumentException("glTF: "+message);}
}
