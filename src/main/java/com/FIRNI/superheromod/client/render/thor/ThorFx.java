package com.FIRNI.superheromod.client.render.thor;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.film.FilmSessionClient;
import com.FIRNI.superheromod.heroes.thor.GodOfThunderSession;
import com.FIRNI.superheromod.network.packet.ThorFxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
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

import java.util.*;

import static com.FIRNI.superheromod.heroes.thor.ThorAction.*;

/**
 * Thor's lightning in the world. Strength comes from rarity: bolts flash for a few ticks and are
 * gone, leaving a faint afterglow and sparks. Also the thrown hammer and its trail, the trail of
 * his flight, the cracked ground of the Wakanda strike with lightning running through it and
 * pillars bursting out of it, the storm of the God of Thunder, the first-person hammer, and the
 * camera's reaction (shake, flash).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ThorFx {
    private record Bolt(List<ThorBolts.Seg> shape, Vec3 from, Vec3 to, long seed, double width, long start, float life, float crackle,
                        double jag, double branches) {}
    private record Flash(Vec3 at, double size, long start, float life, float alpha) {}
    private record Ring(Vec3 at, double radius, long start, float life) {}
    private static final class Cracks {
        Vec3 centre; double radius; long start; float life; boolean ultimate;
        final List<List<Vec3>> lines = new ArrayList<>();
        final List<double[]> reach = new ArrayList<>();
        final List<Vec3> nodes = new ArrayList<>();
        final Set<Integer> fired = new HashSet<>();
    }
    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final List<Cracks> CRACKS = new ArrayList<>();
    private static final Map<Integer, Deque<Vec3>> HAMMER_TRAILS = new HashMap<>(), FLIGHT_TRAILS = new HashMap<>();
    private static float screenFlash;
    private static boolean drawingProxy;

    private ThorFx() {}

    private static long now() { var l = Minecraft.getInstance().level; return l == null ? 0 : l.getGameTime(); }
    private static Random random() { return new Random(System.nanoTime()); }

    // ------------------------------------------------------------------ spawning
    public static void bolt(Vec3 from, Vec3 to, double width, float life, float crackle, double jag, double branches) {
        if (BOLTS.size() > 160) BOLTS.remove(0);
        long seed = random().nextLong();
        BOLTS.add(new Bolt(ThorBolts.bolt(from, to, seed, jag, branches, 2), from, to, seed, width, now(), life, crackle, jag, branches));
    }
    static void flash(Vec3 at, double size, float life, float alpha) {
        if (FLASHES.size() > 64) FLASHES.remove(0);
        FLASHES.add(new Flash(at, size, now(), life, alpha));
    }
    static void ring(Vec3 at, double radius, float life) {
        if (RINGS.size() > 32) RINGS.remove(0);
        RINGS.add(new Ring(at, radius, now(), life));
    }
    private static void sparks(Vec3 at, int count, double speed) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var r = level.getRandom();
        for (int i = 0; i < count; i++) {
            double vx = (r.nextDouble() - .5) * speed, vy = r.nextDouble() * speed * .8, vz = (r.nextDouble() - .5) * speed;
            level.addParticle(i % 3 == 0 ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, vx, vy, vz);
        }
    }
    /** Little bolts flying out of a point in roughly one direction. */
    private static void burst(Vec3 at, Vec3 dir, int count, double length, double width) {
        Random r = random();
        for (int i = 0; i < count; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * .7, r.nextGaussian());
            if (dir.lengthSqr() > 1e-4) d = d.scale(.8).add(dir.normalize().scale(1.2));
            d = d.normalize();
            bolt(at, at.add(d.scale(length * (.6 + r.nextDouble() * .6))), width, 4 + r.nextInt(3), 2, .35, .5);
        }
    }
    private static void shake(Vec3 at, float amount, float range) { ClientScreenShake.addFromSource(at, amount, range); }
    private static void screenFlash(Vec3 at, float amount, double range) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = mc.player.position().distanceTo(at);
        if (d < range) screenFlash = Math.max(screenFlash, amount * (float) (1 - d / range));
    }

    public static void receive(ThorFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 at = p.pos(), dir = p.dir();
        float power = p.power();
        switch (p.kind()) {
            case FX_SWING_HIT -> {
                burst(at, dir, 3, 1.2, .045); flash(at, .9, 4, .7f); sparks(at, 8, .5);
                shake(at, .12f, 6);
            }
            case FX_UPPER -> {
                bolt(at.add(0, -1, 0), at.add(0, 2.8, 0), .07, 5, 3, .3, .5);
                burst(at, new Vec3(0, 1, 0), 3, 1.4, .045);
                flash(at, 1.6, 5, .8f); ring(at.add(0, -1, 0), 2.2, 10); sparks(at, 14, .7);
                shake(at, .32f, 8);
            }
            case FX_HAMMER_HIT -> {
                burst(at, dir.scale(-1), 5, 2.2 * power, .05); flash(at, 1.9 * power, 6, .85f); sparks(at, 18, .8);
                ring(at, 1.6 * power, 9);
                shake(at, .38f * power, 10);
            }
            case FX_CATCH -> { burst(at, Vec3.ZERO, 3, .7, .03); flash(at, .9, 4, .7f); sparks(at, 6, .3); shake(at, .1f, 3); }
            case FX_CLANG -> { burst(at, dir, 2, .9, .035); flash(at, .8, 3, .7f); sparks(at, 16, .9); shake(at, .15f, 5); }
            case FX_COUNTER -> {
                Vec3 hand = at.subtract(dir.scale(1.6));
                bolt(hand, at, .09, 5, 3, .25, .4);
                burst(at, dir, 4, 1.6, .05); flash(at, 1.5, 6, .9f); sparks(at, 18, .9);
                shake(at, .35f, 8); screenFlash(at, .15f, 8);
            }
            case FX_SKY_BOLT -> skyBolt(at, power);
            case FX_TAKEOFF -> takeoff(at, power);
            case FX_SHOUT -> shout(at, power);
            case FX_CRACKS -> impact(at, power, false, p.seed());
            case FX_ULT_IMPACT -> impact(at, power, true, p.seed());
            case FX_RELEASE -> { flash(at, .7, 3, .6f); sparks(at, 6, .4); burst(at, dir, 2, .8, .03); }
            default -> {}
        }
    }

    private static void skyBolt(Vec3 at, float power) {
        Random r = random();
        Vec3 top = at.add((r.nextDouble() - .5) * 8, 38, (r.nextDouble() - .5) * 8);
        bolt(top, at, .32 * power, 9, 4, .22, .8);
        bolt(top.add(3, -6, 2), at.add((r.nextDouble() - .5) * 2, 4, (r.nextDouble() - .5) * 2), .12 * power, 6, 3, .3, .6);
        flash(at, 4.5 * power, 10, 1f);
        flash(top.lerp(at, .5), 9 * power, 6, .35f);
        ring(at.add(0, -1, 0), 3.5 * power, 12);
        sparks(at, 30, 1.2);
        shake(at, .7f * power, 24);
        screenFlash(at, .45f, 40);
    }
    private static void takeoff(Vec3 feet, float power) {
        var level = Minecraft.getInstance().level;
        Random r = random();
        for (int i = 0; i < 4; i++) {
            double a = r.nextDouble() * Math.PI * 2, l = 1 + r.nextDouble() * 1.4;
            bolt(feet.add(0, .1, 0), feet.add(Math.cos(a) * l, .05, Math.sin(a) * l), .035, 4, 2, .35, .3);
        }
        ring(feet.add(0, .05, 0), 2.4 * power, 12);
        flash(feet.add(0, .3, 0), .9, 5, .5f);
        if (level != null) for (int i = 0; i < 18; i++) {
            double a = i * Math.PI * 2 / 18;
            level.addParticle(ParticleTypes.CLOUD, feet.x + Math.cos(a) * .6, feet.y + .1, feet.z + Math.sin(a) * .6, Math.cos(a) * .25, .02, Math.sin(a) * .25);
        }
        shake(feet, .15f * power, 6);
    }
    private static void shout(Vec3 at, float power) {
        Random r = random();
        Vec3 sky = at.add(0, 16, 0);
        flash(sky, 22 * power, 14, .45f);
        for (int i = 0; i < 6; i++) {
            Vec3 a = sky.add((r.nextDouble() - .5) * 30, 6 + r.nextDouble() * 10, (r.nextDouble() - .5) * 30);
            Vec3 b = a.add((r.nextDouble() - .5) * 22, -r.nextDouble() * 6, (r.nextDouble() - .5) * 22);
            bolt(a, b, .18, 7 + r.nextInt(5), 3, .3, .9);
        }
        shake(at, .45f, 20);
        screenFlash(at, .3f, 30);
    }

    // ------------------------------------------------------------------ cracked ground
    private static void impact(Vec3 at, float radius, boolean ultimate, int seed) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Cracks c = new Cracks();
        c.centre = ground(at); c.radius = radius; c.start = now(); c.life = ultimate ? 220 : 130; c.ultimate = ultimate;
        Random r = new Random(seed);
        int mains = 7 + (int) (radius * .6);
        for (int i = 0; i < mains; i++) {
            double angle = (i + r.nextDouble() * .6) * Math.PI * 2 / mains;
            crack(c, r, c.centre, angle, radius * (.7 + r.nextDouble() * .45), 0, 2);
        }
        // A web: arcs running round between the spokes.
        for (double ringR : new double[]{radius * .38, radius * .66}) {
            double a = r.nextDouble() * Math.PI * 2;
            int pieces = (int) (ringR * 2.2);
            List<Vec3> line = new ArrayList<>();
            double[] d = new double[pieces + 1];
            for (int k = 0; k <= pieces; k++) {
                double ang = a + k * Math.PI * 2 / pieces * .8, rr = ringR * (.9 + r.nextDouble() * .2);
                line.add(ground(c.centre.add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr)));
                d[k] = rr;
            }
            c.lines.add(line); c.reach.add(d);
        }
        if (CRACKS.size() > 4) CRACKS.remove(0);
        CRACKS.add(c);
        flash(c.centre.add(0, .5, 0), radius * .55, 8, 1f);
        ring(c.centre.add(0, .05, 0), radius * 1.2, 16);
        ring(c.centre.add(0, .05, 0), radius * .6, 10);
        var state = level.getBlockState(BlockPos.containing(c.centre.x, c.centre.y - .5, c.centre.z));
        var rand = level.getRandom();
        for (int i = 0; i < (ultimate ? 90 : 55); i++) {
            double a = rand.nextDouble() * Math.PI * 2, d = rand.nextDouble() * radius * .6;
            double vx = Math.cos(a) * (.1 + rand.nextDouble() * .3), vz = Math.sin(a) * (.1 + rand.nextDouble() * .3);
            if (!state.isAir()) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), c.centre.x + Math.cos(a) * d, c.centre.y + .2, c.centre.z + Math.sin(a) * d, vx, .4 + rand.nextDouble() * .5, vz);
            if (i % 3 == 0) level.addParticle(ParticleTypes.CLOUD, c.centre.x + Math.cos(a) * d, c.centre.y + .3, c.centre.z + Math.sin(a) * d, vx * .6, .05, vz * .6);
        }
        sparks(c.centre.add(0, .4, 0), 40, 1.4);
        shake(c.centre, ultimate ? 1.5f : 1.0f, (float) radius * 2);
        screenFlash(c.centre, ultimate ? .9f : .4f, radius * 2.5);
    }
    private static void crack(Cracks c, Random r, Vec3 from, double angle, double length, double startDist, int depth) {
        List<Vec3> line = new ArrayList<>();
        List<Double> dist = new ArrayList<>();
        line.add(ground(from)); dist.add(startDist);
        Vec3 at = from;
        double walked = 0;
        while (walked < length) {
            angle += (r.nextDouble() - .5) * .7;
            double step = .45 + r.nextDouble() * .5;
            at = at.add(Math.cos(angle) * step, 0, Math.sin(angle) * step);
            walked += step;
            line.add(ground(at)); dist.add(startDist + walked);
            if (depth > 0 && r.nextDouble() < .16) {
                double side = (r.nextBoolean() ? 1 : -1) * (.6 + r.nextDouble() * .6);
                crack(c, r, at, angle + side, (length - walked) * (.35 + r.nextDouble() * .3), startDist + walked, depth - 1);
            }
        }
        double[] d = new double[dist.size()];
        for (int i = 0; i < d.length; i++) d[i] = dist.get(i);
        c.lines.add(line); c.reach.add(d);
        c.nodes.add(line.get(line.size() - 1));
        if (line.size() > 6) c.nodes.add(line.get(line.size() / 2));
    }
    /** The ground under a point (a little above it so the cracks sit on the surface). */
    private static Vec3 ground(Vec3 p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return p;
        var hit = mc.level.clip(new ClipContext(p.add(0, 2.5, 0), p.add(0, -4, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS ? p.add(0, .03, 0) : hit.getLocation().add(0, .03, 0);
    }

    // ------------------------------------------------------------------ per tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        long now = now();
        screenFlash *= .72f;
        BOLTS.removeIf(b -> now - b.start() > b.life() + 4);
        FLASHES.removeIf(f -> now - f.start() > f.life());
        RINGS.removeIf(r -> now - r.start() > r.life());
        CRACKS.removeIf(c -> now - c.start > c.life);
        Random r = random();
        for (Cracks c : CRACKS) crackLife(c, now - c.start, r);
        for (Player p : mc.level.players()) {
            if (!ThorClient.isThor(p)) continue;
            ThorClient.State s = ThorClient.get(p);
            // The thrown hammer's trail.
            Deque<Vec3> trail = HAMMER_TRAILS.computeIfAbsent(p.getId(), id -> new ArrayDeque<>());
            if (s != null && s.hammerOut()) { trail.addFirst(s.hammer); while (trail.size() > 10) trail.removeLast(); }
            else if (!trail.isEmpty()) trail.removeLast();
            // Flight: a fine electric trail, and now and then a fork of lightning dropping from him.
            float ult = ThorClient.ultimateTime(p, 0);
            Vec3 body = ultimateBody(p, 0);
            boolean flying = s != null && s.flying() && !p.onGround() || ult >= ULT_LAUNCH && ult < ULT_IMPACT;
            Deque<Vec3> fly = FLIGHT_TRAILS.computeIfAbsent(p.getId(), id -> new ArrayDeque<>());
            if (flying) { fly.addFirst(body.add(0, 1.1, 0)); while (fly.size() > 16) fly.removeLast(); }
            else if (!fly.isEmpty()) fly.removeLast();
            if (flying && r.nextFloat() < (ult >= 0 ? .5f : .07f)) {
                Vec3 from = body.add(0, .8, 0);
                bolt(from, from.add((r.nextDouble() - .5) * 2, -1.5 - r.nextDouble() * 2.5, (r.nextDouble() - .5) * 2), ult >= 0 ? .06 : .03, 4, 2, .4, .5);
            }
            if (ult >= 0) ultimateTick(p, ult, r);
        }
        HAMMER_TRAILS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        FLIGHT_TRAILS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }
    /** Lightning running through the cracks, and pillars bursting up out of them. */
    private static void crackLife(Cracks c, long age, Random r) {
        if (age > c.life - 30 || c.lines.isEmpty()) return;
        double open = c.radius / 8;
        // Hops along the cracks: crack, electricity, the next crack.
        int hops = c.ultimate ? 4 : 2;
        for (int i = 0; i < hops; i++) {
            if (r.nextFloat() > .55f) continue;
            int line = r.nextInt(c.lines.size());
            List<Vec3> pts = c.lines.get(line);
            if (pts.size() < 3) continue;
            int at = r.nextInt(pts.size() - 2);
            if (c.reach.get(line)[at] > age * open) continue;
            Vec3 a = pts.get(at), b = pts.get(Math.min(pts.size() - 1, at + 2 + r.nextInt(3)));
            bolt(a.add(0, .05, 0), b.add(0, .05, 0), .035, 3, 2, .5, .4);
        }
        // Pillars: staggered as the cracks reach them, each goes up, bursts and throws sparks sideways.
        for (int i = 0; i < c.nodes.size(); i++) {
            if (c.fired.contains(i)) continue;
            Vec3 n = c.nodes.get(i);
            double d = n.distanceTo(c.centre);
            if (d / open + 3 + (i % 5) * 2.5 > age) continue;
            c.fired.add(i);
            if (i % (c.ultimate ? 1 : 2) != 0) continue;
            double height = (c.ultimate ? 6 : 4) + r.nextDouble() * 3;
            Vec3 top = n.add((r.nextDouble() - .5) * .8, height, (r.nextDouble() - .5) * .8);
            bolt(n, top, c.ultimate ? .16 : .11, 7, 3, .18, .5);
            for (int k = 0; k < 4; k++) {
                double a = r.nextDouble() * Math.PI * 2;
                bolt(top, top.add(Math.cos(a) * (1.5 + r.nextDouble() * 2), -r.nextDouble() * 1.5, Math.sin(a) * (1.5 + r.nextDouble() * 2)), .05, 5, 2, .4, .4);
            }
            flash(top, 1.6, 6, .8f);
            flash(n.add(0, .2, 0), 1.2, 8, .7f);
            sparks(top, 8, .8);
            var mc = Minecraft.getInstance();
            if (mc.level != null && r.nextFloat() < .4f)
                mc.level.playLocalSound(n.x, n.y, n.z, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT, net.minecraft.sounds.SoundSource.PLAYERS, .5f, 1.4f + r.nextFloat() * .4f, false);
        }
    }

    // ------------------------------------------------------------------ God of Thunder in the world
    /** Where this Thor's body is drawn: during the film's leap, up in the air above the real one. */
    public static Vec3 ultimateBody(Player p, float partial) {
        Vec3 pos = p.getPosition(partial);
        float t = ThorClient.ultimateTime(p, partial);
        if (t < ULT_LAUNCH || t >= ULT_IMPACT) return pos;
        var film = FilmSessionClient.get(p.getId());
        var level = Minecraft.getInstance().level;
        if (film == null || level == null) return pos;
        Vec3 forward = FilmSessionClient.forward(film);
        var target = level.getEntity(film.target);
        double land = 5;
        if (target != null) {
            Vec3 d = target.position().subtract(film.anchor);
            land = Math.max(0, Math.sqrt(d.x * d.x + d.z * d.z) - GodOfThunderSession.LAND_SHORT);
        }
        double[] o = ThorMotion.ultimateOffset(t, land);
        return pos.add(forward.scale(o[2])).add(0, o[1], 0);
    }
    private static void ultimateTick(Player p, float t, Random r) {
        var film = FilmSessionClient.get(p.getId());
        var level = Minecraft.getInstance().level;
        if (film == null || level == null) return;
        Vec3 at = p.position();
        // Bolts inside the turning storm, more and closer as it builds.
        if (t > ULT_SKY - 10 && t < ULT_IMPACT + 20 && r.nextFloat() < .35f) {
            double a = r.nextDouble() * Math.PI * 2, d = 6 + r.nextDouble() * 16;
            Vec3 a0 = at.add(Math.cos(a) * d, 24 + r.nextDouble() * 6, Math.sin(a) * d);
            bolt(a0, a0.add((r.nextDouble() - .5) * 14, -3 - r.nextDouble() * 5, (r.nextDouble() - .5) * 14), .12, 5, 2, .3, .8);
        }
        // The power-up: lightning crawling up and down him.
        if (t > ULT_STRIKE && t < ULT_TARGET && r.nextFloat() < .5f) {
            Vec3 c = at.add(0, 1, 0);
            bolt(c.add((r.nextDouble() - .5), -1, (r.nextDouble() - .5)), c.add((r.nextDouble() - .5) * 2, 1.6, (r.nextDouble() - .5) * 2), .05, 3, 2, .4, .5);
        }
        // The target: electricity gathering round them.
        var target = level.getEntity(film.target);
        if (target != null && t > ULT_TARGET + 8 && t < ULT_IMPACT && r.nextFloat() < .55f) {
            Vec3 c = target.position().add(0, target.getBbHeight() * .5, 0);
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 a0 = c.add(Math.cos(a) * 1.4, (r.nextDouble() - .5) * 2, Math.sin(a) * 1.4);
            bolt(a0, a0.add((r.nextDouble() - .5) * 1.5, (r.nextDouble() - .5) * 1.5, (r.nextDouble() - .5) * 1.5), .03, 3, 2, .5, .3);
            if (r.nextFloat() < .1f) bolt(c.add(0, 18, 0), c.add((r.nextDouble() - .5) * 3, -.9, (r.nextDouble() - .5) * 3), .07, 4, 2, .3, .5);
        }
        if (Math.abs(t - ULT_BOOM) < .6f) { flash(at.add(0, 18, 0), 26, 12, .5f); shake(at, .6f, 30); }
    }

    // ------------------------------------------------------------------ drawing: light
    @SubscribeEvent public static void renderLight(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean storm = false;
        for (Player p : mc.level.players()) if (ThorClient.isThor(p) && ThorClient.ultimateTime(p, 0) >= 0) storm = true;
        if (BOLTS.isEmpty() && FLASHES.isEmpty() && RINGS.isEmpty() && CRACKS.isEmpty() && HAMMER_TRAILS.isEmpty() && FLIGHT_TRAILS.isEmpty() && !storm) return;
        float partial = e.getPartialTick();
        long now = now();
        float time = now + partial;
        PoseStack p = e.getPoseStack();
        p.pushPose();
        Vec3 cam = e.getCamera().getPosition();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var buffers = mc.renderBuffers().bufferSource();
        var rotation = e.getCamera().rotation();
        var r = new org.joml.Vector3f(1, 0, 0).rotate(rotation); var u = new org.joml.Vector3f(0, 1, 0).rotate(rotation);
        FilmContext c = new FilmContext(p, buffers, cam, new Vec3(r.x, r.y, r.z), new Vec3(u.x, u.y, u.z), time, 0, partial);
        Matrix4f m = p.last().pose();
        try {
            for (Cracks k : CRACKS) drawCracks(c, m, k, time);
            for (Ring ring : RINGS) {
                float age = (time - ring.start()) / ring.life();
                if (age < 0 || age > 1) continue;
                double grow = 1 - Math.pow(1 - age, 3);
                FilmFx.ring(c, ring.at(), ring.radius() * grow, .25 + .5 * age, ThorBolts.BODY, .55f * (1 - age), true);
            }
            for (Bolt b : BOLTS) {
                float age = time - b.start();
                if (age < 0) continue;
                // The main shape is there for a blink; then only a fading afterglow of it.
                float alpha = age < b.life() ? 1 - (float) Math.pow(age / b.life(), 2) * .7f : Math.max(0, .3f * (1 - (age - b.life()) / 4));
                List<ThorBolts.Seg> shape = b.shape();
                if (age < b.crackle()) shape = ThorBolts.bolt(b.from(), b.to(), b.seed() + (long) age * 7919L, b.jag(), b.branches(), 2);
                ThorBolts.draw(buffers.getBuffer(FilmFx.ADD), m, shape, cam, b.width() * (age < 2 ? 1.25 : 1), alpha);
            }
            for (Flash f : FLASHES) {
                float age = (time - f.start()) / f.life();
                if (age < 0 || age > 1) continue;
                FilmFx.glow(c, f.at(), f.size() * (.8 + .4 * age), ThorBolts.BODY, f.alpha() * (1 - age) * (1 - age));
                FilmFx.glow(c, f.at(), f.size() * .35, ThorBolts.CORE, f.alpha() * (1 - age) * (1 - age) * (1 - age));
            }
            for (var entry : HAMMER_TRAILS.entrySet()) trail(c, m, entry.getValue(), .09, .7f, time, entry.getKey());
            for (var entry : FLIGHT_TRAILS.entrySet()) trail(c, m, entry.getValue(), .05, .45f, time, entry.getKey() + 999);
            for (Player pl : mc.level.players()) {
                if (!ThorClient.isThor(pl)) continue;
                float t = ThorClient.ultimateTime(pl, partial);
                if (t >= 0) storm(c, pl, t);
            }
            buffers.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    private static void drawCracks(FilmContext c, Matrix4f m, Cracks k, float time) {
        float age = time - k.start;
        float fade = Math.min(1, (k.life - age) / 40f);
        if (fade <= 0) return;
        double open = k.radius / 8;
        for (int i = 0; i < k.lines.size(); i++) {
            List<Vec3> line = k.lines.get(i);
            double[] reach = k.reach.get(i);
            for (int j = 0; j + 1 < line.size(); j++) {
                if (reach[j + 1] > age * open) break;
                Vec3 a = line.get(j), b = line.get(j + 1);
                double near = 1 - Math.min(1, reach[j] / (k.radius * 1.1));
                double width = .08 + .2 * near;
                // The gash, then the light inside it pulsing outward along the crack.
                flat(c.buffers().getBuffer(FilmFx.SOFT), m, a, b, width, 0x0a0a10, .85f * fade);
                float pulse = (float) Math.max(0, Math.sin(reach[j] * 1.7 - age * .55));
                float glow = (.35f + .65f * pulse) * fade * (age < 20 ? 1 : .55f + .45f * (float) near);
                flat(c.buffers().getBuffer(FilmFx.ADD), m, a.add(0, .01, 0), b.add(0, .01, 0), width * .55, ThorBolts.GLOW, glow * .7f);
                flat(c.buffers().getBuffer(FilmFx.ADD), m, a.add(0, .015, 0), b.add(0, .015, 0), width * .2, ThorBolts.CORE, glow);
            }
        }
        if (age < 30) FilmFx.glow(c, k.centre.add(0, .3, 0), k.radius * .25 * (1 - age / 30), ThorBolts.BODY, .5f * (1 - age / 30));
    }
    /** A strip lying on the ground from a to b. */
    private static void flat(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double width, int rgb, float alpha) {
        Vec3 side = b.subtract(a).cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-10 || alpha <= .003f) return;
        side = side.normalize().scale(width);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 edge = side.scale(s);
            ThorBolts.put(v, m, a, rgb, alpha); ThorBolts.put(v, m, b, rgb, alpha);
            ThorBolts.put(v, m, b.add(edge), rgb, 0); ThorBolts.put(v, m, a.add(edge), rgb, 0);
        }
    }
    private static void trail(FilmContext c, Matrix4f m, Deque<Vec3> points, double width, float alpha, float time, int salt) {
        if (points.size() < 2) return;
        Vec3[] p = points.toArray(new Vec3[0]);
        for (int i = 0; i + 1 < p.length; i++) {
            float fade = alpha * (1 - (float) i / p.length);
            ThorBolts.ribbon(c.buffers().getBuffer(FilmFx.ADD), m, p[i], p[i + 1], c.camera(), width * (1 - (double) i / p.length), ThorBolts.BODY, fade);
        }
        // A fine electric thread wound round the trail.
        int frame = (int) (time / 2);
        Vec3 head = p[0], tail = p[Math.min(p.length - 1, 6)];
        if (head.distanceTo(tail) > .5)
            ThorBolts.draw(c.buffers().getBuffer(FilmFx.ADD), m, ThorBolts.bolt(head, tail, frame * 31L + salt, .25, .3, 1), c.camera(), width * .35, alpha * .8f);
    }
    /** The sky turning over the battlefield: dark cloud wheeling round, faster and faster. */
    private static void storm(FilmContext c, Player p, float t) {
        float build = FilmFx.ease((t - (ULT_SKY - 30)) / 40f) * (1 - FilmFx.ease((t - (ULT_IMPACT + 10)) / 20f));
        if (build <= .01f) return;
        Vec3 eye = p.position().add(0, 26, 0);
        double spin = t * (.02 + .05 * FilmFx.ease((t - ULT_SKY) / 30f));
        for (int ring = 0; ring < 4; ring++) {
            int n = 18 + ring * 4;
            double radius = 7 + ring * 7;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + spin * (1.6 - ring * .3) + ring;
                Vec3 at = eye.add(Math.cos(a) * radius, -ring * 1.2 + Math.sin(a * 3 + t * .05) * .8, Math.sin(a) * radius);
                FilmFx.puff(c, at, 6 + ring * 1.5, ring % 2 == 0 ? 0x1c1f28 : 0x262a36, .55f * build);
            }
        }
        FilmFx.glow(c, eye.add(0, -2, 0), 9, ThorBolts.HAZE, .18f * build * (.6f + .4f * (float) Math.sin(t * .7)));
    }

    // ------------------------------------------------------------------ drawing: the thrown hammer and the leap
    @SubscribeEvent public static void renderSolid(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        boolean any = false;
        for (Player p : mc.level.players()) {
            if (!ThorClient.isThor(p)) continue;
            ThorClient.State s = ThorClient.get(p);
            float ult = ThorClient.ultimateTime(p, partial);
            if (s != null && s.hammerOut() && ult < 0) { thrownHammer(pose, buffers, cam, p, s, partial); any = true; }
            if (ult >= ULT_LAUNCH && ult < ULT_IMPACT) { leaping(pose, buffers, cam, p, partial); any = true; }
        }
        if (any) buffers.endBatch();
    }
    private static void thrownHammer(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, Player p, ThorClient.State s, float partial) {
        var level = Minecraft.getInstance().level;
        float k = Math.min(1, (level.getGameTime() - s.received) + partial);
        Vec3 at = s.hammerPrev.lerp(s.hammer, k);
        Vec3 dir = s.hammer.subtract(s.hammerPrev);
        float time = level.getGameTime() + partial;
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        if (dir.lengthSqr() > 1e-4) {
            float yaw = (float) Math.atan2(dir.x, dir.z);
            pose.mulPose(Axis.YP.rotation(yaw));
        }
        // End over end, head leading.
        pose.mulPose(Axis.XP.rotation(dir.lengthSqr() > 1e-4 ? time * 1.1f : time * .15f));
        pose.translate(0, -5 / 16f, 0);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(at));
        Mjolnir.draw(pose, buffers, light, .6f, time);
        pose.popPose();
    }
    private static void leaping(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, Player p, float partial) {
        var mc = Minecraft.getInstance();
        Vec3 at = ultimateBody(p, partial);
        var dispatcher = mc.getEntityRenderDispatcher();
        float yaw = p.getViewYRot(partial);
        drawingProxy = true;
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(p, at.x - cam.x, at.y - cam.y, at.z - cam.z, yaw, partial, pose, buffers, Mjolnir.FULL_BRIGHT);
        } finally {
            drawingProxy = false;
            dispatcher.setRenderShadow(mc.options.entityShadows().get());
        }
    }
    /** During the film's leap the real body stays where it is held; only the flying one is drawn. */
    @SubscribeEvent public static void hideHeld(RenderPlayerEvent.Pre e) {
        if (drawingProxy || !ThorClient.isThor(e.getEntity())) return;
        float t = ThorClient.ultimateTime(e.getEntity(), e.getPartialTick());
        if (t >= ULT_LAUNCH && t < ULT_IMPACT) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ first person
    @SubscribeEvent public static void hand(RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !ThorClient.isThor(mc.player)) return;
        if (e.getHand() != InteractionHand.MAIN_HAND) { e.setCanceled(true); return; }
        e.setCanceled(true);
        ThorClient.State s = ThorClient.get(mc.player);
        if (s != null && s.hammerOut()) return;
        float t = s == null ? 0 : ThorClient.clock(s, e.getPartialTick());
        int action = s == null ? IDLE : s.action;
        float time = mc.player.tickCount + e.getPartialTick();
        PoseStack p = e.getPoseStack();
        p.pushPose();
        float bob = (float) Math.sin(time * .1) * .01f;
        p.translate(.48, -.62 + bob, -.85);
        p.mulPose(Axis.XP.rotationDegrees(-12));
        p.mulPose(Axis.ZP.rotationDegrees(-8));
        float power = s != null && s.powered() ? .5f : 0;
        switch (action) {
            case SWING_RIGHT -> {
                float wind = ThorMotion.k(t, 0, 4), strike = ThorMotion.snap(t, 4, SWING_HIT + 1);
                p.translate(-.55 * wind + .9 * strike, .1 * wind, -.1 * strike);
                p.mulPose(Axis.YP.rotationDegrees(55 * wind - 120 * strike));
                p.mulPose(Axis.ZP.rotationDegrees(70 * strike));
            }
            case SWING_LEFT -> {
                float strike = ThorMotion.snap(t, 3, SWING_HIT + 1);
                p.translate(.35 - 1.0 * strike, .1, -.1 * strike);
                p.mulPose(Axis.YP.rotationDegrees(-65 + 130 * strike));
                p.mulPose(Axis.ZP.rotationDegrees(70 - 140 * strike));
            }
            case UPPERCUT -> {
                float load = ThorMotion.k(t, 0, 6), drive = ThorMotion.snap(t, 6, UPPER_HIT);
                p.translate(-.1 * drive, -.3 * load + .55 * drive, -.15 * drive);
                p.mulPose(Axis.XP.rotationDegrees(45 * load - 110 * drive));
                power = Math.max(power, drive);
            }
            case THROW -> {
                float draw = ThorMotion.k(t, 0, THROW_WINDUP - 1);
                p.translate(.1 * draw, .25 * draw, .35 * draw);
                p.mulPose(Axis.XP.rotationDegrees(50 * draw));
            }
            case CATCH -> { float hit = 1 - ThorMotion.k(t, 0, CATCH_TICKS); p.translate(0, -.1 * hit, .15 * hit); power = Math.max(power, hit); }
            case GUARD, COUNTER -> {
                p.translate(-.42, .22, -.25);
                p.mulPose(Axis.ZP.rotation(action == GUARD ? t * 1.3f + .06f * t * t / (1 + t * .1f) : 0));
                power = Math.max(power, .5f);
            }
            default -> {}
        }
        if (s != null && s.flying() && action == IDLE) {
            p.translate(.05, .35, .1);
            p.mulPose(Axis.XP.rotation(time * 1.4f));
        }
        p.scale(.9f, .9f, .9f);
        p.translate(0, -2 / 16f, 0);
        Mjolnir.draw(p, e.getMultiBufferSource(), e.getPackedLight(), power, time);
        p.popPose();
    }

    // ------------------------------------------------------------------ the camera's reaction
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post e) {
        if (screenFlash < .01f) return;
        var g = e.getGuiGraphics();
        g.fill(0, 0, e.getWindow().getGuiScaledWidth(), e.getWindow().getGuiScaledHeight(), HudStyle.alpha(0xFFE6F0FF, Math.min(.85f, screenFlash)));
    }

    private static void clear() {
        BOLTS.clear(); FLASHES.clear(); RINGS.clear(); CRACKS.clear(); HAMMER_TRAILS.clear(); FLIGHT_TRAILS.clear(); screenFlash = 0;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                var renderer = e.getSkin(skin);
                if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) player.addLayer(new ThorLayer(player));
            }
        }
    }
}
