package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.batman.KnightPath;
import com.FIRNI.superheromod.client.render.batman.KnightStage;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.batman.BatmanUltBeats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.heroes.batman.BatmanUltBeats.*;

/**
 * KARA ŞÖVALYE (Batman's X, 29 s), on its own stage (KnightStage: a dead yard at night in the rain, the city far below
 * when it goes up), timed and filmed by KnightPath. The fear under the lamp (four glimpses of him and a shape over the
 * roofs), the grapnel out of the dark, the sticky bomb and the two-foot kick, the shock blast that throws them both high
 * over the city, the fall from the top with the Batwing called in to rake them with its guns, the Batarang thrown without looking that pins them to
 * the wall, his exit up the grapnel, and the still yard. This class is the frame: the sound (rain, wind, the lamp's hum,
 * the Batwing's engines, every beat; vanilla and the mod's own synthesised sounds, nothing recorded), the grade (out of
 * black, the cold flashes, into black) and the title.
 */
final class DarkKnightFilm implements Film {
    static final String ID = BatmanUltBeats.ID;
    private final int attacker;
    private final Segment[] segments;

    DarkKnightFilm(int attacker) {
        this.attacker = attacker;
        Scene yard = new Scene() {
            public int skyTop() { return 0x020306; }
            public int skyBottom() { return 0x0e0b10; }
            public FilmBackdrop.Params backdrop(float local, float t) { return KnightStage.backdrop(t); }
            public void render(FilmContext c) {
                var level = Minecraft.getInstance().level;
                var s = state();
                KnightStage.render(c, level == null || s == null ? null : level.getEntity(s.target), c.time());
            }
        };
        // The stage's camera comes from view(); these shots are only the engine's bookkeeping.
        FilmShot stage = FilmShot.of(0, STAGE_END).path(new Vec3(0, 1.6, -4)).look(new Vec3(0, 1.6, 1)).fov(50, 50).build();
        FilmShot world = FilmShot.of(STAGE_END, TOTAL).path(new Vec3(1.1, 2.2, -3.2), new Vec3(.9, 2.0, -2.8)).look(new Vec3(0, 1.5, 2), new Vec3(0, 1.55, 2)).fov(56, 62).build();
        segments = new Segment[]{new Segment(0, STAGE_END, new FilmShot[]{stage}, yard, 0, 0),
                new Segment(STAGE_END, TOTAL, new FilmShot[]{world}, null, 0x000000, 6)};
        Loop.start(this);
    }
    private FilmSessionClient.State state() { return FilmSessionClient.get(attacker); }
    @Override public Vec3 origin() { var s = state(); return s == null ? Vec3.ZERO : s.anchor; }
    @Override public Vec3 forward() { var s = state(); return s == null ? new Vec3(0, 0, 1) : FilmSessionClient.forward(s); }
    @Override public float duration() { return TOTAL; }
    @Override public Segment[] segments() { return segments; }
    @Override public float time(float partial) { var s = state(); return s == null ? duration() : FilmSessionClient.time(s, partial); }
    @Override public boolean active() { var s = state(); return s != null && ID.equals(s.film); }
    @Override public View view(float t) { return t < STAGE_END ? KnightPath.view(t) : null; }
    @Override public float[] kick(float t) { return t < STAGE_END ? KnightPath.kick(t) : null; }
    @Override public float blendOut() { return 12; }

    // ------------------------------------------------------------------ sound
    @Override public Cue[] cues() {
        List<Cue> c = new ArrayList<>();
        // Far thunder after the flashes in the cloud.
        for (float at : new float[]{22, 96, 148, 268, 358, 498}) add(c, at + 14, SoundEvents.LIGHTNING_BOLT_THUNDER, .3f, .55f);
        // The fear: a sound from each side turns their head; he is there; a rush of cloth and he is gone. A heartbeat under it all.
        add(c, G1 - 5, SoundEvents.CHAIN_STEP, .5f, .7f);
        add(c, G2 - 5, SoundEvents.GRAVEL_STEP, .6f, .6f);
        add(c, G3 - 5, SoundEvents.IRON_DOOR_OPEN, .35f, .5f);
        add(c, G4 - 4, SoundEvents.STONE_STEP, .5f, .5f);
        for (int g : new int[]{G1, G2, G3, G4}) {
            add(c, g + SEEN, SoundEvents.BELL_RESONATE, .22f, .5f);
            add(c, g + SEEN, ModSounds.BATMAN_CAPE.get(), .45f, .8f);
        }
        // How each one ends: melting into the dark (a low breath of air), smoke (a pellet's pop and hiss), a blink (a thump).
        add(c, G1 + GONE, ModSounds.FX_WHOOSH_LIGHT.get(), .4f, .5f);
        add(c, G1 + GONE + 2, SoundEvents.SOUL_ESCAPE, .5f, .6f);
        for (int g : new int[]{G2, G4}) {
            add(c, g + GONE, ModSounds.BATMAN_SMOKE.get(), .9f, 1.1f);
            add(c, g + GONE, SoundEvents.FIRE_EXTINGUISH, .5f, .7f);
        }
        add(c, G3 + GONE, SoundEvents.WARDEN_HEARTBEAT, .8f, .7f);
        add(c, PASS + 2, SoundEvents.PHANTOM_FLAP, .6f, .45f);
        add(c, PASS + 6, ModSounds.FX_WHOOSH_HEAVY.get(), .5f, .7f);
        add(c, PASS + 9, SoundEvents.PHANTOM_FLAP, .5f, .5f);
        float beat = 30;
        for (float at = 44; at < FIRE; at += beat) {
            add(c, at, SoundEvents.WARDEN_HEARTBEAT, .5f + .3f * at / FIRE, .9f);
            beat = Math.max(7, beat * .86f);
        }
        add(c, G4 + SEEN, SoundEvents.WARDEN_NEARBY_CLOSE, .3f, .6f);
        add(c, BACK + 2, SoundEvents.GRAVEL_STEP, .4f, .8f);
        add(c, BACK + 8, SoundEvents.GRAVEL_STEP, .4f, .75f);
        add(c, BACK + 14, SoundEvents.GRAVEL_STEP, .4f, .8f);
        // The grapnel out of the dark: the shot, the bite, the yank.
        add(c, FIRE, ModSounds.BATMAN_GRAPNEL.get(), .8f, .9f);
        add(c, FIRE, SoundEvents.CROSSBOW_SHOOT, .6f, .7f);
        add(c, BITE, SoundEvents.TRIDENT_HIT, .8f, .8f);
        add(c, BITE, SoundEvents.CHAIN_PLACE, .6f, .7f);
        add(c, BITE + 1, SoundEvents.PLAYER_HURT, .6f, .8f);
        add(c, YANK, ModSounds.FX_WHOOSH_HEAVY.get(), 1f, .7f);
        add(c, YANK, SoundEvents.CROSSBOW_LOADING_END, .7f, .6f);
        add(c, LEAP, ModSounds.BATMAN_CAPE.get(), .9f, .9f);
        add(c, LEAP + 2, SoundEvents.PHANTOM_FLAP, .6f, .6f);
        // The bomb: stuck on, beeping faster, the kick, the blast (a cold electric shock, not fire).
        add(c, PLANT, SoundEvents.SLIME_BLOCK_PLACE, .9f, .8f);
        add(c, PLANT, SoundEvents.LEVER_CLICK, .6f, 1.4f);
        float gap = 7;
        for (float at = PLANT + 3; at < BLAST - .5f; at += gap) {
            add(c, at, ModSounds.BATMAN_SONIC_BEEP.get(), .55f, 1.3f);
            gap = Math.max(1.4f, gap * .62f);
        }
        add(c, KICK, ModSounds.BATMAN_PUNCH.get(), 1f, .7f);
        add(c, KICK, SoundEvents.PLAYER_ATTACK_STRONG, 1f, .7f);
        add(c, KICK, ModSounds.FX_IMPACT_HEAVY.get(), .8f, 1f);
        add(c, BLAST, ModSounds.FX_ENERGY_BOOM.get(), 1f, .9f);
        add(c, BLAST, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1f, .6f);
        add(c, BLAST, ModSounds.FX_ELECTRIC_ZAP.get(), .9f, .8f);
        add(c, BLAST + 1, SoundEvents.WARDEN_SONIC_BOOM, .35f, 1.2f);
        add(c, BLAST + 3, ModSounds.FX_ELECTRIC_CRACKLE.get(), .6f, .9f);
        add(c, BLAST + 6, SoundEvents.ELYTRA_FLYING, .5f, 1.3f);
        add(c, 222, ModSounds.BATMAN_CAPE.get(), .8f, .7f);
        // High up: the wind, the cape opening. The call: a click on his forearm, a signal answering.
        add(c, 232, SoundEvents.PHANTOM_FLAP, .5f, .5f);
        add(c, CALL, SoundEvents.STONE_BUTTON_CLICK_ON, .7f, 1.6f);
        add(c, CALL + 1, SoundEvents.BEACON_POWER_SELECT, .35f, 1.7f);
        add(c, SIGNAL, ModSounds.BATMAN_SONIC_LOCK.get(), .5f, 1.2f);
        add(c, SIGNAL + 6, ModSounds.BATMAN_SONIC_BEEP.get(), .4f, 1.5f);
        // The Batwing: far off, then on them with a roar; the scan sweeping; locked; the pass and its pulse.
        add(c, LIGHTS, SoundEvents.BEACON_ACTIVATE, .3f, .5f);
        add(c, ARRIVE - 3, ModSounds.FX_WHOOSH_HEAVY.get(), 1f, .55f);
        add(c, ARRIVE, SoundEvents.WARDEN_SONIC_BOOM, .3f, .7f);
        add(c, ARRIVE, SoundEvents.ELYTRA_FLYING, .9f, 1.6f);
        // The guns: a round every tick, the shots layered every other tick so the rattle stays clean; hits and ricochets.
        for (int at = SCAN; at < PASS_BY; at += 2) {
            if (!KnightPath.fires(at)) continue;
            add(c, at, ModSounds.BATMAN_CANNON_SHOT.get(), .75f, .62f + .06f * (at % 3));
            if (at % 6 == 0) add(c, at + 2, ModSounds.FX_IMPACT_METAL.get(), .35f, 1.5f);
            if (at % 10 == 4) add(c, at + 1, SoundEvents.ARROW_HIT, .4f, .7f);
        }
        add(c, SCAN, SoundEvents.CROSSBOW_LOADING_END, .5f, .5f);
        add(c, SCANNED, ModSounds.BATMAN_CANNON_STOP.get(), .7f, .7f);
        add(c, PASS_BY - 2, ModSounds.FX_WHOOSH_HEAVY.get(), 1f, .8f);
        add(c, PASS_BY, SoundEvents.ELYTRA_FLYING, .8f, 1.7f);
        add(c, DROP, SoundEvents.FIRE_EXTINGUISH, .6f, .6f);
        add(c, DROP + 4, ModSounds.FX_ELECTRIC_CRACKLE.get(), .5f, 1f);
        // The landing in the yard, the Batarang, the throw without looking, the hit, the wall.
        add(c, ROOF, ModSounds.FX_IMPACT_HEAVY.get(), .6f, 1.1f);
        add(c, ROOF, SoundEvents.GENERIC_SPLASH, .5f, 1.1f);
        add(c, ROOF + 1, ModSounds.BATMAN_CAPE.get(), .7f, .8f);
        add(c, RAISE + 1, SoundEvents.ARMOR_EQUIP_LEATHER, .5f, .8f);
        add(c, RAISE + 5, SoundEvents.ARMOR_EQUIP_IRON, .4f, 1.7f);
        add(c, THROW, ModSounds.BATMAN_BATARANG.get(), 1f, .9f);
        add(c, THROW, SoundEvents.PLAYER_ATTACK_SWEEP, .5f, 1.4f);
        add(c, THROW + 6, ModSounds.BATMAN_BATARANG.get(), .7f, 1.15f);
        add(c, THROW + 10, ModSounds.FX_WHOOSH_LIGHT.get(), .6f, 1.3f);
        add(c, HIT, ModSounds.FX_IMPACT_METAL.get(), .9f, 1f);
        add(c, HIT, SoundEvents.PLAYER_HURT, .6f, .8f);
        add(c, WALL, SoundEvents.ANVIL_LAND, .7f, 1.3f);
        add(c, WALL, ModSounds.FX_IMPACT_METAL.get(), 1f, .8f);
        add(c, WALL, ModSounds.FX_IMPACT_HEAVY.get(), .8f, .9f);
        add(c, WALL + 1, SoundEvents.STONE_BREAK, .7f, .8f);
        add(c, WALL + 3, SoundEvents.CHAIN_PLACE, .4f, 1.5f);
        // Gone: the turn, the gun, the shot, the bite far up, the haul; a cable far away; then nothing but the rain going quiet.
        add(c, TURN, ModSounds.BATMAN_CAPE.get(), .7f, .7f);
        add(c, GUN, SoundEvents.ARMOR_EQUIP_IRON, .5f, .7f);
        add(c, GUN + 2, SoundEvents.CROSSBOW_LOADING_END, .5f, .7f);
        add(c, FIRE2, ModSounds.BATMAN_GRAPNEL.get(), .9f, .9f);
        add(c, FIRE2, SoundEvents.CROSSBOW_SHOOT, .6f, .7f);
        add(c, BITE2, SoundEvents.TRIDENT_HIT_GROUND, .35f, .8f);
        add(c, HAUL, ModSounds.FX_WHOOSH_HEAVY.get(), .9f, .8f);
        add(c, HAUL, ModSounds.BATMAN_CAPE.get(), .8f, .9f);
        add(c, CABLE, ModSounds.BATMAN_GRAPNEL.get(), .2f, .6f);
        add(c, CABLE + 1, SoundEvents.CHAIN_STEP, .2f, 1.4f);
        return c.toArray(new Cue[0]);
    }
    private static void add(List<Cue> list, float t, SoundEvent sound, float volume, float pitch) { list.add(new Cue(t, sound, volume, pitch)); }

    /** The sound under everything, looping: rain, wind (high up), the lamp's hum (in the yard), the Batwing's engines. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final DarkKnightFilm film;
        private final int kind;
        private Loop(SoundEvent sound, DarkKnightFilm film, int kind) {
            super(sound, SoundSource.MASTER, RandomSource.create());
            this.film = film; this.kind = kind;
            looping = true; delay = 0; relative = true; attenuation = Attenuation.NONE; volume = .01f; pitch = 1;
        }
        static void start(DarkKnightFilm film) {
            var sounds = Minecraft.getInstance().getSoundManager();
            sounds.play(new Loop(SoundEvents.WEATHER_RAIN, film, 0));
            sounds.play(new Loop(SoundEvents.ELYTRA_FLYING, film, 1));
            sounds.play(new Loop(SoundEvents.BEACON_AMBIENT, film, 2));
            sounds.play(new Loop(ModSounds.BATMAN_CANNON_HUM.get(), film, 3));
        }
        @Override public void tick() {
            if (!film.active() || film.time(0) >= STAGE_END + 4) { stop(); return; }
            float t = film.time(0);
            float in = Math.min(1, t / 12), out = 1 - clamp((t - 500) / 40);
            float high = KnightPath.window(t, 204, DROP + 10, 10);
            float v, p;
            switch (kind) {
                case 0 -> { v = .6f - .25f * high; p = 1; }
                case 1 -> { v = .08f + .45f * high + .3f * KnightPath.window(t, DROP, ROOF, 3); p = .5f + .15f * high; }
                case 2 -> { v = .14f * (1 - high); p = .5f; }
                default -> {
                    Vec3 wing = KnightPath.batwing(t);
                    if (wing == null) { v = 0; p = .5f; break; }
                    double d = wing.distanceTo(KnightPath.view(t).pos());
                    v = (float) Mth.clamp(1.4 / (1 + d * .06), 0, 1) * (t < ARRIVE ? .5f : 1);
                    p = .45f + .25f * (float) Math.exp(-d / 12);
                }
            }
            volume = Math.max(.001f, v * in * out);
            pitch = p;
        }
    }
    private static float clamp(float x) { return Mth.clamp(x, 0, 1); }

    // ------------------------------------------------------------------ picture
    @Override public int grade(float t) {
        float black = 1 - clamp(t / 14);
        black = Math.max(black, clamp((t - 538) / 18));
        if (black > .01f && t < STAGE_END + 1) return (int) (Math.min(1, black) * 255) << 24;
        if (t >= STAGE_END) return 0;
        // The cold flashes wash the frame white-blue.
        float flash = KnightPath.flash(t);
        if (flash > .05f) return ((int) (Math.min(.75f, flash * .8f) * 255) << 24) | 0xe8f2ff;
        // Otherwise a cold, dark night over everything.
        return 0x2a01040c;
    }

    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        if (t >= STAGE_END) return;
        vignette(g, w, h, .42f + .12f * KnightPath.window(t, G1, FIRE, 20));
        // The blink: an eyelid closes over the frame, and when it opens he is gone.
        float[] v = KnightPath.vanish(t);
        if (v != null && (int) v[0] == KnightPath.VANISH_BLINK) {
            float d = v[1];
            float shut = d < 1 ? clamp((d + 1.5f) / 2.5f) : 1 - clamp((d - 1) / 2.8f);
            shut = shut * shut * (3 - 2 * shut);
            int lid = (int) Math.ceil(h * .5f * shut);
            if (lid > 0) { g.fill(0, 0, w, lid, 0xFF000000); g.fill(0, h - lid, w, h, 0xFF000000); }
        }
        // The title over the still yard after he is gone.
        float title = KnightPath.window(t, 502, STAGE_END - 8, 10);
        if (title > .01f) {
            var font = Minecraft.getInstance().font;
            String text = "KARA ŞÖVALYE";
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * .83f, 0);
            g.pose().scale(2f, 2f, 1);
            g.drawString(font, text, -font.width(text) / 2, -4, HudStyle.alpha(0xFFD8DEE8, title), false);
            g.pose().popPose();
            int y = (int) (h * .83f) + 12, half = font.width(text) + 8;
            g.fill(w / 2 - half, y, w / 2 + half, y + 1, HudStyle.alpha(0xFFC9A227, title * .85f));
        }
    }
    private static void vignette(GuiGraphics g, int w, int h, float strength) {
        int edge = Math.max(24, h / 4);
        for (int i = 0; i < edge; i += 2) {
            float a = strength * (float) Math.pow(1 - i / (float) edge, 1.8);
            int col = HudStyle.alpha(0xFF010204, a);
            g.fill(0, i, w, i + 2, col); g.fill(0, h - i - 2, w, h - i, col);
            g.fill(i, 0, i + 2, h, col); g.fill(w - i - 2, 0, w - i, h, col);
        }
    }
}
