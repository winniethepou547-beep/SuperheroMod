package com.FIRNI.superheromod.client.render.iceman;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
public final class IcemanArmory {
    public static void inHand(IceMesh.Ctx c, PoseStack p, int weapon, float form, float size, float crack, float time) {
        float len = length(weapon, size) * form;
        IceMesh.crystal(c, new Vec3(0, 0, 2), new Vec3(0, 0, -1), len + 2, 1.0f * size, 6, weapon * 7 + 3, IceMesh.CLEAR, 1, .5f);
    }
    public static float length(int weapon, float size) { return (weapon == 1 ? 26 : weapon == 0 ? 15 : 20) * size; }
}
