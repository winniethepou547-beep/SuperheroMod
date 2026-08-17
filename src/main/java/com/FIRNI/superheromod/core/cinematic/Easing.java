package com.FIRNI.superheromod.core.cinematic;

/** Kamera hareketleri icin yumusatma egrileri. */
public enum Easing {
    LINEAR {
        public float apply(float x) { return x; }
    },
    IN {
        public float apply(float x) { return x * x; }
    },
    OUT {
        public float apply(float x) { return 1f - (1f - x) * (1f - x); }
    },
    IN_OUT {
        public float apply(float x) {
            return x < 0.5f ? 2f * x * x : 1f - (float) Math.pow(-2f * x + 2f, 2) / 2f;
        }
    },
    /** Sert baslar, hizla yavaslar — vurus anlari icin. */
    SNAP {
        public float apply(float x) { return 1f - (float) Math.pow(1f - x, 4); }
    },
    /** Yavas baslar, sonra firlar — guc patlamalari icin. */
    SURGE {
        public float apply(float x) { return x * x * x; }
    };

    public abstract float apply(float x);
}
