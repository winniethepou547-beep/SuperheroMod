package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.heroes.sandman.ColossusCrystal;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;

/**
 * SAND COLOSSUS KAMERASI — Cyclops ultisindeki gibi ucuncu sahsa gecer, ama
 * cok daha GERIDEN bakar.
 *
 * Sebep: dev 10 blok boyunda. Vanilla ucuncu sahis mesafesi 4 blok oldugu
 * icin o mesafede sadece devin belinden asagisi kadraja giriyor. Kamera
 * geriye ve yukari cekilerek figurun tamami gorunur hale getiriliyor.
 *
 * Kamera konumu dogrudan yaziliyor (Camera.position alani), cunku Forge
 * 1.20.1'de ucuncu sahis MESAFESI icin bir olay yok — sadece aci icin var.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ColossusCameraHandler {

    /** Kameranin geriye cekilecegi ek mesafe (blok). */
    private static final float EXTRA_DISTANCE = 12.0f;
    /** Kameranin yukari cikacagi miktar — devin kafasi kadraja girsin. */
    private static final float EXTRA_HEIGHT = ColossusCrystal.COLOSSUS_HEIGHT * 0.42f;

    /** Gecis yumusak olsun; forma girince kamera birden firlamasin. */
    private static float blend = 0f;

    private static Field cameraPosField;
    private static boolean fieldResolved = false;

    private ColossusCameraHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean colossus = ClientColossusData.isLocalColossus();

        // Forma girildigi an ucuncu sahsa al — Cyclops ultisiyle ayni davranis
        if (ClientColossusData.consumeActivation()) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }

        // Cikista kamera aniden yerine oturmasin diye harmanlama
        blend = colossus
                ? Math.min(1f, blend + 0.045f)
                : Math.max(0f, blend - 0.06f);
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (blend <= 0.001f) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) return;

        Camera camera = event.getCamera();
        Vec3 look = new Vec3(camera.getLookVector());
        if (look.lengthSqr() < 1.0E-6) return;

        // Bakis yonunun TERSINE geri cek + yukari kaldir
        float t = Mth.clamp(blend, 0f, 1f);
        Vec3 pushed = camera.getPosition()
                .subtract(look.normalize().scale(EXTRA_DISTANCE * t))
                .add(0, EXTRA_HEIGHT * t, 0);

        setCameraPosition(camera, pushed);
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
        if (cameraPosField == null) return;

        try {
            cameraPosField.set(camera, pos);
        } catch (Exception ignored) {
            // Kamera ayarlanamazsa oyun normal ucuncu sahista devam etsin
        }
    }
}
