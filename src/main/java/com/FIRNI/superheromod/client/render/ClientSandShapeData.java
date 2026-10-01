package com.FIRNI.superheromod.client.render;

import com.FIRNI.superheromod.network.packet.SandShapeSyncPacket;
import com.FIRNI.superheromod.core.animation.TimedSnapshotBuffer;
import java.util.*;

/** Network snapshots are sampled on one monotonic timeline, never client tick phase. */
public final class ClientSandShapeData {
    private static final long DELAY_NS=100_000_000L;
    private static final long STALE_NS=800_000_000L;
    private static final TimedSnapshotBuffer<Map<Integer,SandShapeSyncPacket.Shape>> history=
            new TimedSnapshotBuffer<>(8);
    private static long lastPacket;
    private ClientSandShapeData() {}

    public static void set(List<SandShapeSyncPacket.Shape> list) {
        Map<Integer,SandShapeSyncPacket.Shape> next=new HashMap<>();
        for(var shape:list)next.put(shape.id(),shape);
        long now=System.nanoTime();
        if(lastPacket!=0 && now-lastPacket>STALE_NS)history.clear();
        history.add(now,Map.copyOf(next));
        lastPacket=now;
    }

    public static List<SandShapeSyncPacket.Shape> get() {
        long now=System.nanoTime();
        if(lastPacket==0 || now-lastPacket>STALE_NS)return Collections.emptyList();
        var blend=history.sample(now-DELAY_NS);
        if(blend==null)return Collections.emptyList();
        List<SandShapeSyncPacket.Shape> out=new ArrayList<>(blend.to().size());
        for(var current:blend.to().values()) {
            var previous=blend.from().get(current.id());
            out.add(previous==null || previous.type()!=current.type()
                    ?current:lerp(previous,current,blend.fraction()));
        }
        return out;
    }

    private static SandShapeSyncPacket.Shape lerp(SandShapeSyncPacket.Shape a,
                                                  SandShapeSyncPacket.Shape b, float t) {
        return new SandShapeSyncPacket.Shape(
                b.id(), b.type(),
                a.x() + (b.x() - a.x()) * t,
                a.y() + (b.y() - a.y()) * t,
                a.z() + (b.z() - a.z()) * t,
                lerpAngle(a.yaw(), b.yaw(), t),
                lerpAngle(a.pitch(), b.pitch(), t),
                a.grow() + (b.grow() - a.grow()) * t,
                a.curl() + (b.curl() - a.curl()) * t,
                a.sink() + (b.sink() - a.sink()) * t);
    }

    /**
     * Aci ara degeri KISA YOLDAN.
     *
     * Duz ara deger 179 dereceden -179 dereceye giderken sekli tam tur
     * dondururdu -- tek karede firil firil donen bir el.
     */
    private static float lerpAngle(float a, float b, float t) {
        float delta = ((b - a) % 360f + 540f) % 360f - 180f;
        return a + delta * t;
    }


    public static void clear() {
        history.clear();
        lastPacket=0;
    }
}
