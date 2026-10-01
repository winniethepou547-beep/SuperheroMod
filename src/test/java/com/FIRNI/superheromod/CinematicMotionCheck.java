package com.FIRNI.superheromod;

import com.FIRNI.superheromod.core.cinematic.*;
import net.minecraft.world.phys.Vec3;

public final class CinematicMotionCheck {
    public static void main(String[] args) {
        CinematicCameraCheck.run();
        CinematicReadinessCheck.run();
        var turnA=ActorPose.of().j(ActorPose.HEAD,0,170,0);
        var turnB=ActorPose.of().j(ActorPose.HEAD,0,-170,0);
        var turnOut=ActorPose.of();
        ActorPose.lerp(turnA,turnB,.5f,null,turnOut);
        var r=turnOut.rot[ActorPose.HEAD];
        var facing=new org.joml.Quaternionf().rotationZYX(r[2],r[1],r[0])
                .transform(new org.joml.Vector3f(0,0,1));
        if(facing.z>-.999f)throw new AssertionError("Joint interpolation took the long turn");
        var crowd=new CrowdMotionClip(3);
        var crowdA=ActorPose.of();crowd.apply(24,1,true,crowdA);
        var crowdB=ActorPose.of();crowd.apply(90,1,true,crowdB);
        crowdB.reset();crowd.apply(24,1,true,crowdB);
        for(int j=0;j<ActorPose.JOINTS;j++)for(int axis=0;axis<3;axis++)
            if(crowdA.rot[j][axis]!=crowdB.rot[j][axis])throw new AssertionError("Crowd motion is history dependent");
        checkSkinning();
        checkGltf();
        checkGltfMesh();
        checkAuthoredPoseBridge();
        var animationCurve = CinematicAnimationClip.read("test", new java.io.StringReader("""
                {"schema":1,"duration":10,"markers":{"contact":5},"channels":{
                    "body.pitch":[[0,0],[5,10],[10,20]],"right_lower_arm.x":[[0,0],[5,-90],[10,0]]}}
                """));
        ActorPose clipPose = ActorPose.of();
        clipPose.j(ActorPose.LEFT_UPPER_ARM,17,0,0);
        float leftAngle=clipPose.rot[ActorPose.LEFT_UPPER_ARM][0];
        animationCurve.apply(5,1,false,clipPose);
        if(Math.abs(clipPose.bodyPitch-10)>1e-5 || Math.abs(clipPose.rot[ActorPose.RIGHT_LOWER_ARM][0]+Math.PI/2)>1e-5
                ||clipPose.rot[ActorPose.LEFT_UPPER_ARM][0]!=leftAngle)throw new AssertionError("Clip mask or degree units");
        animationCurve.apply(4.99f,1,false,clipPose);float before=clipPose.bodyPitch;
        animationCurve.apply(5.01f,1,false,clipPose);float after=clipPose.bodyPitch;
        if(Math.abs((after-before)/.02f-2)>1e-3)throw new AssertionError("Cubic motion stopped at an intermediate key");
        for(float tick=0;tick<10;tick+=.01f) {
            animationCurve.apply(tick,1,false,clipPose);
            float elbow=clipPose.rot[ActorPose.RIGHT_LOWER_ARM][0];
            if(elbow>1e-5||elbow<-Math.PI/2-1e-5)throw new AssertionError("Cubic curve overshoot");
        }
        clipPose.reset();clipPose.bodyPitch=6;
        animationCurve.apply(5,.5f,true,clipPose);
        if(clipPose.bodyPitch!=11)throw new AssertionError("Additive clip blend");
        var layer=new CinematicClipLayer(animationCurve,20,40,1,2,2,true,false);
        clipPose.reset();layer.apply(25,clipPose);float loopPose=clipPose.bodyPitch;
        clipPose.reset();layer.apply(35,clipPose);
        if(clipPose.bodyPitch!=loopPose)throw new AssertionError("Loop depends on sampling history");
        clipPose.reset();layer.apply(40,clipPose);
        if(clipPose.bodyPitch!=0)throw new AssertionError("Clip blend did not release channels");
        for(String asset:new String[]{"counter_cross","evade_backhand","struck_back","advance_cycle"}) {
            var clip=CinematicAnimationClip.bundled(asset);
            for(int fps:new int[]{30,60,144})for(int f=0;f<fps*2;f++) {
                clipPose.reset();clip.apply(f*20f/fps,1,false,clipPose);
                for(float[] angles:clipPose.rot)for(float angle:angles)if(!Float.isFinite(angle))throw new AssertionError("Invalid authored clip");
            }
        }
        var transport = new CinematicPlaybackClock(330);
        transport.speed(.25f);
        for (int i = 0; i < 20; i++) transport.advance();
        if (transport.position() != 5) throw new AssertionError("Quarter-speed drift");
        transport.pause(true);
        for (int i = 0; i < 100; i++) transport.advance();
        if (transport.position() != 5 || transport.rate() != 0) throw new AssertionError("Paused clock moved");
        int revision = transport.revision();
        transport.seek(137);
        transport.step(-.25f);
        if (transport.position() != 136.75f || transport.revision() != revision + 2 || transport.rate() != 0)
            throw new AssertionError("Seek/step did not atomically pause and invalidate interpolation");
        transport.seek(1000);
        if (transport.position() != 330 || transport.finished()) throw new AssertionError("Cannot inspect last frame");
        transport.pause(false);
        if (!transport.finished()) throw new AssertionError("Resuming last frame must finish");
        transport.seek(-1);
        if (transport.position() != 0) throw new AssertionError("Negative seek escaped timeline");
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, 0, 3}) {
            try { transport.speed(invalid); throw new AssertionError("Invalid speed accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        // Forward kinematics must recover the IK endpoint, including unreachable/pole-degenerate inputs.
        for(var goal:new org.joml.Vector3f[]{new org.joml.Vector3f(.25f,.3f,-.2f),
                new org.joml.Vector3f(0,0,0),new org.joml.Vector3f(0,5,0),new org.joml.Vector3f(0,-.4f,0)}) {
            var ik=TwoBoneIk.solve(goal,new org.joml.Vector3f(0,1,0),.25f,.375f);
            var elbow=ik.upper().transform(new org.joml.Vector3f(0,.25f,0));
            var tip=ik.lower().transform(new org.joml.Vector3f(0,.375f,0));
            ik.upper().transform(tip);tip.add(elbow);
            if(!tip.isFinite()||tip.distance(ik.endpoint())>1e-4f
                    ||Math.abs(elbow.length()-.25f)>1e-5f||Math.abs(tip.distance(elbow)-.375f)>1e-5f)
                throw new AssertionError("Two-bone IK length or endpoint mismatch");
            if(goal.length()>.126f&&goal.length()<.624f&&tip.distance(goal)>1e-4f)
                throw new AssertionError("Reachable hand missed its goal");
        }
        var scene=com.FIRNI.superheromod.heroes.sandman.SandArmyPreview.create();
        if(scene.handContacts.size()!=18)throw new AssertionError("Eight soldiers grab; two counterstrikes need contact");
        for(var contact:scene.handContacts) {
            if(contact.weight(contact.start())!=0||contact.weight(contact.end())!=0
                    ||contact.weight((contact.start()+contact.end())*.5f)!=1)throw new AssertionError("Contact transition mismatch");
        }
        if(scene.actorTracks.size()!=12 || scene.totalTicks!=330 || !scene.beats.isEmpty() || scene.setPieces.size()!=1)
            throw new AssertionError("Preview contract: 12 actors, 16.5 seconds, one set piece, no damage beats");
        for(var cue:scene.impacts) {
            if(cue.envelope(cue.tick())!=0||cue.envelope(cue.tick()+cue.duration())!=0)
                throw new AssertionError("Shake leaked outside its cue");
            for(int fps:new int[]{30,60,144})for(int i=0;i<fps*17;i++) {
                float time=i*20f/fps;
                if(Math.abs(cue.oscillation(time))>cue.strength()+1e-5||cue.flash(time)>.141f)
                    throw new AssertionError("Unbounded impact response");
            }
        }
        var cage=scene.setPieces.get(0);
        if(cage.active(224)||!cage.active(225)||cage.active(295)||cage.formation(225)!=0
                ||cage.formation(245)!=1||cage.scatter(260)!=0||cage.scatter(295)!=1)
            throw new AssertionError("Set piece lifecycle boundaries");
        for(float t=0;t<=300;t+=.125f) {
            float value=cage.formation(t), scatter=cage.scatter(t);
            if(value<0||value>1||scatter<0||scatter>1)throw new AssertionError("Unbounded set piece");
            for(int spike=0;spike<32;spike++) {
                float growth=cage.spike(t,spike);
                if(!Float.isFinite(growth)||growth<0||growth>1)throw new AssertionError("Unbounded spike");
            }
        }
        var sample=new CinematicActorTrack.Sample();
        for(var track:scene.actorTracks) {
            track.sample(225,sample);
            Vec3 at225=sample.position;
            for(int fps:new int[]{30,60,144}) {
                for(int f=0;f<=17*fps;f++) {
                    track.sample(f*20f/fps,sample);
                    if(!Double.isFinite(sample.position.lengthSqr()) || !Float.isFinite(sample.scale)
                            || sample.scale<0 || sample.scale>1) throw new AssertionError("Invalid actor transform");
                }
                track.sample(225,sample);
                assertNear(sample.position,at225);
            }
            track.sample(188,sample);
            if(track.binding!=CinematicActorTrack.Binding.ATTACKER && sample.scale>0 && track.dissolveEnd>225 && Math.abs(at225.y-sample.position.y-3)>1e-6)
                throw new AssertionError("Group lift lost synchronization");
            track.sample(245,sample);
            if(track.binding==CinematicActorTrack.Binding.SAND && track.dissolveEnd>245)
                throw new AssertionError("Soldier visible after sphere sealed");
            track.sample(300,sample);
            if(track.binding==CinematicActorTrack.Binding.TARGET && Math.abs(sample.position.y-.12)>1e-5)
                throw new AssertionError("Target did not land");
        }
        for(int i=0;i<2;i++) {
            final String role="soldier_"+i;
            var defeated=scene.actorTracks.stream().filter(a->a.role.equals(role)).findFirst().orElseThrow();
            int hit=i==0?58:89;
            defeated.sample(hit,sample); Vec3 contact=sample.position;
            defeated.sample(hit+15,sample);
            if(sample.position.distanceTo(contact)<2)throw new AssertionError("Counter did not displace attacker");
            for(int tick=hit+42;tick<=330;tick++) {
                defeated.sample(tick,sample);
                if(tick<defeated.dissolveEnd)throw new AssertionError("Defeated attacker returned to swarm");
            }
        }
        var jump=CinematicActorTrack.of("jump",CinematicActorTrack.Binding.SAND)
                .key(0,Vec3.ZERO,0,1,ActorPose.of())
                .key(20,new Vec3(2,0,0),0,1,ActorPose.of(),Easing.LINEAR,2).build();
        jump.sample(10,sample);assertNear(sample.position,new Vec3(1,2,0));
        jump.sample(20,sample);assertNear(sample.position,new Vec3(2,0,0));
        var mutable=ActorPose.of().j(ActorPose.HEAD,10,0,0);
        var wrapped=CinematicActorTrack.of("wrap",CinematicActorTrack.Binding.SAND)
                .key(0,Vec3.ZERO,170,1,mutable).key(10,Vec3.ZERO,-170,1,mutable).build();
        mutable.rot[ActorPose.HEAD][0]=99;
        wrapped.sample(5,sample);
        if(Math.abs(sample.yaw-180)>1e-4 || Math.abs(sample.pose.rot[0][0]-Math.toRadians(10))>1e-5)
            throw new AssertionError("Yaw shortest path or immutable pose failed");
        try {
            CinematicActorTrack.of("bad",CinematicActorTrack.Binding.SAND)
                    .key(2,Vec3.ZERO,0,1,mutable).key(1,Vec3.ZERO,0,1,mutable);
            throw new AssertionError("Out-of-order actor keys accepted");
        } catch(IllegalArgumentException expected) { }
        var curve = Shot.of(40).curve(Vec3.ZERO, new Vec3(0,2,0),
                new Vec3(2,2,0), new Vec3(2,0,0)).build();
        assertNear(curve.positionAt(0), Vec3.ZERO);
        assertNear(curve.positionAt(1), new Vec3(2,0,0));
        assertNear(curve.positionAt(.5f), new Vec3(1,1.5,0));
        assertNear(curve.positionAt(-1), Vec3.ZERO);
        assertNear(curve.positionAt(2), new Vec3(2,0,0));
        Vec3 fixedOffset = CinematicCameraMotion.offset(40, 1, .35f);
        for (int fps : new int[]{30,60,144}) {
            for (int frame=0; frame<fps*3; frame++) {
                float time=frame*20f/fps;
                Vec3 offset=CinematicCameraMotion.offset(time,1,.35f);
                if (!Double.isFinite(offset.lengthSqr()) || offset.length()>.2)
                    throw new AssertionError("Camera offset out of bounds");
            }
            assertNear(CinematicCameraMotion.offset(40,1,.35f),fixedOffset);
        }
        assertNear(CinematicCameraMotion.offset(40,0,0),Vec3.ZERO);
        try {
            Shot.of(10).curve(Vec3.ZERO,new Vec3(Double.NaN,0,0),Vec3.ZERO,Vec3.ZERO);
            throw new AssertionError("Invalid control point accepted");
        } catch (IllegalArgumentException expected) { }
        var def=CinematicDefinition.builder("motion_check").shot(Shot.of(100).at(1,2,3).build())
                .anchorTarget(new Vec3(0,0,1))
                .beat(Beat.moveTarget(10,new Vec3(0,0,.5),.25f))
                .beat(Beat.freeze(18))
                .beat(Beat.launchTarget(30,new Vec3(0,0,.8),new Vec3(0,0,2),4,20))
                .beat(Beat.freeze(55)).build();
        var stage=StageFrame.of(Vec3.ZERO,new Vec3(0,0,1),10);
        Vec3 initial=new Vec3(0,0,10);
        assertNear(CinematicMotion.target(def,stage,initial,18),new Vec3(0,0,8));
        assertNear(CinematicMotion.target(def,stage,initial,25),new Vec3(0,0,8));
        assertNear(CinematicMotion.target(def,stage,initial,40),new Vec3(0,4,14));
        assertNear(CinematicMotion.target(def,stage,initial,90),new Vec3(0,0,20));
        // Random-access seeking must produce the same result after any sample order.
        for(int fps:new int[]{30,60,144}) {
            for(int f=0;f<fps*5;f++) {
                float t=f*20f/fps;
                var p=CinematicMotion.target(def,stage,initial,t);
                if(!Double.isFinite(p.x+p.y+p.z))throw new AssertionError("Nonfinite motion");
            }
            assertNear(CinematicMotion.target(def,stage,initial,40),new Vec3(0,4,14));
        }
        var transformed=StageFrame.of(new Vec3(100,70,-80),new Vec3(1,0,0),10);
        assertNear(CinematicMotion.target(def,transformed,initial,40),new Vec3(114,74,-80));
        System.out.println("PASS: root motion, freeze, launch, arbitrary seeking and rotated stages at 30/60/144 FPS");
    }
    private static void checkAuthoredPoseBridge() {
        for(String name:new String[]{"defense_wide","defense_slim"}) {
            var mesh=GltfSkinnedModel.bundled(name);var direct=mesh.newSample();var prepared=mesh.newSample();
            var pose=ActorPose.of();
            // Directional assertions, not merely agreement between two equally wrong paths.
            prepared.preparePose(pose,"defense_counters",0,1,false);
            for(int joint:new int[]{ActorPose.RIGHT_UPPER_ARM,ActorPose.LEFT_UPPER_ARM}) {
                float[] rotation=pose.rot[joint];
                var tip=new org.joml.Quaternionf().rotationZYX(rotation[2],rotation[1],rotation[0])
                        .transform(new org.joml.Vector3f(0,1,0));
                if(tip.z>-.4f)throw new AssertionError("Guard arm points backwards: "+name);
            }
            float[] head=pose.rot[ActorPose.HEAD];
            var face=new org.joml.Quaternionf().rotationZYX(head[2],head[1],head[0])
                    .transform(new org.joml.Vector3f(0,0,-1));
            if(face.z>-.95f || Math.abs(face.y)>.1f)
                throw new AssertionError("Initial head is not upright/forward: "+name);
            for(float seconds:new float[]{0,.3f,1.2f,2.75f,3.7f})for(float weight:new float[]{0,.5f,1}) {
                pose.reset();direct.apply(pose,"defense_counters",seconds,weight,false);
                prepared.preparePose(pose,"defense_counters",seconds,weight,false);
                prepared.apply(pose,"defense_counters",seconds,weight,false,true);
                for(int i=0;i<mesh.vertexCount();i++) {
                    if(direct.vertex(i).distance(prepared.vertex(i))>2e-5)throw new AssertionError("Authored pose bridge: "+name+" t="+seconds+" w="+weight+" vertex="+i+" direct="+direct.vertex(i)+" prepared="+prepared.vertex(i));
                    if(!Float.isFinite(prepared.normal(i).lengthSquared())||Math.abs(prepared.normal(i).length()-1)>1e-4)
                        throw new AssertionError("Invalid deformed shading normal");
                }
            }
            // A contact solver's post-sample joint adjustment must survive the final render sample.
            pose.reset();prepared.preparePose(pose,"defense_counters",1.2f,1,false);
            prepared.apply(pose,"defense_counters",1.2f,1,false,true);
            var before=new org.joml.Vector3f[mesh.vertexCount()];
            for(int i=0;i<before.length;i++)before[i]=new org.joml.Vector3f(prepared.vertex(i));
            pose.rot[ActorPose.RIGHT_LOWER_ARM][0]+=.3f;
            prepared.apply(pose,"defense_counters",1.2f,1,false,true);
            boolean changed=false;for(int i=0;i<before.length;i++)changed|=before[i].distance(prepared.vertex(i))>1e-4;
            if(!changed)throw new AssertionError("Contact adjustment overwritten by rig clip");
        }
        System.out.println("PASS: Blender defense pose bridge, contact override and deformed normals");
    }

    private static void checkGltfMesh() {
        for(String name:new String[]{"puppet_wide","puppet_slim"}) {
            var mesh=GltfSkinnedModel.bundled(name);var sample=mesh.newSample();var pose=ActorPose.of();
            if(mesh.vertexCount()!=1136||mesh.triangleCount()!=568)throw new AssertionError("Unexpected mesh fixture");
            sample.apply(pose);
            try(var stream=CinematicMotionCheck.class.getResourceAsStream("/assets/superheromod/cinematics/gltf/"+name+".gltf")) {
                var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                String uri=json.getAsJsonArray("buffers").get(0).getAsJsonObject().get("uri").getAsString();
                var bytes=java.nio.ByteBuffer.wrap(java.util.Base64.getDecoder().decode(uri.substring(uri.indexOf(',')+1))).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                // Fixture POSITION is accessor/view zero. Rest skinning must reproduce every authored vertex.
                for(int i=0;i<mesh.vertexCount();i++) {
                    var expected=new org.joml.Vector3f(bytes.getFloat(i*12),bytes.getFloat(i*12+4),bytes.getFloat(i*12+8));
                    if(expected.distance(sample.vertex(i))>1e-6)throw new AssertionError("Inverse bind/hierarchy changed neutral mesh");
                }
                if(!mesh.hasClip("guard_wrists"))throw new AssertionError("Embedded rig clip not loaded");
                var bindVertices=new org.joml.Vector3f[mesh.vertexCount()];
                for(int i=0;i<bindVertices.length;i++)bindVertices[i]=new org.joml.Vector3f(sample.vertex(i));
                sample.apply(pose,"guard_wrists",.25f,1,true);
                int moved=0;
                var animated=new org.joml.Vector3f[mesh.vertexCount()];
                for(int i=0;i<bindVertices.length;i++) {
                    animated[i]=new org.joml.Vector3f(sample.vertex(i));
                    if(animated[i].distance(bindVertices[i])>1e-6) {
                        moved++;
                        if(bindVertices[i].y<9f/16||Math.abs(bindVertices[i].x)<.24f||bindVertices[i].y>.751f)
                            throw new AssertionError("Wrist clip deformed unrelated geometry");
                    }
                }
                if(moved==0)throw new AssertionError("Extra bones were not animated");
                sample.apply(pose,"guard_wrists",1.25f,1,true);
                for(int i=0;i<animated.length;i++)if(animated[i].distance(sample.vertex(i))>1e-6)throw new AssertionError("Rig loop depends on history");
                sample.apply(pose,"guard_wrists",.25f,0,false);
                for(int i=0;i<animated.length;i++)if(bindVertices[i].distance(sample.vertex(i))>1e-6)throw new AssertionError("Zero-weight rig clip persists");
                var placement=new RigClipPlacement("guard_wrists",34,110,1,5,true);
                if(placement.weight(33)!=0||placement.weight(34)!=0||placement.weight(110)!=0||placement.weight(50)!=1)
                    throw new AssertionError("Rig clip placement fades");
                var neutral=new org.joml.Vector3f(sample.vertex(0));
                pose.j(ActorPose.RIGHT_LOWER_ARM,-90,0,0);sample.apply(pose);
                if(sample.vertex(0).distance(neutral)>1e-6)throw new AssertionError("Arm skinning moved head");
                for(int i=0;i<mesh.vertexCount();i++)if(!Float.isFinite(sample.vertex(i).lengthSquared()))throw new AssertionError("Invalid deformed mesh");
                pose.reset();pose.crouch=.2f;sample.apply(pose);
                if(sample.vertex(0).distance(new org.joml.Vector3f(neutral).add(0,.2f,0))>1e-6)throw new AssertionError("Crouch applied twice");
                pose.reset();sample.apply(pose);
                if(sample.vertex(0).distance(neutral)>1e-6)throw new AssertionError("Mesh sampling retained previous pose");
                for(int failure=0;failure<5;failure++) {
                    var bad=json.deepCopy();
                    switch(failure) {
                        case 0 -> {var children=new com.google.gson.JsonArray();children.add(2);bad.getAsJsonArray("nodes").get(0).getAsJsonObject().add("children",children);}
                        case 1 -> bad.getAsJsonArray("bufferViews").get(0).getAsJsonObject().addProperty("byteLength",1);
                        case 2 -> bad.getAsJsonArray("skins").get(0).getAsJsonObject().getAsJsonArray("joints").set(0,new com.google.gson.JsonPrimitive(999));
                        case 3 -> bad.getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonArray("channels").get(0).getAsJsonObject().getAsJsonObject("target").addProperty("node",999);
                        case 4 -> bad.getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonArray("samplers").get(0).getAsJsonObject().addProperty("interpolation","CUBICSPLINE");
                    }
                    try {GltfSkinnedModel.read(new java.io.StringReader(bad.toString()));throw new AssertionError("Invalid skinned mesh accepted");}
                    catch(IllegalArgumentException expected) { }
                }
            } catch(java.io.IOException e) {throw new AssertionError(e);}
        }
        System.out.println("PASS: imported skinned meshes, bind matrices, hierarchy, independent deformation and bounds");
    }

    private static void checkGltf() {
        var clip=GltfPoseClip.bundled("counter_elbow");
        if(clip.durationTicks()!=30)throw new AssertionError("glTF seconds/ticks conversion");
        var pose=ActorPose.of();pose.j(ActorPose.LEFT_UPPER_ARM,20,0,0);
        clip.apply(3.5f,1,false,pose);
        if(Math.abs(pose.rot[ActorPose.RIGHT_LOWER_ARM][0]+Math.PI/2)>1e-5
                ||Math.abs(pose.rot[ActorPose.LEFT_UPPER_ARM][0]-Math.toRadians(20))>1e-5)
            throw new AssertionError("glTF quaternion interpolation or channel mask");
        for(int fps:new int[]{30,60,144}) {
            for(int i=0;i<fps*2;i++) {pose.reset();clip.apply(i*20f/fps,1,false,pose);}
            pose.reset();clip.apply(14,1,false,pose);
            if(Math.abs(pose.rot[ActorPose.RIGHT_LOWER_ARM][0]-Math.toRadians(-5))>1e-5)
                throw new AssertionError("glTF seeking depends on frame history");
        }
        pose.reset();clip.apply(0,.5f,true,pose);
        if(Math.abs(pose.rot[ActorPose.RIGHT_LOWER_ARM][0]-Math.toRadians(-35))>1e-5)
            throw new AssertionError("Additive glTF weight");
        try(var stream=CinematicMotionCheck.class.getResourceAsStream("/assets/superheromod/cinematics/gltf/counter_elbow.gltf")) {
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var step=json.deepCopy();
            step.getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonArray("samplers").get(0).getAsJsonObject().addProperty("interpolation","STEP");
            pose.reset();GltfPoseClip.read(new java.io.StringReader(step.toString())).apply(3.5f,1,false,pose);
            if(Math.abs(pose.rot[ActorPose.RIGHT_LOWER_ARM][0]-Math.toRadians(-70))>1e-5)throw new AssertionError("glTF STEP");
            for(int failure=0;failure<4;failure++) {
                var bad=json.deepCopy();
                switch(failure) {
                    case 0 -> bad.getAsJsonArray("bufferViews").get(1).getAsJsonObject().addProperty("byteLength",4);
                    case 1 -> bad.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("name","unknown_finger");
                    case 2 -> bad.getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonArray("samplers").get(0).getAsJsonObject().addProperty("interpolation","CUBICSPLINE");
                    case 3 -> bad.getAsJsonObject("extras").addProperty("puppetProfile","unknown");
                }
                try {GltfPoseClip.read(new java.io.StringReader(bad.toString()));throw new AssertionError("Malformed glTF accepted");}
                catch(IllegalArgumentException expected) { }
            }
        } catch(java.io.IOException e) {throw new AssertionError(e);}
        System.out.println("PASS: glTF interpolation, masks, seeking, additive blend, malformed assets");
    }

    private static void checkSkinning() {
        var first=new org.joml.Matrix4f();
        var second=new org.joml.Matrix4f();
        var point=new org.joml.Vector3f(.25f,.5f,.125f);
        var result=new org.joml.Vector3f(); var scratch=new org.joml.Vector3f();
        for(float w=0;w<=1;w+=.05f) {
            Skinning.position(first,second,w,point,result,scratch);
            if(result.distance(point)>1e-6)throw new AssertionError("Bind pose changed skin geometry");
        }
        // Rotation around an elbow must preserve the elbow pivot itself.
        var pivot=new org.joml.Vector3f(0,.375f,0);
        for(int degrees=0;degrees<=150;degrees++) {
            second.identity().translate(pivot).rotateX((float)Math.toRadians(degrees)).translate(0,-.375f,0);
            Skinning.position(first,second,.5f,pivot,result,scratch);
            if(result.distance(pivot)>1e-6)throw new AssertionError("Elbow detached");
            // Adjacent rings remain continuous even in deeply bent poses.
            point.set(.125f,.375f-.000001f,.125f);
            Skinning.position(first,second,Skinning.weight(point.y,.25f,.5f),point,result,scratch);
            var before=new org.joml.Vector3f(result);
            point.y+=.000002f;
            Skinning.position(first,second,Skinning.weight(point.y,.25f,.5f),point,result,scratch);
            if(!Float.isFinite(result.x+result.y+result.z)||before.distance(result)>1e-5)
                throw new AssertionError("Skin discontinuity at joint");
        }
        if(Skinning.weight(-1,0,1)!=0||Skinning.weight(2,0,1)!=1)
            throw new AssertionError("Weights exceed joint influences");
        System.out.println("PASS: weighted skin bind pose, elbow pivot, continuity through 150 degrees");
    }

    private static void assertNear(Vec3 actual,Vec3 expected) {
        if(actual.distanceTo(expected)>1e-5)throw new AssertionError(actual+" != "+expected);
    }
}

