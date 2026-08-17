package com.FIRNI.superheromod.core.command;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.social.ClanManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class ClanCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("clan")
                .executes(ClanCommands::statusCommand)
                .then(Commands.literal("create")
                        .then(Commands.argument("isim", StringArgumentType.word())
                                .executes(ClanCommands::createCommand)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .executes(ClanCommands::inviteCommand)))
                .then(Commands.literal("accept").executes(ClanCommands::acceptCommand))
                .then(Commands.literal("deny").executes(ClanCommands::denyCommand))
                .then(Commands.literal("leave").executes(ClanCommands::leaveCommand))
                .then(Commands.literal("kick")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .executes(ClanCommands::kickCommand)))
                .then(Commands.literal("promote")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .executes(ClanCommands::promoteCommand)))
                .then(Commands.literal("disband").executes(ClanCommands::disbandCommand))
                .then(Commands.literal("status").executes(ClanCommands::statusCommand))
                .then(Commands.literal("chat")
                        .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                                .executes(ClanCommands::chatCommand)))
        );

        d.register(Commands.literal("cc")
                .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                        .executes(ClanCommands::chatCommand)));
    }

    private static int createCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "isim");

        if (name.length() < 2 || name.length() > 16) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cKlan ismi 2-16 karakter olmali!"));
            return 0;
        }

        var clan = ClanManager.create(player, name);
        if (clan == null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§cKlan olusturulamadi! Zaten bir klandasin veya bu isim alinmis."));
            return 0;
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§a§l" + name + " §aklani olusturuldu!"));
        return 1;
    }

    private static int inviteCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer inviter = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");
        ClanManager.invite(inviter, target);
        return 1;
    }

    private static int acceptCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClanManager.accept(player);
        return 1;
    }

    private static int denyCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClanManager.deny(player);
        return 1;
    }

    private static int leaveCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClanManager.leave(player);
        return 1;
    }

    private static int kickCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");
        ClanManager.kick(leader, target);
        return 1;
    }

    private static int promoteCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");
        ClanManager.promote(leader, target);
        return 1;
    }

    private static int disbandCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer leader = ctx.getSource().getPlayerOrException();
        ClanManager.disband(leader);
        return 1;
    }

    private static int statusCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ClanManager.showStatus(player);
        return 1;
    }

    private static int chatCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String message = StringArgumentType.getString(ctx, "mesaj");
        ClanManager.clanChat(player, message);
        return 1;
    }
}
