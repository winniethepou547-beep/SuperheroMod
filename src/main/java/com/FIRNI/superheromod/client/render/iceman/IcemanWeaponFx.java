package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.network.packet.IcemanFxPacket;

import static com.FIRNI.superheromod.heroes.iceman.IcemanAction.*;

/** STUB: to be written. */
public final class IcemanWeaponFx {
    private IcemanWeaponFx() {}
    public static void receive(IcemanFxPacket p) {}
    /** Sets what his right hand holds for this draw (IcemanBody.WEAPON*), from his state; called by the layer before drawing him. */
    public static void hold(int entity, IcemanClient.State s, int action, float t, float now) {
        boolean forming = action == FORM;
        boolean out = s.weaponOut() || forming;
        IcemanBody.WEAPON = out ? s.weapon : -1;
        IcemanBody.WEAPON_FORM = forming ? Math.min(1, t / FORM_TICKS) : 1;
        IcemanBody.WEAPON_SIZE = s.weapon == W_MACE && (action == CHARGE || action == RELEASE) ? 1 + (MACE_MAX - 1) * s.charge : 1;
        IcemanBody.WEAPON_CRACK = 0;
    }
}
