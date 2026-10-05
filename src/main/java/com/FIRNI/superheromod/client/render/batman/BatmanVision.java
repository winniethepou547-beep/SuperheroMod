package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The screen-side of Batman's smoke: the smoke round the camera for anyone lost in someone else's smoke (the flash
 * grenade's white-out is BatmanFlashFx, the thermal vision BatmanThermal).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanVision {
    private BatmanVision() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }

    /** 0..1: how far his thermal vision is on (his first-person arms take its colours). */
    public static float thermalAmount() { return BatmanThermal.amount(); }

    /** No outline any more (the thermal vision shows bodies as heat); kept for the mixins. */
    public static boolean outline(Entity e) { return false; }
    public static int outlineColor(Entity e) { return -1; }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        float t = now();
        // Someone else's smoke round the camera: nearly black, thick.
        BatmanFx.Cloud c = BatmanFx.cloudAt(mc.gameRenderer.getMainCamera().getPosition());
        // (A Batman with his thermal vision on sees through it: no black screen for him.)
        if (c != null && c.owner() != mc.player.getId() && BatmanThermal.amount() < .5f) {
            float age = t - c.start(), k = Math.min(1, age / 10f) * (1 - Mth.clamp((age - c.life()) / 30f, 0, 1));
            g.fill(0, 0, w, h, HudStyle.alpha(0xFF141518, .86f * k));
            for (int i = 0; i < 5; i++) {
                float drift = (t * (.4f + i * .13f) + i * 37) % (w + 200) - 100;
                int y = (int) (h * (.15f + .17f * i) + Mth.sin(t * .05f + i) * 12);
                g.fill((int) drift - 120, y - 30, (int) drift + 120, y + 30, HudStyle.alpha(0xFF0B0C0E, .3f * k));
            }
        }
    }
}
