package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;

/**
 * Tek bir kamera cekimi. Tum konumlar SAHNE UZAYINDA (yerel) yazilir.
 *
 * Bir cekim sadece kamera konumu degil, o anin TUM GORSEL DURUMUDUR:
 * kadraj, FOV, yatirma, sarsinti, nefes ve ATMOSFER (sis mesafesi/rengi).
 * Atmosfer cekimin ozelligi olarak tutuluyor cunku sonradan eklenirse her
 * cekim icin elle ayarlamak gerekir ve pratikte hic kullanilmaz.
 */
public final class Shot {

    public enum Transition {
        /** Sert kesme — onceki cekimden bagimsiz, aninda yeni konum. */
        CUT,
        /** Onceki cekimden yumusak gecis. */
        SMOOTH
    }

    /** Kameranin neye kilitlenecegi. */
    public enum LookTarget {
        /** Sabit yerel noktaya bak. */
        FIXED,
        /** Saldirani takip et. */
        ATTACKER,
        /** Hedefi takip et. */
        TARGET,
        /** Ikisinin ortasina bak. */
        MIDPOINT
    }

    /** "Bu cekim sise dokunmuyor" isareti. */
    public static final float NO_FOG = Float.NaN;
    /** "Bu cekim sis rengine dokunmuyor" isareti. */
    public static final int NO_COLOR = -1;

    public final int durationTicks;
    public final Transition transition;
    public final Easing easing;

    /** Kamera baslangic/bitis konumu (yerel). */
    public final Vec3 fromPos;
    public final Vec3 toPos;

    public final LookTarget lookTarget;
    /** LookTarget.FIXED icin yerel bakis noktasi; digerlerinde ofset. */
    public final Vec3 lookOffset;

    public final float fovStart;
    public final float fovEnd;

    /** Sarsinti siddeti (0 = yok). Darbe icin — sert ve hizli. */
    public final float shakeStart;
    public final float shakeEnd;

    /**
     * Kamera nefesi — sarsintidan FARKLI. Cok yavas, cok kucuk, surekli.
     * Sahneyi "hesaplanmis" olmaktan cikarip "cekilmis" yapan sey bu.
     */
    public final float breath;

    /** Kamera yatirma (derece). Oyun kamerasi asla yatmaz; sinematik yatar. */
    public final float rollStart;
    public final float rollEnd;

    // --- Atmosfer ---
    /** Sisin baslangic mesafesi (blok). NO_FOG ise dokunulmaz. */
    public final float fogNearStart;
    public final float fogNearEnd;
    /** Sisin bitis mesafesi (blok). */
    public final float fogFarStart;
    public final float fogFarEnd;
    /** Sis rengi 0xRRGGBB. NO_COLOR ise dokunulmaz. */
    public final int fogColor;

    private Shot(Builder b) {
        this.durationTicks = b.durationTicks;
        this.transition = b.transition;
        this.easing = b.easing;
        this.fromPos = b.fromPos;
        this.toPos = b.toPos == null ? b.fromPos : b.toPos;
        this.lookTarget = b.lookTarget;
        this.lookOffset = b.lookOffset;
        this.fovStart = b.fovStart;
        this.fovEnd = Float.isNaN(b.fovEnd) ? b.fovStart : b.fovEnd;
        this.shakeStart = b.shakeStart;
        this.shakeEnd = Float.isNaN(b.shakeEnd) ? b.shakeStart : b.shakeEnd;
        this.breath = b.breath;
        this.rollStart = b.rollStart;
        this.rollEnd = Float.isNaN(b.rollEnd) ? b.rollStart : b.rollEnd;
        this.fogNearStart = b.fogNearStart;
        this.fogNearEnd = Float.isNaN(b.fogNearEnd) ? b.fogNearStart : b.fogNearEnd;
        this.fogFarStart = b.fogFarStart;
        this.fogFarEnd = Float.isNaN(b.fogFarEnd) ? b.fogFarStart : b.fogFarEnd;
        this.fogColor = b.fogColor;
    }

    public boolean hasFog() {
        return !Float.isNaN(fogNearStart) && !Float.isNaN(fogFarStart);
    }

    public static Builder of(int durationTicks) {
        return new Builder(durationTicks);
    }

    public static final class Builder {
        private final int durationTicks;
        private Transition transition = Transition.CUT;
        private Easing easing = Easing.IN_OUT;
        private Vec3 fromPos = Vec3.ZERO;
        private Vec3 toPos;
        private LookTarget lookTarget = LookTarget.TARGET;
        private Vec3 lookOffset = new Vec3(0, 1.0, 0);
        private float fovStart = 70f;
        private float fovEnd = Float.NaN;
        private float shakeStart = 0f;
        private float shakeEnd = Float.NaN;
        private float breath = 0.35f;          // varsayilan olarak hep acik
        private float rollStart = 0f;
        private float rollEnd = Float.NaN;
        private float fogNearStart = NO_FOG;
        private float fogNearEnd = Float.NaN;
        private float fogFarStart = NO_FOG;
        private float fogFarEnd = Float.NaN;
        private int fogColor = NO_COLOR;
        private double pushInAmount = 0;

        private Builder(int durationTicks) {
            this.durationTicks = Math.max(1, durationTicks);
        }

        public Builder cut() { this.transition = Transition.CUT; return this; }
        public Builder smooth() { this.transition = Transition.SMOOTH; return this; }
        public Builder ease(Easing e) { this.easing = e; return this; }

        /** Sabit kamera. */
        public Builder at(double x, double y, double z) {
            this.fromPos = new Vec3(x, y, z);
            return this;
        }

        /** Hareketli kamera: from -> to. */
        public Builder move(Vec3 from, Vec3 to) {
            this.fromPos = from;
            this.toPos = to;
            return this;
        }

        /**
         * Cekim boyunca bakis noktasina dogru YAVASCA yaklas.
         *
         * Sabit kadraj olu gorunur — beyin onu dondurulmus sanar. Kamera fark
         * edilir etmez ilerledigi surece sahne canli kalir ve gerilim birikir.
         *
         * @param fraction bakis noktasina olan mesafenin ne kadari kapatilacak
         *                 (0.10 - 0.20 arasi dogal durur, fazlasi hucum olur)
         */
        public Builder pushIn(double fraction) {
            this.pushInAmount = fraction;
            // Push-in'de IN_OUT kamerayi sonda durduruyormus gibi gosterir;
            // surunerek yaklasma icin dogrusala yakin egri dogru olan
            if (this.easing == Easing.IN_OUT) this.easing = Easing.OUT;
            return this;
        }

        public Builder lookAtTarget(double yOffset) {
            this.lookTarget = LookTarget.TARGET;
            this.lookOffset = new Vec3(0, yOffset, 0);
            return this;
        }

        public Builder lookAtAttacker(double yOffset) {
            this.lookTarget = LookTarget.ATTACKER;
            this.lookOffset = new Vec3(0, yOffset, 0);
            return this;
        }

        public Builder lookAtMidpoint(double yOffset) {
            this.lookTarget = LookTarget.MIDPOINT;
            this.lookOffset = new Vec3(0, yOffset, 0);
            return this;
        }

        public Builder lookAtFixed(Vec3 localPoint) {
            this.lookTarget = LookTarget.FIXED;
            this.lookOffset = localPoint;
            return this;
        }

        public Builder fov(float f) { this.fovStart = f; return this; }
        public Builder fov(float from, float to) {
            this.fovStart = from;
            this.fovEnd = to;
            return this;
        }

        public Builder shake(float s) { this.shakeStart = s; return this; }
        public Builder shake(float from, float to) {
            this.shakeStart = from;
            this.shakeEnd = to;
            return this;
        }

        /** Elde tutulmus his. 0 = tam sabit (olu), 1 = belirgin salinim. */
        public Builder breath(float amount) { this.breath = amount; return this; }

        public Builder roll(float degrees) { this.rollStart = degrees; return this; }
        public Builder roll(float from, float to) {
            this.rollStart = from;
            this.rollEnd = to;
            return this;
        }

        /**
         * Sis. Yakina cekildikce arka plan erir ve ozne one cikar — ekrani
         * siyahla ortmeden odak kurmanin yolu bu.
         *
         * @param near sisin basladigi mesafe (blok)
         * @param far  gorusun tamamen kapandigi mesafe (blok)
         */
        public Builder fog(float near, float far) {
            this.fogNearStart = near;
            this.fogFarStart = far;
            return this;
        }

        /** Cekim boyunca degisen sis — gerilim sisle kurulabilir. */
        public Builder fog(float nearFrom, float farFrom, float nearTo, float farTo) {
            this.fogNearStart = nearFrom;
            this.fogFarStart = farFrom;
            this.fogNearEnd = nearTo;
            this.fogFarEnd = farTo;
            return this;
        }

        /** Sis rengi 0xRRGGBB — sahnenin tonunu bu belirler. */
        public Builder fogColor(int rgb) { this.fogColor = rgb; return this; }

        public Shot build() {
            resolvePushIn();
            return new Shot(this);
        }

        /**
         * Push-in'i somut bir bitis konumuna cevirir.
         *
         * Bakis noktasinin YEREL karsiligi biliniyor: sahne uzayinda saldiran
         * z=0'da, hedef z=1'de durur. Bu sayede dunya konumunu bilmeden de
         * "hedefe dogru yaklas" hesaplanabiliyor.
         */
        private void resolvePushIn() {
            if (pushInAmount <= 0) return;

            Vec3 lookPoint = switch (lookTarget) {
                case ATTACKER -> new Vec3(0, lookOffset.y, 0);
                case TARGET -> new Vec3(0, lookOffset.y, 1);
                case MIDPOINT -> new Vec3(0, lookOffset.y, 0.5);
                case FIXED -> lookOffset;
            };

            Vec3 base = toPos == null ? fromPos : toPos;
            Vec3 delta = lookPoint.subtract(base);
            if (delta.lengthSqr() < 1.0E-6) return;

            this.toPos = base.add(delta.scale(pushInAmount));
        }
    }
}
