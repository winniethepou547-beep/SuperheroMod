package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The two slides as everyone sees them (his own body is steered by IcemanSlideSteer).
 * <p>
 * The ice slide (SHIFT): the track of ice forming and growing under him (IcemanTrack); getting ready, frost breathing off
 * the ground under his feet; then snow spray and small chunks of ice thrown back off his feet, a light cold mist trailing low
 * behind (longer with speed), all thicker going down the track (behind and low, so he stays in plain view); a sliding
 * hiss (ICEMAN_SLIDE) following him that rises with his speed; in his own ears a wind rush rising on the way down and
 * falling off the ice; in his own view, thin wind streaks rushing past as the speed builds. Let go: on the ground, spray
 * thrown out from the braking foot (the track's end cracks); in the air, the track's last point freezes into ice cubes.
 * <p>
 * The sub-zero slide (CTRL): a whoosh of mist bursting off his feet as it starts (FX_DASH), the ground freezing under
 * him as he goes (thin slabs of ice with frost round them and tiny ice cubes on them, growing in, then melting),
 * spray thrown up to the sides; FX_DASH_HIT: a small cold flash, a frost burst and shards where it struck; FX_SLIDE_HIT:
 * frost bursting off a body the ice slide touched (the frost on the body and over their screen is FrostFx's).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanSlideFx {
    private IcemanSlideFx() {}

    // ------------------------------------------------------------------ from the server
    public static void receive(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        switch (p.kind()) {
            case FX_DASH -> {
                Entity e = mc.level.getEntity(p.entity());
                Vec3 at = e != null ? e.position() : p.pos();
                float yaw = p.power();
                if (IcemanClient.isMe(p.entity())) yaw = IcemanClient.dashYaw();
                Vec3 dir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
                Vec3 side = new Vec3(dir.z, 0, -dir.x);
                IceParticles.ring(at.add(0, .06, 0), 1.7f, .3f, IceParticles.MIST_RGB, .45f, 10, false);
                int n = IceParticles.count(7, at);
                for (int i = 0; i < n; i++) {
                    float sg = i % 2 == 0 ? 1 : -1;
                    Vec3 v = dir.scale(-.05 - .05 * IceParticles.rand()).add(side.scale(sg * (.04 + .06 * IceParticles.rand()))).add(0, .015, 0);
                    IceParticles.mist(at.add(IceParticles.jitter(.2)).add(0, .25, 0), v, .35f, .03f, .3f, 22 + (int) (IceParticles.rand() * 10));
                }
                int s = IceParticles.count(14, at);
                for (int i = 0; i < s; i++)
                    IceParticles.snow(at.add(IceParticles.jitter(.2)).add(0, .1, 0), dir.scale(-.1 * IceParticles.rand()).add(IceParticles.jitter(.08)).add(0, .1, 0), .025f, 16);
                if (IcemanClient.isMe(p.entity())) IcemanClient.kickFov(.55f);
            }
            case FX_DASH_HIT -> {
                Vec3 at = p.pos(), dir = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
                IceParticles.flash(at, .9f, .9f, 4);
                IceParticles.shatter(at, dir.scale(.12), .45f, IceMesh.FRESH);
                burst(at, dir, 16, .14f);
                IceParticles.ring(at.add(0, -.6, 0), 1.3f, .2f, IceParticles.COLD_LIGHT, .5f, 8, true);
                if (IcemanClient.isMe(p.id())) IcemanClient.shake(.25f);
                if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.4f);
            }
            case FX_SLIDE_HIT -> {
                Entity t = mc.level.getEntity(p.entity());
                Vec3 at = t != null ? t.getBoundingBox().getCenter() : p.pos();
                Vec3 dir = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
                IceParticles.flash(at, .6f, .6f, 4);
                burst(at, dir, 14, .1f);
                int k = IceParticles.count(4, at);
                for (int i = 0; i < k; i++)
                    IceParticles.shard(at.add(IceParticles.jitter(.25)), dir.scale(.08).add(IceParticles.jitter(.07)).add(0, .1, 0), .05f + .05f * IceParticles.rand(), 30, IceMesh.FRESH);
                if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.2f);
            }
            default -> {}
        }
    }
    /** Frost bursting off a point: flecks flung out (mostly along dir), a breath of mist. */
    private static void burst(Vec3 at, Vec3 dir, int flecks, float speed) {
        int n = IceParticles.count(flecks, at);
        for (int i = 0; i < n; i++) {
            Vec3 out = IceParticles.jitter(1).normalize().add(dir.scale(.6)).normalize();
            IceParticles.snow(at.add(IceParticles.jitter(.15)), out.scale(speed * (.5 + IceParticles.rand())).add(0, .05, 0), .025f + .02f * IceParticles.rand(), 16 + (int) (IceParticles.rand() * 10));
        }
        int m = IceParticles.count(4, at);
        for (int i = 0; i < m; i++) IceParticles.mist(at.add(IceParticles.jitter(.2)), dir.scale(.02).add(IceParticles.jitter(.01)), .3f, .03f, .3f, 24);
    }

    // ------------------------------------------------------------------ the frozen ground behind the sub-zero slide
    private static final class Patch {
        final Vec3 at; final float yaw, born, rx, rz; final int seed, light;
        Patch(Vec3 at, float yaw, float born, float rx, float rz, int seed, int light) { this.at = at; this.yaw = yaw; this.born = born; this.rx = rx; this.rz = rz; this.seed = seed; this.light = light; }
    }
    private static final List<Patch> PATCHES = new ArrayList<>();
    private static final int PATCH_LIFE = 50, PATCH_MELT = 30, MAX_PATCHES = 160;

    /** One Iceman sliding: how fast (smoothed), where his feet were, his sound. */
    private static final class Rider { float speed; Vec3 last, lastPatch; Loop loop; long seen; boolean sliding; }
    private static final Map<Integer, Rider> RIDERS = new HashMap<>();
    private static ClientLevel lastLevel;

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { clear(); lastLevel = mc.level; }
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        for (var en : IcemanClient.states().entrySet()) {
            int id = en.getKey();
            IcemanClient.State s = en.getValue();
            Entity who = mc.level.getEntity(id);
            if (who == null || !IcemanClient.isHero(who)) continue;
            boolean me = IcemanClient.isMe(id);
            boolean slide = me ? IcemanSlideSteer.riding() : s.action == SLIDE;
            boolean dash = me ? IcemanSlideSteer.dashing() : s.action == DASH;
            // How long he has been getting ready / sliding; the braking exit and its age.
            float age = me ? IcemanSlideSteer.rideAge() : s.action == SLIDE ? IcemanClient.clock(s, 0) : -1;
            float brake = me ? (IcemanSlideSteer.exitKind() == 1 ? IcemanSlideSteer.exitAge() : -1) : s.action == SLIDE_END ? IcemanClient.clock(s, 0) : -1;
            ride(mc.level, who, slide, dash, age, brake, now);
        }
        // His own slide predicted before any state of his has arrived.
        if (mc.player != null && IcemanClient.get(mc.player) == null && IcemanSlideSteer.riding())
            ride(mc.level, mc.player, true, false, IcemanSlideSteer.rideAge(), -1, now);
        for (Iterator<Map.Entry<Integer, Rider>> it = RIDERS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (now - en.getValue().seen > 40) { IcemanTrack.stop(en.getKey(), 1); it.remove(); }
        }
        wind(mc);
        IcemanTrack.tick();
        float t = now;
        PATCHES.removeIf(p -> t - p.born > PATCH_LIFE + PATCH_MELT);
    }
    private static void ride(Level level, Entity who, boolean slide, boolean dash, float age, float brake, long now) {
        int id = who.getId();
        Rider r = RIDERS.get(id);
        if (r == null) {
            if (!slide && !dash) return;
            r = new Rider();
            RIDERS.put(id, r);
        }
        Vec3 pos = who.position();
        Vec3 vel = r.last == null ? Vec3.ZERO : pos.subtract(r.last);
        r.last = pos;
        double flat = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        Vec3 dir = flat < 1e-3 ? new Vec3(-Mth.sin(who.getYRot() * Mth.DEG_TO_RAD), 0, Mth.cos(who.getYRot() * Mth.DEG_TO_RAD)) : new Vec3(vel.x / flat, 0, vel.z / flat);
        Vec3 side = new Vec3(dir.z, 0, -dir.x);
        // The slide over: on the ground its end cracks; in the air its last point freezes into ice cubes.
        if (r.sliding && !slide) {
            boolean ground = who.onGround() || !level.noCollision(who, who.getBoundingBox().move(0, -.3, 0));
            IcemanTrack.stop(id, ground || dash ? 1 : 2);
        }
        r.sliding = slide;
        if (brake >= 0 && brake < 10) {
            // Braking: a foot dragged across the ice, spray thrown out ahead and to the side.
            float k = 1 - brake / 10f, sp = Math.max(r.speed, .2f);
            Vec3 foot = pos.add(side.scale(-.25)).add(dir.scale(.2)).add(0, .06, 0);
            int n = IceParticles.count(Math.round(5 * k * Math.min(1.5f, sp / .5f)), foot);
            for (int i = 0; i < n; i++)
                IceParticles.snow(foot.add(IceParticles.jitter(.1)), dir.scale(sp * (.25 + .3 * IceParticles.rand())).add(side.scale(-.05 - .1 * IceParticles.rand())).add(0, .08 + .1 * IceParticles.rand(), 0),
                        .025f + .025f * IceParticles.rand(), 14 + (int) (IceParticles.rand() * 8));
            if (brake < 6 && IceParticles.count(1, foot) > 0) IceParticles.mist(foot, dir.scale(.04), .3f, .03f, .25f * k, 22);
            if (brake < 1.5f && sp > .45f && IceParticles.count(1, foot) > 0) IceParticles.shard(foot, dir.scale(.12).add(0, .12, 0), .05f, 24, IceMesh.FRESH);
        }
        if (!slide && !dash) { r.speed *= .7f; if (r.loop != null) r.loop.end(); return; }
        r.seen = now;
        r.speed += ((float) flat - r.speed) * .4f;
        float k = Mth.clamp(r.speed / SLIDE_SPEED, 0, 1.45f);
        if (slide) {
            IcemanTrack.grow(who);
            if (r.loop == null || r.loop.isStopped() || r.loop.ended) { r.loop = new Loop(id, who); Minecraft.getInstance().getSoundManager().play(r.loop); }
            Vec3 feet = pos.subtract(dir.scale(.45)).add(0, .08, 0);
            if (age >= 0 && age < SLIDE_PREP) {
                // Getting ready: frost breathing off the ground under his feet.
                if (IceParticles.count(1, pos) > 0) IceParticles.frostDust(pos.add(0, .05, 0), Vec3.ZERO, .8f);
                if (age > 3 && IceParticles.rand() < .5f) IceParticles.mist(pos.add(dir.scale(.8)).add(0, .05, 0), dir.scale(.03), .25f, .02f, .16f, 20);
                return;
            }
            // Down the track (or faster than his top speed) the spray thickens.
            float down = (float) Mth.clamp(-vel.y / Math.max(.1, flat), 0, 1);
            float more = k + .6f * down;
            // Snow spray and shards thrown back off his feet, mist trailing low behind (longer with speed).
            int n = IceParticles.count(Math.round(1 + 3 * more), feet);
            for (int i = 0; i < n; i++) {
                float sg = IceParticles.rand() < .5f ? 1 : -1;
                Vec3 v = dir.scale(-.04 - .1 * IceParticles.rand() * k).add(side.scale(sg * (.03 + .07 * IceParticles.rand()))).add(0, .05 + .07 * IceParticles.rand(), 0);
                IceParticles.snow(feet.add(IceParticles.jitter(.12)), v.add(vel.scale(.3)), .02f + .025f * IceParticles.rand(), 12 + (int) (IceParticles.rand() * 10));
            }
            if (now % 3 == 0 && more > .3f && IceParticles.count(1, feet) > 0)
                IceParticles.shard(feet, dir.scale(-.08).add(IceParticles.jitter(.05)).add(0, .1, 0), .04f + .04f * IceParticles.rand(), 22, IceMesh.CLEAR);
            if (now % 2 == 0 && IceParticles.count(1, feet) > 0)
                IceParticles.mist(pos.subtract(dir.scale(.9)).add(0, .1, 0), dir.scale(-.02).add(vel.scale(.2)).add(0, .004, 0), .3f + .2f * k, .02f, .2f, 22 + Math.round(16 * k));
        } else if (r.loop != null) r.loop.end();
        if (dash) {
            // The ground freezing under him, spray to the sides.
            Vec3 from = r.lastPatch == null ? pos : r.lastPatch;
            double d = from.distanceTo(pos);
            int steps = r.lastPatch == null ? 1 : Math.min(3, (int) Math.floor(d / .7));
            for (int i = 1; i <= steps; i++) {
                Vec3 at = r.lastPatch == null ? pos : from.lerp(pos, i / (double) steps);
                patch(level, at, (float) Math.atan2(-dir.x, dir.z), now - (steps - i) * .4f);
            }
            if (steps > 0) r.lastPatch = pos;
            int n = IceParticles.count(4, pos);
            for (int i = 0; i < n; i++) {
                float sg = i % 2 == 0 ? 1 : -1;
                IceParticles.snow(pos.add(side.scale(sg * .35)).add(0, .1, 0), side.scale(sg * (.08 + .08 * IceParticles.rand())).add(dir.scale(.05)).add(0, .09 + .06 * IceParticles.rand(), 0),
                        .025f + .02f * IceParticles.rand(), 14);
            }
            if (IceParticles.count(1, pos) > 0) IceParticles.mist(pos.subtract(dir.scale(.6)).add(0, .15, 0), dir.scale(-.03).add(0, .003, 0), .32f, .025f, .22f, 24);
        } else r.lastPatch = null;
    }
    // ------------------------------------------------------------------ the wind rush (his own ears)
    private static Wind windLoop;
    /** The wind rising as he goes down the track, past his top speed, or falls off it. */
    private static void wind(Minecraft mc) {
        var p = mc.player;
        float want = 0;
        if (p != null && IcemanClient.isHero(p)) {
            if (IcemanSlideSteer.riding()) want = .8f * Math.max(0, -IcemanSlideSteer.pitch()) / SLIDE_DESCENT_MAX + 1.4f * Math.max(0, IcemanSlideSteer.speed() - .85f);
            else if (IcemanSlideSteer.exitKind() == 2 && !p.onGround()) want = Mth.clamp((float) -p.getDeltaMovement().y / 1.2f, 0, .8f);
        }
        windWant = Math.min(1, want);
        if (windWant > .05f && p != null && (windLoop == null || windLoop.isStopped())) { windLoop = new Wind(p); mc.getSoundManager().play(windLoop); }
    }
    private static float windWant;
    private static final class Wind extends AbstractTickableSoundInstance {
        private float vol;
        private final Entity who;
        Wind(Entity who) {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, RandomSource.create());
            this.who = who;
            looping = true; delay = 0; volume = .01f; pitch = 1;
            x = who.getX(); y = who.getY(); z = who.getZ();
        }
        @Override public void tick() {
            if (who.isRemoved() || windLoop != this) { stop(); return; }
            vol += (windWant - vol) * (windWant > vol ? .12f : .2f);
            if (windWant <= .05f && vol < .02f) { stop(); return; }
            volume = Math.max(.01f, .35f * vol);
            pitch = .85f + .3f * vol;
            x = who.getX(); y = who.getY() + 1; z = who.getZ();
        }
    }

    private static void patch(Level level, Vec3 at, float yaw, float born) {
        float top = Float.NaN;
        BlockPos base = BlockPos.containing(at.x, at.y + .3, at.z);
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos p = base.below(dy);
            var state = level.getBlockState(p);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(level, p);
            if (shape.isEmpty()) continue;
            float y = (float) (p.getY() + shape.max(Direction.Axis.Y));
            if (y <= at.y + .4) { top = y; break; }
        }
        if (Float.isNaN(top)) return;
        if (PATCHES.size() >= MAX_PATCHES) PATCHES.remove(0);
        Vec3 c = new Vec3(at.x, top, at.z);
        PATCHES.add(new Patch(c, yaw, born, .5f + .15f * IceParticles.rand(), .85f + .3f * IceParticles.rand(), (int) (IceParticles.rand() * 1e6), IceStage.light(c.add(0, .5, 0))));
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        boolean wind = windOn();
        if (!IcemanTrack.any() && PATCHES.isEmpty() && !wind) return;
        IceStage st = IceStage.open(e);
        if (st == null) return;
        try {
            IceMesh.Ctx c = st.ice();
            IcemanTrack.draw(st, c);
            for (Patch p : PATCHES) patchIce(st, c, p);
            st.endIce();
            if (wind) wind(st, st.fx());
        } finally {
            st.close();
        }
    }
    /** A thin slab of ice frozen onto the ground along the slide's way, frost round its edge, a couple of tiny ice cubes on it; growing in, melting. */
    private static void patchIce(IceStage st, IceMesh.Ctx c, Patch p) {
        if (p.at.distanceToSqr(st.cam) > 80 * 80) return;
        float age = st.time - p.born;
        if (age < 0) return;
        float g = PantherMotion.snap(age, 0, 3);
        float m = (age - PATCH_LIFE) / PATCH_MELT;
        float shrink = 1 - PantherMotion.ease(m), alpha = 1 - PantherMotion.ease((m - .3f) / .7f);
        float s = g * (.35f + .65f * shrink);
        if (s <= .01f || alpha <= .01f) return;
        c.light = p.light;
        float cy = Mth.cos(p.yaw), sy = Mth.sin(p.yaw);
        // Its sides along the way (u across, w along).
        float[] r = IceParticles.AX;
        r[0] = cy; r[1] = 0; r[2] = sy; r[3] = 0; r[4] = 1; r[5] = 0; r[6] = -sy; r[7] = 0; r[8] = cy;
        float hu = p.rx * s * .8f, hw = p.rz * s * .8f;
        double x = p.at.x, y = p.at.y, z = p.at.z;
        // Frost round it (a flat square a little bigger, just over the ground), the slab of clear ice on it.
        float fu = hu * 1.18f, fw = hw * 1.12f;
        IceMesh.quad(c, (float) (x - cy * fu + sy * fw), (float) y + .012f, (float) (z - sy * fu - cy * fw), (float) (x + cy * fu + sy * fw), (float) y + .012f, (float) (z + sy * fu - cy * fw),
                (float) (x + cy * fu - sy * fw), (float) y + .012f, (float) (z + sy * fu + cy * fw), (float) (x - cy * fu - sy * fw), (float) y + .012f, (float) (z - sy * fu + cy * fw),
                IceMesh.FROST, .55f * alpha);
        IceParticles.obox(c, x, y + .02, z, r, hu, .014f, hw, IceMesh.CLEAR, .92f * alpha);
        if (!st.far(p.at)) for (int i = 0; i < 2; i++) {
            double h = IceMesh.hash(p.seed + i * 3.7), h2 = IceMesh.hash(p.seed + i * 5.1);
            float size = (.06f + .06f * (float) h) * s, lu = (float) (h2 - .5) * 1.6f * hu, lw = (float) (h - .5) * 1.6f * hw;
            IceParticles.cube(c, x + cy * lu - sy * lw, y + .034 + size * .35, z + sy * lu + cy * lw, size, size, size, p.yaw + (float) h * 3, (float) (h2 - .5) * .4f, 0,
                    i == 0 ? IceMesh.CLEAR : IceMesh.MILKY, alpha);
        }
    }

    // ------------------------------------------------------------------ the wind in his own view
    private static boolean windOn() {
        var mc = Minecraft.getInstance();
        return mc.player != null && (IcemanSlideSteer.riding() && IcemanSlideSteer.speed() > .3f || IcemanSlideSteer.dashing());
    }
    /** Thin streaks rushing past round the camera, more and brighter as the speed builds; kept off the middle of the view. */
    private static void wind(IceStage st, FilmContext f) {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null) return;
        Vec3 vel = new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
        double v = vel.length();
        if (v < .15) return;
        Vec3 dir = vel.scale(1 / v);
        float k = Mth.clamp((float) v / SLIDE_SPEED, 0, 1.4f);
        Vec3[] fr = IceMesh.frame(dir);
        int n = Math.round(6 + 10 * Math.min(1, k));
        for (int i = 0; i < n; i++) {
            double h1 = IceMesh.hash(i * 3.17), h2 = IceMesh.hash(i * 7.31), h3 = IceMesh.hash(i * 1.93);
            float ph = (float) ((st.time * (.05 + .03 * h3) * (1 + k) + h1) % 1.0);
            float ang = (float) (h2 * Mth.TWO_PI), rad = 1.3f + 2.2f * (float) h3;
            Vec3 at = st.cam.add(fr[0].scale(Mth.cos(ang) * rad)).add(fr[1].scale(Mth.sin(ang) * rad)).add(dir.scale(7 - 12 * ph));
            Vec3 tail = at.subtract(dir.scale(.8 + 1.6 * k));
            float a = .09f * Math.min(1, k) * Mth.sin(ph * Mth.PI);
            FilmFx.streak(f, tail, at, .012f, 0xeaf6ff, 0, a, false);
        }
    }

    // ------------------------------------------------------------------ the sound
    /** The ice hissing under him, following him, louder and higher with his speed. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final int id;
        private float vol;
        private boolean ended;
        Loop(int id, Entity e) {
            super(ModSounds.ICEMAN_SLIDE.get(), SoundSource.PLAYERS, RandomSource.create());
            this.id = id;
            looping = true; delay = 0; volume = .01f; pitch = .8f;
            x = e.getX(); y = e.getY(); z = e.getZ();
        }
        void end() { ended = true; }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            Rider r = RIDERS.get(id);
            if (e == null || r == null || r.loop != this) { stop(); return; }
            float k = Mth.clamp(r.speed / SLIDE_SPEED, 0, 1);
            float want = ended ? 0 : .2f + .55f * k;
            vol += (want - vol) * (want > vol ? .3f : .35f);
            if (ended && vol < .02f) { stop(); return; }
            volume = Math.max(.01f, vol);
            pitch = .8f + .4f * k;
            x = e.getX(); y = e.getY() + .2; z = e.getZ();
        }
    }

    // ------------------------------------------------------------------ cleaning up
    private static void clear() {
        RIDERS.clear();
        windLoop = null; windWant = 0;
        PATCHES.clear();
        IcemanTrack.clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); lastLevel = null; }
}
