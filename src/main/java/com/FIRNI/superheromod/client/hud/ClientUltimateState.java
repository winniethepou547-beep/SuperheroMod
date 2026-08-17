package com.FIRNI.superheromod.client.hud;

import com.FIRNI.superheromod.network.packet.UltimateStatePacket;
import net.minecraft.client.Minecraft;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClientUltimateState {

    private static final Map<UUID, Byte> phases = new ConcurrentHashMap<>();

    public static void setPhase(UUID playerId, byte phase) {
        if (phase == UltimateStatePacket.PHASE_NONE) {
            phases.remove(playerId);
        } else {
            phases.put(playerId, phase);
        }
    }

    public static byte getPhase(UUID playerId) {
        return phases.getOrDefault(playerId, UltimateStatePacket.PHASE_NONE);
    }

    /** Yerel oyuncu nisan modunda mi? (Confirm/Cancel HUD'u icin) */
    public static boolean isAiming() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return getPhase(mc.player.getUUID()) == UltimateStatePacket.PHASE_AIMING;
    }

    public static boolean isWindup(UUID playerId) {
        return getPhase(playerId) == UltimateStatePacket.PHASE_WINDUP;
    }

    /** Yerel oyuncu ultinin herhangi bir fazinda mi? (kamera acilmasi icin) */
    public static boolean isAnyPhase() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return getPhase(mc.player.getUUID()) != UltimateStatePacket.PHASE_NONE;
    }

    public static void clear() {
        phases.clear();
    }
}
