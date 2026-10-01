package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.phys.Vec3;

/** A seekable visual object. No particles, entities, or terrain state are stored. */
public record CinematicSetPiece(Vec3 center, float radius, int formStart, int sealed,
                                int burst, int end) {
    public CinematicSetPiece {
        if(center==null || !Double.isFinite(center.lengthSqr()) || !Float.isFinite(radius)
                || radius<=0 || formStart<0 || sealed<=formStart || burst<=sealed || end<=burst)
            throw new IllegalArgumentException("Invalid cinematic set piece");
    }
    public boolean active(float tick) { return tick>=formStart && tick<end; }
    public float formation(float tick) { return smooth((tick-formStart)/(sealed-formStart)); }
    public float spike(float tick,int index) {
        return smooth((tick-sealed-index%5*.65f)/6);
    }
    public float scatter(float tick) { return Math.max(0,Math.min(1,(tick-burst)/(end-burst))); }
    private static float smooth(float t) { t=Math.max(0,Math.min(1,t));return t*t*(3-2*t); }
}
