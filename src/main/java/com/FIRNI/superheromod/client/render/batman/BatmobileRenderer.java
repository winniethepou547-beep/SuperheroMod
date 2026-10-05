package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.FIRNI.superheromod.heroes.batman.BatmobileEntity;
import com.FIRNI.superheromod.heroes.batman.TakedownPath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Batmobile after Arkham Knight's (the user's reference renders): long, low and wide, armoured in dark gunmetal with a
 * violet cast; four huge chunky tyres standing outside the body under angular fenders, dark rims with gold calipers; a
 * wedge nose with a splitter, a black intake and thin cold LED headlights; a ridged hood; a low tinted canopy in a frame
 * down the middle; ribbed side intakes between the wheels; a raised rear deck with a spoiler on struts and two fins
 * leaning out; at the back the big turbine (an armoured ring round a glowing core that burns brighter when it drives)
 * and slanted red tail lights. Built of boxes in blocks, +z the nose, y up, centred on its middle; the wheels turn with
 * the distance rolled (BatmobileEntity.spin).
 */
public final class BatmobileRenderer extends EntityRenderer<BatmobileEntity> {
    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 1, 1, 1);
    private static final int FULL = 15728880;
    private static final float[] BODY = {.085f, .082f, .1f}, PANEL = {.12f, .112f, .14f}, EDGE = {.19f, .185f, .22f}, DARK = {.04f, .04f, .048f},
            TYRE = {.032f, .032f, .036f}, TREAD = {.058f, .058f, .062f}, RIM = {.1f, .1f, .11f}, RIM_HI = {.24f, .24f, .26f},
            GOLD = {.55f, .41f, .14f}, GLASS = {.035f, .045f, .06f}, VENT = {.025f, .025f, .03f};
    private static final float[] HEAD = {.85f, .95f, 1f}, TAIL = {1f, .08f, .06f}, CORE = {1f, .42f, .12f};

    public BatmobileRenderer(EntityRendererProvider.Context ctx) { super(ctx); shadowRadius = 1.6f; }
    @Override public ResourceLocation getTextureLocation(BatmobileEntity e) { return GhostMaterials.TEXTURE; }
    @Override public boolean shouldRender(BatmobileEntity e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) { return true; }

    // ------------------------------------------------------------------ boxes (blocks)
    private static void box(PoseStack p, VertexConsumer v, int light, float x0, float y0, float z0, float x1, float y1, float z1, float[] c) {
        float ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1), az = Math.min(z0, z1), bz = Math.max(z0, z1);
        if (bx - ax < 1e-3 || by - ay < 1e-3 || bz - az < 1e-3) return;
        p.pushPose();
        p.translate(ax, ay, az);
        p.scale((bx - ax) * 16, (by - ay) * 16, (bz - az) * 16);
        UNIT.render(p, v, light, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1);
        p.popPose();
    }
    /** A box centred on (x, y, z), turned (x, then y, then z, radians), of size (w, h, d). */
    private static void rbox(PoseStack p, VertexConsumer v, int light, float x, float y, float z, float rx, float ry, float rz, float w, float h, float d, float[] c) {
        p.pushPose();
        p.translate(x, y, z);
        if (rx != 0) p.mulPose(Axis.XP.rotation(rx));
        if (ry != 0) p.mulPose(Axis.YP.rotation(ry));
        if (rz != 0) p.mulPose(Axis.ZP.rotation(rz));
        box(p, v, light, -w / 2, -h / 2, -d / 2, w / 2, h / 2, d / 2, c);
        p.popPose();
    }
    /** A light: the lamp itself full bright, and a soft glow round it. */
    private static void lamp(PoseStack p, MultiBufferSource b, float x, float y, float z, float rz, float w, float h, float d, float[] c, float k) {
        VertexConsumer solid = b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE));
        rbox(p, solid, FULL, x, y, z, 0, 0, rz, w, h, d, new float[]{Math.min(1, c[0] * k), Math.min(1, c[1] * k), Math.min(1, c[2] * k)});
        VertexConsumer glow = b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE));
        rbox(p, glow, FULL, x, y, z, 0, 0, rz, w + .1f, h + .08f, d + .04f, new float[]{c[0] * .35f * k, c[1] * .35f * k, c[2] * .35f * k});
    }

    @Override public void render(BatmobileEntity e, float entityYaw, float partial, PoseStack p, MultiBufferSource b, int light) {
        VertexConsumer v = b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE));
        float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
        float spin = Mth.lerp(partial, e.spinO, e.spin);
        float age = e.clock(partial);
        // How hard it is driving (the turbine burns, the nose lifts, the body leans into the slide); the boost squats it.
        float drive = TakedownPath.drive(age);
        float slide = TakedownPath.slide(age) * (e.run() == null ? 0 : e.run().side());
        float settle = -.6f * TakedownPath.boost(age) * (float) Math.exp(-Math.max(0, age - TakedownPath.IN - TakedownPath.ARC) / 6f);
        p.pushPose();
        p.mulPose(Axis.YP.rotationDegrees(-yaw));
        // The body on its springs: the slide rolls it, braking dips the nose, it rocks once as it stops.
        p.translate(0, .02, 0);
        p.mulPose(Axis.ZP.rotation(-.07f * slide));
        p.mulPose(Axis.XP.rotation(.05f * slide + .03f * settle - .02f * drive));

        // ---- the hull
        box(p, v, light, -1.0f, .32f, -2.95f, 1.0f, .55f, 2.9f, DARK);
        box(p, v, light, -1.12f, .5f, -2.85f, 1.12f, .96f, 2.55f, BODY);
        // nose: a low wedge, the splitter under it, the black intake, the cheeks
        rbox(p, v, light, 0, .66f, 2.88f, -.32f, 0, 0, 2.0f, .3f, .85f, BODY);
        box(p, v, light, -1.08f, .42f, 2.9f, 1.08f, .5f, 3.38f, DARK);
        box(p, v, light, -.55f, .5f, 3.0f, .55f, .66f, 3.12f, VENT);
        for (int s = -1; s <= 1; s += 2) rbox(p, v, light, s * .82f, .78f, 2.7f, -.25f, s * .25f, 0, .5f, .22f, .7f, PANEL);
        // hood with its ridge and two cuts
        rbox(p, v, light, 0, 1.0f, 1.75f, .17f, 0, 0, 1.75f, .18f, 1.85f, PANEL);
        rbox(p, v, light, 0, 1.1f, 1.75f, .17f, 0, 0, .24f, .08f, 1.75f, EDGE);
        for (int s = -1; s <= 1; s += 2) rbox(p, v, light, s * .55f, 1.08f, 1.9f, .17f, 0, 0, .05f, .05f, 1.4f, VENT);
        // canopy: low and tinted, in a frame down the middle
        rbox(p, v, light, 0, 1.27f, .15f, .1f, 0, 0, 1.22f, .42f, 1.95f, GLASS);
        rbox(p, v, light, 0, 1.5f, .02f, .1f, 0, 0, .3f, .07f, 2.0f, EDGE);
        for (int s = -1; s <= 1; s += 2) rbox(p, v, light, s * .63f, 1.27f, .15f, .1f, 0, s * .12f, .07f, .46f, 1.95f, EDGE);
        // rear deck, spoiler on its struts, the two fins leaning out
        box(p, v, light, -1.05f, .95f, -2.85f, 1.05f, 1.22f, -.75f, PANEL);
        rbox(p, v, light, 0, 1.24f, -1.8f, 0, 0, 0, .3f, .06f, 1.9f, EDGE);
        for (int s = -1; s <= 1; s += 2) box(p, v, light, s * .62f, 1.22f, -2.82f, s * .72f, 1.56f, -2.66f, DARK);
        box(p, v, light, -1.18f, 1.55f, -2.98f, 1.18f, 1.63f, -2.5f, EDGE);
        for (int s = -1; s <= 1; s += 2) {
            rbox(p, v, light, s * .98f, 1.55f, -2.3f, -.3f, 0, -s * .38f, .08f, .95f, 1.05f, BODY);
            rbox(p, v, light, s * 1.02f, 1.62f, -2.42f, -.3f, 0, -s * .38f, .03f, .8f, .9f, EDGE);
        }
        // side intakes between the wheels, ribbed
        for (int s = -1; s <= 1; s += 2) {
            box(p, v, light, s * 1.1f, .55f, -1.25f, s * 1.42f, 1.05f, 1.0f, BODY);
            for (int k = 0; k < 5; k++) box(p, v, light, s * 1.41f, .62f, -1.05f + k * .4f, s * 1.45f, .98f, -.85f + k * .4f, VENT);
            box(p, v, light, s * 1.1f, 1.05f, -1.25f, s * 1.38f, 1.12f, 1.0f, EDGE);
        }

        // ---- the wheels: front (smaller), rear (bigger), well outside the body, under their fenders
        wheel(p, b, light, 1.55f, .58f, 2.0f, .58f, .62f, spin);
        wheel(p, b, light, -1.55f, .58f, 2.0f, .58f, .62f, spin);
        wheel(p, b, light, 1.62f, .66f, -2.05f, .66f, .74f, spin * .58f / .66f);
        wheel(p, b, light, -1.62f, .66f, -2.05f, .66f, .74f, spin * .58f / .66f);
        for (int s = -1; s <= 1; s += 2) {
            rbox(p, v, light, s * 1.55f, 1.2f, 2.0f, 0, 0, 0, .74f, .13f, 1.3f, PANEL);
            rbox(p, v, light, s * 1.55f, 1.0f, 2.78f, .75f, 0, 0, .74f, .1f, .55f, PANEL);
            rbox(p, v, light, s * 1.55f, 1.0f, 1.22f, -.75f, 0, 0, .74f, .1f, .55f, PANEL);
            rbox(p, v, light, s * 1.62f, 1.36f, -2.05f, 0, 0, 0, .86f, .13f, 1.5f, PANEL);
            rbox(p, v, light, s * 1.62f, 1.12f, -1.12f, .75f, 0, 0, .86f, .1f, .6f, PANEL);
            rbox(p, v, light, s * 1.62f, 1.12f, -2.98f, -.75f, 0, 0, .86f, .1f, .6f, PANEL);
            box(p, v, light, s * 1.12f, 1.12f, 1.4f, s * 1.24f, 1.22f, 2.6f, EDGE);
        }

        // ---- the turbine: an armoured ring round its core
        float cz = -2.95f, cy = .92f;
        for (int i = 0; i < 10; i++) {
            float a = i * Mth.TWO_PI / 10;
            p.pushPose();
            p.translate(0, cy, cz);
            p.mulPose(Axis.ZP.rotation(a));
            box(p, v, light, -.15f, .36f, -.25f, .15f, .5f, .25f, i % 2 == 0 ? PANEL : EDGE);
            p.popPose();
        }
        box(p, v, light, -.36f, cy - .36f, cz + .05f, .36f, cy + .36f, cz + .2f, DARK);
        float burn = .45f + .55f * drive + .06f * Mth.sin((e.tickCount + partial) * .9f);
        float boost = TakedownPath.boost(age);
        float[] core = {Mth.lerp(boost, CORE[0], .35f), Mth.lerp(boost, CORE[1], .85f), Mth.lerp(boost, CORE[2], 1f)};
        lamp(p, b, 0, cy, cz - .02f, 0, .5f, .5f, .08f, core, burn);
        lamp(p, b, 0, cy, cz - .04f, Mth.PI / 4, .36f, .36f, .06f, new float[]{1f, .75f, .35f}, burn);
        // ---- the guns: a pod either side of the hood, barrels forward, kicking back as each fires
        for (int g = 0; g < 2; g++) {
            float gx = g == 0 ? -TakedownPath.GUN_X : TakedownPath.GUN_X;
            // Each gun fires every tick, the left half a tick after the right.
            float local = age - g * .5f;
            float kick = TakedownPath.fires((int) Math.floor(local)) ? .12f * (1 - (local - (float) Math.floor(local))) : 0;
            box(p, v, light, gx - .16f, TakedownPath.GUN_Y - .16f, TakedownPath.GUN_Z - 1.0f, gx + .16f, TakedownPath.GUN_Y + .1f, TakedownPath.GUN_Z - .45f, PANEL);
            box(p, v, light, gx - .05f, TakedownPath.GUN_Y - .07f, TakedownPath.GUN_Z - .5f - kick, gx + .05f, TakedownPath.GUN_Y + .03f, TakedownPath.GUN_Z - kick, DARK);
        }
        // ---- the lights
        for (int s = -1; s <= 1; s += 2) {
            lamp(p, b, s * .62f, .72f, 3.04f, s * -.18f, .5f, .045f, .04f, HEAD, 1);
            lamp(p, b, s * .95f, .66f, 2.92f, 0, .05f, .2f, .05f, HEAD, .9f);
            lamp(p, b, s * .86f, .84f, -2.87f, s * .45f, .34f, .05f, .04f, TAIL, 1);
            lamp(p, b, s * .8f, .72f, -2.87f, s * .45f, .26f, .05f, .04f, TAIL, .85f);
        }
        p.popPose();
    }

    /** One wheel at (x, y, z): a chunky tyre (segments with tread blocks), dark rim with spokes, a gold caliper. */
    private static void wheel(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float r, float w, float spin) {
        VertexConsumer v = b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE));
        p.pushPose();
        p.translate(x, y, z);
        float side = Math.signum(x);
        // the caliper does not turn
        rbox(p, v, light, side * .02f, .1f, r * .45f, 0, 0, 0, .22f, .3f, .22f, GOLD);
        p.mulPose(Axis.XP.rotation(spin));
        int n = 14;
        float seg = (float) (2 * r * Math.tan(Math.PI / n)) + .02f;
        for (int i = 0; i < n; i++) {
            p.pushPose();
            p.mulPose(Axis.XP.rotation(i * Mth.TWO_PI / n));
            box(p, v, light, -w / 2, r - .2f, -seg / 2, w / 2, r, seg / 2, TYRE);
            // tread: blocks alternating from either edge
            float tx = i % 2 == 0 ? -w * .25f : w * .25f;
            box(p, v, light, tx - w * .22f, r - .01f, -seg * .3f, tx + w * .22f, r + .045f, seg * .3f, TREAD);
            p.popPose();
        }
        // the rim: a dark disc (two crossed squares) with five spokes and a hub, set in from the outside
        float face = side * (w / 2 - .06f);
        for (int k = 0; k < 2; k++) {
            p.pushPose();
            p.mulPose(Axis.XP.rotation(k * Mth.PI / 4));
            box(p, v, light, face - .04f, -(r - .2f) * .8f, -(r - .2f) * .8f, face + .04f, (r - .2f) * .8f, (r - .2f) * .8f, RIM);
            p.popPose();
        }
        for (int i = 0; i < 5; i++) {
            p.pushPose();
            p.mulPose(Axis.XP.rotation(i * Mth.TWO_PI / 5));
            box(p, v, light, face + side * .02f - .03f, 0, -.05f, face + side * .02f + .03f, r - .22f, .05f, RIM_HI);
            p.popPose();
        }
        box(p, v, light, face + side * .05f - .04f, -.1f, -.1f, face + side * .05f + .04f, .1f, .1f, EDGE);
        p.popPose();
    }
}
