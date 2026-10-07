package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.FIRNI.superheromod.client.render.iceman.IceGrowth.grow;
import static com.FIRNI.superheromod.client.render.iceman.IceGrowth.h;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The ice sculpted into the air by the brush (FX_SCULPT_POINT / END / BREAK), on every client: the AIR ITSELF FREEZING
 * along the mouse's path into one irregular mass of grown crystal (the Days of Future Past reference), never a row of
 * blocks.
 * <p>
 * Where it stands: one NODE where the server lays each of its solid boxes (a cube 2 x radius on a side, one every
 * radius x 1.1 along the path; the tip's own when it is let go), so what you bump into is what you see. Each node grows
 * its own ice out of a cold cloud:
 * <ol>
 * <li>cold air: the moment a point arrives, the cryogenic plume bursts there, fed from his hand's direction
 * (IceParticles.cryo): vapour, ice crystals spiralling, fragments;</li>
 * <li>frost (0 .. .15 of the node's formation): a frosted ghost of the mass appears in the cloud;</li>
 * <li>nuclei (.05 .. .2): three small milky crystals start in it, glinting;</li>
 * <li>growth (.15 .. .3): a cluster of crystals pushes out of the path (up, sideways, now and then down like icicles),
 * plus single crystals along and out of the path, each its own delay, size and lean: some tall, some stay small;</li>
 * <li>interlock (.3 .. .45): a long uneven prism of ice grows along the path through the node, overlapping its
 * neighbours' so the whole path is one body;</li>
 * <li>body (.45 .. .7): a second lump from the other way and a deeper keel under it thicken the mass, the frost
 * clears into blue ice;</li>
 * <li>settle: cold mist sinks off it, a hiss as it settles.</li>
 * </ol>
 * A node takes FORM ticks (0.7 s); nodes are laid one after another, so the ice grows along the path. Sounds: crystal
 * ticks every few points, a deep growing rumble once on a long sculpture, frost hiss as the mist settles.
 * <p>
 * Breaking (FX_SCULPT_BREAK): a small crack sounds and glowing cracks start at a few places, branch and spread over the
 * whole mass while it brightens (a deeper crack halfway); after SCULPT_CRACK ticks (when the server drops its solids)
 * it comes apart ZONE BY ZONE in a random order over a few ticks: each zone vanishes into big falling chunks, shards,
 * a frost-dust burst and cold mist, the first and some later ones with a heavy fracture; the shards landing are heard,
 * then the frost hisses away. A sculpture whose end never comes (its maker gone) breaks by itself after a while.
 */
final class IcemanBrushSculpt {
    private IcemanBrushSculpt() {}

    private static final int MAX = 24;
    /** How long one node takes to form (ticks). */
    private static final float FORM = 14;
    /** Per node: centre, path direction, two directions across it (v the most upward), the cluster's way out, birth, crack distance. */
    private static final int STRIDE = 17, X = 0, D = 3, UU = 6, V = 9, O = 12, BORN = 15, CRACK = 16;

    private static final class Sculpture {
        final int id, owner;
        final List<Vec3> pts = new ArrayList<>();
        final float[] born = new float[SCULPT_POINTS + 8];
        float radius = .62f, lastPoint, endAt = -1, crackAt = -1, lost = -1;
        int life = -1;
        /** The nodes (rebuilt when a point arrives or it is let go), STRIDE floats each. */
        float[] n = new float[0];
        int m, built = -1; boolean builtEnd;
        /** The break: zones along the path, when each goes (game time, -1 not yet), whether it went. */
        int zones; float[] zoneAt; boolean[] zoneGone; int[] zoneOf = new int[0];
        boolean deeper, hissed, rumbled, rained;
        float lastCryo = -1;
        double loX, loY, loZ, hiX, hiY, hiZ;
        Vec3 mid = Vec3.ZERO;
        int light = IceMesh.FULL;
        Sculpture(int id, int owner) { this.id = id; this.owner = owner; }
    }
    private static final Map<Integer, Sculpture> ALL = new LinkedHashMap<>();

    static boolean any() { return !ALL.isEmpty(); }
    private static float now() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime();
    }
    private static long gameTick() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime(); }
    private static void sound(Vec3 at, SoundEvent ev, float vol, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.x, at.y, at.z, ev, SoundSource.PLAYERS, vol, pitch, false);
    }

    // ------------------------------------------------------------------ from the server
    static void point(int id, int owner, int index, Vec3 at, float radius) {
        Sculpture sc = ALL.get(id);
        if (sc == null) {
            if (ALL.size() >= MAX) {
                // Too many: the oldest still standing breaks.
                for (Sculpture o : ALL.values()) if (o.crackAt < 0) { crack(o); break; }
            }
            sc = new Sculpture(id, owner);
            ALL.put(id, sc);
        }
        if (sc.crackAt >= 0 || index < sc.pts.size() || sc.pts.size() >= sc.born.length) return;
        if (radius > .05f) sc.radius = radius;
        float t = now();
        // Laid one after another, never all at once (a fast flick grows along its length).
        float prev = sc.pts.isEmpty() ? t - 1 : sc.born[sc.pts.size() - 1];
        sc.born[sc.pts.size()] = Math.max(t, prev + .35f);
        if (sc.pts.isEmpty()) { sc.loX = sc.hiX = at.x; sc.loY = sc.hiY = at.y; sc.loZ = sc.hiZ = at.z; }
        sc.loX = Math.min(sc.loX, at.x); sc.loY = Math.min(sc.loY, at.y); sc.loZ = Math.min(sc.loZ, at.z);
        sc.hiX = Math.max(sc.hiX, at.x); sc.hiY = Math.max(sc.hiY, at.y); sc.hiZ = Math.max(sc.hiZ, at.z);
        sc.mid = new Vec3((sc.loX + sc.hiX) * .5, (sc.loY + sc.hiY) * .5, (sc.loZ + sc.hiZ) * .5);
        sc.light = IceStage.light(sc.mid);
        sc.pts.add(at);
        sc.lastPoint = t;
        sc.lost = -1;
        // The cold first: the plume bursting where the ice will grow, fed from his hand (at most one a tick: a fast flick
        // lays several points at once).
        if (t - sc.lastCryo >= .9f) {
            sc.lastCryo = t;
            var level = Minecraft.getInstance().level;
            Entity who = level == null ? null : level.getEntity(owner);
            Vec3 hand = IcemanLayer.hand(owner, 0);
            if (hand == null && who != null) hand = who.getEyePosition();
            Vec3 dir = hand == null ? new Vec3(0, 0, 1) : at.subtract(hand);
            dir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
            IceParticles.cryo(at.subtract(dir.scale(sc.radius * .5)), dir, .4f, .6f);
        }
        // Ice forming ticks every few points, a deep growing once it gets long.
        if (index % 4 == 3) sound(at, ModSounds.ICEMAN_CRYSTAL_TICKS.get(), .35f, .9f + .3f * IceParticles.rand());
        if (index >= 18 && !sc.rumbled) { sc.rumbled = true; sound(sc.mid, ModSounds.ICEMAN_GROW_RUMBLE.get(), .5f, .95f + .1f * IceParticles.rand()); }
    }
    static void end(int id, int life) {
        Sculpture sc = ALL.get(id);
        if (sc == null) return;
        sc.life = Math.max(1, life);
        sc.endAt = now();
    }
    static void crack(int id, Vec3 at) {
        Sculpture sc = ALL.get(id);
        if (sc == null) {
            // Never seen growing (out of range then): a plain break where it was.
            if (at != null) IceParticles.shatter(at, Vec3.ZERO, .5f, IceMesh.CLEAR);
            return;
        }
        crack(sc);
    }
    /**
     * The break begins: a small crack heard, crack origins picked (a few nodes; every node's distance from the nearest,
     * so the cracks spread from there), the zones laid out along the path.
     */
    private static void crack(Sculpture sc) {
        if (sc.crackAt >= 0) return;
        build(sc);
        sc.crackAt = now();
        int m = sc.m;
        if (sc.pts.size() < 2 || m == 0) sc.crackAt -= SCULPT_CRACK;
        else sound(sc.mid, ModSounds.ICEMAN_CRACK.get(), .45f, 1.5f + .2f * IceParticles.rand());
        int origins = Math.max(1, Math.min(4, m / 8 + 1));
        float maxD = 1;
        for (int k = 0; k < m; k++) {
            float best = 1e9f;
            for (int q = 0; q < origins; q++) {
                int o = Math.min(m - 1, (int) ((q + .2f + .6f * h(sc.id, 900 + q)) / origins * m));
                best = Math.min(best, Math.abs(k - o));
            }
            sc.n[k * STRIDE + CRACK] = best;
            maxD = Math.max(maxD, best);
        }
        for (int k = 0; k < m; k++) sc.n[k * STRIDE + CRACK] /= maxD;
        sc.zones = Mth.clamp(m / 4, 1, 8);
        sc.zoneAt = new float[sc.zones];
        sc.zoneGone = new boolean[sc.zones];
        java.util.Arrays.fill(sc.zoneAt, -1);
        if (sc.zoneOf.length < m) sc.zoneOf = new int[m];
        for (int k = 0; k < m; k++) sc.zoneOf[k] = Math.min(sc.zones - 1, k * sc.zones / Math.max(1, m));
    }

    // ------------------------------------------------------------------ living
    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || ALL.isEmpty()) return;
        float t = gameTick();
        for (Iterator<Sculpture> it = ALL.values().iterator(); it.hasNext(); ) {
            Sculpture sc = it.next();
            if (sc.pts.isEmpty()) { if (t - sc.lastPoint > 40) it.remove(); continue; }
            if (sc.crackAt >= 0) {
                if (breaking(sc, t)) it.remove();
                continue;
            }
            // Its maker gone or its end never come: it breaks by itself after a while.
            var owner = mc.level.getEntity(sc.owner);
            IcemanClient.State s = IcemanClient.get(sc.owner);
            boolean making = owner != null && s != null && s.action == BRUSH && s.sculpture == sc.id;
            if (sc.endAt < 0 && !making) {
                if (sc.lost < 0) sc.lost = t;
                if (t - sc.lost > 60) { sc.life = 16 * 20; sc.endAt = t; }
            } else if (making) sc.lost = -1;
            if (sc.endAt >= 0 && t - sc.endAt > sc.life + 40 || owner == null && sc.endAt >= 0 && t - sc.lastPoint > 60 * 20) {
                crack(sc);
                continue;
            }
            build(sc);
            // The mist settling off the fresh ice (sinking: cold air), frost dust off it now and then, the hiss once.
            for (int k = Math.max(0, sc.m - 14); k < sc.m; k++) {
                float a = (t - sc.n[k * STRIDE + BORN]) / FORM;
                if (a < .45f || a > 1.3f || IceParticles.rand() > .14f) continue;
                Vec3 at = node(sc, k).add(IceParticles.jitter(sc.radius * .5));
                if (IceParticles.count(1, at) == 0) continue;
                IceParticles.mist(at, new Vec3(0, -.007, 0).add(IceParticles.jitter(.004)), .25f + sc.radius * .35f, .022f, .16f, 30);
            }
            if (IceParticles.rand() < .12f && sc.m > 0) {
                int k = Math.min(sc.m - 1, (int) (IceParticles.rand() * sc.m));
                if ((t - sc.n[k * STRIDE + BORN]) / FORM > 1) IceParticles.frostDust(node(sc, k).add(0, -sc.radius * .7, 0), Vec3.ZERO, .4f);
            }
            if (!sc.hissed && sc.pts.size() >= 3 && t - sc.lastPoint > FORM + 4) {
                sc.hissed = true;
                sound(sc.mid, ModSounds.ICEMAN_FROST_HISS.get(), .4f, .95f + .1f * IceParticles.rand());
            }
        }
    }
    /** One tick of the break; true when it is all gone. */
    private static boolean breaking(Sculpture sc, float t) {
        float a = t - sc.crackAt;
        if (sc.m == 0 || sc.zones == 0) return a >= SCULPT_CRACK;
        // The deeper crack halfway.
        if (!sc.deeper && a >= SCULPT_CRACK * .55f) { sc.deeper = true; sound(sc.mid, ModSounds.ICEMAN_CRACK.get(), .85f, .85f + .1f * IceParticles.rand()); }
        if (a < SCULPT_CRACK) return false;
        // The zones, one after another in a random order (the first right away, the rest over a few ticks).
        if (sc.zoneAt[0] < 0) {
            int z = sc.zones, spread = 3 + Math.round(z * 1.2f);
            int[] order = new int[z];
            for (int i = 0; i < z; i++) order[i] = i;
            for (int i = z - 1; i > 0; i--) { int j = (int) (IceParticles.rand() * (i + 1)) % (i + 1), tmp = order[i]; order[i] = order[j]; order[j] = tmp; }
            for (int k = 0; k < z; k++) {
                float when = k == 0 ? 0 : 1 + (spread - 1) * (k / (float) Math.max(1, z - 1)) * (.7f + .3f * IceParticles.rand());
                sc.zoneAt[order[k]] = sc.crackAt + SCULPT_CRACK + when;
            }
        }
        boolean all = true;
        float last = 0;
        for (int z = 0; z < sc.zones; z++) {
            last = Math.max(last, sc.zoneAt[z]);
            if (sc.zoneGone[z]) continue;
            if (t >= sc.zoneAt[z]) { sc.zoneGone[z] = true; breakZone(sc, z); }
            else all = false;
        }
        if (all && !sc.rained) {
            sc.rained = true;
            Vec3 mid = sc.mid;
            IceParticles.later(5, () -> sound(mid, ModSounds.ICEMAN_SHARD_RAIN.get(), .75f, 1));
            IceParticles.later(12, () -> sound(mid, ModSounds.ICEMAN_FROST_HISS.get(), .45f, 1));
        }
        return all && t > last + 1;
    }
    /** A zone comes away: its big chunks fall, shards and frost dust burst, cold mist rolls off it. */
    private static void breakZone(Sculpture sc, int z) {
        float r = sc.radius;
        double cx = 0, cy = 0, cz = 0;
        int count = 0;
        for (int k = 0; k < sc.m; k++) {
            if (sc.zoneOf[k] != z) continue;
            int b = k * STRIDE;
            cx += sc.n[b]; cy += sc.n[b + 1]; cz += sc.n[b + 2]; count++;
            // Big chunks off every node (as many as the effects allow), falling.
            int chunks = IceParticles.count(2, node(sc, k));
            for (int q = 0; q < chunks; q++) {
                Vec3 at = node(sc, k).add(IceParticles.jitter(r * .35));
                Vec3 v = IceParticles.jitter(.05).add(0, .02 + .05 * IceParticles.rand(), 0);
                IceParticles.shard(at, v, r * (.55f + .4f * IceParticles.rand()), 45 + (int) (IceParticles.rand() * 35),
                        q == 0 ? IceMesh.CLEAR : IceParticles.rand() < .5f ? IceMesh.GLACIER : IceMesh.MILKY);
            }
        }
        if (count == 0) return;
        Vec3 at = new Vec3(cx / count, cy / count, cz / count);
        float size = r * (.8f + .35f * (float) Math.sqrt(count));
        IceParticles.shatter(at, IceParticles.jitter(.03).add(0, -.02, 0), size, z % 2 == 0 ? IceMesh.CLEAR : IceMesh.MILKY);
        IceParticles.coldMist(at.add(0, -r, 0), .8f + size, .6f);
        if (z == 0 || IceParticles.rand() < .4f) sound(at, ModSounds.ICEMAN_SHATTER.get(), .85f, .85f + .3f * IceParticles.rand());
    }
    private static Vec3 node(Sculpture sc, int k) { int b = k * STRIDE; return new Vec3(sc.n[b], sc.n[b + 1], sc.n[b + 2]); }

    // ------------------------------------------------------------------ the nodes
    /**
     * Lays the nodes along the points the way the server lays its solid boxes: one on the first point, then one on every
     * point at least radius x 1.1 from the last node, and (once let go) one on the tip if it is radius x .5 from the
     * last. Each gets the way the path runs there (from the point before: it never changes once laid), two ways across
     * it (v the most upward) and the way its crystal cluster pushes out (mostly up or sideways, now and then down).
     */
    private static void build(Sculpture sc) {
        int np = sc.pts.size();
        boolean ended = sc.endAt >= 0;
        if (sc.built == np && sc.builtEnd == ended || sc.crackAt >= 0 && sc.built >= 0) return;
        sc.built = np; sc.builtEnd = ended;
        float r = sc.radius;
        if (sc.n.length < (np + 1) * STRIDE) sc.n = java.util.Arrays.copyOf(sc.n, (np + 8) * STRIDE);
        int m = 0;
        Vec3 last = null;
        for (int i = 0; i < np; i++) {
            Vec3 p = sc.pts.get(i);
            boolean normal = last == null || last.distanceTo(p) >= r * 1.1;
            boolean tip = !normal && ended && i == np - 1 && np >= 2 && last.distanceTo(p) > r * .5;
            if (!normal && !tip) continue;
            float born = tip ? Math.max(sc.born[i], sc.endAt) : sc.born[i];
            Vec3 d = i > 0 ? p.subtract(sc.pts.get(i - 1)) : np > 1 ? sc.pts.get(1).subtract(p) : new Vec3(0, 0, 1);
            d = d.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : d.normalize();
            // Across: v = the world's up with the path's part taken out (or any side when the path runs up or down).
            Vec3 v = new Vec3(0, 1, 0).subtract(d.scale(d.y));
            if (v.lengthSqr() < .04) v = new Vec3(1, 0, 0).subtract(d.scale(d.x));
            v = v.normalize();
            Vec3 u = d.cross(v);
            int sd = sc.id * 977 + m * 131;
            float a = h(sd, 1) < .18f ? Mth.PI + (h(sd, 2) - .5f) * 1.2f : (h(sd, 2) - .5f) * 2.3f;
            Vec3 o = v.scale(Mth.cos(a)).add(u.scale(Mth.sin(a)));
            int b = m * STRIDE;
            float[] n = sc.n;
            n[b] = (float) p.x; n[b + 1] = (float) p.y; n[b + 2] = (float) p.z;
            n[b + D] = (float) d.x; n[b + D + 1] = (float) d.y; n[b + D + 2] = (float) d.z;
            n[b + UU] = (float) u.x; n[b + UU + 1] = (float) u.y; n[b + UU + 2] = (float) u.z;
            n[b + V] = (float) v.x; n[b + V + 1] = (float) v.y; n[b + V + 2] = (float) v.z;
            n[b + O] = (float) o.x; n[b + O + 1] = (float) o.y; n[b + O + 2] = (float) o.z;
            n[b + BORN] = born; n[b + CRACK] = 0;
            m++;
            last = p;
        }
        sc.m = m;
    }
    /** The newest point of a sculpture (where the brush's flow goes while it is being made), or null. */
    static Vec3 tip(int id) {
        Sculpture sc = ALL.get(id);
        return sc == null || sc.pts.isEmpty() ? null : sc.pts.get(sc.pts.size() - 1);
    }

    // ------------------------------------------------------------------ drawing
    static void drawIce(IceStage st, IceMesh.Ctx c) {
        float now = st.time;
        // Standing still: the ice keeps the world's grid for its texture.
        c.ox = c.oy = c.oz = 0;
        for (Sculpture sc : ALL.values()) {
            if (sc.pts.isEmpty()) continue;
            build(sc);
            if (st.distance(sc.mid) > 160) continue;
            boolean far = st.far(sc.mid);
            float crack = sc.crackAt < 0 ? 0 : Mth.clamp((now - sc.crackAt) / SCULPT_CRACK, 0, 1);
            c.light = sc.light;
            c.flash = .3f * crack * crack;
            for (int k = 0; k < sc.m; k++) {
                if (sc.crackAt >= 0 && sc.zoneGone != null && sc.zoneGone[sc.zoneOf[k]]) continue;
                node(c, sc, k, (now - sc.n[k * STRIDE + BORN]) / FORM, far);
            }
            c.flash = 0;
            if (crack > 0) for (int k = 0; k < sc.m; k++) {
                if (sc.zoneGone != null && sc.zoneGone[sc.zoneOf[k]]) continue;
                cracks(c, sc, k, crack, far);
            }
        }
    }
    /** One node of ice at its formation's clock T (0 .. 1 formed): frost, nuclei, crystals, the interlocking body. */
    private static void node(IceMesh.Ctx c, Sculpture sc, int k, float T, boolean far) {
        if (T <= 0) return;
        float[] n = sc.n;
        int b = k * STRIDE, sd = sc.id * 977 + k * 131;
        float r = sc.radius;
        double x = n[b], y = n[b + 1], z = n[b + 2];
        float dx = n[b + D], dy = n[b + D + 1], dz = n[b + D + 2];
        float ux = n[b + UU], uy = n[b + UU + 1], uz = n[b + UU + 2];
        float vx = n[b + V], vy = n[b + V + 1], vz = n[b + V + 2];
        float ox = n[b + O], oy = n[b + O + 1], oz = n[b + O + 2];
        // Frost: a frosted ghost of the mass in the cold cloud, fading as the clear body grows through it.
        float rime = grow(T, 0, .15f) * (1 - grow(T, .5f, .35f));
        if (rime > .02f && !far)
            IceGrowth.crystal(c, x - dx * r * .95, y - dy * r * .95, z - dz * r * .95, dx, dy, dz, r * 1.9f, r * .8f, sd + 10, IceMesh.FROST, .4f + .5f * grow(T, 0, .3f), .5f * rime);
        // Nuclei: three small milky crystals, the first ice in the cloud (later swallowed by the body).
        if (T < .75f && !far) for (int q = 0; q < 3; q++) {
            float a = h(sd, 20 + q) * Mth.TWO_PI, rr = r * .35f * h(sd, 23 + q);
            double bx = x + (ux * Mth.cos(a) + vx * Mth.sin(a)) * rr, by = y + (uy * Mth.cos(a) + vy * Mth.sin(a)) * rr, bz = z + (uz * Mth.cos(a) + vz * Mth.sin(a)) * rr;
            float e1 = h(sd, 26 + q) - .5f, e2 = h(sd, 29 + q) - .5f, e3 = h(sd, 32 + q) - .5f;
            IceGrowth.crystal(c, bx, by, bz, e1 + ox * .5f, e2 + oy * .5f, e3 + oz * .5f, r * (.32f + .2f * h(sd, 35 + q)), r * (.09f + .04f * h(sd, 38 + q)),
                    sd + 40 + q, IceMesh.MILKY, grow(T, .05f + .04f * q, .12f), 1);
        }
        // The cluster pushing out of the path (on most nodes), its crystals each their own size, lean and time.
        if (h(sd, 3) < .8f) {
            float t = (T - .12f) / .55f;
            int count = far ? 3 : 3 + (int) (h(sd, 4) * 2.99f);
            IceGrowth.cluster(c, x + ox * r * .3, y + oy * r * .3, z + oz * r * .3, ox, oy, oz, r * (.5f + .55f * h(sd, 5)), sd + 50, count,
                    h(sd, 6) < .15f ? IceMesh.GLACIER : IceMesh.CLEAR, t, 1);
        }
        // Single crystals along and out of the path, some tall, some small.
        if (!far) for (int q = 0; q < 2; q++) {
            if (h(sd, 60 + q) > .6f) continue;
            float phi = h(sd, 62 + q) * Mth.TWO_PI, th = (h(sd, 64 + q) - .5f) * 2.1f;
            float sx = ux * Mth.cos(phi) + vx * Mth.sin(phi), sy = uy * Mth.cos(phi) + vy * Mth.sin(phi), sz = uz * Mth.cos(phi) + vz * Mth.sin(phi);
            float cs = Mth.cos(th), sn = Mth.sin(th);
            IceGrowth.crystal(c, x + sx * r * .4, y + sy * r * .4, z + sz * r * .4, sx * cs + dx * sn, sy * cs + dy * sn, sz * cs + dz * sn,
                    r * (.5f + 1f * h(sd, 66 + q) * h(sd, 66 + q)), r * (.12f + .09f * h(sd, 68 + q)), sd + 70 + q,
                    h(sd, 72 + q) < .3f ? IceMesh.GLACIER : IceMesh.CLEAR, grow(T, .15f + .2f * h(sd, 74 + q), .22f), 1);
        }
        // The body: a long uneven prism through the node along the path (overlapping its neighbours': one mass) ...
        float j1 = (h(sd, 80) - .5f) * .3f, j2 = (h(sd, 81) - .5f) * .3f;
        IceGrowth.crystal(c, x - dx * r * .95 + (ux * j1 + vx * j2) * r * .3, y - dy * r * .95 + (uy * j1 + vy * j2) * r * .3, z - dz * r * .95 + (uz * j1 + vz * j2) * r * .3,
                dx + ux * j1 + vx * j2, dy + uy * j1 + vy * j2, dz + uz * j1 + vz * j2, r * (1.8f + .25f * h(sd, 82)), r * (.66f + .12f * h(sd, 83)), sd + 10,
                IceMesh.CLEAR, grow(T, .28f, .2f), 1);
        // ... a second lump grown from the other way, and on some a deeper keel under it.
        float j3 = (h(sd, 84) - .5f) * .35f, j4 = (h(sd, 85) - .5f) * .35f;
        IceGrowth.crystal(c, x + dx * r * .85 + (ux * j3 - vx * .1f) * r * .3, y + dy * r * .85 + (uy * j3 - vy * .1f) * r * .3, z + dz * r * .85 + (uz * j3 - vz * .1f) * r * .3,
                -dx + ux * j3 + vx * j4, -dy + uy * j3 + vy * j4, -dz + uz * j3 + vz * j4, r * (1.5f + .3f * h(sd, 86)), r * (.55f + .12f * h(sd, 87)), sd + 11,
                h(sd, 88) < .4f ? IceMesh.GLACIER : IceMesh.CLEAR, grow(T, .42f, .24f), 1);
        if (h(sd, 89) < .55f && !far)
            IceGrowth.crystal(c, x - vx * r * .3 - dx * r * .6, y - vy * r * .3 - dy * r * .6, z - vz * r * .3 - dz * r * .6,
                    dx - vx * .3f, dy - vy * .3f, dz - vz * .3f, r * 1.3f, r * .5f, sd + 12, IceMesh.GLACIER, grow(T, .5f, .2f), 1);
        // Glints over the nuclei while they start.
        if (T > .04f && T < .45f && !far) {
            float tw = .5f + .5f * Mth.sin(c.time * 1.4f + k * 2.3f);
            IceMesh.sparkle(c, new Vec3(x + ox * r * .45, y + oy * r * .45, z + oz * r * .45), .1f + r * .14f, .75f * tw * (1 - T / .45f));
        }
    }
    /**
     * The cracks on one node: from where they started they spread node by node (crack 0..1 over the crack time), each a
     * zigzag running over the body's surface, branching once it has opened, wider and brighter as it deepens.
     */
    private static void cracks(IceMesh.Ctx c, Sculpture sc, int k, float crack, boolean far) {
        float[] n = sc.n;
        int b = k * STRIDE;
        float open = Mth.clamp((crack * 1.4f - n[b + CRACK] * .9f) / .35f, 0, 1);
        if (open <= 0) return;
        float r = sc.radius, w = .012f + .02f * crack, bright = .45f + .55f * crack;
        for (int f = 0; f < (far ? 1 : 2); f++) {
            int seed = sc.id * 131 + k * 17 + f * 7;
            float th = h(seed, 1) * Mth.TWO_PI, turn = (h(seed, 2) - .5f) * 2f, s0 = -.7f + .2f * h(seed, 3);
            Vec3 a = onBody(n, b, r, th, s0);
            for (int q = 1; q <= 4; q++) {
                float u = Math.min(open * 4 - (q - 1), 1);
                if (u <= 0) break;
                float wob = (h(seed, 10 + q) - .5f) * .8f;
                Vec3 e = onBody(n, b, r, th + turn * q * .25f + wob, s0 + .38f * q);
                e = a.lerp(e, u);
                IceMesh.vein(c, a, e, w, bright);
                // A branch off the second step once it is open.
                if (q == 2 && u >= 1 && open > .55f && h(seed, 20) < .7f) {
                    float bu = Mth.clamp((open - .55f) / .45f, 0, 1);
                    Vec3 be = onBody(n, b, r, th + turn * .5f + (h(seed, 21) < .5f ? .9f : -.9f), s0 + .76f + .15f);
                    IceMesh.vein(c, e, e.lerp(be, bu), w * .7f, bright * .8f);
                }
                a = e;
            }
        }
    }
    /** A point on the body's surface round node b: angle th round the path, s along it (-1..1 of the radius). */
    private static Vec3 onBody(float[] n, int b, float r, float th, float s) {
        float cs = Mth.cos(th) * r * .8f, sn = Mth.sin(th) * r * .8f;
        return new Vec3(n[b] + n[b + D] * s * r + n[b + UU] * cs + n[b + V] * sn,
                n[b + 1] + n[b + D + 1] * s * r + n[b + UU + 1] * cs + n[b + V + 1] * sn,
                n[b + 2] + n[b + D + 2] * s * r + n[b + UU + 2] * cs + n[b + V + 2] * sn);
    }
    /** The cold breath round the nodes still forming (thick at first, thinning as it settles), a glow while it cracks. */
    static void drawFx(IceStage st, FilmContext f) {
        float now = st.time;
        for (Sculpture sc : ALL.values()) {
            if (st.distance(sc.mid) > 160) continue;
            for (int k = 0; k < sc.m; k++) {
                float T = (now - sc.n[k * STRIDE + BORN]) / FORM;
                if (T <= 0 || T > 1.2f) continue;
                if (sc.crackAt >= 0 && sc.zoneGone != null && sc.zoneGone[sc.zoneOf[k]]) continue;
                float a = PantherMotion.k(T, 0, .1f) * (1 - PantherMotion.k(T, .45f, 1.2f));
                int b = k * STRIDE;
                FilmFx.puff(f, new Vec3(sc.n[b], sc.n[b + 1] - sc.radius * .15f * T, sc.n[b + 2]), sc.radius * (1.4f + .8f * T), IceParticles.MIST_RGB, .14f * a);
            }
            if (sc.crackAt >= 0) {
                float crack = Mth.clamp((now - sc.crackAt) / SCULPT_CRACK, 0, 1);
                FilmFx.glow(f, sc.mid, sc.radius * 3 + crack, IceParticles.COLD_LIGHT, .2f * crack * crack);
            }
        }
    }

    static void clear() { ALL.clear(); }
}
