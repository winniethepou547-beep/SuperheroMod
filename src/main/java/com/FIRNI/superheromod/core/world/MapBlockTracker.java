package com.FIRNI.superheromod.core.world;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.matchmaking.MatchManager;
import com.FIRNI.superheromod.core.matchmaking.ActiveMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class MapBlockTracker {

    private static final Map<UUID, Map<BlockPos, BlockState>> MATCH_CHANGES = new ConcurrentHashMap<>();

    private MapBlockTracker() {}

    public static void startTracking(UUID matchId) {
        MATCH_CHANGES.put(matchId, new ConcurrentHashMap<>());
    }

    public static void stopTracking(UUID matchId) {
        MATCH_CHANGES.remove(matchId);
    }

    public static List<Runnable> restoreJobs(UUID matchId, ServerLevel level) {
        Map<BlockPos, BlockState> changes = MATCH_CHANGES.get(matchId);
        if (changes == null || changes.isEmpty()) return List.of();

        List<Map.Entry<BlockPos, BlockState>> entries = new ArrayList<>(changes.entrySet());
        changes.clear();

        List<Runnable> jobs = new ArrayList<>();
        int batchSize = 400;
        for (int i = 0; i < entries.size(); i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, entries.size());
            jobs.add(() -> {
                for (int j = start; j < end; j++) {
                    Map.Entry<BlockPos, BlockState> e = entries.get(j);
                    level.setBlock(e.getKey(), e.getValue(), 2);
                }
            });
        }
        return jobs;
    }

    private static void trackChange(UUID matchId, BlockPos pos, BlockState originalState) {
        Map<BlockPos, BlockState> changes = MATCH_CHANGES.get(matchId);
        if (changes != null) {
            changes.putIfAbsent(pos, originalState);
        }
    }

    private static UUID findMatchForPlayer(ServerPlayer player) {
        ActiveMatch match = MatchManager.getMatchOf(player.getUUID());
        return match != null ? match.getMatchId() : null;
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(PvpMapLocations.DIMENSION)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        UUID matchId = findMatchForPlayer(player);
        if (matchId != null) {
            trackChange(matchId, event.getPos(), event.getState());
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(PvpMapLocations.DIMENSION)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID matchId = findMatchForPlayer(player);
        if (matchId != null) {
            trackChange(matchId, event.getPos(), Blocks.AIR.defaultBlockState());
        }
    }
}
