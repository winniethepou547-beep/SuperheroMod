package com.FIRNI.superheromod.client.render.zed;

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

/**
 * Zed's body, built of boxes, after the classic Zed: a silver helm with a gold crest, a V brow over two
 * glowing red eyes and a barred grille over the mouth, under a crimson hood; a crimson cowl round the
 * neck that drapes across the chest; silver plate edged in gold; great layered pauldrons that flare up
 * and out; two four-bladed shurikens hung on his back, their hooked blades showing over his shoulders;
 * a short crimson cape; three long blades along each forearm, past the fist; a crimson skirt with gold
 * bands and gold spikes at the hem; dark baggy trousers; silver greaves with pointed knees and toes.
 * In SHADOW mode the same body is drawn almost black and see-through, with faint red-violet lines through
 * it; its parts can fade in and out separately (a shadow soldier forming, or dissolving).
 * Model space: pixels/16, +y down, -z in front, -x is his right; the root is the player model's.
 */
public final class ZedBody {
    public static final int NORMAL = 0, SHADOW = 1;
    /** Parts that can fade separately in SHADOW mode. */
    public static final int TORSO = 0, HEAD = 1, ARMS = 2, LEGS = 3;
    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 1, 1, 1);
    private static final int FULL = 15728880;
    static final float[] BLACK = {.04f, .04f, .05f}, SUIT = {.08f, .08f, .1f}, STEEL_DARK = {.2f, .21f, .24f}, STEEL = {.44f, .46f, .5f},
            SILVER = {.68f, .7f, .74f}, SILVER_HI = {.86f, .87f, .9f}, GOLD = {.76f, .58f, .27f}, GOLD_DARK = {.5f, .36f, .15f},
            RED = {.55f, .07f, .09f}, RED_DARK = {.33f, .04f, .06f}, RED_FOLD = {.42f, .05f, .07f}, PANTS = {.08f, .08f, .12f},
            PANTS_FOLD = {.11f, .11f, .16f}, EYE = {1f, .22f, .1f};

    // The state of the current draw (render thread only).
    private static int mode;
    private static float alpha, flicker;
    private static int group = TORSO;
    /** Per part opacity in SHADOW mode (a soldier forming torso first, the legs last). */
    public static final float[] PART_ALPHA = {1, 1, 1, 1};
    /** How lit the eyes are (0 dark, 1 normal, more flares); in SHADOW mode they ignore the body's opacity. */
    public static float eyes = 1;
    /** When on, every draw records where the eyes are in the world (eyeRight, eyeLeft). */
    public static boolean capture;
    public static Vec3 eyeRight, eyeLeft;
    /** With capture on: where the tips of his main blades are (for their motion trails). */
    public static Vec3 tipRight, tipLeft;

    private ZedBody() {}

    /** Back to drawing solid (for pieces drawn outside draw(), like the first-person gauntlets). */
    static void solid() { mode = NORMAL; alpha = 1; flicker = 1; group = TORSO; }
    /** Back to the defaults after a soldier or a dissolving body. */
    public static void resetParts() { PART_ALPHA[0] = PART_ALPHA[1] = PART_ALPHA[2] = PART_ALPHA[3] = 1; eyes = 1; }

    private static void px(PoseStack p, double x, double y, double z) { p.translate(x / 16, y / 16, z / 16); }
    private static void rot(PoseStack p, float x, float y, float z) { p.mulPose(new Quaternionf().rotationZYX(z, y, x)); }

    /** A box in pixels: corner (x, y, z), size (w, h, d). */
    static void box(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        if (w <= .01f || h <= .01f || d <= .01f) return;
        if (mode == SHADOW) {
            float a = alpha * flicker * PART_ALPHA[group];
            if (a <= .01f) return;
            p.pushPose();
            p.translate(x / 16, y / 16, z / 16);
            p.scale(w, h, d);
            float lum = (c[0] + c[1] + c[2]) / 3;
            UNIT.render(p, b.getBuffer(RenderType.entityTranslucent(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
                    .04f + lum * .1f, .025f + lum * .05f, .06f + lum * .12f, a);
            p.popPose();
            return;
        }
        p.pushPose();
        p.translate(x / 16, y / 16, z / 16);
        p.scale(w, h, d);
        UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1);
        p.popPose();
    }
    static void centred(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        box(p, b, light, x - w / 2, y, z - d / 2, w, h, d, c);
    }
    /** A box centred on (x, y, z), turned (radians, applied x, then y, then z), of size (w, h, d). */
    static void part(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float rx, float ry, float rz, float w, float h, float d, float[] c) {
        p.pushPose();
        px(p, x, y, z);
        if (rx != 0 || ry != 0 || rz != 0) rot(p, rx, ry, rz);
        box(p, b, light, -w / 2, -h / 2, -d / 2, w, h, d, c);
        p.popPose();
    }
    /** A box that glows (in a shadow, the lines of energy running through it). */
    static void glow(PoseStack p, MultiBufferSource b, float x, float y, float z, float w, float h, float d, float r, float g, float bl) {
        float a = mode == SHADOW ? alpha * PART_ALPHA[group] : 1;
        if (a <= .01f) return;
        p.pushPose();
        p.translate(x / 16, y / 16, z / 16);
        p.scale(w, h, d);
        UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, r * a, g * a, bl * a, 1);
        p.popPose();
    }
    /** A glowing box centred on (x, y, z), slanted about z. */
    private static void glowPart(PoseStack p, MultiBufferSource b, float x, float y, float z, float rz, float w, float h, float d, float r, float g, float bl) {
        p.pushPose();
        px(p, x, y, z);
        if (rz != 0) p.mulPose(Axis.ZP.rotation(rz));
        p.scale(w, h, d);
        p.translate(-.5f / 16, -.5f / 16, -.5f / 16);
        UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, r, g, bl, 1);
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
        float[] legX = new float[2];
        group = LEGS;
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side < 0;
            float lx = right ? pose.rLegX : pose.lLegX, lz = right ? pose.rLegZ : pose.lLegZ, knee = right ? pose.rKnee : pose.lKnee;
            // Short quick steps.
            float sw = Mth.cos(walk * .8f + (right ? 0 : Mth.PI)) * 1.05f * amount;
            lx += sw; knee += Math.max(0, -Mth.sin(walk * .8f + (right ? 0 : Mth.PI))) * .7f * amount;
            legX[right ? 0 : 1] = lx - fold;
            p.pushPose();
            px(p, side * 1.95f, 12 + drop, 0);
            rot(p, lx - fold, 0, lz);
            leg(p, b, light, side, knee + 2 * fold);
            p.popPose();
        }
        px(p, 0, drop, 0);
        // Belt and skirt hang from the hips and swing with the legs.
        group = TORSO;
        skirt(p, b, light, legX[0], legX[1], time, amount);
        px(p, 0, 12, 0);
        p.mulPose(Axis.YP.rotation(pose.torsoYaw)); p.mulPose(Axis.XP.rotation(pose.torsoPitch)); p.mulPose(Axis.ZP.rotation(pose.torsoRoll));
        px(p, 0, -12, 0);
        torso(p, b, light, time, amount, pose.torsoPitch + lean);
        group = ARMS;
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
        group = HEAD;
        p.pushPose();
        p.mulPose(Axis.YP.rotation(headYaw - pose.torsoYaw + pose.headYaw));
        p.mulPose(Axis.XP.rotation(Mth.clamp(headPitch + pose.headPitch - pose.torsoPitch * .5f - lean * .7f, -1.2f, 1.1f)));
        head(p, b, light, time);
        p.popPose();
        p.popPose();
        group = TORSO;
    }

    // ------------------------------------------------------------------ legs
    private static void leg(PoseStack p, MultiBufferSource b, int light, int side, float knee) {
        // Loose dark trousers, gathered at the knee.
        part(p, b, light, 0, 3.1f, 0, 0, 0, 0, 4.5f, 6.2f, 4.5f, PANTS);
        part(p, b, light, side * .35f, 4.4f, -.2f, 0, 0, side * .06f, 4.9f, 2.6f, 4.9f, PANTS_FOLD);
        // Pointed silver knee.
        part(p, b, light, 0, 5.9f, -2.35f, 0, 0, 0, 3.4f, 2.4f, 1.1f, SILVER);
        part(p, b, light, 0, 5.2f, -2.85f, .785f, 0, 0, 1.1f, 1.5f, 1.5f, SILVER_HI);
        px(p, 0, 6, 0);
        p.mulPose(Axis.XP.rotation(knee));
        // Silver greave with a ridge down the shin, a gold band at the top.
        part(p, b, light, 0, 2.7f, 0, 0, 0, 0, 4.3f, 5.4f, 4.4f, SILVER);
        part(p, b, light, 0, .45f, 0, 0, 0, 0, 4.5f, .5f, 4.6f, GOLD);
        part(p, b, light, 0, 2.6f, -2.35f, 0, 0, 0, 1f, 4.8f, .6f, SILVER_HI);
        part(p, b, light, side * 2.2f, 2.6f, .2f, 0, 0, 0, .4f, 4f, 2.6f, STEEL);
        // Armoured boot, the toe drawn to a point.
        part(p, b, light, 0, 5.75f, -.6f, 0, 0, 0, 4.2f, 1.3f, 5.6f, STEEL);
        part(p, b, light, 0, 5.85f, -3.55f, 0, .785f, 0, 1.7f, 1.1f, 1.7f, SILVER);
        part(p, b, light, 0, 6.35f, -.8f, 0, 0, 0, 4.3f, .35f, 6f, BLACK);
    }

    // ------------------------------------------------------------------ belt, skirt
    private static void skirt(PoseStack p, MultiBufferSource b, int light, float rightLeg, float leftLeg, float time, float amount) {
        // A crimson sash between two gold bands, a gold buckle.
        part(p, b, light, 0, 10.6f, 0, 0, 0, 0, 8.8f, 1.4f, 4.8f, RED_DARK);
        part(p, b, light, 0, 9.95f, 0, 0, 0, 0, 9f, .35f, 5f, GOLD);
        part(p, b, light, 0, 11.3f, 0, 0, 0, 0, 9f, .35f, 5f, GOLD);
        part(p, b, light, 0, 10.6f, -2.55f, 0, 0, .785f, 1.5f, 1.5f, .4f, GOLD);
        // Steel tassets over the hips.
        for (int side = -1; side <= 1; side += 2) part(p, b, light, side * 4.35f, 12.8f, 0, 0, 0, side * .18f, .6f, 3.4f, 4.2f, STEEL);
        float sway = (float) Math.sin(time * .17) * .03f;
        // Front: two overlapping panels (the right one longer), pushed forward by whichever leg is ahead.
        float front = Math.min(0, Math.min(rightLeg, leftLeg)) * .85f - sway;
        p.pushPose(); px(p, 0, 11.3f, -2.55f); p.mulPose(Axis.XP.rotation(front));
        skirtPanel(p, b, light, -1.9f, 4.2f, 10.5f, .06f, -.2f, -1);
        skirtPanel(p, b, light, 1.9f, 4f, 8f, -.06f, 0, -1);
        p.popPose();
        // Back: one wide panel, kicked back by a leg behind and by speed.
        float back = Math.max(0, Math.max(rightLeg, leftLeg)) * .85f + .25f * Math.min(1, amount) + sway;
        p.pushPose(); px(p, 0, 11.1f, 2.55f); p.mulPose(Axis.XP.rotation(back));
        skirtPanel(p, b, light, 0, 8.4f, 9.5f, 0, .2f, 1);
        p.popPose();
    }
    /** One crimson panel hanging from the belt: gold bands slanting across its outer face, gold spikes along its hem. */
    private static void skirtPanel(PoseStack p, MultiBufferSource b, int light, float x, float w, float len, float tilt, float z, int outward) {
        part(p, b, light, x, len / 2, z, 0, 0, tilt, w, len, .4f, RED);
        for (float y = 2.6f; y < len - .8f; y += 2.9f) part(p, b, light, x, y, z + outward * .22f, 0, 0, tilt + .14f, w + .1f, .45f, .2f, GOLD);
        float hem = x - (float) Math.sin(tilt) * len / 2;
        for (int i = 0; i < 3; i++) part(p, b, light, hem + (i - 1) * w * .32f, len + .55f, z, 0, 0, (i - 1) * .25f, .5f, 1.3f, .5f, GOLD);
    }

    // ------------------------------------------------------------------ torso
    private static void torso(PoseStack p, MultiBufferSource b, int light, float time, float amount, float pitch) {
        part(p, b, light, 0, 6, 0, 0, 0, 0, 8, 12, 4, SUIT);
        // Silver chest plates angled back at their outer edges, gold along their lower rims.
        for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 2.15f, 2.9f, -2.45f, 0, -side * .2f, side * .06f, 4.3f, 4.6f, 1f, SILVER);
            part(p, b, light, side * 2.15f, 5.25f, -2.95f, 0, -side * .2f, side * .06f, 4.1f, .45f, .4f, GOLD);
            part(p, b, light, side * 2.7f, .9f, -2.35f, 0, -side * .25f, -side * .25f, 3.4f, 1.8f, .8f, SILVER_HI);
            // Side plates and a tall collar flaring up behind the head.
            part(p, b, light, side * 3.95f, 4.6f, 0, 0, 0, 0, .6f, 5.6f, 3.6f, STEEL);
            part(p, b, light, side * 3.4f, -2.5f, 1.1f, -.25f, 0, side * .35f, 1.4f, 5f, 2.4f, SILVER);
            part(p, b, light, side * 3.85f, -2.3f, .1f, -.25f, 0, side * .35f, .35f, 4.6f, .4f, GOLD);
            part(p, b, light, side * 4.35f, -5.2f, 1.5f, -.3f, 0, side * .55f, .7f, 2f, 1.2f, SILVER_HI);
        }
        part(p, b, light, 0, 3, -2.65f, 0, 0, 0, .8f, 4.6f, .6f, STEEL_DARK);
        // Segmented steel over the belly.
        part(p, b, light, 0, 6.7f, -2.25f, 0, 0, 0, 6.2f, 1f, .6f, STEEL);
        part(p, b, light, 0, 7.95f, -2.2f, 0, 0, 0, 5.6f, 1f, .6f, STEEL_DARK);
        part(p, b, light, 0, 6.15f, -2.6f, 0, 0, 0, 6.2f, .25f, .2f, GOLD_DARK);
        // The crimson cowl round the neck, and its drape running from his left shoulder to his right hip.
        part(p, b, light, 0, -.5f, -.1f, 0, 0, 0, 7.2f, 2.4f, 5.8f, RED);
        part(p, b, light, .3f, 4.8f, -3f, 0, 0, .45f, 2.8f, 10.5f, .5f, RED);
        part(p, b, light, .9f, 4.2f, -3.15f, 0, 0, .45f, .5f, 9f, .3f, RED_FOLD);
        // The back, as in the reference: layered dark crimson plates edged in dark steel, a ridge down the
        // spine, a short crimson mantle over the shoulders. No long cape: nothing hides the shurikens.
        part(p, b, light, 0, 3.6f, 2.3f, 0, 0, 0, 7.4f, 6f, .6f, STEEL_DARK);
        part(p, b, light, 0, 1.6f, 2.6f, .12f, 0, 0, 7.8f, 2.8f, .6f, RED_DARK);
        part(p, b, light, 0, 4.2f, 2.65f, .08f, 0, 0, 7.2f, 2.6f, .6f, RED);
        part(p, b, light, 0, 6.7f, 2.55f, .05f, 0, 0, 6.6f, 2.2f, .6f, RED_DARK);
        for (float y : new float[]{2.95f, 5.5f, 7.8f}) part(p, b, light, 0, y, 2.95f, 0, 0, 0, 7f - y * .12f, .35f, .2f, STEEL_DARK);
        part(p, b, light, 0, 4.2f, 3.0f, 0, 0, 0, .7f, 7.4f, .4f, GOLD_DARK);
        part(p, b, light, 0, -.25f, 1.4f, 0, 0, 0, 10.4f, 2f, 4.4f, RED);
        part(p, b, light, 0, .9f, 3.35f, .3f, 0, 0, 5.2f, 2.6f, .5f, RED_FOLD);
        // The two great shurikens hung high on his back, overlapping in the middle, their blades rising
        // over his shoulders and reaching out past them.
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose(); px(p, side * 4.1f, 1.6f, side < 0 ? 3.7f : 4.5f); p.mulPose(Axis.ZP.rotation(side * .28f));
            shuriken(p, b, light, 8.6f, SILVER, SILVER_HI, STEEL_DARK);
            p.popPose();
        }
        if (mode == SHADOW) {
            // Lines of red-violet energy through the shadow's chest.
            glow(p, b, -.2f, .6f, -3.2f, .4f, 7, .2f, .4f, .06f, .3f);
            glow(p, b, -3.2f, 5.6f, -3.2f, 6.4f, .3f, .2f, .35f, .05f, .3f);
        }
    }

    /**
     * A four-bladed shuriken in the plane of x and y, centred here, its hooked blades curling the same
     * way round: each blade a chain of segments, wide at the hub, bending and thinning to a point, with
     * a bright cutting edge. reach is roughly the blade length in pixels.
     */
    static void shuriken(PoseStack p, MultiBufferSource b, int light, float reach, float[] metal, float[] edge, float[] hub) {
        float s = reach;
        part(p, b, light, 0, 0, 0, 0, 0, 0, s * .42f, s * .42f, s * .17f, hub);
        part(p, b, light, 0, 0, 0, 0, 0, .785f, s * .42f, s * .42f, s * .18f, hub);
        part(p, b, light, 0, 0, 0, 0, 0, 0, s * .14f, s * .14f, s * .22f, BLACK);
        float[] len = {.3f, .26f, .22f, .2f}, wid = {.36f, .28f, .19f, .09f};
        for (int i = 0; i < 4; i++) {
            p.pushPose();
            p.mulPose(Axis.ZP.rotation((float) (i * Math.PI / 2)));
            px(p, 0, -s * .14f, 0);
            for (int k = 0; k < len.length; k++) {
                float l = len[k] * s, w = wid[k] * s;
                part(p, b, light, 0, -l / 2, 0, 0, 0, 0, w, l, s * .08f, metal);
                part(p, b, light, w * .42f, -l / 2, 0, 0, 0, 0, w * .2f, l, s * .085f, edge);
                px(p, 0, -l, 0);
                p.mulPose(Axis.ZP.rotation(.36f));
            }
            p.popPose();
        }
    }

    // ------------------------------------------------------------------ arms
    private static void upperArm(PoseStack p, MultiBufferSource b, int light, int side) {
        part(p, b, light, 0, 1, 0, 0, 0, 0, 3.6f, 6, 3.6f, SUIT);
        part(p, b, light, side * .2f, 1.3f, 0, 0, 0, 0, 4.2f, 2f, 4.2f, RED);
        // Layered pauldron: a silver dome with a gold flame, two plates below it, a point flaring up and out.
        part(p, b, light, side * .6f, -2.2f, 0, 0, 0, side * .3f, 6.4f, 2.6f, 6.2f, SILVER);
        part(p, b, light, side * .6f, -3.55f, 0, 0, 0, side * .3f, 4.6f, .4f, 3f, GOLD);
        part(p, b, light, side * 1.3f, -2.5f, -3.15f, 0, 0, side * .6f, .6f, 2f, .3f, GOLD);
        part(p, b, light, side * 1f, -.6f, 0, 0, 0, side * .42f, 6f, 1.8f, 5.8f, SILVER);
        part(p, b, light, side * 1.05f, .35f, 0, 0, 0, side * .42f, 6f, .35f, 5.9f, GOLD);
        part(p, b, light, side * 1.3f, .9f, 0, 0, 0, side * .5f, 5.2f, 1.5f, 5.2f, STEEL);
        part(p, b, light, side * 2.4f, -4.1f, .4f, 0, 0, side * .55f, 1.3f, 3.4f, 2.2f, SILVER);
        part(p, b, light, side * 3.2f, -5.6f, .4f, 0, 0, side * .75f, .6f, 1.6f, 1.2f, SILVER_HI);
        part(p, b, light, 0, 4.1f, 1.5f, 0, 0, 0, 2.6f, 1.8f, 1.4f, SILVER);
    }

    private static void forearm(PoseStack p, MultiBufferSource b, int light, int side, float glowing, float time) {
        part(p, b, light, 0, 2.6f, 0, 0, 0, 0, 3.6f, 5.4f, 3.6f, SUIT);
        // Silver vambrace between two gold rims, a fin along the outside; a black glove.
        part(p, b, light, 0, 2.8f, 0, 0, 0, 0, 4.2f, 4.4f, 4.2f, SILVER);
        part(p, b, light, 0, .75f, 0, 0, 0, 0, 4.4f, .45f, 4.4f, GOLD);
        part(p, b, light, 0, 5f, 0, 0, 0, 0, 4.4f, .45f, 4.4f, GOLD);
        part(p, b, light, side * 2.3f, 2.6f, .4f, 0, 0, 0, .6f, 4.2f, 2.4f, SILVER_HI);
        part(p, b, light, 0, 6.4f, -.2f, 0, 0, 0, 3.4f, 2.6f, 3.4f, BLACK);
        part(p, b, light, 0, 6.5f, -1.85f, 0, 0, 0, 2.8f, 1.6f, .4f, STEEL);
        // Three blades along the outside of the forearm, running far past the fist: a long main blade
        // with a serrated back, a shorter one in front of it and one behind.
        part(p, b, light, side * 2.25f, 1.6f, 0, 0, 0, 0, .8f, 2f, 3f, GOLD);
        part(p, b, light, side * 2.5f, 8f, 0, 0, 0, side * .04f, .5f, 13f, 1.4f, SILVER);
        part(p, b, light, side * 2.85f, 15.1f, -.15f, 0, 0, side * .1f, .4f, 2.6f, .8f, SILVER_HI);
        part(p, b, light, side * 2.1f, 7.3f, -1.25f, 0, 0, side * .07f, .4f, 10.5f, 1f, SILVER);
        part(p, b, light, side * 2.5f, 13.1f, -1.35f, 0, 0, side * .12f, .35f, 2f, .6f, SILVER_HI);
        part(p, b, light, side * 2.1f, 6.4f, 1.25f, 0, 0, side * .07f, .4f, 8f, 1f, SILVER);
        for (int i = 0; i < 4; i++) part(p, b, light, side * 2.9f, 3 + i * 2.5f, .55f, 0, 0, side * .6f, .35f, 1.2f, .5f, SILVER_HI);
        if (glowing > .05f) {
            float g = glowing;
            glow(p, b, side * 2.5f - .35f, 2, -.9f, .7f, 14, .25f, .6f * g, .05f * g, .3f * g);
            glow(p, b, side * 2.1f - .3f, 2.5f, -1.9f, .6f, 10, .2f, .55f * g, .04f * g, .28f * g);
        }
        if (mode == SHADOW) glow(p, b, side * 2.5f - .1f, 2, -.2f, .2f, 13, .2f, .35f, .04f, .3f);
        if (capture) {
            Vec3 tip = world(p, side * 2.8f, 16.2f, -.15f);
            if (side < 0) tipRight = tip; else tipLeft = tip;
        }
    }

    // ------------------------------------------------------------------ head: the helm and the hood
    private static void head(PoseStack p, MultiBufferSource b, int light, float time) {
        part(p, b, light, 0, -4.1f, 0, 0, 0, 0, 8.2f, 8.2f, 8.2f, STEEL);
        // Face plate, a V brow over the eyes, a ridge down the nose, a barred grille over the mouth.
        part(p, b, light, 0, -4.6f, -4.2f, 0, 0, 0, 7.6f, 7f, .6f, SILVER);
        for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 2f, -6.05f, -4.65f, 0, 0, -side * .28f, 3.6f, 1.1f, .7f, SILVER_HI);
            part(p, b, light, side * 1.85f, -5.15f, -4.55f, 0, 0, -side * .22f, 2.7f, 1.25f, .3f, BLACK);
            // Cheek guards angled back, a gold edge round the face.
            part(p, b, light, side * 3.75f, -3f, -3.1f, 0, side * .35f, 0, 1.2f, 4.8f, 3f, SILVER);
            part(p, b, light, side * 3.95f, -4.6f, -4.05f, 0, 0, 0, .45f, 6.6f, .9f, GOLD);
        }
        part(p, b, light, 0, -4.3f, -4.75f, 0, 0, 0, .9f, 4.6f, .6f, SILVER_HI);
        part(p, b, light, 0, -2.45f, -4.53f, 0, 0, 0, 5.8f, 2.9f, .1f, BLACK);
        for (int i = 1; i <= 3; i++) for (int side = -1; side <= 1; side += 2)
            part(p, b, light, side * (i * .88f), -2.45f, -4.68f, 0, 0, 0, .45f, 2.9f, .4f, SILVER_HI);
        part(p, b, light, 0, -.8f, -4.25f, .3f, 0, 0, 5.4f, 1f, 1f, SILVER);
        // The crest rising up the brow to a point, a gold crescent on the forehead.
        part(p, b, light, 0, -8.15f, -4.4f, -.35f, 0, 0, 1.8f, 3f, .7f, SILVER_HI);
        part(p, b, light, 0, -8.65f, -2.6f, 0, 0, 0, 1.4f, 1.4f, 4f, SILVER_HI);
        part(p, b, light, 0, -9.5f, -3.3f, .785f, 0, 0, 1.5f, 2.1f, 2.1f, SILVER);
        for (int side = -1; side <= 1; side += 2) part(p, b, light, side * .95f, -7.2f, -4.68f, 0, 0, side * .6f, .55f, 1.8f, .3f, GOLD);
        part(p, b, light, 0, -7.85f, -4.72f, 0, 0, 0, 1.6f, .45f, .3f, GOLD);
        // The crimson hood over the back and top of the helm, its rim folded over the brow, its point
        // hanging down onto the shoulders.
        part(p, b, light, 0, -4.4f, 4.4f, 0, 0, 0, 9.4f, 9.4f, 1.2f, RED);
        part(p, b, light, 0, -8.95f, 1.6f, 0, 0, 0, 9.4f, 1.2f, 6.6f, RED);
        part(p, b, light, 0, -9.05f, -1.75f, .3f, 0, 0, 9.6f, 1.4f, 1f, RED_DARK);
        for (int side = -1; side <= 1; side += 2) {
            part(p, b, light, side * 4.65f, -4.7f, 1.2f, 0, 0, 0, 1f, 8.8f, 7.2f, RED);
            part(p, b, light, side * 4.9f, -4.4f, -2.2f, 0, 0, 0, .6f, 7.6f, .8f, RED_FOLD);
        }
        part(p, b, light, 0, -.9f, 5.1f, .25f, 0, 0, 6f, 4f, 1f, RED_DARK);
        // The eyes: two slanted slits of red light under the brow (their bloom and trails are ZedEyes').
        float pulse = (.88f + .12f * (float) Math.sin(time * .3)) * Math.min(1.6f, eyes);
        if (pulse > .01f) for (int side = -1; side <= 1; side += 2)
            glowPart(p, b, side * 1.85f, -5.2f, -4.74f, -side * .22f, 1.9f, .7f, .2f, EYE[0] * pulse, EYE[1] * pulse, EYE[2] * pulse);
        if (capture) {
            eyeRight = world(p, -1.85f, -5.2f, -5.2f);
            eyeLeft = world(p, 1.85f, -5.2f, -5.2f);
        }
    }

    /** Where a point of the current model space is in the world (through the inverse view rotation of this frame). */
    private static Vec3 world(PoseStack p, float x, float y, float z) {
        Vector4f v = new Vector4f(x / 16, y / 16, z / 16, 1).mul(p.last().pose());
        Vector3f w = new Vector3f(v.x, v.y, v.z);
        RenderSystem.getInverseViewRotationMatrix().transform(w);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return cam.add(w.x, w.y, w.z);
    }
}
