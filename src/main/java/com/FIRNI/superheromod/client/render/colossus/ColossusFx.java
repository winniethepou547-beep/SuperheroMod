package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.heroes.sandman.ColossusCrystal;
import com.FIRNI.superheromod.heroes.sandman.ColossusForm;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import static com.FIRNI.superheromod.client.render.colossus.ColossusShape.*;

/**
 * THE COLOSSUS' AIR AND LIGHT, drawn after the translucent blocks with the film effect buffers (dust and
 * light each keep their own buffer, so mixing them never forces a draw in between):
 *
 *  - BUILD: six sand streams spiral up from mouths round his base to the height the body has reached, grains
 *    riding them, dust rolling at the base and at every mouth, a puff where each clump lifts off and where it
 *    lands, dust thrown out where the palms slam into the ground.
 *  - STANDING: sand trickling off the undersides (shoulders, forearms, fists, chest, chin), coming and going,
 *    grains falling down each trickle; sand circling the foot of the dune; a soft contact shadow.
 *  - CRYSTALS: a glow gathering in the sand before each one pushes out, a flash as it does, then a slow pulse,
 *    the facet glints of {@link ColossusCrystals#glints} and a star on the brightest facet; cracked ones flicker.
 *  - EYES: lit as the crystals come, guttering while the head crystal is broken.
 *  - COLLAPSE: dust where each clump tears away and where it lands, trails behind the falling ones, a cloud
 *    rolling out round the base.
 *  - SHARDS / BREAKS: twinkling shards, a flash with sparks and dust where a crystal cracks or breaks.
 *
 * The dust is unlit geometry, so it is darkened by the light where he stands. Counts are fixed and drop with
 * distance. Everything is computed in view space directly; nothing is allocated per frame.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ColossusFx {
    private ColossusFx() {}

    private static final float[] SAND = {.86f, .76f, .55f}, DUST = {.72f, .62f, .45f}, GRAIN = {.93f, .85f, .63f};
    private static final int FLASHES = 24;
    private static final float FLASH_LIFE = 14;
    private static final double[] FX = new double[FLASHES], FY = new double[FLASHES], FZ = new double[FLASHES];
    private static final float[] FBORN = new float[FLASHES];
    private static final byte[] FKIND = new byte[FLASHES];
    private static int nextFlash;

    private static final float[] COS8 = new float[9], SIN8 = new float[9], COS16 = new float[17], SIN16 = new float[17];
    static {
        for (int i = 0; i <= 8; i++) { COS8[i] = (float) Math.cos(Math.PI * 2 * i / 8); SIN8[i] = (float) Math.sin(Math.PI * 2 * i / 8); }
        for (int i = 0; i <= 16; i++) { COS16[i] = (float) Math.cos(Math.PI * 2 * i / 16); SIN16[i] = (float) Math.sin(Math.PI * 2 * i / 16); }
        clear();
    }

    // ---- the pass in progress
    private static VertexConsumer soft, add;
    private static final Matrix4f VIEW = new Matrix4f(), M = new Matrix4f();
    private static double camX, camY, camZ;
    private static float lk = 1, lod = 1;
    private static final float[] A = new float[3], B = new float[3], F = new float[3], PEAK = new float[4], S = new float[8];
    private static final ColossusCrystals.Glow GLOW = (x, y, z, r, g, b, a) -> add.vertex(x, y, z).color(r, g, b, a).endVertex();

    /** A crystal cracked (kind 0) or broke (kind 1) at this world point. */
    static void flash(double x, double y, double z, int kind, float now) {
        int i = nextFlash;
        nextFlash = (nextFlash + 1) % FLASHES;
        FX[i] = x; FY[i] = y; FZ[i] = z; FBORN[i] = now; FKIND[i] = (byte) kind;
    }

    static void clear() {
        for (int i = 0; i < FLASHES; i++) FBORN[i] = -1e9f;
    }

    private static boolean anyFlash(float now) {
        for (int i = 0; i < FLASHES; i++) if (now - FBORN[i] >= 0 && now - FBORN[i] < FLASH_LIFE) return true;
        return false;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = e.getPartialTick();
        float now = ColossusRenderer.clock(mc.level, partial);
        if (ColossusRenderer.LIVE.isEmpty() && ColossusRenderer.RUINS.isEmpty() && ColossusRenderer.SHARDS.empty() && !anyFlash(now)) return;
        double game = mc.level.getGameTime() + (double) partial;
        Vec3 cam = e.getCamera().getPosition();
        camX = cam.x; camY = cam.y; camZ = cam.z;
        VIEW.set(e.getPoseStack().last().pose());
        // Vertices go in already in view space.
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.last().pose().identity();
        RenderSystem.applyModelViewMatrix();
        MultiBufferSource.BufferSource buffers = FilmFx.batched();
        try {
            soft = buffers.getBuffer(FilmFx.SOFT);
            add = buffers.getBuffer(FilmFx.ADD);
            for (ColossusVisual v : ColossusRenderer.LIVE.values()) {
                if (!v.drawn || !near(v)) continue;
                lk = v.lightK;
                shadow(v, ColossusBody.smooth((v.progress - .04f) / .2f));
                if (v.progress < .5f) forming(v, now);
                if (v.progress >= .4f) standing(v, now);
                if (v.progress >= .78f) crystals(v, now);
                eyes(v, now);
            }
            for (ColossusVisual r : ColossusRenderer.RUINS) {
                if (!r.drawn || !near(r)) continue;
                lk = r.lightK;
                collapse(r, (float) (game - r.collapseStart), now);
            }
            lk = 1; lod = 1;
            shards(now);
            flashes(now);
            buffers.endBatch();
        } finally {
            soft = add = null;
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** Level of detail by distance: full up close, thinned out further away, nothing past ~96 blocks. */
    private static boolean near(ColossusVisual v) {
        double dx = v.x - camX, dy = v.y + 5 - camY, dz = v.z - camZ;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        lod = 1 - ColossusBody.smooth((d - 40) / 56f);
        return lod > .02f;
    }

    // =====================================================================================================
    // the build
    // =====================================================================================================

    private static void forming(ColossusVisual v, float now) {
        float p = v.progress;
        float env = ColossusBody.smooth(p / .05f) * (1 - ColossusBody.smooth((p - .37f) / .1f));
        float top = 10 * ColossusBody.smooth((p - .03f) / .38f);
        if (env > .01f) {
            int segs = lod > .6f ? 14 : 7;
            for (int k = 0; k < ColossusBody.STREAMS; k++) {
                // The stream: a ribbon along the spiral, its brightness running up it so it reads as flowing.
                ColossusBody.streamPoint(k, 0, Math.max(.6f, top), 1.6f, F);
                foot(v, F[0], F[1], F[2], A);
                for (int s = 1; s <= segs; s++) {
                    float u0 = (s - 1) / (float) segs, u1 = s / (float) segs;
                    ColossusBody.streamPoint(k, u1, Math.max(.6f, top), 1.6f, F);
                    foot(v, F[0], F[1], F[2], B);
                    float f0 = .6f + .4f * (float) Math.sin(Math.PI * 2 * (3 * u0 - now * .09f) + k);
                    float f1 = .6f + .4f * (float) Math.sin(Math.PI * 2 * (3 * u1 - now * .09f) + k);
                    float w = .62f * (1 - .55f * (u0 + u1) * .5f);
                    streak(soft, A, B, w, SAND, env * .42f * f0 * lod, env * .42f * f1 * lod);
                    A[0] = B[0]; A[1] = B[1]; A[2] = B[2];
                }
                // Grains riding it.
                int grains = lod > .6f ? 9 : 4;
                for (int g = 0; g < grains; g++) {
                    float u = frac(g / (float) grains + now * .035f + k * .13f);
                    ColossusBody.streamPoint(k, u, Math.max(.6f, top), 1.6f, F);
                    int h = k * 31 + g;
                    foot(v, F[0] + (hash(h) - .5f) * .5f, F[1] + (hash(h + 7) - .5f) * .4f, F[2] + (hash(h + 13) - .5f) * .5f, A);
                    speck(soft, A, .06f + .04f * hash(h + 3), GRAIN, env * .85f * (1 - u * .5f) * lod);
                }
                // Dust boiling up at its mouth.
                ColossusBody.streamPoint(k, 0, top, 1.6f, F);
                foot(v, F[0], .35f + .25f * (float) Math.sin(now * .11f + k), F[2], A);
                puff(soft, A, 1.1f + .25f * (float) Math.sin(now * .07f + k * 2), DUST, env * .36f * lod);
            }
            // Dust rolling round the base.
            for (int i = 0; i < 12; i++) {
                float drift = frac(i * .37f + now * .012f);
                float a = (float) (i * Math.PI / 6) - now * .03f, r = 3 + 1.8f * drift;
                foot(v, (float) Math.cos(a) * r, .35f + .25f * hash(i + 40) + drift * .4f, (float) Math.sin(a) * r, A);
                puff(soft, A, 1.3f + .5f * drift, i % 2 == 0 ? DUST : SAND, env * .3f * (1 - drift * .7f) * lod);
            }
        }
        // Clumps lifting off the ground, and landing on the body.
        ColossusBody.Frame f = v.frame;
        int step = lod > .6f ? 2 : 4;
        for (int i = 0; i < N; i += step) {
            if (KIND[i] == CORE) continue;
            byte st = f.state[i];
            if (st == ColossusBody.Frame.FLYING && f.phase[i] < .2f) {
                foot(v, f.at[i * 3], Math.max(.2f, f.at[i * 3 + 1]), f.at[i * 3 + 2], A);
                puff(soft, A, .5f + RADIUS[i], DUST, .3f * (1 - f.phase[i] / .2f) * lod);
            } else if (st == ColossusBody.Frame.SET && f.phase[i] < 1.9f) {
                float since = (f.phase[i] - 1) * FLIGHT[i] * ColossusForm.FORM_TICKS;
                if (since < 0 || since >= 6) continue;
                foot(v, f.target[i * 3], f.target[i * 3 + 1], f.target[i * 3 + 2], A);
                puff(soft, A, RADIUS[i] * 1.3f + since * .08f, SAND, .3f * (1 - since / 6) * lod);
            }
        }
        // The palms slam into the ground (tick 60 of the build): dust thrown out from under each hand.
        float since = (p - .60f) * ColossusForm.FORM_TICKS;
        if (since > -.5f && since < 16) {
            for (int side = 0; side < 2; side++) {
                int hand = side == 0 ? R_HAND : L_HAND;
                if (!v.skeleton.visible[hand]) continue;
                Matrix4f m = v.skeleton.bone[hand];
                float px = m.m00() * PALM[0] + m.m10() * PALM[1] + m.m20() * PALM[2] + m.m30();
                float pz = m.m02() * PALM[0] + m.m12() * PALM[1] + m.m22() * PALM[2] + m.m32();
                float k = Math.max(0, since);
                for (int i = 0; i < 10; i++) {
                    float a = (float) (i * Math.PI / 5) + side, r = .6f + k * .22f;
                    foot(v, px + (float) Math.cos(a) * r, .25f + .3f * hash(i + side * 10) + k * .02f, pz + (float) Math.sin(a) * r, A);
                    puff(soft, A, .9f + k * .06f, i % 2 == 0 ? DUST : SAND, .5f * (1 - k / 16) * lod);
                }
            }
        }
    }

    // =====================================================================================================
    // standing
    // =====================================================================================================

    private static void standing(ColossusVisual v, float now) {
        float stand = ColossusBody.smooth((v.progress - .4f) / .2f) * lod;
        if (stand <= .01f) return;
        trickles(v, now, stand, false);
        // Sand circling the foot of the dune.
        for (int k = 0; k < 3; k++) {
            float a0 = now * .045f + k * 2.1f, rr = 2.55f + .35f * k, y = .2f + .12f * k;
            foot(v, (float) Math.cos(a0) * rr, y, (float) Math.sin(a0) * rr, A);
            for (int s = 1; s <= 6; s++) {
                float a = a0 + s * .22f;
                foot(v, (float) Math.cos(a) * rr, y + .06f * (float) Math.sin(now * .1f + s), (float) Math.sin(a) * rr, B);
                float edge = 1 - Math.abs(s - 3.5f) / 3.5f;
                streak(soft, A, B, .22f, SAND, .2f * stand * edge, .2f * stand * edge);
                A[0] = B[0]; A[1] = B[1]; A[2] = B[2];
            }
        }
    }

    /** Sand trickling off the undersides: a thin falling curtain with grains running down it, coming and going. */
    private static void trickles(ColossusVisual v, float now, float strength, boolean pouring) {
        for (int e = 0; e < EMITTERS; e++) {
            int bone = EMIT_BONE[e];
            if (!v.skeleton.visible[bone]) continue;
            float on = pouring ? 1 : .5f + .5f * (float) Math.sin(now * (.05f + .03f * hash(e * 3 + 1)) + e * 2.1f);
            if (on < .3f) continue;
            float a = (pouring ? .45f : .3f) * ColossusBody.smooth((on - .3f) / .3f) * strength;
            if (a <= .01f) continue;
            Matrix4f m = v.skeleton.bone[bone];
            int o = e * 3;
            float ex = m.m00() * EMIT_POS[o] + m.m10() * EMIT_POS[o + 1] + m.m20() * EMIT_POS[o + 2] + m.m30();
            float ey = m.m01() * EMIT_POS[o] + m.m11() * EMIT_POS[o + 1] + m.m21() * EMIT_POS[o + 2] + m.m31();
            float ez = m.m02() * EMIT_POS[o] + m.m12() * EMIT_POS[o + 1] + m.m22() * EMIT_POS[o + 2] + m.m32();
            float len = Math.min(4.5f, ey - .05f);
            if (len < .2f) continue;
            foot(v, ex, ey, ez, A);
            foot(v, ex, ey - len * .85f, ez, B);
            streak(soft, A, B, .045f + .04f * hash(e + 9), SAND, a, 0);
            int grains = lod > .6f ? 4 : 2;
            float rate = .045f + .02f * hash(e * 5);
            for (int j = 0; j < grains; j++) {
                float u = frac(j / (float) grains + now * rate + hash(e * 7));
                foot(v, ex + (hash(e * 11 + j) - .5f) * .14f, ey - len * u * u, ez + (hash(e * 13 + j) - .5f) * .14f, A);
                speck(soft, A, .05f, GRAIN, a * 1.8f * (1 - u));
            }
            if (ey - len < .3f && (e & 1) == 0) {
                foot(v, ex, .15f, ez, A);
                puff(soft, A, .45f, DUST, a * .7f);
            }
        }
    }

    /** A soft dark patch under him: the giant sits on the ground, not over it. */
    private static void shadow(ColossusVisual v, float built) {
        float alpha = .28f * built * lod;
        if (alpha <= .01f) return;
        float r = 3.5f;
        foot(v, 0, .03f, 0, A);
        for (int i = 0; i < 16; i++) {
            foot(v, COS16[i] * r, .03f, SIN16[i] * r, B);
            foot(v, COS16[i + 1] * r, .03f, SIN16[i + 1] * r, F);
            put(soft, A, 0, 0, 0, alpha); put(soft, A, 0, 0, 0, alpha);
            put(soft, F, 0, 0, 0, 0); put(soft, B, 0, 0, 0, 0);
        }
    }

    // =====================================================================================================
    // crystals and eyes
    // =====================================================================================================

    private static void crystals(ColossusVisual v, float now) {
        float p = v.progress;
        for (ColossusCrystal type : ColossusCrystal.values()) {
            int c = type.ordinal(), state = v.states[c];
            if (state >= 3) continue;
            float pre = p >= 1 ? 1 : ColossusBody.smooth((p - (ColossusVisual.CRYSTAL_START[c] - .07f)) / .07f);
            if (pre <= 0) continue;
            float age = v.crystalAge(c);
            float pulse = .62f + .24f * (float) Math.sin(now * .09f + c * 1.3f) + .14f * (float) Math.sin(now * .23f + c);
            if (state == 1) pulse *= .76f + .24f * (float) Math.abs(Math.sin(now * .61f + c) * Math.sin(now * 1.37f));
            else if (state == 2) pulse *= (Math.sin(now * 1.7f + c * 3) > -.25 ? 1 : .25f) * (.45f + .3f * (float) Math.sin(now * .37f));
            float s = pulse * pre * lod;
            Matrix4f m = v.cluster[c];
            float radius = (float) type.radius * ColossusCrystals.SIZE;
            foot(v, m.m30(), m.m31(), m.m32(), A);
            puff(add, A, radius * (2.4f + .3f * (float) Math.sin(now * .11f + c)), 1, .55f, .2f, .24f * s);
            puff(add, A, radius * 1.05f, 1, .82f, .55f, .32f * s);
            if (age > 0) {
                M.set(VIEW).translate((float) (v.x - camX), (float) (v.y - camY), (float) (v.z - camZ)).mul(m);
                ColossusCrystals.glints(GLOW, M, ColossusCrystals.count(state), age, now, s, PEAK);
                if (PEAK[3] > .5f) {
                    B[0] = PEAK[0]; B[1] = PEAK[1]; B[2] = PEAK[2];
                    star(B, radius * .6f * Math.min(1, PEAK[3]), Math.min(1, PEAK[3]) * .9f);
                }
                // It pushes out with a flash.
                if (age < 9) {
                    float k = age / 9;
                    puff(add, A, radius * (1.5f + 3 * k), 1, .85f, .6f, .7f * (1 - k) * (1 - k) * lod);
                }
            }
        }
    }

    private static void eyes(ColossusVisual v, float now) {
        if (v.ownHead || !v.skeleton.visible[HEAD]) return;
        float lit = ColossusRenderer.ignite(v, now) * lod;
        if (lit <= .01f) return;
        Matrix4f m = v.skeleton.bone[HEAD];
        float breathe = .85f + .15f * (float) Math.sin(now * .06f);
        for (int k = 0; k < 2; k++) {
            int o = k * 3;
            float x = m.m00() * EYES[o] + m.m10() * EYES[o + 1] + m.m20() * EYES[o + 2] + m.m30();
            float y = m.m01() * EYES[o] + m.m11() * EYES[o + 1] + m.m21() * EYES[o + 2] + m.m31();
            float z = m.m02() * EYES[o] + m.m12() * EYES[o + 1] + m.m22() * EYES[o + 2] + m.m32();
            foot(v, x, y, z, A);
            puff(add, A, .75f, 1, .55f, .18f, .32f * lit * breathe);
            puff(add, A, .24f, 1, .85f, .5f, .7f * lit);
        }
    }

    // =====================================================================================================
    // collapse
    // =====================================================================================================

    private static void collapse(ColossusVisual r, float k, float now) {
        ColossusBody.Frame f = r.frame;
        int step = lod > .6f ? 2 : 4;
        for (int i = 0; i < N; i += step) {
            if (KIND[i] == CORE) continue;
            byte st = f.state[i];
            int o = i * 3;
            if (st == ColossusBody.Frame.FALLING) {
                float t = f.phase[i];
                if (t < 8) {
                    foot(r, f.target[o], f.target[o + 1], f.target[o + 2], A);
                    puff(soft, A, .45f + RADIUS[i] + t * .05f, DUST, .38f * (1 - t / 8) * lod);
                }
                if (i % 3 == 0) {
                    foot(r, f.at[o], f.at[o + 1], f.at[o + 2], A);
                    foot(r, f.at[o], f.at[o + 1] + .9f, f.at[o + 2], B);
                    streak(soft, A, B, .08f + RADIUS[i] * .3f, SAND, .35f * lod, 0);
                }
            } else if (st == ColossusBody.Frame.LANDED && f.phase[i] < 9) {
                float t = f.phase[i];
                foot(r, f.at[o], .25f, f.at[o + 2], A);
                puff(soft, A, .6f + t * .09f, DUST, .42f * (1 - t / 9) * lod);
            }
        }
        // The cloud rolling out round the base.
        float kk = Math.min(k, 45);
        float fade = ColossusBody.smooth(k / 6) * (1 - ColossusBody.smooth((k - 34) / 32)) * lod;
        if (fade > .01f) for (int i = 0; i < 16; i++) {
            float a = (float) (i * Math.PI / 8) + hash(i) * .3f, rr = 1.8f + kk * .07f;
            foot(r, (float) Math.cos(a) * rr, .4f + hash(i + 3) * .8f + kk * .02f, (float) Math.sin(a) * rr, A);
            puff(soft, A, 1.5f + kk * .04f, i % 2 == 0 ? DUST : SAND, .42f * fade);
        }
        // Early on, sand pours off everything.
        if (k < 26) trickles(r, now, (1 - k / 26) * lod, true);
    }

    // =====================================================================================================
    // shards and breaks
    // =====================================================================================================

    private static void shards(float now) {
        ColossusCrystals.Shards sh = ColossusRenderer.SHARDS;
        if (sh.empty()) return;
        for (int i = 0; i < ColossusCrystals.Shards.CAPACITY; i++) {
            float a = sh.age(i, now);
            if (a < 0) continue;
            sh.at(i, a, S);
            if (S[3] <= .02f) continue;
            view(sh.x[i] + S[0], sh.y[i] + S[1], sh.z[i] + S[2], A);
            float tw = (float) Math.sin(now * .5f + sh.seed[i] * 40);
            if (tw > .65f) star(A, .32f * sh.len[i] + .1f, (tw - .65f) / .35f * S[3]);
            if (S[4] == 0) puff(add, A, .18f + sh.len[i] * .3f, 1, .7f, .35f, .22f * S[3]);
        }
    }

    private static void flashes(float now) {
        for (int i = 0; i < FLASHES; i++) {
            float a = now - FBORN[i];
            if (a < 0 || a >= FLASH_LIFE) continue;
            float k = a / FLASH_LIFE, fade = (1 - k) * (1 - k);
            boolean broke = FKIND[i] == 1;
            view(FX[i], FY[i], FZ[i], A);
            puff(add, A, broke ? .8f + 2.4f * (float) Math.sqrt(k) : .6f + 1.1f * k, 1, .72f, .38f, (broke ? .9f : .6f) * fade);
            int sparks = broke ? 12 : 5;
            for (int s = 0; s < sparks; s++) {
                float dx = hash(i * 97 + s * 3) - .5f, dy = hash(i * 97 + s * 3 + 1) - .3f, dz = hash(i * 97 + s * 3 + 2) - .5f;
                float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4f;
                dx /= l; dy /= l; dz /= l;
                float d0 = a * (broke ? .22f : .15f), d1 = d0 + .3f + a * .18f;
                view(FX[i] + dx * d0, FY[i] + dy * d0 - .004 * a * a, FZ[i] + dz * d0, B);
                view(FX[i] + dx * d1, FY[i] + dy * d1 - .004 * a * a, FZ[i] + dz * d1, F);
                streak(add, B, F, .035f, 1, .82f, .48f, 0, .9f * (1 - k));
            }
            if (broke) for (int s = 0; s < 5; s++) {
                float dx = hash(i * 53 + s) - .5f, dz = hash(i * 53 + s + 9) - .5f;
                view(FX[i] + dx * a * .16f, FY[i] - a * .02f, FZ[i] + dz * a * .16f, B);
                puff(soft, B, .5f + a * .07f, SAND, .35f * (1 - k));
            }
        }
    }

    // =====================================================================================================
    // primitives (view space)
    // =====================================================================================================

    /** Absolute world point to view space. */
    private static void view(double x, double y, double z, float[] out) {
        float rx = (float) (x - camX), ry = (float) (y - camY), rz = (float) (z - camZ);
        out[0] = VIEW.m00() * rx + VIEW.m10() * ry + VIEW.m20() * rz + VIEW.m30();
        out[1] = VIEW.m01() * rx + VIEW.m11() * ry + VIEW.m21() * rz + VIEW.m31();
        out[2] = VIEW.m02() * rx + VIEW.m12() * ry + VIEW.m22() * rz + VIEW.m32();
    }

    /** A point in a colossus' foot space to view space. */
    private static void foot(ColossusVisual v, float x, float y, float z, float[] out) {
        view(v.x + x, v.y + y, v.z + z, out);
    }

    private static void put(VertexConsumer v, float[] p, float r, float g, float b, float a) {
        v.vertex(p[0], p[1], p[2]).color(r, g, b, Math.min(1, a)).endVertex();
    }

    private static void put(VertexConsumer v, float x, float y, float z, float r, float g, float b, float a) {
        v.vertex(x, y, z).color(r, g, b, Math.min(1, a)).endVertex();
    }

    /** A round camera-facing puff (dust in the soft buffer, darkened by the light; light in the additive one). */
    private static void puff(VertexConsumer v, float[] c, float size, float[] rgb, float a) {
        puff(v, c, size, rgb[0] * lk, rgb[1] * lk, rgb[2] * lk, a);
    }

    private static void puff(VertexConsumer v, float[] c, float size, float r, float g, float b, float a) {
        if (a <= .004f || size <= 0) return;
        float x = c[0], y = c[1], z = c[2], mid = size * .45f, am = a * .45f;
        for (int i = 0; i < 8; i++) {
            float c0 = COS8[i], s0 = SIN8[i], c1 = COS8[i + 1], s1 = SIN8[i + 1];
            put(v, x, y, z, r, g, b, a); put(v, x, y, z, r, g, b, a);
            put(v, x + c1 * mid, y + s1 * mid, z, r, g, b, am); put(v, x + c0 * mid, y + s0 * mid, z, r, g, b, am);
            put(v, x + c0 * mid, y + s0 * mid, z, r, g, b, am); put(v, x + c1 * mid, y + s1 * mid, z, r, g, b, am);
            put(v, x + c1 * size, y + s1 * size, z, r, g, b, 0); put(v, x + c0 * size, y + s0 * size, z, r, g, b, 0);
        }
    }

    /** A camera-facing ribbon from a to b, bright along its middle, fading to its edges. */
    private static void streak(VertexConsumer v, float[] a, float[] b, float width, float[] rgb, float aa, float ab) {
        streak(v, a, b, width, rgb[0] * lk, rgb[1] * lk, rgb[2] * lk, aa, ab);
    }

    private static void streak(VertexConsumer v, float[] a, float[] b, float width, float r, float g, float bl, float aa, float ab) {
        if (Math.max(aa, ab) <= .004f) return;
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        // Side: along the ribbon crossed with the direction to the camera (at the origin of view space).
        float sx = dy * -b[2] + dz * b[1], sy = dz * -b[0] + dx * b[2], sz = dx * -b[1] + dy * b[0];
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6f) return;
        sx *= width / l; sy *= width / l; sz *= width / l;
        for (int s = -1; s <= 1; s += 2) {
            put(v, a, r, g, bl, aa); put(v, b, r, g, bl, ab);
            put(v, b[0] + sx * s, b[1] + sy * s, b[2] + sz * s, r, g, bl, 0);
            put(v, a[0] + sx * s, a[1] + sy * s, a[2] + sz * s, r, g, bl, 0);
        }
    }

    /** A grain: a tiny camera-facing square. */
    private static void speck(VertexConsumer v, float[] c, float s, float[] rgb, float a) {
        if (a <= .004f) return;
        float r = rgb[0] * lk, g = rgb[1] * lk, b = rgb[2] * lk;
        put(v, c[0] - s, c[1] - s, c[2], r, g, b, a); put(v, c[0] + s, c[1] - s, c[2], r, g, b, a);
        put(v, c[0] + s, c[1] + s, c[2], r, g, b, a); put(v, c[0] - s, c[1] + s, c[2], r, g, b, a);
    }

    /** A four-pointed glint: thin rays tapering from the centre, and a small glow. */
    private static void star(float[] c, float size, float a) {
        if (a <= .01f) return;
        float w = size * .07f;
        for (int k = 0; k < 4; k++) {
            float dx = k == 0 ? 1 : k == 1 ? -1 : 0, dy = k == 2 ? 1 : k == 3 ? -1 : 0;
            float len = k < 2 ? size : size * .75f;
            put(add, c[0] - dy * w, c[1] + dx * w, c[2], 1, .92f, .75f, a);
            put(add, c[0] + dx * len, c[1] + dy * len, c[2], 1, .92f, .75f, 0);
            put(add, c[0] + dx * len, c[1] + dy * len, c[2], 1, .92f, .75f, 0);
            put(add, c[0] + dy * w, c[1] - dx * w, c[2], 1, .92f, .75f, a);
        }
        puff(add, c, size * .3f, 1, .9f, .7f, a * .6f);
    }

    private static float frac(float x) { return x - (float) Math.floor(x); }
}
