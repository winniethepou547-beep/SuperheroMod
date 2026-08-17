package com.FIRNI.superheromod.core.cinematic;

import java.util.HashMap;
import java.util.Map;

/**
 * Sinematik tanimlari kaydi. Hem sunucu hem istemci ayni tanimlari bilir;
 * ag uzerinden sadece "hangi sinematik + kacinci tick" gonderilir.
 */
public final class CinematicRegistry {

    private static final Map<String, CinematicDefinition> byId = new HashMap<>();
    /** Ag icin kisa sayisal kimlik. */
    private static final Map<Integer, CinematicDefinition> byIndex = new HashMap<>();
    private static final Map<String, Integer> indexOf = new HashMap<>();

    private CinematicRegistry() {}

    public static synchronized void register(CinematicDefinition def) {
        if (byId.containsKey(def.id)) return;
        int index = byId.size();
        byId.put(def.id, def);
        byIndex.put(index, def);
        indexOf.put(def.id, index);
    }

    public static CinematicDefinition get(String id) {
        return byId.get(id);
    }

    public static CinematicDefinition byIndex(int index) {
        return byIndex.get(index);
    }

    public static int indexOf(String id) {
        return indexOf.getOrDefault(id, -1);
    }
}
