package com.FIRNI.superheromod.client.render.thor;

import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Mjolnir, built from its own blocks: a short thick leather-wrapped handle with a silver pommel
 * and wrist strap, a big heavy head with chamfered edges, brighter end faces and a faint rune
 * band. Drawn in its own frame: origin in the fist, the handle running along +y to the head.
 * Electricity on it scales with power: a stray spark (0), thin arcs crawling over it (0.5),
 * wrapped in lightning (1).
 */
public final class Mjolnir {
    private static final ModelPart HANDLE = GhostMaterials.box(-.7f, -2.2f, -.7f, 1.4f, 9.2f, 1.4f);
    private static final ModelPart WRAP = GhostMaterials.box(-.82f, 0, -.82f, 1.64f, .45f, 1.64f);
    private static final ModelPart POMMEL = GhostMaterials.box(-1.0f, -3.2f, -1.0f, 2.0f, 1.1f, 2.0f);
    private static final ModelPart STRAP = GhostMaterials.box(-.22f, -5.0f, -.16f, .44f, 1.9f, .32f);
    private static final ModelPart COLLAR = GhostMaterials.box(-1.15f, 6.5f, -1.15f, 2.3f, .7f, 2.3f);
    private static final ModelPart HEAD = GhostMaterials.box(-3.7f, 7.1f, -2.05f, 7.4f, 4.1f, 4.1f);
    private static final ModelPart SHELL = GhostMaterials.box(-3.35f, 6.8f, -2.4f, 6.7f, 4.7f, 4.8f);
    private static final ModelPart FACE = GhostMaterials.box(-.2f, 7.45f, -1.7f, .4f, 3.4f, 3.4f);
    private static final ModelPart RUNE = GhostMaterials.box(-2.3f, 8.55f, -.05f, 4.6f, .7f, .1f);
    private static final ModelPart RUNE_DOT = GhostMaterials.box(-.35f, 8.25f, -.05f, .7f, 1.3f, .1f);
    public static final int FULL_BRIGHT = 15728880;
    /** Middle of the head in the hammer's frame (pixels). */
    public static final Vec3 HEAD_CENTRE = new Vec3(0, 9.15, 0);

    private Mjolnir() {}

    public static void draw(PoseStack p, MultiBufferSource b, int light, float power, float time) {
        float[] metal = {.5f, .52f, .57f}, dark = {.3f, .31f, .35f}, bright = {.82f, .84f, .88f};
        GhostMaterials.draw(HANDLE, p, b, light, .3f, .22f, .17f);
        for (int i = 0; i < 4; i++) {
            p.pushPose(); p.translate(0, (-1.4 + i * 1.9) / 16, 0);
            GhostMaterials.draw(WRAP, p, b, light, .19f, .14f, .11f);
            p.popPose();
        }
        GhostMaterials.draw(POMMEL, p, b, light, bright[0], bright[1], bright[2]);
        GhostMaterials.draw(STRAP, p, b, light, .25f, .17f, .12f);
        GhostMaterials.draw(COLLAR, p, b, light, metal[0], metal[1], metal[2]);
        GhostMaterials.draw(SHELL, p, b, light, dark[0], dark[1], dark[2]);
        GhostMaterials.draw(HEAD, p, b, light, metal[0], metal[1], metal[2]);
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose(); p.translate(side * 3.62 / 16, 0, 0);
            GhostMaterials.draw(FACE, p, b, light, bright[0], bright[1], bright[2]);
            p.popPose();
        }
        // The rune band glows faintly, more when he calls the storm.
        float rune = .25f + .75f * power + .1f * (float) Math.sin(time * .2);
        int runeLight = power > .2f ? FULL_BRIGHT : light;
        for (int side = -1; side <= 1; side += 2) {
            p.pushPose(); p.translate(0, 0, side * 2.42 / 16);
            GhostMaterials.draw(RUNE, p, b, runeLight, .35f + .4f * rune, .6f + .3f * rune, .8f + .2f * rune);
            GhostMaterials.draw(RUNE_DOT, p, b, runeLight, .35f + .4f * rune, .6f + .3f * rune, .8f + .2f * rune);
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
        double x = -3.7 + u * 7.4, y = 7.1 + v * 4.1, z = -2.05 + u * 4.1;
        return switch (face) {
            case 0 -> new Vec3(-3.75, y, -2 + v * 4);
            case 1 -> new Vec3(3.75, y, -2 + u * 4);
            case 2 -> new Vec3(x, 7.0, z);
            case 3 -> new Vec3(x, 11.25, z);
            case 4 -> new Vec3(x, y, -2.45);
            default -> new Vec3(x, y, 2.45);
        };
    }
}
