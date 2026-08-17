package com.FIRNI.superheromod.client.render.anim;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.gui.PoseStudioScreen;
import com.FIRNI.superheromod.core.cinematic.Easing;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
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
 * POZ STUDYOSU — karakter modelini oyun icinde ucuncu sahistan animasyona
 * cevirmek icin.
 *
 * Kullanim:
 *   [P]  studyoyu ac/kapat. Acilinca kamera ucuncu sahsa gecer, arkadaki
 *        karakter yaptigin her degisikligi ANINDA gosterir.
 *   Panelden parcayi sec, X/Y/Z kaydiraklariyla dondur, dirsegi ayarla.
 *   "Kare Ekle" ile o pozu klibe ekle, "Oynat" ile klibi dongude izle,
 *   "Java Ver" ile hazir kodu run/pose_animation.txt dosyasina yaz.
 *
 * Klip run/pose_clip.txt dosyasina kaydedilip geri yuklenebilir; boylece
 * animasyon uzerinde birden fazla oturumda calisabilirsin.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class PoseStudio {

    public static final KeyMapping KEY_STUDIO = new KeyMapping(
            "key.superheromod.pose_studio", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.superheromod");

    /** Klipteki tek kare: poz + bir sonrakine gecis suresi ve egrisi. */
    public static final class Keyframe {
        public final StudioPose pose;
        public int hold;
        /** Bu kareden bir sonrakine gecisin yumusatma egrisi. */
        public Easing ease;

        public Keyframe(StudioPose pose, int hold, Easing ease) {
            this.pose = pose;
            this.hold = Math.max(1, hold);
            this.ease = ease == null ? Easing.IN_OUT : ease;
        }
    }

    private static final String CLIP_FILE = "pose_clip.txt";
    private static final String JAVA_FILE = "pose_animation.txt";

    private static boolean active;
    private static final StudioPose current = new StudioPose();
    private static final StudioPose blended = new StudioPose();
    private static final List<Keyframe> clip = new ArrayList<>();

    private static boolean playing;
    private static float playhead;

    /**
     * Modelin kendi ekseninde cevrilme acisi (derece). Oyuncunun bakis acisina
     * DOKUNMAZ — sadece cizim sirasinda model dondurulur, boylece kamera ve
     * dunya sabit kalirken karakteri her acidan gorebilirsin.
     */
    private static float modelYaw;
    private static boolean pushedRotation;

    private static CameraType previousCamera;

    private PoseStudio() {}

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class Registration {
        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(KEY_STUDIO);
        }
    }

    // ------------------------------------------------------------------
    // Durum
    // ------------------------------------------------------------------

    public static boolean isActive() { return active; }
    public static StudioPose current() { return current; }
    public static List<Keyframe> clip() { return clip; }
    public static boolean isPlaying() { return playing; }
    public static float playhead() { return playhead; }

    public static void setActive(boolean value) {
        if (active == value) return;
        active = value;

        Minecraft mc = Minecraft.getInstance();
        if (active) {
            // Poz ancak ucuncu sahistan gorulebilir
            previousCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            playing = false;
            if (previousCamera != null) mc.options.setCameraType(previousCamera);
        }
    }

    public static float modelYaw() { return modelYaw; }

    public static void spinModel(float degrees) {
        modelYaw = (modelYaw + degrees) % 360f;
    }

    /**
     * Modeli kendi ekseninde cevirir. Oyuncunun yRot'una dokunmadigimiz icin
     * kamera ve dunya yerinde kalir — sadece cizilen model doner.
     */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        pushedRotation = false;
        if (!active || modelYaw == 0f || !isLocal(event.getEntity())) return;

        event.getPoseStack().pushPose();
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(modelYaw));
        pushedRotation = true;
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!pushedRotation) return;
        pushedRotation = false;
        event.getPoseStack().popPose();
    }

    public static void togglePlay() {
        if (clip.size() < 2) {
            playing = false;
            return;
        }
        playing = !playing;
        if (playing) playhead = 0f;
    }

    public static void addKeyframe(int hold, Easing ease) {
        clip.add(new Keyframe(current.copy(), hold, ease));
    }

    public static void removeKeyframe(int index) {
        if (index >= 0 && index < clip.size()) clip.remove(index);
        if (clip.size() < 2) playing = false;
    }

    /** Secili kareyi duzenlemek uzere kaydiraklara geri yukler. */
    public static void loadKeyframe(int index) {
        if (index < 0 || index >= clip.size()) return;
        current.set(clip.get(index).pose);
    }

    public static void clearClip() {
        clip.clear();
        playing = false;
    }

    public static int totalTicks() {
        int total = 0;
        for (int i = 0; i < clip.size() - 1; i++) total += clip.get(i).hold;
        return Math.max(1, total);
    }

    // ------------------------------------------------------------------
    // Tick / oynatma
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();

        while (KEY_STUDIO.consumeClick()) {
            if (mc.screen == null) {
                setActive(true);
                mc.setScreen(new PoseStudioScreen());
            } else if (mc.screen instanceof PoseStudioScreen) {
                mc.setScreen(null);
            }
        }

        if (!active || !playing || clip.size() < 2) return;

        playhead += 1f;
        if (playhead >= totalTicks()) playhead = 0f;
    }

    /** Oynatiliyorsa ara poz, degilse kaydiraklardaki poz. */
    private static StudioPose effectivePose() {
        if (!playing || clip.size() < 2) return current;

        float t = playhead;
        for (int i = 0; i < clip.size() - 1; i++) {
            Keyframe k = clip.get(i);
            if (t < k.hold) {
                // Gecis egrisi kareye ait; her gecis farkli hissettirilebilir
                float eased = k.ease.apply(t / k.hold);
                StudioPose.lerp(k.pose, clip.get(i + 1).pose, eased, blended);
                return blended;
            }
            t -= k.hold;
        }
        return clip.get(clip.size() - 1).pose;
    }

    // ------------------------------------------------------------------
    // Modele uygulama
    // ------------------------------------------------------------------

    /** Studyo devredeyse yerel oyuncunun modelini pozlar. */
    public static boolean apply(PlayerModel<?> model, LivingEntity entity) {
        if (!active || !isLocal(entity)) return false;

        StudioPose pose = effectivePose();

        setPart(model.head, pose, StudioPose.HEAD);
        setPart(model.body, pose, StudioPose.BODY);
        setPart(model.rightArm, pose, StudioPose.RIGHT_ARM);
        setPart(model.leftArm, pose, StudioPose.LEFT_ARM);
        setPart(model.rightLeg, pose, StudioPose.RIGHT_LEG);
        setPart(model.leftLeg, pose, StudioPose.LEFT_LEG);

        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);

        // Dirsek bukuluyorsa vanilla tek parca kol cizilmez; yerine
        // BendableArmLayer ust kol + on kolu ayri cizer.
        boolean bending = pose.elbow > 0.001f;
        model.rightArm.skipDraw = bending;
        model.rightSleeve.skipDraw = bending;

        return true;
    }

    /** BendableArmLayer icin: studyo devrede degilse -1 doner. */
    public static float elbowFor(Player player) {
        if (!active || !isLocal(player)) return -1f;
        return effectivePose().elbow;
    }

    /**
     * Parcayi once varsayilan duruma dondurur, sonra pozu uygular.
     * Oteleme varsayilan konumun UZERINE eklenir; boylece deger 0 oldugunda
     * parca tam yerinde kalir.
     */
    private static void setPart(ModelPart part, StudioPose pose, int index) {
        part.resetPose();

        part.xRot = pose.rot[index][0];
        part.yRot = pose.rot[index][1];
        part.zRot = pose.rot[index][2];

        part.x += pose.pos[index][0];
        part.y += pose.pos[index][1];
        part.z += pose.pos[index][2];
    }

    private static boolean isLocal(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entity.getUUID().equals(mc.player.getUUID());
    }

    // ------------------------------------------------------------------
    // Kaydetme / yukleme / disa aktarma
    // ------------------------------------------------------------------

    public static void save() {
        StringBuilder sb = new StringBuilder();
        sb.append("# PoseStudio klibi. Satir: sure, 6 parca donus xyz, ")
          .append("6 parca oteleme xyz, dirsek, gecis egrisi\n")
          .append("# Parca sirasi: kafa, govde, sag kol, sol kol, sag bacak, sol bacak\n");

        for (Keyframe k : clip) {
            float[] row = k.pose.toRow(k.hold);
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(String.format(java.util.Locale.ROOT, "%.4f", row[i]));
            }
            sb.append(',').append(k.ease.name()).append('\n');
        }

        write(CLIP_FILE, sb.toString(), clip.size() + " kare kaydedildi");
    }

    public static void load() {
        Path path = Paths.get(CLIP_FILE).toAbsolutePath();
        if (!Files.exists(path)) {
            say(ChatFormatting.RED, "Dosya yok: " + path);
            return;
        }

        try {
            List<String> lines = Files.readAllLines(path);
            List<Keyframe> loaded = new ArrayList<>();

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

                String[] parts = trimmed.split(",");
                if (parts.length < StudioPose.ROW_SIZE) continue;

                float[] row = new float[StudioPose.ROW_SIZE];
                for (int i = 0; i < StudioPose.ROW_SIZE; i++) {
                    row[i] = Float.parseFloat(parts[i].trim());
                }

                Easing ease = Easing.IN_OUT;
                if (parts.length > StudioPose.ROW_SIZE) {
                    try {
                        ease = Easing.valueOf(parts[StudioPose.ROW_SIZE].trim());
                    } catch (IllegalArgumentException ignored) {
                        // Bilinmeyen egri adi -> varsayilan
                    }
                }

                StudioPose pose = new StudioPose();
                int hold = pose.fromRow(row);
                loaded.add(new Keyframe(pose, hold, ease));
            }

            clip.clear();
            clip.addAll(loaded);
            playing = false;
            if (!clip.isEmpty()) current.set(clip.get(0).pose);

            say(ChatFormatting.GREEN, clip.size() + " kare yuklendi.");
        } catch (IOException | NumberFormatException e) {
            say(ChatFormatting.RED, "Okunamadi: " + e.getMessage());
        }
    }

    /** Klibi dogrudan koda yapistirilabilir Java dizisi olarak yazar. */
    public static void exportJava() {
        if (clip.isEmpty()) {
            say(ChatFormatting.RED, "Klip bos.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// PoseStudio ciktisi — ").append(clip.size()).append(" kare.\n")
          .append("// Satir duzeni: sure(tick), 6 parca DONUS xyz, 6 parca OTELEME xyz, dirsek\n")
          .append("// Parca sirasi: kafa, govde, sag kol, sol kol, sag bacak, sol bacak\n")
          .append("// Donus RADYAN, oteleme MODEL PIKSELI. StudioPose.fromRow(...) ile okunur.\n")
          .append("private static final float[][] CLIP = {\n");

        for (Keyframe k : clip) {
            float[] row = k.pose.toRow(k.hold);
            sb.append("        {");
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format(java.util.Locale.ROOT, "%.3ff", row[i]));
            }
            sb.append("},   // gecis: ").append(k.ease.name()).append('\n');
        }

        sb.append("};\n\n")
          .append("// Kare basina gecis egrileri (yukaridaki sira ile):\n")
          .append("private static final Easing[] CLIP_EASING = {\n        ");

        for (int i = 0; i < clip.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("Easing.").append(clip.get(i).ease.name());
        }
        sb.append("\n};\n");

        write(JAVA_FILE, sb.toString(), "Java klibi yazildi");
    }

    private static void write(String file, String content, String okMessage) {
        try {
            Path out = Paths.get(file).toAbsolutePath();
            Files.writeString(out, content);
            say(ChatFormatting.GREEN, okMessage + " -> " + out);
        } catch (IOException e) {
            say(ChatFormatting.RED, "Yazilamadi: " + e.getMessage());
        }
    }

    private static void say(ChatFormatting color, String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.displayClientMessage(
                Component.literal("[Poz] " + msg).withStyle(color), false);
    }
}
