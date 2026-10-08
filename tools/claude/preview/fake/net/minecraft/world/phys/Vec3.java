package net.minecraft.world.phys;
public class Vec3 { public static final Vec3 ZERO = new Vec3(0, 0, 0); public final double x, y, z;
  public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
  public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); } public Vec3 add(double a, double b, double c) { return new Vec3(x + a, y + b, z + c); }
  public Vec3 subtract(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); } public Vec3 subtract(double a, double b, double c) { return new Vec3(x - a, y - b, z - c); }
  public Vec3 scale(double k) { return new Vec3(x * k, y * k, z * k); }
  public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
  public Vec3 cross(Vec3 o) { return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
  public double lengthSqr() { return x * x + y * y + z * z; } public double length() { return Math.sqrt(lengthSqr()); }
  public Vec3 normalize() { double l = length(); return l < 1e-4 ? ZERO : new Vec3(x / l, y / l, z / l); }
  public Vec3 lerp(Vec3 o, double k) { return new Vec3(x + (o.x - x) * k, y + (o.y - y) * k, z + (o.z - z) * k); }
  public double distanceTo(Vec3 o) { return subtract(o).length(); } public double distanceToSqr(Vec3 o) { return subtract(o).lengthSqr(); }
}
