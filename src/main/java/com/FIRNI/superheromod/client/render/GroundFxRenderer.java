package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Random;

/**
 * Yer efektleri cizici — lazer renderer'indan bagimsiz.
 *
 * Lazer: parlak, additive, ic ice silindirik katmanlar.
 * Burasi: MAT koyu volkanik kaya (alpha blend, opak) + aralarindan sizan
 * kizil catlak. Boylece "lazer izi" gibi degil, kaya gibi duruyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public class GroundFxRenderer {

    // Koyu volkanik kaya tonlari (mat, opak)
    private static final float[] ROCK_DARK = {0.13f, 0.10f, 0.11f};
    private static final float[] ROCK_MID = {0.22f, 0.15f, 0.15f};
    // Kayanin arasindan sizan kor
    private static final float[] EMBER = {1.00f, 0.22f, 0.06f};

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientGroundFxData.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<ClientGroundFxData.Fx> list = ClientGroundFxData.get();
        if (list.isEmpty()) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float time = System.nanoTime() / 1_000_000_000f;

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().last().pose().identity();
        RenderSystem.getModelViewStack().last().normal().identity();
        RenderSystem.applyModelViewMatrix();

        // 1) Kaya govdeleri — MAT, opak, alpha blend
        drawRocks(matrix, list, time);
        // 2) Kor catlaklar — additive, kayanin uzerine
        drawEmbers(matrix, list, time);

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        pose.popPose();
    }

    private static void drawRocks(Matrix4f matrix, List<ClientGroundFxData.Fx> list, float time) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        synchronized (list) {
            for (ClientGroundFxData.Fx fx : list) {
                switch (fx.kind) {
                    case MAGMA_ROCK -> rockChunk(matrix, buf, fx, time, false);
                    case SHARD -> shard(matrix, buf, fx, false);
                    case CONE -> { /* koni sadece kor gecte */ }
                }
            }
        }

        tes.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    private static void drawEmbers(Matrix4f matrix, List<ClientGroundFxData.Fx> list, float time) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        synchronized (list) {
            for (ClientGroundFxData.Fx fx : list) {
                switch (fx.kind) {
                    case MAGMA_ROCK -> rockChunk(matrix, buf, fx, time, true);
                    case SHARD -> shard(matrix, buf, fx, true);
                    case CONE -> cone(matrix, buf, fx, time);
                }
            }
        }

        tes.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
    }

    /**
     * Patlamaya hazir volkanik yumru: yerden yukselen duzensiz kaya kutlesi.
     * emberPass=false -> mat koyu kaya, true -> aralarindaki kizil catlak.
     */
    private static void rockChunk(Matrix4f matrix, BufferBuilder buf,
                                  ClientGroundFxData.Fx fx, float time, boolean emberPass) {
        Random rng = new Random(fx.seed);
        float charge = Mth.clamp(fx.charge, 0f, 1f);

        // Referanstaki gibi: dikey diken DEGIL, yerden CAPRAZ cikan yatik
        // kaya levhalari. Aralarindan magma sizar.
        int slabs = 2 + rng.nextInt(2);

        for (int i = 0; i < slabs; i++) {
            // Her levha bir kosegen yone dogru yatik yukselir
            double ang = rng.nextDouble() * Math.PI * 2;
            Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            Vec3 side = new Vec3(-out.z, 0, out.x);

            double w = fx.size * (0.45 + rng.nextDouble() * 0.35);
            double len = fx.size * (0.85 + rng.nextDouble() * 0.75) * (0.5 + 0.5 * charge);
            // Yatiklik: yukselirken disa dogru egilir
            double lift = len * (0.45 + 0.35 * charge);

            Vec3 b0 = fx.pos.add(side.scale(w)).add(out.scale(fx.size * 0.15));
            Vec3 b1 = fx.pos.subtract(side.scale(w)).add(out.scale(fx.size * 0.15));
            Vec3 t0 = b0.add(out.scale(len)).add(0, lift, 0);
            Vec3 t1 = b1.add(out.scale(len)).add(0, lift, 0);

            if (emberPass) {
                // Levhanin tabanindan sizan kor — nabiz atar
                float pulse = 0.5f + 0.5f * Mth.sin(time * 3.5f + i * 1.7f + (float) (fx.seed % 7));
                float a = (0.20f + 0.70f * charge) * pulse * fx.life();

                Vec3 e0 = b0.add(0, 0.03, 0);
                Vec3 e1 = b1.add(0, 0.03, 0);
                Vec3 e2 = e1.add(out.scale(len * 0.35)).add(0, lift * 0.3, 0);
                Vec3 e3 = e0.add(out.scale(len * 0.35)).add(0, lift * 0.3, 0);
                quad(buf, matrix, e0, e1, e2, e3, EMBER, a);
            } else {
                float[] col = (i % 2 == 0) ? ROCK_DARK : ROCK_MID;
                float a = 0.95f * fx.life();
                // Levhanin ust yuzu + iki yan yuzu
                quad(buf, matrix, b0, b1, t1, t0, col, a);
                tri(buf, matrix, b0, t0, fx.pos.add(0, 0.02, 0), ROCK_DARK, a);
                tri(buf, matrix, b1, t1, fx.pos.add(0, 0.02, 0), ROCK_DARK, a);
            }
        }

        // Zeminde yayilan magma catlagi — levhalarin cevresine
        if (emberPass && charge > 0.25f) {
            float a = 0.28f * charge * fx.life();
            for (int i = 0; i < 3; i++) {
                double ang = rng.nextDouble() * Math.PI * 2;
                Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Vec3 from = fx.pos.add(0, 0.02, 0);
                Vec3 to = from.add(d.scale(fx.size * (1.0 + rng.nextDouble())));
                edge(buf, matrix, from, to, 0.05 + 0.04 * charge, EMBER, a);
            }
        }
    }

    /** Patlamada yanlardan firlayan sivri kaya parcasi. */
    private static void shard(Matrix4f matrix, BufferBuilder buf,
                              ClientGroundFxData.Fx fx, boolean emberPass) {
        Random rng = new Random(fx.seed);
        float t = 1f - fx.life();

        Vec3 dir = fx.dir;
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = dir.cross(up);
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();

        // Yay cizerek disa dogru firlar
        Vec3 pos = fx.pos
                .add(dir.scale(fx.size * 2.2 * t))
                .add(0, fx.size * (2.0 * t - 2.6 * t * t), 0);

        double w = fx.size * 0.28;
        double len = fx.size * (0.7 + rng.nextDouble() * 0.5);

        Vec3 tip = pos.add(dir.scale(len));
        Vec3 a = pos.add(side.scale(w));
        Vec3 b = pos.subtract(side.scale(w));
        Vec3 c = pos.add(0, w * 1.4, 0);

        if (emberPass) {
            float alpha = 0.5f * fx.life();
            tri(buf, matrix, a, b, tip, EMBER, alpha * 0.5f);
        } else {
            float alpha = 0.95f * Math.min(1f, fx.life() * 2f);
            tri(buf, matrix, a, b, tip, ROCK_DARK, alpha);
            tri(buf, matrix, a, c, tip, ROCK_MID, alpha);
            tri(buf, matrix, b, c, tip, ROCK_DARK, alpha);
        }
    }

    /** Ultinin gidecegi alani gosteren yer konisi (ok/hiza gostergesi). */
    private static void cone(Matrix4f matrix, BufferBuilder buf,
                             ClientGroundFxData.Fx fx, float time) {
        Vec3 dir = fx.dir;
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();

        double length = fx.size;
        double halfWidth = length * 0.22;

        // Kenar cizgileri — koni acikligi
        Vec3 apex = fx.pos.add(0, 0.03, 0);
        Vec3 farL = apex.add(dir.scale(length)).add(side.scale(halfWidth));
        Vec3 farR = apex.add(dir.scale(length)).subtract(side.scale(halfWidth));

        float base = 0.75f * fx.life();

        // Koni ic dolgusu — silüet hissi versin
        quad(buf, matrix, apex, farL, farR, apex, EMBER, base * 0.10f);

        edge(buf, matrix, apex, farL, 0.11, EMBER, base);
        edge(buf, matrix, apex, farR, 0.11, EMBER, base);
        edge(buf, matrix, farL, farR, 0.11, EMBER, base * 0.8f);

        // Icinde ilerleyen oklar — mesafe ve hiza gostergesi
        int arrows = 5;
        for (int i = 1; i <= arrows; i++) {
            double p = i / (double) (arrows + 1);
            // Oklar hedefe dogru akar
            double flow = (p + time * 0.35) % 1.0;
            double d = length * flow;
            double w = halfWidth * flow;

            Vec3 c = apex.add(dir.scale(d));
            Vec3 l = c.add(side.scale(w)).subtract(dir.scale(w * 0.85));
            Vec3 r = c.subtract(side.scale(w)).subtract(dir.scale(w * 0.85));

            float a = base * (float) (1.0 - Math.abs(flow - 0.5) * 0.8) * 1.6f;
            edge(buf, matrix, l, c, 0.10, EMBER, a);
            edge(buf, matrix, r, c, 0.10, EMBER, a);
        }
    }

    // --- Ilkel cizim yardimcilari ---

    private static void edge(BufferBuilder buf, Matrix4f m, Vec3 a, Vec3 b,
                             double width, float[] col, float alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0E-6) return;
        Vec3 side = d.normalize().cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-6) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width);

        quad(buf, m, a.add(side), b.add(side), b.subtract(side), a.subtract(side), col, alpha);
    }

    private static void quad(BufferBuilder buf, Matrix4f m,
                             Vec3 a, Vec3 b, Vec3 c, Vec3 d, float[] col, float alpha) {
        vert(buf, m, a, col, alpha);
        vert(buf, m, b, col, alpha);
        vert(buf, m, c, col, alpha);
        vert(buf, m, d, col, alpha);
    }

    /** Ucgen — QUADS modunda son kose tekrarlanir. */
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
