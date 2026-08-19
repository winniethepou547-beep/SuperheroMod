package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bir sinematigin tam tanimi: cekim listesi + olay listesi.
 *
 * Karaktere ozel hicbir sey icermez; her kahraman kendi tanimini kurar ve
 * CinematicRegistry'e kaydeder. Motor bu tanimi oynatir.
 */
public final class CinematicDefinition {

    public final String id;
    public final List<Shot> shots;
    public final List<Beat> beats;
    public final int totalTicks;

    /** Sinematik boyunca sinema bantlari gosterilsin mi. */
    public final boolean letterbox;
    /** Sahne kurulurken hedefin sabitlenecegi yerel konum (null = oldugu yer). */
    public final Vec3 targetAnchor;

    /**
     * TABAN ATMOSFER — cekim kendi sisini yazmadiysa bu kullanilir.
     *
     * Her cekime tek tek sis yazmak zorunda kalinsaydi pratikte hicbir cekimde
     * yazilmazdi. Sinematigin geneli buradan gelir, cekimler sadece SAPMA
     * yaptiginda kendi degerini verir.
     */
    public final float baseFogNear;
    public final float baseFogFar;
    public final int baseFogColor;

    private CinematicDefinition(Builder b) {
        this.id = b.id;
        this.shots = Collections.unmodifiableList(b.shots);
        this.beats = Collections.unmodifiableList(sorted(b.beats));
        this.letterbox = b.letterbox;
        this.targetAnchor = b.targetAnchor;
        this.baseFogNear = b.baseFogNear;
        this.baseFogFar = b.baseFogFar;
        this.baseFogColor = b.baseFogColor;

        int sum = 0;
        for (Shot s : b.shots) sum += s.durationTicks;
        this.totalTicks = sum;
    }

    private static List<Beat> sorted(List<Beat> in) {
        List<Beat> copy = new ArrayList<>(in);
        copy.sort((x, y) -> Integer.compare(x.tick, y.tick));
        return copy;
    }

    /** Verilen zaman cizgisi tick'inde hangi cekimdeyiz? */
    public Cursor cursorAt(float timelineTick) {
        float acc = 0;
        for (int i = 0; i < shots.size(); i++) {
            Shot s = shots.get(i);
            if (timelineTick < acc + s.durationTicks) {
                return new Cursor(i, s, timelineTick - acc);
            }
            acc += s.durationTicks;
        }
        int last = shots.size() - 1;
        return new Cursor(last, shots.get(last), shots.get(last).durationTicks);
    }

    public record Cursor(int index, Shot shot, float localTick) {
        public float progress() {
            return Math.min(1f, localTick / Math.max(1, shot.durationTicks));
        }
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private final List<Shot> shots = new ArrayList<>();
        private final List<Beat> beats = new ArrayList<>();
        private boolean letterbox = true;
        private Vec3 targetAnchor;
        private float baseFogNear = Shot.NO_FOG;
        private float baseFogFar = Shot.NO_FOG;
        private int baseFogColor = Shot.NO_COLOR;

        private Builder(String id) {
            this.id = id;
        }

        public Builder shot(Shot s) {
            shots.add(s);
            return this;
        }

        public Builder beat(Beat b) {
            beats.add(b);
            return this;
        }

        public Builder beats(Beat... bs) {
            Collections.addAll(beats, bs);
            return this;
        }

        public Builder letterbox(boolean v) {
            this.letterbox = v;
            return this;
        }

        /** Hedefi sahne kurulurken bu yerel konuma yerlestir. */
        public Builder anchorTarget(Vec3 local) {
            this.targetAnchor = local;
            return this;
        }

        /**
         * Sinematigin geneline sinen atmosfer.
         *
         * @param near sisin basladigi mesafe (blok) — kuculdukce sahne kapanir
         * @param far  gorusun tamamen kapandigi mesafe (blok)
         * @param rgb  sis rengi 0xRRGGBB — sahnenin tonu
         */
        public Builder atmosphere(float near, float far, int rgb) {
            this.baseFogNear = near;
            this.baseFogFar = far;
            this.baseFogColor = rgb;
            return this;
        }

        public CinematicDefinition build() {
            if (shots.isEmpty()) {
                throw new IllegalStateException("Sinematik '" + id + "' cekim icermiyor");
            }
            return new CinematicDefinition(this);
        }
    }
}
