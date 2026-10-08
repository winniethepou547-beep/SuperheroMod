package net.minecraft.world.phys;
public class Vec3 {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public final double x, y, z;
    public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public Vec3 add(Vec3 o) { return null; }
    public Vec3 add(double x, double y, double z) { return null; }
    public Vec3 subtract(Vec3 o) { return null; }
    public Vec3 subtract(double x, double y, double z) { return null; }
    public Vec3 scale(double s) { return null; }
    public Vec3 multiply(double x, double y, double z) { return null; }
    public Vec3 multiply(Vec3 o) { return null; }
    public Vec3 normalize() { return null; }
    public Vec3 cross(Vec3 o) { return null; }
    public double dot(Vec3 o) { return 0; }
    public double length() { return 0; }
    public double lengthSqr() { return 0; }
    public double horizontalDistance() { return 0; }
    public double horizontalDistanceSqr() { return 0; }
    public double distanceTo(Vec3 o) { return 0; }
    public double distanceToSqr(Vec3 o) { return 0; }
    public Vec3 lerp(Vec3 o, double k) { return null; }
    public Vec3 reverse() { return null; }
    public Vec3 yRot(float a) { return null; }
    public Vec3 xRot(float a) { return null; }
    public static Vec3 directionFromRotation(float p, float y) { return null; }
    public static Vec3 atCenterOf(Object o) { return null; }
    public static Vec3 atBottomCenterOf(Object o) { return null; }
}
