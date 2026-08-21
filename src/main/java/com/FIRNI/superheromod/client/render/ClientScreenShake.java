package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * EKRAN SARSINTISI — genel sistem, sadece Colossus'a ozel degil.
 *
 * Fisk arastirmasindan cikan ders buydu: kalite hissi buyuk olcude kucuk
 * tepkilerden geliyor. Agir bir darbe olurken kameranin hic tepki vermemesi
 * darbeyi hafif gosteriyor.
 *
 * Sarsinti KAMERA ACISINA uygulaniyor, konuma degil. Konumu oynatmak duvarin
 * icine girme gibi sorunlar cikariyor; aci oynatmak ise her durumda guvenli.
 *
 * Siddet mesafeyle azaliyor — uzaktaki patlama daha az sarsmali.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ClientScreenShake {

    /** Her tick kalan siddetin ne kadari kaybolur. */
    private static final float DECAY = 0.82f;
    /** Siddetin dereceye cevrim katsayisi. */
    private static final float ANGLE_SCALE = 2.6f;

    private static float intensity = 0f;
    private static long seed = 0L;

    private ClientScreenShake() {}

    /** Dogrudan sarsinti ekler (0..1 arasi mantikli). */
    public static void add(float amount) {
        intensity = Math.min(1.6f, intensity + amount);
        seed = System.nanoTime();
    }

    /**
     * Kaynagi belli bir sarsinti — mesafeye gore zayiflar.
     *
     * @param falloffRange bu mesafede etki sifirlanir
     */
    public static void addFromSource(Vec3 source, float amount, float falloffRange) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double dist = mc.player.position().distanceTo(source);
        // Menzilin iki kati mesafeye kadar hissedilsin, sonra kesilsin
        double range = Math.max(1.0, falloffRange * 2.0);
        if (dist > range) return;

        float falloff = (float) (1.0 - dist / range);
        add(amount * falloff * falloff);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (intensity <= 0.001f) {
            intensity = 0f;
            return;
        }
        intensity *= DECAY;
    }

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (intensity <= 0.001f) return;

        // Sinematik kendi kamerasini surerken karismayalim
        if (com.FIRNI.superheromod.client.render.cinematic.CinematicClient.isRunning()) return;

        // Rastgele degil sinus toplami: rastgelelik titreme yapiyor, sinus
        // toplami gercek bir sarsintiya benziyor
        float t = (System.nanoTime() - seed) / 1.0E8f;
        float amp = intensity * ANGLE_SCALE;

        float yaw = (Mth.sin(t * 3.1f) + Mth.sin(t * 7.7f) * 0.6f) * amp;
        float pitch = (Mth.sin(t * 4.3f) + Mth.sin(t * 9.1f) * 0.5f) * amp * 0.7f;
        float roll = Mth.sin(t * 2.6f) * amp * 0.5f;

        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }
}
