package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import java.lang.reflect.Field;

/**
 * Plays a film for the local player. Every frame it moves the camera along the current shot,
 * and for virtual segments it covers the world with the film's own stage: background, scene
 * geometry and actors drawn by the film. Segments fade through a colour into each other.
 * Input is locked and the game HUD hidden; there are no letterbox bars.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class FilmDirector {
    private static Film film;
    private static CameraType savedCamera;
    private static Field cameraPosition;
    private static float lastCueTime = -1;
    /** The film's sound cues, built once when it starts (building them every frame churned the garbage collector). */
    private static Film.Cue[] cueCache = new Film.Cue[0];
    private static Film cueOwner;
    /** Camera position in stage space for the current frame (virtual segments). */
    private static Vec3 stageCamera = Vec3.ZERO;
    /** Camera angles used this frame on the virtual stage (the backdrop shader rebuilds its rays from them). */
    private static float stageYaw, stagePitch;
    private static boolean drawingStage;

    public static void play(Film next) {
        if (film == null || film.getClass() != next.getClass()) lastCueTime = -1;
        film = next;
    }
    public static boolean playing() { return film != null && film.active(); }
    /** True while a virtual stage is being drawn (world-only hooks should stand aside). */
    public static boolean drawingStage() { return drawingStage; }

    static float ease(float t) { t = Math.max(0, Math.min(1, t)); return t * t * t * (t * (t * 6 - 15) + 10); }
    private static float blend(Film f, float t) {
        return ease(t / f.blendIn()) * (1 - ease((t - (f.duration() - f.blendOut())) / f.blendOut()));
    }
    private static Film.Segment segment(Film f, float t) {
        for (Film.Segment s : f.segments()) if (t < s.end()) return s;
        return f.segments()[f.segments().length - 1];
    }
    private static FilmShot shot(Film.Segment s, float t) {
        for (FilmShot shot : s.shots()) if (t < shot.end()) return shot;
        return s.shots()[s.shots().length - 1];
    }
    private static float progress(FilmShot s, float t) { return ease((t - s.start()) / Math.max(.001f, s.end() - s.start())); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (film != null && (!film.active() || mc.level == null)) film = null;
        if (film != null) {
            if (savedCamera == null) { savedCamera = mc.options.getCameraType(); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
        } else if (savedCamera != null) { mc.options.setCameraType(savedCamera); savedCamera = null; }
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent e) {
        if (!playing()) return;
        var input = e.getInput();
        input.forwardImpulse = 0; input.leftImpulse = 0; input.jumping = false; input.shiftKeyDown = false;
        input.up = input.down = input.left = input.right = false;
    }
    @SubscribeEvent public static void hideHud(RenderGuiOverlayEvent.Pre e) { if (playing()) e.setCanceled(true); }

    // ---------------------------------------------------------------- camera
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (!playing()) return;
        var mc = Minecraft.getInstance();
        float partial = (float) e.getPartialTick(), t = film.time(partial), w = blend(film, t);
        Film.Segment seg = segment(film, t);
        FilmShot s = shot(seg, t);
        float k = progress(s, t);
        boolean virtual = seg.scene() != null;
        Film.View view = film.view(t);
        Vec3 localPos = view != null ? view.pos() : s.position(k), localAim = view != null ? view.aim() : s.aim(k);
        Vec3 pos = virtual ? localPos : film.toWorld(localPos), target = virtual ? localAim : film.toWorld(localAim);
        if (!virtual) {
            var hit = mc.level.clip(new ClipContext(target, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) pos = hit.getLocation().lerp(target, .12);
        }
        Vec3 dir = target.subtract(pos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), pitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1, 1)));
        float shake = view != null ? 0 : Mth.lerp(k, s.shakeA(), s.shakeB());
        for (float impact : film.impacts()) if (t >= impact) shake += Math.max(0, 1.1f - (t - impact) * .12f);
        float n1 = noise(t * .55f), n2 = noise(t * .55f + 37), n3 = noise(t * .4f + 91);
        Camera camera = e.getCamera();
        if (virtual) { stageCamera = pos; stageYaw = yaw + n1 * shake * 1.6f; stagePitch = pitch + n2 * shake * 1.2f; w = 1; }
        else setCameraPosition(camera, camera.getPosition().lerp(pos, w));
        float[] kick = film.kick(t);
        float ky = kick == null ? 0 : kick[0], kp = kick == null ? 0 : kick[1], kr = kick == null ? 0 : kick[2];
        float roll = view != null ? view.roll() : Mth.lerp(k, s.rollA(), s.rollB());
        e.setYaw(Mth.rotLerp(w, e.getYaw(), yaw) + (n1 * shake * 1.6f + ky) * w);
        e.setPitch(Mth.lerp(w, e.getPitch(), pitch) + (n2 * shake * 1.2f + kp) * w);
        e.setRoll(Mth.lerp(w, e.getRoll(), roll) + (n3 * shake * 1.4f + kr) * w);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(ComputeFovModifierEvent e) {
        if (!playing()) return;
        var mc = Minecraft.getInstance();
        float t = film.time(mc.getFrameTime()), w = blend(film, t);
        FilmShot s = shot(segment(film, t), t);
        float punch = 0;
        for (float impact : film.impacts()) if (t >= impact && t < impact + 6) punch += 9 * (1 - (t - impact) / 6);
        float[] kick = film.kick(t);
        if (kick != null && kick.length > 3) punch += kick[3];
        Film.View view = film.view(t);
        float base = view != null ? view.fov() : Mth.lerp(progress(s, t), s.fovA(), s.fovB());
        float wanted = (base + punch) / Math.max(30, mc.options.fov().get());
        e.setNewFovModifier(Mth.lerp(w, e.getNewFovModifier(), wanted));
    }
    private static float noise(float x) {
        return Mth.sin(x * 1.7f) * .5f + Mth.sin(x * 3.1f + 1.3f) * .3f + Mth.sin(x * 7.3f + 4.1f) * .2f;
    }
    private static void setCameraPosition(Camera camera, Vec3 pos) {
        try {
            if (cameraPosition == null) {
                for (Field f : Camera.class.getDeclaredFields()) if (f.getType() == Vec3.class) { cameraPosition = f; break; }
                if (cameraPosition == null) return;
                cameraPosition.setAccessible(true);
            }
            cameraPosition.set(camera, pos);
        } catch (ReflectiveOperationException ignored) { }
    }

    // ---------------------------------------------------------------- virtual stage
    @SubscribeEvent public static void stage(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !playing()) return;
        var mc = Minecraft.getInstance();
        float partial = mc.getFrameTime(), t = film.time(partial);
        Film.Segment seg = segment(film, t);
        if (seg.scene() == null) return;
        // The film's stage replaces the world entirely from here.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        FogRenderer.setupNoFog();
        PoseStack pose = e.getPoseStack();
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        drawingStage = true;
        try {
            var backdrop = seg.scene().backdrop(t - seg.start(), t);
            if (backdrop != null && FilmBackdrop.ready()) {
                Vec3 forward = Vec3.directionFromRotation(stagePitch, stageYaw);
                Vec3 right = forward.cross(new Vec3(0, 1, 0));
                right = right.lengthSqr() < 1e-6 ? new Vec3(-1, 0, 0) : right.normalize();
                Vec3 up = right.cross(forward).normalize();
                FilmBackdrop.draw(backdrop, stageCamera, forward, right, up, t / 20f);
            } else sky(pose, seg.scene().skyTop(), seg.scene().skyBottom());
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            var rot = e.getCamera().rotation();
            var r = new org.joml.Vector3f(1, 0, 0).rotate(rot); var u = new org.joml.Vector3f(0, 1, 0).rotate(rot);
            var buffers = mc.renderBuffers().bufferSource();
            pose.pushPose();
            pose.translate(-stageCamera.x, -stageCamera.y, -stageCamera.z);
            seg.scene().render(new FilmContext(pose, buffers, stageCamera, new Vec3(r.x, r.y, r.z), new Vec3(u.x, u.y, u.z), t, t - seg.start(), partial));
            buffers.endBatch();
            pose.popPose();
        } finally {
            drawingStage = false;
            mv.popPose(); RenderSystem.applyModelViewMatrix();
        }
    }
    /** Opaque gradient dome around the camera. */
    private static void sky(PoseStack pose, int top, int bottom) {
        RenderSystem.disableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var m = pose.last().pose();
        int rings = 12, slices = 24; float radius = 60;
        for (int i = 0; i < rings; i++) for (int j = 0; j < slices; j++) {
            double a0 = Math.PI * i / rings - Math.PI / 2, a1 = Math.PI * (i + 1) / rings - Math.PI / 2;
            double b0 = Math.PI * 2 * j / slices, b1 = Math.PI * 2 * (j + 1) / slices;
            double[][] corners = {{a0, b0}, {a1, b0}, {a1, b1}, {a0, b1}};
            for (double[] c : corners) {
                float y = (float) Math.sin(c[0]);
                float mix = ease((y + 1) / 2);
                int color = lerpColor(bottom, top, mix);
                b.vertex(m, (float) (Math.cos(c[0]) * Math.cos(c[1]) * radius), y * radius, (float) (Math.cos(c[0]) * Math.sin(c[1]) * radius))
                        .color((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1f).endVertex();
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull(); RenderSystem.depthMask(true); RenderSystem.enableDepthTest();
    }
    static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    // ---------------------------------------------------------------- sound and picture layer
    @SubscribeEvent public static void cues(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !playing()) return;
        var mc = Minecraft.getInstance();
        float t = film.time(mc.getFrameTime());
        if (cueOwner != film) { cueOwner = film; cueCache = film.cues(); }
        for (Film.Cue cue : cueCache)
            if (cue.time() > lastCueTime && cue.time() <= t)
                mc.getSoundManager().play(SimpleSoundInstance.forUI(cue.sound(), cue.pitch(), cue.volume()));
        lastCueTime = Math.max(lastCueTime, t);
    }
    @SubscribeEvent public static void layer(RenderGuiEvent.Post e) {
        if (!playing()) return;
        var mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        float t = film.time(mc.getFrameTime());
        int grade = film.grade(t);
        if ((grade >>> 24) > 0) g.fill(0, 0, w, h, grade);
        film.overlay(g, t, w, h);
        // Fade through each segment's colour around its cut.
        Film.Segment[] segments = film.segments();
        for (int i = 1; i < segments.length; i++) {
            Film.Segment s = segments[i];
            if (s.fadeTicks() <= 0) continue;
            float d = Math.abs(t - s.start()), a = 1 - d / Math.max(.5f, s.fadeTicks());
            if (a > 0) g.fill(0, 0, w, h, HudStyle.alpha(0xFF000000 | s.fadeColor(), ease(a)));
        }
        for (float impact : film.impacts()) impactFrame(g, w, h, t - impact);
    }
    /** Impact: white radial streaks bursting from the centre, then a warm flash fading out. */
    private static void impactFrame(GuiGraphics g, int w, int h, float since) {
        if (since < 0 || since > 5) return;
        if (since < 1.2f) wedges(g, w, h, 1 - since / 1.2f, since * 9, .9f, 40);
        g.fill(0, 0, w, h, HudStyle.alpha(0xFFFFF0DC, Math.max(0, 1 - since / 5f) * .7f));
    }
    private static void wedges(GuiGraphics g, int w, int h, float strength, float seed, float alpha, int count) {
        float cx = w / 2f, cy = h / 2f, reach = (float) Math.hypot(cx, cy);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        var m = g.pose().last().pose();
        float cycle = (float) Math.floor(seed);
        for (int i = 0; i < count; i++) {
            double angle = (i + hash(i * 17 + cycle * 3) * .8) / count * Math.PI * 2;
            float inner = reach * (.2f + .3f * hash(i * 5 + cycle)), half = 2 + 5 * hash(i * 11 + cycle);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle), px = -sin * half, py = cos * half;
            float a = alpha * strength * (.5f + .5f * hash(i * 23 + cycle));
            buffer.vertex(m, cx + cos * inner, cy + sin * inner, 0).color(1f, .97f, .92f, 0f).endVertex();
            buffer.vertex(m, cx + cos * reach + px, cy + sin * reach + py, 0).color(1f, .97f, .92f, a).endVertex();
            buffer.vertex(m, cx + cos * reach - px, cy + sin * reach - py, 0).color(1f, .97f, .92f, a).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }
    static float hash(float n) { double x = Math.sin(n * 12.9898) * 43758.5453; return (float) (x - Math.floor(x)); }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        film = null;
        if (savedCamera != null) { Minecraft.getInstance().options.setCameraType(savedCamera); savedCamera = null; }
    }
}
