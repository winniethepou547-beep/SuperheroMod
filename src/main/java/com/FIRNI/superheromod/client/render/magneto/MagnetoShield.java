package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.thor.ThorBolts;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.PLATE_SPIN;
import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.SHIELD_RAISE_TICKS;

/**
 * Magneto's shield as Marvel Rivals draws it: a violet sphere of force round him (glassy, bright at its rim, scan bands
 * drifting over it), heavy iron plates torn up out of the ground circling it, and a light electricity crawling over the
 * surface and leaping between the plates. Where a blow or a projectile lands, the sphere ripples out from that spot and
 * arcs of lightning crackle away from it across the surface. When it bursts, the sphere pops (the plates become the
 * pieces the server throws, from the same places: PLATE_SPIN and the heights match MagnetoController.column).
 */
final class MagnetoShield {
    private MagnetoShield() {}

    /** How high each plate hangs over his feet (they go round at MagnetoAction.PLATE_SPIN). */
    static float plateHeight(int i) { return .55f + (i % 2) * .95f + .2f * MetalMesh.hash(i * 7 + 3); }

    private static final int CORE = 0xf4ecff, ARC = 0xb784ff, RIM = 0xc9a6ff, BODY = 0x7c46e6, TINT = 0x3a1a6e;
    private record Hit(Vec3 dir, float time) {}
    private static final Map<Integer, List<Hit>> HITS = new HashMap<>();
    /** Bursts: owner -> {time, radius, x, y, z}. */
    private static final Map<Integer, float[]> POPS = new HashMap<>();
    /** The sphere's unit directions, computed once (rings x segments). */
    private static final int RINGS = 16, SEGS = 28;
    private static final float[][] UNIT = new float[(RINGS + 1) * (SEGS + 1)][3];
    static {
        for (int i = 0; i <= RINGS; i++) for (int j = 0; j <= SEGS; j++) {
            double lat = Math.PI * i / RINGS - Math.PI / 2, lon = Math.PI * 2 * j / SEGS;
            float[] u = UNIT[i * (SEGS + 1) + j];
            u[0] = (float) (Math.cos(lat) * Math.cos(lon)); u[1] = (float) Math.sin(lat); u[2] = (float) (Math.cos(lat) * Math.sin(lon));
        }
    }

    static Vec3 centre(Vec3 feet) { return feet.add(0, 1.05, 0); }
    /** The sphere's radius as it rises (a quick swell with a little overshoot) and breathes. */
    static float radius(float r, float age, float time) {
        float k = Mth.clamp(age / SHIELD_RAISE_TICKS, 0, 1);
        float swell = k < 1 ? 1 - (1 - k) * (1 - k) * (1 - k) + .12f * Mth.sin(k * Mth.PI) : 1;
        return r * (.15f + .85f * swell) + .03f * Mth.sin(time * .21f);
    }
    /** Where plate i is (its centre) round him at this age. */
    static Vec3 plate(Vec3 feet, float age, int i, int n, float r, float time) {
        float a = Mth.TWO_PI * i / n + age * PLATE_SPIN;
        float rise = Mth.clamp((age - i * .8f) / (SHIELD_RAISE_TICKS + 2), 0, 1);
        rise = 1 - (1 - rise) * (1 - rise) * (1 - rise);
        float ring = r + .45f, y = Mth.lerp(rise, -1.2f, plateHeight(i)) + .08f * Mth.sin(time * .07f + i * 1.7f);
        return feet.add(Mth.cos(a) * ring, y, Mth.sin(a) * ring);
    }

    static void hit(int owner, Vec3 at, Vec3 feet, float time) {
        Vec3 d = at.subtract(centre(feet));
        if (d.lengthSqr() < 1e-4) d = new Vec3(1, 0, 0);
        List<Hit> list = HITS.computeIfAbsent(owner, k -> new ArrayList<>());
        list.add(new Hit(d.normalize(), time));
        if (list.size() > 6) list.remove(0);
    }
    static void burst(int owner, Vec3 feet, float r, float time) {
        POPS.put(owner, new float[]{time, r, (float) feet.x, (float) feet.y, (float) feet.z});
        HITS.remove(owner);
    }
    static void clear() { HITS.clear(); POPS.clear(); }

    // ------------------------------------------------------------------ the plates (solid pass)
    static void plates(PoseStack p, VertexConsumer v, int light, Vec3 cam, Vec3 feet, int owner, float age, int n, float r, float time) {
        for (int i = 0; i < n; i++) {
            Vec3 c = plate(feet, age, i, n, r, time);
            float a = Mth.TWO_PI * i / n + age * PLATE_SPIN;
            float settle = Mth.clamp((age - i * .8f) / (SHIELD_RAISE_TICKS + 2), 0, 1);
            p.pushPose();
            p.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
            // Facing out from him, leaning back a little, tumbling into place as they come up out of the ground.
            p.mulPose(Axis.YP.rotation(Mth.HALF_PI - a));
            p.mulPose(Axis.XP.rotation(-.18f + (1 - settle) * 2.2f + .04f * Mth.sin(time * .09f + i)));
            p.mulPose(Axis.ZP.rotation((1 - settle) * 1.4f * (i % 2 == 0 ? 1 : -1) + .05f * Mth.sin(time * .05f + i * 2)));
            MetalMesh.plate(p, v, light, .95f + .25f * MetalMesh.hash(owner + i), 1.25f + .35f * MetalMesh.hash(owner + i * 3), owner * 7 + i);
            p.popPose();
        }
    }

    // ------------------------------------------------------------------ the sphere and its lightning (light pass)
    static void light(FilmContext c, Vec3 feet, int owner, float age, int n, float r, float time) {
        Vec3 centre = centre(feet);
        float rad = radius(r, age, time);
        List<Hit> hits = HITS.get(owner);
        if (hits != null) hits.removeIf(h -> time - h.time() > 24);
        sphere(c, centre, rad, time, hits, 1);
        // Light electricity: a few short arcs crawling over the surface, a new set every few ticks.
        int slot = (int) Math.floor(time / 3);
        for (int k = 0; k < 3; k++) {
            int seed = owner * 131 + slot * 17 + k * 7;
            if (MetalMesh.hash(seed) < .35f) continue;
            Vec3 a = onSphere(seed, centre, rad), b = a.add(tangent(a.subtract(centre), seed).scale(rad * (.5 + .4 * MetalMesh.hash(seed + 5)))).subtract(centre).normalize().scale(rad).add(centre);
            float life = 1 - (time / 3 - slot);
            arc(c, a, b, seed + (int) (time * 2), .55f * life, .9);
        }
        // Now and then a bolt leaps from a plate to the sphere.
        if (n > 0 && MetalMesh.hash(owner + slot * 3) > .55f) {
            int i = (int) (MetalMesh.hash(owner * 3 + slot) * n) % n;
            Vec3 pl = plate(feet, age, i, n, r, time);
            Vec3 to = pl.subtract(centre).normalize().scale(rad).add(centre);
            arc(c, pl, to, owner + slot * 5 + (int) (time * 2), .6f * (1 - (time / 3 - slot)), .6);
        }
        // Where it was struck: lightning racing away across the surface from the spot, a flash on it.
        if (hits != null) for (Hit h : hits) {
            float since = time - h.time();
            if (since > 10) continue;
            Vec3 at = centre.add(h.dir().scale(rad));
            float fade = 1 - since / 10;
            FilmFx.glow(c, at, .9 + since * .08, RIM, .8f * fade);
            FilmFx.glow(c, at, .35, CORE, fade);
            for (int k = 0; k < 5; k++) {
                int seed = (int) (h.time() * 13) + k * 29;
                Vec3 end = h.dir().scale(rad).add(tangent(h.dir(), seed).scale(rad * (.55 + .5 * MetalMesh.hash(seed + 1)) * Math.min(1, since / 3 + .4)));
                end = end.normalize().scale(rad).add(centre);
                arc(c, at, end, seed + (int) (time * 3), fade, 1.2);
            }
        }
    }
    /** The burst: the sphere snaps outward and is gone. */
    static void pops(FilmContext c, float time) {
        POPS.entrySet().removeIf(e -> time - e.getValue()[0] > 7);
        for (float[] pop : POPS.values()) {
            float k = (time - pop[0]) / 7;
            sphere(c, new Vec3(pop[2], pop[3] + 1.05, pop[4]), pop[1] * (1 + 1.3f * k), time, null, (1 - k) * (1 - k));
        }
    }

    private static Vec3 onSphere(int seed, Vec3 centre, float rad) {
        double a = MetalMesh.hash(seed + 1) * Math.PI * 2, y = MetalMesh.hash(seed + 2) * 1.6 - .6;
        double flat = Math.sqrt(Math.max(0, 1 - y * y));
        return centre.add(Math.cos(a) * flat * rad, y * rad, Math.sin(a) * flat * rad);
    }
    /** A direction along the surface at a point (unit), turned by the seed. */
    private static Vec3 tangent(Vec3 normal, int seed) {
        Vec3 n = normal.normalize();
        Vec3 side = Math.abs(n.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 t1 = n.cross(side).normalize(), t2 = n.cross(t1);
        double a = MetalMesh.hash(seed * 3 + 11) * Math.PI * 2;
        return t1.scale(Math.cos(a)).add(t2.scale(Math.sin(a)));
    }
    private static void arc(FilmContext c, Vec3 a, Vec3 b, int seed, float alpha, double width) {
        if (alpha <= .02f) return;
        for (ThorBolts.Seg s : ThorBolts.bolt(a, b, seed, .16, .35, 1)) {
            FilmFx.streak(c, s.a(), s.b(), .018 * width * s.width(), CORE, alpha, alpha, true);
            FilmFx.streak(c, s.a(), s.b(), .1 * width * s.width(), ARC, alpha * .45f, alpha * .45f, true);
        }
    }
    /**
     * The sphere: a faint violet tint over what is behind it, and light that gathers at its rim (where you look along
     * its surface), scan bands drifting up it, the ripples of the blows it took.
     */
    private static void sphere(FilmContext c, Vec3 centre, float rad, float time, List<Hit> hits, float strength) {
        if (strength <= .01f || rad <= .05f) return;
        Matrix4f m = c.pose().last().pose();
        Vec3 cam = c.camera();
        int count = UNIT.length;
        float[] alpha = SCRATCH_A.length >= count ? SCRATCH_A : (SCRATCH_A = new float[count]);
        float[] white = SCRATCH_W.length >= count ? SCRATCH_W : (SCRATCH_W = new float[count]);
        for (int i = 0; i < count; i++) {
            float[] u = UNIT[i];
            double px = centre.x + u[0] * rad, py = centre.y + u[1] * rad, pz = centre.z + u[2] * rad;
            double tx = cam.x - px, ty = cam.y - py, tz = cam.z - pz, len = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1e-6;
            float facing = (float) Math.abs((u[0] * tx + u[1] * ty + u[2] * tz) / len);
            float fres = 1 - facing;
            float band = (float) Math.pow(Math.max(0, Mth.sin((u[1] * 2.2f - time * .045f) * Mth.PI)), 10) * .25f;
            float ripple = 0, flash = 0;
            if (hits != null) for (Hit h : hits) {
                float since = time - h.time();
                double dot = u[0] * h.dir().x + u[1] * h.dir().y + u[2] * h.dir().z;
                float ang = (float) Math.acos(Mth.clamp((float) dot, -1, 1));
                float front = since * .14f, d = (ang - front) / .16f;
                ripple += (float) Math.exp(-d * d) * Math.max(0, 1 - since / 22);
                flash += (float) Math.exp(-(ang * ang) / .08f) * Math.max(0, 1 - since / 8);
            }
            alpha[i] = Math.min(1, (.05f + .62f * fres * fres * fres + band + .7f * ripple + flash) * strength);
            white[i] = Math.min(1, fres * fres * .6f + ripple * .5f + flash);
        }
        VertexConsumer soft = c.buffers().getBuffer(FilmFx.SOFT);
        for (int i = 0; i < RINGS; i++) for (int j = 0; j < SEGS; j++) {
            int a = i * (SEGS + 1) + j, b = a + 1, d = a + SEGS + 1, e = d + 1;
            tint(soft, m, centre, rad, a, alpha); tint(soft, m, centre, rad, b, alpha); tint(soft, m, centre, rad, e, alpha); tint(soft, m, centre, rad, d, alpha);
        }
        VertexConsumer add = c.buffers().getBuffer(FilmFx.ADD);
        for (int i = 0; i < RINGS; i++) for (int j = 0; j < SEGS; j++) {
            int a = i * (SEGS + 1) + j, b = a + 1, d = a + SEGS + 1, e = d + 1;
            glowVertex(add, m, centre, rad, a, alpha, white); glowVertex(add, m, centre, rad, b, alpha, white);
            glowVertex(add, m, centre, rad, e, alpha, white); glowVertex(add, m, centre, rad, d, alpha, white);
        }
    }
    private static float[] SCRATCH_A = new float[0], SCRATCH_W = new float[0];
    private static void tint(VertexConsumer v, Matrix4f m, Vec3 centre, float rad, int i, float[] alpha) {
        float[] u = UNIT[i];
        v.vertex(m, (float) (centre.x + u[0] * rad), (float) (centre.y + u[1] * rad), (float) (centre.z + u[2] * rad))
                .color((TINT >> 16 & 255) / 255f, (TINT >> 8 & 255) / 255f, (TINT & 255) / 255f, Math.min(.35f, alpha[i] * .45f)).endVertex();
    }
    private static void glowVertex(VertexConsumer v, Matrix4f m, Vec3 centre, float rad, int i, float[] alpha, float[] white) {
        float[] u = UNIT[i];
        float w = white[i];
        float r = Mth.lerp(w, (BODY >> 16 & 255) / 255f, 1), g = Mth.lerp(w, (BODY >> 8 & 255) / 255f, .95f), b = Mth.lerp(w, (BODY & 255) / 255f, 1);
        v.vertex(m, (float) (centre.x + u[0] * rad), (float) (centre.y + u[1] * rad), (float) (centre.z + u[2] * rad)).color(r, g, b, alpha[i]).endVertex();
    }
}
