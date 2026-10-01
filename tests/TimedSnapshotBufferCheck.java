import com.FIRNI.superheromod.core.animation.TimedSnapshotBuffer;

public class TimedSnapshotBufferCheck {
    private static double value(TimedSnapshotBuffer<Double> buffer,long time) {
        var b=buffer.sample(time); return b.from()+(b.to()-b.from())*b.fraction();
    }
    private static void close(double a,double b) {
        if(Math.abs(a-b)>.0001)throw new AssertionError(a+" != "+b);
    }
    public static void main(String[] args) {
        var buffer=new TimedSnapshotBuffer<Double>(6);
        if(buffer.sample(0)!=null)throw new AssertionError("Empty sample");
        // Jittered arrivals; render time is independent of client tick phase.
        for(long time:new long[]{0,48,103,149,207})buffer.add(time,time/50.0);
        for(int time=0;time<=207;time++)close(value(buffer,time),time/50.0);
        double before=value(buffer,180);
        buffer.add(253,253/50.0);
        close(value(buffer,180),before); // New packet must not rewind a buffered frame.
        close(value(buffer,1000),253/50.0); // Stall holds; no runaway extrapolation.
        buffer.add(253,6.0); close(value(buffer,253),6); // Same-clock replacement.
        for(long i=300;i<2000;i+=50)buffer.add(i,(double)i);
        if(buffer.size()!=6)throw new AssertionError("Unbounded history");
        buffer.clear(); if(buffer.sample(0)!=null)throw new AssertionError("Stale scene survived clear");
        System.out.println("PASS: jitter, frame continuity, stall, duplicate timestamp, bounded history, clear");
    }
}
