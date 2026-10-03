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
        float stopAt = -100, stopFor;
        /** Hits soaking into the suit: {level time, side, where on the body they start (flow order)}. */
        public final List<float[]> pulses = new ArrayList<>();
        /** For the flip: the local player's yaw when it began. */
        float flipYaw = Float.NaN;
    }
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static boolean eDown;
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
            if (local) started(mc.player, s, p.action());
        }
        s.action = p.action(); s.age = p.age(); s.flags = p.flags(); s.cooldowns = p.cooldowns();
        s.energy = p.energy(); s.released = p.released(); s.reflex = p.reflex(); s.reflexLeft = p.reflexLeft();
        s.hurtAge = p.hurtAge(); s.hurtPower = p.hurtPower(); s.hurtYaw = p.hurtYaw(); s.threatYaw = p.threatYaw(); s.quiet = p.quiet();
        s.target = p.target(); s.from = p.from(); s.dir = p.dir(); s.apex = p.apex(); s.to = p.to(); s.land = p.land();
        s.reach = p.reach(); s.speed = p.speed(); s.height = p.height();
        s.received = now;
    }
    /** The local Panther starts a move: the view's kick, and what the flip needs to remember. */
    private static void started(LocalPlayer player, State s, int action) {
        if (action == POUNCE) fovKick = 1;
        if (action == SPIN) fovKick = Math.max(fovKick, .5f);
        if (action == POUNCE_FLIP) s.flipYaw = player.getYRot();
    }
    public static float clock(State s, float partial) {
        var level = Minecraft.getInstance().level;
        if (level == null) return s.age;
        float raw = s.age + Math.min(3, Math.max(0, level.getGameTime() - s.received)) + partial;
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
        if (mc.player == null || !isHero(mc.player)) { eDown = false; return; }
        if (mc.screen != null) return;
        boolean clicked = false;
        while (mc.options.keyInventory.consumeClick()) clicked = true;
        boolean down = mc.options.keyInventory.isDown() || clicked;
        if (down && !eDown) ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(AbilitySlot.SKILL_V, true));
        else if (!down && eDown) ModNetworking.CHANNEL.sendToServer(new AbilityInputPacket(AbilitySlot.SKILL_V, false));
        eDown = down;
    }
    /** His left click is his claws: no vanilla swing or mining alongside. */
    @SubscribeEvent public static void claws(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !isHero(mc.player) || !e.isAttack()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }
    /** Rooted while the energy gathers, and while a move carries him. */
    @SubscribeEvent public static void held(MovementInputUpdateEvent e) {
        State s = get(e.getEntity());
        if (s == null) return;
        int a = s.action;
        if (a != POUNCE_LOAD && a != POUNCE && a != POUNCE_FLIP && a != POUNCE_KICK && a != SPIN_LOAD && a != SPIN && a != RELEASE_CHARGE && a != RELEASE
                && !(a == POUNCE_LAND && s.age < LAND_TICKS * .6f)) return;
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
        float t = clock(s, 0) + 1;     // where he must be at the start of the next tick
        switch (s.action) {
            case POUNCE_LOAD, SPIN_LOAD, RELEASE_CHARGE, RELEASE -> player.setDeltaMovement(player.getDeltaMovement().multiply(0, 1, 0));
            case POUNCE -> steer(player, PantherPath.pounce(s.from, s.dir, s.speed, s.reach, t));
            case POUNCE_FLIP -> {
                steer(player, PantherPath.flip(s.from, s.apex, s.to, Math.min(1, t / FLIP_TICKS)));
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
                if (s.age <= 1 && t < 2.5f) player.setDeltaMovement(s.dir.x * s.speed * .45, .34, s.dir.z * s.speed * .45);
            }
            case SPIN -> steer(player, PantherPath.spin(s.from, s.dir, s.reach, s.height, Math.min(t, SPIN_TICKS), SPIN_TICKS));
            case FRENZY -> {
                // The feet keep the range: in when the target drifts away, back a touch when it crowds him.
                Entity target = mc.level.getEntity(s.target);
                if (target != null) {
                    Vec3 to = target.position().subtract(player.position());
                    Vec3 flat = new Vec3(to.x, 0, to.z);
                    double d = flat.length();
                    if (d > 2.4 && d < 6) player.setDeltaMovement(player.getDeltaMovement().add(flat.normalize().scale(.12)));
                    else if (d < 1.3 && d > .01) player.setDeltaMovement(player.getDeltaMovement().add(flat.normalize().scale(-.06)));
                }
            }
            default -> {}
        }
        if (s.action == POUNCE || s.action == POUNCE_FLIP || s.action == POUNCE_KICK || s.action == POUNCE_LAND || s.action == SPIN || s.action == POUNCE_MISS)
            player.fallDistance = 0;
        if (s.action != POUNCE_FLIP && s.action != POUNCE_KICK && s.action != POUNCE_LAND) s.flipYaw = Float.NaN;
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
                && (s.action == POUNCE_FLIP || s.action == POUNCE_KICK || s.action == POUNCE_LAND && s.age < LAND_TICKS - 1);
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
        STATES.clear(); eDown = false;
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
        int row = h - 106;
        hint(g, font, mc.options.keyShift, "Panter Atılışı", s.cooldowns[CD_POUNCE], 10, row);
        hint(g, font, AbilityKeyHandler.KEY_ULTIMATE, "Dönen Üçlü Tekme", s.cooldowns[CD_SPIN], 10, row + 12);
        hint(g, font, mc.options.keyInventory, "Vibranyum Patlaması", s.cooldowns[CD_RELEASE], 10, row + 24);
        hint(g, font, AbilityKeyHandler.KEY_RAPID_FIRE, s.reflexLeft > 0 ? "Refleks açık" : "Panter Refleksi", s.reflexLeft > 0 ? 0 : s.cooldowns[CD_REFLEX], 10, row + 36);
        // The stored kinetic energy: a bar that brightens as it fills.
        int y = h - 58, bw = 92;
        float energy = s.energy;
        HudStyle.caption(g, font, "Vibranyum", 10, y - 10, energy > .05f ? violet : HudStyle.MUTED, -1);
        float pulse = energy >= .999f ? .75f + .25f * Mth.sin((mc.level.getGameTime() + e.getPartialTick()) * .4f) : 1;
        HudStyle.bar(g, 64, y - 8, bw, energy, HudStyle.alpha(violet, Math.max(.45f, energy) * pulse));
        if (s.reflexLeft > 0) {
            HudStyle.caption(g, font, "Refleks", w / 2 - 25, h / 2 + 26, violet, -1);
            HudStyle.bar(g, w / 2 - 25, h / 2 + 36, 50, s.reflex, violet);
        }
    }
    private static void hint(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.gui.Font font, KeyMapping key, String what, int cooldown, int x, int y) {
        String k = key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        int width = HudStyle.hint(g, font, k, what, x, y);
        if (cooldown > 0) HudStyle.caption(g, font, String.format(Locale.ROOT, "%.1f", cooldown / 20f), x + width + 6, y + 1, 0xFFFF9A5A, -1);
    }
}
