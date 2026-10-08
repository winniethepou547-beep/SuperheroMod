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
import net.minecraft.sounds.SoundEvent;
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
 * The ice slide (SHIFT): the path of ice freezing out of the air under him (IcemanTrack). Getting ready: cold air pouring
 * down round his feet, frost breathing off the ground, tiny ice crystals forming in the air ahead. Riding: a low plume of
 * freezing vapour under him and a little behind, ice crystals flicked out sideways off the path's lips, the air freezing
 * where his feet passed, tiny frost fragments and now and then a small shard thrown back, cold vapour trailing low
 * behind (all more with speed and down the track, but never a flood; behind and low, so he stays in plain view); a
 * sliding hiss (ICEMAN_SLIDE) following him that rises with his speed and the quiet ticking of ice forming; in his own
 * ears a wind rush rising on the way down and falling off the ice; in his own view, thin wind streaks rushing past as
 * the speed builds. Let go: on the ground, spray thrown out from the braking foot (the path's end cracks); in the air,
 * the path's last point freezes into a crystal cluster.
 * <p>
 * The sub-zero slide (CTRL): as it starts (FX_DASH) a strong burst of freezing vapour from under him rolling out low;
 * as he goes, a strip of ice grows along his dash line (irregular slab sections rising out of the ground behind him,
 * big crystal ridges leaning out and back off both its edges, clusters, broken chunks lying round it, frost on the
 * ground), spray and crystals thrown to the sides, vapour behind; a couple of seconds later it breaks up in stages
 * (cracks first, then zone by zone from where he started, chips and frost, the frost on the ground lingering).
 * FX_DASH_HIT: a crystal cluster grows fast round the point he struck (a big one with two at its sides and one ahead,
 * 1.2-1.6 blocks), cracks run up it, then it breaks apart with a frost burst and cold mist rolling out.
 * FX_SLIDE_HIT: frost bursting off a body the ice slide touched (the frost on the body and over their screen is FrostFx's).
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
                // He drops and launches: freezing vapour bursting out from under him, rolling out low.
                IceParticles.cryo(at.add(0, .12, 0), dir.scale(-1).add(0, .3, 0), 1.3f, .9f);
                IceParticles.coldMist(at, 2.2f, 1f);
                IceParticles.ring(at.add(0, .06, 0), 1.9f, .32f, IceParticles.MIST_RGB, .45f, 11, false);
                int n = IceParticles.count(5, at);
                for (int i = 0; i < n; i++) {
                    float sg = i % 2 == 0 ? 1 : -1;
                    Vec3 v = dir.scale(-.05 - .05 * IceParticles.rand()).add(side.scale(sg * (.05 + .07 * IceParticles.rand()))).add(0, .015, 0);
                    IceParticles.mist(at.add(IceParticles.jitter(.2)).add(0, .2, 0), v, .4f, .032f, .3f, 24 + (int) (IceParticles.rand() * 12));
                }
                int s = IceParticles.count(10, at);
                for (int i = 0; i < s; i++)
                    IceParticles.snow(at.add(IceParticles.jitter(.2)).add(0, .1, 0), dir.scale(-.1 * IceParticles.rand()).add(IceParticles.jitter(.08)).add(0, .1, 0), .025f, 16);
                sound(at, ModSounds.ICEMAN_COLD_WHOOSH.get(), .6f, 1.1f);
                if (IcemanClient.isMe(p.entity())) IcemanClient.kickFov(.55f);
            }
            case FX_DASH_HIT -> {
                Vec3 at = p.pos(), dir = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
                Vec3 flat = new Vec3(dir.x, 0, dir.z);
                flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
                float gy = IcemanTrack.ground(mc.level, at.add(0, .3, 0));
                Vec3 base = at.y - gy < 3 ? new Vec3(at.x, gy, at.z) : at.add(0, -.5, 0);
                if (IMPACTS.size() >= MAX_IMPACTS) IMPACTS.remove(0);
                IMPACTS.add(new Impact(base, flat, mc.level.getGameTime() + mc.getFrameTime(), (int) (IceParticles.rand() * 1e6), IceStage.light(base.add(0, .6, 0))));
                // The air round the blow freezing at once: a cold flash, the plume, frost flung out, the cold rolling low.
                IceParticles.flash(at, 1.1f, .8f, 4);
                IceParticles.cryo(at, flat.add(0, .45, 0), 1.4f, 1f);
                IceParticles.coldMist(base, 2.4f, 1f);
                burst(at, flat, 12, .14f);
                IceParticles.ring(base.add(0, .06, 0), 1.8f, .28f, IceParticles.COLD_LIGHT, .5f, 9, true);
                sound(at, ModSounds.ICEMAN_CRYSTAL_TICKS.get(), .85f, 1f);
                sound(base, ModSounds.ICEMAN_GROW_RUMBLE.get(), .55f, 1.15f);
                if (IcemanClient.isMe(p.id())) IcemanClient.shake(.25f);
                if (IcemanClient.isMe(p.entity())) IcemanClient.shake(.4f);
            }
            case FX_SLIDE_HIT -> {
                Entity t = mc.level.getEntity(p.entity());
                Vec3 at = t != null ? t.getBoundingBox().getCenter() : p.pos();
                Vec3 dir = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
                IceParticles.flash(at, .6f, .6f, 4);
                burst(at, dir, 12, .1f);
                IceParticles.cryo(at, dir, .5f, .8f);
                int k = IceParticles.count(3, at);
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
    private static void sound(Vec3 at, SoundEvent ev, float vol, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.x, at.y, at.z, ev, SoundSource.PLAYERS, vol, pitch * (.94f + .12f * IceParticles.rand()), false);
    }

    // ------------------------------------------------------------------ the strip of ice behind the sub-zero slide
    /** One point of the strip: on the ground (its top), along the way, when laid; broken off yet. */
    private static final class DNode {
        final Vec3 at, tan, side; final float top, born; final int seed, light; boolean broke;
        DNode(Vec3 at, Vec3 tan, float top, float born, int seed, int light) {
            this.at = at; this.tan = tan; this.side = new Vec3(tan.z, 0, -tan.x); this.top = top; this.born = born; this.seed = seed; this.light = light;
        }
        float h(int salt) { return IceGrowth.h(seed, salt); }
    }
    /** One sub-zero slide's strip: its points; when it ended, when it breaks up (-1 not yet). */
    private static final class Dash {
        final List<DNode> nodes = new ArrayList<>();
        final int seed = (int) (IceParticles.rand() * 1e6);
        float endAt = -1, breakAt = -1, lastAdd;
        boolean broken;
        void end(float now) { if (endAt < 0) { endAt = now; breakAt = now + DASH_HOLD; } }
        /** When point i breaks off (zone by zone from where he started). */
        float breakOf(int i) { return breakAt + 10f * i / Math.max(1, nodes.size() - 1) + 3 * nodes.get(i).h(7); }
    }
    private static final List<Dash> DASHES = new ArrayList<>();
    /** The strip holds DASH_HOLD ticks after the slide ends, breaks up over about 13, its frost lingers DASH_FROST more. */
    private static final int MAX_DASHES = 8, MAX_DASH_NODES = 40, DASH_HOLD = 22, DASH_FROST = 45;

    /** A sub-zero slide's blow: the cluster grown round the point struck. */
    private static final class Impact {
        final Vec3 base, dir; final float born; final int seed, light; boolean broke;
        Impact(Vec3 base, Vec3 dir, float born, int seed, int light) { this.base = base; this.dir = dir; this.born = born; this.seed = seed; this.light = light; }
        float h(int salt) { return IceGrowth.h(seed, salt); }
    }
    private static final List<Impact> IMPACTS = new ArrayList<>();
    /** It grows over IMPACT_GROW ticks, cracks from IMPACT_CRACK, breaks apart at IMPACT_BREAK; its frost lasts to IMPACT_LIFE. */
    private static final int MAX_IMPACTS = 8;
    private static final float IMPACT_GROW = 6, IMPACT_CRACK = 9, IMPACT_BREAK = 13, IMPACT_LIFE = 60;

    /** One Iceman sliding: how fast (smoothed), where his feet were, his sound, his strip, the quiet ice ticks. */
    private static final class Rider { float speed; Vec3 last, lastPatch; Loop loop; long seen, soundAt; boolean sliding, prepped; Dash dash; }
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
            if (now - en.getValue().seen > 40) {
                IcemanTrack.stop(en.getKey(), 1);
                if (en.getValue().dash != null) en.getValue().dash.end(now);
                it.remove();
            }
        }
        wind(mc);
        IcemanTrack.tick();
        dashes(now);
        IMPACTS.removeIf(im -> now - im.born > IMPACT_LIFE);
        for (Impact im : IMPACTS) {
            // The cluster struck breaks apart (once): zone by zone along the blow, frost flung, cold mist left.
            if (im.broke || now - im.born < IMPACT_BREAK) continue;
            im.broke = true;
            IceParticles.breakApart(im.base.add(0, .7, 0), im.dir, 1.5f, 1.3f, im.dir.scale(.1).add(0, .05, 0), 3, 8, IceMesh.CLEAR);
        }
    }
    /** The strips: ended when their slide is long over; breaking up zone by zone; gone once their frost has faded. */
    private static void dashes(float now) {
        for (Iterator<Dash> it = DASHES.iterator(); it.hasNext(); ) {
            Dash d = it.next();
            if (d.endAt < 0 && now - d.lastAdd > 20) d.end(now);
            if (d.breakAt < 0) continue;
            int n = d.nodes.size();
            if (n == 0 || now > d.breakAt + 13 + DASH_FROST) { it.remove(); continue; }
            if (!d.broken && now >= d.breakAt) {
                d.broken = true;
                // The heavy fracture with its sounds (zones along the strip), the rest chips off point by point.
                DNode a = d.nodes.get(0), b = d.nodes.get(n - 1);
                Vec3 mid = a.at.lerp(b.at, .5).add(0, .25, 0), axis = b.at.subtract(a.at);
                float len = (float) Math.max(.5, axis.length());
                IceParticles.breakApart(mid, axis, len, .8f, new Vec3(0, .05, 0), Math.max(1, Math.min(4, n / 4)), 11, IceMesh.CLEAR);
            }
            for (int i = 0; i < n; i++) {
                DNode p = d.nodes.get(i);
                if (p.broke || now < d.breakOf(i)) continue;
                p.broke = true;
                Vec3 at = new Vec3(p.at.x, p.top + .12, p.at.z);
                if (IceParticles.count(1, at) <= 0) continue;
                for (int k = 0; k < 2; k++) {
                    float sg = k == 0 ? 1 : -1;
                    IceParticles.shard(at.add(p.side.scale(sg * (.4 + .3 * IceParticles.rand()))), p.side.scale(sg * .05).add(IceParticles.jitter(.03)).add(0, .12 + .06 * IceParticles.rand(), 0),
                            .1f + .1f * IceParticles.rand(), 26 + (int) (IceParticles.rand() * 16), k == 0 ? IceMesh.FRESH : IceMesh.CLEAR);
                }
                IceParticles.frostDust(at, Vec3.ZERO, .8f);
                if (p.h(9) < .3f) IceParticles.mist(at, new Vec3(0, .004, 0), .35f, .025f, .18f, 30);
            }
        }
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
        // The slide over: on the ground its end cracks; in the air its last point freezes into a crystal cluster.
        if (r.sliding && !slide) {
            boolean ground = who.onGround() || !level.noCollision(who, who.getBoundingBox().move(0, -.3, 0));
            IcemanTrack.stop(id, ground || dash ? 1 : 2);
        }
        if (!slide) r.prepped = false;
        r.sliding = slide;
        // The sub-zero slide over: its strip holds a moment, then breaks up.
        if (!dash) { if (r.dash != null) { r.dash.end(now); r.dash = null; } r.lastPatch = null; }
        if (brake >= 0 && brake < 10) {
            // Braking: a foot dragged across the ice, spray thrown out ahead and to the side.
            float k = 1 - brake / 10f, sp = Math.max(r.speed, .2f);
            Vec3 foot = pos.add(side.scale(-.25)).add(dir.scale(.2)).add(0, .06, 0);
            int n = IceParticles.count(Math.round(5 * k * Math.min(1.5f, sp / .5f)), foot);
            for (int i = 0; i < n; i++)
                IceParticles.snow(foot.add(IceParticles.jitter(.1)), dir.scale(sp * (.25 + .3 * IceParticles.rand())).add(side.scale(-.05 - .1 * IceParticles.rand())).add(0, .08 + .1 * IceParticles.rand(), 0),
                        .025f + .025f * IceParticles.rand(), 14 + (int) (IceParticles.rand() * 8));
            if (brake < 6 && IceParticles.count(1, foot) > 0) IceParticles.mist(foot, dir.scale(.04), .3f, .03f, .25f * k, 22);
            if (brake < 1.5f && sp > .45f && IceParticles.count(1, foot) > 0) {
                IceParticles.shard(foot, dir.scale(.12).add(0, .12, 0), .05f, 24, IceMesh.FRESH);
                IceParticles.crystalDust(foot, dir.scale(.15).add(0, .06, 0), .02f, .4f, 16);
            }
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
                // Getting ready: the cold pouring down round his feet, frost breathing off the ground, crystals forming in the air ahead.
                if (!r.prepped) { r.prepped = true; IceParticles.coldMist(pos, 1.3f, .6f); }
                if (IceParticles.count(1, pos) > 0) IceParticles.frostDust(pos.add(0, .05, 0), Vec3.ZERO, .8f);
                int n = IceParticles.count(2, pos);
                for (int i = 0; i < n; i++)
                    IceParticles.crystalDust(pos.add(dir.scale(.3 + 1.8 * IceParticles.rand())).add(IceParticles.jitter(.25)).add(0, .12, 0),
                            new Vec3(0, .012, 0).add(IceParticles.jitter(.01)), .016f + .012f * IceParticles.rand(), (IceParticles.rand() - .5f) * .6f, 14 + (int) (IceParticles.rand() * 10));
                if (age > 3 && IceParticles.rand() < .5f) IceParticles.mist(pos.add(dir.scale(.8)).add(0, .05, 0), dir.scale(.03), .25f, .02f, .16f, 20);
                return;
            }
            r.prepped = true;
            // Down the track (or faster than his top speed) it all thickens.
            float down = (float) Mth.clamp(-vel.y / Math.max(.1, flat), 0, 1);
            float more = k + .6f * down;
            // Under him and a little behind: the cold pouring off his feet and freezing (a low plume, back and down).
            if (now % 3 == 0 && IceParticles.count(1, feet) > 0)
                IceParticles.cryo(pos.subtract(dir.scale(.35)).add(0, .06, 0), dir.scale(-1).add(0, -.25, 0), .16f + .2f * Math.min(1.2f, more), .5f);
            // Ice crystals flicked out sideways off the lips.
            int n = IceParticles.count(Math.round(1 + 2 * more), feet);
            for (int i = 0; i < n; i++) {
                float sg = IceParticles.rand() < .5f ? 1 : -1;
                Vec3 v = side.scale(sg * (.06 + .08 * IceParticles.rand())).add(dir.scale(-.03 - .05 * IceParticles.rand() * k)).add(0, .03 + .05 * IceParticles.rand(), 0);
                IceParticles.crystalDust(feet.add(side.scale(sg * .4)).add(IceParticles.jitter(.08)), v.add(vel.scale(.25)), .016f + .016f * IceParticles.rand(),
                        (IceParticles.rand() - .5f) * .5f, 12 + (int) (IceParticles.rand() * 10));
            }
            // Tiny frost fragments thrown back, the air freezing where his feet passed.
            int f = IceParticles.count(Math.round(more * 1.5f), feet);
            for (int i = 0; i < f; i++)
                IceParticles.snow(feet.add(IceParticles.jitter(.1)), dir.scale(-.05 - .08 * IceParticles.rand() * k).add(IceParticles.jitter(.03)).add(0, .04, 0).add(vel.scale(.3)),
                        .018f + .015f * IceParticles.rand(), 10 + (int) (IceParticles.rand() * 8));
            IceParticles.freezeTrail(feet, vel.scale(.5), .4f + .5f * Math.min(1, k));
            // Now and then a tiny shard.
            if (now % 4 == 0 && more > .4f && IceParticles.count(1, feet) > 0)
                IceParticles.shard(feet, dir.scale(-.08).add(IceParticles.jitter(.05)).add(0, .1, 0), .035f + .035f * IceParticles.rand(), 20, IceMesh.CLEAR);
            // Cold vapour trailing low behind (longer with speed).
            if (now % 2 == 0 && IceParticles.count(1, feet) > 0)
                IceParticles.mist(pos.subtract(dir.scale(.9)).add(0, .1, 0), dir.scale(-.02).add(vel.scale(.2)).add(0, .004, 0), .3f + .2f * k, .02f, .2f, 22 + Math.round(16 * k));
            // The ice forming under him, heard quietly now and then.
            if (now - r.soundAt > 22 + (id & 7) * 2) { r.soundAt = now; level.playLocalSound(feet.x, feet.y, feet.z, ModSounds.ICEMAN_CRYSTAL_TICKS.get(), SoundSource.PLAYERS, .2f, 1.1f + .25f * IceParticles.rand(), false); }
        } else if (r.loop != null) r.loop.end();
        if (dash) {
            // The strip of ice growing along his dash line.
            if (r.dash == null) {
                if (DASHES.size() >= MAX_DASHES) DASHES.remove(0);
                r.dash = new Dash();
                DASHES.add(r.dash);
                r.lastPatch = null;
            }
            Vec3 from = r.lastPatch == null ? pos : r.lastPatch;
            double d = from.distanceTo(pos);
            int steps = r.lastPatch == null ? 1 : Math.min(4, (int) Math.floor(d / .5));
            for (int i = 1; i <= steps; i++) {
                Vec3 at = r.lastPatch == null ? pos : from.lerp(pos, i / (double) steps);
                lay(level, r.dash, at, dir, now - (steps - i) * .3f);
            }
            if (steps > 0) r.lastPatch = pos;
            // Low and fast: crystals and spray thrown out to the sides, the air freezing behind him, vapour rolling back.
            int n = IceParticles.count(4, pos);
            for (int i = 0; i < n; i++) {
                float sg = i % 2 == 0 ? 1 : -1;
                IceParticles.crystalDust(pos.add(side.scale(sg * .45)).add(0, .12, 0), side.scale(sg * (.1 + .1 * IceParticles.rand())).add(dir.scale(.04)).add(0, .08 + .06 * IceParticles.rand(), 0),
                        .02f + .016f * IceParticles.rand(), (IceParticles.rand() - .5f) * .5f, 14 + (int) (IceParticles.rand() * 8));
                IceParticles.snow(pos.add(side.scale(sg * .35)).add(0, .1, 0), side.scale(sg * (.08 + .08 * IceParticles.rand())).add(dir.scale(.05)).add(0, .09 + .06 * IceParticles.rand(), 0),
                        .025f + .02f * IceParticles.rand(), 14);
            }
            if (now % 2 == 0 && IceParticles.count(1, pos) > 0)
                IceParticles.cryo(pos.subtract(dir.scale(.2)).add(0, .1, 0), dir.scale(-1).add(0, .2, 0), .35f, .6f);
            IceParticles.freezeTrail(pos.add(0, .15, 0), vel.scale(.4), 1.1f);
            if (now % 3 == 0 && IceParticles.count(1, pos) > 0)
                IceParticles.shard(pos.add(0, .15, 0), dir.scale(-.06).add(IceParticles.jitter(.05)).add(0, .12, 0), .05f + .04f * IceParticles.rand(), 22, IceMesh.FRESH);
            if (IceParticles.count(1, pos) > 0) IceParticles.mist(pos.subtract(dir.scale(.6)).add(0, .15, 0), dir.scale(-.03).add(0, .003, 0), .38f, .03f, .26f, 26);
        }
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

    /** A point of the strip where the ground is under him (none over a drop). */
    private static void lay(Level level, Dash d, Vec3 at, Vec3 dir, float born) {
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
        d.lastAdd = born;
        if (Float.isNaN(top) || d.nodes.size() >= MAX_DASH_NODES) return;
        Vec3 c = new Vec3(at.x, top, at.z);
        d.nodes.add(new DNode(c, dir, top, born, d.seed + d.nodes.size() * 7919, IceStage.light(c.add(0, .5, 0))));
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        boolean wind = windOn();
        if (!IcemanTrack.any() && DASHES.isEmpty() && IMPACTS.isEmpty() && !wind) return;
        IceStage st = IceStage.open(e);
        if (st == null) return;
        try {
            IceMesh.Ctx c = st.ice();
            IcemanTrack.draw(st, c);
            c.ox = c.oy = c.oz = 0;
            for (Dash d : DASHES) drawDash(st, c, d);
            for (Impact im : IMPACTS) drawImpact(st, c, im);
            st.endIce();
            if (wind) wind(st, st.fx());
        } finally {
            st.close();
        }
    }
    private static final float[] DA = new float[IceGrowth.SECTION * 3], DB = new float[IceGrowth.SECTION * 3], FR = new float[9];
    /** How far a point of the strip has grown (it rises out of the ground right behind him). */
    private static float dgrown(DNode n, float now) { return IceGrowth.grow(now - n.born + .5f, 0, 4); }
    /** How much of a point is left (all of it until it breaks off, then gone in a few ticks). */
    private static float dleft(Dash d, int i, float now) { return d.breakAt < 0 ? 1 : 1 - PantherMotion.ease((now - d.breakOf(i)) / 3); }
    /** The strip's section at a point: a broad irregular slab rising out of the ground, its sides each their own. */
    private static void dsection(DNode n, float g, float s, float[] out) {
        float w = 1.3f * (.5f + .5f * g) * s, th = .42f * s;
        float top = n.top + .1f * g * s;
        IceGrowth.section(n.at.x, top, n.at.z, n.side.x, 0, n.side.z, 0, 1, 0, w, th, n.seed, g, out);
        // One side bulging more than the other.
        float bl = (n.h(2) - .3f) * .2f * g * s, br = (n.h(3) - .3f) * .2f * g * s;
        out[5 * 3] += (float) n.side.x * bl; out[5 * 3 + 2] += (float) n.side.z * bl; out[5 * 3 + 1] += .05f * bl;
        out[9 * 3] -= (float) n.side.x * br; out[9 * 3 + 2] -= (float) n.side.z * br; out[9 * 3 + 1] += .05f * br;
    }
    /**
     * The sub-zero slide's strip: the slab (frost on the ground round it), big crystal ridges leaning out and back off
     * both edges (each its own delay), now and then a cluster and broken chunks lying beside it; cracks running over it
     * before it breaks; after, only the frost, fading.
     */
    private static void drawDash(IceStage st, IceMesh.Ctx c, Dash d) {
        List<DNode> l = d.nodes;
        int n = l.size();
        float now = st.time;
        for (int i = 0; i < n; i++) {
            DNode a = l.get(i);
            if (a.at.distanceToSqr(st.cam) > 80 * 80) continue;
            boolean far = st.far(a.at);
            float age = now - a.born;
            if (age < -1) continue;
            c.light = a.light;
            float g = dgrown(a, now), s = dleft(d, i, now);
            // The frost on the ground round it (lingering after the ice is gone).
            float frost = PantherMotion.snap(age, 0, 6) * (d.breakAt < 0 ? 1 : 1 - PantherMotion.k(now, d.breakAt + 10, d.breakAt + 13 + DASH_FROST));
            if (!far && i % 2 == 0 && frost > .02f) IcemanGroundFx.rime(c, new Vec3(a.at.x, a.top, a.at.z), (.85f + .35f * a.h(4)) * frost, .6f * frost, a.seed);
            if (s <= .01f) continue;
            if (i + 1 < n) {
                DNode b = l.get(i + 1);
                if (b.at.distanceToSqr(a.at) < 1.6) {
                    float sb = dleft(d, i + 1, now);
                    dsection(a, g, s, DA);
                    dsection(b, dgrown(b, now), Math.max(.01f, sb), DB);
                    boolean capA = i == 0 || l.get(i - 1).at.distanceToSqr(a.at) >= 1.6, capB = i + 2 >= n || l.get(i + 2).at.distanceToSqr(b.at) >= 1.6;
                    IceGrowth.slab(c, DA, DB, a.h(5) < .75f ? IceMesh.CLEAR : IceMesh.GLACIER, a.seed, Math.min(s, sb) < .3f ? Math.min(s, sb) / .3f : 1, capA, capB);
                    // Cracks running over it just before it breaks.
                    if (d.breakAt >= 0 && !far) {
                        float ck = PantherMotion.k(now, d.breakOf(i) - 8, d.breakOf(i) - 1) * s;
                        if (ck > .02f) {
                            Vec3 p0 = new Vec3(a.at.x, a.top + .1 * g + .012, a.at.z).add(a.side.scale((a.h(6) - .5) * .7));
                            Vec3 p1 = new Vec3(b.at.x, b.top + .1 * g + .012, b.at.z).add(b.side.scale((a.h(8) - .5) * .7));
                            Vec3 mid = p0.lerp(p1, .5).add(a.side.scale((a.h(10) - .5) * .4));
                            IceMesh.vein(c, p0, mid, .013f, .8f * ck);
                            IceMesh.vein(c, mid, p1, .013f, .8f * ck);
                            IceMesh.vein(c, mid, mid.add(a.side.scale(a.h(11) < .5f ? .45 : -.45)), .01f, .6f * ck);
                        }
                    }
                }
            }
            if (!far) dashGrowths(c, a, age, g, s);
        }
    }
    /** What grows on a point of the strip: crystal ridges off both edges, a cluster now and then, broken chunks beside it. */
    private static void dashGrowths(IceMesh.Ctx c, DNode n, float age, float g0, float s) {
        // At the break, the crystals are what flies off first.
        float keep = s >= .999f ? 1 : 0;
        if (keep <= 0) return;
        float hw = .65f * (.5f + .5f * g0), top = n.top + .1f * g0;
        double tx = n.tan.x, tz = n.tan.z, sx = n.side.x, sz = n.side.z;
        for (int k = 0; k < 2; k++) {
            float sg = k == 0 ? 1 : -1;
            for (int j = 0; j < 2; j++) {
                if (n.h(10 + k * 4 + j) > (j == 0 ? .82f : .45f)) continue;
                float g = IceGrowth.grow(age, .5f + 2.5f * n.h(20 + k * 4 + j) + j * 1.5f, 3 + 4 * n.h(30 + k * 4 + j));
                float len = (.45f + .6f * n.h(40 + k * 4 + j)) * (j == 0 ? 1 : .6f), r = len * (.22f + .08f * n.h(50 + k * 4 + j));
                float across = sg * hw * (j == 0 ? .82f : .55f);
                float ds = sg * (.6f + .3f * n.h(60 + k * 4 + j)), du = .55f + .4f * n.h(70 + k * 4 + j), dt = -.25f - .5f * n.h(80 + k * 4 + j);
                IceGrowth.crystal(c, n.at.x + sx * across, top - .05, n.at.z + sz * across, sx * ds + tx * dt, du, sz * ds + tz * dt,
                        len, r, n.seed * 11 + k * 4 + j, n.h(90 + k) < .25f ? IceMesh.GLACIER : IceMesh.CLEAR, g, 1);
            }
        }
        if (n.h(100) < .28f) {
            float sg = n.h(101) < .5f ? 1 : -1, across = sg * hw;
            IceGrowth.cluster(c, n.at.x + sx * across, top - .08, n.at.z + sz * across, sx * sg * .55 - tx * .3, 1, sz * sg * .55 - tz * .3,
                    .5f + .25f * n.h(102), n.seed * 13 + 1, 4, IceMesh.CLEAR, (age - 1 - 2 * n.h(103)) / 7f, 1);
        }
        if (n.h(110) < .4f && age > 1) {
            // A broken chunk lying beside it (thrown off as the ice burst up).
            float sg = n.h(111) < .5f ? 1 : -1, across = sg * (hw + .25f + .35f * n.h(112)), size = (.2f + .16f * n.h(113)) * Math.min(1, (age - 1) / 2);
            IceParticles.turn(n.h(114) * Mth.TWO_PI, (n.h(115) - .5f) * .8f, n.h(116) * 3, FR);
            IceGrowth.chip(c, n.at.x + sx * across + tx * (n.h(117) - .5f) * .4, n.top + size * .2f, n.at.z + sz * across + tz * (n.h(117) - .5f) * .4, FR, size,
                    n.seed * 17 + 3, n.h(118) < .4f ? IceMesh.FRESH : IceMesh.CLEAR, 1);
        }
    }
    /**
     * The blow's cluster: a big cluster grown fast round the point struck (leaning along the blow), one at each side and
     * a small one ahead, each its own delay; cracks running up it, then it breaks apart (the pieces are IceParticles');
     * the frost on the ground round it lingers and fades.
     */
    private static void drawImpact(IceStage st, IceMesh.Ctx c, Impact im) {
        if (im.base.distanceToSqr(st.cam) > 90 * 90) return;
        float age = st.time - im.born;
        if (age < 0) return;
        c.light = im.light;
        Vec3 b = im.base, d = im.dir, side = new Vec3(d.z, 0, -d.x);
        float frost = PantherMotion.snap(age, 0, 6) * (1 - PantherMotion.k(age, 30, IMPACT_LIFE));
        if (frost > .02f && !st.far(b)) IcemanGroundFx.rime(c, b, 1.9f * frost, .65f * frost, im.seed);
        float keep = 1 - PantherMotion.k(age, IMPACT_BREAK, IMPACT_BREAK + 2.5f);
        if (keep <= .01f) return;
        float t = age / IMPACT_GROW * (.4f + .6f * keep), al = Math.min(1, keep * 2);
        IceGrowth.cluster(c, b.x, b.y - .05, b.z, d.x * .45, 1, d.z * .45, 1.3f + .3f * im.h(1), im.seed, 7, IceMesh.CLEAR, t, al);
        for (int k = 0; k < 2; k++) {
            float sg = k == 0 ? 1 : -1, off = .5f + .25f * im.h(2 + k);
            IceGrowth.cluster(c, b.x + side.x * sg * off - d.x * .1, b.y - .05, b.z + side.z * sg * off - d.z * .1,
                    side.x * sg * .8 + d.x * .25, .75, side.z * sg * .8 + d.z * .25, .7f + .25f * im.h(4 + k), im.seed * 3 + k, 5,
                    k == 0 ? IceMesh.GLACIER : IceMesh.CLEAR, t - .15f - .15f * im.h(6 + k), al);
        }
        IceGrowth.cluster(c, b.x + d.x * .6, b.y - .05, b.z + d.z * .6, d.x * .9, .5, d.z * .9, .55f + .2f * im.h(8), im.seed * 5 + 1, 4, IceMesh.CLEAR,
                t - .25f, al);
        // Cracks running up it before it breaks (a faint cold line in the ice).
        float ck = PantherMotion.k(age, IMPACT_CRACK, IMPACT_BREAK) * keep;
        if (ck > .02f) for (int i = 0; i < 3; i++) {
            float a = im.h(10 + i) * Mth.TWO_PI, r = .25f + .15f * im.h(14 + i);
            Vec3 p0 = b.add(Mth.cos(a) * r, .2, Mth.sin(a) * r);
            Vec3 p1 = p0.add(Mth.cos(a) * .15 + d.x * .2, .5 + .3 * im.h(18 + i), Mth.sin(a) * .15 + d.z * .2);
            Vec3 p2 = p1.add((im.h(22 + i) - .5) * .3, .35, (im.h(26 + i) - .5) * .3);
            IceMesh.vein(c, p0, p1, .014f, ck);
            IceMesh.vein(c, p1, p2, .011f, .8f * ck);
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
        DASHES.clear();
        IMPACTS.clear();
        IcemanTrack.clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); lastLevel = null; }
}
