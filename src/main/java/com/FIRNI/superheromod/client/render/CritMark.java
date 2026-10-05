package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The critical-hit mark: a small comic-book burst (an orange star of jagged spikes, the longest reaching up to the left,
 * shaded red-orange to orange, a black outline and a hard black shadow) that pops out beside the one struck when a
 * blow lands on someone dazed (seeing stars), wobbles, drifts up a little and fades. Seen by everyone, in the world.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class CritMark {
    private record Mark(Vec3 at, float start, float seed) {}
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final float LIFE = 18, SIZE = .3f;
    /** The burst's outline (x right, y up, about its middle): spikes all round, the longest up to the left. */
    private static final float[] SHAPE = {
            -1.0f, .78f, -.18f, .3f, .02f, .9f, .22f, .36f, .46f, .78f, .5f, .26f, .92f, .52f, .64f, .0f,
            .62f, -.56f, .1f, -.6f, -.16f, -.36f, -.78f, -.46f, -.36f, -.06f, -.86f, .12f, -.3f, .32f};
    private static final float CX = -.02f, CY = .08f;

    private CritMark() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }

    /** A critical landed on the body round this point (its middle). */
    public static void show(Vec3 at) {
        MARKS.add(new Mark(at, now(), (float) Math.random()));
        if (MARKS.size() > 24) MARKS.remove(0);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || MARKS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { MARKS.clear(); return; }
        float t = now();
        MARKS.removeIf(m -> t - m.start() > LIFE);
        if (MARKS.isEmpty()) return;
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        Vec3 right = new Vec3(rr.x, rr.y, rr.z), up = new Vec3(uu.x, uu.y, uu.z);
        var buffers = FilmFx.batched();
        try {
            VertexConsumer v = buffers.getBuffer(FilmFx.SOFT);
            Matrix4f m = p.last().pose();
            for (Mark mark : MARKS) {
                float age = t - mark.start();
                // Pops out past its size and settles; wobbles; drifts up; fades at the end.
                float pop = age < 2.5f ? Mth.sin(age / 2.5f * Mth.HALF_PI) * 1.3f : 1.3f - .3f * Mth.clamp((age - 2.5f) / 2.5f, 0, 1);
                float alpha = 1 - Mth.clamp((age - (LIFE - 5)) / 5, 0, 1);
                float spin = .25f * Mth.sin(age * .7f + mark.seed() * 6) * (float) Math.exp(-age / 8) - .12f;
                Vec3 at = mark.at().add(right.scale(.45 + .1 * mark.seed())).add(up.scale(.45 + .02 * age));
                float size = SIZE * pop;
                // Shadow (down and to the right), the black outline, then the burst itself.
                burst(v, m, at.add(right.scale(.06 * pop)).subtract(up.scale(.06 * pop)), right, up, size * 1.12f, spin, 0x000000, 0x000000, .8f * alpha);
                burst(v, m, at, right, up, size * 1.14f, spin, 0x0a0a0a, 0x0a0a0a, alpha);
                burst(v, m, at, right, up, size, spin, 0xc8452a, 0xf59a3e, alpha);
            }
            buffers.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /** The burst facing the camera: a fan from its middle; coloured from red-orange (upper left) to orange (lower right). */
    private static void burst(VertexConsumer v, Matrix4f m, Vec3 at, Vec3 right, Vec3 up, float size, float spin, int from, int to, float alpha) {
        int n = SHAPE.length / 2;
        float cs = Mth.cos(spin), sn = Mth.sin(spin);
        Vec3 mid = place(at, right, up, CX, CY, size, cs, sn);
        int midCol = mix(from, to, .5f);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = SHAPE[i * 2], ay = SHAPE[i * 2 + 1], bx = SHAPE[j * 2], by = SHAPE[j * 2 + 1];
            Vec3 a = place(at, right, up, ax, ay, size, cs, sn), b = place(at, right, up, bx, by, size, cs, sn);
            put(v, m, mid, midCol, alpha);
            put(v, m, a, mix(from, to, shade(ax, ay)), alpha);
            put(v, m, b, mix(from, to, shade(bx, by)), alpha);
            put(v, m, b, mix(from, to, shade(bx, by)), alpha);
        }
    }
    /** 0 at the upper left, 1 at the lower right. */
    private static float shade(float x, float y) { return Mth.clamp((x - y + 1.6f) / 3.2f, 0, 1); }
    private static Vec3 place(Vec3 at, Vec3 right, Vec3 up, float x, float y, float size, float cs, float sn) {
        float rx = x * cs - y * sn, ry = x * sn + y * cs;
        return at.add(right.scale(rx * size)).add(up.scale(ry * size));
    }
    private static int mix(int a, int b, float k) {
        int r = (int) Mth.lerp(k, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(k, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }
    private static void put(VertexConsumer v, Matrix4f m, Vec3 p, int rgb, float a) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, Mth.clamp(a, 0, 1)).endVertex();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { MARKS.clear(); }
}
