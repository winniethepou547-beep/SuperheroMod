package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanConfig;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Q, the cryogenic shell, as everyone sees it: an organic cocoon of real ice, not a box or a bubble.
 * <ul>
 * <li>The cocoon (drawn here round him, in his body's frame): overlapping crystalline plates laid over an egg shape round
 * the crouching body (two layers, every plate an uneven faceted lump of its own size, turn and ice: mostly blue, some
 * deeper, a little milky frost), crystals jutting out of the shoulders, the back and the shins, clusters of crystals
 * grown out of the ground round his feet. Under it, on his body, a thin inner layer of glacier ice (body(), IcemanBody's
 * SHELL_* hooks) fills the gaps.</li>
 * <li>Forming: the air round him freezes first (ice crystals drawn in spiralling, cold mist creeping up), then the ice
 * grows from the ground up: the foot clusters, the plates of the legs, the torso, the arms, the head last, every piece
 * from a nucleus with its own delay; when it closes, cold air rolls out low over the ground.</li>
 * <li>A blow (FX_SHELL_HIT): a crack network spreads over the cocoon's real surface from where it landed (over a few
 * ticks, longer, wider and more branched the harder the blow), flashes and stays as white fracture lines while the
 * shell holds; a heavy blow knocks the plates there off (a clean bright break left), very heavy ones more; chips, frost
 * dust, a breath of mist; he shakes with the heavier ones.</li>
 * <li>Broken (FX_SHELL_BREAK): glowing cracks run over all of it, then the cocoon comes apart zone by zone (each zone's
 * plates detach a moment after the last), the pieces tumble down and land round him, cold mist escapes, shards rain
 * (IceParticles.breakApart: the sounds in steps), he drops into the tired landing (IcemanShellMotion) while loose
 * fragments keep sliding off his shoulders and back.</li>
 * <li>The burst (Q again): the stress (the plates trembling ever faster, glowing cracks creeping over everything, mist
 * leaking from them, cracks snapping), then KRAAAK: every piece flung outward 360 degrees with the flash, the shock ring
 * and the spray, landing and melting round him.</li>
 * </ul>
 * A shell that ends any other way melts where it stands. The frost on the ground under him stays a while (rime).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanShellFx {
    private IcemanShellFx() {}

    // ------------------------------------------------------------------ the cocoon's shape
    /** The egg the plates are laid over (blocks, his body frame): half widths to the side and forward, its middle forward and up, its half height. */
    private static final float A = .5f, B = .47f, F0 = .06f, UP0 = .69f, CV = .77f;
    private static final int PLATE = 0, SPIRE = 1, FOOT = 2;
    /**
     * One piece of the cocoon in his body frame (s to his right, u up from the feet, f forward): its base, the way it
     * grows (d), the surface's outward normal there (n), length, half width, seed, ice, its growth delay and duration
     * on the formation clock, its break zone (crystals in a foot cluster: count).
     */
    private record Piece(int kind, float s, float u, float f, float ds, float du, float df, float ns, float nu, float nf,
                         float len, float r, int seed, IceMesh.Mat mat, float delay, float dur, int zone, int count) {}

    /** The cocoon of one Iceman (the same on every client: seeded by his entity id). */
    private static Piece[] layout(int id) {
        List<Piece> out = new ArrayList<>();
        Random r = new Random(id * 7919L + 17);
        // Two layers of plates over the egg (a golden spiral, jittered): the second smaller, sunk deeper, a little later.
        for (int layer = 0; layer < 2; layer++) {
            int n = layer == 0 ? 34 : 20;
            for (int i = 0; i < n; i++) {
                float yk = 1 - 2 * (i + .5f) / n + (r.nextFloat() - .5f) * .08f;
                float rr = Mth.sqrt(Math.max(0, 1 - yk * yk));
                float a = i * 2.39996f + layer * 1.2f + (r.nextFloat() - .5f) * .5f;
                float ex = Mth.cos(a) * rr, ez = Mth.sin(a) * rr;
                float s = A * ex, u = UP0 + CV * yk, f = F0 + B * ez;
                if (u < .1f) continue;
                float nx = ex / A, ny = yk / CV, nz = ez / B, nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= nl; ny /= nl; nz /= nl;
                // Grown out along the surface's normal, a little up (ice builds upward), each its own way.
                float dx = nx + (r.nextFloat() - .5f) * .35f, dy = ny + .22f + (r.nextFloat() - .5f) * .3f, dz = nz + (r.nextFloat() - .5f) * .35f;
                float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                boolean back = u > .85f && f < .1f;
                float len = layer == 0 ? .07f + .08f * r.nextFloat() : .05f + .05f * r.nextFloat();
                float rad = (layer == 0 ? .21f + .09f * r.nextFloat() : .15f + .07f * r.nextFloat()) * (back ? 1.12f : 1);
                float sink = layer == 0 ? .035f : .06f;
                float m = r.nextFloat();
                IceMesh.Mat mat = m < .5f ? IceMesh.GLACIER : m < .8f ? IceMesh.CLEAR : m < .9f ? IceMesh.MILKY : IceMesh.DEEP;
                float share = Mth.clamp(u / 1.45f, 0, 1);
                float delay = .04f + .6f * share + .1f * r.nextFloat() + layer * .07f;
                out.add(new Piece(PLATE, s - nx * sink, u - ny * sink, f - nz * sink, dx / dl, dy / dl, dz / dl, nx, ny, nz, len, rad,
                        r.nextInt(1 << 20), mat, delay, .22f + .18f * r.nextFloat(), zone(s, u, f), 0));
            }
        }
        // Crystals jutting out (side, up, forward on the unit egg): the shoulders, the upper back, the crown, the sides, the shins.
        float[][] spires = {{.78f, .48f, -.1f}, {-.76f, .5f, -.12f}, {.3f, .55f, -.75f}, {-.38f, .42f, -.8f}, {.02f, .78f, -.6f},
                {.12f, .96f, .1f}, {.95f, -.12f, .12f}, {-.94f, -.05f, .2f}, {.36f, -.48f, .78f}, {-.32f, -.52f, .78f}, {.55f, .1f, -.8f}};
        for (float[] sp : spires) {
            float ex = sp[0], ey = sp[1], ez = sp[2], el = Mth.sqrt(ex * ex + ey * ey + ez * ez);
            ex /= el; ey /= el; ez /= el;
            float s = A * ex, u = UP0 + CV * ey, f = F0 + B * ez;
            float nx = ex / A, ny = ey / CV, nz = ez / B, nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= nl; ny /= nl; nz /= nl;
            float dx = nx * .75f + (r.nextFloat() - .5f) * .3f, dy = ny * .75f + .45f, dz = nz * .75f + (r.nextFloat() - .5f) * .3f;
            float dl = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            boolean high = u > .8f;
            float len = high ? .42f + .32f * r.nextFloat() : .24f + .16f * r.nextFloat();
            float share = Mth.clamp(u / 1.45f, 0, 1);
            out.add(new Piece(SPIRE, s - nx * .08f, u - ny * .08f, f - nz * .08f, dx / dl, dy / dl, dz / dl, nx, ny, nz, len, len * (.15f + .07f * r.nextFloat()),
                    r.nextInt(1 << 20), r.nextFloat() < .55f ? IceMesh.CLEAR : IceMesh.GLACIER, .16f + .6f * share + .08f * r.nextFloat(), .3f + .15f * r.nextFloat(), zone(s, u, f), 0));
        }
        // Clusters grown out of the ground round his feet (they come first).
        for (int i = 0; i < 6; i++) {
            float a = Mth.TWO_PI * (i + .35f * r.nextFloat()) / 6;
            float d = .5f + .14f * r.nextFloat();
            float s = Mth.cos(a) * d, f = F0 + Mth.sin(a) * d;
            float ux = Mth.cos(a) * .4f, uz = Mth.sin(a) * .4f, ul = Mth.sqrt(ux * ux + 1 + uz * uz);
            out.add(new Piece(FOOT, s, 0, f, ux / ul, 1 / ul, uz / ul, Mth.cos(a), 0, Mth.sin(a), .24f + .14f * r.nextFloat(), 0,
                    r.nextInt(1 << 20), r.nextFloat() < .6f ? IceMesh.GLACIER : IceMesh.CLEAR, .1f * r.nextFloat(), .55f, zone(s, .1f, f), 4 + r.nextInt(2)));
        }
        return out.toArray(new Piece[0]);
    }
    /** Which zone a point of the cocoon breaks with: a quarter round him, low or high. */
    private static int zone(float s, float u, float f) {
        float a = (float) Math.atan2(s, f - F0) + Mth.PI;
        return Mth.clamp((int) (a / Mth.HALF_PI), 0, 3) + (u > .78f ? 4 : 0);
    }
    private static final int ZONES = 8;

    // ------------------------------------------------------------------ cracks on the cocoon
    /**
     * A crack network over the egg: segments between points on the unit egg (side, up, forward), each with its distance
     * from where it started and its branch depth (8 floats a segment); born when, how hard (the stress's own are glowing).
     */
    private record Net(float[] seg, int n, float total, float born, float power, boolean stress) {}
    private static final float[] T3 = new float[3];
    /** A crack network spreading from a point of the unit egg e (side, up, forward), reach in radians of the egg. */
    private static Net net(Random r, float ex, float ey, float ez, float reach, int arms, float born, float power, boolean stress) {
        List<float[]> segs = new ArrayList<>();
        for (int i = 0; i < arms; i++) {
            float ang = Mth.TWO_PI * (i + .6f * r.nextFloat()) / arms;
            walk(r, segs, ex, ey, ez, ang, reach * (.65f + .5f * r.nextFloat()), 0, 0);
        }
        float[] out = new float[segs.size() * 8];
        float total = 0;
        for (int i = 0; i < segs.size(); i++) {
            System.arraycopy(segs.get(i), 0, out, i * 8, 8);
            total = Math.max(total, segs.get(i)[7]);
        }
        return new Net(out, segs.size(), Math.max(1e-3f, total), born, power, stress);
    }
    /** A jagged walk over the unit sphere from p in the tangent direction at angle ang (branches of branches). */
    private static void walk(Random r, List<float[]> segs, float px, float py, float pz, float ang, float len, int depth, float d) {
        // A tangent frame at p.
        float ax = Math.abs(py) < .9f ? 0 : 1, ay = Math.abs(py) < .9f ? 1 : 0;
        float ux = py * 0 - pz * ay, uy = pz * ax - px * 0, uz = px * ay - py * ax, ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = py * uz - pz * uy, vy = pz * ux - px * uz, vz = px * uy - py * ux;
        float tx = ux * Mth.cos(ang) + vx * Mth.sin(ang), ty = uy * Mth.cos(ang) + vy * Mth.sin(ang), tz = uz * Mth.cos(ang) + vz * Mth.sin(ang);
        float step = .07f;
        int n = Math.max(1, (int) (len / step));
        for (int i = 0; i < n && segs.size() < 90; i++) {
            float nx = px + tx * step, ny = py + ty * step, nz = pz + tz * step, nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= nl; ny /= nl; nz /= nl;
            segs.add(new float[]{px, py, pz, nx, ny, nz, d, d + step});
            // Keep the way tangent, then wobble it about the new point.
            float dt = tx * nx + ty * ny + tz * nz;
            tx -= nx * dt; ty -= ny * dt; tz -= nz * dt;
            float tl = Mth.sqrt(tx * tx + ty * ty + tz * tz);
            tx /= tl; ty /= tl; tz /= tl;
            float w = (r.nextFloat() - .5f) * .8f, cw = Mth.cos(w), sw = Mth.sin(w);
            float cx = ny * tz - nz * ty, cy = nz * tx - nx * tz, cz = nx * ty - ny * tx;
            tx = tx * cw + cx * sw; ty = ty * cw + cy * sw; tz = tz * cw + cz * sw;
            px = nx; py = ny; pz = nz; d += step;
            if (depth < 2 && r.nextFloat() < .2f) {
                float side = r.nextBoolean() ? 1 : -1;
                // The branch's own way: the tangent turned by 35..80 degrees about the point.
                float b = side * (.6f + .8f * r.nextFloat()), cb = Mth.cos(b), sb = Mth.sin(b);
                float bx = tx * cb + cx * sb, by = ty * cb + cy * sb, bz = tz * cb + cz * sb;
                walkFrom(r, segs, px, py, pz, bx, by, bz, len * .45f * (1 - i / (float) n) + .05f, depth + 1, d);
            }
        }
    }
    private static void walkFrom(Random r, List<float[]> segs, float px, float py, float pz, float tx, float ty, float tz, float len, int depth, float d) {
        // Same as walk, from a given tangent: find its angle in walk's frame.
        float ax = Math.abs(py) < .9f ? 0 : 1, ay = Math.abs(py) < .9f ? 1 : 0;
        float ux = -pz * ay, uy = pz * ax, uz = px * ay - py * ax, ul = Mth.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul; uy /= ul; uz /= ul;
        float vx = py * uz - pz * uy, vy = pz * ux - px * uz, vz = px * uy - py * ux;
        float ang = (float) Math.atan2(tx * vx + ty * vy + tz * vz, tx * ux + ty * uy + tz * uz);
        walk(r, segs, px, py, pz, ang, len, depth, d);
    }
    /** A point of the unit egg (side, up, forward) lifted out by lift blocks, in his body frame, into T3 (s, u, f). */
    private static void onEgg(float ex, float ey, float ez, float lift) {
        float s = A * ex, u = CV * ey, f = B * ez, l = Mth.sqrt(s * s + u * u + f * f);
        float k = l < 1e-4f ? 1 : 1 + lift / l;
        T3[0] = s * k; T3[1] = UP0 + u * k; T3[2] = F0 + f * k;
    }

    // ------------------------------------------------------------------ one shell as this client follows it
    /** A blow on the shell, kept for his flinch. */
    private static final class Shell {
        Piece[] pieces;
        boolean[] chipped, landed;
        final List<Net> nets = new ArrayList<>();
        /** The last blow (for the flash and the flinch). */
        float hitAt = -100, hitPower, hitFwd, hitSide;
        /** The rime on the ground: where, since when, when it began to fade (-1 still there), when the burst spread it. */
        Vec3 rimeAt; float rimeStart = -100, rimeEnd = -1, rimeBurst = -1;
        /** Which action and clock start the one-shot cues belong to, and which have fired (bits). */
        int action = -1; long start; int fired;
        /** Coming apart: since when (-1 whole), how (BROKE, BURST, MELT), where and turned how, the push, the burst's radius; per zone when it lets go. */
        float breakAt = -1; int how; Vec3 breakFeet = Vec3.ZERO; float breakYaw, radius = 6; Vec3 push = Vec3.ZERO;
        final float[] zoneAt = new float[ZONES];
        /** The last place and turn he was drawn at (for the pieces when the break comes). */
        Vec3 lastFeet; float lastYaw;
    }
    private static final int BROKE = 0, BURST = 1, MELT = 2;
    /** Ticks the broken pieces stay before they are gone (flight, landing, melting). */
    private static final float PIECE_LIFE = 44;
    private static final Map<Integer, Shell> SHELLS = new HashMap<>();
    private static Level lastLevel;

    private static Shell shell(int id) {
        if (SHELLS.size() > 64) SHELLS.clear();
        Shell s = SHELLS.computeIfAbsent(id, k -> new Shell());
        if (s.pieces == null) { s.pieces = layout(id); s.chipped = new boolean[s.pieces.length]; s.landed = new boolean[s.pieces.length]; }
        return s;
    }
    private static float now() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime();
    }
    private static boolean whole(int a) { return a == SHELL_FORM || a == SHELL || a == SHELL_BURST; }
    private static boolean inShell(int a) { return a == SHELL_FORM || a == SHELL || a == SHELL_BURST || a == SHELL_BREAK; }

    // ------------------------------------------------------------------ the packets
    public static void receive(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        switch (p.kind()) {
            case FX_SHELL_HIT -> hit(p);
            case FX_SHELL_BREAK -> broken(p);
            case FX_SHELL_BURST -> burst(p);
            default -> {}
        }
    }

    /** His body's yaw (degrees) and his feet, now. */
    private static float yaw(Entity e, float partial) {
        return e instanceof LivingEntity l ? Mth.rotLerp(partial, l.yBodyRotO, l.yBodyRot) : e.getViewYRot(partial);
    }
    private static Vec3 forward(float yawDeg) { float y = yawDeg * Mth.DEG_TO_RAD; return new Vec3(-Mth.sin(y), 0, Mth.cos(y)); }
    private static Vec3 right(float yawDeg) { float y = yawDeg * Mth.DEG_TO_RAD; return new Vec3(-Mth.cos(y), 0, -Mth.sin(y)); }
    private static Vec3 world(Vec3 feet, Vec3 fw, Vec3 rt, float s, float u, float f) {
        return new Vec3(feet.x + fw.x * f + rt.x * s, feet.y + u, feet.z + fw.z * f + rt.z * s);
    }

    /**
     * The shell struck (power = damage, dir = toward whoever struck, flat; zero when unknown): a crack network spreading
     * from the spot over the cocoon, plates knocked off by the heavy ones, chips, frost, a shake by weight.
     */
    private static void hit(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level.getEntity(p.entity());
        Vec3 feet = e != null ? e.position() : p.pos();
        float yaw = e != null ? yaw(e, 1) : 0;
        Vec3 dir = p.dir();
        if (dir.lengthSqr() < 1e-4) {
            float a = IceParticles.rand() * Mth.TWO_PI;
            dir = new Vec3(Mth.cos(a), 0, Mth.sin(a));
        } else dir = new Vec3(dir.x, 0, dir.z).normalize();
        float dmg = Math.max(0, p.power());
        float now = now();
        Shell s = shell(p.entity());
        Vec3 fw = forward(yaw), rt = right(yaw);
        float fwd = (float) dir.dot(fw), side = (float) dir.dot(rt);
        s.hitAt = now; s.hitPower = dmg; s.hitFwd = fwd; s.hitSide = side;
        // Where on the egg: toward the blow, at a height of its own.
        float ey = -.25f + .95f * IceParticles.rand();
        float hr = Mth.sqrt(Math.max(0, 1 - ey * ey));
        float ex = side * hr, ez = fwd * hr;
        Random r = new Random((long) (IceParticles.rand() * 1e9));
        float weight = Mth.clamp(dmg / 12f, .1f, 1.4f);
        if (s.nets.size() >= 12) s.nets.remove(0);
        s.nets.add(net(r, ex, ey, ez, .22f + .75f * weight, dmg < 4 ? 2 : dmg < 10 ? 4 : 6, now, dmg, false));
        onEgg(ex, ey, ez, .06f);
        Vec3 at = world(feet, fw, rt, T3[0], T3[1], T3[2]);
        Vec3 out = dir.add(0, .25, 0);
        // The plates knocked off where it landed (the nearest ones): one for a heavy blow, three for a very heavy one.
        int off = dmg < 4 ? 0 : dmg < 10 ? 1 : 3;
        for (int k = 0; k < off; k++) {
            int best = -1; double bd = 1e9;
            for (int i = 0; i < s.pieces.length; i++) {
                Piece pc = s.pieces[i];
                if (pc.kind() == FOOT || s.chipped[i]) continue;
                double d = (pc.s() - T3[0]) * (pc.s() - T3[0]) + (pc.u() - T3[1]) * (pc.u() - T3[1]) + (pc.f() - T3[2]) * (pc.f() - T3[2]);
                if (d < bd) { bd = d; best = i; }
            }
            if (best < 0 || bd > .3) break;
            s.chipped[best] = true;
            Piece pc = s.pieces[best];
            Vec3 pa = world(feet, fw, rt, pc.s() + pc.ns() * .06f, pc.u() + pc.nu() * .06f, pc.f() + pc.nf() * .06f);
            Vec3 n = new Vec3(fw.x * pc.nf() + rt.x * pc.ns(), pc.nu(), fw.z * pc.nf() + rt.z * pc.ns());
            // The plate itself flies off, broken.
            IceParticles.shard(pa, n.scale(.12 + .06 * IceParticles.rand()).add(0, .1, 0), pc.r() * 1.4f, 50 + (int) (IceParticles.rand() * 20), pc.mat());
        }
        boolean me = IcemanClient.isMe(p.entity());
        if (dmg < 4) {
            for (int i = 0, n = IceParticles.count(5, at); i < n; i++)
                IceParticles.shard(at.add(IceParticles.jitter(.05)), out.scale(.08 + .06 * IceParticles.rand()).add(IceParticles.jitter(.04)), .035f + .03f * IceParticles.rand(), 18 + (int) (IceParticles.rand() * 14), IceMesh.FRESH);
            IceParticles.frostDust(at, out.scale(.06), 1.2f);
            if (me) IcemanClient.shake(.03f);
        } else if (dmg < 10) {
            for (int i = 0, n = IceParticles.count(8, at); i < n; i++)
                IceParticles.shard(at.add(IceParticles.jitter(.06)), out.scale(.1 + .1 * IceParticles.rand()).add(IceParticles.jitter(.05)), .04f + .04f * IceParticles.rand(), 22 + (int) (IceParticles.rand() * 16), i % 2 == 0 ? IceMesh.FRESH : IceMesh.GLACIER);
            IceParticles.flash(at, .45f, .45f, 3);
            IceParticles.mist(at, out.scale(.03), .3f, .02f, .25f, 26);
            IceParticles.frostDust(at, out.scale(.1), 2.5f);
            if (me) IcemanClient.shake(.13f);
        } else {
            for (int i = 0, n = IceParticles.count(16, at); i < n; i++)
                IceParticles.shard(at.add(IceParticles.jitter(.1)), out.scale(.12 + .16 * IceParticles.rand()).add(IceParticles.jitter(.07)), .045f + .06f * IceParticles.rand(), 26 + (int) (IceParticles.rand() * 20), i % 3 == 0 ? IceMesh.FRESH : IceMesh.GLACIER);
            IceParticles.flash(at, .7f, .6f, 4);
            IceParticles.cryo(at, out, .6f, .5f);
            IceParticles.frostDust(at, out.scale(.12), 4);
            if (me) IcemanClient.shake(.3f);
        }
    }

    /** Broken: cracks everywhere, then the cocoon comes apart zone by zone and falls round him. */
    private static void broken(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level.getEntity(p.entity());
        Shell s = shell(p.entity());
        Vec3 feet = e != null ? e.position() : p.pos();
        Vec3 push = p.dir().lengthSqr() < 1e-4 ? Vec3.ZERO : new Vec3(p.dir().x, 0, p.dir().z).normalize().scale(-.04);
        apart(s, e, feet, BROKE, push, 0);
        Vec3 c = feet.add(0, .7, 0);
        // The fracture's language: crack, zone by zone, shards, frost dust, mist, the shards landing (sounds in steps).
        IceParticles.breakApart(c, new Vec3(0, 1, 0), 1.3f, 1.1f, push, 4, 10, IceMesh.GLACIER);
        IceParticles.coldMist(feet.add(0, .05, 0), 2.2f, 1);
        Shell sh = s;
        if (sh.rimeEnd < 0) sh.rimeEnd = now();
        if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.38f);
        else shakeNear(c, .2f, 8);
    }

    /** KRAAAK: every piece flung out 360 degrees, the flash, the ring, the spray, the mist, the frost spreading, everyone near shaken. */
    private static void burst(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level.getEntity(p.entity());
        Vec3 feet = e != null ? e.position() : p.pos();
        float radius = Math.max(2, p.power());
        Shell s = shell(p.entity());
        apart(s, e, feet, BURST, Vec3.ZERO, radius);
        Vec3 c = feet.add(0, .8, 0);
        IceParticles.flash(c, 2.2f, .9f, 6);
        IceParticles.flash(c, 1.0f, 1f, 3);
        IceParticles.ring(feet.add(0, .06, 0), radius * 1.15f, .55f, IceParticles.COLD_LIGHT, .8f, 9, true);
        IceParticles.ring(feet.add(0, .04, 0), radius * .95f, .9f, IceParticles.SNOW_RGB, .55f, 18, false);
        // The spray between the big pieces: shards and frost flung out over a sphere (a golden spiral), the upper half favoured.
        int n = IceParticles.count(46, c);
        float speed = radius / 30f;
        for (int i = 0; i < n; i++) {
            float yk = 1 - 1.6f * (i + .5f) / n;
            float rr = Mth.sqrt(Math.max(0, 1 - yk * yk));
            float a = i * 2.39996f;
            Vec3 d = new Vec3(Mth.cos(a) * rr, yk * .7f + .12f, Mth.sin(a) * rr);
            IceParticles.shard(c.add(d.scale(.45)), d.scale(speed * (.7f + .55f * IceParticles.rand())), .05f + .07f * IceParticles.rand(),
                    30 + (int) (IceParticles.rand() * 25), i % 3 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        }
        IceParticles.cryo(c, new Vec3(0, 1, 0), 1.6f, 1);
        IceParticles.coldMist(feet.add(0, .05, 0), radius * .8f, 1.2f);
        for (int i = 0, m = IceParticles.count(24, c); i < m; i++) {
            Vec3 d = IceParticles.jitter(1).normalize();
            IceParticles.snow(c.add(d.scale(.4)), d.scale(.25 + .2 * IceParticles.rand()).add(0, .12, 0), .03f + .03f * IceParticles.rand(), 20 + (int) (IceParticles.rand() * 16));
        }
        IceParticles.later(14, () -> sound(feet, ModSounds.ICEMAN_SHARD_RAIN.get(), .9f, .95f));
        IceParticles.later(22, () -> sound(feet, ModSounds.ICEMAN_FROST_HISS.get(), .5f, .9f));
        float now = now();
        if (s.rimeAt == null) { s.rimeAt = feet; s.rimeStart = now - 20; }
        s.rimeBurst = now;
        s.rimeEnd = now;
        if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.6f);
        else shakeNear(c, .65f, radius * 2.5f);
    }
    /** The cocoon begins to come apart (how), from where he is; the zones let go one after another (all at once for the burst). */
    private static void apart(Shell s, Entity e, Vec3 feet, int how, Vec3 push, float radius) {
        if (s.breakAt >= 0 && how != BURST) return;
        float now = now();
        s.breakAt = now; s.how = how; s.push = push; s.radius = radius;
        s.breakFeet = s.lastFeet != null && s.lastFeet.distanceToSqr(feet) < 4 ? s.lastFeet : feet;
        s.breakYaw = s.lastFeet != null ? s.lastYaw : e != null ? yaw(e, 1) : 0;
        java.util.Arrays.fill(s.landed, false);
        // The zones in their own order: the first lets go after the cracks have run, the last some ticks later.
        int[] order = new int[ZONES];
        for (int i = 0; i < ZONES; i++) order[i] = i;
        Random r = new Random((long) (now * 31) + s.pieces.length);
        for (int i = ZONES - 1; i > 0; i--) { int j = r.nextInt(i + 1), t = order[i]; order[i] = order[j]; order[j] = t; }
        for (int k = 0; k < ZONES; k++) s.zoneAt[order[k]] = how == BURST ? r.nextFloat() * 1.2f : how == MELT ? 0 : 2.5f + k * 1.15f + r.nextFloat() * .6f;
    }
    private static void sound(Vec3 at, SoundEvent ev, float vol, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.x, at.y + .8, at.z, ev, SoundSource.PLAYERS, vol, pitch, false);
    }
    /** A shake for the local player by distance (full at the point, nothing at reach). */
    static void shakeNear(Vec3 at, float amount, float reach) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = mc.player.position().distanceTo(at);
        if (d >= reach) return;
        float k = (float) (1 - d / reach);
        IcemanClient.shake(amount * k * k);
    }

    // ------------------------------------------------------------------ on his body
    /**
     * Sets the inner layer of the shell on his body for this draw (IcemanBody.SHELL_*: thin glacier ice over every part,
     * closing from the feet up, falling away from the head down when it breaks), from his state; called by the layer
     * before drawing him. The cocoon itself and its cracks are drawn here (render).
     */
    public static void body(int entity, IcemanClient.State s, int action, float t, float now) {
        IcemanBody.SHELL_COVER = 0; IcemanBody.SHELL_CRACK = 0; IcemanBody.SHELL_FLASH = 0; IcemanBody.SHELL_GLOW = 0;
        IcemanBody.SHELL_THICK = 1.2f;
        if (s == null) return;
        Shell sh = SHELLS.get(entity);
        float flash = 0;
        if (sh != null && sh.hitAt > -100) {
            float dt = now - sh.hitAt;
            if (dt >= 0 && dt < 10) flash = (float) Math.exp(-dt / 2.2f) * Mth.clamp(.2f + sh.hitPower / 16f, 0, .8f);
        }
        boolean apart = sh != null && sh.breakAt >= 0;
        switch (action) {
            case SHELL_FORM -> {
                float x = Mth.clamp(t / SHELL_FORM_TICKS, 0, 1);
                // Organic: quick to take the feet and legs, slowing as it closes over the shoulders and the head.
                float grow = 1 - (1 - x) * (1 - x) * (1 - .35f * x);
                IcemanBody.SHELL_COVER = 1.15f * grow;
                IcemanBody.SHELL_THICK = .7f + .6f * grow;
                IcemanBody.SHELL_FLASH = flash;
            }
            case SHELL -> {
                IcemanBody.SHELL_COVER = 1.15f;
                IcemanBody.SHELL_THICK = 1.3f;
                IcemanBody.SHELL_FLASH = flash;
                IcemanBody.SHELL_GLOW = .04f;
            }
            case SHELL_BURST -> {
                if (t < BURST_STRESS && !apart) {
                    float k = Mth.clamp(t / BURST_STRESS, 0, 1);
                    float pulse = .5f + .5f * Mth.sin(stressPhase(t));
                    IcemanBody.SHELL_COVER = 1.15f;
                    IcemanBody.SHELL_THICK = 1.3f + .15f * k;
                    IcemanBody.SHELL_GLOW = Mth.clamp(.1f + k * (.3f + .4f * pulse), 0, 1);
                    IcemanBody.SHELL_FLASH = flash;
                }
            }
            case SHELL_BREAK -> {
                // The inner ice falls away from the head down as the zones let go.
                float since = apart ? now - sh.breakAt : t;
                IcemanBody.SHELL_COVER = 1.15f * (1 - FilmFx.ease((since - 2) / 8));
                IcemanBody.SHELL_THICK = 1.2f;
            }
            default -> {}
        }
    }
    /** The inner light's pulse during the stress: faster and faster (radians). */
    static float stressPhase(float t) { return .45f * t + .05f * t * t; }

    /**
     * The flinch inside the shell after a blow (for IcemanShellMotion): {toward the blow (1 front, -1 behind), to his
     * right (1) or left (-1), strength 0..1 now}; null when nothing.
     */
    public static float[] flinch(int entity) {
        Shell s = SHELLS.get(entity);
        if (s == null || s.hitAt <= -100) return null;
        float dt = now() - s.hitAt;
        if (dt < 0 || dt > 14) return null;
        float env = (dt / 1.8f) * (float) Math.exp(1 - dt / 1.8f);
        float k = env * Mth.clamp(s.hitPower / 12f, .15f, 1.3f);
        return k < .01f ? null : new float[]{s.hitFwd, s.hitSide, k};
    }

    // ------------------------------------------------------------------ every tick: what follows his clock
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { SHELLS.clear(); lastLevel = mc.level; }
        if (mc.level == null || mc.isPaused()) return;
        float now = mc.level.getGameTime();
        for (var en : IcemanClient.states().entrySet()) {
            IcemanClient.State s = en.getValue();
            int a = s.action;
            Shell sh = inShell(a) ? shell(en.getKey()) : SHELLS.get(en.getKey());
            if (sh == null) continue;
            Entity ent = mc.level.getEntity(en.getKey());
            if (sh.action != a || sh.start != s.start) {
                if (a == SHELL_FORM) {
                    // A new shell: the old one's marks are gone; the rime is laid where he stands.
                    sh.nets.clear(); java.util.Arrays.fill(sh.chipped, false); sh.breakAt = -1; sh.hitAt = -100;
                    if (ent != null) { sh.rimeAt = ent.position(); sh.rimeStart = now; sh.rimeEnd = -1; sh.rimeBurst = -1; }
                }
                // The stress: glowing cracks that creep over all of it.
                if (a == SHELL_BURST) {
                    Random r = new Random(en.getKey() * 31L + s.start);
                    for (int i = 0; i < 6; i++) {
                        float ey = -.3f + 1.2f * r.nextFloat(), hr = Mth.sqrt(Math.max(0, 1 - Math.min(1, ey * ey))), ang = r.nextFloat() * Mth.TWO_PI;
                        if (sh.nets.size() >= 16) sh.nets.remove(0);
                        sh.nets.add(net(r, Mth.cos(ang) * hr, Math.min(1, ey), Mth.sin(ang) * hr, .9f + .5f * r.nextFloat(), 3, now, 12, true));
                    }
                }
                // Ended some other way while whole (no break heard): it melts where it stands.
                boolean wasWhole = whole(sh.action);
                if (wasWhole && !inShell(a) && sh.breakAt < 0 && ent != null) apart(sh, ent, sh.lastFeet != null ? sh.lastFeet : ent.position(), MELT, Vec3.ZERO, 0);
                if (!inShell(a) && sh.rimeAt != null && sh.rimeEnd < 0) sh.rimeEnd = now;
                sh.action = a; sh.start = s.start; sh.fired = 0;
            }
            if (ent != null && sh.breakAt < 0 && (a == SHELL_BURST && IcemanClient.clock(s, 0) > BURST_STRESS + 8 || a == SHELL_BREAK && IcemanClient.clock(s, 0) > 8))
                apart(sh, ent, ent.position(), MELT, Vec3.ZERO, 0);   // the break's packet never came: it goes all the same
            if (ent == null || !inShell(a) || ent.isInvisible()) continue;
            float t = IcemanClient.clock(s, 0);
            Vec3 feet = ent.position();
            float yaw = yaw(ent, 1);
            switch (a) {
                case SHELL_FORM -> {
                    if (t < SHELL_FORM_TICKS) {
                        float x = t / SHELL_FORM_TICKS;
                        // The air freezing: tiny ice crystals drawn in spiralling toward him, cold mist creeping up from the ground.
                        for (int i = 0, n = IceParticles.count(3, feet); i < n; i++) {
                            float ang = IceParticles.rand() * Mth.TWO_PI, r = 1.0f + .5f * IceParticles.rand();
                            Vec3 at = feet.add(Mth.cos(ang) * r, .1 + 1.4 * IceParticles.rand(), Mth.sin(ang) * r);
                            Vec3 in = new Vec3(-Mth.cos(ang), 0, -Mth.sin(ang)).scale(.05 + .03 * IceParticles.rand());
                            IceParticles.crystalDust(at, in, .016f + .016f * IceParticles.rand(), (IceParticles.rand() < .5f ? 1 : -1) * .35f, 14 + (int) (IceParticles.rand() * 8));
                        }
                        for (int i = 0, n = IceParticles.count(2, feet); i < n; i++) {
                            float ang = IceParticles.rand() * Mth.TWO_PI, r = .55f + .3f * IceParticles.rand();
                            Vec3 at = feet.add(Mth.cos(ang) * r, .05 + .2 * IceParticles.rand(), Mth.sin(ang) * r);
                            IceParticles.mist(at, new Vec3(-Mth.cos(ang) * .02, .018 + .01 * IceParticles.rand(), -Mth.sin(ang) * .02), .3f, .012f, .26f, 22);
                        }
                        // Frost dust off the growing line.
                        if (IceParticles.rand() < .6f) IceParticles.frostDust(feet.add(IceParticles.jitter(.3)).add(0, 1.4 * x, 0), new Vec3(0, .01, 0), 1);
                        cue(sh, 5, t >= 1, ent, ModSounds.ICEMAN_CRYSTAL_TICKS.get(), .7f, 1.1f);
                    }
                    // Closed: cold air rolls out low over the ground.
                    if (!fired(sh, 6) && t >= SHELL_FORM_TICKS - 3) {
                        sh.fired |= 1 << 6;
                        IceParticles.coldMist(feet.add(0, .05, 0), 1.8f, .9f);
                        cue(sh, 7, true, ent, ModSounds.ICEMAN_FROST_HISS.get(), .4f, 1.1f);
                    }
                }
                case SHELL -> {
                    // Now and then a breath of cold falling off the cocoon.
                    if (IceParticles.rand() < .12f) {
                        float ang = IceParticles.rand() * Mth.TWO_PI;
                        IceParticles.mist(feet.add(Mth.cos(ang) * .55, .3 + .8 * IceParticles.rand(), Mth.sin(ang) * .55), new Vec3(Mth.cos(ang) * .01, -.006, Mth.sin(ang) * .01), .25f, .012f, .16f, 30);
                    }
                }
                case SHELL_BURST -> {
                    if (t < BURST_STRESS && sh.breakAt < 0) {
                        float k = t / BURST_STRESS;
                        // Mist leaking out of the cracks, more as the stress builds.
                        for (int i = 0, n = IceParticles.count(Math.round(1 + 3 * k), feet); i < n; i++) {
                            float ang = IceParticles.rand() * Mth.TWO_PI;
                            Vec3 out = new Vec3(Mth.cos(ang), 0, Mth.sin(ang));
                            IceParticles.mist(feet.add(out.scale(.6)).add(0, .2 + 1.0 * IceParticles.rand(), 0), out.scale(.02 + .03 * k).add(0, .01, 0), .2f + .15f * k, .02f, .2f + .15f * k, 18);
                        }
                        if (IceParticles.rand() < .3f + .5f * k) IceParticles.frostDust(feet.add(IceParticles.jitter(.4)).add(0, .7, 0), new Vec3(0, -.02, 0), 1 + 2 * k);
                        // The cracks snapping inside it (local: the server plays the rumble).
                        cue(sh, 0, t >= 4, ent, ModSounds.ICEMAN_CRACK.get(), .7f, .8f);
                        cue(sh, 1, t >= 8, ent, ModSounds.ICEMAN_CRACK.get(), .9f, 1.0f);
                        cue(sh, 2, t >= 11, ent, ModSounds.ICEMAN_CRACK.get(), 1.1f, 1.2f);
                        cue(sh, 3, t >= 12.5f, ent, ModSounds.ICEMAN_CRACK.get(), 1.2f, 1.45f);
                        if (IcemanClient.isMe(en.getKey()) && t > 5) IcemanClient.shake(.012f + .03f * k * k);
                    }
                }
                case SHELL_BREAK -> {
                    // He hits the ground in the landing: snow kicked up round him, a jolt in his view.
                    if (!fired(sh, 4) && t >= BREAK_LAND) {
                        sh.fired |= 1 << 4;
                        for (int i = 0, n = IceParticles.count(10, feet); i < n; i++) {
                            float ang = Mth.TWO_PI * i / 10f + IceParticles.rand() * .4f;
                            IceParticles.snow(feet.add(Mth.cos(ang) * .4, .05, Mth.sin(ang) * .4), new Vec3(Mth.cos(ang) * .1, .07 + .05 * IceParticles.rand(), Mth.sin(ang) * .1), .03f, 16);
                        }
                        IceParticles.coldMist(feet.add(0, .05, 0), 1.2f, .6f);
                        if (IcemanClient.isMe(en.getKey())) IcemanClient.shake(.12f);
                    }
                    // Loose fragments sliding off his shoulders and back as he lands and holds there, frost dust with them.
                    if (t >= BREAK_LAND - 2 && t < BREAK_LAND + 24) {
                        float k = 1 - Mth.clamp((t - BREAK_LAND) / 24f, 0, 1);
                        if (IceParticles.rand() < .6f * k * Math.min(1f, IcemanConfig.EFFECTS.get().floatValue())) {
                            Vec3 fw = forward(yaw), rt = right(yaw);
                            float side = IceParticles.rand() * 2 - 1;
                            boolean back = IceParticles.rand() < .4f;
                            Vec3 at = feet.add(fw.scale(back ? -.12 : .15 + .25 * IceParticles.rand())).add(rt.scale(.3 * side)).add(0, .75 + .3 * IceParticles.rand(), 0);
                            Vec3 v = rt.scale(.03 * side).add(fw.scale(back ? -.03 : -.01)).add(0, .01, 0).add(IceParticles.jitter(.01));
                            IceParticles.shard(at, v, .05f + .08f * IceParticles.rand(), 30 + (int) (IceParticles.rand() * 20), IceParticles.rand() < .3f ? IceMesh.MILKY : IceMesh.GLACIER);
                            if (IceParticles.rand() < .5f) IceParticles.frostDust(at, v, 1);
                        }
                    }
                }
                default -> {}
            }
        }
        // The broken pieces landing: a little spray of chips and snow where each comes down.
        for (var en : SHELLS.entrySet()) {
            Shell sh = en.getValue();
            if (sh.breakAt < 0 || sh.how == MELT) continue;
            float bt = now - sh.breakAt;
            if (bt > PIECE_LIFE) continue;
            Vec3 fw = forward(sh.breakYaw), rt = right(sh.breakYaw);
            for (int i = 0; i < sh.pieces.length; i++) {
                if (sh.landed[i] || sh.chipped[i]) continue;
                Piece pc = sh.pieces[i];
                float ft = bt - sh.zoneAt[pc.zone()];
                if (ft < 0) continue;
                float tl = landTime(sh, pc);
                if (ft < tl) continue;
                sh.landed[i] = true;
                if (i % 2 == 1 && pc.kind() == PLATE) continue;
                Vec3 at = flight(sh, pc, tl, fw, rt, null);
                if (IceParticles.count(1, at) <= 0) continue;
                for (int k = 0; k < 2; k++)
                    IceParticles.shard(at.add(IceParticles.jitter(.05)), IceParticles.jitter(.05).add(0, .08, 0), .04f + .05f * IceParticles.rand(), 20 + (int) (IceParticles.rand() * 14), k == 0 ? IceMesh.FRESH : pc.mat());
                IceParticles.snow(at, new Vec3(0, .06, 0).add(IceParticles.jitter(.04)), .03f, 14);
            }
        }
        // Forget shells long gone (their rime faded, no blow in flight, the pieces gone).
        float nowT = now;
        SHELLS.entrySet().removeIf(en -> {
            Shell sh = en.getValue();
            IcemanClient.State s = IcemanClient.get(en.getKey());
            boolean active = s != null && inShell(s.action);
            return !active && (sh.rimeEnd < 0 || nowT - sh.rimeEnd > 80) && nowT - sh.hitAt > 40 && (sh.breakAt < 0 || nowT - sh.breakAt > PIECE_LIFE + 4);
        });
    }
    private static boolean fired(Shell s, int bit) { return (s.fired & 1 << bit) != 0; }
    /** A one-shot local sound at him once its moment has come. */
    private static void cue(Shell s, int bit, boolean due, Entity at, SoundEvent sound, float volume, float pitch) {
        if (!due || fired(s, bit)) return;
        s.fired |= 1 << bit;
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.getX(), at.getY() + .8, at.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    // ------------------------------------------------------------------ a broken piece's flight
    private static final float GRAVITY = .05f;
    /** How it is flung: {outward along the floor, up, spin rate}. */
    private static float[] fling(Shell s, Piece pc) {
        float h0 = IceGrowth.h(pc.seed(), 901), h1 = IceGrowth.h(pc.seed(), 902), h2 = IceGrowth.h(pc.seed(), 903);
        if (s.how == BURST) {
            float sp = s.radius / 30f * (.75f + .6f * h0) * (pc.kind() == FOOT ? .5f : 1);
            return new float[]{sp, sp * (.35f + .5f * Math.max(0, pc.nu())) + .06f, (.25f + .3f * h2) * (h1 < .5f ? 1 : -1)};
        }
        return new float[]{.035f + .05f * h0, .03f + .06f * h1, (.12f + .2f * h2) * (h0 < .5f ? 1 : -1)};
    }
    /** When a piece reaches the ground after it lets go (ticks). */
    private static float landTime(Shell s, Piece pc) {
        if (s.how == MELT) return 0;
        float[] v = fling(s, pc);
        float y0 = pc.u() + pc.du() * pc.len() * .4f, floor = .04f + pc.r() * .25f;
        float drop = Math.max(0, y0 - floor);
        return (v[1] + Mth.sqrt(v[1] * v[1] + 2 * GRAVITY * drop)) / GRAVITY;
    }
    /**
     * Where a piece is (its base) ft ticks after it let go, its turned direction into dir (if given): flung out and up,
     * tumbling, down to the ground and still there.
     */
    private static Vec3 flight(Shell s, Piece pc, float ft, Vec3 fw, Vec3 rt, Vec3[] dir) {
        Vec3 base = world(s.breakFeet, fw, rt, pc.s(), pc.u(), pc.f());
        Vec3 d = new Vec3(fw.x * pc.df() + rt.x * pc.ds(), pc.du(), fw.z * pc.df() + rt.z * pc.ds());
        if (s.how == MELT) { if (dir != null) dir[0] = d; return base; }
        float[] v = fling(s, pc);
        float tl = landTime(s, pc), tt = Math.min(ft, tl);
        Vec3 out = new Vec3(fw.x * pc.nf() + rt.x * pc.ns(), 0, fw.z * pc.nf() + rt.z * pc.ns());
        out = out.lengthSqr() < 1e-6 ? fw : out.normalize();
        Vec3 drift = out.scale(v[0]).add(s.push);
        Vec3 p = base.add(drift.scale(tt)).add(0, v[1] * tt - .5f * GRAVITY * tt * tt, 0);
        if (dir != null) {
            Vec3 axis = new Vec3(0, 1, 0).cross(out);
            axis = axis.lengthSqr() < 1e-6 ? rt : axis.normalize();
            dir[0] = IceParticles.rotate(d, axis, v[2] * tt);
        }
        return p;
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (SHELLS.isEmpty()) return;
        IceStage st = IceStage.open(e);
        if (st == null) return;
        var mc = Minecraft.getInstance();
        try {
            float partial = st.partial, now = st.time;
            IceMesh.Ctx c = st.ice();
            boolean any = false;
            Vec3[] dir = new Vec3[1];
            for (var en : SHELLS.entrySet()) {
                Shell sh = en.getValue();
                // ---- the frost on the ground
                if (sh.rimeAt != null) {
                    float grow = FilmFx.ease((now - sh.rimeStart) / 9f);
                    float fade = sh.rimeEnd < 0 ? 1 : 1 - Mth.clamp((now - sh.rimeEnd) / 70f, 0, 1);
                    float r = 1.1f * grow;
                    if (sh.rimeBurst >= 0) r = Mth.lerp(FilmFx.ease((now - sh.rimeBurst) / 7f), r, 2.6f);
                    if (fade > 0 && r > .02f) {
                        c.light = IceStage.light(sh.rimeAt.add(0, .5, 0));
                        IcemanGroundFx.rime(c, sh.rimeAt, r, .55f * fade, en.getKey() * 7 + 3);
                        any = true;
                    }
                }
                // ---- coming apart: the pieces let go zone by zone, tumble down, land and melt
                if (sh.breakAt >= 0) {
                    float bt = now - sh.breakAt;
                    boolean own = IcemanClient.isMe(en.getKey()) && mc.options.getCameraType().isFirstPerson();
                    if (bt < PIECE_LIFE) { drawApart(st, c, sh, bt, own, dir); any = true; }
                    continue;
                }
                IcemanClient.State s = IcemanClient.get(en.getKey());
                // (Still whole in the break's first ticks until its packet comes: the pieces take over then.)
                if (s == null || !inShell(s.action)) continue;
                Entity ent = mc.level.getEntity(en.getKey());
                if (ent == null || ent.isInvisible()) continue;
                float t = IcemanClient.clock(s, partial);
                Vec3 feet = ent.getPosition(partial);
                float yaw = yaw(ent, partial);
                sh.lastFeet = feet; sh.lastYaw = yaw;
                drawWhole(st, c, sh, s.action, t, feet, yaw, IcemanClient.isMe(en.getKey()) && mc.options.getCameraType().isFirstPerson(), dir);
                any = true;
            }
            st.endIce();
            if (!any) return;
            // ---- the light: a faint cold glow inside, the stress pulsing
            FilmContext f = st.fx();
            for (var en : SHELLS.entrySet()) {
                Shell sh = en.getValue();
                IcemanClient.State s = IcemanClient.get(en.getKey());
                if (s == null || sh.breakAt >= 0) continue;
                Entity ent = mc.level.getEntity(en.getKey());
                if (ent == null || ent.isInvisible()) continue;
                if (IcemanClient.isMe(en.getKey()) && mc.options.getCameraType().isFirstPerson()) continue;
                int a = s.action;
                float t = IcemanClient.clock(s, partial);
                Vec3 c0 = ent.getPosition(partial).add(0, .7, 0);
                if (a == SHELL_BURST && t < BURST_STRESS + 1) {
                    float k = Mth.clamp(t / BURST_STRESS, 0, 1), pulse = .5f + .5f * Mth.sin(stressPhase(t));
                    FilmFx.glow(f, c0, .9 + .6 * k, IceParticles.COLD_LIGHT, (.06f + .38f * k * pulse));
                    FilmFx.glow(f, c0, .45 + .3 * k, 0xffffff, .22f * k * k * pulse);
                } else if (a == SHELL_FORM && t < SHELL_FORM_TICKS + 4) {
                    float k = Mth.clamp(t / SHELL_FORM_TICKS, 0, 1);
                    FilmFx.glow(f, c0, 1.0, IceParticles.COLD_LIGHT, .1f * Mth.sin(Mth.PI * k));
                }
            }
        } finally {
            st.close();
        }
    }

    /** The cocoon whole: forming (every piece on its own clock), holding (chipped where struck), or under the stress (trembling). */
    private static void drawWhole(IceStage st, IceMesh.Ctx c, Shell sh, int action, float t, Vec3 feet, float yaw, boolean own, Vec3[] dir) {
        Vec3 fw = forward(yaw), rt = right(yaw);
        float x = action == SHELL_FORM ? t / SHELL_FORM_TICKS * 1.35f : 2;
        float stress = action == SHELL_BURST ? Mth.clamp(t / BURST_STRESS, 0, 1) : 0;
        boolean far = st.far(feet);
        c.light = IceStage.light(feet.add(0, .8, 0));
        c.origin(feet);
        float now = st.time;
        for (int i = 0; i < sh.pieces.length; i++) {
            Piece pc = sh.pieces[i];
            if (far && pc.kind() == PLATE && (i & 1) == 1 && pc.r() < .2f) continue;
            float g = pc.kind() == FOOT ? Mth.clamp((x - pc.delay()) / pc.dur(), 0, 1.25f) : IceGrowth.grow(x, pc.delay(), pc.dur());
            if (g <= .01f) continue;
            float s = pc.s(), u = pc.u(), f = pc.f();
            if (stress > 0) {
                // Trembling, faster and harder as the stress builds.
                float w = .02f * stress * stress, ph = now * (1.2f + 2.4f * stress) + pc.seed() % 97;
                s += w * Mth.sin(ph); u += w * .6f * Mth.sin(ph * 1.3f + 2); f += w * Mth.sin(ph * .9f + 4);
            }
            Vec3 base = world(feet, fw, rt, s, u, f);
            if (own && base.distanceToSqr(st.cam) < .55) continue;
            Vec3 d = new Vec3(fw.x * pc.df() + rt.x * pc.ds(), pc.du(), fw.z * pc.df() + rt.z * pc.ds());
            if (sh.chipped[i]) {
                // Knocked off: the clean bright break left where it was.
                IceGrowth.crystal(c, base.x, base.y, base.z, d.x, d.y, d.z, pc.len() * .45f, pc.r() * .7f, pc.seed() + 77, IceMesh.FRESH, 1, 1);
                continue;
            }
            draw(c, pc, base, d, g, 1);
        }
        // ---- the crack networks: spreading from each blow, flashing, then white fracture lines; under the stress, glowing
        for (Net n : sh.nets) {
            float age = now - n.born();
            float reveal, bright;
            boolean glowing = n.stress();
            if (glowing) {
                if (action != SHELL_BURST) continue;
                reveal = FilmFx.ease((stress - .15f) / .8f);
                bright = (.4f + .6f * (.5f + .5f * Mth.sin(stressPhase(t)))) * stress;
            } else {
                if (age < 0) continue;
                float weight = Mth.clamp(n.power() / 12f, .15f, 1.3f);
                reveal = FilmFx.ease(age / (2.5f + 3 * weight));
                bright = .22f + .18f * weight + 1.1f * (float) Math.exp(-age / 3.5f) * weight;
            }
            if (reveal <= 0 || bright <= .01f) continue;
            crackNet(st, c, n, feet, fw, rt, reveal, bright, glowing, own, far);
        }
        c.ox = c.oy = c.oz = 0;
    }
    private static void draw(IceMesh.Ctx c, Piece pc, Vec3 base, Vec3 d, float g, float alpha) {
        if (pc.kind() == FOOT)
            IceGrowth.cluster(c, base.x, base.y, base.z, d.x, d.y, d.z, pc.len(), pc.seed(), pc.count(), pc.mat(), g, alpha);
        else IceGrowth.crystal(c, base.x, base.y, base.z, d.x, d.y, d.z, pc.len(), pc.r(), pc.seed(), pc.mat(), g, alpha);
    }
    /** One crack network on the cocoon (revealed up to its share, brightness; glowing = about to burst). */
    private static void crackNet(IceStage st, IceMesh.Ctx c, Net n, Vec3 feet, Vec3 fw, Vec3 rt, float reveal, float bright, boolean glowing, boolean own, boolean far) {
        float limit = reveal * n.total();
        float[] sg = n.seg();
        for (int i = 0; i < n.n(); i++) {
            int o = i * 8;
            float d0 = sg[o + 6], d1 = sg[o + 7];
            if (d0 >= limit) continue;
            if (far && (i & 1) == 1) continue;
            float k = d1 <= limit ? 1 : (limit - d0) / (d1 - d0);
            onEgg(sg[o], sg[o + 1], sg[o + 2], .1f);
            Vec3 a = world(feet, fw, rt, T3[0], T3[1], T3[2]);
            onEgg(Mth.lerp(k, sg[o], sg[o + 3]), Mth.lerp(k, sg[o + 1], sg[o + 4]), Mth.lerp(k, sg[o + 2], sg[o + 5]), .1f);
            Vec3 b = world(feet, fw, rt, T3[0], T3[1], T3[2]);
            if (own && a.distanceToSqr(st.cam) < .6) continue;
            // Thinner toward its tips.
            float w = (.016f - .005f * Math.min(2, i == 0 ? 0 : 1)) * (1 - .5f * d0 / n.total());
            if (glowing) IceMesh.vein(c, a, b, w * .8f, bright);
            else IceMesh.line(c, a, b, w, .6f * bright, .8f * bright, .95f * bright);
        }
    }

    /** The cocoon coming apart, bt ticks after: whole zones still standing, cracks glowing over them, the rest flying and melting. */
    private static void drawApart(IceStage st, IceMesh.Ctx c, Shell sh, float bt, boolean own, Vec3[] dir) {
        Vec3 fw = forward(sh.breakYaw), rt = right(sh.breakYaw);
        if (st.distance(sh.breakFeet) > 64) return;
        c.light = IceStage.light(sh.breakFeet.add(0, .8, 0));
        for (int i = 0; i < sh.pieces.length; i++) {
            if (sh.chipped[i]) continue;
            Piece pc = sh.pieces[i];
            float ft = bt - sh.zoneAt[pc.zone()];
            float alpha = 1, shrink = 1;
            if (sh.how == MELT) {
                // Melting where it stands: shrinking, sinking, going clear.
                float m = FilmFx.ease(bt / 18f);
                if (m >= 1) continue;
                shrink = 1 - .6f * m; alpha = 1 - m;
            } else if (ft > 0) {
                float tl = landTime(sh, pc);
                float m = FilmFx.ease((ft - tl - 6) / 14);
                if (m >= 1) continue;
                shrink = 1 - .65f * m; alpha = 1 - m * m;
            }
            Vec3 base = flight(sh, pc, Math.max(0, ft), fw, rt, dir);
            if (sh.how != MELT && ft <= 0 && own && base.distanceToSqr(st.cam) < .55) continue;
            Vec3 d = dir[0];
            if (sh.how == MELT) base = base.add(0, -.1 * (1 - shrink), 0);
            if (ft > 0 && sh.how != MELT) c.origin(base); else c.origin(sh.breakFeet);
            if (pc.kind() == FOOT) {
                IceGrowth.cluster(c, base.x, base.y, base.z, d.x, d.y, d.z, pc.len() * shrink, pc.seed(), pc.count(), pc.mat(), 1.25f, alpha);
                continue;
            }
            IceGrowth.crystal(c, base.x, base.y, base.z, d.x, d.y, d.z, pc.len() * shrink, pc.r() * shrink, pc.seed(), pc.mat(), 1, alpha);
            // The fresh face where it broke away from the rest, on the pieces in flight.
            if (ft > 0 && sh.how == BROKE && (i % 3) == 0)
                IceGrowth.crystal(c, base.x, base.y, base.z, -d.x, -d.y, -d.z, pc.len() * .4f * shrink, pc.r() * .75f * shrink, pc.seed() + 5, IceMesh.FRESH, 1, alpha);
        }
        c.ox = c.oy = c.oz = 0;
        // Before the zones let go: glowing cracks running over what still stands.
        if (sh.how == BROKE && bt < 12) {
            float reveal = FilmFx.ease(bt / 2.5f), bright = .9f * (1 - FilmFx.ease((bt - 6) / 6));
            int seed = sh.pieces.length * 131 + (int) sh.breakAt;
            for (int z = 0; z < ZONES; z++) {
                if (bt > sh.zoneAt[z]) continue;
                // A crack across this zone (its middle), drawn on the egg.
                float a = (z % 4 + .5f) * Mth.HALF_PI - Mth.PI, ey = z >= 4 ? .55f : -.15f;
                float hr = Mth.sqrt(1 - ey * ey);
                float ex = Mth.sin(a) * hr, ez = Mth.cos(a) * hr;
                Vec3 prev = null;
                for (int q = 0; q <= 5; q++) {
                    float k = q / 5f * reveal;
                    float wob = (IceGrowth.h(seed, z * 16 + q) - .5f) * .25f;
                    float aa = a + (k - .5f) * 1.3f, yy = ey + wob + (k - .5f) * .3f;
                    float h2 = Mth.sqrt(Math.max(0, 1 - Math.min(1, yy * yy)));
                    onEgg(Mth.sin(aa) * h2, Mth.clamp(yy, -1, 1), Mth.cos(aa) * h2, .1f);
                    Vec3 p = world(sh.breakFeet, fw, rt, T3[0], T3[1], T3[2]);
                    if (prev != null && bright > .01f && !(own && p.distanceToSqr(st.cam) < .6)) IceMesh.vein(c, prev, p, .014f, bright);
                    prev = p;
                }
            }
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { SHELLS.clear(); lastLevel = null; }
}
