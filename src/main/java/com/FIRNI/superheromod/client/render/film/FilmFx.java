package com.FIRNI.superheromod.client.render.film;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Light and matter for film stages in any colour: soft additive glows, beams and streaks, dust,
 * shock rings, contact shadows and plain solid blocks. Everything is drawn in the stage pose of
 * the FilmContext, and every call fetches its buffer fresh.
 */
public final class FilmFx extends RenderType {
    /** Light: adds to what is behind it. */
    public static final RenderType ADD = create("film_add", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, true,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false));
    /** Matter in the air (dust, smoke, shadow): covers what is behind it. */
    public static final RenderType SOFT = create("film_soft", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, true,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false));
    /** Opaque, flat-shaded geometry. */
    public static final RenderType SOLID = create("film_solid", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL).createCompositeState(false));

    private FilmFx() { super("unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {}); }

    private static void put(VertexConsumer v, Matrix4f m, Vec3 p, int rgb, float a) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, Math.max(0, Math.min(1, a))).endVertex();
    }

    /** Round, camera-facing light with a soft falloff. */
    public static void glow(FilmContext c, Vec3 at, double size, int rgb, float alpha) {
        disc(c, ADD, at, size, rgb, alpha);
    }
    /** Round, camera-facing puff of dust or smoke. */
    public static void puff(FilmContext c, Vec3 at, double size, int rgb, float alpha) {
        disc(c, SOFT, at, size, rgb, alpha);
    }
    private static void disc(FilmContext c, RenderType type, Vec3 at, double size, int rgb, float alpha) {
        if (alpha <= .003f || size <= 0) return;
        VertexConsumer v = c.buffers().getBuffer(type);
        Matrix4f m = c.pose().last().pose();
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 d0 = c.viewRight().scale(Math.cos(a0)).add(c.viewUp().scale(Math.sin(a0)));
            Vec3 d1 = c.viewRight().scale(Math.cos(a1)).add(c.viewUp().scale(Math.sin(a1)));
            // Inner core to a mid ring, then the mid ring out to nothing: a bell-shaped profile.
            put(v, m, at, rgb, alpha); put(v, m, at, rgb, alpha);
            put(v, m, at.add(d1.scale(size * .35)), rgb, alpha * .42f); put(v, m, at.add(d0.scale(size * .35)), rgb, alpha * .42f);
            put(v, m, at.add(d0.scale(size * .35)), rgb, alpha * .42f); put(v, m, at.add(d1.scale(size * .35)), rgb, alpha * .42f);
            put(v, m, at.add(d1.scale(size)), rgb, 0); put(v, m, at.add(d0.scale(size)), rgb, 0);
        }
    }
    /** Camera-facing ribbon from tail to head, bright along its centre line, fading to its edges. */
    public static void streak(FilmContext c, Vec3 tail, Vec3 head, double width, int rgb, float tailAlpha, float headAlpha, boolean light) {
        Vec3 side = head.subtract(tail).cross(c.camera().subtract(head));
        if (side.lengthSqr() < 1e-12 || Math.max(tailAlpha, headAlpha) <= .003f) return;
        side = side.normalize().scale(width);
        VertexConsumer v = c.buffers().getBuffer(light ? ADD : SOFT);
        Matrix4f m = c.pose().last().pose();
        for (int s = -1; s <= 1; s += 2) {
            Vec3 edge = side.scale(s);
            put(v, m, tail, rgb, tailAlpha); put(v, m, head, rgb, headAlpha);
            put(v, m, head.add(edge), rgb, 0); put(v, m, tail.add(edge), rgb, 0);
        }
    }
    /**
     * Energy beam: nested ribbons from a wide dark-red sheath to a white core, with pulses
     * running down it. palette = {outer, mid, inner, core} colours.
     */
    public static void beam(FilmContext c, Vec3 from, Vec3 to, double radius, float alpha, float time, int[] palette) {
        double length = from.distanceTo(to);
        if (length < 1e-4 || alpha <= .003f) return;
        int pieces = Math.max(2, Math.min(40, (int) (length * 3)));
        double[] widths = {1, .62, .32, .1};
        float[] alphas = {.35f, .6f, .85f, 1};
        for (int layer = 0; layer < 4; layer++) {
            for (int i = 0; i < pieces; i++) {
                double u0 = (double) i / pieces, u1 = (double) (i + 1) / pieces;
                double pulse0 = 1 + .14 * Math.sin(u0 * length * 4 - time * 2.6), pulse1 = 1 + .14 * Math.sin(u1 * length * 4 - time * 2.6);
                // The beam leaves the source narrow and opens out over the first stretch.
                double reachOpen = 1.2 + radius, open0 = .3 + .7 * Math.min(1, u0 * length / reachOpen), open1 = .3 + .7 * Math.min(1, u1 * length / reachOpen);
                Vec3 a = from.lerp(to, u0), b = from.lerp(to, u1);
                Vec3 side = b.subtract(a).cross(c.camera().subtract(a));
                if (side.lengthSqr() < 1e-12) continue;
                side = side.normalize();
                VertexConsumer v = c.buffers().getBuffer(ADD);
                Matrix4f m = c.pose().last().pose();
                double w0 = radius * widths[layer] * pulse0 * open0, w1 = radius * widths[layer] * pulse1 * open1;
                float al = alpha * alphas[layer];
                for (int s = -1; s <= 1; s += 2) {
                    put(v, m, a, palette[layer], al); put(v, m, b, palette[layer], al);
                    put(v, m, b.add(side.scale(s * w1)), palette[layer], 0); put(v, m, a.add(side.scale(s * w0)), palette[layer], 0);
                }
            }
        }
    }
    /** Flat ring on the ground plane (y of centre): a shock front, bright in its middle. */
    public static void ring(FilmContext c, Vec3 centre, double radius, double width, int rgb, float alpha, boolean light) {
        if (alpha <= .003f || radius <= 0) return;
        VertexConsumer v = c.buffers().getBuffer(light ? ADD : SOFT);
        Matrix4f m = c.pose().last().pose();
        int n = 40;
        double in = Math.max(0, radius - width), out = radius + width;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 d0 = new Vec3(Math.cos(a0), 0, Math.sin(a0)), d1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
            put(v, m, centre.add(d0.scale(in)), rgb, 0); put(v, m, centre.add(d1.scale(in)), rgb, 0);
            put(v, m, centre.add(d1.scale(radius)), rgb, alpha); put(v, m, centre.add(d0.scale(radius)), rgb, alpha);
            put(v, m, centre.add(d0.scale(radius)), rgb, alpha); put(v, m, centre.add(d1.scale(radius)), rgb, alpha);
            put(v, m, centre.add(d1.scale(out)), rgb, 0); put(v, m, centre.add(d0.scale(out)), rgb, 0);
        }
    }
    /** Soft dark patch on the ground under a performer. */
    public static void shadow(FilmContext c, Vec3 feet, double radius, float alpha) {
        VertexConsumer v = c.buffers().getBuffer(SOFT);
        Matrix4f m = c.pose().last().pose();
        int n = 16;
        Vec3 centre = feet.add(0, .015, 0);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            put(v, m, centre, 0, alpha); put(v, m, centre, 0, alpha);
            put(v, m, centre.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius), 0, 0);
            put(v, m, centre.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), 0, 0);
        }
    }
    /** Solid box, faces shaded by direction (the top brightest). */
    public static void cube(FilmContext c, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        VertexConsumer v = c.buffers().getBuffer(SOLID);
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}};
        float[] shade = {.85f, .6f, .72f, .72f, 1f, .45f};
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        for (int f = 0; f < 6; f++) for (int k = 0; k < 4; k++)
            v.vertex(m, faces[f][k * 3], faces[f][k * 3 + 1], faces[f][k * 3 + 2]).color(r * shade[f], g * shade[f], b * shade[f], 1).endVertex();
    }
    public static double hash(double n) { double x = Math.sin(n * 12.9898) * 43758.5453; return x - Math.floor(x); }
    public static float ease(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    /** 0 before in, 1 between, 0 after out, with soft edges of the given length. */
    public static float window(float t, float in, float out, float soft) { return Math.min(ease((t - in) / soft), ease((out - t) / soft)); }
}
