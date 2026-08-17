package com.FIRNI.superheromod.core.ability;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.character.CharacterRegistry;
import com.FIRNI.superheromod.core.character.SuperCharacter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public class AbilityManager {

    private static final Map<UUID, String> playerCharacters = new HashMap<>();

    public static void assignCharacter(ServerPlayer player, String characterId) {
        playerCharacters.put(player.getUUID(), characterId);
    }

    public static void removePlayer(UUID playerId) {
        playerCharacters.remove(playerId);
    }

    public static String getCharacterId(UUID playerId) {
        return playerCharacters.get(playerId);
    }

    public static SuperCharacter getCharacter(UUID playerId) {
        String id = playerCharacters.get(playerId);
        if (id == null) return null;
        return CharacterRegistry.get(id);
    }

    public static Ability getAbility(UUID playerId, AbilitySlot slot) {
        SuperCharacter character = getCharacter(playerId);
        if (character == null) return null;
        for (Ability ability : character.getAbilities()) {
            if (ability.getSlot() == slot) return ability;
        }
        return null;
    }

    public static void activateAbility(ServerPlayer player, AbilitySlot slot) {
        Ability ability = getAbility(player.getUUID(), slot);
        if (ability != null) {
            ability.activate(player);
        }
    }

    public static void deactivateAbility(ServerPlayer player, AbilitySlot slot) {
        Ability ability = getAbility(player.getUUID(), slot);
        if (ability != null) {
            ability.deactivate(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Map.Entry<UUID, String> entry : playerCharacters.entrySet()) {
            SuperCharacter character = CharacterRegistry.get(entry.getValue());
            if (character == null) continue;

            ServerPlayer player = findPlayer(entry.getKey());
            if (player == null) continue;

            for (Ability ability : character.getAbilities()) {
                ability.tick(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        removePlayer(event.getEntity().getUUID());
    }

    private static ServerPlayer findPlayer(UUID playerId) {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(playerId);
    }
}
