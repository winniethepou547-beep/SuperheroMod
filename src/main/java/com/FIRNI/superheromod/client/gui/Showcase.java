package com.FIRNI.superheromod.client.gui;

import net.minecraft.world.entity.Entity;

/**
 * A stand-in on the champion select screen playing one of its hero's actions: the hero layers ask
 * here first, and if this body is the one on show they animate that action at this time instead of
 * whatever the game says it is doing. A show can also light the eyes (and the power round the body)
 * and take the weapon out of the hand while it is in the air.
 */
public final class Showcase {
    private static int entity = -1, action;
    private static float time, eyes = -1, aura;
    private static boolean emptyHanded;

    private Showcase() {}

    public static void play(Entity e, int heroAction, float t) {
        entity = e.getId(); action = heroAction; time = t;
        eyes = -1; aura = 0; emptyHanded = false;
    }
    /** Eyes lit to this much (0..1) and the power round the body; -1 leaves the action's own. */
    public static void glow(float eyeLight, float power) { eyes = eyeLight; aura = power; }
    /** The weapon is away from the hand (thrown). */
    public static void emptyHanded(boolean away) { emptyHanded = away; }
    public static void stop() { entity = -1; }
    public static boolean is(Entity e) { return e != null && e.getId() == entity; }
    public static int action() { return action; }
    public static float time() { return time; }
    public static float eyes() { return eyes; }
    public static float aura() { return aura; }
    public static boolean emptyHanded() { return emptyHanded; }
}
