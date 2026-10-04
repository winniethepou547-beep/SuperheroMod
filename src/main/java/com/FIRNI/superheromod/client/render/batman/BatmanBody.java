package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.cloth.CapeCloth;
import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
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

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;

/**
 * Batman's body, built of boxes after the Arkham Origins figure, darkened toward the near-black Minecraft Batman the user
 * liked: a heavy, armoured, broad-shouldered build with a narrow waist, thick forearms and calves. Dark charcoal armour
 * panels over a near-black weave (lighter and darker panels hint at the carbon texture); big sculpted chest plates and
 * banded abdominals; a raised black bat across the chest; thick black neck and shoulder armour, segmented shoulder pads
 * riding on the upper arms; gauntlets with scalloped fins standing out of the outer forearm and armoured knuckles; the
 * gold utility belt with its pouches and the black bat buckle; armoured knee pads, shin guards, heavy boots; the black
 * cowl with medium ears raked forward, the jaw and mouth bare, glowing white-blue eye slits. The cape is not drawn here:
 * the body reports where it hangs from (and, gliding, the arm line it spreads along) to {@link CapeCloth}.
 * <p>
 * Model space as the player model's (pixels/16, +y down, -z in front, -x his right; neck at y 0, hips at 12, feet at
 * 24); the joints are the same chain as Black Panther's and Magneto's (PantherMotion.Pose, signs at the top of
 * PantherMotion), so the same poses and SpikePull work. Shoulder joints sit at +-SHOULDER (5.3 px): he is broad.
 */
public final class BatmanBody {
    private BatmanBody() {}

    static final float SHOULDER = 5.3f;
    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 1, 1, 1);
    private static final int FULL = 15728880;
    static final float[] SUIT = {.105f, .108f, .118f}, SUIT_LIGHT = {.155f, .16f, .172f}, SUIT_DARK = {.068f, .07f, .078f},
            ARMOR = {.05f, .05f, .056f}, ARMOR_HI = {.125f, .127f, .137f}, SEAM = {.028f, .028f, .032f},
            SKIN = {.80f, .62f, .52f}, SKIN_SHADE = {.63f, .47f, .39f}, MOUTH = {.40f, .24f, .21f}, EYE_SOCKET = {.02f, .02f, .025f},
            GOLD = {.82f, .63f, .17f}, GOLD_DARK = {.52f, .38f, .09f}, GOLD_LIGHT = {.96f, .82f, .38f}, LENS = {.84f, .93f, 1f};
    /** What a hand holds while it is drawn (set by whoever draws him, per side: 0 right, 1 left). */
    public static final int HOLD_NONE = 0, HOLD_GUN = 1, HOLD_BATARANG = 2, HOLD_FAN = 3, HOLD_PELLET = 4, HOLD_MINE = 5, HOLD_REMOTE = 6;
    /** How far each wrist cannon is open (side 0 right, 1 left; 0 = plain gauntlet), set by the layer per draw. */
    public static final float[] CANNON = {0, 0};
    /** How much of each electric gauntlet shows (side 0 right, 1 left), and their charge, set by the layer per draw. */
    public static final float[] SHOCK = {0, 0};
    public static float shockEnergy = 1;
    /** The right hand holds the cape's edge (0..1: the reflex block's cape sweep), set by the layer per draw. */
    public static float capeGrab;
    public static final int[] HOLD = {HOLD_NONE, HOLD_NONE};
    /** The held thing's number: the fan's count, the pellet's gadget, the gun's hook out (0..1), the mine's blink. */
    public static final float[] HOLD_ARG = {0, 0};
    /** 0..1: drawn in thermal vision colours (cool blue-black armour, warm electronics); set by the first-person view. */
    public static float thermal;

    /** When on, a draw records the hands, the gun's muzzle and the eyes in the world (null when not drawn). */
    public static boolean capture;
    public static Vec3 handRight, handLeft, muzzle, eyes;
    /** The cape's frame for this draw (null: no cape). */
    private static CapeCloth.Frame cape;

    private static void px(PoseStack p, double x, double y, double z) { p.translate(x / 16, y / 16, z / 16); }
    private static void rot(PoseStack p, float x, float y, float z) { p.mulPose(new Quaternionf().rotationZYX(z, y, x)); }
    private static final float[] TINT = new float[3];
    /** The colour as drawn: clamped (over 1 wraps in the vertex bytes), pulled toward thermal colours. */
    private static float[] tint(float[] c) {
        if (thermal <= .001f) { TINT[0] = Math.min(1, c[0]); TINT[1] = Math.min(1, c[1]); TINT[2] = Math.min(1, c[2]); return TINT; }
        float lum = (c[0] + c[1] + c[2]) / 3;
        float tr = .03f + lum * .12f, tg = .05f + lum * .18f, tb = .11f + lum * .35f;
        TINT[0] = Math.min(1, Mth.lerp(thermal, c[0], tr)); TINT[1] = Math.min(1, Mth.lerp(thermal, c[1], tg)); TINT[2] = Math.min(1, Mth.lerp(thermal, c[2], tb));
        return TINT;
    }
    /** A box centred on (x, y, z) in pixels, turned (x then y then z), of size (w, h, d). */
    private static void part(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float rx, float ry, float rz, float w, float h, float d, float[] c) {
        if (w <= .01f || h <= .01f || d <= .01f) return;
        p.pushPose();
        px(p, x, y, z);
        if (rx != 0 || ry != 0 || rz != 0) rot(p, rx, ry, rz);
        p.translate(-w / 32, -h / 32, -d / 32);
        p.scale(w, h, d);
        float[] k = tint(c);
        UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY, k[0], k[1], k[2], 1);
        p.popPose();
    }
    private static void part(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        part(p, b, light, x, y, z, 0, 0, 0, w, h, d, c);
    }
    /** A glowing box (the eye slits): full-bright core plus a soft additive halo. */
    private static void glow(PoseStack p, MultiBufferSource b, float x, float y, float z, float rz, float w, float h, float d, float k) {
        p.pushPose();
        px(p, x, y, z);
        if (rz != 0) p.mulPose(Axis.ZP.rotation(rz));
        p.pushPose();
        p.translate(-w / 32, -h / 32, -d / 32);
        p.scale(w, h, d);
        UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY,
                Math.min(1, LENS[0] * k), Math.min(1, LENS[1] * k), Math.min(1, LENS[2] * k), 1);
        p.popPose();
        float hw = w + .7f, hh = h + .5f, hd = d + .15f;
        p.translate(-hw / 32, -hh / 32, -hd / 32 - .1f / 16);
        p.scale(hw, hh, hd);
        UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, .22f * k, .3f * k, .42f * k, 1);
        p.popPose();
    }

    /**
     * Draws him at the model root: the pose, where the head looks (radians, relative to the body), the free clock, an
     * extra turn of the whole body about the vertical through his middle (align, radians; the roll and the pull face
     * along their way), and the cape's frame to fill (or null).
     */
    public static void draw(PoseStack p, MultiBufferSource b, int light, Pose pose, float lookYaw, float lookPitch, float time, float align, CapeCloth.Frame capeFrame) {
        float[] v = pose.v;
        cape = capeFrame;
        if (cape != null) cape.reset();
        if (capture) handRight = handLeft = muzzle = eyes = null;
        p.pushPose();
        // The extra turn first (about the vertical through his middle), so the root's shifts go along it too.
        if (align != 0) p.mulPose(Axis.YP.rotation(align));
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
            px(p, s * 2.15f, 0, 0);
            rot(p, legX, s < 0 ? v[o + LEG_Y] : -v[o + LEG_Y], s < 0 ? v[o + LEG_Z] : -v[o + LEG_Z]);
            leg(p, b, light, side, knee, ankle);
            p.popPose();
        }
        hips(p, b, light);
        if (cape != null) {
            cape.mark(0, p, -2.2f, -1.2f, .5f); cape.mark(1, p, 2.2f, -1.2f, .5f);
            // The belt and its back pouches stand out past the torso capsules: one more across the waist keeps the cape off them.
            cape.mark(2, p, -3.9f, -1.3f, .9f);
            cape.capsule(2, p, 3.9f, -1.3f, .9f, 3.0f, true);
        }
        p.mulPose(Axis.YP.rotation(v[SPINE_YAW])); p.mulPose(Axis.XP.rotation(v[SPINE_PITCH])); p.mulPose(Axis.ZP.rotation(v[SPINE_ROLL]));
        abdomen(p, b, light);
        px(p, 0, -5.6f, 0);
        p.mulPose(Axis.YP.rotation(v[CHEST_YAW])); p.mulPose(Axis.XP.rotation(v[CHEST_PITCH])); p.mulPose(Axis.ZP.rotation(v[CHEST_ROLL]));
        chest(p, b, light);
        if (cape != null) capeTop(p);
        for (int side = 0; side < 2; side++) {
            int s = side == 0 ? -1 : 1;
            int o = side == 0 ? R : L;
            p.pushPose();
            px(p, s * SHOULDER, -5.4f - v[o + SH_UP] * .6f, -v[o + SH_FWD] * .6f);
            rot(p, v[o + ARM_X], s < 0 ? v[o + ARM_Y] : -v[o + ARM_Y], s < 0 ? v[o + ARM_Z] : -v[o + ARM_Z]);
            arm(p, b, light, side, v[o + ELBOW], v[o + WRIST_X], v[o + WRIST_Z], v[o + CURL]);
            p.popPose();
        }
        p.pushPose();
        px(p, 0, -6.6f, -v[NECK]);
        p.mulPose(Axis.YP.rotation(lookYaw + v[HEAD_YAW]));
        p.mulPose(Axis.XP.rotation(Mth.clamp(lookPitch + v[HEAD_PITCH], -1.4f, 1.3f)));
        p.mulPose(Axis.ZP.rotation(v[HEAD_ROLL]));
        head(p, b, light, time);
        if (cape != null) cape.capsule(p, 0, -4.2f, .3f, 0, -4.2f, .3f, 4.6f, false);
        p.popPose();
        p.popPose();
        p.popPose();
        cape = null;
    }

    // ------------------------------------------------------------------ the cape's frame
    /** In the chest's frame: the shoulder line it hangs from, the torso capsules (from the hips), the facing, the neck for the wing. */
    private static void capeTop(PoseStack p) {
        for (int c = 0; c < CapeCloth.COLS; c++) {
            float u = (c - 3) / 3f;            // -1 his right .. 1 his left
            float x = 5.7f * u, a = Math.abs(u);
            cape.pin(p, c, x, -6.1f - .5f * a, 3.05f - 1.1f * a * a);
        }
        cape.wing(p, 3, 0, -6.4f, 3.1f);
        cape.wing(p, 2, -SHOULDER - .6f, -5.8f, 2.5f);
        cape.wing(p, 4, SHOULDER + .6f, -5.8f, 2.5f);
        cape.facing(p);
        cape.capsule(0, p, -2.3f, -4.6f, .4f, 2.7f, true);
        cape.capsule(1, p, 2.3f, -4.6f, .4f, 2.7f, true);
    }

    // ------------------------------------------------------------------ legs: textured thighs, knee pads, shin guards, heavy boots
    private static void leg(PoseStack p, MultiBufferSource b, int light, int side, float knee, float ankle) {
        int s = side == 0 ? -1 : 1;
        // Thigh: full and muscular, panelled; the quad plate in front, a darker outer panel, seams.
        part(p, b, light, 0, 2.9f, 0, 4.6f, 6.2f, 4.6f, SUIT);
        part(p, b, light, s * -.3f, 2.6f, -2.3f, -.04f, 0, 0, 2.8f, 4.4f, .45f, SUIT_LIGHT);
        part(p, b, light, s * 2.3f, 3.0f, .1f, .4f, 5.2f, 3.4f, SUIT_DARK);
        part(p, b, light, s * .95f, 2.8f, -2.5f, .25f, 4.6f, .15f, SEAM);
        part(p, b, light, 0, 4.9f, -2.2f, 3.4f, .3f, .3f, SEAM);
        part(p, b, light, 0, 1.6f, 2.2f, 3.6f, 3.0f, .5f, SUIT_LIGHT);
        if (cape != null) cape.capsule(p, 0, 0, 0, 0, 6, 0, 2.5f, false);
        px(p, 0, 6, 0);
        p.mulPose(Axis.XP.rotation(knee));
        // The knee pad: armoured, standing out a little, a ridge down its front.
        part(p, b, light, 0, -.1f, -2.3f, 3.6f, 2.8f, 1.1f, ARMOR);
        part(p, b, light, 0, -.3f, -2.95f, .1f, 0, 0, 2.2f, 1.6f, .5f, ARMOR_HI);
        part(p, b, light, 0, .9f, -2.75f, 3.0f, .35f, .4f, SEAM);
        // The calf (thick), the shin guard in front, the boot from mid-shin down.
        part(p, b, light, 0, 1.6f, .3f, 4.3f, 3.4f, 4.4f, SUIT);
        part(p, b, light, 0, 1.4f, 1.7f, 3.8f, 2.8f, 1.6f, SUIT_LIGHT);
        part(p, b, light, 0, 2.2f, -1.95f, 3.4f, 4.2f, 1.0f, ARMOR);
        part(p, b, light, 0, 2.0f, -2.5f, .7f, 3.6f, .3f, ARMOR_HI);
        part(p, b, light, 0, 3.5f, 0, 4.7f, 2.8f, 4.7f, ARMOR);
        part(p, b, light, 0, 2.15f, 0, 4.9f, .45f, 4.9f, ARMOR_HI);
        if (cape != null) cape.capsule(p, 0, 0, 0, 0, 4.8f, 0, 2.4f, false);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(ankle));
        // The heavy boot: the foot, a capped toe, a thick sole.
        part(p, b, light, 0, .65f, -.8f, 4.5f, 1.4f, 6.0f, ARMOR);
        part(p, b, light, 0, .5f, -3.45f, .1f, 0, 0, 3.9f, 1.1f, 1.3f, ARMOR_HI);
        part(p, b, light, 0, 1.45f, -.8f, 4.7f, .35f, 6.3f, SEAM);
        part(p, b, light, 0, -.2f, .2f, 4.8f, .5f, 4.8f, ARMOR);
        if (cape != null) cape.foot(p, side, s * -.5f, .6f, 1.6f);
    }

    // ------------------------------------------------------------------ hips: the gold belt
    private static void hips(PoseStack p, MultiBufferSource b, int light) {
        part(p, b, light, 0, .4f, 0, 8.2f, 3.6f, 4.8f, SUIT);
        part(p, b, light, 0, 1.4f, -2.45f, 2.8f, 2.4f, .4f, SUIT_DARK);
        part(p, b, light, 0, 1.0f, 2.45f, 6.4f, 2.8f, .4f, SUIT_LIGHT);
        // The belt: a gold band round the waist, edged darker.
        part(p, b, light, 0, -1.35f, 0, 8.9f, 1.5f, 5.4f, GOLD);
        part(p, b, light, 0, -2.05f, 0, 9.0f, .25f, 5.5f, GOLD_DARK);
        part(p, b, light, 0, -.65f, 0, 9.0f, .25f, 5.5f, GOLD_DARK);
        // Pouches and canisters round the front and sides (gold, the lids lighter).
        float[] xs = {-1.9f, -3.1f, 1.9f, 3.1f};
        for (float x : xs) {
            part(p, b, light, x, -1.25f, -2.95f, 1.05f, 1.65f, .9f, GOLD);
            part(p, b, light, x, -2.0f, -2.95f, 1.15f, .35f, 1.0f, GOLD_LIGHT);
            part(p, b, light, x, -1.2f, -3.42f, .3f, .3f, .1f, GOLD_DARK);
        }
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * 4.5f, -1.25f, -1.3f, 0, s * .5f, 0, .9f, 1.6f, 1.1f, GOLD);
            part(p, b, light, s * 4.6f, -1.25f, .6f, .9f, 1.7f, 1.3f, GOLD);
            part(p, b, light, s * 3.6f, -1.25f, 2.75f, 1.2f, 1.5f, .8f, GOLD);
            part(p, b, light, s * 1.3f, -1.25f, 2.85f, 1.1f, 1.5f, .8f, GOLD);
        }
        // The buckle: a black bat on a gold plate.
        part(p, b, light, 0, -1.35f, -2.95f, 2.1f, 1.6f, .6f, GOLD_LIGHT);
        part(p, b, light, 0, -1.3f, -3.3f, .7f, .8f, .2f, ARMOR);
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * .55f, -1.45f, -3.3f, 0, 0, s * -.25f, .7f, .45f, .2f, ARMOR);
            part(p, b, light, s * .82f, -1.2f, -3.3f, 0, 0, s * .45f, .35f, .3f, .2f, ARMOR);
            part(p, b, light, s * .17f, -1.82f, -3.3f, .12f, .25f, .2f, ARMOR);
        }
    }
    private static void abdomen(PoseStack p, MultiBufferSource b, int light) {
        // The narrow waist under the chest's taper.
        part(p, b, light, 0, -3.0f, 0, 7.0f, 5.6f, 4.4f, SUIT);
        for (int row = 0; row < 3; row++)
            for (int s = -1; s <= 1; s += 2) {
                part(p, b, light, s * 1.15f, -1.1f - row * 1.6f, -2.3f, 1.85f, 1.35f, .5f, SUIT_LIGHT);
                part(p, b, light, s * 1.15f, -.38f - row * 1.6f, -2.45f, 1.7f, .14f, .3f, SEAM);
            }
        part(p, b, light, 0, -2.9f, -2.5f, .25f, 4.8f, .3f, SEAM);
        for (int s = -1; s <= 1; s += 2) part(p, b, light, s * 3.35f, -2.8f, -.9f, 0, 0, s * .12f, .8f, 4.6f, 2.4f, SUIT_DARK);
        part(p, b, light, 0, -3.0f, 2.25f, 5.6f, 4.8f, .4f, SUIT_DARK);
    }
    /** The chest: great sculpted plates, the raised black bat, lats, the thick neck and shoulder armour. */
    private static void chest(PoseStack p, MultiBufferSource b, int light) {
        part(p, b, light, 0, -3.3f, 0, 9.8f, 6.8f, 5.2f, SUIT);
        for (int s = -1; s <= 1; s += 2) {
            // Pecs: big plates, a shadow under each, a seam between.
            part(p, b, light, s * 2.35f, -3.75f, -2.75f, -.05f, 0, s * -.06f, 4.4f, 3.9f, .75f, SUIT_LIGHT);
            part(p, b, light, s * 2.35f, -1.75f, -2.95f, 0, 0, s * .2f, 4.0f, .3f, .3f, SEAM);
            // Lats flaring under the arms, the side panels.
            part(p, b, light, s * 4.55f, -2.4f, .2f, 0, 0, s * -.08f, 1.0f, 4.8f, 4.0f, SUIT_DARK);
            // Deltoid cap where the shoulder meets the chest.
            part(p, b, light, s * 4.7f, -5.4f, 0, 0, 0, s * .35f, 2.6f, 2.4f, 4.8f, SUIT);
            // The trapezius armour running up to the neck.
            part(p, b, light, s * 2.6f, -6.75f, .3f, 0, 0, s * .28f, 3.8f, 1.5f, 4.6f, ARMOR);
        }
        part(p, b, light, 0, -3.6f, -3.1f, .3f, 3.6f, .2f, SEAM);
        // The back: a plate across the shoulder blades.
        part(p, b, light, 0, -3.6f, 2.75f, 8.2f, 5.6f, .45f, SUIT_DARK);
        // The collar: a thick ring of black armour round the base of the neck.
        part(p, b, light, 0, -6.95f, .3f, 5.6f, 1.3f, 4.8f, ARMOR);
        part(p, b, light, 0, -6.4f, -2.45f, 4.0f, .9f, .6f, ARMOR_HI);
        // The bat: black, raised off the plates.
        bat(p, b, light);
    }
    private static void bat(PoseStack p, MultiBufferSource b, int light) {
        float z = -3.25f, d = .4f;
        p.pushPose();
        px(p, 0, .9f, 0);
        part(p, b, light, 0, -4.05f, z, .9f, 1.5f, d, ARMOR);
        part(p, b, light, 0, -4.95f, z, .55f, .5f, d, ARMOR);
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * .2f, -5.3f, z, .18f, .35f, d, ARMOR);
            part(p, b, light, s * 1.25f, -4.45f, z, 0, 0, s * -.2f, 1.8f, .75f, d, ARMOR);
            part(p, b, light, s * 2.55f, -4.6f, z, 0, 0, s * .1f, 1.2f, .85f, d, ARMOR);
            part(p, b, light, s * 3.25f, -4.9f, z, 0, 0, s * .55f, .65f, .4f, d, ARMOR);
            // The scalloped underside of the wing.
            part(p, b, light, s * 1.05f, -3.95f, z, 0, 0, s * .35f, .6f, .45f, d, ARMOR);
            part(p, b, light, s * 2.0f, -4.05f, z, 0, 0, s * .2f, .55f, .4f, d, ARMOR);
            part(p, b, light, s * 2.75f, -4.2f, z, 0, 0, s * .1f, .45f, .35f, d, ARMOR);
        }
        p.popPose();
    }

    // ------------------------------------------------------------------ arms: segmented pads, gauntlets with fins, gloves
    private static void arm(PoseStack p, MultiBufferSource b, int light, int side, float elbow, float wristX, float wristZ, float curl) {
        int s = side == 0 ? -1 : 1;
        // Shoulder pads: three overlapping black plates riding the top of the arm, standing out a little.
        part(p, b, light, s * .5f, -.85f, 0, 0, 0, s * .12f, 4.9f, 1.4f, 5.3f, ARMOR);
        part(p, b, light, s * 1.0f, .3f, 0, 0, 0, s * .2f, 4.5f, 1.2f, 5.0f, ARMOR_HI);
        part(p, b, light, s * 1.35f, 1.35f, 0, 0, 0, s * .28f, 3.9f, 1.05f, 4.6f, ARMOR);
        part(p, b, light, s * .8f, -1.6f, 0, 0, 0, s * .12f, 3.2f, .4f, 4.0f, ARMOR_HI);
        // The upper arm: thick, dark, a bicep panel.
        part(p, b, light, s * .25f, 2.6f, 0, 4.1f, 5.2f, 4.1f, SUIT);
        part(p, b, light, s * -.1f, 3.0f, -2.05f, 2.6f, 3.0f, .35f, SUIT_LIGHT);
        part(p, b, light, s * .25f, 4.6f, 1.95f, 3.0f, 1.4f, .5f, SUIT_DARK);
        if (cape != null) cape.capsule(p, 0, 0, 0, s * .3f, 5.0f, 0, 2.3f, false);
        if (cape != null) cape.wing(p, side == 0 ? 1 : 5, s * .3f, 5.0f, 1.9f);
        px(p, s * .3f, 5.0f, 0);
        p.mulPose(Axis.XP.rotation(-elbow));
        forearm(p, b, light, side);
        if (cape != null) {
            cape.capsule(p, 0, 0, 0, 0, 4.8f, 0, 2.3f, false);
            cape.wing(p, side == 0 ? 0 : 6, 0, 4.4f, 1.9f);
        }
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(wristX));
        p.mulPose(Axis.ZP.rotation(s < 0 ? wristZ : -wristZ));
        hand(p, b, light, side, curl);
    }
    /** The gauntlet: thick, flaring toward the wrist, the scalloped fins standing out of the outer edge, the cuff. */
    private static void forearm(PoseStack p, MultiBufferSource b, int light, int side) {
        int s = side == 0 ? -1 : 1;
        part(p, b, light, 0, 1.0f, 0, 3.9f, 2.2f, 3.9f, ARMOR);
        part(p, b, light, 0, 3.2f, 0, 4.3f, 2.8f, 4.3f, ARMOR);
        part(p, b, light, 0, 2.4f, -2.1f, 2.4f, 3.6f, .35f, ARMOR_HI);
        part(p, b, light, 0, 4.45f, 0, 4.5f, .5f, 4.5f, ARMOR_HI);
        fins(p, b, light, s);
        if (CANNON[side] > 0) BatmanCannonFx.gauntlet(p, b, light, side, CANNON[side]);
    }
    /** Four short scalloped blades along the outer forearm, raked back toward the elbow, shorter toward the wrist. */
    private static void fins(PoseStack p, MultiBufferSource b, int light, int s) {
        part(p, b, light, s * 2.25f, 2.4f, .7f, .5f, 4.0f, .9f, ARMOR);
        for (int i = 0; i < 4; i++) {
            float y = .9f + i * 1.05f, len = 1.9f - i * .3f;
            part(p, b, light, s * (2.3f + len * .42f), y - .2f, .9f, 0, 0, s * .62f, len, .55f, .55f, ARMOR);
            part(p, b, light, s * (2.25f + len * .75f), y - .55f, .9f, 0, 0, s * .95f, len * .45f, .35f, .45f, ARMOR_HI);
        }
    }
    /**
     * The glove: the palm, four fingers that curl (open: splayed; closed: a fist), the thumb in front, armoured knuckles
     * on the back of the hand; whatever the hand holds.
     */
    private static void hand(PoseStack p, MultiBufferSource b, int light, int side, float curl) {
        int s = side == 0 ? -1 : 1;
        part(p, b, light, 0, 1.1f, 0, 1.9f, 2.4f, 3.1f, ARMOR);
        part(p, b, light, s * .8f, 1.75f, 0, .5f, 1.2f, 3.0f, ARMOR_HI);
        for (int f = 0; f < 4; f++) {
            p.pushPose();
            px(p, s * .15f, 2.3f, -1.1f + f * .73f);
            p.mulPose(Axis.ZP.rotation(s * (curl * 1.35f + .05f)));
            part(p, b, light, 0, .7f, 0, .8f, 1.4f, .66f, ARMOR);
            part(p, b, light, s * .42f, .3f, 0, .22f, .6f, .6f, ARMOR_HI);
            px(p, 0, 1.35f, 0);
            p.mulPose(Axis.ZP.rotation(s * curl * 1.25f));
            part(p, b, light, 0, .55f, 0, .74f, 1.1f, .62f, ARMOR);
            p.popPose();
        }
        p.pushPose();
        px(p, s * -.85f, 1.3f, -1.55f);
        p.mulPose(Axis.XP.rotation(-.5f - .6f * curl));
        part(p, b, light, 0, .7f, 0, .85f, 1.6f, .85f, ARMOR);
        p.popPose();
        if (capture) { Vec3 w = world(p, 0, 1.5f, 0); if (side == 0) handRight = w; else handLeft = w; }
        if (SHOCK[side] > 0) BatmanShockFx.knuckles(p, b, light, side, SHOCK[side], shockEnergy);
        if (side == 0 && capeGrab > 0 && cape != null) cape.grab(p, 0, 1.5f, -.5f, capeGrab);
        held(p, b, light, side);
    }
    /** What the hand holds, in the hand's frame (fist centre near (0, 1.5, 0), the arm continuing along +y, the thumb toward -z). */
    private static void held(PoseStack p, MultiBufferSource b, int light, int side) {
        int what = HOLD[side];
        if (what == HOLD_NONE) return;
        var v = BatmanGear.buffer(b);
        float arg = HOLD_ARG[side];
        int s = side == 0 ? -1 : 1;
        p.pushPose();
        switch (what) {
            case HOLD_GUN -> {
                // The grip runs through the fist, the barrel along the arm on the thumb's side.
                px(p, 0, 1.5f, -.4f);
                p.mulPose(Axis.XP.rotation(-Mth.HALF_PI));
                BatmanGear.grapnelGun(p, v, light, arg);
                if (capture) muzzle = worldHere(p, 0, BatmanGear.GUN_BARREL_Y, BatmanGear.GUN_MUZZLE_Z);
            }
            case HOLD_BATARANG -> {
                // Pinched between the fingers and the thumb, flat, the wings across the hand.
                px(p, s * -.2f, 2.6f, -1.4f);
                p.mulPose(Axis.XP.rotation(.25f));
                BatmanGear.batarang(p, v, light, .15f);
            }
            case HOLD_FAN -> {
                int n = Math.max(1, Math.round(arg));
                px(p, s * -.2f, 2.5f, -1.2f);
                for (int i = 0; i < n; i++) {
                    p.pushPose();
                    float a = (i - (n - 1) / 2f) * .32f;
                    p.mulPose(Axis.XP.rotation(.2f + a));
                    p.translate(0, .07f, 0);
                    BatmanGear.batarang(p, v, light, .2f);
                    p.popPose();
                }
            }
            case HOLD_PELLET -> {
                px(p, s * -.3f, 2.4f, -.6f);
                BatmanGear.pellet(p, v, light, Math.round(arg));
            }
            case HOLD_REMOTE -> BatmanSonicFx.drawRemote(p, v, light, s, arg);
            case HOLD_MINE -> {
                px(p, s * -.9f, 2.4f, 0);
                p.mulPose(Axis.ZP.rotation(s * Mth.HALF_PI));
                BatmanGear.mine(p, v, light, arg);
            }
            default -> {}
        }
        p.popPose();
    }

    // ------------------------------------------------------------------ head: the cowl
    private static void head(PoseStack p, MultiBufferSource b, int light, float time) {
        part(p, b, light, 0, .5f, .2f, 4.2f, 1.8f, 4.0f, ARMOR);
        // The bare lower face: jaw, chin, the mouth set hard.
        part(p, b, light, 0, -1.5f, -1.3f, 6.0f, 3.0f, 5.6f, SKIN);
        part(p, b, light, 0, -.45f, -3.95f, 2.8f, 1.1f, .7f, SKIN);
        part(p, b, light, 0, -1.65f, -4.12f, 2.0f, .26f, .2f, MOUTH);
        part(p, b, light, 0, -1.25f, -4.1f, 1.6f, .2f, .2f, SKIN_SHADE);
        part(p, b, light, 0, -.05f, -3.9f, 3.0f, .25f, .6f, SKIN_SHADE);
        for (int s = -1; s <= 1; s += 2) part(p, b, light, s * 2.35f, -1.2f, -3.3f, 0, 0, s * .25f, .3f, 1.9f, .3f, SKIN_SHADE);
        // The cowl: the dome, the back down over the neck, the sides down the cheeks leaving the jaw bare.
        part(p, b, light, 0, -4.9f, .4f, 8.2f, 6.8f, 7.6f, ARMOR);
        part(p, b, light, 0, -8.5f, .4f, 7.2f, .9f, 6.8f, ARMOR);
        part(p, b, light, 0, -1.6f, 2.9f, 8.0f, 3.4f, 2.2f, ARMOR);
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * 3.25f, -2.6f, -2.0f, 0, 0, s * -.08f, 1.6f, 3.6f, 4.2f, ARMOR);
            part(p, b, light, s * 2.75f, -2.3f, -3.85f, 0, 0, s * .32f, 1.0f, 2.6f, .6f, ARMOR);
        }
        // The mask over the eyes and the nose, angled down to the cheeks; the eye sockets dark.
        part(p, b, light, 0, -5.2f, -3.85f, 7.0f, 3.8f, .7f, ARMOR);
        part(p, b, light, 0, -3.4f, -4.15f, 1.3f, 1.8f, .7f, ARMOR);
        part(p, b, light, 0, -2.75f, -4.35f, 1.0f, .6f, .4f, ARMOR_HI);
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * 1.55f, -4.75f, -4.22f, 0, 0, s * -.18f, 2.3f, .9f, .1f, EYE_SOCKET);
            // A heavy brow ridge drawn down to the nose: the scowl.
            part(p, b, light, s * 1.6f, -5.65f, -4.3f, 0, 0, s * -.32f, 2.9f, .75f, .55f, ARMOR_HI);
            part(p, b, light, s * 2.8f, -3.7f, -4.05f, 0, 0, s * .5f, 1.6f, .5f, .5f, ARMOR_HI);
        }
        // The eye slits: narrow, angled up at the outer corners, white-blue light.
        for (int s = -1; s <= 1; s += 2) glow(p, b, s * 1.55f, -4.78f, -4.3f, s * -.2f, 1.8f, .42f, .12f, .92f + .08f * Mth.sin(time * .07f));
        if (capture) eyes = world(p, 0, -4.8f, -4.4f);
        // The ears: medium, pointed, raked a little forward.
        for (int s = -1; s <= 1; s += 2) {
            part(p, b, light, s * 2.7f, -8.9f, -.3f, 0, 0, s * .06f, 1.7f, 1.6f, 1.6f, ARMOR);
            part(p, b, light, s * 2.85f, -10.15f, -.55f, -.18f, 0, s * .1f, 1.15f, 1.5f, 1.15f, ARMOR);
            part(p, b, light, s * 2.95f, -11.25f, -.8f, -.22f, 0, s * .14f, .6f,1.1f, .6f, ARMOR);
            part(p, b, light, s * 2.6f, -9.9f, -1.2f, -.18f, 0, s * .1f, .25f, 1.6f, .2f, ARMOR_HI);
        }
    }

    // ------------------------------------------------------------------ first person
    /**
     * One arm for the first-person view, from the elbow down (the stack at the elbow, the forearm along +y): the
     * gauntlet with its fins, the glove, whatever it holds (HOLD/HOLD_ARG). With capture on, it records the hand and the
     * muzzle as for the world draw (the first-person stack is in view space, which world() turns into the world).
     */
    public static void firstPersonArm(PoseStack p, MultiBufferSource b, int light, int side, float wristX, float wristZ, float curl) {
        CapeCloth.Frame saved = cape;
        cape = null;
        int s = side == 0 ? -1 : 1;
        forearm(p, b, light, side);
        px(p, 0, 4.8f, 0);
        p.mulPose(Axis.XP.rotation(wristX));
        p.mulPose(Axis.ZP.rotation(s < 0 ? wristZ : -wristZ));
        hand(p, b, light, side, curl);
        cape = saved;
    }
    /** A strip of the cape for the first-person glide (the stack at the hand, the cloth hanging back along +y and out). */
    public static void firstPersonCape(PoseStack p, MultiBufferSource b, int light, int side, float time) {
        int s = side == 0 ? -1 : 1;
        for (int i = 0; i < 5; i++) {
            float flap = .12f * Mth.sin(time * .9f - i * .9f + side) * (i + 1) / 5f;
            p.pushPose();
            px(p, s * (1.0f + i * 1.6f), 1.0f + i * 1.6f, 1.6f + i * .4f);
            p.mulPose(Axis.XP.rotation(flap));
            part(p, b, light, 0, 0, 0, 0, 0, s * -.6f, 3.4f, 3.4f, .35f, ARMOR);
            p.popPose();
        }
    }

    // ------------------------------------------------------------------ world points
    private static final Vector4f V4 = new Vector4f();
    private static final Vector3f V3 = new Vector3f();
    /** Where a point of the current model space (pixels) is in the world (through the inverse view rotation of this frame). */
    static Vec3 world(PoseStack p, float x, float y, float z) { return worldHere(p, x / 16, y / 16, z / 16); }
    /** The same for a point in blocks of the current space. */
    static Vec3 worldHere(PoseStack p, float x, float y, float z) {
        V4.set(x, y, z, 1).mul(p.last().pose());
        V3.set(V4.x, V4.y, V4.z);
        RenderSystem.getInverseViewRotationMatrix().transform(V3);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return cam.add(V3.x, V3.y, V3.z);
    }
}
