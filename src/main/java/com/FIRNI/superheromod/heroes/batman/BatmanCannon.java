package com.FIRNI.superheromod.heroes.batman;

import net.minecraft.server.level.ServerPlayer;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * The WayneTech dual wrist cannon on the server (gadget G_CANNON, action CANNON): both gauntlets open, four seconds of
 * aimed rapid fire from the wrists that follows his crosshair, then they cool and close. Effects go out as
 * BatmanFxPacket kinds FX_CANNON_FIRST..FX_CANNON_LAST (drawn by BatmanCannonFx).
 */
public final class BatmanCannon {
    private BatmanCannon() {}

    /** R with the cannon picked: starts it (true) or says why not (false: no cooldown is spent). */
    static boolean use(ServerPlayer p, BatmanController.State s) {
        BatmanController.set(s, CANNON);
        return true;
    }
    /** Every tick of the CANNON action (s.age = ticks since it began). */
    static void tick(ServerPlayer p, BatmanController.State s) {}
}
