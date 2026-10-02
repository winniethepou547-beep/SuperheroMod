package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.hulk.OnePunchStage;
import com.FIRNI.superheromod.heroes.hulk.HulkConfig;
import com.FIRNI.superheromod.heroes.hulk.HulkRageSession;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.client.render.film.FilmFx.*;
import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * ONE PUNCH — THE UNSTOPPABLE FORCE (24 s). Played on its own open plain (OnePunchStage), a great
 * mountain far down the line of the punch:
 *   0-36      Hulk and the target face each other; wind, stillness, the stare
 *   36-100    five punches, each one readable, the whole body behind each
 *   100-172   faster and faster until the arms are a blur; the dust swells round them
 *   172-222   it stops dead; silence; the dust clears on Hulk standing tall
 *   222-254   the wind-up, coiled like a spring; a freeze
 *   254-268   the punch: black and red impact frames, cut close
 *   268-338   the air current tears down the plain into the mountain, the camera running with it
 *   338-392   it thins and settles: the mountain stands split open by a vast ravine
 *   392-480   the wide shot, both of them tiny before it; his arm comes down; back to the world
 * "Calm camera" (client setting) takes out the hard shakes, the whip pans and the flicker of the impact frames.
 */
final class RageFilm implements Film {
    static final String ID = HulkRageSession.ID;
    private static final float WORLD = 8;
    private final int attacker;
    private final Segment[] segments;
    private final float shake;
    private final boolean calm;

    RageFilm(int attacker) {
        this.attacker = attacker;
        calm = HulkConfig.get(HulkConfig.CALM_CAMERA);
        shake = (float) (double) HulkConfig.get(HulkConfig.SHAKE) * (calm ? .3f : 1);
        var s = FilmSessionClient.get(attacker);
        var level = Minecraft.getInstance().level;
        double d = 4;
        if (s != null && level != null && level.getEntity(s.target) != null) {
            Vec3 to = level.getEntity(s.target).position().subtract(s.anchor);
            d = Math.max(2, Math.min(16, Math.sqrt(to.x * to.x + to.z * to.z)));
        }
        Scene plain = new Scene() {
            public int skyTop() { return 0x2c5fb8; }
            public int skyBottom() { return 0xa9c4e2; }
            public FilmBackdrop.Params backdrop(float local, float t) { return OnePunchStage.backdrop(t); }
            public void render(FilmContext c) {
                var level = Minecraft.getInstance().level;
                var st = state();
                if (level == null || st == null) return;
                Player hulk = level.getEntity(st.attacker) instanceof Player p ? p : null;
                OnePunchStage.render(c, hulk, level.getEntity(st.target), c.time(), c.partial());
            }
        };
        List<FilmShot> shots = new ArrayList<>();
        // The stare: low, three-quarter, Hulk on the left, the target on the right, the mountain far off.
        shots.add(shot(WORLD, ULT_FIRST, .01f, .02f).path(v(-6.2, 1.1, -4.6), v(-5.7, 1.15, -4.1)).look(v(0, 1.7, 2.6), v(0, 1.75, 2.6)).fov(58, 54).build());
        // The first punches: medium close, close to Hulk's shoulder.
        shots.add(shot(ULT_FIRST, ULT_FIRST + 30, .06f, .1f).path(v(-2.9, 2.5, -1.6), v(-2.6, 2.4, -1.1)).look(v(-.3, 1.9, 3.1)).fov(58, 56).build());
        // From the target's side: Hulk's face, the turn of his body into each blow.
        shots.add(shot(ULT_FIRST + 30, ULT_BARRAGE, .1f, .12f).path(v(-3.4, 2.1, 4.8), v(-2.9, 2.2, 4.3)).look(v(.4, 2.5, .3)).fov(54, 52).whip(!calm).build());
        // The barrage speeding up: low, swinging round them.
        shots.add(shot(ULT_BARRAGE, ULT_BARRAGE + 36, .14f, .3f).path(v(-5.2, .9, .4), v(-4.6, 1.0, -2.8), v(-1.5, 1.1, -4.8))
                .look(v(.1, 1.7, 1.6), v(.1, 1.8, 1.6)).fov(62, 70).roll(-3, 3).build());
        // At its height: further back, the two of them shapes in the dust.
        shots.add(shot(ULT_BARRAGE + 36, ULT_STOP, .35f, .55f).path(v(-9, 3, -6), v(-8, 2.6, -4.5)).look(v(0, 1.6, 1.6)).fov(58, 56).build());
        // It stops.
        shots.add(shot(ULT_STOP, ULT_CLEAR, .6f, .05f).path(v(-8, 2.6, -4.5), v(-7.8, 2.5, -4.3)).look(v(0, 1.8, 1.6)).fov(56, 55).build());
        // The dust clears: closer to Hulk, standing tall, looking at them.
        shots.add(shot(ULT_CLEAR, ULT_WINDUP, .02f, .02f).path(v(-2.4, 2.6, 4.4), v(-2.0, 2.8, 3.5)).look(v(.45, 3.3, 0), v(.45, 3.4, 0)).fov(50, 42).build());
        // The wind-up, from low in front: the coil, the tremble.
        shots.add(shot(ULT_WINDUP, ULT_FREEZE, .1f, .3f).path(v(-1.4, .5, 3.0), v(-1.3, .45, 2.6)).look(v(.55, 2.9, .1)).fov(50, 44).build());
        shots.add(shot(ULT_FREEZE, ULT_PUNCH - 3, 0, 0).path(v(-1.3, .45, 2.6), v(-1.28, .45, 2.55)).look(v(.55, 2.9, .1)).fov(44, 43).build());
        // The punch: a fast push in along his right side toward the fist.
        shots.add(shot(ULT_PUNCH - 3, ULT_PUNCH, .2f, .6f).path(v(-2.8, 2.2, -1.6), v(-1.5, 2.15, 1.0)).look(v(-.25, 2.1, 3.2)).fov(70, 92).whip(!calm).build());
        // The impact frames: close cuts, the contact, his arm, the line of it.
        shots.add(shot(ULT_PUNCH, ULT_PUNCH + 4, 1, .8f).path(v(-1.2, 2.0, 2.2), v(-1.1, 2.0, 2.3)).look(v(-.3, 1.9, 3.1)).fov(38, 36).build());
        shots.add(shot(ULT_PUNCH + 4, ULT_PUNCH + 8, .9f, .7f).path(v(-1.9, 2.3, 1.0), v(-1.8, 2.3, 1.1)).look(v(.2, 2.2, 1.6)).fov(44, 42).build());
        shots.add(shot(ULT_PUNCH + 8, ULT_IMPACT_END, .8f, .5f).path(v(1.8, 3.1, -3.8), v(1.7, 3.0, -3.6)).look(v(-.5, 2.0, 5)).fov(56, 58).build());
        // The air current: running with it down the plain.
        shots.add(shot(ULT_IMPACT_END, 300, .45f, .2f).path(v(-2.5, 2.6, .5), v(-4, 3.5, 20), v(-7, 5, 50))
                .look(v(0, 3, 8), v(0, 4, 35), v(0, 8, 80)).fov(70, 76).build());
        // From far off the side: the current crossing the plain and smothering the mountain.
        shots.add(shot(300, ULT_REVEAL, .08f, .12f).path(v(-90, 30, 40), v(-95, 33, 58)).look(v(0, 15, 85), v(0, 25, 122)).fov(55, 52).build());
        // It thins: the mountain shows through, split open.
        shots.add(shot(ULT_REVEAL, ULT_WIDE, .03f, .02f).path(v(-40, 14, 10), v(-42, 16, 6)).look(v(0, 30, 140), v(0, 31, 140)).fov(55, 53).build());
        // The wide shot: both of them tiny before the ravine, Hulk on the left, the target on the right.
        shots.add(shot(ULT_WIDE, ULT_TOTAL, .01f, 0).path(v(.2, 2.6, -34), v(.2, 3.0, -40)).look(v(-.6, 24, 140)).fov(50, 48).build());
        segments = new Segment[]{
                // A beat in the real world first, behind him, then a cut through black onto the plain.
                new Segment(0, WORLD, new FilmShot[]{FilmShot.of(0, WORLD).path(v(1.4, 2.8, -3.6), v(1.2, 2.7, -3.3)).look(v(0, 1.8, d), v(0, 1.8, d)).fov(60, 58).build()}, null, 0, 0),
                new Segment(WORLD, ULT_TOTAL, shots.toArray(new FilmShot[0]), plain, 0x000000, 6)};
    }
    private FilmShot.Builder shot(float start, float end, float a, float b) { return FilmShot.of(start, end).shake(a * shake, b * shake); }
    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }
    private FilmSessionClient.State state() { return FilmSessionClient.get(attacker); }
    @Override public Vec3 origin() { var s = state(); return s == null ? Vec3.ZERO : s.anchor; }
    @Override public Vec3 forward() { var s = state(); return s == null ? new Vec3(0, 0, 1) : FilmSessionClient.forward(s); }
    @Override public float duration() { return ULT_TOTAL; }
    @Override public Segment[] segments() { return segments; }
    @Override public float time(float partial) { var s = state(); return s == null ? duration() : FilmSessionClient.time(s, partial); }
    @Override public boolean active() { var s = state(); return s != null && ID.equals(s.film); }
    @Override public float blendOut() { return 10; }

    // ------------------------------------------------------------------ sound
    @Override public Cue[] cues() {
        List<Cue> c = new ArrayList<>();
        add(c, WORLD, SoundEvents.ELYTRA_FLYING, .25f, .5f);
        for (int i = 0; i < ULT_HITS.length; i++) {
            float t = ULT_HITS[i], p = ULT_POWER[i];
            if (i < 5 || i == ULT_HITS.length - 1) {
                // The opening blows and the last: each one heavy and on its own.
                add(c, t, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .55f + .05f * i);
                add(c, t, SoundEvents.ANVIL_LAND, .3f + .2f * p, .45f);
                add(c, t, SoundEvents.IRON_GOLEM_ATTACK, .8f, .6f);
                if (p > 1.2f) add(c, t, SoundEvents.GENERIC_EXPLODE, .6f, 1.2f);
            } else {
                // The barrage: blows of different weight and pitch, closer and closer together.
                SoundEvent s = i % 3 == 0 ? SoundEvents.PLAYER_ATTACK_STRONG : i % 3 == 1 ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_CRIT;
                add(c, t, s, .55f + .3f * p, .5f + .35f * (i % 5) / 4f);
                if (i % 4 == 0) add(c, t, SoundEvents.GENERIC_EXPLODE, .18f, 1.6f + .1f * (i % 3));
            }
        }
        // The roar of the air the barrage churns up.
        add(c, ULT_BARRAGE + 20, SoundEvents.ELYTRA_FLYING, .5f, 1.1f);
        add(c, ULT_BARRAGE + 45, SoundEvents.ELYTRA_FLYING, .8f, 1.5f);
        add(c, ULT_STOP, SoundEvents.GENERIC_EXPLODE, .8f, .6f);
        // Silence; stones settling.
        add(c, ULT_STOP + 10, SoundEvents.GRAVEL_FALL, .4f, .6f);
        add(c, ULT_STOP + 26, SoundEvents.STONE_BREAK, .25f, .6f);
        // The wind-up: a low rising hum, the ground cracking.
        add(c, ULT_WINDUP, SoundEvents.WARDEN_HEARTBEAT, 1f, .5f);
        add(c, ULT_WINDUP + 4, SoundEvents.WARDEN_SONIC_CHARGE, 1f, .6f);
        add(c, ULT_WINDUP + 10, SoundEvents.WARDEN_HEARTBEAT, 1f, .55f);
        add(c, ULT_WINDUP + 12, SoundEvents.STONE_BREAK, .5f, .5f);
        add(c, ULT_WINDUP + 18, SoundEvents.WARDEN_HEARTBEAT, 1f, .6f);
        // The punch, after a breath of nothing.
        add(c, ULT_PUNCH, SoundEvents.GENERIC_EXPLODE, 1f, .35f);
        add(c, ULT_PUNCH, SoundEvents.WARDEN_SONIC_BOOM, 1f, .45f);
        add(c, ULT_PUNCH, SoundEvents.ANVIL_LAND, 1f, .3f);
        // The air current: a vast wind, thinning out.
        add(c, ULT_IMPACT_END, SoundEvents.WARDEN_SONIC_BOOM, .8f, .3f);
        add(c, ULT_IMPACT_END, SoundEvents.ELYTRA_FLYING, 1f, .7f);
        add(c, ULT_IMPACT_END + 4, SoundEvents.GENERIC_EXPLODE, .6f, .3f);
        add(c, 300, SoundEvents.ELYTRA_FLYING, .6f, .5f);
        // The mountain giving way, far off.
        add(c, ULT_REVEAL - 12, SoundEvents.GENERIC_EXPLODE, .7f, .25f);
        add(c, ULT_REVEAL - 10, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, .5f, .3f);
        for (int i = 0; i < 9; i++) add(c, ULT_REVEAL + 6 + i * 13, i % 2 == 0 ? SoundEvents.STONE_BREAK : SoundEvents.GRAVEL_BREAK, .3f - i * .02f, .45f + .05f * (i % 3));
        add(c, ULT_WIDE + 6, SoundEvents.ELYTRA_FLYING, .2f, .5f);
        return c.toArray(new Cue[0]);
    }
    private static void add(List<Cue> list, float t, SoundEvent sound, float volume, float pitch) { list.add(new Cue(t, sound, volume, pitch)); }

    // ------------------------------------------------------------------ picture
    /** Impact frames: black, red, black again, each held a beat. Then a breath of white air; black at the very end. */
    @Override public int grade(float t) {
        if (t >= ULT_PUNCH && t < ULT_IMPACT_END) {
            int f = (int) ((t - ULT_PUNCH) / (calm ? 4.5f : 1.5f)) % 3;
            return f == 1 ? 0xD8700000 : f == 0 ? 0xE6000000 : 0xEE050000;
        }
        float white = window(t, ULT_IMPACT_END, ULT_IMPACT_END + 14, 2) * .55f;
        if (white > .01f) return ((int) (white * 255) << 24) | 0xf4f1ea;
        float dark = window(t, ULT_FREEZE - 4, ULT_PUNCH, 4) * .22f + clamp01((t - (ULT_TOTAL - 8)) / 8f) * .9f;
        if (dark > .01f) return ((int) (dark * 255) << 24);
        return 0;
    }
    private static float clamp01(float t) { return Math.max(0, Math.min(1, t)); }

    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        if (t >= ULT_PUNCH && t < ULT_IMPACT_END) impactFrame(g, w, h, t);
        // Speed lines while the barrage is at its fastest and while the air current runs.
        float speed = Math.max(window(t, ULT_BARRAGE + 30, ULT_STOP, 6) * .8f, window(t, ULT_IMPACT_END, 300, 6));
        if (speed > .02f) speedLines(g, w, h, speed, t);
        float title = window(t, ULT_WIDE + 26, ULT_TOTAL - 10, 8);
        if (title > 0) {
            var font = Minecraft.getInstance().font;
            String text = "ONE PUNCH";
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * .16f, 0);
            g.pose().scale(2.6f, 2.6f, 1);
            g.drawString(font, text, -font.width(text) / 2, -4, HudStyle.alpha(0xFFFFFFFF, title * .9f), false);
            g.pose().popPose();
            HudStyle.caption(g, font, "The Unstoppable Force", w / 2, (int) (h * .16f) + 16, HudStyle.alpha(HudStyle.TEXT, title * .8f), 0);
        }
    }
    /**
     * One impact frame: a red burst tearing out from the fist with white strokes through it, or (every
     * other frame) the same shape in black on red; each frame shifts and re-rolls its shapes so the
     * picture flickers like hand-drawn impact frames.
     */
    private void impactFrame(GuiGraphics g, int w, int h, float t) {
        int frame = (int) ((t - ULT_PUNCH) / (calm ? 4.5f : 1.5f));
        boolean inverse = frame % 3 == 1;
        float cx = w * (.52f + .04f * (hash(frame * 3.1f) - .5f)), cy = h * (.48f + .05f * (hash(frame * 5.7f) - .5f));
        float reach = (float) Math.hypot(w, h) * .75f;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        var m = g.pose().last().pose();
        int spikes = 64;
        for (int i = 0; i < spikes; i++) {
            double a0 = (i + hash(i * 7.3f + frame) * .5) / spikes * Math.PI * 2, a1 = (i + 1) / (double) spikes * Math.PI * 2;
            float len = reach * (.25f + .7f * hash(i * 13.1f + frame * 2.3f));
            float r = inverse ? 0 : 1, gg = inverse ? 0 : .08f, b = inverse ? 0 : .04f;
            // The burst: a jagged star, solid at the centre.
            buffer.vertex(m, cx, cy, 0).color(r, inverse ? 0 : .55f, inverse ? 0 : .35f, 1f).endVertex();
            buffer.vertex(m, cx + (float) Math.cos(a0) * len, cy + (float) Math.sin(a0) * len * .8f, 0).color(r, gg, b, inverse ? .85f : .9f).endVertex();
            buffer.vertex(m, cx + (float) Math.cos(a1) * len * .45f, cy + (float) Math.sin(a1) * len * .36f, 0).color(r, gg, b, inverse ? .7f : .75f).endVertex();
        }
        // Sharp strokes across it: white on the red frames, red on the black ones.
        for (int i = 0; i < 26; i++) {
            double a = hash(i * 3.3f + frame * 9.1f) * Math.PI * 2;
            float inner = reach * (.08f + .2f * hash(i * 5.1f + frame)), outer = reach * (.5f + .5f * hash(i * 1.7f + frame * 4.4f));
            float half = 1.2f + 3.5f * hash(i * 2.9f + frame);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a), px = -sin * half, py = cos * half;
            float r = 1, gg = inverse ? .15f : 1, b = inverse ? .1f : 1;
            buffer.vertex(m, cx + cos * inner, cy + sin * inner, 0).color(r, gg, b, 0f).endVertex();
            buffer.vertex(m, cx + cos * outer + px, cy + sin * outer + py, 0).color(r, gg, b, .95f).endVertex();
            buffer.vertex(m, cx + cos * outer - px, cy + sin * outer - py, 0).color(r, gg, b, .95f).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
    /** Film speed lines: thin wedges streaking in from the edges toward the middle, each living a few frames. */
    private static void speedLines(GuiGraphics g, int w, int h, float strength, float time) {
        float cx = w / 2f, cy = h / 2f, reach = (float) Math.hypot(cx, cy);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        var m = g.pose().last().pose();
        int count = (int) (70 * strength);
        for (int i = 0; i < count; i++) {
            float cycle = (float) Math.floor(time * .9f + i * .37f);
            double angle = hash(i * 31 + cycle * 17) * Math.PI * 2;
            float life = (time * .9f + i * .37f) - cycle;
            float inner = reach * (.5f + .25f * hash(i * 7 + cycle)) - life * reach * .15f, outer = reach * 1.05f;
            float alpha = strength * (1 - life) * (.3f + .45f * hash(i * 13 + cycle));
            float half = (1.2f + 2.4f * hash(i * 5 + cycle)) * (.6f + strength * .6f);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float tx = cx + cos * inner, ty = cy + sin * inner * .75f, ox = cx + cos * outer, oy = cy + sin * outer * .75f;
            float px = -sin * half, py = cos * half;
            buffer.vertex(m, tx, ty, 0).color(1f, 1f, 1f, 0f).endVertex();
            buffer.vertex(m, ox + px, oy + py, 0).color(1f, 1f, 1f, alpha).endVertex();
            buffer.vertex(m, ox - px, oy - py, 0).color(1f, 1f, 1f, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
    private static float hash(float n) { double x = Math.sin(n * 12.9898) * 43758.5453; return (float) (x - Math.floor(x)); }
}
