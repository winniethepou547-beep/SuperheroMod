package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.core.matchmaking.GameMap;
import com.FIRNI.superheromod.core.matchmaking.MapRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MapVoteScreen extends Screen {

    private static final int SWEEP_MS = 3200;
    private static final int PAUSE_MS = 500;
    private static final int WINNER_MS = 2200;
    private static final int POST_DELAY_MS = 1000;
    private static final int TOTAL_MS = SWEEP_MS + PAUSE_MS + WINNER_MS + POST_DELAY_MS;

    private static final int CARD_W = 130;
    private static final int CARD_H = 175;
    private static final int CARD_GAP = 24;

    private final int winningIndex;
    private final long openedAtMillis;
    private final List<WinnerParticle> winnerParticles = new ArrayList<>();

    public MapVoteScreen(int winningIndex) {
        super(Component.literal("Harita Secimi"));
        this.winningIndex = winningIndex;
        this.openedAtMillis = Util.getMillis();
    }

    @Override
    protected void init() {
        super.init();
        generateWinnerParticles();
    }

    private void generateWinnerParticles() {
        winnerParticles.clear();
        Random r = new Random(42);
        for (int i = 0; i < 40; i++) {
            winnerParticles.add(new WinnerParticle(
                    r.nextFloat() * 2 - 1f,
                    r.nextFloat() * 2 - 1f,
                    (r.nextFloat() - 0.5f) * 2f,
                    -0.5f - r.nextFloat() * 2f,
                    r.nextFloat() * 6.28f,
                    1 + r.nextInt(3),
                    0.3f + r.nextFloat() * 0.7f
            ));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        List<GameMap> maps = MapRegistry.MAPS;
        long elapsed = Util.getMillis() - openedAtMillis;
        int cx = this.width / 2;
        int cy = this.height / 2;

        renderBackground(g, elapsed);

        int totalW = maps.size() * CARD_W + (maps.size() - 1) * CARD_GAP;
        int startX = cx - totalW / 2;
        int rowY = cy - CARD_H / 2 + 10;

        if (elapsed < SWEEP_MS) {
            renderSweepPhase(g, maps, elapsed, startX, rowY, cx);
        } else if (elapsed < SWEEP_MS + PAUSE_MS) {
            renderPausePhase(g, maps, elapsed, startX, rowY, cx);
        } else if (elapsed < TOTAL_MS) {
            renderWinnerPhase(g, maps, elapsed, startX, rowY, cx, cy);
        }

        if (elapsed > TOTAL_MS) {
            this.minecraft.setScreen(null);
        }
    }

    private void renderBackground(GuiGraphics g, long elapsed) {
        g.fill(0, 0, this.width, this.height, 0xF0080810);

        float t = elapsed / 1000f;
        Random r = new Random(777);
        for (int i = 0; i < 12; i++) {
            float bx = r.nextFloat() * this.width;
            float by = r.nextFloat() * this.height;
            float drift = (float) Math.sin(t * 0.5 + r.nextFloat() * 6.28) * 20;
            float rise = (t * 10 * (0.3f + r.nextFloat() * 0.5f)) % (this.height + 40) - 20;
            float fy = this.height - rise;
            float fx = bx + drift;
            float alpha = (float) (0.15 + 0.1 * Math.sin(t * 2 + r.nextFloat() * 6.28));
            int a = (int) (alpha * 255);
            int size = 1 + r.nextInt(2);
            g.fill((int) fx, (int) fy, (int) fx + size, (int) fy + size, (a << 24) | 0x886622);
        }

        int vSize = 60;
        for (int i = 0; i < vSize; i++) {
            int va = (int) ((1f - (float) i / vSize) * 180);
            g.fill(0, i, this.width, i + 1, (va << 24));
            g.fill(0, this.height - i - 1, this.width, this.height - i, (va << 24));
        }
    }

    private void renderSweepPhase(GuiGraphics g, List<GameMap> maps, long elapsed, int startX, int rowY, int cx) {
        float t = elapsed / (float) SWEEP_MS;

        // Cubic ease-out: starts fast, slows down at the end
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);

        // Total sweep distance - more cycles for faster feel
        float totalStops = maps.size() * 4 + winningIndex;
        float currentPos = eased * totalStops;
        int highlightIndex = ((int) currentPos) % maps.size();

        // "HARITA SECiLiYOR..." title
        renderTitle(g, "HARITA SECiLiYOR...", cx, rowY - 35, elapsed, true);

        for (int i = 0; i < maps.size(); i++) {
            int x = startX + i * (CARD_W + CARD_GAP);
            boolean highlighted = i == highlightIndex;
            float glowIntensity = 0f;
            if (highlighted) {
                float frac = currentPos - (int) currentPos;
                glowIntensity = 1f - Math.abs(frac - 0.5f) * 2f;
            }
            drawMapCard(g, maps.get(i), x, rowY, 1f, highlighted, glowIntensity, false, 0f, elapsed);
        }
    }

    private void renderPausePhase(GuiGraphics g, List<GameMap> maps, long elapsed, int startX, int rowY, int cx) {
        renderTitle(g, "HARITA SECiLiYOR...", cx, rowY - 35, elapsed, false);

        float pauseT = (elapsed - SWEEP_MS) / (float) PAUSE_MS;
        float pulse = (float) (0.7 + 0.3 * Math.sin(pauseT * Math.PI * 4));

        for (int i = 0; i < maps.size(); i++) {
            int x = startX + i * (CARD_W + CARD_GAP);
            boolean isWinner = i == winningIndex;
            drawMapCard(g, maps.get(i), x, rowY, 1f, isWinner, isWinner ? pulse : 0f, false, 0f, elapsed);
        }
    }

    private void renderWinnerPhase(GuiGraphics g, List<GameMap> maps, long elapsed, int startX, int rowY, int cx, int cy) {
        float winT = (elapsed - SWEEP_MS - PAUSE_MS) / (float) WINNER_MS;
        float eased = 1f - (1f - Math.min(1f, winT * 1.5f)) * (1f - Math.min(1f, winT * 1.5f));

        int winRowX = startX + winningIndex * (CARD_W + CARD_GAP);
        int centerX = cx - CARD_W / 2;
        int centerY = cy - CARD_H / 2;

        // Fade and slide out non-winners
        for (int i = 0; i < maps.size(); i++) {
            if (i == winningIndex) continue;
            float alpha = 1f - eased;
            if (alpha > 0.01f) {
                int x = startX + i * (CARD_W + CARD_GAP);
                int slideOut = (int) (eased * (i < winningIndex ? -60 : 60));
                drawMapCard(g, maps.get(i), x + slideOut, rowY, alpha, false, 0f, false, 0f, elapsed);
            }
        }

        // Move winner to center
        int wx = (int) lerp(winRowX, centerX, eased);
        int wy = (int) lerp(rowY, centerY, eased);

        // Winner glow ring
        if (eased > 0.3f) {
            float glowA = (eased - 0.3f) / 0.7f;
            renderWinnerGlow(g, wx, wy, CARD_W, CARD_H, glowA, elapsed);
        }

        // Winner card
        float nameT = winT > 0.4f ? Math.min(1f, (winT - 0.4f) / 0.3f) : 0f;
        drawMapCard(g, maps.get(winningIndex), wx, wy, 1f, true, 1f, true, nameT, elapsed);

        // "KAZANAN" text
        if (nameT > 0) {
            float textEased = 1f - (1f - nameT) * (1f - nameT);
            int textA = (int) (textEased * 255);
            int slideUp = (int) ((1f - textEased) * 15);

            g.pose().pushPose();
            g.pose().translate(cx, wy + CARD_H + 30 + slideUp, 0);
            float textScale = 2.0f;
            g.pose().scale(textScale, textScale, 1f);
            String winner = "KAZANAN";
            int tw = this.font.width(winner);
            // Gold shadow
            g.drawString(this.font, winner, -tw / 2 + 1, 1, (textA << 24) | 0x000000, false);
            // Gold text
            float goldPulse = (float) (0.8 + 0.2 * Math.sin(elapsed / 200.0));
            int gr = (int) (180 + goldPulse * 75);
            int gb = (int) (40 + goldPulse * 40);
            g.drawString(this.font, winner, -tw / 2, 0, (textA << 24) | (0xFF << 16) | (gr << 8) | gb, false);
            g.pose().popPose();
        }

        // Winner particles
        if (eased > 0.5f) {
            renderWinnerParticles(g, wx + CARD_W / 2, wy + CARD_H / 2, elapsed - SWEEP_MS - PAUSE_MS);
        }
    }

    private void renderTitle(GuiGraphics g, String text, int cx, int y, long elapsed, boolean animateDots) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        float titleScale = 1.5f;
        g.pose().scale(titleScale, titleScale, 1f);

        String displayText = text;
        if (animateDots) {
            int dots = (int) ((elapsed / 400) % 4);
            displayText = "HARITA SECiLiYOR" + ".".repeat(dots);
        }

        int tw = this.font.width(displayText);
        g.drawString(this.font, displayText, -tw / 2 + 1, 1, 0xFF000000, false);
        g.drawString(this.font, displayText, -tw / 2, 0, 0xFFEECC44, false);
        g.pose().popPose();
    }

    private void drawMapCard(GuiGraphics g, GameMap map, int x, int y, float alpha, boolean highlighted,
                              float glowIntensity, boolean isWinnerState, float nameProgress, long elapsed) {
        int a = clamp((int) (alpha * 255));

        // Card glow behind (gold for highlighted)
        if (glowIntensity > 0.05f) {
            int glowA = clamp((int) (glowIntensity * alpha * 60));
            int glowColor = (glowA << 24) | 0xFFAA22;
            g.fill(x - 4, y - 4, x + CARD_W + 4, y + CARD_H + 4, glowColor);
            int glowA2 = clamp((int) (glowIntensity * alpha * 30));
            g.fill(x - 7, y - 7, x + CARD_W + 7, y + CARD_H + 7, (glowA2 << 24) | 0xFFCC44);
        }

        // Card background - dark with map color tint
        int mapR = (map.frameColor() >> 16) & 0xFF;
        int mapG = (map.frameColor() >> 8) & 0xFF;
        int mapB = map.frameColor() & 0xFF;
        int bgR = mapR / 6;
        int bgG = mapG / 6;
        int bgB = mapB / 6;
        int bgColor = (clamp((int) (alpha * 220)) << 24) | (bgR << 16) | (bgG << 8) | bgB;
        g.fill(x, y, x + CARD_W, y + CARD_H, bgColor);

        // Image placeholder area (upper 60% of card)
        int imgH = (int) (CARD_H * 0.62);
        int imgMargin = 6;
        int imgX = x + imgMargin;
        int imgY = y + imgMargin;
        int imgW = CARD_W - imgMargin * 2;
        int imgAreaH = imgH - imgMargin;

        int imgBg = (clamp((int) (alpha * 180)) << 24) | (mapR / 4 << 16) | (mapG / 4 << 8) | (mapB / 4);
        g.fill(imgX, imgY, imgX + imgW, imgY + imgAreaH, imgBg);

        // Draw map icon inside image area
        drawMapIcon(g, map.id(), imgX + 4, imgY + 4, imgW - 8, imgAreaH - 8, alpha);

        // Thin image border
        int imgBorderA = clamp((int) (alpha * 100));
        int imgBorder = (imgBorderA << 24) | 0x888888;
        drawBorder(g, imgX, imgY, imgW, imgAreaH, 1, imgBorder);

        // Map name area (lower part)
        int nameY = y + imgH + 8;
        g.pose().pushPose();
        g.pose().translate(x + CARD_W / 2f, nameY, 0);
        float nameScale = 1.1f;
        g.pose().scale(nameScale, nameScale, 1f);
        int nameA = clamp((int) (alpha * 255));
        int nw = this.font.width(map.displayName());
        g.drawString(this.font, map.displayName(), -nw / 2, 0, (nameA << 24) | 0xFFFFFF, false);
        g.pose().popPose();

        // Map type label placeholder
        int typeY = nameY + 16;
        String typeLabel = getMapTypeLabel(map.id());
        int typeA = clamp((int) (alpha * 160));
        int typW = this.font.width(typeLabel);
        g.drawString(this.font, typeLabel, x + CARD_W / 2 - typW / 2, typeY, (typeA << 24) | 0xAAAA88, false);

        // Card border frame
        if (highlighted || isWinnerState) {
            // Gold frame for highlighted/winner
            int borderA = clamp((int) (alpha * 255));
            float pulse = (float) (0.7 + 0.3 * Math.sin(elapsed / 150.0));
            int borderGold = (borderA << 24) | ((int) (200 + pulse * 55) << 16) | ((int) (150 + pulse * 40) << 8) | (int) (20 + pulse * 30);
            drawBorder(g, x, y, CARD_W, CARD_H, 2, borderGold);

            // Corner accents
            int cornerLen = 12;
            int cA = clamp((int) (alpha * glowIntensity * 255));
            int cornerColor = (cA << 24) | 0xFFDD44;
            // TL
            g.fill(x - 2, y - 2, x + cornerLen, y, cornerColor);
            g.fill(x - 2, y - 2, x, y + cornerLen, cornerColor);
            // TR
            g.fill(x + CARD_W - cornerLen, y - 2, x + CARD_W + 2, y, cornerColor);
            g.fill(x + CARD_W, y - 2, x + CARD_W + 2, y + cornerLen, cornerColor);
            // BL
            g.fill(x - 2, y + CARD_H, x + cornerLen, y + CARD_H + 2, cornerColor);
            g.fill(x - 2, y + CARD_H - cornerLen, x, y + CARD_H + 2, cornerColor);
            // BR
            g.fill(x + CARD_W - cornerLen, y + CARD_H, x + CARD_W + 2, y + CARD_H + 2, cornerColor);
            g.fill(x + CARD_W, y + CARD_H - cornerLen, x + CARD_W + 2, y + CARD_H + 2, cornerColor);
        } else {
            int borderA = clamp((int) (alpha * 120));
            int border = (borderA << 24) | 0x555555;
            drawBorder(g, x, y, CARD_W, CARD_H, 1, border);
        }

        // Sweep shimmer line (animated highlight traveling across card)
        if (highlighted && glowIntensity > 0.1f) {
            int shimmerX = (int) (x + CARD_W * ((elapsed / 200.0) % 1.0));
            int shimA = clamp((int) (glowIntensity * alpha * 80));
            g.fill(shimmerX - 2, y, shimmerX + 2, y + CARD_H, (shimA << 24) | 0xFFEE88);
        }
    }

    private String getMapTypeLabel(String mapId) {
        return switch (mapId) {
            case "sehir" -> "ARENA";
            case "liman" -> "ARENA";
            case "kanyon" -> "ARENA";
            default -> "ARENA";
        };
    }

    private void renderWinnerGlow(GuiGraphics g, int x, int y, int w, int h, float intensity, long elapsed) {
        float pulse = (float) (0.6 + 0.4 * Math.sin(elapsed / 200.0));
        int baseA = (int) (intensity * pulse * 40);

        for (int ring = 3; ring >= 1; ring--) {
            int offset = ring * 4;
            int ringA = clamp(baseA / ring);
            int color = (ringA << 24) | 0xFFAA22;
            g.fill(x - offset, y - offset, x + w + offset, y - offset + 1, color);
            g.fill(x - offset, y + h + offset - 1, x + w + offset, y + h + offset, color);
            g.fill(x - offset, y - offset, x - offset + 1, y + h + offset, color);
            g.fill(x + w + offset - 1, y - offset, x + w + offset, y + h + offset, color);
        }
    }

    private void renderWinnerParticles(GuiGraphics g, int cx, int cy, long winElapsed) {
        float t = winElapsed / 1000f;
        for (WinnerParticle p : winnerParticles) {
            float life = (float) (0.5 + 0.5 * Math.sin(t * 3 + p.phase));
            float px = cx + p.baseX * 100 + (float) Math.sin(t * 1.5 + p.phase) * p.vx * 30;
            float py = cy + p.baseY * 120 + (float) Math.cos(t * 1.2 + p.phase) * p.vy * 20;
            px += p.vx * t * 15;
            py += p.vy * t * 10;

            int alpha = (int) (life * p.brightness * 200);
            alpha = clamp(alpha);

            // Gold sparkle
            int r = 255;
            int gr = (int) (180 + life * 75);
            int b = (int) (20 + life * 40);
            int color = (alpha << 24) | (r << 16) | (gr << 8) | b;
            g.fill((int) px, (int) py, (int) px + p.size, (int) py + p.size, color);

            // Bright core
            if (p.size > 1) {
                int coreA = clamp((int) (life * p.brightness * 120));
                g.fill((int) px + 1, (int) py + 1, (int) px + p.size - 1, (int) py + p.size - 1,
                        (coreA << 24) | 0xFFFFCC);
            }
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int thickness, int color) {
        g.fill(x, y, x + w, y + thickness, color);
        g.fill(x, y + h - thickness, x + w, y + h, color);
        g.fill(x, y, x + thickness, y + h, color);
        g.fill(x + w - thickness, y, x + w, y + h, color);
    }

    private static void drawMapIcon(GuiGraphics g, String mapId, int x, int y, int w, int h, float alpha) {
        int a = clamp((int) (alpha * 140));
        int silhouette = (a << 24) | 0x0a0f18;
        switch (mapId) {
            case "sehir" -> drawSkyline(g, x, y, w, h, silhouette);
            case "liman" -> drawHarborIcon(g, x, y, w, h, silhouette);
            case "kanyon" -> drawMountains(g, x, y, w, h, silhouette);
            default -> {}
        }
    }

    private static void drawSkyline(GuiGraphics g, int x, int y, int w, int h, int color) {
        Random random = new Random(17);
        int bars = 7;
        int barW = Math.max(2, w / bars);
        for (int i = 0; i < bars; i++) {
            int bh = h / 3 + random.nextInt(Math.max(1, h / 2));
            int bx = x + i * barW;
            g.fill(bx + 1, y + h - bh, bx + barW - 1, y + h, color);
        }
    }

    private static void drawHarborIcon(GuiGraphics g, int x, int y, int w, int h, int color) {
        int waterY = y + h * 2 / 3;
        g.fill(x, waterY, x + w, y + h, color);
        int craneX = x + w * 3 / 4;
        g.fill(craneX, y + h / 4, craneX + 3, waterY, color);
        g.fill(craneX - 22, y + h / 4, craneX + 4, y + h / 4 + 3, color);
    }

    private static void drawMountains(GuiGraphics g, int x, int y, int w, int h, int color) {
        for (int col = 0; col < w; col += 2) {
            double t = (double) col / w;
            double peak1 = Math.abs(Math.sin(t * Math.PI * 2.3));
            double peak2 = Math.abs(Math.sin(t * Math.PI * 1.1 + 1.5));
            double mix = Math.max(peak1, peak2 * 0.8);
            int barH = (int) (h * 0.25 + mix * h * 0.55);
            g.fill(x + col, y + h - barH, x + col + 2, y + h, color);
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

    private record WinnerParticle(float baseX, float baseY, float vx, float vy, float phase, int size, float brightness) {}
}
