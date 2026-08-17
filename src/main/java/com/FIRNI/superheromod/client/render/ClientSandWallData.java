package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.network.packet.SandWallSyncPacket;

import java.util.Collections;
import java.util.List;

/** Istemcide o an cizilecek kum duvarlari. Sunucu her tick yeniliyor. */
public final class ClientSandWallData {

    private static volatile List<SandWallSyncPacket.Entry> walls = Collections.emptyList();
    /** Son paketin geldigi an — sunucu susarsa duvarlar ekranda kalmasin. */
    private static volatile long lastUpdate = 0L;

    private ClientSandWallData() {}

    public static void set(List<SandWallSyncPacket.Entry> list) {
        walls = list;
        lastUpdate = System.currentTimeMillis();
    }

    public static List<SandWallSyncPacket.Entry> get() {
        if (System.currentTimeMillis() - lastUpdate > 1500L) return Collections.emptyList();
        return walls;
    }

    /** Yerel oyuncunun onizlemesi acik mi — tus yonlendirmesi buna bakiyor. */
    public static boolean hasPreview() {
        for (SandWallSyncPacket.Entry e : get()) {
            if (e.preview()) return true;
        }
        return false;
    }

    public static void clear() {
        walls = Collections.emptyList();
    }
}
