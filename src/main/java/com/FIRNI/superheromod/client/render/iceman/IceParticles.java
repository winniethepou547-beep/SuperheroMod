package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.heroes.iceman.IcemanConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Iceman's loose matter, shared by every effect (client only, pooled and capped: never a flood):
 * <ul>
 * <li>shards: pieces of real ice (IceMesh) that fly, spin, bounce on the ground, then melt away into a little frost;</li>
 * <li>mist: cold vapour, soft puffs that swell and thin out (sinking a little: cold air);</li>
 * <li>snow: fine frost dust and snow spray, small bright flecks under gravity and drag;</li>
 * <li>flashes: short cold-white light at a break;</li>
 * <li>rings: flat shock fronts on the ground.</li>
 * </ul>
 * The break language every ice thing uses is {@link #shatter}: a short flash, the big pieces, the shards, mist and
 * dust (the crack before it is the caller's).
 * The amount follows the client's effects setting, and far from the camera there are fewer.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IceParticles {
    private IceParticles() {}

    private static final class Shard {
        Vec3 pos, prev, vel, axis; float spin, angle, angleO, size; final int seed; final IceMesh.Mat mat;
        int age, life, rest; boolean ground;
        Shard(Vec3 pos, Vec3 vel, float size, int life, int seed, IceMesh.Mat mat) {
            this.pos = pos; prev = pos; this.vel = vel; this.size = size; this.life = life; this.seed = seed; this.mat = mat;
            Random r = new Random(seed);
            axis = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            spin = (r.nextFloat() - .5f) * .9f;
        }
    }
    private static final class Puff {
        Vec3 pos, prev, vel; float size, grow, alpha; int rgb, age, life; final int kind;
        Puff(int kind, Vec3 pos, Vec3 vel, float size, float grow, int rgb, float alpha, int life) {
            this.kind = kind; this.pos = pos; prev = pos; this.vel = vel; this.size = size; this.grow = grow; this.rgb = rgb; this.alpha = alpha; this.life = life;
        }
    }
    private record Ring(Vec3 at, float radius, float width, int rgb, float alpha, float start, float life, boolean light) {}
    private static final int MIST = 0, SNOW = 1, FLASH = 2;
    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final int MAX_SHARDS = 360, MAX_PUFFS = 900;
    private static final Random RNG = new Random();
    public static final int MIST_RGB = 0xdcecff, SNOW_RGB = 0xf4faff, COLD_LIGHT = 0x9fd8ff;

    /** How many of something to make here: the effects setting, fewer far from the camera. */
    public static int count(int n, Vec3 at) {
        var mc = Minecraft.getInstance();
        double k = IcemanConfig.EFFECTS.get();
        if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
            double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
            double far = IcemanConfig.FAR_DETAIL.get();
            if (d > far) k *= Math.max(.2, far / d);
        }
        return (int) Math.round(n * k);
    }
    public static float rand() { return RNG.nextFloat(); }
    public static double gauss() { return RNG.nextGaussian(); }
    public static Vec3 jitter(double s) { return new Vec3(RNG.nextGaussian() * s, RNG.nextGaussian() * s, RNG.nextGaussian() * s); }

    // ------------------------------------------------------------------ making
    public static void shard(Vec3 at, Vec3 vel, float size, int life, IceMesh.Mat mat) {
        if (SHARDS.size() >= MAX_SHARDS) SHARDS.remove(0);
        SHARDS.add(new Shard(at, vel, size, life, RNG.nextInt(1 << 20), mat));
    }
    public static void mist(Vec3 at, Vec3 vel, float size, float grow, float alpha, int life) {
        puff(MIST, at, vel, size, grow, MIST_RGB, alpha, life);
    }
    public static void snow(Vec3 at, Vec3 vel, float size, int life) {
        puff(SNOW, at, vel, size, 0, SNOW_RGB, .9f, life);
    }
    public static void flash(Vec3 at, float size, float alpha, int life) {
        puff(FLASH, at, Vec3.ZERO, size, size * .04f, COLD_LIGHT, alpha, life);
    }
    public static void ring(Vec3 at, float radius, float width, int rgb, float alpha, int life, boolean light) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (RINGS.size() > 40) RINGS.remove(0);
        RINGS.add(new Ring(at, radius, width, rgb, alpha, level.getGameTime() + Minecraft.getInstance().getFrameTime(), life, light));
    }
    private static void puff(int kind, Vec3 at, Vec3 vel, float size, float grow, int rgb, float alpha, int life) {
        if (PUFFS.size() >= MAX_PUFFS) PUFFS.remove(0);
        PUFFS.add(new Puff(kind, at, vel, size, grow, rgb, alpha, life));
    }

    /**
     * Ice breaking apart at a place: a short cold flash, a few big pieces, many shards (sizes from size), mist and frost
     * dust, flung along push and outward. size ~ how big the thing was (blocks). For anything of ice that breaks.
     */
    public static void shatter(Vec3 at, Vec3 push, float size, IceMesh.Mat mat) {
        int big = count(Math.round(3 + size * 3), at), small = count(Math.round(8 + size * 16), at), mist = count(Math.round(4 + size * 5), at), snow = count(Math.round(10 + size * 18), at);
        flash(at, .6f + size * .7f, .8f, 5);
        for (int i = 0; i < big; i++) {
            Vec3 out = jitter(1).normalize();
            Vec3 v = push.add(out.scale(.12 + .1 * size * RNG.nextFloat())).add(0, .12 + .1 * RNG.nextFloat(), 0);
            shard(at.add(out.scale(size * .3 * RNG.nextFloat())), v, size * (.18f + .14f * RNG.nextFloat()), 50 + RNG.nextInt(40), mat);
        }
        for (int i = 0; i < small; i++) {
            Vec3 out = jitter(1).normalize();
            Vec3 v = push.add(out.scale(.15 + .25 * RNG.nextFloat() * Math.min(1.5, .6 + size))).add(0, .1 + .15 * RNG.nextFloat(), 0);
            shard(at.add(out.scale(size * .35 * RNG.nextFloat())), v, (.05f + .07f * RNG.nextFloat()) * Math.min(2.5f, .7f + size * .5f), 30 + RNG.nextInt(30), i % 3 == 0 ? IceMesh.FRESH : mat);
        }
        for (int i = 0; i < mist; i++)
            mist(at.add(jitter(size * .3)), push.scale(.2).add(jitter(.03)).add(0, .01, 0), .3f + size * .3f, .02f + .01f * size, .35f, 30 + RNG.nextInt(25));
        for (int i = 0; i < snow; i++)
            snow(at.add(jitter(size * .25)), push.scale(.4).add(jitter(.14)).add(0, .1, 0), .03f + .03f * RNG.nextFloat(), 18 + RNG.nextInt(20));
    }
    /** Frost dust shed by something cold moving (a few flecks and a breath of mist). */
    public static void frostDust(Vec3 at, Vec3 vel, float amount) {
        int n = count(Math.round(amount * 3), at);
        for (int i = 0; i < n; i++) snow(at.add(jitter(.15)), vel.scale(.3).add(jitter(.02)).add(0, -.01, 0), .018f + .02f * RNG.nextFloat(), 14 + RNG.nextInt(14));
        if (RNG.nextFloat() < amount * .25f) mist(at, vel.scale(.2).add(0, -.004, 0), .12f, .015f, .12f, 22);
    }

    // ------------------------------------------------------------------ living
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        if (mc.isPaused()) return;
        Level level = mc.level;
        for (int i = SHARDS.size() - 1; i >= 0; i--) {
            Shard s = SHARDS.get(i);
            s.prev = s.pos; s.angleO = s.angle;
            if (++s.age > s.life) {
                // Melted down to a little frost.
                if (s.size > .08f && RNG.nextFloat() < .5f) mist(s.pos, new Vec3(0, .003, 0), s.size * 1.2f, .01f, .12f, 16);
                SHARDS.remove(i);
                continue;
            }
            if (s.ground) { s.rest++; continue; }
            Vec3 next = s.pos.add(s.vel);
            if (solid(level, next)) {
                // Bounce off what it hit (mostly the ground), losing most of its speed, then lie still.
                boolean floor = !solid(level, new Vec3(next.x, s.pos.y, next.z));
                s.vel = floor ? new Vec3(s.vel.x * .45, -s.vel.y * .28, s.vel.z * .45) : new Vec3(-s.vel.x * .3, s.vel.y * .6, -s.vel.z * .3);
                s.spin *= .5f;
                if (floor && Math.abs(s.vel.y) < .05) { s.ground = true; s.vel = Vec3.ZERO; s.pos = new Vec3(next.x, Math.floor(next.y) + 1.0 + s.size * .25, next.z); continue; }
            } else s.pos = next;
            s.vel = new Vec3(s.vel.x * .97, s.vel.y * .98 - .045, s.vel.z * .97);
            s.angle += s.spin;
        }
        for (int i = PUFFS.size() - 1; i >= 0; i--) {
            Puff p = PUFFS.get(i);
            p.prev = p.pos;
            if (++p.age > p.life) { PUFFS.remove(i); continue; }
            p.pos = p.pos.add(p.vel);
            if (p.kind == SNOW) {
                p.vel = new Vec3(p.vel.x * .92, p.vel.y * .95 - .012, p.vel.z * .92);
                if (solid(level, p.pos)) { p.vel = Vec3.ZERO; p.pos = p.prev; }
            } else if (p.kind == MIST) p.vel = new Vec3(p.vel.x * .93, p.vel.y * .93 - .0008, p.vel.z * .93);
        }
        float now = level.getGameTime();
        RINGS.removeIf(r -> now - r.start() > r.life() + 1);
    }
    private static boolean solid(Level level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        var state = level.getBlockState(pos);
        if (state.isAir()) return false;
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        double y = at.y - pos.getY();
        return y <= shape.max(net.minecraft.core.Direction.Axis.Y) + 1e-3 && y >= shape.min(net.minecraft.core.Direction.Axis.Y) - 1e-3;
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (SHARDS.isEmpty() && PUFFS.isEmpty() && RINGS.isEmpty()) return;
        IceStage st = IceStage.open(e);
        if (st == null) return;
        try {
            float partial = st.partial;
            if (!SHARDS.isEmpty()) {
                IceMesh.Ctx c = st.ice();
                for (Shard s : SHARDS) {
                    Vec3 at = s.prev.lerp(s.pos, partial);
                    float melt = Mth.clamp((s.life - s.age - partial) / 14f, 0, 1);
                    float size = s.size * (.35f + .65f * melt);
                    c.light = IceStage.light(at);
                    float ang = Mth.lerp(partial, s.angleO, s.angle);
                    Vec3 dir = rotate(new Vec3(0, 1, 0), s.axis, ang);
                    IceMesh.shard(c, at, dir, size, s.seed, s.mat, .4f + .6f * melt);
                }
                st.endIce();
            }
            FilmContext f = st.fx();
            for (Puff p : PUFFS) {
                float k = (p.age + partial) / p.life;
                if (k > 1) continue;
                Vec3 at = p.prev.lerp(p.pos, partial);
                switch (p.kind) {
                    case SNOW -> FilmFx.puff(f, at, p.size, p.rgb, p.alpha * (1 - k * k));
                    case FLASH -> FilmFx.glow(f, at, p.size + p.grow * p.age, p.rgb, p.alpha * (1 - k) * (1 - k));
                    default -> FilmFx.puff(f, at, p.size + p.grow * (p.age + partial), p.rgb, p.alpha * Math.min(1, k * 6) * (1 - k));
                }
            }
            for (Ring r : RINGS) {
                float k = (st.time - r.start()) / r.life();
                if (k < 0 || k > 1) continue;
                float ease = 1 - (1 - k) * (1 - k);
                FilmFx.ring(f, r.at(), r.radius() * ease, r.width() * (1 - .5f * k), r.rgb(), r.alpha() * (1 - k), r.light());
            }
        } finally {
            st.close();
        }
    }
    /** v turned about the unit axis by angle (Rodrigues). */
    static Vec3 rotate(Vec3 v, Vec3 axis, float angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
    }
    public static void clear() { SHARDS.clear(); PUFFS.clear(); RINGS.clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}
