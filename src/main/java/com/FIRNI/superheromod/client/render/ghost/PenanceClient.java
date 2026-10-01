package com.FIRNI.superheromod.client.render.ghost;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.heroes.ghostrider.GhostComboMotion;
import com.FIRNI.superheromod.heroes.ghostrider.PenanceStare;
import com.FIRNI.superheromod.network.packet.GhostPenancePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/**
 * Penance Stare in the world: the synced state, the choreography drawn per frame for everyone
 * (victim pulled in and held, fire breathed into them, eye beams, embers, light, the burst).
 * The two participants additionally watch it as a film (PenanceFilm).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class PenanceClient {
    static final class State {
        int victim, age; long received; float yaw, shown; Vec3 anchor, start;
    }
    static final Map<Integer, State> STATES = new HashMap<>();
    private static final Set<Integer> BURST = new HashSet<>();
    private static final Set<Integer> POSED = new HashSet<>();

    public static void receive(GhostPenancePacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!p.active()) { STATES.remove(p.rider()); BURST.remove(p.rider()); return; }
        State s = STATES.computeIfAbsent(p.rider(), id -> new State());
        s.victim = p.victim(); s.age = p.age(); s.received = mc.level.getGameTime();
        s.yaw = p.yaw(); s.anchor = p.anchor(); s.start = p.victimStart();
        if (mc.player.getId() == p.rider() || mc.player.getId() == p.victim()) FilmDirector.play(new PenanceFilm(p.rider()));
        if (!(mc.level.getEntity(p.victim()) instanceof LivingEntity v)) return;
        Vec3 at = victimAt(s, 1);
        if (p.age() >= PenanceStare.LIFT && p.age() < PenanceStare.CLIMAX_END && p.age() % 2 == 0) {
            var random = mc.level.getRandom();
            Vec3 spark = at.add((random.nextDouble() - .5) * v.getBbWidth(), random.nextDouble() * v.getBbHeight(), (random.nextDouble() - .5) * v.getBbWidth());
            GhostSparks.emit(spark, new Vec3(0, .3, 0), at.y - 4, 2);
        }
        if (p.age() >= PenanceStare.CLIMAX && BURST.add(p.rider())) {
            Vec3 chest = at.add(0, v.getBbHeight() * .6, 0);
            GhostSlamEffects.punch(chest, forward(s));
            var random = mc.level.getRandom();
            for (int i = 0; i < 8; i++) {
                Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * .6 + .4, random.nextGaussian()).normalize().scale(1.4);
                GhostSparks.emit(chest, dir, at.y, 9);
            }
        }
    }
    /** Monotonic, frame-interpolated film time: never steps backwards when a packet is late. */
    static float time(State s, float partial) {
        var level = Minecraft.getInstance().level;
        float predicted = s.age + (level == null ? 0 : Math.min(2, Math.max(0, level.getGameTime() - s.received))) + partial;
        s.shown = Math.min(predicted + 1.5f, Math.max(s.shown, predicted));
        return s.shown;
    }
    static Vec3 forward(State s) { return Vec3.directionFromRotation(0, s.yaw); }
    static Vec3 victimAt(State s, float partial) {
        return PenanceStare.victimAt(s.anchor, s.yaw, s.start, Math.min(time(s, partial), PenanceStare.CLIMAX_END));
    }
    static float ease(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
    private static float stare(float t) { return ease((t - PenanceStare.LIFT) / (PenanceStare.CLIMAX - PenanceStare.LIFT)); }
    private static float breath(float t) { return ease((t - PenanceStare.LIFT + 2) / 4f) * (1 - ease((t - PenanceStare.CLIMAX) / 3f)); }

    public static GhostComboMotion.Pose pose(Player player, float partial) {
        State s = STATES.get(player.getId());
        if (s == null) return null;
        return GhostComboMotion.penance(time(s, partial), PenanceStare.GRAB, PenanceStare.LIFT, PenanceStare.CLIMAX, PenanceStare.CLIMAX_END, PenanceStare.TOTAL);
    }
    public static float jaw(Player player, float partial) {
        State s = STATES.get(player.getId());
        return s == null ? 0 : breath(time(s, partial));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { STATES.clear(); return; }
        STATES.entrySet().removeIf(v -> mc.level.getGameTime() - v.getValue().received > 10);
        State own = mc.player == null ? null : STATES.get(mc.player.getId());
        if (own != null) {
            mc.player.setYRot(own.yaw); mc.player.yRotO = own.yaw; mc.player.setXRot(0);
            mc.player.yBodyRot = own.yaw; mc.player.yHeadRot = own.yaw;
        }
    }

    // ------------------------------------------------------------------ victim drawn on the choreographed path
    private static State victimState(int entity) {
        for (State s : STATES.values()) if (s.victim == entity) return s;
        return null;
    }
    @SubscribeEvent public static void victimPre(RenderLivingEvent.Pre<?, ?> e) {
        if (FilmDirector.drawingStage()) return;
        LivingEntity entity = e.getEntity();
        State s = victimState(entity.getId());
        if (s == null) return;
        float partial = e.getPartialTick();
        if (time(s, partial) >= PenanceStare.CLIMAX_END) return;
        Vec3 offset = victimAt(s, partial).subtract(entity.getPosition(partial));
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        POSED.add(entity.getId());
    }
    @SubscribeEvent public static void victimPost(RenderLivingEvent.Post<?, ?> e) {
        if (POSED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ fire, beams, light (world view)
    @SubscribeEvent public static void effects(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || STATES.isEmpty() || !GhostFireMaterial.ready()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = mc.getFrameTime();
        var p = e.getPoseStack(); Vec3 cam = e.getCamera().getPosition();
        p.pushPose(); p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        try {
            var buffers = mc.renderBuffers().bufferSource();
            var v = buffers.getBuffer(GhostFireMaterial.TYPE);
            var rot = e.getCamera().rotation();
            var r = new org.joml.Vector3f(1, 0, 0).rotate(rot); var u = new org.joml.Vector3f(0, 1, 0).rotate(rot);
            Vec3 viewRight = new Vec3(r.x, r.y, r.z), viewUp = new Vec3(u.x, u.y, u.z);
            for (var entry : STATES.entrySet()) {
                State s = entry.getValue();
                if (!(mc.level.getEntity(entry.getKey()) instanceof LivingEntity rider) || !(mc.level.getEntity(s.victim) instanceof LivingEntity victim)) continue;
                float t = time(s, partial);
                Vec3 forward = forward(s), right = forward.cross(new Vec3(0, 1, 0));
                Vec3 body = t < PenanceStare.CLIMAX_END ? victimAt(s, partial) : victim.getPosition(partial);
                Vec3 eye = rider.getEyePosition(partial), face = body.add(0, victim.getEyeHeight(), 0);
                float b = breath(t), k = stare(t);
                if (b > 0) {
                    Vec3 mouth = eye.add(forward.scale(.22)).add(0, -.2, 0), into = face.add(forward.scale(.2));
                    for (int i = 0; i < 18; i++) {
                        double life = frac(t * .09 + i / 18.0);
                        Vec3 at = mouth.lerp(into, life).add(right.scale(Math.sin(t * .4 + i * 1.7) * .07 * life)).add(0, life * life * .12, 0);
                        double radius = .07 + .22 * life;
                        float alpha = (float) (b * .85 * Math.sqrt(Math.sin(Math.PI * life)));
                        GhostFireMaterial.volume(v, p, at, viewRight, viewUp, radius, radius * 1.1, (float) frac(i * .618), alpha, (float) (1 - .5 * life));
                        if (i % 4 == 0) GhostFireMaterial.glow(v, p, at, viewRight, viewUp, radius * 2.5, alpha * .8f);
                    }
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 from = eye.add(right.scale(side * .085)).add(forward.scale(.2)).add(0, .02, 0);
                        Vec3 to = face.add(right.scale(side * .065)).subtract(forward.scale(.12));
                        streak(v, p, from, to, cam, .016 + .006 * Math.sin(t * 2.3), b);
                    }
                    GhostLights.request(face, 15);
                }
                if (k > 0 && t < PenanceStare.CLIMAX_END + 6) {
                    float climax = t >= PenanceStare.CLIMAX ? 1 - Math.min(1, (t - PenanceStare.CLIMAX) / 14f) : 0;
                    double width = victim.getBbWidth() * .62, height = victim.getBbHeight();
                    for (int i = 0; i < 14; i++) {
                        double a = i * 2.399 + t * .07, y = frac(i * .37 + t * .02) * height;
                        Vec3 at = body.add(Math.cos(a) * width, y, Math.sin(a) * width);
                        GhostFireMaterial.volume(v, p, at, viewRight, viewUp, .16 + .1 * climax, .26 + .12 * climax, (float) frac(i * .618), (k * .55f + climax * .4f), .75f);
                    }
                    GhostFireMaterial.glow(v, p, body.add(0, height * .6, 0), viewRight, viewUp, 1.1 + climax * 1.6, .7f * k + climax);
                }
            }
            buffers.endBatch(GhostFireMaterial.TYPE);
        } finally { mv.popPose(); RenderSystem.applyModelViewMatrix(); p.popPose(); }
    }
    static double frac(double v) { return v - Math.floor(v); }
    static void streak(VertexConsumer v, PoseStack p, Vec3 from, Vec3 to, Vec3 cam, double width, float alpha) {
        Vec3 axis = to.subtract(from);
        Vec3 side = axis.cross(cam.subtract(from));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(width);
        Vec3[] q = {from.subtract(side), from.add(side), to.add(side), to.subtract(side)};
        float[] uu = {0, 1, 1, 0}, tt = {1, 1, 1, 1};
        for (int c = 0; c < 4; c++) GhostFireMaterial.vertex(v, p, q[c].x, q[c].y, q[c].z, uu[c], tt[c], .55f, 0, 1, alpha);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { STATES.clear(); BURST.clear(); POSED.clear(); }
}
