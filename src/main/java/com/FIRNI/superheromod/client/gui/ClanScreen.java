package com.FIRNI.superheromod.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class ClanScreen extends Screen {

    private EditBox inputBox;

    public ClanScreen() {
        super(Component.literal("Klan"));
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = this.height / 2;

        // Input box for commands
        inputBox = new EditBox(this.font, cx - 80, y + 30, 160, 18, Component.literal(""));
        inputBox.setMaxLength(64);
        inputBox.setHint(Component.literal("§7Klan ismi veya komut..."));
        this.addRenderableWidget(inputBox);

        // Quick action buttons
        this.addRenderableWidget(Button.builder(Component.literal("§a Klan Kur"),
                b -> {
                    String name = inputBox.getValue().trim();
                    if (!name.isEmpty()) {
                        sendCommand("clan create " + name);
                        this.onClose();
                    }
                }
        ).bounds(cx - 80, y + 54, 78, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("§e Durum"),
                b -> {
                    sendCommand("clan status");
                    this.onClose();
                }
        ).bounds(cx + 2, y + 54, 78, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("§d Davet Et"),
                b -> {
                    String name = inputBox.getValue().trim();
                    if (!name.isEmpty()) {
                        sendCommand("clan invite " + name);
                        this.onClose();
                    }
                }
        ).bounds(cx - 80, y + 74, 78, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("§c Ayril"),
                b -> {
                    sendCommand("clan leave");
                    this.onClose();
                }
        ).bounds(cx + 2, y + 74, 78, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Geri"),
                b -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player))
        ).bounds(cx - 40, y + 98, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, this.width, this.height, 0xC0101018);
        super.render(g, mouseX, mouseY, pt);

        int cx = this.width / 2;
        int y = this.height / 2 - 60;

        g.drawCenteredString(this.font, "§6§lKLAN YONETIMI", cx, y, 0xFFFFFF);
        y += 4;
        g.fill(cx - 60, y + 10, cx + 60, y + 11, 0xFF6c5ce7);
        y += 18;

        g.drawCenteredString(this.font, "§7Klan komutlari icin asagidaki", cx, y, 0xAAAAAA);
        y += 12;
        g.drawCenteredString(this.font, "§7butonlari kullanabilirsin.", cx, y, 0xAAAAAA);
        y += 16;

        g.drawCenteredString(this.font, "§e/clan status §7- Klan durumu", cx, y, 0xFFFFFF);
        y += 12;
        g.drawCenteredString(this.font, "§e/cc <mesaj> §7- Klan sohbeti", cx, y, 0xFFFFFF);
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
