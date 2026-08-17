package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.core.matchmaking.MatchMode;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ModeSelectedPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

/** PVP portaline girince acilan ekran: once mod sec, sonra ayni ekranda kuyruk/kronometre gosterir. */
public class ModeSelectScreen extends Screen {

    private enum Phase { SELECTING, QUEUEING }

    private Phase phase = Phase.SELECTING;
    private MatchMode selectedMode;
    private long queueStartMillis;

    public ModeSelectScreen() {
        super(Component.literal("PVP - Mod Sec"));
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int y = this.height / 2;

        this.addRenderableWidget(Button.builder(Component.literal("1v1"), b -> selectMode(MatchMode.ONE_V_ONE))
                .bounds(centerX - 105, y, 100, 24).build());
        this.addRenderableWidget(Button.builder(Component.literal("2v2"), b -> selectMode(MatchMode.TWO_V_TWO))
                .bounds(centerX + 5, y, 100, 24).build());
    }

    private void selectMode(MatchMode mode) {
        this.selectedMode = mode;
        this.phase = Phase.QUEUEING;
        this.queueStartMillis = Util.getMillis();
        this.clearWidgets();
        ModNetworking.CHANNEL.sendToServer(new ModeSelectedPacket(mode));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101018);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (phase == Phase.SELECTING) {
            graphics.drawCenteredString(this.font, "PVP - Mod Sec", this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        } else {
            long elapsedSeconds = (Util.getMillis() - queueStartMillis) / 1000;
            String timer = String.format("%d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
            graphics.drawCenteredString(this.font, selectedMode.getDisplayName() + " kuyruguna girildi",
                    this.width / 2, this.height / 2 - 40, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "Kuyrukta bekleniyor... " + timer,
                    this.width / 2, this.height / 2 - 20, 0xFFD700);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
