package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.render.film.FilmContext;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.film.FilmFx;
import com.FIRNI.superheromod.client.render.ghost.GhostMaterials;
import com.FIRNI.superheromod.client.render.panther.PantherMotion;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Pose;
import com.FIRNI.superheromod.client.render.panther.PantherMotion.Track;
import com.FIRNI.superheromod.client.render.thor.ThorBolts;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.batman.BatmanConfig;
import com.FIRNI.superheromod.heroes.batman.BatmanShock;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.*;

import static com.FIRNI.superheromod.client.render.panther.PantherMotion.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.BLOW_HEAVY;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.BLOW_HIT;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.BLOW_SIDE;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.BLOW_TICKS;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.DISCHARGE_AT;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.FX_SHOCK_CLAP;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.FX_SHOCK_DISCHARGE;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.FX_SHOCK_EMPTY;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.FX_SHOCK_HIT;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.FX_SHOCK_MISS;
import static com.FIRNI.superheromod.heroes.batman.BatmanShock.FX_SHOCK_READY;

/**
 * The electric gauntlets as everyone sees them: the heavy WayneTech knuckles locking on and the clap that charges them,
 * the boxer's stance and the heavy blows, the living cyan electricity and the light it throws, the target's shock, his
 * energy bar.
 * <p>
 * The gauntlets (knuckles(), in BatmanBody's hand frame): a black bracer clamped over the wrist, graphite plates over the
 * back of the hand and the palm, the heavy gunmetal knuckle plate in front of the fist with four emitters behind
 * shutters, two coils on the back of the hand, thin conduits. They lock on part by part (bracer, plates, the knuckle
 * plate sliding forward); the shutters open once seated. The electricity lives on them as procedural arcs drawn in the
 * hand's own frame (between the emitters, crawling over the plates and coils, jumping off the knuckles), re-shaped
 * several times a tick so it never repeats; how many and how bright follows the charge: steady and dense over half,
 * sparser to a quarter, intermittent sparks to a tenth, irregular flickers to empty, nothing when the power is off
 * (the shutters close, a faint residual glow only).
 * <p>
 * Light (Minecraft has none that moves, so it is faked where the eye expects it, as for the wrist cannon): a small
 * cyan bloom on each knuckle, a soft dim volume over his hands, forearms, chest and cape, a faint splash on the faces
 * round him found by a few short rays once a tick, all flickering with the arcs; a very short flash at every blow's
 * full extension, a bright one on a hit, the clap's burst. Batman stays dark: the steady light is kept low.
 * <p>
 * A blow that lands (the server says so): a cyan-white flash at the contact, arcs branching round the target and up
 * into the air, electricity crawling over their body for about half a second while they tremble, sparks, the light on
 * them and round them, a small shake (more for the heavy blows) and a short cyan wash for both of them; his pose holds
 * for the hit-stop (local(): his blow's clock stops for a couple of ticks, matching the server's longer blow).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanShockFx {
    private BatmanShockFx() {}

    // ------------------------------------------------------------------ look and tuning
    /** The electricity: white core, pale cyan, cyan, turquoise, deep teal; sparks; the air pushed by a blow. */
    static final int CORE = 0xf2ffff, PALE = 0xbaf6ff, CYAN = 0x48e2ff, TURQ = 0x1fc8d8, TEAL = 0x0e8aa4, SPARK = 0xd8fbff, AIR = 0xc8d4da;
    /** A target's shock lasts SHOCK_LIFE ticks; a punch flash FLASH_LIFE; light on a surface SPLASH_LIFE. */
    static final float SHOCK_LIFE = 11, SPLASH_LIFE = 3.5f;
    /** How far the gauntlets' light is looked for round him. */
    static final double LIGHT_REACH = 5;
    /** His walk while a blow is thrown, and while they go on or come off (part of normal). */
    static final float PUNCH_WALK = .3f, EQUIP_WALK = .45f;
    /** Equip timeline: the bracer, plates and knuckle plate lock on by worn 1 (t 0.5..7); shutters open; the charge spreads. */
    static final float LOCKED = 7, OPEN_FROM = 9, OPEN_TO = 13, CHARGE_FROM = 11, CHARGE_TO = 24;
    private static final int TRAIL = 10;

    // ------------------------------------------------------------------ state
    /** One Batman's gauntlets as this client knows them: where they were drawn, the flashes, the light, the hum. */
    private static final class Rig {
        final int id;
        final Vec3[] knuckle = new Vec3[2], cuff = new Vec3[2], ahead = new Vec3[2];
        long seen = -100;
        final Vec3[][] trail = new Vec3[2][TRAIL];
        final float[][] trailT = new float[2][TRAIL];
        final int[] trailHead = new int[2];
        final float[] flash = {-100, -100}, flashPower = {0, 0};
        float hitAt = -100, open, openAt = -1;
        long raysAt = -1;
        final List<Surface> rays = new ArrayList<>();
        Hum hum;
        Rig(int id) { this.id = id; }
    }
    private record Surface(Vec3 at, Vec3 normal, double distance) {}
    private record Splash(Vec3 at, Vec3 normal, float radius, float alpha, float start, float life) {}
    /** Someone hit by a charged blow: electricity over their body for SHOCK_LIFE ticks (contact relative to their feet). */
    private record Shock(int entity, float start, float power, int seed, Vec3 contact) {}
    /** A free arc in the world for a few ticks (the clap's burst, a miss's crack, a discharge), re-shaped every few frames. */
    private record Bolt(Vec3 a, Vec3 b, float start, float life, int seed, float width) {}
    private static final class Mote {
        Vec3 pos, vel; final float size, grow, life, start, alpha; final int rgb, kind;
        Mote(Vec3 pos, Vec3 vel, float size, float grow, int rgb, float life, float start, int kind, float alpha) {
            this.pos = pos; this.vel = vel; this.size = size; this.grow = grow; this.rgb = rgb; this.life = life; this.start = start; this.kind = kind; this.alpha = alpha;
        }
    }
    private static final int M_GLOW = 0, M_SPARK = 1, M_PUFF = 2;

    private static final Map<Integer, Rig> RIGS = new HashMap<>();
    private static final List<Shock> SHOCKS = new ArrayList<>();
    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static final List<Splash> SPLASHES = new ArrayList<>();
    private static final List<Mote> MOTES = new ArrayList<>();
    private static final Set<Integer> PUSHED = new HashSet<>();
    private static final Random RANDOM = new Random();
    // his own view and HUD
    private static float wash, washAt = -100, readyAt = -100;
    private static float barShown, barValue = -1, barLost, barBam, barAt = -1, dropAt = -100;

    private static float now() { var mc = Minecraft.getInstance(); return mc.level == null ? 0 : mc.level.getGameTime() + mc.getFrameTime(); }
    private static float amount() { return BatmanConfig.EFFECTS.get().floatValue(); }
    private static int me() { var p = Minecraft.getInstance().player; return p == null ? -1 : p.getId(); }
    private static double rnd(double s) { return (RANDOM.nextDouble() - .5) * 2 * s; }
    private static float clamp01(float x) { return Mth.clamp(x, 0, 1); }
    private static float k(float t, float a, float b) { return PantherMotion.k(t, a, b); }
    private static float noise(float x) { return Mth.sin(x * 1.7f) * .5f + Mth.sin(x * 3.1f + 1.3f) * .3f + Mth.sin(x * 7.3f + 4.1f) * .2f; }

    // ------------------------------------------------------------------ the timeline (pure functions of the synced state)
    /** The electricity is on: charge left and not switched off (empty, recharging). */
    static boolean powered(BatmanClient.State s) { return s != null && s.energy > 0 && !BatmanShock.off(s.combo); }
    /** How much of the gauntlets is on his hands at the action clock t, 0..1. */
    static float wornAt(BatmanClient.State s, int action, float t) {
        if (action == SHOCK_EQUIP) return k(t, .5f, LOCKED);
        if (action == SHOCK_UNEQUIP) return 1 - k(t, 8, 14);
        return s.shock ? 1 : 0;
    }
    /** How far the charge has spread through them (on: during the lock-on; off: drained before the last discharge). */
    static float chargeAt(int action, float t) {
        if (action == SHOCK_EQUIP) return k(t, CHARGE_FROM, CHARGE_TO);
        if (action == SHOCK_UNEQUIP) return 1 - k(t, 0, DISCHARGE_AT + .5f);
        return 1;
    }
    /** How open the emitter shutters want to be. */
    private static float openWant(BatmanClient.State s, int action, float t) {
        if (!powered(s)) return 0;
        if (action == SHOCK_EQUIP) return k(t, OPEN_FROM, OPEN_TO);
        if (action == SHOCK_UNEQUIP) return 1 - k(t, 1.5f, 6.5f);
        return 1;
    }
    /**
     * How alive the electricity is at a charge (0..1 of full brightness and density): steady over half, sparser to a
     * quarter, small intermittent sparks to a tenth, irregular flickers below; always flickering a little.
     */
    static float alive(float energy, float now, int id) {
        float flick = .8f + .2f * noise(now * 1.9f + id * .37f);
        if (energy > .5f) return (.85f + .3f * (energy - .5f)) * flick;
        if (energy > .25f) return .6f * flick * (.7f + .3f * Math.max(0, Mth.sin(now * 1.3f + id)));
        if (energy > .1f) return FilmFx.hash(Math.floor(now / 2.5f) * 13.1 + id * 7.7) < .45 ? .42f * flick : .05f;
        if (energy > 0) return FilmFx.hash(Math.floor(now / 1.4f) * 5.3 + id * 3.1) < .22 ? .32f * flick : .02f;
        return 0;
    }
    /** A flash's strength (1 for an instant, then gone within a few frames). */
    private static float flashK(float start, float now) {
        float a = now - start;
        return a < 0 ? 0 : a < .3f ? 1 : (float) Math.exp(-(a - .3f) / 1.1f);
    }
    /** The blow's own clock: held for the hit-stop once it has landed (the server holds the blow as long). */
    static float local(float t, int combo) {
        if (!BatmanShock.landed(combo)) return t;
        int b = BatmanShock.blow(combo);
        float h = BLOW_HIT[b], stop = BatmanShock.stop(b);
        if (t < h) return t;
        if (t < h + stop) return h + (t - h) * .08f;
        return t - stop * .92f;
    }
    /**
     * How strong one hand's electricity is for this Batman now (0..~1.6): the charge's life, spreading in as they lock
     * on and out before they come off, denser on the hand that is striking, a surge after the clap.
     */
    private static float level(BatmanClient.State s, int id, int action, float t, int side, float now) {
        if (!powered(s)) return 0;
        float live = chargeAt(action, t) * alive(s.energy, now, id);
        if (action == SHOCK_PUNCH && !BatmanShock.dry(s.combo)) {
            int b = BatmanShock.blow(s.combo);
            if (BLOW_SIDE[b] == side) {
                float lt = local(t, s.combo), h = BLOW_HIT[b];
                live *= 1 + .9f * FilmFx.window(lt, h * .3f, h + 2.5f, 1.5f);
            }
        }
        if (action == SHOCK_EQUIP) live *= 1 + .9f * FilmFx.window(t, SHOCK_CLAP, SHOCK_CLAP + 6, 1);
        return live;
    }

    private static Rig rig(BatmanClient.State s) {
        for (var en : BatmanClient.states().entrySet()) if (en.getValue() == s) return RIGS.computeIfAbsent(en.getKey(), Rig::new);
        return null;
    }
    private static boolean fresh(Rig r) {
        var level = Minecraft.getInstance().level;
        return r != null && level != null && level.getGameTime() - r.seen <= 2;
    }
    /** Where this hand's knuckles are: as last drawn, or about where they would be from his look. */
    private static Vec3 knuckle(Rig r, Entity e, int side, float partial) {
        if (fresh(r) && r.knuckle[side] != null) return r.knuckle[side];
        if (e == null) return null;
        float yaw = e.getViewYRot(partial) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(side == 0 ? .32 : -.32);
        return e.getEyePosition(partial).add(0, -.45, 0).add(right).add(e.getViewVector(partial).scale(.5));
    }

    // ------------------------------------------------------------------ what the server reports
    /** Effect kinds FX_SHOCK_FIRST..FX_SHOCK_LAST from the server. */
    static void receive(BatmanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = now();
        Rig r = RIGS.computeIfAbsent(p.id(), Rig::new);
        Entity batman = mc.level.getEntity(p.id());
        boolean self = p.id() == me();
        Vec3 at = p.pos(), dir = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
        float partial = mc.getFrameTime();
        switch (p.kind()) {
            case FX_SHOCK_CLAP -> {
                Vec3 a = knuckle(r, batman, 0, partial), b = knuckle(r, batman, 1, partial);
                Vec3 c = a != null && b != null ? a.add(b).scale(.5) : at;
                if (p.power() > 0) {
                    r.flash[0] = r.flash[1] = now; r.flashPower[0] = r.flashPower[1] = 2.6f;
                    MOTES.add(new Mote(c, Vec3.ZERO, .5f, 0, CORE, 3, now, M_GLOW, 1));
                    MOTES.add(new Mote(c, Vec3.ZERO, 2.4f, 0, CYAN, 5, now, M_GLOW, .45f));
                    MOTES.add(new Mote(c, Vec3.ZERO, 5.5f, 0, TEAL, 6, now, M_GLOW, .12f));
                    // The burst: short arcs thrown out round the fists, mostly out to the sides and up.
                    for (int i = 0; i < 9; i++) {
                        Vec3 out = new Vec3(rnd(1), rnd(.6) + .25, rnd(1)).normalize().scale(.5 + RANDOM.nextDouble() * .9);
                        BOLTS.add(new Bolt(c, c.add(out), now, 3 + RANDOM.nextFloat() * 2.5f, RANDOM.nextInt(1 << 20), .035f));
                    }
                    sparks(c, new Vec3(0, .3, 0), (int) (26 * amount()), .3f);
                    light(mc, c, 1.0f, 1.8f, SPLASH_LIFE + 2);
                    readyAt = self ? now : readyAt;
                    if (self) { BatmanClient.shake(.16f); flashView(.11f); }
                    else if (near(c, 6)) BatmanClient.shake(.05f);
                } else {
                    sparks(c, new Vec3(0, .1, 0), (int) (4 * amount()), .08f);
                    MOTES.add(new Mote(c, Vec3.ZERO, .5f, .06f, AIR, 8, now, M_PUFF, .12f));
                    if (self) BatmanClient.shake(.06f);
                }
            }
            case FX_SHOCK_HIT -> hit(mc, r, p, now, self);
            case FX_SHOCK_MISS -> {
                int code = (int) p.power(), b = code % 100;
                boolean charged = code < 100;
                int side = BLOW_SIDE[b];
                Vec3 k = knuckle(r, batman, side, partial);
                Vec3 from = k != null ? k : at;
                // Air pushed ahead of the fist (a faint ring of dust and air), weaker than a hit.
                MOTES.add(new Mote(from.add(dir.scale(.5)), dir.scale(.05), .35f, .07f, AIR, 7, now, M_PUFF, .1f));
                if (charged) {
                    r.flash[side] = now; r.flashPower[side] = .9f;
                    Vec3 end = from.add(dir.scale(.75 + RANDOM.nextDouble() * .4)).add(rnd(.2), rnd(.2), rnd(.2));
                    BOLTS.add(new Bolt(from, end, now, 2.5f, RANDOM.nextInt(1 << 20), .025f));
                    sparks(from, dir.scale(.12), (int) (6 * amount()), .12f);
                    light(mc, from, .35f, 1.0f, 1.8f);
                }
                if (self) BatmanClient.shake(.025f);
            }
            case FX_SHOCK_EMPTY -> {
                // The last of it: both gauntlets sputter, one weak arc each, then dark.
                for (int side = 0; side < 2; side++) {
                    Vec3 k = knuckle(r, batman, side, partial);
                    if (k == null) continue;
                    r.flash[side] = now; r.flashPower[side] = .6f;
                    BOLTS.add(new Bolt(k, k.add(rnd(.3), -.2 - RANDOM.nextDouble() * .3, rnd(.3)), now, 3, RANDOM.nextInt(1 << 20), .02f));
                    sparks(k, new Vec3(0, -.05, 0), (int) (8 * amount()), .1f);
                    MOTES.add(new Mote(k, new Vec3(0, .02, 0), .15f, .03f, AIR, 16, now, M_PUFF, .12f));
                }
                if (self) BatmanClient.shake(.04f);
            }
            case FX_SHOCK_READY -> {
                // Charged again: a crackle runs over both gauntlets.
                for (int side = 0; side < 2; side++) {
                    Vec3 k = knuckle(r, batman, side, partial);
                    if (k == null) continue;
                    r.flash[side] = now; r.flashPower[side] = 1.3f;
                    for (int i = 0; i < 3; i++)
                        BOLTS.add(new Bolt(k, k.add(rnd(.35), rnd(.35), rnd(.35)), now, 2.5f + RANDOM.nextFloat() * 2, RANDOM.nextInt(1 << 20), .022f));
                    sparks(k, new Vec3(0, .08, 0), (int) (7 * amount()), .12f);
                }
                if (self) { readyAt = now; BatmanClient.shake(.05f); }
            }
            case FX_SHOCK_DISCHARGE -> {
                if (p.power() <= 0) break;
                // Coming off: the last of the charge leaves into the ground in a few arcs.
                for (int side = 0; side < 2; side++) {
                    Vec3 k = knuckle(r, batman, side, partial);
                    if (k == null) continue;
                    r.flash[side] = now; r.flashPower[side] = 1.2f;
                    for (int i = 0; i < 2; i++)
                        BOLTS.add(new Bolt(k, k.add(rnd(.4), -.6 - RANDOM.nextDouble() * .6, rnd(.4)), now, 3, RANDOM.nextInt(1 << 20), .026f));
                    sparks(k, new Vec3(0, -.1, 0), (int) (6 * amount()), .14f);
                }
                if (self) BatmanClient.shake(.04f);
            }
            default -> {}
        }
        trim();
    }
    /** A blow that landed. */
    private static void hit(Minecraft mc, Rig r, BatmanFxPacket p, float now, boolean self) {
        int code = (int) p.power(), b = code % 100;
        boolean charged = code < 100, heavy = BLOW_HEAVY[b];
        int side = BLOW_SIDE[b];
        Vec3 at = p.pos(), dir = p.dir().lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : p.dir().normalize();
        Entity target = p.entity() >= 0 ? mc.level.getEntity(p.entity()) : null;
        boolean victim = p.entity() == me();
        if (!charged) {
            // No charge: a plain heavy blow, dust and a dull flash.
            MOTES.add(new Mote(at, Vec3.ZERO, .5f, 0, AIR, 3, now, M_GLOW, .25f));
            for (int i = 0; i < (int) (6 * amount()); i++)
                MOTES.add(new Mote(at, dir.scale(.06).add(rnd(.1), rnd(.08) + .03, rnd(.1)), .25f, .05f, AIR, 12 + RANDOM.nextInt(6), now, M_PUFF, .25f));
            if (self) BatmanClient.shake(heavy ? .14f : .09f);
            else if (victim) BatmanClient.shake(heavy ? .2f : .14f);
            return;
        }
        float power = heavy ? 1.35f : 1;
        r.flash[side] = now; r.flashPower[side] = 2.4f * power;
        r.hitAt = now;
        // The contact: a white-hot point, a cyan flash, a wider soft bloom.
        MOTES.add(new Mote(at, Vec3.ZERO, .42f * power, 0, CORE, 2.5f, now, M_GLOW, 1));
        MOTES.add(new Mote(at, Vec3.ZERO, 1.5f * power, 0, CYAN, 4, now, M_GLOW, .55f));
        MOTES.add(new Mote(at, Vec3.ZERO, 3.6f * power, 0, TEAL, 5, now, M_GLOW, .14f));
        // The discharge round the contact: a few arcs leaving it, some up into the air (not an explosion).
        int n = heavy ? 6 : 4;
        for (int i = 0; i < n; i++) {
            Vec3 out = new Vec3(rnd(1), rnd(.5) + (i < 2 ? 1.1 : .2), rnd(1)).add(dir.scale(-.4)).normalize();
            BOLTS.add(new Bolt(at, at.add(out.scale(.6 + RANDOM.nextDouble() * (i < 2 ? 1.3 : .7))), now, 2.5f + RANDOM.nextFloat() * 2, RANDOM.nextInt(1 << 20), .03f * power));
        }
        sparks(at, dir.scale(-.12).add(0, .1, 0), (int) ((heavy ? 22 : 15) * amount()), .3f);
        // Electricity spreading over their body for about half a second.
        if (target != null) SHOCKS.add(new Shock(target.getId(), now, power, RANDOM.nextInt(1 << 20), at.subtract(target.position())));
        light(mc, at, 1.0f * power, 1.6f, SPLASH_LIFE + 1);
        // The impact frame: a small shake (more for the heavy ones), a short cyan wash for both of them.
        if (self) { BatmanClient.shake(heavy ? .22f : .13f); flashView(heavy ? .09f : .06f); }
        else if (victim) { BatmanClient.shake(heavy ? .28f : .18f); flashView(.12f); }
        else if (near(at, 8)) BatmanClient.shake(.03f);
    }
    private static boolean near(Vec3 at, double range) { var p = Minecraft.getInstance().player; return p != null && p.position().distanceTo(at) < range; }
    private static void flashView(float k) { wash = Math.max(washNow(now()), 0) + k; washAt = now(); }
    private static float washNow(float now) { return wash * (float) Math.exp(-Math.max(0, now - washAt) / 1.3f); }
    private static void sparks(Vec3 at, Vec3 drift, int count, float speed) {
        float now = now();
        for (int i = 0; i < count; i++)
            MOTES.add(new Mote(at, drift.add(rnd(speed), rnd(speed) + speed * .25, rnd(speed)), .02f, 0, i % 3 == 0 ? CORE : i % 3 == 1 ? PALE : CYAN,
                    3 + RANDOM.nextInt(6), now, M_SPARK, 1));
    }
    /** A splash of cyan light on the faces round a point (a flash, a hit, the clap). */
    private static void light(Minecraft mc, Vec3 src, float power, float size, float life) {
        if (mc.player == null) return;
        Vec3[] dirs = {new Vec3(0, -1, 0), new Vec3(1, -.3, 0), new Vec3(-1, -.3, 0), new Vec3(0, -.3, 1), new Vec3(0, -.3, -1), new Vec3(0, 1, 0),
                new Vec3(.7, -.7, .7), new Vec3(-.7, -.7, -.7)};
        for (Vec3 d : dirs) {
            BlockHitResult hit = mc.level.clip(new ClipContext(src, src.add(d.normalize().scale(LIGHT_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) continue;
            float dist = (float) hit.getLocation().distanceTo(src);
            float alpha = .7f * power / (1 + .3f * dist * dist);
            if (alpha < .02f) continue;
            SPLASHES.add(new Splash(hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getNormal()), (size + .45f * dist), Math.min(.9f, alpha), now(), life));
        }
    }
    private static void trim() {
        while (MOTES.size() > 900) MOTES.remove(0);
        while (BOLTS.size() > 120) BOLTS.remove(0);
        while (SPLASHES.size() > 300) SPLASHES.remove(0);
        while (SHOCKS.size() > 40) SHOCKS.remove(0);
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { clear(); return; }
        float t = mc.level.getGameTime();
        SHOCKS.removeIf(s -> t - s.start() > SHOCK_LIFE + 1);
        BOLTS.removeIf(b -> t - b.start() > b.life() + 1);
        SPLASHES.removeIf(s -> t - s.start() > s.life() + 1);
        for (Iterator<Mote> it = MOTES.iterator(); it.hasNext(); ) {
            Mote m = it.next();
            if (t - m.start > m.life) { it.remove(); continue; }
            m.pos = m.pos.add(m.vel);
            m.vel = switch (m.kind) {
                case M_SPARK -> m.vel.add(0, -.03, 0).scale(.88);
                case M_PUFF -> m.vel.scale(.86).add(0, .002, 0);
                default -> m.vel;
            };
        }
        // Each worn pair: the hum while it is charged, the little leftover bits falling off the knuckles.
        for (var en : BatmanClient.states().entrySet()) {
            BatmanClient.State s = en.getValue();
            int action = s.action;
            float clock = BatmanClient.clock(s, 0);
            if (wornAt(s, action, clock) <= 0) continue;
            Entity batman = mc.level.getEntity(en.getKey());
            if (batman == null) continue;
            Rig r = RIGS.computeIfAbsent(en.getKey(), Rig::new);
            boolean on = powered(s) && chargeAt(action, clock) > .05f && action != SHOCK_UNEQUIP;
            if (on && (r.hum == null || r.hum.isStopped())) { r.hum = new Hum(en.getKey(), batman); mc.getSoundManager().play(r.hum); }
            for (int side = 0; side < 2; side++) {
                float lv = level(s, r.id, action, clock, side, t);
                if (lv <= .05f || RANDOM.nextFloat() > .22f * lv * amount()) continue;
                Vec3 k = knuckle(r, batman, side, 0);
                if (k != null) MOTES.add(new Mote(k.add(rnd(.05), rnd(.05), rnd(.05)), new Vec3(rnd(.03), -.01, rnd(.03)), .015f, 0, RANDOM.nextBoolean() ? PALE : CYAN,
                        3 + RANDOM.nextInt(4), t, M_SPARK, .9f));
            }
        }
        // Those still shocked throw off small sparks.
        for (Shock s : SHOCKS) {
            Entity body = mc.level.getEntity(s.entity());
            if (body == null) continue;
            float fade = 1 - (t - s.start()) / SHOCK_LIFE;
            if (fade <= 0) continue;
            AABB box = body.getBoundingBox();
            for (int i = 0; i < (int) (3 * fade * amount() + RANDOM.nextFloat()); i++) {
                Vec3 at = surface(box, RANDOM.nextDouble(), RANDOM.nextDouble(), RANDOM.nextDouble());
                MOTES.add(new Mote(at, new Vec3(rnd(.08), RANDOM.nextDouble() * .08, rnd(.08)), .015f, 0, i % 2 == 0 ? CORE : CYAN, 3 + RANDOM.nextInt(3), t, M_SPARK, 1));
            }
        }
        RIGS.values().removeIf(r -> mc.level.getEntity(r.id) == null && (r.hum == null || r.hum.isStopped()));
    }
    /** A point on a box's surface (face picked by f, u and v across it), a hair outside. */
    private static Vec3 surface(AABB box, double f, double u, double v) {
        double x0 = box.minX - .03, x1 = box.maxX + .03, y0 = box.minY + .05, y1 = box.maxY + .03, z0 = box.minZ - .03, z1 = box.maxZ + .03;
        int face = (int) (f * 5);
        return switch (face) {
            case 0 -> new Vec3(x0, Mth.lerp(u, y0, y1), Mth.lerp(v, z0, z1));
            case 1 -> new Vec3(x1, Mth.lerp(u, y0, y1), Mth.lerp(v, z0, z1));
            case 2 -> new Vec3(Mth.lerp(v, x0, x1), Mth.lerp(u, y0, y1), z0);
            case 3 -> new Vec3(Mth.lerp(v, x0, x1), Mth.lerp(u, y0, y1), z1);
            default -> new Vec3(Mth.lerp(u, x0, x1), y1, Mth.lerp(v, z0, z1));
        };
    }

    // ------------------------------------------------------------------ the hum while they are charged
    /** The stored charge humming in the gauntlets, following him: louder and higher with the charge, flickering, cut when the power goes. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final int id;
        private float level;
        private boolean ended;
        Hum(int id, Entity e) {
            super(ModSounds.BATMAN_SHOCK_HUM.get(), SoundSource.PLAYERS, RandomSource.create());
            this.id = id;
            looping = true; delay = 0; volume = .02f; pitch = 1;
            x = e.getX(); y = e.getY() + 1.1; z = e.getZ();
        }
        @Override public void tick() {
            var mc = Minecraft.getInstance();
            Entity e = mc.level == null ? null : mc.level.getEntity(id);
            BatmanClient.State s = BatmanClient.get(e);
            if (ended || e == null || s == null) { stop(); return; }
            float clock = BatmanClient.clock(s, 0), now = mc.level.getGameTime();
            boolean on = powered(s) && wornAt(s, s.action, clock) > 0 && s.action != SHOCK_UNEQUIP;
            float want = on ? chargeAt(s.action, clock) * (.45f + .55f * Math.max(level(s, id, s.action, clock, 0, now), level(s, id, s.action, clock, 1, now))) : 0;
            // The power cut is instant (silence), the swell in is quick.
            level = on ? level + (want - level) * .4f : 0;
            if (!on) { stop(); return; }
            volume = Math.max(.01f, .32f * level * (.6f + .4f * s.energy));
            pitch = .82f + .22f * s.energy + .05f * level;
            x = e.getX(); y = e.getY() + 1.1; z = e.getZ();
        }
        void end() { ended = true; }
    }

    // ------------------------------------------------------------------ drawing in the world
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean worn = false;
        for (BatmanClient.State s : BatmanClient.states().values()) if (s.shock || s.action == SHOCK_EQUIP || s.action == SHOCK_UNEQUIP) { worn = true; break; }
        if (!worn && SHOCKS.isEmpty() && BOLTS.isEmpty() && SPLASHES.isEmpty() && MOTES.isEmpty()) return;
        float partial = e.getPartialTick(), time = mc.level.getGameTime() + partial;
        PoseStack p = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        p.pushPose();
        p.translate(-cam.x, -cam.y, -cam.z);
        var mv = RenderSystem.getModelViewStack(); mv.pushPose(); mv.last().pose().identity(); RenderSystem.applyModelViewMatrix();
        var rotation = e.getCamera().rotation();
        var rr = new Vector3f(1, 0, 0).rotate(rotation); var uu = new Vector3f(0, 1, 0).rotate(rotation);
        var fx = FilmFx.batched();
        FilmContext c = new FilmContext(p, fx, cam, new Vec3(rr.x, rr.y, rr.z), new Vec3(uu.x, uu.y, uu.z), time, 0, partial);
        try {
            for (Splash s : SPLASHES) {
                float k = (time - s.start()) / s.life();
                if (k < 0 || k > 1) continue;
                splash(c, s.at().add(s.normal().scale(.025)), s.normal(), s.radius() * (1 + .1f * k), CYAN, s.alpha() * (1 - k) * (1 - k));
            }
            for (var en : BatmanClient.states().entrySet()) {
                BatmanClient.State s = en.getValue();
                if (!(s.shock || s.action == SHOCK_EQUIP || s.action == SHOCK_UNEQUIP)) continue;
                Entity batman = mc.level.getEntity(en.getKey());
                if (batman == null) continue;
                gauntletLight(mc, c, RIGS.computeIfAbsent(en.getKey(), Rig::new), s, batman, time, partial);
            }
            for (Shock s : SHOCKS) shock(mc, c, s, time, partial);
            for (Bolt b : BOLTS) {
                float k = (time - b.start()) / b.life();
                if (k < 0 || k > 1) continue;
                int seed = b.seed() + (int) Math.floor(time * 3);
                arc(c, ThorBolts.bolt(b.a(), b.b(), seed, .4, .35, 1), b.width(), (1 - k) * (.6f + .4f * (float) FilmFx.hash(seed)));
            }
            for (Mote m : MOTES) {
                float age = (time - m.start) / m.life;
                if (age < 0 || age > 1) continue;
                Vec3 at = m.pos.add(m.vel.scale(partial));
                switch (m.kind) {
                    case M_SPARK -> FilmFx.streak(c, at.subtract(m.vel.scale(1.4)), at, .016, m.rgb, 0, m.alpha * (1 - age), true);
                    case M_PUFF -> FilmFx.puff(c, at, m.size + m.grow * (time - m.start), m.rgb, m.alpha * (1 - age) * Math.min(1, age * 5));
                    default -> FilmFx.glow(c, at, m.size * (1 + .4f * age), m.rgb, m.alpha * (1 - age) * (1 - age));
                }
            }
            fx.endBatch();
        } finally {
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            p.popPose();
        }
    }
    /**
     * One worn pair's light: a small bloom on each knuckle and a soft dim one over the hand and forearm, a very dim
     * volume over his chest and cape (not seen from inside his own head), the flashes, the faint splash on the faces
     * round him; the electric trail behind a striking fist.
     */
    private static void gauntletLight(Minecraft mc, FilmContext c, Rig r, BatmanClient.State s, Entity batman, float time, float partial) {
        int action = s.action;
        float t = BatmanClient.clock(s, partial);
        boolean self = batman.getId() == me() && mc.options.getCameraType().isFirstPerson();
        float sum = 0, flash = 0;
        Vec3 mid = null;
        for (int side = 0; side < 2; side++) {
            float lv = level(s, r.id, action, t, side, time), fl = flashK(r.flash[side], time) * r.flashPower[side];
            sum += lv; flash = Math.max(flash, fl);
            Vec3 k = knuckle(r, batman, side, partial);
            if (k == null) continue;
            mid = mid == null ? k : mid.add(k).scale(.5);
            if (lv > .01f) {
                FilmFx.glow(c, k, .12 + .06 * lv, PALE, Math.min(.5f, .3f * lv));
                FilmFx.glow(c, k, self ? .35 : .55, TURQ, .07f * lv);
                Vec3 cuff = fresh(r) ? r.cuff[side] : null;
                if (cuff != null && !self) FilmFx.glow(c, k.lerp(cuff, .6), .6, TEAL, .04f * lv);
            }
            if (fl > .01f) {
                FilmFx.glow(c, k, .2 + .18 * fl, CORE, Math.min(1, .7f * fl));
                FilmFx.glow(c, k, .8 + .35 * fl, CYAN, Math.min(.5f, .22f * fl));
            }
            trail(c, r, s, action, t, side, time);
        }
        float avg = sum * .5f;
        if (avg <= .01f && flash <= .01f) return;
        // The light on him: hands, forearms, chest, the cape, the lower face; kept dim (Batman stays dark).
        Vec3 body = batman.getPosition(partial);
        if (!self) FilmFx.glow(c, body.add(0, 1.15, 0), 1.3, TEAL, Math.min(.2f, .03f * avg + .07f * flash));
        // The faces round him: once a tick a few short rays find them; the steady light is faint, the flashes bright.
        Vec3 src = mid != null ? mid : body.add(0, 1.1, 0);
        long tick = mc.level.getGameTime();
        if (r.raysAt != tick && mc.player != null) {
            r.raysAt = tick;
            r.rays.clear();
            float yaw = batman.getYRot() * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)), down = new Vec3(0, -1, 0);
            Vec3[] dirs = {down, fwd.scale(.8).add(down).normalize(), fwd.scale(-.6).add(down).normalize(), right.add(down.scale(.3)).normalize(),
                    right.scale(-1).add(down.scale(.3)).normalize(), fwd, fwd.scale(-1), new Vec3(0, 1, 0)};
            for (Vec3 d : dirs) {
                BlockHitResult hit = mc.level.clip(new ClipContext(src, src.add(d.scale(LIGHT_REACH)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                if (hit.getType() == HitResult.Type.MISS) continue;
                r.rays.add(new Surface(hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getNormal()), hit.getLocation().distanceTo(src)));
            }
        }
        for (Surface sf : r.rays) {
            float d = (float) sf.distance();
            float alpha = (.09f * avg + .45f * Math.min(1.5f, flash)) / (1 + .28f * d * d);
            if (alpha < .01f) continue;
            splash(c, sf.at().add(sf.normal().scale(.025)), sf.normal(), .8 + .45 * d + .3 * Math.min(1, flash), CYAN, Math.min(.7f, alpha));
        }
    }
    /** The electric trail round a striking fist: its path over the last ticks, with a crackling arc along it. */
    private static void trail(FilmContext c, Rig r, BatmanClient.State s, int action, float t, int side, float time) {
        if (action != SHOCK_PUNCH || !powered(s) || BatmanShock.dry(s.combo)) return;
        int b = BatmanShock.blow(s.combo);
        if (BLOW_SIDE[b] != side) return;
        float lt = local(t, s.combo), h = BLOW_HIT[b];
        float w = FilmFx.window(lt, h * .45f, h + 1.2f, .8f);
        if (w <= .01f) return;
        Vec3 last = null;
        int n = 0;
        Vec3[] pts = new Vec3[TRAIL];
        for (int i = 0; i < TRAIL; i++) {
            int j = Math.floorMod(r.trailHead[side] - 1 - i, TRAIL);
            Vec3 pt = r.trail[side][j];
            if (pt == null || time - r.trailT[side][j] > 2.4f) break;
            pts[n++] = pt;
        }
        for (int i = 0; i + 1 < n; i++) {
            float a0 = w * (1 - i / (float) n), a1 = w * (1 - (i + 1) / (float) n);
            FilmFx.streak(c, pts[i + 1], pts[i], .07, TURQ, a1 * .35f, a0 * .35f, true);
            FilmFx.streak(c, pts[i + 1], pts[i], .022, CORE, a1 * .8f, a0 * .8f, true);
            last = pts[i + 1];
        }
        if (last != null && n > 2) {
            int seed = r.id * 31 + side * 7 + (int) Math.floor(time * 3);
            arc(c, ThorBolts.bolt(pts[0], last, seed, .35, .25, 0), .022f, .8f * w);
        }
    }
    /** Electricity over a body hit by a charged blow: arcs between points of its outline, crawling, flickering, fading. */
    private static void shock(Minecraft mc, FilmContext c, Shock s, float time, float partial) {
        float k = (time - s.start()) / SHOCK_LIFE;
        if (k < 0 || k > 1) return;
        Entity body = mc.level.getEntity(s.entity());
        if (body == null) return;
        Vec3 feet = body.getPosition(partial);
        AABB box = body.getBoundingBox().move(feet.subtract(body.position()));
        float fade = (1 - k) * (1 - k * .4f);
        int bucket = (int) Math.floor(time * 3.2f);
        double height = box.getYsize();
        FilmFx.glow(c, box.getCenter(), height * .75, CYAN, .2f * fade * (.75f + .25f * Mth.sin(time * 5.3f)));
        int n = (int) (2 + 6 * fade * s.power());
        for (int i = 0; i < n; i++) {
            int seed = s.seed() * 31 + i * 977 + bucket;
            if (FilmFx.hash(seed * .13) > .25 + .75 * fade) continue;
            Vec3 a = surface(box, FilmFx.hash(seed * 1.1), FilmFx.hash(seed * 2.3), FilmFx.hash(seed * 3.7));
            Vec3 b = surface(box, FilmFx.hash(seed * 4.1), FilmFx.hash(seed * 5.9), FilmFx.hash(seed * 6.7));
            if (a.distanceTo(b) > height * .8) b = a.lerp(b, .45);
            arc(c, ThorBolts.bolt(a, b, seed, .45, .3, 1), .02f * s.power(), .85f * fade);
        }
        // The first moments: arcs from the contact branching round them and up into the air.
        if (k < .35f) {
            Vec3 contact = feet.add(s.contact());
            float early = 1 - k / .35f;
            for (int i = 0; i < 3; i++) {
                int seed = s.seed() * 17 + i * 331 + bucket;
                Vec3 to = i == 0 ? new Vec3(box.getCenter().x, box.maxY + .4 + FilmFx.hash(seed) * .6, box.getCenter().z)
                        : surface(box, FilmFx.hash(seed * 1.7), FilmFx.hash(seed * 2.9), FilmFx.hash(seed * 4.3));
                arc(c, ThorBolts.bolt(contact, to, seed, .4, .4, 1), .028f * s.power(), early);
            }
        }
    }
    /** An arc in the world, camera-facing: turquoise glow, cyan body, white core. */
    private static void arc(FilmContext c, List<ThorBolts.Seg> segs, double width, float alpha) {
        if (alpha <= .01f) return;
        VertexConsumer v = c.buffers().getBuffer(FilmFx.ADD);
        Matrix4f m = c.pose().last().pose();
        Vec3 cam = c.camera();
        for (ThorBolts.Seg s : segs) {
            double w = width * s.width();
            ThorBolts.ribbon(v, m, s.a(), s.b(), cam, w * 3.4, TEAL, alpha * .22f);
            ThorBolts.ribbon(v, m, s.a(), s.b(), cam, w * 1.4, CYAN, alpha * .7f);
            ThorBolts.ribbon(v, m, s.a(), s.b(), cam, w * .5, CORE, alpha);
        }
    }
    /** A disc of light lying on a surface (normal n), bright in the middle, fading out to its rim. */
    private static void splash(FilmContext c, Vec3 at, Vec3 n, double radius, int rgb, float alpha) {
        if (alpha <= .004f || radius <= 0) return;
        VertexConsumer v = c.buffers().getBuffer(FilmFx.ADD);
        Matrix4f m = c.pose().last().pose();
        Vec3 u = Math.abs(n.y) < .9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 w = n.cross(u);
        float r = (rgb >> 16 & 255) / 255f, gg = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f, mid = alpha * .38f;
        double near = radius * .38;
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            Vec3 d0 = u.scale(Math.cos(a0)).add(w.scale(Math.sin(a0))), d1 = u.scale(Math.cos(a1)).add(w.scale(Math.sin(a1)));
            vert(v, m, at, r, gg, b, alpha); vert(v, m, at, r, gg, b, alpha);
            vert(v, m, at.add(d1.scale(near)), r, gg, b, mid); vert(v, m, at.add(d0.scale(near)), r, gg, b, mid);
            vert(v, m, at.add(d0.scale(near)), r, gg, b, mid); vert(v, m, at.add(d1.scale(near)), r, gg, b, mid);
            vert(v, m, at.add(d1.scale(radius)), r, gg, b, 0); vert(v, m, at.add(d0.scale(radius)), r, gg, b, 0);
        }
    }
    private static void vert(VertexConsumer v, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        v.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, Mth.clamp(a, 0, 1)).endVertex();
    }

    // ------------------------------------------------------------------ the target trembling
    /** Someone shocked trembles for a moment (a small jitter of the whole body, fading). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void pre(RenderLivingEvent.Pre<?, ?> e) {
        if (SHOCKS.isEmpty()) return;
        LivingEntity en = e.getEntity();
        float now = now(), k = -1, power = 1;
        for (Shock s : SHOCKS) if (s.entity() == en.getId()) { float a = (now - s.start()) / SHOCK_LIFE; if (a >= 0 && a < 1 && (k < 0 || a < k)) { k = a; power = s.power(); } }
        if (k < 0) return;
        float f = (1 - k) * (1 - k) * power;
        PoseStack p = e.getPoseStack();
        p.pushPose();
        PUSHED.add(en.getId());
        float w = now * 9.1f + en.getId();
        p.translate(.035f * f * Mth.sin(w * 1.3f), .012f * f * Mth.sin(w * 2.1f + 1), .035f * f * Mth.cos(w * 1.7f));
        p.mulPose(Axis.ZP.rotation(.05f * f * Mth.sin(w * 1.1f + 2)));
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void post(RenderLivingEvent.Post<?, ?> e) {
        if (PUSHED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ his own view: walk, wash, the energy bar
    /** A heavy blow plants him (he can only shuffle while it is thrown); locking on and taking off, a slow walk. */
    @SubscribeEvent public static void walk(MovementInputUpdateEvent e) {
        var mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player || mc.player == null) return;
        BatmanClient.State s = BatmanClient.get(mc.player);
        if (s == null || !(s.action == SHOCK_PUNCH || s.action == SHOCK_EQUIP || s.action == SHOCK_UNEQUIP)) return;
        float k = s.action == SHOCK_PUNCH ? PUNCH_WALK : EQUIP_WALK;
        var in = e.getInput();
        in.forwardImpulse *= k;
        in.leftImpulse *= k;
        if (s.action == SHOCK_PUNCH) mc.player.setSprinting(false);
    }
    /** The short cyan wash of the clap and of a hit over his own view (the impact frame). */
    @SubscribeEvent public static void wash(RenderGuiEvent.Pre e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || FilmDirector.playing()) return;
        float k = washNow(now());
        if (k < .005f) return;
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        e.getGuiGraphics().fill(0, 0, w, h, HudStyle.alpha(0xFF6FE9FF, Math.min(.16f, k)));
    }
    /**
     * The energy bar on the right while they are worn (Batman UI: minimal, technical, dark): a gauntlet icon with a
     * bolt, the cyan charge filling a tall track (charging 0→100 as they lock on, READY after the clap), a hit drops it
     * at once (BAM: the bar flashes, the lost part stays lit a moment and runs down), low charge flickers, empty it
     * turns dark grey and shows the slow recharge in dim teal stripes.
     */
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || FilmDirector.playing() || !BatmanClient.isHero(mc.player)) { barShown = 0; barValue = -1; return; }
        BatmanClient.State s = BatmanClient.get(mc.player);
        if (s == null) return;
        float partial = e.getPartialTick(), now = mc.level.getGameTime() + partial, t = BatmanClient.clock(s, partial);
        float dt = barAt < 0 ? 0 : Mth.clamp(now - barAt, 0, 3);
        barAt = now;
        boolean want = s.shock || s.action == SHOCK_EQUIP;
        barShown = clamp01(barShown + (want ? dt / 5 : -dt / 4));
        if (barShown <= .01f) { barValue = -1; return; }
        boolean powered = powered(s);
        float target = s.action == SHOCK_EQUIP ? s.energy * k(t, CHARGE_FROM, CHARGE_TO) : s.energy;
        if (barValue < 0) { barValue = s.action == SHOCK_EQUIP ? 0 : target; barLost = barValue; }
        if (target < barValue - .012f) {
            // BAM: the drop is instant; the part lost stays lit and runs down after a moment.
            barLost = Math.max(barLost, barValue);
            barValue = target;
            dropAt = now;
            barBam = 1;
        } else barValue += (target - barValue) * (1 - (float) Math.exp(-dt * .35f));
        if (now - dropAt > 4) barLost += (barValue - barLost) * (1 - (float) Math.exp(-dt * .16f));
        barLost = Math.max(barLost, barValue);
        barBam *= (float) Math.exp(-dt * .28f);

        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        float a = barShown;
        int bh = 92, bw = 5, x = w - 20, top = h / 2 - bh / 2 + 6;
        // The panel: dark, a thin gunmetal edge, cyan corner marks.
        int px0 = x - 8, px1 = x + bw + 8, py0 = top - 24, py1 = top + bh + 22;
        g.fill(px0, py0, px1, py1, HudStyle.alpha(0xFF05080A, .62f * a));
        int edge = HudStyle.alpha(0xFF2B3238, .9f * a);
        g.fill(px0, py0, px1, py0 + 1, edge); g.fill(px0, py1 - 1, px1, py1, edge);
        g.fill(px0, py0, px0 + 1, py1, edge); g.fill(px1 - 1, py0, px1, py1, edge);
        int mark = HudStyle.alpha(powered ? 0xFF48E2FF : 0xFF5A6670, .9f * a);
        for (int i = 0; i < 2; i++) {
            int yy = i == 0 ? py0 : py1 - 1, dy = i == 0 ? 1 : -1;
            g.fill(px0, yy, px0 + 4, yy + 1, mark); g.fill(px0, yy, px0 + 1, yy + 4 * dy + (dy < 0 ? 1 : 0), mark);
            g.fill(px1 - 4, yy, px1, yy + 1, mark); g.fill(px1 - 1, yy, px1, yy + 4 * dy + (dy < 0 ? 1 : 0), mark);
        }
        icon(g, x + bw / 2, top - 14, powered, a, now);
        // The track, its notches (every tenth, longer at a quarter and a half).
        g.fill(x - 1, top - 1, x + bw + 1, top + bh + 1, HudStyle.alpha(0xFF101519, .95f * a));
        for (int i = 1; i < 10; i++) {
            int yy = top + bh - Math.round(bh * i / 10f), len = i == 5 ? 3 : 2;
            g.fill(x - 1 - len, yy, x - 1, yy + 1, HudStyle.alpha(0xFF3A444C, .9f * a));
        }
        int lowY = top + bh - Math.round(bh * .25f);
        g.fill(x - 4, lowY, x - 1, lowY + 1, HudStyle.alpha(0xFF7A8A94, .9f * a));
        // The lost part (lit white-cyan, fading as it runs down).
        int fillTop = top + bh - Math.round(bh * clamp01(barValue)), lostTop = top + bh - Math.round(bh * clamp01(barLost));
        if (lostTop < fillTop) g.fill(x, lostTop, x + bw, fillTop, HudStyle.alpha(0xFFE8FFFF, (.35f + .5f * barBam) * a));
        // The charge.
        if (powered) {
            float flicker = barValue < .25f ? .55f + .45f * (FilmFx.hash(Math.floor(now / 1.5f) * 3.3) < .7 ? 1 : 0) : 1;
            int col = HudStyle.alpha(0xFF2FD8F2, a * flicker);
            g.fill(x, fillTop, x + bw, top + bh, col);
            g.fill(x + 1, fillTop, x + 2, top + bh, HudStyle.alpha(0xFFB8F8FF, .55f * a * flicker));
            if (fillTop < top + bh) g.fill(x - 1, fillTop - 1, x + bw + 1, fillTop + 1, HudStyle.alpha(0xFFF0FFFF, .9f * a * flicker));
            // A faint glow at the bar's sides.
            g.fill(x - 2, fillTop, x - 1, top + bh, HudStyle.alpha(0xFF2FD8F2, .18f * a));
            g.fill(x + bw + 1, fillTop, x + bw + 2, top + bh, HudStyle.alpha(0xFF2FD8F2, .18f * a));
        } else {
            // Off: the slow recharge in dim teal, stripes running up it.
            g.fill(x, fillTop, x + bw, top + bh, HudStyle.alpha(0xFF145A66, .9f * a));
            int phase = (int) (now * .8f) % 6;
            for (int yy = top + bh - 1 - phase; yy >= fillTop; yy -= 6) g.fill(x, Math.max(fillTop, yy - 1), x + bw, yy + 1, HudStyle.alpha(0xFF2A8C9C, .8f * a));
        }
        if (barBam > .02f) g.fill(x - 1, top - 1, x + bw + 1, top + bh + 1, HudStyle.alpha(0xFFFFFFFF, .3f * barBam * a));
        // The number and what it means.
        String pct = Math.round(clamp01(barValue) * 100) + "%";
        g.pose().pushPose();
        g.pose().translate(x + bw / 2f, top + bh + 5, 0);
        g.pose().scale(.75f, .75f, 1);
        int tw = font.width(pct);
        g.drawString(font, pct, -tw / 2, 0, HudStyle.alpha(powered ? 0xFFDFF8FF : 0xFF8A969E, a), false);
        String status = !powered ? (s.energy > 0 ? "ŞARJ" : "GÜÇ YOK") : now - readyAt < 40 ? "HAZIR" : s.action == SHOCK_EQUIP ? "ŞARJ" : barValue < .25f ? "DÜŞÜK" : "";
        if (!status.isEmpty()) {
            int col = status.equals("HAZIR") ? 0xFF7FF2FF : status.equals("DÜŞÜK") ? 0xFFFFC46A : 0xFF8A969E;
            float blink = status.equals("HAZIR") ? .65f + .35f * Mth.sin(now * .6f) : 1;
            int sw = font.width(status);
            g.drawString(font, status, -sw / 2, 11, HudStyle.alpha(col, a * blink), false);
        }
        g.pose().popPose();
    }
    /** The small gauntlet icon: a fist with its knuckle plate, a bolt across it (cyan when charged, grey when off). */
    private static void icon(GuiGraphics g, int cx, int cy, boolean powered, float a, float now) {
        int body = HudStyle.alpha(0xFF9AA6AE, .95f * a), plate = HudStyle.alpha(0xFFCBD5DB, .95f * a);
        int bolt = HudStyle.alpha(powered ? 0xFF48E2FF : 0xFF4A545C, a * (powered ? .85f + .15f * Mth.sin(now * .9f) : 1));
        g.fill(cx - 4, cy - 2, cx + 3, cy + 5, body);       // the fist
        g.fill(cx - 5, cy - 4, cx + 4, cy - 2, plate);      // the knuckle plate
        g.fill(cx - 3, cy + 5, cx + 2, cy + 7, body);       // the bracer
        for (int i = 0; i < 4; i++) g.fill(cx - 4 + i * 2, cy - 5, cx - 3 + i * 2, cy - 4, bolt);  // the emitters
        // The bolt.
        g.fill(cx + 1, cy - 1, cx + 3, cy, bolt); g.fill(cx, cy, cx + 2, cy + 1, bolt); g.fill(cx - 1, cy + 1, cx + 3, cy + 2, bolt);
        g.fill(cx, cy + 2, cx + 2, cy + 3, bolt); g.fill(cx - 1, cy + 3, cx + 1, cy + 4, bolt);
    }

    // ------------------------------------------------------------------ his body
    /** SHOCK_EQUIP / SHOCK_UNEQUIP / SHOCK_PUNCH poses at clock t over the base (BatmanMotion.sample; combo = the blow). */
    static Pose pose(int action, Pose base, float t, int combo, float time) {
        return switch (action) {
            case SHOCK_EQUIP -> equip(base, t, time);
            case SHOCK_UNEQUIP -> unequip(base, t, time);
            case SHOCK_PUNCH -> blow(base, combo, t, time);
            default -> base;
        };
    }
    private static Pose arm(Pose p, int side, float shFwd, float shUp, float ax, float ay, float az, float elbow) {
        return p.arm(side, SH_FWD, shFwd).arm(side, SH_UP, shUp).arm(side, ARM_X, ax).arm(side, ARM_Y, ay).arm(side, ARM_Z, az).arm(side, ELBOW, elbow).arm(side, CURL, 1);
    }
    /**
     * The boxer's stance while they are worn, over the plain stance: feet wider, the left ahead, knees bent, the torso
     * a little forward and turned behind the lead shoulder, shoulders raised to guard the chin, the right fist at the
     * cheek, the left a little ahead at face height; a slow bounce on the balls of the feet, a weave, the guard drifting
     * in small circles. (Arm angles solved against a forward-kinematics copy of BatmanBody.) In the air it blends out.
     */
    static Pose stance(Pose base, float time) {
        Pose p = base.copy();
        float breath = base.get(CHEST_PITCH) + .03f, lookYaw = base.get(HEAD_YAW), lookPitch = base.get(HEAD_PITCH) - .03f;
        p.set(CROUCH, 2.6f).set(SPINE_PITCH, .2f).set(CHEST_PITCH, .06f + breath).set(PELVIS_YAW, .28f).set(CHEST_YAW, .1f).set(SPINE_YAW, 0)
                .set(HEAD_YAW, -.34f + lookYaw).set(HEAD_PITCH, -.12f + lookPitch).set(NECK, .55f);
        p.leg(1, LEG_X, -.34f).leg(0, LEG_X, .26f).leg(1, LEG_Z, .2f).leg(0, LEG_Z, .22f).leg(1, KNEE, .2f).leg(0, KNEE, .12f)
                .leg(0, ANKLE, .12f).leg(0, LEG_Y, .2f).leg(1, LEG_Y, .05f).leg(1, ANKLE, 0);
        arm(p, 0, .6f, .5f, -1.55f, -.93f, .42f, 1.81f).arm(0, WRIST_X, .1f);
        arm(p, 1, .9f, .4f, -1.42f, -.08f, .13f, 1.88f).arm(1, WRIST_X, .1f);
        // Alive: the bounce, the weave, the guard circling a little.
        float bounce = Mth.sin(time * .2f), weave = Mth.sin(time * .1f);
        p.add(CROUCH, .45f * bounce).add(SHIFT_X, .55f * weave).add(CHEST_YAW, .05f * Mth.sin(time * .1f + .8f))
                .add(PELVIS_ROLL, -.03f * weave).add(SPINE_ROLL, .03f * weave);
        p.legAdd(0, KNEE, .06f * Math.max(0, -weave)).legAdd(1, KNEE, .06f * Math.max(0, weave));
        for (int side = 0; side < 2; side++) {
            float cc = Mth.sin(time * .13f + side * 1.9f), dd = Mth.cos(time * .13f + side * 1.9f);
            p.armAdd(side, ARM_X, .04f * cc).armAdd(side, ELBOW, .05f * dd).armAdd(side, SH_UP, .2f * bounce);
        }
        Pose out = base.copy();
        out.toward(p, clamp(base.get(PLANT)));
        return out;
    }
    /** Turns on the legs for a blow of the right hand (the back foot pivots, the heel up) or the left (the lead foot steps). */
    private static void feet(Pose p, int side, boolean hook) {
        if (side == 0) p.leg(0, ANKLE, .45f).leg(0, LEG_Y, -.1f).legAdd(1, KNEE, .1f);
        else {
            p.leg(1, LEG_X, -.48f).leg(0, ANKLE, .25f);
            if (hook) p.leg(1, LEG_Y, -.3f).leg(1, ANKLE, .3f);
        }
    }
    /**
     * One heavy blow over the guard (base = the boxer's stance): load (the hand drawn back or down, the body wound
     * against it, the weight settling), a deeper wind, the strike with the hips, torso, shoulder and a step behind it
     * (full extension at BLOW_HIT), a short follow-through, the recovery back into the guard. Every blow is different.
     * Arm angles solved against the forward-kinematics copy (fists on the target's head or body line, about a block ahead).
     */
    private static Pose blow(Pose g, int combo, float t, float time) {
        int b = BatmanShock.blow(combo), side = BLOW_SIDE[b];
        float lt = local(t, combo), h = BLOW_HIT[b], len = BLOW_TICKS[b];
        Pose load = g.copy(), hit = g.copy(), through;
        switch (b) {
            case BatmanShock.S_CROSS -> {
                // From out on his left across to his right: the fist sweeps left to right through the target (solved).
                load.set(CHEST_YAW, -.3f).set(PELVIS_YAW, 0).set(SHIFT_Z, .7f).set(CROUCH, 3.2f).set(SPINE_PITCH, .24f).set(HEAD_YAW, -.1f);
                arm(load, 1, .3f, .5f, -1.34f, -.05f, .17f, 2.11f);
                hit.set(CHEST_YAW, .9f).set(SPINE_YAW, .2f).set(PELVIS_YAW, .58f).set(SHIFT_Z, -2.3f).set(CROUCH, 2.9f).set(SPINE_PITCH, .24f).set(HEAD_YAW, -1f);
                arm(hit, 1, 2.4f, .5f, -2.13f, 2.08f, .2f, .2f).arm(1, WRIST_X, 0);
                arm(hit, 0, .6f, .5f, -2.45f, -1.07f, 1.23f, 1.72f);
                feet(hit, 1, false);
                through = hit.copy().set(CHEST_YAW, 1.04f).set(SPINE_YAW, .24f).set(PELVIS_YAW, .62f).add(SHIFT_Z, -.2f);
                arm(through, 1, 2.4f, .5f, -2.13f, 2.03f, .24f, .35f);
            }
            case BatmanShock.S_UPPER -> {
                load.set(CROUCH, 4.8f).set(SPINE_PITCH, .36f).set(CHEST_YAW, .4f).set(PELVIS_YAW, .42f).set(SPINE_ROLL, -.14f).set(SHIFT_Z, .3f).set(HEAD_YAW, -.6f);
                arm(load, 0, -.3f, 0, -.03f, -1.24f, -.6f, 1.7f).arm(0, WRIST_X, -.3f);
                hit.set(CROUCH, .9f).set(LIFT, .5f).set(SPINE_PITCH, -.06f).set(CHEST_PITCH, -.12f).set(CHEST_YAW, -.5f).set(SPINE_YAW, -.1f).set(PELVIS_YAW, -.05f)
                        .set(SHIFT_Z, -1.4f).set(SPINE_ROLL, .05f).set(HEAD_PITCH, -.25f).set(HEAD_YAW, .45f).set(PLANT, .85f);
                arm(hit, 0, 1.6f, 1f, -1.21f, .27f, .08f, 1.16f).arm(0, WRIST_X, .15f);
                arm(hit, 1, .9f, .4f, -1.18f, -.86f, .24f, 1.35f);
                hit.leg(0, ANKLE, .45f).leg(1, ANKLE, .3f);
                through = hit.copy().add(LIFT, .3f);
                arm(through, 0, 1.6f, 1.3f, -1.45f, .24f, .08f, 1.09f).arm(0, WRIST_X, .15f);
            }
            case BatmanShock.S_HOOK -> {
                load.set(CHEST_YAW, -.42f).set(SPINE_YAW, -.12f).set(PELVIS_YAW, .05f).set(CROUCH, 3.4f).set(SPINE_PITCH, .24f).set(SHIFT_Z, -.3f).set(SHIFT_X, .8f).set(HEAD_YAW, .3f);
                arm(load, 1, .3f, .8f, -1.07f, .9f, 1.55f, 1.55f);
                hit.set(CHEST_YAW, .78f).set(SPINE_YAW, .2f).set(PELVIS_YAW, .55f).set(CROUCH, 3f).set(SPINE_PITCH, .22f).set(SHIFT_Z, -1.3f).set(SHIFT_X, -.4f)
                        .set(SPINE_ROLL, -.06f).set(HEAD_YAW, -1.05f);
                arm(hit, 1, 1.2f, .9f, 1.13f, .01f, 2.43f, 1.16f);
                arm(hit, 0, .6f, .5f, -2.93f, -1.06f, 1.54f, 1.26f);
                feet(hit, 1, true);
                through = hit.copy().add(CHEST_YAW, .2f).add(SPINE_YAW, .06f);
            }
            case BatmanShock.S_WIDE -> {
                load.set(CHEST_YAW, .56f).set(SPINE_YAW, .14f).set(PELVIS_YAW, .45f).set(CROUCH, 3.3f).set(SPINE_PITCH, .22f).set(SHIFT_Z, .4f).set(SHIFT_X, -.8f).set(HEAD_YAW, -.8f);
                arm(load, 0, -.4f, .8f, -1.86f, -.01f, .72f, 1.23f);
                hit.set(CHEST_YAW, -.85f).set(SPINE_YAW, -.22f).set(PELVIS_YAW, -.2f).set(CROUCH, 2.9f).set(SPINE_PITCH, .22f).set(SHIFT_Z, -1.5f).set(SHIFT_X, .5f)
                        .set(SPINE_ROLL, .07f).set(HEAD_YAW, .85f);
                arm(hit, 0, 1.3f, .9f, .95f, .21f, 2.6f, 1.43f);
                arm(hit, 1, .9f, .4f, -2.71f, -1.13f, 1.37f, 1.45f);
                feet(hit, 0, true);
                through = hit.copy().add(CHEST_YAW, -.2f).add(SPINE_YAW, -.06f);
            }
            case BatmanShock.S_LOW -> {
                load.set(CROUCH, 4.9f).set(SPINE_PITCH, .34f).set(CHEST_YAW, -.45f).set(SPINE_YAW, -.1f).set(PELVIS_YAW, .06f).set(SPINE_ROLL, -.1f).set(SHIFT_X, .9f).set(HEAD_YAW, .35f);
                arm(load, 1, .2f, .3f, -.51f, -.23f, .43f, 2.2f);
                hit.set(CROUCH, 4.6f).set(SPINE_PITCH, .32f).set(CHEST_YAW, .72f).set(SPINE_YAW, .18f).set(PELVIS_YAW, .5f).set(SHIFT_Z, -1.2f).set(SPINE_ROLL, .08f)
                        .set(SHIFT_X, -.4f).set(HEAD_YAW, -.95f);
                arm(hit, 1, 1.2f, .4f, 1.45f, .56f, 2.08f, 2.33f);
                arm(hit, 0, .6f, .5f, -2.59f, -1.09f, 1.24f, 1.64f);
                feet(hit, 1, true);
                hit.legAdd(0, KNEE, .2f).legAdd(1, KNEE, .2f);
                through = hit.copy().add(CHEST_YAW, .18f);
            }
            case BatmanShock.S_BODY -> {
                load.set(CROUCH, 4.2f).set(SPINE_PITCH, .36f).set(CHEST_YAW, .34f).set(PELVIS_YAW, .38f).set(SHIFT_Z, .8f).set(HEAD_YAW, -.55f);
                arm(load, 0, -.6f, 0, -.32f, -1.17f, .31f, 2.43f);
                hit.set(CROUCH, 3.8f).set(SPINE_PITCH, .38f).set(CHEST_YAW, -.6f).set(SPINE_YAW, -.15f).set(PELVIS_YAW, -.15f).set(SHIFT_Z, -2.6f).set(HEAD_YAW, .6f);
                arm(hit, 0, 2.2f, .3f, -1.36f, .28f, -.38f, 2.13f);
                arm(hit, 1, .9f, .4f, -2.87f, -1.19f, 1.61f, 2.11f);
                feet(hit, 0, false);
                hit.leg(1, LEG_X, -.45f);
                through = hit.copy().add(CHEST_YAW, -.1f).add(SHIFT_Z, -.2f);
            }
            case BatmanShock.S_OVER -> {
                load.set(CHEST_YAW, -.4f).set(SPINE_YAW, -.1f).set(PELVIS_YAW, .1f).set(CROUCH, 3f).set(SPINE_PITCH, .08f).set(SHIFT_Z, .6f).set(HEAD_YAW, .3f);
                arm(load, 1, -.2f, 1.2f, -2.25f, 1.03f, .48f, .86f);
                hit.set(CHEST_YAW, .74f).set(SPINE_YAW, .18f).set(PELVIS_YAW, .5f).set(CROUCH, 3.4f).set(SPINE_PITCH, .4f).set(CHEST_PITCH, .1f).set(SHIFT_Z, -2f)
                        .set(HEAD_YAW, -.95f).set(HEAD_PITCH, -.3f);
                arm(hit, 1, 2f, .8f, -3.15f, .12f, -.4f, 1.13f);
                arm(hit, 0, .6f, .5f, -2.97f, -.99f, 1.78f, 1.85f);
                feet(hit, 1, false);
                through = hit.copy().add(SPINE_PITCH, .08f).add(CROUCH, .4f);
                arm(through, 1, 2f, .8f, -2.58f, .02f, -.57f, 2.11f);
            }
            default -> {
                // The heavy right straight.
                // From out on his right across to his left: the fist sweeps right to left through the target (solved).
                load.set(CHEST_YAW, .5f).set(PELVIS_YAW, .42f).set(SHIFT_Z, .9f).set(CROUCH, 3.3f).set(SPINE_PITCH, .24f).set(HEAD_YAW, -.52f);
                arm(load, 0, -.2f, .6f, -1.5f, -.71f, .26f, 1.68f);
                hit.set(CHEST_YAW, -.78f).set(SPINE_YAW, -.2f).set(PELVIS_YAW, -.24f).set(SHIFT_Z, -2.4f).set(CROUCH, 2.8f).set(SPINE_PITCH, .24f).set(HEAD_YAW, .6f);
                arm(hit, 0, 2.4f, .5f, -2.85f, .42f, -.36f, .2f).arm(0, WRIST_X, 0);
                arm(hit, 1, .9f, .4f, -2.08f, -1.11f, .9f, 1.98f);
                feet(hit, 0, false);
                hit.leg(1, LEG_X, -.42f);
                through = hit.copy().set(CHEST_YAW, -.92f).set(SPINE_YAW, -.24f).set(PELVIS_YAW, -.3f).add(SHIFT_Z, -.2f);
                arm(through, 0, 2.4f, .5f, -2.72f, .38f, -.23f, .35f);
            }
        }
        // The deeper wind before the release, and the way back (still turned, the hand coming home).
        Pose deep = load.copy().add(CROUCH, .3f).add(CHEST_YAW, side == 0 ? .06f : -.06f).add(SHIFT_Z, .15f);
        Pose back = g.copy();
        back.toward(through, .3f);
        Pose p = new Track().key(0, g).key(h * .45f, load).key(h * .78f, deep).key(h, hit).key(h + 1.4f, through).key(h + (len - h) * .6f, back).key(len, g).sample(lt);
        // Held through the hit-stop: the striking arm shudders with the discharge.
        if (BatmanShock.landed(combo) && lt >= h && lt < h + 1.2f) {
            float s = 1 - (lt - h) / 1.2f;
            p.armAdd(side, ELBOW, .05f * s * Mth.sin(time * 9.1f)).armAdd(side, ARM_X, .03f * s * Mth.sin(time * 11.3f + 1)).add(CHEST_ROLL, .02f * s * Mth.sin(time * 8.3f));
        }
        return p;
    }
    /**
     * Locking on (the base is already the boxer's stance; it starts from the plain one): the hands drop a little down
     * and out before him, the head dips to them, the fists close as the plates seat (small jolts at each lock), the
     * shutters open, the charge spreads through both arms (a building tremor, the shoulders rising), the fists are drawn
     * apart and slammed together at SHOCK_CLAP (pressed a moment, trembling), then he settles into the boxer's guard.
     */
    private static Pose equip(Pose base, float t, float time) {
        Pose plain = BatmanMotion.stance(time);
        Pose look = plain.copy().set(CROUCH, 1.2f).set(SPINE_PITCH, .12f).add(HEAD_PITCH, .4f);
        for (int side = 0; side < 2; side++) arm(look, side, .5f, 0, .03f, -.39f, -.04f, 1.62f).arm(side, CURL, .55f).arm(side, WRIST_X, -.2f);
        Pose locked = look.copy();
        for (int side = 0; side < 2; side++) locked.arm(side, CURL, 1).arm(side, WRIST_X, .1f);
        Pose surge = locked.copy().add(CROUCH, .4f).add(HEAD_PITCH, -.1f);
        for (int side = 0; side < 2; side++) surge.armAdd(side, SH_UP, .45f).armAdd(side, ELBOW, .12f);
        Pose apart = plain.copy().set(CROUCH, 2.2f).set(SPINE_PITCH, .14f).set(CHEST_PITCH, -.04f).add(HEAD_PITCH, .05f);
        for (int side = 0; side < 2; side++) arm(apart, side, .4f, .5f, -.58f, .29f, .53f, 2.12f);
        Pose clap = plain.copy().set(CROUCH, 2.6f).set(SPINE_PITCH, .18f).add(HEAD_PITCH, .08f);
        for (int side = 0; side < 2; side++) arm(clap, side, 1f, .6f, -1.35f, -.25f, .74f, 1.55f);
        Pose press = clap.copy().add(CROUCH, .35f);
        for (int side = 0; side < 2; side++) press.armAdd(side, SH_UP, .15f);
        Pose p = new Track().key(0, plain).key(4, look).key(7, locked).key(11, locked).key(19, surge).key(23.2f, apart).key(SHOCK_CLAP, clap)
                .key(SHOCK_CLAP + 1.8f, press).key(SHOCK_EQUIP_TICKS, base).sample(t);
        // The locks: a small jolt of both wrists as each part seats.
        for (float at : new float[]{4.5f, 6.5f, 8.5f}) {
            float j = t - at;
            if (j < 0 || j > 2) continue;
            float pulse = (float) Math.exp(-j / .5f) * Mth.sin(j * 9);
            for (int side = 0; side < 2; side++) p.armAdd(side, WRIST_X, .12f * pulse).armAdd(side, ELBOW, .05f * pulse);
        }
        // The charge spreading: a tremor building in both arms.
        float build = k(t, CHARGE_FROM, CHARGE_TO) * (1 - k(t, SHOCK_CLAP - 3, SHOCK_CLAP - 1)) + .7f * FilmFx.window(t, SHOCK_CLAP, SHOCK_CLAP + 2.5f, .5f);
        if (build > 0) for (int side = 0; side < 2; side++)
            p.armAdd(side, ARM_X, .02f * build * Mth.sin(time * 7.1f + side)).armAdd(side, SH_UP, .14f * build * Mth.sin(time * 5.3f + side * 2));
        return p;
    }
    /**
     * Coming off (the base is the plain stance): out of the guard the hands come down before him, the head dipping to
     * them; the last discharge at DISCHARGE_AT jerks them, the hands open and shake out, the plates release, back to
     * the stance.
     */
    private static Pose unequip(Pose base, float t, float time) {
        Pose guard = stance(base, time);
        Pose low = base.copy().set(CROUCH, 1.3f).set(SPINE_PITCH, .1f).add(HEAD_PITCH, .35f);
        for (int side = 0; side < 2; side++) arm(low, side, .5f, 0, .03f, -.39f, -.04f, 1.62f).arm(side, CURL, .85f);
        Pose flick = low.copy();
        for (int side = 0; side < 2; side++) flick.arm(side, WRIST_X, -.5f).arm(side, CURL, .3f).arm(side, ELBOW, 1.35f);
        Pose release = low.copy();
        for (int side = 0; side < 2; side++) release.arm(side, CURL, .6f).arm(side, WRIST_X, .15f);
        Pose p = new Track().key(0, guard).key(4, low).key(DISCHARGE_AT, low).key(DISCHARGE_AT + 1.6f, flick).key(10.5f, release).key(SHOCK_UNEQUIP_TICKS, base).sample(t);
        float j = t - DISCHARGE_AT;
        if (j >= 0 && j < 2.5f) {
            float pulse = (float) Math.exp(-j / .6f) * Mth.sin(j * 10);
            for (int side = 0; side < 2; side++) p.armAdd(side, ELBOW, .12f * pulse).armAdd(side, SH_UP, .3f * pulse);
        }
        return p;
    }

    // ------------------------------------------------------------------ the gauntlets
    /** For the draw that follows (worn() is called just before each Batman is drawn): the rig, shutters, per-hand electricity and flash. */
    private static Rig drawing;
    private static float drawOpen, drawTime;
    private static boolean drawPowered;
    private static int drawStrike = -1;
    private static float drawReach;
    private static final float[] LEVEL = new float[2], FLASH = new float[2];

    /** How much of the gauntlets shows on his hands now, 0..1 (BatmanLayer copies it into BatmanBody.SHOCK). */
    static float worn(BatmanClient.State s, int action, float t) {
        drawing = null;
        LEVEL[0] = LEVEL[1] = FLASH[0] = FLASH[1] = 0;
        drawOpen = 0; drawPowered = false; drawStrike = -1; drawReach = 0;
        if (s == null) return 0;
        float w = wornAt(s, action, t);
        if (w <= 0) return 0;
        Rig r = rig(s);
        float now = now();
        drawing = r;
        drawTime = now;
        drawPowered = powered(s);
        int id = r == null ? 0 : r.id;
        float want = openWant(s, action, t);
        if (r != null) {
            // The shutters move (quickly), so a power cut closes them instead of snapping.
            float dt = r.openAt < 0 ? 3 : Mth.clamp(now - r.openAt, 0, 3);
            r.openAt = now;
            r.open += (want - r.open) * (1 - (float) Math.exp(-dt * .7f));
            drawOpen = r.open;
        } else drawOpen = want;
        for (int side = 0; side < 2; side++) {
            LEVEL[side] = level(s, id, action, t, side, now);
            FLASH[side] = r == null ? 0 : flashK(r.flash[side], now) * r.flashPower[side];
        }
        if (action == SHOCK_PUNCH && drawPowered && !BatmanShock.dry(s.combo)) {
            int b = BatmanShock.blow(s.combo);
            float lt = local(t, s.combo), h = BLOW_HIT[b];
            drawStrike = BLOW_SIDE[b];
            drawReach = FilmFx.window(lt, h * .35f, h + 1.5f, 1.2f);
        }
        return w;
    }

    private static final ModelPart UNIT = GhostMaterials.box(0, 0, 0, 1, 1, 1);
    private static final int FULL = 15728880;
    /** Matte black, graphite, dark gunmetal, steel edges, grooves, a dead lens; the light: cyan, white core, turquoise. */
    private static final float[] MATTE = {.035f, .036f, .042f}, GRAPHITE = {.075f, .078f, .086f}, GUNMETAL = {.14f, .145f, .16f},
            STEEL = {.38f, .4f, .44f}, GROOVE = {.02f, .02f, .025f}, LENS_OFF = {.03f, .045f, .05f},
            CYAN_E = {.28f, .9f, 1f}, CORE_E = {.86f, 1f, 1f}, TURQ_E = {.1f, .7f, .78f};
    private static final float[] TINT = new float[3], MIX = new float[3];

    private static void px(PoseStack p, double x, double y, double z) { p.translate(x / 16, y / 16, z / 16); }
    /** Armour colour as drawn: clamped, pulled toward the thermal view's cold blue-black when it is on. */
    private static float[] armour(float[] c) {
        float th = BatmanBody.thermal;
        if (th <= .001f) { TINT[0] = Math.min(1, c[0]); TINT[1] = Math.min(1, c[1]); TINT[2] = Math.min(1, c[2]); return TINT; }
        float lum = (c[0] + c[1] + c[2]) / 3;
        TINT[0] = Math.min(1, Mth.lerp(th, c[0], .03f + lum * .12f));
        TINT[1] = Math.min(1, Mth.lerp(th, c[1], .05f + lum * .18f));
        TINT[2] = Math.min(1, Mth.lerp(th, c[2], .11f + lum * .35f));
        return TINT;
    }
    /** A box centred on (x, y, z) px, of size (w, h, d) px. */
    private static void part(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] c) {
        if (w <= .01f || h <= .01f || d <= .01f) return;
        p.pushPose();
        px(p, x, y, z);
        p.translate(-w / 32, -h / 32, -d / 32);
        p.scale(w, h, d);
        float[] k = armour(c);
        UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), light, OverlayTexture.NO_OVERLAY, k[0], k[1], k[2], 1);
        p.popPose();
    }
    /** An emissive part: full-bright, from a dead lens up to the light colour by k, with a small additive halo (px bigger). */
    private static void lamp(PoseStack p, MultiBufferSource b, int light, float x, float y, float z, float w, float h, float d, float[] rgb, float k, float halo) {
        if (k <= .02f) { part(p, b, light, x, y, z, w, h, d, LENS_OFF); return; }
        float kk = Math.min(1, k);
        for (int i = 0; i < 3; i++) MIX[i] = Math.min(1, Mth.lerp(kk, LENS_OFF[i], rgb[i]) * (.75f + .25f * Math.min(1.4f, k)));
        p.pushPose();
        px(p, x, y, z);
        p.pushPose();
        p.translate(-w / 32, -h / 32, -d / 32);
        p.scale(w, h, d);
        UNIT.render(p, b.getBuffer(RenderType.entityCutoutNoCull(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, MIX[0], MIX[1], MIX[2], 1);
        p.popPose();
        if (halo > 0) {
            float hw = w + halo, hh = h + halo, hd = d + halo, g = Math.min(1, .32f * k);
            p.translate(-hw / 32, -hh / 32, -hd / 32);
            p.scale(hw, hh, hd);
            UNIT.render(p, b.getBuffer(RenderType.eyes(GhostMaterials.TEXTURE)), FULL, OverlayTexture.NO_OVERLAY, rgb[0] * g, rgb[1] * g, rgb[2] * g, 1);
        }
        p.popPose();
    }
    private static float backOut(float x) { float c1 = 1.70158f, c3 = c1 + 1, u = x - 1; return 1 + c3 * u * u * u + c1 * u * u; }

    /**
     * The gauntlet on one hand, in BatmanBody.hand's frame (fist centre near (0, 1.5, 0) px, the arm along -y behind it,
     * the knuckles toward +y; side 0 right; s * x is the back of the hand, -z the thumb), worn 0..1, charge 0..1.
     * Locking on: the bracer slides down over the wrist and clamps (worn 0..0.45), the plates close on the back of the
     * hand and the palm (0.3..0.75), the knuckle plate runs forward over the fist and locks with a small overshoot
     * (0.55..1); the shutters over the four emitters open after. Heavier and blockier than his own gauntlets.
     */
    static void knuckles(PoseStack p, MultiBufferSource b, int light, int side, float worn, float energy) {
        int s = side == 0 ? -1 : 1;
        float a = PantherMotion.k(worn, 0, .45f), bb = PantherMotion.k(worn, .3f, .75f), c = backOut(clamp01((worn - .55f) / .45f));
        float open = drawing != null ? drawOpen : drawPowered ? worn : 0;
        float lv = drawing != null ? LEVEL[side] : 0, fl = drawing != null ? FLASH[side] : 0;
        float time = drawing != null ? drawTime : 0;
        float glow = Math.min(1.6f, lv + .8f * fl);
        // ---- the bracer: a heavy black band clamped over the wrist, gunmetal rims, four steel bolts, small lamps
        if (a > .01f) {
            float y = -1.0f - 2.6f * (1 - a);
            part(p, b, light, 0, y, 0, 5.1f, 2.5f, 5.1f, MATTE);
            part(p, b, light, 0, y + 1.1f, 0, 5.35f, .35f, 5.35f, GUNMETAL);
            part(p, b, light, 0, y - 1.1f, 0, 5.35f, .35f, 5.35f, GUNMETAL);
            for (int i = 0; i < 4; i++) {
                float bx = (i % 2 == 0 ? 1 : -1) * 2.62f, bz = (i < 2 ? 1 : -1) * 1.6f;
                part(p, b, light, bx, y, bz, .3f, .45f, .45f, STEEL);
            }
            // The cuff's lamps: front, back and the outer side (the charge's level).
            lamp(p, b, light, s * 2.62f, y, 0, .22f, .55f, .9f, TURQ_E, a * Math.min(1, .55f * glow + .04f), .45f);
            lamp(p, b, light, 0, y, -2.62f, .9f, .55f, .22f, TURQ_E, a * Math.min(1, .45f * glow + .03f), .35f);
        }
        // ---- the plates: graphite over the back of the hand with a gunmetal ridge, a thin palm plate, side guards
        if (bb > .01f) {
            float out = 1.4f * (1 - bb);
            part(p, b, light, s * (1.2f + out), 1.25f, 0, .85f, 2.9f, 3.75f, GRAPHITE);
            part(p, b, light, s * (1.66f + out), 1.25f, 0, .32f, 2.5f, 1.05f, GUNMETAL);
            part(p, b, light, s * (1.66f + out), .1f, 0, .34f, .3f, 3.2f, STEEL);
            part(p, b, light, -s * (1.15f + out), 1.15f, 0, .45f, 2.3f, 3.35f, MATTE);
            part(p, b, light, 0, 1.2f, -2.0f - out, 2.1f, 2.5f, .4f, GRAPHITE);
            part(p, b, light, 0, 1.2f, 2.0f + out, 2.1f, 2.5f, .4f, GRAPHITE);
            // The coils on the back of the hand: two stacks of gunmetal rings with glowing windings, energy running up them.
            for (int cz = -1; cz <= 1; cz += 2) {
                for (int i = 0; i < 4; i++) {
                    float y = .35f + i * .55f;
                    part(p, b, light, s * (2.0f + out), y, cz * .85f, .55f, .24f, .62f, GUNMETAL);
                    float flow = .5f + .5f * Mth.sin(time * 1.6f - i * 1.3f + cz);
                    lamp(p, b, light, s * (2.0f + out), y + .27f, cz * .85f, .46f, .12f, .5f, CYAN_E, bb * glow * (.35f + .65f * flow), .3f);
                }
            }
            // Conduits from the bracer to the knuckle plate.
            for (int cz = -1; cz <= 1; cz += 2)
                lamp(p, b, light, s * (1.66f + out), 1.25f, cz * .62f, .1f, 2.7f, .1f, TURQ_E, bb * Math.min(1, .6f * glow), 0);
        }
        // ---- the knuckle plate: heavy gunmetal in front of the fist, a steel strike face, four emitters behind shutters
        float py = 1.9f + 1.35f * c;
        if (c > .01f) {
            part(p, b, light, -s * .3f, py, 0, 2.75f, .75f, 4.0f, GUNMETAL);
            part(p, b, light, -s * .3f, py + .44f, 0, 2.45f, .16f, 3.7f, STEEL);
            part(p, b, light, s * 1.1f, py - .2f, 0, .5f, .9f, 4.0f, MATTE);
            for (int i = 0; i < 3; i++) part(p, b, light, -s * .3f, py + .5f, -.8f + i * .8f, 2.0f, .1f, .12f, GROOVE);
            for (int i = 0; i < 4; i++) {
                float z = -1.2f + i * .8f, y = py + .55f;
                part(p, b, light, -s * .3f, y, z, .8f, .26f, .58f, GRAPHITE);
                float k = open * glow * (.8f + .2f * Mth.sin(time * 2.3f + i * 1.7f));
                lamp(p, b, light, -s * .3f, y + .14f, z, .46f, .1f, .36f, k > .9f ? CORE_E : CYAN_E, k, .5f);
                // The shutter: two halves sliding apart as it opens.
                float shut = 1 - open;
                if (shut > .02f) {
                    part(p, b, light, -s * .3f + .12f * (1 + open), y + .22f, z, .25f * shut + .01f, .08f, .4f, GUNMETAL);
                    part(p, b, light, -s * .3f - .12f * (1 + open), y + .22f, z, .25f * shut + .01f, .08f, .4f, GUNMETAL);
                }
            }
        }
        // ---- where they are, for the light, the trail and the effects in the world
        if (BatmanBody.capture && drawing != null && c > .5f) {
            var level = Minecraft.getInstance().level;
            Vec3 k = BatmanBody.world(p, -s * .3f, py + .7f, 0);
            drawing.knuckle[side] = k;
            drawing.cuff[side] = BatmanBody.world(p, 0, -1.0f, 0);
            Vec3 ahead = BatmanBody.world(p, -s * .3f, py + 16.7f, 0).subtract(k);
            drawing.ahead[side] = ahead.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : ahead.normalize();
            if (level != null) {
                drawing.seen = level.getGameTime();
                int head = drawing.trailHead[side];
                int prev = Math.floorMod(head - 1, TRAIL);
                if (drawing.trail[side][prev] == null || drawTime - drawing.trailT[side][prev] > .05f) {
                    drawing.trail[side][head] = k;
                    drawing.trailT[side][head] = drawTime;
                    drawing.trailHead[side] = (head + 1) % TRAIL;
                }
            }
        }
        // ---- the electricity living on them
        if (lv > .01f && c > .9f) electricity(p, b, side, s, py, lv, fl, time);
    }
    /**
     * Arcs in the hand's own frame (px), re-shaped about three times a tick, never the same twice: between neighbouring
     * emitters, crawling over the back plate, the coils and the bracer, jumping off the knuckles into the air (more and
     * longer, thrown forward, on the hand that strikes); how many follows the charge's life (lv).
     */
    private static void electricity(PoseStack p, MultiBufferSource b, int side, int s, float py, float lv, float fl, float time) {
        int id = drawing == null ? 0 : drawing.id;
        int bucket = (int) Math.floor(time * 3);
        float reach = side == drawStrike ? drawReach : 0;
        float density = Math.min(1.5f, lv) * BatmanConfig.EFFECTS.get().floatValue();
        p.pushPose();
        p.scale(1 / 16f, 1 / 16f, 1 / 16f);
        Matrix4f m = p.last().pose();
        VertexConsumer v = b.getBuffer(FilmFx.ADD);
        float ex = -s * .3f, ey = py + .72f;
        int arcs = 8;
        for (int i = 0; i < arcs; i++) {
            int seed = id * 7919 + side * 104729 + i * 613 + (bucket + i) / 2 * 31;
            double h0 = FilmFx.hash(seed * .71);
            if (h0 > .25 + .55 * density) continue;
            Vec3 a, z;
            int kind = i % 4;
            if (kind == 0) {
                // Between two neighbouring emitters.
                int e = (int) (FilmFx.hash(seed * 1.3) * 3);
                a = new Vec3(ex, ey, -1.2 + e * .8); z = new Vec3(ex, ey, -.4 + e * .8);
            } else if (kind == 1) {
                // Crawling: from the knuckle plate back over the plates, the coils or down to the bracer.
                double u = FilmFx.hash(seed * 2.1), w = FilmFx.hash(seed * 3.3);
                a = new Vec3(s * 1.2, py - .1, (u - .5) * 3.4);
                z = w < .5 ? new Vec3(s * 2.05, .35 + w * 3.6, (u > .5 ? 1 : -1) * .85) : new Vec3(s * 2.0 * (w - .5) * 2, -1.0 + (w - .5) * .6, s * 2.5 * (u - .5));
            } else if (kind == 2) {
                // Between the two coils, across the back of the hand.
                double u = FilmFx.hash(seed * 4.7);
                a = new Vec3(s * 2.1, .4 + u * 1.7, -.85); z = new Vec3(s * 2.1, .4 + FilmFx.hash(seed * 5.1) * 1.7, .85);
            } else {
                // Jumping off the knuckles into the air (forward and longer while the fist strikes).
                int e = (int) (FilmFx.hash(seed * 6.1) * 4);
                a = new Vec3(ex, ey, -1.2 + e * .8);
                double len = 1.2 + FilmFx.hash(seed * 7.3) * 1.6 + reach * 2.5;
                z = a.add((FilmFx.hash(seed * 8.9) - .5) * 2.2 * (1 - .5 * reach), len, (FilmFx.hash(seed * 9.7) - .5) * 2.2 * (1 - .5 * reach));
            }
            float alpha = Math.min(1, (.55f + .45f * (float) FilmFx.hash(seed * 1.9)) * Math.min(1, lv + .3f * fl));
            arcModel(v, m, ThorBolts.bolt(a, z, seed, .5, .35, kind == 1 ? 1 : 0), .1, alpha);
        }
        // The striking hand: a few more thrown forward off the knuckles.
        if (reach > .05f) for (int i = 0; i < 3; i++) {
            int seed = id * 31 + side * 977 + i * 101 + bucket * 17;
            Vec3 a = new Vec3(ex, ey, -1.2 + (i + FilmFx.hash(seed)) * .9);
            Vec3 z = a.add((FilmFx.hash(seed * 2.2) - .5) * 1.4, 2.0 + 3.0 * reach * FilmFx.hash(seed * 3.1), (FilmFx.hash(seed * 4.4) - .5) * 1.4);
            arcModel(v, m, ThorBolts.bolt(a, z, seed, .55, .45, 1), .12, reach * Math.min(1, lv));
        }
        // A flash on the knuckles at full extension.
        if (fl > .02f) for (int i = 0; i < 4; i++) {
            int seed = id * 13 + side * 7 + i * 59 + bucket * 3;
            Vec3 a = new Vec3(ex, ey, -1.2 + i * .8);
            double ang = FilmFx.hash(seed) * Mth.TWO_PI;
            Vec3 z = a.add(Math.cos(ang) * 1.8 * fl, 1.0 + 1.5 * fl * FilmFx.hash(seed * 2.7), Math.sin(ang) * 1.8 * fl);
            arcModel(v, m, ThorBolts.bolt(a, z, seed, .5, .3, 0), .12, Math.min(1, .6f * fl));
        }
        p.popPose();
    }
    /** An arc drawn in a model's own space (seen from any side): turquoise glow, cyan body, white core (px widths). */
    private static void arcModel(VertexConsumer v, Matrix4f m, List<ThorBolts.Seg> segs, double width, float alpha) {
        if (alpha <= .01f) return;
        for (ThorBolts.Seg s : segs) {
            double w = width * s.width();
            ThorBolts.cross(v, m, s.a(), s.b(), w * 4.5, TEAL, alpha * .2f);
            ThorBolts.cross(v, m, s.a(), s.b(), w * 1.8, CYAN, alpha * .75f);
            ThorBolts.cross(v, m, s.a(), s.b(), w * .6, CORE, alpha);
        }
    }

    private static void clear() {
        for (Rig r : RIGS.values()) if (r.hum != null) r.hum.end();
        RIGS.clear(); SHOCKS.clear(); BOLTS.clear(); SPLASHES.clear(); MOTES.clear(); PUSHED.clear();
        wash = 0; barShown = 0; barValue = -1;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }
}
