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
 * Sandman's sand build-up: a hairline vertical gauge on the left with the five armour levels
 * as ticks and the current level as a numeral. Filling it powers Sand Burst.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class SandArmorOverlay {
    private static final int HEIGHT = 84, LEFT = 18, LEVELS = 5;
    private SandArmorOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float fill = ClientSandArmorData.fill(mc.player);
        int level = ClientSandArmorData.get(mc.player);
        if (fill <= 0.001f && level <= 0) return;
        GuiGraphics gui = event.getGuiGraphics();
        int bottom = mc.getWindow().getGuiScaledHeight() / 2 + HEIGHT / 2, top = bottom - HEIGHT;
        boolean full = fill >= .999f;
        float pulse = (float) (.5 + .5 * Math.sin(System.currentTimeMillis() * .008));
        int color = full ? HudStyle.alpha(0xFFFFC24D, .7f + .3f * pulse) : 0xFFE0C489;
        HudStyle.vbar(gui, LEFT, top, HEIGHT, fill, color);
        for (int i = 1; i < LEVELS; i++) {
            int y = bottom - HEIGHT * i / LEVELS;
            gui.fill(LEFT + 3, y, LEFT + 6, y + 1, i <= level ? 0xFFE0C489 : HudStyle.TRACK);
        }
        HudStyle.value(gui, mc.font, String.valueOf(level), LEFT + 1, top - 16, 1.4f, full ? 0xFFFFC24D : HudStyle.TEXT, 0);
        HudStyle.caption(gui, mc.font, "Sand", LEFT + 1, bottom + 5, HudStyle.MUTED, 0);
    }
}
