package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.input.AbilityKeyHandler;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.GhostBindPacket;
import com.FIRNI.superheromod.network.packet.GhostEscapePacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The chained player's side: a big keycap prompt in the middle of the screen, mash F to break free. */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class GhostBindClient {
    private static boolean bound;
    private static float progress, shown;
    private static long lastPacket, lastPress;
    public static void receive(GhostBindPacket p) {
        var level = Minecraft.getInstance().level;
        bound = p.bound(); progress = p.progress();
        lastPacket = level == null ? 0 : level.getGameTime();
    }
    /** While chained, F belongs to the escape, not to the player's own abilities. */
    public static boolean bound() {
        var level = Minecraft.getInstance().level;
        return bound && level != null && level.getGameTime() - lastPacket < 10;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { bound = false; return; }
        int presses = 0;
        while (AbilityKeyHandler.KEY_SKILL_F.consumeClick()) presses++;
        if (!bound()) return;
        if (presses > 0 && mc.screen == null) {
            ModNetworking.CHANNEL.sendToServer(new GhostEscapePacket(Math.min(4, presses)));
            lastPress = mc.level.getGameTime();
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { bound = false; progress = shown = 0; }
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !e.getOverlay().id().getPath().equals("hotbar")) return;
        boolean visible = bound();
        if (!visible) { shown = 0; return; }
        shown += (progress - shown) * .35f;
        var g = e.getGuiGraphics();
        int cx = e.getWindow().getGuiScaledWidth() / 2, cy = e.getWindow().getGuiScaledHeight() / 2 + 34;
        float now = mc.level.getGameTime() + mc.getFrameTime();
        boolean pressed = now - lastPress < 2.5f;
        float pulse = pressed ? 0 : (float) (Math.sin(now * .55) * .5 + .5);
        // Keycap scaled up from the shared style: it lights and sinks on every press.
        float scale = pressed ? 2.2f : 2.4f + .08f * pulse;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        com.FIRNI.superheromod.client.hud.HudStyle.keycap(g, mc.font, "F", -6, -6, pressed);
        g.pose().popPose();
        com.FIRNI.superheromod.client.hud.HudStyle.caption(g, mc.font, "Chained", cx, cy - 30, com.FIRNI.superheromod.client.hud.HudStyle.MUTED, 0);
        com.FIRNI.superheromod.client.hud.HudStyle.caption(g, mc.font, "Mash to break free", cx, cy + 20, com.FIRNI.superheromod.client.hud.HudStyle.TEXT, 0);
        com.FIRNI.superheromod.client.hud.HudStyle.bar(g, cx - 50, cy + 32, 100, shown, com.FIRNI.superheromod.client.hud.HudStyle.ACCENT);
    }
}
