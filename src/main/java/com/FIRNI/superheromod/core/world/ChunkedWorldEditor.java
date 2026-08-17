package com.FIRNI.superheromod.core.world;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Buyuk dunya duzenleme islerini (300x300 harita insasi, chunk on-yukleme vb.)
 * tek seferde donmaya sebep olmadan, tick basina sinirli sayida is calistirarak
 * arka planda tamamlar. run() ile eklenen isler siraya girer, her sunucu tick'inde
 * bir kismi islenir - buyuk bir /superhero setup komutu artik sunucuyu kilitlemez.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class ChunkedWorldEditor {

    private static final int JOBS_PER_TICK = 12;
    private static final Deque<Runnable> QUEUE = new ArrayDeque<>();

    private ChunkedWorldEditor() {
    }

    public static void run(List<Runnable> jobs) {
        QUEUE.addAll(jobs);
    }

    public static void run(List<Runnable> jobs, Runnable whenDone) {
        QUEUE.addAll(jobs);
        if (whenDone != null) {
            QUEUE.add(whenDone);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (QUEUE.isEmpty()) return;

        int n = JOBS_PER_TICK;
        while (n-- > 0 && !QUEUE.isEmpty()) {
            QUEUE.poll().run();
        }
    }
}
