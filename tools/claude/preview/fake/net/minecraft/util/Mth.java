package net.minecraft.util;
public class Mth {
  public static final float PI = (float) Math.PI, TWO_PI = PI * 2, HALF_PI = PI / 2, DEG_TO_RAD = PI / 180, RAD_TO_DEG = 180 / PI;
  public static float sin(float a) { return (float) Math.sin(a); } public static float cos(float a) { return (float) Math.cos(a); }
  public static float sqrt(float a) { return (float) Math.sqrt(a); }
  public static float clamp(float v, float a, float b) { return v < a ? a : Math.min(v, b); }
  public static double clamp(double v, double a, double b) { return v < a ? a : Math.min(v, b); }
  public static int clamp(int v, int a, int b) { return v < a ? a : Math.min(v, b); }
  public static float lerp(float k, float a, float b) { return a + (b - a) * k; }
  public static double lerp(double k, double a, double b) { return a + (b - a) * k; }
  public static float wrapDegrees(float a) { float f = a % 360; if (f >= 180) f -= 360; if (f < -180) f += 360; return f; }
  public static double wrapDegrees(double a) { double f = a % 360; if (f >= 180) f -= 360; if (f < -180) f += 360; return f; }
  public static float rotLerp(float k, float a, float b) { return a + k * wrapDegrees(b - a); }
  public static int floor(double d) { return (int) Math.floor(d); }
}
