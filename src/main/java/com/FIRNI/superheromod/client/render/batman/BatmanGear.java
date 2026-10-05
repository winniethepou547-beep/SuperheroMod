package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Batman's equipment as meshes of boxes, sizes in BLOCKS, lit by the light passed in (lamps and LEDs full-bright).
 * Axes (in whatever space the stack is in; in the world +Y is up):
 * <ul>
 * <li>{@link #batarang}: flat in the XZ plane (thin along Y), centred; the nose points +Z, the wings along +-X,
 *     ~0.36 across when open (spread 1), folded to ~0.15 (spread 0). Spin it about Y in flight.</li>
 * <li>{@link #grapnelGun}: the barrel along +Z, sights on top (+Y), the grip hanging toward -Y; the origin where the
 *     grip meets the body; the muzzle at (0, GUN_BARREL_Y, GUN_MUZZLE_Z).</li>
 * <li>{@link #pellet}: a small canister ~0.11 across, centred, banded in its gadget's colour.</li>
 * <li>{@link #mine}: a flat disc ~0.3 across, centred on X/Z, sitting ON the origin (its bottom at y 0, top +Y).</li>
 * </ul>
 */
public final class BatmanGear {
    private BatmanGear() {}

    /** The muzzle of the gun in its own space (blocks). */
    public static final float GUN_MUZZLE_Z = .29f, GUN_BARREL_Y = .045f;
    /** 0..1: thermal vision colours (electronics glow warm, the rest goes cool and dark); set by the first-person view. */
    public static float thermal;

    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 16, 16, 16);
    private static final int FULL = 15728880;
    private static final float[] BLACK = {.045f, .045f, .05f}, BLACK_HI = {.11f, .11f, .12f}, METAL = {.27f, .28f, .3f},
            METAL_HI = {.46f, .47f, .5f}, METAL_DARK = {.15f, .155f, .165f}, GOLD = {.82f, .63f, .17f};
    /** Band colours per gadget: smoke, flash, thermal, mine. */
    private static final float[][] BAND = {{.55f, .57f, .6f}, {.95f, .95f, .9f}, {.95f, .45f, .12f}, {.85f, .12f, .1f}};

    public static VertexConsumer buffer(MultiBufferSource b) { return b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)); }

    private static final float[] C = new float[3];
    private static float[] cool(float[] c) {
        if (thermal <= .001f) { C[0] = Math.min(1, c[0]); C[1] = Math.min(1, c[1]); C[2] = Math.min(1, c[2]); return C; }
        float lum = (c[0] + c[1] + c[2]) / 3;
        C[0] = Math.min(1, Mth.lerp(thermal, c[0], .03f + lum * .1f));
        C[1] = Math.min(1, Mth.lerp(thermal, c[1], .05f + lum * .16f));
        C[2] = Math.min(1, Mth.lerp(thermal, c[2], .1f + lum * .32f));
        return C;
    }
    /** A box centred at (x, y, z) of size (w, h, d), blocks. */
    private static void cbox(PoseStack p, VertexConsumer v, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        p.pushPose();
        p.translate(x - w / 2, y - h / 2, z - d / 2);
        p.scale(w, h, d);
        float[] k = cool(c);
        UNIT.render(p, v, light, OverlayTexture.NO_OVERLAY, k[0], k[1], k[2], 1);
        p.popPose();
    }
    /** The same turned about Y (radians) round its own centre. */
    private static void ybox(PoseStack p, VertexConsumer v, int light, float x, float y, float z, float yaw, float w, float h, float d, float[] c) {
        p.pushPose();
        p.translate(x, y, z);
        p.mulPose(Axis.YP.rotation(yaw));
        cbox(p, v, light, 0, 0, 0, w, h, d, c);
        p.popPose();
    }
    /** A lit part (an LED, a lamp): full-bright, its colour times k; in thermal vision electronics run warm. */
    private static void lamp(PoseStack p, VertexConsumer v, float x, float y, float z, float w, float h, float d, float r, float g, float b, float k) {
        p.pushPose();
        p.translate(x - w / 2, y - h / 2, z - d / 2);
        p.scale(w, h, d);
        float rr = Mth.lerp(thermal, r * k, Math.max(r * k, .95f)), gg = Mth.lerp(thermal, g * k, .5f), bb = Mth.lerp(thermal, b * k, .14f);
        UNIT.render(p, v, FULL, OverlayTexture.NO_OVERLAY, Math.min(1, rr), Math.min(1, gg), Math.min(1, bb), 1);
        p.popPose();
    }

    // ------------------------------------------------------------------ the Batarang
    /**
     * The Batarang: a matte black bat with a steel edge, the wings swept back to points, scallops along the trailing
     * edge. spread 0..1 opens the folding wings (they hinge at the body).
     */
    public static void batarang(PoseStack p, VertexConsumer v, int light, float spread) {
        float k = Mth.clamp(spread, 0, 1);
        float t = .022f;
        cbox(p, v, light, 0, 0, 0, .055f, t, .12f, BLACK);
        cbox(p, v, light, 0, 0, .07f, .035f, t, .03f, BLACK);
        cbox(p, v, light, 0, t * .5f + .002f, 0, .02f, .004f, .1f, METAL_HI);
        for (int s = -1; s <= 1; s += 2) {
            // The ears on the nose.
            cbox(p, v, light, s * .014f, 0, .092f, .01f, t, .02f, BLACK);
            p.pushPose();
            p.translate(s * .025f, 0, .02f);
            // Folded, the wing swings back along the body.
            p.mulPose(Axis.YP.rotation(-s * (1 - k) * 1.25f));
            ybox(p, v, light, s * .06f, 0, -.005f, s * .18f, .11f, t, .06f, BLACK);
            ybox(p, v, light, s * .125f, 0, -.035f, s * .55f, .07f, t, .045f, BLACK);
            ybox(p, v, light, s * .158f, 0, -.07f, s * .9f, .045f, t * .9f, .022f, BLACK_HI);
            // Steel along the leading edge; the scallops of the trailing edge cut by darker notches.
            ybox(p, v, light, s * .085f, .0005f, .024f, s * .3f, .13f, t + .004f, .008f, METAL);
            for (int i = 0; i < 2; i++) ybox(p, v, light, s * (.05f + i * .055f), 0, -.04f - i * .01f, s * .2f, .02f, t + .003f, .016f, METAL_DARK);
            p.popPose();
        }
    }

    // ------------------------------------------------------------------ the grapnel gun
    /**
     * The grapnel gun: a squared dark body with a long barrel, a pistol grip with a trigger and guard, a winch drum on
     * the side, a sight on top, a small LED; the three-pronged hook seated in the muzzle until it is fired (hookOut 1:
     * the hook is gone, only the line's eye shows).
     */
    public static void grapnelGun(PoseStack p, VertexConsumer v, int light, float hookOut) {
        // The body and the barrel.
        cbox(p, v, light, 0, .03f, .02f, .075f, .085f, .2f, METAL_DARK);
        cbox(p, v, light, 0, .045f, .19f, .052f, .052f, .16f, METAL);
        cbox(p, v, light, 0, .045f, .27f, .064f, .064f, .03f, METAL_HI);
        cbox(p, v, light, 0, .078f, .05f, .03f, .02f, .12f, BLACK);
        cbox(p, v, light, 0, .095f, .1f, .012f, .018f, .012f, METAL_HI);
        // The winch drum on the side, the spool of line.
        for (int s = -1; s <= 1; s += 2) cbox(p, v, light, s * .045f, .03f, .0f, .02f, .07f, .07f, METAL);
        cbox(p, v, light, .057f, .03f, 0, .006f, .04f, .04f, GOLD);
        // The grip, raked back, the trigger and its guard.
        p.pushPose();
        p.mulPose(Axis.XP.rotation(.25f));
        cbox(p, v, light, 0, -.065f, -.02f, .058f, .12f, .062f, BLACK);
        cbox(p, v, light, 0, -.13f, -.02f, .064f, .015f, .068f, BLACK_HI);
        p.popPose();
        cbox(p, v, light, 0, -.03f, .055f, .012f, .035f, .012f, METAL_HI);
        cbox(p, v, light, 0, -.055f, .06f, .01f, .01f, .06f, METAL);
        // The LED on the back of the body.
        lamp(p, v, 0, .06f, -.082f, .03f, .012f, .004f, .35f, .8f, 1f, .9f);
        lamp(p, v, .039f, .02f, .06f, .004f, .01f, .03f, .35f, .8f, 1f, .6f);
        // The hook seated in the muzzle: a head and three prongs folded back.
        if (hookOut < .5f) {
            cbox(p, v, light, 0, .045f, .3f, .03f, .03f, .04f, METAL_HI);
            for (int i = 0; i < 3; i++) {
                float a = i * Mth.TWO_PI / 3 + .5f;
                float x = Mth.cos(a) * .028f, y = .045f + Mth.sin(a) * .028f;
                cbox(p, v, light, x, y, .296f, .012f, .012f, .045f, METAL);
                cbox(p, v, light, x * 1.25f, .045f + (y - .045f) * 1.25f, .275f, .01f, .01f, .016f, METAL_HI);
            }
        } else cbox(p, v, light, 0, .045f, .29f, .014f, .014f, .012f, METAL_HI);
    }

    // ------------------------------------------------------------------ gadget pellets
    /** A gadget pellet: a dark canister with end caps, a band and a small light in the gadget's colour. */
    public static void pellet(PoseStack p, VertexConsumer v, int light, int gadget) {
        float[] band = BAND[Mth.clamp(gadget, 0, BAND.length - 1)];
        cbox(p, v, light, 0, 0, 0, .085f, .1f, .085f, METAL_DARK);
        cbox(p, v, light, 0, .055f, 0, .07f, .015f, .07f, METAL_HI);
        cbox(p, v, light, 0, -.055f, 0, .07f, .015f, .07f, METAL_HI);
        cbox(p, v, light, 0, 0, 0, .092f, .025f, .092f, band);
        cbox(p, v, light, 0, .066f, 0, .022f, .012f, .022f, BLACK);
        lamp(p, v, 0, .02f, -.044f, .014f, .014f, .004f, band[0], band[1], band[2], 1);
    }

    // ------------------------------------------------------------------ the mine
    /** A proximity mine: a flat dark disc (built of an octagon of boxes), a raised sensor and a blinking red light. */
    public static void mine(PoseStack p, VertexConsumer v, int light, float blink) {
        for (int i = 0; i < 4; i++) ybox(p, v, light, 0, .025f, 0, i * Mth.PI / 4, .3f, .05f, .125f, METAL_DARK);
        for (int i = 0; i < 4; i++) ybox(p, v, light, 0, .053f, 0, i * Mth.PI / 4, .22f, .012f, .09f, BLACK);
        cbox(p, v, light, 0, .064f, 0, .08f, .025f, .08f, METAL);
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI + Mth.PI / 4;
            cbox(p, v, light, Mth.cos(a) * .11f, .052f, Mth.sin(a) * .11f, .022f, .008f, .022f, METAL_HI);
        }
        float k = Mth.clamp(blink, 0, 1);
        lamp(p, v, 0, .081f, 0, .03f, .012f, .03f, 1f, .1f, .07f, .25f + .75f * k);
    }
}
