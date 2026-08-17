package com.FIRNI.superheromod.network;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.AbilityInputPacket;
import com.FIRNI.superheromod.network.packet.BeamSyncPacket;
import com.FIRNI.superheromod.network.packet.CameraStatePacket;
import com.FIRNI.superheromod.network.packet.CinematicSyncPacket;
import com.FIRNI.superheromod.network.packet.GroundFxPacket;
import com.FIRNI.superheromod.network.packet.HeatSyncPacket;
import com.FIRNI.superheromod.network.packet.MatchEndPacket;
import com.FIRNI.superheromod.network.packet.MatchFoundPacket;
import com.FIRNI.superheromod.network.packet.MatchStartingPacket;
import com.FIRNI.superheromod.network.packet.ModeSelectedPacket;
import com.FIRNI.superheromod.network.packet.OpenModeSelectPacket;
import com.FIRNI.superheromod.network.packet.MatchHistorySyncPacket;
import com.FIRNI.superheromod.network.packet.PlayerDataSyncPacket;
import com.FIRNI.superheromod.network.packet.PlayerReadyPacket;
import com.FIRNI.superheromod.network.packet.ShowVsScreenPacket;
import com.FIRNI.superheromod.network.packet.VsReadyUpdatePacket;
import com.FIRNI.superheromod.network.packet.ScoreboardUpdatePacket;
import com.FIRNI.superheromod.network.packet.StartMapVotePacket;
import com.FIRNI.superheromod.network.packet.UltimateConfirmPacket;
import com.FIRNI.superheromod.network.packet.UltimateStatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Sunucu-istemci paket kanali. Tum kuyruk/queue/match-found/harita-secim
 * ekranlari bu kanal uzerinden tetikleniyor.
 */
public final class ModNetworking {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SuperheroMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private ModNetworking() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenModeSelectPacket.class,
                OpenModeSelectPacket::encode, OpenModeSelectPacket::decode, OpenModeSelectPacket::handle);
        CHANNEL.registerMessage(id++, ModeSelectedPacket.class,
                ModeSelectedPacket::encode, ModeSelectedPacket::decode, ModeSelectedPacket::handle);
        CHANNEL.registerMessage(id++, MatchFoundPacket.class,
                MatchFoundPacket::encode, MatchFoundPacket::decode, MatchFoundPacket::handle);
        CHANNEL.registerMessage(id++, StartMapVotePacket.class,
                StartMapVotePacket::encode, StartMapVotePacket::decode, StartMapVotePacket::handle);
        CHANNEL.registerMessage(id++, MatchStartingPacket.class,
                MatchStartingPacket::encode, MatchStartingPacket::decode, MatchStartingPacket::handle);
        CHANNEL.registerMessage(id++, ScoreboardUpdatePacket.class,
                ScoreboardUpdatePacket::encode, ScoreboardUpdatePacket::decode, ScoreboardUpdatePacket::handle);
        CHANNEL.registerMessage(id++, MatchEndPacket.class,
                MatchEndPacket::encode, MatchEndPacket::decode, MatchEndPacket::handle);
        CHANNEL.registerMessage(id++, PlayerDataSyncPacket.class,
                PlayerDataSyncPacket::encode, PlayerDataSyncPacket::decode, PlayerDataSyncPacket::handle);
        CHANNEL.registerMessage(id++, MatchHistorySyncPacket.class,
                MatchHistorySyncPacket::encode, MatchHistorySyncPacket::decode, MatchHistorySyncPacket::handle);
        CHANNEL.registerMessage(id++, ShowVsScreenPacket.class,
                ShowVsScreenPacket::encode, ShowVsScreenPacket::decode, ShowVsScreenPacket::handle);
        CHANNEL.registerMessage(id++, PlayerReadyPacket.class,
                PlayerReadyPacket::encode, PlayerReadyPacket::decode, PlayerReadyPacket::handle);
        CHANNEL.registerMessage(id++, VsReadyUpdatePacket.class,
                VsReadyUpdatePacket::encode, VsReadyUpdatePacket::decode, VsReadyUpdatePacket::handle);
        CHANNEL.registerMessage(id++, AbilityInputPacket.class,
                AbilityInputPacket::encode, AbilityInputPacket::decode, AbilityInputPacket::handle);
        CHANNEL.registerMessage(id++, HeatSyncPacket.class,
                HeatSyncPacket::encode, HeatSyncPacket::decode, HeatSyncPacket::handle);
        CHANNEL.registerMessage(id++, CameraStatePacket.class,
                CameraStatePacket::encode, CameraStatePacket::decode, CameraStatePacket::handle);
        CHANNEL.registerMessage(id++, BeamSyncPacket.class,
                BeamSyncPacket::encode, BeamSyncPacket::decode, BeamSyncPacket::handle);
        CHANNEL.registerMessage(id++, UltimateStatePacket.class,
                UltimateStatePacket::encode, UltimateStatePacket::decode, UltimateStatePacket::handle);
        CHANNEL.registerMessage(id++, UltimateConfirmPacket.class,
                UltimateConfirmPacket::encode, UltimateConfirmPacket::decode, UltimateConfirmPacket::handle);
        CHANNEL.registerMessage(id++, CinematicSyncPacket.class,
                CinematicSyncPacket::encode, CinematicSyncPacket::decode, CinematicSyncPacket::handle);
        CHANNEL.registerMessage(id++, GroundFxPacket.class,
                GroundFxPacket::encode, GroundFxPacket::decode, GroundFxPacket::handle);
    }
}
