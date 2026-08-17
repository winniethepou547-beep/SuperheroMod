package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.SandWallSyncPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Random;

/**
 * Kum duvarlarini cizer.
 *
 * Onizleme saydam ve soluk; gercek duvar opak kum tonlarinda. Dikenler dis
 * yuzeyden cikan piramitler olarak ciziliyor ve sadece firlatma hazirliginda
 * gorunuyor.
 *
 * Bu gecici bir cizim: model asamasinda yerini sand_wall.bbmodel alacak.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class SandWallRenderer {

    private static final float[] SAND_LIGHT = {0.86f, 0.75f, 0.50f};
    private static final float[] SAND_MID = {0.72f, 0.61f, 0.39f};
    private static final float[] SAND_DARK = {0.52f, 0.43f, 0.27f};
    private static final float[] PREVIEW = {0.95f, 0.85f, 0.55f};

    private SandWallRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<SandWallSyncPacket.Entry> walls = ClientSandWallData.get();
        if (walls.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().last().pose().identity();
        RenderSystem.getModelViewStack().last().normal().identity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (SandWallSyncPacket.Entry wall : walls) {
            drawWall(buf, matrix, wall);
        }

        tes.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        pose.popPose();
    }

    private static void drawWall(BufferBuilder buf, Matrix4f m, SandWallSyncPacket.Entry wall) {
        double yaw = Math.toRadians(wall.yaw());
        // Dis yuzey normali ve duvarin yan ekseni
        Vec3 facing = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 side = new Vec3(facing.z, 0, -facing.x);
        Vec3 up = new Vec3(0, 1, 0);

        boolean preview = wall.preview();
        float alpha = preview ? 0.32f : 0.95f;

        // Hasar aldikca koyulasip soluklasir
        float health = Mth.clamp(wall.health(), 0f, 1f);
        float[] body = preview ? PREVIEW : (health > 0.5f ? SAND_MID : SAND_DARK);
        float[] edge = preview ? PREVIEW : SAND_LIGHT;

        Vec3 c = wall.center();
        Vec3 hw = side.scale(wall.halfWidth());
        Vec3 hh = up.scale(wall.halfHeight());
        Vec3 hd = facing.scale(wall.halfDepth());

        box(buf, m, c, hw, hh, hd, body, alpha);

        // Kenar vurgusu — duvarin sinirlari belli olsun
        box(buf, m, c, hw.scale(1.02), hh.scale(0.06), hd.scale(1.02), edge, alpha * 0.8f);
        box(buf, m, c.add(0, wall.halfHeight() * 0.95, 0),
                hw.scale(1.02), hh.scale(0.05), hd.scale(1.02), edge, alpha * 0.8f);

        float spike = wall.spike();
        if (spike > 0.01f) {
            drawSpikes(buf, m, wall, facing, side, up, spike, alpha);
        }
    }

    /** Dis yuzeyden cikan dikenler — sadece firlatma hazirliginda gorunur. */
    private static void drawSpikes(BufferBuilder buf, Matrix4f m, SandWallSyncPacket.Entry wall,
                                   Vec3 facing, Vec3 side, Vec3 up, float spike, float alpha) {
        Random rng = new Random(Double.doubleToLongBits(wall.center().x) ^ 0x5A17D);

        int cols = 5;
        int rows = 3;
        double maxLen = 1.1 * spike;

        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                double u = (col + 0.5) / cols * 2.0 - 1.0;
                double v = (r + 0.5) / rows * 2.0 - 1.0;

                // Duzenli izgara yerine hafif dagilim — kum dikeni duzgun olmaz
                u += (rng.nextDouble() - 0.5) * 0.18;
                v += (rng.nextDouble() - 0.5) * 0.25;

                Vec3 base = wall.center()
                        .add(side.scale(u * wall.halfWidth() * 0.85))
                        .add(up.scale(v * wall.halfHeight() * 0.8))
                        .add(facing.scale(wall.halfDepth()));

                double len = maxLen * (0.65 + rng.nextDouble() * 0.55);
                double thick = 0.16 + rng.nextDouble() * 0.10;

                Vec3 tip = base.add(facing.scale(len));

                Vec3 a = base.add(side.scale(thick)).add(up.scale(thick));
                Vec3 b = base.add(side.scale(thick)).add(up.scale(-thick));
                Vec3 d = base.add(side.scale(-thick)).add(up.scale(-thick));
                Vec3 e = base.add(side.scale(-thick)).add(up.scale(thick));

                tri(buf, m, a, b, tip, SAND_LIGHT, alpha);
                tri(buf, m, b, d, tip, SAND_MID, alpha);
                tri(buf, m, d, e, tip, SAND_DARK, alpha);
                tri(buf, m, e, a, tip, SAND_MID, alpha);
            }
        }
    }

    /** Merkez + üc yari eksenden kutu. */
    private static void box(BufferBuilder buf, Matrix4f m, Vec3 c,
                            Vec3 hw, Vec3 hh, Vec3 hd, float[] col, float alpha) {
        Vec3 p000 = c.subtract(hw).subtract(hh).subtract(hd);
        Vec3 p100 = c.add(hw).subtract(hh).subtract(hd);
        Vec3 p110 = c.add(hw).add(hh).subtract(hd);
        Vec3 p010 = c.subtract(hw).add(hh).subtract(hd);
        Vec3 p001 = c.subtract(hw).subtract(hh).add(hd);
        Vec3 p101 = c.add(hw).subtract(hh).add(hd);
        Vec3 p111 = c.add(hw).add(hh).add(hd);
        Vec3 p011 = c.subtract(hw).add(hh).add(hd);

        quad(buf, m, p001, p101, p111, p011, col, alpha);          // dis yuz
        quad(buf, m, p100, p000, p010, p110, col, alpha);          // ic yuz
        quad(buf, m, p000, p001, p011, p010, darker(col), alpha);  // yan
        quad(buf, m, p101, p100, p110, p111, darker(col), alpha);  // yan
        quad(buf, m, p010, p011, p111, p110, col, alpha);          // ust
        quad(buf, m, p000, p100, p101, p001, darker(col), alpha);  // alt
    }

    private static float[] darker(float[] col) {
        return new float[]{col[0] * 0.78f, col[1] * 0.78f, col[2] * 0.78f};
    }

    private static void quad(BufferBuilder buf, Matrix4f m,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float alpha) {
        vert(buf, m, a, col, alpha);
        vert(buf, m, b, col, alpha);
        vert(buf, m, c, col, alpha);
        vert(buf, m, d, col, alpha);
    }

    private static void tri(BufferBuilder buf, Matrix4f m,
                            Vec3 a, Vec3 b, Vec3 c, float[] col, float alpha) {
        vert(buf, m, a, col, alpha);
        vert(buf, m, b, col, alpha);
        vert(buf, m, c, col, alpha);
        vert(buf, m, c, col, alpha);
    }

    private static void vert(BufferBuilder buf, Matrix4f m, Vec3 p, float[] col, float alpha) {
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(col[0], col[1], col[2], Mth.clamp(alpha, 0f, 1f)).endVertex();
    }
}
