package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static com.FIRNI.superheromod.client.render.iceman.IceMesh.*;
import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;

/**
 * Iceman as in the user's reference (the comic cover): the X-Men suit — black, a red panel over the shoulders and chest
 * narrowing in a V to the belt, a black V neckline down to the round X emblem on the breastbone, black sides, arms and
 * legs — and ice where his power shows: the face and neck of solid ice, hair of ice swept up and back in sharp crystals,
 * big chunky ice gauntlets from the elbows over the fists, the knees, shins and feet sheathed in ice. The ice is solid and
 * cut like crystal (IceMesh: deep blue shadows, cyan body, cold white highlights, a cyan glow on the outline, cracks glowing
 * cyan, crystal spikes and icicles), never see-through.
 * <p>
 * Model space as the player model's (pixels, +y down, -z in front, -x his right; neck at y 0, hips at 12, feet at 24);
 * the joints are the same chain as Black Panther's, Magneto's and Batman's (PantherMotion.Pose), so the same poses and
 * BatmanMotion's walk work. The hand's frame (for what it holds): the fist's middle at (0, FIST, 0), the arm continuing
 * along +y, the thumb toward -z (a weapon's long axis).
 * <p>
 * Set per draw by whoever draws him (the layer): WEAPON (what the right hand holds, with its forming, size and
 * cracking), the shell (SHELL_*: how far up it has closed, how thick, its cracks, a hit's flash, the stress glow),
 * HAND_GLOW (the cold gathering in each hand).
 */
public final class IcemanBody {
    private IcemanBody() {}

    static final float SHOULDER = 5.0f, FIST = 1.7f;
    // ---- set per draw
    /** The weapon in the right hand (IcemanAction.W_*, -1 none), how formed (0..1), how big (1 plain), how cracked (0..1). */
    public static int WEAPON = -1;
    public static float WEAPON_FORM = 1, WEAPON_SIZE = 1, WEAPON_CRACK;
    /** The shell: closed from the feet up to this share of his height (0..1, over 1 complete), its thickness (px), its cracks (0..1), a hit's flash, the stress glow. */
    public static float SHELL_COVER, SHELL_THICK = 1.8f, SHELL_CRACK, SHELL_FLASH, SHELL_GLOW;
    /** The cold gathering in each hand (0..1; side 0 right). */
    public static final float[] HAND_GLOW = {0, 0};
    /** The whole body this much as opaque (forming, fading). */
    public static float ALPHA = 1;
    /** When on, a draw records the hands, the eyes, the chest and the weapon in the world (null when not drawn). */
    public static boolean capture;
    public static Vec3 handRight, handLeft, eyes, chest, weaponBase, weaponTip;

    private static IceMesh.Ctx c;
    /** The stack being drawn with (for the small turned pieces). */
    private static PoseStack ps;
    private static float time;

    private static void px(PoseStack p, double x, double y, double z) { p.translate(x, y, z); }
    private static void rot(PoseStack p, float x, float y, float z) { p.mulPose(new Quaternionf().rotationZYX(z, y, x)); }

    /**
     * Draws him at the model root: the pose, where the head looks (radians, relative to the body), the free clock, an extra
     * turn of the whole body about the vertical through his middle (align, radians).
     */
    public static void draw(PoseStack p, MultiBufferSource b, int light, Pose pose, float lookYaw, float lookPitch, float t, float align) {
        float[] v = pose.v;
        time = t;
        if (capture) handRight = handLeft = eyes = chest = weaponBase = weaponTip = null;
        p.pushPose();
        if (align != 0) p.mulPose(Axis.YP.rotation(align));
        p.scale(1 / 16f, 1 / 16f, 1 / 16f);
        c = IceMesh.begin(p, b, light);
        ps = p;
        c.time = t;
        c.alpha = ALPHA;
        try {
            px(p, v[SHIFT_X], -v[LIFT], v[SHIFT_Z]);
            px(p, 0, 11, 0);
            if (v[ROOT_PITCH] != 0) p.mulPose(Axis.XP.rotation(v[ROOT_PITCH]));
            if (v[ROOT_YAW] != 0) p.mulPose(Axis.YP.rotation(v[ROOT_YAW]));
            if (v[ROOT_ROLL] != 0) p.mulPose(Axis.ZP.rotation(v[ROOT_ROLL]));
            px(p, 0, -11, 0);
            float drop = Mth.clamp(v[CROUCH], -1, 9.5f);
            float fold = (float) Math.acos(Mth.clamp(1 - Math.max(0, drop) / 10.8f, -1, 1));
            float plant = Mth.clamp(v[PLANT], 0, 1);
            p.pushPose();
            px(p, 0, 12 + drop, 0);
            p.mulPose(Axis.YP.rotation(v[PELVIS_YAW])); p.mulPose(Axis.XP.rotation(v[PELVIS_PITCH])); p.mulPose(Axis.ZP.rotation(v[PELVIS_ROLL]));
            for (int side = 0; side < 2; side++) {
                int s = side == 0 ? -1 : 1;
                int o = side == 0 ? RL : LL;
                float legX = v[o + LEG_X] - fold * plant - v[PELVIS_PITCH] * plant, knee = v[o + KNEE] + 2 * fold * plant;
                float ankle = v[o + ANKLE] - (legX + knee + v[PELVIS_PITCH]) * plant;
                p.pushPose();
                px(p, s * 2.1f, 0, 0);
                rot(p, legX, s < 0 ? v[o + LEG_Y] : -v[o + LEG_Y], s < 0 ? v[o + LEG_Z] : -v[o + LEG_Z]);
                leg(p, side, knee, ankle);
                p.popPose();
            }
            c.at(p);
            hips();
            p.mulPose(Axis.YP.rotation(v[SPINE_YAW])); p.mulPose(Axis.XP.rotation(v[SPINE_PITCH])); p.mulPose(Axis.ZP.rotation(v[SPINE_ROLL]));
            c.at(p);
            abdomen();
            px(p, 0, -5.6f, 0);
            p.mulPose(Axis.YP.rotation(v[CHEST_YAW])); p.mulPose(Axis.XP.rotation(v[CHEST_PITCH])); p.mulPose(Axis.ZP.rotation(v[CHEST_ROLL]));
            c.at(p);
            chestPart();
            if (capture) chest = world(p, 0, -3, 0);
            for (int side = 0; side < 2; side++) {
                p.pushPose();
                arm(p, pose, side);
                p.popPose();
            }
            p.pushPose();
            px(p, 0, -6.6f, -v[NECK]);
            p.mulPose(Axis.YP.rotation(lookYaw + v[HEAD_YAW]));
            p.mulPose(Axis.XP.rotation(Mth.clamp(lookPitch + v[HEAD_PITCH], -1.4f, 1.3f)));
            p.mulPose(Axis.ZP.rotation(v[HEAD_ROLL]));
            c.at(p);
            head(p);
            p.popPose();
            p.popPose();
        } finally {
            c.end();
            c = null;
            ps = null;
            p.popPose();
        }
    }

    /**
     * Only the arms, for his own view: the stack at the chest's frame (pixels, as in draw after the chest's turn), the
     * pose for the arms. Draws the shoulders down, the hands and what they hold.
     */
    public static void drawArms(PoseStack p, MultiBufferSource b, int light, Pose pose, float t, boolean[] sides) {
        time = t;
        if (capture) handRight = handLeft = eyes = chest = weaponBase = weaponTip = null;
        c = IceMesh.begin(p, b, light);
        ps = p;
        c.time = t;
        c.alpha = ALPHA;
        try {
            for (int side = 0; side < 2; side++) {
                if (!sides[side]) continue;
                p.pushPose();
                arm(p, pose, side);
                p.popPose();
            }
        } finally {
            c.end();
            c = null;
            ps = null;
        }
    }

    // ------------------------------------------------------------------ materials
    /** His face and neck: pale solid ice. */
    static final Mat SKIN = new Mat(.62f, .87f, 1f, .98f, 1f, .1f, .75f, .7f);
    /** The hair: white-blue frosted crystal. */
    static final Mat HAIR = new Mat(.8f, .94f, 1f, .98f, 1f, .3f, .8f, .8f);
    /** The ice over his forearms, fists, knees and shins: thick cyan ice. */
    static final Mat ARMOUR = new Mat(.5f, .82f, 1f, .97f, 1f, .05f, .9f, .9f);

    /**
     * The suit's colour at a point of his torso (model space: x across, y down from the neck (0) to the belt (12), z: front
     * negative): black collar, the black V neckline down to the emblem, the red panel over the chest and the shoulders
     * narrowing in a V to the belt, black sides; the back black but for the red over the shoulders.
     */
    static Mat suit(float x, float y, float z) {
        float ax = Math.abs(x);
        if (y < .5f) return SUIT_BLACK;
        if (z < .4f) {
            if (y < 4.4f && ax < 2.9f * (1 - (y - .5f) / 3.9f)) return SUIT_BLACK;
            if (y < 6.4f) return ax < 4.55f ? SUIT_RED : SUIT_BLACK;
            float half = Mth.lerp(Mth.clamp((y - 6.4f) / 5.4f, 0, 1), 3.85f, .45f);
            return ax < half ? SUIT_RED : SUIT_BLACK;
        }
        return y < 2.4f && ax > 1.4f ? SUIT_RED : SUIT_BLACK;
    }
    /** What colour a facet is, from its middle (in the current frame). */
    interface Paint { Mat at(float x, float y, float z); }

    // ------------------------------------------------------------------ building blocks
    /** A segment along y in the current frame: rings at the given heights (y, rx, rz, z offset), one material, the shell over it once it has reached height h. */
    private static void seg(float x, float[] ys, float[] rxs, float[] rzs, float[] zs, int n, int seed, Mat skin, float h) {
        seg(x, ys, rxs, rzs, zs, n, seed, skin, h, 0, .1f);
    }
    /** caps: 1 closes the first ring, 2 the last (only where an end shows); jitter: how uneven the rings are (ice: rougher). */
    private static void seg(float x, float[] ys, float[] rxs, float[] rzs, float[] zs, int n, int seed, Mat skin, float h, int caps, float jitter) {
        int k = ys.length;
        float[][] outer = new float[k][];
        for (int i = 0; i < k; i++) outer[i] = hring(x, ys[i], zs[i], rxs[i], rzs[i], n, jitter, seed + i * 3, seed * .37f);
        for (int i = 0; i < k - 1; i++) loft(c, outer[i], outer[i + 1], skin, 1, i == 0 && (caps & 1) != 0, i == k - 2 && (caps & 2) != 0);
        shell(x, ys, rxs, rzs, zs, n, seed, h);
    }
    /**
     * A painted segment (the suit): rings every step pixels between the key rings (y, rx, rz, z offset), n facets round,
     * each facet coloured by paint at its middle; dy is added to y before asking the paint (the frame's height in the body).
     */
    private static void painted(float[] ys, float[] rxs, float[] rzs, float[] zs, int n, float step, float dy, Paint paint, float h) {
        java.util.List<float[]> rings = new java.util.ArrayList<>();
        for (int i = 0; i < ys.length - 1; i++) {
            int parts = Math.max(1, (int) Math.ceil(Math.abs(ys[i + 1] - ys[i]) / step));
            for (int j = 0; j < parts; j++) {
                float u = j / (float) parts;
                rings.add(hring(0, Mth.lerp(u, ys[i], ys[i + 1]), Mth.lerp(u, zs[i], zs[i + 1]), Mth.lerp(u, rxs[i], rxs[i + 1]), Mth.lerp(u, rzs[i], rzs[i + 1]), n, 0, 0, 0));
            }
        }
        int last = ys.length - 1;
        rings.add(hring(0, ys[last], zs[last], rxs[last], rzs[last], n, 0, 0, 0));
        for (int r = 0; r < rings.size() - 1; r++) {
            float[] ra = rings.get(r), rb = rings.get(r + 1);
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float mx = (ra[i * 3] + ra[j * 3] + rb[i * 3] + rb[j * 3]) / 4, my = (ra[i * 3 + 1] + rb[i * 3 + 1]) / 2, mz = (ra[i * 3 + 2] + ra[j * 3 + 2] + rb[i * 3 + 2] + rb[j * 3 + 2]) / 4;
                quad(c, ra[i * 3], ra[i * 3 + 1], ra[i * 3 + 2], ra[j * 3], ra[j * 3 + 1], ra[j * 3 + 2],
                        rb[j * 3], rb[j * 3 + 1], rb[j * 3 + 2], rb[i * 3], rb[i * 3 + 1], rb[i * 3 + 2], paint.at(mx, my + dy, mz), 1);
            }
        }
        shell(0, ys, rxs, rzs, zs, Math.min(n, 10), 300 + (int) dy, h);
    }
    /** The shell over a segment once it has closed up to its height (h: 0 feet .. 1 crown). */
    private static void shell(float x, float[] ys, float[] rxs, float[] rzs, float[] zs, int n, int seed, float h) {
        if (SHELL_COVER <= 0) return;
        float k = Mth.clamp((SHELL_COVER - h) / .12f, 0, 1);
        if (k <= 0) return;
        float th = SHELL_THICK * (.25f + .75f * k);
        int m = ys.length;
        float[][] r = new float[m][];
        for (int i = 0; i < m; i++) r[i] = hring(x, ys[i], zs[i], rxs[i] + th, rzs[i] + th, Math.max(5, n - 1), .28f, seed + 91 + i * 5, seed * .9f);
        float flash = c.flash;
        c.flash = SHELL_FLASH * .6f + SHELL_GLOW * .25f;
        for (int i = 0; i < m - 1; i++) loft(c, r[i], r[i + 1], GLACIER, .55f + .45f * k, true, true);
        c.flash = flash;
        // Cracks running over it as it fails.
        if (SHELL_CRACK > .02f) {
            for (int i = 0; i < m - 1; i++) {
                int a = (int) (hash(seed * 7 + i) * r[i].length / 3) * 3;
                Vec3 p0 = new Vec3(r[i][a], r[i][a + 1], r[i][a + 2]);
                Vec3 p1 = new Vec3(r[i + 1][a], r[i + 1][a + 1], r[i + 1][a + 2]).lerp(p0, .3);
                Vec3 mid = p0.lerp(p1, .5).add(hash(seed + i) - .5, 0, hash(seed * 3 + i) - .5);
                float cr = SHELL_CRACK, g = .5f + .5f * SHELL_GLOW;
                line(c, p0, mid, .07f, .5f * cr * g, .8f * cr * g, cr * g);
                line(c, mid, p1, .06f, .5f * cr * g, .8f * cr * g, cr * g);
            }
        }
    }
    /** A box turned about z, then x. */
    private static void plate(float x, float y, float z, float rx, float rz, float w, float h, float d, Mat mat) { plate(x, y, z, rx, 0, rz, w, h, d, mat); }
    /** The same, turned about z, then y, then x. */
    private static void plate(float x, float y, float z, float rx, float ry, float rz, float w, float h, float d, Mat mat) {
        PoseStack p = ps;
        p.pushPose();
        p.translate(x, y, z);
        if (rz != 0) p.mulPose(Axis.ZP.rotation(rz));
        if (ry != 0) p.mulPose(Axis.YP.rotation(ry));
        if (rx != 0) p.mulPose(Axis.XP.rotation(rx));
        c.at(p);
        cube(c, 0, 0, 0, w, h, d, mat, 1);
        p.popPose();
        c.at(p);
    }
    /** A crack glowing cyan on the ice's surface, from a to b (current frame). */
    private static void crack(float ax, float ay, float az, float bx, float by, float bz, float k) {
        IceMesh.vein(c, new Vec3(ax, ay, az), new Vec3(bx, by, bz), .05f, .55f * k);
    }
    /** A crystal spike out of the ice (base, direction, length, radius). */
    private static void spike(float x, float y, float z, float dx, float dy, float dz, float len, float r, int seed, Mat mat) {
        crystal(c, new Vec3(x, y, z), new Vec3(dx, dy, dz), len, r, 5, seed, mat, 1, .12f);
    }
    /** An icicle hanging down (+y) from a point. */
    private static void icicle(float x, float y, float z, float len, float r, int seed) {
        crystal(c, new Vec3(x, y, z), new Vec3(.05 * (hash(seed) - .5), 1, .05 * (hash(seed + 3) - .5)), len, r, 4, seed, ARMOUR, 1, .2f);
    }

    // ------------------------------------------------------------------ legs: black suit to the knee, ice over the knee, shin and foot
    private static void leg(PoseStack p, int side, float knee, float ankle) {
        int s = side == 0 ? -1 : 1;
        c.at(p);
        // The thigh in the black suit, the quad's fullness in front.
        seg(0, new float[]{-.6f, 1.4f, 3.6f, 6.1f}, new float[]{2.2f, 2.3f, 2.15f, 1.75f}, new float[]{2.25f, 2.45f, 2.3f, 1.85f},
                new float[]{0, -.15f, -.1f, 0}, 10, 11 + side, SUIT_BLACK, .52f, 0, 0);
        // The ice creeping up over the lower thigh (a ragged edge).
        seg(0, new float[]{4.4f, 6.2f}, new float[]{1.95f, 2.0f}, new float[]{2.05f, 2.1f}, new float[]{-.05f, 0}, 7, 15 + side, ARMOUR, .45f, 0, .22f);
        px(p, 0, 6, 0);
        p.mulPose(Axis.XP.rotation(knee));
        c.at(p);
        // The knee: a block of ice with crystals standing out of it.
        seg(0, new float[]{-1.4f, .3f, 1.6f}, new float[]{2.05f, 2.25f, 2.0f}, new float[]{2.15f, 2.45f, 2.1f}, new float[]{0, -.25f, -.1f}, 7, 19 + side, ARMOUR, .3f, 0, .22f);
        spike(s * .4f, -.2f, -2.2f, s * .3f, -.5f, -1, 1.6f, .45f, 23 + side, ARMOUR);
        spike(s * -.9f, .5f, -2.0f, s * -.4f, -.3f, -1, 1.1f, .35f, 25 + side, MILKY);
        spike(s * 1.9f, -.6f, -.4f, s, -.4f, -.2f, 1.2f, .35f, 27 + side, ARMOUR);
        crack(s * .3f, -.9f, -2.5f, s * -.6f, 1.0f, -2.35f, 1);
        // The shin: thick ice down to the ankle, rough, icicles hanging off the calf.
        seg(0, new float[]{1.2f, 2.6f, 4.9f}, new float[]{2.1f, 2.15f, 1.75f}, new float[]{2.2f, 2.4f, 1.9f}, new float[]{0, .2f, 0}, 7, 21 + side, ARMOUR, .25f, 0, .2f);
        icicle(s * .8f, 4.6f, 1.6f, 1.6f, .35f, 29 + side);
        icicle(s * -.7f, 4.8f, 1.4f, 1.1f, .28f, 31 + side);
        spike(s * 1.8f, 2.6f, .4f, s, .2f, .4f, .9f, .3f, 33 + side, MILKY);
        crack(s * -.4f, 1.8f, -2.15f, s * .2f, 4.2f, -1.75f, .8f);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(ankle));
        c.at(p);
        // The foot: a heavy wedge of ice, the toe pointed.
        float[] heel = ring(0, .45f, 1.1f, X, Y, 1.55f, .95f, 6, .14f, 35 + side, .3f);
        float[] mid = ring(0, .8f, -1.0f, X, Y, 1.65f, .85f, 6, .14f, 36 + side, .3f);
        float[] toe = ring(0, 1.15f, -3.3f, X, Y, 1.2f, .5f, 6, .18f, 37 + side, .3f);
        loft(c, heel, mid, ARMOUR, 1, true, false);
        loft(c, mid, toe, ARMOUR, 1, false, true);
        shell(0, new float[]{-.2f, 1.6f}, new float[]{1.5f, 1.5f}, new float[]{2.4f, 2.6f}, new float[]{-.8f, -.8f}, 6, 41 + side, 0);
    }

    // ------------------------------------------------------------------ torso: the suit
    private static void hips() {
        // The black briefs and the belt over them, a small grey buckle with the X.
        painted(new float[]{2.4f, 1.0f, -.3f, -1.6f}, new float[]{3.3f, 3.85f, 3.95f, 3.65f}, new float[]{2.05f, 2.35f, 2.4f, 2.25f}, new float[]{0, 0, 0, 0},
                14, 1.4f, 12, (x, y, z) -> SUIT_BLACK, .5f);
        painted(new float[]{-.5f, -1.5f}, new float[]{4.0f, 3.75f}, new float[]{2.45f, 2.3f}, new float[]{0, 0}, 14, 1, 12, (x, y, z) -> SUIT_GREY, .5f);
        cube(c, 0, -1.0f, -2.45f, 1.4f, 1.0f, .35f, SUIT_PALE);
        cube(c, 0, -1.0f, -2.65f, .9f, .6f, .1f, SUIT_BLACK);
    }
    private static void abdomen() {
        painted(new float[]{-1.4f, -3.3f, -5.9f}, new float[]{3.6f, 3.4f, 4.0f}, new float[]{2.2f, 2.1f, 2.4f}, new float[]{0, 0, 0},
                20, .7f, 12, IcemanBody::suit, .58f);
    }
    private static void chestPart() {
        painted(new float[]{.4f, -2.5f, -4.9f, -6.3f}, new float[]{4.05f, 4.75f, 4.9f, 3.4f}, new float[]{2.4f, 2.8f, 2.55f, 2.15f}, new float[]{0, -.1f, .05f, .2f},
                20, .7f, 6.4f, IcemanBody::suit, .7f);
        // The X emblem on the breastbone: a red ring, a black disc, the red X.
        float ey = -2.2f, ez = -2.85f;
        float[] r0 = ring(0, ey, ez, X, Y, 1.0f, 1.0f, 12, 0, 0, 0), r1 = ring(0, ey, ez - .2f, X, Y, 1.0f, 1.0f, 12, 0, 0, 0);
        loft(c, r0, r1, SUIT_RED, 1, false, true);
        float[] d0 = ring(0, ey, ez - .2f, X, Y, .78f, .78f, 12, 0, 0, 0), d1 = ring(0, ey, ez - .28f, X, Y, .78f, .78f, 12, 0, 0, 0);
        loft(c, d0, d1, SUIT_BLACK, 1, false, true);
        for (int s = -1; s <= 1; s += 2) plate(0, ey, ez - .32f, 0, s * .785f, 1.25f, .24f, .08f, SUIT_RED);
        // The neck: ice.
        seg(0, new float[]{-5.6f, -7.5f}, new float[]{1.75f, 1.6f}, new float[]{1.85f, 1.65f}, new float[]{.2f, .1f}, 8, 99, SKIN, .82f, 0, .06f);
        // The collar's black rim round it.
        seg(0, new float[]{-6.0f, -6.6f}, new float[]{2.25f, 2.05f}, new float[]{2.25f, 2.05f}, new float[]{.2f, .2f}, 12, 98, SUIT_BLACK, .8f, 0, 0);
    }

    // ------------------------------------------------------------------ arms: red shoulders, black sleeves, ice gauntlets
    private static void arm(PoseStack p, Pose pose, int side) {
        float[] v = pose.v;
        int s = side == 0 ? -1 : 1;
        int o = side == 0 ? R : L;
        px(p, s * SHOULDER, -5.3f - v[o + SH_UP] * .6f, -v[o + SH_FWD] * .6f);
        rot(p, v[o + ARM_X], s < 0 ? v[o + ARM_Y] : -v[o + ARM_Y], s < 0 ? v[o + ARM_Z] : -v[o + ARM_Z]);
        c.at(p);
        // The shoulder cap in the suit's red, the sleeve black.
        seg(s * .15f, new float[]{-1.6f, -.3f, 1.5f}, new float[]{1.3f, 2.05f, 1.95f}, new float[]{1.45f, 2.1f, 2.0f}, new float[]{0, 0, 0}, 10, 101 + side, SUIT_RED, .76f, 0, 0);
        seg(s * .2f, new float[]{1.3f, 3.0f, 5.2f}, new float[]{1.9f, 2.0f, 1.55f}, new float[]{1.95f, 2.1f, 1.6f}, new float[]{0, -.2f, 0}, 10, 111 + side, SUIT_BLACK, .7f, 0, 0);
        px(p, s * .2f, 5.0f, 0);
        p.mulPose(Axis.XP.rotation(-v[o + ELBOW]));
        c.at(p);
        forearm(side);
        px(p, 0, 4.7f, 0);
        p.mulPose(Axis.XP.rotation(v[o + WRIST_X]));
        p.mulPose(Axis.ZP.rotation(s < 0 ? v[o + WRIST_Z] : -v[o + WRIST_Z]));
        c.at(p);
        hand(p, side, v[o + CURL]);
    }
    /** The ice gauntlet: thick, rough ice from the elbow to the wrist, crystals standing out of it, icicles hanging. */
    private static void forearm(int side) {
        int s = side == 0 ? -1 : 1;
        seg(0, new float[]{-1.0f, .6f, 2.4f, 4.8f}, new float[]{1.95f, 2.35f, 2.45f, 2.1f}, new float[]{2.0f, 2.3f, 2.35f, 2.05f}, new float[]{.1f, 0, 0, 0},
                7, 121 + side, ARMOUR, .62f, 1, .24f);
        spike(s * 2.0f, .4f, .6f, s, -.6f, .5f, 1.8f, .42f, 130 + side * 5, ARMOUR);
        spike(s * 2.2f, 1.9f, -.2f, s, -.4f, -.1f, 1.3f, .35f, 131 + side * 5, MILKY);
        spike(s * .4f, .2f, 2.1f, s * .2f, -.7f, 1, 1.4f, .38f, 132 + side * 5, ARMOUR);
        spike(s * -1.6f, 1.2f, -1.2f, s * -.7f, -.5f, -.6f, .9f, .3f, 133 + side * 5, MILKY);
        icicle(s * .6f, 4.6f, 1.6f, 1.3f, .3f, 134 + side * 5);
        crack(s * 1.2f, -.2f, -1.95f, s * .3f, 2.6f, -2.2f, 1);
        crack(s * .3f, 2.6f, -2.2f, s * -.8f, 4.2f, -1.7f, .8f);
    }
    /** The hand of ice: a big chunky fist (open: thick fingers spread), the thumb in front; what it holds; the cold gathering in it. */
    private static void hand(PoseStack p, int side, float curl) {
        int s = side == 0 ? -1 : 1;
        float[] r0 = hring(0, -.2f, 0, 1.3f, 1.7f, 6, .14f, 141 + side, 0), r1 = hring(0, 2.1f, 0, 1.35f, 1.9f, 6, .14f, 142 + side, 0);
        loft(c, r0, r1, ARMOUR, 1, true, true);
        for (int f = 0; f < 4; f++) {
            p.pushPose();
            px(p, s * .1f, 2.05f, -1.2f + f * .8f);
            p.mulPose(Axis.ZP.rotation(s * (curl * 1.35f + .05f)));
            c.at(p);
            seg(0, new float[]{0, 1.35f}, new float[]{.5f, .45f}, new float[]{.45f, .42f}, new float[]{0, 0}, 5, 150 + f + side * 8, ARMOUR, .64f, 0, .12f);
            px(p, 0, 1.35f, 0);
            p.mulPose(Axis.ZP.rotation(s * curl * 1.25f));
            c.at(p);
            crystal(c, new Vec3(0, 0, 0), new Vec3(0, 1, 0), 1.25f - f * .08f, .42f, 5, 160 + f + side * 8, ARMOUR, 1, .2f);
            p.popPose();
        }
        p.pushPose();
        px(p, s * -.95f, 1.0f, -1.7f);
        p.mulPose(Axis.XP.rotation(-.5f - .6f * curl));
        c.at(p);
        seg(0, new float[]{0, 1.1f}, new float[]{.55f, .48f}, new float[]{.55f, .48f}, new float[]{0, 0}, 5, 170 + side, ARMOUR, .64f, 0, .12f);
        crystal(c, new Vec3(0, 1.1f, 0), new Vec3(0, 1, 0), 1.0f, .45f, 5, 172 + side, ARMOUR, 1, .2f);
        p.popPose();
        c.at(p);
        // Knuckle crystals on the back of the fist.
        for (int k = 0; k < 3; k++) spike(s * 1.1f, 1.5f, -.9f + k * .9f, s, .3f, 0, .8f - k * .1f, .28f, 175 + k + side * 4, MILKY);
        if (capture) { Vec3 w = world(p, 0, FIST, 0); if (side == 0) handRight = w; else handLeft = w; }
        float glow = HAND_GLOW[side];
        if (glow > .01f) {
            float flick = .85f + .15f * Mth.sin(time * 1.3f + side * 2);
            IceMesh.glow(c, new Vec3(0, FIST, -.6f), 3.4f * glow, .18f * glow * flick, .34f * glow * flick, .48f * glow * flick);
            for (int i = 0; i < 3; i++) {
                float a = time * .25f + i * 2.1f + side;
                IceMesh.sparkle(c, new Vec3(Mth.cos(a) * 1.9f, FIST + Mth.sin(a * 1.3f) * 1.4f, -.8f + Mth.sin(a) * 1.4f), .55f * glow, .75f * glow);
            }
        }
        if (side == 0 && WEAPON >= 0) {
            p.pushPose();
            px(p, 0, FIST, 0);
            c.at(p);
            IcemanArmory.inHand(c, p, WEAPON, WEAPON_FORM, WEAPON_SIZE, WEAPON_CRACK, time);
            if (capture) {
                float len = IcemanArmory.length(WEAPON, WEAPON_SIZE) * WEAPON_FORM;
                weaponBase = world(p, 0, 0, 0);
                weaponTip = world(p, 0, 0, -len);
            }
            p.popPose();
            c.at(p);
        }
    }

    // ------------------------------------------------------------------ the head: a face of solid ice, hair of ice crystals
    /** The skull's rings from the jaw to the crown: height, half width, half depth, centre's depth. */
    private static final float[] HY = {.6f, -.3f, -1.7f, -3.4f, -5.0f, -6.6f, -7.6f, -8.1f},
            HX = {1.9f, 2.4f, 3.05f, 3.45f, 3.7f, 3.6f, 3.0f, 1.9f},
            HZ = {2.0f, 2.7f, 3.35f, 3.6f, 3.8f, 3.75f, 3.2f, 2.0f},
            HC = {.1f, -.75f, -.5f, -.25f, -.05f, .15f, .3f, .35f};
    /** A ring round the head: an ellipse behind, squarer in front (the broad plane of the face). */
    private static float[] skull(float y, float rx, float rz, float z, int seed, float k) {
        int n = 12;
        float[] r = new float[n * 3];
        for (int i = 0; i < n; i++) {
            float a = .26f + Mth.TWO_PI * i / n, cs = Mth.cos(a), sn = Mth.sin(a);
            float j = 1 + .04f * (float) (hash(seed * 31 + i * 7.13) - .5) * 2;
            float x, zz;
            if (sn < 0) { x = Math.signum(cs) * (float) Math.pow(Math.abs(cs), .7) * rx; zz = -(float) Math.pow(-sn, .7) * rz * .9f; }
            else { x = cs * rx; zz = sn * rz; }
            r[i * 3] = x * j * k; r[i * 3 + 1] = y; r[i * 3 + 2] = z + zz * j * k;
        }
        return r;
    }
    /** Where the face's surface is (its depth, z) at (x, y), from the same rings. */
    private static float faceZ(float x, float y) {
        int i = 0;
        while (i < HY.length - 2 && y < HY[i + 1]) i++;
        float u = Mth.clamp((y - HY[i]) / (HY[i + 1] - HY[i]), 0, 1);
        float rx = Mth.lerp(u, HX[i], HX[i + 1]), rz = Mth.lerp(u, HZ[i], HZ[i + 1]), zc = Mth.lerp(u, HC[i], HC[i + 1]);
        float cs = Math.min(1, (float) Math.pow(Math.min(1, Math.abs(x) / rx), 1 / .7));
        float sn = Mth.sqrt(Math.max(0, 1 - cs * cs));
        return zc - (float) Math.pow(sn, .7) * rz * .9f;
    }
    private static void head(PoseStack p) {
        int m = HY.length;
        float[][] outer = new float[m][];
        for (int i = 0; i < m; i++) outer[i] = skull(HY[i], HX[i], HZ[i], HC[i], 181 + i, 1);
        for (int i = 0; i < m - 1; i++) loft(c, outer[i], outer[i + 1], SKIN, 1, false, i == m - 2);
        shell(0, new float[]{.6f, -4.5f, -8.6f}, new float[]{2.4f, 3.9f, 1.9f}, new float[]{2.6f, 4.0f, 2.0f}, new float[]{-.4f, -.1f, .35f}, 9, 201, .9f);
        // The face, standing out of the ice: a heavy frowning brow, the cheekbones, the nose's ridge, the mouth set hard,
        // a strong chin; the sockets of darker ice.
        for (int s = -1; s <= 1; s += 2) {
            plate(s * 1.35f, -5.55f, faceZ(1.35f, -5.55f) - .2f, -.25f, s * -.22f, s * .2f, 2.5f, .7f, .8f, SKIN);
            plate(s * 1.4f, -4.85f, faceZ(1.4f, -4.85f) + .02f, 0, s * -.22f, s * -.05f, 1.9f, .95f, .3f, DEEP);
            plate(s * 2.3f, -3.55f, faceZ(2.3f, -3.55f) - .1f, -.15f, s * -.5f, s * .25f, 1.4f, .7f, .6f, SKIN);
            plate(s * 1.05f, -1.4f, faceZ(1.05f, -1.4f) - .05f, 0, s * -.2f, s * .12f, 1.0f, .3f, .35f, SKIN);
            plate(s * 3.6f, -4.2f, .1f, 0, 0, 0, .45f, 1.6f, 1.1f, SKIN);
        }
        plate(0, -3.65f, faceZ(0, -3.65f) - .15f, .3f, 0, 0, .75f, 2.1f, .7f, SKIN);
        plate(0, -2.75f, faceZ(0, -2.75f) - .35f, 0, 0, 0, 1.0f, .5f, .5f, SKIN);
        plate(0, -1.45f, faceZ(0, -1.45f) - .02f, 0, 0, 0, 1.8f, .2f, .2f, DEEP);
        plate(0, .1f, faceZ(0, .1f) - .1f, .2f, 0, 0, 1.6f, .9f, .7f, SKIN);
        crack(-2.6f, -2.6f, faceZ(-2.6f, -2.6f) - .05f, -1.9f, -.9f, faceZ(-1.9f, -.9f) - .05f, .5f);
        // The eyes: white, cold, glowing, set in the sockets.
        float pulse = .9f + .1f * Mth.sin(time * .07f);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 e = new Vec3(s * 1.4f, -4.85f, faceZ(1.4f, -4.85f) - .2f);
            Vec3 in = new Vec3(s * .45, .07 * s, s * -.1);
            line(c, e.subtract(in), e.add(in), .14f, .8f * pulse, .95f * pulse, pulse);
            IceMesh.glow(c, e.add(0, 0, -.15), .75f, .1f * pulse, .2f * pulse, .28f * pulse);
        }
        if (capture) eyes = world(p, 0, -4.85f, faceZ(0, -4.85f) - .3f);
        // The hair: a mass of frosted ice over the crown (it swells up and back from the hairline), and out of it crystals
        // swept up and back, the longest rising over the forehead like the comic's.
        float[][] cap = {skull(-6.5f, 3.75f, 3.9f, .35f, 271, 1), skull(-8.0f, 3.5f, 3.7f, .7f, 272, 1), skull(-9.3f, 2.8f, 3.1f, 1.1f, 273, 1), skull(-10.0f, 1.5f, 1.9f, 1.4f, 274, 1)};
        for (int i = 0; i < cap.length - 1; i++) loft(c, cap[i], cap[i + 1], HAIR, 1, false, i == cap.length - 2);
        for (int i = 0; i < 7; i++) {
            float x = (i - 3) * .95f, ax = Math.abs(x);
            float z = faceZ(x, -6.8f) + .9f;
            spike(x, -8.2f + ax * .25f, z, x * .12f, -1, .55f, 5.2f - ax * .55f, 1.0f - ax * .08f, 231 + i, i % 2 == 0 ? HAIR : ARMOUR);
        }
        for (int i = 0; i < 6; i++) {
            float x = (i - 2.5f) * 1.1f, ax = Math.abs(x);
            spike(x, -9.0f + ax * .3f, 1.2f, x * .16f, -.7f, 1, 4.4f - ax * .35f, .9f, 241 + i, HAIR);
        }
        for (int i = 0; i < 5; i++) {
            float x = (i - 2) * 1.3f;
            spike(x, -7.6f, 3.0f, x * .2f, -.35f, 1, 3.2f, .75f, 251 + i, i % 2 == 0 ? HAIR : ARMOUR);
        }
        // Over the temples, swept back.
        for (int s = -1; s <= 1; s += 2) {
            spike(s * 3.2f, -6.2f, -.4f, s * .35f, -.5f, 1, 2.6f, .6f, 261 + s, HAIR);
            spike(s * 3.4f, -5.2f, .8f, s * .3f, -.2f, 1, 2.0f, .5f, 263 + s, ARMOUR);
        }
    }

    // ------------------------------------------------------------------ world points
    private static final Vector4f V4 = new Vector4f();
    private static final Vector3f V3 = new Vector3f();
    /** Where a point of the current model space (pixels: the stack is scaled) is in the world (through the inverse view rotation). */
    static Vec3 world(PoseStack p, float x, float y, float z) {
        V4.set(x, y, z, 1).mul(p.last().pose());
        V3.set(V4.x, V4.y, V4.z);
        RenderSystem.getInverseViewRotationMatrix().transform(V3);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return cam.add(V3.x, V3.y, V3.z);
    }
}
