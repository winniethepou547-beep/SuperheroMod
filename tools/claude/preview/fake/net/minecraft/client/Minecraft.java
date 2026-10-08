package net.minecraft.client;
import net.minecraft.world.phys.Vec3;
public class Minecraft { public static final Minecraft I = new Minecraft(); public static Minecraft getInstance() { return I; }
  public final GR gameRenderer = new GR();
  public static class GR { public Cam getMainCamera() { return new Cam(); } }
  public static class Cam { public Vec3 getPosition() { return Vec3.ZERO; } } }
