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
 * The ice slide's track (Days of Future Past): a continuous ribbon of ice that grows in under the sliding Iceman's feet
 * and follows his path, built on every client from where it sees him each tick.
 * <p>
 * Its cross-section is a shallow channel with raised lips and a keel under it; it banks into the turns, its width and
 * thickness wander along it, small crystals stand out of its lips, icicles grow down from the stretches raised over the
 * ground, it sprays frost where it touches the ground. It grows in just ahead of and under him (from nothing, never
 * popping), stays SLIDE_TRACK_LIFE ticks, then cracks (glinting lines, the odd crack), the raised parts drop pieces and
 * it melts away over SLIDE_TRACK_MELT, oldest first. Points are capped; far away it is drawn plainer.
 */
final class IcemanTrack {
    private static final float SPACING = .45f;
    private static final int MAX_NODES = 600, MAX_TRACKS = 16;

    static final class Node {
        final Vec3 pos, tan, side, up;
        final float born, width, thick, height, lip;
        final int seed, light;
        boolean broke;
        Node(Vec3 pos, Vec3 tan, Vec3 side, Vec3 up, float born, float width, float thick, float height, int seed, int light) {
            this.pos = pos; this.tan = tan; this.side = side; this.up = up; this.born = born; this.width = width; this.thick = thick;
            this.height = height; this.seed = seed; this.light = light; lip = .06f + .05f * width;
        }
    }

    final int owner;
    final List<Node> nodes = new ArrayList<>();
    boolean live = true;
    float dist, bank, seed;
    Vec3 lastTan;

    private IcemanTrack(int owner) { this.owner = owner; seed = IceParticles.rand() * 100; }

    private static final List<IcemanTrack> ALL = new ArrayList<>();
    private static final Map<Integer, IcemanTrack> LIVE = new HashMap<>();

    static boolean any() { return !ALL.isEmpty(); }
    static void clear() { ALL.clear(); LIVE.clear(); }

    // ------------------------------------------------------------------ growing and melting (every client tick)
    /** Grows the track under this Iceman while he slides (called every tick for each one sliding). */
    static void grow(Entity e) {
        IcemanTrack t = LIVE.get(e.getId());
        // A jump in his place (a teleport): a new piece of track from there.
        if (t != null && !t.nodes.isEmpty() && t.nodes.get(t.nodes.size() - 1).pos.distanceToSqr(e.position()) > 36) { stop(e.getId()); t = null; }
        if (t == null) {
            if (ALL.size() >= MAX_TRACKS) { IcemanTrack old = ALL.remove(0); LIVE.values().remove(old); }
            t = new IcemanTrack(e.getId());
            ALL.add(t);
            LIVE.put(e.getId(), t);
        }
        t.add(e);
    }
    /** He stopped sliding: the track stops growing (and melts in its own time). */
    static void stop(int id) {
        IcemanTrack t = LIVE.remove(id);
        if (t != null) t.live = false;
    }
    static boolean growing(int id) { return LIVE.containsKey(id); }

    private void add(Entity e) {
        Level level = e.level();
        Vec3 feet = e.position();
        float now = level.getGameTime();
        if (nodes.isEmpty()) {
            float yaw = e.getYRot() * Mth.DEG_TO_RAD;
            lastTan = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            place(level, feet, null, now);
            return;
        }
        Node last = nodes.get(nodes.size() - 1);
        Vec3 from = last.pos;
        double d = span(from, feet).length();
        if (d < SPACING) return;
        int steps = Math.min(8, (int) Math.floor(d / SPACING));
        Vec3 fromFeet = new Vec3(from.x, feetY(last), from.z);
        for (int i = 1; i <= steps; i++) place(level, fromFeet.lerp(feet, i / (double) steps), last, now - (steps - i) * (1f / steps));
        while (nodes.size() > MAX_NODES) nodes.remove(0);
    }
    private static float feetY(Node n) { return (float) (n.height < .3f ? n.pos.y - .06 : n.pos.y + .03); }
    private static Vec3 span(Vec3 a, Vec3 b) { return new Vec3(b.x - a.x, b.y - a.y, b.z - a.z); }

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
        float w = .95f * (.88f + .14f * Mth.sin(dist * .45f + seed) + .08f * Mth.sin(dist * 1.3f + seed * 2));
        float th = height < .3f ? .2f : .26f + .08f * Mth.sin(dist * .7f + seed) + Math.min(.12f, height * .02f);
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
    private static float ground(Level level, Vec3 at) {
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

    /** Every track: the melting (cracks, pieces dropping, frost), the oldest points gone, the finished tracks gone. */
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
                // Fresh ice breathing cold.
                if (age < 10 && IceParticles.rand() < .06f && IceParticles.count(1, n.pos) > 0)
                    IceParticles.mist(n.pos.add(n.side.scale((IceParticles.rand() - .5) * n.width)), new Vec3(0, -.004, 0).add(n.tan.scale(-.01)), .28f, .02f, .16f, 26);
                if (n.broke || age < SLIDE_TRACK_LIFE + SLIDE_TRACK_MELT * .25f) continue;
                n.broke = true;
                if (n.height > 1 && IceMesh.hash(n.seed * .91) < .34 && IceParticles.count(1, n.pos) > 0) {
                    // A piece of the raised stretch dropping away.
                    for (int k = 0; k < 2; k++)
                        IceParticles.shard(n.pos.add(n.up.scale(-n.thick * .5)).add(n.side.scale((IceParticles.rand() - .5) * n.width)),
                                IceParticles.jitter(.03).add(0, -.02, 0), .12f + .12f * IceParticles.rand(), 40 + (int) (IceParticles.rand() * 20), k == 0 ? IceMesh.CLEAR : IceMesh.MILKY);
                    IceParticles.mist(n.pos, new Vec3(0, -.01, 0), .3f, .02f, .2f, 24);
                } else if (IceMesh.hash(n.seed * 1.7) < .2 && IceParticles.count(1, n.pos) > 0) IceParticles.frostDust(n.pos, Vec3.ZERO, .6f);
                if (IceMesh.hash(n.seed * 2.3) < .045) mc.level.playLocalSound(n.pos.x, n.pos.y, n.pos.z, ModSounds.ICEMAN_CRACK.get(), SoundSource.PLAYERS, .25f, 1.2f + IceParticles.rand() * .3f, false);
            }
            if (gone > 0) t.nodes.subList(0, gone).clear();
            if (t.nodes.isEmpty() && !t.live) it.remove();
        }
    }

    // ------------------------------------------------------------------ drawing
    private static final float[] SS8 = new float[8], UU8 = new float[8], SS5 = new float[5], UU5 = new float[5];
    private static final float[] RA8 = new float[24], RB8 = new float[24], RA5 = new float[15], RB5 = new float[15], CA = new float[15], CB = new float[15];

    /** Draws every track; head = per live track, where his feet are now (interpolated) and the ice just ahead of them. */
    static void draw(IceStage st, IceMesh.Ctx c) {
        var mc = Minecraft.getInstance();
        float now = st.time;
        for (IcemanTrack t : ALL) {
            if (t.nodes.isEmpty()) continue;
            List<Node> list = t.nodes;
            Node feetNode = null, leadNode = null;
            if (t.live) {
                Entity e = mc.level.getEntity(t.owner);
                if (e != null) {
                    Node last = list.get(list.size() - 1);
                    Vec3 feet = e.getPosition(st.partial);
                    Vec3 vel = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
                    feetNode = head(last, feet, now - .8f);
                    leadNode = head(last, feet.add(vel.scale(1.3)), now + 1.2f);
                }
            }
            int n = list.size() + (feetNode != null ? 2 : 0);
            Node prev = null;
            for (int i = 0; i < n; i++) {
                Node node = i < list.size() ? list.get(i) : i == list.size() ? feetNode : leadNode;
                if (prev != null) segment(st, c, prev, node, now, i);
                prev = node;
            }
        }
    }
    private static Node head(Node last, Vec3 feet, float born) {
        Vec3 tan = feet.subtract(new Vec3(last.pos.x, feetY(last), last.pos.z));
        tan = tan.lengthSqr() < 1e-6 ? last.tan : tan.normalize();
        Vec3 pos = last.height < .3f ? new Vec3(feet.x, Math.max(feet.y, last.pos.y - .06) + .06, feet.z) : feet.add(0, -.03, 0);
        return new Node(pos, tan, last.side, last.up, born, last.width, last.thick, last.height, last.seed + 3, last.light);
    }
    /** How much of a point's ice there is (growing in, melting away), and how opaque. */
    private static float size(Node n, float now) {
        float age = now - n.born;
        float grow = PantherMotion.snap(age + 1.2f, 0, 4.5f);
        float m = (age - SLIDE_TRACK_LIFE) / SLIDE_TRACK_MELT;
        return grow * (1 - PantherMotion.ease((m - .15f) / .85f));
    }
    private static float alpha(Node n, float now) {
        float m = (now - n.born - SLIDE_TRACK_LIFE) / SLIDE_TRACK_MELT;
        return 1 - PantherMotion.ease((m - .45f) / .55f);
    }
    private static void segment(IceStage st, IceMesh.Ctx c, Node a, Node b, float now, int index) {
        double d2 = a.pos.distanceToSqr(st.cam);
        if (d2 > 110 * 110) return;
        float sa = size(a, now), sb = size(b, now);
        if (sa <= .002f && sb <= .002f) return;
        float al = Math.min(alpha(a, now), alpha(b, now));
        if (al <= .01f) return;
        boolean far = st.far(a.pos);
        c.light = a.light;
        float m = (now - a.born - SLIDE_TRACK_LIFE) / SLIDE_TRACK_MELT;
        c.flash = m > 0 && m < .4f ? .12f * Mth.sin(m / .4f * Mth.PI) : 0;
        if (far) {
            ring5(a, sa, 1, 0, RA5); ring5(b, sb, 1, 0, RB5);
            IceMesh.loft(c, RA5, RB5, IceMesh.CLEAR, al, false, false);
        } else {
            ring8(a, sa, RA8); ring8(b, sb, RB8);
            IceMesh.loft(c, RA8, RB8, IceMesh.CLEAR, al, false, false);
            // The milky body inside it.
            ring5(a, sa, .5f, -.45f, CA); ring5(b, sb, .5f, -.45f, CB);
            IceMesh.loft(c, CA, CB, IceMesh.MILKY, al * .9f, false, false);
            details(c, a, sa, al, now, index);
            // The cracks once it starts to melt.
            if (m > 0 && m < .6f && a.seed % 3 == 0) {
                float k = Mth.sin(Mth.clamp(m / .6f, 0, 1) * Mth.PI) * al;
                Vec3 p0 = a.pos.add(a.side.scale((IceMesh.hash(a.seed) - .5) * a.width * .7 * sa)).add(a.up.scale(.012));
                Vec3 p1 = b.pos.add(b.side.scale((IceMesh.hash(a.seed + 1) - .5) * b.width * .7 * sb)).add(b.up.scale(.012));
                IceMesh.line(c, p0, p1, .018f, .6f * k, .85f * k, k);
            }
        }
        c.flash = 0;
    }
    /** Crystals out of the lips, icicles under the raised stretches, a glint along the lip. */
    private static void details(IceMesh.Ctx c, Node n, float s, float al, float now, int index) {
        if (s < .2f) return;
        double h = IceMesh.hash(n.seed * .73), h2 = IceMesh.hash(n.seed * 1.37);
        if (h < .32) {
            float sg = h2 < .5 ? 1 : -1;
            Vec3 base = n.pos.add(n.side.scale(sg * n.width * .48 * s)).add(n.up.scale(n.lip * s));
            Vec3 dir = n.side.scale(sg * .8).add(n.up.scale(.65)).add(n.tan.scale(-.3)).normalize();
            IceMesh.crystal(c, base, dir, (.16f + .26f * (float) h2) * s, .045f * s, 5, n.seed, h2 < .3 ? IceMesh.MILKY : IceMesh.CLEAR, al, .4f);
        }
        float age = now - n.born;
        if (n.height > 1.2f && h2 < .38) {
            float g = PantherMotion.k(age, 4, 34) * s;
            Vec3 base = n.pos.add(n.up.scale(-n.thick * .9 * s)).add(n.side.scale((h - .5) * n.width * .5));
            Vec3 down = new Vec3((h - .5) * .15, -1, (h2 - .5) * .15).normalize();
            IceMesh.crystal(c, base, down, (.25f + .75f * (float) IceMesh.hash(n.seed * 2.9)) * g, .05f * Math.min(1, g * 2), 4, n.seed + 9, IceMesh.CLEAR, al, .3f);
        }
        if (index % 2 == 0) {
            float k = .25f * al * s * (.6f + .4f * Mth.sin(now * .3f + index));
            Vec3 l0 = n.pos.add(n.side.scale(n.width * .5 * s)).add(n.up.scale(n.lip * s));
            IceMesh.line(c, l0, l0.add(n.tan.scale(SPACING)), .012f, .6f * k, .85f * k, k);
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
