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
            endMessageColor = 0xFFC27A;
        } else {
            endMessage = winnerName + " kazandi";
            endMessageColor = 0xD8CFC4;
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
        if (com.FIRNI.superheromod.client.render.cinematic.CinematicClient.shouldDrive()) return;
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

    /** Names as quiet captions either side, the score large in the middle, on a soft wash. */
    private static void renderScoreboard(GuiGraphics graphics, Font font, int screenW) {
        int cx = screenW / 2, y = 8;
        String score = leftKills + "  " + rightKills;
        int scoreW = (int) (font.width(score) * 1.8f);
        int nameW = Math.max(com.FIRNI.superheromod.client.hud.HudStyle.captionWidth(font, leftName),
                com.FIRNI.superheromod.client.hud.HudStyle.captionWidth(font, rightName));
        int half = scoreW / 2 + 12 + nameW;
        com.FIRNI.superheromod.client.hud.HudStyle.shade(graphics, cx - half - 14, y - 4, (half + 14) * 2, 24, 1f);
        com.FIRNI.superheromod.client.hud.HudStyle.value(graphics, font, score, cx, y, 1.8f, com.FIRNI.superheromod.client.hud.HudStyle.TEXT, 0);
        graphics.fill(cx, y + 2, cx + 1, y + 13, com.FIRNI.superheromod.client.hud.HudStyle.alpha(com.FIRNI.superheromod.client.hud.HudStyle.TEXT, .4f));
        com.FIRNI.superheromod.client.hud.HudStyle.caption(graphics, font, leftName, cx - scoreW / 2 - 12, y + 4, com.FIRNI.superheromod.client.hud.HudStyle.MUTED, 1);
        com.FIRNI.superheromod.client.hud.HudStyle.caption(graphics, font, rightName, cx + scoreW / 2 + 12, y + 4, com.FIRNI.superheromod.client.hud.HudStyle.MUTED, -1);
        graphics.fill(cx - scoreW / 2, y + 18, cx + scoreW / 2, y + 19, com.FIRNI.superheromod.client.hud.HudStyle.ACCENT);
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
        graphics.drawString(font, endMessage, -(msgWidth / 2), -(font.lineHeight / 2), color, false);
        graphics.pose().popPose();
    }
}
