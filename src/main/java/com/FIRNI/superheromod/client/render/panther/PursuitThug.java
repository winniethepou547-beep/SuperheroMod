package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.client.render.panther.PursuitRig.*;

/**
 * The gunman in the back seat: a different man every time (the seed picks his skin, hair, jacket, trousers,
 * shoes, whether he wears a cap or a beanie or a mask over his mouth, and his pistol's finish; he shoots left-handed), built of boxes on
 * the same joints as Panther, so the film's poses mean the same on him. Lit by the same night as the car. Each
 * draw reports where his gun's muzzle is, for the shots.
 */
final class PursuitThug {
    private PursuitThug() {}

    private static final int[] SKIN = {0xf1c27d, 0xe0ac69, 0xc68642, 0x8d5524, 0xffdbac, 0xd9a066};
    private static final int[] HAIR = {0x1b1b1b, 0x3b2a1a, 0x6b4a2b, 0xb08d57, 0x141414};
    private static final int[] JACKET = {0x18181a, 0x2f3a24, 0x22283a, 0x4a4a4e, 0x5a1f1f, 0x3a2c22};
    private static final int[] TROUSERS = {0x2a3a5a, 0x151515, 0x6b5e45, 0x2b2b30};
    /** The muzzle of his gun and the way it points, from the last draw (stage space). */
    static Vec3 muzzle = Vec3.ZERO, aim = new Vec3(0, 0, 1);

    record Look(int skin, int hair, boolean bald, int jacket, int trousers, int shoes, int hat, int hatColour, boolean mask, int gun) {
        static Look of(int seed) {
            double h = PursuitPath.hash(seed * 1.37);
            return new Look(SKIN[(int) (PursuitPath.hash(seed * 2.1) * SKIN.length)], HAIR[(int) (PursuitPath.hash(seed * 3.3) * HAIR.length)], h < .18,
                    JACKET[(int) (PursuitPath.hash(seed * 4.7) * JACKET.length)], TROUSERS[(int) (PursuitPath.hash(seed * 5.9) * TROUSERS.length)],
                    PursuitPath.hash(seed * 6.1) < .3 ? 0xdedede : 0x111111, (int) (PursuitPath.hash(seed * 7.3) * 3),
                    PursuitPath.hash(seed * 8.9) < .5 ? 0x1a1a1a : 0x6b1d1d, PursuitPath.hash(seed * 9.7) < .35, PursuitPath.hash(seed * 10.3) < .5 ? 0x1c1c1f : 0x6d6f74);
        }
    }

    /** Draws him (body matrix as Place.matrix(): model pixels, y down) in the pose. */
    static void draw(FilmContext c, Matrix4f body, PantherMotion.Pose pose, Look look) {
        Matrix4f view = c.pose().last().pose();
        Vector3f at = bone(body, pose, PELVIS).transformPosition(new Vector3f());
        PursuitShade.lightsNear(at.x, at.y, at.z, 14);
        VertexConsumer v = PursuitShade.solid(c);
        // Hips and legs in his trousers, his shoes.
        Matrix4f pelvis = bone(body, pose, PELVIS);
        box(v, view, pelvis, -4.1f, -1.4f, -2.2f, 4.1f, 2.2f, 2.2f, look.trousers(), .05f);
        for (int side = 0; side < 2; side++) {
            Matrix4f thigh = bone(body, pose, side == 0 ? R_THIGH : L_THIGH);
            box(v, view, thigh, -2.05f, 0, -2.05f, 2.05f, 6.3f, 2.05f, look.trousers(), .05f);
            Matrix4f shin = bone(body, pose, side == 0 ? R_SHIN : L_SHIN);
            box(v, view, shin, -1.95f, -.2f, -1.95f, 1.95f, 5.0f, 1.95f, look.trousers(), .05f);
            Matrix4f foot = bone(body, pose, side == 0 ? R_FOOT : L_FOOT);
            box(v, view, foot, -2.0f, -.2f, -4.2f, 2.0f, 1.3f, 1.9f, look.shoes(), .3f);
        }
        // The jacket over the body, its collar; the arms in its sleeves; bare hands.
        Matrix4f chest = bone(body, pose, CHEST);
        box(v, view, chest, -4.4f, -6.6f, -2.5f, 4.4f, .2f, 2.5f, look.jacket(), .25f);
        Matrix4f belly = new Matrix4f(chest).translate(0, 5.6f / 16, 0);
        box(v, view, belly, -4.2f, -5.8f, -2.4f, 4.2f, -.6f, 2.4f, look.jacket(), .25f);
        box(v, view, chest, -2.6f, -7.4f, -2.0f, 2.6f, -6.4f, 2.2f, look.jacket(), .2f);
        box(v, view, chest, -.15f, -6.4f, -2.56f, .15f, -.2f, -2.5f, 0x8a8d92, .8f);
        for (int side = 0; side < 2; side++) {
            Matrix4f upper = bone(body, pose, side == 0 ? R_UPPER : L_UPPER);
            box(v, view, upper, -2.0f, -1.6f, -2.0f, 2.0f, 5.3f, 2.0f, look.jacket(), .25f);
            Matrix4f fore = bone(body, pose, side == 0 ? R_FOREARM : L_FOREARM);
            box(v, view, fore, -1.8f, -.3f, -1.8f, 1.8f, 4.6f, 1.8f, look.jacket(), .25f);
            Matrix4f hand = bone(body, pose, side == 0 ? R_HAND : L_HAND);
            box(v, view, hand, -1.2f, -.2f, -1.4f, 1.2f, 2.8f, 1.4f, look.skin(), .1f);
            if (side == 1) gun(v, view, hand, look);
        }
        // The head: face, hair or a bald crown, the cap or beanie, the mask over his mouth.
        Matrix4f head = bone(body, pose, HEAD);
        box(v, view, head, -1.4f, -1.4f, -1.4f, 1.4f, .2f, 1.4f, look.skin(), .1f);
        box(v, view, head, -3.6f, -8.6f, -3.6f, 3.6f, -1.2f, 3.6f, look.skin(), .15f);
        if (!look.bald()) {
            box(v, view, head, -3.75f, -8.9f, -3.2f, 3.75f, -6.6f, 3.8f, look.hair(), .2f);
            box(v, view, head, -3.75f, -6.6f, .6f, 3.75f, -2.8f, 3.8f, look.hair(), .2f);
        }
        if (look.hat() == 1) box(v, view, head, -3.9f, -9.6f, -3.9f, 3.9f, -6.4f, 3.9f, look.hatColour(), .1f);
        if (look.hat() == 2) {
            box(v, view, head, -3.85f, -9.2f, -3.85f, 3.85f, -7.0f, 3.85f, look.hatColour(), .2f);
            box(v, view, head, -2.6f, -7.3f, 3.85f, 2.6f, -6.9f, 6.6f, look.hatColour(), .2f);
        }
        // Eyes and brows; the mask or the mouth.
        for (int s = -1; s <= 1; s += 2) {
            box(v, view, head, s * 1.9f - .55f, -5.3f, -3.7f, s * 1.9f + .55f, -4.7f, -3.6f, 0xf2f2f2, .3f);
            box(v, view, head, s * 1.9f - .25f, -5.25f, -3.75f, s * 1.9f + .25f, -4.75f, -3.68f, 0x1a1410, .5f);
            box(v, view, head, s * 1.9f - .8f, -6.1f, -3.72f, s * 1.9f + .8f, -5.75f, -3.6f, look.bald() ? 0x2a2018 : look.hair(), .1f);
        }
        if (look.mask()) box(v, view, head, -3.75f, -4.0f, -3.8f, 3.75f, -1.1f, 3.0f, 0x141416, .1f);
        else box(v, view, head, -1.1f, -2.7f, -3.7f, 1.1f, -2.35f, -3.6f, 0x5a2e2a, .2f);
    }
    /** His pistol: the slide along his forearm's line, the grip in his fist. Records the muzzle. */
    private static void gun(VertexConsumer v, Matrix4f view, Matrix4f hand, Look look) {
        box(v, view, hand, -.55f, -.4f, -2.3f, .55f, 6.4f, -1.2f, look.gun(), .8f);
        box(v, view, hand, -.5f, .6f, -1.3f, .5f, 3.6f, -.4f, 0x18181a, .4f);
        box(v, view, hand, -.45f, 1.0f, -1.2f, .45f, 2.1f, 2.6f, 0x141416, .3f);
        Vector3f tip = hand.transformPosition(new Vector3f(0, 6.6f / 16, -1.75f / 16));
        Vector3f back = hand.transformPosition(new Vector3f(0, 0, -1.75f / 16));
        muzzle = new Vec3(tip.x, tip.y, tip.z);
        aim = new Vec3(tip.x - back.x, tip.y - back.y, tip.z - back.z).normalize();
    }
    /** A box in a bone's frame, in model pixels. */
    private static void box(VertexConsumer v, Matrix4f view, Matrix4f bone, float x0, float y0, float z0, float x1, float y1, float z1, int colour, float gloss) {
        PursuitShade.box(v, view, bone, x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16, colour, gloss);
    }
}
