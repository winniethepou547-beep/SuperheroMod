package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;

/**
 * Hellfire actually lights the world: invisible light blocks follow the flames, on this client
 * only. Nothing is sent to or saved on the server; a light is only ever placed into empty air
 * and removed as soon as the flame moves on.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class GhostLights {
    private static final Map<BlockPos, Integer> PLACED = new HashMap<>();
    private static final Map<BlockPos, Integer> WANTED = new HashMap<>();
    private static ClientLevel world;
    private static final int MAX_LIGHTS = 48;
    /** Light block changes allowed per tick: each one costs a light update and a chunk re-mesh. */
    private static final int MAX_CHANGES = 4;
    /** Sources faster than this (blocks/tick) are left unlit; chasing them re-lit chunks every tick. */
    private static final double FAST = .35;

    /** Ask for light around a flame this tick; the brightest request per block wins. */
    public static void request(Vec3 at, int level) {
        if (WANTED.size() >= MAX_LIGHTS) return;
        // Snap to a 2-block lattice so a slowly moving flame keeps the same light block.
        BlockPos pos = new BlockPos(Math.floorDiv((int) Math.floor(at.x), 2) * 2, Math.floorDiv((int) Math.floor(at.y), 2) * 2 + 1, Math.floorDiv((int) Math.floor(at.z), 2) * 2);
        WANTED.merge(pos, Math.max(1, Math.min(15, level)), Math::max);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != world) { PLACED.clear(); WANTED.clear(); world = mc.level; }
        if (mc.level == null || mc.player == null) return;
        collect(mc);
        var level = mc.level;
        // Retire lights whose flame has left; only ever remove our own light blocks.
        int[] budget = {MAX_CHANGES};
        PLACED.entrySet().removeIf(entry -> {
            Integer wanted = WANTED.get(entry.getKey());
            if (wanted != null && wanted.equals(entry.getValue())) return false;
            if (budget[0] <= 0) return false;
            budget[0]--;
            if (level.getBlockState(entry.getKey()).is(Blocks.LIGHT)) level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), 18);
            return true;
        });
        for (var entry : WANTED.entrySet()) {
            BlockPos pos = entry.getKey();
            if (PLACED.containsKey(pos) || budget[0] <= 0) continue;
            budget[0]--;
            var state = level.getBlockState(pos);
            if (!state.isAir()) continue;
            level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, entry.getValue()), 18);
            PLACED.put(pos, entry.getValue());
        }
        WANTED.clear();
    }
    /** Flames that are always lit: the skull and a running Hell Cycle's wheels. */
    private static void collect(Minecraft mc) {
        for (Player player : mc.level.players()) {
            if (player.distanceToSqr(mc.player) > 64 * 64) continue;
            if (!"ghost_rider".equals(ClientHeroRegistry.get(player.getUUID())) || player.isInvisible()) continue;
            Vec3 moved = (player.getVehicle() != null ? player.getVehicle() : player).getDeltaMovement();
            if (moved.horizontalDistance() > FAST || player.getVehicle() instanceof HellCycleEntity bike && bike.speed() > FAST) continue;
            request(player.getEyePosition().add(0, .35, 0), 14);
        }
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof HellCycleEntity bike) || bike.distanceToSqr(mc.player) > 64 * 64 || bike.speed() > FAST) continue;
            Vec3 forward = Vec3.directionFromRotation(0, bike.getYRot());
            request(bike.position().add(0, .5, 0).add(forward.scale(1.4)), 12);
            request(bike.position().add(0, .5, 0).subtract(forward.scale(1.0)), 13);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { PLACED.clear(); WANTED.clear(); world = null; }
}
