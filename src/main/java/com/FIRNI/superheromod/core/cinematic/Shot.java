package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;

/**
 * Tek bir kamera cekimi. Tum konumlar SAHNE UZAYINDA (yerel) yazilir.
 *
 * Kamera bir cekim boyunca start -> end arasinda hareket eder; hangi hizla
 * hareket edecegi Easing ile belirlenir.
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

    /** Sarsinti siddeti (0 = yok). Cekim boyunca shakeCurve ile olceklenir. */
    public final float shakeStart;
    public final float shakeEnd;

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

        public Shot build() { return new Shot(this); }
    }
}
