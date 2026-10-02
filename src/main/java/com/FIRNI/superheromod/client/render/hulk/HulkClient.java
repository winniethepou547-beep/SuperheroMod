package com.FIRNI.superheromod.client.render.hulk;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.input.AbilityKeyHandler;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.client.render.film.FilmSessionClient;
import com.FIRNI.superheromod.heroes.hulk.HulkConfig;
import com.FIRNI.superheromod.heroes.hulk.HulkRageSession;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.HulkInputPacket;
import com.FIRNI.superheromod.network.packet.HulkStatePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.hulk.HulkAction.*;

/**
 * Hulk on each client: every Banner/Hulk's state from the server's clock, the space bar (the leap),
 * the smoothed pose the layer draws, and the HUD.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class HulkClient {
    public static final class State {
        public int action, age, flags, rockBlock, smashDelay = 40;
        public float charge, stamina = STAMINA_MAX;
        public int[] cooldowns = new int[HulkStatePacket.COOLDOWNS];
        public Vec3 rock = Vec3.ZERO, rockPrev = Vec3.ZERO;
        long received, actionStart;
        HulkMotion.Pose shown;
        float shownTime = -1, fallSpeed;
        public boolean hulk() { return (flags & FLAG_HULK) != 0; }
        public boolean rockFlying() { return (flags & FLAG_ROCK_FLYING) != 0; }
        public BlockState rockState() { BlockState s = Block.stateById(rockBlock); return s.isAir() ? Blocks.STONE.defaultBlockState() : s; }
    }
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static boolean spaceDown, tapJump;
    private static int spaceTicks;

    private HulkClient() {}

    public static boolean isHero(Entity e) { return e instanceof Player && ID.equals(ClientHeroRegistry.get(e.getUUID())); }
    public static State get(Entity e) { return e == null ? null : STATES.get(e.getId()); }

    public static void receive(HulkStatePacket p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        State s = STATES.computeIfAbsent(p.entity(), id -> new State());
        long now = level.getGameTime();
        if (p.action() != s.action || p.age() < s.age) s.actionStart = now - p.age();
        boolean flew = s.rockFlying();
        s.action = p.action(); s.age = p.age(); s.flags = p.flags(); s.charge = p.charge(); s.stamina = p.stamina();
        s.rockPrev = flew && s.rockFlying() ? s.rock : p.rock(); s.rock = p.rock(); s.rockBlock = p.rockBlock();
        s.cooldowns = p.cooldowns(); s.smashDelay = p.smashDelay(); s.received = now;
    }
    public static float clock(State s, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) return s.age;
        return s.age + Math.min(3, Math.max(0, level.getGameTime() - s.received)) + partial;
    }
    /** Film clock of a running Gamma Rage for this Hulk, or -1. */
    public static float rageTime(Entity e, float partial) {
        var film = FilmSessionClient.get(e.getId());
        if (film == null || !HulkRageSession.ID.equals(film.film)) return -1;
        return FilmSessionClient.time(film, partial);
    }
    public static int smashTick(Entity e) { State s = get(e); return ULT_CRASH + (s == null ? 40 : s.smashDelay); }

    /** The pose to draw this frame, eased from the last one so nothing snaps. */
    public static HulkMotion.Pose pose(Player e, float partial, float time) {
        State s = STATES.computeIfAbsent(e.getId(), id -> new State());
        int action = s.action;
        float t = clock(s, partial);
        float rage = rageTime(e, partial);
        if (rage >= 0) { action = ULTIMATE; t = rage; }
        double vy = e.getY() - e.yo;
        s.fallSpeed = vy < 0 ? (float) -vy : 0;
        var in = new HulkMotion.Input(action, t, s.hulk(), s.charge, time, e.onGround(), s.fallSpeed,
                e.getViewXRot(partial) * (float) Math.PI / 180, smashTick(e));
        HulkMotion.Pose target = HulkMotion.sample(in);
        if (s.shown == null || time - s.shownTime > 10 || time < s.shownTime || FilmDirector.drawingStage()) s.shown = target.copy();
        else {
            float dt = Math.max(0, time - s.shownTime);
            float k = 1 - (float) Math.exp(-dt * (action == IDLE ? .35f : .95f));
            float size = target.size;
            s.shown.toward(target, k);
            // The body's size follows the transformation exactly (it is already smooth).
            if (action == TRANSFORM || action == REVERT) s.shown.size = size;
        }
        s.shownTime = time;
        return s.shown.copy();
    }

    // ------------------------------------------------------------------ the space bar
    @SubscribeEvent public static void keys(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { STATES.clear(); return; }
        long now = mc.level.getGameTime();
        STATES.entrySet().removeIf(v -> mc.level.getEntity(v.getKey()) == null || now - v.getValue().received > 600);
        var player = mc.player;
        boolean down = player != null && mc.screen == null && isHero(player) && mc.options.keyJump.isDown();
        State s = get(player);
        boolean hulk = s != null && s.hulk();
        if (down && !spaceDown && leapReady(player, s)) { ModNetworking.CHANNEL.sendToServer(new HulkInputPacket(true)); spaceDown = true; spaceTicks = 0; }
        else if (down && spaceDown) spaceTicks++;
        else if (!down && spaceDown) {
            ModNetworking.CHANNEL.sendToServer(new HulkInputPacket(false));
            spaceDown = false;
            // A quick tap is an ordinary jump.
            if (spaceTicks < LEAP_TAP) tapJump = true;
        }
        // In a leap he comes down hard and fast, like the weight he is.
        if (player != null && hulk && s.action == LEAP && !player.onGround() && !player.isInWater() && !player.getAbilities().flying) {
            var v = player.getDeltaMovement();
            if (v.y < .1) player.setDeltaMovement(v.x, Math.max(-3.4, v.y - .055), v.z);
        }
    }
    /** Can a press of space start the leap (Hulk, on the ground, not swimming, the leap not cooling down)? */
    private static boolean leapReady(Player player, State s) {
        return player != null && s != null && s.hulk() && player.onGround() && !player.isInWater() && !player.onClimbable() && s.cooldowns[5] <= 0;
    }
    /** As Hulk a held space bar is the leap; a quick tap is still an ordinary jump (in water it still swims). */
    @SubscribeEvent public static void noHop(MovementInputUpdateEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player)) return;
        State s = get(mc.player);
        if (s == null || !s.hulk() || mc.player.isInWater() || mc.player.onClimbable()) { tapJump = false; return; }
        if (tapJump) { tapJump = false; e.getInput().jumping = mc.player.onGround(); return; }
        // Space is held for the leap only while the press could start one; otherwise it is the plain jump.
        if (spaceDown) e.getInput().jumping = false;
        // Charging anything plants him: barely moving while he winds up.
        if (s.action == LEAP_CHARGE && s.age >= LEAP_TAP || s.action == PUNCH_CHARGE) { e.getInput().forwardImpulse *= .3f; e.getInput().leftImpulse *= .3f; }
    }
    /** As Hulk the mouse buttons are his fists and his guard: no vanilla mining, hitting or item use alongside. */
    @SubscribeEvent public static void fists(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player)) return;
        State s = get(mc.player);
        if (s == null || !s.hulk() || !(e.isAttack() || e.isUseItem())) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { STATES.clear(); spaceDown = false; }

    // ------------------------------------------------------------------ HUD
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !isHero(mc.player) || !HulkConfig.get(HulkConfig.HUD) || FilmDirector.playing()) return;
        State s = get(mc.player);
        if (s == null) return;
        var g = e.getGuiGraphics();
        var font = mc.font;
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        boolean hulk = s.hulk();
        int green = 0xFF6CE04A, cx = w / 2, y = h - 62;
        // Form, and the change key.
        String form = hulk ? "HULK" : "BRUCE BANNER";
        HudStyle.caption(g, font, form, 10, h - 46, hulk ? green : HudStyle.TEXT, -1);
        hint(g, font, AbilityKeyHandler.KEY_SKILL_G, hulk ? "Banner" : "Hulk", s.cooldowns[6], 10, h - 34);
        if (!hulk) return;
        // Moves and their cooldowns, down the left side.
        int row = h - 118;
        hint(g, font, AbilityKeyHandler.KEY_RAPID_FIRE, "Thunderclap", s.cooldowns[0], 10, row);
        hint(g, font, AbilityKeyHandler.KEY_SKILL_F, "Yer Yumruğu", s.cooldowns[1], 10, row + 12);
        hint(g, font, AbilityKeyHandler.KEY_RICOCHET, "Kaya", s.cooldowns[2], 10, row + 24);
        hint(g, font, AbilityKeyHandler.KEY_XRAY, "Gama Öfkesi", s.cooldowns[3], 10, row + 36);
        hint(g, font, mc.options.keyJump, "Sıçrama", s.cooldowns[5], 10, row + 48);
        hint(g, font, mc.options.keyAttack, "Yıkıcı Yumruk (basılı)", s.cooldowns[4], 10, row + 60);
        // Guard stamina: shown while it is used or recovering.
        if (s.stamina < STAMINA_MAX - .5f || s.action == GUARD) {
            boolean broken = (s.flags & FLAG_GUARD_BROKEN) != 0;
            HudStyle.caption(g, font, broken ? "Gard kırıldı" : "Gard", cx - 50, y - 12, broken ? 0xFFFF6A50 : HudStyle.MUTED, -1);
            HudStyle.bar(g, cx - 50, y, 100, s.stamina / STAMINA_MAX, broken ? 0xFFFF6A50 : green);
        }
        // Charge bar for whatever is being charged.
        if (s.action == PUNCH_CHARGE && s.age >= 1 || s.action == LEAP_CHARGE && s.age >= LEAP_TAP) {
            String what = s.action == LEAP_CHARGE ? "Sıçrama" : "Yıkıcı Yumruk";
            HudStyle.caption(g, font, what, cx, h / 2 + 22, HudStyle.TEXT, 0);
            HudStyle.bar(g, cx - 40, h / 2 + 34, 80, s.charge, s.charge >= .99f ? 0xFFB6FF7A : green);
        }
    }
    private static void hint(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, KeyMapping key, String what, int cooldown, int x, int y) {
        String k = key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        int width = HudStyle.hint(g, font, k, what, x, y);
        if (cooldown > 0) HudStyle.caption(g, font, String.format(Locale.ROOT, "%.1f", cooldown / 20f), x + width + 6, y + 1, 0xFFFF9A5A, -1);
    }
}
