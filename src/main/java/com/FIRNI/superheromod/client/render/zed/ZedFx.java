package com.FIRNI.superheromod.client.render.zed;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.network.packet.ZedFxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
 * Zed's shadows and blades in the world: a light aura of shadow always curling off him; his two
 * shadows standing where they were left (see-through, near black, smoke curling off their edges, the
 * W shadow moving as he moves when it copies a throw or a slash); the hooked shurikens spinning flat,
 * a thin red streak behind them wrapped in shadow; the Shadow Slash (a dark pool on the ground, red
 * crescents round its rim, shadow torn off them); the shadow running out along the ground; the energy
 * trails of a swap; the Death Mark: his body sinking into shadow, two shadow copies of him running to
 * the target and into it, the burning X on them, and the way it bursts as he steps out behind them; his
 * gauntlets in first person. Dark smoke for the body of it, red and violet only for the lines.
 * Also the conductor of the frame: eye light first, then the smoke over it, then the bright lines.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ZedFx {
    static final int RED = 0xff2a3c, VIOLET = 0x8a2cff, DARK = 0x120a14, WHITE = 0xfff0f4, SHADOW_TINT = 0x0c0612;

    private static final class Star { Vec3 pos, prev, dir; double travelled; final boolean shadow; final long start; boolean dead;
        Star(Vec3 pos, Vec3 dir, boolean shadow, long start) { this.pos = this.prev = pos; this.dir = dir; this.shadow = shadow; this.start = start; } }
    private record Burst(int kind, Vec3 pos, Vec3 dir, float power, int entity, long start) {}
    /** One of the two shadow copies running to the Death Mark's target. */
    private record Copy(Vec3 from, int target, int side, long start, int key) {}
    private static final List<Star> STARS = new ArrayList<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final List<Copy> COPIES = new ArrayList<>();
    private static float vignette;

    private ZedFx() {}

    private static long now() { var l = Minecraft.getInstance().level; return l == null ? 0 : l.getGameTime(); }
    private static Random random() { return ShadowSmoke.random(); }

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
                ShadowSmoke.burst(at, 8, .5, dir.normalize().scale(.06), .05, .3f, 16, .55f);
                add(p);
            }
            case FX_SPIN -> {
                add(p);
                // Shadow torn off the crescents, swept round with the cut.
                int n = (int) (26 * p.power());
                for (int i = 0; i < n; i++) {
                    double a = r.nextDouble() * Math.PI * 2, rad = SPIN_RADIUS * (.55 + .45 * r.nextDouble());
                    Vec3 pos = at.add(Math.cos(a) * rad, .2 + r.nextDouble() * 1.2, Math.sin(a) * rad);
                    Vec3 v = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(.12 + r.nextDouble() * .1).add(Math.cos(a) * .04, .01, Math.sin(a) * .04);
                    ShadowSmoke.Wisp w = ShadowSmoke.add(pos, v, .22f + r.nextFloat() * .3f, 12 + r.nextInt(14), .5f + r.nextFloat() * .25f,
                            r.nextFloat() < .45f ? ShadowSmoke.TENDRIL : ShadowSmoke.BLOB);
                    w.tint = p.power() > .8f ? ShadowSmoke.MATTER : SHADOW_TINT;
                }
                if (near(at, 8)) ClientScreenShake.add(.12f * p.power());
            }
            case FX_SHADOW_CAST -> { add(p); ShadowSmoke.burst(at.add(0, .4, 0), 12, .8, dir.normalize().scale(.08), .04, .3f, 16, .55f); }
            case FX_SWAP, FX_RETURN -> {
                add(p);
                ShadowSmoke.burst(at.add(0, 1, 0), 16, .9, Vec3.ZERO, .06, .32f, 18, .6f);
                ShadowSmoke.burst(at.add(dir).add(0, 1, 0), 16, .9, Vec3.ZERO, .06, .32f, 18, .6f);
                var me = mc.player;
                if (me != null && (me.position().distanceTo(at) < 1.5 || me.position().distanceTo(at.add(dir)) < 1.5)) vignette = .45f;
            }
            case FX_MARK_VANISH -> {
                // Sinking into shadow (power > 0), or stepping back out of it (power < 0).
                boolean out = p.power() < 0;
                for (int i = 0; i < 34; i++) {
                    Vec3 pos = at.add(r.nextGaussian() * .3, .1 + r.nextDouble() * 1.8, r.nextGaussian() * .3);
                    Vec3 v = pos.subtract(at.add(0, .9, 0)).normalize().scale(out ? .1 + r.nextDouble() * .08 : .03).add(0, out ? .01 : -.02, 0);
                    ShadowSmoke.Wisp w = ShadowSmoke.add(pos, v, .25f + r.nextFloat() * .3f, 14 + r.nextInt(16), .7f, r.nextFloat() < .4f ? ShadowSmoke.TENDRIL : ShadowSmoke.BLOB);
                    if (!out) { w.sink = true; w.ground = at.y; }
                }
                add(p);
                if (out && near(at, 10)) ClientScreenShake.add(.15f);
                if (!out && mc.player != null && mc.player.getId() == p.entity()) vignette = .5f;
            }
            case FX_MARK_DASH -> {
                int key = -(Math.abs(p.entity()) * 16 + 3);
                COPIES.add(new Copy(at, p.entity(), 1, now(), key));
                COPIES.add(new Copy(at, p.entity(), -1, now(), key - 1));
                if (COPIES.size() > 12) COPIES.subList(0, COPIES.size() - 12).clear();
            }
            case FX_MARK_ARRIVE -> {
                add(p);
                // The shadow they were made of rushes into the body after them.
                for (int i = 0; i < 24; i++) {
                    Vec3 off = new Vec3(r.nextGaussian(), r.nextGaussian() * .6, r.nextGaussian()).normalize().scale(1 + r.nextDouble() * .6);
                    ShadowSmoke.add(at.add(off), off.scale(-.11), .2f + r.nextFloat() * .2f, 9 + r.nextInt(5), .65f, ShadowSmoke.TENDRIL);
                }
                if (near(at, 12)) ClientScreenShake.add(.18f);
            }
            case FX_MARK_LOCK, FX_SLASH_HIT, FX_PASSIVE -> add(p);
            case FX_MARK_APPLY -> add(p);
            case FX_MARK_POP -> {
                add(p);
                if (near(at, 14)) ClientScreenShake.add(.3f + .3f * p.power());
                for (int i = 0; i < 20; i++) mc.level.addParticle(new DustParticleOptions(new Vector3f(.8f, .08f, .25f), 1.2f), at.x, at.y, at.z,
                        (r.nextDouble() - .5) * .8, (r.nextDouble() - .5) * .8, (r.nextDouble() - .5) * .8);
                ShadowSmoke.burst(at, 22, .8, Vec3.ZERO, .12, .35f, 18, .7f);
            }
            case FX_SHADOW_END -> ShadowSmoke.burst(at.add(0, 1, 0), 20, .8, new Vec3(0, .02, 0), .05, .3f, 18, .55f);
            default -> {}
        }
    }
    private static void add(ZedFxPacket p) {
        BURSTS.add(new Burst(p.kind(), p.pos(), p.dir(), p.power(), p.entity(), now()));
        if (BURSTS.size() > 64) BURSTS.remove(0);
    }
    private static boolean near(Vec3 at, double range) { var me = Minecraft.getInstance().player; return me != null && me.position().distanceTo(at) < range; }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { STARS.clear(); BURSTS.clear(); COPIES.clear(); return; }
        if (mc.isPaused()) return;
        long now = now();
        Random r = random();
        for (Star s : STARS) {
            s.prev = s.pos;
            if (s.dead) continue;
            Vec3 next = s.pos.add(s.dir.scale(SHURIKEN_SPEED));
            var hit = mc.level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) { next = hit.getLocation(); s.dead = true; }
            s.travelled += s.pos.distanceTo(next);
            // Shadow peeling off its path, left behind and spreading.
            for (int i = 0; i < 3; i++) {
                Vec3 at = s.pos.lerp(next, r.nextDouble()).add(r.nextGaussian() * .08, r.nextGaussian() * .08, r.nextGaussian() * .08);
                ShadowSmoke.Wisp w = ShadowSmoke.add(at, s.dir.scale(.05).add(r.nextGaussian() * .015, .006, r.nextGaussian() * .015),
                        .13f + r.nextFloat() * .14f, 8 + r.nextInt(10), .42f, i == 0 ? ShadowSmoke.TENDRIL : ShadowSmoke.BLOB);
                w.tint = s.shadow ? SHADOW_TINT : ShadowSmoke.MATTER; w.grow = 1.6f;
            }
            s.pos = next;
            if (s.travelled >= SHURIKEN_RANGE) s.dead = true;
        }
        STARS.removeIf(s -> s.dead && now - s.start > 2 && s.prev == s.pos);
        BURSTS.removeIf(b -> now - b.start() > 30);
        vignette *= .75f;
        // The shadow copies of the Death Mark trail shadow as they run.
        for (Copy c : COPIES) {
            float age = now - c.start();
            if (age > DASH_TICKS) continue;
            Vec3 a = copyAt(c, age, 0), b = copyAt(c, age + 1, 0);
            if (a == null || b == null) continue;
            for (int i = 0; i < 4; i++) {
                Vec3 at = a.lerp(b, r.nextDouble()).add(r.nextGaussian() * .15, .3 + r.nextDouble() * 1.4, r.nextGaussian() * .15);
                ShadowSmoke.add(at, b.subtract(a).scale(.35).add(0, .01, 0), .2f + r.nextFloat() * .25f, 12 + r.nextInt(12), .62f,
                        i % 2 == 0 ? ShadowSmoke.TENDRIL : ShadowSmoke.BLOB);
            }
        }
        COPIES.removeIf(c -> now - c.start() > DASH_TICKS + 4);
        // His light aura; smoke curling off the shadows; sparks circling the marked.
        for (Player p : mc.level.players()) {
            if (!ZedClient.isHero(p) || p.isInvisible() || ExecutionFx.performing(p.getId())) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            Vec3 v = new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
            int n = (r.nextFloat() < .6f ? 1 : 0) + (int) Math.min(4, v.horizontalDistance() * 7);
            for (int i = 0; i < n; i++) {
                Vec3 at = p.position().add(r.nextGaussian() * .2, .1 + r.nextDouble() * 1.75, r.nextGaussian() * .2);
                ShadowSmoke.Wisp w = ShadowSmoke.add(at, v.scale(.3).add(r.nextGaussian() * .006, .01, r.nextGaussian() * .006),
                        .12f + r.nextFloat() * .15f, 14 + r.nextInt(14), .2f + r.nextFloat() * .14f, r.nextFloat() < .35f ? ShadowSmoke.TENDRIL : ShadowSmoke.BLOB);
                w.rise = .0012f;
            }
        }
        for (var entry : ZedClient.all()) {
            ZedClient.State s = entry.getValue();
            if (s.wAlive && r.nextFloat() < .8f) {
                ShadowSmoke.Wisp w = ShadowSmoke.add(s.wPos.add(r.nextGaussian() * .22, .2 + r.nextDouble() * 1.6, r.nextGaussian() * .22), new Vec3(0, .012, 0), .16f + r.nextFloat() * .14f, 16, .32f, ShadowSmoke.BLOB);
                w.tint = SHADOW_TINT;
            }
            if (s.rAlive && r.nextFloat() < .6f) ShadowSmoke.add(s.rPos.add(r.nextGaussian() * .22, .2 + r.nextDouble() * 1.6, r.nextGaussian() * .22), new Vec3(0, .012, 0), .16f, 16, .28f, ShadowSmoke.BLOB);
            Entity marked = s.markTarget >= 0 ? mc.level.getEntity(s.markTarget) : null;
            if (marked != null && r.nextFloat() < .7f) {
                double a = r.nextDouble() * Math.PI * 2;
                Vec3 c = marked.position().add(Math.cos(a) * .7, marked.getBbHeight() * (.2 + .7 * r.nextDouble()), Math.sin(a) * .7);
                mc.level.addParticle(new DustParticleOptions(new Vector3f(.75f, .06f, .3f), .7f), c.x, c.y, c.z, 0, .02, 0);
            }
        }
    }
    /** Where a Death Mark copy is (its feet) at this age; null once its target is gone. */
    private static Vec3 copyAt(Copy c, float age, float partial) {
        var level = Minecraft.getInstance().level;
        Entity t = level == null ? null : level.getEntity(c.target());
        if (t == null) return null;
        Vec3 to = t.getPosition(partial);
        float k = ZedMotion.ease(Math.min(1, age / DASH_TICKS));
        Vec3 d = to.subtract(c.from()), side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        double bend = c.side() * Math.min(2.6, d.length() * .35);
        return c.from().add(d.scale(k)).add(side.scale(bend * Math.sin(Math.PI * k))).add(0, .5 * Math.sin(Math.PI * k), 0);
    }

    // ------------------------------------------------------------------ drawing helpers
    private static void put(VertexConsumer v, Matrix4f m, Vec3 p, int rgb, float a) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, Math.max(0, Math.min(1, a))).endVertex();
    }
    /** A ring in the plane across `normal`. */
    static void ring(FilmContext c, RenderType type, Vec3 centre, Vec3 normal, double radius, double width, int rgb, float alpha) {
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
    static void arc(FilmContext c, RenderType type, Vec3 centre, Vec3 normal, double radius, double a0, double a1, double width, int rgb, float alpha) {
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
    static void flat(FilmContext c, RenderType type, Vec3 a, Vec3 b, double w0, double w1, int rgb, float alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        VertexConsumer v = c.buffers().getBuffer(type);
        Matrix4f m = c.pose().last().pose();
        Vec3 side = d.cross(new Vec3(0, 1, 0)).normalize();
        put(v, m, a.add(side.scale(w0)), rgb, alpha); put(v, m, b.add(side.scale(w1)), rgb, alpha);
        put(v, m, b.subtract(side.scale(w1)), rgb, alpha); put(v, m, a.subtract(side.scale(w0)), rgb, alpha);
    }
    /**
     * A flat pool of shadow on the ground: irregular rim that creeps and wanders, a dense middle, soft
     * edge; spikes push out of the rim and draw back in.
     */
    static void pool(FilmContext c, Vec3 centre, double radius, float alpha, double seed, float time) {
        if (alpha <= .003f || radius <= .02) return;
        VertexConsumer v = c.buffers().getBuffer(FilmFx.SOFT);
        Matrix4f m = c.pose().last().pose();
        int n = 36;
        double[] rim = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            double wob = .14 * Math.sin(a * 3 + seed + time * .05) + .1 * Math.sin(a * 5 - seed * 2 + time * .08) + .06 * Math.sin(a * 9 + time * .13);
            // A few tongues of shadow reaching out and pulling back.
            double spike = Math.max(0, Math.sin(a * 4 + seed * 3 + time * .04)) * Math.max(0, Math.sin(time * .09 + i * .7 + seed)) * .35;
            rim[i] = radius * (1 + wob + spike);
        }
        Vec3 mid = centre.add(0, .02, 0);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 d0 = new Vec3(Math.cos(a0), 0, Math.sin(a0)), d1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
            put(v, m, mid, 0x030205, alpha); put(v, m, mid, 0x030205, alpha);
            put(v, m, mid.add(d1.scale(rim[i + 1] * .7)), 0x050308, alpha * .85f); put(v, m, mid.add(d0.scale(rim[i] * .7)), 0x050308, alpha * .85f);
            put(v, m, mid.add(d0.scale(rim[i] * .7)), 0x050308, alpha * .85f); put(v, m, mid.add(d1.scale(rim[i + 1] * .7)), 0x050308, alpha * .85f);
            put(v, m, mid.add(d1.scale(rim[i + 1])), 0x07050a, 0); put(v, m, mid.add(d0.scale(rim[i])), 0x07050a, 0);
        }
    }
    /** A point on a curved path from a to b, bulging sideways by `bend`. */
    private static Vec3 curve(Vec3 a, Vec3 b, double k, double bend) {
        Vec3 d = b.subtract(a), side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        return a.add(d.scale(k)).add(side.scale(bend * Math.sin(Math.PI * k))).add(0, .6 * Math.sin(Math.PI * k), 0);
    }
    /** The X of the Death Mark on a body, facing the camera: two crossed cuts. */
    private static void cross(FilmContext c, Vec3 at, double size, float alpha, float heat) {
        Vec3 facing = c.camera().subtract(at).normalize();
        Vec3 u = Math.abs(facing.y) < .9 ? facing.cross(new Vec3(0, 1, 0)).normalize() : facing.cross(new Vec3(1, 0, 0)).normalize(), w = facing.cross(u).normalize();
        Vec3 front = at.add(facing.scale(.45));
        for (int i = 0; i < 2; i++) {
            Vec3 d = (i == 0 ? u.add(w) : u.subtract(w)).normalize().scale(size);
            FilmFx.streak(c, front, front.add(d), .2 * size, DARK, .7f * alpha, 0, false);
            FilmFx.streak(c, front, front.subtract(d), .2 * size, DARK, .7f * alpha, 0, false);
        }
        for (int i = 0; i < 2; i++) {
            Vec3 d = (i == 0 ? u.add(w) : u.subtract(w)).normalize().scale(size);
            FilmFx.streak(c, front, front.add(d), .11 * size, RED, alpha, .1f * alpha, true);
            FilmFx.streak(c, front, front.subtract(d), .11 * size, RED, alpha, .1f * alpha, true);
            FilmFx.streak(c, front, front.add(d.scale(.8)), .035 * size, WHITE, .7f * alpha * heat, 0, true);
            FilmFx.streak(c, front, front.subtract(d.scale(.8)), .035 * size, WHITE, .7f * alpha * heat, 0, true);
        }
        FilmFx.glow(c, front, .9 * size, RED, .35f * alpha * (.6f + .4f * heat));
    }

    // ------------------------------------------------------------------ drawing: light, smoke and lines
    @SubscribeEvent public static void renderLight(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean marks = false;
        for (var entry : ZedClient.all()) if (entry.getValue().markTarget >= 0) marks = true;
        if (STARS.isEmpty() && BURSTS.isEmpty() && COPIES.isEmpty() && !marks && ShadowSmoke.count() == 0 && !ExecutionFx.any()) {
            ZedEyes.clear();
            return;
        }
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
            // Light first, so the smoke can veil it: the films' light inside the storm, the eyes.
            ExecutionFx.beforeSmoke(c, partial);
            ZedEyes.render(c, time);
            for (Copy cp : COPIES) copyTrail(c, cp, time - cp.start(), partial);
            // His aura: a soft dark patch on the ground under him.
            for (Player pl : mc.level.players()) {
                if (!ZedClient.isHero(pl) || pl.isInvisible() || ExecutionFx.performing(pl.getId())) continue;
                Vec3 feet = pl.getPosition(partial);
                pool(c, feet, .55, .32f, pl.getId(), time);
            }
            // The Shadow Slash's dark pool goes under its smoke.
            for (Burst b : BURSTS) if (b.kind() == FX_SPIN) spinPool(c, b, time);
            ShadowSmoke.render(c, partial, ExecutionFx.glowThrough());
            // Then the bright lines over it.
            for (Star s : STARS) {
                if (s.dead && s.travelled < .1) continue;
                Vec3 at = s.prev.lerp(s.pos, partial);
                double len = Math.min(3.2, s.travelled);
                int col = s.shadow ? VIOLET : RED;
                FilmFx.streak(c, at.subtract(s.dir.scale(len)), at, .3, DARK, 0, .4f, false);
                FilmFx.streak(c, at.subtract(s.dir.scale(len * .9)), at, .09, col, 0, .85f, true);
                FilmFx.streak(c, at.subtract(s.dir.scale(len * .5)), at, .025, WHITE, 0, .6f, true);
                FilmFx.glow(c, at, .4, col, .3f);
            }
            for (Burst b : BURSTS) burst(c, b, time, partial);
            // The X on the marked: burning, beating faster as the end nears.
            for (var entry : ZedClient.all()) {
                ZedClient.State s = entry.getValue();
                if (s.markTarget < 0) continue;
                Entity t = mc.level.getEntity(s.markTarget);
                if (t == null) continue;
                mark(c, t, time, partial, s.markLeft);
            }
            ExecutionFx.afterSmoke(c, partial);
            buffers.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /** The X burning on a marked body (and a second one flat on the ground under them). */
    private static void mark(FilmContext c, Entity t, float time, float partial, int left) {
        Vec3 feet = t.getPosition(partial), centre = feet.add(0, t.getBbHeight() * .55, 0);
        float lived = MARK_LIFE - left + partial;
        float urgency = 1 - Math.min(1, left / (float) MARK_LIFE);
        float beat = .5f + .5f * (float) Math.sin(time * (.45 + 1.4 * urgency));
        // Slams on big, settles; in its last moments it draws in on itself and burns white.
        float slam = 1 + .6f * (1 - FilmFx.ease(lived / 4f)), squeeze = left < 6 ? .65f + .35f * left / 6f : 1;
        double size = .62 * Math.max(.8, t.getBbHeight() / 1.8) * slam * squeeze;
        cross(c, centre, size * (1 + .06 * beat), .75f + .25f * beat, urgency);
        // On the ground.
        Vec3 g = feet.add(0, .06, 0);
        for (int i = 0; i < 2; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2 + time * .01;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(1.1 * slam);
            flat(c, FilmFx.SOFT, g.subtract(d), g.add(d), .22, .22, DARK, .45f);
            flat(c, FilmFx.ADD, g.subtract(d), g.add(d), .07, .07, RED, (.45f + .3f * beat));
        }
        ring(c, FilmFx.ADD, g, new Vec3(0, 1, 0), 1.25 * slam, .04, RED, .4f + .2f * beat);
        ShadowSmoke.light(centre, RED, .35f + .25f * beat, 1.4f);
    }
    /** The dark trail of a Death Mark copy: the curve it has run, densest right behind it. */
    private static void copyTrail(FilmContext c, Copy cp, float age, float partial) {
        if (age > DASH_TICKS + 3) return;
        float head = Math.min(DASH_TICKS, age + partial);
        Vec3 prev = null;
        for (int i = 0; i <= 10; i++) {
            float t = head - i * .7f;
            if (t < 0) break;
            Vec3 at = copyAt(cp, t, partial);
            if (at == null) return;
            at = at.add(0, 1, 0);
            if (prev != null) {
                float a = (1 - i / 10f) * (age > DASH_TICKS ? Math.max(0, 1 - (age - DASH_TICKS) / 3) : 1);
                FilmFx.streak(c, at, prev, .5 * (1 - i / 12f), DARK, .55f * a, .7f * a, false);
                FilmFx.streak(c, at, prev, .05, VIOLET, .3f * a, .4f * a, true);
            }
            prev = at;
        }
    }
    private static void spinPool(FilmContext c, Burst b, float time) {
        float age = time - b.start();
        if (age < 0 || age > 18) return;
        double radius = SPIN_RADIUS * (b.power() > .8f ? 1 : .8) * (.4 + .6 * FilmFx.ease(age / 4f));
        float a = .62f * (1 - FilmFx.ease((age - 6) / 12f));
        pool(c, b.pos(), radius, a, b.start() % 97, time);
    }
    private static void burst(FilmContext c, Burst b, float time, float partial) {
        float age = time - b.start();
        if (age < 0) return;
        Vec3 at = b.pos();
        switch (b.kind()) {
            case FX_SPIN -> {
                // Big red crescents sweeping round the rim of the pool, each wrapped in dark.
                float life = 10, k = age / life;
                if (k > 1) return;
                float a = (1 - k) * (1 - k) * (b.power() > .8f ? 1 : .75f);
                double radius = SPIN_RADIUS * (b.power() > .8f ? 1 : .8) * (.55 + .45 * (1 - Math.pow(1 - Math.min(1, k * 1.8), 2)));
                int col = b.power() > .8f ? RED : VIOLET;
                for (int i = 0; i < 3; i++) {
                    double start = i * Math.PI * 2 / 3 + k * 2.6 + b.start() % 7;
                    Vec3 tilt = new Vec3(Math.sin(i * 1.7 + 1) * .3, 1, Math.cos(i * 2.3) * .3);
                    Vec3 centre = at.add(0, .35 + .2 * i, 0);
                    double r = radius * (.92 + .05 * i);
                    arc(c, FilmFx.SOFT, centre, tilt, r, start, start + 2.1, .5, DARK, .65f * a);
                    arc(c, FilmFx.ADD, centre, tilt, r, start, start + 2.1, .22, col, a);
                    arc(c, FilmFx.ADD, centre, tilt, r, start + .25, start + 1.85, .06, WHITE, .8f * a);
                }
                FilmFx.ring(c, at.add(0, .08, 0), radius, .14, col, .55f * a, true);
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
            case FX_SWAP, FX_RETURN -> {
                // Energy trails curling between the two places (one each way for a swap), dark with a red edge.
                float life = 10, k = age / life;
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
            case FX_MARK_VANISH -> {
                // A dark ring on the ground where he went into the shadow (or came out of it).
                float k = age / 12;
                if (k > 1) return;
                ring(c, FilmFx.SOFT, at.add(0, .05, 0), new Vec3(0, 1, 0), .4 + 1.6 * k, .4, DARK, .65f * (1 - k));
                ring(c, FilmFx.ADD, at.add(0, .07, 0), new Vec3(0, 1, 0), .4 + 1.6 * k, .05, b.power() < 0 ? RED : VIOLET, .7f * (1 - k));
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
            }
            case FX_MARK_ARRIVE -> {
                // The copies vanish into the body: a flash, a ring snapping inward.
                float k = age / 7;
                if (k > 1) return;
                ring(c, FilmFx.ADD, at, c.camera().subtract(at), 1.6 * (1 - k), .12, RED, .9f * (1 - k * .5f));
                FilmFx.glow(c, at, 1.5 * (1 - k * .6), WHITE, .7f * (1 - k));
                FilmFx.glow(c, at, 2.4, RED, .45f * (1 - k));
            }
            case FX_MARK_POP -> {
                // The X shrinks to a point, a flash, then its two cuts fly apart and a sharp ring is thrown out.
                Vec3 facing = c.camera().subtract(at).normalize();
                if (age < 3) {
                    float k = age / 3;
                    cross(c, at, .62 * (1 - k * .6), 1, 1);
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
                Vec3 u = Math.abs(facing.y) < .9 ? facing.cross(new Vec3(0, 1, 0)).normalize() : facing.cross(new Vec3(1, 0, 0)).normalize(), w = facing.cross(u).normalize();
                for (int i = 0; i < 2; i++) {
                    Vec3 d = (i == 0 ? u.add(w) : u.subtract(w)).normalize(), apart = (i == 0 ? u.subtract(w) : u.add(w)).normalize().scale(1.4 * k);
                    Vec3 mid = at.add(facing.scale(.4)).add(apart);
                    FilmFx.streak(c, mid.subtract(d.scale(.9)), mid.add(d.scale(.9)), .09, RED, .9f * a, .9f * a, true);
                    mid = at.add(facing.scale(.4)).subtract(apart);
                    FilmFx.streak(c, mid.subtract(d.scale(.9)), mid.add(d.scale(.9)), .09, RED, .9f * a, .9f * a, true);
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

    // ------------------------------------------------------------------ drawing: the shadows, the copies and the shurikens
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
            int id = entry.getKey();
            if (s.wAlive) {
                float age = time - s.wBorn;
                Vec3 at = s.wFrom.lerp(s.wPos, ZedMotion.ease(age / SHADOW_TRAVEL));
                float alpha = .62f * ZedMotion.clamp(age / 4) * ZedMotion.clamp(s.wLeft / 12f);
                ZedMotion.Pose p = ZedMotion.sample(s.wAction, ZedClient.shadowClock(s, partial), time + 7);
                // Gliding out to its place, low.
                if (age < SHADOW_TRAVEL) { p.crouch += 3 * (1 - age / SHADOW_TRAVEL); p.bodyPitch = .4f * (1 - age / SHADOW_TRAVEL); }
                shadow(pose, buffers, cam, at, s.wYaw, p, time, alpha, -(id * 16 + 1), .7f, 1);
                any = true;
            }
            if (s.rAlive) {
                ZedMotion.Pose p = ZedMotion.idle(time + 13);
                shadow(pose, buffers, cam, s.rPos, s.rYaw, p, time, .5f * ZedMotion.clamp(s.rLeft / 12f), -(id * 16 + 2), .55f, 1);
                any = true;
            }
        }
        // The Death Mark copies, running in their lunge; they shrink into the body at the end.
        for (Copy cp : COPIES) {
            float age = time - cp.start();
            if (age > DASH_TICKS) continue;
            Vec3 at = copyAt(cp, age, partial), ahead = copyAt(cp, age + .5f, partial);
            if (at == null || ahead == null) continue;
            Vec3 d = ahead.subtract(at);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float k = age / DASH_TICKS;
            float shrink = k > .8f ? 1 - (k - .8f) / .2f * .75f : 1;
            shadow(pose, buffers, cam, at, yaw, ZedMotion.dash(time), time, .78f * Math.min(1, age / 2), cp.key(), 1.2f, shrink);
            any = true;
        }
        // The shurikens: four hooked blades round a hub, spinning flat.
        for (Star s : STARS) {
            if (s.dead && s.travelled < .1) continue;
            Vec3 at = s.prev.lerp(s.pos, partial);
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(Axis.YP.rotation((float) Math.atan2(s.dir.x, s.dir.z)));
            pose.mulPose(Axis.XP.rotation((float) (Math.PI / 2 - .3)));
            pose.mulPose(Axis.ZP.rotation(-(time - s.start) * 1.1f));
            int light = s.shadow ? LevelRenderer.getLightColor(mc.level, BlockPos.containing(at)) & 0xF0 : LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
            ZedBody.solid();
            if (s.shadow) ZedBody.shuriken(pose, buffers, light, 8, new float[]{.16f, .1f, .22f}, new float[]{.42f, .26f, .58f}, new float[]{.05f, .03f, .08f});
            else ZedBody.shuriken(pose, buffers, light, 8, ZedBody.SILVER, ZedBody.SILVER_HI, ZedBody.STEEL_DARK);
            pose.popPose();
            any = true;
        }
        if (ExecutionFx.solid(pose, buffers, cam, partial)) any = true;
        if (any) buffers.endBatch();
    }
    /**
     * A shadow of Zed standing at a place, facing a way, drawn as the living-entity renderer would draw
     * him; its eyes are reported under key, this lit.
     */
    static void shadow(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, Vec3 at, float yaw, ZedMotion.Pose p, float time, float alpha,
                       int key, float eyes, float scale) {
        if (alpha <= .01f || scale <= .01f) return;
        var mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.translate(0, .9 * (1 - scale), 0);
        pose.scale(-.9375f * scale, -.9375f * scale, .9375f * scale);
        pose.translate(0, -1.501, 0);
        ZedBody.capture = true; ZedBody.eyeRight = ZedBody.eyeLeft = null;
        ZedBody.eyes = eyes;
        try {
            ZedBody.draw(pose, buffers, LevelRenderer.getLightColor(mc.level, BlockPos.containing(at.add(0, 1, 0))), p, 0, 0, 0, 0, time, ZedBody.SHADOW, alpha);
        } finally {
            ZedBody.capture = false; ZedBody.eyes = 1;
        }
        if (ZedBody.eyeRight != null) ZedEyes.record(key, ZedBody.eyeRight, ZedBody.eyeLeft, time, eyes * Math.min(1, alpha * 1.4f));
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
        if (action == MARK_DASH || action == MARK_HIDDEN || action == ULTIMATE) return;
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
            // Silver vambrace between gold rims, the glove; three blades run out past the fist.
            ZedBody.box(p, b, light, -2.1f, -2, -.1f, 4.2f, 9, 4.2f, ZedBody.SILVER);
            ZedBody.box(p, b, light, -2.2f, 5.5f, -.2f, 4.4f, .5f, 4.4f, ZedBody.GOLD);
            ZedBody.box(p, b, light, -2.2f, 1f, -.2f, 4.4f, .5f, 4.4f, ZedBody.GOLD);
            ZedBody.box(p, b, light, -1.8f, -3.5f, .2f, 3.6f, 2.6f, 3.6f, ZedBody.BLACK);
            ZedBody.box(p, b, light, side * 2.2f - .4f, -.5f, .5f, .8f, 2.4f, 3f, ZedBody.GOLD);
            ZedBody.box(p, b, light, side * 2.4f - .25f, -14, 1.3f, .5f, 14, 1.4f, ZedBody.SILVER);
            ZedBody.box(p, b, light, side * 2.6f - .2f, -16, 1.5f, .4f, 2.2f, .9f, ZedBody.SILVER_HI);
            ZedBody.box(p, b, light, side * 2f - .2f, -11, .2f, .4f, 11, 1f, ZedBody.SILVER);
            ZedBody.box(p, b, light, side * 2f - .2f, -9, 2.8f, .4f, 9, 1f, ZedBody.SILVER);
            p.popPose();
        }
    }

    // ------------------------------------------------------------------ the camera
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        var g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        // Gone into shadow for the Death Mark: the world seen through the dark.
        ZedClient.State own = mc.player == null ? null : ZedClient.get(mc.player);
        if (own != null && (own.action == MARK_DASH || own.action == MARK_HIDDEN) && !FilmDirector.playing()) {
            g.fill(0, 0, w, h, HudStyle.alpha(0xFF0A0410, .28f));
            int edge = Math.max(24, h / 5);
            for (int i = 0; i < edge; i += 2) {
                float a = .55f * (1 - i / (float) edge);
                int col = HudStyle.alpha(0xFF050208, a);
                g.fill(0, i, w, i + 2, col); g.fill(0, h - i - 2, w, h - i, col);
                g.fill(i, 0, i + 2, h, col); g.fill(w - i - 2, 0, w - i, h, col);
            }
        }
        if (vignette < .01f) return;
        g.fill(0, 0, w, h, HudStyle.alpha(0xFF0A0410, vignette));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { STARS.clear(); BURSTS.clear(); COPIES.clear(); vignette = 0; ZedEyes.clear(); }

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
