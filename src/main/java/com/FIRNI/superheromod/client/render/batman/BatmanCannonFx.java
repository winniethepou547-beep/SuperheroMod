package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.batman.BatmanConfig;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanCannon.*;

/**
 * The wrist cannon as everyone sees it: the gauntlets opening into the WayneTech emitters, his body's stance and both
 * arms laid on his aim while it fires, the shots (flashes, tracers, impacts, the light they throw), cooling and closing.
 * <p>
 * Light: Minecraft has no moving lights, so every flash is faked where the eye expects it. Each tick that he fires, a
 * handful of short rays from his emitters (down, ahead, to the sides, back, up) find the floor, the walls and the
 * ceiling round him, and every shot lays a soft additive splash of warm light on those faces, brighter and tighter
 * the closer they are; the impacts light the face they hit; soft glows round his gauntlets and chest light him and his
 * cape; bodies near him and the ones hit take a warm glow. All of it lives two or three ticks, so a dark alley strobes
 * yellow while he fires and is dark again the moment he stops.
 * <p>
 * Sound, flash and camera tick come from the same shot packet: every shot plays its own crack where it leaves his
 * wrist, its flash, and (for him) a small kick of the view, which add up into a steady vibration while he holds the fire.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanCannonFx {
    private BatmanCannonFx() {}

    // ------------------------------------------------------------------ look and tuning
    /** The light: white-yellow core, warm yellow, gold, amber; cooling heat; dust and steam. */
    static final int CORE = 0xfff7d6, WHITE_GOLD = 0xffeaa6, WARM = 0xffcf4a, GOLD = 0xffa81c, AMBER = 0xff8614, HEAT = 0xff5212,
            DUST = 0x8a8075, STEAM = 0xd6cfc4;
    /** Tracers fly TRACER_SPEED blocks a tick, a streak TRACER_LENGTH long; a muzzle flash lives FLASH_LIFE ticks; light on a surface SPLASH_LIFE. */
    static final float TRACER_SPEED = 14, TRACER_LENGTH = 2.4f, FLASH_LIFE = 3f, SPLASH_LIFE = 2.4f, TINT_LIFE = 3f;
    /** How far the light of a flash is looked for round him (blocks), and how bright a splash a metre away is. */
    static final double LIGHT_REACH = 7.5;
    static final float LIGHT = .5f;
    /** His walk while firing (part of normal); the arms' convergence (radians each); the elbows' bend. */
    static final float WALK = .32f, CONVERGE = .05f, ELBOW_BEND = .22f;
    /** His view's kick per shot (degrees up, degrees of roll toward the hand's side) and how fast it settles (ticks). */
    static final float KICK_PITCH = .32f, KICK_ROLL = .16f, KICK_TAU = 1.4f;

    // ------------------------------------------------------------------ state
    /** One Batman's cannon as this client knows it: where the emitters were drawn, the last flash of each hand, the hum. */
    private static final class Gun {
        final int id;
        final Vec3[] muzzle = new Vec3[2], aim = new Vec3[2];
        long seen = -100;
        final float[] flash = {-100, -100};
        final boolean[] big = new boolean[2];
        /** The faces round him the light falls on (looked for once a tick). */
        long raysAt = -1; final List<Surface> rays = new ArrayList<>();
        Hum hum;
        Gun(int id) { this.id = id; }
    }
    private record Surface(Vec3 at, Vec3 normal, double distance) {}
    private static final class Shot {
        final Vec3 from, to; final float start, arrive; final int hit, seed; final boolean big; boolean landed;
        Shot(Vec3 from, Vec3 to, float start, float arrive, int hit, boolean big, int seed) {
            this.from = from; this.to = to; this.start = start; this.arrive = arrive; this.hit = hit; this.big = big; this.seed = seed;
        }
    }
    private record Flash(int gun, int side, float start, boolean big, int seed) {}
    private record Splash(Vec3 at, Vec3 normal, float radius, float alpha, float start, float life, int rgb) {}
    private record Tint(int entity, float start, float power) {}
    private static final class Mote {
        Vec3 pos, vel; final float size, grow, life, start, alpha; final int rgb, kind;
        Mote(Vec3 pos, Vec3 vel, float size, float grow, int rgb, float life, float start, int kind, float alpha) {
            this.pos = pos; this.vel = vel; this.size = size; this.grow = grow; this.rgb = rgb; this.life = life; this.start = start; this.kind = kind; this.alpha = alpha;
        }
    }
    private static final int M_GLOW = 0, M_DUST = 1, M_SPARK = 2, M_HEAT = 3;

    private static final Map<Integer, Gun> GUNS = new HashMap<>();
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Splash> SPLASHES = new ArrayList<>();
    private static final List<Tint> TINTS = new ArrayList<>();
    private static final List<Mote> MOTES = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static int shotCount, particlesThisTick;
    // his own view's kick
    private static float kickPitch, kickRoll, kickAt = -100, screenFlash, screenAt = -100;

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }
    private static float amount() { return BatmanConfig.EFFECTS.get().floatValue(); }
    private static int me() { var p = Minecraft.getInstance().player; return p == null ? -1 : p.getId(); }
    private static double rnd(double s) { return (RANDOM.nextDouble() - .5) * 2 * s; }
    private static float clamp01(float x) { return Mth.clamp(x, 0, 1); }

    // ------------------------------------------------------------------ the timeline (pure functions of the action clock)
    /** How far one gauntlet is open at the action clock t: the right opens first, the left follows; they close the same way after the fire. */
    static float amount(int side, float t) {
        float in = clamp01((t - (side == 0 ? 0 : 3)) / (CANNON_DEPLOY - 3));
        float out = clamp01((t - CANNON_DEPLOY - CANNON_FIRE - RETRACT_AT - (side == 0 ? 0 : 2)) / (CANNON_RETRACT - RETRACT_AT - 2));
        return in * (1 - out);
    }
    /** How bright the emitters burn: waking as the assemblies lock, charging, steady through the fire, a moment more, then dropping. */
    static float glowAt(float t) {
        float f = t - CANNON_DEPLOY;
        if (f < -4) return 0;
        if (f < 0) return .3f * PantherMotion.k(f, -4, 0);
        if (f < CHARGE_TICKS) return .3f + .55f * f / CHARGE_TICKS;
        if (f <= CANNON_FIRE) return .85f;
        float r = f - CANNON_FIRE;
        return r < GLOW_HOLD ? .9f : .9f * (float) Math.exp(-(r - GLOW_HOLD) / 3.2f);
    }
    /** Heat in the gauntlets (0..1): it builds through the fire and bleeds off after it (the vents glow, the light turns amber). */
    static float heatAt(float t) {
        float f = t - CANNON_DEPLOY;
        if (f <= 0) return 0;
        if (f <= CANNON_FIRE) return .9f * (float) Math.pow(f / CANNON_FIRE, .7);
        return .9f * (float) Math.exp(-(f - CANNON_FIRE) / 6f);
    }
    /** 0 while firing, 1 once the light has turned to cooling amber. */
    static float coolAt(float t) { return clamp01((t - CANNON_DEPLOY - CANNON_FIRE - GLOW_HOLD) / 6f); }
    /** A hand's flash (1 at a shot, gone in a couple of ticks). */
    private static float flashK(float start, float now) {
        float a = now - start;
        return a < 0 ? 0 : a < .3f ? 1 : (float) Math.exp(-(a - .3f) / 1.1f);
    }
    /** The kick of one shot in the arm (quick, a little longer to settle). */
    private static float recoilK(float a) { return a < 0 ? 0 : a < .35f ? a / .35f : (float) Math.exp(-(a - .35f) / 1.1f); }

    private static Gun gun(BatmanClient.State s) {
        for (var en : BatmanClient.states().entrySet()) if (en.getValue() == s) return GUNS.computeIfAbsent(en.getKey(), Gun::new);
        return null;
    }
    /** This hand's shot kick for this Batman now (0..1, more for the opening and final shots): the first-person arms use it. */
    static float recoil(BatmanClient.State s, int side) {
        Gun g = s == null ? null : gun(s);
        return g == null ? 0 : recoilK(now() - g.flash[side]) * (g.big[side] ? 2.5f : 1);
    }

    // ------------------------------------------------------------------ what the server reports
    /** Effect kinds FX_CANNON_FIRST..FX_CANNON_LAST from the server. */
    static void receive(BatmanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = now();
        Gun g = GUNS.computeIfAbsent(p.id(), Gun::new);
        Entity batman = mc.level.getEntity(p.id());
        switch (p.kind()) {
            case FX_DEPLOY -> { g.flash[0] = g.flash[1] = -100; if (g.hum != null) g.hum.end(); g.hum = null; }
            case FX_SHOT -> shots(mc, g, batman, p, now);
            case FX_COOL -> {
                // The fire stops: a breath of heat off both emitters.
                for (int side = 0; side < 2; side++) {
                    Vec3 m = muzzle(g, batman, side, mc.getFrameTime());
                    if (m == null) continue;
                    MOTES.add(new Mote(m, Vec3.ZERO, .5f, 0, AMBER, 4, now, M_GLOW, .5f));
                    for (int i = 0; i < (int) (6 * amount()); i++)
                        MOTES.add(new Mote(m, new Vec3(rnd(.03), .02 + RANDOM.nextDouble() * .03, rnd(.03)), .12f, .035f, STEAM, 18 + RANDOM.nextInt(10), now, M_HEAT, .12f));
                }
            }
            default -> {}
        }
        trim();
    }
    private static void trim() {
        while (MOTES.size() > 900) MOTES.remove(0);
        while (SHOTS.size() > 300) SHOTS.remove(0);
        while (SPLASHES.size() > 400) SPLASHES.remove(0);
        while (FLASHES.size() > 120) FLASHES.remove(0);
        while (TINTS.size() > 120) TINTS.remove(0);
    }

    /** One shot packet: up to two shots, their flashes, sounds, light, and his view's kick. */
    private static void shots(Minecraft mc, Gun g, Entity batman, BatmanFxPacket p, float now) {
        int flags = (int) p.power();
        boolean two = (flags & SHOT_TWO) != 0, first = (flags & SHOT_FIRST) != 0, last = (flags & SHOT_FINAL) != 0, big = first || last;
        int side = (flags & SHOT_LEFT) != 0 ? 1 : 0;
        Vec3 a = fire(mc, g, batman, side, p.pos(), hitA(flags), big, now);
        Vec3 b = two ? fire(mc, g, batman, 1 - side, p.pos().add(p.dir()), hitB(flags), big, now) : null;
        float power = last ? 2.2f : first ? 1.6f : two ? 1.25f : 1;
        light(mc, g, batman, now, power);
        // The crack of each shot where it leaves the wrist (a mechanical snap layered on every few), the final discharge's heavier one.
        boolean self = g.id == me();
        for (Vec3 at : new Vec3[]{a, b}) {
            if (at == null) continue;
            shotCount++;
            float vol = (self ? .5f : .65f) * (big ? 1.5f : 1), pitch = .93f + RANDOM.nextFloat() * .14f;
            mc.level.playLocalSound(at.x, at.y, at.z, ModSounds.BATMAN_CANNON_SHOT.get(), SoundSource.PLAYERS, vol, pitch, false);
            if (shotCount % 3 == 0) mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, .1f, 1.9f + RANDOM.nextFloat() * .1f, false);
        }
        Vec3 at = a != null ? a : b;
        if (at != null && last) {
            mc.level.playLocalSound(at.x, at.y, at.z, ModSounds.BATMAN_CANNON_FINAL.get(), SoundSource.PLAYERS, 1.3f, 1f, false);
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, .45f, 1.5f, false);
        } else if (at != null && first) mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, .35f, 1.7f, false);
        // His view: a small kick with every shot (toward the hand that fired), more for the opening pair and the final discharge.
        if (self) {
            BatmanClient.shake(last ? .24f : first ? .07f : .02f * (two ? 2 : 1));
            kick(side, last ? 4.5f : first ? 1.8f : 1);
            if (two) kick(1 - side, last ? 0 : first ? 1.8f : 1);
            screenFlash = Math.min(1, flashScreen(now) + (last ? .9f : first ? .55f : .22f));
            screenAt = now;
        }
    }
    /** One shot: the hand's flash, the tracer to where it ended, sparks off the emitter. Returns where it left from. */
    private static Vec3 fire(Minecraft mc, Gun g, Entity batman, int side, Vec3 end, int hit, boolean big, float now) {
        Vec3 from = muzzle(g, batman, side, mc.getFrameTime());
        if (from == null) return null;
        g.flash[side] = now;
        g.big[side] = big;
        int seed = RANDOM.nextInt(1 << 20);
        FLASHES.add(new Flash(g.id, side, now, big, seed));
        double len = from.distanceTo(end);
        SHOTS.add(new Shot(from, end, now, now + Math.max(.5f, (float) (len / TRACER_SPEED)), hit, big, seed));
        Vec3 dir = len < 1e-3 ? aim(g, batman, side, mc.getFrameTime()) : end.subtract(from).scale(1 / len);
        // A spit of sparks off the emitter and a wisp of heat.
        int n = (int) ((big ? 9 : 2) * amount() + RANDOM.nextFloat());
        for (int i = 0; i < n; i++)
            MOTES.add(new Mote(from, dir.scale(.12 + RANDOM.nextDouble() * .1).add(rnd(.07), rnd(.07) + .02, rnd(.07)), .02f, 0,
                    i % 2 == 0 ? CORE : WARM, 3 + RANDOM.nextInt(4), now, M_SPARK, 1));
        if (RANDOM.nextFloat() < .35f * amount() || big)
            MOTES.add(new Mote(from.add(dir.scale(.08)), new Vec3(rnd(.01), .015, rnd(.01)), .08f, .03f, STEAM, 6, now, M_HEAT, .07f));
        return from;
    }
    /**
     * The light a shot throws round him: once a tick a few short rays from between his emitters find the faces round
     * him (floor, walls, ceiling); every flash lays a warm splash on each, brighter and tighter the nearer it is. Bodies
     * near him take a warm glow too.
     */
    private static void light(Minecraft mc, Gun g, Entity batman, float now, float power) {
        if (batman == null || mc.player == null) return;
        Vec3 a = muzzle(g, batman, 0, mc.getFrameTime()), b = muzzle(g, batman, 1, mc.getFrameTime());
        if (a == null || b == null) return;
        Vec3 src = a.add(b).scale(.5);
        long tick = mc.level.getGameTime();
        if (g.raysAt != tick) {
            g.raysAt = tick;
            g.rays.clear();
            float yaw = batman.getYRot() * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)), down = new Vec3(0, -1, 0);
            Vec3[] dirs = {down, fwd.scale(.8).add(down).normalize(), fwd.scale(-.6).add(down).normalize(), right.add(down.scale(.25)).normalize(),
                    right.scale(-1).add(down.scale(.25)).normalize(), fwd.scale(-1), new Vec3(0, 1, 0), fwd};
            for (Vec3 d : dirs) {
                BlockHitResult hit = mc.level.clip(new ClipContext(src, src.add(d.scale(LIGHT_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() == HitResult.Type.MISS) continue;
                Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                g.rays.add(new Surface(hit.getLocation(), n, hit.getLocation().distanceTo(src)));
            }
        }
        for (Surface s : g.rays) {
            float d = (float) s.distance();
            float alpha = LIGHT * power / (1 + .18f * d * d);
            if (alpha < .015f) continue;
            SPLASHES.add(new Splash(s.at(), s.normal(), (.9f + .5f * d) * (power > 1.5f ? 1.4f : 1), Math.min(.9f, alpha), now, SPLASH_LIFE * (power > 2 ? 1.8f : 1), WARM));
        }
        // Bodies round him catch the light (only the near ones; the far ones get it from the hits).
        AABB box = batman.getBoundingBox().inflate(6);
        for (Entity e : mc.level.getEntities(batman, box, e -> e instanceof LivingEntity && e.isAlive())) {
            double d = e.position().distanceTo(batman.position());
            TINTS.add(new Tint(e.getId(), now, (float) (power * .55 / (1 + .12 * d * d))));
        }
    }

    /** Where this hand's emitter is: as last drawn (fresh), or about where it would be from his look. */
    private static Vec3 muzzle(Gun g, Entity e, int side, float partial) {
        var level = Minecraft.getInstance().level;
        if (g.muzzle[side] != null && level != null && level.getGameTime() - g.seen <= 2) return g.muzzle[side];
        if (e == null) return null;
        Vec3 look = e.getViewVector(partial);
        float yaw = e.getViewYRot(partial) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(side == 0 ? .3 : -.3);
        return e.getEyePosition(partial).add(0, -.3, 0).add(right).add(look.scale(.75));
    }
    private static Vec3 aim(Gun g, Entity e, int side, float partial) {
        var level = Minecraft.getInstance().level;
        if (g.aim[side] != null && level != null && level.getGameTime() - g.seen <= 2) return g.aim[side];
        return e == null ? new Vec3(0, 0, 1) : e.getViewVector(partial);
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        float t = mc.level.getGameTime();
        particlesThisTick = 0;
        // Shots reaching their end: the impact.
        for (Shot s : SHOTS) if (!s.landed && t >= s.arrive) { s.landed = true; impact(mc, s, t); }
        SHOTS.removeIf(s -> t - s.arrive > 2);
        FLASHES.removeIf(f -> t - f.start() > FLASH_LIFE + 1);
        SPLASHES.removeIf(s -> t - s.start() > s.life() + 1);
        TINTS.removeIf(s -> t - s.start() > TINT_LIFE + 1);
        for (Iterator<Mote> it = MOTES.iterator(); it.hasNext(); ) {
            Mote m = it.next();
            if (t - m.start > m.life) { it.remove(); continue; }
            m.pos = m.pos.add(m.vel);
            m.vel = switch (m.kind) {
                case M_SPARK -> m.vel.add(0, -.035, 0).scale(.9);
                case M_DUST -> m.vel.scale(.88).add(0, .002, 0);
                case M_HEAT -> m.vel.scale(.96).add(rnd(.003), .003, rnd(.003));
                default -> m.vel;
            };
        }
        // Each firing Batman: the hum under the fire; heat shimmering off the emitters while they cool.
        for (var en : BatmanClient.states().entrySet()) {
            BatmanClient.State s = en.getValue();
            if (s.action != CANNON) continue;
            Entity batman = mc.level.getEntity(en.getKey());
            if (batman == null) continue;
            Gun g = GUNS.computeIfAbsent(en.getKey(), Gun::new);
            float f = BatmanClient.clock(s, 0) - CANNON_DEPLOY;
            if (f >= -1 && f < CANNON_FIRE && (g.hum == null || g.hum.isStopped())) {
                g.hum = new Hum(en.getKey(), batman);
                mc.getSoundManager().play(g.hum);
            }
            float heat = heatAt(f + CANNON_DEPLOY);
            if (f > CANNON_FIRE && heat > .1f)
                for (int side = 0; side < 2; side++) {
                    if (RANDOM.nextFloat() > heat * amount()) continue;
                    Vec3 m = muzzle(g, batman, side, 0);
                    if (m != null) MOTES.add(new Mote(m.add(rnd(.04), .02, rnd(.04)), new Vec3(rnd(.006), .025 + RANDOM.nextDouble() * .02, rnd(.006)),
                            .07f, .03f, STEAM, 14 + RANDOM.nextInt(8), t, M_HEAT, .1f * heat));
                }
        }
        GUNS.values().removeIf(g -> mc.level.getEntity(g.id) == null && (g.hum == null || g.hum.isStopped()));
    }
    /**
     * A shot's end: on a body a bright flash, a little kinetic burst and sparks (armour: a shower of sparks and a ricochet
     * streak, the light caught on the metal); on a block a short yellow flash lighting the face, dust and chips of that
     * block (metal blocks spark and ring instead).
     */
    private static void impact(Minecraft mc, Shot s, float now) {
        Vec3 at = s.to, dir = s.to.subtract(s.from);
        double len = dir.length();
        dir = len < 1e-4 ? new Vec3(0, 0, 1) : dir.scale(1 / len);
        float k = s.big ? 1.8f : 1;
        switch (s.hit) {
            case HIT_BODY, HIT_ARMOUR -> {
                boolean armour = s.hit == HIT_ARMOUR;
                MOTES.add(new Mote(at, Vec3.ZERO, .28f * k, 0, CORE, 2, now, M_GLOW, .95f));
                MOTES.add(new Mote(at, Vec3.ZERO, .8f * k, 0, WARM, 3, now, M_GLOW, .4f));
                int n = (int) ((armour ? 8 : 3) * k * amount() + RANDOM.nextFloat());
                for (int i = 0; i < n; i++)
                    MOTES.add(new Mote(at, dir.scale(-.1).add(rnd(.16), RANDOM.nextDouble() * .14, rnd(.16)), .02f, 0, i % 3 == 0 ? CORE : WARM, 4 + RANDOM.nextInt(5), now, M_SPARK, 1));
                // The kinetic burst: a few short white streaks thrown back out of the hit.
                for (int i = 0; i < 3; i++)
                    MOTES.add(new Mote(at, dir.scale(-.18).add(rnd(.12), rnd(.12), rnd(.12)), .02f, 0, CORE, 2, now, M_SPARK, .8f));
                if (armour) {
                    // A ricochet: one long bright spark glancing off.
                    Vec3 off = dir.scale(-.3).add(rnd(.5), .2 + RANDOM.nextDouble() * .3, rnd(.5));
                    MOTES.add(new Mote(at, off, .03f, 0, WHITE_GOLD, 6, now, M_SPARK, 1));
                    if (RANDOM.nextInt(3) == 0) mc.level.playLocalSound(at.x, at.y, at.z, ModSounds.FX_IMPACT_METAL.get(), SoundSource.PLAYERS, .18f, 1.7f + RANDOM.nextFloat() * .3f, false);
                }
                Entity body = nearestBody(mc, at);
                if (body != null) {
                    TINTS.add(new Tint(body.getId(), now, armour ? 1.3f : 1));
                    if (body.getId() == me()) BatmanClient.shake(s.big ? .2f : .035f);
                }
                if (RANDOM.nextInt(2) == 0) mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, .25f, 1.5f + RANDOM.nextFloat() * .3f, false);
            }
            case HIT_BLOCK -> {
                BlockHitResult hit = mc.level.clip(new ClipContext(at.subtract(dir.scale(.4)), at.add(dir.scale(.4)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                Vec3 n = hit.getType() == HitResult.Type.MISS ? dir.scale(-1) : Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                BlockState state = hit.getType() == HitResult.Type.MISS ? null : mc.level.getBlockState(hit.getBlockPos());
                boolean metal = state != null && metal(state.getSoundType());
                Vec3 face = at.add(n.scale(.04));
                SPLASHES.add(new Splash(at, n, 1.1f * k, .6f, now, 2.2f, WARM));
                MOTES.add(new Mote(face, Vec3.ZERO, .22f * k, 0, CORE, 2, now, M_GLOW, .9f));
                MOTES.add(new Mote(face, Vec3.ZERO, .6f * k, 0, GOLD, 3, now, M_GLOW, .35f));
                if (RANDOM.nextFloat() < .6f * amount())
                    MOTES.add(new Mote(face.add(n.scale(.1)), n.scale(.04).add(rnd(.02), .01, rnd(.02)), .16f, .045f, DUST, 10 + RANDOM.nextInt(6), now, M_DUST, .35f));
                int sparks = (int) ((metal ? 7 : 1) * k * amount() + RANDOM.nextFloat() * .8f);
                for (int i = 0; i < sparks; i++)
                    MOTES.add(new Mote(face, n.scale(.12).add(dir.scale(.08)).add(rnd(.12), RANDOM.nextDouble() * .12, rnd(.12)), .02f, 0, i % 2 == 0 ? WHITE_GOLD : WARM, 4 + RANDOM.nextInt(5), now, M_SPARK, 1));
                if (metal) {
                    // Off metal the bolt glances away in a long spark and the plate rings.
                    Vec3 bounce = dir.subtract(n.scale(2 * dir.dot(n))).scale(.45).add(rnd(.1), rnd(.1), rnd(.1));
                    MOTES.add(new Mote(face, bounce, .03f, 0, WHITE_GOLD, 6, now, M_SPARK, 1));
                    if (RANDOM.nextInt(2) == 0) mc.level.playLocalSound(at.x, at.y, at.z, ModSounds.FX_IMPACT_METAL.get(), SoundSource.PLAYERS, .16f, 1.8f + RANDOM.nextFloat() * .3f, false);
                } else if (state != null && !state.isAir() && particlesThisTick < 24) {
                    // Chips of the block it hit.
                    int chips = (int) (2 * amount() * k + RANDOM.nextFloat());
                    for (int i = 0; i < chips; i++, particlesThisTick++) {
                        Vec3 v = n.scale(.12).add(rnd(.08), .06 + RANDOM.nextDouble() * .1, rnd(.08));
                        mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), face.x, face.y, face.z, v.x, v.y, v.z);
                    }
                }
            }
            default -> MOTES.add(new Mote(at, Vec3.ZERO, .12f, 0, WARM, 2, now, M_GLOW, .3f));
        }
    }
    private static boolean metal(SoundType t) {
        return t == SoundType.METAL || t == SoundType.ANVIL || t == SoundType.NETHERITE_BLOCK || t == SoundType.CHAIN || t == SoundType.COPPER || t == SoundType.LANTERN;
    }
    private static Entity nearestBody(Minecraft mc, Vec3 at) {
        Entity best = null;
        double bd = 1e9;
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(at, at).inflate(.8), e -> e instanceof LivingEntity && e.isAlive())) {
            double d = e.getBoundingBox().getCenter().distanceToSqr(at);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    // ------------------------------------------------------------------ the hum under the fire
    /** The energy hum while he fires, following him; it swells in as the fire starts and dies away as it stops. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final int id;
        private float level;
        private boolean ended;
        Hum(int id, Entity e) {
            super(ModSounds.BATMAN_CANNON_HUM.get(), SoundSource.PLAYERS, RandomSource.create());
            this.id = id;
            looping = true; delay = 0; volume = .05f; pitch = .9f;
            x = e.getX(); y = e.getY() + 1.2; z = e.getZ();
        }
        void end() { ended = true; }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            BatmanClient.State s = BatmanClient.get(e);
            if (ended || e == null || s == null || s.action != CANNON) { stop(); return; }
            float f = BatmanClient.clock(s, 0) - CANNON_DEPLOY;
            float want = f < 0 ? 0 : f < CANNON_FIRE ? 1 : Math.max(0, 1 - (f - CANNON_FIRE) / 6f);
            level += (want - level) * .35f;
            if (f > CANNON_FIRE + 8 && level < .02f) { stop(); return; }
            // The stream's pitch sags as it slows at the end.
            float sag = f > SLOW_AT ? Math.min(1, (f - SLOW_AT) / (float) (CANNON_FIRE - SLOW_AT)) : 0;
            volume = Math.max(.01f, .5f * level);
            pitch = .88f + .14f * level - .12f * sag;
            x = e.getX(); y = e.getY() + 1.2; z = e.getZ();
        }
    }

    // ------------------------------------------------------------------ drawing in the world
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean firing = false;
        for (BatmanClient.State s : BatmanClient.states().values()) if (s.action == CANNON) { firing = true; break; }
        if (!firing && SHOTS.isEmpty() && FLASHES.isEmpty() && SPLASHES.isEmpty() && TINTS.isEmpty() && MOTES.isEmpty()) return;
        float partial = e.getPartialTick(), time = mc.level.getGameTime() + partial;
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        var fx = FilmFx.batched();
        FilmContext c = new FilmContext(p, fx, cam, new Vec3(rr.x, rr.y, rr.z), new Vec3(uu.x, uu.y, uu.z), time, 0, partial);
        try {
            // The light on the surfaces round him (under everything else).
            for (Splash s : SPLASHES) {
                float k = (time - s.start()) / s.life();
                if (k < 0 || k > 1) continue;
                float fade = (1 - k) * (1 - k);
                splash(c, s.at().add(s.normal().scale(.025)), s.normal(), s.radius() * (1 + .15f * k), s.rgb(), s.alpha() * fade);
            }
            // Each firing Batman's emitters: their glow, the charge gathering, the light on him.
            for (var en : BatmanClient.states().entrySet()) {
                BatmanClient.State s = en.getValue();
                if (s.action != CANNON) continue;
                Entity batman = mc.level.getEntity(en.getKey());
                if (batman == null) continue;
                emitters(c, GUNS.computeIfAbsent(en.getKey(), Gun::new), batman, BatmanClient.clock(s, partial), time, partial);
            }
            for (Flash f : FLASHES) flash(c, f, time, partial);
            for (Shot s : SHOTS) tracer(c, s, time);
            for (Tint t : TINTS) {
                float k = (time - t.start()) / TINT_LIFE;
                if (k < 0 || k > 1) continue;
                Entity body = mc.level.getEntity(t.entity());
                if (body == null) continue;
                Vec3 mid = body.getPosition(partial).add(0, body.getBbHeight() * .55, 0);
                FilmFx.glow(c, mid, body.getBbHeight() * .7, WARM, Math.min(.35f, .16f * t.power()) * (1 - k) * (1 - k));
            }
            for (Mote m : MOTES) {
                float age = (time - m.start) / m.life;
                if (age < 0 || age > 1) continue;
                Vec3 at = m.pos.add(m.vel.scale(partial));
                switch (m.kind) {
                    case M_SPARK -> FilmFx.streak(c, at.subtract(m.vel.scale(1.3)), at, .018, m.rgb, 0, m.alpha * (1 - age), true);
                    case M_DUST -> FilmFx.puff(c, at, m.size + m.grow * (time - m.start), m.rgb, m.alpha * (1 - age) * Math.min(1, age * 5));
                    case M_HEAT -> FilmFx.puff(c, at, m.size + m.grow * (time - m.start), m.rgb, m.alpha * Mth.sin(Mth.PI * age));
                    default -> FilmFx.glow(c, at, m.size * (1 + .5f * age), m.rgb, m.alpha * (1 - age));
                }
            }
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /** One Batman's emitters: the steady glow (amber as they cool), the charge drawn in, the warm light on his arms, chest and cape. */
    private static void emitters(FilmContext c, Gun g, Entity batman, float t, float time, float partial) {
        float glow = glowAt(t), cool = coolAt(t), f = t - CANNON_DEPLOY;
        if (glow <= .01f) return;
        int tone = cool > .5f ? AMBER : GOLD;
        float lit = 0;
        for (int side = 0; side < 2; side++) {
            if (amount(side, t) < .8f && f < 0) continue;
            Vec3 m = muzzle(g, batman, side, partial), a = aim(g, batman, side, partial);
            if (m == null) continue;
            FilmFx.glow(c, m, .1 + .08 * glow, cool > .5f ? AMBER : WHITE_GOLD, .7f * glow);
            FilmFx.glow(c, m, .45, tone, .12f * glow * (1 - .5f * cool));
            // Charging: sparks of energy drawn into the emitter from round it, the glow swelling.
            if (f >= 0 && f < CHARGE_TICKS + 1) {
                Vec3 u = a.cross(new Vec3(0, 1, 0));
                u = u.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : u.normalize();
                Vec3 v = u.cross(a).normalize();
                float in = f / CHARGE_TICKS;
                for (int i = 0; i < 7; i++) {
                    double ang = i * Mth.TWO_PI / 7 + side * .4 + f * .5, u0 = (f * .55 + i / 7f) % 1, r = .45 * (1 - u0);
                    Vec3 dirOut = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
                    Vec3 head = m.add(dirOut.scale(r)), tail = m.add(dirOut.scale(r + .12)).subtract(a.scale(.05));
                    FilmFx.streak(c, tail, head, .016, WHITE_GOLD, 0, .9f * (float) u0, true);
                }
                FilmFx.glow(c, m, .2 + .35 * in, WARM, .5f * in);
            }
            float fl = flashK(g.flash[side], time) * (g.big[side] ? 1.8f : 1);
            lit = Math.max(lit, fl);
            // The flash lighting his glove and forearm.
            if (fl > .01f) FilmFx.glow(c, m.subtract(a.scale(.25)), 1.0 + .3 * fl, WARM, Math.min(.5f, .2f * fl));
        }
        // The flashes light him: chest, shoulders and the cape's edges (a big soft warm volume round his upper body).
        if (lit > .01f) {
            Vec3 body = batman.getPosition(partial);
            FilmFx.glow(c, body.add(0, 1.25, 0), 1.3, WARM, Math.min(.22f, .09f * lit));
            FilmFx.glow(c, body.add(0, 1.0, 0), 2.4, GOLD, Math.min(.12f, .045f * lit));
        }
    }
    /** One muzzle flash at the emitter as it is now: white-yellow core, gold flash, a small radial burst, a short streak ahead. */
    private static void flash(FilmContext c, Flash f, float time, float partial) {
        float a = time - f.start();
        if (a < 0 || a > FLASH_LIFE) return;
        Gun g = GUNS.get(f.gun());
        var level = Minecraft.getInstance().level;
        if (g == null || level == null) return;
        Entity batman = level.getEntity(f.gun());
        Vec3 m = muzzle(g, batman, f.side(), partial), d = aim(g, batman, f.side(), partial);
        if (m == null) return;
        float k = 1 - a / FLASH_LIFE, kk = k * k, big = f.big() ? 2.2f : 1;
        FilmFx.glow(c, m, .13 * big * (1 + .3 * a), CORE, kk);
        FilmFx.glow(c, m.add(d.scale(.08)), .4 * big * (1 + .4 * a), WARM, .75f * kk);
        FilmFx.glow(c, m, .95 * big, GOLD, .18f * k);
        FilmFx.streak(c, m, m.add(d.scale(.5 * big * (.6 + .4 * k))), .045 * big, CORE, .9f * kk, 0, true);
        // The radial burst, spreading.
        Vec3 u = d.cross(new Vec3(0, 1, 0));
        u = u.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : u.normalize();
        Vec3 v = u.cross(d).normalize();
        int n = f.big() ? 9 : 5;
        for (int i = 0; i < n; i++) {
            double h = FilmFx.hash(f.seed() + i * 7.1), ang = i * Mth.TWO_PI / n + h * .9;
            double len = (.16 + .12 * h) * big * (.5 + a * .5);
            Vec3 out = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang))).add(d.scale(.35));
            FilmFx.streak(c, m.add(d.scale(.03)), m.add(out.scale(len)), .018, WHITE_GOLD, .9f * kk, 0, true);
        }
    }
    /** A bolt: a short bright streak racing from the wrist to where it ended, collapsing into the point as it lands. */
    private static void tracer(FilmContext c, Shot s, float time) {
        float age = time - s.start, total = s.arrive - s.start;
        if (age < 0 || age > total + .5f) return;
        Vec3 span = s.to.subtract(s.from);
        double len = span.length();
        if (len < .05) return;
        Vec3 dir = span.scale(1 / len);
        float big = s.big ? 1.6f : 1;
        double travelled = len * Math.min(1, age / total);
        double streak = Math.min(TRACER_LENGTH * big, travelled);
        if (age > total) streak *= 1 - (age - total) / .5f;
        if (streak < .02) return;
        Vec3 head = s.from.add(dir.scale(travelled)), tail = head.subtract(dir.scale(streak));
        // Far away they would melt to nothing: a little wider with distance.
        double w = 1 + c.camera().distanceTo(head) / 30;
        FilmFx.streak(c, tail, head, .065 * big * w, GOLD, 0, .55f, true);
        FilmFx.streak(c, tail, head, .022 * big * w, CORE, 0, 1, true);
        FilmFx.glow(c, head, .15 * big * w, WARM, .45f);
    }
    /** A disc of light lying on a surface (normal n), bright in the middle, fading out to its rim. */
    private static void splash(FilmContext c, Vec3 at, Vec3 n, double radius, int rgb, float alpha) {
        if (alpha <= .004f || radius <= 0) return;
        VertexConsumer v = c.buffers().getBuffer(FilmFx.ADD);
        Matrix4f m = c.pose().last().pose();
        Vec3 u = Math.abs(n.y) < .9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 w = n.cross(u);
        float r = (rgb >> 16 & 255) / 255f, gg = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f, mid = alpha * .38f;
        double near = radius * .38;
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0))), d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
            vert(v, m, at, r, gg, b, alpha); vert(v, m, at, r, gg, b, alpha);
            vert(v, m, at.add(d1.scale(near)), r, gg, b, mid); vert(v, m, at.add(d0.scale(near)), r, gg, b, mid);
            vert(v, m, at.add(d0.scale(near)), r, gg, b, mid); vert(v, m, at.add(d1.scale(near)), r, gg, b, mid);
            vert(v, m, at.add(d1.scale(radius)), r, gg, b, 0); vert(v, m, at.add(d0.scale(radius)), r, gg, b, 0);
        }
    }
    private static void vert(VertexConsumer v, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, Mth.clamp(a, 0, 1)).endVertex();
    }

    // ------------------------------------------------------------------ his own view: the kick, the warm flash, the reticle
    private static float kickNow(float now, float v) { return v * (float) Math.exp(-Math.max(0, now - kickAt) / KICK_TAU); }
    /** A shot's kick of his view (toward the hand that fired), adding to what is left of the last ones. */
    private static void kick(int side, float power) {
        if (power <= 0) return;
        float now = now();
        kickPitch = kickNow(now, kickPitch) + KICK_PITCH * power;
        kickRoll = kickNow(now, kickRoll) + (side == 0 ? 1 : -1) * KICK_ROLL * Math.min(power, 2);
        kickAt = now;
    }
    private static float flashScreen(float now) { return screenFlash * (float) Math.exp(-Math.max(0, now - screenAt) / 1.2f); }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (kickPitch == 0 && kickRoll == 0) return;
        float now = now(), pitch = kickNow(now, kickPitch), roll = kickNow(now, kickRoll);
        if (Math.abs(pitch) < .003f && Math.abs(roll) < .003f) { kickPitch = kickRoll = 0; return; }
        float k = BatmanConfig.SHAKE.get().floatValue();
        e.setPitch(e.getPitch() - pitch * k);
        e.setRoll(e.getRoll() + roll * k);
    }
    /** While he fires he walks slowly and cannot jump or run. */
    @SubscribeEvent public static void walk(MovementInputUpdateEvent e) {
        var mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player || mc.player == null) return;
        BatmanClient.State s = BatmanClient.get(mc.player);
        if (s == null || s.action != CANNON) return;
        var in = e.getInput();
        in.forwardImpulse *= WALK;
        in.leftImpulse *= WALK;
        in.jumping = false;
        mc.player.setSprinting(false);
    }
    /** The warm wash of each flash over his own view (faint; stronger for the final discharge). */
    @SubscribeEvent public static void wash(RenderGuiEvent.Pre e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || FilmDirector.playing()) return;
        float k = flashScreen(now());
        if (k < .01f) return;
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        e.getGuiGraphics().fill(0, 0, w, h, HudStyle.alpha(0xFFFFC94A, Math.min(.12f, .065f * k)));
    }
    /** The reticle while he fires: four gold brackets round the crosshair pulsing with the shots, the fire left underneath. */
    @SubscribeEvent public static void reticle(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || FilmDirector.playing() || !BatmanClient.isHero(mc.player)) return;
        BatmanClient.State s = BatmanClient.get(mc.player);
        if (s == null || s.action != CANNON) return;
        float partial = e.getPartialTick(), t = BatmanClient.clock(s, partial), f = t - CANNON_DEPLOY;
        float shown = PantherMotion.k(t, CANNON_DEPLOY - 4, CANNON_DEPLOY) * (1 - PantherMotion.k(f, CANNON_FIRE, CANNON_FIRE + 6));
        if (shown <= .01f) return;
        GuiGraphics g = e.getGuiGraphics();
        float cx = e.getWindow().getGuiScaledWidth() / 2f, cy = e.getWindow().getGuiScaledHeight() / 2f, now = now();
        Gun gun = gun(s);
        float pulse = gun == null ? 0 : Math.max(flashK(gun.flash[0], now), flashK(gun.flash[1], now));
        float r = 11 - 2.5f * pulse, spin = f * 1.6f;
        int col = HudStyle.alpha(f > CANNON_FIRE ? 0xFFFF8A2A : 0xFFFFC94A, shown * (.55f + .45f * pulse));
        for (int i = 0; i < 4; i++) HudStyle.arc(g, cx, cy, r, r + 1.4f, spin + i * 90 + 25, spin + i * 90 + 65, col);
        float left = 1 - clamp01(f / CANNON_FIRE);
        HudStyle.bar(g, (int) cx - 14, (int) cy + 16, 28, left, HudStyle.alpha(0xFFFFC94A, shown * .85f));
    }

    // ------------------------------------------------------------------ his body
    /** The CANNON move's pose at its clock t over the base pose (BatmanMotion.sample). */
    static Pose pose(Pose base, float t, float time) {
        Pose p = track(base).sample(t);
        // While the stream runs the frame takes it: a faint steady tremor through the spine and shoulders.
        float fire = FilmFx.window(t, CANNON_DEPLOY + FIRST_AT, CANNON_DEPLOY + CANNON_FIRE, 2);
        if (fire > 0) {
            p.add(SPINE_PITCH, .006f * fire * Mth.sin(time * 2.9f)).add(CHEST_ROLL, .005f * fire * Mth.sin(time * 3.7f + 1));
            for (int side = 0; side < 2; side++) p.armAdd(side, SH_UP, .12f * fire * Mth.sin(time * 3.3f + side * 1.7f));
        }
        return p;
    }
    /**
     * Deploy: the forearms brought up before the chest, the head dipped to them, the knees softening as the plates
     * shift; the lock. Fire: a grounded combat stance (feet apart, the left ahead, knees bent, the torso forward, the
     * shoulders set); the arms are laid on the aim by arms(). The final discharge rocks him back a little. Cooling: the
     * forearms lowered before the waist, the hands dipped, then back to the stance.
     */
    private static Track track(Pose base) {
        Pose ready = base.copy().set(CROUCH, 1.4f).set(SPINE_PITCH, .1f).add(HEAD_PITCH, .14f);
        ready.leg(1, LEG_X, -.2f).leg(0, LEG_X, .14f);
        for (int side = 0; side < 2; side++)
            ready.arm(side, SH_FWD, .7f).arm(side, ARM_X, -.55f).arm(side, ARM_Y, -.3f).arm(side, ARM_Z, .12f).arm(side, ELBOW, 1.45f).arm(side, WRIST_X, .05f).arm(side, CURL, 1);
        Pose locked = ready.copy().add(HEAD_PITCH, -.08f);
        for (int side = 0; side < 2; side++) locked.arm(side, ELBOW, 1.58f).arm(side, WRIST_X, -.15f);
        Pose stance = base.copy().set(CROUCH, 2.4f).set(SPINE_PITCH, .15f).set(CHEST_PITCH, .03f).set(PELVIS_YAW, .14f).set(CHEST_YAW, -.08f)
                .set(NECK, .55f).set(HEAD_PITCH, -.04f);
        stance.leg(1, LEG_X, -.32f).leg(0, LEG_X, .24f).leg(0, LEG_Z, .16f).leg(1, LEG_Z, .14f).leg(1, KNEE, .12f).leg(0, ANKLE, .1f);
        for (int side = 0; side < 2; side++)
            stance.arm(side, SH_FWD, 1.2f).arm(side, SH_UP, .35f).arm(side, ARM_X, -1.45f).arm(side, ARM_Y, -.15f).arm(side, ARM_Z, 0).arm(side, ELBOW, .3f).arm(side, CURL, 1);
        Pose rocked = stance.copy().set(SPINE_PITCH, .04f).set(SHIFT_Z, .7f).set(CROUCH, 2.0f).add(HEAD_PITCH, -.06f);
        Pose cool = base.copy().set(CROUCH, 1.2f).set(SPINE_PITCH, .08f).add(HEAD_PITCH, .1f);
        cool.leg(1, LEG_X, -.16f).leg(0, LEG_X, .12f);
        for (int side = 0; side < 2; side++)
            cool.arm(side, SH_FWD, .6f).arm(side, ARM_X, -.45f).arm(side, ARM_Y, -.25f).arm(side, ARM_Z, .15f).arm(side, ELBOW, 1.25f).arm(side, WRIST_X, .35f).arm(side, CURL, .8f);
        float d = CANNON_DEPLOY, end = CANNON_DEPLOY + CANNON_FIRE;
        return new Track(true).key(0, base).key(3.5f, ready).key(d - 4, ready).key(d - 2.5f, locked).key(d + 1, stance)
                .key(d + FINAL_AT, stance).key(d + FINAL_AT + 1.2f, rocked).key(end + 3, stance).key(end + RETRACT_AT + 2, cool).key(CANNON_TICKS, base);
    }
    /**
     * After his look is spread through the body: both arms laid on the aim (slightly converging, elbows a touch bent,
     * shoulders set), the torso following the aim a little more; each hand kicks back with its own shots.
     */
    static void arms(Pose pose, BatmanClient.State s, float t, float headYaw, float headPitch) {
        float end = CANNON_DEPLOY + CANNON_FIRE;
        float w = PantherMotion.k(t, CANNON_DEPLOY - 3, CANNON_DEPLOY + 1) * (1 - PantherMotion.k(t, end + 2, end + RETRACT_AT + 3));
        if (w <= .001f) return;
        // The torso turns and leans with the aim (the head, already looking there, is turned back by as much).
        float ty = .14f * headYaw * w, tp = .14f * headPitch * w;
        pose.add(SPINE_YAW, ty).add(CHEST_PITCH, tp).add(HEAD_YAW, -ty).add(HEAD_PITCH, -tp);
        Gun g = s == null ? null : gun(s);
        float now = now();
        for (int side = 0; side < 2; side++) {
            BatmanMotion.aim(pose, side, BatmanMotion.look(headYaw + (side == 0 ? -CONVERGE : CONVERGE), headPitch), w);
            // The elbow a touch bent, the forearm kept on the aim (the upper arm takes the bend back).
            float bend = ELBOW_BEND * w;
            pose.armAdd(side, ARM_X, bend).armAdd(side, ELBOW, bend).armAdd(side, SH_UP, .25f * w).armAdd(side, WRIST_X, -.04f * w);
            if (g == null) continue;
            float r = recoilK(now - g.flash[side]) * (g.big[side] ? 3 : 1) * w;
            if (r <= .001f) continue;
            pose.armAdd(side, SH_FWD, -.45f * r).armAdd(side, ARM_X, -.05f * r).armAdd(side, ELBOW, .07f * r).armAdd(side, WRIST_X, -.1f * r);
            pose.add(CHEST_YAW, (side == 0 ? .012f : -.012f) * r).add(CHEST_PITCH, -.01f * r);
        }
    }

    // ------------------------------------------------------------------ the gauntlets
    /** For the draw that follows (deployed() is called just before each Batman is drawn): per side open, glow, flash, heat, cool. */
    private static final float[] AMOUNT = new float[2], GLOW = new float[2], FLASH = new float[2], HEAT_NOW = new float[2], COOL = new float[2];
    private static Gun drawing;
    /** How far the gauntlets are open for this Batman now, 0..1 (0 = plain gauntlets). */
    static float deployed(BatmanClient.State s, int action, float t) {
        drawing = null;
        for (int side = 0; side < 2; side++) AMOUNT[side] = GLOW[side] = FLASH[side] = HEAT_NOW[side] = COOL[side] = 0;
        if (s == null || action != CANNON) return 0;
        Gun g = gun(s);
        drawing = g;
        float now = now(), glow = glowAt(t), heat = heatAt(t), cool = coolAt(t);
        for (int side = 0; side < 2; side++) {
            AMOUNT[side] = amount(side, t);
            GLOW[side] = glow;
            FLASH[side] = g == null ? 0 : flashK(g.flash[side], now) * (g.big[side] ? 1.5f : 1);
            HEAT_NOW[side] = heat;
            COOL[side] = cool;
        }
        return Math.max(AMOUNT[0], AMOUNT[1]);
    }

    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 1, 1, 1);
    private static final int FULL = 15728880;
    /** Matte black, graphite, dark gunmetal, the silver details, the grooves; a dead lens; the light: gold, white-yellow core, cooling amber. */
    private static final float[] MATTE = {.04f, .04f, .046f}, GRAPHITE = {.085f, .088f, .096f}, GUNMETAL = {.15f, .155f, .17f},
            SILVER = {.52f, .54f, .58f}, GROOVE = {.022f, .022f, .027f}, LENS_OFF = {.07f, .06f, .04f},
            GOLD_E = {1f, .74f, .16f}, CORE_E = {1f, .96f, .74f}, AMBER_E = {1f, .42f, .08f};
    private static final float[] TINT = new float[3], MIX = new float[3];

    private static void px(PoseStack p, double x, double y, double z) { p.translate(x / 16, y / 16, z / 16); }
    /** Armour colour as drawn: clamped, pulled toward the thermal view's cold blue-black when it is on. */
    private static float[] armour(float[] c) {
        float th = BatmanBody.thermal;
        if (th <= .001f) { TINT[0] = Math.min(1, c[0]); TINT[1] = Math.min(1, c[1]); TINT[2] = Math.min(1, c[2]); return TINT; }
        float lum = (c[0] + c[1] + c[2]) / 3;
        TINT[0] = Math.min(1, Mth.lerp(th, c[0], .03f + lum * .12f));
        TINT[1] = Math.min(1, Mth.lerp(th, c[1], .05f + lum * .18f));
        TINT[2] = Math.min(1, Mth.lerp(th, c[2], .11f + lum * .35f));
        return TINT;
    }
    /** A box centred on (x, y, z) px, turned (x then y then z), of size (w, h, d) px. */
    private static void part(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float rx, float ry, float rz, float w, float h, float d, float[] c) {
        if (w <= .01f || h <= .01f || d <= .01f) return;
        p.pushPose();
        px(p, x, y, z);
        if (rx != 0 || ry != 0 || rz != 0) p.mulPose(new org.joml.Quaternionf().rotationZYX(rz, ry, rx));
        p.translate(-w / 32, -h / 32, -d / 32);
        p.scale(w, h, d);
        float[] k = armour(c);
        UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY, k[0], k[1], k[2], 1);
        p.popPose();
    }
    private static void part(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        part(p, b, light, x, y, z, 0, 0, 0, w, h, d, c);
    }
    /** An emissive part: full-bright, from a dead lens up to the light colour by k, with an additive halo (halo = how much bigger, px). */
    private static void lamp(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] rgb, float k, float halo) {
        if (k <= .02f) { part(p, b, light, x, y, z, w, h, d, LENS_OFF); return; }
        float kk = Math.min(1, k);
        for (int i = 0; i < 3; i++) MIX[i] = Math.min(1, Mth.lerp(kk, LENS_OFF[i], rgb[i]) * (.7f + .3f * Math.min(1.4f, k)));
        p.pushPose();
        px(p, x, y, z);
        p.pushPose();
        p.translate(-w / 32, -h / 32, -d / 32);
        p.scale(w, h, d);
        UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, MIX[0], MIX[1], MIX[2], 1);
        p.popPose();
        if (halo > 0) {
            float hw = w + halo, hh = h + halo, hd = d + halo, g = Math.min(1, .38f * k);
            p.translate(-hw / 32, -hh / 32, -hd / 32);
            p.scale(hw, hh, hd);
            UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, rgb[0] * g, rgb[1] * g, rgb[2] * g, 1);
        }
        p.popPose();
    }
    private static float[] lightColour(float flash, float cool) {
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) out[i] = Mth.lerp(cool, Mth.lerp(Math.min(1, flash), GOLD_E[i], CORE_E[i]), AMBER_E[i]);
        return out;
    }
    private static float backOut(float x) { float c1 = 1.70158f, c3 = c1 + 1, u = x - 1; return 1 + c3 * u * u * u + c1 * u * u; }

    /**
     * The opened emitter assembly on one forearm, in BatmanBody's forearm frame (the forearm along +y from the elbow to
     * the wrist at 4.8, px units /16; -z is the top of the forearm when the arm is raised forward, the thumb's side;
     * s * x is the outer side, where the fins are). As it opens: the two top armour plates lift and fan apart, the
     * emitter housing rises between them and slides forward toward the wrist, the wrist collar's segments open out, the
     * barrel with its emitter rings runs out over the back of the hand and locks (a small overshoot and settle); the
     * emitters light up once it has locked. Closing runs it all backward. The gold light is only in the emitters, the
     * thin conduits and (cooling) the vents; everything else stays matte and dark.
     */
    static void gauntlet(PoseStack p, MultiBufferSource b, int light, int side, float amount) {
        float a = AMOUNT[side] > 0 || drawing != null ? AMOUNT[side] : amount;
        if (a <= .002f) return;
        int s = side == 0 ? -1 : 1;
        float glow = GLOW[side], flash = FLASH[side], heat = HEAT_NOW[side], cool = COOL[side];
        float plates = PantherMotion.k(a, 0, .3f), slide = PantherMotion.k(a, .15f, .6f), open = PantherMotion.k(a, .35f, .78f);
        float lock = backOut(clamp01((a - .6f) / .32f)), seat = PantherMotion.k(a, .9f, 1f);
        float lit = glow * PantherMotion.k(a, .8f, 1f);
        float[] tone = lightColour(flash, cool), heatTone = lightColour(0, 1);

        // ---- the two top plates: lifted off the forearm and fanned apart, tilting outward about the forearm's axis
        for (int i = -1; i <= 1; i += 2) {
            p.pushPose();
            px(p, i * (.8f + .5f * plates), 2.5f, -2.3f - .28f * plates);
            p.mulPose(Axis.YP.rotation(i * .24f * plates));
            part(p, b, light, 0, 0, 0, 1.5f, 3.6f, .4f, GUNMETAL);
            part(p, b, light, i * .7f, 0, -.12f, .14f, 3.4f, .24f, SILVER);
            part(p, b, light, 0, -1.25f, -.21f, 1.25f, .12f, .08f, GROOVE);
            // Three vent slots: dark, glowing amber while the heat bleeds off.
            for (int v = 0; v < 3; v++) {
                if (heat > .15f && cool > 0) lamp(p, b, light, -i * .15f, -.45f + v * .55f, -.22f, .8f, .14f, .08f, heatTone, heat * cool * .9f, 0);
                else part(p, b, light, -i * .15f, -.45f + v * .55f, -.22f, .8f, .14f, .08f, GROOVE);
            }
            p.popPose();
        }

        // ---- the emitter housing: rises out of the forearm between the plates and slides forward toward the wrist
        float hy = 1.9f + 1.5f * slide, hz = -1.5f - 1.25f * slide;
        if (slide > .01f) {
            part(p, b, light, 0, hy, hz, 1.6f, 3.6f, 1.1f, GRAPHITE);
            part(p, b, light, 0, hy - 1.75f, hz, 1.7f, .3f, 1.2f, GUNMETAL);
            for (int i = -1; i <= 1; i += 2) part(p, b, light, i * .86f, hy, hz - .1f, .14f, 3.4f, .35f, SILVER);
            for (int k = 0; k < 3; k++) part(p, b, light, 0, hy - .9f + k * .9f, hz - .56f, 1.62f, .1f, .06f, GROOVE);
            // Energy conduits along its top: thin lines of light.
            for (int i = -1; i <= 1; i += 2) lamp(p, b, light, i * .42f, hy + .1f, hz - .58f, .13f, 3.0f, .08f, tone, lit * (.45f + .55f * flash), 0);
            // The lock pins drop in at the very end.
            if (seat > .01f) for (int i = -1; i <= 1; i += 2) part(p, b, light, i * .98f, hy - 1.2f, hz - .35f, .28f * seat, .28f * seat, .28f * seat, SILVER);
        }

        // ---- the wrist collar: its segments (under, outer, inner) open out round the wrist, small joints at the corners
        float wy = 4.5f, ro = 2.3f + .45f * open, ri = 2.3f + .3f * open, rb = 2.3f + .4f * open;
        part(p, b, light, 0, wy, rb, 3.4f, 1.0f, .5f, GUNMETAL);
        part(p, b, light, s * ro, wy, -.2f, .5f, 1.0f, 3.2f, GUNMETAL);
        part(p, b, light, -s * ri, wy, -.2f, .5f, 1.0f, 3.2f, GUNMETAL);
        part(p, b, light, s * (ro - .1f), wy, rb - .1f, .5f, .6f, .5f, SILVER);
        part(p, b, light, -s * (ri - .1f), wy, rb - .1f, .5f, .6f, .5f, SILVER);
        part(p, b, light, s * ro, wy - .55f, -.2f, .55f, .12f, 3.0f, MATTE);
        // The small emitters round the wrist: two on the outer segment, one underneath.
        lamp(p, b, light, s * (ro + .28f), wy, -1.0f, .2f, .42f, .42f, tone, lit * (.55f + .45f * flash), .5f);
        lamp(p, b, light, s * (ro + .28f), wy, .6f, .2f, .42f, .42f, tone, lit * (.55f + .45f * flash), .5f);
        lamp(p, b, light, 0, wy, rb + .28f, .42f, .42f, .2f, tone, lit * .5f, .4f);
        // A conduit down the inner side of the forearm.
        if (open > .05f) lamp(p, b, light, -s * 2.26f, 2.6f, -.8f, .1f, 3.0f * open, .12f, tone, lit * .4f, 0);

        // ---- the barrel: runs out of the housing over the back of the hand and locks, emitter rings round its mouth
        if (lock > .001f) {
            float tip = 4.6f + 1.6f * lock, len = 1.0f + 1.6f * Math.max(0, lock);
            part(p, b, light, 0, tip - len / 2, hz, 1.15f, len, 1.15f, MATTE);
            part(p, b, light, 0, tip - .35f, hz, 1.42f, .22f, 1.42f, SILVER);
            part(p, b, light, 0, tip - .95f, hz, 1.36f, .18f, 1.36f, GUNMETAL);
            // The emitter ring: four points of light round the mouth.
            for (int i = 0; i < 4; i++) {
                float ox = (i % 2 == 0 ? 1 : -1) * .5f, oz = (i < 2 ? 1 : -1) * .5f;
                lamp(p, b, light, ox, tip - .2f, hz + oz, .2f, .2f, .2f, tone, lit * (.6f + .4f * flash), .35f);
            }
            // The lens, its white-yellow core, and the flash blooming out of it.
            lamp(p, b, light, 0, tip + .05f, hz, .82f, .14f, .82f, tone, lit * (.65f + .5f * flash), 1.0f);
            lamp(p, b, light, 0, tip + .1f, hz, .4f, .12f, .4f, CORE_E, lit * (.35f + .8f * flash), 0);
            if (flash > .03f && lit > .05f) {
                float size = 1.8f + 2.6f * Math.min(1.5f, flash);
                float g = Math.min(1, .3f * flash * lit);
                p.pushPose();
                px(p, 0, tip + .6f, hz);
                p.translate(-size / 32, -size / 32, -size / 32);
                p.scale(size, size, size);
                UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, tone[0] * g, tone[1] * g * .9f, tone[2] * g * .6f, 1);
                p.popPose();
            }
            if (BatmanBody.capture && drawing != null) {
                var level = Minecraft.getInstance().level;
                Vec3 at = BatmanBody.world(p, 0, tip + .2f, hz), ahead = BatmanBody.world(p, 0, tip + 16.2f, hz);
                Vec3 d = ahead.subtract(at);
                drawing.muzzle[side] = at;
                drawing.aim[side] = d.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : d.normalize();
                if (level != null) drawing.seen = level.getGameTime();
            }
        }
    }

    private static void clear() {
        for (Gun g : GUNS.values()) if (g.hum != null) g.hum.end();
        GUNS.clear(); SHOTS.clear(); FLASHES.clear(); SPLASHES.clear(); TINTS.clear(); MOTES.clear();
        kickPitch = kickRoll = 0; screenFlash = 0;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}
