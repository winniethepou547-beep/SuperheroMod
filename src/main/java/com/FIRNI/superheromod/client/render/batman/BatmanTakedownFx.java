package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.heroes.batman.BatmobileEntity;
import com.FIRNI.superheromod.heroes.batman.TakedownPath;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * The Batmobile remote takedown as everyone sees it (the server's side is BatmanTakedown, the car BatmobileEntity, his
 * body TakedownMotion): the earpiece signal by his head (a three-quarter ring sending small waves; red with a red X
 * when a missed call is cancelled), the air torn by the dash, the tracker on the back of their head with its blinking
 * red light, the one held turned away from him, struggling, jolted right and left by the rounds and driven face first
 * into the ground (a render turn of their whole body), the Batmobile's guns (muzzle flashes, short fast tracers, sparks
 * and smoke where they land), the slide (tyre smoke, black tyre marks, grit thrown up, the headlights), the blue Arkham
 * afterburner as it boosts away, smoke rising from where the rounds struck, dust where they hit the ground; his own
 * camera's small moves (a look toward the car, shakes as the rounds land, the dip of the flip).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanTakedownFx {
    /** One takedown seen here: the dash's way, the one touched and their feet, when it began, where he was then (his own client). */
    static final class Rec {
        Vec3 dir = new Vec3(0, 0, 1), feet, from;
        int target = -1;
        float dashAt = -1000, hitAt = -1000, missAt = -1000;
        float startYaw;
        boolean dazedDust;
    }
    private static final Map<Integer, Rec> RECS = new HashMap<>();
    private static final Set<Integer> PUSHED = new HashSet<>();
    private static final int CYAN = 0x8fe6ff, RED = 0xff3b30, SPARK = 0xffd9a0, SMOKE = 0x8a8a8a, BOOST = 0x5fd0ff, BOOST_CORE = 0xd8f6ff;

    private BatmanTakedownFx() {}

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }
    private static int me() { var p = Minecraft.getInstance().player; return p == null ? -1 : p.getId(); }

    static Rec rec(int batman) { return RECS.get(batman); }
    /** Ticks since his touch (hold time), or -1 when there is none going on. */
    static float holdTime(Rec r) { return r == null || r.hitAt < -999 ? -1 : now() - r.hitAt; }

    public static void receive(BatmanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Rec r = RECS.computeIfAbsent(p.id(), id -> new Rec());
        float t = now();
        Vec3 d = p.dir();
        r.dir = d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : new Vec3(d.x, 0, d.z).normalize();
        switch ((int) p.power()) {
            case 0 -> { r.dashAt = t; r.hitAt = r.missAt = -1000; r.target = -1; r.feet = r.from = null; if (p.id() == me()) BatmanClient.kickFov(.6f); }
            case 1 -> {
                r.hitAt = t; r.target = p.entity(); r.feet = p.pos(); r.dazedDust = false;
                Entity b = mc.level.getEntity(p.id());
                r.from = b == null ? p.pos().subtract(r.dir) : b.position();
                r.startYaw = b == null ? 0 : b.getYRot();
                if (p.id() == me() || p.entity() == me()) BatmanClient.shake(.25f);
            }
            default -> r.missAt = t;
        }
    }

    // ------------------------------------------------------------------ geometry
    private static Vec3 front(Rec r) { return r.dir.scale(-1); }
    /** How far over (0..1) the held one is, face first, at hold time t (down at the impact, up again after TD_DOWN). */
    static float lie(float t) {
        if (t < TD_FALL) return 0;
        float fall = TakedownPath.ease((t - TD_FALL) / (TD_IMPACT - TD_FALL));
        fall = fall * fall;
        return Math.min(fall, 1 - TakedownPath.ease((t - TD_DOWN) / 9f));
    }
    /** A point on the held body (up along it, then forward, then to its left), with its fall worked in. */
    private static Vec3 onBody(Rec r, Vec3 feet, float t, double up, double fwd, double left) {
        Vec3 f = front(r), l = new Vec3(f.z, 0, -f.x);
        double phi = lie(t) * Mth.HALF_PI;
        Vec3 upDir = new Vec3(0, Math.cos(phi), 0).add(f.scale(Math.sin(phi)));
        Vec3 fwdDir = f.scale(Math.cos(phi)).add(0, -Math.sin(phi), 0);
        return feet.add(0, .2 * lie(t), 0).add(upDir.scale(up)).add(fwdDir.scale(fwd)).add(l.scale(left));
    }
    private static LivingEntity target(Rec r) {
        var level = Minecraft.getInstance().level;
        return level == null || r.target < 0 || !(level.getEntity(r.target) instanceof LivingEntity l) ? null : l;
    }
    /** The run and its clock: the car itself when it is here, else worked out from the touch. */
    private static TakedownPath.Run run(Rec r) { return new TakedownPath.Run(r.feet, front(r), TakedownPath.side(idOf(r), r.target)); }
    private static int idOf(Rec r) { for (var en : RECS.entrySet()) if (en.getValue() == r) return en.getKey(); return -1; }
    private static BatmobileEntity car(int batman) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        for (BatmobileEntity e : mc.level.getEntitiesOfClass(BatmobileEntity.class, mc.player.getBoundingBox().inflate(160), e -> e.ownerId() == batman)) return e;
        return null;
    }
    private static float carClock(int batman, Rec r, float partial) {
        BatmobileEntity e = car(batman);
        return e != null && e.run() != null ? e.clock(partial) : holdTime(r) - TD_CAR;
    }
    /** A muzzle of the car at its clock c (its pose on the run there). */
    private static Vec3 muzzle(TakedownPath.Run run, float c, int gun) {
        Vec3 at = run.pos(c), nose = run.nose(c), left = new Vec3(nose.z, 0, -nose.x);
        float x = gun == 0 ? -TakedownPath.GUN_X : TakedownPath.GUN_X;
        return at.add(left.scale(x)).add(0, TakedownPath.GUN_Y, 0).add(nose.scale(TakedownPath.GUN_Z));
    }
    private static Vec3 rearWheel(TakedownPath.Run run, float c, int side) {
        Vec3 at = run.pos(c), nose = run.nose(c), left = new Vec3(nose.z, 0, -nose.x);
        return at.add(left.scale(side * 1.62)).subtract(nose.scale(2.05));
    }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || RECS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = now(), partial = e.getPartialTick();
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        var fx = FilmFx.batched();
        Vec3 right = new Vec3(rr.x, rr.y, rr.z), up = new Vec3(uu.x, uu.y, uu.z);
        FilmContext c = new FilmContext(p, fx, cam, right, up, t, 0, partial);
        try {
            for (var en : RECS.entrySet()) {
                int batman = en.getKey();
                Rec r = en.getValue();
                Entity b = mc.level.getEntity(batman);
                if (b != null) signal(c, b, partial, right, up);
                if (b != null) dash(c, b, r, t, partial);
                if (r.feet == null || r.hitAt < -999) continue;
                float h = t - r.hitAt;
                if (h > TD_DOWN + 140) continue;
                LivingEntity target = target(r);
                Vec3 feet = target == null ? r.feet : target.getPosition(partial);
                tracker(c, r, feet, h, target);
                float cc = carClock(batman, r, partial);
                TakedownPath.Run run = run(r);
                tyres(c, run, r, cc, h);
                if (cc > -1 && cc < TakedownPath.GONE + 2) car(c, run, cc);
                rounds(c, run, r, feet, cc, h);
                scorched(c, r, feet, cc, h);
                impact(c, r, feet, h);
            }
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }

    /** The earpiece signal: a small three-quarter ring by his head sending waves; cancelled: red, with a red X. */
    private static void signal(FilmContext c, Entity b, float partial, Vec3 right, Vec3 up) {
        BatmanClient.State s = BatmanClient.get(b);
        if (s == null || (s.action != TD_SIGNAL && s.action != TD_MISS)) return;
        if (b == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        float t = BatmanClient.clock(s, partial);
        boolean miss = s.action == TD_MISS;
        float from = miss ? TD_MISS_EAR + 1 : TD_EAR - 1, to = miss ? TD_MISS_TICKS - 2 : TD_SIGNAL_TICKS + 2;
        float k = Math.min(TakedownPath.k(t, from, from + 2), 1 - TakedownPath.k(t, to - 3, to));
        if (k <= .01f) return;
        float yaw = Mth.rotLerp(partial, b.yRotO, b.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 at = b.getEyePosition(partial).add(side.scale(.38)).add(0, .06, 0);
        boolean cancelled = miss && t >= TD_ABORT;
        int col = cancelled ? RED : CYAN;
        float confirm = miss ? 0 : Math.max(0, 1 - Math.abs(t - TD_CONFIRM) / 2.5f);
        arc(c, at, right, up, .13, cancelled || confirm > .5f ? 360 : 270, .014, col, .9f * k);
        FilmFx.glow(c, at, .1 + .15 * confirm, cancelled ? RED : 0xdff8ff, (.5f + .5f * confirm) * k);
        // The waves going out, a small one every five ticks.
        if (!cancelled) for (int i = 0; i < 2; i++) {
            float ph = ((t * .2f) + i * .5f) % 1;
            arc(c, at, right, up, .15 + .26 * ph, 270, .01, col, .6f * (1 - ph) * k);
        }
        if (cancelled) {
            float x = TakedownPath.ease((t - TD_ABORT) / 3f) * .11f;
            Vec3 a = right.add(up).normalize().scale(x), d = right.subtract(up).normalize().scale(x);
            FilmFx.streak(c, at.subtract(a), at.add(a), .02, RED, .95f * k, .95f * k, true);
            FilmFx.streak(c, at.subtract(d), at.add(d), .02, RED, .95f * k, .95f * k, true);
        }
    }
    /** An arc of a ring facing the camera (a three-quarter ring opens at its lower left). */
    private static void arc(FilmContext c, Vec3 at, Vec3 right, Vec3 up, double radius, float degrees, double width, int rgb, float alpha) {
        if (alpha <= .01f) return;
        int n = 22;
        double start = Math.toRadians(225 - degrees), span = Math.toRadians(degrees);
        Vec3 prev = null;
        for (int i = 0; i <= n; i++) {
            double a = start + span * i / n;
            Vec3 pt = at.add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
            if (prev != null) FilmFx.streak(c, prev, pt, width, rgb, alpha, alpha, true);
            prev = pt;
        }
    }
    /** The air torn by the dash: pale streaks streaming off him. */
    private static void dash(FilmContext c, Entity b, Rec r, float t, float partial) {
        BatmanClient.State s = BatmanClient.get(b);
        if (s == null || s.action != TD_DASH) return;
        float d = BatmanClient.clock(s, partial);
        float k = Math.min(TakedownPath.k(d, TD_WIND, TD_WIND + 1.5f), 1 - TakedownPath.k(d, TD_WIND + TD_MOVE, TD_DASH_TICKS + 2));
        if (k <= .01f) return;
        Vec3 at = b.getPosition(partial), back = r.dir.scale(-1), side = new Vec3(-r.dir.z, 0, r.dir.x);
        for (int i = 0; i < 7; i++) {
            double h = .25 + 1.5 * TakedownPath.hash(i * 3.1), o = (TakedownPath.hash(i * 5.7) - .5) * 1.1;
            Vec3 head = at.add(side.scale(o)).add(0, h, 0).add(back.scale(.3 + .4 * TakedownPath.hash(i + Math.floor(d))));
            FilmFx.streak(c, head.add(back.scale(1.4 + TakedownPath.hash(i * 9.1))), head, .02, 0xe8f0ff, 0, .35f * k, true);
        }
    }
    /** The tracker: a small gunmetal puck on the back of their head, its red light blinking. */
    private static void tracker(FilmContext c, Rec r, Vec3 feet, float h, LivingEntity target) {
        if (h < TD_TRACK || h > TD_DOWN + 60) return;
        double height = target == null ? 1.8 : target.getBbHeight();
        Vec3 at = onBody(r, feet, h, height * .86, -.27, 0);
        Vec3 out = onBody(r, feet, h, height * .86, -.33, 0).subtract(at).normalize();
        float s = .06f;
        Matrix4f m = c.pose().last().pose();
        FilmFx.cube(c, m, (float) at.x - s, (float) at.y - s, (float) at.z - s, (float) at.x + s, (float) at.y + s * .6f, (float) at.z + s, 0x2a2d33);
        boolean on = ((int) (h - TD_TRACK)) % 6 < 2;
        Vec3 led = at.add(out.scale(.07));
        FilmFx.glow(c, led, on ? .1 : .05, RED, on ? 1f : .35f);
        if (on) FilmFx.glow(c, led, .32, RED, .3f);
        // The click: a small red ring as it sets.
        float since = h - TD_TRACK;
        if (since < 5) FilmFx.ring(c, led, .05 + .25 * since / 5, .02, RED, .7f * (1 - since / 5), true);
    }
    /** Tyre smoke off the back wheels through the slide, and the black marks they leave. */
    private static void tyres(FilmContext c, TakedownPath.Run run, Rec r, float cc, float h) {
        float y = (float) r.feet.y + .03f;
        float fade = 1 - TakedownPath.k(h, TD_DOWN + 60, TD_DOWN + 140);
        if (fade <= 0) return;
        // Marks: along the rear wheels' path wherever it slid, up to now.
        VertexConsumer v = c.buffers().getBuffer(FilmFx.SOFT);
        Matrix4f m = c.pose().last().pose();
        for (float s0 = TakedownPath.IN - 2; s0 < Math.min(cc, TakedownPath.IN + TakedownPath.ARC + 3); s0 += .5f) {
            float k = TakedownPath.slide(s0);
            if (k < .15f) continue;
            for (int side = -1; side <= 1; side += 2) {
                Vec3 a = rearWheel(run, s0, side), b = rearWheel(run, s0 + .5f, side);
                flat(v, m, a, b, y, .28, 0x0d0d0e, .55f * k * fade);
            }
        }
        // Smoke: puffs left behind at every tick of the slide, rising and spreading as they age.
        for (int k = 0; k < 40; k++) {
            float s0 = (float) Math.floor(cc) - k;
            float slide = TakedownPath.slide(s0);
            if (slide < .2f) continue;
            float age = cc - s0;
            for (int side = -1; side <= 1; side += 2) {
                Vec3 w = rearWheel(run, s0, side);
                Vec3 at = new Vec3(w.x, y + .25 + age * .05, w.z).add((TakedownPath.hash(s0 * 3 + side) - .5) * age * .05, 0, (TakedownPath.hash(s0 * 7 + side) - .5) * age * .05);
                FilmFx.puff(c, at, .7 + age * .09, 0xa4a4a6, .42f * slide * Math.max(0, 1 - age / 40));
            }
        }
    }
    /** A flat strip on the ground from a to b. */
    private static void flat(VertexConsumer v, Matrix4f m, Vec3 a, Vec3 b, float y, double width, int rgb, float alpha) {
        Vec3 d = b.subtract(a);
        Vec3 s = new Vec3(-d.z, 0, d.x);
        if (s.lengthSqr() < 1e-8) return;
        s = s.normalize().scale(width / 2);
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, bl = (rgb & 255) / 255f, al = Mth.clamp(alpha, 0, 1);
        v.vertex(m, (float) (a.x - s.x), y, (float) (a.z - s.z)).color(r, g, bl, al).endVertex();
        v.vertex(m, (float) (b.x - s.x), y, (float) (b.z - s.z)).color(r, g, bl, al).endVertex();
        v.vertex(m, (float) (b.x + s.x), y, (float) (b.z + s.z)).color(r, g, bl, al).endVertex();
        v.vertex(m, (float) (a.x + s.x), y, (float) (a.z + s.z)).color(r, g, bl, al).endVertex();
    }
    /** The car's lights in the air: the headlights' glare and, boosting away, the blue afterburner and its trail. */
    private static void car(FilmContext c, TakedownPath.Run run, float cc) {
        Vec3 at = run.pos(cc), nose = run.nose(cc), left = new Vec3(nose.z, 0, -nose.x);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 lamp = at.add(left.scale(s * .62)).add(0, .72, 0).add(nose.scale(3.1));
            FilmFx.glow(c, lamp, .55, 0xeef6ff, .55f);
            FilmFx.glow(c, lamp.add(nose.scale(.6)), 1.8, 0xcfe4ff, .12f);
        }
        float boost = TakedownPath.boost(cc);
        if (boost <= .01f) return;
        Vec3 jet = at.add(0, .92, 0).subtract(nose.scale(3.05));
        float flick = .8f + .2f * Mth.sin(cc * 3.1f);
        FilmFx.streak(c, jet.subtract(nose.scale(2.6 * boost * flick)), jet, .32, BOOST, 0, .85f * boost, true);
        FilmFx.streak(c, jet.subtract(nose.scale(1.4 * boost * flick)), jet, .14, BOOST_CORE, 0, boost, true);
        FilmFx.glow(c, jet, 1.1, BOOST, .7f * boost);
        // The exhaust trail: where the jet was over the last ticks, fading.
        for (int k = 1; k < 12; k++) {
            float s0 = cc - k * .7f;
            if (TakedownPath.boost(s0) <= .05f) break;
            Vec3 p = run.pos(s0).add(0, .92, 0).subtract(run.nose(s0).scale(3.3));
            FilmFx.glow(c, p, .7 + k * .08, BOOST, .3f * boost * (1 - k / 12f));
        }
        // Speed lines along its flanks.
        for (int s = -1; s <= 1; s += 2) {
            Vec3 edge = at.add(left.scale(s * 1.3)).add(0, 1.0, 0);
            FilmFx.streak(c, edge.subtract(nose.scale(5 * boost)), edge.subtract(nose.scale(1.5)), .03, 0xdde8ff, 0, .25f * boost, true);
        }
    }
    /** The rounds: muzzle flash, a short fast tracer, and where it lands a flash, sparks and a puff of smoke. */
    private static void rounds(FilmContext c, TakedownPath.Run run, Rec r, Vec3 feet, float cc, float h) {
        if (cc < TakedownPath.FIRE_FROM - 1 || cc > TakedownPath.FIRE_TO + 8) return;
        for (int round = TakedownPath.FIRE_FROM; round < TakedownPath.FIRE_TO; round++) {
            float age = cc - round;
            if (age < 0 || age > TakedownPath.ROUND_TICKS + 5) continue;
            int gun = TakedownPath.gun(round);
            Vec3 mz = muzzle(run, round, gun);
            Vec3 hit = onBody(r, feet, h, .7 + .9 * TakedownPath.hash(round * 1.7), .22, (TakedownPath.hash(round * 2.9) - .5) * .6);
            Vec3 way = hit.subtract(mz);
            double len = way.length();
            Vec3 dir = len < 1e-4 ? new Vec3(0, 0, 1) : way.scale(1 / len);
            if (age < 1.2f) {
                float k = 1 - age / 1.2f;
                FilmFx.glow(c, mz, .45 * k + .1, 0xfff4c8, k);
                FilmFx.glow(c, mz, 1.0, 0xffa040, .45f * k);
                FilmFx.streak(c, mz, mz.add(dir.scale(.7 * k)), .12, 0xfff0b0, .9f * k, 0, true);
            }
            if (age < TakedownPath.ROUND_TICKS) {
                double u = age / TakedownPath.ROUND_TICKS;
                Vec3 head = mz.add(way.scale(u)), tail = mz.add(way.scale(Math.max(0, u - .9 / Math.max(1, len))));
                FilmFx.streak(c, tail, head, .035, 0xffe8a0, 0, 1, true);
                FilmFx.streak(c, tail, head, .09, 0xff8a30, 0, .35f, true);
            } else {
                float d = age - TakedownPath.ROUND_TICKS, k = 1 - d / 5;
                if (d < 1.5f) FilmFx.glow(c, hit, .4 * (1 - d / 1.5f) + .1, 0xffffff, 1 - d / 1.5f);
                FilmFx.glow(c, hit, .7, 0xffb050, .4f * k);
                for (int i = 0; i < 5; i++) {
                    Vec3 sv = dir.scale(-.6).add((TakedownPath.hash(round * 4 + i) - .5) * 1.4, TakedownPath.hash(round * 6 + i) * .9, (TakedownPath.hash(round * 8 + i) - .5) * 1.4);
                    Vec3 head = hit.add(sv.scale(d * .35)).add(0, -.02 * d * d, 0), tail = hit.add(sv.scale(Math.max(0, d - .8) * .35));
                    FilmFx.streak(c, tail, head, .018, SPARK, 0, .95f * k, true);
                }
                FilmFx.puff(c, hit.add(dir.scale(-.15)).add(0, d * .04, 0), .2 + d * .07, 0x6a6a6a, .35f * k);
            }
        }
    }
    /** Smoke curling up off where the rounds struck (shoulders, back, chest, arms) for a few seconds; nothing burns. */
    private static void scorched(FilmContext c, Rec r, Vec3 feet, float cc, float h) {
        float since = cc - TakedownPath.FIRE_FROM - TakedownPath.ROUND_TICKS;
        if (since < 0 || since > 110) return;
        float fade = 1 - Mth.clamp((since - 70) / 40f, 0, 1);
        for (int i = 0; i < 9; i++) {
            double up = .8 + .75 * TakedownPath.hash(i * 3.3), fwd = (TakedownPath.hash(i * 1.9) - .5) * .5, left = (TakedownPath.hash(i * 7.1) - .5) * .7;
            Vec3 base = onBody(r, feet, h, up, fwd, left);
            for (int j = 0; j < 4; j++) {
                float age = (since * .9f + j * 6 + i * 2.3f) % 24;
                if (age > since) continue;
                Vec3 at = base.add((TakedownPath.hash(i + j * 13) - .5) * .1, age * .045, (TakedownPath.hash(i * 5 + j) - .5) * .1);
                FilmFx.puff(c, at, .1 + age * .018, SMOKE, .22f * (1 - age / 24) * fade);
            }
        }
    }
    /** Dust thrown up where they hit the ground face first, a low ring of it spreading. */
    private static void impact(FilmContext c, Rec r, Vec3 feet, float h) {
        float d = h - TD_IMPACT;
        if (d < 0 || d > 30) return;
        Vec3 head = feet.add(front(r).scale(1.1)).add(0, .05, 0);
        float u = d / 30;
        FilmFx.ring(c, head, .3 + 2.6 * Math.sqrt(u), .3 * (1 - u), 0xa89a88, .5f * (1 - u), false);
        for (int i = 0; i < 10; i++) {
            double a = i / 10.0 * Math.PI * 2;
            double rr = .4 + 1.6 * (1 - Math.exp(-d / 5));
            Vec3 at = head.add(Math.cos(a) * rr, .2 + d * .02, Math.sin(a) * rr);
            FilmFx.puff(c, at, .45 + d * .03, 0x9a8f80, .45f * (1 - u));
        }
        if (d < 3) FilmFx.glow(c, head, 1.2 * (1 - d / 3), 0xfff4e0, .25f * (1 - d / 3));
    }

    // ------------------------------------------------------------------ the held one's body
    private static Rec holding(int entity) {
        for (Rec r : RECS.values()) if (r.target == entity && r.feet != null && now() - r.hitAt < TD_DOWN + 12) return r;
        return null;
    }
    /** Turned to face away from him, struggling, jolted by the rounds, then driven face first into the ground. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pre(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity en = e.getEntity();
        Rec r = holding(en.getId());
        if (r == null) return;
        float h = now() - r.hitAt;
        if (h < 0) return;
        Vec3 f = front(r);
        float body = Mth.rotLerp(e.getPartialTick(), en.yBodyRotO, en.yBodyRot);
        float fyaw = (float) Math.toDegrees(Math.atan2(-f.x, f.z));
        float turn = TakedownPath.k(h, 2, 10) * (1 - TakedownPath.k(h, TD_DOWN + 4, TD_DOWN + 12));
        float yaw = (body + Mth.wrapDegrees(fyaw - body) * turn) * Mth.DEG_TO_RAD;
        Vec3 face = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        float lie = lie(h);
        // Struggling against his arms, the rounds jolting them right and left, a flinch back as each lands.
        float struggle = h > TD_LOCK && h < TD_FALL ? .1f * Mth.sin(h * .55f) + .05f * Mth.sin(h * 1.3f + 1) : 0;
        float jolt = h < TD_FALL ? TakedownMotion.jolt(h) : 0;
        float flinch = h < TD_FLIP ? -.12f * Mth.sin(Mth.PI * h / TD_FLIP) : 0;
        PoseStack p = e.getPoseStack();
        p.pushPose();
        PUSHED.add(en.getId());
        p.translate(0, .2 * lie, 0);
        p.mulPose(Axis.YP.rotation((float) Math.atan2(-face.x, -face.z)));
        p.mulPose(Axis.XP.rotation(-lie * Mth.HALF_PI + flinch + Math.abs(jolt) * .25f));
        p.mulPose(Axis.ZP.rotation(struggle + jolt));
        p.mulPose(Axis.YP.rotationDegrees(-(180 - body)));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void post(RenderLivingEvent.Post<?, ?> e) {
        if (PUSHED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ grit, shakes, his camera
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { RECS.clear(); return; }
        float t = mc.level.getGameTime();
        RECS.entrySet().removeIf(en -> {
            Rec r = en.getValue();
            float last = Math.max(r.dashAt, Math.max(r.hitAt, r.missAt));
            return t - last > TD_DOWN + 160;
        });
        for (var en : RECS.entrySet()) {
            Rec r = en.getValue();
            if (r.feet == null || r.hitAt < -999) continue;
            float h = t - r.hitAt, cc = carClock(en.getKey(), r, 0);
            TakedownPath.Run run = run(r);
            // Grit and dust thrown up by the back wheels through the slide.
            if (TakedownPath.slide(cc) > .3f) for (int side = -1; side <= 1; side += 2) {
                Vec3 w = rearWheel(run, cc, side);
                BlockState ground = mc.level.getBlockState(BlockPos.containing(w.x, r.feet.y - .3, w.z));
                if (ground.isAir()) continue;
                var rnd = mc.level.random;
                for (int i = 0; i < 3; i++)
                    mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), w.x + (rnd.nextDouble() - .5), r.feet.y + .1, w.z + (rnd.nextDouble() - .5),
                            (rnd.nextDouble() - .5) * .3, .15 + rnd.nextDouble() * .2, (rnd.nextDouble() - .5) * .3);
            }
            // The rounds landing shake the ones involved; the slam shakes everyone near.
            boolean mine = en.getKey() == me() || r.target == me();
            int c = (int) cc;
            if (mine && TakedownPath.fires(c - (int) TakedownPath.ROUND_TICKS)) BatmanClient.shake(.035f);
            if ((int) h == TD_IMPACT && !r.dazedDust) {
                r.dazedDust = true;
                if (mc.player != null && mc.player.position().distanceTo(r.feet) < 12) BatmanClient.shake(mine ? .3f : .12f);
                BlockState ground = mc.level.getBlockState(BlockPos.containing(r.feet.x, r.feet.y - .3, r.feet.z));
                if (!ground.isAir()) for (int i = 0; i < 18; i++) {
                    Vec3 at = r.feet.add(front(r).scale(1.1));
                    mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x + (mc.level.random.nextDouble() - .5) * 1.2, r.feet.y + .1, at.z + (mc.level.random.nextDouble() - .5) * 1.2, 0, .2, 0);
                }
            }
        }
    }
    /** His own camera: dips with the flip, glances toward the car as it slides round, a small push with the takedown. */
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Rec r = RECS.get(mc.player.getId());
        if (r == null || r.feet == null || r.hitAt < -999) return;
        float h = now() - r.hitAt;
        if (h < 0 || h > TD_HOLD_TICKS + 6) return;
        float pitch = 0, yaw = 0;
        if (h < TD_FLIP + 2) pitch += 14 * Mth.sin(Mth.PI * Mth.clamp(h / (TD_FLIP + 2), 0, 1));
        float cc = carClock(mc.player.getId(), r, (float) e.getPartialTick());
        float look = Math.min(TakedownPath.k(cc, TakedownPath.IN - 6, TakedownPath.IN), 1 - TakedownPath.k(cc, TakedownPath.IN + TakedownPath.ARC, TakedownPath.IN + TakedownPath.ARC + 8));
        if (look > 0) {
            Vec3 car = run(r).pos(cc), eye = mc.gameRenderer.getMainCamera().getPosition();
            float to = (float) Math.toDegrees(Math.atan2(-(car.x - eye.x), car.z - eye.z));
            yaw += Mth.clamp(Mth.wrapDegrees(to - (float) e.getYaw()) * .3f, -14, 14) * look;
        }
        float push = Math.max(0, 1 - Math.abs(h - TD_IMPACT) / 4);
        pitch += 4 * push;
        if (pitch != 0 || yaw != 0) { e.setPitch(e.getPitch() + pitch); e.setYaw(e.getYaw() + yaw); }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { RECS.clear(); PUSHED.clear(); }

    /** For his own client's steering: the flip's start, their feet, the dash's way, their height. */
    static Vec3 flipAt(Rec r, float t) {
        LivingEntity target = target(r);
        double height = target == null ? 1.8 : target.getBbHeight();
        return TakedownPath.flip(r.from, r.feet, r.dir, height, t);
    }
    /** The way he faces at hold time t (Minecraft yaw, degrees): along the dash, turning round to face back over the flip. */
    static float faceYaw(Rec r, float t) {
        float along = (float) Math.toDegrees(Math.atan2(-r.dir.x, r.dir.z));
        return along + 180 * TakedownPath.twist(t);
    }
}
