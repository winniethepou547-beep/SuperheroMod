package com.FIRNI.superheromod.client;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.gui.StatsScreen;
import com.FIRNI.superheromod.client.gui.ClanScreen;
import com.FIRNI.superheromod.client.gui.DmScreen;
import com.FIRNI.superheromod.client.gui.MatchHistoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public class InventoryOverlay {

    private static final int BTN_WIDTH = 58;
    private static final int BTN_HEIGHT = 16;
    private static final int BTN_GAP = 2;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inv)) return;

        Minecraft mc = Minecraft.getInstance();
        int guiLeft = (inv.width - 176) / 2;
        int guiTop = (inv.height - 166) / 2;

        int btnX = guiLeft + 176 + 4;
        int btnY = guiTop + 2;

        event.addListener(Button.builder(Component.literal("§e⚔ Stats"), b ->
                mc.setScreen(new StatsScreen())
        ).bounds(btnX, btnY, BTN_WIDTH, BTN_HEIGHT).build());

        event.addListener(Button.builder(Component.literal("§6☰ Klan"), b ->
                mc.setScreen(new ClanScreen())
        ).bounds(btnX, btnY + BTN_HEIGHT + BTN_GAP, BTN_WIDTH, BTN_HEIGHT).build());

        event.addListener(Button.builder(Component.literal("§d✉ DM"), b ->
                mc.setScreen(new DmScreen())
        ).bounds(btnX, btnY + (BTN_HEIGHT + BTN_GAP) * 2, BTN_WIDTH, BTN_HEIGHT).build());

        event.addListener(Button.builder(Component.literal("§c⚑ Gecmis"), b ->
                mc.setScreen(new MatchHistoryScreen())
        ).bounds(btnX, btnY + (BTN_HEIGHT + BTN_GAP) * 3, BTN_WIDTH, BTN_HEIGHT).build());
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen inv)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;

        int guiLeft = (inv.width - 176) / 2;
        int guiTop = (inv.height - 166) / 2;

        int barY = guiTop - 22;
        int barX = guiLeft;
        int totalW = 176;

        // Background panel
        graphics.fill(barX - 2, barY - 2, barX + totalW + 2, barY + 18, 0xCC1a1a2e);
        // Border
        graphics.fill(barX - 2, barY - 2, barX + totalW + 2, barY - 1, 0xFF6c5ce7);
        graphics.fill(barX - 2, barY + 18, barX + totalW + 2, barY + 19, 0xFF6c5ce7);

        // Gold icon + value
        graphics.drawString(font, "§6⛃", barX + 2, barY + 2, 0xFFFFFF, true);
        graphics.drawString(font, "§f" + ClientPlayerData.gold, barX + 12, barY + 2, 0xFFFFFF, true);

        // XP bar
        int xpBarX = barX + 56;
        int xpBarW = 50;
        int xpBarH = 6;
        int xpBarY = barY + 3;
        graphics.fill(xpBarX, xpBarY, xpBarX + xpBarW, xpBarY + xpBarH, 0xFF333333);
        float xpFraction = ClientPlayerData.xpNeeded > 0
                ? Math.min(1f, (float) ClientPlayerData.pveXp / ClientPlayerData.xpNeeded) : 0f;
        int filledW = (int) (xpBarW * xpFraction);
        if (filledW > 0) {
            graphics.fill(xpBarX, xpBarY, xpBarX + filledW, xpBarY + xpBarH, 0xFF00d2d3);
        }
        // XP text on bar
        String xpText = "Lv" + ClientPlayerData.level;
        graphics.drawString(font, xpText, xpBarX + 2, xpBarY + xpBarH + 2, 0xFF00d2d3, true);

        // Rank display
        String rankDisplay = ClientPlayerData.rankColor + ClientPlayerData.rankName;
        int rankW = font.width(rankDisplay);
        graphics.drawString(font, rankDisplay, barX + totalW - rankW - 2, barY + 2, 0xFFFFFF, true);

        // Right panel background for buttons
        int btnX = guiLeft + 176 + 4;
        int btnPanelY = guiTop - 2;
        int btnPanelH = (BTN_HEIGHT + BTN_GAP) * 4 + 6;
        graphics.fill(btnX - 2, btnPanelY, btnX + BTN_WIDTH + 2, btnPanelY + btnPanelH, 0xCC1a1a2e);
        graphics.fill(btnX - 2, btnPanelY, btnX - 1, btnPanelY + btnPanelH, 0xFF6c5ce7);
    }
}
