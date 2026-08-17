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

    /**
     * Kalin isinlarin cikis agzi: TAM GOZ HIZASINDAN sacilan kirmizi toz
     * partikulleri. Isinin kendisi geometri olarak ciziliyor; bu partikuller
     * gozun onunde "namlu" hissi veren ikincil detay.
     *
     * Onceki renderOriginFlash cagrilari isini bakis yonunde one kaydirilmis
     * bir noktadan basliyordu; burasi kaydirmasiz, dogrudan gozun oldugu yer.
     *
     * @param widthMult isin kalinligi — sacilma yaricapi buna gore buyur
     */
    public static void renderEyeMuzzle(ServerLevel level, Vec3 eye, Vec3 look,
                                       float widthMult) {
        double spread = 0.09 * widthMult;
        // Partikuller hafifce bakis yonunde ilerlesin
        double drift = 0.02;

        level.sendParticles(DOT_WHITE,
                eye.x, eye.y, eye.z,
                2 + (int) widthMult, spread * 0.6, spread * 0.6, spread * 0.6, drift);
        level.sendParticles(DOT_BRIGHT_RED,
                eye.x + look.x * 0.08, eye.y + look.y * 0.08, eye.z + look.z * 0.08,
                3 + (int) (widthMult * 2), spread, spread, spread, drift);
        level.sendParticles(DOT_PINK,
                eye.x + look.x * 0.12, eye.y + look.y * 0.12, eye.z + look.z * 0.12,
                2 + (int) widthMult, spread * 1.3, spread * 1.3, spread * 1.3, drift);
        level.sendParticles(DOT_DEEP_RED,
                eye.x + look.x * 0.16, eye.y + look.y * 0.16, eye.z + look.z * 0.16,
                2 + (int) widthMult, spread * 1.6, spread * 1.6, spread * 1.6, drift * 1.5);
        level.sendParticles(ORIGIN_GLOW,
                eye.x, eye.y, eye.z,
                2, spread * 0.5, spread * 0.5, spread * 0.5, 0);
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
