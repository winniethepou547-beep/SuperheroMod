package com.FIRNI.superheromod.heroes.cyclops;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class CyclopsBeamRenderer {

    private static final DustParticleOptions DOT_WHITE =
            new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.75f), 0.4f);

    private static final DustParticleOptions DOT_BRIGHT_RED =
            new DustParticleOptions(new Vector3f(0.95f, 0.08f, 0.05f), 0.5f);

    private static final DustParticleOptions DOT_DARK_RED =
            new DustParticleOptions(new Vector3f(0.45f, 0.0f, 0.0f), 0.6f);

    private static final DustParticleOptions DOT_PINK =
            new DustParticleOptions(new Vector3f(1.0f, 0.2f, 0.15f), 0.45f);

    private static final DustParticleOptions DOT_DEEP_RED =
            new DustParticleOptions(new Vector3f(0.7f, 0.02f, 0.02f), 0.5f);

    private static final DustParticleOptions IMPACT_WHITE =
            new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.75f), 1.2f);

    private static final DustParticleOptions IMPACT_RED =
            new DustParticleOptions(new Vector3f(0.9f, 0.05f, 0.05f), 1.5f);

    private static final DustParticleOptions IMPACT_DARK =
            new DustParticleOptions(new Vector3f(0.35f, 0.0f, 0.0f), 1.3f);

    private static final DustParticleOptions ORIGIN_GLOW =
            new DustParticleOptions(new Vector3f(1.0f, 0.6f, 0.4f), 0.6f);

    private static final DustParticleOptions[] BEAM_DOTS = {
            DOT_WHITE, DOT_BRIGHT_RED, DOT_DARK_RED, DOT_PINK, DOT_DEEP_RED
    };

    public static void renderImpact(ServerLevel level, Vec3 pos) {
        level.sendParticles(IMPACT_WHITE,
                pos.x, pos.y, pos.z,
                2, 0.04, 0.04, 0.04, 0);
        level.sendParticles(IMPACT_RED,
                pos.x, pos.y, pos.z,
                3, 0.1, 0.1, 0.1, 0);
        level.sendParticles(IMPACT_DARK,
                pos.x, pos.y, pos.z,
                2, 0.15, 0.15, 0.15, 0);
    }

    // --- Namlu agzi renkleri: icten disa beyaz-sicak -> pembe -> kirmizi -> bordo ---
    private static final DustParticleOptions MUZZLE_CORE =
            new DustParticleOptions(new Vector3f(1.00f, 0.96f, 0.92f), 0.35f);
    private static final DustParticleOptions MUZZLE_HOT =
            new DustParticleOptions(new Vector3f(1.00f, 0.45f, 0.52f), 0.45f);
    private static final DustParticleOptions MUZZLE_RED =
            new DustParticleOptions(new Vector3f(1.00f, 0.10f, 0.14f), 0.60f);
    private static final DustParticleOptions MUZZLE_DEEP =
            new DustParticleOptions(new Vector3f(0.55f, 0.01f, 0.05f), 0.80f);

    /** Kafa kutusu 0.5 genisliginde; yuzun on yuzeyi gozun 0.25 onunde. */
    private static final double FACE_OFFSET = 0.32;
    /** Partikul bulutunun bakis yonundeki kalinligi — one tasmasin. */
    private static final double FACE_DEPTH = 0.05;

    /**
     * Kalin isinlarin cikis agzi: yuzun ON hizasinda, bakis yonune DIK duran
     * yogun bir kor diski.
     *
     * Onceki surum partikulleri gozun merkezine kup seklinde sacyordu; bu
     * yuzden kafanin yanindan ve arkasindan da cikiyor, seyrek duruyordu.
     * Burada bulut iki sekilde sinirlaniyor:
     *
     *  1) Baslangic noktasi yuzun on yuzeyine kaydiriliyor (FACE_OFFSET), yani
     *     kafanin icinde kalmiyor.
     *  2) Sacilma yaricapi eksen basina bakis vektorune gore daraltiliyor:
     *     bakisla ayni eksende neredeyse sifir, dik eksenlerde tam genislik.
     *     Sonuc kure degil, yuze yapisik ince bir disk.
     *
     * Renkler ice dogru isiniyor: kenarda bordo hale, ortada beyaz cekirdek.
     *
     * @param widthMult isin kalinligi — disk yaricapi buna gore hafifce buyur
     */
    public static void renderEyeMuzzle(ServerLevel level, Vec3 eye, Vec3 look,
                                       float widthMult) {
        // Kalinlik farki disk boyutunu ezmesin diye bastirilmis olcek
        double scale = 0.55 + widthMult * 0.11;

        Vec3 face = eye.add(look.scale(FACE_OFFSET));

        emitDisc(level, face, look, MUZZLE_DEEP, 7, 0.19 * scale);
        emitDisc(level, face, look, MUZZLE_RED, 9, 0.14 * scale);
        emitDisc(level, face, look, MUZZLE_HOT, 7, 0.09 * scale);
        emitDisc(level, face, look, MUZZLE_CORE, 5, 0.05 * scale);
    }

    /**
     * Bakis yonune DIK bir disk uzerine partikul serper.
     *
     * sendParticles'in sacilmasi dunya eksenlerine gore calisir, bakis yonune
     * gore degil. Bu yuzden her eksenin sacilmasi o eksenin bakisa dikligiyle
     * carpiliyor: sqrt(1 - look_eksen^2). Bakisla ayni eksende bu deger sifira
     * yaklasir, dik eksenlerde bire. Ortaya cikan elipsoid, bakisa dik ince
     * bir diske denk geliyor.
     */
    private static void emitDisc(ServerLevel level, Vec3 center, Vec3 look,
                                 DustParticleOptions dust, int count, double radius) {
        double sx = radius * Math.sqrt(Math.max(0.0, 1.0 - look.x * look.x)) + FACE_DEPTH * Math.abs(look.x);
        double sy = radius * Math.sqrt(Math.max(0.0, 1.0 - look.y * look.y)) + FACE_DEPTH * Math.abs(look.y);
        double sz = radius * Math.sqrt(Math.max(0.0, 1.0 - look.z * look.z)) + FACE_DEPTH * Math.abs(look.z);

        // Hiz 0: partikuller dagilmasin, yogun bir kor kutlesi olarak dursun
        level.sendParticles(dust, center.x, center.y, center.z, count, sx, sy, sz, 0.0);
    }

    public static void renderOriginFlash(ServerLevel level, Vec3 origin) {
        level.sendParticles(ORIGIN_GLOW,
                origin.x, origin.y, origin.z,
                2, 0.02, 0.02, 0.02, 0);
        level.sendParticles(DOT_BRIGHT_RED,
                origin.x, origin.y, origin.z,
                2, 0.04, 0.04, 0.04, 0);
    }

    /** Isin cevresinde havada asili duran cok minik kabarciklar. */
    private static final DustParticleOptions BUBBLE_BRIGHT =
            new DustParticleOptions(new Vector3f(1.0f, 0.35f, 0.28f), 0.16f);
    private static final DustParticleOptions BUBBLE_RED =
            new DustParticleOptions(new Vector3f(0.95f, 0.06f, 0.04f), 0.20f);
    private static final DustParticleOptions BUBBLE_DARK =
            new DustParticleOptions(new Vector3f(0.5f, 0.0f, 0.0f), 0.24f);

    private static final DustParticleOptions[] BUBBLES = {
            BUBBLE_BRIGHT, BUBBLE_RED, BUBBLE_DARK
    };

    /**
     * Isinin etrafina cok kucuk kabarciklar serper. Isinin kendisi geometri;
     * bunlar sadece ikincil detay.
     */
    public static void renderBeamBubbles(ServerLevel level, Vec3 origin, Vec3 end, float widthMult) {
        Vec3 dir = end.subtract(origin);
        double length = dir.length();
        if (length < 0.5) return;

        Vec3 norm = dir.normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = norm.cross(up);
        if (right.lengthSqr() < 1.0E-4) right = norm.cross(new Vec3(1, 0, 0));
        right = right.normalize();
        Vec3 upPerp = norm.cross(right).normalize();

        int count = Math.min(14, (int) (length * 0.7) + 3);
        double ring = 0.30 * widthMult;

        for (int i = 0; i < count; i++) {
            double t = level.random.nextDouble();
            Vec3 pos = origin.add(norm.scale(length * t));

            // Isinin hemen disinda bir halka uzerinde
            double angle = level.random.nextDouble() * Math.PI * 2;
            double r = ring * (0.8 + level.random.nextDouble() * 0.9);
            Vec3 offset = right.scale(Math.cos(angle) * r)
                    .add(upPerp.scale(Math.sin(angle) * r));

            DustParticleOptions bubble = BUBBLES[level.random.nextInt(BUBBLES.length)];

            level.sendParticles(bubble,
                    pos.x + offset.x, pos.y + offset.y, pos.z + offset.z,
                    1, 0.015, 0.015, 0.015, 0);
        }
    }

    public static void renderBeamParticles(ServerLevel level, Vec3 origin, Vec3 end) {
        Vec3 dir = end.subtract(origin);
        double length = dir.length();
        if (length < 0.5) return;

        Vec3 norm = dir.normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = norm.cross(up).normalize();
        if (right.length() < 0.01) right = norm.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 upPerp = norm.cross(right).normalize();

        int dotCount = Math.min(8, (int)(length / 2.0) + 1);
        for (int i = 0; i < dotCount; i++) {
            double t = Math.random();
            Vec3 pos = origin.add(norm.scale(length * t));

            double spread = 0.05 + Math.random() * 0.15;
            double angle = Math.random() * Math.PI * 2;
            double ox = Math.cos(angle) * spread;
            double oy = Math.sin(angle) * spread;
            Vec3 offset = right.scale(ox).add(upPerp.scale(oy));

            DustParticleOptions dot = BEAM_DOTS[(int)(Math.random() * BEAM_DOTS.length)];

            level.sendParticles(dot,
                    pos.x + offset.x, pos.y + offset.y, pos.z + offset.z,
                    1, 0.01, 0.01, 0.01, 0);
        }
    }
}
