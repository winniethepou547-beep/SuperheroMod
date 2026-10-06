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
 * Iceman as in the user's reference (a blocky Minecraft Iceman): built of boxes like a Minecraft player, never rounded,
 * textured in pixels (IceMesh skin, tools/textures/iceman_skin.py): a cube head of bright blue ice with a simple pixel
 * face (white eyes under heavy brows, glowing), a crest of ice spikes swept up and back over a frosted hair layer; the
 * X-Men suit on the torso (red over the shoulders and chest, the black V neckline down to the X, black sides with dark
 * grey panels, the grey belt); red shoulders, then arms of ice: chunky ice gauntlets over the forearms with spikes and
 * icicles, ice fists; black shorts to mid thigh, then ice: the knees with spikes, the shins, the feet. The ice is solid
 * and glossy (IceMesh), never see-through.
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

    static final float SHOULDER = 6.0f, FIST = 1.7f;
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
        c.texel = 1;
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
                px(p, s * 2.0f, 0, 0);
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
        c.texel = 1;
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

    // ------------------------------------------------------------------ the skin's boxes
    /** Where each box's texture is in skin.png (u, v, w, h, d; Minecraft's layout): kept in step with tools/textures/iceman_skin.py. */
    static final int[] SKIN_HEAD = {0, 0, 8, 8, 8}, SKIN_HAIR = {32, 0, 8, 8, 8}, SKIN_CHEST = {0, 16, 8, 7, 4}, SKIN_ABDOMEN = {24, 16, 8, 5, 4},
            SKIN_PELVIS = {0, 27, 8, 4, 4}, SKIN_UPPER_ARM = {48, 16, 4, 7, 4}, SKIN_FOREARM = {24, 27, 4, 6, 4}, SKIN_HAND = {40, 27, 4, 4, 4},
            SKIN_THIGH = {0, 37, 4, 6, 4}, SKIN_SHIN = {16, 37, 4, 6, 4}, SKIN_FOOT = {32, 37, 4, 2, 5};
    /** A box of his skin (current frame), the shell over it once it has closed up to its height h (0 feet .. 1 crown). */
    private static void part(int[] t, float x0, float y0, float z0, float x1, float y1, float z1, Mat mat, float h) {
        skinBox(c, x0, y0, z0, x1, y1, z1, t[0], t[1], t[2], t[3], t[4], mat, 1);
        shell(x0, y0, z0, x1, y1, z1, h);
    }
    /** The shell over a box once it has closed up to its height (h: 0 feet .. 1 crown): a thick block of glacier ice round it. */
    private static void shell(float x0, float y0, float z0, float x1, float y1, float z1, float h) {
        if (SHELL_COVER <= 0) return;
        float k = Mth.clamp((SHELL_COVER - h) / .12f, 0, 1);
        if (k <= 0) return;
        float th = SHELL_THICK * (.25f + .75f * k);
        float flash = c.flash;
        c.flash = SHELL_FLASH * .6f + SHELL_GLOW * .25f;
        box(c, x0 - th, y0 - th * .6f, z0 - th, x1 + th, y1 + th * .6f, z1 + th, GLACIER, .55f + .45f * k);
        c.flash = flash;
        // Cracks running over its front as it fails.
        if (SHELL_CRACK > .02f) {
            float cr = SHELL_CRACK, g = .5f + .5f * SHELL_GLOW, zf = z0 - th - .05f;
            int seed = (int) (x0 * 7 + y0 * 13 + z1 * 3);
            float mx = Mth.lerp((float) hash(seed), x0, x1), my = Mth.lerp((float) hash(seed + 1), y0, y1);
            line(c, new Vec3(x0 - th, y0, zf), new Vec3(mx, my, zf), .07f, .5f * cr * g, .8f * cr * g, cr * g);
            line(c, new Vec3(mx, my, zf), new Vec3(x1 + th * .5f, y1, zf), .06f, .5f * cr * g, .8f * cr * g, cr * g);
        }
    }
    /** An ice spike out of the ice (base, direction, length, half width): four-sided, blunt-based, sharp. */
    private static void spike(float x, float y, float z, float dx, float dy, float dz, float len, float r, int seed, Mat mat) {
        crystal(c, new Vec3(x, y, z), new Vec3(dx, dy, dz), len, r, 4, seed, mat, 1, 0);
    }
    /** An icicle hanging from a point along +y (toward the end of the limb), a little askew. */
    private static void icicle(float x, float y, float z, float len, float r, int seed) {
        crystal(c, new Vec3(x, y, z), new Vec3(.12 * (hash(seed) - .5), 1, .12 * (hash(seed + 3) - .5)), len, r, 4, seed, CLEAR, 1, 0);
    }
    /** Pale glossy ice for the spikes (the reference's bright shards), and the ice of the hair. */
    static final Mat SPIKE = new Mat(.6f, .86f, 1f, 1, 1, .05f, .7f, 1f), HAIR = new Mat(.56f, .85f, 1f, 1, 1, .05f, .6f, .9f);

    // ------------------------------------------------------------------ legs: shorts to mid thigh, then ice
    private static void leg(PoseStack p, int side, float knee, float ankle) {
        int s = side == 0 ? -1 : 1;
        c.at(p);
        part(SKIN_THIGH, -2, -.5f, -2, 2, 6.1f, 2, SKIN_ICE, .45f);
        px(p, 0, 6, 0);
        p.mulPose(Axis.XP.rotation(knee));
        c.at(p);
        // The shin: an ice sheath a little thicker than the thigh, spikes on the knee and the outside of the calf.
        part(SKIN_SHIN, -2.2f, -.7f, -2.2f, 2.2f, 5.2f, 2.2f, SKIN_ICE, .25f);
        spike(s * .7f, -.3f, -2.2f, s * .25f, -.55f, -1, 1.7f, .5f, 23 + side, SPIKE);
        spike(s * -.8f, .3f, -2.2f, s * -.2f, -.3f, -1, 1.1f, .38f, 25 + side, SPIKE);
        spike(s * 2.2f, .9f, .2f, s, -.55f, .2f, 1.5f, .45f, 27 + side, SPIKE);
        spike(s * 2.2f, 3.0f, -.6f, s, -.35f, -.1f, 1.0f, .35f, 29 + side, SPIKE);
        icicle(s * 1.1f, 5.2f, 1.4f, 1.0f, .32f, 31 + side);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(ankle));
        c.at(p);
        part(SKIN_FOOT, -2.1f, -.5f, -3.4f, 2.1f, 1.4f, 1.7f, SKIN_ICE, 0);
    }

    // ------------------------------------------------------------------ torso: the suit
    private static void hips() { part(SKIN_PELVIS, -4.2f, -3, -2.2f, 4.2f, 1, 2.2f, SKIN_SUIT, .5f); }
    private static void abdomen() { part(SKIN_ABDOMEN, -4, -7, -2, 4, -2, 2, SKIN_SUIT, .58f); }
    private static void chestPart() { part(SKIN_CHEST, -4.15f, -6.55f, -2.2f, 4.15f, .75f, 2.2f, SKIN_SUIT, .7f); }

    // ------------------------------------------------------------------ arms: red shoulders, then ice
    private static void arm(PoseStack p, Pose pose, int side) {
        float[] v = pose.v;
        int s = side == 0 ? -1 : 1;
        int o = side == 0 ? R : L;
        px(p, s * SHOULDER, -5.3f - v[o + SH_UP] * .6f, -v[o + SH_FWD] * .6f);
        rot(p, v[o + ARM_X], s < 0 ? v[o + ARM_Y] : -v[o + ARM_Y], s < 0 ? v[o + ARM_Z] : -v[o + ARM_Z]);
        c.at(p);
        part(SKIN_UPPER_ARM, -2, -1.6f, -2, 2, 5.3f, 2, SKIN_ICE, .76f);
        // Ice shards standing up out of the shoulder, the biggest on the outside.
        spike(s * 1.2f, -1.6f, -.5f, s * .45f, -1, .1f, 3.8f, .9f, 101 + side, SPIKE);
        spike(s * 1.7f, -1.1f, .9f, s * .75f, -1, .45f, 3.0f, .75f, 103 + side, SPIKE);
        spike(s * .1f, -1.6f, 1.1f, s * .25f, -1, .65f, 1.8f, .5f, 105 + side, SPIKE);
        spike(s * 2.0f, .4f, -.8f, s, -.7f, -.2f, 1.4f, .42f, 107 + side, SPIKE);
        px(p, 0, 5, 0);
        p.mulPose(Axis.XP.rotation(-v[o + ELBOW]));
        c.at(p);
        forearm(side);
        px(p, 0, 4.7f, 0);
        p.mulPose(Axis.XP.rotation(v[o + WRIST_X]));
        p.mulPose(Axis.ZP.rotation(s < 0 ? v[o + WRIST_Z] : -v[o + WRIST_Z]));
        c.at(p);
        hand(p, side, v[o + CURL]);
    }
    /** The ice gauntlet: a chunky block of ice from the elbow to the wrist, shards out of its outside, icicles hanging. */
    private static void forearm(int side) {
        int s = side == 0 ? -1 : 1;
        part(SKIN_FOREARM, -2.35f, -.8f, -2.35f, 2.35f, 4.9f, 2.35f, SKIN_ICE, .62f);
        spike(s * 2.35f, .5f, .5f, s, -.55f, .45f, 2.0f, .55f, 130 + side * 5, SPIKE);
        spike(s * 2.35f, 2.5f, -.4f, s, -.35f, .1f, 1.5f, .45f, 131 + side * 5, SPIKE);
        spike(s * .7f, .9f, 2.35f, s * .3f, -.5f, 1, 1.4f, .42f, 132 + side * 5, SPIKE);
        spike(s * -1.2f, 1.6f, 2.35f, s * -.2f, -.45f, 1, 1.0f, .34f, 133 + side * 5, SPIKE);
        icicle(s * 1.5f, 4.9f, 1.6f, 1.5f, .36f, 134 + side * 5);
        icicle(s * -.4f, 4.9f, 1.8f, 1.0f, .3f, 135 + side * 5);
        icicle(s * 1.8f, 4.9f, -.9f, .8f, .26f, 136 + side * 5);
    }
    /** The fist of ice; what it holds; the cold gathering in it. */
    private static void hand(PoseStack p, int side, float curl) {
        int s = side == 0 ? -1 : 1;
        // A fist block: open, the hand is longer and flatter (the fingers straight), closed, a square fist.
        float open = 1 - Mth.clamp(curl, 0, 1), hx = 1.95f - .55f * open;
        part(SKIN_HAND, -hx, -.3f, -1.95f, hx, 3.4f + .6f * open, 1.95f, SKIN_ICE, .64f);
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

    // ------------------------------------------------------------------ the head: a cube of ice, a crest of ice spikes
    private static void head(PoseStack p) {
        part(SKIN_HEAD, -4, -8, -4, 4, 0, 4, SKIN_ICE, .9f);
        // The frosted hair layer over it (cut away below the hairline in its texture), like a skin's hat layer.
        skinBox(c, -4.5f, -8.5f, -4.5f, 4.5f, .5f, 4.5f, SKIN_HAIR[0], SKIN_HAIR[1], SKIN_HAIR[2], SKIN_HAIR[3], SKIN_HAIR[4], SKIN_ICE, 1);
        // The eyes: white, cold, glowing (the texture's white eyes lit up).
        float pulse = .9f + .1f * Mth.sin(time * .07f);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 e = new Vec3(s * 2.0f, -3.5f, -4.08f);
            line(c, e.add(-.9, 0, 0), e.add(.9, 0, 0), .4f, .4f * pulse, .52f * pulse, .6f * pulse);
            IceMesh.glow(c, e.add(0, 0, -.1), 1.3f, .07f * pulse, .14f * pulse, .2f * pulse);
        }
        if (capture) eyes = world(p, 0, -3.5f, -4.3f);
        // The crest: ice spikes swept up and back over the crown, the longest in the middle, more over the temples.
        spike(0, -8.4f, -1.6f, 0, -1, .55f, 4.6f, 1.1f, 231, HAIR);
        spike(0, -8.4f, 1.2f, 0, -.8f, 1, 4.0f, 1.0f, 232, SPIKE);
        for (int s = -1; s <= 1; s += 2) {
            spike(s * 1.9f, -8.4f, -.8f, s * .3f, -1, .6f, 3.6f, .9f, 233 + s, HAIR);
            spike(s * 2.4f, -8.2f, 1.9f, s * .45f, -.75f, 1, 3.0f, .8f, 235 + s, SPIKE);
            spike(s * 3.6f, -7.2f, -.2f, s * .75f, -.8f, .55f, 2.6f, .7f, 237 + s, HAIR);
            spike(s * 4.3f, -5.6f, 1.3f, s * .6f, -.45f, 1, 2.2f, .55f, 239 + s, SPIKE);
        }
        spike(0, -7.0f, 4.3f, 0, -.35f, 1, 2.8f, .8f, 241, HAIR);
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
