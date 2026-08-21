package com.FIRNI.superheromod.client.render.colossus;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.packet.ColossusActionPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Devin o an oynattigi hareketler — istemci tarafi sayac. */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ClientColossusActions {

    public static final class Action {
        public final byte type;
        public final int duration;
        public int ticks;

        Action(byte type, int duration) {
            this.type = type;
            this.duration = Math.max(1, duration);
            this.ticks = 0;
        }

        /** 0..1 — hareketin ilerlemesi (kare arasi dahil). */
        public float progress(float partialTick) {
            return Math.min(1f, (ticks + partialTick) / duration);
        }
    }

    private static final Map<UUID, Action> actions = new ConcurrentHashMap<>();

    private ClientColossusActions() {}

    public static void start(UUID playerId, byte type, int duration) {
        actions.put(playerId, new Action(type, duration));
    }

    public static Action of(UUID playerId) {
        return actions.get(playerId);
    }

    public static boolean isMaceInRight(UUID playerId, int rightShoulderState) {
        // Sag omuz kristali kirildiysa dev topuzu sol ele aliyor
        return rightShoulderState < 3;
    }

    public static void clear() {
        actions.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Iterator<Map.Entry<UUID, Action>> it = actions.entrySet().iterator();
        while (it.hasNext()) {
            Action action = it.next().getValue();
            if (++action.ticks >= action.duration) it.remove();
        }
    }

    /** Kolay okunurluk icin. */
    public static boolean isMaceSwing(Action action) {
        return action != null && action.type == ColossusActionPacket.MACE_SWING;
    }

    public static boolean isRockThrow(Action action) {
        return action != null && action.type == ColossusActionPacket.ROCK_THROW;
    }
}
