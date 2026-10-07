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
 * <li>shards: chunks of real ice, small textured ice CUBES (Minecraft's block-break bits, made 3D) that fly, tumble,
 * bounce on the ground, then melt away into a little frost;</li>
 * <li>mist: cold vapour, soft puffs that swell and thin out (sinking a little: cold air);</li>
 * <li>snow: fine frost dust and snow spray, small bright flecks under gravity and drag;</li>
 * <li>flashes: short cold-white light at a break;</li>
 * <li>rings: flat shock fronts on the ground.</li>
 * </ul>
 * The break language every ice thing uses is {@link #shatter}: a short flash, the big pieces, the shards, mist and
 * dust (the crack before it is the caller's).
 * The amount follows the client's effects setting, and far from the camera there are fewer.
 * <p>
 * It also holds the block shapes every ice effect is built from (the user's direction: Minecraft's cube structure,
 * never crystals or round tubes): {@link #obox} a turned box, {@link #cube} a box turned by yaw / pitch / roll,
 * {@link #hexa} any six-faced block from its corners, {@link #spike} a square ice spike or icicle (stepped square tiers
 * closing to a four-sided point, like pointed dripstone made of ice).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IceParticles {
    private IceParticles() {}

    private static final class Shard {
        Vec3 pos, prev, vel, axis; float spin, angle, angleO, size, grav = .045f; final int seed; final IceMesh.Mat mat;
        int age, life, rest; boolean ground;
        Shard(Vec3 pos, Vec3 vel, float size, int life, int seed, IceMesh.Mat mat) {
            this.pos = pos; prev = pos; this.vel = vel; this.size = size; this.life = life; this.seed = seed; this.mat = mat;
            Random r = new Random(seed);
            axis = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            spin = (r.nextFloat() - .5f) * .9f;
        }
    }
    private static final class Puff {
        Vec3 pos, prev, vel; float size, grow, alpha, swirl; int rgb, age, life; final int kind;
        Puff(int kind, Vec3 pos, Vec3 vel, float size, float grow, int rgb, float alpha, int life) {
            this.kind = kind; this.pos = pos; prev = pos; this.vel = vel; this.size = size; this.grow = grow; this.rgb = rgb; this.alpha = alpha; this.life = life;
        }
    }
    private record Ring(Vec3 at, float radius, float width, int rgb, float alpha, float start, float life, boolean light) {}
    private static final int MIST = 0, SNOW = 1, FLASH = 2, DUST = 3;
    private static final List<Shard> SHARDS = new ArrayList<>();
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final List<Ring> RINGS = new ArrayList<>();
    private static final int MAX_SHARDS = 360, MAX_PUFFS = 900;
    private static final Random RNG = new Random();
    /** Cold vapour is blue-white (never smoke grey), snow and ice dust white with a blue cast, cold light. */
    public static final int MIST_RGB = 0xc4defc, SNOW_RGB = 0xeef7ff, ICE_DUST_RGB = 0xb8dcff, COLD_LIGHT = 0x9fd8ff;

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
    public static void shard(Vec3 at, Vec3 vel, float size, int life, IceMesh.Mat mat) { shard(at, vel, size, life, mat, .045f); }
    /** A piece with its own gravity (a light crystal fragment floats in the cold plume: small grav). */
    public static void shard(Vec3 at, Vec3 vel, float size, int life, IceMesh.Mat mat, float grav) {
        if (SHARDS.size() >= MAX_SHARDS) SHARDS.remove(0);
        Shard sh = new Shard(at, vel, size, life, RNG.nextInt(1 << 20), mat);
        sh.grav = grav;
        SHARDS.add(sh);
    }
    public static void mist(Vec3 at, Vec3 vel, float size, float grow, float alpha, int life) {
        puff(MIST, at, vel, size, grow, MIST_RGB, alpha, life);
    }
    public static void snow(Vec3 at, Vec3 vel, float size, int life) {
        puff(SNOW, at, vel, size, 0, SNOW_RGB, .9f, life);
    }
    /** A tiny ice crystal in the air that drifts, spirals round its way (swirl: radians a tick, 0 straight) and glints out. */
    public static void crystalDust(Vec3 at, Vec3 vel, float size, float swirl, int life) {
        puff(DUST, at, vel, size, 0, RNG.nextFloat() < .55f ? SNOW_RGB : ICE_DUST_RGB, .95f, life);
        PUFFS.get(PUFFS.size() - 1).swirl = swirl;
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

    // ------------------------------------------------------------------ the cold (the air itself freezing)
    /**
     * The cryogenic plume (the reference's hand effect): the air freezing out of a point toward dir (unit) with power
     * (about .3 a puff .. 1 a full blast .. 2 a huge one). Five layers, never a flood: (1) soft blue-white vapour rolling
     * out and swelling, slowed by the air; (2) tiny ice crystals, some shot fast, some floating, some spiralling, glinting
     * out; (3) crystal fragments, little broken pieces that barely fall; (4) the ice itself is the caller's, growing
     * inside this cloud; (5) frost residue (the caller's ground frost, and the vapour sinking as it thins).
     * spread = how wide it opens (0 a jet .. 1 a burst all round).
     */
    public static void cryo(Vec3 at, Vec3 dir, float power, float spread) {
        Vec3 d = dir.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : dir.normalize();
        int mist = count(Math.round(2 + power * 5), at), dust = count(Math.round(6 + power * 16), at), frag = count(Math.round(1 + power * 4), at);
        for (int i = 0; i < mist; i++) {
            Vec3 v = d.scale(.04 + .14 * RNG.nextFloat() * (.6 + power * .5)).add(jitter(.03 + .05 * spread));
            mist(at.add(jitter(.12 * power)), v, .22f + .3f * power * (.6f + .4f * RNG.nextFloat()), .022f + .012f * power, .2f + .06f * power, 26 + RNG.nextInt(22));
        }
        for (int i = 0; i < dust; i++) {
            float kind = RNG.nextFloat();
            Vec3 cone = d.add(jitter(.25 + .7 * spread)).normalize();
            Vec3 v = kind < .35f ? cone.scale(.3 + .35 * RNG.nextFloat() * (.7 + .3 * power))        // shot out fast
                    : kind < .7f ? cone.scale(.06 + .08 * RNG.nextFloat()).add(0, .01, 0)            // floating
                    : cone.scale(.12 + .12 * RNG.nextFloat());                                       // spiralling
            float swirl = kind >= .7f ? (RNG.nextBoolean() ? 1 : -1) * (.25f + .35f * RNG.nextFloat()) : 0;
            crystalDust(at.add(jitter(.08 * power)), v, .018f + .022f * RNG.nextFloat(), swirl, 14 + RNG.nextInt(24));
        }
        for (int i = 0; i < frag; i++) {
            Vec3 cone = d.add(jitter(.3 + .6 * spread)).normalize();
            shard(at.add(jitter(.06)), cone.scale(.12 + .2 * RNG.nextFloat()), .05f + .05f * RNG.nextFloat() * Math.min(2, power), 14 + RNG.nextInt(12),
                    RNG.nextFloat() < .4f ? IceMesh.FRESH : IceMesh.CLEAR, .012f);
        }
    }
    /**
     * Cold air rolling out low along the ground round a point (a big formation, a slam, the shell): vapour spreading
     * outward to radius, hugging the ground, thinning, with a little ice dust in it.
     */
    public static void coldMist(Vec3 at, float radius, float power) {
        int n = count(Math.round(4 + radius * 3 * power), at);
        for (int i = 0; i < n; i++) {
            float a = RNG.nextFloat() * Mth.TWO_PI;
            Vec3 out = new Vec3(Mth.cos(a), 0, Mth.sin(a));
            Vec3 v = out.scale(radius * (.025 + .03 * RNG.nextFloat())).add(0, .004, 0);
            mist(at.add(out.scale(radius * .2 * RNG.nextFloat())).add(0, .15, 0), v, .35f + .25f * radius * .3f, .025f, .16f + .08f * power, 40 + RNG.nextInt(30));
        }
        int d = count(Math.round(4 + radius * 2 * power), at);
        for (int i = 0; i < d; i++) {
            float a = RNG.nextFloat() * Mth.TWO_PI;
            crystalDust(at.add(Mth.cos(a) * radius * .3, .2 + .4 * RNG.nextFloat(), Mth.sin(a) * radius * .3),
                    new Vec3(Mth.cos(a) * .05, .015, Mth.sin(a) * .05), .016f + .016f * RNG.nextFloat(), (RNG.nextFloat() - .5f) * .5f, 20 + RNG.nextInt(20));
        }
    }
    /**
     * The air freezing behind something moving fast and cold (a weapon's edge, a spear's tip, a fist): a breath of vapour
     * and a few ice crystals left where it passed, dragged a little after it (vel), k = how much (0..1+).
     */
    public static void freezeTrail(Vec3 at, Vec3 vel, float k) {
        if (RNG.nextFloat() < .35f * k) mist(at, vel.scale(.08).add(0, .002, 0), .12f + .1f * k, .012f, .1f + .05f * k, 18 + RNG.nextInt(10));
        int n = count(Math.round(k * 2), at);
        for (int i = 0; i < n; i++) crystalDust(at.add(jitter(.05)), vel.scale(.15).add(jitter(.015)), .014f + .014f * RNG.nextFloat(), (RNG.nextFloat() - .5f) * .4f, 10 + RNG.nextInt(10));
    }

    /**
     * A big piece of ice breaking (the user's ten steps): a small crack, a deeper one, the heavy fracture zone by zone
     * (zones break one after another over spread ticks, not all at once: the first chunks fall, others hold a moment,
     * then the rest collapses), shards and frost dust thrown, cold vapour left behind, the shards landing (heard).
     * at = its middle, axis = its long way (zones are laid along it, length long), size = how big, push = where the
     * pieces are flung. The cracks drawn on the object itself are the caller's.
     */
    public static void breakApart(Vec3 at, Vec3 axis, float length, float size, Vec3 push, int zones, int spread, IceMesh.Mat mat) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 ax = axis.lengthSqr() < 1e-8 ? Vec3.ZERO : axis.normalize();
        sound(at, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_CRACK.get(), .7f, 1.4f);
        zones = Math.max(1, zones);
        int[] order = new int[zones];
        for (int i = 0; i < zones; i++) order[i] = i;
        for (int i = zones - 1; i > 0; i--) { int j = RNG.nextInt(i + 1), t = order[i]; order[i] = order[j]; order[j] = t; }
        for (int k = 0; k < zones; k++) {
            int z = order[k];
            Vec3 p = at.add(ax.scale(length * ((z + .5) / zones - .5)));
            int when = k == 0 ? 2 : 3 + (int) ((spread - 3) * (k / (float) Math.max(1, zones - 1)) * (.7 + .3 * RNG.nextFloat()));
            float piece = size / (float) Math.sqrt(zones) * (.8f + .5f * RNG.nextFloat());
            later(when, () -> {
                if (k0(z)) sound(p, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_SHATTER.get(), .9f, .85f + .3f * RNG.nextFloat());
                shatter(p, push.add(jitter(.04)), piece, mat);
                coldMist(p, .8f + piece, .6f);
            });
        }
        later(spread + 8, () -> sound(at, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_SHARD_RAIN.get(), .8f, 1f));
        later(spread + 14, () -> sound(at, com.FIRNI.superheromod.core.sound.ModSounds.ICEMAN_FROST_HISS.get(), .45f, 1f));
    }
    private static boolean k0(int z) { return z % 2 == 0; }
    private static void sound(Vec3 at, net.minecraft.sounds.SoundEvent ev, float vol, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.x, at.y, at.z, ev, net.minecraft.sounds.SoundSource.PLAYERS, vol, pitch, false);
    }
    /** Something to do a few ticks from now (the stages of a break). */
    public static void later(int ticks, Runnable r) { LATER.add(new Later(ticks, r)); }
    private static final class Later { int left; final Runnable r; Later(int left, Runnable r) { this.left = left; this.r = r; } }
    private static final List<Later> LATER = new ArrayList<>();

    // ------------------------------------------------------------------ living
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        if (mc.isPaused()) return;
        Level level = mc.level;
        for (int i = LATER.size() - 1; i >= 0; i--) {
            Later l = LATER.get(i);
            if (--l.left <= 0) { LATER.remove(i); l.r.run(); }
        }
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
                if (floor && Math.abs(s.vel.y) < .05) { s.ground = true; s.vel = Vec3.ZERO; s.pos = new Vec3(next.x, Math.floor(next.y) + 1.0 + s.size * .3, next.z); continue; }
            } else s.pos = next;
            s.vel = new Vec3(s.vel.x * .97, s.vel.y * .98 - s.grav, s.vel.z * .97);
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
            } else if (p.kind == MIST) {
                // Cold air: slowed, sinking a little, a slow turbulent roll (never a straight drift).
                float tn = (level.getGameTime() + p.age) * .09f;
                double wx = Math.sin(p.pos.z * 1.7 + tn) * .0035, wz = Math.cos(p.pos.x * 1.7 - tn) * .0035;
                p.vel = new Vec3(p.vel.x * .93 + wx, p.vel.y * .93 - .0008, p.vel.z * .93 + wz);
            } else if (p.kind == DUST) {
                // A tiny crystal: drag, hardly any weight, spiralling round its way when it spins.
                Vec3 v = p.vel;
                if (p.swirl != 0) {
                    float cs = Mth.cos(p.swirl), sn = Mth.sin(p.swirl);
                    v = new Vec3(v.x * cs - v.z * sn, v.y, v.x * sn + v.z * cs);
                }
                p.vel = new Vec3(v.x * .9, v.y * .92 - .0025, v.z * .9);
                if (solid(level, p.pos)) { p.vel = Vec3.ZERO; p.pos = p.prev; }
            }
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
                    double x = Mth.lerp(partial, s.prev.x, s.pos.x), y = Mth.lerp(partial, s.prev.y, s.pos.y), z = Mth.lerp(partial, s.prev.z, s.pos.z);
                    float melt = Mth.clamp((s.life - s.age - partial) / 14f, 0, 1);
                    float size = s.size * (.35f + .65f * melt) * .78f;
                    c.light = IceStage.light(s.pos);
                    float ang = Mth.lerp(partial, s.angleO, s.angle);
                    // A tumbling piece of broken ice (every one its own uneven shape), its texture riding with it.
                    axisAngle((float) s.axis.x, (float) s.axis.y, (float) s.axis.z, ang + s.seed % 7, AX);
                    c.ox = (float) x; c.oy = (float) y; c.oz = (float) z;
                    IceGrowth.chip(c, x, y, z, AX, size * 1.15f, s.seed, s.mat, .4f + .6f * melt);
                }
                c.ox = c.oy = c.oz = 0;
                st.endIce();
            }
            FilmContext f = st.fx();
            for (Puff p : PUFFS) {
                float k = (p.age + partial) / p.life;
                if (k > 1) continue;
                Vec3 at = p.prev.lerp(p.pos, partial);
                switch (p.kind) {
                    case SNOW -> FilmFx.puff(f, at, p.size, p.rgb, p.alpha * (1 - k * k));
                    case DUST -> {
                        // A glint: it catches the light now and then as it turns.
                        float tw = .55f + .45f * Mth.sin((p.age + partial) * 1.3f + p.rgb % 7);
                        FilmFx.glow(f, at, p.size * 1.6f, p.rgb, p.alpha * tw * (1 - k));
                    }
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
    // ------------------------------------------------------------------ block shapes (every ice effect)
    /** Scratch: a turned frame (u, v, w axes, 3 floats each) and a block's corners (render thread only). */
    static final float[] AX = new float[9];
    private static final float[] CORNERS = new float[24], RING = new float[12];

    /**
     * A six-faced block from its corners p (x, y, z each): 0..3 round one end, 4..7 round the other the same way round.
     * The six faces are flat quads (IceMesh mapped like a block: the texture on the side each face looks to).
     */
    static void hexa(IceMesh.Ctx c, float[] p, IceMesh.Mat mat, float a) {
        if (a <= .003f) return;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            IceMesh.quad(c, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], p[j * 3], p[j * 3 + 1], p[j * 3 + 2],
                    p[j * 3 + 12], p[j * 3 + 13], p[j * 3 + 14], p[i * 3 + 12], p[i * 3 + 13], p[i * 3 + 14], mat, a);
        }
        IceMesh.quad(c, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], mat, a);
        IceMesh.quad(c, p[12], p[13], p[14], p[15], p[16], p[17], p[18], p[19], p[20], p[21], p[22], p[23], mat, a);
    }
    /** A box centred on (x, y, z) along the frame r (u = r[0..2], v = r[3..5], w = r[6..8], unit), half-sizes hu, hv, hw. */
    static void obox(IceMesh.Ctx c, double x, double y, double z, float[] r, float hu, float hv, float hw, IceMesh.Mat mat, float a) {
        if (hu <= .0005f || hv <= .0005f || hw <= .0005f || a <= .003f) return;
        float[] p = CORNERS;
        for (int k = 0; k < 8; k++) {
            int q = k & 3;
            float su = q == 0 || q == 3 ? -hu : hu, sv = q < 2 ? -hv : hv, sw = k < 4 ? -hw : hw;
            p[k * 3] = (float) (x + r[0] * su + r[3] * sv + r[6] * sw);
            p[k * 3 + 1] = (float) (y + r[1] * su + r[4] * sv + r[7] * sw);
            p[k * 3 + 2] = (float) (z + r[2] * su + r[5] * sv + r[8] * sw);
        }
        hexa(c, p, mat, a);
    }
    /** A box centred on at along the unit axes u, v, w. */
    static void obox(IceMesh.Ctx c, Vec3 at, Vec3 u, Vec3 v, Vec3 w, float hu, float hv, float hw, IceMesh.Mat mat, float a) {
        float[] r = AX;
        r[0] = (float) u.x; r[1] = (float) u.y; r[2] = (float) u.z; r[3] = (float) v.x; r[4] = (float) v.y; r[5] = (float) v.z;
        r[6] = (float) w.x; r[7] = (float) w.y; r[8] = (float) w.z;
        obox(c, at.x, at.y, at.z, r, hu, hv, hw, mat, a);
    }
    /** A box of size (sx, sy, sz) centred on (x, y, z), turned by yaw (about up), pitch, roll (radians). */
    static void cube(IceMesh.Ctx c, double x, double y, double z, float sx, float sy, float sz, float yaw, float pitch, float roll, IceMesh.Mat mat, float a) {
        turn(yaw, pitch, roll, AX);
        obox(c, x, y, z, AX, sx * .5f, sy * .5f, sz * .5f, mat, a);
    }
    /**
     * The frame of a turn: roll about the forward axis, then pitch (positive tips the forward axis down), then yaw about
     * up (0 = forward along +z, positive toward +x). Into o: u, v, w (the turned x, y, z axes).
     */
    static float[] turn(float yaw, float pitch, float roll, float[] o) {
        float cy = Mth.cos(yaw), sy = Mth.sin(yaw), cp = Mth.cos(pitch), sp = Mth.sin(pitch), cr = Mth.cos(roll), sr = Mth.sin(roll);
        for (int i = 0; i < 3; i++) {
            float x = i == 0 ? cr : i == 1 ? -sr : 0, y = i == 0 ? sr : i == 1 ? cr : 0, z = i == 2 ? 1 : 0;
            float y2 = y * cp - z * sp, z2 = y * sp + z * cp;
            o[i * 3] = x * cy + z2 * sy;
            o[i * 3 + 1] = y2;
            o[i * 3 + 2] = -x * sy + z2 * cy;
        }
        return o;
    }
    /** The yaw and pitch that turn the forward axis (+z) onto the unit direction (dx, dy, dz) (see turn). */
    static float yawOf(double dx, double dz) { return (float) Math.atan2(dx, dz); }
    static float pitchOf(double dy) { return (float) -Math.asin(Mth.clamp(dy, -1, 1)); }
    /** The frame turned by angle about the unit axis (ax, ay, az), into o (Rodrigues). */
    static float[] axisAngle(float ax, float ay, float az, float angle, float[] o) {
        float c = Mth.cos(angle), s = Mth.sin(angle), t = 1 - c;
        o[0] = c + ax * ax * t; o[1] = ay * ax * t + az * s; o[2] = az * ax * t - ay * s;
        o[3] = ax * ay * t - az * s; o[4] = c + ay * ay * t; o[5] = az * ay * t + ax * s;
        o[6] = ax * az * t + ay * s; o[7] = ay * az * t - ax * s; o[8] = c + az * az * t;
        return o;
    }
    /**
     * A square ice spike (or icicle, pointing down): from base along the unit direction d, len long, half-width half at
     * its foot, turned by roll about its axis; `tiers` square steps (each narrower, like pointed dripstone), then a
     * four-sided point over the last part. 0 tiers: a plain square pyramid. Its foot is open (it stands in something).
     */
    static void spike(IceMesh.Ctx c, double bx, double by, double bz, double dx, double dy, double dz, float len, float half, float roll, int tiers,
                      IceMesh.Mat mat, float a) {
        spike(c, bx, by, bz, dx, dy, dz, len, half, roll, tiers, mat, a, true);
    }
    /** A square post: the spike's frame (the same roll lines up with it) as one flat-topped square prism (a broken spike's stump). */
    static void post(IceMesh.Ctx c, Vec3 base, Vec3 d, float len, float half, float roll, IceMesh.Mat mat, float a) {
        spike(c, base.x, base.y, base.z, d.x, d.y, d.z, len, half, roll, 1, mat, a, false);
    }
    private static void spike(IceMesh.Ctx c, double bx, double by, double bz, double dx, double dy, double dz, float len, float half, float roll, int tiers,
                              IceMesh.Mat mat, float a, boolean point) {
        if (len <= .002f || half <= .0005f || a <= .003f) return;
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-8) { dx = 0; dy = 1; dz = 0; } else { dx /= l; dy /= l; dz /= l; }
        // A frame round the axis: u = d x helper, v = d x u, turned by roll.
        double hx = Math.abs(dy) < .9 ? 0 : 1, hy = Math.abs(dy) < .9 ? 1 : 0;
        double ux = dy * 0 - dz * hy, uy = dz * hx - dx * 0, uz = dx * hy - dy * hx;
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        double vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        double cr = Math.cos(roll), sr = Math.sin(roll);
        double Ux = ux * cr + vx * sr, Uy = uy * cr + vy * sr, Uz = uz * cr + vz * sr;
        double Vx = vx * cr - ux * sr, Vy = vy * cr - uy * sr, Vz = vz * cr - uz * sr;
        float body = !point ? 1 : tiers <= 0 ? 0 : Math.min(.72f, .5f + .08f * tiers);
        float[] p = CORNERS;
        float s0 = 0;
        for (int t = 0; t < tiers; t++) {
            float s1 = body * len * (t + 1) / tiers, w = half * (1 - .26f * t);
            for (int k = 0; k < 8; k++) {
                int q = k & 3;
                double su = q == 0 || q == 3 ? -w : w, sv = q < 2 ? -w : w, sd = k < 4 ? s0 : s1;
                p[k * 3] = (float) (bx + Ux * su + Vx * sv + dx * sd);
                p[k * 3 + 1] = (float) (by + Uy * su + Vy * sv + dy * sd);
                p[k * 3 + 2] = (float) (bz + Uz * su + Vz * sv + dz * sd);
            }
            // The sides and the step's top (its foot stays open).
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) & 3;
                IceMesh.quad(c, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], p[j * 3], p[j * 3 + 1], p[j * 3 + 2],
                        p[j * 3 + 12], p[j * 3 + 13], p[j * 3 + 14], p[i * 3 + 12], p[i * 3 + 13], p[i * 3 + 14], mat, a);
            }
            IceMesh.quad(c, p[12], p[13], p[14], p[15], p[16], p[17], p[18], p[19], p[20], p[21], p[22], p[23], mat, a);
            s0 = s1;
        }
        if (!point) return;
        // The point: a square pyramid off the last step (a little narrower than it, so the step shows).
        float w = half * (tiers <= 0 ? 1 : (1 - .26f * (tiers - 1)) * .8f);
        float[] r = RING;
        for (int q = 0; q < 4; q++) {
            double su = q == 0 || q == 3 ? -w : w, sv = q < 2 ? -w : w;
            r[q * 3] = (float) (bx + Ux * su + Vx * sv + dx * s0);
            r[q * 3 + 1] = (float) (by + Uy * su + Vy * sv + dy * s0);
            r[q * 3 + 2] = (float) (bz + Uz * su + Vz * sv + dz * s0);
        }
        float tx = (float) (bx + dx * len), ty = (float) (by + dy * len), tz = (float) (bz + dz * len);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            IceMesh.tri(c, r[i * 3], r[i * 3 + 1], r[i * 3 + 2], r[j * 3], r[j * 3 + 1], r[j * 3 + 2], tx, ty, tz, mat, a);
        }
    }
    static void spike(IceMesh.Ctx c, Vec3 base, Vec3 d, float len, float half, float roll, int tiers, IceMesh.Mat mat, float a) {
        spike(c, base.x, base.y, base.z, d.x, d.y, d.z, len, half, roll, tiers, mat, a);
    }

    /** v turned about the unit axis by angle (Rodrigues). */
    static Vec3 rotate(Vec3 v, Vec3 axis, float angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return v.scale(c).add(axis.cross(v).scale(s)).add(axis.scale(axis.dot(v) * (1 - c)));
    }
    public static void clear() { SHARDS.clear(); PUFFS.clear(); RINGS.clear(); LATER.clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}
