package com.FIRNI.superheromod.heroes.batman;

import net.minecraft.server.level.ServerPlayer;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * The electric gauntlets on the server (gadget G_SHOCK; actions SHOCK_EQUIP, SHOCK_UNEQUIP, SHOCK_PUNCH): R puts them on
 * (they lock on, charge, the fists clap together) or takes them off; worn, left click is a heavy electric boxing combo
 * instead of the rapid punches; a blow that lands drains a big share of the charge (State.energy), idling drains it
 * slowly, empty they recharge after a pause. State.shock / State.energy are synced to every client.
 */
public final class BatmanShock {
    private BatmanShock() {}

    /** R with the gauntlets picked: on or off (true = done, the cooldown is spent). */
    static boolean toggle(ServerPlayer p, BatmanController.State s) {
        if (s.shock) { s.shock = false; BatmanController.set(s, SHOCK_UNEQUIP); }
        else { s.shock = true; BatmanController.set(s, SHOCK_EQUIP); }
        return true;
    }
    /** Left click while they are worn. */
    static void click(ServerPlayer p, BatmanController.State s) {}
    /** Every tick of SHOCK_EQUIP, SHOCK_UNEQUIP and SHOCK_PUNCH. */
    static void tick(ServerPlayer p, BatmanController.State s) {}
}
