package com.FIRNI.superheromod.client.render.thor;

import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Mjolnir, built from its own blocks after the film prop: a dark leather handle with a silver cord
 * wound criss-cross over it, a stacked silver pommel with a tan wrist strap hanging in a loop, and
 * a heavy silver head with bevelled edges and dark engraved panels on its ends. Drawn in its own frame: origin in the fist, the handle running along +y to the head.
 * Electricity on it scales with power: a stray spark (0), thin arcs crawling over it (0.5),
 * wrapped in lightning (1).
 */
public final class Mjolnir {
    // Handle: dark brown leather with a silver cord wound criss-cross over it.
    private static final ModelPart HANDLE = GhostMaterials.box(-.75f, -3.4f, -.75f, 1.5f, 11.8f, 1.5f);
    private static final ModelPart WRAP = GhostMaterials.box(-.86f, -.13f, -.86f, 1.72f, .26f, 1.72f);
    // Pommel: stacked silver, a cap on top, a strap of tan leather hanging from it in a loop.
    private static final ModelPart POMMEL = GhostMaterials.box(-1.0f, -4.3f, -1.0f, 2.0f, 1.0f, 2.0f);
    private static final ModelPart POMMEL_CAP = GhostMaterials.box(-.7f, -5.1f, -.7f, 1.4f, .9f, 1.4f);
    private static final ModelPart POMMEL_RING = GhostMaterials.box(-.9f, -3.45f, -.9f, 1.8f, .35f, 1.8f);
    private static final ModelPart STRAP = GhostMaterials.box(-.2f, -4.6f, -.12f, .4f, 4.6f, .24f);
    private static final ModelPart STRAP_END = GhostMaterials.box(-.75f, -.2f, -.13f, 1.5f, .4f, .26f);
    private static final ModelPart COLLAR = GhostMaterials.box(-1.15f, 8.05f, -1.15f, 2.3f, .85f, 2.3f);
    // Head: a heavy silver block, its long faces raised in the middle so the edges read as bevels.
    private static final ModelPart HEAD = GhostMaterials.box(-4.0f, 8.7f, -2.3f, 8.0f, 4.6f, 4.6f);
    private static final ModelPart HEAD_FACES = GhostMaterials.box(-3.55f, 8.5f, -2.5f, 7.1f, 5.0f, 5.0f);
    private static final ModelPart HEAD_ENDS = GhostMaterials.box(-4.25f, 9.05f, -1.95f, 8.5f, 3.9f, 3.9f);
    private static final ModelPart END_BAND = GhostMaterials.box(-.16f, 8.45f, -2.55f, .32f, 5.1f, 5.1f);
    // End faces: dark panels with silver knotwork.
    private static final ModelPart END_PANEL = GhostMaterials.box(-.12f, 9.35f, -1.6f, .24f, 3.3f, 3.2f);
    private static final ModelPart KNOT = GhostMaterials.box(-.15f, -.32f, -.32f, .3f, .64f, .64f);
    private static final ModelPart KNOT_BAR = GhostMaterials.box(-.15f, -.12f, -1.1f, .3f, .24f, 2.2f);
    public static final int FULL_BRIGHT = 15728880;
    /** Middle of the head in the hammer's frame (pixels). */
    public static final Vec3 HEAD_CENTRE = new Vec3(0, 11.0, 0);

    private Mjolnir() {}

    public static void draw(PoseStack p, MultiBufferSource b, int light, float power, float time) {
        float[] silver = {.72f, .74f, .78f}, edge = {.56f, .58f, .62f}, bright = {.86f, .88f, .9f}, panel = {.13f, .13f, .15f},
                leather = {.2f, .1f, .075f}, tan = {.6f, .4f, .23f};
        GhostMaterials.draw(HANDLE, p, b, light, leather[0], leather[1], leather[2]);
        // The cord crosses itself all the way up the grip.
        for (int i = 0; i < 9; i++) {
            p.pushPose(); p.translate(0, (-2.7 + i * 1.3) / 16, 0); p.mulPose(Axis.ZP.rotation(i % 2 == 0 ? .38f : -.38f));
            GhostMaterials.draw(WRAP, p, b, light, bright[0], bright[1], bright[2]);
            p.popPose();
        }
        GhostMaterials.draw(POMMEL_RING, p, b, light, edge[0], edge[1], edge[2]);
        GhostMaterials.draw(POMMEL, p, b, light, silver[0], silver[1], silver[2]);
        GhostMaterials.draw(POMMEL_CAP, p, b, light, bright[0], bright[1], bright[2]);
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose(); p.translate(side * .5 / 16, -4.4 / 16, 0); p.mulPose(Axis.ZP.rotation(side * -.11f));
            GhostMaterials.draw(STRAP, p, b, light, tan[0], tan[1], tan[2]);
            p.popPose();
        }
        p.pushPose(); p.translate(0, -8.95 / 16, 0); GhostMaterials.draw(STRAP_END, p, b, light, tan[0] * .85f, tan[1] * .85f, tan[2] * .85f); p.popPose();
        GhostMaterials.draw(COLLAR, p, b, light, bright[0], bright[1], bright[2]);
        GhostMaterials.draw(HEAD, p, b, light, edge[0], edge[1], edge[2]);
        GhostMaterials.draw(HEAD_FACES, p, b, light, silver[0], silver[1], silver[2]);
        GhostMaterials.draw(HEAD_ENDS, p, b, light, edge[0], edge[1], edge[2]);
        for (int side = -1; side <= 1; side += 2) {
            // A darker band where each end begins, then the engraved end panel.
            p.pushPose(); p.translate(side * 3.4 / 16, 0, 0);
            GhostMaterials.draw(END_BAND, p, b, light, edge[0] * .75f, edge[1] * .75f, edge[2] * .75f);
            p.popPose();
            p.pushPose(); p.translate(side * 4.3 / 16, 0, 0);
            GhostMaterials.draw(END_PANEL, p, b, light, panel[0], panel[1], panel[2]);
            for (int k = 0; k < 4; k++) {
                p.pushPose(); p.translate(0, (11.0 + (k / 2 == 0 ? -.8 : .8)) / 16, (k % 2 == 0 ? -.8 : .8) / 16);
                GhostMaterials.draw(KNOT, p, b, light, edge[0], edge[1], edge[2]);
                p.popPose();
            }
            p.pushPose(); p.translate(0, 11.0 / 16, 0); p.mulPose(Axis.XP.rotation(.785f));
            GhostMaterials.draw(KNOT_BAR, p, b, light, edge[0] * .9f, edge[1] * .9f, edge[2] * .9f);
            p.mulPose(Axis.XP.rotation(1.571f));
            GhostMaterials.draw(KNOT_BAR, p, b, light, edge[0] * .9f, edge[1] * .9f, edge[2] * .9f);
            p.popPose();
            p.popPose();
        }
        electricity(p, b, power, time);
    }

    /** Arcs on the hammer's surface: born, branching, gone; a new set every couple of ticks. */
    static void electricity(PoseStack p, MultiBufferSource b, float power, float time) {
        int frame = (int) Math.floor(time / 2);
        int arcs = power >= .95f ? 9 : power >= .45f ? 3 : 0;
        // At rest only a stray spark now and then.
        boolean spark = power < .45f && FilmFx.hash(frame * 7.13) < .18;
        if (arcs == 0 && !spark) return;
        p.pushPose();
        p.scale(1 / 16f, 1 / 16f, 1 / 16f);
        var m = p.last().pose();
        int count = arcs == 0 ? 1 : arcs;
        for (int i = 0; i < count; i++) {
            long seed = frame * 131L + i * 17L;
            Vec3 a = surface(seed), z = surface(seed * 31 + 7);
            if (arcs >= 9 && i % 3 == 0) z = new Vec3(0, -2 + FilmFx.hash(seed) * 6, 0).add(z.scale(.15));   // down the handle
            float life = 1 - (time / 2 - frame);
            List<ThorBolts.Seg> segs = ThorBolts.bolt(a, z, seed, .45, power >= .95f ? .6 : .3, 1);
            ThorBolts.crossBolt(b.getBuffer(FilmFx.ADD), m, segs, .22 + .12 * power, (spark ? .7f : .9f) * (.4f + .6f * life));
        }
        if (power >= .95f) {
            // Wrapped in it: a soft halo round the head.
            for (int i = 0; i < 3; i++) {
                double a = time * .9 + i * 2.1;
                Vec3 c = HEAD_CENTRE.add(Math.cos(a) * 1.5, Math.sin(a * 1.3) * 1.2, Math.sin(a) * 1.5);
                ThorBolts.cross(b.getBuffer(FilmFx.ADD), m, HEAD_CENTRE, c, 3.2, ThorBolts.GLOW, .12f);
            }
        }
        p.popPose();
    }
    /** A point on the head's surface (pixels), from a seed. */
    private static Vec3 surface(long seed) {
        double u = FilmFx.hash(seed * .37 + 1), v = FilmFx.hash(seed * .61 + 2), w = FilmFx.hash(seed * .83 + 3);
        int face = (int) (w * 6);
        double x = -4.0 + u * 8.0, y = 8.7 + v * 4.6, z = -2.3 + u * 4.6;
        return switch (face) {
            case 0 -> new Vec3(-4.35, y, -2.2 + v * 4.4);
            case 1 -> new Vec3(4.35, y, -2.2 + u * 4.4);
            case 2 -> new Vec3(x, 8.5, z);
            case 3 -> new Vec3(x, 13.45, z);
            case 4 -> new Vec3(x, y, -2.6);
            default -> new Vec3(x, y, 2.6);
        };
    }
}
