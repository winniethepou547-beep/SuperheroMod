package com.FIRNI.superheromod.client.gui;

import com.FIRNI.superheromod.client.render.anim.PoseStudio;
import com.FIRNI.superheromod.client.render.anim.StudioPose;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Poz studyosunun paneli. Sol tarafta durur; ekranin geri kalani ACIK kalir
 * ki arkadaki karakteri ucuncu sahistan izleyebilesin. Dunya donmez, klip
 * panel acikken de oynar.
 */
public class PoseStudioScreen extends Screen {

    private static final int PANEL_W = 186;
    private static final int PANEL_X = 6;
    private static final int ROW_H = 16;
    private static final int PITCH = 17;

    private static final String[] AXIS = {"X", "Y", "Z"};

    /** Kaydiraklarin kapsadigi aci araligi (derece). */
    private static final float ROT_RANGE = 180f;
    private static final float ELBOW_MAX = 170f;
    private static final int HOLD_MIN = 1;
    private static final int HOLD_MAX = 60;

    private static int selectedPart = StudioPose.RIGHT_ARM;
    private static int selectedFrame = -1;
    private static int holdTicks = 8;

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

    @Override
    protected void init() {
        super.init();

        StudioPose pose = PoseStudio.current();
        int y = 18;
        int half = (PANEL_W - 4) / 2;
        int rightX = PANEL_X + half + 4;

        // --- Parca secimi ---
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int part = row * 2 + col;
                int x = col == 0 ? PANEL_X : rightX;
                String label = (part == selectedPart ? "▶ " : "") + StudioPose.NAMES[part];
                addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    selectedPart = part;
                    rebuildWidgets();
                }).bounds(x, y + row * PITCH, half, ROW_H).build());
            }
        }
        y += 3 * PITCH + 3;

        // --- Secili parcanin uc ekseni ---
        for (int axis = 0; axis < 3; axis++) {
            final int ax = axis;
            addRenderableWidget(new PoseSlider(PANEL_X, y, PANEL_W, ROW_H,
                    rotToSlider(pose.rot[selectedPart][ax]),
                    v -> pose.rot[selectedPart][ax] = sliderToRot(v),
                    v -> AXIS[ax] + ": " + Math.round(Math.toDegrees(sliderToRot(v))) + "°"));
            y += PITCH;
        }

        // --- Dirsek (vanilla modelde olmayan eklem) ---
        addRenderableWidget(new PoseSlider(PANEL_X, y, PANEL_W, ROW_H,
                pose.elbow / (float) Math.toRadians(ELBOW_MAX),
                v -> pose.elbow = (float) (v * Math.toRadians(ELBOW_MAX)),
                v -> "Dirsek: " + Math.round(v * ELBOW_MAX) + "°"));
        y += PITCH + 3;

        // --- Kare suresi ---
        addRenderableWidget(new PoseSlider(PANEL_X, y, PANEL_W, ROW_H,
                (holdTicks - HOLD_MIN) / (float) (HOLD_MAX - HOLD_MIN),
                v -> {
                    holdTicks = HOLD_MIN + Math.round(v * (HOLD_MAX - HOLD_MIN));
                    if (selectedFrame >= 0 && selectedFrame < PoseStudio.clip().size()) {
                        PoseStudio.clip().get(selectedFrame).hold = holdTicks;
                    }
                },
                v -> "Sure: " + (HOLD_MIN + Math.round(v * (HOLD_MAX - HOLD_MIN))) + " tick"));
        y += PITCH + 3;

        // --- Kare islemleri ---
        addRenderableWidget(Button.builder(Component.literal("Kare Ekle"), b -> {
            PoseStudio.addKeyframe(holdTicks);
            selectedFrame = PoseStudio.clip().size() - 1;
        }).bounds(PANEL_X, y, half, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("Kareyi Sil"), b -> {
            PoseStudio.removeKeyframe(selectedFrame);
            selectedFrame = Math.min(selectedFrame, PoseStudio.clip().size() - 1);
        }).bounds(rightX, y, half, ROW_H).build());
        y += PITCH;

        addRenderableWidget(Button.builder(Component.literal("◀ Kare"), b -> {
            step(-1);
            rebuildWidgets();
        }).bounds(PANEL_X, y, half, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("Kare ▶"), b -> {
            step(1);
            rebuildWidgets();
        }).bounds(rightX, y, half, ROW_H).build());
        y += PITCH;

        addRenderableWidget(Button.builder(
                Component.literal(PoseStudio.isPlaying() ? "Durdur" : "Oynat"), b -> {
            PoseStudio.togglePlay();
            rebuildWidgets();
        }).bounds(PANEL_X, y, half, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("Klibi Temizle"), b -> {
            PoseStudio.clearClip();
            selectedFrame = -1;
            rebuildWidgets();
        }).bounds(rightX, y, half, ROW_H).build());
        y += PITCH;

        // --- Dosya ---
        addRenderableWidget(Button.builder(Component.literal("Kaydet"),
                b -> PoseStudio.save()).bounds(PANEL_X, y, half, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("Yukle"), b -> {
            PoseStudio.load();
            selectedFrame = PoseStudio.clip().isEmpty() ? -1 : 0;
            rebuildWidgets();
        }).bounds(rightX, y, half, ROW_H).build());
        y += PITCH;

        addRenderableWidget(Button.builder(Component.literal("Java Ver"),
                b -> PoseStudio.exportJava()).bounds(PANEL_X, y, half, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("Pozu Sifirla"), b -> {
            PoseStudio.current().reset();
            rebuildWidgets();
        }).bounds(rightX, y, half, ROW_H).build());
        y += PITCH;

        // --- Karakteri dondur (pozu her acidan gormek icin) ---
        addRenderableWidget(Button.builder(Component.literal("◀ Dondur"),
                b -> spin(-15f)).bounds(PANEL_X, y, half, ROW_H).build());

        addRenderableWidget(Button.builder(Component.literal("Dondur ▶"),
                b -> spin(15f)).bounds(rightX, y, half, ROW_H).build());
        y += PITCH;

        addRenderableWidget(Button.builder(Component.literal("Studyodan Cik"), b -> {
            PoseStudio.setActive(false);
            onClose();
        }).bounds(PANEL_X, y, PANEL_W, ROW_H).build());
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
    }

    /** Modeli yerinde cevirir; pozu profilden de gorebilmek icin. */
    private void spin(float degrees) {
        Player p = this.minecraft == null ? null : this.minecraft.player;
        if (p == null) return;
        float yr = p.getYRot() + degrees;
        p.setYRot(yr);
        p.yRotO = yr;
        p.setYBodyRot(yr);
        p.yBodyRotO = yr;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int panelBottom = 18 + 13 * PITCH + ROW_H + 6;
        g.fill(PANEL_X - 4, 4, PANEL_X + PANEL_W + 4, panelBottom, 0xD0101018);
        g.fill(PANEL_X - 4, 4, PANEL_X + PANEL_W + 4, 5, 0xFF6c5ce7);

        g.drawString(this.font, "§6§lPOZ STUDYOSU", PANEL_X, 8, 0xFFFFFF, false);

        super.render(g, mouseX, mouseY, partialTick);

        // Durum satiri — panelin altinda
        int size = PoseStudio.clip().size();
        String state = "§7Kare: §f" + (selectedFrame < 0 ? "-" : (selectedFrame + 1))
                + "§7/§f" + size
                + "§7  Toplam: §f" + PoseStudio.totalTicks() + " tick"
                + (PoseStudio.isPlaying() ? "  §aOYNUYOR" : "");
        g.drawString(this.font, state, PANEL_X, panelBottom + 4, 0xFFFFFF, false);

        g.drawString(this.font,
                "§8[P] paneli kapat §7- poz uzerinde kalir",
                PANEL_X, panelBottom + 15, 0xFFFFFF, false);
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
}
