package com.mojang.blaze3d.vertex;
import org.joml.Quaternionf; import org.joml.Matrix4f; import org.joml.Matrix3f;
import java.util.ArrayDeque;
public class PoseStack {
  public static final class Pose { final Matrix4f m; final Matrix3f n; Pose(Matrix4f m, Matrix3f n) { this.m = m; this.n = n; } public Matrix4f pose() { return m; } public Matrix3f normal() { return n; } }
  private final ArrayDeque<Pose> s = new ArrayDeque<>();
  public PoseStack() { s.add(new Pose(new Matrix4f(), new Matrix3f())); }
  public void pushPose() { Pose p = s.getLast(); s.addLast(new Pose(new Matrix4f(p.m), new Matrix3f(p.n))); }
  public void popPose() { s.removeLast(); }
  public Pose last() { return s.getLast(); }
  public void translate(double x, double y, double z) { s.getLast().m.translate((float) x, (float) y, (float) z); }
  public void translate(float x, float y, float z) { s.getLast().m.translate(x, y, z); }
  public void scale(float x, float y, float z) { Pose p = s.getLast(); p.m.scale(x, y, z);
    if (x == y && y == z) { if (x < 0) p.n.scale(-1); } else p.n.scale(1 / x, 1 / y, 1 / z); }
  public void mulPose(Quaternionf q) { Pose p = s.getLast(); p.m.rotate(q); p.n.rotate(q); }
}
