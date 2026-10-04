package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.client.render.panther.PursuitPath.*;

/**
 * The city of THE FINAL PURSUIT, built the same every time from fixed numbers: a wide boulevard at night, three
 * lanes each way either side of a concrete median, its asphalt wet, pools of lamplight down it and every light
 * smeared long across it toward the eye; kerbs, pavements, trees; tall buildings close on both sides with lit
 * windows and bright shopfronts, crowded with neon: blade signs standing out over the pavement, boards across the
 * shop fronts, their characters in tubes of colour; overhead gantries, cross streets with signals and crossings.
 * Only what is near the camera is drawn; the haze takes the rest, and the backdrop's skyline and hills lie beyond.
 */
final class PursuitCity {
    private PursuitCity() {}

    static final float LAMP_GAP = 24, FACADE = ROAD_HALF + WALK + .25f;
    private static final int[] NEON = {0xff2fa8, 0x33e6ff, 0xff3a3a, 0xb04dff, 0x45ff9e, 0xffb020, 0xfff2e0, 0x4d7dff, 0xff6a2a};
    private static final int ASPHALT = 0x111318, LINE = 0xc9c9c2, YELLOW = 0xd2a52a, CONCRETE = 0x5d5f66, WALKWAY = 0x34363c, KERB = 0x55575d, POLE = 0x2b2d33;

    record Building(int side, float z0, float z1, float face, float depth, float height, int colour, int seed) {}
    record Sign(int side, boolean blade, float x, float y0, float y1, float z0, float z1, int colour, int seed) {
        float cx() { return blade ? x - side * .75f : x; }
        float cy() { return (y0 + y1) * .5f; }
        float cz() { return (z0 + z1) * .5f; }
    }
    static final List<Building> BUILDINGS = new ArrayList<>();
    static final List<Sign> SIGNS = new ArrayList<>();
    static final List<Float> CROSSINGS = new ArrayList<>();
    static final List<Float> GANTRIES = new ArrayList<>();
    private static final float Z_MIN = -260, Z_MAX = 1400;

    static {
        for (float z = 90; z < Z_MAX; z += 118 + 46 * (float) hash(z * .37)) CROSSINGS.add(z);
        for (float z = 140; z < Z_MAX; z += 160 + 60 * (float) hash(z * .91)) {
            boolean clash = false;
            for (float c : CROSSINGS) if (Math.abs(c - z) < 20) clash = true;
            if (!clash) GANTRIES.add(z);
        }
        int seed = 0;
        for (int side = -1; side <= 1; side += 2) {
            float z = Z_MIN;
            while (z < Z_MAX) {
                if (crossingAt(z, 9)) { z += 18; continue; }
                seed++;
                float len = 9 + 17 * (float) hash(seed * 1.31);
                float h = (float) hash(seed * 2.77);
                float height = h < .35f ? 9 + 10 * h : h < .8f ? 18 + 26 * h : 38 + 40 * h;
                float face = FACADE + .3f * (float) hash(seed * 5.1) + (hash(seed * 7.7) < .2 ? 2.5f : 0);
                int colour = mix(0x1a1c25, 0x2c2a33, (float) hash(seed * 3.3));
                if (hash(seed * 9.1) < .25) colour = mix(colour, 0x3a3226, .5f);
                Building b = new Building(side, z, z + len - .6f, face, 9 + 10 * (float) hash(seed * 4.4), height, colour, seed);
                BUILDINGS.add(b);
                float sx = side * face;
                // Neon: a board across the shop front, and often a blade sign standing out over the pavement.
                if (hash(seed * 6.2) < .85) SIGNS.add(new Sign(side, false, sx - side * .08f, 4.3f, 5.6f + (float) hash(seed * 8.4) * .6f, z + 1, z + len - 1.8f,
                        NEON[(int) (hash(seed * 1.9) * NEON.length)], seed * 3));
                if (hash(seed * 4.8) < .7 && height > 12) {
                    float top = Math.min(height - 1, 9 + 9 * (float) hash(seed * 2.2));
                    float at = z + (hash(seed * 3.9) < .5 ? .6f : len - 1.4f);
                    SIGNS.add(new Sign(side, true, sx, 6.2f, 6.2f + top * .7f, at, at + .28f, NEON[(int) (hash(seed * 5.5) * NEON.length)], seed * 3 + 1));
                }
                if (hash(seed * 7.1) < .3 && height > 20) {
                    // A big board high on the building, lit.
                    float y = height * (.55f + .25f * (float) hash(seed * 6.6));
                    SIGNS.add(new Sign(side, false, sx - side * .08f, y, y + 2.4f, z + 1.5f, z + Math.min(len - 1.5f, 10), NEON[(int) (hash(seed * 8.8) * NEON.length)], seed * 3 + 2));
                }
                z += len;
            }
        }
        SIGNS.sort((a, b) -> Float.compare(a.z0(), b.z0()));
        BUILDINGS.sort((a, b) -> Float.compare(a.z0(), b.z0()));
    }
    /** Street furniture standing right where a shot puts the camera is left out (it would fill the frame). */
    private static boolean nearCamera(FilmContext c, float x, float z, float r) {
        double dx = x - c.camera().x, dz = z - c.camera().z;
        return dx * dx + dz * dz < r * r && c.camera().y < 7;
    }
    static boolean crossingAt(float z, float half) {
        for (float c : CROSSINGS) if (Math.abs(c - z) < half) return true;
        return false;
    }
    static int mix(int a, int b, float k) {
        int r = (int) ((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k), g = (int) ((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k), bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return r << 16 | g << 8 | bl;
    }
    /** The lamps: sodium orange in some stretches, cold white in others. */
    static int lampColour(float z) { return hash(Math.floor(z / 190) * 3.7) < .55 ? 0xffb066 : 0xd7e4ff; }

    // ------------------------------------------------------------------ the city's lights (for shading)
    static void lights(List<PursuitShade.Light> out, double x, double z, float radius) {
        // Street lamps both sides and on the median.
        int k0 = (int) Math.floor((z - radius) / LAMP_GAP), k1 = (int) Math.ceil((z + radius) / LAMP_GAP);
        for (int k = k0; k <= k1; k++) {
            float lz = k * LAMP_GAP + 7;
            if (crossingAt(lz, 6)) continue;
            int c = lampColour(lz);
            float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
            for (int side = -1; side <= 1; side += 2) {
                float lx = side * (ROAD_HALF - 1.0f);
                if (Math.abs(lx - x) < radius) out.add(new PursuitShade.Light(lx, 7.3f, lz, r * .9f, g * .9f, b * .9f, 16));
            }
            if ((k & 1) == 0) for (int side = -1; side <= 1; side += 2) out.add(new PursuitShade.Light(side * 1.6f, 8.2f, lz + 12, r * .8f, g * .8f, b * .8f, 15));
        }
        // Neon signs.
        int i = firstSign((float) (z - radius - 12));
        for (; i < SIGNS.size(); i++) {
            Sign s = SIGNS.get(i);
            if (s.z0() > z + radius) break;
            if (Math.abs(s.cx() - x) > radius + 4) continue;
            int c = s.colour();
            float f = flicker(s, 0) * (s.blade() ? .9f : .7f);
            out.add(new PursuitShade.Light(s.cx() - s.side() * (s.blade() ? 0 : .6f), s.cy(), s.cz(), (c >> 16 & 255) / 255f * f, (c >> 8 & 255) / 255f * f, (c & 255) / 255f * f, s.blade() ? 9 : 8));
        }
    }
    private static int firstSign(float z) {
        int lo = 0, hi = SIGNS.size();
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (SIGNS.get(mid).z0() < z) lo = mid + 1; else hi = mid; }
        return Math.max(0, lo - 4);
    }
    private static int firstBuilding(float z) {
        int lo = 0, hi = BUILDINGS.size();
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (BUILDINGS.get(mid).z0() < z - 30) lo = mid + 1; else hi = mid; }
        return lo;
    }
    /** Now and then a tube stutters. */
    static float flicker(Sign s, float time) {
        if (s.seed() % 11 != 0) return 1;
        double t = Math.floor(time * .6 + s.seed());
        return hash(t * 1.3 + s.seed()) < .12 ? .25f : 1;
    }

    // ------------------------------------------------------------------ drawing
    private static final Vector3f A = new Vector3f(), B = new Vector3f(), C = new Vector3f(), D = new Vector3f();
    private static final Matrix4f ID = new Matrix4f();

    /** The city's solid matter around the camera (draw before anything translucent; light() adds its lights after). */
    static void solid(FilmContext c, float tau) {
        Matrix4f m = c.pose().last().pose();
        double cz = c.camera().z;
        float z0 = (float) cz - 70, z1 = (float) cz + 240;
        VertexConsumer v = PursuitShade.solid(c);
        PursuitShade.noLights();
        // The asphalt, both carriageways, in long strips; the median barrier; kerbs and pavements.
        float step = 12;
        for (float z = (float) Math.floor(z0 / step) * step; z < z1; z += step) {
            float za = z, zb = z + step;
            PursuitShade.quad(v, m, ID, -ROAD_HALF, 0, za, ROAD_HALF, 0, za, ROAD_HALF, 0, zb, -ROAD_HALF, 0, zb, ASPHALT, 0);
            if (crossingAt(z + step / 2, 13)) continue;
            for (int side = -1; side <= 1; side += 2) {
                float e = side * ROAD_HALF, w = side * (ROAD_HALF + WALK);
                PursuitShade.quad(v, m, ID, e, .15f, za, w, .15f, za, w, .15f, zb, e, .15f, zb, WALKWAY, 0);
                PursuitShade.quad(v, m, ID, e, 0, za, e, .15f, za, e, .15f, zb, e, 0, zb, KERB, 0);
                // Beyond the pavement, the ground floors' base and the cross streets' ends.
                PursuitShade.quad(v, m, ID, w, .15f, za, side * (FACADE + 12), .15f, za, side * (FACADE + 12), .15f, zb, w, .15f, zb, 0x202127, 0);
            }
        }
        // The median: a concrete barrier, its face catching the light.
        for (float z = (float) Math.floor(z0 / step) * step; z < z1; z += step) {
            if (crossingAt(z + step / 2, 8)) continue;
            PursuitShade.lightsNear(0, 1, z + step / 2, 14);
            PursuitShade.box(v, m, ID, -.25f, 0, z, .25f, .3f, z + step, CONCRETE, .1f);
            PursuitShade.box(v, m, ID, -.12f, .3f, z, .12f, .82f, z + step, CONCRETE, .1f);
        }
        // Lane markings: dashed between lanes, solid along the edges, yellow by the median.
        PursuitShade.noLights();
        for (float z = (float) Math.floor(z0 / 9) * 9; z < z1; z += 9) {
            if (crossingAt(z, 7)) continue;
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 1; k <= 2; k++) {
                    float x = side * (MEDIAN + LANE_W * k);
                    PursuitShade.quad(v, m, ID, x - .07f, .012f, z, x + .07f, .012f, z, x + .07f, .012f, z + 3, x - .07f, .012f, z + 3, LINE, .3f);
                }
                float ex = side * (ROAD_HALF - .3f);
                PursuitShade.quad(v, m, ID, ex - .08f, .012f, z, ex + .08f, .012f, z, ex + .08f, .012f, z + 9, ex - .08f, .012f, z + 9, LINE, .3f);
                float yx = side * (MEDIAN + .25f);
                PursuitShade.quad(v, m, ID, yx - .06f, .012f, z, yx + .06f, .012f, z, yx + .06f, .012f, z + 9, yx - .06f, .012f, z + 9, YELLOW, .3f);
            }
        }
        // Crossings: zebra stripes, stop lines, the cross street's asphalt running off.
        for (float cr : CROSSINGS) {
            if (cr < z0 - 20 || cr > z1 + 20) continue;
            for (int side = -1; side <= 1; side += 2) {
                PursuitShade.quad(v, m, ID, side * ROAD_HALF, .01f, cr - 8, side * (FACADE + 40), .01f, cr - 8, side * (FACADE + 40), .01f, cr + 8, side * ROAD_HALF, .01f, cr + 8, ASPHALT, 0);
                for (float x = .8f; x < ROAD_HALF - .3f; x += 1.1f) {
                    float xa = side * x, xb = side * (x + .55f);
                    PursuitShade.quad(v, m, ID, xa, .013f, cr - 7, xb, .013f, cr - 7, xb, .013f, cr - 4, xa, .013f, cr - 4, LINE, .3f);
                }
                PursuitShade.quad(v, m, ID, 0, .013f, cr - 9.4f, side * ROAD_HALF, .013f, cr - 9.4f, side * ROAD_HALF, .013f, cr - 9, 0, .013f, cr - 9, LINE, .3f);
            }
        }
        // Buildings: the face toward the road, the end walls, the roofs.
        for (int i = firstBuilding(z0); i < BUILDINGS.size(); i++) {
            Building b = BUILDINGS.get(i);
            if (b.z0() > z1) break;
            if (b.z1() < z0) continue;
            float x0 = b.side() * b.face(), x1 = b.side() * (b.face() + b.depth());
            PursuitShade.lightsNear(x0, 6, (b.z0() + b.z1()) * .5f, 18);
            PursuitShade.box(v, m, ID, Math.min(x0, x1), 0, b.z0(), Math.max(x0, x1), b.height(), b.z1(), b.colour(), .05f);
            // A cornice and the shop level's darker band.
            float cx0 = x0 - b.side() * .35f;
            PursuitShade.box(v, m, ID, Math.min(cx0, x0), b.height() - .5f, b.z0(), Math.max(cx0, x0), b.height(), b.z1(), mix(b.colour(), 0x000000, .3f), 0);
            PursuitShade.box(v, m, ID, Math.min(cx0, x0), 3.9f, b.z0(), Math.max(cx0, x0), 4.25f, b.z1(), 0x121318, 0);
        }
        // Lamp posts with their arms out over the road, the median's double lamps.
        int k0 = (int) Math.floor(z0 / LAMP_GAP), k1 = (int) Math.ceil(z1 / LAMP_GAP);
        PursuitShade.noLights();
        for (int k = k0; k <= k1; k++) {
            float lz = k * LAMP_GAP + 7;
            if (crossingAt(lz, 6)) continue;
            for (int side = -1; side <= 1; side += 2) {
                float px = side * (ROAD_HALF + .7f), ax = side * (ROAD_HALF - 1.0f);
                if (nearCamera(c, px, lz, 1.6f)) continue;
                PursuitShade.box(v, m, ID, px - .09f, .15f, lz - .09f, px + .09f, 7.6f, lz + .09f, POLE, .4f);
                PursuitShade.box(v, m, ID, Math.min(px, ax), 7.45f, lz - .06f, Math.max(px, ax), 7.58f, lz + .06f, POLE, .4f);
                PursuitShade.box(v, m, ID, ax - .35f, 7.3f, lz - .16f, ax + .35f, 7.48f, lz + .16f, 0x3a3c42, .5f);
            }
            if ((k & 1) == 0) {
                float mz = lz + 12;
                PursuitShade.box(v, m, ID, -.08f, .82f, mz - .08f, .08f, 8.5f, mz + .08f, POLE, .4f);
                PursuitShade.box(v, m, ID, -1.8f, 8.38f, mz - .05f, 1.8f, 8.48f, mz + .05f, POLE, .4f);
                for (int side = -1; side <= 1; side += 2) PursuitShade.box(v, m, ID, side * 1.6f - .3f, 8.2f, mz - .14f, side * 1.6f + .3f, 8.38f, mz + .14f, 0x3a3c42, .5f);
            }
        }
        // Trees in the pavement, now and then.
        for (float z = (float) Math.floor(z0 / 14) * 14; z < z1; z += 14) {
            for (int side = -1; side <= 1; side += 2) {
                double h = hash(z * .73 + side * 9.1);
                if (h < .35 || crossingAt(z, 10)) continue;
                float tx = side * (ROAD_HALF + 1.6f), tz = z + 3;
                if (nearCamera(c, tx, tz, 2.6f)) continue;
                PursuitShade.lightsNear(tx, 3, tz, 14);
                PursuitShade.box(v, m, ID, tx - .12f, .15f, tz - .12f, tx + .12f, 2.6f, tz + .12f, 0x2a2018, 0);
                PursuitShade.box(v, m, ID, tx - 1.1f, 2.4f, tz - 1.1f, tx + 1.1f, 3.9f, tz + 1.1f, 0x18301c, 0);
                PursuitShade.box(v, m, ID, tx - .75f, 3.8f, tz - .75f, tx + .75f, 4.6f, tz + .75f, 0x1d3a21, 0);
            }
        }
        // Gantries over his carriageway: posts, a truss, two boards.
        for (float g : GANTRIES) {
            if (g < z0 - 10 || g > z1) continue;
            PursuitShade.lightsNear(-5, 6, g, 16);
            for (float x : new float[]{-(ROAD_HALF + .9f), -.55f}) PursuitShade.box(v, m, ID, x - .2f, 0, g - .2f, x + .2f, 7.6f, g + .2f, 0x4a4c52, .4f);
            PursuitShade.box(v, m, ID, -(ROAD_HALF + 1.1f), 7.0f, g - .25f, -.35f, 7.6f, g + .25f, 0x4a4c52, .4f);
            for (int i = 0; i < 2; i++) {
                float xa = -(1.2f + i * 5), xb = xa - 4.2f;
                PursuitShade.box(v, m, ID, xb, 5.2f, g - .32f, xa, 6.9f, g - .2f, 0x0d4a2a, .2f);
            }
        }
        // Signal poles at the crossings.
        for (float cr : CROSSINGS) {
            if (cr < z0 || cr > z1) continue;
            for (int side = -1; side <= 1; side += 2) {
                float px = side * (ROAD_HALF + .9f), pz = cr - 9.6f;
                if (nearCamera(c, px, pz, 1.6f)) continue;
                PursuitShade.box(v, m, ID, px - .1f, .15f, pz - .1f, px + .1f, 6.4f, pz + .1f, POLE, .4f);
                PursuitShade.box(v, m, ID, Math.min(px, side * 3.5f), 6.1f, pz - .07f, Math.max(px, side * 3.5f), 6.25f, pz + .07f, POLE, .4f);
                PursuitShade.box(v, m, ID, side * 4.2f - .55f, 5.55f, pz - .18f, side * 4.2f + .55f, 6.05f, pz + .12f, 0x16171b, .3f);
            }
        }
        // Signs: their dark backing (the light is drawn later).
        for (int i = firstSign(z0); i < SIGNS.size(); i++) {
            Sign s = SIGNS.get(i);
            if (s.z0() > z1) break;
            if (s.z1() < z0) continue;
            PursuitShade.noLights();
            if (s.blade()) {
                float xa = s.x(), xb = s.x() - s.side() * 1.5f;
                PursuitShade.box(v, m, ID, Math.min(xa, xb), s.y0(), s.z0(), Math.max(xa, xb), s.y1(), s.z1(), 0x0c0c10, .2f);
            } else {
                float xa = s.x(), xb = s.x() - s.side() * .12f;
                PursuitShade.box(v, m, ID, Math.min(xa, xb), s.y0(), s.z0(), Math.max(xa, xb), s.y1(), s.z1(), 0x0c0c10, .2f);
            }
        }
    }

    /** The city's light: windows, shopfronts, neon, lamps and their pools, the signals, and everything mirrored in the wet road. */
    static void light(FilmContext c, float tau, float time) {
        Matrix4f m = c.pose().last().pose();
        double cx = c.camera().x, cy = c.camera().y, cz = c.camera().z;
        float z0 = (float) cz - 70, z1 = (float) cz + 240;
        VertexConsumer v = PursuitShade.add(c);
        // Windows: a grid on each face toward the road, some lit warm, some cold, most dark; shop fronts bright.
        for (int i = firstBuilding(z0); i < BUILDINGS.size(); i++) {
            Building b = BUILDINGS.get(i);
            if (b.z0() > z1) break;
            if (b.z1() < z0) continue;
            float fx = b.side() * (b.face() - .04f);
            float fade = 1 - PursuitShade.fog(fx, 10, (b.z0() + b.z1()) * .5f);
            if (fade < .03f) continue;
            // The shop front at street level.
            int shop = hash(b.seed() * 2.9) < .5 ? 0xffd9a0 : hash(b.seed() * 1.1) < .5 ? 0xd8ecff : 0xfff0d8;
            A.set(fx, .5f, b.z0() + .5f); B.set(fx, .5f, b.z1() - .5f); C.set(fx, 3.6f, b.z1() - .5f); D.set(fx, 3.6f, b.z0() + .5f);
            PursuitShade.flat(v, m, A, B, C, D, shop, .55f * fade, .55f * fade, .25f * fade, .25f * fade);
            // Far buildings get every other row of windows (the haze has taken most of them anyway).
            float rowStep = Math.abs((b.z0() + b.z1()) * .5 - cz) > 140 ? 6.2f : 3.1f;
            for (float y = 5.6f; y < b.height() - 1.2f; y += rowStep) {
                for (float z = b.z0() + .9f; z < b.z1() - 1.1f; z += 1.7f) {
                    double h = hash(b.seed() * 13.1 + y * 3.7 + z * 1.9);
                    if (h < .48) continue;
                    int col = h < .72 ? 0xffc77a : h < .9 ? 0xbfd6ff : 0xffe9c8;
                    float a = (float) (.18 + .3 * hash(h * 91)) * fade;
                    A.set(fx, y, z); B.set(fx, y, z + 1.0f); C.set(fx, y + 1.6f, z + 1.0f); D.set(fx, y + 1.6f, z);
                    PursuitShade.flat(v, m, A, B, C, D, col, a, a, a, a);
                }
            }
        }
        // Neon: tubes round each sign, its characters in light, a glow about it.
        for (int i = firstSign(z0); i < SIGNS.size(); i++) {
            Sign s = SIGNS.get(i);
            if (s.z0() > z1) break;
            if (s.z1() < z0) continue;
            float fade = (1 - PursuitShade.fog(s.cx(), s.cy(), s.cz())) * flicker(s, time);
            if (fade < .03f) continue;
            v = PursuitShade.add(c);
            if (s.blade()) {
                // Both broad faces look along the road.
                for (int f = 0; f < 2; f++) {
                    float z = f == 0 ? s.z0() - .02f : s.z1() + .02f;
                    float xa = s.x() - s.side() * .1f, xb = s.x() - s.side() * 1.4f;
                    tubeRect(v, m, xa, xb, s.y0() + .1f, s.y1() - .1f, z, s.colour(), fade, true);
                    int chars = Math.max(1, (int) ((s.y1() - s.y0() - .4f) / 1.25f));
                    for (int k = 0; k < chars; k++) {
                        float yc = s.y1() - .35f - (k + .5f) * (s.y1() - s.y0() - .5f) / chars;
                        glyph(v, m, (xa + xb) * .5f, yc, z, .5f, s.seed() * 7 + k, s.colour(), fade, true, s.side());
                    }
                }
                discGlow(c, s.cx(), s.cy(), s.cz(), 2.6f + (s.y1() - s.y0()) * .25f, s.colour(), .22f * fade);
            } else {
                float x = s.x() - s.side() * .14f;
                tubeRect(v, m, s.z0() + .08f, s.z1() - .08f, s.y0() + .08f, s.y1() - .08f, x, s.colour(), fade, false);
                float h = s.y1() - s.y0();
                int chars = Math.max(1, (int) ((s.z1() - s.z0() - .3f) / (h * .9f)));
                for (int k = 0; k < chars; k++) {
                    float zc = s.z0() + .25f + (k + .5f) * (s.z1() - s.z0() - .5f) / chars;
                    glyph(v, m, x, s.cy(), zc, h * .34f, s.seed() * 5 + k, s.colour(), fade, false, s.side());
                }
                discGlow(c, x - s.side() * .5f, s.cy(), s.cz(), 1.2f + h, s.colour(), .14f * fade);
            }
        }
        // The lamps: their bright undersides, a halo, a pool on the road, and the long smear of each in the wet asphalt.
        int k0 = (int) Math.floor(z0 / LAMP_GAP), k1 = (int) Math.ceil(z1 / LAMP_GAP);
        for (int k = k0; k <= k1; k++) {
            float lz = k * LAMP_GAP + 7;
            if (crossingAt(lz, 6)) continue;
            int col = lampColour(lz);
            for (int side = -1; side <= 1; side += 2) lamp(c, side * (ROAD_HALF - 1.0f), 7.28f, lz, col, cx, cz);
            if ((k & 1) == 0) for (int side = -1; side <= 1; side += 2) lamp(c, side * 1.6f, 8.18f, lz + 12, col, cx, cz);
        }
        // Reflections of the neon in the road, longer and fainter.
        for (int i = firstSign(z0); i < SIGNS.size(); i++) {
            Sign s = SIGNS.get(i);
            if (s.z0() > z1) break;
            if (s.z1() < z0 || !s.blade() && hash(s.seed()) < .5) continue;
            float edge = s.side() * (ROAD_HALF - .4f);
            smear(c, s.blade() ? s.cx() : edge, s.cz(), s.colour(), .16f * flicker(s, time), 1.4f, cx, cz);
        }
        // Gantry boards: reflective lettering lit by their lamps.
        v = PursuitShade.add(c);
        for (float g : GANTRIES) {
            if (g < z0 - 10 || g > z1) continue;
            float fade = 1 - PursuitShade.fog(-5, 6, g);
            for (int i = 0; i < 2; i++) {
                float xa = -(1.2f + i * 5), xb = xa - 4.2f, z = g - .34f;
                for (int row = 0; row < 3; row++) {
                    float y = 6.45f - row * .45f, len = (float) (1.6 + 2.0 * hash(g * 3 + i * 7 + row));
                    A.set(xa - .4f, y, z); B.set(xa - .4f - len, y, z); C.set(xa - .4f - len, y + .18f, z); D.set(xa - .4f, y + .18f, z);
                    PursuitShade.flat(v, m, A, B, C, D, 0xe8f0e8, .45f * fade, .45f * fade, .45f * fade, .45f * fade);
                }
                discGlow(c, (xa + xb) * .5f, 7.0f, g - .6f, 2.4f, 0xd8e8ff, .1f * fade);
                v = PursuitShade.add(c);
            }
        }
        // Signals: red for the cross street, green for the boulevard (so traffic flows).
        for (float cr : CROSSINGS) {
            if (cr < z0 || cr > z1) continue;
            for (int side = -1; side <= 1; side += 2) {
                float pz = cr - 9.6f;
                discGlow(c, side * 4.2f + .3f, 5.8f, pz - .22f, .5f, 0x40ff90, .8f);
                discGlow(c, side * 4.2f + .3f, 5.8f, pz - .22f, 1.6f, 0x40ff90, .18f);
                smear(c, side * 4.2f, pz, 0x40ff90, .12f, .6f, cx, cz);
            }
        }
    }

    /** One lamp: the glowing underside, a halo, its pool on the asphalt, its streak in the wet road. */
    private static void lamp(FilmContext c, float x, float y, float z, int col, double cx, double cz) {
        Matrix4f m = c.pose().last().pose();
        float fade = 1 - PursuitShade.fog(x, y, z);
        if (fade < .03f) return;
        VertexConsumer v = PursuitShade.add(c);
        A.set(x - .3f, y, z - .14f); B.set(x + .3f, y, z - .14f); C.set(x + .3f, y, z + .14f); D.set(x - .3f, y, z + .14f);
        PursuitShade.flat(v, m, A, B, C, D, 0xffffff, fade, fade, fade, fade);
        discGlow(c, x, y - .1f, z, .9f, 0xffffff, .55f * fade);
        discGlow(c, x, y - .2f, z, 3.2f, col, .26f * fade);
        // The pool of light on the road under it.
        v = PursuitShade.add(c);
        int n = 16;
        float rx = 4.2f, rz = 5.5f;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            A.set(x, .02f, z); B.set(x, .02f, z);
            C.set(x + (float) Math.cos(a1) * rx, .02f, z + (float) Math.sin(a1) * rz);
            D.set(x + (float) Math.cos(a0) * rx, .02f, z + (float) Math.sin(a0) * rz);
            PursuitShade.flat(v, m, A, B, C, D, col, .2f * fade, .2f * fade, 0, 0);
        }
        smear(c, x, z, col, .32f * fade, 1, cx, cz);
    }
    /**
     * A light mirrored in the wet asphalt: a streak on the road from below the light toward the camera, widest
     * and brightest near its foot, as a wet street at night always shows them.
     */
    static void smear(FilmContext c, float x, float z, int col, float alpha, float width, double cx, double cz) {
        double dx = cx - x, dz = cz - z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < .5 || alpha < .01f) return;
        float ux = (float) (dx / len), uz = (float) (dz / len);
        float reach = (float) Math.min(14, len * .55);
        float px = -uz * .5f * width, pz = ux * .5f * width;
        Matrix4f m = c.pose().last().pose();
        VertexConsumer v = PursuitShade.add(c);
        float bx = x + ux * reach * .35f, bz = z + uz * reach * .35f, ex = x + ux * reach, ez = z + uz * reach;
        A.set(x - px * .6f, .018f, z - pz * .6f); B.set(x + px * .6f, .018f, z + pz * .6f); C.set(bx + px, .018f, bz + pz); D.set(bx - px, .018f, bz - pz);
        PursuitShade.flat(v, m, A, B, C, D, col, alpha * .7f, alpha * .7f, alpha, alpha);
        A.set(bx - px, .018f, bz - pz); B.set(bx + px, .018f, bz + pz); C.set(ex + px * .5f, .018f, ez + pz * .5f); D.set(ex - px * .5f, .018f, ez - pz * .5f);
        PursuitShade.flat(v, m, A, B, C, D, col, alpha, alpha, 0, 0);
    }
    /** A rectangle of neon tube: four thin bars, a white-hot core inside the colour. */
    private static void tubeRect(VertexConsumer v, Matrix4f m, float a0, float a1, float y0, float y1, float fixed, int col, float fade, boolean faceZ) {
        float t = .07f;
        bar(v, m, a0, a1, y0, y0 + t, fixed, col, fade, faceZ);
        bar(v, m, a0, a1, y1 - t, y1, fixed, col, fade, faceZ);
        bar(v, m, a0, a0 + (a1 > a0 ? t : -t), y0, y1, fixed, col, fade, faceZ);
        bar(v, m, a1 - (a1 > a0 ? t : -t), a1, y0, y1, fixed, col, fade, faceZ);
    }
    /** A flat bar of light; faceZ: the bar lies in a plane of constant z (a = x), else of constant x (a = z). */
    private static void bar(VertexConsumer v, Matrix4f m, float a0, float a1, float y0, float y1, float fixed, int col, float fade, boolean faceZ) {
        if (faceZ) { A.set(a0, y0, fixed); B.set(a1, y0, fixed); C.set(a1, y1, fixed); D.set(a0, y1, fixed); }
        else { A.set(fixed, y0, a0); B.set(fixed, y0, a1); C.set(fixed, y1, a1); D.set(fixed, y1, a0); }
        PursuitShade.flat(v, m, A, B, C, D, col, .9f * fade, .9f * fade, .9f * fade, .9f * fade);
        PursuitShade.flat(v, m, A, B, C, D, 0xffffff, .25f * fade, .25f * fade, .25f * fade, .25f * fade);
    }
    /**
     * One character in tubes, after the look of Hangul on neon signs: two to four strokes in a square cell (bars,
     * hooks, a box, a ring), chosen by the seed.
     */
    private static void glyph(VertexConsumer v, Matrix4f m, float u, float y, float fixed, float half, int seed, int col, float fade, boolean faceZ, int side) {
        int strokes = 2 + (int) (hash(seed * 1.7) * 3);
        for (int s = 0; s < strokes; s++) {
            double h = hash(seed * 3.1 + s * 7.7);
            float cu = u + (float) (hash(seed + s * 2.3) - .5) * half, cy = y + (float) (hash(seed * 2 + s * 5.1) - .5) * half;
            float len = half * (.6f + .7f * (float) hash(seed * 4 + s));
            float t = Math.max(.06f, half * .14f);
            if (h < .35) bar(v, m, cu - len * .5f, cu + len * .5f, cy - t * .5f, cy + t * .5f, fixed, col, fade, faceZ);
            else if (h < .65) bar(v, m, cu - t * .5f, cu + t * .5f, cy - len * .5f, cy + len * .5f, fixed, col, fade, faceZ);
            else {
                float r = len * .35f;
                bar(v, m, cu - r, cu + r, cy - r, cy - r + t, fixed, col, fade, faceZ);
                bar(v, m, cu - r, cu + r, cy + r - t, cy + r, fixed, col, fade, faceZ);
                bar(v, m, cu - r, cu - r + t, cy - r, cy + r, fixed, col, fade, faceZ);
                bar(v, m, cu + r - t, cu + r, cy - r, cy + r, fixed, col, fade, faceZ);
            }
        }
    }
    /** A round glow facing the camera (the film's own soft light). */
    static void discGlow(FilmContext c, float x, float y, float z, float size, int col, float alpha) {
        com.FIRNI.superheromod.client.render.film.FilmFx.glow(c, new net.minecraft.world.phys.Vec3(x, y, z), size, col, alpha);
    }
}
