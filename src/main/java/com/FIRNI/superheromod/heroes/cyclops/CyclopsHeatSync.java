package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.resource.ResourceBar;
import com.FIRNI.superheromod.core.resource.ResourceManager;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.HeatSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Isı barini her tick client'a senkronize eder. Yetenek aktif olmasa bile
 * calisir: bar sogurken ve overheat cooldown'u sirasinda da dogru deger gider.
 * Bu olmadan bar birakilinca 0 gosterip tekrar basilinca eski degere "ziplar".
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class CyclopsHeatSync {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceBar heat = ResourceManager.get(player.getUUID(),
                    ConcussiveBeamAbility.HEAT_RESOURCE_ID);
            if (heat == null) continue;

            boolean visible = heat.getCurrentValue() > 0.01f || heat.isOverheated();

            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new HeatSyncPacket(heat.getCurrentValue(), heat.getMaxValue(),
                            heat.isOverheated(), visible));
        }
    }
}
