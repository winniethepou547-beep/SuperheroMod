package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;
import static com.FIRNI.superheromod.client.render.iceman.FrostShellMesh.*;

/**
 * The DEEP FREEZE, after the user's Mortal Kombat 1 Sub-Zero freeze reference: the body is not put in a block of ice, the
 * ice grows OVER it and keeps its shape (head, shoulders, arms, hands, torso, legs, feet still read), so it reads as
 * "the enemy, frozen".
 * <ul>
 * <li>The take (DEEP_SEIZE ticks): a cold burst where the cold arrives (the side it came from, {@code way}), frost there,
 *     mist rolling round the body; the ice spreads from that contact point across the body and climbs from the feet
 *     (every vertex of the shell has its own time: its distance from the contact point and from the ground); the body
 *     keeps moving, slower and slower ({@link #pose} lets the drawn limbs follow the real ones less and less, the body's
 *     turn too), and is locked mid-motion in whatever it was doing.</li>
 * <li>The shell: an uneven faceted sleeve round every part of the body's own pose (rings round each limb, each point its
 *     own thickness, ridges here and there), thick where the spec wants it (head sides, shoulders, the lower legs and
 *     feet), thinner and clearer on the chest and arms so the body shows through the milky ice; masses of crystals on
 *     the shoulders, clusters where the cold struck, small detailed crystals on the hands; frost on the ground at the
 *     feet with a few crystals where they are frozen to it (never a platform); last, small thin curved crystals pushed
 *     out on the side AWAY from the cold, each its own size and angle, never in a row.</li>
 * <li>The break: a tiny crack, branches, more cracks from other points that run into it (a crack network across the
 *     shell's own surface, its fronts sparking), then the shell separates into chunks of its own shape (big ones from the
 *     shoulders/torso/back, smaller from the limbs), which let go one after another as the cracks reach them (some
 *     hang on a moment longer), fly off the body and burst into chips where they land; frost dust and cold mist escape,
 *     the last fragments fall, the frost on the ground melts. Sounds follow it: small crack, deeper fracture, the break
 *     (server), shards landing, the frost's hiss.</li>
 * </ul>
 * Humanoid models are followed limb by limb (FrostBodies' rig, read off the model after it is drawn); any other body
 * gets FrostBodies' box layout. Drawn after the translucent blocks with Iceman's ice (IceMesh), never from inside (the
 * frozen player's own view is FrostScreen's).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class FrostShell {
    private FrostShell() {}

    private static final Map<Integer, Shell> SHELLS = new HashMap<>();

    static boolean encased(int id) { Shell s = SHELLS.get(id); return s != null && s.broke < 0; }
    static boolean any() { return !SHELLS.isEmpty(); }
    /** 0..1: how far the ice holds the body here (the frozen player's own input fades with it); 0 when not frozen. */
    static float seized(int id) { Shell s = SHELLS.get(id); return s == null || s.broke >= 0 ? 0 : s.seize(FrostFx.now()); }

    /** Frozen (since: ticks since it began, when we only see it now; way: the cold's way, or null = at their face). */
    static void freeze(int id, Vec3 feet, float width, float height, int total, float since, Vec3 way) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        boolean human = e instanceof LivingEntity le && FrostBodies.humanoid(le);
        if (way == null || way.lengthSqr() < 1e-4) {
            // At their face: the cold travelled the way opposite to where they look.
            float yaw = e instanceof LivingEntity le ? le.yBodyRot * Mth.DEG_TO_RAD : 0;
            way = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw));
        }
        Shell s = new Shell(id, human, feet, width, height, FrostFx.now() - since, Math.max(total, DEEP_GROW + 4), way.normalize());
        SHELLS.put(id, s);
        if (since > 1) return;
        // PHASE 1-3: the cold impact where it arrives, frost there, the cold cloud spreading round the body.
        Vec3 hit = contact(s).add(feet);
        Vec3 back = way.scale(-1);
        IceParticles.cryo(hit, back.add(0, .2, 0), .9f, .6f);
        int n = IceParticles.count(8, hit);
        for (int i = 0; i < n; i++)
            IceParticles.snow(hit.add(IceParticles.jitter(.12)), back.scale(.06 + .06 * IceParticles.rand()).add(IceParticles.jitter(.04)).add(0, .02, 0), .02f + .02f * IceParticles.rand(), 14 + (int) (IceParticles.rand() * 10));
        for (int i = 0; i < IceParticles.count(3, hit); i++)
            IceParticles.shard(hit.add(IceParticles.jitter(.08)), back.scale(.1).add(IceParticles.jitter(.06)).add(0, .06, 0), .05f + .04f * IceParticles.rand(), 20, IceParticles.rand() < .5f ? IceMesh.FRESH : IceMesh.MILKY);
        IceParticles.flash(hit, .35f, .35f, 4);
        play(hit, ModSounds.ICEMAN_FROST.get(), .8f, 1.15f);
        // The cloud rolls round the body over the next ticks, the cold sinking to the ground.
        for (int k = 0; k < 4; k++) {
            int kk = k;
            IceParticles.later(1 + k * 2, () -> {
                Shell sh = SHELLS.get(id);
                if (sh == null || sh.broke >= 0) return;
                Vec3 mid = sh.feet.add(0, sh.height * (.75 - .15 * kk), 0);
                int m = IceParticles.count(3, mid);
                for (int i = 0; i < m; i++) {
                    double a = IceParticles.rand() * Math.PI * 2;
                    Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                    IceParticles.mist(mid.add(out.scale(sh.width * .45)), out.scale(.025).add(0, -.004, 0), .26f + .06f * kk, .02f, .2f, 30);
                }
                if (kk == 2) IceParticles.coldMist(sh.feet.add(0, .05, 0), sh.width * 1.6f + .6f, .8f);
            });
        }
        IceParticles.later(5, () -> { Shell sh = SHELLS.get(id); if (sh != null && sh.broke < 0) play(sh.feet.add(0, sh.height * .5, 0), ModSounds.ICEMAN_CRYSTAL_TICKS.get(), .7f, 1.1f); });
        IceParticles.later(DEEP_SEIZE, () -> { Shell sh = SHELLS.get(id); if (sh != null && sh.broke < 0) play(sh.feet.add(0, sh.height * .5, 0), ModSounds.ICEMAN_GROW_RUMBLE.get(), .45f, 1.3f); });
    }
    /** The ice breaks (how 2: shattered by a blow coming the way `blow`, else it gives way by itself). */
    static void breakShell(int id, int how, Vec3 blow) {
        Shell s = SHELLS.get(id);
        if (s == null || s.broke >= 0) return;
        float now = FrostFx.now();
        s.broke = now;
        s.how = how;
        s.blow = blow == null ? Vec3.ZERO : blow;
        if (!s.built) { SHELLS.remove(id); IceParticles.breakApart(s.feet.add(0, s.height * .5, 0), new Vec3(0, 1, 0), s.height * .9f, s.height * .5f, Vec3.ZERO, 3, 6, IceMesh.GLACIER); return; }
        // A blow: the crack starts where it landed, now, and runs fast.
        if (s.cracks == null || how == 2) startCracks(s, now, how == 2);
        s.chunks = chunks(s, now);
        Vec3 mid = s.feet.add(0, s.height * .5, 0);
        // Frost dust and cold mist escaping as it opens.
        IceParticles.later(how == 2 ? 1 : 2, () -> {
            IceParticles.coldMist(s.feet.add(0, .1, 0), s.width * 2 + .8f, 1f);
            int n = IceParticles.count(10, mid);
            for (int i = 0; i < n; i++) {
                Vec3 out = IceParticles.jitter(1).multiply(1, .4, 1).normalize();
                IceParticles.mist(mid.add(out.scale(s.width * .4)).add(0, (IceParticles.rand() - .5) * s.height * .6, 0), out.scale(.05).add(0, .01, 0), .2f, .025f, .22f, 26);
                IceParticles.crystalDust(mid.add(IceParticles.jitter(s.width * .4)), out.scale(.1 + .1 * IceParticles.rand()), .02f, (IceParticles.rand() - .5f) * .6f, 18 + (int) (IceParticles.rand() * 14));
            }
            IceParticles.frostDust(mid, Vec3.ZERO, 3f);
        });
        if (how == 2) { play(mid, ModSounds.ICEMAN_CRACK.get(), .7f, 1.75f); IceParticles.later(2, () -> play(mid, ModSounds.ICEMAN_CRACK.get(), .9f, .8f)); }
        IceParticles.later(16, () -> play(mid, ModSounds.ICEMAN_FROST_HISS.get(), .5f, 1f));
    }

    // ------------------------------------------------------------------ the held pose (HumanoidModelMixin, after setupAnim)
    /**
     * Called for every humanoid model right after its own animation: while the ice takes hold the drawn limbs follow the
     * real ones less and less, then stop where they are (locked mid-motion); the head and the hat with them.
     */
    public static void pose(HumanoidModel<?> m, LivingEntity e) {
        Shell s = SHELLS.get(e.getId());
        if (s == null || s.broke >= 0) return;
        float now = FrostFx.now();
        ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        if (!s.heldSet) {
            for (int i = 0; i < 6; i++) { s.held[i * 3] = parts[i].xRot; s.held[i * 3 + 1] = parts[i].yRot; s.held[i * 3 + 2] = parts[i].zRot; }
            s.heldSet = true;
            s.lastPose = now;
        }
        float f = follow(s, now, true);
        for (int i = 0; i < 6; i++) {
            ModelPart p = parts[i];
            int o = i * 3;
            s.held[o] += (p.xRot - s.held[o]) * f;
            s.held[o + 1] += (p.yRot - s.held[o + 1]) * f;
            s.held[o + 2] += (p.zRot - s.held[o + 2]) * f;
            p.xRot = s.held[o]; p.yRot = s.held[o + 1]; p.zRot = s.held[o + 2];
        }
        m.hat.copyFrom(m.head);
    }
    /** How much of the way to the real pose the held one goes this frame (1 at first, nothing once locked). */
    private static float follow(Shell s, float now, boolean advance) {
        float k = s.seize(now);
        float dt = Mth.clamp(now - s.lastPose, 0, 2);
        if (advance) s.lastPose = now;
        if (k >= 1) return 0;
        float free = (1 - k) * (1 - k) * (1 - k);
        return Mth.clamp(free * (.25f + dt * 1.5f), 0, 1);
    }
    private static final Set<Integer> TURNED = new HashSet<>();
    /** The body's turn is held the same way (drawn turned back to the held facing). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void turnPre(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity en = e.getEntity();
        Shell s = SHELLS.get(en.getId());
        if (s == null || s.broke >= 0) return;
        float live = Mth.rotLerp(e.getPartialTick(), en.yBodyRotO, en.yBodyRot);
        if (Float.isNaN(s.heldYaw)) s.heldYaw = live;
        float k = s.seize(FrostFx.now());
        float free = (1 - k) * (1 - k) * (1 - k);
        s.heldYaw += Mth.wrapDegrees(live - s.heldYaw) * Mth.clamp(free * .5f, 0, 1);
        float turn = Mth.wrapDegrees(live - s.heldYaw);
        if (Math.abs(turn) < .01f) return;
        e.getPoseStack().pushPose();
        e.getPoseStack().mulPose(Axis.YP.rotationDegrees(turn));
        TURNED.add(en.getId());
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void turnPost(RenderLivingEvent.Post<?, ?> e) {
        if (TURNED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }
    /** The yaw the shell is built on (the held facing, degrees). */
    private static float yaw(Shell s, LivingEntity le, float partial) {
        return Float.isNaN(s.heldYaw) ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : s.heldYaw;
    }

    /** Builds this frame's shell from the body's pose (its limbs as drawn, the held facing). */
    private static void build(Shell s, LivingEntity le, float partial, float now) {
        FrostBodies.parts(le, s.human, yaw(s, le, partial), le.getBbWidth(), le.getBbHeight());
        FrostShellMesh.build(s, FrostBodies.PART, FrostBodies.BOX, le.getBbHeight(), s.age(now));
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SHELLS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        float now = FrostFx.ticks();
        for (Iterator<Shell> it = SHELLS.values().iterator(); it.hasNext(); ) {
            Shell s = it.next();
            float age = s.age(now);
            if (s.broke < 0) {
                // The growth front glinting and dusting as it crosses the body.
                if (s.built && age < DEEP_GROW && age > 1) front(s, age);
                // By itself the ice starts cracking a while before the server breaks it.
                if (s.cracks == null && s.built && age >= crackFrom(s)) {
                    startCracks(s, now, false);
                    play(s.feet.add(0, s.height * .6, 0), ModSounds.ICEMAN_CRACK.get(), .45f, 1.8f);
                    IceParticles.later(9, () -> { if (s.broke < 0) play(s.feet.add(0, s.height * .5, 0), ModSounds.ICEMAN_CRACK.get(), .8f, .85f); });
                }
                // Never left standing: long past its time with no word from the server, it breaks by itself.
                if (age > s.total + 60) breakShell(s.entity, 1, null);
                continue;
            }
            if (s.chunks != null) fly(s, now);
            if (now - s.broke > 60) it.remove();
        }
    }
    private static float crackFrom(Shell s) { return Math.max(DEEP_GROW + 2, s.total - 22); }
    /** Glints, crystal dust and a breath of mist where the ice is growing now. */
    private static void front(Shell s, float age) {
        for (int tries = 0; tries < 3; tries++) {
            int p = s.rng.nextInt(PARTS), v = s.rng.nextInt(s.rings[p] * RN);
            float d = s.delay[p][v];
            if (age < d || age > d + GROW) continue;
            Vec3 at = vec(s, p, v).add(s.feet);
            Vec3 n = new Vec3(s.nrm[p][v * 3], s.nrm[p][v * 3 + 1], s.nrm[p][v * 3 + 2]);
            if (IceParticles.count(1, at) <= 0) return;
            IceParticles.crystalDust(at, n.scale(.04).add(0, .01, 0), .018f, (s.rng.nextFloat() - .5f) * .5f, 12 + s.rng.nextInt(8));
            if (s.rng.nextFloat() < .35f) IceParticles.mist(at, n.scale(.015).add(0, -.003, 0), .14f, .015f, .14f, 20);
        }
    }

    // ------------------------------------------------------------------ drawing
    /** Draws every shell (FrostBodies' render pass, with its ice context; the pose stack is the world's). */
    static void draw(IceStage st, IceMesh.Ctx c, boolean firstPerson) {
        if (SHELLS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        float now = FrostFx.now();
        for (Shell s : SHELLS.values()) {
            Entity ent = mc.level.getEntity(s.entity);
            boolean self = ent != null && ent == mc.getCameraEntity() && firstPerson;
            if (s.broke < 0 && ent instanceof LivingEntity le) {
                s.feet = le.getPosition(st.partial);
                if (!le.isInvisible()) build(s, le, st.partial, now);
            }
            if (!s.built || st.distance(s.feet) > 64) continue;
            boolean far = st.far(s.feet);
            st.pose.pushPose();
            st.pose.translate(s.feet.x, s.feet.y, s.feet.z);
            c.at(st.pose);
            c.light = IceStage.light(s.feet.add(0, s.height * .5, 0));
            c.ox = c.oy = c.oz = 0;
            ground(c, s, now);
            if (s.broke < 0) { if (!self) shell(c, s, now, far); }
            else if (!self || now - s.broke > 3) pieces(c, s, now, far);
            if (s.cracks != null && !self) cracks(c, s, now);
            c.ox = c.oy = c.oz = 0;
            st.pose.popPose();
        }
    }
    /** The cold light inside the ice as it closes (FilmFx, after the ice). */
    static void glow(FilmContext f) {
        float now = FrostFx.now();
        for (Shell s : SHELLS.values()) {
            float age = s.age(now);
            if (s.broke >= 0 || age > DEEP_GROW) continue;
            float k = Mth.sin(Mth.clamp(age / DEEP_GROW, 0, 1) * Mth.PI);
            FilmFx.glow(f, s.feet.add(contact(s)), s.height * .5, IceParticles.COLD_LIGHT, .22f * k);
        }
    }

    /** Pieces flying off, bursting into chips where they land; the first landing and the frost hiss heard. */
    private static void fly(Shell s, float now) {
        for (Chunk ch : s.chunks) {
            if (ch.gone) continue;
            float t = now - ch.letGo;
            if (t < 0) continue;
            Vec3 world = flight(ch, t).add(s.feet);
            if (!ch.chipped) {
                // As it lets go: a few small shards and frost from the break.
                ch.chipped = true;
                int n = IceParticles.count(ch.size > .12f ? 3 : 1, world);
                for (int i = 0; i < n; i++)
                    IceParticles.shard(world.add(IceParticles.jitter(ch.size * .4)), ch.vel.scale(.8).add(IceParticles.jitter(.05)).add(0, .03, 0), .025f + .035f * IceParticles.rand(), 22 + (int) (IceParticles.rand() * 12),
                            IceParticles.rand() < .4f ? IceMesh.FRESH : IceMesh.MILKY);
                IceParticles.frostDust(world, ch.vel, .6f);
            }
            Vec3 at = flight(ch, t);
            boolean down = t > 2 && at.y <= ch.size * .4 + 1e-3;
            if (down || t > 30) {
                ch.gone = true;
                IceParticles.shatter(world, ch.vel.multiply(1, 0, 1).scale(.5), Math.max(.12f, ch.size * 1.2f), IceParticles.rand() < .5f ? IceMesh.MILKY : IceMesh.GLACIER);
                if (!s.landedHeard) {
                    s.landedHeard = true;
                    play(world, ModSounds.ICEMAN_SHARD_RAIN.get(), .7f, 1.05f);
                }
                if (IceParticles.rand() < .25f) play(world, SoundEvents.AMETHYST_CLUSTER_BREAK, .35f, 1.3f + .4f * IceParticles.rand());
            }
        }
    }
    // ------------------------------------------------------------------ helpers
    private static void play(Vec3 at, SoundEvent ev, float vol, float pitch) {
        var mc = Minecraft.getInstance();
        if (mc.level != null) mc.level.playLocalSound(at.x, at.y, at.z, ev, SoundSource.PLAYERS, vol, pitch, false);
    }
    static void clear() { SHELLS.clear(); TURNED.clear(); }
}
