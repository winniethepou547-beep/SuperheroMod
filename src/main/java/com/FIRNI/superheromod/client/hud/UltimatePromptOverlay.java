package com.FIRNI.superheromod.client.hud;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** While aiming an ultimate: its name above the crosshair and the two key hints below. */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public class UltimatePromptOverlay {
    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        if (!ClientUltimateState.isAiming()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics gui = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float pulse = (float) (.5 + .5 * Math.sin(System.currentTimeMillis() * .006));
        HudStyle.caption(gui, mc.font, "Ruby Rage", w / 2, h / 2 - 30, HudStyle.alpha(HudStyle.DANGER, .7f + .3f * pulse), 0);
        int total = HudStyle.hintWidth(mc.font, "LMB", "Confirm") + 16 + HudStyle.hintWidth(mc.font, "RMB", "Cancel");
        int x = w / 2 - total / 2, y = h / 2 + 18;
        x += HudStyle.hint(gui, mc.font, "LMB", "Confirm", x, y) + 16;
        HudStyle.hint(gui, mc.font, "RMB", "Cancel", x, y);
    }
}
