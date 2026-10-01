package com.FIRNI.superheromod.core.cinematic;

/** Server-owned rehearsal transport. Position is in ticks, independent of playback speed. */
public final class CinematicPlaybackClock {
    private final int duration;
    private float position, speed = 1;
    private boolean paused;
    private int revision;

    public CinematicPlaybackClock(int duration) {
        if (duration <= 0) throw new IllegalArgumentException("Positive duration required");
        this.duration = duration;
    }

    public void advance() { position = Math.min(duration, position + rate()); }
    public void pause(boolean value) { paused = value; revision++; }
    public void speed(float value) {
        if (!Float.isFinite(value) || value < .1f || value > 2f)
            throw new IllegalArgumentException("Speed must be between 0.1 and 2");
        speed = value;
        revision++;
    }
    public void seek(float tick) {
        if (!Float.isFinite(tick)) throw new IllegalArgumentException("Finite tick required");
        position = Math.max(0, Math.min(duration, tick));
        paused = true;
        revision++;
    }
    public void step(float ticks) { seek(position + ticks); }
    public float position() { return position; }
    public float rate() { return paused ? 0 : speed; }
    public int revision() { return revision; }
    public boolean finished() { return !paused && position >= duration; }
}
