package com.FIRNI.superheromod.core.matchmaking;

import com.FIRNI.superheromod.core.world.PvpMapLocations;

import java.util.List;
import java.util.Random;

/**
 * Buyuk, ucus/kacis dostu PVP haritalari. Yer tutucu kalitede insa edildiler
 * (PvpMapBuilder) - gercek tasarimlar gelince buradaki spawn/renk/isim
 * degerleri guncellenecek, kod tarafinda baska bir sey degismesi gerekmiyor.
 */
public final class MapRegistry {

    public static final List<GameMap> MAPS = List.of(
            new GameMap("sehir", "Sehir", 0xFF7A8B99, PvpMapLocations.CITY_SPAWN),
            new GameMap("liman", "Liman", 0xFF2E9CB0, PvpMapLocations.HARBOR_SPAWN),
            new GameMap("kanyon", "Kanyon", 0xFFC1662F, PvpMapLocations.CANYON_SPAWN)
    );

    private static final Random RANDOM = new Random();

    private MapRegistry() {
    }

    public static int pickRandomIndex() {
        return RANDOM.nextInt(MAPS.size());
    }
}
