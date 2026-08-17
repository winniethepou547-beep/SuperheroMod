package com.FIRNI.superheromod.client;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Oyuncu mac haritasina isinlaninca beliren "3 2 1 FIGHT!" gecis efekti.
 * Hareketi kilitlemez, sadece HUD'un ustune cizilen gecici bir overlay.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class CountdownOverlay {

    private static final int PHASE_MS = 1100;
    private static final int FIGHT_MS = 2000;
    private static final int TOTAL_MS = PHASE_MS * 3 + FIGHT_MS;

    private static boolean active = false;
    private static long startMillis;

    private CountdownOverlay() {
    }

    public static void start() {
        active = true;
        startMillis = Util.getMillis();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!active) return;

        long elapsed = Util.getMillis() - startMillis;
        if (elapsed >= TOTAL_MS) {
            active = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float centerX = screenW / 2f;
        float centerY = screenH / 2f;

        if (elapsed < (long) PHASE_MS * 3) {
            int phaseIndex = (int) (elapsed / PHASE_MS); // 0,1,2 -> "3","2","1"
            long local = elapsed - (long) phaseIndex * PHASE_MS;
            String text = String.valueOf(3 - phaseIndex);
            renderPunch(graphics, font, text, centerX, centerY, local, PHASE_MS, 0xFFFFFF);
        } else {
            long local = elapsed - (long) PHASE_MS * 3;
            if (local < 120) {
                int alpha = (int) (200 * (1f - local / 120f));
                graphics.fill(0, 0, screenW, screenH, (alpha << 24) | 0xFFFFFF);
            }
            renderPunch(graphics, font, "FIGHT!", centerX, centerY, local, FIGHT_MS, 0xFF4A3D);
        }
    }

    private static void renderPunch(GuiGraphics graphics, Font font, String text, float centerX, float centerY,
                                     long local, int phaseDurationMs, int color) {
        float inMs = 220f;
        float outMs = 250f;
        float scale;
        float alpha;
        if (local < inMs) {
            float t = local / inMs;
            scale = 2.4f - 1.4f * easeOutBack(t);
            alpha = t;
        } else if (local > phaseDurationMs - outMs) {
            float t = (local - (phaseDurationMs - outMs)) / outMs;
            scale = 1.0f;
            alpha = 1f - t;
        } else {
            scale = 1.0f;
            alpha = 1f;
        }

        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        int drawColor = (a << 24) | (color & 0xFFFFFF);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale * 2f, scale * 2f, 1f);
        int width = font.width(text);
        graphics.drawString(font, text, -(width / 2), -(font.lineHeight / 2), drawColor, true);
        graphics.pose().popPose();
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        float p = t - 1;
        return 1 + c3 * p * p * p + c1 * p * p;
    }
}
