package com.FIRNI.superheromod;

import com.FIRNI.superheromod.core.cinematic.*;
import java.nio.file.*;

/** Tests an actual editor-exported file using the game's reader and deformation code. */
public final class GltfAssetCheck {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Supply converted glTF path");
        try(var reader=Files.newBufferedReader(Path.of(args[0]))) {
            var mesh=GltfSkinnedModel.read(reader);var sample=mesh.newSample();var pose=ActorPose.of();
            sample.apply(pose);
            float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
            for(int i=0;i<mesh.vertexCount();i++){min=Math.min(min,sample.vertex(i).y);max=Math.max(max,sample.vertex(i).y);}
            if(Math.abs(min+.5f)>1e-4||Math.abs(max-1.5f)>1e-4)throw new AssertionError("Editor changed model height/origin: "+min+".."+max);
            if(mesh.clipNames().isEmpty())throw new AssertionError("Animation lost on export");
            var neutral=new org.joml.Vector3f[mesh.vertexCount()];
            for(int i=0;i<neutral.length;i++)neutral[i]=new org.joml.Vector3f(sample.vertex(i));
            for(String clip:mesh.clipNames())for(int fps:new int[]{30,60,144})for(int i=0;i<fps*2;i++) {
                sample.apply(pose,clip,i/(float)fps,1,true);
                for(int vertex=0;vertex<mesh.vertexCount();vertex++)if(!Float.isFinite(sample.vertex(vertex).lengthSquared()))throw new AssertionError("Invalid editor animation");
            }
            if(mesh.hasClip("contact_test")) {
                sample.apply(pose,"contact_test",.6f,1,false);
                int changed=0;
                for(int i=0;i<neutral.length;i++)if(neutral[i].distance(sample.vertex(i))>.01f)changed++;
                if(changed<20)throw new AssertionError("Blender-authored action did not deform the imported mesh");
                System.out.println("PASS Blender-authored contact_test deforms "+changed+" vertices");
            }
            System.out.println("PASS editor asset: "+mesh.vertexCount()+" vertices; clips="+mesh.clipNames()+"; height="+min+".."+max);
        }
    }
}
