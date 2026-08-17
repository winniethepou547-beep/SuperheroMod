package com.FIRNI.superheromod.core.region;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.world.ArenaLocations;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.OpenModeSelectPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Oyuncunun hangi bolgede oldugunu takip eder, bolgeye YENI girince bir kere tetiklenir.
 * Ileride burasi GUI acma / kuyruga alma ile degistirilecek (bkz. plan C/D/E asamalari).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class RegionTickHandler {

    private static final Map<UUID, String> CURRENT_REGION = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        String previous = CURRENT_REGION.get(player.getUUID());
        Region current = findRegionAt(player);
        String currentName = current == null ? null : current.getName();

        if (!Objects.equals(previous, currentName)) {
            CURRENT_REGION.put(player.getUUID(), currentName);
            if (current != null) {
                onEnterRegion(player, current);
            }
        }
    }

    private static Region findRegionAt(ServerPlayer player) {
        ResourceLocation dimension = player.level().dimension().location();
        BlockPos pos = player.blockPosition();
        for (Region region : RegionManager.getAll().values()) {
            if (region.contains(dimension, pos)) {
                return region;
            }
        }
        return null;
    }

    private static void onEnterRegion(ServerPlayer player, Region region) {
        switch (region.getType()) {
            case PVP_ENTRY -> {
                teleportAndNotify(player, ArenaLocations.PVP_SPAWN, "PVP arenasina isinlandin!");
                ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenModeSelectPacket());
            }
            case PVE_ENTRY -> teleportAndNotify(player, ArenaLocations.PVE_SPAWN, "PVE arenasina isinlandin!");
            case MAIN_HALL_ENTRY -> teleportAndNotify(player, ArenaLocations.HUB_SPAWN, "Ana meydana geri dondun!");
        }
    }

    private static void teleportAndNotify(ServerPlayer player, BlockPos destination, String message) {
        player.teleportTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
        player.sendSystemMessage(Component.literal(message));
    }
}
