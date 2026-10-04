package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.client.render.film.FilmBackdrop;
import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.thor.ThorBolts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import java.util.List;

import static com.FIRNI.superheromod.client.render.magneto.MagneticPath.*;
import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.*;

/**
 * MAGNETIC EXECUTION's stage: a dead world under a crimson storm (the backdrop draws the sky, the ruins on the horizon,
 * the soaked ground and the rain far off), and here, close: wreckage and bones strewn over the ground, the mound of
 * skulls the X stands on, Magneto, the metal circling him, the target, the two pillars with their glowing edges, the
 * crush and the ball, sparks, splashes, dust, the shock ring, lightning coming down close, and the rain round the
 * camera. Everything metal is lit by the storm (MetalMesh.SHADE: a dim red, white in the flashes).
 */
public final class MagneticStage {
    private MagneticStage() {}

    private static final int FULL = LightTexture.FULL_BRIGHT;
    private static final float BODY_SCALE = .9375f;
    private static final int GLOW_CORE = 0xffd65a, GLOW_HALO = 0xff6a14, SPARK = 0xffe2a0, BOLT_CORE = 0xfff4ff, BOLT_HALO = 0xff5f86;
    private static FilmCast cast;

    public static FilmBackdrop.Params backdrop(float t) {
        Strike s = strike(t);
        float flash = flash(t);
        if (s == null) return FilmBackdrop.Params.wasteland(new Vec3(60, 0, 120), flash, 1, 0, 1, .9f);
        float bolt = s.near() ? 0 : flicker(t - s.time()) * (t - s.time() < 6 ? 1 : 0);
        return FilmBackdrop.Params.wasteland(s.at(), flash, s.seed() + (int) ((t - s.time()) / 2) * .3f, bolt, 1, .9f);
    }

    public static void render(FilmContext c, Entity target, float t) {
        float flash = flash(t);
        float[] shade = MetalMesh.SHADE;
        float r = .74f + .75f * flash, g = .46f + .78f * flash, b = .46f + .86f * flash;
        shade[0] = Math.min(1.3f, r); shade[1] = Math.min(1.25f, g); shade[2] = Math.min(1.3f, b);
        try {
            FilmFx.floor(c, c.camera().x, 0, c.camera().z, 400);
            PoseStack p = c.pose();
            VertexConsumer v = MetalMesh.buffer(c.buffers());
            wreckage(p, v);
            pillars(p, v, t);
            metal(p, v, t);
            debris(p, v, t);
            drawMagneto(c, t);
            if (target != null && targetShown(t)) {
                if (cast == null || cast.entity() != target) cast = FilmCast.of(target);
                if (cast != null) cast.draw(c, targetFeet(t), 180, targetPose(t), 1, rgb(shade[0] * .95f, shade[1] * .95f, shade[2] * .95f));
            }
        } finally {
            shade[0] = shade[1] = shade[2] = 1;
        }
        c.buffers().endBatch();
        // Light, matter in the air and the bones, batched so mixing them never flushes.
        var fxBuffers = FilmFx.batched();
        FilmContext fx = new FilmContext(c.pose(), fxBuffers, c.camera(), c.viewRight(), c.viewUp(), c.time(), c.local(), c.partial());
        bones(fx, flash);
        glowEdges(fx, t);
        impacts(fx, t);
        lightning(fx, t);
        drawBall(fx, t);
        footsteps(fx, t);
        rain(fx, t, flash);
        fxBuffers.endBatch();
    }

    private static int rgb(float r, float g, float b) {
        return (int) (Mth.clamp(r, 0, 1) * 255) << 16 | (int) (Mth.clamp(g, 0, 1) * 255) << 8 | (int) (Mth.clamp(b, 0, 1) * 255);
    }

    // ------------------------------------------------------------------ Magneto
    private static void drawMagneto(FilmContext c, float t) {
        Vec3 at = magneto(t);
        float time = c.time();
        Pose pose = pose(t, time);
        float[] look = look(t);
        PoseStack p = c.pose();
        p.pushPose();
        p.translate(at.x, at.y, at.z);
        p.mulPose(Axis.YP.rotation(Mth.PI - yaw(t)));
        p.scale(-BODY_SCALE, -BODY_SCALE, BODY_SCALE);
        p.translate(0, -1.501, 0);
        MagnetoBody.GLOW[0] = effort(t);
        MagnetoBody.GLOW[1] = Math.max(window(t, ULT_PULL, ULT_SLAM + 4, 6), .6f * window(t, ULT_PIN - 2, ULT_PIN + 14, 4)) * (t > ULT_PIN ? 0 : 1);
        MagnetoBody.glowTime = time;
        try {
            MagnetoBody.draw(p, c.buffers(), FULL, pose, look[0], look[1], time, cloth(t));
        } finally {
            MagnetoBody.GLOW[0] = MagnetoBody.GLOW[1] = 0;
            p.popPose();
        }
    }

    // ------------------------------------------------------------------ the metal round him and on the target
    private static void metal(PoseStack p, VertexConsumer v, float t) {
        if (t > ULT_STAGE_END) return;
        for (int i = 0; i < PIECES; i++) {
            Vec3 at = i < LAUNCHED ? launched(i, t) : orbit(i, t);
            if (i < LAUNCHED && t > ULT_HURL + HURL_TIME) continue;
            float spin = tumble(i, t);
            boolean locked = i < LAUNCHED && t > arrival(i);
            p.pushPose();
            p.translate(at.x, at.y, at.z);
            p.mulPose(Axis.YP.rotation(spin * .7f));
            p.mulPose(Axis.XP.rotation(spin));
            p.mulPose(Axis.ZP.rotation(spin * .4f + i));
            if (locked) {
                // Clamped round a limb: a short bent bar and a plate.
                MetalMesh.rod(p, v, FULL, .55f, i * 3 + 1);
                MetalMesh.scrap(p, v, FULL, i, .3f);
            } else if (i % 3 == 0) MetalMesh.rod(p, v, FULL, 1.1f + 1.1f * MetalMesh.hash(i * 7), i);
            else MetalMesh.scrap(p, v, FULL, i, .34f + .3f * MetalMesh.hash(i * 5));
            p.popPose();
        }
        // The ball: the crumpled pillars are drawn by pillars(); a few torn plates fill its gaps.
        float in = k(t, ULT_CRUSH + 10, ULT_BALL);
        if (in > 0 && t < ULT_HURL + HURL_TIME) {
            Vec3 c = ball(t);
            for (int i = 0; i < 10; i++) {
                float a = hash(i * 3) * 6.28f, e = (hash(i * 7) - .5f) * 2.6f;
                p.pushPose();
                p.translate(c.x + Math.cos(a) * Math.cos(e) * .6, c.y + Math.sin(e) * .6, c.z + Math.sin(a) * Math.cos(e) * .6);
                p.mulPose(Axis.YP.rotation(a + t * .02f)); p.mulPose(Axis.XP.rotation(e + i));
                p.scale(in, in, in);
                MetalMesh.fragment(p, v, FULL, i + 40, .9f);
                p.popPose();
            }
        }
    }

    // ------------------------------------------------------------------ the pillars
    private static void pillars(PoseStack p, VertexConsumer v, float t) {
        for (int pi = 0; pi < 2; pi++) for (int k = 0; k < SEGMENTS; k++) {
            Segment s = segment(pi, k, t);
            if (s == null) continue;
            if (t > ULT_HURL + HURL_TIME - 2) continue;
            p.pushPose();
            p.translate(s.centre().x, s.centre().y, s.centre().z);
            p.mulPose(frame(s));
            if (s.crumple() != 0) { p.mulPose(Axis.XP.rotation(s.crumple())); p.mulPose(Axis.ZP.rotation(s.crumple() * .6f)); }
            p.scale(s.scale(), s.scale(), s.scale());
            segmentMesh(p, v, pi * 31 + k, k == 0 ? -1 : k == SEGMENTS - 1 ? 1 : 0);
            p.popPose();
        }
    }
    private static Quaternionf frame(Segment s) {
        Vec3 x = s.side(), y = s.axis(), z = x.cross(y);
        Matrix3f m = new Matrix3f((float) x.x, (float) x.y, (float) x.z, (float) y.x, (float) y.y, (float) y.z, (float) z.x, (float) z.y, (float) z.z);
        return new Quaternionf().setFromNormalized(m);
    }
    /** One length of a pillar along y, centred: a heavy riveted iron beam, plated, banded, rusted; end = which end is torn off (-1, 1, 0 none). */
    private static void segmentMesh(PoseStack p, VertexConsumer v, int seed, int end) {
        float h = THICK / 2, l = HALF / SEGMENTS + .04f;
        float[] dark = tint(MetalMesh.IRON_DARK, seed), plate = tint(MetalMesh.IRON, seed + 3);
        MetalMesh.box(p, v, FULL, -h, -l, -h, h, l, h, dark);
        // Plates proud of each face with a seam across the middle.
        for (int f = 0; f < 4; f++) {
            p.pushPose();
            p.mulPose(Axis.YP.rotation(f * Mth.HALF_PI));
            MetalMesh.box(p, v, FULL, -h + .08f, -l + .12f, -h - .04f, h - .08f, -.04f, -h + .02f, plate);
            MetalMesh.box(p, v, FULL, -h + .08f, .04f, -h - .04f, h - .08f, l - .12f, -h + .02f, tint(MetalMesh.IRON, seed + f + 5));
            if (hash(seed * 7 + f) < .45f) MetalMesh.box(p, v, FULL, -.1f + .4f * (hash(seed + f) - .5f), -l * .7f, -h - .05f, .02f + .4f * (hash(seed + f) - .5f), l * .5f, -h - .03f, MetalMesh.RUST);
            // Rivets down the plate edges.
            for (int i = 0; i < 4; i++) {
                float y = -l + .3f + i * (2 * l - .6f) / 3;
                MetalMesh.box(p, v, FULL, -h + .12f, y - .04f, -h - .07f, -h + .2f, y + .04f, -h - .03f, MetalMesh.IRON_LIGHT);
                MetalMesh.box(p, v, FULL, h - .2f, y - .04f, -h - .07f, h - .12f, y + .04f, -h - .03f, MetalMesh.IRON_LIGHT);
            }
            p.popPose();
        }
        // Bands round it and bright edges along its corners.
        MetalMesh.box(p, v, FULL, -h - .06f, l - .18f, -h - .06f, h + .06f, l - .04f, h + .06f, MetalMesh.IRON);
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? -h - .03f : h - .05f, z = i < 2 ? -h - .03f : h - .05f;
            MetalMesh.box(p, v, FULL, x, -l, z, x + .08f, l, z + .08f, MetalMesh.IRON_LIGHT);
        }
        if (end != 0) {
            // The torn end: jagged plates and bent bars sticking out.
            float y0 = end * l;
            for (int i = 0; i < 5; i++) {
                float x = (hash(seed + i * 3) - .5f) * THICK * .8f, z = (hash(seed + i * 5) - .5f) * THICK * .8f, len = .15f + .35f * hash(seed + i);
                float lo = end > 0 ? y0 : y0 - len, hi = end > 0 ? y0 + len : y0;
                MetalMesh.box(p, v, FULL, x - .12f, lo, z - .12f, x + .12f, hi, z + .12f, i % 2 == 0 ? MetalMesh.RUST_DARK : MetalMesh.IRON_DARK);
            }
        }
    }
    private static float[] tint(float[] c, int seed) { float k = .88f + .2f * hash(seed); return new float[]{c[0] * k, c[1] * k, c[2] * k}; }

    /** The glowing edges along the pillars (after the reference: hot yellow lines on the black iron), and their smear on the wet ground. */
    private static void glowEdges(FilmContext fx, float t) {
        float heat = heat(t);
        if (heat <= .01f) return;
        float pulse = .85f + .15f * Mth.sin(t * .4f);
        Vec3 cam = fx.camera();
        for (int pi = 0; pi < 2; pi++) for (int k = 0; k < SEGMENTS; k++) {
            Segment s = segment(pi, k, t);
            if (s == null) continue;
            float h = THICK / 2 * s.scale() + .02f, l = (HALF / SEGMENTS) * s.scale();
            Vec3 x = s.side(), y = s.axis(), z = x.cross(y);
            for (int e = 0; e < 4; e++) {
                Vec3 off = x.scale(e % 2 == 0 ? -h : h).add(z.scale(e < 2 ? -h : h));
                Vec3 a = s.centre().add(off).subtract(y.scale(l)), b2 = s.centre().add(off).add(y.scale(l));
                FilmFx.streak(fx, a, b2, .07, GLOW_CORE, heat * pulse, heat * pulse, true);
                FilmFx.streak(fx, a, b2, .32, GLOW_HALO, heat * .3f, heat * .3f, true);
            }
            // Falling: a dark wake up the line it came down.
            if (t < ULT_SLAM && slide(pi, t) > .5f) {
                Vec3 tail = s.centre().add(AXIS[pi].scale(-Math.min(14, slide(pi, t) * .6f + 3)));
                FilmFx.streak(fx, tail, s.centre(), .7, 0x1a0806, 0, .45f, false);
                FilmFx.streak(fx, tail, s.centre(), .25, GLOW_HALO, 0, .35f, true);
            }
            // On the soaked ground below: the glow's reflection, smeared toward the camera.
            if (t >= ULT_SLAM && s.centre().y < 12) {
                Vec3 base = new Vec3(s.centre().x, .02, s.centre().z);
                Vec3 to = new Vec3(cam.x - base.x, 0, cam.z - base.z);
                if (to.lengthSqr() > 1e-3) {
                    Vec3 end = base.add(to.normalize().scale(Math.min(7, s.centre().y * .9 + 1)));
                    FilmFx.streak(fx, base, end, .45, GLOW_HALO, heat * .22f, 0, true);
                }
            }
        }
    }

    // ------------------------------------------------------------------ what flies when things hit
    private record Burst(float time, Vec3 at, int seed, int count, float speed, float up) {}
    private static final Burst[] SPARKS = {
            new Burst(ULT_SLAM, CROSS, 1, 90, .55f, .1f), new Burst(ULT_SLAM, foot(0).multiply(1, 0, 1), 2, 50, .5f, .5f),
            new Burst(ULT_SLAM, foot(1).multiply(1, 0, 1), 3, 50, .5f, .5f), new Burst(ULT_PIN + 9, PIN.add(0, 1.1, .4), 4, 40, .35f, .2f),
            new Burst(ULT_CRUSH + 6, PIN.add(0, 1.1, 0), 5, 50, .4f, .2f), new Burst(ULT_CRUSH + 16, PIN.add(0, 1.1, 0), 6, 40, .35f, .2f),
            new Burst(ULT_BALL, PIN.add(0, 1.1, 0), 7, 70, .45f, .2f), new Burst(ULT_HURL, PIN.add(0, 1.4, 0), 8, 40, .4f, .4f)};
    private static Vec3 dir(int seed, float up) {
        Vec3 d = new Vec3(hash(seed * 3 + 1) - .5, hash(seed * 5 + 2) - .5 + up, hash(seed * 7 + 3) - .5);
        return d.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : d.normalize();
    }
    private static Vec3 flight(Vec3 at, Vec3 vel, float tau, float gravity) {
        Vec3 p = at.add(vel.scale(tau)).add(0, -gravity * tau * tau / 2, 0);
        return p.y < .02 ? new Vec3(p.x, .02, p.z) : p;
    }
    private static void impacts(FilmContext fx, float t) {
        for (Burst b : SPARKS) {
            float tau = t - b.time();
            if (tau < 0 || tau > 30) continue;
            for (int i = 0; i < b.count(); i++) {
                int seed = b.seed() * 977 + i;
                float life = 10 + 16 * hash(seed);
                if (tau > life) continue;
                Vec3 vel = dir(seed, b.up()).scale(b.speed() * (.35f + .65f * hash(seed * 11)));
                Vec3 head = flight(b.at(), vel, tau, .035f), tail = flight(b.at(), vel, Math.max(0, tau - 1.3f), .035f);
                float a = 1 - tau / life;
                FilmFx.streak(fx, tail, head, .035, SPARK, 0, a, true);
                if (i % 6 == 0) FilmFx.glow(fx, head, .18, GLOW_HALO, a * .5f);
            }
            FilmFx.glow(fx, b.at(), 2.5 * (1 + tau * .05), GLOW_HALO, Math.max(0, .6f - tau * .06f));
            FilmFx.glow(fx, b.at(), 1.0, 0xfff0d0, Math.max(0, .9f - tau * .2f));
        }
        float slam = t - ULT_SLAM;
        if (slam >= 0 && slam < 70) {
            // The shock ring over the ground, the water thrown up where the pillars bit in, the dust rolling out.
            Vec3 ground = new Vec3(CROSS.x, .04, CROSS.z);
            FilmFx.ring(fx, ground, 1 + slam * .9, .7 + slam * .03, 0xc8a8a0, Math.max(0, .55f - slam * .025f), false);
            FilmFx.ring(fx, ground, 1 + slam * .9, .25, GLOW_HALO, Math.max(0, .5f - slam * .04f), true);
            for (int f = 0; f < 2; f++) {
                Vec3 at = foot(f).multiply(1, 0, 1).add(0, .05, 0);
                for (int i = 0; i < 28; i++) {
                    int seed = 5000 + f * 300 + i;
                    float life = 14 + 10 * hash(seed);
                    if (slam < life) {
                        Vec3 vel = dir(seed, 1.4f).scale(.25f + .3f * hash(seed * 3));
                        Vec3 head = flight(at, vel, slam, .05f), tail = flight(at, vel, Math.max(0, slam - 1.5f), .05f);
                        FilmFx.streak(fx, tail, head, .05, 0xe0cfd8, 0, (1 - slam / life) * .6f, false);
                    }
                }
                for (int i = 0; i < 9; i++) {
                    int seed = 7000 + f * 100 + i;
                    float a = hash(seed) * 6.28f, out = (1.2f + 4 * hash(seed * 3)) * (1 - (float) Math.exp(-slam / 14f));
                    Vec3 puff = at.add(Math.cos(a) * out, .4 + .9 * hash(seed * 5) + slam * .015, Math.sin(a) * out);
                    FilmFx.puff(fx, puff, 1.0 + slam * .045, i % 2 == 0 ? 0x3b2420 : 0x5a3a33, Math.max(0, .55f * (1 - slam / 70f)));
                }
            }
        }
        float pin = t - ULT_PIN - 9;
        if (pin >= 0 && pin < 40)
            for (int i = 0; i < 6; i++) {
                float a = hash(800 + i) * 6.28f;
                FilmFx.puff(fx, PIN.add(Math.cos(a) * (.6 + pin * .03), 1.1 + Math.sin(a) * (.6 + pin * .03), .45), .7 + pin * .03, 0x4a302a, .4f * (1 - pin / 40));
            }
        // The clamps landing: a spark where each one bites.
        for (int j = 0; j < LAUNCHED; j += 2) {
            float d = t - arrival(j);
            if (d < 0 || d > 6 || t > ULT_CRUSH) continue;
            FilmFx.glow(fx, anchor(anchorOf(j), t), .5, SPARK, (1 - d / 6) * .8f);
        }
        // Grinding while it crushes: sparks spitting from the bending iron.
        if (t > ULT_CRUSH && t < ULT_BALL + 4) {
            for (int i = 0; i < 18; i++) {
                int seed = (int) t * 31 + i;
                Segment s = segment(i % 2, (int) (hash(seed) * SEGMENTS), t);
                if (s == null) continue;
                Vec3 vel = dir(seed, .3f).scale(.3f);
                float tau = (t * 3 + i) % 4 / 3;
                FilmFx.streak(fx, flight(s.centre(), vel, Math.max(0, tau - .6f), .03f), flight(s.centre(), vel, tau, .03f), .03, SPARK, 0, 1 - tau, true);
            }
        }
    }
    /** Pieces torn off at the slam: tumbling out and lying where they fall. */
    private static void debris(PoseStack p, VertexConsumer v, float t) {
        float slam = t - ULT_SLAM;
        if (slam < 0 || t > ULT_STAGE_END) return;
        for (int i = 0; i < 22; i++) {
            int seed = 300 + i;
            Vec3 from = i < 8 ? CROSS : foot(i % 2).multiply(1, 0, 1).add(0, .3, 0);
            Vec3 vel = dir(seed, i < 8 ? .2f : .8f).scale(.3f + .3f * hash(seed * 3));
            float land = (float) ((vel.y + Math.sqrt(vel.y * vel.y + 2 * .04 * Math.max(0, from.y - .05))) / .04);
            float tau = Math.min(slam, land);
            Vec3 at = from.add(vel.scale(tau)).add(0, -.02 * tau * tau, 0);
            p.pushPose();
            p.translate(at.x, Math.max(.05, at.y), at.z);
            p.mulPose(Axis.XP.rotation(tau * .3f + i)); p.mulPose(Axis.ZP.rotation(tau * .2f + i * 2));
            MetalMesh.fragment(p, v, FULL, seed, .5f + .4f * hash(seed));
            p.popPose();
        }
    }

    // ------------------------------------------------------------------ the storm, close
    private static void lightning(FilmContext fx, float t) {
        for (Strike s : STRIKES) {
            float tau = t - s.time();
            if (!s.near() || tau < 0 || tau > 9) continue;
            float a = flicker(tau);
            Vec3 end = s.time() == ULT_SLAM ? CROSS.add(0, HALF * .7, 0) : s.at();
            Vec3 top = end.add(14 * (hash(s.seed()) - .5), 70, 18 + 10 * hash(s.seed() * 3));
            List<ThorBolts.Seg> segs = ThorBolts.bolt(top, end, s.seed() * 131L + (long) (tau / 1.5f), .09, .5, 2);
            for (ThorBolts.Seg g : segs) {
                FilmFx.streak(fx, g.a(), g.b(), .16 * g.width(), BOLT_CORE, a, a, true);
                FilmFx.streak(fx, g.a(), g.b(), .9 * g.width(), BOLT_HALO, a * .35f, a * .35f, true);
            }
            FilmFx.glow(fx, end, 5, BOLT_HALO, a * .7f);
            FilmFx.glow(fx, end, 1.6, BOLT_CORE, a);
        }
    }
    private static double wrap(double a, double size) { return ((a % size) + size) % size; }
    /** Rain round the camera: slanted streaks falling fast, pale red, white in a flash; rings where they hit the water. */
    private static void rain(FilmContext fx, float t, float flash) {
        Vec3 cam = fx.camera();
        int col = flash > .3f ? 0xf0e8f4 : 0xc9928e;
        float alpha = .22f + .3f * Math.min(1, flash);
        double box = 18, height = 9, fall = .95, wind = .22;
        for (int i = 0; i < 260; i++) {
            // Each drop has a fixed place in the world; the box of rain travels with the camera.
            double x = cam.x - box / 2 + wrap(hash(i * 3 + 1) * 97 + t * fall * wind - cam.x + box / 2, box);
            double z = cam.z - box / 2 + wrap(hash(i * 5 + 2) * 89 - cam.z + box / 2, box);
            double y = cam.y - height * .45 + wrap(hash(i * 7 + 3) * height - t * fall, height);
            if (y < .05) continue;
            Vec3 head = new Vec3(x, y, z), tail = head.add(-wind * .9, .9, 0);
            FilmFx.streak(fx, tail, head, .012, col, 0, alpha, false);
        }
        // Rings on the water near the camera.
        for (int i = 0; i < 26; i++) {
            float phase = t * .12f + hash(i * 13);
            int cycle = (int) Math.floor(phase);
            float age = phase - cycle;
            int seed = i * 101 + cycle * 7;
            double x = cam.x + (hash(seed) - .5) * 14, z = cam.z + (hash(seed * 3) - .5) * 14;
            FilmFx.ring(fx, new Vec3(x, .03, z), .05 + age * .35, .03, 0xd8b4b4, (1 - age) * .35f, false);
        }
    }
    /** His steps through the water: a ripple and a little splash at each footfall. */
    private static void footsteps(FilmContext fx, float t) {
        if (t < ULT_TURN) return;
        float turn = yaw(t) * 1.6f, phase = walked(t) * 2.66f + turn;
        int step = (int) Math.floor(phase / Mth.PI);
        for (int k = 0; k < 4; k++) {
            int s = step - k;
            if (s < 0) break;
            float since = (phase - s * Mth.PI) / Mth.PI * 18;
            // Where he was when that foot came down.
            double d = Math.max(0, (s * Mth.PI - Mth.PI * 1.6) / 2.66);
            double side = (s % 2 == 0 ? .14 : -.14) * (t > ULT_WALK ? -1 : 1);
            Vec3 foot = new Vec3(side, .03, -d);
            FilmFx.ring(fx, foot, .1 + since * .05, .04, 0xe8c4c4, Math.max(0, .45f - since * .012f), false);
            if (since < 5) FilmFx.puff(fx, foot.add(0, .08, 0), .25, 0xc8a0a0, .3f * (1 - since / 5));
        }
    }
    /** The ball: hot, then thrown with a trail of sparks into the storm, and a distant burst where it comes down. */
    private static void drawBall(FilmContext fx, float t) {
        if (t < ULT_BALL) return;
        if (t < ULT_HURL + HURL_TIME) {
            Vec3 c = ball(t);
            float hot = t < ULT_HURL ? 1 : 1 - (t - ULT_HURL) / HURL_TIME * .6f;
            FilmFx.glow(fx, c, 2.4, GLOW_HALO, .35f * hot);
            if (ballFlying(t)) {
                Vec3 back = ball(Math.max(ULT_HURL, t - 4));
                FilmFx.streak(fx, back, c, .9, 0x241310, 0, .5f, false);
                FilmFx.streak(fx, back, c, .25, GLOW_HALO, 0, .7f * hot, true);
                for (int i = 0; i < 10; i++) {
                    float d = i * .8f;
                    Vec3 e = ball(Math.max(ULT_HURL, t - d)).add((hash(i * 7 + (int) t) - .5) * .8, (hash(i * 3 + (int) t) - .5) * .8, (hash(i * 5 + (int) t) - .5) * .8);
                    FilmFx.glow(fx, e, .2, SPARK, (1 - i / 10f) * .8f);
                }
            }
        }
        float far = t - (ULT_HURL + HURL_TIME);
        if (far >= 0 && far < 40) {
            float a = (float) Math.exp(-far / 10);
            FilmFx.glow(fx, FAR, 26 + far * .8, GLOW_HALO, .7f * a);
            FilmFx.glow(fx, FAR, 9, 0xfff0d0, a);
        }
    }

    // ------------------------------------------------------------------ the ground: wreckage, bones, the mound
    private record Prop(float x, float y, float z, float yaw, float pitch, float roll, int kind, int seed, float size) {}
    private static final Prop[] WRECKAGE = wreckage(), SKULLS = skulls();
    private static boolean clear(double x, double z) {
        // Keep his walk, the target's place and the mound's foot free.
        return !(Math.abs(x) < 2.6 && z > -14 && z < 10) && Math.hypot(x, z - CROSS.z) > 7.5;
    }
    private static Prop[] wreckage() {
        Prop[] out = new Prop[40];
        int n = 0, i = 0;
        while (n < out.length) {
            i++;
            float a = hash(i * 3) * 6.28f, d = 5 + 30 * hash(i * 5);
            float x = Mth.cos(a) * d, z = Mth.sin(a) * d + 4;
            if (!clear(x, z)) continue;
            int kind = n < 18 ? 0 : n < 32 ? 1 : 2;
            out[n++] = new Prop(x, kind == 0 ? .2f : 0, z, hash(i * 7) * 6.28f, kind == 0 ? .3f + .9f * hash(i * 11) : 0, (hash(i * 13) - .5f) * .6f, kind, i, .6f + .8f * hash(i * 17));
        }
        return out;
    }
    private static Prop[] skulls() {
        Prop[] out = new Prop[230];
        for (int i = 0; i < out.length; i++) {
            if (i < 170) {
                // The mound under the X: a cone of skulls heaped over rubble.
                float a = hash(i * 3 + 7) * 6.28f, rr = (float) Math.sqrt(hash(i * 5 + 9)) * 6.6f;
                float y = 2.1f * (1 - rr / 6.6f) + .25f * hash(i * 11) - .1f;
                out[i] = new Prop(Mth.cos(a) * rr, Math.max(0, y), (float) CROSS.z + Mth.sin(a) * rr * .8f, hash(i * 13) * 6.28f, (hash(i * 17) - .5f) * 1.1f, (hash(i * 19) - .5f) * .9f, 0, i, .42f + .2f * hash(i * 23));
            } else {
                float a = hash(i * 3 + 1) * 6.28f, d = 3 + 26 * hash(i * 5 + 3);
                float x = Mth.cos(a) * d, z = Mth.sin(a) * d + 3;
                if (!clear(x, z)) { x += 5 * Math.signum(x == 0 ? 1 : x); }
                out[i] = new Prop(x, 0, z, hash(i * 13) * 6.28f, (hash(i * 17) - .5f) * .5f, (hash(i * 19) - .5f) * .6f, 1, i, .4f + .15f * hash(i * 23));
            }
        }
        return out;
    }
    private static void wreckage(PoseStack p, VertexConsumer v) {
        for (Prop w : WRECKAGE) {
            p.pushPose();
            p.translate(w.x(), w.y(), w.z());
            p.mulPose(Axis.YP.rotation(w.yaw()));
            switch (w.kind()) {
                case 0 -> {
                    // A girder or bar driven into the ground at an angle (the floor hides what is buried).
                    p.mulPose(Axis.XP.rotation(w.pitch())); p.mulPose(Axis.ZP.rotation(w.roll()));
                    MetalMesh.rod(p, v, FULL, 2.5f + 3 * w.size(), w.seed());
                }
                case 1 -> {
                    p.mulPose(Axis.ZP.rotation(w.roll() * .3f));
                    MetalMesh.scrap(p, v, FULL, w.seed(), .8f + w.size());
                }
                default -> {
                    // The stump of a torn column.
                    p.translate(0, -.3, 0);
                    p.mulPose(Axis.XP.rotation(w.roll() * .4f));
                    MetalMesh.column(p, v, FULL, 1 + 2.4f * w.size(), w.seed());
                }
            }
            p.popPose();
        }
    }
    private static void bones(FilmContext fx, float flash) {
        float[] k = {Math.min(1.25f, .8f + .7f * flash), Math.min(1.2f, .36f + .8f * flash), Math.min(1.2f, .32f + .85f * flash)};
        // Rubble under the mound so the skulls lie on something.
        PoseStack p = fx.pose();
        for (int i = 0; i < 46; i++) {
            float a = hash(i * 7 + 400) * 6.28f, rr = (float) Math.sqrt(hash(i * 3 + 401)) * 5.8f, s = .9f + .9f * hash(i * 5 + 402);
            float top = 1.9f * (1 - rr / 6.6f);
            p.pushPose();
            p.translate(Mth.cos(a) * rr, 0, CROSS.z + Mth.sin(a) * rr * .8f);
            p.mulPose(Axis.YP.rotation(a));
            FilmFx.cube(fx, p.last().pose(), -s, -.2f, -s * .7f, s, top, s * .7f, rgb(.12f * k[0], .05f * k[1], .045f * k[2]));
            p.popPose();
        }
        int bone = rgb(.62f * k[0], .5f * k[1], .42f * k[2]), shadow = rgb(.05f, .01f, .01f), tooth = rgb(.75f * k[0], .65f * k[1], .55f * k[2]);
        for (Prop s : SKULLS) {
            p.pushPose();
            p.translate(s.x(), s.y(), s.z());
            p.mulPose(Axis.YP.rotation(s.yaw()));
            p.mulPose(Axis.XP.rotation(s.pitch()));
            p.mulPose(Axis.ZP.rotation(s.roll()));
            float z = s.size();
            p.scale(z, z, z);
            var m = p.last().pose();
            int tone = s.seed() % 3 == 0 ? rgb(.52f * k[0], .4f * k[1], .33f * k[2]) : bone;
            FilmFx.cube(fx, m, -.25f, .12f, -.25f, .25f, .56f, .27f, tone);
            FilmFx.cube(fx, m, -.2f, 0, -.31f, .2f, .2f, -.05f, tone);
            FilmFx.cube(fx, m, -.18f, .24f, -.27f, -.04f, .37f, -.24f, shadow);
            FilmFx.cube(fx, m, .04f, .24f, -.27f, .18f, .37f, -.24f, shadow);
            FilmFx.cube(fx, m, -.03f, .15f, -.325f, .03f, .22f, -.3f, shadow);
            FilmFx.cube(fx, m, -.15f, .01f, -.33f, .15f, .07f, -.3f, tooth);
            if (s.kind() == 1 || s.seed() % 2 == 0) FilmFx.cube(fx, m, -.17f, -.06f, -.28f, .17f, .01f, -.04f, tone);
            p.popPose();
        }
    }
}
