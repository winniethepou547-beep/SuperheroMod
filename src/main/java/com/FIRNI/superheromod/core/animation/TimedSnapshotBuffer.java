package com.FIRNI.superheromod.core.animation;

import java.util.ArrayDeque;

/** Bounded interpolation history. Caller supplies a monotonic clock and playback delay. */
public final class TimedSnapshotBuffer<T> {
    private record Snapshot<T>(long time,T value) {}
    public record Blend<T>(T from,T to,float fraction) {}
    private final ArrayDeque<Snapshot<T>> history=new ArrayDeque<>();
    private final int capacity;
    public TimedSnapshotBuffer(int capacity) {
        if(capacity<2)throw new IllegalArgumentException("Need two snapshots");
        this.capacity=capacity;
    }
    public void add(long time,T value) {
        if(!history.isEmpty() && time<=history.getLast().time) {
            if(time<history.getLast().time)throw new IllegalArgumentException("Clock moved backwards");
            history.removeLast();
        }
        history.addLast(new Snapshot<>(time,value));
        while(history.size()>capacity)history.removeFirst();
    }
    public Blend<T> sample(long time) {
        if(history.isEmpty())return null;
        Snapshot<T> before=history.getFirst();
        if(time<=before.time)return new Blend<>(before.value,before.value,0);
        for(Snapshot<T> after:history) {
            if(after.time>=time) {
                float fraction=(float)((double)(time-before.time)/(after.time-before.time));
                return new Blend<>(before.value,after.value,fraction);
            }
            before=after;
        }
        return new Blend<>(before.value,before.value,0);
    }
    public void clear() { history.clear(); }
    public int size() { return history.size(); }
}
