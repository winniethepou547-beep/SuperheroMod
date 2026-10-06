package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Locale;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The Ice Armory wheel (E held), in the style of Batman's gadget wheel: a disc of dark glass with a frosted rim, three
 * sectors clockwise from the top (mace, spear, sword) with their icons drawn as flat vector shapes, the one under the
 * cursor lit ice blue, the weapon in hand marked, its name and a one-line description in the middle.
 */
public final class IcemanWheel {
    private IcemanWheel() {}
    static final int ICE = 0xFF8FD8FF, PALE = 0xFFD8F2FF, GLASS = 0xC00B1520, RIM = 0xFF35617D, MARK = 0xFFEAF8FF;
    static final float SECTOR = 360f / WEAPONS;
    private static final String[] INFO = {"Ağır: 3. vuruş ezme, basılı: büyür", "Hızlı: 3. vuruş seri dürtme, basılı: fırlat", "Orta: 3. vuruş dönüş, basılı: girdap"};

    static void draw(GuiGraphics g, Font font, IcemanClient.State s, float cx, float cy, float shown, int hovered, float curX, float curY, float time) {
        float k = 1 - (1 - shown) * (1 - shown), r = 62 * (.85f + .15f * k);
        g.pose().pushPose();
        g.fill(0, 0, (int) (cx * 2), (int) (cy * 2), HudStyle.alpha(0x7A000814, k));
        g.flush();
        HudStyle.arc(g, cx, cy, 0, r + 2, 0, 360, HudStyle.alpha(GLASS, k));
        HudStyle.arc(g, cx, cy, r, r + 2, 0, 360, HudStyle.alpha(RIM, k));
        HudStyle.arc(g, cx, cy, r * .42f, r * .42f + 1, 0, 360, HudStyle.alpha(RIM, k));
        // Frost on the rim: small crystals standing out of it, glinting in turn.
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + Math.PI / 12;
            float px = cx + (float) Math.cos(a) * (r + 2), py = cy + (float) Math.sin(a) * (r + 2);
            float glint = .45f + .55f * Math.max(0, Mth.sin(time * .12f - i * .9f));
            spike(g, px, py, (float) a, 3.2f + (i % 3 == 0 ? 2 : 0), HudStyle.alpha(PALE, .55f * glint * k));
        }
        for (int i = 0; i < WEAPONS; i++) {
            float mid = -90 + i * SECTOR, a0 = mid - SECTOR / 2 + 2, a1 = mid + SECTOR / 2 - 2;
            boolean hot = i == hovered, picked = i == s.weapon;
            HudStyle.arc(g, cx, cy, r * .45f, r - 2, a0, a1, hot ? HudStyle.alpha(ICE, .3f * k) : HudStyle.alpha(0x26D8F2FF, k));
            if (hot) HudStyle.arc(g, cx, cy, r - 3, r + 1, a0, a1, HudStyle.alpha(ICE, k));
            if (picked) HudStyle.arc(g, cx, cy, r * .45f, r * .45f + 2, a0 + 8, a1 - 8, HudStyle.alpha(MARK, k));
            double a = Math.toRadians(mid);
            float ix = cx + (float) Math.cos(a) * r * .72f, iy = cy + (float) Math.sin(a) * r * .72f;
            int col = HudStyle.alpha(hot ? 0xFFFFFFFF : picked ? PALE : 0xFFB4D6EA, k);
            icon(g, i, ix, iy, hot ? 11 : 9.5f, col, HudStyle.alpha(hot ? ICE : 0xFF6FA8CC, k));
            if (picked && s.cooldowns[CD_WEAPON] > 0)
                HudStyle.caption(g, font, String.format(Locale.ROOT, "%.0f", Math.ceil(s.cooldowns[CD_WEAPON] / 20f)), (int) ix, (int) iy + 11, HudStyle.alpha(0xFFFF9A8A, k), 0);
        }
        // The middle: the name and what it does.
        int show = Mth.clamp(hovered >= 0 ? hovered : s.weapon, 0, WEAPONS - 1);
        snowflake(g, cx, cy - 11, 4.5f, HudStyle.alpha(0xFF2C4A60, k));
        HudStyle.caption(g, font, WEAPON_NAMES[show], (int) cx, (int) cy - 2, HudStyle.alpha(hovered >= 0 ? ICE : MARK, k), 0);
        g.pose().pushPose();
        g.pose().translate(cx, cy + 9, 0);
        g.pose().scale(.55f, .55f, 1);
        String info = INFO[show];
        g.drawString(font, info, -font.width(info) / 2, 0, HudStyle.alpha(0xFFB8CCD8, k), false);
        g.pose().popPose();
        // The cursor.
        float px = cx + curX / 30f * r * .9f, py = cy + curY / 30f * r * .9f;
        HudStyle.arc(g, px, py, 0, 2.2f, 0, 360, HudStyle.alpha(0xFFFFFFFF, .85f * k));
        HudStyle.caption(g, font, "Sol tık / E bırak: seç", (int) cx, (int) (cy + r + 10), HudStyle.alpha(0xFF9FB8C6, k), 0);
        g.pose().popPose();
    }

    /** A weapon's icon, pointing up and to the right: col the ice, rim the glinting edge. */
    static void icon(GuiGraphics g, int weapon, float x, float y, float s, int col, int rim) {
        switch (weapon) {
            case W_MACE -> {
                // A thick handle, a pommel crystal, a spiked crystal head.
                shape(g, x, y, s, new float[]{-.13f, .95f, .13f, .95f, .13f, -.2f, -.13f, -.2f}, col);
                shape(g, x, y, s, new float[]{0, .85f, .2f, 1.05f, 0, 1.25f, -.2f, 1.05f}, rim);
                float[] star = new float[32];
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    float rr = i % 2 == 0 ? .62f : .36f;
                    star[i * 2] = (float) Math.cos(a) * rr; star[i * 2 + 1] = (float) Math.sin(a) * rr;
                }
                shape(g, x, y, s, offset(star, 0, -.55f), col);
                shape(g, x, y, s, offset(circle(.22f, 10), 0, -.55f), rim);
            }
            case W_SPEAR -> {
                // A long thin shaft, a collar, a leaf blade, a butt spike.
                shape(g, x, y, s, new float[]{-.06f, 1.25f, .06f, 1.25f, .06f, -.55f, -.06f, -.55f}, col);
                shape(g, x, y, s, new float[]{-.17f, -.5f, .17f, -.5f, .12f, -.62f, -.12f, -.62f}, rim);
                shape(g, x, y, s, new float[]{0, -1.3f, .2f, -.95f, .14f, -.7f, 0, -.62f, -.14f, -.7f, -.2f, -.95f}, col);
                shape(g, x, y, s, new float[]{0, -1.3f, .05f, -.95f, 0, -.7f, -.05f, -.95f}, rim);
                shape(g, x, y, s, new float[]{-.08f, 1.22f, .08f, 1.22f, 0, 1.45f}, rim);
            }
            default -> {
                // A broad blade with its ridge, a crossguard, the grip, the pommel.
                shape(g, x, y, s, new float[]{0, -1.25f, .2f, -.95f, .2f, .35f, -.2f, .35f, -.2f, -.95f}, col);
                shape(g, x, y, s, new float[]{0, -1.15f, .04f, -.9f, .04f, .3f, -.04f, .3f, -.04f, -.9f}, rim);
                shape(g, x, y, s, new float[]{-.5f, .35f, .5f, .35f, .4f, .5f, -.4f, .5f}, rim);
                shape(g, x, y, s, new float[]{-.07f, .5f, .07f, .5f, .07f, .95f, -.07f, .95f}, col);
                shape(g, x, y, s, new float[]{0, .9f, .14f, 1.05f, 0, 1.2f, -.14f, 1.05f}, rim);
            }
        }
    }
    /** A shape pointing up, turned 45° to point up and right, filled as a fan from its own middle. */
    private static void shape(GuiGraphics g, float x, float y, float s, float[] pts, int col) {
        float c = Mth.cos(Mth.PI / 4), sn = Mth.sin(Mth.PI / 4);
        int n = pts.length / 2;
        float mx = 0, my = 0;
        float[] out = new float[pts.length];
        for (int i = 0; i < n; i++) {
            float px = pts[i * 2], py = pts[i * 2 + 1];
            out[i * 2] = px * c - py * sn; out[i * 2 + 1] = px * sn + py * c;
            mx += out[i * 2]; my += out[i * 2 + 1];
        }
        mx /= n; my /= n;
        for (int i = 0; i < n; i++) { out[i * 2] -= mx; out[i * 2 + 1] -= my; }
        fan(g, x + mx * s, y + my * s, out, s, col);
    }
    private static float[] offset(float[] pts, float dx, float dy) {
        float[] out = pts.clone();
        for (int i = 0; i < out.length; i += 2) { out[i] += dx; out[i + 1] += dy; }
        return out;
    }
    private static float[] circle(float r, int n) {
        float[] out = new float[n * 2];
        for (int i = 0; i < n; i++) { double a = Math.PI * 2 * i / n; out[i * 2] = (float) Math.cos(a) * r; out[i * 2 + 1] = (float) Math.sin(a) * r; }
        return out;
    }
    /** A small crystal standing out of the rim at angle a. */
    private static void spike(GuiGraphics g, float x, float y, float a, float len, int col) {
        float c = Mth.cos(a), s = Mth.sin(a), w = 1.1f;
        fan(g, x, y, new float[]{-s * w, c * w, c * len, s * len, s * w, -c * w}, 1, col);
    }
    /** A snowflake: six thin arms. */
    private static void snowflake(GuiGraphics g, float x, float y, float r, int col) {
        for (int i = 0; i < 6; i++) {
            float a = Mth.PI * i / 3, c = Mth.cos(a), s = Mth.sin(a), w = .45f;
            fan(g, x, y, new float[]{-s * w, c * w, c * r, s * r, s * w, -c * w}, 1, col);
        }
    }

    /** A filled shape: a fan from (x, y) round the outline (points scaled by s); both windings drawn. */
    static void fan(GuiGraphics g, float x, float y, float[] pts, float s, int color) {
        float a = (color >>> 24) / 255f, r = (color >> 16 & 255) / 255f, gr = (color >> 8 & 255) / 255f, b = (color & 255) / 255f;
        if (a <= 0) return;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
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
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
