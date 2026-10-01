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
