package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.thor.ThorMotion;
import com.FIRNI.superheromod.heroes.thor.GodOfThunderSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.heroes.thor.ThorAction.*;
import static com.FIRNI.superheromod.client.render.film.FilmFx.*;

/**
 * GOD OF THUNDER, the film (16.5 s), shot in the real world around the two of them. Thor stands at
 * the stage origin facing +z, the target at distance D in front of him. Thor's own body is
 * animated by ThorMotion from the same clock; the storm, the bolts and the cracks come from ThorFx.
 *   0-40     quiet: behind the target, Thor far off and still, the sky darkening
 *   40-80    his face: the first spark crosses his eye, then both eyes light
 *   80-120   Mjolnir in his hand, then raised to the sky
 *   120-160  the sky wheels; a giant bolt strikes him and he takes it in
 *   160-200  wrapped in lightning, the cry, the thunder
 *   200-240  the target, the electricity gathering round them
 *   240-280  the whirl, the leap, the flight over them
 *   280-300  above them, both hands up; a second of silence; down
 *   300-330  the impact, the cracked ground, the hammer on his shoulder
 */
final class GodOfThunderFilm implements Film {
    static final String ID = GodOfThunderSession.ID;
    private final int attacker;
    private final double distance, land;
    private final Segment[] segments;

    GodOfThunderFilm(int attacker) {
        this.attacker = attacker;
        var s = FilmSessionClient.get(attacker);
        var level = Minecraft.getInstance().level;
        double d = 6;
        if (s != null && level != null && level.getEntity(s.target) != null) {
            Vec3 to = level.getEntity(s.target).position().subtract(s.anchor);
            d = Math.sqrt(to.x * to.x + to.z * to.z);
        }
        distance = Math.max(2.5, Math.min(18, d));
        land = Math.max(0, distance - GodOfThunderSession.LAND_SHORT);
        double D = distance, L = land;
        Vec3 fly1 = body(ULT_LAUNCH + 8), fly2 = body(ULT_LAUNCH + 18), fly3 = body(ULT_HOVER - 2);
        segments = new Segment[]{new Segment(0, ULT_TOTAL, new FilmShot[]{
                // Quiet: over the target's shoulder, Thor small and still in the distance.
                FilmShot.of(0, ULT_CLOSE).path(v(.85, 2.0, D + 2.6), v(.75, 1.95, D + 2.2)).look(v(0, 1.3, 0), v(0, 1.4, 0)).fov(48, 44).shake(.02f, .02f).build(),
                // His face; the spark, then the eyes.
                FilmShot.of(ULT_CLOSE, ULT_HAMMER).path(v(.45, 1.7, 2.6), v(.25, 1.66, 1.3), v(.14, 1.64, .85)).look(v(0, 1.62, 0)).fov(42, 28).roll(0, 3).shake(.02f, .05f).build(),
                // Mjolnir in his hand...
                FilmShot.of(ULT_HAMMER, ULT_RAISE).path(v(1.25, .95, 1.1), v(1.05, .85, .95)).look(v(.45, .62, .05), v(.45, .66, .05)).fov(36, 32).shake(.02f, .03f).build(),
                // ...going up to the sky; the camera rises with it.
                FilmShot.of(ULT_RAISE, ULT_SKY).path(v(1.5, .7, 1.7), v(1.3, .55, 1.55)).look(v(.3, 1.0, 0), v(.15, 3.2, 0)).fov(52, 58).shake(.03f, .08f).build(),
                // The sky turning; the strike.
                FilmShot.of(ULT_SKY, ULT_STRIKE - 4).path(v(.9, .35, 1.8), v(.8, .3, 1.7)).look(v(0, 14, 3), v(0, 26, 1)).fov(72, 80).roll(-4, 4).shake(.15f, .35f).build(),
                FilmShot.of(ULT_STRIKE - 4, ULT_POWER).path(v(3.2, 1.6, 3.4), v(3.4, 1.8, 3.8)).look(v(0, 2.6, 0), v(0, 1.8, 0)).fov(70, 62).shake(.9f, .3f).whip().build(),
                // Power: low and in front, the cry, the thunder.
                FilmShot.of(ULT_POWER, ULT_TARGET).path(v(.55, .45, 2.8), v(.45, .5, 2.3)).look(v(0, 1.55, 0), v(0, 1.75, 0)).fov(56, 50).roll(3, -2).shake(.2f, .45f).build(),
                // The target: electricity gathering round them, Thor behind.
                FilmShot.of(ULT_TARGET, ULT_TARGET + 24).path(v(1.7, 1.55, D - 1.5), v(1.5, 1.6, D - 1.2)).look(v(0, 1.5, D), v(-.2, 1.5, D)).fov(50, 46).shake(.05f, .08f).build(),
                FilmShot.of(ULT_TARGET + 24, ULT_FLIGHT).path(v(3.6, 2.4, D + .5), v(4.2, 2.8, D * .5)).look(v(0, 1.3, D * .7), v(0, 1.6, D * .5)).fov(60, 64).shake(.06f, .1f).build(),
                // The whirl and the leap, from behind him.
                FilmShot.of(ULT_FLIGHT, ULT_LAUNCH).path(v(1.0, 1.8, -2.6), v(.9, 1.6, -2.2)).look(v(0, 1.8, 1), v(0, 2.2, 1)).fov(58, 64).shake(.1f, .25f).build(),
                FilmShot.of(ULT_LAUNCH, ULT_HOVER).path(v(1.0, 1.7, -2.6), fly1.add(1.1, .9, -3.4), fly2.add(1.0, 1.0, -3.6), fly3.add(.9, .8, -3.4))
                        .look(v(0, 2.0, 1), fly1.add(0, 1.2, 0), fly2.add(0, 1.2, 0), fly3.add(0, 1.0, 0)).fov(70, 78).roll(-6, 4).shake(.3f, .2f).build(),
                // Above them: from below, both hands up; a breath; down.
                FilmShot.of(ULT_HOVER, ULT_PLUNGE).path(v(1.5, .55, L + 1.4), v(1.35, .5, L + 1.25)).look(v(0, 9.6, L), v(0, 9.7, L)).fov(62, 56).roll(4, 6).shake(.04f, .04f).build(),
                FilmShot.of(ULT_PLUNGE, ULT_IMPACT).path(v(3.0, 1.3, L + 3.2), v(2.8, 1.2, L + 3.0)).look(v(0, 4, L), v(0, .8, L)).fov(74, 80).shake(.4f, 1f).whip().build(),
                // The aftermath.
                FilmShot.of(ULT_IMPACT, ULT_TOTAL).path(v(4.4, 3.4, L - 3.4), v(3.8, 2.7, L - 2.6), v(3.2, 2.1, L - 1.9)).look(v(0, 1.0, L), v(0, 1.3, L)).fov(66, 56).shake(.8f, .05f).build()},
                null, 0, 0)};
    }
    /** Where the film shows Thor's body at time t (stage space). */
    private Vec3 body(float t) {
        double[] o = ThorMotion.ultimateOffset(t, land);
        return v(o[0], o[1], o[2]);
    }
    private static Vec3 v(double x, double y, double z) { return new Vec3(x, y, z); }
    private FilmSessionClient.State state() { return FilmSessionClient.get(attacker); }
    @Override public Vec3 origin() { var s = state(); return s == null ? Vec3.ZERO : s.anchor; }
    @Override public Vec3 forward() { var s = state(); return s == null ? new Vec3(0, 0, 1) : FilmSessionClient.forward(s); }
    @Override public float duration() { return ULT_TOTAL; }
    @Override public Segment[] segments() { return segments; }
    @Override public float time(float partial) { var s = state(); return s == null ? duration() : FilmSessionClient.time(s, partial); }
    @Override public boolean active() { var s = state(); return s != null && ID.equals(s.film); }
    @Override public float[] impacts() { return new float[]{ULT_STRIKE, ULT_BOOM, ULT_IMPACT}; }
    @Override public float blendOut() { return 14; }

    @Override public Cue[] cues() {
        List<Cue> c = new ArrayList<>();
        add(c, 3, SoundEvents.WEATHER_RAIN, .35f, .7f); add(c, 28, SoundEvents.LIGHTNING_BOLT_THUNDER, .35f, .55f);
        add(c, 44, SoundEvents.WEATHER_RAIN, .4f, .6f);
        add(c, ULT_EYE_SPARK, SoundEvents.AMETHYST_BLOCK_HIT, .9f, 2f); add(c, ULT_EYE_SPARK, SoundEvents.LIGHTNING_BOLT_IMPACT, .25f, 2f);
        add(c, ULT_EYES, SoundEvents.BEACON_POWER_SELECT, .7f, 1.6f);
        add(c, ULT_HAMMER + 4, SoundEvents.BEACON_AMBIENT, .7f, 1.8f);
        add(c, ULT_RAISE, SoundEvents.TRIDENT_RIPTIDE_3, .8f, .8f);
        add(c, ULT_SKY, SoundEvents.LIGHTNING_BOLT_THUNDER, .7f, .7f); add(c, ULT_SKY + 6, SoundEvents.ELYTRA_FLYING, .45f, .6f);
        add(c, ULT_STRIKE, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 1f); add(c, ULT_STRIKE, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, .8f);
        add(c, ULT_STRIKE + 1, SoundEvents.GENERIC_EXPLODE, .6f, 1.2f);
        add(c, ULT_POWER, SoundEvents.BEACON_ACTIVATE, .8f, .6f);
        add(c, ULT_SHOUT, SoundEvents.RAVAGER_ROAR, 1f, .7f);
        add(c, ULT_BOOM, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, .6f); add(c, ULT_BOOM, SoundEvents.GENERIC_EXPLODE, .8f, .6f);
        add(c, ULT_TARGET + 6, SoundEvents.BEACON_AMBIENT, .6f, 1.4f);
        add(c, ULT_FLIGHT, SoundEvents.TRIDENT_RIPTIDE_1, .8f, 1.2f);
        add(c, ULT_LAUNCH, SoundEvents.TRIDENT_RIPTIDE_3, 1f, 1f); add(c, ULT_LAUNCH + 2, SoundEvents.ELYTRA_FLYING, .6f, 1f);
        // A second of silence while he hangs above them; then everything at once.
        add(c, ULT_PLUNGE, SoundEvents.TRIDENT_THUNDER, 1f, .8f);
        add(c, ULT_IMPACT, SoundEvents.GENERIC_EXPLODE, 1f, .5f); add(c, ULT_IMPACT, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, .5f);
        add(c, ULT_IMPACT + 1, SoundEvents.ANVIL_LAND, .8f, .5f);
        add(c, ULT_IMPACT + 12, SoundEvents.LIGHTNING_BOLT_IMPACT, .5f, 1.5f);
        add(c, ULT_IMPACT + 22, SoundEvents.ARMOR_EQUIP_IRON, .7f, .8f);
        return c.toArray(new Cue[0]);
    }
    private static void add(List<Cue> list, float t, SoundEvent sound, float volume, float pitch) { list.add(new Cue(t, sound, volume, pitch)); }

    /** The world darkens under the storm; blue-white when the bolt hits; it clears after the impact. */
    @Override public int grade(float t) {
        float dark = ease(t / 40f) * .35f + ease((t - ULT_SKY) / 30f) * .2f;
        dark *= 1 - ease((t - ULT_IMPACT - 6) / 24f);
        if (dark <= 0) return 0;
        return ((int) (dark * 255) << 24) | 0x0a1224;
    }
    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        var font = Minecraft.getInstance().font;
        float title = window(t, ULT_BOOM - 2, ULT_TARGET + 18, 3);
        if (title <= 0) return;
        String text = "GOD OF THUNDER";
        float slam = 1 + .3f * (1 - ease((t - ULT_BOOM + 2) / 4f));
        g.pose().pushPose();
        g.pose().translate(w / 2f, h * .28f, 0);
        g.pose().scale(3.2f * slam, 3.2f * slam, 1);
        g.drawString(font, text, -font.width(text) / 2, -4, HudStyle.alpha(0xFFBFE0FF, title), false);
        g.pose().popPose();
        String sub = "Thor Odinson  ·  Asgard";
        int sw = HudStyle.captionWidth(font, sub), y = (int) (h * .28f) + 21;
        HudStyle.caption(g, font, sub, w / 2, y, HudStyle.alpha(HudStyle.TEXT, title * .85f), 0);
        g.fill(w / 2 - sw / 2 - 34, y + 3, w / 2 - sw / 2 - 8, y + 4, HudStyle.alpha(0xFF8CC8FF, title));
        g.fill(w / 2 + sw / 2 + 8, y + 3, w / 2 + sw / 2 + 34, y + 4, HudStyle.alpha(0xFF8CC8FF, title));
    }
}
