package com.FIRNI.superheromod.core.command;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.social.Party;
import com.FIRNI.superheromod.core.social.PartyManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class PartyCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("party")
                .then(Commands.literal("create").executes(PartyCommands::createCommand))
                .then(Commands.literal("invite")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .executes(PartyCommands::inviteCommand)))
                .then(Commands.literal("accept").executes(PartyCommands::acceptCommand))
                .then(Commands.literal("deny").executes(PartyCommands::denyCommand))
                .then(Commands.literal("leave").executes(PartyCommands::leaveCommand))
                .then(Commands.literal("kick")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .executes(PartyCommands::kickCommand)))
                .then(Commands.literal("promote")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .executes(PartyCommands::promoteCommand)))
                .then(Commands.literal("list").executes(PartyCommands::listCommand))
                .then(Commands.literal("disband").executes(PartyCommands::disbandCommand))
                .then(Commands.literal("chat")
                        .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                                .executes(PartyCommands::chatCommand))));

        // /p <message> shortcut
        d.register(Commands.literal("p")
                .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                        .executes(PartyCommands::chatCommand)));
    }

    private static int createCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Party party = PartyManager.createParty(player);
        if (party == null) {
            player.sendSystemMessage(Component.literal("§cZaten bir partidesin!"));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§aParti olusturuldu! §7/party invite <oyuncu> ile davet gonder."));
        return 1;
    }

    private static int inviteCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");
        PartyManager.invite(player, target);
        return 1;
    }

    private static int acceptCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PartyManager.accept(player);
        return 1;
    }

    private static int denyCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PartyManager.deny(player);
        return 1;
    }

    private static int leaveCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PartyManager.leave(player);
        return 1;
    }

    private static int kickCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");
        PartyManager.kick(player, target);
        return 1;
    }

    private static int promoteCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");
        PartyManager.promote(player, target);
        return 1;
    }

    private static int listCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Party party = PartyManager.getParty(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(Component.literal("§cBir partide degilsin!"));
            return 0;
        }

        player.sendSystemMessage(Component.literal("§6§lParti Uyeleri §7(" + party.getSize() + "/4)"));
        for (UUID memberId : party.getMembers()) {
            ServerPlayer member = player.getServer().getPlayerList().getPlayer(memberId);
            String name = member != null ? member.getName().getString() : "???";
            String prefix = party.isLeader(memberId) ? "§e★ " : "§7• ";
            player.sendSystemMessage(Component.literal(prefix + "§f" + name));
        }
        return 1;
    }

    private static int disbandCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PartyManager.disband(player);
        return 1;
    }

    private static int chatCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String message = StringArgumentType.getString(ctx, "mesaj");
        PartyManager.partyChat(player, message);
        return 1;
    }
}
