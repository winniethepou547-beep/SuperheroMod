package com.mojang.math;
import org.joml.Quaternionf; import org.joml.Matrix4f; import org.joml.Matrix3f;
public interface Axis { Quaternionf rotation(float a);
  default Quaternionf rotationDegrees(float a) { return rotation((float) java.lang.Math.toRadians(a)); }
  Axis XP = a -> new Quaternionf().rotationX(a); Axis YP = a -> new Quaternionf().rotationY(a); Axis ZP = a -> new Quaternionf().rotationZ(a); }
