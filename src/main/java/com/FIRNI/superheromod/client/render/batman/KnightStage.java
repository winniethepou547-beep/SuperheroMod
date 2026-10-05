package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.cloth.CapeCloth;
import com.FIRNI.superheromod.client.render.film.FilmBackdrop;
import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.client.render.batman.KnightPath.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanUltBeats.*;

/**
 * Draws KARA ŞÖVALYE's stage (KnightPath / KnightTarget say where everything is): the dead yard walled in by black
 * buildings in the rain, lit by one street lamp (a hard warm cone, its pool on the wet asphalt, the rain bright inside
 * it) and the cage lamp on the warehouse wall; low fog; Batman as a dark shape with burning white eyes and his cloth
 * cape; the target as their own jointed puppet; the grapnel lines, the bomb and its cold shock blast; high up, wisps
 * of cloud over the city's grid; the Batwing with its two lights, engines and scan; the Batarang; the pin in the wall.
 * Solid things first (one batch), then light, smoke and rain (FilmFx.batched, so mixing them never flushes).
 */
public final class KnightStage {
    private KnightStage() {}

    private static FilmCast cast;
    /** The cape needs a body to belong to: a stand far below the world, so it is simulated in the model's own space. */
    private static ArmorStand dummy;
    private static final CapeCloth.Frame FRAME = new CapeCloth.Frame();
    private static final int CAPE_KEY = 0x0B47_0C1A;
    /** Where his gun's muzzle, his eyes and his left hand were last drawn (stage space; null when not drawn). */
    private static Vec3 muzzle, eyes, handLeft;

    public static FilmBackdrop.Params backdrop(float t) {
        float high = window(t, 214, DROP + 6, 10);
        float flash = Math.max(.75f * lightning(t), .5f * flash(t));
        return FilmBackdrop.Params.gotham(new Vec3(-.42, .5, .76), .22f + .4f * high, .7f, 1 - .25f * high, flash);
    }

    public static void render(FilmContext c, Entity target, float t) {
        muzzle = eyes = handLeft = null;
        PoseStack p = c.pose();
        Matrix4f m = p.last().pose();
        yard(c, m, t);
        drawBatman(c, t);
        if (target != null) drawTarget(c, target, t);
        drawBatwing(c, t);
        drawBatarang(c, t);
        drawBomb(c, m, t);
        debris(c, m, t);
        c.buffers().endBatch();
        var fxBuffers = FilmFx.batched();
        FilmContext fx = new FilmContext(c.pose(), fxBuffers, c.camera(), c.viewRight(), c.viewUp(), c.time(), c.local(), c.partial());
        lamps(fx, t);
        shadows(fx, t);
        fog(fx, t);
        clouds(fx, t);
        lines(fx, t);
        blast(fx, t);
        batwingLights(fx, t);
        guns(fx, t);
        vanishes(fx, t);
        smoke(fx, t);
        batarangTrail(fx, t);
        wallHit(fx, t);
        sparks(fx, t);
        eyesAndSignal(fx, t);
        rain(fx, t);
        fxBuffers.endBatch();
    }

    // ------------------------------------------------------------------ light
    /** The street lamp's light at a point (0..1): a soft-edged cone widening downward. */
    static float lamp(double x, double y, double z) {
        double dy = LAMP.y - y;
        if (dy < 0) return 0;
        double r = .55 + dy * .62, dx = x - LAMP.x, dz = z - LAMP.z;
        return (float) (Math.exp(-(dx * dx + dz * dz) / (r * r) * 1.6) / (1 + dy * dy * .02));
    }
    /** The cage lamp on the warehouse wall (0..1). */
    static float wallLamp(double x, double y, double z) {
        double dx = x - WALL_LAMP.x, dy = (y - WALL_LAMP.y) * (y > WALL_LAMP.y ? 2.5 : 1), dz = z - WALL_LAMP.z;
        return (float) Math.exp(-(dx * dx + dy * dy + dz * dz) / 15);
    }
    private static final float[] WARM = {1f, .7f, .4f}, NIGHT = {.08f, .09f, .13f};
    private static int lit(float[] albedo, float x, float y, float z, float vary) {
        float l = lamp(x, y, z), w = wallLamp(x, y, z);
        float r = albedo[0] * vary * (NIGHT[0] + 1.6f * WARM[0] * l + 1.2f * w), g = albedo[1] * vary * (NIGHT[1] + 1.6f * WARM[1] * l + 1.0f * w),
                b = albedo[2] * vary * (NIGHT[2] + 1.6f * WARM[2] * l + .75f * w);
        return rgb(r, g, b);
    }
    static int rgb(float r, float g, float b) {
        return (int) (Mth.clamp(r, 0, 1) * 255) << 16 | (int) (Mth.clamp(g, 0, 1) * 255) << 8 | (int) (Mth.clamp(b, 0, 1) * 255);
    }
    private static void vtx(VertexConsumer v, Matrix4f m, float x, float y, float z, int c) {
        v.vertex(m, x, y, z).color((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 1).endVertex();
    }

    // ------------------------------------------------------------------ the yard
    private static final float[] ASPHALT = {.55f, .55f, .6f}, BRICK = {.62f, .4f, .33f}, CONCRETE = {.5f, .5f, .54f};

    private static void yard(FilmContext c, Matrix4f m, float t) {
        VertexConsumer v = c.buffers().getBuffer(FilmFx.SOLID);
        // The wet asphalt, lit vertex by vertex so the lamp's pool is round; puddles darker and glossier.
        float step = .75f;
        for (float x = -12; x < 12; x += step) {
            for (float z = -14; z < 14; z += step) {
                float x1 = x + step, z1 = z + step;
                float vary = .8f + .35f * (float) FilmFx.hash(Math.floor(x * 1.3) * 31 + Math.floor(z * 1.3) * 7);
                vtx(v, m, x, 0, z1, ground(x, z1, vary)); vtx(v, m, x1, 0, z1, ground(x1, z1, vary));
                vtx(v, m, x1, 0, z, ground(x1, z, vary)); vtx(v, m, x, 0, z, ground(x, z, vary));
            }
        }
        // The warehouse wall ahead: brick lit by its cage lamp; a shuttered loading door; dark high windows.
        for (int x = -16; x < 16; x++) {
            for (int y = 0; y < WALL_H; y++) {
                float vary = .75f + .4f * (float) FilmFx.hash(x * 17 + y * 5.3);
                boolean door = x >= -7 && x < -2 && y < 5;
                float[] al = door ? CONCRETE : BRICK;
                if (door) vary = .55f + .08f * (y % 2);
                float z = (float) WALL_Z;
                vtx(v, m, x + 1, y, z, lit(al, x + 1, y, z, vary)); vtx(v, m, x, y, z, lit(al, x, y, z, vary));
                vtx(v, m, x, y + 1, z, lit(al, x, y + 1, z, vary)); vtx(v, m, x + 1, y + 1, z, lit(al, x + 1, y + 1, z, vary));
            }
        }
        FilmFx.cube(c, m, -20, 0, (float) WALL_Z + .02f, 20, (float) WALL_H, 30, 0x0d0d10);
        FilmFx.cube(c, m, -20, (float) WALL_H, (float) WALL_Z - .3f, 20, (float) WALL_H + .5f, (float) WALL_Z + .3f, 0x15151a);
        // The building behind (-z): tall and black, a parapet, a Gothic spire and a water tower against the sky.
        FilmFx.cube(c, m, -26, 0, -30, 26, (float) BACK_H, (float) BACK_Z, 0x0c0d10);
        FilmFx.cube(c, m, -26, (float) BACK_H, (float) BACK_Z - .5f, 26, (float) BACK_H + .5f, (float) BACK_Z, 0x16171b);
        float sx = 9, sz = -21;
        float[][] spire = {{1.6f, 6}, {1.1f, 4}, {.6f, 4}, {.22f, 3.5f}};
        float y0 = (float) BACK_H;
        for (float[] s : spire) {
            FilmFx.cube(c, m, sx - s[0], y0, sz - s[0], sx + s[0], y0 + s[1], sz + s[0], 0x0e0f13);
            y0 += s[1];
        }
        float tx = -7, tz = -19, ty = (float) BACK_H;
        for (int i = 0; i < 4; i++) {
            float lx = tx + (i % 2 == 0 ? -1.1f : 1.1f), lz = tz + (i < 2 ? -1.1f : 1.1f);
            FilmFx.cube(c, m, lx - .1f, ty, lz - .1f, lx + .1f, ty + 3.2f, lz + .1f, 0x111216);
        }
        FilmFx.cube(c, m, tx - 1.4f, ty + 3.2f, tz - 1.4f, tx + 1.4f, ty + 6.4f, tz + 1.4f, 0x121317);
        FilmFx.cube(c, m, tx - 1f, ty + 6.4f, tz - 1f, tx + 1f, ty + 7.1f, tz + 1f, 0x101115);
        FilmFx.cube(c, m, tx - .5f, ty + 7.1f, tz - .5f, tx + .5f, ty + 7.6f, tz + .5f, 0x0e0f12);
        // The fire escape building (right, -x): platforms, rails and stairs zig-zagging up.
        FilmFx.cube(c, m, -30, 0, -14, (float) EAST_X, (float) EAST_H, 14, 0x0e0f12);
        FilmFx.cube(c, m, (float) EAST_X - .4f, (float) EAST_H, -14, (float) EAST_X, (float) EAST_H + .5f, 14, 0x16171b);
        float ex = (float) EAST_X;
        float[] floors = {4.2f, 8.3f, 12.4f};
        for (int f = 0; f < floors.length; f++) {
            float fy = floors[f];
            int metal = lit(CONCRETE, ex + 1, fy, 3, .35f);
            FilmFx.cube(c, m, ex, fy - .1f, 1.0f, ex + 1.45f, fy, 5.4f, metal);
            FilmFx.cube(c, m, ex + 1.38f, fy + .95f, 1.0f, ex + 1.46f, fy + 1.02f, 5.4f, metal);
            for (float z = 1.0f; z <= 5.41f; z += .88f) FilmFx.cube(c, m, ex + 1.38f, fy, z - .03f, ex + 1.45f, fy + 1f, z + .03f, metal);
            // The stairs up to the next platform.
            if (f + 1 < floors.length) {
                float rise = floors[f + 1] - fy;
                for (int s = 0; s < 8; s++) {
                    float k = s / 8f;
                    float zz = 1.3f + 3.2f * k, yy = fy + rise * k;
                    FilmFx.cube(c, m, ex + .1f, yy, zz, ex + .8f, yy + .06f, zz + .32f, metal);
                }
            }
            // A window behind each platform; a couple lit.
            int glass = f == 1 ? 0x2a2014 : f == 2 ? 0x0f1520 : 0x0b0c0f;
            FilmFx.cube(c, m, ex - .02f, fy + .4f, 2.4f, ex + .01f, fy + 2.2f, 3.8f, glass);
        }
        // The low building on the left (+x): a parapet and boxes on the roof.
        FilmFx.cube(c, m, (float) WEST_X, 0, -14, 30, (float) WEST_H, 14, 0x0f1013);
        FilmFx.cube(c, m, (float) WEST_X, (float) WEST_H, -14, (float) WEST_X + .4f, (float) WEST_H + .5f, 14, 0x17181c);
        FilmFx.cube(c, m, (float) WEST_X + 3, (float) WEST_H, 4, (float) WEST_X + 5.5f, (float) WEST_H + 1.6f, 6.5f, 0x121317);
        // Lit windows here and there (most of the city sleeps).
        float[][] windows = {{-6, 9.5f, 1}, {4, 9.5f, 0}, {9, 6.5f, 1}, {-12, 7.5f, 2}, {-3, 13.5f, 2}, {6, 4.5f, 3}, {-9, 3.5f, 3}};
        for (float[] w : windows) {
            int col = w[2] == 0 ? 0x5a4126 : w[2] == 1 ? 0x2a3348 : 0x3d2c18;
            if (w[2] < 2) FilmFx.cube(c, m, w[0], w[1], (float) WALL_Z - .02f, w[0] + 1.1f, w[1] + 1.4f, (float) WALL_Z, col);
            else if (w[2] == 2) FilmFx.cube(c, m, w[0], w[1], (float) BACK_Z, w[0] + 1.1f, w[1] + 1.5f, (float) BACK_Z + .02f, col);
            else FilmFx.cube(c, m, (float) WEST_X - .02f, w[1], w[0], (float) WEST_X, w[1] + 1.4f, w[0] + 1.1f, col);
        }
        // Junk in the yard: crates, barrels, a dumpster, the fence posts.
        crate(c, m, 4.4f, 0, 3.4f, 1.1f, .2f);
        crate(c, m, 4.6f, 1.1f, 3.6f, .9f, .6f);
        crate(c, m, 5.7f, 0, 3.2f, 1f, -.3f);
        crate(c, m, -6.2f, 0, -5.8f, 1.2f, .4f);
        for (int i = 0; i < 3; i++) {
            float bx = -4.6f + .7f * i, bz = 5.2f + .25f * (i % 2);
            FilmFx.cube(c, m, bx - .3f, 0, bz - .3f, bx + .3f, .95f, bz + .3f, lit(new float[]{.35f, .14f, .1f}, bx, .5f, bz, 1));
        }
        FilmFx.cube(c, m, 7.4f, 0, -11.4f, 9.8f, 1.3f, -9.8f, lit(new float[]{.12f, .26f, .16f}, 8.6f, 1, -10.6f, 1));
        FilmFx.cube(c, m, 7.3f, 1.3f, -11.5f, 9.9f, 1.42f, -9.7f, 0x0c120e);
        for (float z = -6; z <= 6.01f; z += 2) FilmFx.cube(c, m, 8f, 0, z - .04f, 8.08f, 2.3f, z + .04f, lit(CONCRETE, 8, 1.5f, z, .4f));
        FilmFx.cube(c, m, 8f, 2.2f, -6, 8.08f, 2.28f, 6, lit(CONCRETE, 8, 2.2f, 0, .4f));
        // The street lamp: pole, arm, head, and the bulb's face (it is the light, so it is drawn bright).
        int pole = lit(new float[]{.22f, .23f, .26f}, (float) POLE.x, 3, (float) POLE.z, .7f);
        FilmFx.cube(c, m, (float) POLE.x - .07f, 0, (float) POLE.z - .07f, (float) POLE.x + .07f, 5.2f, (float) POLE.z + .07f, pole);
        FilmFx.cube(c, m, (float) POLE.x - .14f, 0, (float) POLE.z - .14f, (float) POLE.x + .14f, .5f, (float) POLE.z + .14f, pole);
        FilmFx.cube(c, m, (float) LAMP.x, 5.12f, (float) POLE.z - .04f, (float) POLE.x, 5.2f, (float) POLE.z + .04f, pole);
        FilmFx.cube(c, m, (float) LAMP.x - .04f, 5.12f, (float) LAMP.z, (float) LAMP.x + .04f, 5.2f, (float) POLE.z, pole);
        FilmFx.cube(c, m, (float) LAMP.x - .26f, (float) LAMP.y + .02f, (float) LAMP.z - .2f, (float) LAMP.x + .26f, (float) LAMP.y + .3f, (float) LAMP.z + .2f, 0x18191d);
        FilmFx.cube(c, m, (float) LAMP.x - .2f, (float) LAMP.y - .01f, (float) LAMP.z - .14f, (float) LAMP.x + .2f, (float) LAMP.y + .02f, (float) LAMP.z + .14f, 0xffe6bc);
        // The cage lamp on the wall.
        FilmFx.cube(c, m, (float) WALL_LAMP.x - .18f, (float) WALL_LAMP.y - .1f, (float) WALL_Z - .45f, (float) WALL_LAMP.x + .18f, (float) WALL_LAMP.y + .25f, (float) WALL_Z, 0x1a1b1f);
        FilmFx.cube(c, m, (float) WALL_LAMP.x - .12f, (float) WALL_LAMP.y - .14f, (float) WALL_Z - .4f, (float) WALL_LAMP.x + .12f, (float) WALL_LAMP.y - .1f, (float) WALL_Z - .1f, 0xffdcae);
    }
    private static int ground(float x, float z, float vary) {
        // Puddles: patches of a smooth noise, darker but taking more of the light.
        float n = Mth.sin(x * .7f + 1.3f) * Mth.sin(z * .6f + .4f) + .5f * Mth.sin(x * 1.7f - z * 1.3f);
        float puddle = Mth.clamp((n - .55f) * 3, 0, 1);
        float l = lamp(x, 0, z), w = wallLamp(x, 0, z);
        float base = (.045f + .01f * vary) * (1 - .4f * puddle);
        float shine = (.42f + .5f * puddle) * l + .3f * w;
        return rgb(base + shine * WARM[0] * .9f, base + shine * WARM[1] * .85f, base * 1.15f + shine * WARM[2] * .8f);
    }
    private static void crate(FilmContext c, Matrix4f m, float x, float y, float z, float s, float turn) {
        PoseStack p = c.pose();
        p.pushPose();
        p.translate(x, y, z);
        p.mulPose(Axis.YP.rotation(turn));
        int col = lit(new float[]{.32f, .22f, .13f}, x, y + s * .5f, z, 1);
        FilmFx.cube(c, p.last().pose(), -s / 2, 0, -s / 2, s / 2, s, s / 2, col);
        p.popPose();
    }

    // ------------------------------------------------------------------ Batman
    private static void drawBatman(FilmContext c, float t) {
        KnightPath.Act a = KnightPath.batman(t, c.time());
        if (!a.shown) return;
        var level = Minecraft.getInstance().level;
        if (level != null && (dummy == null || dummy.level() != level)) dummy = stand(level);
        PoseStack p = c.pose();
        p.pushPose();
        p.translate(a.at.x, a.at.y, a.at.z);
        p.mulPose(Axis.YP.rotation(Mth.PI - a.yaw));
        if (a.pitch != 0 || a.roll != 0) {
            p.translate(0, MIDDLE, 0);
            p.mulPose(Axis.XP.rotation(-a.pitch));
            p.mulPose(Axis.ZP.rotation(a.roll));
            p.translate(0, -MIDDLE, 0);
        }
        p.scale(-BODY, -BODY, BODY);
        p.translate(0, -1.501, 0);
        // Lit by where he is: the lamp's light when he stands in it, black against the sky everywhere else.
        Vec3 mid = a.middle();
        float l = Math.max(a.light, .9f * lamp(mid.x, mid.y, mid.z));
        int light = LightTexture.pack(Mth.clamp(Math.round(l * 15), 0, 15), 0);
        BatmanBody.HOLD[0] = a.holdRight; BatmanBody.HOLD_ARG[0] = a.holdArg;
        BatmanBody.HOLD[1] = a.holdLeft; BatmanBody.HOLD_ARG[1] = 0;
        BatmanBody.capture = true;
        try {
            BatmanBody.draw(p, c.buffers(), light, a.pose, a.lookYaw, a.lookPitch, c.time(), 0, FRAME);
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (BatmanBody.muzzle != null) muzzle = BatmanBody.muzzle.subtract(cam).add(c.camera());
            if (BatmanBody.eyes != null) eyes = BatmanBody.eyes.subtract(cam).add(c.camera());
            if (BatmanBody.handLeft != null) handLeft = BatmanBody.handLeft.subtract(cam).add(c.camera());
        } finally {
            BatmanBody.capture = false;
            BatmanBody.HOLD[0] = BatmanBody.HOLD[1] = BatmanBody.HOLD_NONE;
            BatmanBody.HOLD_ARG[0] = BatmanBody.HOLD_ARG[1] = 0;
        }
        FRAME.spread = a.spread;
        if (dummy != null) CapeCloth.draw(p, c.buffers(), light, dummy, c.partial(), CAPE_KEY, FRAME, BatmanLayer.CAPE);
        p.popPose();
    }
    private static ArmorStand stand(Level level) {
        ArmorStand s = new ArmorStand(EntityType.ARMOR_STAND, level);
        s.setPos(0, -4000, 0);
        s.xo = s.getX(); s.yo = s.getY(); s.zo = s.getZ();
        return s;
    }

    // ------------------------------------------------------------------ the target
    private static void drawTarget(FilmContext c, Entity target, float t) {
        if (cast == null || cast.entity() != target) cast = FilmCast.of(target);
        if (cast == null) return;
        Vec3 at = KnightTarget.centre(t);
        PoseStack p = c.pose();
        p.pushPose();
        p.translate(at.x, at.y, at.z);
        p.mulPose(Axis.ZP.rotationDegrees(KnightTarget.roll(t)));
        p.mulPose(Axis.XP.rotationDegrees(KnightTarget.tumble(t)));
        p.translate(0, -KnightTarget.MID, 0);
        try {
            cast.draw(c, Vec3.ZERO, KnightTarget.yaw(t), KnightTarget.pose(t), 1, KnightTarget.tint(t));
        } finally {
            p.popPose();
        }
    }

    // ------------------------------------------------------------------ the Batwing
    private static final int HULL = 0x15171c, HULL_D = 0x0c0d10, EDGE = 0x262a31, GLASS = 0x0b1822;
    /** Its size against the bodies. */
    private static final float WING_SCALE = .8f;

    private static Quaternionf batwingTurn(float t) {
        Vec3 h = batwingHeading(t);
        return new Quaternionf().rotationY((float) Math.atan2(h.x, h.z)).rotateX((float) -Math.asin(Mth.clamp(h.y, -1, 1))).rotateZ(batwingBank(t));
    }
    /** A point of the Batwing (its own space: +z the nose, +x its left, y up) in stage space. */
    private static Vec3 wingPoint(Vec3 at, Quaternionf turn, float x, float y, float z) {
        Vector3f v = new Vector3f(x * WING_SCALE, y * WING_SCALE, z * WING_SCALE);
        turn.transform(v);
        return at.add(v.x, v.y, v.z);
    }
    private static void drawBatwing(FilmContext c, float t) {
        Vec3 at = batwing(t);
        if (at == null || at.distanceTo(c.camera()) > 90) return;
        PoseStack p = c.pose();
        p.pushPose();
        p.translate(at.x, at.y, at.z);
        p.mulPose(batwingTurn(t));
        p.scale(WING_SCALE, WING_SCALE, WING_SCALE);
        Matrix4f m = p.last().pose();
        // The body: a long armoured hull, the nose, the dark canopy, intakes, the two engines, a keel under it.
        FilmFx.cube(c, m, -.7f, -.35f, -3.2f, .7f, .4f, 3.0f, HULL);
        FilmFx.cube(c, m, -.42f, .4f, -2.6f, .42f, .62f, 1.2f, HULL_D);
        FilmFx.cube(c, m, -.5f, -.28f, 3.0f, .5f, .3f, 4.0f, HULL);
        FilmFx.cube(c, m, -.28f, -.18f, 4.0f, .28f, .16f, 4.75f, HULL_D);
        FilmFx.cube(c, m, -.36f, .4f, 1.2f, .36f, .74f, 2.7f, GLASS);
        FilmFx.cube(c, m, -.5f, -.56f, -2.0f, .5f, -.35f, 2.2f, HULL_D);
        for (int s = -1; s <= 1; s += 2) {
            FilmFx.cube(c, m, Math.min(s * .7f, s * 1.08f), -.3f, -.5f, Math.max(s * .7f, s * 1.08f), .18f, 1.7f, HULL_D);
            FilmFx.cube(c, m, Math.min(s * .55f, s * 1.18f), -.48f, -3.65f, Math.max(s * .55f, s * 1.18f), .16f, -1.2f, EDGE);
            // The bat wing: stepping out and back, drooping a little toward the tip, ribs along the scalloped trailing edge.
            for (int i = 0; i < 5; i++) {
                float xa = s * (.9f + .85f * i), xb = s * (.9f + .85f * (i + 1));
                float lead = .7f - .58f * i, chord = 2.5f - .38f * i, y = -.05f * i;
                FilmFx.cube(c, m, Math.min(xa, xb), y - .08f, lead - chord, Math.max(xa, xb), y + .08f, lead, i % 2 == 0 ? HULL : EDGE);
                if (i > 0) FilmFx.cube(c, m, Math.min(xa, xa + s * .14f), y - .06f, lead - chord - .55f, Math.max(xa, xa + s * .14f), y + .06f, lead - chord + .1f, HULL_D);
            }
            float tip = s * 5.15f;
            FilmFx.cube(c, m, Math.min(tip, tip + s * .3f), -.45f, -2.4f, Math.max(tip, tip + s * .3f), .05f, -1.0f, HULL_D);
            // The tail fins, canted out.
            p.pushPose();
            p.translate(s * .42f, .4f, -2.7f);
            p.mulPose(Axis.ZP.rotation(-s * .3f));
            FilmFx.cube(c, p.last().pose(), -.06f, 0, -.65f, .06f, 1.15f, .55f, HULL);
            p.popPose();
        }
        p.popPose();
    }
    private static void batwingLights(FilmContext c, float t) {
        Vec3 at = batwing(t);
        if (at == null) return;
        Quaternionf turn = batwingTurn(t);
        double dist = at.distanceTo(c.camera());
        float far = (float) Math.max(1, dist * .012);
        float show = KnightPath.clamp((t - LIGHTS) / 4f);
        // The two lights: what is seen first, far off in the dark.
        for (int s = -1; s <= 1; s += 2) {
            Vec3 l = wingPoint(at, turn, s * .32f, 0, 4.4f);
            FilmFx.glow(c, l, .45 * far, 0xeaf4ff, .95f * show);
            FilmFx.glow(c, l, 1.6 * far, 0x8fb6ff, .3f * show);
            if (dist < 70) FilmFx.streak(c, l, wingPoint(at, turn, s * .6f, -.4f, 11), .5, 0xbfd6ff, .14f * show, 0, true);
            // Engines and wing-tip lights.
            Vec3 e = wingPoint(at, turn, s * .86f, -.16f, -3.75f);
            FilmFx.glow(c, e, .55, 0x9fd4ff, .8f);
            FilmFx.streak(c, wingPoint(at, turn, s * .86f, -.16f, -7f), e, .22, 0x7fb8ff, 0, .5f, true);
            boolean blink = Mth.sin(t * .9f + s) > .6f;
            FilmFx.glow(c, wingPoint(at, turn, s * 5.3f, -.25f, -1.7f), .25, s > 0 ? 0xff3b30 : 0xe8fff0, blink ? .9f : .25f);
        }
        FilmFx.glow(c, wingPoint(at, turn, 0, .6f, 2.0f), .5, 0x5fe0ff, .22f);
    }
    /**
     * The Batwing's guns: a round every tick, the two guns taking turns, each a hot streak flying at ROUND_SPEED from the
     * muzzle to a point on the body (scattered a little), a flash at the muzzle as it leaves, sparks and a puff where it
     * strikes. Nothing graphic: sparks off a falling body, as in the games.
     */
    private static void guns(FilmContext c, float t) {
        int first = (int) Math.floor(t - 12), last = (int) Math.floor(t);
        for (int tick = Math.max(first, SCAN); tick <= last; tick++) {
            if (!KnightPath.fires(tick)) continue;
            Vec3 at = batwing(tick);
            if (at == null) continue;
            int gun = tick % 2 == 0 ? 1 : -1;
            Vec3 muzzle = wingPoint(at, batwingTurn(tick), gun * KnightPath.GUN_X, KnightPath.GUN_Y, KnightPath.GUN_Z);
            double jx = (FilmFx.hash(tick * 3.1) - .5) * .7, jy = (FilmFx.hash(tick * 5.3) - .5) * 1.4, jz = (FilmFx.hash(tick * 7.7) - .5) * .5;
            float age = t - tick;
            // Where it strikes: the body where it is when the round gets there.
            Vec3 aimAt = KnightTarget.point(tick + 1.5f, jx, jy, jz);
            double dist = aimAt.distanceTo(muzzle);
            float flight = (float) (dist / KnightPath.ROUND_SPEED);
            if (age < .9f) {
                FilmFx.glow(c, muzzle, .9, 0xffd27a, .9f * (1 - age / .9f));
                FilmFx.glow(c, muzzle, .35, 0xffffff, 1 - age / .9f);
            }
            if (age < flight) {
                Vec3 dir = aimAt.subtract(muzzle).normalize();
                Vec3 head = muzzle.add(dir.scale(age * KnightPath.ROUND_SPEED));
                Vec3 tail = head.subtract(dir.scale(Math.min(2.4, age * KnightPath.ROUND_SPEED)));
                FilmFx.streak(c, tail, head, .05, 0xffb04a, 0, .95f, true);
                FilmFx.streak(c, tail, head, .018, 0xffffff, 0, .9f, true);
            } else if (age < flight + 6) {
                float d = (age - flight) / 6;
                Vec3 hit = KnightTarget.point(Math.min(t, HIT), jx, jy, jz);
                if (d < .35f) FilmFx.glow(c, hit, .5, 0xffe2a0, .9f * (1 - d / .35f));
                for (int k = 0; k < 4; k++) {
                    Vec3 sv = new Vec3(FilmFx.hash(tick * 11 + k) - .5, FilmFx.hash(tick * 13 + k) - .2, FilmFx.hash(tick * 17 + k) - .5).normalize();
                    Vec3 sa = hit.add(sv.scale(.15 + 1.1 * d)), sb = hit.add(sv.scale(.05 + .8 * d));
                    FilmFx.streak(c, sb, sa, .015, 0xffc060, 0, .9f * (1 - d), true);
                }
                FilmFx.puff(c, hit, .3 + .6 * d, 0x3a3a40, .35f * (1 - d));
            }
        }
    }
    /**
     * The glimpses' endings: shadow pouring up over him as he melts into the dark (his eyes go last); a burst of black
     * smoke that swallows him and drifts off; the blink is the film's own (an eyelid over the frame).
     */
    private static Vec3 lastEyes;
    private static void vanishes(FilmContext c, float t) {
        float[] v = KnightPath.vanish(t);
        if (v == null) return;
        int kind = (int) v[0];
        float d = v[1];
        Vec3 at = KnightPath.spot((int) v[2]);
        if (kind == KnightPath.VANISH_DARK) {
            float k = KnightPath.clamp(d / KnightPath.DARK_TICKS);
            if (d > -1 && d < KnightPath.DARK_TICKS + 14) {
                float out = 1 - KnightPath.clamp((d - KnightPath.DARK_TICKS) / 14f);
                for (int i = 0; i < 16; i++) {
                    double a = FilmFx.hash(i * 2.7) * Math.PI * 2, r = .2 + .7 * FilmFx.hash(i * 4.1);
                    double rise = Math.min(1, k * 1.3) * (.2 + 1.9 * FilmFx.hash(i * 6.3)) + .15 * d / 10;
                    Vec3 pf = at.add(Math.cos(a) * r, rise, Math.sin(a) * r);
                    FilmFx.puff(c, pf, .5 + .7 * k + .3 * FilmFx.hash(i), 0x040406, .85f * Math.min(1, k * 2) * out);
                }
            }
            // His eyes, the last of him, fading in the dark.
            if (eyes != null && d < KnightPath.DARK_TICKS * .75f) lastEyes = eyes;
            if (lastEyes != null && d >= KnightPath.DARK_TICKS * .75f && d < KnightPath.DARK_TICKS + 4) {
                float e = 1 - KnightPath.clamp((d - KnightPath.DARK_TICKS * .75f) / (KnightPath.DARK_TICKS * .25f + 4));
                FilmFx.glow(c, lastEyes, .18, 0xf0f6ff, .9f * e);
                FilmFx.glow(c, lastEyes, .5, 0x9fb8ff, .3f * e);
            }
        } else if (kind == KnightPath.VANISH_SMOKE && d > -1 && d < 30) {
            // A smoke pellet at his feet: a dense black burst that hides him at once, then rolls and thins away.
            float k = KnightPath.clamp(d / 30f);
            if (d < 2) FilmFx.glow(c, at.add(0, .2, 0), 1.2, 0xfff0d8, .4f * (1 - d / 2));
            for (int i = 0; i < 22; i++) {
                double a = FilmFx.hash(i * 3.3) * Math.PI * 2, r = (.3 + 1.4 * FilmFx.hash(i * 1.9)) * (1 - Math.exp(-(d + 1) / 3));
                Vec3 pf = at.add(Math.cos(a) * r, .2 + 2.0 * FilmFx.hash(i * 5.1) * (1 - Math.exp(-(d + 1) / 4)) + .03 * d, Math.sin(a) * r);
                FilmFx.puff(c, pf, .7 + 1.1 * k + .4 * FilmFx.hash(i * 7), FilmFx.hash(i) < .5 ? 0x0b0c0f : 0x1b1c21, .9f * (1 - k * k));
            }
        }
    }

    // ------------------------------------------------------------------ lines, the bomb, the blast
    private static void lines(FilmContext c, float t) {
        Vec3 hook = KnightPath.hook(t);
        if (hook != null) {
            Vec3 from = muzzle != null && t < LEAP ? muzzle : SNIPE.add(0, 1.45, .3);
            if (t >= LEAP) from = from.lerp(hook, KnightPath.clamp((t - LEAP) / 2f));
            line(c, from, hook, t >= BITE);
        }
        Vec3 hook2 = KnightPath.hook2(t);
        if (hook2 != null) {
            KnightPath.Act a = KnightPath.batman(t, c.time());
            if (a.shown) line(c, muzzle != null ? muzzle : a.at.add(0, 1.5, 0), hook2, t >= BITE2);
        }
    }
    private static void line(FilmContext c, Vec3 from, Vec3 to, boolean bitten) {
        FilmFx.streak(c, from, to, .022, 0x0d0e11, .95f, .95f, false);
        FilmFx.streak(c, from, to, .012, 0x9aa4b8, .22f, .3f, true);
        FilmFx.glow(c, to, .14, 0xcfd8e8, bitten ? .25f : .6f);
    }
    private static void drawBomb(FilmContext c, Matrix4f m, float t) {
        Vec3 b = bomb(t);
        if (b == null) return;
        FilmFx.cube(c, m, (float) b.x - .09f, (float) b.y - .06f, (float) b.z - .09f, (float) b.x + .09f, (float) b.y + .06f, (float) b.z + .09f, 0x1b1d22);
    }
    private static void blast(FilmContext c, float t) {
        Vec3 b = bomb(t);
        if (b != null) {
            // The fuse light: blinking faster and faster.
            float u = (t - PLANT) / (BLAST - PLANT), period = Mth.lerp(u, 9, 1.4f);
            if (((t - PLANT) % period) / period < .4f) FilmFx.glow(c, b, .25, 0xff2a1e, .95f);
            FilmFx.glow(c, b, .07, 0xff6a50, .9f);
        }
        float d = t - BLAST;
        if (d < 0 || d > 30) return;
        Vec3 at = blastAt();
        // A cold shock, not fire: a white core, shells of blue light spreading, sparks thrown out, a ring of smoke.
        if (d < 6) FilmFx.glow(c, at, 3.2 * (1 - d / 6), 0xffffff, .95f * (1 - d / 6));
        if (d < 10) FilmFx.glow(c, at, 6 * (1 - d / 10) + 1, 0x7fc8ff, .55f * (1 - d / 10));
        float r = 7 * (1 - (float) Math.exp(-d / 3.2f)), a = Math.max(0, 1 - d / 16);
        PoseStack p = c.pose();
        for (int i = 0; i < 3; i++) {
            p.pushPose();
            p.translate(at.x, at.y, at.z);
            p.mulPose(Axis.XP.rotation(i * 1.05f + .3f));
            p.mulPose(Axis.ZP.rotation(i * .7f));
            FilmFx.ring(c, Vec3.ZERO, r * (1 - .12f * i), .35, 0xbfe6ff, .7f * a, true);
            p.popPose();
        }
        for (int i = 0; i < 26; i++) {
            Vec3 dir = new Vec3(FilmFx.hash(i * 3.1) - .5, FilmFx.hash(i * 5.7) - .5, FilmFx.hash(i * 9.3) - .5).normalize();
            double reach = (2 + 6 * FilmFx.hash(i * 1.7)) * (1 - Math.exp(-d / 2.5));
            Vec3 head = at.add(dir.scale(reach)), tail = at.add(dir.scale(reach * .6));
            FilmFx.streak(c, tail, head, .04, i % 3 == 0 ? 0xffffff : 0x9fd8ff, 0, .9f * Math.max(0, 1 - d / 9), true);
        }
        for (int i = 0; i < 16; i++) {
            double ang = i / 16.0 * Math.PI * 2;
            double rr = 1 + 4.5 * (1 - Math.exp(-d / 5));
            Vec3 pf = at.add(Math.cos(ang) * rr, .4 * Math.sin(ang * 3 + d * .1), Math.sin(ang) * rr);
            FilmFx.puff(c, pf, 1.2 + d * .08, 0x2c2f36, .5f * Math.max(0, 1 - d / 28));
        }
    }

    // ------------------------------------------------------------------ the Batarang and the wall
    private static void drawBatarang(FilmContext c, float t) {
        Vec3 at = batarang(t);
        if (at == null) return;
        PoseStack p = c.pose();
        p.pushPose();
        p.translate(at.x, at.y, at.z);
        if (t < WALL) {
            Vec3 a = batarang(Math.max(THROW, t - .5f)), b = batarang(t + .5f);
            Vec3 d = b == null || a == null || b.distanceToSqr(a) < 1e-8 ? new Vec3(0, 0, 1) : b.subtract(a).normalize();
            p.mulPose(Axis.YP.rotation((float) Math.atan2(d.x, d.z)));
            p.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(d.y, -1, 1))));
            if (t < HIT) p.mulPose(Axis.YP.rotation((t - THROW) * 1.9f));
        } else {
            // In the wall: the nose buried, tilted, the wings out.
            p.mulPose(Axis.XP.rotation(-.15f));
            p.mulPose(Axis.ZP.rotation(.45f));
            p.translate(0, 0, .08);
        }
        p.scale(2.4f, 2.4f, 2.4f);
        float l = Math.max(t < WALL ? .7f : .25f, wallLamp(at.x, at.y, at.z));
        BatmanGear.batarang(p, BatmanGear.buffer(c.buffers()), LightTexture.pack(Math.round(l * 15), 0), 1);
        p.popPose();
    }
    private static void batarangTrail(FilmContext c, float t) {
        if (t < THROW || t > WALL + 2) return;
        Vec3 head = batarang(Math.min(t, WALL)), tail = batarang(Math.max(THROW, Math.min(t, WALL) - 4f));
        if (head == null || tail == null) return;
        FilmFx.streak(c, tail, head, .09, 0xdfeaff, 0, .6f, true);
        // A cold glint on its blades as it spins: it is the one bright thing in the air.
        float glint = .45f + .35f * Math.abs(Mth.sin((t - THROW) * 1.9f));
        FilmFx.glow(c, head, .45, 0xe6f0ff, t < HIT ? glint : .2f);
        FilmFx.glow(c, head, 1.1, 0x9fb8ff, t < HIT ? .18f : .08f);
    }
    /**
     * Sparks: a burst of metal on the body where the Batarang strikes (HIT), the rest dragged along with it into the
     * wall, and a big spray off the brick when it pins them (WALL), falling and dying out.
     */
    private static void sparks(FilmContext c, float t) {
        burst(c, t - HIT, batarang(HIT), 16, 1, 7);
        burst(c, t - WALL, stuck(), 30, 2, 13);
        // Dragged along the carry: a few sparks shed behind it as it rides the body in.
        if (t >= HIT && t < WALL) for (int i = 0; i < 4; i++) {
            Vec3 a = batarang(t - .3f * i), b = batarang(t - .3f * i - .5f);
            if (a == null || b == null) continue;
            Vec3 off = new Vec3(FilmFx.hash(i * 3.1 + Math.floor(t)) - .5, FilmFx.hash(i * 5.3 + Math.floor(t)) - .7, FilmFx.hash(i * 7.7 + Math.floor(t)) - .5).scale(.4);
            FilmFx.streak(c, b.add(off), a.add(off.scale(.5)), .02, 0xffcf7a, 0, .8f, true);
        }
    }
    /** One spray of sparks d ticks after it began at the point (kind 1: off a body, all round; 2: off the wall, out of it). */
    private static void burst(FilmContext c, float d, Vec3 at, int count, int kind, float life) {
        if (at == null || d < 0 || d > life) return;
        if (d < 3) {
            FilmFx.glow(c, at, (kind == 2 ? 1.6 : 1.0) * (1 - d / 3), 0xfff2d0, .9f * (1 - d / 3));
            FilmFx.ring(c, at, .2 + (kind == 2 ? 1.6 : 1.0) * d / 3, .06, 0xffe0a0, .6f * (1 - d / 3), true);
        }
        for (int i = 0; i < count; i++) {
            double a = FilmFx.hash(i * 3.7 + kind) * Math.PI * 2, e = (FilmFx.hash(i * 5.9 + kind) - .35) * 1.6;
            double sp = .25 + .35 * FilmFx.hash(i * 8.3 + kind);
            Vec3 v = new Vec3(Math.cos(a) * Math.cos(e), Math.sin(e), Math.sin(a) * Math.cos(e)).scale(sp);
            // Off the wall they fly out of it (toward -z), spread flat along the brick.
            if (kind == 2) v = new Vec3(v.x * 1.3, v.y + .08, -Math.abs(v.z) * .8 - .12);
            float my = life * (.55f + .45f * (float) FilmFx.hash(i * 1.3 + kind));
            if (d > my) continue;
            Vec3 head = at.add(v.scale(d)).add(0, -.018 * d * d, 0);
            Vec3 tail = at.add(v.scale(Math.max(0, d - .9))).add(0, -.018 * Math.max(0, d - .9) * Math.max(0, d - .9), 0);
            float fade = 1 - d / my;
            FilmFx.streak(c, tail, head, .028, fade > .5f ? 0xfff0c0 : 0xffa040, 0, .95f * fade, true);
        }
    }
    private static void wallHit(FilmContext c, float t) {
        float d = t - WALL;
        if (d < 0) return;
        Vec3 at = stuck();
        // The cracks it leaves in the brick (they stay), the dust bursting off the wall, a spark at the hit.
        for (int i = 0; i < 9; i++) {
            double ang = i / 9.0 * Math.PI * 2 + FilmFx.hash(i * 4.4);
            double len = .5 + 1.1 * FilmFx.hash(i * 2.3);
            Vec3 tip = at.add(Math.cos(ang) * len, Math.sin(ang) * len, -.01);
            Vec3 knee = at.add(Math.cos(ang + .3) * len * .5, Math.sin(ang + .3) * len * .5, -.01);
            FilmFx.streak(c, at.add(0, 0, -.01), knee, .025, 0x050506, .9f, .8f, false);
            FilmFx.streak(c, knee, tip, .018, 0x050506, .8f, 0, false);
        }
        if (d < 4) FilmFx.glow(c, at, 1.2 * (1 - d / 4), 0xffffff, .8f * (1 - d / 4));
        for (int i = 0; i < 10 && d < 30; i++) {
            double ang = i / 10.0 * Math.PI * 2;
            double rr = .3 + 1.6 * (1 - Math.exp(-d / 4));
            Vec3 pf = at.add(Math.cos(ang) * rr, Math.sin(ang) * rr - d * .02, -.3 - .25 * FilmFx.hash(i));
            FilmFx.puff(c, pf, .5 + d * .04, 0x5b5148, .45f * Math.max(0, 1 - d / 30));
        }
    }
    /** Chips of brick knocked off the wall, falling. */
    private static void debris(FilmContext c, Matrix4f m, float t) {
        float d = t - WALL;
        if (d < 0 || d > 30) return;
        Vec3 at = stuck();
        for (int i = 0; i < 9; i++) {
            double vx = (FilmFx.hash(i * 2.1) - .5) * .25, vy = .05 + .15 * FilmFx.hash(i * 3.7), vz = -.08 - .12 * FilmFx.hash(i * 6.1);
            double y = at.y + vy * d - .03 * d * d;
            if (y < .05) y = .05;
            double x = at.x + vx * d, z = Math.max(at.z + vz * d, WALL_Z - 2.5);
            float s = .05f + .05f * (float) FilmFx.hash(i * 9.9);
            FilmFx.cube(c, m, (float) x - s, (float) y - s, (float) z - s, (float) x + s, (float) y + s, (float) z + s, lit(BRICK, (float) x, (float) y, (float) z, .9f));
        }
    }

    // ------------------------------------------------------------------ light in the air, shadows, fog, cloud, smoke, rain
    private static void lamps(FilmContext c, float t) {
        // The bulb, its halo in the wet air, the hard cone down to the ground, the pool's highlight on the asphalt.
        Vec3 bulb = LAMP.add(0, -.04, 0);
        float flicker = .93f + .07f * Mth.sin(t * 2.1f) * Mth.sin(t * .37f);
        FilmFx.glow(c, bulb, .5, 0xfff2d6, flicker);
        FilmFx.glow(c, bulb, 2.2, 0xffb560, .35f * flicker);
        cone(c, bulb, 3.6, .14f * flicker, 0xffc27a);
        pool(c, new Vec3(LAMP.x, .015, LAMP.z), 3.0, 0xffb766, .22f * flicker);
        // The cage lamp on the wall, and its fan of light down the brick.
        Vec3 w = WALL_LAMP.add(0, -.15, -.2);
        FilmFx.glow(c, w, .35, 0xffeccc, .9f);
        FilmFx.glow(c, w, 1.6, 0xffb060, .3f);
        cone(c, w, 2.6, .08f, 0xffc27a);
    }
    /** A cone of light from a point straight down to a disc on the ground. */
    private static void cone(FilmContext c, Vec3 top, double radius, float alpha, int rgb) {
        VertexConsumer v = c.buffers().getBuffer(FilmFx.ADD);
        Matrix4f m = c.pose().last().pose();
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float x0 = (float) (top.x + Math.cos(a0) * radius), z0 = (float) (top.z + Math.sin(a0) * radius);
            float x1 = (float) (top.x + Math.cos(a1) * radius), z1 = (float) (top.z + Math.sin(a1) * radius);
            v.vertex(m, (float) top.x, (float) top.y, (float) top.z).color(r, g, b, alpha).endVertex();
            v.vertex(m, (float) top.x, (float) top.y, (float) top.z).color(r, g, b, alpha).endVertex();
            v.vertex(m, x1, .02f, z1).color(r, g, b, 0).endVertex();
            v.vertex(m, x0, .02f, z0).color(r, g, b, 0).endVertex();
        }
    }
    /** A flat disc of light on the ground, bright in its middle. */
    private static void pool(FilmContext c, Vec3 centre, double radius, int rgb, float alpha) {
        VertexConsumer v = c.buffers().getBuffer(FilmFx.ADD);
        Matrix4f m = c.pose().last().pose();
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float y = (float) centre.y;
            v.vertex(m, (float) centre.x, y, (float) centre.z).color(r, g, b, alpha).endVertex();
            v.vertex(m, (float) centre.x, y, (float) centre.z).color(r, g, b, alpha).endVertex();
            v.vertex(m, (float) (centre.x + Math.cos(a0) * radius), y, (float) (centre.z + Math.sin(a0) * radius)).color(r, g, b, 0).endVertex();
            v.vertex(m, (float) (centre.x + Math.cos(a1) * radius), y, (float) (centre.z + Math.sin(a1) * radius)).color(r, g, b, 0).endVertex();
        }
    }
    private static void shadows(FilmContext c, float t) {
        Vec3 feet = KnightTarget.feet(t);
        if (feet.y < 1.5 && t < YANK + 3) FilmFx.shadow(c, new Vec3(feet.x, 0, feet.z), .7, .55f);
        KnightPath.Act a = KnightPath.batman(t, c.time());
        if (a.shown && a.at.y < .3) FilmFx.shadow(c, new Vec3(a.at.x, 0, a.at.z), .8, .6f);
    }
    private static void fog(FilmContext c, float t) {
        // Low fog lying in the yard, drifting; warm where the lamp falls on it.
        for (int i = 0; i < 34; i++) {
            double x = -11 + 22 * FilmFx.hash(i * 1.37) + .02 * t * (1 + FilmFx.hash(i * 2.9)), z = -12 + 25 * FilmFx.hash(i * 3.11);
            x = -11 + ((x + 11) % 22 + 22) % 22;
            double y = .25 + .7 * FilmFx.hash(i * 4.7);
            float l = lamp(x, y, z);
            int col = rgb(.13f + .7f * l, .15f + .5f * l, .2f + .3f * l);
            FilmFx.puff(c, new Vec3(x, y, z), 2.2 + 1.6 * FilmFx.hash(i * 5.3), col, .16f + .1f * l);
        }
    }
    private static void clouds(FilmContext c, float t) {
        if (c.camera().y < 25) return;
        // Wisps of cloud around them up there; below, a layer lit orange from the streets.
        Vec3 centre = KnightTarget.centre(Mth.clamp(t, 250, DROP));
        for (int i = 0; i < 26; i++) {
            double a = FilmFx.hash(i * 2.3) * Math.PI * 2, r = 10 + 34 * FilmFx.hash(i * 5.9);
            double drift = t * .04 * (1 + FilmFx.hash(i));
            Vec3 at = centre.add(Math.cos(a) * r + drift, -6 + 12 * FilmFx.hash(i * 7.1), Math.sin(a) * r);
            FilmFx.puff(c, at, 6 + 6 * FilmFx.hash(i * 3.3), 0x1c2029, .2f);
        }
        for (int i = 0; i < 16; i++) {
            double a = FilmFx.hash(i * 8.3) * Math.PI * 2, r = 6 + 40 * FilmFx.hash(i * 1.9);
            Vec3 at = new Vec3(centre.x + Math.cos(a) * r, 30 + 4 * FilmFx.hash(i * 2.7), centre.z + Math.sin(a) * r);
            FilmFx.puff(c, at, 10 + 8 * FilmFx.hash(i * 4.1), 0x3a2618, .16f);
        }
    }
    /** The target under the Batwing's guns and after: smoke streaming off them, a few embers over the body. */
    private static void smoke(FilmContext c, float t) {
        if (t < SCAN || t > WALL + 30) return;
        for (int j = 0; j < 26; j++) {
            float age = j * 1.4f, s = t - age;
            if (s < SCAN) break;
            if (s > HIT + 6) continue;
            Vec3 at = KnightTarget.centre(s).add((FilmFx.hash(j * 3 + Math.floor(s)) - .5) * .4, (FilmFx.hash(j * 7 + 1) - .5) * .4, 0);
            float fade = 1 - j / 26f;
            float l = lamp(at.x, at.y, at.z);
            FilmFx.puff(c, at, .5 + age * .07, rgb(.16f + .6f * l, .17f + .45f * l, .2f + .3f * l), .5f * fade);
        }
        if (t < HIT) for (int i = 0; i < 5; i++) {
            if (FilmFx.hash(Math.floor(t) * 13 + i) < .5) continue;
            Vec3 a = KnightTarget.point(t, (FilmFx.hash(i * 2 + Math.floor(t)) - .5) * .6, (FilmFx.hash(i * 5 + Math.floor(t)) - .5) * 1.6, .15);
            Vec3 b = a.add((FilmFx.hash(i * 9 + Math.floor(t)) - .5) * .5, (FilmFx.hash(i * 11 + Math.floor(t)) - .5) * .5, (FilmFx.hash(i * 3.3 + Math.floor(t)) - .5) * .5);
            FilmFx.streak(c, a, b, .02, 0xffb060, .9f, .3f, true);
        }
    }
    private static void eyesAndSignal(FilmContext c, float t) {
        KnightPath.Act a = KnightPath.batman(t, c.time());
        if (!a.shown) return;
        // High in the sky: a cold haze of moonlight in the cloud right behind him, so his black shape stands out.
        float sky = window(t, BLAST + 6, DROP + 4, 6);
        if (sky > 0) {
            Vec3 m = a.middle(), away = m.subtract(c.camera()).normalize();
            FilmFx.glow(c, m.add(away.scale(1.4)), 2.6 + 1.2 * a.spread, 0x5a72a8, .24f * sky);
            FilmFx.glow(c, m.add(away.scale(.8)), 1.3, 0x9fb4e0, .14f * sky);
        }
        // In the dark his eyes are what you see.
        if (eyes != null) FilmFx.glow(c, eyes, .32, 0xdcecff, (.3f + .25f * (1 - a.light)) * (1 - .5f * a.dark));
        if (a.signal > 0 && handLeft != null) {
            FilmFx.glow(c, handLeft, .12, 0x9fe8ff, a.signal);
            FilmFx.glow(c, handLeft, .45, 0x5fbfff, .35f * a.signal);
        }
        float s = t - SIGNAL;
        if (s >= 0 && s < 12 && handLeft != null) FilmFx.ring(c, handLeft, .2 + 1.6 * s / 12, .05, 0x8fd8ff, .6f * (1 - s / 12), true);
    }
    /** Rain round the camera: cold streaks in the dark, bright inside the lamp's cone; splashes in its pool. */
    private static void rain(FilmContext c, float t) {
        Vec3 cam = c.camera();
        float box = 13, height = 16, speed = 1.7f;
        double windX = .16, windZ = .08;
        for (int i = 0; i < 440; i++) {
            double x = wrap(FilmFx.hash(i * 1.31) * box * 2 + t * windX * speed, cam.x - box, box * 2);
            double z = wrap(FilmFx.hash(i * 2.77) * box * 2 + t * windZ * speed, cam.z - box, box * 2);
            double y = wrap(FilmFx.hash(i * 3.97) * height - t * speed, cam.y - height * .5, height);
            if (y < 0) continue;
            Vec3 head = new Vec3(x, y, z), tail = head.add(-windX * .5, .55, -windZ * .5);
            float l = lamp(x, y, z) + .6f * wallLamp(x, y, z);
            int col = l > .05f ? rgb(.55f + .45f * l, .55f + .3f * l, .6f) : 0x8290a6;
            FilmFx.streak(c, tail, head, .012, col, 0, .09f + .5f * Math.min(1, l), true);
        }
        if (cam.y > 30) return;
        for (int i = 0; i < 36; i++) {
            double a = FilmFx.hash(i * 6.1) * Math.PI * 2, r = 3.2 * Math.sqrt(FilmFx.hash(i * 2.2));
            float ph = (float) ((t * .2 + FilmFx.hash(i * 9.4)) % 1);
            Vec3 at = new Vec3(LAMP.x + Math.cos(a) * r, .03, LAMP.z + Math.sin(a) * r);
            FilmFx.ring(c, at, .02 + .14 * ph, .015, 0xffd9a8, .35f * (1 - ph) * lamp(at.x, 0, at.z) * 2, true);
        }
    }
    private static double wrap(double v, double start, double size) {
        double d = (v - start) % size;
        if (d < 0) d += size;
        return start + d;
    }
}
