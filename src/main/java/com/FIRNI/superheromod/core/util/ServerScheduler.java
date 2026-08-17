package com.FIRNI.superheromod.core.util;

import com.FIRNI.superheromod.SuperheroMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Genel amacli "N tick sonra sunu calistir" zamanlayicisi. Sunucu ana thread'inde
 * calisir (dunya/oyuncu erisimi guvenli). Queue/match-found/harita-secim gibi
 * zamanlamali her akis bunu kullanabilir.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class ServerScheduler {

    private static final CopyOnWriteArrayList<Task> TASKS = new CopyOnWriteArrayList<>();

    private ServerScheduler() {
    }

    public static void schedule(int delayTicks, Runnable action) {
        TASKS.add(new Task(delayTicks, action));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (TASKS.isEmpty()) return;

        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            task.ticksRemaining--;
            if (task.ticksRemaining <= 0) {
                TASKS.remove(task);
                task.action.run();
            }
        }
    }

    private static final class Task {
        int ticksRemaining;
        final Runnable action;

        Task(int ticksRemaining, Runnable action) {
            this.ticksRemaining = ticksRemaining;
            this.action = action;
        }
    }
}
