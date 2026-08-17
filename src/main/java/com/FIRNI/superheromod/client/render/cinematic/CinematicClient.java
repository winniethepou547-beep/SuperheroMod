package com.FIRNI.superheromod.client.render.cinematic;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.cinematic.CinematicDefinition;
import com.FIRNI.superheromod.core.cinematic.CinematicRegistry;
import com.FIRNI.superheromod.core.cinematic.Shot;
import com.FIRNI.superheromod.core.cinematic.StageFrame;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * SINEMATIK ISTEMCISI — kamerayi cekim tanimlarindan surer.
 *
 * Sunucu sadece "hangi sinematik + kacinci tick + sahne nerede" gonderir;
 * kamera konumu, FOV, sarsinti ve gecisler burada hesaplanir. Boylece kamera
 * kare hizinda akici olur, 20Hz tick'e takilmaz.
 *
 * Sadece katilimcilarin kamerasi ele alinir; digerleri normal oynar.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class CinematicClient {

    private static boolean active = false;
    private static CinematicDefinition def;
    private static int attackerId = -1;
    private static int targetId = -1;
    private static Vec3 stageOrigin = Vec3.ZERO;
    private static Vec3 stageForward = new Vec3(0, 0, 1);
    private static float darkness = 0f;
    /** Saldiran-hedef mesafesi. Agdan gelir; iki noktadan yeniden
     *  hesaplamak yanlis olcek verip kadraji bozuyordu. */
    private static float span = 1.5f;

    private static int serverTick = 0;
    private static long serverTickMs = 0;
    private static long lastPacketMs = 0;

    // Gecis durumu
    private static Vec3 heldCam, heldLook;
    private static int lastShotIndex = -1;
    private static float blend = 1f;
    private static float fov = 70f;

    private static final Random SHAKE = new Random();

    private static Field cameraPosField;
    private static boolean fieldResolved = false;

    private CinematicClient() {}

    // ------------------------------------------------------------------
    // Ag girisi
    // ------------------------------------------------------------------

    public static void update(int cinematicIndex, int tick, int attacker, int target,
                              Vec3 origin, Vec3 forward, float dark, float spanIn) {
        CinematicDefinition d = CinematicRegistry.byIndex(cinematicIndex);
        if (d == null) return;

        boolean fresh = !active || d != def;

        active = true;
        def = d;
        attackerId = attacker;
        targetId = target;
        stageOrigin = origin;
        stageForward = forward.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : forward.normalize();
        darkness = dark;
        span = spanIn;

        serverTick = tick;
        serverTickMs = System.currentTimeMillis();
        lastPacketMs = serverTickMs;

        if (fresh) reset();
    }

    public static void stop() {
        active = false;
        def = null;
        reset();
    }

    public static boolean isRunning() {
        // FAILSAFE: paket akisi kesilirse sinematikte takili kalmayalim
        if (active && System.currentTimeMillis() - lastPacketMs > 1500) {
            stop();
        }
        return active && def != null;
    }

    private static void reset() {
        heldCam = null;
        heldLook = null;
        lastShotIndex = -1;
        blend = 1f;
        fov = 70f;
    }

    // ------------------------------------------------------------------
    // Kamera
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!shouldDrive()) return;

        Minecraft mc = Minecraft.getInstance();
        float partial = (float) event.getPartialTick();

        Entity attacker = resolve(attackerId);
        Entity target = resolve(targetId);
        if (attacker == null || target == null) return;

        Vec3 aPos = attacker.getPosition(partial);
        Vec3 tPos = target.getPosition(partial);

        // Sahne uzayi: sunucudan gelen origin + forward ile kurulur.
        // Boylece koreografi dunya konumundan bagimsiz.
        StageFrame stage = stageFrom(aPos);

        CinematicDefinition.Cursor cursor = def.cursorAt(timelineTick());
        Shot shot = cursor.shot();
        float p = shot.easing.apply(cursor.progress());

        Vec3 localCam = lerp(shot.fromPos, shot.toPos, p);
        Vec3 camPos = stage.toWorldSpan(localCam);
        Vec3 lookAt = resolveLook(shot, stage, aPos, tPos);

        // Sarsinti
        float shake = Mth.lerp(p, shot.shakeStart, shot.shakeEnd);
        if (shake > 0.001f) {
            double s = shake * 0.09;
            camPos = camPos.add(
                    (SHAKE.nextDouble() - 0.5) * s,
                    (SHAKE.nextDouble() - 0.5) * s,
                    (SHAKE.nextDouble() - 0.5) * s);
        }

        // Gecis: CUT aninda otur, SMOOTH kisa surede harmanla
        boolean shotChanged = cursor.index() != lastShotIndex;
        if (heldCam == null || (shotChanged && shot.transition == Shot.Transition.CUT)) {
            heldCam = camPos;
            heldLook = lookAt;
            blend = 1f;
        } else if (shotChanged) {
            blend = 0f;
        }
        lastShotIndex = cursor.index();

        if (blend < 1f) {
            blend = Math.min(1f, blend + 0.12f);
            heldCam = heldCam.add(camPos.subtract(heldCam).scale(blend));
            heldLook = heldLook.add(lookAt.subtract(heldLook).scale(blend));
        } else {
            // Gecikme sifir: cekimin istedigi yerde tam dur
            heldCam = camPos;
            heldLook = lookAt;
        }

        float wantFov = Mth.lerp(p, shot.fovStart, shot.fovEnd);
        fov += (wantFov - fov) * 0.3f;

        setCameraPosition(event.getCamera(), heldCam);

        Vec3 dir = heldLook.subtract(heldCam);
        if (dir.lengthSqr() < 1.0E-6) return;
        dir = dir.normalize();

        event.setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        event.setPitch((float) Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0))));
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if (!shouldDrive()) return;
        event.setNewFovModifier(fov / 70f);
    }

    /** Sinema bantlari + karanlik katmani. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        if (!shouldDrive()) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gui = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // Ekran karartma ve sinema bantlari kaldirildi — sahne kamerayla
        // anlatiliyor, ekrani siyahla kapatarak degil.
        if (!def.letterbox) return;

        // Sadece giris/cikista cok kisa bir kararma ile gecis
        float t = timelineTick();
        float fade = 0f;
        if (t < 3f) fade = 1f - t / 3f;
        else if (t > def.totalTicks - 4f) fade = (t - (def.totalTicks - 4f)) / 4f;

        if (fade > 0.01f) {
            int alpha = (int) (Mth.clamp(fade, 0f, 1f) * 255) << 24;
            gui.fill(0, 0, w, h, alpha);
        }
    }

    // ------------------------------------------------------------------
    // Yardimcilar
    // ------------------------------------------------------------------

    private static boolean shouldDrive() {
        if (!isRunning()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        // Sadece katilimcilarin kamerasi ele alinir
        int id = mc.player.getId();
        return id == attackerId || id == targetId;
    }

    /** Sunucu tick'i 20Hz; ekran daha hizli. Kesirli zaman cizgisi uretilir. */
    private static float timelineTick() {
        long delta = System.currentTimeMillis() - serverTickMs;
        return serverTick + Math.min(1.6f, delta / 50f);
    }

    /** Sunucunun kurdugu ekseni birebir yeniden kurar (span dahil). */
    private static StageFrame stageFrom(Vec3 attackerPos) {
        return StageFrame.of(stageOrigin, stageForward, span);
    }

    private static Vec3 resolveLook(Shot shot, StageFrame stage, Vec3 aPos, Vec3 tPos) {
        return switch (shot.lookTarget) {
            case ATTACKER -> aPos.add(shot.lookOffset);
            case TARGET -> tPos.add(shot.lookOffset);
            case MIDPOINT -> aPos.add(tPos.subtract(aPos).scale(0.5)).add(shot.lookOffset);
            case FIXED -> stage.toWorldSpan(shot.lookOffset);
        };
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, float t) {
        return a.add(b.subtract(a).scale(t));
    }

    private static Entity resolve(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || id < 0) return null;
        return mc.level.getEntity(id);
    }

    private static void setCameraPosition(Camera camera, Vec3 pos) {
        if (!fieldResolved) {
            fieldResolved = true;
            try {
                cameraPosField = Camera.class.getDeclaredField("position");
                cameraPosField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                for (Field f : Camera.class.getDeclaredFields()) {
                    if (f.getType() == Vec3.class) {
                        cameraPosField = f;
                        cameraPosField.setAccessible(true);
                        break;
                    }
                }
            }
        }
        if (cameraPosField != null) {
            try {
                cameraPosField.set(camera, pos);
            } catch (Exception ignored) {}
        }
    }
}
