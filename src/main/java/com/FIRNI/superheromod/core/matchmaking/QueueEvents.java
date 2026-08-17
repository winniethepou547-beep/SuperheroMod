package com.FIRNI.superheromod.core.matchmaking;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Oyuncu baglantisi koparsa kuyruktan dusur - aksi halde hayalet kayit maci sonsuza dek tikar. */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class QueueEvents {

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QueueManager.dequeue(player);
        }
    }
}
