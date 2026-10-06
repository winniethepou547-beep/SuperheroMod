package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The ice slide's track: a beam of bright blue Minecraft ice that grows out of the sliding Iceman's feet and follows his
 * real path (along the ground, up the ramps SPACE raises, over the crest and down), built on every client from where it
 * sees him each tick. After the user's reference (a blocky Iceman riding a straight-edged slide of ice blocks): it is
 * NOT a crystal and not a round tube.
 * <p>
 * The beam: a rectangular cross-section (a flat top as wide as the track, flat sides, a flat bottom), banking into the
 * turns, built as a chain of six-faced blocks between consecutive cross-sections, textured like ice blocks (the texture
 * keeps to the world's block grid). Bright clear ice with patches of milky and deep glacier ice, a thin white glint
 * along its top edges. Here and there a small ice cube sits on an edge; under the raised stretches a few square
 * icicles and ice cubes hang from its underside.
 * <p>
 * Forming (SHIFT pressed, while he gets ready): frost spreads on the ground under his feet (pixel frost), small ice cubes
 * appear one after another out ahead of him and grow, and the beam grows out from his feet over them. Never a ready
 * platform: everything grows in.
 * <p>
 * Ends: let go on the ground, its last stretch cracks at once and sheds a few small cubes of ice; let go in the air, its
 * last point freezes into a cluster of ice cubes. Dissolving (never all at once, oldest first, from behind him): cracks
 * (thin bright lines on its top), then cube chunks breaking off and falling (bigger ones from the raised parts), then it
 * turns to frost and dissipates. Points are capped; far away it is drawn plainer (no pieces on it).
 */
final class IcemanTrack {
    private static final float SPACING = .45f;
    private static final int MAX_NODES = 600, MAX_TRACKS = 16;

    static final class Node {
        final Vec3 pos, tan, side, up;
        final float born, width, thick, height;
        final int seed, light;
        /** Which patch of ice it is in (clear, milky, glacier come in stretches of a couple of blocks). */
        final int patch;
        /** Dissolving stage reached (0 none, 1 chunks shed, 2 frost given off); cracked early (the ground exit). */
        int stage; float crackAt = -1;
        Node(Vec3 pos, Vec3 tan, Vec3 side, Vec3 up, float born, float width, float thick, float height, int seed, int light, int patch) {
            this.pos = pos; this.tan = tan; this.side = side; this.up = up; this.born = born; this.width = width; this.thick = thick;
            this.height = height; this.seed = seed; this.light = light; this.patch = patch;
        }
        double h(double k) { return IceMesh.hash(seed * k + 1.7); }
    }

    final int owner;
    final List<Node> nodes = new ArrayList<>();
    boolean live = true;
    float dist, bank, seed;
    Vec3 lastTan;
    /** Forming: when, where his feet were, which way. */
    final float formAt; Vec3 formAt3, formDir;
    /** How it ended: 0 still going, 1 on the ground (its end cracks), 2 in the air (its end freezes into cubes); when. */
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
    /** He stopped sliding: the track stops growing; kind 1 on the ground (its end cracks), 2 in the air (its end freezes into cubes). */
    static void stop(int id, int kind) {
        IcemanTrack t = LIVE.remove(id);
        if (t == null) return;
        t.live = false;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = mc.level.getGameTime();
        t.endKind = kind;
        t.endAt = now;
        if (kind == 1) {
            // The last stretch cracks at once and a few small cubes of ice break off it.
            int n = t.nodes.size();
            for (int i = Math.max(0, n - 10); i < n; i++) t.nodes.get(i).crackAt = now + (n - 1 - i) * .6f;
            for (int i = Math.max(0, n - 6); i < n; i += 2) {
                Node nd = t.nodes.get(i);
                IceParticles.shard(nd.pos.add(nd.side.scale((IceParticles.rand() - .5) * nd.width)).add(0, .1, 0),
                        IceParticles.jitter(.05).add(0, .12, 0), .06f + .05f * IceParticles.rand(), 30, IceMesh.FRESH);
            }
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
        for (int i = 1; i <= steps; i++) place(level, from.lerp(feet, i / (double) steps), last, now - (steps - i) * (1f / steps));
        while (nodes.size() > MAX_NODES) nodes.remove(0);
    }
    private static float feetY(Node n) { return (float) (n.height < .3f ? n.pos.y - .06 : n.pos.y + .03); }

    private void place(Level level, Vec3 feet, Node prev, float born) {
        Vec3 tan = prev == null ? null : feet.subtract(new Vec3(prev.pos.x, feetY(prev), prev.pos.z));
        if (tan == null || tan.lengthSqr() < 1e-6) tan = lastTan != null ? lastTan : new Vec3(0, 0, 1);
        tan = tan.normalize();
        // The bank: leaning into the turn by how much the way turned since the last point.
        if (lastTan != null) {
            float a0 = (float) Math.atan2(-lastTan.x, lastTan.z), a1 = (float) Math.atan2(-tan.x, tan.z);
            float turn = Mth.wrapDegrees((a1 - a0) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            bank = Mth.lerp(.35f, bank, Mth.clamp(turn * 3.2f, -.5f, .5f));
        }
        lastTan = tan;
        Vec3 side = new Vec3(tan.z, 0, -tan.x);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = tan.cross(side).normalize();
        float cb = Mth.cos(bank), sb = Mth.sin(bank);
        Vec3 s2 = side.scale(cb).add(up.scale(sb)), u2 = up.scale(cb).subtract(side.scale(sb));
        float ground = ground(level, feet);
        float height = (float) (feet.y - ground);
        Vec3 pos = height < .3f ? new Vec3(feet.x, Math.max(feet.y, ground) + .06, feet.z) : feet.add(0, -.03, 0);
        dist += prev == null ? 0 : SPACING;
        // A straight-edged beam: the same width all along; thicker where it stands in the air (its bottom only
        // wandering slowly, so it stays a flat-sided block).
        float w = .98f;
        float th = height < .3f ? .2f : .3f + .06f * Mth.sin(dist * .5f + seed) + Math.min(.14f, height * .02f);
        Node n = new Node(pos, tan, s2, u2, born, w, th, height, (int) (seed * 1000) + nodes.size() * 7 + (int) (dist * 13), IceStage.light(pos),
                (int) Math.floor(dist / 2.2f));
        nodes.add(n);
        // Frost spraying off both lips where it runs on the ground.
        if (height < .4f) {
            int k = IceParticles.count(2, pos);
            for (int i = 0; i < k; i++) {
                float sg = i % 2 == 0 ? 1 : -1;
                Vec3 at = pos.add(s2.scale(sg * w * .5));
                IceParticles.snow(at, s2.scale(sg * (.06 + .08 * IceParticles.rand())).add(tan.scale(-.05)).add(0, .05 + .05 * IceParticles.rand(), 0),
                        .022f + .02f * IceParticles.rand(), 14 + (int) (IceParticles.rand() * 10));
            }
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

    /** Dissolving: cracks (with the odd crack heard), then cube chunks shed, then frost given off; old points gone. */
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
                // Fresh ice breathing a light dry-ice mist that spills off its lips.
                if (age < 12 && IceParticles.rand() < .05f && IceParticles.count(1, n.pos) > 0) {
                    float sg = IceParticles.rand() < .5f ? 1 : -1;
                    IceParticles.mist(n.pos.add(n.side.scale(sg * n.width * .5)), n.side.scale(sg * .008).add(0, -.006, 0), .26f, .02f, .13f, 28);
                }
                float m = (age - SLIDE_TRACK_LIFE) / SLIDE_TRACK_MELT;
                if (m > 0 && m < .05f && n.h(2.3) < .045)
                    mc.level.playLocalSound(n.pos.x, n.pos.y, n.pos.z, ModSounds.ICEMAN_CRACK.get(), SoundSource.PLAYERS, .25f, 1.2f + IceParticles.rand() * .3f, false);
                if (n.stage == 0 && m >= .3f) {
                    n.stage = 1;
                    // Small cubes breaking off its top; bigger chunks dropping from the raised stretches.
                    if (n.height > 1 && n.h(.91) < .34 && IceParticles.count(1, n.pos) > 0) {
                        for (int k = 0; k < 2; k++)
                            IceParticles.shard(n.pos.add(n.up.scale(-n.thick * .5)).add(n.side.scale((IceParticles.rand() - .5) * n.width)),
                                    IceParticles.jitter(.03).add(0, -.02, 0), .16f + .14f * IceParticles.rand(), 40 + (int) (IceParticles.rand() * 20), k == 0 ? IceMesh.CLEAR : IceMesh.MILKY);
                    } else if (n.h(1.3) < .3 && IceParticles.count(1, n.pos) > 0)
                        IceParticles.shard(n.pos.add(n.side.scale((IceParticles.rand() - .5) * n.width)).add(0, .08, 0),
                                IceParticles.jitter(.04).add(0, .06, 0), .07f + .06f * IceParticles.rand(), 30, IceMesh.FRESH);
                }
                if (n.stage == 1 && m >= .6f) {
                    n.stage = 2;
                    // Frost given off as it goes.
                    if (n.h(1.7) < .3 && IceParticles.count(1, n.pos) > 0) {
                        IceParticles.frostDust(n.pos, Vec3.ZERO, .7f);
                        IceParticles.mist(n.pos, new Vec3(0, -.004, 0), .3f, .025f, .16f, 26);
                    }
                }
            }
            if (gone > 0) t.nodes.subList(0, gone).clear();
            if (t.nodes.isEmpty() && !t.live) it.remove();
        }
    }

    // ------------------------------------------------------------------ drawing
    private static final float[] RA = new float[12], RB = new float[12];

    /** Draws every track, with the ice growing out ahead of his feet on the live ones. */
    static void draw(IceStage st, IceMesh.Ctx c) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = st.time;
        // The beam keeps to the world's block grid (its texture is not pinned to anything moving).
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
                // Out ahead of his feet: the ice growing out (while he gets ready, out along his way; then just ahead of him).
                float age = now - t.formAt;
                float reach = 2.6f * PantherMotion.k(age, 3.5f, SLIDE_PREP + 1) * (1 - PantherMotion.k(age, SLIDE_PREP + 3, SLIDE_PREP + 9));
                Vec3 ahead = vel.scale(1.3);
                if (reach > ahead.length()) ahead = (vel.lengthSqr() > 1e-4 ? vel.normalize() : t.formDir).scale(reach);
                feetNode = head(last, feet, now - .8f);
                leadNode = head(last, feet.add(ahead), now + 1.2f);
                formation(st, c, t, now, age);
            }
            int n = list.size() + (feetNode != null ? 2 : 0);
            Node prev = null;
            for (int i = 0; i < n; i++) {
                Node node = i < list.size() ? list.get(i) : i == list.size() ? feetNode : leadNode;
                if (prev != null) segment(st, c, t, prev, node, now, i, t.live && i >= list.size() - 14, i == 1, i == n - 1);
                prev = node;
            }
            if (t.endKind == 2) crest(c, t, list.get(list.size() - 1), now);
        }
    }
    private static Node head(Node last, Vec3 feet, float born) {
        Vec3 tan = feet.subtract(new Vec3(last.pos.x, feetY(last), last.pos.z));
        tan = tan.lengthSqr() < 1e-6 ? last.tan : tan.normalize();
        Vec3 pos = last.height < .3f ? new Vec3(feet.x, Math.max(feet.y, last.pos.y - .06) + .06, feet.z) : feet.add(0, -.03, 0);
        return new Node(pos, tan, last.side, last.up, born, last.width, last.thick, last.height, last.seed + 3, last.light, last.patch);
    }
    /**
     * Forming, while he gets ready: pixel frost spreading under his feet, small ice cubes appearing one after another out
     * ahead of him and growing, then sinking into the beam that grows over them.
     */
    private static void formation(IceStage st, IceMesh.Ctx c, IcemanTrack t, float now, float age) {
        if (age > SLIDE_PREP + 12 || t.formAt3 == null || st.far(t.formAt3)) return;
        Vec3 o = t.formAt3, dir = t.formDir, side = new Vec3(dir.z, 0, -dir.x);
        Node first = t.nodes.get(0);
        float y = (float) (first.height < .3f ? first.pos.y - .045 : first.pos.y - .03);
        c.light = first.light;
        // The frost under his feet.
        float frost = PantherMotion.snap(age, 0, 3.5f) * (1 - PantherMotion.k(age, SLIDE_PREP + 4, SLIDE_PREP + 12));
        if (frost > .01f) IcemanGroundFx.rime(c, new Vec3(o.x, y, o.z), 1.0f * frost, .65f * frost, (int) (t.seed * 13));
        // The cubes out ahead, appearing in turn, growing, then merging into the beam.
        float yaw = IceParticles.yawOf(dir.x, dir.z);
        for (int i = 0; i < 9; i++) {
            float at = 1.5f + i * .45f;
            float g = PantherMotion.snap(age, at, at + 2.5f) * (1 - PantherMotion.k(age, SLIDE_PREP + 1 + i * .3f, SLIDE_PREP + 5 + i * .3f));
            if (g <= .01f) continue;
            double h = IceMesh.hash(t.seed * 3 + i), h2 = IceMesh.hash(t.seed * 5 + i * 1.7);
            float size = (.12f + .13f * (float) h) * g;
            Vec3 base = o.add(dir.scale(.35 + i * .28)).add(side.scale((h - .5) * .5));
            IceParticles.cube(c, base.x, y + size * .38f, base.z, size, size, size, yaw + (float) (h2 - .5) * .9f, (float) (h - .5) * .3f, (float) (h2 - .5) * .25f,
                    i % 3 == 0 ? IceMesh.MILKY : IceMesh.CLEAR, .55f + .45f * g);
            IceMesh.sparkle(c, new Vec3(base.x, y + size * .9f, base.z), .12f, .6f * g * (1 - g * .5f));
        }
    }
    /** Let go in the air: the track's last point freezes into a cluster of ice cubes. */
    private static void crest(IceMesh.Ctx c, IcemanTrack t, Node n, float now) {
        float age = now - t.endAt;
        float g = PantherMotion.snap(age, 0, 6) * size(n, now);
        if (g <= .01f) return;
        c.light = n.light;
        float al = alpha(n, now), yaw = IceParticles.yawOf(n.tan.x, n.tan.z);
        Vec3 o = n.pos.add(n.up.scale(-n.thick * .45));
        for (int i = 0; i < 5; i++) {
            double h = IceMesh.hash(n.seed + i * 3.3), h2 = IceMesh.hash(n.seed + i * 5.9);
            float a = (float) (h * Mth.TWO_PI);
            Vec3 dir = n.tan.scale(.7).add(n.side.scale(Mth.cos(a) * .7)).add(n.up.scale(Mth.sin(a) * .6));
            float size = (.22f + .22f * (float) h2) * g;
            Vec3 at = o.add(dir.scale((.12 + .22 * h) * g));
            IceParticles.cube(c, at.x, at.y, at.z, size, size * (.8f + .3f * (float) h), size, yaw + (float) (h - .5) * 1.2f, (float) (h2 - .5) * .7f, (float) (h - .5) * .6f,
                    i % 2 == 0 ? IceMesh.CLEAR : IceMesh.MILKY, al);
        }
    }
    /** How much of a point's ice there is (growing in, then shrinking as it dissolves), how opaque, how frosted. */
    private static float size(Node n, float now) {
        float age = now - n.born;
        float grow = PantherMotion.snap(age + 1.2f, 0, 4.5f);
        float m = (age - SLIDE_TRACK_LIFE) / SLIDE_TRACK_MELT;
        return grow * (1 - .75f * PantherMotion.ease((m - .3f) / .7f));
    }
    private static float alpha(Node n, float now) {
        float m = (now - n.born - SLIDE_TRACK_LIFE) / SLIDE_TRACK_MELT;
        return 1 - PantherMotion.ease((m - .55f) / .45f);
    }
    private static float frosted(Node n, float now) {
        float m = (now - n.born - SLIDE_TRACK_LIFE) / SLIDE_TRACK_MELT;
        return PantherMotion.k(m, .45f, .8f);
    }
    /** Mostly bright clear ice, with stretches of milky and of deep glacier ice. */
    private static IceMesh.Mat patch(IcemanTrack t, Node n) {
        double h = IceMesh.hash(t.seed * 7.3 + n.patch * 1.91);
        return h < .74 ? IceMesh.CLEAR : h < .88 ? IceMesh.MILKY : IceMesh.GLACIER;
    }
    private static void segment(IceStage st, IceMesh.Ctx c, IcemanTrack t, Node a, Node b, float now, int index, boolean fresh, boolean first, boolean last) {
        double d2 = a.pos.distanceToSqr(st.cam);
        if (d2 > 110 * 110) return;
        float sa = size(a, now), sb = size(b, now);
        if (sa <= .002f && sb <= .002f) return;
        float al = Math.min(alpha(a, now), alpha(b, now));
        if (al <= .01f) return;
        boolean far = st.far(a.pos);
        float fr = frosted(a, now);
        c.light = a.light;
        float m = (now - a.born - SLIDE_TRACK_LIFE) / SLIDE_TRACK_MELT;
        c.flash = m > 0 && m < .35f ? .12f * Mth.sin(m / .35f * Mth.PI) : 0;
        // The block between the two cross-sections (its ends closed where the beam begins and ends).
        ring(a, sa, RA); ring(b, sb, RB);
        IceMesh.Mat mat = patch(t, a);
        float body = al * (1 - .7f * fr);
        IceMesh.loft(c, RA, RB, mat, body, false, false);
        if (first) end(c, RA, mat, body);
        if (last) end(c, RB, mat, body);
        // Frost taking over its surface as it dissolves.
        if (fr > .01f && !far) IceMesh.loft(c, RA, RB, IceMesh.FROST, al * fr * .8f, false, false);
        if (far) { c.flash = 0; return; }
        details(c, a, sa, al * (1 - fr), now, index);
        // Cracks on its top: on dissolving, or at once at the end where he braked.
        float crack = m > 0 && m < .6f ? Mth.sin(Mth.clamp(m / .6f, 0, 1) * Mth.PI) : 0;
        if (a.crackAt >= 0 && now >= a.crackAt) crack = Math.max(crack, PantherMotion.k(now - a.crackAt, 0, 2) * (1 - fr));
        if (crack > .01f && (a.seed % 3 == 0 || a.crackAt >= 0)) {
            float k = crack * al;
            Vec3 p0 = a.pos.add(a.side.scale((a.h(1) - .5) * a.width * .7 * sa)).add(a.up.scale(.012));
            Vec3 p1 = b.pos.add(b.side.scale((a.h(2) - .5) * b.width * .7 * sb)).add(b.up.scale(.012));
            Vec3 mid = p0.lerp(p1, .5).add(a.side.scale((a.h(3) - .5) * .25 * sa));
            IceMesh.line(c, p0, mid, .016f, .6f * k, .85f * k, k);
            IceMesh.line(c, mid, p1, .016f, .6f * k, .85f * k, k);
        }
        // Faint frost waves running over its top just behind him.
        if (fresh) {
            float wave = Mth.sin(index * .9f - now * .8f);
            float k = (float) Math.pow(Math.max(0, wave), 6) * .3f * al * sa;
            if (k > .01f) {
                Vec3 l = a.pos.add(a.side.scale(-a.width * .48 * sa)).add(a.up.scale(.01)), r = a.pos.add(a.side.scale(a.width * .48 * sa)).add(a.up.scale(.01));
                IceMesh.line(c, l, r, .012f, .8f * k, .92f * k, k);
            }
        }
        c.flash = 0;
    }
    /** Closes the beam's end (its cross-section). */
    private static void end(IceMesh.Ctx c, float[] r, IceMesh.Mat mat, float a) {
        IceMesh.quad(c, r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10], r[11], mat, a);
    }
    /** The pieces on it: now and then a small ice cube on an edge; square icicles and cubes hanging under the raised parts; the white glint on its top edges. */
    private static void details(IceMesh.Ctx c, Node n, float s, float al, float now, int index) {
        if (s < .2f || al <= .02f) return;
        double h = n.h(.73), h2 = n.h(1.37), h3 = n.h(2.11);
        float age = now - n.born, yaw = IceParticles.yawOf(n.tan.x, n.tan.z);
        float th = n.thick * s;
        if (h < .085) {
            // A small cube of ice frozen onto an edge, half sunk into it.
            float sg = h2 < .5 ? 1 : -1, size = (.15f + .12f * (float) h3) * s;
            Vec3 at = n.pos.add(n.side.scale(sg * (n.width * .5 * s - size * .3))).add(n.up.scale(size * .22));
            IceParticles.cube(c, at.x, at.y, at.z, size, size, size, yaw + (float) (h3 - .5) * 1.1f, (float) (h2 - .5) * .4f, (float) (h3 - .5) * .4f,
                    h3 < .35 ? IceMesh.MILKY : IceMesh.CLEAR, al);
        }
        if (n.height > 1.2f) {
            if (h2 < .2) {
                // A square icicle growing down from the raised stretch.
                float g = PantherMotion.k(age, 4, 34) * s;
                Vec3 base = n.pos.add(n.up.scale(-th * .97)).add(n.side.scale((h - .5) * n.width * .6));
                IceParticles.spike(c, base.x, base.y, base.z, (h - .5) * .12, -1, (h2 - .5) * .12, (.22f + .55f * (float) h3) * g, .065f * Math.min(1, g * 2),
                        yaw, 2, IceMesh.CLEAR, al);
            } else if (h2 > .88) {
                // An ice cube stuck under the keel.
                float size = (.24f + .14f * (float) h) * s;
                Vec3 at = n.pos.add(n.up.scale(-th - size * .22)).add(n.side.scale((h3 - .5) * n.width * .4));
                IceParticles.cube(c, at.x, at.y, at.z, size, size * .85f, size, yaw + (float) (h - .5) * .8f, (float) (h3 - .5) * .3f, (float) (h - .5) * .3f,
                        h3 < .5 ? IceMesh.GLACIER : IceMesh.CLEAR, al);
            }
        }
        if (index % 2 == 0) {
            // The glint along its top edges (white, wet).
            float k = .2f * al * s * (.6f + .4f * Mth.sin(now * .3f + index));
            for (int sg = -1; sg <= 1; sg += 2) {
                Vec3 l0 = n.pos.add(n.side.scale(sg * n.width * .5 * s)).add(n.up.scale(.004));
                IceMesh.line(c, l0, l0.add(n.tan.scale(SPACING)), .01f, .8f * k, .92f * k, k);
            }
        }
    }
    /** The cross-section: a rectangle, its top at the point (where his feet run), as wide as the track, thick below it. */
    private static void ring(Node n, float s, float[] out) {
        float w = n.width * s * .5f, th = n.thick * s;
        put(n, 0, -w, 0, out); put(n, 1, w, 0, out); put(n, 2, w, -th, out); put(n, 3, -w, -th, out);
    }
    private static void put(Node n, int i, float ss, float uu, float[] out) {
        out[i * 3] = (float) (n.pos.x + n.side.x * ss + n.up.x * uu);
        out[i * 3 + 1] = (float) (n.pos.y + n.side.y * ss + n.up.y * uu);
        out[i * 3 + 2] = (float) (n.pos.z + n.side.z * ss + n.up.z * uu);
    }
}
