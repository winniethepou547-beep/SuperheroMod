package com.FIRNI.superheromod.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class DmScreen extends Screen {

    private EditBox playerBox;
    private EditBox messageBox;

    public DmScreen() {
        super(Component.literal("Ozel Mesaj"));
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = this.height / 2 - 20;

        playerBox = new EditBox(this.font, cx - 80, y, 160, 18, Component.literal(""));
        playerBox.setMaxLength(32);
        playerBox.setHint(Component.literal("§7Oyuncu ismi..."));
        this.addRenderableWidget(playerBox);

        messageBox = new EditBox(this.font, cx - 80, y + 24, 160, 18, Component.literal(""));
        messageBox.setMaxLength(256);
        messageBox.setHint(Component.literal("§7Mesajin..."));
        this.addRenderableWidget(messageBox);

        this.addRenderableWidget(Button.builder(Component.literal("§a Gonder"),
                b -> {
                    String player = playerBox.getValue().trim();
                    String msg = messageBox.getValue().trim();
                    if (!player.isEmpty() && !msg.isEmpty()) {
                        sendCommand("msg " + player + " " + msg);
                        this.onClose();
                    }
                }
        ).bounds(cx - 80, y + 48, 78, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("§e Yanit"),
                b -> {
                    String msg = messageBox.getValue().trim();
                    if (!msg.isEmpty()) {
                        sendCommand("r " + msg);
                        this.onClose();
                    }
                }
        ).bounds(cx + 2, y + 48, 78, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Geri"),
                b -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player))
        ).bounds(cx - 40, y + 72, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, this.width, this.height, 0xC0101018);
        super.render(g, mouseX, mouseY, pt);

        int cx = this.width / 2;
        int y = this.height / 2 - 60;

        g.drawCenteredString(this.font, "§d§l✉ OZEL MESAJ", cx, y, 0xFFFFFF);
        y += 4;
        g.fill(cx - 50, y + 10, cx + 50, y + 11, 0xFF6c5ce7);
        y += 16;

        g.drawCenteredString(this.font, "§7Bir oyuncuya ozel mesaj gonder", cx, y, 0xAAAAAA);
    }

    private void sendCommand(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.connection.sendCommand(cmd);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
