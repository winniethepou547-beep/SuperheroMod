package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Night light for THE FINAL PURSUIT's geometry, worked out per vertex: the dark blue of the night and the city's
 * glow from above, then the street lamps and neon signs near it (light falling on each face, and a gloss
 * highlight on paint, glass and wet things, so the city's lights slide along the car as it passes them), all of
 * it sinking into the purple haze with distance. Faces are drawn into the film's flat-colour buffers.
 */
final class PursuitShade {
    private PursuitShade() {}

    /** A light in the city: where, its colour (0..1, may exceed 1 for strong ones) and how far it reaches. */
    record Light(float x, float y, float z, float r, float g, float b, float reach) {}

    static final float FOG_R = .12f, FOG_G = .07f, FOG_B = .12f;
    /** The camera for this frame (highlights and haze depend on it), and the lights near what is being shaded. */
    private static float cx, cy, cz;
    private static final List<Light> LIGHTS = new ArrayList<>();
    /** How much the haze closes in (the dust after the crash thickens it). */
    static float haze = 1;
    /** A flash over everything (the release, the crash): added light of this colour. */
    static float flashR, flashG, flashB;
    static void camera(double x, double y, double z) { cx = (float) x; cy = (float) y; cz = (float) z; }
    /** Use the lights near (x, z): the nearest of the city's, the effects' own. */
    static void lightsNear(double x, double y, double z, float radius) {
        LIGHTS.clear();
        PursuitCity.lights(LIGHTS, x, z, radius);
        LIGHTS.addAll(EXTRA);
    }
    /** Lights the effects add for a moment (muzzle flashes, the release, fire). */
    static final List<Light> EXTRA = new ArrayList<>();
    /** Only the night and the haze (large flat things the lamps' pools are drawn over). */
    static void noLights() { LIGHTS.clear(); }
    /** The colour the lights near (x, y, z) cast together (for the suit's sheen and the effects' tint). */
    static float[] ambient(double x, double y, double z) {
        float r = 0, g = 0, b = 0;
        for (Light l : LIGHTS) {
            double dx = l.x() - x, dy = l.y() - y, dz = l.z() - z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > l.reach()) continue;
            float k = (float) (1 - d / l.reach());
            k *= k;
            r += l.r() * k; g += l.g() * k; b += l.b() * k;
        }
        return new float[]{r + flashR, g + flashG, b + flashB};
    }

    /** The colour (packed 0xRRGGBB) of a face at p with normal n, base colour, gloss 0..1 (paint .6, glass .9, matt 0). */
    static int shade(float px, float py, float pz, float nx, float ny, float nz, int base, float gloss) {
        float br = (base >> 16 & 255) / 255f, bg = (base >> 8 & 255) / 255f, bb = (base & 255) / 255f;
        // The night: a dark blue all round, the city's glow from above.
        float up = Math.max(0, ny);
        float r = br * (.16f + .10f * up) + .010f, g = bg * (.17f + .09f * up) + .010f, b = bb * (.24f + .14f * up) + .018f;
        float vx = cx - px, vy = cy - py, vz = cz - pz;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-4f;
        vx /= vl; vy /= vl; vz /= vl;
        for (Light l : LIGHTS) {
            float lx = l.x() - px, ly = l.y() - py, lz = l.z() - pz;
            float d2 = lx * lx + ly * ly + lz * lz;
            if (d2 > l.reach() * l.reach()) continue;
            float d = (float) Math.sqrt(d2) + 1e-4f;
            lx /= d; ly /= d; lz /= d;
            float fall = 1 - d / l.reach();
            fall *= fall;
            float lambert = Math.max(0, nx * lx + ny * ly + nz * lz);
            float k = lambert * fall;
            r += br * l.r() * k * 1.4f; g += bg * l.g() * k * 1.4f; b += bb * l.b() * k * 1.4f;
            if (gloss > 0) {
                // The light seen mirrored in the surface: a tight highlight that slides as the viewer or the light moves.
                float hx = lx + vx, hy = ly + vy, hz = lz + vz;
                float hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz) + 1e-4f;
                float spec = Math.max(0, (nx * hx + ny * hy + nz * hz) / hl);
                spec = spec * spec; spec = spec * spec; spec = spec * spec; spec = spec * spec;
                float s = gloss * spec * (.35f + .65f * fall) * 1.6f;
                r += l.r() * s; g += l.g() * s; b += l.b() * s;
            }
        }
        r += flashR * br + flashR * .15f; g += flashG * bg + flashG * .15f; b += flashB * bb + flashB * .15f;
        // The haze swallows it with distance.
        float dist = vl;
        float f = 1 - (float) Math.exp(-dist / (150f / haze));
        r += (FOG_R - r) * f; g += (FOG_G - g) * f; b += (FOG_B - b) * f;
        return pack(r, g, b);
    }
    static int pack(float r, float g, float b) {
        // A soft shoulder instead of a hard clip, so the brightest highlights stay coloured.
        r = r / (1 + r * .35f) * 1.35f; g = g / (1 + g * .35f) * 1.35f; b = b / (1 + b * .35f) * 1.35f;
        return (int) (Math.min(1, r) * 255) << 16 | (int) (Math.min(1, g) * 255) << 8 | (int) (Math.min(1, b) * 255);
    }
    /** How much the haze covers a point at this distance (for lights and glows, which do not go through shade()). */
    static float fog(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return 1 - (float) Math.exp(-Math.sqrt(dx * dx + dy * dy + dz * dz) / (150f / haze));
    }

    // ------------------------------------------------------------------ faces
    private static final Vector3f A = new Vector3f(), B = new Vector3f(), C = new Vector3f(), D = new Vector3f();
    /** A lit quad from four stage-space corners (counter-clockwise seen from its front). */
    static void quad(VertexConsumer v, Matrix4f view, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int base, float gloss) {
        float ux = c.x - a.x, uy = c.y - a.y, uz = c.z - a.z, wx = d.x - b.x, wy = d.y - b.y, wz = d.z - b.z;
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-9f) return;
        nx /= nl; ny /= nl; nz /= nl;
        // Seen from behind: light it as its back (thin panels are drawn once, lit from whichever side we see).
        float mx = (a.x + c.x) * .5f, my = (a.y + c.y) * .5f, mz = (a.z + c.z) * .5f;
        if ((cx - mx) * nx + (cy - my) * ny + (cz - mz) * nz < 0) { nx = -nx; ny = -ny; nz = -nz; }
        vert(v, view, a, shade(a.x, a.y, a.z, nx, ny, nz, base, gloss));
        vert(v, view, b, shade(b.x, b.y, b.z, nx, ny, nz, base, gloss));
        vert(v, view, c, shade(c.x, c.y, c.z, nx, ny, nz, base, gloss));
        vert(v, view, d, shade(d.x, d.y, d.z, nx, ny, nz, base, gloss));
    }
    /** A lit quad given in a model's space (corners transformed by model into stage space). */
    static void quad(VertexConsumer v, Matrix4f view, Matrix4f model, float ax, float ay, float az, float bx, float by, float bz,
                     float cx2, float cy2, float cz2, float dx, float dy, float dz, int base, float gloss) {
        model.transformPosition(ax, ay, az, A); model.transformPosition(bx, by, bz, B);
        model.transformPosition(cx2, cy2, cz2, C); model.transformPosition(dx, dy, dz, D);
        quad(v, view, A, B, C, D, base, gloss);
    }
    /** A lit box (model space, corner and size), every face. */
    static void box(VertexConsumer v, Matrix4f view, Matrix4f model, float x0, float y0, float z0, float x1, float y1, float z1, int base, float gloss) {
        quad(v, view, model, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, base, gloss);
        quad(v, view, model, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, base, gloss);
        quad(v, view, model, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, base, gloss);
        quad(v, view, model, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, base, gloss);
        quad(v, view, model, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, base, gloss);
        quad(v, view, model, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, base, gloss);
    }
    private static void vert(VertexConsumer v, Matrix4f view, Vector3f p, int rgb) {
        v.vertex(view, p.x, p.y, p.z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1f).endVertex();
    }

    // ------------------------------------------------------------------ light and glass
    /** An unlit quad of light (additive) or matter (translucent), one colour, alpha per corner. */
    static void flat(VertexConsumer v, Matrix4f view, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int rgb, float aa, float ab, float ac, float ad) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f;
        v.vertex(view, a.x, a.y, a.z).color(r, g, bl, clamp(aa)).endVertex();
        v.vertex(view, b.x, b.y, b.z).color(r, g, bl, clamp(ab)).endVertex();
        v.vertex(view, c.x, c.y, c.z).color(r, g, bl, clamp(ac)).endVertex();
        v.vertex(view, d.x, d.y, d.z).color(r, g, bl, clamp(ad)).endVertex();
    }
    static void flat(VertexConsumer v, Matrix4f view, Matrix4f model, float ax, float ay, float az, float bx, float by, float bz,
                     float cx2, float cy2, float cz2, float dx, float dy, float dz, int rgb, float alpha) {
        model.transformPosition(ax, ay, az, A); model.transformPosition(bx, by, bz, B);
        model.transformPosition(cx2, cy2, cz2, C); model.transformPosition(dx, dy, dz, D);
        flat(v, view, A, B, C, D, rgb, alpha, alpha, alpha, alpha);
    }
    private static float clamp(float a) { return Math.max(0, Math.min(1, a)); }

    static VertexConsumer solid(FilmContext c) { return c.buffers().getBuffer(FilmFx.SOLID); }
    static VertexConsumer add(FilmContext c) { return c.buffers().getBuffer(FilmFx.ADD); }
    static VertexConsumer soft(FilmContext c) { return c.buffers().getBuffer(FilmFx.SOFT); }
}
