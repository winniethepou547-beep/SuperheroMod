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
 * Burasi: hedef alinan bloklarin USTUNE oturan %80 opak siyah kaplama ve
 * uzerinde buyuyen catlaklar. Yandan cikan geometri YOK — her sey zemine
 * yapisik duz kalir; patlamayi bloklarin kendisi yapar.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public class GroundFxRenderer {

    /** Blok ustunu kaplayan siyah. */
    private static final float[] SCORCH = {0.02f, 0.01f, 0.01f};
    /** Kaplamayi kaplayan siyahin opakligi. */
    private static final float SCORCH_ALPHA = 0.80f;
    /** Catlak / koni kor rengi. */
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

        // 1) Siyah kaplama — mat, %80 opak, alpha blend
        drawScorch(matrix, list);
        // 2) Catlaklar ve nisan konisi — additive, kaplamanin uzerine
        drawEmbers(matrix, list, time);

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        pose.popPose();
    }

    private static void drawScorch(Matrix4f matrix, List<ClientGroundFxData.Fx> list) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        synchronized (list) {
            for (ClientGroundFxData.Fx fx : list) {
                if (fx.kind == ClientGroundFxData.Kind.SCORCH) scorchQuad(matrix, buf, fx);
            }
        }

        tes.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
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
                    case CRACK -> crackLine(matrix, buf, fx, time);
                    case CONE -> cone(matrix, buf, fx, time);
                    case SCORCH -> { /* kaplama sadece ilk gecte */ }
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
     * Hedef alinan blogun ust yuzune oturan siyah kaplama.
     * Blok boyutunda ve eksene hizali cizilir; boylece yan yana bloklar
     * tek parca kesintisiz siyah bir alan olusturur.
     */
    private static void scorchQuad(Matrix4f matrix, BufferBuilder buf,
                                   ClientGroundFxData.Fx fx) {
        double h = fx.size * 0.5;
        // Giris/cikisi yumusat, ortada tam %80'de kalsin
        float a = SCORCH_ALPHA * Math.min(1f, fx.life() * 4f);

        Vec3 c = fx.pos.add(0, 0.015, 0);
        quad(buf, matrix,
                c.add(-h, 0, -h), c.add(h, 0, -h), c.add(h, 0, h), c.add(-h, 0, h),
                SCORCH, a);
    }

    /**
     * Catlak agindan tek bir dal. Blok sinirlarini tanimaz — sunucu tum
     * kaplama alani icin dallanan tek bir ag kurar, buraya o agin bir parcasi
     * gelir. Duz cizgi degil: boyunca kirilarak ilerler.
     *
     * pos = baslangic, dir = yon, size = uzunluk, charge = catlak seviyesi.
     */
    private static void crackLine(Matrix4f matrix, BufferBuilder buf,
                                  ClientGroundFxData.Fx fx, float time) {
        Random rng = new Random(fx.seed);
        float charge = Mth.clamp(fx.charge, 0f, 1f);
        float life = Math.min(1f, fx.life() * 4f);

        Vec3 dir = fx.dir;
        if (dir.lengthSqr() < 1.0E-6) return;
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        if (side.lengthSqr() < 1.0E-6) return;
        side = side.normalize();

        // Sarj yukseldikce parlaklik ve nabiz frekansi artar
        float pulse = 0.60f + 0.40f * Mth.sin(
                time * (2.0f + 5.5f * charge) + (float) (fx.seed % 19));
        float alpha = (0.22f + 0.78f * charge) * pulse * life;
        double width = 0.030 + 0.055 * charge;

        // Uzunluga gore parcalanir; her kirilma noktasi yana sapar
        int segs = Math.max(2, (int) Math.ceil(fx.size / 0.55));
        double wobble = 0.16 + 0.10 * rng.nextDouble();

        Vec3 prev = fx.pos;
        for (int s = 1; s <= segs; s++) {
            double t = s / (double) segs;
            double off = (rng.nextDouble() - 0.5) * wobble * 2.0;
            // Uc nokta tam hedefte bitsin ki dallar birbirine baglansin
            if (s == segs) off = 0;

            Vec3 next = fx.pos.add(dir.scale(fx.size * t)).add(side.scale(off));
            edge(buf, matrix, prev, next, width, EMBER, alpha);
            prev = next;
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

    private static void vert(BufferBuilder buf, Matrix4f m, Vec3 p, float[] col, float alpha) {
        buf.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(col[0], col[1], col[2], Mth.clamp(alpha, 0f, 1f)).endVertex();
    }
}
