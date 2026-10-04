package com.FIRNI.superheromod.heroes.batman;

import net.minecraft.server.level.ServerPlayer;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * The sonic trap on the server (gadget G_SONIC, action SONIC; Batman v Superman reference): he presses the red button
 * of a remote, two sonic emitters rise out of the ground either side of him and blast the target with sound (no damage:
 * a heavy slow, a shaking view, overwhelming sound) for a few seconds, unless they are broken first. Effects go out as
 * BatmanFxPacket kinds FX_SONIC_FIRST..FX_SONIC_LAST (drawn by BatmanSonicFx).
 */
public final class BatmanSonic {
    private BatmanSonic() {}

    /** R with the trap picked: starts it (true) or says why not (false: no cooldown is spent). */
    static boolean use(ServerPlayer p, BatmanController.State s) {
        BatmanController.set(s, SONIC);
        return true;
    }
    /** Every tick of the SONIC action (the remote in his hand; s.age = ticks since it began). */
    static void tick(ServerPlayer p, BatmanController.State s) {}
}
