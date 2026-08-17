package com.FIRNI.superheromod.client.hud;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.ClientSandArmorData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sandman'in kum birikimi — ekranin solunda DIKEY bar.
 *
 * Bar hasar aldikca doluyor; doluluk hem verdigi hasari hem de Sand Burst ile
 * atacagi diken sayisini belirliyor. Kademeler (zirh seviyeleri) barin
 * uzerinde cizgi olarak isaretli, boylece bir sonraki seviyeye ne kadar
 * kaldigi gorulebiliyor.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class SandArmorOverlay {

    private static final int BAR_WIDTH = 9;
    private static final int BAR_HEIGHT = 84;
    private static final int MARGIN_LEFT = 14;

    private static final int LEVELS = 5;

    private SandArmorOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float fill = ClientSandArmorData.fill(mc.player);
        int level = ClientSandArmorData.get(mc.player);

        // Bos barda ekrani kalabaliklastirma
        if (fill <= 0.001f && level <= 0) return;

        GuiGraphics gui = event.getGuiGraphics();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int x = MARGIN_LEFT;
        int bottom = screenH / 2 + BAR_HEIGHT / 2;
        int top = bottom - BAR_HEIGHT;

        // Cerceve ve zemin
        gui.fill(x - 2, top - 2, x + BAR_WIDTH + 2, bottom + 2, 0xB0000000);
        gui.fill(x, top, x + BAR_WIDTH, bottom, 0x55201808);

        // Dolum asagidan yukari
        int filled = Math.round(BAR_HEIGHT * Math.min(1f, fill));
        if (filled > 0) {
            gui.fillGradient(x, bottom - filled, x + BAR_WIDTH, bottom,
                    colorTop(fill), colorBottom(fill));
        }

        // Seviye cizgileri
        for (int i = 1; i < LEVELS; i++) {
            int y = bottom - (BAR_HEIGHT * i / LEVELS);
            gui.fill(x, y, x + BAR_WIDTH, y + 1, 0x66000000);
        }

        // Bar doluyken hafif parlama
        if (fill >= 0.999f) {
            long pulse = System.currentTimeMillis() % 800;
            int alpha = pulse < 400 ? 0x55 : 0x22;
            gui.fill(x - 2, top - 2, x + BAR_WIDTH + 2, bottom + 2,
                    (alpha << 24) | 0xFFD27A);
        }

        // Seviye rakami
        String label = String.valueOf(level);
        int textW = mc.font.width(label);
        gui.drawString(mc.font, label,
                x + BAR_WIDTH / 2 - textW / 2, top - 12, 0xFFE8C88A, true);
    }

    /** Dolduça soluk kumdan kizgin turuncuya. */
    private static int colorTop(float fill) {
        return fill > 0.8f ? 0xFFFFC24D : 0xFFE0C489;
    }

    private static int colorBottom(float fill) {
        return fill > 0.8f ? 0xFFB86A18 : 0xFF9A7E42;
    }
}
