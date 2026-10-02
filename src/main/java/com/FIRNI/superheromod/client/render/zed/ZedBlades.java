package com.FIRNI.superheromod.client.render.zed;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The thin trail a blade tip leaves when it cuts: made from where the tips really were over the last
 * couple of ticks, and only where they moved fast (a cut, not a walk), so it shows the exact arc of
 * the cut and dies at once. Dark red with a hot core, never thick.
 */
public final class ZedBlades {
    private record Sample(float time, Vec3 right, Vec3 left) {}
    private static final Map<Integer, ArrayDeque<Sample>> TIPS = new HashMap<>();
    private static final float WINDOW = 2.6f;

    private ZedBlades() {}

    public static void record(int key, Vec3 right, Vec3 left, float time) {
        if (right == null || left == null) return;
        ArrayDeque<Sample> q = TIPS.computeIfAbsent(key, k -> new ArrayDeque<>());
        Sample last = q.peekLast();
        if (last != null && time - last.time < .02f) q.pollLast();
        if (last != null && last.right.distanceTo(right) > 5) q.clear();
        q.addLast(new Sample(time, right, left));
        while (q.size() > 40 || !q.isEmpty() && time - q.peekFirst().time > WINDOW) q.pollFirst();
    }

    public static void render(FilmContext c, float time) {
        for (Iterator<ArrayDeque<Sample>> it = TIPS.values().iterator(); it.hasNext(); ) {
            ArrayDeque<Sample> q = it.next();
            while (!q.isEmpty() && time - q.peekFirst().time > WINDOW) q.pollFirst();
            if (q.isEmpty()) { it.remove(); continue; }
            Sample prev = null;
            for (Sample s : q) {
                if (prev != null) {
                    float dt = Math.max(.05f, s.time - prev.time);
                    float age = (time - s.time) / WINDOW, fade = (1 - age) * (1 - age);
                    for (int side = 0; side < 2; side++) {
                        Vec3 a = side == 0 ? prev.right : prev.left, b = side == 0 ? s.right : s.left;
                        float speed = (float) a.distanceTo(b) / dt;
                        float fast = Math.max(0, Math.min(1, (speed - .35f) / .5f));
                        if (fast <= .01f) continue;
                        FilmFx.streak(c, a, b, .05, ZedFx.RED, .55f * fast * fade, .7f * fast * fade, true);
                        FilmFx.streak(c, a, b, .14, 0x2a0610, .3f * fast * fade, .4f * fast * fade, false);
                        FilmFx.streak(c, a, b, .015, ZedFx.WHITE, .5f * fast * fade, .7f * fast * fade, true);
                    }
                }
                prev = s;
            }
        }
    }
    public static void clear() { TIPS.clear(); }
}
