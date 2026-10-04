package com.FIRNI.superheromod.client.render.hulk;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.film.FilmSessionClient;
import com.FIRNI.superheromod.heroes.hulk.HulkConfig;
import com.FIRNI.superheromod.heroes.hulk.HulkRageSession;
import com.FIRNI.superheromod.network.packet.HulkFxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * Hulk's weight in the world: shock rings in the air and on the ground, the forward wall of the
 * Thunderclap, dust that rolls out and hangs, chunks of the real ground (the block he hit) that
 * fly, bounce and settle, cracks spreading from every landing, gamma light, the rock in flight,
 * the two bodies of GAMMA RAGE, his fists in first person and the camera's reaction. Every amount
 * scales with the client "effects" setting and every shake with the "camera shake" setting.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class HulkFx {
    static final int GAMMA = 0x5aff3a, GAMMA_CORE = 0xd8ffc0, AIR = 0xeef4ff, CRACK = 0x16120e;

    private record Puff(Vec3 at, Vec3 drift, double size, long start, float life, int rgb, float alpha) {}
    private record Glow(Vec3 at, double size, long start, float life, int rgb, float alpha) {}
    /** A shock ring; normal null = flat on the ground. */
    private record Ring(Vec3 at, Vec3 normal, double radius, double width, long start, float life, int rgb, float alpha, boolean light) {}
    /** The Thunderclap's wall of air, sweeping forward along the ground in an arc. */
    private record Arc(Vec3 at, Vec3 dir, double range, double spread, double height, long start, float life, BlockState ground) {}
    /** The charged punch's shock front, travelling from the fist to where it bursts. */
    /** A thrown boulder stuck in the ground where it hit. */
    private record Stuck(Vec3 at, BlockState state, long start, float yaw, float tilt) {}
    private static final List<Stuck> STUCK = new ArrayList<>();
    static final int STUCK_LIFE = 180;
    /** The boulder he tears up and throws: about as big as he can carry. */
    static final float BOULDER = 2.3f;
    /**
     * A boulder of this block: a big core block and lumps of the same stone round it, so it reads as one
     * rough rock rather than a cube. Centred on the pose's origin; size is its width in blocks.
     */
    static void boulder(PoseStack pose, MultiBufferSource b, BlockState state, int light, float size, int seed) {
        var blocks = Minecraft.getInstance().getBlockRenderer();
        float[][] lumps = {{0, 0, 0, .72f}, {.3f, .18f, .12f, .48f}, {-.28f, .1f, -.2f, .5f}, {.05f, -.3f, .25f, .46f},
                {-.15f, .28f, .3f, .42f}, {.22f, -.15f, -.32f, .44f}, {-.32f, -.22f, .05f, .4f}};
        for (int i = 0; i < lumps.length; i++) {
            float[] l = lumps[i];
            pose.pushPose();
            pose.translate(l[0] * size, l[1] * size, l[2] * size);
            pose.mulPose(Axis.YP.rotationDegrees((seed * 53 + i * 71) % 360));
            pose.mulPose(Axis.XP.rotationDegrees((seed * 29 + i * 47) % 40 - 20));
            float s = l[3] * size;
            pose.scale(s, s, s);
            pose.translate(-.5, -.5, -.5);
            blocks.renderSingleBlock(state, pose, b, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
    private record PunchWave(Vec3 from, Vec3 dir, double range, float power, long start) {}
    private record Crack(Vec3 centre, List<List<Vec3>> lines, long start, float life, boolean gamma) {}

    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final List<Glow> GLOWS = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final List<Arc> ARCS = new ArrayList<>();
    private static final List<Crack> CRACKS = new ArrayList<>();
    private static final List<PunchWave> WAVES = new ArrayList<>();
    private static float screenFlash;
    private static int flashColor = 0xFFFFFF;

    private HulkFx() {}

    private static long now() { var l = Minecraft.getInstance().level; return l == null ? 0 : l.getGameTime(); }
    private static Random random() { return new Random(System.nanoTime()); }
    private static float amount() { return (float) Math.max(0, Math.min(2, HulkConfig.get(HulkConfig.EFFECTS))); }
    private static int n(double count) { return (int) Math.round(count * amount()); }
    private static <T> void cap(List<T> list, int max) { while (list.size() > max) list.remove(0); }

    // ------------------------------------------------------------------ spawning
    private static void shake(Vec3 at, float strength, float range) {
        float s = (float) (double) HulkConfig.get(HulkConfig.SHAKE);
        if (s > 0) ClientScreenShake.addFromSource(at, strength * s, range);
    }
    private static void screenFlash(Vec3 at, float strength, double range, int rgb) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || amount() <= 0) return;
        double d = mc.player.position().distanceTo(at);
        if (d < range && strength * (float) (1 - d / range) > screenFlash) { screenFlash = strength * (float) (1 - d / range); flashColor = rgb; }
    }
    private static void glow(Vec3 at, double size, float life, int rgb, float alpha) {
        if (amount() <= 0) return;
        GLOWS.add(new Glow(at, size, now(), life, rgb, alpha)); cap(GLOWS, 64);
    }
    private static void ring(Vec3 at, Vec3 normal, double radius, double width, float life, int rgb, float alpha, boolean light, int delay) {
        if (amount() <= 0) return;
        RINGS.add(new Ring(at, normal, radius, width, now() + delay, life, rgb, alpha, light)); cap(RINGS, 72);
    }
    /** Dust from this ground: the block's own colour, greyed toward earth. */
    private static int dustColor(BlockState s) {
        var level = Minecraft.getInstance().level;
        int c = 0x8a7a66;
        if (level != null && !s.isAir()) {
            int m = s.getMapColor(level, BlockPos.ZERO).col;
            if (m != 0) c = m;
        }
        int r = (c >> 16 & 255), g = (c >> 8 & 255), b = c & 255;
        r = (r * 6 + 0x8a * 4) / 10; g = (g * 6 + 0x7e * 4) / 10; b = (b * 6 + 0x6c * 4) / 10;
        return r << 16 | g << 8 | b;
    }
    /** Rolling dust: puffs that drift out from the centre and hang a while. */
    private static void dust(Vec3 at, int count, double spread, double speed, double size, float life, BlockState ground, double lift) {
        Random r = random();
        int rgb = dustColor(ground);
        for (int i = 0; i < n(count); i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * spread;
            Vec3 from = at.add(Math.cos(a) * d, r.nextDouble() * .3, Math.sin(a) * d);
            Vec3 drift = new Vec3(Math.cos(a) * speed * (.6 + r.nextDouble() * .6), lift * (.4 + r.nextDouble() * .8), Math.sin(a) * speed * (.6 + r.nextDouble() * .6));
            PUFFS.add(new Puff(from, drift, size * (.7 + r.nextDouble() * .6), now(), life * (.7f + r.nextFloat() * .6f), rgb, .55f + r.nextFloat() * .25f));
        }
        cap(PUFFS, 600);
    }
    private static void particles(Vec3 at, int count, double spread, double speed, BlockState state) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Random r = random();
        if (state == null || state.isAir()) state = Blocks.DIRT.defaultBlockState();
        var block = new BlockParticleOption(ParticleTypes.BLOCK, state);
        for (int i = 0; i < n(count); i++)
            level.addParticle(block, at.x + (r.nextDouble() - .5) * spread, at.y + r.nextDouble() * .4, at.z + (r.nextDouble() - .5) * spread,
                    (r.nextDouble() - .5) * speed, r.nextDouble() * speed, (r.nextDouble() - .5) * speed);
    }
    private static void gammaMotes(Vec3 at, int count, double spread, double rise) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Random r = random();
        for (int i = 0; i < n(count); i++) {
            float bright = .6f + r.nextFloat() * .4f;
            level.addParticle(new DustParticleOptions(new Vector3f(.35f * bright, 1f * bright, .25f * bright), 1.1f + r.nextFloat() * .8f),
                    at.x + (r.nextDouble() - .5) * spread, at.y + r.nextDouble() * spread, at.z + (r.nextDouble() - .5) * spread,
                    0, rise * r.nextDouble(), 0);
        }
    }
    /** Cracks running out from a point along the ground, jagged and forking. */
    private static void cracks(Vec3 centre, double radius, int count, float life, boolean gamma, Vec3 along) {
        if (amount() <= 0) return;
        Random r = random();
        List<List<Vec3>> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle;
            if (along != null && along.horizontalDistanceSqr() > 1e-4) angle = Math.atan2(along.z, along.x) + (r.nextDouble() - .5) * .9;
            else angle = i * Math.PI * 2 / count + r.nextGaussian() * .25;
            double length = radius * (.6 + r.nextDouble() * .5);
            List<Vec3> line = new ArrayList<>();
            Vec3 p = centre;
            line.add(p);
            int steps = 5 + r.nextInt(3);
            for (int s = 1; s <= steps; s++) {
                angle += r.nextGaussian() * .35;
                p = p.add(Math.cos(angle) * length / steps, 0, Math.sin(angle) * length / steps);
                line.add(p);
                if (s == steps / 2 && r.nextFloat() < .6f) {
                    // A fork off the middle.
                    List<Vec3> fork = new ArrayList<>();
                    fork.add(p);
                    double fa = angle + (r.nextBoolean() ? .7 : -.7);
                    Vec3 q = p;
                    for (int k = 0; k < 3; k++) { fa += r.nextGaussian() * .3; q = q.add(Math.cos(fa) * length * .12, 0, Math.sin(fa) * length * .12); fork.add(q); }
                    lines.add(fork);
                }
            }
            lines.add(line);
        }
        CRACKS.add(new Crack(centre.add(0, .03, 0), lines, now(), life, gamma));
        cap(CRACKS, 16);
    }
    private static BlockState state(int id) { BlockState s = Block.stateById(id); return s.isAir() ? Blocks.DIRT.defaultBlockState() : s; }
    private static Vec3 flatDir(Vec3 d) { Vec3 f = new Vec3(d.x, 0, d.z); return f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize(); }

    // ------------------------------------------------------------------ what the server tells us
    public static void receive(HulkFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 at = p.pos(), dir = p.dir();
        float power = p.power();
        BlockState ground = state(p.block());
        switch (p.kind()) {
            case FX_TRANSFORM -> {
                if (power < 1.5f) {
                    // The first pulse: gamma flickering under the skin.
                    gammaMotes(at.add(0, 1, 0), 18, 1.2, .08);
                    glow(at.add(0, 1.2, 0), 2.2, 10, GAMMA, .45f);
                } else {
                    // The roar: the change is done; the ground jumps.
                    ring(at.add(0, .1, 0), null, 7, .9, 14, AIR, .55f, true, 0);
                    ring(at.add(0, .1, 0), null, 4.5, .6, 10, GAMMA, .5f, true, 2);
                    glow(at.add(0, 1.8, 0), 4.5, 12, GAMMA, .6f);
                    dust(at, 26, 1.5, .16, 1.3, 40, mc.level.getBlockState(BlockPos.containing(at).below()), .02);
                    gammaMotes(at.add(0, 1.2, 0), 40, 2.5, .15);
                    cracks(at, 3.2, 7, 120, true, null);
                    shake(at, .55f, 14);
                    screenFlash(at, .25f, 10, GAMMA);
                }
            }
            case FX_REVERT -> {
                // Steam off the shrinking body.
                var level = mc.level;
                Random r = random();
                for (int i = 0; i < n(24); i++)
                    level.addParticle(ParticleTypes.CLOUD, at.x + (r.nextDouble() - .5) * 1.2, at.y + .5 + r.nextDouble() * 1.8, at.z + (r.nextDouble() - .5) * 1.2, 0, .05, 0);
                glow(at.add(0, 1.2, 0), 1.6, 8, GAMMA, .25f);
            }
            case FX_PUNCH -> {
                // A jab bursting where it lands: a small explosion of air, grit and a ring.
                float k = Math.min(1.6f, power);
                ring(at, dir, .9 + .7 * k, .32, 6, AIR, .75f, true, 0);
                ring(at, dir, .5 + .4 * k, .2, 4, GAMMA_CORE, .4f * k, true, 0);
                glow(at, 1.6 * k, 4, AIR, .7f);
                var level = mc.level;
                Random r = random();
                if (k > 1.1f) level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 0, 0, 0);
                for (int i = 0; i < n(10); i++) level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, (r.nextDouble() - .5) * .8 + dir.x * .6, r.nextDouble() * .5, (r.nextDouble() - .5) * .8 + dir.z * .6);
                for (int i = 0; i < n(6); i++) level.addParticle(ParticleTypes.POOF, at.x, at.y, at.z, (r.nextDouble() - .5) * .3 + dir.x * .3, r.nextDouble() * .15, (r.nextDouble() - .5) * .3 + dir.z * .3);
                if (p.block() != 0) particles(at.add(0, -.5, 0), 10, .6, .25, ground);
                shake(at, .22f * k, 8);
            }
            case FX_BLOCK -> particles(at, 12, .9, .2, ground);
            case FX_WAVE_HIT -> {
                // Someone caught by a wave: a burst of air and dust off them as they are thrown.
                ring(at, dir, 1.2 + .7 * power, .4, 6, AIR, .7f, true, 0);
                glow(at, 1.5 * power, 4, AIR, .55f);
                dust(at.add(0, -.8, 0), 6, .6, .14, 1.1, 30, ground, .05);
                particles(at.add(0, -.9, 0), 12, .9, .3, ground);
                shake(at, .3f * power, 9);
            }
            case FX_PUNCH_WAVE -> {
                // The charged punch: a burst at the fist, then a shock front travelling out to where it will burst.
                double range = Math.max(1.5, p.block() / 10.0);
                ring(at, dir, 1.4 + 1.4 * power, .5, 6, AIR, .85f, true, 0);
                ring(at, dir, .8 + power, .35, 5, GAMMA_CORE, .55f * power, true, 0);
                glow(at, 2.4 + 1.6 * power, 6, AIR, .85f);
                glow(at, 1.4 + power, 7, GAMMA, .45f * power);
                if (amount() > 0) { WAVES.add(new PunchWave(at, dir.normalize(), range, power, now())); cap(WAVES, 6); }
                shake(at, .45f + .5f * power, 14);
                screenFlash(at, .1f * power, 6, AIR);
            }
            case FX_BLAST -> {
                // Where the charged punch's wave bursts: a dome of air and dust, rings over the ground, debris (the real
                // blocks are thrown by the server), smoke, a flash.
                float k = Math.max(.3f, power);
                ring(at, dir, 2.5 + 3 * k, .9, 9, AIR, .9f, true, 0);
                ring(at, new Vec3(0, 1, 0), 3 + 4 * k, 1, 12, AIR, .7f, true, 1);
                BlockPos below = BlockPos.containing(at);
                Vec3 floor = at;
                for (int i = 0; i < 5; i++) if (!mc.level.getBlockState(below.below(i + 1)).isAir()) { floor = new Vec3(at.x, below.getY() - i, at.z); break; }
                ring(floor.add(0, .1, 0), null, 4 + 6 * k, .7 + .5 * k, 12 + 6 * k, AIR, .7f, true, 0);
                ring(floor.add(0, .15, 0), null, 2.5 + 3 * k, .5, 9, GAMMA_CORE, .45f * k, true, 2);
                glow(at, 4 + 4 * k, 7, AIR, 1f);
                glow(at, 2.5 + 2 * k, 10, GAMMA, .5f * k);
                Random r = random();
                int rgb = dustColor(ground);
                // The dome: puffs blown out in every direction from the burst.
                for (int i = 0; i < n(26 + 30 * k); i++) {
                    Vec3 d = new Vec3(r.nextGaussian(), Math.abs(r.nextGaussian()) * .7 + .1, r.nextGaussian()).normalize();
                    PUFFS.add(new Puff(at.add(d.scale(.5)), d.scale(.35 + .3 * k), 1.4 + r.nextDouble() * 1.2 * k, now(), 34 + r.nextFloat() * 26, i % 3 == 0 ? 0xd9dde2 : rgb, .5f + r.nextFloat() * .2f));
                }
                dust(floor, (int) (14 + 20 * k), 1 + k, .2 + .2 * k, 1.6 + .6 * k, 60, ground, .04);
                particles(floor, (int) (30 + 50 * k), 2.5 + 2 * k, .55, ground);
                var level = mc.level;
                level.addParticle(k > .75f ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at.x, at.y, at.z, 0, 0, 0);
                for (int i = 0; i < n(6 * k); i++) level.addParticle(ParticleTypes.EXPLOSION, at.x + r.nextGaussian() * k, at.y + r.nextDouble() * k, at.z + r.nextGaussian() * k, 0, 0, 0);
                for (int i = 0; i < n(20 * k); i++) level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, r.nextGaussian() * .15, r.nextDouble() * .15, r.nextGaussian() * .15);
                cracks(floor, 3 + 3 * k, 8, 160, false, null);
                shake(at, .7f + .8f * k, 26);
                screenFlash(at, .25f * k, 14, AIR);
            }
            case FX_CHARGED_WAVE -> {}
            case FX_CLAP -> {
                // The clap: a flash between his hands at chest height, a burst of rings, then the wall of air
                // rolling forward across the ground (it moves exactly as fast as the server's wave).
                Vec3 f = flatDir(dir), hands = at.add(0, 1.75, 0);
                glow(hands, 4.5, 6, AIR, 1f);
                glow(hands, 2.4, 8, GAMMA_CORE, .55f);
                ring(hands, f, 2, .55, 6, AIR, .95f, true, 0);
                ring(hands.add(f.scale(2)), f, 3.6, .7, 8, AIR, .8f, true, 1);
                ring(hands.add(f.scale(4.5)), f, 5.4, .9, 10, AIR, .6f, true, 3);
                ring(at.add(0, .1, 0), null, 4.5, .7, 8, AIR, .6f, true, 0);
                if (amount() > 0) { ARCS.add(new Arc(at, f, power, Math.toRadians(CLAP_SPREAD), 4.6, now(), (float) (power / CLAP_SPEED) + 6, ground)); cap(ARCS, 8); }
                var level = mc.level;
                Random r = random();
                for (int i = 0; i < n(70); i++) {
                    double a = (r.nextDouble() - .5) * Math.toRadians(CLAP_SPREAD * 2);
                    Vec3 d = rotY(f, a);
                    Vec3 q = at.add(d.scale(1 + r.nextDouble() * 2)).add(0, r.nextDouble() * 2.6, 0);
                    Vec3 v = d.scale(1.1 + r.nextDouble() * 1.3);
                    level.addParticle(i % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.POOF, q.x, q.y, q.z, v.x, .02, v.z);
                }
                dust(at.add(f.scale(1.5)), 18, 1.5, .32, 1.7, 44, ground, .02);
                shake(at, 1.1f, 24);
                screenFlash(hands, .22f, 9, AIR);
            }
            case FX_POUND_STEP -> {
                // The ground wave: the real earth is thrown up by the server; here the dust, grit, rings and cracks.
                Vec3 f = flatDir(dir);
                if (power > 1.5f) {
                    ring(at.add(0, .1, 0), null, 8, 1, 12, AIR, .7f, true, 0);
                    ring(at.add(0, .15, 0), null, 4.5, .6, 8, AIR, .6f, true, 2);
                    ring(at.add(0, 1.2, 0), new Vec3(0, 1, 0), 3, .6, 6, GAMMA_CORE, .5f, true, 0);
                    glow(at.add(0, .5, 0), 4, 6, AIR, .8f);
                    dust(at, 30, 1.8, .28, 1.9, 64, ground, .05);
                    particles(at, 70, 3.5, .5, ground);
                    mc.level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y + .5, at.z, 0, 0, 0);
                    cracks(at, 6.5, 11, 170, false, null);
                    shake(at, 1.3f, 26);
                    screenFlash(at, .14f, 8, AIR);
                } else {
                    dust(at, 8, 1.3, .12, 1.5, 44, ground, .05);
                    // Dust left hanging along the split.
                    dust(at, 6, 2.4, .02, 1.8, 110, ground, .006);
                    particles(at, 30, 2.8, .4, ground);
                    ring(at.add(0, .1, 0), null, 2.6, .45, 7, AIR, .45f, true, 0);
                    cracks(at, 2.6, 3, 120, false, f);
                    shake(at, .45f, 15);
                }
                mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1f, .55f, false);
            }
            case FX_LANDING -> {
                // Landing: dust rolling out, rings, cracks - all by how hard he hit (the server throws the real ground).
                double s = Math.max(.3, power);
                ring(at.add(0, .1, 0), null, 3 + 7 * s, .6 + .5 * s, 9 + 7 * (float) s, AIR, .7f, true, 0);
                if (s > .5) {
                    ring(at.add(0, .15, 0), null, 2 + 3 * s, .45, 7, AIR, .55f, true, 2);
                    ring(at.add(0, 1, 0), new Vec3(0, 1, 0), 2 + 2 * s, .5, 6, GAMMA_CORE, .4f, true, 0);
                    mc.level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y + .4, at.z, 0, 0, 0);
                }
                glow(at.add(0, .4, 0), 2.5 + 2.5 * s, 5, AIR, .6f * (float) s);
                dust(at, (int) (16 + 34 * s), 1 + 1.6 * s, .18 + .26 * s, 1.3 + .8 * s, 44 + 24 * (float) s, ground, .03);
                particles(at, (int) (24 + 50 * s), 2.2 + 2 * s, .45, ground);
                cracks(at, 2 + 4 * s, 6 + (int) (5 * s), 150, false, null);
                shake(at, .4f + .9f * (float) s, 12 + 12 * (float) s);
            }
            case FX_ROCK_PULL -> {
                particles(at, 40, 1.8, .4, ground);
                dust(at, 12, .8, .08, 1.2, 30, ground, .05);
                cracks(at.add(0, -.5, 0), 2.6, 6, 110, false, null);
                shake(at, .4f, 10);
            }
            case FX_ROCK_HIT -> {
                // The boulder buries itself where it hit and stays there a while.
                Vec3 rest = at;
                BlockPos column = BlockPos.containing(at);
                for (int i = 0; i < 4; i++) if (!mc.level.getBlockState(column.below(i + 1)).isAir()) { rest = new Vec3(at.x, column.getY() - i, at.z); break; }
                Random sr = random();
                STUCK.add(new Stuck(rest, ground, now(), sr.nextFloat() * 360, .2f + sr.nextFloat() * .5f));
                cap(STUCK, 12);
                ring(at.add(0, .05, 0), null, 7, .9, 12, AIR, .7f, true, 0);
                ring(at, dir, 3, .6, 7, AIR, .6f, true, 0);
                glow(at, 3.2, 6, AIR, .8f);
                mc.level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 0, 0, 0);
                particles(at, 70, 3, .5, ground);
                dust(at, 28, 1.6, .22, 1.7, 54, ground, .04);
                cracks(at, 4, 8, 130, false, null);
                shake(at, .8f, 18);
            }
            case FX_ULT_CRASH -> {
                // The target hits the ground: a column of dust and a first ring.
                ring(at.add(0, .1, 0), null, 6, .7, 10, AIR, .6f, true, 0);
                glow(at.add(0, .5, 0), 3, 6, AIR, .7f);
                dust(at, 30, 1.5, .18, 1.3, 50, ground, .07);
                particles(at, 40, 2.5, .4, ground);
                cracks(at, 4, 8, 160, false, null);
                shake(at, .9f, 24);
                screenFlash(at, .2f, 16, AIR);
            }
            case FX_ULT_SMASH -> {
                // Both fists into the ground: everything at once, and big.
                for (int i = 0; i < 3; i++) ring(at.add(0, .1 + i * .05, 0), null, 9 + i * 5, 1 + i * .4, 12 + i * 6, i == 1 ? GAMMA : AIR, .7f - .15f * i, true, i * 2);
                ring(at.add(0, 1.2, 0), new Vec3(0, 1, 0), 3.5, .8, 6, GAMMA_CORE, .8f, true, 0);
                glow(at.add(0, 1, 0), 7, 10, AIR, .9f);
                glow(at.add(0, 1, 0), 5, 18, GAMMA, .6f);
                dust(at, 70, 3, .3, 2.2, 90, ground, .05);
                dust(at, 20, 1, .08, 2.8, 70, ground, .16);
                particles(at, 80, 5, .55, ground);
                cracks(at, 10, 12, 260, true, null);
                gammaMotes(at.add(0, .2, 0), 50, 4, .2);
                shake(at, 1.4f, 40);
                screenFlash(at, .65f, 30, 0xFFFFFF);
            }
            case FX_GUARD_HIT -> {
                ring(at, dir, .9 * power + .3, .25, 5, power > .6f ? GAMMA_CORE : AIR, .55f, true, 0);
                glow(at, 1.1 * power, 4, AIR, .45f);
                shake(at, .15f * power, 6);
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ every tick

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) { if (mc.level == null) clear(); return; }
        long now = now();
        var level = mc.level;
        PUFFS.removeIf(p -> now - p.start() > p.life());
        GLOWS.removeIf(g -> now - g.start() > g.life());
        RINGS.removeIf(r -> now - r.start() > r.life());
        ARCS.removeIf(a -> now - a.start() > a.life());
        Random dustRandom = random();
        WAVES.removeIf(w -> now - w.start() > w.range() / PUNCH_WAVE_SPEED + 8);
        for (Stuck k : STUCK) if (now - k.start() == STUCK_LIFE - 20) {
            particles(k.at().add(0, .4, 0), 40, 2.2, .3, k.state());
            dust(k.at(), 10, 1, .06, 1.2, 40, k.state(), .03);
        }
        STUCK.removeIf(k -> now - k.start() > STUCK_LIFE);
        // The punch's front tears dust off whatever it passes.
        for (PunchWave w : WAVES) {
            double d = Math.min(w.range(), PUNCH_WAVE_SPEED * (now - w.start()));
            if (d >= w.range()) continue;
            Vec3 q = w.from().add(w.dir().scale(d));
            for (int i = 0; i < n(5); i++) {
                Vec3 o = new Vec3(dustRandom.nextGaussian(), dustRandom.nextGaussian(), dustRandom.nextGaussian()).scale(.6 + .5 * w.power());
                level.addParticle(i % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.POOF, q.x + o.x, q.y + o.y, q.z + o.z, w.dir().x * 1.2, w.dir().y * 1.2, w.dir().z * 1.2);
            }
        }
        // The Thunderclap's front kicks up a curtain of dust and grit wherever it is passing.
        for (Arc a : ARCS) {
            double reach = Math.min(a.range(), 1.5 + CLAP_SPEED * (now - a.start()));
            if (reach >= a.range() - .1 && now - a.start() > a.range() / CLAP_SPEED + 1) continue;
            for (int i = 0; i < n(7); i++) {
                Vec3 d = rotY(a.dir(), (dustRandom.nextDouble() * 2 - 1) * a.spread());
                Vec3 q = a.at().add(d.scale(reach - dustRandom.nextDouble() * 1.2));
                BlockPos column = BlockPos.containing(q);
                BlockState under = level.getBlockState(column.below());
                if (under.isAir()) { under = level.getBlockState(column.below(2)); q = q.add(0, -1, 0); }
                if (under.isAir()) continue;
                PUFFS.add(new Puff(q.add(0, dustRandom.nextDouble() * .5, 0), d.scale(.16).add(0, .03, 0), 1.4 + dustRandom.nextDouble() * 1.1, now,
                        70 + dustRandom.nextFloat() * 50, dustColor(under), .42f + dustRandom.nextFloat() * .2f));
                if (i % 2 == 0) particles(q, 2, .8, .3, under);
            }
            cap(PUFFS, 600);
        }
        CRACKS.removeIf(c -> now - c.start() > c.life());
        screenFlash *= .8f;

        Random r = random();
        for (Player p : level.players()) {
            if (!HulkClient.isHero(p)) continue;
            HulkClient.State s = HulkClient.get(p);
            if (s == null) continue;
            float t = HulkClient.clock(s, 0);
            Vec3 at = p.position();
            switch (s.action) {
                case TRANSFORM -> {
                    float g = HulkMotion.clamp((t - 4) / (GROW_END - 4));
                    // Scraps of his shirt tearing off as he swells.
                    if (t > GROW_START + 3 && t < GROW_START + 18)
                        for (int i = 0; i < n(3); i++) {
                            double a = r.nextDouble() * Math.PI * 2;
                            level.addParticle(new DustParticleOptions(new Vector3f(.56f, .46f, .72f), 1.4f + r.nextFloat()), at.x + Math.cos(a) * .9, at.y + 1.2 + r.nextDouble() * 1.4, at.z + Math.sin(a) * .9,
                                    Math.cos(a) * .15, .05, Math.sin(a) * .15);
                        }
                    if (t < GROW_END + 4) gammaMotes(at.add(0, .6, 0), 2 + (int) (5 * g), 1 + g, .06 + .1 * g);
                    if (t >= GROW_START && t < GROW_END && r.nextFloat() < .4f)
                        level.playLocalSound(at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, .6f + g * .6f, .7f + g * .4f, false);
                }
                case PUNCH_CHARGE -> {
                    if (s.charge > .3f) {
                        Vec3 fist = p.getEyePosition().add(p.getLookAngle().scale(.6)).add(flatDir(p.getLookAngle()).cross(new Vec3(0, 1, 0)).scale(.55)).add(0, -.5, 0);
                        gammaMotes(fist, (int) (3 * s.charge), .5, .03);
                    }
                    if (s.charge >= .99f && t % 6 < 1) particles(at, 4, 1, .1, level.getBlockState(p.blockPosition().below()));
                }
                case LEAP_CHARGE -> {
                    if (s.charge > .5f && r.nextFloat() < s.charge) particles(at, 2, 1.4, .08, level.getBlockState(p.blockPosition().below()));
                    if (s.charge >= .99f && t % 8 < 1) dust(at, 2, .8, .03, .6, 16, level.getBlockState(p.blockPosition().below()), .02);
                }
                default -> {}
            }
            // The rock in flight trails grit.
            if (s.rockFlying()) {
                particles(s.rock, 2, .6, .05, s.rockState());
                if (r.nextFloat() < .5f) level.addParticle(ParticleTypes.POOF, s.rock.x, s.rock.y, s.rock.z, 0, 0, 0);
            }
        }
    }
    // ------------------------------------------------------------------ drawing: light and dust
    static void put(VertexConsumer v, Matrix4f m, Vec3 p, int rgb, float a) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, Math.max(0, Math.min(1, a))).endVertex();
    }
    static Vec3 perpendicular(Vec3 d) {
        Vec3 a = Math.abs(d.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return d.cross(a).normalize();
    }
    /** A ring standing in the plane across `normal`: a shock front travelling that way. */
    static void tiltedRing(FilmContext c, Vec3 centre, Vec3 normal, double radius, double width, int rgb, float alpha) {
        tiltedRing(c, centre, normal, radius, width, rgb, alpha, FilmFx.ADD);
    }
    static void tiltedRing(FilmContext c, Vec3 centre, Vec3 normal, double radius, double width, int rgb, float alpha, net.minecraft.client.renderer.RenderType type) {
        if (alpha <= .003f || radius <= 0) return;
        VertexConsumer v = c.buffers().getBuffer(type);
        Matrix4f m = c.pose().last().pose();
        Vec3 n = normal.normalize(), u = perpendicular(n), w = n.cross(u).normalize();
        int count = 36;
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
    /** A flat ribbon lying on the ground from a to b. */
    static void flat(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, double width, int rgb, float alpha) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        Vec3 side = d.cross(new Vec3(0, 1, 0)).normalize().scale(width / 2);
        put(v, m, a.add(side), rgb, alpha); put(v, m, b.add(side.scale(.6)), rgb, alpha);
        put(v, m, b.subtract(side.scale(.6)), rgb, alpha); put(v, m, a.subtract(side), rgb, alpha);
    }

    @SubscribeEvent public static void renderLight(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (PUFFS.isEmpty() && GLOWS.isEmpty() && RINGS.isEmpty() && ARCS.isEmpty() && WAVES.isEmpty() && CRACKS.isEmpty()) return;
        float partial = e.getPartialTick();
        float time = now() + partial;
        PoseStack p = e.getPoseStack();
        p.pushPose();
        Vec3 cam = e.getCamera().getPosition();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var buffers = FilmFx.batched();     // (one buffer per kind: no draw forced between a glow and a puff)
        var rotation = e.getCamera().rotation();
        var r = new Vector3f(1, 0, 0).rotate(rotation); var u = new Vector3f(0, 1, 0).rotate(rotation);
        FilmContext c = new FilmContext(p, buffers, cam, new Vec3(r.x, r.y, r.z), new Vec3(u.x, u.y, u.z), time, 0, partial);
        Matrix4f m = p.last().pose();
        try {
            // Cracks first, lying on the ground.
            for (Crack k : CRACKS) {
                float age = time - k.start();
                float grow = FilmFx.ease(age / 5f), fade = 1 - FilmFx.ease((age - (k.life() - 30)) / 30f);
                for (List<Vec3> line : k.lines()) {
                    int segs = line.size() - 1;
                    for (int i = 0; i < segs; i++) {
                        float show = HulkMotion.clamp(grow * segs - i);
                        if (show <= 0) break;
                        Vec3 a = line.get(i).add(0, .03, 0), b = a.lerp(line.get(i + 1).add(0, .03, 0), show);
                        double width = .28 * (1 - i / (double) (segs + 1));
                        flat(buffers.getBuffer(FilmFx.SOFT), m, a, b, width, CRACK, .8f * fade);
                        if (k.gamma()) flat(buffers.getBuffer(FilmFx.ADD), m, a.add(0, .01, 0), b.add(0, .01, 0), width * .5,
                                GAMMA, .7f * fade * (1 - HulkMotion.clamp(age / 80f)) + .1f * fade);
                    }
                }
            }
            for (Ring k : RINGS) {
                float age = (time - k.start()) / k.life();
                if (age < 0 || age > 1) continue;
                double grow = 1 - Math.pow(1 - age, 3);
                float alpha = k.alpha() * (1 - age) * (1 - age);
                double radius = k.radius() * grow, width = k.width() * (.6 + .8 * age);
                // Light alone vanishes in daylight: every shock ring also has a body of pressed, dusty air,
                // pale in the middle with a darker leading edge, so it reads against a bright sky too.
                if (k.normal() == null) {
                    FilmFx.ring(c, k.at(), radius, width, k.rgb(), alpha, k.light());
                    if (k.light()) {
                        FilmFx.ring(c, k.at().add(0, .05, 0), radius, width * 1.3, 0xd6dbe0, alpha * .5f, false);
                        FilmFx.ring(c, k.at().add(0, .06, 0), radius + width * 1.1, width * .35, 0x4a4f55, alpha * .35f, false);
                    }
                } else {
                    tiltedRing(c, k.at(), k.normal(), radius, width, k.rgb(), alpha);
                    tiltedRing(c, k.at(), k.normal(), radius, width * 1.35, 0xd8dde3, alpha * .45f, FilmFx.SOFT);
                    tiltedRing(c, k.at(), k.normal(), radius + width * 1.15, width * .35, 0x50555c, alpha * .3f, FilmFx.SOFT);
                }
            }
            for (Arc a : ARCS) drawArc(c, m, a, time);
            for (PunchWave w : WAVES) drawPunchWave(c, m, w, time);
            for (Glow g : GLOWS) {
                float age = (time - g.start()) / g.life();
                if (age < 0 || age > 1) continue;
                FilmFx.glow(c, g.at(), g.size() * (.8 + .4 * age), g.rgb(), g.alpha() * (1 - age) * (1 - age));
            }
            // Dust: puffs drifting out and slowing, swelling and thinning.
            for (Puff k : PUFFS) {
                float age = (time - k.start()) / k.life();
                if (age < 0 || age > 1) continue;
                double travel = (1 - Math.exp(-age * 4)) * k.life() * .25;
                Vec3 at = k.at().add(k.drift().scale(travel));
                float alpha = k.alpha() * FilmFx.ease(age / .08f) * (float) Math.pow(1 - age, 1.6);
                FilmFx.puff(c, at, k.size() * (.6 + 1.2 * Math.sqrt(age)), k.rgb(), alpha);
            }
            buffers.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /** The Thunderclap's wall: a tall curved sheet of air sweeping out along the ground, a bright seam where it meets the ground. */
    private static void drawArc(FilmContext c, Matrix4f m, Arc a, float time) {
        float age = time - a.start();
        if (age < 0 || age > a.life()) return;
        double reach = Math.min(a.range(), 1.5 + CLAP_SPEED * age);
        float fade = 1 - FilmFx.ease((float) ((age - a.range() / CLAP_SPEED) / 6f));
        if (fade <= 0) return;
        double h = a.height() * (.8 + .3 * Math.min(1, reach / a.range()));
        float alpha = .62f * fade;
        int count = 28;
        for (int layer = 0; layer < 3; layer++) {
            VertexConsumer v = c.buffers().getBuffer(FilmFx.ADD);
            double back = reach - layer * .9, lh = h * (1 - layer * .22);
            float la = alpha * (layer == 0 ? 1 : layer == 1 ? .5f : .25f);
            if (back <= .5) continue;
            for (int i = 0; i < count; i++) {
                double a0 = -a.spread() + 2 * a.spread() * i / count, a1 = -a.spread() + 2 * a.spread() * (i + 1) / count;
                Vec3 d0 = rotY(a.dir(), a0), d1 = rotY(a.dir(), a1);
                Vec3 p0 = a.at().add(d0.scale(back)), p1 = a.at().add(d1.scale(back));
                float e0 = edge(i, count), e1 = edge(i + 1, count);
                // Brightest in its lower third, fading out toward the top.
                put(v, m, p0, AIR, la * e0); put(v, m, p1, AIR, la * e1);
                put(v, m, p1.add(0, lh * .35, 0), AIR, la * e1 * .8f); put(v, m, p0.add(0, lh * .35, 0), AIR, la * e0 * .8f);
                put(v, m, p0.add(0, lh * .35, 0), AIR, la * e0 * .8f); put(v, m, p1.add(0, lh * .35, 0), AIR, la * e1 * .8f);
                put(v, m, p1.add(0, lh, 0), AIR, 0); put(v, m, p0.add(0, lh, 0), AIR, 0);
            }
        }
        // The body of the wall: churned dust in the ground's colour, thick at the bottom, so it is seen by day.
        VertexConsumer soft = c.buffers().getBuffer(FilmFx.SOFT);
        int dustRgb = dustColor(a.ground());
        for (int i = 0; i < count; i++) {
            double a0 = -a.spread() + 2 * a.spread() * i / count, a1 = -a.spread() + 2 * a.spread() * (i + 1) / count;
            Vec3 d0 = rotY(a.dir(), a0), d1 = rotY(a.dir(), a1);
            float e0 = edge(i, count), e1 = edge(i + 1, count);
            for (int layer = 0; layer < 2; layer++) {
                double back = reach - .4 - layer * 1.1;
                if (back <= .5) continue;
                double lh = h * (layer == 0 ? .75 : .5);
                float la = .42f * fade * (layer == 0 ? 1 : .55f);
                int rgb = layer == 0 ? 0xdde2e6 : dustRgb;
                Vec3 p0 = a.at().add(d0.scale(back)), p1 = a.at().add(d1.scale(back));
                put(soft, m, p0, rgb, la * e0); put(soft, m, p1, rgb, la * e1);
                put(soft, m, p1.add(0, lh, 0), rgb, 0); put(soft, m, p0.add(0, lh, 0), rgb, 0);
            }
        }
        // The seam on the ground and the wind lines streaming behind the front.
        VertexConsumer v = c.buffers().getBuffer(FilmFx.ADD);
        for (int i = 0; i < count; i++) {
            double a0 = -a.spread() + 2 * a.spread() * i / count, a1 = -a.spread() + 2 * a.spread() * (i + 1) / count;
            Vec3 p0 = a.at().add(rotY(a.dir(), a0).scale(reach)).add(0, .06, 0), p1 = a.at().add(rotY(a.dir(), a1).scale(reach)).add(0, .06, 0);
            flat(v, m, p0, p1, .7, GAMMA_CORE, .55f * fade * edge(i, count));
        }
        for (int i = 0; i < 14; i++) {
            double ang = (FilmFx.hash(i * 7.3 + a.start()) * 2 - 1) * a.spread();
            double y = .4 + FilmFx.hash(i * 3.1 + a.start()) * h * .7, len = 1.5 + 2 * FilmFx.hash(i * 5.7);
            Vec3 d = rotY(a.dir(), ang);
            Vec3 head = a.at().add(d.scale(reach - .3)).add(0, y, 0), tail = head.subtract(d.scale(len));
            FilmFx.streak(c, tail, head, .12, AIR, 0, .35f * fade, true);
        }
    }
    /** The charged punch's front: a cone of pressed air pushing out, with a bright core, until it bursts. */
    private static void drawPunchWave(FilmContext c, Matrix4f m, PunchWave w, float time) {
        float age = time - w.start();
        double d = PUNCH_WAVE_SPEED * age;
        if (age < 0 || d > w.range() + .5) return;
        double r = (1.1 + 1.3 * w.power()) * (.7 + .3 * Math.min(1, d / 4));
        Vec3 at = w.from().add(w.dir().scale(Math.min(d, w.range())));
        for (int i = 0; i < 3; i++) {
            Vec3 q = at.subtract(w.dir().scale(i * .9));
            float a = (.9f - i * .28f);
            tiltedRing(c, q, w.dir(), r * (1 - i * .18), .45, AIR, a * .8f);
            tiltedRing(c, q, w.dir(), r * (1 - i * .18), .6, 0xdfe3e8, a * .45f, FilmFx.SOFT);
        }
        tiltedRing(c, at.add(w.dir().scale(.3)), w.dir(), r * 1.15, .18, 0x4d5259, .35f, FilmFx.SOFT);
        FilmFx.glow(c, at, r * 1.2, AIR, .55f);
        FilmFx.glow(c, at, r * .6, GAMMA_CORE, .35f * w.power());
        FilmFx.streak(c, w.from(), at, .5 + .4 * w.power(), AIR, 0, .5f, true);
        for (int i = 0; i < 10; i++) {
            double ang = i * Math.PI * 2 / 10 + w.start();
            Vec3 off = perpendicular(w.dir()).scale(Math.cos(ang) * r * .8).add(w.dir().cross(perpendicular(w.dir())).scale(Math.sin(ang) * r * .8));
            FilmFx.streak(c, at.add(off).subtract(w.dir().scale(2.5)), at.add(off), .1, AIR, 0, .4f, true);
        }
    }
    private static float edge(int i, int count) { float x = Math.abs(i / (float) count * 2 - 1); return 1 - x * x; }
    private static Vec3 rotY(Vec3 d, double a) { return new Vec3(d.x * Math.cos(a) - d.z * Math.sin(a), 0, d.x * Math.sin(a) + d.z * Math.cos(a)); }

    // ------------------------------------------------------------------ drawing: solid things
    @SubscribeEvent public static void renderSolid(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float partial = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var blocks = mc.getBlockRenderer();
        boolean any = false;
        float time = now() + partial;
        // Thrown boulders stuck where they hit, half buried; at the end they sink and crumble.
        for (Stuck k : STUCK) {
            float age = time - k.start();
            float sink = HulkMotion.clamp((age - (STUCK_LIFE - 24)) / 24f);
            pose.pushPose();
            pose.translate(k.at().x - cam.x, k.at().y - cam.y - .55 - sink * 1.4, k.at().z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(k.yaw()));
            pose.mulPose(Axis.XP.rotation(k.tilt()));
            boulder(pose, buffers, k.state(), LevelRenderer.getLightColor(mc.level, BlockPos.containing(k.at().add(0, 1, 0))), BOULDER * (1 - sink * .35f), (int) k.start());
            pose.popPose();
            any = true;
        }
        for (Player p : mc.level.players()) {
            if (!HulkClient.isHero(p)) continue;
            HulkClient.State s = HulkClient.get(p);
            // The rock in flight, tumbling end over end.
            if (s != null && s.rockFlying()) {
                Vec3 at = s.rockPrev.lerp(s.rock, partial);
                pose.pushPose();
                pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
                pose.mulPose(Axis.YP.rotationDegrees(p.getId() * 37 % 360));
                pose.mulPose(Axis.XP.rotation(time * .3f));
                boulder(pose, buffers, s.rockState(), LevelRenderer.getLightColor(mc.level, BlockPos.containing(at)), BOULDER, p.getId());
                pose.popPose();
                any = true;
            }
        }
        if (any) buffers.endBatch();
    }

    // ------------------------------------------------------------------ first person
    /** Hulk's fists at the bottom of the view; Banner keeps the ordinary hand (his own skin arm). */
    @SubscribeEvent public static void hand(RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !HulkClient.isHero(mc.player) || FilmDirector.playing()) return;
        HulkClient.State s = HulkClient.get(mc.player);
        if (s == null) return;
        boolean changing = s.action == TRANSFORM || s.action == REVERT;
        if (!s.hulk() && !changing) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        float t = HulkClient.clock(s, e.getPartialTick());
        float time = mc.player.tickCount + e.getPartialTick();
        float k = 1;
        if (s.action == TRANSFORM) k = FilmFx.ease((t - GROW_START) / (GROW_END - GROW_START));
        if (s.action == REVERT) k = 1 - FilmFx.ease(t / (REVERT_TICKS - 6f));
        PoseStack p = e.getPoseStack();
        var b = e.getMultiBufferSource();
        int light = e.getPackedLight();
        float walk = mc.player.walkDist + (mc.player.walkDist - mc.player.walkDistO) * e.getPartialTick();
        float bob = (float) Math.sin(walk * Math.PI) * .03f * Math.min(1, (float) mc.player.getDeltaMovement().horizontalDistance() * 8);
        boolean carrying = s.holdingRock() && s.action != ROCK;
        boolean rockHeld = carrying || s.action == ROCK && t >= ROCK_GRAB - 1 && t < ROCK_THROW;
        for (int side = -1; side <= 1; side += 2) {
            boolean right = side > 0;
            p.pushPose();
            p.translate(side * .52, -.62 + (right ? bob : -bob), -.72);
            p.mulPose(Axis.YP.rotationDegrees(-side * 8));
            p.mulPose(Axis.XP.rotationDegrees(8));
            if (carrying) {
                // Both forearms up holding the rock over his head.
                p.translate(-side * .18, .55, 0);
                p.mulPose(Axis.XP.rotationDegrees(-60));
            } else switch (s.action) {
                case PUNCH_RIGHT, PUNCH_LEFT -> {
                    boolean mine = right == (s.action == PUNCH_RIGHT);
                    float hit = HulkMotion.snap(t, 1, PUNCH_HIT) * (1 - HulkMotion.k(t, PUNCH_HIT + 1, PUNCH_TICKS));
                    if (mine) { p.translate(-side * .36 * hit, .14 * hit, -.55 * hit); p.mulPose(Axis.YP.rotationDegrees(side * 18 * hit)); }
                    else p.translate(0, -.06 * hit, .1 * hit);
                }
                case PUNCH_CHARGE -> {
                    float c = Math.max(s.charge, HulkMotion.k(t, 0, 6) * .3f);
                    if (right) {
                        p.translate(.08 * c + Math.sin(time * 3.3) * .012 * c, -.12 * c, .3 * c);
                        p.mulPose(Axis.XP.rotationDegrees(-25 * c));
                    } else p.translate(-.05 * c, .05 * c, -.05 * c);
                }
                case PUNCH_RELEASE -> {
                    float hit = HulkMotion.snap(t, 0, RELEASE_HIT) * (1 - HulkMotion.k(t, RELEASE_HIT + 4, RELEASE_TICKS));
                    if (right) { p.translate(-.42 * hit, .16 * hit, -.9 * hit); p.mulPose(Axis.YP.rotationDegrees(22 * hit)); }
                    else p.translate(0, -.12 * hit, .15 * hit);
                }
                case GUARD -> {
                    float up = HulkMotion.k(t, 0, 4);
                    p.translate(-side * .32 * up, .32 * up, .05 * up);
                    p.mulPose(Axis.ZP.rotationDegrees(side * 38 * up));
                    p.mulPose(Axis.XP.rotationDegrees(-30 * up));
                }
                case THUNDERCLAP -> {
                    float open = HulkMotion.k(t, 0, CLAP_HIT - 4), clap = HulkMotion.snap(t, CLAP_HIT - 4, CLAP_HIT), back = HulkMotion.k(t, CLAP_HIT + 6, CLAP_TICKS);
                    float apart = open * (1 - clap);
                    p.translate((side * .3 * apart - side * .47 * clap) * (1 - back), (.25 * open) * (1 - back), -.15 * clap * (1 - back));
                    p.mulPose(Axis.YP.rotationDegrees(side * (-30 * apart + 60 * clap) * (1 - back)));
                }
                case POUND -> {
                    float up = HulkMotion.k(t, 0, POUND_HIT - 3), slam = HulkMotion.snap(t, POUND_HIT - 3, POUND_HIT), back = HulkMotion.k(t, POUND_HIT + 6, POUND_TICKS);
                    p.translate(-side * .2 * up * (1 - back), (.55 * up - 1.1 * slam) * (1 - back), -.25 * slam * (1 - back));
                    p.mulPose(Axis.XP.rotationDegrees((-50 * up + 80 * slam) * (1 - back)));
                }
                case ROCK -> {
                    float lift = HulkMotion.k(t, ROCK_GRAB, ROCK_LIFT), down = HulkMotion.k(t, 0, ROCK_GRAB - 2) * (1 - lift);
                    float hurl = HulkMotion.snap(t, ROCK_LIFT + 2, ROCK_THROW), back = HulkMotion.k(t, ROCK_THROW + 2, ROCK_TICKS);
                    p.translate(-side * .18 * lift * (1 - back), (-.4 * down + .55 * lift - .5 * hurl) * (1 - back), -.45 * hurl * (1 - back));
                    p.mulPose(Axis.XP.rotationDegrees((-60 * lift + 70 * hurl) * (1 - back)));
                }
                case LEAP_CHARGE -> p.translate(0, -.12 * s.charge, .05 * s.charge);
                case LEAP -> { float up = HulkMotion.k(t, 0, 4); p.translate(side * .1 * up, .15 * up, 0); }
                case LANDING -> { float hit = 1 - HulkMotion.k(t, 0, LANDING_TICKS); p.translate(0, -.25 * hit, 0); }
                case TRANSFORM -> {
                    // Clutching at the head, then the arms swell and drop.
                    float clutch = HulkMotion.k(t, 0, 7) * (1 - HulkMotion.k(t, GROW_START + 6, GROW_START + 14));
                    p.translate(-side * .2 * clutch, .45 * clutch, .1 * clutch);
                    p.translate(Math.sin(time * 3.7 + side) * .01 * k, Math.sin(time * 4.1) * .01 * k, 0);
                }
                default -> {}
            }
            fist(p, b, light, k, side, time);
            p.popPose();
        }
        if (rockHeld) {
            float lift = carrying ? 1 : HulkMotion.k(t, ROCK_GRAB, ROCK_LIFT), hurl = carrying ? 0 : HulkMotion.snap(t, ROCK_LIFT + 2, ROCK_THROW);
            p.pushPose();
            p.translate(0, -.3 + 1.1 * lift - .5 * hurl, -1.5 - .4 * hurl);
            boulder(p, b, s.rockState(), light, 1.6f, mc.player.getId());
            p.popPose();
        }
    }
    /** One forearm and fist, seen from behind: green and enormous as Hulk, Banner's sleeve and hand on the way in or out. */
    private static void fist(PoseStack p, MultiBufferSource b, int light, float k, int side, float time) {
        float[] skin = mix(HulkLayer.SKIN, HulkLayer.GREEN, k), dark = mix(new float[]{.73f, .56f, .44f}, HulkLayer.GREEN_DARK, k), lit = mix(HulkLayer.SKIN, HulkLayer.GREEN_LIGHT, k);
        float w = 3.6f + 3.6f * k, f = 4 + 4.4f * k;
        // Forearm running back toward the camera, the fist out in front.
        HulkLayer.box(p, b, light, -w / 2, -w / 2, 0, w, w, 16, skin);
        HulkLayer.box(p, b, light, -w / 2 - .3f * k, w / 2 - .6f, 2, w + .6f * k, 1.2f * k, 8, lit);
        if (k < .6f) HulkLayer.box(p, b, light, -w / 2 - .25f, -w / 2 - .25f, 9, w + .5f, w + .5f, 8, HulkLayer.SHIRT);
        HulkLayer.box(p, b, light, -f / 2, -f / 2, -f + .5f, f, f, f, dark);
        for (int i = 0; i < 4; i++) HulkLayer.box(p, b, light, -f / 2 + f * (i + .15f) / 4, -f / 2 + f * .55f, -f + .1f, f * .7f / 4, f * .3f, .6f, lit);
    }
    private static float[] mix(float[] a, float[] b, float k) { return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k}; }

    // ------------------------------------------------------------------ the camera's reaction
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post e) {
        if (screenFlash < .01f) return;
        var g = e.getGuiGraphics();
        g.fill(0, 0, e.getWindow().getGuiScaledWidth(), e.getWindow().getGuiScaledHeight(), HudStyle.alpha(0xFF000000 | flashColor, Math.min(.7f, screenFlash)));
    }

    private static void clear() {
        WAVES.clear(); STUCK.clear(); PUFFS.clear(); GLOWS.clear(); RINGS.clear(); ARCS.clear(); CRACKS.clear(); screenFlash = 0;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                var renderer = e.getSkin(skin);
                if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) player.addLayer(new HulkLayer(player));
            }
        }
    }
}
