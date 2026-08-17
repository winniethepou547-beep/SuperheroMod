package com.FIRNI.superheromod.core.command;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.social.MessageManager;
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

import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class SocialCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        // /msg <player> <message>
        d.register(Commands.literal("msg")
                .then(Commands.argument("oyuncu", EntityArgument.player())
                        .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                                .executes(SocialCommands::msgCommand))));
        d.register(Commands.literal("tell")
                .then(Commands.argument("oyuncu", EntityArgument.player())
                        .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                                .executes(SocialCommands::msgCommand))));
        d.register(Commands.literal("whisper")
                .then(Commands.argument("oyuncu", EntityArgument.player())
                        .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                                .executes(SocialCommands::msgCommand))));

        // /r <message>
        d.register(Commands.literal("r")
                .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                        .executes(SocialCommands::replyCommand)));
        d.register(Commands.literal("reply")
                .then(Commands.argument("mesaj", StringArgumentType.greedyString())
                        .executes(SocialCommands::replyCommand)));

        // /ignore <player>
        d.register(Commands.literal("ignore")
                .then(Commands.literal("list").executes(SocialCommands::ignoreListCommand))
                .then(Commands.argument("oyuncu", EntityArgument.player())
                        .executes(SocialCommands::ignoreCommand)));

        // /unignore <player>
        d.register(Commands.literal("unignore")
                .then(Commands.argument("oyuncu", EntityArgument.player())
                        .executes(SocialCommands::unignoreCommand)));
    }

    private static int msgCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");
        String message = StringArgumentType.getString(ctx, "mesaj");

        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("§cKendine mesaj gonderemezsin!"));
            return 0;
        }

        if (MessageManager.isIgnored(target.getUUID(), sender.getUUID())) {
            sender.sendSystemMessage(Component.literal("§cBu oyuncu seni engelledi."));
            return 0;
        }

        MessageManager.setLastMessaged(sender.getUUID(), target.getUUID());

        sender.sendSystemMessage(Component.literal(
                "§7[§d" + target.getName().getString() + "§7'e] §f" + message));
        target.sendSystemMessage(Component.literal(
                "§7[§d" + sender.getName().getString() + "§7'den] §f" + message));
        return 1;
    }

    private static int replyCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        String message = StringArgumentType.getString(ctx, "mesaj");

        UUID lastId = MessageManager.getLastMessaged(sender.getUUID());
        if (lastId == null) {
            sender.sendSystemMessage(Component.literal("§cYanit verecek kimse yok!"));
            return 0;
        }

        ServerPlayer target = sender.getServer().getPlayerList().getPlayer(lastId);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("§cO oyuncu artik online degil."));
            return 0;
        }

        if (MessageManager.isIgnored(target.getUUID(), sender.getUUID())) {
            sender.sendSystemMessage(Component.literal("§cBu oyuncu seni engelledi."));
            return 0;
        }

        MessageManager.setLastMessaged(sender.getUUID(), target.getUUID());

        sender.sendSystemMessage(Component.literal(
                "§7[§d" + target.getName().getString() + "§7'e] §f" + message));
        target.sendSystemMessage(Component.literal(
                "§7[§d" + sender.getName().getString() + "§7'den] §f" + message));
        return 1;
    }

    private static int ignoreCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");

        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("§cKendini engelleyemezsin!"));
            return 0;
        }

        MessageManager.ignore(sender.getUUID(), target.getUUID());
        sender.sendSystemMessage(Component.literal(
                "§e" + target.getName().getString() + " §fengellendi."));
        return 1;
    }

    private static int unignoreCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");

        MessageManager.unignore(sender.getUUID(), target.getUUID());
        sender.sendSystemMessage(Component.literal(
                "§e" + target.getName().getString() + " §fengeli kaldirildi."));
        return 1;
    }

    private static int ignoreListCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        Set<UUID> ignored = MessageManager.getIgnoreList(sender.getUUID());

        if (ignored.isEmpty()) {
            sender.sendSystemMessage(Component.literal("§7Engelli oyuncu yok."));
            return 0;
        }

        sender.sendSystemMessage(Component.literal("§eEngelli oyuncular:"));
        for (UUID uuid : ignored) {
            ServerPlayer target = sender.getServer().getPlayerList().getPlayer(uuid);
            String name = target != null ? target.getName().getString() : uuid.toString().substring(0, 8);
            sender.sendSystemMessage(Component.literal("§7- §f" + name));
        }
        return 1;
    }
}
