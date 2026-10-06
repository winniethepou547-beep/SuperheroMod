package com.FIRNI.superheromod.client.render.iceman;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.client.render.iceman.IceMesh.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The Ice Armory's three weapons, all of Iceman's own ice (IceMesh), in the hand's frame: pixels, the grip at the origin
 * (inside his fist), the long axis toward -z, the blades' width along y. Built like Minecraft items made 3D: boxes and
 * four-sided points, textured ice (never hexagonal crystals, never round).
 * <ul>
 * <li>MACE: a heavy cube of ice for a head, a square ice spike standing out of each side and a longer one off its end,
 * small cubes studding its corners; a square handle wrapped in wider bands of milky ice, a square collar plate where
 * the head sits, a cube pommel. Held and grown (size 1..MACE_MAX) it is the same mace scaled up with new blocks of
 * glacier ice accreting over the head, one after another, each turned its own way with its own square spikes (rime
 * first, then the ice thickening), until it is about three times his height.</li>
 * <li>SPEAR: a long square shaft (about 1.6 blocks) with frost knots, a square collar with four small points, a flat
 * blade of ice (a box widening out of the collar, then narrowing in a step to a four-sided point) with a milky ridge
 * down its middle, a square butt spike. Drawn back for a throw (size above 1) its blade grows longer.</li>
 * <li>SWORD: a flat box blade with a milky ridge, its point a step and then a four-sided point, a bar crossguard with
 * cube ends, a square grip with bands, a cube pommel.</li>
 * </ul>
 * Forming (form 0..1) follows the ice language: a breath of frost and sparkles along the weapon's shape first, then small
 * frost cubes along it, then the real parts growing out of the grip along the axis and thickening (never popping in at
 * full size). Cracking (crack 0..1): bright crack lines spreading over it; 2 and over: the mace's head (or the blade) has
 * broken away and only the handle is left in the fist.
 */
public final class IcemanArmory {
    private IcemanArmory() {}

    /** Plain sizes (pixels): the mace's head centre, its half-size, its tip; the spear's blade base; the sword's tip. */
    static final float MACE_HEAD = 14.5f, MACE_R = 3.2f, MACE_TIP = 20.5f, MACE_POMMEL = 5.0f, SPEAR_BLADE = 17f, SPEAR_BUTT = 8.5f,
            SWORD_TIP = 21f, SWORD_POMMEL = 4.2f;

    /** How long the weapon is from the grip to its tip (pixels). */
    public static float length(int weapon, float size) {
        return switch (weapon) {
            case W_MACE -> MACE_TIP * size;
            case W_SPEAR -> SPEAR_BLADE + 8 * size;
            default -> SWORD_TIP * size;
        };
    }
    /** Where the mace's head centre is along the axis (pixels from the grip, toward -z) at a size. */
    public static float maceHead(float size) { return MACE_HEAD * size; }

    // ------------------------------------------------------------------ forming
    private static float form;
    /** How far a part at u (0 at the grip .. 1 at the tip; the butt end counts by its distance too) has grown. */
    private static float grown(float u) {
        if (form >= 1) return 1;
        float front = (form - .28f) / .72f * 1.35f;
        return Mth.clamp((front - Math.abs(u)) / .3f, 0, 1);
    }
    /** A part's thickness while it grows (thin first, filling out). */
    private static float thick(float g) { return .3f + .7f * Mth.sqrt(g); }

    /**
     * Draws the weapon in the hand's frame (pixels; the grip at the origin, the long axis toward -z), formed (0..1), its
     * size (1 plain), cracked (0..1; 2 and over: the head / blade gone). After any change of the pose stack call c.at(p).
     */
    public static void inHand(IceMesh.Ctx c, PoseStack p, int weapon, float form, float size, float crack, float time) {
        if (form <= .001f) return;
        IcemanArmory.form = Mth.clamp(form, 0, 1);
        float flash = c.flash, ox = c.ox, oy = c.oy, oz = c.oz;
        // The texture belongs to the weapon (its own frame, pixel for pixel).
        c.ox = c.oy = c.oz = 0;
        if (form < 1) c.flash = Math.max(flash, .35f * (1 - form));
        try {
            switch (weapon) {
                case W_MACE -> mace(c, size, crack, time);
                case W_SPEAR -> spear(c, size, crack, time);
                default -> sword(c, size, crack, time);
            }
            if (form < 1) forming(c, weapon, size, time);
        } finally {
            c.flash = flash;
            c.ox = ox; c.oy = oy; c.oz = oz;
            IcemanArmory.form = 1;
        }
    }

    /** The first stages: frost and sparkles along the shape, small frost cubes that the real ice then swallows. */
    private static void forming(IceMesh.Ctx c, int weapon, float size, float time) {
        float len = length(weapon, size), back = weapon == W_MACE ? MACE_POMMEL * size : weapon == W_SPEAR ? SPEAR_BUTT : SWORD_POMMEL;
        float frost = Mth.clamp(form / .2f, 0, 1) * (1 - Mth.clamp((form - .45f) / .4f, 0, 1));
        int n = 14;
        for (int i = 0; i < n; i++) {
            float q = (float) hash(i * 3.7 + weapon * 11);
            float z = Mth.lerp(q, back, -len);
            float u = z / len;
            float r = radiusAt(weapon, -z, size);
            double a = hash(i * 5.3 + weapon) * Mth.TWO_PI;
            Vec3 at = new Vec3(Math.cos(a) * r, Math.sin(a) * r, z);
            // The breath of frost: sparkles flickering along the shape.
            float tw = .5f + .5f * Mth.sin(time * 1.7f + i * 2.1f);
            if (frost > .02f) IceMesh.sparkle(c, at, .9f + .6f * r * .2f, .8f * frost * tw);
            // A nucleus: a small cube of frost that grows, then is swallowed by the part growing over it.
            float nk = Mth.clamp((form - .12f - Math.abs(u) * .3f) / .25f, 0, 1) * (1 - grown(u));
            if (nk > .01f) {
                float sz = 1.3f * nk * (.7f + .6f * (float) hash(i * 2.9));
                IceParticles.cube(c, at.x * .7, at.y * .7, at.z, sz, sz, sz, (float) a, (float) (hash(i * 9.1) - .5), (float) a * .5f, FROST, .9f);
            }
        }
        // A thin frosted sheath along the shape before the ice thickens inside it.
        if (frost > .02f) {
            float gz = -len * Mth.clamp(form / .35f, 0, 1), r = radiusAt(weapon, 0, size) + .25f;
            IceMesh.box(c, -r, -r, gz, r, r, Math.min(back, 2), FROST, .35f * frost);
        }
    }
    /** About how thick the weapon is at a distance d (pixels) out from the grip (negative: behind it). */
    private static float radiusAt(int weapon, float d, float size) {
        return switch (weapon) {
            case W_MACE -> Math.abs(d - MACE_HEAD * size) < MACE_R * size * 1.2f ? MACE_R * size : .8f * size;
            case W_SPEAR -> d > SPEAR_BLADE ? 1.4f * size : .55f;
            default -> d > 2.6f ? 1.2f : .6f;
        };
    }

    // ------------------------------------------------------------------ shapes in the hand's frame
    /** A square bar along the axis from z0 to z1, half-width h. */
    private static void bar(IceMesh.Ctx c, float z0, float z1, float h, Mat mat, float a) {
        IceMesh.box(c, -h, -h, Math.min(z0, z1), h, h, Math.max(z0, z1), mat, a);
    }
    private static final float[] P8 = new float[24];
    /** A flat block along the axis from z0 (half-width w0, half-thickness t0) to z1 (w1, t1): its width along y, thickness along x. */
    private static void slab(IceMesh.Ctx c, float z0, float w0, float t0, float z1, float w1, float t1, Mat mat, float a) {
        float[] p = P8;
        for (int k = 0; k < 8; k++) {
            int q = k & 3;
            float w = k < 4 ? w0 : w1, t = k < 4 ? t0 : t1;
            p[k * 3] = q == 0 || q == 3 ? -t : t;
            p[k * 3 + 1] = q < 2 ? -w : w;
            p[k * 3 + 2] = k < 4 ? z0 : z1;
        }
        IceParticles.hexa(c, p, mat, a);
    }
    /** A four-sided point along the axis: from the rectangle at z0 (half-width w along y, half-thickness t along x) to the tip. */
    private static void point(IceMesh.Ctx c, float z0, float w, float t, float tipZ, Mat mat, float a) {
        IceMesh.tri(c, -t, -w, z0, t, -w, z0, 0, 0, tipZ, mat, a);
        IceMesh.tri(c, t, -w, z0, t, w, z0, 0, 0, tipZ, mat, a);
        IceMesh.tri(c, t, w, z0, -t, w, z0, 0, 0, tipZ, mat, a);
        IceMesh.tri(c, -t, w, z0, -t, -w, z0, 0, 0, tipZ, mat, a);
    }

    // ------------------------------------------------------------------ the mace
    private static void mace(IceMesh.Ctx c, float s, float crack, float time) {
        float headZ = -MACE_HEAD * s, r = MACE_R * s, handleTop = -(MACE_HEAD - MACE_R * .82f) * s;
        boolean headGone = crack >= 2;
        float total = MACE_TIP * s;
        // The pommel: a cube behind the fist with a short point.
        float gp = grown(-MACE_POMMEL * s / total * 1.2f);
        if (gp > 0) {
            float h = 1.15f * s * thick(gp);
            IceMesh.box(c, -h, -h, (MACE_POMMEL - 1.6f) * s, h, h, (MACE_POMMEL + .2f) * s, MILKY, 1);
            point(c, (MACE_POMMEL + .2f) * s, .7f * s * thick(gp), .7f * s * thick(gp), (MACE_POMMEL + .2f + 1.3f * gp) * s, CLEAR, 1);
        }
        // The handle: a square bar wrapped in wider bands; grows out of the fist both ways.
        int segs = 8;
        float z0 = (MACE_POMMEL - 1.4f) * s, z1 = headGone ? handleTop * .72f : handleTop;
        for (int i = 0; i < segs; i++) {
            float a = Mth.lerp(i / (float) segs, z0, z1), b = Mth.lerp((i + 1) / (float) segs, z0, z1);
            float g = grown(Math.max(Math.abs(a), Math.abs(b)) / total * (a > 0 ? 1.2f : 1));
            if (g <= 0) continue;
            float bb = a + (b - a) * Math.min(1, g * 1.3f);
            boolean band = i % 3 == 1;
            bar(c, a, bb, (band ? .98f : .8f) * s * thick(g), band ? MILKY : CLEAR, 1);
        }
        if (headGone) {
            // The jagged stump left in the fist, the clean bright inside showing.
            for (int i = 0; i < 4; i++) {
                double a = i * 1.7 + .4;
                IceParticles.spike(c, Math.cos(a) * .45 * s, Math.sin(a) * .45 * s, z1 + .3f * s, Math.cos(a) * .35, Math.sin(a) * .35, -1,
                        (i % 2 == 0 ? 1.4f : 1.9f) * s, .4f * s, (float) a, 0, FRESH, 1);
            }
            cracksShaft(c, MACE_CRACKS_HANDLE, 1, s, z0, z1, .82f * s, false);
            return;
        }
        float gh = grown(MACE_HEAD * s / total);
        if (gh <= 0) return;
        // The collar where the head sits: a square plate and four small points at its corners.
        float gc = grown((MACE_HEAD - MACE_R) * s / total);
        if (gc > 0) {
            float h = 1.75f * s * thick(gc);
            IceMesh.box(c, -h, -h, handleTop - .3f * s, h, h, handleTop + 1.0f * s, MILKY, 1);
            for (int i = 0; i < 4; i++) {
                float sx = i == 0 || i == 3 ? -1 : 1, sy = i < 2 ? -1 : 1;
                IceParticles.spike(c, sx * h * .8f, sy * h * .8f, handleTop + .4f * s, sx * .6, sy * .6, -1, 1.6f * s * gc, .38f * s, Mth.HALF_PI * .5f, 0, CLEAR, 1);
            }
        }
        // The head grows from its middle outward: the cube, then its spikes and studs.
        float hr = r * Mth.clamp(gh * 1.25f, 0, 1);
        float hk = thick(gh);
        float layers = (s - 1) / (MACE_MAX - 1) * 6;
        float gf = Mth.clamp((gh - .25f) / .75f, 0, 1);
        {
            IceMesh.box(c, -hr, -hr, headZ - hr * 1.06f, hr, hr, headZ + hr * 1.06f, CLEAR, 1);
            if (gf > 0) {
                // A square spike out of the middle of each side.
                for (int i = 0; i < 4; i++) {
                    float dx = i == 0 ? 1 : i == 1 ? -1 : 0, dy = i == 2 ? 1 : i == 3 ? -1 : 0;
                    IceParticles.spike(c, dx * hr * .9f, dy * hr * .9f, headZ, dx, dy, 0, (2.4f * s + hr * .1f) * gf, .95f * s * hk, Mth.HALF_PI * .5f, 1, CLEAR, 1);
                }
                // Small cubes studding its corners.
                float st = .95f * s * gf;
                for (int i = 0; i < 8; i++) {
                    float sx = (i & 1) == 0 ? -1 : 1, sy = (i & 2) == 0 ? -1 : 1, sz = (i & 4) == 0 ? -1 : 1;
                    IceMesh.cube(c, sx * hr, sy * hr, headZ + sz * hr * 1.06f, st, st, st, MILKY, 1);
                }
            }
        }
        // The long spike off its end.
        float tipLen = (MACE_TIP - MACE_HEAD - MACE_R * .8f) * s * gf + .01f;
        IceParticles.spike(c, 0, 0, headZ - hr * .95f - r * .07f * Math.min(6, layers), 0, 0, -1, tipLen, .95f * s * hk, Mth.HALF_PI * .5f, 2, CLEAR, 1);
        // Grown: the new blocks of ice accreting over the head, each turned its own way with its own spikes.
        for (int i = 1; i <= 6 && layers > 0; i++) {
            float f = Mth.clamp(layers - (i - 1), 0, 1);
            if (f <= 0) break;
            layer(c, headZ, r, i, f, s, time);
        }
        if (crack > 0) {
            cracksHead(c, MACE_CRACKS_HEAD, Math.min(1, crack), s, headZ, r * (1.04f + .07f * Math.min(6, layers)));
            cracksShaft(c, MACE_CRACKS_HANDLE, Math.min(1, crack) * .8f, s, z0, z1, .82f * s, false);
        }
    }
    /**
     * One block of the growing mace (i 1..6, f 0..1 how far it has formed): rime first (white, see-through), then the
     * square spikes of the layer standing out of it, then the glacier ice thickening into a block over everything under it.
     */
    private static void layer(IceMesh.Ctx c, float headZ, float r, int i, float f, float s, float time) {
        float shell = r * (1.02f + .07f * i), under = r * (1.02f + .07f * (i - 1));
        float rime = Mth.clamp(f / .3f, 0, 1) * (1 - Mth.clamp((f - .5f) / .4f, 0, 1));
        float body = Mth.clamp((f - .25f) / .6f, 0, 1);
        // Each block turned its own way about the axis, tipped a little (uneven, piled up).
        float roll = (float) (hash(i * 3.3) - .5) * .8f, tip = (float) (hash(i * 5.9) - .5) * .35f, yaw = (float) (hash(i * 8.1) - .5) * .35f;
        float[] ax = IceParticles.turn(yaw, tip, roll, IceParticles.AX);
        if (rime > .01f) {
            float h = Mth.lerp(.5f, under, shell);
            IceParticles.obox(c, 0, 0, headZ, ax, h, h, h * 1.08f, FROST.alpha(.45f * rime), 1);
        }
        if (body > .01f) {
            float h = Mth.lerp(body, under, shell);
            IceParticles.obox(c, 0, 0, headZ, ax, h, h, h * 1.08f, GLACIER.alpha(.35f + .65f * body), 1);
        }
        // The layer's own square spikes, in new directions (uneven: some long, some stubs).
        float gs = Mth.clamp((f - .15f) / .7f, 0, 1);
        if (gs <= .01f) return;
        for (int k = 0; k < 5; k++) {
            double a = hash(i * 13 + k * 3.1) * Mth.TWO_PI, z = (hash(i * 7 + k * 5.7) - .5) * 1.8;
            Vec3 d = new Vec3(Math.cos(a), Math.sin(a), z).normalize();
            float len = (1.6f + 2.2f * (float) hash(i * 3 + k)) * s / (1 + .1f * i) * gs;
            Vec3 b = new Vec3(0, 0, headZ).add(d.scale(shell * .85f));
            IceParticles.spike(c, b, d, len, .5f * s * (.5f + .5f * gs) / (1 + .08f * i), (float) a, 1, k % 2 == 0 ? CLEAR : MILKY, 1);
        }
        // Sparkles where the new ice is still forming.
        if (f < 1) for (int k = 0; k < 4; k++) {
            double a = hash(i * 17 + k) * Mth.TWO_PI + time * .05, z = (hash(i * 19 + k) - .5) * 1.6;
            Vec3 d = new Vec3(Math.cos(a), Math.sin(a), z).normalize();
            IceMesh.sparkle(c, new Vec3(0, 0, headZ).add(d.scale(shell)), 1.2f * s * .5f, .9f * (1 - f));
        }
    }

    // ------------------------------------------------------------------ the spear
    private static void spear(IceMesh.Ctx c, float size, float crack, float time) {
        float total = SPEAR_BLADE + 8 * size;
        // The butt: a square spike.
        float gb = grown(-(SPEAR_BUTT + 2) / total);
        if (gb > 0) {
            float h = .62f * thick(gb);
            IceMesh.box(c, -h, -h, SPEAR_BUTT - 1.4f, h, h, SPEAR_BUTT - .2f, MILKY, 1);
            point(c, SPEAR_BUTT - .2f, h, h, SPEAR_BUTT - .2f + 2.6f * gb, CLEAR, 1);
        }
        // The shaft: a long square bar with knots of frost.
        int segs = 6;
        float z0 = SPEAR_BUTT - .2f, z1 = -SPEAR_BLADE + .6f;
        for (int i = 0; i < segs; i++) {
            float a = Mth.lerp(i / (float) segs, z0, z1), b = Mth.lerp((i + 1) / (float) segs, z0, z1);
            float g = grown(Math.max(Math.abs(a), Math.abs(b)) / total * (a > 0 ? 1.3f : 1));
            if (g <= 0) continue;
            float bb = a + (b - a) * Math.min(1, g * 1.3f);
            bar(c, a, bb, .52f * thick(g), CLEAR, 1);
            if (g >= 1 && i % 2 == 1) IceMesh.cube(c, 0, 0, b, 1.3f, 1.3f, .7f, FROST.alpha(.75f));
        }
        float gl = grown((SPEAR_BLADE - 1) / total);
        if (gl > 0) IceMesh.line(c, new Vec3(.53, .3, SPEAR_BUTT - 1), new Vec3(.53, .3, Mth.lerp(gl, SPEAR_BUTT, z1)), .06f, .35f * gl, .42f * gl, .5f * gl);
        if (crack >= 2) return;
        // The collar: a square block with four small points swept forward.
        float gc = grown(SPEAR_BLADE / total);
        if (gc > 0) {
            float h = 1.05f * thick(gc);
            IceMesh.box(c, -h, -h, -SPEAR_BLADE - .2f, h, h, -SPEAR_BLADE + 1.2f, MILKY, 1);
            for (int i = 0; i < 4; i++) {
                float sx = i == 0 || i == 3 ? -1 : 1, sy = i < 2 ? -1 : 1;
                IceParticles.spike(c, sx * h * .7f, sy * h * .7f, -SPEAR_BLADE + .4f, sx * .5, sy * .5, -1, 1.5f * gc, .3f, Mth.HALF_PI * .5f, 0, CLEAR, 1);
            }
        }
        // The blade, growing out of the collar to its point (longer as it is drawn back): a flat block widening, a step, the point.
        float blade = 8 * size, w = 2.0f * (1 + .25f * (size - 1)), th = .5f;
        float gB = grown((SPEAR_BLADE + blade * .5f) / total);
        if (gB > 0) {
            float reach = blade * Math.min(1, gB * 1.15f), ww = w * thick(gB), tt = th * thick(gB), za = -SPEAR_BLADE - .1f;
            float zb = za - reach * .4f, zc = za - reach * .68f, tip = za - reach;
            slab(c, za, ww * .6f, tt, zb, ww, tt, CLEAR, 1);
            slab(c, zb, ww, tt, zc, ww * .62f, tt * .85f, CLEAR, 1);
            point(c, zc, ww * .5f, tt * .8f, tip, CLEAR, 1);
            // The milky ridge down its middle.
            IceMesh.box(c, -tt * 1.3f, -.28f, zc + reach * .05f, tt * 1.3f, .28f, za, MILKY, .95f);
            float k = Math.max(0, gB * 1.1f - .1f);
            if (k > .01f) edges(c, za, zb, zc, tip, ww * .6f, ww, ww * .62f, ww * .5f, .07f, k);
            // The tip still growing while he draws it back: frost glittering on the point.
            if (size > 1.01f) {
                float kk = Mth.clamp((size - 1) / .6f, 0, 1);
                Vec3 at = new Vec3(0, 0, tip);
                IceMesh.sparkle(c, at, 1.5f + .8f * Mth.sin(time * .8f), .6f * kk);
                IceMesh.glow(c, at, 2.2f * kk, .1f * kk, .2f * kk, .3f * kk);
            }
        }
        if (crack > 0) cracksShaft(c, SPEAR_CRACKS, Math.min(1, crack), 1, z0, -SPEAR_BLADE - blade * .9f, .56f, false);
    }
    /** A white glint along both edges of a blade: through its widths at four places (z0 widening to z1, stepping in to z2, the point at tip). */
    private static void edges(IceMesh.Ctx c, float z0, float z1, float z2, float tip, float w0, float w1, float w2, float w3, float lw, float k) {
        float r = .62f * k, g = .78f * k, b = .9f * k;
        for (int sg = -1; sg <= 1; sg += 2) {
            Vec3 a = new Vec3(0, sg * w0, z0), p1 = new Vec3(0, sg * w1, z1), p2 = new Vec3(0, sg * w2, z2), p3 = new Vec3(0, sg * w3, z2), t = new Vec3(0, 0, tip);
            IceMesh.line(c, a, p1, lw, r, g, b);
            IceMesh.line(c, p1, p2, lw, r, g, b);
            IceMesh.line(c, p3, t, lw, r, g, b);
        }
    }

    // ------------------------------------------------------------------ the sword
    private static void sword(IceMesh.Ctx c, float size, float crack, float time) {
        float total = SWORD_TIP * size;
        float gp = grown(-(SWORD_POMMEL + 1.5f) / total);
        if (gp > 0) {
            float h = 1.0f * thick(gp);
            IceMesh.box(c, -h, -h, SWORD_POMMEL - 1, h, h, SWORD_POMMEL + .9f, MILKY, 1);
        }
        // The grip: a square bar with bands of ice.
        for (int i = 0; i < 3; i++) {
            float a = SWORD_POMMEL - 1 - i * 2.1f, b = a - 2.1f;
            float g = grown(Math.max(Math.abs(a), Math.abs(b)) / total * (a > 0 ? 1.3f : 1));
            if (g <= 0) continue;
            bar(c, a, a + (b - a) * Math.min(1, g * 1.3f), (i % 2 == 0 ? .58f : .68f) * thick(g), i % 2 == 0 ? CLEAR : MILKY, 1);
        }
        // The crossguard: a bar of milky ice across the blade's width, a cube of clear ice at each end.
        float gg = grown(2.6f / total);
        if (gg > 0) {
            float reach = 1.0f + 3.0f * gg, t = .7f * thick(gg);
            IceMesh.box(c, -t, -reach, -3.0f, t, reach, -1.8f, MILKY, 1);
            float e = 1.25f * thick(gg);
            for (int sd = -1; sd <= 1; sd += 2) IceMesh.cube(c, 0, sd * reach, -2.4f, e, e, e * 1.1f, CLEAR, 1);
        }
        if (crack >= 2) return;
        // The blade: a flat box, then a step in and a four-sided point; a milky ridge down its middle; glints on its edges.
        float len = (SWORD_TIP - 2.9f) * size;
        float gB = grown(.5f);
        if (gB > 0) {
            float reach = len * Math.min(1, gB * 1.15f), w = 1.3f * thick(gB), th = .42f * thick(gB);
            float za = -2.9f, zb = za - reach * .72f, zc = za - reach * .86f, tip = za - reach;
            IceMesh.box(c, -th, -w, zb, th, w, za, CLEAR, 1);
            slab(c, zb, w, th, zc, w * .62f, th * .9f, CLEAR, 1);
            point(c, zc, w * .62f, th * .9f, tip, CLEAR, 1);
            IceMesh.box(c, -th * 1.28f, -.26f, -2.9f - reach * .8f, th * 1.28f, .26f, -3.0f, MILKY, .95f);
            float k = Math.max(0, gB * 1.1f - .1f);
            if (k > .01f) {
                edges(c, za, zb, zc, tip, w, w, w * .62f, w * .62f, .06f, k);
                // The glint: a bright point sliding down the edge now and then.
                float g = (time * .045f) % 1.6f;
                if (k > .5f && g < 1) {
                    float z = za - g * reach, ww = g < .72f ? w : g < .86f ? Mth.lerp((g - .72f) / .14f, w, w * .62f) : w * .62f * (1 - (g - .86f) / .14f);
                    IceMesh.sparkle(c, new Vec3(0, ww, z), 1.6f, Mth.sin(Mth.PI * g) * k);
                }
            }
        }
        if (crack > 0) cracksShaft(c, SWORD_CRACKS, Math.min(1, crack), 1, -3.2f, -2.9f - len * .95f, 1.1f, true);
    }

    // ------------------------------------------------------------------ cracks
    /**
     * Crack lines, generated once per shape: each a list of points {a, b} on the part's surface (head: a = angle round
     * the axis (onto the cube's square), b = -1..1 along it; shaft/blade: a = angle, b = 0..1 from its start to its end)
     * with the order they open in.
     */
    private static final float[][] MACE_CRACKS_HEAD = crackSet(9, 101), MACE_CRACKS_HANDLE = crackSet(3, 131), SPEAR_CRACKS = crackSet(5, 151),
            SWORD_CRACKS = crackSet(6, 171);
    private static float[][] crackSet(int count, int seed) {
        float[][] out = new float[count][];
        for (int i = 0; i < count; i++) {
            int n = 5;
            float[] pts = new float[n * 3];
            float a = (float) hash(seed + i * 7.1) * Mth.TWO_PI, b = (float) hash(seed * 3 + i * 2.3) * 2 - 1;
            float da = ((float) hash(seed + i * 4.4) - .5f) * .9f, db = ((float) hash(seed + i * 8.8) - .5f) * .7f;
            float start = (float) hash(seed * 7 + i) * .45f;
            for (int k = 0; k < n; k++) {
                pts[k * 3] = a; pts[k * 3 + 1] = b; pts[k * 3 + 2] = start + k * .14f;
                a += da + ((float) hash(seed + i * 31 + k * 3.3) - .5f) * .5f;
                b += db + ((float) hash(seed + i * 37 + k * 5.1) - .5f) * .3f;
            }
            out[i] = pts;
        }
        return out;
    }
    /** A crack set spreading over a shaft or a blade from z0 to z1 (k 0..1 how far they have run); flat: on a blade's two faces. */
    private static void cracksShaft(IceMesh.Ctx c, float[][] set, float k, float s, float z0, float z1, float r, boolean flat) { cracks(c, set, k, s, z0, z1, r, flat ? 2 : 0); }
    /** A crack set spreading over the mace's head (centre z0, radius r). */
    private static void cracksHead(IceMesh.Ctx c, float[][] set, float k, float s, float z0, float r) { cracks(c, set, k, s, z0, 0, r, 1); }
    private static void cracks(IceMesh.Ctx c, float[][] set, float k, float s, float z0, float z1, float r, int mode) {
        if (k <= .01f) return;
        float w = .1f * Math.max(1, s * .6f);
        for (float[] pts : set) {
            Vec3 prev = null;
            for (int i = 0; i < pts.length / 3; i++) {
                float open = Mth.clamp((k - pts[i * 3 + 2]) / .12f, 0, 1);
                float a = pts[i * 3], b = pts[i * 3 + 1];
                Vec3 p = mode == 1 ? onSphere(a, b, z0, r) : mode == 2 ? onBlade(a, b, z0, z1, r) : onShaft(a, b, z0, z1, r);
                if (prev != null && open > 0) {
                    Vec3 to = prev.lerp(p, open);
                    float br = .55f + .45f * k;
                    IceMesh.line(c, prev, to, w, .55f * br, .85f * br, br);
                }
                if (open <= 0) break;
                prev = p;
            }
        }
    }
    private static Vec3 onBlade(float a, float b, float z0, float z1, float r) {
        float q = Mth.clamp((b + 1) / 2, 0, 1);
        return new Vec3(Mth.cos(a) >= 0 ? .46f : -.46f, Mth.sin(a) * r * (1 - .6f * q * q), Mth.lerp(q, z0, z1));
    }
    /** A point on the head's cube (half-size r about z0): round the axis by a (onto the square), along it by b (-1..1). */
    private static Vec3 onSphere(float a, float b, float z0, float r) {
        float ca = Mth.cos(a), sa = Mth.sin(a), m = Math.max(Math.abs(ca), Math.abs(sa));
        return new Vec3(ca / m * r * 1.02f, sa / m * r * 1.02f, z0 + Mth.clamp(b, -.95f, .95f) * r * 1.08f);
    }
    /** A point on a square bar (half-width r) from z0 to z1. */
    private static Vec3 onShaft(float a, float b, float z0, float z1, float r) {
        float q = Mth.clamp((b + 1) / 2, 0, 1);
        float ca = Mth.cos(a), sa = Mth.sin(a), m = Math.max(Math.abs(ca), Math.abs(sa));
        return new Vec3(ca / m * r * 1.04f, sa / m * r * 1.04f, Mth.lerp(q, z0, z1));
    }
}
