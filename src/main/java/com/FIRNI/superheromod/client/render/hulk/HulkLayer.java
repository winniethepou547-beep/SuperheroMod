package com.FIRNI.superheromod.client.render.hulk;

import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * Bruce Banner and Hulk, one body that grows between them (the player model is hidden for this hero).
 * Every part is a box whose size, place and colour run from Banner's to Hulk's with `size`: a wider,
 * deeper chest with pecs, abs and traps, a head sunk between the shoulders, huge forearms and fists,
 * thick short legs, bare feet; Banner's shirt and glasses tear away as he grows, the purple trousers
 * end as ragged shorts. Model space: pixels/16, +y down, -z in front, -x is his right.
 */
public final class HulkLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** One pixel cube; every box is this one, scaled. */
    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 1, 1, 1);
    private static final int FULL = 15728880;
    /** How much bigger Hulk stands overall than Banner. */
    public static final float HULK_SCALE = 1.5f;

    static final float[] SKIN = {.86f, .66f, .52f}, GREEN = {.33f, .55f, .22f}, GREEN_DARK = {.24f, .42f, .15f}, GREEN_LIGHT = {.42f, .65f, .28f},
            SHIRT = {.62f, .7f, .8f}, PANTS = {.33f, .2f, .45f}, PANTS_DARK = {.24f, .14f, .33f}, BANNER_HAIR = {.3f, .2f, .13f},
            HULK_HAIR = {.07f, .07f, .08f}, SHOE = {.25f, .17f, .1f}, BELT = {.12f, .09f, .07f}, GLASSES = {.08f, .08f, .09f},
            TEETH = {.92f, .9f, .8f}, MOUTH = {.18f, .05f, .05f}, EYE = {.95f, .95f, .92f};

    public HulkLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    private static float l(float a, float b, float k) { return a + (b - a) * k; }
    private static float[] mix(float[] a, float[] b, float k) { return new float[]{l(a[0], b[0], k), l(a[1], b[1], k), l(a[2], b[2], k)}; }
    /** A box in pixels: corner (x, y, z), size (w, h, d). */
    static void box(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        if (w <= .01f || h <= .01f || d <= .01f) return;
        p.pushPose();
        p.translate(x / 16, y / 16, z / 16);
        p.scale(w, h, d);
        GhostMaterials.draw(UNIT, p, b, light, c[0], c[1], c[2]);
        p.popPose();
    }
    /** A box centred on x (and on z), standing from y down by h. */
    static void centred(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        box(p, b, light, x - w / 2, y, z - d / 2, w, h, d, c);
    }
    private static void px(PoseStack p, double x, double y, double z) { p.translate(x / 16, y / 16, z / 16); }
    private static void rot(PoseStack p, float x, float y, float z) { p.mulPose(new Quaternionf().rotationZYX(z, y, x)); }

    @Override
    public void render(PoseStack p, MultiBufferSource b, int light, AbstractClientPlayer e, float walk, float amount, float partial, float time, float yaw, float pitch) {
        if (!HulkClient.isHero(e) || e.isInvisible()) return;
        var model = getParentModel();
        HulkMotion.Pose pose = HulkClient.pose(e, partial, time);
        HulkClient.State state = HulkClient.get(e);
        float k = Mth.clamp(pose.size, 0, 1);
        float skinK = Mth.clamp((k - .05f) / .6f, 0, 1);
        float[] skin = mix(SKIN, GREEN, skinK), shade = mix(new float[]{SKIN[0] * .85f, SKIN[1] * .85f, SKIN[2] * .85f}, GREEN_DARK, skinK);
        float[] light2 = mix(SKIN, GREEN_LIGHT, skinK);

        // The layer's own frame, to place the carried rock between the two fists afterwards.
        Matrix4f rootInverse = new Matrix4f(p.last().pose()).invert();
        Vector3f[] fistsAt = new Vector3f[2];
        p.pushPose();
        // Grow from the feet: the whole body scales up around the ground point.
        float scale = 1 + (HULK_SCALE - 1) * k;
        px(p, 0, 24, 0); p.scale(scale, scale, scale); px(p, 0, -24, 0);
        px(p, 0, -pose.rise, 0);
        px(p, 0, 12, 0); p.mulPose(Axis.XP.rotation(pose.bodyPitch)); px(p, 0, -12, 0);

        // ---- legs (thick and short on Hulk: his weight is all in the chest and arms)
        float drop = Mth.clamp(pose.crouch, -1, 10);
        float fold = (float) Math.acos(Mth.clamp(1 - Math.max(0, drop) / 12f, -1, 1));
        boolean walking = e.onGround();
        float legX = l(1.95f, 2.9f, k), legW = l(4, 5.6f, k);
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side < 0;
            float lx = right ? pose.rLegX : pose.lLegX, lz = right ? pose.rLegZ : pose.lLegZ, knee = right ? pose.rKnee : pose.lKnee;
            if (walking) {
                float sw = Mth.cos(walk * .6662f + (right ? 0 : Mth.PI)) * l(1.2f, .9f, k) * amount;
                lx += sw; knee += Math.max(0, -Mth.sin(walk * .6662f + (right ? 0 : Mth.PI))) * .6f * amount;
            }
            p.pushPose();
            px(p, side * legX, 12 + drop, 0);
            rot(p, lx - fold, 0, lz);
            centred(p, b, light, 0, 0, 0, legW, 6.2f, legW, PANTS);
            // Hulk's thigh muscle bulging through the torn cloth.
            centred(p, b, light, side * -.3f * k, 1, -legW / 2 - .2f * k, legW * .7f * k, 3.5f * k, .6f * k, PANTS_DARK);
            px(p, 0, 6, 0);
            p.mulPose(Axis.XP.rotation(knee + 2 * fold));
            // Shin: trouser leg on Banner, a ragged hem then bare green calf on Hulk.
            float hem = l(6, 1.6f, k);
            centred(p, b, light, 0, 0, 0, legW - .1f, hem, legW - .1f, PANTS);
            if (k > .05f) {
                for (int i = 0; i < 3; i++) centred(p, b, light, (i - 1) * legW * .3f, hem, 0, legW * .28f, .8f + .5f * (i % 2), legW, PANTS_DARK);
                centred(p, b, light, 0, hem, 0, legW * .95f, 6 - hem, legW * .95f, skin);
                centred(p, b, light, 0, 1.5f, -legW / 2, legW * .6f, 2.5f * k, .5f * k, light2);
            }
            // Feet: shoes on Banner, bare green feet on Hulk.
            centred(p, b, light, 0, 5, -l(.7f, 1.4f, k), l(4.2f, 6, k), l(1.4f, 1.6f, k), l(5.4f, 7.5f, k), k > .5f ? skin : SHOE);
            p.popPose();
        }

        // ---- everything above the hips
        px(p, 0, drop, 0);
        px(p, 0, 12, 0);
        p.mulPose(Axis.YP.rotation(pose.torsoYaw)); p.mulPose(Axis.XP.rotation(pose.torsoPitch)); p.mulPose(Axis.ZP.rotation(pose.torsoRoll));
        px(p, 0, -12, 0);
        torso(p, b, light, k, skin, shade, light2);

        float shoulderX = l(5, 8.4f, k), shoulderY = l(2, 2.4f, k);
        float swingArms = walking && state != null && state.action == IDLE ? amount : 0;
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side < 0;
            float ax = right ? pose.rArmX : pose.lArmX, ay = right ? pose.rArmY : pose.lArmY, az = right ? pose.rArmZ : pose.lArmZ;
            float elbow = right ? pose.rElbow : pose.lElbow;
            ax += Mth.cos(walk * .6662f + (right ? Mth.PI : 0)) * .7f * swingArms;
            p.pushPose();
            px(p, side * shoulderX, shoulderY, 0);
            rot(p, ax, ay, az);
            arm(p, b, light, k, skin, shade, light2, side);
            px(p, 0, l(4, 4.6f, k), 0);
            p.mulPose(Axis.XP.rotation(-elbow));
            forearm(p, b, light, k, skin, shade, light2, pose.fists, side);
            fistsAt[right ? 0 : 1] = rootInverse.transformPosition(p.last().pose().transformPosition(0, l(7, 9.5f, k) / 16, 0, new Vector3f()));
            p.popPose();
        }

        // ---- head: sunk between Hulk's shoulders, it keeps looking where the player looks.
        p.pushPose();
        px(p, 0, l(0, 1.6f, k), l(0, -1.2f, k));
        float headYaw = model.head.yRot - pose.torsoYaw + pose.headYaw;
        float headPitch = model.head.xRot + pose.headPitch - pose.torsoPitch * .6f - pose.bodyPitch * .8f;
        p.mulPose(Axis.YP.rotation(headYaw)); p.mulPose(Axis.XP.rotation(headPitch));
        head(p, b, light, k, skin, shade, pose);
        p.popPose();

        // Gamma: a green glow coming off him while he is charged or raging.
        if (pose.gamma > .03f) glow(p, b, pose.gamma, time, k);
        p.popPose();

        // The rock he is carrying, between his hands.
        if (pose.rock > .5f && state != null && fistsAt[0] != null && fistsAt[1] != null) {
            p.pushPose();
            Vector3f mid = new Vector3f(fistsAt[0]).add(fistsAt[1]).mul(.5f);
            // Pushed a little away from the body, so it sits in the hands rather than in the chest.
            p.translate(mid.x, mid.y, mid.z - .25f * scale);
            float size = 1.15f * scale;
            p.scale(size, size, size);
            p.mulPose(Axis.YP.rotationDegrees(25));
            p.translate(-.5, -.5, -.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state.rockState(), p, b, light, OverlayTexture.NO_OVERLAY);
            p.popPose();
        }
    }

    private void torso(PoseStack p, MultiBufferSource b, int light, float k, float[] skin, float[] shade, float[] light2) {
        float w = l(8, 13.5f, k), d = l(4, 8.5f, k);
        // The trunk, then the shape on it: traps, pecs, abs, lats.
        centred(p, b, light, 0, 0, 0, w, 12, d, skin);
        if (k > .05f) {
            centred(p, b, light, 0, -1.2f * k, .5f, w * .62f, 2.6f * k, d * .7f, shade);                          // traps
            for (int s = -1; s <= 1; s += 2) {
                centred(p, b, light, s * w * .23f, 1, -d / 2 - .55f * k, w * .44f, 4.2f * k, 1.1f * k, light2);  // pecs
                centred(p, b, light, s * (w / 2 + .3f * k), 2.5f, 0, .9f * k, 6 * k, d * .7f, shade);             // lats
            }
            for (int r = 0; r < 3; r++) for (int s = -1; s <= 1; s += 2)
                centred(p, b, light, s * 1.25f, 5.6f + r * 1.9f, -d / 2 - .25f * k, 2.2f, 1.5f * k, .5f * k, light2); // abs
        }
        // Banner's shirt: whole at first, splitting into rags, gone by the time he is Hulk.
        float shirt = 1 - Mth.clamp((k - .2f) / .45f, 0, 1);
        if (shirt > 0) {
            centred(p, b, light, 0, -.1f, 0, w + .3f, 10.5f * shirt + .5f, d + .3f, SHIRT);
            centred(p, b, light, 0, -.2f, -d / 2 - .2f, 2.2f, 1.2f * shirt, .3f, new float[]{SHIRT[0] * .85f, SHIRT[1] * .85f, SHIRT[2] * .85f});
        }
        // Trousers up to the waist and a belt; on Hulk the waistband is all that holds.
        centred(p, b, light, 0, 10, 0, w + .2f, 2.2f, d + .2f, PANTS);
        centred(p, b, light, 0, 9.6f, 0, w + .35f, .9f, d + .35f, k > .5f ? PANTS_DARK : BELT);
    }

    private void arm(PoseStack p, MultiBufferSource b, int light, float k, float[] skin, float[] shade, float[] light2, int side) {
        float w = l(4, 6.6f, k), len = l(6, 6.6f, k);
        centred(p, b, light, 0, -2, 0, w, len, w, skin);
        if (k > .05f) {
            centred(p, b, light, 0, -2.6f, 0, w + 1.2f * k, 3 * k, w + .9f * k, shade);                      // delt
            centred(p, b, light, 0, .2f, -w / 2 - .4f * k, w * .7f, 3.4f * k, .8f * k, light2);             // bicep
        }
        float sleeve = 1 - Mth.clamp((k - .15f) / .4f, 0, 1);
        if (sleeve > 0) centred(p, b, light, 0, -2.1f, 0, w + .3f, 4.8f * sleeve, w + .3f, SHIRT);
    }

    private void forearm(PoseStack p, MultiBufferSource b, int light, float k, float[] skin, float[] shade, float[] light2, float fists, int side) {
        // Hulk's forearms flare out toward the wrist; the fist is enormous.
        float w = l(3.8f, 7, k), len = l(5.4f, 6.4f, k);
        centred(p, b, light, 0, 0, 0, w, len, w, skin);
        if (k > .05f) centred(p, b, light, 0, 1, -w / 2 - .3f * k, w * .8f, 3.6f * k, .6f * k, shade);
        float fist = l(3.6f, 7.4f, k), open = 1 - fists;
        centred(p, b, light, 0, len - .2f, 0, fist, l(2.2f, 4.2f, k) + open * 1.6f * k, fist, shade);
        if (k > .05f && fists > .5f) for (int i = 0; i < 4; i++)                                             // knuckles
            centred(p, b, light, (i - 1.5f) * fist * .22f, len + l(2.2f, 4.2f, k) - .6f, -fist / 2 + .1f, fist * .18f, .9f * k, .9f * k, light2);
    }

    private void head(PoseStack p, MultiBufferSource b, int light, float k, float[] skin, float[] shade, HulkMotion.Pose pose) {
        float s = l(8, 8.6f, k);
        centred(p, b, light, 0, -s, 0, s, s, s, skin);
        // Hulk's face: a heavy brow ridge, broad nose, a jaw that juts; open and roaring when angry.
        if (k > .05f) {
            centred(p, b, light, 0, -s * .62f, -s / 2 - .55f * k, s + .2f, 1.3f * k, 1.2f * k, shade);   // brow
            centred(p, b, light, 0, -s * .44f, -s / 2 - .5f * k, 2.2f, 2 * k, 1 * k, shade);               // nose
            centred(p, b, light, 0, -1.6f, -s / 2 - .3f * k, s * .8f, 2.2f * k, 1 * k, skin);              // jaw
        }
        // Eyes: Banner's calm brown; Hulk's narrowed and green-lit with gamma.
        int eyeLight = pose.gamma > .4f ? FULL : light;
        float[] iris = mix(new float[]{.35f, .22f, .12f}, new float[]{.45f, .95f, .35f}, Math.max(k * .6f, pose.gamma));
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * s * .23f, ey = -s * .52f + .4f * k;
            box(p, b, eyeLight, ex - 1, ey, -s / 2 - .15f, 2, l(1, .7f, k), .2f, EYE);
            box(p, b, eyeLight, ex - .45f, ey, -s / 2 - .2f, .9f, l(1, .7f, k), .2f, iris);
        }
        // Mouth: a line on Banner; bared teeth on Hulk; wide open in a roar.
        float open = pose.mouth;
        box(p, b, light, -s * .28f, -2.6f, -s / 2 - .2f - .4f * k, s * .56f, .5f + 2.2f * open, .3f, MOUTH);
        if (k > .3f) {
            box(p, b, light, -s * .26f, -2.7f, -s / 2 - .3f - .4f * k, s * .52f, .45f, .3f, TEETH);
            box(p, b, light, -s * .26f, -2.6f + 1.9f * open, -s / 2 - .3f - .4f * k, s * .52f, .4f, .3f, TEETH);
        }
        // Banner's glasses, gone the moment he starts to change.
        if (k < .15f) {
            for (int side = -1; side <= 1; side += 2) {
                float ex = side * s * .23f, ey = -s * .52f - .3f;
                box(p, b, light, ex - 1.3f, ey, -s / 2 - .35f, 2.6f, .35f, .2f, GLASSES);
                box(p, b, light, ex - 1.3f, ey + 1.5f, -s / 2 - .35f, 2.6f, .35f, .2f, GLASSES);
                box(p, b, light, side > 0 ? ex - 1.3f : ex + 1f, ey, -s / 2 - .35f, .3f, 1.8f, .2f, GLASSES);
            }
            box(p, b, light, -.6f, -s * .52f, -s / 2 - .35f, 1.2f, .3f, .2f, GLASSES);
        }
        // Hair: Banner's neat brown; Hulk's black, thick and wild.
        float[] hair = mix(BANNER_HAIR, HULK_HAIR, k);
        centred(p, b, light, 0, -s - .6f - .6f * k, 0, s + .5f, 1.6f + .8f * k, s + .5f, hair);
        centred(p, b, light, 0, -s + .5f, s / 2 - .2f, s + .5f, 3 + 2.5f * k, 1.2f + .4f * k, hair);
        if (k > .2f) for (int i = 0; i < 5; i++) {
            p.pushPose(); px(p, (i - 2) * s * .2f, -s - 1.4f * k, -s / 2 + 1 + (i % 2));
            p.mulPose(Axis.ZP.rotation((i - 2) * .25f)); p.mulPose(Axis.XP.rotation(-.4f));
            box(p, b, light, -.8f, -2.2f * k, -.8f, 1.6f, 2.2f * k, 1.6f, hair);
            p.popPose();
        }
        for (int side = -1; side <= 1; side += 2) centred(p, b, light, side * (s / 2 + .1f), -s + .4f, .5f, .5f, 3, s * .7f, hair);
    }

    /** Gamma: soft green light round his chest, fists and eyes, flickering with the anger. */
    private void glow(PoseStack p, MultiBufferSource b, float gamma, float time, float k) {
        p.pushPose();
        p.scale(1 / 16f, 1 / 16f, 1 / 16f);
        var m = p.last().pose();
        float flicker = .8f + .2f * (float) Math.sin(time * 1.3);
        var v = b.getBuffer(FilmFx.ADD);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10 + time * .05, r = l(6, 10, k);
            float y = (float) (2 + 9 * (.5 + .5 * Math.sin(time * .2 + i)));
            com.FIRNI.superheromod.client.render.thor.ThorBolts.cross(v, m, new net.minecraft.world.phys.Vec3(Math.cos(a) * r, y, Math.sin(a) * r),
                    new net.minecraft.world.phys.Vec3(Math.cos(a) * r * 1.1, y - 3, Math.sin(a) * r * 1.1), 2.2 * gamma, 0x5aff3a, .14f * gamma * flicker);
        }
        p.popPose();
    }
}
