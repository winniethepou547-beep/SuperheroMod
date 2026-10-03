package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.panther.PursuitCamera;
import com.FIRNI.superheromod.client.render.panther.PursuitPath;
import com.FIRNI.superheromod.client.render.panther.PursuitStage;
import com.FIRNI.superheromod.heroes.panther.PantherUltSession;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * THE FINAL PURSUIT (Black Panther's X, 38 s): a car chase at night on its own stage (PursuitStage, the city,
 * the car, the gunman), filmed by PursuitCamera, timed by PursuitPath. This class is the film's frame: no world at
 * the start (it opens straight on the eye of his mask), the world for a moment at the end so the camera can hand
 * back gently; the sound (the engine's drone through the cabin, the wind outside, the rain-wet city, the hum of the
 * charge; the shots, the glass, the metal; a breath of silence; the release, slowed with the picture; the crash;
 * the fire); the grade (out of black, the impact frame of the release, the flash of the crash, back to black), a
 * vignette, a few speed lines outside, and the title over the last held image.
 */
final class FinalPursuitFilm implements Film {
    static final String ID = PantherUltSession.ID;
    private final int attacker, seed;
    private final Segment[] segments;

    FinalPursuitFilm(int attacker) {
        this.attacker = attacker;
        var s = FilmSessionClient.get(attacker);
        seed = attacker * 31 + (s == null ? 0 : (int) (s.anchor.x * 7.13 + s.anchor.z * 13.7 + s.anchor.y * 3.1));
        Scene city = new Scene() {
            public int skyTop() { return 0x05070f; }
            public int skyBottom() { return 0x24131f; }
            public FilmBackdrop.Params backdrop(float local, float t) { return PursuitStage.backdrop(t); }
            public void render(FilmContext c) { PursuitStage.render(c, c.time(), seed); }
        };
        // The stage's camera comes from view(); this shot is only the engine's bookkeeping.
        FilmShot stage = FilmShot.of(0, ULT_STAGE_END).path(new Vec3(0, 1.6, 0)).look(new Vec3(0, 1.6, 1)).fov(60, 60).build();
        FilmShot world = FilmShot.of(ULT_STAGE_END, ULT_TOTAL).path(new Vec3(.9, 2.3, -3.4), new Vec3(.6, 2.0, -2.8)).look(new Vec3(0, 1.5, 2), new Vec3(0, 1.55, 2)).fov(58, 64).build();
        segments = new Segment[]{new Segment(0, ULT_STAGE_END, new FilmShot[]{stage}, city, 0, 0),
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
    @Override public View view(float t) { return PursuitCamera.view(t); }
    @Override public float[] kick(float t) { return PursuitCamera.kick(t); }
    @Override public float blendOut() { return 12; }

    // ------------------------------------------------------------------ sound
    @Override public Cue[] cues() {
        List<Cue> c = new ArrayList<>();
        // The gunman: a rustle, the slide racked.
        add(c, ULT_NPC + 2, SoundEvents.ARMOR_EQUIP_LEATHER, .4f, .9f);
        add(c, ULT_NPC + 8, SoundEvents.CROSSBOW_LOADING_END, .7f, 1.6f);
        add(c, ULT_NPC + 9, SoundEvents.LEVER_CLICK, .5f, 1.4f);
        add(c, ULT_NPC + 10, SoundEvents.ARMOR_EQUIP_IRON, .35f, 1.8f);
        add(c, ULT_RAISE, SoundEvents.ARMOR_EQUIP_LEATHER, .4f, 1.2f);
        // The rear window smashed; the roar of the air coming in.
        add(c, ULT_SMASH, SoundEvents.GLASS_BREAK, 1f, .9f);
        add(c, ULT_SMASH, SoundEvents.PLAYER_ATTACK_STRONG, .6f, 1.1f);
        add(c, ULT_SMASH + 2, SoundEvents.GLASS_BREAK, .3f, .8f);
        add(c, ULT_LEAN, SoundEvents.ELYTRA_FLYING, .4f, 1.3f);
        // The shots, loud and close inside the car with their echo; what each hits.
        for (PursuitPath.Shot s : PursuitPath.SHOTS) {
            boolean inside = s.time() >= ULT_ROOF_FIRE;
            add(c, s.time(), SoundEvents.FIREWORK_ROCKET_BLAST, inside ? .8f : 1f, inside ? 1.3f : 1.5f);
            add(c, s.time(), SoundEvents.CROSSBOW_SHOOT, .55f, 1.8f);
            add(c, s.time() + 2, SoundEvents.FIREWORK_ROCKET_BLAST, .3f, 1.05f);
            add(c, s.time() + 4, SoundEvents.CHAIN_STEP, .2f, 2f);
            switch (s.hits()) {
                case 0 -> { add(c, s.time() + .5f, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f); add(c, s.time() + .5f, SoundEvents.BEACON_POWER_SELECT, .3f, 1.9f);
                    add(c, s.time() + .5f, SoundEvents.ANVIL_LAND, .12f, 2f); }
                case 1 -> { add(c, s.time() + .5f, SoundEvents.ANVIL_LAND, .3f, 1.9f); add(c, s.time() + .5f, SoundEvents.CHAIN_HIT, .6f, 1.5f); }
                case 2 -> add(c, s.time() + .5f, SoundEvents.GLASS_BREAK, .5f, 1.6f);
                default -> {
                    if (inside) { add(c, s.time() + .5f, SoundEvents.ANVIL_LAND, .22f, 2f); add(c, s.time() + .5f, SoundEvents.CHAIN_HIT, .45f, 1.7f); }
                    else add(c, s.time() + .5f, SoundEvents.WOOL_HIT, .6f, 1f);
                }
            }
        }
        add(c, ULT_FIRE, SoundEvents.GLASS_BREAK, 1f, 1.2f);
        // Out of the window and up; the face at the glass.
        add(c, ULT_EXIT, SoundEvents.ARMOR_EQUIP_LEATHER, .6f, 1.4f);
        add(c, ULT_EXIT + 2, SoundEvents.PHANTOM_FLAP, .5f, 1.5f);
        add(c, ULT_EXIT + 3, SoundEvents.ELYTRA_FLYING, .45f, 1.2f);
        add(c, ULT_EXIT + 11, SoundEvents.IRON_TRAPDOOR_CLOSE, .45f, .6f);
        add(c, ULT_REVEAL + 1, SoundEvents.WARDEN_SONIC_CHARGE, .15f, 1.6f);
        add(c, ULT_REVEAL + 10, SoundEvents.AMETHYST_BLOCK_CHIME, .3f, .7f);
        // Back onto the car behind; the leap across.
        add(c, ULT_HOP, SoundEvents.PHANTOM_FLAP, .5f, 1.4f);
        add(c, ULT_HOP + 10, SoundEvents.ANVIL_LAND, .35f, .7f);
        add(c, ULT_HOP + 10, SoundEvents.GENERIC_BIG_FALL, .45f, .9f);
        add(c, ULT_LEAP, SoundEvents.PHANTOM_FLAP, .7f, 1.2f);
        add(c, ULT_LEAP, SoundEvents.PLAYER_ATTACK_SWEEP, .45f, .7f);
        add(c, ULT_LEAP + 1, SoundEvents.ELYTRA_FLYING, .6f, 1.6f);
        // Down on the roof: the blow through the metal, the claws scraping till they catch.
        add(c, ULT_TOUCH, SoundEvents.ANVIL_LAND, .8f, .65f);
        add(c, ULT_TOUCH, SoundEvents.GENERIC_BIG_FALL, .7f, .8f);
        add(c, ULT_TOUCH + 1, SoundEvents.GRINDSTONE_USE, .6f, 1.6f);
        // He turns, his weight on the glass; the claws out; one hand, then the other, through the metal.
        add(c, ULT_TURN + 2, SoundEvents.IRON_TRAPDOOR_CLOSE, .3f, 1.4f);
        add(c, ULT_TURN + 7, SoundEvents.IRON_TRAPDOOR_CLOSE, .3f, 1.3f);
        add(c, ULT_TURN + 6, SoundEvents.GLASS_BREAK, .25f, 1.8f);
        add(c, ULT_CLAW_R - 6, SoundEvents.ARMOR_EQUIP_NETHERITE, .5f, 1.6f);
        for (float hit : new float[]{ULT_CLAW_R, ULT_CLAW_L}) {
            float p = hit == ULT_CLAW_R ? 1 : 1.08f;
            add(c, hit, SoundEvents.ANVIL_LAND, .9f, 1.25f * p);
            add(c, hit, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, .9f, 1.1f * p);
            add(c, hit, SoundEvents.TRIDENT_HIT, .6f, .9f * p);
            add(c, hit + 1, SoundEvents.IRON_GOLEM_HURT, .4f, 1.5f * p);
        }
        // The tear: the metal groaning, rending, then ripping free; the back windows bursting.
        add(c, ULT_PRESS, SoundEvents.IRON_GOLEM_STEP, .5f, .6f);
        add(c, ULT_TEAR, SoundEvents.ANVIL_USE, .6f, .6f);
        add(c, ULT_TEAR, SoundEvents.GRINDSTONE_USE, .8f, .8f);
        add(c, ULT_TEAR + 4, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, .8f, .7f);
        add(c, ULT_TEAR + 8, SoundEvents.IRON_GOLEM_HURT, .7f, .5f);
        add(c, ULT_ROOF_FREE, SoundEvents.ANVIL_DESTROY, 1f, .8f);
        add(c, ULT_ROOF_FREE, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1f, .7f);
        add(c, ULT_ROOF_FREE, SoundEvents.GLASS_BREAK, 1f, 1f);
        add(c, ULT_ROOF_FREE + 1, SoundEvents.ITEM_BREAK, .6f, .6f);
        add(c, ULT_ROOF_FREE + 30, SoundEvents.ANVIL_LAND, .25f, 1.4f);
        // The gunman hauled out and flung up into the night.
        add(c, ULT_REACH + 6, SoundEvents.ARMOR_EQUIP_LEATHER, .7f, .9f);
        add(c, ULT_THROW + 2, SoundEvents.PLAYER_ATTACK_SWEEP, .9f, .6f);
        add(c, ULT_THROW + 3, SoundEvents.ELYTRA_FLYING, .8f, 1.8f);
        add(c, ULT_THROW + 3, SoundEvents.VINDICATOR_HURT, .6f, 1.1f);
        // The charge: the hum rising, a heartbeat closer each time; a breath of silence; the release.
        add(c, ULT_CHARGE, SoundEvents.BEACON_ACTIVATE, .5f, .8f);
        add(c, ULT_CHARGE + 12, SoundEvents.WARDEN_HEARTBEAT, .6f, .8f);
        add(c, ULT_CHARGE + 22, SoundEvents.WARDEN_HEARTBEAT, .7f, .85f);
        add(c, ULT_CHARGE + 31, SoundEvents.WARDEN_HEARTBEAT, .8f, .9f);
        add(c, ULT_CHARGE + 38, SoundEvents.BEACON_POWER_SELECT, .4f, .6f);
        add(c, ULT_HOLD + 1, SoundEvents.WARDEN_SONIC_CHARGE, .45f, .5f);
        add(c, ULT_BOOM, SoundEvents.GENERIC_EXPLODE, 1f, .55f);
        add(c, ULT_BOOM, SoundEvents.WARDEN_SONIC_BOOM, 1f, .6f);
        add(c, ULT_BOOM, SoundEvents.LIGHTNING_BOLT_THUNDER, .7f, .6f);
        add(c, ULT_BOOM, SoundEvents.BEACON_DEACTIVATE, .8f, .5f);
        add(c, ULT_BOOM + 1, SoundEvents.GLASS_BREAK, 1f, .7f);
        // The slow motion: everything stretched low.
        add(c, ULT_BOOM + 4, SoundEvents.WARDEN_SONIC_BOOM, .5f, .3f);
        add(c, ULT_BOOM + 8, SoundEvents.ANVIL_LAND, .6f, .45f);
        add(c, ULT_BOOM + 9, SoundEvents.GRINDSTONE_USE, .6f, .4f);
        add(c, ULT_BOOM + 20, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, .5f, .4f);
        add(c, ULT_BOOM + 30, SoundEvents.GLASS_BREAK, .5f, .5f);
        add(c, ULT_BOOM + 30, SoundEvents.WARDEN_HEARTBEAT, .5f, .5f);
        add(c, ULT_BOOM + 50, SoundEvents.ELYTRA_FLYING, .5f, .4f);
        add(c, ULT_BOOM + 62, SoundEvents.WARDEN_HEARTBEAT, .5f, .5f);
        // His landing; the skid.
        float land = PursuitPath.realSince(PursuitPath.LAND);
        add(c, land, SoundEvents.ANVIL_LAND, .9f, .5f);
        add(c, land, SoundEvents.GENERIC_EXPLODE, .5f, 1.2f);
        add(c, land, SoundEvents.GENERIC_BIG_FALL, 1f, .7f);
        add(c, land + 1, SoundEvents.GRINDSTONE_USE, .7f, 1.2f);
        // The whip to the car; its groaning as it turns over; the crash; the debris; the fire.
        add(c, PursuitCamera.WHIP, SoundEvents.PLAYER_ATTACK_SWEEP, .6f, .5f);
        add(c, PursuitCamera.WHIP + 1, SoundEvents.ELYTRA_FLYING, .5f, .9f);
        add(c, PursuitCamera.WHIP + 10, SoundEvents.IRON_GOLEM_HURT, .4f, .4f);
        add(c, PursuitCamera.WHIP + 20, SoundEvents.IRON_GOLEM_HURT, .4f, .45f);
        float crash = PursuitCamera.CRASH_T;
        add(c, crash, SoundEvents.GENERIC_EXPLODE, 1f, .7f);
        add(c, crash + 1, SoundEvents.GENERIC_EXPLODE, .8f, .5f);
        add(c, crash, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 1f, .5f);
        add(c, crash, SoundEvents.ANVIL_LAND, 1f, .5f);
        add(c, crash, SoundEvents.GLASS_BREAK, 1f, .8f);
        add(c, crash + 2, SoundEvents.BLAZE_SHOOT, .8f, .6f);
        add(c, crash + 2, SoundEvents.FIRECHARGE_USE, .7f, .5f);
        for (int i = 0; i < 6; i++) add(c, crash + 5 + i * 3.5f, i % 2 == 0 ? SoundEvents.ANVIL_LAND : SoundEvents.CHAIN_BREAK, .3f - i * .03f, 1.2f + .1f * i);
        add(c, crash + 14, SoundEvents.BLAZE_BURN, .5f, .6f);
        // The dust; the wind taking it; the last of him: up, the claws away, the light going out of the suit.
        add(c, PursuitCamera.DUST, SoundEvents.ELYTRA_FLYING, .3f, .6f);
        add(c, 662, SoundEvents.ELYTRA_FLYING, .5f, .8f);
        add(c, 694, SoundEvents.ARMOR_EQUIP_LEATHER, .4f, .8f);
        add(c, 722, SoundEvents.BEACON_DEACTIVATE, .5f, .9f);
        add(c, 726, SoundEvents.ARMOR_EQUIP_NETHERITE, .4f, 1.3f);
        add(c, 727, SoundEvents.LEVER_CLICK, .2f, 1.8f);
        add(c, 738, SoundEvents.AMETHYST_BLOCK_CHIME, .2f, 1.2f);
        return c.toArray(new Cue[0]);
    }
    private static void add(List<Cue> list, float t, SoundEvent sound, float volume, float pitch) { list.add(new Cue(t, sound, volume, pitch)); }

    /**
     * The sound under everything, looping: the engine through the cabin (low and muffled when the camera is inside,
     * the tyres and the wind when it is out), the wet city, the charge's hum, the fire. They all fall away for the
     * held breath before the release, and drop in pitch with the slow motion.
     */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final FinalPursuitFilm film;
        private final int kind;
        private Loop(SoundEvent sound, FinalPursuitFilm film, int kind) {
            super(sound, SoundSource.MASTER, RandomSource.create());
            this.film = film; this.kind = kind;
            looping = true; delay = 0; relative = true; attenuation = Attenuation.NONE; volume = .01f; pitch = 1;
        }
        static void start(FinalPursuitFilm film) {
            var sounds = Minecraft.getInstance().getSoundManager();
            sounds.play(new Loop(SoundEvents.MINECART_INSIDE, film, 0));
            sounds.play(new Loop(SoundEvents.MINECART_RIDING, film, 1));
            sounds.play(new Loop(SoundEvents.ELYTRA_FLYING, film, 2));
            sounds.play(new Loop(SoundEvents.WEATHER_RAIN, film, 3));
            sounds.play(new Loop(SoundEvents.BEACON_AMBIENT, film, 4));
            sounds.play(new Loop(SoundEvents.FIRE_AMBIENT, film, 5));
        }
        @Override public void tick() {
            if (!film.active()) { stop(); return; }
            float t = film.time(0), tau = PursuitPath.scene(t);
            boolean inside = PursuitCamera.inside(t);
            float driving = t < ULT_BOOM ? 1 : 0;
            float hush = 1 - PursuitPath.window(t, ULT_HOLD, ULT_BOOM + .5f, 1.5f);
            float slow = Math.max(.25f, PursuitPath.scene(t + .5f) - PursuitPath.scene(t - .5f));
            float in = t < 12 ? t / 12 : 1;
            float v, p;
            switch (kind) {
                case 0 -> { v = driving * (inside ? .55f : .12f); p = .55f + .1f * Math.min(1, t / 80); }
                case 1 -> { v = driving * (inside ? .08f : .5f); p = .8f; }
                case 2 -> { v = driving * (inside ? (t > ULT_SMASH ? .18f : .03f) : .55f); p = 1.2f; }
                case 3 -> { v = .12f * (t < PursuitCamera.CRASH_T ? 1 : .5f); p = 1; }
                case 4 -> { v = t >= ULT_CHARGE && t < ULT_BOOM ? .25f + .7f * PursuitPath.clamp((t - ULT_CHARGE) / 40) : 0; p = .6f + .5f * PursuitPath.clamp((t - ULT_CHARGE) / 44); }
                default -> { v = t > PursuitCamera.CRASH_T + 4 ? .45f * Math.min(1, (t - PursuitCamera.CRASH_T - 4) / 20) : 0; p = .8f; }
            }
            volume = Math.max(.001f, v * hush * in * (.6f + .4f * slow));
            pitch = Math.max(.5f, p * (float) Math.pow(slow, .4));
        }
    }

    // ------------------------------------------------------------------ picture
    @Override public int grade(float t) {
        // Out of black at the start; into black at the end of the stage.
        float black = 1 - PursuitPath.clamp(t / 14);
        black = Math.max(black, PursuitPath.clamp((t - (ULT_STAGE_END - 8)) / 8));
        if (black > .01f && t < ULT_STAGE_END + 1) return (int) (Math.min(1, black) * 255) << 24;
        // The impact frame of the release: the picture crushed nearly black for two frames (the overlay draws the core over it).
        if (t >= ULT_BOOM && t < ULT_BOOM + 2.5f) return 0xD8080010;
        // The crash's flash, warm.
        float c = t - PursuitCamera.CRASH_T;
        if (c >= 0 && c < 4) return ((int) (.35f * (1 - c / 4) * 255) << 24) | 0xfff0d8;
        // The slow motion, a little colder.
        float cold = PursuitPath.window(t, ULT_BOOM + 3, 560, 8) * .1f;
        if (cold > .01f) return ((int) (cold * 255) << 24) | 0x302050;
        return 0;
    }

    @Override public void overlay(GuiGraphics g, float t, int w, int h) {
        if (t >= ULT_STAGE_END) return;
        if (t >= ULT_BOOM && t < ULT_BOOM + 2.5f) impactFrame(g, t, w, h);
        // A vignette all through; heavier in the dust and the last shot.
        float vig = .28f + .12f * PursuitPath.clamp((t - PursuitCamera.DUST) / 30);
        vignette(g, w, h, vig);
        // Speed outside: thin streaks at the edges while the camera runs alongside or rides the roof.
        float speed = Math.max(PursuitPath.window(t, ULT_COIL, ULT_TOUCH, 4), .7f * PursuitPath.window(t, ULT_TURN, ULT_CHARGE, 6)) * .5f;
        if (speed > .02f) speedLines(g, w, h, speed, t);
        // The title over the last held image.
        float title = PursuitPath.window(t, 722, ULT_STAGE_END - 4, 6);
        if (title > .01f) {
            var font = Minecraft.getInstance().font;
            String text = "THE FINAL PURSUIT";
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * .82f, 0);
            g.pose().scale(2f, 2f, 1);
            g.drawString(font, text, -font.width(text) / 2, -4, HudStyle.alpha(0xFFD9C8FF, title), false);
            g.pose().popPose();
            int y = (int) (h * .82f) + 12, half = font.width(text) + 8;
            g.fill(w / 2 - half, y, w / 2 + half, y + 1, HudStyle.alpha(0xFF7A4DFF, title * .8f));
        }
    }
    /** The release's impact frame: on the near-black, the core where he is, white, a violet burst, black strokes tearing out. */
    private void impactFrame(GuiGraphics g, float t, int w, int h) {
        View v = view(t);
        float[] at = v == null ? null : project(v, PursuitPath.blastCentre(), w, h, t);
        float cx = at == null ? w / 2f : at[0], cy = at == null ? h * .45f : at[1];
        int frame = (int) ((t - ULT_BOOM) / 1.2f);
        float reach = (float) Math.hypot(w, h);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        var m = g.pose().last().pose();
        int ring = 40;
        float glow = reach * .22f;
        for (int i = 0; i < ring; i++) {
            double a0 = i * Math.PI * 2 / ring, a1 = (i + 1) * Math.PI * 2 / ring;
            float r0 = glow * (.75f + .5f * hash(i * 3.7f + frame)), r1 = glow * (.75f + .5f * hash((i + 1) % ring * 3.7f + frame));
            b.vertex(m, cx, cy, 0).color(1f, 1f, 1f, 1f).endVertex();
            b.vertex(m, cx + (float) Math.cos(a0) * r0, cy + (float) Math.sin(a0) * r0, 0).color(.6f, .35f, 1f, 0f).endVertex();
            b.vertex(m, cx + (float) Math.cos(a1) * r1, cy + (float) Math.sin(a1) * r1, 0).color(.6f, .35f, 1f, 0f).endVertex();
        }
        for (int i = 0; i < 70; i++) {
            double a = hash(i * 3.3f + frame * 9.1f) * Math.PI * 2;
            float inner = glow * (.5f + .6f * hash(i * 5.1f + frame)), outer = inner + reach * (.2f + .4f * hash(i * 1.7f + frame * 4.4f));
            float half = 1.5f + 4f * hash(i * 2.9f + frame);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a), px = -sin * half, py = cos * half;
            b.vertex(m, cx + cos * inner + px, cy + sin * inner + py, 0).color(0f, 0f, 0f, .95f).endVertex();
            b.vertex(m, cx + cos * inner - px, cy + sin * inner - py, 0).color(0f, 0f, 0f, .95f).endVertex();
            b.vertex(m, cx + cos * outer, cy + sin * outer, 0).color(0f, 0f, 0f, 0f).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.disableBlend();
    }
    /** Where a stage point falls on the screen for a camera (null behind it). */
    private float[] project(View v, Vec3 p, int w, int h, float t) {
        Vec3 f = v.aim().subtract(v.pos()).normalize();
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        if (r.lengthSqr() < 1e-6) return null;
        r = r.normalize();
        Vec3 u = r.cross(f).normalize();
        Vec3 d = p.subtract(v.pos());
        double z = d.dot(f);
        if (z <= .05) return null;
        float[] kick = kick(t);
        double tan = Math.tan(Math.toRadians((v.fov() + (kick == null ? 0 : kick[3])) / 2));
        double nx = d.dot(r) / (z * tan * w / h), ny = d.dot(u) / (z * tan);
        return new float[]{(float) (w / 2 * (1 + nx)), (float) (h / 2 * (1 - ny))};
    }
    private static void vignette(GuiGraphics g, int w, int h, float strength) {
        int edge = Math.max(24, h / 4);
        for (int i = 0; i < edge; i += 2) {
            float a = strength * (float) Math.pow(1 - i / (float) edge, 1.8);
            int col = HudStyle.alpha(0xFF03020A, a);
            g.fill(0, i, w, i + 2, col); g.fill(0, h - i - 2, w, h - i, col);
            g.fill(i, 0, i + 2, h, col); g.fill(w - i - 2, 0, w - i, h, col);
        }
    }
    /** Thin streaks coming in from the edges, each living a few frames. */
    private static void speedLines(GuiGraphics g, int w, int h, float strength, float time) {
        float cx = w / 2f, cy = h / 2f, reach = (float) Math.hypot(cx, cy);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        var m = g.pose().last().pose();
        int count = (int) (60 * strength);
        for (int i = 0; i < count; i++) {
            float cycle = (float) Math.floor(time * .9f + i * .37f);
            double angle = hash(i * 31 + cycle * 17) * Math.PI * 2;
            float life = (time * .9f + i * .37f) - cycle;
            float inner = reach * (.68f + .2f * hash(i * 7 + cycle)) - life * reach * .1f, outer = reach * 1.05f;
            float alpha = strength * (1 - life) * (.25f + .35f * hash(i * 13 + cycle));
            float half = 1f + 1.6f * hash(i * 5 + cycle);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float tx = cx + cos * inner, ty = cy + sin * inner * .75f, ox = cx + cos * outer, oy = cy + sin * outer * .75f;
            float px = -sin * half, py = cos * half;
            b.vertex(m, tx, ty, 0).color(.85f, .88f, 1f, 0f).endVertex();
            b.vertex(m, ox + px, oy + py, 0).color(.85f, .88f, 1f, alpha).endVertex();
            b.vertex(m, ox - px, oy - py, 0).color(.85f, .88f, 1f, alpha).endVertex();
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.disableBlend();
    }
    private static float hash(float n) { double x = Math.sin(n * 12.9898) * 43758.5453; return (float) (x - Math.floor(x)); }
}
