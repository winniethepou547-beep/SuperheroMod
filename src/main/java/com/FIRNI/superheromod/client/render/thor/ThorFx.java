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
    /** A tiny shock ring where the hammer lands, facing the way the blow travels. */
    private record Shock(Vec3 at, Vec3 dir, long start, double size) {}
    private static final List<Shock> SHOCKS = new ArrayList<>();
    private static final float SHOCK_LIFE = 5;
    static void shock(Vec3 at, Vec3 dir, double size) {
        if (dir.lengthSqr() < 1e-6) dir = new Vec3(0, 1, 0);
        if (SHOCKS.size() > 24) SHOCKS.remove(0);
        SHOCKS.add(new Shock(at, dir.normalize(), now(), size));
    }
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
                shock(at, dir, .8);
                burst(at, dir, 3, 1.2, .045); flash(at, .9, 4, .7f); sparks(at, 8, .5);
                shake(at, .12f, 6);
            }
            case FX_UPPER -> {
                shock(at, new Vec3(0, 1, 0), .9);
                bolt(at.add(0, -1, 0), at.add(0, 2.8, 0), .07, 5, 3, .3, .5);
                burst(at, new Vec3(0, 1, 0), 3, 1.4, .045);
                flash(at, 1.6, 5, .8f); ring(at.add(0, -1, 0), 2.2, 10); sparks(at, 14, .7);
                shake(at, .32f, 8);
            }
            case FX_HAMMER_HIT -> {
                shock(at, dir, .8 * power);
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
            case FX_BEAM_HIT -> { sparks(at, 6, .6); flash(at, 1.1, 3, .6f); shake(at, .22f, 3); }
            case FX_CHARGED -> {
                var mc2 = Minecraft.getInstance();
                var who = mc2.level.getNearestPlayer(at.x, at.y, at.z, 2, false);
                Vec3 hammer = who == null ? at.add(0, 1.3, 0) : chargedHammer(who);
                flash(hammer, 1.2, 5, .8f); burst(hammer, Vec3.ZERO, 4, .9, .03); sparks(hammer, 10, .5);
            }
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
            float filmT = ThorClient.ultimateTime(p, 0);
            Ult filmView = filmT >= 0 ? ult(p) : null;
            Vec3 filmHammer = filmView == null ? null : filmView.path.hammer(filmT);
            if (filmHammer != null) { trail.addFirst(filmView.world(filmHammer)); while (trail.size() > 10) trail.removeLast(); }
            else if (s != null && s.hammerOut() && filmT < 0) { trail.addFirst(s.hammer); while (trail.size() > 10) trail.removeLast(); }
            else if (!trail.isEmpty()) trail.removeLast();
            float t = s == null ? 0 : ThorClient.clock(s, 0);
            int action = s == null ? IDLE : s.action;
            // Wakanda strike: up in the air, lightning lashes out of him in every direction.
            if (action == WAKANDA && t >= WK_RISE && t < LANDED) {
                Vec3 c = p.position().add(0, 1.2, 0);
                if (r.nextFloat() < .55f) {
                    double a = r.nextDouble() * Math.PI * 2, up = (r.nextDouble() - .35) * 1.2, l = 4 + r.nextDouble() * 6;
                    Vec3 d = new Vec3(Math.cos(a), up, Math.sin(a)).normalize();
                    bolt(c.add(d.scale(.5)), c.add(d.scale(l)), .07 + r.nextDouble() * .05, 3 + r.nextInt(3), 2, .3, .8);
                }
                if (r.nextFloat() < .12f) bolt(c.add((r.nextDouble() - .5) * 10, 22, (r.nextDouble() - .5) * 10), c.add((r.nextDouble() - .5) * 3, 1, (r.nextDouble() - .5) * 3), .12, 5, 3, .25, .7);
            }
            // Full charge: the whirling hammer spits small sparks.
            if (action == CHARGE && t >= CHARGE_FULL) {
                Vec3 h = chargedHammer(p);
                if (r.nextFloat() < .6f) {
                    Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                    bolt(h.add(d.scale(.3)), h.add(d.scale(.8 + r.nextDouble() * .6)), .025, 2 + r.nextInt(2), 1, .4, .3);
                }
                sparks(h, 1, .25);
            }
            // The thunder beam: sparks where it lands.
            if (action == BEAM && t >= BM_AIM && t < BM_END) {
                Vec3[] beam = beam(p, 0);
                sparks(beam[1], 3, .5);
            }
            // The God of Thunder film.
            float ult = ThorClient.ultimateTime(p, 0);
            Deque<Vec3> fly = FLIGHT_TRAILS.computeIfAbsent(p.getId(), id -> new ArrayDeque<>());
            Ult view = ult >= 0 ? ult(p) : null;
            boolean flying = view != null && ult >= ULT_RISE && ult < ULT_CATCH || action == DASH && t <= dashTicks(s.dashCharge) + 1;
            Vec3 body = view != null ? view.world(view.path.thor(ult)) : p.position();
            if (flying) { fly.addFirst(body.add(0, 1.1, 0)); while (fly.size() > 16) fly.removeLast(); }
            else if (!fly.isEmpty()) fly.removeLast();
            if (flying && r.nextFloat() < (view != null ? .5f : .25f)) {
                Vec3 from = body.add(0, .8, 0);
                bolt(from, from.add((r.nextDouble() - .5) * 2, -1.5 - r.nextDouble() * 2.5, (r.nextDouble() - .5) * 2), view != null ? .06 : .035, 4, 2, .4, .5);
            }
            Float before = LAST_ULT.get(p.getId());
            if (view != null) { ultimateTick(view, before == null ? -1 : before, ult, r); LAST_ULT.put(p.getId(), ult); }
            else { LAST_ULT.remove(p.getId()); VIEWS.remove(p.getId()); }
        }
        Map<Integer, Integer> was = new HashMap<>(CARRIED);
        CARRIED.clear();
        for (Player p : mc.level.players()) {
            var s = ThorClient.isThor(p) ? ThorClient.get(p) : null;
            if (s != null && s.action == DASH && s.carried >= 0) CARRIED.put(s.carried, p.getId());
        }
        // Let go of the hammer: thrown on, tumbling head over heels before landing on their feet.
        for (var gone : was.entrySet()) {
            if (CARRIED.containsKey(gone.getKey())) continue;
            var thor = mc.level.getEntity(gone.getValue());
            FLUNG.put(gone.getKey(), new Fling(now, thor == null ? 0 : thor.getYRot() + 180));
        }
        FLUNG.entrySet().removeIf(f -> now - f.getValue().start() > FLING_TICKS || mc.level.getEntity(f.getKey()) == null);
        SHOCKS.removeIf(k -> now - k.start() > SHOCK_LIFE);
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
    /** One Thor's running film: his stage (anchor, facing) and the path both bodies follow on it. */
    static final class Ult {
        final Player thor; final AerialPath path; final Vec3 anchor, forward, right; final float yaw; final int target;
        Ult(Player thor, AerialPath path, Vec3 anchor, Vec3 forward, float yaw, int target) {
            this.thor = thor; this.path = path; this.anchor = anchor; this.forward = forward; this.yaw = yaw; this.target = target;
            right = forward.cross(new Vec3(0, 1, 0)).normalize();
        }
        Vec3 world(Vec3 stage) { return anchor.add(right.scale(stage.x)).add(0, stage.y, 0).add(forward.scale(stage.z)); }
    }
    private static final Map<Integer, Ult> VIEWS = new HashMap<>();
    private static final Map<Integer, Float> LAST_ULT = new HashMap<>();
    /** The film this Thor is in, or null; the target's distance is read once and kept for the whole film. */
    static Ult ult(Player p) {
        var film = FilmSessionClient.get(p.getId());
        var level = Minecraft.getInstance().level;
        if (film == null || level == null || !GodOfThunderSession.ID.equals(film.film)) return null;
        Ult known = VIEWS.get(p.getId());
        if (known != null && known.anchor.distanceToSqr(film.anchor) < 1e-4) return known;
        var target = level.getEntity(film.target);
        double d = 5;
        if (target != null) {
            Vec3 to = target.position().subtract(film.anchor);
            d = Math.max(2.5, Math.min(20, Math.sqrt(to.x * to.x + to.z * to.z)));
        }
        Ult view = new Ult(p, new AerialPath(d), film.anchor, FilmSessionClient.forward(film), film.yaw, film.target);
        VIEWS.put(p.getId(), view);
        return view;
    }
    /** World height of the underside of the film's cloud ceiling. */
    private static double cloudY(Ult v) { return v.world(new Vec3(0, ULT_CLOUDS - .5, 0)).y; }
    private static boolean crossed(float before, float now, float beat) { return before < beat && now >= beat; }
    /** The film's lightning, fired as the clock passes each beat; the storm thickens toward the whiteout. */
    private static void ultimateTick(Ult v, float before, float t, Random r) {
        AerialPath path = v.path;
        Vec3 target = v.world(path.target(t)), chest = target.add(0, 1.1, 0);
        Vec3 thor = v.world(path.thor(t));
        if (crossed(before, t, ULT_HIT1)) {
            shock(chest, v.right, .9);
            burst(chest, v.right, 4, 1.4, .05); flash(chest, 1.4, 5, .8f); sparks(chest, 14, .7); ring(chest, 1.2, 8);
        }
        if (crossed(before, t, ULT_HIT2)) {
            shock(chest, v.forward, 1.0);
            burst(chest, v.forward, 6, 1.8, .06); flash(chest, 2.0, 6, .9f); sparks(chest, 26, 1.0); ring(chest, 1.8, 10);
        }
        if (crossed(before, t, ULT_UPPER)) {
            Vec3 feet = v.world(path.target(ULT_UPPER));
            shock(feet.add(0, 1.4, 0), new Vec3(0, 1, 0), 1.0);
            bolt(feet, feet.add(0, 6, 0), .1, 6, 3, .25, .6);
            flash(feet.add(0, 1.2, 0), 2.4, 7, 1f); ring(feet.add(0, .05, 0), 3.2, 14); sparks(feet.add(0, 1, 0), 24, 1.1);
            var level = Minecraft.getInstance().level;
            if (level != null) for (int i = 0; i < 20; i++) {
                double a = i * Math.PI * 2 / 20;
                level.addParticle(ParticleTypes.CLOUD, feet.x + Math.cos(a) * .5, feet.y + .1, feet.z + Math.sin(a) * .5, Math.cos(a) * .3, .03, Math.sin(a) * .3);
            }
        }
        // Climbing into the storm: great bolts tear past, the sky starts to crawl with light.
        for (int beat : new int[]{ULT_UPPER + 50, ULT_UPPER + 60, ULT_UPPER + 70})
            if (crossed(before, t, beat)) {
                double side = (beat == ULT_UPPER + 60 ? 1 : -1) * (3 + r.nextDouble() * 2);
                Vec3 at = chest.add(v.right.scale(side));
                bolt(at.add(0, 26, 0), at.add(v.forward.scale(r.nextDouble() * 4 - 2)).add(0, -18, 0), .3, 7, 3, .18, .8);
                flash(at, 5, 6, .6f);
            }
        // Mjolnir thrown up into the storm, spitting lightning all the way until the clouds swallow it.
        Vec3 hammer = path.hammer(t);
        if (hammer != null) {
            Vec3 h = v.world(hammer);
            for (int i = 0; i < 2; i++) {
                Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * .6, r.nextGaussian()).normalize();
                bolt(h, h.add(d.scale(1.5 + r.nextDouble() * 2.5)), .05, 3, 2, .35, .6);
            }
            sparks(h, 2, .4);
        }
        if (crossed(before, t, ULT_TOSS)) { Vec3 h = v.world(path.hammer(ULT_TOSS)); flash(h, 1.6, 5, .9f); burst(h, new Vec3(0, 1, 0), 4, 1.6, .05); }
        // Where it vanished, the clouds keep flashing: the hammer is up there, charging the storm.
        Vec3 cloud = v.world(path.cloudPoint());
        if (crossed(before, t, ULT_VANISH)) { flash(cloud, 9, 10, .8f); bolt(cloud, cloud.add(0, -12, 0), .2, 6, 3, .2, .8); }
        if (t >= ULT_VANISH && t < ULT_RECALL - AerialPath.RECALL_FALL && r.nextFloat() < .45f) {
            Vec3 a0 = cloud.add((r.nextDouble() - .5) * 8, (r.nextDouble() - .5) * 2, (r.nextDouble() - .5) * 8);
            bolt(a0, a0.add((r.nextDouble() - .5) * 12, -1 - r.nextDouble() * 4, (r.nextDouble() - .5) * 12), .1, 4, 2, .3, .8);
            if (r.nextFloat() < .35f) flash(a0, 7 + r.nextDouble() * 5, 5, .45f);
        }
        // Called back: it falls out of the clouds into his raised hand.
        if (crossed(before, t, ULT_RECALL - AerialPath.RECALL_FALL)) { flash(cloud, 8, 8, .8f); bolt(cloud.add(0, 4, 0), cloud.add(0, -6, 0), .22, 5, 3, .2, .7); }
        if (crossed(before, t, ULT_RECALL)) {
            Vec3 hand = v.world(path.thor(ULT_RECALL).add(.36, 2.25, 0));
            flash(hand, 2.2, 6, 1f); burst(hand, Vec3.ZERO, 5, 1.4, .05); sparks(hand, 16, .7);
        }
        if (t > ULT_UPPER + 30 && t < ULT_FADE && r.nextFloat() < (t < ULT_STORM ? .35f : .7f)) {
            Vec3 sky = v.world(new Vec3(0, ULT_CLOUDS - 2, path.distance()));
            double a = r.nextDouble() * Math.PI * 2, d = 4 + r.nextDouble() * 18;
            Vec3 a0 = sky.add(Math.cos(a) * d, r.nextDouble() * 5, Math.sin(a) * d);
            bolt(a0, a0.add((r.nextDouble() - .5) * 16, -2 - r.nextDouble() * 5, (r.nextDouble() - .5) * 16), .12, 5, 2, .3, .8);
        }
        if (crossed(before, t, ULT_CATCH)) { flash(thor.add(0, 1.3, 0), 1.8, 6, .9f); sparks(thor.add(0, 1.3, 0), 18, .8); }
        // The cry: lightning out of his eyes, thick and branching, up into the clouds.
        if (t >= ULT_EYEBOLT && t < ULT_BLAST && (crossed(before, t, ULT_EYEBOLT) || (int) t != (int) before && (int) t % 3 == 0)) {
            Vec3 eyes = thor.add(0, 1.65, 0).add(v.forward.scale(.25));
            for (int side = -1; side <= 1; side += 2) {
                Vec3 eye = eyes.add(v.right.scale(side * .12));
                // Up into the cloud ceiling itself.
                Vec3 up = eye.add(v.right.scale(side * (2 + r.nextDouble() * 10))).add(v.forward.scale(r.nextDouble() * 10 - 3));
                up = new Vec3(up.x, cloudY(v) + r.nextDouble() * 1.5, up.z);
                bolt(eye, up, t < ULT_STORM ? .2 : .14, 6, 3, .22, .9);
            }
            if (crossed(before, t, ULT_EYEBOLT)) { flash(eyes, 3, 8, 1f); flash(new Vec3(eyes.x, cloudY(v), eyes.z), 20, 10, .6f); }
        }
        // Then the columns: one great strike after another all round them, ever more.
        if (t >= ULT_STORM && t < ULT_BLAST) {
            float density = (t - ULT_STORM) / (ULT_BLAST - ULT_STORM);
            int strikes = r.nextFloat() < .4f + .6f * density ? 1 + (density > .6f ? 1 : 0) : 0;
            for (int i = 0; i < strikes; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = 2.5 + r.nextDouble() * (14 - 8 * density);
                Vec3 col = thor.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                // Out of the clouds, down past them, all the way to the ground.
                Vec3 top = new Vec3(col.x, cloudY(v) - r.nextDouble(), col.z), bottom = new Vec3(col.x + (r.nextDouble() - .5) * 3, v.anchor.y, col.z + (r.nextDouble() - .5) * 3);
                bolt(top, bottom, .28 + .2 * density, 6, 3, .12, .7);
                flash(col.add(0, 1, 0), 3 + 4 * density, 5, .6f);
                flash(top, 8 + 6 * density, 5, .5f);
            }
        }
        if (crossed(before, t, ULT_BLAST)) {
            // The great discharge: the whole ceiling lets go at once, every bolt aimed at the two of them.
            flash(thor.add(0, 1, 0), 40, 18, 1f);
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14 + r.nextDouble() * .4, d = 3 + r.nextDouble() * 16;
                Vec3 top = new Vec3(thor.x + Math.cos(a) * d, cloudY(v) - r.nextDouble(), thor.z + Math.sin(a) * d);
                bolt(top, thor.add((r.nextDouble() - .5) * 2, .5 + r.nextDouble(), (r.nextDouble() - .5) * 2), .35, 10, 4, .14, .8);
                flash(top, 12, 10, .7f);
            }
        }
        if (crossed(before, t, ULT_LET_GO)) { flash(target.add(0, 1, 0), 1.6, 5, .7f); burst(target.add(0, 1, 0), new Vec3(0, -1, 0), 3, 1.5, .05); }
        if (crossed(before, t, ULT_LANDED)) takeoff(thor, .7f);
        // Afterwards: small arcs still running over the ground round him.
        if (t > ULT_LANDED && r.nextFloat() < .3f) {
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 at = thor.add(Math.cos(a) * (.5 + r.nextDouble()), .05, Math.sin(a) * (.5 + r.nextDouble()));
            bolt(at, at.add((r.nextDouble() - .5) * 1.4, .1 + r.nextDouble() * .5, (r.nextDouble() - .5) * 1.4), .025, 3, 1, .4, .3);
        }
    }

    // ------------------------------------------------------------------ the thunder beam (F)
    /** Where the hammer is while the arm holds it out at full stretch, the charged spin out to his right. */
    static Vec3 chargedHammer(Player p) {
        Vec3 f = Vec3.directionFromRotation(0, p.getYRot()), right = f.cross(new Vec3(0, 1, 0)).normalize();
        return p.position().add(0, 1.35, 0).add(right.scale(.95)).add(f.scale(.55));
    }
    /** Hammer head and where the beam stops (a wall, a body or full range), for this frame. */
    static Vec3[] beam(Player p, float partial) {
        Vec3 look = p.getViewVector(partial);
        Vec3 f = Vec3.directionFromRotation(0, p.getViewYRot(partial)), right = f.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 shoulder = p.getPosition(partial).add(0, 1.38, 0).add(right.scale(.36));
        Vec3 head = shoulder.add(look.scale(1.25));
        Vec3 eye = p.getEyePosition(partial), far = eye.add(look.scale(BM_RANGE));
        var level = Minecraft.getInstance().level;
        var hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        double best = eye.distanceToSqr(end);
        for (var e : level.getEntities(p, new net.minecraft.world.phys.AABB(eye, end).inflate(BM_RADIUS))) {
            if (!(e instanceof net.minecraft.world.entity.LivingEntity)) continue;
            var clip = e.getBoundingBox().inflate(.3).clip(eye, end);
            if (clip.isPresent() && clip.get().distanceToSqr(eye) < best) { best = clip.get().distanceToSqr(eye); end = clip.get(); }
        }
        return new Vec3[]{head, end};
    }
    private static void drawBeam(FilmContext c, Matrix4f m, Player p, float t, float partial) {
        float alpha = FilmFx.ease((t - BM_AIM + 1) / 2f) * (1 - FilmFx.ease((t - BM_END) / 3f));
        if (alpha <= .01f) return;
        Vec3[] ends = beam(p, partial);
        Vec3 head = ends[0], end = ends[1];
        int frame = (int) (c.time() * .75f);
        // A straight bright core with the lightning twisting round it, re-formed every tick or so.
        ThorBolts.ribbon(c.buffers().getBuffer(FilmFx.ADD), m, head, end, c.camera(), .55, ThorBolts.GLOW, .25f * alpha);
        ThorBolts.ribbon(c.buffers().getBuffer(FilmFx.ADD), m, head, end, c.camera(), .14, ThorBolts.BODY, .55f * alpha);
        for (int i = 0; i < 3; i++)
            ThorBolts.draw(c.buffers().getBuffer(FilmFx.ADD), m, ThorBolts.bolt(head, end, frame * 97L + i * 13L + p.getId(), .14 + .05 * i, .45, 1), c.camera(), i == 0 ? .14 : .08, alpha);
        FilmFx.glow(c, head, .9, ThorBolts.BODY, .8f * alpha);
        FilmFx.glow(c, head, .35, ThorBolts.CORE, alpha);
        FilmFx.glow(c, end, 1.5, ThorBolts.BODY, .7f * alpha);
        FilmFx.glow(c, end, .5, ThorBolts.CORE, .9f * alpha);
    }

    // ------------------------------------------------------------------ drawing: light
    @SubscribeEvent public static void renderLight(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean storm = false;
        for (Player p : mc.level.players()) {
            if (!ThorClient.isThor(p)) continue;
            var s = ThorClient.get(p);
            if (ThorClient.ultimateTime(p, 0) >= 0 || s != null && s.action == BEAM) storm = true;
        }
        if (BOLTS.isEmpty() && SHOCKS.isEmpty() && FLASHES.isEmpty() && RINGS.isEmpty() && CRACKS.isEmpty() && HAMMER_TRAILS.isEmpty() && FLIGHT_TRAILS.isEmpty() && !storm) return;
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
            for (Shock k : SHOCKS) drawShock(c, m, k, time);
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
                Ult view = t >= 0 ? ult(pl) : null;
                if (view != null) storm(c, view, t);
                var s = ThorClient.get(pl);
                if (s != null && s.action == BEAM) drawBeam(c, m, pl, ThorClient.clock(s, partial), partial);
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
    /** The shock ring: grows fast from nothing to its size and fades as it goes; thin and faint. */
    private static void drawShock(FilmContext c, Matrix4f m, Shock k, float time) {
        float age = (time - k.start()) / SHOCK_LIFE;
        if (age < 0 || age > 1) return;
        double radius = k.size() * (.15 + .85 * (1 - (1 - age) * (1 - age))), width = .06 + .05 * age;
        float alpha = .32f * (1 - age);
        Vec3 u = ThorBolts.perpendicular(k.dir()), w = k.dir().cross(u);
        var v = c.buffers().getBuffer(FilmFx.ADD);
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0))), d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
            ThorBolts.put(v, m, k.at().add(d0.scale(radius - width)), 0xdfeaff, 0); ThorBolts.put(v, m, k.at().add(d1.scale(radius - width)), 0xdfeaff, 0);
            ThorBolts.put(v, m, k.at().add(d1.scale(radius)), 0xdfeaff, alpha); ThorBolts.put(v, m, k.at().add(d0.scale(radius)), 0xdfeaff, alpha);
            ThorBolts.put(v, m, k.at().add(d0.scale(radius)), 0xdfeaff, alpha); ThorBolts.put(v, m, k.at().add(d1.scale(radius)), 0xdfeaff, alpha);
            ThorBolts.put(v, m, k.at().add(d1.scale(radius + width)), 0xdfeaff, 0); ThorBolts.put(v, m, k.at().add(d0.scale(radius + width)), 0xdfeaff, 0);
        }
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
    /**
     * One clump of storm cloud, dense enough to read against any sky: a solid core, a body and soft
     * edges, slightly lighter on top where the sky lights it.
     */
    /** A solid block of cloud, its faces shaded by which way they face so it reads as a 3D mass. */
    private static void block(FilmContext c, Matrix4f m, Vec3 lo, Vec3 hi, int rgb, float bright) {
        VertexConsumer v = c.buffers().getBuffer(FilmFx.SOLID);
        float x0 = (float) lo.x, y0 = (float) lo.y, z0 = (float) lo.z, x1 = (float) hi.x, y1 = (float) hi.y, z1 = (float) hi.z;
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}};
        // Sides a step darker than the top, the underside darkest but still lit by the storm's glow.
        float[] shade = {.8f, .66f, .74f, .74f, 1f, .6f};
        float r = (rgb >> 16 & 255) / 255f * bright, g = (rgb >> 8 & 255) / 255f * bright, b = (rgb & 255) / 255f * bright;
        for (int f = 0; f < 6; f++) for (int k = 0; k < 4; k++)
            v.vertex(m, faces[f][k * 3], faces[f][k * 3 + 1], faces[f][k * 3 + 2])
                    .color(Math.min(1, r * shade[f]), Math.min(1, g * shade[f]), Math.min(1, b * shade[f]), 1).endVertex();
    }
    /** One cloud: a big flat slab with a couple of lumps on it, all solid. */
    private static void cloudMass(FilmContext c, Matrix4f m, Vec3 centre, double half, double height, double seed, float grown, float bright) {
        if (grown <= .02f) return;
        int[] palette = {0x3b404c, 0x464c5a, 0x525a6a};
        int colour = palette[(int) (FilmFx.hash(seed * 1.7) * 2.99)];
        double h = height * grown;
        block(c, m, centre.add(-half, -h / 2, -half * .9), centre.add(half, h / 2, half * .9), colour, bright);
        for (int i = 0; i < 2; i++) {
            double s = seed * 3.1 + i * 7.7;
            double lump = half * (.35 + .25 * FilmFx.hash(s));
            Vec3 at = centre.add((FilmFx.hash(s + 1) - .5) * half, (i == 0 ? 1 : -1) * h * .45, (FilmFx.hash(s + 2) - .5) * half);
            block(c, m, at.add(-lump, -lump * .45 * grown, -lump), at.add(lump, lump * .45 * grown, lump), colour, bright * (i == 0 ? 1.08f : .92f));
        }
    }

    /**
     * The storm: a low ceiling of solid dark cloud closing over the whole place from the edges in,
     * turning slowly, and masses of it drifting round the two of them at the height of the fight.
     * Lightning inside it lights it up.
     */
    private static void storm(FilmContext c, Ult v, float t) {
        float build = FilmFx.ease((t - (ULT_UPPER + 8)) / 45f);
        if (build <= .01f) return;
        Matrix4f m = c.pose().last().pose();
        Vec3 eye = v.world(new Vec3(0, ULT_CLOUDS + 2, v.path.distance()));
        float fade = 1 - .3f * FilmFx.ease((t - ULT_LANDED) / 20f);
        // A flash inside the clouds every so often brightens the whole ceiling for a moment.
        // During the storm of strikes the flashes come thick and fast.
        double flashRate = t >= ULT_STORM && t < ULT_FADE ? 1.6 : .6, flashOdds = t >= ULT_STORM && t < ULT_FADE ? .45 : .7;
        float bright = (FilmFx.hash(Math.floor(t * flashRate) * 1.37) > flashOdds ? 1.5f : 1f) * fade;
        double spin = t * .004;
        double cos = Math.cos(spin), sin = Math.sin(spin);
        int cells = 8;
        double step = 6.2;
        for (int i = -cells; i <= cells; i++) for (int j = -cells; j <= cells; j++) {
            double seed = i * 31.7 + j * 17.3;
            if (FilmFx.hash(seed) < .14) continue;   // a few gaps
            double x = i * step + (FilmFx.hash(seed + 5) - .5) * 2.5, z = j * step + (FilmFx.hash(seed + 9) - .5) * 2.5;
            double dist = Math.sqrt(x * x + z * z);
            if (dist > cells * step) continue;
            // Closes in from the edge toward the middle.
            float grown = FilmFx.ease((float) ((t - (ULT_UPPER + 8) - (1 - dist / (cells * step)) * 28) / 22));
            Vec3 at = eye.add(x * cos - z * sin, (FilmFx.hash(seed + 3) - .5) * 2.4, x * sin + z * cos);
            cloudMass(c, m, at, step * .62 + FilmFx.hash(seed + 4) * 1.2, 2.2 + FilmFx.hash(seed + 6) * 2.6, seed, grown, bright);
        }
        // Masses at the height of the fight, sliding round.
        float banks = FilmFx.ease((t - (ULT_UPPER + 20)) / 30f) * (1 - FilmFx.ease((t - ULT_LET_GO) / 30f));
        if (banks > .02f) {
            Vec3 mid = v.world(new Vec3(0, ULT_HEIGHT - 2, v.path.distance()));
            for (int ring = 0; ring < 3; ring++) {
                int n = 9 + ring * 3;
                double radius = 13 + ring * 8;
                for (int i = 0; i < n; i++) {
                    double seed = i * 3.7 + ring * 9.1;
                    double a = i * Math.PI * 2 / n + t * .006 * (ring + 1) + ring;
                    Vec3 at = mid.add(Math.cos(a) * radius, (ring - 1) * 5 + (FilmFx.hash(seed) - .5) * 4, Math.sin(a) * radius);
                    cloudMass(c, m, at, 2.5 + FilmFx.hash(seed + 1) * 2, 1.6 + FilmFx.hash(seed + 2) * 1.8, seed, banks, bright * .95f);
                }
            }
        }
        FilmFx.glow(c, eye.add(0, -3.5, 0), 22, ThorBolts.HAZE, .12f * build * (bright > 1.2f ? 1 : .3f));
        if (t >= ULT_VANISH && t < ULT_RECALL) FilmFx.glow(c, v.world(v.path.cloudPoint()).add(0, -2, 0), 9, ThorBolts.BODY, bright > 1.2f ? .45f : .15f);
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
            Ult view = ult >= 0 ? ult(p) : null;
            if (view != null) {
                performers(pose, buffers, cam, view, ult, partial);
                Vec3 h = view.path.hammer(ult);
                if (h != null) filmHammer(pose, buffers, cam, view.world(h), ult);
                any = true;
            }
        }
        for (var entry : CARRIED.entrySet()) { if (carried(pose, buffers, cam, entry.getKey(), entry.getValue(), partial)) any = true; }
        for (var entry : FLUNG.entrySet()) { if (flung(pose, buffers, cam, entry.getKey(), entry.getValue(), partial)) any = true; }
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
    /** The hammer in the film while it flies up into the storm and falls back out of it. */
    private static void filmHammer(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, Vec3 at, float t) {
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        pose.mulPose(Axis.XP.rotation(t * .9f));
        pose.mulPose(Axis.ZP.rotation(t * .35f));
        pose.translate(0, -5.5 / 16f, 0);
        Mjolnir.draw(pose, buffers, Mjolnir.FULL_BRIGHT, 1, t);
        pose.popPose();
    }

    /** Bodies stuck on a launched hammer's head (body id -> Thor id). */
    private static final Map<Integer, Integer> CARRIED = new HashMap<>();
    /**
     * Someone the Shift launch hit: drawn as a puppet folded round the hammer head, pushed ahead
     * of him, arms and legs dragged back toward him by the speed.
     */
    private static boolean carried(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, int id, int thorId, float partial) {
        var mc = Minecraft.getInstance();
        var body = mc.level.getEntity(id);
        var thor = mc.level.getEntity(thorId);
        if (!(body instanceof net.minecraft.world.entity.LivingEntity) || thor == null) return false;
        var cast = CASTS.get(id);
        if (cast == null || cast.entity() != body) { cast = com.FIRNI.superheromod.client.render.film.FilmCast.of(body); CASTS.put(id, cast); }
        if (cast == null) return false;
        float time = mc.level.getGameTime() + partial;
        float flail = (float) Math.sin(time * 1.3) * 8, flail2 = (float) Math.sin(time * 1.7 + 1) * 8;
        var p = com.FIRNI.superheromod.core.cinematic.ActorPose.of()
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.CHEST, 55, 0, 0)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.HEAD, 25, 0, 0)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.HIPS, 10, 0, 0)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.RIGHT_UPPER_ARM, -75 + flail, 0, 35)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.RIGHT_LOWER_ARM, -25, 0, 0)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.LEFT_UPPER_ARM, -70 + flail2, 0, -35)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.LEFT_LOWER_ARM, -30, 0, 0)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.RIGHT_UPPER_LEG, -55 + flail2 * .5f, 0, 8)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.RIGHT_LOWER_LEG, 45, 0, 0)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.LEFT_UPPER_LEG, -45 + flail * .5f, 0, -8)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.LEFT_LOWER_LEG, 55, 0, 0)
                .body(0, 22);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        var rotation = mc.gameRenderer.getMainCamera().rotation();
        var r = new org.joml.Vector3f(1, 0, 0).rotate(rotation); var u = new org.joml.Vector3f(0, 1, 0).rotate(rotation);
        FilmContext c = new FilmContext(pose, buffers, cam, new Vec3(r.x, r.y, r.z), new Vec3(u.x, u.y, u.z), time, 0, partial);
        drawingCast = true;
        try {
            Vec3 look = thor.getViewVector(partial);
            Vec3 at = thor.getPosition(partial).add(look.scale(CARRY_AHEAD)).add(0, Math.max(-.6, look.y * .4), 0);
            cast.draw(c, at, thor.getViewYRot(partial) + 180, p, 1, 0xffffff);
        } finally {
            drawingCast = false;
            pose.popPose();
        }
        return true;
    }

    private record Fling(long start, float yaw) {}
    private static final int FLING_TICKS = 18;
    private static final Map<Integer, Fling> FLUNG = new HashMap<>();
    /** Thrown off the hammer: one full backward flip through the air, limbs flung out, ending upright. */
    private static boolean flung(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, int id, Fling f, float partial) {
        var mc = Minecraft.getInstance();
        var body = mc.level.getEntity(id);
        if (!(body instanceof net.minecraft.world.entity.LivingEntity)) return false;
        var cast = CASTS.get(id);
        if (cast == null || cast.entity() != body) { cast = com.FIRNI.superheromod.client.render.film.FilmCast.of(body); CASTS.put(id, cast); }
        if (cast == null) return false;
        float age = mc.level.getGameTime() - f.start() + partial, k = Math.min(1, age / FLING_TICKS);
        float spin = 1 - (1 - k) * (1 - k);
        float open = (float) Math.sin(Math.PI * k);
        float flail = (float) Math.sin(age * 1.4) * 14 * open;
        var p = com.FIRNI.superheromod.core.cinematic.ActorPose.of()
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.CHEST, -30 * open, 0, 0)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.HEAD, -25 * open, 0, 0)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.RIGHT_UPPER_ARM, -140 * open + flail, 0, 40 * open)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.LEFT_UPPER_ARM, -130 * open - flail, 0, -40 * open)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.RIGHT_UPPER_LEG, -40 * open + flail, 0, 10 * open)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.RIGHT_LOWER_LEG, 50 * open, 0, 0)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.LEFT_UPPER_LEG, -20 * open - flail, 0, -10 * open)
                .j(com.FIRNI.superheromod.core.cinematic.ActorPose.LEFT_LOWER_LEG, 40 * open, 0, 0)
                .body(0, 360 * spin);
        // The flip turns about the feet; lift the body by its own height mid-flip so it turns about its middle.
        Vec3 at = body.getPosition(partial).add(0, .9 * Math.sin(Math.PI * spin), 0);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        var rotation = mc.gameRenderer.getMainCamera().rotation();
        var r = new org.joml.Vector3f(1, 0, 0).rotate(rotation); var u = new org.joml.Vector3f(0, 1, 0).rotate(rotation);
        FilmContext c = new FilmContext(pose, buffers, cam, new Vec3(r.x, r.y, r.z), new Vec3(u.x, u.y, u.z), age, 0, partial);
        drawingCast = true;
        try {
            cast.draw(c, at, f.yaw(), p, 1, 0xffffff);
        } finally {
            drawingCast = false;
            pose.popPose();
        }
        return true;
    }

    private static final Map<Integer, com.FIRNI.superheromod.client.render.film.FilmCast> CASTS = new HashMap<>();
    private static boolean drawingCast;
    /**
     * The film's two bodies in the world: Thor where the path puts him (his real body stays held at
     * the start), and the target as a jointed puppet that can bend, block, fly and fall.
     */
    private static void performers(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, Ult v, float t, float partial) {
        var mc = Minecraft.getInstance();
        Player p = v.thor;
        Vec3 at = v.world(v.path.thor(t));
        var dispatcher = mc.getEntityRenderDispatcher();
        float yaw = v.yaw + v.path.thorYaw(t);
        float body = p.yBodyRot, bodyO = p.yBodyRotO, head = p.yHeadRot, headO = p.yHeadRotO;
        p.yBodyRot = p.yBodyRotO = p.yHeadRot = p.yHeadRotO = yaw;
        drawingProxy = true;
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(p, at.x - cam.x, at.y - cam.y, at.z - cam.z, yaw, partial, pose, buffers, Mjolnir.FULL_BRIGHT);
        } finally {
            drawingProxy = false;
            dispatcher.setRenderShadow(mc.options.entityShadows().get());
            p.yBodyRot = body; p.yBodyRotO = bodyO; p.yHeadRot = head; p.yHeadRotO = headO;
        }
        var target = mc.level.getEntity(v.target);
        if (target == null) return;
        var cast = CASTS.get(v.target);
        if (cast == null || cast.entity() != target) { cast = com.FIRNI.superheromod.client.render.film.FilmCast.of(target); CASTS.put(v.target, cast); }
        if (CASTS.size() > 8) CASTS.clear();
        if (cast == null) return;
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        var rotation = mc.gameRenderer.getMainCamera().rotation();
        var r = new org.joml.Vector3f(1, 0, 0).rotate(rotation); var u = new org.joml.Vector3f(0, 1, 0).rotate(rotation);
        FilmContext c = new FilmContext(pose, buffers, cam, new Vec3(r.x, r.y, r.z), new Vec3(u.x, u.y, u.z), t, t, partial);
        drawingCast = true;
        try {
            cast.draw(c, v.world(v.path.target(t)), v.yaw + 180, AerialPath.targetPose(t), 1, 0xffffff);
        } finally {
            drawingCast = false;
            pose.popPose();
        }
    }
    /** During the film the real bodies stay held at the start; only the performers are drawn. */
    @SubscribeEvent public static void hideHeld(RenderPlayerEvent.Pre e) {
        if (drawingProxy || drawingCast || !ThorClient.isThor(e.getEntity())) return;
        if (ThorClient.ultimateTime(e.getEntity(), e.getPartialTick()) >= 0) e.setCanceled(true);
    }
    @SubscribeEvent public static void hideTarget(RenderLivingEvent.Pre<?, ?> e) {
        if (drawingCast || drawingProxy) return;
        int id = e.getEntity().getId();
        if (CARRIED.containsKey(id) || FLUNG.containsKey(id)) { e.setCanceled(true); return; }
        for (Ult v : VIEWS.values()) if (v.target == id) { e.setCanceled(true); return; }
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
            case CHARGE -> {
                // Whirling out to the right, a blur in the corner of the view.
                float in = ThorMotion.k(t, 0, 4);
                p.translate(.25 * in, .25 * in, -.15 * in);
                p.mulPose(Axis.ZP.rotation(ThorMotion.spin(t, 2.9f, 8)));
                power = 0;
            }
            case DASH -> {
                float out = ThorMotion.snap(t, 0, 2);
                p.translate(-.35 * out, .3 * out, -.3 * out);
                p.mulPose(Axis.XP.rotationDegrees(-95 * out));
            }
            case BEAM -> {
                float up = ThorMotion.k(t, 0, 8), aim = ThorMotion.snap(t, BM_AIM - 5, BM_AIM), done = ThorMotion.k(t, BM_END, BM_TOTAL);
                float raise = up * (1 - aim);
                p.translate(-.1 * raise, .9 * raise, 0);
                p.translate(-.3 * aim * (1 - done), .25 * aim * (1 - done), -.2 * aim * (1 - done));
                p.mulPose(Axis.XP.rotationDegrees(-95 * aim * (1 - done)));
                if (t >= BM_AIM && t < BM_END) p.translate(Math.sin(time * 3.1) * .01, Math.sin(time * 7.3) * .01, 0);
                power = Math.max(power, up);
            }
            default -> {}
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
