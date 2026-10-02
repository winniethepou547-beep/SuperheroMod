package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.thor.AerialPath;
import com.FIRNI.superheromod.heroes.thor.GodOfThunderSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.FIRNI.superheromod.heroes.thor.ThorAction.*;
import static com.FIRNI.superheromod.client.render.film.FilmFx.*;

/**
 * GOD OF THUNDER — Aerial Punishment (18 s), shot in the real world. The bodies are drawn where
 * AerialPath puts them (ThorFx.performers: Thor himself, the target as a jointed puppet) and
 * Thor moves by ThorMotion; this film is the camera, the sound and the picture over it.
 *   0-24     behind the target; Thor comes at them
 *   24-52    the left-to-right hit, straight into the right-to-left that breaks the guard
 *   52-104   the crouch, the uppercut, the camera rising with the target into the gathering cloud
 *   104-178  Thor whirls Mjolnir and throws himself up after them; he flings the hammer on into the
 *            clouds (they keep flashing where it vanished) and catches them empty-handed at the top
 *   178-266  the lock (both arms), the cry, lightning out of his eyes, the columns, the whiteout, silence
 *   266-312  the fall, the let-go, Mjolnir back out of the clouds into his hand, the target driven into the ground
 *   312-362  Thor glides down beside the crater; the storm still overhead
 */
final class GodOfThunderFilm implements Film {
    static final String ID = GodOfThunderSession.ID;
    private final int attacker;
    private final AerialPath path;
    private final Segment[] segments;

    GodOfThunderFilm(int attacker) {
        this.attacker = attacker;
        var s = FilmSessionClient.get(attacker);
        var level = Minecraft.getInstance().level;
        double d = 5;
        if (s != null && level != null && level.getEntity(s.target) != null) {
            Vec3 to = level.getEntity(s.target).position().subtract(s.anchor);
            d = Math.max(2.5, Math.min(20, Math.sqrt(to.x * to.x + to.z * to.z)));
        }
        path = new AerialPath(d);
        double D = d, H = ULT_HEIGHT;
        Vec3 lock = path.thor(ULT_SCREAM), crater = path.crater(), land = path.landing();
        float s1 = ULT_HIT1 - SWING_HIT / ULT_SWING_PACE, s2 = ULT_HIT2 - SWING_HIT / ULT_SWING_PACE;
        Vec3 cloud = path.cloudPoint();
        segments = new Segment[]{new Segment(0, ULT_TOTAL, new FilmShot[]{
                // Behind the target: Thor a few blocks off, then coming.
                FilmShot.of(0, s1).path(v(1.6, 1.9, D + 2.7), v(1.4, 1.85, D + 2.3)).look(v(0, 1.2, D * .45), v(0, 1.3, D - 1.2)).fov(50, 46).shake(.02f, .05f).build(),
                // The first hit, side on: the whole body behind it.
                FilmShot.of(s1, s2).path(v(3.0, 1.45, D - 1.0), v(2.7, 1.4, D - .9)).look(v(0, 1.2, D - .85)).fov(52, 49).shake(.04f, .06f).build(),
                // Straight into the second, from the other side: the guard goes up and breaks.
                FilmShot.of(s2, ULT_LOAD).path(v(-2.8, 1.35, D - .6), v(-2.5, 1.3, D - .5)).look(v(0, 1.25, D - .4)).fov(50, 48).shake(.05f, .08f).whip().build(),
                // Low: the crouch, then the uppercut.
                FilmShot.of(ULT_LOAD, ULT_UPPER + 1).path(v(1.5, .45, D - 3.3), v(1.3, .4, D - 3.0)).look(v(0, .95, D - 1.4), v(0, 1.4, D - .7)).fov(58, 60).shake(.03f, .1f).build(),
                // Rising with the target into the gathering cloud.
                FilmShot.of(ULT_UPPER + 1, ULT_SPIN + 2).path(follow(path::target, new Vec3(2.4, -1.6, 1.8), ULT_UPPER + 1, ULT_UPPER + 12, ULT_UPPER + 24, ULT_SPIN + 2))
                        .look(follow(path::target, new Vec3(0, 1, 0), ULT_UPPER + 1, ULT_UPPER + 12, ULT_UPPER + 24, ULT_SPIN + 2)).fov(64, 72).roll(-3, 2).shake(.3f, .15f).build(),
                // Back down to Thor: the whirl.
                FilmShot.of(ULT_SPIN + 2, ULT_RISE).path(v(1.8, 5.2, D - 4.2), v(1.5, 4.4, D - 3.7)).look(v(0, 1.0, D - 1.75), v(0, 1.6, D - 1.75)).fov(58, 62).shake(.05f, .2f).whip().build(),
                // Following him up, from behind and below.
                FilmShot.of(ULT_RISE, ULT_TOSS - 2).path(follow(path::thor, new Vec3(.9, -1.6, -2.6), ULT_RISE, ULT_RISE + 8, ULT_RISE + 15, ULT_TOSS - 2))
                        .look(follow(path::thor, new Vec3(0, 2.6, .6), ULT_RISE, ULT_RISE + 8, ULT_RISE + 15, ULT_TOSS - 2)).fov(72, 76).roll(-5, 3).shake(.35f, .25f).build(),
                // The throw: the camera tips up and follows Mjolnir past the target into the clouds.
                FilmShot.of(ULT_TOSS - 2, ULT_VANISH + 6).path(path.thor(ULT_TOSS).add(1.6, .2, -2.2), path.thor(ULT_TOSS + 8).add(1.8, 1.0, -2.4), path.thor(ULT_VANISH).add(2.0, 2.2, -2.6))
                        .look(path.thor(ULT_TOSS).add(0, 2.4, 0), hammerAt(ULT_TOSS + 6), hammerAt(ULT_TOSS + 13), cloud.add(0, 1, 0), cloud).fov(66, 58).roll(2, -3).shake(.2f, .3f).build(),
                // Back to Thor coming up under them, empty-handed: the catch, wide and side on.
                FilmShot.of(ULT_VANISH + 6, ULT_SCREAM).path(v(6.5, H - 2.2, D), v(6.0, H - 1.4, D + .4)).look(v(0, H - 1.2, D + .2), v(0, H - .2, D + .3)).fov(58, 54).shake(.08f, .12f).whip().build(),
                // The cry.
                FilmShot.of(ULT_SCREAM, ULT_STORM + 10).path(lock.add(2.2, 1.4, -1.6), lock.add(1.9, 1.6, -1.4)).look(lock.add(0, 1.6, .3)).fov(54, 48).shake(.1f, .45f).build(),
                // The columns and the whiteout.
                FilmShot.of(ULT_STORM + 10, ULT_FADE).path(lock.add(11, -3, -8), lock.add(12, -2, -6)).look(lock.add(0, 2, 0)).fov(66, 76).shake(.45f, .9f).build(),
                // The fall: from above, the let-go, the hammer coming back down to him.
                FilmShot.of(ULT_FADE, ULT_RECALL + 4).path(follow(path::thor, new Vec3(2.2, 2.6, -2.4), ULT_FADE, ULT_LET_GO, ULT_LET_GO + 10, ULT_RECALL + 4))
                        .look(follow(path::thor, new Vec3(0, 1.0, .5), ULT_FADE, ULT_LET_GO, ULT_LET_GO + 10, ULT_RECALL + 4)).fov(62, 68).shake(.1f, .2f).build(),
                // From the ground: the target comes down, the impact.
                FilmShot.of(ULT_RECALL + 4, ULT_CRASH + 4).path(crater.add(3.6, .6, 3.4), crater.add(3.2, .5, 3.0))
                        .look(follow(path::target, new Vec3(0, .8, 0), ULT_RECALL + 4, ULT_RECALL + 9, ULT_CRASH - 4, ULT_CRASH + 4)).fov(70, 78).shake(.2f, 1f).build(),
                // Thor comes down beside the crater; the camera draws back under the storm.
                FilmShot.of(ULT_CRASH + 4, ULT_TOTAL).path(crater.add(1.4, .45, 1.8), crater.add(3.5, 1.5, -.5), land.add(5.5, 3.2, -4.0), land.add(6.5, 3.8, -5.0))
                        .look(follow(path::thor, new Vec3(0, 1.3, 0), ULT_CRASH + 4, ULT_CRASH + 18, ULT_LANDED, ULT_TOTAL)).fov(60, 58).shake(.3f, .03f).build()},
                null, 0, 0)};
    }
    /** Where the thrown hammer is at time t (stage space); its last known spot if it is not flying then. */
    private Vec3 hammerAt(float t) {
        Vec3 h = path.hammer(t);
        return h == null ? path.cloudPoint() : h;
    }
    /** Control points that follow a body: its position plus an offset, at the given times. */
    private static Vec3[] follow(Function<Float, Vec3> body, Vec3 offset, float... times) {
        Vec3[] out = new Vec3[times.length];
        for (int i = 0; i < times.length; i++) out[i] = body.apply(times[i]).add(offset);
        return out;
    }
    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }
    private FilmSessionClient.State state() { return FilmSessionClient.get(attacker); }
    @Override public Vec3 origin() { var s = state(); return s == null ? Vec3.ZERO : s.anchor; }
    @Override public Vec3 forward() { var s = state(); return s == null ? new Vec3(0, 0, 1) : FilmSessionClient.forward(s); }
    @Override public float duration() { return ULT_TOTAL; }
    @Override public Segment[] segments() { return segments; }
    @Override public float time(float partial) { var s = state(); return s == null ? duration() : FilmSessionClient.time(s, partial); }
    @Override public boolean active() { var s = state(); return s != null && ID.equals(s.film); }
    @Override public float[] impacts() { return new float[]{ULT_HIT1, ULT_HIT2, ULT_UPPER, ULT_CATCH, ULT_CRASH}; }
    @Override public float blendOut() { return 14; }

    @Override public Cue[] cues() {
        List<Cue> c = new ArrayList<>();
        add(c, 2, SoundEvents.WEATHER_RAIN, .3f, .8f);
        add(c, ULT_LUNGE, SoundEvents.TRIDENT_RIPTIDE_1, .6f, 1.4f);
        // The hits: metal, flesh and a crack of lightning each.
        add(c, ULT_HIT1 - 4, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .7f);
        add(c, ULT_HIT1, SoundEvents.ANVIL_LAND, 1f, .9f); add(c, ULT_HIT1, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .6f);
        add(c, ULT_HIT1, SoundEvents.LIGHTNING_BOLT_IMPACT, .7f, 1.6f);
        add(c, ULT_HIT2 - 4, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, .6f);
        add(c, ULT_HIT2, SoundEvents.SHIELD_BREAK, 1f, .7f); add(c, ULT_HIT2, SoundEvents.ANVIL_LAND, 1f, .7f);
        add(c, ULT_HIT2, SoundEvents.LIGHTNING_BOLT_IMPACT, .8f, 1.3f);
        // A short silence while he sinks into the crouch.
        add(c, ULT_LOAD + 8, SoundEvents.TRIDENT_RIPTIDE_2, .5f, .6f);
        add(c, ULT_UPPER, SoundEvents.ANVIL_LAND, 1f, .5f); add(c, ULT_UPPER, SoundEvents.GENERIC_EXPLODE, .8f, 1.1f);
        add(c, ULT_UPPER, SoundEvents.PLAYER_ATTACK_CRIT, 1f, .6f); add(c, ULT_UPPER, SoundEvents.LIGHTNING_BOLT_THUNDER, .6f, 1.4f);
        add(c, ULT_UPPER + 5, SoundEvents.ELYTRA_FLYING, .8f, 1.3f);
        add(c, ULT_UPPER + 33, SoundEvents.LIGHTNING_BOLT_THUNDER, .6f, .6f);
        add(c, ULT_UPPER + 50, SoundEvents.LIGHTNING_BOLT_THUNDER, .9f, .9f); add(c, ULT_UPPER + 60, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1f);
        add(c, ULT_UPPER + 70, SoundEvents.LIGHTNING_BOLT_THUNDER, .9f, .8f);
        add(c, ULT_SPIN, SoundEvents.TRIDENT_RIPTIDE_1, .8f, 1f); add(c, ULT_SPIN + 10, SoundEvents.TRIDENT_RIPTIDE_2, .9f, 1.1f);
        add(c, ULT_RISE, SoundEvents.TRIDENT_RIPTIDE_3, 1f, .9f); add(c, ULT_RISE + 2, SoundEvents.ELYTRA_FLYING, .7f, 1f);
        add(c, ULT_TOSS, SoundEvents.TRIDENT_THROW, 1f, .6f); add(c, ULT_TOSS + 2, SoundEvents.TRIDENT_THUNDER, .5f, 1.4f);
        add(c, ULT_VANISH, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, .8f);
        add(c, ULT_CATCH, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, .5f); add(c, ULT_CATCH, SoundEvents.ANVIL_PLACE, .5f, .6f);
        add(c, ULT_SCREAM, SoundEvents.RAVAGER_ROAR, 1f, .65f);
        add(c, ULT_EYEBOLT, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 1.2f); add(c, ULT_EYEBOLT, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 1f);
        for (int t = ULT_STORM; t < ULT_BLAST; t += 5) add(c, t, t % 2 == 0 ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.LIGHTNING_BOLT_IMPACT, .9f, .7f + (t % 3) * .15f);
        add(c, ULT_BLAST, SoundEvents.GENERIC_EXPLODE, 1f, .5f); add(c, ULT_BLAST, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, .5f);
        // Then nothing until the white clears.
        add(c, ULT_FADE + 4, SoundEvents.WEATHER_RAIN, .4f, .7f); add(c, ULT_LET_GO + 2, SoundEvents.ELYTRA_FLYING, .6f, .7f);
        add(c, ULT_RECALL - 14, SoundEvents.TRIDENT_RETURN, 1f, .7f); add(c, ULT_RECALL - 14, SoundEvents.LIGHTNING_BOLT_THUNDER, .7f, 1f);
        add(c, ULT_RECALL, SoundEvents.ANVIL_PLACE, .5f, 1.6f); add(c, ULT_RECALL, SoundEvents.LIGHTNING_BOLT_IMPACT, .8f, 1.3f);
        add(c, ULT_CRASH, SoundEvents.GENERIC_EXPLODE, 1f, .6f); add(c, ULT_CRASH, SoundEvents.ANVIL_LAND, 1f, .4f);
        add(c, ULT_CRASH + 1, SoundEvents.LIGHTNING_BOLT_IMPACT, .8f, .8f);
        add(c, ULT_LANDED, SoundEvents.STONE_STEP, 1f, .6f); add(c, ULT_LANDED + 1, SoundEvents.ARMOR_EQUIP_IRON, .5f, .8f);
        add(c, ULT_LANDED + 10, SoundEvents.LIGHTNING_BOLT_THUNDER, .4f, .6f);
        return c.toArray(new Cue[0]);
    }
    private static void add(List<Cue> list, float t, SoundEvent sound, float volume, float pitch) { list.add(new Cue(t, sound, volume, pitch)); }

    /** The lightning whitens the whole picture, holds, and clears. The storm itself is real cloud, not a filter. */
    @Override public int grade(float t) {
        float white = t < ULT_BLAST ? ease((t - ULT_WHITE) / (ULT_BLAST - ULT_WHITE)) * .85f : t < ULT_FADE ? 1 : 1 - ease((t - ULT_FADE) / 16f);
        if (white <= .01f) return 0;
        return ((int) (Math.min(1, white) * 255) << 24) | 0xf4f8ff;
    }
    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        var font = Minecraft.getInstance().font;
        float title = window(t, ULT_LANDED - 6, ULT_TOTAL - 4, 4);
        if (title <= 0) return;
        String text = "GOD OF THUNDER";
        float slam = 1 + .25f * (1 - ease((t - ULT_LANDED + 6) / 5f));
        g.pose().pushPose();
        g.pose().translate(w / 2f, h * .24f, 0);
        g.pose().scale(3.0f * slam, 3.0f * slam, 1);
        g.drawString(font, text, -font.width(text) / 2, -4, HudStyle.alpha(0xFFBFE0FF, title), false);
        g.pose().popPose();
        String sub = "Aerial Punishment";
        int sw = HudStyle.captionWidth(font, sub), y = (int) (h * .24f) + 20;
        HudStyle.caption(g, font, sub, w / 2, y, HudStyle.alpha(HudStyle.TEXT, title * .85f), 0);
        g.fill(w / 2 - sw / 2 - 34, y + 3, w / 2 - sw / 2 - 8, y + 4, HudStyle.alpha(0xFF8CC8FF, title));
        g.fill(w / 2 + sw / 2 + 8, y + 3, w / 2 + sw / 2 + 34, y + 4, HudStyle.alpha(0xFF8CC8FF, title));
    }
}
