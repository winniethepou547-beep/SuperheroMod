package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.zed.ExecutionFx;
import com.FIRNI.superheromod.client.render.zed.ExecutionPath;
import com.FIRNI.superheromod.heroes.zed.ZedExecutionSession;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.heroes.zed.ZedAction.*;

/**
 * SHADOW EXECUTION (Zed's X, 16.8 s), shot in the real world. ExecutionFx draws the bodies, the shadow
 * and the light from ExecutionPath; this film is the camera (placed moment by moment by the path: lag
 * behind him, a low swing round with an overshoot, the shadow crossing the lens), the event-driven
 * kicks, the sound (silence for the held breath before the shadows strike), and the picture over it:
 * the world darkening at its edges as the shadow takes over, and the short red bloom of the flash.
 */
final class ShadowExecutionFilm implements Film {
    static final String ID = ZedExecutionSession.ID;
    private final int attacker;
    private ExecutionPath path;
    private final Segment[] segments;

    ShadowExecutionFilm(int attacker) {
        this.attacker = attacker;
        // The camera comes from view(); the shot is only there for the engine's bookkeeping.
        segments = new Segment[]{new Segment(0, ULT_TOTAL, new FilmShot[]{FilmShot.of(0, ULT_TOTAL).path(new Vec3(3, 1.6, 0)).look(new Vec3(0, 1.1, 2)).fov(52, 50).build()}, null, 0, 0)};
    }
    private ExecutionPath path() {
        if (path == null) path = ExecutionFx.pathFor(attacker);
        return path != null ? path : new ExecutionPath(3, 0);
    }
    private FilmSessionClient.State state() { return FilmSessionClient.get(attacker); }
    @Override public Vec3 origin() { var s = state(); return s == null ? Vec3.ZERO : s.anchor; }
    @Override public Vec3 forward() { var s = state(); return s == null ? new Vec3(0, 0, 1) : FilmSessionClient.forward(s); }
    @Override public float duration() { return ULT_TOTAL; }
    @Override public Segment[] segments() { return segments; }
    @Override public float time(float partial) { var s = state(); return s == null ? duration() : FilmSessionClient.time(s, partial); }
    @Override public boolean active() { var s = state(); return s != null && ID.equals(s.film); }
    @Override public View view(float t) { return path().camera(t); }
    @Override public float[] kick(float t) { return path().kick(t); }
    @Override public float blendOut() { return 16; }

    @Override public Cue[] cues() {
        List<Cue> c = new ArrayList<>();
        add(c, 1, SoundEvents.ENDERMAN_STARE, .35f, .6f); add(c, 3, SoundEvents.WARDEN_HEARTBEAT, .6f, 1f);
        if (path().step) { add(c, .5f, SoundEvents.ILLUSIONER_MIRROR_MOVE, .7f, .8f); add(c, 9, SoundEvents.ENDERMAN_TELEPORT, .4f, 1.4f); }
        // The hook; the slip under it; the cuts, each metal on flesh; the landings.
        add(c, 14, SoundEvents.PLAYER_BREATH, .8f, .8f); add(c, 22, SoundEvents.PLAYER_ATTACK_SWEEP, .9f, .7f); add(c, 22, ModSounds.ZED_BLADE_SLASH.get(), (.9f) * 1.0f, 1.0f);
        add(c, ULT_BURST, SoundEvents.TRIDENT_RIPTIDE_1, .6f, 1.6f);
        add(c, ULT_CUT1, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 1.3f); add(c, ULT_CUT1, SoundEvents.TRIDENT_HIT, .9f, 1.6f); add(c, ULT_CUT1, SoundEvents.ANVIL_LAND, .2f, 2f);
        add(c, ULT_LAND1, SoundEvents.GENERIC_SMALL_FALL, .6f, .8f); add(c, ULT_LEAP2, SoundEvents.TRIDENT_RIPTIDE_2, .7f, 1.5f);
        add(c, ULT_CUT2, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1.1f); add(c, ULT_CUT2, SoundEvents.TRIDENT_HIT, 1f, 1.3f); add(c, ULT_CUT2, SoundEvents.ANVIL_LAND, .25f, 1.8f);
        add(c, ULT_LAND2, SoundEvents.GENERIC_SMALL_FALL, .6f, .7f); add(c, ULT_DASH3, SoundEvents.TRIDENT_RIPTIDE_3, .8f, 1.7f);
        add(c, ULT_CUT3, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .8f); add(c, ULT_CUT3, SoundEvents.TRIDENT_HIT, 1f, 1.1f); add(c, ULT_CUT3, SoundEvents.PLAYER_ATTACK_CRIT, 1f, .9f);
        add(c, 66, SoundEvents.ELYTRA_FLYING, .5f, 1.6f);
        // He comes apart; the dark gathers and circles.
        add(c, ULT_FADE, SoundEvents.SOUL_ESCAPE, 1f, .6f); add(c, ULT_FADE, ModSounds.ZED_SHADOW_WHOOSH.get(), (1f) * 1.0f, 0.9f); add(c, 76, SoundEvents.ILLUSIONER_MIRROR_MOVE, .7f, .6f); add(c, ULT_GONE, SoundEvents.ENDERMAN_STARE, .4f, .5f);
        for (int t = 96; t < 196; t += 20) { add(c, t, SoundEvents.SOUL_ESCAPE, .6f, .5f + (t / 20 % 3) * .1f); add(c, t, ModSounds.ZED_SHADOW_WHOOSH.get(), .6f, 0.9f); }
        add(c, 100, SoundEvents.ELYTRA_FLYING, .25f, .6f); add(c, 140, SoundEvents.ELYTRA_FLYING, .3f, .55f);
        add(c, ULT_RISE, SoundEvents.WARDEN_EMERGE, .5f, 1.4f);
        for (float eyes : new float[]{137, 146, 152, 159, 166, 162, 176}) add(c, eyes, SoundEvents.FIRECHARGE_USE, .25f, 1.7f);
        add(c, 186, SoundEvents.WARDEN_HEARTBEAT, .8f, .8f); add(c, 193, SoundEvents.WARDEN_HEARTBEAT, .9f, .8f);
        // Silence for the held breath. Then everything at once.
        add(c, ULT_ATTACK - 3, SoundEvents.WARDEN_SONIC_CHARGE, .6f, 1.6f);
        add(c, ULT_ATTACK, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .8f); add(c, ULT_ATTACK, ModSounds.ZED_BLADE_SLASH.get(), (1f) * 1.0f, 1.0f); add(c, ULT_ATTACK + 1, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1f); add(c, ULT_ATTACK + 1, ModSounds.ZED_BLADE_SLASH.get(), (1f) * 1.0f, 1.0f);
        add(c, ULT_ATTACK + 2, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.2f); add(c, ULT_ATTACK + 2, ModSounds.ZED_BLADE_SLASH.get(), (1f) * 1.0f, 1.0f); add(c, ULT_ATTACK, SoundEvents.TRIDENT_RIPTIDE_1, .8f, 1.2f);
        for (int t = ULT_STORM; t < ULT_FLASH - 1; t += 2) add(c, t, t % 4 == 0 ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_CRIT, .5f, .6f + (t % 6) * .12f);
        add(c, 226, SoundEvents.GENERIC_BIG_FALL, .6f, .8f);
        add(c, ULT_FLASH, SoundEvents.WARDEN_SONIC_BOOM, 1f, .7f); add(c, ULT_FLASH, SoundEvents.GENERIC_EXPLODE, .8f, .6f); add(c, ULT_FLASH, ModSounds.ZED_MARK_BURST.get(), (.8f) * 1.0f, 1.0f); add(c, ULT_FLASH, SoundEvents.WITHER_BREAK_BLOCK, .5f, .7f);
        // It sinks into the ground; he is back.
        add(c, ULT_COLLAPSE + 2, SoundEvents.SOUL_ESCAPE, 1f, .5f); add(c, ULT_COLLAPSE + 2, ModSounds.ZED_SHADOW_WHOOSH.get(), (1f) * 1.0f, 0.9f); add(c, 250, SoundEvents.FIRE_EXTINGUISH, .4f, .5f); add(c, 262, SoundEvents.SOUL_ESCAPE, .5f, .45f); add(c, 262, ModSounds.ZED_SHADOW_WHOOSH.get(), (.5f) * 1.0f, 0.9f);
        add(c, ULT_REFORM, SoundEvents.ENDERMAN_STARE, .3f, .7f); add(c, ULT_SOLID, SoundEvents.ARMOR_EQUIP_IRON, .6f, .7f); add(c, ULT_SOLID + 4, SoundEvents.PLAYER_BREATH, .4f, .6f);
        add(c, 318, SoundEvents.WARDEN_HEARTBEAT, .4f, .6f);
        return c.toArray(new Cue[0]);
    }
    private static void add(List<Cue> list, float t, SoundEvent sound, float volume, float pitch) { list.add(new Cue(t, sound, volume, pitch)); }

    private static float ease(float x) { return FilmFx.ease(x); }
    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        // The world darkening at its edges as the shadow takes over; darker still just after the flash.
        float vignette = .38f * ease((t - 80) / 40) + .12f * ease((t - 170) / 30) + .1f * FilmFx.window(t, ULT_STORM, ULT_FLASH, 6)
                + .22f * FilmFx.window(t, ULT_FLASH + 2, 252, 8);
        vignette *= 1 - ease((t - 262) / 50);
        if (vignette > .01f) {
            g.fill(0, 0, w, h, HudStyle.alpha(0xFF050208, vignette * .3f));
            int edge = Math.max(30, h / 4);
            for (int i = 0; i < edge; i += 2) {
                float a = vignette * (float) Math.pow(1 - i / (float) edge, 1.6);
                int col = HudStyle.alpha(0xFF040107, a);
                g.fill(0, i, w, i + 2, col); g.fill(0, h - i - 2, w, h - i, col);
                g.fill(i, 0, i + 2, h, col); g.fill(w - i - 2, 0, w - i, h, col);
            }
        }
        // The flash: a short, strong red bloom from the middle of the picture, then gone.
        float f = ExecutionPath.flash(t);
        if (f > .01f) {
            radial(g, w / 2f, h / 2f, h * .75f, 0xff3020, .6f * f);
            g.fill(0, 0, w, h, HudStyle.alpha(0xFFB01018, .2f * f));
        }
        // A quiet title over the last held image.
        float title = FilmFx.window(t, 312, ULT_TOTAL - 2, 5);
        if (title > .01f) {
            var font = Minecraft.getInstance().font;
            String text = "GÖLGE İNFAZI";
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * .8f, 0);
            g.pose().scale(2.2f, 2.2f, 1);
            g.drawString(font, text, -font.width(text) / 2, -4, HudStyle.alpha(0xFFC81E28, title), false);
            g.pose().popPose();
            int y = (int) (h * .8f) + 14, half = font.width(text) * 11 / 10 + 10;
            g.fill(w / 2 - half, y, w / 2 + half, y + 1, HudStyle.alpha(0xFF6A0A10, title * .8f));
        }
    }
    /** A round glow on the screen: full in the middle, nothing at the edge. */
    private static void radial(GuiGraphics g, float cx, float cy, float radius, int rgb, float alpha) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        var m = g.pose().last().pose();
        float r = (rgb >> 16 & 255) / 255f, gr = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f;
        int n = 40;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            b.vertex(m, cx, cy, 0).color(r, gr, bl, alpha).endVertex();
            b.vertex(m, cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius, 0).color(r, gr, bl, 0f).endVertex();
            b.vertex(m, cx + (float) Math.cos(a0) * radius, cy + (float) Math.sin(a0) * radius, 0).color(r, gr, bl, 0f).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.disableBlend();
    }
}
