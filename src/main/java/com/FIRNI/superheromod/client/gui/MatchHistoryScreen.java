package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.client.ClientMatchHistory;
import com.FIRNI.superheromod.network.packet.MatchHistorySyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MatchHistoryScreen extends Screen {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM HH:mm");

    public MatchHistoryScreen() {
        super(Component.literal("Mac Gecmisi"));
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("Geri"),
                b -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player))
        ).bounds(this.width / 2 - 40, this.height / 2 + 90, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, this.width, this.height, 0xC0101018);
        super.render(g, mouseX, mouseY, pt);

        int cx = this.width / 2;
        int y = this.height / 2 - 80;

        g.drawCenteredString(this.font, "§c§l⚑ MAC GECMISI", cx, y, 0xFFFFFF);
        y += 4;
        g.fill(cx - 70, y + 10, cx + 70, y + 11, 0xFF6c5ce7);
        y += 18;

        List<MatchHistorySyncPacket.Entry> entries = ClientMatchHistory.getEntries();

        if (entries.isEmpty()) {
            g.drawCenteredString(this.font, "§7Henuz mac gecmisi yok.", cx, y + 20, 0xAAAAAA);
            return;
        }

        // Header
        int colX = cx - 110;
        g.drawString(this.font, "§e Sonuc", colX, y, 0xFFFFFF, true);
        g.drawString(this.font, "§e Mod", colX + 50, y, 0xFFFFFF, true);
        g.drawString(this.font, "§e Rakip", colX + 85, y, 0xFFFFFF, true);
        g.drawString(this.font, "§e Rating", colX + 155, y, 0xFFFFFF, true);
        g.drawString(this.font, "§e Tarih", colX + 195, y, 0xFFFFFF, true);
        y += 12;
        g.fill(colX, y, colX + 230, y + 1, 0xFF444444);
        y += 4;

        for (MatchHistorySyncPacket.Entry e : entries) {
            String result = e.won() ? "§a GAL" : "§c MAG";
            String ratingStr = e.ratingChange() >= 0
                    ? "§a+" + e.ratingChange()
                    : "§c" + e.ratingChange();
            String date = DATE_FMT.format(new Date(e.timestamp()));

            g.drawString(this.font, result, colX, y, 0xFFFFFF, true);
            g.drawString(this.font, "§f " + e.mode(), colX + 50, y, 0xFFFFFF, true);
            g.drawString(this.font, "§f " + e.opponent(), colX + 85, y, 0xFFFFFF, true);
            g.drawString(this.font, ratingStr, colX + 155, y, 0xFFFFFF, true);
            g.drawString(this.font, "§7 " + date, colX + 195, y, 0xFFFFFF, true);
            y += 12;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
