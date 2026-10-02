package com.FIRNI.superheromod.client.render.thor;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.client.render.film.FilmSessionClient;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.heroes.thor.GodOfThunderSession;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.AbilityInputPacket;
import com.FIRNI.superheromod.network.packet.ThorStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.thor.ThorAction.*;

/**
 * Thor on each client: the state every Thor is in (from the server's clock), the E key, the
 * local Thor's own flight and his rise and dive in the Wakanda strike, and the smoothed pose
 * the layer draws.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ThorClient {
    public static final class State {
        public int action, age, flags;
        public Vec3 hammer = Vec3.ZERO, hammerPrev = Vec3.ZERO;
        long received, lastCombat = -10000, actionStart;
        /** The pose drawn last frame and when, for easing between poses. */
        ThorMotion.Pose shown;
        float shownTime = -1;
        float spinX, spinY, lastSpinTime = -1;
        /** How wound-up the last hammer launch was (0..1), taken from how long the charge lasted. */
        public float dashCharge;
        long shookFor = -1;
        public boolean flying() { return (flags & FLAG_FLYING) != 0; }
        public boolean hammerOut() { return (flags & FLAG_HAMMER_OUT) != 0; }
        public boolean powered() { return (flags & FLAG_POWERED) != 0; }
    }
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static boolean guardDown;

    private ThorClient() {}

    public static boolean isThor(Entity e) { return e instanceof Player && ID.equals(ClientHeroRegistry.get(e.getUUID())); }

    public static void receive(ThorStatePacket p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        State s = STATES.computeIfAbsent(p.entity(), id -> new State());
        long now = level.getGameTime();
        if (p.action() != s.action || p.age() < s.age) s.actionStart = now - p.age();
        if (p.action() != IDLE && p.action() != CATCH) s.lastCombat = now;
        boolean wasOut = s.hammerOut();
        if (p.action() == DASH && s.action == CHARGE) s.dashCharge = Math.min(1, (s.age + 1) / (float) CHARGE_FULL);
        s.action = p.action(); s.age = p.age(); s.flags = p.flags(); s.received = now;
        s.hammerPrev = wasOut && s.hammerOut() ? s.hammer : p.hammer();
        s.hammer = p.hammer();
    }
    public static State get(Entity e) { return e == null ? null : STATES.get(e.getId()); }

    /** Action clock in ticks, running on between packets. */
    public static float clock(State s, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) return s.age;
        if (s.action == ULTIMATE) return s.age + partial;
        return s.age + Math.min(3, Math.max(0, level.getGameTime() - s.received)) + partial;
    }
    /** Film clock of a running God of Thunder for this Thor, or -1. */
    public static float ultimateTime(Entity e, float partial) {
        var film = FilmSessionClient.get(e.getId());
        if (film == null || !GodOfThunderSession.ID.equals(film.film)) return -1;
        return FilmSessionClient.time(film, partial);
    }

    // ------------------------------------------------------------------ pose
    /** The pose to draw this frame, eased from the last one so nothing ever snaps. */
    public static ThorMotion.Pose pose(Player e, float partial, float time) {
        State s = STATES.computeIfAbsent(e.getId(), id -> new State());
        var level = Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        int action = s.action;
        float t = clock(s, partial);
        float ult = ultimateTime(e, partial);
        if (ult >= 0) { action = ULTIMATE; t = ult; }
        Vec3 v = e.getDeltaMovement();
        if (e != Minecraft.getInstance().player) v = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        float speed = (float) v.length();
        boolean combat = now - s.lastCombat < 120;
        float charge = action == CHARGE ? Math.min(1, t / CHARGE_FULL) : s.dashCharge;
        float look = e.getViewXRot(partial) * (float) Math.PI / 180;
        var in = new ThorMotion.Input(action, t, false, s.hammerOut(), s.powered(), combat, speed, time, e.onGround(), charge, look);
        ThorMotion.Pose target = ThorMotion.sample(in);
        // Spins are angles that keep growing; they are carried separately and never eased.
        float spinX = target.wristX, spinY = target.wristY;
        boolean spinning = target.spinMode > 0 && target.spinRing > .01f;
        if (s.shown == null || s.shownTime < 0 || time - s.shownTime > 10 || com.FIRNI.superheromod.client.render.film.FilmDirector.drawingStage()) {
            s.shown = target.copy();
        } else {
            float dt = Math.max(0, time - s.shownTime);
            // Fast enough to keep every authored swing, slow enough to round off any jump between poses.
            float k = 1 - (float) Math.exp(-dt * (action == IDLE ? .35f : .9f));
            float sx = s.shown.wristX, sy = s.shown.wristY;
            s.shown.toward(target, k);
            if (spinning) { s.shown.wristX = spinX; s.shown.wristY = spinY; }
            else if (Math.abs(sx - target.wristX) > 3 || Math.abs(sy - target.wristY) > 3) {
                // Coming out of a spin: unwind to the nearest turn instead of spinning backwards.
                s.shown.wristX = unwind(sx, target.wristX, k); s.shown.wristY = unwind(sy, target.wristY, k);
            }
        }
        s.shownTime = time;
        ThorMotion.Pose out = s.shown.copy();
        out.spinMode = target.spinMode;
        return out;
    }
    private static float unwind(float from, float to, float k) {
        double turn = Math.PI * 2;
        double nearest = to + Math.round((from - to) / turn) * turn;
        return (float) (from + (nearest - from) * k);
    }

    // ------------------------------------------------------------------ input and the local Thor's movement
    @SubscribeEvent public static void beforeKeys(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isThor(mc.player)) { guardDown = false; return; }
        if (mc.screen != null) return;
        // E is Thor's guard: it must not open the inventory while he is Thor.
        boolean clicked = false;
        while (mc.options.keyInventory.consumeClick()) clicked = true;
        boolean down = mc.options.keyInventory.isDown() || clicked;
        if (down && !guardDown) ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(AbilitySlot.SKILL_V, true));
        else if (!down && guardDown) ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(AbilitySlot.SKILL_V, false));
        guardDown = down;
    }

    @SubscribeEvent public static void move(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { STATES.clear(); return; }
        long now = mc.level.getGameTime();
        STATES.entrySet().removeIf(v -> now - v.getValue().received > 400 && v.getValue().action == IDLE && v.getValue().flags == 0
                || mc.level.getEntity(v.getKey()) == null);
        var player = mc.player;
        if (player == null || !isThor(player)) return;
        State s = STATES.get(player.getId());
        if (s == null) return;
        if (com.FIRNI.superheromod.client.render.film.FilmDirector.playing()) return;
        float t = clock(s, 0);
        if (s.action == WAKANDA) { wakandaMove(player, t); return; }
        if (s.action == DASH) dashMove(player, s, t);
        if (s.action == CHARGE) player.setDeltaMovement(player.getDeltaMovement().multiply(.6, 1, .6));
        swingShake(s, t);
    }
    /** A small kick of the camera every time one of his swings lands its weight. */
    private static void swingShake(State s, float t) {
        int hit = s.action == SWING_RIGHT || s.action == SWING_LEFT ? SWING_HIT : s.action == UPPERCUT ? UPPER_HIT : -1;
        if (hit < 0 || t < hit || s.shookFor == s.actionStart) return;
        s.shookFor = s.actionStart;
        com.FIRNI.superheromod.client.render.ClientScreenShake.add(s.action == UPPERCUT ? .2f : .1f);
    }
    /** The launch: pulled along where he looks, as fast and as far as the charge allows, then let go. */
    private static void dashMove(net.minecraft.client.player.LocalPlayer player, State s, float t) {
        player.fallDistance = 0;
        if (t > dashTicks(s.dashCharge)) return;
        Vec3 want = player.getLookAngle().scale(dashSpeed(s.dashCharge));
        // Eases in over the first two ticks so it reads as the hammer pulling, not a teleport.
        float k = Math.min(1, (t + 1) / 3f);
        Vec3 now = player.getDeltaMovement();
        player.setDeltaMovement(now.add(want.subtract(now).scale(k)));
    }
    private static void wakandaMove(net.minecraft.client.player.LocalPlayer player, float t) {
        player.fallDistance = 0;
        Vec3 v = player.getDeltaMovement();
        if (t >= LANDED || t < WK_RISE) { player.setDeltaMovement(0, Math.min(0, v.y), 0); return; }
        if (t < WK_HOVER) {
            float k = (t - WK_RISE) / (WK_HOVER - WK_RISE);
            player.setDeltaMovement(0, WK_RISE_SPEED * (1 - k) + .04, 0);
        } else if (t < WK_DIVE) {
            player.setDeltaMovement(0, Math.sin(t * .3) * .01, 0);
        } else {
            Vec3 ahead = Vec3.directionFromRotation(0, player.getYRot()).scale(.25);
            player.setDeltaMovement(ahead.x, -WK_DIVE_SPEED, ahead.z);
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { STATES.clear(); guardDown = false; }
}
