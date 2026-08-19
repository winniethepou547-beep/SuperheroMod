package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.cinematic.*;
import net.minecraft.world.phys.Vec3;

/**
 * MAXIMUM POWER — Cyclops'un sinematik bitirme hamlesi (623 tick / ~31 sn).
 *
 * Tum konumlar SAHNE UZAYINDA yazilir:
 *   x = sag (+ saga)
 *   y = yukari
 *   z = ileri; 0 = Cyclops, 1 = hedefin baslangic yeri
 * Bu yuzden koreografi dunyadaki konumdan bagimsizdir.
 *
 * TEMPO — senaryonun en onemli kurali:
 * Son saldiriya kadar kamera SAKIN kalir. Onceki surumde her cekim 4-5 tick'ti
 * ve gecisler arka arkaya geldigi icin hicbir an okunmuyordu. Simdi acilista
 * cekimler 25-55 tick; sadece son bolumde (firlatma) 10-14 tick'e dusuyor.
 * Boylece hizlanma anlam kazaniyor.
 *
 * ANLATININ TAMAMI SU KARSITLIK UZERINE KURULU:
 *   ILK ISIN     -> hedef geri kayar ama DIRENIR, hatta ONE ADIM ATAR
 *   MAXIMUM POWER-> direnis aninda kirilir, hedef yerden kesilip savrulur
 */
public final class MaximumPowerCinematic {

    public static final String ID = "cyclops:maximum_power";

    private static final Vec3 TARGET_ANCHOR = new Vec3(0, 0, 1.0);

    // Isin genislikleri — fark gorsel olarak okunmali
    private static final float BEAM_THIN = 0.5f;
    private static final float BEAM_NORMAL = 1.2f;
    private static final float BEAM_MAX = 5.0f;

    // Sis tonlari
    private static final int FOG_DARK = 0x0A0A0C;      // acilis: neredeyse siyah
    private static final int FOG_BLOOD = 0x3A1216;     // isin yanarken
    private static final int FOG_HOT = 0x5A1C14;       // maximum power
    private static final int FOG_BLAST = 0x8A3A18;     // patlama

    private MaximumPowerCinematic() {}

    public static void register() {
        CinematicRegistry.register(build());
    }

    private static CinematicDefinition build() {
        return CinematicDefinition.builder(ID)
                .letterbox(true)
                .anchorTarget(TARGET_ANCHOR)

                // Taban atmosfer: yogun, koyu gri-siyah sis. Uzakta hicbir
                // cevre secilmez — devasa bir bosluk hissi.
                .atmosphere(2.5f, 22.0f, FOG_DARK)

                // ==========================================================
                // SAHNE 1 — TAM KARANLIK / YALNIZLIK          (tick 0-44)
                // ==========================================================
                // Kamera karsidan DEGIL, gogus hizasinda ve biraz asagida.
                // Cok yavas yatay drift — "sinematik basladi" diye bagirmiyor.
                .shot(Shot.of(45).cut().ease(Easing.LINEAR)
                        .move(new Vec3(2.30, 1.35, 1.85), new Vec3(1.95, 1.35, 1.80))
                        .lookAtTarget(1.15)
                        .fov(52f)
                        .breath(0.5f)
                        .build())

                // ==========================================================
                // SAHNE 2 — ETRAFINI KONTROL ETME            (tick 45-99)
                // ==========================================================
                // Kamera karakteri TAKIP ETMEZ. Sabit dururken karakterin
                // kadraj icinde donmesi ortami daha tehditkar hissettiriyor.
                .shot(Shot.of(30).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(1.95, 1.35, 1.80), new Vec3(1.70, 1.38, 1.70))
                        .lookAtTarget(1.25)
                        .fov(50f)
                        .breath(0.5f)
                        .build())

                // Yavas push-in: bel ustunden gogus+kafa kadrajina
                .shot(Shot.of(25).smooth().ease(Easing.LINEAR)
                        .at(1.70, 1.42, 1.62)
                        .lookAtTarget(1.40)
                        .fov(46f)
                        .pushIn(0.16)
                        .breath(0.6f)
                        .build())

                // ==========================================================
                // SAHNE 3 — ILK OPTIC BLAST                 (tick 100-125)
                // ==========================================================
                // Isin sol-arkadan sag-one caprazlar. Bloom sisi aydinlatir.
                .shot(Shot.of(14).cut().ease(Easing.SNAP)
                        .at(1.55, 1.45, 1.55)
                        .lookAtTarget(1.35)
                        .fov(44f, 58f)
                        .shake(0.7f, 0.15f)
                        .roll(-5f, -1f)
                        .fog(1.5f, 45.0f, 3.0f, 30.0f)
                        .fogColor(FOG_BLOOD)
                        .build())

                // Hafif orbit — izleyici isinin gercekten uzaktan geldigini anlar
                .shot(Shot.of(12).smooth().ease(Easing.OUT)
                        .move(new Vec3(1.30, 1.50, 1.90), new Vec3(0.55, 1.55, 2.15))
                        .lookAtTarget(1.25)
                        .fov(56f)
                        .breath(0.4f)
                        .build())

                // ==========================================================
                // SAHNE 4 — SESSIZLIK VE GERILIM            (tick 126-180)
                // ==========================================================
                // Senaryo: "bu bolum ozellikle uzun tutulmali"
                // Omuz ustu plan — onumuzde sadece sis, hicbir sey yok.
                .shot(Shot.of(35).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(0.45, 1.62, 1.95), new Vec3(0.42, 1.60, 1.72))
                        .lookAtFixed(new Vec3(0, 1.35, 0.10))
                        .fov(50f)
                        .breath(0.55f)
                        .fog(2.0f, 16.0f)
                        .fogColor(FOG_DARK)
                        .build())

                // Uzakta kucucuk kirmizi bir isik belirir — ve KAYBOLMAZ
                .shot(Shot.of(20).smooth().ease(Easing.LINEAR)
                        .at(0.42, 1.58, 1.62)
                        .lookAtFixed(new Vec3(0, 1.55, 0.05))
                        .fov(44f)
                        .pushIn(0.10)
                        .breath(0.5f)
                        .fog(2.0f, 20.0f, 2.0f, 26.0f)
                        .build())

                // ==========================================================
                // SAHNE 5 — CYCLOPS'UN OPTIC GLOW'U         (tick 181-220)
                // ==========================================================
                // Gozluk -> goz cevresi -> cene -> omuz sirasiyla karanliktan
                // ayrilir. Cyclops hareket etmez, sadece durur.
                .shot(Shot.of(25).smooth().ease(Easing.LINEAR)
                        .at(0.40, 1.60, 1.35)
                        .lookAtAttacker(1.60)
                        .fov(40f)
                        .pushIn(0.14)
                        .breath(0.45f)
                        .fogColor(FOG_BLOOD)
                        .build())

                // Gozluge asiri yakin — kadraj yatik, kamera surunuyor
                .shot(Shot.of(15).smooth().ease(Easing.LINEAR)
                        .at(0.10, 1.64, 0.55)
                        .lookAtAttacker(1.64)
                        .fov(32f)
                        .pushIn(0.16)
                        .roll(4f, 7f)
                        .breath(0.4f)
                        .fog(1.2f, 10.0f)
                        .build())

                // ==========================================================
                // SAHNE 6 — DEVAMLI KALIN OPTIC BEAM        (tick 221-242)
                // ==========================================================
                .shot(Shot.of(22).cut().ease(Easing.SURGE)
                        .move(new Vec3(3.90, 1.55, 0.55), new Vec3(3.60, 1.50, 0.70))
                        .lookAtMidpoint(1.30)
                        .fov(72f)
                        .shake(0.45f, 0.18f)
                        .roll(-7f, -2f)
                        .fog(2.0f, 40.0f)
                        .fogColor(FOG_BLOOD)
                        .build())

                // ==========================================================
                // SAHNE 7 — ILK BUYUK CARPISMA              (tick 243-276)
                // ==========================================================
                // TAM 90 derece yan profil. Kamera hedefle ayni hizda hareket
                // ETMEZ — karakter geri kayarken kamera sabit kalir, boylece
                // gercekten uzaklastigi hissedilir.
                .shot(Shot.of(34).cut().ease(Easing.LINEAR)
                        .at(4.20, 1.05, 1.10)
                        .lookAtTarget(1.00)
                        .fov(62f)
                        .shake(0.30f, 0.12f)
                        .breath(0.5f)
                        .build())

                // ==========================================================
                // SAHNE 8 — DIRENIS                         (tick 277-316)
                // ==========================================================
                // 3/4 yan. Isin kadraji capraz kapliyor, kenarlar parliyor.
                .shot(Shot.of(40).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(3.10, 0.95, 1.35), new Vec3(2.70, 0.90, 1.30))
                        .lookAtTarget(0.95)
                        .fov(58f)
                        .shake(0.14f)
                        .roll(2f, 4f)
                        .breath(0.5f)
                        .build())

                // ==========================================================
                // SAHNE 9 — KARAKTERIN ILERLEMESI           (tick 317-360)
                // ==========================================================
                // Karakter kameraya dogru yururken kamera GERI cekilir.
                .shot(Shot.of(44).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(0.90, 1.15, 1.95), new Vec3(1.15, 1.20, 2.55))
                        .lookAtTarget(1.05)
                        .fov(60f)
                        .shake(0.10f)
                        .breath(0.55f)
                        .build())

                // ==========================================================
                // SAHNE 10 — CYCLOPS'A DONUS                (tick 361-390)
                // ==========================================================
                // Cyclops + gozluk + beam + hedef ayni kadrajda okunuyor.
                .shot(Shot.of(30).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(-1.85, 1.55, 0.05), new Vec3(-1.60, 1.52, 0.20))
                        .lookAtAttacker(1.52)
                        .fov(54f)
                        .breath(0.45f)
                        .fogColor(FOG_BLOOD)
                        .build())

                // ==========================================================
                // SAHNE 11 — GOZLUGE GIDEN EL               (tick 391-430)
                // ==========================================================
                // Bel ustu 3/4 frontal. Gozlugun glow'u parmaklara vuruyor.
                .shot(Shot.of(40).smooth().ease(Easing.LINEAR)
                        .at(-1.15, 1.60, 0.62)
                        .lookAtAttacker(1.58)
                        .fov(42f)
                        .pushIn(0.12)
                        .breath(0.4f)
                        .fog(1.5f, 14.0f)
                        .build())

                // ==========================================================
                // SAHNE 12 — MAXIMUM POWER                  (tick 431-454)
                // ==========================================================
                // Isin kesilir, sessizlik, sonra gozluk cikar ve kamera
                // yuze dogru HIZLI ama kontrollu push-in yapar.
                .shot(Shot.of(24).smooth().ease(Easing.IN)
                        .move(new Vec3(-0.55, 1.66, 0.55), new Vec3(-0.16, 1.66, 0.26))
                        .lookAtAttacker(1.66)
                        .fov(38f, 26f)
                        .roll(0f, 5f)
                        .breath(0.3f)
                        .fog(1.0f, 8.0f, 1.0f, 26.0f)
                        .fogColor(FOG_HOT)
                        .build())

                // ==========================================================
                // SAHNE 13 — DEVASA BEAM                    (tick 455-484)
                // ==========================================================
                // Bel ustu 3/4 yan. Bir omuz kameraya yakin, kafa hafif onde.
                // Guclu ve SAKIN bir durus — saldiriyi kontrol ediyor.
                .shot(Shot.of(30).cut().ease(Easing.SURGE)
                        .move(new Vec3(-2.30, 1.70, 0.10), new Vec3(-2.05, 1.62, 0.30))
                        .lookAtAttacker(1.55)
                        .fov(66f)
                        .shake(0.55f, 0.25f)
                        .roll(-4f, -1f)
                        .fog(2.5f, 48.0f)
                        .fogColor(FOG_HOT)
                        .build())

                // ==========================================================
                // SAHNE 14 — HEDEFIN KIRILMA ANI            (tick 485-506)
                // ==========================================================
                // Hafif ALTTAN 3/4 aci — kirilma yukarigi dogru okunuyor.
                .shot(Shot.of(22).cut().ease(Easing.IN)
                        .move(new Vec3(2.20, 0.45, 1.55), new Vec3(2.05, 0.70, 1.50))
                        .lookAtTarget(1.20)
                        .fov(64f)
                        .shake(0.35f, 0.85f)
                        .roll(3f, 8f)
                        .build())

                // ==========================================================
                // SAHNE 15 — DEVASA CARPISMA                (tick 507-516)
                // ==========================================================
                .shot(Shot.of(10).cut().ease(Easing.SNAP)
                        .at(3.40, 1.30, 1.35)
                        .lookAtTarget(1.30)
                        .fov(78f)
                        .shake(1.0f, 0.5f)
                        .roll(-10f, -3f)
                        .fogColor(FOG_HOT)
                        .build())

                // ==========================================================
                // SAHNE 16 — HIZIN BIRDEN ARTMASI           (tick 517-528)
                // ==========================================================
                // Buraya kadar kamera agirdi. Simdi tempo degisiyor — kamera
                // hedefe yetisemiyor ve whip pan ile arkasindan doniyor.
                .shot(Shot.of(12).smooth().ease(Easing.IN)
                        .move(new Vec3(2.60, 1.45, 1.90), new Vec3(1.20, 1.50, 2.60))
                        .lookAtTarget(1.20)
                        .fov(80f, 92f)
                        .shake(0.45f)
                        .roll(-6f, 6f)
                        .breath(0f)
                        .build())

                // ==========================================================
                // SAHNE 17 — SISE DOGRU FIRLAMA             (tick 529-550)
                // ==========================================================
                // Kamera arkadan bakiyor. Karakter sise girip yutuluyor,
                // sonra ~1 saniye BOS SIS goruyoruz. Hemen kesilmiyor.
                .shot(Shot.of(22).smooth().ease(Easing.OUT)
                        .move(new Vec3(0.30, 1.55, 1.60), new Vec3(0.20, 1.50, 2.30))
                        .lookAtFixed(new Vec3(0, 1.2, 3.6))
                        .fov(74f, 62f)
                        .shake(0.20f, 0f)
                        .fog(3.0f, 20.0f, 3.0f, 12.0f)
                        .fogColor(FOG_BLOOD)
                        .build())

                // ==========================================================
                // SAHNE 18 — SISIN ICINDEKI PATLAMA         (tick 551-586)
                // ==========================================================
                // Patlama dogrudan gorunmuyor — sisin TAMAMI aydinlaniyor.
                // Kamera uzakta ve sabit; asiri sarsinti YOK, patlamanin uzak
                // oldugu hissedilmeli.
                .shot(Shot.of(36).smooth().ease(Easing.OUT)
                        .at(0.20, 1.50, 2.30)
                        .lookAtFixed(new Vec3(0, 1.2, 3.8))
                        .fov(62f)
                        .shake(0.05f, 0.30f)
                        .breath(0.4f)
                        .fog(3.0f, 12.0f, 6.0f, 34.0f)
                        .fogColor(FOG_BLAST)
                        .build())

                // ==========================================================
                // SAHNE 19 — SON KARE                       (tick 587-622)
                // ==========================================================
                // Sis tekrar kapanir. Havada sadece kucuk kirmizi parcaciklar.
                .shot(Shot.of(36).smooth().ease(Easing.OUT)
                        .move(new Vec3(0.20, 1.50, 2.30), new Vec3(0.35, 1.58, 2.05))
                        .lookAtFixed(new Vec3(0, 1.1, 3.8))
                        .fov(58f)
                        .breath(0.5f)
                        .fog(6.0f, 34.0f, 2.5f, 16.0f)
                        .fogColor(FOG_DARK)
                        .build())

                // ==========================================================
                // OLAYLAR
                // ==========================================================

                // --- SAHNE 2: karakter etrafina bakiyor ---
                .beat(Beat.lookPitch(50, 0f))
                .beat(Beat.fx(62, "dust", new Vec3(0, 0.05, 1.0), 0.25f))

                // --- SAHNE 3: ILK OPTIC BLAST (tick 100) ---
                .beat(Beat.beam(100, BEAM_THIN))
                .beat(Beat.sound(100, "minecraft:entity.blaze.shoot", 1.7f, 1.5f))
                .beat(Beat.fx(100, "impact", new Vec3(0.4, 1.2, 1.0), 0.9f))
                .beat(Beat.darkness(100, 0.15f))
                // Hedef yarim adim geri kayar ama savrulmaz
                .beat(Beat.moveTarget(103, new Vec3(0, 0, 1.08), 0.045f))
                .beat(Beat.fx(104, "dust", new Vec3(0, 0.08, 1.05), 0.5f))
                .beat(Beat.beamOff(112))
                .beat(Beat.darkness(113, 0.55f))

                // --- SAHNE 4: sessizlik, uzakta kirmizi nokta ---
                .beat(Beat.darkness(126, 0.65f))
                .beat(Beat.fx(163, "charge", new Vec3(0, 1.62, 0), 0.35f))
                .beat(Beat.sound(163, "minecraft:block.beacon.ambient", 0.5f, 1.8f))

                // --- SAHNE 5: optic glow buyur ---
                .beat(Beat.fx(185, "charge", new Vec3(0, 1.62, 0), 0.7f))
                .beat(Beat.darkness(190, 0.50f))
                .beat(Beat.fx(200, "charge", new Vec3(0, 1.62, 0), 1.1f))
                .beat(Beat.sound(206, "minecraft:entity.blaze.ambient", 1.0f, 0.45f))
                .beat(Beat.fx(212, "charge", new Vec3(0, 1.64, 0), 1.6f))
                .beat(Beat.darkness(215, 0.38f))

                // --- SAHNE 6: KALIN ISIN CIKAR (tick 221) ---
                .beat(Beat.beam(221, BEAM_NORMAL))
                .beat(Beat.sound(221, "minecraft:entity.lightning_bolt.thunder", 1.5f, 1.2f))
                .beat(Beat.sound(221, "minecraft:entity.blaze.shoot", 1.6f, 0.9f))
                .beat(Beat.darkness(221, 0.20f))
                .beat(Beat.fx(221, "charge", new Vec3(0, 1.62, 0), 2.2f))

                // --- SAHNE 7: CARPMA, hedef geri kayar (243-262) ---
                .beat(Beat.fx(243, "impact", new Vec3(0, 1.0, 1.0), 1.2f))
                .beat(Beat.sound(243, "minecraft:entity.generic.explode", 0.9f, 1.4f))
                .beat(Beat.moveTarget(245, new Vec3(0, 0, 1.30), 0.050f))
                .beat(Beat.fx(248, "dust", new Vec3(0, 0.10, 1.18), 0.8f))
                .beat(Beat.fx(254, "dust", new Vec3(0, 0.10, 1.26), 0.8f))

                // Ayak yere basar — kayma durur
                .beat(Beat.freeze(262))
                .beat(Beat.sound(262, "minecraft:block.anvil.land", 0.8f, 1.6f))
                .beat(Beat.fx(262, "dust", new Vec3(0, 0.08, 1.30), 1.3f))

                // --- SAHNE 8: DIRENIS — titreyen kollar ---
                .beat(Beat.fx(285, "dust", new Vec3(0, 0.06, 1.28), 0.4f))
                .beat(Beat.fx(300, "dust", new Vec3(0, 0.06, 1.28), 0.4f))

                // --- SAHNE 9: ISINA KARSI ONE ADIM (320, 338) ---
                .beat(Beat.moveTarget(320, new Vec3(0, 0, 1.12), 0.022f))
                .beat(Beat.sound(322, "minecraft:block.gravel.step", 0.9f, 0.6f))
                .beat(Beat.fx(322, "dust", new Vec3(0, 0.08, 1.22), 0.9f))
                .beat(Beat.moveTarget(338, new Vec3(0, 0, 0.94), 0.022f))
                .beat(Beat.sound(340, "minecraft:block.gravel.step", 0.9f, 0.6f))
                .beat(Beat.fx(340, "dust", new Vec3(0, 0.08, 1.05), 0.9f))

                // --- SAHNE 11: el gozluge gider, ISIN DERINLESIR ---
                .beat(Beat.sound(400, "minecraft:block.beacon.ambient", 1.2f, 0.4f))
                .beat(Beat.fx(408, "charge", new Vec3(0, 1.62, 0), 1.4f))

                // --- SAHNE 12: VISOR CIKAR — ISIN KESILIR, SESSIZLIK ---
                .beat(Beat.beamOff(431))
                .beat(Beat.freeze(431))
                .beat(Beat.sound(431, "minecraft:block.beacon.deactivate", 0.9f, 2.0f))
                .beat(Beat.darkness(431, 0.30f))

                // Goz reveal
                .beat(Beat.fx(442, "charge", new Vec3(0, 1.66, 0), 2.6f))
                .beat(Beat.darkness(444, 0.18f))

                // --- SAHNE 13: MAXIMUM POWER (tick 455) ---
                .beat(Beat.beam(455, BEAM_MAX))
                .beat(Beat.darkness(455, 0.04f))
                .beat(Beat.sound(455, "minecraft:entity.lightning_bolt.thunder", 2.0f, 0.6f))
                .beat(Beat.sound(455, "minecraft:entity.generic.explode", 1.6f, 0.4f))
                .beat(Beat.fx(455, "explode", new Vec3(0, 1.3, 0.3), 1.2f))
                .beat(Beat.fx(462, "charge", new Vec3(0, 1.66, 0), 3.0f))

                // --- SAHNE 14-15: DIRENIS KIRILIR, hedef savrulur ---
                .beat(Beat.launchTarget(485,
                        new Vec3(0, 0, 0.94), new Vec3(0, 0, 3.60), 2.8f, 30))
                .beat(Beat.sound(485, "minecraft:entity.generic.explode", 1.4f, 0.8f))
                .beat(Beat.fx(492, "smoke", new Vec3(0, 1.0, 1.8), 0.9f))
                .beat(Beat.fx(500, "smoke", new Vec3(0, 1.1, 2.5), 0.9f))
                .beat(Beat.fx(507, "impact", new Vec3(0, 1.1, 3.0), 1.4f))

                // --- SAHNE 16-17: firlama, isin kesilir ---
                .beat(Beat.fx(519, "smoke", new Vec3(0, 1.1, 3.3), 1.0f))
                .beat(Beat.beamOff(529))
                .beat(Beat.fx(532, "smoke", new Vec3(0, 1.0, 3.6), 1.2f))

                // --- SAHNE 18: SISIN ICINDEKI PATLAMA ---
                // Once kucuk kirmizi isik (0.2 sn), sonra patlama.
                // HASAR BURADA — tum sinematik boyunca tek hasar ani.
                .beat(Beat.fx(551, "charge", new Vec3(0, 1.1, 3.8), 0.5f))
                .beat(Beat.damage(555, 0.85f))
                .beat(Beat.fx(555, "explode", new Vec3(0, 1.0, 3.8), 1.6f))
                .beat(Beat.fx(556, "dust", new Vec3(0, 0.2, 3.8), 2.6f))
                .beat(Beat.darkness(555, 0.02f))
                // Ses ISIKTAN SONRA gelir — patlama uzakta
                .beat(Beat.sound(559, "minecraft:entity.generic.explode", 2.0f, 0.35f))
                .beat(Beat.sound(561, "minecraft:entity.lightning_bolt.thunder", 1.8f, 0.5f))
                .beat(Beat.fx(566, "smoke", new Vec3(0, 1.0, 3.8), 2.0f))
                .beat(Beat.darkness(570, 0.25f))

                // --- SAHNE 19: sis kapanir, kucuk kirmizi parcaciklar ---
                .beat(Beat.fx(590, "charge", new Vec3(0, 0.9, 3.7), 0.4f))
                .beat(Beat.fx(602, "charge", new Vec3(0, 1.2, 3.9), 0.3f))
                .beat(Beat.darkness(600, 0.45f))
                .beat(Beat.darkness(618, 0f))

                .build();
    }
}
