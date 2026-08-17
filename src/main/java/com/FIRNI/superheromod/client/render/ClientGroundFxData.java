package com.FIRNI.superheromod.client.render;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Yer efektleri: lazerden TAMAMEN ayri. Kendi geometrisi ve renkleri var —
 * koyu volkanik kaya + icten sizan kizil catlak. Lazer katmanlarini kullanmaz.
 */
public class ClientGroundFxData {

    public enum Kind {
        /** Ultinin gidecegi alani gosteren yer konisi. */
        CONE,
        /** Patlamaya hazir volkanik kaya yumrusu. */
        MAGMA_ROCK,
        /** Patlamada yanlardan firlayan kaya parcasi. */
        SHARD
    }

    public static final class Fx {
        public final Kind kind;
        public final Vec3 pos;
        /** CONE icin yon; SHARD icin firlama yonu. */
        public final Vec3 dir;
        public final float size;
        /** 0..1 — kaya icin "patlamaya hazirlik" seviyesi. */
        public float charge;
        public int ticksRemaining;
        public final int maxTicks;
        public final long seed;

        public Fx(Kind kind, Vec3 pos, Vec3 dir, float size, int ticks, long seed) {
            this.kind = kind;
            this.pos = pos;
            this.dir = dir;
            this.size = size;
            this.ticksRemaining = ticks;
            this.maxTicks = ticks;
            this.seed = seed;
        }

        public float life() {
            return maxTicks <= 0 ? 0 : (float) ticksRemaining / maxTicks;
        }
    }

    private static final List<Fx> effects = Collections.synchronizedList(new ArrayList<>());

    /** Ayni anda cizilecek azami efekt — performans siniri. */
    private static final int MAX_EFFECTS = 220;

    public static void add(Fx fx) {
        synchronized (effects) {
            if (effects.size() >= MAX_EFFECTS) return;
            effects.add(fx);
        }
    }

    public static List<Fx> get() {
        return effects;
    }

    public static void tick() {
        synchronized (effects) {
            effects.removeIf(fx -> --fx.ticksRemaining <= 0);
        }
    }

    public static void clear() {
        synchronized (effects) {
            effects.clear();
        }
    }
}
