package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * The metal Magneto throws about, built of boxes so it reads as real, heavy, used iron and steel (no glow): rods and
 * girders with banded, rusted lengths, a sharpened tip and a torn-off end; scrap (plates, bars, gears, bolts, bent
 * sheet); the riveted columns of his shield; the giant fist of plates and bars; the jagged pieces the columns burst
 * into. Sizes in blocks, lit by the world's light where they are.
 */
public final class MetalMesh {
    private MetalMesh() {}

    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 16, 16, 16);
    public static final float[] IRON = {.40f, .41f, .44f}, IRON_DARK = {.23f, .235f, .26f}, IRON_LIGHT = {.64f, .65f, .69f},
            RUST = {.44f, .25f, .15f}, RUST_DARK = {.27f, .15f, .09f}, STEEL = {.55f, .57f, .62f};

    /** A light colour every piece (and Magneto's body) is multiplied by; a film sets it for its stage's light and puts it back. */
    public static final float[] SHADE = {1, 1, 1};
    public static VertexConsumer buffer(MultiBufferSource b) { return b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)); }
    /** A box from (x0, y0, z0) to (x1, y1, z1), in blocks. */
    public static void box(PoseStack p, VertexConsumer v, int light, float x0, float y0, float z0, float x1, float y1, float z1, float[] c) {
        p.pushPose();
        p.translate(x0, y0, z0);
        // (The unit part is a 16-pixel cube: one block.)
        p.scale((x1 - x0), (y1 - y0), (z1 - z0));
        UNIT.render(p, v, light, OverlayTexture.NO_OVERLAY, Math.min(1, c[0] * SHADE[0]), Math.min(1, c[1] * SHADE[1]), Math.min(1, c[2] * SHADE[2]), 1);
        p.popPose();
    }
    private static void cbox(PoseStack p, VertexConsumer v, int light, float cx, float cy, float cz, float w, float h, float d, float[] c) {
        box(p, v, light, cx - w / 2, cy - h / 2, cz - d / 2, cx + w / 2, cy + h / 2, cz + d / 2, c);
    }
    static float hash(int n) { double x = Math.sin(n * 12.9898 + 78.233) * 43758.5453; return (float) (x - Math.floor(x)); }
    private static float[] tint(float[] c, int seed) {
        float k = .88f + .2f * hash(seed);
        return new float[]{c[0] * k, c[1] * k, c[2] * k};
    }

    /** A rod of the barrage along +y, centred: a square bar (or a girder), banded, rust in places, the tip sharpened, the end torn. */
    public static void rod(PoseStack p, VertexConsumer v, int light, float len, int seed) {
        float h = len / 2;
        float[] body = tint(IRON, seed);
        if (seed % 3 == 0) {
            // A girder: two flanges and the web between.
            box(p, v, light, -.16f, -h, -.15f, .16f, h, -.1f, body);
            box(p, v, light, -.16f, -h, .1f, .16f, h, .15f, body);
            box(p, v, light, -.035f, -h, -.1f, .035f, h, .1f, tint(IRON_DARK, seed + 1));
        } else {
            box(p, v, light, -.11f, -h, -.11f, .11f, h, .11f, body);
            for (int s = 0; s < 4; s++) {
                float x = s % 2 == 0 ? -.11f : .08f, z = s < 2 ? -.11f : .08f;
                box(p, v, light, x, -h + .1f, z, x + .03f, h - .2f, z + .03f, IRON_LIGHT);
            }
        }
        for (int i = 0; i < 3; i++) {
            float y = -h + len * (.22f + .28f * i) + .1f * hash(seed + i);
            cbox(p, v, light, 0, y, 0, .29f, .07f, .29f, IRON_DARK);
        }
        // Rust streaks.
        cbox(p, v, light, .1f, -h * .3f, 0, .03f, len * .3f, .16f, RUST);
        cbox(p, v, light, -.06f, h * .2f + .3f * hash(seed + 7), -.112f, .12f, len * .18f, .01f, RUST_DARK);
        // The tip, ground to a point, and the torn end.
        cbox(p, v, light, 0, h + .12f, 0, .15f, .24f, .15f, IRON_LIGHT);
        cbox(p, v, light, 0, h + .3f, 0, .07f, .14f, .07f, IRON_LIGHT);
        cbox(p, v, light, .04f, -h - .06f, .03f, .13f, .12f, .1f, RUST_DARK);
        cbox(p, v, light, -.05f, -h - .1f, -.04f, .09f, .14f, .08f, IRON_DARK);
    }

    /** A piece of scrap (about size across), its kind and shape picked by the seed. */
    public static void scrap(PoseStack p, VertexConsumer v, int light, int seed, float size) {
        float s = size * (.75f + .5f * hash(seed * 3));
        float[] c = hash(seed * 5) < .3f ? tint(RUST, seed) : tint(IRON, seed);
        switch (Math.abs(seed) % 5) {
            case 0 -> { cbox(p, v, light, 0, 0, 0, s, .06f, s * .7f, c); cbox(p, v, light, s * .2f, .035f, 0, s * .3f, .01f, s * .3f, RUST_DARK); }
            case 1 -> {
                cbox(p, v, light, 0, 0, 0, .08f, s * 1.3f, .08f, c);
                p.pushPose(); p.translate(0, s * .65f, 0); p.mulPose(Axis.ZP.rotation(.9f)); cbox(p, v, light, 0, s * .3f, 0, .08f, s * .6f, .08f, c); p.popPose();
            }
            case 2 -> {
                cbox(p, v, light, 0, 0, 0, s, .08f, s, c);
                for (int i = 0; i < 4; i++) { p.pushPose(); p.mulPose(Axis.YP.rotation(i * (float) Math.PI / 4)); cbox(p, v, light, 0, 0, 0, s * 1.3f, .07f, .1f, IRON_DARK); p.popPose(); }
                cbox(p, v, light, 0, 0, 0, s * .25f, .12f, s * .25f, IRON_LIGHT);
            }
            case 3 -> { for (int i = 0; i < 3; i++) cbox(p, v, light, (hash(seed + i) - .5f) * s, (hash(seed + i + 9) - .5f) * s * .4f, (hash(seed + i + 4) - .5f) * s, s * .3f, s * .3f, s * .3f, i == 0 ? IRON_LIGHT : c); }
            default -> {
                cbox(p, v, light, -s * .25f, 0, 0, s * .5f, .05f, s * .6f, c);
                p.pushPose(); p.mulPose(Axis.ZP.rotation(.7f)); cbox(p, v, light, s * .25f, 0, 0, s * .5f, .05f, s * .6f, c); p.popPose();
            }
        }
    }

    /** One column of the shield, standing on y 0 up to height: riveted iron, banded, capped, streaked with rust. */
    public static void column(PoseStack p, VertexConsumer v, int light, float height, int seed) {
        float w = .44f;
        box(p, v, light, -w, 0, -w, w, height, w, tint(IRON_DARK, seed));
        for (int s = 0; s < 4; s++) {
            float x = s % 2 == 0 ? -w - .02f : w - .06f, z = s < 2 ? -w - .02f : w - .06f;
            box(p, v, light, x, .2f, z, x + .08f, height - .1f, z + .08f, IRON_LIGHT);
        }
        // Plates on each face with a seam between.
        for (int f = 0; f < 4; f++) {
            p.pushPose(); p.mulPose(Axis.YP.rotation(f * (float) Math.PI / 2));
            box(p, v, light, -w + .07f, .3f, -w - .03f, w - .07f, height * .5f - .04f, -w + .02f, tint(IRON, seed + f));
            box(p, v, light, -w + .07f, height * .5f + .04f, -w - .03f, w - .07f, height - .3f, -w + .02f, tint(IRON, seed + f + 4));
            if (hash(seed + f * 7) < .6f) box(p, v, light, -.06f + .3f * (hash(seed + f) - .5f), height * .4f, -w - .04f, .04f + .3f * (hash(seed + f) - .5f), height - .35f, -w - .02f, RUST);
            p.popPose();
        }
        for (float y = .5f; y < height - .2f; y += .9f) box(p, v, light, -w - .05f, y, -w - .05f, w + .05f, y + .1f, w + .05f, IRON);
        box(p, v, light, -w - .1f, height - .02f, -w - .1f, w + .1f, height + .18f, w + .1f, IRON);
        box(p, v, light, -w - .12f, 0, -w - .12f, w + .12f, .22f, w + .12f, IRON_DARK);
        // A torn, uneven top.
        box(p, v, light, -.2f, height + .18f, -.1f, .1f, height + .36f, .25f, tint(IRON_DARK, seed + 9));
    }

    /**
     * The giant fist, centred, its knuckles facing down (-y), the forearm rising behind it: plates over a frame of
     * bars, riveted, scrap stuck to it. assemble 0..1: the pieces flying in from all round and locking together.
     */
    public static void fist(PoseStack p, VertexConsumer v, int light, float assemble, int seed) {
        int[] i = {0};
        // The forearm rising from the wrist, banded.
        part(p, v, light, assemble, i, 0, 1.75f, 0, 1.3f, 1.7f, 1.3f, IRON_DARK);
        part(p, v, light, assemble, i, 0, 1.25f, 0, 1.5f, .18f, 1.5f, IRON);
        part(p, v, light, assemble, i, 0, 2.2f, 0, 1.45f, .16f, 1.45f, IRON);
        part(p, v, light, assemble, i, .66f, 1.8f, 0, .06f, 1.5f, .9f, RUST);
        // The back of the hand and the palm.
        part(p, v, light, assemble, i, 0, .35f, -.1f, 2.0f, 1.3f, 1.5f, IRON);
        part(p, v, light, assemble, i, 0, .45f, -.86f, 1.8f, 1.0f, .12f, IRON_LIGHT);
        part(p, v, light, assemble, i, 0, .4f, .7f, 1.9f, 1.1f, .2f, IRON_DARK);
        // The four knuckles leading, the fingers curled under toward the palm.
        for (int f = 0; f < 4; f++) {
            float x = -.72f + f * .48f;
            part(p, v, light, assemble, i, x, -.55f, -.35f, .44f, .65f, .8f, IRON_LIGHT);
            part(p, v, light, assemble, i, x, -.6f, .3f, .42f, .55f, .55f, tint(IRON, seed + f));
            part(p, v, light, assemble, i, x, -.28f, -.78f, .3f, .1f, .08f, IRON_DARK);
        }
        // The thumb wrapped across the front of the fingers.
        part(p, v, light, assemble, i, -.95f, -.1f, .55f, .5f, .9f, .5f, IRON);
        part(p, v, light, assemble, i, -.55f, -.45f, .75f, .7f, .35f, .4f, IRON);
        // Rivets and scrap stuck on.
        for (int k = 0; k < 6; k++) part(p, v, light, assemble, i, -.8f + .32f * k, .95f, -.88f, .1f, .1f, .06f, IRON_DARK);
        for (int k = 0; k < 4; k++) {
            float a = hash(seed + k * 3) * 6.28f;
            part(p, v, light, assemble, i, (float) Math.cos(a) * .72f, 1.4f + .9f * hash(seed + k), (float) Math.sin(a) * .72f, .4f, .06f, .3f, hash(seed + k) < .5f ? RUST : IRON);
        }
    }
    /** One piece of the fist: flown in from its own direction while it assembles. */
    private static void part(PoseStack p, VertexConsumer v, int light, float assemble, int[] i, float cx, float cy, float cz, float w, float h, float d, float[] c) {
        int n = i[0]++;
        float out = (1 - assemble);
        out = out * out * 4;
        if (out > .001f) {
            float a = hash(n * 7 + 1) * 6.28f, up = hash(n * 3 + 2) - .3f;
            p.pushPose();
            p.translate(Math.cos(a) * out, up * out, Math.sin(a) * out);
            p.translate(cx, cy, cz);
            p.mulPose(Axis.XP.rotation(out * 2 * hash(n)));
            p.mulPose(Axis.ZP.rotation(out * 2 * hash(n + 5)));
            cbox(p, v, light, 0, 0, 0, w, h, d, c);
            p.popPose();
            return;
        }
        cbox(p, v, light, cx, cy, cz, w, h, d, c);
    }

    /**
     * A plate of the shield, upright, centred, facing -z: a thick slab of dark iron torn from something bigger, a raised
     * rim, a seam and rivets across it, rust down one side, one corner bitten off.
     */
    public static void plate(PoseStack p, VertexConsumer v, int light, float w, float h, int seed) {
        float hw = w / 2, hh = h / 2, d = .16f;
        float[] face = tint(IRON, seed), dark = tint(IRON_DARK, seed + 1);
        box(p, v, light, -hw, -hh, -d / 2, hw - .22f, hh, d / 2, dark);
        box(p, v, light, hw - .22f, -hh, -d / 2, hw, hh - .3f, d / 2, dark);
        // The face, proud of the slab, split by a seam.
        box(p, v, light, -hw + .07f, -hh + .07f, -d / 2 - .04f, hw - .07f, -.03f, -d / 2 + .01f, face);
        box(p, v, light, -hw + .07f, .03f, -d / 2 - .04f, hw - .3f, hh - .07f, -d / 2 + .01f, tint(IRON, seed + 5));
        // The rim along the edges.
        box(p, v, light, -hw - .03f, -hh - .03f, -d / 2 - .06f, hw + .03f, -hh + .06f, d / 2 + .02f, IRON_LIGHT);
        box(p, v, light, -hw - .03f, hh - .06f, -d / 2 - .06f, hw - .28f, hh + .03f, d / 2 + .02f, IRON_LIGHT);
        box(p, v, light, -hw - .03f, -hh, -d / 2 - .06f, -hw + .06f, hh, d / 2 + .02f, IRON_LIGHT);
        // Rivets across the seam and rust running down.
        for (int i = 0; i < 4; i++) {
            float x = -hw + .2f + i * (w - .4f) / 3;
            cbox(p, v, light, x, 0, -d / 2 - .06f, .07f, .07f, .03f, IRON_DARK);
        }
        if (hash(seed * 3) < .7f) box(p, v, light, -hw + .15f + .4f * hash(seed), -hh * .8f, -d / 2 - .05f, -hw + .25f + .4f * hash(seed), hh * .3f, -d / 2 - .04f, RUST);
        // The bitten corner: a step and a bent tongue of metal.
        cbox(p, v, light, hw - .12f, hh - .2f, 0, .14f, .22f, d * .7f, RUST_DARK);
    }
    /** A jagged piece of a torn column. */
    public static void fragment(PoseStack p, VertexConsumer v, int light, int seed, float size) {
        float s = size * (.7f + .6f * hash(seed));
        cbox(p, v, light, 0, 0, 0, s, .08f, s * .6f, tint(IRON_DARK, seed));
        cbox(p, v, light, s * .25f, .05f, s * .1f, s * .4f, .05f, s * .3f, hash(seed + 3) < .4f ? RUST : IRON);
        cbox(p, v, light, -s * .35f, 0, -s * .2f, s * .2f, .14f, s * .2f, IRON_LIGHT);
    }
    /** A shard flicked from his hand: a small sharp sliver along +y. */
    public static void shard(PoseStack p, VertexConsumer v, int light, int seed) {
        cbox(p, v, light, 0, 0, 0, .1f, .5f, .04f, tint(IRON_LIGHT, seed));
        cbox(p, v, light, 0, .3f, 0, .05f, .14f, .03f, STEEL);
    }
}
