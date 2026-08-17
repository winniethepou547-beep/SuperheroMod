package com.FIRNI.superheromod.core.command;

import com.FIRNI.superheromod.SuperheroMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class HelpCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        for (String alias : new String[]{"yardim", "komutlar"}) {
            d.register(Commands.literal(alias)
                    .executes(HelpCommand::showCategories)
                    .then(Commands.literal("genel").executes(ctx -> show(ctx, 1)))
                    .then(Commands.literal("pvp").executes(ctx -> show(ctx, 2)))
                    .then(Commands.literal("sosyal").executes(ctx -> show(ctx, 3)))
                    .then(Commands.literal("parti").executes(ctx -> show(ctx, 4)))
                    .then(Commands.literal("klan").executes(ctx -> show(ctx, 5)))
            );
        }
    }

    private static int show(CommandContext<CommandSourceStack> ctx, int cat) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        switch (cat) {
            case 1 -> showGeneral(player);
            case 2 -> showPvP(player);
            case 3 -> showSocial(player);
            case 4 -> showParty(player);
            case 5 -> showClan(player);
        }
        return 1;
    }

    private static int showCategories(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§6§l SUPERHERO NETWORK §7- Yardim"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§e /yardim genel §7- Genel Komutlar"));
        player.sendSystemMessage(Component.literal("§e /yardim pvp §7- PvP Komutlari"));
        player.sendSystemMessage(Component.literal("§e /yardim sosyal §7- Sosyal Komutlar"));
        player.sendSystemMessage(Component.literal("§e /yardim parti §7- Parti Komutlari"));
        player.sendSystemMessage(Component.literal("§e /yardim klan §7- Klan Komutlari"));
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§7Ornek: §e/yardim pvp"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
        return 1;
    }

    private static void showGeneral(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§6§l GENEL KOMUTLAR"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§e /hub §7- Hub'a isinlan"));
        player.sendSystemMessage(Component.literal("§e /balance §7- Altin bakiyeni gor"));
        player.sendSystemMessage(Component.literal("§e /pay <oyuncu> <miktar> §7- Altin gonder"));
        player.sendSystemMessage(Component.literal("§e /stats §7- Profil istatistikleri"));
        player.sendSystemMessage(Component.literal("§e /level §7- Seviye bilgisi"));
        player.sendSystemMessage(Component.literal("§e /ping §7- Gecikme suresi"));
        player.sendSystemMessage(Component.literal("§e /online §7- Online oyuncu sayisi"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
    }

    private static void showPvP(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§c§l PVP KOMUTLARI"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§e /pvp §7- PvP mod secim ekrani"));
        player.sendSystemMessage(Component.literal("§e /pvp queue 1v1 §7- 1v1 kuyruguna gir"));
        player.sendSystemMessage(Component.literal("§e /pvp queue 2v2 §7- 2v2 kuyruguna gir"));
        player.sendSystemMessage(Component.literal("§e /pvp leave §7- Kuyruktan cik"));
        player.sendSystemMessage(Component.literal("§e /pvp stats §7- PvP istatistikleri"));
        player.sendSystemMessage(Component.literal("§e /ff §7- Mac icinde pes et (surrender)"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
    }

    private static void showSocial(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§d§l SOSYAL KOMUTLAR"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§e /msg <oyuncu> <mesaj> §7- Ozel mesaj"));
        player.sendSystemMessage(Component.literal("§e /r <mesaj> §7- Son mesaja yanit"));
        player.sendSystemMessage(Component.literal("§e /ignore <oyuncu> §7- Oyuncu engelle"));
        player.sendSystemMessage(Component.literal("§e /unignore <oyuncu> §7- Engeli kaldir"));
        player.sendSystemMessage(Component.literal("§e /ignore list §7- Engel listesi"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
    }

    private static void showParty(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§a§l PARTI KOMUTLARI"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§e /party invite <oyuncu> §7- Davet gonder"));
        player.sendSystemMessage(Component.literal("§e /party accept §7- Daveti kabul et"));
        player.sendSystemMessage(Component.literal("§e /party deny §7- Daveti reddet"));
        player.sendSystemMessage(Component.literal("§e /party leave §7- Partiden ayril"));
        player.sendSystemMessage(Component.literal("§e /party kick <oyuncu> §7- Oyuncu at"));
        player.sendSystemMessage(Component.literal("§e /party promote <oyuncu> §7- Lider yap"));
        player.sendSystemMessage(Component.literal("§e /party disband §7- Partiyi dagit"));
        player.sendSystemMessage(Component.literal("§e /party chat <mesaj> §7- Parti sohbeti"));
        player.sendSystemMessage(Component.literal("§e /pc <mesaj> §7- Parti sohbeti (kisa)"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
    }

    private static void showClan(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§6§l KLAN KOMUTLARI"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
        player.sendSystemMessage(Component.literal("§e /clan create <isim> §7- Klan olustur"));
        player.sendSystemMessage(Component.literal("§e /clan invite <oyuncu> §7- Klana davet et"));
        player.sendSystemMessage(Component.literal("§e /clan accept §7- Daveti kabul et"));
        player.sendSystemMessage(Component.literal("§e /clan deny §7- Daveti reddet"));
        player.sendSystemMessage(Component.literal("§e /clan leave §7- Klandan ayril"));
        player.sendSystemMessage(Component.literal("§e /clan kick <oyuncu> §7- Oyuncu at"));
        player.sendSystemMessage(Component.literal("§e /clan promote <oyuncu> §7- Lider yap"));
        player.sendSystemMessage(Component.literal("§e /clan disband §7- Klani dagit"));
        player.sendSystemMessage(Component.literal("§e /clan status §7- Klan durumu"));
        player.sendSystemMessage(Component.literal("§e /clan chat <mesaj> §7- Klan sohbeti"));
        player.sendSystemMessage(Component.literal("§e /cc <mesaj> §7- Klan sohbeti (kisa)"));
        player.sendSystemMessage(Component.literal("§6§m                              "));
    }
}
