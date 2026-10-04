package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Locale;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Batman's HUD drawings: the gadget wheel (Arkham / Spider-Man 2 style: a dark glass disc, five sectors with their
 * icons, the one under the cursor lit cyan, the picked one marked, its name and cooldown in the middle), the bat
 * emblem and the Batarang icon of the belt. Flat vector shapes drawn as triangle fans.
 */
public final class BatmanWheel {
    private BatmanWheel() {}
    static final int CYAN = 0xFF5FD6FF, GLASS = 0xC0101820, RIM = 0xFF2B4A5A;
    private static final String[] INFO = {"Geniş kara sis: içindekiler göremez, sen termalle görürsün", "Yakındakileri kör eder", "Tak / çıkar: ağır elektrikli boks",
            "4 sn iki bilekten seri atış, nişangâhı izler", "İki sonik verici yerden çıkar, hedefi sersemletir"};
    /** Degrees each sector spans. */
    static final float SECTOR = 360f / GADGETS;

    /** The classic bat outline (unit size, centred, y down): wings with three scallops, the head with ears. */
    private static final float[] BAT = {
            0, -.18f, .08f, -.42f, .13f, -.22f, .32f, -.30f, .62f, -.42f, 1f, -.1f, .78f, .02f, .66f, .28f, .5f, .12f, .34f, .2f,
            .18f, .1f, .08f, .42f, 0, .3f, -.08f, .42f, -.18f, .1f, -.34f, .2f, -.5f, .12f, -.66f, .28f, -.78f, .02f, -1f, -.1f,
            -.62f, -.42f, -.32f, -.30f, -.13f, -.22f, -.08f, -.42f};
    /** A Batarang: a flat bat-shaped blade (unit size, centred). */
    private static final float[] RANG = {
            0, -.25f, .25f, -.35f, .6f, -.55f, 1f, -.2f, .7f, -.05f, .55f, .25f, .3f, .1f, 0, .4f, -.3f, .1f, -.55f, .25f, -.7f, -.05f,
            -1f, -.2f, -.6f, -.55f, -.25f, -.35f};

    static void draw(GuiGraphics g, Font font, BatmanClient.State s, float cx, float cy, float shown, int hovered, float curX, float curY, float time) {
        float k = 1 - (1 - shown) * (1 - shown), r = 64 * (.85f + .15f * k);
        g.pose().pushPose();
        // A dim over the world while the wheel is open.
        g.fill(0, 0, (int) (cx * 2), (int) (cy * 2), HudStyle.alpha(0x88000000, k));
        HudStyle.arc(g, cx, cy, 0, r + 2, 0, 360, HudStyle.alpha(GLASS, k));
        HudStyle.arc(g, cx, cy, r, r + 2, 0, 360, HudStyle.alpha(RIM, k));
        HudStyle.arc(g, cx, cy, r * .42f, r * .42f + 1, 0, 360, HudStyle.alpha(RIM, k));
        for (int i = 0; i < GADGETS; i++) {
            float mid = -90 + i * SECTOR, a0 = mid - SECTOR / 2 + 2, a1 = mid + SECTOR / 2 - 2;
            boolean hot = i == hovered, picked = i == s.gadget;
            int sector = hot ? HudStyle.alpha(CYAN, .32f * k) : HudStyle.alpha(0x30FFFFFF, .5f * k);
            HudStyle.arc(g, cx, cy, r * .45f, r - 2, a0, a1, sector);
            if (hot) HudStyle.arc(g, cx, cy, r - 3, r + 1, a0, a1, HudStyle.alpha(CYAN, k));
            if (picked) HudStyle.arc(g, cx, cy, r * .45f, r * .45f + 2, a0 + 6, a1 - 6, HudStyle.alpha(0xFFE8C547, k));
            double a = Math.toRadians(mid);
            float ix = cx + (float) Math.cos(a) * r * .72f, iy = cy + (float) Math.sin(a) * r * .72f;
            int col = s.cooldowns[i] > 0 ? HudStyle.alpha(0xFF7A8088, k) : HudStyle.alpha(hot ? 0xFFFFFFFF : 0xFFCFE9F2, k);
            icon(g, i, ix, iy, hot ? 9 : 7.5f, col, time);
            if (s.cooldowns[i] > 0) HudStyle.caption(g, font, String.format(Locale.ROOT, "%.0f", Math.ceil(s.cooldowns[i] / 20f)), (int) ix, (int) iy + 10, HudStyle.alpha(0xFFFF8A7A, k), 0);
        }
        // The middle: the name and what it does.
        int show = hovered >= 0 ? hovered : s.gadget;
        emblem(g, cx, cy - 9, 6, 0, HudStyle.alpha(0xFF2E3A44, k));
        HudStyle.caption(g, font, GADGET_NAMES[show], (int) cx, (int) cy - 1, HudStyle.alpha(hovered >= 0 ? CYAN : 0xFFE8C547, k), 0);
        g.pose().pushPose();
        g.pose().translate(cx, cy + 10, 0);
        g.pose().scale(.6f, .6f, 1);
        int tw = font.width(INFO[show]);
        g.drawString(font, INFO[show], -tw / 2, 0, HudStyle.alpha(0xFFB9C6CE, k), false);
        g.pose().popPose();
        // The cursor: where the mouse points on the wheel.
        float px = cx + curX / 30f * r * .9f, py = cy + curY / 30f * r * .9f;
        HudStyle.arc(g, px, py, 0, 2.2f, 0, 360, HudStyle.alpha(0xFFFFFFFF, .8f * k));
        HudStyle.caption(g, font, "Sol tık / R bırak: seç", (int) cx, (int) (cy + r + 10), HudStyle.alpha(0xFF9FB4BF, k), 0);
        g.pose().popPose();
    }

    /** A gadget's icon: smoke (puffs), flash (a star burst), electric gauntlets (a fist and a bolt), wrist cannon (two gauntlets firing), sonic trap (an emitter and its waves). */
    static void icon(GuiGraphics g, int gadget, float x, float y, float s, int col, float time) {
        switch (gadget) {
            case G_SMOKE -> {
                disc(g, x - s * .45f, y + s * .15f, s * .45f, col);
                disc(g, x + s * .4f, y + s * .2f, s * .4f, col);
                disc(g, x, y - s * .25f, s * .52f, col);
                disc(g, x, y + s * .35f, s * .38f, col);
            }
            case G_FLASH -> {
                float[] star = new float[32];
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16 + time * .02;
                    float rr = (i % 2 == 0 ? 1f : .38f) * s;
                    star[i * 2] = (float) Math.cos(a) * rr; star[i * 2 + 1] = (float) Math.sin(a) * rr;
                }
                fan(g, x, y, star, 1, col);
            }
            case G_CANNON -> {
                // Two gauntlets side by side, pointing up-right, a gold muzzle flash at each.
                for (int k = 0; k < 2; k++) {
                    float ox = x - s * .45f + k * s * .5f, oy = y + s * .3f - k * s * .25f;
                    fan(g, ox, oy, new float[]{-.22f, .55f, .22f, .55f, .3f, -.2f, .12f, -.45f, -.12f, -.45f, -.3f, -.2f}, s, col);
                    float f = (int) (time / 2 + k) % 2 == 0 ? 1 : .55f;
                    disc(g, ox + s * .05f, oy - s * .62f, s * .2f * f, HudStyle.alpha(0xFFFFD34A, .9f));
                }
            }
            case G_SONIC -> {
                // The emitter's round chamber on its post, waves going out from it.
                g.fill((int) (x - s * .12f), (int) (y + s * .1f), (int) (x + s * .12f), (int) (y + s * .85f), col);
                g.fill((int) (x - s * .45f), (int) (y + s * .75f), (int) (x + s * .45f), (int) (y + s * .9f), col);
                HudStyle.arc(g, x, y - s * .15f, s * .32f, s * .48f, 0, 360, col);
                disc(g, x, y - s * .15f, s * .14f, HudStyle.alpha(0xFFDFF4FF, .9f));
                float w = (time * .08f) % 1;
                for (int k = 0; k < 2; k++) {
                    float r = s * (.62f + .38f * ((w + k * .5f) % 1));
                    HudStyle.arc(g, x, y - s * .15f, r, r + 1.1f, -50, 50, HudStyle.alpha(col, 1 - (w + k * .5f) % 1));
                }
            }
            default -> {
                // A fist (the knuckles a row of squares) and a cyan bolt across it.
                g.fill((int) (x - s * .55f), (int) (y - s * .2f), (int) (x + s * .45f), (int) (y + s * .7f), col);
                for (int k = 0; k < 4; k++) g.fill((int) (x - s * .55f + k * s * .25f), (int) (y - s * .5f), (int) (x - s * .35f + k * s * .25f), (int) (y - s * .2f), col);
                int bolt = (int) (time / 3) % 3 == 0 ? 0xFFFFFFFF : 0xFF4FE3E8;
                fan(g, x + s * .15f, y, new float[]{.5f, -1f, -.05f, -.05f, .25f, -.05f, -.5f, 1f, .05f, .1f, -.2f, .1f}, s * .7f, bolt);
            }
        }
    }
    /** The bat emblem on an oval. bg 0 = no oval. */
    public static void emblem(GuiGraphics g, float x, float y, float s, int bg, int bat) {
        if (bg != 0) {
            float[] oval = new float[48];
            for (int i = 0; i < 24; i++) { double a = Math.PI * 2 * i / 24; oval[i * 2] = (float) Math.cos(a) * 1.25f; oval[i * 2 + 1] = (float) Math.sin(a) * .72f; }
            fan(g, x, y, oval, s, bg);
        }
        fan(g, x, y, BAT, s, bat);
    }
    /** A Batarang icon, filled from the bottom up to fill (0..1). */
    public static void batarang(GuiGraphics g, float x, float y, float s, int col, float fill) { filled(g, x, y, s, RANG, col, fill); }
    /** The plain bat symbol (the belt's count), filled from the bottom up to fill (0..1). */
    public static void bat(GuiGraphics g, float x, float y, float s, int col, float fill) { filled(g, x, y, s, BAT, col, fill); }
    /** A shape filled from the bottom up to fill (0..1): the outline cut at the fill line, the part below it drawn. */
    private static void filled(GuiGraphics g, float x, float y, float s, float[] shape, int col, float fill) {
        if (fill >= .999f) { fan(g, x, y, shape, s, col); return; }
        if (fill <= .001f) return;
        float top = 1e9f, bottom = -1e9f;
        for (int i = 1; i < shape.length; i += 2) { top = Math.min(top, shape[i]); bottom = Math.max(bottom, shape[i]); }
        // y down: keep what lies below the line.
        float line = bottom - fill * (bottom - top);
        float[] cut = new float[shape.length * 2 + 4];
        int n = 0;
        for (int i = 0; i < shape.length / 2; i++) {
            float ax = shape[i * 2], ay = shape[i * 2 + 1], bx = shape[(i * 2 + 2) % shape.length], by = shape[(i * 2 + 3) % shape.length];
            boolean ain = ay >= line, bin = by >= line;
            if (ain) { cut[n++] = ax; cut[n++] = ay; }
            if (ain != bin) { float t = (line - ay) / (by - ay); cut[n++] = ax + (bx - ax) * t; cut[n++] = line; }
        }
        if (n < 6) return;
        float[] pts = new float[n];
        System.arraycopy(cut, 0, pts, 0, n);
        float mx = 0, my = 0;
        for (int i = 0; i < n / 2; i++) { mx += pts[i * 2]; my += pts[i * 2 + 1]; }
        mx /= n / 2f; my /= n / 2f;
        for (int i = 0; i < n / 2; i++) { pts[i * 2] -= mx; pts[i * 2 + 1] -= my; }
        fan(g, x + mx * s, y + my * s, pts, s, col);
    }
    static void disc(GuiGraphics g, float x, float y, float r, int col) { HudStyle.arc(g, x, y, 0, r, 0, 360, col); }

    /** A filled shape: a fan from (x, y) round the outline (unit points scaled by s). */
    static void fan(GuiGraphics g, float x, float y, float[] pts, float s, int color) {
        float a = (color >>> 24) / 255f, r = (color >> 16 & 255) / 255f, gr = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
        if (a <= 0) return;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = g.pose().last().pose();
        buffer.vertex(m, x, y, 0).color(r, gr, b, a).endVertex();
        int n = pts.length / 2;
        for (int i = 0; i <= n; i++) {
            int j = i % n;
            buffer.vertex(m, x + pts[j * 2] * s, y + pts[j * 2 + 1] * s, 0).color(r, gr, b, a).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
    static float ease(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }
}
