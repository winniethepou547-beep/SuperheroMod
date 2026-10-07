package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanConfig;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.FIRNI.superheromod.client.render.iceman.IceGrowth.h;
import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The cryogenic brush as everyone sees it (right click held, both hands raised), and its sculptures (IcemanBrushSculpt).
 * <p>
 * Never a clean beam: the AIR FREEZING out of his hands. Every tick a cryogenic plume leaves one hand or the other
 * (IceParticles.cryo: dense cold vapour, tiny ice crystals shot out, floating and spiralling, little fragments), and a
 * drawn stream of it rolls on to where it is aimed, widening as it goes: (1) soft blue-white vapour churning along a
 * turbulent path, denser and whiter at the hands; (2) snow and ice flecks carried in it; (3) glinting ice crystals;
 * (4) crystal nuclei that grow into small crystals and broken fragments as they travel, tumbling. The flow fades in and
 * out (the mist thinning), its end eases from one target to the next.
 * <p>
 * On a body: where it lands small clusters of ice crystals freeze onto them at points on the side facing him (each its
 * own place, size and time: growing out of a nucleus, holding, then breaking off and falling as chips with a breath of
 * mist, again and again), frost flecks and mist splash off; the frost on the body and their frosted screen are FrostFx's.
 * Into the air: the flow runs to the growing tip of the ice being sculpted. A hissing loop (ICEMAN_BRUSH) follows him
 * while he brushes and fades away when he stops; crystals forming on a body tick now and then.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanBrushFx {
    private IcemanBrushFx() {}

    public static void receive(IcemanFxPacket p) {
        switch (p.kind()) {
            case FX_SCULPT_POINT -> IcemanBrushSculpt.point(p.id(), p.entity(), Math.round(p.power()), p.pos(), (float) p.dir().x);
            case FX_SCULPT_END -> IcemanBrushSculpt.end(p.id(), Math.round(p.power()));
            case FX_SCULPT_BREAK -> IcemanBrushSculpt.crack(p.id(), p.pos());
            default -> {}
        }
    }

    /** Crystals frozen onto the body at once (at most). */
    private static final int GROWTHS = 6;
    /** One Iceman's flow: how strong (eased in and out), how much of it is on a body, where it ends (eased), its sound. */
    private static final class Flow {
        float level, levelO, want, body = -1, frame = -1;
        int target = -1, hand;
        long seen;
        Vec3 end, endWant;
        // this frame's geometry
        Vec3 h0, h1, mid, ctrl, fu, fv;
        double len;
        Loop loop;
        // the crystals on the body: when each began (game time; -1 none), how long it lasts, where on them, its way out, size
        final float[] gBorn = new float[GROWTHS], gLife = new float[GROWTHS], gSize = new float[GROWTHS];
        final Vec3[] gOff = new Vec3[GROWTHS], gUp = new Vec3[GROWTHS];
        final int[] gSeed = new int[GROWTHS], gOn = new int[GROWTHS];
        Flow() { java.util.Arrays.fill(gBorn, -1); }
    }
    private static final Map<Integer, Flow> FLOWS = new HashMap<>();
    private static ClientLevel lastLevel;

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { clear(); lastLevel = mc.level; }
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        IcemanBrushSculpt.tick();
        for (var en : IcemanClient.states().entrySet()) {
            IcemanClient.State s = en.getValue();
            if (s.action != BRUSH) continue;
            Entity e2 = mc.level.getEntity(en.getKey());
            if (e2 == null || !IcemanClient.isHero(e2)) continue;
            Flow f = FLOWS.computeIfAbsent(en.getKey(), id -> new Flow());
            f.seen = now;
        }
        for (Iterator<Map.Entry<Integer, Flow>> it = FLOWS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            int id = en.getKey();
            Flow f = en.getValue();
            Entity who = mc.level.getEntity(id);
            IcemanClient.State s = IcemanClient.get(id);
            boolean on = who != null && s != null && s.action == BRUSH && IcemanClient.clock(s, 0) >= BRUSH_RAISE - 1;
            f.want = on ? 1 : 0;
            if (on) f.target = s.brushTarget;
            f.levelO = f.level;
            f.level += (f.want - f.level) * (f.want > f.level ? .35f : .22f);
            growths(f, now, on);
            if (!on && f.level < .01f || who == null && now - f.seen > 20) { dropAll(f); it.remove(); continue; }
            if (on && (f.loop == null || f.loop.isStopped())) { f.loop = new Loop(id, who); mc.getSoundManager().play(f.loop); }
            if (on) plume(f, id, who);
            if (on && f.end != null && f.mid != null) splash(f);
        }
    }
    /**
     * The plume leaving his hands, one hand a tick in turn (modest: a steady cold breath, never a flood); from his own
     * hands in his own view a thinner one, started a little ahead so it never fogs his eyes.
     */
    private static void plume(Flow f, int id, Entity who) {
        f.hand ^= 1;
        Vec3 hand = IcemanLayer.hand(id, f.hand);
        Vec3 look = who.getLookAngle();
        if (hand == null) {
            float yaw = who.getYRot() * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
            hand = who.getEyePosition().add(look.scale(.6)).add(right.scale(f.hand == 0 ? .36 : -.36)).add(0, -.32, 0);
        }
        Vec3 dir = f.end != null ? f.end.subtract(hand) : look;
        dir = dir.lengthSqr() < 1e-6 ? look : dir.normalize();
        var mc = Minecraft.getInstance();
        boolean own = who == mc.player && mc.options.getCameraType().isFirstPerson();
        float body = f.body < 0 ? 0 : f.body;
        if (own) IceParticles.cryo(hand.add(dir.scale(.55)), dir, .16f, .2f);
        else IceParticles.cryo(hand.add(dir.scale(.12)), dir, .28f + .08f * body, .22f);
    }
    /** Where it lands: frost flecks and mist splashing off, now and then a chip; vapour left drifting along it. */
    private static void splash(Flow f) {
        Vec3 end = f.end, dir = end.subtract(f.mid);
        double len = dir.length();
        if (len < .1) return;
        dir = dir.scale(1 / len);
        boolean body = f.target >= 0;
        int n = IceParticles.count(body ? 3 : 1, end);
        for (int i = 0; i < n; i++) {
            Vec3 out = dir.scale(-.05 - .08 * IceParticles.rand()).add(IceParticles.jitter(.06)).add(0, .03, 0);
            IceParticles.snow(end.add(IceParticles.jitter(.12)), out, .02f + .025f * IceParticles.rand(), 12 + (int) (IceParticles.rand() * 10));
        }
        if (IceParticles.rand() < (body ? .5f : .25f) && IceParticles.count(1, end) > 0)
            IceParticles.mist(end.add(IceParticles.jitter(.15)), dir.scale(-.01).add(IceParticles.jitter(.01)), .25f, .025f, body ? .24f : .15f, 22);
        // Cold vapour left hanging along the way, sinking.
        if (IceParticles.rand() < .5f && f.ctrl != null && IceParticles.count(1, f.mid) > 0) {
            float u = .2f + .7f * IceParticles.rand();
            IceParticles.mist(axis(f, u), dir.scale(.015).add(IceParticles.jitter(.006)).add(0, -.003, 0), .18f + .12f * u, .02f, body ? .12f : .08f, 28);
        }
    }

    // ------------------------------------------------------------------ crystals freezing onto the body
    /** Starts new crystals on the body now and then, breaks off the ones whose time is up. */
    private static void growths(Flow f, long now, boolean on) {
        var level = Minecraft.getInstance().level;
        Entity t = on && f.target >= 0 ? level.getEntity(f.target) : null;
        for (int i = 0; i < GROWTHS; i++) {
            if (f.gBorn[i] < 0) continue;
            Entity on2 = level.getEntity(f.gOn[i]);
            if (on2 == null || on2.isRemoved()) { f.gBorn[i] = -1; continue; }
            if (now - f.gBorn[i] >= f.gLife[i] || f.gOn[i] != f.target || !on) drop(f, i, on2);
        }
        if (t == null || f.mid == null || IceParticles.rand() > .4f) return;
        int free = -1;
        for (int i = 0; i < GROWTHS && free < 0; i++) if (f.gBorn[i] < 0) free = i;
        if (free < 0) return;
        // A point on the side facing him, somewhere up their body; it grows out of them toward him, a little upward.
        Vec3 c = t.position().add(0, t.getBbHeight() * .5, 0);
        Vec3 n = f.mid.subtract(c);
        n = new Vec3(n.x, 0, n.z);
        n = n.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : n.normalize();
        Vec3 side = new Vec3(-n.z, 0, n.x);
        float w = t.getBbWidth(), hh = t.getBbHeight();
        float across = (IceParticles.rand() - .5f) * .85f, up = .12f + .8f * IceParticles.rand();
        Vec3 off = n.scale(w * .5 * (1 - Math.abs(across) * .6)).add(side.scale(across * w)).add(0, hh * up, 0);
        Vec3 out = n.scale(.8).add(side.scale(across * 1.2)).add(IceParticles.jitter(.35)).add(0, .25, 0).normalize();
        f.gBorn[free] = now; f.gLife[free] = 16 + (int) (IceParticles.rand() * 18);
        f.gOff[free] = off; f.gUp[free] = out; f.gSize[free] = (.16f + .2f * IceParticles.rand()) * Math.min(1.6f, Math.max(.7f, hh / 1.8f));
        f.gSeed[free] = (int) (IceParticles.rand() * 100000); f.gOn[free] = t.getId();
        if (IceParticles.rand() < .3f) {
            Vec3 at = t.position().add(off);
            level.playLocalSound(at.x, at.y, at.z, ModSounds.ICEMAN_CRYSTAL_TICKS.get(), SoundSource.PLAYERS, .22f, 1.2f + .3f * IceParticles.rand(), false);
        }
    }
    /** A crystal breaks off the body: a few chips falling, a fleck of frost, a breath of mist. */
    private static void drop(Flow f, int i, Entity on) {
        Vec3 at = on.position().add(f.gOff[i]);
        float s = f.gSize[i];
        int chips = IceParticles.count(2, at);
        for (int k = 0; k < chips; k++)
            IceParticles.shard(at.add(IceParticles.jitter(s * .3)), f.gUp[i].scale(.05).add(IceParticles.jitter(.03)).add(0, .04, 0), s * (.35f + .3f * IceParticles.rand()), 22 + (int) (IceParticles.rand() * 14),
                    k == 0 ? IceMesh.CLEAR : IceMesh.MILKY);
        IceParticles.frostDust(at, f.gUp[i].scale(.05), .6f);
        if (IceParticles.rand() < .5f) IceParticles.mist(at, f.gUp[i].scale(.01).add(0, -.004, 0), .18f, .02f, .16f, 20);
        f.gBorn[i] = -1;
    }
    private static void dropAll(Flow f) {
        var level = Minecraft.getInstance().level;
        for (int i = 0; i < GROWTHS; i++) {
            if (f.gBorn[i] < 0) continue;
            Entity on = level == null ? null : level.getEntity(f.gOn[i]);
            if (on != null) drop(f, i, on); else f.gBorn[i] = -1;
        }
    }

    // ------------------------------------------------------------------ the shape of the flow
    /** Works out this frame's hands, end and path. False when there is nothing to draw. */
    private static boolean geom(int id, Flow f, float partial, float time) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level.getEntity(id);
        IcemanClient.State s = IcemanClient.get(id);
        if (e == null) return false;
        Vec3 eye = e.getEyePosition(partial), look = e.getViewVector(partial);
        float yaw = e.getViewYRot(partial) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 h0 = IcemanLayer.hand(id, 0), h1 = IcemanLayer.hand(id, 1);
        if (h0 == null) h0 = eye.add(look.scale(.6)).add(right.scale(.36)).add(0, -.32, 0);
        if (h1 == null) h1 = eye.add(look.scale(.6)).add(right.scale(-.36)).add(0, -.32, 0);
        f.h0 = h0; f.h1 = h1;
        f.mid = h0.add(h1).scale(.5);
        // The end: the body's near side, or the growing tip of the sculpture (or where his aim is).
        Vec3 want = null;
        boolean brushing = s != null && s.action == BRUSH;
        int target = brushing ? s.brushTarget : f.target;
        float bodyWant = target >= 0 ? 1 : 0;
        if (target >= 0) {
            Entity t = mc.level.getEntity(target);
            if (t != null) {
                Vec3 c = t.getPosition(partial).add(0, t.getBbHeight() * .55, 0);
                Vec3 to = c.subtract(f.mid);
                double d = to.length();
                want = d < 1e-3 ? c : c.subtract(to.scale(Math.min(d * .5, t.getBbWidth() * .45) / d));
            }
        } else if (brushing && s.sculpture != 0) want = IcemanBrushSculpt.tip(s.sculpture);
        // Fading out (or the body gone): the end stays where it was while the flow thins away.
        if (want == null && f.end != null && (!brushing || target >= 0)) want = f.end;
        if (want == null) want = eye.add(look.scale(target >= 0 ? 8 : IcemanConfig.SCULPT_DISTANCE.get()));
        float dt = f.frame < 0 ? 0 : Mth.clamp(time - f.frame, 0, 3);
        f.frame = time;
        if (f.end == null || f.end.distanceToSqr(want) > 30 * 30) f.end = want;
        else f.end = f.end.lerp(want, 1 - Math.exp(-dt * .55));
        f.body = f.body < 0 ? bodyWant : f.body + (bodyWant - f.body) * (1 - (float) Math.exp(-dt * .3f));
        Vec3 dir = f.end.subtract(f.mid);
        f.len = dir.length();
        if (f.len < .2) return false;
        Vec3 dn = dir.scale(1 / f.len);
        Vec3[] fr = IceMesh.frame(dn);
        f.fu = fr[0]; f.fv = fr[1];
        // A gentle arc: lifted a little and swaying lazily to the side.
        f.ctrl = f.mid.lerp(f.end, .42).add(0, f.len * .07, 0).add(f.fu.scale(f.len * .05 * Mth.sin(time * .09f)));
        return true;
    }
    /** The middle line of the flow at u (0 the hands .. 1 the end). */
    private static Vec3 axis(Flow f, float u) {
        float a = (1 - u) * (1 - u), b = 2 * u * (1 - u), c = u * u;
        return new Vec3(f.mid.x * a + f.ctrl.x * b + f.end.x * c, f.mid.y * a + f.ctrl.y * b + f.end.y * c, f.mid.z * a + f.ctrl.z * b + f.end.z * c);
    }
    /**
     * Something carried in the stream at u (its own index i): leaving one hand or the other, swept toward the middle
     * line, thrown about by churning air that opens wider the further it goes (the plume's volume); open = how far out.
     */
    private static Vec3 carried(Flow f, int i, float u, float time, float open) {
        Vec3 hand = (i & 1) == 0 ? f.h0 : f.h1;
        Vec3 off = hand.subtract(f.mid).scale(Math.pow(1 - u, 1.6));
        float p = h(i, 1) * Mth.TWO_PI, q = h(i, 2) * Mth.TWO_PI;
        float wide = open * (.05f + .42f * u) * (.4f + .6f * h(i, 3));
        float a = p + u * (3 + 4 * h(i, 4)) - time * (.08f + .1f * h(i, 5));
        float tu = Mth.cos(a) + .35f * Mth.sin(u * 9.1f + time * .23f + q), tv = Mth.sin(a) + .35f * Mth.cos(u * 7.3f - time * .19f + p);
        return axis(f, u).add(off).add(f.fu.scale(tu * wide)).add(f.fv.scale(tv * wide));
    }
    private static float edges(float u) { return Math.min(1, u / .06f) * Math.min(1, (1 - u) / .08f); }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (FLOWS.isEmpty() && !IcemanBrushSculpt.any()) return;
        IceStage st = IceStage.open(e);
        if (st == null) return;
        try {
            float time = st.time, partial = st.partial;
            IceMesh.Ctx c = st.ice();
            IcemanBrushSculpt.drawIce(st, c);
            for (var en : FLOWS.entrySet()) {
                Flow f = en.getValue();
                float level = Mth.lerp(partial, f.levelO, f.level);
                f.mid = null;
                bodyIce(st, c, f);
                if (level <= .01f || !geom(en.getKey(), f, partial, time)) { f.mid = null; continue; }
                c.light = IceStage.light(f.mid);
                flowIce(st, c, f, level, time);
            }
            st.endIce();
            FilmContext fx = st.fx();
            IcemanBrushSculpt.drawFx(st, fx);
            for (Flow f : FLOWS.values()) {
                if (f.mid == null) continue;
                flowFx(st, fx, f, Mth.lerp(partial, f.levelO, f.level), time);
            }
        } finally {
            st.close();
        }
    }
    /** The crystals frozen onto the body: each a little cluster grown out of a nucleus, loosening just before it breaks off. */
    private static void bodyIce(IceStage st, IceMesh.Ctx c, Flow f) {
        var level = Minecraft.getInstance().level;
        for (int i = 0; i < GROWTHS; i++) {
            if (f.gBorn[i] < 0) continue;
            Entity on = level.getEntity(f.gOn[i]);
            if (on == null) continue;
            float age = st.time - f.gBorn[i];
            if (age < 0) continue;
            float loose = PantherMotion.k(age, f.gLife[i] - 4, f.gLife[i]);
            Vec3 up = f.gUp[i];
            Vec3 at = on.getPosition(st.partial).add(f.gOff[i]).add(up.scale(.05 * loose)).add(0, -.12 * loose * loose, 0);
            c.light = IceStage.light(at);
            c.ox = (float) at.x; c.oy = (float) at.y; c.oz = (float) at.z;
            int count = st.far(at) ? 3 : 4 + (f.gSeed[i] & 1);
            IceGrowth.cluster(c, at.x, at.y, at.z, up.x, up.y, up.z, f.gSize[i], f.gSeed[i], count, (f.gSeed[i] & 2) == 0 ? IceMesh.CLEAR : IceMesh.GLACIER, age / 9f, 1);
        }
        c.ox = c.oy = c.oz = 0;
    }
    /** The hard bits carried in it: crystal nuclei growing into little crystals and broken fragments as they travel, glints. */
    private static void flowIce(IceStage st, IceMesh.Ctx c, Flow f, float level, float time) {
        float body = f.body, open = .75f + .35f * body;
        boolean far = st.far(f.end) && st.far(f.mid);
        Vec3 d = f.end.subtract(f.mid).scale(1 / f.len);
        // Glinting ice crystals.
        int m = Mth.clamp((int) (f.len * (far ? 2 : 4.5f)), 8, 44);
        for (int k = 0; k < m; k++) {
            float u = frac(k / (float) m + time * (.04f + .02f * h(k, 9)) + h(k, 10) * .4f);
            float tw = .5f + .5f * Mth.sin(time * (1.4f + .8f * h(k, 11)) + k * 3.7f);
            IceMesh.sparkle(c, carried(f, 100 + k, u, time, open), .045f + .06f * h(k, 12), .7f * level * tw * tw * edges(u));
        }
        if (far) return;
        // Nuclei growing as they go: needles of ice along the flow, broken chips tumbling.
        int n = Mth.clamp((int) (f.len * (2 + body)), 4, 26);
        for (int k = 0; k < n; k++) {
            float u = frac(k / (float) n + time * (.05f + .015f * h(k, 20)) + h(k, 21) * .3f);
            float a = level * edges(u);
            if (a <= .02f) continue;
            Vec3 at = carried(f, 200 + k, u, time, open * 1.1f);
            float size = (.03f + .05f * h(k, 22)) * (.35f + 1.1f * u) * (.75f + .35f * body);
            c.ox = (float) at.x; c.oy = (float) at.y; c.oz = (float) at.z;
            if ((k & 1) == 0) {
                IceGrowth.crystal(c, at.x, at.y, at.z, d.x + (h(k, 23) - .5f) * .6f, d.y + (h(k, 24) - .5f) * .6f, d.z + (h(k, 25) - .5f) * .6f,
                        size * 2.4f, size * .45f, 300 + k, IceMesh.CLEAR, Math.min(1, .2f + u * 1.4f), a);
            } else {
                float ax = h(k, 26) - .5f, ay = h(k, 27) - .2f, az = h(k, 28) - .5f, al = Mth.sqrt(ax * ax + ay * ay + az * az) + 1e-4f;
                IceParticles.axisAngle(ax / al, ay / al, az / al, time * (.2f + .3f * h(k, 29)) + k, IceParticles.AX);
                IceGrowth.chip(c, at.x, at.y, at.z, IceParticles.AX, size * 1.4f, 400 + k, (k % 3) == 1 ? IceMesh.MILKY : IceMesh.FRESH, a);
            }
        }
        c.ox = c.oy = c.oz = 0;
        if (body > .05f) IceMesh.sparkle(c, f.end, .26f, .5f * level * body * (.7f + .3f * Mth.sin(time * 2.1f)));
    }
    /**
     * The soft parts: the vapour churning along it (dense and whiter at the hands, opening and thinning further on),
     * snow flecks carried in it, a faint cold glow at the hands and the end.
     */
    private static void flowFx(IceStage st, FilmContext fx, Flow f, float level, float time) {
        float body = f.body, k = .6f + .4f * body, open = .8f + .35f * body;
        int puffs = Mth.clamp((int) (f.len * 3.4f * k), 10, 40);
        for (int i = 0; i < puffs; i++) {
            float u = frac(i / (float) puffs + time * (.03f + .012f * h(i, 40)));
            Vec3 at = carried(f, 500 + i, u, time, open * .8f);
            float size = .14f + .5f * u * k * (.7f + .5f * h(i, 41));
            FilmFx.puff(fx, at, size, IceParticles.MIST_RGB, .15f * level * k * edges(u) * (1 - .35f * u));
        }
        // Dense cold at the hands: whiter vapour boiling off them.
        for (int i = 0; i < 6; i++) {
            float u = frac(i / 6f + time * .07f) * .25f;
            Vec3 at = carried(f, 600 + i, u, time, open);
            FilmFx.puff(fx, at, .1f + .5f * u, 0xdcecff, .2f * level * Math.min(1, u * 20) * (1 - u * 4));
        }
        // Snow flecks.
        int flecks = Mth.clamp((int) (f.len * 5), 10, 48);
        for (int i = 0; i < flecks; i++) {
            float u = frac(i / (float) flecks + time * (.045f + .03f * h(i, 50)) + h(i, 51) * .5f);
            FilmFx.puff(fx, carried(f, 700 + i, u, time, open * 1.2f), .022f + .02f * h(i, 52), IceParticles.SNOW_RGB, .75f * level * edges(u));
        }
        float flick = .85f + .15f * Mth.sin(time * 1.3f);
        FilmFx.glow(fx, f.h0, .22f, IceParticles.COLD_LIGHT, .14f * level * flick);
        FilmFx.glow(fx, f.h1, .22f, IceParticles.COLD_LIGHT, .14f * level * flick);
        FilmFx.glow(fx, f.end, .3f + .3f * body, IceParticles.COLD_LIGHT, (.08f + .12f * body) * level * flick);
        FilmFx.puff(fx, f.end, .4f + .35f * body, IceParticles.MIST_RGB, .16f * level);
    }
    private static float frac(float x) { return x - (float) Math.floor(x); }

    // ------------------------------------------------------------------ the sound
    /** The brush's hiss, following him while he brushes, fading in and out. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final int id;
        private float vol;
        Loop(int id, Entity e) {
            super(ModSounds.ICEMAN_BRUSH.get(), SoundSource.PLAYERS, RandomSource.create());
            this.id = id;
            looping = true; delay = 0; volume = .01f; pitch = 1;
            x = e.getX(); y = e.getY() + 1.3; z = e.getZ();
        }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            Flow f = FLOWS.get(id);
            if (e == null || f == null || f.loop != this) { stop(); return; }
            float body = f.body < 0 ? (f.target >= 0 ? 1 : 0) : f.body;
            float want = f.want * (.45f + .35f * body);
            vol += (want - vol) * (want > vol ? .35f : .25f);
            if (f.want <= 0 && vol < .02f) { stop(); return; }
            volume = Math.max(.01f, vol);
            pitch = .92f + .16f * body + .03f * Mth.sin(e.tickCount * .2f);
            Vec3 at = f.mid != null ? f.mid : e.getEyePosition();
            x = at.x; y = at.y; z = at.z;
        }
    }

    // ------------------------------------------------------------------ cleaning up
    private static void clear() {
        FLOWS.clear();
        IcemanBrushSculpt.clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); lastLevel = null; }
}
