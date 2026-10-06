package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanConfig;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The Ice Armory on the clients: what his hand holds each draw (forming, carried, grown, cracking, broken), and the
 * effects of the weapons.
 * <ul>
 * <li>Swing trails: ribbons of cold light (and a pale breath of frost that reads in daylight) swept between the weapon's
 * middle and its tip, sampled from where the weapon was really drawn each frame, fading in a few ticks; snow shed off
 * the tip when it moves fast.</li>
 * <li>FX_FORM: the humidity drawn into his hand: mist streams spiralling in, sparkles, a small cold flash as it closes.</li>
 * <li>FX_HIT: a frost burst on the body: small ice cubes popping out of the impact and melting, chunks, snow, a short
 * cold flash; heavier for the mace (a ring, a shake). A short hit-stop for the swinging Iceman's pose.</li>
 * <li>FX_SLAM: the flash, a shock ring and a dust ring on the ground, cracks of light running out over the ground (they
 * follow its shape) and fading, frost on the ground, snow and shards thrown up, the camera shaken by distance and size,
 * a kick of his own view; the giant mace's slam several times bigger.</li>
 * <li>FX_SPEAR / FX_SPEAR_STUCK / FX_SPEAR_SPIKES: IcemanSpearFx.</li>
 * <li>FX_SPIN: snow and shards flung round him in a disc; the held spin also draws a vortex of mist streams pulling in
 * round him, faster with the spin.</li>
 * <li>The mace growing: a rumble (shake and a kick of his view) at every new layer, frost rolling off the head.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanWeaponFx {
    private IcemanWeaponFx() {}

    /** What the clients remember per Iceman between draws. */
    private static final class Track {
        int action = -1, combo, layer = -1; long start = Long.MIN_VALUE; float strikeEnd = -1000;
        long hitStart = Long.MIN_VALUE; float hitAt, hitPower;
        long brokeKey = Long.MIN_VALUE;
        // the trail: inner point and tip (world) per sample, and when
        final float[] pts = new float[TRAIL * 6]; final float[] when = new float[TRAIL]; int head, count; float last = -1;
        Vec3 lastTip;
    }
    private static final int TRAIL = 48;
    private static final Map<Integer, Track> TRACKS = new HashMap<>();

    private static final class Form { int entity, weapon; Vec3 pos; float start; boolean closed; }
    private static final class Impact { Vec3 at, dir; float start, size; int seed; }
    private static final class Slam { Vec3 at; float start, life, radius, size; boolean giant; final List<float[]> cracks = new ArrayList<>(); }
    private static final List<Form> FORMS = new ArrayList<>();
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final List<Slam> SLAMS = new ArrayList<>();
    private static Level lastLevel;

    static float gameTime() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime();
    }
    private static Track track(int entity) {
        if (TRACKS.size() > 64) TRACKS.clear();
        return TRACKS.computeIfAbsent(entity, k -> new Track());
    }

    // ------------------------------------------------------------------ what the hand holds
    /** Sets what his right hand holds for this draw (IcemanBody.WEAPON*), from his state; called by the layer before drawing him. */
    public static void hold(int entity, IcemanClient.State s, int action, float t, float now) {
        Track tr = track(entity);
        if (action != tr.action || s.start != tr.start) {
            if (tr.action == STRIKE) tr.strikeEnd = now;
            tr.action = action; tr.start = s.start;
        }
        if (action == STRIKE) tr.combo = s.combo;
        int w = Mth.clamp(s.weapon, 0, WEAPONS - 1);
        boolean show = s.weaponOut();
        float form = 1, size = 1, crack = 0;
        switch (action) {
            case FORM -> { show = true; form = Mth.clamp(t / FORM_TICKS, 0, 1); }
            case STRIKE -> {
                if (w == W_MACE && s.combo == 2) {
                    // The overhead slam: the head cracks from the impact and bursts, the handle stays in the fist.
                    int hit = SWING_HIT[W_MACE][2];
                    crack = t < hit ? 0 : t < hit + 6 ? (t - hit) / 6f : 2;
                    if (t >= hit + 6) breakHead(entity, tr, s, 1, show);
                }
            }
            case CHARGE -> {
                float held = t - HOLD_TICKS;
                if (w == W_MACE) {
                    size = maceSize(Mth.clamp(held / MACE_GROW, 0, 1));
                    crack = .3f * PantherMotion.k(held, MACE_GROW, MACE_GROW + 8);
                } else if (w == W_SPEAR) size = 1 + .6f * PantherMotion.ease(Mth.clamp(held / SPEAR_DRAW, 0, 1));
                else crack = .55f * PantherMotion.k(held, SPIN_MAX - 30, SPIN_MAX);
            }
            case RELEASE -> {
                if (w == W_MACE) {
                    show = show || t < MACE_SLAM_TICKS;
                    size = maceSize(s.charge);
                    crack = t < MACE_SLAM_HIT ? 0 : t < MACE_SLAM_HIT + 8 ? (t - MACE_SLAM_HIT) / 8f : 2;
                    if (t >= MACE_SLAM_HIT + 8) breakHead(entity, tr, s, size, show);
                } else if (w == W_SPEAR) {
                    show = t < SPEAR_THROW_AT;
                    size = 1 + .6f * PantherMotion.ease(Mth.clamp(s.charge, 0, 1));
                } else {
                    // The sword: the cracks race over it, then it bursts as the spin stops.
                    show = t < 1.2f;
                    crack = .6f + .4f * Mth.clamp(t / 1.2f, 0, 1);
                    if (!show) breakBlade(entity, tr, s);
                }
            }
            default -> {}
        }
        IcemanBody.WEAPON = show ? w : -1;
        IcemanBody.WEAPON_FORM = form;
        IcemanBody.WEAPON_SIZE = size;
        IcemanBody.WEAPON_CRACK = crack;
    }
    /** The mace's size while growing: layer by layer (a surge as each one forms, a pause before the next). */
    static float maceSize(float charge) {
        if (charge >= 1) return MACE_MAX;
        float x = Mth.clamp(charge, 0, 1) * 6;
        int i = Math.min(5, (int) Math.floor(x));
        float e = PantherMotion.ease(Math.min(1, (x - i) / .7f));
        return 1 + (MACE_MAX - 1) * (i + e) / 6;
    }
    /** The mace's head bursting (once per action), at the head as it was last drawn. */
    private static void breakHead(int entity, Track tr, IcemanClient.State s, float size, boolean shown) {
        long key = s.start * 4 + 1;
        if (tr.brokeKey == key) return;
        tr.brokeKey = key;
        Vec3[] w = IcemanLayer.weapon(entity);
        if (w == null || !shown) return;
        Vec3 head = w[0].lerp(w[1], IcemanArmory.MACE_HEAD / IcemanArmory.MACE_TIP);
        Vec3 push = w[1].subtract(w[0]).normalize().scale(.08);
        IceParticles.shatter(head, push.add(0, .05, 0), .35f * size + .15f, IceMesh.GLACIER);
        IceParticles.flash(head, .5f + .4f * size, .8f, 5);
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(head.x, head.y, head.z, ModSounds.ICEMAN_SHATTER.get(), SoundSource.PLAYERS, .6f + .2f * size, 1.1f - .1f * size, false);
    }
    /** The sword bursting out of the spin: pieces all along the blade. */
    private static void breakBlade(int entity, Track tr, IcemanClient.State s) {
        long key = s.start * 4 + 2;
        if (tr.brokeKey == key) return;
        tr.brokeKey = key;
        Vec3[] w = IcemanLayer.weapon(entity);
        if (w == null) return;
        Vec3 along = w[1].subtract(w[0]);
        for (int i = 0; i < 6; i++) {
            Vec3 p = w[0].add(along.scale(.2 + .8 * i / 5.0));
            Vec3 out = along.normalize().scale(.06 * i).add(IceParticles.jitter(.08)).add(0, .06, 0);
            for (int k = 0, n = IceParticles.count(3, p); k < n; k++)
                IceParticles.shard(p.add(IceParticles.jitter(.05)), out.add(IceParticles.jitter(.05)), .05f + .05f * IceParticles.rand(), 30 + (int) (IceParticles.rand() * 25), k == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
            IceParticles.snow(p, out, .03f, 18);
        }
        IceParticles.flash(w[0].lerp(w[1], .5), .6f, .8f, 5);
    }

    // ------------------------------------------------------------------ for the poses
    /** The combo the next swing will be (the server's rule: within COMBO_CHAIN ticks of the last swing's end). */
    static int nextCombo(int entity) {
        Track tr = TRACKS.get(entity);
        IcemanClient.State s = IcemanClient.get(entity);
        if (tr == null || s == null || tr.combo >= 2) return 0;
        return s.start - tr.strikeEnd <= COMBO_CHAIN + 1 ? tr.combo + 1 : 0;
    }
    /**
     * The clock fed to a swing's pose with the hit-stop in it: for a moment after a heavy hit lands the swing nearly
     * stops (the weapon held in the blow), then catches up smoothly.
     */
    static float stopped(int entity, int action, float t) {
        Track tr = TRACKS.get(entity);
        IcemanClient.State s = IcemanClient.get(entity);
        if (tr == null || s == null || tr.hitStart != s.start || s.action != action) return t;
        float tau = t - tr.hitAt;
        if (tau <= 0) return t;
        float hold = .9f + 1.3f * tr.hitPower, catchUp = 3.2f, lag = .85f * hold;
        return t - (tau < hold ? .85f * tau : lag * (1 - PantherMotion.ease((tau - hold) / catchUp)));
    }
    private static void hitStop(int entity, float power) {
        IcemanClient.State s = IcemanClient.get(entity);
        if (s == null || !(s.action == STRIKE || s.action == RELEASE)) return;
        Track tr = track(entity);
        float clock = IcemanClient.clock(s, Minecraft.getInstance().getFrameTime());
        if (tr.hitStart == s.start && Math.abs(clock - tr.hitAt) < 1.5f) { tr.hitPower = Math.max(tr.hitPower, power); return; }
        tr.hitStart = s.start; tr.hitAt = clock; tr.hitPower = power;
    }
    /** The Iceman swinging nearest a point (for a hit that names only the body struck), or -1. */
    private static int swinger(Vec3 at) {
        var level = Minecraft.getInstance().level;
        if (level == null) return -1;
        int best = -1; double bestD = 36;
        for (var en : IcemanClient.states().entrySet()) {
            int a = en.getValue().action;
            if (a != STRIKE && a != RELEASE && a != CHARGE) continue;
            Entity e = level.getEntity(en.getKey());
            if (e == null) continue;
            double d = e.position().add(0, 1, 0).distanceToSqr(at);
            if (d < bestD) { bestD = d; best = en.getKey(); }
        }
        return best;
    }
    /** A shake for whoever sees this, weaker with the camera's distance (gone at range). */
    static void shakeAt(Vec3 at, float amount, double range) {
        var mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) return;
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        if (d > range) return;
        float k = (float) (1 - d / range);
        IcemanClient.shake(amount * k * k);
    }

    // ------------------------------------------------------------------ packets
    public static void receive(IcemanFxPacket p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        switch (p.kind()) {
            case FX_FORM -> form(p);
            case FX_HIT -> hit(p);
            case FX_SLAM -> slam(p, level);
            case FX_SPEAR -> IcemanSpearFx.thrown(p);
            case FX_SPEAR_STUCK -> IcemanSpearFx.stuck(p);
            case FX_SPEAR_SPIKES -> IcemanSpearFx.spikes(p.pos(), p.power(), 1);
            case FX_SPIN -> spin(p);
            case FX_SWORD_BREAK -> {
                // The crack before the burst (the pieces come from the drawn blade, IcemanWeaponFx.hold).
                Vec3 at = p.pos();
                IceParticles.flash(at, .4f, .6f, 3);
                for (int i = 0, n = IceParticles.count(10, at); i < n; i++) IceParticles.snow(at.add(IceParticles.jitter(.3)), IceParticles.jitter(.1).add(0, .05, 0), .03f, 16);
                if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.12f);
            }
            default -> {}
        }
    }
    private static void form(IcemanFxPacket p) {
        FORMS.removeIf(f -> f.entity == p.entity());
        if (FORMS.size() > 16) FORMS.remove(0);
        Form f = new Form();
        f.entity = p.entity(); f.weapon = (int) p.power(); f.pos = p.pos(); f.start = gameTime();
        FORMS.add(f);
    }
    private static void hit(IcemanFxPacket p) {
        Vec3 at = p.pos();
        int w = Mth.clamp((int) p.power(), 0, WEAPONS - 1), kind = p.id();
        Vec3 dir = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
        float heavy = w == W_MACE ? 1 : w == W_SWORD ? (kind == 9 ? .4f : .65f) : (kind >= 2 ? .35f : .45f);
        IceParticles.flash(at, .3f + .45f * heavy, .75f, 4);
        for (int i = 0, n = IceParticles.count(Math.round(3 + 8 * heavy), at); i < n; i++) {
            Vec3 v = dir.scale(.12 + .14 * IceParticles.rand()).add(IceParticles.jitter(.09)).add(0, .08 + .08 * IceParticles.rand(), 0);
            IceParticles.shard(at.add(IceParticles.jitter(.08)), v, (.04f + .06f * IceParticles.rand()) * (.7f + .5f * heavy), 25 + (int) (IceParticles.rand() * 25),
                    i % 3 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        }
        for (int i = 0, n = IceParticles.count(Math.round(8 + 14 * heavy), at); i < n; i++)
            IceParticles.snow(at.add(IceParticles.jitter(.1)), dir.scale(.1 + .12 * IceParticles.rand()).add(IceParticles.jitter(.08)).add(0, .04, 0), .025f + .025f * IceParticles.rand(), 14 + (int) (IceParticles.rand() * 14));
        for (int i = 0, n = IceParticles.count(Math.round(1 + 3 * heavy), at); i < n; i++)
            IceParticles.mist(at.add(IceParticles.jitter(.12)), dir.scale(.03).add(IceParticles.jitter(.01)), .22f + .18f * heavy, .02f, .3f, 22);
        if (IMPACTS.size() > 40) IMPACTS.remove(0);
        Impact im = new Impact();
        im.at = at; im.dir = dir; im.start = gameTime(); im.size = .6f + .5f * heavy; im.seed = (int) (IceParticles.rand() * 100000);
        IMPACTS.add(im);
        if (w == W_MACE) {
            IceParticles.ring(at, 1.1f, .18f, IceParticles.COLD_LIGHT, .55f, 6, true);
            shakeAt(at, .16f, 12);
        }
        // The swing nearly stops in the blow (not for the flurry's quick thrusts nor the spin's).
        if (kind <= 1 || kind == SPEAR_FLURRY.length + 1) {
            int who = swinger(at);
            if (who >= 0) {
                hitStop(who, heavy);
                if (IcemanClient.isMe(who)) IcemanClient.shake(.05f + .1f * heavy);
            }
        }
        if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.1f + .12f * heavy);
    }
    private static void slam(IcemanFxPacket p, Level level) {
        boolean giant = p.id() == 1;
        float size = Math.max(1, p.power()), k = Mth.clamp((size - 1) / (MACE_MAX - 1), 0, 1);
        Vec3 at = p.pos();
        // Where the head really came down (as drawn), when it is close to where the server placed it.
        Vec3[] w = IcemanLayer.weapon(p.entity());
        if (w != null) {
            Vec3 head = w[0].lerp(w[1], IcemanArmory.MACE_HEAD / IcemanArmory.MACE_TIP);
            double dx = head.x - at.x, dz = head.z - at.z;
            if (dx * dx + dz * dz < (giant ? 16 : 4)) at = new Vec3(head.x, IcemanSpearFx.groundY(level, head.x, at.y + .5, head.z), head.z);
        }
        float radius = giant ? 2.1f + 4.9f * k : 2.3f;
        if (SLAMS.size() > 8) SLAMS.remove(0);
        Slam s = new Slam();
        s.at = at; s.start = gameTime(); s.radius = radius; s.size = size; s.giant = giant; s.life = giant ? 80 + 40 * k : 50;
        cracks(s, level);
        SLAMS.add(s);
        // The flash, the shock ring and the dust ring, snow and shards thrown up, the mist rolling out.
        IceParticles.flash(at.add(0, .3, 0), .8f + .45f * radius, .95f, 6);
        IceParticles.ring(at.add(0, .06, 0), radius, .3f + .1f * radius, IceParticles.COLD_LIGHT, .85f, giant ? 12 : 8, true);
        IceParticles.ring(at.add(0, .05, 0), radius * 1.25f, .45f + .15f * radius, 0xe8f2ff, .55f, giant ? 22 : 15, false);
        if (giant) IceParticles.ring(at.add(0, .07, 0), radius * 1.7f, .3f + .1f * radius, IceParticles.COLD_LIGHT, .4f, 18, true);
        int shards = IceParticles.count(Math.round(8 + 7 * radius), at);
        for (int i = 0; i < shards; i++) {
            double a = IceParticles.rand() * Mth.TWO_PI;
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 v = o.scale(.1 + .14 * IceParticles.rand() * (1 + k)).add(0, .22 + .25 * IceParticles.rand() * (1 + .6f * k), 0);
            IceParticles.shard(at.add(o.scale(.3 * radius * IceParticles.rand())).add(0, .2, 0), v, (.05f + .08f * IceParticles.rand()) * (1 + .8f * k),
                    35 + (int) (IceParticles.rand() * 30), i % 3 == 0 ? IceMesh.FRESH : i % 3 == 1 ? IceMesh.GLACIER : IceMesh.CLEAR);
        }
        int chunks = IceParticles.count(Math.round(2 + 2 * radius * (giant ? 1 : .5f)), at);
        for (int i = 0; i < chunks; i++) {
            double a = IceParticles.rand() * Mth.TWO_PI;
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            IceParticles.shard(at.add(o.scale(.4)).add(0, .3, 0), o.scale(.08 + .08 * IceParticles.rand()).add(0, .3 + .2 * IceParticles.rand(), 0),
                    (.18f + .14f * IceParticles.rand()) * (1 + 1.2f * k), 55 + (int) (IceParticles.rand() * 30), IceMesh.GLACIER);
        }
        for (int i = 0, n = IceParticles.count(Math.round(20 + 14 * radius), at); i < n; i++) {
            double a = IceParticles.rand() * Mth.TWO_PI, r = radius * .5 * IceParticles.rand();
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            IceParticles.snow(at.add(o.scale(r)).add(0, .15, 0), o.scale(.12 + .2 * IceParticles.rand()).add(0, .1 + .25 * IceParticles.rand(), 0), .03f + .04f * IceParticles.rand(), 20 + (int) (IceParticles.rand() * 20));
        }
        for (int i = 0, n = IceParticles.count(Math.round(6 + 3 * radius), at); i < n; i++) {
            double a = IceParticles.rand() * Mth.TWO_PI;
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            IceParticles.mist(at.add(o.scale(radius * .3)).add(0, .3, 0), o.scale(.08 + .06 * IceParticles.rand()).add(0, .015, 0), .45f + .12f * radius, .03f + .01f * radius, .35f, 40);
        }
        if (giant) IcemanSpearFx.spikes(at, radius * .7f, .55f + .45f * k);
        shakeAt(at, giant ? .45f + .55f * k : .3f, radius * 5 + 8);
        if (IcemanClient.isMe(p.entity())) IcemanClient.kickFov(giant ? .4f + .4f * k : .3f);
        hitStop(p.entity(), giant ? 1.3f : 1);
        level.playLocalSound(at.x, at.y, at.z, ModSounds.ICEMAN_GROUND_CRACK.get(), SoundSource.PLAYERS, .45f + .5f * k, 1.15f - .3f * k, false);
    }
    /** The cracks of a slam: lines running out from the middle over the ground (each point set on the ground under it), branching. */
    private static void cracks(Slam s, Level level) {
        int n = (s.giant ? 9 : 6) + Math.round(3 * (s.size - 1) / (MACE_MAX - 1));
        float speed = 1.3f + .5f * s.radius, base = IceParticles.rand() * Mth.TWO_PI;
        for (int i = 0; i < n; i++) {
            float ang = base + i * Mth.TWO_PI / n + (IceParticles.rand() - .5f) * .5f;
            float len = s.radius * (.65f + .55f * IceParticles.rand());
            crackLine(s, level, s.at.x, s.at.z, ang, len, 0, 6, speed, true);
        }
    }
    private static void crackLine(Slam s, Level level, double x, double z, float ang, float len, float dist, int segs, float speed, boolean branch) {
        float[] pts = new float[(segs + 1) * 4];
        float step = len / segs;
        for (int k = 0; k <= segs; k++) {
            pts[k * 4] = (float) x; pts[k * 4 + 1] = (float) IcemanSpearFx.groundY(level, x, s.at.y + .4, z) + .02f; pts[k * 4 + 2] = (float) z;
            pts[k * 4 + 3] = dist / speed;
            if (branch && k >= 2 && k <= 4 && IceParticles.rand() < .45f)
                crackLine(s, level, x, z, ang + (IceParticles.rand() < .5f ? -1 : 1) * (.45f + .4f * IceParticles.rand()), len * .4f, dist, 3, speed, false);
            ang += (IceParticles.rand() - .5f) * .7f;
            x += -Mth.sin(ang) * step; z += Mth.cos(ang) * step;
            dist += step;
        }
        s.cracks.add(pts);
    }
    private static void spin(IcemanFxPacket p) {
        Vec3 c = p.pos().add(0, 1, 0);
        boolean hold = p.power() >= .5f;
        int snow = IceParticles.count(hold ? 10 : 30, c), shards = IceParticles.count(hold ? 2 : 7, c);
        // He turns to his left: things leave his blades along the turn and outward.
        for (int i = 0; i < snow + shards; i++) {
            float th = IceParticles.rand() * Mth.TWO_PI, r = 1.0f + 1.4f * IceParticles.rand();
            Vec3 out = new Vec3(-Mth.sin(th), 0, Mth.cos(th)), along = new Vec3(Mth.cos(th), 0, Mth.sin(th));
            Vec3 at = c.add(out.scale(r)).add(0, (IceParticles.rand() - .5) * .6, 0);
            Vec3 v = along.scale(.25 + .15 * IceParticles.rand()).add(out.scale(.1 + .08 * IceParticles.rand())).add(0, .04 + .05 * IceParticles.rand(), 0);
            if (i < snow) IceParticles.snow(at, v, .03f + .03f * IceParticles.rand(), 16 + (int) (IceParticles.rand() * 14));
            else IceParticles.shard(at, v, .05f + .05f * IceParticles.rand(), 28 + (int) (IceParticles.rand() * 20), i % 2 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        }
        if (!hold) {
            IceParticles.ring(p.pos().add(0, .06, 0), 2.6f, .25f, IceParticles.COLD_LIGHT, .55f, 7, true);
            IceParticles.ring(p.pos().add(0, .05, 0), 2.9f, .5f, 0xeef6ff, .35f, 12, false);
        }
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { clear(); lastLevel = mc.level; }
        if (mc.level == null || mc.isPaused()) return;
        Level level = mc.level;
        float now = level.getGameTime();
        IcemanSpearFx.tick(level);
        // The weapon forming: mist drawn in toward the hand, a flash as it closes.
        for (int i = FORMS.size() - 1; i >= 0; i--) {
            Form f = FORMS.get(i);
            float t = now - f.start;
            Vec3 hand = formHand(f);
            if (t < 7) {
                for (int k = 0, n = IceParticles.count(2, hand); k < n; k++) {
                    Vec3 from = hand.add(IceParticles.jitter(1).normalize().scale(.8 + .4 * IceParticles.rand()));
                    IceParticles.mist(from, hand.subtract(from).scale(.18), .14f, -.008f, .22f, 9);
                    IceParticles.snow(from, hand.subtract(from).scale(.16), .02f, 8);
                }
            }
            if (!f.closed && t >= FORM_TICKS - 1) {
                f.closed = true;
                IceParticles.flash(hand, .45f, .6f, 4);
                IceParticles.frostDust(hand, Vec3.ZERO, 1.5f);
            }
            if (t > FORM_TICKS + 6) FORMS.remove(i);
        }
        IMPACTS.removeIf(m -> now - m.start > 12);
        SLAMS.removeIf(s -> now - s.start > s.life);
        // Per Iceman: snow off a fast weapon tip, the held spin's vortex drawing mist in, the mace's rumble as it grows.
        for (var en : IcemanClient.states().entrySet()) {
            int id = en.getKey();
            IcemanClient.State s = en.getValue();
            Entity ent = level.getEntity(id);
            if (ent == null) continue;
            Track tr = track(id);
            float t = IcemanClient.clock(s, 0);
            Vec3[] w = IcemanLayer.weapon(id);
            boolean swinging = s.action == STRIKE || s.action == RELEASE || s.action == CHARGE && s.weapon == W_SWORD;
            if (w != null && swinging) {
                if (tr.lastTip != null) {
                    Vec3 v = w[1].subtract(tr.lastTip);
                    double sp = v.length();
                    if (sp > .25 && sp < 6) {
                        for (int k = 0, n = IceParticles.count((int) Math.min(5, 1 + sp * 2), w[1]); k < n; k++)
                            IceParticles.snow(tr.lastTip.lerp(w[1], IceParticles.rand()), v.scale(.12).add(IceParticles.jitter(.03)), .022f + .02f * IceParticles.rand(), 12 + (int) (IceParticles.rand() * 10));
                        if (IceParticles.rand() < .3f) IceParticles.mist(w[1], v.scale(.05), .15f, .015f, .18f, 16);
                    }
                }
                tr.lastTip = w[1];
            } else tr.lastTip = null;
            if (s.action == CHARGE && s.weapon == W_SWORD && t >= HOLD_TICKS) {
                float held = t - HOLD_TICKS, rOut = (float) (double) IcemanConfig.SWORD_PULL_RADIUS.get();
                Vec3 c = ent.position().add(0, .8, 0);
                for (int k = 0, n = IceParticles.count(2, c); k < n; k++) {
                    float th = IceParticles.rand() * Mth.TWO_PI, r = rOut * (.7f + .3f * IceParticles.rand());
                    Vec3 out = new Vec3(-Mth.sin(th), 0, Mth.cos(th)), along = new Vec3(Mth.cos(th), 0, Mth.sin(th));
                    Vec3 at = c.add(out.scale(r)).add(0, (IceParticles.rand() - .4) * 1.2, 0);
                    float pull = .05f + .04f * Math.min(1, held / 30);
                    IceParticles.mist(at, out.scale(-pull * r * .4).add(along.scale(pull * r * .3)), .3f, .01f, .22f, 18);
                    IceParticles.snow(at, out.scale(-pull * r * .5).add(along.scale(pull * r * .4)).add(0, .02, 0), .025f, 16);
                }
            }
            if (s.action == CHARGE && s.weapon == W_MACE && t >= HOLD_TICKS) {
                float charge = Mth.clamp((t - HOLD_TICKS) / MACE_GROW, 0, 1);
                int layer = (int) Math.min(6, charge * 6);
                boolean me = IcemanClient.isMe(id);
                if (layer != tr.layer) {
                    if (tr.layer >= 0 && layer > tr.layer) {
                        if (me) { IcemanClient.shake(.1f + .04f * layer); IcemanClient.kickFov(.07f + .025f * layer); }
                        else shakeAt(ent.position(), .05f + .02f * layer, 14);
                    }
                    tr.layer = layer;
                }
                if (me && (int) now % 3 == 0) IcemanClient.shake(.025f + .035f * charge);
                if (w != null) {
                    float size = maceSize(charge);
                    Vec3 head = w[0].lerp(w[1], IcemanArmory.MACE_HEAD / IcemanArmory.MACE_TIP);
                    float r = .2f * size;
                    if (IceParticles.rand() < .5f + .3f * charge)
                        IceParticles.mist(head.add(IceParticles.jitter(r)), new Vec3(0, -.012, 0), .2f + .12f * size, .015f, .2f, 26);
                    IceParticles.frostDust(head.add(IceParticles.jitter(r)), Vec3.ZERO, .5f + .3f * size);
                }
            } else tr.layer = -1;
        }
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean any = !FORMS.isEmpty() || !IMPACTS.isEmpty() || !SLAMS.isEmpty() || !IcemanSpearFx.idle();
        if (!any) for (IcemanClient.State s : IcemanClient.states().values()) if (s.action == STRIKE || s.action == CHARGE || s.action == RELEASE) { any = true; break; }
        if (!any) { for (Track tr : TRACKS.values()) tr.count = 0; return; }
        IceStage st = IceStage.open(e);
        if (st == null) return;
        try {
            float now = st.time;
            sample(now);
            IceMesh.Ctx c = st.ice();
            for (Impact m : IMPACTS) impact(c, m, now);
            for (Slam s : SLAMS) slamGlints(c, s, now);
            IcemanSpearFx.drawIce(st, c);
            st.endIce();
            FilmContext f = st.fx();
            for (var en : TRACKS.entrySet()) trail(f, en.getKey(), en.getValue(), now);
            for (Form fm : FORMS) formFx(f, fm, now);
            for (Slam s : SLAMS) slamFx(f, s, now);
            for (var en : IcemanClient.states().entrySet()) vortex(f, en.getKey(), en.getValue(), st.partial, now);
            IcemanSpearFx.drawFx(st, f);
        } finally {
            st.close();
        }
    }

    /** Records where each swinging Iceman's weapon is this frame (for the trails). */
    private static void sample(float now) {
        for (var en : IcemanClient.states().entrySet()) {
            IcemanClient.State s = en.getValue();
            Track tr = TRACKS.get(en.getKey());
            if (tr == null) continue;
            boolean swinging = s.action == STRIKE || s.action == RELEASE || s.action == CHARGE && s.weapon == W_SWORD && IcemanClient.clock(s, 0) >= HOLD_TICKS;
            Vec3[] w = swinging ? IcemanLayer.weapon(en.getKey()) : null;
            if (w == null) continue;
            if (tr.count > 0 && now - tr.last < .1f) continue;
            if (tr.count > 0 && now - tr.last > 6) tr.count = 0;
            float inner = s.weapon == W_MACE ? .5f : s.weapon == W_SPEAR ? .72f : .3f;
            Vec3 a = w[0].lerp(w[1], inner);
            int i = tr.head;
            tr.pts[i * 6] = (float) a.x; tr.pts[i * 6 + 1] = (float) a.y; tr.pts[i * 6 + 2] = (float) a.z;
            tr.pts[i * 6 + 3] = (float) w[1].x; tr.pts[i * 6 + 4] = (float) w[1].y; tr.pts[i * 6 + 5] = (float) w[1].z;
            tr.when[i] = now;
            tr.head = (i + 1) % TRAIL;
            tr.count = Math.min(TRAIL, tr.count + 1);
            tr.last = now;
        }
    }
    /** A ribbon of cold light between the samples (and a faint pale one for daylight), fading with age. */
    private static void trail(FilmContext f, int id, Track tr, float now) {
        if (tr.count < 2) return;
        IcemanClient.State s = IcemanClient.get(id);
        int weapon = s == null ? W_SWORD : s.weapon;
        float life = weapon == W_MACE ? 5 : weapon == W_SPEAR ? 3 : 4;
        var mc = Minecraft.getInstance();
        boolean own = IcemanClient.isMe(id) && mc.options.getCameraType().isFirstPerson();
        float strength = own ? .45f : 1;
        Matrix4f m = f.pose().last().pose();
        VertexConsumer add = f.buffers().getBuffer(FilmFx.ADD);
        VertexConsumer soft = f.buffers().getBuffer(FilmFx.SOFT);
        int n = tr.count;
        for (int k = 0; k < n - 1; k++) {
            int i = Math.floorMod(tr.head - n + k, TRAIL), j = (i + 1) % TRAIL;
            float ai = 1 - (now - tr.when[i]) / life, aj = 1 - (now - tr.when[j]) / life;
            if (aj <= 0) continue;
            ai = Math.max(0, ai);
            float[] p = tr.pts;
            float dx = p[j * 6 + 3] - p[i * 6 + 3], dy = p[j * 6 + 4] - p[i * 6 + 4], dz = p[j * 6 + 5] - p[i * 6 + 5];
            float jump = dx * dx + dy * dy + dz * dz;
            if (jump > 9 || jump < 1e-6f) continue;
            float ea = ai * ai * strength, eb = aj * aj * strength;
            quad(add, m, p, i, j, .55f, .82f, 1f, .7f * ea, .7f * eb);
            quad(soft, m, p, i, j, .93f, .97f, 1f, .16f * ea, .16f * eb);
        }
    }
    /** One piece of ribbon: inner edge clear, the tip edge bright. */
    private static void quad(VertexConsumer v, Matrix4f m, float[] p, int i, int j, float r, float g, float b, float ai, float aj) {
        v.vertex(m, p[i * 6], p[i * 6 + 1], p[i * 6 + 2]).color(r, g, b, 0).endVertex();
        v.vertex(m, p[i * 6 + 3], p[i * 6 + 4], p[i * 6 + 5]).color(r, g, b, Mth.clamp(ai, 0, 1)).endVertex();
        v.vertex(m, p[j * 6 + 3], p[j * 6 + 4], p[j * 6 + 5]).color(r, g, b, Mth.clamp(aj, 0, 1)).endVertex();
        v.vertex(m, p[j * 6], p[j * 6 + 1], p[j * 6 + 2]).color(r, g, b, 0).endVertex();
    }
    private static Vec3 formHand(Form f) {
        Vec3 h = IcemanLayer.hand(f.entity, 0);
        return h == null ? f.pos : h;
    }
    /** The humidity drawn into his hand: bright streams spiralling in, a glow gathering. */
    private static void formFx(FilmContext f, Form fm, float now) {
        float t = now - fm.start;
        Vec3 hand = formHand(fm);
        float e = Mth.clamp(t / 7.5f, 0, 1), fade = Mth.clamp(t / 1.2f, 0, 1) * (1 - Mth.clamp((t - 8) / 4, 0, 1));
        for (int j = 0; j < 7; j++) {
            Vec3 d = new Vec3(IceMesh.hash(j * 3.1 + fm.entity) - .5, IceMesh.hash(j * 5.7 + fm.entity) - .3, IceMesh.hash(j * 7.3 + fm.entity) - .5).normalize();
            Vec3 head = spiral(hand, d, e), tail = spiral(hand, d, Math.max(0, e - .14f));
            float a = .55f * fade * Mth.sqrt(1 - e);
            FilmFx.streak(f, tail, head, .035, IceParticles.COLD_LIGHT, 0, a, true);
            FilmFx.glow(f, head, .06, 0xeaf6ff, a);
        }
        float bloom = Mth.sin(Mth.PI * Mth.clamp(t / FORM_TICKS, 0, 1));
        FilmFx.glow(f, hand, .2 + .3 * e, IceParticles.COLD_LIGHT, .45f * bloom * fade);
        FilmFx.puff(f, hand, .18 + .15 * e, 0xeef6ff, .12f * bloom);
    }
    private static Vec3 spiral(Vec3 hand, Vec3 d, float e) {
        float r = 1.15f * (float) Math.pow(1 - e, 1.3), a = e * 2.4f;
        double c = Math.cos(a), s = Math.sin(a);
        return hand.add((d.x * c - d.z * s) * r, d.y * r, (d.x * s + d.z * c) * r);
    }
    /** A hit's frost burst: small ice cubes popping out of the impact (frozen on, a little apart), then melting away. */
    private static void impact(IceMesh.Ctx c, Impact m, float now) {
        float t = now - m.start;
        if (t < 0 || t > 12) return;
        float grow = 1 - (1 - Mth.clamp(t / 1.6f, 0, 1)) * (1 - Mth.clamp(t / 1.6f, 0, 1));
        float melt = Mth.clamp((t - 4) / 8, 0, 1);
        c.light = IceStage.light(m.at);
        for (int i = 0; i < 5; i++) {
            double h = IceMesh.hash(m.seed + i * 2.2);
            Vec3 d = new Vec3(IceMesh.hash(m.seed + i * 3.1) - .5, IceMesh.hash(m.seed + i * 5.3) - .3, IceMesh.hash(m.seed + i * 7.9) - .5).normalize().add(m.dir.scale(.7)).normalize();
            float size = (.07f + .07f * (float) h) * m.size * grow * (1 - .6f * melt);
            if (size <= .004f) continue;
            Vec3 at = m.at.add(d.scale(size * .5 + .02));
            IceParticles.cube(c, at.x, at.y, at.z, size, size, size, (float) (h * 6.3), (float) (IceMesh.hash(m.seed + i * 4.4) - .5) * 1.5f, (float) (h - .5) * 1.5f,
                    i % 2 == 0 ? IceMesh.CLEAR : IceMesh.MILKY, 1 - melt);
        }
        if (t < 3) IceMesh.glow(c, m.at, .45f * m.size * (1 - t / 3), .25f, .4f, .55f);
    }
    /** A slam's cracks on the ground: light running out along them, then fading. */
    private static void slamGlints(IceMesh.Ctx c, Slam s, float now) {
        float t = now - s.start;
        float fade = 1 - Mth.clamp((t - s.life * .55f) / (s.life * .45f), 0, 1);
        if (fade <= 0) return;
        float w = .035f + .02f * Mth.sqrt(s.size);
        float flick = .85f + .15f * Mth.sin(t * .9f);
        for (float[] pts : s.cracks) {
            for (int k = 0; k + 1 < pts.length / 4; k++) {
                float open = Mth.clamp((t - pts[k * 4 + 3]) / .8f, 0, 1);
                if (open <= 0) break;
                Vec3 a = new Vec3(pts[k * 4], pts[k * 4 + 1], pts[k * 4 + 2]), b = new Vec3(pts[k * 4 + 4], pts[k * 4 + 5], pts[k * 4 + 6]);
                b = a.lerp(b, open);
                float hot = 1 + .8f * Math.max(0, 1 - (t - pts[k * 4 + 3]) / 6);
                float br = fade * flick * Math.min(1.2f, hot) * (1 - .25f * k / (pts.length / 4f));
                IceMesh.line(c, a, b, w * (1 - .4f * k / (pts.length / 4f)), .45f * br, .78f * br, br);
            }
        }
    }
    /** A slam's frost on the ground (pale, so it reads in daylight). */
    private static void slamFx(FilmContext f, Slam s, float now) {
        float t = now - s.start;
        float fade = 1 - Mth.clamp((t - s.life * .5f) / (s.life * .5f), 0, 1), grow = Mth.clamp(t / 4, 0, 1);
        if (fade <= 0) return;
        FilmFx.ring(f, s.at.add(0, .025, 0), s.radius * .45 * grow, s.radius * .45 * grow, 0xeef6ff, .3f * fade, false);
        for (float[] pts : s.cracks) {
            for (int k = 0; k + 1 < pts.length / 4; k++) {
                float open = Mth.clamp((t - pts[k * 4 + 3]) / .8f, 0, 1);
                if (open <= 0) break;
                Vec3 a = new Vec3(pts[k * 4], pts[k * 4 + 1] + .005, pts[k * 4 + 2]), b = new Vec3(pts[k * 4 + 4], pts[k * 4 + 5] + .005, pts[k * 4 + 6]);
                FilmFx.streak(f, a, a.lerp(b, open), .06 + .02 * s.size, 0xf2f9ff, .35f * fade, .35f * fade, false);
            }
        }
    }
    /** The held spin's vortex: streams of mist spiralling in round him, faster as the spin speeds up. */
    private static void vortex(FilmContext f, int id, IcemanClient.State s, float partial, float now) {
        if (s.action != CHARGE || s.weapon != W_SWORD) return;
        float t = IcemanClient.clock(s, partial);
        if (t < HOLD_TICKS) return;
        var level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(id);
        if (e == null) return;
        float held = t - HOLD_TICKS;
        float in = Mth.clamp(held / 8, 0, 1), speed = IcemanWeaponMotion.spinSpeed(held) / (Mth.TWO_PI / SPIN_TURN);
        float rOut = (float) (double) IcemanConfig.SWORD_PULL_RADIUS.get();
        Vec3 c = e.getPosition(partial);
        // Phases from the spin's own clock (smooth as it speeds up): the streams turn with him, their dashes run inward.
        float turned = IcemanWeaponMotion.spinTurned(held), turn = .05f * held + .35f * turned, flow = .8f * held + .9f * turned;
        int segs = 12;
        for (int j = 0; j < 6; j++) {
            float th0 = j * Mth.TWO_PI / 6 - turn, h = .3f + 1.3f * (float) IceMesh.hash(j * 7.7 + id);
            Vec3 prev = null;
            for (int m = 0; m <= segs; m++) {
                float u = m / (float) segs;
                float r = Mth.lerp(u, rOut, .9f), th = th0 - u * 2.6f;
                Vec3 p = c.add(-Mth.sin(th) * r, h + .2f * Mth.sin(u * 3 + held * .2f + j), Mth.cos(th) * r);
                if (prev != null) {
                    float dash = .55f + .45f * Mth.sin(u * 12 - flow + j);
                    float a = in * Mth.sin(Mth.PI * u) * dash;
                    FilmFx.streak(f, prev, p, .14 + .14 * (1 - u), 0xeef6ff, .13f * a, .13f * a, false);
                    FilmFx.streak(f, prev, p, .05, IceParticles.COLD_LIGHT, .12f * a * speed, .12f * a * speed, true);
                }
                prev = p;
            }
        }
    }

    // ------------------------------------------------------------------ cleanup
    static void clear() {
        TRACKS.clear(); FORMS.clear(); IMPACTS.clear(); SLAMS.clear();
        IcemanSpearFx.clear();
        IcemanWeaponMotion.clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); lastLevel = null; }
}
