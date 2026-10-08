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
    public static void main(String[] a) throws Exception {
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
