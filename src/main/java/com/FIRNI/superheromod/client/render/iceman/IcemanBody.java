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
 * Iceman's body after Days of Future Past: no skin anywhere, a man made entirely of living ice. Every part is a faceted
 * solid in two layers (IceMesh): a dense milky core inside (the "bones and muscle" seen through the ice) under a clear,
 * blue-tinted shell whose facets catch a bright rim of light at their edges; milky plates sit just under the surface
 * where the muscles are (pecs, abdominals, shoulder blades, the quad, the calf), faint cracks run inside the ice, small
 * crystal ridges stand out of the forearms, the collarbones and down the spine, rime collects at the joints. The head
 * is a smooth faceted skull of ice with a heavy brow, deep-set eye sockets of darker ice and pale glowing eyes, a ridge
 * of low crystals swept back over the crown.
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
        }
    }

    // ------------------------------------------------------------------ building blocks
    /** A segment along y in the current frame: rings at the given heights (y, rx, rz, z offset), a core inside, the shell over all (when it has reached height h). */
    private static void seg(float x, float[] ys, float[] rxs, float[] rzs, float[] zs, int n, int seed, Mat skin, float h) {
        int k = ys.length;
        float[][] outer = new float[k][], inner = new float[k][];
        for (int i = 0; i < k; i++) {
            outer[i] = hring(x, ys[i], zs[i], rxs[i], rzs[i], n, .1f, seed + i * 3, seed * .37f);
            inner[i] = hring(x, ys[i], zs[i] + .05f, rxs[i] * .6f, rzs[i] * .6f, n, .18f, seed + i * 3 + 1, seed * .37f + .4f);
        }
        for (int i = 0; i < k - 1; i++) loft(c, inner[i], inner[i + 1], CORE, ALPHA_CORE, i == 0, i == k - 2);
        for (int i = 0; i < k - 1; i++) loft(c, outer[i], outer[i + 1], skin, 1, i == 0, i == k - 2);
        shell(x, ys, rxs, rzs, zs, n, seed, h);
    }
    private static final float ALPHA_CORE = 1;
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
    /** A milky plate just under the surface (muscle seen through the ice): a box turned about z then x. */
    private static void plate(PoseStack p, float x, float y, float z, float rx, float rz, float w, float h, float d, Mat mat) {
        p.pushPose();
        p.translate(x, y, z);
        if (rz != 0) p.mulPose(Axis.ZP.rotation(rz));
        if (rx != 0) p.mulPose(Axis.XP.rotation(rx));
        c.at(p);
        cube(c, 0, 0, 0, w, h, d, mat, 1);
        p.popPose();
        c.at(p);
    }
    /** A faint crack inside the ice from a to b (current frame). */
    private static void crack(float ax, float ay, float az, float bx, float by, float bz, float k) {
        line(c, new Vec3(ax, ay, az), new Vec3(bx, by, bz), .045f, .22f * k, .42f * k, .55f * k);
    }
    /** A little rime on a joint. */
    private static void rime(float x, float y, float z, float w, float h, float d) { cube(c, x, y, z, w, h, d, FROST.alpha(.8f), 1); }

    // ------------------------------------------------------------------ legs
    private static void leg(PoseStack p, int side, float knee, float ankle) {
        int s = side == 0 ? -1 : 1;
        c.at(p);
        // Thigh: full, the quad bulging in front, tapering to the knee.
        seg(0, new float[]{-.6f, 2.4f, 6.1f}, new float[]{2.2f, 2.3f, 1.7f}, new float[]{2.25f, 2.4f, 1.8f}, new float[]{0, -.15f, 0}, 7, 11 + side, CLEAR, .52f);
        plate(p, s * -.2f, 2.6f, -1.55f, -.05f, 0, 2.2f, 3.4f, .5f, MILKY.alpha(.8f));
        crack(s * .9f, .8f, -2.05f, s * .2f, 4.2f, -1.9f, 1);
        px(p, 0, 6, 0);
        p.mulPose(Axis.XP.rotation(knee));
        c.at(p);
        // The knee: a cap of milky ice, rime round it.
        cube(c, 0, -.1f, -1.65f, 1.9f, 1.8f, .8f, MILKY, 1);
        rime(0, -.9f, -1.8f, 1.6f, .25f, .5f);
        // The shin, the calf behind it, tapering to the ankle.
        seg(0, new float[]{-.4f, 1.9f, 4.9f}, new float[]{1.75f, 1.85f, 1.2f}, new float[]{1.8f, 2.15f, 1.3f}, new float[]{0, .3f, 0}, 7, 21 + side, CLEAR, .25f);
        plate(p, 0, 1.6f, 1.25f, .1f, 0, 2.0f, 2.4f, .6f, MILKY.alpha(.7f));
        crack(0, .4f, -1.5f, s * .4f, 3.6f, -1.1f, .8f);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(ankle));
        c.at(p);
        // The foot: a faceted wedge of ice, the toe a little pointed.
        float[] heel = ring(0, .55f, 1.0f, X, Y, 1.25f, .85f, 6, .1f, 31 + side, .3f);
        float[] mid = ring(0, .85f, -1.0f, X, Y, 1.4f, .75f, 6, .1f, 32 + side, .3f);
        float[] toe = ring(0, 1.2f, -3.1f, X, Y, 1.05f, .42f, 6, .15f, 33 + side, .3f);
        loft(c, heel, mid, CLEAR, 1, true, false);
        loft(c, mid, toe, CLEAR, 1, false, true);
        cube(c, 0, .9f, -.6f, 1.6f, .9f, 2.6f, CORE, 1);
        shell(0, new float[]{-.2f, 1.6f}, new float[]{1.5f, 1.5f}, new float[]{2.4f, 2.6f}, new float[]{-.8f, -.8f}, 6, 41 + side, 0);
    }

    // ------------------------------------------------------------------ torso
    private static void hips() {
        seg(0, new float[]{2.3f, .4f, -1.6f}, new float[]{3.3f, 3.9f, 3.6f}, new float[]{2.0f, 2.35f, 2.2f}, new float[]{0, 0, 0}, 10, 51, CLEAR, .5f);
        // The hip bones and the low belly showing as milky shapes.
        for (int s = -1; s <= 1; s += 2) cube(c, s * 2.4f, -.6f, -1.55f, 1.8f, 1.2f, .5f, MILKY.alpha(.6f));
        crack(-2.6f, 1.6f, -2.1f, 1.8f, -.8f, -2.2f, .7f);
    }
    private static void abdomen() {
        seg(0, new float[]{-1.2f, -3.3f, -5.8f}, new float[]{3.55f, 3.35f, 3.95f}, new float[]{2.15f, 2.05f, 2.35f}, new float[]{0, 0, 0}, 10, 61, CLEAR, .58f);
        // The abdominals: three rows of milky blocks under the clear front; the obliques at the sides.
        for (int row = 0; row < 3; row++)
            for (int s = -1; s <= 1; s += 2) cube(c, s * 1.05f, -1.2f - row * 1.55f, -1.55f, 1.65f, 1.25f, .55f, MILKY.alpha(.75f));
        for (int s = -1; s <= 1; s += 2) cube(c, s * 3.0f, -3.0f, -.6f, .7f, 3.6f, 2.2f, MILKY.alpha(.45f));
        // The spine down the back: a ridge of small crystals.
        for (int i = 0; i < 3; i++) crystal(c, new Vec3(0, -1.3f - i * 1.7f, 1.7f), new Vec3(0, -.35, 1), .9f + .15f * i, .32f, 4, 70 + i, MILKY, 1, .25f);
    }
    private static void chestPart() {
        seg(0, new float[]{.2f, -2.5f, -4.9f, -6.6f}, new float[]{4.0f, 4.7f, 4.85f, 3.0f}, new float[]{2.35f, 2.75f, 2.5f, 2.05f}, new float[]{0, -.1f, .05f, .2f}, 10, 81, CLEAR, .7f);
        for (int s = -1; s <= 1; s += 2) {
            // The pecs: broad milky plates, angled; the shoulder blades behind.
            cube(c, s * 2.1f, -3.7f, -2.05f, 3.6f, 2.9f, .7f, MILKY.alpha(.85f));
            cube(c, s * 2.3f, -2.35f, -2.25f, 3.0f, .45f, .4f, CORE.alpha(.5f));
            cube(c, s * 2.2f, -3.6f, 1.9f, 2.8f, 3.6f, .6f, MILKY.alpha(.6f));
            // The collarbone: a thin crystal ridge out to the shoulder.
            crystal(c, new Vec3(s * .7f, -5.9f, -1.6f), new Vec3(s, -.1, .05), 3.3f, .35f, 4, 90 + s, MILKY, 1, .3f);
            // Lats.
            cube(c, s * 4.05f, -2.4f, .2f, .8f, 4.2f, 3.2f, MILKY.alpha(.4f));
        }
        // The breastbone line and the faint cracks across the chest.
        cube(c, 0, -3.4f, -2.45f, .35f, 3.4f, .3f, CORE.alpha(.6f));
        crack(-3.6f, -5.4f, -2.6f, -1.0f, -2.6f, -2.7f, 1);
        crack(-1.0f, -2.6f, -2.7f, .6f, -.8f, -2.4f, .8f);
        crack(2.8f, -4.6f, -2.6f, 3.6f, -1.6f, -2.3f, .7f);
        // Up the back between the shoulder blades.
        for (int i = 0; i < 2; i++) crystal(c, new Vec3(0, -2.5f - i * 1.8f, 2.3f), new Vec3(0, -.4, 1), 1.0f, .34f, 4, 77 + i, MILKY, 1, .25f);
        // The neck.
        seg(0, new float[]{-5.6f, -7.4f}, new float[]{1.7f, 1.55f}, new float[]{1.8f, 1.6f}, new float[]{.2f, .1f}, 7, 99, CLEAR, .82f);
    }

    // ------------------------------------------------------------------ arms
    private static void arm(PoseStack p, Pose pose, int side) {
        float[] v = pose.v;
        int s = side == 0 ? -1 : 1;
        int o = side == 0 ? R : L;
        px(p, s * SHOULDER, -5.3f - v[o + SH_UP] * .6f, -v[o + SH_FWD] * .6f);
        rot(p, v[o + ARM_X], s < 0 ? v[o + ARM_Y] : -v[o + ARM_Y], s < 0 ? v[o + ARM_Z] : -v[o + ARM_Z]);
        c.at(p);
        // The deltoid: a rounded cap of ice; the upper arm.
        seg(s * .15f, new float[]{-1.6f, -.2f, 1.6f}, new float[]{1.3f, 2.25f, 2.0f}, new float[]{1.5f, 2.3f, 2.05f}, new float[]{0, 0, 0}, 8, 101 + side, CLEAR, .76f);
        seg(s * .2f, new float[]{1.4f, 3.0f, 5.1f}, new float[]{1.85f, 1.95f, 1.45f}, new float[]{1.9f, 2.05f, 1.5f}, new float[]{0, -.2f, 0}, 7, 111 + side, CLEAR, .7f);
        cube(c, s * .1f, 2.9f, -1.35f, 1.6f, 2.4f, .5f, MILKY.alpha(.7f));
        crack(s * 1.1f, .2f, -1.7f, s * .6f, 3.5f, -1.8f, .7f);
        px(p, s * .2f, 5.0f, 0);
        p.mulPose(Axis.XP.rotation(-v[o + ELBOW]));
        c.at(p);
        rime(0, -.2f, 1.45f, 1.4f, .9f, .4f);
        forearm(side);
        px(p, 0, 4.7f, 0);
        p.mulPose(Axis.XP.rotation(v[o + WRIST_X]));
        p.mulPose(Axis.ZP.rotation(s < 0 ? v[o + WRIST_Z] : -v[o + WRIST_Z]));
        c.at(p);
        hand(p, side, v[o + CURL]);
    }
    /** The forearm, a little ridge of crystals standing out of its outer edge, raked back toward the elbow. */
    private static void forearm(int side) {
        int s = side == 0 ? -1 : 1;
        seg(0, new float[]{-.3f, 1.4f, 4.7f}, new float[]{1.5f, 1.75f, 1.15f}, new float[]{1.55f, 1.6f, 1.05f}, new float[]{0, 0, 0}, 7, 121 + side, CLEAR, .62f);
        for (int i = 0; i < 3; i++) {
            float y = 1.0f + i * 1.15f, len = 1.5f - i * .3f;
            crystal(c, new Vec3(s * 1.25f, y, .55f), new Vec3(s * .8, -.55, .45), len, .28f, 4, 130 + side * 5 + i, CLEAR, 1, .5f);
        }
        crack(0, .5f, -1.4f, s * .3f, 3.8f, -1.0f, .8f);
    }
    /** The hand: a palm of ice, four fingers that curl (open: spread; closed: a fist), the thumb in front; what it holds; the cold gathering in it. */
    private static void hand(PoseStack p, int side, float curl) {
        int s = side == 0 ? -1 : 1;
        float[] r0 = hring(0, 0, 0, .95f, 1.35f, 6, .08f, 141 + side, 0), r1 = hring(0, 1.9f, 0, 1.0f, 1.55f, 6, .08f, 142 + side, 0);
        loft(c, r0, r1, CLEAR, 1, true, true);
        cube(c, 0, 1.0f, 0, .9f, 1.6f, 1.6f, CORE, 1);
        for (int f = 0; f < 4; f++) {
            p.pushPose();
            px(p, s * .1f, 1.95f, -1.05f + f * .7f);
            p.mulPose(Axis.ZP.rotation(s * (curl * 1.35f + .05f)));
            c.at(p);
            seg(0, new float[]{0, 1.25f}, new float[]{.36f, .32f}, new float[]{.32f, .3f}, new float[]{0, 0}, 4, 150 + f + side * 8, CLEAR, .64f);
            px(p, 0, 1.25f, 0);
            p.mulPose(Axis.ZP.rotation(s * curl * 1.25f));
            c.at(p);
            crystal(c, new Vec3(0, 0, 0), new Vec3(0, 1, 0), 1.15f - f * .08f, .3f, 4, 160 + f + side * 8, CLEAR, 1, 0);
            p.popPose();
        }
        p.pushPose();
        px(p, s * -.75f, 1.0f, -1.45f);
        p.mulPose(Axis.XP.rotation(-.5f - .6f * curl));
        c.at(p);
        seg(0, new float[]{0, 1.0f}, new float[]{.42f, .36f}, new float[]{.42f, .36f}, new float[]{0, 0}, 4, 170 + side, CLEAR, .64f);
        crystal(c, new Vec3(0, 1.0f, 0), new Vec3(0, 1, 0), .9f, .34f, 4, 172 + side, CLEAR, 1, 0);
        p.popPose();
        c.at(p);
        if (capture) { Vec3 w = world(p, 0, FIST, 0); if (side == 0) handRight = w; else handLeft = w; }
        float glow = HAND_GLOW[side];
        if (glow > .01f) {
            float flick = .85f + .15f * Mth.sin(time * 1.3f + side * 2);
            IceMesh.glow(c, new Vec3(0, FIST, -.6f), 3.2f * glow, .18f * glow * flick, .32f * glow * flick, .45f * glow * flick);
            for (int i = 0; i < 3; i++) {
                float a = time * .25f + i * 2.1f + side;
                IceMesh.sparkle(c, new Vec3(Mth.cos(a) * 1.6f, FIST + Mth.sin(a * 1.3f) * 1.2f, -.8f + Mth.sin(a) * 1.2f), .5f * glow, .7f * glow);
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

    // ------------------------------------------------------------------ the head
    /** A ring round the head at height y: an ellipse a little flattened in front (the face). */
    private static float[] skull(float y, float rx, float rz, float z, int seed) {
        float[] r = hring(0, y, z, rx, rz, 10, .05f, seed, .31f);
        for (int i = 0; i < 10; i++) if (r[i * 3 + 2] < z) r[i * 3 + 2] = z + (r[i * 3 + 2] - z) * .86f;
        return r;
    }
    private static void head(PoseStack p) {
        // The skull: rings from the jaw to the crown.
        float[] ys = {.6f, -.3f, -1.7f, -3.4f, -5.0f, -6.6f, -7.8f, -8.4f};
        float[] rx = {1.9f, 2.35f, 3.05f, 3.5f, 3.75f, 3.6f, 2.85f, 1.6f};
        float[] rz = {2.0f, 2.6f, 3.3f, 3.65f, 3.85f, 3.75f, 3.1f, 1.8f};
        float[] zs = {.1f, -.85f, -.55f, -.25f, -.05f, .15f, .3f, .35f};
        float[][] outer = new float[ys.length][], inner = new float[ys.length][];
        for (int i = 0; i < ys.length; i++) {
            outer[i] = skull(ys[i], rx[i], rz[i], zs[i], 181 + i);
            inner[i] = skull(ys[i], rx[i] * .62f, rz[i] * .62f, zs[i] + .2f, 191 + i);
        }
        for (int i = 0; i < ys.length - 1; i++) loft(c, inner[i], inner[i + 1], CORE, 1, i == 0, i == ys.length - 2);
        for (int i = 0; i < ys.length - 1; i++) loft(c, outer[i], outer[i + 1], CLEAR, 1, i == 0, i == ys.length - 2);
        shell(0, new float[]{.6f, -4.5f, -8.6f}, new float[]{2.4f, 3.9f, 1.9f}, new float[]{2.6f, 4.0f, 2.0f}, new float[]{-.4f, -.1f, .35f}, 9, 201, .9f);
        // The face: a heavy brow, deep sockets of darker ice, the eyes; the nose's ridge, the cheekbones, the mouth set hard, the chin.
        for (int s = -1; s <= 1; s += 2) {
            plate(p, s * 1.45f, -5.75f, -3.55f, -.2f, s * -.18f, 2.7f, .8f, .9f, MILKY);
            plate(p, s * 1.45f, -4.8f, -3.25f, 0, s * -.12f, 2.0f, 1.05f, .5f, DEEP);
            plate(p, s * 2.45f, -3.55f, -2.95f, -.15f, s * .35f, 1.6f, .7f, .8f, MILKY.alpha(.85f));
            plate(p, s * 1.0f, -1.55f, -3.15f, 0, s * .1f, .9f, .35f, .4f, MILKY.alpha(.7f));
            cube(c, s * 3.65f, -4.2f, .1f, .5f, 1.6f, 1.1f, CLEAR, 1);
        }
        plate(p, 0, -3.75f, -3.6f, .3f, 0, .75f, 2.0f, .7f, MILKY);
        plate(p, 0, -2.85f, -3.85f, 0, 0, 1.0f, .5f, .5f, MILKY);
        plate(p, 0, -1.5f, -3.32f, 0, 0, 1.9f, .22f, .3f, DEEP);
        plate(p, 0, .05f, -2.75f, .2f, 0, 1.6f, .9f, .8f, MILKY.alpha(.8f));
        // The eyes: pale, cold, glowing.
        float pulse = .9f + .1f * Mth.sin(time * .07f);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 e = new Vec3(s * 1.45f, -4.8f, -3.6f);
            line(c, e.add(-.6, .05 * s, 0), e.add(.6, -.05 * s, 0), .2f, .75f * pulse, .95f * pulse, pulse);
            IceMesh.glow(c, e.add(0, 0, -.1), 1.5f, .12f * pulse, .22f * pulse, .3f * pulse);
        }
        if (capture) eyes = world(p, 0, -4.8f, -3.8f);
        // The crest: low crystals swept back over the crown, the ice of the scalp.
        for (int i = 0; i < 5; i++) {
            float z = -1.9f + i * 1.1f, y = -8.0f + Math.abs(i - 1.5f) * .25f;
            crystal(c, new Vec3(0, y, z), new Vec3(0, -.55, .85), 1.1f - Math.abs(i - 1.5f) * .12f, .45f, 5, 211 + i, MILKY, 1, .4f);
            for (int s = -1; s <= 1; s += 2)
                crystal(c, new Vec3(s * 1.7f, y + .9f, z + .2f), new Vec3(s * .35, -.5, .8), .8f, .3f, 4, 221 + i + s, CLEAR, 1, .25f);
        }
        crack(-2.4f, -6.6f, -3.0f, -.6f, -7.6f, -2.2f, .8f);
        crack(1.8f, -2.4f, -3.2f, 3.0f, -.8f, -2.4f, .6f);
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
