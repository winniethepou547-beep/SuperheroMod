package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.core.matchmaking.MatchMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

import java.util.Random;

public class MatchFoundScreen extends Screen {

    private static final int COUNTDOWN_SECONDS = 10;
    private static final long COUNTDOWN_MS = COUNTDOWN_SECONDS * 1000L;
    private static final int RING_RADIUS = 88;
    private static final int RING_THICKNESS = 4;
    private static final int INNER_RADIUS = 82;

    private final MatchMode mode;
    private final long openedAtMillis;
    private boolean accepted = false;
    private long acceptedAtMillis = 0;

    public MatchFoundScreen(MatchMode mode) {
        super(Component.literal("Mac Bulundu"));
        this.mode = mode;
        this.openedAtMillis = Util.getMillis();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        int cx = this.width / 2;
        int ringCy = this.height / 2 - 15;

        // Decline button (X) - top right of ring
        int decX = cx + 68;
        int decY = ringCy - 78;
        if (mx >= decX - 10 && mx <= decX + 10 && my >= decY - 10 && my <= decY + 10) {
            this.minecraft.setScreen(null);
            return true;
        }

        // Accept button
        if (!accepted) {
            int btnW = 140;
            int btnH = 22;
            int btnX = cx - btnW / 2;
            int btnY = ringCy + RING_RADIUS + 18;
            if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                accepted = true;
                acceptedAtMillis = Util.getMillis();
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        long elapsed = Util.getMillis() - openedAtMillis;
        int cx = this.width / 2;
        int ringCy = this.height / 2 - 15;

        renderBackground(g, elapsed);

        // Ring depletion ratio (1.0 = full, 0.0 = empty)
        float remaining = Math.max(0f, 1f - (elapsed / (float) COUNTDOWN_MS));

        renderTickMarks(g, cx, ringCy, elapsed, remaining);
        renderDepletingRing(g, cx, ringCy, remaining, elapsed);
        renderInnerPanel(g, cx, ringCy, elapsed);
        renderLogo(g, cx, ringCy - 38, elapsed);
        renderMatchFoundText(g, cx, ringCy + 10, elapsed);
        renderModeText(g, cx, ringCy + 32, elapsed);
        renderDeclineButton(g, cx, ringCy, mouseX, mouseY);
        renderAcceptButton(g, cx, ringCy + RING_RADIUS + 18, mouseX, mouseY, elapsed);
        renderCountdown(g, cx, ringCy + RING_RADIUS + 52, elapsed, remaining);

        // Initial flash
        if (elapsed < 200) {
            float t = 1f - (elapsed / 200f);
            int alpha = (int) (t * 120);
            g.fill(0, 0, this.width, this.height, (alpha << 24) | 0xFFFFFF);
        }
    }

    private void renderBackground(GuiGraphics g, long elapsed) {
        g.fill(0, 0, this.width, this.height, 0xE8080810);

        float t = elapsed / 1000f;
        Random r = new Random(333);
        for (int i = 0; i < 8; i++) {
            float bx = r.nextFloat() * this.width;
            float by = r.nextFloat() * this.height;
            float drift = (float) Math.sin(t * 0.4 + r.nextFloat() * 6.28) * 15;
            float rise = (t * 8 * (0.2f + r.nextFloat() * 0.4f)) % (this.height + 30) - 15;
            float fy = this.height - rise;
            float fx = bx + drift;
            float alpha = (float) (0.1 + 0.08 * Math.sin(t * 2 + r.nextFloat() * 6.28));
            int a = (int) (alpha * 255);
            g.fill((int) fx, (int) fy, (int) fx + 2, (int) fy + 2, (a << 24) | 0x3366CC);
        }

        int vSize = 50;
        for (int i = 0; i < vSize; i++) {
            int va = (int) ((1f - (float) i / vSize) * 160);
            g.fill(0, i, this.width, i + 1, (va << 24));
            g.fill(0, this.height - i - 1, this.width, this.height - i, (va << 24));
        }
    }

    private void renderInnerPanel(GuiGraphics g, int cx, int cy, long elapsed) {
        for (int dy = -INNER_RADIUS; dy <= INNER_RADIUS; dy++) {
            int halfW = (int) Math.sqrt((long) INNER_RADIUS * INNER_RADIUS - (long) dy * dy);
            g.fill(cx - halfW, cy + dy, cx + halfW, cy + dy + 1, 0xD0101828);
        }

        // Inner rim
        drawCircleOutline(g, cx, cy, INNER_RADIUS, 1, 0x40AACCFF);

        // Subtle radial gradient overlay
        for (int dy = -INNER_RADIUS + 5; dy <= INNER_RADIUS - 5; dy++) {
            int halfW = (int) Math.sqrt((long) (INNER_RADIUS - 5) * (INNER_RADIUS - 5) - (long) dy * dy);
            float dist = Math.abs(dy) / (float) INNER_RADIUS;
            int gradA = (int) (dist * 20);
            g.fill(cx - halfW, cy + dy, cx + halfW, cy + dy + 1, (gradA << 24) | 0x4488CC);
        }
    }

    private void renderDepletingRing(GuiGraphics g, int cx, int cy, float remaining, long elapsed) {
        // Background ring (dark, always full)
        drawCircleOutline(g, cx, cy, RING_RADIUS, RING_THICKNESS, 0x30334455);

        // Blue ring that depletes
        if (remaining > 0.001f) {
            int startAngle = -90;
            int sweepAngle = (int) (360 * remaining);

            float pulse = (float) (0.85 + 0.15 * Math.sin(elapsed / 300.0));
            int blueR = (int) (40 * pulse);
            int blueG = (int) (140 + 40 * pulse);
            int blueB = 255;
            int blueA = (int) (220 * pulse);
            int blueColor = (blueA << 24) | (blueR << 16) | (blueG << 8) | blueB;

            drawArc(g, cx, cy, RING_RADIUS, RING_THICKNESS, startAngle, startAngle + sweepAngle, blueColor);

            // Glow layer (wider, more transparent)
            int glowA = (int) (50 * pulse);
            int glowColor = (glowA << 24) | 0x44AAFF;
            drawArc(g, cx, cy, RING_RADIUS, RING_THICKNESS + 3, startAngle, startAngle + sweepAngle, glowColor);

            // Bright tip at the end of the arc
            if (sweepAngle > 5) {
                double tipRad = Math.toRadians(startAngle + sweepAngle);
                int tipX = cx + (int) (RING_RADIUS * Math.cos(tipRad));
                int tipY = cy + (int) (RING_RADIUS * Math.sin(tipRad));
                g.fill(tipX - 3, tipY - 3, tipX + 4, tipY + 4, (int) (blueA * 0.8) << 24 | 0xAADDFF);
                g.fill(tipX - 1, tipY - 1, tipX + 2, tipY + 2, 0xFFFFFFFF);
            }
        }

        // Warning: last 3 seconds, ring flashes red
        if (remaining > 0 && remaining < 0.3f) {
            float flash = (float) (0.5 + 0.5 * Math.sin(elapsed / 100.0));
            int redA = (int) (flash * 40);
            drawCircleOutline(g, cx, cy, RING_RADIUS, RING_THICKNESS + 1, (redA << 24) | 0xFF4444);
        }
    }

    private void renderTickMarks(GuiGraphics g, int cx, int cy, long elapsed, float remaining) {
        int outerR = RING_RADIUS + 8;
        int innerR = RING_RADIUS + 3;
        int tickCount = 60;

        for (int i = 0; i < tickCount; i++) {
            double angle = Math.toRadians(-90 + i * (360.0 / tickCount));
            int ox = cx + (int) (outerR * Math.cos(angle));
            int oy = cy + (int) (outerR * Math.sin(angle));
            int ix = cx + (int) (innerR * Math.cos(angle));
            int iy = cy + (int) (innerR * Math.sin(angle));

            float tickRatio = i / (float) tickCount;
            boolean isLit = tickRatio < remaining;

            int color;
            if (i % 5 == 0) {
                // Major tick
                color = isLit ? 0xBBCCAA44 : 0x30665522;
            } else {
                color = isLit ? 0x55887733 : 0x18443311;
            }

            drawLine(g, ix, iy, ox, oy, color);
        }
    }

    private void renderLogo(GuiGraphics g, int cx, int cy, long elapsed) {
        float appear = Math.min(1f, elapsed / 400f);
        int a = (int) (appear * 255);

        // V-wing logo placeholder (stylized V shape)
        int logoW = 30;
        int logoH = 22;
        int lx = cx - logoW / 2;
        int ly = cy - logoH / 2;

        // Crown top
        g.fill(cx - 3, ly - 3, cx + 4, ly, (a << 24) | 0xFFDD44);
        g.fill(cx - 1, ly - 5, cx + 2, ly - 2, (a << 24) | 0xFFDD44);

        // V shape
        for (int i = 0; i < logoH; i++) {
            float t = i / (float) logoH;
            int halfW = (int) (logoW / 2 * (1f - t * 0.7f));
            int yy = ly + i;

            // Left wing
            g.fill(cx - halfW - 2, yy, cx - halfW + 1, yy + 1, (a << 24) | 0xCCCCCC);
            // Right wing
            g.fill(cx + halfW - 1, yy, cx + halfW + 2, yy + 1, (a << 24) | 0xCCCCCC);

            // Inner fill
            if (t > 0.3f) {
                int innerHalf = (int) (halfW * 0.4f);
                g.fill(cx - innerHalf, yy, cx + innerHalf, yy + 1, (int) (a * 0.3f) << 24 | 0x88AACC);
            }
        }

        // Bottom point
        g.fill(cx - 1, ly + logoH, cx + 2, ly + logoH + 3, (a << 24) | 0xCCCCCC);
    }

    private void renderMatchFoundText(GuiGraphics g, int cx, int cy, long elapsed) {
        float appear = Math.min(1f, elapsed / 500f);
        if (appear < 0.01f) return;

        float eased = 1f - (1f - appear) * (1f - appear);
        int alpha = (int) (eased * 255);

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        float scale = 2.0f * eased;
        g.pose().scale(scale, scale, 1f);

        String text = "MATCH FOUND";
        int tw = this.font.width(text);
        // Shadow
        g.drawString(this.font, text, -tw / 2 + 1, 1, (alpha << 24), false);
        // Main text
        g.drawString(this.font, text, -tw / 2, 0, (alpha << 24) | 0xFFFFFF, false);
        g.pose().popPose();
    }

    private void renderModeText(GuiGraphics g, int cx, int cy, long elapsed) {
        float appear = Math.min(1f, (elapsed - 200) / 400f);
        if (appear < 0.01f) return;

        int alpha = (int) (appear * 255);

        String modeName = mode == MatchMode.ONE_V_ONE ? "1v1" : "2v2";

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        float scale = 1.4f;
        g.pose().scale(scale, scale, 1f);
        int tw = this.font.width(modeName);
        g.drawString(this.font, modeName, -tw / 2, 0, (alpha << 24) | 0xFFAA33, false);
        g.pose().popPose();
    }

    private void renderDeclineButton(GuiGraphics g, int cx, int ringCy, int mouseX, int mouseY) {
        int decX = cx + 68;
        int decY = ringCy - 78;

        boolean hovered = mouseX >= decX - 10 && mouseX <= decX + 10 && mouseY >= decY - 10 && mouseY <= decY + 10;

        // Circle bg
        int bgColor = hovered ? 0xCC883333 : 0x99554433;
        for (int dy = -9; dy <= 9; dy++) {
            int halfW = (int) Math.sqrt(81 - dy * dy);
            g.fill(decX - halfW, decY + dy, decX + halfW, decY + dy + 1, bgColor);
        }
        drawCircleOutline(g, decX, decY, 9, 1, hovered ? 0xFFFF6666 : 0xAABB8855);

        // X mark
        int xColor = hovered ? 0xFFFFAAAA : 0xDDCCCCCC;
        for (int i = -4; i <= 4; i++) {
            g.fill(decX + i - 1, decY + i - 1, decX + i + 2, decY + i + 2, xColor);
            g.fill(decX - i - 1, decY + i - 1, decX - i + 2, decY + i + 2, xColor);
        }
    }

    private void renderAcceptButton(GuiGraphics g, int cx, int btnY, int mouseX, int mouseY, long elapsed) {
        int btnW = 140;
        int btnH = 22;
        int btnX = cx - btnW / 2;

        boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        if (accepted) {
            float pulse = (float) (0.7 + 0.3 * Math.sin(elapsed / 200.0));
            int ga = (int) (pulse * 255);

            // Green accepted state
            g.fill(btnX - 2, btnY - 2, btnX + btnW + 2, btnY + btnH + 2, (ga << 24) | 0x00AA44);
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xDD006622);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, 0xFF00DD55);
            g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, 0xFF003311);

            // Angled sides
            g.fill(btnX - 5, btnY + 3, btnX, btnY + btnH - 3, 0xAA005522);
            g.fill(btnX + btnW, btnY + 3, btnX + btnW + 5, btnY + btnH - 3, 0xAA005522);

            g.drawCenteredString(this.font, "§a§lKABUL EDiLDi", cx, btnY + 7, 0xFF00FF88);
        } else {
            // Cyan/blue accept button
            int bg = hovered ? 0xDD1A3366 : 0xDD102244;
            int border = hovered ? 0xFF55BBFF : 0xFF3388CC;

            g.fill(btnX - 2, btnY - 2, btnX + btnW + 2, btnY + btnH + 2, border);
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, 0xFF66CCFF);
            g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, 0xFF112233);

            // Angled sides
            g.fill(btnX - 5, btnY + 3, btnX, btnY + btnH - 3, hovered ? 0xAA224466 : 0xAA112244);
            g.fill(btnX + btnW, btnY + 3, btnX + btnW + 5, btnY + btnH - 3, hovered ? 0xAA224466 : 0xAA112244);

            g.pose().pushPose();
            g.pose().translate(cx, btnY + 4, 0);
            float s = 1.2f;
            g.pose().scale(s, s, 1f);
            String text = "ACCEPT!";
            int tw = this.font.width(text);
            g.drawString(this.font, text, -tw / 2, 0, 0xFF66DDFF, false);
            g.pose().popPose();
        }
    }

    private void renderCountdown(GuiGraphics g, int cx, int y, long elapsed, float remaining) {
        int seconds = (int) Math.ceil(remaining * COUNTDOWN_SECONDS);
        if (seconds < 0) seconds = 0;

        String countText = String.valueOf(seconds);

        // Color: white normally, red when < 3
        int color;
        if (seconds <= 3 && seconds > 0) {
            float flash = (float) (0.5 + 0.5 * Math.sin(elapsed / 100.0));
            int r = 255;
            int gr = (int) (100 * flash);
            color = 0xFF000000 | (r << 16) | (gr << 8) | (int) (50 * flash);
        } else {
            color = 0xFFCCCCCC;
        }

        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        float scale = 2.0f;
        g.pose().scale(scale, scale, 1f);
        int tw = this.font.width(countText);
        g.drawString(this.font, countText, -tw / 2 + 1, 1, 0xFF000000, false);
        g.drawString(this.font, countText, -tw / 2, 0, color, false);
        g.pose().popPose();
    }

    private void drawCircleOutline(GuiGraphics g, int cx, int cy, int radius, int thickness, int color) {
        for (int angle = 0; angle < 360; angle++) {
            double rad = Math.toRadians(angle);
            int px = cx + (int) (radius * Math.cos(rad));
            int py = cy + (int) (radius * Math.sin(rad));
            int half = thickness / 2;
            g.fill(px - half, py - half, px + half + 1, py + half + 1, color);
        }
    }

    private void drawArc(GuiGraphics g, int cx, int cy, int radius, int thickness, int startDeg, int endDeg, int color) {
        for (int angle = startDeg; angle < endDeg; angle++) {
            double rad = Math.toRadians(angle);
            int px = cx + (int) (radius * Math.cos(rad));
            int py = cy + (int) (radius * Math.sin(rad));
            int half = thickness / 2;
            g.fill(px - half, py - half, px + half + 1, py + half + 1, color);
        }
    }

    private void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int steps = (int) Math.max(1, Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)));
        for (int s = 0; s <= steps; s++) {
            float t = (float) s / steps;
            int px = (int) (x1 + (x2 - x1) * t);
            int py = (int) (y1 + (y2 - y1) * t);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
