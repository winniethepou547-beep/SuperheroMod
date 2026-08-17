package com.FIRNI.superheromod.client;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mac sirasinda ekranin ust-ortasinda gosterilen skor tablosu.
 * Format: "PlayerA  X - X  PlayerB"
 * Koyu yari-saydam arka plan + beyaz metin, okunabilir ama oyunu engellemez.
 * Scoreboard verisi ScoreboardUpdatePacket ile sunucudan gelir.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ScoreboardOverlay {

    private static boolean active = false;
    private static String leftName = "";
    private static int leftKills = 0;
    private static String rightName = "";
    private static int rightKills = 0;

    // Mac sonu mesaji
    private static boolean showEndMessage = false;
    private static String endMessage = "";
    private static int endMessageColor = 0xFFFFFF;
    private static long endMessageStartMs = 0;
    private static final int END_MESSAGE_DURATION_MS = 3000;

    private ScoreboardOverlay() {
    }

    /**
     * Skor guncellemesi geldiginde cagirilir. Eger skorboard henuz acik degilse acar.
     */
    public static void update(String playerName, int playerKills, String opponentName, int opponentKills) {
        active = true;
        leftName = playerName;
        leftKills = playerKills;
        rightName = opponentName;
        rightKills = opponentKills;
    }

    /**
     * Mac bittiginde cagirilir. Kazanan mesajini gosterir, sonra gizler.
     */
    public static void showEnd(String winnerName, boolean youWon) {
        showEndMessage = true;
        if (youWon) {
            endMessage = "KAZANDIN!";
            endMessageColor = 0x55FF55; // yesil
        } else {
            endMessage = winnerName + " kazandi";
            endMessageColor = 0xFF5555; // kirmizi
        }
        endMessageStartMs = Util.getMillis();
    }

    /**
     * Skor tablosunu kapatir (mac bittikten sonra hub'a donunce).
     */
    public static void hide() {
        active = false;
        showEndMessage = false;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!active && !showEndMessage) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();

        if (active) {
            renderScoreboard(graphics, font, screenW);
        }

        if (showEndMessage) {
            long elapsed = Util.getMillis() - endMessageStartMs;
            if (elapsed > END_MESSAGE_DURATION_MS) {
                showEndMessage = false;
                // Skorboard'u da kapat (mac bitti)
                active = false;
            } else {
                renderEndMessage(graphics, font, screenW, elapsed);
            }
        }
    }

    private static void renderScoreboard(GuiGraphics graphics, Font font, int screenW) {
        // "PlayerA  3 - 1  PlayerB" formatinda metin
        String scoreText = leftName + "  " + leftKills + " - " + rightKills + "  " + rightName;
        int textWidth = font.width(scoreText);

        int paddingH = 8;
        int paddingV = 4;
        int boxWidth = textWidth + paddingH * 2;
        int boxHeight = font.lineHeight + paddingV * 2;
        int boxX = (screenW - boxWidth) / 2;
        int boxY = 4; // ekranin ust kenarinda, 4px bosluk

        // Arka plan (koyu, yari-saydam)
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xAA000000);

        // Ince cizgi kenarligi
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFF444444);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0xFF444444);
        graphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, 0xFF444444);
        graphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF444444);

        // Sol oyuncu ismi (beyaz)
        int textX = boxX + paddingH;
        int textY = boxY + paddingV;
        graphics.drawString(font, leftName + "  ", textX, textY, 0xFFFFFF, true);
        int afterLeftName = textX + font.width(leftName + "  ");

        // Sol skor (sari)
        String leftScore = String.valueOf(leftKills);
        graphics.drawString(font, leftScore, afterLeftName, textY, 0xFFFF55, true);
        int afterLeftScore = afterLeftName + font.width(leftScore);

        // Tire (gri)
        String separator = " - ";
        graphics.drawString(font, separator, afterLeftScore, textY, 0xAAAAAA, true);
        int afterSep = afterLeftScore + font.width(separator);

        // Sag skor (sari)
        String rightScore = String.valueOf(rightKills);
        graphics.drawString(font, rightScore, afterSep, textY, 0xFFFF55, true);
        int afterRightScore = afterSep + font.width(rightScore);

        // Sag oyuncu ismi (beyaz)
        graphics.drawString(font, "  " + rightName, afterRightScore, textY, 0xFFFFFF, true);
    }

    private static void renderEndMessage(GuiGraphics graphics, Font font, int screenW, long elapsed) {
        float alpha;
        if (elapsed < 300) {
            alpha = elapsed / 300f;
        } else if (elapsed > END_MESSAGE_DURATION_MS - 500) {
            alpha = (END_MESSAGE_DURATION_MS - elapsed) / 500f;
        } else {
            alpha = 1f;
        }

        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        int color = (a << 24) | (endMessageColor & 0xFFFFFF);

        // Buyuk yazi, ekranin ortasinda
        graphics.pose().pushPose();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        float cx = screenW / 2f;
        float cy = screenH / 2f - 20;
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(3f, 3f, 1f);
        int msgWidth = font.width(endMessage);
        graphics.drawString(font, endMessage, -(msgWidth / 2), -(font.lineHeight / 2), color, true);
        graphics.pose().popPose();
    }
}
