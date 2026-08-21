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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/**
 * SOK DALGASI — merkezden disa dogru buyuyen BEYAZ HALKALAR.
 *
 * Partikul degil geometri: partikuller kare ve dagilir, buradaki halkanin
 * duzgun bir daire olarak buyumesi gerekiyor.
 *
 * Tek halka yerine IC ICE UC halka var, her biri kucuk bir gecikmeyle
 * baslar. Tek halka "bir cember buyudu" gibi durur; ardisik halkalar ise
 * darbenin yayildigi hissini veriyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ShockwaveRenderer {

    /** Ic ice halka sayisi. */
    private static final int RINGS = 3;
    /** Halkalar arasi gecikme (ilerleme cinsinden). */
    private static final float RING_DELAY = 0.16f;
    /** Daireyi olusturan kenar sayisi — yeterince yuvarlak gorunsun. */
    private static final int SEGMENTS = 56;

    private ShockwaveRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        List<ClientShockwaveData.Wave> waves = ClientShockwaveData.get();
        if (waves.isEmpty()) return;

        float partial = mc.getFrameTime();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.getModelViewStack().pushPose();
        RenderSystem.getModelViewStack().last().pose().identity();
        RenderSystem.getModelViewStack().last().normal().identity();
        RenderSystem.applyModelViewMatrix();

        // Toplamali karisim: halka isik gibi gorunsun, boya gibi degil
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tes = Tesselator.getInstance();
        BufferBuilder buf = tes.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        synchronized (waves) {
            for (ClientShockwaveData.Wave wave : waves) {
                drawWave(buf, matrix, wave, partial);
            }
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

    private static void drawWave(BufferBuilder buf, Matrix4f m,
                                 ClientShockwaveData.Wave wave, float partial) {
        float base = wave.progress(partial);

        for (int i = 0; i < RINGS; i++) {
            float t = base - i * RING_DELAY;
            if (t <= 0f || t >= 1f) continue;

            // Hizli baslar, sonra yavaslar — gercek bir darbe dalgasi gibi
            float eased = 1f - (1f - t) * (1f - t);
            float radius = wave.maxRadius * eased;

            // Buyudukce soner; ic halkalar biraz daha soluk
            float alpha = (1f - t) * (1f - t) * (i == 0 ? 0.85f : 0.55f);
            if (alpha < 0.01f) continue;

            // Halka genisligi disa dogru incelir
            float width = Mth.lerp(t, 0.55f, 1.6f);

            ring(buf, m, wave.center, radius, width, alpha);
        }
    }

    /** Zemine yatik, ic ve dis yaricapi olan bir halka bandi. */
    private static void ring(BufferBuilder buf, Matrix4f m, Vec3 center,
                             float radius, float width, float alpha) {
        double inner = Math.max(0.0, radius - width * 0.5);
        double outer = radius + width * 0.5;
        double y = center.y + 0.08;   // zemine hafif yukarida, z-fighting olmasin

        for (int s = 0; s < SEGMENTS; s++) {
            double a0 = (s / (double) SEGMENTS) * Math.PI * 2;
            double a1 = ((s + 1) / (double) SEGMENTS) * Math.PI * 2;

            double cos0 = Math.cos(a0), sin0 = Math.sin(a0);
            double cos1 = Math.cos(a1), sin1 = Math.sin(a1);

            vert(buf, m, center.x + cos0 * inner, y, center.z + sin0 * inner, alpha * 0.35f);
            vert(buf, m, center.x + cos1 * inner, y, center.z + sin1 * inner, alpha * 0.35f);
            vert(buf, m, center.x + cos1 * outer, y, center.z + sin1 * outer, alpha);
            vert(buf, m, center.x + cos0 * outer, y, center.z + sin0 * outer, alpha);
        }
    }

    private static void vert(BufferBuilder buf, Matrix4f m,
                             double x, double y, double z, float alpha) {
        buf.vertex(m, (float) x, (float) y, (float) z)
                .color(1.0f, 1.0f, 1.0f, Mth.clamp(alpha, 0f, 1f))
                .endVertex();
    }
}
