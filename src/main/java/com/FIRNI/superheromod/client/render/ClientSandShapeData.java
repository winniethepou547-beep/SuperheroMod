package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;

import java.util.Collections;
import java.util.List;

/** Istemcide o an cizilecek somut kum sekilleri. Sunucu her tick yeniliyor. */
public final class ClientSandShapeData {

    private static volatile List<SandShapeSyncPacket.Shape> shapes = Collections.emptyList();
    /** Son paketin geldigi an — sunucu susarsa sekiller ekranda asili kalmasin. */
    private static volatile long lastUpdate = 0L;

    private ClientSandShapeData() {}

    public static void set(List<SandShapeSyncPacket.Shape> list) {
        shapes = list;
        lastUpdate = System.currentTimeMillis();
    }

    public static List<SandShapeSyncPacket.Shape> get() {
        if (System.currentTimeMillis() - lastUpdate > 800L) return Collections.emptyList();
        return shapes;
    }

    public static void clear() {
        shapes = Collections.emptyList();
    }
}
