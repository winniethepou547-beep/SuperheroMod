package com.FIRNI.superheromod.client.render.cinematic;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.cinematic.StageFrame;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * KAMERA KAYDEDICI — sinematik cekimlerini oyun icinde elle belirlemek icin.
 *
 * Kullanim:
 *   1) Cyclops'un duracagi yere gec, hedefe DON, [N] -> sahne capasi kurulur
 *   2) Spectator'a gec (/gamemode spectator), kamerayi istedigin yere goturt
 *   3) [K] -> o kadraji cekim olarak kaydeder (konum + bakis + FOV)
 *   4) Butun cekimler bitince [L] -> hazir Java kodu dosyaya yazilir
 *
 * Cekimler SAHNE UZAYINA cevrilerek kaydedilir; bu yuzden kayit nerede
 * yapilirsa yapilsin sinematik her yerde ayni kadraji verir.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class CameraRecorder {

    public static final KeyMapping KEY_ANCHOR = new KeyMapping(
            "key.superheromod.cine_anchor", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N, "key.categories.superheromod");

    public static final KeyMapping KEY_CAPTURE = new KeyMapping(
            "key.superheromod.cine_capture", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.superheromod");

    public static final KeyMapping KEY_EXPORT = new KeyMapping(
            "key.superheromod.cine_export", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L, "key.categories.superheromod");

    private record Frame(Vec3 localPos, Vec3 localLook, float distance) {}

    private static StageFrame stage;
    private static Vec3 anchorPos;
    private static final List<Frame> frames = new ArrayList<>();

    private CameraRecorder() {}

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class Registration {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(KEY_ANCHOR);
            event.register(KEY_CAPTURE);
            event.register(KEY_EXPORT);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        while (KEY_ANCHOR.consumeClick()) setAnchor(mc);
        while (KEY_CAPTURE.consumeClick()) capture(mc);
        while (KEY_EXPORT.consumeClick()) export(mc);
    }

    /** Sahne capasini kurar: duruyor oldugun yer Cyclops, baktigin yon ileri. */
    private static void setAnchor(Minecraft mc) {
        anchorPos = mc.player.position();
        Vec3 look = mc.player.getLookAngle();

        // Hedefin 10 blok ileride oldugunu varsayiyoruz (span referansi)
        Vec3 assumedTarget = anchorPos.add(look.x * 10, 0, look.z * 10);
        stage = StageFrame.between(anchorPos, assumedTarget);
        frames.clear();

        say(mc, ChatFormatting.GREEN, "Sahne capasi kuruldu. Span = 10 blok "
                + "(z=1.0 hedefin yeri). Simdi [K] ile kadraj yakala.");
    }

    /** O anki kamera konumunu ve bakisini cekim olarak kaydeder. */
    private static void capture(Minecraft mc) {
        if (stage == null) {
            say(mc, ChatFormatting.RED, "Once [N] ile sahne capasi kur.");
            return;
        }

        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 look = new Vec3(mc.gameRenderer.getMainCamera().getLookVector());

        Vec3 local = toLocal(camPos);
        // Bakis noktasi: kameranin 4 blok onu
        Vec3 lookPoint = toLocal(camPos.add(look.scale(4.0)));

        frames.add(new Frame(local, lookPoint, (float) camPos.distanceTo(anchorPos)));

        say(mc, ChatFormatting.AQUA, "Cekim " + frames.size() + " kaydedildi  "
                + fmt(local) + "  (capaya " + String.format("%.1f", camPos.distanceTo(anchorPos)) + " blok)");
    }

    /** Kaydedilen cekimleri hazir Java kodu olarak dosyaya yazar. */
    private static void export(Minecraft mc) {
        if (frames.isEmpty()) {
            say(mc, ChatFormatting.RED, "Kaydedilmis cekim yok.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// CameraRecorder ciktisi — ").append(frames.size())
          .append(" cekim. Sureleri ve gecisleri kendine gore ayarla.\n\n");

        for (int i = 0; i < frames.size(); i++) {
            Frame f = frames.get(i);
            sb.append(String.format(
                    "// %02d  (capaya %.1f blok)%n" +
                    ".shot(Shot.of(%d).cut().ease(Easing.IN_OUT)%n" +
                    "        .at(%.2f, %.2f, %.2f)%n" +
                    "        .lookAtFixed(new Vec3(%.2f, %.2f, %.2f))%n" +
                    "        .fov(70f)%n" +
                    "        .build())%n%n",
                    i + 1, f.distance(), 8,
                    f.localPos().x, f.localPos().y, f.localPos().z,
                    f.localLook().x, f.localLook().y, f.localLook().z));
        }

        try {
            Path out = Paths.get("cinematic_shots.txt").toAbsolutePath();
            Files.writeString(out, sb.toString());
            say(mc, ChatFormatting.GREEN, "Yazildi: " + out);
            say(mc, ChatFormatting.GRAY,
                    "lookAtFixed yerine lookAtTarget(1.0) / lookAtAttacker(1.6) kullanabilirsin.");
        } catch (IOException e) {
            say(mc, ChatFormatting.RED, "Yazilamadi: " + e.getMessage());
        }
    }

    /** Dunya konumunu sahne uzayina cevirir (z hedefe oran olarak). */
    private static Vec3 toLocal(Vec3 world) {
        Vec3 d = world.subtract(anchorPos);
        double x = d.dot(stage.right());
        double y = d.dot(stage.up());
        double z = d.dot(stage.forward()) / stage.span();
        return new Vec3(x, y, z);
    }

    private static String fmt(Vec3 v) {
        return String.format("x=%.2f y=%.2f z=%.2f", v.x, v.y, v.z);
    }

    private static void say(Minecraft mc, ChatFormatting color, String msg) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(
                Component.literal("[Cine] " + msg).withStyle(color), false);
    }
}
