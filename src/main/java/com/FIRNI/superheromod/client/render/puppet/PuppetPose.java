package com.FIRNI.superheromod.client.render.puppet;

/**
 * Sahne aktorunun tam pozu.
 *
 * Vanilla oyuncu 6 kati kutudur; onlari dondurunce omuzda ve kalcada dikis
 * gorunur ve karakter "havada duran kutular" gibi durur. Burada gövde 11
 * parcaya bolundu: bel, iki dirsek ve iki diz eklendi. Cömelme, kasilma,
 * geriye bukulme ve savrulma ancak bu eklemlerle inandirici oluyor.
 *
 * Aci birimi RADYAN.
 */
public final class PuppetPose {

    public static final int HEAD = 0;
    public static final int CHEST = 1;
    public static final int HIPS = 2;
    public static final int RIGHT_UPPER_ARM = 3;
    public static final int RIGHT_LOWER_ARM = 4;
    public static final int LEFT_UPPER_ARM = 5;
    public static final int LEFT_LOWER_ARM = 6;
    public static final int RIGHT_UPPER_LEG = 7;
    public static final int RIGHT_LOWER_LEG = 8;
    public static final int LEFT_UPPER_LEG = 9;
    public static final int LEFT_LOWER_LEG = 10;

    public static final int JOINTS = 11;

    public static final String[] NAMES = {
            "Kafa", "Gogus", "Kalca",
            "Sag Ust Kol", "Sag On Kol",
            "Sol Ust Kol", "Sol On Kol",
            "Sag Ust Bacak", "Sag Alt Bacak",
            "Sol Ust Bacak", "Sol Alt Bacak"
    };

    /** [eklem][eksen] radyan. */
    public final float[][] rot = new float[JOINTS][3];

    /** Govdenin tamaminin yerden yuksekligi (blok) — comelme icin. */
    public float crouch;

    /**
     * Govdenin kendi ekseninde donmesi (derece).
     * Firlatilirken vucudun donmeye baslamasi bununla yapiliyor.
     */
    public float bodyRoll;
    public float bodyPitch;

    public PuppetPose copy() {
        PuppetPose out = new PuppetPose();
        out.set(this);
        return out;
    }

    public void set(PuppetPose other) {
        for (int j = 0; j < JOINTS; j++) {
            System.arraycopy(other.rot[j], 0, this.rot[j], 0, 3);
        }
        this.crouch = other.crouch;
        this.bodyRoll = other.bodyRoll;
        this.bodyPitch = other.bodyPitch;
    }

    public void reset() {
        for (int j = 0; j < JOINTS; j++) {
            rot[j][0] = rot[j][1] = rot[j][2] = 0f;
        }
        crouch = 0f;
        bodyRoll = 0f;
        bodyPitch = 0f;
    }

    /**
     * Iki poz arasi gecis — TEPKI ZINCIRI destekli.
     *
     * Senaryonun en onemli kurali: butun model ayni anda hareket etmemeli.
     * Kuvvet vucuttan gecmeli (gogus -> omuz -> kafa -> kollar -> kalca ->
     * diz -> ayak). Bunu her eklem icin AYRI bir gecikme uygulayarak
     * yapiyoruz: gecikmesi buyuk olan eklem gecise daha gec basliyor.
     *
     * @param delays eklem basina 0..1 gecikme; 0 = hemen, 0.5 = yarida basla
     */
    public static void lerp(PuppetPose a, PuppetPose b, float t,
                            float[] delays, PuppetPose out) {
        for (int j = 0; j < JOINTS; j++) {
            float local = t;

            if (delays != null && delays.length > j && delays[j] > 0f) {
                float d = Math.min(0.9f, delays[j]);
                // Gecikme bitene kadar 0'da bekle, sonra kalan surede tamamla
                local = t <= d ? 0f : (t - d) / (1f - d);
            }

            // Yumusak giris/cikis — dogrusal gecis mekanik duruyor
            float e = local * local * (3f - 2f * local);

            for (int ax = 0; ax < 3; ax++) {
                out.rot[j][ax] = a.rot[j][ax] + (b.rot[j][ax] - a.rot[j][ax]) * e;
            }
        }

        float e = t * t * (3f - 2f * t);
        out.crouch = a.crouch + (b.crouch - a.crouch) * e;
        out.bodyRoll = a.bodyRoll + (b.bodyRoll - a.bodyRoll) * e;
        out.bodyPitch = a.bodyPitch + (b.bodyPitch - a.bodyPitch) * e;
    }

    // ------------------------------------------------------------------
    // Hazir tepki zincirleri
    // ------------------------------------------------------------------

    /** Lazeri fark ederken: kafa once, govde sonra. */
    public static float[] noticeChain() {
        float[] d = new float[JOINTS];
        d[HEAD] = 0.00f;
        d[CHEST] = 0.18f;
        d[HIPS] = 0.30f;
        d[RIGHT_UPPER_ARM] = 0.22f;
        d[LEFT_UPPER_ARM] = 0.22f;
        d[RIGHT_LOWER_ARM] = 0.32f;
        d[LEFT_LOWER_ARM] = 0.32f;
        d[RIGHT_UPPER_LEG] = 0.40f;
        d[LEFT_UPPER_LEG] = 0.40f;
        return d;
    }

    /** Lazer iterken: gogus once, ayak en son — kuvvet asagi dogru gecer. */
    public static float[] impactChain() {
        float[] d = new float[JOINTS];
        d[CHEST] = 0.00f;
        d[RIGHT_UPPER_ARM] = 0.08f;
        d[LEFT_UPPER_ARM] = 0.08f;
        d[HEAD] = 0.14f;
        d[RIGHT_LOWER_ARM] = 0.20f;
        d[LEFT_LOWER_ARM] = 0.20f;
        d[HIPS] = 0.28f;
        d[RIGHT_UPPER_LEG] = 0.38f;
        d[LEFT_UPPER_LEG] = 0.38f;
        d[RIGHT_LOWER_LEG] = 0.48f;
        d[LEFT_LOWER_LEG] = 0.48f;
        return d;
    }

    /** Firlatilirken: govde once, bacaklar en son suruklenir. */
    public static float[] launchChain() {
        float[] d = new float[JOINTS];
        d[CHEST] = 0.00f;
        d[HIPS] = 0.06f;
        d[RIGHT_UPPER_ARM] = 0.14f;
        d[LEFT_UPPER_ARM] = 0.14f;
        d[RIGHT_LOWER_ARM] = 0.24f;
        d[LEFT_LOWER_ARM] = 0.24f;
        d[HEAD] = 0.30f;
        d[RIGHT_UPPER_LEG] = 0.40f;
        d[LEFT_UPPER_LEG] = 0.40f;
        d[RIGHT_LOWER_LEG] = 0.52f;
        d[LEFT_LOWER_LEG] = 0.52f;
        return d;
    }
}
