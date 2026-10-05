package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.batman.BatmanConfig;
import com.FIRNI.superheromod.heroes.batman.BatmanSonic;
import com.FIRNI.superheromod.heroes.batman.SonicEmitterEntity;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.*;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;
import static com.FIRNI.superheromod.heroes.batman.SonicEmitterEntity.*;

/**
 * The sonic trap as everyone sees and hears it: the remote in his hand and the press of its red button, the two
 * emitters coming up out of the ground (the soil stirring and breaking open, the dark torn patch round each foot, dust),
 * their cold core light, the pulses (one moving pressure wave each: two thin rings, a short trail, a haze, then the
 * impact ring and flash), the failure (sparks, arcs, flicker, the small blast, metal pieces, smoke), and for the one it
 * hits: a heavy low-frequency shake of the view (strong side to side, a little up and down), a short FOV punch, a
 * rippling colour-split picture (post shader shaders/post/batman_sonic.json; a plain vignette if it cannot load), the
 * hearing swamped (other sounds quieter and lower for a moment) and ears ringing.
 * <p>
 * Sound is played here, per client, by role: the target hears the trap loud and close (its sounds are placed nearer to
 * their ears), Batman hears it quiet and controlled, everyone else normally. Nothing is played by the server for it.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanSonicFx {
    private BatmanSonicFx() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final RandomSource RANDOM = RandomSource.create();
    /** Colours: the cold core and pulse light, the pressure ring, the torn earth, dust, sparks, smoke. */
    static final int COLD = 0xcfeeff, RING = 0xdff6ff, HAZE = 0xdfe6ea, EARTH = 0x120d09, DUST = 0x8a7f72, SPARK = 0xffe2a8,
            ARC = 0xbfe4ff, SMOKE = 0x3a3c40, IRON = 0x2a2c30;
    /** How fast a pulse closes the distance (blocks per tick), its shortest and longest flight (ticks). */
    private static final float PULSE_SPEED = 4.5f, PULSE_MIN = 2f, PULSE_MAX = 6;
    /** A pulse is a train of wave fronts: how many, how far apart (blocks), and how fast each front widens with distance. */
    private static final int FRONTS = 6;
    private static final double FRONT_GAP = .55, FRONT_SPREAD = .1;
    /** Volume by role: the target, Batman, anyone else (times each sound's own level); the target's sounds are moved this much of the way to their ears. */
    private static final float VOL_TARGET = 1f, VOL_OWNER = .32f, VOL_OTHER = .65f, CLOSER = .7f;

    // ------------------------------------------------------------------ what is in the air
    private static final class Pulse {
        final int emitter, victim; final boolean hit; final Vec3 from, to; final float start, travel; boolean landed;
        Pulse(int emitter, int victim, boolean hit, Vec3 from, Vec3 to, float start) {
            this.emitter = emitter; this.victim = victim; this.hit = hit; this.from = from; this.to = to; this.start = start;
            this.travel = Mth.clamp((float) from.distanceTo(to) / PULSE_SPEED, PULSE_MIN, PULSE_MAX);
        }
    }
    private static final int M_GLOW = 0, M_DUST = 1, M_SPARK = 2, M_CUBE = 3, M_ARC = 4;
    private static final class Mote {
        Vec3 pos, vel; final float size, grow, life, start, alpha, spin; final int rgb, kind;
        Mote(int kind, Vec3 pos, Vec3 vel, float size, float grow, int rgb, float life, float start, float alpha) {
            this.kind = kind; this.pos = pos; this.vel = vel; this.size = size; this.grow = grow; this.rgb = rgb; this.life = life; this.start = start; this.alpha = alpha;
            this.spin = RANDOM.nextFloat() * 6;
        }
    }
    /** A ring in the air (normal = its axis; a flat one on the ground has normal up). */
    private record Ring(Vec3 at, Vec3 normal, float from, float to, float width, float start, float life, int rgb, float alpha, boolean light) {}
    /** The torn ground left a moment after an emitter has gone (sunk back, or blown apart: scorched). */
    private record Scar(Vec3 at, int id, float start, float life, boolean burnt) {}
    /** What this client has already played for one emitter. */
    private static final class Watch { float last = -2; Hum hum; boolean breakSeen, blasted; float hitAt = -100; }

    private static final List<Pulse> PULSES = new ArrayList<>();
    private static final List<Mote> MOTES = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final List<Scar> SCARS = new ArrayList<>();
    private static final Map<Integer, Watch> WATCH = new HashMap<>();
    /** Sound instances this class started (never muffled). */
    private static final Set<SoundInstance> OURS = Collections.newSetFromMap(new WeakHashMap<>());

    // ------------------------------------------------------------------ the one it hits
    private static float lastHit = -1000, shakeAtHit, fovAtHit, flashAtHit, side;
    private static TinnitusSound ears;
    // the post effect
    private static final ResourceLocation EFFECT = new ResourceLocation(SuperheroMod.MODID, "shaders/post/batman_sonic.json");
    private static PostChain chain;
    private static List<PostPass> passes = List.of();
    private static int chainW = -1, chainH = -1;
    private static boolean failed;

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }
    private static float amount() { return BatmanConfig.EFFECTS.get().floatValue(); }
    private static int me() { var p = Minecraft.getInstance().player; return p == null ? -1 : p.getId(); }
    private static double rnd(double s) { return (RANDOM.nextDouble() - .5) * 2 * s; }

    // ------------------------------------------------------------------ what the server reports
    /** Effect kinds FX_SONIC_FIRST..FX_SONIC_LAST from the server. */
    static void receive(BatmanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = now();
        switch (p.kind()) {
            case BatmanSonic.FX_PRESS -> {
                // The red button: one clean beep from his hand (loud for the one it is meant for, if near).
                Vec3 at = BatmanLayer.hand(p.entity(), 1);
                play(ModSounds.BATMAN_SONIC_BEEP.get(), at == null ? p.pos() : at, .75f, 1, p.id(), p.entity());
            }
            case BatmanSonic.FX_PULSE -> {
                Entity found = mc.level.getEntity(p.id());
                SonicEmitterEntity e = found instanceof SonicEmitterEntity se ? se : null;
                Vec3 from = p.pos();
                if (e != null) {
                    e.pulsedAt = t;
                    Vec3[] face = face(e, 0);
                    if (face != null) from = face[0];
                }
                boolean hit = p.power() > 0;
                PULSES.add(new Pulse(p.id(), p.entity(), hit, from, p.dir(), t));
                Vec3 dir = p.dir().subtract(from).normalize();
                RINGS.add(new Ring(from, dir, .25f, .85f, .06f, t, 4, RING, .7f, true));
                MOTES.add(new Mote(M_GLOW, from, Vec3.ZERO, .7f, 0, COLD, 3, t, .8f));
                int target = e == null ? p.entity() : e.targetId(), owner = e == null ? -1 : e.ownerId();
                play(ModSounds.BATMAN_SONIC_PULSE.get(), from, .8f, .92f + RANDOM.nextFloat() * .16f, target, owner);
            }
            case BatmanSonic.FX_DAMAGED -> {
                Entity found = mc.level.getEntity(p.id());
                int target = found instanceof SonicEmitterEntity se ? se.targetId() : -1, owner = found instanceof SonicEmitterEntity se2 ? se2.ownerId() : -1;
                Watch w = WATCH.computeIfAbsent(p.id(), k -> new Watch());
                w.hitAt = t;
                sparks(p.pos(), new Vec3(0, 1, 0), (int) (10 * amount()), .22f);
                MOTES.add(new Mote(M_GLOW, p.pos(), Vec3.ZERO, .45f, 0, SPARK, 2, t, .7f));
                play(ModSounds.FX_IMPACT_METAL.get(), p.pos(), .7f, .7f + RANDOM.nextFloat() * .2f, target, owner);
                play(SoundEvents.ANVIL_LAND, p.pos(), .18f, 1.7f, target, owner);
            }
            default -> {}
        }
        trim();
    }
    private static void trim() {
        while (MOTES.size() > 600) MOTES.remove(0);
        while (RINGS.size() > 120) RINGS.remove(0);
        while (PULSES.size() > 60) PULSES.remove(0);
    }

    // ------------------------------------------------------------------ sound by role
    /** A sound of the trap: loud and close for its target, quiet for Batman, normal for the rest. */
    private static void play(SoundEvent sound, Vec3 at, float volume, float pitch, int target, int owner) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || at == null) return;
        int self = mc.player.getId();
        Vec3 pos = at;
        float role = VOL_OTHER;
        if (self == target) {
            role = VOL_TARGET;
            Vec3 ear = mc.player.getEyePosition();
            pos = ear.add(at.subtract(ear).scale(1 - CLOSER));
        } else if (self == owner) role = VOL_OWNER;
        var instance = new SimpleSoundInstance(sound, SoundSource.PLAYERS, Math.min(1, volume * role), pitch, RANDOM, pos.x, pos.y, pos.z);
        OURS.add(instance);
        mc.getSoundManager().play(instance);
    }
    private static void play(SoundEvent sound, SonicEmitterEntity e, Vec3 at, float volume, float pitch) { play(sound, at, volume, pitch, e.targetId(), e.ownerId()); }

    /** The active emitter's hum (looped, on the emitter; loud and close for its target), fading in and out with its light. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final SonicEmitterEntity e;
        private float level;
        Hum(SonicEmitterEntity e) {
            super(ModSounds.BATMAN_SONIC_HUM.get(), SoundSource.PLAYERS, RandomSource.create());
            this.e = e;
            looping = true; delay = 0; volume = .01f; pitch = 1;
            place();
        }
        private void place() {
            var mc = Minecraft.getInstance();
            Vec3 at = e.head();
            if (mc.player != null && mc.player.getId() == e.targetId()) { Vec3 ear = mc.player.getEyePosition(); at = ear.add(at.subtract(ear).scale(1 - CLOSER)); }
            x = at.x; y = at.y; z = at.z;
        }
        @Override public void tick() {
            if (e.isRemoved()) { stop(); return; }
            float age = e.age(0);
            boolean on = e.active(age) || (age >= RISEN + 2 && age < e.activeEnd());
            level += ((on ? 1 : 0) - level) * (on ? .25f : .18f);
            if (!on && level < .02f) { stop(); return; }
            int self = Minecraft.getInstance().player == null ? -1 : Minecraft.getInstance().player.getId();
            float role = self == e.targetId() ? VOL_TARGET : self == e.ownerId() ? VOL_OWNER * .8f : VOL_OTHER;
            // A swell under each pulse.
            float since = e.level().getGameTime() - e.pulsedAt;
            float swell = since >= 0 && since < 4 ? .15f * (1 - since / 4) : 0;
            volume = Math.max(.001f, Math.min(1, (.8f + swell) * role * level));
            pitch = .85f + .15f * level + swell * .3f;
            place();
        }
    }
    /** Ringing ears for the one it hit (not positioned, quiet): fades out after the last hit. */
    private static final class TinnitusSound extends AbstractTickableSoundInstance {
        TinnitusSound() {
            super(ModSounds.BATMAN_SONIC_RING.get(), SoundSource.PLAYERS, RandomSource.create());
            looping = true; delay = 0; relative = true; attenuation = SoundInstance.Attenuation.NONE; volume = .01f; pitch = 1; x = y = z = 0;
        }
        @Override public void tick() {
            float d = disruption();
            if (d < .015f) { stop(); return; }
            volume = Math.max(.001f, .3f * d);
            pitch = .98f + .04f * d;
        }
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        float t = mc.level.getGameTime();
        // ---- each emitter: its sounds, soil and dust at the moments of its timeline
        Set<Integer> seen = new HashSet<>();
        for (Iterator<SonicEmitterEntity> it = SonicEmitterEntity.CLIENT.iterator(); it.hasNext(); ) {
            SonicEmitterEntity e = it.next();
            if (e.isRemoved() || e.level() != mc.level) { it.remove(); gone(e); continue; }
            if (!e.placed()) continue;
            seen.add(e.getId());
            emitterTick(e, WATCH.computeIfAbsent(e.getId(), k -> new Watch()), t);
        }
        WATCH.keySet().removeIf(id -> !seen.contains(id) && mc.level.getEntity(id) == null);
        // ---- the pulses: landing
        for (Iterator<Pulse> it = PULSES.iterator(); it.hasNext(); ) {
            Pulse p = it.next();
            if (!p.landed && t >= p.start + p.travel) { p.landed = true; land(p, t); }
            if (t > p.start + p.travel + 2) it.remove();
        }
        // ---- motes
        for (Iterator<Mote> it = MOTES.iterator(); it.hasNext(); ) {
            Mote m = it.next();
            if (t - m.start > m.life) { it.remove(); continue; }
            m.pos = m.pos.add(m.vel);
            switch (m.kind) {
                case M_SPARK -> m.vel = m.vel.add(0, -.04, 0).scale(.93);
                case M_CUBE -> {
                    m.vel = m.vel.add(0, -.06, 0).scale(.97);
                    BlockPos under = BlockPos.containing(m.pos.x, m.pos.y - m.size, m.pos.z);
                    if (m.vel.y < 0 && !mc.level.getBlockState(under).getCollisionShape(mc.level, under).isEmpty())
                        { m.pos = new Vec3(m.pos.x, Math.max(m.pos.y, under.getY() + 1 + m.size), m.pos.z); m.vel = new Vec3(m.vel.x * .5, -m.vel.y * .3, m.vel.z * .5); }
                }
                case M_DUST -> m.vel = m.vel.scale(.9).add(0, .003, 0);
                default -> {}
            }
        }
        RINGS.removeIf(r -> t - r.start() > r.life());
        SCARS.removeIf(s -> t - s.start() > s.life());
        // ---- the one it hit: ringing ears, no sprinting through it
        float d = disruption();
        if (d > .015f) {
            if (ears == null || !mc.getSoundManager().isActive(ears)) { ears = new TinnitusSound(); OURS.add(ears); mc.getSoundManager().play(ears); }
            if (d > .3f && mc.player.isSprinting()) mc.player.setSprinting(false);
        }
    }
    /** The moments of one emitter's timeline that have just passed on this client (each played once; a late arrival skips the past). */
    private static void emitterTick(SonicEmitterEntity e, Watch w, float t) {
        float age = e.age(0);
        if (w.last < -1) w.last = age < 3 ? -1 : age;
        float last = w.last;
        w.last = age;
        Vec3 at = e.position();
        boolean broken = e.broken();
        float fx = amount();
        if (!broken) {
            if (passed(last, age, 0)) play(ModSounds.BATMAN_SONIC_RUMBLE.get(), e, at, .95f, .95f + RANDOM.nextFloat() * .1f);
            // The ground stirring: soil and small stones moving, more as it is about to break.
            if (age < PREP && RANDOM.nextFloat() < (.5f + age / PREP) * fx) earth(e, 1, .7, .05);
            if (passed(last, age, BREAK_AT)) {
                earth(e, (int) (14 * fx), .9, .2);
                puffs(at.add(0, .1, 0), (int) (4 * fx), .7f, t);
                play(SoundEvents.ROOTED_DIRT_BREAK, e, at, .7f, .6f);
                play(SoundEvents.GRAVEL_BREAK, e, at, .5f, .7f);
            }
            if (passed(last, age, PREP)) { play(ModSounds.BATMAN_SONIC_RISE.get(), e, at, .9f, 1); play(SoundEvents.PISTON_EXTEND, e, at, .35f, .55f); }
            if (age > PREP && age < RISEN && RANDOM.nextFloat() < .6f * fx) earth(e, 1, .65, .12);
            if (passed(last, age, RISEN)) {
                play(ModSounds.BATMAN_SONIC_LOCK.get(), e, at, 1f, 1);
                play(SoundEvents.IRON_TRAPDOOR_CLOSE, e, at, .5f, .5f);
                earth(e, (int) (8 * fx), .9, .15);
                puffs(at.add(0, .05, 0), (int) (5 * fx), .9f, t);
                RINGS.add(new Ring(at.add(0, .04, 0), new Vec3(0, 1, 0), .4f, 1.8f, .3f, t, 10, DUST, .3f, false));
            }
            if (passed(last, age, RISEN + 2)) play(SoundEvents.BEACON_ACTIVATE, e, e.head(), .22f, 1.8f);
            if (age >= RISEN + 2 && age < e.activeEnd() && (w.hum == null || w.hum.isStopped())) {
                w.hum = new Hum(e);
                OURS.add(w.hum);
                Minecraft.getInstance().getSoundManager().play(w.hum);
            }
            if (passed(last, age, e.activeEnd())) play(SoundEvents.BEACON_DEACTIVATE, e, e.head(), .3f, 1.4f);
            // Cooling: a breath of vapour off the fins.
            if (age > e.activeEnd() && age < e.retractFrom() && RANDOM.nextFloat() < .35f * fx) {
                Vec3[] face = face(e, 0);
                if (face != null) MOTES.add(new Mote(M_DUST, face[2].subtract(face[1].scale(.4)), new Vec3(rnd(.01), .025, rnd(.01)), .18f, .03f, 0xd8dcdf, 22, t, .18f));
            }
            if (passed(last, age, e.retractFrom())) { play(ModSounds.BATMAN_SONIC_RETRACT.get(), e, at, .9f, 1); play(SoundEvents.PISTON_CONTRACT, e, at, .35f, .55f); }
            if (age > e.retractFrom() + RETRACT * .55f && RANDOM.nextFloat() < .7f * fx) earth(e, 1, .6, .08);
            if (passed(last, age, e.gone() - 2)) {
                play(ModSounds.BATMAN_SONIC_LOCK.get(), e, at, .5f, .75f);
                earth(e, (int) (10 * fx), .8, .12);
                puffs(at.add(0, .05, 0), (int) (3 * fx), .6f, t);
                SCARS.add(new Scar(at, e.getId(), t, 40, false));
            }
        } else {
            float b = e.brokenAge(0);
            if (!w.breakSeen) {
                w.breakSeen = true;
                if (b < 3) play(ModSounds.BATMAN_SONIC_BREAK.get(), e, e.head(), 1f, 1);
            }
            Vec3[] face = face(e, 0);
            Vec3 head = face == null ? e.head() : face[2];
            // Shorting out: sparks spitting off it, a crackle of arcs, smoke starting.
            if (b < FAIL) {
                if (RANDOM.nextFloat() < .8f) sparks(head.add(rnd(.35), rnd(.3), rnd(.35)), new Vec3(rnd(1), .6, rnd(1)), (int) (3 * fx) + 1, .16f);
                if (RANDOM.nextFloat() < .35f) MOTES.add(new Mote(M_ARC, head.add(rnd(.3), rnd(.25), rnd(.3)), new Vec3(rnd(.5), rnd(.5), rnd(.5)), .03f, 0, ARC, 2, t, .9f));
                if (RANDOM.nextFloat() < .4f * fx) MOTES.add(new Mote(M_DUST, head.add(rnd(.2), .2, rnd(.2)), new Vec3(rnd(.01), .03, rnd(.01)), .25f, .03f, SMOKE, 26, t, .4f));
                if (RANDOM.nextFloat() < .2f) Minecraft.getInstance().level.addParticle(ParticleTypes.ELECTRIC_SPARK, head.x + rnd(.3), head.y + rnd(.3), head.z + rnd(.3), rnd(.2), .1, rnd(.2));
            }
            if (!w.blasted && b >= FAIL) { w.blasted = true; blast(e, head, t); }
        }
    }
    private static boolean passed(float last, float now, float at) { return last < at && now >= at; }
    /** An emitter left this client's world without the end being seen (out of range, or the blast tick missed). */
    private static void gone(SonicEmitterEntity e) {
        Watch w = WATCH.remove(e.getId());
        if (w != null && w.hum != null) w.hum.level = 0;
    }
    /** The small mechanical blast: a short flash, sparks, an arc or two, metal pieces flying, smoke; the ground scorched. */
    private static void blast(SonicEmitterEntity e, Vec3 head, float t) {
        var mc = Minecraft.getInstance();
        float fx = amount();
        play(SoundEvents.GENERIC_EXPLODE, e, head, .35f, 1.8f);
        MOTES.add(new Mote(M_GLOW, head, Vec3.ZERO, 1.6f, 0, 0xfff1d8, 3, t, .95f));
        MOTES.add(new Mote(M_GLOW, head, Vec3.ZERO, 3.2f, 0, COLD, 4, t, .35f));
        RINGS.add(new Ring(head, new Vec3(0, 1, 0), .2f, 2.2f, .1f, t, 6, 0xfff4e0, .5f, true));
        sparks(head, new Vec3(0, 1, 0), (int) (26 * fx) + 4, .35f);
        for (int i = 0; i < 3; i++) MOTES.add(new Mote(M_ARC, head, new Vec3(rnd(1.4), rnd(1) + .3, rnd(1.4)), .04f, 0, ARC, 3, t, 1));
        for (int i = 0; i < (int) (12 * fx) + 3; i++)
            MOTES.add(new Mote(M_CUBE, head.add(rnd(.3), rnd(.3), rnd(.3)), new Vec3(rnd(.28), .15 + RANDOM.nextDouble() * .3, rnd(.28)), .03f + RANDOM.nextFloat() * .05f, 0,
                    i % 3 == 0 ? 0x4a4c52 : IRON, 30 + RANDOM.nextInt(20), t, 1));
        for (int i = 0; i < (int) (9 * fx) + 2; i++)
            MOTES.add(new Mote(M_DUST, head.add(rnd(.3), rnd(.3), rnd(.3)), new Vec3(rnd(.06), .03 + RANDOM.nextDouble() * .05, rnd(.06)), .45f + RANDOM.nextFloat() * .3f, .04f, SMOKE, 40 + RANDOM.nextInt(25), t, .55f));
        mc.level.addParticle(ParticleTypes.LARGE_SMOKE, head.x, head.y, head.z, 0, .05, 0);
        earth(e, (int) (8 * fx), .8, .2);
        SCARS.add(new Scar(e.position(), e.getId(), t, 80, true));
        if (mc.player != null && mc.player.position().distanceTo(head) < 6 && mc.player.getId() != e.ownerId()) BatmanClient.shake(.12f);
    }
    /** A pulse arriving: the pressure ring and flash where it lands; on the target, everything else. */
    private static void land(Pulse p, float t) {
        var mc = Minecraft.getInstance();
        Vec3 to = p.to;
        Entity victim = p.hit ? mc.level.getEntity(p.victim) : null;
        if (victim != null) to = victim.getBoundingBox().getCenter();
        Vec3 dir = to.subtract(p.from);
        dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Entity src = mc.level.getEntity(p.emitter);
        int target = src instanceof SonicEmitterEntity se ? se.targetId() : p.victim, owner = src instanceof SonicEmitterEntity se2 ? se2.ownerId() : -1;
        if (p.hit) {
            RINGS.add(new Ring(to, dir, .3f, 2.1f, .12f, t, 7, RING, .65f, true));
            RINGS.add(new Ring(to.subtract(dir.scale(.3)), dir, .2f, 1.3f, .08f, t + 1.5f, 6, COLD, .45f, true));
            MOTES.add(new Mote(M_GLOW, to, Vec3.ZERO, 1.3f, 0, 0xf4fbff, 3, t, .7f));
            if (victim != null) RINGS.add(new Ring(victim.position().add(0, .05, 0), new Vec3(0, 1, 0), .3f, 2.4f, .35f, t, 9, 0xbdb6a8, .28f, false));
            puffs(to, (int) (3 * amount()), .5f, t);
            play(ModSounds.BATMAN_SONIC_HIT.get(), to, 1f, .9f + RANDOM.nextFloat() * .15f, target, owner);
            if (p.victim == me()) disrupt(t, dir);
        } else {
            RINGS.add(new Ring(to, dir, .2f, 1.1f, .08f, t, 5, RING, .45f, true));
            MOTES.add(new Mote(M_GLOW, to, Vec3.ZERO, .6f, 0, COLD, 2, t, .5f));
            puffs(to, (int) (2 * amount()), .4f, t);
            play(ModSounds.BATMAN_SONIC_PULSE.get(), to, .45f, .7f, target, owner);
        }
    }

    // ------------------------------------------------------------------ the one it hits
    /** A pulse landed on this player: a big kick the first time, smaller ones on top while it keeps coming. */
    private static void disrupt(float t, Vec3 dir) {
        boolean first = t - lastHit > 30;
        float dt = t - lastHit;
        shakeAtHit = Math.min(1.5f, shakeAtHit * (float) Math.exp(-dt / SHAKE_FADE) * .6f + (first ? 1.15f : .55f));
        fovAtHit = first ? 1 : .5f;
        flashAtHit = first ? 1 : .55f;
        lastHit = t;
        // Which side of the view the blast came from (for the colour split): the push's direction across the view.
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            Vec3 look = mc.player.getLookAngle(), right = new Vec3(-look.z, 0, look.x);
            side = (float) Mth.clamp(right.lengthSqr() < 1e-6 ? 0 : -dir.dot(right.normalize()), -1, 1);
        }
    }
    /** How long the shake takes to fade (ticks), the disruption (muffle, picture, ringing) after the last hit. */
    private static final float SHAKE_FADE = 4.5f, DISRUPT_FADE = 26;
    /** 0..1: how swamped this player is now (the picture, the muffle, the ringing). */
    static float disruption() {
        float dt = now() - lastHit;
        if (dt < 0 || dt > DISRUPT_FADE * 5) return 0;
        return (float) Math.exp(-dt / DISRUPT_FADE) * Mth.clamp(dt / .5f + .3f, 0, 1);
    }
    private static boolean shown() { return !FilmDirector.playing() && Minecraft.getInstance().player != null; }

    /** The low, heavy judder: strong side to side, a little up and down and a slight roll. Not the vanilla hurt shake. */
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e) {
        float t = now(), dt = t - lastHit;
        if (dt < 0 || dt > 40 || !shown()) return;
        float a = shakeAtHit * (float) Math.exp(-dt / SHAKE_FADE) * BatmanConfig.SHAKE.get().floatValue();
        if (a < .002f) return;
        // The first instant: a shove away from the blast, then the vibration.
        float shove = dt < 3 ? (1 - dt / 3) * side * 2.2f : 0;
        float yaw = a * (2.8f * Mth.sin(t * Mth.TWO_PI * 7 / 20) + 1f * Mth.sin(t * Mth.TWO_PI * 17 / 20 + 1.3f)) + shove * a;
        float pitch = a * .75f * Mth.sin(t * Mth.TWO_PI * 5 / 20 + .7f);
        float roll = a * 1.2f * Mth.sin(t * Mth.TWO_PI * 3.5f / 20 + 2);
        e.setYaw(e.getYaw() + yaw);
        e.setPitch(e.getPitch() + pitch);
        e.setRoll(e.getRoll() + roll);
    }
    /** A very short FOV punch outward on each hit (WOOOM). */
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e) {
        float dt = now() - lastHit;
        if (dt < 0 || dt > 12 || !shown()) return;
        float k = fovAtHit * (float) Math.exp(-dt / 2.2f) * Mth.clamp(dt / .6f, 0, 1) * BatmanConfig.SHAKE.get().floatValue();
        e.setFOV(e.getFOV() + 7 * k);
    }
    /** The picture: the post effect while swamped (before the hands and the HUD). */
    @SubscribeEvent public static void picture(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        float d = disruption();
        if (d < .01f || failed || !shown()) return;
        var mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (!ensureChain(mc, main)) return;
        float t = now(), dt = t - lastHit;
        uniform("Amount", Math.min(1, d * 1.1f));
        uniform("Wave", Mth.clamp(dt / 6, 0, 1));
        uniform("Flash", flashAtHit * (float) Math.exp(-Math.max(0, dt) / 1.5f));
        uniform("Time", (t % 2000) / 20f * Mth.TWO_PI);
        uniform("Side", side);
        try {
            chain.process(e.getPartialTick());
        } catch (Exception ex) {
            failed = true;
            LOG.warn("Batman sonic trap: the distortion effect failed; a plain vignette is used instead", ex);
        }
        main.bindWrite(false);
    }
    private static boolean ensureChain(Minecraft mc, RenderTarget main) {
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, EFFECT);
                chainW = chainH = -1;
                passes = findPasses(chain);
            }
            if (chainW != main.width || chainH != main.height) { chain.resize(main.width, main.height); chainW = main.width; chainH = main.height; }
            return true;
        } catch (Exception ex) {
            failed = true;
            LOG.warn("Batman sonic trap could not load its distortion effect; a plain vignette is used instead", ex);
            return false;
        }
    }
    @SuppressWarnings("unchecked")
    private static List<PostPass> findPasses(PostChain c) {
        for (Field f : PostChain.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                List<?> list = (List<?>) f.get(c);
                if (list != null && !list.isEmpty() && list.get(0) instanceof PostPass) return (List<PostPass>) list;
            } catch (Exception ignored) {}
        }
        return List.of();
    }
    private static void uniform(String name, float v) { for (PostPass p : passes) p.getEffect().safeGetUniform(name).set(v); }
    /** Without the post effect: a cold vignette pulse on the HUD. */
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post e) {
        if (!failed) return;
        float d = disruption();
        if (d < .01f || !shown()) return;
        var g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float flash = flashAtHit * (float) Math.exp(-Math.max(0, now() - lastHit) / 1.5f);
        int edge = (int) (Mth.clamp(d * .55f, 0, .55f) * 255) << 24 | 0x0a1018;
        int band = Math.max(8, h / 5);
        g.fillGradient(0, 0, w, band, edge, 0x0a1018);
        g.fillGradient(0, h - band, w, h, 0x0a1018, edge);
        for (int i = 0; i < 8; i++) {
            int a = (int) (Mth.clamp(d * .55f, 0, .55f) * 255 * (1 - i / 8f)) << 24 | 0x0a1018;
            int x = i * band / 8;
            g.fill(x, 0, x + band / 8, h, a);
            g.fill(w - x - band / 8, 0, w - x, h, a);
        }
        if (flash > .02f) g.fill(0, 0, w, h, (int) (flash * 40) << 24 | 0xe8f6ff);
    }
    /** Swamped hearing: other sounds come through quieter and a little lower for a moment (not the trap's own, not music). */
    @SubscribeEvent public static void muffle(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null || OURS.contains(s) || s instanceof TickableSoundInstance) return;
        float d = disruption();
        if (d < .05f) return;
        SoundSource src = s.getSource();
        if (src != SoundSource.BLOCKS && src != SoundSource.HOSTILE && src != SoundSource.NEUTRAL && src != SoundSource.PLAYERS
                && src != SoundSource.AMBIENT && src != SoundSource.WEATHER) return;
        ResourceLocation id = s.getLocation();
        if (id != null && SuperheroMod.MODID.equals(id.getNamespace()) && id.getPath().startsWith("batman.sonic")) return;
        e.setSound(new Muffled(s, 1 - .65f * d, 1 - .2f * d));
    }
    /** Another sound, quieter and lower (everything else as it was). */
    private static final class Muffled implements SoundInstance {
        private final SoundInstance s; private final float volume, pitch;
        Muffled(SoundInstance s, float volume, float pitch) { this.s = s; this.volume = volume; this.pitch = pitch; }
        @Override public ResourceLocation getLocation() { return s.getLocation(); }
        @Override public WeighedSoundEvents resolve(SoundManager m) { return s.resolve(m); }
        @Override public Sound getSound() { return s.getSound(); }
        @Override public SoundSource getSource() { return s.getSource(); }
        @Override public boolean isLooping() { return s.isLooping(); }
        @Override public boolean isRelative() { return s.isRelative(); }
        @Override public int getDelay() { return s.getDelay(); }
        @Override public float getVolume() { return s.getVolume() * volume; }
        @Override public float getPitch() { return s.getPitch() * pitch; }
        @Override public double getX() { return s.getX(); }
        @Override public double getY() { return s.getY(); }
        @Override public double getZ() { return s.getZ(); }
        @Override public SoundInstance.Attenuation getAttenuation() { return s.getAttenuation(); }
        public boolean canStartSilent() { return s.canStartSilent(); }
        public boolean canPlaySound() { return s.canPlaySound(); }
    }

    // ------------------------------------------------------------------ helpers
    /** Where an emitter's chamber face is now [face centre, facing, pitch axis] (world), or null before it is placed. */
    static Vec3[] face(SonicEmitterEntity e, float partial) {
        float age = e.age(partial);
        if (age < 0) return null;
        float broken = e.brokenAge(partial);
        SonicEmitterRenderer.Stage s = SonicEmitterRenderer.stage(e, broken >= 0 ? Math.max(0, age - broken) : age);
        float pivot = SonicEmitterRenderer.PIVOT_FOLDED + SonicEmitterRenderer.MAST_LIFT * s.mast;
        double y = -(1 - s.lift) * SonicEmitterRenderer.BURIED + s.jolt + pivot;
        float baseYaw = e.getYRot(), aimYaw = Mth.rotLerp(partial, e.aimYawO, e.aimYaw), aimPitch = Mth.lerp(partial, e.aimPitchO, e.aimPitch);
        float yaw = baseYaw + Mth.wrapDegrees(aimYaw - baseYaw) * s.fold;
        float elev = Mth.lerp(s.fold, Mth.HALF_PI, aimPitch * (broken >= 0 ? .3f : 1)) - SonicEmitterRenderer.droop(broken);
        Vec3 dir = direction(yaw, elev), axis = e.getPosition(partial).add(0, y, 0);
        return new Vec3[]{axis.add(dir.scale(.2)), dir, axis};
    }
    /** Bits of the real ground round an emitter's foot (block particles of the block under it). */
    private static void earth(SonicEmitterEntity e, int n, double spread, double up) {
        var level = Minecraft.getInstance().level;
        if (level == null || n <= 0) return;
        BlockPos pos = BlockPos.containing(e.getX(), e.getY() - .5, e.getZ());
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, r = (.35 + RANDOM.nextDouble() * .65) * spread;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), e.getX() + Math.cos(a) * r, e.getY() + .05, e.getZ() + Math.sin(a) * r,
                    Math.cos(a) * .08, up + RANDOM.nextDouble() * up, Math.sin(a) * .08);
        }
    }
    private static void puffs(Vec3 at, int n, float size, float t) {
        for (int i = 0; i < n; i++)
            MOTES.add(new Mote(M_DUST, at.add(rnd(.4), RANDOM.nextDouble() * .2, rnd(.4)), new Vec3(rnd(.05), .02 + RANDOM.nextDouble() * .03, rnd(.05)),
                    size * (.6f + RANDOM.nextFloat() * .5f), .04f, DUST, 26 + RANDOM.nextInt(16), t, .32f));
    }
    private static void sparks(Vec3 at, Vec3 dir, int n, float speed) {
        float t = now();
        for (int i = 0; i < n; i++)
            MOTES.add(new Mote(M_SPARK, at, dir.scale(speed * .5).add(rnd(speed), RANDOM.nextDouble() * speed, rnd(speed)), .02f, 0, i % 3 == 0 ? 0xffffff : SPARK, 5 + RANDOM.nextInt(7), t, 1));
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (SonicEmitterEntity.CLIENT.isEmpty() && PULSES.isEmpty() && MOTES.isEmpty() && RINGS.isEmpty() && SCARS.isEmpty()) return;
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
            for (SonicEmitterEntity em : SonicEmitterEntity.CLIENT) if (!em.isRemoved() && em.level() == mc.level && em.placed()) emitter(c, em, partial, time);
            for (Scar s : SCARS) {
                float k = (time - s.start()) / s.life();
                if (k < 0 || k > 1) continue;
                ground(c, s.at(), s.id(), 1 - k, 1, s.burnt());
            }
            for (Pulse pl : PULSES) pulse(c, pl, time);
            for (Ring r : RINGS) {
                float k = (time - r.start()) / r.life();
                if (k < 0 || k > 1) continue;
                float rad = Mth.lerp(1 - (1 - k) * (1 - k), r.from(), r.to());
                ring(c, r.at(), r.normal(), rad, r.width() * (1 - .5f * k), r.rgb(), r.alpha() * (1 - k) * (1 - k), r.light());
            }
            for (Mote m : MOTES) {
                float age = (time - m.start) / m.life;
                if (age < 0 || age > 1) continue;
                Vec3 at = m.pos.add(m.vel.scale(partial));
                switch (m.kind) {
                    case M_SPARK -> FilmFx.streak(c, at.subtract(m.vel.scale(1.4)), at, .02, m.rgb, 0, m.alpha * (1 - age), true);
                    case M_DUST -> FilmFx.puff(c, at, m.size + m.grow * (time - m.start), m.rgb, m.alpha * (1 - age) * Math.min(1, age * 5));
                    case M_CUBE -> {
                        p.pushPose();
                        p.translate(at.x, at.y, at.z);
                        p.mulPose(Axis.XP.rotation(m.spin + (time - m.start) * .4f));
                        p.mulPose(Axis.YP.rotation(m.spin * 2 + (time - m.start) * .3f));
                        float s = m.size;
                        FilmFx.cube(c, p.last().pose(), -s, -s * .6f, -s * .8f, s, s * .6f, s * .8f, m.rgb);
                        p.popPose();
                    }
                    case M_ARC -> arc(c, m.pos, m.pos.add(m.vel), m.rgb, m.alpha * (1 - age), time);
                    default -> FilmFx.glow(c, at, m.size * (1 + .5f * age), m.rgb, m.alpha * (1 - age));
                }
            }
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /** One emitter's torn ground and core light. */
    private static void emitter(FilmContext c, SonicEmitterEntity e, float partial, float time) {
        float age = e.age(partial);
        float broken = e.brokenAge(partial);
        Vec3 at = e.getPosition(partial);
        float open = SonicEmitterRenderer.k(age, BREAK_AT - 2, BREAK_AT + 4), cracks = SonicEmitterRenderer.k(age, 0, PREP);
        float close = broken >= 0 ? 1 : 1 - SonicEmitterRenderer.k(age, e.gone() - 6, e.gone());
        ground(c, at, e.getId(), close, open, false);
        crackLines(c, at, e.getId(), cracks * close);
        if (broken >= FAIL) return;
        Vec3[] face = face(e, partial);
        if (face == null) return;
        SonicEmitterRenderer.Stage s = SonicEmitterRenderer.stage(e, broken >= 0 ? Math.max(0, age - broken) : age);
        float glow = s.glow;
        if (broken >= 0) glow = SonicEmitterRenderer.Flicker.on(e.getId(), age) ? .8f : 0;
        if (glow <= .01f || s.lift < .95f) return;
        float since = time - e.pulsedAt, kick = since >= 0 && since < 5 ? (float) Math.exp(-since / 1.3f) : 0;
        Vec3 core = face[0].add(face[1].scale(.02));
        FilmFx.glow(c, core, .3 + .3 * kick, COLD, (.45f + .4f * kick) * glow);
        FilmFx.glow(c, core, .9 + .6 * kick, 0x9fd8ff, (.12f + .2f * kick) * glow);
        // A faint cone of light thrown forward while active (very faint: a hint, not a beam).
        if (broken < 0) FilmFx.streak(c, core, core.add(face[1].scale(1.6)), .22, COLD, .1f * glow, 0, true);
    }
    /** The dark, broken patch of earth round a foot (about a block across), fading with k; scorched after a blast. */
    private static void ground(FilmContext c, Vec3 at, int id, float k, float open, boolean burnt) {
        if (k <= .01f || open <= .01f) return;
        VertexConsumer v = c.buffers().getBuffer(FilmFx.SOFT);
        Matrix4f m = c.pose().last().pose();
        double y = at.y + .012;
        int n = 18;
        int dark = burnt ? 0x0a0a0a : EARTH;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            // A ragged edge: each step's radius from the emitter's id.
            double r0 = (.48 + .14 * SonicEmitterRenderer.hash(id * 13 + i)) * open, r1 = (.48 + .14 * SonicEmitterRenderer.hash(id * 13 + (i + 1) % n)) * open;
            double o0 = r0 + .38, o1 = r1 + .38;
            quad(v, m, at.x, y, at.z, at.x, y, at.z, at.x + Math.cos(a1) * r1, y, at.z + Math.sin(a1) * r1, at.x + Math.cos(a0) * r0, y, at.z + Math.sin(a0) * r0, dark, .92f * k, .92f * k);
            quad(v, m, at.x + Math.cos(a0) * r0, y, at.z + Math.sin(a0) * r0, at.x + Math.cos(a1) * r1, y, at.z + Math.sin(a1) * r1,
                    at.x + Math.cos(a1) * o1, y, at.z + Math.sin(a1) * o1, at.x + Math.cos(a0) * o0, y, at.z + Math.sin(a0) * o0, dark, .92f * k, 0);
        }
        // Loose dust thrown round the hole.
        FilmFx.ring(c, at.add(0, .014, 0), .95 * open, .3, burnt ? 0x202020 : 0x5e5040, .22f * k, false);
    }
    /** Cracks running out from the hole across the ground. */
    private static void crackLines(FilmContext c, Vec3 at, int id, float grow) {
        if (grow <= .01f) return;
        VertexConsumer v = c.buffers().getBuffer(FilmFx.SOFT);
        Matrix4f m = c.pose().last().pose();
        double y = at.y + .016;
        for (int i = 0; i < 7; i++) {
            double h1 = SonicEmitterRenderer.hash(id * 7 + i * 3), h2 = SonicEmitterRenderer.hash(id * 7 + i * 3 + 1);
            double a = i * Math.PI * 2 / 7 + h1 * .6, len = (.55 + h2 * .8) * grow, bend = (h2 - .5) * .5;
            double x0 = at.x + Math.cos(a) * .4, z0 = at.z + Math.sin(a) * .4;
            double am = a + bend * .5, xm = at.x + Math.cos(am) * (.4 + len * .55), zm = at.z + Math.sin(am) * (.4 + len * .55);
            double ae = a + bend, x1 = at.x + Math.cos(ae) * (.4 + len), z1 = at.z + Math.sin(ae) * (.4 + len);
            strip(v, m, x0, y, z0, xm, zm, .045, .03, EARTH, .8f, .6f);
            strip(v, m, xm, y, zm, x1, z1, .03, .0, EARTH, .6f, 0);
        }
    }
    private static void strip(VertexConsumer v, Matrix4f m, double x0, double y, double z0, double x1, double z1, double w0, double w1, int rgb, float a0, float a1) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return;
        double nx = -dz / len, nz = dx / len;
        quad(v, m, x0 - nx * w0, y, z0 - nz * w0, x0 + nx * w0, y, z0 + nz * w0, x1 + nx * w1, y, z1 + nz * w1, x1 - nx * w1, y, z1 - nz * w1, rgb, a0, a1);
    }
    /** A flat quad: the first two corners at alpha a (inner / start), the last two at b (outer / end). */
    private static void quad(VertexConsumer v, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2,
                             double x3, double y3, double z3, int rgb, float a, float b) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f;
        v.vertex(m, (float) x0, (float) y0, (float) z0).color(r, g, bl, Mth.clamp(a, 0, 1)).endVertex();
        v.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, bl, Mth.clamp(a, 0, 1)).endVertex();
        v.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, bl, Mth.clamp(b, 0, 1)).endVertex();
        v.vertex(m, (float) x3, (float) y3, (float) z3).color(r, g, bl, Mth.clamp(b, 0, 1)).endVertex();
    }
    /** A thin ring about an axis (light: added; else matter), fading to its inner and outer edges. */
    private static void ring(FilmContext c, Vec3 at, Vec3 normal, double radius, double width, int rgb, float alpha, boolean light) {
        if (alpha <= .003f || radius <= 0) return;
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 u = Math.abs(n.y) < .9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 w = n.cross(u);
        VertexConsumer v = c.buffers().getBuffer(light ? FilmFx.ADD : FilmFx.SOFT);
        Matrix4f m = c.pose().last().pose();
        double in = Math.max(0, radius - width), out = radius + width;
        int steps = 28;
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2 * i / steps, a1 = Math.PI * 2 * (i + 1) / steps;
            Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0))), d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
            for (int half = 0; half < 2; half++) {
                double e = half == 0 ? in : out;
                Vec3 p0 = at.add(d0.scale(e)), p1 = at.add(d1.scale(e)), q1 = at.add(d1.scale(radius)), q0 = at.add(d0.scale(radius));
                v.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, b, 0f).endVertex();
                v.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, 0f).endVertex();
                v.vertex(m, (float) q1.x, (float) q1.y, (float) q1.z).color(r, g, b, alpha).endVertex();
                v.vertex(m, (float) q0.x, (float) q0.y, (float) q0.z).color(r, g, b, alpha).endVertex();
            }
        }
    }
    /** A short jagged electric arc between two points, re-drawn jittering every frame. */
    private static void arc(FilmContext c, Vec3 a, Vec3 b, int rgb, float alpha, float time) {
        Vec3 prev = a;
        int seg = 6;
        long seed = (long) (time * 3) * 31 + (long) (a.x * 1000);
        Random r = new Random(seed);
        for (int i = 1; i <= seg; i++) {
            double u = i / (double) seg;
            Vec3 pt = a.lerp(b, u);
            if (i < seg) pt = pt.add((r.nextDouble() - .5) * .18, (r.nextDouble() - .5) * .18, (r.nextDouble() - .5) * .18);
            FilmFx.streak(c, prev, pt, .025, rgb, alpha, alpha, true);
            FilmFx.streak(c, prev, pt, .07, rgb, alpha * .3f, alpha * .3f, true);
            prev = pt;
        }
    }
    /**
     * A pulse in flight: a train of sound-wave fronts rolling out from the emitter, ring after ring (not a beam): each
     * front a thin ring about the line that widens the further it has come (a sound cone), wobbling a little, the
     * leading one brightest and the ones behind it fading; a faint haze of pushed air between them.
     */
    private static void pulse(FilmContext c, Pulse p, float time) {
        float u = (time - p.start) / p.travel;
        if (u < 0 || u > 1) return;
        Vec3 dir = p.to.subtract(p.from);
        double len = dir.length();
        if (len < 1e-3) return;
        dir = dir.scale(1 / len);
        double head = len * u;
        for (int k = 0; k < FRONTS; k++) {
            double d = head - k * FRONT_GAP;
            if (d < .2) break;
            Vec3 at = p.from.add(dir.scale(d));
            float fade = 1 - k / (float) FRONTS;
            double wobble = 1 + .08 * Math.sin(time * 1.7 + k * 1.9);
            double radius = (.22 + FRONT_SPREAD * d) * wobble;
            ring(c, at, dir, radius, .04 + .015 * k, k == 0 ? RING : COLD, (k == 0 ? .8f : .45f) * fade, true);
            if (k % 2 == 1) FilmFx.puff(c, at, radius * 1.1, HAZE, .05f * fade);
        }
        FilmFx.glow(c, p.from.add(dir.scale(head)), .4, 0xe8f8ff, .3f);
    }

    // ------------------------------------------------------------------ Batman: the remote and the move
    /** The red button's travel in the SONIC move (0..1): pressed at SONIC_PRESS, let go soon after. */
    static float press(float t) { return SonicEmitterRenderer.snap(t, SONIC_PRESS - 1.4f, SONIC_PRESS) * (1 - SonicEmitterRenderer.k(t, SONIC_PRESS + 2.5f, SONIC_PRESS + 4.5f)); }
    /** How far the remote is raised toward the chest in the SONIC move (0..1), for the first-person view. */
    static float raise(float t) { return SonicEmitterRenderer.k(t, 0, 5) * (1 - SonicEmitterRenderer.k(t, SONIC_TICKS - 6, SONIC_TICKS)); }

    /**
     * The SONIC move: controlled, professional. A slight crouch, the shoulders square, the left hand brings the remote up
     * to the stomach/chest with the top toward him, the thumb goes onto the red button and presses it (the hand dips a
     * touch with the force), a beat holding it with the eyes on the target (the layer keeps the look), then the hand
     * comes down again. The right hand stays a loose fist low at his side.
     */
    static Pose pose(Pose base, float t, float time) {
        Pose up = base.copy().set(CROUCH, 1.0f).add(SPINE_PITCH, .05f).add(CHEST_YAW, .1f).add(HEAD_PITCH, -.02f);
        up.leg(1, PantherMotion.LEG_X, -.18f).leg(0, PantherMotion.LEG_X, .12f).leg(1, PantherMotion.KNEE, .14f);
        up.arm(1, SH_FWD, .7f).arm(1, ARM_X, -.42f).arm(1, ARM_Y, -.48f).arm(1, ARM_Z, .04f).arm(1, ELBOW, 1.88f).arm(1, WRIST_X, .12f).arm(1, CURL, .72f);
        up.arm(0, ARM_Z, .26f).arm(0, ELBOW, .45f).arm(0, CURL, .9f);
        Pose ready = up.copy();
        ready.arm(1, CURL, .8f).arm(1, WRIST_X, .1f);
        Pose pressed = up.copy();
        pressed.arm(1, CURL, 1f).arm(1, WRIST_X, .24f).arm(1, ARM_X, -.4f).add(SPINE_PITCH, .01f);
        Pose hold = up.copy();
        hold.arm(1, CURL, .84f);
        Pose out = new Track(true).key(0, base).key(5, up).key(SONIC_PRESS - 1.5f, ready).key(SONIC_PRESS, pressed).key(SONIC_PRESS + 4, hold)
                .key(SONIC_TICKS - 5, hold).key(SONIC_TICKS, base).sample(t);
        // A faint tension tremor while the thumb is on the button.
        float tense = SonicEmitterRenderer.k(t, 5, SONIC_PRESS) * (1 - SonicEmitterRenderer.k(t, SONIC_PRESS + 2, SONIC_PRESS + 5));
        out.armAdd(1, ARM_X, .006f * tense * Mth.sin(time * 2.1f));
        return out;
    }
    /**
     * The remote's button press for this Batman now (0..1), or -1 when the remote is not in his left hand: only while the
     * sonic trap is the gadget picked, and only when the hand is free (standing, walking, the wheel, the SONIC move);
     * never while he punches, throws, grapples, rolls, aims or glides.
     */
    static float remote(BatmanClient.State s, int action, float t) {
        if (s == null || s.gadget != G_SONIC) return -1;
        if (action == SONIC) return press(t);
        if (s.gliding() || s.aiming()) return -1;
        return action == IDLE || action == WHEEL ? 0 : -1;
    }

    private static final float[] RED = {.72f, .05f, .045f}, RED_LIT = {1f, .2f, .14f};
    /** A box in the hand frame's pixels. */
    private static void px(PoseStack p, VertexConsumer v, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        SonicEmitterRenderer.box(p, v, light, x / 16, y / 16, z / 16, w / 16, h / 16, d / 16, c);
    }
    /**
     * The remote in the hand (BatmanBody.held's hand frame: fist centre near (0, 1.5, 0) px, the fingers curling toward
     * -s x, the thumb toward -z; side s = -1 right, 1 left; press 0..1): a small black military detonator held upright
     * in the fist, its top (toward the thumb) with ONE big red button in a guard ring, the safety cover flipped open, a
     * stubby antenna and a status light; gunmetal rails, grip grooves, end caps.
     */
    static void drawRemote(PoseStack p, VertexConsumer v, int light, int s, float press) {
        float cx = s * -1.55f, cy = 2.75f;
        float[] black = SonicEmitterRenderer.BLACK, blackHi = SonicEmitterRenderer.BLACK_HI, metal = SonicEmitterRenderer.METAL,
                metalDark = SonicEmitterRenderer.METAL_DARK, metalHi = SonicEmitterRenderer.METAL_HI;
        // The body, its grip grooves, the gunmetal side rails and end caps.
        px(p, v, light, cx, cy, -.3f, 1.3f, 1.8f, 4.6f, black);
        for (int i = 0; i < 3; i++) px(p, v, light, cx, cy, .35f + i * .5f, 1.36f, 1.86f, .12f, blackHi);
        px(p, v, light, cx, cy - .93f, -.4f, .9f, .08f, 4.0f, metalDark);
        px(p, v, light, cx, cy + .93f, -.4f, .9f, .08f, 4.0f, metalDark);
        px(p, v, light, cx, cy, 2.02f, 1.42f, 1.92f, .3f, metal);
        px(p, v, light, cx, cy, -2.64f, 1.44f, 1.94f, .18f, metal);
        // Screws on the outer face.
        float face = cx - s * .67f;
        px(p, v, light, face, cy - .55f, -1.9f, .06f, .2f, .2f, metalHi);
        px(p, v, light, face, cy + .55f, 1.5f, .06f, .2f, .2f, metalHi);
        px(p, v, light, face, cy, -.6f, .05f, 1.1f, .7f, metalDark);
        // The red button in its guard ring; it goes down when pressed.
        float bx = s * -1.25f, by = 2.25f, top = -2.73f;
        float height = .42f - .28f * Mth.clamp(press, 0, 1);
        for (int i = 0; i < 4; i++) {
            float ox = i == 0 ? -.5f : i == 1 ? .5f : 0, oy = i == 2 ? -.5f : i == 3 ? .5f : 0;
            px(p, v, light, bx + ox, by + oy, top - .14f, i < 2 ? .14f : 1.14f, i < 2 ? 1.14f : .14f, .28f, blackHi);
        }
        px(p, v, light, bx, by, top - height / 2, .78f, .78f, height, RED);
        if (press > .4f) SonicEmitterRenderer.lamp(p, v, bx / 16, by / 16, (top - height - .01f) / 16, .5f / 16, .5f / 16, .02f / 16, RED_LIT, .4f + .6f * press);
        // The safety cover, flipped open against the antenna side.
        px(p, v, light, bx, by + .66f, top - .45f, .9f, .07f, .8f, metalDark);
        // The stubby antenna and the status light.
        px(p, v, light, s * -1.95f, 3.3f, -3.15f, .32f, .32f, 1.0f, black);
        px(p, v, light, s * -1.95f, 3.3f, -3.7f, .4f, .4f, .14f, metalDark);
        SonicEmitterRenderer.lamp(p, v, s * -2.0f / 16, 3.05f / 16, -2.76f / 16, .22f / 16, .22f / 16, .03f / 16, RED_LIT, press > .2f ? 1 : .25f);
    }

    // ------------------------------------------------------------------ clean up
    private static void clear() {
        PULSES.clear(); MOTES.clear(); RINGS.clear(); SCARS.clear(); WATCH.clear();
        lastHit = -1000; shakeAtHit = 0;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        clear();
        SonicEmitterEntity.CLIENT.clear();
        if (chain != null) { chain.close(); chain = null; }
    }
}
