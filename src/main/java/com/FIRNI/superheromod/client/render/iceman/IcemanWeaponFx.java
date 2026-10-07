package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanConfig;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The Ice Armory on the clients: what his hand holds each draw (forming, carried, grown, cracking, broken), and the
 * effects of the weapons. Nothing glows like a magic blade: the cold is vapour, ice crystals and frost.
 * <ul>
 * <li>Swings: the air freezing along the weapon's real path (IceParticles.freezeTrail sampled between where its tip and
 * its middle were drawn): the mace heavy, slow and dense; the spear a thin fast short streak; the sword a smooth misty
 * arc (a faint pale ribbon of vapour behind the mace and the sword too).</li>
 * <li>FX_FORM: each weapon's own growth in the hand (IcemanArmory) inside a cold plume: vapour and ice crystals drawn into
 * the palm, frost when it closes (the mace's cold rolling down off it).</li>
 * <li>FX_HIT: a burst of frost on the body, small crystals growing out of the impact and melting, shards, vapour; heavier
 * for the mace (a shake, shards landing after). A short hit-stop for the swinging Iceman's pose.</li>
 * <li>FX_SLAM: the ground cracks running out, a cluster of ice crystals erupting out of the ground at the impact
 * (IcemanSpearFx.erupt; the giant mace's several times bigger), a frost burst, shards, cold mist rolling out, the camera
 * shaken by distance and size; the mace's head cracks on the ground, then breaks apart in heavy chunks.</li>
 * <li>FX_SPEAR / FX_SPEAR_STUCK / FX_SPEAR_SPIKES: IcemanSpearFx.</li>
 * <li>FX_SWORD_PLANT: the sword planted in the ground (drawn there, the blade part under the surface): crystals growing
 * at its foot, frost spreading over the ground, and from all round the cold drawn in toward it: lines of small crystals
 * growing along the ground toward the sword one after another, ground frost creeping in, vapour and tiny ice crystals
 * flowing inward. FX_SWORD_BREAK: it cracks where it stands, then shatters in blade-like pieces; the mist lingers.</li>
 * <li>FX_WEAPON_BREAK: the weapon in his hand cracks (WEAPON_CRACK_TICKS), then falls apart (the staged break: heavy
 * chunks for the mace, long shards for the spear, blade pieces for the sword).</li>
 * <li>The mace growing: a rumble at every new layer, frost and vapour rolling off the head, more as it grows; at full
 * size the ground frosts over under him. The spear drawn back: frost and crystals gathering on its point.</li>
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
        // the misty ribbon: inner point and tip (world) per sample, and when
        final float[] pts = new float[TRAIL * 6]; final float[] when = new float[TRAIL]; int head, count; float last = -1;
        Vec3 lastTip, lastMid;
        // the flurry's thrusts already given their cold
        int thrust = -1; long thrustKey = Long.MIN_VALUE;
        // a weapon breaking in his hand
        float breakAt = -1, breakSize = 1; int breakWeapon, breakHow; Vec3 breakPos = Vec3.ZERO, breakDir = Vec3.ZERO;
        // the frost on the ground under a full-grown mace
        float frost;
    }
    private static final int TRAIL = 48;
    private static final Map<Integer, Track> TRACKS = new HashMap<>();

    private static final class Form { int entity, weapon; Vec3 pos; float start; boolean closed; }
    private static final class Impact { Vec3 at, dir; float start, size; int seed; }
    private static final class Slam { Vec3 at; float start, life, radius, size; boolean giant; final List<float[]> cracks = new ArrayList<>(); }
    private static final class Streak { Vec3 a, b; float start; }
    /** A line of small crystals growing along the ground toward the planted sword (from the outside in). */
    private static final class Chain { float[] pts; float start, len, meltAt = -1; int n, seed; Vec3 lean, from; }
    private static final class Plant {
        int entity, id, seed; Vec3 at, dir; float start, breakAt = -1, end = -1; boolean shattered; float nextChain;
        final List<Chain> chains = new ArrayList<>();
    }
    private static final List<Form> FORMS = new ArrayList<>();
    private static final List<Impact> IMPACTS = new ArrayList<>();
    private static final List<Slam> SLAMS = new ArrayList<>();
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static final List<Plant> PLANTS = new ArrayList<>();
    private static Level lastLevel;

    static float gameTime() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime();
    }
    private static Track track(int entity) {
        if (TRACKS.size() > 64) TRACKS.clear();
        return TRACKS.computeIfAbsent(entity, k -> new Track());
    }
    private static void sound(Vec3 at, SoundEvent ev, float vol, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.x, at.y, at.z, ev, SoundSource.PLAYERS, vol, pitch, false);
    }

    // ------------------------------------------------------------------ what the hand holds
    /** Sets what his right hand holds for this draw (IcemanBody.WEAPON*, IcemanArmory.TURN), from his state; called by whoever draws him. */
    public static void hold(int entity, IcemanClient.State s, int action, float t, float now) {
        Track tr = track(entity);
        if (action != tr.action || s.start != tr.start) {
            if (tr.action == STRIKE) tr.strikeEnd = now;
            tr.action = action; tr.start = s.start;
        }
        if (action == STRIKE) tr.combo = s.combo;
        int w = Mth.clamp(s.weapon, 0, WEAPONS - 1);
        boolean show = s.weaponOut();
        float form = 1, size = 1, crack = 0, turn = 0;
        switch (action) {
            case FORM -> { show = true; form = Mth.clamp(t / FORM_TICKS, 0, 1); }
            case STRIKE -> {
                if (w == W_MACE && s.combo == 2) {
                    // The overhead slam: the head cracks on the ground, then breaks apart; the handle stays in the fist.
                    int hit = SWING_HIT[W_MACE][2];
                    crack = t < hit ? 0 : t < hit + 6 ? .15f + .85f * (t - hit) / 6f : 2;
                    if (t >= hit + 6) breakHead(entity, tr, s, 1, show);
                }
            }
            case CHARGE -> {
                float held = t - HOLD_TICKS;
                if (w == W_MACE) {
                    size = maceSize(Mth.clamp(held / MACE_GROW, 0, 1));
                    crack = .25f * PantherMotion.k(held, MACE_GROW, MACE_GROW + 8);
                } else if (w == W_SPEAR) size = 1 + .6f * PantherMotion.ease(Mth.clamp(held / SPEAR_DRAW, 0, 1));
                else if (held >= 0) {
                    // Turned point down in his hand, then driven in: from then on it stands in the ground (drawn there).
                    turn = Mth.PI * PantherMotion.ease(held / PLANT_TURN);
                    if (held >= PLANT_AT && plantFor(entity) != null) show = false;
                }
            }
            case RELEASE -> {
                if (w == W_MACE) {
                    show = show || t < MACE_SLAM_TICKS;
                    size = maceSize(s.charge);
                    crack = t < MACE_SLAM_HIT ? 0 : t < MACE_SLAM_HIT + 8 ? .15f + .85f * (t - MACE_SLAM_HIT) / 8f : 2;
                    if (t >= MACE_SLAM_HIT + 8) breakHead(entity, tr, s, size, show);
                } else if (w == W_SPEAR) {
                    show = t < SPEAR_THROW_AT;
                    size = 1 + .6f * PantherMotion.ease(Mth.clamp(s.charge, 0, 1));
                } else if (plantFor(entity) != null || t > 2) show = false;
                else {
                    // Never planted on this client (it came into view late): it breaks in his hand.
                    show = t < 1.2f;
                    turn = Mth.PI;
                    crack = .6f + .4f * Mth.clamp(t / 1.2f, 0, 1);
                    if (!show) breakBlade(entity, tr, s);
                }
            }
            default -> {}
        }
        // A weapon broken in his hand: it cracks there before it falls apart (tick).
        if (!show && tr.breakAt >= 0 && action != FORM) {
            show = true;
            w = tr.breakWeapon; size = tr.breakSize; form = 1; turn = 0;
            crack = tr.breakHow == 1 ? 2 : .15f + .85f * Mth.clamp((now - tr.breakAt) / WEAPON_CRACK_TICKS, 0, 1);
        }
        IcemanBody.WEAPON = show ? w : -1;
        IcemanBody.WEAPON_FORM = form;
        IcemanBody.WEAPON_SIZE = size;
        IcemanBody.WEAPON_CRACK = crack;
        IcemanArmory.TURN = show ? turn : 0;
    }
    /** The mace's size while growing: layer by layer (a surge as each one forms, a pause before the next). */
    static float maceSize(float charge) {
        if (charge >= 1) return MACE_MAX;
        float x = Mth.clamp(charge, 0, 1) * 6;
        int i = Math.min(5, (int) Math.floor(x));
        float e = PantherMotion.ease(Math.min(1, (x - i) / .7f));
        return 1 + (MACE_MAX - 1) * (i + e) / 6;
    }
    /** The mace's head breaking apart on the ground (once per action), at the head as it was last drawn: heavy chunks, zone by zone. */
    private static void breakHead(int entity, Track tr, IcemanClient.State s, float size, boolean shown) {
        long key = s.start * 4 + 1;
        if (tr.brokeKey == key) return;
        tr.brokeKey = key;
        Vec3[] w = IcemanLayer.weapon(entity);
        if (w == null || !shown) return;
        Vec3 head = w[0].lerp(w[1], IcemanArmory.MACE_HEAD / IcemanArmory.MACE_TIP), axis = w[1].subtract(w[0]);
        float r = IcemanArmory.MACE_R * (1 + .45f * (size - 1)) / 16f;
        IceParticles.breakApart(head, axis, r * 2.4f, .35f * size + .2f, axis.normalize().scale(.05).add(0, .06, 0), 3 + Math.round(size), 8 + Math.round(2 * size), IceMesh.GLACIER);
        for (int i = 0, n = IceParticles.count(Math.round(2 + size), head); i < n; i++)
            IceParticles.shard(head.add(IceParticles.jitter(r * .5)), IceParticles.jitter(.08).add(0, .14, 0), (.2f + .12f * IceParticles.rand()) * (.7f + .3f * size), 50 + (int) (IceParticles.rand() * 30), IceMesh.GLACIER);
        IceParticles.coldMist(head, 1.2f + .5f * size, .8f);
    }
    /** The sword breaking in his hand (only when it was never seen planted). */
    private static void breakBlade(int entity, Track tr, IcemanClient.State s) {
        long key = s.start * 4 + 2;
        if (tr.brokeKey == key) return;
        tr.brokeKey = key;
        Vec3[] w = IcemanLayer.weapon(entity);
        if (w == null) return;
        IceParticles.breakApart(w[0].lerp(w[1], .5), w[1].subtract(w[0]), .9f, .3f, new Vec3(0, .04, 0), 4, 8, IceMesh.CLEAR);
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
    /** This Iceman's planted sword still standing (null: none). */
    private static Plant plantFor(int entity) {
        for (int i = PLANTS.size() - 1; i >= 0; i--) {
            Plant p = PLANTS.get(i);
            if (p.entity == entity && !p.shattered) return p;
        }
        return null;
    }
    /** Where this Iceman's sword stands (or stood, while its pieces still fly: he rises from there); null when none. */
    static Vec3 plantAt(int entity, boolean standingOnly) {
        for (int i = PLANTS.size() - 1; i >= 0; i--) {
            Plant p = PLANTS.get(i);
            if (p.entity == entity && !(standingOnly && p.shattered)) return p.at;
        }
        return null;
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
            case FX_SWORD_PLANT -> plant(p);
            case FX_SWORD_BREAK -> swordBreak(p);
            case FX_WEAPON_BREAK -> weaponBreak(p);
            default -> {}
        }
    }
    private static void form(IcemanFxPacket p) {
        FORMS.removeIf(f -> f.entity == p.entity());
        if (FORMS.size() > 16) FORMS.remove(0);
        Form f = new Form();
        f.entity = p.entity(); f.weapon = (int) p.power(); f.pos = p.pos(); f.start = gameTime();
        FORMS.add(f);
        // A new weapon forming ends any old one still cracking in the hand (it falls apart now).
        Track tr = TRACKS.get(p.entity());
        if (tr != null && tr.breakAt >= 0) fallApart(p.entity(), tr);
    }
    private static void hit(IcemanFxPacket p) {
        Vec3 at = p.pos();
        int w = Mth.clamp((int) p.power(), 0, WEAPONS - 1), kind = p.id();
        Vec3 dir = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
        float heavy = w == W_MACE ? 1 : w == W_SWORD ? (kind == 9 ? .4f : .65f) : (kind >= 2 ? .35f : .45f);
        // The frost bursting off them: vapour and ice crystals thrown along the blow, broken ice.
        IceParticles.cryo(at, dir.add(0, .3, 0), .45f + .6f * heavy, .7f);
        for (int i = 0, n = IceParticles.count(Math.round(2 + 6 * heavy), at); i < n; i++) {
            Vec3 v = dir.scale(.12 + .14 * IceParticles.rand()).add(IceParticles.jitter(.09)).add(0, .08 + .08 * IceParticles.rand(), 0);
            IceParticles.shard(at.add(IceParticles.jitter(.08)), v, (.04f + .06f * IceParticles.rand()) * (.7f + .5f * heavy), 25 + (int) (IceParticles.rand() * 25),
                    i % 3 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        }
        if (IMPACTS.size() > 40) IMPACTS.remove(0);
        Impact im = new Impact();
        im.at = at; im.dir = dir; im.start = gameTime(); im.size = .6f + .6f * heavy; im.seed = (int) (IceParticles.rand() * 100000);
        IMPACTS.add(im);
        if (w == W_MACE) {
            shakeAt(at, .16f, 12);
            // The shards coming down after the heavy blow.
            IceParticles.later(7, () -> sound(at, ModSounds.ICEMAN_SHARD_RAIN.get(), .3f, 1.2f));
        }
        // The swing nearly stops in the blow (not for the flurry's quick thrusts nor the planted sword's).
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
        // The ice impact: a cluster of crystals growing out of the ground where it struck.
        if (giant) IcemanSpearFx.erupt(at, radius * .75f, 1.4f + 1.8f * k, 4 + Math.round(3 * k), 8 + Math.round(8 * k));
        else IcemanSpearFx.erupt(at, 1.5f, 1.1f, 3, 6);
        // The frost burst, the shock of air on the ground, broken ice thrown up, the cold rolling out.
        IceParticles.flash(at.add(0, .3, 0), .5f + .3f * radius, .6f, 5);
        IceParticles.ring(at.add(0, .05, 0), radius * 1.25f, .45f + .15f * radius, 0xe8f2ff, .5f, giant ? 22 : 15, false);
        IceParticles.cryo(at.add(0, .2, 0), new Vec3(0, 1, 0), 1 + .8f * k + (giant ? .4f : 0), 1);
        int shards = IceParticles.count(Math.round(8 + 6 * radius), at);
        for (int i = 0; i < shards; i++) {
            double a = IceParticles.rand() * Mth.TWO_PI;
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 v = o.scale(.1 + .14 * IceParticles.rand() * (1 + k)).add(0, .22 + .25 * IceParticles.rand() * (1 + .6f * k), 0);
            IceParticles.shard(at.add(o.scale(.3 * radius * IceParticles.rand())).add(0, .2, 0), v, (.05f + .08f * IceParticles.rand()) * (1 + .8f * k),
                    35 + (int) (IceParticles.rand() * 30), i % 3 == 0 ? IceMesh.FRESH : i % 3 == 1 ? IceMesh.GLACIER : IceMesh.CLEAR);
        }
        for (int i = 0, n = IceParticles.count(Math.round(14 + 10 * radius), at); i < n; i++) {
            double a = IceParticles.rand() * Mth.TWO_PI, r = radius * .5 * IceParticles.rand();
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            IceParticles.snow(at.add(o.scale(r)).add(0, .15, 0), o.scale(.12 + .2 * IceParticles.rand()).add(0, .1 + .25 * IceParticles.rand(), 0), .03f + .04f * IceParticles.rand(), 20 + (int) (IceParticles.rand() * 20));
        }
        IceParticles.coldMist(at, radius * 1.1f, 1 + .5f * k);
        shakeAt(at, giant ? .45f + .55f * k : .3f, radius * 5 + 8);
        if (IcemanClient.isMe(p.entity())) IcemanClient.kickFov(giant ? .4f + .4f * k : .3f);
        hitStop(p.entity(), giant ? 1.3f : 1);
        level.playLocalSound(at.x, at.y, at.z, ModSounds.ICEMAN_GROW_RUMBLE.get(), SoundSource.PLAYERS, .55f + .5f * k, .8f - .25f * k, false);
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
    /** The sword's finisher spin: frost and broken ice flung off the blade along the turn. */
    private static void spin(IcemanFxPacket p) {
        Vec3 c = p.pos().add(0, 1, 0);
        int snow = IceParticles.count(24, c), shards = IceParticles.count(6, c);
        for (int i = 0; i < snow + shards; i++) {
            float th = IceParticles.rand() * Mth.TWO_PI, r = 1.0f + 1.4f * IceParticles.rand();
            Vec3 out = new Vec3(-Mth.sin(th), 0, Mth.cos(th)), along = new Vec3(Mth.cos(th), 0, Mth.sin(th));
            Vec3 at = c.add(out.scale(r)).add(0, (IceParticles.rand() - .5) * .6, 0);
            Vec3 v = along.scale(.25 + .15 * IceParticles.rand()).add(out.scale(.1 + .08 * IceParticles.rand())).add(0, .04 + .05 * IceParticles.rand(), 0);
            if (i < snow) IceParticles.crystalDust(at, v, .02f + .02f * IceParticles.rand(), 0, 16 + (int) (IceParticles.rand() * 14));
            else IceParticles.shard(at, v, .05f + .05f * IceParticles.rand(), 28 + (int) (IceParticles.rand() * 20), i % 2 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        }
        IceParticles.coldMist(p.pos(), 2.6f, .7f);
    }
    /** The sword driven into the ground: the impact, then the planted sword lives on as a Plant. */
    private static void plant(IcemanFxPacket p) {
        PLANTS.removeIf(pl -> pl.entity == p.entity() && !pl.shattered);
        if (PLANTS.size() > 12) PLANTS.remove(0);
        Plant pl = new Plant();
        pl.entity = p.entity(); pl.id = p.id(); pl.at = p.pos();
        Vec3 d = new Vec3(p.dir().x, 0, p.dir().z);
        pl.dir = d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
        pl.start = gameTime(); pl.nextChain = pl.start + 6; pl.seed = (int) (IceParticles.rand() * 100000);
        PLANTS.add(pl);
        Vec3 at = pl.at;
        // The blade going in: frost bursting out of the ground round it, broken ground ice, the cold rolling out.
        IceParticles.cryo(at.add(0, .15, 0), new Vec3(0, 1, 0), 1.1f, 1);
        IceParticles.coldMist(at, 2.2f, 1);
        IceParticles.ring(at.add(0, .05, 0), 1.6f, .5f, 0xeef6ff, .45f, 14, false);
        for (int i = 0, n = IceParticles.count(8, at); i < n; i++) {
            double a = IceParticles.rand() * Mth.TWO_PI;
            Vec3 o = new Vec3(Math.cos(a), 0, Math.sin(a));
            IceParticles.shard(at.add(o.scale(.2)).add(0, .1, 0), o.scale(.08 + .08 * IceParticles.rand()).add(0, .18 + .12 * IceParticles.rand(), 0), .05f + .05f * IceParticles.rand(),
                    30 + (int) (IceParticles.rand() * 20), i % 2 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        }
        shakeAt(at, .25f, 12);
        if (IcemanClient.isMe(p.entity())) IcemanClient.kickFov(.18f);
    }
    /** The planted sword let go (or cut short): it cracks where it stands; tick shatters it SWORD_CRACK_TICKS later. */
    private static void swordBreak(IcemanFxPacket p) {
        Plant pl = plantFor(p.entity());
        if (pl == null) {
            // Not seen planted here: it breaks where it is.
            if (p.id() == 1) IceParticles.breakApart(p.pos().add(0, PLANT_GRIP * .5, 0), new Vec3(0, 1, 0), .7f, .35f, new Vec3(0, .04, 0), 4, 8, IceMesh.CLEAR);
            return;
        }
        if (pl.breakAt < 0) pl.breakAt = gameTime();
    }
    /** A weapon breaking in his hand: remembered; the hand shows it cracking (hold), tick lets it fall apart. */
    private static void weaponBreak(IcemanFxPacket p) {
        Track tr = track(p.entity());
        if (tr.breakAt >= 0) fallApart(p.entity(), tr);
        tr.breakAt = gameTime();
        tr.breakWeapon = Mth.clamp(p.id() & 15, 0, WEAPONS - 1);
        tr.breakHow = p.id() >> 4;
        tr.breakSize = Math.max(1, p.power());
        tr.breakPos = p.pos(); tr.breakDir = p.dir();
        sound(p.pos(), ModSounds.ICEMAN_CRACK.get(), .35f, 1.7f);
    }
    /** The weapon in his hand falls apart (the staged break, along it as last drawn). */
    private static void fallApart(int entity, Track tr) {
        tr.breakAt = -1;
        int w = tr.breakWeapon;
        float size = tr.breakSize;
        Vec3[] pts = IcemanLayer.weapon(entity);
        Vec3 base, tip;
        if (pts != null) { base = pts[0]; tip = pts[1]; }
        else {
            Vec3 d = tr.breakDir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : tr.breakDir.normalize();
            base = tr.breakPos;
            tip = base.add(d.scale(IcemanArmory.length(w, size) / 16f));
        }
        Vec3 axis = tip.subtract(base), push = tr.breakDir.scale(.4).add(0, .05, 0);
        float len = (float) axis.length();
        if (w == W_MACE) {
            if (tr.breakHow != 1) {
                // Heavy chunks off the head, then the handle.
                Vec3 head = base.lerp(tip, IcemanArmory.MACE_HEAD / IcemanArmory.MACE_TIP);
                IceParticles.breakApart(head, axis, len * .35f, .3f * size + .15f, push, 3, 8, IceMesh.GLACIER);
                for (int i = 0, n = IceParticles.count(2, head); i < n; i++)
                    IceParticles.shard(head, IceParticles.jitter(.07).add(0, .1, 0), (.16f + .1f * IceParticles.rand()) * Math.min(2.5f, size), 45 + (int) (IceParticles.rand() * 25), IceMesh.GLACIER);
            }
            Vec3 handle = base.lerp(tip, .3);
            if (tr.breakHow == 1) IceParticles.breakApart(handle, axis, len * .45f, .14f * Math.min(2.5f, size), push, 2, 6, IceMesh.CLEAR);
            else IceParticles.later(4, () -> IceParticles.shatter(handle, push, .14f * Math.min(2.5f, size), IceMesh.CLEAR));
        } else {
            boolean spear = w == W_SPEAR;
            Vec3 mid = base.lerp(tip, spear ? .45 : .55);
            IceParticles.breakApart(mid, axis, len * .85f, spear ? .26f : .3f, push, spear ? 5 : 4, 8, IceMesh.CLEAR);
            // The spear in long shards, the sword in pieces of blade.
            for (int i = 0, n = IceParticles.count(spear ? 5 : 4, mid); i < n; i++) {
                Vec3 at = base.lerp(tip, .2 + .75 * IceParticles.rand());
                IceParticles.shard(at, IceParticles.jitter(.06).add(push.scale(.5)).add(0, .06, 0), (spear ? .13f : .11f) + .06f * IceParticles.rand(), 32 + (int) (IceParticles.rand() * 22),
                        i % 2 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
            }
        }
        IceParticles.coldMist(base, .9f, .5f);
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
        // The weapon forming: the air freezing into the palm.
        for (int i = FORMS.size() - 1; i >= 0; i--) {
            Form f = FORMS.get(i);
            float t = now - f.start;
            Vec3 hand = formHand(f);
            if (t < FORM_TICKS - 1) {
                Vec3[] w = IcemanLayer.weapon(f.entity);
                Vec3 along = w == null ? new Vec3(0, 1, 0) : w[1].subtract(w[0]);
                if ((int) t % 2 == 0) IceParticles.cryo(hand, along, f.weapon == W_MACE ? .4f : .28f, .85f);
                for (int k = 0, n = IceParticles.count(f.weapon == W_MACE ? 3 : 2, hand); k < n; k++) {
                    Vec3 from = hand.add(IceParticles.jitter(1).normalize().scale(.6 + .4 * IceParticles.rand()));
                    IceParticles.crystalDust(from, hand.subtract(from).scale(.16), .016f + .012f * IceParticles.rand(), (IceParticles.rand() - .5f) * .3f, 8);
                    if (IceParticles.rand() < .4f) IceParticles.mist(from, hand.subtract(from).scale(.12), .12f, -.004f, .16f, 9);
                }
            }
            if (!f.closed && t >= FORM_TICKS - 1) {
                f.closed = true;
                IceParticles.frostDust(hand, Vec3.ZERO, 1.5f);
                if (f.weapon == W_MACE) IceParticles.coldMist(hand.add(0, -.8, 0), 1f, .5f);
            }
            if (t > FORM_TICKS + 6) FORMS.remove(i);
        }
        IMPACTS.removeIf(m -> now - m.start > 14);
        SLAMS.removeIf(s -> now - s.start > s.life);
        STREAKS.removeIf(s -> now - s.start > 6);
        tickPlants(level, now);
        // Weapons breaking in a hand: their time is up (or a new one is forming).
        for (var en : TRACKS.entrySet()) {
            Track tr = en.getValue();
            if (tr.breakAt < 0) continue;
            IcemanClient.State s = IcemanClient.get(en.getKey());
            if (now >= tr.breakAt + WEAPON_CRACK_TICKS || s != null && s.action == FORM) fallApart(en.getKey(), tr);
        }
        // Per Iceman: the cold along the swings, the flurry's thrusts, the mace growing, the spear drawn back.
        for (var en : IcemanClient.states().entrySet()) {
            int id = en.getKey();
            IcemanClient.State s = en.getValue();
            Entity ent = level.getEntity(id);
            if (ent == null) continue;
            Track tr = track(id);
            float t = IcemanClient.clock(s, 0);
            Vec3[] w = IcemanLayer.weapon(id);
            boolean swinging = s.action == STRIKE || s.action == RELEASE && s.weapon != W_SWORD;
            if (w != null && swinging) {
                Vec3 mid = w[0].lerp(w[1], s.weapon == W_MACE ? .55 : .5);
                if (tr.lastTip != null) {
                    Vec3 v = w[1].subtract(tr.lastTip);
                    double sp = v.length();
                    if (sp > .12 && sp < 6) {
                        // The air freezing along where it really went: heavy and dense behind the mace, a thin short streak
                        // behind the spear, a smooth breath behind the sword.
                        int n = s.weapon == W_MACE ? 4 : s.weapon == W_SPEAR ? 2 : 3;
                        float k = s.weapon == W_MACE ? 1.1f : s.weapon == W_SPEAR ? .5f : .75f;
                        for (int i = 0; i < n; i++) IceParticles.freezeTrail(tr.lastTip.lerp(w[1], (i + .5) / n), v, k);
                        if (s.weapon != W_SPEAR && tr.lastMid != null)
                            IceParticles.freezeTrail(tr.lastMid.lerp(mid, IceParticles.rand()), mid.subtract(tr.lastMid), s.weapon == W_MACE ? .8f : .45f);
                        if (s.weapon == W_MACE && IceParticles.rand() < .35f) IceParticles.mist(w[1], v.scale(.04), .22f, .02f, .16f, 26);
                    }
                }
                tr.lastTip = w[1]; tr.lastMid = mid;
            } else tr.lastTip = tr.lastMid = null;
            // The flurry: each thrust leaves a little cold at the point (vapour, a crystal or two, a short frost streak).
            if (s.action == STRIKE && s.weapon == W_SPEAR && s.combo == 2) {
                if (tr.thrustKey != s.start) { tr.thrustKey = s.start; tr.thrust = -1; }
                for (int i = tr.thrust + 1; i < SPEAR_FLURRY.length && t >= SPEAR_FLURRY[i]; i++) {
                    tr.thrust = i;
                    if (w != null) thrustCold(w);
                }
            }
            boolean full = false;
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
                    // Frost and cold vapour rolling off the head, more as it grows.
                    if (IceParticles.rand() < .4f + .5f * charge)
                        IceParticles.mist(head.add(IceParticles.jitter(r)), new Vec3(0, -.014, 0), .2f + .12f * size, .016f, .18f + .06f * charge, 28);
                    IceParticles.frostDust(head.add(IceParticles.jitter(r)), Vec3.ZERO, .5f + .35f * size);
                    if (IceParticles.rand() < .5f * charge)
                        IceParticles.crystalDust(head.add(IceParticles.jitter(r * 1.2)), IceParticles.jitter(.02).add(0, .01, 0), .02f + .015f * IceParticles.rand(), (IceParticles.rand() - .5f) * .4f, 20);
                }
                full = charge >= .98f;
                // At full size the ground answers: frost spreading under him, the cold creeping out low.
                if (full && (int) now % 6 == 0) IceParticles.coldMist(ent.position(), 1.8f, .45f);
            } else tr.layer = -1;
            tr.frost = Mth.clamp(tr.frost + (full ? .04f : -.05f), 0, 1);
            if (s.action == CHARGE && s.weapon == W_SPEAR && t >= HOLD_TICKS && w != null) {
                // The point growing: frost, vapour and crystals gathering on it.
                float k = Mth.clamp((t - HOLD_TICKS) / SPEAR_DRAW, 0, 1);
                Vec3 tip = w[1], d = w[1].subtract(w[0]).normalize();
                if (IceParticles.rand() < .25f + .55f * k) IceParticles.crystalDust(tip.add(IceParticles.jitter(.12)), d.scale(-.01).add(0, .005, 0), .016f + .014f * IceParticles.rand(), .3f, 16);
                if (IceParticles.rand() < .12f + .3f * k) IceParticles.mist(tip, new Vec3(0, -.006, 0), .1f + .08f * k, .012f, .14f, 22);
                if (k >= 1 && (int) now % 7 == 0) IceParticles.cryo(tip, d, .25f, .5f);
            }
        }
    }
    /** One thrust of the flurry: a breath of vapour, a crystal or two and a short frost streak at the point. */
    private static void thrustCold(Vec3[] w) {
        Vec3 d = w[1].subtract(w[0]).normalize(), tip = w[1];
        IceParticles.freezeTrail(tip, d.scale(.3), 1);
        for (int i = 0, n = IceParticles.count(3, tip); i < n; i++)
            IceParticles.crystalDust(tip.add(IceParticles.jitter(.06)), d.scale(.08 + .05 * IceParticles.rand()).add(IceParticles.jitter(.02)), .02f + .01f * IceParticles.rand(), 0, 10 + (int) (IceParticles.rand() * 6));
        IceParticles.mist(tip, d.scale(.03), .1f, .014f, .15f, 14);
        if (STREAKS.size() > 24) STREAKS.remove(0);
        Streak s = new Streak();
        s.a = tip.subtract(d.scale(.45)); s.b = tip.add(d.scale(.15)); s.start = gameTime();
        STREAKS.add(s);
    }
    /** The planted swords: the cold drawn in toward them, their crystal lines, cracking, shattering, fading. */
    private static void tickPlants(Level level, float now) {
        double radius = IcemanConfig.SWORD_PULL_RADIUS.get();
        for (Iterator<Plant> it = PLANTS.iterator(); it.hasNext(); ) {
            Plant pl = it.next();
            if (pl.shattered) {
                for (Chain ch : pl.chains) if (ch.meltAt < 0) ch.meltAt = now;
                pl.chains.removeIf(ch -> now > ch.meltAt + 16);
                if (now > pl.end + 40) it.remove();
                continue;
            }
            // Its Iceman no longer holding it (out of sight, the hold cut short without word): it breaks.
            IcemanClient.State s = IcemanClient.get(pl.entity);
            boolean holding = s != null && s.weapon == W_SWORD && (s.action == CHARGE || s.action == RELEASE) && level.getEntity(pl.entity) != null;
            if (!holding && pl.breakAt < 0) pl.breakAt = now;
            if (pl.breakAt >= 0 && now >= pl.breakAt + SWORD_CRACK_TICKS) { shatter(pl, now); continue; }
            Vec3 c = pl.at;
            if (pl.breakAt < 0) {
                // A new line of crystals now and then, growing in from the outside toward the sword.
                if (now >= pl.nextChain && pl.chains.size() < 7) {
                    pl.chains.add(chain(level, pl, radius, now));
                    pl.nextChain = now + 8 + IceParticles.rand() * 8;
                }
                // Vapour flowing in low over the ground, tiny ice crystals drawn in with it (straight in: no whirl).
                for (int k = 0, n = IceParticles.count(2, c); k < n; k++) {
                    float a = IceParticles.rand() * Mth.TWO_PI, r = (float) radius * (.5f + .5f * IceParticles.rand());
                    Vec3 from = new Vec3(c.x - Mth.sin(a) * r, c.y + .12, c.z + Mth.cos(a) * r);
                    Vec3 in = c.subtract(from).multiply(1, 0, 1).normalize();
                    IceParticles.mist(from, in.scale(.05 + .04 * IceParticles.rand()), .3f, .012f, .15f, 30);
                    Vec3 dust = new Vec3(c.x - Mth.sin(a) * r * .7, c.y + .1 + .5 * IceParticles.rand(), c.z + Mth.cos(a) * r * .7);
                    IceParticles.crystalDust(dust, in.scale(.08 + .05 * IceParticles.rand()), .016f + .012f * IceParticles.rand(), 0, 18);
                }
                if ((int) now % 5 == 0) IceParticles.frostDust(c.add(0, .25, 0), Vec3.ZERO, .8f);
            }
            for (Chain ch : pl.chains) if (ch.meltAt < 0 && now > ch.start + ch.n * 1.6f + 24) ch.meltAt = now;
            pl.chains.removeIf(ch -> ch.meltAt >= 0 && now > ch.meltAt + 16);
        }
    }
    /** A new line of crystals: from a point out round the sword in toward it, each a little crystal leaning toward it. */
    private static Chain chain(Level level, Plant pl, double radius, float now) {
        Chain ch = new Chain();
        float a = IceParticles.rand() * Mth.TWO_PI, r = (float) radius * (.6f + .35f * IceParticles.rand());
        Vec3 c = pl.at, from = new Vec3(c.x - Mth.sin(a) * r, c.y, c.z + Mth.cos(a) * r);
        Vec3 in = c.subtract(from).multiply(1, 0, 1);
        double dist = in.length();
        in = in.normalize();
        ch.n = Math.max(3, Math.min(9, (int) ((dist - .6) / .5)));
        ch.pts = new float[ch.n * 3];
        for (int i = 0; i < ch.n; i++) {
            double u = i / (double) ch.n * (dist - .6);
            double x = from.x + in.x * u + (IceParticles.rand() - .5) * .18, z = from.z + in.z * u + (IceParticles.rand() - .5) * .18;
            double y = IcemanSpearFx.groundY(level, x, c.y + .6, z);
            if (Math.abs(y - c.y) > 1.5) y = c.y;
            ch.pts[i * 3] = (float) x; ch.pts[i * 3 + 1] = (float) y - .03f; ch.pts[i * 3 + 2] = (float) z;
        }
        ch.lean = in.scale(.75).add(0, 1, 0).normalize();
        ch.from = from;
        ch.start = now; ch.seed = (int) (IceParticles.rand() * 100000); ch.len = .14f + .14f * IceParticles.rand();
        return ch;
    }
    /** The planted sword shatters: blade-like pieces zone by zone up what stood out of the ground, its foot bursting, the mist lingering. */
    private static void shatter(Plant pl, float now) {
        pl.shattered = true;
        pl.end = now;
        Vec3 up = new Vec3(0, 1, 0), c = pl.at;
        Vec3 mid = c.add(0, PLANT_GRIP * .5, 0);
        IceParticles.breakApart(mid, up, PLANT_GRIP + .15f, .42f, pl.dir.scale(-.02).add(0, .05, 0), 4, 9, IceMesh.CLEAR);
        for (int i = 0, n = IceParticles.count(4, mid); i < n; i++)
            IceParticles.shard(c.add(0, .15 + PLANT_GRIP * IceParticles.rand(), 0), IceParticles.jitter(.07).add(0, .08, 0), .11f + .06f * IceParticles.rand(), 34 + (int) (IceParticles.rand() * 20),
                    i % 2 == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
        IceParticles.shatter(c.add(0, .1, 0), new Vec3(0, .04, 0), .35f, IceMesh.MILKY);
        IceParticles.coldMist(c, 2f, 1);
        for (int i = 0, n = IceParticles.count(5, c); i < n; i++)
            IceParticles.mist(c.add(IceParticles.jitter(.5)).add(0, .15, 0), IceParticles.jitter(.008).multiply(1, 0, 1), .45f, .008f, .16f, 60 + (int) (IceParticles.rand() * 30));
        if (IcemanClient.isMe(pl.entity)) IcemanClient.shake(.12f);
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean any = !FORMS.isEmpty() || !IMPACTS.isEmpty() || !SLAMS.isEmpty() || !STREAKS.isEmpty() || !PLANTS.isEmpty() || !IcemanSpearFx.idle();
        if (!any) for (IcemanClient.State s : IcemanClient.states().values()) if (s.action == STRIKE || s.action == CHARGE || s.action == RELEASE) { any = true; break; }
        if (!any) for (Track tr : TRACKS.values()) if (tr.frost > 0) { any = true; break; }
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
            for (Plant pl : PLANTS) plantIce(st, c, pl, now);
            st.endIce();
            if (ownArmsInWorld()) ownArms(st);
            FilmContext f = st.fx();
            for (var en : TRACKS.entrySet()) trail(f, en.getKey(), en.getValue(), now);
            for (Form fm : FORMS) formFx(f, fm, now);
            for (Slam s : SLAMS) slamFx(f, s, now);
            for (Streak s : STREAKS) {
                float k = 1 - (now - s.start) / 6;
                if (k > 0) FilmFx.streak(f, s.a, s.b, .05, 0xeef6ff, 0, .4f * k, false);
            }
            for (Plant pl : PLANTS) plantFx(f, pl, now);
            for (var en : TRACKS.entrySet()) {
                Track tr = en.getValue();
                if (tr.frost <= 0) continue;
                Entity ent = mc.level.getEntity(en.getKey());
                if (ent != null) FilmFx.ring(f, ent.getPosition(st.partial).add(0, .03, 0), .9 * tr.frost, .9 * tr.frost, 0xeef6ff, .3f * tr.frost, false);
            }
            IcemanSpearFx.drawFx(st, f);
        } finally {
            st.close();
        }
    }

    /** Records where each swinging Iceman's weapon is this frame (for the misty ribbon). */
    private static void sample(float now) {
        for (var en : IcemanClient.states().entrySet()) {
            IcemanClient.State s = en.getValue();
            Track tr = TRACKS.get(en.getKey());
            if (tr == null) continue;
            boolean swinging = s.weapon != W_SPEAR && (s.action == STRIKE || s.action == RELEASE && s.weapon == W_MACE);
            Vec3[] w = swinging ? IcemanLayer.weapon(en.getKey()) : null;
            if (w == null) continue;
            if (tr.count > 0 && now - tr.last < .1f) continue;
            if (tr.count > 0 && now - tr.last > 6) tr.count = 0;
            Vec3 a = w[0].lerp(w[1], s.weapon == W_MACE ? .5f : .3f);
            int i = tr.head;
            tr.pts[i * 6] = (float) a.x; tr.pts[i * 6 + 1] = (float) a.y; tr.pts[i * 6 + 2] = (float) a.z;
            tr.pts[i * 6 + 3] = (float) w[1].x; tr.pts[i * 6 + 4] = (float) w[1].y; tr.pts[i * 6 + 5] = (float) w[1].z;
            tr.when[i] = now;
            tr.head = (i + 1) % TRAIL;
            tr.count = Math.min(TRAIL, tr.count + 1);
            tr.last = now;
        }
    }
    /** A faint pale ribbon of vapour between the samples (the mace's denser, the sword's a smooth misty arc), fading. */
    private static void trail(FilmContext f, int id, Track tr, float now) {
        if (tr.count < 2) return;
        IcemanClient.State s = IcemanClient.get(id);
        int weapon = s == null ? W_SWORD : s.weapon;
        if (weapon == W_SPEAR) return;
        float life = weapon == W_MACE ? 6 : 5;
        var mc = Minecraft.getInstance();
        boolean own = IcemanClient.isMe(id) && mc.options.getCameraType().isFirstPerson();
        float strength = (own ? .5f : 1) * (weapon == W_MACE ? .2f : .14f);
        Matrix4f m = f.pose().last().pose();
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
            quad(soft, m, p, i, j, .9f, .95f, 1f, ai * ai * strength, aj * aj * strength);
        }
    }
    /** One piece of ribbon: inner edge clear, the tip edge densest. */
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
    /** The humidity drawn into his hand: pale streams of vapour spiralling in, a breath of frost gathering. */
    private static void formFx(FilmContext f, Form fm, float now) {
        float t = now - fm.start;
        Vec3 hand = formHand(fm);
        float e = Mth.clamp(t / (FORM_TICKS - 2), 0, 1), fade = Mth.clamp(t / 1.2f, 0, 1) * (1 - Mth.clamp((t - FORM_TICKS + 2) / 4, 0, 1));
        for (int j = 0; j < 6; j++) {
            Vec3 d = new Vec3(IceMesh.hash(j * 3.1 + fm.entity) - .5, IceMesh.hash(j * 5.7 + fm.entity) - .3, IceMesh.hash(j * 7.3 + fm.entity) - .5).normalize();
            Vec3 head = spiral(hand, d, e), tail = spiral(hand, d, Math.max(0, e - .16f));
            float a = .3f * fade * Mth.sqrt(1 - e);
            FilmFx.streak(f, tail, head, .07, 0xeef6ff, 0, a, false);
        }
        float bloom = Mth.sin(Mth.PI * e);
        FilmFx.puff(f, hand, .22 + .18 * e, 0xeef6ff, .14f * bloom * fade);
        FilmFx.glow(f, hand, .18 + .1 * e, IceParticles.COLD_LIGHT, .12f * bloom * fade);
    }
    private static Vec3 spiral(Vec3 hand, Vec3 d, float e) {
        float r = 1.0f * (float) Math.pow(1 - e, 1.3), a = e * 2.4f;
        double c = Math.cos(a), s = Math.sin(a);
        return hand.add((d.x * c - d.z * s) * r, d.y * r, (d.x * s + d.z * c) * r);
    }
    /** A hit's frost: small crystals growing out of where it struck (frozen onto them), then melting away. */
    private static void impact(IceMesh.Ctx c, Impact m, float now) {
        float t = now - m.start;
        if (t < 0 || t > 14) return;
        float melt = Mth.clamp((t - 6) / 8, 0, 1);
        c.light = IceStage.light(m.at);
        for (int i = 0; i < 5; i++) {
            int seed = m.seed + i * 13;
            Vec3 d = new Vec3(IceGrowth.h(seed, 1) - .5, IceGrowth.h(seed, 2) - .3, IceGrowth.h(seed, 3) - .5).normalize().add(m.dir.scale(.8)).normalize();
            float len = (.08f + .1f * IceGrowth.h(seed, 4)) * m.size * (1 - .4f * melt), g = IceGrowth.grow(t, i * .35f, 2.4f);
            if (g <= 0) continue;
            Vec3 at = m.at.add(IceGrowth.h(seed, 5) * .06 - .03, IceGrowth.h(seed, 6) * .06 - .03, IceGrowth.h(seed, 7) * .06 - .03);
            IceGrowth.crystal(c, at.x, at.y, at.z, d.x, d.y, d.z, len, len * .26f, seed, i % 2 == 0 ? IceMesh.CLEAR : IceMesh.MILKY, g, 1 - melt);
        }
    }
    /** A slam's cracks on the ground: thin cold light running out along them as they open, then only the fading lines. */
    private static void slamGlints(IceMesh.Ctx c, Slam s, float now) {
        float t = now - s.start;
        float fade = 1 - Mth.clamp((t - s.life * .55f) / (s.life * .45f), 0, 1);
        if (fade <= 0) return;
        float w = .03f + .02f * Mth.sqrt(s.size);
        for (float[] pts : s.cracks) {
            for (int k = 0; k + 1 < pts.length / 4; k++) {
                float open = Mth.clamp((t - pts[k * 4 + 3]) / .8f, 0, 1);
                if (open <= 0) break;
                Vec3 a = new Vec3(pts[k * 4], pts[k * 4 + 1], pts[k * 4 + 2]), b = new Vec3(pts[k * 4 + 4], pts[k * 4 + 5], pts[k * 4 + 6]);
                b = a.lerp(b, open);
                float hot = .55f + .45f * Math.max(0, 1 - (t - pts[k * 4 + 3]) / 5);
                float br = fade * hot * .6f * (1 - .25f * k / (pts.length / 4f));
                IceMesh.line(c, a, b, w * (1 - .4f * k / (pts.length / 4f)), .4f * br, .7f * br, br);
            }
        }
    }
    /** A slam's frost on the ground (pale, so it reads in daylight), along the cracks too. */
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

    // ------------------------------------------------------------------ the planted sword
    private static final Matrix3f ROT = new Matrix3f();
    private static final Quaternionf ROTQ = new Quaternionf();
    /** The sword standing in the ground (its grip PLANT_GRIP up, the rest of the blade under the surface), the crystals at its foot, its lines of crystals. */
    private static void plantIce(IceStage st, IceMesh.Ctx c, Plant pl, float now) {
        float t = now - pl.start;
        if (!pl.shattered) {
            float crack = pl.breakAt < 0 ? 0 : .15f + .85f * Mth.clamp((now - pl.breakAt) / SWORD_CRACK_TICKS, 0, 1);
            PoseStack pose = st.pose;
            pose.pushPose();
            Vec3 g = pl.at.add(0, PLANT_GRIP, 0);
            pose.translate(g.x, g.y, g.z);
            // The hand's frame turned point down: its -z down, its y (the blade's width) to his right, its x toward him.
            Vec3 f = pl.dir;
            ROT.set((float) -f.x, 0, (float) -f.z, (float) -f.z, 0, (float) f.x, 0, 1, 0);
            pose.mulPose(ROTQ.setFromNormalized(ROT));
            pose.scale(.9375f / 16, .9375f / 16, .9375f / 16);
            c.light = IceStage.light(g);
            c.at(pose);
            IcemanArmory.TURN = 0;
            IcemanArmory.inHand(c, pose, W_SWORD, 1, 1, crack, st.time);
            pose.popPose();
            c.at(pose);
            // The crystals growing at its foot.
            c.light = IceStage.light(pl.at.add(0, .3, 0));
            IceGrowth.cluster(c, pl.at.x, pl.at.y - .03, pl.at.z, 0, 1, 0, .36f, pl.seed, 6, IceMesh.CLEAR, Mth.clamp(t / 12, 0, 1.3f), 1);
        }
        boolean far = st.far(pl.at);
        for (Chain ch : pl.chains) {
            float melt = ch.meltAt < 0 ? 0 : Mth.clamp((now - ch.meltAt) / 16, 0, 1);
            for (int i = 0; i < ch.n; i++) {
                if (far && (i & 1) == 1) continue;
                float g = IceGrowth.grow(now - ch.start, i * 1.6f, 5);
                if (g <= 0) break;
                int seed = ch.seed + i * 7;
                float len = ch.len * (.6f + .8f * IceGrowth.h(seed, 1)) * (1 - .5f * melt);
                double lx = ch.lean.x + (IceGrowth.h(seed, 2) - .5) * .5, lz = ch.lean.z + (IceGrowth.h(seed, 3) - .5) * .5;
                IceGrowth.crystal(c, ch.pts[i * 3], ch.pts[i * 3 + 1], ch.pts[i * 3 + 2], lx, ch.lean.y, lz, len, len * .26f, seed,
                        IceGrowth.h(seed, 4) < .3f ? IceMesh.MILKY : IceMesh.CLEAR, g, 1 - melt);
            }
        }
    }
    /** The frost on the ground round it, and the frost creeping in along each line of crystals. */
    private static void plantFx(FilmContext f, Plant pl, float now) {
        float t = now - pl.start;
        float fade = pl.shattered ? 1 - Mth.clamp((now - pl.end) / 40, 0, 1) : 1;
        if (fade <= 0) return;
        double radius = IcemanConfig.SWORD_PULL_RADIUS.get();
        float grow = Mth.clamp(t / 40, 0, 1);
        FilmFx.ring(f, pl.at.add(0, .025, 0), .4 + radius * .25 * grow, .4 + radius * .25 * grow, 0xeef6ff, .32f * fade, false);
        for (Chain ch : pl.chains) {
            float melt = ch.meltAt < 0 ? 0 : Mth.clamp((now - ch.meltAt) / 16, 0, 1);
            int front = Math.min(ch.n - 1, (int) ((now - ch.start) / 1.6f));
            if (front < 0) continue;
            Vec3 a = ch.from.add(0, .04, 0), b = new Vec3(ch.pts[front * 3], ch.pts[front * 3 + 1] + .07, ch.pts[front * 3 + 2]);
            FilmFx.streak(f, a, b, .14, 0xf2f9ff, .05f * fade * (1 - melt), .3f * fade * (1 - melt), false);
        }
    }
    /** His own arms (first person) are drawn here, in the world, onto the planted hilt: true while that is so. */
    public static boolean ownArmsInWorld() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || !IcemanClient.isHero(mc.player)) return false;
        IcemanClient.State s = IcemanClient.get(mc.player);
        if (s == null || s.weapon != W_SWORD || plantFor(mc.player.getId()) == null) return false;
        float t = IcemanClient.clock(s, mc.getFrameTime());
        return s.action == CHARGE && t >= HOLD_TICKS + PLANT_AT || s.action == RELEASE && t < SWORD_CRACK_TICKS;
    }
    private static final boolean[] BOTH = {true, true};
    private static final Vector3f FPG = new Vector3f();
    /** His arms in his own view while the sword stands planted: a chest frame of his own, facing the sword, both hands solved onto its hilt. */
    private static void ownArms(IceStage st) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        IcemanClient.State s = IcemanClient.get(player);
        Plant pl = plantFor(player.getId());
        if (s == null || pl == null) return;
        float partial = st.partial, t = IcemanClient.clock(s, partial), time = player.tickCount + partial;
        Pose base = IcemanMotion.stance(time);
        IcemanMotion.Ctx ctx = new IcemanMotion.Ctx(time, s.weapon, s.combo, s.charge, true, false, player.getViewXRot(partial) * Mth.DEG_TO_RAD, 0, 0, player.getId());
        Pose pose = IcemanMotion.sample(s.action, t, base, ctx);
        float yaw = (float) Math.toDegrees(Math.atan2(-pl.dir.x, pl.dir.z));
        // The chest where it is as he kneels (the camera does not kneel with him): level with the hilt, half a block back.
        Vec3 origin = pl.at.add(0, PLANT_GRIP, 0).subtract(pl.dir.scale(8.4 / 16));
        IcemanWeaponReach.toFrame(origin, yaw, 16, pl.at.add(0, PLANT_GRIP, 0), FPG);
        IcemanWeaponMotion.hiltLocal(pose, FPG);
        PoseStack ps = st.pose;
        ps.pushPose();
        ps.translate(origin.x, origin.y, origin.z);
        ps.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        ps.scale(-1 / 16f, -1 / 16f, 1 / 16f);
        IcemanBody.WEAPON = -1;
        IcemanBody.SHELL_COVER = 0;
        IcemanBody.HAND_GLOW[0] = IcemanBody.HAND_GLOW[1] = 0;
        IcemanBody.capture = true;
        var buffers = mc.renderBuffers().bufferSource();
        try {
            IcemanBody.drawArms(ps, buffers, IceStage.light(pl.at.add(0, .8, 0)), pose, time, BOTH);
            IcemanLayer.store(player.getId(), IcemanBody.handRight, IcemanBody.handLeft, null, null, null, null);
        } finally {
            IcemanBody.capture = false;
            ps.popPose();
            IceMesh.endBatches(buffers);
        }
    }

    // ------------------------------------------------------------------ cleanup
    static void clear() {
        TRACKS.clear(); FORMS.clear(); IMPACTS.clear(); SLAMS.clear(); STREAKS.clear(); PLANTS.clear();
        IcemanSpearFx.clear();
        IcemanWeaponMotion.clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); lastLevel = null; }
}
