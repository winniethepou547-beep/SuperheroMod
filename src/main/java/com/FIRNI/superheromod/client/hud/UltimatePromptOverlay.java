package com.FIRNI.superheromod.client.hud;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ulti nisan modundayken ekranin ortasinda Confirm / Cancel istemi gosterir.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public class UltimatePromptOverlay {

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        if (!ClientUltimateState.isAiming()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        int cy = h / 2 - 6;

        drawPrompt(gui, mc, w / 2 - 90, cy, "Confirm", "LMB", 0xFFFFFFFF, 0xCC1A1A1A);
        drawPrompt(gui, mc, w / 2 + 34, cy, "Cancel", "RMB", 0xFFFFFFFF, 0xCC1A1A1A);

        String title = "RUBY RAGE";
        int tw = mc.font.width(title);
        long pulse = System.currentTimeMillis() % 700;
        int titleColor = pulse < 350 ? 0xFFFF3333 : 0xFFAA1111;
        gui.drawString(mc.font, title, w / 2 - tw / 2, cy - 26, titleColor, true);
    }

    private static void drawPrompt(GuiGraphics gui, Minecraft mc, int x, int y,
                                   String label, String key, int textColor, int bg) {
        int keyW = mc.font.width(key);
        int boxW = keyW + 8;

        gui.fill(x, y - 2, x + boxW, y + 11, bg);
        gui.fill(x, y - 2, x + boxW, y - 1, 0xFF555555);
        gui.fill(x, y + 10, x + boxW, y + 11, 0xFF555555);
        gui.drawString(mc.font, key, x + 4, y + 1, 0xFFFFDD55, true);

        gui.drawString(mc.font, label, x + boxW + 5, y + 1, textColor, true);
    }
}
