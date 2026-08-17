package com.FIRNI.superheromod.core.command;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.combat.CombatTagManager;
import com.FIRNI.superheromod.core.matchmaking.MatchManager;
import com.FIRNI.superheromod.core.matchmaking.MatchMode;
import com.FIRNI.superheromod.core.matchmaking.QueueManager;
import com.FIRNI.superheromod.core.progression.*;
import com.FIRNI.superheromod.core.world.ArenaLocations;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.OpenModeSelectPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class PlayerCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        // /hub - teleport to hub
        d.register(Commands.literal("hub").executes(PlayerCommands::hubCommand));
        d.register(Commands.literal("lobby").executes(PlayerCommands::hubCommand));
        d.register(Commands.literal("spawn").executes(PlayerCommands::hubCommand));

        // /balance
        d.register(Commands.literal("balance").executes(PlayerCommands::balanceCommand));
        d.register(Commands.literal("bal").executes(PlayerCommands::balanceCommand));

        // /pay <player> <amount>
        d.register(Commands.literal("pay")
                .then(Commands.argument("oyuncu", EntityArgument.player())
                        .then(Commands.argument("miktar", IntegerArgumentType.integer(1))
                                .executes(PlayerCommands::payCommand))));

        // /stats
        d.register(Commands.literal("stats").executes(PlayerCommands::statsCommand));

        // /pvp - open mode select screen
        d.register(Commands.literal("pvp")
                .executes(PlayerCommands::pvpMenuCommand)
                .then(Commands.literal("queue")
                        .then(Commands.literal("1v1").executes(ctx -> queueCommand(ctx, MatchMode.ONE_V_ONE)))
                        .then(Commands.literal("2v2").executes(ctx -> queueCommand(ctx, MatchMode.TWO_V_TWO))))
                .then(Commands.literal("leave").executes(PlayerCommands::leaveQueueCommand))
                .then(Commands.literal("stats").executes(PlayerCommands::pvpStatsCommand)));

        // /level
        d.register(Commands.literal("level").executes(PlayerCommands::levelCommand));

        // /ff - forfeit/surrender
        d.register(Commands.literal("ff").executes(PlayerCommands::ffCommand));
        d.register(Commands.literal("surrender").executes(PlayerCommands::ffCommand));

        // /ping
        d.register(Commands.literal("ping").executes(PlayerCommands::pingCommand));

        // /online
        d.register(Commands.literal("online").executes(PlayerCommands::onlineCommand));
    }

    private static int hubCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if (MatchManager.getMatchOf(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("§cMac sirasinda hub'a donulemez!"));
            return 0;
        }
        if (CombatTagManager.isInCombat(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cSavas sirasinda hub'a donulemezsin! 15 saniye bekle."));
            return 0;
        }

        QueueManager.dequeue(player);

        var overworld = player.getServer().overworld();
        player.teleportTo(overworld,
                ArenaLocations.HUB_SPAWN.getX() + 0.5,
                ArenaLocations.HUB_SPAWN.getY(),
                ArenaLocations.HUB_SPAWN.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("§aHub'a isinlandin!"));
        return 1;
    }

    private static int balanceCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int gold = GoldSystem.getGold(player);
        player.sendSystemMessage(Component.literal("§6Altin: §f" + gold));
        return 1;
    }

    private static int payCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");
        int amount = IntegerArgumentType.getInteger(ctx, "miktar");

        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("§cKendine altin gonderemezsin!"));
            return 0;
        }

        int senderGold = GoldSystem.getGold(sender);
        if (senderGold < amount) {
            sender.sendSystemMessage(Component.literal("§cYeterli altinin yok! Mevcut: §6" + senderGold));
            return 0;
        }

        GoldSystem.addGold(sender, -amount);
        GoldSystem.addGold(target, amount);

        sender.sendSystemMessage(Component.literal(
                "§a" + target.getName().getString() + " oyuncusuna §6" + amount + " altin §agonderdin."));
        target.sendSystemMessage(Component.literal(
                "§a" + sender.getName().getString() + " oyuncusundan §6" + amount + " altin §aaldin."));
        return 1;
    }

    private static int statsCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data -> {
            RankTier tier = RankTier.fromRating(data.getPvpRating());
            int xpNeeded = LevelSystem.xpForLevel(data.getLevel());

            player.sendSystemMessage(Component.literal("§6§m                              "));
            player.sendSystemMessage(Component.literal("§6§lSUPERHERO NETWORK §7- Profil"));
            player.sendSystemMessage(Component.literal("§6§m                              "));
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§e Altin: §f" + data.getGold()));
            player.sendSystemMessage(Component.literal("§e Arena Token: §f" + data.getArenaTokens()));
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§c§l PVP"));
            player.sendSystemMessage(Component.literal("§7 Rating: §f" + data.getPvpRating()
                    + " §7(En Yuksek: §f" + data.getPeakRating() + "§7)"));
            player.sendSystemMessage(Component.literal("§7 Rank: " + tier.getColoredName()));
            player.sendSystemMessage(Component.literal("§7 Galibiyet: §a" + data.getWins()
                    + " §7| Maglubiyet: §c" + data.getLosses()));
            player.sendSystemMessage(Component.literal("§7 Kill: §a" + data.getKills()
                    + " §7| Olum: §c" + data.getDeaths()));
            String kd = data.getDeaths() > 0
                    ? String.format("%.2f", (double) data.getKills() / data.getDeaths())
                    : "-";
            player.sendSystemMessage(Component.literal("§7 K/D: §f" + kd));
            player.sendSystemMessage(Component.literal("§7 PvP XP: §b" + data.getPvpXp()));
            player.sendSystemMessage(Component.literal(""));
            player.sendSystemMessage(Component.literal("§2§l PVE"));
            player.sendSystemMessage(Component.literal("§7 Seviye: §a" + data.getLevel()));
            player.sendSystemMessage(Component.literal("§7 XP: §b" + data.getPveXp() + "§7/" + xpNeeded));
            player.sendSystemMessage(Component.literal("§6§m                              "));
        });
        return 1;
    }

    private static int pvpMenuCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenModeSelectPacket());
        return 1;
    }

    private static int queueCommand(CommandContext<CommandSourceStack> ctx, MatchMode mode) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (MatchManager.getMatchOf(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("§cZaten bir macta oldugun icin kuyruga giremezsin!"));
            return 0;
        }
        QueueManager.enqueue(player, mode);
        return 1;
    }

    private static int leaveQueueCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        QueueManager.dequeue(player);
        player.sendSystemMessage(Component.literal("§eKuyruktan cikarildin."));
        return 1;
    }

    private static int pvpStatsCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return statsCommand(ctx);
    }

    private static int levelCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int level = LevelSystem.getLevel(player);
        int xp = LevelSystem.getPveXp(player);
        int needed = LevelSystem.xpForLevel(level);
        player.sendSystemMessage(Component.literal(
                "§aSeviye: §f" + level + " §7| §bXP: §f" + xp + "§7/" + needed));
        return 1;
    }

    private static int pingCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int latency = player.latency;
        player.sendSystemMessage(Component.literal("§aPing: §f" + latency + "ms"));
        return 1;
    }

    private static int ffCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MatchManager.handleForfeit(player, player.getServer());
        return 1;
    }

    private static int onlineCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int count = player.getServer().getPlayerCount();
        player.sendSystemMessage(Component.literal("§aOnline: §f" + count + " oyuncu"));
        return 1;
    }
}
