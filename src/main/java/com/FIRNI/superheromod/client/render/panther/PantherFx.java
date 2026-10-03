package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.heroes.panther.PantherConfig;
import com.FIRNI.superheromod.network.packet.PantherFxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * Black Panther's effects. Deliberate and short-lived, each one saying what happened: four thin trails
 * from the four claws of a striking hand (eight for the double claw); a gray-white air streak behind a
 * kicking foot; faint violet afterimages behind fast moves; a flash, a small kinetic ring and a puff of
 * dust where a blow lands; a thrown body tumbling, then scraping along the ground with debris of whatever
 * it slides on (dirt, stone, sand, wood, snow) and a scraped trail behind it; the suit soaking up a hit
 * (light running in along its lines); the kinetic sphere of the release with its Wakandan lattice, the
 * ground ring, cracks and dust, and violet streaks behind everyone it throws. Plus his arms and claws in
 * first person, with their own trails.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class PantherFx {
    static final int VIOLET = 0x9a5cff, VIOLET_DEEP = 0x5a24c8, BLUE = 0x6f8cff, WHITE = 0xffffff, SILVER = 0xd8dcf0, AIR = 0xd6d9e2;
    private record ClawSample(float time, Vec3[] tips, float power) {}
    private record KickSample(float time, Vec3 right, Vec3 left, float power) {}
    private record Ghost(Vec3 pos, float yaw, PantherMotion.Pose pose, float headYaw, float headPitch, float time, float anim) {}
    private record Burst(int kind, Vec3 pos, Vec3 dir, float power, int entity, float start) {}
    private static final class Slider { final int entity; final float start; Vec3 last; final float speed; int quiet; Slider(int e, float s, Vec3 at, float sp) { entity = e; start = s; last = at; speed = sp; } }
    private record Mark(Vec3 a, Vec3 b, double width, int rgb, float start) {}
    private static final class Tumble { final Vec3 axis; final float start, amount; float landed = -1; Tumble(Vec3 a, float s, float m) { axis = a; start = s; amount = m; } }
    private static final class Mote { Vec3 pos, vel; final double size, grow; final int rgb; final float life, start; final boolean light, heavy; final float alpha;
        Mote(Vec3 p, Vec3 v, double size, double grow, int rgb, float life, float start, boolean light, boolean heavy, float alpha) {
            pos = p; vel = v; this.size = size; this.grow = grow; this.rgb = rgb; this.life = life; this.start = start; this.light = light; this.heavy = heavy; this.alpha = alpha; } }
    private static final class Streamer { final ArrayDeque<Vec3> points = new ArrayDeque<>(); final float start; Streamer(float s) { start = s; } }

    private static final Map<Integer, ArrayDeque<ClawSample>> CLAWS = new HashMap<>();
    private static final Map<Integer, ArrayDeque<KickSample>> KICKS = new HashMap<>();
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final Map<Integer, Slider> SLIDERS = new HashMap<>();
    private static final List<Mark> MARKS = new ArrayList<>();
    private static final Map<Integer, Tumble> TUMBLES = new HashMap<>();
    private static final List<Mote> MOTES = new ArrayList<>();
    private static final Map<Integer, Streamer> STREAMERS = new HashMap<>();
    private static final Set<Integer> TUMBLING_NOW = new HashSet<>();
    private static final float CLAW_WINDOW = 2.4f, KICK_WINDOW = 3.2f, GHOST_LIFE = 3.2f;

    private PantherFx() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }
    private static Random random() { return RANDOM; }
    private static final Random RANDOM = new Random();
    private static float amount() { return PantherConfig.EFFECTS.get().floatValue(); }

    // ------------------------------------------------------------------ what the layer reports each frame
    static void claws(int id, Vec3[] tips, float time, float power) {
        for (Vec3 v : tips) if (v == null) return;
        ArrayDeque<ClawSample> q = CLAWS.computeIfAbsent(id, k -> new ArrayDeque<>());
        ClawSample last = q.peekLast();
        if (last != null && time - last.time < .03f) q.pollLast();
        if (last != null && last.tips[0].distanceTo(tips[0]) > 5) q.clear();
        q.addLast(new ClawSample(time, tips.clone(), power));
        while (q.size() > 40 || !q.isEmpty() && time - q.peekFirst().time > CLAW_WINDOW) q.pollFirst();
    }
    static void kicks(int id, Vec3 right, Vec3 left, float time, float power) {
        if (right == null || left == null) return;
        ArrayDeque<KickSample> q = KICKS.computeIfAbsent(id, k -> new ArrayDeque<>());
        KickSample last = q.peekLast();
        if (last != null && time - last.time < .03f) q.pollLast();
        if (last != null && last.right.distanceTo(right) > 6) q.clear();
        q.addLast(new KickSample(time, right, left, power));
        while (q.size() > 50 || !q.isEmpty() && time - q.peekFirst().time > KICK_WINDOW) q.pollFirst();
    }
    static void ghost(int id, Vec3 pos, float yaw, PantherMotion.Pose pose, float headYaw, float headPitch, float time, float anim) {
        if (!PantherConfig.AFTERIMAGES.get()) return;
        GHOSTS.add(new Ghost(pos, yaw, pose.copy(), headYaw, headPitch, time, anim));
        while (GHOSTS.size() > 48) GHOSTS.remove(0);
    }

    // ------------------------------------------------------------------ what the server reports
    public static void receive(PantherFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = now();
        Vec3 at = p.pos(), dir = p.dir();
        switch (p.kind()) {
            case FX_CLAW_HIT -> {
                BURSTS.add(new Burst(p.kind(), at, dir, p.power(), p.entity(), t));
                boolean heavy = Math.abs(p.power()) >= 2;
                PantherClient.struck(at, heavy ? 1.6f : Math.abs(p.power()) > 1.2f ? .7f : 1.1f, heavy ? .07f : .035f);
                sparks(at, dir, heavy ? 7 : 4, SILVER);
            }
            case FX_UPPER -> {
                BURSTS.add(new Burst(p.kind(), at, dir, p.power(), p.entity(), t));
                PantherClient.struck(at, 2f, .13f);
                sparks(at, new Vec3(0, 1, 0), 8, SILVER);
                Entity e = mc.level.getEntity(p.entity());
                if (e != null) { dust(e.position(), 10, .5, .9f); tumble(p.entity(), dir, .35f); }
            }
            case FX_POUNCE -> {
                BURSTS.add(new Burst(p.kind(), at, dir, p.power(), p.entity(), t));
                dust(at, 12, .7, 1f);
                for (int i = 0; i < 8; i++) {
                    double a = random().nextDouble() * Math.PI * 2;
                    Vec3 v = dir.scale(-.25 - random().nextDouble() * .2).add(Math.cos(a) * .08, .05 + random().nextDouble() * .1, Math.sin(a) * .08);
                    MOTES.add(new Mote(at.add(0, .1, 0), v, .35, .06, surfaceColour(at), 22, t, false, false, .55f));
                }
            }
            case FX_CONTACT -> {
                BURSTS.add(new Burst(p.kind(), at, dir, p.power(), p.entity(), t));
                PantherClient.struck(at, CONTACT_HOLD, .1f);
                dust(at.add(0, -1, 0), 6, .4, .6f);
            }
            case FX_KICK -> {
                BURSTS.add(new Burst(p.kind(), at, dir, p.power(), p.entity(), t));
                PantherClient.shake(near(at, 16) ? .28f : .1f);
                sparks(at, dir, 10, WHITE);
                tumble(p.entity(), dir, .9f);
                stream(p.entity(), t);
            }
            case FX_LAUNCH -> {
                BURSTS.add(new Burst(p.kind(), at, dir, p.power(), p.entity(), t));
                tumble(p.entity(), dir, .45f + .4f * p.power());
                stream(p.entity(), t);
            }
            case FX_SCRAPE -> {
                Entity e = mc.level.getEntity(p.entity());
                if (e != null) SLIDERS.put(p.entity(), new Slider(p.entity(), t, e.position(), p.power()));
                BURSTS.add(new Burst(p.kind(), at, dir, p.power(), p.entity(), t));
                dust(at, (int) (14 * amount()), .9, 1.2f);
                Tumble tu = TUMBLES.get(p.entity());
                if (tu != null && tu.landed < 0) tu.landed = t;
            }
            case FX_SPIN_KICK -> {
                BURSTS.add(new Burst(p.kind(), at, dir, p.power(), p.entity(), t));
                if (p.entity() >= 0) {
                    boolean last = p.power() >= 2;
                    PantherClient.shake(last ? .17f : .06f);
                    sparks(at, dir, last ? 9 : 4, WHITE);
                    if (last) { tumble(p.entity(), dir, .6f); stream(p.entity(), t); }
                }
            }
            case FX_RELEASE -> {
                BURSTS.add(new Burst(p.kind(), at, dir, p.power(), p.entity(), t));
                float k = p.power();
                double radius = Math.max(2, dir.x);
                if (mc.player != null) {
                    double d = mc.player.position().distanceTo(at);
                    if (d < radius * 2.5) PantherClient.shake((.25f + .55f * k) * (float) Math.max(.3, 1 - d / (radius * 2.5)));
                    if (p.entity() == mc.player.getId()) PantherClient.fovKick = Math.max(PantherClient.fovKick, 1.4f);
                }
                // The ground round him: torn up as dust and grit in a ring.
                int n = (int) ((20 + 50 * k) * amount());
                for (int i = 0; i < n; i++) {
                    double a = random().nextDouble() * Math.PI * 2, r = random().nextDouble() * radius * .5;
                    Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 pos = at.add(o.scale(r + .5)).add(0, .1, 0);
                    MOTES.add(new Mote(pos, o.scale(.25 + .5 * k + random().nextDouble() * .3).add(0, .06 + random().nextDouble() * .12, 0),
                            .5 + random().nextDouble() * .6 * (1 + k), .05, surfaceColour(at), 26 + random().nextInt(14), t, false, false, .6f));
                }
                blocks(at, (int) ((12 + 30 * k) * amount()), radius * .45, .45 + .3 * k);
            }
            case FX_ABSORB -> {
                Entity e = mc.level.getEntity(p.entity());
                if (e == null) return;
                float yaw = e.getYRot() * Mth.DEG_TO_RAD;
                double side = dir.x * -Mth.cos(yaw) + dir.z * -Mth.sin(yaw);
                int s = Math.abs(side) < .3 ? 0 : side > 0 ? -1 : 1;     // -1: from his right
                float order = Mth.lerp(Mth.clamp(p.power(), 0, 1), .05f, .64f);
                PantherClient.absorb(p.entity(), s, order);
                Vec3 hit = e.position().add(0, e.getBbHeight() * Mth.clamp(p.power(), .15f, .9f), 0).add(dir.multiply(1, 0, 1).scale(.35));
                BURSTS.add(new Burst(p.kind(), hit, dir, p.power(), p.entity(), t));
            }
            case FX_DODGE, FX_REFLEX, FX_LAND -> BURSTS.add(new Burst(p.kind(), at, dir, p.power(), p.entity(), t));
            default -> {}
        }
        while (BURSTS.size() > 80) BURSTS.remove(0);
    }
    private static boolean near(Vec3 at, double range) { var me = Minecraft.getInstance().player; return me != null && me.position().distanceTo(at) < range; }
    private static void tumble(int entity, Vec3 dir, float amount) {
        if (entity < 0) return;
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(1, 0, 0);
        flat = flat.normalize();
        // Turning about the horizontal line across the blow: the feet swept out from under them.
        TUMBLES.put(entity, new Tumble(new Vec3(flat.z, 0, -flat.x), now(), amount));
    }
    private static void stream(int entity, float t) { if (entity >= 0) STREAMERS.put(entity, new Streamer(t)); }
    private static void sparks(Vec3 at, Vec3 dir, int n, int rgb) {
        float t = now();
        Vec3 d = dir.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : dir.normalize();
        for (int i = 0; i < n; i++) {
            Vec3 v = d.scale(.15 + random().nextDouble() * .25).add((random().nextDouble() - .5) * .35, (random().nextDouble() - .2) * .3, (random().nextDouble() - .5) * .35);
            MOTES.add(new Mote(at, v, .05, 0, rgb, 4 + random().nextInt(4), t, true, true, .9f));
        }
    }
    /** A puff of the ground's own dust at a spot. */
    private static void dust(Vec3 at, int n, double spread, float size) {
        float t = now();
        int rgb = surfaceColour(at);
        for (int i = 0; i < n; i++) {
            double a = random().nextDouble() * Math.PI * 2;
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            MOTES.add(new Mote(at.add(o.scale(random().nextDouble() * spread * .4)).add(0, .1, 0), o.scale(.05 + random().nextDouble() * .08 * spread).add(0, .03 + random().nextDouble() * .05, 0),
                    .3 * size + random().nextDouble() * .3 * size, .04 * size, rgb, 18 + random().nextInt(12), t, false, false, .5f));
        }
    }
    /** Grit of the real ground thrown up. */
    private static void blocks(Vec3 at, int n, double spread, double up) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (int i = 0; i < n; i++) {
            double a = random().nextDouble() * Math.PI * 2, r = random().nextDouble() * spread;
            Vec3 pos = at.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            BlockState state = level.getBlockState(BlockPos.containing(pos).below());
            if (state.isAir()) continue;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y + .1, pos.z, Math.cos(a) * .15, up * random().nextDouble(), Math.sin(a) * .15);
        }
    }

    // ------------------------------------------------------------------ surfaces
    private enum Surface { DIRT, STONE, SAND, WOOD, SNOW, OTHER }
    private static Surface surface(BlockState s) {
        if (s.isAir()) return Surface.OTHER;
        SoundType type = s.getSoundType();
        if (s.is(BlockTags.SAND) || type == SoundType.SAND || type == SoundType.GRAVEL) return Surface.SAND;
        if (type == SoundType.SNOW || type == SoundType.POWDER_SNOW) return Surface.SNOW;
        if (s.is(BlockTags.DIRT) || type == SoundType.GRASS || type == SoundType.ROOTED_DIRT) return Surface.DIRT;
        if (s.is(BlockTags.PLANKS) || s.is(BlockTags.LOGS) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_STAIRS) || type == SoundType.WOOD) return Surface.WOOD;
        if (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.STONE_BRICKS)
                || type == SoundType.STONE || type == SoundType.DEEPSLATE || type == SoundType.NETHERRACK || type == SoundType.BASALT || type == SoundType.TUFF) return Surface.STONE;
        return Surface.OTHER;
    }
    private static BlockState under(Vec3 at) {
        var level = Minecraft.getInstance().level;
        if (level == null) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockPos pos = BlockPos.containing(at.add(0, -.05, 0));
        BlockState s = level.getBlockState(pos);
        return s.isAir() ? level.getBlockState(pos.below()) : s;
    }
    private static int surfaceColour(Vec3 at) {
        return switch (surface(under(at))) {
            case DIRT -> 0x7a5d3e; case STONE -> 0x8c8c8e; case SAND -> 0xd9c58e; case WOOD -> 0xa88a62; case SNOW -> 0xf2f4f8; default -> 0x9a948a;
        };
    }

    // ------------------------------------------------------------------ every tick: the slides, the dust, the streams
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        float t = mc.level.getGameTime();
        for (Iterator<Slider> it = SLIDERS.values().iterator(); it.hasNext(); ) {
            Slider sl = it.next();
            Entity ent = mc.level.getEntity(sl.entity);
            if (ent == null || t - sl.start > 50) { it.remove(); continue; }
            Vec3 pos = ent.position();
            Vec3 moved = pos.subtract(sl.last);
            double speed = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
            if (speed < .04 || !ent.onGround()) { if (++sl.quiet > 4) it.remove(); sl.last = pos; continue; }
            sl.quiet = 0;
            scrape(ent, sl.last, pos, speed, t);
            sl.last = pos;
        }
        MARKS.removeIf(m -> t - m.start > 90);
        while (MARKS.size() > 300) MARKS.remove(0);
        for (Iterator<Mote> it = MOTES.iterator(); it.hasNext(); ) {
            Mote m = it.next();
            if (t - m.start > m.life) { it.remove(); continue; }
            m.pos = m.pos.add(m.vel);
            m.vel = m.heavy ? m.vel.multiply(.9, 1, .9).add(0, -.04, 0) : m.vel.multiply(.88, .9, .88).add(0, .004, 0);
        }
        while (MOTES.size() > 700) MOTES.remove(0);
        for (Iterator<Map.Entry<Integer, Streamer>> it = STREAMERS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Entity ent = mc.level.getEntity(entry.getKey());
            if (ent == null || t - entry.getValue().start > 18) { it.remove(); continue; }
            var pts = entry.getValue().points;
            pts.addLast(ent.position().add(0, ent.getBbHeight() * .5, 0));
            while (pts.size() > 9) pts.pollFirst();
        }
        TUMBLES.entrySet().removeIf(v -> {
            Entity ent = mc.level.getEntity(v.getKey());
            Tumble tu = v.getValue();
            if (ent == null || t - tu.start > 60) return true;
            if (tu.landed < 0 && t - tu.start > 3 && ent.onGround()) tu.landed = t;
            return tu.landed >= 0 && t - tu.landed > 14;
        });
        GHOSTS.removeIf(g -> t + 1 - g.time > GHOST_LIFE + 1);
        BURSTS.removeIf(b -> t - b.start > 70);
    }
    /**
     * A body sliding along the ground: grit of the real block it slides on thrown up and back, the dust of
     * that ground rolling out to the sides (a wide cloud on sand, a little on wood), sparks off stone, a
     * scraped trail left behind.
     */
    private static void scrape(Entity ent, Vec3 from, Vec3 to, double speed, float t) {
        var level = Minecraft.getInstance().level;
        BlockState state = under(to);
        Surface surface = surface(state);
        float amt = amount();
        Vec3 dir = to.subtract(from).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        double w = ent.getBbWidth();
        int grit = (int) ((surface == Surface.WOOD ? 2 : surface == Surface.SAND ? 7 : 5) * Math.min(2, speed * 3) * amt);
        if (!state.isAir()) for (int i = 0; i < grit; i++) {
            Vec3 at = to.add(side.scale((random().nextDouble() - .5) * w)).add(0, .1, 0);
            Vec3 v = dir.scale(-.05 - random().nextDouble() * .1).add(side.scale((random().nextDouble() - .5) * .25)).add(0, .1 + random().nextDouble() * .2 * Math.min(1, speed * 2), 0);
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, v.x, v.y, v.z);
        }
        int colour = surfaceColour(to);
        int puffs = (int) ((surface == Surface.SAND ? 4 : surface == Surface.WOOD ? 1 : surface == Surface.SNOW ? 3 : 2) * amt + .5);
        double big = surface == Surface.SAND ? 1.6 : surface == Surface.SNOW ? 1.3 : surface == Surface.WOOD ? .6 : 1;
        for (int i = 0; i < puffs; i++) {
            Vec3 at = to.add(side.scale((random().nextDouble() - .5) * w * 1.4)).add(0, .15, 0);
            Vec3 v = side.scale((random().nextDouble() - .5) * .18 * big).add(dir.scale(-.03)).add(0, .02 + random().nextDouble() * .04, 0);
            MOTES.add(new Mote(at, v, (.35 + random().nextDouble() * .35) * big, .05 * big, colour, 20 + random().nextInt(14), t, false, false, surface == Surface.WOOD ? .3f : .5f));
        }
        if (surface == Surface.DIRT && random().nextFloat() < .6f)
            MOTES.add(new Mote(to.add(0, .15, 0), dir.scale(-.05).add(side.scale((random().nextDouble() - .5) * .2)).add(0, .18, 0), .07, 0, 0x5f8a3a, 14, t, false, true, .9f));
        if (surface == Surface.STONE && random().nextFloat() < .5f * Math.min(1, speed * 2))
            for (int i = 0; i < 2; i++) MOTES.add(new Mote(to.add(side.scale((random().nextDouble() - .5) * w)).add(0, .08, 0),
                    dir.scale(.08 + random().nextDouble() * .1).add(side.scale((random().nextDouble() - .5) * .2)).add(0, .08 + random().nextDouble() * .1, 0),
                    .03, 0, 0xffd27a, 4 + random().nextInt(3), t, true, true, 1));
        double markWidth = surface == Surface.SAND ? w * 1.3 : surface == Surface.WOOD ? w * .6 : w * .9;
        int dark = switch (surface) { case DIRT -> 0x3a2a1a; case STONE -> 0x505052; case SAND -> 0xa89060; case SNOW -> 0xc8ccd4; case WOOD -> 0x5a4630; default -> 0x4a4640; };
        double y = Math.floor(to.y + .001) + (to.y - Math.floor(to.y)) + .02;
        MARKS.add(new Mark(new Vec3(from.x, y, from.z), new Vec3(to.x, y, to.z), markWidth, dark, t));
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (CLAWS.isEmpty() && KICKS.isEmpty() && GHOSTS.isEmpty() && BURSTS.isEmpty() && MARKS.isEmpty() && MOTES.isEmpty() && STREAMERS.isEmpty()) return;
        float partial = e.getPartialTick();
        float time = mc.level.getGameTime() + partial;
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        // The afterimages first: real (translucent) bodies.
        if (!GHOSTS.isEmpty()) {
            PantherBody.CHARGE.reset();
            for (Ghost g : GHOSTS) {
                float age = time - g.time;
                if (age < .6f || age > GHOST_LIFE) continue;
                float a = .32f * (1 - (age - .6f) / (GHOST_LIFE - .6f));
                p.pushPose();
                p.translate(g.pos.x - cam.x, g.pos.y - cam.y, g.pos.z - cam.z);
                p.mulPose(Axis.YP.rotationDegrees(180 - g.yaw));
                p.scale(-.9375f, -.9375f, .9375f);
                p.translate(0, -1.501, 0);
                PantherBody.draw(p, buffers, 15728880, g.pose, g.headYaw, g.headPitch, g.anim, PantherBody.GHOST, a, 0);
                p.popPose();
            }
            buffers.endBatch();
        }
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        FilmContext c = new FilmContext(p, buffers, cam, new Vec3(rr.x, rr.y, rr.z), new Vec3(uu.x, uu.y, uu.z), time, 0, partial);
        try {
            for (Mark m : MARKS) {
                float a = .55f * (1 - (time - m.start) / 90f);
                flat(c, m.a, m.b, m.width, m.rgb, a, false);
            }
            for (Mote m : MOTES) {
                float age = (time - m.start) / m.life;
                if (age < 0 || age > 1) continue;
                Vec3 at = m.pos.add(m.vel.scale(partial));
                if (m.light) FilmFx.streak(c, at.subtract(m.vel.scale(1.2)), at, m.size, m.rgb, 0, m.alpha * (1 - age), true);
                else if (m.heavy) FilmFx.puff(c, at, m.size * 1.5, m.rgb, m.alpha * (1 - age));
                else FilmFx.puff(c, at, m.size + m.grow * (time - m.start), m.rgb, m.alpha * (1 - age) * Math.min(1, age * 6));
            }
            for (var entry : CLAWS.entrySet()) clawTrails(c, entry.getValue(), time);
            for (var entry : KICKS.entrySet()) kickTrails(c, entry.getValue(), time);
            for (var entry : STREAMERS.entrySet()) {
                Streamer st = entry.getValue();
                float fade = 1 - (time - st.start) / 18f;
                Vec3 prev = null;
                int i = 0, n = st.points.size();
                for (Vec3 pt : st.points) {
                    if (prev != null) {
                        float k0 = (i - 1f) / n, k1 = (float) i / n;
                        FilmFx.streak(c, prev, pt, .22, VIOLET, .5f * k0 * fade, .5f * k1 * fade, true);
                        FilmFx.streak(c, prev, pt, .06, WHITE, .4f * k0 * fade, .4f * k1 * fade, true);
                    }
                    prev = pt; i++;
                }
            }
            for (Burst b : BURSTS) burst(c, b, time, partial);
            buffers.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /**
     * Four thin parallel trails per hand, one from each claw tip, only where the tip is really sweeping
     * (a hand at rest leaves nothing): a silver-white core, a faint violet tint round it, gone in a blink.
     */
    private static void clawTrails(FilmContext c, ArrayDeque<ClawSample> q, float time) {
        while (!q.isEmpty() && time - q.peekFirst().time > CLAW_WINDOW) q.pollFirst();
        if (q.size() < 2) return;
        ClawSample prev = null;
        for (ClawSample s : q) {
            if (prev != null) {
                float dt = Math.max(.05f, s.time - prev.time);
                float k0 = 1 - (time - prev.time) / CLAW_WINDOW, k1 = 1 - (time - s.time) / CLAW_WINDOW;
                for (int i = 0; i < 8; i++) {
                    Vec3 a = prev.tips[i], b = s.tips[i];
                    double speed = a.distanceTo(b) / dt;
                    float show = Mth.clamp((float) (speed - .18) / .35f, 0, 1) * s.power;
                    if (show < .02f) continue;
                    float a0 = k0 * k0 * show, a1 = k1 * k1 * show;
                    FilmFx.streak(c, a, b, .05, VIOLET, .32f * a0, .32f * a1, true);
                    FilmFx.streak(c, a, b, .018, SILVER, .85f * a0, .85f * a1, true);
                }
            }
            prev = s;
        }
    }
    /** The kicking foot's air streak: a gray-white smear with a violet edge, widest at the foot. */
    private static void kickTrails(FilmContext c, ArrayDeque<KickSample> q, float time) {
        while (!q.isEmpty() && time - q.peekFirst().time > KICK_WINDOW) q.pollFirst();
        if (q.size() < 2) return;
        KickSample prev = null;
        for (KickSample s : q) {
            if (prev != null) {
                float dt = Math.max(.05f, s.time - prev.time);
                float k0 = 1 - (time - prev.time) / KICK_WINDOW, k1 = 1 - (time - s.time) / KICK_WINDOW;
                for (int f = 0; f < 2; f++) {
                    Vec3 a = f == 0 ? prev.right : prev.left, b = f == 0 ? s.right : s.left;
                    double speed = a.distanceTo(b) / dt;
                    float show = Mth.clamp((float) (speed - .3) / .5f, 0, 1) * s.power;
                    if (show < .02f) continue;
                    FilmFx.streak(c, a, b, .2 * k1, AIR, .18f * k0 * show, .3f * k1 * show, false);
                    FilmFx.streak(c, a, b, .1 * k1, VIOLET, .18f * k0 * show, .3f * k1 * show, true);
                    FilmFx.streak(c, a, b, .03, WHITE, .4f * k0 * show, .6f * k1 * show, true);
                }
            }
            prev = s;
        }
    }

    private static void burst(FilmContext c, Burst b, float time, float partial) {
        float age = time - b.start();
        Vec3 at = b.pos(), dir = b.dir();
        switch (b.kind()) {
            case FX_CLAW_HIT -> {
                if (age > 6) return;
                float a = 1 - age / 6;
                boolean dbl = Math.abs(b.power()) >= 2;
                FilmFx.glow(c, at, (dbl ? .9 : .6) * (1 + age * .1), VIOLET, .45f * a);
                FilmFx.glow(c, at, dbl ? .35 : .25, WHITE, .7f * a * a);
                // Four claw marks across them, for an instant (eight for the double).
                Vec3 look = dir.normalize();
                Vec3 across = look.cross(new Vec3(0, 1, 0));
                if (across.lengthSqr() < 1e-4) across = new Vec3(1, 0, 0);
                across = across.normalize();
                Vec3 up = across.cross(look).normalize();
                int hands = dbl ? 2 : 1;
                for (int h = 0; h < hands; h++) {
                    float sgn = dbl ? (h == 0 ? 1 : -1) : Math.signum(b.power());
                    Vec3 slash = across.scale(-sgn).add(up.scale(dbl ? .2 : -.7)).normalize();
                    Vec3 step = slash.cross(look).normalize().scale(.13);
                    Vec3 centre = at.subtract(look.scale(.5)).add(dbl ? across.scale(.18 * sgn) : Vec3.ZERO);
                    float draw = Mth.clamp(age / 1.2f, 0, 1);
                    for (int i = 0; i < 4; i++) {
                        Vec3 o = centre.add(step.scale(i - 1.5));
                        Vec3 s0 = o.subtract(slash.scale(.55)), s1 = s0.add(slash.scale(1.1 * draw));
                        FilmFx.streak(c, s0, s1, .045, VIOLET, .2f * a, .55f * a, true);
                        FilmFx.streak(c, s0, s1, .015, WHITE, .3f * a, .9f * a, true);
                    }
                }
            }
            case FX_UPPER -> {
                if (age > 8) return;
                float a = 1 - age / 8;
                FilmFx.glow(c, at, 1.1 * (1 + age * .12), VIOLET, .5f * a);
                FilmFx.glow(c, at, .4, WHITE, .8f * a * a);
                for (int i = 0; i < 4; i++) {
                    Vec3 o = at.add(new Vec3(dir.z, 0, -dir.x).normalize().scale((i - 1.5) * .14)).add(dir.scale(-.4));
                    float draw = Mth.clamp(age / 1.4f, 0, 1);
                    FilmFx.streak(c, o.add(0, -.7, 0), o.add(0, -.7 + 1.6 * draw, 0), .05, VIOLET, .2f * a, .6f * a, true);
                    FilmFx.streak(c, o.add(0, -.7, 0), o.add(0, -.7 + 1.6 * draw, 0), .016, WHITE, .3f * a, .9f * a, true);
                }
            }
            case FX_POUNCE -> {
                if (age > 7) return;
                float a = 1 - age / 7;
                ring(c, at.add(0, .9, 0).subtract(dir.scale(.3 + age * .25)), dir, .5 + age * .22, .07, AIR, .45f * a);
                ring(c, at.add(0, .9, 0).subtract(dir.scale(.2 + age * .2)), dir, .4 + age * .18, .04, VIOLET, .4f * a);
            }
            case FX_CONTACT -> {
                if (age > 4) return;
                float a = 1 - age / 4;
                FilmFx.glow(c, at, .9 + age * .2, WHITE, .65f * a * a);
                FilmFx.glow(c, at, 1.4, VIOLET, .35f * a);
            }
            case FX_KICK -> {
                if (age > 10) return;
                float a = 1 - age / 10;
                FilmFx.glow(c, at, 1.6 * (1 + age * .08), WHITE, .7f * a * a);
                FilmFx.glow(c, at, 2.2, VIOLET, .4f * a);
                // The kinetic ring: square to the kick, racing out from the impact.
                ring(c, at.add(dir.scale(age * .25)), dir, .4 + age * .35, .12 * a + .03, WHITE, .6f * a);
                ring(c, at.add(dir.scale(age * .2)), dir, .3 + age * .28, .08, VIOLET, .55f * a);
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI / 3 + b.entity();
                    Vec3 side = new Vec3(dir.z, 0, -dir.x).normalize().scale(Math.cos(ang)).add(0, Math.sin(ang), 0);
                    Vec3 s0 = at.add(side.scale(.3 + age * .2)).add(dir.scale(age * .3)), s1 = s0.add(dir.scale(.9 * a)).add(side.scale(.3));
                    FilmFx.streak(c, s0, s1, .03, AIR, .6f * a, 0, true);
                }
            }
            case FX_LAUNCH -> {
                if (age > 5) return;
                FilmFx.glow(c, at, .9, VIOLET, .45f * (1 - age / 5));
            }
            case FX_SCRAPE -> {
                if (age > 6) return;
                float a = 1 - age / 6;
                FilmFx.ring(c, at.add(0, .05, 0), .5 + age * .3, .12, surfaceColour(at), .35f * a, false);
            }
            case FX_SPIN_KICK -> {
                boolean last = Math.abs(b.power()) >= 2;
                if (b.entity() < 0) {
                    // The swept leg's air, a flat ring round him at hip height.
                    if (age > 6) return;
                    float a = 1 - age / 6;
                    FilmFx.ring(c, at, 1.2 + age * .35, last ? .1 : .06, AIR, (last ? .4f : .25f) * a, true);
                    FilmFx.ring(c, at, 1.0 + age * .3, .04, VIOLET, .35f * a, true);
                    return;
                }
                if (age > (last ? 9 : 5)) return;
                float a = 1 - age / (last ? 9 : 5);
                FilmFx.glow(c, at, (last ? 1.5 : .8) * (1 + age * .1), WHITE, .6f * a * a);
                FilmFx.glow(c, at, last ? 2 : 1, VIOLET, .35f * a);
                if (last) ring(c, at.add(dir.scale(age * .2)), dir, .35 + age * .3, .09, WHITE, .5f * a);
            }
            case FX_RELEASE -> release(c, b, age, time);
            case FX_ABSORB -> {
                if (age > 6) return;
                float a = 1 - age / 6;
                FilmFx.glow(c, at, .6 + age * .1, VIOLET, .55f * a);
                FilmFx.glow(c, at, .2, WHITE, .6f * a * a);
            }
            case FX_DODGE -> {
                if (age > 5) return;
                float a = 1 - age / 5;
                // Short streaks where his body just was, the way the attack came.
                Vec3 from = at.add(0, 1, 0);
                for (int i = 0; i < 3; i++) {
                    Vec3 o = from.add(0, (i - 1) * .45, 0);
                    FilmFx.streak(c, o.add(dir.scale(.9)), o.subtract(dir.scale(.3)), .05, VIOLET, 0, .45f * a, true);
                }
            }
            case FX_REFLEX -> {
                if (age > 12) return;
                float a = 1 - age / 12;
                FilmFx.ring(c, at.add(0, .05, 0), .4 + age * .12, .05, VIOLET, .5f * a, true);
                Entity e = Minecraft.getInstance().level.getEntity(b.entity());
                if (e != null) {
                    Vec3 head = e.getPosition(partial).add(0, e.getBbHeight() * .86, 0);
                    FilmFx.glow(c, head, .9, VIOLET, .3f * a);
                }
            }
            case FX_LAND -> {
                if (age > 10) return;
                float a = 1 - age / 10;
                FilmFx.ring(c, at.add(0, .05, 0), .4 + age * .16 * b.power(), .14, surfaceColour(at), .3f * a, false);
            }
            default -> {}
        }
    }

    /**
     * The kinetic release: a white core, then a sphere of violet energy racing out through everyone near —
     * bright at its rim, a Wakandan lattice of light across its surface, a second shell behind it — a ring
     * of light and dust along the ground, cracks under him; all of it gone again in about a second.
     */
    private static void release(FilmContext c, Burst b, float age, float time) {
        if (age > 40) return;
        float k = b.power();
        double radius = Math.max(2, b.dir().x);
        Vec3 centre = b.pos().add(0, 1.1, 0);
        // Cracks under him first, so the light goes over them; they last a little longer than the light.
        float cracks = 1 - age / 40f;
        int n = 5 + (int) (6 * k);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + FilmFx.hash(i + b.start()) * .5;
            double len = radius * (.18 + .2 * FilmFx.hash(i * 3.1 + b.start())) * Math.min(1, age / 2);
            Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 mid = b.pos().add(dir.scale(len * .5)).add(dir.cross(new Vec3(0, 1, 0)).scale((FilmFx.hash(i + 7) - .5) * .3 * len));
            Vec3 s0 = b.pos().add(dir.scale(.4)).add(0, .03, 0), s1 = new Vec3(mid.x, s0.y, mid.z), s2 = b.pos().add(dir.scale(len)).add(0, .03, 0);
            flat(c, s0, s1, .08, 0x16121c, .7f * cracks, false);
            flat(c, s1, s2, .05, 0x16121c, .6f * cracks, false);
            if (age < 8) { flat(c, s0, s1, .04, VIOLET, .6f * (1 - age / 8), true); flat(c, s1, s2, .03, VIOLET, .5f * (1 - age / 8), true); }
        }
        if (age > 14) return;
        float out = 1 - (float) Math.pow(1 - Math.min(1, age / 7f), 3);
        double r = radius * out;
        float fade = age < 7 ? 1 : 1 - (age - 7) / 7f;
        if (age < 3) {
            FilmFx.glow(c, centre, 1.6 + 2.5 * k, WHITE, (1 - age / 3) * .9f);
            FilmFx.glow(c, centre, 3 + 4 * k, VIOLET, (1 - age / 3) * .6f);
        }
        sphere(c, centre, r, VIOLET, .55f * fade * (.6f + .4f * k));
        sphere(c, centre, r * .82, BLUE, .25f * fade);
        // A faint darker shell just ahead of it: the air pushed out of the way.
        if (age < 9) FilmFx.glow(c, centre, r * 1.15, VIOLET_DEEP, .08f * fade);
        lattice(c, centre, r, time, .75f * fade * (.6f + .4f * k));
        // Along the ground: a ring of light and a ring of dust.
        Vec3 ground = b.pos().add(0, .06, 0);
        FilmFx.ring(c, ground, r * 1.05, .25 + .3 * k, VIOLET, .6f * fade, true);
        FilmFx.ring(c, ground, r * 1.05, .1, WHITE, .5f * fade, true);
        FilmFx.ring(c, ground, r * .95, .9 * (1 + k), surfaceColour(b.pos()), .35f * fade, false);
    }
    /** A sphere of light, brighter at its rim than across its face. */
    private static void sphere(FilmContext c, Vec3 centre, double r, int rgb, float alpha) {
        if (alpha < .01f || r < .05) return;
        VertexConsumer v = c.buffers().getBuffer(FilmFx.ADD);
        Matrix4f m = c.pose().last().pose();
        int lat = 12, lon = 20;
        float cr = (rgb >> 16 & 255) / 255f, cg = (rgb >> 8 & 255) / 255f, cb = (rgb & 255) / 255f;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                Vec3[] q = {dirAt(t0, p0), dirAt(t1, p0), dirAt(t1, p1), dirAt(t0, p1)};
                for (Vec3 n : q) {
                    Vec3 at = centre.add(n.scale(r));
                    Vec3 view = at.subtract(c.camera()).normalize();
                    float rim = 1 - (float) Math.abs(n.dot(view));
                    float a = alpha * (.1f + .9f * rim * rim * rim);
                    v.vertex(m, (float) at.x, (float) at.y, (float) at.z).color(cr, cg, cb, a).endVertex();
                }
            }
        }
    }
    private static Vec3 dirAt(double theta, double phi) { return new Vec3(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi)); }
    /** The Wakandan lattice: the edges of a geodesic sphere drawn in light across the surface, turning slowly. */
    private static void lattice(FilmContext c, Vec3 centre, double r, float time, float alpha) {
        if (alpha < .01f || r < .2) return;
        double spin = time * .04;
        double cs = Math.cos(spin), sn = Math.sin(spin);
        for (int[] edge : EDGES) {
            Vec3 a = VERTS[edge[0]], bb = VERTS[edge[1]];
            Vec3 prev = null;
            for (int s = 0; s <= 3; s++) {
                Vec3 d = a.lerp(bb, s / 3.0).normalize();
                d = new Vec3(d.x * cs - d.z * sn, d.y, d.x * sn + d.z * cs);
                Vec3 at = centre.add(d.scale(r));
                if (prev != null) {
                    Vec3 view = at.subtract(c.camera()).normalize();
                    float rim = .35f + .65f * (1 - (float) Math.abs(d.dot(view)));
                    FilmFx.streak(c, prev, at, .05 + r * .006, SILVER, alpha * .5f * rim, alpha * .5f * rim, true);
                    FilmFx.streak(c, prev, at, .14 + r * .01, VIOLET, alpha * .3f * rim, alpha * .3f * rim, true);
                }
                prev = at;
            }
        }
    }
    private static final Vec3[] VERTS;
    private static final int[][] EDGES;
    static {
        // An icosahedron, each face split in four: a geodesic lattice of 42 points and 120 edges.
        double g = (1 + Math.sqrt(5)) / 2;
        List<Vec3> v = new ArrayList<>(List.of(new Vec3(-1, g, 0), new Vec3(1, g, 0), new Vec3(-1, -g, 0), new Vec3(1, -g, 0),
                new Vec3(0, -1, g), new Vec3(0, 1, g), new Vec3(0, -1, -g), new Vec3(0, 1, -g),
                new Vec3(g, 0, -1), new Vec3(g, 0, 1), new Vec3(-g, 0, -1), new Vec3(-g, 0, 1)));
        v.replaceAll(Vec3::normalize);
        int[][] faces = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        Map<Long, Integer> mids = new HashMap<>();
        Set<Long> edges = new HashSet<>();
        for (int[] f : faces) {
            int[] m = new int[3];
            for (int i = 0; i < 3; i++) {
                int a = f[i], b = f[(i + 1) % 3];
                long key = (long) Math.min(a, b) << 32 | Math.max(a, b);
                Integer mid = mids.get(key);
                if (mid == null) { v.add(v.get(a).add(v.get(b)).normalize()); mid = v.size() - 1; mids.put(key, mid); }
                m[i] = mid;
            }
            int[][] tris = {{f[0], m[0], m[2]}, {f[1], m[1], m[0]}, {f[2], m[2], m[1]}, {m[0], m[1], m[2]}};
            for (int[] t : tris) for (int i = 0; i < 3; i++) {
                int a = t[i], b = t[(i + 1) % 3];
                edges.add((long) Math.min(a, b) << 32 | Math.max(a, b));
            }
        }
        VERTS = v.toArray(new Vec3[0]);
        EDGES = new int[edges.size()][];
        int i = 0;
        for (long e : edges) EDGES[i++] = new int[]{(int) (e >> 32), (int) e};
    }
    /** A ring of light in the plane square to normal. */
    static void ring(FilmContext c, Vec3 centre, Vec3 normal, double radius, double width, int rgb, float alpha) {
        if (alpha < .01f) return;
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 u = Math.abs(n.y) < .9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 w = n.cross(u).normalize();
        int seg = 24;
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            Vec3 at = centre.add(u.scale(Math.cos(a) * radius)).add(w.scale(Math.sin(a) * radius));
            if (prev != null) FilmFx.streak(c, prev, at, width, rgb, alpha, alpha, true);
            prev = at;
        }
    }
    /** A flat band lying on the ground from a to b. */
    private static void flat(FilmContext c, Vec3 a, Vec3 b, double width, int rgb, float alpha, boolean light) {
        if (alpha < .01f) return;
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize().scale(width * .5);
        VertexConsumer v = c.buffers().getBuffer(light ? FilmFx.ADD : FilmFx.SOFT);
        Matrix4f m = c.pose().last().pose();
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f;
        Vec3 p0 = a.add(side), p1 = b.add(side), p2 = b.subtract(side), p3 = a.subtract(side);
        for (Vec3 q : new Vec3[]{p0, p1, p2, p3}) v.vertex(m, (float) q.x, (float) q.y, (float) q.z).color(r, g, bl, alpha).endVertex();
    }

    // ------------------------------------------------------------------ thrown bodies tumble
    @SubscribeEvent public static void tumbleIn(RenderLivingEvent.Pre<?, ?> e) {
        Tumble tu = TUMBLES.get(e.getEntity().getId());
        if (tu == null) return;
        float time = now();
        float since = time - tu.start;
        // Spinning up through the flight, eased back upright once they are sliding on the ground.
        float angle = tu.amount * 75 * (1 - (float) Math.exp(-since / 5));
        if (tu.landed >= 0) angle *= 1 - PantherMotion.ease((time - tu.landed) / 10f);
        if (Math.abs(angle) < .5f) return;
        PoseStack p = e.getPoseStack();
        float h = e.getEntity().getBbHeight() * .5f;
        p.pushPose();
        p.translate(0, h, 0);
        p.mulPose(new Quaternionf().rotateAxis(angle * Mth.DEG_TO_RAD, (float) tu.axis.x, (float) tu.axis.y, (float) tu.axis.z));
        p.translate(0, -h, 0);
        TUMBLING_NOW.add(e.getEntity().getId());
    }
    @SubscribeEvent public static void tumbleOut(RenderLivingEvent.Post<?, ?> e) {
        if (TUMBLING_NOW.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ first person: his arms and claws
    /** Where a first-person arm is at a moment of a move: offset (view space), yaw, pitch, roll (degrees), wrist and curl. */
    private static float[] fpPose(int side, int action, float t, int flags) {
        boolean right = side == 0;
        float sx = right ? 1 : -1;
        float x = .42f * sx, y = -.62f, z = -.72f, yaw = -10 * sx, pitch = -18, roll = 12 * sx, wrist = -.2f, curl = .4f;
        switch (action) {
            case CLAW_RIGHT, CLAW_LEFT, CLAW_UPPER -> {
                boolean striking = action == CLAW_UPPER ? right : (action == CLAW_RIGHT) == right;
                if (striking && action != CLAW_UPPER) {
                    float load = PantherMotion.k(t, 0, 1.3f), cut = PantherMotion.snap(t, 1.3f, CLAW_HIT + .4f), back = PantherMotion.k(t, CLAW_HIT + 1.2f, CLAW_TICKS);
                    float w = 1 - back;
                    x += (.15f * load - .75f * cut) * sx * w; y += (.3f * load * (1 - cut) - .18f * cut) * w; z += (.1f * load - .25f * cut) * w;
                    yaw += (25 * load - 70 * cut) * sx * w; roll += (35 * load - 70 * cut) * sx * w; pitch += (-20 * load + 25 * cut) * w;
                    curl = .4f - .4f * Math.max(load, cut) * w;
                } else if (striking) {
                    float load = PantherMotion.k(t, 0, 3), up = PantherMotion.snap(t, 3, UPPER_HIT + .5f), back = PantherMotion.k(t, UPPER_HIT + 2, UPPER_TICKS);
                    float w = 1 - back;
                    x += -.2f * sx * up * w; y += (-.35f * load * (1 - up) + .75f * up) * w; z += (-.25f * up) * w;
                    pitch += (25 * load * (1 - up) - 60 * up) * w; roll += -20 * sx * up * w;
                    curl = .4f - .4f * up * w;
                } else {
                    float w = 1 - PantherMotion.k(t, 4, CLAW_TICKS);
                    y -= .08f * w; z += .06f * w;
                }
            }
            case CLAW_DOUBLE -> {
                float drop = PantherMotion.k(t, 0, 1.8f), hit = PantherMotion.snap(t, 1.8f, DOUBLE_HIT + .4f), back = PantherMotion.k(t, DOUBLE_HIT + 1.5f, DOUBLE_TICKS);
                float w = 1 - back;
                x += (.12f * drop * (1 - hit) - .3f * hit) * sx * w; y += (-.15f * drop * (1 - hit) + .1f * hit) * w; z += (.2f * drop * (1 - hit) - .45f * hit) * w;
                pitch += (20 * drop - 30 * hit) * w; yaw += -25 * sx * hit * w;
                curl = .4f - .4f * hit * w;
            }
            case FRENZY -> {
                float period = FRENZY_STRIKE * 2;
                float phase = (t % period) / period * Mth.TWO_PI;
                float wv = Mth.sin(phase);
                float out = Math.max(0, (right ? 1 : -1) * Math.signum(wv) * (float) Math.pow(Math.abs(wv), .55));
                x += -.45f * sx * out; y += .1f * out; z += -.3f * out; yaw += -45 * sx * out; roll += -40 * sx * out; curl = .4f - .35f * out;
            }
            case RELEASE_CHARGE -> {
                float open = PantherMotion.k(t, 3, 10.5f), gather = PantherMotion.k(t, 10.5f, CHARGE_TICKS);
                x += (.55f * open - .25f * gather) * sx; y += .15f * open; yaw += 30 * sx * open; roll += 40 * sx * open; curl = .4f - .4f * open + .2f * gather;
            }
            case RELEASE -> { float out = PantherMotion.snap(t, 0, 1.5f) * (1 - PantherMotion.k(t, 3, RELEASE_TICKS)); x += .8f * sx * out; y += .25f * out; roll += 60 * sx * out; curl = 0; }
            case DODGE -> {
                float s = flags == DODGE_LEFT || flags == DODGE_BACK_LEFT ? -1 : flags == DODGE_RIGHT || flags == DODGE_BACK_RIGHT ? 1 : 0;
                float w = PantherMotion.snap(t, .3f, 2) * (1 - PantherMotion.k(t, 3, DODGE_TICKS));
                x += .25f * s * w; y += (flags == DODGE_CROUCH ? .25f : .05f) * w; z += (flags == DODGE_BACK ? .15f : 0) * w; roll += 20 * s * w;
            }
            case POUNCE_LOAD, POUNCE -> { x += -.05f * sx; y += .1f; z += -.15f; curl = 0; pitch += -10; }
            case SPIN, SPIN_LOAD -> { x += -.15f * sx; y += .05f; curl = .6f; }
            default -> {}
        }
        return new float[]{x, y, z, yaw, pitch, roll, wrist, curl};
    }
    private static void fpApply(PoseStack p, float[] f, float bob) {
        p.translate(f[0], f[1] + bob, f[2]);
        p.mulPose(Axis.YP.rotationDegrees(f[3]));
        p.mulPose(Axis.XP.rotationDegrees(f[4]));
        p.mulPose(Axis.ZP.rotationDegrees(f[5]));
        // From the view into the body's space: the forearm forward (+y of the model), the palm toward the middle.
        p.mulPose(Axis.XP.rotationDegrees(-90));
        p.mulPose(Axis.YP.rotationDegrees(180));
        p.scale(1.2f, 1.2f, 1.2f);
        p.translate(0, -.15f, 0);
    }
    @SubscribeEvent public static void hand(RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !PantherClient.isHero(mc.player) || FilmDirector.playing()) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        PantherClient.State s = PantherClient.get(mc.player);
        int action = s == null ? IDLE : s.action;
        float t = s == null ? 0 : PantherClient.clock(s, e.getPartialTick());
        int flags = s == null ? 0 : s.flags;
        float time = mc.player.tickCount + e.getPartialTick();
        float bob = .012f * Mth.sin(time * .1f);
        PoseStack p = e.getPoseStack();
        MultiBufferSource b = e.getMultiBufferSource();
        int light = e.getPackedLight();
        PantherBody.Charge charge = PantherBody.CHARGE.reset();
        if (s != null) {
            charge.level = s.energy;
            if (action == RELEASE_CHARGE) { charge.flow = PantherMotion.clamp(t / CHARGE_TICKS); charge.level = Math.max(charge.level, s.released); }
            if (action == RELEASE) charge.flash = 1.4f * (1 - PantherMotion.k(t, 1, 3));
            charge.time = time;
        }
        for (int side = 0; side < 2; side++) {
            float[] f = fpPose(side, action, t, flags);
            p.pushPose();
            fpApply(p, f, bob);
            PantherBody.firstPersonArm(p, b, light, side, f[6], 0, f[7]);
            p.popPose();
            // The claws' trails: the same arm at moments just past, joined up.
            if (clawing(action)) fpTrails(p, b, side, action, t, flags, bob);
        }
    }
    private static void fpTrails(PoseStack p, MultiBufferSource b, int side, int action, float t, int flags, float bob) {
        int steps = 7;
        Vec3[][] tips = new Vec3[steps][4];
        for (int j = 0; j < steps; j++) {
            float at = t - j * .32f;
            float[] f = fpPose(side, action, Math.max(0, at), flags);
            p.pushPose();
            fpApply(p, f, bob);
            p.translate(0, 4.8f / 16, 0);
            p.mulPose(Axis.XP.rotation(f[6]));
            for (int i = 0; i < 4; i++) tips[j][i] = PantherBody.clawTip(p, side, i, f[7]);
            p.popPose();
        }
        VertexConsumer v = b.getBuffer(FilmFx.ADD);
        Matrix4f m = new Matrix4f();
        for (int j = 0; j < steps - 1; j++) {
            float a0 = 1 - j / (float) (steps - 1), a1 = 1 - (j + 1) / (float) (steps - 1);
            for (int i = 0; i < 4; i++) {
                Vec3 x0 = tips[j][i], x1 = tips[j + 1][i];
                double speed = x0.distanceTo(x1) / .32;
                float show = Mth.clamp((float) (speed - .05) / .12f, 0, 1);
                if (show < .02f) continue;
                Vec3 d = x1.subtract(x0);
                Vec3 across = d.cross(x0).normalize().scale(.006);
                quad(v, m, x0.add(across), x1.add(across), x1.subtract(across), x0.subtract(across), SILVER, .8f * a0 * show, .8f * a1 * show);
                Vec3 wide = across.scale(3);
                quad(v, m, x0.add(wide), x1.add(wide), x1.subtract(wide), x0.subtract(wide), VIOLET, .25f * a0 * show, .25f * a1 * show);
            }
        }
    }
    private static void quad(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alphaA, float alphaB) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f;
        v.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, alphaA).endVertex();
        v.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, alphaB).endVertex();
        v.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(r, g, bl, alphaB).endVertex();
        v.vertex(m, (float) d.x, (float) d.y, (float) d.z).color(r, g, bl, alphaA).endVertex();
    }

    private static void clear() {
        CLAWS.clear(); KICKS.clear(); GHOSTS.clear(); BURSTS.clear(); SLIDERS.clear(); MARKS.clear(); TUMBLES.clear(); MOTES.clear(); STREAMERS.clear();
        TUMBLING_NOW.clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); PantherLayer.clear(); }

    @Mod.EventBusSubscriber(modid = SuperheroMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e) {
            for (String skin : e.getSkins()) {
                var renderer = e.getSkin(skin);
                if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer player) player.addLayer(new PantherLayer(player));
            }
        }
    }
}
