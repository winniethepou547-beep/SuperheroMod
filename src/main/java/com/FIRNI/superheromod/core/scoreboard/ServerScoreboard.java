package com.FIRNI.superheromod.core.scoreboard;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.matchmaking.MatchManager;
import com.FIRNI.superheromod.core.progression.LevelSystem;
import com.FIRNI.superheromod.core.progression.ProgressionEvents;
import com.FIRNI.superheromod.core.progression.RankTier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class ServerScoreboard {

    private static final String OBJECTIVE_NAME = "shnet";
    private static int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 40;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter < UPDATE_INTERVAL) return;
        tickCounter = 0;

        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (MatchManager.getMatchOf(player.getUUID()) != null) continue;
            updateScoreboard(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            updateScoreboard(player);
        }
    }

    private static void updateScoreboard(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();

        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }

        objective = scoreboard.addObjective(
                OBJECTIVE_NAME,
                ObjectiveCriteria.DUMMY,
                Component.literal("§6§lSUPERHERO NETWORK"),
                ObjectiveCriteria.RenderType.INTEGER
        );
        scoreboard.setDisplayObjective(1, objective);

        final Objective obj = objective;
        player.getCapability(ProgressionEvents.PROGRESSION_CAPABILITY).ifPresent(data -> {
            RankTier tier = RankTier.fromRating(data.getPvpRating());
            int xpNeeded = LevelSystem.xpForLevel(data.getLevel());
            int online = player.getServer() != null ? player.getServer().getPlayerCount() : 0;

            setLine(scoreboard, obj, "§7§m           ", 10);
            setLine(scoreboard, obj, "§fAltin: §6" + data.getGold(), 9);
            setLine(scoreboard, obj, "§fToken: §d" + data.getArenaTokens(), 8);
            setLine(scoreboard, obj, " ", 7);
            setLine(scoreboard, obj, "§fRating: §e" + data.getPvpRating(), 6);
            setLine(scoreboard, obj, "§fRank: " + tier.getColoredName(), 5);
            setLine(scoreboard, obj, "§fW/L: §a" + data.getWins() + "§7/§c" + data.getLosses(), 4);
            setLine(scoreboard, obj, "  ", 3);
            setLine(scoreboard, obj, "§fSeviye: §a" + data.getLevel(), 2);
            setLine(scoreboard, obj, "§fXP: §b" + data.getPveXp() + "§7/" + xpNeeded, 1);
            setLine(scoreboard, obj, "§7Online: §f" + online, 0);
        });
    }

    private static void setLine(Scoreboard scoreboard, Objective objective, String text, int score) {
        scoreboard.getOrCreatePlayerScore(text, objective).setScore(score);
    }
}
