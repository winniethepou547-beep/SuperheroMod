package net.minecraft.client.renderer;
import net.minecraft.resources.ResourceLocation;
public class RenderType { public final String name; public RenderType(String n) { name = n; }
  public static RenderType entityTranslucent(ResourceLocation r) { return new RenderType(r.path); }
  public static RenderType eyes(ResourceLocation r) { return new RenderType("glint"); } }
