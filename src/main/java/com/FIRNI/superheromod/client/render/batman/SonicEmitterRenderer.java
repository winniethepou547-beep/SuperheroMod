package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.FIRNI.superheromod.heroes.batman.SonicEmitterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.FIRNI.superheromod.heroes.batman.SonicEmitterEntity.*;

/**
 * The sonic trap's emitter (Batman v Superman's WayneTech emitters): black and gunmetal, heavy, brutalist. A squat
 * armoured housing on an octagonal foot with four hydraulic outrigger legs; a telescoping mast pushed up by two
 * pistons; a yaw ring with a fork; in the fork the big sonic chamber: an octagonal drum with cooling fins at the back,
 * a stepped bowl of concentric rings (the middle ones turning), radial struts, the emitter core in the centre glowing a
 * faint cold white-blue when active, a rangefinder on top. Lit by the world light (only the lights are full-bright).
 * <p>
 * Its motion is all from the entity's synced clock (SonicEmitterEntity's timeline): it comes up folded (housing first,
 * slowly), the mast drives the chamber up, the chamber unfolds and the legs slam down, it locks with a jolt; the rings
 * spin up; the chamber tracks the target (the entity's damped aim) and kicks back on every pulse; cooling, the light
 * dies and the rings slow; then it folds and sinks back. Broken: it slumps, twitches and shudders until it blows apart.
 * The torn-up soil round its foot (clods of the real ground's colour) is drawn here; the dark broken-earth patch, the
 * light and the effects by BatmanSonicFx. Mesh sizes in BLOCKS, front +Z, origin on the ground in the middle.
 */
public final class SonicEmitterRenderer extends EntityRenderer<SonicEmitterEntity> {
    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 16, 16, 16);
    static final int FULL = 15728880;
    static final float[] BLACK = {.04f, .042f, .046f}, BLACK_HI = {.085f, .088f, .095f}, METAL = {.2f, .21f, .225f},
            METAL_DARK = {.12f, .125f, .135f}, METAL_HI = {.36f, .37f, .4f};
    /** The core light: cold white with a breath of blue-cyan (never neon). */
    static final float[] COLD = {.8f, .93f, 1f};
    /** Pivot of the chamber over the ground folded (mast down) and how far the mast lifts it (to HEAD_Y). */
    static final float PIVOT_FOLDED = 1.05f, MAST_LIFT = (float) HEAD_Y - PIVOT_FOLDED;
    /** How deep the folded device starts below the ground. */
    static final float BURIED = 1.42f;

    public SonicEmitterRenderer(EntityRendererProvider.Context ctx) { super(ctx); shadowRadius = 0; }
    @Override public ResourceLocation getTextureLocation(SonicEmitterEntity e) { return GhostMaterials.TEXTURE; }

    // ------------------------------------------------------------------ the boxes
    static VertexConsumer buffer(MultiBufferSource b) { return b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)); }
    /** A box centred at (x, y, z), size (w, h, d), blocks; colour times k (clamped). */
    static void box(PoseStack p, VertexConsumer v, int light, float x, float y, float z, float w, float h, float d, float[] c, float k) {
        if (w <= 1e-4f || h <= 1e-4f || d <= 1e-4f) return;
        p.pushPose();
        p.translate(x - w / 2, y - h / 2, z - d / 2);
        p.scale(w, h, d);
        UNIT.render(p, v, light, OverlayTexture.NO_OVERLAY, Math.min(1, c[0] * k), Math.min(1, c[1] * k), Math.min(1, c[2] * k), 1);
        p.popPose();
    }
    static void box(PoseStack p, VertexConsumer v, int light, float x, float y, float z, float w, float h, float d, float[] c) { box(p, v, light, x, y, z, w, h, d, c, 1); }
    /** A box turned about Y (radians) round its centre. */
    static void ybox(PoseStack p, VertexConsumer v, int light, float x, float y, float z, float yaw, float w, float h, float d, float[] c) {
        p.pushPose(); p.translate(x, y, z); p.mulPose(Axis.YP.rotation(yaw)); box(p, v, light, 0, 0, 0, w, h, d, c); p.popPose();
    }
    /** A box turned about Z (radians) round the Z axis through the origin, placed at radius r up the turned Y. */
    static void zbox(PoseStack p, VertexConsumer v, int light, float angle, float r, float z, float w, float h, float d, float[] c, float k) {
        p.pushPose(); p.mulPose(Axis.ZP.rotation(angle)); box(p, v, light, 0, r, z, w, h, d, c, k); p.popPose();
    }
    /** A full-bright lit part (the core, the LEDs): its colour times k. */
    static void lamp(PoseStack p, VertexConsumer v, float x, float y, float z, float w, float h, float d, float[] c, float k) {
        box(p, v, FULL, x, y, z, w, h, d, c, Mth.clamp(k, .03f, 1));
    }
    /** A bar of square section t from a to b. */
    static void bar(PoseStack p, VertexConsumer v, int light, float ax, float ay, float az, float bx, float by, float bz, float t, float[] c) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3f) return;
        p.pushPose();
        p.translate((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2);
        p.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f(dx / len, dy / len, dz / len)));
        box(p, v, light, 0, 0, 0, t, len, t, c);
        p.popPose();
    }
    /** An octagonal slab about the Z axis (four boxes turned in 45° steps): across w, thick d, centred at z. */
    static void octZ(PoseStack p, VertexConsumer v, int light, float z, float w, float d, float[] c) {
        float side = w * .4142f;
        for (int i = 0; i < 4; i++) zbox(p, v, light, i * Mth.PI / 4, 0, z, w, side, d, c, 1);
    }

    // ------------------------------------------------------------------ the timeline as positions
    /** Where every moving part is at an age (0..1 each). */
    static final class Stage {
        float lift, mast, fold, legs, deploy, glow, spin, jolt;
    }
    private static float clamp(float x) { return Mth.clamp(x, 0, 1); }
    static float ease(float x) { x = clamp(x); return x * x * (3 - 2 * x); }
    static float k(float t, float a, float b) { return ease((t - a) / (b - a)); }
    static float snap(float t, float a, float b) { float x = clamp((t - a) / (b - a)); return 1 - (1 - x) * (1 - x) * (1 - x); }

    static Stage stage(SonicEmitterEntity e, float a) {
        Stage s = new Stage();
        int activeEnd = e.activeEnd(), retract = e.retractFrom();
        if (a >= PREP) {
            float u = (a - PREP) / RISE;
            // Slow: the folded device heaves up through the soil; medium: the mast; fast: the chamber unfolds, the legs slam.
            s.lift = (float) Math.pow(ease(u / .45f), 1.25);
            s.mast = k(u, .45f, .78f);
            s.fold = snap(u, .76f, .94f);
            s.legs = snap(u, .82f, .98f);
            if (u < .45f) s.jolt = .012f * Mth.sin(a * 5.3f) * Mth.sin(u / .45f * Mth.PI);
            else if (u < .78f) s.jolt = .006f * Mth.sin(a * 7.1f);
        }
        if (a >= RISEN) s.jolt = -.045f * (float) Math.exp(-(a - RISEN) / 1.6f) * Mth.cos((a - RISEN) * 1.9f);
        s.deploy = clamp((a - RISEN) / DEPLOY);
        // The light: warming up through the deploy, steady while active, dying (with a stutter) as it cools.
        if (a < activeEnd) s.glow = s.deploy;
        else s.glow = Math.max(0, 1 - (a - activeEnd) / COOL) * (.75f + .25f * Mth.sin(a * 3.7f));
        s.spin = spin(a, activeEnd);
        if (a >= retract) {
            float v = (a - retract) / RETRACT;
            s.fold = 1 - snap(v, 0, .25f);
            s.legs = 1 - k(v, .05f, .3f);
            s.mast = 1 - k(v, .25f, .6f);
            s.lift = 1 - ease((v - .58f) / .42f);
            s.deploy = 1 - clamp(v / .15f);
            s.glow = 0;
            s.jolt = v > .58f ? .008f * Mth.sin(a * 6.1f) : 0;
        }
        return s;
    }
    /** The rings' turn: speeding up through the deploy, steady while active, running down through the cooling (an integral, no jumps). */
    private static float spin(float a, int activeEnd) {
        float rate = .22f, up = RISEN, full = ACTIVE_FROM, down = activeEnd, stop = activeEnd + COOL;
        float turned = 0;
        if (a > up) { float t = Math.min(a, full) - up; turned += rate * t * t / (2 * (full - up)); }
        if (a > full) turned += rate * (Math.min(a, down) - full);
        if (a > down) { float t = Math.min(a, stop) - down; turned += rate * (t - t * t / (2 * (stop - down))); }
        return turned;
    }

    // ------------------------------------------------------------------ drawing
    @Override public void render(SonicEmitterEntity e, float entityYaw, float partial, PoseStack p, MultiBufferSource b, int light) {
        float age = e.age(partial);
        if (age < 0) return;
        float broken = e.brokenAge(partial);
        if (broken >= FAIL) return;
        // Broken: frozen where it was, slumping and twitching.
        float frozen = broken >= 0 ? Math.max(0, age - broken) : age;
        Stage s = stage(e, frozen);
        VertexConsumer v = buffer(b);
        float baseYaw = e.getYRot();
        float aimYaw = Mth.rotLerp(partial, e.aimYawO, e.aimYaw), aimPitch = Mth.lerp(partial, e.aimPitchO, e.aimPitch);
        float shudder = 0, droop = 0, twitch = 0;
        if (broken >= 0) {
            float w = broken / FAIL;
            shudder = .02f * (.4f + w) * Mth.sin(age * 9.7f) + .012f * Mth.sin(age * 23.1f);
            droop = droop(broken);
            twitch = 9f * w * Mth.sin(age * 5.3f) * Mth.sin(age * 1.7f);
            s.glow = (Flicker.on(e.getId(), age) ? .9f : .05f) * (1 - w * .6f);
        }
        float recoil = 0;
        float since = e.level().getGameTime() + partial - e.pulsedAt;
        if (since >= 0 && since < 6) recoil = (float) Math.exp(-since / 1.4f) * clamp(since / .4f);

        p.pushPose();
        clods(e, p, v, light, age, s);
        p.translate(shudder, -(1 - s.lift) * BURIED + s.jolt, shudder * .6f);
        p.mulPose(Axis.YP.rotationDegrees(-baseYaw));
        float pivot = PIVOT_FOLDED + MAST_LIFT * s.mast;
        base(p, v, light, s, pivot);
        // The yaw ring, the fork, the chamber: turned onto the aim once unfolded.
        float relYaw = Mth.wrapDegrees(aimYaw - baseYaw) * s.fold + twitch;
        p.pushPose();
        p.translate(0, pivot, 0);
        p.mulPose(Axis.YP.rotationDegrees(-relYaw));
        fork(p, v, light);
        float elev = Mth.lerp(s.fold, Mth.HALF_PI, aimPitch * (broken >= 0 ? .3f : 1)) - droop;
        p.mulPose(Axis.XP.rotation(-elev));
        p.translate(0, 0, -.065f * recoil);
        chamber(p, v, light, s, recoil);
        p.popPose();
        p.popPose();
        super.render(e, entityYaw, partial, p, b, light);
    }

    /** The foot, the housing, the legs, the mast and its pistons (not turning). */
    private static void base(PoseStack p, VertexConsumer v, int light, Stage s, float pivot) {
        for (int i = 0; i < 4; i++) ybox(p, v, light, 0, .07f, 0, i * Mth.PI / 4, 1.0f, .14f, .42f, METAL_DARK);
        box(p, v, light, 0, .35f, 0, .66f, .42f, .66f, BLACK);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) box(p, v, light, sx * .3f, .35f, sz * .3f, .1f, .44f, .1f, METAL);
        // Ribbed armour panels on every side.
        for (int i = 0; i < 3; i++) {
            float y = .22f + .11f * i;
            for (int sz = -1; sz <= 1; sz += 2) box(p, v, light, 0, y, sz * .335f, .48f, .025f, .02f, METAL_DARK);
            for (int sx = -1; sx <= 1; sx += 2) box(p, v, light, sx * .335f, y, 0, .02f, .025f, .48f, METAL_DARK);
        }
        // Bolts on the foot.
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.PI / 4 + Mth.PI / 8;
            box(p, v, light, Mth.sin(a) * .44f, .15f, Mth.cos(a) * .44f, .04f, .025f, .04f, METAL_HI);
        }
        lamp(p, v, 0, .5f, .342f, .05f, .02f, .01f, COLD, .15f + .7f * s.glow);
        box(p, v, light, 0, .59f, 0, .74f, .06f, .74f, METAL);
        // The outrigger legs: stowed upright along the corners, slammed out and down into the ground.
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            float ex = sx * Mth.lerp(s.legs, .36f, .7f), ez = sz * Mth.lerp(s.legs, .36f, .7f), ey = Mth.lerp(s.legs, .12f, -.03f);
            bar(p, v, light, sx * .33f, .52f, sz * .33f, ex, ey, ez, .055f, METAL);
            bar(p, v, light, sx * .33f, .3f, sz * .33f, Mth.lerp(.5f, sx * .33f, ex), Mth.lerp(.5f, .52f, ey), Mth.lerp(.5f, sz * .33f, ez), .03f, METAL_HI);
            box(p, v, light, ex, ey + .02f, ez, .12f, .04f, .12f, METAL_DARK);
        }
        // The telescoping mast: three sections coming out one after another.
        float top = pivot - .3f, h = Math.max(0, top - .62f);
        float a1 = Math.min(h, .26f), a2 = Math.min(h, .5f);
        box(p, v, light, 0, .62f + a1 / 2, 0, .3f, a1, .3f, METAL_DARK);
        if (a2 > a1) box(p, v, light, 0, .62f + (a1 + a2) / 2 - .02f, 0, .24f, a2 - a1 + .04f, .24f, METAL);
        if (h > a2) box(p, v, light, 0, .62f + (a2 + h) / 2 - .02f, 0, .18f, h - a2 + .04f, .18f, METAL_HI);
        // The head collar on the mast and the two pistons pushing it up.
        box(p, v, light, 0, Math.max(.64f, top), 0, .36f, .06f, .36f, METAL);
        for (int sx = -1; sx <= 1; sx += 2) {
            box(p, v, light, sx * .25f, .71f, 0, .075f, .18f, .075f, METAL_DARK);
            box(p, v, light, sx * .25f, .81f, 0, .09f, .03f, .09f, METAL);
            if (top > .82f) bar(p, v, light, sx * .25f, .8f, 0, sx * .25f, top, 0, .035f, METAL_HI);
            box(p, v, light, sx * .21f, Math.max(.64f, top), 0, .12f, .045f, .08f, METAL_DARK);
        }
    }
    /** The yaw ring and the fork that holds the chamber (in the yaw frame, origin at the pitch axis). */
    private static void fork(PoseStack p, VertexConsumer v, int light) {
        for (int i = 0; i < 4; i++) ybox(p, v, light, 0, -.24f, 0, i * Mth.PI / 4, .5f, .06f, .21f, METAL_DARK);
        box(p, v, light, 0, -.27f, 0, 1.06f, .05f, .16f, METAL);
        for (int sx = -1; sx <= 1; sx += 2) {
            box(p, v, light, sx * .5f, -.12f, 0, .06f, .3f, .22f, METAL);
            bar(p, v, light, sx * .2f, -.25f, 0, sx * .48f, -.06f, 0, .04f, METAL_DARK);
            box(p, v, light, sx * .47f, 0, 0, .06f, .17f, .17f, METAL_HI);
        }
    }
    /** The sonic chamber (origin at the pitch axis, the face toward +Z). */
    private static void chamber(PoseStack p, VertexConsumer v, int light, Stage s, float recoil) {
        // The drum, its back and the cooling fins.
        octZ(p, v, light, -.03f, .82f, .42f, BLACK);
        octZ(p, v, light, -.03f, .86f, .08f, METAL_DARK);
        box(p, v, light, 0, 0, -.29f, .5f, .5f, .1f, METAL_DARK);
        for (int i = 0; i < 5; i++) box(p, v, light, -.2f + i * .1f, 0, -.38f, .025f, .56f, .12f, METAL);
        for (int sx = -1; sx <= 1; sx += 2) box(p, v, light, sx * .43f, 0, 0, .08f, .18f, .18f, METAL);
        // The rangefinder on top.
        box(p, v, light, 0, .44f, -.02f, .13f, .09f, .2f, METAL_DARK);
        lamp(p, v, 0, .44f, .085f, .045f, .045f, .01f, COLD, .1f + .4f * s.glow);
        // The bezel: a heavy outer ring; the bowl stepping in behind it.
        float flare = 1 + .25f * recoil;
        for (int i = 0; i < 16; i++) zbox(p, v, light, i * Mth.TWO_PI / 16, .4f, .2f, .17f, .07f, .07f, METAL, flare);
        octZ(p, v, light, .17f, .7f, .04f, BLACK);
        float spin = s.spin;
        for (int i = 0; i < 12; i++) zbox(p, v, light, spin + i * Mth.TWO_PI / 12, .3f, .15f, .13f, .05f, .05f, METAL, 1);
        octZ(p, v, light, .125f, .48f, .03f, BLACK_HI);
        for (int i = 0; i < 10; i++) zbox(p, v, light, -spin * .6f + i * Mth.TWO_PI / 10, .195f, .115f, .1f, .04f, .04f, METAL_HI, 1);
        octZ(p, v, light, .085f, .3f, .03f, BLACK);
        // Radial struts holding the core.
        for (int i = 0; i < 4; i++) zbox(p, v, light, Mth.PI / 4 + i * Mth.HALF_PI, .29f, .175f, .03f, .2f, .04f, METAL_DARK, 1);
        // The core: slides forward as it deploys; a cold light in its tip, faint lights in the ring round it.
        float ext = s.deploy * .06f;
        box(p, v, light, 0, 0, .1f + ext * .8f, .12f, .12f, .12f, METAL);
        box(p, v, light, 0, 0, .17f + ext, .075f, .075f, .04f, METAL_HI);
        lamp(p, v, 0, 0, .193f + ext, .05f, .05f, .01f, COLD, .05f + .95f * s.glow * (1 + .4f * recoil));
        for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8 + spin * .3f;
            lamp(p, v, Mth.sin(a) * .25f, Mth.cos(a) * .25f, .135f, .02f, .02f, .01f, COLD, .05f + .5f * s.glow);
        }
    }
    /**
     * The soil torn open round the foot: clods (the real ground's colour, and dark earth and stones) that heave, pop out
     * over the rim when the ground breaks and lie there; they sink back as it goes.
     */
    private static void clods(SonicEmitterEntity e, PoseStack p, VertexConsumer v, int light, float age, Stage s) {
        BlockPos below = BlockPos.containing(e.getX(), e.getY() - .5, e.getZ());
        var level = e.level();
        int col = level.getBlockState(below).getMapColor(level, below).col;
        float[] ground = {(col >> 16 & 255) / 255f * .8f, (col >> 8 & 255) / 255f * .8f, (col & 255) / 255f * .8f};
        float[] soil = {.27f, .19f, .13f}, stone = {.38f, .37f, .36f};
        float end = e.gone();
        float sink = e.broken() ? 0 : k(age, end - 8, end);
        for (int i = 0; i < 12; i++) {
            float h1 = (float) hash(e.getId() * 31 + i), h2 = (float) hash(e.getId() * 31 + i + 17), h3 = (float) hash(e.getId() * 31 + i + 43);
            float a = i * Mth.TWO_PI / 12 + h1 * .4f, r = .58f + h2 * .25f, size = .09f + h3 * .11f;
            float heave = k(age, 0, BREAK_AT) * (1 - k(age, BREAK_AT, BREAK_AT + 1));
            float pop = clamp((age - BREAK_AT - h1 * 2) / 7f);
            float y = -.1f + .08f * heave * Mth.sin(age * 2 + i) + (pop > 0 ? .1f + size * .3f : 0) + .3f * 4 * pop * (1 - pop) * (.5f + h2);
            r += .18f * pop + .05f * s.legs * h3;
            y -= sink * .35f;
            if (age < 1) continue;
            p.pushPose();
            p.translate(Mth.sin(a) * r, y, Mth.cos(a) * r);
            p.mulPose(Axis.YP.rotation(a + h3 * 3));
            p.mulPose(Axis.XP.rotation((.5f + h1) * .7f * Math.min(1, pop * 1.4f)));
            box(p, v, light, 0, 0, 0, size, size * .7f, size * (.8f + h2 * .5f), i % 3 == 0 ? soil : i % 5 == 1 ? stone : ground);
            p.popPose();
        }
    }
    /** How far a broken chamber has slumped (radians); 0 while whole (broken < 0). */
    static float droop(float broken) { return broken < 0 ? 0 : .55f * snap(broken, 0, 5) + .15f * broken / FAIL; }
    static double hash(double n) { double x = Math.sin(n * 12.9898) * 43758.5453; return x - Math.floor(x); }

    /** A broken emitter's light stuttering: on or off, changing every tick or two, the same on every client. */
    static final class Flicker {
        static boolean on(int id, float age) { return hash(id * 7 + (int) (age * .7f)) > .45; }
    }
}
