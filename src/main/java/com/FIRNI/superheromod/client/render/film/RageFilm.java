package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.hulk.HulkClient;
import com.FIRNI.superheromod.client.render.hulk.HulkFx;
import com.FIRNI.superheromod.client.render.hulk.RagePath;
import com.FIRNI.superheromod.heroes.hulk.HulkConfig;
import com.FIRNI.superheromod.heroes.hulk.HulkRageSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.FIRNI.superheromod.client.render.film.FilmFx.*;
import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * GAMMA RAGE (about 14 s with the default 2 s delay), shot in the real world. The bodies are drawn
 * where RagePath puts them (HulkFx.performers: Hulk himself, the target as a jointed puppet); this
 * film is the camera, the sound and the picture over it.
 *   0-42      gamma gathers in him, trembling; the roar, close on his face
 *   42-78     the leap onto the target, the one-armed grab
 *   78-150    straight up into the sky; held in both hands before his face, the roar into theirs
 *   150-182   flung down; the target crashes first (the first damage)
 *   182-smash Hulk hangs up there, both fists raised, and dives
 *   smash     the second, bigger impact, seen from far off; then he rises out of the dust and roars
 * "Calm camera" (client setting) takes out the hard shakes and the whip pans.
 */
final class RageFilm implements Film {
    static final String ID = HulkRageSession.ID;
    private final int attacker;
    private final RagePath path;
    private final Segment[] segments;
    private final float shake;
    private final boolean calm;

    RageFilm(int attacker) {
        this.attacker = attacker;
        var s = FilmSessionClient.get(attacker);
        var level = Minecraft.getInstance().level;
        double d = s == null ? 5 : HulkFx.distance(s);
        int smash = ULT_CRASH + 40;
        if (s != null && level != null && level.getEntity(attacker) != null) smash = HulkClient.smashTick(level.getEntity(attacker));
        path = new RagePath(d, smash);
        calm = HulkConfig.get(HulkConfig.CALM_CAMERA);
        shake = (float) (double) HulkConfig.get(HulkConfig.SHAKE) * (calm ? .3f : 1);
        double D = d, H = ULT_HEIGHT;
        Vec3 grab = path.hulk(ULT_GRAB), top = path.hulk(ULT_HOLD + 4), crater = path.crater(), spot = path.smashSpot();
        int dive = smash - 14, total = path.total();
        List<FilmShot> shots = new ArrayList<>();
        // Low in front of him, pushing in as the gamma builds; he trembles, the ground with him.
        shots.add(shot(0, ULT_ROAR, .02f, .2f).path(v(-1.6, .7, 4.2), v(-.9, 1.2, 2.9)).look(v(0, 2.1, 0), v(0, 2.5, 0)).fov(60, 50).build());
        // The roar: right on his face, the camera thrown back by it.
        shots.add(shot(ULT_ROAR, ULT_ROAR + 10, .5f, .35f).path(v(-.5, 2.9, 2.2), v(-.6, 2.95, 2.6)).look(v(0, 3.0, 0)).fov(44, 54).roll(0, -3).build());
        // Behind him, wide: the target ahead, the crouch.
        shots.add(shot(ULT_ROAR + 10, ULT_LEAP, .08f, .1f).path(v(1.8, 2.4, -4.2), v(1.6, 2.2, -3.8)).look(v(0, 1.4, D * .6)).fov(64, 62).whip(!calm).build());
        // The leap, tracked side on.
        shots.add(shot(ULT_LEAP, ULT_GRAB - 2, .1f, .15f).path(follow(path::hulk, new Vec3(6.5, 1.4, -.5), ULT_LEAP, ULT_LEAP + 9, ULT_GRAB - 2))
                .look(follow(path::hulk, new Vec3(0, 2, .5), ULT_LEAP, ULT_LEAP + 9, ULT_GRAB - 2)).fov(70, 64).build());
        // The grab, low behind the target: his hand closing round them, hoisting.
        shots.add(shot(ULT_GRAB - 2, ULT_SKY, .45f, .1f).path(v(1.4, .5, D + 2.6), v(1.2, .45, D + 2.3)).look(grab.add(0, 2.6, 0), grab.add(0, 3.6, 0)).fov(62, 58).build());
        // Up into the sky: from the ground looking straight up after them, then rising alongside.
        shots.add(shot(ULT_SKY, ULT_SKY + 10, .6f, .3f).path(grab.add(3, .4, -2), grab.add(3.2, .5, -2.2)).look(grab.add(0, 3, 0), grab.add(0, H * .5, 0)).fov(74, 80).build());
        shots.add(shot(ULT_SKY + 10, ULT_HOLD + 6, .25f, .1f).path(follow(path::hulk, new Vec3(3.5, -1.5, -3), ULT_SKY + 10, ULT_SKY + 18, ULT_HOLD + 6))
                .look(follow(path::hulk, new Vec3(0, 2.6, .3), ULT_SKY + 10, ULT_SKY + 18, ULT_HOLD + 6)).fov(70, 62).roll(-4, 2).build());
        // Held before his face: the two-shot, side on, then close as he roars into them.
        shots.add(shot(ULT_HOLD + 6, ULT_HOLD + 12, .05f, .05f).path(top.add(4.5, 2.4, .6), top.add(4.2, 2.5, .6)).look(top.add(0, 2.5, .6)).fov(52, 50).build());
        shots.add(shot(ULT_HOLD + 12, ULT_THROW, .45f, .25f).path(top.add(1.9, 2.7, 2.6), top.add(1.6, 2.75, 2.3)).look(top.add(0, 2.7, .4)).fov(48, 42).roll(2, -2).build());
        // The throw: from above his shoulder, looking down the fall to the ground.
        shots.add(shot(ULT_THROW, ULT_CRASH - 4, .3f, .15f).path(top.add(-1.4, 4.2, -2.2), top.add(-1.2, 4.4, -2.0)).look(top.add(0, 1, 1.4), crater.add(0, 1, 0)).fov(62, 54).whip(!calm).build());
        // The crash, from the ground nearby.
        shots.add(shot(ULT_CRASH - 4, ULT_RAISE, .2f, 1f).path(crater.add(4.2, .8, 3.6), crater.add(4.8, 1.1, 4.2)).look(crater.add(0, 1.2, 0), crater.add(0, .4, 0)).fov(66, 72).build());
        // Up there, alone: he raises both fists over his head. From below, small against the sky.
        shots.add(shot(ULT_RAISE, dive, .05f, .1f).path(crater.add(2.4, .5, 3), crater.add(2.2, .45, 2.8)).look(top.add(0, 2, 0), top.add(0, 3.2, 0)).fov(46, 38).build());
        // The dive, chasing him down.
        shots.add(shot(dive, smash - 1, .3f, .6f).path(follow(path::hulk, new Vec3(4, 2.5, -3.5), dive, dive + 7, smash - 1))
                .look(follow(path::hulk, new Vec3(0, 1, 1), dive, dive + 7, smash - 1)).fov(72, 84).roll(3, -2).build());
        // The smash, seen from far off: the whole crater, the dust rolling out.
        shots.add(shot(smash - 1, smash + 30, 1f, .15f).path(spot.add(18, 9, -14), spot.add(20, 10, -16)).look(spot.add(0, 1, 0)).fov(58, 54).build());
        // Out of the dust: low in front, slowly back, the power stance and a last roar.
        shots.add(shot(smash + 30, total, .05f, .2f).path(spot.add(-1.2, .6, -4.2), spot.add(-2.2, 1.2, -6.5)).look(spot.add(0, 2.4, 0), spot.add(0, 2.6, 0)).fov(56, 62).build());
        segments = new Segment[]{new Segment(0, total, shots.toArray(new FilmShot[0]), null, 0, 0)};
    }
    private FilmShot.Builder shot(float start, float end, float a, float b) { return FilmShot.of(start, end).shake(a * shake, b * shake); }
    private static Vec3[] follow(Function<Float, Vec3> body, Vec3 offset, float... times) {
        Vec3[] out = new Vec3[times.length];
        for (int i = 0; i < times.length; i++) out[i] = body.apply(times[i]).add(offset);
        return out;
    }
    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }
    private FilmSessionClient.State state() { return FilmSessionClient.get(attacker); }
    @Override public Vec3 origin() { var s = state(); return s == null ? Vec3.ZERO : s.anchor; }
    @Override public Vec3 forward() { var s = state(); return s == null ? new Vec3(0, 0, 1) : FilmSessionClient.forward(s); }
    @Override public float duration() { return path.total(); }
    @Override public Segment[] segments() { return segments; }
    @Override public float time(float partial) { var s = state(); return s == null ? duration() : FilmSessionClient.time(s, partial); }
    @Override public boolean active() { var s = state(); return s != null && ID.equals(s.film); }
    @Override public float[] impacts() {
        return calm ? new float[]{ULT_CRASH, path.smash()} : new float[]{ULT_GRAB, ULT_SKY, ULT_CRASH, path.smash()};
    }
    @Override public float blendOut() { return 14; }

    @Override public Cue[] cues() {
        List<Cue> c = new ArrayList<>();
        int smash = path.smash();
        for (int t = 0; t < ULT_ROAR; t += 7) add(c, t, SoundEvents.WARDEN_HEARTBEAT, .7f + t / 40f, .7f + t / 60f);
        add(c, ULT_ROAR, SoundEvents.RAVAGER_ROAR, 1f, .55f);
        add(c, ULT_LEAP, SoundEvents.GENERIC_EXPLODE, .7f, 1.2f); add(c, ULT_LEAP, SoundEvents.ANVIL_LAND, .6f, .5f);
        add(c, ULT_LEAP + 2, SoundEvents.ELYTRA_FLYING, .6f, 1.2f);
        add(c, ULT_GRAB, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .5f); add(c, ULT_GRAB, SoundEvents.ANVIL_LAND, .5f, .7f);
        add(c, ULT_SKY, SoundEvents.GENERIC_EXPLODE, 1f, .7f); add(c, ULT_SKY + 1, SoundEvents.ELYTRA_FLYING, 1f, .8f);
        add(c, ULT_HOLD + 12, SoundEvents.RAVAGER_ROAR, 1f, .5f); add(c, ULT_HOLD + 13, SoundEvents.WARDEN_ROAR, .9f, .7f);
        add(c, ULT_THROW, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .5f); add(c, ULT_THROW + 2, SoundEvents.ELYTRA_FLYING, .8f, 1.4f);
        add(c, ULT_CRASH + 2, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, .8f, .5f);
        add(c, ULT_RAISE + 2, SoundEvents.WARDEN_HEARTBEAT, 1f, .6f); add(c, ULT_RAISE + 14, SoundEvents.WARDEN_HEARTBEAT, 1f, .65f);
        add(c, smash - 14, SoundEvents.ELYTRA_FLYING, 1f, .7f); add(c, smash - 8, SoundEvents.RAVAGER_ROAR, .8f, .6f);
        add(c, smash, SoundEvents.ANVIL_LAND, 1f, .4f);
        add(c, smash + 10, SoundEvents.ROOTED_DIRT_BREAK, 1f, .5f);
        add(c, smash + 43, SoundEvents.RAVAGER_ROAR, 1f, .5f); add(c, smash + 44, SoundEvents.WARDEN_ROAR, .7f, .8f);
        return c.toArray(new Cue[0]);
    }
    private static void add(List<Cue> list, float t, SoundEvent sound, float volume, float pitch) { list.add(new Cue(t, sound, volume, pitch)); }

    /** A green tinge while the gamma gathers; the white of the smash clearing. */
    @Override public int grade(float t) {
        int smash = path.smash();
        float white = window(t, smash - .5f, smash + 3, 2) * .85f + (t > smash + 3 ? .85f * (1 - ease((t - smash - 3) / 10f)) : 0);
        if (white > .01f && t >= smash - 1) return ((int) (Math.min(1, white) * 255) << 24) | 0xffffff;
        float green = window(t, 0, ULT_LEAP, 10) * .22f;
        if (green <= .01f) return 0;
        return ((int) (green * 255) << 24) | 0x1a5a12;
    }
    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        var font = Minecraft.getInstance().font;
        int smash = path.smash();
        float title = window(t, smash + 42, path.total() - 2, 4);
        if (title <= 0) return;
        String text = "GAMA ÖFKESİ";
        float slam = 1 + .25f * (1 - ease((t - smash - 42) / 5f));
        g.pose().pushPose();
        g.pose().translate(w / 2f, h * .24f, 0);
        g.pose().scale(3.0f * slam, 3.0f * slam, 1);
        g.drawString(font, text, -font.width(text) / 2, -4, HudStyle.alpha(0xFF7CFF5A, title), false);
        g.pose().popPose();
        String sub = "Gamma Rage";
        int sw = HudStyle.captionWidth(font, sub), y = (int) (h * .24f) + 20;
        HudStyle.caption(g, font, sub, w / 2, y, HudStyle.alpha(HudStyle.TEXT, title * .85f), 0);
        g.fill(w / 2 - sw / 2 - 34, y + 3, w / 2 - sw / 2 - 8, y + 4, HudStyle.alpha(0xFF6CE04A, title));
        g.fill(w / 2 + sw / 2 + 8, y + 3, w / 2 + sw / 2 + 34, y + 4, HudStyle.alpha(0xFF6CE04A, title));
    }
}
