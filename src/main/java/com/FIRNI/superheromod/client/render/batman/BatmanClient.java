package com.FIRNI.superheromod.client.render.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.input.AbilityKeyHandler;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.heroes.batman.BatmanConfig;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BatmanInputPacket;
import com.FIRNI.superheromod.network.packet.BatmanStatePacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Batman on the client: every Batman's synced state (BatmanStatePacket), his own input (R gadget wheel / use, E grapnel,
 * CTRL roll, SPACE glide), steering his own body through the moves the server hands to his client (the pull along the
 * line, the strike's jump and backflip, the roll, the glide), and his HUD (skills, the Batarang belt, the wheel, the
 * grapnel reticle).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class BatmanClient {
    /** One Batman as his clients know him. */
    public static final class State {
        /** What he is doing and since when (server ticks; clock() runs it smoothly). */
        public int action, age;
        /** GLIDING, AIMING (grapnel gun out), WHEEL (gadget wheel open), HOOKED (line attached). */
        public int flags;
        /** The blow of the punch chain (0-based count; BatmanAction.blow(combo) is the pose), Batarangs counted up in a held throw, Batarangs left. */
        public int combo, charge, batarangs = BATARANG_MAX;
        /** How far the next Batarang is (0..1). */
        public float refill;
        /** The gadget picked on the wheel (BatmanAction.G_*). */
        public int gadget;
        /** The roll's direction against his body's facing (radians, 0 = forward, positive = to his right). */
        public float dodgeYaw;
        /** Where the grapnel's hook is (flying or attached), or null; the body it is in (-1 for a block). */
        public Vec3 hook, hookPrev;
        public int hookEntity = -1;
        public int[] cooldowns = new int[COOLDOWNS];
        /** Game time the last packet arrived, and when the current action began. */
        public long received, start;
        public boolean gliding() { return (flags & GLIDING) != 0; }
        public boolean aiming() { return (flags & AIMING) != 0; }
        public boolean wheel() { return (flags & WHEEL) != 0; }
        public boolean hooked() { return (flags & HOOKED) != 0; }
    }
    public static final int GLIDING = 1, AIMING = 2, WHEEL = 4, HOOKED = 8;
    static final int GOLD = 0xFFE8C547, CYAN = 0xFF5FD6FF, STEEL = 0xFFB9C2CC;

    private static final Map<Integer, State> STATES = new HashMap<>();

    // his own input
    private static boolean rDown, eDown, ctrlDown, glideSent;
    private static int rHeld;
    private static boolean wheelOpen;
    private static float wheelX, wheelY, lockYaw, lockPitch, wheelShown;
    private static int hovered = -1;
    // his own moves, steered here
    private static long dodgeStart = -100, pullStart = -100, strikeStart = -100;
    private static boolean pop, letGo, shiftRun;
    private static long letGoAt = -100;
    private static float dodgeWorldYaw;
    private static Vec3 strikeDir = new Vec3(0, 0, 1);
    private static int strikeTarget = -1;
    private static boolean jumped, flipped;
    private static float fovKick;

    private BatmanClient() {}

    public static boolean isHero(Entity e) { return e instanceof Player && ID.equals(ClientHeroRegistry.get(e.getUUID())); }
    /** The state of this Batman, or null when he is not one (or nothing has arrived yet). */
    public static State get(Entity e) { return e == null || !isHero(e) ? null : STATES.get(e.getId()); }
    /** His action's clock in ticks, smooth between packets. */
    public static float clock(State s, float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? s.age : (float) Math.max(0, level.getGameTime() - s.start) + partial;
    }
    static Map<Integer, State> states() { return STATES; }
    /** The gadget wheel is open on this client (left click picks a gadget then, it never punches). */
    public static boolean wheelOpen() { return wheelOpen; }
    public static void shake(float amount) { ClientScreenShake.add(amount * BatmanConfig.SHAKE.get().floatValue()); }

    public static void receive(BatmanStatePacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        State s = STATES.computeIfAbsent(p.entity(), id -> new State());
        long now = mc.level.getGameTime();
        if (p.action() != s.action || Math.abs(now - s.start - p.age()) > 3) s.start = now - p.age();
        s.action = p.action(); s.age = p.age(); s.flags = p.flags(); s.combo = p.combo(); s.charge = p.charge();
        s.batarangs = p.batarangs(); s.refill = p.refill(); s.gadget = p.gadget(); s.dodgeYaw = p.dodgeYaw();
        s.hookPrev = s.hook == null ? p.hook() : s.hook; s.hook = p.hook(); s.hookEntity = p.hookEntity(); s.cooldowns = p.cooldowns();
        s.received = now;
    }
    /** From BatmanFx: the line caught (start the pull), the strike began (its target and direction), the pull arrived. */
    static void hookHit(int batman) { if (isMe(batman)) { pullStart = now(); } }
    static void strike(int batman, int target, Vec3 dir) {
        if (!isMe(batman)) return;
        strikeStart = now(); strikeTarget = target; strikeDir = dir.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : dir.normalize();
        jumped = flipped = false; pullStart = -100;
    }
    static void arrived(int batman, boolean popUp) { if (isMe(batman)) { pullStart = -100; if (popUp) pop = true; } }
    private static boolean isMe(int id) { var mc = Minecraft.getInstance(); return mc.player != null && mc.player.getId() == id; }
    private static long now() { var level = Minecraft.getInstance().level; return level == null ? 0 : level.getGameTime(); }
    private static void send(int kind, int value, float amount) { ModNetworking.CHANNEL.sendToServer(new BatmanInputPacket(kind, value, amount)); }

    // ------------------------------------------------------------------ input
    /**
     * Read at the start of the tick, before the game handles its keys: R (tap = use the gadget, held = the wheel),
     * E (the grapnel, never the inventory while he is Batman), CTRL (the roll), SPACE in the air (the glide); left click
     * while the wheel is open picks the gadget (it never punches then).
     */
    @SubscribeEvent public static void keys(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player)) { rDown = eDown = ctrlDown = false; if (wheelOpen) closeWheel(false); return; }
        if (mc.screen != null || FilmDirector.playing()) { if (wheelOpen) closeWheel(false); return; }
        var p = mc.player;
        State s = get(p);
        // R: a tap uses the gadget picked, held it opens the wheel.
        boolean r = AbilityKeyHandler.KEY_RAPID_FIRE.isDown();
        if (r) {
            rHeld++;
            if (rHeld == TAP_TICKS && !wheelOpen) openWheel(p);
        } else if (rDown) {
            if (wheelOpen) closeWheel(true);
            else if (rHeld < TAP_TICKS) send(IN_GADGET_USE, 0, 0);
            rHeld = 0;
        }
        rDown = r;
        if (wheelOpen) {
            boolean pick = false;
            while (mc.options.keyAttack.consumeClick()) pick = true;
            if (pick && hovered >= 0) { send(IN_GADGET_SELECT, hovered, 0); closeWheel(false); rHeld = TAP_TICKS + 99; }
        }
        // E: the grapnel gun out / away.
        boolean clicked = false;
        while (mc.options.keyInventory.consumeClick()) clicked = true;
        boolean ed = mc.options.keyInventory.isDown() || clicked;
        if (ed && !eDown) {
            // Pulled along the line: E lets go (the hop is thrown here at once, the server follows).
            if (s != null && s.action == GRAPNEL_PULL && now() - letGoAt > 10) { letGo = true; letGoAt = now(); }
            send(IN_GRAPNEL_TOGGLE, 0, 0);
        }
        eDown = ed;
        // CTRL: the roll, toward where he is moving (forward when standing).
        long win = mc.getWindow().getWindow();
        boolean ctrl = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (ctrl && !ctrlDown && (s == null || s.cooldowns[CD_DODGE] <= 0) && now() - dodgeStart > DODGE_TICKS + 2
                && (s == null || (s.action != GRAPNEL_PULL && s.action != GRAPNEL_STRIKE && s.action != GRAPNEL_FIRE))) {
            float f = p.input == null ? 0 : p.input.forwardImpulse, l = p.input == null ? 0 : p.input.leftImpulse;
            float rel = Math.abs(f) + Math.abs(l) < .01f ? 0 : (float) Math.atan2(-l, f);
            dodgeStart = now();
            dodgeWorldYaw = p.getYRot() * Mth.DEG_TO_RAD + rel;
            send(IN_DODGE, 0, rel);
        }
        ctrlDown = ctrl;
        // SPACE held in the air (falling): the cape spreads.
        boolean busyMove = s != null && (s.action == GRAPNEL_PULL && now() - letGoAt > 6 || s.action == GRAPNEL_STRIKE || s.action == DODGE);
        boolean want = mc.options.keyJump.isDown() && !p.onGround() && !p.isInWater() && !p.getAbilities().flying && !busyMove
                && (glideSent || p.getDeltaMovement().y < -.08);
        if (want != glideSent) { send(want ? IN_GLIDE_ON : IN_GLIDE_OFF, 0, 0); glideSent = want; }
    }
    private static void openWheel(Player p) {
        wheelOpen = true; wheelX = wheelY = 0; hovered = -1;
        lockYaw = p.getYRot(); lockPitch = p.getXRot();
        send(IN_WHEEL_OPEN, 0, 0);
        p.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), .25f, 1.8f);
    }
    private static void closeWheel(boolean pick) {
        if (pick && hovered >= 0) send(IN_GADGET_SELECT, hovered, 0);
        wheelOpen = false;
        send(IN_WHEEL_CLOSE, 0, 0);
    }
    /** While the wheel is open the mouse moves its cursor, not the view. */
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e) {
        var mc = Minecraft.getInstance();
        if (!wheelOpen || mc.player == null) return;
        var p = mc.player;
        float dy = Mth.wrapDegrees(p.getYRot() - lockYaw), dp = p.getXRot() - lockPitch;
        wheelX = Mth.clamp(wheelX + dy, -30, 30); wheelY = Mth.clamp(wheelY + dp, -30, 30);
        float len = Mth.sqrt(wheelX * wheelX + wheelY * wheelY);
        if (len > 30) { wheelX *= 30 / len; wheelY *= 30 / len; }
        if (len > 6) {
            // Clockwise from the top: smoke, flash, mine, wrist cannon, sonic trap.
            double a = Math.atan2(wheelX, -wheelY);
            hovered = Math.floorMod((int) Math.round(a / (Math.PI * 2 / GADGETS)), GADGETS);
        }
        p.setYRot(lockYaw); p.yRotO = lockYaw; p.setXRot(lockPitch); p.xRotO = lockPitch; p.yHeadRot = lockYaw;
        e.setYaw(lockYaw); e.setPitch(lockPitch);
    }

    // ------------------------------------------------------------------ his body's moves
    /** The pull, the strike, the roll and the glide move his body; the vanilla walk stays out of those. */
    @SubscribeEvent public static void steer(MovementInputUpdateEvent e) {
        var p = e.getEntity();
        var mc = Minecraft.getInstance();
        if (p != mc.player || !isHero(p) || FilmDirector.playing()) return;
        State s = get(p);
        if (s == null) return;
        long now = now();
        var input = e.getInput();
        Vec3 v = p.getDeltaMovement();
        // The roll (Elden Ring style): a dive off the front foot (a low hop forward), over the shoulder along the
        // ground and up, carried a long way and easing out at the end.
        float dt = now - dodgeStart;
        if (dt >= 0 && dt < DODGE_TICKS) {
            double k = 1 - .75 * dt / DODGE_TICKS, speed = DODGE_DIST / DODGE_TICKS * 1.55 * k;
            Vec3 dir = new Vec3(-Mth.sin(dodgeWorldYaw), 0, Mth.cos(dodgeWorldYaw));
            double vy = dt < 1 && p.onGround() ? .24 : Math.min(v.y, p.onGround() ? 0 : v.y);
            p.setDeltaMovement(dir.x * speed, vy, dir.z * speed);
            input.forwardImpulse = input.leftImpulse = 0; input.jumping = false;
            return;
        }
        // E while pulled: let go, carried on by the pull and hopping up out of it.
        if (letGo) {
            letGo = false; pullStart = -100;
            p.setDeltaMovement(v.x * 1.1, Math.max(v.y, 0) + .78, v.z * 1.1);
            p.fallDistance = 0;
            fovKick = Math.min(1, fovKick + .3f);
            return;
        }
        // Pulled along the line: faster and faster toward the hook.
        if (s.action == GRAPNEL_PULL && s.hook != null && now - letGoAt > 6) {
            if (pullStart < 0 || now - pullStart > 120) pullStart = now;
            float age = now - pullStart;
            Vec3 to = s.hook.subtract(p.position().add(0, 1, 0));
            double d = to.length();
            double speed = Mth.lerp(Math.min(1, age / 7f), PULL_START, PULL_SPEED) * Math.min(1, d / 2.5 + .25);
            Vec3 want = d < 1e-3 ? Vec3.ZERO : to.scale(speed / d);
            p.setDeltaMovement(v.lerp(want, .55));
            p.fallDistance = 0;
            input.forwardImpulse = input.leftImpulse = 0; input.jumping = false;
            fovKick = Math.min(1, fovKick + .15f);
            return;
        }
        // The strike: hold close for the uppercut, spring up after them, the kick, then the backflip away.
        if (s.action == GRAPNEL_STRIKE && now - strikeStart < STRIKE_TICKS + 4) {
            float t = now - strikeStart;
            Entity target = strikeTarget >= 0 ? mc.level.getEntity(strikeTarget) : null;
            if (t < STRIKE_JUMP) p.setDeltaMovement(v.x * .3, Math.max(v.y, -.05), v.z * .3);
            else if (!jumped) { jumped = true; p.setDeltaMovement(strikeDir.x * .1, .98, strikeDir.z * .1); }
            else if (t < STRIKE_FLIP && target != null) {
                // Rising with them to the same height, a little behind.
                Vec3 to = target.position().subtract(strikeDir.scale(1.2)).subtract(p.position());
                p.setDeltaMovement(to.x * .3, Math.max(v.y, to.y * .35), to.z * .3);
            } else if (t >= STRIKE_FLIP && !flipped) { flipped = true; p.setDeltaMovement(-strikeDir.x * .55, .42, -strikeDir.z * .55); }
            p.fallDistance = 0;
            input.forwardImpulse = input.leftImpulse = 0; input.jumping = false;
            return;
        }
        // Arrived at a ledge: a small hop up over it.
        if (pop) { pop = false; p.setDeltaMovement(p.getLookAngle().x * .25, .55, p.getLookAngle().z * .25); return; }
        // The glide: carried forward along the view like a wingsuit, sinking slowly; only a steep dive (looking well
        // down) trades height for speed. The game's own gravity and air drag are taken back out of the velocity first
        // (they were added after last tick's move), so the glide is what this sets, not a fall fighting it.
        if (glideSent && !p.onGround()) {
            float pitch = p.getXRot() * Mth.DEG_TO_RAD;
            double dive = Math.max(0, Math.sin(pitch) - .5) / .5, climb = Math.max(0, -Math.sin(pitch));
            double speed = GLIDE_SPEED * (1 + dive * GLIDE_DIVE) * (1 - climb * .3);
            double sink = GLIDE_SINK + dive * .4 + climb * .02;
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            Vec3 was = new Vec3(v.x / .91, v.y / .98 + .08, v.z / .91);
            Vec3 want = new Vec3(-Mth.sin(yaw) * speed, -sink, Mth.cos(yaw) * speed);
            // The cape catches the air at once (the fall is arrested quickly), the forward speed builds up smoothly.
            Vec3 next = new Vec3(Mth.lerp(.1, was.x, want.x), Mth.lerp(.28, was.y, want.y), Mth.lerp(.1, was.z, want.z));
            p.setDeltaMovement(next);
            p.fallDistance = 0;
            input.forwardImpulse = input.leftImpulse = 0;
            fovKick = Math.min(.6f, fovKick + .05f);
        }
    }
    /** SHIFT held is his run (the vanilla sneak is not used for him): sprinting, 1.3 times the walk. */
    @SubscribeEvent public static void run(MovementInputUpdateEvent e) {
        var mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player || !isHero(mc.player)) { shiftRun = false; return; }
        var p = mc.player;
        var keys = e.getInput();
        boolean shift = keys.shiftKeyDown;
        keys.shiftKeyDown = false;
        boolean moving = keys.forwardImpulse > .1f;
        if (shift && moving && !p.isInWater() && !BatmanBound.bound()) { p.setSprinting(true); shiftRun = true; }
        else if (shiftRun && (!shift || !moving)) { shiftRun = false; p.setSprinting(false); }
    }
    /** His clicks are his own: no vanilla swing, mining or item use alongside. */
    @SubscribeEvent public static void clicks(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player) || !(e.isAttack() || e.isUseItem())) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { STATES.clear(); return; }
        fovKick *= .85f;
        if (STATES.size() > 64) STATES.entrySet().removeIf(en -> mc.level.getEntity(en.getKey()) == null);
    }
    @SubscribeEvent public static void fov(ComputeFovModifierEvent e) {
        if (!isHero(e.getPlayer())) return;
        // The running speed should not stretch the view; the pull and the dive widen it a little.
        e.setNewFovModifier(Mth.lerp(.6f, e.getFovModifier(), 1f) * (1 + .12f * fovKick));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        STATES.clear(); wheelOpen = false; rDown = eDown = ctrlDown = glideSent = shiftRun = letGo = false; rHeld = 0;
    }

    // ------------------------------------------------------------------ HUD
    /** The vanilla crosshair gives way to the grapnel reticle and the wheel. */
    @SubscribeEvent public static void crosshair(RenderGuiOverlayEvent.Pre e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player) || e.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        State s = get(mc.player);
        if (wheelOpen || s != null && s.aiming()) e.setCanceled(true);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !isHero(mc.player) || FilmDirector.playing()) return;
        State s = get(mc.player);
        if (s == null) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int h = e.getWindow().getGuiScaledHeight(), w = e.getWindow().getGuiScaledWidth();
        float time = mc.level.getGameTime() + e.getPartialTick();
        HudStyle.caption(g, font, "BATMAN", 10, h - 46, GOLD, -1);
        int row = h - 124;
        String punch = s.action == PUNCH && s.combo > 0 ? (s.combo >= RAPID ? "Seri Yumruk x" + (s.combo + 1) : "Kombo x" + (s.combo + 1)) : "Yumruk";
        hint(g, font, mc.options.keyAttack, s.aiming() ? "Kanca: çekil / düşmana vuruş" : punch, 0, s.action == PUNCH && s.combo >= RAPID || s.aiming(), 10, row - 24);
        hint(g, font, mc.options.keyUse, s.aiming() ? "Kanca: düşmanı bacağından çek" : s.charge > 0 ? "Batarang x" + s.charge + " (bırak)" : "Batarang (basılı: çoklu)", 0, s.charge > 0 || s.aiming(), 10, row - 12);
        hint(g, font, AbilityKeyHandler.KEY_RAPID_FIRE, GADGET_NAMES[s.gadget] + " (basılı: seç)", s.cooldowns[s.gadget], wheelOpen, 10, row);
        hint(g, font, mc.options.keyInventory, s.aiming() ? "Kanca: hazır (sol tık)" : "Kanca", s.cooldowns[CD_GRAPNEL], s.aiming(), 10, row + 12);
        hintRaw(g, font, "CTRL", "Takla", s.cooldowns[CD_DODGE], s.action == DODGE, 10, row + 24);
        hint(g, font, mc.options.keyJump, "Pelerinle Süzül (havada basılı)", 0, s.gliding(), 10, row + 36);
        hint(g, font, mc.options.keyShift, "Koş (basılı)", 0, mc.player.isSprinting(), 10, row + 48);
        belt(g, s, 14, row - 46, time);
        if (s.aiming()) reticle(g, font, mc, w, h, time);
        wheelShown = Mth.clamp(wheelShown + (wheelOpen ? .25f : -.25f), 0, 1);
        if (wheelShown > 0) BatmanWheel.draw(g, font, s, w / 2f, h / 2f, wheelShown, hovered, wheelX, wheelY, time);
    }
    /**
     * The Batarang count over the skill list: five thin plain bat symbols, white for each one he has, the next one
     * filling back in from below; while a throw is held, the ones counted up glow gold.
     */
    private static void belt(GuiGraphics g, State s, int x, int y, float time) {
        float size = 7.5f, step = 19;
        for (int i = 0; i < BATARANG_MAX; i++) {
            float cx = x + size + i * step, cy = y;
            boolean has = i < s.batarangs, counted = s.charge > 0 && i < s.charge;
            int col = counted ? HudStyle.alpha(GOLD, .8f + .2f * Mth.sin(time * 1.4f + i)) : 0xFFF2F0EA;
            float fill = has ? 1 : i == s.batarangs ? s.refill : 0;
            BatmanWheel.bat(g, cx + .6f, cy + .8f, size, 0x90000000, 1);
            BatmanWheel.bat(g, cx, cy, size, 0x38FFFFFF, 1);
            if (fill > 0) BatmanWheel.bat(g, cx, cy, size, has ? col : HudStyle.alpha(col, .7f), fill);
        }
    }
    /** The grapnel reticle: cyan on a block in reach, red on a body (the strike), grey and crossed when out of reach. */
    private static void reticle(GuiGraphics g, Font font, Minecraft mc, int w, int h, float time) {
        var p = mc.player;
        double range = BatmanConfig.GRAPNEL_RANGE.get();
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        var bh = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        boolean body = false;
        for (Entity t : mc.level.getEntities(p, new AABB(eye, stop).inflate(1), en -> en instanceof LivingEntity && en.isAlive()))
            if (t.getBoundingBox().inflate(.3).clip(eye, stop).isPresent()) { body = true; break; }
        boolean block = !body && bh.getType() != HitResult.Type.MISS;
        int col = body ? 0xFFFF4B3E : block ? CYAN : 0xAAB0B0B0;
        float cx = w / 2f, cy = h / 2f, spin = time * (body ? .08f : .03f);
        for (int i = 0; i < 4; i++) {
            float a0 = (float) Math.toDegrees(spin) + i * 90 + 12, a1 = a0 + 66;
            HudStyle.arc(g, cx, cy, 8, 9.6f, a0, a1, col);
        }
        g.fill((int) cx - 1, (int) cy - 1, (int) cx + 1, (int) cy + 1, col);
        if (!body && !block) {
            for (int i = -3; i <= 3; i++) { g.fill((int) cx + i, (int) cy + i, (int) cx + i + 1, (int) cy + i + 1, col); g.fill((int) cx + i, (int) cy - i, (int) cx + i + 1, (int) cy - i + 1, col); }
        }
        String label = body ? "SOL: VURUŞ · SAĞ: ÇEK" : block ? String.format(Locale.ROOT, "%.0f m", bh.getLocation().distanceTo(eye)) : "MENZİL DIŞI";
        HudStyle.caption(g, font, label, (int) cx, (int) cy + 15, col, 0);
    }
    private static int hint(GuiGraphics g, Font font, KeyMapping key, String what, int cooldown, boolean active, int x, int y) {
        return hintRaw(g, font, key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT), what, cooldown, active, x, y);
    }
    private static int hintRaw(GuiGraphics g, Font font, String key, String what, int cooldown, boolean active, int x, int y) {
        return HudStyle.skill(g, font, key, what, cooldown, active, x, y, GOLD);
    }
}
