package com.FIRNI.superheromod.heroes.sandman;

import net.minecraft.world.phys.Vec3;

/**
 * SAND COLOSSUS'UN ZAYIF NOKTALARI.
 *
 * Dokumandaki ana mekanik: dev cok yuksek dayanikliliga sahip ama vucudundaki
 * kristaller normal govdeye gore 2 KAT hasar alir. Boylece oyuncular sadece
 * can eritmez, devin vucudunu stratejik olarak parcalar.
 *
 * Her kristalin kirilmasinin AYRI bir bedeli var; hangi kristali once
 * kirdigin devin nasil zayifladigini belirliyor.
 */
public enum ColossusCrystal {

    // Konumlar DEVIN 10 BLOKLUK olcegine gore:
    //   alt kum kutlesi  0.0 - 3.5
    //   govde            3.5 - 7.5
    //   kafa             7.5 - 9.5
    //   omuzlar          ~7.0 hizasinda

    /** Gogus — kirilinca ultinin SURESI kisalir ve zirh duser. */
    CHEST("Gogus", new Vec3(0.0, 5.60, -1.70), 1.10, 40f),

    /** Sag omuz — kirilinca sag kolun vurusu zayiflar ve yavaslar. */
    RIGHT_SHOULDER("Sag Omuz", new Vec3(-2.90, 7.00, 0.0), 0.95, 30f),

    /** Sol omuz — ayni sekilde sol kol icin. */
    LEFT_SHOULDER("Sol Omuz", new Vec3(2.90, 7.00, 0.0), 0.95, 30f),

    /** Kafa — kirilinca dev kisa sure sersemler. */
    HEAD("Kafa", new Vec3(0.0, 8.50, -1.00), 0.80, 26f),

    /** Sirt — arkadan saldiranlar icin firsat. */
    BACK("Sirt", new Vec3(0.0, 6.00, 1.70), 1.00, 34f);

    /** Devin toplam boyu (blok). Kamera ve carpisma kutusu buna gore. */
    public static final float COLOSSUS_HEIGHT = 10.0f;
    /** Devin govde genisligi (blok). */
    public static final float COLOSSUS_WIDTH = 3.6f;

    /** Devin merkezine gore yerel konum (blok). z negatif = on taraf. */
    public final Vec3 offset;
    /** Isabet yaricapi (blok). */
    public final double radius;
    /** Kristalin dayanikliligi. */
    public final float maxHealth;
    public final String displayName;

    ColossusCrystal(String displayName, Vec3 offset, double radius, float maxHealth) {
        this.displayName = displayName;
        this.offset = offset;
        this.radius = radius;
        this.maxHealth = maxHealth;
    }

    /**
     * Gorsel hasar durumu — modelde catlak seviyesi olarak gosterilecek.
     *
     * 0 saglam, 1 catlak, 2 agir catlak, 3 kirik
     */
    public static int stateOf(float health, float maxHealth) {
        if (health <= 0f) return 3;
        float ratio = health / maxHealth;
        if (ratio > 0.66f) return 0;
        if (ratio > 0.33f) return 1;
        return 2;
    }

    /**
     * Kristalin dunya konumu.
     *
     * Devin baktigi yone gore donduruluyor; aksi halde arkadan saldiran biri
     * gogus kristaline vurabilirdi.
     */
    public Vec3 worldPosition(Vec3 base, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);

        // Standart Y ekseni donusu (Minecraft yaw yonune uygun)
        double x = offset.x * cos - offset.z * sin;
        double z = offset.x * sin + offset.z * cos;

        return base.add(x, offset.y, z);
    }
}
