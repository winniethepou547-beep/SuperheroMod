package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * R, shattered ground, as everyone sees it: the ground itself freezing and bursting upward. Every crack line the
 * server runs (its front sent every tick, FX_GROUND) is followed here by its id:
 * <ul>
 * <li>where it sets off (under his hands) frost spreads over the ground with short frost lines radiating;</li>
 * <li>the crack travels along the very path of the front, hugging the ground: a zigzag of cold cracks with branches
 * forking off, glowing at the front (the ground about to break) and turning to white fracture lines behind it; frost
 * spreads along it and a ridge of ice crystals breaks up through the ground behind the front (leaning out to either
 * side, most of them small, now and then a big one, each grown on its own clock), as if the ice were growing under the
 * ground toward the target; snow, ice crystals and chips thrown up at the front; the trail melts after a few seconds;</li>
 * <li>where it arrives (FX_GROUND_ERUPT) the ground freezes solid and explodes upward: a great central cluster of ice,
 * clusters of different sizes and angles round it (some behind the target, some in front, some taller, some smaller),
 * single big crystals leaning out, smaller ones further out and broken slabs of frozen ground tilted up round their
 * feet; every formation grows in a few ticks on its own delay (bigger and more of them the more frost the target had;
 * a deep-frozen target gets the biggest); a cold plume, cold mist rolling out low, snow and large ice fragments thrown
 * up, the deep rumble of the growth, a shake by distance. They stand GROUND_SPIKE_LIFE ticks; cracks glow up them, then
 * they break one after another (the big ones fracture zone by zone into chunks and shards, the small ones sink and
 * melt into mist), the shards rain down, the frost hisses away.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanGroundFx {
    private IcemanGroundFx() {}

    /** One point of the crack's line on the ground, every STEP blocks: where, its sideways, when the front passed it. */
    private record Sample(Vec3 p, Vec3 side, Vec3 along, float reach, int seed) {}
    /**
     * One formation of the eruption: a cluster (size = its height, count crystals), a single crystal (size = length,
     * r = half width) or a slab of frozen ground heaved up (a squat crystal); base, the way it grows, growth delay (shares
     * of GROUND_ERUPT), how it breaks (BIG: fractures; else sinks and melts) and when (ticks after GROUND_SPIKE_LIFE).
     */
    private record Form(int kind, Vec3 base, Vec3 dir, float size, float r, int count, int seed, IceMesh.Mat mat, float delay, boolean big, float breakAfter) {}
    private static final int CLUSTER = 0, CRYSTAL = 1, SLAB = 2;
    private static final class Crack {
        final int id; float bonus = 0; int target = -1;
        final List<Vec3> points = new ArrayList<>();
        final List<Float> reach = new ArrayList<>();
        final List<Sample> samples = new ArrayList<>();
        float created, lastUpdate;
        /** How far along the path the last sample is (from its point). */
        double carry;
        boolean erupted; float eruptAt; Vec3 eruptPos; float eruptPower;
        final List<Form> forms = new ArrayList<>();
        boolean[] broken = new boolean[0];
        int fired;
        Crack(int id) { this.id = id; }
        float lastReach() { return reach.isEmpty() ? created : reach.get(reach.size() - 1); }
    }
    private static final Map<Integer, Crack> CRACKS = new HashMap<>();
    private static final float STEP = .3f;
    /** The trail holds this long after the front passed, then melts over FADE (ticks). */
    private static final float HOLD = 55, FADE = 35;
    /** The last formation breaks this long after GROUND_SPIKE_LIFE; they are gone this long after it (ticks). */
    private static final float BREAK_SPREAD = 12, GONE = 10;
    private static final int MAX_CRACKS = 16, MAX_POINTS = 160;
    private static Level lastLevel;

    // ------------------------------------------------------------------ the packets
    public static void receive(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.kind() == FX_GROUND) front(p);
        else if (p.kind() == FX_GROUND_ERUPT) erupt(p);
    }
    private static Crack crack(int id, float now) {
        Crack c = CRACKS.get(id);
        if (c == null) {
            if (CRACKS.size() >= MAX_CRACKS) {
                // The oldest goes (never more than a handful at once in play).
                int oldest = -1; float at = Float.MAX_VALUE;
                for (Crack o : CRACKS.values()) if (o.created < at) { at = o.created; oldest = o.id; }
                CRACKS.remove(oldest);
            }
            c = new Crack(id);
            c.created = now;
            CRACKS.put(id, c);
        }
        return c;
    }
    /** The crack's start (power = frost bonus) or its front moving on (power -1). */
    private static void front(IcemanFxPacket p) {
        float now = Minecraft.getInstance().level.getGameTime();
        Crack c = crack(p.id(), now);
        if (p.power() >= 0) { c.bonus = p.power(); c.target = p.entity(); }
        if (c.erupted || c.points.size() >= MAX_POINTS) return;
        Vec3 at = p.pos();
        if (!c.points.isEmpty() && c.points.get(c.points.size() - 1).distanceToSqr(at) < 1e-4) return;
        c.lastUpdate = now;
        // The front is drawn one tick behind the packets, so it can run smoothly between them.
        float reach = c.points.isEmpty() ? now : Math.max(now + 1, c.lastReach() + .2f);
        c.points.add(at);
        c.reach.add(reach);
        resample(c);
    }
    /** Lays samples every STEP blocks along the newest stretch of the path, each on the ground. */
    private static void resample(Crack c) {
        int n = c.points.size();
        var level = Minecraft.getInstance().level;
        if (n == 1) {
            Vec3 p = c.points.get(0);
            c.samples.add(new Sample(p, new Vec3(1, 0, 0), new Vec3(0, 0, 1), c.reach.get(0), c.id * 131));
            c.carry = 0;
            return;
        }
        Vec3 a = c.points.get(n - 2), b = c.points.get(n - 1);
        float ra = c.reach.get(n - 2), rb = c.reach.get(n - 1);
        Vec3 d = new Vec3(b.x - a.x, 0, b.z - a.z);
        double len = d.length();
        if (len < 1e-4) return;
        Vec3 along = d.scale(1 / len), side = new Vec3(-along.z, 0, along.x);
        double s = STEP - c.carry;
        while (s <= len) {
            double k = s / len;
            Vec3 p = a.lerp(b, k);
            double gy = groundY(level, p.x, p.y + 1, p.z, 4, p.y);
            if (Math.abs(gy - p.y) < 1.5) p = new Vec3(p.x, gy, p.z);
            c.samples.add(new Sample(p, side, along, Mth.lerp((float) k, ra, rb), c.id * 131 + c.samples.size()));
            s += STEP;
        }
        c.carry = len - (s - STEP);
    }

    /** The ground bursts where the crack arrived (power = radius * strength). */
    private static void erupt(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        float now = mc.level.getGameTime();
        Crack c = crack(p.id(), now);
        if (c.erupted) return;
        c.erupted = true;
        c.eruptAt = now;
        Vec3 at = p.pos();
        c.eruptPos = at;
        c.eruptPower = Math.max(1, p.power());
        boolean deep = c.bonus >= 2;
        float frost = deep ? 1 : Mth.clamp(c.bonus, 0, 1);
        // How big: from the server's strength and the target's frost; deep frozen, the biggest of all.
        float big = Math.max(Mth.clamp(c.eruptPower / 2.8f, 1, 1.7f), deep ? 1.7f : 1 + .6f * frost);
        float reach = Mth.clamp(c.eruptPower * .55f, 1.2f, 4f);
        int seed = c.id * 977;
        var level = mc.level;
        Random r = new Random(seed);
        // The great cluster in the middle, nearly upright.
        Vec3 up = new Vec3(0, 1, 0);
        c.forms.add(new Form(CLUSTER, at.add(0, -.25, 0), up.add((r.nextFloat() - .5) * .3, 0, (r.nextFloat() - .5) * .3), (1.5f + .5f * frost + (deep ? .4f : 0)) * Mth.clamp(c.eruptPower / 2.8f, .9f, 1.3f),
                0, 7, seed, IceMesh.GLACIER, 0, true, 4 + 3 * r.nextFloat()));
        // Clusters round it, of different sizes and angles: behind, in front, to the sides.
        int clusters = 4 + Math.round(2 * frost) + (deep ? 2 : 0);
        for (int i = 0; i < clusters; i++) {
            float ang = Mth.TWO_PI * (i + .6f * r.nextFloat()) / clusters;
            Vec3 out = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
            float tall = r.nextFloat() < .3f ? 1.4f : 1;
            c.forms.add(new Form(CLUSTER, ground(level, at.add(out.scale(reach * (.45f + .45f * r.nextFloat()))), at.y, -.15),
                    out.scale(.3 + .5 * r.nextFloat()).add(up), (.65f + .55f * r.nextFloat()) * big * tall, 0, 4 + r.nextInt(3),
                    seed + 11 + i, r.nextFloat() < .7f ? IceMesh.GLACIER : IceMesh.CLEAR, .1f + .5f * r.nextFloat(), true, BREAK_SPREAD * r.nextFloat()));
        }
        // Single big crystals leaning out between them.
        int singles = 6 + Math.round(3 * frost) + (deep ? 2 : 0);
        for (int i = 0; i < singles; i++) {
            float ang = r.nextFloat() * Mth.TWO_PI;
            Vec3 out = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
            float len = (.7f + 1.0f * r.nextFloat()) * big;
            Vec3 dir = out.scale(.35 + .75 * r.nextFloat()).add(up).add((r.nextFloat() - .5) * .3, 0, (r.nextFloat() - .5) * .3);
            c.forms.add(new Form(CRYSTAL, ground(level, at.add(out.scale(reach * (.25f + .75f * r.nextFloat()))), at.y, -.2), dir, len, len * (.12f + .07f * r.nextFloat()),
                    0, seed + 41 + i, r.nextFloat() < .6f ? IceMesh.CLEAR : IceMesh.GLACIER, .15f + .7f * r.nextFloat(), len > 1.1f, BREAK_SPREAD * r.nextFloat()));
        }
        // Small ones further out, nearly flat, and slabs of frozen ground heaved up round the feet.
        for (int i = 0; i < 10; i++) {
            float ang = r.nextFloat() * Mth.TWO_PI;
            Vec3 out = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
            float len = .25f + .35f * r.nextFloat();
            c.forms.add(new Form(CRYSTAL, ground(level, at.add(out.scale(reach * (.75f + .55f * r.nextFloat()))), at.y, -.08), out.scale(.6 + .8 * r.nextFloat()).add(up),
                    len, len * (.16f + .08f * r.nextFloat()), 0, seed + 71 + i, r.nextFloat() < .5f ? IceMesh.CLEAR : IceMesh.MILKY, .5f + .8f * r.nextFloat(), false, BREAK_SPREAD * r.nextFloat()));
        }
        for (int i = 0; i < 7; i++) {
            float ang = Mth.TWO_PI * (i + .5f * r.nextFloat()) / 7;
            Vec3 out = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
            c.forms.add(new Form(SLAB, ground(level, at.add(out.scale(reach * (.3f + .7f * r.nextFloat()))), at.y, -.06), out.scale(.6 + .6 * r.nextFloat()).add(up),
                    .1f + .06f * r.nextFloat(), (.26f + .18f * r.nextFloat()) * Math.min(1.3f, big), 0, seed + 91 + i, r.nextFloat() < .5f ? IceMesh.MILKY : IceMesh.GLACIER,
                    .05f * r.nextFloat(), false, BREAK_SPREAD * r.nextFloat()));
        }
        c.broken = new boolean[c.forms.size()];
        // The burst out of the ground: a cold plume, mist rolling out low, ice fragments and snow thrown up.
        Vec3 mid = at.add(0, .6, 0);
        IceParticles.flash(mid, .9f * big, .5f, 4);
        IceParticles.ring(at.add(0, .04, 0), c.eruptPower * .95f, .8f, IceParticles.SNOW_RGB, .5f, 16, false);
        IceParticles.cryo(at.add(0, .3, 0), up, 1.4f * big, .7f);
        IceParticles.coldMist(at.add(0, .05, 0), reach * 1.6f, 1.1f);
        for (int i = 0, n = IceParticles.count(Math.round(16 * big), at); i < n; i++) {
            Vec3 d = new Vec3(IceParticles.gauss() * .45, 1, IceParticles.gauss() * .45).normalize();
            boolean large = i % 4 == 0;
            IceParticles.shard(at.add(d.x * reach * .7, .2, d.z * reach * .7), d.scale(.22 + .25 * IceParticles.rand()), large ? .15f + .12f * IceParticles.rand() : .05f + .07f * IceParticles.rand(),
                    (large ? 45 : 30) + (int) (IceParticles.rand() * 25), i % 3 == 0 ? IceMesh.FRESH : IceMesh.GLACIER);
        }
        for (int i = 0, n = IceParticles.count(Math.round(28 * big), at); i < n; i++) {
            Vec3 d = new Vec3(IceParticles.gauss() * .6, 1, IceParticles.gauss() * .6).normalize();
            IceParticles.snow(at.add(d.x * reach, .1, d.z * reach), d.scale(.2 + .3 * IceParticles.rand()), .03f + .03f * IceParticles.rand(), 20 + (int) (IceParticles.rand() * 16));
        }
        level.playLocalSound(at.x, at.y + .5, at.z, ModSounds.ICEMAN_GROW_RUMBLE.get(), SoundSource.PLAYERS, 1.1f, .9f + .1f * (2 - big), false);
        IcemanShellFx.shakeNear(at, .35f + .2f * big, 16);
        if (IcemanClient.isMe(c.target)) IcemanClient.shake(.25f);
    }
    /** A point brought onto the ground under it (near the height y), sunk a little (sink, negative). */
    private static Vec3 ground(Level level, Vec3 p, double y, double sink) {
        double gy = groundY(level, p.x, y + 1, p.z, 4, y);
        return new Vec3(p.x, (Math.abs(gy - y) < 1.5 ? gy : y) + sink, p.z);
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { CRACKS.clear(); lastLevel = mc.level; }
        if (mc.level == null || mc.isPaused() || CRACKS.isEmpty()) return;
        float now = mc.level.getGameTime();
        CRACKS.values().removeIf(c -> {
            boolean trailGone = now > c.lastReach() + HOLD + FADE + 2;
            if (c.erupted) return trailGone && now > c.eruptAt + GROUND_SPIKE_LIFE + BREAK_SPREAD + GONE + 6;
            return trailGone && now - c.lastUpdate > 40;
        });
        for (Crack c : CRACKS.values()) {
            // At the running front: snow, ice crystals and frost thrown up, a little mist, chips of ice breaking the surface.
            boolean running = !c.erupted && now - c.lastUpdate < 3 && !c.points.isEmpty();
            if (running) {
                Vec3 head = head(c, now);
                for (int i = 0, n = IceParticles.count(3, head); i < n; i++)
                    IceParticles.snow(head.add(IceParticles.jitter(.15)).add(0, .05, 0), new Vec3(IceParticles.gauss() * .05, .1 + .08 * IceParticles.rand(), IceParticles.gauss() * .05), .025f + .02f * IceParticles.rand(), 14 + (int) (IceParticles.rand() * 10));
                for (int i = 0, n = IceParticles.count(2, head); i < n; i++)
                    IceParticles.crystalDust(head.add(IceParticles.jitter(.12)).add(0, .1, 0), new Vec3(IceParticles.gauss() * .03, .05 + .05 * IceParticles.rand(), IceParticles.gauss() * .03), .018f, (IceParticles.rand() - .5f) * .6f, 14 + (int) (IceParticles.rand() * 8));
                if (IceParticles.rand() < .5f) IceParticles.mist(head.add(0, .12, 0), new Vec3(0, .006, 0), .3f, .02f, .2f, 24);
                if (IceParticles.rand() < .45f)
                    IceParticles.shard(head.add(0, .05, 0), new Vec3(IceParticles.gauss() * .04, .12 + .06 * IceParticles.rand(), IceParticles.gauss() * .04), .04f + .04f * IceParticles.rand(), 18, IceMesh.GLACIER);
            }
            if (!c.erupted) continue;
            float t = now - c.eruptAt;
            var level = mc.level;
            Vec3 at = c.eruptPos;
            // The formations begin to crack...
            if ((c.fired & 1) == 0 && t >= GROUND_SPIKE_LIFE - 14) {
                c.fired |= 1;
                level.playLocalSound(at.x, at.y + 1, at.z, ModSounds.ICEMAN_CRACK.get(), SoundSource.PLAYERS, .9f, .75f, false);
            }
            if ((c.fired & 2) == 0 && t >= GROUND_SPIKE_LIFE - 5) {
                c.fired |= 2;
                level.playLocalSound(at.x, at.y + 1, at.z, ModSounds.ICEMAN_CRACK.get(), SoundSource.PLAYERS, 1.1f, .6f, false);
            }
            // ...and break one after another: the big ones fracture (the middle one zone by zone), the small ones sink and melt.
            boolean heavy = false;
            for (int i = 0; i < c.forms.size(); i++) {
                Form f = c.forms.get(i);
                if (c.broken[i] || t < GROUND_SPIKE_LIFE + f.breakAfter()) continue;
                c.broken[i] = true;
                Vec3 dir = f.dir().normalize();
                float h = f.kind() == CLUSTER ? f.size() : f.size() * .9f;
                Vec3 mid = f.base().add(dir.scale(h * .45));
                if (i == 0) {
                    IceParticles.breakApart(mid, dir, h, f.size() * .9f, Vec3.ZERO, 4, 8, IceMesh.GLACIER);
                    IceParticles.coldMist(f.base().add(0, .1, 0), 2.4f, 1);
                } else if (f.big()) {
                    IceParticles.shatter(mid, dir.scale(.03).add(0, .02, 0), Math.min(1.3f, h * .5f), IceMesh.GLACIER);
                    IceParticles.coldMist(f.base().add(0, .1, 0), .9f + h * .4f, .5f);
                    heavy = true;
                } else if (f.kind() != SLAB || i % 2 == 0) {
                    IceParticles.mist(mid, new Vec3(0, .01, 0), .3f + h * .3f, .02f, .22f, 30);
                    IceParticles.frostDust(mid, Vec3.ZERO, 1.2f);
                }
            }
            if (heavy && (c.fired & 4) == 0) {
                c.fired |= 4;
                level.playLocalSound(at.x, at.y + 1, at.z, ModSounds.ICEMAN_SHATTER.get(), SoundSource.PLAYERS, .8f, .95f, false);
            }
            // (The shards' rain and the frost's hiss after it are the middle one's breakApart.)
        }
    }
    /** Where the front is now (between the last two points, one tick behind the packets). */
    private static Vec3 head(Crack c, float now) {
        int n = c.points.size();
        if (n == 1) return c.points.get(0);
        for (int i = n - 1; i > 0; i--) {
            float r0 = c.reach.get(i - 1), r1 = c.reach.get(i);
            if (now >= r0) return c.points.get(i - 1).lerp(c.points.get(i), Mth.clamp((now - r0) / Math.max(.05f, r1 - r0), 0, 1));
        }
        return c.points.get(0);
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (CRACKS.isEmpty()) return;
        IceStage st = IceStage.open(e);
        if (st == null) return;
        try {
            float now = st.time;
            IceMesh.Ctx c = st.ice();
            for (Crack k : CRACKS.values()) drawIce(st, c, k, now);
            st.endIce();
            FilmContext f = st.fx();
            for (Crack k : CRACKS.values()) {
                if (k.points.isEmpty()) continue;
                // A faint cold light running with the front (the ground freezing), under the hands as it sets off.
                if (!k.erupted && now - k.lastUpdate < 3) FilmFx.glow(f, head(k, now).add(0, .15, 0), .8, IceParticles.COLD_LIGHT, .28f);
                float s0 = now - k.created;
                if (s0 < 14) FilmFx.glow(f, k.points.get(0).add(0, .2, 0), 1.2, IceParticles.COLD_LIGHT, .25f * (1 - s0 / 14));
            }
        } finally {
            st.close();
        }
    }

    private static void drawIce(IceStage st, IceMesh.Ctx c, Crack k, float now) {
        if (k.points.isEmpty()) return;
        boolean far = st.far(k.points.get(k.points.size() - 1));
        float frost = k.bonus >= 2 ? 1.6f : 1 + .6f * Mth.clamp(k.bonus, 0, 1);
        // ---- the frost spreading from under his hands, frost lines radiating from it
        Vec3 start = k.points.get(0);
        float s0 = now - k.created;
        float rimeFade = 1 - Mth.clamp((s0 - HOLD - 10) / FADE, 0, 1);
        if (rimeFade > 0) {
            c.light = IceStage.light(start.add(0, .5, 0));
            float r = 1.5f * frost * FilmFx.ease(s0 / 9f);
            rime(c, start, r, .6f * rimeFade, k.id * 17);
            if (!far) {
                for (int i = 0; i < 7; i++) {
                    float ang = (float) (Mth.TWO_PI * (i + IceMesh.hash(k.id * 7 + i) * .5) / 7);
                    float len = r * (1.1f + .5f * (float) IceMesh.hash(k.id * 3 + i));
                    Vec3 d = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
                    Vec3 a = start.add(d.scale(r * .3)).add(0, .03, 0), b = start.add(d.scale(len)).add(0, .03, 0);
                    float br = .4f * rimeFade * (.6f + .4f * (float) Math.exp(-s0 / 8));
                    IceMesh.line(c, a, b, .02f, .7f * br, .85f * br, br, 0, 0, 0);
                }
            }
        }
        // ---- the trail: the crack's lines, the frost, a ridge of crystals breaking up through the ground behind the front
        List<Sample> ss = k.samples;
        int n = ss.size();
        Vec3 prev = null;
        for (int i = 0; i < n; i++) {
            Sample s = ss.get(i);
            float a = now - s.reach;
            if (a < 0) break;
            float melt = 1 - Mth.clamp((a - HOLD) / FADE, 0, 1);
            if (melt <= 0) { prev = null; continue; }
            float hot = (float) Math.exp(-a / 5);
            c.light = IceStage.light(s.p.add(0, .5, 0));
            double off = (IceMesh.hash(s.seed) - .5) * .26;
            Vec3 p = s.p.add(s.side.scale(off)).add(0, .035, 0);
            // The crack's line: glowing at the front (the ground about to break), a white fracture line behind it.
            if (prev != null) {
                if (hot > .08f) IceMesh.vein(c, prev, p, .02f + .03f * hot, hot * melt);
                float br = .32f * melt;
                IceMesh.line(c, prev, p, .022f, .6f * br, .8f * br, br);
            }
            prev = p;
            // Branches forking off it.
            if (!far && IceMesh.hash(s.seed * 1.3) > .45) {
                float sgn = IceMesh.hash(s.seed * 2.1) > .5 ? 1 : -1;
                float ang = .55f + .6f * (float) IceMesh.hash(s.seed * 2.7);
                Vec3 d = s.along.scale(Mth.cos(ang)).add(s.side.scale(sgn * Mth.sin(ang)));
                float len = (.25f + .4f * (float) IceMesh.hash(s.seed * 3.3)) * FilmFx.ease(a / 4) * frost;
                Vec3 m = p.add(d.scale(len * .55));
                Vec3 q = m.add(d.add(s.side.scale(sgn * .5)).normalize().scale(len * .45));
                float br = (.22f + .5f * hot) * melt;
                IceMesh.line(c, p, m, .016f, .55f * br, .78f * br, br);
                IceMesh.line(c, m, q, .011f, .45f * br, .7f * br, .9f * br);
            }
            // Frost laid over the ground along it.
            if (i % 3 == 0) rime(c, s.p.add(s.side.scale(off * 1.5)), (.32f + .25f * (float) IceMesh.hash(s.seed * 4.1)) * FilmFx.ease(a / 4) * frost, .4f * melt, s.seed);
            // The ridge: a crystal breaking up out of the ground, leaning out to one side (each its own size and moment).
            if (far && (i & 1) == 1) continue;
            float hs = (float) IceMesh.hash(s.seed * 5.7);
            boolean bigOne = hs > .8f;
            float delay = (float) IceMesh.hash(s.seed * 6.3) * 2.5f;
            float g = IceGrowth.grow(a, .4f + delay, bigOne ? 4 : 2.6f);
            if (g <= .01f) continue;
            float sgn = IceMesh.hash(s.seed * 6.9) > .5 ? 1 : -1;
            float lean = .35f + .55f * (float) IceMesh.hash(s.seed * 7.3);
            Vec3 dir = new Vec3(0, 1, 0).add(s.side.scale(sgn * lean)).add(s.along.scale(.25 + .2 * IceMesh.hash(s.seed * 7.9)));
            float len = (bigOne ? .5f + .3f * hs : .16f + .2f * hs) * frost;
            // Melting: shrinking and sinking back into the ground.
            float shrink = .35f + .65f * melt;
            Vec3 b = p.add(s.side.scale(sgn * .06)).add(0, -.1 - .15 * (1 - melt), 0);
            IceMesh.Mat mat = IceMesh.hash(s.seed * 9.7) > .7 ? IceMesh.CLEAR : IceMesh.GLACIER;
            IceGrowth.crystal(c, b.x, b.y, b.z, dir.x, dir.y, dir.z, len * shrink, len * (bigOne ? .2f : .26f) * shrink, s.seed, mat, g, melt < 1 ? Math.min(1, melt * 1.5f) : 1);
            // A slab of frozen ground pushed up beside it now and then.
            if (!far && i % 4 == 2) {
                Vec3 sb = p.add(s.side.scale(-sgn * .12)).add(0, -.06 - .08 * (1 - melt), 0);
                Vec3 sd = new Vec3(0, 1, 0).add(s.side.scale(-sgn * .9));
                IceGrowth.crystal(c, sb.x, sb.y, sb.z, sd.x, sd.y, sd.z, .07f * shrink, .17f * frost * shrink, s.seed + 3, IceMesh.MILKY, IceGrowth.grow(a, .2f + delay * .5f, 2), Math.min(1, melt * 1.5f));
            }
        }
        // ---- the eruption
        if (k.erupted) drawForms(st, c, k, now, far);
    }

    /** The eruption's formations: growing on their own delays, standing, cracks glowing up them, then breaking one after another. */
    private static void drawForms(IceStage st, IceMesh.Ctx c, Crack k, float now, boolean far) {
        float t = now - k.eruptAt;
        float life = GROUND_SPIKE_LIFE;
        c.light = IceStage.light(k.eruptPos.add(0, 1, 0));
        // The frost on the ground round them.
        float around = 1 - Mth.clamp((t - life - BREAK_SPREAD) / 30, 0, 1);
        float reach = Mth.clamp(k.eruptPower * .55f, 1.2f, 4f);
        if (around > 0) rime(c, k.eruptPos, reach * 1.35f * FilmFx.ease(t / 4), .55f * around, k.id * 23);
        float te = t / GROUND_ERUPT;
        for (int i = 0; i < k.forms.size(); i++) {
            Form f = k.forms.get(i);
            if (far && !f.big() && (i & 1) == 1) continue;
            float bt = t - life - f.breakAfter();
            float alpha = 1, shrink = 1;
            Vec3 base = f.base();
            if (bt >= 0) {
                if (f.big()) {
                    // Fractured: the pieces fly as particles; what is left drops and goes in a few ticks.
                    float m = FilmFx.ease(bt / (i == 0 ? 7 : 4));
                    if (m >= 1) continue;
                    alpha = 1 - m; shrink = 1 - .25f * m;
                    base = base.add(0, -.25 * m, 0);
                } else {
                    // Sinking and melting away.
                    float m = FilmFx.ease(bt / GONE);
                    if (m >= 1) continue;
                    alpha = 1 - m; shrink = 1 - .6f * m;
                    base = base.add(0, -.4 * m * f.size(), 0);
                }
            }
            c.origin(f.base());
            Vec3 d = f.dir();
            switch (f.kind()) {
                case CLUSTER -> IceGrowth.cluster(c, base.x, base.y, base.z, d.x, d.y, d.z, f.size() * shrink, f.seed(), f.count(), f.mat(),
                        Mth.clamp((te - f.delay()) / 1.1f, 0, 1.25f), alpha);
                case CRYSTAL -> IceGrowth.crystal(c, base.x, base.y, base.z, d.x, d.y, d.z, f.size() * shrink, f.r() * shrink, f.seed(), f.mat(),
                        IceGrowth.grow(te, f.delay(), .9f), alpha);
                default -> IceGrowth.crystal(c, base.x, base.y, base.z, d.x, d.y, d.z, f.size() * shrink, f.r() * shrink, f.seed(), f.mat(),
                        IceGrowth.grow(te, f.delay(), .5f), alpha);
            }
            // Cracks glowing up the big ones before they break (on the side facing the eye).
            float pre = bt + 14;
            if (f.big() && pre > 0 && bt < 0 && !far) {
                float k1 = FilmFx.ease(pre / 12);
                Vec3 dn = d.normalize();
                float h = f.kind() == CLUSTER ? f.size() * 1.05f : f.size();
                float r = f.kind() == CLUSTER ? f.size() * .2f : f.r();
                Vec3 toEye = st.cam.subtract(base);
                Vec3 side = toEye.subtract(dn.scale(toEye.dot(dn)));
                if (side.lengthSqr() < 1e-6) continue;
                side = side.normalize();
                Vec3 across = dn.cross(side);
                for (int j = 0; j < (i == 0 ? 3 : 2); j++) {
                    float w0 = (IceGrowth.h(f.seed(), 200 + j) - .5f) * 1.2f;
                    Vec3 p0 = base.add(dn.scale(h * .08)).add(side.scale(r * 1.05)).add(across.scale(w0 * r));
                    for (int q = 1; q <= 4; q++) {
                        float uq = k1 * q / 4;
                        float wob = (IceGrowth.h(f.seed(), 210 + j * 8 + q) - .5f) * .8f;
                        float wid = r * (1 - .6f * uq);
                        Vec3 p1 = base.add(dn.scale(h * .8f * uq)).add(side.scale(wid * 1.05)).add(across.scale(Mth.clamp(w0 + wob, -.9f, .9f) * wid));
                        IceMesh.vein(c, p0, p1, Math.max(.014f, r * .06f), .5f + .6f * k1);
                        p0 = p1;
                    }
                }
            }
        }
        c.ox = c.oy = c.oz = 0;
    }

    // ------------------------------------------------------------------ shared
    /**
     * Frost lying on the ground, centre at, radius r: pixel frost, Minecraft's way. Square cells on the world's block grid
     * (a quarter block each), each row of the frosted patch one flat run of cells, its outline stepped and uneven; the
     * last cell of every row fades in as the radius reaches into it (it spreads cell by cell, never pops). For the
     * shell's footing, the cracks' start and trail, the spikes' feet, the slide's forming.
     */
    static void rime(IceMesh.Ctx c, Vec3 at, float r, float alpha, int seed) {
        if (r <= .01f || alpha <= .01f) return;
        final float cell = .25f;
        float y = (float) at.y + .018f + (seed & 7) * .0015f;
        // Static on the ground: the texture on the block grid.
        float ox = c.ox, oy = c.oy, oz = c.oz;
        c.ox = c.oy = c.oz = 0;
        IceMesh.Mat frost = IceMesh.FROST;
        int z0 = Mth.floor((at.z - r * 1.2f) / cell), z1 = Mth.floor((at.z + r * 1.2f) / cell);
        for (int zi = z0; zi <= z1; zi++) {
            float zc = (zi + .5f) * cell, dz = (float) (zc - at.z);
            // Each row reaches its own way out (an uneven, stepped outline).
            float j = .78f + .4f * (float) IceMesh.hash(seed * 13 + zi * 7.31);
            float rr = r * j;
            if (Math.abs(dz) >= rr) continue;
            float half = Mth.sqrt(rr * rr - dz * dz);
            float xa = (float) at.x - half, xb = (float) at.x + half;
            int ia = Mth.ceil(xa / cell), ib = Mth.floor(xb / cell);
            float za = zi * cell, zb = za + cell;
            if (ib > ia) IceMesh.quad(c, ia * cell, y, za, ib * cell, y, za, ib * cell, y, zb, ia * cell, y, zb, frost, alpha * .6f);
            // The cells at either end, fading in as the frost reaches into them.
            if (ib < ia) {
                // All inside one cell: that cell, as far as the frost covers it.
                float f = Mth.clamp((xb - xa) / cell, 0, 1);
                if (f > .02f) IceMesh.quad(c, ib * cell, y, za, ia * cell, y, za, ia * cell, y, zb, ib * cell, y, zb, frost, alpha * .6f * f);
                continue;
            }
            float fa = Mth.clamp((ia * cell - xa) / cell, 0, 1), fb = Mth.clamp((xb - ib * cell) / cell, 0, 1);
            if (fa > .02f) IceMesh.quad(c, (ia - 1) * cell, y, za, ia * cell, y, za, ia * cell, y, zb, (ia - 1) * cell, y, zb, frost, alpha * .6f * fa);
            if (fb > .02f) IceMesh.quad(c, ib * cell, y, za, (ib + 1) * cell, y, za, (ib + 1) * cell, y, zb, ib * cell, y, zb, frost, alpha * .6f * fb);
        }
        c.ox = ox; c.oy = oy; c.oz = oz;
    }
    /** The top of the ground under (or just over) a point, searching down; fallback when there is none. */
    static double groundY(Level level, double x, double y, double z, int down, double fallback) {
        if (level == null) return fallback;
        BlockPos base = BlockPos.containing(x, y, z);
        for (int dy = 1; dy >= -down; dy--) {
            BlockPos pos = base.above(dy);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty())
                return pos.getY() + shape.max(Direction.Axis.Y);
        }
        return fallback;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { CRACKS.clear(); lastLevel = null; }
}
