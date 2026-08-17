package com.FIRNI.superheromod.core.command;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.progression.ProgressionEvents;
import com.FIRNI.superheromod.core.region.Region;
import com.FIRNI.superheromod.core.region.RegionManager;
import com.FIRNI.superheromod.core.region.RegionType;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.character.CharacterRegistry;
import com.FIRNI.superheromod.core.character.SuperCharacter;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.CameraStatePacket;
import net.minecraftforge.network.PacketDistributor;
import com.FIRNI.superheromod.core.matchmaking.MatchMode;
import com.FIRNI.superheromod.core.matchmaking.QueueManager;
import com.FIRNI.superheromod.core.world.HubBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /superhero komut agaci: bolge tanimlama (pos1/pos2/region) ve rank-gold-xp test komutlari (stats/give).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class SuperheroCommands {

    // Oyuncu basina gecici kose secimi (kalici degil, sadece /region create sirasinda kullanilir)
    private static final Map<UUID, BlockPos> POS1 = new HashMap<>();
    private static final Map<UUID, BlockPos> POS2 = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> regionNode = Commands.literal("region")
                .then(Commands.literal("create")
                        .then(Commands.argument("isim", StringArgumentType.word())
                                .then(Commands.argument("tip", StringArgumentType.word())
                                        .executes(SuperheroCommands::createRegion))))
                .then(Commands.literal("list").executes(SuperheroCommands::listRegions))
                .then(Commands.literal("remove")
                        .then(Commands.argument("isim", StringArgumentType.word())
                                .executes(SuperheroCommands::removeRegion)));

        LiteralArgumentBuilder<CommandSourceStack> giveNode = Commands.literal("give")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("gold")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .then(Commands.argument("miktar", IntegerArgumentType.integer())
                                        .executes(ctx -> giveStat(ctx, "gold")))))
                .then(Commands.literal("rating")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .then(Commands.argument("miktar", IntegerArgumentType.integer())
                                        .executes(ctx -> giveStat(ctx, "rating")))))
                .then(Commands.literal("pvpxp")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .then(Commands.argument("miktar", IntegerArgumentType.integer())
                                        .executes(ctx -> giveStat(ctx, "pvpxp")))))
                .then(Commands.literal("pvexp")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .then(Commands.argument("miktar", IntegerArgumentType.integer())
                                        .executes(ctx -> giveStat(ctx, "pvexp")))))
                .then(Commands.literal("tokens")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .then(Commands.argument("miktar", IntegerArgumentType.integer())
                                        .executes(ctx -> giveStat(ctx, "tokens")))))
                .then(Commands.literal("level")
                        .then(Commands.argument("oyuncu", EntityArgument.player())
                                .then(Commands.argument("miktar", IntegerArgumentType.integer())
                                        .executes(ctx -> giveStat(ctx, "level")))));

        dispatcher.register(Commands.literal("superhero")
                .then(Commands.literal("pos1").executes(SuperheroCommands::setPos1))
                .then(Commands.literal("pos2").executes(SuperheroCommands::setPos2))
                .then(regionNode)
                .then(Commands.literal("stats").executes(SuperheroCommands::stats))
                .then(giveNode)
                .then(Commands.literal("setup")
                        .requires(source -> source.hasPermission(2))
                        .executes(SuperheroCommands::setupWorld))
                .then(Commands.literal("testmatch")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("1v1").executes(ctx -> testMatch(ctx, MatchMode.ONE_V_ONE)))
                        .then(Commands.literal("2v2").executes(ctx -> testMatch(ctx, MatchMode.TWO_V_TWO))))
                .then(Commands.literal("hero")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("characterId", StringArgumentType.word())
                                .executes(SuperheroCommands::selectHero))));
    }

    private static int setupWorld(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        HubBuilder.setupWorld(level);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Hub, PVP ve PVE meydanlari olusturuldu. Buyuk PVP haritalari arka planda insa ediliyor, birazdan 'hazir' mesaji gelecek."), true);
        return 1;
    }

    private static int testMatch(CommandContext<CommandSourceStack> ctx, MatchMode mode) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        QueueManager.startTestMatch(player, mode);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Test maci baslatildi (" + mode.getDisplayName() + "). Ekranlar sirayla acilacak."), false);
        return 1;
    }

    private static int setPos1(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos pos = player.blockPosition();
        POS1.put(player.getUUID(), pos);
        ctx.getSource().sendSuccess(() -> Component.literal("1. kose kaydedildi: " + format(pos)), false);
        return 1;
    }

    private static int setPos2(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos pos = player.blockPosition();
        POS2.put(player.getUUID(), pos);
        ctx.getSource().sendSuccess(() -> Component.literal("2. kose kaydedildi: " + format(pos)), false);
        return 1;
    }

    private static int createRegion(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "isim");
        String typeArg = StringArgumentType.getString(ctx, "tip");

        RegionType type;
        if (typeArg.equalsIgnoreCase("pvp")) {
            type = RegionType.PVP_ENTRY;
        } else if (typeArg.equalsIgnoreCase("pve")) {
            type = RegionType.PVE_ENTRY;
        } else {
            ctx.getSource().sendFailure(Component.literal("Tip 'pvp' veya 'pve' olmali."));
            return 0;
        }

        BlockPos p1 = POS1.get(player.getUUID());
        BlockPos p2 = POS2.get(player.getUUID());
        if (p1 == null || p2 == null) {
            ctx.getSource().sendFailure(Component.literal("Once /superhero pos1 ve /superhero pos2 ile iki koseyi isaretle."));
            return 0;
        }

        if (RegionManager.get(name).isPresent()) {
            ctx.getSource().sendFailure(Component.literal("'" + name + "' isimli bolge zaten var."));
            return 0;
        }

        Region region = new Region(name, type, player.level().dimension().location(), p1, p2);
        RegionManager.register(region);

        ctx.getSource().sendSuccess(() -> Component.literal("Bolge olusturuldu: " + name + " (" + type + ")"), true);
        return 1;
    }

    private static int listRegions(CommandContext<CommandSourceStack> ctx) {
        Map<String, Region> all = RegionManager.getAll();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Hic bolge yok."), false);
            return 0;
        }
        for (Region region : all.values()) {
            ctx.getSource().sendSuccess(() -> Component.literal("- " + region.getName() + " [" + region.getType() + "] "
                    + format(region.getMin()) + " -> " + format(region.getMax())), false);
        }
        return all.size();
    }

    private static int removeRegion(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "isim");
        boolean removed = RegionManager.remove(name);
        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.literal("Bolge silindi: " + name), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("'" + name + "' isimli bolge bulunamadi."));
            return 0;
        }
    }

    private static int stats(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data ->
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "Rating: " + data.getPvpRating() + " | Gold: " + data.getGold()
                                + " | Level: " + data.getLevel() + " | W/L: " + data.getWins() + "/" + data.getLosses()), false));
        return 1;
    }

    private static int giveStat(CommandContext<CommandSourceStack> ctx, String stat) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "oyuncu");
        int amount = IntegerArgumentType.getInteger(ctx, "miktar");

        target.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data -> {
            switch (stat) {
                case "gold" -> data.addGold(amount);
                case "rating" -> data.addPvpRating(amount);
                case "pvpxp" -> data.addPvpXp(amount);
                case "pvexp" -> data.addPveXp(amount);
                case "tokens" -> data.addArenaTokens(amount);
                case "level" -> data.setLevel(data.getLevel() + amount);
            }
        });

        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " icin " + stat + " +" + amount), true);
        return 1;
    }

    private static int selectHero(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String characterId = StringArgumentType.getString(ctx, "characterId");

        SuperCharacter character = CharacterRegistry.get(characterId);
        if (character == null) {
            ctx.getSource().sendFailure(Component.literal("Karakter bulunamadi: " + characterId));
            return 0;
        }

        AbilityManager.assignCharacter(player, characterId);
        ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CameraStatePacket(true));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Karakter secildi: " + character.getDisplayName()), false);
        return 1;
    }

    private static String format(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}
