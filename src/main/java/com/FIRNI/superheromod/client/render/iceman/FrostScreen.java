package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.heroes.iceman.IcemanConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The frost on the frozen player's own screen, drawn under the HUD: always "looking through frosted glass", never blind.
 * <ul>
 * <li>By their own frost meter: low = a faint frost at the very edges; medium = real window frost (fern-like crystals,
 *     textures/gui/iceman/frost_atlas.png from tools/icons/iceman_frost.py) creeping in from the edges, the picture soft
 *     there; high = heavy frost in the corners, a colder picture, a slight vignette, cracks starting from the corners as
 *     it nears 100; the brush on them makes the frosty edges flare as it lands.</li>
 * <li>DEEP FREEZE: in DEEP_SEIZE ticks the frost grows over most of the view from the edges (the centre still readable),
 *     a short white flash, crack lines spread, the world's sounds dip (quieter, duller); near the end a whole network of
 *     cracks; when it breaks, the screen's ice splits into pieces that fall away, a short flash, the sound comes back.</li>
 * <li>The ice slide's lens (FX_LENS, to the one it touched): frosted corners, a clearer centre, the world wobbling
 *     through it (the frame drawn back on a grid with small offsets: refraction), a few cracks, condensation drops;
 *     it melts away at the end.</li>
 * </ul>
 * From medium frost on, ice crystals grow in on the glass from the corners (textures/gui/iceman/frost_crystals.png, scaled
 * out from each corner, never reaching the middle), and the picture at the edges wavers a little with a faint cyan
 * fringe (the cold glass bending the light). The middle of the view is always left readable.
 * Strength follows IcemanConfig.SCREEN_FROST.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class FrostScreen {
    private static final Logger LOG = LogUtils.getLogger();
    private FrostScreen() {}

    private static final ResourceLocation ATLAS = new ResourceLocation(SuperheroMod.MODID, "textures/gui/iceman/frost_atlas.png"),
            NET = new ResourceLocation(SuperheroMod.MODID, "textures/gui/iceman/crack_net.png"),
            DROPS = new ResourceLocation(SuperheroMod.MODID, "textures/gui/iceman/lens_drops.png"),
            CRYSTALS = new ResourceLocation(SuperheroMod.MODID, "textures/gui/iceman/frost_crystals.png");
    /** The atlas: 12 frames of growing frost, 4 x 3 cells; how far in each frame's frost reaches (pixels of 360). */
    private static final int COLS = 4, ROWS = 3, FRAMES = 12;
    private static final float[] REACH = {6, 11, 17, 24, 32, 41, 51, 62, 74, 86, 98, 110};
    /** The grid every layer is drawn on (per-corner alpha, colour and texture offsets). */
    private static final int GX = 20, GY = 12, NV = (GX + 1) * (GY + 1);

    // ------------------------------------------------------------------ state
    private static boolean frozen;
    private static float deepStart = -1e5f, breakStart = -1e5f, breakCover, breakPresence;
    private static int deepTotal = DEEP_TICKS, breakHow;
    private static float lensStart = -1e5f, lensTicks = 1, lensPower;
    private static float brushAt = -1e5f;
    private static float flashAt = -1e5f, flashPower, flashLife = 5;
    private static Crack[] edgeCracks, deepCracks, lensCracks;
    private static Shard[] shards;
    private static final Random RNG = new Random();

    /** The player here froze solid (since: ticks ago, when only seen now). */
    static void freeze(int total, float since) {
        float now = FrostFx.now();
        frozen = true;
        deepStart = now - since;
        deepTotal = Math.max(DEEP_SEIZE + 2, total);
        breakStart = -1e5f; shards = null;
        Random r = new Random(RNG.nextLong());
        deepCracks = new Crack[4 + r.nextInt(2)];
        for (int i = 0; i < deepCracks.length; i++) deepCracks[i] = fromEdge(r, .55f + .3f * r.nextFloat());
        if (since < 2) flash(.5f, 6);
    }
    /** The ice over the view broke (how 2: by a blow). */
    static void broke(int how) {
        if (!frozen) return;
        float now = FrostFx.now();
        breakCover = deepCover(now);
        breakPresence = 1;
        frozen = false;
        breakStart = now;
        breakHow = how;
        shards = shards(new Random(RNG.nextLong()));
        flash(how == 2 ? .45f : .32f, 5);
    }
    /** The slide's icy lens for ticks (power = strength). */
    static void lens(float power, int ticks) {
        float now = FrostFx.now();
        boolean fresh = now - lensStart > lensTicks;
        lensStart = now; lensTicks = ticks; lensPower = Mth.clamp(power, 0, 1.2f);
        if (fresh || lensCracks == null) {
            Random r = new Random(RNG.nextLong());
            lensCracks = new Crack[2 + r.nextInt(2)];
            for (int i = 0; i < lensCracks.length; i++) lensCracks[i] = fromCorner(r, .25f + .15f * r.nextFloat() * power);
        }
        flash(.14f * power, 4);
    }
    static void brushed() { brushAt = FrostFx.now(); }
    private static void flash(float power, float life) { flashAt = FrostFx.now(); flashPower = power; flashLife = life; }

    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int me = mc.player.getId();
        boolean deep = FrostFx.deep(me);
        // Never left frozen over: the freeze over without a word, the ice breaks all the same.
        if (frozen && !deep) broke(1);
        float m = FrostFx.meter(me);
        if (m < 70) edgeCracks = null;
        else if (edgeCracks == null) {
            Random r = new Random(RNG.nextLong());
            edgeCracks = new Crack[]{fromCorner(r, .3f), fromCorner(r, .26f)};
        }
    }
    static void clear() {
        frozen = false; deepStart = breakStart = lensStart = brushAt = flashAt = -1e5f;
        edgeCracks = deepCracks = lensCracks = null; shards = null;
    }

    // ------------------------------------------------------------------ how much
    /** The frost cover (0..12 frames of the atlas) for a meter. */
    private static float cover(float m) {
        if (m <= 0) return 0;
        if (m < 25) return m / 25 * 1.6f;
        if (m < 50) return 1.6f + (m - 25) / 25 * 2.4f;
        if (m < 75) return 4f + (m - 50) / 25 * 2.4f;
        return 6.4f + Math.min(1, (m - 75) / 25) * 1.6f;
    }
    /** While deep frozen: the frost grows over most of the view in DEEP_SEIZE ticks. */
    private static float deepCover(float now) { return Mth.lerp(FilmFx.ease((now - deepStart) / DEEP_SEIZE), 8f, 11.7f); }
    /** The world's sounds while frozen over: 0 = as they are. */
    private static float muffled() {
        float now = FrostFx.now();
        if (frozen) return .85f * FilmFx.ease((now - deepStart) / 3);
        float t = now - breakStart;
        return t >= 0 && t < 14 ? .85f * (1 - FilmFx.ease(t / 14)) : 0;
    }

    // ------------------------------------------------------------------ drawing
    private static TextureTarget half, small;
    private static boolean copyFailed;
    private static final float[] GXS = new float[NV], GYS = new float[NV], EDGE = new float[NV], CORNER = new float[NV], MASK = new float[NV];
    private static final float[] U = new float[NV], V = new float[NV], R = new float[NV], G = new float[NV], B = new float[NV], A = new float[NV];

    @SubscribeEvent public static void screen(RenderGuiEvent.Pre e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float S = IcemanConfig.SCREEN_FROST.get().floatValue();
        float now = FrostFx.now();
        FrostFx.Body body = FrostFx.BODIES.get(mc.player.getId());
        float m = body == null ? 0 : Mth.clamp(body.deep ? FROST_MAX : body.shown(mc.getFrameTime()), 0, FROST_MAX);
        float meterPres = FilmFx.ease(m / 6);
        float cover = cover(m);
        float deepAge = now - deepStart;
        if (frozen) cover = Math.max(cover, deepCover(now));
        float lt = now - lensStart;
        float lp = lt >= 0 && lt <= lensTicks ? lensPower * FilmFx.ease(lt / 5) * (1 - FilmFx.ease((lt - lensTicks * .6f) / (lensTicks * .4f))) : 0;
        float lensCover = Mth.clamp(lp, 0, 1) * 7.5f;
        float brushK = 1 - Mth.clamp((now - brushAt) / 14, 0, 1);
        float bt = now - breakStart;
        boolean breaking = shards != null && bt >= 0 && bt < 24;
        float ft = now - flashAt, flashK = ft >= 0 && ft < flashLife ? flashPower * (1 - ft / flashLife) * (1 - ft / flashLife) : 0;
        if (S <= .001f || (cover < .01f && lp < .01f && !breaking && flashK < .01f && !frozen)) return;
        // The crystals in the corners: from medium frost, full while frozen over, a little with the slide's lens; falling
        // away as the deep freeze's ice breaks.
        float crystals = Math.max(Math.max(.75f * FilmFx.ease((m - 40) / 55), frozen ? .75f + .25f * FilmFx.ease(deepAge / DEEP_SEIZE) : 0), .45f * Mth.clamp(lp, 0, 1));
        float crystalsOut = 0;
        if (!frozen && shards != null && bt >= 0 && bt < 10) { crystalsOut = FilmFx.ease(bt / 8); crystals = Math.max(crystals, 1 - crystalsOut); }
        float cold = S * FilmFx.ease((m - 35) / 45);
        float drawCover = Math.max(cover, lensCover);

        int sw = mc.getWindow().getWidth(), sh = mc.getWindow().getHeight();
        float asp = sh <= 0 ? 1.78f : sw / (float) sh;
        grid(asp, meterPres, lp, S);

        e.getGuiGraphics().flush();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        try {
            // 1) The world seen through the ice: refraction (the lens, the deep freeze), softness at the edges.
            float refr = S * (lp * .011f + (frozen ? .004f : 0) + brushK * .003f * meterPres) + .0025f * cold;
            float blur = S * Math.max(Math.max(FilmFx.ease((m - 22) / 50) * .85f, frozen ? 1 : 0), lp * .6f);
            if ((refr > .0005f || blur > .02f) && copyFrame(mc)) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.defaultBlendFunc();
                if (refr > .0005f) {
                    float bulge = .025f * lp * S;
                    for (int i = 0; i < NV; i++) {
                        float x = GXS[i], y = GYS[i], c = CORNER[i];
                        float k = refr * (.35f + .65f * c);
                        float ox = k * (Mth.sin(x * 11 + now * .05f + Mth.sin(y * 7)) * .5f + Mth.sin(y * 13 - now * .04f + x * 5) * .5f);
                        float oy = k * (Mth.cos(y * 10 + now * .045f + Mth.sin(x * 6)) * .5f + Mth.sin(x * 12 + now * .035f) * .5f);
                        float dx = x - .5f, dy = y - .5f, r2 = Math.min(1, (dx * dx + dy * dy) * 2);
                        float z = 1 - bulge * (1 - r2);
                        U[i] = .5f + dx * z + ox;
                        V[i] = 1 - (.5f + dy * z + oy);
                        R[i] = .94f; G[i] = .98f; B[i] = 1;
                        A[i] = Math.min(1, lp * (.55f + .45f * c) + (frozen ? .5f * EDGE[i] : 0) + brushK * .4f * EDGE[i] + .45f * cold * EDGE[i]);
                    }
                    RenderSystem.setShaderTexture(0, half.getColorTextureId());
                    draw(true);
                    // The cyan fringe: the same picture a hair further out, added faintly in cold blue at the edges.
                    float fringe = Math.min(.35f, .22f * cold + .12f * lp * S + (frozen ? .12f * Math.min(1, S) : 0));
                    if (fringe > .01f) {
                        for (int i = 0; i < NV; i++) {
                            float dx = GXS[i] - .5f, dy = GYS[i] - .5f;
                            U[i] += dx * .012f; V[i] -= dy * .012f;
                            R[i] = 0; G[i] = .5f; B[i] = .75f;
                            A[i] = fringe * EDGE[i] * EDGE[i];
                        }
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                        draw(true);
                        RenderSystem.defaultBlendFunc();
                    }
                }
                if (blur > .02f) {
                    float p = 3 - 2 * Math.min(1, blur);
                    for (int i = 0; i < NV; i++) {
                        U[i] = GXS[i]; V[i] = 1 - GYS[i];
                        R[i] = G[i] = B[i] = 1;
                        A[i] = Math.min(1, blur * (float) Math.pow(EDGE[i], p) * 1.1f);
                    }
                    RenderSystem.setShaderTexture(0, small.getColorTextureId());
                    draw(true);
                }
            }
            // 2) Colder: the picture multiplied toward ice blue, a slight vignette; a pale mist over the edges.
            float tint = S * Mth.clamp(m / 100f * .8f + (frozen ? .3f : 0) + lp * .4f, 0, 1.2f);
            if (tint > .01f) {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
                for (int i = 0; i < NV; i++) {
                    float ed = EDGE[i], a = Math.min(1, tint * (.45f + .55f * ed));
                    float vig = 1 - .32f * tint * ed * ed;
                    R[i] = (1 - .26f * a) * vig; G[i] = (1 - .1f * a) * vig; B[i] = vig; A[i] = 1;
                }
                draw(false);
                RenderSystem.defaultBlendFunc();
                for (int i = 0; i < NV; i++) { R[i] = .86f; G[i] = .93f; B[i] = 1; A[i] = Math.min(.5f, tint * .12f * EDGE[i]); }
                draw(false);
            }
            // 3) The frost: the atlas' frames drawn one over another up to the cover (the last one growing in).
            if (drawCover > .01f) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                texture(ATLAS);
                RenderSystem.defaultBlendFunc();
                float tone = frozen ? .93f : 1;
                frost(drawCover, 1, tone);
                if (brushK > .01f) {
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    frost(drawCover, .45f * brushK, 1);
                    RenderSystem.defaultBlendFunc();
                }
                sparkles(now, drawCover, asp, sw, sh, S * Math.max(meterPres, lp));
            }
            if (crystals > .01f) crystals(crystals, crystalsOut, asp, Math.min(1, S));
            // 4) The crack network over the ice near the end of a deep freeze.
            float net = frozen ? .9f * FilmFx.ease((deepAge - lateFrom()) / Math.max(4, deepTotal - lateFrom())) * Math.min(1, S) : 0;
            if (net > .01f) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                texture(NET);
                RenderSystem.defaultBlendFunc();
                for (int i = 0; i < NV; i++) { U[i] = GXS[i]; V[i] = GYS[i]; R[i] = G[i] = B[i] = 1; A[i] = net * (.4f + .6f * EDGE[i]); }
                draw(true);
            }
            // 5) The cracks running over it.
            List<Crack> list = new ArrayList<>(8);
            List<float[]> how = new ArrayList<>(8);
            if (edgeCracks != null && !frozen && m > 85) {
                float k = FilmFx.ease((m - 85) / 15);
                for (Crack c : edgeCracks) { list.add(c); how.add(new float[]{k, k * S}); }
            }
            if (deepCracks != null && (frozen || breaking)) {
                float fade = frozen ? 1 : 1 - Mth.clamp(bt / 4, 0, 1);
                float early = FilmFx.ease((deepAge - DEEP_SEIZE * .6f) / 7), late = FilmFx.ease((deepAge - lateFrom()) / Math.max(4, deepTotal - lateFrom()));
                float reveal = frozen ? .6f * early + .4f * late : 1;
                for (Crack c : deepCracks) { list.add(c); how.add(new float[]{reveal, fade * Math.min(1, S)}); }
            }
            if (lensCracks != null && lp > .01f) {
                float reveal = FilmFx.ease((lt - 1) / 8);
                for (Crack c : lensCracks) { list.add(c); how.add(new float[]{reveal, Math.min(1, lp) * S}); }
            }
            if (!list.isEmpty()) cracks(list, how, sw, sh);
            // 6) Condensation on the lens.
            if (lp > .01f) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                texture(DROPS);
                RenderSystem.defaultBlendFunc();
                float slide = lt * .0012f;
                for (int i = 0; i < NV; i++) { U[i] = GXS[i]; V[i] = GYS[i] - slide; R[i] = G[i] = B[i] = 1; A[i] = Math.min(1, lp * S * (.25f + .75f * EDGE[i])); }
                draw(true);
            }
            // 7) The ice over the view breaking: its pieces fall away.
            if (breaking) pieces(bt, asp, S);
            // 8) The flash.
            if (flashK > .005f) {
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                for (int i = 0; i < NV; i++) { R[i] = .9f; G[i] = .96f; B[i] = 1; A[i] = Math.min(1, flashK * Math.min(1, S) * (.7f + .3f * EDGE[i])); }
                draw(false);
            }
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }
    /**
     * The ice crystals grown in the four corners (the one texture mirrored into each), scaled out from the corner by how
     * far they have grown (each corner its own size), going clear and sliding off as they fall away (out 0..1).
     */
    private static void crystals(float grow, float out, float asp, float k) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        texture(CRYSTALS);
        RenderSystem.defaultBlendFunc();
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[] own = {1f, .86f, .93f, .8f};
        float a = Math.min(1, grow * 2.2f) * (1 - out) * k;
        for (int corner = 0; corner < 4; corner++) {
            // The share of the screen's height the corner's square reaches: up to about 0.4, never the middle.
            float size = .42f * own[corner] * (.25f + .75f * FilmFx.ease(grow)) * (1 + .12f * out);
            float sx = size * 2 / asp, sy = size * 2;
            boolean right = (corner & 1) == 1, bottom = (corner & 2) != 0;
            float x0 = right ? 1 : -1, y0 = bottom ? -1 : 1;
            float dx = right ? -sx : sx, dy = bottom ? sy : -sy;
            float drop = -.15f * out;
            // Corner, along the top/bottom edge, the far corner, along the side edge (uv: the texture's own corner at 0, 0).
            buf.vertex(x0, y0 + drop, 0).uv(0, 0).color(1f, 1f, 1f, a).endVertex();
            buf.vertex(x0 + dx, y0 + drop, 0).uv(1, 0).color(1f, 1f, 1f, a).endVertex();
            buf.vertex(x0 + dx, y0 + dy + drop, 0).uv(1, 1).color(1f, 1f, 1f, a).endVertex();
            buf.vertex(x0, y0 + dy + drop, 0).uv(0, 1).color(1f, 1f, 1f, a).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
    }
    private static float lateFrom() { return Math.max(DEEP_SEIZE + 4, deepTotal * .62f); }

    /** The grid's points: where (0..1, y down), how near an edge (1 at the edges, 0 in the middle), how near a corner, the frost's mask. */
    private static void grid(float asp, float meterPres, float lp, float S) {
        for (int j = 0, i = 0; j <= GY; j++) for (int k = 0; k <= GX; k++, i++) {
            float x = k / (float) GX, y = j / (float) GY;
            GXS[i] = x; GYS[i] = y;
            float ex = Math.max(1e-3f, Math.min(x, 1 - x) * asp), ey = Math.max(1e-3f, Math.min(y, 1 - y));
            float d = (float) Math.pow(Math.pow(ex, -2.2) + Math.pow(ey, -2.2), -1 / 2.2);
            EDGE[i] = 1 - FilmFx.ease(d / .5f);
            float dx = Math.abs(x - .5f) * 2, dy = Math.abs(y - .5f) * 2;
            float c = Mth.clamp((dx * dx + dy * dy - .35f) / 1.35f, 0, 1);
            CORNER[i] = c * c * (3 - 2 * c);
            MASK[i] = Math.min(1, Math.max(meterPres, lp * (.25f + .75f * CORNER[i])) * S);
        }
    }
    /** The frost frames up to cover (the last one at its share), alpha k, colour tone. */
    private static void frost(float cover, float k, float tone) {
        int full = (int) Math.floor(cover);
        float frac = cover - full;
        for (int f = 0; f < FRAMES && f <= full; f++) {
            float a = f < full ? 1 : frac;
            if (a <= .004f) continue;
            float u0 = (f % COLS) / (float) COLS, v0 = (f / COLS) / (float) ROWS;
            float iu = .5f / (640 * COLS), iv = .5f / (360 * ROWS);
            for (int i = 0; i < NV; i++) {
                U[i] = u0 + iu + GXS[i] * (1f / COLS - 2 * iu);
                V[i] = v0 + iv + GYS[i] * (1f / ROWS - 2 * iv);
                R[i] = tone; G[i] = tone + (1 - tone) * .5f; B[i] = 1;
                A[i] = MASK[i] * a * k;
            }
            draw(true);
        }
    }
    /** Little glints twinkling in the frost near the edges. */
    private static void sparkles(float now, float cover, float asp, int sw, int sh, float k) {
        if (k <= .01f || sw <= 0 || sh <= 0) return;
        float reach = REACH[Mth.clamp((int) cover, 0, FRAMES - 1)] / 360f;
        int n = Math.min(40, (int) (8 + cover * 2.4f));
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float px = 2f / sw, py = 2f / sh;
        for (int i = 0; i < n; i++) {
            float period = 20 + (i % 5) * 5;
            float ph = (float) FilmFx.hash(i * 7.13), u = now / period + ph;
            float cyc = (float) Math.floor(u), f = u - cyc;
            float h1 = (float) FilmFx.hash(i * 13.7 + cyc * 3.1), h2 = (float) FilmFx.hash(i * 5.3 + cyc * 7.7), h3 = (float) FilmFx.hash(i * 2.9 + cyc * 11.3);
            float d = h2 * reach * .85f;
            float x, y;
            switch ((int) (h1 * 4)) {
                case 0 -> { x = h3; y = d; }
                case 1 -> { x = h3; y = 1 - d; }
                case 2 -> { x = d / asp; y = h3; }
                default -> { x = 1 - d / asp; y = h3; }
            }
            float a = Mth.sin(f * Mth.PI); a = a * a * a * a * k * .9f;
            if (a < .01f) continue;
            float s = (3 + 6 * (float) FilmFx.hash(i * 3.3 + cyc)) * Math.max(1, sh / 720f);
            float cx = x * 2 - 1, cy = 1 - y * 2;
            star(buf, cx, cy, s * px, .7f * py, a);
            star(buf, cx, cy, .7f * px, s * py, a);
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.defaultBlendFunc();
    }
    private static void star(BufferBuilder buf, float cx, float cy, float rx, float ry, float a) {
        buf.vertex(cx - rx, cy, 0).color(.8f, .93f, 1f, 0f).endVertex();
        buf.vertex(cx, cy - ry, 0).color(.8f, .93f, 1f, a).endVertex();
        buf.vertex(cx + rx, cy, 0).color(.8f, .93f, 1f, 0f).endVertex();
        buf.vertex(cx, cy + ry, 0).color(.8f, .93f, 1f, a).endVertex();
    }
    /** Draws the grid with the per-point U, V, R, G, B, A (textured or plain colour; the shader and blend set by the caller). */
    private static void draw(boolean tex) {
        boolean any = false;
        for (int i = 0; i < NV && !any; i++) any = A[i] > .002f;
        if (!any) return;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, tex ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR);
        for (int j = 0; j < GY; j++) for (int k = 0; k < GX; k++) {
            int a = j * (GX + 1) + k, b = a + 1, c = a + GX + 2, d = a + GX + 1;
            // Bottom-left, bottom-right, top-right, top-left on the screen (y runs down in the grid).
            put(buf, d, tex); put(buf, c, tex); put(buf, b, tex); put(buf, a, tex);
        }
        BufferUploader.drawWithShader(buf.end());
    }
    private static void put(BufferBuilder buf, int i, boolean tex) {
        buf.vertex(GXS[i] * 2 - 1, 1 - GYS[i] * 2, 0);
        if (tex) buf.uv(U[i], V[i]);
        buf.color(Math.min(1, R[i]), Math.min(1, G[i]), Math.min(1, B[i]), Mth.clamp(A[i], 0, 1)).endVertex();
    }
    private static void texture(ResourceLocation id) {
        Minecraft.getInstance().getTextureManager().getTexture(id).setFilter(true, false);
        RenderSystem.setShaderTexture(0, id);
    }
    /** A half-size copy of the frame (the refraction) and a sixth-size one from it (the blur: linear filtering softens it). */
    private static boolean copyFrame(Minecraft mc) {
        if (copyFailed) return false;
        try {
            RenderTarget main = mc.getMainRenderTarget();
            int w = Math.max(1, main.width / 2), h = Math.max(1, main.height / 2);
            int w6 = Math.max(1, main.width / 6), h6 = Math.max(1, main.height / 6);
            if (half == null) half = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            else if (half.width != w || half.height != h) half.resize(w, h, Minecraft.ON_OSX);
            if (small == null) small = new TextureTarget(w6, h6, false, Minecraft.ON_OSX);
            else if (small.width != w6 || small.height != h6) small.resize(w6, h6, Minecraft.ON_OSX);
            half.setFilterMode(GL11.GL_LINEAR);
            small.setFilterMode(GL11.GL_LINEAR);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, half.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, half.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, small.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w6, h6, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            main.bindWrite(true);
            return true;
        } catch (Throwable ex) {
            copyFailed = true;
            mc.getMainRenderTarget().bindWrite(true);
            LOG.warn("Iceman frost: could not copy the frame for the frosted glass; the frost is drawn without it", ex);
            return false;
        }
    }

    // ------------------------------------------------------------------ cracks
    /** A crack: segments (x0, y0, x1, y1 on the screen 0..1, the distance along it at each end, its branch depth). */
    private record Crack(float[] seg, int n, float total) {}
    private static Crack fromEdge(Random r, float len) {
        int side = r.nextInt(4);
        float t = .1f + .8f * r.nextFloat();
        float x = side == 0 ? t : side == 1 ? t : side == 2 ? 0 : 1, y = side == 0 ? 0 : side == 1 ? 1 : t;
        float ang = side == 0 ? Mth.HALF_PI : side == 1 ? -Mth.HALF_PI : side == 2 ? 0 : Mth.PI;
        return crack(r, x, y, ang + (r.nextFloat() - .5f), len);
    }
    private static Crack fromCorner(Random r, float len) {
        int c = r.nextInt(4);
        float x = (c & 1) == 0 ? .02f * r.nextFloat() : 1 - .02f * r.nextFloat(), y = (c & 2) == 0 ? .03f * r.nextFloat() : 1 - .03f * r.nextFloat();
        float ang = (float) Math.atan2(.5f - y, (.5f - x) * 1.78f) + (r.nextFloat() - .5f) * .7f;
        return crack(r, x, y, ang, len);
    }
    /** A jagged crack (in a 16:9 space, stored 0..1) with branches of branches. */
    private static Crack crack(Random r, float x, float y, float ang, float len) {
        List<float[]> segs = new ArrayList<>();
        walk(r, segs, x * 1.78f, y, ang, len, 0, 0);
        float[] out = new float[segs.size() * 7];
        float total = 0;
        for (int i = 0; i < segs.size(); i++) {
            float[] s = segs.get(i);
            s[0] /= 1.78f; s[2] /= 1.78f;
            System.arraycopy(s, 0, out, i * 7, 7);
            total = Math.max(total, s[5]);
        }
        return new Crack(out, segs.size(), Math.max(1e-3f, total));
    }
    private static void walk(Random r, List<float[]> segs, float x, float y, float ang, float len, float d, int depth) {
        float step = .022f;
        int n = Math.max(1, (int) (len / step));
        for (int i = 0; i < n && segs.size() < 160; i++) {
            ang += (r.nextFloat() - .5f) * .6f;
            float nx = x + Mth.cos(ang) * step, ny = y + Mth.sin(ang) * step;
            segs.add(new float[]{x, y, nx, ny, d, d + step, depth});
            x = nx; y = ny; d += step;
            if (depth < 2 && r.nextFloat() < .18f)
                walk(r, segs, x, y, ang + (r.nextBoolean() ? 1 : -1) * (.5f + .6f * r.nextFloat()), len * .45f * (1 - i / (float) n) + .03f, d, depth + 1);
        }
    }
    /** Draws cracks (how: {reveal 0..1 of its length, alpha}): a dark shadow line, a bright edge line, a glint at the front. */
    private static void cracks(List<Crack> list, List<float[]> how, int sw, int sh) {
        if (sw <= 0 || sh <= 0) return;
        float px = 2f / sw, py = 2f / sh, scale = Math.max(1, sh / 720f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) RenderSystem.defaultBlendFunc();
            else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            BufferBuilder buf = Tesselator.getInstance().getBuilder();
            buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (int c = 0; c < list.size(); c++) {
                Crack cr = list.get(c);
                float reveal = how.get(c)[0] * cr.total(), alpha = Math.min(1, how.get(c)[1]);
                if (reveal <= 0 || alpha <= .01f) continue;
                float[] s = cr.seg();
                for (int i = 0; i < cr.n(); i++) {
                    int o = i * 7;
                    float d0 = s[o + 4], d1 = s[o + 5];
                    if (d0 >= reveal) continue;
                    float k = d1 <= reveal ? 1 : (reveal - d0) / (d1 - d0);
                    float x0 = s[o] * 2 - 1, y0 = 1 - s[o + 1] * 2;
                    float x1 = Mth.lerp(k, s[o], s[o + 2]) * 2 - 1, y1 = 1 - Mth.lerp(k, s[o + 1], s[o + 3]) * 2;
                    float w = (s[o + 6] == 0 ? 1 : s[o + 6] == 1 ? .7f : .5f) * scale * (1 - .4f * d0 / cr.total());
                    if (pass == 0) seg(buf, x0, y0, x1, y1, w * 1.6f, px, py, .1f, .2f, .32f, .5f * alpha, 0, 0);
                    else seg(buf, x0, y0, x1, y1, w * .75f, px, py, .82f, .93f, 1f, .85f * alpha, -px * scale, py * scale);
                    if (pass == 1 && k < 1) { star(buf, x1, y1, 6 * scale * px, .7f * py, alpha); star(buf, x1, y1, .7f * px, 6 * scale * py, alpha); }
                }
            }
            BufferUploader.drawWithShader(buf.end());
        }
        RenderSystem.defaultBlendFunc();
    }
    private static void seg(BufferBuilder buf, float x0, float y0, float x1, float y1, float w, float px, float py, float r, float g, float b, float a, float ox, float oy) {
        // The perpendicular in pixels, so the line is as wide whatever its direction.
        float dx = (x1 - x0) / px, dy = (y1 - y0) / py, l = Mth.sqrt(dx * dx + dy * dy);
        if (l < 1e-4f) return;
        float nx = -dy / l * w * px, ny = dx / l * w * py;
        buf.vertex(x0 - nx + ox, y0 - ny + oy, 0).color(r, g, b, a).endVertex();
        buf.vertex(x1 - nx + ox, y1 - ny + oy, 0).color(r, g, b, a).endVertex();
        buf.vertex(x1 + nx + ox, y1 + ny + oy, 0).color(r, g, b, a).endVertex();
        buf.vertex(x0 + nx + ox, y0 + ny + oy, 0).color(r, g, b, a).endVertex();
    }

    // ------------------------------------------------------------------ the ice over the view breaking
    /** A piece of the screen's ice: its three corners at rest (0..1), its middle, how it flies and turns, when it lets go. */
    private record Shard(float[] x, float[] y, float cx, float cy, float vx, float vy, float spin, float delay) {}
    private static Shard[] shards(Random r) {
        int nx = 8, ny = 5;
        float[][] px = new float[ny + 1][nx + 1], py = new float[ny + 1][nx + 1];
        for (int j = 0; j <= ny; j++) for (int i = 0; i <= nx; i++) {
            boolean edgeX = i == 0 || i == nx, edgeY = j == 0 || j == ny;
            px[j][i] = i / (float) nx + (edgeX ? 0 : (r.nextFloat() - .5f) * .7f / nx);
            py[j][i] = j / (float) ny + (edgeY ? 0 : (r.nextFloat() - .5f) * .7f / ny);
        }
        List<Shard> out = new ArrayList<>();
        for (int j = 0; j < ny; j++) for (int i = 0; i < nx; i++) {
            boolean flip = r.nextBoolean();
            float[][] tris = flip
                    ? new float[][]{{px[j][i], py[j][i], px[j][i + 1], py[j][i + 1], px[j + 1][i + 1], py[j + 1][i + 1]}, {px[j][i], py[j][i], px[j + 1][i + 1], py[j + 1][i + 1], px[j + 1][i], py[j + 1][i]}}
                    : new float[][]{{px[j][i], py[j][i], px[j][i + 1], py[j][i + 1], px[j + 1][i], py[j + 1][i]}, {px[j][i + 1], py[j][i + 1], px[j + 1][i + 1], py[j + 1][i + 1], px[j + 1][i], py[j + 1][i]}};
            for (float[] t : tris) {
                float cx = (t[0] + t[2] + t[4]) / 3, cy = (t[1] + t[3] + t[5]) / 3;
                float vx = (cx - .5f) * .02f + (r.nextFloat() - .5f) * .006f, vy = (cy - .5f) * .008f - .004f - r.nextFloat() * .006f;
                out.add(new Shard(new float[]{t[0], t[2], t[4]}, new float[]{t[1], t[3], t[5]}, cx, cy, vx, vy, (r.nextFloat() - .5f) * .2f, r.nextFloat() * 2.5f));
            }
        }
        return out.toArray(new Shard[0]);
    }
    private static void pieces(float bt, float asp, float S) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        texture(ATLAS);
        RenderSystem.defaultBlendFunc();
        int full = (int) Math.floor(breakCover);
        float frac = breakCover - full;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float iu = .5f / (640 * COLS), iv = .5f / (360 * ROWS);
        float[] sx = new float[3], sy = new float[3];
        for (Shard s : shards) {
            float t = Math.max(0, bt - s.delay());
            float a = (1 - FilmFx.ease(t / 16)) * breakPresence * Math.min(1, S);
            if (a <= .01f) continue;
            float th = s.spin() * t, cos = Mth.cos(th), sin = Mth.sin(th);
            float mx = s.vx() * t, my = s.vy() * t + .5f * .0045f * t * t;
            for (int v = 0; v < 3; v++) {
                float dx = (s.x()[v] - s.cx()) * asp, dy = s.y()[v] - s.cy();
                sx[v] = s.cx() + (dx * cos - dy * sin) / asp + mx;
                sy[v] = s.cy() + dx * sin + dy * cos + my;
            }
            for (int f = 0; f < FRAMES && f <= full; f++) {
                float fa = (f < full ? 1 : frac) * a;
                if (fa <= .004f) continue;
                float u0 = (f % COLS) / (float) COLS, v0 = (f / COLS) / (float) ROWS;
                for (int v = 0; v < 4; v++) {
                    int q = Math.min(v, 2);
                    buf.vertex(sx[q] * 2 - 1, 1 - sy[q] * 2, 0)
                            .uv(u0 + iu + s.x()[q] * (1f / COLS - 2 * iu), v0 + iv + s.y()[q] * (1f / ROWS - 2 * iv))
                            .color(.95f, .98f, 1f, fa).endVertex();
                }
            }
        }
        BufferUploader.drawWithShader(buf.end());
    }

    // ------------------------------------------------------------------ the ears
    /** The world's sounds while frozen over: quieter and duller (not music, not Iceman's own ice cracking round them). */
    @SubscribeEvent public static void muffle(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null || s instanceof TickableSoundInstance) return;
        float m = muffled();
        if (m < .03f) return;
        SoundSource src = s.getSource();
        if (src == SoundSource.MUSIC || src == SoundSource.RECORDS || src == SoundSource.MASTER) return;
        ResourceLocation loc = s.getLocation();
        if (loc != null && (loc.getPath().startsWith("iceman") || loc.getPath().contains("glass"))) return;
        e.setSound(new Muffled(s, 1 - .7f * m, 1 - .12f * m));
    }
    private static final class Muffled implements SoundInstance {
        private final SoundInstance s; private final float volume, pitch;
        Muffled(SoundInstance s, float volume, float pitch) { this.s = s; this.volume = volume; this.pitch = pitch; }
        @Override public ResourceLocation getLocation() { return s.getLocation(); }
        @Override public WeighedSoundEvents resolve(SoundManager m) { return s.resolve(m); }
        @Override public Sound getSound() { return s.getSound(); }
        @Override public SoundSource getSource() { return s.getSource(); }
        @Override public boolean isLooping() { return s.isLooping(); }
        @Override public boolean isRelative() { return s.isRelative(); }
        @Override public int getDelay() { return s.getDelay(); }
        @Override public float getVolume() { return s.getVolume() * volume; }
        @Override public float getPitch() { return s.getPitch() * pitch; }
        @Override public double getX() { return s.getX(); }
        @Override public double getY() { return s.getY(); }
        @Override public double getZ() { return s.getZ(); }
        @Override public SoundInstance.Attenuation getAttenuation() { return s.getAttenuation(); }
        public boolean canStartSilent() { return s.canStartSilent(); }
        public boolean canPlaySound() { return s.canPlaySound(); }
    }
}
