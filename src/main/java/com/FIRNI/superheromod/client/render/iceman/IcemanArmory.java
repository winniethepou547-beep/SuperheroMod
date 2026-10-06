package com.FIRNI.superheromod.client.render.iceman;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.FIRNI.superheromod.client.render.iceman.IceMesh.*;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The Ice Armory's three weapons, all of Iceman's own ice (IceMesh), in the hand's frame: pixels, the grip at the origin
 * (inside his fist), the long axis toward -z, the blades' width along y.
 * <ul>
 * <li>MACE: a heavy crystalline head (a milky core inside clear outer facets, six flanged crystal blades round it, spikes
 * between them and a long one on top, bright rims on every flange), a thick ridged handle, a collar of crystals where
 * the head sits, a pommel crystal. Held and grown (size 1..MACE_MAX) it is the same mace scaled up with new uneven
 * shells of glacier ice accreting over the head, one layer after another, each with new spikes (frost first, then
 * crystals, then thick ice), until it is about three times his height.</li>
 * <li>SPEAR: a long faceted shaft (about 1.6 blocks) with a milky core, a crystal collar, a leaf-shaped faceted blade
 * with a milky midrib and glinting edges, a butt spike. Drawn back for a throw (size above 1) its blade grows longer.</li>
 * <li>SWORD: a broad faceted blade with a ridge (a milky line down its middle, clear edges catching glints that run along
 * them), a crystal crossguard, a ridged grip, a pommel crystal.</li>
 * </ul>
 * Forming (form 0..1) follows the ice language: a breath of frost and sparkles along the weapon's shape first, then small
 * crystal nuclei along it, then the real parts growing out of the grip along the axis and thickening (never popping in
 * at full size). Cracking (crack 0..1): bright crack lines spreading over it; 2 and over: the mace's head (or the
 * blade) has broken away and only the handle is left in the fist.
 */
public final class IcemanArmory {
    private IcemanArmory() {}

    /** Plain sizes (pixels): the mace's head centre, its radius, its tip; the spear's blade base; the sword's tip. */
    static final float MACE_HEAD = 14.5f, MACE_R = 3.2f, MACE_TIP = 20.5f, MACE_POMMEL = 5.0f, SPEAR_BLADE = 17f, SPEAR_BUTT = 8.5f,
            SWORD_TIP = 21f, SWORD_POMMEL = 4.2f;
    private static final Vec3 BACK = new Vec3(0, 0, 1), AHEAD = new Vec3(0, 0, -1);

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
        float flash = c.flash;
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
            IcemanArmory.form = 1;
        }
    }

    /** The first stages: frost and sparkles along the shape, crystal nuclei that the real ice then swallows. */
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
            // A nucleus: a small crystal that grows, then is swallowed by the part growing over it.
            float nk = Mth.clamp((form - .12f - Math.abs(u) * .3f) / .25f, 0, 1) * (1 - grown(u));
            if (nk > .01f) {
                Vec3 out = new Vec3(Math.cos(a), Math.sin(a), (hash(i * 9.1) - .5) * 1.4).normalize();
                IceMesh.crystal(c, at.scale(.6), out, 1.6f * nk * (.7f + .6f * (float) hash(i * 2.9)), .32f * nk + .05f, 4, 300 + i, FROST, .9f, .6f);
            }
        }
        // A thin frosted sheath along the shape before the ice thickens inside it.
        if (frost > .02f) {
            float gz = -len * Mth.clamp(form / .35f, 0, 1);
            IceMesh.limb(c, new Vec3(0, 0, Math.min(back, 2)), new Vec3(0, 0, gz), radiusAt(weapon, 0, size) * 1.1f + .2f,
                    radiusAt(weapon, -gz, size) * .9f + .2f, 6, 330 + weapon, FROST.alpha(.35f * frost), null, 1);
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

    // ------------------------------------------------------------------ the mace
    private static void mace(IceMesh.Ctx c, float s, float crack, float time) {
        float headZ = -MACE_HEAD * s, r = MACE_R * s, handleTop = -(MACE_HEAD - MACE_R * .82f) * s;
        boolean headGone = crack >= 2;
        float total = MACE_TIP * s;
        // The pommel crystal, behind the fist.
        float gp = grown(-MACE_POMMEL * s / total * 1.2f);
        if (gp > 0) {
            IceMesh.crystal(c, new Vec3(0, 0, (MACE_POMMEL - .4f) * s), BACK, 2.6f * s * gp, .95f * s * thick(gp), 6, 41, MILKY, 1, .5f);
            IceMesh.limb(c, new Vec3(0, 0, (MACE_POMMEL - .3f) * s), new Vec3(0, 0, (MACE_POMMEL - 1.4f) * s), 1.15f * s * thick(gp), .9f * s * thick(gp), 7, 42, CLEAR, null, 1);
        }
        // The handle: ridged, a milky core inside clear ice; grows out of the fist both ways.
        int segs = 8;
        float z0 = (MACE_POMMEL - 1.2f) * s, z1 = headGone ? handleTop * .72f : handleTop;
        for (int i = 0; i < segs; i++) {
            float a = Mth.lerp(i / (float) segs, z0, z1), b = Mth.lerp((i + 1) / (float) segs, z0, z1);
            float g = grown(Math.max(Math.abs(a), Math.abs(b)) / total * (a > 0 ? 1.2f : 1));
            if (g <= 0) continue;
            float rr = (i % 2 == 0 ? .78f : .95f) * s * thick(g);
            float bb = a + (b - a) * Math.min(1, g * 1.3f);
            IceMesh.limb(c, new Vec3(0, 0, a), new Vec3(0, 0, bb), rr, (i % 2 == 0 ? .95f : .78f) * s * thick(g), 7, 50 + i, CLEAR, MILKY, 1);
        }
        if (headGone) {
            // The jagged stump left in the fist, the clean bright inside showing.
            for (int i = 0; i < 4; i++) {
                double a = i * 1.7 + .4;
                IceMesh.crystal(c, new Vec3(Math.cos(a) * .4 * s, Math.sin(a) * .4 * s, z1 + .3f * s), new Vec3(Math.cos(a) * .4, Math.sin(a) * .4, -1),
                        (i % 2 == 0 ? 1.4f : 1.9f) * s, .35f * s, 4, 60 + i, FRESH, 1, .8f);
            }
            cracksShaft(c, MACE_CRACKS_HANDLE, 1, s, z0, z1, .9f * s, false);
            return;
        }
        float gh = grown(MACE_HEAD * s / total);
        if (gh <= 0) return;
        // The collar where the head sits: a flared ring and short crystals.
        float gc = grown((MACE_HEAD - MACE_R) * s / total);
        if (gc > 0) {
            IceMesh.limb(c, new Vec3(0, 0, handleTop + 1.6f * s), new Vec3(0, 0, handleTop - .2f * s), .9f * s * thick(gc), 1.6f * s * thick(gc), 8, 70, CLEAR, MILKY, 1);
            for (int i = 0; i < 6; i++) {
                double a = i * Mth.TWO_PI / 6 + .5;
                IceMesh.crystal(c, new Vec3(Math.cos(a) * .9 * s, Math.sin(a) * .9 * s, handleTop + .8f * s), new Vec3(Math.cos(a), Math.sin(a), -1.4),
                        1.7f * s * gc, .36f * s, 4, 71 + i, CLEAR, 1, .6f);
            }
        }
        // The head grows from its middle outward: the core, the facets, the flanges, the spikes.
        float hr = r * Mth.clamp(gh * 1.25f, 0, 1);
        float hk = thick(gh);
        Vec3 centre = new Vec3(0, 0, headZ);
        ellipsoid(c, centre, hr * .62f, hr * .78f, 7, 81, CORE, 1);
        ellipsoid(c, centre, hr, hr * 1.12f, 9, 82, CLEAR, .95f);
        float gf = Mth.clamp((gh - .25f) / .75f, 0, 1);
        for (int i = 0; i < 6; i++) {
            double a = i * Mth.TWO_PI / 6;
            Vec3 out = new Vec3(Math.cos(a), Math.sin(a), 0);
            flange(c, centre, out, hr * .55f, hr + 2.6f * s * gf, 2.3f * s * hk, .42f * s * hk, gf);
            // A spike between two flanges, above and below the middle.
            double b = a + Mth.PI / 6;
            for (int k = -1; k <= 1; k += 2) {
                Vec3 d = new Vec3(Math.cos(b), Math.sin(b), k * .75).normalize();
                IceMesh.crystal(c, centre.add(d.scale(hr * .75f)), d, 2.3f * s * gf, .5f * s * hk, 5, 90 + i * 2 + (k + 1) / 2, CLEAR, 1, .7f);
            }
        }
        IceMesh.crystal(c, centre.add(0, 0, -hr * .8f), AHEAD, (MACE_TIP - MACE_HEAD - MACE_R * .8f) * s * gf + .01f, .75f * s * hk, 6, 99, MILKY, 1, .8f);
        // Grown: the new layers of ice accreting over the head, each its own uneven shell with new spikes.
        float layers = (s - 1) / (MACE_MAX - 1) * 6;
        for (int i = 1; i <= 6 && layers > 0; i++) {
            float f = Mth.clamp(layers - (i - 1), 0, 1);
            if (f <= 0) break;
            layer(c, centre, r, i, f, s, time);
        }
        if (crack > 0) {
            cracksHead(c, MACE_CRACKS_HEAD, Math.min(1, crack), s, headZ, r * (1.04f + .07f * layers));
            cracksShaft(c, MACE_CRACKS_HANDLE, Math.min(1, crack) * .8f, s, z0, z1, .9f * s, false);
        }
    }
    /** An uneven faceted ellipsoid round the axis (radius r across, half-length h along z). */
    private static void ellipsoid(IceMesh.Ctx c, Vec3 centre, float r, float h, int sides, int seed, Mat mat, float alpha) {
        if (r < .02f) return;
        int rings = 6;
        float[] prev = null;
        for (int i = 0; i <= rings; i++) {
            float k = -1 + 2f * i / rings;
            float rr = r * Mth.sqrt(Math.max(.02f, 1 - k * k)), z = (float) centre.z + k * h;
            float[] ring = IceMesh.ring((float) centre.x, (float) centre.y, z, X, Y, rr, rr, sides, .16f, seed + i * 5, seed * .3f);
            if (prev != null) loft(c, prev, ring, mat, alpha, i == 1, i == rings);
            prev = ring;
        }
    }
    /** A flanged blade standing out of the head: a flat double-pointed crystal from inner to outer radius, its rims bright. */
    private static void flange(IceMesh.Ctx c, Vec3 centre, Vec3 out, float inner, float outer, float width, float thick, float g) {
        if (g <= .01f) return;
        Vec3 side = new Vec3(-out.y, out.x, 0);
        Vec3 base = centre.add(out.scale(inner)), tip = centre.add(out.scale(outer)), mid = centre.add(out.scale(Mth.lerp(.45f, inner, outer)));
        Vec3 fwd = mid.add(0, 0, -width), aft = mid.add(0, 0, width * .8f), l = mid.add(side.scale(thick)), rr = mid.subtract(side.scale(thick));
        Vec3[] ring = {fwd, l, aft, rr};
        for (int i = 0; i < 4; i++) {
            Vec3 a = ring[i], b = ring[(i + 1) % 4];
            tri(c, a, b, tip, CLEAR, 1);
            tri(c, b, a, base, MILKY, 1);
        }
        float k = g;
        IceMesh.line(c, fwd, tip, .14f, .55f * k, .8f * k, k);
        IceMesh.line(c, aft, tip, .12f, .45f * k, .7f * k, .9f * k);
    }
    private static void tri(IceMesh.Ctx c, Vec3 a, Vec3 b, Vec3 d, Mat mat, float alpha) {
        IceMesh.tri(c, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, (float) d.x, (float) d.y, (float) d.z, mat, alpha);
    }
    /**
     * One layer of the growing mace (i 1..6, f 0..1 how far it has formed): rime first (white, see-through), then the
     * crystals of the layer standing out of it, then the shell of glacier ice thickening over everything under it.
     */
    private static void layer(IceMesh.Ctx c, Vec3 centre, float r, int i, float f, float s, float time) {
        float shell = r * (1.02f + .07f * i), under = r * (1.02f + .07f * (i - 1));
        float rime = Mth.clamp(f / .3f, 0, 1) * (1 - Mth.clamp((f - .5f) / .4f, 0, 1));
        float body = Mth.clamp((f - .25f) / .6f, 0, 1);
        if (rime > .01f) ellipsoid(c, centre, Mth.lerp(.5f, under, shell), Mth.lerp(.5f, under, shell) * 1.12f, 8, 400 + i * 7, FROST.alpha(.45f * rime), 1);
        if (body > .01f) {
            float rr = Mth.lerp(body, under, shell);
            ellipsoid(c, centre, rr, rr * 1.12f, 9, 410 + i * 7, GLACIER.alpha(.35f + .4f * body), 1);
        }
        // The layer's own spikes, in new directions (uneven: some long, some stubs).
        float gs = Mth.clamp((f - .15f) / .7f, 0, 1);
        if (gs <= .01f) return;
        for (int k = 0; k < 5; k++) {
            double a = hash(i * 13 + k * 3.1) * Mth.TWO_PI, z = (hash(i * 7 + k * 5.7) - .5) * 1.8;
            Vec3 d = new Vec3(Math.cos(a), Math.sin(a), z).normalize();
            float len = (1.6f + 2.2f * (float) hash(i * 3 + k)) * s / (1 + .1f * i) * gs;
            IceMesh.crystal(c, centre.add(d.scale(shell * .85f)), d, len, .45f * s * (.5f + .5f * gs) / (1 + .08f * i), 5, 430 + i * 9 + k, k % 2 == 0 ? CLEAR : MILKY, 1, .7f);
        }
        // Sparkles where the new ice is still forming.
        if (f < 1) for (int k = 0; k < 4; k++) {
            double a = hash(i * 17 + k) * Mth.TWO_PI + time * .05, z = (hash(i * 19 + k) - .5) * 1.6;
            Vec3 d = new Vec3(Math.cos(a), Math.sin(a), z).normalize();
            IceMesh.sparkle(c, centre.add(d.scale(shell)), 1.2f * s * .5f, .9f * (1 - f));
        }
    }

    // ------------------------------------------------------------------ the spear
    private static void spear(IceMesh.Ctx c, float size, float crack, float time) {
        float total = SPEAR_BLADE + 8 * size;
        // The butt spike.
        float gb = grown(-(SPEAR_BUTT + 2) / total);
        if (gb > 0) IceMesh.crystal(c, new Vec3(0, 0, SPEAR_BUTT - .6f), BACK, 3.2f * gb, .62f * thick(gb), 6, 141, MILKY, 1, .5f);
        // The shaft: long, faceted, a milky core, a few rime knots.
        int segs = 6;
        float z0 = SPEAR_BUTT, z1 = -SPEAR_BLADE + .6f;
        for (int i = 0; i < segs; i++) {
            float a = Mth.lerp(i / (float) segs, z0, z1), b = Mth.lerp((i + 1) / (float) segs, z0, z1);
            float g = grown(Math.max(Math.abs(a), Math.abs(b)) / total * (a > 0 ? 1.3f : 1));
            if (g <= 0) continue;
            float bb = a + (b - a) * Math.min(1, g * 1.3f);
            IceMesh.limb(c, new Vec3(0, 0, a), new Vec3(0, 0, bb), (i % 2 == 0 ? .5f : .58f) * thick(g), (i % 2 == 0 ? .58f : .5f) * thick(g), 6, 150 + i, CLEAR, MILKY, 1);
            if (g >= 1 && i % 2 == 1) IceMesh.cube(c, 0, 0, b, 1.25f, 1.25f, .6f, FROST.alpha(.6f));
        }
        float gl = grown((SPEAR_BLADE - 1) / total);
        if (gl > 0) IceMesh.line(c, new Vec3(.45, .2, SPEAR_BUTT - 1), new Vec3(.45, .2, Mth.lerp(gl, SPEAR_BUTT, z1)), .07f, .3f * gl, .5f * gl, .65f * gl);
        if (crack >= 2) return;
        // The collar: a flared ring with crystals swept forward.
        float gc = grown(SPEAR_BLADE / total);
        if (gc > 0) {
            IceMesh.limb(c, new Vec3(0, 0, -SPEAR_BLADE + 1.2f), new Vec3(0, 0, -SPEAR_BLADE - .2f), .6f * thick(gc), 1.05f * thick(gc), 7, 160, CLEAR, MILKY, 1);
            for (int i = 0; i < 5; i++) {
                double a = i * Mth.TWO_PI / 5 + .3;
                IceMesh.crystal(c, new Vec3(Math.cos(a) * .7, Math.sin(a) * .7, -SPEAR_BLADE + .5f), new Vec3(Math.cos(a) * .8, Math.sin(a) * .8, -1),
                        1.7f * gc, .28f, 4, 161 + i, CLEAR, 1, .6f);
            }
        }
        // The leaf blade, growing out of the collar to its point (longer as it is drawn back).
        float blade = 8 * size, w = 2.0f * (1 + .25f * (size - 1)), th = .5f;
        float gB = grown((SPEAR_BLADE + blade * .5f) / total);
        if (gB > 0) {
            float reach = blade * Math.min(1, gB * 1.15f);
            leaf(c, -SPEAR_BLADE - .1f, reach, w * thick(gB), th * thick(gB), .35f, 171, Math.max(0, gB * 1.1f - .1f));
            IceMesh.limb(c, new Vec3(0, 0, -SPEAR_BLADE), new Vec3(0, 0, -SPEAR_BLADE - reach * .85f), .3f, .08f, 4, 175, MILKY, null, .9f);
            // The tip still growing while he draws it back: frost glittering on the point.
            if (size > 1.01f) {
                float k = Mth.clamp((size - 1) / .6f, 0, 1);
                Vec3 tip = new Vec3(0, 0, -SPEAR_BLADE - reach);
                IceMesh.sparkle(c, tip, 1.5f + .8f * Mth.sin(time * .8f), .6f * k);
                IceMesh.glow(c, tip, 2.2f * k, .1f * k, .2f * k, .3f * k);
            }
        }
        if (crack > 0) {
            cracksShaft(c, SPEAR_CRACKS, Math.min(1, crack), 1, z0, -SPEAR_BLADE - blade * .9f, .62f, false);
        }
    }
    /**
     * A flat faceted leaf (spear blade, sword blade) from z0 forward (toward -z) over len: half-width w along y, a ridge of
     * half-thickness th along x; widest at widest (share of the length; 0 = a straight blade, see sword). Edges glint (k).
     */
    private static void leaf(IceMesh.Ctx c, float z0, float len, float w, float th, float widest, int seed, float k) {
        int n = 7;
        float[] prev = null;
        Vec3 pl = null, pr = null;
        for (int i = 0; i <= n; i++) {
            float q = i / (float) n, z = z0 - q * len;
            float ww = q < widest ? w * (.55f + .45f * Mth.sin(Mth.HALF_PI * q / widest)) : w * (1 - (float) Math.pow((q - widest) / (1 - widest), 1.5));
            ww = Math.max(.02f, ww);
            float tt = Math.max(.02f, th * (ww / w * .7f + .3f) * (1 - q * .6f));
            float[] ring = {tt, 0, z, 0, ww, z, -tt, 0, z, 0, -ww, z};
            if (prev != null) loft(c, prev, ring, CLEAR, 1, i == 1, false);
            Vec3 l = new Vec3(0, ww, z), r = new Vec3(0, -ww, z);
            if (k > .01f && pl != null) {
                IceMesh.line(c, pl, l, .09f, .5f * k, .78f * k, k);
                IceMesh.line(c, pr, r, .09f, .5f * k, .78f * k, k);
            }
            pl = l; pr = r; prev = ring;
        }
    }

    // ------------------------------------------------------------------ the sword
    private static void sword(IceMesh.Ctx c, float size, float crack, float time) {
        float total = SWORD_TIP * size;
        float gp = grown(-(SWORD_POMMEL + 1.5f) / total);
        if (gp > 0) {
            IceMesh.crystal(c, new Vec3(0, 0, SWORD_POMMEL - .3f), BACK, 2.2f * gp, .85f * thick(gp), 6, 241, MILKY, 1, .5f);
            IceMesh.limb(c, new Vec3(0, 0, SWORD_POMMEL), new Vec3(0, 0, SWORD_POMMEL - 1), 1.0f * thick(gp), .75f * thick(gp), 7, 242, CLEAR, null, 1);
        }
        // The grip, wrapped in ridges of ice.
        for (int i = 0; i < 3; i++) {
            float a = SWORD_POMMEL - 1 - i * 2.1f, b = a - 2.1f;
            float g = grown(Math.max(Math.abs(a), Math.abs(b)) / total * (a > 0 ? 1.3f : 1));
            if (g <= 0) continue;
            IceMesh.limb(c, new Vec3(0, 0, a), new Vec3(0, 0, a + (b - a) * Math.min(1, g * 1.3f)), (i % 2 == 0 ? .58f : .66f) * thick(g), (i % 2 == 0 ? .66f : .58f) * thick(g), 6, 250 + i, CLEAR, MILKY, 1);
        }
        // The crossguard: a block of milky ice, two crystal arms out along the blade's width, swept a little forward.
        float gg = grown(2.6f / total);
        if (gg > 0) {
            IceMesh.cube(c, 0, 0, -2.4f, 1.3f * thick(gg), 1.7f * thick(gg), 1.1f, MILKY, 1);
            for (int sd = -1; sd <= 1; sd += 2) {
                IceMesh.crystal(c, new Vec3(0, sd * .6f, -2.4f), new Vec3(0, sd, -.28), 3.8f * gg, .55f * thick(gg), 5, 255 + sd, CLEAR, 1, .7f);
                IceMesh.crystal(c, new Vec3(0, sd * 1.4f, -2.3f), new Vec3(0, sd * .5, 1), 1.0f * gg, .3f, 4, 258 + sd, MILKY, 1, .4f);
            }
        }
        if (crack >= 2) return;
        // The blade: broad, straight, then the point; a milky core line down the ridge; glints running down its edges.
        float len = (SWORD_TIP - 2.9f) * size;
        float gB = grown(.5f);
        if (gB > 0) {
            float reach = len * Math.min(1, gB * 1.15f);
            blade(c, -2.9f, reach, 1.3f * thick(gB), .42f * thick(gB), Math.max(0, gB * 1.1f - .1f), time);
            IceMesh.limb(c, new Vec3(0, 0, -3.0f), new Vec3(0, 0, -2.9f - reach * .82f), .3f, .12f, 4, 275, MILKY, null, .95f);
        }
        if (crack > 0) cracksShaft(c, SWORD_CRACKS, Math.min(1, crack), 1, -3.2f, -2.9f - len * .95f, 1.1f, true);
    }
    /** The sword's blade: straight edges to 72% of its length, then the point; a glint running down each edge. */
    private static void blade(IceMesh.Ctx c, float z0, float len, float w, float th, float k, float time) {
        float[] qs = {0, .25f, .5f, .72f, .86f, 1};
        float[] ws = {.95f, 1, .97f, .92f, .55f, .02f};
        float[] prev = null;
        Vec3 pl = null, pr = null;
        for (int i = 0; i < qs.length; i++) {
            float z = z0 - qs[i] * len, ww = w * ws[i], tt = Math.max(.02f, th * (ws[i] * .7f + .3f));
            float[] ring = {tt, 0, z, 0, ww, z, -tt, 0, z, 0, -ww, z};
            if (prev != null) loft(c, prev, ring, CLEAR, 1, i == 1, false);
            Vec3 l = new Vec3(0, ww, z), r = new Vec3(0, -ww, z);
            if (k > .01f && pl != null) {
                IceMesh.line(c, pl, l, .08f, .45f * k, .72f * k, .95f * k);
                IceMesh.line(c, pr, r, .08f, .45f * k, .72f * k, .95f * k);
            }
            pl = l; pr = r; prev = ring;
        }
        // The glint: a bright point sliding down the edge now and then.
        float g = (time * .045f) % 1.6f;
        if (k > .5f && g < 1) {
            float q = g, z = z0 - q * len;
            float ww = w * (q < .72f ? 1 : Math.max(.02f, 1 - (q - .72f) / .28f));
            IceMesh.sparkle(c, new Vec3(0, ww, z), 1.6f, Mth.sin(Mth.PI * g) * k);
        }
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
    private static Vec3 onSphere(float a, float b, float z0, float r) {
        float bb = Mth.clamp(b, -.95f, .95f), rr = r * Mth.sqrt(1 - bb * bb);
        return new Vec3(Mth.cos(a) * rr, Mth.sin(a) * rr, z0 + bb * r * 1.12f);
    }
    private static Vec3 onShaft(float a, float b, float z0, float z1, float r) {
        float q = Mth.clamp((b + 1) / 2, 0, 1);
        return new Vec3(Mth.cos(a) * r, Mth.sin(a) * r, Mth.lerp(q, z0, z1));
    }
}
