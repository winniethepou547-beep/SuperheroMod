package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.heroes.batman.BatmanConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The screen-side of Batman's gadgets: the flash's white-out for those it blinds and a brief brightening for those it
 * does not, and the smoke round the camera for anyone lost in someone else's smoke (the thermal vision is BatmanThermal).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanVision {
    private static float flashStart = -100, flashLife = 1, flashPower;
    private static float glimpseStart = -100, glimpsePower;

    private BatmanVision() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }

    /** 0..1: how far his thermal vision is on (his first-person arms take its colours). */
    public static float thermalAmount() { return BatmanThermal.amount(); }

    static void flashed(float power) {
        flashStart = now(); flashPower = Mth.clamp(power, .2f, 1); flashLife = (float) (BatmanConfig.FLASH_SECONDS.get() * 20 * flashPower);
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.get(), .35f, 2f);
    }
    /** A flash went off where this client could see it without being blinded: a quick brightening of the screen. */
    static void glimpse(float power) { glimpseStart = now(); glimpsePower = Mth.clamp(power, 0, 1); }
    static void clear() { flashStart = glimpseStart = -100; }

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
        if (c != null && c.owner() != mc.player.getId()) {
            float age = t - c.start(), k = Math.min(1, age / 10f) * (1 - Mth.clamp((age - c.life()) / 30f, 0, 1));
            g.fill(0, 0, w, h, HudStyle.alpha(0xFF141518, .86f * k));
            for (int i = 0; i < 5; i++) {
                float drift = (t * (.4f + i * .13f) + i * 37) % (w + 200) - 100;
                int y = (int) (h * (.15f + .17f * i) + Mth.sin(t * .05f + i) * 12);
                g.fill((int) drift - 120, y - 30, (int) drift + 120, y + 30, HudStyle.alpha(0xFF0B0C0E, .3f * k));
            }
        }
        // A flash seen without being blinded: a bright blink.
        float gs = t - glimpseStart;
        if (gs >= 0 && gs < 6) g.fill(0, 0, w, h, HudStyle.alpha(0xFFFFFFFF, .2f * glimpsePower * (1 - gs / 6)));
        // Blinded: white, giving the view back slowly (fast at first, the last of it lingering).
        float since = t - flashStart;
        if (since >= 0 && since < flashLife) {
            float k = 1 - since / flashLife;
            float a = since < 4 ? 1 : (float) Math.pow(k, .6);
            g.fill(0, 0, w, h, HudStyle.alpha(0xFFFFFFFF, a * (.6f + .4f * flashPower)));
        }
    }
}
