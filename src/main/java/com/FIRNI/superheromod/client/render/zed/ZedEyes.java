package com.FIRNI.superheromod.client.render.zed;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Zed's eyes as light: a hot core with a red bloom round it, and a thin red streak left behind when his
 * head moves. The streak is made from where the eyes really were over the last few ticks, so its length
 * follows his speed (almost nothing standing, a short streak running, a long one that dies fast in a
 * dash) and a turn draws a curved stroke. Every body that is drawn with eyes reports them here under its
 * own key (a player's id; a film's shadows and soldiers use negative keys).
 */
public final class ZedEyes {
    private record Sample(float time, Vec3 right, Vec3 left, float power) {}
    private static final Map<Integer, ArrayDeque<Sample>> EYES = new HashMap<>();
    /** How long (ticks) a streak lasts. */
    private static final float WINDOW = 4.5f;
    static final int CORE = 0xfff0dc, RED = 0xff2a1c, DEEP = 0xc0100c;

    private ZedEyes() {}

    /** Where a body's eyes were drawn at this moment, and how lit they are. */
    public static void record(int key, Vec3 right, Vec3 left, float time, float power) {
        if (right == null || left == null) return;
        ArrayDeque<Sample> q = EYES.computeIfAbsent(key, k -> new ArrayDeque<>());
        Sample last = q.peekLast();
        if (last != null && time - last.time < .02f) q.pollLast();
        // A body that jumped (a teleport, a cut) starts a fresh streak.
        if (last != null && last.right.distanceTo(right) > 6) q.clear();
        q.addLast(new Sample(time, right, left, power));
        while (q.size() > 60 || !q.isEmpty() && time - q.peekFirst().time > WINDOW) q.pollFirst();
    }
    /** Forget a body's streak (it vanished). */
    public static void forget(int key) { EYES.remove(key); }

    /** The blooms and streaks; also lights the smoke near the eyes. Drawn before the smoke so it can veil them. */
    public static void render(FilmContext c, float time) {
        for (Iterator<Map.Entry<Integer, ArrayDeque<Sample>>> it = EYES.entrySet().iterator(); it.hasNext(); ) {
            ArrayDeque<Sample> q = it.next().getValue();
            while (!q.isEmpty() && time - q.peekFirst().time > WINDOW) q.pollFirst();
            if (q.isEmpty()) { it.remove(); continue; }
            Sample newest = q.peekLast();
            Sample prev = null;
            for (Sample s : q) {
                if (prev != null) {
                    float k0 = (time - prev.time) / WINDOW, k1 = (time - s.time) / WINDOW;
                    float a0 = (float) Math.pow(Math.max(0, 1 - k0), 2.2) * prev.power, a1 = (float) Math.pow(Math.max(0, 1 - k1), 2.2) * s.power;
                    for (int e = 0; e < 2; e++) {
                        Vec3 from = side(prev, e, time), to = side(s, e, time);
                        if (from.distanceToSqr(to) < 1e-5) continue;
                        FilmFx.streak(c, from, to, .055 * (1 - k1 * .5), RED, a0 * .75f, a1 * .75f, true);
                        FilmFx.streak(c, from, to, .018, CORE, a0 * .6f, a1 * .6f, true);
                    }
                }
                prev = s;
            }
            // The bloom, only while the body is still being drawn.
            if (time - newest.time > 1.5f) continue;
            float p = newest.power;
            if (p <= .01f) continue;
            for (int e = 0; e < 2; e++) {
                Vec3 eye = e == 0 ? newest.right : newest.left;
                Vec3 at = eye.add(c.camera().subtract(eye).normalize().scale(.1));
                FilmFx.glow(c, at, .07 * Math.min(1.6, p), CORE, Math.min(1, .95f * p));
                FilmFx.glow(c, at, .2 * Math.min(1.6, p), RED, Math.min(1, .6f * p));
            }
            Vec3 mid = newest.right.add(newest.left).scale(.5);
            FilmFx.glow(c, mid.add(c.camera().subtract(mid).normalize().scale(.1)), .7 * Math.min(1.6, p), DEEP, .12f * Math.min(1.5f, p));
            ShadowSmoke.light(mid, RED, .45f * Math.min(1, p), 1.1f);
        }
    }
    /**
     * Where a past sample of an eye is drawn: right at the eye when new, then swept out to the side of
     * the head, so the streak runs back past his temples instead of through his head and out of the
     * back of it.
     */
    private static Vec3 side(Sample s, int eye, float time) {
        Vec3 across = s.left.subtract(s.right);
        if (across.lengthSqr() < 1e-8) return eye == 0 ? s.right : s.left;
        across = across.normalize();
        float age = Math.max(0, time - s.time);
        float k = Math.min(1, age / .9f);
        double out = .26 * k * k * (3 - 2 * k);
        return eye == 0 ? s.right.subtract(across.scale(out)) : s.left.add(across.scale(out));
    }
    public static void clear() { EYES.clear(); }
}
