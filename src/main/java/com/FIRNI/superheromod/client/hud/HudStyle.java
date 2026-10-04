package com.FIRNI.superheromod.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import java.util.Locale;

/**
 * One quiet visual language for every HUD element: no boxes or outlines, soft shade instead
 * of panels, 2px bars, tracked uppercase captions and a single ember accent.
 */
public final class HudStyle {
    public static final int ACCENT = 0xFFFF7A2A, ACCENT_HOT = 0xFFFFC27A, TEXT = 0xFFEDE6DD, MUTED = 0x99D8CFC4,
            TRACK = 0x30FFFFFF, DANGER = 0xFFFF4B3E;
    private HudStyle() {}

    public static int alpha(int color, float a) {
        int base = color >>> 24;
        return ((int) (Math.max(0, Math.min(1, a)) * base) << 24) | (color & 0xFFFFFF);
    }
    public static int captionWidth(Font font, String text) {
        String s = text.toUpperCase(Locale.ROOT);
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += font.width(String.valueOf(s.charAt(i))) + 1;
        return Math.max(0, w - 1);
    }
    /** Uppercase, letter-spaced, no shadow. align: -1 left, 0 centre, 1 right. */
    public static void caption(GuiGraphics g, Font font, String text, int x, int y, int color, int align) {
        String s = text.toUpperCase(Locale.ROOT);
        int w = captionWidth(font, s);
        int cx = align < 0 ? x : align == 0 ? x - w / 2 : x - w;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            g.drawString(font, ch, cx, y, color, false);
            cx += font.width(ch) + 1;
        }
    }
    /** Large numeral with a soft drop shade. */
    public static void value(GuiGraphics g, Font font, String text, float x, float y, float scale, int color, int align) {
        int w = font.width(text);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        int left = align < 0 ? 0 : align == 0 ? -w / 2 : -w;
        g.drawString(font, text, left + 1, 1, alpha(0xFF000000, .35f), false);
        g.drawString(font, text, left, 0, color, false);
        g.pose().popPose();
    }
    /** 2px track and fill with a bright leading tip and a faint glow. */
    public static void bar(GuiGraphics g, int x, int y, int w, float progress, int color) {
        progress = Math.max(0, Math.min(1, progress));
        g.fill(x, y, x + w, y + 2, TRACK);
        int filled = Math.round(w * progress);
        if (filled <= 0) return;
        g.fill(x, y - 1, x + filled, y, alpha(color, .22f));
        g.fill(x, y + 2, x + filled, y + 3, alpha(color, .22f));
        g.fill(x, y, x + filled, y + 2, color);
        g.fill(x + filled - 1, y - 1, x + filled, y + 3, ACCENT_HOT);
    }
    public static void vbar(GuiGraphics g, int x, int y, int h, float progress, int color) {
        progress = Math.max(0, Math.min(1, progress));
        g.fill(x, y, x + 2, y + h, TRACK);
        int filled = Math.round(h * progress);
        if (filled <= 0) return;
        g.fill(x - 1, y + h - filled, x, y + h, alpha(color, .22f));
        g.fill(x + 2, y + h - filled, x + 3, y + h, alpha(color, .22f));
        g.fill(x, y + h - filled, x + 2, y + h, color);
        g.fill(x - 1, y + h - filled, x + 3, y + h - filled + 1, ACCENT_HOT);
    }
    /** Short pips for discrete counters. */
    public static void segments(GuiGraphics g, int x, int y, int w, int count, int filled, int color) {
        int gap = 2, size = (w - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            int sx = x + i * (size + gap);
            g.fill(sx, y, sx + size, y + 2, i < filled ? color : TRACK);
        }
    }
    /** Soft dark wash behind text, fading out at the edges: no hard panel. */
    public static void shade(GuiGraphics g, int x, int y, int w, int h, float strength) {
        for (int i = 0; i < 6; i++) {
            int a = (int) (strength * 22);
            g.fill(x + i * 2, y + i, x + w - i * 2, y + h - i, a << 24);
        }
    }
    /** Minimal keycap: hairline outline with cut corners, key label inside. */
    public static int keycap(GuiGraphics g, Font font, String key, int x, int y, boolean lit) {
        int w = Math.max(11, font.width(key) + 7), h = 11, line = lit ? ACCENT : alpha(TEXT, .75f);
        g.fill(x + 1, y, x + w - 1, y + 1, line); g.fill(x + 1, y + h - 1, x + w - 1, y + h, line);
        g.fill(x, y + 1, x + 1, y + h - 1, line); g.fill(x + w - 1, y + 1, x + w, y + h - 1, line);
        if (lit) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, alpha(ACCENT, .22f));
        g.drawString(font, key, x + (w - font.width(key)) / 2 + 1, y + 2, lit ? ACCENT_HOT : TEXT, false);
        return w;
    }
    /** Keycap followed by its action, e.g. [RMB] PULL. Returns total width. */
    public static int hint(GuiGraphics g, Font font, String key, String action, int x, int y) {
        int w = keycap(g, font, key, x, y, false);
        caption(g, font, action, x + w + 4, y + 2, MUTED, -1);
        return w + 4 + captionWidth(font, action);
    }
    /** Per skill (by its label): the longest cooldown seen (for the fill), when it last came ready, its last value. */
    private static final java.util.Map<String, float[]> SKILLS = new java.util.HashMap<>();
    /**
     * One row of a hero's skill list: a soft dark pill fading out to the right, the hero's colour in a thin stripe down
     * its left edge, the keycap, the skill's name. While it cools down the pill fills back up from the left in the hero's
     * colour and the seconds count down at its end; when it comes ready a light sweeps across it and the stripe flares.
     * active = the skill is running right now (the stripe pulses). Returns the row's width.
     */
    public static int skill(GuiGraphics g, Font font, String key, String action, int cooldown, boolean active, int x, int y, int accent) {
        long ms = System.currentTimeMillis();
        float now = (ms % 10_000_000L) / 50f;
        float[] st = SKILLS.computeIfAbsent(action, k -> new float[]{0, -100, 0});
        if (cooldown > st[2] + 1) st[0] = cooldown;
        if (cooldown <= 0 && st[2] > 0) { st[1] = now; st[0] = 0; }
        st[2] = cooldown;
        if (SKILLS.size() > 96) SKILLS.clear();
        float max = Math.max(cooldown, st[0]);
        float progress = cooldown <= 0 ? 1 : 1 - cooldown / Math.max(1f, max);
        float ready = Math.max(0, 1 - (now - st[1]) / 12f);
        String cd = cooldown > 0 ? String.format(Locale.ROOT, "%.1f", cooldown / 20f) : "";
        int capW = Math.max(11, font.width(key) + 7), labelW = captionWidth(font, action);
        int w = 3 + capW + 5 + labelW + (cd.isEmpty() ? 8 : 6 + font.width(cd) + 6);
        int h = 11;
        // The pill: dark, fading out to the right; the cooldown's fill in the hero's colour.
        for (int i = 0; i < w; i += 2) {
            float fadeOut = i > w - 14 ? (w - i) / 14f : 1;
            g.fill(x + i, y, Math.min(x + w, x + i + 2), y + h, alpha(0xC0060408, .75f * fadeOut));
        }
        if (cooldown > 0) {
            int filled = Math.round((w - 3) * progress);
            g.fill(x + 3, y + h - 2, x + 3 + filled, y + h - 1, alpha(accent, .9f));
            g.fill(x + 3, y + 1, x + 3 + filled, y + h - 2, alpha(accent, .12f));
        } else g.fill(x + 3, y + h - 2, x + w - 6, y + h - 1, alpha(accent, .28f));
        // The stripe: steady when ready, dim while cooling, pulsing while the skill runs, flaring as it comes ready.
        float pulse = active ? .65f + .35f * (float) Math.sin(now * .35f) : 1;
        float stripe = (cooldown > 0 ? .35f : 1) * pulse;
        g.fill(x, y, x + 2, y + h, alpha(accent, stripe));
        if (ready > 0) {
            g.fill(x, y - 1, x + 3, y + h + 1, alpha(0xFFFFFFFF, ready));
            int sweep = Math.round((w + 20) * (1 - ready)) - 10;
            for (int k = -6; k <= 6; k++) {
                int sx = x + sweep + k;
                if (sx < x || sx >= x + w) continue;
                g.fill(sx, y, sx + 1, y + h, alpha(0xFFFFFFFF, ready * .5f * (1 - Math.abs(k) / 7f)));
            }
        }
        // Keycap, name, seconds.
        boolean lit = cooldown <= 0 && (active || ready > 0);
        keycap(g, font, key, x + 3, y, lit);
        caption(g, font, action, x + 3 + capW + 5, y + 2, cooldown > 0 ? alpha(MUTED, .7f) : alpha(TEXT, .95f), -1);
        if (!cd.isEmpty()) g.drawString(font, cd, x + 3 + capW + 5 + labelW + 6, y + 2, alpha(accent | 0xFF000000, 1), false);
        return w;
    }
    public static int hintWidth(Font font, String key, String action) {
        return Math.max(11, font.width(key) + 7) + 4 + captionWidth(font, action);
    }
    /** Thin ring arc in degrees (0 = right, clockwise). */
    public static void arc(GuiGraphics g, float cx, float cy, float inner, float outer, float startDeg, float endDeg, int color) {
        if (endDeg <= startDeg) return;
        float a = (color >>> 24) / 255f, r = (color >> 16 & 255) / 255f, gr = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        int segments = Math.max(4, (int) ((endDeg - startDeg) / 4));
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(startDeg + (endDeg - startDeg) * i / segments);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            buffer.vertex(m, cx + cos * outer, cy + sin * outer, 0).color(r, gr, b, a).endVertex();
            buffer.vertex(m, cx + cos * inner, cy + sin * inner, 0).color(r, gr, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
}
