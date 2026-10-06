package com.FIRNI.superheromod.client.render.iceman;

import com.FIRNI.superheromod.network.packet.IcemanFxPacket;

/** STUB: to be written. */
public final class IcemanShellFx {
    private IcemanShellFx() {}
    public static void receive(IcemanFxPacket p) {}
    /** Sets the shell on his body for this draw (IcemanBody.SHELL_*), from his state; called by the layer before drawing him. */
    public static void body(int entity, IcemanClient.State s, int action, float t, float now) {
        IcemanBody.SHELL_COVER = 0; IcemanBody.SHELL_CRACK = 0; IcemanBody.SHELL_FLASH = 0; IcemanBody.SHELL_GLOW = 0;
    }
}
