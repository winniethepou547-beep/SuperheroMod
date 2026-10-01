package com.FIRNI.superheromod.client.render.puppet;

import com.FIRNI.superheromod.core.cinematic.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/** Mirrors the renderer hierarchy so contact goals follow root, waist and chest animation. */
public final class PuppetContacts {
    public static void apply(List<CinematicPuppet> actors,List<CinematicHandContact> contacts,float tick) {
        for(var contact:contacts) {
            float weight=contact.weight(tick);
            if(weight<=0)continue;
            CinematicPuppet source=find(actors,contact.sourceRole()),target=find(actors,contact.targetRole());
            if(source==null||target==null||source.scale<.01||target.scale<.01||source.sourceEntity!=null)continue;
            Matrix4f shoulder=chest(source,source.position).translate(contact.rightHand()?-.3125f:.3125f,-.25f,0);
            var p=contact.targetPoint();
            Vector3f goal=chest(target,source.position).transformPosition(new Vector3f((float)p.x,(float)p.y,(float)p.z));
            shoulder.invert().transformPosition(goal);
            // Front of the rig is -Z. Release a contact that has moved behind the
            // shoulder instead of forcing an arm through the torso to chase it.
            float front = Math.max(0, Math.min(1, (.04f-goal.z)/.18f));
            weight *= front*front*(3-2*front);
            if(weight<=0)continue;
            var solved=TwoBoneIk.solve(goal,new Vector3f(contact.rightHand()?-1:1,.6f,-.35f),.25f,.375f);
            blend(source.pose,contact.rightHand()?ActorPose.RIGHT_UPPER_ARM:ActorPose.LEFT_UPPER_ARM,solved.upper(),weight);
            blend(source.pose,contact.rightHand()?ActorPose.RIGHT_LOWER_ARM:ActorPose.LEFT_LOWER_ARM,solved.lower(),weight);
        }
    }
    private static CinematicPuppet find(List<CinematicPuppet> actors,String role) {
        for(var actor:actors)if(actor.track!=null&&actor.track.role.equals(role))return actor;
        return null;
    }
    private static Matrix4f chest(CinematicPuppet actor,net.minecraft.world.phys.Vec3 origin) {
        ActorPose p=actor.pose;
        var relative=actor.position.subtract(origin);
        Matrix4f m=new Matrix4f().translation((float)relative.x,(float)relative.y,(float)relative.z)
                .rotateY((float)Math.toRadians(180-actor.yaw))
                .rotateZ((float)Math.toRadians(p.bodyRoll)).rotateX((float)Math.toRadians(p.bodyPitch))
                .scale(-actor.scale,-actor.scale,actor.scale).translate(0,-1.501f,0)
                .translate(0,.75f+p.crouch,0);
        rotate(m,p.rot[ActorPose.HIPS]);m.translate(0,-.375f,0);rotate(m,p.rot[ActorPose.CHEST]);return m;
    }
    private static void rotate(Matrix4f m,float[] r){m.rotateZYX(r[2],r[1],r[0]);}
    private static void blend(ActorPose pose,int joint,Quaternionf target,float weight) {
        float[] r=pose.rot[joint];
        var q=new Quaternionf().rotationZYX(r[2],r[1],r[0]).slerp(target,weight);
        Vector3f angles=com.FIRNI.superheromod.core.cinematic.JointRotations.toEuler(q,new Vector3f());
        r[0]=angles.x;r[1]=angles.y;r[2]=angles.z;
    }
    private PuppetContacts() {}
}
