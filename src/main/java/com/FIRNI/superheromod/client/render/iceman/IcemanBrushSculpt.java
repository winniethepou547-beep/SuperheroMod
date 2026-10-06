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
 * Each point the server lays arrives with its time, a little after the one before it, so the ice grows along the mouse's
 * path: first a bright thin line of cold, then a thin tube, then its full uneven faceted volume (a clear shell over a
 * milky core that comes and goes along it, so it is part clear and part milky), then it crystallises: crystals break
 * out of its sides, pinnacles stand up from it, icicles grow down from its underside, the ends sharpen into crystal
 * points. Humidity is drawn in to where it is forming (mist moving in), sparkles run over the growing parts.
 * The path is a Catmull-Rom curve through the points, so a sculpture is one smooth body, never a chain of blocks.
 * <p>
 * Breaking (FX_SCULPT_BREAK): bright crack lines spread over it from the middle for SCULPT_CRACK ticks while it
 * brightens, then it falls apart along its whole length: big pieces, shards, a short flash, mist; the small pieces melt
 * into frost (IceParticles). A sculpture whose end never comes (its maker gone) breaks by itself after a while.
 */
final class IcemanBrushSculpt {
    private IcemanBrushSculpt() {}

    private static final int SUB = 3, MAX = 24;

    private static final class Sculpture {
        final int id, owner;
        final List<Vec3> pts = new ArrayList<>();
        final float[] born = new float[SCULPT_POINTS + 8];
        float radius = .62f, lastPoint, endAt = -1, crackAt = -1, lost = -1;
        int life = -1;
        // the smooth path (rebuilt when a point arrives), its frames, the birth time of each of its points
        final List<Vec3> path = new ArrayList<>();
        Vec3[] fu = new Vec3[0], fv = new Vec3[0];
        float[] pathBorn = new float[0], radii = new float[0], core = new float[0];
        int built = -1;
        Vec3 mid = Vec3.ZERO;
        int light = IceMesh.FULL;
        /** The crack lines once it is breaking: per line, the path indices it runs over and its turn round the tube. */
        int[][] crackIdx; float[][] crackAng;
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
        if (sc.pts.size() < 2) { sc.crackAt -= SCULPT_CRACK; return; }
        build(sc);
        // A few cracks setting off from about the middle, running both ways along it, wandering round the tube.
        int n = sc.path.size(), lines = 3 + Math.min(3, n / 40);
        java.util.Random r = new java.util.Random(sc.id * 7919L);
        sc.crackIdx = new int[lines][];
        sc.crackAng = new float[lines][];
        for (int l = 0; l < lines; l++) {
            int from = Mth.clamp(n / 2 + (int) ((r.nextFloat() - .5f) * n * .5f), 0, n - 1);
            int dir = l % 2 == 0 ? 1 : -1;
            int len = Math.max(2, (int) (n * (.35f + .4f * r.nextFloat())));
            List<Integer> idx = new ArrayList<>();
            for (int i = 0, j = from; i < len && j >= 0 && j < n; i++, j += dir) idx.add(j);
            sc.crackIdx[l] = idx.stream().mapToInt(Integer::intValue).toArray();
            float[] ang = new float[sc.crackIdx[l].length];
            float a = r.nextFloat() * Mth.TWO_PI;
            for (int i = 0; i < ang.length; i++) { ang[i] = a; a += (r.nextFloat() - .5f) * 1.1f; }
            sc.crackAng[l] = ang;
        }
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
                int i = (int) (IceParticles.rand() * n);
                if (t - sc.born[Math.min(i, n - 1)] > SCULPT_THICK) IceParticles.frostDust(sc.pts.get(Math.min(i, n - 1)).add(0, -sc.radius * .6, 0), Vec3.ZERO, .4f);
            }
        }
    }
    /** Falls apart along its whole length: the big pieces, shards, mist, the small pieces melting. */
    private static void shatter(Sculpture sc) {
        int n = sc.pts.size();
        if (n == 0) return;
        float r = sc.radius;
        int breaks = Math.min(5, 1 + n / 10);
        for (int k = 0; k < breaks; k++) {
            Vec3 at = sc.pts.get(Math.min(n - 1, (int) ((k + .5f) / breaks * n)));
            IceParticles.shatter(at, IceParticles.jitter(.04), r * 1.3f, k % 2 == 0 ? IceMesh.CLEAR : IceMesh.MILKY);
        }
        int chunks = IceParticles.count(Math.min(28, (n + 1) / 2), sc.mid);
        for (int k = 0; k < chunks; k++) {
            Vec3 at = sc.pts.get(Math.min(n - 1, (int) ((k + IceParticles.rand()) / Math.max(1, chunks) * n)));
            Vec3 v = IceParticles.jitter(.05).add(0, .04 + .05 * IceParticles.rand(), 0);
            IceParticles.shard(at.add(IceParticles.jitter(r * .3)), v, r * (.55f + .4f * IceParticles.rand()), 50 + (int) (IceParticles.rand() * 35),
                    k % 3 == 0 ? IceMesh.MILKY : IceMesh.CLEAR);
        }
        for (int k = 0; k < Math.min(8, n / 4 + 1); k++)
            IceParticles.mist(sc.pts.get((int) (IceParticles.rand() * n)), new Vec3(0, -.004, 0), .4f + r * .4f, .02f, .25f, 30);
    }

    // ------------------------------------------------------------------ the smooth path
    private static void build(Sculpture sc) {
        int n = sc.pts.size();
        if (sc.built == n) return;
        sc.built = n;
        sc.path.clear();
        int m = n < 2 ? n : (n - 1) * SUB + 1;
        sc.pathBorn = new float[m];
        sc.radii = new float[m];
        sc.core = new float[m];
        for (int i = 0; i < n - 1; i++) {
            Vec3 p0 = sc.pts.get(Math.max(0, i - 1)), p1 = sc.pts.get(i), p2 = sc.pts.get(i + 1), p3 = sc.pts.get(Math.min(n - 1, i + 2));
            for (int s = 0; s < SUB; s++) {
                float u = s / (float) SUB;
                sc.pathBorn[sc.path.size()] = Mth.lerp(u, sc.born[i], sc.born[i + 1]);
                sc.path.add(catmull(p0, p1, p2, p3, u));
            }
        }
        sc.pathBorn[m - 1] = sc.born[n - 1];
        sc.path.add(sc.pts.get(n - 1));
        // Frames carried along the path (the same way IceMesh.tube turns its rings), for the cracks and the crystals.
        sc.fu = new Vec3[m]; sc.fv = new Vec3[m];
        Vec3 u = null;
        for (int i = 0; i < m; i++) {
            Vec3 tan = tangent(sc.path, i);
            if (u == null) u = IceMesh.frame(tan)[0];
            u = u.subtract(tan.scale(u.dot(tan)));
            u = u.lengthSqr() < 1e-8 ? IceMesh.frame(tan)[0] : u.normalize();
            sc.fu[i] = u; sc.fv[i] = tan.cross(u).normalize();
        }
        Vec3 lo = sc.pts.get(0), hi = lo;
        for (Vec3 p : sc.pts) { lo = new Vec3(Math.min(lo.x, p.x), Math.min(lo.y, p.y), Math.min(lo.z, p.z)); hi = new Vec3(Math.max(hi.x, p.x), Math.max(hi.y, p.y), Math.max(hi.z, p.z)); }
        sc.mid = lo.add(hi).scale(.5);
        sc.light = IceStage.light(sc.mid);
    }
    private static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        double a = -.5 * t3 + t2 - .5 * t, b = 1.5 * t3 - 2.5 * t2 + 1, c = -1.5 * t3 + 2 * t2 + .5 * t, d = .5 * t3 - .5 * t2;
        return new Vec3(p0.x * a + p1.x * b + p2.x * c + p3.x * d, p0.y * a + p1.y * b + p2.y * c + p3.y * d, p0.z * a + p1.z * b + p2.z * c + p3.z * d);
    }
    private static Vec3 tangent(List<Vec3> path, int i) {
        int m = path.size();
        Vec3 t = path.get(Math.min(m - 1, i + 1)).subtract(path.get(Math.max(0, i - 1)));
        return t.lengthSqr() < 1e-10 ? IceMesh.Y : t.normalize();
    }
    /** The newest point of a sculpture (where the brush's flow goes while it is being made), or null. */
    static Vec3 tip(int id) {
        Sculpture sc = ALL.get(id);
        return sc == null || sc.pts.isEmpty() ? null : sc.pts.get(sc.pts.size() - 1);
    }

    // ------------------------------------------------------------------ drawing
    static void drawIce(IceStage st, IceMesh.Ctx c) {
        float now = st.time;
        for (Sculpture sc : ALL.values()) {
            if (sc.pts.isEmpty()) continue;
            build(sc);
            int m = sc.path.size();
            if (m == 0) continue;
            // Only the part whose points have arrived (the newest still on their way along the path).
            int count = 0;
            while (count < m && now - sc.pathBorn[count] >= 0) count++;
            if (count == 0) continue;
            boolean far = st.far(sc.mid);
            float crack = sc.crackAt < 0 ? 0 : Mth.clamp((now - sc.crackAt) / SCULPT_CRACK, 0, 1);
            float r0 = sc.radius;
            c.light = sc.light;
            c.flash = .35f * crack * crack;
            // The radius of each point by its age: line, thickening, volume (uneven), the ends tapering to points.
            int tubeTo = 0;
            for (int j = 0; j < count; j++) {
                float a = now - sc.pathBorn[j];
                float grow = .2f * PantherMotion.k(a, SCULPT_LINE, SCULPT_THICK) + .8f * PantherMotion.k(a, SCULPT_THICK, SCULPT_VOLUME);
                float uneven = .82f + .2f * Mth.sin(j * .9f + sc.id * 1.7f) + .12f * Mth.sin(j * 2.3f + sc.id * 3.1f);
                int end = Math.min(j, m - 1 - j);
                float taper = (float) Math.pow(Math.min(1, (end + .6f) / 4f), .7);
                sc.radii[j] = r0 * grow * uneven * taper;
                sc.core[j] = sc.radii[j] * (.4f + .22f * Mth.sin(j * .55f + sc.id) + .1f * Mth.sin(j * 1.7f));
                if (sc.radii[j] > .004f) tubeTo = j + 1;
            }
            // The line of cold first, ahead of the thickening.
            for (int j = 0; j + 1 < count; j++) {
                float a = now - sc.pathBorn[j + 1];
                float k = PantherMotion.k(a, 0, .8f) * (1 - PantherMotion.k(a, SCULPT_LINE, SCULPT_THICK + 1));
                if (k <= .01f) continue;
                IceMesh.line(c, sc.path.get(j), sc.path.get(j + 1), .035f, .5f * k, .8f * k, k);
            }
            if (tubeTo >= 2) {
                if (!far) IceMesh.tube(c, sc.path, sc.core, tubeTo, 6, sc.id * 13 + 5, IceMesh.MILKY, .95f);
                IceMesh.tube(c, sc.path, sc.radii, tubeTo, far ? 5 : 7, sc.id * 13, IceMesh.CLEAR, 1);
            }
            crystals(st, c, sc, now, far);
            // Sparkles over the parts still growing.
            if (!far) for (int j = 0; j < count; j += 2) {
                float a = now - sc.pathBorn[j];
                if (a < SCULPT_LINE || a > SCULPT_CRYSTAL) continue;
                float tw = .5f + .5f * Mth.sin(now * 1.3f + j * 2.7f);
                Vec3 off = sc.fu[j].scale(Mth.cos(j * 2.1f + now * .2f)).add(sc.fv[j].scale(Mth.sin(j * 2.1f + now * .2f))).scale(sc.radii[j] * 1.05);
                IceMesh.sparkle(c, sc.path.get(j).add(off), .1f + r0 * .12f, .8f * tw * (1 - a / SCULPT_CRYSTAL));
            }
            // The cracks spreading over it.
            if (crack > 0 && sc.crackIdx != null) {
                float shown = PantherMotion.ease(Math.min(1, crack * 1.35f));
                for (int l = 0; l < sc.crackIdx.length; l++) {
                    int[] idx = sc.crackIdx[l];
                    int upTo = (int) (idx.length * shown);
                    for (int i = 0; i + 1 < Math.min(upTo, idx.length); i++) {
                        int a = idx[i], b = idx[i + 1];
                        if (a >= count || b >= count) break;
                        Vec3 pa = surface(sc, a, sc.crackAng[l][i]), pb = surface(sc, b, sc.crackAng[l][i + 1]);
                        float k = .6f + .4f * crack;
                        IceMesh.line(c, pa, pb, .022f + .018f * crack, .75f * k, .92f * k, k);
                        // Small branches off the main line.
                        if ((a * 7 + l) % 9 == 0) {
                            Vec3 pc = surface(sc, b, sc.crackAng[l][i + 1] + .7f);
                            IceMesh.line(c, pb, pc, .015f, .5f * k, .7f * k, .8f * k);
                        }
                    }
                }
            }
            c.flash = 0;
        }
    }
    private static Vec3 surface(Sculpture sc, int j, float ang) {
        return sc.path.get(j).add(sc.fu[j].scale(Mth.cos(ang) * sc.radii[j] * 1.04)).add(sc.fv[j].scale(Mth.sin(ang) * sc.radii[j] * 1.04));
    }
    /** Crystals out of its sides, pinnacles up, icicles down, crystal points at the ends: once each part has its volume. */
    private static void crystals(IceStage st, IceMesh.Ctx c, Sculpture sc, float now, boolean far) {
        int n = sc.pts.size(), m = sc.path.size();
        float r = sc.radius;
        for (int i = 0; i < n; i++) {
            float a = now - sc.born[i];
            float g = PantherMotion.k(a, SCULPT_VOLUME - 2, SCULPT_CRYSTAL);
            if (g <= 0) continue;
            int j = Math.min(m - 1, i * SUB);
            Vec3 p = sc.path.get(j), tan = tangent(sc.path, j);
            float rad = sc.radii[j];
            if (rad < .02f) continue;
            double h1 = IceMesh.hash(sc.id * 7.1 + i * 3.3), h2 = IceMesh.hash(sc.id * 1.3 + i * 5.7), h3 = IceMesh.hash(sc.id * 4.9 + i * 2.1);
            int seed = sc.id * 101 + i * 7;
            if (h1 < (far ? .3 : .6)) {
                float ang = (float) (h2 * Mth.TWO_PI);
                Vec3 out = sc.fu[j].scale(Mth.cos(ang)).add(sc.fv[j].scale(Mth.sin(ang))).add(tan.scale((h3 - .5) * .6)).normalize();
                IceMesh.crystal(c, p.add(out.scale(rad * .5)), out, r * (1.0f + 1.6f * (float) h3) * g, r * (.2f + .12f * (float) h2) * Math.min(1, g * 1.5f),
                        far ? 4 : 6, seed, h2 < .4 ? IceMesh.MILKY : IceMesh.CLEAR, 1, far ? 0 : .45f);
            }
            if (far) continue;
            if (h2 < .25) {
                Vec3 up = new Vec3(Math.cos(h3 * 9) * .3, 1, Math.sin(h3 * 9) * .3).normalize();
                IceMesh.crystal(c, p.add(0, rad * .4, 0), up, r * (1.8f + 2.0f * (float) h1) * g, r * .32f * Math.min(1, g * 1.5f), 6, seed + 3, IceMesh.CLEAR, 1, .5f);
            }
            if (Math.abs(tan.y) < .75 && h3 < .5) {
                float gi = PantherMotion.k(a, SCULPT_VOLUME, SCULPT_CRYSTAL + 12);
                Vec3 down = new Vec3((h1 - .5) * .2, -1, (h2 - .5) * .2).normalize();
                IceMesh.crystal(c, p.add(0, -rad * .55, 0), down, r * (1.0f + 2.6f * (float) IceMesh.hash(seed * .37)) * gi, r * .2f * Math.min(1, gi * 2),
                        4, seed + 5, h1 < .5 ? IceMesh.CLEAR : IceMesh.MILKY, 1, .35f);
            }
        }
        // The ends sharpen into crystal points.
        for (int e = 0; e < 2; e++) {
            int i = e == 0 ? 0 : n - 1, j = e == 0 ? 0 : m - 1;
            float g = PantherMotion.k(now - sc.born[i], SCULPT_THICK, SCULPT_CRYSTAL);
            if (g <= 0 || m < 2) continue;
            Vec3 out = tangent(sc.path, j).scale(e == 0 ? -1 : 1);
            int k = e == 0 ? Math.min(m - 1, 3) : Math.max(0, m - 4);
            float rad = Math.max(sc.radii[k], r * .3f * g);
            IceMesh.crystal(c, sc.path.get(j).subtract(out.scale(r * .4)), out, r * 2.4f * g, rad * .75f, 6, sc.id * 11 + e, IceMesh.CLEAR, 1, far ? 0 : .5f);
        }
    }
    /** The cold breath round the parts still forming, a glow while it cracks. */
    static void drawFx(IceStage st, FilmContext f) {
        float now = st.time;
        for (Sculpture sc : ALL.values()) {
            if (sc.path.isEmpty()) continue;
            int m = sc.path.size();
            for (int j = 0; j < m; j += 4) {
                float a = now - sc.pathBorn[j];
                if (a < 0 || a > SCULPT_CRYSTAL) continue;
                float k = PantherMotion.k(a, 0, 2) * (1 - PantherMotion.k(a, SCULPT_VOLUME, SCULPT_CRYSTAL));
                FilmFx.puff(f, sc.path.get(j), sc.radius * 1.6f, IceParticles.MIST_RGB, .12f * k);
            }
            if (sc.crackAt >= 0) {
                float crack = Mth.clamp((now - sc.crackAt) / SCULPT_CRACK, 0, 1);
                FilmFx.glow(f, sc.mid, sc.radius * 3 + crack, IceParticles.COLD_LIGHT, .25f * crack * crack);
            }
        }
    }

    static void clear() { ALL.clear(); }
}
