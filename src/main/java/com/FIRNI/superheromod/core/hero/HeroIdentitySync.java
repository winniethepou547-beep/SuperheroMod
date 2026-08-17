package com.FIRNI.superheromod.core.hero;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.HeroIdentityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Kim hangi kahraman — bu bilgiyi tum istemcilere yayar.
 *
 * Skin ve poz sistemi bunu kullanir. Sadece secen oyuncuya gonderilseydi
 * karakteri kendisi disinda kimse dogru gormezdi.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class HeroIdentitySync {

    private HeroIdentitySync() {}

    /** Bir oyuncunun kahraman degisikligini herkese duyurur. */
    public static void broadcast(ServerPlayer player) {
        String characterId = AbilityManager.getCharacterId(player.getUUID());
        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new HeroIdentityPacket(player.getUUID(),
                        characterId == null ? "" : characterId));
    }

    /** Kahramanligin kaldirildigini duyurur. */
    public static void broadcastCleared(ServerPlayer player) {
        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new HeroIdentityPacket(player.getUUID(), ""));
    }

    /**
     * Yeni giren oyuncuya o an sunucuda kimin ne oldugunu bildirir; aksi
     * halde kendisinden once kahraman secmis olanlari normal skinli gorurdu.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joiner)) return;

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            String id = AbilityManager.getCharacterId(other.getUUID());
            if (id == null || id.isEmpty()) continue;

            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> joiner),
                    new HeroIdentityPacket(other.getUUID(), id));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer leaver)) return;
        broadcastCleared(leaver);
    }
}
