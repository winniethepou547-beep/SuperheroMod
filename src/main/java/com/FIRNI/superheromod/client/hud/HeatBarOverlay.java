package com.FIRNI.superheromod.client.hud;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Optic heat: a hairline arc in the lower right that warms toward red, with a quiet readout. */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public class HeatBarOverlay {
    private static final float START = 200f, END = 340f, INNER = 34f, OUTER = 36.5f;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        if (!ClientHeatData.hasHeat() && !ClientHeatData.isBeamActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        GuiGraphics gui = event.getGuiGraphics();
        float cx = mc.getWindow().getGuiScaledWidth() - 52, cy = mc.getWindow().getGuiScaledHeight() - 30;
        float pct = ClientHeatData.getPercent();
        boolean overheated = ClientHeatData.isOverheated();
        float pulse = (float) (.5 + .5 * Math.sin(System.currentTimeMillis() * .012));
        int color = overheated ? HudStyle.alpha(HudStyle.DANGER, .45f + .55f * pulse)
                : pct >= .85f ? HudStyle.DANGER : pct >= .5f ? HudStyle.ACCENT : 0xFFFFB25C;
        HudStyle.arc(gui, cx, cy, INNER, OUTER, START, END, HudStyle.TRACK);
        HudStyle.arc(gui, cx, cy, INNER, OUTER, START, START + (END - START) * (overheated ? 1 : pct), color);
        if (pct >= .85f || overheated) HudStyle.arc(gui, cx, cy, OUTER, OUTER + 1.5f, START, START + (END - START) * pct, HudStyle.alpha(color, .35f));
        if (overheated) HudStyle.caption(gui, mc.font, "Overheat", (int) cx, (int) cy - 14, color, 0);
        else HudStyle.value(gui, mc.font, (int) (pct * 100) + "%", cx, cy - 16, 1.4f, HudStyle.TEXT, 0);
        HudStyle.caption(gui, mc.font, "Optic heat", (int) cx, (int) cy - 2, HudStyle.MUTED, 0);
    }
}
