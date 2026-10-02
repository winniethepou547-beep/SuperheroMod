package com.FIRNI.superheromod.client.render.zed;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The classic sword swoosh: when a blade cuts, the air it passes through is drawn as a thin sheet swept
 * between the blade's root and its tip, brightest along the edge the tip traced and fading to nothing
 * toward the root and with age. Made from where the blades really were over the last couple of ticks,
 * and only where they moved fast (a cut, not a walk), so it shows the exact arc and dies at once.
 */
public final class ZedBlades {
    private record Sample(float time, Vec3 tipRight, Vec3 tipLeft, Vec3 baseRight, Vec3 baseLeft) {}
    private static final Map<Integer, ArrayDeque<Sample>> BLADES = new HashMap<>();
    private static final float WINDOW = 2.4f;
    private static final int AIR = 0xe6ecff, EDGE = 0xffffff, TINT = 0xff4a5a;

    private ZedBlades() {}

    public static void record(int key, Vec3 tipRight, Vec3 tipLeft, Vec3 baseRight, Vec3 baseLeft, float time) {
        if (tipRight == null || tipLeft == null || baseRight == null || baseLeft == null) return;
        ArrayDeque<Sample> q = BLADES.computeIfAbsent(key, k -> new ArrayDeque<>());
        Sample last = q.peekLast();
        if (last != null && time - last.time < .02f) q.pollLast();
        if (last != null && last.tipRight.distanceTo(tipRight) > 5) q.clear();
        q.addLast(new Sample(time, tipRight, tipLeft, baseRight, baseLeft));
        while (q.size() > 40 || !q.isEmpty() && time - q.peekFirst().time > WINDOW) q.pollFirst();
    }

    private static void put(VertexConsumer v, Matrix4f m, Vec3 p, int rgb, float a) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, Math.max(0, Math.min(1, a))).endVertex();
    }

    public static void render(FilmContext c, float time) {
        for (Iterator<ArrayDeque<Sample>> it = BLADES.values().iterator(); it.hasNext(); ) {
            ArrayDeque<Sample> q = it.next();
            while (!q.isEmpty() && time - q.peekFirst().time > WINDOW) q.pollFirst();
            if (q.isEmpty()) { it.remove(); continue; }
            Sample prev = null;
            for (Sample s : q) {
                if (prev != null) {
                    float dt = Math.max(.05f, s.time - prev.time);
                    float age0 = (time - prev.time) / WINDOW, age1 = (time - s.time) / WINDOW;
                    float fade0 = (1 - age0) * (1 - age0), fade1 = (1 - age1) * (1 - age1);
                    for (int side = 0; side < 2; side++) {
                        Vec3 tipA = side == 0 ? prev.tipRight : prev.tipLeft, tipB = side == 0 ? s.tipRight : s.tipLeft;
                        Vec3 baseA = side == 0 ? prev.baseRight : prev.baseLeft, baseB = side == 0 ? s.baseRight : s.baseLeft;
                        float speed = (float) tipA.distanceTo(tipB) / dt;
                        float fast = Math.max(0, Math.min(1, (speed - .35f) / .5f));
                        if (fast <= .01f) continue;
                        float a0 = .42f * fast * fade0, a1 = .42f * fast * fade1;
                        // The swept sheet: nothing at the root, thickening to the edge the tip traced.
                        Vec3 midA = baseA.lerp(tipA, .55), midB = baseB.lerp(tipB, .55);
                        VertexConsumer v = c.buffers().getBuffer(FilmFx.ADD);
                        Matrix4f m = c.pose().last().pose();
                        put(v, m, baseA, AIR, 0); put(v, m, baseB, AIR, 0); put(v, m, midB, AIR, a1 * .3f); put(v, m, midA, AIR, a0 * .3f);
                        put(v, m, midA, AIR, a0 * .3f); put(v, m, midB, AIR, a1 * .3f); put(v, m, tipB, AIR, a1); put(v, m, tipA, AIR, a0);
                        // A crisp edge along the tip's path, with a faint warm tint behind it.
                        FilmFx.streak(c, tipA, tipB, .022, EDGE, a0 * 1.4f, a1 * 1.4f, true);
                        FilmFx.streak(c, tipA, tipB, .07, TINT, a0 * .35f, a1 * .35f, true);
                    }
                }
                prev = s;
            }
        }
    }
    public static void clear() { BLADES.clear(); }
}
