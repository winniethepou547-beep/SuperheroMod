package com.FIRNI.superheromod.network;
import com.FIRNI.superheromod.network.packet.GhostSlamPacket;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.HellfireBreathPacket;
import com.FIRNI.superheromod.network.packet.AbilityInputPacket;
import com.FIRNI.superheromod.network.packet.BeamSyncPacket;
import com.FIRNI.superheromod.network.packet.CameraStatePacket;
import com.FIRNI.superheromod.network.packet.HeroIdentityPacket;
import com.FIRNI.superheromod.network.packet.ColossusActionPacket;
import com.FIRNI.superheromod.network.packet.ColossusSyncPacket;
import com.FIRNI.superheromod.network.packet.SandArmorPacket;
import com.FIRNI.superheromod.network.packet.ShockwavePacket;
import com.FIRNI.superheromod.network.packet.SandWallActionPacket;
import com.FIRNI.superheromod.network.packet.SandGraspActionPacket;
import com.FIRNI.superheromod.network.packet.SandGraspPreviewPacket;
import com.FIRNI.superheromod.network.packet.SandPillarPacket;
import com.FIRNI.superheromod.network.packet.SandArmSyncPacket;
import com.FIRNI.superheromod.network.packet.SandTravelPacket;
import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;
import com.FIRNI.superheromod.network.packet.SandWallSyncPacket;
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

    private static final String PROTOCOL_VERSION = "25";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SuperheroMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private ModNetworking() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.HellCycleInputPacket.class,
                com.FIRNI.superheromod.network.packet.HellCycleInputPacket::encode,
                com.FIRNI.superheromod.network.packet.HellCycleInputPacket::decode,
                com.FIRNI.superheromod.network.packet.HellCycleInputPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
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
        CHANNEL.registerMessage(id++, HeroIdentityPacket.class,
                HeroIdentityPacket::encode, HeroIdentityPacket::decode, HeroIdentityPacket::handle);
        CHANNEL.registerMessage(id++, SandWallSyncPacket.class,
                SandWallSyncPacket::encode, SandWallSyncPacket::decode, SandWallSyncPacket::handle);
        CHANNEL.registerMessage(id++, SandWallActionPacket.class,
                SandWallActionPacket::encode, SandWallActionPacket::decode, SandWallActionPacket::handle);
        CHANNEL.registerMessage(id++, SandArmorPacket.class,
                SandArmorPacket::encode, SandArmorPacket::decode, SandArmorPacket::handle);
        CHANNEL.registerMessage(id++, ColossusSyncPacket.class,
                ColossusSyncPacket::encode, ColossusSyncPacket::decode, ColossusSyncPacket::handle);
        CHANNEL.registerMessage(id++, ShockwavePacket.class,
                ShockwavePacket::encode, ShockwavePacket::decode, ShockwavePacket::handle);
        CHANNEL.registerMessage(id++, ColossusActionPacket.class,
                ColossusActionPacket::encode, ColossusActionPacket::decode,
                ColossusActionPacket::handle);
        CHANNEL.registerMessage(id++, SandGraspPreviewPacket.class,
                SandGraspPreviewPacket::encode, SandGraspPreviewPacket::decode,
                SandGraspPreviewPacket::handle);
        CHANNEL.registerMessage(id++, SandGraspActionPacket.class,
                SandGraspActionPacket::encode, SandGraspActionPacket::decode,
                SandGraspActionPacket::handle);
        CHANNEL.registerMessage(id++, SandPillarPacket.class,
                SandPillarPacket::encode, SandPillarPacket::decode,
                SandPillarPacket::handle);
        CHANNEL.registerMessage(id++, SandShapeSyncPacket.class,
                SandShapeSyncPacket::encode, SandShapeSyncPacket::decode,
                SandShapeSyncPacket::handle);
        CHANNEL.registerMessage(id++, SandArmSyncPacket.class,
                SandArmSyncPacket::encode, SandArmSyncPacket::decode,
                SandArmSyncPacket::handle);
        CHANNEL.registerMessage(id++, SandTravelPacket.class,
                SandTravelPacket::encode, SandTravelPacket::decode, SandTravelPacket::handle);
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.CinematicPreviewRequestPacket.class,
                com.FIRNI.superheromod.network.packet.CinematicPreviewRequestPacket::encode,
                com.FIRNI.superheromod.network.packet.CinematicPreviewRequestPacket::decode,
                com.FIRNI.superheromod.network.packet.CinematicPreviewRequestPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.CinematicPreparePacket.class,
                com.FIRNI.superheromod.network.packet.CinematicPreparePacket::encode,
                com.FIRNI.superheromod.network.packet.CinematicPreparePacket::decode,
                com.FIRNI.superheromod.network.packet.CinematicPreparePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.CinematicReadyPacket.class,
                com.FIRNI.superheromod.network.packet.CinematicReadyPacket::encode,
                com.FIRNI.superheromod.network.packet.CinematicReadyPacket::decode,
                com.FIRNI.superheromod.network.packet.CinematicReadyPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.GhostChainPacket.class,
                com.FIRNI.superheromod.network.packet.GhostChainPacket::encode,
                com.FIRNI.superheromod.network.packet.GhostChainPacket::decode,
                com.FIRNI.superheromod.network.packet.GhostChainPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, GhostSlamPacket.class,
                GhostSlamPacket::encode,GhostSlamPacket::decode,GhostSlamPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, HellfireBreathPacket.class,
                HellfireBreathPacket::encode,HellfireBreathPacket::decode,HellfireBreathPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.GhostBindPacket.class,
                com.FIRNI.superheromod.network.packet.GhostBindPacket::encode,
                com.FIRNI.superheromod.network.packet.GhostBindPacket::decode,
                com.FIRNI.superheromod.network.packet.GhostBindPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.GhostPenancePacket.class,
                com.FIRNI.superheromod.network.packet.GhostPenancePacket::encode,
                com.FIRNI.superheromod.network.packet.GhostPenancePacket::decode,
                com.FIRNI.superheromod.network.packet.GhostPenancePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.GhostEscapePacket.class,
                com.FIRNI.superheromod.network.packet.GhostEscapePacket::encode,
                com.FIRNI.superheromod.network.packet.GhostEscapePacket::decode,
                com.FIRNI.superheromod.network.packet.GhostEscapePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.FilmSessionPacket.class,
                com.FIRNI.superheromod.network.packet.FilmSessionPacket::encode,
                com.FIRNI.superheromod.network.packet.FilmSessionPacket::decode,
                com.FIRNI.superheromod.network.packet.FilmSessionPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.ThorStatePacket.class,
                com.FIRNI.superheromod.network.packet.ThorStatePacket::encode,
                com.FIRNI.superheromod.network.packet.ThorStatePacket::decode,
                com.FIRNI.superheromod.network.packet.ThorStatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.ThorFxPacket.class,
                com.FIRNI.superheromod.network.packet.ThorFxPacket::encode,
                com.FIRNI.superheromod.network.packet.ThorFxPacket::decode,
                com.FIRNI.superheromod.network.packet.ThorFxPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.HulkStatePacket.class,
                com.FIRNI.superheromod.network.packet.HulkStatePacket::encode,
                com.FIRNI.superheromod.network.packet.HulkStatePacket::decode,
                com.FIRNI.superheromod.network.packet.HulkStatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.HulkFxPacket.class,
                com.FIRNI.superheromod.network.packet.HulkFxPacket::encode,
                com.FIRNI.superheromod.network.packet.HulkFxPacket::decode,
                com.FIRNI.superheromod.network.packet.HulkFxPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.HulkInputPacket.class,
                com.FIRNI.superheromod.network.packet.HulkInputPacket::encode,
                com.FIRNI.superheromod.network.packet.HulkInputPacket::decode,
                com.FIRNI.superheromod.network.packet.HulkInputPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.ZedStatePacket.class,
                com.FIRNI.superheromod.network.packet.ZedStatePacket::encode,
                com.FIRNI.superheromod.network.packet.ZedStatePacket::decode,
                com.FIRNI.superheromod.network.packet.ZedStatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.ZedFxPacket.class,
                com.FIRNI.superheromod.network.packet.ZedFxPacket::encode,
                com.FIRNI.superheromod.network.packet.ZedFxPacket::decode,
                com.FIRNI.superheromod.network.packet.ZedFxPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.PantherStatePacket.class,
                com.FIRNI.superheromod.network.packet.PantherStatePacket::encode,
                com.FIRNI.superheromod.network.packet.PantherStatePacket::decode,
                com.FIRNI.superheromod.network.packet.PantherStatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.PantherFxPacket.class,
                com.FIRNI.superheromod.network.packet.PantherFxPacket::encode,
                com.FIRNI.superheromod.network.packet.PantherFxPacket::decode,
                com.FIRNI.superheromod.network.packet.PantherFxPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.PantherInputPacket.class,
                com.FIRNI.superheromod.network.packet.PantherInputPacket::encode,
                com.FIRNI.superheromod.network.packet.PantherInputPacket::decode,
                com.FIRNI.superheromod.network.packet.PantherInputPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.MagnetoStatePacket.class,
                com.FIRNI.superheromod.network.packet.MagnetoStatePacket::encode,
                com.FIRNI.superheromod.network.packet.MagnetoStatePacket::decode,
                com.FIRNI.superheromod.network.packet.MagnetoStatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.MagnetoFxPacket.class,
                com.FIRNI.superheromod.network.packet.MagnetoFxPacket::encode,
                com.FIRNI.superheromod.network.packet.MagnetoFxPacket::decode,
                com.FIRNI.superheromod.network.packet.MagnetoFxPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.MagnetoInputPacket.class,
                com.FIRNI.superheromod.network.packet.MagnetoInputPacket::encode,
                com.FIRNI.superheromod.network.packet.MagnetoInputPacket::decode,
                com.FIRNI.superheromod.network.packet.MagnetoInputPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.SpikeStuckPacket.class,
                com.FIRNI.superheromod.network.packet.SpikeStuckPacket::encode,
                com.FIRNI.superheromod.network.packet.SpikeStuckPacket::decode,
                com.FIRNI.superheromod.network.packet.SpikeStuckPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.SpikePullPacket.class,
                com.FIRNI.superheromod.network.packet.SpikePullPacket::encode,
                com.FIRNI.superheromod.network.packet.SpikePullPacket::decode,
                com.FIRNI.superheromod.network.packet.SpikePullPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.BatmanStatePacket.class,
                com.FIRNI.superheromod.network.packet.BatmanStatePacket::encode,
                com.FIRNI.superheromod.network.packet.BatmanStatePacket::decode,
                com.FIRNI.superheromod.network.packet.BatmanStatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.BatmanFxPacket.class,
                com.FIRNI.superheromod.network.packet.BatmanFxPacket::encode,
                com.FIRNI.superheromod.network.packet.BatmanFxPacket::decode,
                com.FIRNI.superheromod.network.packet.BatmanFxPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.BatmanInputPacket.class,
                com.FIRNI.superheromod.network.packet.BatmanInputPacket::encode,
                com.FIRNI.superheromod.network.packet.BatmanInputPacket::decode,
                com.FIRNI.superheromod.network.packet.BatmanInputPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, com.FIRNI.superheromod.network.packet.ChampionLockPacket.class,
                com.FIRNI.superheromod.network.packet.ChampionLockPacket::encode,
                com.FIRNI.superheromod.network.packet.ChampionLockPacket::decode,
                com.FIRNI.superheromod.network.packet.ChampionLockPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
    }
}
