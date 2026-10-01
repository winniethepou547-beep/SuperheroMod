package com.FIRNI.superheromod.core.cinematic;

import com.google.gson.*;
import org.joml.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded, animation-independent skinned glTF mesh. No Minecraft client classes. */
public final class GltfSkinnedModel {
    private static final List<String> JOINT_NAMES=List.of("head","chest","hips","right_upper_arm",
            "right_lower_arm","left_upper_arm","left_lower_arm","right_upper_leg",
            "right_lower_leg","left_upper_leg","left_lower_leg");
    private record Bone(int parent,int poseJoint,Vector3f translation,Quaternionf rotation,Vector3f scale) {}
    private record Channel(int node,String path,float[] times,float[] values,int width,boolean step) {}
    private record Clip(float seconds,List<Channel> channels) {}
    private final Map<String,Clip> clips;
    private final Bone[] bones;
    private final int[] joints,indices;
    private final Matrix4f[] inverseBind;
    private final float[] positions,uv,weights;
    private final float[] normals;
    private final int[] influences;
    private GltfSkinnedModel(Bone[] bones,int[] joints,Matrix4f[] inverseBind,float[] positions,
                             float[] uv,float[] weights,int[] influences,int[] indices,Map<String,Clip> clips) {
        this.bones=bones;this.joints=joints;this.inverseBind=inverseBind;
        this.positions=positions;this.uv=uv;this.weights=weights;this.influences=influences;this.indices=indices;
        this.clips=Map.copyOf(clips);
        normals=new float[positions.length];
        Vector3f a=new Vector3f(),b=new Vector3f(),n=new Vector3f();
        for(int i=0;i<indices.length;i+=3) {
            int x=indices[i]*3,y=indices[i+1]*3,z=indices[i+2]*3;
            a.set(positions[y]-positions[x],positions[y+1]-positions[x+1],positions[y+2]-positions[x+2]);
            b.set(positions[z]-positions[x],positions[z+1]-positions[x+1],positions[z+2]-positions[x+2]);a.cross(b,n);
            for(int k=0;k<3;k++){int p=indices[i+k]*3;normals[p]+=n.x;normals[p+1]+=n.y;normals[p+2]+=n.z;}
        }
        for(int i=0;i<normals.length;i+=3){n.set(normals[i],normals[i+1],normals[i+2]);if(n.lengthSquared()>1e-12f)n.normalize();else n.set(0,1,0);normals[i]=n.x;normals[i+1]=n.y;normals[i+2]=n.z;}
    }
    public int vertexCount() {return positions.length/3;}
    public int triangleCount() {return indices.length/3;}
    public int index(int index) {return indices[index];}
    public float u(int vertex) {return uv[vertex*2];}
    public float v(int vertex) {return uv[vertex*2+1];}
    public Sample newSample() {return new Sample();}
    public boolean hasClip(String name) {return clips.containsKey(name);}
    public Set<String> clipNames() {return clips.keySet();}

    /** Per-actor scratch storage: no per-vertex allocations during animation. */
    public final class Sample {
        private final Matrix4f[] globals=new Matrix4f[bones.length], palette=new Matrix4f[joints.length];
        private final boolean[] visited=new boolean[bones.length];
        private final Vector3f[] deformed=new Vector3f[vertexCount()];
        private final Vector3f[] deformedNormals=new Vector3f[vertexCount()];
        private final Matrix3f[] normalPalette=new Matrix3f[joints.length];
        private final Vector3f bind=new Vector3f(),temp=new Vector3f();
        private final Quaternionf rotation=new Quaternionf();
        private final Quaternionf qa=new Quaternionf(),qb=new Quaternionf();
        private final Vector3f[] translations=new Vector3f[bones.length],scales=new Vector3f[bones.length];
        private final Quaternionf[] rotations=new Quaternionf[bones.length];
        private boolean mappedPosePrepared;
        private final Vector3f euler=new Vector3f();
        private Sample() {
            for(int i=0;i<globals.length;i++) {globals[i]=new Matrix4f();translations[i]=new Vector3f();scales[i]=new Vector3f();rotations[i]=new Quaternionf();}
            for(int i=0;i<palette.length;i++){palette[i]=new Matrix4f();normalPalette[i]=new Matrix3f();}
            for(int i=0;i<deformed.length;i++){deformed[i]=new Vector3f();deformedNormals[i]=new Vector3f();}
        }
        public Vector3f vertex(int i) {return deformed[i];}
        public Vector3f normal(int i) {return deformedNormals[i];}
        public void apply(ActorPose pose) {
            apply(pose,null,0,0,false);
        }
        public void apply(ActorPose pose,String clipName,float seconds,float weight,boolean loop) {
            apply(pose,clipName,seconds,weight,loop,false);
        }
        public void apply(ActorPose pose,String clipName,float seconds,float weight,boolean loop,boolean prepared) {
            mappedPosePrepared=prepared;
            require(Float.isFinite(seconds)&&Float.isFinite(weight)&&weight>=0&&weight<=1,"Invalid clip time/weight");
            for(int i=0;i<bones.length;i++) {
                translations[i].set(bones[i].translation);scales[i].set(bones[i].scale);rotations[i].set(bones[i].rotation);
            }
            if(clipName!=null) {
                Clip clip=clips.get(clipName);require(clip!=null,"Missing rig clip: "+clipName);
                float time=java.lang.Math.max(0,seconds);time=loop?time%clip.seconds:java.lang.Math.min(time,clip.seconds);
                for(Channel channel:clip.channels)animate(channel,time,weight);
            }
            Arrays.fill(visited,false);
            for(int i=0;i<bones.length;i++)global(i,pose);
            for(int i=0;i<joints.length;i++){palette[i].set(globals[joints[i]]).mul(inverseBind[i]);normalPalette[i].set(palette[i]).invert().transpose();}
            for(int i=0;i<deformed.length;i++) {
                bind.set(positions[i*3],positions[i*3+1],positions[i*3+2]);deformed[i].zero();
                for(int k=0;k<4;k++) {
                    float w=weights[i*4+k];if(w==0)continue;
                    palette[influences[i*4+k]].transformPosition(bind,temp);
                    deformed[i].fma(w,temp);
                }
                bind.set(normals[i*3],normals[i*3+1],normals[i*3+2]);deformedNormals[i].zero();
                for(int k=0;k<4;k++){float w=weights[i*4+k];if(w==0)continue;normalPalette[influences[i*4+k]].transform(bind,temp);deformedNormals[i].fma(w,temp);}
                if(deformedNormals[i].lengthSquared()>1e-12f)deformedNormals[i].normalize();else deformedNormals[i].set(0,1,0);
            }
        }
        /** Blend authored named joints into ActorPose BEFORE the existing hand-contact solver. */
        public void preparePose(ActorPose pose,String name,float seconds,float weight,boolean loop) {
            require(Float.isFinite(seconds)&&Float.isFinite(weight)&&weight>=0&&weight<=1,"Invalid pose sample");
            Clip clip=clips.get(name);require(clip!=null,"Missing rig clip: "+name);
            for(int i=0;i<bones.length;i++){rotations[i].set(bones[i].rotation);translations[i].set(bones[i].translation);scales[i].set(bones[i].scale);}
            float time=java.lang.Math.max(0,seconds);time=loop?time%clip.seconds:java.lang.Math.min(time,clip.seconds);
            for(Channel c:clip.channels)animate(c,time,1);
            for(Channel c:clip.channels)if(c.path.equals("rotation")&&bones[c.node].poseJoint>=0) {
                float[] r=pose.rot[bones[c.node].poseJoint];
                qa.set(bones[c.node].rotation).invert().mul(rotations[c.node]).normalize();
                qb.rotationZYX(r[2],r[1],r[0]).slerp(qa,weight).normalize();
                JointRotations.toEuler(qb,euler);
                r[0]=euler.x;r[1]=euler.y;r[2]=euler.z;
            }
        }
        private void animate(Channel c,float time,float weight) {
            int low=0,high=c.times.length-1;
            while(low<high) {int mid=(low+high+1)>>>1;if(c.times[mid]<=time)low=mid;else high=mid-1;}
            int next=java.lang.Math.min(low+1,c.times.length-1);
            float t=c.step||next==low?0:java.lang.Math.max(0,java.lang.Math.min(1,(time-c.times[low])/(c.times[next]-c.times[low])));
            int a=low*c.width,b=next*c.width;
            if(c.path.equals("rotation")) {
                qa.set(c.values[a],c.values[a+1],c.values[a+2],c.values[a+3]);
                qb.set(c.values[b],c.values[b+1],c.values[b+2],c.values[b+3]);
                qa.slerp(qb,t).normalize();rotations[c.node].slerp(qa,weight).normalize();
            } else {
                temp.set(c.values[a]+(c.values[b]-c.values[a])*t,c.values[a+1]+(c.values[b+1]-c.values[a+1])*t,c.values[a+2]+(c.values[b+2]-c.values[a+2])*t);
                (c.path.equals("translation")?translations[c.node]:scales[c.node]).lerp(temp,weight);
            }
        }
        private void global(int i,ActorPose pose) {
            if(visited[i])return;Bone bone=bones[i];
            if(bone.parent>=0) {global(bone.parent,pose);globals[i].set(globals[bone.parent]);}
            else globals[i].identity();
            rotation.set(mappedPosePrepared&&bone.poseJoint>=0?bone.rotation:rotations[i]);
            if(bone.poseJoint>=0) {
                float[] r=pose.rot[bone.poseJoint];rotation.rotateZYX(r[2],r[1],r[0]).normalize();
            }
            globals[i].translate(translations[i].x,translations[i].y+(bone.poseJoint==ActorPose.HIPS?pose.crouch:0),translations[i].z)
                    .rotate(rotation).scale(scales[i]);
            visited[i]=true;
        }
    }

    public static GltfSkinnedModel bundled(String name) {
        if(!name.matches("[a-z0-9_]+"))throw new IllegalArgumentException("Invalid model name");
        String path="/assets/superheromod/cinematics/gltf/"+name+".gltf";
        try(var stream=GltfSkinnedModel.class.getResourceAsStream(path)) {
            if(stream==null)throw new IllegalArgumentException("Missing skinned model: "+path);
            return read(new InputStreamReader(stream,StandardCharsets.UTF_8));
        } catch(IOException e) {throw new IllegalArgumentException("Cannot read model: "+path,e);}
    }

    public static GltfSkinnedModel read(Reader reader) {
        JsonObject json=JsonParser.parseReader(reader).getAsJsonObject();
        require(json.getAsJsonObject("asset").get("version").getAsString().equals("2.0"),"glTF 2.0 required");
        require(json.has("extras")&&json.getAsJsonObject("extras").has("puppetProfile")
                &&json.getAsJsonObject("extras").get("puppetProfile").getAsString().equals("model-space-v1"),"Explicit model-space-v1 profile required");
        require(!json.has("extensionsRequired")||json.getAsJsonArray("extensionsRequired").isEmpty(),"Required extensions unsupported");
        require(!json.has("materials"),"Material import unsupported: appearance is bound by the cinematic role");
        Data data=new Data(json);
        JsonArray nodes=json.getAsJsonArray("nodes"),skins=json.getAsJsonArray("skins"),meshes=json.getAsJsonArray("meshes");
        require(nodes.size()>0&&nodes.size()<=128&&skins.size()==1&&meshes.size()==1,"Node/skin/mesh budget");
        int[] parents=new int[nodes.size()];Arrays.fill(parents,-1);
        Set<String> names=new HashSet<>();Bone[] bones=new Bone[nodes.size()];int meshNodes=0;
        for(int i=0;i<nodes.size();i++) {
            JsonObject node=nodes.get(i).getAsJsonObject();
            if(node.has("children"))for(JsonElement value:node.getAsJsonArray("children")) {
                int child=value.getAsInt();require(child>=0&&child<nodes.size()&&child!=i&&parents[child]==-1,"Invalid/multiple parent");parents[child]=i;
            }
        }
        for(int i=0;i<nodes.size();i++) {
            int p=i;for(int depth=0;p>=0;depth++,p=parents[p])require(depth<nodes.size(),"Skeleton cycle");
            JsonObject node=nodes.get(i).getAsJsonObject();require(!node.has("matrix"),"Use TRS rest transforms");
            String name=node.has("name")?node.get("name").getAsString():"node_"+i;
            require(names.add(name),"Duplicate bone name");
            float[] t=vector(node,"translation",new float[]{0,0,0}),q=vector(node,"rotation",new float[]{0,0,0,1}),s=vector(node,"scale",new float[]{1,1,1});
            Quaternionf rotation=new Quaternionf(q[0],q[1],q[2],q[3]);
            require(java.lang.Math.abs(rotation.lengthSquared()-1)<.01f&&s[0]>0&&s[1]>0&&s[2]>0,"Invalid rest rotation/scale");
            if(node.has("mesh")) {
                meshNodes++;require(node.get("mesh").getAsInt()==0&&node.has("skin")&&node.get("skin").getAsInt()==0,"Mesh skin binding");
                require(parents[i]==-1&&Arrays.equals(t,new float[]{0,0,0})&&Arrays.equals(q,new float[]{0,0,0,1})&&Arrays.equals(s,new float[]{1,1,1}),"Skinned mesh node must have identity transform");
            }
            bones[i]=new Bone(parents[i],JOINT_NAMES.indexOf(name),new Vector3f(t),rotation.normalize(),new Vector3f(s));
        }
        require(meshNodes==1,"Exactly one skinned mesh instance required");
        JsonObject skin=skins.get(0).getAsJsonObject();JsonArray jointList=skin.getAsJsonArray("joints");
        require(jointList.size()>0&&jointList.size()<=128,"Joint budget");
        int[] joints=new int[jointList.size()];Set<Integer> seen=new HashSet<>();
        for(int i=0;i<joints.length;i++) {joints[i]=jointList.get(i).getAsInt();require(joints[i]>=0&&joints[i]<bones.length&&seen.add(joints[i]),"Invalid skin joint");}
        float[] matrices=data.floats(skin.get("inverseBindMatrices").getAsInt(),"MAT4",16);
        require(matrices.length==joints.length*16,"Inverse bind count");Matrix4f[] inverse=new Matrix4f[joints.length];
        for(int i=0;i<inverse.length;i++) {inverse[i]=new Matrix4f().set(Arrays.copyOfRange(matrices,i*16,i*16+16));require(java.lang.Math.abs(inverse[i].determinant())>1e-8f,"Singular inverse bind");}
        JsonArray primitives=meshes.get(0).getAsJsonObject().getAsJsonArray("primitives");require(primitives.size()==1,"One primitive required");
        JsonObject primitive=primitives.get(0).getAsJsonObject();require(!primitive.has("targets")&&!primitive.has("material")&&(!primitive.has("mode")||primitive.get("mode").getAsInt()==4),"Only unmaterialed triangles supported");
        JsonObject a=primitive.getAsJsonObject("attributes");
        require(!a.has("JOINTS_1")&&!a.has("WEIGHTS_1"),"At most four influences");
        float[] positions=data.floats(a.get("POSITION").getAsInt(),"VEC3",3),uv=data.floats(a.get("TEXCOORD_0").getAsInt(),"VEC2",2),weights=data.floats(a.get("WEIGHTS_0").getAsInt(),"VEC4",4);
        int[] influences=data.ints(a.get("JOINTS_0").getAsInt(),"VEC4",4),indices=data.ints(primitive.get("indices").getAsInt(),"SCALAR",1);
        int count=positions.length/3;require(count<=16384&&uv.length==count*2&&weights.length==count*4&&influences.length==count*4&&indices.length%3==0,"Attribute cardinality");
        for(int index:indices)require(index>=0&&index<count,"Triangle index outside mesh");
        for(int i=0;i<count;i++) {
            float total=0;for(int k=0;k<4;k++) {int p=i*4+k;require(influences[p]>=0&&influences[p]<joints.length&&weights[p]>=0,"Invalid influence");total+=weights[p];}
            require(java.lang.Math.abs(total-1)<.01f,"Weights must sum to one");for(int k=0;k<4;k++)weights[i*4+k]/=total;
        }
        Map<String,Clip> clips=new HashMap<>();
        if(json.has("animations")) {
            require(json.getAsJsonArray("animations").size()<=32,"Clip budget");
            for(JsonElement element:json.getAsJsonArray("animations")) {
                JsonObject animation=element.getAsJsonObject();String name=animation.get("name").getAsString();
                require(!name.isBlank()&&!clips.containsKey(name),"Missing/duplicate clip name");
                List<Channel> channels=new ArrayList<>();Set<String> targets=new HashSet<>();float duration=0;
                for(JsonElement entry:animation.getAsJsonArray("channels")) {
                    require(channels.size()<384,"Channel budget");JsonObject c=entry.getAsJsonObject(),target=c.getAsJsonObject("target");
                    int node=target.get("node").getAsInt();String path=target.get("path").getAsString();
                    require(node>=0&&node<bones.length&&!nodes.get(node).getAsJsonObject().has("mesh")&&targets.add(node+":"+path),"Invalid/duplicate animation target");
                    require(path.equals("rotation")||path.equals("translation")||path.equals("scale"),"Unsupported animation path");
                    JsonObject sampler=animation.getAsJsonArray("samplers").get(c.get("sampler").getAsInt()).getAsJsonObject();
                    String mode=sampler.has("interpolation")?sampler.get("interpolation").getAsString():"LINEAR";
                    require(mode.equals("LINEAR")||mode.equals("STEP"),"Export LINEAR/STEP animation");
                    int width=path.equals("rotation")?4:3;
                    float[] times=data.floats(sampler.get("input").getAsInt(),"SCALAR",1),values=data.floats(sampler.get("output").getAsInt(),width==4?"VEC4":"VEC3",width);
                    require(times.length<=4096&&values.length==times.length*width,"Animation key budget/count");
                    for(int i=0;i<times.length;i++) {
                        require(times[i]>=0&&times[i]<=600&&(i==0||times[i]>times[i-1]),"Invalid animation time");
                        if(width==4) {
                            float length=0;for(int k=0;k<4;k++)length+=values[i*4+k]*values[i*4+k];
                            require(java.lang.Math.abs(length-1)<.01f,"Unit rotation required");
                            float divisor=(float)java.lang.Math.sqrt(length);for(int k=0;k<4;k++)values[i*4+k]/=divisor;
                        } else if(path.equals("scale"))for(int k=0;k<3;k++)require(values[i*3+k]>0,"Positive animated scale required");
                    }
                    duration=java.lang.Math.max(duration,times[times.length-1]);channels.add(new Channel(node,path,times,values,width,mode.equals("STEP")));
                }
                require(duration>0&&!channels.isEmpty(),"Empty rig animation");clips.put(name,new Clip(duration,List.copyOf(channels)));
            }
        }
        return new GltfSkinnedModel(bones,joints,inverse,positions,uv,weights,influences,indices,clips);
    }
    private static float[] vector(JsonObject node,String key,float[] fallback) {
        if(!node.has(key))return fallback;JsonArray values=node.getAsJsonArray(key);require(values.size()==fallback.length,"TRS vector size");
        float[] out=new float[fallback.length];for(int i=0;i<out.length;i++){out[i]=values.get(i).getAsFloat();require(Float.isFinite(out[i]),"Nonfinite transform");}return out;
    }
    private static void require(boolean test,String reason) {if(!test)throw new IllegalArgumentException("glTF mesh: "+reason);}
    private static final class Data {
        final JsonObject json;final ByteBuffer bytes;
        Data(JsonObject json) {
            this.json=json;JsonArray buffers=json.getAsJsonArray("buffers");require(buffers.size()==1,"Single embedded buffer required");
            JsonObject b=buffers.get(0).getAsJsonObject();String uri=b.get("uri").getAsString(),prefix="data:application/octet-stream;base64,";
            require(uri.startsWith(prefix)&&uri.length()<16_000_000,"Embedded buffer budget");byte[] raw=Base64.getDecoder().decode(uri.substring(prefix.length()));
            require(raw.length==b.get("byteLength").getAsInt(),"Buffer length");bytes=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        }
        float[] floats(int index,String type,int components) {return values(index,type,components,true);}
        int[] ints(int index,String type,int components) {float[] values=values(index,type,components,false);int[] result=new int[values.length];for(int i=0;i<result.length;i++)result[i]=(int)values[i];return result;}
        float[] values(int index,String type,int components,boolean floating) {
            JsonObject a=json.getAsJsonArray("accessors").get(index).getAsJsonObject();int component=a.get("componentType").getAsInt(),count=a.get("count").getAsInt();
            require(!a.has("sparse")&&(!a.has("normalized")||!a.get("normalized").getAsBoolean())&&a.get("type").getAsString().equals(type),"Unsupported accessor");
            require(floating?component==5126:component==5121||component==5123,"Use FLOAT attributes and unsigned byte/short indices");
            require(count>0&&count<=98304,"Accessor budget");int size=component==5121?1:component==5123?2:4;
            JsonObject view=json.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();require(view.get("buffer").getAsInt()==0,"Buffer index");
            int start=view.has("byteOffset")?view.get("byteOffset").getAsInt():0,offset=a.has("byteOffset")?a.get("byteOffset").getAsInt():0;
            int stride=view.has("byteStride")?view.get("byteStride").getAsInt():components*size,length=view.get("byteLength").getAsInt();
            require(start>=0&&offset>=0&&stride>=components*size&&stride%size==0&&start%size==0&&offset%size==0
                    &&(long)offset+(long)(count-1)*stride+components*size<=length&&(long)start+length<=bytes.capacity(),"Accessor bounds/alignment");
            float[] out=new float[count*components];for(int i=0;i<count;i++)for(int c=0;c<components;c++) {
                int p=start+offset+i*stride+c*size;float v=floating?bytes.getFloat(p):size==1?Byte.toUnsignedInt(bytes.get(p)):Short.toUnsignedInt(bytes.getShort(p));
                require(Float.isFinite(v),"Nonfinite vertex");out[i*components+c]=v;
            }return out;
        }
    }
}
