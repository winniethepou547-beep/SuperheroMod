package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.client.render.anim.PoseStudio;
import com.FIRNI.superheromod.client.render.anim.StudioPose;
import com.FIRNI.superheromod.core.cinematic.Easing;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Poz studyosunun paneli. Sol tarafta durur; ekranin geri kalani ACIK kalir
 * ki arkadaki karakteri ucuncu sahistan izleyebilesin. Dunya donmez, klip
 * panel acikken de oynar.
 *
 * Yerlesim ekran yuksekligine gore sikisir; yazilarin ust uste binmemesi
 * icin durum satiri basliga, ipucu satiri panelin ALTINA alinmistir.
 */
public class PoseStudioScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_X = 6;
    private static final int TOP = 30;
    private static final int ROWS = 14;
    private static final int GAP = 3;

    private static final String[] AXIS = {"X", "Y", "Z"};

    /** Donus kaydiraklarinin kapsadigi aci araligi (derece). */
    private static final float ROT_RANGE = 180f;
    /** Oteleme araligi (model pikseli). */
    private static final float POS_RANGE = 10f;
    private static final float ELBOW_MAX = 170f;
    private static final int HOLD_MIN = 1;
    private static final int HOLD_MAX = 60;

    private static int selectedPart = StudioPose.RIGHT_ARM;
    private static int selectedFrame = -1;
    private static int holdTicks = 8;
    private static Easing easing = Easing.IN_OUT;
    /** false = donus kaydiraklari, true = oteleme kaydiraklari. */
    private static boolean translateMode;

    private int pitch;
    private int rowH;
    private int panelBottom;
    private int elbowRowY;

    public PoseStudioScreen() {
        super(Component.literal("Poz Studyosu"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;   // dunya donmeye devam etsin, klip oynasin
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // Ekrani karartma — arkadaki karakter net gorunmeli
    }

    private int rowY(int index) {
        return TOP + index * pitch;
    }

    @Override
    protected void init() {
        super.init();

        // Yerlesimi ekrana sigdir; kucuk ekranda satirlar birbirine girmesin
        pitch = Math.max(11, Math.min(16, (this.height - TOP - 26) / ROWS));
        rowH = pitch - 2;
        panelBottom = rowY(ROWS) + 4;

        StudioPose pose = PoseStudio.current();

        int colW = (PANEL_W - GAP * 2) / 3;
        int c0 = PANEL_X;
        int c1 = PANEL_X + colW + GAP;
        int c2 = PANEL_X + (colW + GAP) * 2;
        int[] cols = {c0, c1, c2};

        // --- Parca secimi: 3 sutun x 2 satir ---
        for (int part = 0; part < StudioPose.PARTS; part++) {
            final int p = part;
            int x = cols[part % 3];
            int y = rowY(part / 3);
            String label = (part == selectedPart ? "▶ " : "") + StudioPose.NAMES[part];

            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                selectedPart = p;
                rebuildWidgets();
            }).bounds(x, y, colW, rowH).build());
        }

        // --- Donus / oteleme kipi ---
        addRenderableWidget(Button.builder(
                Component.literal(translateMode ? "Kip: KONUM (oteleme)" : "Kip: DONUS (aci)"),
                b -> {
                    translateMode = !translateMode;
                    rebuildWidgets();
                }).bounds(PANEL_X, rowY(2), PANEL_W, rowH).build());

        // --- Secili parcanin uc ekseni ---
        for (int axis = 0; axis < 3; axis++) {
            final int ax = axis;
            if (translateMode) {
                addRenderableWidget(new PoseSlider(PANEL_X, rowY(3 + ax), PANEL_W, rowH,
                        posToSlider(pose.pos[selectedPart][ax]),
                        v -> pose.pos[selectedPart][ax] = sliderToPos(v),
                        v -> AXIS[ax] + " kaydir: "
                                + String.format("%.1f", sliderToPos(v)) + " px"));
            } else {
                addRenderableWidget(new PoseSlider(PANEL_X, rowY(3 + ax), PANEL_W, rowH,
                        rotToSlider(pose.rot[selectedPart][ax]),
                        v -> pose.rot[selectedPart][ax] = sliderToRot(v),
                        v -> AXIS[ax] + " dondur: "
                                + Math.round(Math.toDegrees(sliderToRot(v))) + "°"));
            }
        }

        // --- Eklem: sadece sag kolda var (BendableArm dirsegi) ---
        elbowRowY = rowY(6);
        if (selectedPart == StudioPose.RIGHT_ARM) {
            addRenderableWidget(new PoseSlider(PANEL_X, elbowRowY, PANEL_W, rowH,
                    pose.elbow / (float) Math.toRadians(ELBOW_MAX),
                    v -> pose.elbow = (float) (v * Math.toRadians(ELBOW_MAX)),
                    v -> "Dirsek: " + Math.round(v * ELBOW_MAX) + "°"));
        }

        // --- Kare suresi ---
        addRenderableWidget(new PoseSlider(PANEL_X, rowY(7), PANEL_W, rowH,
                (holdTicks - HOLD_MIN) / (float) (HOLD_MAX - HOLD_MIN),
                v -> {
                    holdTicks = HOLD_MIN + Math.round(v * (HOLD_MAX - HOLD_MIN));
                    if (selectedFrame >= 0 && selectedFrame < PoseStudio.clip().size()) {
                        PoseStudio.clip().get(selectedFrame).hold = holdTicks;
                    }
                },
                v -> "Sure: " + (HOLD_MIN + Math.round(v * (HOLD_MAX - HOLD_MIN))) + " tick"));

        // --- Gecis egrisi ---
        addRenderableWidget(Button.builder(
                Component.literal("Gecis: " + easing.name() + "  " + easingHint(easing)),
                b -> {
                    Easing[] all = Easing.values();
                    easing = all[(easing.ordinal() + 1) % all.length];
                    if (selectedFrame >= 0 && selectedFrame < PoseStudio.clip().size()) {
                        PoseStudio.clip().get(selectedFrame).ease = easing;
                    }
                    rebuildWidgets();
                }).bounds(PANEL_X, rowY(8), PANEL_W, rowH).build());

        // --- Kare islemleri ---
        addRenderableWidget(Button.builder(Component.literal("Kare Ekle"), b -> {
            PoseStudio.addKeyframe(holdTicks, easing);
            selectedFrame = PoseStudio.clip().size() - 1;
        }).bounds(c0, rowY(9), colW, rowH).build());

        addRenderableWidget(Button.builder(Component.literal("Kareyi Sil"), b -> {
            PoseStudio.removeKeyframe(selectedFrame);
            selectedFrame = Math.min(selectedFrame, PoseStudio.clip().size() - 1);
        }).bounds(c1, rowY(9), colW, rowH).build());

        addRenderableWidget(Button.builder(
                Component.literal(PoseStudio.isPlaying() ? "Durdur" : "Oynat"), b -> {
            PoseStudio.togglePlay();
            rebuildWidgets();
        }).bounds(c2, rowY(9), colW, rowH).build());

        addRenderableWidget(Button.builder(Component.literal("◀ Kare"), b -> {
            step(-1);
            rebuildWidgets();
        }).bounds(c0, rowY(10), colW, rowH).build());

        addRenderableWidget(Button.builder(Component.literal("Kare ▶"), b -> {
            step(1);
            rebuildWidgets();
        }).bounds(c1, rowY(10), colW, rowH).build());

        addRenderableWidget(Button.builder(Component.literal("Klibi Temizle"), b -> {
            PoseStudio.clearClip();
            selectedFrame = -1;
            rebuildWidgets();
        }).bounds(c2, rowY(10), colW, rowH).build());

        // --- Dosya ---
        addRenderableWidget(Button.builder(Component.literal("Kaydet"),
                b -> PoseStudio.save()).bounds(c0, rowY(11), colW, rowH).build());

        addRenderableWidget(Button.builder(Component.literal("Yukle"), b -> {
            PoseStudio.load();
            selectedFrame = PoseStudio.clip().isEmpty() ? -1 : 0;
            rebuildWidgets();
        }).bounds(c1, rowY(11), colW, rowH).build());

        addRenderableWidget(Button.builder(Component.literal("Java Ver"),
                b -> PoseStudio.exportJava()).bounds(c2, rowY(11), colW, rowH).build());

        // --- Modeli cevir (kamera ve dunya yerinde kalir) ---
        addRenderableWidget(Button.builder(Component.literal("◀ Dondur"),
                b -> PoseStudio.spinModel(-15f)).bounds(c0, rowY(12), colW, rowH).build());

        addRenderableWidget(Button.builder(Component.literal("Dondur ▶"),
                b -> PoseStudio.spinModel(15f)).bounds(c1, rowY(12), colW, rowH).build());

        addRenderableWidget(Button.builder(Component.literal("Pozu Sifirla"), b -> {
            PoseStudio.current().reset();
            rebuildWidgets();
        }).bounds(c2, rowY(12), colW, rowH).build());

        addRenderableWidget(Button.builder(Component.literal("Studyodan Cik"), b -> {
            PoseStudio.setActive(false);
            onClose();
        }).bounds(PANEL_X, rowY(13), PANEL_W, rowH).build());
    }

    private static String easingHint(Easing e) {
        return switch (e) {
            case LINEAR -> "§7(duz)";
            case IN -> "§7(yavas basla)";
            case OUT -> "§7(yavas bit)";
            case IN_OUT -> "§7(iki uc yumusak)";
            case SNAP -> "§7(sert vurus)";
            case SURGE -> "§7(patlayarak)";
        };
    }

    /** Kareler arasi gezinir ve o karenin pozunu kaydiraklara yukler. */
    private void step(int delta) {
        int size = PoseStudio.clip().size();
        if (size == 0) {
            selectedFrame = -1;
            return;
        }
        selectedFrame = selectedFrame < 0
                ? (delta > 0 ? 0 : size - 1)
                : Math.floorMod(selectedFrame + delta, size);

        PoseStudio.loadKeyframe(selectedFrame);
        holdTicks = PoseStudio.clip().get(selectedFrame).hold;
        easing = PoseStudio.clip().get(selectedFrame).ease;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(PANEL_X - 4, 2, PANEL_X + PANEL_W + 4, panelBottom, 0xD0101018);
        g.fill(PANEL_X - 4, 2, PANEL_X + PANEL_W + 4, 3, 0xFF6c5ce7);

        // Baslik ve durum — panelin USTUNDE, dugmelerle cakismaz
        int size = PoseStudio.clip().size();
        g.drawString(this.font, "§6§lPOZ STUDYOSU §8| §f" + StudioPose.NAMES[selectedPart],
                PANEL_X, 7, 0xFFFFFF, false);
        g.drawString(this.font,
                "§7Kare §f" + (selectedFrame < 0 ? "-" : (selectedFrame + 1))
                        + "§7/§f" + size
                        + " §8| §7toplam §f" + PoseStudio.totalTicks() + "t"
                        + (PoseStudio.isPlaying() ? " §8| §aOYNUYOR" : ""),
                PANEL_X, 18, 0xFFFFFF, false);

        super.render(g, mouseX, mouseY, partialTick);

        // Eklemi olmayan parcada dirsek satiri bos kalir — neden oldugunu yaz
        if (selectedPart != StudioPose.RIGHT_ARM) {
            g.drawString(this.font,
                    "§8" + StudioPose.NAMES[selectedPart] + " icin eklem yok "
                            + "(sadece sag kolda dirsek var)",
                    PANEL_X + 2, elbowRowY + (rowH - 8) / 2, 0xFFFFFF, false);
        }

        g.drawString(this.font, "§8[P] paneli kapat — poz uzerinde kalir",
                PANEL_X, panelBottom + 4, 0xFFFFFF, false);
    }

    // ------------------------------------------------------------------

    /** Kaydirak: 0..1 degerini alir, geri cagirma ile poza yazar. */
    private static class PoseSlider extends AbstractSliderButton {

        interface Apply { void accept(float value); }
        interface Label { String of(float value); }

        private final Apply apply;
        private final Label label;

        PoseSlider(int x, int y, int w, int h, float initial, Apply apply, Label label) {
            super(x, y, w, h, Component.empty(), clamp01(initial));
            this.apply = apply;
            this.label = label;
            updateMessage();
        }

        private static double clamp01(float v) {
            return v < 0f ? 0f : (v > 1f ? 1f : v);
        }

        @Override
        protected void updateMessage() {
            if (label != null) setMessage(Component.literal(label.of((float) this.value)));
        }

        @Override
        protected void applyValue() {
            if (apply != null) apply.accept((float) this.value);
        }
    }

    private static float sliderToRot(float sliderValue) {
        return (float) Math.toRadians(sliderValue * (ROT_RANGE * 2) - ROT_RANGE);
    }

    private static float rotToSlider(float radians) {
        return (float) ((Math.toDegrees(radians) + ROT_RANGE) / (ROT_RANGE * 2));
    }

    private static float sliderToPos(float sliderValue) {
        return sliderValue * (POS_RANGE * 2) - POS_RANGE;
    }

    private static float posToSlider(float pixels) {
        return (pixels + POS_RANGE) / (POS_RANGE * 2);
    }
}
