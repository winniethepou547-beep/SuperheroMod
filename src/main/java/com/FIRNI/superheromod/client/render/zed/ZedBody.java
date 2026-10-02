package com.FIRNI.superheromod.client.render.zed;

import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Zed's body, built of boxes, for himself and for his shadows: lean and athletic; dark steel plates
 * over a black suit; crimson cloth (a tabard front and back, a sash); layered pauldrons, the left one
 * crowned by a great curved blade; a four-pointed shuriken on his back; long twin blades along each
 * forearm; a tall crested mask with a narrow red eye slit. In SHADOW mode the same body is drawn
 * almost black and see-through, with faint red-violet lines running through it and flickering edges.
 * Model space: pixels/16, +y down, -z in front, -x is his right; the root is the player model's.
 */
public final class ZedBody {
    public static final int NORMAL = 0, SHADOW = 1;
    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 1, 1, 1);
    private static final int FULL = 15728880;
    static final float[] BLACK = {.07f, .07f, .09f}, STEEL = {.2f, .21f, .24f}, STEEL_LIGHT = {.34f, .35f, .39f}, SILVER = {.66f, .68f, .73f},
            RED = {.44f, .05f, .07f}, RED_DARK = {.27f, .03f, .05f}, PANTS = {.1f, .1f, .15f}, LEATHER = {.16f, .1f, .08f}, EYE = {1f, .16f, .12f};

    // The state of the current draw (render thread only).
    private static int mode;
    private static float alpha, flicker;

    private ZedBody() {}

    /** Back to drawing solid (for pieces drawn outside draw(), like the first-person gauntlets). */
    static void solid() { mode = NORMAL; alpha = 1; flicker = 1; }

    private static float l(float a, float b, float k) { return a + (b - a) * k; }
    private static void px(PoseStack p, double x, double y, double z) { p.translate(x / 16, y / 16, z / 16); }
    private static void rot(PoseStack p, float x, float y, float z) { p.mulPose(new Quaternionf().rotationZYX(z, y, x)); }

    /** A box in pixels: corner (x, y, z), size (w, h, d). */
    static void box(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        if (w <= .01f || h <= .01f || d <= .01f) return;
        p.pushPose();
        p.translate(x / 16, y / 16, z / 16);
        p.scale(w, h, d);
        if (mode == SHADOW) {
            float lum = (c[0] + c[1] + c[2]) / 3;
            UNIT.render(p, b.getBuffer(RenderType.entityTranslucent(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
                    .05f + lum * .12f, .03f + lum * .06f, .08f + lum * .14f, alpha * flicker);
        } else UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1);
        p.popPose();
    }
    static void centred(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        box(p, b, light, x - w / 2, y, z - d / 2, w, h, d, c);
    }
    /** A box that glows (the eyes; in a shadow, the lines of energy running through it). */
    static void glow(PoseStack p, MultiBufferSource b, float x, float y, float z, float w, float h, float d, float r, float g, float bl) {
        p.pushPose();
        p.translate(x / 16, y / 16, z / 16);
        p.scale(w, h, d);
        float a = mode == SHADOW ? alpha : 1;
        UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, r * a, g * a, bl * a, 1);
        p.popPose();
    }

    /**
     * Draws him at the model root: pose, the walk cycle (walk, amount), whether he is on the move,
     * where the head looks (radians, relative to the body), the time, NORMAL or SHADOW and its opacity.
     */
    public static void draw(PoseStack p, MultiBufferSource b, int light, ZedMotion.Pose pose, float walk, float amount,
                            float headYaw, float headPitch, float time, int drawMode, float opacity) {
        mode = drawMode; alpha = opacity;
        flicker = drawMode == SHADOW ? .85f + .15f * (float) Math.sin(time * 2.1) : 1;
        p.pushPose();
        // A runner's lean when he is moving quickly.
        float lean = pose.bodyPitch + .22f * Math.min(1, amount);
        px(p, 0, 12, 0); p.mulPose(Axis.XP.rotation(lean)); px(p, 0, -12, 0);

        float drop = Mth.clamp(pose.crouch, -1, 10);
        float fold = (float) Math.acos(Mth.clamp(1 - Math.max(0, drop) / 12f, -1, 1));
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side < 0;
            float lx = right ? pose.rLegX : pose.lLegX, lz = right ? pose.rLegZ : pose.lLegZ, knee = right ? pose.rKnee : pose.lKnee;
            // Short quick steps.
            float sw = Mth.cos(walk * .8f + (right ? 0 : Mth.PI)) * 1.05f * amount;
            lx += sw; knee += Math.max(0, -Mth.sin(walk * .8f + (right ? 0 : Mth.PI))) * .7f * amount;
            p.pushPose();
            px(p, side * 1.95f, 12 + drop, 0);
            rot(p, lx - fold, 0, lz);
            leg(p, b, light, side, knee + 2 * fold);
            p.popPose();
        }
        px(p, 0, drop, 0);
        px(p, 0, 12, 0);
        p.mulPose(Axis.YP.rotation(pose.torsoYaw)); p.mulPose(Axis.XP.rotation(pose.torsoPitch)); p.mulPose(Axis.ZP.rotation(pose.torsoRoll));
        px(p, 0, -12, 0);
        torso(p, b, light, time, amount);
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side < 0;
            float ax = right ? pose.rArmX : pose.lArmX, ay = right ? pose.rArmY : pose.lArmY, az = right ? pose.rArmZ : pose.lArmZ, elbow = right ? pose.rElbow : pose.lElbow;
            ax += Mth.cos(walk * .8f + (right ? Mth.PI : 0)) * .45f * amount;
            p.pushPose();
            px(p, side * 5.2f, 2, 0);
            rot(p, ax, ay, az);
            upperArm(p, b, light, side);
            px(p, 0, 4, 0);
            p.mulPose(Axis.XP.rotation(-elbow));
            forearm(p, b, light, side, pose.glow, time);
            p.popPose();
        }
        p.pushPose();
        p.mulPose(Axis.YP.rotation(headYaw - pose.torsoYaw + pose.headYaw));
        p.mulPose(Axis.XP.rotation(Mth.clamp(headPitch + pose.headPitch - pose.torsoPitch * .5f - lean * .7f, -1.2f, 1.1f)));
        head(p, b, light, time);
        p.popPose();
        p.popPose();
    }

    private static void leg(PoseStack p, MultiBufferSource b, int light, int side, float knee) {
        // Loose dark trousers to the knee, a steel knee plate.
        centred(p, b, light, 0, 0, 0, 4.3f, 6.2f, 4.3f, PANTS);
        centred(p, b, light, side * .3f, 1.5f, -2.2f, 3f, 3.5f, .4f, PANTS);
        centred(p, b, light, 0, 5.2f, -2.45f, 3.2f, 2.2f, .7f, STEEL_LIGHT);
        px(p, 0, 6, 0);
        p.mulPose(Axis.XP.rotation(knee));
        // Armoured greaves and boots.
        centred(p, b, light, 0, 0, 0, 4.1f, 6, 4.1f, PANTS);
        centred(p, b, light, 0, .8f, -.15f, 4.4f, 4.4f, 4.4f, STEEL);
        centred(p, b, light, 0, 1.2f, -2.4f, 2.4f, 3.4f, .5f, STEEL_LIGHT);
        centred(p, b, light, 0, 4.6f, -.8f, 4.3f, 1.6f, 5.8f, BLACK);
    }

    private static void torso(PoseStack p, MultiBufferSource b, int light, float time, float amount) {
        centred(p, b, light, 0, 0, 0, 8, 12, 4, BLACK);
        // Chest plates: two overlapping layers, a red line down the middle and along the bottom.
        centred(p, b, light, 0, .3f, -.3f, 8.6f, 5.8f, 4.7f, STEEL);
        centred(p, b, light, 0, 1, -2.75f, 7.4f, 3.6f, .5f, STEEL_LIGHT);
        centred(p, b, light, 0, .5f, -2.95f, .6f, 5.4f, .3f, RED);
        centred(p, b, light, 0, 5.9f, -2.7f, 8.4f, .5f, .4f, RED_DARK);
        for (int r = 0; r < 3; r++) centred(p, b, light, 0, 6.6f + r * 1.1f, -.1f, 7.4f - r * .4f, 1f, 4.4f, r % 2 == 0 ? STEEL : BLACK);
        // Tall collar.
        centred(p, b, light, 0, -1.4f, .2f, 6.4f, 1.8f, 4.6f, STEEL);
        // Sash and belt.
        centred(p, b, light, 0, 9.5f, 0, 8.6f, 1.4f, 4.6f, RED_DARK);
        centred(p, b, light, 0, 10.6f, 0, 8.8f, .8f, 4.8f, LEATHER);
        for (int i = -1; i <= 1; i += 2) centred(p, b, light, i * 2.6f, 10.4f, -2.5f, 1.2f, 1.2f, .3f, SILVER);
        // Crimson tabard, front and back, swinging a little as he moves.
        float sway = (float) Math.sin(time * .2) * .04f + .35f * Math.min(1, amount);
        p.pushPose(); px(p, 0, 11.2f, -2.45f); p.mulPose(Axis.XP.rotation(-sway * .6f));
        centred(p, b, light, 0, 0, 0, 4.6f, 7.5f, .35f, RED);
        centred(p, b, light, 0, 7.3f, 0, 3.6f, .8f, .35f, RED_DARK);
        p.popPose();
        p.pushPose(); px(p, 0, 10.8f, 2.45f); p.mulPose(Axis.XP.rotation(sway));
        centred(p, b, light, 0, 0, 0, 6.6f, 8.5f, .35f, RED);
        for (int i = 0; i < 3; i++) centred(p, b, light, (i - 1) * 2.2f, 8.4f, 0, 2f, .8f + .6f * (i % 2), .35f, RED_DARK);
        p.popPose();
        // The great four-pointed shuriken worn on his back.
        p.pushPose(); px(p, 0, 3.8f, 3.1f);
        for (int i = 0; i < 4; i++) {
            p.pushPose(); p.mulPose(Axis.ZP.rotation((float) (i * Math.PI / 2 + Math.PI / 4)));
            centred(p, b, light, 0, -7, 0, 1.6f, 5.5f, .5f, SILVER);
            centred(p, b, light, .5f, -9.2f, 0, .8f, 2.4f, .5f, SILVER);
            p.popPose();
        }
        centred(p, b, light, 0, -1.5f, 0, 3, 3, .9f, STEEL);
        if (mode == SHADOW) glow(p, b, -.4f, -.4f, -.6f, .8f, .8f, .3f, .55f, .1f, .35f);
        p.popPose();
        if (mode == SHADOW) {
            // Lines of red-violet energy through the shadow's chest.
            glow(p, b, -.2f, .6f, -2.6f, .4f, 7, .2f, .4f, .06f, .3f);
            glow(p, b, -3.2f, 5.6f, -2.6f, 6.4f, .3f, .2f, .35f, .05f, .3f);
        }
    }

    private static void upperArm(PoseStack p, MultiBufferSource b, int light, int side) {
        centred(p, b, light, 0, -2, 0, 3.6f, 6, 3.6f, BLACK);
        // Layered pauldron, red cloth showing under it.
        centred(p, b, light, side * .4f, -3.4f, 0, 5.4f, 2.6f, 5.2f, STEEL);
        centred(p, b, light, side * .6f, -1.6f, 0, 5f, 1.6f, 4.8f, STEEL_LIGHT);
        centred(p, b, light, side * .3f, -.2f, 0, 4.4f, 1.6f, 4.4f, RED);
        if (side > 0) {
            // His left shoulder carries the great curved blade, sweeping up and out.
            p.pushPose(); px(p, 1.6f, -4.2f, .6f);
            float[] angles = {.25f, .6f, 1f, 1.45f};
            float y = 0;
            for (int i = 0; i < angles.length; i++) {
                p.pushPose(); p.mulPose(Axis.ZP.rotation(-angles[i]));
                centred(p, b, light, 0, -y - 2.6f, 0, 1.6f - i * .25f, 2.8f, .5f, SILVER);
                p.popPose();
                y += 2.2f;
            }
            p.popPose();
        } else centred(p, b, light, -1.8f, -5.6f, 0, .9f, 2.4f, .9f, SILVER);
    }

    private static void forearm(PoseStack p, MultiBufferSource b, int light, int side, float glowing, float time) {
        // Steel gauntlet, dark glove.
        centred(p, b, light, 0, 0, 0, 3.6f, 5.4f, 3.6f, BLACK);
        centred(p, b, light, 0, .6f, 0, 4.1f, 3.8f, 4.1f, STEEL);
        centred(p, b, light, side * 1.9f, 1.2f, 0, .4f, 3, 3, RED_DARK);
        centred(p, b, light, 0, 5.3f, -.1f, 3.4f, 2.4f, 3.2f, BLACK);
        // Twin blades along the outside of the forearm, running out past the fist.
        for (int i = 0; i < 2; i++) {
            p.pushPose();
            px(p, side * (2.2f + i * .9f), 1.5f, (i - .5f) * 1.1f);
            p.mulPose(Axis.ZP.rotation(side * (.06f + i * .05f)));
            centred(p, b, light, 0, 0, 0, .5f, 8, 1.3f, SILVER);
            centred(p, b, light, 0, 8, 0, .4f, 3.5f, .8f, SILVER);
            if (glowing > .05f) glow(p, b, -.3f, 0, -.8f, .6f, 11.5f, .2f, .55f * glowing, .05f * glowing, .3f * glowing);
            p.popPose();
        }
    }

    private static void head(PoseStack p, MultiBufferSource b, int light, float time) {
        // The mask: a steel helm, a darker face plate, a crest down the middle, swept-back horns.
        centred(p, b, light, 0, -8.6f, 0, 8.4f, 8.6f, 8.4f, STEEL);
        centred(p, b, light, 0, -5.6f, -3.9f, 7.4f, 5.4f, .9f, BLACK);
        centred(p, b, light, 0, -10.6f, .4f, 1.2f, 2.4f, 8.8f, STEEL_LIGHT);
        centred(p, b, light, 0, -9.6f, -4.4f, 1.2f, 4.6f, .7f, STEEL_LIGHT);
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose(); px(p, side * 4.1f, -7.4f, 2.2f); p.mulPose(Axis.XP.rotation(.7f)); p.mulPose(Axis.ZP.rotation(side * .25f));
            centred(p, b, light, 0, -3.5f, 0, .8f, 3.5f, 1.2f, STEEL_LIGHT);
            p.popPose();
            // Cheek guards.
            centred(p, b, light, side * 3.6f, -3.2f, -2.4f, 1.4f, 3.4f, 3.6f, STEEL_LIGHT);
        }
        // The eye slit: a narrow band of red light.
        float pulse = .85f + .15f * (float) Math.sin(time * .3);
        glow(p, b, -3, -5.4f, -4.45f, 2.3f, .7f, .2f, EYE[0] * pulse, EYE[1] * pulse, EYE[2] * pulse);
        glow(p, b, .7f, -5.4f, -4.45f, 2.3f, .7f, .2f, EYE[0] * pulse, EYE[1] * pulse, EYE[2] * pulse);
        // A short tail of red cloth from the back of the helm.
        centred(p, b, light, 0, -4.5f, 4.4f, 2.4f, 5.5f, .4f, RED);
    }
}
