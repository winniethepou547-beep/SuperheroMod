package com.FIRNI.superheromod.client;

import com.FIRNI.superheromod.client.gui.MapVoteScreen;
import com.FIRNI.superheromod.client.gui.MatchFoundScreen;
import com.FIRNI.superheromod.client.gui.ModeSelectScreen;
import com.FIRNI.superheromod.client.gui.VsScreen;
import com.FIRNI.superheromod.core.matchmaking.MatchMode;
import net.minecraft.client.Minecraft;

public final class ClientPacketHandler {

    private static VsScreen currentVsScreen;

    private ClientPacketHandler() {
    }

    public static void openModeSelect() {
        Minecraft.getInstance().setScreen(new ModeSelectScreen());
    }

    public static void showMatchFound(MatchMode mode) {
        Minecraft.getInstance().setScreen(new MatchFoundScreen(mode));
    }

    public static void showMapVote(int winningIndex) {
        Minecraft.getInstance().setScreen(new MapVoteScreen(winningIndex));
    }

    public static void showVsScreen(String playerName, String opponentName, String modeName,
                                     String playerRank, int playerRating, int playerWins, int playerLosses,
                                     String opponentRank, int opponentRating, int opponentWins, int opponentLosses) {
        currentVsScreen = new VsScreen(playerName, opponentName, modeName,
                playerRank, playerRating, playerWins, playerLosses,
                opponentRank, opponentRating, opponentWins, opponentLosses);
        Minecraft.getInstance().setScreen(currentVsScreen);
    }

    public static void updateVsReady(boolean playerReady, boolean opponentReady) {
        if (currentVsScreen != null && Minecraft.getInstance().screen == currentVsScreen) {
            currentVsScreen.updateReadyState(playerReady, opponentReady);
        }
    }

    public static void startFightCountdown() {
        currentVsScreen = null;
        Minecraft.getInstance().setScreen(null);
        CountdownOverlay.start();
    }

    public static void updateScoreboard(String playerName, int playerKills, String opponentName, int opponentKills) {
        ScoreboardOverlay.update(playerName, playerKills, opponentName, opponentKills);
    }

    public static void showMatchEnd(String winnerName, boolean youWon) {
        ScoreboardOverlay.showEnd(winnerName, youWon);
    }
}
