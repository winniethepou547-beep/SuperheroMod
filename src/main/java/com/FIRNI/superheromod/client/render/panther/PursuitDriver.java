package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.client.render.panther.PursuitRig.*;

/**
 * The driver of THE FINAL PURSUIT: the player the film was aimed at, in their own skin. Built on the same joints as
 * Panther and the gunman (PursuitRig), so the film's poses mean the same on them: each part of the player model
 * (head, body, arms and legs, with the outer layer over them) is a box with the skin's own pixels on its faces; the
 * jointed limbs take their share of the arm's and leg's pixels above and below the elbow and knee. Lit by the same
 * night as everything else, vertex by vertex. A driver who is not a player is a stranger built like the gunman.
 */
final class PursuitDriver {
    private PursuitDriver() {}

    /** Who drives: the skin and arm shape of the player, or (no skin) a stranger picked by the seed. */
    record Look(ResourceLocation skin, boolean slim, int seed) {}

    static void draw(FilmContext c, Matrix4f body, PantherMotion.Pose pose, Look look) {
        if (look.skin() == null) { PursuitThug.draw(c, body, pose, PursuitThug.Look.of(look.seed()), false); return; }
        Matrix4f view = c.pose().last().pose();
        Vector3f at = bone(body, pose, CHEST).transformPosition(new Vector3f());
        PursuitShade.lightsNear(at.x, at.y, at.z, 14);
        VertexConsumer v = c.buffers().getBuffer(RenderType.text(look.skin()));
        float aw = look.slim() ? 3 : 4;
        for (int layer = 0; layer < 2; layer++) {
            boolean outer = layer == 1;
            float g = outer ? .25f : 0;
            // The head (the hat a little larger round it).
            cube(v, view, bone(body, pose, HEAD), -4, -8, -4, 4, 0, 4, outer ? .5f : 0, outer ? 32 : 0, 0, 8, 8, 8, 0, 1, true, true);
            // The body, and the bottom of it again at the hips (so a bend at the waist never opens a gap).
            Matrix4f chest = bone(body, pose, CHEST);
            cube(v, view, chest, -4, -6.4f, -2, 4, 5.6f, 2, g, 16, outer ? 32 : 16, 8, 12, 4, 0, 1, true, false);
            cube(v, view, bone(body, pose, PELVIS), -4, -1.6f, -2, 4, .4f, 2, g, 16, outer ? 32 : 16, 8, 12, 4, 10f / 12, 1, false, true);
            for (int side = 0; side < 2; side++) {
                boolean right = side == 0;
                // The arm: above the elbow, the forearm, the hand; its pixels shared out down its length.
                float x0 = right ? -aw + 1 : -1, x1 = right ? 1 : aw - 1;
                int au = right ? 40 : outer ? 48 : 32, av = right ? (outer ? 32 : 16) : 48;
                cube(v, view, bone(body, pose, right ? R_UPPER : L_UPPER), x0, -2, -2, x1, 5, 2, g, au, av, aw, 12, 4, 0, 7f / 12, true, false);
                cube(v, view, bone(body, pose, right ? R_FOREARM : L_FOREARM), x0, 0, -2, x1, 4.8f, 2, g, au, av, aw, 12, 4, 7f / 12, 11f / 12, false, false);
                cube(v, view, bone(body, pose, right ? R_HAND : L_HAND), x0, 0, -2, x1, 1.2f, 2, g, au, av, aw, 12, 4, 11f / 12, 1, false, true);
                // The leg: thigh, shin, foot.
                int lu = right ? 0 : outer ? 0 : 16, lv = right ? (outer ? 32 : 16) : 48;
                cube(v, view, bone(body, pose, right ? R_THIGH : L_THIGH), -2, 0, -2, 2, 6, 2, g, lu, lv, 4, 12, 4, 0, .5f, true, false);
                cube(v, view, bone(body, pose, right ? R_SHIN : L_SHIN), -2, 0, -2, 2, 4.8f, 2, g, lu, lv, 4, 12, 4, .5f, .9f, false, false);
                cube(v, view, bone(body, pose, right ? R_FOOT : L_FOOT), -2, -.1f, -2, 2, 1.2f, 2, g, lu, lv, 4, 12, 4, .9f, 1, false, true);
            }
        }
    }

    // ------------------------------------------------------------------ a box with the skin on it
    private static final Vector3f A = new Vector3f(), B = new Vector3f(), C = new Vector3f(), D = new Vector3f(), M = new Vector3f();
    /**
     * A box in a bone's frame (model pixels; grown by g on every side), skinned as a player model part whose texture
     * starts at (u, v) for a w x h x d cube. Only rows from slice0 to slice1 of its sides are used (the share of a limb
     * this piece is); its top and bottom are drawn when asked.
     */
    private static void cube(VertexConsumer vc, Matrix4f view, Matrix4f bone, float x0, float y0, float z0, float x1, float y1, float z1, float g,
                             float u, float v, float w, float h, float d, float slice0, float slice1, boolean top, boolean bottom) {
        x0 -= g; y0 -= g; z0 -= g; x1 += g; y1 += g; z1 += g;
        bone.transformPosition((x0 + x1) / 32, (y0 + y1) / 32, (z0 + z1) / 32, M);
        float s0 = v + d + h * slice0, s1 = v + d + h * slice1;
        // Front (-z): the viewer's left is the model's right (-x).
        face(vc, view, bone, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, u + d, s0, u + d + w, s1);
        // Back (+z).
        face(vc, view, bone, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, u + 2 * d + w, s0, u + 2 * d + 2 * w, s1);
        // His right side (-x), then his left (+x).
        face(vc, view, bone, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, u, s0, u + d, s1);
        face(vc, view, bone, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, u + d + w, s0, u + 2 * d + w, s1);
        if (top) face(vc, view, bone, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, u + d, v, u + d + w, v + d);
        if (bottom) face(vc, view, bone, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, u + d + w, v, u + d + 2 * w, v + d);
    }
    /**
     * One face: corners top-left, top-right, bottom-right, bottom-left as seen from outside (model pixels), texture
     * pixels (64 x 64 skin). Wound to face outward (away from the box's middle) whatever the bone's turn, lit per corner.
     */
    private static void face(VertexConsumer vc, Matrix4f view, Matrix4f bone, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float u0, float v0, float u1, float v1) {
        bone.transformPosition(ax / 16, ay / 16, az / 16, A); bone.transformPosition(bx / 16, by / 16, bz / 16, B);
        bone.transformPosition(cx / 16, cy / 16, cz / 16, C); bone.transformPosition(dx / 16, dy / 16, dz / 16, D);
        float ux = B.x - A.x, uy = B.y - A.y, uz = B.z - A.z, wx = D.x - A.x, wy = D.y - A.y, wz = D.z - A.z;
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-9f) return;
        nx /= nl; ny /= nl; nz /= nl;
        float ox = (A.x + C.x) * .5f - M.x, oy = (A.y + C.y) * .5f - M.y, oz = (A.z + C.z) * .5f - M.z;
        boolean outward = nx * ox + ny * oy + nz * oz > 0;
        if (!outward) { nx = -nx; ny = -ny; nz = -nz; }
        float tu0 = u0 / 64, tv0 = v0 / 64, tu1 = u1 / 64, tv1 = v1 / 64;
        // (b - a) x (d - a) along the outward normal means a, b, c, d already runs counter-clockwise seen from outside
        // (the front the GPU keeps); otherwise the other way round.
        if (outward) {
            vert(vc, view, A, nx, ny, nz, tu0, tv0); vert(vc, view, B, nx, ny, nz, tu1, tv0);
            vert(vc, view, C, nx, ny, nz, tu1, tv1); vert(vc, view, D, nx, ny, nz, tu0, tv1);
        } else {
            vert(vc, view, A, nx, ny, nz, tu0, tv0); vert(vc, view, D, nx, ny, nz, tu0, tv1);
            vert(vc, view, C, nx, ny, nz, tu1, tv1); vert(vc, view, B, nx, ny, nz, tu1, tv0);
        }
    }
    private static void vert(VertexConsumer vc, Matrix4f view, Vector3f p, float nx, float ny, float nz, float u, float v) {
        int rgb = PursuitShade.shade(p.x, p.y, p.z, nx, ny, nz, 0xffffff, .05f);
        vc.vertex(view, p.x, p.y, p.z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1f).uv(u, v).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
}
