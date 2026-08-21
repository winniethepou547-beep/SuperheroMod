package com.FIRNI.superheromod.heroes.sandman;

/**
 * SAND COLOSSUS'UN OLUSMA EGRISI.
 *
 * Kum askerlerinde oldugu gibi dev de bir anda belirmez, asama asama kurulur.
 * Fark su: askerde parcalar tek tek aciliyordu (ayak, bacak, govde...), devde
 * ise bacak YOK — bu yuzden asamalar yerden yukselen bir KUTLE olarak
 * kurgulandi.
 *
 * Egri bilerek duz degil: her asamada hizla buyur, sonra KISA BIR AN DURUR.
 * Duraklamalar olmazsa buyume tek bir yumusak sisme gibi gorunur ve
 * "parca parca olusuyor" hissi kaybolur.
 */
public final class ColossusForm {

    /** Olusmanin toplam suresi (tick). */
    public static final int FORM_TICKS = 40;

    /** Oyuncu boyu — olcek bunun uzerine hesaplaniyor. */
    private static final float PLAYER_HEIGHT = 1.8f;

    /** Tam olusmus devin olcegi. */
    public static final float FULL_SCALE = ColossusCrystal.COLOSSUS_HEIGHT / PLAYER_HEIGHT;

    private ColossusForm() {}

    /**
     * Olusma asamalari (progress 0..1):
     *   0.00-0.15  zeminde kum toplanir, govde henuz normal boyutta
     *   0.15-0.35  ALT KUM KUTLESI yukselir
     *   0.35-0.45  bekleme
     *   0.45-0.65  GOVDE olusur
     *   0.65-0.72  bekleme
     *   0.72-0.88  DEV KOLLAR olusur
     *   0.88-1.00  KAFA ve KRISTALLER
     *
     * @return 0..1 arasi buyume orani
     */
    public static float growth(float progress) {
        if (progress <= 0.15f) return 0f;
        if (progress < 0.35f) return ramp(progress, 0.15f, 0.35f, 0.00f, 0.35f);
        if (progress < 0.45f) return 0.35f;
        if (progress < 0.65f) return ramp(progress, 0.45f, 0.65f, 0.35f, 0.72f);
        if (progress < 0.72f) return 0.72f;
        if (progress < 0.88f) return ramp(progress, 0.72f, 0.88f, 0.72f, 0.93f);
        return ramp(progress, 0.88f, 1.00f, 0.93f, 1.00f);
    }

    /** Olusma sirasindaki gorsel olcek — 1.0 (normal oyuncu) ile FULL_SCALE arasi. */
    public static float scaleFor(float progress) {
        return 1.0f + (FULL_SCALE - 1.0f) * growth(progress);
    }

    /** Kristaller ancak son asamada ortaya cikar; oncesinde hedeflenemez. */
    public static boolean crystalsActive(float progress) {
        return progress >= 0.88f;
    }

    /** Asama numarasi — parcacik efektleri buna gore degisiyor. */
    public static int stage(float progress) {
        if (progress < 0.15f) return 0;   // kum toplaniyor
        if (progress < 0.45f) return 1;   // alt kutle
        if (progress < 0.72f) return 2;   // govde
        if (progress < 0.88f) return 3;   // kollar
        return 4;                          // kafa + kristaller
    }

    private static float ramp(float x, float from, float to, float outFrom, float outTo) {
        float t = (x - from) / (to - from);
        // Yumusak giris/cikis — asama iceriside bile mekanik durmasin
        t = t * t * (3f - 2f * t);
        return outFrom + (outTo - outFrom) * t;
    }
}
