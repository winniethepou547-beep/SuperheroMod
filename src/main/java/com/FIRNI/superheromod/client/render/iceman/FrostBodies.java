package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The frost meter seen on bodies (any living thing: players, zombies, animals), drawn in Iceman's ice (IceMesh) after
 * the translucent blocks, stage by stage, every piece growing in from nothing as the meter rises and melting away as
 * it falls. Minecraft ice, never crystals: small square spikes and cubes of ice, flat square plates of frost:
 * <ul>
 * <li>from the first touch: rime on the ground round the feet and on the feet, a thin whitening of tiny frost specks
 *     all over, frosty footprints behind them;</li>
 * <li>from 25: square spikes of ice up the legs, little clusters of ice cubes on the shoulders;</li>
 * <li>from 50: bigger icing on the arms (hanging a little, like square icicles) and the torso, frost plates;</li>
 * <li>from 75: heavy cover, big spikes and cubes everywhere, a cluster on the head; the body shivers now and then;</li>
 * <li>DEEP FREEZE: a block of ice closes round them from the ground up in DEEP_CLOSE ticks, a chunky mass of big
 *     overlapping blocks (clear, milky and glacier ice) with flat tops and lids closing over the head, a frost skirt
 *     of square tiles and small cubes on the ground; cracks run over it near the end; when it breaks the blocks split,
 *     fly apart, tumble, shatter into ice cubes and melt (the inside of every break clean and bright).</li>
 * </ul>
 * Crystals ride on the body's real limbs where the model is a humanoid's (its limbs read just after it is drawn), on a
 * box layout of the bounding box otherwise (four legs for animals). Fewer of them far away, none past 48 blocks.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class FrostBodies {
    private FrostBodies() {}

    // ------------------------------------------------------------------ the layouts
    /** Parts: humanoid head, torso, right arm, left arm, right leg, left leg; four-legged head, torso, four legs. */
    static final int HEAD = 0, TORSO = 1, ARM_R = 2, ARM_L = 3, LEG_R = 4, LEG_L = 5;
    /** A humanoid model's boxes (pixels, model space: y down, front -z) and their pivots at rest. */
    private static final float[][] HUMAN_BOX = {{-4, -8, -4, 4, 0, 4}, {-4, 0, -2, 4, 12, 2}, {-3, -2, -2, 1, 10, 2},
            {-1, -2, -2, 3, 10, 2}, {-2, 0, -2, 2, 12, 2}, {-2, 0, -2, 2, 12, 2}};
    private static final float[][] HUMAN_PIVOT = {{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};
    private static final int CRYSTAL = 0, SPECK = 1, PLATE = 2, CLUSTER = 3, GROUND = 4, SPROUT = 5;

    /** One place frost grows: on a part's face (w across it, t from its top 0 to its bottom 1), from meter t0. */
    record Site(int kind, int part, int face, float w, float t, float t0, float span, float len, float rad, float bias, int seed, IceMesh.Mat mat) {}
    /** Every place frost grows on one body (made once from its id, so it is the same on every client). */
    record Sites(boolean humanoid, Site[] all) {}

    /** A humanoid's limbs as last drawn (pivot x, y, z and rotations x, y, z per part), read off its model. */
    private static final class Rig { final float[] parts = new float[36]; }
    private static final Map<Integer, Rig> RIGS = new HashMap<>();

    static boolean humanoid(LivingEntity le) {
        return le instanceof Player || !(le instanceof Animal) && le.getBbHeight() >= le.getBbWidth() * 1.5f;
    }

    private static Sites sites(FrostFx.Body b, LivingEntity le) {
        boolean human = humanoid(le);
        if (b.sites != null && b.sites.humanoid() == human) return b.sites;
        b.sites = new Sites(human, generate(b.id, human));
        return b.sites;
    }
    /** Where the frost will grow, stage by stage (see the class comment). */
    private static Site[] generate(int id, boolean human) {
        List<Site> out = new ArrayList<>();
        Random r = new Random(id * 7919L + 31);
        int top = human ? 2 : 3, front = human ? 4 : 5, back = human ? 5 : 4;
        int[] sides = {0, 1, 4, 5};
        int[] legs = human ? new int[]{LEG_R, LEG_L} : new int[]{2, 3, 4, 5};
        int[] arms = human ? new int[]{ARM_R, ARM_L} : new int[0];
        int[] any = human ? new int[]{TORSO, TORSO, TORSO, HEAD, ARM_R, ARM_L, LEG_R, LEG_L} : new int[]{TORSO, TORSO, TORSO, HEAD, 2, 3, 4, 5};
        IceMesh.Mat[] ice = {IceMesh.CLEAR, IceMesh.MILKY, IceMesh.GLACIER};
        // 0..25: the feet, the rime round them, the whitening.
        for (int leg : legs) for (int i = 0; i < (human ? 6 : 4); i++)
            out.add(new Site(CRYSTAL, leg, sides[r.nextInt(4)], f(r, .1f, .9f), f(r, .8f, .97f), f(r, 1, 13), 10, f(r, .05f, .09f), f(r, .012f, .02f), f(r, .4f, .9f), r.nextInt(1 << 20), IceMesh.FROST));
        for (int i = 0; i < 12; i++) {
            float a = f(r, 0, Mth.TWO_PI), d = f(r, .15f, .62f);
            out.add(new Site(GROUND, -1, 0, Mth.cos(a) * d, Mth.sin(a) * d, f(r, 1, 22), 12, f(r, .008f, .016f), f(r, .05f, .12f), 0, r.nextInt(1 << 20), r.nextInt(3) == 0 ? IceMesh.MILKY : IceMesh.FROST));
        }
        for (int i = 0; i < 7; i++) {
            float a = f(r, 0, Mth.TWO_PI), d = f(r, .25f, .65f);
            out.add(new Site(SPROUT, -1, 0, Mth.cos(a) * d, Mth.sin(a) * d, f(r, 6, 24), 10, f(r, .04f, .09f), f(r, .01f, .018f), f(r, .2f, .6f), r.nextInt(1 << 20), IceMesh.CLEAR));
        }
        for (int i = 0; i < 26; i++)
            out.add(new Site(SPECK, any[r.nextInt(any.length)], r.nextFloat() < .2f ? top : sides[r.nextInt(4)], f(r, .1f, .9f), f(r, .05f, .95f), f(r, 3, 30), 8, f(r, .02f, .04f), f(r, .007f, .011f), f(r, -.2f, .4f), r.nextInt(1 << 20), IceMesh.FROST));
        // 25..50: up the legs, the shoulders.
        for (int leg : legs) {
            for (int i = 0; i < (human ? 7 : 4); i++)
                out.add(new Site(CRYSTAL, leg, sides[r.nextInt(4)], f(r, .1f, .9f), f(r, .3f, .95f), f(r, 25, 48), 10, f(r, .07f, .15f), f(r, .018f, .03f), f(r, -.2f, .5f), r.nextInt(1 << 20), ice[r.nextInt(2)]));
            for (int i = 0; i < 2; i++)
                out.add(new Site(PLATE, leg, i == 0 ? front : sides[r.nextInt(4)], f(r, .2f, .8f), f(r, .35f, .85f), f(r, 30, 48), 10, f(r, .012f, .022f), f(r, .05f, .08f), 0, r.nextInt(1 << 20), r.nextBoolean() ? IceMesh.MILKY : IceMesh.FROST));
        }
        for (int arm : arms) {
            out.add(new Site(CLUSTER, arm, top, .5f, .5f, f(r, 26, 36), 12, f(r, .1f, .17f), f(r, .02f, .03f), 1.1f, r.nextInt(1 << 20), IceMesh.MILKY));
            for (int i = 0; i < 2; i++)
                out.add(new Site(CRYSTAL, arm, arm == ARM_R ? 0 : 1, f(r, .2f, .8f), f(r, 0, .15f), f(r, 28, 45), 10, f(r, .07f, .13f), f(r, .016f, .026f), .6f, r.nextInt(1 << 20), ice[r.nextInt(2)]));
        }
        if (human) for (float w : new float[]{.12f, .88f})
            out.add(new Site(CLUSTER, TORSO, top, w, .5f, f(r, 30, 42), 12, f(r, .08f, .14f), f(r, .018f, .026f), 1.1f, r.nextInt(1 << 20), IceMesh.CLEAR));
        else for (int i = 0; i < 3; i++)
            out.add(new Site(CLUSTER, TORSO, top, f(r, .2f, .8f), (i + .5f) / 3, f(r, 26, 40), 12, f(r, .1f, .17f), f(r, .02f, .03f), 1.1f, r.nextInt(1 << 20), IceMesh.MILKY));
        // 50..75: the arms (hanging like icicles), the torso.
        for (int arm : arms) {
            int outer = arm == ARM_R ? 0 : 1;
            int[] faces = {outer, outer, front, back};
            for (int i = 0; i < 6; i++)
                out.add(new Site(CRYSTAL, arm, faces[r.nextInt(4)], f(r, .15f, .85f), f(r, .2f, .95f), f(r, 50, 72), 12, f(r, .09f, .18f), f(r, .02f, .035f), f(r, -.7f, -.2f), r.nextInt(1 << 20), ice[r.nextInt(2)]));
            for (int i = 0; i < 3; i++)
                out.add(new Site(PLATE, arm, i == 0 ? front : outer, f(r, .2f, .8f), f(r, .15f, .85f), f(r, 52, 72), 12, f(r, .014f, .026f), f(r, .05f, .09f), 0, r.nextInt(1 << 20), r.nextBoolean() ? IceMesh.MILKY : IceMesh.FROST));
        }
        int[] torsoFaces = human ? new int[]{front, back} : sides;
        for (int i = 0; i < 7; i++)
            out.add(new Site(CRYSTAL, TORSO, torsoFaces[r.nextInt(torsoFaces.length)], f(r, .15f, .85f), f(r, .1f, .9f), f(r, 50, 74), 12, f(r, .1f, .2f), f(r, .02f, .035f), f(r, -.3f, .5f), r.nextInt(1 << 20), ice[r.nextInt(3)]));
        for (int i = 0; i < 5; i++)
            out.add(new Site(PLATE, TORSO, torsoFaces[r.nextInt(torsoFaces.length)], f(r, .15f, .85f), f(r, .1f, .9f), f(r, 50, 74), 12, f(r, .016f, .03f), f(r, .07f, .13f), 0, r.nextInt(1 << 20), r.nextBoolean() ? IceMesh.MILKY : IceMesh.FROST));
        // 75..99: heavy cover.
        for (int i = 0; i < 16; i++)
            out.add(new Site(CRYSTAL, any[r.nextInt(any.length)], sides[r.nextInt(4)], f(r, .15f, .85f), f(r, .1f, .9f), f(r, 75, 96), 14, f(r, .18f, .32f), f(r, .035f, .055f), f(r, -.4f, .6f), r.nextInt(1 << 20), ice[r.nextInt(3)]));
        for (int i = 0; i < 9; i++)
            out.add(new Site(PLATE, any[r.nextInt(any.length)], sides[r.nextInt(4)], f(r, .15f, .85f), f(r, .1f, .9f), f(r, 76, 97), 14, f(r, .03f, .05f), f(r, .09f, .15f), 0, r.nextInt(1 << 20), r.nextBoolean() ? IceMesh.MILKY : IceMesh.GLACIER));
        out.add(new Site(CLUSTER, HEAD, top, f(r, .3f, .7f), f(r, .3f, .7f), f(r, 80, 92), 12, f(r, .12f, .2f), f(r, .022f, .032f), 1f, r.nextInt(1 << 20), IceMesh.CLEAR));
        for (int i = 0; i < 2; i++)
            out.add(new Site(CRYSTAL, HEAD, i == 0 ? 0 : 1, f(r, .2f, .8f), f(r, .2f, .8f), f(r, 85, 98), 10, f(r, .1f, .16f), f(r, .02f, .03f), .4f, r.nextInt(1 << 20), IceMesh.MILKY));
        return out.toArray(new Site[0]);
    }
    private static float f(Random r, float a, float b) { return a + (b - a) * r.nextFloat(); }

    // ------------------------------------------------------------------ reading the limbs, the shiver
    /** Just after a humanoid body is drawn its model still holds this body's limb angles: kept for its frost. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void capture(RenderLivingEvent.Post<?, ?> e) {
        LivingEntity en = e.getEntity();
        if (!FrostFx.BODIES.containsKey(en.getId())) return;
        if (!(e.getRenderer().getModel() instanceof HumanoidModel<?> m)) return;
        Rig r = RIGS.computeIfAbsent(en.getId(), k -> new Rig());
        ModelPart[] parts = {m.head, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        for (int i = 0; i < 6; i++) {
            ModelPart p = parts[i];
            int o = i * 6;
            r.parts[o] = p.x; r.parts[o + 1] = p.y; r.parts[o + 2] = p.z;
            r.parts[o + 3] = p.xRot; r.parts[o + 4] = p.yRot; r.parts[o + 5] = p.zRot;
        }
    }

    private static final Set<Integer> PUSHED = new HashSet<>();
    /** Nearly frozen through, the body shivers in short bouts; a hard shiver right after the ice round it broke. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void shiverPre(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity en = e.getEntity();
        FrostFx.Body b = FrostFx.BODIES.get(en.getId());
        if (b == null || b.deep) return;
        float t = en.tickCount % 2000 + e.getPartialTick() + en.getId() % 97 * 3.7f;
        float shown = b.shown(e.getPartialTick());
        float bout = Mth.sin(t * .13f);
        float amp = shown > 70 ? (shown - 70) / 30f * .011f * bout * bout : 0;
        amp += b.shiver / 22f * .03f;
        if (amp < .0008f) return;
        PoseStack p = e.getPoseStack();
        p.pushPose();
        PUSHED.add(en.getId());
        p.translate(amp * Mth.sin(t * 7.3f), 0, amp * Mth.cos(t * 6.1f + 1));
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void shiverPost(RenderLivingEvent.Post<?, ?> e) {
        if (PUSHED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ footprints
    private record Print(double x, double y, double z, float yaw, float born, float life, float strength, int seed, float size) {}
    private static final List<Print> PRINTS = new ArrayList<>();
    private static final Random RNG = new Random();

    static void footprint(LivingEntity le, float meter, boolean left) {
        if (le.isInvisible()) return;
        float yaw = le.yBodyRot * Mth.DEG_TO_RAD;
        float side = (left ? 1 : -1) * le.getBbWidth() * .2f;
        double x = le.getX() + Mth.cos(yaw) * side, z = le.getZ() + Mth.sin(yaw) * side;
        if (PRINTS.size() >= 90) PRINTS.remove(0);
        int stage = stage(meter, false);
        PRINTS.add(new Print(x, le.getY() + .012, z, yaw, FrostFx.now(), 50 + 25 * stage, Mth.clamp(.35f + .18f * stage, 0, 1), RNG.nextInt(1 << 20), Mth.clamp(le.getBbWidth() / .6f, .5f, 2.2f)));
    }

    // ------------------------------------------------------------------ the deep freeze
    /** One of the big blocks of the deep freeze: round the body at an angle, leaning, its height, its two half-widths. */
    private record Slab(float ang, float radial, float by, float h, float w, float d, float lean, float delay, int sides, int seed,
                        IceMesh.Mat mat, float vOut, float vUp, float spin) {}
    private static final class Encase {
        final int entity; Vec3 feet; final float width, height, yaw, start; final int total;
        final Slab[] slabs; final float[][] cracks; final int[] crackSlab;
        float broke = -1; int how; boolean crackHeard, shardsDone;
        Encase(int entity, Vec3 feet, float width, float height, float yaw, float start, int total, Slab[] slabs, float[][] cracks, int[] crackSlab) {
            this.entity = entity; this.feet = feet; this.width = width; this.height = height; this.yaw = yaw; this.start = start; this.total = total;
            this.slabs = slabs; this.cracks = cracks; this.crackSlab = crackSlab;
        }
        float age(float now) { return now - start; }
    }
    private static final Map<Integer, Encase> ENCASED = new HashMap<>();
    /** Ticks the pieces of a broken block fly, and how long its frost skirt takes to melt. */
    private static final float PIECES = 18, SKIRT = 34;

    static boolean encased(int id) { Encase en = ENCASED.get(id); return en != null && en.broke < 0; }

    /** Deep frozen (since: ticks since it began, when we only see it now). */
    static void freeze(int id, Vec3 feet, float width, float height, int total, float since) {
        var mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(id);
        float yaw = e instanceof LivingEntity le ? le.yBodyRot * Mth.DEG_TO_RAD : 0;
        Random r = new Random(id * 31L + (long) FrostFx.now());
        float R = width * .5f + .08f;
        List<Slab> slabs = new ArrayList<>();
        IceMesh.Mat[] mats = {IceMesh.MILKY.alpha(.75f), IceMesh.CLEAR, IceMesh.GLACIER.alpha(.8f)};
        int n = 9;
        for (int i = 0; i < n; i++)
            slabs.add(new Slab(Mth.TWO_PI * i / n + f(r, -.25f, .25f), R * f(r, .45f, .8f), 0, f(r, .74f, 1.12f), width * f(r, .28f, .4f) + .05f, width * f(r, .2f, .3f) + .04f,
                    f(r, -.05f, .08f), f(r, 0, .5f), 5 + r.nextInt(2), r.nextInt(1 << 20), mats[i % 3], f(r, .05f, .1f), f(r, .06f, .14f), f(r, .12f, .26f) * (r.nextBoolean() ? 1 : -1)));
        // The lids closing over the head last.
        for (int i = 0; i < 3; i++)
            slabs.add(new Slab(f(r, 0, Mth.TWO_PI), R * .22f, .45f, f(r, 1.02f, 1.16f), width * .3f + .04f, width * .24f + .03f,
                    -.12f, f(r, .55f, .75f), 5, r.nextInt(1 << 20), IceMesh.CLEAR, f(r, .03f, .07f), f(r, .14f, .2f), f(r, .15f, .3f) * (r.nextBoolean() ? 1 : -1)));
        // The big cracks: random walks up and across the outer faces of a few blocks.
        int cn = 6;
        float[][] cracks = new float[cn][];
        int[] crackSlab = new int[cn];
        for (int c = 0; c < cn; c++) {
            crackSlab[c] = r.nextInt(n);
            int pts = 6 + r.nextInt(4);
            float[] p = new float[pts * 2];
            float w = f(r, -.6f, .6f), y = f(r, .06f, .3f);
            for (int k = 0; k < pts; k++) {
                p[k * 2] = w; p[k * 2 + 1] = y;
                w = Mth.clamp(w + f(r, -.32f, .32f), -.85f, .85f);
                y = Math.min(.78f, y + f(r, .04f, .11f));
            }
            cracks[c] = p;
        }
        Encase en = new Encase(id, feet, width, height, yaw, FrostFx.now() - since, Math.max(total, DEEP_CLOSE + 2), slabs.toArray(new Slab[0]), cracks, crackSlab);
        ENCASED.put(id, en);
        if (since > 1) return;
        // Humidity drawn in, a cold flash as it closes, a ring of frost on the ground.
        Vec3 mid = feet.add(0, height * .5, 0);
        int m = IceParticles.count(10, mid);
        for (int i = 0; i < m; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = R * 2.4;
            Vec3 at = feet.add(Math.cos(a) * d, height * (.1 + .8 * r.nextDouble()), Math.sin(a) * d);
            IceParticles.mist(at, mid.subtract(at).scale(.07), .25f, .006f, .28f, 12);
        }
        IceParticles.flash(mid, height * .45f, .45f, DEEP_CLOSE + 2);
        IceParticles.ring(feet.add(0, .05, 0), R * 2.2f, .3f, IceParticles.COLD_LIGHT, .45f, 10, true);
    }
    /** The block breaks (how 2: shattered by a blow, a bigger burst). */
    static void breakEncase(int id, int how) {
        Encase en = ENCASED.get(id);
        if (en == null || en.broke >= 0) return;
        en.broke = FrostFx.now();
        en.how = how;
        Vec3 mid = en.feet.add(0, en.height * .5, 0);
        float size = en.height * (how == 2 ? .75f : .55f);
        Vec3 push = how == 2 ? IceParticles.jitter(.08).multiply(1, 0, 1) : Vec3.ZERO;
        IceParticles.shatter(mid, push, size, IceMesh.CLEAR);
        IceParticles.shatter(mid.add(0, en.height * .2, 0), push, size * .5f, IceMesh.FRESH);
        IceParticles.ring(en.feet.add(0, .06, 0), en.width * 2 + 1.2f, .35f, IceParticles.COLD_LIGHT, .5f, 12, true);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ENCASED.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        float now = FrostFx.ticks();
        for (Iterator<Encase> it = ENCASED.values().iterator(); it.hasNext(); ) {
            Encase en = it.next();
            if (en.broke < 0) {
                // The crack before the break: heard once, as the cracks start to run.
                if (!en.crackHeard && en.age(now) >= crackFrom(en)) {
                    en.crackHeard = true;
                    mc.level.playLocalSound(en.feet.x, en.feet.y + en.height * .6, en.feet.z, ModSounds.ICEMAN_CRACK.get(), SoundSource.PLAYERS, .7f, .95f, false);
                }
                // Never left standing: long past its time with no word from the server, it breaks by itself.
                if (en.age(now) > en.total + 60) breakEncase(en.entity, 1);
                continue;
            }
            float t = now - en.broke;
            if (!en.shardsDone && t >= 8) {
                // The flying pieces burst into chunks of ice as they come down.
                en.shardsDone = true;
                float cos = Mth.cos(en.yaw), sin = Mth.sin(en.yaw);
                for (Slab s : en.slabs) {
                    Vec3 local = piece(en, s, .5f, 8, null, 1);
                    Vec3 at = en.feet.add(local.x * cos - local.z * sin, local.y, local.x * sin + local.z * cos);
                    int n = IceParticles.count(2, at);
                    for (int i = 0; i < n; i++)
                        IceParticles.shard(at.add(IceParticles.jitter(.1)), IceParticles.jitter(.06).add(0, .05, 0), s.w() * (.35f + .3f * RNG.nextFloat()), 30 + RNG.nextInt(20), i == 0 ? IceMesh.FRESH : s.mat());
                    if (IceParticles.count(1, at) > 0) IceParticles.mist(at, new Vec3(0, .004, 0), .25f, .015f, .2f, 22);
                }
            }
            if (t > SKIRT) it.remove();
        }
    }
    private static float crackFrom(Encase en) { return Math.max(DEEP_CLOSE + 4, en.total - 24); }

    // ------------------------------------------------------------------ drawing
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (FrostFx.BODIES.isEmpty() && ENCASED.isEmpty() && PRINTS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        IceStage st = IceStage.open(e);
        if (st == null) return;
        try {
            float now = FrostFx.now();
            IceMesh.Ctx c = st.ice();
            boolean firstPerson = mc.options.getCameraType().isFirstPerson();
            for (FrostFx.Body b : FrostFx.BODIES.values()) {
                if (!(mc.level.getEntity(b.id) instanceof LivingEntity le) || le.isInvisible()) continue;
                float shown = b.shown(st.partial);
                if (shown < .3f && b.blooms.isEmpty()) continue;
                Vec3 feet = le.getPosition(st.partial);
                double dist = st.distance(feet);
                if (dist > 48) continue;
                boolean self = le == mc.getCameraEntity() && firstPerson;
                st.pose.pushPose();
                st.pose.translate(feet.x, feet.y, feet.z);
                c.at(st.pose);
                c.light = IceStage.light(feet.add(0, le.getBbHeight() * .5, 0));
                body(c, b, le, shown, st.partial, now, st.far(feet), self);
                st.pose.popPose();
            }
            prints(c, st, now);
            for (Encase en : ENCASED.values()) {
                Entity ent = mc.level.getEntity(en.entity);
                if (en.broke < 0 && ent != null) en.feet = ent.getPosition(st.partial);
                if (st.distance(en.feet) > 64) continue;
                boolean self = ent != null && ent == mc.getCameraEntity() && firstPerson;
                st.pose.pushPose();
                st.pose.translate(en.feet.x, en.feet.y, en.feet.z);
                st.pose.mulPose(Axis.YP.rotation(-en.yaw));
                c.at(st.pose);
                c.light = IceStage.light(en.feet.add(0, en.height * .5, 0));
                encase(c, en, now, self, st.far(en.feet));
                st.pose.popPose();
            }
            st.endIce();
            // A cold glow inside the block as it closes.
            FilmContext f = st.fx();
            for (Encase en : ENCASED.values()) {
                float age = en.age(now);
                if (en.broke >= 0 || age > DEEP_CLOSE + 6) continue;
                float k = 1 - Mth.clamp(age / (DEEP_CLOSE + 6), 0, 1);
                FilmFx.glow(f, en.feet.add(0, en.height * .5, 0), en.height * .8, IceParticles.COLD_LIGHT, .3f * k);
            }
        } finally {
            st.close();
        }
    }

    // reused while drawing
    private static final Vector3f P = new Vector3f(), N = new Vector3f();
    private static final Matrix4f[] PART = {new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f()};
    private static final float[][] BOX = new float[6][6];
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** Every frost site on one body (the pose is at its feet). */
    private static void body(IceMesh.Ctx c, FrostFx.Body b, LivingEntity le, float shown, float partial, float now, boolean far, boolean self) {
        Sites sites = sites(b, le);
        float h = le.getBbHeight(), w = le.getBbWidth();
        float k = Mth.clamp(h / 1.8f, .4f, 2.5f);
        float yawDeg = Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot), yaw = yawDeg * Mth.DEG_TO_RAD;
        float cos = Mth.cos(yaw), sin = Mth.sin(yaw);
        // The limbs' frost only where the limbs are where we think (standing or crouching, alive, not seen from inside).
        boolean attached = !self && le.deathTime <= 0 && (le.getPose() == Pose.STANDING || le.getPose() == Pose.CROUCHING);
        if (attached) parts(le, sites.humanoid(), yawDeg, w, h);
        float ground = b.grounded(partial) * (le.isPassenger() ? 0 : 1);
        for (Site s : sites.all()) {
            if (far && (s.kind() == SPECK || (s.seed() & 1) == 1)) continue;
            float g = FilmFx.ease((shown - s.t0()) / s.span());
            if (g <= .01f) continue;
            float alpha = Math.min(1, g * 2.2f);
            if (s.kind() == GROUND || s.kind() == SPROUT) {
                if (ground < .02f) continue;
                float lx = s.w() * Math.max(w, .4f), lz = s.t() * Math.max(w, .4f);
                Vec3 at = new Vec3(lx * cos - lz * sin, .012, lx * sin + lz * cos);
                if (s.kind() == GROUND) plate(c, at, UP, s.rad() * k * (.4f + .6f * g), s.len() * k, s.seed(), s.mat(), alpha * ground * .9f);
                else {
                    // A little cube of ice frozen onto the ground, leaning out.
                    float size = s.len() * k * g * .8f;
                    Vec3 o = at.multiply(1, 0, 1);
                    float turn = (float) Math.atan2(o.x, o.z) + (float) IceMesh.hash(s.seed()) * .8f;
                    IceParticles.cube(c, at.x, at.y + size * .35f, at.z, size, size, size, turn, s.bias() * -.5f, 0, s.mat(), alpha * ground);
                }
                continue;
            }
            if (!attached || s.part() < 0) continue;
            float[] box = BOX[s.part()];
            onFace(PART[s.part()], box, s.face(), s.w(), s.t(), sites.humanoid());
            Vec3 at = new Vec3(P.x, P.y, P.z), n = new Vec3(N.x, N.y, N.z);
            switch (s.kind()) {
                case PLATE -> plate(c, at.add(n.scale(.004)), n, s.rad() * k * (.35f + .65f * g), s.len() * k * g, s.seed(), s.mat(), alpha * .9f);
                case CLUSTER -> {
                    // A few small cubes of ice frozen together on the surface.
                    Vec3 d = n.add(0, s.bias(), 0).normalize();
                    Vec3[] fr = IceMesh.frame(d);
                    for (int i = 0; i < 3; i++) {
                        float a = s.seed() * .37f + i * 2.1f;
                        float size = s.len() * k * g * (.5f + .25f * (float) IceMesh.hash(s.seed() + i));
                        Vec3 p = at.add(fr[0].scale(Mth.cos(a) * size * .5)).add(fr[1].scale(Mth.sin(a) * size * .5)).add(d.scale(size * (.2 + .25 * i)));
                        IceParticles.cube(c, p.x, p.y, p.z, size, size, size, a, (float) IceMesh.hash(s.seed() * 3 + i) - .5f, (float) IceMesh.hash(s.seed() * 5 + i) - .5f, s.mat(), alpha);
                    }
                }
                case SPECK -> {
                    // A fleck of frost: a tiny cube.
                    float size = s.len() * k * g * .6f;
                    Vec3 p = at.add(n.scale(size * .2));
                    IceParticles.cube(c, p.x, p.y, p.z, size, size, size, s.seed() * .7f, s.seed() * .3f, s.seed() * .11f, s.mat(), alpha);
                }
                default -> {
                    // A small square spike of ice standing out of the surface (hanging a little where it is biased down: a square icicle).
                    Vec3[] fr = IceMesh.frame(n);
                    float j0 = (float) IceMesh.hash(s.seed() * 1.3) - .5f, j1 = (float) IceMesh.hash(s.seed() * 2.9) - .5f;
                    Vec3 d = n.add(0, s.bias(), 0).add(fr[0].scale(j0 * .6)).add(fr[1].scale(j1 * .6)).normalize();
                    float r = s.rad() * k * (.35f + .65f * g);
                    float len = s.len() * k * g;
                    IceParticles.spike(c, at.subtract(d.scale(r * .6f)), d, len, r, s.seed() * .37f, far || len < .08f ? 0 : 1, s.mat(), alpha);
                }
            }
        }
        // The brush's blooms: a little burst of ice cubes freezing on, then melting.
        for (FrostFx.Bloom bl : b.blooms) {
            float age = now - bl.born();
            float g = FilmFx.ease(age / 3) * (1 - FilmFx.ease((age - 12) / 16));
            if (g <= .01f || self) continue;
            Vec3 at = new Vec3(bl.x() * cos - bl.z() * sin, bl.y(), bl.x() * sin + bl.z() * cos);
            Vec3 out = new Vec3(bl.nx() * cos - bl.nz() * sin, 0, bl.nx() * sin + bl.nz() * cos);
            Vec3[] fr = IceMesh.frame(out);
            for (int i = 0; i < 4; i++) {
                float a = bl.seed() * .53f + i * 1.7f;
                Vec3 di = out.add(fr[0].scale(Mth.cos(a) * .7)).add(fr[1].scale(Mth.sin(a) * .7)).normalize();
                float size = (.035f + .04f * (float) IceMesh.hash(bl.seed() + i * 3)) * k * g;
                Vec3 p = at.add(fr[0].scale(Mth.cos(a) * .04)).add(fr[1].scale(Mth.sin(a) * .04)).add(di.scale(size * .5));
                IceParticles.cube(c, p.x, p.y, p.z, size, size, size, a, a * .7f, a * .3f, i % 2 == 0 ? IceMesh.FROST : IceMesh.CLEAR, g);
            }
            if (age < 5) IceMesh.sparkle(c, at.add(out.scale(.05)), .09f * k, (1 - age / 5) * .9f);
        }
    }
    /** Every part's place (feet-relative) this frame: from the humanoid model's limbs, or a box layout. */
    private static void parts(LivingEntity le, boolean human, float yawDeg, float w, float h) {
        if (human) {
            float s = le instanceof Player ? .9375f : Mth.clamp(h / 1.95f, .3f, 4f);
            float drop = le instanceof Player && le.isCrouching() ? -.125f : 0;
            Rig rig = RIGS.get(le.getId());
            for (int i = 0; i < 6; i++) {
                Matrix4f m = PART[i].identity().translate(0, drop, 0).rotateY((180 - yawDeg) * Mth.DEG_TO_RAD).scale(-s, -s, s).translate(0, -1.501f, 0).scale(1 / 16f);
                if (rig != null) {
                    int o = i * 6;
                    m.translate(rig.parts[o], rig.parts[o + 1], rig.parts[o + 2]).rotateZYX(rig.parts[o + 5], rig.parts[o + 4], rig.parts[o + 3]);
                } else m.translate(HUMAN_PIVOT[i][0], HUMAN_PIVOT[i][1], HUMAN_PIVOT[i][2]);
                System.arraycopy(HUMAN_BOX[i], 0, BOX[i], 0, 6);
            }
            return;
        }
        for (int i = 0; i < 6; i++) PART[i].identity().rotateY(-yawDeg * Mth.DEG_TO_RAD);
        set(BOX[HEAD], -.24f * w, .58f * h, .6f * w, .24f * w, .98f * h, .98f * w);
        set(BOX[TORSO], -.42f * w, .42f * h, -.62f * w, .42f * w, .86f * h, .62f * w);
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -.27f : .27f) * w, z = (i < 2 ? .42f : -.42f) * w;
            set(BOX[2 + i], x - .1f * w, 0, z - .1f * w, x + .1f * w, .5f * h, z + .1f * w);
        }
    }
    private static void set(float[] b, float x0, float y0, float z0, float x1, float y1, float z1) { b[0] = x0; b[1] = y0; b[2] = z0; b[3] = x1; b[4] = y1; b[5] = z1; }
    /**
     * A point on a face of a part's box (into P, its outward normal into N, both feet-relative): faces 0..5 = -x, +x,
     * -y, +y, -z, +z of the box; w across the face, t from the part's top (0) to its bottom (1).
     */
    private static void onFace(Matrix4f m, float[] box, int face, float w, float t, boolean yDown) {
        int axis = face >> 1;
        boolean max = (face & 1) == 1;
        float vy = yDown ? Mth.lerp(t, box[1], box[4]) : Mth.lerp(t, box[4], box[1]);
        switch (axis) {
            case 0 -> { P.set(max ? box[3] : box[0], vy, Mth.lerp(w, box[2], box[5])); N.set(max ? 1 : -1, 0, 0); }
            case 1 -> { P.set(Mth.lerp(w, box[0], box[3]), max ? box[4] : box[1], Mth.lerp(t, box[2], box[5])); N.set(0, max ? 1 : -1, 0); }
            default -> { P.set(Mth.lerp(w, box[0], box[3]), vy, max ? box[5] : box[2]); N.set(0, 0, max ? 1 : -1); }
        }
        m.transformPosition(P);
        m.transformDirection(N);
        if (N.lengthSquared() < 1e-10f) N.set(0, 1, 0); else N.normalize();
    }
    /** A patch of frost hugging a surface: a flat square plate of ice lying on the plane of normal n, turned its own way. */
    private static void plate(IceMesh.Ctx c, Vec3 at, Vec3 n, float radius, float thick, int seed, IceMesh.Mat mat, float alpha) {
        if (radius <= .002f || alpha <= .01f) return;
        Vec3[] fr = IceMesh.frame(n);
        float ph = (float) IceMesh.hash(seed) * 6;
        Vec3 u = fr[0].scale(Mth.cos(ph)).add(fr[1].scale(Mth.sin(ph))), v = fr[1].scale(Mth.cos(ph)).subtract(fr[0].scale(Mth.sin(ph)));
        float t = Math.max(.002f, thick) * .5f;
        IceParticles.obox(c, at.add(n.scale(t)), u, n, v, radius * .8f, t, radius * .62f, mat, alpha);
    }

    private static void prints(IceMesh.Ctx c, IceStage st, float now) {
        if (PRINTS.isEmpty()) return;
        PRINTS.removeIf(p -> now - p.born() > p.life());
        c.at(st.pose);
        for (Print p : PRINTS) {
            Vec3 at = new Vec3(p.x(), p.y(), p.z());
            if (st.distance(at) > 40) continue;
            float age = (now - p.born()) / p.life();
            float melt = 1 - FilmFx.ease((age - .45f) / .55f);
            float a = p.strength() * melt * FilmFx.ease(age * 12);
            if (a <= .01f) continue;
            c.light = IceStage.light(at.add(0, .3, 0));
            Vec3 fwd = new Vec3(-Mth.sin(p.yaw()), 0, Mth.cos(p.yaw()));
            float s = p.size() * (.6f + .4f * melt);
            plate(c, at.add(fwd.scale(.07 * p.size())), UP, .075f * s, .008f, p.seed(), IceMesh.FROST, a);
            plate(c, at.subtract(fwd.scale(.09 * p.size())), UP, .055f * s, .007f, p.seed() + 7, IceMesh.FROST, a * .9f);
        }
    }

    /** The block of ice (the pose at the feet, turned to the body's facing when it froze). */
    private static void encase(IceMesh.Ctx c, Encase en, float now, boolean self, boolean far) {
        float age = en.age(now), H = en.height, R = en.width * .5f + .08f;
        float broken = en.broke < 0 ? -1 : now - en.broke;
        // The frost skirt on the ground: grows as it closes, melts after the break.
        float skirt = FilmFx.ease(age / (DEEP_CLOSE + 2)) * (broken < 0 ? 1 : 1 - FilmFx.ease(broken / SKIRT));
        if (skirt > .01f) for (int i = 0; i < 8; i++) {
            float a = i * Mth.TWO_PI / 8 + (float) IceMesh.hash(en.entity + i) * .6f, d = R * (1.05f + .35f * (float) IceMesh.hash(en.entity * 3 + i));
            Vec3 at = new Vec3(Mth.cos(a) * d, .012, Mth.sin(a) * d);
            plate(c, at, UP, (.12f + .1f * (float) IceMesh.hash(i * 7 + en.entity)) * skirt, .02f, en.entity + i * 13, IceMesh.FROST, .85f * skirt);
            float size = .1f * skirt * (.6f + .5f * (float) IceMesh.hash(i + en.entity * 5));
            Vec3 p = at.scale(.92);
            IceParticles.cube(c, p.x, p.y + size * .35f, p.z, size, size, size, a, .3f, 0, IceMesh.CLEAR, skirt);
        }
        if (broken < 0) {
            if (self) return;
            float crackK = FilmFx.ease((age - crackFrom(en)) / Math.max(4, en.total - crackFrom(en)));
            for (int i = 0; i < en.slabs.length; i++) {
                Slab s = en.slabs[i];
                float gp = FilmFx.ease((age - s.delay() * DEEP_CLOSE * .6f) / (DEEP_CLOSE * .75f));
                if (gp <= .01f) continue;
                float len = (s.h() - s.by()) * H * gp, ww = s.w() * (.45f + .55f * gp), dd = s.d() * (.45f + .55f * gp);
                Vec3 O = new Vec3(Mth.cos(s.ang()), 0, Mth.sin(s.ang())), S = new Vec3(-Mth.sin(s.ang()), 0, Mth.cos(s.ang()));
                Vec3 A = UP.add(O.scale(s.lean())).normalize();
                Vec3 base = O.scale(s.radial()).add(0, s.by() * H, 0);
                prism(c, base, A, O, S, len, ww, dd, 0, 1, far ? 4 : s.sides(), s.seed(), s.mat(), IceMesh.FRESH, Math.min(1, gp * 3), true);
            }
            if (crackK > .01f) for (int ci = 0; ci < en.cracks.length; ci++) crack(c, en, ci, crackK);
            return;
        }
        if (self && broken < 3) return;
        // Broken: every block splits in two, the pieces fly, tumble, shrink and melt.
        for (Slab s : en.slabs) for (int half = 0; half < 2; half++) {
            float y0 = half * .5f, y1 = y0 + .5f;
            Vec3[] axes = new Vec3[3];
            Vec3 centre = piece(en, s, (y0 + y1) / 2, broken, axes, half == 1 ? 1.25f : .8f);
            float melt = 1 - FilmFx.ease((broken - 7) / (PIECES - 7));
            if (melt <= .01f) continue;
            float len = (s.h() - s.by()) * H * melt;
            Vec3 base = centre.subtract(axes[0].scale(len * (y0 + y1) / 2));
            prism(c, base, axes[0], axes[1], axes[2], len, s.w() * melt, s.d() * melt, y0, y1, 4 + (s.sides() & 1), s.seed() + half * 29, s.mat(), IceMesh.FRESH, (float) Math.sqrt(melt), false);
        }
    }
    /**
     * A piece of a slab (around its share mid of the height) `t` ticks after the break: where its middle is (body-local)
     * and, into axes (if given), its turned axis, outward and side directions. k scales its throw (upper pieces further).
     */
    private static Vec3 piece(Encase en, Slab s, float mid, float t, Vec3[] axes, float k) {
        float H = en.height, len = (s.h() - s.by()) * H;
        Vec3 O = new Vec3(Mth.cos(s.ang()), 0, Mth.sin(s.ang())), S = new Vec3(-Mth.sin(s.ang()), 0, Mth.cos(s.ang()));
        Vec3 A = UP.add(O.scale(s.lean())).normalize();
        Vec3 c0 = O.scale(s.radial()).add(0, s.by() * H, 0).add(A.scale(len * mid));
        float kk = k, blow = en.how == 2 ? 1.6f : 1;
        float out = s.vOut() * kk * blow * t, up = s.vUp() * kk * t - .5f * .05f * t * t;
        Vec3 c = c0.add(O.scale(out)).add(0, up, 0);
        if (c.y < .08) c = new Vec3(c.x, .08, c.z);
        if (axes != null) {
            float th = s.spin() * blow * t * (mid > .5f ? 1.2f : .8f);
            axes[0] = IceParticles.rotate(A, S, th);
            axes[1] = IceParticles.rotate(O, S, th);
            axes[2] = S;
        }
        return c;
    }
    /** A crack running over a block's outer face, glowing (it is about to break), its front a spark. */
    private static void crack(IceMesh.Ctx c, Encase en, int ci, float k) {
        Slab s = en.slabs[en.crackSlab[ci]];
        float[] p = en.cracks[ci];
        int n = p.length / 2;
        float H = en.height, len = (s.h() - s.by()) * H;
        Vec3 O = new Vec3(Mth.cos(s.ang()), 0, Mth.sin(s.ang())), S = new Vec3(-Mth.sin(s.ang()), 0, Mth.cos(s.ang()));
        Vec3 A = UP.add(O.scale(s.lean())).normalize();
        Vec3 base = O.scale(s.radial()).add(0, s.by() * H, 0);
        float reach = k * (n - 1) * (1.1f - .2f * ci / (float) en.cracks.length);
        Vec3 prev = null;
        for (int i = 0; i < n; i++) {
            float y = p[i * 2 + 1], w = p[i * 2];
            Vec3 at = base.add(A.scale(len * y)).add(O.scale(s.d() * 1.04f)).add(S.scale(s.w() * w));
            if (prev != null) {
                float seg = Mth.clamp(reach - (i - 1), 0, 1);
                if (seg <= 0) break;
                Vec3 to = prev.lerp(at, seg);
                IceMesh.vein(c, prev, to, .007f + .006f * k, .95f * k);
                if (seg < 1) { IceMesh.sparkle(c, to, .06f, .8f * k); break; }
            }
            prev = at;
        }
    }
    /**
     * A block of the deep freeze between the shares y0..y1 of its height: a box along A (half-widths w across along S,
     * d outward along O), flat at both ends; an end that is a break (not the block's own bottom or top) in the bright
     * clean ice. (n, seed: unused, kept for the callers.)
     */
    private static void prism(IceMesh.Ctx c, Vec3 base, Vec3 A, Vec3 O, Vec3 S, float len, float w, float d, float y0, float y1,
                              int n, int seed, IceMesh.Mat mat, IceMesh.Mat cut, float alpha, boolean whole) {
        if (len <= .01f || alpha <= .01f) return;
        Vec3 u = O.subtract(A.scale(O.dot(A)));
        u = u.lengthSqr() < 1e-8 ? IceMesh.frame(A)[0] : u.normalize();
        float[] p = BLOCK;
        for (int k = 0; k < 8; k++) {
            int q = k & 3;
            double su = q == 0 || q == 3 ? -d : d, sv = q < 2 ? -w : w, sa = len * (k < 4 ? y0 : y1);
            p[k * 3] = (float) (base.x + u.x * su + S.x * sv + A.x * sa);
            p[k * 3 + 1] = (float) (base.y + u.y * su + S.y * sv + A.y * sa);
            p[k * 3 + 2] = (float) (base.z + u.z * su + S.z * sv + A.z * sa);
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            IceMesh.quad(c, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], p[j * 3], p[j * 3 + 1], p[j * 3 + 2],
                    p[j * 3 + 12], p[j * 3 + 13], p[j * 3 + 14], p[i * 3 + 12], p[i * 3 + 13], p[i * 3 + 14], mat, alpha);
        }
        IceMesh.quad(c, p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], y0 > 0 || !whole ? cut : mat, alpha);
        IceMesh.quad(c, p[12], p[13], p[14], p[15], p[16], p[17], p[18], p[19], p[20], p[21], p[22], p[23], y1 < 1 || !whole ? cut : mat, alpha);
    }
    private static final float[] BLOCK = new float[24];

    /** The body is no longer followed (gone, or its frost melted away). */
    static void forget(int id) { RIGS.remove(id); }
    static void clear() { RIGS.clear(); ENCASED.clear(); PRINTS.clear(); PUSHED.clear(); }
}
