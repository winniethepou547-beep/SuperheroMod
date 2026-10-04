package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.heroes.batman.BatmanConfig;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Batman's effects in the world, for everyone: Batarangs flying (spinning flat) and stuck in walls, gadget pellets,
 * the smoke clouds (thick and wide; thin for him, he sees through his own smoke), the flash, the thermal pulse, the
 * mines (blinking, the blast), the grapnel line (slack and waving while the hook flies, snapping taut when it bites),
 * punch impacts, the strike's blows, the roll's dust.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanFx {
    private static final class Rang {
        final int id, count; Vec3 pos, prev, vel; final float spawn; boolean stuck; float stuckAt;
        /** Where it was over the last ticks (newest last): the trail. */
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        Rang(int id, int count, Vec3 pos, Vec3 vel, float spawn) { this.id = id; this.count = count; this.pos = pos; this.prev = pos; this.vel = vel; this.spawn = spawn; }
    }
    private static final class Pellet {
        final int id, gadget; Vec3 pos, prev, vel; final float spawn;
        Pellet(int id, int gadget, Vec3 pos, Vec3 vel, float spawn) { this.id = id; this.gadget = gadget; this.pos = pos; this.prev = pos; this.vel = vel; this.spawn = spawn; }
    }
    /** A smoke cloud: its puffs are placed from its id, so every client sees the same cloud. */
    record Cloud(int owner, int id, Vec3 at, float radius, float start, float life) {
        boolean inside(Vec3 p, float now) {
            if (now - start > life) return false;
            double dx = p.x - at.x, dz = p.z - at.z, dy = (p.y - at.y) / .75;
            return dx * dx + dz * dz + Math.max(0, dy) * Math.max(0, dy) < radius * radius && dy > -1.5;
        }
    }
    private static final class Mine { final int id; final Vec3 at; final float start; boolean armed; Mine(int id, Vec3 at, float start) { this.id = id; this.at = at; this.start = start; } }
    /** The grapnel line of one Batman. */
    private static final class Line { final int batman; final float start; float taut = -1; final Vec3 from, to; Line(int batman, float start, Vec3 from, Vec3 to) { this.batman = batman; this.start = start; this.from = from; this.to = to; } }
    private static final class Mote {
        Vec3 pos, vel; final float size, grow, life, start, alpha; final int rgb, kind;
        Mote(Vec3 pos, Vec3 vel, float size, float grow, int rgb, float life, float start, int kind, float alpha) {
            this.pos = pos; this.vel = vel; this.size = size; this.grow = grow; this.rgb = rgb; this.life = life; this.start = start; this.kind = kind; this.alpha = alpha;
        }
    }
    private static final int M_GLOW = 0, M_DUST = 1, M_SPARK = 2;
    private record Ring(Vec3 at, float radius, float start, float life, int rgb, boolean light) {}

    private static final Map<Integer, Rang> RANGS = new HashMap<>();
    private static final Map<Integer, Pellet> PELLETS = new HashMap<>();
    static final List<Cloud> CLOUDS = new ArrayList<>();
    private static final Map<Integer, Mine> MINES = new HashMap<>();
    private static final Map<Integer, Line> LINES = new HashMap<>();
    private static final List<Mote> MOTES = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    /** Smoke: nearly black, a little lift in the lighter puffs so it still reads as gas. */
    static final int SMOKE = 0x26282c, SMOKE_DARK = 0x111214, FLASH = 0xfffbf0, SPARK = 0xffd9a0, DUST = 0x8a8075;
    /** Batarang trail: ticks kept, and its colours (a cold steel-white edge round a pale gold core). */
    private static final int TRAIL = 9, RANG_EDGE = 0xcfe3f2, RANG_CORE = 0xfff1c8;

    private BatmanFx() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }
    private static float amount() { return BatmanConfig.EFFECTS.get().floatValue(); }
    private static int me() { var p = Minecraft.getInstance().player; return p == null ? -1 : p.getId(); }
    private static boolean near(Vec3 at, double range) { var p = Minecraft.getInstance().player; return p != null && p.position().distanceTo(at) < range; }

    // ------------------------------------------------------------------ what the server reports
    public static void receive(BatmanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = now();
        Vec3 at = p.pos(), dir = p.dir();
        if (p.kind() >= FX_CANNON_FIRST && p.kind() <= FX_CANNON_LAST) { BatmanCannonFx.receive(p); return; }
        if (p.kind() >= FX_SONIC_FIRST && p.kind() <= FX_SONIC_LAST) { BatmanSonicFx.receive(p); return; }
        switch (p.kind()) {
            case FX_PUNCH -> {
                boolean rapid = p.power() >= RAPID;
                MOTES.add(new Mote(at, Vec3.ZERO, rapid ? .35f : .6f, 0, FLASH, 3, t, M_GLOW, rapid ? .45f : .7f));
                for (int i = 0; i < (int) ((rapid ? 3 : 7) * amount()); i++)
                    MOTES.add(new Mote(at, dir.scale(.08).add(rnd(.12), rnd(.1) + .04, rnd(.12)), .3f + RANDOM.nextFloat() * .25f, .05f, DUST, 14 + RANDOM.nextInt(8), t, M_DUST, .35f));
                if (p.id() == me()) BatmanClient.shake(rapid ? .05f : .12f);
                else if (p.entity() == me()) BatmanClient.shake(rapid ? .1f : .22f);
            }
            case FX_BATARANG -> RANGS.put(p.id(), new Rang(p.id(), (int) p.power(), at, dir, t));
            case FX_BATARANG_HIT -> {
                Rang r = RANGS.get(p.id());
                if (p.power() < 0) RANGS.remove(p.id());
                else if (r != null) { r.pos = r.prev = at.subtract(dir.scale(.12)); r.vel = dir; r.stuck = true; r.stuckAt = t; }
                else { r = new Rang(p.id(), 1, at, dir, t); r.stuck = true; r.stuckAt = t; RANGS.put(p.id(), r); }
                sparks(at, dir.scale(-1), (int) (6 * amount()), .14f);
            }
            case FX_GADGET -> PELLETS.put(p.id(), new Pellet(p.id(), (int) p.power(), at, dir, t));
            case FX_SMOKE -> {
                PELLETS.remove(p.id());
                CLOUDS.add(new Cloud(p.entity(), p.id(), at, p.power(), t, (float) dir.x));
                RINGS.add(new Ring(at.add(0, .1, 0), p.power() * 1.1f, t, 18, 0x5a5e64, false));
                MOTES.add(new Mote(at.add(0, .3, 0), Vec3.ZERO, 1.1f, 0, 0xfff0d8, 3, t, M_GLOW, .35f));
                earth(at, (int) (10 * amount()), 1.2);
                // The burst: gas thrown out along the ground before the cloud fills in behind it.
                for (int i = 0; i < (int) (60 * amount()); i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, sp = (.25 + RANDOM.nextDouble() * .35) * Math.max(1, p.power() / 9);
                    MOTES.add(new Mote(at.add(0, .3, 0), new Vec3(Math.cos(a) * sp, .03 + RANDOM.nextDouble() * .08, Math.sin(a) * sp),
                            .6f + RANDOM.nextFloat() * .5f, .09f, i % 2 == 0 ? SMOKE_DARK : SMOKE, 28 + RANDOM.nextInt(20), t, M_DUST, .55f));
                }
                // His own smoke: his thermal vision comes on by itself to see the ones lost in it.
                if (p.entity() == me()) BatmanThermal.smoke(t + (float) dir.x);
            }
            case FX_FLASH -> {
                if (p.entity() >= 0 && p.entity() == me()) { BatmanVision.flashed(p.power()); break; }
                PELLETS.remove(p.id());
                // For the ones it does not blind: a small, sharp pop of light with a bloom, lighting the ground round it for an instant.
                MOTES.add(new Mote(at, Vec3.ZERO, 1.1f, 0, 0xffffff, 3, t, M_GLOW, 1f));
                MOTES.add(new Mote(at, Vec3.ZERO, 3.2f, 0, FLASH, 5, t, M_GLOW, .75f));
                MOTES.add(new Mote(at, Vec3.ZERO, 6.5f, 0, 0xdfe8ff, 4, t, M_GLOW, .25f));
                RINGS.add(new Ring(at.add(0, .05, 0), 5.5f, t, 6, 0xfff8e8, true));
                RINGS.add(new Ring(at.add(0, .05, 0), 2.6f, t, 5, 0xffffff, true));
                sparks(at, new Vec3(0, 1, 0), (int) (16 * amount()), .26f);
                var cam = mc.gameRenderer.getMainCamera().getPosition();
                double d = cam.distanceTo(at);
                boolean seen = d < p.power() * 2 && mc.level.clip(new ClipContext(cam, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
                if (seen) BatmanVision.glimpse((float) (1 - d / (p.power() * 2)));
                if (near(at, p.power() * 1.6)) BatmanClient.shake(.12f);
            }
            case FX_MINE -> { MINES.put(p.id(), new Mine(p.id(), at, t)); earth(at, (int) (3 * amount()), .2); }
            case FX_MINE_ARMED -> { Mine m = MINES.get(p.id()); if (m != null) m.armed = true; }
            case FX_MINE_BOOM -> {
                MINES.remove(p.id());
                if (p.power() <= 0) { for (int i = 0; i < 4; i++) MOTES.add(new Mote(at.add(0, .2, 0), new Vec3(rnd(.03), .03, rnd(.03)), .3f, .03f, SMOKE_DARK, 20, t, M_DUST, .4f)); break; }
                mc.level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y + .4, at.z, 0, 0, 0);
                MOTES.add(new Mote(at.add(0, .5, 0), Vec3.ZERO, 2.4f, 0, 0xffc070, 5, t, M_GLOW, .9f));
                RINGS.add(new Ring(at.add(0, .06, 0), Math.max(4.5f, p.power()), t, 13, 0xcfc6b8, false));
                RINGS.add(new Ring(at.add(0, .1, 0), Math.max(3f, p.power() * .7f), t, 9, 0xffc070, true));
                sparks(at, new Vec3(0, 1, 0), (int) (18 * amount()), .32f);
                earth(at, (int) (24 * amount()), 1);
                for (int i = 0; i < (int) (16 * amount()); i++)
                    MOTES.add(new Mote(at.add(0, .3, 0), new Vec3(rnd(.25), .05 + RANDOM.nextDouble() * .15, rnd(.25)), .6f + RANDOM.nextFloat() * .5f, .07f, DUST, 30 + RANDOM.nextInt(20), t, M_DUST, .5f));
                if (near(at, 20)) BatmanClient.shake(near(at, 6) ? .4f : .15f);
            }
            case FX_HOOK -> LINES.put(p.id(), new Line(p.id(), t, at, dir));
            case FX_HOOK_HIT -> {
                Line l = LINES.get(p.id());
                if (l != null) l.taut = t;
                sparks(at, new Vec3(0, 1, 0), (int) (5 * amount()), .12f);
                // The line round their legs: a little dust kicked up at their feet.
                if (p.power() == 2) for (int i = 0; i < (int) (6 * amount()); i++)
                    MOTES.add(new Mote(at.add(rnd(.3), 0, rnd(.3)), new Vec3(rnd(.04), .03, rnd(.04)), .3f, .04f, DUST, 14, t, M_DUST, .35f));
                BatmanClient.hookHit(p.id());
            }
            case FX_HOOK_END -> { LINES.remove(p.id()); BatmanClient.arrived(p.id(), p.power() > 0); }
            case FX_STRIKE -> {
                if (p.power() == 0) { BatmanClient.strike(p.id(), p.entity(), dir); LINES.remove(p.id()); break; }
                MOTES.add(new Mote(at, Vec3.ZERO, 1.2f, 0, FLASH, 4, t, M_GLOW, .85f));
                RINGS.add(new Ring(at, 2.2f, t, 8, 0xffffff, true));
                for (int i = 0; i < (int) (10 * amount()); i++)
                    MOTES.add(new Mote(at, dir.scale(.15).add(rnd(.15), rnd(.15), rnd(.15)), .4f, .06f, DUST, 16, t, M_DUST, .4f));
                if (p.id() == me() || p.entity() == me()) BatmanClient.shake(p.power() == 2 ? .35f : .25f);
            }
            case FX_DODGE -> {
                for (int i = 0; i < (int) (8 * amount()); i++)
                    MOTES.add(new Mote(at.add(rnd(.4), .1, rnd(.4)), new Vec3(rnd(.06), .02, rnd(.06)), .35f, .05f, DUST, 18, t, M_DUST, .35f));
            }
            case FX_STAGGER -> BatmanStatus.stagger(p.entity(), p.power());
            case FX_BOUND -> BatmanBound.receive(p);
            case FX_DOWNED -> BatmanStatus.downed(p.entity(), dir, p.power(), p.id());
            case FX_CRIT -> {
                BatmanStatus.crit(p.entity());
                MOTES.add(new Mote(at, Vec3.ZERO, 1f, 0, 0xffe27a, 4, t, M_GLOW, .8f));
                for (int i = 0; i < (int) (14 * amount()); i++)
                    MOTES.add(new Mote(at, new Vec3(rnd(.25), RANDOM.nextDouble() * .25, rnd(.25)), .02f, 0, i % 2 == 0 ? 0xffffff : 0xffd34a, 6 + RANDOM.nextInt(5), t, M_SPARK, 1));
                for (int i = 0; i < 10; i++) mc.level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, rnd(.6), RANDOM.nextDouble() * .5, rnd(.6));
                if (p.id() == me() || p.entity() == me()) BatmanClient.shake(.2f);
            }
            case FX_LAND -> { earth(at, (int) (6 * amount()), .6); RINGS.add(new Ring(at.add(0, .05, 0), 1.6f, t, 10, 0xc8c0b4, false)); }
            default -> {}
        }
        while (MOTES.size() > 700) MOTES.remove(0);
        while (CLOUDS.size() > 12) CLOUDS.remove(0);
    }
    private static double rnd(double s) { return (RANDOM.nextDouble() - .5) * 2 * s; }
    private static void sparks(Vec3 at, Vec3 dir, int n, float speed) {
        float t = now();
        for (int i = 0; i < n; i++)
            MOTES.add(new Mote(at, dir.scale(speed * .6).add(rnd(speed), RANDOM.nextDouble() * speed, rnd(speed)), .02f, 0, SPARK, 5 + RANDOM.nextInt(6), t, M_SPARK, 1));
    }
    private static void earth(Vec3 at, int n, double spread) {
        var level = Minecraft.getInstance().level;
        if (level == null || n <= 0) return;
        BlockPos pos = BlockPos.containing(at.x, at.y - .2, at.z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) { pos = pos.below(); state = level.getBlockState(pos); }
        if (state.isAir()) return;
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, r = RANDOM.nextDouble() * spread;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x + Math.cos(a) * r, at.y + .1, at.z + Math.sin(a) * r, Math.cos(a) * .15, .15 + RANDOM.nextDouble() * .2, Math.sin(a) * .15);
        }
    }

    /** True while the smoke of another Batman (or anyone's, for a non-Batman) covers this point now. */
    static Cloud cloudAt(Vec3 p) {
        float t = now();
        for (Cloud c : CLOUDS) if (c.inside(p, t)) return c;
        return null;
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        float t = mc.level.getGameTime();
        for (Iterator<Rang> it = RANGS.values().iterator(); it.hasNext(); ) {
            Rang r = it.next();
            r.prev = r.pos;
            if (!r.stuck) { r.trail.addLast(r.pos); while (r.trail.size() > TRAIL) r.trail.removeFirst(); }
            else if (!r.trail.isEmpty()) r.trail.removeFirst();
            if (r.stuck) { if (t - r.stuckAt > 100) it.remove(); continue; }
            if (t - r.spawn > 50) { it.remove(); continue; }
            Vec3 next = r.pos.add(r.vel);
            var hit = mc.level.clip(new ClipContext(r.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) { r.pos = r.prev = hit.getLocation().subtract(r.vel.normalize().scale(.12)); r.stuck = true; r.stuckAt = t; continue; }
            r.pos = next;
        }
        for (Iterator<Pellet> it = PELLETS.values().iterator(); it.hasNext(); ) {
            Pellet pl = it.next();
            pl.prev = pl.pos;
            if (t - pl.spawn > 80) { it.remove(); continue; }
            Vec3 next = pl.pos.add(pl.vel);
            var hit = mc.level.clip(new ClipContext(pl.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) { pl.pos = pl.prev = hit.getLocation(); pl.vel = Vec3.ZERO; continue; }
            pl.pos = next;
            pl.vel = pl.vel.add(0, -.05, 0).scale(.99);
            if (((int) t + pl.id) % 2 == 0) MOTES.add(new Mote(pl.pos, Vec3.ZERO, .12f, .02f, pl.gadget == G_SMOKE ? SMOKE : 0xdddddd, 8, t, M_DUST, .3f));
        }
        for (Iterator<Mote> it = MOTES.iterator(); it.hasNext(); ) {
            Mote m = it.next();
            if (t - m.start > m.life) { it.remove(); continue; }
            m.pos = m.pos.add(m.vel);
            m.vel = m.kind == M_SPARK ? m.vel.add(0, -.04, 0).scale(.94) : m.kind == M_DUST ? m.vel.scale(.9).add(0, .002, 0) : m.vel;
        }
        RINGS.removeIf(r -> t - r.start() > r.life());
        CLOUDS.removeIf(c -> t - c.start() > c.life() + 30);
        MINES.values().removeIf(m -> t - m.start > 20 * 620);
        // Smoke: gas keeps creeping out along the ground from the edge; inside someone else's, the view shakes a little.
        var camPos = mc.gameRenderer.getMainCamera().getPosition();
        for (Cloud c : CLOUDS) {
            float age = t - c.start();
            if (age < c.life() && RANDOM.nextFloat() < .9f * amount()) {
                double a = RANDOM.nextDouble() * Math.PI * 2, r = c.radius() * (.6 + RANDOM.nextDouble() * .4);
                Vec3 from = c.at().add(Math.cos(a) * r, .2 + RANDOM.nextDouble() * .6, Math.sin(a) * r);
                MOTES.add(new Mote(from, new Vec3(Math.cos(a) * .045, .008, Math.sin(a) * .045), .7f + RANDOM.nextFloat() * .5f, .05f,
                        RANDOM.nextBoolean() ? SMOKE : SMOKE_DARK, 40 + RANDOM.nextInt(30), t, M_DUST, .32f));
            }
            if (c.owner() != me() && c.inside(camPos, t) && ((int) t % 8) == 0) BatmanClient.shake(.03f);
        }
        // A little smoke still curls from a cloud as it thins.
        for (Cloud c : CLOUDS) {
            float age = t - c.start();
            if (age > c.life() - 40 && age < c.life() && RANDOM.nextFloat() < .3f * amount())
                MOTES.add(new Mote(c.at().add(rnd(c.radius() * .6), RANDOM.nextDouble() * 1.5, rnd(c.radius() * .6)), new Vec3(0, .02, 0), 1f, .04f, SMOKE, 30, t, M_DUST, .25f));
        }
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (RANGS.isEmpty() && PELLETS.isEmpty() && CLOUDS.isEmpty() && MINES.isEmpty() && LINES.isEmpty() && MOTES.isEmpty() && RINGS.isEmpty()) return;
        float partial = e.getPartialTick(), time = mc.level.getGameTime() + partial;
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer v = BatmanGear.buffer(buffers);
        // ---- the solid things
        for (Rang r : RANGS.values()) {
            Vec3 at = r.prev.lerp(r.pos, partial);
            p.pushPose();
            p.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            Vec3 d = r.vel.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : r.vel.normalize();
            // Flat in the air, spinning about its up axis; stuck: the tip in the wall, tilted.
            p.mulPose(Axis.YP.rotation((float) Math.atan2(d.x, d.z)));
            p.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(d.y, -1, 1))));
            if (r.stuck) p.mulPose(Axis.ZP.rotation(.5f + (r.id % 5) * .15f));
            else p.mulPose(Axis.YP.rotation((time - r.spawn) * 1.7f));
            BatmanGear.batarang(p, v, light(at), r.stuck ? 1 : 1.4f);
            p.popPose();
        }
        for (Pellet pl : PELLETS.values()) {
            Vec3 at = pl.prev.lerp(pl.pos, partial);
            p.pushPose();
            p.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            p.mulPose(Axis.XP.rotation((time - pl.spawn) * .5f));
            BatmanGear.pellet(p, v, light(at), pl.gadget);
            p.popPose();
        }
        for (Mine m : MINES.values()) {
            p.pushPose();
            p.translate(m.at.x - cam.x, m.at.y - cam.y, m.at.z - cam.z);
            float age = time - m.start;
            float blink = m.armed ? ((int) (age / 10) % 2 == 0 ? 1 : .15f) : ((int) (age / 3) % 2 == 0 ? .6f : .2f);
            BatmanGear.mine(p, v, light(m.at), blink);
            p.popPose();
        }
        buffers.endBatch();

        // ---- smoke, light, the line
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        var fx = FilmFx.batched();
        FilmContext c = new FilmContext(p, fx, cam, new Vec3(rr.x, rr.y, rr.z), new Vec3(uu.x, uu.y, uu.z), time, 0, partial);
        try {
            int self = me();
            for (Cloud cl : CLOUDS) smoke(c, cl, time, cl.owner() == self);
            for (Ring r : RINGS) {
                float k = (time - r.start()) / r.life();
                if (k < 0 || k > 1) continue;
                FilmFx.ring(c, r.at(), r.radius() * (1 - (1 - k) * (1 - k)), .25 + .35 * (1 - k), r.rgb(), (r.light() ? .55f : .35f) * (1 - k), r.light());
            }
            for (Mote m : MOTES) {
                float age = (time - m.start) / m.life;
                if (age < 0 || age > 1) continue;
                Vec3 at = m.pos.add(m.vel.scale(partial));
                switch (m.kind) {
                    case M_SPARK -> FilmFx.streak(c, at.subtract(m.vel.scale(1.4)), at, .02, m.rgb, 0, m.alpha * (1 - age), true);
                    case M_DUST -> FilmFx.puff(c, at, m.size + m.grow * (time - m.start), m.rgb, m.alpha * (1 - age) * Math.min(1, age * 5));
                    default -> FilmFx.glow(c, at, m.size * (1 + .5f * age), m.rgb, m.alpha * (1 - age));
                }
            }
            for (Rang r : RANGS.values()) rangTrail(c, r, time, partial);
            for (Mine m : MINES.values()) if (m.armed && (int) ((time - m.start) / 10) % 2 == 0) FilmFx.glow(c, m.at.add(0, .14, 0), .35, 0xff3b30, .8f);
            for (Line l : LINES.values()) line(c, l, time, partial);
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /**
     * A Batarang's trail: a ribbon through where it was over the last ticks, wide and bright at the Batarang and
     * thinning to nothing behind (a cold steel edge round a pale gold core), two thin spiral streaks cut by its spinning
     * wing tips, a soft glow on it and a glint each half turn. A stuck one keeps a short fading tail and a glint.
     */
    private static void rangTrail(FilmContext c, Rang r, float time, float partial) {
        Vec3 at = r.prev.lerp(r.pos, partial);
        Vec3[] pts = r.trail.toArray(new Vec3[0]);
        int n = pts.length;
        if (n > 0) {
            Vec3 next = at;
            for (int i = n - 1; i >= 0; i--) {
                Vec3 a = pts[i];
                float k0 = (i + 1f) / (n + 1), k1 = i / (n + 1f);
                if (next.distanceToSqr(a) > 1e-4) {
                    FilmFx.streak(c, a, next, .12 * k0 + .02, RANG_EDGE, .32f * k1, .32f * k0, true);
                    FilmFx.streak(c, a, next, .04 * k0 + .01, RANG_CORE, .55f * k1, .7f * k0, true);
                }
                next = a;
            }
        }
        if (r.stuck) {
            float since = time - r.stuckAt;
            if (since < 8) FilmFx.glow(c, at, .4, RANG_CORE, .5f * (1 - since / 8));
            return;
        }
        Vec3 d = r.vel.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : r.vel.normalize();
        Vec3 side = d.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        // The wing tips' spiral: two thin streaks wound round the flight line behind it.
        float spin = (time - r.spawn) * 1.7f;
        for (int w = 0; w < 2; w++) {
            Vec3 last = null;
            for (int j = 0; j <= 6; j++) {
                double back = j * .22, a = spin - j * .55 + w * Math.PI;
                Vec3 pt = at.subtract(d.scale(back)).add(side.scale(Math.cos(a) * .2)).add(d.cross(side).scale(Math.sin(a) * .05));
                if (last != null) FilmFx.streak(c, last, pt, .018, 0xffffff, .5f * (1 - (j - 1) / 6f), .5f * (1 - j / 6f), true);
                last = pt;
            }
        }
        FilmFx.glow(c, at, .38, RANG_EDGE, .32f);
        float glint = Math.max(0, Mth.cos(spin * 2));
        if (glint > .9f) FilmFx.glow(c, at.add(side.scale(.12)), .22, 0xffffff, .8f * (glint - .9f) * 10);
    }
    /**
     * A smoke cloud: about seventy big soft puffs filling a low dome, bursting out from the middle over the first
     * second, churning slowly, thinning away at the end. For him it is a thin veil (he sees through it).
     */
    private static void smoke(FilmContext c, Cloud cl, float time, boolean own) {
        float age = time - cl.start();
        if (age < 0 || age > cl.life() + 30) return;
        float grow = 1 - (float) Math.pow(1 - Math.min(1, age / (22f + cl.radius())), 3);
        float fade = 1 - Mth.clamp((age - cl.life()) / 30f, 0, 1);
        // More (and bigger) puffs for a wider cloud; fewer when it is far away.
        double far = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(cl.at()) - cl.radius();
        float wide = Math.max(1, cl.radius() / 7f);
        int n = (int) (Math.min(260, 90 * Math.pow(wide, 1.25)) * Math.max(.3f, amount()) * (far > 48 ? .4 : far > 24 ? .7 : 1));
        float base = (own ? .2f : .82f) * fade;
        for (int i = 0; i < n; i++) {
            double h1 = FilmFx.hash(cl.id() * 131 + i), h2 = FilmFx.hash(cl.id() * 131 + i + 41), h3 = FilmFx.hash(cl.id() * 131 + i + 83);
            double a = h1 * Math.PI * 2 + age * .004 * (h2 - .5), rad = Math.sqrt(h2) * cl.radius() * grow;
            double y = (.25 + h3 * .85) * Math.min(cl.radius() * .45, 6) * (.4 + .6 * grow) * (1 - .45 * (rad / Math.max(.1, cl.radius())));
            // Each puff churns slowly round its place and swells; the whole cloud breathes.
            double churn = age * (.012 + .01 * h3) + i;
            Vec3 at = cl.at().add(Math.cos(a) * rad + Math.sin(churn) * .45, y + Math.sin(age * .02 + i * 1.7) * .25, Math.sin(a) * rad + Math.cos(churn * .9) * .45);
            double size = (1.5 + h3 * 1.4) * Math.sqrt(wide) * (.5 + .5 * grow) * (1 + age / Math.max(1, cl.life()) * .35);
            int rgb = i % 3 == 0 ? SMOKE_DARK : SMOKE;
            FilmFx.puff(c, at, size, rgb, base * (.6f + .4f * (float) h1));
        }
    }
    /**
     * The grapnel line from his gun to the hook: while the hook flies it is slack, a travelling wave that dies along it;
     * the moment the hook bites it snaps taut (the wave collapses over TAUT ticks), then hangs straight.
     */
    private static void line(FilmContext c, Line l, float time, float partial) {
        var mc = Minecraft.getInstance();
        Entity batman = mc.level.getEntity(l.batman);
        if (batman == null) return;
        Vec3 from = BatmanLayer.muzzle(l.batman);
        if (from == null) from = batman.getPosition(partial).add(0, 1.35, 0).add(batman.getViewVector(partial).scale(.6));
        BatmanClient.State s = BatmanClient.get(batman);
        Vec3 to;
        if (s != null && s.hook != null) {
            float k = Mth.clamp((time - s.received) / 1f, 0, 1);
            to = s.hookPrev == null ? s.hook : s.hookPrev.lerp(s.hook, k);
        } else {
            double d = l.to.distanceTo(l.from), k = Math.min(1, (time - l.start) * HOOK_SPEED / Math.max(.1, d));
            to = l.from.lerp(l.to, k);
        }
        // The yank: his left hand holds the line between the gun and their legs, hauling it.
        if (s != null && s.action == GRAPNEL_YANK) {
            Vec3 hand = BatmanLayer.hand(l.batman, 1);
            if (hand == null) hand = batman.getPosition(partial).add(0, 1.05, 0).add(batman.getViewVector(partial).scale(.5));
            FilmFx.streak(c, from, hand, .03, 0x16181b, .95f, .95f, false);
            FilmFx.streak(c, hand, to, .03, 0x16181b, .95f, .95f, false);
            return;
        }
        Vec3 span = to.subtract(from);
        double len = span.length();
        if (len < .05) return;
        Vec3 dir = span.scale(1 / len);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        float slack = l.taut < 0 ? 1 : 1 - Mth.clamp((time - l.taut) / TAUT, 0, 1);
        int seg = 24;
        Vec3 prev = from;
        for (int i = 1; i <= seg; i++) {
            double u = i / (double) seg;
            double env = Math.sin(u * Math.PI);
            double wave = Math.sin(u * 9 - time * 1.4) * .32 * env * slack + Math.sin(u * 4 + time * .6) * .14 * env * slack;
            double sag = -env * .4 * slack;
            Vec3 pt = from.add(span.scale(u)).add(side.scale(wave)).add(up.scale(sag + Math.cos(u * 7 - time) * .08 * env * slack));
            FilmFx.streak(c, prev, pt, .028, 0x16181b, .95f, .95f, false);
            prev = pt;
        }
        if (l.taut >= 0 && time - l.taut < 4) FilmFx.glow(c, to, .5, 0xffffff, .5f * (1 - (time - l.taut) / 4));
    }
    private static int light(Vec3 at) {
        var level = Minecraft.getInstance().level;
        return level == null ? 15728880 : LevelRenderer.getLightColor(level, BlockPos.containing(at));
    }
    private static void clear() { RANGS.clear(); PELLETS.clear(); CLOUDS.clear(); MINES.clear(); LINES.clear(); MOTES.clear(); RINGS.clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); BatmanVision.clear(); }
}
