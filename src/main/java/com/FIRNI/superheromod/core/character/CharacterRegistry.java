package com.FIRNI.superheromod.core.character;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sistemde var olan TUM karakterlerin merkezi kayit defteri.
 * Yeni bir karakter yazdiginda burada register edilir,
 * boylece karakter secim ekrani, matchmaking, unlock sistemi
 * hepsi bu listeden karakterleri okuyabilir.
 */
public class CharacterRegistry {

    // LinkedHashMap kullaniyoruz ki eklenme sirasi korunsun (secim ekraninda duzenli gorunsun)
    private static final Map<String, SuperCharacter> characters = new LinkedHashMap<>();

    /**
     * Yeni bir karakteri sisteme kaydeder.
     * Her karakter kendi dosyasinda, mod baslarken bunu cagirir.
     */
    public static void register(SuperCharacter character) {
        characters.put(character.getCharacterId(), character);
    }

    public static SuperCharacter get(String characterId) {
        return characters.get(characterId);
    }

    public static Map<String, SuperCharacter> getAll() {
        return characters;
    }

    public static boolean exists(String characterId) {
        return characters.containsKey(characterId);
    }
}