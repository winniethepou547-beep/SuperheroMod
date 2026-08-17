package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.PlayerReadyPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VsScreen extends Screen {

    private final String playerName;
    private final String opponentName;
    private final String modeName;
    private final String playerRank;
    private final int playerRating;
    private final int playerWins;
    private final int playerLosses;
    private final String opponentRank;
    private final int opponentRating;
    private final int opponentWins;
    private final int opponentLosses;
    private final long openedAt;

    private boolean playerReady = false;
    private boolean opponentReady = false;

    private final List<CrackSegment> crackPath = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<BackgroundEmber> embers = new ArrayList<>();

    private static final float BODY_ANGLE = 35f;
    private static final int MODEL_SCALE = 75;

    public VsScreen(String playerName, String opponentName, String modeName,
                    String playerRank, int playerRating, int playerWins, int playerLosses,
                    String opponentRank, int opponentRating, int opponentWins, int opponentLosses) {
        super(Component.literal("VS"));
        this.playerName = playerName;
        this.opponentName = opponentName;
        this.modeName = modeName;
        this.playerRank = playerRank;
        this.playerRating = playerRating;
        this.playerWins = playerWins;
        this.playerLosses = playerLosses;
        this.opponentRank = opponentRank;
        this.opponentRating = opponentRating;
        this.opponentWins = opponentWins;
        this.opponentLosses = opponentLosses;
        this.openedAt = Util.getMillis();
    }

    @Override
    protected void init() {
        super.init();
        generateCrackPath();
        generateEmbers();
    }

    private void generateCrackPath() {
        crackPath.clear();
        Random r = new Random(12345);
        int segments = 22;
        float segH = (float) this.height / segments;
        float x = this.width / 2f;
        for (int i = 0; i <= segments; i++) {
            float y = i * segH;
            float offsetX = (r.nextFloat() - 0.5f) * 28;
            if (i == 0 || i == segments) offsetX = 0;
            crackPath.add(new CrackSegment(x + offsetX, y));
        }
        particles.clear();
        for (int i = 0; i < 35; i++) {
            int segIdx = r.nextInt(crackPath.size());
            CrackSegment seg = crackPath.get(segIdx);
            float px = seg.x + (r.nextFloat() - 0.5f) * 16;
            float py = seg.y + (r.nextFloat() - 0.5f) * 8;
            float vx = (r.nextFloat() - 0.5f) * 1.2f;
            float vy = (r.nextFloat() - 0.5f) * 0.6f;
            int size = 1 + r.nextInt(3);
            float phase = r.nextFloat() * 6.28f;
            particles.add(new Particle(px, py, vx, vy, size, phase));
        }
    }

    private void generateEmbers() {
        embers.clear();
        Random r = new Random(9999);
        for (int i = 0; i < 20; i++) {
            embers.add(new BackgroundEmber(
                    r.nextFloat(), r.nextFloat(),
                    0.2f + r.nextFloat() * 0.5f,
                    r.nextFloat() * 6.28f,
                    1 + r.nextInt(2),
                    r.nextBoolean()
            ));
        }
    }

    public void updateReadyState(boolean myReady, boolean oppReady) {
        this.playerReady = myReady;
        this.opponentReady = oppReady;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !playerReady) {
            int cx = this.width / 2;
            int btnY = this.height - 28;
            int btnW = 120;
            int btnH = 20;
            int btnX = cx - btnW / 2;
            if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                playerReady = true;
                ModNetworking.CHANNEL.sendToServer(new PlayerReadyPacket());
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        long elapsed = Util.getMillis() - openedAt;
        int cx = this.width / 2;
        int cy = this.height / 2;

        renderBackground(g, cx, elapsed);
        renderCrackEffect(g, elapsed);

        renderPlayerModel(g, cx, cy, true, elapsed);
        renderPlayerModel(g, cx, cy, false, elapsed);

        renderInfoPanel(g, true, playerName, playerRank, playerRating, playerWins, playerLosses, playerReady, elapsed);
        renderInfoPanel(g, false, opponentName, opponentRank, opponentRating, opponentWins, opponentLosses, opponentReady, elapsed);

        renderVsText(g, cx, cy, elapsed);
        renderModeBanner(g, cx, elapsed);
        renderReadyButton(g, cx, mouseX, mouseY, elapsed);
    }

    private void renderBackground(GuiGraphics g, int cx, long elapsed) {
        g.fill(0, 0, this.width, this.height, 0xFF080812);

        for (int i = 0; i < cx; i++) {
            float ratio = (float) i / cx;
            int alpha = (int) (15 + ratio * 30);
            g.fill(i, 0, i + 1, this.height, (alpha << 24) | 0x1530AA);
        }
        for (int i = cx; i < this.width; i++) {
            float ratio = 1f - (float) (i - cx) / (this.width - cx);
            int alpha = (int) (15 + ratio * 30);
            g.fill(i, 0, i + 1, this.height, (alpha << 24) | 0xAA2020);
        }

        int gridColor = 0x06FFFFFF;
        for (int x = 0; x < this.width; x += 32) {
            g.fill(x, 0, x + 1, this.height, gridColor);
        }
        for (int y = 0; y < this.height; y += 32) {
            g.fill(0, y, this.width, y + 1, gridColor);
        }

        float t = elapsed / 1000f;
        for (BackgroundEmber ember : embers) {
            float drift = (float) Math.sin(t * ember.speed + ember.phase) * 12;
            float rise = ((t * ember.speed * 15) % (this.height + 30)) - 15;
            float finalY = this.height - rise + (float) Math.sin(t * 2 + ember.phase) * 4;
            float finalX = ember.baseX * this.width + drift;
            if (finalY < -10) finalY += this.height + 20;
            float alpha = (float) (0.25 + 0.25 * Math.sin(t * 3 + ember.phase));
            int a = (int) (alpha * 255);
            int color = ember.isBlue ? ((a << 24) | 0x4488FF) : ((a << 24) | 0xFF6633);
            g.fill((int) finalX, (int) finalY, (int) finalX + ember.size, (int) finalY + ember.size, color);
        }

        int vSize = 50;
        for (int i = 0; i < vSize; i++) {
            int vigAlpha = (int) ((1f - (float) i / vSize) * 160);
            int vc = (vigAlpha << 24);
            g.fill(0, i, this.width, i + 1, vc);
            g.fill(0, this.height - i - 1, this.width, this.height - i, vc);
        }
    }

    private void renderCrackEffect(GuiGraphics g, long elapsed) {
        if (crackPath.size() < 2) return;
        float reveal = Math.min(1f, elapsed / 800f);
        int vis = (int) (crackPath.size() * reveal);

        for (int i = 0; i < vis - 1 && i < crackPath.size() - 1; i++) {
            CrackSegment a = crackPath.get(i);
            CrackSegment b = crackPath.get(i + 1);
            int steps = (int) Math.max(1, Math.abs(b.y - a.y));
            for (int s = 0; s < steps; s++) {
                float t = (float) s / steps;
                int px = (int) (a.x + (b.x - a.x) * t);
                int py = (int) (a.y + (b.y - a.y) * t);
                g.fill(px - 6, py, px + 6, py + 1, 0x12FFAA44);
                g.fill(px - 3, py, px + 3, py + 1, 0x28FFCC66);
            }
        }

        for (int i = 0; i < vis - 1 && i < crackPath.size() - 1; i++) {
            CrackSegment a = crackPath.get(i);
            CrackSegment b = crackPath.get(i + 1);
            int steps = (int) Math.max(1, Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)));
            float shimmer = (float) (0.8 + 0.2 * Math.sin(elapsed / 150.0 + a.y * 0.1));
            int mainAlpha = (int) (shimmer * 220);
            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                int px = (int) (a.x + (b.x - a.x) * t);
                int py = (int) (a.y + (b.y - a.y) * t);
                g.fill(px - 1, py, px + 2, py + 1, (mainAlpha << 24) | 0xFFEEAA);
                g.fill(px, py, px + 1, py + 1, 0xFFFFFFDD);
            }
        }

        Random br = new Random(777);
        for (int i = 1; i < vis && i < crackPath.size(); i += 3) {
            CrackSegment seg = crackPath.get(i);
            int branchLen = 8 + br.nextInt(16);
            float angle = br.nextFloat() * 1.2f + 0.3f;
            if (br.nextBoolean()) angle = -angle;
            float branchReveal = Math.min(1f, (elapsed - 400f) / 600f);
            if (branchReveal > 0) {
                int ex = (int) (seg.x + Math.cos(angle) * branchLen * branchReveal);
                int ey = (int) (seg.y + Math.sin(angle) * branchLen * branchReveal);
                drawLine(g, (int) seg.x, (int) seg.y, ex, ey, 0x99FFDD88);
            }
        }

        float time = elapsed / 1000f;
        for (Particle p : particles) {
            float life = (float) Math.sin(time * 2 + p.phase);
            if (life < 0) continue;
            float px = p.baseX + (float) Math.sin(time * 1.5 + p.phase) * p.vx * 12;
            float py = p.baseY + (float) Math.cos(time * 1.2 + p.phase) * p.vy * 12;
            px += p.vx * time * 6;
            int alpha = (int) (life * 180);
            g.fill((int) px, (int) py, (int) px + p.size, (int) py + p.size, (alpha << 24) | 0xFFCC55);
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

    private void renderPlayerModel(GuiGraphics g, int cx, int cy, boolean isLeft, long elapsed) {
        float slideIn = Math.min(1f, elapsed / 600f);
        float eased = 1f - (1f - slideIn) * (1f - slideIn);
        int slideOffset = (int) ((1f - eased) * (isLeft ? -80 : 80));

        int sideCx = isLeft ? (int) (this.width * 0.33) : (int) (this.width * 0.67);
        int modelX = sideCx + slideOffset;
        int modelY = cy + 65;
        int scale = 105;

        Minecraft mc = Minecraft.getInstance();
        Player localPlayer = mc.player;

        if (localPlayer != null) {
            float origYRot = localPlayer.getYRot();
            float origBodyRot = localPlayer.yBodyRot;
            float origHeadRot = localPlayer.yHeadRot;
            float origHeadRotO = localPlayer.yHeadRotO;

            float bodyAngle = isLeft ? -BODY_ANGLE : BODY_ANGLE;
            localPlayer.setYRot(bodyAngle);
            localPlayer.yBodyRot = bodyAngle;
            localPlayer.yHeadRot = bodyAngle;
            localPlayer.yHeadRotO = bodyAngle;

            InventoryScreen.renderEntityInInventoryFollowsMouse(
                    g, modelX, modelY, scale,
                    -5.0f, 0.0f,
                    localPlayer
            );

            localPlayer.setYRot(origYRot);
            localPlayer.yBodyRot = origBodyRot;
            localPlayer.yHeadRot = origHeadRot;
            localPlayer.yHeadRotO = origHeadRotO;
        }

        // NOT READY / READY under model
        boolean ready = isLeft ? playerReady : opponentReady;
        int readyY = modelY + 18;
        if (ready) {
            float pulse = (float) (0.7 + 0.3 * Math.sin(elapsed / 200.0));
            int ga = (int) (pulse * 255);
            g.fill(sideCx - 40, readyY, sideCx + 40, readyY + 16, 0xCC005500);
            g.fill(sideCx - 40, readyY, sideCx + 40, readyY + 1, (ga << 24) | 0x00FF00);
            g.fill(sideCx - 40, readyY + 15, sideCx + 40, readyY + 16, 0xFF004400);
            g.drawCenteredString(this.font, "§a§lREADY", sideCx, readyY + 4, 0xFF00FF00);
        } else {
            g.fill(sideCx - 45, readyY, sideCx + 45, readyY + 16, 0x80330000);
            g.fill(sideCx - 45, readyY, sideCx + 45, readyY + 1, 0xAAFF4444);
            g.fill(sideCx - 45, readyY + 15, sideCx + 45, readyY + 16, 0xFF220000);
            g.drawCenteredString(this.font, "§c§lNOT READY", sideCx, readyY + 4, 0xFFFF4444);
        }
    }

    private void renderInfoPanel(GuiGraphics g, boolean isLeft,
                                  String name, String rank, int rating,
                                  int wins, int losses, boolean ready, long elapsed) {
        float slideIn = Math.min(1f, elapsed / 500f);
        float eased = 1f - (1f - slideIn) * (1f - slideIn);
        int alpha = (int) (eased * 255);

        int panelW = 110;
        int panelH = this.height - 50;
        int panelX = isLeft ? 3 : this.width - panelW - 3;
        int panelY = 22;

        int bgColor = isLeft ? 0xBB0C1A55 : 0xBB551A0C;
        int borderColor = isLeft ? 0xFF3366FF : 0xFFFF4444;
        int accentColor = isLeft ? 0xFF2244AA : 0xFFAA2222;

        // Outer glow
        int glowA = (int) (30 + 15 * Math.sin(elapsed / 300.0));
        int glowColor = (glowA << 24) | (isLeft ? 0x3366FF : 0xFF4444);
        g.fill(panelX - 3, panelY - 3, panelX + panelW + 3, panelY + panelH + 3, glowColor);

        // Panel background
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, bgColor);

        // Border frame - double line style
        // Outer border
        g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY, borderColor);
        g.fill(panelX - 1, panelY + panelH, panelX + panelW + 1, panelY + panelH + 1, borderColor);
        g.fill(panelX - 1, panelY - 1, panelX, panelY + panelH + 1, borderColor);
        g.fill(panelX + panelW, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, borderColor);

        // Inner border (2px inset)
        int inset = 3;
        int innerColor = 0x60FFFFFF;
        g.fill(panelX + inset, panelY + inset, panelX + panelW - inset, panelY + inset + 1, innerColor);
        g.fill(panelX + inset, panelY + panelH - inset - 1, panelX + panelW - inset, panelY + panelH - inset, innerColor);
        g.fill(panelX + inset, panelY + inset, panelX + inset + 1, panelY + panelH - inset, innerColor);
        g.fill(panelX + panelW - inset - 1, panelY + inset, panelX + panelW - inset, panelY + panelH - inset, innerColor);

        // Corner accents (small L shapes)
        int cornerLen = 8;
        // Top-left
        g.fill(panelX - 2, panelY - 2, panelX + cornerLen, panelY - 1, borderColor);
        g.fill(panelX - 2, panelY - 2, panelX - 1, panelY + cornerLen, borderColor);
        // Top-right
        g.fill(panelX + panelW - cornerLen, panelY - 2, panelX + panelW + 2, panelY - 1, borderColor);
        g.fill(panelX + panelW + 1, panelY - 2, panelX + panelW + 2, panelY + cornerLen, borderColor);
        // Bottom-left
        g.fill(panelX - 2, panelY + panelH + 1, panelX + cornerLen, panelY + panelH + 2, borderColor);
        g.fill(panelX - 2, panelY + panelH - cornerLen, panelX - 1, panelY + panelH + 2, borderColor);
        // Bottom-right
        g.fill(panelX + panelW - cornerLen, panelY + panelH + 1, panelX + panelW + 2, panelY + panelH + 2, borderColor);
        g.fill(panelX + panelW + 1, panelY + panelH - cornerLen, panelX + panelW + 2, panelY + panelH + 2, borderColor);

        // Top accent bar
        g.fill(panelX + 10, panelY - 2, panelX + panelW - 10, panelY - 1, accentColor);

        int textX = panelX + panelW / 2;
        int y = panelY + 10;

        // Name (scaled up)
        g.pose().pushPose();
        g.pose().translate(textX, y, 0);
        float nameScale = 1.3f;
        g.pose().scale(nameScale, nameScale, 1f);
        int nameW = this.font.width(name);
        g.drawString(this.font, "§l" + name, -nameW / 2 + 1, 0, 0xFF000000, false);
        g.drawString(this.font, "§l" + name, -nameW / 2, -1, (alpha << 24) | 0xFFFFFF, false);
        g.pose().popPose();
        y += 18;

        // Decorative separator
        g.fill(panelX + 8, y, panelX + panelW - 8, y + 1, borderColor);
        g.fill(panelX + 15, y + 1, panelX + panelW - 15, y + 2, 0x30FFFFFF);
        y += 8;

        // Rank (scaled up)
        g.pose().pushPose();
        g.pose().translate(textX, y, 0);
        float rankScale = 1.2f;
        g.pose().scale(rankScale, rankScale, 1f);
        int rankW = this.font.width(rank);
        g.drawString(this.font, rank, -rankW / 2, 0, (alpha << 24) | 0xFFFFFF, false);
        g.pose().popPose();
        y += 16;

        // Rating number
        String ratingStr = String.valueOf(rating) + " MMR";
        int ratingW = this.font.width(ratingStr);
        g.drawString(this.font, ratingStr, textX - ratingW / 2 + 1, y, 0xFF000000, false);
        g.drawString(this.font, ratingStr, textX - ratingW / 2, y - 1, (alpha << 24) | 0xFFDD44, false);
        y += 16;

        // Separator
        g.fill(panelX + 12, y, panelX + panelW - 12, y + 1, 0x30FFFFFF);
        y += 6;

        // W/L label
        g.drawCenteredString(this.font, "§7W / L", textX, y, (alpha << 24) | 0xAAAAAA);
        y += 12;

        // W/L numbers (scaled)
        g.pose().pushPose();
        g.pose().translate(textX, y, 0);
        float wlScale = 1.4f;
        g.pose().scale(wlScale, wlScale, 1f);
        String wl = "§a" + wins + " §7/ §c" + losses;
        int wlW = this.font.width(wins + " / " + losses);
        g.drawString(this.font, wl, -wlW / 2, 0, 0xFFFFFFFF, false);
        g.pose().popPose();
        y += 22;

        // Separator
        g.fill(panelX + 12, y, panelX + panelW - 12, y + 1, 0x30FFFFFF);
        y += 6;

        // Best character label
        g.drawCenteredString(this.font, "§7Best Hero", textX, y, (alpha << 24) | 0xAAAAAA);
        y += 13;

        // Character icon placeholder (larger)
        int iconSize = 20;
        int iconX = textX - iconSize / 2;
        int iconY = y;

        // Icon outer frame
        g.fill(iconX - 2, iconY - 2, iconX + iconSize + 2, iconY + iconSize + 2, borderColor);
        g.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, accentColor);
        g.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF1A1A2A);

        // Placeholder "?"
        g.pose().pushPose();
        g.pose().translate(iconX + iconSize / 2f, iconY + iconSize / 2f - 4, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(this.font, "?", -this.font.width("?") / 2, 0, 0xFFAAAAAA, false);
        g.pose().popPose();
    }

    private void renderVsText(GuiGraphics g, int cx, int cy, long elapsed) {
        float appear = Math.min(1f, elapsed / 700f);
        if (appear < 0.01f) return;

        float pulse = (float) (1.0 + 0.05 * Math.sin(elapsed / 250.0));
        float scale = appear * 4.5f * pulse;
        float eased = 1f - (1f - appear) * (1f - appear) * (1f - appear);

        g.pose().pushPose();
        g.pose().translate(cx, cy - 10, 200);
        g.pose().scale(scale, scale, 1f);

        String vs = "VS";
        int w = this.font.width(vs);
        int hx = -(w / 2);
        int hy = -(this.font.lineHeight / 2);

        int ga = (int) (eased * 80);
        int glow = (ga << 24) | 0xFF4400;
        g.drawString(this.font, vs, hx - 1, hy - 1, glow, false);
        g.drawString(this.font, vs, hx + 1, hy + 1, glow, false);
        g.drawString(this.font, vs, hx - 1, hy + 1, glow, false);
        g.drawString(this.font, vs, hx + 1, hy - 1, glow, false);
        g.drawString(this.font, vs, hx + 2, hy + 2, 0xFF000000, false);

        float cp = (float) (0.5 + 0.5 * Math.sin(elapsed / 400.0));
        int r = 255;
        int gr = (int) (80 + cp * 80);
        int b = (int) (20 + cp * 30);
        g.drawString(this.font, vs, hx, hy, 0xFF000000 | (r << 16) | (gr << 8) | b, false);

        g.pose().popPose();
    }

    private void renderModeBanner(GuiGraphics g, int cx, long elapsed) {
        float appear = Math.min(1f, elapsed / 500f);
        int alpha = (int) (appear * 255);

        int bannerY = 3;
        float bannerScale = 2.2f;
        int modeW = (int) (this.font.width(modeName) * bannerScale);
        int bx1 = cx - modeW / 2 - 28;
        int bx2 = cx + modeW / 2 + 28;

        g.fill(bx1, bannerY, bx2, bannerY + 24, (int) (appear * 200) << 24);
        g.fill(bx1, bannerY + 23, bx2, bannerY + 24, (alpha << 24) | 0xFFAA00);
        g.fill(bx1, bannerY, bx2, bannerY + 1, (alpha << 24) | 0xFFAA00);
        g.fill(bx1, bannerY + 22, bx2, bannerY + 23, (alpha << 24) | 0xCC8800);

        g.pose().pushPose();
        g.pose().translate(cx, bannerY + 5, 0);
        g.pose().scale(bannerScale, bannerScale, 1f);
        int tw = this.font.width(modeName);
        g.drawString(this.font, modeName, -tw / 2 + 1, 1, 0xFF000000, false);
        g.drawString(this.font, modeName, -tw / 2, 0, (alpha << 24) | 0xFFDD55, false);
        g.pose().popPose();
    }

    private void renderReadyButton(GuiGraphics g, int cx, int mouseX, int mouseY, long elapsed) {
        int btnW = 120;
        int btnH = 20;
        int btnX = cx - btnW / 2;
        int btnY = this.height - 28;

        boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        if (playerReady) {
            float pulse = (float) (0.6 + 0.4 * Math.sin(elapsed / 200.0));
            int ga = (int) (pulse * 255);
            g.fill(btnX - 1, btnY - 1, btnX + btnW + 1, btnY + btnH + 1, (ga << 24) | 0x00AA00);
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xDD006600);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, 0xFF00FF00);
            g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, 0xFF004400);
            g.drawCenteredString(this.font, "§a§l✔ READY", cx, btnY + 6, 0xFF00FF00);
        } else {
            int bg = hovered ? 0xDD442222 : 0xDD331111;
            int border = hovered ? 0xFFFF6666 : 0xFFCC3333;
            g.fill(btnX - 1, btnY - 1, btnX + btnW + 1, btnY + btnH + 1, border);
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, 0xFFFF4444);
            g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, 0xFF220000);
            g.drawCenteredString(this.font, "§c✘ NOT READY", cx, btnY + 6, 0xFFFF4444);
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

    private record CrackSegment(float x, float y) {}
    private record Particle(float baseX, float baseY, float vx, float vy, int size, float phase) {}
    private record BackgroundEmber(float baseX, float baseY, float speed, float phase, int size, boolean isBlue) {}
}
