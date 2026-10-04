package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.magneto.MagneticPath;
import com.FIRNI.superheromod.client.render.magneto.MagneticStage;
import com.FIRNI.superheromod.heroes.magneto.MagnetoUltSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.*;

/**
 * MAGNETIC EXECUTION (Magneto's X, 20.6 s), on its own stage (MagneticStage: a dead world under a crimson storm), timed
 * and filmed by MagneticPath. Magneto stands calm among the circling metal; one hand rises and the orbits race; the
 * metal flies at the target, tears its limbs open into an X and lifts it; he draws an X with his hands and two giant
 * pillars drive down out of the storm into an X behind it; it is pinned there; he turns his back and walks toward
 * the camera; a fist, a flick of the hand as if throwing away rubbish, and the pillars fold round the target and crush
 * it into a ball that is thrown far into the storm. He never looks back. This class is the frame: the sound (rain,
 * wind, the hum of the metal, thunder after each flash, every blow of iron), the grade (out of black, the flashes,
 * into black) and the title.
 */
final class MagneticExecutionFilm implements Film {
    static final String ID = MagnetoUltSession.ID;
    private final int attacker;
    private final Segment[] segments;

    MagneticExecutionFilm(int attacker) {
        this.attacker = attacker;
        Scene waste = new Scene() {
            public int skyTop() { return 0x0c0102; }
            public int skyBottom() { return 0x5a0a06; }
            public FilmBackdrop.Params backdrop(float local, float t) { return MagneticStage.backdrop(t); }
            public void render(FilmContext c) {
                var level = Minecraft.getInstance().level;
                var s = state();
                MagneticStage.render(c, level == null || s == null ? null : level.getEntity(s.target), c.time());
            }
        };
        // The stage's camera comes from view(); these shots are only the engine's bookkeeping.
        FilmShot stage = FilmShot.of(0, ULT_STAGE_END).path(new Vec3(0, 1.6, -4)).look(new Vec3(0, 1.6, 1)).fov(50, 50).build();
        FilmShot world = FilmShot.of(ULT_STAGE_END, ULT_TOTAL).path(new Vec3(1.1, 2.2, -3.2), new Vec3(.9, 2.0, -2.8)).look(new Vec3(0, 1.5, 2), new Vec3(0, 1.55, 2)).fov(56, 62).build();
        segments = new Segment[]{new Segment(0, ULT_STAGE_END, new FilmShot[]{stage}, waste, 0, 0),
                new Segment(ULT_STAGE_END, ULT_TOTAL, new FilmShot[]{world}, null, 0x000000, 6)};
        Loop.start(this);
    }
    private FilmSessionClient.State state() { return FilmSessionClient.get(attacker); }
    @Override public Vec3 origin() { var s = state(); return s == null ? Vec3.ZERO : s.anchor; }
    @Override public Vec3 forward() { var s = state(); return s == null ? new Vec3(0, 0, 1) : FilmSessionClient.forward(s); }
    @Override public float duration() { return ULT_TOTAL; }
    @Override public Segment[] segments() { return segments; }
    @Override public float time(float partial) { var s = state(); return s == null ? duration() : FilmSessionClient.time(s, partial); }
    @Override public boolean active() { var s = state(); return s != null && ID.equals(s.film); }
    @Override public View view(float t) { return t < ULT_STAGE_END ? MagneticPath.view(t) : null; }
    @Override public float[] kick(float t) { return t < ULT_STAGE_END ? MagneticPath.kick(t) : null; }
    @Override public float blendOut() { return 12; }

    // ------------------------------------------------------------------ sound
    @Override public Cue[] cues() {
        List<Cue> c = new ArrayList<>();
        // Thunder after every flash: close ones crack at once, far ones roll in late and low.
        for (MagneticPath.Strike s : MagneticPath.STRIKES) {
            double far = s.at().horizontalDistance();
            float delay = s.near() ? 0 : (float) Math.min(26, far / 6);
            if (s.near()) add(c, s.time(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, .8f);
            add(c, s.time() + delay, SoundEvents.LIGHTNING_BOLT_THUNDER, (float) Math.max(.25, Math.min(1, s.power() * 1.6 - far / 200)), .65f + .25f * (s.seed() % 3) / 2f);
        }
        // The stirring hand: the metal answering, a low ring.
        add(c, 24, SoundEvents.CHAIN_STEP, .4f, .6f);
        add(c, 33, SoundEvents.AMETHYST_BLOCK_CHIME, .25f, .5f);
        add(c, 44, SoundEvents.CHAIN_STEP, .4f, .55f);
        // The raise: the orbits race; the metal sings.
        add(c, ULT_RAISE, SoundEvents.BEACON_ACTIVATE, .6f, .55f); add(c, ULT_RAISE, ModSounds.MAGNETO_MAGNETIC_HUM.get(), (.6f) * 1.3f, 0.8f);
        add(c, ULT_RAISE + 10, SoundEvents.WARDEN_SONIC_CHARGE, .35f, .6f);
        add(c, ULT_RAISE + 24, SoundEvents.ELYTRA_FLYING, .4f, 1.4f);
        // The launch: the metal tearing loose and flying; each clamp biting.
        add(c, ULT_LAUNCH, SoundEvents.TRIDENT_RIPTIDE_3, .9f, .7f); add(c, ULT_LAUNCH, ModSounds.MAGNETO_FLING.get(), (.9f) * 1.0f, 0.8f);
        add(c, ULT_LAUNCH + 1, SoundEvents.TRIDENT_THROW, .8f, .6f);
        for (int j = 0; j < MagneticPath.LAUNCHED; j += 3) {
            add(c, MagneticPath.arrival(j), SoundEvents.ANVIL_PLACE, .45f, 1.2f + .05f * (j % 4));
            add(c, MagneticPath.arrival(j), SoundEvents.CHAIN_PLACE, .7f, .8f); add(c, MagneticPath.arrival(j), ModSounds.MAGNETO_METAL_SHING.get(), (.7f) * 0.7f, 1.0f);
        }
        add(c, MagneticPath.arrival(0) + 1, SoundEvents.PLAYER_HURT, .8f, .8f);
        // Torn open into the X and lifted: chains drawn tight, the metal groaning.
        add(c, ULT_PULL + 2, SoundEvents.IRON_GOLEM_REPAIR, .8f, .6f); add(c, ULT_PULL + 2, ModSounds.MAGNETO_TELEKINESIS_GRAB.get(), (.8f) * 1.2f, 0.9f);
        add(c, ULT_PULL + 10, SoundEvents.CHAIN_FALL, .7f, .6f);
        add(c, ULT_PULL + 22, SoundEvents.GRINDSTONE_USE, .5f, .5f);
        add(c, ULT_PULL + 30, SoundEvents.PLAYER_HURT, .6f, .7f);
        // The X drawn: two strokes of the hands; the pillars howling down out of the storm.
        add(c, ULT_XCUT + 2, SoundEvents.PLAYER_ATTACK_SWEEP, .8f, .5f); add(c, ULT_XCUT + 2, ModSounds.FX_WHOOSH_HEAVY.get(), (.8f) * 1.0f, 0.8f);
        add(c, ULT_XCUT + 9, SoundEvents.PLAYER_ATTACK_SWEEP, .8f, .45f); add(c, ULT_XCUT + 9, ModSounds.FX_WHOOSH_HEAVY.get(), (.8f) * 1.0f, 0.8f);
        add(c, ULT_XCUT + 3, SoundEvents.ELYTRA_FLYING, .8f, .6f);
        add(c, ULT_XCUT + 6, SoundEvents.WITHER_SHOOT, .4f, .5f); add(c, ULT_XCUT + 6, ModSounds.MAGNETO_ROD_WHISTLE.get(), (.4f) * 2.0f, 0.6f);
        // The slam: iron into the ground, a crack of lightning, the earth shaking.
        add(c, ULT_SLAM, SoundEvents.ANVIL_LAND, 1f, .45f); add(c, ULT_SLAM, ModSounds.MAGNETO_METAL_CLANG_BIG.get(), (1f) * 0.8f, 1.0f);
        add(c, ULT_SLAM, SoundEvents.GENERIC_EXPLODE, 1f, .6f); add(c, ULT_SLAM, ModSounds.FX_IMPACT_HEAVY.get(), (1f) * 1.0f, 0.9f);
        add(c, ULT_SLAM, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1f, .5f);
        add(c, ULT_SLAM + 1, SoundEvents.WARDEN_SONIC_BOOM, .6f, .5f);
        add(c, ULT_SLAM + 2, SoundEvents.GENERIC_EXPLODE, .5f, .45f); add(c, ULT_SLAM + 2, ModSounds.FX_IMPACT_HEAVY.get(), (.5f) * 1.0f, 0.9f);
        add(c, ULT_SLAM + 4, SoundEvents.ANVIL_LAND, .4f, .7f); add(c, ULT_SLAM + 4, ModSounds.MAGNETO_METAL_CLANG_BIG.get(), (.4f) * 0.8f, 1.0f);
        for (int i = 0; i < 6; i++) add(c, ULT_SLAM + 10 + i * 4, i % 2 == 0 ? SoundEvents.CHAIN_FALL : SoundEvents.ANVIL_LAND, .3f - i * .03f, 1.3f + .1f * i);
        // Pinned onto the X.
        add(c, ULT_PIN + 2, SoundEvents.ELYTRA_FLYING, .4f, 1.6f);
        add(c, ULT_PIN + 9, SoundEvents.ANVIL_LAND, .9f, .7f); add(c, ULT_PIN + 9, ModSounds.MAGNETO_METAL_CLANG_BIG.get(), (.9f) * 0.8f, 1.0f);
        add(c, ULT_PIN + 9, SoundEvents.IRON_DOOR_CLOSE, 1f, .5f);
        add(c, ULT_PIN + 10, SoundEvents.PLAYER_HURT, .8f, .6f);
        add(c, ULT_PIN + 12, SoundEvents.CHAIN_PLACE, .8f, .6f); add(c, ULT_PIN + 12, ModSounds.MAGNETO_METAL_SHING.get(), (.8f) * 0.7f, 1.0f);
        // The turn and the walk: the cape, his steps in the water.
        add(c, ULT_TURN + 2, SoundEvents.ARMOR_EQUIP_LEATHER, .5f, .6f);
        float turn = MagneticPath.yaw(ULT_WALK) * 1.6f;
        for (int s = 1; s < 40; s++) {
            float d = (s * (float) Math.PI - turn) / 2.66f;
            float t = stepTime(d);
            if (t < ULT_TURN + 4 || t > ULT_STAGE_END) continue;
            add(c, t, SoundEvents.STONE_STEP, .5f, .7f + .05f * (s % 3));
            add(c, t, SoundEvents.FIRE_EXTINGUISH, .08f, 1.8f);
        }
        // The fist: the hand rising; the metal falling silent, held; the fist closing; the flick.
        add(c, ULT_FIST + 4, SoundEvents.ARMOR_EQUIP_IRON, .5f, .6f);
        add(c, ULT_FLICK - 4, SoundEvents.ARMOR_EQUIP_NETHERITE, .8f, .7f);
        add(c, ULT_FLICK - 2, SoundEvents.CHAIN_STEP, .6f, .5f);
        add(c, ULT_FLICK + 2, SoundEvents.PLAYER_ATTACK_WEAK, .7f, .6f);
        // The crush: the pillars wrenched toward each other, bending, screaming, folding into a ball.
        add(c, ULT_CRUSH, SoundEvents.IRON_GOLEM_HURT, .9f, .4f);
        add(c, ULT_CRUSH + 2, SoundEvents.ANVIL_USE, 1f, .5f); add(c, ULT_CRUSH + 2, ModSounds.MAGNETO_METAL_RISE.get(), (1f) * 1.0f, 0.7f);
        add(c, ULT_CRUSH + 4, SoundEvents.GRINDSTONE_USE, 1f, .6f);
        add(c, ULT_CRUSH + 6, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1f, .45f);
        add(c, ULT_CRUSH + 9, SoundEvents.ANVIL_USE, .9f, .4f); add(c, ULT_CRUSH + 9, ModSounds.MAGNETO_METAL_RISE.get(), (.9f) * 1.0f, 0.7f);
        add(c, ULT_CRUSH + 12, SoundEvents.IRON_GOLEM_HURT, .8f, .35f);
        add(c, ULT_CRUSH + 16, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, .9f, .4f);
        add(c, ULT_CRUSH + 20, SoundEvents.ANVIL_DESTROY, 1f, .5f); add(c, ULT_CRUSH + 20, ModSounds.MAGNETO_SHIELD_BURST.get(), (1f) * 0.8f, 0.7f);
        add(c, ULT_BALL, SoundEvents.ANVIL_LAND, 1f, .4f); add(c, ULT_BALL, ModSounds.MAGNETO_METAL_CLANG_BIG.get(), (1f) * 0.8f, 1.0f);
        add(c, ULT_BALL, SoundEvents.GENERIC_EXPLODE, .5f, 1.2f); add(c, ULT_BALL, ModSounds.FX_IMPACT_HEAVY.get(), (.5f) * 1.0f, 0.9f);
        // Thrown into the storm; far away, it comes down.
        add(c, ULT_HURL, SoundEvents.TRIDENT_RIPTIDE_2, 1f, .5f); add(c, ULT_HURL, ModSounds.MAGNETO_FLING.get(), (1f) * 1.0f, 0.7f);
        add(c, ULT_HURL, SoundEvents.WARDEN_SONIC_BOOM, .4f, .7f);
        add(c, ULT_HURL + 2, SoundEvents.ELYTRA_FLYING, .6f, 1.2f);
        add(c, ULT_HURL + MagneticPath.HURL_TIME + 6, SoundEvents.GENERIC_EXPLODE, .5f, .45f); add(c, ULT_HURL + MagneticPath.HURL_TIME + 6, ModSounds.FX_IMPACT_HEAVY.get(), (.5f) * 1.0f, 0.9f);
        return c.toArray(new Cue[0]);
    }
    /** When he has walked d blocks (inverse of MagneticPath.walked; during the turn, spread through it). */
    private static float stepTime(float d) {
        if (d <= 0) return ULT_TURN + (ULT_WALK - ULT_TURN) * (1 + d / 2f);
        float v = MagneticPath.WALK_SPEED, ramp = v * 12 * 12 / 24;
        return d < ramp ? ULT_WALK + (float) Math.sqrt(d * 24 / v) : ULT_WALK + d / v + 6;
    }
    private static void add(List<Cue> list, float t, SoundEvent sound, float volume, float pitch) { list.add(new Cue(t, sound, volume, pitch)); }

    /** The sound under everything, looping: rain, the wind, the hum of the metal (rising with the orbits), the hiss of hot iron. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final MagneticExecutionFilm film;
        private final int kind;
        private Loop(SoundEvent sound, MagneticExecutionFilm film, int kind) {
            super(sound, SoundSource.MASTER, RandomSource.create());
            this.film = film; this.kind = kind;
            looping = true; delay = 0; relative = true; attenuation = Attenuation.NONE; volume = .01f; pitch = 1;
        }
        static void start(MagneticExecutionFilm film) {
            var sounds = Minecraft.getInstance().getSoundManager();
            sounds.play(new Loop(SoundEvents.WEATHER_RAIN, film, 0));
            sounds.play(new Loop(SoundEvents.ELYTRA_FLYING, film, 1));
            sounds.play(new Loop(SoundEvents.BEACON_AMBIENT, film, 2));
            sounds.play(new Loop(SoundEvents.FIRE_AMBIENT, film, 3));
        }
        @Override public void tick() {
            if (!film.active() || film.time(0) >= ULT_STAGE_END + 4) { stop(); return; }
            float t = film.time(0);
            float in = Math.min(1, t / 14), out = 1 - MagneticPath.clamp((t - (ULT_STAGE_END - 10)) / 12);
            float v, p;
            switch (kind) {
                case 0 -> { v = .55f; p = 1; }
                case 1 -> { v = .18f + .1f * (float) Math.sin(t * .05); p = .45f; }
                case 2 -> {
                    float race = MagneticPath.window(t, ULT_RAISE, ULT_LAUNCH + 20, 12), held = MagneticPath.window(t, ULT_FIST + 8, ULT_FLICK, 4);
                    v = .25f + .6f * race + .4f * held; p = .5f + .5f * race + .3f * held;
                }
                default -> { v = .4f * MagneticPath.heat(t); p = .6f; }
            }
            volume = Math.max(.001f, v * in * out);
            pitch = p;
        }
    }

    // ------------------------------------------------------------------ picture
    @Override public int grade(float t) {
        float black = 1 - MagneticPath.clamp(t / 16);
        black = Math.max(black, MagneticPath.clamp((t - (ULT_STAGE_END - 10)) / 10));
        if (black > .01f && t < ULT_STAGE_END + 1) return (int) (Math.min(1, black) * 255) << 24;
        if (t >= ULT_STAGE_END) return 0;
        // A flash washes the frame; the slam hits hardest.
        float flash = MagneticPath.flash(t);
        float slam = t - ULT_SLAM;
        float white = Math.max(flash > .9f ? (flash - .9f) * .6f : 0, slam >= 0 && slam < 3 ? .55f * (1 - slam / 3) : 0);
        if (white > .01f) return ((int) (Math.min(.7f, white) * 255) << 24) | 0xfff0f2;
        // Otherwise a faint blood-red cast over everything.
        return 0x18300004;
    }

    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        if (t >= ULT_STAGE_END) return;
        vignette(g, w, h, .34f + .1f * MagneticPath.clamp((t - ULT_WALK) / 60));
        // The title as he walks out of the last image.
        float title = MagneticPath.window(t, 362, ULT_STAGE_END - 6, 8);
        if (title > .01f) {
            var font = Minecraft.getInstance().font;
            String text = "MANYETİK İNFAZ";
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * .83f, 0);
            g.pose().scale(2f, 2f, 1);
            g.drawString(font, text, -font.width(text) / 2, -4, HudStyle.alpha(0xFFE8D2CC, title), false);
            g.pose().popPose();
            int y = (int) (h * .83f) + 12, half = font.width(text) + 8;
            g.fill(w / 2 - half, y, w / 2 + half, y + 1, HudStyle.alpha(0xFFB01818, title * .85f));
        }
    }
    private static void vignette(GuiGraphics g, int w, int h, float strength) {
        int edge = Math.max(24, h / 4);
        for (int i = 0; i < edge; i += 2) {
            float a = strength * (float) Math.pow(1 - i / (float) edge, 1.8);
            int col = HudStyle.alpha(0xFF080102, a);
            g.fill(0, i, w, i + 2, col); g.fill(0, h - i - 2, w, h - i, col);
            g.fill(i, 0, i + 2, h, col); g.fill(w - i - 2, 0, w - i, h, col);
        }
    }
}
