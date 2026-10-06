package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The two slides as everyone sees them (his own body is steered by IcemanSlideSteer).
 * <p>
 * The ice slide (SHIFT): the track of ice growing under him (IcemanTrack), snow spray and small shards thrown back off
 * his feet, cold mist trailing low behind (behind and low, so he stays in plain view), a sliding hiss (ICEMAN_SLIDE)
 * following him that rises with his speed; in his own view, thin wind streaks rushing past as the speed builds.
 * <p>
 * The sub-zero slide (CTRL): a whoosh of mist bursting off his feet as it starts (FX_DASH), the ground freezing under
 * him as he goes (thin patches of ice with rime round them and tiny crystals standing up, growing in, then melting),
 * spray thrown up to the sides; FX_DASH_HIT: a small cold flash, a frost burst and shards where it struck; FX_SLIDE_HIT:
 * frost bursting off a body the ice slide touched (the frost on the body and over their screen is FrostFx's).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanSlideFx {
    private IcemanSlideFx() {}

    // ------------------------------------------------------------------ from the server
    public static void receive(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        switch (p.kind()) {
            case FX_DASH -> {
                Entity e = mc.level.getEntity(p.entity());
                Vec3 at = e != null ? e.position() : p.pos();
                float yaw = p.power();
                if (IcemanClient.isMe(p.entity())) yaw = IcemanClient.dashYaw();
                Vec3 dir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
                Vec3 side = new Vec3(dir.z, 0, -dir.x);
                IceParticles.ring(at.add(0, .06, 0), 1.7f, .3f, IceParticles.MIST_RGB, .45f, 10, false);
                int n = IceParticles.count(7, at);
                for (int i = 0; i < n; i++) {
                    float sg = i % 2 == 0 ? 1 : -1;
                    Vec3 v = dir.scale(-.05 - .05 * IceParticles.rand()).add(side.scale(sg * (.04 + .06 * IceParticles.rand()))).add(0, .015, 0);
                    IceParticles.mist(at.add(IceParticles.jitter(.2)).add(0, .25, 0), v, .35f, .03f, .3f, 22 + (int) (IceParticles.rand() * 10));
                }
                int s = IceParticles.count(14, at);
                for (int i = 0; i < s; i++)
                    IceParticles.snow(at.add(IceParticles.jitter(.2)).add(0, .1, 0), dir.scale(-.1 * IceParticles.rand()).add(IceParticles.jitter(.08)).add(0, .1, 0), .025f, 16);
                if (IcemanClient.isMe(p.entity())) IcemanClient.kickFov(.55f);
            }
            case FX_DASH_HIT -> {
                Vec3 at = p.pos(), dir = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
                IceParticles.flash(at, .9f, .9f, 4);
                IceParticles.shatter(at, dir.scale(.12), .45f, IceMesh.FRESH);
                burst(at, dir, 16, .14f);
                IceParticles.ring(at.add(0, -.6, 0), 1.3f, .2f, IceParticles.COLD_LIGHT, .5f, 8, true);
                if (IcemanClient.isMe(p.id())) IcemanClient.shake(.25f);
                if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.4f);
            }
            case FX_SLIDE_HIT -> {
                Entity t = mc.level.getEntity(p.entity());
                Vec3 at = t != null ? t.getBoundingBox().getCenter() : p.pos();
                Vec3 dir = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
                IceParticles.flash(at, .6f, .6f, 4);
                burst(at, dir, 14, .1f);
                int k = IceParticles.count(4, at);
                for (int i = 0; i < k; i++)
                    IceParticles.shard(at.add(IceParticles.jitter(.25)), dir.scale(.08).add(IceParticles.jitter(.07)).add(0, .1, 0), .05f + .05f * IceParticles.rand(), 30, IceMesh.FRESH);
                if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.2f);
            }
            default -> {}
        }
    }
    /** Frost bursting off a point: flecks flung out (mostly along dir), a breath of mist. */
    private static void burst(Vec3 at, Vec3 dir, int flecks, float speed) {
        int n = IceParticles.count(flecks, at);
        for (int i = 0; i < n; i++) {
            Vec3 out = IceParticles.jitter(1).normalize().add(dir.scale(.6)).normalize();
            IceParticles.snow(at.add(IceParticles.jitter(.15)), out.scale(speed * (.5 + IceParticles.rand())).add(0, .05, 0), .025f + .02f * IceParticles.rand(), 16 + (int) (IceParticles.rand() * 10));
        }
        int m = IceParticles.count(4, at);
        for (int i = 0; i < m; i++) IceParticles.mist(at.add(IceParticles.jitter(.2)), dir.scale(.02).add(IceParticles.jitter(.01)), .3f, .03f, .3f, 24);
    }

    // ------------------------------------------------------------------ the frozen ground behind the sub-zero slide
    private static final class Patch {
        final Vec3 at; final float yaw, born, rx, rz; final int seed, light;
        Patch(Vec3 at, float yaw, float born, float rx, float rz, int seed, int light) { this.at = at; this.yaw = yaw; this.born = born; this.rx = rx; this.rz = rz; this.seed = seed; this.light = light; }
    }
    private static final List<Patch> PATCHES = new ArrayList<>();
    private static final int PATCH_LIFE = 50, PATCH_MELT = 30, MAX_PATCHES = 160;

    /** One Iceman sliding: how fast (smoothed), where his feet were, his sound. */
    private static final class Rider { float speed; Vec3 last, lastPatch; Loop loop; long seen; }
    private static final Map<Integer, Rider> RIDERS = new HashMap<>();
    private static ClientLevel lastLevel;

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { clear(); lastLevel = mc.level; }
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        Set<Integer> sliding = new HashSet<>();
        for (var en : IcemanClient.states().entrySet()) {
            int id = en.getKey();
            IcemanClient.State s = en.getValue();
            Entity who = mc.level.getEntity(id);
            if (who == null || !IcemanClient.isHero(who)) continue;
            boolean me = IcemanClient.isMe(id);
            boolean slide = me ? IcemanSlideSteer.riding() : s.action == SLIDE;
            boolean dash = me ? IcemanSlideSteer.dashing() : s.action == DASH;
            ride(mc.level, who, slide, dash, now);
            if (slide) sliding.add(id);
        }
        // His own slide predicted before any state of his has arrived.
        if (mc.player != null && IcemanClient.get(mc.player) == null && IcemanSlideSteer.riding()) {
            ride(mc.level, mc.player, true, false, now);
            sliding.add(mc.player.getId());
        }
        for (Iterator<Map.Entry<Integer, Rider>> it = RIDERS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (!sliding.contains(en.getKey())) IcemanTrack.stop(en.getKey());
            if (now - en.getValue().seen > 40) { IcemanTrack.stop(en.getKey()); it.remove(); }
        }
        IcemanTrack.tick();
        float t = now;
        PATCHES.removeIf(p -> t - p.born > PATCH_LIFE + PATCH_MELT);
    }
    private static void ride(Level level, Entity who, boolean slide, boolean dash, long now) {
        int id = who.getId();
        Rider r = RIDERS.get(id);
        if (r == null) {
            if (!slide && !dash) return;
            r = new Rider();
            RIDERS.put(id, r);
        }
        Vec3 pos = who.position();
        Vec3 vel = r.last == null ? Vec3.ZERO : pos.subtract(r.last);
        r.last = pos;
        if (!slide && !dash) { r.speed *= .7f; if (r.loop != null) r.loop.end(); return; }
        r.seen = now;
        double flat = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        r.speed += ((float) flat - r.speed) * .4f;
        Vec3 dir = flat < 1e-3 ? new Vec3(-Mth.sin(who.getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(who.getYRot() * Mth.DEG_TO_RAD)) : new Vec3(vel.x / flat, 0, vel.z / flat);
        Vec3 side = new Vec3(dir.z, 0, -dir.x);
        float k = Mth.clamp(r.speed / SLIDE_SPEED, 0, 1);
        if (slide) {
            IcemanTrack.grow(who);
            if (r.loop == null || r.loop.isStopped() || r.loop.ended) { r.loop = new Loop(id, who); Minecraft.getInstance().getSoundManager().play(r.loop); }
            // Snow spray and shards thrown back off his feet, mist trailing low behind.
            Vec3 feet = pos.subtract(dir.scale(.45)).add(0, .08, 0);
            int n = IceParticles.count(Math.round(1 + 3 * k), feet);
            for (int i = 0; i < n; i++) {
                float sg = IceParticles.rand() < .5f ? 1 : -1;
                Vec3 v = dir.scale(-.04 - .1 * IceParticles.rand() * k).add(side.scale(sg * (.03 + .07 * IceParticles.rand()))).add(0, .05 + .07 * IceParticles.rand(), 0);
                IceParticles.snow(feet.add(IceParticles.jitter(.12)), v.add(vel.scale(.3)), .02f + .025f * IceParticles.rand(), 12 + (int) (IceParticles.rand() * 10));
            }
            if (now % 3 == 0 && k > .3f && IceParticles.count(1, feet) > 0)
                IceParticles.shard(feet, dir.scale(-.08).add(IceParticles.jitter(.05)).add(0, .1, 0), .04f + .04f * IceParticles.rand(), 22, IceMesh.CLEAR);
            if (now % 2 == 0 && IceParticles.count(1, feet) > 0)
                IceParticles.mist(pos.subtract(dir.scale(.9)).add(0, .1, 0), dir.scale(-.02).add(vel.scale(.2)).add(0, .004, 0), .3f + .2f * k, .02f, .2f, 28);
        } else if (r.loop != null) r.loop.end();
        if (dash) {
            // The ground freezing under him, spray to the sides.
            Vec3 from = r.lastPatch == null ? pos : r.lastPatch;
            double d = from.distanceTo(pos);
            int steps = r.lastPatch == null ? 1 : Math.min(3, (int) Math.floor(d / .7));
            for (int i = 1; i <= steps; i++) {
                Vec3 at = r.lastPatch == null ? pos : from.lerp(pos, i / (double) steps);
                patch(level, at, (float) Math.atan2(-dir.x, dir.z), now - (steps - i) * .4f);
            }
            if (steps > 0) r.lastPatch = pos;
            int n = IceParticles.count(4, pos);
            for (int i = 0; i < n; i++) {
                float sg = i % 2 == 0 ? 1 : -1;
                IceParticles.snow(pos.add(side.scale(sg * .35)).add(0, .1, 0), side.scale(sg * (.08 + .08 * IceParticles.rand())).add(dir.scale(.05)).add(0, .09 + .06 * IceParticles.rand(), 0),
                        .025f + .02f * IceParticles.rand(), 14);
            }
            if (IceParticles.count(1, pos) > 0) IceParticles.mist(pos.subtract(dir.scale(.6)).add(0, .15, 0), dir.scale(-.03).add(0, .003, 0), .32f, .025f, .22f, 24);
        } else r.lastPatch = null;
    }
    private static void patch(Level level, Vec3 at, float yaw, float born) {
        float top = Float.NaN;
        BlockPos base = BlockPos.containing(at.x, at.y + .3, at.z);
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos p = base.below(dy);
            var state = level.getBlockState(p);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(level, p);
            if (shape.isEmpty()) continue;
            float y = (float) (p.getY() + shape.max(Direction.Axis.Y));
            if (y <= at.y + .4) { top = y; break; }
        }
        if (Float.isNaN(top)) return;
        if (PATCHES.size() >= MAX_PATCHES) PATCHES.remove(0);
        Vec3 c = new Vec3(at.x, top, at.z);
        PATCHES.add(new Patch(c, yaw, born, .5f + .15f * IceParticles.rand(), .85f + .3f * IceParticles.rand(), (int) (IceParticles.rand() * 1e6), IceStage.light(c.add(0, .5, 0))));
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        boolean wind = windOn();
        if (!IcemanTrack.any() && PATCHES.isEmpty() && !wind) return;
        IceStage st = IceStage.open(e);
        if (st == null) return;
        try {
            IceMesh.Ctx c = st.ice();
            IcemanTrack.draw(st, c);
            for (Patch p : PATCHES) patchIce(st, c, p);
            st.endIce();
            if (wind) wind(st, st.fx());
        } finally {
            st.close();
        }
    }
    private static final float[] PA = new float[8 * 3], PB = new float[8 * 3];
    /** A thin sheet of ice frozen onto the ground, rime round its edge, tiny crystals standing up; growing in, melting. */
    private static void patchIce(IceStage st, IceMesh.Ctx c, Patch p) {
        if (p.at.distanceToSqr(st.cam) > 80 * 80) return;
        float age = st.time - p.born;
        if (age < 0) return;
        float g = PantherMotion.snap(age, 0, 3);
        float m = (age - PATCH_LIFE) / PATCH_MELT;
        float shrink = 1 - PantherMotion.ease(m), alpha = 1 - PantherMotion.ease((m - .3f) / .7f);
        float s = g * (.35f + .65f * shrink);
        if (s <= .01f || alpha <= .01f) return;
        c.light = p.light;
        float cy = Mth.cos(p.yaw), sy = Mth.sin(p.yaw);
        // Rime under, a clear sheet over it (a hair higher).
        sheet(p, s * 1.15f, .012f, cy, sy, PA);
        fan(c, PA, p.at.add(0, .012, 0), IceMesh.FROST, .55f * alpha);
        sheet(p, s, .03f, cy, sy, PB);
        fan(c, PB, p.at.add(0, .03, 0), IceMesh.CLEAR, .9f * alpha);
        if (!st.far(p.at)) for (int i = 0; i < 2; i++) {
            double h = IceMesh.hash(p.seed + i * 3.7);
            int k = (int) (h * 8) % 8;
            Vec3 base = new Vec3(PB[k * 3], PB[k * 3 + 1], PB[k * 3 + 2]);
            Vec3 out = base.subtract(p.at).multiply(1, 0, 1);
            out = out.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : out.normalize().scale(.5).add(0, 1, 0).normalize();
            IceMesh.crystal(c, base, out, (.1f + .12f * (float) h) * s, .03f * s, 4, p.seed + i, IceMesh.CLEAR, alpha, .35f);
        }
    }
    private static void sheet(Patch p, float s, float lift, float cy, float sy, float[] out) {
        for (int i = 0; i < 8; i++) {
            float a = Mth.TWO_PI * i / 8;
            float j = .8f + .4f * (float) IceMesh.hash(p.seed + i * 1.31);
            float lx = Mth.cos(a) * p.rx * s * j, lz = Mth.sin(a) * p.rz * s * j;
            // Turned to lie along the slide's way (lz along it).
            out[i * 3] = (float) (p.at.x + lx * cy - lz * sy);
            out[i * 3 + 1] = (float) (p.at.y + lift);
            out[i * 3 + 2] = (float) (p.at.z + lx * sy + lz * cy);
        }
    }
    private static void fan(IceMesh.Ctx c, float[] ring, Vec3 mid, IceMesh.Mat mat, float a) {
        int n = ring.length / 3;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            IceMesh.tri(c, ring[i * 3], ring[i * 3 + 1], ring[i * 3 + 2], ring[j * 3], ring[j * 3 + 1], ring[j * 3 + 2], (float) mid.x, (float) mid.y, (float) mid.z, mat, a);
        }
    }

    // ------------------------------------------------------------------ the wind in his own view
    private static boolean windOn() {
        var mc = Minecraft.getInstance();
        return mc.player != null && (IcemanSlideSteer.riding() && IcemanSlideSteer.speed() > .3f || IcemanSlideSteer.dashing());
    }
    /** Thin streaks rushing past round the camera, more and brighter as the speed builds; kept off the middle of the view. */
    private static void wind(IceStage st, FilmContext f) {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null) return;
        Vec3 vel = new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
        double v = vel.length();
        if (v < .15) return;
        Vec3 dir = vel.scale(1 / v);
        float k = Mth.clamp((float) v / SLIDE_SPEED, 0, 1.4f);
        Vec3[] fr = IceMesh.frame(dir);
        int n = Math.round(6 + 10 * Math.min(1, k));
        for (int i = 0; i < n; i++) {
            double h1 = IceMesh.hash(i * 3.17), h2 = IceMesh.hash(i * 7.31), h3 = IceMesh.hash(i * 1.93);
            float ph = (float) ((st.time * (.05 + .03 * h3) * (1 + k) + h1) % 1.0);
            float ang = (float) (h2 * Mth.TWO_PI), rad = 1.3f + 2.2f * (float) h3;
            Vec3 at = st.cam.add(fr[0].scale(Mth.cos(ang) * rad)).add(fr[1].scale(Mth.sin(ang) * rad)).add(dir.scale(7 - 12 * ph));
            Vec3 tail = at.subtract(dir.scale(.8 + 1.6 * k));
            float a = .09f * Math.min(1, k) * Mth.sin(ph * Mth.PI);
            FilmFx.streak(f, tail, at, .012f, 0xeaf6ff, 0, a, false);
        }
    }

    // ------------------------------------------------------------------ the sound
    /** The ice hissing under him, following him, louder and higher with his speed. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final int id;
        private float vol;
        private boolean ended;
        Loop(int id, Entity e) {
            super(ModSounds.ICEMAN_SLIDE.get(), SoundSource.PLAYERS, RandomSource.create());
            this.id = id;
            looping = true; delay = 0; volume = .01f; pitch = .8f;
            x = e.getX(); y = e.getY(); z = e.getZ();
        }
        void end() { ended = true; }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            Rider r = RIDERS.get(id);
            if (e == null || r == null || r.loop != this) { stop(); return; }
            float k = Mth.clamp(r.speed / SLIDE_SPEED, 0, 1);
            float want = ended ? 0 : .2f + .55f * k;
            vol += (want - vol) * (want > vol ? .3f : .35f);
            if (ended && vol < .02f) { stop(); return; }
            volume = Math.max(.01f, vol);
            pitch = .8f + .4f * k;
            x = e.getX(); y = e.getY() + .2; z = e.getZ();
        }
    }

    // ------------------------------------------------------------------ cleaning up
    private static void clear() {
        RIDERS.clear();
        PATCHES.clear();
        IcemanTrack.clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); lastLevel = null; }
}
