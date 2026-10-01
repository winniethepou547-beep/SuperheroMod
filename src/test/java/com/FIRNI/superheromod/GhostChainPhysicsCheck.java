package com.FIRNI.superheromod;
import com.FIRNI.superheromod.client.render.ghost.ChainDynamics;
import net.minecraft.world.phys.Vec3;
public final class GhostChainPhysicsCheck {
    public static void main(String[] args) {
        Vec3 from=new Vec3(0,2,0),to=new Vec3(4,2,0);
        ChainDynamics a=run(30,from,to), b=run(144,from,to);
        if(a.point(.5).distanceTo(b.point(.5))>.03)throw new AssertionError("Frame-rate dependent chain");
        if(a.point(.5).y>=2)throw new AssertionError("No gravitational sag");
        a.update(new Vec3(0,2,1),new Vec3(-3,2,0),41);
        for(int n=0;n<=24;n++) {
            Vec3 p=a.point(n/24.0);
            if(!Double.isFinite(p.x+p.y+p.z) || p.length()>12)throw new AssertionError("Unstable reversal");
        }
        a.update(from,from,42);
        for(int n=0;n<=24;n++)if(!Double.isFinite(a.point(n/24.0).length()))throw new AssertionError("Collapsed chain NaN");
        a.update(new Vec3(100,5,100),new Vec3(101,5,100),100);
        if(a.point(0).distanceTo(new Vec3(100,5,100))>1e-6)throw new AssertionError("Teleport reset");
        System.out.println("PASS: fixed-step frame rate equivalence, sag, reversal, collapsed endpoints, teleport reset");
        driven();
    }
    /** Lash swinging 150 degrees in 6 ticks, then holding still. */
    private static Vec3 lash(double t,double time) {
        double s=Math.max(0,Math.min(1,(time-4)/6)); s=s*s*(3-2*s);
        double angle=Math.toRadians(-75+150*s);
        Vec3 hand=new Vec3(s*.4,2,0);
        return hand.add(new Vec3(Math.sin(angle),0,Math.cos(angle)).scale(3.6*t));
    }
    private static double angleOf(Vec3 v) { return Math.toDegrees(Math.atan2(v.x,v.z)); }
    private static void driven() {
        ChainDynamics a=new ChainDynamics(), b=new ChainDynamics();
        double lagSeen=0,overshoot=-999;
        for(int i=0;i<=60*2;i++) {
            double time=i/3.0;
            a.drive(GhostChainPhysicsCheck::lash,time);
            if(a.point(0).distanceTo(lash(0,time))>1e-6)throw new AssertionError("Hand anchor drift");
            Vec3 tip=a.point(1).subtract(a.point(0));
            if(tip.length()>3.6*1.02)throw new AssertionError("Chain stretched: "+tip.length());
            double target=angleOf(lash(1,time).subtract(lash(0,time)));
            if(time>5 && time<10)lagSeen=Math.max(lagSeen,target-angleOf(tip));
            if(time>=10)overshoot=Math.max(overshoot,angleOf(tip)-75);
        }
        for(int i=0;i<=144*2;i++)b.drive(GhostChainPhysicsCheck::lash,i*20.0/144);
        if(lagSeen<8)throw new AssertionError("Tip does not trail the hand: "+lagSeen);
        if(overshoot<3)throw new AssertionError("No whip overshoot after the hand stops: "+overshoot);
        if(a.point(1).distanceTo(b.point(1))>.08)throw new AssertionError("Driven chain frame-rate dependent: "+a.point(1).distanceTo(b.point(1)));
        Vec3 settled=a.point(1).subtract(a.point(0));
        if(Math.abs(angleOf(settled)-75)>6)throw new AssertionError("Tip did not settle on the lash: "+angleOf(settled));
        System.out.printf("PASS: driven whip lag %.1f deg, overshoot %.1f deg, settled, anchored, inextensible%n",lagSeen,overshoot);
    }
    private static ChainDynamics run(int fps,Vec3 a,Vec3 b) {
        ChainDynamics d=new ChainDynamics();
        for(int i=0;i<=fps*2;i++)d.update(a,b,i*20.0/fps);
        if(d.point(0).distanceTo(a)>1e-6 || d.point(1).distanceTo(b)>1e-6)throw new AssertionError("Anchor drift");
        return d;
    }
}
