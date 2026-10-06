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
 * The ice slide's track (Days of Future Past): physical 3D ice that grows out of the sliding Iceman's feet and follows
 * his real path (along the ground, up the ramps SPACE raises, over the crest and down), built on every client from where
 * it sees him each tick.
 * <p>
 * Forming (SHIFT pressed, while he gets ready): thin frost spreads on the ground under his feet, small crystal nuclei
 * appear one after another out ahead of him and grow, and the ice surface grows out from his feet over them, merging
 * them into one thickening track. Never a ready platform: everything grows in.
 * <p>
 * The track: a shallow channel with raised lips over a keel, banking into the turns, its width and thickness wandering
 * along it, a mix of clear, milky and deep glacier ice in patches. Along it, different pieces of ice rather than one
 * even ribbon: clusters of crystals breaking out of the lips, flat plates of milky ice lying on its surface, crystal
 * pinnacles pointing down and out under the raised stretches, icicles growing down from them; bright glints along the
 * lips, faint frost waves running over the surface just behind him.
 * <p>
 * Ends: let go on the ground, its last stretch cracks at once and sheds a few small shards; let go in the air, its last
 * point crystallises into a cluster of crystals. Dissolving (never all at once, oldest first, from behind him): cracks,
 * then small shards breaking off (pieces dropping from the raised parts), then it turns to frost and dissipates.
 * Points are capped; far away it is drawn plainer.
 */
final class IcemanTrack {
    private static final float SPACING = .45f;
    private static final int MAX_NODES = 600, MAX_TRACKS = 16;

    static final class Node {
        final Vec3 pos, tan, side, up;
        final float born, width, thick, height, lip;
        final int seed, light;
        /** Dissolving stage reached (0 none, 1 shards shed, 2 frost given off); cracked early (the ground exit). */
        int stage; float crackAt = -1;
        Node(Vec3 pos, Vec3 tan, Vec3 side, Vec3 up, float born, float width, float thick, float height, int seed, int light) {
            this.pos = pos; this.tan = tan; this.side = side; this.up = up; this.born = born; this.width = width; this.thick = thick;
            this.height = height; this.seed = seed; this.light = light; lip = .06f + .05f * width;
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
    /** How it ended: 0 still going, 1 on the ground (its end cracks), 2 in the air (its end crystallises); when. */
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
    /** He stopped sliding: the track stops growing; kind 1 on the ground (its end cracks), 2 in the air (its end crystallises). */
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
            // The last stretch cracks at once and a few small shards break off it.
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
        float w = .95f * (.86f + .16f * Mth.sin(dist * .45f + seed) + .08f * Mth.sin(dist * 1.3f + seed * 2));
        // Thicker and more uneven where it stands in the air.
        float th = height < .3f ? .2f : .28f + .1f * Mth.sin(dist * .7f + seed) + .06f * Mth.sin(dist * 2.1f + seed) + Math.min(.14f, height * .02f);
        Node n = new Node(pos, tan, s2, u2, born, w, th, height, (int) (seed * 1000) + nodes.size() * 7 + (int) (dist * 13), IceStage.light(pos));
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

    /** Dissolving: cracks (with the odd crack heard), then small shards shed, then frost given off; old points gone. */
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
                    // Small shards breaking off; bigger pieces dropping from the raised stretches.
                    if (n.height > 1 && n.h(.91) < .34 && IceParticles.count(1, n.pos) > 0) {
                        for (int k = 0; k < 2; k++)
                            IceParticles.shard(n.pos.add(n.up.scale(-n.thick * .5)).add(n.side.scale((IceParticles.rand() - .5) * n.width)),
                                    IceParticles.jitter(.03).add(0, -.02, 0), .12f + .12f * IceParticles.rand(), 40 + (int) (IceParticles.rand() * 20), k == 0 ? IceMesh.CLEAR : IceMesh.MILKY);
                    } else if (n.h(1.3) < .3 && IceParticles.count(1, n.pos) > 0)
                        IceParticles.shard(n.pos.add(n.side.scale((IceParticles.rand() - .5) * n.width)).add(0, .08, 0),
                                IceParticles.jitter(.04).add(0, .06, 0), .04f + .04f * IceParticles.rand(), 26, IceMesh.FRESH);
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
    private static final float[] SS8 = new float[8], UU8 = new float[8], SS5 = new float[5], UU5 = new float[5];
    private static final float[] RA8 = new float[24], RB8 = new float[24], RA5 = new float[15], RB5 = new float[15], CA = new float[15], CB = new float[15];

    /** Draws every track, with the ice growing out ahead of his feet on the live ones. */
    static void draw(IceStage st, IceMesh.Ctx c) {
        var mc = Minecraft.getInstance();
        float now = st.time;
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
                if (prev != null) segment(st, c, prev, node, now, i, t.live && i >= list.size() - 14);
                prev = node;
            }
            if (t.endKind == 2) crest(c, t, list.get(list.size() - 1), now);
        }
    }
    private static Node head(Node last, Vec3 feet, float born) {
        Vec3 tan = feet.subtract(new Vec3(last.pos.x, feetY(last), last.pos.z));
        tan = tan.lengthSqr() < 1e-6 ? last.tan : tan.normalize();
        Vec3 pos = last.height < .3f ? new Vec3(feet.x, Math.max(feet.y, last.pos.y - .06) + .06, feet.z) : feet.add(0, -.03, 0);
        return new Node(pos, tan, last.side, last.up, born, last.width, last.thick, last.height, last.seed + 3, last.light);
    }
    /**
     * Forming, while he gets ready: thin frost spreading under his feet, crystal nuclei appearing one after another out
     * ahead of him and growing, then sinking into the surface that grows over them.
     */
    private static void formation(IceStage st, IceMesh.Ctx c, IcemanTrack t, float now, float age) {
        if (age > SLIDE_PREP + 12 || t.formAt3 == null || st.far(t.formAt3)) return;
        Vec3 o = t.formAt3, dir = t.formDir, side = new Vec3(dir.z, 0, -dir.x);
        Node first = t.nodes.get(0);
        float y = (float) (first.height < .3f ? first.pos.y - .045 : first.pos.y - .03);
        c.light = first.light;
        // The frost under his feet.
        float frost = PantherMotion.snap(age, 0, 3.5f) * (1 - PantherMotion.k(age, SLIDE_PREP + 4, SLIDE_PREP + 12));
        if (frost > .01f) {
            int k = 9;
            float r = 1.0f * frost;
            for (int i = 0; i < k; i++) {
                float a0 = Mth.TWO_PI * i / k, a1 = Mth.TWO_PI * (i + 1) / k;
                float j0 = .75f + .45f * (float) IceMesh.hash(t.seed + i), j1 = .75f + .45f * (float) IceMesh.hash(t.seed + (i + 1) % k);
                IceMesh.tri(c, (float) o.x + Mth.cos(a0) * r * j0, y + .015f, (float) o.z + Mth.sin(a0) * r * j0,
                        (float) o.x + Mth.cos(a1) * r * j1, y + .015f, (float) o.z + Mth.sin(a1) * r * j1, (float) o.x, y + .015f, (float) o.z, IceMesh.FROST, .6f * frost);
            }
        }
        // The nuclei out ahead, appearing in turn, growing, then merging into the surface.
        for (int i = 0; i < 9; i++) {
            float at = 1.5f + i * .45f;
            float g = PantherMotion.snap(age, at, at + 2.5f) * (1 - PantherMotion.k(age, SLIDE_PREP + 1 + i * .3f, SLIDE_PREP + 5 + i * .3f));
            if (g <= .01f) continue;
            double h = IceMesh.hash(t.seed * 3 + i);
            Vec3 base = o.add(dir.scale(.35 + i * .28)).add(side.scale((h - .5) * .5)).add(0, y - o.y, 0);
            Vec3 up = new Vec3(0, 1, 0).add(dir.scale(.5)).add(side.scale((h - .5) * .8)).normalize();
            IceMesh.crystal(c, base, up, (.14f + .14f * (float) h) * g, .05f * g + .01f, 5, (int) (t.seed * 7) + i, i % 3 == 0 ? IceMesh.MILKY : IceMesh.CLEAR, 1, .5f);
            IceMesh.sparkle(c, base.add(up.scale(.1 * g)), .12f, .7f * g * (1 - g * .5f));
        }
    }
    /** Let go in the air: the track's last point crystallises into a cluster. */
    private static void crest(IceMesh.Ctx c, IcemanTrack t, Node n, float now) {
        float age = now - t.endAt;
        float g = PantherMotion.snap(age, 0, 6) * size(n, now);
        if (g <= .01f) return;
        c.light = n.light;
        for (int i = 0; i < 6; i++) {
            double h = IceMesh.hash(n.seed + i * 3.3);
            float a = (float) (h * Mth.TWO_PI);
            Vec3 dir = n.tan.scale(.9).add(n.side.scale(Mth.cos(a) * .7)).add(n.up.scale(Mth.sin(a) * .7)).normalize();
            IceMesh.crystal(c, n.pos.add(n.up.scale(-n.thick * .3)), dir, (.3f + .45f * (float) h) * g, .07f * g, 6, n.seed + i, i % 2 == 0 ? IceMesh.CLEAR : IceMesh.MILKY,
                    alpha(n, now), .55f);
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
    private static IceMesh.Mat patch(Node n) {
        double h = n.h(.53);
        return h < .6 ? IceMesh.CLEAR : h < .82 ? IceMesh.GLACIER : IceMesh.MILKY;
    }
    private static void segment(IceStage st, IceMesh.Ctx c, Node a, Node b, float now, int index, boolean fresh) {
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
        IceMesh.Mat mat = patch(a);
        if (far) {
            ring5(a, sa, 1, 0, RA5); ring5(b, sb, 1, 0, RB5);
            IceMesh.loft(c, RA5, RB5, mat, al * (1 - fr), false, false);
            if (fr > .01f) IceMesh.loft(c, RA5, RB5, IceMesh.FROST, al * fr, false, false);
            c.flash = 0;
            return;
        }
        ring8(a, sa, RA8); ring8(b, sb, RB8);
        IceMesh.loft(c, RA8, RB8, mat, al * (1 - .7f * fr), false, false);
        // The milky body inside it; frost taking over the surface as it dissolves.
        ring5(a, sa, .5f, -.45f, CA); ring5(b, sb, .5f, -.45f, CB);
        IceMesh.loft(c, CA, CB, IceMesh.MILKY, al * .9f * (1 - fr), false, false);
        if (fr > .01f) IceMesh.loft(c, RA8, RB8, IceMesh.FROST, al * fr * .8f, false, false);
        details(c, a, sa, al * (1 - fr), now, index);
        // Cracks: on dissolving, or at once at the end where he braked.
        float crack = m > 0 && m < .6f ? Mth.sin(Mth.clamp(m / .6f, 0, 1) * Mth.PI) : 0;
        if (a.crackAt >= 0 && now >= a.crackAt) crack = Math.max(crack, PantherMotion.k(now - a.crackAt, 0, 2) * (1 - fr));
        if (crack > .01f && (a.seed % 3 == 0 || a.crackAt >= 0)) {
            float k = crack * al;
            Vec3 p0 = a.pos.add(a.side.scale((a.h(1) - .5) * a.width * .7 * sa)).add(a.up.scale(.012));
            Vec3 p1 = b.pos.add(b.side.scale((a.h(2) - .5) * b.width * .7 * sb)).add(b.up.scale(.012));
            Vec3 mid = p0.lerp(p1, .5).add(a.side.scale((a.h(3) - .5) * .25 * sa));
            IceMesh.line(c, p0, mid, .018f, .6f * k, .85f * k, k);
            IceMesh.line(c, mid, p1, .018f, .6f * k, .85f * k, k);
        }
        // Faint frost waves running over the surface just behind him.
        if (fresh) {
            float wave = Mth.sin(index * .9f - now * .8f);
            float k = (float) Math.pow(Math.max(0, wave), 6) * .35f * al * sa;
            if (k > .01f) {
                Vec3 l = a.pos.add(a.side.scale(-a.width * .45 * sa)).add(a.up.scale(.02)), r = a.pos.add(a.side.scale(a.width * .45 * sa)).add(a.up.scale(.02));
                IceMesh.line(c, l, r, .014f, .6f * k, .85f * k, k);
            }
        }
        c.flash = 0;
    }
    /** The pieces along it: crystal clusters out of the lips, milky plates on the surface, pinnacles and icicles under the raised parts, glints along the lips. */
    private static void details(IceMesh.Ctx c, Node n, float s, float al, float now, int index) {
        if (s < .2f || al <= .02f) return;
        double h = n.h(.73), h2 = n.h(1.37), h3 = n.h(2.11);
        float age = now - n.born;
        if (h < .3) {
            // A cluster of crystals breaking out of a lip.
            float sg = h2 < .5 ? 1 : -1;
            Vec3 base = n.pos.add(n.side.scale(sg * n.width * .48 * s)).add(n.up.scale(n.lip * s));
            int count = 1 + (int) (h3 * 3);
            for (int i = 0; i < count; i++) {
                double hi = n.h(3.1 + i);
                Vec3 dir = n.side.scale(sg * (.6 + .4 * hi)).add(n.up.scale(.5 + .4 * hi)).add(n.tan.scale(-.4 + .5 * hi)).normalize();
                IceMesh.crystal(c, base.add(n.tan.scale((hi - .5) * .3)), dir, (.14f + .3f * (float) hi) * s, (.035f + .025f * (float) hi) * s, 5, n.seed + i,
                        hi < .3 ? IceMesh.MILKY : IceMesh.CLEAR, al, .45f);
            }
        } else if (h < .42) {
            // A flat plate of milky ice lying on the surface.
            float sg = h2 < .5 ? 1 : -1;
            Vec3 at = n.pos.add(n.side.scale(sg * n.width * .22 * s)).add(n.up.scale(.035));
            IceMesh.shard(c, at, n.tan.add(n.side.scale((h3 - .5) * .6)), .16f * s, n.seed + 5, IceMesh.MILKY, al * .9f);
        }
        if (n.height > 1.2f) {
            if (h2 < .3) {
                // Icicles growing down from the raised stretch.
                float g = PantherMotion.k(age, 4, 34) * s;
                Vec3 base = n.pos.add(n.up.scale(-n.thick * .9 * s)).add(n.side.scale((h - .5) * n.width * .5));
                Vec3 down = new Vec3((h - .5) * .15, -1, (h2 - .5) * .15).normalize();
                IceMesh.crystal(c, base, down, (.25f + .75f * (float) h3) * g, .05f * Math.min(1, g * 2), 4, n.seed + 9, IceMesh.CLEAR, al, .3f);
            } else if (h2 > .82) {
                // A pinnacle pointing down and out from the keel.
                float sg = h3 < .5 ? 1 : -1;
                Vec3 base = n.pos.add(n.up.scale(-n.thick * .7 * s));
                Vec3 dir = n.up.scale(-1).add(n.side.scale(sg * .7)).add(n.tan.scale(-.3)).normalize();
                IceMesh.crystal(c, base, dir, (.3f + .4f * (float) h) * s, .09f * s, 6, n.seed + 11, IceMesh.GLACIER, al, .4f);
            }
        }
        if (index % 2 == 0) {
            // The bright lips.
            float k = .28f * al * s * (.6f + .4f * Mth.sin(now * .3f + index));
            for (int sg = -1; sg <= 1; sg += 2) {
                Vec3 l0 = n.pos.add(n.side.scale(sg * n.width * .5 * s)).add(n.up.scale(n.lip * s));
                IceMesh.line(c, l0, l0.add(n.tan.scale(SPACING)), .012f, .6f * k, .85f * k, k);
            }
        }
    }
    /** The full cross-section: a shallow channel with raised lips over a keel. */
    private static void ring8(Node n, float s, float[] out) {
        float w = n.width * s, th = n.thick * s, lip = n.lip * s;
        float[] ss = SS8, uu = UU8;
        ss[0] = -.5f * w; ss[1] = -.25f * w; ss[2] = .25f * w; ss[3] = .5f * w; ss[4] = .42f * w; ss[5] = .15f * w; ss[6] = -.15f * w; ss[7] = -.42f * w;
        uu[0] = lip; uu[1] = -.015f * s; uu[2] = -.015f * s; uu[3] = lip; uu[4] = -.55f * th; uu[5] = -th; uu[6] = -th; uu[7] = -.55f * th;
        put(n, ss, uu, out);
    }
    /** A plainer cross-section (far away), or the milky body inside (scale k, lowered by drop of its thickness). */
    private static void ring5(Node n, float s, float k, float drop, float[] out) {
        float w = n.width * s * k, th = n.thick * s * k, lip = n.lip * s * k, o = drop * n.thick * s;
        float[] ss = SS5, uu = UU5;
        ss[0] = -.5f * w; ss[1] = .5f * w; ss[2] = .35f * w; ss[3] = 0; ss[4] = -.35f * w;
        uu[0] = lip + o; uu[1] = lip + o; uu[2] = -.6f * th + o; uu[3] = -th + o; uu[4] = -.6f * th + o;
        put(n, ss, uu, out);
    }
    private static void put(Node n, float[] ss, float[] uu, float[] out) {
        for (int i = 0; i < ss.length; i++) {
            out[i * 3] = (float) (n.pos.x + n.side.x * ss[i] + n.up.x * uu[i]);
            out[i * 3 + 1] = (float) (n.pos.y + n.side.y * ss[i] + n.up.y * uu[i]);
            out[i * 3 + 2] = (float) (n.pos.z + n.side.z * ss[i] + n.up.z * uu[i]);
        }
    }
}
