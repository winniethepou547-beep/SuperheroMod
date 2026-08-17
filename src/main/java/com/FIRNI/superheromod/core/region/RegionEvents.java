package com.FIRNI.superheromod.core.region;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class RegionEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        RegionManager.load(event);
    }
}
