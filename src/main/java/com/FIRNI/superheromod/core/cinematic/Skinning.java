package com.FIRNI.superheromod.core.cinematic;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Two-influence linear skinning. Matrices are animated global * inverse bind. */
public final class Skinning {
    private Skinning() {}

    public static float weight(float position, float start, float end) {
        float t = Math.max(0, Math.min(1, (position - start) / (end - start)));
        return t * t * (3 - 2 * t);
    }

    public static void position(Matrix4f first, Matrix4f second, float weight,
                                Vector3f bind, Vector3f result, Vector3f scratch) {
        first.transformPosition(bind, result);
        second.transformPosition(bind, scratch);
        result.lerp(scratch, weight);
    }
}
