package com.FIRNI.superheromod.client.render.film;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.FilmSessionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Client side of the server-held film finishers: the synced clock per performer, and the film
 * each one plays for the two people in it.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class FilmSessionClient {
    public static final class State {
        public String film; public int attacker, target, age; public float yaw; public Vec3 anchor = Vec3.ZERO;
        long received; float shown;
    }
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final Map<String, IntFunction<Film>> FILMS = Map.of(
            MaximumPowerFilm.ID, MaximumPowerFilm::new,
            SandArmyFilm.ID, SandArmyFilm::new,
            GodOfThunderFilm.ID, GodOfThunderFilm::new);

    public static void receive(FilmSessionPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!p.active()) { STATES.remove(p.attacker()); return; }
        State s = STATES.computeIfAbsent(p.attacker(), id -> new State());
        boolean fresh = s.film == null;
        s.film = p.film(); s.attacker = p.attacker(); s.target = p.target(); s.age = p.age();
        s.received = mc.level.getGameTime(); s.yaw = p.yaw(); s.anchor = p.anchor();
        boolean watching = mc.player.getId() == p.attacker() || mc.player.getId() == p.target();
        var factory = FILMS.get(p.film());
        if (watching && factory != null && (fresh || !FilmDirector.playing())) FilmDirector.play(factory.apply(p.attacker()));
    }
    public static State get(int attacker) { return STATES.get(attacker); }
    /** Monotonic, frame-interpolated film time: never steps backwards when a packet is late. */
    public static float time(State s, float partial) {
        var level = Minecraft.getInstance().level;
        float predicted = s.age + (level == null ? 0 : Math.min(2, Math.max(0, level.getGameTime() - s.received))) + partial;
        s.shown = Math.min(predicted + 1.5f, Math.max(s.shown, predicted));
        return s.shown;
    }
    public static Vec3 forward(State s) { return Vec3.directionFromRotation(0, s.yaw); }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) { STATES.clear(); return; }
        STATES.entrySet().removeIf(v -> mc.level.getGameTime() - v.getValue().received > 10);
        State own = mc.player == null ? null : STATES.get(mc.player.getId());
        if (own != null) {
            mc.player.setYRot(own.yaw); mc.player.yRotO = own.yaw; mc.player.setXRot(0);
            mc.player.yBodyRot = own.yaw; mc.player.yHeadRot = own.yaw;
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { STATES.clear(); }
}
