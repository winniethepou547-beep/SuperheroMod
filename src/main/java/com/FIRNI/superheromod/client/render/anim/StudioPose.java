package com.FIRNI.superheromod.client.render.anim;

/**
 * Tek bir poz: modelin her parcasi icin x/y/z donusu (radyan) + sag dirsek
 * bukulmesi.
 *
 * Dirsek ayri tutuluyor cunku vanilla modelde dirsek eklemi YOK; kol iki
 * parcaya BendableArm/BendableArmLayer tarafindan bolunuyor ve bukulme
 * miktari oradan okunuyor.
 */
public final class StudioPose {

    public static final int HEAD = 0;
    public static final int BODY = 1;
    public static final int RIGHT_ARM = 2;
    public static final int LEFT_ARM = 3;
    public static final int RIGHT_LEG = 4;
    public static final int LEFT_LEG = 5;

    public static final int PARTS = 6;

    public static final String[] NAMES =
            {"Kafa", "Govde", "Sag Kol", "Sol Kol", "Sag Bacak", "Sol Bacak"};

    /**
     * Satir formatindaki deger sayisi:
     * sure + 6 parca * 3 donus + 6 parca * 3 oteleme + dirsek.
     */
    public static final int ROW_SIZE = 1 + PARTS * 3 + PARTS * 3 + 1;

    /** [parca][eksen] radyan. */
    public final float[][] rot = new float[PARTS][3];

    /** [parca][eksen] model pikseli — parcanin varsayilan yerine gore oteleme. */
    public final float[][] pos = new float[PARTS][3];

    /** Sag dirsek bukulmesi (radyan, 0 = duz kol). */
    public float elbow;

    public StudioPose copy() {
        StudioPose out = new StudioPose();
        out.set(this);
        return out;
    }

    public void set(StudioPose other) {
        for (int p = 0; p < PARTS; p++) {
            System.arraycopy(other.rot[p], 0, this.rot[p], 0, 3);
            System.arraycopy(other.pos[p], 0, this.pos[p], 0, 3);
        }
        this.elbow = other.elbow;
    }

    public void reset() {
        for (int p = 0; p < PARTS; p++) {
            for (int ax = 0; ax < 3; ax++) {
                rot[p][ax] = 0f;
                pos[p][ax] = 0f;
            }
        }
        elbow = 0f;
    }

    /** Iki poz arasi gecis; {@code t} zaten yumusatilmis olarak gelir. */
    public static void lerp(StudioPose a, StudioPose b, float t, StudioPose out) {
        for (int p = 0; p < PARTS; p++) {
            for (int ax = 0; ax < 3; ax++) {
                out.rot[p][ax] = a.rot[p][ax] + (b.rot[p][ax] - a.rot[p][ax]) * t;
                out.pos[p][ax] = a.pos[p][ax] + (b.pos[p][ax] - a.pos[p][ax]) * t;
            }
        }
        out.elbow = a.elbow + (b.elbow - a.elbow) * t;
    }

    /** Duz satir: [sure, ...donusler, ...otelemeler, dirsek]. */
    public float[] toRow(int holdTicks) {
        float[] row = new float[ROW_SIZE];
        row[0] = holdTicks;
        int i = 1;
        for (int p = 0; p < PARTS; p++) {
            for (int ax = 0; ax < 3; ax++) row[i++] = rot[p][ax];
        }
        for (int p = 0; p < PARTS; p++) {
            for (int ax = 0; ax < 3; ax++) row[i++] = pos[p][ax];
        }
        row[i] = elbow;
        return row;
    }

    /** {@link #toRow} ciktisini geri okur; sure donus degeri olarak verilir. */
    public int fromRow(float[] row) {
        if (row.length < ROW_SIZE) return 1;
        int i = 1;
        for (int p = 0; p < PARTS; p++) {
            for (int ax = 0; ax < 3; ax++) rot[p][ax] = row[i++];
        }
        for (int p = 0; p < PARTS; p++) {
            for (int ax = 0; ax < 3; ax++) pos[p][ax] = row[i++];
        }
        elbow = row[i];
        return Math.max(1, (int) row[0]);
    }
}
