package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.client.ClientPlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class StatsScreen extends Screen {

    public StatsScreen() {
        super(Component.literal("Istatistikler"));
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(Component.literal("Geri"),
                b -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player))
        ).bounds(this.width / 2 - 50, this.height / 2 + 90, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        g.fill(0, 0, this.width, this.height, 0xC0101018);
        super.render(g, mouseX, mouseY, pt);

        int cx = this.width / 2;
        int y = this.height / 2 - 80;

        // Title
        g.drawCenteredString(this.font, "§6§lSUPERHERO NETWORK §7- Profil", cx, y, 0xFFFFFF);
        y += 4;
        g.fill(cx - 90, y + 10, cx + 90, y + 11, 0xFF6c5ce7);
        y += 16;

        // Economy
        drawLine(g, cx, y, "§e Altin:", "§f " + ClientPlayerData.gold); y += 12;
        drawLine(g, cx, y, "§e Arena Token:", "§f " + ClientPlayerData.arenaTokens); y += 16;

        // PvP Header
        g.drawCenteredString(this.font, "§c§l⚔ PVP", cx, y, 0xFFFFFF); y += 14;

        drawLine(g, cx, y, "§7 Rating:", "§f " + ClientPlayerData.pvpRating
                + " §7(Peak: §f" + ClientPlayerData.peakRating + "§7)"); y += 12;
        drawLine(g, cx, y, "§7 Rank:", " " + ClientPlayerData.rankColor + ClientPlayerData.rankName); y += 12;
        drawLine(g, cx, y, "§7 Galibiyet:", "§a " + ClientPlayerData.wins
                + " §7| Maglubiyet: §c" + ClientPlayerData.losses); y += 12;
        drawLine(g, cx, y, "§7 K/D:", "§f " + ClientPlayerData.getKd()
                + " §7(§a" + ClientPlayerData.kills + "§7/§c" + ClientPlayerData.deaths + "§7)"); y += 12;
        drawLine(g, cx, y, "§7 PvP XP:", "§b " + ClientPlayerData.pvpXp); y += 16;

        // PvE Header
        g.drawCenteredString(this.font, "§2§l☘ PVE", cx, y, 0xFFFFFF); y += 14;

        drawLine(g, cx, y, "§7 Seviye:", "§a " + ClientPlayerData.level); y += 12;

        // XP bar
        int barX = cx - 60;
        int barW = 120;
        int barH = 8;
        g.fill(barX, y, barX + barW, y + barH, 0xFF333333);
        float frac = ClientPlayerData.xpNeeded > 0
                ? Math.min(1f, (float) ClientPlayerData.pveXp / ClientPlayerData.xpNeeded) : 0f;
        int filled = (int) (barW * frac);
        if (filled > 0) {
            g.fill(barX, y, barX + filled, y + barH, 0xFF00d2d3);
        }
        g.drawCenteredString(this.font, "§b" + ClientPlayerData.pveXp + "§7/" + ClientPlayerData.xpNeeded,
                cx, y + barH + 2, 0xFFFFFF);
    }

    private void drawLine(GuiGraphics g, int cx, int y, String label, String value) {
        g.drawString(this.font, label + value, cx - 90, y, 0xFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
