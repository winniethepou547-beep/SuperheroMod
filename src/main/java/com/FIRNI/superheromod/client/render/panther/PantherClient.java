package com.FIRNI.superheromod.client.render.panther;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.input.AbilityKeyHandler;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.heroes.panther.PantherConfig;
import com.FIRNI.superheromod.heroes.panther.PantherPath;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.AbilityInputPacket;
import com.FIRNI.superheromod.network.packet.PantherStatePacket;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.*;

import static com.FIRNI.superheromod.heroes.panther.PantherAction.*;

/**
 * Black Panther on each client: every Panther's synced state, his E key (the release, not the inventory),
 * his own body steered along the server's paths (pounce, flip, kick, landing, spin), the camera that
 * swings round the action while he flips over a target, the shakes and the view's kicks, and the HUD.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class PantherClient {
    public static final class State {
        public int action, age, flags, reflexLeft, hurtAge = 100, hurtPower, quiet = 255, target = -1;
        public int[] cooldowns = new int[COOLDOWNS];
        public float energy, released, reflex, hurtYaw, threatYaw, reach, speed, height;
        public Vec3 from = Vec3.ZERO, dir = new Vec3(0, 0, 1), apex = Vec3.ZERO, to = Vec3.ZERO, land = Vec3.ZERO;
        long received;
        /** When a strike of his last landed (level time): his body holds still for an instant there. */
        double stopAt = -100; float stopFor;
        /** Hits soaking into the suit: {level time, side, where on the body they start (flow order)}. */
        public final List<float[]> pulses = new ArrayList<>();
        /** For the flip: the local player's yaw when it began. */
        float flipYaw = Float.NaN;
        public int camoLeft;
        /** The local clock: the level time the current action began at, so its time runs smoothly between packets. */
        double start;
        /**
         * What his own client already started without waiting for the server (the pounce on the key, the crouch):
         * the action, when, and the path it leaves on. Kept until the server's state catches up.
         */
        int predicted = -1; double predAt;
        /** The path his own client steers: where the pounce / flip / dash left from, which way, how far. */
        Vec3 localFrom = Vec3.ZERO, localDir = new Vec3(0, 0, 1); float localReach; int localFor = -1;
    }
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static boolean eDown, shiftDown, jumpDown, jumped, crouchDown, shiftRun;
    private static int airTicks;
    static float fovKick;
    private static CameraType savedCamera;
    private static Field cameraPosition;

    private PantherClient() {}

    public static boolean isHero(Entity e) { return e instanceof Player && ID.equals(ClientHeroRegistry.get(e.getUUID())); }
    public static State get(Entity e) { return e == null ? null : STATES.get(e.getId()); }

    public static void receive(PantherStatePacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        State s = STATES.computeIfAbsent(p.entity(), id -> new State());
        long now = mc.level.getGameTime();
        boolean local = mc.player != null && p.entity() == mc.player.getId();
        if (p.action() != s.action) {
            s.stopAt = -100;
            // A move his client predicted keeps its own start, so nothing jumps when the server confirms it.
            if (local && s.predicted == p.action() && Math.abs(now - s.predAt - p.age()) < 4) { s.start = s.predAt; s.predicted = -1; }
            else s.start = now - p.age();
            // Out of the moves that carry him, his own path is spent (unless the pounce he predicted is still on its way).
            int a = p.action();
            boolean waiting = s.predicted == POUNCE && (free(a) || a == POUNCE_LOAD || a == SNEAK);
            if (!waiting && a != POUNCE && a != POUNCE_FLIP && a != POUNCE_KICK && a != POUNCE_LAND && a != POUNCE_MISS && a != DASH && a != CROSS && a != SPIN) s.localFor = -1;
            // The server went somewhere the prediction never leads: drop it.
            if (local && s.predicted >= 0 && !free(a) && a != POUNCE_LOAD && a != SNEAK && a != s.predicted) s.predicted = -1;
        } else if (Math.abs(now - s.start - p.age()) > 3) s.start = now - p.age();
        boolean changed = p.action() != s.action;
        s.action = p.action(); s.age = p.age(); s.flags = p.flags(); s.cooldowns = p.cooldowns();
        s.energy = p.energy(); s.released = p.released(); s.reflex = p.reflex(); s.reflexLeft = p.reflexLeft(); s.camoLeft = p.camoLeft();
        s.hurtAge = p.hurtAge(); s.hurtPower = p.hurtPower(); s.hurtYaw = p.hurtYaw(); s.threatYaw = p.threatYaw(); s.quiet = p.quiet();
        s.target = p.target(); s.from = p.from(); s.dir = p.dir(); s.apex = p.apex(); s.to = p.to(); s.land = p.land();
        s.reach = p.reach(); s.speed = p.speed(); s.height = p.height();
        s.received = now;
        if (changed && local) started(mc.player, s, p.action());
    }
    /** The local Panther starts a move: the view's kick, and where his own path for it leaves from. */
    private static void started(LocalPlayer player, State s, int action) {
        if (action == POUNCE && s.localFor != POUNCE) { fovKick = 1; local(s, POUNCE, s.from, s.dir, s.reach); }
        if (action == SPIN) fovKick = Math.max(fovKick, .5f);
        if (action == POUNCE_FLIP) { s.flipYaw = player.getYRot(); local(s, POUNCE_FLIP, player.position(), s.dir, 0); }
        if (action == DASH) { fovKick = Math.max(fovKick, .6f); local(s, DASH, player.position(), s.dir, 0); }
        if (action == SPIN) local(s, SPIN, s.from, s.dir, s.reach);
    }
    private static void local(State s, int action, Vec3 from, Vec3 dir, float reach) { s.localFor = action; s.localFrom = from; s.localDir = dir; s.localReach = reach; }

    /** A move his client may start on its own, before the server confirms it. */
    private static boolean free(int action) { return action == IDLE || combo(action) || action == FRENZY || action == DODGE; }
    /** Is the server still behind what his client predicted? */
    private static boolean behind(State s) {
        return switch (s.predicted) {
            case POUNCE_LOAD -> free(s.action) || s.action == SNEAK;
            case POUNCE, SNEAK -> free(s.action) || s.action == POUNCE_LOAD;
            default -> false;
        };
    }
    /** What a Panther is doing, as his own client already knows it (a predicted move before the server's word). */
    public static int action(State s) {
        var level = Minecraft.getInstance().level;
        if (s.predicted >= 0 && level != null && level.getGameTime() - s.predAt < 8 && behind(s)) return s.predicted;
        return s.action;
    }
    public static float clock(State s, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) return s.age;
        boolean predicted = s.predicted >= 0 && action(s) == s.predicted && s.predicted != s.action;
        float raw = (float) Math.max(0, level.getGameTime() - (predicted ? s.predAt : s.start)) + partial;
        // Hit-stop: the body holds an instant where a strike bit.
        if (s.stopAt > 0) raw -= Math.min(s.stopFor, Math.max(0, level.getGameTime() + partial - s.stopAt));
        return raw;
    }
    /** A strike landed here: every Panther striking next to it holds for an instant; the local one feels it. */
    public static void struck(Vec3 at, float hold, float shake) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (var entry : STATES.entrySet()) {
            State s = entry.getValue();
            if (!clawing(action(s))) continue;
            var body = mc.level.getEntity(entry.getKey());
            if (body == null || body.position().distanceTo(at) > 6) continue;
            s.stopAt = mc.level.getGameTime() + mc.getFrameTime();
            s.stopFor = hold;
            if (body == mc.player) shake(shake);
        }
    }
    public static void shake(float amount) { ClientScreenShake.add(amount * PantherConfig.SHAKE.get().floatValue()); }
    /** A hit soaking into a Panther's suit. */
    public static void absorb(int entity, int side, float order) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        State s = STATES.computeIfAbsent(entity, id -> new State());
        s.pulses.add(new float[]{level.getGameTime() + Minecraft.getInstance().getFrameTime(), side, order});
        if (s.pulses.size() > 6) s.pulses.remove(0);
    }

    // ------------------------------------------------------------------ input
    /** E is his kinetic release: while he is Black Panther it does not open the inventory. */
    @SubscribeEvent public static void keys(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player)) {
            eDown = false;
            if (crouchDown) { crouchDown = false; if (mc.player != null) ModNetworking.CHANNEL.sendToServer(new com.FIRNI.superheromod.network.packet.PantherInputPacket(INPUT_CROUCH_UP)); }
            return;
        }
        if (mc.screen != null) {
            // A screen opened while crouching: let the server know the key is up (it cannot see it from here).
            if (crouchDown) { crouchDown = false; ModNetworking.CHANNEL.sendToServer(new com.FIRNI.superheromod.network.packet.PantherInputPacket(INPUT_CROUCH_UP)); }
            return;
        }
        boolean clicked = false;
        while (mc.options.keyInventory.consumeClick()) clicked = true;
        boolean down = mc.options.keyInventory.isDown() || clicked;
        if (down && !eDown) ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(AbilitySlot.SKILL_V, true));
        else if (!down && eDown) ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(AbilitySlot.SKILL_V, false));
        eDown = down;
        predictShift(mc.player);
        crouch(mc);
        doubleJump(mc.player);
    }
    /**
     * The crouch (and the camouflage it gathers into) is the sprint key held, CTRL. While he is Black Panther that key
     * does not sprint (a double tap of forward still does): it is read straight off the keyboard and taken away from
     * the vanilla sprint every tick.
     */
    private static void crouch(Minecraft mc) {
        KeyMapping key = mc.options.keySprint;
        boolean down = held(mc, key);
        // (With the toggle-sprint option the mapping flips on a press instead: set it back off.)
        if (mc.options.toggleSprint().get()) { if (key.isDown()) key.setDown(true); }
        else key.setDown(false);
        if (down == crouchDown) return;
        crouchDown = down;
        ModNetworking.CHANNEL.sendToServer(new com.FIRNI.superheromod.network.packet.PantherInputPacket(down ? INPUT_CROUCH_DOWN : INPUT_CROUCH_UP));
        State s = get(mc.player);
        if (s == null) return;
        int a = action(s);
        if (down && (a == IDLE || a == DODGE)) { s.predicted = SNEAK; s.predAt = mc.level.getGameTime(); }
        else if (!down && s.predicted == SNEAK) s.predicted = -1;
    }
    /** Is a key really held down right now (whatever the vanilla mapping was told)? */
    private static boolean held(Minecraft mc, KeyMapping key) {
        var k = key.getKey();
        long window = mc.getWindow().getWindow();
        if (k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) return k.getValue() >= 0 && com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, k.getValue());
        if (k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, k.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        return false;
    }
    /** Is his crouch key held (for the HUD)? */
    public static boolean crouching() { return crouchDown; }
    /**
     * SHIFT, started at once on his own client (the server's word follows a moment later): down, the load;
     * let go within a tap, the pounce, from exactly where he stands (out of the crouch too).
     */
    private static void predictShift(LocalPlayer player) {
        State s = get(player);
        boolean down = Minecraft.getInstance().options.keyShift.isDown();
        long now = player.level().getGameTime();
        if (s != null) {
            int a = action(s);
            if (down && !shiftDown && (free(a) || a == SNEAK) && s.cooldowns[CD_POUNCE] <= 0) {
                s.predicted = POUNCE_LOAD;
                s.predAt = now;
                Vec3 look = player.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0, look.z);
                s.localDir = flat.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
            } else if (!down && shiftDown && a == POUNCE_LOAD && clock(s, 0) < TAP_TICKS && s.cooldowns[CD_POUNCE] <= 0) {
                Vec3 dir = s.predicted == POUNCE_LOAD ? s.localDir : s.dir;
                // A tap: his client decides it and tells the server, which pounces from the same spot.
                ModNetworking.CHANNEL.sendToServer(new com.FIRNI.superheromod.network.packet.PantherInputPacket(INPUT_POUNCE));
                s.predicted = POUNCE;
                s.predAt = now;
                local(s, POUNCE, player.position(), dir, reach(player, dir, PantherConfig.POUNCE_DISTANCE.get()));
                fovKick = 1;
            } else if (!down && shiftDown && s.predicted == POUNCE_LOAD) s.predicted = crouchDown ? SNEAK : -1;
            else if (down && s.predicted == POUNCE_LOAD && now - s.predAt >= TAP_TICKS) { s.predicted = crouchDown ? SNEAK : -1; s.predAt = now; }
        }
        shiftDown = down;
    }
    /** As far as the pounce can go before a wall, checked at the knees and the head (as the server does). */
    private static float reach(LocalPlayer player, Vec3 dir, double max) {
        double reach = max;
        for (double h : new double[]{.4, 1.5}) {
            Vec3 a = player.position().add(0, h, 0), b = a.add(dir.scale(max + .4));
            var hit = player.level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) reach = Math.min(reach, Math.max(0, hit.getLocation().distanceTo(a) - .55));
        }
        return (float) reach;
    }
    /** The second jump: jump again in the air and he springs off nothing, flipping, a burst of air under his feet. */
    private static void doubleJump(LocalPlayer player) {
        boolean down = Minecraft.getInstance().options.keyJump.isDown();
        if (player.onGround() || player.isInWater() || player.isPassenger() || player.onClimbable() || player.isFallFlying()) { jumped = false; airTicks = 0; }
        else airTicks++;
        State s = get(player);
        if (down && !jumpDown && !jumped && airTicks > 1 && !player.getAbilities().flying && (s == null || free(action(s)) || action(s) == POUNCE_MISS)) {
            jumped = true;
            Vec3 v = player.getDeltaMovement();
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
            Vec3 push = forward.scale(player.input.forwardImpulse).add(left.scale(player.input.leftImpulse));
            if (push.lengthSqr() > 1e-4) push = push.normalize().scale(.22);
            player.setDeltaMovement(v.x * .6 + push.x, PantherConfig.DOUBLE_JUMP.get(), v.z * .6 + push.z);
            player.fallDistance = 0;
            ModNetworking.CHANNEL.sendToServer(new com.FIRNI.superheromod.network.packet.PantherInputPacket(INPUT_DOUBLE_JUMP));
            PantherFx.doubleJump(player.getId(), player.position());
        }
        jumpDown = down;
    }
    /** His left click is his claws: no vanilla swing or mining alongside. */
    @SubscribeEvent public static void claws(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player) || !(e.isAttack() || e.isUseItem())) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }
    /**
     * Rooted while the energy gathers, and while a move carries him; slow and quiet in the crouch. SHIFT is his, never the
     * vanilla sneak: a tap is the pounce, held it is his run (sprinting whenever he moves forward, the pounce's load
     * not stopping him on the way in).
     */
    @SubscribeEvent public static void held(MovementInputUpdateEvent e) {
        State s = get(e.getEntity());
        if (s == null || !(e.getEntity() instanceof LocalPlayer player) || !isHero(player)) return;
        int a = action(s);
        var keys = e.getInput();
        boolean shift = Minecraft.getInstance().options.keyShift.isDown();
        keys.shiftKeyDown = false;
        if (shift && (a == IDLE || a == POUNCE_LOAD || a == DODGE) && keys.forwardImpulse > 0 && !player.isUsingItem()) {
            player.setSprinting(true);
            shiftRun = true;
            return;
        }
        // Let go of SHIFT: the run he started with it ends too.
        if (shiftRun && !shift) { shiftRun = false; player.setSprinting(false); }
        if (a == SNEAK) {
            var input = e.getInput();
            input.forwardImpulse *= .3f; input.leftImpulse *= .3f; input.jumping = false;
            return;
        }
        if (a != POUNCE && a != POUNCE_FLIP && a != POUNCE_KICK && a != SPIN_LOAD && a != SPIN && a != RELEASE_CHARGE && a != RELEASE
                && a != DASH && !(a == POUNCE_LAND && clock(s, 0) < LAND_TICKS * .6f)) return;
        var input = e.getInput();
        input.forwardImpulse = 0; input.leftImpulse = 0; input.jumping = false; input.shiftKeyDown = false;
        input.up = input.down = input.left = input.right = false;
    }

    // ------------------------------------------------------------------ his body along the paths
    @SubscribeEvent public static void move(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { STATES.clear(); return; }
        long now = mc.level.getGameTime();
        STATES.entrySet().removeIf(v -> mc.level.getEntity(v.getKey()) == null || now - v.getValue().received > 200);
        for (State s : STATES.values()) s.pulses.removeIf(pu -> now - pu[0] > 14);
        fovKick *= .78f;
        LocalPlayer player = mc.player;
        if (player == null || !isHero(player) || FilmDirector.playing()) return;
        State s = STATES.get(player.getId());
        if (s == null) return;
        int action = action(s);
        float t = clock(s, 0) + 1;     // where he must be at the start of the next tick
        switch (action) {
            case SPIN_LOAD, RELEASE_CHARGE, RELEASE -> player.setDeltaMovement(player.getDeltaMovement().multiply(0, 1, 0));
            case POUNCE -> {
                // His own path, from where he really launched (the server's is the same line, a moment later).
                boolean own = s.localFor == POUNCE;
                float speed = s.speed > 0 ? s.speed : PantherConfig.POUNCE_SPEED.get().floatValue();
                if (own || s.reach > 0) steer(player, PantherPath.pounce(own ? s.localFrom : s.from, own ? s.localDir : s.dir, speed, own ? s.localReach : s.reach, t));
            }
            case POUNCE_FLIP -> {
                // Over them from wherever his body really is at the contact (no pull back to the server's point).
                steer(player, PantherPath.flip(s.localFor == POUNCE_FLIP ? s.localFrom : s.from, s.apex, s.to, Math.min(1, t / FLIP_TICKS)));
                // Through the flip he turns right round, so he comes down facing the one he went over.
                if (!Float.isNaN(s.flipYaw)) {
                    float k = PantherMotion.ease(Math.min(1, (t - 1) / FLIP_TICKS));
                    float yaw = s.flipYaw + 180 * k;
                    player.yRotO = player.getYRot();
                    player.setYRot(yaw);
                }
            }
            case POUNCE_KICK -> steer(player, PantherPath.kick(s.to, s.dir, Math.min(t, KICK_TICKS), KICK_HIT, KICK_TICKS));
            case POUNCE_LAND -> { if (t < LAND_TICKS * .5f) steer(player, PantherPath.land(s.to, s.dir, s.land, t, LAND_TICKS, KICK_TICKS, KICK_HIT)); }
            case POUNCE_MISS -> {
                // Momentum carries him on into the rolling flip; after that, his own weight.
                if (t < 2.5f) player.setDeltaMovement(s.dir.x * s.speed * .45, .34, s.dir.z * s.speed * .45);
            }
            case SPIN -> steer(player, PantherPath.spin(s.from, s.dir, s.reach, s.height, Math.min(t, SPIN_TICKS), SPIN_TICKS));
            case DASH -> { int ticks = dashTicks(s.reach); steer(player, PantherPath.dash(s.localFor == DASH ? s.localFrom : s.from, s.to, Math.min(t, ticks), ticks)); }
            case CROSS -> { if (t < 3) player.setDeltaMovement(player.getDeltaMovement().multiply(.4, 1, .4)); }
            case FRENZY -> {
                // The flurry drives forward (as Wolverine's does); with someone in front, the feet keep the range:
                // in when they drift away, back a touch when they crowd him.
                Entity target = mc.level.getEntity(s.target);
                Vec3 to = target == null ? null : target.position().subtract(player.position());
                double d = to == null ? 99 : Math.sqrt(to.x * to.x + to.z * to.z);
                if (d < 1.3 && d > .01) player.setDeltaMovement(player.getDeltaMovement().add(new Vec3(to.x, 0, to.z).normalize().scale(-.06)));
                else if (d > 2.4 && d < 6) player.setDeltaMovement(player.getDeltaMovement().add(new Vec3(to.x, 0, to.z).normalize().scale(.12)));
                else if (d >= 6 && player.onGround()) {
                    Vec3 ahead = Vec3.directionFromRotation(0, player.getYRot()).scale(.075);
                    player.setDeltaMovement(player.getDeltaMovement().add(ahead));
                }
            }
            default -> {}
        }
        if (action == POUNCE || action == POUNCE_FLIP || action == POUNCE_KICK || action == POUNCE_LAND || action == SPIN || action == POUNCE_MISS || action == DASH)
            player.fallDistance = 0;
        if (action != POUNCE_FLIP && action != POUNCE_KICK && action != POUNCE_LAND) s.flipYaw = Float.NaN;
    }
    /** Velocity that puts him exactly on the path at the next tick (collisions still apply). */
    private static void steer(LocalPlayer player, Vec3 at) {
        Vec3 v = at.subtract(player.position());
        if (v.length() > 4) v = v.normalize().scale(4);
        player.setDeltaMovement(v);
    }

    // ------------------------------------------------------------------ the camera
    /** How much the flip camera has taken over the view (0..1). */
    private static float orbitWeight(State s, float t) {
        if (!PantherConfig.FLIP_CAMERA.get()) return 0;
        return switch (s.action) {
            case POUNCE_FLIP -> PantherMotion.ease(t / 1.6f);
            case POUNCE_KICK -> 1;
            case POUNCE_LAND -> 1 - PantherMotion.ease((t - 2.5f) / 5.5f);
            default -> 0;
        };
    }
    /** Round the action: behind him at the contact, his side as he goes over, three-quarter behind him for the kick. */
    private static float orbitAngle(State s, float t) {
        float k = switch (s.action) {
            case POUNCE_FLIP -> .62f * PantherMotion.ease(t / FLIP_TICKS);
            case POUNCE_KICK -> .62f + .16f * PantherMotion.ease(t / KICK_TICKS);
            case POUNCE_LAND -> .78f + .22f * PantherMotion.ease(t / LAND_TICKS);
            default -> 0;
        };
        return 180 * k;
    }
    @SubscribeEvent public static void cameraType(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        State s = mc.player == null ? null : get(mc.player);
        boolean want = s != null && !FilmDirector.playing() && orbitWeight(s, clock(s, 0)) > .02f
                && (s.action == POUNCE_FLIP || s.action == POUNCE_KICK || s.action == POUNCE_LAND && clock(s, 0) < LAND_TICKS - 1);
        if (want) {
            if (savedCamera == null) { savedCamera = mc.options.getCameraType(); mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
        } else if (savedCamera != null) { mc.options.setCameraType(savedCamera); savedCamera = null; }
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || FilmDirector.playing() || !isHero(mc.player)) return;
        State s = get(mc.player);
        if (s == null) return;
        float partial = (float) e.getPartialTick(), t = clock(s, partial);
        if (s.action == SPIN && mc.options.getCameraType().isFirstPerson()) {
            // A slight roll with the spin: the view feels the turn without spinning.
            float k = PantherMotion.k(t, 0, 3) * (1 - PantherMotion.k(t, SPIN_TICKS - 3, SPIN_TICKS));
            e.setRoll(e.getRoll() + 2.5f * k * Mth.sin(t * .75f));
            return;
        }
        float w = orbitWeight(s, t);
        if (w <= .001f || savedCamera == null) return;
        Entity target = mc.level.getEntity(s.target);
        Vec3 me = mc.player.getPosition(partial).add(0, .9, 0);
        Vec3 them = target != null ? target.getPosition(partial).add(0, target.getBbHeight() * .5, 0) : s.to;
        Vec3 centre = me.lerp(them, s.action == POUNCE_LAND ? .35 : .5);
        double a = Math.toRadians(orbitAngle(s, t));
        // Start behind him (against the pounce), swing round his left side to behind him the other way.
        Vec3 back = s.dir.scale(-1);
        Vec3 side = new Vec3(-s.dir.z, 0, s.dir.x);
        Vec3 offset = back.scale(Math.cos(a)).add(side.scale(Math.sin(a))).scale(4.3).add(0, 1.5 - .4 * Math.sin(a), 0);
        Vec3 pos = centre.add(offset);
        var hit = mc.level.clip(new ClipContext(centre, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) pos = hit.getLocation().lerp(centre, .15);
        Vec3 dir = centre.subtract(pos).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), pitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1, 1)));
        Camera camera = e.getCamera();
        Vec3 eye = mc.player.getEyePosition(partial);
        setCameraPosition(camera, eye.lerp(pos, w));
        e.setYaw(Mth.rotLerp(w, e.getYaw(), yaw));
        e.setPitch(Mth.lerp(w, e.getPitch(), pitch));
    }
    private static void setCameraPosition(Camera camera, Vec3 pos) {
        try {
            if (cameraPosition == null) {
                for (Field f : Camera.class.getDeclaredFields()) if (f.getType() == Vec3.class) { cameraPosition = f; break; }
                if (cameraPosition == null) return;
                cameraPosition.setAccessible(true);
            }
            cameraPosition.set(camera, pos);
        } catch (ReflectiveOperationException ignored) { }
    }
    @SubscribeEvent public static void fov(ComputeFovModifierEvent e) {
        if (FilmDirector.playing() || !isHero(e.getPlayer())) return;
        // His sprint is faster than a player's: keep the view from stretching with it; the pounce kicks it wide for a moment.
        float base = e.getPlayer().isSprinting() ? 1.12f : 1f;
        e.setNewFovModifier(base * (1 + .1f * fovKick));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        STATES.clear(); eDown = false; crouchDown = false;
        if (savedCamera != null) { Minecraft.getInstance().options.setCameraType(savedCamera); savedCamera = null; }
    }

    // ------------------------------------------------------------------ HUD
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !isHero(mc.player) || FilmDirector.playing()) return;
        State s = get(mc.player);
        if (s == null) return;
        var g = e.getGuiGraphics();
        var font = mc.font;
        int h = e.getWindow().getGuiScaledHeight(), w = e.getWindow().getGuiScaledWidth();
        int violet = 0xFF9A6BFF;
        HudStyle.caption(g, font, "BLACK PANTHER", 10, h - 46, violet, -1);
        int row = h - 124;
        hint(g, font, mc.options.keyAttack, "Basılı: Vahşi Pençe", s.cooldowns[CD_FRENZY], 10, row - 48);
        hint(g, font, mc.options.keyUse, "Pençe Atılışı (işaretli hedefe)", s.cooldowns[CD_DASH], 10, row - 36);
        hint(g, font, mc.options.keyShift, "Dokun: Atılış · Basılı: Koş", s.cooldowns[CD_POUNCE], 10, row - 24);
        hint(g, font, mc.options.keySprint, "Basılı: Eğil / Kamuflaj", s.camoLeft > 0 ? 0 : s.cooldowns[CD_CAMO], s.camoLeft > 0, 10, row - 12);
        hint(g, font, mc.options.keyJump, "Havada: Çift Zıplama", 0, 10, row);
        hint(g, font, AbilityKeyHandler.KEY_ULTIMATE, "Dönen Üçlü Tekme", s.cooldowns[CD_SPIN], 10, row + 12);
        hint(g, font, mc.options.keyInventory, "Vibranyum Patlaması", s.cooldowns[CD_RELEASE], 10, row + 24);
        hint(g, font, AbilityKeyHandler.KEY_RAPID_FIRE, s.reflexLeft > 0 ? "Refleks açık" : "Panter Refleksi", s.reflexLeft > 0 ? 0 : s.cooldowns[CD_REFLEX], 10, row + 36);
        hint(g, font, AbilityKeyHandler.KEY_XRAY, "Son Kovalamaca", s.cooldowns[CD_ULT], 10, row + 48);
        // The stored kinetic energy: a bar that brightens as it fills.
        int y = h - 58, bw = 92;
        float energy = s.energy;
        HudStyle.caption(g, font, "Vibranyum", 10, y - 10, energy > .05f ? violet : HudStyle.MUTED, -1);
        float pulse = energy >= .999f ? .75f + .25f * Mth.sin((mc.level.getGameTime() + e.getPartialTick()) * .4f) : 1;
        HudStyle.bar(g, 64, y - 8, bw, energy, HudStyle.alpha(violet, Math.max(.45f, energy) * pulse));
        float time = mc.level.getGameTime() + e.getPartialTick();
        float cx = w / 2f, cy = h / 2f;
        // Panther Reflex: a violet ring round the crosshair running down, a flash racing out with every parry.
        if (s.reflexLeft > 0) {
            float left = s.reflex;
            boolean low = left < .25f;
            float blink = low ? .55f + .45f * Mth.sin(time * .9f) : 1;
            ring(g, cx, cy, 13, 2.2f, left, HudStyle.alpha(violet, blink), time, false);
            float since = time - parryAt;
            if (since >= 0 && since < 8) {
                float k = since / 8;
                HudStyle.arc(g, cx, cy, 13 + 9 * k, 14.5f + 9 * k, 0, 360, HudStyle.alpha(0xFFE8DCFF, .7f * (1 - k)));
            }
            HudStyle.caption(g, font, String.format(Locale.ROOT, "Refleks %.1f", s.reflexLeft / 20f), (int) cx, (int) cy + 25, HudStyle.alpha(violet, blink), 0);
        }
        // The camouflage: the crouch gathering it (a pale ring filling, sparks of light along it), then its time running
        // out as a shimmering ring of bent light.
        int a = action(s);
        int glass = 0xFFB8E4F8;
        if (a == SNEAK && s.camoLeft <= 0) {
            boolean waiting = s.cooldowns[CD_CAMO] > 0;
            float k = waiting ? 0 : Math.min(1, clock(s, e.getPartialTick()) / CAMO_CHARGE);
            ring(g, cx, cy, 18, 1.6f, waiting ? 1 - s.cooldowns[CD_CAMO] / (float) Math.max(1, PantherConfig.CAMO_COOLDOWN.get()) : k,
                    waiting ? 0x66B0B8C0 : glass, time, !waiting);
            HudStyle.caption(g, font, waiting ? String.format(Locale.ROOT, "Kamuflaj %.1f sn", s.cooldowns[CD_CAMO] / 20f) : "Kamuflaj toplanıyor",
                    (int) cx, (int) cy + (s.reflexLeft > 0 ? 35 : 25), waiting ? HudStyle.MUTED : glass, 0);
        }
        if (s.camoLeft > 0) {
            float left = s.camoLeft / (float) PantherConfig.CAMO_DURATION.get();
            float[] change = PantherFx.camoChange(mc.player.getId());
            float since = change == null ? 99 : time - change[0];
            float pop = since < 8 ? 1 + .35f * (1 - since / 8) : 1;
            ring(g, cx, cy, 18 * pop, 1.8f, left, HudStyle.alpha(glass, left < .25f ? .55f + .45f * Mth.sin(time * .9f) : 1), time, true);
            HudStyle.caption(g, font, String.format(Locale.ROOT, "Görünmez %.1f", s.camoLeft / 20f), (int) cx, (int) cy + (s.reflexLeft > 0 ? 35 : 25), glass, 0);
        }
    }
    /** When the local Panther last parried (level time), for the reflex ring's flash. */
    static float parryAt = -100;
    /**
     * A ring round the crosshair: a faint full track, the filled part from the top going clockwise (with a soft glow
     * under it and a bright head), and, shimmering, light running round it like light bent through glass.
     */
    private static void ring(net.minecraft.client.gui.GuiGraphics g, float cx, float cy, float r, float width, float progress, int color, float time, boolean shimmer) {
        progress = Mth.clamp(progress, 0, 1);
        HudStyle.arc(g, cx, cy, r, r + width, 0, 360, 0x22FFFFFF);
        if (progress <= 0) return;
        float end = -90 + 360 * progress;
        HudStyle.arc(g, cx, cy, r - 1.5f, r + width + 1.5f, -90, end, HudStyle.alpha(color, .2f));
        if (!shimmer) HudStyle.arc(g, cx, cy, r, r + width, -90, end, color);
        else {
            int pieces = Math.max(1, (int) (36 * progress));
            for (int i = 0; i < pieces; i++) {
                float a0 = -90 + 360 * progress * i / pieces, a1 = -90 + 360 * progress * (i + 1) / pieces;
                float lit = .55f + .45f * Mth.sin(i * .7f - time * .45f);
                HudStyle.arc(g, cx, cy, r, r + width, a0, a1 + .5f, HudStyle.alpha(color, lit));
            }
        }
        HudStyle.arc(g, cx, cy, r - 1, r + width + 1, end - 7, end, 0xFFFFFFFF);
    }
    private static void hint(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, KeyMapping key, String what, int cooldown, int x, int y) {
        hint(g, font, key, what, cooldown, false, x, y);
    }
    /** One row of the skill list in his colour (HudStyle.skill); active = running right now. */
    private static void hint(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, KeyMapping key, String what, int cooldown, boolean active, int x, int y) {
        String k = key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        HudStyle.skill(g, font, k, what, cooldown, active, x, y, 0xFFA77BFF);
    }
}
