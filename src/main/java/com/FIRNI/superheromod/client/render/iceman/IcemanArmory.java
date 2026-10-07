package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.iceman.IceMesh.Ctx;
import com.FIRNI.superheromod.client.render.iceman.IceMesh.Mat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.client.render.iceman.IceMesh.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The Ice Armory's three weapons, grown of Iceman's own ice (IceMesh, IceGrowth): never boxes or clean points but
 * fused crystals and broken faceted pieces, blue ice with milky frosted regions, white frost only on edges and tips,
 * every piece a little uneven (seeded, so the same each frame). In the hand's frame: pixels, the grip at the origin
 * (inside his fist), the long axis toward -z, a blade's width along y.
 * <ul>
 * <li>MACE: a heavy irregular head (a faceted lump of glacier ice with thick stubby crystals standing out of it on every
 * side and a long crystal off its end), a ring of small crystals where it meets the handle, the handle a crooked faceted
 * bar with milky frost knots, a pommel of three crystals. Held and grown it gains new layers of crystals over the head
 * one after another (each its own delay), the head itself thickening, cracks of inner pressure glowing in it near full
 * size; it ends as big as before (about three times his height).</li>
 * <li>SPEAR: a long crooked shaft of fused crystal (frost knots, a few tiny crystals sticking out), a frosted collar with
 * swept-back points, a sharp faceted blade (a diamond section, uneven edges, its point off the middle) flanked by two
 * barbs; drawn back for a throw, its point grows longer and new crystals grow along its edges.</li>
 * <li>SWORD: a solid crystalline blade (a six-sided section: a ridge down each face, sharp uneven edges with notches, white
 * frost along them, flat layers of milky crystal grown on its faces), a crossguard of two crystals out of a frosted lump,
 * a faceted grip with frost bands, a pommel crystal.</li>
 * </ul>
 * Forming (form 0..1) is each weapon's own growth: the mace's crystals gather round the palm, thick branches grow out to
 * where the head swells and the handle grows down into the fist; the spear's thin line of crystal shoots out, the shaft
 * thickens along it, sharp pieces converge into its head; the sword's frost spreads round the hand, a thin crystal
 * grows out, flat layers grow along it and the blade fills into a solid crystal. Cracking (crack 0..1): cracks spreading
 * over it; 2 and over: the mace's head (or the blade) has broken away and only a jagged stump is left in the fist.
 */
public final class IcemanArmory {
    private IcemanArmory() {}

    /** Plain sizes (pixels): the mace's head centre, its half-size, its tip; the spear's blade base; the sword's tip. */
    static final float MACE_HEAD = 14.5f, MACE_R = 3.2f, MACE_TIP = 20.5f, MACE_POMMEL = 5.0f, SPEAR_BLADE = 17f, SPEAR_BUTT = 8.5f,
            SWORD_TIP = 21f, SWORD_POMMEL = 4.2f;
    /**
     * Turned in the hand (radians about the hand's x, set per draw by whoever draws it, 0 after): PI = held point down
     * the other way round (the sword turned to be driven into the ground).
     */
    public static float TURN;

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

    // ------------------------------------------------------------------ growth helpers
    private static float form;
    /** A piece's own growth inside the forming (nothing before a, grown by b), eased; 1 once formed. */
    private static float g(float a, float b) {
        if (form >= 1) return 1;
        return IceGrowth.grow(form, a, Math.max(.01f, b - a));
    }
    private static float r01(int seed, int salt) { return IceGrowth.h(seed, salt); }

    /**
     * Draws the weapon in the hand's frame (pixels; the grip at the origin, the long axis toward -z), formed (0..1), its
     * size (1 plain), cracked (0..1; 2 and over: the head / blade gone). After any change of the pose stack call c.at(p).
     */
    public static void inHand(IceMesh.Ctx c, PoseStack p, int weapon, float form, float size, float crack, float time) {
        if (form <= .001f) return;
        IcemanArmory.form = Mth.clamp(form, 0, 1);
        float flash = c.flash, ox = c.ox, oy = c.oy, oz = c.oz;
        boolean turned = Math.abs(TURN) > 1e-4f;
        if (turned) { p.pushPose(); p.mulPose(Axis.XP.rotation(TURN)); c.at(p); }
        // The texture belongs to the weapon (its own frame, pixel for pixel).
        c.ox = c.oy = c.oz = 0;
        if (form < 1) c.flash = Math.max(flash, .12f * (1 - form));
        try {
            switch (weapon) {
                case W_MACE -> mace(c, size, crack, time);
                case W_SPEAR -> spear(c, size, crack, time);
                default -> sword(c, size, crack, time);
            }
            if (form < 1) sparkles(c, weapon, size, time);
        } finally {
            c.flash = flash;
            c.ox = ox; c.oy = oy; c.oz = oz;
            IcemanArmory.form = 1;
            // The turn is per draw: whoever wants it sets it again.
            TURN = 0;
            if (turned) { p.popPose(); c.at(p); }
        }
    }
    /** The cold glinting where the ice is still forming (a few sparkles, fading as it closes). */
    private static void sparkles(Ctx c, int weapon, float size, float time) {
        float k = Mth.sin(Mth.PI * Mth.clamp(form, 0, 1));
        if (k < .02f) return;
        float len = length(weapon, size);
        for (int i = 0; i < 6; i++) {
            float q = r01(weapon * 31 + i, 1);
            float z = -len * q * Mth.clamp(form * 1.3f, 0, 1);
            float a = r01(weapon * 31 + i, 2) * Mth.TWO_PI + time * .07f;
            float rr = 1.4f + 1.2f * r01(weapon * 31 + i, 3);
            float tw = .5f + .5f * Mth.sin(time * 1.9f + i * 2.3f);
            IceMesh.sparkle(c, new Vec3(Mth.cos(a) * rr, Mth.sin(a) * rr, z), .6f + .4f * q, .7f * k * tw);
        }
    }

    // ------------------------------------------------------------------ shapes (scratch: render thread only)
    private static final int MAXN = 8;
    private static final float[] RA = new float[MAXN * 3], RB = new float[MAXN * 3], FR = new float[6];
    /** Two unit vectors at right angles to the unit axis (dx, dy, dz) into FR, turned by twist. */
    private static void frame(float dx, float dy, float dz, float twist) {
        float hx = Math.abs(dy) < .9f ? 0 : 1, hy = Math.abs(dy) < .9f ? 1 : 0;
        float ux = -dz * hy, uy = dz * hx, uz = dx * hy - dy * hx;
        float ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        float cs = Mth.cos(twist), sn = Mth.sin(twist);
        FR[0] = ux * cs + vx * sn; FR[1] = uy * cs + vy * sn; FR[2] = uz * cs + vz * sn;
        FR[3] = vx * cs - ux * sn; FR[4] = vy * cs - uy * sn; FR[5] = vz * cs - uz * sn;
    }
    /** A ring of n corners round (x, y, z) in FR's plane, radius r, every corner its own (jitter), turned by phase. */
    private static void ring(float[] out, int n, float x, float y, float z, float r, float phase, int seed, int salt, float jitter) {
        for (int i = 0; i < n; i++) {
            float a = phase + Mth.TWO_PI * i / n + (r01(seed, salt + i * 3) - .5f) * .55f / n * Mth.TWO_PI;
            float rr = r * (1 - jitter * .5f + jitter * r01(seed, salt + i * 7 + 1));
            float cu = Mth.cos(a) * rr, sv = Mth.sin(a) * rr;
            out[i * 3] = x + FR[0] * cu + FR[3] * sv;
            out[i * 3 + 1] = y + FR[1] * cu + FR[4] * sv;
            out[i * 3 + 2] = z + FR[2] * cu + FR[5] * sv;
        }
    }
    /** The facets between two rings (n corners each), some of them frosted (alt, by seed). */
    private static void side(Ctx c, float[] a, float[] b, int n, Mat mat, Mat alt, float altShare, int seed, float alpha) {
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Mat m = alt != null && r01(seed, 300 + i) < altShare ? alt : mat;
            quad(c, a[i * 3], a[i * 3 + 1], a[i * 3 + 2], a[j * 3], a[j * 3 + 1], a[j * 3 + 2],
                    b[j * 3], b[j * 3 + 1], b[j * 3 + 2], b[i * 3], b[i * 3 + 1], b[i * 3 + 2], m, alpha);
        }
    }
    /** A ring closed to a point (px, py, pz): a broken, pointed end. */
    private static void point(Ctx c, float[] a, int n, float px, float py, float pz, Mat mat, Mat alt, int seed, float alpha) {
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            tri(c, a[i * 3], a[i * 3 + 1], a[i * 3 + 2], a[j * 3], a[j * 3 + 1], a[j * 3 + 2], px, py, pz, alt != null && r01(seed, 500 + i) < .45f ? alt : mat, alpha);
        }
    }
    /**
     * A crooked faceted bar from (x0, y0, z0) (half-width r0) to (x1, y1, z1) (r1): n uneven sides, the facets a little
     * twisted end to end, some frosted; the ends closed by shallow broken caps when asked.
     */
    private static void bar(Ctx c, float x0, float y0, float z0, float x1, float y1, float z1, float r0, float r1, int n, int seed, Mat mat, Mat alt,
                            float alpha, boolean cap0, boolean cap1) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, l = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-4f || alpha <= .01f || Math.max(r0, r1) < .01f) return;
        dx /= l; dy /= l; dz /= l;
        frame(dx, dy, dz, r01(seed, 1) * Mth.TWO_PI);
        float tw = (r01(seed, 2) - .5f) * .5f;
        ring(RA, n, x0, y0, z0, r0, 0, seed, 10, .3f);
        ring(RB, n, x1, y1, z1, r1, tw, seed, 40, .3f);
        side(c, RA, RB, n, mat, alt, .3f, seed, alpha);
        float cut = Math.min(r0, r1) * .45f;
        if (cap0) point(c, RA, n, x0 - dx * cut * (.5f + r01(seed, 3)), y0 - dy * cut, z0 - dz * cut * (.5f + r01(seed, 3)), mat, null, seed, alpha);
        if (cap1) point(c, RB, n, x1 + dx * cut * (.5f + r01(seed, 4)), y1 + dy * cut, z1 + dz * cut * (.5f + r01(seed, 4)), mat, null, seed, alpha);
    }
    /**
     * An irregular faceted lump of ice round (x, y, z), longest along the unit axis (half length hl), half width r: four
     * uneven rings closing to two broken points off the middle; n sides. For the mace's head, collars, pommels.
     */
    private static void lump(Ctx c, float x, float y, float z, float axx, float axy, float axz, float hl, float r, int n, int seed, Mat mat, Mat alt, float alpha) {
        if (r < .01f || alpha <= .01f) return;
        frame(axx, axy, axz, r01(seed, 1) * Mth.TWO_PI);
        float[] k = LUMP_AT, w = LUMP_W;
        float ph = 0;
        for (int i = 0; i < 4; i++) {
            float t = (k[i] + (r01(seed, 20 + i) - .5f) * .12f) * hl;
            float[] dst = (i & 1) == 0 ? RA : RB;
            ring(dst, n, x + axx * t, y + axy * t, z + axz * t, r * w[i] * (.9f + .2f * r01(seed, 30 + i)), ph, seed, 50 + i * 13, .38f);
            ph += (r01(seed, 40 + i) - .5f) * .4f;
            if (i == 0) {
                float e = hl * (.95f + .2f * r01(seed, 5)), ox = (r01(seed, 6) - .5f) * r * .5f, oy = (r01(seed, 7) - .5f) * r * .5f;
                point(c, RA, n, x - axx * e + FR[0] * ox + FR[3] * oy, y - axy * e + FR[1] * ox + FR[4] * oy, z - axz * e + FR[2] * ox + FR[5] * oy, mat, alt, seed + 1, alpha);
            } else if ((i & 1) == 1) side(c, RA, RB, n, mat, alt, .3f, seed + i, alpha);
            else side(c, RB, RA, n, mat, alt, .3f, seed + i, alpha);
        }
        float e = hl * (.95f + .2f * r01(seed, 8)), ox = (r01(seed, 9) - .5f) * r * .5f, oy = (r01(seed, 10) - .5f) * r * .5f;
        point(c, RB, n, x + axx * e + FR[0] * ox + FR[3] * oy, y + axy * e + FR[1] * ox + FR[4] * oy, z + axz * e + FR[2] * ox + FR[5] * oy, mat, alt, seed + 2, alpha);
    }
    private static final float[] LUMP_AT = {-.62f, -.2f, .28f, .66f}, LUMP_W = {.62f, .98f, 1f, .58f};
    /** One crystal (IceGrowth: uneven, leaning, broken top) from a base along a direction, grown gr. */
    private static void crystal(Ctx c, float bx, float by, float bz, float dx, float dy, float dz, float len, float r, int seed, Mat mat, float gr) {
        IceGrowth.crystal(c, bx, by, bz, dx, dy, dz, len, r, seed, mat, gr, 1);
    }
    /** A small flake of frost lying flat on a surface whose outward normal is (nx, ny, nz) (unit): an imperfection, never a thorn. */
    private static final float[] CHIP = new float[9];
    private static void flake(Ctx c, float x, float y, float z, float nx, float ny, float nz, float size, int seed, Mat mat) {
        frame(nx, ny, nz, r01(seed, 3) * Mth.TWO_PI);
        // Its length along the surface, its thin side along the normal; sunk half into it.
        CHIP[0] = FR[0]; CHIP[1] = FR[1]; CHIP[2] = FR[2];
        CHIP[3] = FR[3]; CHIP[4] = FR[4]; CHIP[5] = FR[5];
        CHIP[6] = nx; CHIP[7] = ny; CHIP[8] = nz;
        IceGrowth.chip(c, x, y, z, CHIP, size, seed, mat, 1);
    }

    // ------------------------------------------------------------------ the mace
    private static void mace(Ctx c, float s, float crack, float time) {
        float k = Mth.clamp((s - 1) / (MACE_MAX - 1), 0, 1);
        float core = MACE_R * (1 + .45f * (s - 1));
        float headZ = -MACE_HEAD * s, sh = 1 + .4f * (s - 1);
        float collarZ = headZ + core * 1.02f;
        boolean headGone = crack >= 2;
        // ---- the first frost: small crystals round the palm (they stay, frosted, round the grip)
        for (int i = 0; i < 5; i++) {
            float gi = g(.0f + .05f * i, .22f + .05f * i);
            if (gi <= 0) continue;
            float a = i * 1.26f + r01(i, 71) * .6f, rr = 1.75f;
            float keep = form < 1 ? 1 - .45f * Mth.clamp((form - .7f) / .3f, 0, 1) : .55f;
            crystal(c, Mth.cos(a) * rr, Mth.sin(a) * rr, (r01(i, 72) - .5f) * 2.2f, Mth.cos(a), Mth.sin(a), (r01(i, 73) - .5f) * .8f,
                    (.9f + .5f * r01(i, 74)) * keep, .32f * keep, 9100 + i, i % 2 == 0 ? FROST : MILKY, gi);
        }
        // ---- the palm's mass swelling (it becomes the grip)
        float gm = g(.08f, .35f);
        if (gm > 0 && form < 1) lump(c, 0, 0, -.6f, 0, 0, -1, 1.5f * gm, 1.05f * gm, 6, 9150, CLEAR, MILKY, 1);
        // ---- the branches: thick crystals growing out of the palm toward where the head swells, sinking into the handle as it forms
        if (form < 1) {
            float fade = 1 - Mth.clamp((form - .78f) / .2f, 0, 1);
            for (int i = 0; i < 3; i++) {
                float gb = g(.18f + .05f * i, .55f + .04f * i);
                if (gb <= 0 || fade <= 0) continue;
                float a = i * 2.09f + .4f, off = .7f;
                float bx = Mth.cos(a) * off, by = Mth.sin(a) * off, len = -headZ - 1;
                float ex = Mth.cos(a + .5f) * core * .35f, ey = Mth.sin(a + .5f) * core * .35f;
                crystal(c, bx, by, -1.2f, ex - bx, ey - by, -len, len, (.55f + .2f * r01(i, 81)) * sh * fade, 9200 + i, i == 1 ? GLACIER : CLEAR, gb);
            }
        }
        // ---- the handle: grows down from the head into his fist, crooked, frost knots
        float gh = g(.55f, .92f);
        float pomZ = (MACE_POMMEL - 1.3f) * s, top = headGone ? collarZ * .72f : collarZ;
        if (gh > 0) {
            float bottom = Mth.lerp(gh, top, pomZ);
            int segs = 3;
            float px = 0, py = 0, pz = top;
            for (int i = 1; i <= segs; i++) {
                float z = Mth.lerp(i / (float) segs, top, bottom);
                float nx = i == segs ? 0 : (r01(i, 91) - .5f) * .3f * sh, ny = i == segs ? 0 : (r01(i, 92) - .5f) * .3f * sh;
                bar(c, px, py, pz, nx, ny, z, .82f * sh, .8f * sh, 6, 9300 + i, i == 2 ? GLACIER : CLEAR, MILKY, 1, i == 1, i == segs);
                if (i < segs && gh > .3f) {
                    // A knot of milky frost round the joint, a tiny crystal out of it.
                    bar(c, nx, ny, z + .55f * sh, nx, ny, z - .55f * sh, 1.12f * sh, 1.02f * sh, 6, 9320 + i, MILKY, FROST, 1, true, true);
                    float a = r01(i, 93) * Mth.TWO_PI;
                    crystal(c, nx + Mth.cos(a) * .9f * sh, ny + Mth.sin(a) * .9f * sh, z, Mth.cos(a), Mth.sin(a), -.6f, 1.2f * sh, .28f * sh, 9330 + i, CLEAR, 1);
                }
                px = nx; py = ny; pz = z;
            }
        }
        // ---- the pommel: three crystals out behind the fist
        float gp = g(.8f, 1f);
        if (gp > 0) {
            float z = (MACE_POMMEL - 1.6f) * s;
            lump(c, 0, 0, z, 0, 0, 1, 1.1f * sh, 1.05f * sh, 6, 9400, MILKY, CLEAR, gp);
            crystal(c, 0, 0, z + .6f * sh, .1f, .05f, 1, 2.1f * sh, .62f * sh, 9401, CLEAR, gp);
            crystal(c, .5f * sh, -.3f * sh, z + .3f * sh, .8f, -.4f, .7f, 1.3f * sh, .38f * sh, 9402, GLACIER, gp);
            crystal(c, -.5f * sh, .4f * sh, z + .2f * sh, -.7f, .6f, .6f, 1.0f * sh, .3f * sh, 9403, CLEAR, gp);
        }
        if (headGone) {
            // The jagged stump left in the fist: broken crystals, the clean bright inside showing.
            for (int i = 0; i < 4; i++) {
                float a = i * 1.7f + .4f;
                crystal(c, Mth.cos(a) * .45f * sh, Mth.sin(a) * .45f * sh, top + .3f * sh, Mth.cos(a) * .35f, Mth.sin(a) * .35f, -1,
                        (i % 2 == 0 ? 1.3f : 1.9f) * sh, .42f * sh, 9450 + i, FRESH, 1);
            }
            cracksShaft(c, MACE_CRACKS_HANDLE, 1, sh, pomZ, top, .82f * sh, false);
            return;
        }
        // ---- the head: a heavy faceted lump, thick crystals standing out of it, the long crystal off its end
        float gl = g(.42f, .78f);
        if (gl > 0) lump(c, 0, 0, headZ, 0, 0, -1, core * 1.05f * (.35f + .65f * gl), core * (.3f + .7f * gl), 7, 9500, GLACIER, MILKY, 1);
        for (int i = 0; i < 6; i++) {
            float gi = g(.36f + .06f * i, .7f + .04f * i);
            if (gi <= 0) continue;
            // Round the head (a crooked ring of four big ones and two smaller between), leaning forward a little.
            float a = (i < 4 ? i * Mth.HALF_PI : i * Mth.HALF_PI * 1.5f + .8f) + (r01(i, 101) - .5f) * .5f;
            boolean big = i < 4;
            float fz = -.15f - .35f * r01(i, 102);
            float dx = Mth.cos(a), dy = Mth.sin(a);
            float len = (big ? 1.0f : .65f) * core + (big ? 1.6f : 1.0f) * (float) Math.pow(s, .6f);
            float r = (big ? .44f : .32f) * core;
            crystal(c, dx * core * .35f, dy * core * .35f, headZ + (r01(i, 103) - .5f) * core * .6f, dx, dy, fz, len, r, 9510 + i,
                    i == 2 ? MILKY : i == 5 ? GLACIER : CLEAR, gi);
        }
        // The long crystal off its end, two smaller beside it.
        float ge = g(.5f, .88f);
        if (ge > 0) {
            float baseZ = headZ - core * .55f, len = MACE_TIP * s - MACE_HEAD * s - core * .55f + core * .25f;
            crystal(c, 0, 0, baseZ, .04f, -.03f, -1, len, .5f * core, 9530, CLEAR, ge);
            crystal(c, core * .3f, core * .2f, baseZ + core * .2f, .5f, .35f, -1, len * .5f, .3f * core, 9531, GLACIER, g(.6f, .95f));
            crystal(c, -core * .32f, -core * .1f, baseZ + core * .25f, -.6f, -.2f, -1, len * .38f, .26f * core, 9532, MILKY, g(.65f, 1f));
        }
        // The collar: a ring of small crystals swept back where the head meets the handle.
        float gc = g(.62f, .95f);
        if (gc > 0) for (int i = 0; i < 5; i++) {
            float a = i * 1.257f + r01(i, 111) * .5f;
            crystal(c, Mth.cos(a) * .7f * sh, Mth.sin(a) * .7f * sh, collarZ - .3f * sh, Mth.cos(a), Mth.sin(a), .75f + .3f * r01(i, 112),
                    (1.2f + .7f * r01(i, 113)) * sh, .36f * sh, 9540 + i, i % 2 == 0 ? CLEAR : MILKY, gc);
        }
        // White frost flakes caught on it here and there.
        if (gl > .8f) for (int i = 0; i < 5; i++) {
            float a = r01(i, 121) * Mth.TWO_PI, zz = headZ + (r01(i, 122) - .5f) * core;
            flake(c, Mth.cos(a) * core * .8f, Mth.sin(a) * core * .8f, zz, Mth.cos(a), Mth.sin(a), 0, (.8f + .5f * r01(i, 123)) * Math.min(2.5f, sh), 9560 + i, FROST);
        }
        // ---- grown: the new layers of crystals (each its own delay), the cracks of the pressure inside near the end
        if (k > 0) {
            for (int i = 1; i <= 6; i++) layer(c, headZ, core, s, i, k, time);
            if (k > .55f) pressure(c, headZ, core, s, k, time);
        }
        if (crack > 0) {
            cracksHead(c, MACE_CRACKS_HEAD, Math.min(1, crack), sh, headZ, core * (1.1f + .35f * k));
            cracksShaft(c, MACE_CRACKS_HANDLE, Math.min(1, crack) * .8f, sh, pomZ, collarZ, .82f * sh, false);
        }
    }
    /**
     * A layer of the growing mace (i 1..6): rime first (frost flakes), then its crystals pushing out of the head (each with
     * its own delay: some first, some lagging, long and short), the layer's own milky lump thickening the head.
     */
    private static void layer(Ctx c, float headZ, float core, float s, int i, float k, float time) {
        float start = (i - 1) / 6f * .86f + (r01(i, 131) - .5f) * .04f;
        float f = Mth.clamp((k - start) / .22f, 0, 1.5f);
        if (f <= 0) return;
        float outer = MACE_R * s * 1.6f, shell = core * (.55f + .1f * i);
        // The layer's lump: the head thickening one way or another.
        float gl = IceGrowth.grow(f, .1f, .6f);
        if (gl > 0) {
            float a = r01(i, 132) * Mth.TWO_PI, zz = (r01(i, 133) - .5f) * .8f;
            float dx = Mth.cos(a) * .6f, dy = Mth.sin(a) * .6f, dz = -.5f + zz, dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            lump(c, dx * shell * .4f, dy * shell * .4f, headZ + zz * shell * .5f, dx / dl, dy / dl, dz / dl, shell * .75f * gl, shell * .62f * gl, 6, 9600 + i * 7,
                    i % 2 == 0 ? GLACIER : CLEAR, MILKY, 1);
        }
        int n = 4 + (i & 1) + (i > 3 ? 1 : 0);
        for (int j = 0; j < n; j++) {
            int seed = 9700 + i * 17 + j;
            float a = r01(seed, 1) * Mth.TWO_PI, zz = (r01(seed, 2) - .65f) * 1.4f;
            float dx = Mth.cos(a), dy = Mth.sin(a);
            float len = (outer - shell * .5f) * (.45f + .55f * r01(seed, 3));
            float r = len * (.18f + .1f * r01(seed, 4));
            float gc = IceGrowth.grow(f, .05f + .45f * r01(seed, 5), .45f + .3f * r01(seed, 6));
            if (gc <= 0) continue;
            crystal(c, dx * shell * .5f, dy * shell * .5f, headZ + zz * shell * .4f, dx, dy, zz, len, r, seed, r01(seed, 7) < .25f ? MILKY : r01(seed, 7) < .45f ? GLACIER : CLEAR, gc);
        }
        // Rime before the ice: white flakes catching on where it will grow.
        float rime = Mth.clamp(f / .25f, 0, 1) * (1 - Mth.clamp((f - .4f) / .5f, 0, 1));
        if (rime > .02f) for (int j = 0; j < 3; j++) {
            int seed = 9800 + i * 11 + j;
            float a = r01(seed, 1) * Mth.TWO_PI;
            flake(c, Mth.cos(a) * shell, Mth.sin(a) * shell, headZ + (r01(seed, 2) - .5f) * shell, Mth.cos(a), Mth.sin(a), 0, 1.1f * rime * Math.min(2.5f, 1 + .3f * s), seed, FROST);
        }
        if (f < 1) for (int j = 0; j < 2; j++) {
            float a = r01(i, 140 + j) * Mth.TWO_PI + time * .05f;
            IceMesh.sparkle(c, new Vec3(Mth.cos(a) * shell, Mth.sin(a) * shell, headZ), .9f + .3f * s, .8f * (1 - Mth.clamp(f, 0, 1)));
        }
    }
    /** Cracks of the pressure inside the grown mace: thin cold lines running out from the heart of the head, pulsing. */
    private static void pressure(Ctx c, float headZ, float core, float s, float k, float time) {
        float kk = Mth.clamp((k - .55f) / .45f, 0, 1);
        for (int i = 0; i < 6; i++) {
            float open = Mth.clamp(kk * 1.6f - i * .12f, 0, 1);
            if (open <= 0) continue;
            int seed = 9900 + i;
            float a = r01(seed, 1) * Mth.TWO_PI, zz = (r01(seed, 2) - .5f) * .8f;
            float r0 = core * .7f, r1 = core * (1.05f + .3f * kk);
            float a2 = a + (r01(seed, 3) - .5f) * .5f;
            Vec3 p0 = new Vec3(Mth.cos(a) * r0, Mth.sin(a) * r0, headZ + zz * core * .5f);
            Vec3 p1 = new Vec3(Mth.cos(a2) * r1, Mth.sin(a2) * r1, headZ + zz * core * .9f);
            float pulse = .55f + .45f * Mth.sin(time * (.6f + .2f * i) + i * 1.7f);
            IceMesh.vein(c, p0, p0.lerp(p1, open), .09f * (1 + .25f * s), kk * pulse * .8f);
        }
    }

    // ------------------------------------------------------------------ the spear
    private static final float[] SA = new float[12], SB = new float[12];
    private static void spear(Ctx c, float size, float crack, float time) {
        float kd = Mth.clamp((size - 1) / .6f, 0, 1);
        float tip = -SPEAR_BLADE - 8 * size;
        // ---- the thin line of crystal that shoots out first (it thickens into the shaft)
        float gLine = g(0, .3f), gThick = g(.22f, .62f);
        if (form < 1 && gLine > 0) {
            float rr = .22f + .1f * gThick;
            bar(c, 0, 0, Mth.lerp(gLine, 0, SPEAR_BUTT - .4f), 0, 0, Mth.lerp(gLine, -1, tip * .92f), rr, rr * .8f, 5, 9001, FRESH, null, .9f, true, true);
        }
        // ---- the shaft: four crooked segments of fused crystal, frost knots at two joints
        float z0 = SPEAR_BUTT - .4f, z1 = -SPEAR_BLADE + .5f;
        float px = 0, py = 0, pz = z0;
        for (int i = 1; i <= 5; i++) {
            float z = Mth.lerp(i / 5f, z0, z1);
            float gi = form >= 1 ? 1 : IceGrowth.grow(form, .2f + .06f * Math.abs(3 - i), .3f);
            float nx = i == 5 ? 0 : (r01(i, 201) - .5f) * .34f, ny = i == 5 ? 0 : (r01(i, 202) - .5f) * .34f;
            if (gi > 0) {
                float r = (.5f + .06f * r01(i, 204)) * (.4f + .6f * gi);
                bar(c, px, py, pz, nx, ny, z, r, r * .92f, 5, 9010 + i, i == 3 ? GLACIER : CLEAR, MILKY, 1, i == 1, false);
                if ((i == 1 || i == 3) && gi > .6f) {
                    // A frost knot: a milky collar, a tiny crystal out of it.
                    bar(c, nx, ny, z + .55f, nx, ny, z - .45f, .8f, .68f, 5, 9020 + i, MILKY, FROST, 1, true, true);
                    float a = r01(i, 203) * Mth.TWO_PI;
                    crystal(c, nx + Mth.cos(a) * .6f, ny + Mth.sin(a) * .6f, z, Mth.cos(a), Mth.sin(a), -.9f, 1.2f, .24f, 9030 + i, CLEAR, gi);
                }
                if (i == 2 || i == 4) {
                    float a = r01(i, 205) * Mth.TWO_PI;
                    flake(c, nx + Mth.cos(a) * .45f, ny + Mth.sin(a) * .45f, z + 1.2f, Mth.cos(a), Mth.sin(a), 0, .8f * gi, 9035 + i, FROST);
                }
            }
            px = nx; py = ny; pz = z;
        }
        // ---- the butt: a crystal out behind
        float gb = g(.6f, .9f);
        if (gb > 0) {
            crystal(c, 0, 0, SPEAR_BUTT - 1.2f, .06f, -.05f, 1, 3.0f, .6f, 9040, CLEAR, gb);
            crystal(c, .3f, .2f, SPEAR_BUTT - .9f, .5f, .3f, 1, 1.4f, .32f, 9041, MILKY, gb);
        }
        if (crack >= 2) return;
        // ---- the collar: a frosted lump, small points swept back
        float gc = g(.55f, .85f);
        if (gc > 0) {
            lump(c, 0, 0, -SPEAR_BLADE + .3f, 0, 0, -1, 1.0f * gc, .95f * gc, 6, 9050, MILKY, CLEAR, 1);
            for (int i = 0; i < 3; i++) {
                float a = i * 2.09f + .3f;
                crystal(c, Mth.cos(a) * .6f, Mth.sin(a) * .6f, -SPEAR_BLADE - .2f, Mth.cos(a) * .7f, Mth.sin(a) * .7f, 1, 1.5f + .3f * i, .3f, 9051 + i, CLEAR, gc);
            }
        }
        // ---- the head: sharp pieces converging into the blade, then the blade itself
        float za = -SPEAR_BLADE - .1f, reach = 8 * size;
        float gp = g(.45f, .82f), gB = g(.62f, 1f);
        if (form < 1 && gp > 0) {
            for (int i = 0; i < 4; i++) {
                float a = i * Mth.HALF_PI + .4f, off = 2.2f * (1 - gp);
                float bx = Mth.cos(a) * (.4f + off), by = Mth.sin(a) * (.4f + off);
                crystal(c, bx, by, za - reach * .1f * i, -bx * .25f, -by * .25f, -1, reach * (.55f + .1f * i) * (1 - .5f * gB), .55f, 9060 + i, i % 2 == 0 ? FRESH : CLEAR, gp);
            }
        }
        if (gB > 0) {
            float w = 2.1f * (1 + .25f * (size - 1)) * (.3f + .7f * gB), th = .42f * (.5f + .5f * gB);
            blade(c, za, za - reach * Math.min(1, gB * 1.1f + .1f), w, th, 9070, false);
            // The two barbs flanking its base.
            for (int sg = -1; sg <= 1; sg += 2)
                crystal(c, 0, sg * w * .45f, za + .2f, 0, sg * .8f, -1, 2.4f * gB, .42f, 9080 + sg, sg < 0 ? CLEAR : MILKY, gB);
            // Drawn back: new crystals growing along its edges toward the point (each its own time), frost glinting on it.
            if (kd > 0) for (int i = 0; i < 5; i++) {
                int seed = 9090 + i;
                float u = .25f + .14f * i, gx = IceGrowth.grow(kd, .08f + .14f * i + .05f * r01(seed, 1), .4f);
                if (gx <= 0) continue;
                float sg = i % 2 == 0 ? 1 : -1, z = za - reach * u;
                crystal(c, 0, sg * w * (.85f - .5f * u), z, (r01(seed, 2) - .5f) * .3f, sg * .55f, -1, (1.4f + 1.2f * r01(seed, 3)) * (1 - .4f * u), .32f, seed,
                        i % 3 == 0 ? MILKY : CLEAR, gx);
            }
            if (size > 1.01f) {
                Vec3 at = new Vec3(0, 0, za - reach);
                IceMesh.sparkle(c, at, 1.3f + .6f * Mth.sin(time * .8f), .55f * kd);
            }
        }
        if (crack > 0) cracksShaft(c, SPEAR_CRACKS, Math.min(1, crack), 1, z0, za - reach * .9f, .56f, false);
    }
    /**
     * A faceted blade along the axis from za to the point at zt: half width w (along y), half thickness th (along x), a
     * six-sided section (a flat down the middle of each face, bevels out to the two sharp edges). Every station along it
     * its own: the edges uneven (notches, a bite here and there), the stations not evenly spaced, the point off the
     * middle. Spear (sword false): a leaf, widest a third of the way, a narrow milky ridge, clear bevels. Sword: almost
     * straight to a stepped point, a wide flat, the bevels white with frost now and then.
     */
    private static void blade(Ctx c, float za, float zt, float w, float th, int seed, boolean sword) {
        int stations = sword ? 10 : 7;
        float len = za - zt;
        float[] prev = RA2, cur = RB2;
        for (int i = 0; i <= stations; i++) {
            float u = i == 0 || i == stations ? i / (float) stations : (i + (r01(seed, 5 + i) - .5f) * .5f) / stations;
            float z = za - len * u, wi;
            if (sword) wi = u < .74f ? 1 : u < .9f ? Mth.lerp((u - .74f) / .16f, 1, .58f) : Mth.lerp((u - .9f) / .1f, .58f, .22f);
            else wi = u < .32f ? Mth.lerp(u / .32f, .45f, 1) : u < .7f ? Mth.lerp((u - .32f) / .38f, 1, .6f) : Mth.lerp((u - .7f) / .3f, .6f, .18f);
            // Each edge its own: notches and bites.
            float wp = w * wi * (.88f + .24f * r01(seed, 10 + i)), wn = w * wi * (.88f + .24f * r01(seed, 30 + i));
            if (sword && i > 0 && i < stations - 1 && r01(seed, 50 + i) < .25f) wp *= .8f;
            float tt = th * (.9f + .2f * r01(seed, 70 + i)) * (1 - .45f * u * u);
            float ev = (sword ? .52f + .1f * r01(seed, 90 + i) : .14f + .06f * r01(seed, 90 + i));
            float off = (r01(seed, 110 + i) - .5f) * w * .08f;
            cur[0] = 0; cur[1] = wp + off; cur[2] = z;
            cur[3] = tt; cur[4] = wp * ev + off; cur[5] = z;
            cur[6] = tt; cur[7] = -wn * ev + off; cur[8] = z;
            cur[9] = 0; cur[10] = -wn + off; cur[11] = z;
            cur[12] = -tt; cur[13] = -wn * ev + off; cur[14] = z;
            cur[15] = -tt; cur[16] = wp * ev + off; cur[17] = z;
            if (i == 0) {
                quad(c, cur[0], cur[1], cur[2], cur[3], cur[4], cur[5], cur[6], cur[7], cur[8], cur[9], cur[10], cur[11], MILKY, 1);
                quad(c, cur[0], cur[1], cur[2], cur[9], cur[10], cur[11], cur[12], cur[13], cur[14], cur[15], cur[16], cur[17], MILKY, 1);
            } else {
                for (int k = 0; k < 6; k++) {
                    int j = (k + 1) % 6;
                    boolean bevel = k == 0 || k == 2 || k == 3 || k == 5;
                    int hs = seed + i * 7 + k;
                    Mat m = sword ? (bevel ? (r01(hs, 1) < .4f ? FROST : r01(hs, 2) < .5f ? MILKY : CLEAR) : r01(hs, 3) < .3f ? GLACIER : CLEAR)
                            : (bevel ? (r01(hs, 4) < .25f ? GLACIER : CLEAR) : MILKY);
                    quad(c, prev[k * 3], prev[k * 3 + 1], prev[k * 3 + 2], prev[j * 3], prev[j * 3 + 1], prev[j * 3 + 2],
                            cur[j * 3], cur[j * 3 + 1], cur[j * 3 + 2], cur[k * 3], cur[k * 3 + 1], cur[k * 3 + 2], m, 1);
                }
            }
            float[] t = prev; prev = cur; cur = t;
        }
        // The point: off the middle a little.
        point(c, prev, 6, 0, (r01(seed, 99) - .5f) * w * .3f, zt, CLEAR, sword ? FROST : MILKY, seed, 1);
        // A few small crystals grown out of the edges, swept toward the point (the sword's serrations, the spear's teeth).
        int teeth = sword ? 4 : 2;
        for (int i = 0; i < teeth; i++) {
            int ts = seed + 200 + i;
            float u = (sword ? .12f : .2f) + (sword ? .16f : .2f) * i + .05f * r01(ts, 1), sg = (i & 1) == 0 ? 1 : -1;
            float wi = sword ? 1 : u < .32f ? Mth.lerp(u / .32f, .45f, 1) : Mth.lerp((u - .32f) / .38f, 1, .6f);
            crystal(c, 0, sg * w * wi * .85f, za - len * u, (r01(ts, 2) - .5f) * .2f, sg * .7f, -1, (sword ? .9f : 1.1f) * (.7f + .6f * r01(ts, 3)), .2f, ts,
                    r01(ts, 4) < .5f ? FRESH : CLEAR, 1);
        }
    }
    private static final float[] RA2 = new float[18], RB2 = new float[18];

    // ------------------------------------------------------------------ the sword
    private static void sword(Ctx c, float size, float crack, float time) {
        // ---- frost round the hand first
        float frost = form >= 1 ? 0 : Mth.clamp(form / .12f, 0, 1) * (1 - Mth.clamp((form - .5f) / .3f, 0, 1));
        if (frost > .02f) for (int i = 0; i < 6; i++) {
            float a = i * 1.05f + r01(i, 301);
            flake(c, Mth.cos(a) * 2.0f, Mth.sin(a) * 2.0f, (r01(i, 302) - .5f) * 3, Mth.cos(a), Mth.sin(a), 0, 1.3f * frost, 9200 + i * 3, FROST);
        }
        // ---- the grip with frost bands, the pommel
        float gg = g(.18f, .55f);
        if (gg > 0) {
            float zA = SWORD_POMMEL - 1.0f, zB = -1.8f;
            bar(c, 0, 0, Mth.lerp(gg, 0, zA), 0, 0, Mth.lerp(gg, -.5f, zB), .6f, .58f, 6, 9210, CLEAR, MILKY, 1, false, false);
            if (gg > .5f) {
                bar(c, 0, 0, 1.6f, 0, 0, .6f, .72f, .7f, 6, 9211, MILKY, FROST, 1, true, true);
                bar(c, 0, 0, -.5f, 0, 0, -1.4f, .7f, .68f, 6, 9212, MILKY, null, 1, true, true);
            }
        }
        float gp = g(.45f, .75f);
        if (gp > 0) {
            lump(c, 0, 0, SWORD_POMMEL, 0, 0, 1, .95f * gp, .9f * gp, 6, 9220, MILKY, CLEAR, 1);
            crystal(c, 0, 0, SWORD_POMMEL + .4f, .1f, .08f, 1, 1.6f, .45f, 9221, CLEAR, gp);
        }
        // ---- the crossguard: two crystals out of a frosted lump
        float gq = g(.3f, .7f);
        if (gq > 0) {
            lump(c, 0, 0, -2.4f, 0, 1, 0, 1.4f * gq, .95f * gq, 6, 9230, MILKY, CLEAR, 1);
            for (int sg = -1; sg <= 1; sg += 2) {
                crystal(c, 0, sg * .8f, -2.4f, (r01(sg + 2, 311) - .5f) * .2f, sg, -.28f, 3.4f, .58f, 9231 + sg, CLEAR, gq);
                crystal(c, .2f, sg * 1.6f, -2.2f, .2f, sg * .6f, .5f, 1.3f, .3f, 9234 + sg, GLACIER, gq);
            }
        }
        if (crack >= 2) return;
        float za = -2.9f, len = (SWORD_TIP - 2.9f) * size, zt = za - len;
        // ---- the thin crystal growing out first (the blade's core)
        float gCore = g(.1f, .45f);
        if (form < 1 && gCore > 0) {
            float zEnd = Mth.lerp(gCore, za, zt);
            bar(c, 0, 0, za, 0, 0, zEnd, .2f, .14f, 5, 9240, FRESH, null, .95f, false, true);
        }
        // ---- the flat layers growing along it (on both faces), staggered
        float gLay = g(.32f, .85f);
        if (gLay > 0) {
            for (int i = 0; i < 6; i++) {
                int seed = 9250 + i;
                float u0 = .05f + .13f * i, u1 = u0 + .2f + .06f * r01(seed, 1);
                float gi = form >= 1 ? 1 : IceGrowth.grow(gLay, .1f * i, .5f);
                if (gi <= 0) continue;
                float sx = (i & 1) == 0 ? 1 : -1, w = 1.25f * (.85f + .25f * r01(seed, 2)) * (u1 > .8f ? .6f : 1);
                float zz0 = za - len * u0, zz1 = za - len * Math.min(.93f, u0 + (u1 - u0) * gi);
                plate(c, sx * .42f, zz0, zz1, w * (.3f + .7f * gi), .13f, seed, i % 3 == 0 ? FROST : MILKY);
            }
        }
        // ---- the blade: a solid irregular crystal filling out
        float gB = g(.58f, 1f);
        if (gB > 0) {
            float w = 1.45f * (.3f + .7f * gB), th = .32f * (.5f + .5f * gB);
            blade(c, za, Mth.lerp(Math.min(1, .55f + gB * .5f), za, zt), w, th, 9270, true);
            // Imperfections: a few frost flakes caught on its faces.
            if (gB > .7f) for (int i = 0; i < 4; i++) {
                int seed = 9280 + i;
                float u = .15f + .2f * i;
                float fx = (i & 1) == 0 ? 1 : -1;
                flake(c, fx * th * .9f, (r01(seed, 1) - .5f) * w * .7f, za - len * u, fx, 0, 0, .7f + .4f * r01(seed, 2), seed, FROST);
            }
            // The glint: a bright point sliding down the edge now and then.
            float k = Math.max(0, gB * 1.1f - .1f);
            float gl = (time * .045f) % 1.6f;
            if (k > .5f && gl < 1) IceMesh.sparkle(c, new Vec3(0, w * (gl < .72f ? 1 : 1 - (gl - .72f) * 2), za - gl * len), 1.4f, Mth.sin(Mth.PI * gl) * k * .7f);
        }
        if (crack > 0) cracksShaft(c, SWORD_CRACKS, Math.min(1, crack), 1, -3.2f, za - len * .95f, 1.1f, true);
    }
    /** A flat layer of crystal lying on a blade's face (at x) from z0 to z1, half width w, thickness t: a thin uneven slab. */
    private static void plate(Ctx c, float x, float z0, float z1, float w, float t, int seed, Mat mat) {
        if (Math.abs(z1 - z0) < .05f || w < .02f) return;
        float sx = Math.signum(x), x0 = x, x1 = x + sx * t;
        float wa = w * (.7f + .3f * r01(seed, 3)), wb = w * (.5f + .4f * r01(seed, 4)), oy = (r01(seed, 5) - .5f) * w * .3f;
        // Four corners at each end, the far end narrower and pointed off the middle (a grown sheet, not a box).
        float[] a = SA, b = SB;
        a[0] = x0; a[1] = oy + wa; a[2] = z0;   a[3] = x1; a[4] = oy + wa * .8f; a[5] = z0;
        a[6] = x1; a[7] = oy - wa * .8f; a[8] = z0;   a[9] = x0; a[10] = oy - wa; a[11] = z0;
        b[0] = x0; b[1] = oy + wb; b[2] = z1;   b[3] = x1; b[4] = oy + wb * .7f; b[5] = z1;
        b[6] = x1; b[7] = oy - wb * .7f; b[8] = z1;   b[9] = x0; b[10] = oy - wb; b[11] = z1;
        side(c, a, b, 4, mat, CLEAR, .25f, seed, 1);
        point(c, b, 4, x0 + sx * t * .5f, oy, z1 - Math.signum(z1 - z0) * w * .8f, mat, null, seed, 1);
        point(c, a, 4, x0 + sx * t * .5f, oy, z0 + Math.signum(z1 - z0) * w * .3f, mat, null, seed + 1, 1);
    }

    // ------------------------------------------------------------------ cracks
    /**
     * Crack lines, generated once per shape: each a list of points {a, b} on the part's surface (head: a = angle round
     * the axis, b = -1..1 along it; shaft/blade: a = angle, b = 0..1 from its start to its end) with the order they open in.
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
    private static void cracksShaft(Ctx c, float[][] set, float k, float s, float z0, float z1, float r, boolean flat) { cracks(c, set, k, s, z0, z1, r, flat ? 2 : 0); }
    /** A crack set spreading over the mace's head (centre z0, radius r). */
    private static void cracksHead(Ctx c, float[][] set, float k, float s, float z0, float r) { cracks(c, set, k, s, z0, 0, r, 1); }
    private static void cracks(Ctx c, float[][] set, float k, float s, float z0, float z1, float r, int mode) {
        if (k <= .01f) return;
        float w = .1f * Math.max(1, s * .6f);
        for (float[] pts : set) {
            Vec3 prev = null;
            for (int i = 0; i < pts.length / 3; i++) {
                float open = Mth.clamp((k - pts[i * 3 + 2]) / .12f, 0, 1);
                float a = pts[i * 3], b = pts[i * 3 + 1];
                Vec3 p = mode == 1 ? onHead(a, b, z0, r) : mode == 2 ? onBlade(a, b, z0, z1, r) : onShaft(a, b, z0, z1, r);
                if (prev != null && open > 0) IceMesh.vein(c, prev, prev.lerp(p, open), w, .55f + .45f * k);
                if (open <= 0) break;
                prev = p;
            }
        }
    }
    private static Vec3 onBlade(float a, float b, float z0, float z1, float r) {
        float q = Mth.clamp((b + 1) / 2, 0, 1);
        return new Vec3(Mth.cos(a) >= 0 ? .46f : -.46f, Mth.sin(a) * r * (1 - .6f * q * q), Mth.lerp(q, z0, z1));
    }
    /** A point on the head (about a ball of radius r round z0): round the axis by a, along it by b (-1..1). */
    private static Vec3 onHead(float a, float b, float z0, float r) {
        float bb = Mth.clamp(b, -.95f, .95f), rr = r * Mth.sqrt(1 - bb * bb * .6f);
        return new Vec3(Mth.cos(a) * rr, Mth.sin(a) * rr, z0 + bb * r);
    }
    /** A point on a bar (about radius r) from z0 to z1. */
    private static Vec3 onShaft(float a, float b, float z0, float z1, float r) {
        float q = Mth.clamp((b + 1) / 2, 0, 1);
        return new Vec3(Mth.cos(a) * r * 1.08f, Mth.sin(a) * r * 1.08f, Mth.lerp(q, z0, z1));
    }
}
