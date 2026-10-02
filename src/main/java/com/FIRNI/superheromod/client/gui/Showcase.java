package com.FIRNI.superheromod.client.gui;

import net.minecraft.world.entity.Entity;

/**
 * A stand-in on the champion select screen playing one of its hero's actions: the hero layers ask
 * here first, and if this body is the one on show they animate that action at this time instead of
 * whatever the game says it is doing.
 */
public final class Showcase {
    private static int entity = -1, action;
    private static float time;

    private Showcase() {}

    public static void play(Entity e, int heroAction, float t) { entity = e.getId(); action = heroAction; time = t; }
    public static void stop() { entity = -1; }
    public static boolean is(Entity e) { return e != null && e.getId() == entity; }
    public static int action() { return action; }
    public static float time() { return time; }
}
