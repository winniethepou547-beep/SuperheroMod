package com.FIRNI.superheromod.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Istemcide hangi oyuncu Sand Colossus formunda ve kristalleri ne durumda. */
public final class ClientColossusData {

    private static final Map<UUID, int[]> colossi = new ConcurrentHashMap<>();
    /** Olusma ilerlemesi — gorsel olcek buradan hesaplaniyor. */
    private static final Map<UUID, Float> formProgress = new ConcurrentHashMap<>();

    /** Form yeni acildiysa kamera bir kez ucuncu sahsa alinsin diye isaret. */
    private static volatile boolean justActivated = false;

    private ClientColossusData() {}

    public static void set(UUID playerId, boolean active,
                           float progress, int[] crystalStates) {
        if (!active) {
            colossi.remove(playerId);
            formProgress.remove(playerId);
            return;
        }

        boolean wasActive = colossi.containsKey(playerId);
        colossi.put(playerId, crystalStates);
        formProgress.put(playerId, progress);

        Minecraft mc = Minecraft.getInstance();
        if (!wasActive && mc.player != null && mc.player.getUUID().equals(playerId)) {
            justActivated = true;
        }
    }

    public static boolean isColossus(Player player) {
        return player != null && colossi.containsKey(player.getUUID());
    }

    public static boolean isLocalColossus() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && colossi.containsKey(mc.player.getUUID());
    }

    /** Kristalin gorsel durumu; bilinmiyorsa saglam kabul edilir. */
    public static int crystalState(Player player, int index) {
        int[] states = colossi.get(player.getUUID());
        if (states == null || index < 0 || index >= states.length) return 0;
        return states[index];
    }

    /** Olusma ilerlemesi 0..1; kayit yoksa tam olusmus kabul edilir. */
    public static float formProgress(Player player) {
        return formProgress.getOrDefault(player.getUUID(), 1f);
    }

    /** Kamera bir kez ayarlansin diye okunup sifirlanan bayrak. */
    public static boolean consumeActivation() {
        if (!justActivated) return false;
        justActivated = false;
        return true;
    }

    public static void clear() {
        colossi.clear();
        formProgress.clear();
        justActivated = false;
    }
}
