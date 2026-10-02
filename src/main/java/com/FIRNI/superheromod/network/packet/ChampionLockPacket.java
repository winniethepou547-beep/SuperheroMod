package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.character.CharacterRegistry;
import com.FIRNI.superheromod.core.character.SuperCharacter;
import com.FIRNI.superheromod.core.film.FilmSessions;
import com.FIRNI.superheromod.core.hero.HeroIdentitySync;
import com.FIRNI.superheromod.network.ModNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** LOCK IN on the champion select screen: become this hero (the same as /superhero hero, for anyone). */
public record ChampionLockPacket(String character) {
    public static void encode(ChampionLockPacket p, FriendlyByteBuf b) { b.writeUtf(p.character, 64); }
    public static ChampionLockPacket decode(FriendlyByteBuf b) { return new ChampionLockPacket(b.readUtf(64)); }
    public static void handle(ChampionLockPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> {
            var player = c.get().getSender();
            if (player == null) return;
            SuperCharacter character = CharacterRegistry.get(p.character);
            // Not mid-film: a finisher holds both of its performers.
            if (character == null || FilmSessions.busy(player.getUUID())) return;
            AbilityManager.assignCharacter(player, p.character);
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CameraStatePacket(true));
            HeroIdentitySync.broadcast(player);
            player.displayClientMessage(Component.literal("§6" + character.getDisplayName() + " §fseçildi"), true);
        });
        c.get().setPacketHandled(true);
    }
}
