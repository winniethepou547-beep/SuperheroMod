package com.FIRNI.superheromod.client.render.zed;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Zed's living shadow as matter in the air: not round puffs but wisps that keep the speed they were
 * born with and lose it slowly (so when he turns, his shadow carries on the old way for a moment, bends
 * and tears), that curl in a turbulent drift, stretch along their motion, split into smaller wisps,
 * thin out and dissolve, each at its own time. Three shapes: irregular masses with soft or torn edges,
 * tapering tendrils that draw the curve they flew, and thin torn sheets. Darkest and densest when new
 * and close to him, more see-through, ragged and soft as they spread. Wisps told to sink fall to the
 * ground, flatten and spread on it, then draw back into it. Lights placed in the smoke (his eyes, a
 * spark, the red flash) tint the thin smoke round them; the dense heart stays black.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ShadowSmoke {
    public static final int BLOB = 0, TENDRIL = 1, SHEET = 2;
    public static final int MATTER = 0x07050a;
    private static final int TRAIL = 6, MAX = 1600;

    public static final class Wisp {
        double x, y, z, ox, oy, oz;
        public double vx, vy, vz;
        float age;
        public float life, size, grow = .9f, alpha, drag = .9f, turbulence = .006f, rise;
        public int shape, tint = MATTER;
        final float seed;
        /** Drawn round a centre: pulled toward it and swept round it (a storm), its height kept near hold. */
        public Vec3 centre; public float pull, swirl; public double hold = Double.NaN;
        /** Falling to the ground at this height, to spread on it and draw back into it. */
        public boolean sink; public double ground = Double.NaN;
        boolean puddle, split; float puddleAge;
        final double[] hx = new double[TRAIL], hy = new double[TRAIL], hz = new double[TRAIL];
        int hn;
        Wisp(float seed) { this.seed = seed; }
        public Vec3 pos() { return new Vec3(x, y, z); }
    }
    private record Light(Vec3 at, int rgb, float power, float radius) {}

    private static final List<Wisp> WISPS = new ArrayList<>();
    private static final List<Light> LIGHTS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    /** How fast the smoke lives (a film's held breath slows it). */
    public static float timeScale = 1;
    private static float clock;

    private ShadowSmoke() {}

    public static Random random() { return RANDOM; }
    public static int count() { return WISPS.size(); }
    public static List<Wisp> all() { return WISPS; }

    /** A new wisp at a place, moving, of a size (blocks), lasting so many ticks, this dense, of this shape. */
    public static Wisp add(Vec3 at, Vec3 velocity, float size, float life, float alpha, int shape) {
        Wisp w = new Wisp(RANDOM.nextFloat() * 100);
        w.x = w.ox = at.x; w.y = w.oy = at.y; w.z = w.oz = at.z;
        w.vx = velocity.x; w.vy = velocity.y; w.vz = velocity.z;
        w.size = size; w.life = Math.max(2, life); w.alpha = alpha; w.shape = shape;
        for (int i = 0; i < TRAIL; i++) { w.hx[i] = at.x; w.hy[i] = at.y; w.hz[i] = at.z; }
        WISPS.add(w);
        if (WISPS.size() > MAX) WISPS.remove(0);
        return w;
    }
    /** A spray of wisps from a place: count of them, spread (blocks), inheriting a velocity, jittered. */
    public static void burst(Vec3 at, int count, double spread, Vec3 inherit, double jitter, float size, float life, float alpha) {
        for (int i = 0; i < count; i++) {
            Vec3 off = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * .7, RANDOM.nextGaussian()).scale(spread * .5);
            Vec3 v = inherit.add(off.normalize().scale(jitter * (.4 + RANDOM.nextDouble())));
            int shape = RANDOM.nextFloat() < .3f ? TENDRIL : RANDOM.nextFloat() < .2f ? SHEET : BLOB;
            add(at.add(off), v, size * (.6f + RANDOM.nextFloat() * .8f), life * (.6f + RANDOM.nextFloat() * .8f), alpha * (.7f + RANDOM.nextFloat() * .3f), shape);
        }
    }
    /** A light inside the smoke for this frame only. */
    public static void light(Vec3 at, int rgb, float power, float radius) { if (power > .01f) LIGHTS.add(new Light(at, rgb, power, radius)); }
    public static void clear() { WISPS.clear(); LIGHTS.clear(); }

    // ------------------------------------------------------------------ life
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        if (mc.isPaused()) return;
        float k = timeScale;
        clock += k;
        List<Wisp> born = new ArrayList<>();
        for (int i = WISPS.size() - 1; i >= 0; i--) {
            Wisp w = WISPS.get(i);
            w.ox = w.x; w.oy = w.y; w.oz = w.z;
            if (w.puddle) {
                w.puddleAge += k;
                if (w.puddleAge > 16) WISPS.remove(i);
                continue;
            }
            w.age += k;
            if (w.age >= w.life) { WISPS.remove(i); continue; }
            double drag = Math.pow(w.drag, k);
            w.vx *= drag; w.vy *= drag; w.vz *= drag;
            // A slow curling drift; never the same for two wisps.
            double t = w.turbulence * k;
            w.vx += Math.sin(w.y * 1.7 + clock * .21 + w.seed * 13) * t;
            w.vy += Math.sin(w.z * 1.3 + clock * .17 + w.seed * 7) * t * .6 + w.rise * k;
            w.vz += Math.sin(w.x * 1.5 + clock * .19 + w.seed * 3) * t;
            if (w.centre != null) {
                double dx = w.centre.x - w.x, dz = w.centre.z - w.z, d = Math.max(.2, Math.sqrt(dx * dx + dz * dz));
                w.vx += (dx * w.pull + -dz / d * w.swirl) * k;
                w.vz += (dz * w.pull + dx / d * w.swirl) * k;
                if (!Double.isNaN(w.hold)) w.vy += (w.hold - w.y) * .02 * k;
            }
            if (w.sink) {
                // Drawn down, faster and faster, as if the ground were taking it back.
                w.vy -= .022 * k;
                if (w.vy < -.45) w.vy = -.45;
            }
            w.x += w.vx * k; w.y += w.vy * k; w.z += w.vz * k;
            if (w.sink && !Double.isNaN(w.ground) && w.y <= w.ground + .04) {
                w.y = w.ground + .02; w.puddle = true; w.puddleAge = 0;
                continue;
            }
            // Its path, a point a tick, for the tendrils.
            System.arraycopy(w.hx, 0, w.hx, 1, TRAIL - 1); System.arraycopy(w.hy, 0, w.hy, 1, TRAIL - 1); System.arraycopy(w.hz, 0, w.hz, 1, TRAIL - 1);
            w.hx[0] = w.x; w.hy[0] = w.y; w.hz[0] = w.z;
            if (w.hn < TRAIL) w.hn++;
            // Big masses tear into smaller wisps partway through their lives.
            if (!w.split && w.size > .4f && w.age > w.life * .4f) {
                w.split = true;
                for (int s = 0; s < 2; s++) {
                    Wisp c = new Wisp(RANDOM.nextFloat() * 100);
                    c.x = c.ox = w.x; c.y = c.oy = w.y; c.z = c.oz = w.z;
                    double side = s == 0 ? 1 : -1;
                    c.vx = w.vx * .9 + -w.vz * .25 * side + RANDOM.nextGaussian() * .01; c.vz = w.vz * .9 + w.vx * .25 * side + RANDOM.nextGaussian() * .01;
                    c.vy = w.vy * .9 + RANDOM.nextGaussian() * .01;
                    c.size = w.size * .55f; c.life = (w.life - w.age) * (.8f + RANDOM.nextFloat() * .5f) + 4; c.alpha = w.alpha * .8f;
                    c.shape = RANDOM.nextBoolean() ? TENDRIL : BLOB; c.tint = w.tint; c.drag = w.drag; c.turbulence = w.turbulence * 1.4f; c.rise = w.rise;
                    c.centre = w.centre; c.pull = w.pull; c.swirl = w.swirl; c.hold = w.hold; c.sink = w.sink; c.ground = w.ground;
                    for (int h = 0; h < TRAIL; h++) { c.hx[h] = w.x; c.hy[h] = w.y; c.hz[h] = w.z; }
                    born.add(c);
                }
                w.size *= .8f;
            }
        }
        WISPS.addAll(born);
        while (WISPS.size() > MAX) WISPS.remove(0);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }

    // ------------------------------------------------------------------ drawing
    private static float hash(float n) { double v = Math.sin(n * 12.9898) * 43758.5453; return (float) (v - Math.floor(v)); }
    private static void put(VertexConsumer v, Matrix4f m, double x, double y, double z, float r, float g, float b, float a) {
        v.vertex(m, (float) x, (float) y, (float) z).color(r, g, b, Math.max(0, Math.min(1, a))).endVertex();
    }
    /** How dense a wisp is right now: in fast, holding, out slowly. */
    private static float density(Wisp w) {
        if (w.puddle) return w.alpha * (1 - FilmFx.ease((w.puddleAge - 4) / 12f));
        float k = w.age / w.life;
        return w.alpha * FilmFx.ease(k / .12f) * (1 - FilmFx.ease((k - .5f) / .5f));
    }
    /** The colour of a wisp: its own dark, warmed toward a light by how near it is and how thin. */
    private static int colour(Wisp w, Vec3 at, float a) {
        int c = w.tint;
        for (Light l : LIGHTS) {
            double d = at.distanceTo(l.at);
            if (d >= l.radius) continue;
            double near = 1 - d / l.radius;
            float f = (float) (l.power * near * near) * (1 - a * .5f);
            c = mix(c, l.rgb, Math.min(1, f));
        }
        return c;
    }
    static int mix(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t), g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t),
                bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    /**
     * Draws every wisp (the FilmContext pose is world space minus the camera). Lights given this frame
     * tint the smoke and are then forgotten. scale > 0 also lets light glow through the thin smoke near
     * the lights (the red flash inside the storm).
     */
    public static void render(FilmContext c, float partial, float glowThrough) {
        if (WISPS.isEmpty()) { LIGHTS.clear(); return; }
        VertexConsumer v = c.buffers().getBuffer(FilmFx.SOFT);
        Matrix4f m = c.pose().last().pose();
        Vec3 right = c.viewRight(), up = c.viewUp();
        for (Wisp w : WISPS) {
            float a = density(w);
            if (a <= .004f) continue;
            double x = w.ox + (w.x - w.ox) * partial, y = w.oy + (w.y - w.oy) * partial, z = w.oz + (w.z - w.oz) * partial;
            Vec3 at = new Vec3(x, y, z);
            int rgb = colour(w, at, a);
            float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
            float k = w.puddle ? 1 : w.age / w.life;
            float size = w.size * (1 + w.grow * k);
            if (w.puddle) { puddle(v, m, w, x, y, z, size, r, g, b, a); continue; }
            if (w.shape == TENDRIL && w.hn > 1) tendril(v, m, c, w, x, y, z, size, r, g, b, a);
            mass(v, m, w, x, y, z, right, up, w.shape == TENDRIL ? size * .55f : size, r, g, b, w.shape == TENDRIL ? a * .8f : a, w.shape == SHEET);
        }
        if (glowThrough > 0 && !LIGHTS.isEmpty()) {
            // Light passing through the thinner smoke round a light, as if from inside it.
            for (Wisp w : WISPS) {
                float a = density(w);
                if (a <= .02f || w.puddle) continue;
                double x = w.ox + (w.x - w.ox) * partial, y = w.oy + (w.y - w.oy) * partial, z = w.oz + (w.z - w.oz) * partial;
                Vec3 at = new Vec3(x, y, z);
                for (Light l : LIGHTS) {
                    double d = at.distanceTo(l.at) / l.radius;
                    if (d >= 1 || l.radius < 1.5) continue;
                    // Brightest in the middle layer: the heart is too dense, the outside too far.
                    float f = (float) (l.power * Math.sin(Math.PI * Math.min(1, d * 1.15)) * (1 - d)) * glowThrough * (1.1f - a);
                    if (f > .02f) FilmFx.glow(c, at, w.size * (1.4 + .8 * (w.age / w.life)), l.rgb, Math.min(.5f, f * .45f));
                }
            }
        }
        LIGHTS.clear();
    }
    /** An irregular mass facing the camera, stretched along its motion, its rim soft or torn. */
    private static void mass(VertexConsumer v, Matrix4f m, Wisp w, double x, double y, double z, Vec3 right, Vec3 up, float size,
                             float r, float g, float b, float a, boolean sheet) {
        int n = 9;
        // Its motion as seen on screen: the mass is drawn out along it.
        double vr = w.vx * right.x + w.vy * right.y + w.vz * right.z, vu = w.vx * up.x + w.vy * up.y + w.vz * up.z;
        double speed = Math.sqrt(vr * vr + vu * vu);
        double ax = speed > 1e-4 ? vr / speed : 1, ay = speed > 1e-4 ? vu / speed : 0;
        double stretch = 1 + Math.min(sheet ? 3.5 : 2.2, speed * (sheet ? 14 : 9));
        boolean torn = hash(w.seed) < .35f || sheet;
        float rough = sheet ? .55f : torn ? .45f : .32f;
        float spin = w.seed + w.age * (hash(w.seed + 3) - .5f) * .06f;
        double[] px = new double[n + 1], py = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            int j = i % n;
            double ang = Math.PI * 2 * j / n + spin;
            float wobble = .5f + .5f * (float) Math.sin(w.seed * 7 + j * 2.4f + w.age * .09f * (1 + j % 3));
            double rad = size * (1 - rough + rough * wobble);
            double lx = Math.cos(ang) * rad, ly = Math.sin(ang) * rad;
            // Stretch along the motion.
            double along = lx * ax + ly * ay, across = -lx * ay + ly * ax;
            along *= stretch;
            px[i] = along * ax - across * ay; py[i] = along * ay + across * ax;
        }
        float rimAlpha = torn ? a * .3f : 0, core = sheet ? a * .55f : a;
        double inner = .45;
        for (int i = 0; i < n; i++) {
            double x0 = px[i], y0 = py[i], x1 = px[i + 1], y1 = py[i + 1];
            // Dense core to the inner ring...
            put(v, m, x, y, z, r, g, b, core); put(v, m, x, y, z, r, g, b, core);
            put(v, m, x + (right.x * x1 + up.x * y1) * inner, y + (right.y * x1 + up.y * y1) * inner, z + (right.z * x1 + up.z * y1) * inner, r, g, b, core * .8f);
            put(v, m, x + (right.x * x0 + up.x * y0) * inner, y + (right.y * x0 + up.y * y0) * inner, z + (right.z * x0 + up.z * y0) * inner, r, g, b, core * .8f);
            // ...then out to the rim, soft or with a torn edge.
            put(v, m, x + (right.x * x0 + up.x * y0) * inner, y + (right.y * x0 + up.y * y0) * inner, z + (right.z * x0 + up.z * y0) * inner, r, g, b, core * .8f);
            put(v, m, x + (right.x * x1 + up.x * y1) * inner, y + (right.y * x1 + up.y * y1) * inner, z + (right.z * x1 + up.z * y1) * inner, r, g, b, core * .8f);
            put(v, m, x + right.x * x1 + up.x * y1, y + right.y * x1 + up.y * y1, z + right.z * x1 + up.z * y1, r, g, b, rimAlpha);
            put(v, m, x + right.x * x0 + up.x * y0, y + right.y * x0 + up.y * y0, z + right.z * x0 + up.z * y0, r, g, b, rimAlpha);
            if (torn) {
                double f = 1.18;
                put(v, m, x + right.x * x0 + up.x * y0, y + right.y * x0 + up.y * y0, z + right.z * x0 + up.z * y0, r, g, b, rimAlpha);
                put(v, m, x + right.x * x1 + up.x * y1, y + right.y * x1 + up.y * y1, z + right.z * x1 + up.z * y1, r, g, b, rimAlpha);
                put(v, m, x + (right.x * x1 + up.x * y1) * f, y + (right.y * x1 + up.y * y1) * f, z + (right.z * x1 + up.z * y1) * f, r, g, b, 0);
                put(v, m, x + (right.x * x0 + up.x * y0) * f, y + (right.y * x0 + up.y * y0) * f, z + (right.z * x0 + up.z * y0) * f, r, g, b, 0);
            }
        }
    }
    /** A tapering tendril through the last few places the wisp passed: it keeps the curve it flew. */
    private static void tendril(VertexConsumer v, Matrix4f m, FilmContext c, Wisp w, double x, double y, double z, float size,
                                float r, float g, float b, float a) {
        int count = w.hn;
        Vec3 prev = new Vec3(x, y, z);
        for (int i = 1; i < count; i++) {
            Vec3 next = new Vec3(w.hx[i], w.hy[i], w.hz[i]);
            Vec3 d = next.subtract(prev), side = d.cross(c.camera().subtract(prev));
            if (side.lengthSqr() < 1e-10) { prev = next; continue; }
            side = side.normalize();
            float k0 = (i - 1) / (float) (count - 1), k1 = i / (float) (count - 1);
            double w0 = size * .5 * (1 - k0), w1 = size * .5 * (1 - k1);
            float a0 = a * (1 - k0) * .85f, a1 = a * (1 - k1) * .85f;
            put(v, m, prev.x + side.x * w0, prev.y + side.y * w0, prev.z + side.z * w0, r, g, b, a0 * .4f);
            put(v, m, next.x + side.x * w1, next.y + side.y * w1, next.z + side.z * w1, r, g, b, a1 * .4f);
            put(v, m, next.x - side.x * w1, next.y - side.y * w1, next.z - side.z * w1, r, g, b, a1 * .4f);
            put(v, m, prev.x - side.x * w0, prev.y - side.y * w0, prev.z - side.z * w0, r, g, b, a0 * .4f);
            put(v, m, prev.x + side.x * w0 * .45, prev.y + side.y * w0 * .45, prev.z + side.z * w0 * .45, r, g, b, a0);
            put(v, m, next.x + side.x * w1 * .45, next.y + side.y * w1 * .45, next.z + side.z * w1 * .45, r, g, b, a1);
            put(v, m, next.x - side.x * w1 * .45, next.y - side.y * w1 * .45, next.z - side.z * w1 * .45, r, g, b, a1);
            put(v, m, prev.x - side.x * w0 * .45, prev.y - side.y * w0 * .45, prev.z - side.z * w0 * .45, r, g, b, a0);
            prev = next;
        }
    }
    /** Smoke that has reached the ground: flat, spreading for a moment, then drawn back into it. */
    private static void puddle(VertexConsumer v, Matrix4f m, Wisp w, double x, double y, double z, float size, float r, float g, float b, float a) {
        float spread = 1 + .9f * FilmFx.ease(w.puddleAge / 5f), shrink = 1 - FilmFx.ease((w.puddleAge - 5) / 11f);
        double rad = size * 1.3 * spread * shrink;
        if (rad < .01) return;
        int n = 10;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n + w.seed, a1 = Math.PI * 2 * (i + 1) / n + w.seed;
            double r0 = rad * (.75 + .25 * Math.sin(w.seed * 5 + i * 1.9)), r1 = rad * (.75 + .25 * Math.sin(w.seed * 5 + (i + 1) * 1.9));
            put(v, m, x, y, z, r, g, b, a); put(v, m, x, y, z, r, g, b, a);
            put(v, m, x + Math.cos(a1) * r1, y, z + Math.sin(a1) * r1, r, g, b, 0);
            put(v, m, x + Math.cos(a0) * r0, y, z + Math.sin(a0) * r0, r, g, b, 0);
        }
    }
}
