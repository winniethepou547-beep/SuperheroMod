package com.FIRNI.superheromod.core.social;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.matchmaking.QueueManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class SocialCleanupHandler {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QueueManager.dequeue(player);
            MessageManager.cleanup(player.getUUID());
            PartyManager.cleanup(player.getUUID());
        }
    }
}
