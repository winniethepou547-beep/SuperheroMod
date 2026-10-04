package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.SpikePullPacket;
import com.FIRNI.superheromod.network.packet.SpikeStuckPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Locale;

/**
 * The side of whoever has Magneto's spike in them (any hero, or none): their left clicks pull at it instead of
 * attacking, and under the crosshair a mouse with its left button lights up on every click while the ring round it
 * fills clockwise; full, the spike is out.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class SpikeClient {
    private static final int VIOLET = 0xFFC9A2FF;
    private static boolean stuck;
    private static float progress, shown;
    private static long lastPacket, outAt = -1000;
    private static int immune;
    /** The last few clicks (game time with the frame), for the button flash and the ripples. */
    private static final float[] CLICKS = new float[6];
    private static int nextClick;

    private SpikeClient() {}

    private static long now() { var level = Minecraft.getInstance().level; return level == null ? 0 : level.getGameTime(); }
    public static void receive(SpikeStuckPacket p) {
        if (!p.stuck() && stuck && p.immune() > 0) { outAt = now(); immune = p.immune(); }
        if (p.stuck() && !stuck) shown = 0;
        stuck = p.stuck(); progress = p.progress(); lastPacket = now();
    }
    /** A spike in the local player right now: left click is theirs to pull it out with. */
    public static boolean stuck() { return stuck && Minecraft.getInstance().level != null && now() - lastPacket < 10; }

    /** The clicks are taken at the start of the tick, before the game would turn them into a swing or an attack. */
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { stuck = false; return; }
        if (!stuck() || mc.screen != null) return;
        int presses = 0;
        while (mc.options.keyAttack.consumeClick()) presses++;
        if (presses <= 0) return;
        ModNetworking.CHANNEL.sendToServer(new SpikePullPacket(Math.min(3, presses)));
        float t = mc.level.getGameTime() + mc.getFrameTime();
        for (int i = 0; i < Math.min(3, presses); i++) { CLICKS[nextClick] = t + i * .3f; nextClick = (nextClick + 1) % CLICKS.length; }
        // Each tug moves it a little at once (the server's count follows).
        progress = Math.min(.98f, progress + .5f / 12f);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { stuck = false; progress = shown = 0; outAt = -1000; }

    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui || !e.getOverlay().id().getPath().equals("hotbar")) return;
        GuiGraphics g = e.getGuiGraphics();
        int cx = e.getWindow().getGuiScaledWidth() / 2, cy = e.getWindow().getGuiScaledHeight() / 2 + 46;
        float time = mc.level.getGameTime() + mc.getFrameTime();
        if (!stuck()) {
            // Out: a short word, then it fades.
            float since = time - outAt;
            if (since < 50) {
                float a = 1 - Mth.clamp((since - 30) / 20, 0, 1);
                HudStyle.caption(g, mc.font, "Çubuğu çıkardın", cx, cy - 4, HudStyle.alpha(0xFFFFFFFF, a), 0);
                HudStyle.caption(g, mc.font, String.format(Locale.ROOT, "%d sn bağışıksın", immune / 20), cx, cy + 8, HudStyle.alpha(VIOLET, a), 0);
            }
            return;
        }
        shown += (progress - shown) * .3f;
        float last = -100;
        for (float c : CLICKS) last = Math.max(last, c);
        float since = time - last;
        boolean lit = since < 2.5f;
        float nudge = since < 2 ? (1 - since / 2) : 0;

        // The ring: a dark track, the part pulled so far going clockwise from the top, a white head; a ripple per click.
        float r = 19;
        HudStyle.arc(g, cx, cy, r - 1, r + 4, 0, 360, 0x66000000);
        HudStyle.arc(g, cx, cy, r, r + 3, 0, 360, 0x30FFFFFF);
        if (shown > .005f) {
            float end = -90 + 360 * Mth.clamp(shown, 0, 1);
            HudStyle.arc(g, cx, cy, r - 1.5f, r + 4.5f, -90, end, HudStyle.alpha(VIOLET, .25f));
            HudStyle.arc(g, cx, cy, r, r + 3, -90, end, VIOLET);
            HudStyle.arc(g, cx, cy, r - 1, r + 4, end - 8, end, 0xFFFFFFFF);
        }
        for (float c : CLICKS) {
            float k = (time - c) / 8;
            if (k < 0 || k > 1) continue;
            HudStyle.arc(g, cx, cy, r + 3 + 9 * k, r + 4.5f + 9 * k, 0, 360, HudStyle.alpha(0xFFFFFFFF, .6f * (1 - k)));
        }

        // The mouse: its left button lights (and the mouse dips) on every click, and pulses between clicks to ask for more.
        g.pose().pushPose();
        g.pose().translate(cx, cy + nudge * 1.2f, 0);
        float scale = 1.5f - .08f * nudge;
        g.pose().scale(scale, scale, 1);
        int x0 = -6, y0 = -9, x1 = 6, y1 = 9;
        rounded(g, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFFE9E6F0);
        rounded(g, x0, y0, x1, y1, 0xFF26222E);
        float pulse = .35f + .35f * (float) Math.sin(time * .6f);
        int left = lit ? 0xFFFFFFFF : HudStyle.alpha(VIOLET, pulse);
        g.fill(x0 + 1, y0 + 1, -1, y0 + 7, left);
        g.fill(x0 + 2, y0, -1, y0 + 1, left);
        g.fill(0, y0 + 1, x1 - 1, y0 + 7, 0xFF3A3544);
        g.fill(-1, y0, 0, y0 + 8, 0xFFE9E6F0);
        g.fill(x0, y0 + 7, x1, y0 + 8, 0xFFE9E6F0);
        g.fill(-1, y0 + 2, 0, y0 + 5, 0xFF8E879C);
        g.pose().popPose();

        HudStyle.caption(g, mc.font, "Demir çubuk saplandı", cx, cy - 36, HudStyle.alpha(VIOLET, .9f), 0);
        HudStyle.caption(g, mc.font, "Sol tık spamla!", cx, cy + 28, lit ? 0xFFFFFFFF : HudStyle.TEXT, 0);
    }
    /** A box with its corners cut: the mouse's body. */
    private static void rounded(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0 + 2, y0, x1 - 2, y1, color);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, color);
        g.fill(x0, y0 + 2, x1, y1 - 2, color);
    }
}
