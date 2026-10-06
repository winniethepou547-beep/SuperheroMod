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

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The cryogenic brush as everyone sees it (right click held, both hands raised), and its sculptures (IcemanBrushSculpt).
 * <p>
 * On a body: cold matter flowing out of both hands to them, not a beam: from each hand a strand that winds round the
 * other (a double helix of sparkles, thin glints and small tumbling shards travelling along it), mist puffs carried
 * along a gently curving path and spreading as they go, a faint broken shimmer pulsing down its middle; where it lands
 * frost crystals keep forming, growing and dropping away, frost flecks and mist splash off (the frost on the body and
 * their frosted screen are FrostFx's). Into the air: a lighter flow from the hands to the growing tip of the ice being
 * sculpted. The flow fades in and out (the mist thinning), its end eases from one target to the next; a hissing loop
 * (ICEMAN_BRUSH) follows him while he brushes and fades away when he stops.
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

    /** One Iceman's flow: how strong (eased in and out), how much of it is on a body, where it ends (eased), its sound. */
    private static final class Flow {
        float level, levelO, want, body = -1, frame = -1;
        int target = -1;
        long seen;
        Vec3 end, endWant;
        // this frame's geometry
        Vec3 h0, h1, mid, ctrl, fu, fv;
        double len;
        Loop loop;
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
            if (!on && f.level < .01f || who == null && now - f.seen > 20) { it.remove(); continue; }
            if (on && (f.loop == null || f.loop.isStopped())) { f.loop = new Loop(id, who); mc.getSoundManager().play(f.loop); }
            if (on && f.end != null && f.mid != null) splash(f, s, who);
        }
    }
    /** Where it lands: frost flecks and mist splashing off, now and then a small shard; mist left drifting along it. */
    private static void splash(Flow f, IcemanClient.State s, Entity who) {
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
            IceParticles.mist(end.add(IceParticles.jitter(.15)), dir.scale(-.01).add(IceParticles.jitter(.01)), .25f, .025f, body ? .26f : .16f, 22);
        if (body && IceParticles.rand() < .22f && IceParticles.count(1, end) > 0)
            IceParticles.shard(end, dir.scale(-.06).add(IceParticles.jitter(.06)).add(0, .08, 0), .05f + .04f * IceParticles.rand(), 26, IceMesh.FRESH);
        // Cold vapour left hanging along the way.
        if (IceParticles.rand() < .6f && IceParticles.count(1, f.mid) > 0) {
            float u = .15f + .7f * IceParticles.rand();
            IceParticles.mist(axis(f, u), dir.scale(.02).add(IceParticles.jitter(.006)), .16f, .018f, body ? .14f : .09f, 26);
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
    /** Strand i (the hand it leaves) at u: leaving the hand, winding round the middle line, closing in toward the end. */
    private static Vec3 strand(Flow f, int i, float u, float time, float spread) {
        Vec3 off = (i == 0 ? f.h0 : f.h1).subtract(f.mid).scale(Math.pow(1 - u, 1.3));
        float turns = (float) Math.min(6, f.len * .45);
        float ang = i * Mth.PI + u * turns * Mth.TWO_PI - time * .45f;
        float rho = spread * (.15f + .12f * Mth.sin(Mth.PI * u)) * (1 - .55f * u * u * u) * Math.min(1, u * 6 + .2f);
        return axis(f, u).add(off).add(f.fu.scale(Mth.cos(ang) * rho)).add(f.fv.scale(Mth.sin(ang) * rho));
    }
    private static float edges(float u) { return Math.min(1, u / .06f) * Math.min(1, (1 - u) / .06f); }

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
    /** The solid and bright parts: the strands' glints and sparkles, the tumbling shards, the crystals where it lands. */
    private static void flowIce(IceStage st, IceMesh.Ctx c, Flow f, float level, float time) {
        float body = f.body, light = .55f + .45f * body;
        boolean far = st.far(f.end) && st.far(f.mid);
        float spread = .7f + .3f * body;
        int n = Mth.clamp((int) (f.len * 6), 12, 60);
        for (int i = 0; i < 2; i++) {
            // The strand's thin glint, pulsing as matter runs down it.
            Vec3 prev = strand(f, i, 0, time, spread);
            for (int k = 1; k <= n; k++) {
                float u = k / (float) n;
                Vec3 q = strand(f, i, u, time, spread);
                float pulse = .35f + .65f * Math.max(0, Mth.sin(u * 26 - time * 1.7f + i * 2));
                float b = level * light * pulse * edges(u) * .5f;
                if (b > .01f) IceMesh.line(c, prev, q, .018f + .01f * body, .45f * b, .75f * b, b);
                prev = q;
            }
            // Sparkles travelling along it.
            int m = Mth.clamp((int) (f.len * (far ? 2 : 4)), 6, 48);
            for (int k = 0; k < m; k++) {
                float u = frac(k / (float) m + time * .045f + i * .37f);
                float tw = .55f + .45f * Mth.sin(time * 1.9f + k * 3.7f + i);
                IceMesh.sparkle(c, strand(f, i, u, time, spread), .07f + .07f * (float) IceMesh.hash(k * 3.1 + i), .75f * level * light * tw * edges(u));
            }
            if (far) continue;
            // Small shards of ice tumbling along with it.
            int sh = Mth.clamp((int) (f.len * (.8f + body)), 3, 24);
            for (int k = 0; k < sh; k++) {
                float u = frac(k / (float) sh + time * .055f + (float) IceMesh.hash(k * 1.9 + i) * .3f);
                Vec3 at = strand(f, i, u, time, spread * 1.3f).add(f.fu.scale(.06 * Mth.sin(k * 2.3f + time * .3f)));
                Vec3 spin = new Vec3(Mth.sin(time * .5f + k), Mth.cos(time * .37f + k * 1.3f), Mth.sin(time * .29f + k * .7f));
                IceMesh.shard(c, at, spin, (.035f + .04f * (float) IceMesh.hash(k * 5.3 + i)) * (.6f + .4f * body), k * 17 + i, k % 3 == 0 ? IceMesh.MILKY : IceMesh.CLEAR,
                        level * edges(u) * .9f);
            }
        }
        // Where it lands: frost crystals forming, growing, falling away, again and again.
        if (body > .05f) {
            Vec3 back = f.mid.subtract(f.end).normalize();
            Vec3[] fr = IceMesh.frame(back);
            for (int k = 0; k < (far ? 3 : 7); k++) {
                float cyc = frac((time + k * 2.6f) / 18f);
                float g = PantherMotion.snap(cyc, 0, .45f) * (1 - PantherMotion.k(cyc, .78f, 1));
                if (g <= .01f) continue;
                int gen = (int) Math.floor((time + k * 2.6f) / 18f);
                double h = IceMesh.hash(k * 13.7 + gen * 3.3), h2 = IceMesh.hash(k * 5.1 + gen * 7.9);
                float ang = (float) (h * Mth.TWO_PI), rr = (float) (.08 + .22 * h2);
                Vec3 base = f.end.add(fr[0].scale(Mth.cos(ang) * rr)).add(fr[1].scale(Mth.sin(ang) * rr));
                Vec3 dir = back.add(fr[0].scale(Mth.cos(ang) * .8)).add(fr[1].scale(Mth.sin(ang) * .8)).normalize();
                IceMesh.crystal(c, base, dir, (.16f + .2f * (float) h2) * g, .045f * g + .01f, 5, k * 31 + gen, k % 2 == 0 ? IceMesh.CLEAR : IceMesh.FROST,
                        level * body, .5f);
            }
            IceMesh.sparkle(c, f.end, .3f, .6f * level * body * (.7f + .3f * Mth.sin(time * 2.1f)));
        } else {
            // Sculpting: crystal nuclei winking at the tip.
            for (int k = 0; k < 3; k++) {
                float a = time * .4f + k * 2.1f;
                Vec3 at = f.end.add(f.fu.scale(Mth.cos(a) * .18)).add(f.fv.scale(Mth.sin(a) * .18));
                IceMesh.sparkle(c, at, .14f, .7f * level * (.6f + .4f * Mth.sin(time * 2.3f + k)));
            }
        }
    }
    /** The soft parts: mist carried along it, a faint broken shimmer down its middle, the cold glow at the hands and the end. */
    private static void flowFx(IceStage st, FilmContext fx, Flow f, float level, float time) {
        float body = f.body, k = .6f + .4f * body;
        int puffs = Mth.clamp((int) (f.len * 2.2f * k), 6, 30);
        for (int i = 0; i < puffs; i++) {
            float u = frac(i / (float) puffs + time * .03f);
            float a = i * 2.4f + time * .2f, rho = .1f + .12f * u;
            Vec3 at = axis(f, u).add(f.fu.scale(Mth.cos(a) * rho)).add(f.fv.scale(Mth.sin(a) * rho));
            FilmFx.puff(fx, at, .16f + .42f * u * k, IceParticles.MIST_RGB, .17f * level * k * edges(u));
        }
        // The shimmer: short pulses running down the middle, never a solid line.
        int n = Mth.clamp((int) (f.len * 4), 8, 40);
        Vec3 prev = axis(f, 0);
        for (int i = 1; i <= n; i++) {
            float u = i / (float) n, u0 = (i - 1) / (float) n;
            Vec3 q = axis(f, u);
            float p0 = pulse(u0, f.len, time), p1 = pulse(u, f.len, time);
            FilmFx.streak(fx, prev, q, .11f * k, IceParticles.COLD_LIGHT, .13f * level * k * p0 * edges(u0), .13f * level * k * p1 * edges(u), true);
            prev = q;
        }
        float flick = .85f + .15f * Mth.sin(time * 1.3f);
        FilmFx.glow(fx, f.h0, .3f, IceParticles.COLD_LIGHT, .22f * level * flick);
        FilmFx.glow(fx, f.h1, .3f, IceParticles.COLD_LIGHT, .22f * level * flick);
        FilmFx.glow(fx, f.end, .35f + .4f * body, IceParticles.COLD_LIGHT, (.12f + .2f * body) * level * flick);
        FilmFx.puff(fx, f.end, .4f + .3f * body, IceParticles.MIST_RGB, .14f * level);
    }
    private static float pulse(float u, double len, float time) {
        float s = Math.max(0, Mth.sin((float) (u * len * 2.3 - time * .9f)));
        return s * s;
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
