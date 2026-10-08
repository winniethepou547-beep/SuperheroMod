package com.FIRNI.superheromod.client.render.iceman;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import java.io.PrintWriter;
public class Dump {
    static PrintWriter w;
    static String mode = "ice";
    public static void dump(String name, Pose pose, float viewYaw, float pitch) { dump(name, pose, viewYaw, pitch, 4.0f, -1.12f); }
    public static void dump(String name, Pose pose, float viewYaw, float pitch, float dist, float h) {
        w.println("#" + name);
        PoseStack p = new PoseStack();
        p.mulPose(Axis.XP.rotation(pitch));
        p.translate(0, h, -dist);
        p.mulPose(Axis.YP.rotationDegrees(180 + viewYaw));
        p.scale(-1, -1, 1);
        p.translate(0, -1.501, 0);
        MultiBufferSource b = t -> (x, y, z, r, g, bl, a, u, v, o, l, nx, ny, nz) -> w.println((t.name.equals("glint") ? "G glint " : "I " + t.name + " ") + x + " " + y + " " + z + " " + r + " " + g + " " + bl + " " + a + " " + nx + " " + ny + " " + nz + " " + u + " " + v);
        IcemanBody.draw(p, b, 15728880, pose, 0, 0, 0, 0);
    }
    /** Ice formations on their own (world-like units: blocks), the camera dist away, looking at the origin from yaw. */
    public static void growth(String name, float t, float viewYaw) {
        w.println("#" + name);
        PoseStack p = new PoseStack();
        p.mulPose(Axis.XP.rotation(.3f));
        p.translate(0, -1.5, -5.4);
        p.mulPose(Axis.YP.rotationDegrees(viewYaw));
        MultiBufferSource b = t2 -> (x, y, z, r, g, bl, al, u, v, o, l, nx, ny, nz) -> w.println((t2.name.equals("glint") ? "G glint " : "I " + t2.name + " ") + x + " " + y + " " + z + " " + r + " " + g + " " + bl + " " + al + " " + nx + " " + ny + " " + nz + " " + u + " " + v);
        IceMesh.Ctx c = IceMesh.begin(p, b, 15728880);
        IceGrowth.cluster(c, -.9, 0, 0, 0, 1, 0, 1.3f, 7, 9, IceMesh.CLEAR, t, 1);
        IceGrowth.cluster(c, .8, 0, .4, .2, 1, 0, .7f, 21, 6, IceMesh.CLEAR, t * 1.1f, 1);
        float[] A = new float[30], B = new float[30];
        for (int i = 0; i < 6; i++) {
            float z0 = -1.2f + i * .5f, z1 = z0 + .5f;
            IceGrowth.section(1.9, .5 + i * .12, z0, 1, 0, 0, 0, 1, 0, 1f, .4f, 100 + i, Math.min(1, t * 1.5f), A);
            IceGrowth.section(1.9, .5 + (i + 1) * .12, z1, 1, 0, 0, 0, 1, 0, 1f, .4f, 101 + i, Math.min(1, t * 1.5f), B);
            IceGrowth.slab(c, A, B, IceMesh.CLEAR, 100 + i, 1, i == 0, i == 5);
        }
        c.end();
    }
    /**
     * A body deep frozen (FrostShellMesh) in a pose (rig: per part pivot x, y, z and rotations x, y, z, the vanilla
     * model's), the cold from way, age ticks after it began; broke >= 0: that many ticks after it broke.
     */
    static final float[][] BOXES = {{-4, -8, -4, 4, 0, 4}, {-4, 0, -2, 4, 12, 2}, {-3, -2, -2, 1, 10, 2}, {-1, -2, -2, 3, 10, 2}, {-2, 0, -2, 2, 12, 2}, {-2, 0, -2, 2, 12, 2}};
    public static void frozen(String name, float[] rig, float age, float broke, float viewYaw, double wx, double wz, boolean body) {
        w.println("#" + name);
        PoseStack p = new PoseStack();
        p.mulPose(Axis.XP.rotation(.12f));
        p.translate(0, -.98, -4.6);
        p.mulPose(Axis.YP.rotationDegrees(viewYaw));
        MultiBufferSource b = t2 -> (x, y, z, r, g, bl, al, u, v, o, l, nx, ny, nz) -> w.println((t2.name.equals("glint") ? "G glint " : "I " + t2.name + " ") + x + " " + y + " " + z + " " + r + " " + g + " " + bl + " " + al + " " + nx + " " + ny + " " + nz + " " + u + " " + v);
        org.joml.Matrix4f[] parts = new org.joml.Matrix4f[6];
        float[][] boxes = new float[6][];
        for (int i = 0; i < 6; i++) {
            org.joml.Matrix4f m = new org.joml.Matrix4f().rotateY((float) Math.toRadians(180)).scale(-.9375f, -.9375f, .9375f).translate(0, -1.501f, 0).scale(1 / 16f);
            int o = i * 6;
            m.translate(rig[o], rig[o + 1], rig[o + 2]).rotateZYX(rig[o + 5], rig[o + 4], rig[o + 3]);
            parts[i] = m; boxes[i] = BOXES[i];
        }
        FrostShellMesh.Shell s = new FrostShellMesh.Shell(4242, true, new net.minecraft.world.phys.Vec3(0, 0, 0), .6f, 1.8f, 0, 64, new net.minecraft.world.phys.Vec3(wx, 0, wz).normalize());
        float now = broke >= 0 ? 60 + broke : age;
        FrostShellMesh.build(s, parts, boxes, 1.8f, broke >= 0 ? 60 : age);
        IceMesh.Ctx c = IceMesh.begin(p, b, 15728880);
        if (body) {
            // The body inside (plain boxes), each part on its own matrix.
            IceMesh.Mat skin = new IceMesh.Mat(.55f, .42f, .33f, 1, 1, 0, .2f, .3f, IceMesh.CLOTH_KIND), cloth = new IceMesh.Mat(.2f, .3f, .55f, 1, 1, 0, .2f, .3f, IceMesh.CLOTH_KIND);
            for (int i = 0; i < 6; i++) {
                PoseStack q = new PoseStack();
                q.last().pose().set(new org.joml.Matrix4f(p.last().pose()).mul(parts[i]));
                q.last().normal().set(new org.joml.Matrix3f(q.last().pose()).invert().transpose());
                IceMesh.Ctx cb = IceMesh.begin(q, b, 15728880);
                float[] x = BOXES[i];
                IceMesh.box(cb, x[0], x[1], x[2], x[3], x[4], x[5], i == 0 || i >= 2 && i <= 3 ? skin : cloth, 1);
                cb.end();
            }
        }
        if (broke >= 0) {
            FrostShellMesh.startCracks(s, 60 - 20, false);
            s.broke = 60; s.how = 1;
            s.chunks = FrostShellMesh.chunks(s, 60);
            FrostShellMesh.pieces(c, s, now, false);
            FrostShellMesh.cracks(c, s, now);
        } else {
            FrostShellMesh.ground(c, s, now);
            FrostShellMesh.shell(c, s, now, false);
            if (age > 44) { if (s.cracks == null) FrostShellMesh.startCracks(s, 44, false); FrostShellMesh.cracks(c, s, now); }
        }
        c.end();
    }
    /** A running pose and a standing one (pivots and rotations like the vanilla model's). */
    static final float[] RUN = {0, 0, 0, -.15f, .3f, 0,  0, 0, 0, .1f, 0, 0,  -5, 2, 0, .9f, 0, .1f,  5, 2, 0, -1.0f, 0, -.1f,  -1.9f, 12, 0, -.8f, 0, 0,  1.9f, 12, 0, .7f, 0, 0};
    static final float[] STAND = {0, 0, 0, 0, 0, 0,  0, 0, 0, 0, 0, 0,  -5, 2, 0, 0, 0, .06f,  5, 2, 0, 0, 0, -.06f,  -1.9f, 12, 0, 0, 0, 0,  1.9f, 12, 0, 0, 0, 0};
    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[1].equals("frozen")) {
            w = new PrintWriter(a[0]);
            frozen("grow 6", RUN, 6, -1, 30, 0, -1, true);
            frozen("grow 11", RUN, 11, -1, 30, 0, -1, true);
            frozen("frozen front", RUN, 30, -1, 20, 0, -1, true);
            frozen("frozen back", RUN, 30, -1, 200, 0, -1, true);
            frozen("frozen side", STAND, 30, -1, 100, 0, -1, true);
            frozen("cracking", STAND, 52, -1, 20, 0, -1, true);
            frozen("broke 3", STAND, 0, 3, 20, 0, -1, false);
            frozen("broke 9", STAND, 0, 9, 20, 0, -1, false);
            w.close(); return;
        }
        if (a.length > 1) { w = new PrintWriter(a[0]); growth("t .25", .25f, 20); growth("t .55", .55f, 20); growth("t 1.2", 1.2f, 20); growth("t 1.2 side", 1.2f, 110); w.close(); return; }
        w = new PrintWriter(a[0]);
        Pose st = IcemanMotion.stance(0);
        dump("front", st, 0, 0);
        dump("three-quarter", st, 35, 0);
        dump("side", st, 90, 0);
        dump("face", st, 20, 0, 1.8f, -1.75f);
        dump("face side", st, 110, 0, 1.8f, -1.75f);
        dump("back", st, 180, 0);
        Pose armed = IcemanMotion.stance(0); IcemanMotion.armed(armed, 2); IcemanBody.WEAPON = 2;
        dump("armed sword", armed, 30, 0);
        IcemanBody.WEAPON = -1;
        IcemanBody.SHELL_COVER = .6f;
        dump("shell half", st, 30, 0);
        IcemanBody.SHELL_COVER = 0;
        dump("splash", st, 24, .1f, 3.3f, -1.62f);
        w.close();
    }
}
