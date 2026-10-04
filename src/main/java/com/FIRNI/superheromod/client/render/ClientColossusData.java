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
    private static final Map<UUID,Double> formStart=new ConcurrentHashMap<>();

    /** Form yeni acildiysa kamera bir kez ucuncu sahsa alinsin diye isaret. */
    private static volatile boolean justActivated = false;

    private ClientColossusData() {}

    public static void set(UUID playerId, boolean active,
                           float progress, int[] crystalStates) {
        if (!active) {
            // The giant does not just vanish: what he looked like last crumbles into sand.
            if (colossi.containsKey(playerId))
                com.FIRNI.superheromod.client.render.colossus.ColossusRenderer.dissolve(playerId);
            colossi.remove(playerId);
            formProgress.remove(playerId);
            formStart.remove(playerId);
            return;
        }

        boolean wasActive = colossi.containsKey(playerId);
        int[] before = colossi.get(playerId);
        colossi.put(playerId, crystalStates);
        // A crystal that took damage throws off shards (and bursts when it breaks).
        if (before != null) {
            for (int i = 0; i < Math.min(before.length, crystalStates.length); i++) {
                if (crystalStates[i] > before[i])
                    com.FIRNI.superheromod.client.render.colossus.ColossusRenderer.crystalChanged(playerId, i, before[i], crystalStates[i]);
            }
        }
        formProgress.put(playerId, progress);

        Minecraft mc = Minecraft.getInstance();
        if(mc.level!=null) {
            double start=mc.level.getGameTime()-progress*com.FIRNI.superheromod.heroes.sandman.ColossusForm.FORM_TICKS;
            Double old=formStart.get(playerId);
            if(old==null || Math.abs(old-start)>5)formStart.put(playerId,start);
        }
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
        float reported=formProgress.getOrDefault(player.getUUID(),1f);
        var mc=Minecraft.getInstance();
        Double start=formStart.get(player.getUUID());
        if(reported>=1||start==null||mc.level==null)return reported;
        return net.minecraft.util.Mth.clamp((float)((mc.level.getGameTime()+mc.getFrameTime()-start)
                /com.FIRNI.superheromod.heroes.sandman.ColossusForm.FORM_TICKS),0,1);
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
        formStart.clear();
        justActivated = false;
    }
}
