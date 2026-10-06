package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.core.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The ice sculpted into the air by the brush (FX_SCULPT_POINT / END / BREAK), on every client.
 * <p>
 * Minecraft ice, built of blocks: each stretch of the mouse's path gets a cube of ice 2 x radius on a side, laid where
 * the server lays its solid box (one every radius x 1.1 along the path, so what you see is what you bump into), turned
 * a little toward the way the path runs; smaller cubes are frozen in at some of the joints, so it reads as one chunky
 * body of ice blocks. Each point the server lays arrives with its time, a little after the one before it, so the ice
 * grows along the mouse's path: first a bright thin line of cold, then each block forms: a small frosted cube that grows
 * to full size and clears into bright blue ice; a few square icicles grow down from its underside. Humidity is drawn in
 * to where it is forming (mist moving in), sparkles run over the growing blocks. No round tubes, no crystals.
 * <p>
 * Breaking (FX_SCULPT_BREAK): glowing cracks run over the blocks' faces from the middle outward for SCULPT_CRACK ticks
 * while it brightens, then it falls apart along its whole length into chunks of ice (cubes tumbling), a short flash,
 * mist; the small pieces melt into frost (IceParticles). A sculpture whose end never comes (its maker gone) breaks by
 * itself after a while.
 */
final class IcemanBrushSculpt {
    private IcemanBrushSculpt() {}

    private static final int MAX = 24;

    private static final class Sculpture {
        final int id, owner;
        final List<Vec3> pts = new ArrayList<>();
        final float[] born = new float[SCULPT_POINTS + 8];
        float radius = .62f, lastPoint, endAt = -1, crackAt = -1, lost = -1;
        int life = -1;
        /** The blocks (rebuilt when a point arrives or it is let go): centre, birth, half-size, frame, joint or not. */
        final List<Vec3> blocks = new ArrayList<>();
        float[] bBorn = new float[0], bHalf = new float[0], bFrame = new float[0];
        boolean[] bJoint = new boolean[0];
        int built = -1; boolean builtEnd;
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
    private static void crack(Sculpture sc) {
        if (sc.crackAt >= 0) return;
        sc.crackAt = now();
        if (sc.pts.size() < 2) sc.crackAt -= SCULPT_CRACK;
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
                if (t - sc.crackAt >= SCULPT_CRACK) { shatter(sc); it.remove(); }
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
                mc.level.playLocalSound(sc.mid.x, sc.mid.y, sc.mid.z, ModSounds.ICEMAN_CRACK.get(), SoundSource.PLAYERS, .8f, .9f, false);
                continue;
            }
            // Humidity drawn in to where it is still forming, frost dust off the fresh ice now and then.
            int n = sc.pts.size();
            for (int i = Math.max(0, n - 12); i < n; i++) {
                float a = t - sc.born[i];
                if (a < 0 || a > SCULPT_VOLUME) continue;
                if (IceParticles.rand() > .22f || IceParticles.count(1, sc.pts.get(i)) == 0) continue;
                Vec3 out = IceParticles.jitter(1).normalize().scale(sc.radius * 2.2);
                IceParticles.mist(sc.pts.get(i).add(out), out.scale(-.06), .25f + sc.radius * .3f, -.004f, .18f, 14);
            }
            if (IceParticles.rand() < .15f) {
                int i = Math.min(n - 1, (int) (IceParticles.rand() * n));
                if (t - sc.born[i] > SCULPT_THICK) IceParticles.frostDust(sc.pts.get(i).add(0, -sc.radius, 0), Vec3.ZERO, .4f);
            }
        }
    }
    /** Falls apart along its whole length: chunks of ice off every block, the flash, the small pieces, mist. */
    private static void shatter(Sculpture sc) {
        int n = sc.pts.size();
        if (n == 0) return;
        build(sc);
        float r = sc.radius;
        int breaks = Math.min(5, 1 + n / 10);
        for (int k = 0; k < breaks; k++) {
            Vec3 at = sc.pts.get(Math.min(n - 1, (int) ((k + .5f) / breaks * n)));
            IceParticles.shatter(at, IceParticles.jitter(.04), r * 1.1f, k % 2 == 0 ? IceMesh.CLEAR : IceMesh.MILKY);
        }
        // Every block comes apart into a few big cubes of ice (as many as the effects allow).
        int m = sc.blocks.size();
        int chunks = IceParticles.count(Math.min(30, m * 2), sc.mid);
        for (int k = 0; k < chunks && m > 0; k++) {
            int b = Math.min(m - 1, (int) ((k + IceParticles.rand()) / Math.max(1, chunks) * m));
            Vec3 at = sc.blocks.get(b).add(IceParticles.jitter(sc.bHalf[b] * .35));
            Vec3 v = IceParticles.jitter(.05).add(0, .04 + .05 * IceParticles.rand(), 0);
            IceParticles.shard(at, v, sc.bHalf[b] * (.9f + .5f * IceParticles.rand()), 50 + (int) (IceParticles.rand() * 35),
                    k % 3 == 0 ? IceMesh.MILKY : IceMesh.CLEAR);
        }
        for (int k = 0; k < Math.min(8, n / 4 + 1); k++)
            IceParticles.mist(sc.pts.get((int) (IceParticles.rand() * n)), new Vec3(0, -.004, 0), .4f + r * .4f, .02f, .25f, 30);
    }

    // ------------------------------------------------------------------ the blocks
    /**
     * Lays the blocks along the points the way the server lays its solid boxes: one on the first point, then one on
     * every point at least radius x 1.1 from the last block, and (once let go) one on the tip if it is radius x .5 from
     * the last. Each turned a little toward the way the path runs (a fraction of its yaw, half its pitch, a hint of roll);
     * a smaller cube, turned its own way, frozen into some of the joints.
     */
    private static void build(Sculpture sc) {
        int n = sc.pts.size();
        boolean ended = sc.endAt >= 0;
        if (sc.built == n && sc.builtEnd == ended) return;
        sc.built = n; sc.builtEnd = ended;
        sc.blocks.clear();
        float r = sc.radius;
        int cap = n + n + 2;
        if (sc.bBorn.length < cap) { sc.bBorn = new float[cap]; sc.bHalf = new float[cap]; sc.bFrame = new float[cap * 9]; sc.bJoint = new boolean[cap]; }
        int lastBlock = -1;
        float lastBorn = 0;
        Vec3 last = null;
        for (int i = 0; i < n; i++) {
            Vec3 p = sc.pts.get(i);
            boolean normal = last == null || last.distanceTo(p) >= r * 1.1;
            boolean tip = !normal && ended && i == n - 1 && n >= 2 && last.distanceTo(p) > r * .5;
            if (!normal && !tip) continue;
            // Laid when its point arrived; the tip's own block (added when it is let go) grows in from then.
            float born = tip ? Math.max(sc.born[i], sc.endAt) : sc.born[i];
            // A joint block between this one and the last (now and then).
            if (lastBlock >= 0 && IceMesh.hash(sc.id * 3.7 + i * 1.3) < .55) {
                double h = IceMesh.hash(sc.id * 5.1 + i * 2.9);
                int k = sc.blocks.size();
                sc.blocks.add(last.lerp(p, .5).add((h - .5) * r * .3, (IceMesh.hash(sc.id + i * 4.3) - .5) * r * .3, 0));
                sc.bBorn[k] = Math.max(lastBorn, born) + 1;
                sc.bHalf[k] = r * (.55f + .15f * (float) h);
                sc.bJoint[k] = true;
                IceParticles.turn((float) (h * 6.3), (float) (IceMesh.hash(sc.id + i * 6.1) - .5) * .9f, (float) (IceMesh.hash(sc.id + i * 7.7) - .5) * .9f, IceParticles.AX);
                System.arraycopy(IceParticles.AX, 0, sc.bFrame, k * 9, 9);
            }
            int k = sc.blocks.size();
            sc.blocks.add(p);
            sc.bBorn[k] = born;
            sc.bHalf[k] = r;
            sc.bJoint[k] = false;
            // The way the path runs here (from the point before: it never changes once laid).
            Vec3 d = i > 0 ? p.subtract(sc.pts.get(i - 1)) : n > 1 ? sc.pts.get(1).subtract(p) : new Vec3(0, 0, 1);
            if (d.lengthSqr() < 1e-8) d = new Vec3(0, 0, 1); else d = d.normalize();
            float yaw = IceParticles.yawOf(d.x, d.z);
            // Only part of the way round (a cube looks the same every quarter turn): it leans toward the path, no more.
            yaw -= Math.round(yaw / Mth.HALF_PI) * Mth.HALF_PI;
            float pitch = Mth.clamp(IceParticles.pitchOf(d.y) * .5f, -.35f, .35f);
            float roll = (float) (IceMesh.hash(sc.id * 9.3 + i * 1.7) - .5) * .2f;
            IceParticles.turn(yaw * .6f, pitch, roll, IceParticles.AX);
            System.arraycopy(IceParticles.AX, 0, sc.bFrame, k * 9, 9);
            last = p; lastBlock = i; lastBorn = born;
        }
    }
    /** The newest point of a sculpture (where the brush's flow goes while it is being made), or null. */
    static Vec3 tip(int id) {
        Sculpture sc = ALL.get(id);
        return sc == null || sc.pts.isEmpty() ? null : sc.pts.get(sc.pts.size() - 1);
    }
    private static final float[] FR = new float[9];

    // ------------------------------------------------------------------ drawing
    static void drawIce(IceStage st, IceMesh.Ctx c) {
        float now = st.time;
        // Standing still: the blocks keep the world's block grid for their texture.
        c.ox = c.oy = c.oz = 0;
        for (Sculpture sc : ALL.values()) {
            int n = sc.pts.size();
            if (n == 0) continue;
            build(sc);
            boolean far = st.far(sc.mid);
            float crack = sc.crackAt < 0 ? 0 : Mth.clamp((now - sc.crackAt) / SCULPT_CRACK, 0, 1);
            float r0 = sc.radius;
            c.light = sc.light;
            c.flash = .35f * crack * crack;
            // The line of cold first, ahead of the blocks.
            for (int j = 0; j + 1 < n; j++) {
                float a = now - sc.born[j + 1];
                float k = PantherMotion.k(a, 0, .8f) * (1 - PantherMotion.k(a, SCULPT_LINE, SCULPT_THICK + 1));
                if (k <= .01f) continue;
                IceMesh.line(c, sc.pts.get(j), sc.pts.get(j + 1), .03f, .72f * k, .88f * k, k);
            }
            int m = sc.blocks.size();
            for (int b = 0; b < m; b++) {
                float a = now - sc.bBorn[b];
                if (a < 0) continue;
                // A small frosted cube growing to full size, clearing into bright ice.
                float g = .15f + .85f * PantherMotion.ease(PantherMotion.k(a, 0, SCULPT_VOLUME));
                float solid = .45f + .55f * PantherMotion.k(a, 0, SCULPT_THICK);
                float rime = 1 - PantherMotion.k(a, SCULPT_LINE, SCULPT_VOLUME + 2);
                float h = sc.bHalf[b] * g;
                Vec3 p = sc.blocks.get(b);
                System.arraycopy(sc.bFrame, b * 9, FR, 0, 9);
                double hm = IceMesh.hash(sc.id * 2.3 + b * 3.1);
                IceMesh.Mat mat = sc.bJoint[b] ? (hm < .5 ? IceMesh.MILKY : IceMesh.CLEAR) : hm < .16 ? IceMesh.MILKY : hm > .9 ? IceMesh.GLACIER : IceMesh.CLEAR;
                IceParticles.obox(c, p.x, p.y, p.z, FR, h, h, h, mat, solid);
                if (rime > .01f && !far) IceParticles.obox(c, p.x, p.y, p.z, FR, h * 1.03f, h * 1.03f, h * 1.03f, IceMesh.FROST, .75f * rime);
                if (far || sc.bJoint[b]) continue;
                // A square icicle hanging from its underside (where it runs about level).
                if (Math.abs(FR[7]) < .5f && IceMesh.hash(sc.id * 4.9 + b * 2.1) < .3) {
                    float gi = PantherMotion.k(a, SCULPT_VOLUME, SCULPT_CRYSTAL + 12);
                    if (gi > .01f) {
                        double ho = IceMesh.hash(sc.id * 6.7 + b * 1.9) - .5, hz = IceMesh.hash(sc.id * 8.3 + b) - .5;
                        IceParticles.spike(c, p.x + ho * h, p.y - h * .96, p.z + hz * h, ho * .1, -1, hz * .1, r0 * (.6f + 1.1f * (float) IceMesh.hash(sc.id + b * 5.3)) * gi,
                                r0 * .16f * Math.min(1, gi * 2), (float) hm * 3, 2, IceMesh.CLEAR, 1);
                    }
                }
                // Sparkles over the blocks still forming.
                if (a >= SCULPT_LINE && a <= SCULPT_CRYSTAL) {
                    float tw = .5f + .5f * Mth.sin(now * 1.3f + b * 2.7f);
                    float sx = (b & 1) == 0 ? 1 : -1, sy = (b & 2) == 0 ? 1 : -1;
                    Vec3 at = new Vec3(p.x + (FR[0] * sx + FR[3] * sy + FR[6]) * h, p.y + (FR[1] * sx + FR[4] * sy + FR[7]) * h, p.z + (FR[2] * sx + FR[5] * sy + FR[8]) * h);
                    IceMesh.sparkle(c, at, .1f + r0 * .12f, .8f * tw * (1 - a / SCULPT_CRYSTAL));
                }
            }
            // The cracks running over the blocks' faces, from the middle outward.
            if (crack > 0 && m > 0) cracks(c, sc, crack, far);
            c.flash = 0;
        }
    }
    /** Glowing cracks over each block's faces (two faces each), opening from the middle block outward. */
    private static void cracks(IceMesh.Ctx c, Sculpture sc, float crack, boolean far) {
        int m = sc.blocks.size();
        float shown = PantherMotion.ease(Math.min(1, crack * 1.35f));
        float k = .6f + .4f * crack;
        for (int b = 0; b < m; b++) {
            if (sc.bJoint[b]) continue;
            float from = Math.abs(b - (m - 1) * .5f) / Math.max(1, m * .5f);
            float open = Mth.clamp((shown - from * .7f) / .3f, 0, 1);
            if (open <= 0) continue;
            System.arraycopy(sc.bFrame, b * 9, FR, 0, 9);
            Vec3 p = sc.blocks.get(b);
            float h = sc.bHalf[b];
            for (int f = 0; f < (far ? 1 : 2); f++) {
                int seed = sc.id * 131 + b * 17 + f * 7;
                int face = (int) (IceMesh.hash(seed) * 6);
                // A zigzag from inside the face out to one of its edges.
                float s0 = (float) (IceMesh.hash(seed * 1.3) - .5) * .6f, t0 = (float) (IceMesh.hash(seed * 2.1) - .5) * .6f;
                float ds = (float) (IceMesh.hash(seed * 3.7) - .5) * 2, dt = (float) (IceMesh.hash(seed * 4.3) - .5) * 2;
                float dl = Math.max(.3f, Mth.sqrt(ds * ds + dt * dt));
                ds /= dl; dt /= dl;
                Vec3 a = onFace(p, h, face, s0, t0);
                for (int q = 1; q <= 3; q++) {
                    float u = Math.min(open * 3 - (q - 1), 1);
                    if (u <= 0) break;
                    float wob = (float) (IceMesh.hash(seed * 5.9 + q) - .5) * .5f;
                    float s1 = Mth.clamp(s0 + (ds + wob * dt) * .35f * q, -.98f, .98f), t1 = Mth.clamp(t0 + (dt - wob * ds) * .35f * q, -.98f, .98f);
                    Vec3 e = onFace(p, h, face, s1, t1);
                    e = a.lerp(e, u);
                    IceMesh.vein(c, a, e, .02f + .015f * crack, k);
                    a = e;
                }
            }
        }
    }
    /** A point on a face of a block (centre p, half-size h, frame FR): face 0..5 = -u, +u, -v, +v, -w, +w; s, t across it (-1..1). */
    private static Vec3 onFace(Vec3 p, float h, int face, float s, float t) {
        int ax = face >> 1;
        float sg = (face & 1) == 0 ? -1 : 1;
        int a1 = (ax + 1) % 3, a2 = (ax + 2) % 3;
        double x = p.x + (FR[ax * 3] * sg + FR[a1 * 3] * s + FR[a2 * 3] * t) * h * 1.01;
        double y = p.y + (FR[ax * 3 + 1] * sg + FR[a1 * 3 + 1] * s + FR[a2 * 3 + 1] * t) * h * 1.01;
        double z = p.z + (FR[ax * 3 + 2] * sg + FR[a1 * 3 + 2] * s + FR[a2 * 3 + 2] * t) * h * 1.01;
        return new Vec3(x, y, z);
    }
    /** The cold breath round the blocks still forming, a glow while it cracks. */
    static void drawFx(IceStage st, FilmContext f) {
        float now = st.time;
        for (Sculpture sc : ALL.values()) {
            int m = sc.blocks.size();
            for (int b = 0; b < m; b += 2) {
                float a = now - sc.bBorn[b];
                if (a < 0 || a > SCULPT_CRYSTAL) continue;
                float k = PantherMotion.k(a, 0, 2) * (1 - PantherMotion.k(a, SCULPT_VOLUME, SCULPT_CRYSTAL));
                FilmFx.puff(f, sc.blocks.get(b), sc.radius * 1.6f, IceParticles.MIST_RGB, .12f * k);
            }
            if (sc.crackAt >= 0) {
                float crack = Mth.clamp((now - sc.crackAt) / SCULPT_CRACK, 0, 1);
                FilmFx.glow(f, sc.mid, sc.radius * 3 + crack, IceParticles.COLD_LIGHT, .25f * crack * crack);
            }
        }
    }

    static void clear() { ALL.clear(); }
}
