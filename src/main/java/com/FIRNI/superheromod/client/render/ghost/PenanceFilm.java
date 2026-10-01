package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.film.Film;
import com.FIRNI.superheromod.client.render.film.FilmBackdrop;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmShot;
import com.FIRNI.superheromod.heroes.ghostrider.GhostComboMotion;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleEntity;
import com.FIRNI.superheromod.heroes.ghostrider.HellCycleRig;
import com.FIRNI.superheromod.heroes.ghostrider.PenanceStare;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Penance Stare, the film (19 s): into the Ghost Rider's hell and back.
 *   0-50     world    the grab, wide; then into the fire of his eye socket
 *   50-92    descent  falling with the soul, bound bone by bone in chain
 *   92-150   highway  he rides the Hell Cycle down the road, past the camera and on to the far end
 *   150-350  the judgement, beat for beat:
 *            throw (the bike slides in, he jumps off and whips the chain into the lens) - chase
 *            (ride the chain tip to the soul) - rise (the soul held in a pillar of fire) - bind
 *            (chain coils round every bone) - claw (his burning hand closes) - web (pinned in a
 *            web of chains, impact frame, fire crackling along them) - cage (the chains collapse,
 *            an iron cage slams down, shards burst; his skull, laughing) - pull (the soul's light
 *            gathered in front of his face, rays, white flash)
 *   350-380  world    the body driven into the ground; back to the game camera
 */
final class PenanceFilm implements Film {
    static final int W1 = 0, DESCENT = 50, ROAD = 92, THROW = 150, CHASE = 190, RISE = 206, BIND = 224, CLAW = 238,
            WEB = 252, CAGE = 278, FACE = 294, PULL = 304, W2 = PenanceStare.CLIMAX_END;
    /** Throw beat, local ticks: the bike stops, he hops off, winds up, releases. */
    private static final float STOP = 10, OFF = 16, RELEASE = 26, HIT = 34;
    private static final Vec3 THROWER = new Vec3(1.1, 0, .4), LENS = new Vec3(1.4, 1.4, 3.4);
    /** Where the soul hangs during rise and bind, and the light point in front of his face. */
    private static final Vec3 HELD = new Vec3(0, 3, 0), GIANT = new Vec3(0, 0, -3.4), FACE_POINT = new Vec3(0, 1.66, -3.0);
    private final int rider;
    private final Segment[] segments;

    PenanceFilm(int rider) {
        this.rider = rider;
        this.segments = new Segment[]{
                new Segment(W1, DESCENT, new FilmShot[]{
                        FilmShot.of(W1, 34).path(v(3.6, 1.2, -2.0), v(3.9, 1.45, -.2), v(3.5, 1.6, 1.4))
                                .look(v(0, 1.3, .4), v(0, 1.45, .4)).fov(52, 48).shake(.02f, .03f).build(),
                        FilmShot.of(34, DESCENT).path(v(3.5, 1.6, 1.4), v(1.8, 1.68, .9), v(.6, 1.68, .5), v(.13, 1.67, .24))
                                .look(v(0, 1.55, .4), v(.1, 1.66, .15), v(.13, 1.67, 0)).fov(48, 24).shake(.03f, .06f).build()},
                        null, 0, 0),
                new Segment(DESCENT, ROAD, new FilmShot[]{FilmShot.of(DESCENT, ROAD)
                        .path(v(2.2, 3.4, 1.4), v(-.6, 3.0, 2.5), v(-2.3, 2.5, .4), v(-1.5, 2.1, -1.8))
                        .look(v(0, .9, 0)).fov(62, 54).shake(.06f, .08f).build()}, descent(), 0xFF7A2A, 6),
                new Segment(ROAD, THROW, new FilmShot[]{
                        // Low at the roadside looking down the highway; he comes out of the haze...
                        FilmShot.of(ROAD, ROAD + 36).path(v(3.0, .7, 12), v(2.8, .8, 11), v(2.6, .9, 9.5))
                                .look(v(0, 1.2, -80), v(0, 1.1, -40), v(0, 1.0, -12)).fov(50, 44).shake(.02f, .06f).build(),
                        // ...roars past, and the camera whips round to watch him go to the far end.
                        FilmShot.of(ROAD + 36, THROW).path(v(2.6, .9, 9.5), v(2.2, 1.2, 8.2), v(1.8, 1.4, 7.6))
                                .look(v(0, 1.0, -10), v(-.2, 1.2, 40), v(-.4, 1.4, 160)).fov(44, 58).shake(.25f, .08f).build()},
                        road(), 0x1A0603, 5),
                new Segment(THROW, CHASE, new FilmShot[]{
                        FilmShot.of(THROW, THROW + OFF).path(v(3.4, 1.1, 4.8), v(3.1, 1.3, 4.1))
                                .look(v(0, 1.0, -.5), v(.7, 1.1, .3)).fov(50, 46).shake(.08f, .04f).build(),
                        // Full figure, three-quarter: the wind-up, then the chain comes straight at us.
                        FilmShot.of(THROW + OFF, CHASE).path(v(-1.1, 1.55, 3.7), v(-.2, 1.5, 3.9), v(1.4, 1.4, 3.4))
                                .look(v(1.1, 1.3, .4), v(1.2, 1.35, 1.2), v(1.4, 1.4, 6)).fov(52, 60).shake(.03f, .25f).build()},
                        throwing(), 0, 0),
                new Segment(CHASE, RISE, new FilmShot[]{FilmShot.of(CHASE, RISE)
                        .path(v(0, 1.5, 1.5), v(0, 1.55, 12), v(0, 1.6, 21))
                        .look(v(0, 1.5, 12), v(0, 1.55, 24), v(0, 1.6, 30)).fov(74, 64).roll(-6, 6).shake(.18f, .22f).build()},
                        chase(), 0x000000, 2),
                new Segment(RISE, BIND, new FilmShot[]{FilmShot.of(RISE, BIND)
                        .path(v(2.4, 2.6, 3.7), v(2.1, 3.2, 3.4), v(1.9, 3.7, 3.2))
                        .look(v(0, 3.7, 0), v(0, 3.9, 0), v(0, 4.1, 0)).fov(52, 48).shake(.05f, .08f).build()},
                        rise(), 0, 0),
                new Segment(BIND, CLAW, new FilmShot[]{FilmShot.of(BIND, CLAW)
                        .path(v(.95, 4.75, 1.25), v(.75, 4.6, 1.05)).look(v(0, 4.15, 0)).fov(40, 36).shake(.1f, .14f).build()},
                        bind(), 0, 0),
                new Segment(CLAW, WEB, new FilmShot[]{FilmShot.of(CLAW, WEB)
                        .path(v(0, 2.0, 4.0), v(0, 2.0, 3.7)).look(v(0, 2.0, 0)).fov(52, 48).shake(.12f, .3f).build()},
                        claw(), 0, 0),
                new Segment(WEB, CAGE, new FilmShot[]{FilmShot.of(WEB, CAGE)
                        .path(v(0, 1.0, 5.5), v(1.2, 1.1, 5.3), v(2.2, 1.2, 4.9)).look(v(0, 1.0, 0))
                        .fov(58, 54).roll(0, 18).shake(.15f, .08f).build()},
                        web(), 0xFFE8B0, 1),
                new Segment(CAGE, PULL, new FilmShot[]{
                        FilmShot.of(CAGE, FACE).path(v(3.8, 1.4, 3.0), v(3.0, 1.8, 3.9)).look(v(0, 1.2, 0)).fov(56, 50).shake(.08f, .35f).build(),
                        // Straight on, centred: the skull fills the frame.
                        FilmShot.of(FACE, PULL).path(v(0, 1.68, -1.45), v(0, 1.67, -1.75)).look(v(0, 1.66, -3.4)).fov(42, 36).shake(.03f, .05f).build()},
                        cage(), 0, 0),
                new Segment(PULL, W2, new FilmShot[]{
                        // Wide: the soul in front of him, its light tearing away toward his face.
                        FilmShot.of(PULL, PULL + PULL_WIDE).path(v(2.4, 1.6, -.2), v(1.8, 1.7, -.8))
                                .look(v(.4, 1.35, -2.4), v(.25, 1.5, -2.7)).fov(50, 46).shake(.03f, .06f).build(),
                        // Straight on: the light grows in front of the skull, then bursts.
                        FilmShot.of(PULL + PULL_WIDE, W2).path(v(0, 1.67, -1.55), v(0, 1.66, -1.15), v(0, 1.62, .9))
                                .look(v(0, 1.66, -3.1)).fov(40, 72).shake(.04f, .7f).build()},
                        pull(), 0, 0),
                new Segment(W2, PenanceStare.TOTAL, new FilmShot[]{FilmShot.of(W2, PenanceStare.TOTAL)
                        .path(v(3.6, .5, -.4), v(4.2, 1.1, -1.4), v(4.8, 1.9, -2.4))
                        .look(v(0, .7, 1.1), v(0, 1.0, 1.0), v(0, 1.2, .8)).fov(70, 66).shake(.2f, .02f).build()},
                        null, 0xFFF4E4, 4)};
    }
    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }
    private PenanceClient.State state() { return PenanceClient.STATES.get(rider); }
    @Override public Vec3 origin() { var s = state(); return s == null ? Vec3.ZERO : s.anchor; }
    @Override public Vec3 forward() { var s = state(); return s == null ? new Vec3(0, 0, 1) : PenanceClient.forward(s); }
    @Override public float duration() { return PenanceStare.TOTAL; }
    @Override public Segment[] segments() { return segments; }
    @Override public float time(float partial) { var s = state(); return s == null ? duration() : PenanceClient.time(s, partial); }
    @Override public boolean active() { return state() != null; }
    @Override public float[] impacts() { return new float[]{WEB, PenanceStare.CLIMAX}; }
    @Override public float blendOut() { return 14; }

    @Override public Cue[] cues() {
        return new Cue[]{
                new Cue(2, SoundEvents.PLAYER_ATTACK_STRONG, .8f, .6f), new Cue(10, SoundEvents.CHAIN_HIT, .9f, .6f),
                new Cue(28, SoundEvents.FIRE_AMBIENT, 1f, .6f), new Cue(45, SoundEvents.BLAZE_SHOOT, .8f, .5f),
                new Cue(DESCENT + 1, SoundEvents.SOUL_ESCAPE, 1f, .5f), new Cue(DESCENT + 12, SoundEvents.WITHER_AMBIENT, .4f, .45f),
                new Cue(ROAD + 4, SoundEvents.BLAZE_BURN, .6f, .45f), new Cue(ROAD + 18, SoundEvents.BLAZE_BURN, .8f, .6f),
                new Cue(ROAD + 30, SoundEvents.BLAZE_BURN, 1f, .8f), new Cue(ROAD + 36, SoundEvents.FIRECHARGE_USE, 1f, .55f),
                new Cue(ROAD + 38, SoundEvents.BLAZE_SHOOT, 1f, .6f),
                new Cue(THROW + 2, SoundEvents.BLAZE_BURN, 1f, .7f), new Cue(THROW + 8, SoundEvents.FIRE_EXTINGUISH, .9f, .6f),
                new Cue(THROW + OFF, SoundEvents.CHAIN_FALL, 1f, .6f), new Cue(THROW + OFF + 3, SoundEvents.CHAIN_STEP, 1f, .7f),
                new Cue(THROW + RELEASE - 3, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .55f), new Cue(THROW + RELEASE, SoundEvents.CHAIN_PLACE, 1f, .6f),
                new Cue(THROW + RELEASE + 1, SoundEvents.BLAZE_SHOOT, .8f, .7f), new Cue(THROW + HIT, SoundEvents.CHAIN_HIT, 1f, .8f),
                new Cue(CHASE, SoundEvents.CHAIN_PLACE, .9f, .9f), new Cue(CHASE + 6, SoundEvents.CHAIN_STEP, .9f, 1.2f),
                new Cue(CHASE + 12, SoundEvents.CHAIN_PLACE, .9f, 1.1f),
                new Cue(RISE, SoundEvents.BLAZE_SHOOT, 1f, .4f), new Cue(RISE + 6, SoundEvents.BEACON_POWER_SELECT, .7f, .5f),
                new Cue(BIND + 2, SoundEvents.CHAIN_HIT, 1f, .6f), new Cue(BIND + 7, SoundEvents.CHAIN_PLACE, 1f, .5f),
                new Cue(CLAW + 2, SoundEvents.PLAYER_ATTACK_STRONG, 1f, .5f), new Cue(CLAW + 8, SoundEvents.LAVA_POP, 1f, .5f),
                new Cue(CLAW + 11, SoundEvents.ANVIL_LAND, .5f, .6f),
                new Cue(WEB, SoundEvents.LIGHTNING_BOLT_THUNDER, .8f, 1.2f), new Cue(WEB, SoundEvents.GENERIC_EXPLODE, .7f, 1f),
                new Cue(WEB + 5, SoundEvents.FIRE_EXTINGUISH, .7f, .5f), new Cue(WEB + 18, SoundEvents.CHAIN_HIT, .9f, .4f),
                new Cue(CAGE + 2, SoundEvents.CHAIN_FALL, 1f, .5f), new Cue(CAGE + 7, SoundEvents.ANVIL_LAND, 1f, .5f),
                new Cue(CAGE + 7, SoundEvents.IRON_DOOR_CLOSE, 1f, .5f), new Cue(CAGE + 8, SoundEvents.GLASS_BREAK, 1f, .6f),
                new Cue(FACE + 1, SoundEvents.WITCH_CELEBRATE, .9f, .45f), new Cue(FACE + 5, SoundEvents.WITCH_CELEBRATE, .7f, .4f),
                new Cue(PULL, SoundEvents.SOUL_ESCAPE, 1f, .5f), new Cue(PULL + 12, SoundEvents.BEACON_AMBIENT, .8f, .5f),
                new Cue(PULL + 26, SoundEvents.BEACON_ACTIVATE, .9f, .6f), new Cue(PULL + 36, SoundEvents.BLAZE_SHOOT, 1f, .4f),
                new Cue(PenanceStare.CLIMAX, SoundEvents.GENERIC_EXPLODE, 1f, .7f), new Cue(PenanceStare.CLIMAX, SoundEvents.LIGHTNING_BOLT_THUNDER, .8f, .9f),
                new Cue(W2 + 2, SoundEvents.BEACON_DEACTIVATE, .6f, .8f)};
    }
    @Override public int grade(float t) {
        float socket = PenanceClient.ease((t - 42) / 8f) * (t < DESCENT ? 1 : 0);
        if (socket > 0) return ((int) (socket * 200) << 24) | 0xFF5A10;
        // The chain hits the lens and the frame goes dark into the chase.
        float dark = PenanceClient.ease((t - (THROW + HIT)) / 4f) * (t < CHASE ? 1 : 0);
        if (dark > 0) return ((int) (dark * 235) << 24);
        float scan = t >= W2 ? 1 - PenanceClient.ease((t - W2) / 12f) : 0;
        if (scan > 0) return ((int) (scan * 150) << 24) | 0xE04A08;
        return 0;
    }

    // ------------------------------------------------------------------ on-screen text (only outside the judgement)
    private static float window(float t, float in, float out) { return Math.min(PenanceClient.ease((t - in) / 5), PenanceClient.ease((out - t) / 5)); }
    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        var font = Minecraft.getInstance().font;
        String top = null; float a = 0;
        if (t < DESCENT) { top = "[ Judgement ]"; a = window(t, 8, DESCENT - 6); }
        else if (t < ROAD) { top = "[ Descent · Circle IX ]"; a = window(t, DESCENT + 4, ROAD - 2); }
        else if (t < THROW) { top = "[ Highway · Ninth Circle ]"; a = window(t, ROAD + 3, THROW - 4); }
        else if (t >= W2) { top = "[ Judgement Rendered ]"; a = window(t, W2 + 4, PenanceStare.TOTAL - 6); }
        if (top != null && a > 0) HudStyle.caption(g, font, top, w / 2, 14, HudStyle.alpha(HudStyle.ACCENT, a), 0);
        float title = window(t, ROAD + 6, ROAD + 30);
        if (title > 0) {
            int y = (int) (h * .40f);
            float drift = (t - ROAD) * .15f;
            g.pose().pushPose();
            g.pose().translate(w / 2f - drift, y, 0); g.pose().scale(2.6f, 2.6f, 1);
            HudStyle.caption(g, font, "Penance Stare", 0, 0, HudStyle.alpha(HudStyle.ACCENT, title), 0);
            g.pose().popPose();
            String sub = "Spirit of Vengeance  ·  Ghost Rider";
            int sw = HudStyle.captionWidth(font, sub);
            HudStyle.caption(g, font, sub, w / 2, y + 28, HudStyle.alpha(HudStyle.TEXT, title * .8f), 0);
            g.fill(w / 2 - sw / 2 - 34, y + 31, w / 2 - sw / 2 - 8, y + 32, HudStyle.alpha(HudStyle.ACCENT, title));
            g.fill(w / 2 + sw / 2 + 8, y + 31, w / 2 + sw / 2 + 34, y + 32, HudStyle.alpha(HudStyle.ACCENT, title));
        }
        String label = null, number = ""; float value = 0, shown = 0;
        if (t >= DESCENT && t < ROAD) { float k = (t - DESCENT) / (ROAD - DESCENT); value = k; label = "Depth"; number = String.format("%,d m", (int) (k * 6666)); shown = window(t, DESCENT + 6, ROAD - 2); }
        else if (t >= ROAD && t < THROW) { float k = Math.min(1, (t - ROAD) / 40f); value = k; label = "Hell Cycle"; number = (int) (120 + k * 540) + " km/h"; shown = window(t, ROAD + 8, THROW - 4); }
        if (label != null && shown > 0) {
            int y = h - 30, half = 64;
            HudStyle.caption(g, font, label, w / 2 - half, y, HudStyle.alpha(HudStyle.MUTED, shown), -1);
            HudStyle.caption(g, font, number, w / 2 + half, y, HudStyle.alpha(HudStyle.ACCENT, shown), 1);
            HudStyle.bar(g, w / 2 - half, y + 11, half * 2, value, HudStyle.alpha(HudStyle.ACCENT, shown));
        }
        float scan = t >= W2 ? 1 - PenanceClient.ease((t - W2) / 12f) : 0;
        if (scan > 0) for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, HudStyle.alpha(0xFF000000, scan * .35f));
    }

    // ------------------------------------------------------------------ drawing helpers (a fresh buffer for every call)
    private static double hash(double n) { double x = Math.sin(n * 12.9898) * 43758.5453; return x - Math.floor(x); }
    private static void vol(FilmContext c, Vec3 at, double w, double h, float seed, float alpha, float heat) {
        GhostFireMaterial.volume(c.buffers().getBuffer(GhostFireMaterial.TYPE), c.pose(), at, c.viewRight(), c.viewUp(), w, h, seed, alpha, heat);
    }
    private static void glow(FilmContext c, Vec3 at, double size, float alpha) {
        GhostFireMaterial.glow(c.buffers().getBuffer(GhostFireMaterial.TYPE), c.pose(), at, c.viewRight(), c.viewUp(), size, alpha);
    }
    /** Camera-facing streak: mode .55 hot spark (tail cool, head hot), .46 pale hairline. */
    private static void streak(FilmContext c, Vec3 tail, Vec3 head, double width, float mode, float heat, float alpha) {
        Vec3 side = head.subtract(tail).cross(c.camera().subtract(head));
        if (side.lengthSqr() < 1e-10 || alpha <= .002f) return;
        side = side.normalize().scale(width);
        var vc = c.buffers().getBuffer(GhostFireMaterial.TYPE);
        Vec3[] q = {tail.subtract(side), tail.add(side), head.add(side), head.subtract(side)};
        float[] u = {0, 1, 1, 0}, tt = {0, 0, 1, 1};
        for (int i = 0; i < 4; i++) GhostFireMaterial.vertex(vc, c.pose(), q[i].x, q[i].y, q[i].z, u[i], mode < .5f ? .5f : tt[i], mode, 0, heat, alpha);
    }
    private static void embers(FilmContext c, int count, double width, double height, double speed, float heat, float alpha) {
        for (int i = 0; i < count; i++) {
            double life = ((hash(i + 13) + c.time() * speed / height) % 1 + 1) % 1;
            Vec3 head = new Vec3((hash(i) - .5) * width + Math.sin(c.time() * .05 + i) * .3, life * height - height * .45, (hash(i + 71) - .5) * width);
            Vec3 tail = head.subtract(0, Math.signum(speed) * (.12 + Math.abs(speed) * 2.5), 0);
            streak(c, tail, head, .012 + hash(i + 5) * .02, .55f, heat, alpha * (float) Math.sin(Math.PI * life));
        }
    }
    private static void soulAura(FilmContext c, Vec3 at, float t, float strength) {
        for (int i = 0; i < 12; i++) {
            double a = i * 2.399 + t * .05, y = ((hash(i) + t * .025) % 1) * 2.1;
            vol(c, at.add(Math.cos(a) * .42, y, Math.sin(a) * .42), .22, .34, (float) hash(i + 3), .45f * strength, .9f);
        }
        glow(c, at.add(0, 1.2, 0), 2.4, .85f * strength);
    }
    private static void chain(FilmContext c, java.util.function.DoubleFunction<Vec3> curve, double length, int heat) {
        GhostChainRenderer.drawChain(c.pose(), curve, length, heat);
    }
    private static void straightChain(FilmContext c, Vec3 a, Vec3 b, double sag, int heat) {
        chain(c, u -> a.lerp(b, u).add(0, -Math.sin(Math.PI * u) * sag, 0), a.distanceTo(b) * 1.02, heat);
    }
    /** Chain curving from a to b through a raised (or lowered) middle. */
    private static void arcChain(FilmContext c, Vec3 a, Vec3 control, Vec3 b, int heat) {
        java.util.function.DoubleFunction<Vec3> curve = u -> a.scale((1 - u) * (1 - u)).add(control.scale(2 * u * (1 - u))).add(b.scale(u * u));
        double length = 0; Vec3 last = a;
        for (int i = 1; i <= 12; i++) { Vec3 next = curve.apply(i / 12.0); length += next.distanceTo(last); last = next; }
        chain(c, curve, length, heat);
    }
    /** A tight helix of chain round the segment from a to b, `turns` times, `amount` grown 0..1. */
    private static void wrap(FilmContext c, Vec3 a, Vec3 b, double rx, double rz, double turns, float amount, int heat) {
        if (amount <= 0) return;
        Vec3 axis = b.subtract(a);
        Vec3 sideX = axis.cross(Math.abs(axis.normalize().y) > .9 ? v(0, 0, 1) : v(0, 1, 0)).normalize();
        Vec3 sideZ = axis.cross(sideX).normalize();
        java.util.function.DoubleFunction<Vec3> helix = u -> {
            double s = u * amount, angle = s * turns * Math.PI * 2;
            return a.add(axis.scale(s)).add(sideX.scale(Math.cos(angle) * rx)).add(sideZ.scale(Math.sin(angle) * rz));
        };
        double around = Math.PI * (rx + rz) * turns, length = Math.sqrt(around * around + axis.lengthSqr()) * amount;
        chain(c, helix, length + .02, heat);
    }
    /**
     * Binds the soul bone by bone: four tight turns round the ribcage and three round each arm and
     * leg, following the limbs as the agony pose spreads them. amount grows the binding in.
     * Returns where the chain leaves the body (top of the ribcage).
     */
    private static Vec3 bindSkeleton(FilmContext c, Vec3 soul, float height, float agony, float amount) {
        double k = height / 1.95;
        int heat = 6;
        wrap(c, soul.add(0, 1.48 * k, 0), soul.add(0, .8 * k, 0), .29 * k, .16 * k, 4, amount, heat);
        double arm = .12 + 1.2 * agony;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 shoulder = soul.add(side * .3125 * k, 1.376 * k, 0);
            Vec3 hand = shoulder.add(side * Math.sin(arm) * .7 * k, -Math.cos(arm) * .7 * k, 0);
            wrap(c, shoulder.lerp(hand, .12), hand, .085 * k, .085 * k, 3, amount, heat);
            double legAngle = .05 + .06 * agony;
            Vec3 hip = soul.add(side * .125 * k, .74 * k, 0);
            Vec3 foot = hip.add(side * Math.sin(legAngle) * .7 * k, -Math.cos(legAngle) * .7 * k, 0);
            wrap(c, hip.lerp(foot, .08), foot, .085 * k, .085 * k, 3, amount, heat);
        }
        return soul.add(.29 * k, 1.48 * k, 0);
    }
    private static void actor(FilmContext c, Entity entity, Vec3 at, float facing, float scale) {
        var mc = Minecraft.getInstance();
        var dispatcher = mc.getEntityRenderDispatcher();
        float yRot = entity.getYRot(), yRotO = entity.yRotO, xRot = entity.getXRot(), xRotO = entity.xRotO;
        float body = 0, bodyO = 0, head = 0, headO = 0;
        if (entity instanceof LivingEntity living) {
            body = living.yBodyRot; bodyO = living.yBodyRotO; head = living.yHeadRot; headO = living.yHeadRotO;
            living.yBodyRot = living.yBodyRotO = living.yHeadRot = living.yHeadRotO = facing;
        }
        entity.setYRot(facing); entity.yRotO = facing; entity.setXRot(0); entity.xRotO = 0;
        dispatcher.setRenderShadow(false);
        try {
            c.pose().pushPose();
            c.pose().translate(at.x, at.y, at.z);
            c.pose().scale(scale, scale, scale);
            dispatcher.render(entity, 0, 0, 0, facing, c.partial(), c.pose(), c.buffers(), 15728880);
            c.pose().popPose();
        } finally {
            dispatcher.setRenderShadow(mc.options.entityShadows().get());
            entity.setYRot(yRot); entity.yRotO = yRotO; entity.setXRot(xRot); entity.xRotO = xRotO;
            if (entity instanceof LivingEntity living) { living.yBodyRot = body; living.yBodyRotO = bodyO; living.yHeadRot = head; living.yHeadRotO = headO; }
        }
    }
    private Entity riderEntity() { var level = Minecraft.getInstance().level; return level == null ? null : level.getEntity(rider); }
    /** The Ghost Rider himself on the stage, in a pose chosen by the film. */
    private void ghostRider(FilmContext c, Vec3 at, float scale, GhostComboMotion.Pose pose) {
        Entity player = riderEntity();
        if (player == null) return;
        GhostRiderLayer.filmPose = pose;
        try { actor(c, player, at, 0, scale); } finally { GhostRiderLayer.filmPose = null; }
    }
    /** The Ghost Rider seated on the stage bike. */
    private void riding(FilmContext c, HellCycleEntity bike, Vec3 at) {
        Entity player = riderEntity();
        if (player == null) return;
        GhostRiderLayer.filmBike = bike;
        try { actor(c, player, at.add(HellCycleRig.riderFeet(0, 0, 0)), 0, 1); } finally { GhostRiderLayer.filmBike = null; }
    }
    private static Vec3 handOf(GhostComboMotion.Pose pose, Vec3 feet, int side) {
        return GhostComboMotion.modelToWorld(feet, 0, GhostComboMotion.arm(pose, side).hand());
    }
    private static HellCycleEntity stageBike;
    private static HellCycleEntity stageBike() {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        if (stageBike == null || stageBike.level() != level)
            stageBike = new HellCycleEntity(com.FIRNI.superheromod.core.entity.ModEntities.HELL_CYCLE.get(), level);
        return stageBike;
    }
    private static void cube(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float bl) {
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}};
        float[] shade = {1f, .6f, .75f, .75f, 1.2f, .5f};
        for (int f = 0; f < 6; f++) for (int k = 0; k < 4; k++)
            b.vertex(m, faces[f][k * 3], faces[f][k * 3 + 1], faces[f][k * 3 + 2]).color(r * shade[f], g * shade[f], bl * shade[f], 1).endVertex();
    }
    private static BufferBuilder beginCubes() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }
    private static void endCubes(BufferBuilder b) {
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
    }

    // ------------------------------------------------------------------ descent
    private static Scene descent() {
        return new Scene() {
            public int skyTop() { return 0x220400; }
            public int skyBottom() { return 0x5a1404; }
            public FilmBackdrop.Params backdrop(float local, float t) { return FilmBackdrop.Params.clouds(.9f, 1f, .08f); }
            public void render(FilmContext c) {
                float t = c.time();
                Vec3 soul = new Vec3(0, Math.sin(t * .08) * .1, 0);
                SoulSkeleton.draw(c.pose(), c.buffers(), soul, 0, 2f, t, .35f, 1f, .74f, .38f, 1, true);
                soulAura(c, soul, t, .7f);
                Vec3 leave = bindSkeleton(c, soul, 2f, .35f, 1);
                Vec3 end = soul.add(-.29, .8, 0);
                for (Vec3 from : new Vec3[]{leave, end}) {
                    double side = Math.signum(from.x);
                    chain(c, u -> from.add(side * u * 1.4 + Math.sin(u * 6 + t * .25) * .25 * u, u * 11, Math.cos(u * 5 + t * .2) * .2 * u), 11.5, 7);
                }
                embers(c, 220, 20, 18, .55, .85f, .75f);
            }
        };
    }

    // ------------------------------------------------------------------ highway: one end to the other
    /** Down the whole road: out of the haze, past the camera, on to the far end. */
    private static double passZ(float local) { double k = Math.min(1, local / (THROW - ROAD)); return -340 + 760 * k * k; }
    private Scene road() {
        return new Scene() {
            public int skyTop() { return 0x050000; }
            public int skyBottom() { return 0x300602; }
            public FilmBackdrop.Params backdrop(float local, float t) { return FilmBackdrop.Params.hell(0, 0); }
            public void render(FilmContext c) {
                float local = c.local();
                double z = passZ(local);
                drawRoad(c);
                for (int i = 1; i < 34; i++) {
                    double k = i / 34.0, tz = z - i * 1.6;
                    vol(c, v(Math.sin(i * 1.3) * .08, .25 + .25 * (1 - k), tz), .3 * (1 - k) + .1, .45 * (1 - k) + .12, (float) hash(i), (float) (.85 * (1 - k)), (float) (1 - .6 * k));
                }
                var bike = stageBike();
                Vec3 at = v(0, 0, z);
                if (bike != null) {
                    bike.filmPose(2.8f, local * 75);
                    actor(c, bike, at, 0, 1);
                    riding(c, bike, at);
                }
                glow(c, at.add(0, 1.05, 1.1), 1.2 + Math.max(0, -z) * .02, 1f);
                embers(c, 160, 26, 14, .06, .85f, .6f);
            }
        };
    }
    /** A road running to the horizon both ways, burning centre line, fire along both shoulders. */
    private static void drawRoad(FilmContext c) {
        var b = beginCubes();
        var m = c.pose().last().pose();
        for (int i = 0; i < 360; i++) {
            float z0 = 900 - i * 5f, z1 = z0 - 5;
            float[] c0 = asphalt(z0), c1 = asphalt(z1);
            b.vertex(m, -3.6f, .01f, z0).color(c0[0], c0[1], c0[2], 1).endVertex();
            b.vertex(m, 3.6f, .01f, z0).color(c0[0], c0[1], c0[2], 1).endVertex();
            b.vertex(m, 3.6f, .01f, z1).color(c1[0], c1[1], c1[2], 1).endVertex();
            b.vertex(m, -3.6f, .01f, z1).color(c1[0], c1[1], c1[2], 1).endVertex();
        }
        endCubes(b);
        for (int i = 0; i < 160; i++) {
            double z0 = 470 - i * 6, z1 = z0 - 3;
            streak(c, v(0, .03, z0), v(0, .03, z1), .07, .55f, .85f, haze(z0));
        }
        for (int i = 0; i < 90; i++) {
            double fz = 300 - i * 7;
            float fade = haze(fz);
            for (int side = -1; side <= 1; side += 2) {
                double flicker = .8 + .2 * Math.sin(c.time() * .6 + i * 1.7 + side);
                vol(c, v(side * 4.1, .55 * flicker, fz), .45 * flicker, .7 * flicker, (float) hash(i * 2 + side), .75f * fade, .9f);
            }
        }
    }
    private static float haze(double z) { return (float) Math.exp(-Math.abs(10 - z) * .004); }
    private static float[] asphalt(double z) {
        float near = haze(z);
        return new float[]{Mth.lerp(near, .3f, .045f), Mth.lerp(near, .045f, .03f), Mth.lerp(near, 0f, .03f)};
    }

    // ------------------------------------------------------------------ 1. throw: off the bike, chain into the lens
    private Scene throwing() {
        return new Scene() {
            public int skyTop() { return 0x050000; }
            public int skyBottom() { return 0x300602; }
            public FilmBackdrop.Params backdrop(float local, float t) { return FilmBackdrop.Params.hell(0, 0); }
            public void render(FilmContext c) {
                float local = c.local(), t = c.time();
                drawRoad(c);
                var bike = stageBike();
                // The bike slides in and stops.
                float slide = PenanceClient.ease(Math.min(1, local / STOP));
                double bz = -40 + 40 * (1 - Math.pow(1 - Math.min(1, local / STOP), 2.5));
                Vec3 bikeAt = v(0, 0, bz);
                if (bike != null) {
                    bike.filmPose(2.8f * (1 - slide), local * 40 * (1 - slide));
                    actor(c, bike, bikeAt, 0, 1);
                }
                float skid = PenanceClient.ease((local - 4) / 3f) * (1 - PenanceClient.ease((local - 14) / 6f));
                for (int i = 0; i < 10 && skid > 0; i++) vol(c, v(-.4 + hash(i) * 1.4, .3 + hash(i + 3) * .7, bz - .8 - hash(i + 7) * 1.5), .55, .55, (float) hash(i), skid * .55f, .25f);
                if (local < STOP) {
                    if (bike != null) riding(c, bike, bikeAt);
                } else if (local < OFF) {
                    // He swings off the seat and lands beside it.
                    float k = PenanceClient.ease((local - STOP) / (OFF - STOP));
                    Vec3 seat = bikeAt.add(HellCycleRig.riderFeet(0, 0, 0));
                    Vec3 at = seat.lerp(THROWER, k).add(0, Math.sin(Math.PI * k) * .5, 0);
                    ghostRider(c, at, 1, GhostComboMotion.pose(0, 0));
                } else {
                    var pose = GhostComboMotion.cast(Math.min((local - OFF) * .3f, 6));
                    ghostRider(c, THROWER, 1, pose);
                    Vec3 hand = handOf(pose, THROWER, -1);
                    if (local < RELEASE) {
                        // Wind-up: the chain whirls behind him, getting faster.
                        double spin = Math.pow((local - OFF) / (RELEASE - OFF), 1.6) * Math.PI * 4;
                        Vec3 tip = hand.add(0, .2 + Math.cos(spin) * 1.1, -.6 + Math.sin(spin) * 1.1);
                        arcChain(c, hand, hand.lerp(tip, .5).add(0, .25, 0), tip, 7);
                        glow(c, tip, .5, .8f);
                    } else {
                        // Release: an arc over his shoulder and straight at the camera.
                        float fly = PenanceClient.ease(Math.min(1, (local - RELEASE) / (HIT - RELEASE)));
                        Vec3 tip = hand.lerp(LENS.add(0, -.05, -.25), fly);
                        Vec3 control = hand.lerp(tip, .5).add(0, .9 * (1 - fly) + .15, 0);
                        arcChain(c, hand, control, tip, 8);
                        for (int i = 0; i < 6; i++) vol(c, tip.lerp(control, i * .06), .22 - i * .025, .3 - i * .03, i * .2f, .9f - i * .12f, .95f);
                        glow(c, tip, .9, 1f);
                    }
                }
                embers(c, 120, 18, 12, .06, .85f, .55f);
            }
        };
    }
    // ------------------------------------------------------------------ 2. chase: riding the chain tip to the soul
    private static Scene chase() {
        return new Scene() {
            public int skyTop() { return 0x050000; }
            public int skyBottom() { return 0x200400; }
            public FilmBackdrop.Params backdrop(float local, float t) { return FilmBackdrop.Params.abyss(1.2f); }
            public void render(FilmContext c) {
                float t = c.time();
                Vec3 cam = c.camera();
                Vec3 tip = cam.add(0, -.32, 1.3);
                straightChain(c, v(0, 1.4, -4), tip, .15, 8);
                for (int i = 0; i < 8; i++) vol(c, tip.add(0, 0, -i * .45), .2 - i * .015, .2 - i * .015, i * .17f, .9f - i * .1f, .95f);
                glow(c, tip, .7, 1f);
                // Dark chains rushing past on both sides, swaying.
                for (int i = 0; i < 12; i++) {
                    double side = i % 2 == 0 ? -1 : 1, x = side * (1.5 + (i / 2) * .55), y = .2 + hash(i) * 3;
                    double sway = Math.sin(t * .2 + i) * .4, phase = i + t * .1;
                    final int n = i;
                    chain(c, u -> v(x + Math.sin(u * 7 + n) * .3, y + Math.sin(u * 5 + phase) * .6 + sway * u, -10 + u * 70), 72, 1);
                }
                // Sparks streaming past the lens.
                for (int i = 0; i < 120; i++) {
                    double a = hash(i) * Math.PI * 2, r = 1 + hash(i + 3) * 2.6;
                    double z = cam.z + 1 + ((hash(i + 7) - t * .08) % 1 + 1) % 1 * 26;
                    Vec3 head = v(Math.cos(a) * r, cam.y + Math.sin(a) * r * .8, z);
                    streak(c, head.add(0, 0, 1.4), head, .015, .55f, .9f, .6f);
                }
                Vec3 soul = v(0, .3, 26);
                SoulSkeleton.draw(c.pose(), c.buffers(), soul, 180, 2f, t, .45f, 1f, .74f, .38f, 1, true);
                soulAura(c, soul, t, .8f);
                glow(c, soul.add(0, 1, 0), 3.5, .8f);
            }
        };
    }
    // ------------------------------------------------------------------ 3. rise: already lifted, held in a pillar of fire
    private static Scene rise() {
        return new Scene() {
            public int skyTop() { return 0x030000; }
            public int skyBottom() { return 0x1a0300; }
            public FilmBackdrop.Params backdrop(float local, float t) { return FilmBackdrop.Params.abyss(.3f); }
            public void render(FilmContext c) {
                float local = c.local(), t = c.time();
                Vec3 soul = HELD.add(0, PenanceClient.ease(local / (BIND - RISE)) * .25, 0);
                pillar(c, t, 1);
                SoulSkeleton.draw(c.pose(), c.buffers(), soul, 0, 2f, t, .5f, 1f, .8f, .5f, 1, true);
                Vec3[][] cross = {{v(-8, -2, -3), v(8, 8, 2)}, {v(7, -1, -4), v(-7, 9, 1)}, {v(-9, 5, 3), v(9, 3, -5)}, {v(-6, 10, -2), v(6, -3, 2)}};
                for (Vec3[] line : cross) straightChain(c, line[0], line[1], .3, 2);
                embers(c, 140, 14, 14, .15, .9f, .6f);
            }
        };
    }
    /** Column of fire with light raining down through it. */
    private static void pillar(FilmContext c, float t, float strength) {
        for (int i = 0; i < 18; i++) {
            double y = -2 + i * .8, a = t * .1 + i * 2.1;
            for (int k = 0; k < 3; k++) {
                double ang = a + k * 2.094;
                vol(c, v(Math.cos(ang) * .6, y, Math.sin(ang) * .6), .7, .9, (float) hash(i * 3 + k), .32f * strength, .95f);
            }
            if (i % 2 == 0) glow(c, v(0, y, 0), 2.2, .45f * strength);
        }
        for (int i = 0; i < 40; i++) {
            double r = hash(i) * 2.2, a = hash(i + 9) * Math.PI * 2;
            double y = 12 - ((hash(i + 4) + t * .09) % 1) * 15;
            streak(c, v(Math.cos(a) * r, y + 2, Math.sin(a) * r), v(Math.cos(a) * r, y, Math.sin(a) * r), .015 + hash(i + 2) * .02, .55f, 1, .7f * strength);
        }
    }
    // ------------------------------------------------------------------ 4. bind: the chain coils round every bone
    private static Scene bind() {
        return new Scene() {
            public int skyTop() { return 0x030000; }
            public int skyBottom() { return 0x1a0300; }
            public FilmBackdrop.Params backdrop(float local, float t) { return FilmBackdrop.Params.abyss(0); }
            public void render(FilmContext c) {
                float local = c.local(), t = c.time();
                Vec3 soul = HELD.add(0, .25, 0);
                pillar(c, t, .6f);
                SoulSkeleton.draw(c.pose(), c.buffers(), soul, 0, 2f, t, .7f, 1f, .8f, .5f, 1, true);
                Vec3 leave = bindSkeleton(c, soul, 2f, .7f, PenanceClient.ease(local / 9f));
                straightChain(c, leave, v(-7, 10, 5), .4, 6);
            }
        };
    }
    // ------------------------------------------------------------------ 5. claw: his burning hand closes on the lens
    private static Scene claw() {
        return new Scene() {
            public int skyTop() { return 0x030000; }
            public int skyBottom() { return 0x240500; }
            public FilmBackdrop.Params backdrop(float local, float t) { return FilmBackdrop.Params.abyss(0); }
            public void render(FilmContext c) {
                float local = c.local();
                float curl = PenanceClient.ease(local / 10f) * 1.25f;
                Vec3 centre = v(0, 1.75, 1.7);
                float scale = 1.5f;
                drawHand(c, centre, scale, curl);
                double[] xs = {-.36, -.12, .12, .36};
                double[] lens = {.45, .35, .28};
                for (int f = 0; f < 4; f++) {
                    Vec3 p = v(xs[f], .5, 0);
                    for (int s = 0; s < 3; s++) {
                        double angle = (s + 1) * curl;
                        p = p.add(0, Math.cos(angle) * lens[s], Math.sin(angle) * lens[s]);
                        vol(c, centre.add(p.scale(scale)).add(0, .1, 0), .25, .35, (float) hash(f * 3 + s), .55f, .95f);
                    }
                    glow(c, centre.add(p.scale(scale)), .9, .7f);
                }
                glow(c, centre, 2.6, .5f);
                embers(c, 120, 10, 8, .12, .9f, .6f);
            }
        };
    }
    /** Charred glove: palm, four three-jointed fingers curling toward the lens, a thumb. */
    private static void drawHand(FilmContext c, Vec3 centre, float scale, float curl) {
        PoseStack pose = c.pose();
        var b = beginCubes();
        pose.pushPose();
        pose.translate(centre.x, centre.y, centre.z);
        pose.scale(scale, scale, scale);
        cube(b, pose.last().pose(), -.5f, -.5f, -.15f, .5f, .5f, .15f, .07f, .055f, .05f);
        cube(b, pose.last().pose(), -.45f, -.85f, -.13f, .45f, -.5f, .13f, .05f, .04f, .04f);
        float[] xs = {-.36f, -.12f, .12f, .36f};
        float[] lens = {.45f, .35f, .28f};
        for (float x : xs) {
            pose.pushPose();
            pose.translate(x, .5f, 0);
            for (int s = 0; s < 3; s++) {
                pose.mulPose(Axis.XP.rotation(curl));
                cube(b, pose.last().pose(), -.1f, 0, -.1f, .1f, lens[s], .1f, .07f, .055f, .05f);
                cube(b, pose.last().pose(), -.06f, lens[s] - .06f, .08f, .06f, lens[s], .12f, .55f, .55f, .6f);
                pose.translate(0, lens[s], 0);
            }
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(.55f, -.1f, .05f);
        pose.mulPose(Axis.ZP.rotation(-.7f + curl * .4f));
        pose.mulPose(Axis.XP.rotation(curl * .8f));
        cube(b, pose.last().pose(), -.11f, 0, -.11f, .11f, .4f, .11f, .07f, .055f, .05f);
        pose.popPose();
        pose.popPose();
        endCubes(b);
    }
    // ------------------------------------------------------------------ 6. web: pinned in a web of chains
    private static final Vec3[] ANCHORS = anchors();
    private static Vec3[] anchors() {
        Vec3[] a = new Vec3[14];
        for (int i = 0; i < a.length; i++) {
            double y = 1 - 2 * (i + .5) / a.length, r = Math.sqrt(1 - y * y), phi = i * 2.399963;
            a[i] = v(Math.cos(phi) * r * 11, 1 + y * 8, Math.sin(phi) * r * 11 - 3);
        }
        return a;
    }
    private static final Vec3[] HOLDS = {v(-.95, 1.6, 0), v(.95, 1.6, 0), v(-.2, .05, 0), v(.2, .05, 0), v(0, 1.55, 0), v(0, .9, 0), v(-.5, 1.4, 0)};
    private static Scene web() {
        return new Scene() {
            public int skyTop() { return 0x030000; }
            public int skyBottom() { return 0x240500; }
            public FilmBackdrop.Params backdrop(float local, float t) { return FilmBackdrop.Params.abyss(0); }
            public void render(FilmContext c) {
                float local = c.local(), t = c.time();
                SoulSkeleton.draw(c.pose(), c.buffers(), Vec3.ZERO, 0, 2f, t, 1, 1f, .8f, .5f, 1, true);
                glow(c, v(0, 1, 0), 3.2, .9f);
                for (int i = 0; i < ANCHORS.length; i++) {
                    Vec3 a = ANCHORS[i], b = HOLDS[i % HOLDS.length];
                    straightChain(c, a, b, .2, 3);
                    double s = (hash(i) + local * .07) % 1;
                    Vec3 side = b.subtract(a).cross(v(0, 1, 0)).normalize();
                    Vec3 last = a.lerp(b, s);
                    for (int k = 1; k <= 4; k++) {
                        Vec3 next = a.lerp(b, Math.min(1, s + k * .018)).add(side.scale((hash(i * 7 + k + Math.floor(t)) - .5) * .35));
                        streak(c, last, next, .03, .55f, 1, .95f);
                        last = next;
                    }
                }
                embers(c, 120, 18, 12, .1, .9f, .5f);
            }
        };
    }
    // ------------------------------------------------------------------ 7. cage: chains collapse, an iron cage slams down, shards; his skull
    private static final float CAGE_LAND = 7;
    private Scene cage() {
        return new Scene() {
            public int skyTop() { return 0x030000; }
            public int skyBottom() { return 0x240500; }
            public FilmBackdrop.Params backdrop(float local, float t) { return FilmBackdrop.Params.abyss(0); }
            public void render(FilmContext c) {
                float local = c.local(), t = c.time();
                float collapse = PenanceClient.ease(local / 6f);
                if (local < FACE - CAGE) {
                    SoulSkeleton.draw(c.pose(), c.buffers(), Vec3.ZERO, 0, 2f, t, 1, 1f, .85f, .6f, 1, true);
                    for (int i = 0; i < ANCHORS.length; i++) {
                        Vec3 b = HOLDS[i % HOLDS.length], a = ANCHORS[i].lerp(b.add(ANCHORS[i].subtract(b).normalize().scale(1.4)), collapse);
                        straightChain(c, a, b, .1, 4);
                    }
                    drawCage(c, local);
                    float burst = local - CAGE_LAND;
                    if (burst > 0) for (int i = 0; i < 40; i++) {
                        Vec3 dir = v(hash(i) - .5, hash(i + 9) * .6, hash(i + 21) - .5).normalize();
                        double d = burst * (.3 + hash(i + 4) * .5);
                        Vec3 head = v(0, -.5, 0).add(dir.scale(d + 1.4)), tail = head.subtract(dir.scale(.3));
                        streak(c, tail, head, .05 + hash(i + 2) * .05, .55f, .9f, Math.max(0, 1 - burst / 10));
                    }
                }
                ghostRider(c, GIANT, 1, GhostComboMotion.pose(0, 0));
                embers(c, 140, 16, 12, .1, .9f, .55f);
            }
        };
    }
    /** Dark iron cage: eight bars and two rings, dropping from above and slamming down round the soul. */
    private static void drawCage(FilmContext c, float local) {
        float fall = (float) Math.pow(Mth.clamp((local - 2) / (CAGE_LAND - 2), 0, 1), 2.2);
        float bounce = local > CAGE_LAND ? (float) (Math.exp(-(local - CAGE_LAND) * .8) * Math.sin((local - CAGE_LAND) * 2.4) * .12) : 0;
        float base = Mth.lerp(fall, 9f, -.6f) + bounce;
        float radius = 1.45f, height = 3.6f, bar = .07f;
        var b = beginCubes();
        PoseStack pose = c.pose();
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < 8; i++) {
            float a = (float) (i * Math.PI / 4);
            float x = (float) Math.cos(a) * radius, z = (float) Math.sin(a) * radius;
            cube(b, m, x - bar, base, z - bar, x + bar, base + height, z + bar, .08f, .065f, .06f);
            cube(b, m, x - bar * .5f, base + height, z - bar * .5f, x + bar * .5f, base + height + .35f, z + bar * .5f, .1f, .08f, .07f);
        }
        for (int ring = 0; ring < 2; ring++) {
            float y = base + (ring == 0 ? .1f : height - .1f);
            for (int i = 0; i < 8; i++) {
                double a0 = i * Math.PI / 4, a1 = (i + 1) * Math.PI / 4;
                Vec3 p0 = v(Math.cos(a0) * radius, y, Math.sin(a0) * radius), p1 = v(Math.cos(a1) * radius, y, Math.sin(a1) * radius);
                pose.pushPose();
                pose.translate(p0.x, p0.y, p0.z);
                Vec3 d = p1.subtract(p0);
                pose.mulPose(new org.joml.Quaternionf().rotationTo(1, 0, 0, (float) d.x, (float) d.y, (float) d.z));
                cube(b, pose.last().pose(), 0, -bar, -bar, (float) d.length(), bar, bar, .07f, .055f, .05f);
                pose.popPose();
            }
        }
        endCubes(b);
        // Heat where the bars hit the ground.
        if (local > CAGE_LAND) for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            glow(c, v(Math.cos(a) * radius, -.5, Math.sin(a) * radius), .8, Math.max(0, 1 - (local - CAGE_LAND) / 8f));
        }
    }
    // ------------------------------------------------------------------ 8. pull: the soul's light gathered in front of his face
    /** Pull beat, local ticks: the wide shot, the light swelling, the rays, the flash. */
    private static final float PULL_WIDE = 22, SWELL = 56, BURST = 72;
    private static final Vec3 SOUL_PULL = new Vec3(.95, .55, -1.9);
    private Scene pull() {
        return new Scene() {
            public int skyTop() { return 0x030000; }
            public int skyBottom() { return 0x240500; }
            public FilmBackdrop.Params backdrop(float local, float t) { return FilmBackdrop.Params.abyss(PenanceClient.ease((local - SWELL) / 14f) * .9f); }
            public void render(FilmContext c) {
                float local = c.local(), t = c.time();
                ghostRider(c, GIANT, 1, GhostComboMotion.pose(0, 0));
                Vec3 point = FACE_POINT;
                // The soul fades as its light is drawn out of it.
                float drain = PenanceClient.ease((local - 4) / 36f);
                if (drain < 1) {
                    SoulSkeleton.draw(c.pose(), c.buffers(), SOUL_PULL, -110, 1.6f, t, .8f, 1f, .8f, .5f, 1 - drain, true);
                    for (int i = 0; i < 60; i++) {
                        double life = (hash(i) + local * .035) % 1;
                        Vec3 from = SOUL_PULL.add((hash(i + 3) - .5) * .5, hash(i + 5) * 1.6, (hash(i + 8) - .5) * .4);
                        double a = i * 2.4 + life * 6;
                        Vec3 swirl = c.viewRight().scale(Math.cos(a) * (1 - life) * .5).add(c.viewUp().scale(Math.sin(a) * (1 - life) * .5));
                        Vec3 at = from.lerp(point, life * life).add(swirl);
                        Vec3 next = from.lerp(point, Math.min(1, life * life + .06)).add(swirl.scale(.8));
                        streak(c, at, next, .025, .55f, 1, (float) (Math.sin(Math.PI * life) * (1 - drain * .7)));
                    }
                }
                // Spiralling motes converging on the point in front of his face.
                for (int i = 0; i < 90; i++) {
                    double life = (hash(i) + local * .03) % 1, r = 2.6 * (1 - life), a = i * 2.4 + t * .2 + life * 5;
                    Vec3 at = point.add(c.viewRight().scale(Math.cos(a) * r)).add(c.viewUp().scale(Math.sin(a) * r * .8));
                    streak(c, at, at.lerp(point, .18), .022, .55f, .95f, (float) (Math.sin(Math.PI * life)));
                }
                // The light itself: a white-hot core inside layered bloom, swelling.
                float swell = PenanceClient.ease(local / SWELL);
                float burst = PenanceClient.ease((local - SWELL) / (BURST - SWELL));
                float pulse = .92f + .08f * Mth.sin(t * 1.8f);
                glow(c, point, (.4 + 1.6 * swell + 2.5 * burst) * pulse, 1f);
                glow(c, point, (1 + 3 * swell + 4 * burst) * pulse, .55f);
                vol(c, point, .1 + .35 * swell + .3 * burst, .1 + .35 * swell + .3 * burst, .2f, 1f, 1f);
                for (int k = 0; k < 2; k++) {
                    Vec3 axis = (k == 0 ? c.viewRight() : c.viewUp()).scale(.25 + .9 * swell + 1.5 * burst);
                    streak(c, point.subtract(axis), point.add(axis), .02 + .02 * swell, .46f, 1, .9f);
                }
                // Rays tear out of it, then the flash.
                for (int i = 0; i < 30 && burst > 0; i++) {
                    double a = i * 2 * Math.PI / 30 + hash(i) * .3;
                    Vec3 dir = c.viewRight().scale(Math.cos(a)).add(c.viewUp().scale(Math.sin(a)));
                    double length = burst * (4 + 7 * hash(i + 3));
                    streak(c, point, point.add(dir.scale(length)), .02 + .07 * burst * hash(i + 7), .46f, 1, burst * .95f);
                }
                embers(c, 100, 12, 10, .1, .9f, .5f);
            }
        };
    }
}
