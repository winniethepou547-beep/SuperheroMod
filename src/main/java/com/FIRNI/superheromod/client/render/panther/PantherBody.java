package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;

/**
 * Black Panther's body, built of boxes after the MCU suit: a full black mask with pointed ears, angular
 * silver-white eyes and the silver Wakandan lines over the brow, nose and muzzle; the Vibranium necklace
 * of silver fangs round the collar; a sculpted near-black suit (pectorals, abdominals, obliques, lats,
 * a muscular back) crossed by thin silver triangle patterns; long arms with Wakandan bracers, gloves with
 * four fingers and a curved metal claw on each; sculpted legs, knee plates, shaped boots.
 *
 * Materials: the suit is near-black, but every panel edge catches a cold sheen (drawn full-bright, so the
 * shape reads in any light, tinted violet as the suit charges); the silver, the necklace and the claws are
 * brighter, with full-bright edge highlights like polished metal. The violet energy lines follow the suit's
 * own lines and light up region by region as kinetic energy is stored (see Charge).
 *
 * Model space: pixels/16, +y down, -z in front, -x is his right; the root is the player model's (neck line
 * at y 0, hips at 12, feet at 24). In GHOST mode the body is drawn as a faint violet afterimage.
 */
public final class PantherBody {
    /** NORMAL: the suit. GHOST: a see-through copy in one colour (afterimages, the glitch). CAMO: the camouflage, glassy and shimmering. */
    public static final int NORMAL = 0, GHOST = 1, CAMO = 2;
    /** The colour a GHOST draw takes (violet afterimages; cyan and magenta for the glitch). */
    public static float[] tint = {.14f, .07f, .28f};
    private static int boxIndex;
    private static float drawTime;
    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 1, 1, 1);
    private static final int FULL = 15728880;
    static final float[] SUIT = {.045f, .045f, .055f}, SUIT_PANEL = {.075f, .075f, .09f}, SUIT_DEEP = {.025f, .025f, .03f},
            SILVER = {.62f, .64f, .7f}, SILVER_DARK = {.36f, .37f, .42f}, CLAW = {.8f, .82f, .88f}, LENS = {.72f, .76f, .84f};
    /** The cold sheen along the panels' edges (full-bright). */
    private static final float[] SHEEN = {.13f, .13f, .17f};

    // ------------------------------------------------------------------ the stored energy's light
    /** Regions of the suit, in the order the energy reaches them and the charge it takes to light them. */
    public static final int COLLAR = 0, CHEST = 1, RIBS = 2, BACK = 3, SHOULDER = 4, UPPER_ARM = 5, FOREARM = 6, HAND = 7, HIPS = 8,
            THIGH = 9, SHIN = 10, MASK = 11;
    private static final float[] THRESHOLD = {.02f, .08f, .22f, .3f, .36f, .48f, .6f, .75f, .3f, .68f, .8f, .92f};
    private static final float[] ORDER = {.62f, .55f, .45f, .5f, .7f, .8f, .9f, 1f, .3f, .15f, 0f, .66f};
    /** When each region goes dark as the energy drains out after a release: the chest first, then the arms, the legs last. */
    private static final float[] DRAIN = {0f, 0f, .05f, .05f, .3f, .38f, .46f, .54f, .62f, .7f, .78f, .1f};
    /** Where a hit on a region starts its pulse along the suit's lines (0 feet .. 1 hands). */
    public static float order(int region) { return ORDER[Mth.clamp(region, 0, ORDER.length - 1)]; }

    /** The light in the suit for the current draw: what is stored, a charge flowing up, a release flash, hits soaking in. */
    public static final class Charge {
        /** drain: 0..1 as the light leaves the suit region by region (chest, arms, legs). */
        public float level, flow = -1, flash, fade = 1, drain;
        public final List<float[]> pulses = new ArrayList<>();   // {age, side, start order}
        float time;
        float brightness(int region, int side) {
            float thr = THRESHOLD[region], order = ORDER[region];
            float b = smooth((level - thr) / .12f) * (.35f + .65f * level);
            if (level > thr) {
                // Energy moving through the suit: a pulse travelling up the lines every couple of seconds.
                float front = (time * .028f) % 1.4f - .2f;
                b += .35f * level * gauss(order - front, .07f);
            }
            if (flow >= 0) b = Math.max(b, smooth((flow * 1.25f - order) * 6) * (.55f + .45f * flow));
            b = Math.max(b, flash);
            for (float[] pulse : pulses) {
                float age = pulse[0];
                float at = Mth.lerp(Math.min(1, age / 7f), pulse[2], .58f);
                float match = side == 0 ? .7f : side == (int) pulse[1] ? 1 : .25f;
                b += Math.max(0, 1 - age / 11f) * match * gauss(order - at, .1f) * 1.2f;
            }
            if (drain > 0) b *= 1 - smooth((drain - DRAIN[region]) / .3f);
            return b * fade;
        }
        public Charge reset() { level = 0; flow = -1; flash = 0; fade = 1; drain = 0; pulses.clear(); return this; }
    }
    private static float smooth(float x) { x = Mth.clamp(x, 0, 1); return x * x * (3 - 2 * x); }
    private static float gauss(float x, float w) { return (float) Math.exp(-(x * x) / (2 * w * w)); }

    public static final Charge CHARGE = new Charge();

    // ------------------------------------------------------------------ the current draw (render thread only)
    private static int mode;
    private static float alpha;
    /** How lit the eyes are this draw (0 subtle .. 1 full white-blue). */
    private static float eyes;
    /** When on, every draw records where the claw tips, the toes and the eyes are in the world. */
    public static boolean capture;
    /** With capture on: the four claw tips of each hand (right then left), each foot's toe, the eyes. */
    public static final Vec3[] CLAWS = new Vec3[8];
    public static Vec3 toeRight, toeLeft, eyeRight, eyeLeft;
    /** How far the claws are out (1 out, 0 drawn into the fingertips). */
    public static float clawLength = 1;
    /** City light caught on the suit's sheen this draw (added to it; the film's neon). */
    public static float[] reflect = {0, 0, 0};

    private PantherBody() {}

    private static void px(PoseStack p, double x, double y, double z) { p.translate(x / 16, y / 16, z / 16); }
    private static void rot(PoseStack p, float x, float y, float z) { p.mulPose(new Quaternionf().rotationZYX(z, y, x)); }

    /** A box in pixels: corner (x, y, z), size (w, h, d). */
    static void box(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        if (w <= .01f || h <= .01f || d <= .01f) return;
        p.pushPose();
        p.translate(x / 16, y / 16, z / 16);
        p.scale(w, h, d);
        if (mode == GHOST) {
            if (alpha > .01f) {
                float lum = .6f + (c[0] + c[1] + c[2]) / 3;
                UNIT.render(p, b.getBuffer(RenderType.entityTranslucent(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY,
                        Math.min(1, tint[0] * lum), Math.min(1, tint[1] * lum), Math.min(1, tint[2] * lum), alpha);
            }
        } else if (mode == CAMO) {
            // Glass that bends the light: barely there, with a ripple of brighter edges running through it.
            float ripple = Math.max(0, Mth.sin(drawTime * .32f + boxIndex++ * .37f));
            float a = alpha * (.1f + .14f * ripple * ripple * ripple);
            if (a > .005f) UNIT.render(p, b.getBuffer(RenderType.entityTranslucent(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
                    .62f + .2f * ripple, .68f + .2f * ripple, .78f + .15f * ripple, a);
        } else UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1);
        p.popPose();
    }
    /** A box centred on (x, y, z), turned (radians, x then y then z), of size (w, h, d). */
    static void part(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float rx, float ry, float rz, float w, float h, float d, float[] c) {
        p.pushPose();
        px(p, x, y, z);
        if (rx != 0 || ry != 0 || rz != 0) rot(p, rx, ry, rz);
        box(p, b, light, -w / 2, -h / 2, -d / 2, w, h, d, c);
        p.popPose();
    }
    /** A full-bright edge: the sheen on the suit, the shine on metal. */
    private static void shine(PoseStack p, MultiBufferSource b, float x, float y, float z, float rx, float ry, float rz, float w, float h, float d, float[] c) {
        part(p, b, FULL, x, y, z, rx, ry, rz, w, h, d, c);
    }
    private static float[] sheen() {
        float l = Math.min(1, CHARGE.level);
        return new float[]{Math.min(1, SHEEN[0] + .1f * l + reflect[0]), Math.min(1, SHEEN[1] + .02f * l + reflect[1]), Math.min(1, SHEEN[2] + .18f * l + reflect[2])};
    }
    /** A line of the suit's energy, over its silver line: dark until the region is charged. */
    private static void energy(PoseStack p, MultiBufferSource b, int region, int side, float x, float y, float z, float rx, float ry, float rz, float w, float h, float d) {
        float g = CHARGE.brightness(region, side) * (mode == GHOST ? .4f * alpha : mode == CAMO ? .15f : 1);
        if (g < .015f) return;
        float core = Math.max(0, g - .8f) * .7f;
        float r = Math.min(1, .55f * g + core), gr = Math.min(1, .26f * g + core), bl = Math.min(1, 1f * g + core);
        p.pushPose();
        px(p, x, y, z);
        if (rx != 0 || ry != 0 || rz != 0) rot(p, rx, ry, rz);
        p.scale(w + .12f, h + .12f, d + .12f);
        p.translate(-.5f / 16, -.5f / 16, -.5f / 16);
        UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, r, gr, bl, 1);
        p.popPose();
    }
    /** A silver line of the suit and its energy line together. */
    private static void line(PoseStack p, MultiBufferSource b, int light, int region, int side, float x, float y, float z, float rx, float ry, float rz, float w, float h, float d) {
        part(p, b, light, x, y, z, rx, ry, rz, w, h, d, SILVER);
        energy(p, b, region, side, x, y, z, rx, ry, rz, w, h, d);
    }
    private static void glowBox(PoseStack p, MultiBufferSource b, float x, float y, float z, float rz, float w, float h, float d, float r, float g, float bl) {
        p.pushPose();
        px(p, x, y, z);
        if (rz != 0) p.mulPose(Axis.ZP.rotation(rz));
        p.scale(w, h, d);
        p.translate(-.5f / 16, -.5f / 16, -.5f / 16);
        UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, r, g, bl, 1);
        p.popPose();
    }

    // ------------------------------------------------------------------ the whole body
    /**
     * Draws him at the model root: the pose (walk cycle already in it), where the head looks (radians,
     * relative to the body), the time, NORMAL or GHOST and its opacity, and align: an extra turn of the
     * whole body about the vertical (radians) for moves whose facing differs from the player's.
     */
    public static void draw(PoseStack p, MultiBufferSource b, int light, Pose pose, float lookYaw, float lookPitch, float time, int drawMode, float opacity, float align) {
        mode = drawMode; alpha = opacity; eyes = pose.get(EYES); boxIndex = 0; drawTime = time;
        CHARGE.time = time;
        float[] v = pose.v;
        p.pushPose();
        // The root: lift, sway, then the whole-body turns about the centre of mass.
        px(p, v[SHIFT_X], -v[LIFT], v[SHIFT_Z]);
        px(p, 0, 11, 0);
        if (align != 0) p.mulPose(Axis.YP.rotation(align));
        if (v[ROOT_PITCH] != 0) p.mulPose(Axis.XP.rotation(v[ROOT_PITCH]));
        if (v[ROOT_YAW] != 0) p.mulPose(Axis.YP.rotation(v[ROOT_YAW]));
        if (v[ROOT_ROLL] != 0) p.mulPose(Axis.ZP.rotation(v[ROOT_ROLL]));
        px(p, 0, -11, 0);

        // ---- pelvis: dropped by the crouch; the legs fold so planted feet stay where they are.
        float drop = Mth.clamp(v[CROUCH], -1, 9.5f);
        float fold = (float) Math.acos(Mth.clamp(1 - Math.max(0, drop) / 10.8f, -1, 1));   // hip to ankle: 6 + 4.8
        float plant = Mth.clamp(v[PLANT], 0, 1);
        p.pushPose();
        px(p, 0, 12 + drop, 0);
        p.mulPose(Axis.YP.rotation(v[PELVIS_YAW])); p.mulPose(Axis.XP.rotation(v[PELVIS_PITCH])); p.mulPose(Axis.ZP.rotation(v[PELVIS_ROLL]));
        for (int side = 0; side < 2; side++) {
            int s = side == 0 ? -1 : 1;
            int o = side == 0 ? RL : LL;
            float legX = v[o + LEG_X] - fold * plant - v[PELVIS_PITCH] * plant, knee = v[o + KNEE] + 2 * fold * plant;
            // Planted, the foot stays flat on the ground whatever the leg does; in the air it does what the pose says.
            float ankle = v[o + ANKLE] - (legX + knee + v[PELVIS_PITCH]) * plant;
            p.pushPose();
            px(p, s * 2.05f, 0, 0);
            rot(p, legX, s < 0 ? v[o + LEG_Y] : -v[o + LEG_Y], s < 0 ? v[o + LEG_Z] : -v[o + LEG_Z]);
            leg(p, b, light, side, knee, ankle);
            p.popPose();
        }
        hips(p, b, light);

        // ---- spine (bending about the pelvis), then the chest.
        p.mulPose(Axis.YP.rotation(v[SPINE_YAW])); p.mulPose(Axis.XP.rotation(v[SPINE_PITCH])); p.mulPose(Axis.ZP.rotation(v[SPINE_ROLL]));
        abdomen(p, b, light);
        px(p, 0, -5.6f, 0);
        p.mulPose(Axis.YP.rotation(v[CHEST_YAW])); p.mulPose(Axis.XP.rotation(v[CHEST_PITCH])); p.mulPose(Axis.ZP.rotation(v[CHEST_ROLL]));
        chest(p, b, light, time);
        for (int side = 0; side < 2; side++) {
            int s = side == 0 ? -1 : 1;
            int o = side == 0 ? R : L;
            p.pushPose();
            px(p, s * 5.0f, -5.4f - v[o + SH_UP] * .6f, -v[o + SH_FWD] * .6f);
            rot(p, v[o + ARM_X], s < 0 ? v[o + ARM_Y] : -v[o + ARM_Y], s < 0 ? v[o + ARM_Z] : -v[o + ARM_Z]);
            arm(p, b, light, side, v[o + ELBOW], v[o + WRIST_X], v[o + WRIST_Z], v[o + CURL]);
            p.popPose();
        }
        // ---- head: on a neck reaching forward; it keeps looking where the player looks.
        p.pushPose();
        px(p, 0, -6.6f, -v[NECK]);
        p.mulPose(Axis.YP.rotation(lookYaw + v[HEAD_YAW]));
        p.mulPose(Axis.XP.rotation(Mth.clamp(lookPitch + v[HEAD_PITCH], -1.4f, 1.3f)));
        p.mulPose(Axis.ZP.rotation(v[HEAD_ROLL]));
        head(p, b, light, time);
        p.popPose();
        p.popPose();
        p.popPose();
    }

    // ------------------------------------------------------------------ legs
    private static void leg(PoseStack p, MultiBufferSource b, int light, int side, float knee, float ankle) {
        int s = side == 0 ? -1 : 1;
        float[] hi = sheen();
        // Thigh: full at the top, tapering to the knee; the quad panel in front, silver along its outside.
        part(p, b, light, 0, 1.8f, 0, 0, 0, 0, 4.3f, 3.6f, 4.3f, SUIT);
        part(p, b, light, 0, 4.4f, 0, 0, 0, 0, 3.8f, 3.4f, 3.9f, SUIT);
        part(p, b, light, s * -.2f, 2.9f, -2.05f, -.05f, 0, 0, 2.6f, 4.4f, .5f, SUIT_PANEL);
        part(p, b, light, s * 1.3f, 3.1f, -.2f, 0, 0, 0, .6f, 4.8f, 3.4f, SUIT_PANEL);
        shine(p, b, s * -.2f, .8f, -2.25f, 0, 0, 0, 2.4f, .22f, .2f, hi);
        line(p, b, light, THIGH, s, s * 1.15f, 3.2f, -2.2f, 0, 0, s * .05f, .26f, 4.8f, .2f);
        line(p, b, light, THIGH, s, s * -1.4f, 3.6f, -2.2f, 0, 0, s * -.12f, .22f, 3.6f, .2f);
        // The knee plate, ringed in silver.
        part(p, b, light, 0, 6.0f, -1.95f, 0, 0, 0, 2.8f, 2.1f, .7f, SUIT_PANEL);
        line(p, b, light, THIGH, s, 0, 4.95f, -2.32f, 0, 0, 0, 2.6f, .2f, .2f);
        shine(p, b, 0, 5.25f, -2.33f, 0, 0, 0, 2.2f, .18f, .18f, hi);
        px(p, 0, 6, 0);
        p.mulPose(Axis.XP.rotation(knee));
        // Shin: the calf behind, a raised panel in front with silver down its middle.
        part(p, b, light, 0, 2.4f, 0, 0, 0, 0, 3.7f, 4.8f, 3.6f, SUIT);
        part(p, b, light, 0, 1.8f, 1.35f, 0, 0, 0, 3.2f, 2.8f, 1.2f, SUIT_PANEL);
        part(p, b, light, 0, 2.5f, -1.85f, 0, 0, 0, 2.2f, 4.2f, .5f, SUIT_PANEL);
        line(p, b, light, SHIN, s, 0, 2.6f, -2.12f, 0, 0, 0, .3f, 4.0f, .2f);
        line(p, b, light, SHIN, s, s * .9f, 1.2f, -2.1f, 0, 0, s * .5f, .2f, 1.6f, .2f);
        shine(p, b, 0, .6f, -2.12f, 0, 0, 0, 1.8f, .18f, .18f, hi);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(ankle));
        // The boot: shaped toe, a silver band round the ankle.
        part(p, b, light, 0, .7f, -.7f, 0, 0, 0, 3.5f, 1.4f, 5.2f, SUIT_DEEP);
        part(p, b, light, 0, .55f, -3.2f, .12f, 0, 0, 3.0f, 1.1f, 1.4f, SUIT_PANEL);
        part(p, b, light, 0, -.15f, .1f, 0, 0, 0, 3.8f, .35f, 3.8f, SILVER_DARK);
        shine(p, b, 0, .05f, -2.4f, 0, 0, 0, 2.6f, .16f, .16f, hi);
        if (capture) { Vec3 toe = world(p, 0, .8f, -3.9f); if (side == 0) toeRight = toe; else toeLeft = toe; }
    }

    // ------------------------------------------------------------------ hips and abdomen
    private static void hips(PoseStack p, MultiBufferSource b, int light) {
        part(p, b, light, 0, .4f, 0, 0, 0, 0, 7.6f, 3.6f, 4.4f, SUIT);
        part(p, b, light, 0, 1.5f, -2.25f, 0, 0, 0, 3.2f, 2.4f, .4f, SUIT_PANEL);
        // Silver lines in a V down to the middle, over the hips.
        for (int side = -1; side <= 1; side += 2) {
            line(p, b, light, HIPS, side, side * 1.9f, .6f, -2.3f, 0, 0, side * .55f, .24f, 3.4f, .2f);
            part(p, b, light, side * 3.75f, .3f, 0, 0, 0, side * .1f, .5f, 3.2f, 4.0f, SUIT_PANEL);
        }
    }
    private static void abdomen(PoseStack p, MultiBufferSource b, int light) {
        float[] hi = sheen();
        part(p, b, light, 0, -3.1f, 0, 0, 0, 0, 6.8f, 5.6f, 4.0f, SUIT);
        // Abdominals: three rows of two, the obliques angled in at the sides.
        for (int row = 0; row < 3; row++) for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 1.15f, -1.2f - row * 1.6f, -2.1f, 0, 0, 0, 1.9f, 1.35f, .45f, SUIT_PANEL);
            shine(p, b, side * 1.15f, -1.85f - row * 1.6f, -2.32f, 0, 0, 0, 1.6f, .12f, .12f, hi);
        }
        for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 3.1f, -2.9f, -1.0f, 0, side * -.35f, side * -.1f, 1.2f, 4.8f, 2.4f, SUIT_PANEL);
            line(p, b, light, RIBS, side, side * 2.75f, -3.0f, -2.05f, 0, 0, side * -.35f, .22f, 4.4f, .2f);
            energy(p, b, RIBS, side, side * 3.5f, -2.8f, 0, 0, 0, side * -.15f, .2f, 4.0f, .2f);
        }
        part(p, b, light, 0, -3.1f, -2.2f, 0, 0, 0, .3f, 5.0f, .2f, SILVER_DARK);
        // The lower back.
        part(p, b, light, 0, -3.0f, 2.05f, 0, 0, 0, 6.2f, 5.0f, .4f, SUIT_PANEL);
        line(p, b, light, BACK, 0, 0, -3.2f, 2.3f, 0, 0, 0, .3f, 4.6f, .2f);
    }

    // ------------------------------------------------------------------ chest, back, necklace
    private static void chest(PoseStack p, MultiBufferSource b, int light, float time) {
        float[] hi = sheen();
        part(p, b, light, 0, -3.3f, 0, 0, 0, 0, 8.4f, 6.6f, 4.8f, SUIT);
        // Lats flaring wider toward the shoulders.
        for (int side = -1; side <= 1; side += 2) part(p, b, light, side * 4.3f, -3.8f, .4f, 0, 0, side * -.18f, 1.0f, 5.2f, 3.8f, SUIT_PANEL);
        // Pectorals, their upper edges catching the light.
        for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 2.0f, -4.2f, -2.55f, -.06f, side * -.1f, 0, 3.8f, 3.1f, .75f, SUIT_PANEL);
            shine(p, b, side * 2.0f, -5.7f, -2.85f, 0, side * -.1f, 0, 3.4f, .2f, .2f, hi);
            shine(p, b, side * 3.85f, -4.2f, -2.75f, 0, 0, 0, .18f, 2.6f, .18f, hi);
        }
        // The Wakandan pattern: silver chevrons from the shoulders to the sternum, a line down the middle,
        // a band under the pectorals; triangles between.
        for (int side = -1; side <= 1; side += 2) {
            line(p, b, light, CHEST, side, side * 2.1f, -4.6f, -2.98f, 0, 0, side * -.95f, .24f, 4.6f, .2f);
            line(p, b, light, CHEST, side, side * 2.2f, -2.45f, -2.95f, 0, 0, side * .32f, .22f, 2.9f, .2f);
            line(p, b, light, CHEST, side, side * 1.6f, -5.6f, -2.98f, 0, 0, side * .62f, .2f, 1.9f, .2f);
            line(p, b, light, SHOULDER, side, side * 3.6f, -6.3f, -1.4f, 0, 0, side * 1.25f, .22f, 2.2f, .2f);
        }
        line(p, b, light, CHEST, 0, 0, -3.6f, -2.98f, 0, 0, 0, .26f, 5.0f, .2f);
        line(p, b, light, CHEST, 0, 0, -.9f, -2.75f, 0, 0, 0, 6.2f, .22f, .2f);
        // Traps sloping up to the neck.
        for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 2.4f, -6.6f, .3f, 0, 0, side * .32f, 3.2f, 1.2f, 3.6f, SUIT);
            shine(p, b, side * 2.4f, -7.15f, .3f, 0, 0, side * .32f, 2.9f, .14f, 3.2f, hi);
        }
        // The back: two great muscle panels either side of the spine, a silver chevron down it.
        for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 1.9f, -3.6f, 2.5f, .05f, side * .12f, 0, 3.4f, 5.4f, .6f, SUIT_PANEL);
            shine(p, b, side * 1.9f, -6.2f, 2.82f, 0, side * .12f, 0, 3.0f, .16f, .16f, hi);
            line(p, b, light, BACK, side, side * 1.6f, -4.0f, 2.86f, 0, 0, side * .7f, .22f, 4.4f, .2f);
        }
        line(p, b, light, BACK, 0, 0, -3.3f, 2.88f, 0, 0, 0, .26f, 6.2f, .2f);
        necklace(p, b, light, time);
    }
    /** The Vibranium necklace: a band round the collar hung with silver fangs, the middle ones longest. */
    private static void necklace(PoseStack p, MultiBufferSource b, int light, float time) {
        float rx = 3.45f, rz = 2.7f, y = -6.35f;
        for (int i = 0; i < 18; i++) {
            float a0 = Mth.TWO_PI * i / 18, a1 = Mth.TWO_PI * (i + 1) / 18, am = (a0 + a1) / 2;
            float x = Mth.sin(am) * rx, z = -Mth.cos(am) * rz;
            float len = (float) Math.hypot(Mth.sin(a1) * rx - Mth.sin(a0) * rx, Mth.cos(a1) * rz - Mth.cos(a0) * rz);
            part(p, b, light, x, y, z, 0, -am, 0, len + .1f, .55f, .5f, SILVER_DARK);
            energy(p, b, COLLAR, x < -.5f ? -1 : x > .5f ? 1 : 0, x, y, z, 0, -am, 0, len, .3f, .3f);
        }
        // Fangs over the front half, pointing down, the middle ones longest; each with a polished edge.
        for (int i = -5; i <= 5; i++) {
            float am = i * .26f;
            float x = Mth.sin(am) * (rx + .15f), z = -Mth.cos(am) * (rz + .15f);
            float len = 1.7f - Math.abs(i) * .1f;
            part(p, b, light, x, y + .3f + len / 2, z, -.18f, -am, 0, .62f, len, .42f, SILVER);
            part(p, b, light, x, y + .35f + len, z - .02f, -.18f, -am, 0, .3f, .5f, .32f, SILVER);
            shine(p, b, x, y + .3f + len / 2, z - .22f, -.18f, -am, 0, .16f, len * .9f, .1f, CLAW);
        }
    }

    // ------------------------------------------------------------------ arms and hands
    private static void arm(PoseStack p, MultiBufferSource b, int light, int side, float elbow, float wristX, float wristZ, float curl) {
        int s = side == 0 ? -1 : 1;
        float[] hi = sheen();
        // The deltoid cap and the upper arm, the bicep panel in front; silver curving over the shoulder.
        part(p, b, light, s * .2f, -.1f, 0, 0, 0, 0, 4.4f, 3.4f, 4.4f, SUIT);
        part(p, b, light, s * .1f, -1.85f, 0, 0, 0, 0, 3.6f, .7f, 3.8f, SUIT_PANEL);
        shine(p, b, s * .1f, -2.25f, 0, 0, 0, 0, 3.2f, .14f, 3.4f, hi);
        part(p, b, light, 0, 3.1f, 0, 0, 0, 0, 3.5f, 4.4f, 3.5f, SUIT);
        part(p, b, light, 0, 2.9f, -1.85f, 0, 0, 0, 2.2f, 2.8f, .5f, SUIT_PANEL);
        shine(p, b, 0, 1.55f, -2.07f, 0, 0, 0, 1.9f, .14f, .14f, hi);
        line(p, b, light, SHOULDER, s, s * 2.25f, -.6f, -.8f, 0, 0, s * -.5f, .22f, 2.4f, .2f);
        line(p, b, light, SHOULDER, s, s * 1.4f, -1.95f, -1.6f, 0, 0, s * 1.1f, .2f, 2.0f, .2f);
        line(p, b, light, UPPER_ARM, s, s * 1.8f, 3.0f, 0, 0, 0, 0, .2f, 3.8f, .26f);
        energy(p, b, UPPER_ARM, s, s * -1.0f, 3.2f, -1.8f, 0, 0, 0, .2f, 3.0f, .2f);
        px(p, 0, 5.0f, 0);
        p.mulPose(Axis.XP.rotation(-elbow));
        // The forearm and its Wakandan bracer: chevrons along the outside, rings at the wrist.
        part(p, b, light, 0, 1.3f, 0, 0, 0, 0, 3.4f, 2.8f, 3.4f, SUIT);
        part(p, b, light, 0, 3.6f, 0, 0, 0, 0, 3.0f, 2.2f, 3.0f, SUIT);
        part(p, b, light, s * 1.55f, 2.3f, 0, 0, 0, 0, .5f, 3.8f, 2.4f, SUIT_PANEL);
        for (int i = 0; i < 3; i++) {
            line(p, b, light, FOREARM, s, s * 1.85f, 1.0f + i * 1.25f, -.5f, .6f, 0, 0, .2f, 1.3f, .22f);
            line(p, b, light, FOREARM, s, s * 1.85f, 1.0f + i * 1.25f, .5f, -.6f, 0, 0, .2f, 1.3f, .22f);
        }
        part(p, b, light, 0, 4.55f, 0, 0, 0, 0, 3.25f, .4f, 3.25f, SILVER_DARK);
        line(p, b, light, FOREARM, s, 0, 2.4f, -1.62f, 0, 0, 0, .22f, 3.6f, .2f);
        shine(p, b, 0, .2f, -1.75f, 0, 0, 0, 2.6f, .14f, .14f, hi);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(wristX));
        p.mulPose(Axis.ZP.rotation(s < 0 ? wristZ : -wristZ));
        hand(p, b, light, side, curl);
    }
    /**
     * The gloved hand, palm toward the body: four fingers in a row front to back, each curling toward the
     * palm in two joints, a curved metal claw out of each fingertip; the thumb in front.
     */
    private static void hand(PoseStack p, MultiBufferSource b, int light, int side, float curl) {
        int s = side == 0 ? -1 : 1;
        float[] hi = sheen();
        part(p, b, light, 0, 1.0f, 0, 0, 0, 0, 1.5f, 2.1f, 2.9f, SUIT_DEEP);
        part(p, b, light, s * .55f, .9f, 0, 0, 0, 0, .4f, 1.6f, 2.4f, SUIT_PANEL);
        line(p, b, light, HAND, s, s * .8f, 1.0f, 0, 0, 0, 0, .18f, 1.4f, .2f);
        float bend = s * curl;      // toward the palm (the body's side)
        for (int f = 0; f < 4; f++) {
            float z = -1.08f + f * .72f;
            float len = f == 0 || f == 3 ? .85f : 1f;
            p.pushPose();
            px(p, 0, 2.05f, z);
            p.mulPose(Axis.ZP.rotation(bend * 1.1f + s * .08f));
            part(p, b, light, 0, .55f * len, 0, 0, 0, 0, .75f, 1.1f * len, .6f, SUIT_DEEP);
            px(p, 0, 1.1f * len, 0);
            p.mulPose(Axis.ZP.rotation(bend * .9f));
            part(p, b, light, 0, .45f * len, 0, 0, 0, 0, .65f, .9f * len, .55f, SUIT_DEEP);
            px(p, 0, .9f * len, 0);
            // The claw: a curved blade in two pieces, a polished edge on the inside of the curve.
            p.mulPose(Axis.ZP.rotation(bend * .4f + s * .15f));
            float out = Mth.clamp(clawLength, 0, 1);
            if (out > .02f) {
                part(p, b, light, 0, .5f * out, 0, 0, 0, 0, .42f, 1.0f * out, .46f, CLAW);
                shine(p, b, s * -.16f, .5f * out, 0, 0, 0, 0, .1f, .95f * out, .3f, CLAW);
                px(p, 0, 1.0f * out, 0);
                p.mulPose(Axis.ZP.rotation(s * .35f * out));
                part(p, b, light, 0, .4f * out, 0, 0, 0, 0, .28f, .8f * out, .34f, CLAW);
                shine(p, b, s * -.1f, .35f * out, 0, 0, 0, 0, .08f, .7f * out, .2f, CLAW);
            }
            if (capture) CLAWS[side * 4 + f] = world(p, 0, .85f * out, 0);
            p.popPose();
        }
        // The thumb, in front of the fingers.
        p.pushPose();
        px(p, s * -.2f, 1.2f, -1.55f);
        p.mulPose(Axis.XP.rotation(-.5f - .5f * curl));
        part(p, b, light, 0, .6f, 0, 0, 0, 0, .65f, 1.3f, .65f, SUIT_DEEP);
        part(p, b, light, 0, 1.45f, 0, 0, 0, 0, .3f, .6f, .35f, CLAW);
        p.popPose();
        shine(p, b, 0, -.05f, -1.4f, 0, 0, 0, 1.2f, .12f, .12f, hi);
    }

    // ------------------------------------------------------------------ head: the mask
    private static void head(PoseStack p, MultiBufferSource b, int light, float time) {
        float[] hi = sheen();
        part(p, b, light, 0, .2f, 0, 0, 0, 0, 3.6f, 2.2f, 3.6f, SUIT);
        // The skull of the mask, narrower toward the top, a muzzle in front, the jaw.
        part(p, b, light, 0, -4.8f, .1f, 0, 0, 0, 7.4f, 7.4f, 7.6f, SUIT);
        part(p, b, light, 0, -8.75f, .4f, 0, 0, 0, 6.6f, .9f, 6.8f, SUIT);
        shine(p, b, 0, -9.25f, .4f, 0, 0, 0, 5.8f, .14f, 6f, hi);
        part(p, b, light, 0, -2.55f, -3.85f, 0, 0, 0, 4.8f, 3.1f, 1.3f, SUIT_PANEL);
        part(p, b, light, 0, -1.0f, -.4f, 0, 0, 0, 5.8f, 1.4f, 6.4f, SUIT);
        part(p, b, light, 0, -3.95f, -4.42f, 0, 0, 0, 2.0f, 1.1f, .3f, SUIT_DEEP);
        // The ears: low points on the top corners, tipped out.
        for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 2.65f, -9.35f, .3f, 0, 0, side * .3f, 1.6f, 1.6f, 1.2f, SUIT);
            part(p, b, light, side * 2.95f, -10.2f, .3f, 0, 0, side * .45f, .8f, 1.0f, .9f, SUIT);
            shine(p, b, side * 2.55f, -9.9f, -.32f, 0, 0, side * .3f, .14f, 1.2f, .12f, hi);
        }
        // The brow: two ridges in a V over the eyes, sheen along them.
        for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 1.7f, -6.15f, -3.85f, 0, 0, side * -.3f, 3.0f, .9f, .6f, SUIT_PANEL);
            shine(p, b, side * 1.7f, -6.55f, -4.12f, 0, 0, side * -.3f, 2.8f, .12f, .12f, hi);
        }
        // The eyes: angular lenses, the outer corners lifted; pale silver, lit white-blue when the power is up.
        for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 1.65f, -5.25f, -3.95f, 0, 0, side * -.28f, 2.3f, .8f, .25f, LENS);
            float e = Mth.clamp(eyes, 0, 1.5f);
            float glow = (mode == GHOST ? .6f * alpha : mode == CAMO ? .35f : 1) * (.08f + .92f * e);
            glowBox(p, b, side * 1.65f, -5.25f, -4.1f, side * -.28f, 2.2f, .7f, .15f, .85f * glow, .9f * glow, glow);
        }
        // The silver lines of the mask: down the forehead to the nose, round the eyes and down the cheeks,
        // framing the muzzle, back over the top of the head.
        line(p, b, light, MASK, 0, 0, -7.3f, -3.92f, 0, 0, 0, .28f, 3.4f, .2f);
        line(p, b, light, MASK, 0, 0, -4.6f, -4.52f, 0, 0, 0, .26f, 1.6f, .2f);
        for (int side = -1; side <= 1; side += 2) {
            line(p, b, light, MASK, side, side * 3.0f, -4.4f, -3.92f, 0, 0, side * .35f, .22f, 2.6f, .2f);
            line(p, b, light, MASK, side, side * 2.3f, -2.6f, -4.52f, 0, 0, 0, .22f, 2.6f, .2f);
            line(p, b, light, MASK, side, side * 1.2f, -4.05f, -4.52f, 0, 0, side * 1.2f, .2f, 1.4f, .2f);
            line(p, b, light, MASK, side, side * 1.0f, -8.2f, -3.92f, 0, 0, side * -.5f, .2f, 2.2f, .2f);
            line(p, b, light, MASK, side, side * 1.3f, -9.24f, .2f, 0, 0, 0, .22f, .2f, 6.4f);
        }
        line(p, b, light, MASK, 0, 0, -1.05f, -4.52f, 0, 0, 0, 3.6f, .2f, .2f);
        if (capture) {
            eyeRight = world(p, -1.65f, -5.25f, -4.3f);
            eyeLeft = world(p, 1.65f, -5.25f, -4.3f);
        }
    }

    // ------------------------------------------------------------------ first person: the arms in front of the camera
    /** One arm for the first-person view, from the elbow down (the pose stack is at the elbow, forearm along +y). */
    public static void firstPersonArm(PoseStack p, MultiBufferSource b, int light, int side, float wristX, float wristZ, float curl, boolean camo, float time) {
        mode = camo ? CAMO : NORMAL; alpha = camo ? 1.6f : 1; boxIndex = side * 40; drawTime = time;
        int s = side == 0 ? -1 : 1;
        float[] hi = sheen();
        part(p, b, light, 0, 1.3f, 0, 0, 0, 0, 3.4f, 2.8f, 3.4f, SUIT);
        part(p, b, light, 0, 3.6f, 0, 0, 0, 0, 3.0f, 2.2f, 3.0f, SUIT);
        part(p, b, light, s * 1.55f, 2.3f, 0, 0, 0, 0, .5f, 3.8f, 2.4f, SUIT_PANEL);
        for (int i = 0; i < 3; i++) {
            line(p, b, light, FOREARM, s, s * 1.85f, 1.0f + i * 1.25f, -.5f, .6f, 0, 0, .2f, 1.3f, .22f);
            line(p, b, light, FOREARM, s, s * 1.85f, 1.0f + i * 1.25f, .5f, -.6f, 0, 0, .2f, 1.3f, .22f);
        }
        part(p, b, light, 0, 4.55f, 0, 0, 0, 0, 3.25f, .4f, 3.25f, SILVER_DARK);
        shine(p, b, 0, .2f, -1.75f, 0, 0, 0, 2.6f, .14f, .14f, hi);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(wristX));
        p.mulPose(Axis.ZP.rotation(s < 0 ? wristZ : -wristZ));
        boolean was = capture;
        capture = false;
        hand(p, b, light, side, curl);
        capture = was;
    }
    /** The claw tips of a first-person hand, in the current frame's view space (for their trails). */
    public static Vec3 clawTip(PoseStack p, int side, int finger, float curl) {
        int s = side == 0 ? -1 : 1;
        float bend = s * curl;
        float len = finger == 0 || finger == 3 ? .85f : 1f;
        p.pushPose();
        px(p, 0, 2.05f, -1.08f + finger * .72f);
        p.mulPose(Axis.ZP.rotation(bend * 1.1f + s * .08f));
        px(p, 0, 1.1f * len, 0);
        p.mulPose(Axis.ZP.rotation(bend * .9f));
        px(p, 0, .9f * len, 0);
        p.mulPose(Axis.ZP.rotation(bend * .4f + s * .15f));
        px(p, 0, 1.0f, 0);
        p.mulPose(Axis.ZP.rotation(s * .35f));
        Vector4f v = new Vector4f(0, .85f / 16, 0, 1).mul(p.last().pose());
        p.popPose();
        return new Vec3(v.x, v.y, v.z);
    }

    /** Where a point of the current model space is in the world (through the inverse view rotation of this frame). */
    static Vec3 world(PoseStack p, float x, float y, float z) {
        Vector4f v = new Vector4f(x / 16, y / 16, z / 16, 1).mul(p.last().pose());
        Vector3f w = new Vector3f(v.x, v.y, v.z);
        RenderSystem.getInverseViewRotationMatrix().transform(w);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return cam.add(w.x, w.y, w.z);
    }
}
