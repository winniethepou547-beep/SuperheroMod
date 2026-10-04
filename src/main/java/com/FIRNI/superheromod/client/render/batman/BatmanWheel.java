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
 * Batman's HUD drawings: the gadget wheel (Arkham / Spider-Man 2 style: a dark glass disc, four sectors with their
 * icons, the one under the cursor lit cyan, the picked one marked, its name and cooldown in the middle), the bat
 * emblem and the Batarang icon of the belt. Flat vector shapes drawn as triangle fans.
 */
public final class BatmanWheel {
    private BatmanWheel() {}
    static final int CYAN = 0xFF5FD6FF, GLASS = 0xC0101820, RIM = 0xFF2B4A5A;
    private static final String[] INFO = {"Geniş sis: içindekiler seni göremez", "Yakındakileri kör eder", "Duvar arkasını gösterir", "Yaklaşanı havaya uçurur"};

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
            float mid = -90 + i * 90, a0 = mid - 43, a1 = mid + 43;
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

    /** A gadget's icon: smoke (three puffs), flash (a star burst), thermal (a target in rings), mine (a disc with its light). */
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
            case G_THERMAL -> {
                HudStyle.arc(g, x, y, s * .82f, s, 0, 360, col);
                HudStyle.arc(g, x, y, s * .45f, s * .6f, 0, 360, col);
                disc(g, x, y, s * .22f, col);
                float sweep = (time * 12) % 360;
                HudStyle.arc(g, x, y, s * .6f, s * .82f, sweep, sweep + 50, HudStyle.alpha(col, .6f));
            }
            default -> {
                disc(g, x, y, s * .9f, HudStyle.alpha(col, .45f));
                HudStyle.arc(g, x, y, s * .75f, s * .9f, 0, 360, col);
                disc(g, x, y, s * .28f, (int) (time / 8) % 2 == 0 ? 0xFFFF4B3E : HudStyle.alpha(0xFFFF4B3E, .4f));
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
    public static void batarang(GuiGraphics g, float x, float y, float s, int col, float fill) {
        if (fill >= .999f) { fan(g, x, y, RANG, s, col); return; }
        // Cut the shape at the fill line (y down: keep what lies below it).
        float line = .4f - fill * (.4f + .55f);
        float[] cut = new float[RANG.length * 2 + 4];
        int n = 0;
        for (int i = 0; i < RANG.length / 2; i++) {
            float ax = RANG[i * 2], ay = RANG[i * 2 + 1], bx = RANG[(i * 2 + 2) % RANG.length], by = RANG[(i * 2 + 3) % RANG.length];
            boolean ain = ay >= line, bin = by >= line;
            if (ain) { cut[n++] = ax; cut[n++] = ay; }
            if (ain != bin) { float t = (line - ay) / (by - ay); cut[n++] = ax + (bx - ax) * t; cut[n++] = line; }
        }
        if (n >= 6) {
            float[] pts = new float[n];
            System.arraycopy(cut, 0, pts, 0, n);
            float mx = 0, my = 0;
            for (int i = 0; i < n / 2; i++) { mx += pts[i * 2]; my += pts[i * 2 + 1]; }
            mx /= n / 2f; my /= n / 2f;
            for (int i = 0; i < n / 2; i++) { pts[i * 2] -= mx; pts[i * 2 + 1] -= my; }
            fan(g, x + mx * s, y + my * s, pts, s, col);
        }
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
