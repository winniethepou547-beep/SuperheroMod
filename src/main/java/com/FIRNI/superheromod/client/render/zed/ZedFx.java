package com.FIRNI.superheromod.client.render.zed;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.FIRNI.superheromod.network.packet.ZedFxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

import static com.FIRNI.superheromod.heroes.zed.ZedAction.*;

/**
 * Zed's shadows and blades in the world: his two shadows standing where they were left (see-through,
 * near black, smoke curling off their edges, and the W shadow moving as he moves when it copies a
 * throw or a slash); the spinning shurikens and their thin trails; the rings of the Shadow Slash; the
 * shadow running out along the ground; the energy trails of a swap; the Death Mark's sigil, the seal
 * on the target that beats and turns and the way it collapses and bursts; his gauntlets in first person.
 * Dark smoke for the body of it, red and violet only for the lines.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ZedFx {
    static final int RED = 0xff2a3c, VIOLET = 0x8a2cff, DARK = 0x120a14, WHITE = 0xfff0f4;

    private static final class Star { Vec3 pos, prev, dir; double travelled; final boolean shadow; final long start; boolean dead;
        Star(Vec3 pos, Vec3 dir, boolean shadow, long start) { this.pos = this.prev = pos; this.dir = dir; this.shadow = shadow; this.start = start; } }
    private record Burst(int kind, Vec3 pos, Vec3 dir, float power, int entity, long start) {}
    private static final List<Star> STARS = new ArrayList<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static float vignette;

    private ZedFx() {}

    private static long now() { var l = Minecraft.getInstance().level; return l == null ? 0 : l.getGameTime(); }
    private static Random random() { return new Random(System.nanoTime()); }

    // ------------------------------------------------------------------ what the server tells us
    public static void receive(ZedFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 at = p.pos(), dir = p.dir();
        Random r = random();
        switch (p.kind()) {
            case FX_SHURIKEN -> { STARS.add(new Star(at, dir.normalize(), p.power() > .5f, now())); if (STARS.size() > 24) STARS.remove(0); }
            case FX_SHURIKEN_HIT -> {
                if (p.entity() < 0) for (Star s : STARS) if (s.pos.distanceTo(at) < 2.5) s.dead = true;
                for (int i = 0; i < 8; i++) mc.level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, (r.nextDouble() - .5) * .6 + dir.x * .3, r.nextDouble() * .4, (r.nextDouble() - .5) * .6 + dir.z * .3);
                smoke(at, 4, .3, .03);
                add(p);
            }
            case FX_SPIN -> { add(p); smoke(at.add(0, .3, 0), (int) (12 * p.power()), SPIN_RADIUS * .6, .02); if (near(at, 8)) ClientScreenShake.add(.12f * p.power()); }
            case FX_SHADOW_CAST, FX_MARK_DASH -> { add(p); smoke(at.add(0, .2, 0), 10, .6, .04); }
            case FX_SWAP, FX_RETURN -> {
                add(p);
                smoke(at.add(0, 1, 0), 18, .5, .05);
                smoke(at.add(dir).add(0, 1, 0), 18, .5, .05);
                var me = mc.player;
                if (me != null && (me.position().distanceTo(at) < 1.5 || me.position().distanceTo(at.add(dir)) < 1.5)) vignette = .45f;
            }
            case FX_MARK_LOCK, FX_MARK_APPLY, FX_SLASH_HIT, FX_PASSIVE -> add(p);
            case FX_MARK_POP -> {
                add(p);
                if (near(at, 14)) ClientScreenShake.add(.3f + .3f * p.power());
                for (int i = 0; i < 20; i++) mc.level.addParticle(new DustParticleOptions(new Vector3f(.8f, .08f, .25f), 1.2f), at.x, at.y, at.z,
                        (r.nextDouble() - .5) * .8, (r.nextDouble() - .5) * .8, (r.nextDouble() - .5) * .8);
                smoke(at, 10, .4, .06);
            }
            case FX_SHADOW_END -> smoke(at.add(0, 1, 0), 20, .4, .05);
            default -> {}
        }
    }
    private static void add(ZedFxPacket p) {
        BURSTS.add(new Burst(p.kind(), p.pos(), p.dir(), p.power(), p.entity(), now()));
        if (BURSTS.size() > 64) BURSTS.remove(0);
    }
    private static boolean near(Vec3 at, double range) { var me = Minecraft.getInstance().player; return me != null && me.position().distanceTo(at) < range; }
    private static void smoke(Vec3 at, int count, double spread, double rise) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Random r = random();
        for (int i = 0; i < count; i++) {
            double x = at.x + (r.nextDouble() - .5) * 2 * spread, y = at.y + (r.nextDouble() - .5) * spread, z = at.z + (r.nextDouble() - .5) * 2 * spread;
            if (i % 3 == 2) level.addParticle(new DustParticleOptions(new Vector3f(.45f, .1f, .5f), .9f), x, y, z, 0, rise, 0);
            else level.addParticle(i % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, y, z, 0, rise * (.5 + r.nextDouble()), 0);
        }
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { STARS.clear(); BURSTS.clear(); return; }
        if (mc.isPaused()) return;
        long now = now();
        for (Star s : STARS) {
            s.prev = s.pos;
            if (s.dead) continue;
            Vec3 next = s.pos.add(s.dir.scale(SHURIKEN_SPEED));
            var hit = mc.level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) { next = hit.getLocation(); s.dead = true; }
            s.travelled += s.pos.distanceTo(next);
            s.pos = next;
            if (s.travelled >= SHURIKEN_RANGE) s.dead = true;
        }
        STARS.removeIf(s -> s.dead && now - s.start > 2 && s.prev == s.pos);
        BURSTS.removeIf(b -> now - b.start() > 30);
        vignette *= .75f;
        // Smoke curling off the shadows' edges; the seal's sparks circling the marked.
        Random r = random();
        for (var entry : ZedClient.all()) {
            ZedClient.State s = entry.getValue();
            if (s.wAlive && r.nextFloat() < .6f) smoke(s.wPos.add(0, .3 + r.nextDouble() * 1.6, 0), 1, .35, .02);
            if (s.rAlive && r.nextFloat() < .4f) smoke(s.rPos.add(0, .3 + r.nextDouble() * 1.6, 0), 1, .35, .02);
            Entity marked = s.markTarget >= 0 ? mc.level.getEntity(s.markTarget) : null;
            if (marked != null && r.nextFloat() < .7f) {
                double a = r.nextDouble() * Math.PI * 2;
                Vec3 c = marked.position().add(Math.cos(a) * .7, marked.getBbHeight() * (.2 + .7 * r.nextDouble()), Math.sin(a) * .7);
                mc.level.addParticle(new DustParticleOptions(new Vector3f(.75f, .06f, .3f), .7f), c.x, c.y, c.z, 0, .02, 0);
            }
        }
    }

    // ------------------------------------------------------------------ drawing helpers
    private static void put(VertexConsumer v, Matrix4f m, Vec3 p, int rgb, float a) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, Math.max(0, Math.min(1, a))).endVertex();
    }
    /** A ring in the plane across `normal`. */
    private static void ring(FilmContext c, RenderType type, Vec3 centre, Vec3 normal, double radius, double width, int rgb, float alpha) {
        if (alpha <= .003f || radius <= 0) return;
        VertexConsumer v = c.buffers().getBuffer(type);
        Matrix4f m = c.pose().last().pose();
        Vec3 n = normal.normalize(), u = Math.abs(n.y) < .9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize(), w = n.cross(u).normalize();
        int count = 32;
        double in = Math.max(0, radius - width), out = radius + width;
        for (int i = 0; i < count; i++) {
            double a0 = Math.PI * 2 * i / count, a1 = Math.PI * 2 * (i + 1) / count;
            Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0))), d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
            put(v, m, centre.add(d0.scale(in)), rgb, 0); put(v, m, centre.add(d1.scale(in)), rgb, 0);
            put(v, m, centre.add(d1.scale(radius)), rgb, alpha); put(v, m, centre.add(d0.scale(radius)), rgb, alpha);
            put(v, m, centre.add(d0.scale(radius)), rgb, alpha); put(v, m, centre.add(d1.scale(radius)), rgb, alpha);
            put(v, m, centre.add(d1.scale(out)), rgb, 0); put(v, m, centre.add(d0.scale(out)), rgb, 0);
        }
    }
    /** A curved cut: an arc of a circle (centre, radius, from angle a0 to a1 in the plane across `normal`), sharp at both ends. */
    private static void arc(FilmContext c, RenderType type, Vec3 centre, Vec3 normal, double radius, double a0, double a1, double width, int rgb, float alpha) {
        if (alpha <= .003f) return;
        VertexConsumer v = c.buffers().getBuffer(type);
        Matrix4f m = c.pose().last().pose();
        Vec3 n = normal.normalize(), u = Math.abs(n.y) < .9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize(), w = n.cross(u).normalize();
        int count = 18;
        for (int i = 0; i < count; i++) {
            double t0 = i / (double) count, t1 = (i + 1) / (double) count;
            double b0 = a0 + (a1 - a0) * t0, b1 = a0 + (a1 - a0) * t1;
            double w0 = width * Math.sin(Math.PI * t0), w1 = width * Math.sin(Math.PI * t1);
            Vec3 d0 = u.scale(Math.cos(b0)).add(w.scale(Math.sin(b0))), d1 = u.scale(Math.cos(b1)).add(w.scale(Math.sin(b1)));
            float f0 = alpha * (float) (.3 + .7 * t0), f1 = alpha * (float) (.3 + .7 * t1);
            put(v, m, centre.add(d0.scale(radius - w0)), rgb, f0); put(v, m, centre.add(d1.scale(radius - w1)), rgb, f1);
            put(v, m, centre.add(d1.scale(radius + w1)), rgb, f1); put(v, m, centre.add(d0.scale(radius + w0)), rgb, f0);
        }
    }
    /** A flat ribbon on the ground from a to b. */
    private static void flat(FilmContext c, RenderType type, Vec3 a, Vec3 b, double w0, double w1, int rgb, float alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        VertexConsumer v = c.buffers().getBuffer(type);
        Matrix4f m = c.pose().last().pose();
        Vec3 side = d.cross(new Vec3(0, 1, 0)).normalize();
        put(v, m, a.add(side.scale(w0)), rgb, alpha); put(v, m, b.add(side.scale(w1)), rgb, alpha);
        put(v, m, b.subtract(side.scale(w1)), rgb, alpha); put(v, m, a.subtract(side.scale(w0)), rgb, alpha);
    }
    /** A point on a curved path from a to b, bulging sideways by `bend`. */
    private static Vec3 curve(Vec3 a, Vec3 b, double k, double bend) {
        Vec3 d = b.subtract(a), side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        return a.add(d.scale(k)).add(side.scale(bend * Math.sin(Math.PI * k))).add(0, .6 * Math.sin(Math.PI * k), 0);
    }

    // ------------------------------------------------------------------ drawing: light, smoke and lines
    @SubscribeEvent public static void renderLight(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean marks = false;
        for (var entry : ZedClient.all()) if (entry.getValue().markTarget >= 0) marks = true;
        if (STARS.isEmpty() && BURSTS.isEmpty() && !marks) return;
        float partial = e.getPartialTick();
        float time = now() + partial;
        PoseStack p = e.getPoseStack();
        p.pushPose();
        Vec3 cam = e.getCamera().getPosition();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var buffers = mc.renderBuffers().bufferSource();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        FilmContext c = new FilmContext(p, buffers, cam, new Vec3(rr.x, rr.y, rr.z), new Vec3(uu.x, uu.y, uu.z), time, 0, partial);
        try {
            // Shuriken trails: thin, dark-cored, red (his) or violet (his shadow's).
            for (Star s : STARS) {
                if (s.dead && s.travelled < .1) continue;
                Vec3 at = s.prev.lerp(s.pos, partial);
                double len = Math.min(2.6, s.travelled);
                int col = s.shadow ? VIOLET : RED;
                FilmFx.streak(c, at.subtract(s.dir.scale(len)), at, .32, DARK, 0, .45f, false);
                FilmFx.streak(c, at.subtract(s.dir.scale(len * .8)), at, .1, col, 0, .8f, true);
                FilmFx.glow(c, at, .35, col, .35f);
            }
            for (Burst b : BURSTS) burst(c, b, time, partial);
            // The seals on the marked: turning, beating, brighter the more is stored.
            for (var entry : ZedClient.all()) {
                ZedClient.State s = entry.getValue();
                if (s.markTarget < 0) continue;
                Entity t = mc.level.getEntity(s.markTarget);
                if (t == null) continue;
                Vec3 centre = t.getPosition(partial).add(0, t.getBbHeight() * .55, 0);
                seal(c, centre, cam, time, s.markLeft, s.markStored);
            }
            buffers.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /** The Death Mark on its target: a ring and a four-bladed star in it, turning, beating faster as the end nears. */
    private static void seal(FilmContext c, Vec3 centre, Vec3 cam, float time, int left, float stored) {
        Vec3 facing = cam.subtract(centre).normalize();
        Vec3 at = centre.add(facing.scale(.5));
        float urgency = 1 - Math.min(1, left / (float) MARK_LIFE);
        float beat = .5f + .5f * (float) Math.sin(time * (.35 + 1.2 * urgency));
        float power = .55f + .45f * Math.min(1, stored / 20f);
        // In its last moments it draws in on itself.
        float squeeze = left < 6 ? left / 6f : 1;
        double r = (.45 + .08 * beat) * squeeze;
        ring(c, FilmFx.SOFT, at, facing, r, .16, DARK, .55f * power);
        ring(c, FilmFx.ADD, at, facing, r, .06, RED, (.55f + .35f * beat) * power);
        ring(c, FilmFx.ADD, at, facing, r * .62, .035, VIOLET, .5f * power);
        Vec3 u = Math.abs(facing.y) < .9 ? facing.cross(new Vec3(0, 1, 0)).normalize() : facing.cross(new Vec3(1, 0, 0)).normalize(), w = facing.cross(u).normalize();
        double spin = time * .08;
        for (int i = 0; i < 4; i++) {
            double a = spin + i * Math.PI / 2;
            Vec3 d = u.scale(Math.cos(a)).add(w.scale(Math.sin(a)));
            FilmFx.streak(c, at, at.add(d.scale(r * 1.15)), .09 * squeeze, RED, .9f * power, 0, true);
        }
        FilmFx.glow(c, at, .7 * squeeze, RED, (.25f + .25f * beat) * power);
    }
    private static void burst(FilmContext c, Burst b, float time, float partial) {
        float age = time - b.start();
        if (age < 0) return;
        Vec3 at = b.pos();
        switch (b.kind()) {
            case FX_SPIN -> {
                // A few separate curved cuts sweeping round him, a ring of pressed air at the ground.
                float life = 8, k = age / life;
                if (k > 1) return;
                float a = (1 - k) * (1 - k) * (b.power() > .8f ? 1 : .7f);
                double radius = 1 + (SPIN_RADIUS - 1) * (1 - Math.pow(1 - Math.min(1, k * 1.6), 2));
                int col = b.power() > .8f ? RED : VIOLET;
                for (int i = 0; i < 4; i++) {
                    double start = i * Math.PI / 2 + k * 2.2 + i * .4;
                    Vec3 tilt = new Vec3(Math.sin(i * 1.7) * .25, 1, Math.cos(i * 2.3) * .25);
                    Vec3 centre = at.add(0, .8 + .25 * i, 0);
                    arc(c, FilmFx.SOFT, centre, tilt, radius * (.85 + .05 * i), start, start + 2.4, .28, DARK, .6f * a);
                    arc(c, FilmFx.ADD, centre, tilt, radius * (.85 + .05 * i), start, start + 2.4, .09, col, .95f * a);
                    arc(c, FilmFx.ADD, centre, tilt, radius * (.85 + .05 * i), start + .2, start + 2.2, .03, WHITE, .7f * a);
                }
                FilmFx.ring(c, at.add(0, .06, 0), radius, .35, DARK, .5f * a, false);
                FilmFx.ring(c, at.add(0, .08, 0), radius, .12, col, .7f * a, true);
            }
            case FX_SHADOW_CAST -> {
                // The shadow pouring out along the ground, the copy rising at the end of it.
                float k = Math.min(1, age / SHADOW_TRAVEL), fade = 1 - Math.max(0, (age - SHADOW_TRAVEL) / 10f);
                if (fade <= 0) return;
                Vec3 from = at.add(0, .05, 0), to = from.add(b.dir().scale(k));
                flat(c, FilmFx.SOFT, from, to, .7, .45, DARK, .75f * fade);
                flat(c, FilmFx.ADD, from, to, .08, .05, VIOLET, .5f * fade);
                ring(c, FilmFx.SOFT, from, new Vec3(0, 1, 0), .9 + .4 * k, .3, DARK, .6f * fade);
                ring(c, FilmFx.ADD, to.add(0, .02, 0), new Vec3(0, 1, 0), .7, .08, VIOLET, .6f * fade);
            }
            case FX_SWAP, FX_RETURN, FX_MARK_DASH -> {
                // Energy trails curling between the two places (one each way for a swap), dark with a red edge.
                float life = b.kind() == FX_MARK_DASH ? 14 : 10, k = age / life;
                if (k > 1) return;
                float a = 1 - k;
                Vec3 from = at.add(0, 1, 0), to = at.add(b.dir()).add(0, 1, 0);
                int ways = b.kind() == FX_SWAP ? 2 : 1;
                for (int w = 0; w < ways; w++) {
                    Vec3 s0 = w == 0 ? from : to, s1 = w == 0 ? to : from;
                    double bend = (w == 0 ? 1 : -1) * Math.min(2.5, s0.distanceTo(s1) * .25);
                    Vec3 prev = s0;
                    for (int i = 1; i <= 12; i++) {
                        Vec3 next = curve(s0, s1, i / 12.0, bend);
                        FilmFx.streak(c, prev, next, .55, DARK, .7f * a, .7f * a, false);
                        FilmFx.streak(c, prev, next, .12, b.kind() == FX_RETURN ? VIOLET : RED, .8f * a, .8f * a, true);
                        prev = next;
                    }
                }
                FilmFx.glow(c, from, 1.2, VIOLET, .4f * a);
                FilmFx.glow(c, to, 1.2, RED, .4f * a);
            }
            case FX_MARK_LOCK -> {
                // The sigil under the target, following them.
                Entity t = Minecraft.getInstance().level.getEntity(b.entity());
                Vec3 feet = t != null ? t.getPosition(partial) : at;
                float k = Math.min(1, age / 4), fade = 1 - Math.max(0, (age - 10) / 8);
                if (fade <= 0) return;
                double r = 1.4 * k;
                ring(c, FilmFx.SOFT, feet.add(0, .05, 0), new Vec3(0, 1, 0), r, .35, DARK, .6f * fade);
                ring(c, FilmFx.ADD, feet.add(0, .07, 0), new Vec3(0, 1, 0), r, .06, RED, .9f * fade);
                ring(c, FilmFx.ADD, feet.add(0, .07, 0), new Vec3(0, 1, 0), r * .6, .04, VIOLET, .7f * fade);
                for (int i = 0; i < 4; i++) {
                    double a = time * .12 + i * Math.PI / 2;
                    Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
                    flat(c, FilmFx.ADD, feet.add(0, .08, 0).add(d.scale(r * .3)), feet.add(0, .08, 0).add(d.scale(r * 1.05)), .12, .01, RED, .8f * fade);
                }
            }
            case FX_MARK_APPLY -> {
                float k = age / 6;
                if (k > 1) return;
                ring(c, FilmFx.ADD, at, c.camera().subtract(at), .3 + 1.4 * k, .15, RED, .9f * (1 - k));
                FilmFx.glow(c, at, 1.2, RED, .7f * (1 - k));
            }
            case FX_MARK_POP -> {
                // Collapses into a point, a flash, then a sharp ring and cut lines thrown out.
                Vec3 facing = c.camera().subtract(at).normalize();
                if (age < 3) {
                    float k = age / 3;
                    ring(c, FilmFx.ADD, at, facing, .9 * (1 - k), .08, RED, .9f);
                    FilmFx.glow(c, at, .5 + 1.2 * k, VIOLET, .6f * k);
                    return;
                }
                float k = (age - 3) / 12;
                if (k > 1) return;
                float a = (1 - k) * (1 - k);
                FilmFx.glow(c, at, 2.2 * (1 - k * .5), WHITE, .9f * a);
                FilmFx.glow(c, at, 3, RED, .6f * a);
                ring(c, FilmFx.SOFT, at, facing, .5 + 3 * k, .4, DARK, .6f * a);
                ring(c, FilmFx.ADD, at, facing, .5 + 3 * k, .12, RED, .9f * a);
                ring(c, FilmFx.ADD, at.add(0, -.6, 0), new Vec3(0, 1, 0), .5 + 3.5 * k, .1, VIOLET, .7f * a);
                for (int i = 0; i < 8; i++) {
                    double ang = i * Math.PI / 4 + .3;
                    Vec3 d = new Vec3(Math.cos(ang), Math.sin(ang * 2) * .4, Math.sin(ang)).normalize();
                    FilmFx.streak(c, at.add(d.scale(.4 + 2 * k)), at.add(d.scale(1.4 + 3 * k)), .1, RED, .9f * a, 0, true);
                }
            }
            case FX_SLASH_HIT, FX_PASSIVE, FX_SHURIKEN_HIT -> {
                // A crossing cut where it landed; bigger, with a dark burst, for the passive.
                boolean passive = b.kind() == FX_PASSIVE;
                float life = passive ? 8 : 5, k = age / life;
                if (k > 1) return;
                float a = 1 - k;
                Vec3 facing = c.camera().subtract(at).normalize();
                double r = passive ? .9 : .55;
                double spin = b.power() < 0 ? Math.PI * .6 : 0;
                arc(c, FilmFx.ADD, at, facing, r, spin + 2.4 + k, spin + 4.4 + k, passive ? .12 : .07, passive ? RED : WHITE, .95f * a);
                arc(c, FilmFx.ADD, at, facing, r * .8, spin + .9 - k, spin + 2.6 - k, .05, RED, .8f * a);
                if (passive) {
                    ring(c, FilmFx.SOFT, at, facing, .4 + 1.2 * k, .3, DARK, .6f * a);
                    FilmFx.glow(c, at, 1, VIOLET, .6f * a);
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ drawing: the shadows and the shurikens
    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 1, 1, 1);
    @SubscribeEvent public static void renderSolid(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        float time = now() + partial;
        boolean any = false;
        for (var entry : ZedClient.all()) {
            ZedClient.State s = entry.getValue();
            if (s.wAlive) {
                float age = time - s.wBorn;
                Vec3 at = s.wFrom.lerp(s.wPos, ZedMotion.ease(age / SHADOW_TRAVEL));
                float alpha = .62f * ZedMotion.clamp(age / 4) * ZedMotion.clamp(s.wLeft / 12f);
                ZedMotion.Pose p = ZedMotion.sample(s.wAction, ZedClient.shadowClock(s, partial), time + 7);
                // Gliding out to its place, low.
                if (age < SHADOW_TRAVEL) { p.crouch += 3 * (1 - age / SHADOW_TRAVEL); p.bodyPitch = .4f * (1 - age / SHADOW_TRAVEL); }
                shadow(pose, buffers, cam, at, s.wYaw, p, time, alpha);
                any = true;
            }
            if (s.rAlive) {
                ZedMotion.Pose p = ZedMotion.idle(time + 13);
                shadow(pose, buffers, cam, s.rPos, s.rYaw, p, time, .5f * ZedMotion.clamp(s.rLeft / 12f));
                any = true;
            }
        }
        // The shurikens: four curved blades round a hub, spinning flat.
        for (Star s : STARS) {
            if (s.dead && s.travelled < .1) continue;
            Vec3 at = s.prev.lerp(s.pos, partial);
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(Axis.YP.rotation((float) Math.atan2(s.dir.x, s.dir.z)));
            pose.mulPose(Axis.XP.rotation(-.3f));
            pose.mulPose(Axis.YP.rotation((time - s.start) * 1.1f));
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
            float[] steel = s.shadow ? new float[]{.18f, .12f, .24f} : new float[]{.62f, .64f, .7f};
            for (int i = 0; i < 4; i++) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotation((float) (i * Math.PI / 2)));
                blade(pose, buffers, light, .02f, -.02f, -.035f, .38f, .04f, .07f, steel);
                pose.mulPose(Axis.YP.rotation(.5f));
                blade(pose, buffers, light, .3f, -.02f, -.02f, .16f, .04f, .04f, steel);
                pose.popPose();
            }
            blade(pose, buffers, light, -.08f, -.03f, -.08f, .16f, .06f, .16f, new float[]{.2f, .2f, .23f});
            pose.popPose();
            any = true;
        }
        if (any) buffers.endBatch();
    }
    private static void blade(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        p.pushPose();
        p.translate(x, y, z);
        p.scale(w * 16, h * 16, d * 16);
        UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY, c[0], c[1], c[2], 1);
        p.popPose();
    }
    /** A shadow of Zed standing at a place, facing a way, drawn as the living-entity renderer would draw him. */
    private static void shadow(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, Vec3 at, float yaw, ZedMotion.Pose p, float time, float alpha) {
        if (alpha <= .01f) return;
        var mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.scale(-.9375f, -.9375f, .9375f);
        pose.translate(0, -1.501, 0);
        ZedBody.draw(pose, buffers, LevelRenderer.getLightColor(mc.level, BlockPos.containing(at.add(0, 1, 0))), p, 0, 0, 0, 0, time, ZedBody.SHADOW, alpha);
        pose.popPose();
    }

    // ------------------------------------------------------------------ first person: the gauntlets and their blades
    @SubscribeEvent public static void hand(RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !ZedClient.isHero(mc.player) || FilmDirector.playing()) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        ZedClient.State s = ZedClient.get(mc.player);
        int action = s == null ? IDLE : s.action;
        float t = s == null ? 0 : ZedClient.clock(s, e.getPartialTick());
        PoseStack p = e.getPoseStack();
        var b = e.getMultiBufferSource();
        int light = e.getPackedLight();
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side > 0;
            p.pushPose();
            p.translate(side * .5, -.6, -.75);
            p.mulPose(Axis.YP.rotationDegrees(-side * 6));
            switch (action) {
                case SLASH_RIGHT, SLASH_LEFT -> {
                    if (right == (action == SLASH_RIGHT)) {
                        float cut = ZedMotion.snap(t, 1.5f, SLASH_HIT + .5f), back = ZedMotion.k(t, SLASH_HIT + 1, SLASH_TICKS);
                        float w = 1 - back;
                        p.translate((side * .25 - side * .8 * cut) * w, .2 * w, -.2 * w);
                        p.mulPose(Axis.YP.rotationDegrees(side * (40 - 110 * cut) * w));
                        p.mulPose(Axis.ZP.rotationDegrees(side * 70 * w));
                    }
                }
                case THROW -> {
                    if (right) {
                        float out = ZedMotion.snap(t, THROW_RELEASE - .5f, THROW_RELEASE + 1), draw = ZedMotion.k(t, 0, THROW_RELEASE), back = ZedMotion.k(t, THROW_RELEASE + 2, THROW_TICKS);
                        float w = 1 - back;
                        p.translate(.1 * draw * (1 - out) * w - .25 * out * w, .25 * draw * w, (.2 * draw * (1 - out) - .45 * out) * w);
                    }
                }
                case SPIN -> {
                    float open = ZedMotion.snap(t, SPIN_HIT - 1, SPIN_HIT + 1) * (1 - ZedMotion.k(t, SPIN_HIT + 3, SPIN_TICKS));
                    p.translate(side * .35 * open, .15 * open, .1 * open);
                    p.mulPose(Axis.ZP.rotationDegrees(-side * 50 * open));
                }
                case SHADOW_CAST -> { if (right) { float w = ZedMotion.snap(t, 0, 2) * (1 - ZedMotion.k(t, 4, CAST_TICKS)); p.translate(-.2 * w, .1 * w, -.3 * w); } }
                default -> {}
            }
            p.mulPose(Axis.XP.rotationDegrees(-80));
            ZedBody.solid();
            // The forearm runs back toward the camera; the blades run out past the fist.
            ZedBody.box(p, b, light, -2, -2, 0, 4, 9, 4, ZedBody.STEEL);
            ZedBody.box(p, b, light, -1.8f, -3.5f, .2f, 3.6f, 2.6f, 3.6f, ZedBody.BLACK);
            for (int i = 0; i < 2; i++) ZedBody.box(p, b, light, side * (2 + i * .8f) - .25f, -13, .6f + i * 1.2f, .5f, 12, 1.1f, ZedBody.SILVER);
            p.popPose();
        }
    }

    // ------------------------------------------------------------------ the camera
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post e) {
        if (vignette < .01f) return;
        var g = e.getGuiGraphics();
        g.fill(0, 0, e.getWindow().getGuiScaledWidth(), e.getWindow().getGuiScaledHeight(), HudStyle.alpha(0xFF0A0410, vignette));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { STARS.clear(); BURSTS.clear(); vignette = 0; }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                var renderer = e.getSkin(skin);
                if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) player.addLayer(new ZedLayer(player));
            }
        }
    }
}
