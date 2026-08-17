package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.cinematic.*;
import net.minecraft.world.phys.Vec3;

/**
 * MAXIMUM POWER — Cyclops'un sinematik bitirme hamlesi (~5.7 sn / 114 tick).
 *
 * Tum konumlar SAHNE UZAYINDA yazilir:
 *   x = sag (+ saga)
 *   y = yukari
 *   z = ileri; 0 = Cyclops, 1 = hedefin baslangic yeri
 * Bu yuzden koreografi dunyadaki konumdan bagimsizdir.
 *
 * ANLATININ TAMAMI SU KARSITLIK UZERINE KURULU:
 *   ILK ISIN     -> hedef geri kayar ama DIRENIR, hatta ONE ADIM ATAR
 *   MAXIMUM POWER-> direnis aninda kirilir, hedef yerden kesilip savrulur
 *
 * Hedefin z konumu bunu somut olarak anlatir:
 *   1.00 baslangic -> 1.14 geri kayar -> 1.14 ayak basar (durur)
 *        -> 0.98 adim -> 0.86 ikinci adim  (ISINA KARSI ILERLIYOR)
 *        -> 2.95 savrulur (DIRENIS BITTI)
 */
public final class MaximumPowerCinematic {

    public static final String ID = "cyclops:maximum_power";

    private static final Vec3 TARGET_ANCHOR = new Vec3(0, 0, 1.0);

    // Isin genislikleri — fark gorsel olarak okunmali
    private static final float BEAM_NORMAL = 1.0f;
    private static final float BEAM_MAX = 5.0f;

    private MaximumPowerCinematic() {}

    public static void register() {
        CinematicRegistry.register(build());
    }

    private static CinematicDefinition build() {
        return CinematicDefinition.builder(ID)
                .letterbox(true)
                .anchorTarget(TARGET_ANCHOR)

                // ==========================================================
                // BOLUM 1 — KARANLIK VE YALNIZLIK
                // ==========================================================

                // 01 KURULUS — genis plan, hedef kadrajda merkezde DEGIL,
                // etrafinda bos siyah alan var (birazdan orada bir sey belirecek)
                .shot(Shot.of(9).cut().ease(Easing.IN_OUT)
                        .move(new Vec3(3.6, 2.2, 1.75), new Vec3(2.9, 2.0, 1.55))
                        .lookAtTarget(1.05)
                        .fov(64f)
                        .build())

                // 02 HEDEF YAKIN PLAN — sol/sag bakinir, kamera hafif takip eder
                .shot(Shot.of(8).cut().ease(Easing.LINEAR)
                        .move(new Vec3(-1.9, 1.65, 1.42), new Vec3(-1.5, 1.62, 1.28))
                        .lookAtTarget(1.42)
                        .fov(54f)
                        .build())

                // 03 OMUZ USTU — hedef onde, kamera KARANLIGA bakiyor. Bos.
                .shot(Shot.of(5).cut()
                        .at(0.75, 1.72, 1.42)
                        .lookAtFixed(new Vec3(0, 1.5, 0.15))
                        .fov(58f)
                        .build())

                // ==========================================================
                // BOLUM 2 — KARANLIKTAKI KIZIL OPTIK
                // ==========================================================

                // 04 ILK KIZIL OPTIK — hala omuz ustu, optik yavasca parlar
                .shot(Shot.of(5).smooth().ease(Easing.IN)
                        .move(new Vec3(0.75, 1.72, 1.38), new Vec3(0.6, 1.70, 1.25))
                        .lookAtAttacker(1.55)
                        .fov(52f)
                        .build())

                // 05 OPTIK ASIRI YAKIN PLAN — kadraj neredeyse tamamen siyah
                .shot(Shot.of(3).cut()
                        .at(0.0, 1.66, 0.34)
                        .lookAtAttacker(1.66)
                        .fov(34f)
                        .build())

                // ==========================================================
                // BOLUM 3 — ANI ISIN VE DIRENIS
                // ==========================================================

                // 06 ILK ISIN — genis yan profil, ucu de kadrajda. BANG.
                .shot(Shot.of(3).cut()
                        .at(5.2, 1.9, 0.5)
                        .lookAtMidpoint(1.15)
                        .fov(76f)
                        .shake(0.55f, 0.2f)
                        .build())

                // 07 ISIN CARPMASI — kamera hedefe dogru bastirir, hedef kayar
                .shot(Shot.of(6).smooth().ease(Easing.OUT)
                        .move(new Vec3(5.0, 1.9, 0.65), new Vec3(4.2, 1.8, 0.9))
                        .lookAtTarget(1.05)
                        .fov(72f)
                        .shake(0.25f)
                        .build())

                // 08 AYAK PLANI — cok alcak kamera, ayak kayar sonra yere BASAR
                .shot(Shot.of(4).cut()
                        .at(1.5, 0.4, 1.22)
                        .lookAtTarget(0.18)
                        .fov(50f)
                        .shake(0.15f, 0.6f)
                        .build())

                // 09 DIRENIS — bel alti/gogus hizasi, hafif alcak aci.
                // Hedef one egilir ve ISINA KARSI ADIM ATAR.
                .shot(Shot.of(6).cut().ease(Easing.LINEAR)
                        .move(new Vec3(1.9, 1.05, 1.30), new Vec3(1.6, 1.15, 1.05))
                        .lookAtTarget(1.15)
                        .fov(58f)
                        .shake(0.2f)
                        .build())

                // 10 IKILI PLAN — direnisin kaniti: hedef isine ragmen yaklasti
                .shot(Shot.of(6).cut()
                        .at(6.0, 2.3, 0.55)
                        .lookAtMidpoint(1.1)
                        .fov(78f)
                        .shake(0.18f)
                        .build())

                // ==========================================================
                // BOLUM 4 — CYCLOPS KARANLIKTAN CIKAR
                // ==========================================================

                // 11 REVEAL — kamera Cyclops'a kayar, silüetten govdeye
                .shot(Shot.of(6).smooth().ease(Easing.IN_OUT)
                        .move(new Vec3(2.6, 1.5, -1.9), new Vec3(1.5, 1.35, -0.7))
                        .lookAtAttacker(1.5)
                        .fov(62f)
                        .shake(0.1f)
                        .build())

                // 12 ALCAK ACI — Cyclops guclu gorunsun, isin kadrajdan gecsin
                .shot(Shot.of(4).cut()
                        .at(1.5, 0.95, -0.55)
                        .lookAtAttacker(1.6)
                        .fov(66f)
                        .shake(0.12f)
                        .build())

                // 13 "YETMIYOR" — Cyclops yakin plan, optik yuklenmeye baslar
                .shot(Shot.of(4).cut().ease(Easing.IN)
                        .move(new Vec3(0.85, 1.6, 0.55), new Vec3(0.7, 1.62, 0.45))
                        .lookAtAttacker(1.62)
                        .fov(48f)
                        .shake(0.25f, 0.45f)
                        .build())

                // ==========================================================
                // BOLUM 5 — VISOR VE SESSIZLIK
                // ==========================================================

                // 14 VISOR ASIRI YAKIN PLAN — visor cikar, ISIN KESILIR
                .shot(Shot.of(6).cut().ease(Easing.IN)
                        .move(new Vec3(0.0, 1.66, 0.40), new Vec3(0.0, 1.66, 0.30))
                        .lookAtAttacker(1.66)
                        .fov(38f, 33f)
                        .shake(0.3f, 0.05f)
                        .build())

                // 15 GOZ REVEAL — sessizlik, gozler beyaz-kizila doner
                .shot(Shot.of(4).cut().ease(Easing.SURGE)
                        .at(0.0, 1.67, 0.24)
                        .lookAtAttacker(1.67)
                        .fov(30f, 26f)
                        .shake(0.7f)
                        .build())

                // ==========================================================
                // BOLUM 6 — MAXIMUM POWER
                // ==========================================================

                // 16 HIZLI GERI CEKILIS — asiri yakindan devasa genise
                .shot(Shot.of(4).smooth().ease(Easing.SURGE)
                        .move(new Vec3(0.0, 1.67, 0.24), new Vec3(7.5, 3.2, 0.4))
                        .lookAtMidpoint(1.2)
                        .fov(30f, 98f)
                        .shake(1.3f)
                        .build())

                // 17 DIRENIS KIRILIR — genis yan plan, hedef yerden kesilir
                .shot(Shot.of(4).cut()
                        .at(7.0, 2.8, 0.6)
                        .lookAtMidpoint(1.2)
                        .fov(96f)
                        .shake(1.5f)
                        .build())

                // 18 UCUS TAKIBI — kamera hedefi yandan izler
                .shot(Shot.of(6).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(5.5, 2.3, 1.5), new Vec3(6.0, 2.5, 2.5))
                        .lookAtTarget(1.0)
                        .fov(94f, 102f)
                        .shake(0.8f)
                        .build())

                // 19 HEDEF BAKISI — kisa: Cyclops uzakta, dev isin kadraji doldurur
                .shot(Shot.of(3).cut()
                        .at(0.0, 1.5, 2.55)
                        .lookAtAttacker(1.6)
                        .fov(88f)
                        .shake(1.0f)
                        .build())

                // 20 DEVASA GENIS — tum aksiyon tek kadrajda
                .shot(Shot.of(4).cut()
                        .at(9.5, 4.0, 1.4)
                        .lookAtMidpoint(1.3)
                        .fov(100f)
                        .shake(0.9f)
                        .build())

                // ==========================================================
                // BOLUM 7 — SON CARPMA
                // ==========================================================

                // 21 CARPMA KAMERASI — hedef kadraja girer ve vurur
                .shot(Shot.of(5).cut().ease(Easing.SNAP)
                        .at(2.6, 1.6, 3.0)
                        .lookAtTarget(1.0)
                        .fov(70f)
                        .shake(2.0f, 0.4f)
                        .build())

                // 22 X-RAY ENERJI ANI — cok kisa, hedef parlayan silüet
                .shot(Shot.of(4).cut()
                        .at(1.7, 1.3, 2.75)
                        .lookAtTarget(1.05)
                        .fov(52f)
                        .shake(0.6f)
                        .build())

                // 23 SONRASI — isin biter, kizil parilti kalir
                .shot(Shot.of(5).smooth().ease(Easing.OUT)
                        .move(new Vec3(3.5, 2.0, 2.2), new Vec3(4.2, 2.3, 1.6))
                        .lookAtTarget(0.9)
                        .fov(74f)
                        .shake(0.25f, 0f)
                        .build())

                // 24 KAHRAMAN PLANI — goz altindan, yukari bakan alcak aci
                .shot(Shot.of(9).cut().ease(Easing.OUT)
                        .move(new Vec3(0.6, 1.05, -1.5), new Vec3(1.1, 1.30, -2.8))
                        .lookAtAttacker(1.58)
                        .fov(60f)
                        .shake(0.06f, 0f)
                        .build())

                // ==========================================================
                // OLAYLAR — tick'ler yukaridaki cekimlerle hizali
                // ==========================================================

                // --- Karanlik, hedef yalniz (0-22) ---
                .beat(Beat.darkness(1, 0.86f))
                .beat(Beat.freeze(1))
                .beat(Beat.sound(1, "minecraft:block.beacon.deactivate", 0.6f, 0.45f))

                // --- Kizil optik belirir (22) ---
                .beat(Beat.darkness(22, 0.78f))
                .beat(Beat.fx(22, "charge", new Vec3(0, 1.62, 0), 0.6f))
                .beat(Beat.sound(22, "minecraft:block.beacon.power_select", 0.7f, 0.45f))
                .beat(Beat.fx(25, "charge", new Vec3(0, 1.62, 0), 1.0f))

                // --- Optik yakin plan (27) ---
                .beat(Beat.fx(27, "charge", new Vec3(0, 1.62, 0), 1.6f))

                // --- ILK ISIN (30) ---
                .beat(Beat.beam(30, BEAM_NORMAL))
                .beat(Beat.darkness(30, 0.55f))
                .beat(Beat.sound(30, "minecraft:entity.blaze.shoot", 1.6f, 1.15f))
                .beat(Beat.fx(30, "impact", new Vec3(0, 1.0, 1.0), 1.0f))

                // --- DIRENIS: geri kayar (33-39) ---
                .beat(Beat.moveTarget(33, new Vec3(0, 0, 1.14), 0.055f))
                .beat(Beat.fx(35, "dust", new Vec3(0, 0.1, 1.08), 0.6f))

                // --- AYAK YERE BASAR (39) ---
                .beat(Beat.freeze(39))
                .beat(Beat.sound(39, "minecraft:block.anvil.land", 0.7f, 1.7f))
                .beat(Beat.fx(39, "dust", new Vec3(0, 0.08, 1.14), 1.1f))

                // --- ISINA KARSI ONE ADIM (43, 47) ---
                .beat(Beat.moveTarget(43, new Vec3(0, 0, 0.98), 0.030f))
                .beat(Beat.fx(44, "dust", new Vec3(0, 0.08, 1.08), 0.5f))
                .beat(Beat.sound(45, "minecraft:block.gravel.step", 0.8f, 0.7f))
                .beat(Beat.moveTarget(47, new Vec3(0, 0, 0.86), 0.030f))
                .beat(Beat.fx(48, "dust", new Vec3(0, 0.08, 0.95), 0.5f))
                .beat(Beat.sound(50, "minecraft:block.gravel.step", 0.8f, 0.7f))

                // --- Cyclops karanliktan cikar (55) ---
                .beat(Beat.darkness(55, 0.42f))
                .beat(Beat.fx(57, "charge", new Vec3(0, 1.62, 0), 1.2f))
                .beat(Beat.darkness(61, 0.32f))

                // --- "Yetmiyor" — optik yuklenir (65) ---
                .beat(Beat.fx(65, "charge", new Vec3(0, 1.62, 0), 2.0f))
                .beat(Beat.sound(65, "minecraft:entity.blaze.ambient", 1.3f, 0.35f))
                .beat(Beat.fx(67, "charge", new Vec3(0, 1.62, 0), 2.4f))

                // --- VISOR CIKAR: ISIN KESILIR, SESSIZLIK (69) ---
                .beat(Beat.beamOff(69))
                .beat(Beat.freeze(69))
                .beat(Beat.sound(69, "minecraft:block.beacon.deactivate", 0.85f, 2.0f))
                .beat(Beat.darkness(69, 0.26f))

                // --- Goz reveal (75) ---
                .beat(Beat.fx(75, "charge", new Vec3(0, 1.66, 0), 2.6f))
                .beat(Beat.darkness(75, 0.20f))

                // --- MAXIMUM POWER (79) ---
                .beat(Beat.beam(79, BEAM_MAX))
                .beat(Beat.darkness(79, 0.06f))
                .beat(Beat.sound(79, "minecraft:entity.lightning_bolt.thunder", 2.0f, 0.65f))
                .beat(Beat.sound(79, "minecraft:entity.generic.explode", 1.5f, 0.45f))
                .beat(Beat.fx(79, "explode", new Vec3(0, 1.3, 0.3), 1.0f))

                // --- DIRENIS KIRILIR: hedef savrulur (83-97) ---
                .beat(Beat.launchTarget(83,
                        new Vec3(0, 0, 0.86), new Vec3(0, 0, 2.95), 2.6f, 14))
                .beat(Beat.fx(86, "smoke", new Vec3(0, 1.0, 1.6), 0.7f))
                .beat(Beat.fx(91, "smoke", new Vec3(0, 1.0, 2.3), 0.7f))

                // --- SON CARPMA — HASAR BURADA (97) ---
                .beat(Beat.damage(97, 0.85f))
                .beat(Beat.sound(97, "minecraft:entity.generic.explode", 2.0f, 0.5f))
                .beat(Beat.fx(97, "explode", new Vec3(0, 1.0, 2.95), 1.0f))
                .beat(Beat.fx(97, "impact", new Vec3(0, 1.0, 2.95), 1.0f))
                .beat(Beat.fx(98, "dust", new Vec3(0, 0.1, 2.95), 2.2f))

                // --- X-Ray enerji ani (102) ---
                .beat(Beat.fx(102, "impact", new Vec3(0, 1.1, 2.95), 1.2f))
                .beat(Beat.fx(103, "charge", new Vec3(0, 1.1, 2.95), 1.6f))

                // --- Sonrasi + kahraman plani (106+) ---
                .beat(Beat.beamOff(106))
                .beat(Beat.fx(106, "smoke", new Vec3(0, 0.8, 2.95), 1.2f))
                .beat(Beat.darkness(106, 0.12f))
                .beat(Beat.fx(109, "charge", new Vec3(0, 1.62, 0), 0.7f))
                .beat(Beat.darkness(112, 0f))

                .build();
    }
}
