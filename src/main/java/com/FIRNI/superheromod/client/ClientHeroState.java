package com.FIRNI.superheromod.client;

/** Yerel oyuncunun bir kahramana atanip atanmadigini tutar (istemci tarafi). */
public class ClientHeroState {

    private static boolean hero = false;

    public static void setHero(boolean value) {
        hero = value;
    }

    public static boolean isHero() {
        return hero;
    }
}
