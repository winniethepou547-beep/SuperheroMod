package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.heroes.iceman.IcemanAction;
import com.FIRNI.superheromod.network.packet.IcemanFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * The frost meter on the client: every body's frost as the server sends it (FX_FROST, FX_DEEP_FREEZE, FX_DEEP_BREAK),
 * eased so the frost on them grows in and melts away smoothly, and what goes with it while they live: their breath
 * puffing as cold vapour from the mouth (denser the colder), frost dust and little bits of ice shed as they move, frosty
 * footprints, a quiet crackle now and then when they are nearly frozen through, mist round the feet of the deep frozen.
 * The frost drawn on the bodies and the deep freeze's block of ice are FrostBodies; the frost on the frozen player's own
 * screen (and the slide's icy lens, FX_LENS) is FrostScreen. While the player here is deep frozen they cannot move,
 * jump, attack or use anything.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class FrostFx {
    private FrostFx() {}

    /** One body's frost as this client knows it. */
    static final class Body {
        final int id;
        /** The server's meter; the shown value eased toward it (last tick and now, for the frame in between). */
        float meter, shown, shownO;
        /** Deep frozen, and the ticks the server said it still holds. */
        boolean deep; int deepLeft;
        int stage;
        /** On the ground (eased 0..1, the rime round the feet follows it). */
        float grounded, groundedO;
        Vec3 last; float speed, step; boolean leftFoot;
        int breathIn = 20, breathLeft, crackIn = 40, dustIn, bitIn;
        /** A hard shiver for a moment after the ice round them broke (ticks left). */
        int shiver;
        /** Short-lived crystals where the brush's stream lands (body-local). */
        final List<Bloom> blooms = new ArrayList<>();
        /** The places the frost grows on this body (FrostBodies makes them once). */
        FrostBodies.Sites sites;
        Body(int id) { this.id = id; }
        float shown(float partial) { return Mth.lerp(partial, shownO, shown); }
        float grounded(float partial) { return Mth.lerp(partial, groundedO, grounded); }
    }
    /** A burst of crystals on a body (body-local: x to its left, y up, z ahead, from the feet), born at a time. */
    record Bloom(float x, float y, float z, float nx, float nz, float born, int seed) {}

    static final Map<Integer, Body> BODIES = new HashMap<>();
    private static final Random RNG = new Random();
    private static ClientLevel lastLevel;

    // ------------------------------------------------------------------ for other files
    /** A body's frost meter as shown (0..100; 100 while deep frozen). */
    public static float meter(int entityId) {
        Body b = BODIES.get(entityId);
        return b == null ? 0 : b.deep ? FROST_MAX : Mth.clamp(b.shown, 0, FROST_MAX);
    }
    /** True while the body is deep frozen (held in a block of ice). */
    public static boolean deep(int entityId) { Body b = BODIES.get(entityId); return b != null && b.deep; }
    /** The frost stage the body shows (IcemanAction.stage: 0 none .. 4 heavy, 5 deep frozen). */
    public static int stage(int entityId) { Body b = BODIES.get(entityId); return b == null ? 0 : IcemanAction.stage(b.shown, b.deep); }

    /** This client's own clock (ticks since the game started, plus the frame's share): small numbers, so it stays smooth. */
    private static long clock;
    static float now() { return clock + Minecraft.getInstance().getFrameTime(); }
    static float ticks() { return clock; }
    static boolean isMe(int id) { var mc = Minecraft.getInstance(); return mc.player != null && mc.player.getId() == id; }
    private static Body body(int id) { return BODIES.computeIfAbsent(id, Body::new); }

    // ------------------------------------------------------------------ from the server
    public static void receive(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        switch (p.kind()) {
            case FX_FROST -> {
                if (p.entity() < 0) return;
                Body b = body(p.entity());
                b.meter = Mth.clamp(p.power(), 0, FROST_MAX);
                if (p.id() > 0) {
                    b.deepLeft = p.id();
                    // Deep frozen and we never saw it begin (just came in sight): the ice as it stands now.
                    if (!b.deep) {
                        b.deep = true;
                        Entity e = mc.level.getEntity(b.id);
                        float since = p.dir().x < 0 ? DEEP_CLOSE : (float) p.dir().x;
                        froze(b, e == null ? p.pos() : e.position(), e == null ? .6f : e.getBbWidth(), e == null ? 1.8f : e.getBbHeight(), (int) since + p.id(), since);
                    }
                } else if (b.deep || FrostBodies.encased(b.id)) {
                    // The deep freeze ended without a break being heard (the meter set lower): it breaks all the same.
                    thawed(b, 1, false);
                }
            }
            case FX_DEEP_FREEZE -> {
                if (p.entity() < 0) return;
                Body b = body(p.entity());
                b.deep = true; b.deepLeft = p.id(); b.meter = FROST_MAX;
                froze(b, p.pos(), Math.max(.2f, p.power()), (float) Math.max(.3, p.dir().y), Math.max(DEEP_CLOSE + 2, p.id()), 0);
            }
            case FX_DEEP_BREAK -> {
                if (p.entity() < 0) return;
                thawed(body(p.entity()), p.power() >= 1.5f ? 2 : 1, true);
            }
            case FX_LENS -> FrostScreen.lens(p.power(), Math.max(6, p.id()));
            case FX_BRUSH_FROST -> brush(p);
            default -> {}
        }
    }
    private static void froze(Body b, Vec3 feet, float width, float height, int total, float since) {
        FrostBodies.freeze(b.id, feet, width, height, total, since);
        if (isMe(b.id)) FrostScreen.freeze(total, since);
    }
    /**
     * The ice round them broke (how: 1 by itself, 2 shattered by a blow). The frost on them drops with it at once to
     * what the server leaves them (IcemanFrost.thaw: 45, or 30 after a blow): the ice that broke off carried it away.
     */
    private static void thawed(Body b, int how, boolean heard) {
        boolean was = b.deep || FrostBodies.encased(b.id);
        b.deep = false; b.deepLeft = 0;
        float left = how == 2 ? 30 : 45;
        if (heard) { b.meter = left; b.shown = b.shownO = Math.min(b.shown, left); }
        else b.shown = b.shownO = Math.min(b.shown, Math.max(b.meter, 0));
        if (!was) return;
        b.shiver = 22;
        FrostBodies.breakEncase(b.id, how);
        if (isMe(b.id)) FrostScreen.broke(how);
    }
    /** The brush's stream on a body: frost blooms where it lands (on the side facing the Iceman brushing it). */
    private static void brush(IcemanFxPacket p) {
        var mc = Minecraft.getInstance();
        if (!(mc.level.getEntity(p.entity()) instanceof LivingEntity le)) return;
        Body b = body(le.getId());
        b.meter = Math.max(b.meter, Mth.clamp(p.power(), 0, FROST_MAX));
        Vec3 centre = le.getBoundingBox().getCenter();
        Vec3 from = null;
        for (var en : IcemanClient.states().entrySet()) {
            if (en.getValue().brushTarget != le.getId()) continue;
            Entity ice = mc.level.getEntity(en.getKey());
            if (ice != null) { from = ice.getEyePosition(); break; }
        }
        Vec3 toward = from == null ? le.getLookAngle() : from.subtract(centre);
        toward = new Vec3(toward.x, toward.y * .3, toward.z);
        toward = toward.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : toward.normalize();
        float h = le.getBbHeight(), w = le.getBbWidth();
        Vec3 hit = centre.add(toward.scale(w * .5)).add((RNG.nextFloat() - .5) * w * .5, (RNG.nextFloat() - .45) * h * .5, (RNG.nextFloat() - .5) * w * .5);
        // The bloom, body-local (it rides with the body as it turns).
        float yaw = le.yBodyRot * Mth.DEG_TO_RAD, cos = Mth.cos(yaw), sin = Mth.sin(yaw);
        Vec3 d = hit.subtract(le.position());
        float lx = (float) (d.x * cos + d.z * sin), lz = (float) (-d.x * sin + d.z * cos);
        float nx = (float) (toward.x * cos + toward.z * sin), nz = (float) (-toward.x * sin + toward.z * cos);
        if (b.blooms.size() >= 10) b.blooms.remove(0);
        b.blooms.add(new Bloom(lx, (float) d.y, lz, nx, nz, now(), RNG.nextInt(1 << 20)));
        // Light: a breath of mist, a few flecks, now and then a glint.
        if (IceParticles.count(1, hit) > 0) IceParticles.mist(hit, toward.scale(.025).add(0, .004, 0), .12f, .018f, .22f, 18);
        int n = IceParticles.count(3, hit);
        for (int i = 0; i < n; i++) IceParticles.snow(hit.add(IceParticles.jitter(.06)), toward.scale(.05).add(IceParticles.jitter(.03)).add(0, .03, 0), .02f + .015f * RNG.nextFloat(), 14 + RNG.nextInt(10));
        if (RNG.nextFloat() < .3f) IceParticles.flash(hit, .22f, .3f, 4);
        if (isMe(le.getId())) FrostScreen.brushed();
    }

    // ------------------------------------------------------------------ every tick
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { clear(); lastLevel = mc.level; }
        if (mc.level == null || mc.isPaused()) return;
        clock++;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Iterator<Body> it = BODIES.values().iterator(); it.hasNext(); ) {
            Body b = it.next();
            LivingEntity le = mc.level.getEntity(b.id) instanceof LivingEntity l ? l : null;
            if (le == null || le.isRemoved()) {
                // Gone (dead, out of sight): an ice block round them breaks, nothing is left standing.
                if (FrostBodies.encased(b.id)) FrostBodies.breakEncase(b.id, 1);
                if (isMe(b.id)) FrostScreen.broke(1);
                FrostBodies.forget(b.id);
                it.remove();
                continue;
            }
            ease(b, le);
            int stage = IcemanAction.stage(b.shown, b.deep);
            boolean near = le.position().distanceToSqr(cam) < 48 * 48;
            if (stage > b.stage && stage >= 3 && stage < 5 && near)
                mc.level.playLocalSound(le.getX(), le.getY() + le.getBbHeight() * .5, le.getZ(), ModSounds.ICEMAN_CRACK.get(), SoundSource.PLAYERS, .22f, 1.6f + RNG.nextFloat() * .25f, false);
            b.stage = stage;
            float now = clock;
            b.blooms.removeIf(bl -> now - bl.born() > 30);
            if (near && !le.isInvisible()) live(mc, b, le, cam);
            if (b.shiver > 0) b.shiver--;
            if (!b.deep && b.meter <= 0 && b.shown <= .05f && b.blooms.isEmpty() && !FrostBodies.encased(b.id)) { FrostBodies.forget(b.id); it.remove(); }
        }
        FrostScreen.tick();
    }
    /** The shown frost follows the meter: grows in fairly quickly, melts away slower. */
    private static void ease(Body b, LivingEntity le) {
        b.shownO = b.shown;
        float target = b.deep ? FROST_MAX : b.meter;
        if (target > b.shown) b.shown = Math.min(target, b.shown + Math.max(.8f, (target - b.shown) * .2f));
        else if (target < b.shown) b.shown = Math.max(target, b.shown - Math.max(.3f, (b.shown - target) * .07f));
        if (b.deepLeft > 0) b.deepLeft--;
        b.groundedO = b.grounded;
        b.grounded += ((le.onGround() ? 1 : 0) - b.grounded) * .3f;
        Vec3 pos = le.position();
        float moved = b.last == null ? 0 : (float) Math.sqrt((pos.x - b.last.x) * (pos.x - b.last.x) + (pos.z - b.last.z) * (pos.z - b.last.z));
        if (moved > 3) moved = 0;   // a teleport, not a step
        b.speed += (moved - b.speed) * .4f;
        b.last = pos;
        // Frosty footprints left as they walk.
        if (b.shown >= 3 && !b.deep && le.onGround() && moved > .01f) {
            b.step += moved;
            if (b.step > .62f) { b.step = 0; FrostBodies.footprint(le, b.shown, b.leftFoot); b.leftFoot = !b.leftFoot; }
        }
    }
    /** Breath, shed frost, crackles and the deep freeze's mist. */
    private static void live(Minecraft mc, Body b, LivingEntity le, Vec3 cam) {
        float k = Mth.clamp(le.getBbHeight() / 1.8f, .4f, 2.5f);
        Vec3 vel = le.getDeltaMovement();
        int stage = b.stage;
        boolean firstPerson = isMe(b.id) && mc.options.getCameraType().isFirstPerson();
        if (b.deep) {
            // Cold mist creeping round the base of the ice.
            if (le.tickCount % 5 == 0) {
                double a = RNG.nextDouble() * Math.PI * 2, r = le.getBbWidth() * .7 + .25;
                Vec3 at = le.position().add(Math.cos(a) * r, .08, Math.sin(a) * r);
                if (IceParticles.count(1, at) > 0) IceParticles.mist(at, new Vec3(Math.cos(a) * .012, -.002, Math.sin(a) * .012), .22f * k, .02f, .2f, 30);
            }
            return;
        }
        // The breath: a slow exhale every so often, denser and more often the colder.
        if (stage >= 1 && !le.isUnderWater()) {
            if (b.breathLeft > 0) {
                if (b.breathLeft % (stage >= 3 ? 1 : 2) == 0) breath(le, b, stage, k, vel, firstPerson);
                b.breathLeft--;
            } else if (--b.breathIn <= 0) {
                b.breathLeft = 7 + stage;
                b.breathIn = new int[]{70, 70, 56, 44, 34}[Math.min(4, stage)] + RNG.nextInt(14);
            }
        }
        if (firstPerson) return;
        // Frost dust and little bits of ice shed when they move (now and then even standing, when heavy).
        boolean moving = b.speed > .035f;
        if (stage >= 2 && (moving || stage >= 4)) {
            if (--b.dustIn <= 0) {
                b.dustIn = (moving ? 8 - stage : 22) + RNG.nextInt(4);
                Vec3 at = onBody(le, .12f, .8f);
                IceParticles.frostDust(at, vel, .35f + .2f * stage);
            }
            if (moving && --b.bitIn <= 0) {
                b.bitIn = 15 - 2 * stage + RNG.nextInt(6);
                Vec3 at = onBody(le, .1f, .75f);
                if (IceParticles.count(1, at) > 0)
                    IceParticles.shard(at, vel.scale(.4).add(IceParticles.jitter(.02)).add(0, .03, 0), (.022f + .016f * RNG.nextFloat()) * k, 20 + RNG.nextInt(12), RNG.nextBoolean() ? IceMesh.FROST : IceMesh.MILKY);
            }
        }
        // Nearly frozen through: the ice on them creaks and cracks now and then.
        if (stage >= 4 && --b.crackIn <= 0) {
            b.crackIn = 30 + RNG.nextInt(50);
            if (le.position().distanceToSqr(cam) < 24 * 24)
                mc.level.playLocalSound(le.getX(), le.getY() + le.getBbHeight() * .6, le.getZ(), ModSounds.ICEMAN_CRACK.get(), SoundSource.PLAYERS, .16f + .14f * RNG.nextFloat(), 1.45f + .45f * RNG.nextFloat(), false);
        }
    }
    private static void breath(LivingEntity le, Body b, int stage, float k, Vec3 vel, boolean firstPerson) {
        float yaw = le.yHeadRot * Mth.DEG_TO_RAD, pitch = le.getXRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch) * .6f - .15f, Mth.cos(yaw) * Mth.cos(pitch)).normalize();
        Vec3 eye = le.getEyePosition();
        // Seen from inside (first person) the breath shows a little lower and further out, not over the eyes.
        Vec3 at = firstPerson ? eye.add(dir.scale(.55)).add(0, -.3, 0) : eye.add(dir.scale(le.getBbWidth() * .45 + .05)).add(0, -.1 * k, 0);
        if (IceParticles.count(1, at) <= 0) return;
        float a = (firstPerson ? .6f : 1) * (.14f + .05f * stage);
        IceParticles.mist(at, dir.scale(.03 + .006 * stage).add(vel.scale(.8)).add(0, -.002, 0), (.05f + .015f * stage) * k, (.011f + .004f * stage) * k, a, 20 + 4 * stage);
    }
    /** A random point on the body's surface between the heights lo and hi (shares of its height). */
    private static Vec3 onBody(LivingEntity le, float lo, float hi) {
        double a = RNG.nextDouble() * Math.PI * 2, r = le.getBbWidth() * .5;
        return le.position().add(Math.cos(a) * r, le.getBbHeight() * (lo + (hi - lo) * RNG.nextFloat()), Math.sin(a) * r);
    }

    // ------------------------------------------------------------------ the deep frozen player here cannot act
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void hold(MovementInputUpdateEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || e.getEntity() != mc.player || !deep(mc.player.getId())) return;
        var in = e.getInput();
        in.forwardImpulse = 0; in.leftImpulse = 0; in.jumping = false; in.shiftKeyDown = false;
        in.up = in.down = in.left = in.right = false;
        mc.player.setSprinting(false);
    }
    @SubscribeEvent public static void clicks(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && deep(mc.player.getId()) && (e.isAttack() || e.isUseItem())) { e.setCanceled(true); e.setSwingHand(false); }
    }

    static void clear() { BODIES.clear(); FrostBodies.clear(); FrostScreen.clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { clear(); lastLevel = null; }
}
