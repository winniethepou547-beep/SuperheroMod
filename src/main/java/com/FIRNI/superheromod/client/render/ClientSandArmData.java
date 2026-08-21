package com.FIRNI.superheromod.client.render;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Uzayan kum kolunun istemci tarafi durumu.
 *
 * Kol artik dunya uzayinda cizilmiyor; oyuncu modelinin bir KATMANI
 * olarak, omuz donusumunun icinden ciziliyor. Bu yuzden burada konum
 * degil sadece UZUNLUK tutuluyor -- konumu zaten kolun kendisi
 * belirliyor ve kol bedene kusursuz yapisik kaliyor.
 *
 * Onceki surumde kol dunya koordinatlariyla ciziliyordu ve oyuncu her
 * kipirdadiginda koldan kopuyordu; fotografta gorulen "koldan bile
 * cikmiyor" sorunu tam olarak buydu.
 */
public final class ClientSandArmData {

    /** Sunucu tick suresi (ms) — ara deger bu pencereye yayiliyor. */
    private static final float TICK_MS = 50f;

    public record Arm(float length, boolean active, float hammer) {}

    private static volatile Map<Integer, Arm> previous = Collections.emptyMap();
    private static volatile Map<Integer, Arm> current = Collections.emptyMap();
    private static volatile long currentTime = 0L;

    private ClientSandArmData() {}

    public static void set(Map<Integer, Arm> next) {
        previous = current;
        current = next;
        currentTime = System.currentTimeMillis();
    }

    /**
     * Oyuncunun o anki kol uzunlugu (blok), ara degeri uygulanmis.
     *
     * Sunucu saniyede 20 guncelleme gonderiyor, ekran 60+ kare ciziyor;
     * ham deger kullanilirsa kol saniyede 20 kez sicrayarak uzuyor.
     */
    public static float lengthOf(int entityId) {
        Arm now = current.get(entityId);
        if (now == null) return 0f;

        long age = System.currentTimeMillis() - currentTime;
        if (age > 500L) return 0f;   // sunucu sustu, kol ekranda kalmasin

        Arm before = previous.get(entityId);
        if (before == null) return now.length();

        float t = Math.min(1f, age / TICK_MS);
        return before.length() + (now.length() - before.length()) * t;
    }

    /** Ucta olusan balyozun orani, ara degeri uygulanmis. */
    public static float hammerOf(int entityId) {
        Arm now = current.get(entityId);
        if (now == null) return 0f;

        long age = System.currentTimeMillis() - currentTime;
        if (age > 500L) return 0f;

        Arm before = previous.get(entityId);
        if (before == null) return now.hammer();

        float t = Math.min(1f, age / TICK_MS);
        return before.hammer() + (now.hammer() - before.hammer()) * t;
    }

    /**
     * Yetenek acik mi — kol SIFIR uzunlukta olsa bile.
     *
     * Poz bunu kullaniyor: sarj sirasinda kol henuz uzamamis oluyor ama
     * ileri bakmasi gerekiyor, ve geri toplanma bitene kadar ileri
     * bakmaya devam etmeli.
     */
    public static boolean isActive(int entityId) {
        if (System.currentTimeMillis() - currentTime > 500L) return false;
        Arm now = current.get(entityId);
        return now != null && now.active();
    }

    public static void clear() {
        previous = Collections.emptyMap();
        current = Collections.emptyMap();
    }
}
