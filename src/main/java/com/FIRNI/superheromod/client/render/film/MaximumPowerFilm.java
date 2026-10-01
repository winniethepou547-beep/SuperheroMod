package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import com.FIRNI.superheromod.heroes.cyclops.MaximumPowerSession;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.core.cinematic.ActorPose.*;
import static com.FIRNI.superheromod.client.render.film.FilmFx.*;

/**
 * MAXIMUM POWER, the film (21 s). Cyclops stands at the stage origin facing +z, the target
 * 3.2 blocks in front of him, in a dark arena of fog and wet stone.
 *   0-36     world   behind Cyclops, then into the visor
 *   36-100   alone in the dark: the target looks round; a thin blast from nowhere; a red dot in the fog
 *   100-140  the glow: Cyclops out of the dark, light gathering in the visor
 *   140-236  the beam: it hits, drives the target back, they dig in and walk into it
 *   236-300  the hand goes to the visor; the beam stops; silence; the light builds
 *   300-348  MAXIMUM POWER: the visor comes off, the giant beam, the guard breaks, the launch
 *   348-392  into the fog, and the explosion far away in it
 *   392-420  world   back to the game as the target is hurled away
 */
final class MaximumPowerFilm implements Film {
    static final String ID = MaximumPowerSession.ID;
    static final int STAGE = 36, W2 = MaximumPowerSession.RELEASE, TOTAL = MaximumPowerSession.TOTAL;
    /** Beats in film ticks. */
    static final float FIRST = 64, FIRST_OFF = 72, FIRE = 142, HIT = 148, PLANT = 174, STEP1 = 214, STEP2 = 230,
            REACH = 262, GRIP = 280, CUT = 282, GIANT = 300, BREAK = 322, LAUNCH = 334, BEAM_END = 350, EXPLODE = MaximumPowerSession.BLAST;
    static final double TARGET_Z = 3.2;
    static final Vec3 VISOR = new Vec3(0, 1.62, .24);
    private static final int[] RUBY = {0x5a0404, 0xff1a10, 0xff8070, 0xffffff};
    private static final int RED = 0xff2414, HOT = 0xff6a40;
    private final int attacker;
    private final Segment[] segments;
    private FilmCast cyclops, target;
    private static final FilmCast.Track CYCLOPS = cyclopsTrack(), TARGET = targetTrack();

    MaximumPowerFilm(int attacker) {
        this.attacker = attacker;
        Scene arena = arena();
        segments = new Segment[]{
                new Segment(0, STAGE, new FilmShot[]{
                        FilmShot.of(0, 20).path(v(-1.3, 1.9, -2.4), v(-1.0, 1.8, -1.6)).look(v(0, 1.5, 3), v(0, 1.55, 2)).fov(55, 50).shake(.02f, .02f).build(),
                        FilmShot.of(20, STAGE).path(v(1.2, 1.7, 1.9), v(.5, 1.66, .95), v(.2, 1.64, .6)).look(v(0, 1.62, 0)).fov(46, 30).shake(.02f, .04f).build()},
                        null, 0, 0),
                new Segment(STAGE, W2, new FilmShot[]{
                        // Alone in the dark.
                        FilmShot.of(STAGE, FIRST).path(v(2.3, 1.4, 4.1), v(1.95, 1.42, 3.95)).look(v(0, 1.3, 3.2), v(0, 1.45, 3.2)).fov(52, 50).shake(.02f, .02f).build(),
                        FilmShot.of(FIRST, 82).path(v(1.6, .5, 2.2), v(1.3, .6, 2.3)).look(v(.3, .3, 2.7), v(0, 1.0, 3.2)).fov(44, 58).roll(-5, -1).shake(.7f, .15f).build(),
                        FilmShot.of(82, 100).path(v(.45, 1.62, 4.15), v(.42, 1.6, 3.95)).look(v(0, 1.45, 0)).fov(50, 44).shake(.03f, .03f).build(),
                        // The glow.
                        FilmShot.of(100, 122).path(v(.5, 1.6, 1.45), v(.4, 1.6, 1.2)).look(v(0, 1.58, 0), v(0, 1.6, 0)).fov(40, 38).shake(.02f, .02f).build(),
                        FilmShot.of(122, FIRE - 2).path(v(.12, 1.64, .7), v(.08, 1.64, .55)).look(v(0, 1.63, 0)).fov(32, 27).roll(4, 7).shake(.02f, .05f).build(),
                        // The beam.
                        FilmShot.of(FIRE - 2, 156).path(v(4.2, 1.5, 1.9), v(3.95, 1.45, 2.1)).look(v(0, 1.3, 2.0), v(0, 1.2, 2.6)).fov(72, 70).roll(-7, -2).shake(.45f, .18f).build(),
                        FilmShot.of(156, 180).path(v(4.8, 1.0, 3.7), v(4.7, .95, 3.9)).look(v(0, .9, 4.3), v(0, .85, 4.6)).fov(62, 62).shake(.3f, .12f).build(),
                        FilmShot.of(180, 206).path(v(1.7, .75, 3.0), v(1.4, .7, 3.2)).look(v(0, 1.0, 4.7)).fov(58, 58).roll(2, 4).shake(.14f, .14f).build(),
                        FilmShot.of(206, 236).path(v(1.1, 1.25, 6.4), v(1.3, 1.3, 6.9)).look(v(0, 1.1, 2.4)).fov(60, 60).shake(.1f, .1f).build(),
                        // The visor.
                        FilmShot.of(236, 256).path(v(-1.85, 1.55, .6), v(-1.62, 1.52, .75)).look(v(0, 1.52, 0)).fov(54, 54).shake(.04f, .04f).build(),
                        FilmShot.of(256, CUT).path(v(-1.15, 1.6, .62), v(-1.05, 1.6, .55)).look(v(0, 1.58, 0)).fov(42, 40).shake(.03f, .03f).build(),
                        FilmShot.of(CUT, GIANT).path(v(-.55, 1.66, .55), v(-.43, 1.68, .48), v(-.21, 1.67, .35), v(-.16, 1.66, .3))
                                .look(v(0, 1.66, 0)).fov(38, 26).roll(0, 5).shake(.01f, .06f).build(),
                        // Maximum power.
                        FilmShot.of(GIANT, 318).path(v(-3.8, 1.9, -.6), v(-3.5, 1.8, -.3)).look(v(0, 1.5, 2.4)).fov(66, 66).roll(-4, -1).shake(.55f, .25f).build(),
                        FilmShot.of(318, 332).path(v(3.5, .5, 3.4), v(3.3, .75, 3.5)).look(v(0, 1.2, 4.5)).fov(64, 64).roll(3, 8).shake(.35f, .85f).build(),
                        FilmShot.of(332, 340).path(v(3.8, 1.3, 4.6), v(3.7, 1.3, 4.7)).look(v(0, 1.3, 4.5), v(0, 1.5, 5.6)).fov(78, 78).roll(-10, -3).shake(1f, .5f).build(),
                        FilmShot.of(340, BLAST_SHOT).path(v(3.8, 1.45, 5.2), v(2.9, 1.6, 6.0)).look(v(0, 1.6, 7), v(0, 1.6, 10)).fov(80, 92).roll(-6, 6).shake(.45f, .45f).build(),
                        // Into the fog.
                        FilmShot.of(BLAST_SHOT, 364).path(v(.3, 1.55, 4.6), v(.2, 1.5, 5.4)).look(v(0, 1.2, 15), v(0, 1.2, 19)).fov(74, 62).shake(.2f, 0).build(),
                        FilmShot.of(364, W2).path(v(.2, 1.5, 5.4), v(.35, 1.58, 5.1)).look(v(0, 1.1, 19)).fov(62, 58).shake(.05f, .3f).build()},
                        arena, 0x200000, 5),
                new Segment(W2, TOTAL, new FilmShot[]{FilmShot.of(W2, TOTAL)
                        .path(v(3.4, 1.3, -1.0), v(4.2, 1.9, -2.0)).look(v(0, 1.0, 3), v(0, 1.2, 8)).fov(66, 60).shake(.3f, .02f).build()},
                        null, 0xFFE0D8, 4)};
    }
    private static final int BLAST_SHOT = 348;
    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }
    private FilmSessionClient.State state() { return FilmSessionClient.get(attacker); }
    @Override public Vec3 origin() { var s = state(); return s == null ? Vec3.ZERO : s.anchor; }
    @Override public Vec3 forward() { var s = state(); return s == null ? new Vec3(0, 0, 1) : FilmSessionClient.forward(s); }
    @Override public float duration() { return TOTAL; }
    @Override public Segment[] segments() { return segments; }
    @Override public float time(float partial) { var s = state(); return s == null ? duration() : FilmSessionClient.time(s, partial); }
    @Override public boolean active() { var s = state(); return s != null && ID.equals(s.film); }
    @Override public float[] impacts() { return new float[]{HIT, GIANT, LAUNCH, EXPLODE}; }
    @Override public float blendOut() { return 12; }

    // ------------------------------------------------------------------ sound
    @Override public Cue[] cues() {
        List<Cue> c = new ArrayList<>();
        add(c, 2, SoundEvents.BEACON_AMBIENT, .6f, 1.6f); add(c, 26, SoundEvents.BEACON_POWER_SELECT, .5f, 1.8f);
        add(c, STAGE + 1, SoundEvents.SOUL_ESCAPE, .5f, .6f); add(c, 46, SoundEvents.GRAVEL_STEP, .6f, .7f);
        add(c, FIRST, SoundEvents.BLAZE_SHOOT, 1f, 1.5f); add(c, FIRST + 1, SoundEvents.GENERIC_EXPLODE, .4f, 1.7f);
        add(c, FIRST + 3, SoundEvents.GRAVEL_BREAK, .8f, .8f);
        add(c, 101, SoundEvents.BEACON_AMBIENT, .7f, 1.8f); add(c, 116, SoundEvents.BEACON_AMBIENT, .9f, 1.2f);
        add(c, 132, SoundEvents.BLAZE_AMBIENT, 1f, .45f); add(c, 136, SoundEvents.BEACON_POWER_SELECT, .8f, 1.2f);
        add(c, FIRE, SoundEvents.LIGHTNING_BOLT_THUNDER, .9f, 1.2f); add(c, FIRE, SoundEvents.BLAZE_SHOOT, 1f, .9f);
        add(c, HIT, SoundEvents.GENERIC_EXPLODE, .9f, 1.4f); add(c, HIT + 2, SoundEvents.FIRE_EXTINGUISH, .8f, .6f);
        for (float t = HIT + 10; t < CUT; t += 22) add(c, t, SoundEvents.BEACON_AMBIENT, .9f, .9f + (t - HIT) * .002f);
        add(c, LANDED, SoundEvents.GRAVEL_BREAK, 1f, .6f); add(c, LANDED + 1, SoundEvents.PLAYER_ATTACK_KNOCKBACK, .7f, .6f);
        add(c, 164, SoundEvents.GRAVEL_STEP, .9f, .5f); add(c, PLANT, SoundEvents.ANVIL_LAND, .8f, 1.6f);
        add(c, STEP1 - 2, SoundEvents.GRAVEL_STEP, 1f, .6f); add(c, STEP2 - 2, SoundEvents.GRAVEL_STEP, 1f, .6f);
        add(c, 266, SoundEvents.BEACON_AMBIENT, 1f, .4f); add(c, CUT, SoundEvents.BEACON_DEACTIVATE, .9f, 2f);
        add(c, 290, SoundEvents.BEACON_POWER_SELECT, 1f, .5f); add(c, 296, SoundEvents.BLAZE_AMBIENT, 1f, .3f);
        add(c, GIANT, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, .6f); add(c, GIANT, SoundEvents.GENERIC_EXPLODE, 1f, .4f);
        add(c, GIANT + 1, SoundEvents.BLAZE_SHOOT, 1f, .5f);
        add(c, BREAK, SoundEvents.GENERIC_EXPLODE, .8f, .8f); add(c, BREAK + 1, SoundEvents.SHIELD_BREAK, 1f, .7f);
        add(c, LAUNCH, SoundEvents.GENERIC_EXPLODE, 1f, 1f); add(c, LAUNCH + 1, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .6f);
        add(c, BEAM_END, SoundEvents.BEACON_DEACTIVATE, .8f, .8f);
        // The light reaches us before the sound: the explosion is far away in the fog.
        add(c, EXPLODE + 4, SoundEvents.GENERIC_EXPLODE, 1f, .35f); add(c, EXPLODE + 6, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, .5f);
        add(c, W2 + 1, SoundEvents.BEACON_DEACTIVATE, .6f, .8f);
        return c.toArray(new Cue[0]);
    }
    private static void add(List<Cue> list, float t, SoundEvent sound, float volume, float pitch) { list.add(new Cue(t, sound, volume, pitch)); }

    // ------------------------------------------------------------------ picture layer
    @Override public int grade(float t) {
        float push = ease((t - 30) / 6f) * (t < STAGE ? 1 : 0);
        if (push > 0) return ((int) (push * 190) << 24) | 0x7a0606;
        float max = t >= GIANT ? 1 - ease((t - GIANT) / 22f) : 0;
        if (max > 0) return ((int) (max * 120) << 24) | 0xff1a10;
        float blast = t >= EXPLODE ? 1 - ease((t - EXPLODE) / 16f) : 0;
        if (blast > 0) return ((int) (blast * 110) << 24) | 0xffa070;
        return 0;
    }
    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        var font = Minecraft.getInstance().font;
        float title = window(t, 286, 330, 3);
        if (title > 0) {
            String text = "MAXIMUM POWER";
            float slam = 1 + .35f * (1 - ease((t - 286) / 4f));
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * .30f, 0);
            g.pose().scale(3.4f * slam, 3.4f * slam, 1);
            g.drawString(font, text, -font.width(text) / 2, -4, HudStyle.alpha(0xFFFF3020, title), false);
            g.pose().popPose();
            String sub = "Scott Summers  ·  Cyclops";
            int sw = HudStyle.captionWidth(font, sub), y = (int) (h * .30f) + 22;
            HudStyle.caption(g, font, sub, w / 2, y, HudStyle.alpha(HudStyle.TEXT, title * .85f), 0);
            g.fill(w / 2 - sw / 2 - 34, y + 3, w / 2 - sw / 2 - 8, y + 4, HudStyle.alpha(0xFFFF3020, title));
            g.fill(w / 2 + sw / 2 + 8, y + 3, w / 2 + sw / 2 + 34, y + 4, HudStyle.alpha(0xFFFF3020, title));
        }
        // Optic output: what the visor is letting through.
        float shown = window(t, FIRE, W2 - 4, 5) * (t >= CUT && t < GIANT ? .35f : 1);
        if (shown > 0) {
            float value = t < CUT ? .34f + .28f * ease((t - FIRE) / (CUT - FIRE)) : t < GIANT ? .1f : 1;
            int y = h - 30, half = 64;
            boolean max = t >= GIANT;
            int accent = max && ((int) (t / 2) & 1) == 0 ? 0xFFFFFFFF : 0xFFFF3020;
            HudStyle.caption(g, font, "Optic Output", w / 2 - half, y, HudStyle.alpha(HudStyle.MUTED, shown), -1);
            HudStyle.caption(g, font, max ? "MAX" : (int) (value * 100) + "%", w / 2 + half, y, HudStyle.alpha(accent, shown), 1);
            HudStyle.bar(g, w / 2 - half, y + 11, half * 2, value, HudStyle.alpha(accent, shown));
        }
        float top = window(t, STAGE + 4, 98, 5);
        if (top > 0) HudStyle.caption(g, font, "[ Danger Room · Lights Out ]", w / 2, 14, HudStyle.alpha(0xFFFF3020, top), 0);
    }

    // ------------------------------------------------------------------ choreography
    private static FilmCast.Track cyclopsTrack() {
        ActorPose rest = of().j(RIGHT_UPPER_ARM, 0, 0, 5).j(LEFT_UPPER_ARM, 0, 0, -5).j(RIGHT_LOWER_ARM, -9, 0, 0).j(LEFT_LOWER_ARM, -9, 0, 0);
        ActorPose dominant = of().j(CHEST, -4, 0, 0).j(HEAD, -3, 0, 0).j(RIGHT_UPPER_ARM, 2, 0, 9).j(LEFT_UPPER_ARM, 2, 0, -9)
                .j(RIGHT_LOWER_ARM, -12, 0, 0).j(LEFT_LOWER_ARM, -12, 0, 0).j(RIGHT_UPPER_LEG, 0, 0, 4).j(LEFT_UPPER_LEG, 0, 0, -4);
        ActorPose firing = dominant.copy().j(CHEST, -7, 0, 0).j(HEAD, -6, 0, 0).j(RIGHT_UPPER_LEG, -10, 0, 5).j(RIGHT_LOWER_LEG, 10, 0, 0)
                .j(LEFT_UPPER_LEG, 12, 0, -5).j(LEFT_LOWER_LEG, 4, 0, 0).crouch(.05f);
        ActorPose reach = firing.copy().j(RIGHT_UPPER_ARM, -42, 0, 14).j(RIGHT_LOWER_ARM, -64, 0, 0);
        ActorPose grip = firing.copy().j(HEAD, -6, 0, 0).j(RIGHT_UPPER_ARM, -78, 0, 20).j(RIGHT_LOWER_ARM, -118, 0, 0);
        ActorPose max = of().j(CHEST, -9, 0, 0).j(HEAD, -12, 0, 0).j(RIGHT_UPPER_ARM, -30, -20, 70).j(RIGHT_LOWER_ARM, -26, 0, 0)
                .j(LEFT_UPPER_ARM, 8, 0, -26).j(LEFT_LOWER_ARM, -20, 0, 0).j(RIGHT_UPPER_LEG, -14, 0, 7).j(RIGHT_LOWER_LEG, 14, 0, 0)
                .j(LEFT_UPPER_LEG, 16, 0, -7).j(LEFT_LOWER_LEG, 8, 0, 0).crouch(.12f);
        ActorPose after = max.copy().j(RIGHT_UPPER_ARM, 2, 0, 14).j(RIGHT_LOWER_ARM, -14, 0, 0).j(CHEST, -3, 0, 0).crouch(.04f);
        return new FilmCast.Track().key(0, 0, rest).key(120, 20, dominant).key(FIRE + 2, 4, firing, impactChain())
                .key(REACH + 8, 14, reach).key(GRIP, 12, grip).key(GIANT + 2, 3, max, impactChain()).key(BEAM_END + 18, 20, after);
    }
    private static FilmCast.Track targetTrack() {
        ActorPose neutral = of().j(RIGHT_UPPER_ARM, 0, 0, 4).j(LEFT_UPPER_ARM, 0, 0, -7).j(RIGHT_LOWER_ARM, -8, 0, 0).j(LEFT_LOWER_ARM, -11, 0, 0);
        ActorPose lookRight = of().j(HEAD, 4, 28, 0).j(CHEST, 0, 6, 0).j(RIGHT_UPPER_ARM, 0, 0, 5).j(LEFT_UPPER_ARM, 0, 0, -7)
                .j(RIGHT_LOWER_ARM, -10, 0, 0).j(LEFT_LOWER_ARM, -12, 0, 0);
        ActorPose lookLeft = of().j(HEAD, 2, -38, 0).j(CHEST, 0, -12, 0).j(LEFT_UPPER_ARM, -26, 0, -14).j(LEFT_LOWER_ARM, -38, 0, 0)
                .j(RIGHT_UPPER_ARM, 0, 0, 5).j(RIGHT_LOWER_ARM, -12, 0, 0);
        ActorPose flinch = of().j(HEAD, -10, 46, 6).j(CHEST, -6, 20, 0).j(RIGHT_UPPER_ARM, -48, 0, 16).j(RIGHT_LOWER_ARM, -78, 0, 0)
                .j(LEFT_UPPER_ARM, -20, 0, -18).j(LEFT_LOWER_ARM, -30, 0, 0).j(RIGHT_UPPER_LEG, 14, 0, 0).j(RIGHT_LOWER_LEG, 10, 0, 0)
                .j(LEFT_UPPER_LEG, -10, 0, 0).crouch(.08f);
        ActorPose wary = of().j(HEAD, -4, 10, 7).j(CHEST, 3, 4, 0).j(RIGHT_UPPER_ARM, -14, 0, 9).j(RIGHT_LOWER_ARM, -26, 0, 0)
                .j(LEFT_UPPER_ARM, -10, 0, -10).j(LEFT_LOWER_ARM, -22, 0, 0);
        // The forearms crossed in an X in front of the face, the head tucked behind them, the front
        // knee bent and the back leg driven out behind: a body leaning into the beam.
        ActorPose block = of().j(CHEST, 22, 0, 0).j(HEAD, 16, 0, 0)
                .j(RIGHT_UPPER_ARM, -82, -64, 0).j(RIGHT_LOWER_ARM, -72, 0, 0).j(LEFT_UPPER_ARM, -88, 64, 0).j(LEFT_LOWER_ARM, -70, 0, 0)
                .j(LEFT_UPPER_LEG, -38, 0, -6).j(LEFT_LOWER_LEG, 48, 0, 0).j(RIGHT_UPPER_LEG, 24, 0, 6).j(RIGHT_LOWER_LEG, 26, 0, 0).crouch(.30f);
        // Blown off the ground: the X torn open, head thrown back, legs trailing.
        ActorPose blown = of().j(CHEST, -30, 0, 0).j(HEAD, -26, 0, 0).j(HIPS, 8, 0, 0)
                .j(RIGHT_UPPER_ARM, -64, -30, 26).j(RIGHT_LOWER_ARM, -40, 0, 0).j(LEFT_UPPER_ARM, -60, 30, -26).j(LEFT_LOWER_ARM, -44, 0, 0)
                .j(RIGHT_UPPER_LEG, -40, 0, 6).j(RIGHT_LOWER_LEG, 52, 0, 0).j(LEFT_UPPER_LEG, -12, 0, -6).j(LEFT_LOWER_LEG, 30, 0, 0).body(0, 16);
        // Landed and skidding: deep crouch, one hand down to the floor, the other arm still up.
        ActorPose skid = of().j(CHEST, 38, 0, 0).j(HEAD, 6, 0, 0)
                .j(RIGHT_UPPER_ARM, -82, -64, 0).j(RIGHT_LOWER_ARM, -72, 0, 0).j(LEFT_UPPER_ARM, -24, 0, -34).j(LEFT_LOWER_ARM, -14, 0, 0)
                .j(LEFT_UPPER_LEG, -72, 0, -8).j(LEFT_LOWER_LEG, 98, 0, 0).j(RIGHT_UPPER_LEG, 28, 0, 8).j(RIGHT_LOWER_LEG, 70, 0, 0).crouch(.58f);
        ActorPose rising = skid.copy().j(CHEST, 30, 0, 0).j(LEFT_UPPER_ARM, -60, 30, -20).j(LEFT_LOWER_ARM, -50, 0, 0)
                .j(LEFT_UPPER_LEG, -55, 0, -6).j(LEFT_LOWER_LEG, 75, 0, 0).crouch(.45f);
        // Each step forward: the back leg swings through, then the weight drops onto it.
        ActorPose lift1 = block.copy().j(CHEST, 30, 0, 0).j(RIGHT_UPPER_LEG, -48, 0, 4).j(RIGHT_LOWER_LEG, 62, 0, 0)
                .j(LEFT_UPPER_LEG, 8, 0, -6).j(LEFT_LOWER_LEG, 24, 0, 0).crouch(.24f);
        ActorPose plant1 = block.copy().j(CHEST, 28, 0, 0).j(RIGHT_UPPER_LEG, -38, 0, 6).j(RIGHT_LOWER_LEG, 48, 0, 0)
                .j(LEFT_UPPER_LEG, 24, 0, -6).j(LEFT_LOWER_LEG, 26, 0, 0).crouch(.36f);
        ActorPose lift2 = plant1.copy().j(CHEST, 30, 0, 0).j(LEFT_UPPER_LEG, -48, 0, -4).j(LEFT_LOWER_LEG, 62, 0, 0)
                .j(RIGHT_UPPER_LEG, 8, 0, 6).j(RIGHT_LOWER_LEG, 24, 0, 0).crouch(.24f);
        ActorPose plant2 = block.copy().j(CHEST, 26, 0, 0).crouch(.34f);
        // The beam stops: the guard drops, breathing hard, looking up at him.
        ActorPose relief = of().j(CHEST, 12, 0, 0).j(HEAD, -16, 0, 0)
                .j(RIGHT_UPPER_ARM, -38, -26, 12).j(RIGHT_LOWER_ARM, -52, 0, 0).j(LEFT_UPPER_ARM, -36, 26, -12).j(LEFT_LOWER_ARM, -50, 0, 0)
                .j(LEFT_UPPER_LEG, -28, 0, -6).j(LEFT_LOWER_LEG, 34, 0, 0).j(RIGHT_UPPER_LEG, 16, 0, 6).j(RIGHT_LOWER_LEG, 18, 0, 0).crouch(.2f);
        ActorPose bracing = block.copy().j(CHEST, 34, 0, 0).j(HEAD, 22, 0, 0).j(LEFT_UPPER_LEG, -46, 0, -8).j(LEFT_LOWER_LEG, 64, 0, 0).crouch(.42f);
        ActorPose broken = of().j(CHEST, -44, 0, 0).j(HEAD, -34, 0, 8).j(HIPS, 14, 0, 0).j(RIGHT_UPPER_ARM, 48, 0, 48).j(RIGHT_LOWER_ARM, -8, 0, 0)
                .j(LEFT_UPPER_ARM, 48, 0, -48).j(LEFT_LOWER_ARM, -8, 0, 0).j(RIGHT_UPPER_LEG, -14, 0, 8).j(RIGHT_LOWER_LEG, 28, 0, 0)
                .j(LEFT_UPPER_LEG, -8, 0, -8).j(LEFT_LOWER_LEG, 22, 0, 0).crouch(.10f);
        ActorPose launched = of().j(CHEST, -58, 0, 0).j(HEAD, -48, 0, 14).j(HIPS, 20, 0, 0).j(RIGHT_UPPER_ARM, 72, 0, 62).j(RIGHT_LOWER_ARM, -16, 0, 0)
                .j(LEFT_UPPER_ARM, 66, 0, -58).j(LEFT_LOWER_ARM, -22, 0, 0).j(RIGHT_UPPER_LEG, 34, 0, 10).j(RIGHT_LOWER_LEG, 30, 0, 0)
                .j(LEFT_UPPER_LEG, 26, 0, -12).j(LEFT_LOWER_LEG, 38, 0, 0);
        ActorPose flying = of().j(CHEST, -62, 0, 0).j(HEAD, -50, 0, 20).j(RIGHT_UPPER_ARM, 80, 0, 68).j(RIGHT_LOWER_ARM, -20, 0, 0)
                .j(LEFT_UPPER_ARM, 74, 0, -64).j(LEFT_LOWER_ARM, -26, 0, 0).j(RIGHT_UPPER_LEG, 40, 0, 12).j(LEFT_UPPER_LEG, 32, 0, -14);
        return new FilmCast.Track().key(0, 0, neutral).key(48, 12, lookRight, noticeChain()).key(60, 10, lookLeft, noticeChain())
                .key(FIRST + 4, 4, flinch, noticeChain()).key(92, 16, wary)
                // The beam is coming: the arms snap up into the X just before it lands.
                .key(HIT - 1, 5, block, noticeChain()).key(HIT + 3, 4, blown, impactChain())
                .key(LANDED + 1, 4, skid, impactChain()).key(PLANT, 10, skid).key(RECOVER - 6, 10, rising).key(RECOVER, 8, block, noticeChain())
                .key(STEP1 - 3, 5, lift1).key(STEP1, 3, plant1, impactChain()).key(STEP2 - 3, 5, lift2).key(STEP2, 3, plant2, impactChain())
                .key(CUT + 10, 10, relief).key(GIANT - 1, 4, block).key(GIANT + 4, 4, bracing, impactChain())
                .key(BREAK + 3, 4, broken, impactChain()).key(LAUNCH + 3, 4, launched, launchChain()).key(356, 16, flying);
    }
    /** Beats of the struggle: lands from the blow-back, gets up into the block. */
    static final float LANDED = 158, RECOVER = 186;
    /**
     * Where the target's feet are: blown back off the ground, skidding to a stop, pushed further
     * while holding, two hard steps forward into the beam, shoved again, then launched into the fog.
     */
    static Vec3 targetFeet(float t) {
        double z = TARGET_Z, y = 0;
        float fly = Mth.clamp((t - HIT) / (LANDED - HIT), 0, 1);
        z += 1.1 * (1 - (1 - fly) * (1 - fly));
        if (t > HIT && t < LANDED) y = .38 * Math.sin(Math.PI * fly);
        float skid = Mth.clamp((t - LANDED) / (PLANT - LANDED), 0, 1);
        z += .42 * (1 - (1 - skid) * (1 - skid));
        z += .16 * ease((t - RECOVER) / 20f);
        z -= .36 * ease((t - (STEP1 - 4)) / 5f) + .36 * ease((t - (STEP2 - 4)) / 5f);
        z += .35 * ease((t - GIANT) / 6f) + .25 * ease((t - BREAK) / 10f);
        if (t < LAUNCH) return v(0, y, z);
        float k = t - LAUNCH;
        return v(Math.sin(k * .07) * .4, 1.6 * Math.sin(Math.PI * Math.min(1, k / 30f)) + .4 * Math.min(1, k / 30f), z + 15.2 * (1 - Math.exp(-k / 10)));
    }
    /** Where the beam is aimed: the crossed forearms in front of the face while they block. */
    private Vec3 beamEnd(float t) {
        if (t >= LAUNCH) return v(0, 1.25, 30);
        Vec3 feet = targetFeet(t);
        double height = target == null ? 1.8 : target.height(), crouch = TARGET.sample(t).crouch;
        return feet.add(0, height * .76 - crouch, -.45);
    }
    /** Where his eyes are: follows the crouch and the lean of his chest and head. */
    static Vec3 eyes(float t) {
        ActorPose p = CYCLOPS.sample(t);
        double a = p.rot[CHEST][0], b = a + p.rot[HEAD][0], base = 1.125 - p.crouch;
        Vec3 neck = v(0, base + .375 * Math.cos(a), .375 * Math.sin(a));
        return neck.add(0, .22 * Math.cos(b) - .27 * Math.sin(b), .22 * Math.sin(b) + .27 * Math.cos(b));
    }

    // ------------------------------------------------------------------ the stage
    private Scene arena() {
        return new Scene() {
            public int skyTop() { return 0x08080b; }
            public int skyBottom() { return 0x150404; }
            public FilmBackdrop.Params backdrop(float local, float t) {
                if (t >= EXPLODE - 2) {
                    float boom = t < EXPLODE ? 0 : (float) Math.exp(-(t - EXPLODE) * .08);
                    return FilmBackdrop.Params.arena(targetFeet(EXPLODE).add(0, 1, 0), .3f + 7 * boom, 0, 0, 0, .25f * boom * boom, 1.1f, 0);
                }
                Vec3 eye = eyes(t);
                float glow = .35f + .1f * ease((t - 84) / 16f) + 1.1f * ease((t - 100) / 40f);
                float beam = 0, radius = .35f, length = (float) (beamEnd(t).z - eye.z), flash = 0;
                if (t >= FIRE && t < CUT) beam = ease((t - FIRE) / 4f);
                if (t >= CUT && t < GIANT) { beam = 0; glow = 1 + 2 * ease((t - CUT) / (GIANT - CUT)); }
                if (t >= GIANT && t < BEAM_END) { beam = 4; radius = 2.8f; glow = 3.5f; length = 30; flash = (float) Math.exp(-(t - GIANT) * .35) * .75f; }
                if (t >= BEAM_END) { glow = .6f + 2.4f * (float) Math.exp(-(t - BEAM_END) * .15); }
                if (t >= FIRST && t < FIRST_OFF + 4) glow += 1.2f * (1 - ease((t - FIRST) / 12f));
                float grown = t >= GIANT ? 1 : ease((t - FIRE) / 6f);
                return FilmBackdrop.Params.arena(eye, glow, beam, radius, length * grown, flash, 1, darkness(t));
            }
            public void render(FilmContext c) {
                float t = c.time();
                var level = Minecraft.getInstance().level;
                if (level == null) return;
                var s = state();
                if (s == null) return;
                if (cyclops == null || cyclops.entity() != level.getEntity(s.attacker)) cyclops = FilmCast.of(level.getEntity(s.attacker));
                if (target == null || target.entity() != level.getEntity(s.target)) target = FilmCast.of(level.getEntity(s.target));
                Vec3 feet = targetFeet(t);
                boolean inFlight = t >= LAUNCH;
                // Ground: the trench the giant beam tears open, rings where it lands.
                trench(c, t);
                shadow(c, Vec3.ZERO, .6, .55f);
                if (!inFlight) shadow(c, feet.multiply(1, 0, 1), .55, .5f / (1 + (float) feet.y * 3));
                if (cyclops != null) cyclops.draw(c, Vec3.ZERO, 0, CYCLOPS.sample(t), 1, cyclopsLight(t));
                if (target != null && (!inFlight || t < EXPLODE)) target.draw(c, feet, 180, targetPose(t), 1, targetLight(t));
                visorProp(c, t);
                debris(c, t);
                mist(c, t);
                light(c, t, feet);
            }
        };
    }
    /** The opening stays near black: only his eyes and the red they throw into the fog. */
    private static float darkness(float t) { return .85f * (1 - ease((t - 96) / 20f)); }
    /** How hard a beam is pushing on the target (0 when none is). */
    private static float strain(float t) {
        return Math.max(t >= HIT && t < CUT ? 1 - ease((t - CUT + 4) / 4f) : 0, t >= GIANT && t < BREAK ? 1.8f : 0);
    }
    /**
     * The pose with the struggle laid over it: the whole body trembling against the beam, the
     * arms shuddering in the X, the head shaking; the launched body tumbling as it flies.
     */
    private static ActorPose targetPose(float t) {
        ActorPose pose = TARGET.sample(t);
        float s = strain(t);
        if (s > 0) {
            float r = (float) Math.toRadians(1);
            float n1 = Mth.sin(t * 2.3f) * .6f + Mth.sin(t * 5.7f) * .4f, n2 = Mth.sin(t * 3.1f + 1) * .6f + Mth.sin(t * 6.9f + 2) * .4f;
            pose.rot[CHEST][0] += n1 * 2.5f * r * s;
            pose.rot[CHEST][1] += n2 * 2f * r * s;
            pose.rot[HEAD][0] += n2 * 3f * r * s;
            pose.rot[HEAD][1] += n1 * 4f * r * s;
            for (int arm : new int[]{RIGHT_UPPER_ARM, LEFT_UPPER_ARM}) { pose.rot[arm][0] += n1 * 3f * r * s; pose.rot[arm][2] += n2 * 2.5f * r * s; }
            for (int leg : new int[]{RIGHT_LOWER_LEG, LEFT_LOWER_LEG}) pose.rot[leg][0] += Math.abs(n2) * 4f * r * s;
            pose.crouch += Math.abs(n1) * .015f * s;
            pose.bodyRoll += n2 * 1.2f * s;
        }
        if (t > LAUNCH) { pose.bodyPitch += (t - LAUNCH) * 13; pose.bodyRoll += (t - LAUNCH) * 2.5f; }
        return pose;
    }
    /** Cyclops is a silhouette in the dark until his own light finds him. */
    private static int cyclopsLight(float t) {
        float lit = ease((t - 100) / 22f);
        float red = Math.min(1, lit + (t >= GIANT ? 1 : 0));
        return mix(0x0a0808, 0xffb0a0, red * (t >= GIANT && t < BEAM_END ? 1.15f : 1));
    }
    /** The target barely visible in the dark, then lit red by the beam in front of them. */
    private static int targetLight(float t) {
        float beam = t >= FIRE && t < CUT ? ease((t - FIRE) / 6f) : t >= GIANT ? 1 : 0;
        float flash = t >= FIRST && t < FIRST + 10 ? 1 - (t - FIRST) / 10f : 0;
        int cold = mix(0x3a3d46, 0x7a7f8c, ease((t - 96) / 20f));
        return mix(cold, t >= GIANT ? 0xffe0d8 : 0xffa898, Math.max(beam, flash));
    }
    private static int mix(int a, int b, float k) {
        k = Mth.clamp(k, 0, 1);
        int r = (int) Mth.lerp(k, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(k, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(k, a & 255, b & 255);
        return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, bl);
    }

    /** Every light in the scene: the visor, the first blast, the beam and its splash, the giant beam, the explosion. */
    private void light(FilmContext c, float t, Vec3 feet) {
        // Everything leaves from his eyes, wherever his pose has put them this frame.
        final Vec3 VISOR = eyes(t);
        // The visor glow: a dot in the fog, gathering light, then a flare.
        float glow = .45f + .05f * ease((t - 84) / 16f) + .6f * ease((t - 100) / 40f);
        if (t >= CUT && t < GIANT) glow = 1 + 1.5f * ease((t - CUT) / (GIANT - CUT));
        if (t >= GIANT && t < BEAM_END) glow = 2.6f;
        if (t >= BEAM_END) glow = .5f + 2f * (float) Math.exp(-(t - BEAM_END) * .15);
        float flicker = .93f + .07f * Mth.sin(t * 2.1f) * Mth.sin(t * .73f);
        glow(c, VISOR, .35 * glow * flicker, RED, Math.min(1, .8f * glow));
        glow(c, VISOR, .12 * glow, 0xffd0c8, Math.min(1, glow));
        // In the dark his eyes are the only light: a wide red bloom round them, breathing.
        float dark = darkness(t) / .85f;
        if (dark > 0) {
            float breathe = .8f + .2f * Mth.sin(t * .35f);
            glow(c, VISOR, 1.9 * breathe, RED, .32f * dark);
            glow(c, VISOR, .8 * breathe, 0xff5040, .55f * dark);
        }
        float flare = glow * (t >= 100 ? 1 : 1.3f);
        for (int s = -1; s <= 1; s += 2) streak(c, VISOR, VISOR.add(c.viewRight().scale(s * (.5 + .9 * flare))), .012 + .01 * flare, RED, Math.min(1, .7f * flare), 0, true);
        // Light gathering into the visor before each shot.
        float gather = window(t, 104, FIRE, 6) + window(t, 268, GIANT, 6) * 1.6f;
        for (int i = 0; i < 46 && gather > 0; i++) {
            double life = (hash(i) + t * .028) % 1, r = 1.8 * (1 - life) + .1, a = i * 2.4 + t * .12 + life * 4;
            Vec3 at = VISOR.add(c.viewRight().scale(Math.cos(a) * r)).add(c.viewUp().scale(Math.sin(a) * r * .8));
            streak(c, at, at.lerp(VISOR, .2), .012, HOT, 0, (float) Math.sin(Math.PI * life) * Math.min(1, gather), true);
        }
        // The first blast: a thin line out of the dark into the ground at the target's feet.
        if (t >= FIRST && t < FIRST_OFF + 10) {
            Vec3 ground = v(.32, .02, 2.62);
            float on = t < FIRST_OFF ? 1 : 1 - (t - FIRST_OFF) / 10f;
            if (t < FIRST_OFF) {
                beam(c, VISOR, ground, .07, 1, t, RUBY);
                beam(c, VISOR, ground, .32, .35f, t, RUBY);
            }
            glow(c, ground, 1.4 * on, RED, on);
            spray(c, ground, v(0, 1, -.3), .9, 30, t - FIRST, 1.6, HOT, on);
            ring(c, ground, .3 + (t - FIRST) * .09, .08, HOT, on * .8f, true);
            for (int i = 0; i < 10; i++) puff(c, ground.add((hash(i) - .5) * .8, .1 + hash(i + 4) * .4 + (t - FIRST) * .02, (hash(i + 9) - .5) * .8), .35, 0x2a2626, .4f * on);
        }
        // The beam: a tip racing out, then a steady stream breaking on the target's guard.
        if (t >= FIRE && t < CUT + 2) {
            float out = ease((t - FIRE) / (HIT - FIRE)), off = t < CUT ? 1 : 1 - (t - CUT) / 2f;
            Vec3 end = beamEnd(t), tip = VISOR.lerp(end, out);
            beam(c, VISOR, tip, .26, off, t, RUBY);
            glow(c, tip, .9 + .3 * Mth.sin(t * 1.3f), RED, off);
            if (t >= HIT) {
                float splash = off * (t < HIT + 4 ? 1.6f : 1);
                glow(c, end, 1.6, HOT, .8f * splash);
                spray(c, end, v(0, .5, -1), 1.4, 70, t - HIT, 2.2, HOT, splash);
                spray(c, end, v(0, .2, -1), 2.2, 30, t - HIT + 9, 1.4, RED, splash * .7f);
            }
            // The feet ploughing back.
            if (t >= LANDED && t < PLANT + 6) {
                float plough = 1 - ease((t - PLANT) / 6f);
                for (int i = 0; i < 16; i++) {
                    double from = TARGET_Z + 1.1, back = hash(i) * (feet.z - from);
                    puff(c, v((hash(i + 3) - .5) * .6, .08 + hash(i + 6) * .3, from + back), .3, 0x2c2a2a, .35f * plough);
                }
                spray(c, feet.add(0, .05, -.1), v(0, .5, 1), 1.2, 24, t - LANDED, 1.8, 0xffb070, plough);
            }
            // Each hard step forward stamps dust out of the floor.
            for (float step : new float[]{LANDED, STEP1, STEP2}) {
                float k = t - step;
                if (k < 0 || k > 10) continue;
                for (int i = 0; i < 8; i++) {
                    double a = i * .785 + step;
                    puff(c, feet.multiply(1, 0, 1).add(Math.cos(a) * (.3 + k * .05), .08 + k * .015, Math.sin(a) * (.3 + k * .05)), .28 + k * .03, 0x2c2a2a, .45f * (1 - k / 10));
                }
            }
        }
        // MAXIMUM POWER: a column of light as wide as a body, fog lit through, the frame torn red.
        if (t >= GIANT - 1 && t < BEAM_END + 3) {
            float on = ease((t - GIANT + 1) / 2f) * (t < BEAM_END ? 1 : 1 - (t - BEAM_END) / 3f);
            Vec3 end = beamEnd(t);
            beam(c, VISOR, end, 2.4 + .15 * Mth.sin(t * 1.9f), on, t * 1.6f, RUBY);
            beam(c, VISOR, end, 1.1, on, t * 2.3f + 3, RUBY);
            beam(c, VISOR, end, .45, on, t * 3.1f + 7, RUBY);
            glow(c, VISOR, 4.2, 0xffe8e0, on);
            glow(c, VISOR, 7.5, RED, .6f * on);
            if (t < LAUNCH) {
                glow(c, end, 4.5, HOT, on);
                spray(c, end, v(0, .4, -1), 3.2, 160, t - GIANT, 4.2, HOT, on);
            }
            // Spiral filaments running down the beam.
            for (int k = 0; k < 3; k++) {
                Vec3 last = VISOR;
                for (int i = 1; i <= 30; i++) {
                    double u = i / 30.0, z = VISOR.z + u * Math.min(18, end.z - VISOR.z), a = u * 14 - t * .9 + k * 2.094;
                    Vec3 next = v(Math.cos(a) * (.5 + u * 2.6), VISOR.y + (end.y - VISOR.y) * u + Math.sin(a) * (.5 + u * 2.6), z);
                    streak(c, last, next, .03, 0xffc0b0, .6f * on, .6f * on, true);
                    last = next;
                }
            }
        }
        // The launched body burning as it flies into the fog.
        if (t >= LAUNCH && t < EXPLODE) {
            Vec3 chest = feet.add(0, .9, 0);
            glow(c, chest, 1.4, HOT, .8f);
            for (int i = 0; i < 12; i++) {
                double lag = i * .55;
                puff(c, targetFeet(Math.max(LAUNCH, t - (float) lag)).add(0, .9 + hash(i) * .4, 0), .45 + i * .06, 0x241e1e, .4f * (1 - i / 12f));
            }
        }
        // The explosion far away in the fog: a white core, a fireball, a shock ring across the floor.
        if (t >= EXPLODE - 1) {
            Vec3 at = targetFeet(EXPLODE).multiply(1, 0, 1);
            float k = t - EXPLODE;
            float core = (float) Math.exp(-Math.max(0, k) * .12);
            glow(c, at.add(0, 1.2, 0), 3 + k * .5, 0xffffff, core);
            glow(c, at.add(0, 1.2, 0), 7 + k * .9, HOT, core * .9f);
            glow(c, at.add(0, 1, 0), 14 + k, RED, core * .6f);
            ring(c, at.add(0, .03, 0), k * .9, 1.2, HOT, core, true);
            for (int i = 0; i < 18; i++) {
                double a = hash(i) * Math.PI * 2, rise = Math.min(k, 30) * (.08 + hash(i + 2) * .1);
                puff(c, at.add(Math.cos(a) * (1 + k * .12), .6 + rise + hash(i + 5) * 1.5, Math.sin(a) * (1 + k * .12)), 1.6 + k * .06, 0x2e1c18, .55f * Math.min(1, k / 3f) * (1 - Math.min(1, k / 40f)));
            }
            spray(c, at.add(0, 1, 0), v(0, 1, 0), 3, 90, k, 1.4, 0xffd0a0, core);
        }
    }
    /** Sparks: streaks flung from a point along dir, falling as they cool. */
    private static void spray(FilmContext c, Vec3 at, Vec3 dir, double spread, int count, float since, double reach, int rgb, float alpha) {
        if (alpha <= 0 || since < 0) return;
        Vec3 d0 = dir.normalize();
        for (int i = 0; i < count; i++) {
            double life = (hash(i * 3 + 1) + since * (.05 + hash(i) * .05)) % 1;
            Vec3 d = d0.add((hash(i + 11) - .5) * spread, (hash(i + 23) - .5) * spread * .6, (hash(i + 37) - .5) * spread).normalize();
            double dist = life * reach * (.6 + hash(i + 5) * .8);
            Vec3 head = at.add(d.scale(dist)).add(0, -life * life * .9, 0);
            Vec3 tail = head.subtract(d.scale(.12 + .25 * (1 - life))).add(0, life * .1, 0);
            streak(c, tail, head, .012 + hash(i + 7) * .012, rgb, 0, (float) (alpha * (1 - life)), true);
        }
    }
    /** The giant beam ploughs a glowing trench and throws up stone. */
    private static void trench(FilmContext c, float t) {
        if (t < GIANT) return;
        float age = t - GIANT, cool = (float) Math.exp(-Math.max(0, t - BEAM_END) * .05);
        double reach = Math.min(30, .5 + age * 1.6);
        for (int i = 0; i < 60; i++) {
            double z0 = .6 + i * .5, z1 = z0 + .5;
            if (z0 > reach) break;
            double w = .6 + .2 * Math.sin(i * 1.7);
            streak(c, v(0, .025, z0), v(0, .025, z1), w, HOT, .8f * cool, .8f * cool, true);
            streak(c, v(0, .03, z0), v(0, .03, z1), w * .35, 0xffe8c0, .9f * cool, .9f * cool, true);
        }
    }
    private static void debris(FilmContext c, float t) {
        if (t < GIANT) return;
        var pose = c.pose();
        for (int i = 0; i < 40; i++) {
            float born = GIANT + i * .6f;
            float k = t - born;
            if (k < 0) continue;
            double z = .8 + i * .45, side = hash(i) < .5 ? -1 : 1;
            double vx = side * (.05 + hash(i + 3) * .1), vy = .18 + hash(i + 6) * .2;
            double x = side * .3 + vx * k, y = Math.max(0, vy * k - .012 * k * k);
            if (y <= 0 && k > 4) { x = side * .3 + vx * (vy / .012); }
            float size = .07f + (float) hash(i + 8) * .14f;
            pose.pushPose();
            pose.translate(x, y + size, z);
            pose.mulPose(Axis.XP.rotationDegrees(k * 23 * (float) (hash(i + 1) - .5)));
            pose.mulPose(Axis.ZP.rotationDegrees(k * 31 * (float) (hash(i + 2) - .5)));
            cube(c, pose.last().pose(), -size, -size, -size, size, size, size, 0x1d1c20);
            pose.popPose();
        }
    }
    /** The visor torn off at maximum power, spinning away and clattering down. */
    private static void visorProp(FilmContext c, float t) {
        if (t < GIANT) return;
        float k = Math.min(t - GIANT, 22);
        Vec3 face = eyes(GIANT);
        double x = -.08 * k, y = face.y + .11 * k - .0068 * k * k * 1.5, z = face.z - .02 * k;
        y = Math.max(.04, y);
        var pose = c.pose();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.ZP.rotationDegrees(k * 29));
        pose.mulPose(Axis.XP.rotationDegrees(k * 17));
        cube(c, pose.last().pose(), -.27f, -.06f, -.05f, .27f, .06f, .05f, 0x3a0c0c);
        cube(c, pose.last().pose(), -.22f, -.03f, .045f, .22f, .03f, .06f, 0xff2a20);
        pose.popPose();
    }
    /** Low banks of fog drifting between the two of them; thinner near the lens. */
    private static void mist(FilmContext c, float t) {
        for (int i = 0; i < 26; i++) {
            Vec3 at = v((hash(i) - .5) * 9 + Math.sin(t * .01 + i) * .8, .15 + hash(i + 3) * 1.1, (hash(i + 7) - .3) * 9 + t * .004 * (i % 3));
            double near = at.distanceTo(c.camera());
            float fade = (float) Mth.clamp((near - 1.2) / 2.5, 0, 1);
            puff(c, at, 1.4 + hash(i + 5) * 1.2, 0x1c1d22, .22f * fade);
        }
    }
}
