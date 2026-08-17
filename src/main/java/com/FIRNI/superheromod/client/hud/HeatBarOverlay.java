package com.FIRNI.superheromod.client.hud;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public class HeatBarOverlay {

    private static final int ARC_SEGMENTS = 32;
    private static final float ARC_START_ANGLE = 220f;
    private static final float ARC_END_ANGLE = 320f;
    private static final float ARC_RADIUS_OUTER = 38f;
    private static final float ARC_RADIUS_INNER = 32f;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        if (!ClientHeatData.hasHeat() && !ClientHeatData.isBeamActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        float centerX = screenW - 55;
        float centerY = screenH - 55;

        float pct = ClientHeatData.getPercent();
        boolean overheated = ClientHeatData.isOverheated();

        drawArc(gui, centerX, centerY, ARC_RADIUS_INNER, ARC_RADIUS_OUTER,
                ARC_START_ANGLE, ARC_END_ANGLE, 0x44000000, ARC_SEGMENTS);

        float fillEndAngle = ARC_START_ANGLE + (ARC_END_ANGLE - ARC_START_ANGLE) * pct;

        int barColor;
        if (overheated) {
            long pulse = System.currentTimeMillis() % 300;
            barColor = pulse < 150 ? 0xFFFF0000 : 0x44FF0000;
        } else if (pct >= 0.85f) {
            barColor = 0xDDFF2200;
        } else if (pct >= 0.65f) {
            barColor = 0xDDFF6600;
        } else if (pct >= 0.40f) {
            barColor = 0xDDFFAA00;
        } else {
            barColor = 0xBBFF4444;
        }

        if (pct > 0.01f) {
            drawArc(gui, centerX, centerY, ARC_RADIUS_INNER, ARC_RADIUS_OUTER,
                    ARC_START_ANGLE, fillEndAngle, barColor, ARC_SEGMENTS);
        }

        if (overheated) {
            long flashMs = System.currentTimeMillis() % 300;
            float flashAlpha = flashMs < 150 ? 0.6f : 0.15f;
            int flashColor = (((int) (flashAlpha * 255)) << 24) | 0xFF0000;
            drawArc(gui, centerX, centerY, ARC_RADIUS_INNER - 4, ARC_RADIUS_OUTER + 4,
                    ARC_START_ANGLE, ARC_END_ANGLE, flashColor, ARC_SEGMENTS);

            String text = "OVERHEAT";
            int textWidth = mc.font.width(text);
            int textColor = flashMs < 150 ? 0xFFFF0000 : 0xAAFF3333;
            gui.drawString(mc.font, text,
                    (int) centerX - textWidth / 2,
                    (int) centerY - 5, textColor, true);
        } else if (pct >= 0.85f) {
            float glowAlpha = (float) (0.3f + 0.2f * Math.sin(System.currentTimeMillis() * 0.01));
            int glowColor = (((int) (glowAlpha * 255)) << 24) | 0xFF2200;
            drawArc(gui, centerX, centerY, ARC_RADIUS_INNER - 2, ARC_RADIUS_OUTER + 2,
                    ARC_START_ANGLE, fillEndAngle, glowColor, ARC_SEGMENTS);
        }

        int pctDisplay = (int) (pct * 100);
        if (pctDisplay > 0) {
            String pctText = pctDisplay + "%";
            int tw = mc.font.width(pctText);
            gui.drawString(mc.font, pctText,
                    (int) centerX - tw / 2,
                    (int) centerY + 6, 0xCCFFFFFF, true);
        }
    }

    private static void drawArc(GuiGraphics gui, float cx, float cy,
                                float innerR, float outerR,
                                float startDeg, float endDeg,
                                int color, int segments) {
        float a = (float) (color >> 24 & 255) / 255.0f;
        float r = (float) (color >> 16 & 255) / 255.0f;
        float g = (float) (color >> 8 & 255) / 255.0f;
        float b = (float) (color & 255) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float angleRange = endDeg - startDeg;
        float segmentAngle = angleRange / segments;

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        Matrix4f matrix = gui.pose().last().pose();

        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.toRadians(startDeg + segmentAngle * i);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            buffer.vertex(matrix, cx + cos * outerR, cy + sin * outerR, 0).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, cx + cos * innerR, cy + sin * innerR, 0).color(r, g, b, a).endVertex();
        }

        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
}
