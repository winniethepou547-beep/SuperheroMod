package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.heroes.magneto.MagnetoConfig;
import com.FIRNI.superheromod.network.packet.MagnetoFxPacket;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.*;

/**
 * Magneto's metal and what it does, on every client: the rods of the barrage falling, turning, driven into the ground
 * (earth thrown up, sparks, a ring of dust), standing there and sinking away; the shards he flicks; the scrap that flies
 * in from behind the one he grabs and wraps them, then falls off; the iron fist assembling, following his aim, punching
 * (shockwave, earth, sparks) and falling apart; the shield's columns rising round him and the pieces they burst into,
 * stuck where they land. All the metal is drawn solid and lit by the world; the light, dust and sparks after it.
 * Faint field lines join his hand to whatever he holds.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class MagnetoFx {
    private MagnetoFx() {}

    static final int STEEL = 0xd4d8e2, SPARK = 0xffc777, DUST = 0x8a7f72, FIELD = 0xb8c6ff, FLASH = 0xfff2dc;
    private static final float G_DUST = .03f;

    /** A rod, shard or piece of the burst: flying (simulated here as the server does), then stuck where it hit. */
    private static final class Bit {
        final int id, kind; Vec3 pos, prev, vel, axis; final float g, spawn; boolean stuck; float stuckAt; final Quaternionf spinAxis;
        Bit(int id, int kind, Vec3 pos, Vec3 vel, float g, float spawn) {
            this.id = id; this.kind = kind; this.pos = pos; this.prev = pos; this.vel = vel; this.g = g; this.spawn = spawn;
            this.axis = vel.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : vel.normalize();
            this.spinAxis = new Quaternionf().rotationXYZ(MetalMesh.hash(id) * 6f, MetalMesh.hash(id + 1) * 6f, MetalMesh.hash(id + 2) * 6f);
        }
    }
    private static final int ROD = 0, SHARD_BIT = 1, PIECE = 2;
    /** Scrap wrapping someone he holds. */
    private record Grab(int magneto, int target, Vec3 dir, Vec3 from, int seed, int count, float start) {}
    /** A falling piece (scrap let go, the fist breaking up): tumbling down, resting on the ground, fading. */
    private static final class Fall {
        Vec3 pos, prev, vel; final Vec3 spin; final int seed, kind; final float size, start; float rest = -1; final double ground;
        Fall(Vec3 pos, Vec3 vel, int seed, int kind, float size, float start, double ground) {
            this.pos = pos; this.prev = pos; this.vel = vel; this.seed = seed; this.kind = kind; this.size = size; this.start = start; this.ground = ground;
            this.spin = new Vec3(MetalMesh.hash(seed) - .5, MetalMesh.hash(seed + 1) - .5, MetalMesh.hash(seed + 2) - .5).scale(.6);
        }
    }
    /** A light, a puff of dust or a spark (drawn with the film's light and matter). */
    private static final class Mote {
        Vec3 pos, vel; final float size, grow, life, start, alpha; final int rgb, kind;
        Mote(Vec3 pos, Vec3 vel, float size, float grow, int rgb, float life, float start, int kind, float alpha) {
            this.pos = pos; this.vel = vel; this.size = size; this.grow = grow; this.rgb = rgb; this.life = life; this.start = start; this.kind = kind; this.alpha = alpha;
        }
    }
    private static final int M_GLOW = 0, M_DUST = 1, M_SPARK = 2;
    private record Ring(Vec3 at, float radius, float start, float life, int rgb, boolean light) {}

    private static final Map<Integer, Bit> BITS = new HashMap<>();
    private static final Map<Integer, Grab> GRABS = new HashMap<>();
    private static final List<Fall> FALLS = new ArrayList<>();
    private static final List<Mote> MOTES = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    /** The shield's columns per Magneto: how many and how far out. */
    private static final Map<Integer, float[]> COLUMNS = new HashMap<>();
    private static final Random RANDOM = new Random();

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }
    private static float amount() { return MagnetoConfig.EFFECTS.get().floatValue(); }
    private static boolean near(Vec3 at, double range) { var me = Minecraft.getInstance().player; return me != null && me.position().distanceTo(at) < range; }

    // ------------------------------------------------------------------ what the server reports
    public static void receive(MagnetoFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = now();
        Vec3 at = p.pos(), dir = p.dir();
        switch (p.kind()) {
            case FX_SHARD -> BITS.put(p.id(), new Bit(p.id(), SHARD_BIT, at, dir, .015f, t));
            case FX_ROD -> BITS.put(p.id(), new Bit(p.id(), ROD, at, dir, p.power(), t));
            case FX_PIECE -> BITS.put(p.id(), new Bit(p.id(), PIECE, at, dir, p.power(), t));
            case FX_SHARD_HIT, FX_PIECE_HIT -> {
                Bit b = BITS.get(p.id());
                if (p.power() < 0) BITS.remove(p.id());
                else if (b != null) stick(b, at, dir, t);
                sparks(at, dir.scale(-1), (int) (6 * amount()), .12f);
                MOTES.add(new Mote(at, Vec3.ZERO, .3f, 0, FLASH, 3, t, M_GLOW, .6f));
                if (p.power() >= 0) earth(at, (int) (3 * amount()), .15);
            }
            case FX_ROD_HIT -> {
                Bit b = BITS.get(p.id());
                if (b != null) stick(b, at, dir, t);
                else { b = new Bit(p.id(), ROD, at, dir, 0, t); stick(b, at, dir, t); BITS.put(p.id(), b); }
                Vec3 ground = at.add(dir.scale(1.7 - 1.0));
                impact(ground, p.power(), .7f);
                if (near(ground, 24)) MagnetoClient.shake(near(ground, 8) ? .22f : .08f);
            }
            case FX_GRAB -> {
                Entity target = mc.level.getEntity(p.entity());
                Vec3 base = target == null ? at : target.position();
                GRABS.put(p.entity(), new Grab(-1, p.entity(), dir, base.add(dir.scale(3.6)).add(0, 1.2, 0), p.id(), (int) p.power(), t));
                earth(base.add(dir.scale(3.6)), (int) (8 * amount()), .6);
            }
            case FX_SLAM -> {
                impact(at, p.id() == 1 ? 2.2f : 1.2f, .55f);
                sparks(at, dir.scale(-1), (int) (10 * amount()), .2f);
                if (near(at, 18)) MagnetoClient.shake(.16f);
            }
            case FX_RELEASE -> {
                Grab g = GRABS.remove(p.entity());
                Entity target = mc.level.getEntity(p.entity());
                if (g != null && target != null) {
                    // The scrap lets go: every piece drops off them.
                    for (int i = 0; i < g.count(); i++) {
                        Vec3 pos = target.position().add(offset(g, i, target, t));
                        Vec3 vel = pos.subtract(target.position().add(0, target.getBbHeight() * .5, 0)).normalize().scale(.12).add(dir.scale(.4)).add(0, .1, 0);
                        FALLS.add(new Fall(pos, vel, g.seed() + i * 13, 0, .32f, t, groundY(pos)));
                    }
                }
            }
            case FX_FIST_UP -> earth(at.subtract(0, MagnetoConfig.FIST_HEIGHT.get(), 0), (int) (14 * amount()), 1.2);
            case FX_PUNCH -> {
                impact(at, p.power(), 1f);
                RINGS.add(new Ring(at.add(0, .1, 0), p.power() * 1.6f, t, 12, 0xd8d2c8, false));
                if (near(at, 30)) MagnetoClient.shake(near(at, 10) ? .4f : .15f);
            }
            case FX_FIST_BREAK -> {
                for (int i = 0; i < 26; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    Vec3 pos = at.add(Math.cos(a) * .8, RANDOM.nextDouble() * 2 - .5, Math.sin(a) * .8);
                    Vec3 vel = new Vec3(Math.cos(a) * .15, .12 + RANDOM.nextDouble() * .15, Math.sin(a) * .15).add(dir.scale(.3));
                    FALLS.add(new Fall(pos, vel, p.id() + i * 7, i % 3 == 0 ? 1 : 0, .45f, t, groundY(pos)));
                }
                sparks(at, new Vec3(0, 1, 0), (int) (12 * amount()), .25f);
            }
            case FX_COLUMNS -> {
                COLUMNS.put(p.entity(), new float[]{Math.max(3, p.id()), p.power()});
                for (int i = 0; i < p.id(); i++) {
                    double a = Math.PI * 2 * i / p.id();
                    earth(at.add(Math.cos(a) * p.power(), 0, Math.sin(a) * p.power()), (int) (6 * amount()), .4);
                }
                if (near(at, 16)) MagnetoClient.shake(.12f);
            }
            case FX_BLOCK -> { sparks(at, dir.scale(-1), (int) (8 * amount()), .18f); MOTES.add(new Mote(at, Vec3.ZERO, .5f, 0, FLASH, 3, t, M_GLOW, .7f)); }
            case FX_BURST -> {
                COLUMNS.remove(p.entity());
                MOTES.add(new Mote(at.add(0, 1.2, 0), Vec3.ZERO, 2.4f, 0, FLASH, 4, t, M_GLOW, .55f));
                RINGS.add(new Ring(at.add(0, .1, 0), p.power() * 3.5f, t, 14, 0xd8d2c8, false));
                RINGS.add(new Ring(at.add(0, 1.2, 0), p.power() * 2.6f, t, 8, STEEL, true));
                earth(at, (int) (20 * amount()), p.power());
                if (near(at, 24)) MagnetoClient.shake(near(at, 8) ? .35f : .12f);
            }
            case FX_LIFT -> { RINGS.add(new Ring(at.add(0, .05, 0), 2.2f, t, 10, 0xc8c0b4, false)); earth(at, (int) (6 * amount()), .8); }
            case FX_LAND -> earth(at, (int) (5 * amount()), .6);
            default -> {}
        }
        while (MOTES.size() > 600) MOTES.remove(0);
        while (FALLS.size() > 220) FALLS.remove(0);
    }
    private static void stick(Bit b, Vec3 at, Vec3 dir, float t) {
        b.pos = at; b.prev = at; b.axis = dir.lengthSqr() < 1e-6 ? b.axis : dir.normalize(); b.vel = Vec3.ZERO; b.stuck = true; b.stuckAt = t;
    }
    /** Something heavy striking the ground: earth thrown up, a ring of dust, sparks, a short flash. */
    private static void impact(Vec3 at, float radius, float power) {
        float t = now();
        earth(at, (int) ((10 + 14 * power) * amount()), radius * .5);
        sparks(at, new Vec3(0, 1, 0), (int) ((8 + 10 * power) * amount()), .22f * (.6f + power));
        RINGS.add(new Ring(at.add(0, .06, 0), radius * 1.3f, t, 10, 0xcfc6b8, false));
        MOTES.add(new Mote(at.add(0, .3, 0), Vec3.ZERO, .9f + radius * .3f, 0, FLASH, 3, t, M_GLOW, .8f * power));
        for (int i = 0; i < (int) (14 * amount()); i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            Vec3 v = new Vec3(Math.cos(a) * (.08 + RANDOM.nextDouble() * .12), .03 + RANDOM.nextDouble() * .06, Math.sin(a) * (.08 + RANDOM.nextDouble() * .12));
            MOTES.add(new Mote(at.add(0, .2, 0), v, .5f + RANDOM.nextFloat() * .4f, .06f, DUST, 26 + RANDOM.nextInt(16), t, M_DUST, .5f));
        }
    }
    /** Real pieces of the ground under a spot flying up (the game's own block particles, in its colours). */
    private static void earth(Vec3 at, int n, double spread) {
        var level = Minecraft.getInstance().level;
        if (level == null || n <= 0) return;
        BlockPos pos = BlockPos.containing(at.x, at.y - .2, at.z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) { pos = pos.below(); state = level.getBlockState(pos); }
        if (state.isAir()) return;
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, r = RANDOM.nextDouble() * spread;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x + Math.cos(a) * r, at.y + .1, at.z + Math.sin(a) * r,
                    Math.cos(a) * .2, .2 + RANDOM.nextDouble() * .3, Math.sin(a) * .2);
        }
    }
    private static void sparks(Vec3 at, Vec3 dir, int n, float speed) {
        float t = now();
        for (int i = 0; i < n; i++) {
            Vec3 v = dir.scale(speed * .6).add((RANDOM.nextDouble() - .5) * speed * 2, RANDOM.nextDouble() * speed, (RANDOM.nextDouble() - .5) * speed * 2);
            MOTES.add(new Mote(at, v, .02f, 0, SPARK, 5 + RANDOM.nextInt(6), t, M_SPARK, 1));
        }
    }
    private static double groundY(Vec3 at) {
        var level = Minecraft.getInstance().level;
        if (level == null) return at.y - 2;
        var me = Minecraft.getInstance().player;
        if (me == null) return at.y - 2;
        var hit = level.clip(new ClipContext(at, at.subtract(0, 30, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, me));
        return hit.getType() == HitResult.Type.MISS ? at.y - 30 : hit.getLocation().y;
    }

    // ------------------------------------------------------------------ every tick: the physics
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        float t = mc.level.getGameTime();
        for (Iterator<Bit> it = BITS.values().iterator(); it.hasNext(); ) {
            Bit b = it.next();
            b.prev = b.pos;
            if (b.stuck) {
                float stay = b.kind == ROD ? ROD_STAY + ROD_SINK : SHARD_STAY + SHARD_FADE;
                if (t - b.stuckAt > stay) it.remove();
                // A rod sinks back into the ground before it goes.
                else if (b.kind == ROD && t - b.stuckAt > ROD_STAY) b.pos = b.pos.add(b.axis.scale(.14));
                continue;
            }
            if (t - b.spawn > 90) { it.remove(); continue; }
            Vec3 vel = b.vel.add(0, -b.g, 0);
            float half = b.kind == ROD ? 1.7f : b.kind == PIECE ? .35f : .25f;
            Vec3 tip = b.pos.add(b.axis.scale(half)), next = b.pos.add(vel), nextTip = next.add(vel.normalize().scale(half));
            // Our own world says it struck (the server's word follows): it stops there.
            if (mc.player == null) continue;
            var hit = mc.level.clip(new ClipContext(tip, nextTip, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 d = vel.normalize();
                stick(b, hit.getLocation().subtract(d.scale(half - (b.kind == ROD ? 1.0 : .25))), d, t);
                b.prev = b.pos;
                continue;
            }
            b.pos = next; b.vel = vel;
            if (b.kind == ROD) b.axis = vel.normalize();
            // The rod's own wind: a few streaks of air and grit behind it.
            if (b.kind == ROD && ((int) t + b.id) % 2 == 0) MOTES.add(new Mote(b.pos.subtract(b.axis.scale(1.6)), b.vel.scale(-.05), .25f, .04f, 0xb0aca6, 8, t, M_DUST, .25f));
        }
        for (Iterator<Fall> it = FALLS.iterator(); it.hasNext(); ) {
            Fall f = it.next();
            f.prev = f.pos;
            if (t - f.start > 120) { it.remove(); continue; }
            if (f.rest >= 0) continue;
            f.vel = f.vel.add(0, -.05, 0).scale(.98);
            f.pos = f.pos.add(f.vel);
            if (f.pos.y <= f.ground + .05) {
                f.pos = new Vec3(f.pos.x, f.ground + .05, f.pos.z);
                if (Math.abs(f.vel.y) < .12) { f.rest = t; f.vel = Vec3.ZERO; }
                else f.vel = new Vec3(f.vel.x * .5, -f.vel.y * .35, f.vel.z * .5);
            }
        }
        for (Iterator<Mote> it = MOTES.iterator(); it.hasNext(); ) {
            Mote m = it.next();
            if (t - m.start > m.life) { it.remove(); continue; }
            m.pos = m.pos.add(m.vel);
            m.vel = m.kind == M_SPARK ? m.vel.add(0, -.04, 0).scale(.94) : m.kind == M_DUST ? m.vel.scale(.9).add(0, G_DUST * .1, 0) : m.vel;
        }
        RINGS.removeIf(r -> t - r.start() > r.life());
        GRABS.values().removeIf(g -> mc.level.getEntity(g.target()) == null || t - g.start() > 600);
        // The columns rising throw a little earth up round their feet.
        for (var entry : MagnetoClient.all()) {
            MagnetoClient.State s = entry.getValue();
            float[] col = COLUMNS.get(entry.getKey());
            Entity owner = mc.level.getEntity(entry.getKey());
            if (col == null || owner == null || !s.shield() || s.shieldAge > SHIELD_RAISE_TICKS || ((int) t & 1) != 0) continue;
            for (int i = 0; i < (int) col[0]; i++) earth(column(owner.position(), s.shieldAge, i, (int) col[0], col[1]), 1, .3);
        }
    }

    // ------------------------------------------------------------------ positions
    private static Vec3 column(Vec3 owner, float age, int i, int n, float radius) {
        double a = Math.PI * 2 * i / n + age * .004;
        return owner.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
    }
    /** Where piece i of the scrap sits on the one held (relative to their feet), slowly circling round them. */
    private static Vec3 offset(Grab g, int i, Entity target, float t) {
        float h = MetalMesh.hash(g.seed() + i * 3), a = MetalMesh.hash(g.seed() + i * 5) * 6.283f + (t - g.start()) * .025f;
        double r = target.getBbWidth() * .55 + .12 + .1 * MetalMesh.hash(g.seed() + i * 7);
        return new Vec3(Math.cos(a) * r, .1 + h * (target.getBbHeight() - .2), Math.sin(a) * r);
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean anyState = false;
        for (var entry : MagnetoClient.all()) { var s = entry.getValue(); if (s.fistUp() || s.shield() || s.holding() || s.action == FIST_SUMMON) anyState = true; }
        if (BITS.isEmpty() && FALLS.isEmpty() && MOTES.isEmpty() && RINGS.isEmpty() && GRABS.isEmpty() && !anyState) return;
        float partial = e.getPartialTick();
        float time = mc.level.getGameTime() + partial;
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer v = MetalMesh.buffer(buffers);
        // ---- the metal, solid
        for (Bit b : BITS.values()) {
            Vec3 at = b.prev.lerp(b.pos, partial);
            p.pushPose();
            p.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            int light = light(at);
            if (b.kind == ROD) {
                // Pointing along its flight, tip first, turning about its length as it falls.
                p.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) b.axis.x, (float) b.axis.y, (float) b.axis.z)));
                float spin = b.stuck ? (b.stuckAt - b.spawn) * .5f : (time - b.spawn) * .5f;
                p.mulPose(Axis.YP.rotation(spin + b.id));
                MetalMesh.rod(p, v, light, 3.4f, b.id);
            } else {
                p.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) b.axis.x, (float) b.axis.y, (float) b.axis.z)));
                if (b.kind == PIECE) {
                    float tumble = b.stuck ? (b.stuckAt - b.spawn) : (time - b.spawn);
                    p.mulPose(b.spinAxis);
                    p.mulPose(Axis.XP.rotation(tumble * .55f));
                    float fade = b.stuck ? 1 - Mth.clamp((time - b.stuckAt - SHARD_STAY) / SHARD_FADE, 0, 1) : 1;
                    p.scale(fade, fade, fade);
                    MetalMesh.fragment(p, v, light, b.id, .55f);
                } else MetalMesh.shard(p, v, light, b.id);
            }
            p.popPose();
        }
        for (Fall f : FALLS) {
            Vec3 at = f.prev.lerp(f.pos, partial);
            float age = time - f.start, fade = 1 - Mth.clamp((age - 90) / 30, 0, 1);
            if (fade <= 0) continue;
            float turn = f.rest >= 0 ? f.rest - f.start : age;
            p.pushPose();
            p.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            p.mulPose(new Quaternionf().rotationXYZ((float) f.spin.x * turn, (float) f.spin.y * turn, (float) f.spin.z * turn));
            p.scale(fade, fade, fade);
            if (f.kind == 1) MetalMesh.fragment(p, v, light(at), f.seed, f.size);
            else MetalMesh.scrap(p, v, light(at), f.seed, f.size);
            p.popPose();
        }
        // The scrap round whoever is held.
        for (Grab g : GRABS.values()) {
            Entity target = mc.level.getEntity(g.target());
            if (target == null) continue;
            Vec3 base = target.getPosition(partial);
            float k = Mth.clamp((time - g.start()) / SCRAP_FLY, 0, 1);
            for (int i = 0; i < g.count(); i++) {
                float delay = MetalMesh.hash(g.seed() + i * 11) * .35f;
                float ki = Mth.clamp((k - delay) / (1 - delay), 0, 1);
                ki = ki * ki * (3 - 2 * ki);
                Vec3 end = base.add(offset(g, i, target, time));
                Vec3 start = g.from().add((MetalMesh.hash(g.seed() + i) - .5) * 3, (MetalMesh.hash(g.seed() + i + 1) - .5) * 2.4, (MetalMesh.hash(g.seed() + i + 2) - .5) * 3);
                Vec3 mid = start.lerp(end, .5).add(0, 1.2, 0);
                Vec3 at = start.scale((1 - ki) * (1 - ki)).add(mid.scale(2 * ki * (1 - ki))).add(end.scale(ki * ki));
                p.pushPose();
                p.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
                Vec3 out = end.subtract(base.add(0, end.y - base.y, 0));
                float face = (float) Math.atan2(out.x, out.z);
                p.mulPose(Axis.YP.rotation(face));
                p.mulPose(Axis.XP.rotation((1 - ki) * (time - g.start()) * .7f + 1.4f + .3f * MetalMesh.hash(g.seed() + i * 9)));
                p.mulPose(Axis.ZP.rotation(.04f * Mth.sin(time * .9f + i)));
                MetalMesh.scrap(p, v, light(at), g.seed() + i * 13, .32f);
                p.popPose();
            }
        }
        // Each Magneto's fist and shield columns.
        for (var entry : MagnetoClient.all()) {
            MagnetoClient.State s = entry.getValue();
            Entity owner = mc.level.getEntity(entry.getKey());
            if (owner == null) continue;
            if (s.fistUp() || s.action == FIST_SUMMON) {
                Vec3 at = MagnetoClient.fist(s, partial);
                float assemble = s.action == FIST_SUMMON ? Mth.clamp(MagnetoClient.clock(s, partial) / FIST_SUMMON_TICKS, 0, 1) : 1;
                Vec3 moving = s.fist.subtract(s.fistPrev);
                p.pushPose();
                p.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
                // Facing the way he looks along, leaning into its swing.
                p.mulPose(Axis.YP.rotationDegrees(-owner.getViewYRot(partial)));
                p.mulPose(Axis.XP.rotation((float) Mth.clamp(moving.z * .4, -.4, .4)));
                p.mulPose(Axis.ZP.rotation((float) Mth.clamp(-moving.x * .4, -.4, .4) + .03f * Mth.sin(time * .07f)));
                MetalMesh.fist(p, v, light(at), assemble, entry.getKey());
                p.popPose();
            }
            float[] col = COLUMNS.get(entry.getKey());
            if (s.shield() && col != null) {
                float age = s.shieldAge + partial;
                float rise = Mth.clamp(age / SHIELD_RAISE_TICKS, 0, 1);
                rise = 1 - (1 - rise) * (1 - rise) * (1 - rise);
                Vec3 pos = owner.getPosition(partial);
                for (int i = 0; i < (int) col[0]; i++) {
                    Vec3 c = column(pos, age, i, (int) col[0], col[1]);
                    float height = 3.2f + .5f * MetalMesh.hash(entry.getKey() + i);
                    p.pushPose();
                    p.translate(c.x - cam.x, c.y - cam.y - height * (1 - rise) - .3, c.z - cam.z);
                    p.mulPose(Axis.YP.rotation((float) (Math.PI * 2 * i / col[0]) + age * .004f));
                    p.mulPose(Axis.ZP.rotation(.03f * Mth.sin(time * .05f + i)));
                    MetalMesh.column(p, v, light(c.add(0, 1, 0)), height, entry.getKey() * 7 + i);
                    p.popPose();
                }
            }
        }
        buffers.endBatch();

        // ---- light, dust, sparks, field lines
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        var fx = FilmFx.batched();
        FilmContext c = new FilmContext(p, fx, cam, new Vec3(rr.x, rr.y, rr.z), new Vec3(uu.x, uu.y, uu.z), time, 0, partial);
        try {
            for (Ring r : RINGS) {
                float k = (time - r.start()) / r.life();
                if (k < 0 || k > 1) continue;
                FilmFx.ring(c, r.at(), r.radius() * (1 - (1 - k) * (1 - k)), .25 + .3 * (1 - k), r.rgb(), (r.light() ? .5f : .35f) * (1 - k), r.light());
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
            // Flying rods: a faint glint at the tip.
            for (Bit b : BITS.values()) if (b.kind == ROD && !b.stuck) FilmFx.glow(c, b.prev.lerp(b.pos, partial).add(b.axis.scale(1.9)), .25, STEEL, .25f);
            if (MagnetoConfig.FIELD_LINES.get()) fieldLines(c, time, partial);
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /** Faint lines of the field from his hand to whatever he holds or drives, dashes running along them. */
    private static void fieldLines(FilmContext c, float time, float partial) {
        var mc = Minecraft.getInstance();
        for (var entry : MagnetoClient.all()) {
            MagnetoClient.State s = entry.getValue();
            Entity owner = mc.level.getEntity(entry.getKey());
            if (owner == null) continue;
            Vec3 to = null;
            if (s.holding() && s.held >= 0) { Entity t = mc.level.getEntity(s.held); if (t != null) to = t.getPosition(partial).add(0, t.getBbHeight() * .5, 0); }
            else if (s.fistUp()) to = MagnetoClient.fist(s, partial).add(0, 1.2, 0);
            if (to == null) continue;
            float yaw = Mth.rotLerp(partial, ((net.minecraft.world.entity.LivingEntity) owner).yBodyRotO, ((net.minecraft.world.entity.LivingEntity) owner).yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 hand = owner.getPosition(partial).add(-Mth.cos(yaw) * .38 - Mth.sin(yaw) * .55, 1.35, -Mth.sin(yaw) * .38 + Mth.cos(yaw) * .55);
            for (int line = 0; line < 3; line++) {
                Vec3 bend = new Vec3(Mth.sin(line * 2.1f + time * .05f), .6 + .3 * line, Mth.cos(line * 2.1f + time * .05f)).scale(.6 + .3 * line);
                Vec3 prev = hand;
                int n = 12;
                for (int i = 1; i <= n; i++) {
                    float u = i / (float) n;
                    Vec3 pt = hand.scale((1 - u) * (1 - u)).add(hand.lerp(to, .5).add(bend).scale(2 * u * (1 - u))).add(to.scale(u * u));
                    float dash = .5f + .5f * Mth.sin(u * 18 - time * .6f + line);
                    FilmFx.streak(c, prev, pt, .012, FIELD, .12f * dash, .12f * dash, true);
                    prev = pt;
                }
            }
        }
    }
    private static int light(Vec3 at) {
        var level = Minecraft.getInstance().level;
        return level == null ? 15728880 : LevelRenderer.getLightColor(level, BlockPos.containing(at));
    }

    // ------------------------------------------------------------------ first person: his gauntlets
    @SubscribeEvent public static void hand(RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !MagnetoClient.isHero(mc.player) || FilmDirector.playing()) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        MagnetoClient.State s = MagnetoClient.get(mc.player);
        int action = s == null ? IDLE : s.action;
        float t = s == null ? 0 : MagnetoClient.clock(s, e.getPartialTick());
        float time = mc.player.tickCount + e.getPartialTick();
        PoseStack p = e.getPoseStack();
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            float sx = right ? 1 : -1;
            // Where the hand rests (low at the edge), and how far a cast brings it up into view.
            float up = 0, curl = .35f, reach = 0;
            switch (action) {
                case SHARD -> { if (right) { up = Mth.sin(Mth.clamp(t / SHARD_TICKS, 0, 1) * Mth.PI); reach = up; curl = .05f; } }
                case BARRAGE -> { if (right) { up = t < BARRAGE_AT ? Mth.clamp(t / 4, 0, 1) * 1.3f : 1 - Mth.clamp((t - BARRAGE_AT) / 6, 0, 1) * .5f; curl = .2f; } }
                case GRAB, CONTROL -> { if (right) { up = 1; reach = 1; curl = action == CONTROL ? .78f : .3f; } }
                case THROW -> { if (right) { up = 1 - Mth.clamp(t / THROW_TICKS, 0, 1); reach = 1.2f; curl = 0; } }
                case FIST_SUMMON, FIST -> { up = right ? 1 : .5f; reach = right ? .8f : .2f; curl = 1;
                    if (right && s != null && s.punchAge >= 0) { float a = s.punchAge + e.getPartialTick(); up -= .7f * (a < PUNCH_DOWN ? a / PUNCH_DOWN : Math.max(0, 1 - (a - PUNCH_DOWN - PUNCH_HOLD) / PUNCH_BACK)); } }
                case SHIELD_RAISE, SHIELD -> { up = action == SHIELD ? .55f : Mth.clamp(t / SHIELD_RAISE_TICKS, 0, 1) * .7f; curl = .25f; }
                case BURST -> { up = 1 - Mth.clamp(t / BURST_TICKS, 0, 1); reach = 1; curl = 0; }
                default -> {}
            }
            if (up <= .01f && reach <= .01f) continue;
            float bob = .01f * Mth.sin(time * .1f);
            p.pushPose();
            p.translate(.45f * sx, -.95f + .55f * up + bob, -.75f - .2f * reach);
            p.mulPose(Axis.YP.rotationDegrees(-8 * sx));
            p.mulPose(Axis.XP.rotationDegrees(-70 - 20 * reach));
            p.mulPose(Axis.ZP.rotationDegrees(10 * sx));
            MagnetoBody.firstPersonArm(p, e.getMultiBufferSource(), e.getPackedLight(), side, -.2f, curl);
            p.popPose();
        }
    }

    private static void clear() { BITS.clear(); GRABS.clear(); FALLS.clear(); MOTES.clear(); RINGS.clear(); COLUMNS.clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); MagnetoLayer.clear(); }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                var renderer = e.getSkin(skin);
                if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) player.addLayer(new MagnetoLayer(player));
            }
        }
    }
}
