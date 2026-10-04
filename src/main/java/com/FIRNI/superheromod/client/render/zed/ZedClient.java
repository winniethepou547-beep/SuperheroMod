package com.FIRNI.superheromod.client.render.zed;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.client.ClientHeroRegistry;
import com.FIRNI.superheromod.client.hud.HudStyle;
import com.FIRNI.superheromod.client.input.AbilityKeyHandler;
import com.FIRNI.superheromod.client.render.film.FilmDirector;
import com.FIRNI.superheromod.core.ability.AbilitySlot;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.AbilityInputPacket;
import com.FIRNI.superheromod.network.packet.ZedStatePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static com.FIRNI.superheromod.heroes.zed.ZedAction.*;

/**
 * Zed on each client: every Zed's synced state (his action and its clock, his two shadows, the
 * mark), the E key (Shadow Slash, not the inventory, while he is Zed), the view's kick when he
 * swaps or dashes, and the HUD.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ZedClient {
    public static final class State {
        public int action, age, side;
        public int[] cooldowns = new int[ZedStatePacket.COOLDOWNS];
        public boolean wAlive, rAlive;
        public Vec3 wPos = Vec3.ZERO, wFrom = Vec3.ZERO, rPos = Vec3.ZERO;
        public float wYaw, rYaw;
        public int wAction, wAge, wLeft, rLeft;
        public long wBorn;
        public int markTarget = -1, markLeft;
        /** When a cut of his last landed (level time): his body holds still for an instant there. */
        float stopAt = -100;
        public float markStored;
        long received;
    }
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static boolean eDown;
    static float fovKick;

    private ZedClient() {}

    public static boolean isHero(Entity e) { return e instanceof Player && ID.equals(ClientHeroRegistry.get(e.getUUID())); }
    public static State get(Entity e) { return e == null ? null : STATES.get(e.getId()); }
    public static Iterable<Map.Entry<Integer, State>> all() { return STATES.entrySet(); }

    public static void receive(ZedStatePacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        State s = STATES.computeIfAbsent(p.entity(), id -> new State());
        long now = mc.level.getGameTime();
        // A new shadow runs out from where he stood when it was sent.
        if (p.wAlive() && !s.wAlive) {
            var owner = mc.level.getEntity(p.entity());
            s.wFrom = owner != null ? owner.position() : p.wPos();
            s.wBorn = now;
        }
        // The view kicks for the local Zed when he blinks somewhere.
        if (mc.player != null && p.entity() == mc.player.getId() && p.action() != s.action
                && (p.action() == SWAP || p.action() == MARK_RETURN || p.action() == MARK_DASH || p.action() == MARK_STRIKE)) fovKick = 1;
        if (p.action() != s.action) s.stopAt = -100;
        s.action = p.action(); s.age = p.age(); s.side = p.flags(); s.cooldowns = p.cooldowns();
        s.wAlive = p.wAlive(); s.wPos = p.wPos(); s.wYaw = p.wYaw(); s.wAction = p.wAction(); s.wAge = p.wAge(); s.wLeft = p.wLeft();
        s.rAlive = p.rAlive(); s.rPos = p.rPos(); s.rYaw = p.rYaw(); s.rLeft = p.rLeft();
        s.markTarget = p.markTarget(); s.markLeft = p.markLeft(); s.markStored = p.markStored();
        s.received = now;
    }
    public static float clock(State s, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) return s.age;
        float raw = s.age + Math.min(3, Math.max(0, level.getGameTime() - s.received)) + partial;
        // Hit-stop: the cut holds for a moment where it bit.
        if (s.stopAt > 0) raw -= Math.min(HIT_STOP, Math.max(0, level.getGameTime() + partial - s.stopAt));
        return raw;
    }
    private static final float HIT_STOP = 1.4f;
    /** A cut landed here: every Zed cutting next to it stops for an instant; the local one feels it. */
    public static void cutLanded(net.minecraft.world.phys.Vec3 at, boolean heavy) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (var entry : STATES.entrySet()) {
            State s = entry.getValue();
            if (s.action != SLASH_RIGHT && s.action != SLASH_LEFT && s.action != SLASH_FINISH) continue;
            var body = mc.level.getEntity(entry.getKey());
            if (body == null || body.position().distanceTo(at) > 5) continue;
            s.stopAt = mc.level.getGameTime() + mc.getFrameTime();
            if (body == mc.player) com.FIRNI.superheromod.client.render.ClientScreenShake.add(heavy ? .1f : .045f);
        }
    }
    public static float shadowClock(State s, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) return s.wAge;
        return s.wAge + Math.min(3, Math.max(0, level.getGameTime() - s.received)) + partial;
    }
    /** Is anyone's Death Mark on this entity right now? Returns the Zed's state or null. */
    public static State markOn(int entity) {
        for (State s : STATES.values()) if (s.markTarget == entity) return s;
        return null;
    }

    // ------------------------------------------------------------------ input
    /** E is his Shadow Slash: while he is Zed it does not open the inventory. */
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
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { STATES.clear(); return; }
        long now = mc.level.getGameTime();
        STATES.entrySet().removeIf(v -> mc.level.getEntity(v.getKey()) == null || now - v.getValue().received > 200);
        fovKick *= .7f;
    }
    /** His left click is his slashes: no vanilla swing or mining alongside. */
    @SubscribeEvent public static void blades(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player) || !e.isAttack()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }
    /** Gone into shadow for the Death Mark: he cannot move until he steps out behind the target. */
    @SubscribeEvent public static void held(MovementInputUpdateEvent e) {
        State s = get(e.getEntity());
        if (s == null || s.action != MARK_DASH && s.action != MARK_HIDDEN) return;
        var input = e.getInput();
        input.forwardImpulse = 0; input.leftImpulse = 0; input.jumping = false; input.shiftKeyDown = false;
        input.up = input.down = input.left = input.right = false;
    }
    @SubscribeEvent public static void fov(ComputeFovModifierEvent e) {
        if (FilmDirector.playing() || !isHero(e.getPlayer())) return;
        // His sprint is much faster than a player's: keep the view from stretching with it.
        float base = e.getPlayer().isSprinting() ? 1.15f : 1f;
        e.setNewFovModifier(base * (1 + .12f * fovKick));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { STATES.clear(); eDown = false; }

    // ------------------------------------------------------------------ HUD
    @SubscribeEvent public static void hud(RenderGuiEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !isHero(mc.player) || FilmDirector.playing()) return;
        State s = get(mc.player);
        if (s == null) return;
        var g = e.getGuiGraphics();
        var font = mc.font;
        int h = e.getWindow().getGuiScaledHeight(), w = e.getWindow().getGuiScaledWidth();
        int red = 0xFFE0303A;
        HudStyle.caption(g, font, "ZED", 10, h - 46, red, -1);
        int row = h - 106;
        hint(g, font, AbilityKeyHandler.KEY_ULTIMATE, "Shuriken", s.cooldowns[CD_Q], 10, row);
        hint(g, font, AbilityKeyHandler.KEY_SKILL_F, s.wAlive ? "Gölgeyle Yer Değiştir" : "Canlı Gölge", s.wAlive ? 0 : s.cooldowns[CD_W], 10, row + 12);
        hint(g, font, mc.options.keyInventory, "Gölge Darbesi", s.cooldowns[CD_E], 10, row + 24);
        hint(g, font, AbilityKeyHandler.KEY_RAPID_FIRE, s.rAlive ? "R Gölgesine Dön" : "Ölüm İşareti", s.rAlive ? 0 : s.cooldowns[CD_R], 10, row + 36);
        hint(g, font, AbilityKeyHandler.KEY_XRAY, "Gölge İnfazı", s.cooldowns[CD_X], 10, row + 48);
        // The shadows' time left, and the mark's.
        int y = h / 2 + 26;
        if (s.wAlive) { HudStyle.caption(g, font, "Gölge", w / 2 - 60, y, HudStyle.MUTED, -1); HudStyle.bar(g, w / 2 - 60, y + 10, 50, s.wLeft / (float) SHADOW_LIFE, 0xFF7A3AD0); }
        if (s.rAlive) { HudStyle.caption(g, font, "R Gölgesi", w / 2 + 10, y, HudStyle.MUTED, -1); HudStyle.bar(g, w / 2 + 10, y + 10, 50, s.rLeft / (float) R_SHADOW_LIFE, red); }
        if (s.markTarget >= 0) {
            HudStyle.caption(g, font, String.format(Locale.ROOT, "İşaret %.1f", s.markLeft / 20f), w / 2, y - 14, red, 0);
        }
    }
    private static void hint(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, KeyMapping key, String what, int cooldown, int x, int y) {
        hint(g, font, key, what, cooldown, false, x, y);
    }
    /** One row of the skill list in his colour (HudStyle.skill); active = running right now. */
    private static void hint(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, KeyMapping key, String what, int cooldown, boolean active, int x, int y) {
        String k = key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        HudStyle.skill(g, font, k, what, cooldown, active, x, y, 0xFFE0343A);
    }
}
