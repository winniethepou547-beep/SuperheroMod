package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;

/**
 * Magneto's body, built of boxes after the painting: the crimson armoured suit (sculpted chest plates in a V from the
 * collar, banded abdominal plates, broad shoulders, the darker seams between plates), violet panels down the flanks
 * and the outer legs, long violet gauntlets flaring at the cuff, tall violet boots edged in steel, steel clasps at
 * the hips, the rounded crimson helmet with its crest, cheek guards and violet trim round the face (an old man's lined
 * face inside it), and the long violet cape falling from a mantle over the shoulders, its folds lifting and flowing
 * when he flies. Model space as the player model's (pixels/16, +y down, -z in front, -x his right; neck at y 0, hips
 * at 12, feet at 24); the joints are the same chain as Black Panther's, so the same poses work.
 */
public final class MagnetoBody {
    private MagnetoBody() {}

    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 1, 1, 1);
    private static final int FULL = 15728880;
    static final float[] RED = {.56f, .07f, .08f}, RED_DARK = {.30f, .03f, .045f}, RED_LIGHT = {.74f, .13f, .13f}, RED_SEAM = {.2f, .02f, .03f},
            VIOLET = {.34f, .15f, .48f}, VIOLET_DARK = {.18f, .07f, .27f}, VIOLET_LIGHT = {.5f, .27f, .66f}, CAPE_IN = {.14f, .05f, .21f},
            STEEL = {.6f, .62f, .68f}, STEEL_DARK = {.34f, .35f, .4f}, SKIN = {.84f, .68f, .6f}, SKIN_SHADE = {.69f, .53f, .46f},
            BROW = {.93f, .93f, .92f}, EYE = {.12f, .1f, .1f}, BEARD = {.9f, .9f, .88f}, BEARD_SHADE = {.72f, .72f, .71f}, MOUTH = {.36f, .2f, .19f};
    /**
     * How strongly each hand glows while he works metal (0 right, 1 left; 0..1): set by whoever draws him, read while the
     * hands are drawn (the hand turns translucent violet and throws off tiny pale sparks), then put back to 0.
     */
    public static final float[] GLOW = {0, 0};
    /** The clock the sparks run on. */
    public static float glowTime;
    /** How lifted the cape is (speed, falling), how fast it is moving, which way it is pushed sideways, and the flight (0..1). */
    public record Cloth(float lift, float speed, float side, float flying) {}

    private static void px(PoseStack p, double x, double y, double z) { p.translate(x / 16, y / 16, z / 16); }
    private static void rot(PoseStack p, float x, float y, float z) { p.mulPose(new Quaternionf().rotationZYX(z, y, x)); }
    /** A box centred on (x, y, z) in pixels, turned (x then y then z), of size (w, h, d). */
    private static void part(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float rx, float ry, float rz, float w, float h, float d, float[] c) {
        p.pushPose();
        px(p, x, y, z);
        if (rx != 0 || ry != 0 || rz != 0) rot(p, rx, ry, rz);
        p.translate(-w / 32, -h / 32, -d / 32);
        p.scale(w, h, d);
        float[] k = MetalMesh.SHADE;
        // (Clamped: a colour over 1 wraps round in the vertex bytes, which is what turned his face blue in the film's flashes.)
        UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY,
                Math.min(1, c[0] * k[0]), Math.min(1, c[1] * k[1]), Math.min(1, c[2] * k[2]), 1);
        p.popPose();
    }
    private static void part(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        part(p, b, light, x, y, z, 0, 0, 0, w, h, d, c);
    }

    /**
     * Draws him at the model root: the pose, where the head looks (radians, relative to the body), the free clock, the
     * cape's state and the hand's glow (0..1, a faint light round the casting hand while he works metal).
     */
    public static void draw(PoseStack p, MultiBufferSource b, int light, Pose pose, float lookYaw, float lookPitch, float time, Cloth cloth) {
        float[] v = pose.v;
        p.pushPose();
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
            px(p, s * 2.05f, 0, 0);
            rot(p, legX, s < 0 ? v[o + LEG_Y] : -v[o + LEG_Y], s < 0 ? v[o + LEG_Z] : -v[o + LEG_Z]);
            leg(p, b, light, s, knee, ankle);
            p.popPose();
        }
        hips(p, b, light);
        p.mulPose(Axis.YP.rotation(v[SPINE_YAW])); p.mulPose(Axis.XP.rotation(v[SPINE_PITCH])); p.mulPose(Axis.ZP.rotation(v[SPINE_ROLL]));
        abdomen(p, b, light);
        px(p, 0, -5.6f, 0);
        p.mulPose(Axis.YP.rotation(v[CHEST_YAW])); p.mulPose(Axis.XP.rotation(v[CHEST_PITCH])); p.mulPose(Axis.ZP.rotation(v[CHEST_ROLL]));
        chest(p, b, light);
        cape(p, b, light, cloth, time, v[ROOT_PITCH] + v[SPINE_PITCH]);
        for (int side = 0; side < 2; side++) {
            int s = side == 0 ? -1 : 1;
            int o = side == 0 ? R : L;
            p.pushPose();
            px(p, s * 5.0f, -5.4f - v[o + SH_UP] * .6f, -v[o + SH_FWD] * .6f);
            rot(p, v[o + ARM_X], s < 0 ? v[o + ARM_Y] : -v[o + ARM_Y], s < 0 ? v[o + ARM_Z] : -v[o + ARM_Z]);
            arm(p, b, light, s, v[o + ELBOW], v[o + WRIST_X], v[o + WRIST_Z], v[o + CURL]);
            p.popPose();
        }
        p.pushPose();
        px(p, 0, -6.6f, -v[NECK]);
        p.mulPose(Axis.YP.rotation(lookYaw + v[HEAD_YAW]));
        p.mulPose(Axis.XP.rotation(Mth.clamp(lookPitch + v[HEAD_PITCH], -1.4f, 1.3f)));
        p.mulPose(Axis.ZP.rotation(v[HEAD_ROLL]));
        head(p, b, light);
        p.popPose();
        p.popPose();
        p.popPose();
    }

    // ------------------------------------------------------------------ legs: red thighs with violet outer panels, knee plates, tall boots
    private static void leg(PoseStack p, MultiBufferSource b, int light, int s, float knee, float ankle) {
        part(p, b, light, 0, 3.1f, 0, 4.3f, 6.4f, 4.3f, RED);
        part(p, b, light, s * 2.0f, 3.2f, .2f, .5f, 6.0f, 3.2f, VIOLET);
        part(p, b, light, s * -.3f, 2.8f, -2.1f, 2.6f, 4.6f, .3f, RED_LIGHT);
        part(p, b, light, s * .9f, 2.8f, -2.14f, .25f, 4.8f, .2f, RED_SEAM);
        px(p, 0, 6, 0);
        p.mulPose(Axis.XP.rotation(knee));
        // The knee plate, then the boot from just below the knee to the foot.
        part(p, b, light, 0, -.2f, -2.05f, 3.2f, 2.4f, .7f, RED_LIGHT);
        part(p, b, light, 0, .6f, 0, 4.1f, 1.6f, 4.1f, RED);
        part(p, b, light, 0, 3.0f, 0, 4.3f, 3.8f, 4.3f, VIOLET);
        part(p, b, light, 0, 1.15f, 0, 4.5f, .7f, 4.5f, VIOLET_LIGHT);
        part(p, b, light, s * .0f, 3.0f, -2.2f, .5f, 3.6f, .2f, STEEL);
        part(p, b, light, s * 2.2f, 3.0f, 0, .2f, 3.4f, .5f, STEEL_DARK);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(ankle));
        part(p, b, light, 0, .65f, -.7f, 4.2f, 1.3f, 5.4f, VIOLET_DARK);
        part(p, b, light, 0, .1f, -2.2f, 3.6f, .7f, 1.6f, VIOLET);
    }
    private static void hips(PoseStack p, MultiBufferSource b, int light) {
        part(p, b, light, 0, -.6f, 0, 8.4f, 3.0f, 4.5f, RED);
        part(p, b, light, 0, .3f, -2.2f, 3.0f, 2.4f, .4f, RED_DARK);
        // Steel clasps at the hips, where the violet side panels meet the red.
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * 3.6f, -1.1f, -2.0f, 0, 0, s * .2f, 1.6f, 1.0f, .7f, STEEL);
            part(p, b, light, s * 4.1f, -.6f, 0, .5f, 2.4f, 3.4f, VIOLET);
        }
        part(p, b, light, 0, -1.9f, 0, 8.6f, .6f, 4.6f, RED_SEAM);
    }
    private static void abdomen(PoseStack p, MultiBufferSource b, int light) {
        part(p, b, light, 0, -3.0f, 0, 8.0f, 5.6f, 4.3f, RED);
        // Banded abdominal plates, two columns, darker seams between.
        for (int row = 0; row < 2; row++)
            for (int s = -1; s <= 1; s += 2) part(p, b, light, s * 1.35f, -1.3f - row * 2.1f, -2.15f, 2.4f, 1.8f, .3f, RED_LIGHT);
        part(p, b, light, 0, -2.4f, -2.2f, .35f, 4.6f, .25f, RED_SEAM);
        for (int s = -1; s <= 1; s += 2) part(p, b, light, s * 3.85f, -3.0f, 0, .5f, 5.4f, 3.6f, VIOLET);
    }
    /** The chest: two great plates in a V from the collar, broad shoulders, the collar, violet flanks under the arms. */
    private static void chest(PoseStack p, MultiBufferSource b, int light) {
        part(p, b, light, 0, -3.2f, 0, 8.8f, 6.6f, 4.6f, RED);
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * 2.15f, -3.2f, -2.35f, 0, 0, s * -.12f, 3.8f, 3.4f, .5f, RED_LIGHT);
            part(p, b, light, s * 2.1f, -1.35f, -2.5f, 0, 0, s * .32f, 3.6f, .25f, .2f, RED_SEAM);
            part(p, b, light, s * 4.1f, -2.6f, .2f, .6f, 4.6f, 3.6f, VIOLET);
            // Shoulder plates, rounded with a second smaller box over them.
            part(p, b, light, s * 4.9f, -6.2f, 0, 0, 0, s * .18f, 3.8f, 1.8f, 4.8f, RED);
            part(p, b, light, s * 5.2f, -6.9f, 0, 0, 0, s * .18f, 2.6f, .8f, 3.6f, RED_LIGHT);
        }
        // The V of the collar plate, the seam down the middle.
        part(p, b, light, 0, -5.4f, -2.45f, 0, 0, Mth.PI / 4, 2.6f, 2.6f, .3f, RED_DARK);
        part(p, b, light, 0, -3.0f, -2.55f, .3f, 3.6f, .2f, RED_SEAM);
        part(p, b, light, 0, -6.7f, 0, 4.4f, .8f, 4.0f, RED_DARK);
    }
    /** The arm: the red upper arm, the long violet gauntlet flaring at the cuff, the gloved hand. */
    private static void arm(PoseStack p, MultiBufferSource b, int light, int s, float elbow, float wristX, float wristZ, float curl) {
        part(p, b, light, s * .3f, 1.6f, 0, 3.6f, 5.6f, 3.6f, RED);
        part(p, b, light, s * .3f, -.6f, 0, 4.0f, 1.6f, 4.2f, RED_LIGHT);
        part(p, b, light, s * 1.95f, 2.0f, 0, .3f, 4.0f, 2.2f, RED_SEAM);
        px(p, s * .3f, 5.0f, 0);
        p.mulPose(Axis.XP.rotation(-elbow));
        part(p, b, light, 0, 2.4f, 0, 3.3f, 4.8f, 3.3f, VIOLET);
        // The flared cuff, edged.
        part(p, b, light, 0, .5f, 0, 3.9f, 1.4f, 3.9f, VIOLET_LIGHT);
        part(p, b, light, 0, -.15f, 0, 4.0f, .3f, 4.0f, VIOLET_DARK);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(wristX));
        p.mulPose(Axis.ZP.rotation(s < 0 ? wristZ : -wristZ));
        hand(p, b, light, s, curl);
    }
    /** The gloved hand: the palm, four fingers that curl (open: splayed a little; closed: a fist), the thumb. */
    private static void hand(PoseStack p, MultiBufferSource b, int light, int s, float curl) {
        handShape(p, b, light, s, curl, false, 0);
        float glow = GLOW[s < 0 ? 0 : 1];
        if (glow > .02f) {
            handShape(p, b, light, s, curl, true, Math.min(1, glow));
            sparks(p, b, s, Math.min(1, glow));
        }
    }
    /** A glowing copy of a part: a little larger, violet light added over what is behind it. */
    private static void glowPart(PoseStack p, MultiBufferSource b, float x, float y, float z, float w, float h, float d, float k) {
        p.pushPose();
        px(p, x, y, z);
        p.translate(-(w + .5f) / 32, -(h + .5f) / 32, -(d + .5f) / 32);
        p.scale(w + .5f, h + .5f, d + .5f);
        float pulse = .8f + .2f * Mth.sin(glowTime * .45f);
        UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, .42f * k * pulse, .2f * k * pulse, .78f * k * pulse, 1);
        p.popPose();
    }
    /** Tiny pale violet and white sparks leaping off the glowing hand and dying away. */
    private static void sparks(PoseStack p, MultiBufferSource b, int s, float k) {
        for (int i = 0; i < 10; i++) {
            float phase = glowTime * (.06f + .03f * MetalMesh.hash(i * 13 + s)) + MetalMesh.hash(i * 7 + s * 3);
            phase -= (float) Math.floor(phase);
            float a = MetalMesh.hash(i * 5 + (int) (glowTime * .06f + MetalMesh.hash(i * 7 + s * 3))) * Mth.TWO_PI, e = (MetalMesh.hash(i * 11 + 1) - .3f) * 2.2f;
            float out = 1.6f + phase * 5.5f;
            float x = Mth.cos(a) * Mth.cos(e) * out, y = 2.3f + Mth.sin(e) * out, z = Mth.sin(a) * Mth.cos(e) * out;
            float fade = (1 - phase) * k, white = i % 3 == 0 ? 1 : .55f;
            p.pushPose();
            px(p, x, y, z);
            p.scale(.32f, .32f, .32f);
            p.translate(-.5f / 16, -.5f / 16, -.5f / 16);
            UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, fade * (.7f + .3f * white), fade * (.55f + .45f * white), fade, 1);
            p.popPose();
        }
    }
    private static void handShape(PoseStack p, MultiBufferSource b, int light, int s, float curl, boolean glow, float k) {
        if (glow) glowPart(p, b, 0, 1.1f, 0, 2.9f, 2.4f, 3.1f, k);
        else part(p, b, light, 0, 1.1f, 0, 2.9f, 2.4f, 3.1f, VIOLET);
        for (int f = 0; f < 4; f++) {
            p.pushPose();
            px(p, s * .2f, 2.3f, -1.1f + f * .73f);
            p.mulPose(Axis.XP.rotation(0));
            p.mulPose(Axis.ZP.rotation(s * (curl * 1.3f + .05f)));
            if (glow) glowPart(p, b, 0, .75f, 0, .75f, 1.5f, .62f, k); else part(p, b, light, 0, .75f, 0, .75f, 1.5f, .62f, VIOLET);
            px(p, 0, 1.45f, 0);
            p.mulPose(Axis.ZP.rotation(s * curl * 1.2f));
            if (glow) glowPart(p, b, 0, .6f, 0, .7f, 1.2f, .58f, k); else part(p, b, light, 0, .6f, 0, .7f, 1.2f, .58f, VIOLET_DARK);
            p.popPose();
        }
        p.pushPose();
        px(p, s * -.9f, 1.4f, -1.5f);
        p.mulPose(Axis.XP.rotation(-.5f - .6f * curl));
        if (glow) glowPart(p, b, 0, .7f, 0, .8f, 1.6f, .8f, k); else part(p, b, light, 0, .7f, 0, .8f, 1.6f, .8f, VIOLET);
        p.popPose();
    }
    /** The rounded crimson helmet, its crest and cheek guards, violet trim round the face; inside it an old man's face. */
    private static void head(PoseStack p, MultiBufferSource b, int light) {
        // The face (inside the helmet's opening).
        part(p, b, light, 0, -3.6f, -.2f, 7.4f, 7.2f, 7.4f, SKIN);
        part(p, b, light, 0, -3.5f, -3.95f, .9f, 2.0f, .8f, SKIN);
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * 1.6f, -4.4f, -3.92f, 1.3f, .7f, .2f, new float[]{.9f, .9f, .88f});
            part(p, b, light, s * 1.6f, -4.4f, -4.0f, .55f, .6f, .2f, EYE);
            // Heavy white brows drawn down toward the nose: the stern look of the reference.
            part(p, b, light, s * 1.65f, -5.25f, -4.0f, 0, 0, s * -.28f, 2.1f, .7f, .45f, BROW);
            // Lines of age under the eyes and down the cheeks.
            part(p, b, light, s * 1.55f, -3.8f, -3.93f, 1.2f, .2f, .2f, SKIN_SHADE);
            part(p, b, light, s * 1.25f, -2.6f, -3.92f, 0, 0, s * .35f, .2f, 1.0f, .2f, SKIN_SHADE);
        }
        // The white beard: a full moustache over the mouth, the beard round the jaw and down past the chin, its shading.
        part(p, b, light, 0, -1.75f, -4.05f, 3.4f, .65f, .45f, BEARD);
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * 1.55f, -1.2f, -4.0f, 0, 0, s * -.35f, 1.0f, 1.1f, .45f, BEARD);
            part(p, b, light, s * 2.75f, -1.7f, -3.0f, 1.2f, 3.4f, 2.0f, BEARD);
            part(p, b, light, s * 2.9f, -2.9f, -2.4f, .9f, 1.4f, 2.4f, BEARD_SHADE);
        }
        part(p, b, light, 0, -1.05f, -4.0f, 1.6f, .3f, .3f, MOUTH);
        part(p, b, light, 0, -.15f, -3.85f, 5.0f, 1.6f, .8f, BEARD);
        part(p, b, light, 0, .9f, -3.55f, 3.6f, 1.2f, 1.2f, BEARD);
        part(p, b, light, 0, 1.7f, -3.25f, 2.2f, .8f, .9f, BEARD_SHADE);
        part(p, b, light, 0, .2f, -2.0f, 6.0f, 1.4f, 3.2f, BEARD_SHADE);
        // The dome: a big box, a smaller one over it to round the top, the back down over the neck.
        part(p, b, light, 0, -5.4f, .6f, 8.8f, 6.0f, 8.0f, RED);
        part(p, b, light, 0, -8.6f, .4f, 7.6f, 1.2f, 7.4f, RED);
        part(p, b, light, 0, -9.15f, .4f, 5.8f, .5f, 5.6f, RED_LIGHT);
        part(p, b, light, 0, -1.6f, 3.4f, 8.6f, 3.6f, 1.8f, RED);
        // The crest down the middle, from the brow over the top.
        part(p, b, light, 0, -9.0f, .2f, 1.2f, .9f, 8.2f, RED_DARK);
        part(p, b, light, 0, -6.2f, -4.3f, 0, 0, 0, 1.2f, 3.6f, .7f, RED_DARK);
        // The brow plate dipping to a point over the nose; the cheek guards down past the jaw; violet trim round the opening.
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * 2.1f, -6.6f, -4.15f, 0, 0, s * -.32f, 4.2f, 1.6f, .7f, RED);
            part(p, b, light, s * 3.9f, -3.2f, -2.6f, 1.3f, 6.4f, 3.6f, RED);
            part(p, b, light, s * 3.6f, -.4f, -3.4f, 0, 0, s * .25f, 1.4f, 2.0f, 1.6f, RED_LIGHT);
            part(p, b, light, s * 3.25f, -3.2f, -4.3f, .35f, 6.0f, .3f, VIOLET_LIGHT);
            part(p, b, light, s * 2.0f, -5.95f, -4.42f, 0, 0, s * -.32f, 3.8f, .3f, .2f, VIOLET_LIGHT);
        }
    }

    /** The cape: a mantle over the shoulders, then six rows of cloth falling to his calves, each row bending further. */
    private static void cape(PoseStack p, MultiBufferSource b, int light, Cloth c, float time, float bodyPitch) {
        // The mantle draped over both shoulders and round the back of the neck.
        part(p, b, light, 0, -6.85f, 1.2f, 10.6f, 1.0f, 3.4f, VIOLET);
        for (int s = -1; s <= 1; s += 2) part(p, b, light, s * 4.8f, -6.1f, .4f, 0, 0, s * .5f, 2.6f, 1.0f, 4.6f, VIOLET);
        p.pushPose();
        px(p, 0, -6.4f, 2.55f);
        float fly = c.flying();
        float lying = Mth.clamp(bodyPitch / 1.3f, 0, 1);
        float lift = Math.max(0, c.lift()) * (1 - .6f * lying);
        float flap = .02f + .05f * Math.max(0, c.lift()) + .04f * Math.abs(c.speed()) + .05f * fly;
        // Floating, it hangs back and out to one side and billows; standing, it falls straight.
        p.mulPose(Axis.XP.rotation(.04f + lift * .75f + .22f * fly));
        p.mulPose(Axis.ZP.rotation(Mth.clamp(c.side(), -.35f, .35f) * .45f + .1f * fly * Mth.sin(time * .03f)));
        for (int row = 0; row < 6; row++) {
            float wave = (float) Math.sin(time * (.13 + .25 * lift + .1 * fly) - row * .95) * flap * (row + 1) * .5f;
            p.mulPose(Axis.XP.rotation(lift * .1f * row + wave - c.speed() * .2f * row + .05f * fly));
            float widen = row * .45f;
            part(p, b, light, 0, 2.1f, 0, 11.4f + widen, 4.3f, .5f, VIOLET);
            // Folds: lit ridges and shadowed troughs, more of them as the cape widens.
            for (int f = 0; f < 5; f++) {
                float x = (-4.6f + f * 2.3f) * (1 + widen / 11.4f);
                part(p, b, light, x, 2.1f, f % 2 == 0 ? .35f : .12f, 2.0f, 4.3f, .45f, f % 2 == 0 ? VIOLET_LIGHT : VIOLET_DARK);
            }
            part(p, b, light, 0, 2.1f, .55f, 11.3f + widen, 4.2f, .3f, CAPE_IN);
            if (row == 5) part(p, b, light, 0, 4.0f, 0, 11.6f + widen, .6f, .65f, VIOLET_DARK);
            px(p, 0, 4.1f, 0);
        }
        p.popPose();
    }

    /** One arm for the first-person view, from the elbow down (the stack at the elbow, forearm along +y): the gauntlet and hand. */
    public static void firstPersonArm(PoseStack p, MultiBufferSource b, int light, int side, float wristX, float curl) {
        int s = side == 0 ? -1 : 1;
        part(p, b, light, 0, 2.4f, 0, 3.3f, 4.8f, 3.3f, VIOLET);
        part(p, b, light, 0, .5f, 0, 3.9f, 1.4f, 3.9f, VIOLET_LIGHT);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(wristX));
        hand(p, b, light, s, curl);
    }
    /** A faint, cold light round a hand that is working metal (the casting hand), drawn full-bright. */
    static void glowHand(PoseStack p, MultiBufferSource b, float strength) {
        if (strength <= .02f) return;
        float k = Math.min(1, strength);
        p.pushPose();
        p.scale(4.2f, 4.2f, 4.2f);
        p.translate(-.5f / 16, -.1f / 16, -.5f / 16);
        UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, .25f * k, .22f * k, .32f * k, 1);
        p.popPose();
    }
}
