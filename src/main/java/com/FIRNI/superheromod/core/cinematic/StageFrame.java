package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;

/**
 * SAHNE UZAYI — sinematik motorunun kalbi.
 *
 * Mortal Kombat / Marvel Tokon mantigi: koreografi dunyaya gore degil,
 * DOVUSCULERE gore yazilir. Bir cekim "hedefin 3 blok solunda, 2 blok
 * yukarida" diye tanimlanir; sahne nerede oynarsa oynasin ayni gorunur.
 *
 * Yerel eksenler:
 *   forward = saldirandan hedefe dogru (yatay)
 *   right   = forward'in sagi
 *   up      = dunya yukarisi
 *
 * Yerel koordinat: (x = sag, y = yukari, z = ileri)
 */
public final class StageFrame {

    private final Vec3 origin;
    private final Vec3 forward;
    private final Vec3 right;
    private final Vec3 up;
    /** Saldiran-hedef arasi yatay mesafe; cekimler buna gore olceklenebilir. */
    private final double span;

    private StageFrame(Vec3 origin, Vec3 forward, Vec3 right, Vec3 up, double span) {
        this.origin = origin;
        this.forward = forward;
        this.right = right;
        this.up = up;
        this.span = span;
    }

    /**
     * Sahneyi iki aktore gore kurar. Origin saldiranin ayak hizasi,
     * forward hedefe dogru yatay yon.
     */
    public static StageFrame between(Vec3 attackerPos, Vec3 targetPos) {
        Vec3 delta = targetPos.subtract(attackerPos);
        Vec3 flat = new Vec3(delta.x, 0, delta.z);
        return of(attackerPos, flat, Math.max(1.5, flat.length()));
    }

    /**
     * Sahneyi dogrudan kurar. Istemci, sunucunun kurdugu ekseni birebir
     * yeniden uretmek icin bunu kullanir — span da agdan gelir, cunku
     * iki nokta arasindan yeniden hesaplamak yanlis olcek veriyordu.
     */
    public static StageFrame of(Vec3 origin, Vec3 forwardRaw, double span) {
        Vec3 flat = new Vec3(forwardRaw.x, 0, forwardRaw.z);
        Vec3 forward = flat.lengthSqr() < 1.0E-4
                ? new Vec3(0, 0, 1)
                : flat.normalize();

        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);

        return new StageFrame(origin, forward, right, up, Math.max(1.5, span));
    }

    /** Yerel (sag, yukari, ileri) -> dunya konumu. */
    public Vec3 toWorld(Vec3 local) {
        return origin
                .add(right.scale(local.x))
                .add(up.scale(local.y))
                .add(forward.scale(local.z));
    }

    /** Yerel yonu dunya yonune cevirir (konum kaydirmasi yok). */
    public Vec3 dirToWorld(Vec3 localDir) {
        return right.scale(localDir.x)
                .add(up.scale(localDir.y))
                .add(forward.scale(localDir.z));
    }

    /**
     * Sahne mesafesine gore olceklenmis yerel konum: z bileseni 0..1
     * arasinda saldirandan hedefe oran olarak yorumlanir.
     */
    public Vec3 toWorldSpan(Vec3 local) {
        return origin
                .add(right.scale(local.x))
                .add(up.scale(local.y))
                .add(forward.scale(local.z * span));
    }

    public Vec3 origin() { return origin; }
    public Vec3 forward() { return forward; }
    public Vec3 right() { return right; }
    public Vec3 up() { return up; }
    public double span() { return span; }

    /** Yerel yonun dunya yaw acisi (derece). */
    public float yawOf(Vec3 localDir) {
        Vec3 w = dirToWorld(localDir);
        return (float) Math.toDegrees(Math.atan2(-w.x, w.z));
    }
}
