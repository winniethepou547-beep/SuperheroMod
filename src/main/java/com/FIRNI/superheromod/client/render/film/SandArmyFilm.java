package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.entity.SandSoldierModel;
import com.FIRNI.superheromod.core.cinematic.ActorPose;
import com.FIRNI.superheromod.heroes.sandman.SandArmySession;
import com.FIRNI.superheromod.heroes.sandman.SandSoldierEntity;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.core.cinematic.ActorPose.*;
import static com.FIRNI.superheromod.client.render.film.FilmFx.*;

/**
 * SAND ARMY, the film (20 s). Sandman stands at the stage origin facing +z, his opponent 3.5
 * blocks in front of him, in an open desert at the golden hour with a storm coming up.
 *   0-30     world   Sandman raises his hand
 *   30-92    the army rises out of the dunes in a ring round the opponent
 *   92-160   the charge: two soldiers attack first and the opponent breaks both of them
 *   160-214  the rest arrive, seize and pin the opponent and beat on them
 *   214-262  they fall into a mound of sand round the opponent and it rises as a pillar
 *   262-330  a giant soldier rises out of the desert behind the pillar and lifts both fists
 *   330-380  the slam: the opponent driven from the top of the pillar into the sand; the storm
 *   380-400  world   back to the game, sand bursting where they stand
 */
final class SandArmyFilm implements Film {
    static final String ID = SandArmySession.ID;
    static final int STAGE = 30, W2 = SandArmySession.RELEASE, TOTAL = SandArmySession.TOTAL;
    static final float COUNTER = 123, BACKHAND = 148, GRAB = 180, MOUND = 214, LIFT = 232, RISEN = 262,
            GIANT = 262, GIANT_UP = 300, SLAM = SandArmySession.SLAM, CRUMBLE = 340;
    static final Vec3 OPP = new Vec3(0, 0, 3.5), GIANT_AT = new Vec3(0, 0, 6.4);
    static final float PILLAR = 3.2f, GIANT_SCALE = 3.6f;
    private static final int SAND = 0xd9c08a, SAND_DARK = 0xa88a58, DUST = 0xcdb07a;
    private static final ResourceLocation SAND_TEXTURE = new ResourceLocation("minecraft", "textures/block/sand.png");
    /** The army: where each soldier rises (degrees round the opponent, 0 = behind them) and when. */
    private static final float[] ANGLE = {90, 0, -90, 140, -140, 40, -40, 115, -115, 65};
    private static final int COUNT = ANGLE.length;
    private final int attacker;
    private final Segment[] segments;
    private FilmCast sandman, opponent;
    private static SandSoldierModel<SandSoldierEntity> soldierModel;
    private static final FilmCast.Track SANDMAN = sandmanTrack(), OPPONENT = opponentTrack();

    SandArmyFilm(int attacker) {
        this.attacker = attacker;
        Scene desert = desert();
        segments = new Segment[]{
                new Segment(0, STAGE, new FilmShot[]{FilmShot.of(0, STAGE)
                        .path(v(1.8, 1.0, 2.6), v(1.4, 1.15, 2.1), v(1.1, 1.3, 1.7)).look(v(0, 1.5, 0), v(0, 1.6, 0)).fov(55, 46).shake(.02f, .03f).build()},
                        null, 0, 0),
                new Segment(STAGE, W2, new FilmShot[]{
                        // The army rises.
                        FilmShot.of(STAGE, 64).path(v(6.5, 3.4, -1.2), v(5.8, 3.0, -.5), v(5.2, 2.6, .2)).look(v(0, .8, 3.5), v(0, 1.1, 3.5)).fov(60, 58).shake(.03f, .03f).build(),
                        FilmShot.of(64, 92).path(v(-.9, .55, 10.6), v(-.6, .6, 10.2)).look(v(0, 1.2, 3.5), v(0, 1.3, 3.0)).fov(62, 58).shake(.03f, .03f).build(),
                        // The charge and the two exchanges.
                        FilmShot.of(92, 114).path(v(6.4, .6, 2.3), v(4.4, .7, 2.4), v(2.7, .8, 2.5)).look(v(5, 1, 3.5), v(2.4, 1.1, 3.5), v(.8, 1.2, 3.5)).fov(64, 60).shake(.08f, .12f).build(),
                        FilmShot.of(114, 136).path(v(1.9, 1.4, 1.6), v(1.75, 1.45, 1.85)).look(v(.5, 1.2, 3.5), v(.6, 1.1, 3.6)).fov(50, 52).shake(.05f, .1f).build(),
                        FilmShot.of(136, 160).path(v(-2.2, 1.2, 2.2), v(-2.0, 1.05, 2.6)).look(v(-.2, 1.1, 3.8), v(0, 1.0, 4.2)).fov(52, 50).roll(-3, 2).shake(.06f, .1f).build(),
                        // Seized and beaten.
                        FilmShot.of(160, 186).path(v(3.6, 2.6, 6.0), v(2.9, 2.75, 6.5), v(2.2, 2.9, 6.8)).look(v(0, .9, 3.5)).fov(62, 60).shake(.04f, .05f).build(),
                        FilmShot.of(186, MOUND).path(v(1.45, 1.1, 2.35), v(1.2, 1.0, 2.7)).look(v(0, 1.0, 3.5)).fov(56, 54).roll(2, -2).shake(.12f, .16f).build(),
                        // The mound and the pillar.
                        FilmShot.of(MOUND, LIFT).path(v(-1.6, 1.5, 1.2), v(-1.4, 1.55, 1.0)).look(v(0, 1.5, 0), v(.2, 1.7, .4)).fov(46, 44).shake(.03f, .03f).build(),
                        FilmShot.of(LIFT, RISEN).path(v(3.8, 1.2, 3.0), v(4.0, 2.6, 3.1), v(3.6, 3.9, 3.2)).look(v(0, 1.0, 3.5), v(0, 2.6, 3.5), v(0, 3.7, 3.5)).fov(64, 60).shake(.06f, .1f).build(),
                        // The giant.
                        FilmShot.of(RISEN, GIANT_UP).path(v(-2.8, .4, 1.2), v(-3.0, .45, .9), v(-3.2, .5, .6)).look(v(0, 4, 5.5), v(0, 6, 6)).fov(70, 66).shake(.2f, .12f).build(),
                        FilmShot.of(GIANT_UP, SLAM - 4).path(v(9, 4, 4.5), v(8.7, 4.3, 4.6), v(8.4, 4.6, 4.7)).look(v(0, 4, 5)).fov(60, 56).shake(.05f, .08f).build(),
                        // The slam.
                        FilmShot.of(SLAM - 4, 338).path(v(5.4, 1.2, 1.0), v(5.2, 1.6, 1.2)).look(v(0, 3.0, 3.5), v(0, .6, 3.5)).fov(72, 76).shake(.2f, 1f).build(),
                        FilmShot.of(338, 358).path(v(6, 2.2, -.5), v(6.4, 2.6, -.95), v(6.8, 3, -1.4)).look(v(0, .6, 3.5)).fov(62, 60).shake(.4f, .1f).build(),
                        FilmShot.of(358, W2).path(v(-.7, 1.75, -1.1), v(-.65, 1.78, -1.25), v(-.6, 1.8, -1.4)).look(v(0, .4, 3.5)).fov(50, 48).shake(.03f, .02f).build()},
                        desert, 0xd8b880, 6),
                new Segment(W2, TOTAL, new FilmShot[]{FilmShot.of(W2, TOTAL)
                        .path(v(2.8, 1.4, -1.6), v(3.4, 1.9, -2.4)).look(v(0, 1.0, 3), v(0, .8, 3)).fov(60, 58).shake(.15f, .02f).build()},
                        null, 0xe8d0a0, 4)};
    }
    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }
    private FilmSessionClient.State state() { return FilmSessionClient.get(attacker); }
    @Override public Vec3 origin() { var s = state(); return s == null ? Vec3.ZERO : s.anchor; }
    @Override public Vec3 forward() { var s = state(); return s == null ? new Vec3(0, 0, 1) : FilmSessionClient.forward(s); }
    @Override public float duration() { return TOTAL; }
    @Override public Segment[] segments() { return segments; }
    @Override public float time(float partial) { var s = state(); return s == null ? duration() : FilmSessionClient.time(s, partial); }
    @Override public boolean active() { var s = state(); return s != null && ID.equals(s.film); }
    @Override public float[] impacts() { return new float[]{COUNTER, BACKHAND, 200, SLAM}; }
    @Override public float blendOut() { return 12; }

    // ------------------------------------------------------------------ sound
    @Override public Cue[] cues() {
        List<Cue> c = new ArrayList<>();
        add(c, 4, SoundEvents.SAND_STEP, .8f, .7f); add(c, 20, SoundEvents.ELYTRA_FLYING, .25f, .6f);
        for (int i = 0; i < COUNT; i++) { add(c, spawn(i) + 1, SoundEvents.SAND_BREAK, .9f, .55f + i * .03f); add(c, spawn(i) + 1, ModSounds.SANDMAN_SAND_IMPACT.get(), (.9f) * 0.9f, 1.0f); if (i % 3 == 0) add(c, spawn(i) + 4, SoundEvents.WARDEN_DIG, .5f, 1.3f); }
        add(c, 40, SoundEvents.HUSK_AMBIENT, .7f, .6f);
        for (int i = 0; i < 8; i++) add(c, 94 + i * 5, SoundEvents.SAND_STEP, .9f, .8f + (i % 3) * .1f);
        add(c, 118, SoundEvents.HUSK_AMBIENT, .9f, .9f); add(c, COUNTER, SoundEvents.PLAYER_ATTACK_STRONG, 1f, .7f);
        add(c, COUNTER + 1, SoundEvents.SAND_BREAK, 1f, .5f); add(c, COUNTER + 1, ModSounds.SANDMAN_SAND_IMPACT.get(), (1f) * 0.9f, 1.0f); add(c, COUNTER + 3, SoundEvents.SAND_FALL, 1f, .6f); add(c, COUNTER + 3, ModSounds.SANDMAN_SAND_WHOOSH.get(), (1f) * 0.8f, 0.9f);
        add(c, BACKHAND - 4, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .7f); add(c, BACKHAND, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .7f);
        add(c, BACKHAND + 1, SoundEvents.SAND_BREAK, 1f, .45f); add(c, BACKHAND + 1, ModSounds.SANDMAN_SAND_IMPACT.get(), (1f) * 0.9f, 1.0f);
        for (int i = 0; i < 6; i++) add(c, 166 + i * 3, SoundEvents.SAND_STEP, .9f, .7f + i * .05f);
        add(c, GRAB, SoundEvents.SAND_HIT, 1f, .6f); add(c, GRAB + 2, SoundEvents.PLAYER_HURT, .7f, .8f);
        for (int i = 2; i < COUNT; i++) for (int p = 0; p < 3; p++) if ((i + p) % 2 == 0) add(c, punch(i, p) + 4, SoundEvents.PLAYER_ATTACK_STRONG, .7f, .8f + (i % 3) * .1f);
        add(c, MOUND, SoundEvents.SAND_FALL, 1f, .4f); add(c, MOUND, ModSounds.SANDMAN_SAND_WHOOSH.get(), (1f) * 0.8f, 0.9f); add(c, MOUND + 8, SoundEvents.SAND_PLACE, 1f, .5f); add(c, MOUND + 8, ModSounds.SANDMAN_SAND_WHOOSH.get(), (1f) * 0.8f, 1.0f);
        add(c, LIFT, SoundEvents.WARDEN_EMERGE, .6f, 1.4f); add(c, LIFT + 6, SoundEvents.ELYTRA_FLYING, .5f, .5f);
        add(c, GIANT, SoundEvents.WARDEN_EMERGE, 1f, .6f); add(c, GIANT + 20, SoundEvents.SAND_FALL, 1f, .4f); add(c, GIANT + 20, ModSounds.SANDMAN_SAND_WHOOSH.get(), (1f) * 0.8f, 0.9f);
        add(c, GIANT_UP, SoundEvents.RAVAGER_ROAR, 1f, .55f); add(c, 316, SoundEvents.ELYTRA_FLYING, .7f, .4f);
        add(c, SLAM - 3, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .4f);
        add(c, SLAM, SoundEvents.GENERIC_EXPLODE, 1f, .5f); add(c, SLAM, ModSounds.FX_IMPACT_HEAVY.get(), (1f) * 1.0f, 1.0f); add(c, SLAM, SoundEvents.ANVIL_LAND, .9f, .5f);
        add(c, SLAM + 1, SoundEvents.SAND_BREAK, 1f, .4f); add(c, SLAM + 1, ModSounds.SANDMAN_SAND_IMPACT.get(), (1f) * 0.9f, 1.0f); add(c, SLAM + 3, SoundEvents.LIGHTNING_BOLT_THUNDER, .6f, 1.4f);
        add(c, CRUMBLE, SoundEvents.SAND_FALL, 1f, .35f); add(c, CRUMBLE, ModSounds.SANDMAN_SAND_WHOOSH.get(), (1f) * 0.8f, 0.9f); add(c, CRUMBLE + 6, SoundEvents.SAND_BREAK, .8f, .4f); add(c, CRUMBLE + 6, ModSounds.SANDMAN_SAND_IMPACT.get(), (.8f) * 0.9f, 1.0f);
        add(c, 362, SoundEvents.ELYTRA_FLYING, .3f, .7f);
        return c.toArray(new Cue[0]);
    }
    private static void add(List<Cue> list, float t, SoundEvent sound, float volume, float pitch) { list.add(new Cue(t, sound, volume, pitch)); }

    // ------------------------------------------------------------------ picture layer
    @Override public int grade(float t) {
        float storm = t >= SLAM ? 1 - ease((t - SLAM) / 30f) : 0;
        if (storm > 0) return ((int) (storm * 140) << 24) | 0xd8b070;
        return 0;
    }
    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        var font = Minecraft.getInstance().font;
        float title = window(t, 44, 86, 6);
        if (title > 0) {
            int y = (int) (h * .40f);
            float drift = (t - 44) * .15f;
            g.pose().pushPose();
            g.pose().translate(w / 2f - drift, y, 0); g.pose().scale(2.6f, 2.6f, 1);
            HudStyle.caption(g, font, "Sand Army", 0, 0, HudStyle.alpha(0xFFF0CC88, title), 0);
            g.pose().popPose();
            String sub = "Flint Marko  ·  Sandman";
            int sw = HudStyle.captionWidth(font, sub);
            HudStyle.caption(g, font, sub, w / 2, y + 28, HudStyle.alpha(HudStyle.TEXT, title * .8f), 0);
            g.fill(w / 2 - sw / 2 - 34, y + 31, w / 2 - sw / 2 - 8, y + 32, HudStyle.alpha(0xFFF0CC88, title));
            g.fill(w / 2 + sw / 2 + 8, y + 31, w / 2 + sw / 2 + 34, y + 32, HudStyle.alpha(0xFFF0CC88, title));
        }
        // The army's strength as it rises, falls and is spent into the sand.
        float shown = window(t, STAGE + 8, MOUND + 10, 6);
        if (shown > 0) {
            int standing = 0;
            for (int i = 0; i < COUNT; i++) if (t >= spawn(i) + 12 && t < gone(i)) standing++;
            int y = h - 30, half = 64;
            HudStyle.caption(g, font, "Sand Soldiers", w / 2 - half, y, HudStyle.alpha(HudStyle.MUTED, shown), -1);
            HudStyle.caption(g, font, standing + " / " + COUNT, w / 2 + half, y, HudStyle.alpha(0xFFF0CC88, shown), 1);
            HudStyle.segments(g, w / 2 - half, y + 11, half * 2, COUNT, standing, HudStyle.alpha(0xFFF0CC88, shown));
        }
    }

    // ------------------------------------------------------------------ choreography: the two of them
    private static FilmCast.Track sandmanTrack() {
        ActorPose calm = of().j(RIGHT_UPPER_ARM, 0, 0, 4).j(LEFT_UPPER_ARM, 0, 0, -4).j(RIGHT_LOWER_ARM, -8, 0, 0).j(LEFT_LOWER_ARM, -8, 0, 0);
        ActorPose command = calm.copy().j(RIGHT_UPPER_ARM, -75, -12, 12).j(RIGHT_LOWER_ARM, -40, 0, 0).j(CHEST, 0, -8, 0).j(HEAD, -4, 0, 0);
        ActorPose sweep = command.copy().j(RIGHT_UPPER_ARM, -88, -40, 10).j(RIGHT_LOWER_ARM, -10, 0, 0).j(CHEST, 0, 14, 0);
        ActorPose lift = calm.copy().j(RIGHT_UPPER_ARM, -135, -8, 10).j(RIGHT_LOWER_ARM, -22, 0, 0).j(CHEST, -6, 0, 0).j(HEAD, -12, 0, 0);
        ActorPose both = lift.copy().j(LEFT_UPPER_ARM, -130, 8, -10).j(LEFT_LOWER_ARM, -22, 0, 0).j(CHEST, -12, 0, 0).j(HEAD, -18, 0, 0)
                .j(RIGHT_UPPER_LEG, -10, 0, 6).j(LEFT_UPPER_LEG, 8, 0, -6).crouch(.04f);
        ActorPose slam = calm.copy().j(RIGHT_UPPER_ARM, -40, 0, 14).j(RIGHT_LOWER_ARM, -30, 0, 0).j(LEFT_UPPER_ARM, -38, 0, -14).j(LEFT_LOWER_ARM, -30, 0, 0)
                .j(CHEST, 22, 0, 0).j(HEAD, 6, 0, 0).j(RIGHT_UPPER_LEG, -30, 0, 6).j(RIGHT_LOWER_LEG, 40, 0, 0).j(LEFT_UPPER_LEG, -12, 0, -6)
                .j(LEFT_LOWER_LEG, 26, 0, 0).crouch(.2f);
        return new FilmCast.Track().key(0, 0, calm).key(22, 12, command).key(96, 10, sweep).key(130, 16, command)
                .key(MOUND + 8, 14, lift).key(GIANT + 10, 14, both).key(SLAM + 1, 4, slam, impactChain()).key(370, 22, calm);
    }
    private static FilmCast.Track opponentTrack() {
        ActorPose calm = of().j(RIGHT_UPPER_ARM, 0, 0, 4).j(LEFT_UPPER_ARM, 0, 0, -6).j(RIGHT_LOWER_ARM, -8, 0, 0).j(LEFT_LOWER_ARM, -10, 0, 0);
        // The ground erupts round them: a start, head first, hands half up.
        ActorPose startle = of().j(HEAD, -6, 40, 0).j(CHEST, -4, 14, 0).j(RIGHT_UPPER_ARM, -24, 0, 14).j(RIGHT_LOWER_ARM, -34, 0, 0)
                .j(LEFT_UPPER_ARM, -18, 0, -12).j(LEFT_LOWER_ARM, -30, 0, 0).j(RIGHT_UPPER_LEG, 10, 0, 4).j(LEFT_UPPER_LEG, -8, 0, -4).crouch(.05f);
        ActorPose lookLeft = startle.copy().j(HEAD, -4, -48, 0).j(CHEST, -2, -16, 0).j(RIGHT_UPPER_ARM, -36, 0, 20).j(LEFT_UPPER_ARM, -38, 0, -20)
                .j(LEFT_LOWER_ARM, -55, 0, 0).j(RIGHT_LOWER_ARM, -50, 0, 0);
        // Fighting stance: fists by the chin, chin down, left foot forward, knees soft.
        ActorPose guard = of().j(CHEST, 8, 0, 0).j(HEAD, 10, 0, 0)
                .j(RIGHT_UPPER_ARM, -55, -25, 20).j(RIGHT_LOWER_ARM, -100, 0, 0).j(LEFT_UPPER_ARM, -70, 25, -15).j(LEFT_LOWER_ARM, -95, 0, 0)
                .j(LEFT_UPPER_LEG, -22, 0, -5).j(LEFT_LOWER_LEG, 28, 0, 0).j(RIGHT_UPPER_LEG, 14, 0, 6).j(RIGHT_LOWER_LEG, 22, 0, 0).crouch(.12f);
        ActorPose halfGuard = guard.copy().j(CHEST, 2, 0, 0).j(RIGHT_UPPER_ARM, -45, -15, 22).j(RIGHT_LOWER_ARM, -70, 0, 0)
                .j(LEFT_UPPER_ARM, -50, 15, -18).j(LEFT_LOWER_ARM, -70, 0, 0).crouch(.08f);
        // First attacker: lean out of the line, load the hips, then a straight right fully extended.
        ActorPose slip = guard.copy().j(CHEST, -10, 28, 0).j(HEAD, -8, 15, 0).j(RIGHT_UPPER_ARM, -40, -10, 35).j(RIGHT_LOWER_ARM, -120, 0, 0).crouch(.18f);
        ActorPose counter = guard.copy().j(CHEST, 12, -45, 0).j(HEAD, 6, -10, 0).j(RIGHT_UPPER_ARM, -92, -25, 8).j(RIGHT_LOWER_ARM, -8, 0, 0)
                .j(RIGHT_UPPER_LEG, -20, 0, 6).j(RIGHT_LOWER_LEG, 30, 0, 0).j(LEFT_UPPER_LEG, 10, 0, -5).j(LEFT_LOWER_LEG, 18, 0, 0).crouch(.14f).body(0, -6);
        ActorPose follow = counter.copy().j(CHEST, 16, -58, 0).j(RIGHT_UPPER_ARM, -88, -40, 6).j(RIGHT_LOWER_ARM, -4, 0, 0);
        // Second attacker, from behind: the head turns first, a duck under the swing, a spinning backhand.
        ActorPose lookBack = guard.copy().j(HEAD, 0, 70, 0).j(CHEST, 4, 25, 0);
        ActorPose duck = guard.copy().j(CHEST, 35, 30, 0).j(HEAD, 0, 40, 0).j(LEFT_UPPER_LEG, -40, 0, -6).j(LEFT_LOWER_LEG, 75, 0, 0)
                .j(RIGHT_UPPER_LEG, -30, 0, 6).j(RIGHT_LOWER_LEG, 70, 0, 0).crouch(.4f).body(-10, 0);
        ActorPose backhand = guard.copy().j(CHEST, 5, 60, 0).j(HEAD, 0, 20, 0).j(LEFT_UPPER_ARM, -85, 75, -10).j(LEFT_LOWER_ARM, -10, 0, 0)
                .crouch(.2f).body(0, -8);
        ActorPose backFollow = backhand.copy().j(CHEST, 5, 75, 0).j(LEFT_UPPER_ARM, -70, 95, -15);
        // Seized: both arms yanked out wide, braced, then dragged down under the blows.
        ActorPose grabbed = of().j(CHEST, 20, 0, 0).j(HEAD, -10, 0, 0).j(RIGHT_UPPER_ARM, -60, 0, 75).j(RIGHT_LOWER_ARM, -20, 0, 0)
                .j(LEFT_UPPER_ARM, -60, 0, -75).j(LEFT_LOWER_ARM, -20, 0, 0).j(RIGHT_UPPER_LEG, -6, 0, 12).j(LEFT_UPPER_LEG, -6, 0, -12)
                .j(RIGHT_LOWER_LEG, 14, 0, 0).j(LEFT_LOWER_LEG, 14, 0, 0).crouch(.15f);
        ActorPose pinned = grabbed.copy().j(CHEST, 26, 0, 0).j(HEAD, 18, 0, 0).j(RIGHT_UPPER_ARM, -45, 0, 70).j(RIGHT_LOWER_ARM, -40, 0, 0)
                .j(LEFT_UPPER_ARM, -45, 0, -70).j(LEFT_LOWER_ARM, -40, 0, 0).j(RIGHT_UPPER_LEG, -30, 0, 8).j(RIGHT_LOWER_LEG, 55, 0, 0)
                .j(LEFT_UPPER_LEG, -30, 0, -8).j(LEFT_LOWER_LEG, 55, 0, 0).crouch(.3f);
        // Swallowed to the knees: looking down, pulling at the sand.
        ActorPose sinking = of().j(CHEST, 30, 0, 0).j(HEAD, 35, 0, 0).j(RIGHT_UPPER_ARM, -30, 0, 40).j(RIGHT_LOWER_ARM, -90, 0, 0)
                .j(LEFT_UPPER_ARM, -34, 0, -40).j(LEFT_LOWER_ARM, -85, 0, 0).crouch(.1f);
        // Carried up: arms out for balance, looking down at the drop.
        ActorPose balance = of().j(CHEST, -5, 0, 0).j(HEAD, 22, 0, 0).j(RIGHT_UPPER_ARM, 0, 0, 82).j(RIGHT_LOWER_ARM, -12, 0, 0)
                .j(LEFT_UPPER_ARM, 0, 0, -78).j(LEFT_LOWER_ARM, -16, 0, 0).crouch(.08f);
        // Something rises behind: a look over the shoulder, then arms crossed over the head.
        ActorPose overShoulder = balance.copy().j(HEAD, -18, 75, 0).j(CHEST, -4, 35, 0).j(RIGHT_UPPER_ARM, -20, 0, 60).j(LEFT_UPPER_ARM, -20, 0, -55);
        ActorPose shield = of().j(CHEST, -12, 0, 0).j(HEAD, -20, 0, 0).j(RIGHT_UPPER_ARM, -165, -30, 0).j(RIGHT_LOWER_ARM, -60, 0, 0)
                .j(LEFT_UPPER_ARM, -165, 30, 0).j(LEFT_LOWER_ARM, -60, 0, 0).j(RIGHT_UPPER_LEG, -14, 0, 6).j(RIGHT_LOWER_LEG, 26, 0, 0)
                .j(LEFT_UPPER_LEG, -14, 0, -6).j(LEFT_LOWER_LEG, 26, 0, 0).crouch(.2f);
        ActorPose smashed = of().j(CHEST, -18, 20, 0).j(HEAD, -14, 0, 0).j(RIGHT_UPPER_ARM, 40, 0, 60).j(LEFT_UPPER_ARM, 40, 0, -60)
                .j(RIGHT_LOWER_ARM, -20, 0, 0).j(LEFT_LOWER_ARM, -20, 0, 0).body(6, -65);
        ActorPose lying = calm.copy().body(0, -90).j(HEAD, 10, 20, 0).j(RIGHT_UPPER_ARM, -10, 0, 50).j(LEFT_UPPER_ARM, -14, 0, -40)
                .j(RIGHT_UPPER_LEG, -10, 0, 8).j(LEFT_UPPER_LEG, 6, 0, -6);
        return new FilmCast.Track().key(0, 0, calm).key(46, 6, startle, noticeChain()).key(62, 8, lookLeft, noticeChain())
                .key(78, 10, halfGuard).key(98, 12, guard)
                .key(COUNTER - 4, 4, slip, noticeChain()).key(COUNTER, 3, counter, impactChain()).key(COUNTER + 5, 4, follow).key(136, 9, guard)
                .key(139, 4, lookBack, noticeChain()).key(BACKHAND - 3, 4, duck).key(BACKHAND, 3, backhand, impactChain())
                .key(BACKHAND + 5, 4, backFollow).key(166, 12, guard)
                .key(GRAB + 3, 3, grabbed, impactChain()).key(194, 10, pinned)
                .key(MOUND + 10, 10, sinking).key(LIFT + 16, 14, balance).key(282, 10, overShoulder, noticeChain())
                .key(GIANT_UP + 10, 10, shield).key(SLAM + 2, 3, smashed, impactChain()).key(SLAM + 7, 5, lying);
    }
    /**
     * The opponent's pose with life laid over it: breathing, a fighter's bounce in the guard, the
     * head snapping with every punch that lands, tugging against the grips, wobbling on the pillar.
     */
    private static ActorPose opponentPose(float t) {
        ActorPose pose = OPPONENT.sample(t);
        float r = (float) Math.toRadians(1);
        pose.rot[CHEST][0] += Mth.sin(t * .25f) * 1.5f * r;
        boolean exchange = t > 113 && t < 134 || t > 136 && t < 158;
        float bounce = window(t, 98, GRAB, 6) * (exchange ? 0 : 1);
        if (bounce > 0) {
            pose.crouch += .025f * (Mth.sin(t * .45f) + 1) * .5f * bounce;
            pose.rot[CHEST][1] += Mth.sin(t * .23f) * 4 * r * bounce;
            pose.rot[HEAD][1] += Mth.sin((t - 160) * .35f) * 25 * r * window(t, 160, GRAB - 2, 4);
        }
        for (int i = 2; i < COUNT; i++) for (int p = 0; p < 3; p++) {
            float since = t - punch(i, p) - 5;
            if (since < 0 || since > 12) continue;
            float hit = (float) Math.exp(-since / 2.2), side = Math.signum((float) Math.sin(Math.toRadians(ANGLE[i]))) * (p % 2 == 0 ? 1 : -1);
            pose.rot[HEAD][1] -= side * 18 * r * hit;
            pose.rot[HEAD][0] -= 10 * r * hit;
            pose.rot[CHEST][0] -= 8 * r * hit;
            pose.rot[CHEST][1] -= side * 8 * r * hit;
            pose.crouch += .05f * hit;
            pose.bodyRoll -= side * 3 * hit;
        }
        float struggle = window(t, GRAB + 3, LIFT, 5);
        if (struggle > 0) {
            pose.rot[CHEST][1] += Mth.sin(t * .31f) * 14 * r * struggle;
            pose.rot[RIGHT_UPPER_ARM][2] += Mth.sin(t * .53f) * 10 * r * struggle;
            pose.rot[LEFT_UPPER_ARM][2] -= Mth.sin(t * .47f + 1) * 10 * r * struggle;
            pose.bodyRoll += Mth.sin(t * .29f) * 3 * struggle;
        }
        float wobble = window(t, LIFT, SLAM, 6);
        if (wobble > 0) {
            pose.bodyRoll += Mth.sin(t * .21f) * 4 * wobble;
            pose.rot[RIGHT_UPPER_ARM][2] += Mth.sin(t * .33f) * 8 * r * wobble;
            pose.rot[LEFT_UPPER_ARM][2] += Mth.sin(t * .37f + 2) * 8 * r * wobble;
        }
        return pose;
    }
    /** Opponent's facing: toward Sandman, turning to meet each attacker, at the end to face the giant. */
    private static float opponentYaw(float t) {
        float yaw = 180;
        yaw = Mth.rotLerp(ease((t - 113) / 6f), yaw, -90);
        yaw = Mth.rotLerp(ease((t - 132) / 6f), yaw, 180);
        yaw = Mth.rotLerp(ease((t - 140) / 6f), yaw, 20);
        yaw = Mth.rotLerp(ease((t - 158) / 8f), yaw, 180);
        yaw = Mth.rotLerp(ease((t - 288) / 12f), yaw, 0);
        return yaw;
    }
    /**
     * Where the opponent's feet are: a step back as the army rises, a step in with the counter, a
     * sidestep under the second swing; on the pillar; smashed into the sand.
     */
    private static Vec3 opponentFeet(float t) {
        double y = PILLAR * ease((t - LIFT) / (RISEN - LIFT));
        if (t >= SLAM - 1) y = PILLAR * (1 - Mth.clamp((t - SLAM + 1) / 3f, 0, 1) * Mth.clamp((t - SLAM + 1) / 3f, 0, 1)) - .25 * ease((t - SLAM) / 4f);
        double x = .25 * window(t, 119, 134, 3) - .3 * window(t, 142, 162, 3);
        double z = .2 * window(t, 58, 98, 6);
        return OPP.add(x, y, z);
    }

    // ------------------------------------------------------------------ choreography: the army
    private static float spawn(int i) { return 34 + i * 4; }
    private static float runStart(int i) { return i == 0 ? 92 : i == 1 ? 116 : 140 + (i - 2) * 2; }
    private static float runTime(int i) { return i < 2 ? 22 : 20 + (i % 3) * 2; }
    private static float contact(int i) { return i == 0 ? COUNTER : BACKHAND; }
    private static float punch(int i, int p) { return 186 + (i % 3) * 3 + p * 8; }
    private static float crumbleStart(int i) { return i < 2 ? contact(i) + 3 : MOUND + (i - 2) * 1.5f; }
    /** Gone once its crumble has run (12 ticks). */
    private static float gone(int i) { return crumbleStart(i) + 12; }
    private static Vec3 around(int i, double radius) {
        double a = Math.toRadians(ANGLE[i]);
        return OPP.add(Math.sin(a) * radius, 0, Math.cos(a) * radius);
    }
    private static Vec3 soldierFeet(int i, float t) {
        Vec3 start = around(i, 6), stop = around(i, i < 2 ? 1.05 : .82);
        float run = Mth.clamp((t - runStart(i)) / runTime(i), 0, 1);
        Vec3 at = start.lerp(stop, run * run * (2 - run));
        if (i < 2 && t > contact(i)) {
            // Broken by the counter: thrown back out, coming apart in the air.
            float k = t - contact(i);
            Vec3 out = stop.subtract(OPP).normalize();
            at = stop.add(out.scale(2.4 * (1 - Math.exp(-k / 4)))).add(0, Math.max(0, .9 * Math.sin(Math.PI * Math.min(1, k / 10f))), 0);
        } else if (i >= 2 && t > GRAB - 4) at = at.lerp(around(i, .66), ease((t - GRAB + 4) / 6f));
        return at;
    }
    private static float soldierYaw(int i, float t) {
        Vec3 to = OPP.subtract(soldierFeet(i, Math.min(t, runStart(i) + runTime(i))));
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        if (i < 2 && t > contact(i)) yaw += (t - contact(i)) * 14;
        return yaw;
    }

    // ------------------------------------------------------------------ the stage
    private Scene desert() {
        return new Scene() {
            public int skyTop() { return 0x6a7890; }
            public int skyBottom() { return 0xe8b878; }
            public FilmBackdrop.Params backdrop(float local, float t) {
                float storm = .12f + .2f * ease((t - 92) / 60f) + .25f * ease((t - MOUND) / 40f) + .3f * ease((t - GIANT) / 50f);
                if (t >= SLAM) storm = .9f - .45f * ease((t - SLAM) / 45f);
                float wind = .25f + .5f * storm;
                return FilmBackdrop.Params.desert(new Vec3(-.6, .17, .8), storm, wind, 0, 0);
            }
            public void render(FilmContext c) {
                float t = c.time();
                var level = Minecraft.getInstance().level;
                var s = state();
                if (level == null || s == null) return;
                if (sandman == null || sandman.entity() != level.getEntity(s.attacker)) sandman = FilmCast.of(level.getEntity(s.attacker));
                if (opponent == null || opponent.entity() != level.getEntity(s.target)) opponent = FilmCast.of(level.getEntity(s.target));
                Vec3 feet = opponentFeet(t);
                shadow(c, Vec3.ZERO, .6, .35f);
                shadow(c, OPP, t < LIFT ? .6 : 1.3, .35f);
                if (sandman != null) sandman.draw(c, Vec3.ZERO, 0, SANDMAN.sample(t), 1, 0xfff0d8);
                if (opponent != null) opponent.draw(c, feet, opponentYaw(t), opponentPose(t), 1, 0xffecd0);
                army(c, t);
                giant(c, t);
                pillar(c, t);
                sandFx(c, t);
            }
        };
    }
    private static SandSoldierModel<SandSoldierEntity> soldierModel() {
        if (soldierModel == null)
            soldierModel = new SandSoldierModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(SandSoldierModel.LAYER));
        return soldierModel;
    }
    /** Draws the shared soldier model as it is currently posed. */
    private static void drawSoldier(FilmContext c, Vec3 feet, float yaw, float scale, float alpha) {
        var pose = c.pose();
        pose.pushPose();
        pose.translate(feet.x, feet.y, feet.z);
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.scale(-scale, -scale, scale);
        pose.translate(0, -1.501, 0);
        soldierModel().renderToBuffer(pose, c.buffers().getBuffer(RenderType.entityTranslucent(SAND_TEXTURE)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, .93f, .8f, alpha);
        pose.popPose();
    }
    private static void army(FilmContext c, float t) {
        var model = soldierModel();
        for (int i = 0; i < COUNT; i++) {
            float born = spawn(i);
            if (t < born || t >= gone(i)) continue;
            Vec3 feet = soldierFeet(i, t);
            float yaw = soldierYaw(i, t), idle = t / 20f;
            float run = t >= runStart(i) && t < runStart(i) + runTime(i) ? 1 : 0;
            float runPhase = (t - runStart(i)) / 20f * 1.8f;
            if (t < born + 24) model.filmPose(idle, 0, 0, "spawn", (t - born) / 20f, .94f);
            else if (t >= crumbleStart(i)) model.filmPose(idle, 0, 0, "crumble", (t - crumbleStart(i)) / 20f, .94f);
            else if (i < 2 && t >= contact(i) - 6) model.filmPose(idle, 0, 0, i == 0 ? "strike_right" : "strike_left", (t - contact(i) + 6) / 20f, .94f);
            else if (i >= 2 && t >= GRAB - 4) {
                String strike = null; float since = 0;
                for (int p = 0; p < 3; p++) {
                    float at = punch(i, p);
                    if (t >= at && t < at + 13) { strike = (i + p) % 2 == 0 ? "strike_right" : "strike_left"; since = (t - at) / 20f; }
                }
                model.filmPose(idle, 0, 0, strike, since, .94f);
                if (strike == null) grab(model, ease((t - GRAB + 4) / 5f));
            } else model.filmPose(idle, runPhase, run, null, 0, .94f);
            drawSoldier(c, feet, yaw, 1, 1);
            if (run > 0) puff(c, feet.add(0, .12, 0), .35, DUST, .35f);
        }
    }
    /** Both arms forward, gripping. */
    private static void grab(SandSoldierModel<?> model, float k) {
        for (String side : new String[]{"right", "left"}) {
            var arm = model.bone(side + "_upper_arm");
            arm.xRot += -1.35f * k;
            arm.yRot += (side.equals("right") ? .25f : -.25f) * k;
            model.bone(side + "_forearm").xRot += -.35f * k;
        }
        model.bone("chest").xRot += .18f * k;
    }
    /** The giant: rises out of the dunes, lifts both fists overhead, brings them down, falls apart. */
    private static void giant(FilmContext c, float t) {
        if (t < GIANT) return;
        if (t >= CRUMBLE) disintegrate(c, t);
        if (t >= CRUMBLE + 30) return;
        var model = soldierModel();
        float idle = t / 20f;
        if (t < GIANT_UP) model.filmPose(idle, 0, 0, "spawn", (t - GIANT) / (GIANT_UP - GIANT) * 1.2f, 1.12f);
        else if (t >= CRUMBLE) {
            // Sagging as it comes apart, the fists still in the sand where they struck.
            model.filmPose(idle, 0, 0, "crumble", (t - CRUMBLE) / 40f * .6f, 1.12f);
            for (String side : new String[]{"right", "left"}) model.bone(side + "_upper_arm").xRot += -.55f;
        }
        else {
            model.filmPose(idle, 0, 0, null, 0, 1.12f);
            float up = ease((t - (GIANT_UP + 4)) / 18f), down = (float) Math.pow(Mth.clamp((t - (SLAM - 4)) / 4f, 0, 1), 2);
            float arm = -2.9f * up * (1 - down) + -.55f * down;
            for (String side : new String[]{"right", "left"}) {
                model.bone(side + "_upper_arm").xRot += arm;
                model.bone(side + "_upper_arm").zRot += (side.equals("right") ? .22f : -.22f) * up * (1 - down);
                model.bone(side + "_forearm").xRot += -.4f * up * (1 - down);
            }
            model.bone("chest").xRot += -.25f * up * (1 - down) + .45f * down;
            model.bone("head").xRot += .3f * down;
        }
        drawSoldier(c, GIANT_AT, 180, GIANT_SCALE, 1 - ease((t - CRUMBLE - 4) / 22f));
        // Sand pouring off it as it comes up and as it falls apart.
        float pour = window(t, GIANT, GIANT_UP + 10, 6) + window(t, CRUMBLE, CRUMBLE + 30, 4) * 1.5f;
        for (int i = 0; i < 44 && pour > 0; i++) {
            double fall = (hash(i) + t * .04) % 1;
            Vec3 top = GIANT_AT.add((hash(i + 3) - .5) * 3.2, 2 + hash(i + 7) * 4.5, (hash(i + 9) - .5) * 1.6);
            Vec3 head = top.add(0, -fall * top.y, 0);
            streak(c, head.add(0, .9, 0), head, .05 + hash(i + 2) * .06, SAND, 0, .7f * Math.min(1, pour), false);
        }
    }
    /** Parts of the giant's body in model pixels (feet at 0, front toward -z): x0, x1, y0, y1, z0, z1. */
    private static final float[][] BODY = {
            {-4, 4, 24, 32, -4, 4},                                   // head
            {-4.5f, 4.5f, 12, 24, -2.5f, 2.5f}, {-4.5f, 4.5f, 12, 24, -2.5f, 2.5f}, // chest
            {-9.5f, -4.5f, 6, 22, -8, -1}, {4.5f, 9.5f, 6, 22, -8, -1},      // arms, reaching down to the strike
            {-4.5f, -.5f, 0, 12, -2.5f, 2.5f}, {.5f, 4.5f, 0, 12, -2.5f, 2.5f}}; // legs
    private static final int GRAINS = 340;
    /**
     * The giant breaking up into sand: clumps tear away from the top down, tumble out on the wind
     * and rain into the dunes, each leaving a puff of dust where it came loose; a cloud rolls out
     * round its base.
     */
    private static void disintegrate(FilmContext c, float t) {
        var pose = c.pose();
        double px = GIANT_SCALE / 16.0;
        for (int i = 0; i < GRAINS; i++) {
            float[] part = BODY[i % BODY.length];
            double lx = Mth.lerp(hash(i * 3 + 1), part[0], part[1]) * 1.12, ly = Mth.lerp(hash(i * 3 + 2), part[2], part[3]);
            double lz = Mth.lerp(hash(i * 3 + 3), part[4], part[5]) * 1.12;
            Vec3 home = GIANT_AT.add(lx * px, ly * px, lz * px);
            float loose = CRUMBLE + (float) (1 - ly / 32) * 16 + (float) hash(i + 500) * 4;
            float k = t - loose;
            if (k < 0) continue;
            double out = Math.signum(lx == 0 ? 1 : lx) * (.025 + hash(i + 7) * .05);
            double vx = out + .012, vz = (hash(i + 9) - .5) * .06 - .015, vy = .015 + hash(i + 11) * .05;
            double y = home.y + vy * k - .014 * k * k;
            double size = (.09 + hash(i + 13) * .16) * GIANT_SCALE / 3.6;
            double landed = 0;
            if (y < size) {
                // Down in the dunes: the clump settles and sinks away.
                double tHit = (vy + Math.sqrt(vy * vy + 4 * .014 * Math.max(0, home.y - size))) / (2 * .014);
                landed = k - tHit;
                y = size - Math.min(1, landed / 25) * size * 1.6;
                k = (float) tHit;
            }
            double shrink = 1 - Math.min(1, Math.max(0, landed) / 25);
            if (shrink <= 0) continue;
            double x = home.x + vx * k + .006 * k * k, z = home.z + vz * k;
            float s = (float) (size * shrink);
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(Axis.XP.rotationDegrees(k * 21 * (float) (hash(i + 15) - .5)));
            pose.mulPose(Axis.ZP.rotationDegrees(k * 27 * (float) (hash(i + 17) - .5)));
            int colour = i % 3 == 0 ? SAND_DARK : i % 3 == 1 ? SAND : 0xe6d09e;
            cube(c, pose.last().pose(), -s, -s, -s, s, s, s, colour);
            pose.popPose();
        }
        // Dust where the clumps tear away, and grains streaming off them.
        for (int i = 0; i < GRAINS; i += 9) {
            float[] part = BODY[i % BODY.length];
            double ly = Mth.lerp(hash(i * 3 + 2), part[2], part[3]);
            float since = t - (CRUMBLE + (float) (1 - ly / 32) * 16 + (float) hash(i + 500) * 4);
            if (since < 0 || since > 14) continue;
            double lx = Mth.lerp(hash(i * 3 + 1), part[0], part[1]) * 1.12, lz = Mth.lerp(hash(i * 3 + 3), part[4], part[5]) * 1.12;
            Vec3 at = GIANT_AT.add(lx * px + since * .03, ly * px - since * .02, lz * px);
            puff(c, at, (.5 + since * .06) * GIANT_SCALE / 3.6, DUST, .5f * (1 - since / 14));
            streak(c, at, at.add(.6, -.9, 0), .04, SAND, .6f * (1 - since / 14), 0, false);
        }
        // The cloud rolling out round its base.
        float k = t - CRUMBLE;
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2 / 18 + hash(i) * .3, r = 1.2 + Math.min(k, 40) * .07;
            puff(c, GIANT_AT.add(Math.cos(a) * r * 1.3 + k * .02, .4 + hash(i + 3) * .8 + Math.min(k, 40) * .02, Math.sin(a) * r),
                    1.4 + Math.min(k, 40) * .04, i % 2 == 0 ? DUST : SAND_DARK, .5f * Math.min(1, k / 6f));
        }
    }
    /** The mound the soldiers become, the pillar it rises into, and what is left of it after the slam. */
    private static void pillar(FilmContext c, float t) {
        if (t < MOUND) return;
        var m = c.pose().last().pose();
        float mound = ease((t - MOUND) / 20f);
        float top = PILLAR * ease((t - LIFT) / (RISEN - LIFT));
        boolean broken = t >= SLAM;
        float burst = broken ? t - SLAM : 0;
        int n = 0;
        // Column: rings of sand blocks from the ground up to the top, the mound cupping the opponent's legs.
        for (double y = 0; y < top + 1.0 * mound; y += .34) {
            double cap = y - top;
            double radius = cap > 0 ? .95 * Math.sqrt(Math.max(0, 1 - cap * cap / 1.1)) * mound : .9 + .12 * Math.sin(y * 3 + t * .2);
            int around = Math.max(5, (int) (radius * 9));
            for (int k = 0; k < around; k++) {
                n++;
                double a = k * Math.PI * 2 / around + y * .7 + hash(n) * .3;
                double size = .2 + hash(n + 5) * .12;
                Vec3 at = OPP.add(Math.cos(a) * radius, y + size, Math.sin(a) * radius);
                if (broken) {
                    // Blown outward and falling.
                    Vec3 out = new Vec3(Math.cos(a), .4 + hash(n + 2) * .8, Math.sin(a));
                    at = at.add(out.scale(burst * (.18 + hash(n + 1) * .2))).add(0, -.012 * burst * burst, 0);
                    if (at.y < -.5 || burst > 26) continue;
                    size *= 1 - burst / 30;
                }
                int colour = hash(n + 9) < .5 ? SAND : SAND_DARK;
                cube(c, m, (float) (at.x - size), (float) (at.y - size), (float) (at.z - size), (float) (at.x + size), (float) (at.y + size), (float) (at.z + size), colour);
            }
        }
        // Streams of sand spiralling up the column while it grows.
        float rising = window(t, LIFT - 4, RISEN + 6, 6);
        for (int i = 0; i < 18 && rising > 0; i++) {
            double life = (hash(i) + t * .05) % 1, a = i * 2.1 + life * 7;
            Vec3 p0 = OPP.add(Math.cos(a) * 1.25, life * (top + 1), Math.sin(a) * 1.25);
            Vec3 p1 = OPP.add(Math.cos(a + .5) * 1.25, (life + .06) * (top + 1), Math.sin(a + .5) * 1.25);
            streak(c, p0, p1, .06, SAND, .6f * rising, .2f * rising, false);
        }
    }
    /** Dust, bursts and the storm. */
    private static void sandFx(FilmContext c, float t) {
        // Each soldier bursts out of the dunes, and each broken one goes up in sand.
        for (int i = 0; i < COUNT; i++) {
            float k = t - spawn(i);
            if (k >= 0 && k < 18) burst(c, around(i, 6), k, 1.1f, 10 + i);
            float g = t - crumbleStart(i);
            if (g >= 0 && g < 20) burst(c, soldierFeet(i, t).add(0, .9, 0), g, i < 2 ? 1.4f : .8f, 40 + i);
        }
        // Punches landing.
        for (int i = 2; i < COUNT; i++) for (int p = 0; p < 3; p++) {
            float k = t - punch(i, p) - 5;
            if (k >= 0 && k < 6) puff(c, OPP.add(around(i, .3).subtract(OPP)).add(0, 1.1, 0), .3 + k * .08, DUST, .5f * (1 - k / 6));
        }
        // The giant breaking the surface.
        if (t >= GIANT && t < GIANT_UP + 20) {
            float k = t - GIANT;
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                puff(c, GIANT_AT.add(Math.cos(a) * (1.6 + k * .03), .3 + hash(i) * 1.2 + k * .02, Math.sin(a) * (1.2 + k * .02)), 1.2 + k * .02, DUST, .5f * (1 - k / 58));
            }
        }
        // The slam: a ring of sand racing out, a wall of dust rising, chunks in the air.
        if (t >= SLAM) {
            float k = t - SLAM, fade = 1 - Math.min(1, k / 50f);
            ring(c, OPP.add(0, .05, 0), k * .45, .8 + k * .03, DUST, .7f * fade, false);
            ring(c, OPP.add(0, .06, 0), k * .3, .35, 0xfff0c8, .5f * (1 - Math.min(1, k / 12f)), true);
            for (int i = 0; i < 30; i++) {
                double a = i * 2.399, r = Math.min(k, 30) * (.12 + hash(i) * .12);
                puff(c, OPP.add(Math.cos(a) * r, .4 + hash(i + 3) * 1.6 + k * .03, Math.sin(a) * r), 1.2 + k * .05, i % 3 == 0 ? SAND_DARK : DUST, .65f * fade);
            }
            glow(c, OPP.add(0, .6, 0), 3.5, 0xfff0d0, (float) Math.exp(-k * .25) * .8f);
        }
        // The storm: grains blowing across everything, thicker as it builds.
        float storm = .12f + .2f * ease((t - 92) / 60f) + .25f * ease((t - MOUND) / 40f) + .3f * ease((t - GIANT) / 50f);
        if (t >= SLAM) storm = .9f - .45f * ease((t - SLAM) / 45f);
        Vec3 cam = c.camera();
        for (int i = 0; i < 90; i++) {
            if (hash(i + 77) > storm) continue;
            double x = ((hash(i) + t * (.012 + hash(i + 4) * .01)) % 1) * 24 - 12;
            Vec3 at = cam.add(x, (hash(i + 2) - .4) * 4, (hash(i + 6) - .5) * 14 + 4);
            streak(c, at, at.add(.5 + storm, -.02, 0), .012, 0xf6deb0, 0, .5f, false);
        }
    }
    /** A burst of sand: a puff of dust and grains flung up and falling back. */
    private static void burst(FilmContext c, Vec3 at, float k, float size, int seed) {
        float fade = 1 - k / 20f;
        puff(c, at.add(0, .2 + k * .03, 0), (.6 + k * .06) * size, DUST, .55f * fade);
        for (int i = 0; i < 12; i++) {
            Vec3 d = new Vec3(hash(seed * 31 + i) - .5, .5 + hash(seed * 17 + i) * .8, hash(seed * 7 + i) - .5).normalize();
            Vec3 head = at.add(d.scale(k * .14 * size)).add(0, -.006 * k * k, 0);
            streak(c, head.subtract(d.scale(.15)), head, .03, SAND, 0, .8f * fade, false);
        }
    }
}
