package com.FIRNI.superheromod.client.render.magneto;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.input.AbilityKeyHandler;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.heroes.magneto.MagnetoConfig;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.AbilityInputPacket;
import com.FIRNI.superheromod.network.packet.MagnetoInputPacket;
import com.FIRNI.superheromod.network.packet.MagnetoStatePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.*;

/**
 * Magneto on each client: every Magneto's synced state; his E key (telekinesis, not the inventory); jumping twice into
 * and out of flight; his own flight (steered here so it answers at once: smooth acceleration along where he looks,
 * jump to rise, CTRL to sink, a slow bob when he hangs still); the shakes; the HUD.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class MagnetoClient {
    public static final class State {
        public int action, age, flags, charges, flightAge, held = -1, holdAge, holdTicks, fistAge, punchAge = -1, punchesLeft, shieldAge;
        public int[] cooldowns = new int[COOLDOWNS];
        public float recharge, flySpeed = .5f, flyRise = .32f, fuel = 1;
        public int fuelTicks = 160;
        /** The fist: where it was last tick, where it is, and the server's latest (taken in at the end of each client tick). */
        public Vec3 fist = Vec3.ZERO, fistPrev = Vec3.ZERO, fistNext = Vec3.ZERO;
        long received;
        /** The local clock: the level time the current action began at. */
        double start;
        public boolean flying() { return (flags & MagnetoStatePacket.FLYING) != 0; }
        /** Out of flight in the air: sinking slowly to the ground. */
        public boolean gliding() { return (flags & MagnetoStatePacket.GLIDING) != 0; }
        public boolean shield() { return (flags & MagnetoStatePacket.SHIELD) != 0; }
        public boolean fistUp() { return (flags & MagnetoStatePacket.FIST) != 0; }
        public boolean holding() { return (flags & MagnetoStatePacket.HOLDING) != 0; }
    }
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static boolean eDown, jumpDown;
    private static long lastJump = -100;
    /** His flight's own velocity (the vanilla drag and gravity never touch it; the body is moved by it each tick). */
    private static Vec3 flyVel = null;

    private MagnetoClient() {}

    public static boolean isHero(Entity e) { return e instanceof Player && ID.equals(ClientHeroRegistry.get(e.getUUID())); }
    public static State get(Entity e) { return e == null ? null : STATES.get(e.getId()); }
    public static Iterable<Map.Entry<Integer, State>> all() { return STATES.entrySet(); }

    public static void receive(MagnetoStatePacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        State s = STATES.computeIfAbsent(p.entity(), id -> new State());
        long now = mc.level.getGameTime();
        if (p.action() != s.action || Math.abs(now - s.start - p.age()) > 3) s.start = now - p.age();
        boolean fresh = s.fist.lengthSqr() < 1e-6 || !s.fistUp() || (p.flags() & MagnetoStatePacket.FIST) == 0;
        s.action = p.action(); s.age = p.age(); s.flags = p.flags(); s.cooldowns = p.cooldowns(); s.charges = p.charges(); s.recharge = p.recharge();
        s.flightAge = p.flightAge(); s.held = p.held(); s.holdAge = p.holdAge(); s.holdTicks = p.holdTicks(); s.fistNext = p.fist(); s.fistAge = p.fistAge();
        if (fresh) { s.fist = s.fistPrev = p.fist(); }
        s.flySpeed = p.flySpeed(); s.flyRise = p.flyRise(); s.fuel = p.fuel(); s.fuelTicks = p.fuelTicks();
        s.punchAge = p.punchAge(); s.punchesLeft = p.punchesLeft(); s.shieldAge = p.shieldAge();
        s.received = now;
    }
    /** The action's time, running smoothly between packets. */
    public static float clock(State s, float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? s.age : (float) Math.max(0, level.getGameTime() - s.start) + partial;
    }
    /** Where the iron fist is this frame (between the last two server ticks). */
    public static Vec3 fist(State s, float partial) { return s.fistPrev.lerp(s.fist, Mth.clamp(partial, 0, 1)); }
    public static void shake(float amount) { ClientScreenShake.add(amount * MagnetoConfig.SHAKE.get().floatValue()); }

    // ------------------------------------------------------------------ input
    /** E is his telekinesis (not the inventory) while he is Magneto; two jumps take him up or set him down. */
    @SubscribeEvent public static void keys(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player)) { eDown = false; return; }
        if (mc.screen != null) return;
        boolean clicked = false;
        while (mc.options.keyInventory.consumeClick()) clicked = true;
        boolean down = mc.options.keyInventory.isDown() || clicked;
        if (down && !eDown) ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(AbilitySlot.SKILL_V, true));
        else if (!down && eDown) ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(AbilitySlot.SKILL_V, false));
        eDown = down;
        boolean jump = mc.options.keyJump.isDown();
        long now = mc.level.getGameTime();
        if (jump && !jumpDown) {
            if (now - lastJump <= 7 && !FilmDirector.playing()) { ModNetworking.CHANNEL.sendToServer(new MagnetoInputPacket(INPUT_FLIGHT)); lastJump = -100; }
            else lastJump = now;
        }
        jumpDown = jump;
        // While he flies, the sprint key sinks him: it does not sprint.
        State s = get(mc.player);
        if (s != null && s.flying()) mc.options.keySprint.setDown(false);
    }
    /** Flying: his own client steers his body (smooth, never snapping), and the vanilla walk, jump and sprint stay out of it. */
    @SubscribeEvent public static void steer(MovementInputUpdateEvent e) {
        var player = e.getEntity();
        var mc = Minecraft.getInstance();
        if (player != mc.player || !isHero(player) || FilmDirector.playing()) return;
        State s = get(player);
        if (s == null || !s.flying() && !s.gliding()) { flyVel = null; return; }
        var input = e.getInput();
        if (s.gliding()) {
            // Flight spent: he sinks slowly, arms open, still drifting the way he steers, and lands on his feet.
            float fwd = input.forwardImpulse, lft = input.leftImpulse;
            input.forwardImpulse = 0; input.leftImpulse = 0; input.jumping = false; input.shiftKeyDown = false;
            input.up = input.down = input.left = input.right = false;
            player.getAbilities().flying = false;
            float gy = player.getYRot() * Mth.DEG_TO_RAD;
            Vec3 fwdDir = new Vec3(-Mth.sin(gy), 0, Mth.cos(gy)), sideDir = new Vec3(Mth.cos(gy), 0, Mth.sin(gy));
            Vec3 drift = fwdDir.scale(fwd).add(sideDir.scale(lft));
            if (drift.lengthSqr() > 1) drift = drift.normalize();
            Vec3 want = drift.scale(s.flySpeed * .5).add(0, -.16, 0);
            Vec3 v = flyVel == null ? player.getDeltaMovement() : flyVel;
            flyVel = v.add(want.subtract(v).scale(.12));
            if (player.horizontalCollision) flyVel = new Vec3(flyVel.x * .5, flyVel.y, flyVel.z * .5);
            player.setDeltaMovement(flyVel);
            player.fallDistance = 0;
            return;
        }
        float forward = input.forwardImpulse, left = input.leftImpulse;
        boolean up = input.jumping, down = held(mc, mc.options.keySprint);
        input.forwardImpulse = 0; input.leftImpulse = 0; input.jumping = false; input.shiftKeyDown = false;
        input.up = input.down = input.left = input.right = false;
        player.getAbilities().flying = false;
        double speed = s.flySpeed, rise = s.flyRise;
        Vec3 look = player.getLookAngle();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 side = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 want = look.scale(forward).add(side.scale(left));
        if (want.lengthSqr() > 1) want = want.normalize();
        want = want.scale(speed).add(0, (up ? rise : 0) - (down ? rise : 0), 0);
        // The lift off the ground, then a slow bob while he hangs still.
        float lift = Math.min(1, s.flightAge / (float) LIFT_TICKS);
        if (s.flightAge < LIFT_TICKS) want = want.add(0, .18 * (1 - lift), 0);
        float time = player.tickCount;
        if (Math.abs(forward) + Math.abs(left) < .01f && !up && !down) want = want.add(0, .012 * Mth.cos(time * Mth.TWO_PI / BOB_CYCLE), 0);
        Vec3 v = flyVel == null ? player.getDeltaMovement() : flyVel;
        // Heavy and smooth: he gathers speed and slows over a few ticks (a little quicker to stop than to start).
        double k = want.lengthSqr() > v.lengthSqr() ? .14 : .2;
        flyVel = v.add(want.subtract(v).scale(k));
        // Bumping into something takes that speed away.
        if (player.horizontalCollision) flyVel = new Vec3(flyVel.x * .5, flyVel.y, flyVel.z * .5);
        if (player.verticalCollision && flyVel.y < 0 && player.onGround()) flyVel = new Vec3(flyVel.x, 0, flyVel.z);
        player.setDeltaMovement(flyVel);
        player.fallDistance = 0;
    }
    private static boolean held(Minecraft mc, KeyMapping key) {
        var k = key.getKey();
        long window = mc.getWindow().getWindow();
        if (k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) return k.getValue() >= 0 && com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, k.getValue());
        if (k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, k.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        return false;
    }
    /** His clicks are his own: no vanilla swing or mining alongside. */
    @SubscribeEvent public static void clicks(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player) || !(e.isAttack() || e.isUseItem())) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { STATES.clear(); return; }
        long now = mc.level.getGameTime();
        STATES.entrySet().removeIf(v -> mc.level.getEntity(v.getKey()) == null || now - v.getValue().received > 200);
        // The fist steps once per tick, whenever its packets arrive, so the frames between never jump back.
        for (State s : STATES.values()) { s.fistPrev = s.fist; s.fist = s.fistNext; }
    }
    @SubscribeEvent public static void fov(ComputeFovModifierEvent e) {
        if (FilmDirector.playing() || !isHero(e.getPlayer())) return;
        State s = get(e.getPlayer());
        if (s != null && s.flying()) e.setNewFovModifier(1.05f);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { STATES.clear(); eDown = false; }

    // ------------------------------------------------------------------ HUD
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !isHero(mc.player) || FilmDirector.playing()) return;
        State s = get(mc.player);
        if (s == null) return;
        GuiGraphics g = e.getGuiGraphics();
        Font font = mc.font;
        int h = e.getWindow().getGuiScaledHeight(), w = e.getWindow().getGuiScaledWidth();
        int crimson = 0xFFE0384A, violet = 0xFFA77BFF, steel = 0xFFC8CCD6;
        float time = mc.level.getGameTime() + e.getPartialTick();
        HudStyle.caption(g, font, "MAGNETO", 10, h - 46, crimson, -1);
        int row = h - 124;
        hint(g, font, mc.options.keyShift, s.flying() ? "Uçuş: açık (Boşluk yüksel, CTRL alçal)" : "Uç (ya da Boşluk x2)", 0, s.flying(), 10, row - 36);
        String lmb = s.fistUp() ? "Yumruk (" + s.punchesLeft + " kaldı)" : s.holding() ? "Fırlat" : "Demir Çubuk";
        hint(g, font, mc.options.keyAttack, lmb, s.fistUp() || s.holding() ? 0 : s.cooldowns[CD_SHARD], 10, row - 24);
        int x = hint(g, font, AbilityKeyHandler.KEY_ULTIMATE, "Demir Yağmuru", 0, 10, row - 12);
        // The barrage's charges: three pips, the next one filling.
        int max = MagnetoConfig.BARRAGE_CHARGES.get();
        for (int i = 0; i < max; i++) {
            int px = 10 + x + 8 + i * 9, py = row - 9;
            boolean full = i < s.charges;
            g.fill(px, py, px + 7, py + 4, full ? crimson : 0x40FFFFFF);
            if (i == s.charges) g.fill(px, py, px + Math.round(7 * s.recharge), py + 4, HudStyle.alpha(crimson, .55f));
        }
        hint(g, font, mc.options.keyInventory, s.holding() ? "Savur (fareyle) · Sol: İleri · Sağ: Yukarı" : "Hurda Telekinezisi", s.holding() ? 0 : s.cooldowns[CD_GRAB], s.holding(), 10, row);
        hint(g, font, AbilityKeyHandler.KEY_RAPID_FIRE, s.shield() ? "Kalkanı Patlat" : s.fistUp() ? "Yumruğu Dağıt" : "Dev Demir Yumruk", s.fistUp() || s.shield() ? 0 : s.cooldowns[CD_FIST], s.fistUp(), 10, row + 12);
        hint(g, font, AbilityKeyHandler.KEY_SKILL_F, s.shield() ? "Kalkanı Patlat" : "Manyetik Demir Kalkan", s.shield() ? 0 : s.cooldowns[CD_SHIELD], s.shield(), 10, row + 24);
        hint(g, font, AbilityKeyHandler.KEY_XRAY, "Manyetik İnfaz", s.cooldowns[CD_ULT], 10, row + 36);
        float cx = w / 2f, cy = h / 2f;
        flightBar(g, font, s, w, h, time);
        // Holding someone: a crimson ring round the crosshair running out.
        if (s.holding() && s.holdTicks > 0) {
            float left = 1 - s.holdAge / (float) s.holdTicks;
            ring(g, cx, cy, 13, 2.2f, left, crimson);
            HudStyle.caption(g, font, String.format(Locale.ROOT, "Tutuyor %.1f", Math.max(0, s.holdTicks - s.holdAge) / 20f), (int) cx, (int) cy + 25, crimson, 0);
        }
        // The fist: its punches left as pips under the crosshair.
        if (s.fistUp()) {
            int n = MagnetoConfig.FIST_PUNCHES.get();
            for (int i = 0; i < n; i++) {
                int px = (int) cx - n * 5 + i * 10, py = (int) cy + 18;
                g.fill(px, py, px + 7, py + 7, i < s.punchesLeft ? steel : 0x30FFFFFF);
            }
            HudStyle.caption(g, font, "Sol tık: yumruk", (int) cx, (int) cy + 29, steel, 0);
        }
        // The shield up: a slow violet pulse round the crosshair.
        if (s.shield()) {
            float pulse = .55f + .45f * Mth.sin(time * .12f);
            HudStyle.arc(g, cx, cy, 18, 19.5f, 0, 360, HudStyle.alpha(violet, .5f * pulse));
            for (int i = 0; i < MagnetoConfig.SHIELD_COLUMNS.get(); i++) {
                float a = 360f * i / MagnetoConfig.SHIELD_COLUMNS.get() + time * .4f;
                HudStyle.arc(g, cx, cy, 17, 21, a - 4, a + 4, HudStyle.alpha(steel, .8f));
            }
        }
    }
    private static float barShown;
    /**
     * His flight on the right of the screen: a tall violet bar that drains while he flies and fills back up on the
     * ground, seconds left under it; low, it blinks crimson; spent in the air, it says he is gliding down.
     */
    private static void flightBar(GuiGraphics g, Font font, State s, int w, int h, float time) {
        boolean air = s.flying() || s.gliding();
        barShown = Mth.clamp(barShown + ((air || s.fuel < .995f) ? .08f : -.04f), 0, 1);
        if (barShown <= .01f) return;
        int bh = 86, bw = 5, x = w - 22, y = h / 2 - bh / 2;
        float a = barShown;
        boolean low = s.fuel < .25f;
        int violet = 0xFFA77BFF, crimson = 0xFFE0384A;
        int col = low && (s.flying() ? Mth.sin(time * .6f) > 0 : true) ? crimson : violet;
        // A soft dark slot, the fill rising from the bottom, a bright tip, a faint glow beside it.
        g.fill(x - 3, y - 3, x + bw + 3, y + bh + 3, HudStyle.alpha(0xA0060408, a));
        g.fill(x, y, x + bw, y + bh, HudStyle.alpha(0x30FFFFFF, a));
        int filled = Math.round(bh * Mth.clamp(s.fuel, 0, 1));
        if (filled > 0) {
            g.fill(x, y + bh - filled, x + bw, y + bh, HudStyle.alpha(col, a));
            g.fill(x - 2, y + bh - filled, x, y + bh, HudStyle.alpha(col, .25f * a));
            g.fill(x + bw, y + bh - filled, x + bw + 2, y + bh, HudStyle.alpha(col, .25f * a));
            g.fill(x - 1, y + bh - filled, x + bw + 1, y + bh - filled + 1, HudStyle.alpha(0xFFFFFFFF, .9f * a));
        }
        // A tick for every two seconds.
        for (int i = 1; i < s.fuelTicks / 40; i++) {
            int ty = y + bh - Math.round(bh * i * 40f / s.fuelTicks);
            g.fill(x, ty, x + bw, ty + 1, HudStyle.alpha(0x60000000, a));
        }
        HudStyle.caption(g, font, "UÇUŞ", x + bw / 2 + 1, y - 13, HudStyle.alpha(violet, a), 0);
        String under = s.gliding() ? "SÜZÜLÜYOR" : String.format(Locale.ROOT, "%.1f", s.fuel * s.fuelTicks / 20f);
        HudStyle.caption(g, font, under, s.gliding() ? x + bw + 1 : x + bw / 2 + 1, y + bh + 6, HudStyle.alpha(s.gliding() ? crimson : 0xFFEDE6DD, a), s.gliding() ? 1 : 0);
    }
    private static void ring(GuiGraphics g, float cx, float cy, float r, float width, float progress, int color) {
        progress = Mth.clamp(progress, 0, 1);
        HudStyle.arc(g, cx, cy, r, r + width, 0, 360, 0x22FFFFFF);
        if (progress <= 0) return;
        float end = -90 + 360 * progress;
        HudStyle.arc(g, cx, cy, r - 1.5f, r + width + 1.5f, -90, end, HudStyle.alpha(color, .2f));
        HudStyle.arc(g, cx, cy, r, r + width, -90, end, color);
        HudStyle.arc(g, cx, cy, r - 1, r + width + 1, end - 7, end, 0xFFFFFFFF);
    }
    private static int hint(GuiGraphics g, Font font, KeyMapping key, String what, int cooldown, int x, int y) {
        return hint(g, font, key, what, cooldown, false, x, y);
    }
    /** One row of the skill list in his colour (HudStyle.skill); active = running right now. */
    private static int hint(GuiGraphics g, Font font, KeyMapping key, String what, int cooldown, boolean active, int x, int y) {
        String k = key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        return HudStyle.skill(g, font, k, what, cooldown, active, x, y, 0xFFC04050);
    }
}
