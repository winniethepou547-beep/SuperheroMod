package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The ice slide's track: a path of REAL ice frozen out of the air under the sliding Iceman (the user's Days of Future
 * Past reference), not a waterslide or a rollercoaster beam. It follows his real path (along the ground, up the
 * formations SPACE raises, over the crest and down), built on every client from where it sees him each tick.
 * <p>
 * The path: irregular slab cross-sections ({@link IceGrowth#section}): a flat walkable top (where his feet run, exactly
 * where the server lays its solid boxes), broken crystalline lips, bulging broken sides whose height wanders along the
 * way, an uneven keel; every stretch seeded, so it is stable and never two alike. Along its edges crystals lean out and
 * back like a frozen spray (ridges of crystals), now and then a small cluster bursts off a side. Into a turn the
 * INSIDE grows more: the side bulges out, more and bigger crystals (turning left, the left side; right, the right).
 * Under the raised stretches crystals hang like icicles; on the climbs the mass under it thickens, spikes grow out of
 * its underside and sides, and crystal pillars rise from the ground to meet it, so a climb reads as a formation growing
 * upward, not a ramp. Where it leaves the ground, a frosted footing with a cluster growing up into it.
 * <p>
 * Growing: each stretch begins as a thin narrow skin and thickens and widens over a few ticks; its crystals come each on
 * their own delay (some first, some late, some long, some short); the ice reaches out a little ahead of his feet.
 * Forming (SHIFT pressed, while he gets ready): frost spreading under his feet, tiny crystal nuclei out ahead growing,
 * then sinking into the path as it grows over them.
 * <p>
 * Ends: let go on the ground, its last stretch cracks at once and sheds a few chips; let go in the air, its last point
 * freezes into a crystal cluster. Dissolving (never all at once, oldest first, from behind him): cracks (a faint cold
 * line on its top, a crack heard now and then), then its crystals break off as chips (the raised stretches now and then
 * break apart in zones, sparingly), then frost takes it over and it shrinks away with a frost hiss. Crystals per stretch
 * are capped, there is a crystal budget per frame, and past the far-detail distance only the slab is drawn.
 */
final class IcemanTrack {
    private static final float SPACING = .45f;
    private static final int MAX_NODES = 600, MAX_TRACKS = 16;
    /** Crystals drawn per frame at most (all tracks together); the nearest tracks come first in practice. */
    private static final int CRYSTAL_BUDGET = 650;

    static final class Node {
        final Vec3 pos, tan, side, up;
        final float born, width, thick, height;
        final int seed, light;
        /** Which patch of ice it is in (clear, milky, glacier come in stretches of a couple of blocks). */
        final int patch;
        /** How far along the track (blocks), which side grows more (-1..1 along side: the inside of the turn), how steep it climbs (0..1). */
        final float along, bias, climb;
        /** The ground's top under it (very low when none in reach). */
        final float ground;
        /** A pillar rises from the ground to it; it is where the track left the ground. */
        final boolean support, takeoff;
        /** Dissolving stage reached (0 none, 1 chips shed, 2 frost given off); cracked early (the ground exit). */
        int stage; float crackAt = -1;
        Node(Vec3 pos, Vec3 tan, Vec3 side, Vec3 up, float born, float width, float thick, float height, int seed, int light, int patch,
             float along, float bias, float climb, float ground, boolean support, boolean takeoff) {
            this.pos = pos; this.tan = tan; this.side = side; this.up = up; this.born = born; this.width = width; this.thick = thick;
            this.height = height; this.seed = seed; this.light = light; this.patch = patch;
            this.along = along; this.bias = bias; this.climb = climb; this.ground = ground; this.support = support; this.takeoff = takeoff;
        }
        /** The same ice moved to another place (the stretch growing out ahead of his feet). */
        Node(Node o, Vec3 pos, Vec3 tan, float born, int seed, float along) {
            this(pos, tan, o.side, o.up, born, o.width, o.thick, o.height, seed, o.light, o.patch, along, o.bias, o.climb, o.ground, false, false);
        }
        double h(double k) { return IceMesh.hash(seed * k + 1.7); }
        float h(int salt) { return IceGrowth.h(seed, salt); }
    }

    final int owner;
    final List<Node> nodes = new ArrayList<>();
    boolean live = true;
    float dist, bank, seed;
    /** Points placed so far (seeds them; never goes down when old ones go). */
    int placed;
    float lastSupport = -99, lastTakeoff = -99;
    /** When it last cracked aloud, last broke apart, last hissed (so its sounds never pile up). */
    float crackSound = -99, breakAt = -99, hissAt = -99;
    Vec3 lastTan;
    /** Forming: when, where his feet were, which way. */
    final float formAt; Vec3 formAt3, formDir;
    /** How it ended: 0 still going, 1 on the ground (its end cracks), 2 in the air (its end freezes into a cluster); when. */
    int endKind; float endAt;

    private IcemanTrack(int owner, float now) { this.owner = owner; seed = IceParticles.rand() * 100; formAt = now; }

    private static final List<IcemanTrack> ALL = new ArrayList<>();
    private static final Map<Integer, IcemanTrack> LIVE = new HashMap<>();

    static boolean any() { return !ALL.isEmpty(); }
    static void clear() { ALL.clear(); LIVE.clear(); }

    // ------------------------------------------------------------------ growing and dissolving (every client tick)
    /** Grows the track under this Iceman while he slides (called every tick for each one sliding, getting ready included). */
    static void grow(Entity e) {
        IcemanTrack t = LIVE.get(e.getId());
        // A jump in his place (a teleport): a new piece of track from there.
        if (t != null && !t.nodes.isEmpty() && t.nodes.get(t.nodes.size() - 1).pos.distanceToSqr(e.position()) > 36) { stop(e.getId(), 1); t = null; }
        if (t == null) {
            if (ALL.size() >= MAX_TRACKS) { IcemanTrack old = ALL.remove(0); LIVE.values().remove(old); }
            t = new IcemanTrack(e.getId(), e.level().getGameTime());
            float yaw = e.getYRot() * Mth.DEG_TO_RAD;
            t.formDir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            t.formAt3 = e.position();
            ALL.add(t);
            LIVE.put(e.getId(), t);
        }
        t.add(e);
    }
    /** He stopped sliding: the track stops growing; kind 1 on the ground (its end cracks), 2 in the air (its end freezes into a cluster). */
    static void stop(int id, int kind) {
        IcemanTrack t = LIVE.remove(id);
        if (t == null) return;
        t.live = false;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = mc.level.getGameTime();
        t.endKind = kind;
        t.endAt = now;
        int n = t.nodes.size();
        if (kind == 1) {
            // The last stretch cracks at once (the crack running back along it) and a few chips break off it.
            for (int i = Math.max(0, n - 10); i < n; i++) t.nodes.get(i).crackAt = now + (n - 1 - i) * .6f;
            for (int i = Math.max(0, n - 6); i < n; i += 2) {
                Node nd = t.nodes.get(i);
                if (IceParticles.count(1, nd.pos) <= 0) continue;
                IceParticles.shard(nd.pos.add(nd.side.scale((IceParticles.rand() - .5) * nd.width)).add(0, .1, 0),
                        IceParticles.jitter(.05).add(0, .12, 0), .07f + .06f * IceParticles.rand(), 30, IceMesh.FRESH);
                IceParticles.frostDust(nd.pos.add(0, .08, 0), Vec3.ZERO, .6f);
            }
        } else if (kind == 2 && n > 0) {
            // The last point freezing into a cluster: the air round it freezing first.
            Node last = t.nodes.get(n - 1);
            IceParticles.cryo(last.pos.add(last.up.scale(-last.thick * .5)), last.tan, .7f, .7f);
            sound(last.pos, ModSounds.ICEMAN_CRYSTAL_TICKS.get(), .45f, 1.1f);
        }
    }
    static boolean growing(int id) { return LIVE.containsKey(id); }

    private void add(Entity e) {
        Level level = e.level();
        Vec3 feet = e.position();
        float now = level.getGameTime();
        if (nodes.isEmpty()) {
            lastTan = formDir;
            place(level, feet, null, now);
            return;
        }
        Node last = nodes.get(nodes.size() - 1);
        double d = last.pos.distanceTo(feet);
        if (d < SPACING) return;
        int steps = Math.min(8, (int) Math.floor(d / SPACING));
        Vec3 from = new Vec3(last.pos.x, feetY(last), last.pos.z);
        for (int i = 1; i <= steps; i++) place(level, from.lerp(feet, i / (double) steps), nodes.get(nodes.size() - 1), now - (steps - i) * (1f / steps));
        while (nodes.size() > MAX_NODES) nodes.remove(0);
    }
    private static float feetY(Node n) { return (float) (n.height < .3f ? n.pos.y - .06 : n.pos.y + .03); }
    /** The seed of the point placed as number i (the stretch ahead of his feet uses the next ones, so it never changes shape when it is laid). */
    private int seedOf(int i) { return (int) (seed * 1000) + i * 7919; }

    private void place(Level level, Vec3 feet, Node prev, float born) {
        Vec3 tan = prev == null ? null : feet.subtract(new Vec3(prev.pos.x, feetY(prev), prev.pos.z));
        if (tan == null || tan.lengthSqr() < 1e-6) tan = lastTan != null ? lastTan : new Vec3(0, 0, 1);
        tan = tan.normalize();
        // The bank: leaning into the turn by how much the way turned since the last point (positive: to his right).
        if (lastTan != null) {
            float a0 = (float) Math.atan2(-lastTan.x, lastTan.z), a1 = (float) Math.atan2(-tan.x, tan.z);
            float turn = Mth.wrapDegrees((a1 - a0) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            bank = Mth.lerp(.35f, bank, Mth.clamp(turn * 3.2f, -.5f, .5f));
        }
        lastTan = tan;
        // side = his left (looking along the way).
        Vec3 side = new Vec3(tan.z, 0, -tan.x);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = tan.cross(side).normalize();
        float cb = Mth.cos(bank), sb = Mth.sin(bank);
        Vec3 s2 = side.scale(cb).add(up.scale(sb)), u2 = up.scale(cb).subtract(side.scale(sb));
        float ground = ground(level, feet);
        float height = (float) (feet.y - ground);
        // The walkable top: just over the ground, or just under his feet in the air (the server's boxes have their top there).
        Vec3 pos = height < .3f ? new Vec3(feet.x, Math.max(feet.y, ground) + .06, feet.z) : feet.add(0, -.03, 0);
        dist += prev == null ? 0 : SPACING;
        float climb = Mth.clamp((float) tan.y / .55f, 0, 1);
        // The inside of the turn grows more (turning right: his right side, which is -side).
        float bias = Mth.clamp(-bank * 2.4f, -1, 1);
        // Thin where it runs on the ground, thicker in the air, thickest under the climbs (the formation's mass).
        float th = height < .3f ? .22f + .04f * Mth.sin(dist * .7f + seed)
                : .34f + .08f * Mth.sin(dist * .5f + seed) + Math.min(.2f, height * .03f) + .24f * climb;
        boolean found = height < 90;
        boolean takeoff = prev != null && prev.height < .45f && height >= .45f && tan.y > .04 && dist - lastTakeoff > 4;
        if (takeoff) lastTakeoff = dist;
        // Pillars rise to the climbs (and just past where it left the ground), every couple of blocks, each its own gap.
        boolean support = found && height > .6f && height < 4.5f && (climb > .08f || dist - lastTakeoff < 5)
                && dist - lastSupport > 1.5f + 1.3f * IceGrowth.h(seedOf(placed), 3);
        if (support) lastSupport = dist;
        Node n = new Node(pos, tan, s2, u2, born, 1.02f, th, height, seedOf(placed), IceStage.light(pos), (int) Math.floor(dist / 2.2f),
                dist, bias, climb, found ? ground : -1e6f, support, takeoff);
        placed++;
        nodes.add(n);
        // The air freezing out of it as it is laid: tiny crystals flicked out off its lips, low cold vapour.
        if (IceParticles.count(1, pos) > 0) {
            float sg = IceParticles.rand() < .5f + .3f * bias ? 1 : -1;
            Vec3 at = pos.add(s2.scale(sg * .5));
            IceParticles.crystalDust(at, s2.scale(sg * (.05 + .07 * IceParticles.rand())).add(tan.scale(-.04)).add(0, .03 + .04 * IceParticles.rand(), 0),
                    .016f + .014f * IceParticles.rand(), (IceParticles.rand() - .5f) * .5f, 12 + (int) (IceParticles.rand() * 10));
            if (height > .6f && IceParticles.rand() < .25f)
                IceParticles.mist(pos.add(u2.scale(-th)), new Vec3(0, -.012, 0).add(tan.scale(-.01)), .22f, .02f, .1f, 26);
        }
        if (takeoff) {
            // Leaving the ground: the cold pouring down to it, frost spreading, the formation rising out of it.
            Vec3 g = new Vec3(pos.x, ground, pos.z);
            IceParticles.coldMist(g, 1.6f, .8f);
            IceParticles.cryo(g.add(0, .1, 0), new Vec3(0, 1, 0), .6f, .8f);
            sound(g, ModSounds.ICEMAN_GROW_RUMBLE.get(), .35f, 1.25f);
        }
    }
    /** The top of the ground under a point (within 8 blocks), or far below when there is none. */
    static float ground(Level level, Vec3 at) {
        BlockPos base = BlockPos.containing(at.x, at.y + .2, at.z);
        for (int dy = 0; dy <= 8; dy++) {
            BlockPos p = base.below(dy);
            var state = level.getBlockState(p);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(level, p);
            if (shape.isEmpty()) continue;
            float top = (float) (p.getY() + shape.max(Direction.Axis.Y));
            if (top <= at.y + .25) return top;
        }
        return (float) at.y - 99;
    }
    private static void sound(Vec3 at, SoundEvent ev, float vol, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.x, at.y, at.z, ev, SoundSource.PLAYERS, vol, pitch * (.94f + .12f * IceParticles.rand()), false);
    }

    /** Dissolving: cracks (a crack heard now and then), then chips break off (the odd raised stretch breaks apart), then frost; old points gone. */
    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || ALL.isEmpty()) return;
        float now = mc.level.getGameTime();
        for (Iterator<IcemanTrack> it = ALL.iterator(); it.hasNext(); ) {
            IcemanTrack t = it.next();
            int gone = 0;
            for (int i = 0; i < t.nodes.size(); i++) {
                Node n = t.nodes.get(i);
                float age = now - n.born;
                if (age > SLIDE_TRACK_LIFE + SLIDE_TRACK_MELT) { gone = i + 1; continue; }
                // Fresh ice breathing a light cold vapour that spills off its lips and sinks.
                if (age < 12 && IceParticles.rand() < .05f && IceParticles.count(1, n.pos) > 0) {
                    float sg = IceParticles.rand() < .5f ? 1 : -1;
                    IceParticles.mist(n.pos.add(n.side.scale(sg * n.width * .5)), n.side.scale(sg * .008).add(0, -.006, 0), .26f, .02f, .13f, 28);
                }
                float m = (age - SLIDE_TRACK_LIFE) / SLIDE_TRACK_MELT;
                if (m > 0 && m < .25f && n.h(23) < .06f && now - t.crackSound > 7) {
                    t.crackSound = now;
                    sound(n.pos, ModSounds.ICEMAN_CRACK.get(), .25f, 1.25f);
                }
                if (n.stage == 0 && m >= .3f) {
                    n.stage = 1;
                    if (IceParticles.count(1, n.pos) <= 0) continue;
                    Vec3 under = n.pos.add(n.up.scale(-n.thick * .55));
                    if (n.height > 1.2f && n.h(91) < .16f && now - t.breakAt > 24) {
                        // A raised stretch breaking apart in zones (sparingly: its sounds and big chunks).
                        t.breakAt = now;
                        IceParticles.breakApart(under, n.tan, 1.8f, .6f, new Vec3(0, -.04, 0), 2, 9, IceMesh.CLEAR);
                    } else if (n.h(13) < .4f) {
                        // Its crystals breaking off as chips (bigger ones drop from the raised stretches).
                        boolean raised = n.height > 1;
                        IceParticles.shard(raised ? under.add(n.side.scale((IceParticles.rand() - .5) * n.width)) : n.pos.add(n.side.scale((IceParticles.rand() - .5) * n.width)).add(0, .08, 0),
                                IceParticles.jitter(.035).add(0, raised ? -.02 : .06, 0), raised ? .13f + .12f * IceParticles.rand() : .07f + .06f * IceParticles.rand(),
                                30 + (int) (IceParticles.rand() * 20), n.h(17) < .5f ? IceMesh.FRESH : IceMesh.CLEAR);
                        if (n.h(19) < .4f) IceParticles.crystalDust(n.pos.add(0, .1, 0), IceParticles.jitter(.03).add(0, .03, 0), .018f, .3f, 18);
                    }
                }
                if (n.stage == 1 && m >= .6f) {
                    n.stage = 2;
                    // Frost given off as it goes, the hiss of it heard now and then.
                    if (n.h(17.3) < .3 && IceParticles.count(1, n.pos) > 0) {
                        IceParticles.frostDust(n.pos, Vec3.ZERO, .7f);
                        IceParticles.mist(n.pos, new Vec3(0, -.004, 0), .3f, .025f, .16f, 26);
                    }
                    if (now - t.hissAt > 26) { t.hissAt = now; sound(n.pos, ModSounds.ICEMAN_FROST_HISS.get(), .22f, 1.1f); }
                }
            }
            if (gone > 0) t.nodes.subList(0, gone).clear();
            if (t.nodes.isEmpty() && !t.live) it.remove();
        }
    }

    // ------------------------------------------------------------------ drawing
    private static final float[] SA = new float[IceGrowth.SECTION * 3], SB = new float[IceGrowth.SECTION * 3],
            KA = new float[IceGrowth.SECTION * 3], KB = new float[IceGrowth.SECTION * 3];
    private static int budget;
    private static double farSq;

    /** Draws every track, with the ice growing out ahead of his feet on the live ones. */
    static void draw(IceStage st, IceMesh.Ctx c) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = st.time;
        budget = CRYSTAL_BUDGET;
        double far = IcemanConfig.FAR_DETAIL.get();
        farSq = far * far;
        // The ice keeps to the world's grid (its texture is not pinned to anything moving).
        c.ox = c.oy = c.oz = 0;
        for (IcemanTrack t : ALL) {
            List<Node> list = t.nodes;
            if (list.isEmpty()) continue;
            Node feetNode = null, leadNode = null;
            Entity e = t.live ? mc.level.getEntity(t.owner) : null;
            if (e != null) {
                Node last = list.get(list.size() - 1);
                Vec3 feet = e.getPosition(st.partial);
                Vec3 vel = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
                // Out ahead of his feet: the ice reaching out (while he gets ready, out along his way; then just ahead of him).
                float age = now - t.formAt;
                float reach = 2.6f * PantherMotion.k(age, 3.5f, SLIDE_PREP + 1) * (1 - PantherMotion.k(age, SLIDE_PREP + 3, SLIDE_PREP + 9));
                Vec3 ahead = vel.scale(1.3);
                if (reach > ahead.length()) ahead = (vel.lengthSqr() > 1e-4 ? vel.normalize() : t.formDir).scale(reach);
                feetNode = head(t, last, feet, now - .3f, 0);
                leadNode = head(t, last, feet.add(ahead), now + 1.2f, 1);
                formation(st, c, t, now, age);
            }
            int n = list.size() + (feetNode != null ? 2 : 0);
            Node prev = null;
            for (int i = 0; i < n; i++) {
                Node node = i < list.size() ? list.get(i) : i == list.size() ? feetNode : leadNode;
                if (prev != null) segment(st, c, t, prev, node, now, i == 1, i == n - 1);
                prev = node;
            }
            // What grows on it (the newest first, so the budget goes to the ice round him).
            for (int i = list.size() - 1; i >= 0 && budget > 0; i--) growths(st, c, t, list.get(i), now);
            if (t.endKind == 2) crest(c, t, list.get(list.size() - 1), now);
        }
    }
    private static Node head(IcemanTrack t, Node last, Vec3 feet, float born, int k) {
        Vec3 tan = feet.subtract(new Vec3(last.pos.x, feetY(last), last.pos.z));
        tan = tan.lengthSqr() < 1e-6 ? last.tan : tan.normalize();
        Vec3 pos = last.height < .3f ? new Vec3(feet.x, Math.max(feet.y, last.pos.y - .06) + .06, feet.z) : feet.add(0, -.03, 0);
        return new Node(last, pos, tan, born, t.seedOf(t.placed + k), last.along + SPACING * (k + 1));
    }
    /**
     * Forming, while he gets ready: frost spreading under his feet, tiny crystal nuclei appearing out ahead of him one
     * after another and growing (in pairs that lean into each other), then sinking into the path as it grows over them.
     */
    private static void formation(IceStage st, IceMesh.Ctx c, IcemanTrack t, float now, float age) {
        if (age > SLIDE_PREP + 14 || t.formAt3 == null || st.far(t.formAt3)) return;
        Vec3 o = t.formAt3, dir = t.formDir, side = new Vec3(dir.z, 0, -dir.x);
        Node first = t.nodes.get(0);
        float y = (float) (first.height < .3f ? first.pos.y - .045 : first.pos.y - .03);
        c.light = first.light;
        // The frost under his feet, spreading.
        float frost = PantherMotion.snap(age, 0, 4.5f) * (1 - PantherMotion.k(age, SLIDE_PREP + 5, SLIDE_PREP + 14));
        if (frost > .01f) IcemanGroundFx.rime(c, new Vec3(o.x, y, o.z), 1.25f * frost, .65f * frost, (int) (t.seed * 13));
        int sd = (int) (t.seed * 977);
        for (int i = 0; i < 9; i++) {
            float at = 1 + i * .45f + 1.2f * IceGrowth.h(sd, i);
            float g = IceGrowth.grow(age, at, 3 + 2 * IceGrowth.h(sd, 20 + i));
            // Merging: the path grows over it, it sinks into it.
            float merge = PantherMotion.k(age, SLIDE_PREP + .5f + i * .35f, SLIDE_PREP + 5 + i * .35f);
            g *= 1 - .75f * merge;
            if (g <= .01f) continue;
            float hx = IceGrowth.h(sd, 40 + i), hz = IceGrowth.h(sd, 60 + i);
            double bx = o.x + dir.x * (.35 + i * .3) + side.x * (hx - .5) * .7, bz = o.z + dir.z * (.35 + i * .3) + side.z * (hx - .5) * .7;
            double by = y - .03 - .12 * merge;
            for (int k = 0; k < 2; k++) {
                if (k == 1 && hz < .4f) continue;
                float lean = (k == 0 ? -1 : 1) * (.3f + .4f * IceGrowth.h(sd, 80 + i * 2 + k));
                float len = (.18f + .3f * IceGrowth.h(sd, 100 + i * 2 + k)) * (k == 0 ? 1 : .7f), r = len * (.26f + .1f * hz);
                IceGrowth.crystal(c, bx + side.x * lean * .12, by, bz + side.z * lean * .12, side.x * lean + dir.x * .35, 1, side.z * lean + dir.z * .35,
                        len, r, sd * 7 + i * 2 + k, i % 3 == 0 ? IceMesh.GLACIER : IceMesh.CLEAR, k == 0 ? g : IceGrowth.grow(age, at + 1, 3) * (1 - .75f * merge), 1);
            }
            if (g < .9f) IceMesh.sparkle(c, new Vec3(bx, by + .2 * g, bz), .1f, .5f * g * (1 - g));
        }
    }
    /** Let go in the air: the track's last point freezes into a crystal cluster, a smaller one hanging under it. */
    private static void crest(IceMesh.Ctx c, IcemanTrack t, Node n, float now) {
        float age = now - t.endAt;
        float s = size(n, now), al = alpha(n, now) * (1 - frosted(n, now)), keep = 1 - PantherMotion.k(melt(n, now), .25f, .45f);
        if (s <= .05f || al <= .02f || keep <= 0) return;
        c.light = n.light;
        Vec3 o = n.pos.add(n.up.scale(-n.thick * .45 * s));
        Vec3 d = n.tan.add(0, -.35, 0);
        IceGrowth.cluster(c, o.x, o.y, o.z, d.x, d.y, d.z, .85f * s, n.seed * 5 + 1, 6, IceMesh.CLEAR, age / 12f * keep, al);
        Vec3 o2 = o.add(n.up.scale(-n.thick * .3 * s));
        IceGrowth.cluster(c, o2.x, o2.y, o2.z, n.tan.x * .3, -1, n.tan.z * .3, .55f * s, n.seed * 5 + 2, 4, IceMesh.GLACIER, (age - 3) / 14f * keep, al);
    }
    /** How far it has grown (a thin skin at first, thickening and widening over a few ticks). */
    private static float grown(Node n, float now) { return IceGrowth.grow(now - n.born + 1, 0, 5.5f); }
    /** How far it has dissolved (below 0 still whole, 1 gone). */
    private static float melt(Node n, float now) { return (now - n.born - SLIDE_TRACK_LIFE) / SLIDE_TRACK_MELT; }
    /** How much of a point's ice is left as it dissolves. */
    private static float size(Node n, float now) { return 1 - .75f * PantherMotion.ease((melt(n, now) - .3f) / .7f); }
    private static float alpha(Node n, float now) { return 1 - PantherMotion.ease((melt(n, now) - .55f) / .45f); }
    private static float frosted(Node n, float now) { return PantherMotion.k(melt(n, now), .45f, .8f); }
    /** Mostly clear blue ice, with stretches of deep glacier ice and of milky ice. */
    private static IceMesh.Mat patch(IcemanTrack t, Node n) {
        double h = IceMesh.hash(t.seed * 7.3 + n.patch * 1.91);
        return h < .72 ? IceMesh.CLEAR : h < .88 ? IceMesh.GLACIER : IceMesh.MILKY;
    }
    /** A smooth wander along the track (-1..1), its own per side. */
    private static float wander(IcemanTrack t, float along, float sg) {
        float o = t.seed * (sg > 0 ? 1.3f : 2.1f);
        return .6f * Mth.sin(along * 1.05f + o) + .4f * Mth.sin(along * 2.6f + o * 1.7f + 1);
    }
    /** Moves a section's point i by (across, up) in the node's frame. */
    private static void move(float[] out, int i, Node n, float a, float b) {
        out[i * 3] += (float) (n.side.x * a + n.up.x * b);
        out[i * 3 + 1] += (float) (n.side.y * a + n.up.y * b);
        out[i * 3 + 2] += (float) (n.side.z * a + n.up.z * b);
    }
    /**
     * The path's cross-section at a point (IceGrowth.section): grown g, s left as it dissolves; then the sides' height
     * wandering along the way and the inside of a turn bulging out with more ice (its top untouched).
     */
    private static void section(IcemanTrack t, Node n, float g, float s, float[] out) {
        float w = n.width * (.55f + .45f * g) * s, th = n.thick * s;
        IceGrowth.section(n.pos.x, n.pos.y, n.pos.z, n.side.x, n.side.y, n.side.z, n.up.x, n.up.y, n.up.z, w, th, n.seed, g, out);
        // Point 4 / 5 / 6 are the lip, the upper and the lower side on +side; 0 / 9 / 8 the same on -side.
        for (int k = 0; k < 2; k++) {
            float sg = k == 0 ? 1 : -1, more = Math.max(0, sg * n.bias) * g * s, wv = wander(t, n.along, sg) * g * s;
            int lip = k == 0 ? 4 : 0, hi = k == 0 ? 5 : 9, lo = k == 0 ? 6 : 8;
            move(out, lip, n, 0, .025f * wv);
            move(out, hi, n, sg * (.06f * wv + .28f * more), .07f * wv - .04f * more);
            move(out, lo, n, sg * (.03f * wv + .2f * more), -.14f * more * th / .3f);
        }
    }
    /** The mass under a climb (hidden inside the slab where it is flat): narrower, deep, its own broken shape. */
    private static float keelMass(Node n) { return n.height > .5f ? PantherMotion.k(n.climb, .08f, .6f) : 0; }
    private static void keel(Node n, float g, float s, float[] out) {
        float k = keelMass(n);
        float w = n.width * (.4f + .32f * k) * (.55f + .45f * g) * s, th = n.thick * (.2f + 1.5f * k) * s;
        double d = n.thick * .5 * s;
        IceGrowth.section(n.pos.x - n.up.x * d, n.pos.y - n.up.y * d, n.pos.z - n.up.z * d, n.side.x, n.side.y, n.side.z, n.up.x, n.up.y, n.up.z,
                w, th, n.seed + 101, g, out);
    }
    private static void segment(IceStage st, IceMesh.Ctx c, IcemanTrack t, Node a, Node b, float now, boolean first, boolean last) {
        double d2 = a.pos.distanceToSqr(st.cam);
        if (d2 > 110 * 110) return;
        float sa = size(a, now), sb = size(b, now);
        float al = Math.min(alpha(a, now), alpha(b, now));
        if (al <= .01f) return;
        boolean far = d2 > farSq;
        float fr = frosted(a, now);
        c.light = a.light;
        float m = melt(a, now);
        c.flash = m > 0 && m < .35f ? .1f * Mth.sin(m / .35f * Mth.PI) : 0;
        float ga = grown(a, now), gb = grown(b, now);
        section(t, a, ga, sa, SA);
        section(t, b, gb, sb, SB);
        IceMesh.Mat mat = patch(t, a);
        float body = al * (1 - .7f * fr);
        IceGrowth.slab(c, SA, SB, mat, a.seed, body, first, last);
        // Frost taking over its surface as it dissolves.
        if (fr > .01f && !far) IceMesh.loft(c, SA, SB, IceMesh.FROST, al * fr * .8f, false, false);
        // The mass under the climbs.
        if (!far && (keelMass(a) > 0 || keelMass(b) > 0)) {
            keel(a, ga, sa, KA);
            keel(b, gb, sb, KB);
            IceGrowth.slab(c, KA, KB, IceMesh.GLACIER, a.seed + 7, body, first, last);
        }
        if (far) { c.flash = 0; return; }
        // Cracks on its top: on dissolving, or at once at the end where he braked (a faint cold line, then it breaks).
        float crack = m > 0 && m < .6f ? Mth.sin(Mth.clamp(m / .6f, 0, 1) * Mth.PI) : 0;
        if (a.crackAt >= 0 && now >= a.crackAt) crack = Math.max(crack, PantherMotion.k(now - a.crackAt, 0, 2) * (1 - fr));
        if (crack > .01f && (a.seed % 3 == 0 || a.crackAt >= 0)) {
            float k = crack * al;
            Vec3 p0 = a.pos.add(a.side.scale((a.h(1.0) - .5) * a.width * .7 * sa)).add(a.up.scale(.012));
            Vec3 p1 = b.pos.add(b.side.scale((a.h(2.0) - .5) * b.width * .7 * sb)).add(b.up.scale(.012));
            Vec3 mid = p0.lerp(p1, .5).add(a.side.scale((a.h(3.0) - .5) * .3 * sa));
            IceMesh.vein(c, p0, mid, .012f, .7f * k);
            IceMesh.vein(c, mid, p1, .012f, .7f * k);
            // A branch running off toward a lip.
            if (a.h(4.0) < .5) IceMesh.vein(c, mid, mid.add(a.side.scale((a.h(5.0) < .5 ? -1 : 1) * .3 * sa)).add(a.tan.scale(.12)), .009f, .5f * k);
        }
        c.flash = 0;
    }
    /** A crystal on a point: its base across / down in the point's frame, its way (side, up, along), grown g. */
    private static void crystal(IceMesh.Ctx c, Node n, float across, float down, float ds, float du, float dt, float len, float r, int seed, IceMesh.Mat mat, float g, float al) {
        if (g <= .01f || budget <= 0) return;
        budget--;
        double bx = n.pos.x + n.side.x * across - n.up.x * down, by = n.pos.y + n.side.y * across - n.up.y * down, bz = n.pos.z + n.side.z * across - n.up.z * down;
        IceGrowth.crystal(c, bx, by, bz, n.side.x * ds + n.up.x * du + n.tan.x * dt, n.side.y * ds + n.up.y * du + n.tan.y * dt, n.side.z * ds + n.up.z * du + n.tan.z * dt,
                len, r, seed, mat, g, al);
    }
    /**
     * What grows on a point (near only; each on its own delay; broken off first as it dissolves): the crystalline lips,
     * a cluster bursting off a side (mostly the inside of a turn), crystals hanging under the raised stretches, spikes
     * out of the underside of a climb, the pillar rising to it, the footing where it left the ground, a glint on fresh ice.
     */
    private static void growths(IceStage st, IceMesh.Ctx c, IcemanTrack t, Node n, float now) {
        if (n.pos.distanceToSqr(st.cam) > farSq) return;
        float s = size(n, now), al = alpha(n, now) * (1 - frosted(n, now));
        float keep = 1 - PantherMotion.k(melt(n, now), .25f, .45f);
        if (s < .2f || al * keep < .02f) return;
        al *= .3f + .7f * keep;
        float age = now - n.born;
        float hw = n.width * .5f * s * (.55f + .45f * grown(n, now)), th = n.thick * s;
        int sd = n.seed;
        c.light = n.light;
        // The crystalline lips: crystals leaning out and back off the edges, more and longer on the inside of a turn.
        for (int k = 0; k < 2; k++) {
            float sg = k == 0 ? 1 : -1, more = Math.max(0, sg * n.bias);
            if (n.h(10 + k) > .42f + .5f * more) continue;
            float g = IceGrowth.grow(age, 1.5f + 5 * n.h(12 + k), 5 + 7 * n.h(14 + k)) * keep;
            float len = (.24f + .34f * n.h(16 + k)) * (1 + 1.1f * more), r = len * (.2f + .1f * n.h(18 + k));
            crystal(c, n, sg * (hw * (.86f + .16f * n.h(20 + k)) + .24f * more), .04f + .08f * n.h(22 + k),
                    sg * (.55f + .35f * n.h(24 + k)), .3f + .5f * n.h(26 + k), (n.h(28 + k) - .68f) * 1.4f,
                    len, r, sd * 13 + k, n.h(30 + k) < .2f ? IceMesh.GLACIER : IceMesh.CLEAR, g, al);
        }
        // A cluster bursting off a side.
        float big = Math.abs(n.bias);
        if (n.h(40) < .05f + .32f * big && budget > 0) {
            float sg = n.bias > .12f ? 1 : n.bias < -.12f ? -1 : n.h(41) < .5f ? 1 : -1;
            float tc = (age - 2 - 5 * n.h(42)) / (12 + 8 * n.h(43)) * keep;
            float across = sg * (hw * .95f + .26f * big);
            double down = th * .3;
            double bx = n.pos.x + n.side.x * across - n.up.x * down, by = n.pos.y + n.side.y * across - n.up.y * down, bz = n.pos.z + n.side.z * across - n.up.z * down;
            int count = 3 + (int) (n.h(45) * 2.99f);
            budget -= count;
            IceGrowth.cluster(c, bx, by, bz, n.side.x * sg * .75 + n.up.x * .65 - n.tan.x * .25, n.side.y * sg * .75 + n.up.y * .65 - n.tan.y * .25,
                    n.side.z * sg * .75 + n.up.z * .65 - n.tan.z * .25, (.36f + .3f * n.h(44)) * (1 + .7f * big) * s, sd * 17 + 3, count,
                    n.h(46) < .25f ? IceMesh.GLACIER : IceMesh.CLEAR, tc, al);
        }
        // Hanging under the raised stretches, like icicles (more under the climbs).
        if (n.height > 1 && n.h(50) < .2f + .3f * n.climb && budget > 0) {
            budget--;
            float g = IceGrowth.grow(age, 4 + 6 * n.h(51), 14 + 14 * n.h(52)) * keep;
            float across = (n.h(53) - .5f) * hw * 1.3f;
            double bx = n.pos.x + n.side.x * across - n.up.x * th * .9, by = n.pos.y + n.side.y * across - n.up.y * th * .9, bz = n.pos.z + n.side.z * across - n.up.z * th * .9;
            float len = .3f + .55f * n.h(54) + .5f * n.climb;
            IceGrowth.crystal(c, bx, by, bz, (n.h(55) - .5f) * .35f, -1, (n.h(56) - .5f) * .35f, len, .06f + .05f * n.h(57), sd * 19 + 1,
                    n.h(58) < .3f ? IceMesh.GLACIER : IceMesh.CLEAR, g, al);
        }
        // A climb: spikes growing out of its underside and sides (a formation growing upward, not a ramp).
        if (n.climb > .25f && n.height > .5f && n.h(60) < .6f * n.climb) {
            float sg = n.h(61) < .5f + .3f * n.bias ? 1 : -1;
            float g = IceGrowth.grow(age, 3 + 6 * n.h(62), 9 + 9 * n.h(63)) * keep;
            float len = (.45f + .55f * n.h(64)) * (1 + .5f * n.climb);
            crystal(c, n, sg * hw * (.3f + .55f * n.h(65)), th * (.6f + .35f * n.h(66)), sg * (.55f + .3f * n.h(67)), -.55f - .3f * n.h(68), -.35f,
                    len, len * (.2f + .07f * n.h(69)), sd * 23 + 2, n.h(70) < .4f ? IceMesh.GLACIER : IceMesh.CLEAR, g, al);
        }
        // The pillar: crystals rising from the ground to meet its underside, frost spreading round their foot.
        if (n.support && budget > 0) {
            float across = (n.h(80) - .5f) * hw * .7f;
            double tx = n.pos.x + n.side.x * across - n.up.x * th * .8, ty = n.pos.y + n.side.y * across - n.up.y * th * .8, tz = n.pos.z + n.side.z * across - n.up.z * th * .8;
            double fx = tx + (n.h(81) - .5f) * .5, fy = n.ground, fz = tz + (n.h(82) - .5f) * .5;
            double dx = tx - fx, dy = ty - fy, dz = tz - fz, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dy > .3) {
                float g = IceGrowth.grow(age, 2 + 3 * n.h(83), 12 + 8 * n.h(84)) * keep;
                float r = (float) Math.min(.36, .16 + .05 * len) * (.85f + .3f * n.h(85));
                budget -= 3;
                IceGrowth.crystal(c, fx, fy - .1, fz, dx, dy, dz, (float) len * 1.12f, r, sd * 29 + 1, IceMesh.GLACIER, g, al);
                // A second one leaning against it, shorter.
                IceGrowth.crystal(c, fx + (n.h(86) - .5f) * .4, fy - .05, fz + (n.h(87) - .5f) * .4, dx + (n.h(88) - .5f) * 1.2, dy, dz + (n.h(89) - .5f) * 1.2,
                        (float) len * (.45f + .3f * n.h(90)), r * .7f, sd * 29 + 2, IceMesh.CLEAR, IceGrowth.grow(age, 4 + 4 * n.h(91), 10), al);
                IceGrowth.cluster(c, fx, fy - .05, fz, 0, 1, 0, .4f + .2f * n.h(92), sd * 29 + 3, 4, IceMesh.CLEAR, (age - 1) / 10f * keep, al);
                float frost = PantherMotion.snap(age, 0, 8) * al;
                if (frost > .02f) IcemanGroundFx.rime(c, new Vec3(fx, fy, fz), .45f + .5f * frost, .55f * frost, sd);
            }
        }
        // Where it left the ground: frost spreading wide and a cluster growing up into it.
        if (n.takeoff && budget > 0) {
            Vec3 g0 = new Vec3(n.pos.x, Math.max(n.ground, (float) n.pos.y - 3), n.pos.z);
            budget -= 6;
            IceGrowth.cluster(c, g0.x, g0.y - .05, g0.z, n.tan.x * .3, 1, n.tan.z * .3, 1f * s, sd * 31 + 1, 6, IceMesh.CLEAR, age / 16f * keep, al);
            float frost = PantherMotion.snap(age, 0, 10) * al;
            if (frost > .02f) IcemanGroundFx.rime(c, g0, 1.6f * frost, .6f * frost, sd + 5);
        }
        // A glint on fresh ice now and then.
        if (age < 16 && n.h(95) < .12f)
            IceMesh.sparkle(c, n.pos.add(n.up.scale(.05)).add(n.side.scale((n.h(96) - .5) * hw * 1.6)), .12f, .5f * (1 - age / 16));
    }
}
