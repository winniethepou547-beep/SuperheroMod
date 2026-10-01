package com.FIRNI.superheromod;

import com.FIRNI.superheromod.core.cinematic.CinematicReadiness;
import java.util.*;

final class CinematicReadinessCheck {
    static void run() {
        UUID session = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        var barrier = new CinematicReadiness(session, Set.of(a, b));
        barrier.reply(UUID.randomUUID(), a, true);
        barrier.reply(session, UUID.randomUUID(), true);
        barrier.reply(session, a, true);
        barrier.reply(session, a, true);
        if (barrier.ready()) throw new AssertionError("Single or unrelated client released barrier");
        barrier.reply(session, b, true);
        if (!barrier.ready()) throw new AssertionError("Both clients did not release barrier");
        var npc = new CinematicReadiness(session, Set.of(a));
        npc.reply(session, a, true);
        if (!npc.ready()) throw new AssertionError("NPC requires nonexistent client");
        var timeout = new CinematicReadiness(session, Set.of(a));
        for (int i = 0; i < 199; i++) timeout.waitTick();
        if (timeout.expired()) throw new AssertionError("Early timeout");
        timeout.waitTick();
        if (!timeout.expired()) throw new AssertionError("Unbounded preparation wait");
        var failed = new CinematicReadiness(session, Set.of(a, b));
        failed.reply(session, a, false); failed.reply(session, b, true);
        if (!failed.expired() || failed.ready()) throw new AssertionError("Failed load started scene");
        System.out.println("Readiness: two clients, NPC, stale/duplicate/unrelated replies, timeout and failure OK");
    }
}
