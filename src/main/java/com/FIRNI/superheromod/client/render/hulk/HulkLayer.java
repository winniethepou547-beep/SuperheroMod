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

    // Marvel Rivals' Hulk: olive green skin, dark navy torn shorts over a shredded purple waistband, a
    // silver gamma belt with green lights, black hair, eyes lit green.
    static final float[] SKIN = {.86f, .66f, .52f}, GREEN = {.42f, .58f, .26f}, GREEN_DARK = {.27f, .41f, .16f}, GREEN_LIGHT = {.53f, .69f, .33f},
            GREEN_DEEP = {.2f, .31f, .12f},
            SHIRT = {.56f, .46f, .72f}, PANTS = {.15f, .17f, .26f}, PANTS_DARK = {.09f, .1f, .16f}, PURPLE = {.46f, .24f, .62f},
            BANNER_HAIR = {.18f, .14f, .11f}, HULK_HAIR = {.06f, .065f, .07f}, SHOE = {.22f, .15f, .1f},
            SILVER = {.74f, .76f, .8f}, SILVER_DARK = {.4f, .42f, .47f}, GAMMA_LIT = {.55f, 1f, .32f}, GLASSES = {.08f, .08f, .09f},
            TEETH = {.92f, .9f, .8f}, MOUTH = {.16f, .05f, .05f}, EYE = {.95f, .95f, .92f};

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

        // ---- legs: thick, slightly bowed, in torn navy shorts that end at the knee; bare green calves and feet.
        float drop = Mth.clamp(pose.crouch, -1, 10);
        float fold = (float) Math.acos(Mth.clamp(1 - Math.max(0, drop) / 12f, -1, 1));
        boolean walking = e.onGround();
        float legX = l(1.95f, 3.2f, k), legW = l(4, 6.2f, k);
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side < 0;
            float lx = right ? pose.rLegX : pose.lLegX, lz = right ? pose.rLegZ : pose.lLegZ, knee = right ? pose.rKnee : pose.lKnee;
            if (walking) {
                float sw = Mth.cos(walk * .6662f + (right ? 0 : Mth.PI)) * l(1.2f, .85f, k) * amount;
                lx += sw; knee += Math.max(0, -Mth.sin(walk * .6662f + (right ? 0 : Mth.PI))) * .6f * amount;
            }
            p.pushPose();
            px(p, side * legX, 12 + drop, 0);
            rot(p, lx - fold, 0, lz + side * -.06f * k);
            legs(p, b, light, k, skin, shade, light2, side, knee + 2 * fold);
            p.popPose();
        }

        // ---- everything above the hips
        px(p, 0, drop, 0);
        px(p, 0, 12, 0);
        p.mulPose(Axis.YP.rotation(pose.torsoYaw)); p.mulPose(Axis.XP.rotation(pose.torsoPitch)); p.mulPose(Axis.ZP.rotation(pose.torsoRoll));
        px(p, 0, -12, 0);
        torso(p, b, light, k, skin, shade, light2);

        float shoulderX = l(5, 9.3f, k), shoulderY = l(2, 1.7f, k);
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
            px(p, 0, l(4, 6.2f, k), 0);
            p.mulPose(Axis.XP.rotation(-elbow));
            forearm(p, b, light, k, skin, shade, light2, pose.fists, side);
            fistsAt[right ? 0 : 1] = rootInverse.transformPosition(p.last().pose().transformPosition(0, l(7, 10.5f, k) / 16, 0, new Vector3f()));
            p.popPose();
        }

        // ---- head: sunk between Hulk's shoulders, it keeps looking where the player looks.
        p.pushPose();
        // Hulk's head sits low and forward between the traps.
        px(p, 0, l(0, 2.2f, k), l(0, -2f, k));
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

    private void legs(PoseStack p, MultiBufferSource b, int light, float k, float[] skin, float[] shade, float[] light2, int side, float knee) {
        float w = l(4, 6.4f, k);
        // Thigh in the shorts; on Hulk the cloth is split where the quads bulge through.
        centred(p, b, light, 0, 0, 0, w + .3f * k, 6.2f, w + .2f * k, PANTS);
        if (k > .05f) {
            centred(p, b, light, side * .4f, 1.6f, -w / 2 - .35f * k, w * .55f, 2.6f * k, .5f * k, skin);         // quad through a tear
            centred(p, b, light, side * -.9f, 3.4f, w / 2 + .05f, w * .5f, 1.8f * k, .4f * k, skin);               // tear at the back
            centred(p, b, light, -side * (w / 2 + .1f), 2.2f, 0, .4f * k, 2.2f * k, w * .5f, PANTS_DARK);          // seam
        }
        px(p, 0, 6, 0);
        p.mulPose(Axis.XP.rotation(knee));
        // The shorts end in a ragged hem just below the knee; then bare calf.
        float hem = l(6, 1.4f, k);
        centred(p, b, light, 0, 0, 0, w + .2f * k, hem, w + .1f * k, PANTS);
        if (k > .05f) {
            for (int i = 0; i < 4; i++) centred(p, b, light, (i - 1.5f) * w * .26f, hem, (i % 2 == 0 ? -.2f : .2f), w * .24f, .6f + .7f * ((i * 7) % 3) / 2f, w + .1f, PANTS_DARK);
            centred(p, b, light, 0, hem, 0, w * .92f, 6.2f - hem, w * .92f, skin);
            centred(p, b, light, 0, 1.8f, w * .46f, w * .7f, 3 * k, .9f * k, shade);                               // calf
            centred(p, b, light, 0, 1.4f, -w * .46f, w * .45f, 3.4f * k, .4f * k, light2);                         // shin
        }
        // Feet: shoes on Banner; on Hulk huge bare feet with toes.
        float fw = l(4.2f, 6.6f, k), fl = l(5.4f, 8.2f, k);
        centred(p, b, light, 0, 4.8f, -l(.7f, 1.7f, k), fw, l(1.4f, 1.8f, k), fl, k > .5f ? skin : SHOE);
        if (k > .5f) for (int t = 0; t < 5; t++) {
            float tw = t == (side < 0 ? 4 : 0) ? 1.7f : 1.15f;
            centred(p, b, light, (t - 2) * fw * .19f, 5.5f, -1.7f - fl / 2 - .55f, tw, 1.1f, 1.2f, t % 2 == 0 ? light2 : skin);
        }
    }

    private void torso(PoseStack p, MultiBufferSource b, int light, float k, float[] skin, float[] shade, float[] light2) {
        // A V: the chest and back very wide and deep, narrowing to a much smaller waist.
        float w = l(8, 15.5f, k), d = l(4, 9.5f, k), waist = l(8, 11.5f, k), wd = l(4, 7.6f, k);
        float[] deep = mix(shade, GREEN_DEEP, k);
        centred(p, b, light, 0, 0, 0, w, l(12, 7.2f, k), d, skin);                       // chest and back
        centred(p, b, light, 0, l(0, 6.6f, k), .3f * k, waist, l(12, 5.6f, k), wd, skin); // belly
        if (k > .05f) {
            // Traps: a mountain from the neck out to the shoulders.
            centred(p, b, light, 0, -3.2f * k, 1.4f * k, w * .74f, 3.6f * k, d * .62f, shade);
            centred(p, b, light, 0, -4.3f * k, 2f * k, w * .42f, 1.4f * k, d * .4f, shade);
            // Pecs: two slabs with a shadowed line under them and down the middle.
            for (int s = -1; s <= 1; s += 2) {
                centred(p, b, light, s * w * .235f, .6f, -d / 2 - .7f * k, w * .45f, 5 * k, 1.4f * k, light2);
                centred(p, b, light, s * w * .235f, 5.4f, -d / 2 - .45f * k, w * .42f, .7f * k, 1.1f * k, deep);
                // Lats flaring out under the arms, the serratus below them.
                centred(p, b, light, s * (w / 2 - .2f * k), 3, .8f, 1.6f * k, 5.5f * k, d * .7f, shade);
                for (int r = 0; r < 3; r++) centred(p, b, light, s * (waist / 2 + .15f), 6.8f + r * 1.3f, -wd * .2f, .6f * k, .9f * k, 1.6f * k, deep);
                // Back: the two big columns either side of the spine.
                centred(p, b, light, s * w * .2f, .8f, d / 2 + .5f * k, w * .34f, 6.5f * k, 1f * k, shade);
            }
            centred(p, b, light, 0, .5f, -d / 2 - .75f * k, .5f * k, 5.4f * k, 1.6f * k, deep);       // between the pecs
            centred(p, b, light, 0, .8f, d / 2 + .6f * k, .7f * k, 9 * k, 1f * k, deep);               // spine
            for (int r = 0; r < 3; r++) for (int s = -1; s <= 1; s += 2)
                centred(p, b, light, s * 1.45f, 6.7f + r * 1.25f, -wd / 2 - .35f * k + .3f * k, 2.5f, 1.05f * k, .6f * k, light2); // abs
            centred(p, b, light, 0, 6.6f, -wd / 2 - .2f * k, .4f * k, 4 * k, .7f * k, deep);
        }
        // Banner's shirt: whole at first, splitting into rags, gone by the time he is Hulk.
        float shirt = 1 - Mth.clamp((k - .2f) / .45f, 0, 1);
        if (shirt > 0) {
            centred(p, b, light, 0, -.1f, 0, w + .3f, 10.5f * shirt + .5f, d + .3f, SHIRT);
            centred(p, b, light, 0, -.2f, -d / 2 - .2f, 2.2f, 1.2f * shirt, .3f, new float[]{SHIRT[0] * .85f, SHIRT[1] * .85f, SHIRT[2] * .85f});
        }
        // Shorts up to the waist, the shredded purple waistband over them, then the gamma belt.
        centred(p, b, light, 0, 10.6f, .3f * k, waist + .9f * k, 2.4f, wd + .6f * k, PANTS);
        if (k > .2f) for (int i = 0; i < 7; i++) {
            float x = (i - 3) * waist * .15f;
            centred(p, b, light, x, 9.5f, -wd / 2 - .1f, waist * .13f, 1.2f + .9f * (i % 3) / 2f, .5f, PURPLE);
            centred(p, b, light, x, 9.5f, wd / 2 + .6f * k, waist * .13f, 1 + .8f * ((i + 1) % 3) / 2f, .5f, PURPLE);
        }
        beltAndBuckle(p, b, light, k, waist, wd);
    }

    /** The gamma belt: silver plates linked round the waist, green lights, the triangular buckle glowing in front. */
    private void beltAndBuckle(PoseStack p, MultiBufferSource b, int light, float k, float waist, float wd) {
        float y = 9.9f, h = l(.9f, 1.4f, k);
        centred(p, b, light, 0, y, .3f * k, waist + 1.1f * k + .3f, h, wd + .8f * k + .3f, SILVER_DARK);
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 3; i++) {
                float x = s * (2.4f + i * 1.5f) * l(.7f, 1, k);
                centred(p, b, light, x, y - .15f, -wd / 2 - .55f * k - .2f, 1.1f, h + .3f, .4f, SILVER);
                centred(p, b, FULL, x + s * .75f, y + .2f, -wd / 2 - .5f * k - .2f, .45f, h - .4f, .3f, GAMMA_LIT);
            }
            centred(p, b, light, s * (waist / 2 + .55f * k), y - .1f, .3f * k, .5f, h + .2f, wd * .5f, SILVER);
        }
        centred(p, b, light, 0, y - .2f, wd / 2 + .7f * k + .2f, waist * .45f, h + .4f, .4f, SILVER);
        // Buckle: a silver shield narrowing downward with the green core.
        float z = -wd / 2 - .9f * k - .3f;
        centred(p, b, light, 0, y - .7f * k, z, l(2, 3.6f, k), l(1, 1.3f, k), .6f, SILVER);
        centred(p, b, light, 0, y + .5f * k, z, l(1.4f, 2.6f, k), l(.6f, 1f, k), .6f, SILVER);
        centred(p, b, light, 0, y + 1.4f * k, z, l(.8f, 1.4f, k), l(.4f, .8f, k), .6f, SILVER);
        centred(p, b, FULL, 0, y - .3f * k, z - .15f, l(1, 1.8f, k), l(.8f, 1.6f, k), .5f, GAMMA_LIT);
    }

    private void arm(PoseStack p, MultiBufferSource b, int light, float k, float[] skin, float[] shade, float[] light2, int side) {
        float w = l(4, 7.2f, k), len = l(6, 8.2f, k);
        centred(p, b, light, 0, -2, 0, w, len, w, skin);
        if (k > .05f) {
            // Deltoids like boulders, a bicep in front, a tricep behind.
            centred(p, b, light, 0, -3.2f, 0, w + 2.2f * k, 4.2f * k, w + 1.6f * k, shade);
            centred(p, b, light, side * -.3f * k, -2.4f, 0, w + 1.4f * k, 5.2f * k, w + 1f * k, skin);
            centred(p, b, light, 0, 1.2f, -w / 2 - .5f * k, w * .75f, 3.8f * k, 1f * k, light2);
            centred(p, b, light, 0, .4f, w / 2 + .45f * k, w * .7f, 4.4f * k, .9f * k, shade);
        }
        float sleeve = 1 - Mth.clamp((k - .15f) / .4f, 0, 1);
        if (sleeve > 0) centred(p, b, light, 0, -2.1f, 0, w + .3f, 4.8f * sleeve, w + .3f, SHIRT);
    }

    private void forearm(PoseStack p, MultiBufferSource b, int light, float k, float[] skin, float[] shade, float[] light2, float fists, int side) {
        // Hulk's forearms flare out toward the wrist; the hands are enormous.
        float w = l(3.8f, 7.8f, k), len = l(5.4f, 7.2f, k);
        centred(p, b, light, 0, 0, 0, w, len, w, skin);
        if (k > .05f) {
            centred(p, b, light, 0, 1.2f, 0, w + 1f * k, 3.4f * k, w + .8f * k, skin);                    // the swell below the elbow
            centred(p, b, light, side * -.6f * k, 1.4f, -w / 2 - .5f * k, w * .6f, 3.6f * k, .7f * k, light2);
            centred(p, b, light, 0, 1.6f, w / 2 + .45f * k, w * .6f, 3.2f * k, .6f * k, shade);
        }
        float hand = l(3.6f, 8.4f, k), open = 1 - fists, ph = l(2.2f, 4f, k);
        float[] palm = mix(skin, shade, .35f);
        centred(p, b, light, 0, len - .3f, 0, hand, ph, hand * .82f, palm);
        if (k > .05f) {
            // Fingers: folded into a fist, or hanging open and curled.
            float fl = (2.4f + 1.4f * open) * k;
            for (int i = 0; i < 4; i++) {
                float x = (i - 1.5f) * hand * .23f;
                p.pushPose();
                px(p, x, len - .3f + ph, -hand * .41f + .9f);
                p.mulPose(Axis.XP.rotation(-(1.9f * fists + .35f)));
                centred(p, b, light, 0, -.3f, 0, hand * .2f, fl, hand * .2f, i % 2 == 0 ? light2 : skin);
                p.popPose();
            }
            // The thumb on the inside.
            p.pushPose();
            px(p, -side * hand * .5f, len + ph * .4f, -hand * .2f);
            p.mulPose(Axis.ZP.rotation(side * (.5f + .5f * fists)));
            centred(p, b, light, 0, 0, 0, hand * .22f, 2.6f * k, hand * .24f, skin);
            p.popPose();
        }
    }

    private void head(PoseStack p, MultiBufferSource b, int light, float k, float[] skin, float[] shade, HulkMotion.Pose pose) {
        float s = l(8, 8.2f, k);
        float[] deep = mix(shade, GREEN_DEEP, k);
        centred(p, b, light, 0, -s, 0, s, s, s, skin);
        // Hulk's face: a heavy brow ridge in a frown, high cheekbones, broad nose, a jaw wider than the skull.
        if (k > .05f) {
            centred(p, b, light, 0, -s - .2f * k, 0, s - .6f, 1, s - .6f, skin);                                    // crown
            for (int side = -1; side <= 1; side += 2) {
                p.pushPose(); px(p, side * s * .22f, -s * .64f, -s / 2 - .55f * k);
                p.mulPose(Axis.ZP.rotation(side * -.22f * k));
                centred(p, b, light, 0, 0, 0, s * .5f, 1.3f * k, 1.3f * k, shade);                                  // brow, angled into a scowl
                p.popPose();
                centred(p, b, light, side * s * .3f, -s * .36f, -s / 2 - .3f * k, s * .25f, 1.2f * k, .8f * k, skin);   // cheekbone
            }
            centred(p, b, light, 0, -s * .5f, -s / 2 - .65f * k, 2.4f, 2.4f * k, 1.2f * k, shade);                    // nose
            centred(p, b, light, 0, -2.4f, -s / 2 - .35f * k, s + .9f * k, 2.6f * k, 1.4f * k, skin);                 // jaw
            centred(p, b, light, 0, -.6f, -s * .1f, s + .9f * k, 1.4f * k, s * .9f, deep);                           // under the jaw
        }
        // Eyes: Banner's calm brown; Hulk's narrowed and burning green.
        boolean lit = k > .5f || pose.gamma > .4f;
        int eyeLight = lit ? FULL : light;
        float[] white = mix(EYE, new float[]{.7f, 1f, .55f}, k), iris = mix(new float[]{.35f, .22f, .12f}, GAMMA_LIT, Math.max(k, pose.gamma));
        for (int side = -1; side <= 1; side += 2) {
            float ex = side * s * .23f, ey = -s * .52f + .5f * k;
            box(p, b, eyeLight, ex - 1, ey, -s / 2 - .15f - .3f * k, 2, l(1, .6f, k), .2f, white);
            box(p, b, eyeLight, ex - .45f, ey, -s / 2 - .2f - .3f * k, .9f, l(1, .6f, k), .2f, iris);
        }
        // Mouth: a line on Banner; a grimace of bared teeth on Hulk; wide open in a roar.
        float open = pose.mouth;
        float mz = -s / 2 - .2f - .9f * k;
        box(p, b, light, -s * .3f, -2.8f, mz, s * .6f, .5f + 2.4f * open, .3f, MOUTH);
        if (k > .3f) {
            box(p, b, light, -s * .28f, -2.9f, mz - .1f, s * .56f, .45f, .3f, TEETH);
            box(p, b, light, -s * .28f, -2.75f + 2.1f * open, mz - .1f, s * .56f, .4f, .3f, TEETH);
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
        // Hair: Banner's neat brown; Hulk's black, short at the sides, swept up and back on top.
        float[] hair = mix(BANNER_HAIR, HULK_HAIR, k);
        centred(p, b, light, 0, -s - .6f - .4f * k, .3f * k, s + .4f, 1.6f + .4f * k, s + .4f - .4f * k, hair);
        centred(p, b, light, 0, -s + .5f, s / 2 - .2f, s + .4f, 2.8f - .8f * k, 1.1f, hair);
        for (int side = -1; side <= 1; side += 2) centred(p, b, light, side * (s / 2 + .1f), -s + .3f, .6f, .5f, 2.4f - .6f * k, s * .7f, hair);
        if (k > .2f) for (int i = 0; i < 5; i++) {
            p.pushPose(); px(p, (i - 2) * s * .18f, -s - 1.2f * k, -s / 2 + 1.4f + (i % 2) * .6f);
            p.mulPose(Axis.ZP.rotation((i - 2) * .18f)); p.mulPose(Axis.XP.rotation(.55f));
            box(p, b, light, -.8f, -2.4f * k, -.8f, 1.6f, 2.4f * k, 2.2f, hair);
            p.popPose();
        }
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
