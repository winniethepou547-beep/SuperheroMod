package com.FIRNI.superheromod.client.render.thor;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Thor's lightning: the shape of a bolt (a jagged main channel that forks, forks again, and
 * now and then rejoins itself) and how it is drawn (a white-hot core inside a pale blue body
 * inside a faint violet-blue glow). A bolt lives for a few ticks; the same seed gives the same
 * shape, and re-seeding every tick makes it crackle.
 */
public final class ThorBolts {
    public static final int CORE = 0xffffff, BODY = 0xa8d4ff, GLOW = 0x5a7cff, HAZE = 0x7a5cff;

    /** One straight piece of a bolt; width is relative (1 = the main channel). */
    public record Seg(Vec3 a, Vec3 b, float width) {}

    private ThorBolts() {}

    /**
     * A bolt from a to b. jag scales the sideways wander (fraction of the length), branches is
     * how readily it forks (0..1), depth how many times forks can fork again.
     */
    public static List<Seg> bolt(Vec3 a, Vec3 b, long seed, double jag, double branches, int depth) {
        List<Seg> out = new ArrayList<>();
        channel(out, new Random(seed), a, b, 1f, jag, branches, depth);
        return out;
    }

    private static void channel(List<Seg> out, Random r, Vec3 a, Vec3 b, float width, double jag, double branches, int depth) {
        Vec3 axis = b.subtract(a);
        double length = axis.length();
        if (length < 1e-4) return;
        int pieces = Math.max(3, Math.min(28, (int) (length * 2.4)));
        Vec3 dir = axis.scale(1 / length);
        Vec3 side1 = perpendicular(dir), side2 = dir.cross(side1);
        List<Vec3> points = new ArrayList<>();
        points.add(a);
        double wander1 = 0, wander2 = 0;
        for (int i = 1; i < pieces; i++) {
            double u = (double) i / pieces;
            // A random walk pulled back toward the line, so the bolt meanders but still arrives.
            wander1 = wander1 * .55 + (r.nextDouble() - .5) * jag * length * .35;
            wander2 = wander2 * .55 + (r.nextDouble() - .5) * jag * length * .35;
            double pin = Math.sin(Math.PI * u);
            points.add(a.add(axis.scale(u)).add(side1.scale(wander1 * pin)).add(side2.scale(wander2 * pin)));
        }
        points.add(b);
        for (int i = 0; i + 1 < points.size(); i++) {
            float taper = width * (float) (1 - .45 * i / points.size());
            out.add(new Seg(points.get(i), points.get(i + 1), taper));
        }
        if (depth <= 0) return;
        for (int i = 1; i < points.size() - 1; i++) {
            if (r.nextDouble() > branches * .3) continue;
            Vec3 from = points.get(i);
            double remaining = length * (1 - (double) i / points.size());
            // Fork: leaves at an angle, shorter and thinner.
            Vec3 bend = dir.add(side1.scale((r.nextDouble() - .5) * 1.6)).add(side2.scale((r.nextDouble() - .5) * 1.6)).normalize();
            double reach = remaining * (.3 + r.nextDouble() * .35) + length * .08;
            channel(out, r, from, from.add(bend.scale(reach)), width * .5f, jag * 1.1, branches * .7, depth - 1);
            // Now and then a fork finds its way back into the main channel further down.
            if (r.nextDouble() < .25 && i + 3 < points.size()) {
                Vec3 rejoin = points.get(Math.min(points.size() - 1, i + 2 + r.nextInt(3)));
                Vec3 mid = from.lerp(rejoin, .5).add(side1.scale((r.nextDouble() - .5) * length * .18)).add(side2.scale((r.nextDouble() - .5) * length * .18));
                channel(out, r, from, mid, width * .4f, jag, 0, 0);
                channel(out, r, mid, rejoin, width * .4f, jag, 0, 0);
            }
        }
    }

    static Vec3 perpendicular(Vec3 d) {
        Vec3 up = Math.abs(d.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return d.cross(up).normalize();
    }

    // ------------------------------------------------------------------ drawing
    /**
     * Draws a bolt as light, facing the camera (camera in the same space as the points).
     * width is the main channel's width in blocks; alpha fades the whole thing.
     */
    public static void draw(VertexConsumer v, Matrix4f m, List<Seg> segs, Vec3 camera, double width, float alpha) {
        if (alpha <= .003f) return;
        for (Seg s : segs) {
            double w = width * s.width();
            ribbon(v, m, s.a(), s.b(), camera, w * 3.2, GLOW, alpha * .22f);
            ribbon(v, m, s.a(), s.b(), camera, w * 1.3, BODY, alpha * .7f);
            ribbon(v, m, s.a(), s.b(), camera, w * .45, CORE, alpha);
        }
    }
    /** A strip from a to b, brightest along its centre line, fading to nothing at its edges. */
    public static void ribbon(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 camera, double width, int rgb, float alpha) {
        Vec3 side = b.subtract(a).cross(camera.subtract(a));
        if (side.lengthSqr() < 1e-12 || alpha <= .003f) return;
        side = side.normalize().scale(width);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 edge = side.scale(s);
            put(v, m, a, rgb, alpha); put(v, m, b, rgb, alpha);
            put(v, m, b.add(edge), rgb, 0); put(v, m, a.add(edge), rgb, 0);
        }
    }
    /**
     * Same strip seen from any side: two crossed ribbons. For geometry drawn in a model's own
     * space where the camera is not at hand (arcs on the hammer, sparks round his eyes).
     */
    public static void cross(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double width, int rgb, float alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-12 || alpha <= .003f) return;
        Vec3 n1 = perpendicular(d.normalize()), n2 = d.normalize().cross(n1);
        for (Vec3 n : new Vec3[]{n1, n2}) for (int s = -1; s <= 1; s += 2) {
            Vec3 edge = n.scale(width * s);
            put(v, m, a, rgb, alpha); put(v, m, b, rgb, alpha);
            put(v, m, b.add(edge), rgb, 0); put(v, m, a.add(edge), rgb, 0);
        }
    }
    public static void crossBolt(VertexConsumer v, Matrix4f m, List<Seg> segs, double width, float alpha) {
        for (Seg s : segs) {
            double w = width * s.width();
            cross(v, m, s.a(), s.b(), w * 2.6, GLOW, alpha * .3f);
            cross(v, m, s.a(), s.b(), w, BODY, alpha * .8f);
            cross(v, m, s.a(), s.b(), w * .35, CORE, alpha);
        }
    }
    static void put(VertexConsumer v, Matrix4f m, Vec3 p, int rgb, float a) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, Math.max(0, Math.min(1, a))).endVertex();
    }
}
