package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Istemcide o an cizilen sok dalgalari. */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID, value = Dist.CLIENT)
public final class ClientShockwaveData {

    public static final class Wave {
        public final Vec3 center;
        public final float maxRadius;
        public final int maxTicks;
        public int ticks;

        Wave(Vec3 center, float maxRadius, int maxTicks) {
            this.center = center;
            this.maxRadius = maxRadius;
            this.maxTicks = maxTicks;
            this.ticks = 0;
        }

        /** 0..1 — dalganin ilerlemesi. */
        public float progress(float partialTick) {
            return Math.min(1f, (ticks + partialTick) / maxTicks);
        }
    }

    private static final List<Wave> waves =
            Collections.synchronizedList(new ArrayList<>());

    /** Ayni anda cizilecek azami dalga — performans siniri. */
    private static final int MAX_WAVES = 8;

    private ClientShockwaveData() {}

    public static void add(Vec3 center, float maxRadius, int durationTicks) {
        synchronized (waves) {
            if (waves.size() >= MAX_WAVES) waves.remove(0);
            waves.add(new Wave(center, maxRadius, Math.max(1, durationTicks)));
        }
    }

    public static List<Wave> get() {
        return waves;
    }

    public static void clear() {
        synchronized (waves) {
            waves.clear();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        synchronized (waves) {
            Iterator<Wave> it = waves.iterator();
            while (it.hasNext()) {
                Wave wave = it.next();
                if (++wave.ticks >= wave.maxTicks) it.remove();
            }
        }
    }
}
