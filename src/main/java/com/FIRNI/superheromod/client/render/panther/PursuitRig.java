package com.FIRNI.superheromod.client.render.panther;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;

/**
 * Where any part of Panther's body is, without drawing him: the same chain of joints PantherBody.draw walks
 * (root, pelvis, spine, chest, then each arm to the hand, each leg to the foot, the neck to the head), so the
 * film can aim its camera at his eye before he is drawn, put a bullet's hit on his shoulder or a claw's
 * puncture where his hand really is. Positions in model pixels as in PantherBody (y down, -z in front, -x his right).
 */
public final class PursuitRig {
    public static final int PELVIS = 0, CHEST = 1, HEAD = 2, R_UPPER = 3, L_UPPER = 4, R_HAND = 5, L_HAND = 6, R_FOOT = 7, L_FOOT = 8,
            R_THIGH = 9, L_THIGH = 10, R_FOREARM = 11, L_FOREARM = 12, R_SHIN = 13, L_SHIN = 14;
    private PursuitRig() {}

    private static void px(Matrix4f m, float x, float y, float z) { m.translate(x / 16, y / 16, z / 16); }
    private static void rot(Matrix4f m, float x, float y, float z) { m.rotate(new Quaternionf().rotationZYX(z, y, x)); }

    /** The frame of one bone (model pixels inside it), for a body placed by place.matrix(). */
    public static Matrix4f bone(Matrix4f body, Pose pose, int bone) {
        float[] v = pose.v;
        Matrix4f m = new Matrix4f(body);
        px(m, v[SHIFT_X], -v[LIFT], v[SHIFT_Z]);
        px(m, 0, 11, 0);
        if (v[ROOT_PITCH] != 0) m.rotateX(v[ROOT_PITCH]);
        if (v[ROOT_YAW] != 0) m.rotateY(v[ROOT_YAW]);
        if (v[ROOT_ROLL] != 0) m.rotateZ(v[ROOT_ROLL]);
        px(m, 0, -11, 0);
        float drop = Mth.clamp(v[CROUCH], -1, 9.5f);
        float fold = (float) Math.acos(Mth.clamp(1 - Math.max(0, drop) / 10.8f, -1, 1));
        float plant = Mth.clamp(v[PLANT], 0, 1);
        px(m, 0, 12 + drop, 0);
        m.rotateY(v[PELVIS_YAW]).rotateX(v[PELVIS_PITCH]).rotateZ(v[PELVIS_ROLL]);
        if (bone == PELVIS) return m;
        if (bone == R_THIGH || bone == L_THIGH || bone == R_SHIN || bone == L_SHIN || bone == R_FOOT || bone == L_FOOT) {
            int side = bone == R_THIGH || bone == R_SHIN || bone == R_FOOT ? 0 : 1;
            int s = side == 0 ? -1 : 1, o = side == 0 ? RL : LL;
            float legX = v[o + LEG_X] - fold * plant - v[PELVIS_PITCH] * plant, knee = v[o + KNEE] + 2 * fold * plant;
            float ankle = v[o + ANKLE] - (legX + knee + v[PELVIS_PITCH]) * plant;
            px(m, s * 2.05f, 0, 0);
            rot(m, legX, s < 0 ? v[o + LEG_Y] : -v[o + LEG_Y], s < 0 ? v[o + LEG_Z] : -v[o + LEG_Z]);
            if (bone == R_THIGH || bone == L_THIGH) return m;
            px(m, 0, 6, 0);
            m.rotateX(knee);
            if (bone == R_SHIN || bone == L_SHIN) return m;
            px(m, 0, 4.8f, 0);
            m.rotateX(ankle);
            return m;
        }
        m.rotateY(v[SPINE_YAW]).rotateX(v[SPINE_PITCH]).rotateZ(v[SPINE_ROLL]);
        px(m, 0, -5.6f, 0);
        m.rotateY(v[CHEST_YAW]).rotateX(v[CHEST_PITCH]).rotateZ(v[CHEST_ROLL]);
        if (bone == CHEST) return m;
        if (bone == HEAD) {
            px(m, 0, -6.6f, -v[NECK]);
            m.rotateY(v[HEAD_YAW]).rotateX(Mth.clamp(v[HEAD_PITCH], -1.4f, 1.3f)).rotateZ(v[HEAD_ROLL]);
            return m;
        }
        int side = bone == R_UPPER || bone == R_FOREARM || bone == R_HAND ? 0 : 1;
        int s = side == 0 ? -1 : 1, o = side == 0 ? R : L;
        px(m, s * 5.0f, -5.4f - v[o + SH_UP] * .6f, -v[o + SH_FWD] * .6f);
        rot(m, v[o + ARM_X], s < 0 ? v[o + ARM_Y] : -v[o + ARM_Y], s < 0 ? v[o + ARM_Z] : -v[o + ARM_Z]);
        if (bone == R_UPPER || bone == L_UPPER) return m;
        px(m, 0, 5.0f, 0);
        m.rotateX(-v[o + ELBOW]);
        if (bone == R_FOREARM || bone == L_FOREARM) return m;
        px(m, 0, 4.8f, 0);
        m.rotateX(v[o + WRIST_X]);
        m.rotateZ(s < 0 ? v[o + WRIST_Z] : -v[o + WRIST_Z]);
        return m;
    }
    /** A point (model pixels) in a bone's frame, in stage space. */
    public static Vec3 point(Matrix4f body, Pose pose, int bone, float x, float y, float z) {
        Vector3f p = bone(body, pose, bone).transformPosition(new Vector3f(x / 16, y / 16, z / 16));
        return new Vec3(p.x, p.y, p.z);
    }
    /** The right eye's lens (stage space). */
    public static Vec3 eye(Matrix4f body, Pose pose) { return point(body, pose, HEAD, -1.65f, -5.25f, -4.3f); }
    /** The middle of the head (stage space). */
    public static Vec3 head(Matrix4f body, Pose pose) { return point(body, pose, HEAD, 0, -4.8f, 0); }
    /** The middle of the chest, on its surface in front. */
    public static Vec3 chest(Matrix4f body, Pose pose) { return point(body, pose, CHEST, 0, -3.3f, -2.6f); }
    /** The middle of the palm of a hand (side 0 right), and the tip of its middle claw. */
    public static Vec3 palm(Matrix4f body, Pose pose, int side) { return point(body, pose, side == 0 ? R_HAND : L_HAND, 0, 1.6f, 0); }
    public static Vec3 claw(Matrix4f body, Pose pose, int side, int finger) {
        return point(body, pose, side == 0 ? R_HAND : L_HAND, 0, 5.6f, -1.08f + finger * .72f);
    }
    /** Where a hit on a suit region lands (stage space), for the shots. */
    public static Vec3 region(Matrix4f body, Pose pose, int region, int side) {
        boolean left = side >= 0;
        return switch (region) {
            case PantherBody.CHEST -> point(body, pose, CHEST, .8f, -4.0f, -2.9f);
            case PantherBody.RIBS -> point(body, pose, CHEST, left ? 3.9f : -3.9f, -1.6f, -1.0f);
            case PantherBody.SHOULDER -> point(body, pose, left ? L_UPPER : R_UPPER, 0, -.6f, -1.2f);
            case PantherBody.UPPER_ARM -> point(body, pose, left ? L_UPPER : R_UPPER, left ? 1.8f : -1.8f, 2.6f, -.4f);
            case PantherBody.THIGH -> point(body, pose, left ? L_THIGH : R_THIGH, 0, 3.4f, -2.2f);
            case PantherBody.SHIN -> point(body, pose, left ? L_SHIN : R_SHIN, 0, 2.2f, -2.0f);
            default -> point(body, pose, CHEST, 0, -3.3f, -2.6f);
        };
    }
}
