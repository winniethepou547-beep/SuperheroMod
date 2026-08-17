package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;

/**
 * Zaman cizgisindeki tek bir olay. Sunucu bunlari tam tick'inde isler.
 *
 * Kamera (Shot) istemcide, olaylar (Beat) sunucuda yasar — boylece gorsel
 * kurgu ile oyun mantigi ayrilir.
 */
public final class Beat {

    public enum Action {
        /** Isini baslat/genislet. param1 = genislik. */
        BEAM,
        /** Isini kes. */
        BEAM_OFF,
        /** Ses cal. text = ses adi, param1 = ses, param2 = perde. */
        SOUND,
        /** Hedefi sahne uzayinda belirtilen yerel noktaya tasi (yumusak). */
        ACTOR_MOVE_TARGET,
        /** Hedefi yay cizerek firlat: from -> to, param1 = yay yuksekligi. */
        LAUNCH_TARGET,
        /** Aktorleri yerinde dondur. */
        FREEZE,
        /** Hasar uygula. param1 = maks canin yuzdesi. */
        DAMAGE,
        /** Ekran karartma seviyesi. param1 = 0..1. */
        DARKNESS,
        /** Yavas cekim. param1 = zaman olcegi (1 = normal). */
        TIME_SCALE,
        /** Toz/duman gibi ikincil efekt. text = tur. */
        FX,
        /** Aktorun bakis acisini ayarla. param1 = pitch. */
        LOOK_PITCH
    }

    public final int tick;
    public final Action action;
    public final float param1;
    public final float param2;
    public final Vec3 localA;
    public final Vec3 localB;
    public final String text;

    private Beat(int tick, Action action, float p1, float p2,
                 Vec3 a, Vec3 b, String text) {
        this.tick = tick;
        this.action = action;
        this.param1 = p1;
        this.param2 = p2;
        this.localA = a;
        this.localB = b;
        this.text = text;
    }

    public static Beat beam(int tick, float width) {
        return new Beat(tick, Action.BEAM, width, 0, null, null, null);
    }

    public static Beat beamOff(int tick) {
        return new Beat(tick, Action.BEAM_OFF, 0, 0, null, null, null);
    }

    public static Beat sound(int tick, String id, float volume, float pitch) {
        return new Beat(tick, Action.SOUND, volume, pitch, null, null, id);
    }

    public static Beat moveTarget(int tick, Vec3 local, float speed) {
        return new Beat(tick, Action.ACTOR_MOVE_TARGET, speed, 0, local, null, null);
    }

    public static Beat launchTarget(int tick, Vec3 from, Vec3 to, float arc, int overTicks) {
        return new Beat(tick, Action.LAUNCH_TARGET, arc, overTicks, from, to, null);
    }

    public static Beat freeze(int tick) {
        return new Beat(tick, Action.FREEZE, 0, 0, null, null, null);
    }

    public static Beat damage(int tick, float healthPercent) {
        return new Beat(tick, Action.DAMAGE, healthPercent, 0, null, null, null);
    }

    public static Beat darkness(int tick, float amount) {
        return new Beat(tick, Action.DARKNESS, amount, 0, null, null, null);
    }

    public static Beat timeScale(int tick, float scale) {
        return new Beat(tick, Action.TIME_SCALE, scale, 0, null, null, null);
    }

    public static Beat fx(int tick, String kind, Vec3 local, float size) {
        return new Beat(tick, Action.FX, size, 0, local, null, kind);
    }

    public static Beat lookPitch(int tick, float pitch) {
        return new Beat(tick, Action.LOOK_PITCH, pitch, 0, null, null, null);
    }
}
