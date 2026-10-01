package com.FIRNI.superheromod.heroes.cyclops;

import com.FIRNI.superheromod.core.cinematic.*;
import com.FIRNI.superheromod.core.cinematic.CinematicDefinition.Actor;
import net.minecraft.world.phys.Vec3;

/**
 * MAXIMUM POWER — Cyclops'un sinematik bitirme hamlesi (408 tick / ~20 sn).
 *
 * Tum konumlar SAHNE UZAYINDA yazilir:
 *   x = sag (+ saga)
 *   y = yukari
 *   z = ileri; 0 = Cyclops, 1 = hedefin baslangic yeri
 *
 * TEMPO: acilista cekimler 16-26 tick, son bolumde (firlatma) 8 tick'e
 * duser. Hizlanmanin anlam kazanmasi icin oncesinin sakin olmasi sart.
 *
 * POZLAR: aktorler artik gercek entity degil KUKLA oldugu icin Minecraft'in
 * yapamayacagi seyleri yapabiliyorlar — comelme, belden bukulme, havada
 * savrulma. Her poz anahtarina bir ZINCIR verilir; zincir eklemlerin sirayla
 * hareket etmesini saglar (kuvvet vucuttan gecer), hepsi ayni anda oynarsa
 * mekanik durur.
 */
public final class MaximumPowerCinematic {

    public static final String ID = "cyclops:maximum_power";

    private static final Vec3 TARGET_ANCHOR = new Vec3(0, 0, 1.0);

    private static final float BEAM_THIN = 0.5f;
    private static final float BEAM_NORMAL = 1.2f;
    private static final float BEAM_MAX = 5.0f;

    private static final int FOG_DARK = 0x0A0A0C;
    private static final int FOG_BLOOD = 0x3A1216;
    private static final int FOG_HOT = 0x5A1C14;
    private static final int FOG_BLAST = 0x8A3A18;

    private MaximumPowerCinematic() {}

    public static void register() {
        CinematicRegistry.register(build());
    }

    private static CinematicDefinition build() {
        return CinematicDefinition.builder(ID)
                .letterbox(true)
                .anchorTarget(TARGET_ANCHOR)
                .atmosphere(2.5f, 22.0f, FOG_DARK)

                // ==========================================================
                // CEKIMLER
                // ==========================================================

                // S1 — KARANLIK / YALNIZLIK (0-25)
                .shot(Shot.of(26).cut().ease(Easing.LINEAR)
                        .move(new Vec3(2.30, 1.35, 1.85), new Vec3(2.05, 1.35, 1.80))
                        .lookAtTarget(1.15)
                        .fov(52f).breath(0.5f)
                        .build())

                // S2 — ETRAFINI KONTROL (26-61). Kamera TAKIP ETMEZ.
                .shot(Shot.of(20).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(2.05, 1.35, 1.80), new Vec3(1.85, 1.38, 1.72))
                        .lookAtTarget(1.25)
                        .fov(50f).breath(0.5f)
                        .build())
                .shot(Shot.of(16).smooth().ease(Easing.LINEAR)
                        .at(1.85, 1.42, 1.66)
                        .lookAtTarget(1.40)
                        .fov(46f).pushIn(0.14).breath(0.6f)
                        .build())

                // S3 — ILK OPTIC BLAST (62-79)
                .shot(Shot.of(10).cut().ease(Easing.SNAP)
                        .at(1.60, 1.45, 1.58)
                        .lookAtTarget(1.35)
                        .fov(44f, 58f)
                        .shake(0.7f, 0.15f).roll(-5f, -1f)
                        .fog(1.5f, 45.0f, 3.0f, 30.0f).fogColor(FOG_BLOOD)
                        .build())
                .shot(Shot.of(8).smooth().ease(Easing.OUT)
                        .curve(new Vec3(1.30, 1.50, 1.90), new Vec3(1.12, 1.51, 2.02),
                                new Vec3(0.83, 1.54, 2.10), new Vec3(0.60, 1.55, 2.10))
                        .lookAtTarget(1.25)
                        .fov(56f).breath(0.4f)
                        .build())

                // S4 — SESSIZLIK (80-113). Omuz ustu, onumuzde sadece sis.
                .shot(Shot.of(20).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(0.45, 1.62, 1.95), new Vec3(0.42, 1.60, 1.74))
                        .lookAtFixed(new Vec3(0, 1.35, 0.10))
                        .fov(50f).breath(0.55f)
                        .fog(2.0f, 16.0f).fogColor(FOG_DARK)
                        .build())
                .shot(Shot.of(14).smooth().ease(Easing.LINEAR)
                        .at(0.42, 1.58, 1.64)
                        .lookAtFixed(new Vec3(0, 1.55, 0.05))
                        .fov(44f).pushIn(0.10).breath(0.5f)
                        .fog(2.0f, 20.0f, 2.0f, 26.0f)
                        .build())

                // S5 — CYCLOPS'UN OPTIC GLOW'U (114-141)
                .shot(Shot.of(18).smooth().ease(Easing.LINEAR)
                        .at(0.40, 1.60, 1.35)
                        .lookAtAttacker(1.60)
                        .fov(40f).pushIn(0.14).breath(0.45f)
                        .fogColor(FOG_BLOOD)
                        .build())
                .shot(Shot.of(10).smooth().ease(Easing.LINEAR)
                        .at(0.10, 1.64, 0.55)
                        .lookAtAttacker(1.64)
                        .fov(32f).pushIn(0.16).roll(4f, 7f).breath(0.4f)
                        .fog(1.2f, 10.0f)
                        .build())

                // S6 — KALIN BEAM (142-155)
                .shot(Shot.of(14).cut().ease(Easing.SURGE)
                        .move(new Vec3(3.90, 1.55, 0.55), new Vec3(3.65, 1.50, 0.70))
                        .lookAtMidpoint(1.30)
                        .fov(72f).shake(0.45f, 0.18f).roll(-7f, -2f)
                        .fog(2.0f, 40.0f).fogColor(FOG_BLOOD)
                        .build())

                // S7 — ILK CARPISMA (156-179). TAM YAN PROFIL, kamera SABIT.
                .shot(Shot.of(24).cut().ease(Easing.LINEAR)
                        .at(4.20, 1.05, 1.10)
                        .lookAtTarget(1.00)
                        .fov(62f).shake(0.30f, 0.12f).breath(0.5f)
                        .build())

                // S8 — DIRENIS (180-205)
                .shot(Shot.of(26).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(3.10, 0.95, 1.35), new Vec3(2.75, 0.90, 1.30))
                        .lookAtTarget(0.95)
                        .fov(58f).shake(0.14f).roll(2f, 4f).breath(0.5f)
                        .build())

                // S9 — ILERLEME (206-233). Karakter gelirken kamera geri gider.
                .shot(Shot.of(28).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(0.90, 1.15, 1.95), new Vec3(1.10, 1.20, 2.45))
                        .lookAtTarget(1.05)
                        .fov(60f).shake(0.10f).breath(0.55f)
                        .build())

                // S10 — CYCLOPS'A DONUS (234-253)
                .shot(Shot.of(20).smooth().ease(Easing.LINEAR)
                        .move(new Vec3(-1.85, 1.55, 0.05), new Vec3(-1.62, 1.52, 0.18))
                        .lookAtAttacker(1.52)
                        .fov(54f).breath(0.45f).fogColor(FOG_BLOOD)
                        .build())

                // S11 — GOZLUGE GIDEN EL (254-279)
                .shot(Shot.of(26).smooth().ease(Easing.LINEAR)
                        .at(-1.15, 1.60, 0.62)
                        .lookAtAttacker(1.58)
                        .fov(42f).pushIn(0.12).breath(0.4f)
                        .fog(1.5f, 14.0f)
                        .build())

                // S12 — MAXIMUM POWER (280-295)
                .shot(Shot.of(16).smooth().ease(Easing.IN)
                        .curve(new Vec3(-0.55, 1.66, 0.55), new Vec3(-0.43, 1.68, 0.48),
                                new Vec3(-0.21, 1.67, 0.35), new Vec3(-0.16, 1.66, 0.26))
                        .lookAtAttacker(1.66)
                        .fov(38f, 26f).roll(0f, 5f).breath(0.3f)
                        .fog(1.0f, 8.0f, 1.0f, 26.0f).fogColor(FOG_HOT)
                        .build())

                // S13 — DEVASA BEAM (296-315)
                .shot(Shot.of(20).cut().ease(Easing.SURGE)
                        .move(new Vec3(-2.30, 1.70, 0.10), new Vec3(-2.08, 1.62, 0.28))
                        .lookAtAttacker(1.55)
                        .fov(66f).shake(0.55f, 0.25f).roll(-4f, -1f)
                        .fog(2.5f, 48.0f).fogColor(FOG_HOT)
                        .build())

                // S14 — KIRILMA (316-329). Hafif ALTTAN.
                .shot(Shot.of(14).cut().ease(Easing.IN)
                        .move(new Vec3(2.20, 0.45, 1.55), new Vec3(2.05, 0.70, 1.50))
                        .lookAtTarget(1.20)
                        .fov(64f).shake(0.35f, 0.85f).roll(3f, 8f)
                        .build())

                // S15 — DEVASA CARPISMA (330-337)
                .shot(Shot.of(8).cut().ease(Easing.SNAP)
                        .at(3.40, 1.30, 1.35)
                        .lookAtTarget(1.30)
                        .fov(78f).shake(1.0f, 0.5f).roll(-10f, -3f)
                        .fogColor(FOG_HOT)
                        .build())

                // S16 — WHIP PAN (338-345). Tempo burada degisir.
                .shot(Shot.of(8).smooth().ease(Easing.IN)
                        .move(new Vec3(2.60, 1.45, 1.90), new Vec3(1.20, 1.50, 2.60))
                        .lookAtTarget(1.20)
                        .fov(80f, 92f).shake(0.45f).roll(-6f, 6f).breath(0f)
                        .build())

                // S17 — SISE FIRLAMA (346-361)
                .shot(Shot.of(16).smooth().ease(Easing.OUT)
                        .move(new Vec3(0.30, 1.55, 1.60), new Vec3(0.20, 1.50, 2.30))
                        .lookAtFixed(new Vec3(0, 1.2, 3.6))
                        .fov(74f, 62f).shake(0.20f, 0f)
                        .fog(3.0f, 20.0f, 3.0f, 12.0f).fogColor(FOG_BLOOD)
                        .build())

                // S18 — SISIN ICINDEKI PATLAMA (362-387)
                .shot(Shot.of(26).smooth().ease(Easing.OUT)
                        .at(0.20, 1.50, 2.30)
                        .lookAtFixed(new Vec3(0, 1.2, 3.8))
                        .fov(62f).shake(0.05f, 0.30f).breath(0.4f)
                        .fog(3.0f, 12.0f, 6.0f, 34.0f).fogColor(FOG_BLAST)
                        .build())

                // S19 — SON KARE (388-407)
                .shot(Shot.of(20).smooth().ease(Easing.OUT)
                        .move(new Vec3(0.20, 1.50, 2.30), new Vec3(0.35, 1.58, 2.10))
                        .lookAtFixed(new Vec3(0, 1.1, 3.8))
                        .fov(58f).breath(0.5f)
                        .fog(6.0f, 34.0f, 2.5f, 16.0f).fogColor(FOG_DARK)
                        .build())

                // ==========================================================
                // HEDEFIN POZLARI
                // ==========================================================

                // Notr durus — omuzlar bilerek simetrik degil
                .pose(0, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.RIGHT_UPPER_ARM, 0, 0, 4)
                        .j(ActorPose.LEFT_UPPER_ARM, 0, 0, -7)
                        .j(ActorPose.RIGHT_LOWER_ARM, -8, 0, 0)
                        .j(ActorPose.LEFT_LOWER_ARM, -11, 0, 0))

                // Saga bakar: once kafa, govde geriden gelir
                .pose(34, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.HEAD, 4, 28, 0)
                        .j(ActorPose.CHEST, 0, 6, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, 0, 0, 5)
                        .j(ActorPose.LEFT_UPPER_ARM, 0, 0, -7)
                        .j(ActorPose.RIGHT_LOWER_ARM, -10, 0, 0)
                        .j(ActorPose.LEFT_LOWER_ARM, -12, 0, 0),
                        ActorPose.noticeChain())

                // Sola bakar, sol el hafif yukari — saldiriya hazirlaniyor
                .pose(52, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.HEAD, 2, -38, 0)
                        .j(ActorPose.CHEST, 0, -12, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, -26, 0, -14)
                        .j(ActorPose.LEFT_LOWER_ARM, -38, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, 0, 0, 5)
                        .j(ActorPose.RIGHT_LOWER_ARM, -12, 0, 0),
                        ActorPose.noticeChain())

                // ILK BLAST — kafa sertce doner, el gogse, yarim adim geri
                .pose(64, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.HEAD, -10, 46, 6)
                        .j(ActorPose.CHEST, -6, 20, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, -48, 0, 16)
                        .j(ActorPose.RIGHT_LOWER_ARM, -78, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, -20, 0, -18)
                        .j(ActorPose.LEFT_LOWER_ARM, -30, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, 14, 0, 0)
                        .j(ActorPose.RIGHT_LOWER_LEG, 10, 0, 0)
                        .j(ActorPose.LEFT_UPPER_LEG, -10, 0, 0)
                        .crouch(0.08f),
                        ActorPose.noticeChain())

                // Sessizlik — temkinli, hafif yana yatik bas
                .pose(88, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.HEAD, -4, 10, 7)
                        .j(ActorPose.CHEST, 3, 4, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, -14, 0, 9)
                        .j(ActorPose.RIGHT_LOWER_ARM, -26, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, -10, 0, -10)
                        .j(ActorPose.LEFT_LOWER_ARM, -22, 0, 0))

                // BEAM CARPTI — gogus geri, kollar one, dizler kirilir
                .pose(160, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.CHEST, -26, 0, 0)
                        .j(ActorPose.HEAD, -20, 0, 0)
                        .j(ActorPose.HIPS, 10, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, -96, 0, 20)
                        .j(ActorPose.RIGHT_LOWER_ARM, -24, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, -96, 0, -20)
                        .j(ActorPose.LEFT_LOWER_ARM, -24, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, -30, 0, 4)
                        .j(ActorPose.RIGHT_LOWER_LEG, 46, 0, 0)
                        .j(ActorPose.LEFT_UPPER_LEG, -24, 0, -4)
                        .j(ActorPose.LEFT_LOWER_LEG, 40, 0, 0)
                        .crouch(0.26f),
                        ActorPose.impactChain())

                // DIRENIS — omurga ONE egik, dizler kirik, ayaklar saglam
                .pose(186, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.CHEST, 28, 0, 0)
                        .j(ActorPose.HEAD, -34, 0, 0)
                        .j(ActorPose.HIPS, 6, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, -112, 0, 24)
                        .j(ActorPose.RIGHT_LOWER_ARM, -18, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, -112, 0, -24)
                        .j(ActorPose.LEFT_LOWER_ARM, -18, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, -38, 0, 6)
                        .j(ActorPose.RIGHT_LOWER_LEG, 58, 0, 0)
                        .j(ActorPose.LEFT_UPPER_LEG, -30, 0, -6)
                        .j(ActorPose.LEFT_LOWER_LEG, 50, 0, 0)
                        .crouch(0.42f),
                        ActorPose.impactChain())

                // ONE ADIM — cene yukari, meydan okuma
                .pose(214, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.CHEST, 32, 0, 0)
                        .j(ActorPose.HEAD, -42, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, -118, 0, 26)
                        .j(ActorPose.RIGHT_LOWER_ARM, -14, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, -118, 0, -26)
                        .j(ActorPose.LEFT_LOWER_ARM, -14, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, -52, 0, 5)
                        .j(ActorPose.RIGHT_LOWER_LEG, 40, 0, 0)
                        .j(ActorPose.LEFT_UPPER_LEG, 18, 0, -5)
                        .j(ActorPose.LEFT_LOWER_LEG, 24, 0, 0)
                        .crouch(0.30f))

                // Ikinci adim
                .pose(232, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.CHEST, 30, 0, 0)
                        .j(ActorPose.HEAD, -44, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, -120, 0, 28)
                        .j(ActorPose.RIGHT_LOWER_ARM, -12, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, -120, 0, -28)
                        .j(ActorPose.LEFT_LOWER_ARM, -12, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, 20, 0, 5)
                        .j(ActorPose.RIGHT_LOWER_LEG, 26, 0, 0)
                        .j(ActorPose.LEFT_UPPER_LEG, -50, 0, -5)
                        .j(ActorPose.LEFT_LOWER_LEG, 38, 0, 0)
                        .crouch(0.28f))

                // KIRILMA — kollar geriye savrulur, bilekler kirilir
                .pose(322, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.CHEST, -44, 0, 0)
                        .j(ActorPose.HEAD, -34, 0, 8)
                        .j(ActorPose.HIPS, 14, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, 48, 0, 48)
                        .j(ActorPose.RIGHT_LOWER_ARM, -8, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, 48, 0, -48)
                        .j(ActorPose.LEFT_LOWER_ARM, -8, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, -14, 0, 8)
                        .j(ActorPose.RIGHT_LOWER_LEG, 28, 0, 0)
                        .j(ActorPose.LEFT_UPPER_LEG, -8, 0, -8)
                        .j(ActorPose.LEFT_LOWER_LEG, 22, 0, 0)
                        .crouch(0.10f),
                        ActorPose.impactChain())

                // FIRLATILIR — tam savrulma, govde kendi ekseninde donmeye baslar
                .pose(334, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.CHEST, -58, 0, 0)
                        .j(ActorPose.HEAD, -48, 0, 14)
                        .j(ActorPose.HIPS, 20, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, 72, 0, 62)
                        .j(ActorPose.RIGHT_LOWER_ARM, -16, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, 66, 0, -58)
                        .j(ActorPose.LEFT_LOWER_ARM, -22, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, 34, 0, 10)
                        .j(ActorPose.RIGHT_LOWER_LEG, 30, 0, 0)
                        .j(ActorPose.LEFT_UPPER_LEG, 26, 0, -12)
                        .j(ActorPose.LEFT_LOWER_LEG, 38, 0, 0)
                        .body(16f, -22f)
                        .crouch(-0.18f),
                        ActorPose.launchChain())

                // Sise dogru — donme devam eder
                .pose(356, Actor.TARGET, ActorPose.of()
                        .j(ActorPose.CHEST, -62, 0, 0)
                        .j(ActorPose.HEAD, -50, 0, 20)
                        .j(ActorPose.RIGHT_UPPER_ARM, 80, 0, 68)
                        .j(ActorPose.RIGHT_LOWER_ARM, -20, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, 74, 0, -64)
                        .j(ActorPose.LEFT_LOWER_ARM, -26, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, 40, 0, 12)
                        .j(ActorPose.LEFT_UPPER_LEG, 32, 0, -14)
                        .body(38f, -30f)
                        .crouch(-0.20f))

                // ==========================================================
                // CYCLOPS'UN POZLARI
                // ==========================================================

                .pose(0, Actor.ATTACKER, ActorPose.of()
                        .j(ActorPose.RIGHT_UPPER_ARM, 0, 0, 5)
                        .j(ActorPose.LEFT_UPPER_ARM, 0, 0, -5)
                        .j(ActorPose.RIGHT_LOWER_ARM, -9, 0, 0)
                        .j(ActorPose.LEFT_LOWER_ARM, -9, 0, 0))

                // Karanliktan cikar — hareketsiz ama dominant durus
                .pose(120, Actor.ATTACKER, ActorPose.of()
                        .j(ActorPose.CHEST, -4, 0, 0)
                        .j(ActorPose.HEAD, -3, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, 2, 0, 9)
                        .j(ActorPose.LEFT_UPPER_ARM, 2, 0, -9)
                        .j(ActorPose.RIGHT_LOWER_ARM, -12, 0, 0)
                        .j(ActorPose.LEFT_LOWER_ARM, -12, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, 0, 0, 4)
                        .j(ActorPose.LEFT_UPPER_LEG, 0, 0, -4))

                // El yukselmeye baslar — once dirsek bukulur
                .pose(262, Actor.ATTACKER, ActorPose.of()
                        .j(ActorPose.CHEST, -4, 0, 0)
                        .j(ActorPose.HEAD, -4, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, -42, 0, 14)
                        .j(ActorPose.RIGHT_LOWER_ARM, -64, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, 2, 0, -9)
                        .j(ActorPose.LEFT_LOWER_ARM, -12, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, 0, 0, 4)
                        .j(ActorPose.LEFT_UPPER_LEG, 0, 0, -4))

                // Parmaklar gozlukte
                .pose(280, Actor.ATTACKER, ActorPose.of()
                        .j(ActorPose.CHEST, -5, 0, 0)
                        .j(ActorPose.HEAD, -6, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, -78, 0, 20)
                        .j(ActorPose.RIGHT_LOWER_ARM, -118, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, 2, 0, -9)
                        .j(ActorPose.LEFT_LOWER_ARM, -12, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, 0, 0, 4)
                        .j(ActorPose.LEFT_UPPER_LEG, 0, 0, -4))

                // MAXIMUM POWER — gozluk cikti, guclu ve SAKIN durus
                .pose(300, Actor.ATTACKER, ActorPose.of()
                        .j(ActorPose.CHEST, -9, 0, 0)
                        .j(ActorPose.HEAD, -12, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, 14, 0, 30)
                        .j(ActorPose.RIGHT_LOWER_ARM, -26, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, 8, 0, -26)
                        .j(ActorPose.LEFT_LOWER_ARM, -20, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_LEG, -8, 0, 7)
                        .j(ActorPose.RIGHT_LOWER_LEG, 8, 0, 0)
                        .j(ActorPose.LEFT_UPPER_LEG, 10, 0, -7)
                        .j(ActorPose.LEFT_LOWER_LEG, 6, 0, 0)
                        .crouch(0.10f),
                        ActorPose.impactChain())

                // Sonrasi — omuzlar iner
                .pose(392, Actor.ATTACKER, ActorPose.of()
                        .j(ActorPose.CHEST, -2, 0, 0)
                        .j(ActorPose.HEAD, -6, 0, 0)
                        .j(ActorPose.RIGHT_UPPER_ARM, 2, 0, 11)
                        .j(ActorPose.RIGHT_LOWER_ARM, -14, 0, 0)
                        .j(ActorPose.LEFT_UPPER_ARM, 2, 0, -11)
                        .j(ActorPose.LEFT_LOWER_ARM, -14, 0, 0))

                // ==========================================================
                // OLAYLAR
                // ==========================================================

                .beat(Beat.lookPitch(30, 0f))

                // S3 — ILK BLAST (62)
                .beat(Beat.beam(62, BEAM_THIN))
                .beat(Beat.sound(62, "minecraft:entity.blaze.shoot", 1.7f, 1.5f))
                .beat(Beat.fx(62, "impact", new Vec3(0.4, 1.2, 1.0), 0.9f))
                .beat(Beat.darkness(62, 0.15f))
                .beat(Beat.moveTarget(65, new Vec3(0, 0, 1.08), 0.045f))
                .beat(Beat.fx(66, "dust", new Vec3(0, 0.08, 1.05), 0.5f))
                .beat(Beat.beamOff(71))
                .beat(Beat.darkness(72, 0.55f))

                // S4 — sessizlik, uzakta kirmizi nokta
                .beat(Beat.darkness(80, 0.65f))
                .beat(Beat.fx(101, "charge", new Vec3(0, 1.62, 0), 0.35f))
                .beat(Beat.sound(101, "minecraft:block.beacon.ambient", 0.5f, 1.8f))

                // S5 — glow buyur
                .beat(Beat.fx(116, "charge", new Vec3(0, 1.62, 0), 0.7f))
                .beat(Beat.darkness(120, 0.50f))
                .beat(Beat.fx(128, "charge", new Vec3(0, 1.62, 0), 1.1f))
                .beat(Beat.sound(132, "minecraft:entity.blaze.ambient", 1.0f, 0.45f))
                .beat(Beat.fx(136, "charge", new Vec3(0, 1.64, 0), 1.6f))
                .beat(Beat.darkness(138, 0.38f))

                // S6 — KALIN ISIN (142)
                .beat(Beat.beam(142, BEAM_NORMAL))
                .beat(Beat.sound(142, "minecraft:entity.lightning_bolt.thunder", 1.5f, 1.2f))
                .beat(Beat.sound(142, "minecraft:entity.blaze.shoot", 1.6f, 0.9f))
                .beat(Beat.darkness(142, 0.20f))
                .beat(Beat.fx(142, "charge", new Vec3(0, 1.62, 0), 2.2f))

                // S7 — CARPMA, geri kayar (156-172)
                .beat(Beat.fx(156, "impact", new Vec3(0, 1.0, 1.0), 1.2f))
                .beat(Beat.sound(156, "minecraft:entity.generic.explode", 0.9f, 1.4f))
                .beat(Beat.moveTarget(158, new Vec3(0, 0, 1.30), 0.050f))
                .beat(Beat.fx(161, "dust", new Vec3(0, 0.10, 1.18), 0.8f))
                .beat(Beat.fx(167, "dust", new Vec3(0, 0.10, 1.26), 0.8f))

                // Ayak yere basar
                .beat(Beat.freeze(174))
                .beat(Beat.sound(174, "minecraft:block.anvil.land", 0.8f, 1.6f))
                .beat(Beat.fx(174, "dust", new Vec3(0, 0.08, 1.30), 1.3f))

                // S8 — direnis titremesi
                .beat(Beat.fx(190, "dust", new Vec3(0, 0.06, 1.28), 0.4f))
                .beat(Beat.fx(200, "dust", new Vec3(0, 0.06, 1.28), 0.4f))

                // S9 — ISINA KARSI ONE ADIM (210, 226)
                .beat(Beat.moveTarget(210, new Vec3(0, 0, 1.12), 0.022f))
                .beat(Beat.sound(212, "minecraft:block.gravel.step", 0.9f, 0.6f))
                .beat(Beat.fx(212, "dust", new Vec3(0, 0.08, 1.22), 0.9f))
                .beat(Beat.moveTarget(226, new Vec3(0, 0, 0.94), 0.022f))
                .beat(Beat.sound(228, "minecraft:block.gravel.step", 0.9f, 0.6f))
                .beat(Beat.fx(228, "dust", new Vec3(0, 0.08, 1.05), 0.9f))

                // S11 — el gozluge gider
                .beat(Beat.sound(266, "minecraft:block.beacon.ambient", 1.2f, 0.4f))
                .beat(Beat.fx(272, "charge", new Vec3(0, 1.62, 0), 1.4f))

                // S12 — VISOR CIKAR, ISIN KESILIR
                .beat(Beat.beamOff(280))
                .beat(Beat.freeze(280))
                .beat(Beat.sound(280, "minecraft:block.beacon.deactivate", 0.9f, 2.0f))
                .beat(Beat.darkness(280, 0.30f))
                .beat(Beat.fx(288, "charge", new Vec3(0, 1.66, 0), 2.6f))
                .beat(Beat.darkness(290, 0.18f))

                // S13 — MAXIMUM POWER (296)
                .beat(Beat.beam(296, BEAM_MAX))
                .beat(Beat.darkness(296, 0.04f))
                .beat(Beat.sound(296, "minecraft:entity.lightning_bolt.thunder", 2.0f, 0.6f))
                .beat(Beat.sound(296, "minecraft:entity.generic.explode", 1.6f, 0.4f))
                .beat(Beat.fx(296, "explode", new Vec3(0, 1.3, 0.3), 1.2f))
                .beat(Beat.fx(302, "charge", new Vec3(0, 1.66, 0), 3.0f))

                // S14-15 — DIRENIS KIRILIR, savrulur
                .beat(Beat.launchTarget(316,
                        new Vec3(0, 0, 0.94), new Vec3(0, 0, 3.60), 2.8f, 24))
                .beat(Beat.sound(316, "minecraft:entity.generic.explode", 1.4f, 0.8f))
                .beat(Beat.fx(322, "smoke", new Vec3(0, 1.0, 1.8), 0.9f))
                .beat(Beat.fx(330, "smoke", new Vec3(0, 1.1, 2.5), 0.9f))
                .beat(Beat.fx(334, "impact", new Vec3(0, 1.1, 3.0), 1.4f))

                // S16-17 — firlama, isin kesilir
                .beat(Beat.fx(340, "smoke", new Vec3(0, 1.1, 3.3), 1.0f))
                .beat(Beat.beamOff(346))
                .beat(Beat.fx(350, "smoke", new Vec3(0, 1.0, 3.6), 1.2f))

                // S18 — SISIN ICINDEKI PATLAMA. HASAR BURADA.
                .beat(Beat.fx(362, "charge", new Vec3(0, 1.1, 3.8), 0.5f))
                .beat(Beat.damage(366, 0.85f))
                .beat(Beat.fx(366, "explode", new Vec3(0, 1.0, 3.8), 1.6f))
                .beat(Beat.fx(367, "dust", new Vec3(0, 0.2, 3.8), 2.6f))
                .beat(Beat.darkness(366, 0.02f))
                // Ses ISIKTAN SONRA gelir — patlama uzakta
                .beat(Beat.sound(370, "minecraft:entity.generic.explode", 2.0f, 0.35f))
                .beat(Beat.sound(372, "minecraft:entity.lightning_bolt.thunder", 1.8f, 0.5f))
                .beat(Beat.fx(378, "smoke", new Vec3(0, 1.0, 3.8), 2.0f))
                .beat(Beat.darkness(380, 0.25f))

                // S19 — sis kapanir
                .beat(Beat.fx(392, "charge", new Vec3(0, 0.9, 3.7), 0.4f))
                .beat(Beat.fx(400, "charge", new Vec3(0, 1.2, 3.9), 0.3f))
                .beat(Beat.darkness(396, 0.45f))
                .beat(Beat.darkness(404, 0f))

                .build();
    }
}
