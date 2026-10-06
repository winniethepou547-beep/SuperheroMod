package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.input.AbilityKeyHandler;
import com.FIRNI.superheromod.client.render.ClientScreenShake;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.heroes.iceman.IcemanConfig;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.IcemanInputPacket;
import com.FIRNI.superheromod.network.packet.IcemanStatePacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/**
 * Iceman on the client: every Iceman's synced state (IcemanStatePacket), his own input (E held: the Ice Armory wheel;
 * SHIFT held: the ice slide; CTRL: the sub-zero slide), his view (the FOV while sliding, the wheel's locked view) and
 * his HUD (the skills, the weapon, the shell's health, the slide's time). The slides' steering of his body is in
 * IcemanSlideFx (it knows the track).
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class IcemanClient {
    /** One Iceman as his clients know him. */
    public static final class State {
        /** What he is doing and since when (server ticks; clock() runs it smoothly). */
        public int action, age;
        /** F_WHEEL, F_WEAPON (in hand), F_SHELL (up), F_SCULPTING, F_SLIDING. */
        public int flags;
        /** The weapon picked (W_*), the combo's swing (0, 1, 2 = finisher). */
        public int weapon, combo;
        /** The hold's charge (0..1): the mace's growth, the spear's draw, the spin's time. */
        public float charge;
        /** The body the brush is on (-1: none / sculpting into the air). */
        public int brushTarget = -1;
        /** The shell's health (0..1), the slide's time left (0..1). */
        public float shell, slide;
        /** The sculpture being made (0: none). */
        public int sculpture;
        public int[] cooldowns = new int[COOLDOWNS];
        /** Game time the last packet arrived, and when the current action began. */
        public long received, start;
        public boolean wheel() { return (flags & F_WHEEL) != 0; }
        public boolean weaponOut() { return (flags & F_WEAPON) != 0; }
        public boolean shellUp() { return (flags & F_SHELL) != 0; }
        public boolean sculpting() { return (flags & F_SCULPTING) != 0; }
        public boolean sliding() { return (flags & F_SLIDING) != 0; }
    }
    public static final int ICE = 0xFF8FD8FF, ICE_PALE = 0xFFD8F2FF, ICE_DEEP = 0xFF2E7FD0;

    private static final Map<Integer, State> STATES = new HashMap<>();

    // his own input
    private static boolean eDown, ctrlDown, slideSent;
    private static boolean wheelOpen;
    private static float wheelX, wheelY, lockYaw, lockPitch, wheelShown;
    private static int hovered = -1;
    private static long dashStart = -100, wheelOpenedAt;
    private static float dashWorldYaw;
    private static float fovKick, fovSpeed;

    private IcemanClient() {}

    public static boolean isHero(Entity e) { return e instanceof Player && ID.equals(ClientHeroRegistry.get(e.getUUID())); }
    /** The state of this Iceman, or null when he is not one (or nothing has arrived yet). */
    public static State get(Entity e) { return e == null || !isHero(e) ? null : STATES.get(e.getId()); }
    public static State get(int id) { return STATES.get(id); }
    static Map<Integer, State> states() { return STATES; }
    /** His action's clock in ticks, smooth between packets. */
    public static float clock(State s, float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? s.age : (float) Math.max(0, level.getGameTime() - s.start) + partial;
    }
    /** The weapon wheel is open on this client (left click picks then, it never swings). */
    public static boolean wheelOpen() { return wheelOpen; }
    public static void shake(float amount) { ClientScreenShake.add(amount * IcemanConfig.SHAKE.get().floatValue()); }
    /** A widening of his own view (a burst of speed); speed (0..1) is a steady widening while it lasts (the slide). */
    public static void kickFov(float amount) { fovKick = Math.min(1, fovKick + amount); }
    public static void speedFov(float k) { fovSpeed = Math.max(fovSpeed, k); }
    /** When his own sub-zero slide began (game time) and its way (radians, world yaw), for the steering. */
    public static long dashStart() { return dashStart; }
    public static float dashYaw() { return dashWorldYaw; }
    public static boolean isMe(int id) { var mc = Minecraft.getInstance(); return mc.player != null && mc.player.getId() == id; }
    static long now() { var level = Minecraft.getInstance().level; return level == null ? 0 : level.getGameTime(); }
    static void send(int kind, int value, float amount) { ModNetworking.CHANNEL.sendToServer(new IcemanInputPacket(kind, value, amount)); }

    public static void receive(IcemanStatePacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        State s = STATES.computeIfAbsent(p.entity(), id -> new State());
        long now = mc.level.getGameTime();
        if (p.action() != s.action || Math.abs(now - s.start - p.age()) > 3) s.start = now - p.age();
        s.action = p.action(); s.age = p.age(); s.flags = p.flags(); s.weapon = p.weapon(); s.combo = p.combo(); s.charge = p.charge();
        s.brushTarget = p.brushTarget(); s.shell = p.shell(); s.sculpture = p.sculpture(); s.slide = p.slide(); s.cooldowns = p.cooldowns();
        s.received = now;
    }

    // ------------------------------------------------------------------ input
    /**
     * Read at the start of the tick, before the game handles its keys: E (held: the wheel, never the inventory while he
     * is Iceman), SHIFT (held: the ice slide), CTRL (the sub-zero slide); left click while the wheel is open picks.
     */
    @SubscribeEvent public static void keys(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player)) {
            eDown = ctrlDown = false;
            if (wheelOpen) closeWheel(false);
            if (slideSent) { slideSent = false; }
            return;
        }
        if (mc.screen != null || FilmDirector.playing()) {
            if (wheelOpen) closeWheel(false);
            if (slideSent) { send(IN_SLIDE_OFF, 0, 0); slideSent = false; }
            return;
        }
        var p = mc.player;
        State s = get(p);
        // E: held opens the wheel and letting go picks what the cursor is on; a quick tap leaves it open (left click or
        // E again picks).
        boolean clicked = false;
        while (mc.options.keyInventory.consumeClick()) clicked = true;
        boolean down = mc.options.keyInventory.isDown();
        if ((down || clicked) && !eDown) {
            if (!wheelOpen) { openWheel(p); wheelOpenedAt = now(); }
            else closeWheel(true);
        } else if (!down && eDown && wheelOpen && now() - wheelOpenedAt >= 5) closeWheel(true);
        eDown = down;
        if (wheelOpen) {
            boolean pick = false;
            while (mc.options.keyAttack.consumeClick()) pick = true;
            if (pick && hovered >= 0) { send(IN_WEAPON_SELECT, hovered, 0); closeWheel(false); }
        }
        // SHIFT held: the ice slide (read off the key itself: the hero sneak suppressor may clear the input's flag).
        boolean shift = mc.options.keyShift.isDown() && !wheelOpen;
        boolean canSlide = s == null || s.action == IDLE || s.action == SLIDE || s.action == BRUSH || s.action == FORM || s.action == WHEEL;
        if (shift && !slideSent && canSlide && (s == null || s.cooldowns[CD_SLIDE] <= 0)) { send(IN_SLIDE_ON, 0, 0); slideSent = true; }
        else if (!shift && slideSent) { send(IN_SLIDE_OFF, 0, 0); slideSent = false; }
        // CTRL: the sub-zero slide, toward where he is moving (forward when standing).
        long win = mc.getWindow().getWindow();
        boolean ctrl = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (ctrl && !ctrlDown && (s == null || s.cooldowns[CD_DASH] <= 0) && now() - dashStart > DASH_TICKS + 2
                && (s == null || s.action == IDLE || s.action == BRUSH || s.action == STRIKE || s.action == SLIDE || s.action == FORM || s.action == WHEEL)) {
            float f = p.input == null ? 0 : p.input.forwardImpulse, l = p.input == null ? 0 : p.input.leftImpulse;
            float rel = Math.abs(f) + Math.abs(l) < .01f ? 0 : (float) Math.atan2(-l, f);
            dashStart = now();
            dashWorldYaw = p.getYRot() * Mth.DEG_TO_RAD + rel;
            send(IN_DASH, 0, rel);
            if (slideSent) slideSent = false;
        }
        ctrlDown = ctrl;
    }
    private static void openWheel(Player p) {
        wheelOpen = true; wheelX = wheelY = 0; hovered = -1;
        lockYaw = p.getYRot(); lockPitch = p.getXRot();
        send(IN_WHEEL_OPEN, 0, 0);
        p.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), .25f, 1.9f);
    }
    private static void closeWheel(boolean pick) {
        if (pick && hovered >= 0) send(IN_WEAPON_SELECT, hovered, 0);
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
            // Clockwise from the top: mace, spear, sword.
            double a = Math.atan2(wheelX, -wheelY);
            hovered = Math.floorMod((int) Math.round(a / (Math.PI * 2 / WEAPONS)), WEAPONS);
        }
        p.setYRot(lockYaw); p.yRotO = lockYaw; p.setXRot(lockPitch); p.xRotO = lockPitch; p.yHeadRot = lockYaw;
        e.setYaw(lockYaw); e.setPitch(lockPitch);
    }

    /** Holding still where his moves hold him: the shell, the broken landing, the ground strike, the heavy holds. */
    @SubscribeEvent public static void hold(MovementInputUpdateEvent e) {
        var mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player || !isHero(mc.player)) return;
        var input = e.getInput();
        input.shiftKeyDown = false;
        State s = get(mc.player);
        if (s == null) return;
        float t = clock(s, 0);
        switch (s.action) {
            case SHELL_FORM, SHELL, SHELL_BURST -> { input.forwardImpulse = input.leftImpulse = 0; input.jumping = false; }
            case SHELL_BREAK -> {
                float k = t < BREAK_TICKS - BREAK_RISE ? 0 : (t - (BREAK_TICKS - BREAK_RISE)) / BREAK_RISE * .5f;
                input.forwardImpulse *= k; input.leftImpulse *= k; input.jumping = false;
            }
            case GROUND -> { input.forwardImpulse *= .15f; input.leftImpulse *= .15f; input.jumping = false; }
            case CHARGE -> {
                float k = s.weapon == W_SWORD ? .65f : s.weapon == W_MACE ? .35f - .2f * s.charge : .5f;
                input.forwardImpulse *= k; input.leftImpulse *= k;
            }
            case RELEASE -> { input.forwardImpulse *= .3f; input.leftImpulse *= .3f; input.jumping = false; }
            case BRUSH -> { input.forwardImpulse *= .55f; input.leftImpulse *= .55f; }
            case STRIKE -> { input.forwardImpulse *= .6f; input.leftImpulse *= .6f; }
            default -> {}
        }
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
        fovKick *= .86f;
        fovSpeed *= .9f;
        if (STATES.size() > 64) STATES.entrySet().removeIf(en -> mc.level.getEntity(en.getKey()) == null);
    }
    @SubscribeEvent public static void fov(ComputeFovModifierEvent e) {
        if (!isHero(e.getPlayer())) return;
        // His walk never stretches the view; the slide widens it with its speed, bursts kick it out a little.
        e.setNewFovModifier(Mth.lerp(.6f, e.getFovModifier(), 1f) * (1 + .1f * fovKick + .14f * fovSpeed));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        STATES.clear(); wheelOpen = false; eDown = ctrlDown = slideSent = false;
    }

    // ------------------------------------------------------------------ HUD
    @SubscribeEvent public static void crosshair(RenderGuiOverlayEvent.Pre e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player) || e.getOverlay() != VanillaGuiOverlay.CROSSHAIR.type()) return;
        if (wheelOpen) e.setCanceled(true);
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
        HudStyle.caption(g, font, "ICEMAN", 10, h - 46, ICE, -1);
        int row = h - 136;
        String weapon = WEAPON_NAMES[Mth.clamp(s.weapon, 0, WEAPONS - 1)];
        String lmb = !s.weaponOut() ? weapon + " (oluştur)" : s.action == CHARGE && clock(s, 0) >= HOLD_TICKS ? switch (s.weapon) {
            case W_MACE -> "Gürz büyüyor %" + Math.round(s.charge * 100);
            case W_SPEAR -> "Mızrak geriliyor %" + Math.round(s.charge * 100);
            default -> "Kılıç dönüşü";
        } : s.action == STRIKE ? weapon + " " + (s.combo + 1) + ". vuruş" : weapon + " (basılı: güçlü)";
        hint(g, font, mc.options.keyAttack, lmb, s.cooldowns[CD_WEAPON], s.action == STRIKE || s.action == CHARGE, 10, row - 12);
        hint(g, font, mc.options.keyUse, s.action == BRUSH ? (s.brushTarget >= 0 ? "Kriyojenik Fırça: dondur" : "Kriyojenik Fırça: heykel") : "Kriyojenik Fırça (basılı)", s.cooldowns[CD_BRUSH], s.action == BRUSH, 10, row);
        hint(g, font, mc.options.keyInventory, "Buz Cephaneliği (basılı: seç)", 0, wheelOpen, 10, row + 12);
        hint(g, font, mc.options.keyShift, "Buz Kaydırağı (basılı)", s.cooldowns[CD_SLIDE], s.action == SLIDE, 10, row + 24);
        hintRaw(g, font, "CTRL", "Sıfır Altı Kayış", s.cooldowns[CD_DASH], s.action == DASH, 10, row + 36);
        hint(g, font, AbilityKeyHandler.KEY_RAPID_FIRE, "Parçalanmış Zemin", s.cooldowns[CD_GROUND], s.action == GROUND, 10, row + 48);
        hint(g, font, AbilityKeyHandler.KEY_ULTIMATE, s.shellUp() ? "Kabuk: PATLAT" : "Kriyojenik Kabuk", s.cooldowns[CD_SHELL], s.shellUp(), 10, row + 60);
        hint(g, font, AbilityKeyHandler.KEY_XRAY, "Ultimate (henüz yok)", 0, false, 10, row + 72);
        // The shell's health, the slide's time: thin bars under the middle of the screen.
        if (s.shellUp() || s.action == SHELL_BURST) {
            HudStyle.caption(g, font, "KABUK", w / 2, h / 2 + 22, ICE_PALE, 0);
            HudStyle.bar(g, w / 2 - 40, h / 2 + 32, 80, s.shell, ICE);
        }
        if (s.action == SLIDE) HudStyle.bar(g, w / 2 - 30, h / 2 + 26, 60, s.slide, ICE_PALE);
        wheelShown = Mth.clamp(wheelShown + (wheelOpen ? .25f : -.25f), 0, 1);
        if (wheelShown > 0) IcemanWheel.draw(g, font, s, w / 2f, h / 2f, wheelShown, hovered, wheelX, wheelY, time);
    }
    private static int hint(GuiGraphics g, Font font, KeyMapping key, String what, int cooldown, boolean active, int x, int y) {
        return hintRaw(g, font, key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT), what, cooldown, active, x, y);
    }
    private static int hintRaw(GuiGraphics g, Font font, String key, String what, int cooldown, boolean active, int x, int y) {
        return HudStyle.skill(g, font, key, what, cooldown, active, x, y, ICE);
    }
}
