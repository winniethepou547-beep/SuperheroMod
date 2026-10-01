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

        event.addListener(Button.builder(Component.literal("Stats"), b ->
                mc.setScreen(new StatsScreen())
        ).bounds(btnX, btnY, BTN_WIDTH, BTN_HEIGHT).build());

        event.addListener(Button.builder(Component.literal("Klan"), b ->
                mc.setScreen(new ClanScreen())
        ).bounds(btnX, btnY + BTN_HEIGHT + BTN_GAP, BTN_WIDTH, BTN_HEIGHT).build());

        event.addListener(Button.builder(Component.literal("DM"), b ->
                mc.setScreen(new DmScreen())
        ).bounds(btnX, btnY + (BTN_HEIGHT + BTN_GAP) * 2, BTN_WIDTH, BTN_HEIGHT).build());

        event.addListener(Button.builder(Component.literal("Gecmis"), b ->
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

        // Quiet strip above the inventory: gold, level progress, rank. No boxes.
        com.FIRNI.superheromod.client.hud.HudStyle.shade(graphics, barX - 6, barY - 4, totalW + 12, 22, .9f);
        com.FIRNI.superheromod.client.hud.HudStyle.caption(graphics, font, "Gold", barX, barY, com.FIRNI.superheromod.client.hud.HudStyle.MUTED, -1);
        graphics.drawString(font, String.valueOf(ClientPlayerData.gold), barX, barY + 9, 0xFFFFD27A, false);
        int xpBarX = barX + 52, xpBarW = 64;
        float xpFraction = ClientPlayerData.xpNeeded > 0
                ? Math.min(1f, (float) ClientPlayerData.pveXp / ClientPlayerData.xpNeeded) : 0f;
        com.FIRNI.superheromod.client.hud.HudStyle.caption(graphics, font, "Lv " + ClientPlayerData.level, xpBarX, barY, com.FIRNI.superheromod.client.hud.HudStyle.MUTED, -1);
        com.FIRNI.superheromod.client.hud.HudStyle.bar(graphics, xpBarX, barY + 11, xpBarW, xpFraction, 0xFF5FD3D4);
        String rankDisplay = ClientPlayerData.rankColor + ClientPlayerData.rankName;
        graphics.drawString(font, rankDisplay, barX + totalW - font.width(rankDisplay), barY + 5, 0xFFFFFFFF, false);
        // Hairline accent beside the side buttons.
        int btnX = guiLeft + 176 + 4;
        int btnPanelH = (BTN_HEIGHT + BTN_GAP) * 4 - BTN_GAP;
        graphics.fill(btnX - 3, guiTop + 2, btnX - 2, guiTop + 2 + btnPanelH, com.FIRNI.superheromod.client.hud.HudStyle.ACCENT);
    }
}
