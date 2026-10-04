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
    // The face: Banner's brown eyebrows, lips and eyes; Hulk's near-black brows, white eyes with a gamma tint.
    static final float[] BROW = {.07f, .085f, .06f}, LIP = {.72f, .48f, .42f}, IRIS = {.35f, .22f, .12f},
            EYE_HULK = {.88f, .97f, .82f}, PUPIL = {.03f, .09f, .03f}, TONGUE = {.42f, .14f, .14f};
    /** The head's colours of this frame are mixed into these, so drawing the face allocates nothing. */
    private static final float[] C_DEEP = new float[3], C_HAIR = new float[3], C_BROW = new float[3], C_LIP = new float[3],
            C_WHITE = new float[3], C_IRIS = new float[3];

    public HulkLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    private static float l(float a, float b, float k) { return a + (b - a) * k; }
    private static float[] mix(float[] a, float[] b, float k) { return new float[]{l(a[0], b[0], k), l(a[1], b[1], k), l(a[2], b[2], k)}; }
    private static float[] mix(float[] out, float[] a, float[] b, float k) {
        out[0] = l(a[0], b[0], k); out[1] = l(a[1], b[1], k); out[2] = l(a[2], b[2], k);
        return out;
    }
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
    /** A slab on the face: centred on x, from y down by h, standing `out` in front of the face plane z. */
    private static void onFace(PoseStack p, MultiBufferSource b, int light, float x, float y, float face, float w, float h, float out, float[] c) {
        box(p, b, light, x - w / 2, y, face - out, w, h, out, c);
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

        // ---- head: set between Hulk's traps, it keeps looking where the player looks.
        p.pushPose();
        // Hulk's head sits low between the traps, the chin just on the chest (not pushed forward like a troll's).
        px(p, 0, l(0, .6f, k), l(0, -1.4f, k));
        float headYaw = Mth.clamp(Mth.wrapDegrees((model.head.yRot - pose.torsoYaw + pose.headYaw) * Mth.RAD_TO_DEG), -75, 75) * Mth.DEG_TO_RAD;
        // His thick neck: the head turns about its own middle (not the neck's base, which buried it in his back
        // when he looked up) and only so far up or down.
        float headPitch = Mth.clamp(model.head.xRot + pose.headPitch - pose.torsoPitch * .6f - pose.bodyPitch * .8f, l(-1.4f, -.65f, k), l(1.4f, .7f, k));
        float pivot = l(0, -4.6f, k);
        px(p, 0, pivot, 0);
        p.mulPose(Axis.YP.rotation(headYaw)); p.mulPose(Axis.XP.rotation(headPitch));
        px(p, 0, -pivot, 0);
        head(p, b, light, k, skin, shade, light2, pose);
        p.popPose();

        // Gamma: a green glow coming off him while he is charged or raging.
        if (pose.gamma > .03f) glow(p, b, pose.gamma, time, k);
        p.popPose();

        // The rock he is carrying, between his hands.
        if (pose.rock > .5f && state != null && fistsAt[0] != null && fistsAt[1] != null) {
            p.pushPose();
            Vector3f mid = new Vector3f(fistsAt[0]).add(fistsAt[1]).mul(.5f);
            // Pushed a little away from the body, so it sits in the hands rather than in the chest.
            // Held up in both hands; pushed out a little (model space: -y is up, -z in front).
            p.translate(mid.x, mid.y - .35f * scale, mid.z - .45f * scale);
            p.mulPose(Axis.YP.rotationDegrees(25));
            HulkFx.boulder(p, b, state.rockState(), light, HulkFx.BOULDER * scale / HULK_SCALE, e.getId());
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
        centred(p, b, light, 0, l(12, 6.6f, k), .3f * k, waist, l(0, 5.6f, k), wd, skin); // belly (Hulk's narrower waist)
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
        shirt(p, b, light, k, w, d);
        // Shorts up to the waist, the shredded purple waistband over them, then the gamma belt.
        centred(p, b, light, 0, 10.4f, .3f * k, waist + .5f + .9f * k, l(1.9f, 2.6f, k), wd + .5f + .6f * k, PANTS);
        if (k > .2f) for (int i = 0; i < 7; i++) {
            float x = (i - 3) * waist * .15f;
            centred(p, b, light, x, 9.5f, -wd / 2 - .1f, waist * .13f, 1.2f + .9f * (i % 3) / 2f, .5f, PURPLE);
            centred(p, b, light, x, 9.5f, wd / 2 + .6f * k, waist * .13f, 1 + .8f * ((i + 1) % 3) / 2f, .5f, PURPLE);
        }
        beltAndBuckle(p, b, light, k, waist, wd);
    }

    /**
     * Banner's shirt tearing apart as he grows: panels split along the seams, the gaps open, the ragged
     * pieces ride out on the swelling muscle and shorten until nothing is left.
     */
    private void shirt(PoseStack p, MultiBufferSource b, int light, float k, float w, float d) {
        float tear = Mth.clamp((k - .12f) / .5f, 0, 1);
        if (tear >= 1) return;
        float[] dark = {SHIRT[0] * .82f, SHIRT[1] * .82f, SHIRT[2] * .82f};
        int strips = 4;
        for (int face = 0; face < 2; face++) {
            float z = (face == 0 ? -1 : 1) * (d / 2 + .15f + tear * .5f);
            for (int i = 0; i < strips; i++) {
                float sw = (w + .3f) / strips, gap = tear * sw * .45f;
                float x = -w / 2 - .15f + sw * (i + .5f) + (i - 1.5f) * tear * .5f;
                float len = 10.5f * (1 - tear * (.4f + .6f * ((i * 5 + face * 3) % 4) / 3f));
                centred(p, b, light, x, -.1f + tear * 1.2f, z, sw - gap, len, .3f, i % 2 == 0 ? SHIRT : dark);
            }
        }
        // Sides, and the collar while it holds.
        for (int s = -1; s <= 1; s += 2)
            centred(p, b, light, s * (w / 2 + .15f + tear * .5f), -.1f + tear * 1.5f, 0, .3f, 10.5f * (1 - tear * .8f), d * (1 - tear * .6f), SHIRT);
        if (tear < .3f) centred(p, b, light, 0, -.2f, -d / 2 - .25f, 2.2f, 1.2f, .3f, dark);
    }

    /** The gamma belt: silver plates linked round the waist, green lights, the triangular buckle glowing in front. */
    private void beltAndBuckle(PoseStack p, MultiBufferSource b, int light, float k, float waist, float wd) {
        float y = 9.9f, h = l(.9f, 1.4f, k);
        centred(p, b, light, 0, y, .3f * k, waist + 1.1f * k + .7f, h, wd + .8f * k + .7f, SILVER_DARK);
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
        // The sleeve splits along its seams and the halves fall away as the arm swells.
        float tear = Mth.clamp((k - .1f) / .45f, 0, 1);
        if (tear < 1) for (int i = 0; i < 4; i++) {
            float piece = 4.8f * (1 - tear * (.55f + .45f * ((i * 7 + 3) % 5) / 4f));
            float out = tear * .9f;
            float x = (i % 2 == 0 ? -1 : 1) * (w / 4 + out * .5f), z = (i < 2 ? -1 : 1) * (w / 4 + out * .5f);
            centred(p, b, light, x, -2.1f + tear * .4f, z, w / 2 + .15f - tear * .5f, piece, w / 2 + .15f - tear * .5f, SHIRT);
        }
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

    /**
     * The head: one square block that turns from Banner's into Hulk's. Hulk's face is the classic blocky
     * Hulk: short black hair flat on top with a clean hairline and short sides, a strong flat brow with dark
     * eyebrows angled down to the nose in a frown, deep-set narrow eyes, a broad short nose, a wide square
     * jaw and a grimace of clenched teeth. The jaw is its own block hinged under the ears, so the roar
     * opens it. Head space: y from -s (crown) to 0 (chin), the face is the -z side.
     */
    private void head(PoseStack p, MultiBufferSource b, int light, float k, float[] skin, float[] shade, float[] light2, HulkMotion.Pose pose) {
        float s = l(8, 9.2f, k), h = s / 2, f = -h;
        float tint = Mth.clamp((k - .05f) / .6f, 0, 1);
        float grim = Mth.clamp((k - .25f) / .45f, 0, 1);                    // how far the lips draw back off the teeth
        float[] deep = mix(C_DEEP, shade, GREEN_DEEP, k), lip = mix(C_LIP, LIP, GREEN_DEEP, tint);
        float ey = -s * l(.52f, .55f, k), eh = l(1, .8f, k);               // top of the eyes, their height
        float ex = s * l(.23f, .232f, k), ew = l(2, 2.2f, k);             // eye centre off the middle, eye width
        float ym = -s * l(.27f, .255f, k);                                  // the mouth line: skull above, jaw below
        float zh = s * .15f;                                                // the jaw's hinge, under the ears
        float tw = s * l(.1625f, .25f, k), mw = tw + .45f;                  // half the width of the teeth, of the open mouth
        float lipTop = ym - l(.3f, .95f, k), open = Mth.clamp(pose.mouth, 0, 1);

        // Skull down to the mouth line, and its back down to the neck behind the jaw's hinge.
        centred(p, b, light, 0, -s, 0, s, s + ym, s, skin);
        box(p, b, light, -h, ym, zh, s, -ym, h - zh, skin);
        // Ears: small, at the sides, level with the eyes and nose.
        float earW = l(.45f, .5f, k);
        for (int side = -1; side <= 1; side += 2) {
            centred(p, b, light, side * (h + earW / 2), ey + .1f, zh - .1f, earW, l(1.8f, 1.9f, k), 1.2f, skin);
            centred(p, b, light, side * (h + earW + .01f), ey + .55f, zh - .1f, .06f, 1, .55f, shade);    // the hollow of the ear
        }

        // Brow: a strong flat ridge right across, the dark eyebrows on it angled down to the nose in a frown,
        // two creases between them.
        float bh = l(.7f, 1.15f, k), by = ey - bh, ridge = .6f * k;
        float[] brow = mix(C_BROW, BANNER_HAIR, BROW, k);
        onFace(p, b, light, 0, by, f, s - .3f, bh, ridge, skin);
        onFace(p, b, light, 0, by - .3f * k, f, s - .6f, .3f * k, ridge * .6f, light2);       // the forehead rolling into it
        for (int side = -1; side <= 1; side += 2) {
            float len = l(2.2f, 3.3f, k), th = l(.4f, .85f, k);
            p.pushPose(); px(p, side * l(1.84f, 1.95f, k), by + bh * .4f * k, f - ridge);
            p.mulPose(Axis.ZP.rotation(side * -.32f * k));
            box(p, b, light, -len / 2, -th / 2, -.12f, len, th, .3f, brow);
            p.popPose();
            onFace(p, b, light, side * .28f, by - .3f, f, .16f, 1.1f * k, ridge + .05f, deep);         // frown crease
        }

        // Eyes: Banner's calm brown; Hulk's set deep under the brow, narrowed, white with a burning green iris.
        boolean lit = k > .5f || pose.gamma > .4f;
        int eyeLight = lit ? FULL : light;
        float[] white = mix(C_WHITE, EYE, EYE_HULK, k), iris = mix(C_IRIS, IRIS, GAMMA_LIT, Math.max(k, pose.gamma));
        for (int side = -1; side <= 1; side += 2) {
            float x = side * ex, ix = x - side * .1f * k;
            onFace(p, b, light, x, ey, f, ew + .5f * k, eh + .4f * k, .03f * k, deep);                   // the socket's shadow
            onFace(p, b, eyeLight, x, ey, f, ew, eh, .07f, white);
            onFace(p, b, eyeLight, ix, ey, f, l(.9f, .85f, k), eh, .1f, iris);
            if (k > .3f) onFace(p, b, light, ix, ey + eh * .25f, f, .36f, eh * .5f, .15f, PUPIL);
        }

        // Nose: broad and short, a narrow bridge down from the brow, nostrils underneath.
        float noseBot = lipTop - l(.25f, .03f, k), nh = l(.95f, .8f, k), nw = l(1.5f, 2.8f, k), nout = l(.45f, .9f, k);
        onFace(p, b, light, 0, ey - .1f, f, l(.8f, 1.1f, k), noseBot - nh - ey + .4f, l(.25f, .5f, k), skin);   // bridge
        onFace(p, b, light, 0, noseBot - nh, f, nw, nh, nout, skin);
        onFace(p, b, light, 0, noseBot - nh + .1f, f, nw * .45f, nh * .5f, nout + .04f, light2);             // the tip catching the light
        for (int side = -1; side <= 1; side += 2) {
            onFace(p, b, light, side * (nw / 2 + .2f), noseBot - nh * .75f, f, .45f, nh * .75f, nout * .55f, shade);   // wing
            if (k > .2f) onFace(p, b, light, side * nw * .22f, noseBot - .26f, f, nw * .2f, .26f, nout + .03f, deep);   // nostril
        }

        // Cheekbones over a shadowed hollow, and the snarl's folds from the nose down to the mouth corners.
        if (k > .05f) for (int side = -1; side <= 1; side += 2) {
            onFace(p, b, light, side * (ex + .45f), ey + eh + .15f, f, 2.1f, .7f, .32f * k, light2);
            onFace(p, b, light, side * (ex + .75f), ey + eh + .85f, f, 1.6f, 1.1f, .05f * k, deep);
            p.pushPose(); px(p, side * (nw / 2 + .35f), noseBot - .2f, f);
            p.mulPose(Axis.ZP.rotation(-side * .5f));
            box(p, b, light, -.1f, 0, -.08f, .2f, 1.3f * k, .1f, deep);
            p.popPose();
        }

        // Mouth, upper half: the lip drawn back off clenched teeth, dark at the corners.
        float teethOut = l(.08f, .14f, k), lipOut = l(.12f, .32f, k), dark = l(.25f, .7f, k);
        onFace(p, b, light, 0, ym - dark, f, 2 * tw + .3f, dark, .04f, MOUTH);
        if (k > .25f) {
            onFace(p, b, light, 0, ym - .62f, f, 2 * tw, .58f, teethOut, TEETH);
            for (int i = -2; i <= 2; i++) onFace(p, b, light, i * tw * .4f, ym - .62f, f, .1f, .58f, teethOut + .04f, MOUTH);
        }
        onFace(p, b, light, 0, lipTop, f, 2 * tw + .5f, ym - lipTop - .62f * grim, lipOut, lip);
        // Seen only when the jaw drops (inside the closed jaw until then): the dark of the throat, and the
        // cheeks either side of it so the mouth opens no wider than the lips.
        if (open > .01f) {
            box(p, b, light, -mw, ym + .02f, f + .1f, 2 * mw, -ym - .35f, zh - f - .3f, MOUTH);
            for (int side = -1; side <= 1; side += 2)
                box(p, b, light, side < 0 ? -h + .02f : mw, ym - .01f, f + .02f, h - .02f - mw, -ym - .34f, zh - .8f - f, skin);
        }

        // The jaw, hinged under the ears: a touch wider than the skull for the square jaw line; the lower teeth,
        // the lip and the square chin drop open with the roar.
        p.pushPose();
        px(p, 0, ym, zh); p.mulPose(Axis.XP.rotation(open * l(.32f, .42f, k))); px(p, 0, -ym, -zh);
        float jw = s + .5f * k;
        box(p, b, light, -jw / 2, ym, f, jw, -ym, zh - f, skin);
        if (open > .01f) {
            box(p, b, light, -mw, ym - .04f, f + .15f, 2 * mw, .04f, zh - f - .5f, MOUTH);              // the mouth's floor
            box(p, b, light, -tw * .75f, ym - .3f, f + .5f, tw * 1.5f, .3f, 2.2f, TONGUE);             // tongue on it
        }
        onFace(p, b, light, 0, ym, f, 2 * tw + .3f, dark - .05f, .04f, MOUTH);
        if (k > .25f) {
            onFace(p, b, light, 0, ym + .04f, f, 2 * tw - .5f, .52f, teethOut, TEETH);
            for (int i = -1; i <= 1; i++) onFace(p, b, light, i * tw * .45f, ym + .04f, f, .1f, .52f, teethOut + .04f, MOUTH);
        }
        float lipBot = ym + l(.3f, .9f, k);
        onFace(p, b, light, 0, ym + .56f * grim, f, 2 * tw + .4f, lipBot - ym - .56f * grim, lipOut, lip);
        onFace(p, b, light, 0, lipBot + .15f, f, l(2.6f, 3.8f, k), -lipBot - .3f, .3f * k, skin);         // chin
        p.popPose();

        // Banner's glasses, gone the moment he starts to change.
        if (k < .15f) {
            for (int side = -1; side <= 1; side += 2) {
                float x = side * ex, y = ey - .3f;
                box(p, b, light, x - 1.3f, y, f - .35f, 2.6f, .35f, .2f, GLASSES);
                box(p, b, light, x - 1.3f, y + 1.5f, f - .35f, 2.6f, .35f, .2f, GLASSES);
                box(p, b, light, side > 0 ? x - 1.3f : x + 1f, y, f - .35f, .3f, 1.8f, .2f, GLASSES);
            }
            box(p, b, light, -.6f, ey, f - .35f, 1.2f, .3f, .2f, GLASSES);
        }

        // Hair: Banner's neat brown; Hulk's black, flat on top, cut straight across the forehead, short at the
        // sides and the back, the temples bare.
        float[] hair = mix(C_HAIR, BANNER_HAIR, HULK_HAIR, k);
        float top = l(1.1f, 1f, k), fringe = l(1.1f, 1.6f, k), temple = l(1f, 1.4f, k), sides = ey + .1f + s, o = .22f;
        box(p, b, light, -h - o, -s - top, f - .35f, s + 2 * o, top + .3f, s + .35f + o, hair);
        box(p, b, light, -h - o + .3f, -s - top - .3f * k, f - .35f, s + 2 * o - .6f, .3f * k, s * .45f, hair);   // brushed up at the front
        box(p, b, light, -h - o, -s, f - .35f, s + 2 * o, fringe, .45f, hair);
        box(p, b, light, -h - o, -s, h, s + 2 * o, l(3.2f, 4.8f, k), o, hair);
        for (int side = -1; side <= 1; side += 2) {
            float x = side < 0 ? -h - o : h;
            box(p, b, light, x, -s, f + temple, o, sides, h - f - temple + o, hair);
            box(p, b, light, x, -s + sides - .1f, zh - 1.5f, o, l(.5f, .9f, k), .8f, hair);              // sideburn
        }
    }

    /**
     * Gamma: wisps of green light rising off his shoulders, arms and back, flickering with the anger.
     * Drawn as glowing (emissive, additive) boxes in the body's own render pass, so the light stays on him.
     */
    private void glow(PoseStack p, MultiBufferSource b, float gamma, float time, float k) {
        var v = b.getBuffer(net.minecraft.client.renderer.RenderType.eyes(GhostMaterials.TEXTURE));
        float flicker = .75f + .25f * (float) Math.sin(time * 1.3);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + time * .04;
            float life = (float) ((time * .05 + i * .37) % 1);
            float r = l(5, 9.5f, k), x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r * .7f;
            float y = 9 - 13 * life, size = (1.4f - life) * 1.6f;
            float bright = gamma * flicker * (1 - life) * .55f;
            p.pushPose();
            px(p, x, y, z);
            p.scale(size, size * 2.2f, size);
            UNIT.render(p, v, FULL, OverlayTexture.NO_OVERLAY, .35f * bright, 1f * bright, .25f * bright, 1);
            p.popPose();
        }
    }
}
