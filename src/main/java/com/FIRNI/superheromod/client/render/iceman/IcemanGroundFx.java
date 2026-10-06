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

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * R, shattered ground, as everyone sees it. Every crack line the server runs (its front sent every tick, FX_GROUND) is
 * followed here by its id:
 * <ul>
 * <li>where it sets off (under his hands) a disc of rime spreads over the ground with short frost lines radiating;</li>
 * <li>the crack travels along the very path of the front, hugging the ground: a bright zigzag of glinting crack lines
 * with branches forking off, hot at the front and cooling behind it; the ground bulges along it, a low ridge of milky ice
 * pushing up behind the front with shards poking through, snow and frost dust thrown up at the front, as if something
 * were growing under the ground toward the target; the trail frosts over, then melts away after a few seconds;</li>
 * <li>where it arrives (FX_GROUND_ERUPT) a cluster of great ice spikes bursts out of the ground in a few ticks, tilted
 * outward, the biggest in the middle (bigger and more of them the more frost the target had; a deep-frozen target gets
 * the biggest), with a flash, a ring, shards and snow thrown up and a shake by distance; they stand GROUND_SPIKE_LIFE
 * ticks, then glinting cracks run up them and they break (the big ones shatter, the small ones sink and melt away).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanGroundFx {
    private IcemanGroundFx() {}

    /** One point of the crack's line on the ground, every STEP blocks: where, its sideways, when the front passed it. */
    private record Sample(Vec3 p, Vec3 side, Vec3 along, float reach, int seed) {}
    private static final class Spike {
        final Vec3 base, dir; final float len, radius, delay; final int seed; final boolean big;
        Spike(Vec3 base, Vec3 dir, float len, float radius, float delay, int seed) {
            this.base = base; this.dir = dir; this.len = len; this.radius = radius; this.delay = delay; this.seed = seed; big = len > 1.05f;
        }
    }
    private static final class Crack {
        final int id; float bonus = 0; int target = -1;
        final List<Vec3> points = new ArrayList<>();
        final List<Float> reach = new ArrayList<>();
        final List<Sample> samples = new ArrayList<>();
        float created, lastUpdate;
        /** How far along the path the last sample is (from its point). */
        double carry;
        boolean erupted; float eruptAt; Vec3 eruptPos; float eruptPower;
        final List<Spike> spikes = new ArrayList<>();
        int fired;
        Crack(int id) { this.id = id; }
        float lastReach() { return reach.isEmpty() ? created : reach.get(reach.size() - 1); }
    }
    private static final Map<Integer, Crack> CRACKS = new HashMap<>();
    private static final float STEP = .3f;
    /** The trail holds this long after the front passed, then melts over FADE (ticks). */
    private static final float HOLD = 55, FADE = 35;
    private static final int MAX_CRACKS = 16, MAX_POINTS = 160;
    private static Level lastLevel;

    private static float now() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime();
    }

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

    /** The spikes burst where the crack arrived (power = radius * strength). */
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
        float reach = Math.min(c.eruptPower * .5f, 1.35f * big);
        int seed = c.id * 977;
        var level = mc.level;
        // The middle one, nearly straight up.
        Vec3 tilt = new Vec3(IceMesh.hash(seed) - .5, 0, IceMesh.hash(seed + 1) - .5).scale(.18);
        c.spikes.add(new Spike(at.add(0, -.15, 0), new Vec3(0, 1, 0).add(tilt), (deep ? 3.4f : 2.1f) * big / (deep ? 1.7f : 1), .3f * big, 0, seed));
        int inner = 5 + Math.round(2 * frost) + (deep ? 2 : 0), outer = 6 + Math.round(3 * frost) + (deep ? 3 : 0);
        for (int ring = 0; ring < 2; ring++) {
            int n = ring == 0 ? inner : outer;
            for (int i = 0; i < n; i++) {
                int sd = seed + 10 + ring * 40 + i;
                float ang = (float) (Mth.TWO_PI * (i + .5 * IceMesh.hash(sd)) / n + ring * .4f);
                float r = (ring == 0 ? .5f : 1f) * reach * (.85f + .3f * (float) IceMesh.hash(sd * 3));
                Vec3 out = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
                Vec3 base = at.add(out.scale(r));
                double gy = groundY(level, base.x, base.y + 1, base.z, 4, base.y);
                base = new Vec3(base.x, (Math.abs(gy - at.y) < 1.5 ? gy : at.y) - .12, base.z);
                float lean = ring == 0 ? .35f + .2f * (float) IceMesh.hash(sd * 5) : .6f + .3f * (float) IceMesh.hash(sd * 5);
                Vec3 dir = out.scale(lean).add(0, 1, 0);
                float len = (ring == 0 ? 1.35f : .8f) * big * (.75f + .5f * (float) IceMesh.hash(sd * 7));
                float rad = (ring == 0 ? .19f : .12f) * big * (.8f + .4f * (float) IceMesh.hash(sd * 11));
                float delay = ring == 0 ? .5f + .7f * (float) IceMesh.hash(sd * 13) : 1.1f + 1.2f * (float) IceMesh.hash(sd * 13);
                c.spikes.add(new Spike(base, dir, len, rad, delay, sd));
            }
        }
        // The burst out of the ground.
        Vec3 mid = at.add(0, .6, 0);
        IceParticles.flash(mid, 1.4f * big, .95f, 6);
        IceParticles.ring(at.add(0, .06, 0), c.eruptPower * 1.1f, .45f, IceParticles.COLD_LIGHT, .9f, 8, true);
        IceParticles.ring(at.add(0, .04, 0), c.eruptPower * .9f, .8f, IceParticles.SNOW_RGB, .5f, 16, false);
        for (int i = 0, n = IceParticles.count(Math.round(18 * big), at); i < n; i++) {
            Vec3 d = new Vec3(IceParticles.gauss() * .4, 1, IceParticles.gauss() * .4).normalize();
            IceParticles.shard(at.add(d.x * reach * .7, .2, d.z * reach * .7), d.scale(.25 + .25 * IceParticles.rand()), .05f + .08f * IceParticles.rand(),
                    30 + (int) (IceParticles.rand() * 25), i % 3 == 0 ? IceMesh.FRESH : IceMesh.GLACIER);
        }
        for (int i = 0, n = IceParticles.count(Math.round(30 * big), at); i < n; i++) {
            Vec3 d = new Vec3(IceParticles.gauss() * .6, 1, IceParticles.gauss() * .6).normalize();
            IceParticles.snow(at.add(d.x * reach, .1, d.z * reach), d.scale(.2 + .3 * IceParticles.rand()), .03f + .03f * IceParticles.rand(), 20 + (int) (IceParticles.rand() * 16));
        }
        for (int i = 0, n = IceParticles.count(8, at); i < n; i++) {
            float ang = Mth.TWO_PI * i / 8f;
            IceParticles.mist(at.add(Mth.cos(ang) * reach * .8, .3, Mth.sin(ang) * reach * .8), new Vec3(Mth.cos(ang) * .05, .03, Mth.sin(ang) * .05), .55f, .04f, .35f, 32);
        }
        IcemanShellFx.shakeNear(at, .35f + .2f * big, 16);
        if (IcemanClient.isMe(c.target)) IcemanClient.shake(.25f);
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
            if (c.erupted) return trailGone && now > c.eruptAt + GROUND_SPIKE_LIFE + 14;
            return trailGone && now - c.lastUpdate > 40;
        });
        for (Crack c : CRACKS.values()) {
            // At the running front: snow and frost thrown up, a little mist, chips of ice breaking the surface.
            boolean running = !c.erupted && now - c.lastUpdate < 3 && !c.points.isEmpty();
            if (running) {
                Vec3 head = head(c, now);
                for (int i = 0, n = IceParticles.count(3, head); i < n; i++)
                    IceParticles.snow(head.add(IceParticles.jitter(.15)).add(0, .05, 0), new Vec3(IceParticles.gauss() * .05, .1 + .08 * IceParticles.rand(), IceParticles.gauss() * .05), .025f + .02f * IceParticles.rand(), 14 + (int) (IceParticles.rand() * 10));
                if (IceParticles.rand() < .5f) IceParticles.mist(head.add(0, .15, 0), new Vec3(0, .01, 0), .3f, .02f, .2f, 22);
                if (IceParticles.rand() < .45f)
                    IceParticles.shard(head.add(0, .05, 0), new Vec3(IceParticles.gauss() * .04, .12 + .06 * IceParticles.rand(), IceParticles.gauss() * .04), .035f + .03f * IceParticles.rand(), 18, IceMesh.MILKY);
            }
            if (!c.erupted) continue;
            float t = now - c.eruptAt;
            var level = mc.level;
            Vec3 at = c.eruptPos;
            // The spikes begin to crack...
            if ((c.fired & 1) == 0 && t >= GROUND_SPIKE_LIFE - 14) {
                c.fired |= 1;
                level.playLocalSound(at.x, at.y + 1, at.z, ModSounds.ICEMAN_CRACK.get(), SoundSource.PLAYERS, 1f, .75f, false);
            }
            // ...and break: the big ones shatter (pieces, a flash), the small ones sink and melt (mist).
            if ((c.fired & 2) == 0 && t >= GROUND_SPIKE_LIFE) {
                c.fired |= 2;
                level.playLocalSound(at.x, at.y + 1, at.z, ModSounds.ICEMAN_SHATTER.get(), SoundSource.PLAYERS, .9f, .9f, false);
                int shattered = 0;
                for (Spike s : c.spikes) {
                    Vec3 dir = s.dir.normalize();
                    if (s.big && shattered < 5) {
                        shattered++;
                        IceParticles.shatter(s.base.add(dir.scale(s.len * .45f)), dir.scale(.04).add(0, .03, 0), Math.min(1.4f, s.len * .4f), IceMesh.GLACIER);
                    } else IceParticles.mist(s.base.add(dir.scale(s.len * .3f)), new Vec3(0, .01, 0), .35f + s.len * .2f, .02f, .25f, 30);
                }
            }
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
                // A cold light running with the front.
                if (!k.erupted && now - k.lastUpdate < 3) FilmFx.glow(f, head(k, now).add(0, .15, 0), .9, IceParticles.COLD_LIGHT, .45f);
                // Under the hands as it sets off.
                float s0 = now - k.created;
                if (s0 < 14) FilmFx.glow(f, k.points.get(0).add(0, .2, 0), 1.3, IceParticles.COLD_LIGHT, .35f * (1 - s0 / 14));
                if (k.erupted) {
                    float t = now - k.eruptAt;
                    if (t < 10) FilmFx.glow(f, k.eruptPos.add(0, .8, 0), 1.6 + k.eruptPower * .3, IceParticles.COLD_LIGHT, .5f * (1 - t / 10));
                }
            }
        } finally {
            st.close();
        }
    }

    private static void drawIce(IceStage st, IceMesh.Ctx c, Crack k, float now) {
        if (k.points.isEmpty()) return;
        boolean far = st.far(k.points.get(k.points.size() - 1));
        float frost = k.bonus >= 2 ? 1.6f : 1 + .6f * Mth.clamp(k.bonus, 0, 1);
        // ---- the rime spreading from under his hands, frost lines radiating from it
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
                    float br = .45f * rimeFade * (.6f + .4f * (float) Math.exp(-s0 / 8));
                    IceMesh.line(c, a, b, .025f, .5f * br, .8f * br, br, 0, 0, 0);
                }
            }
        }
        // ---- the trail: the crack lines, the frost, the ridge bulging up behind the front with shards through it
        List<Sample> ss = k.samples;
        int n = ss.size();
        List<Vec3> ridge = new ArrayList<>();
        float[] radius = new float[n + 1];
        Vec3 prev = null;
        for (int i = 0; i < n; i++) {
            Sample s = ss.get(i);
            float a = now - s.reach;
            if (a < 0) break;
            float melt = 1 - Mth.clamp((a - HOLD) / FADE, 0, 1);
            if (melt <= 0) { prev = null; continue; }
            float hot = (float) Math.exp(-a / 6);
            float grow = FilmFx.ease(a / 4);
            c.light = IceStage.light(s.p.add(0, .5, 0));
            double off = (IceMesh.hash(s.seed) - .5) * .26;
            Vec3 p = s.p.add(s.side.scale(off)).add(0, .035, 0);
            // The crack's line: bright and wide at the front, cooling behind it.
            if (prev != null) {
                float br = (.4f + .9f * hot) * melt;
                IceMesh.line(c, prev, p, .03f + .035f * hot, .55f * br, .85f * br, br);
            }
            prev = p;
            // Branches forking off it.
            if (!far && IceMesh.hash(s.seed * 1.3) > .45) {
                float sgn = IceMesh.hash(s.seed * 2.1) > .5 ? 1 : -1;
                float ang = .55f + .6f * (float) IceMesh.hash(s.seed * 2.7);
                Vec3 d = s.along.scale(Mth.cos(ang)).add(s.side.scale(sgn * Mth.sin(ang)));
                float len = (.25f + .4f * (float) IceMesh.hash(s.seed * 3.3)) * grow * frost;
                Vec3 m = p.add(d.scale(len * .55));
                Vec3 q = m.add(d.add(s.side.scale(sgn * .5)).normalize().scale(len * .45));
                float br = (.25f + .6f * hot) * melt;
                IceMesh.line(c, p, m, .02f, .5f * br, .8f * br, br);
                IceMesh.line(c, m, q, .014f, .4f * br, .7f * br, .9f * br);
            }
            // Frost laid over the ground along it.
            if (i % 2 == 0) rime(c, s.p.add(s.side.scale(off * 1.5)), (.3f + .25f * (float) IceMesh.hash(s.seed * 4.1)) * grow * frost, .38f * melt, s.seed);
            // The bulge: a ridge of ice half out of the ground, rising behind the front and settling as it melts.
            float rise = rise(a / 3);
            ridge.add(s.p.add(s.side.scale(off * .6)).add(0, -.06, 0));
            radius[ridge.size() - 1] = (.09f + .05f * (float) IceMesh.hash(s.seed * 5.7)) * frost * rise * melt;
            // Shards breaking through the ridge.
            if (!far && IceMesh.hash(s.seed * 6.1) > .4) {
                float sgn = IceMesh.hash(s.seed * 6.9) > .5 ? 1 : -1;
                Vec3 dir = new Vec3(0, 1, 0).add(s.side.scale(sgn * (.25 + .35 * IceMesh.hash(s.seed * 7.3)))).add(s.along.scale(.2));
                float len = (.16f + .28f * (float) IceMesh.hash(s.seed * 8.9)) * frost * rise * melt;
                IceMesh.crystal(c, p.add(s.side.scale(sgn * .06)).add(0, -.08, 0), dir, len, .05f + .03f * frost, 4, s.seed,
                        IceMesh.hash(s.seed * 9.7) > .5 ? IceMesh.MILKY : IceMesh.CLEAR, .9f * melt, .4f * melt);
            }
        }
        // The ridge's tip at the moving front: tapered into the ground so it pushes on rather than ends.
        if (!k.erupted && !ridge.isEmpty() && now - k.lastUpdate < 3) {
            Vec3 h = head(k, now);
            if (h.distanceToSqr(ridge.get(ridge.size() - 1)) > .01) { ridge.add(h.add(0, -.12, 0)); radius[ridge.size() - 1] = .01f; }
        }
        if (ridge.size() >= 2) {
            c.light = IceStage.light(ridge.get(ridge.size() / 2).add(0, .5, 0));
            IceMesh.tube(c, ridge, radius, ridge.size(), far ? 4 : 5, k.id * 31, IceMesh.GLACIER, .85f);
        }
        // ---- the spikes
        if (k.erupted) drawSpikes(st, c, k, now, far);
    }

    /** Out of the ground fast with a little overshoot (0 .. ~1.06 .. 1). */
    private static float rise(float x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x) * (1 - x) + .12f * Mth.sin(Mth.PI * x) * x;
    }

    private static void drawSpikes(IceStage st, IceMesh.Ctx c, Crack k, float now, boolean far) {
        float t = now - k.eruptAt;
        float life = GROUND_SPIKE_LIFE;
        c.light = IceStage.light(k.eruptPos.add(0, 1, 0));
        // The frost round their feet.
        float around = 1 - Mth.clamp((t - life - 10) / 30, 0, 1);
        if (around > 0) rime(c, k.eruptPos, Math.min(k.eruptPower * .8f, 3f) * FilmFx.ease(t / 4), .55f * around, k.id * 23);
        float crack = Mth.clamp((t - (life - 14)) / 12, 0, 1);
        int shattered = 0;
        for (Spike s : k.spikes) {
            float g = rise((t - s.delay) / GROUND_ERUPT);
            if (g <= 0) continue;
            float len = s.len * g, r = s.radius * Math.min(1, .4f + .6f * g);
            Vec3 base = s.base;
            float alpha = 1;
            if (t >= life) {
                if (s.big && shattered < 5) { shattered++; continue; }
                // The small ones sink and melt away.
                float m = Mth.clamp((t - life) / 12, 0, 1);
                if (m >= 1) continue;
                len *= 1 - .7f * m; r *= 1 - .5f * m; alpha = 1 - m;
                base = base.add(0, -.35 * m * s.len, 0);
            }
            Vec3 dir = s.dir.normalize();
            IceMesh.crystal(c, base, dir, len, r, far ? 4 : 6, s.seed, IceMesh.GLACIER, .95f * alpha, .8f * alpha);
            if (!far) IceMesh.crystal(c, base.add(dir.scale(.05)), dir, len * .72f, r * .45f, 5, s.seed + 7, IceMesh.CORE, .85f * alpha, 0);
            // Ice heaved up round its foot.
            if (!far && s.big) {
                for (int i = 0; i < 3; i++) {
                    float ang = (float) (Mth.TWO_PI * (i + IceMesh.hash(s.seed * 3 + i)) / 3);
                    Vec3 at = base.add(Mth.cos(ang) * r * 1.4, .12, Mth.sin(ang) * r * 1.4);
                    IceMesh.shard(c, at, new Vec3(Mth.cos(ang) * .5, 1, Mth.sin(ang) * .5), r * .9f * Math.min(1, g), s.seed + i * 3, IceMesh.FROST, .9f * alpha);
                }
            }
            // The cracks running up them before they break.
            if (crack > 0 && t < life) {
                Vec3[] fr = IceMesh.frame(dir);
                for (int j = 0; j < (far ? 1 : 3); j++) {
                    float ang = (float) (Mth.TWO_PI * IceMesh.hash(s.seed * 7 + j));
                    Vec3 out = fr[0].scale(Mth.cos(ang)).add(fr[1].scale(Mth.sin(ang))).scale(r * .95);
                    Vec3 p0 = base.add(out).add(dir.scale(len * .05));
                    int segs = 4;
                    for (int q = 1; q <= segs; q++) {
                        float u = crack * q / segs;
                        float wob = (float) (IceMesh.hash(s.seed * 11 + j * 5 + q) - .5) * .6f;
                        Vec3 p1 = base.add(dir.scale(len * .8f * u)).add(out.scale(1 - .7 * u)).add(fr[0].scale(wob * r)).add(fr[1].scale(-wob * r * .5));
                        float br = .6f + .6f * crack;
                        IceMesh.line(c, p0, p1, Math.max(.012f, r * .07f), .6f * br, .85f * br, br);
                        p0 = p1;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ shared
    /**
     * A disc of rime lying on the ground (irregular, its rim feathered out into points), centre at, radius r. For the
     * shell's footing, the cracks' start and trail, the spikes' feet.
     */
    static void rime(IceMesh.Ctx c, Vec3 at, float r, float alpha, int seed) {
        if (r <= .01f || alpha <= .01f) return;
        int n = 11;
        float x = (float) at.x, y = (float) at.y + .018f + (seed % 7) * .0015f, z = (float) at.z;
        float[] px = new float[n], pz = new float[n];
        for (int i = 0; i < n; i++) {
            float ang = Mth.TWO_PI * i / n + seed * .37f;
            float j = .7f + .45f * (float) IceMesh.hash(seed * 13 + i);
            px[i] = x + Mth.cos(ang) * r * j; pz[i] = z + Mth.sin(ang) * r * j;
        }
        IceMesh.Mat frost = IceMesh.FROST;
        for (int i = 0; i < n; i++) {
            int k = (i + 1) % n;
            IceMesh.tri(c, x, y, z, px[i], y, pz[i], px[k], y, pz[k], frost, alpha * .55f);
            // Feathers of frost growing out of the rim.
            if (IceMesh.hash(seed * 7 + i) > .35) {
                float mx = (px[i] + px[k]) / 2, mz = (pz[i] + pz[k]) / 2;
                float tx = x + (mx - x) * 1.45f, tz = z + (mz - z) * 1.45f;
                IceMesh.tri(c, px[i], y, pz[i], px[k], y, pz[k], tx, y, tz, frost, alpha * .32f);
            }
        }
        // A cleaner, brighter patch in the middle.
        float ir = r * .45f;
        for (int i = 0; i < 6; i++) {
            float a0 = Mth.TWO_PI * i / 6 + seed, a1 = Mth.TWO_PI * (i + 1) / 6 + seed;
            IceMesh.tri(c, x, y + .004f, z, x + Mth.cos(a0) * ir, y + .004f, z + Mth.sin(a0) * ir, x + Mth.cos(a1) * ir, y + .004f, z + Mth.sin(a1) * ir, IceMesh.FRESH, alpha * .35f);
        }
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
