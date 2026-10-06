package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The thrown ice spear and the spikes, on the clients: the spear flies with the server's own physics (gravity that
 * weakens the more it was drawn back, a little drag) with a cold streak and snow behind it, sticks (in a block, or in a
 * body it then rides with), cracks over its last SPEAR_CRACK ticks and bursts. The spikes burst out of the ground in a
 * ring round where it struck, leaning outward, in a fast wave from the middle out; they stand, crack (with the sound),
 * their tops break off in shards and the stumps sink and melt away into frost (nothing pops). The giant mace's slam uses
 * the same spikes ({@link #spikes}).
 */
final class IcemanSpearFx {
    private IcemanSpearFx() {}

    private static final class Spear {
        final int id; final float charge, gravity;
        Vec3 pos, prev, vel, prevVel, dir, offset;
        int age, stuck = -1, body = -1; boolean held;
        Spear(int id, Vec3 pos, Vec3 vel, float charge) {
            this.id = id; this.pos = pos; prev = pos; this.vel = vel; prevVel = vel; this.charge = charge;
            gravity = .055f * (1 - .85f * charge);
            dir = vel.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : vel.normalize();
        }
        float size() { return 1 + .6f * charge; }
    }
    private static final class Spike { Vec3 base, dir; float h, r, delay; int seed; boolean milky; }
    private static final class Spikes { Vec3 at; float radius, start; Spike[] spikes; boolean cracked, broke; }
    private static final List<Spear> SPEARS = new ArrayList<>();
    private static final List<Spikes> SPIKES = new ArrayList<>();
    /** Spikes: they grow over GROW ticks, crack from CRACK, break at BREAK, the stumps melt until GONE. */
    private static final float GROW = 3, CRACK = 16, BREAK = 24, GONE = 44;

    static boolean idle() { return SPEARS.isEmpty() && SPIKES.isEmpty(); }
    static void clear() { SPEARS.clear(); SPIKES.clear(); }

    // ------------------------------------------------------------------ packets
    static void thrown(IcemanFxPacket p) {
        SPEARS.removeIf(s -> s.id == p.id());
        if (SPEARS.size() > 24) SPEARS.remove(0);
        SPEARS.add(new Spear(p.id(), p.pos(), p.dir(), Mth.clamp(p.power(), 0, 1)));
        // The release: a breath of cold off the hand.
        IceParticles.flash(p.pos(), .35f, .5f, 3);
        for (int i = 0, n = IceParticles.count(6, p.pos()); i < n; i++)
            IceParticles.snow(p.pos().add(IceParticles.jitter(.1)), p.dir().scale(.15).add(IceParticles.jitter(.05)), .03f, 14);
    }
    static void stuck(IcemanFxPacket p) {
        Spear s = null;
        for (Spear x : SPEARS) if (x.id == p.id()) s = x;
        if (s == null) {
            s = new Spear(p.id(), p.pos(), p.dir(), Mth.clamp(p.power(), 0, 1));
            SPEARS.add(s);
        }
        Vec3 d = p.dir().lengthSqr() < 1e-6 ? s.dir : p.dir().normalize();
        s.pos = p.pos(); s.prev = p.pos(); s.dir = d; s.stuck = 0; s.held = false;
        s.body = p.entity();
        var level = Minecraft.getInstance().level;
        Entity e = s.body >= 0 && level != null ? level.getEntity(s.body) : null;
        if (e != null) s.offset = p.pos().subtract(e.position()); else s.body = -1;
        // The strike: a burst of frost where it went in.
        Vec3 at = p.pos();
        IceParticles.flash(at, .5f, .7f, 4);
        for (int i = 0, n = IceParticles.count(9, at); i < n; i++) {
            Vec3 out = IceParticles.jitter(1).normalize().subtract(d.scale(.8));
            IceParticles.shard(at, out.scale(.12 + .1 * IceParticles.rand()).add(0, .08, 0), .04f + .05f * IceParticles.rand(), 25 + (int) (IceParticles.rand() * 20), i % 2 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        }
        for (int i = 0, n = IceParticles.count(16, at); i < n; i++)
            IceParticles.snow(at, IceParticles.jitter(.12).subtract(d.scale(.1)).add(0, .05, 0), .025f + .02f * IceParticles.rand(), 16 + (int) (IceParticles.rand() * 14));
        for (int i = 0, n = IceParticles.count(3, at); i < n; i++) IceParticles.mist(at.add(IceParticles.jitter(.15)), IceParticles.jitter(.02), .25f, .02f, .3f, 26);
        IcemanWeaponFx.shakeAt(at, .14f + .1f * s.charge, 10);
    }
    /** The ring of spikes bursting out of the ground round at (radius blocks); scale sizes them; for the spear and the giant slam. */
    static void spikes(Vec3 at, float radius, float scale) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (SPIKES.size() > 12) SPIKES.remove(0);
        Spikes sp = new Spikes();
        sp.at = at; sp.radius = Math.max(.8f, radius); sp.start = IcemanWeaponFx.gameTime();
        int n = Math.max(6, Math.min(26, IceParticles.count(Math.round(7 + sp.radius * 3.4f), at)));
        sp.spikes = new Spike[n];
        float hScale = Mth.sqrt(sp.radius / 3.2f) * scale;
        int seed = (int) (IceParticles.rand() * 10000);
        for (int i = 0; i < n; i++) {
            Spike s = new Spike();
            boolean inner = i % 3 == 0;
            float ang = (float) (i * Mth.TWO_PI / n + (IceMesh.hash(seed + i) - .5) * .5);
            float d = sp.radius * (inner ? .35f + .2f * (float) IceMesh.hash(seed + i * 3) : .62f + .38f * (float) IceMesh.hash(seed + i * 5));
            double x = at.x - Mth.sin(ang) * d, z = at.z + Mth.cos(ang) * d;
            s.base = new Vec3(x, groundY(level, x, at.y, z) - .1, z);
            float tilt = (inner ? .2f : .45f) + .25f * (float) IceMesh.hash(seed + i * 7);
            Vec3 out = new Vec3(-Mth.sin(ang), 0, Mth.cos(ang));
            s.dir = out.scale(Mth.sin(tilt)).add(0, Mth.cos(tilt), 0).normalize();
            s.h = (inner ? 1.5f : 1.0f) * (.75f + .5f * (float) IceMesh.hash(seed + i * 11)) * hScale;
            s.r = s.h * (.13f + .05f * (float) IceMesh.hash(seed + i * 13));
            s.delay = d / sp.radius * 2.6f;
            s.seed = seed + i * 17;
            s.milky = i % 4 == 1;
            sp.spikes[i] = s;
        }
        SPIKES.add(sp);
        // The ground bursting: a cold flash low down, snow and mist thrown out, a ring of frost running out.
        IceParticles.flash(at.add(0, .3, 0), .8f + .3f * sp.radius, .6f, 5);
        IceParticles.ring(at.add(0, .05, 0), sp.radius * 1.15f, .3f + .08f * sp.radius, IceParticles.COLD_LIGHT, .7f, 8, true);
        IceParticles.ring(at.add(0, .04, 0), sp.radius * 1.3f, .45f + .12f * sp.radius, 0xeef6ff, .45f, 14, false);
        for (int i = 0, k = IceParticles.count(Math.round(18 + sp.radius * 8), at); i < k; i++) {
            double a = IceParticles.rand() * Mth.TWO_PI, r = sp.radius * IceParticles.rand();
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            IceParticles.snow(at.add(o.scale(r)).add(0, .1, 0), o.scale(.08 + .1 * IceParticles.rand()).add(0, .12 + .14 * IceParticles.rand(), 0), .03f + .03f * IceParticles.rand(), 18 + (int) (IceParticles.rand() * 16));
        }
        for (int i = 0, k = IceParticles.count(Math.round(4 + sp.radius * 1.5f), at); i < k; i++) {
            double a = IceParticles.rand() * Mth.TWO_PI;
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            IceParticles.mist(at.add(o.scale(sp.radius * .6)).add(0, .3, 0), o.scale(.05).add(0, .01, 0), .4f + .1f * sp.radius, .03f, .3f, 34);
        }
        IcemanWeaponFx.shakeAt(at, .18f + .04f * sp.radius, sp.radius * 3);
    }

    // ------------------------------------------------------------------ every tick
    static void tick(Level level) {
        var mc = Minecraft.getInstance();
        for (int i = SPEARS.size() - 1; i >= 0; i--) {
            Spear s = SPEARS.get(i);
            s.prev = s.pos; s.prevVel = s.vel;
            s.age++;
            if (s.stuck >= 0) {
                s.stuck++;
                if (s.body >= 0) {
                    Entity e = level.getEntity(s.body);
                    if (e != null && e.isAlive()) s.pos = e.position().add(s.offset); else s.body = -1;
                }
                // The frost it sheds while it stands.
                if (s.stuck % 6 == 0) IceParticles.frostDust(grip(s, s.pos).lerp(s.pos, .5), Vec3.ZERO, .6f);
                if (s.stuck >= SPEAR_STUCK) { burst(s, s.pos); SPEARS.remove(i); }
                continue;
            }
            if (s.age > 95) { SPEARS.remove(i); continue; }
            if (s.held) continue;
            Vec3 next = s.pos.add(s.vel);
            // Our own copy stops at a wall too and waits there for the server's word (FX_SPEAR_STUCK).
            BlockHitResult hit = level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) { s.pos = hit.getLocation(); s.held = true; continue; }
            // The trail: snow off the shaft, now and then a breath of mist.
            Vec3 along = s.pos;
            for (int k = 0, n = IceParticles.count(3, along); k < n; k++)
                IceParticles.snow(along.lerp(next, IceParticles.rand()).add(IceParticles.jitter(.06)), s.vel.scale(.08).add(IceParticles.jitter(.02)), .025f, 12 + (int) (IceParticles.rand() * 10));
            if (s.age % 2 == 0) IceParticles.mist(along, s.vel.scale(.05), .16f, .02f, .2f, 18);
            s.pos = next;
            s.vel = s.vel.add(0, -s.gravity, 0).scale(.995);
            if (s.vel.lengthSqr() > 1e-6) s.dir = s.vel.normalize();
        }
        float now = IcemanWeaponFx.gameTime();
        for (int i = SPIKES.size() - 1; i >= 0; i--) {
            Spikes sp = SPIKES.get(i);
            float t = now - sp.start;
            if (!sp.cracked && t >= CRACK) {
                sp.cracked = true;
                level.playLocalSound(sp.at.x, sp.at.y, sp.at.z, ModSounds.ICEMAN_CRACK.get(), SoundSource.PLAYERS, .55f, 1.25f, false);
            }
            if (!sp.broke && t >= BREAK) {
                sp.broke = true;
                level.playLocalSound(sp.at.x, sp.at.y, sp.at.z, ModSounds.ICEMAN_SHATTER.get(), SoundSource.PLAYERS, .45f, 1.35f, false);
                // The tops break off: shards from the upper part of each spike, a short flash here and there.
                for (int k = 0; k < sp.spikes.length; k++) {
                    Spike s = sp.spikes[k];
                    Vec3 top = s.base.add(s.dir.scale(s.h * .72));
                    int n = IceParticles.count(3, top);
                    for (int m = 0; m < n; m++)
                        IceParticles.shard(top.add(s.dir.scale(s.h * .2 * IceParticles.rand())), s.dir.scale(.06).add(IceParticles.jitter(.08)).add(0, .06, 0),
                                s.r * (.5f + .5f * IceParticles.rand()), 30 + (int) (IceParticles.rand() * 25), m == 0 ? IceMesh.FRESH : s.milky ? IceMesh.MILKY : IceMesh.CLEAR);
                    if (k % 3 == 0) IceParticles.flash(top, .3f + s.h * .25f, .5f, 4);
                }
            }
            if (sp.broke && t < GONE && (int) t % 4 == 0) {
                Spike s = sp.spikes[(int) (IceParticles.rand() * sp.spikes.length)];
                IceParticles.mist(s.base.add(0, .2, 0), new Vec3(0, .006, 0), .3f, .015f, .2f, 24);
            }
            if (t > GONE + 2) SPIKES.remove(i);
        }
    }
    /** Where a stuck or flying spear's grip is, its point at (or past) tip. */
    private static Vec3 grip(Spear s, Vec3 at) {
        float len = IcemanArmory.length(W_SPEAR, s.size()) / 16f;
        return s.stuck >= 0 ? at.subtract(s.dir.scale(len - .3f)) : at;
    }
    /** The spear bursts: pieces all along it. */
    private static void burst(Spear s, Vec3 at) {
        Vec3 g = grip(s, at), tip = g.add(s.dir.scale(IcemanArmory.length(W_SPEAR, s.size()) / 16f));
        for (int i = 0; i < 6; i++) {
            Vec3 p = g.lerp(tip, i / 5.0);
            for (int k = 0, n = IceParticles.count(2, p); k < n; k++)
                IceParticles.shard(p, IceParticles.jitter(.08).add(0, .08, 0), .05f + .04f * IceParticles.rand(), 30 + (int) (IceParticles.rand() * 20), k == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
            IceParticles.snow(p, IceParticles.jitter(.06), .03f, 16);
        }
        IceParticles.flash(g.lerp(tip, .5), .5f, .6f, 4);
    }

    // ------------------------------------------------------------------ drawing
    private static final Vector3f AXIS = new Vector3f(0, 0, -1);
    /** The spears and the spikes, as ice (the stage's ice context). */
    static void drawIce(IceStage st, IceMesh.Ctx c) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float partial = st.partial;
        PoseStack pose = st.pose;
        for (Spear s : SPEARS) {
            Vec3 at = position(s, level, partial);
            Vec3 d = s.stuck >= 0 || s.held ? s.dir : s.prevVel.lerp(s.vel, partial);
            if (d.lengthSqr() < 1e-6) d = s.dir;
            d = d.normalize();
            float crack = s.stuck < 0 ? 0 : Mth.clamp((s.stuck + partial - (SPEAR_STUCK - SPEAR_CRACK)) / SPEAR_CRACK, 0, 1);
            float size = s.size();
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(new Quaternionf().rotationTo(AXIS, new Vector3f((float) d.x, (float) d.y, (float) d.z)));
            pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
            // Stuck: the point buried a little way in.
            if (s.stuck >= 0) pose.translate(0, 0, IcemanArmory.length(W_SPEAR, size) - 5);
            c.light = IceStage.light(at.subtract(d.scale(.5)));
            c.at(pose);
            IcemanArmory.inHand(c, pose, W_SPEAR, 1, size, crack, st.time);
            pose.popPose();
            c.at(pose);
        }
        float now = st.time;
        for (Spikes sp : SPIKES) {
            float t = now - sp.start;
            float crackK = Mth.clamp((t - CRACK) / (BREAK - CRACK), 0, 1);
            float melt = Mth.clamp((t - BREAK) / (GONE - BREAK), 0, 1);
            boolean far = st.far(sp.at);
            c.light = IceStage.light(sp.at.add(0, .5, 0));
            for (Spike s : sp.spikes) {
                float g = snap(t - s.delay, GROW);
                if (g <= 0) continue;
                float len, alpha = 1;
                Vec3 base = s.base;
                if (t < BREAK) len = s.h * g;
                else {
                    // The stump left after the top broke away, sinking and melting into frost.
                    len = s.h * .42f * (1 - melt);
                    base = base.subtract(0, .25 * melt, 0);
                    alpha = 1 - .6f * melt;
                }
                if (len <= .01f) continue;
                IceMesh.crystal(c, base, s.dir, len, s.r * (.45f + .55f * g) * (t < BREAK ? 1 : 1 - .4f * melt), far ? 4 : 6, s.seed,
                        s.milky ? IceMesh.MILKY : IceMesh.CLEAR, alpha, far ? 0 : .7f);
                if (!far && t < BREAK) {
                    // A milky heart inside the clear ones.
                    if (!s.milky) IceMesh.crystal(c, base, s.dir, len * .8f, s.r * .45f, 4, s.seed + 3, IceMesh.CORE, .8f, 0);
                    if (crackK > 0) {
                        Vec3 a = base.add(s.dir.scale(len * .15)), b = base.add(s.dir.scale(len * (.15 + .7 * crackK)));
                        Vec3 side = new Vec3(s.dir.z, 0, -s.dir.x).scale(s.r * .5);
                        Vec3 m = a.lerp(b, .5).add(side.scale(IceMesh.hash(s.seed) - .5));
                        float k = .5f + .5f * crackK;
                        IceMesh.line(c, a, m, .02f, .55f * k, .85f * k, k);
                        IceMesh.line(c, m, b, .018f, .55f * k, .85f * k, k);
                    }
                }
            }
        }
    }
    /** The light and matter round them: the flying spear's cold streak and glow; a frost patch under the spikes. */
    static void drawFx(IceStage st, FilmContext f) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float partial = st.partial;
        for (Spear s : SPEARS) {
            if (s.stuck >= 0) continue;
            Vec3 at = position(s, level, partial);
            Vec3 v = s.held ? Vec3.ZERO : s.prevVel.lerp(s.vel, partial);
            float speed = (float) v.length();
            float tipLen = IcemanArmory.length(W_SPEAR, s.size()) / 16f;
            Vec3 tip = at.add(s.dir.scale(tipLen));
            if (speed > .05f) {
                Vec3 tail = at.subtract(v.scale(1.6));
                FilmFx.streak(f, tail, tip, .1 + .06 * s.charge, IceParticles.COLD_LIGHT, 0, .55f, true);
                FilmFx.streak(f, tail, at, .16, 0xf2f8ff, 0, .25f, false);
            }
            FilmFx.glow(f, tip, .25 + .2 * s.charge, IceParticles.COLD_LIGHT, .45f + .2f * Mth.sin(st.time * 1.3f));
        }
        float now = st.time;
        for (Spikes sp : SPIKES) {
            float t = now - sp.start;
            float a = Mth.clamp(t / 3, 0, 1) * (1 - Mth.clamp((t - BREAK) / (GONE - BREAK), 0, 1));
            FilmFx.ring(f, sp.at.add(0, .03, 0), sp.radius * .55, sp.radius * .55, 0xeef6ff, .28f * a, false);
            if (t < 6) FilmFx.ring(f, sp.at.add(0, .06, 0), sp.radius * (.4 + .7 * t / 6), .25, IceParticles.COLD_LIGHT, .6f * (1 - t / 6), true);
        }
    }
    private static Vec3 position(Spear s, Level level, float partial) {
        if (s.stuck >= 0 && s.body >= 0) {
            Entity e = level.getEntity(s.body);
            if (e != null && e.isAlive()) return e.getPosition(partial).add(s.offset);
        }
        return s.prev.lerp(s.pos, partial);
    }

    /** The top of the ground under (or just over) a point, else the point's own height. */
    static double groundY(Level level, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y, z);
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos pos = base.above(dy);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty())
                return pos.getY() + shape.max(Direction.Axis.Y);
        }
        return y;
    }

    /** A fast start that settles over d ticks (0 before 0). */
    private static float snap(float t, float d) {
        if (t <= 0) return 0;
        float x = Math.min(1, t / d);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }
}
