package com.FIRNI.superheromod.core.cinematic;

import java.util.*;

/** One session's bounded participant barrier; duplicate and unrelated replies cannot release it. */
public final class CinematicReadiness {
    private final UUID session;
    private final Set<UUID> pending;
    private boolean failed;
    private int waited;
    public CinematicReadiness(UUID session, Set<UUID> participants) {
        this.session = session;
        pending = new HashSet<>(participants);
    }
    public void reply(UUID session, UUID participant, boolean success) {
        if (!this.session.equals(session) || !pending.remove(participant)) return;
        if (!success) failed = true;
    }
    public boolean ready() { return !failed && pending.isEmpty(); }
    public boolean expired() { return failed || waited >= 200; }
    public void waitTick() { if (!ready()) waited++; }
}
