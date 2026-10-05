package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Batman's flash grenade on the client: two separate things.
 * <p>
 * A) The light, for everyone who can see it: a small white core and a short radial glow, a pressure ring, and a real
 * pulse of light 0 → blinding → 0 in about half a second. Walls, floor and ceiling round it take a white splash (rays
 * from the burst find the faces, brighter the nearer), and the world's light map itself is lifted for the moment so
 * bodies, blocks and everything else in view brighten too (more the nearer the camera is and if it sees the burst).
 * <p>
 * B) The white-out, only for a player the server says it got (FX_FLASHED: within the blind radius and in sight of it):
 * pure white at once, then the view comes back slowly: about 0.2 s still white, 0.5 s faint shapes through the glare,
 * 1 s clearer, 1.5 s nearly back, normal by 2 s; not a flat fill but a white haze → washed-out, overexposed picture
 * (a soft blur and bloom of the real picture, lowered contrast) → normal. The ears: a loud high ringing (it fades loud
 * → medium → low → gone) and the world's sounds come through heavily muffled (quieter and duller), coming back with
 * the sight. Behind a wall it is only a dim glare and a little ringing; the thrower himself gets at most a soft ring.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanFlashFx {
    private static final Logger LOG = LogUtils.getLogger();

    private BatmanFlashFx() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }

    // ------------------------------------------------------------------ A) the light
    private record Surface(Vec3 at, Vec3 normal, double distance) {}
    private record Burst(Vec3 at, float start, List<Surface> faces, float seen) {}
    private static final List<Burst> BURSTS = new ArrayList<>();
    /** How far the light's rays look for faces round the burst, and how long the pulse lasts (ticks). */
    private static final double REACH = 18;
    private static final float PULSE = 18;
    private static final int WHITE = 0xfff8ee, COOL = 0xe6eeff, RAYS = 72;

    /** The light 0..1 `age` ticks after the burst: full in about a tick, then falling away fast. */
    static float pulse(float age) {
        if (age < 0 || age > PULSE) return 0;
        if (age < 1.1f) { float u = age / 1.1f; return u * u * (3 - 2 * u); }
        // A held instant of full light (CS2: the room goes white), then a long-ish falling tail.
        if (age < 3) return 1;
        return (float) Math.exp(-(age - 3) / 3.4f) * (1 - (age - 3) / (PULSE - 3));
    }

    /** The grenade went off at `at` (everyone near gets this). */
    static void burst(Vec3 at) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        List<Surface> faces = new ArrayList<>();
        // Rays every way (the cube's faces, edges and corners): the faces round it that the light falls on.
        // Rays evenly every way (a spiral over the sphere): the faces round it that the light falls on.
        for (int i = 0; i < RAYS; i++) {
            double yy = 1 - 2 * (i + .5) / RAYS, rr = Math.sqrt(1 - yy * yy), a = i * 2.399963;
            Vec3 d = new Vec3(Math.cos(a) * rr, yy, Math.sin(a) * rr);
            BlockHitResult hit = mc.level.clip(new ClipContext(at, at.add(d.scale(REACH)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) continue;
            faces.add(new Surface(hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getNormal()), hit.getLocation().distanceTo(at)));
        }
        // How much of it this camera takes: near and in sight fully, round a corner some (the light fills the room).
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(at);
        boolean inSight = mc.level.clip(new ClipContext(cam, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
        float seen = (float) Mth.clamp(1 - (d - 8) / 34, 0, 1) * (inSight ? 1 : .55f);
        BURSTS.add(new Burst(at, now(), faces, seen));
    }

    /** The world's light lifted now (0..1), from every burst going on. */
    private static float worldLight(float t) {
        float k = 0;
        for (Burst b : BURSTS) k = Math.max(k, pulse(t - b.start()) * b.seen());
        return k;
    }

    @SubscribeEvent public static void light(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) { lightMap(); return; }
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BURSTS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        float t = now();
        BURSTS.removeIf(b -> t - b.start() > PULSE + 8);
        if (BURSTS.isEmpty() || mc.level == null) return;
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        var fx = FilmFx.batched();
        FilmContext c = new FilmContext(p, fx, cam, new Vec3(rr.x, rr.y, rr.z), new Vec3(uu.x, uu.y, uu.z), t, 0, e.getPartialTick());
        try {
            for (Burst b : BURSTS) {
                float age = t - b.start(), k = pulse(age);
                if (k > .005f) {
                    // The light on the faces round it, brighter and tighter the nearer.
                    for (Surface s : b.faces()) {
                        float d = (float) s.distance();
                        float alpha = 2.4f * k / (1 + .035f * d * d);
                        if (alpha <= .01f) continue;
                        Vec3 on = s.at().add(s.normal().scale(.02));
                        BatmanCannonFx.splash(c, on, s.normal(), 2.2 + 1.0 * d, WHITE, Math.min(1, alpha));
                        // Past full white the light spreads wider over the surface (a second, broader layer).
                        if (alpha > 1) BatmanCannonFx.splash(c, on, s.normal(), 3.4 + 1.5 * d, WHITE, Math.min(1, alpha - 1));
                    }
                    // The core (small) and the radial glow (short).
                    FilmFx.glow(c, b.at(), .9 + .5 * k, 0xffffff, k);
                    FilmFx.glow(c, b.at(), 4.5 * (.6 + .4 * k), WHITE, k);
                    FilmFx.glow(c, b.at(), 14, COOL, .55f * k);
                    FilmFx.glow(c, b.at(), 26, WHITE, .22f * k);
                }
                // The pressure: one quick shell of air, a faint ring along the ground.
                if (age < 7) {
                    float u = age / 7;
                    FilmFx.ring(c, b.at(), .3 + 3.4 * Math.sqrt(u), .18 * (1 - u), COOL, .4f * (1 - u), true);
                    FilmFx.ring(c, b.at().add(0, -.08, 0), .3 + 4.2 * Math.sqrt(u), .25 * (1 - u), 0xd8d2c8, .3f * (1 - u), false);
                }
            }
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }

    // the light map: found once by type (the names differ outside the dev game), lifted toward white during a pulse
    private static Field pixelsField, textureField, dirtyField;
    private static boolean lightFailed, lifted;
    /**
     * Right after the sky, before any block or body is drawn, the light map (made fresh this frame) is lifted toward
     * white by the pulse; the game is told to make it fresh again next frame, so the lift never piles up.
     */
    private static void lightMap() {
        if (lightFailed) return;
        float k = worldLight(now());
        if (k < .004f && !lifted) return;
        try {
            LightTexture lt = Minecraft.getInstance().gameRenderer.lightTexture();
            if (pixelsField == null) {
                for (Field f : LightTexture.class.getDeclaredFields()) {
                    if (f.getType() == NativeImage.class) pixelsField = f;
                    else if (f.getType() == DynamicTexture.class) textureField = f;
                    else if (f.getType() == boolean.class && dirtyField == null) dirtyField = f;
                }
                if (pixelsField == null || textureField == null || dirtyField == null) throw new IllegalStateException("light map fields not found");
                pixelsField.setAccessible(true); textureField.setAccessible(true); dirtyField.setAccessible(true);
            }
            dirtyField.setBoolean(lt, true);
            if (k < .004f) { lifted = false; return; }
            NativeImage px = (NativeImage) pixelsField.get(lt);
            float lift = Math.min(.97f, k * 1.1f);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int c = px.getPixelRGBA(x, y);
                int a = c >>> 24, c2 = c >> 16 & 255, c1 = c >> 8 & 255, c0 = c & 255;
                c2 += (int) ((255 - c2) * lift); c1 += (int) ((255 - c1) * lift); c0 += (int) ((255 - c0) * lift * .97f);
                px.setPixelRGBA(x, y, a << 24 | c2 << 16 | c1 << 8 | c0);
            }
            ((DynamicTexture) textureField.get(lt)).upload();
            lifted = true;
        } catch (Throwable ex) {
            lightFailed = true;
            LOG.warn("Batman flash grenade: could not lift the light map; the burst lights only the faces round it", ex);
        }
    }

    // ------------------------------------------------------------------ B) the white-out and the ears
    private static final int FULL = 0, THROWER = 1, BEHIND_WALL = 2;
    private static float blindStart = -1000, blindTicks = 1, blindPower;
    private static int blindMode = -1;
    private static Ring ring;

    /** The server says the flash got this player (see FX_FLASHED). */
    static void flashed(float power, float ticks, boolean thrower, boolean behindWall) {
        blindMode = thrower ? THROWER : behindWall ? BEHIND_WALL : FULL;
        blindStart = now();
        blindPower = Mth.clamp(power, 0, 1);
        blindTicks = Math.max(8, ticks);
        var sounds = Minecraft.getInstance().getSoundManager();
        if (ring == null || ring.isStopped() || !sounds.isActive(ring)) { ring = new Ring(); sounds.play(ring); }
    }
    static void clear() { blindStart = -1000; blindMode = -1; BURSTS.clear(); }
    /** 0..1 through the recovery (1 = over). */
    private static float recovery(float stretch) { return Mth.clamp((now() - blindStart) / (blindTicks * stretch), 0, 1); }
    /** How loud the ringing is now. */
    private static float ringing() {
        if (blindMode < 0) return 0;
        float x = recovery(blindMode == FULL ? 1.25f : 1);
        if (x >= 1) return 0;
        return switch (blindMode) {
            case FULL -> (.45f + .55f * blindPower) * (float) Math.pow(1 - x, 1.35);
            case THROWER -> .14f * (1 - x) * (1 - x);
            default -> .25f * (1 - x);
        };
    }
    /** How muffled the world sounds now (0 = normal). */
    private static float muffled() {
        if (blindMode < 0 || blindMode == THROWER) return 0;
        float x = recovery(1.1f);
        return x >= 1 ? 0 : (blindMode == FULL ? .5f + .5f * blindPower : .3f) * (float) Math.pow(1 - x, 1.1);
    }

    /** The ringing: one looping tone whose loudness follows the recovery; it stops itself when it has faded. */
    private static final class Ring extends AbstractTickableSoundInstance {
        Ring() {
            super(ModSounds.BATMAN_FLASH_RING.get(), SoundSource.MASTER, RandomSource.create());
            looping = true; delay = 0; relative = true; attenuation = Attenuation.NONE; volume = Math.max(.001f, ringing()); pitch = 1;
        }
        @Override public void tick() {
            float v = ringing();
            if (v <= .002f) { stop(); return; }
            volume = v;
            // The tone sags a touch as it fades.
            pitch = .97f + .03f * Math.min(1, v * 1.5f);
        }
    }
    /** The world's sounds while deafened: quieter and duller (not the ringing, not music, not the menus). */
    @SubscribeEvent public static void muffle(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null || s instanceof TickableSoundInstance) return;
        float m = muffled();
        if (m < .03f) return;
        SoundSource src = s.getSource();
        if (src == SoundSource.MUSIC || src == SoundSource.RECORDS || src == SoundSource.MASTER) return;
        e.setSound(new Muffled(s, 1 - .78f * m, 1 - .14f * m));
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

    // the picture: a small copy of the frame (its blur for free) laid back over it, then the haze and the white
    private static TextureTarget small;
    private static boolean copyFailed;

    /**
     * Before the HUD (it stays readable, as in the games): the frame softened and bloomed from a third-size copy of
     * itself, washed toward a pale grey (contrast and colour going), lifted (overexposed), and the white over it all.
     */
    @SubscribeEvent public static void screen(RenderGuiEvent.Pre e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // Everyone who sees a burst (blinded or not, Batman too): the whole picture blazes up for the moment of the
        // light, as the eye takes it in (CS2's flash lights the room for the ones it misses too).
        float glare = Math.min(.75f, .8f * worldLight(now()));
        boolean blinded = blindMode == FULL || blindMode == BEHIND_WALL;
        float x = blinded ? recovery(1) : 1;
        if (x >= 1 && glare < .01f) return;
        float white = 0, wash = 0, expose = 0, blur = 0, bloom = 0;
        if (x >= 1) { expose = glare; bloom = .5f * glare; }
        else if (blindMode == FULL) {
            // Solid white first (as in the games), then the shapes come back slowly through the glare.
            float y = Mth.clamp((x - .14f) / .86f, 0, 1);
            white = (float) Math.pow(1 - y, 1.45);
            wash = .36f * (float) Math.pow(1 - y, 1.1);
            expose = .42f * (float) Math.pow(1 - y, .85);
            blur = .6f * (float) Math.pow(1 - y, 1.4);
            bloom = .35f * (float) Math.pow(1 - y, 1.2);
        } else {
            float y = 1 - x;
            white = .28f * y * y; wash = .1f * y; expose = .22f * y; blur = .2f * y; bloom = .12f * y;
        }
        expose = Math.max(expose, glare);
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
            if ((blur > .01f || bloom > .01f) && copyFrame(mc)) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, small.getColorTextureId());
                RenderSystem.defaultBlendFunc();
                frame(blur);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                frame(bloom);
            }
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.defaultBlendFunc();
            fill(.86f, .87f, .9f, wash);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            fill(1, .99f, .96f, expose);
            RenderSystem.defaultBlendFunc();
            fill(1, 1, 1, Math.min(1, white));
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
    /** A third-size copy of the frame as it is now (linear filtering: drawn back full size it is softly blurred). */
    private static boolean copyFrame(Minecraft mc) {
        if (copyFailed) return false;
        try {
            RenderTarget main = mc.getMainRenderTarget();
            int w = Math.max(1, main.width / 3), h = Math.max(1, main.height / 3);
            if (small == null) small = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            else if (small.width != w || small.height != h) small.resize(w, h, Minecraft.ON_OSX);
            small.setFilterMode(GL11.GL_LINEAR);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, small.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            main.bindWrite(true);
            return true;
        } catch (Throwable ex) {
            copyFailed = true;
            mc.getMainRenderTarget().bindWrite(true);
            LOG.warn("Batman flash grenade: could not copy the frame for the glare; plain white is used", ex);
            return false;
        }
    }
    private static void frame(float a) {
        if (a <= .005f) return;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(-1, -1, 0).uv(0, 0).color(1f, 1f, 1f, a).endVertex();
        buf.vertex(1, -1, 0).uv(1, 0).color(1f, 1f, 1f, a).endVertex();
        buf.vertex(1, 1, 0).uv(1, 1).color(1f, 1f, 1f, a).endVertex();
        buf.vertex(-1, 1, 0).uv(0, 1).color(1f, 1f, 1f, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }
    private static void fill(float r, float g, float b, float a) {
        if (a <= .005f) return;
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buf.vertex(-1, -1, 0).color(r, g, b, a).endVertex();
        buf.vertex(1, -1, 0).color(r, g, b, a).endVertex();
        buf.vertex(1, 1, 0).color(r, g, b, a).endVertex();
        buf.vertex(-1, 1, 0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buf.end());
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}
