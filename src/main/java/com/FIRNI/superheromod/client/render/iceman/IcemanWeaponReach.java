package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;

/**
 * Iceman's hands put where a weapon needs them: the forward kinematics of IcemanBody's chain (the same translations
 * and turns, in model pixels: +y down, -z his front, -x his right) and a small solver that sets an arm's six joints
 * (ARM_X / Y / Z, ELBOW, WRIST_X / Z) so its fist reaches a point, the hand turned as asked (the weapon's long axis, the
 * hand frame's -z; and which way the fingers run, its +y), staying near a natural starting pose (the seed) so the elbow
 * never flips. Used where both hands must stay on one weapon: the spear's flurry (the front hand on the shaft wherever
 * the rear hand drives it) and the planted sword (both hands on its hilt, where the server planted it).
 * <p>
 * Render thread only (scratch objects); a few dozen evaluations of the chain per arm, nothing allocated per frame.
 */
final class IcemanWeaponReach {
    private IcemanWeaponReach() {}

    private static final float FIST = IcemanBody.FIST, SHOULDER = IcemanBody.SHOULDER;
    private static final Quaternionf Q = new Quaternionf();
    private static final Matrix4f F = new Matrix4f();
    /** Joint limits: ARM_X, ARM_Y, ARM_Z, ELBOW, WRIST_X, WRIST_Z. */
    private static final float[] LO = {-3.3f, -2.0f, -1.2f, 0f, -1.0f, -1.2f}, HI = {1.2f, 2.0f, 2.4f, 2.6f, 1.75f, 1.2f};
    private static final int[] JOINTS = {ARM_X, ARM_Y, ARM_Z, ELBOW, WRIST_X, WRIST_Z};

    /** The chest's frame in model space for a pose (as IcemanBody.draw builds it), into out. */
    static Matrix4f chest(Pose pose, Matrix4f out) {
        float[] v = pose.v;
        out.identity().translate(v[SHIFT_X], -v[LIFT], v[SHIFT_Z]).translate(0, 11, 0);
        if (v[ROOT_PITCH] != 0) out.rotateX(v[ROOT_PITCH]);
        if (v[ROOT_YAW] != 0) out.rotateY(v[ROOT_YAW]);
        if (v[ROOT_ROLL] != 0) out.rotateZ(v[ROOT_ROLL]);
        out.translate(0, -11, 0);
        float drop = Mth.clamp(v[CROUCH], -1, 9.5f);
        out.translate(0, 12 + drop, 0).rotateY(v[PELVIS_YAW]).rotateX(v[PELVIS_PITCH]).rotateZ(v[PELVIS_ROLL]);
        out.rotateY(v[SPINE_YAW]).rotateX(v[SPINE_PITCH]).rotateZ(v[SPINE_ROLL]);
        out.translate(0, -5.6f, 0).rotateY(v[CHEST_YAW]).rotateX(v[CHEST_PITCH]).rotateZ(v[CHEST_ROLL]);
        return out;
    }
    /** The fist's frame (its middle, the weapon's frame: long axis -z) under the chest frame, with the arm's joints q (six, as JOINTS). */
    static Matrix4f fist(Pose pose, int side, Matrix4f chest, float[] q, Matrix4f out) {
        float[] v = pose.v;
        int s = side == 0 ? -1 : 1, o = side == 0 ? R : L;
        out.set(chest).translate(s * SHOULDER, -5.3f - v[o + SH_UP] * .6f, -v[o + SH_FWD] * .6f);
        out.rotate(Q.rotationZYX(s < 0 ? q[2] : -q[2], s < 0 ? q[1] : -q[1], q[0]));
        out.translate(0, 5, 0).rotateX(-q[3]);
        out.translate(0, 4.7f, 0).rotateX(q[4]).rotateZ(s < 0 ? q[5] : -q[5]);
        return out.translate(0, FIST, 0);
    }
    /** The fist's frame for the pose's own arm joints. */
    static Matrix4f fist(Pose pose, int side, Matrix4f chest, Matrix4f out) {
        int o = side == 0 ? R : L;
        float[] q = Q0;
        for (int k = 0; k < 6; k++) q[k] = pose.v[o + JOINTS[k]];
        return fist(pose, side, chest, q, out);
    }

    // ------------------------------------------------------------------ the solver
    private static final float[] Q0 = new float[6], QT = new float[6], QN = new float[6], RES = new float[15], RES2 = new float[15];
    private static final float[][] J = new float[15][6];
    private static final float[] A = new float[36], B = new float[6], DQ = new float[6];
    private static int n;
    private static float tx, ty, tz, ax, ay, az, wa, yx, yy, yz, wy;
    private static boolean useAxis, useY;
    private static float reg;

    /**
     * Sets the arm's joints (side 0 right) so its fist reaches target (in the space chest is given in), starting from
     * seed (six joint values, a natural pose near the answer: the solution is pulled toward it, gently). axis: where the
     * hand frame's -z should point (unit; null: free), wAxis how much that matters; fingers: where its +y should point
     * (null: free). Returns how far the fist ends from the target (pixels).
     */
    static float reach(Pose pose, int side, Matrix4f chest, Vector3f target, Vector3f axis, float wAxis, Vector3f fingers, float wFingers, float[] seed) {
        tx = target.x; ty = target.y; tz = target.z;
        useAxis = axis != null; useY = fingers != null;
        if (useAxis) { ax = axis.x; ay = axis.y; az = axis.z; wa = wAxis; }
        if (useY) { yx = fingers.x; yy = fingers.y; yz = fingers.z; wy = wFingers; }
        reg = .08f;
        System.arraycopy(seed, 0, Q0, 0, 6);
        float[] q = QT;
        System.arraycopy(seed, 0, q, 0, 6);
        float lambda = .01f;
        float err = residual(pose, side, chest, q, RES);
        for (int it = 0; it < 14; it++) {
            // The Jacobian by small steps of each joint.
            for (int k = 0; k < 6; k++) {
                float keep = q[k];
                q[k] = keep + 1e-3f;
                residual(pose, side, chest, q, RES2);
                q[k] = keep;
                for (int r = 0; r < n; r++) J[r][k] = (RES2[r] - RES[r]) / 1e-3f;
            }
            // (JtJ + lambda) dq = -Jt r
            for (int i = 0; i < 6; i++) {
                float b = 0;
                for (int r = 0; r < n; r++) b += J[r][i] * RES[r];
                B[i] = -b;
                for (int j = 0; j < 6; j++) {
                    float a = 0;
                    for (int r = 0; r < n; r++) a += J[r][i] * J[r][j];
                    A[i * 6 + j] = a + (i == j ? lambda * (1 + a) : 0);
                }
            }
            if (!solve6(A, B, DQ)) break;
            for (int k = 0; k < 6; k++) QN[k] = Mth.clamp(q[k] + DQ[k], LO[k], HI[k]);
            float e2 = residual(pose, side, chest, QN, RES2);
            if (e2 < err) {
                System.arraycopy(QN, 0, q, 0, 6);
                System.arraycopy(RES2, 0, RES, 0, n);
                float gain = err - e2;
                err = e2;
                lambda = Math.max(1e-4f, lambda * .4f);
                if (gain < 1e-6f) break;
            } else lambda *= 5;
        }
        int o = side == 0 ? R : L;
        for (int k = 0; k < 6; k++) pose.v[o + JOINTS[k]] = q[k];
        fist(pose, side, chest, q, F);
        float dx = F.m30() - tx, dy = F.m31() - ty, dz = F.m32() - tz;
        return Mth.sqrt(dx * dx + dy * dy + dz * dz);
    }
    /** The residuals (the fist off the target, the hand's turn off what is asked, the joints off the seed) into r; returns their squared sum. */
    private static float residual(Pose pose, int side, Matrix4f chest, float[] q, float[] r) {
        fist(pose, side, chest, q, F);
        int i = 0;
        r[i++] = F.m30() - tx; r[i++] = F.m31() - ty; r[i++] = F.m32() - tz;
        if (useAxis) { r[i++] = wa * (-F.m20() - ax); r[i++] = wa * (-F.m21() - ay); r[i++] = wa * (-F.m22() - az); }
        if (useY) { r[i++] = wy * (F.m10() - yx); r[i++] = wy * (F.m11() - yy); r[i++] = wy * (F.m12() - yz); }
        for (int k = 0; k < 6; k++) r[i++] = reg * (q[k] - Q0[k]);
        n = i;
        float e = 0;
        for (int k = 0; k < i; k++) e += r[k] * r[k];
        return e;
    }
    /** Gaussian elimination with partial pivoting on a 6x6 system (a is destroyed). */
    private static boolean solve6(float[] a, float[] b, float[] x) {
        for (int c = 0; c < 6; c++) {
            int piv = c;
            for (int r = c + 1; r < 6; r++) if (Math.abs(a[r * 6 + c]) > Math.abs(a[piv * 6 + c])) piv = r;
            if (Math.abs(a[piv * 6 + c]) < 1e-9f) return false;
            if (piv != c) {
                for (int k = 0; k < 6; k++) { float t = a[c * 6 + k]; a[c * 6 + k] = a[piv * 6 + k]; a[piv * 6 + k] = t; }
                float t = b[c]; b[c] = b[piv]; b[piv] = t;
            }
            for (int r = c + 1; r < 6; r++) {
                float f = a[r * 6 + c] / a[c * 6 + c];
                if (f == 0) continue;
                for (int k = c; k < 6; k++) a[r * 6 + k] -= f * a[c * 6 + k];
                b[r] -= f * b[c];
            }
        }
        for (int r = 5; r >= 0; r--) {
            float s = b[r];
            for (int k = r + 1; k < 6; k++) s -= a[r * 6 + k] * x[k];
            x[r] = s / a[r * 6 + r];
        }
        return true;
    }

    // ------------------------------------------------------------------ the body in the world
    /**
     * A point of the world in the model space of a player drawn at pos (feet) with body yaw bodyYaw (degrees), as the
     * player renderer places the model (turned, flipped, scaled .9375, lifted 1.501) and IcemanBody scales it (pixels).
     */
    static Vector3f toModel(Vec3 pos, float bodyYaw, Vec3 world, Vector3f out) {
        double a = Math.toRadians(bodyYaw - 180), c = Math.cos(a), s = Math.sin(a);
        double wx = world.x - pos.x, wy = world.y - pos.y, wz = world.z - pos.z;
        double x = wx * c + wz * s, z = -wx * s + wz * c;
        return out.set((float) (-x / .9375 * 16), (float) ((-wy / .9375 + 1.501) * 16), (float) (z / .9375 * 16));
    }
    /**
     * A point of the world in a frame placed at origin, facing yaw (degrees, as a body's), flipped like a model (+y down,
     * -z ahead), scale pixels a block (no lift): his own levelled view of the planted sword.
     */
    static Vector3f toFrame(Vec3 origin, float yaw, float scale, Vec3 world, Vector3f out) {
        double a = Math.toRadians(yaw - 180), c = Math.cos(a), s = Math.sin(a);
        double wx = world.x - origin.x, wy = world.y - origin.y, wz = world.z - origin.z;
        double x = wx * c + wz * s, z = -wx * s + wz * c;
        return out.set((float) (-x * scale), (float) (-wy * scale), (float) (z * scale));
    }
    /** A world direction in that model space (unit stays unit). */
    static Vector3f dirToModel(float bodyYaw, Vec3 d, Vector3f out) {
        double a = Math.toRadians(bodyYaw - 180), c = Math.cos(a), s = Math.sin(a);
        double x = d.x * c + d.z * s, z = -d.x * s + d.z * c;
        return out.set((float) -x, (float) -d.y, (float) z);
    }
}
