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
 * The thrown ice spear and the ice that erupts where it strikes, on the clients.
 * <ul>
 * <li>The spear flies with the server's own physics (gravity that weakens the more it was drawn back, a little drag),
 * the air freezing behind it (a strong but controlled streak of cold vapour, ice crystals left in the air).</li>
 * <li>Stuck (in a block, or in a body it then rides with): a burst of frost where it went in, then it cracks and
 * shatters in stages into long shards (SHATTER_AT), the eruption already standing round it.</li>
 * <li>The eruption ({@link #erupt}, also the mace's slams): a natural cluster of ice crystals bursting out of the ground,
 * one big central growth (2 to 3 blocks for a full throw) with crystals interlocking round its foot, several medium
 * clusters leaning outward, small shards round about; each crystal its own size, lean and growth time (the middle first,
 * outward after), in a cloud of freezing vapour rolling out low. It stands, cracks (the cracks running up the big
 * crystals), breaks zone by zone (the big growth heavily, the others after it), the stumps sink and melt into frost.</li>
 * </ul>
 */
final class IcemanSpearFx {
    private IcemanSpearFx() {}

    private static final class Spear {
        final int id; final float charge, gravity;
        Vec3 pos, prev, vel, prevVel, dir, offset;
        int age, stuck = -1, body = -1; boolean held, shattered;
        Spear(int id, Vec3 pos, Vec3 vel, float charge) {
            this.id = id; this.pos = pos; prev = pos; this.vel = vel; prevVel = vel; this.charge = charge;
            gravity = .055f * (1 - .85f * charge);
            dir = vel.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : vel.normalize();
        }
        float size() { return 1 + .6f * charge; }
    }
    /** One crystal of an eruption: its foot (world), its way, length, half width, growth delay and time, when it breaks. */
    private static final class Piece {
        Vec3 base, dir; float len, r, delay, dur, breakAt; int seed, group; IceMesh.Mat mat; boolean broke;
    }
    private static final class Eruption { Vec3 at; float radius, start, gone; Piece[] pieces; boolean cracked; }
    private static final List<Spear> SPEARS = new ArrayList<>();
    private static final List<Eruption> ERUPTIONS = new ArrayList<>();
    /** The stuck spear cracks from SHATTER_AT - 8 and shatters at SHATTER_AT (ticks after it struck). */
    private static final int SHATTER_AT = 16;
    /** An eruption: its cracks open at CRACK, the pieces break from BREAK (zone by zone), the stumps are gone by MELT after their break. */
    private static final float CRACK = 32, BREAK = 40, MELT = 26;

    static boolean idle() { return SPEARS.isEmpty() && ERUPTIONS.isEmpty(); }
    static void clear() { SPEARS.clear(); ERUPTIONS.clear(); }

    // ------------------------------------------------------------------ packets
    static void thrown(IcemanFxPacket p) {
        SPEARS.removeIf(s -> s.id == p.id());
        if (SPEARS.size() > 24) SPEARS.remove(0);
        SPEARS.add(new Spear(p.id(), p.pos(), p.dir(), Mth.clamp(p.power(), 0, 1)));
        // The release: the cold torn off the hand with it.
        Vec3 d = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
        IceParticles.cryo(p.pos(), d, .5f + .4f * p.power(), .25f);
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
        // The strike: frost bursting back out of where it went in, broken ice thrown.
        Vec3 at = p.pos();
        IceParticles.flash(at, .4f + .2f * s.charge, .55f, 4);
        IceParticles.cryo(at, d.scale(-1).add(0, .4, 0), .8f + .5f * s.charge, .8f);
        for (int i = 0, n = IceParticles.count(8, at); i < n; i++) {
            Vec3 out = IceParticles.jitter(1).normalize().subtract(d.scale(.8));
            IceParticles.shard(at, out.scale(.12 + .1 * IceParticles.rand()).add(0, .08, 0), .05f + .06f * IceParticles.rand(), 25 + (int) (IceParticles.rand() * 20),
                    i % 2 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        }
        IcemanWeaponFx.shakeAt(at, .16f + .12f * s.charge, 12);
    }
    /** The eruption round a thrown spear (radius blocks); scale sizes it (kept for older callers: the same as erupt). */
    static void spikes(Vec3 at, float radius, float scale) {
        // The user's spec: the spear's impact must have real presence (one large spike, two medium formations, smaller
        // side crystals, thin icicle-like growths), much bigger than before.
        erupt(at, radius * 1.15f, (2.6f + .5f * radius) * scale, Math.round(4 + radius * .5f), Math.round(8 + radius * 1.6f));
    }
    /**
     * A cluster of ice crystals erupting out of the ground at (on the ground there) over radius: a central growth whose
     * biggest crystal is central long (blocks), mediums medium clusters, smalls small shards round about.
     */
    static void erupt(Vec3 at, float radius, float central, int mediums, int smalls) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (ERUPTIONS.size() > 10) ERUPTIONS.remove(0);
        Eruption er = new Eruption();
        er.at = at; er.radius = Math.max(.8f, radius); er.start = IcemanWeaponFx.gameTime();
        int seed = (int) (IceParticles.rand() * 100000);
        mediums = Math.max(2, Math.min(mediums, IceParticles.count(mediums, at)));
        smalls = Math.max(3, Math.min(smalls, IceParticles.count(smalls, at)));
        List<Piece> list = new ArrayList<>();
        // The central growth: a squat foot, the big crystal a little off upright, crystals interlocking round it.
        float lean = (IceGrowth.h(seed, 1) - .5f) * .35f, lean2 = (IceGrowth.h(seed, 2) - .5f) * .35f;
        list.add(piece(level, at, 0, 0, new Vec3(lean * .5, 1, lean2 * .5), central * .36f, central * .34f, seed + 1, 0, 0, 3, IceMesh.GLACIER));
        list.add(piece(level, at, 0, 0, new Vec3(lean, 1, lean2), central, central * .19f, seed + 2, 0, .5f, 7, IceMesh.CLEAR));
        int around = 4;
        for (int i = 0; i < around; i++) {
            float a = i * Mth.TWO_PI / around + (IceGrowth.h(seed, 10 + i) - .5f) * 1.2f, tilt = .35f + .5f * IceGrowth.h(seed, 20 + i);
            Vec3 d = new Vec3(-Mth.sin(a) * Mth.sin(tilt), Mth.cos(tilt), Mth.cos(a) * Mth.sin(tilt));
            float len = central * (.35f + .4f * IceGrowth.h(seed, 30 + i));
            list.add(piece(level, at, -Mth.sin(a) * central * .12f, Mth.cos(a) * central * .12f, d, len, len * .2f, seed + 40 + i, 0, 1 + 2.5f * IceGrowth.h(seed, 40 + i), 5 + 3 * IceGrowth.h(seed, 50 + i),
                    i % 3 == 0 ? IceMesh.MILKY : IceMesh.CLEAR));
        }
        // Two medium FORMATIONS on their own sides (never opposite, never alike): a squat foot and three or four
        // crystals each, a little later than the centre.
        float fa = IceGrowth.h(seed, 300) * Mth.TWO_PI;
        for (int f = 0; f < 2; f++) {
            float a = fa + (f == 0 ? 0 : 1.9f + 1.1f * IceGrowth.h(seed, 301));
            float dist = er.radius * (.42f + .2f * IceGrowth.h(seed, 302 + f));
            float x = -Mth.sin(a) * dist, z = Mth.cos(a) * dist;
            float size = central * (f == 0 ? .58f : .44f) * (.9f + .2f * IceGrowth.h(seed, 304 + f));
            float delay = 1 + 3 * dist / er.radius;
            list.add(piece(level, at, x, z, new Vec3(-Mth.sin(a) * .25, 1, Mth.cos(a) * .25), size * .3f, size * .32f, seed + 310 + f, 1, delay, 3, IceMesh.GLACIER));
            int n = 3 + (int) (IceGrowth.h(seed, 306 + f) * 1.99f);
            for (int k = 0; k < n; k++) {
                float aa = a + (k == 0 ? 0 : (IceGrowth.h(seed, 320 + f * 9 + k) - .5f) * 2.6f);
                float tilt = k == 0 ? .25f + .2f * IceGrowth.h(seed, 330 + f) : .45f + .6f * IceGrowth.h(seed, 340 + f * 9 + k);
                Vec3 d = new Vec3(-Mth.sin(aa) * Mth.sin(tilt), Mth.cos(tilt), Mth.cos(aa) * Mth.sin(tilt));
                float len = size * (k == 0 ? 1 : .35f + .4f * IceGrowth.h(seed, 350 + f * 9 + k));
                list.add(piece(level, at, x, z, d, len, len * (.17f + .06f * IceGrowth.h(seed, 360 + k)), seed + 370 + f * 9 + k, 1, delay + .6f + k * .9f,
                        4 + 3 * IceGrowth.h(seed, 380 + f * 9 + k), IceGrowth.h(seed, 390 + f * 9 + k) < .3f ? IceMesh.MILKY : IceMesh.CLEAR));
            }
        }
        // Thin icicle-like needles pushed out low from the feet of the growths, each its own length and way.
        int needles = Math.max(4, IceParticles.count(Math.round(6 + er.radius * 1.5f), at));
        for (int i = 0; i < needles; i++) {
            float a = IceGrowth.h(seed, 400 + i) * Mth.TWO_PI, dist = er.radius * (.08f + .45f * IceGrowth.h(seed, 410 + i));
            float tilt = .75f + .6f * IceGrowth.h(seed, 420 + i), aa = a + (IceGrowth.h(seed, 430 + i) - .5f) * .8f;
            Vec3 d = new Vec3(-Mth.sin(aa) * Mth.sin(tilt), Mth.cos(tilt), Mth.cos(aa) * Mth.sin(tilt));
            float len = Math.min(1.1f, central * (.12f + .2f * IceGrowth.h(seed, 440 + i)));
            list.add(piece(level, at, -Mth.sin(a) * dist, Mth.cos(a) * dist, d, len, len * .065f, seed + 450 + i, 2, 2 + 5 * IceGrowth.h(seed, 460 + i), 3 + 2 * IceGrowth.h(seed, 470 + i),
                    IceGrowth.h(seed, 480 + i) < .4f ? IceMesh.FRESH : IceMesh.CLEAR));
        }
        // Smaller side crystals in pairs, leaning outward, the later the further out.
        for (int i = 0; i < mediums; i++) {
            float a = i * Mth.TWO_PI / mediums + (IceGrowth.h(seed, 60 + i) - .5f) * .9f;
            float dist = er.radius * (.35f + .35f * IceGrowth.h(seed, 70 + i));
            float x = -Mth.sin(a) * dist, z = Mth.cos(a) * dist;
            float size = central * (.18f + .16f * IceGrowth.h(seed, 80 + i));
            float delay = 1.5f + 5 * dist / er.radius;
            for (int k = 0; k < 2; k++) {
                float tilt = (.3f + .45f * IceGrowth.h(seed, 90 + i * 3 + k)) * (k == 0 ? 1 : 1.5f), aa = a + (k == 0 ? 0 : (IceGrowth.h(seed, 100 + i) - .5f) * 1.4f);
                Vec3 d = new Vec3(-Mth.sin(aa) * Mth.sin(tilt), Mth.cos(tilt), Mth.cos(aa) * Mth.sin(tilt));
                float len = size * (k == 0 ? 1 : .55f);
                list.add(piece(level, at, x, z, d, len, len * .2f, seed + 110 + i * 3 + k, 1, delay + k * 1.5f, 5 + 3 * IceGrowth.h(seed, 120 + i),
                        IceGrowth.h(seed, 130 + i * 2 + k) < .25f ? IceMesh.GLACIER : IceGrowth.h(seed, 140 + i * 2 + k) < .3f ? IceMesh.MILKY : IceMesh.CLEAR));
            }
        }
        // Small shards round about, every one its own way.
        for (int i = 0; i < smalls; i++) {
            float a = IceGrowth.h(seed, 200 + i) * Mth.TWO_PI, dist = er.radius * (.45f + .6f * IceGrowth.h(seed, 210 + i));
            float tilt = .2f + .9f * IceGrowth.h(seed, 220 + i), aa = a + (IceGrowth.h(seed, 230 + i) - .5f) * 1.5f;
            Vec3 d = new Vec3(-Mth.sin(aa) * Mth.sin(tilt), Mth.cos(tilt), Mth.cos(aa) * Mth.sin(tilt));
            float len = Math.min(.6f, central * (.1f + .14f * IceGrowth.h(seed, 240 + i)));
            list.add(piece(level, at, -Mth.sin(a) * dist, Mth.cos(a) * dist, d, len, len * .22f, seed + 250 + i, 2, 3 + 6 * dist / er.radius, 3 + 3 * IceGrowth.h(seed, 260 + i),
                    i % 4 == 0 ? IceMesh.MILKY : IceMesh.CLEAR));
        }
        // When each breaks: the big growth first, then the mediums one by one, the small ones melting with them.
        for (Piece pc : list) pc.breakAt = BREAK + (pc.group == 0 ? IceGrowth.h(pc.seed, 7) * 2 : pc.group == 1 ? 2 + 8 * IceGrowth.h(pc.seed, 8) : 4 + 10 * IceGrowth.h(pc.seed, 9));
        er.pieces = list.toArray(new Piece[0]);
        er.gone = BREAK + 14 + MELT + 2;
        ERUPTIONS.add(er);
        // The air freezing round it: the plume up out of the middle, the cold rolling out low, a frost front on the ground.
        Vec3 mid = at.add(0, .2, 0);
        IceParticles.cryo(mid, new Vec3(0, 1, 0), 1 + .25f * er.radius, .9f);
        IceParticles.coldMist(at, er.radius, 1.1f);
        IceParticles.ring(at.add(0, .05, 0), er.radius * 1.2f, .4f + .12f * er.radius, 0xeef6ff, .4f, 16, false);
        for (int i = 0, k = IceParticles.count(Math.round(10 + er.radius * 5), at); i < k; i++) {
            double a = IceParticles.rand() * Mth.TWO_PI, r = er.radius * IceParticles.rand();
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            IceParticles.snow(at.add(o.scale(r)).add(0, .1, 0), o.scale(.06 + .08 * IceParticles.rand()).add(0, .1 + .12 * IceParticles.rand(), 0), .03f + .03f * IceParticles.rand(), 18 + (int) (IceParticles.rand() * 16));
        }
        IcemanWeaponFx.shakeAt(at, .18f + .05f * er.radius, er.radius * 3 + 6);
    }
    private static Piece piece(Level level, Vec3 at, double dx, double dz, Vec3 dir, float len, float r, int seed, int group, float delay, float dur, IceMesh.Mat mat) {
        Piece pc = new Piece();
        double x = at.x + dx, z = at.z + dz;
        double y = Math.abs(dx) + Math.abs(dz) < 1e-3 ? at.y : groundY(level, x, at.y + .6, z);
        if (Math.abs(y - at.y) > 1.5) y = at.y;
        pc.base = new Vec3(x, y - .06, z);
        pc.dir = dir.normalize();
        pc.len = len; pc.r = r; pc.seed = seed; pc.group = group; pc.delay = delay; pc.dur = dur; pc.mat = mat;
        return pc;
    }

    // ------------------------------------------------------------------ every tick
    static void tick(Level level) {
        var mc = Minecraft.getInstance();
        // ClipContext needs a non-null entity (its collision context reads it).
        if (mc.player == null) return;
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
                if (s.stuck == SHATTER_AT - 8) sound(level, s.pos, ModSounds.ICEMAN_CRACK.get(), .5f, 1.5f);
                if (!s.shattered && s.stuck >= SHATTER_AT) { burst(s, s.pos); s.shattered = true; }
                if (s.stuck >= SHATTER_AT + 2) SPEARS.remove(i);
                continue;
            }
            if (s.age > 95) { SPEARS.remove(i); continue; }
            if (s.held) continue;
            Vec3 next = s.pos.add(s.vel);
            // Our own copy stops at a wall too and waits there for the server's word (FX_SPEAR_STUCK).
            BlockHitResult hit = level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) { s.pos = hit.getLocation(); s.held = true; continue; }
            // The air freezing behind it along the whole step, a breath of vapour now and then.
            for (int k = 0; k < 3; k++) IceParticles.freezeTrail(s.pos.lerp(next, k / 3.0), s.vel.scale(.4), .55f + .35f * s.charge);
            if (s.age % 2 == 0) IceParticles.mist(s.pos, s.vel.scale(.04), .14f + .06f * s.charge, .018f, .17f, 22);
            s.pos = next;
            s.vel = s.vel.add(0, -s.gravity, 0).scale(.995);
            if (s.vel.lengthSqr() > 1e-6) s.dir = s.vel.normalize();
        }
        float now = IcemanWeaponFx.gameTime();
        for (int i = ERUPTIONS.size() - 1; i >= 0; i--) {
            Eruption er = ERUPTIONS.get(i);
            float t = now - er.start;
            if (!er.cracked && t >= CRACK) {
                er.cracked = true;
                sound(level, er.at, ModSounds.ICEMAN_CRACK.get(), .6f, 1.05f);
            }
            boolean first = true;
            for (Piece pc : er.pieces) {
                if (pc.broke || t < pc.breakAt) continue;
                pc.broke = true;
                Vec3 mid = pc.base.add(pc.dir.scale(pc.len * .5));
                if (pc.group == 0 && pc.len > .8f) {
                    // The big growth: the staged fracture, zone by zone up its length (its own sounds).
                    if (first) { IceParticles.breakApart(mid, pc.dir, pc.len * .85f, pc.len * .5f, pc.dir.scale(.03).add(0, .04, 0), 4, 10, pc.mat); first = false; }
                    else IceParticles.shatter(mid, pc.dir.scale(.04), pc.len * .35f, pc.mat);
                } else if (pc.group < 2) {
                    IceParticles.shatter(mid, pc.dir.scale(.05).add(0, .03, 0), pc.len * .45f, pc.mat);
                    if (IceParticles.rand() < .5f) sound(level, mid, ModSounds.ICEMAN_SHATTER.get(), .35f, 1.1f + .3f * IceParticles.rand());
                } else if (IceParticles.rand() < .5f) IceParticles.frostDust(mid, Vec3.ZERO, 1);
            }
            if (t > er.gone) ERUPTIONS.remove(i);
        }
    }
    private static void sound(Level level, Vec3 at, net.minecraft.sounds.SoundEvent ev, float vol, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, ev, SoundSource.PLAYERS, vol, pitch, false);
    }
    /** Where a stuck or flying spear's grip is, its point at (or past) tip. */
    private static Vec3 grip(Spear s, Vec3 at) {
        float len = IcemanArmory.length(W_SPEAR, s.size()) / 16f;
        return s.stuck >= 0 ? at.subtract(s.dir.scale(len - .3f)) : at;
    }
    /** The spear shatters: the staged fracture along it, long shards flying. */
    private static void burst(Spear s, Vec3 at) {
        Vec3 g = grip(s, at), tip = g.add(s.dir.scale(IcemanArmory.length(W_SPEAR, s.size()) / 16f));
        Vec3 mid = g.lerp(tip, .45);
        IceParticles.breakApart(mid, s.dir, (float) g.distanceTo(tip) * .9f, .3f, s.dir.scale(-.03).add(0, .04, 0), 5, 8, IceMesh.CLEAR);
        for (int i = 0, n = IceParticles.count(5, mid); i < n; i++) {
            Vec3 p = g.lerp(tip, .15 + .7 * IceParticles.rand());
            IceParticles.shard(p, IceParticles.jitter(.06).add(0, .07, 0), .12f + .07f * IceParticles.rand(), 34 + (int) (IceParticles.rand() * 20), i % 2 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        }
    }

    // ------------------------------------------------------------------ drawing
    private static final Vector3f AXIS = new Vector3f(0, 0, -1);
    /** The spears and the eruptions, as ice (the stage's ice context). */
    static void drawIce(IceStage st, IceMesh.Ctx c) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float partial = st.partial;
        PoseStack pose = st.pose;
        for (Spear s : SPEARS) {
            if (s.shattered) continue;
            Vec3 at = position(s, level, partial);
            Vec3 d = s.stuck >= 0 || s.held ? s.dir : s.prevVel.lerp(s.vel, partial);
            if (d.lengthSqr() < 1e-6) d = s.dir;
            d = d.normalize();
            float crack = s.stuck < 0 ? 0 : Mth.clamp((s.stuck + partial - (SHATTER_AT - 8)) / 8, 0, 1);
            float size = s.size();
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(new Quaternionf().rotationTo(AXIS, new Vector3f((float) d.x, (float) d.y, (float) d.z)));
            pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
            // Stuck: the point buried a little way in.
            if (s.stuck >= 0) pose.translate(0, 0, IcemanArmory.length(W_SPEAR, size) - 5);
            c.light = IceStage.light(at.subtract(d.scale(.5)));
            c.at(pose);
            IcemanArmory.TURN = 0;
            IcemanArmory.inHand(c, pose, W_SPEAR, 1, size, crack, st.time);
            pose.popPose();
            c.at(pose);
        }
        float now = st.time;
        for (Eruption er : ERUPTIONS) {
            float t = now - er.start;
            boolean far = st.far(er.at);
            c.light = IceStage.light(er.at.add(0, .5, 0));
            float crackK = Mth.clamp((t - CRACK) / (BREAK - CRACK), 0, 1);
            for (Piece pc : er.pieces) {
                if (far && pc.group == 2) continue;
                if (t < pc.breakAt) {
                    float g = IceGrowth.grow(t, pc.delay, pc.dur);
                    if (g <= 0) continue;
                    IceGrowth.crystal(c, pc.base.x, pc.base.y, pc.base.z, pc.dir.x, pc.dir.y, pc.dir.z, pc.len, pc.r, pc.seed, pc.mat, g, 1);
                    if (!far && crackK > 0 && pc.group < 2) {
                        // The cracks running up it before it goes.
                        Vec3 side = pc.dir.cross(new Vec3(0, 1, 0));
                        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
                        Vec3 face = side.scale(pc.r * .9);
                        Vec3 a = pc.base.add(pc.dir.scale(pc.len * .1)).add(face), b = pc.base.add(pc.dir.scale(pc.len * (.1 + .55 * crackK))).add(face.scale(.6));
                        Vec3 m = a.lerp(b, .5).add(pc.dir.cross(side).scale(pc.r * (IceGrowth.h(pc.seed, 77) - .5)));
                        float k = .45f + .55f * crackK;
                        IceMesh.vein(c, a, m, .025f + .01f * pc.len, k);
                        IceMesh.vein(c, m, b, .02f + .01f * pc.len, k);
                    }
                } else {
                    // The stump left, sinking and melting into frost.
                    float melt = Mth.clamp((t - pc.breakAt) / MELT, 0, 1);
                    if (melt >= 1) continue;
                    Vec3 base = pc.base.subtract(0, .2 * melt * Math.max(.3, pc.len * .3), 0);
                    IceGrowth.crystal(c, base.x, base.y, base.z, pc.dir.x, pc.dir.y, pc.dir.z, pc.len * .28f * (1 - .5f * melt), pc.r * (1 - .3f * melt), pc.seed + 1,
                            IceMesh.MILKY, 1, 1 - melt);
                }
            }
        }
    }
    /** The cold round them: the flying spear's streak of vapour; the frost on the ground under an eruption. */
    static void drawFx(IceStage st, FilmContext f) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        float partial = st.partial;
        for (Spear s : SPEARS) {
            if (s.stuck >= 0 || s.shattered) continue;
            Vec3 at = position(s, level, partial);
            Vec3 v = s.held ? Vec3.ZERO : s.prevVel.lerp(s.vel, partial);
            float speed = (float) v.length();
            float tipLen = IcemanArmory.length(W_SPEAR, s.size()) / 16f;
            Vec3 tip = at.add(s.dir.scale(tipLen));
            if (speed > .05f) {
                // Cold vapour streaming off it (pale, soft), a thin cold line along its path: no glowing trail.
                Vec3 tail = at.subtract(v.scale(2.2));
                FilmFx.streak(f, tail, tip, .16 + .06 * s.charge, 0xeef6ff, 0, .32f, false);
                FilmFx.streak(f, at.subtract(v.scale(1.2)), tip, .035, IceParticles.COLD_LIGHT, 0, .22f + .1f * s.charge, true);
            }
        }
        float now = st.time;
        for (Eruption er : ERUPTIONS) {
            float t = now - er.start;
            float a = Mth.clamp(t / 4, 0, 1) * (1 - Mth.clamp((t - BREAK) / (er.gone - BREAK), 0, 1));
            FilmFx.ring(f, er.at.add(0, .03, 0), er.radius * .55, er.radius * .55, 0xeef6ff, .3f * a, false);
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
}
