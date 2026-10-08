package com.mojang.blaze3d.vertex;
public interface VertexConsumer { void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int o, int l, float nx, float ny, float nz);
  float[] CUR = new float[6];
  default VertexConsumer vertex(double x, double y, double z) { CUR[0] = (float) x; CUR[1] = (float) y; CUR[2] = (float) z; return this; }
  default VertexConsumer color(float r, float g, float b, float a) { CUR[3] = r; CUR[4] = g; CUR[5] = b; return this; }
  default void endVertex() { vertex(CUR[0], CUR[1], CUR[2], CUR[3], CUR[4], CUR[5], 1, 0, 0, 0, 0, 0, 0, 0); } }
