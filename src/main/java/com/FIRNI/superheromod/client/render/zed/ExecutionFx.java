package com.FIRNI.superheromod.client.render.zed;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmCast;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.film.FilmSessionClient;
import com.FIRNI.superheromod.heroes.zed.ZedExecutionSession;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

import static com.FIRNI.superheromod.heroes.zed.ZedAction.*;

/**
 * SHADOW EXECUTION in the world, for everyone near it (the two in it watch it as a film): Zed and his
 * victim drawn where ExecutionPath puts them (their real bodies stay held where they stood, unseen),
 * his shadow torn off him as he moves and kept moving the way it was going, his eyes streaking, the
 * sparks where his blade meets them, the shadow circling and thickening round the victim, the pool on
 * the ground, the soldiers pulling themselves out of it, the red light inside the storm, the shadow
 * sinking back into the ground, and Zed reforming. Everything reads the same film clock.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ExecutionFx {
    static final String ID = ZedExecutionSession.ID;
    static final int RED = 0xff2a3c, DARK = 0x07050a, HOT = 0xfff4dc, WARM = 0xffc060, CRIMSON = 0xc0101a;

    /** One running execution: its stage (where it stands, which way), its path and its victim. */
    static final class View {
        final int zed; int target; final ExecutionPath path; final Vec3 anchor, forward, right; final float yaw;
        FilmCast cast; float lastT = -1; long seen;
        View(int zed, int target, ExecutionPath path, Vec3 anchor, float yaw) {
            this.zed = zed; this.target = target; this.path = path; this.anchor = anchor; this.yaw = yaw;
            forward = Vec3.directionFromRotation(0, yaw);
            right = forward.cross(new Vec3(0, 1, 0)).normalize();
        }
        Vec3 world(Vec3 stage) { return anchor.add(right.scale(stage.x)).add(0, stage.y, 0).add(forward.scale(stage.z)); }
        /** Victim frame to world. */
        Vec3 at(Vec3 local) { return world(path.stage(local)); }
        Vec3 dir(Vec3 local) { return right.scale(local.x).add(0, local.y, 0).add(forward.scale(local.z)); }
    }
    private static final Map<Integer, View> VIEWS = new HashMap<>();
    private static boolean drawingCast;

    private ExecutionFx() {}

    /** The execution this Zed is in, or null. */
    static View view(Player zed) {
        var s = FilmSessionClient.get(zed.getId());
        var level = Minecraft.getInstance().level;
        if (s == null || level == null || !ID.equals(s.film)) return null;
        View known = VIEWS.get(zed.getId());
        if (known != null && known.anchor.distanceToSqr(s.anchor) < 1e-4) {
            if (s.target >= 0) known.target = s.target;
            known.seen = level.getGameTime();
            return known;
        }
        Entity target = s.target >= 0 ? level.getEntity(s.target) : null;
        double d = 3, dy = 0;
        if (target != null) {
            Vec3 to = target.position().subtract(s.anchor);
            d = Mth.clamp(Math.sqrt(to.x * to.x + to.z * to.z), 1.2, 14);
            dy = Mth.clamp(to.y, -3, 3);
        }
        View view = new View(zed.getId(), s.target, new ExecutionPath(d, dy), s.anchor, s.yaw);
        view.seen = level.getGameTime();
        VIEWS.put(zed.getId(), view);
        return view;
    }
    /** The path the execution of this Zed is laid out on (for its film's camera), or null. */
    public static ExecutionPath pathFor(int zedId) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(zedId) instanceof Player p) {
            View v = view(p);
            if (v != null) return v.path;
        }
        return null;
    }
    private static float time(View v, float partial) {
        var s = FilmSessionClient.get(v.zed);
        return s == null || !ID.equals(s.film) ? -1 : FilmSessionClient.time(s, partial);
    }
    /** Is this body playing in an execution right now (so it is drawn by the film, not as itself)? */
    public static boolean performing(int id) {
        for (View v : VIEWS.values()) if (v.zed == id || v.target == id) return true;
        return false;
    }
    public static boolean any() { return !VIEWS.isEmpty(); }
    /** How strongly light shines through the smoke this frame (the red flash). */
    public static float glowThrough() {
        float f = 0;
        var mc = Minecraft.getInstance();
        for (View v : VIEWS.values()) f = Math.max(f, ExecutionPath.flash(time(v, mc.getFrameTime())));
        return f;
    }

    // ------------------------------------------------------------------ every tick: the shadow, the dust
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { VIEWS.clear(); ShadowSmoke.timeScale = 1; return; }
        if (mc.isPaused()) return;
        for (Player p : mc.level.players()) view(p);
        long now = mc.level.getGameTime();
        VIEWS.values().removeIf(v -> now - v.seen > 2 || FilmSessionClient.get(v.zed) == null);
        float scale = 1;
        Random r = ShadowSmoke.random();
        for (View v : VIEWS.values()) {
            float t = time(v, 0);
            if (t < 0) continue;
            float before = v.lastT < 0 ? t - 1 : v.lastT;
            v.lastT = t;
            // The held breath: everything slows, the smoke still curling.
            if (t >= ULT_PAUSE && t < ULT_ATTACK) scale = Math.min(scale, .3f + .7f * ExecutionPath.ease((t - ULT_ATTACK + 4) / 4));
            fight(v, t, before, r);
            dark(v, t, before, r);
            dust(v, t, before, r);
        }
        ShadowSmoke.timeScale = scale;
    }
    private static boolean crossed(float before, float now, float beat) { return before < beat && now >= beat; }

    /** Zed's shadow during the fight: torn off him as he moves, carrying on the way he was going. */
    private static void fight(View v, float t, float before, Random r) {
        ExecutionPath path = v.path;
        if (t > ULT_EYES_OUT + 2) return;
        Vec3 feet = v.at(path.zed(t)), vel = v.dir(path.zedVelocity(t));
        double speed = vel.length();
        boolean smoke = path.step && t > 2.5f && t < 7.5f;
        boolean fading = t > ULT_FADE;
        int n = 1 + (int) Math.min(7, speed * 9) + (smoke ? 6 : 0) + (fading ? 9 : 0);
        if (path.zedSolid(t) < 0) n = 0;
        for (int i = 0; i < n; i++) {
            Vec3 at = feet.add(r.nextGaussian() * .22, .15 + r.nextDouble() * 1.7, r.nextGaussian() * .22);
            float size = (.16f + r.nextFloat() * .2f) * (float) (1 + Math.min(1.5, speed * 1.5)) * (fading ? 1.4f : 1);
            int shape = speed > .25 && r.nextFloat() < .55f ? ShadowSmoke.TENDRIL : r.nextFloat() < .2f ? ShadowSmoke.SHEET : ShadowSmoke.BLOB;
            ShadowSmoke.Wisp w = ShadowSmoke.add(at, vel.scale(.55).add(r.nextGaussian() * .015, r.nextGaussian() * .01, r.nextGaussian() * .015),
                    size, 10 + r.nextInt(15), fading ? .75f : .55f + r.nextFloat() * .2f, shape);
            w.turbulence = .008f;
        }
        // The two places he comes apart at, and where he is put back together.
        if (path.step && crossed(before, t, .5f)) ShadowSmoke.burst(v.at(path.start).add(0, 1, 0), 26, 1.0, Vec3.ZERO, .07, .35f, 18, .75f);
        if (path.step && crossed(before, t, 9.5f)) ShadowSmoke.burst(v.at(path.zed(9.5f)).add(0, 1, 0), 24, 1.0, Vec3.ZERO, .08, .32f, 16, .7f);
        if (crossed(before, t, ULT_FADE)) ShadowSmoke.burst(feet.add(0, 1, 0), 30, 1.1, vel.scale(.5), .05, .4f, 22, .8f);
        if (crossed(before, t, ULT_GONE)) ShadowSmoke.burst(feet.add(0, 1, 0), 18, .9, vel.scale(.4), .04, .45f, 24, .7f);
    }

    /** The darkness: circling streams, the core thickening round the victim, the soldiers' smoke, the collapse, the reform. */
    private static void dark(View v, float t, float before, Random r) {
        ExecutionPath path = v.path;
        Vec3 ground = v.at(path.victimAt(Math.min(t, 96)).multiply(1, 0, 1));
        Vec3 centre = ground.add(0, 1, 0);
        double floor = v.at(Vec3.ZERO).y;
        if (t >= ULT_CIRCLE && t < ULT_FLASH) {
            float rate = 1 + 3 * ExecutionPath.ease((t - 90) / 100) + (t > ULT_ATTACK ? 2 : 0);
            for (int i = 0; i < ExecutionPath.STREAMS; i++) {
                Vec3 head = v.at(path.stream(i, t)), vel = v.dir(path.stream(i, t + .5f).subtract(path.stream(i, t - .5f)));
                int n = (int) rate + (r.nextFloat() < rate % 1 ? 1 : 0);
                for (int k = 0; k < n; k++) {
                    float pick = r.nextFloat();
                    ShadowSmoke.Wisp w = ShadowSmoke.add(head.add(r.nextGaussian() * .15, r.nextGaussian() * .15, r.nextGaussian() * .15),
                            vel.scale(.75).add(r.nextGaussian() * .01, r.nextGaussian() * .01, r.nextGaussian() * .01),
                            .25f + r.nextFloat() * .3f, 14 + r.nextInt(16), .45f + r.nextFloat() * .25f + (t > ULT_ATTACK ? .1f : 0),
                            pick < .5f ? ShadowSmoke.TENDRIL : pick < .7f ? ShadowSmoke.SHEET : ShadowSmoke.BLOB);
                    w.centre = centre; w.pull = t > ULT_ATTACK ? .01f : .002f; w.swirl = (i % 3 == 2 ? -1 : 1) * .006f; w.hold = head.y; w.turbulence = .01f;
                }
            }
            // Twice, a sheet of shadow sweeps right across the lens.
            for (float wipe : new float[]{118, 166}) {
                if (Math.abs(t - wipe) >= 7) continue;
                var view = path.camera(t);
                Vec3 cam = v.world(view.pos()), aim = v.world(view.aim());
                Vec3 side = aim.subtract(cam).cross(new Vec3(0, 1, 0)).normalize();
                float k = (t - wipe + 7) / 14f;
                Vec3 at = cam.lerp(aim, .32).add(side.scale(-2.6 + 5.2 * k)).add(0, -.4 + .8 * k, 0);
                for (int i = 0; i < 9; i++)
                    ShadowSmoke.add(at.add(r.nextGaussian() * .3, r.nextGaussian() * .3, r.nextGaussian() * .3), side.scale(.37).add(r.nextGaussian() * .02, 0, r.nextGaussian() * .02),
                            .5f + r.nextFloat() * .3f, 10 + r.nextInt(6), .8f, i % 3 == 0 ? ShadowSmoke.SHEET : ShadowSmoke.BLOB);
            }
        }
        // The core thickening round them, stage by stage, until they are gone inside it.
        if (t >= 120 && t < 240) {
            int n = (int) (1 + 7 * ExecutionPath.ease((t - 120) / 80)) + (t > ULT_STORM ? 6 : 0);
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = .2 + .75 * r.nextDouble();
                Vec3 at = ground.add(Math.cos(a) * rad, .1 + r.nextDouble() * 1.9, Math.sin(a) * rad);
                Vec3 swirl = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(.03 + (t > ULT_STORM ? .06 : 0));
                ShadowSmoke.Wisp w = ShadowSmoke.add(at, swirl, .28f + r.nextFloat() * .22f, 12 + r.nextInt(10),
                        .4f + .3f * ExecutionPath.ease((t - 150) / 50) + r.nextFloat() * .1f, r.nextFloat() < .35f ? ShadowSmoke.TENDRIL : ShadowSmoke.BLOB);
                w.centre = centre; w.pull = .004f; w.swirl = .012f; w.turbulence = .012f;
            }
        }
        // The soldiers: smoke dripping off them as they form; streaming down into the ground as they go.
        float[] parts = new float[4];
        for (int i = 0; i < ExecutionPath.SOLDIERS; i++) {
            if (!path.soldierShown(i, t)) continue;
            Vec3 feet = v.at(path.soldier(i, t)).add(0, path.soldierSink(i, t), 0);
            path.soldierParts(i, t, parts);
            boolean forming = t < path.soldierFormStart(i) + 22, going = t > ULT_COLLAPSE;
            int n = going ? 4 : forming ? 3 : 1;
            for (int k = 0; k < n; k++) {
                Vec3 at = feet.add(r.nextGaussian() * .2, .2 + r.nextDouble() * 1.6, r.nextGaussian() * .2);
                ShadowSmoke.Wisp w = ShadowSmoke.add(at, new Vec3(r.nextGaussian() * .01, going ? -.06 : -.015, r.nextGaussian() * .01),
                        .16f + r.nextFloat() * .16f, 12 + r.nextInt(10), .5f, r.nextFloat() < .5f ? ShadowSmoke.TENDRIL : ShadowSmoke.BLOB);
                if (going) { w.sink = true; w.ground = floor; }
            }
            if (crossed(before, t, path.soldierFormStart(i))) ShadowSmoke.burst(feet.add(0, .3, 0), 10, .8, new Vec3(0, .03, 0), .03, .35f, 16, .7f);
        }
        // After the flash the whole storm sinks back into the ground.
        if (crossed(before, t, ULT_COLLAPSE) || crossed(before, t, ULT_COLLAPSE + 4)) {
            boolean first = crossed(before, t, ULT_COLLAPSE);
            for (ShadowSmoke.Wisp w : ShadowSmoke.all()) {
                if (w.sink || w.pos().distanceTo(centre) > 8) continue;
                if (first && r.nextFloat() < .4f) continue;
                w.sink = true; w.ground = floor; w.centre = null; w.life += 20; w.vx *= .3; w.vz *= .3;
            }
        }
        // The last wisps crawl along the ground away from the body and are taken into it.
        if (t > 268 && t < 300 && r.nextFloat() < .7f) {
            Vec3 body = v.at(path.victimAt(t)).add(v.dir(new Vec3(0, 0, .9)));
            double a = r.nextDouble() * Math.PI * 2;
            ShadowSmoke.Wisp w = ShadowSmoke.add(body.add(Math.cos(a) * .4, .12, Math.sin(a) * .4), new Vec3(Math.cos(a) * .025, 0, Math.sin(a) * .025),
                    .14f, 26, .45f, ShadowSmoke.TENDRIL);
            w.sink = true; w.ground = floor; w.turbulence = .002f;
        }
        // Zed reforming: the smoke draws in on one place, his eyes in it first.
        Vec3 spot = v.at(path.reveal);
        if (t > 266 && t < ULT_SOLID - 2) {
            for (int i = 0; i < 4; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = 1.4 + r.nextDouble() * .8;
                Vec3 at = spot.add(Math.cos(a) * rad, .2 + r.nextDouble() * 1.8, Math.sin(a) * rad);
                Vec3 in = spot.add(0, 1, 0).subtract(at).normalize().scale(.07 + r.nextDouble() * .04);
                ShadowSmoke.add(at, in, .22f + r.nextFloat() * .2f, 12 + r.nextInt(6), .6f, ShadowSmoke.TENDRIL);
            }
        }
        if (t > ULT_REFORM && t < ULT_SOLID + 10 && r.nextFloat() < .8f)
            ShadowSmoke.add(spot.add(r.nextGaussian() * .25, .2 + r.nextDouble() * 1.6, r.nextGaussian() * .25), new Vec3(r.nextGaussian() * .02, .015, r.nextGaussian() * .02), .2f, 16, .55f, ShadowSmoke.BLOB);
        // The shadow peels off his armour.
        if (crossed(before, t, ULT_SOLID)) ShadowSmoke.burst(spot.add(0, 1, 0), 30, .8, new Vec3(0, .02, 0), .09, .3f, 20, .8f);
    }

    /** The ground answering: dust kicked up where he pushes off, lands, skids; a ring of it under the flash. */
    private static void dust(View v, float t, float before, Random r) {
        var level = Minecraft.getInstance().level;
        float[] beats = {ULT_BURST, ULT_LAND1, 42, ULT_LEAP2, ULT_LAND2, 58, ULT_DASH3, 66, 72};
        for (float beat : beats) {
            if (!crossed(before, t, beat)) continue;
            Vec3 feet = v.at(v.path.zed(beat).multiply(1, 0, 1));
            Vec3 push = v.dir(v.path.zedVelocity(beat)).multiply(1, 0, 1);
            kick(level, feet, push.lengthSqr() < 1e-6 ? Vec3.ZERO : push.normalize(), 14, r);
        }
        if (crossed(before, t, ULT_FLASH)) {
            Vec3 ground = v.at(v.path.victimAt(t).multiply(1, 0, 1));
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                kick(level, ground.add(out.scale(2.4)), out, 4, r);
            }
        }
    }
    private static void kick(net.minecraft.client.multiplayer.ClientLevel level, Vec3 feet, Vec3 push, int count, Random r) {
        BlockPos below = BlockPos.containing(feet.x, feet.y - .2, feet.z);
        var state = level.getBlockState(below);
        if (state.isAir()) state = level.getBlockState(below.below());
        if (state.isAir()) return;
        for (int i = 0; i < count; i++) {
            Vec3 d = push.scale(.15 + r.nextDouble() * .2).add(r.nextGaussian() * .08, .12 + r.nextDouble() * .18, r.nextGaussian() * .08);
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), feet.x + r.nextGaussian() * .3, feet.y + .1, feet.z + r.nextGaussian() * .3, d.x, d.y, d.z);
        }
    }

    // ------------------------------------------------------------------ drawing: under the smoke
    static void beforeSmoke(FilmContext c, float partial) {
        if (VIEWS.isEmpty()) return;
        for (View v : VIEWS.values()) {
            float t = time(v, partial);
            if (t < 0) continue;
            ExecutionPath path = v.path;
            Vec3 ground = v.at(path.victimAt(Math.min(t, 96)).multiply(1, 0, 1));
            // The pool of shadow, and the tendrils that tie it to the cloud.
            float pool = ExecutionPath.poolRadius(t);
            if (pool > .02f) ZedFx.pool(c, ground, pool, .88f, v.zed, c.time());
            float tendrils = ExecutionPath.window(t, 128, 145, 228, 242);
            if (tendrils > .01f) for (int k = 0; k < 5; k++) {
                double a = k * 1.2566 + t * .012;
                Vec3 base = ground.add(Math.cos(a) * pool * .82, .03, Math.sin(a) * pool * .82);
                Vec3 top = ground.add(Math.cos(a + .7) * 1.1, 1.7 + .7 * Math.sin(t * .05 + k), Math.sin(a + .7) * 1.1);
                Vec3 prev = base;
                for (int s = 1; s <= 8; s++) {
                    double u = s / 8.0;
                    Vec3 mid = base.lerp(top, u).add(Math.sin(t * .09 + k * 2 + u * 4) * .3 * Math.sin(Math.PI * u), 0, Math.cos(t * .08 + k + u * 3) * .3 * Math.sin(Math.PI * u));
                    FilmFx.streak(c, prev, mid, .26 * (1 - u * .85), DARK, .7f * tendrils, .7f * tendrils * (float) (1 - u * .6), false);
                    prev = mid;
                }
            }
            // His shadow on the ground under him while he is in the air.
            if (t < ULT_EYES_OUT && path.zedSolid(t) > .2f) {
                Vec3 feet = path.zed(t);
                double height = feet.y;
                Vec3 under = v.at(new Vec3(feet.x, 0, feet.z)).add(0, .02, 0);
                FilmFx.shadow(c, under, .55 + .15 * Math.min(2, height), .5f * (float) Math.max(0, 1 - height / 3.5));
            }
            // Red cuts flickering through the storm, deep inside it (the smoke will veil them).
            if (t > ULT_STORM - 2 && t < ULT_FLASH) {
                Vec3 heart = v.at(new Vec3(0, 1, 0).add(path.victimAt(t).multiply(1, 0, 1)));
                for (int j = (int) t - 2; j <= (int) t; j++) for (int n = 0; n < 3; n++) {
                    float seed = j * 7 + n * 13;
                    float age = t - j;
                    if (age < 0 || age > 3) continue;
                    Vec3 dir = new Vec3(ExecutionPath.hash(seed) - .5, ExecutionPath.hash(seed + 1) - .5, ExecutionPath.hash(seed + 2) - .5).normalize();
                    Vec3 at = heart.add(dir.scale(.5 + .8 * ExecutionPath.hash(seed + 3)));
                    Vec3 normal = new Vec3(ExecutionPath.hash(seed + 4) - .5, ExecutionPath.hash(seed + 5) - .5, ExecutionPath.hash(seed + 6) - .5);
                    double a0 = ExecutionPath.hash(seed + 7) * Math.PI * 2;
                    ZedFx.arc(c, FilmFx.ADD, at, normal, .55, a0, a0 + 1.7, .06, RED, .9f * (1 - age / 3));
                }
            }
            // The flash: red light bursting inside the storm. Only the thin smoke lets it through.
            float f = ExecutionPath.flash(t);
            if (f > .01f) {
                Vec3 heart = v.at(path.victimChest(t));
                FilmFx.glow(c, heart, 5, 0x8a0810, .85f * f);
                FilmFx.glow(c, heart, 2.5, 0xff2020, f);
                FilmFx.glow(c, heart, 1, 0xffd8c8, .8f * f);
                ShadowSmoke.light(heart, CRIMSON, 1.5f * f, 4.2f);
            }
            // The light of a cut reaches the smoke round it.
            for (int i = 0; i < ExecutionPath.CUTS.length; i++) {
                float age = t - ExecutionPath.CUTS[i];
                if (age >= 0 && age < 3) ShadowSmoke.light(v.at(path.cutPoint(i)), WARM, 1.2f * (1 - age / 3), 1.8f);
            }
        }
    }

    // ------------------------------------------------------------------ drawing: over the smoke
    static void afterSmoke(FilmContext c, float partial) {
        for (View v : VIEWS.values()) {
            float t = time(v, partial);
            if (t < 0) continue;
            for (int i = 0; i < ExecutionPath.CUTS.length; i++) {
                float age = t - ExecutionPath.CUTS[i];
                if (age >= 0 && age < 9) spark(c, v.at(v.path.cutPoint(i)), v.dir(v.path.cutDirection(i)).normalize(), age, i);
            }
        }
    }
    /**
     * Where the blade meets them: a white-hot point for an instant, a short warm light, yellow-white
     * streaks thrown the way the blade was going, and a few tiny fragments. Not a firework.
     */
    private static void spark(FilmContext c, Vec3 at, Vec3 dir, float age, int seed) {
        if (age < 2.5f) FilmFx.glow(c, at, .32 * (1 - age / 2.5f) + .06, HOT, 1 - age / 2.5f);
        if (age < 4) FilmFx.glow(c, at, 1.5, WARM, .5f * (1 - age / 4));
        Vec3 up = new Vec3(0, 1, 0), side = dir.cross(up);
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 lift = side.cross(dir).normalize();
        for (int i = 0; i < 16; i++) {
            float h = seed * 31 + i * 7;
            double spread = .75;
            Vec3 d = dir.scale(.75).add(side.scale((ExecutionPath.hash(h) - .5) * 2 * spread)).add(lift.scale((ExecutionPath.hash(h + 1) - .5) * 2 * spread * .7)).normalize();
            double speed = .25 + .4 * ExecutionPath.hash(h + 2);
            float life = 3 + 4 * ExecutionPath.hash(h + 3);
            if (age > life) continue;
            Vec3 head = at.add(d.scale(speed * age)).add(0, -.02 * age * age, 0);
            double len = (.12 + .45 * ExecutionPath.hash(h + 4)) * (1 - age / life * .5);
            float a = 1 - age / life;
            int col = FilmFx.ease(age / life) < .5f ? HOT : WARM;
            FilmFx.streak(c, head.subtract(d.scale(len)), head, .018, col, 0, a, true);
        }
        for (int i = 0; i < 8; i++) {
            float h = seed * 53 + i * 11;
            float life = 4 + 4 * ExecutionPath.hash(h + 5);
            if (age > life) continue;
            Vec3 d = new Vec3(ExecutionPath.hash(h) - .5, ExecutionPath.hash(h + 1) - .3, ExecutionPath.hash(h + 2) - .5).normalize().add(dir.scale(.5));
            Vec3 p = at.add(d.scale(.2 * age)).add(0, -.015 * age * age, 0);
            FilmFx.glow(c, p, .035, WARM, .9f * (1 - age / life));
        }
    }

    // ------------------------------------------------------------------ drawing: the bodies
    static boolean solid(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, float partial) {
        if (VIEWS.isEmpty()) return false;
        var mc = Minecraft.getInstance();
        float time = mc.level.getGameTime() + partial;
        for (View v : VIEWS.values()) {
            float t = time(v, partial);
            if (t < 0) continue;
            ExecutionPath path = v.path;
            // Zed.
            float solid = path.zedSolid(t);
            if (solid >= 0) {
                Vec3 feet = v.at(path.zed(t));
                float yaw = v.yaw + path.zedYaw(t);
                // His head keeps the victim in sight while the body turns (not when he is upside down).
                Vec3 head = feet.add(0, 1.6, 0), target = v.at(path.victimChest(t));
                float want = (float) Math.toDegrees(Math.atan2(-(target.x - head.x), target.z - head.z));
                float track = Math.max(0, (float) Math.cos(path.zedPitch(t))) * .65f * (t < ULT_FADE + 6 ? 1 : 0);
                float headYaw = (float) Math.toRadians(Mth.clamp(Mth.wrapDegrees(want - yaw), -70, 70)) * track;
                float headPitch = (float) Math.atan2(head.y - target.y, Math.max(.5, Math.hypot(target.x - head.x, target.z - head.z))) * track;
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(feet.add(0, 1, 0)));
                float lit = path.zedLight(t);
                if (lit < 1) light = LightTexture.pack((int) (LightTexture.block(light) * lit), (int) (LightTexture.sky(light) * lit));
                drawZed(pose, buffers, cam, feet, yaw, path.zedPitch(t), path.zedRoll(t), path.zedStretch(t), path.zedPose(t, time), headYaw, headPitch, time,
                        solid >= 1 ? ZedBody.NORMAL : ZedBody.SHADOW, solid >= 1 ? 1 : solid, light, path.zedEyes(t), -(v.zed * 16 + 5));
            }
            // The soldiers (the last of them is Zed, barely lit).
            float[] parts = new float[4];
            for (int i = 0; i < ExecutionPath.SOLDIERS; i++) {
                if (!path.soldierShown(i, t)) continue;
                path.soldierParts(i, t, parts);
                System.arraycopy(parts, 0, ZedBody.PART_ALPHA, 0, 4);
                Vec3 feet = v.at(path.soldier(i, t)).add(0, path.soldierSink(i, t), 0);
                boolean real = path.soldierSolid(i, t);
                int light = real ? LightTexture.pack(3, 2) : LevelRenderer.getLightColor(mc.level, BlockPos.containing(feet.add(0, 1, 0)));
                drawZed(pose, buffers, cam, feet, v.yaw + path.soldierYaw(i, t), 0, 0, 1, path.soldierPose(i, t, time), 0, 0, time,
                        real ? ZedBody.NORMAL : ZedBody.SHADOW, i == ExecutionPath.REAL ? .9f : .82f, light, path.soldierEyes(i, t), -(v.zed * 16 + 6 + i));
                ZedBody.resetParts();
            }
            // The victim, as a jointed puppet.
            victim(pose, buffers, cam, v, t, partial);
        }
        return true;
    }
    /** Zed (or a shadow of him) with his whole body turned and tipped about his middle. */
    private static void drawZed(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, Vec3 feet, float yaw, float pitch, float roll, float stretch,
                                ZedMotion.Pose p, float headYaw, float headPitch, float time, int mode, float alpha, int light, float eyes, int key) {
        pose.pushPose();
        pose.translate(feet.x - cam.x, feet.y - cam.y + 1, feet.z - cam.z);
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.mulPose(Axis.XP.rotation(-pitch));
        pose.mulPose(Axis.ZP.rotation(roll));
        if (stretch != 1) pose.scale(1, 1 + (stretch - 1) * .25f, stretch);
        pose.translate(0, -1, 0);
        pose.scale(-.9375f, -.9375f, .9375f);
        pose.translate(0, -1.501, 0);
        ZedBody.capture = true; ZedBody.eyeRight = ZedBody.eyeLeft = null;
        ZedBody.eyes = eyes;
        try {
            ZedBody.draw(pose, buffers, light, p, 0, 0, headYaw, headPitch, time, mode, alpha);
        } finally {
            ZedBody.capture = false; ZedBody.eyes = 1;
        }
        if (ZedBody.eyeRight != null && eyes > .01f) ZedEyes.record(key, ZedBody.eyeRight, ZedBody.eyeLeft, time, eyes);
        pose.popPose();
    }
    /** The victim: lit by the world, darkened as the shadow closes, warmed by the sparks, reddened by the flash. */
    private static void victim(PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, View v, float t, float partial) {
        var mc = Minecraft.getInstance();
        Entity target = v.target >= 0 ? mc.level.getEntity(v.target) : null;
        if (target != null && (v.cast == null || v.cast.entity() != target)) v.cast = FilmCast.of(target);
        if (v.cast == null) return;
        ExecutionPath path = v.path;
        var p = path.victimPose(t);
        Vec3 feet = v.at(path.victimAt(t)).add(0, .14 * Math.max(0, p.bodyPitch) / 88f, 0);
        int tint = 0xe2e2e2;
        float dark = ExecutionPath.window(t, 96, 170, 250, 300);
        tint = ShadowSmoke.mix(tint, 0x5e5a66, dark * .8f);
        for (float cut : ExecutionPath.CUTS) if (t >= cut && t < cut + 3) tint = ShadowSmoke.mix(tint, 0xfff6e0, 1 - (t - cut) / 3);
        float f = ExecutionPath.flash(t);
        if (f > .01f) tint = ShadowSmoke.mix(tint, 0xff4040, f * .8f);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        var rotation = mc.gameRenderer.getMainCamera().rotation();
        var r = new Vector3f(1, 0, 0).rotate(rotation); var u = new Vector3f(0, 1, 0).rotate(rotation);
        FilmContext c = new FilmContext(pose, buffers, cam, new Vec3(r.x, r.y, r.z), new Vec3(u.x, u.y, u.z), t, t, partial);
        drawingCast = true;
        try {
            v.cast.draw(c, feet, v.yaw + 180 + path.victimYaw(t), p, 1, tint);
        } finally {
            drawingCast = false;
            pose.popPose();
        }
    }

    /** While it plays, the real bodies stay held where they stood, unseen; only the performers are drawn. */
    @SubscribeEvent public static void hide(RenderLivingEvent.Pre<?, ?> e) {
        if (drawingCast || VIEWS.isEmpty()) return;
        if (performing(e.getEntity().getId())) e.setCanceled(true);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { VIEWS.clear(); ShadowSmoke.timeScale = 1; }
}
