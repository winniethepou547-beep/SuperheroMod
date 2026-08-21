package com.FIRNI.superheromod.client.render;

/**
 * Yerel oyuncunun Sand Grasp onizlemesi acik mi.
 *
 * Sadece tus yonlendirmesi icin: onizleme acikken sol tik onaylar,
 * sag tik iptal eder.
 */
public final class ClientSandGraspData {

    private static volatile boolean preview = false;

    private ClientSandGraspData() {}

    public static void set(boolean active) {
        preview = active;
    }

    public static boolean hasPreview() {
        return preview;
    }

    public static void clear() {
        preview = false;
    }
}
