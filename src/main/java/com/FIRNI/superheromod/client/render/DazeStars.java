package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The one "seeing stars" effect for every daze and stun (Batman's flash grenade, his stagger, anything later): 3 to 5
 * very small gold stars with white hearts circling over the head of whoever is dazed, for everyone to see. They spin
 * about the head, bob a little, turn on themselves and pulse; as the daze wears off they slow, shrink, fade and drift
 * up until they are gone (never a pop). One set per body: a new daze on someone already seeing stars only stretches it.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class DazeStars {
    private static final class Daze {
        float start, until, strength, shown, at, turned;
        Daze(float start, float until, float strength) { this.start = start; this.until = until; this.strength = strength; this.at = start; }
    }
    private static final Map<Integer, Daze> DAZED = new HashMap<>();
    /** The last ticks of a daze, in which the stars slow, shrink, fade and drift away. */
    private static final float WEAR_OFF = 28;
    private static final int GOLD = 0xffb81c, PALE_GOLD = 0xffe17a, HEART = 0xfffbe8;

    private DazeStars() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }

    /** Someone is dazed for this many ticks (strength 0..1: more stars, a little bigger). Extends a daze already on. */
    public static void daze(int entity, float ticks, float strength) {
        if (ticks <= 0) return;
        float t = now();
        Daze d = DAZED.get(entity);
        if (d == null || d.until < t) DAZED.put(entity, new Daze(t, t + ticks, Mth.clamp(strength, 0, 1)));
        else { d.until = Math.max(d.until, t + ticks); d.strength = Math.max(d.strength, Mth.clamp(strength, 0, 1)); }
    }
    /** The daze ends early: the stars wear off over the next `linger` ticks at most. */
    public static void release(int entity, float linger) {
        Daze d = DAZED.get(entity);
        if (d != null) d.until = Math.min(d.until, now() + Math.max(linger, WEAR_OFF * .6f));
    }
    public static boolean dazed(Entity e) { Daze d = DAZED.get(e.getId()); return d != null && d.until > now(); }

    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || DAZED.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { DAZED.clear(); return; }
        float t = now(), partial = e.getPartialTick();
        DAZED.values().removeIf(d -> d.until < t - 1);
        if (DAZED.isEmpty()) return;
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        Vec3 right = new Vec3(rr.x, rr.y, rr.z), up = new Vec3(uu.x, uu.y, uu.z);
        var fx = FilmFx.batched();
        FilmContext c = new FilmContext(p, fx, cam, right, up, t, 0, partial);
        try {
            for (var en : DAZED.entrySet()) {
                Entity body = mc.level.getEntity(en.getKey());
                if (body == null || body == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
                draw(c, body, en.getValue(), t, partial, right, up);
            }
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }

    private static void draw(FilmContext c, Entity body, Daze d, float t, float partial, Vec3 right, Vec3 up) {
        // How "on" the daze is: in over a few ticks, out over WEAR_OFF; eased toward so an extension never jumps.
        float target = Math.min(Mth.clamp((t - d.start) / 5f, 0, 1), Mth.clamp((d.until - t) / WEAR_OFF, 0, 1));
        float dt = Math.max(0, t - d.at);
        d.at = t;
        d.shown += (target - d.shown) * Math.min(1, dt * .35f);
        float k = d.shown;
        // Round the head, slower as they wear off (turned on by the frame, so a slowing never jerks them back).
        d.turned += dt * .17f * (.35f + .65f * k);
        if (k < .01f) return;
        float wear = 1 - k;
        // Over the head, drifting up as they wear off.
        Vec3 head = body.getPosition(partial).add(0, body.getBbHeight() + .14 + .4 * wear * wear, 0);
        double radius = Mth.clamp(body.getBbWidth() * .58, .26, .7) * (.85 + .15 * k);
        int n = 3 + Math.round(2 * d.strength);
        for (int i = 0; i < n; i++) {
            float a = d.turned + i * Mth.TWO_PI / n;
            double bob = .045 * Mth.sin(t * .23f + i * 1.7f);
            Vec3 at = head.add(radius * Mth.cos(a), bob, radius * Mth.sin(a));
            float pulse = 1 + .18f * Mth.sin(t * .42f + i * 2.1f);
            float size = .085f * (.85f + .3f * d.strength) * pulse * (.35f + .65f * k);
            float alpha = (float) Math.pow(k, .8);
            star(c, at, right, up, size, t * .21f * (.4f + .6f * k) + i * 1.3f, alpha);
            FilmFx.glow(c, at, size * 2.6, HEART, .45f * alpha);
            FilmFx.glow(c, at, size * 5, GOLD, .12f * alpha);
        }
    }
    /** A five-pointed star facing the camera, gold at the points, a white heart. */
    private static void star(FilmContext c, Vec3 at, Vec3 right, Vec3 up, float r, float angle, float alpha) {
        if (alpha <= .01f) return;
        VertexConsumer v = c.buffers().getBuffer(FilmFx.SOFT);
        Matrix4f m = c.pose().last().pose();
        float inner = r * .46f;
        Vec3[] pts = new Vec3[10];
        for (int i = 0; i < 10; i++) {
            double a = angle + Math.PI / 2 + i * Math.PI / 5;
            double rad = i % 2 == 0 ? r : inner;
            pts[i] = at.add(right.scale(Math.cos(a) * rad)).add(up.scale(Math.sin(a) * rad));
        }
        for (int i = 0; i < 10; i += 2) {
            Vec3 prev = pts[(i + 9) % 10], tip = pts[i], next = pts[i + 1];
            put(v, m, at, HEART, alpha);
            put(v, m, prev, PALE_GOLD, alpha);
            put(v, m, tip, GOLD, alpha);
            put(v, m, next, PALE_GOLD, alpha);
        }
    }
    private static void put(VertexConsumer v, Matrix4f m, Vec3 p, int rgb, float a) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, Mth.clamp(a, 0, 1)).endVertex();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { DAZED.clear(); }
}
