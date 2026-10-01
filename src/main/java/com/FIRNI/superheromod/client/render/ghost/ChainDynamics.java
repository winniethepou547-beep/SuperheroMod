package com.FIRNI.superheromod.client.render.ghost;
import net.minecraft.world.phys.Vec3;

/**
 * Fixed 120 Hz Verlet chain, independent of render FPS.
 * update(): pinned at both ends (grapple/latched).
 * drive(): pinned only at the hand; every other link is a free mass pulled toward the
 * choreographed lash by a spring that stiffens toward the hand. The target shape reaches
 * each link with a delay that grows along the chain, so the hand leads and the chain
 * whips after it, overshoots and settles with real momentum.
 */
public final class ChainDynamics {
    /** Choreographed chain shape: t in [0,1] from hand to tip, time in game ticks. */
    public interface Target { Vec3 at(double t, double time); }
    /**
     * rootStiffness/tipStiffness: spring toward the authored shape, 1/s^2.
     * drag: velocity kept per 120 Hz step. waveDelay: ticks for the hand's motion to reach the tip.
     */
    public record Tuning(double rootStiffness,double tipStiffness,double drag,double waveDelay) {}
    /** Heavy whip: hand leads, the tip trails, overshoots and settles. */
    public static final Tuning LASH=new Tuning(2600,320,.988,1.8);
    /** Fast twin-chain spin: stays on its ring but trails, sags and drags on the ground. */
    public static final Tuning SPIN=new Tuning(9000,2600,.99,.35);
    private static final int COUNT=33;
    private static final double STEP=1.0/120, GRAVITY=-9.8*STEP*STEP;
    private static final double ROPE_DRAG=.993;
    private static final double TIP_INV_MASS=.45;                        // heavier end link carries momentum
    private static final double LINK_RADIUS=.03, GROUND_FRICTION=.08;
    private final Vec3[] nodes=new Vec3[COUNT], old=new Vec3[COUNT], targets=new Vec3[COUNT];
    private final double[] rest=new double[COUNT-1];
    private final boolean[] touching=new boolean[COUNT];
    private double clock=Double.NaN, pending, floor=Double.NEGATIVE_INFINITY;
    /** Ground height under the chain; links rest on it and drag with friction. */
    public void floor(double y) { floor=y; }
    /** Links lying on the ground after the last update, with their sliding speed in blocks/tick. */
    public void contacts(java.util.function.BiConsumer<Vec3,Vec3> sink) {
        for(int i=1;i<COUNT;i++)if(touching[i])sink.accept(nodes[i],nodes[i].subtract(old[i]).scale(1/(STEP*20)));
    }

    private boolean needsReset(Vec3 from,double tickTime) {
        return Double.isNaN(clock) || tickTime<clock || tickTime-clock>10 || nodes[0].distanceTo(from)>5;
    }
    /** Advances the shared fixed-step clock and returns how many 120 Hz steps to run. */
    private int advance(double tickTime) {
        pending+=Math.min(.1,(tickTime-clock)/20);clock=tickTime;
        int steps=0;
        while(pending>=STEP-1e-9 && steps<12){pending-=STEP;steps++;}
        if(steps==12)pending=0;
        return steps;
    }
    public void update(Vec3 from,Vec3 to,double tickTime) {
        if(needsReset(from,tickTime)) {
            for(int i=0;i<COUNT;i++)nodes[i]=old[i]=from.lerp(to,i/(double)(COUNT-1));
            clock=tickTime; pending=0;
        }
        int steps=advance(tickTime);
        for(int step=0;step<steps;step++) {
            for(int i=1;i<COUNT-1;i++) {
                Vec3 current=nodes[i];
                nodes[i]=current.add(current.subtract(old[i]).scale(ROPE_DRAG)).add(0,GRAVITY,0);old[i]=current;
            }
            double length=Math.max(.015,from.distanceTo(to)*1.025/(COUNT-1));
            for(int pass=0;pass<12;pass++) {
                nodes[0]=from;nodes[COUNT-1]=to;
                for(int i=0;i<COUNT-1;i++) {
                    Vec3 d=nodes[i+1].subtract(nodes[i]);double len=d.length();if(len<1e-7)continue;
                    Vec3 correction=d.scale((len-length)/len);
                    if(i==0)nodes[i+1]=nodes[i+1].subtract(correction);
                    else if(i+1==COUNT-1)nodes[i]=nodes[i].add(correction);
                    else {nodes[i]=nodes[i].add(correction.scale(.5));nodes[i+1]=nodes[i+1].subtract(correction.scale(.5));}
                }
            }
        }
        nodes[0]=from;nodes[COUNT-1]=to;
    }
    public void drive(Target target,double tickTime) { drive(target,tickTime,LASH); }
    public void drive(Target target,double tickTime,Tuning tuning) {
        Vec3 hand=target.at(0,tickTime);
        if(needsReset(hand,tickTime)) {
            for(int i=0;i<COUNT;i++)nodes[i]=old[i]=target.at(i/(double)(COUNT-1),tickTime-tuning.waveDelay()*i/(COUNT-1));
            clock=tickTime; pending=0;
        }
        java.util.Arrays.fill(touching,false);
        int steps=advance(tickTime);
        for(int step=1;step<=steps;step++) {
            // Exact fixed-step time of this substep, so every frame rate samples the same moments.
            double time=tickTime-(pending+(steps-step)*STEP)*20;
            // Link lengths follow the current authored chain; spring targets arrive with delay.
            Vec3 previous=target.at(0,time);
            for(int i=0;i<COUNT;i++) {
                double t=i/(double)(COUNT-1);
                targets[i]=target.at(t,time-tuning.waveDelay()*t);
                if(i>0){Vec3 now=target.at(t,time);rest[i-1]=Math.max(.004,now.distanceTo(previous));previous=now;}
            }
            nodes[0]=old[0]=targets[0];
            for(int i=1;i<COUNT;i++) {
                double t=i/(double)(COUNT-1);
                double k=tuning.tipStiffness()+(tuning.rootStiffness()-tuning.tipStiffness())*Math.pow(1-t,1.5);
                Vec3 current=nodes[i];
                Vec3 pull=targets[i].subtract(current).scale(k*STEP*STEP);
                nodes[i]=current.add(current.subtract(old[i]).scale(tuning.drag())).add(pull).add(0,GRAVITY,0);
                old[i]=current;
            }
            // Links are inextensible but go slack under compression, like a real chain.
            for(int pass=0;pass<10;pass++) {
                for(int i=0;i<COUNT-1;i++) {
                    Vec3 d=nodes[i+1].subtract(nodes[i]);double len=d.length();
                    if(len<=rest[i] || len<1e-7)continue;
                    double wa=i==0?0:1, wb=i+1==COUNT-1?TIP_INV_MASS:1;
                    Vec3 correction=d.scale((len-rest[i])/len/(wa+wb));
                    nodes[i]=nodes[i].add(correction.scale(wa));
                    nodes[i+1]=nodes[i+1].subtract(correction.scale(wb));
                }
            }
            // Follow-the-leader pass from the hand: no link may exceed its length.
            for(int i=1;i<COUNT;i++) {
                Vec3 d=nodes[i].subtract(nodes[i-1]);double len=d.length();
                if(len>rest[i-1])nodes[i]=nodes[i-1].add(d.scale(rest[i-1]/len));
            }
            // Ground: links cannot sink; sliding links lose speed to friction.
            for(int i=1;i<COUNT;i++) {
                double lowest=floor+LINK_RADIUS;
                if(nodes[i].y>=lowest)continue;
                nodes[i]=new Vec3(nodes[i].x,lowest,nodes[i].z);
                Vec3 slide=nodes[i].subtract(old[i]);
                old[i]=new Vec3(old[i].x+slide.x*GROUND_FRICTION,lowest,old[i].z+slide.z*GROUND_FRICTION);
                touching[i]=true;
            }
        }
        nodes[0]=hand;
    }
    public Vec3 point(double t) {
        double index=Math.max(0,Math.min(1,t))*(COUNT-1);int i=Math.min(COUNT-2,(int)index);
        return nodes[i].lerp(nodes[i+1],index-i);
    }
}
