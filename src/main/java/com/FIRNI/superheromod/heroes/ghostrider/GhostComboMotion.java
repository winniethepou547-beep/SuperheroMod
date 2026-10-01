package com.FIRNI.superheromod.heroes.ghostrider;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared choreography, sampled continuously on clients and at contact ticks on the server. */
public final class GhostComboMotion {
    public record Pose(float torso, float lean, float armX, float armY, float armZ,
                       float elbow, float guard, float weight, Vec3 grip, boolean twoHanded, int handSide, boolean twinChains,
                       Vec3 offGrip) {
        public Pose(float torso,float lean,float armX,float armY,float armZ,float elbow,float guard,float weight,Vec3 grip,boolean twoHanded,int handSide,boolean twinChains) {
            this(torso,lean,armX,armY,armZ,elbow,guard,weight,grip,twoHanded,handSide,twinChains,null);
        }
        public Pose(float torso,float lean,float armX,float armY,float armZ,float elbow,float guard,float weight,Vec3 grip,boolean twoHanded) {
            this(torso,lean,armX,armY,armZ,elbow,guard,weight,grip,twoHanded,-1,false);
        }
        public Pose(float torso,float lean,float armX,float armY,float armZ,float elbow,float guard,float weight) {
            this(torso,lean,armX,armY,armZ,elbow,guard,weight,null,false);
        }
    }
    private static final Pose REST = new Pose(0,0,0,0,0,-.12f,0,0,new Vec3(-.375,.68,0),false);
    // Grip paths are authored in model space: -X is the character's right, -Y is up.
    // Hand, arm IK, chain anchor and lash all share these paths.
    private static Pose strike(float twist,float lean,double x,double y,double z) {
        return new Pose(twist,lean,0,0,0,-.2f,-.8f,1,new Vec3(x,y,z),false);
    }
    // Deeper coil and follow-through so the body visibly throws its weight into the chain.
    private static final Pose[] WINDUP = {
            strike(-.38f,-.14f,-.68,-.20,-.24),
            strike(.38f,-.14f,.68,-.20,-.24),
            strike(-.12f,-.24f,-.35,-.38,-.18)};
    private static final Pose[] CONTACT = {
            strike(.08f,.14f,-.29,.10,-.53),
            strike(-.08f,.14f,.29,.10,-.53),
            strike(.04f,.24f,-.35,.08,-.55)};
    private static final Pose[] FOLLOW = {
            strike(.42f,.26f,.08,.42,-.23),
            strike(-.42f,.26f,-.08,.42,-.23),
            strike(.1f,.34f,-.35,.63,-.25)};
    /** Global chain length factor (+30%), applied to every chain move. */
    public static final double LENGTH=1.3;
    /** Extra lash reach in blocks on top of the scaled chain. */
    public static final double EXTRA_REACH=4;
    /** Lash reach relative to the original 3.6 block lash: +40%, the global +30%, then +4 blocks. */
    public static final double REACH=1.4*LENGTH+EXTRA_REACH/3.6;
    public static int duration(int combo) { return combo == 2 ? 29 : 23; }
    // Slow, heavy raise (hit-3 ticks) followed by a fast 3-tick strike.
    public static int impactTick(int combo) { return combo == 2 ? 15 : 11; }
    private static float ease(float t) { t=Math.max(0,Math.min(1,t)); return t*t*(3-2*t); }
    /** Slow start, gathering speed: a heavy chain being hauled up. */
    private static float haul(float t) { t=Math.max(0,Math.min(1,t)); return t*t*(2.2f-1.2f*t); }
    private static float mix(float a,float b,float t) { return a+(b-a)*t; }
    private static Pose blend(Pose a,Pose b,float t) { return shaped(a,b,ease(t)); }
    private static Pose shaped(Pose a,Pose b,float t) {
        return new Pose(mix(a.torso,b.torso,t),mix(a.lean,b.lean,t),mix(a.armX,b.armX,t),
                mix(a.armY,b.armY,t),mix(a.armZ,b.armZ,t),mix(a.elbow,b.elbow,t),
                mix(a.guard,b.guard,t),mix(a.weight,b.weight,t),
                a.grip==null || b.grip==null?null:a.grip.lerp(b.grip,t),a.twoHanded || b.twoHanded);
    }
    public static Pose pose(int combo,float tick) {
        int c=Math.floorMod(combo,3), hit=impactTick(c);
        Pose rest=c==1?new Pose(0,0,0,0,0,-.12f,0,0,new Vec3(.375,.68,0),false):REST;
        Pose p;
        if(tick<0 || tick>=duration(c))p=REST;
        else if(tick<=hit-3)p=shaped(rest,WINDUP[c],haul(tick/(hit-3)));
        else if(tick<=hit)p=blend(WINDUP[c],CONTACT[c],(tick-hit+3)/3);
        else if(tick<=hit+3)p=blend(CONTACT[c],FOLLOW[c],(tick-hit)/3);
        else p=blend(FOLLOW[c],rest,(tick-hit-3)/(duration(c)-hit-3));
        // Left-hand strike starts and recovers from the left resting grip.
        if(c==1 && (tick<0 || tick>=duration(c)))p=new Pose(0,0,0,0,0,-.12f,0,0,new Vec3(.375,.68,0),false);
        return new Pose(p.torso,p.lean,p.armX,p.armY,p.armZ,p.elbow,p.guard,p.weight,p.grip,c==2,c==1?1:-1,c==2);
    }
    /** Same shoulder/elbow/forearm transforms as the rendered layer, in vanilla model pixels. */
    public static Matrix4f armMatrix(Pose p,int side) {
        float shoulderX=side*5*(float)Math.cos(p.torso), shoulderZ=-side*5*(float)Math.sin(p.torso);
        return new Matrix4f().translate(shoulderX/16,2f/16,shoulderZ/16)
                .rotateZYX(side<0?p.armZ:.12f,side<0?p.armY+p.torso:p.torso*.5f,side<0?p.armX:p.guard)
                .translate(side*.0625f,0,0);
    }
    public static GhostRidingArms.Arm arm(Pose p,int side) {
        Vector3f s=new Matrix4f().rotateY(p.torso).rotateX(p.lean)
                .transformPosition(new Vector3f(side*.375f,.125f,0));
        Vec3 shoulder=new Vec3(s.x,s.y,s.z);
        Vec3 target=side==p.handSide?p.grip:p.offGrip!=null?p.offGrip:(p.twoHanded?
                (p.twinChains?new Vec3(-p.grip.x,p.grip.y,p.grip.z):p.grip.add(.065,-.035,-.17))
                :new Vec3(side*.31,.20,-.39));
        return GhostRidingArms.solve(shoulder,target,new Vec3(side,.35,.15));
    }
    public static Vec3 modelToWorld(Vec3 feet,float yaw,Vec3 point) {
        return feet.add(new Vec3(point.x,1.501-point.y,-point.z)
                .scale(HellCycleRig.PLAYER_SCALE).yRot((float)Math.toRadians(-yaw)));
    }
    /** Full charge, then a lean-back wind-up and a short forward throw before the rings fly. */
    public static final int CHARGE_FULL=40, CHARGE_COIL=5, CHARGE_THROW=3;
    public static int chargeRelease() { return CHARGE_FULL+CHARGE_COIL+CHARGE_THROW; }
    private static float coil(float tick) {
        return ease((tick-CHARGE_FULL)/CHARGE_COIL)*(1-ease((tick-CHARGE_FULL-CHARGE_COIL)/CHARGE_THROW));
    }
    private static float thrown(float tick) { return ease((tick-CHARGE_FULL-CHARGE_COIL)/CHARGE_THROW); }
    public static Pose chargePose(float tick) {
        float growth=ease(tick/8), back=coil(tick), out=thrown(tick);
        return new Pose((float)Math.sin(tick*.18)*.10f*growth*(1-back),-.08f*growth-.36f*back+.30f*out,0,0,0,-.6f,-.6f,growth,
                chargeGrip(tick,-1,Vec3.ZERO),true);
    }
    /** Spin angle in radians: starts slow, winds up to ~1.4 rad per tick (over 4 turns a second). */
    public static double chargePhase(float tick) {
        double t=Math.max(0,tick);
        return .25*t+1.2*(t-14*(1-Math.exp(-t/14)));
    }
    public static double chargeSpin(float tick) { return .25+1.2*(1-Math.exp(-Math.max(0,tick)/14)); }
    /** shift: physical reaction of the hands to the chain tension, in model space. */
    private static Vec3 chargeGrip(float tick,int side,Vec3 shift) {
        float grow=ease(tick/8), back=coil(tick), out=thrown(tick);
        Vec3 base=new Vec3(side*(.375+.12*grow),.53,-.27);
        // Coil: hands drawn up and back over the shoulders. Throw: whipped forward.
        base=base.add(-side*.05*back,-.22*back,.20*back).add(-side*.10*out,-.12*out,-.13*out);
        return base.add(shift);
    }
    public static GhostRidingArms.Arm chargeArm(float tick,int side) { return chargeArm(tick,side,Vec3.ZERO); }
    public static GhostRidingArms.Arm chargeArm(float tick,int side,Vec3 shift) {
        Pose p=chargePose(tick);
        Vector3f s=new Matrix4f().rotateY(p.torso).rotateX(p.lean).transformPosition(new Vector3f(side*.375f,.125f,0));
        return GhostRidingArms.solve(new Vec3(s.x,s.y,s.z),chargeGrip(tick,side,shift),new Vec3(side*.2,.9,.15));
    }
    public static Vec3 chargePoint(Vec3 feet,float yaw,float tick,int side,double t) {
        return chargePoint(feet,yaw,tick,side,t,Vec3.ZERO);
    }
    public static Vec3 chargePoint(Vec3 feet,float yaw,float tick,int side,double t,Vec3 shift) {
        Vec3 hand=modelToWorld(feet,yaw,chargeGrip(tick,side,shift));
        double growth=ease(tick/8),angle=chargePhase(tick)*side-(1-t)*Math.PI*1.75;
        Vec3 radial=new Vec3(0,0,1);
        Vec3 offset=radial.scale((.75+Math.cos(angle)*1.7)*growth*LENGTH)
                .add(side*.08*growth,(.5+Math.sin(angle)*1.7)*growth*LENGTH,.25*growth);
        double attach=ease((float)(t/.24));
        return hand.add(offset.scale(attach).yRot((float)Math.toRadians(-yaw)));
    }
    public static Pose grapple(float tick,boolean pulling) {
        if(!pulling)return new Pose(0,.08f,-1.5f,-.15f,-.12f,-.15f,-.7f,0,new Vec3(-.32,.10,-.53),false);
        // Gather the chain, then haul hand over hand: one hand reaches forward along the
        // chain while the other drags back to the hip, the torso twisting and leaning back.
        float gather=ease(tick/3),ramp=ease((tick-3)/4);
        double phase=haulPhase(tick);
        double a=.5+.5*Math.sin(phase);
        Vec3 start=new Vec3(-.32,.10,-.53).lerp(new Vec3(-.12,.08,-.48),gather);
        Vec3 right=start.lerp(new Vec3(-.16,.10,-.52).lerp(new Vec3(-.26,.34,-.10),a),ramp);
        Vec3 left=start.add(.065,-.035,-.17).lerp(new Vec3(-.02,.12,-.50).lerp(new Vec3(.14,.34,-.10),1-a),ramp);
        float torso=(float)(-.40*ramp+.12*Math.sin(phase)*ramp);
        return new Pose(torso,-.22f*ramp,0,0,0,-1.1f,-1.1f,ramp,right,true,-1,false,left);
    }
    /** One hand-over-hand cycle in ticks; each cycle has two hauls (one per hand). */
    public static final float HAUL_PERIOD=8;
    private static double haulPhase(float tick) { return Math.max(0,tick-3)/HAUL_PERIOD*Math.PI*2; }
    /** How hard the chain is being hauled right now (0.35 between hauls, 1 mid-haul). */
    public static double pullStrength(float tick) {
        return tick<3?.3:.35+.65*Math.abs(Math.cos(haulPhase(tick)));
    }
    /** Right-click throw: arm cocks back over the shoulder, then whips forward and releases. */
    public static final int CAST_RELEASE=3;
    public static Pose cast(float tick) {
        float back=ease(tick/CAST_RELEASE), out=ease((tick-CAST_RELEASE)/2f);
        Vec3 grip=REST.grip.lerp(new Vec3(-.44,-.06,.24),back).lerp(new Vec3(-.30,.14,-.46),out);
        float torso=-.35f*back*(1-out)+.28f*out, lean=-.06f*back*(1-out)+.16f*out;
        return new Pose(torso,lean,0,0,0,-.3f,-.6f,Math.max(back,out),grip,false);
    }
    /** Penance Stare: seize the throat, lean in face to face, convulse at the climax, throw down. */
    public static Pose penance(float tick,int grab,int lift,int climax,int climaxEnd,int total) {
        float reach=ease(tick/grab), inLean=ease((tick-grab)/(float)(lift-grab));
        float stare=ease((tick-lift)/(float)(climax-lift)), burst=ease((tick-climax)/3f)*(1-ease((tick-climaxEnd)/4f));
        float release=ease((tick-climaxEnd)/4f)*(1-ease((tick-climaxEnd-4)/(float)(total-climaxEnd-4)));
        float recover=ease((tick-climaxEnd-4)/(float)(total-climaxEnd-4));
        Vec3 throat=new Vec3(-.12,-.1,-.5);
        Vec3 grip=REST.grip.lerp(throat,reach).add(0,.02*Math.sin(tick*1.7)*stare,0)
                .lerp(new Vec3(-.20,.10,-.50),release).lerp(REST.grip,recover);
        Vec3 fist=new Vec3(.31,.20,-.39).lerp(new Vec3(.40,.22,.10),inLean).lerp(new Vec3(.375,.68,0),recover);
        float lean=(.12f*inLean+.18f*stare+.12f*burst+.1f*release)*(1-recover);
        float torso=(-.12f*inLean-.08f*burst+.2f*release)*(1-recover);
        return new Pose(torso,lean,0,0,0,-.3f,-.6f,Math.max(inLean,release)*(1-recover),grip,false,-1,false,fist);
    }
    // Hell Slam (F): throw both chains, wrap and hoist the target overhead, then drive it down.
    public static final int SLAM_WINDUP=4, SLAM_RELEASE=7, LIFT_WRAP=4, LIFT_TOTAL=16, DOWN_STRIKE=3;
    /** Ticks for a hand movement to travel down the chain to the bound body. */
    public static final int CHAIN_LAG=3;
    private static Pose twin(float torso,float lean,float weight,Vec3 right) {
        return new Pose(torso,lean,0,0,0,-.4f,-.6f,weight,right,false,-1,true,new Vec3(-right.x,right.y,right.z));
    }
    public static Pose slamThrow(float tick) {
        float back=ease(tick/SLAM_WINDUP), out=ease((tick-SLAM_WINDUP)/(float)(SLAM_RELEASE-SLAM_WINDUP));
        Vec3 grip=REST.grip.lerp(new Vec3(-.42,-.02,.20),back).lerp(new Vec3(-.22,.16,-.52),out);
        return twin(0,-.2f*back*(1-out)+.22f*out,Math.max(back,out),grip);
    }
    public static Pose slamLift(float tick) {
        float raise=ease((tick-LIFT_WRAP)/(float)(LIFT_TOTAL-LIFT_WRAP));
        Vec3 grip=new Vec3(-.22,.16,-.52).lerp(new Vec3(-.16,-.42,-.10),raise);
        return twin(0,.22f*(1-raise)-.22f*raise,1,grip);
    }
    public static Pose slamDown(float tick) {
        float down=ease(tick/DOWN_STRIKE);
        Vec3 grip=new Vec3(-.16,-.42,-.10).lerp(new Vec3(-.24,.46,-.42),down);
        return twin(0,-.22f+.60f*down,1,grip);
    }
    private static final Vec3 FIST_COCKED=new Vec3(-.40,.22,.10), FIST_OUT=new Vec3(-.14,.20,-.52);
    private static final Vec3 REEL_GRIP=new Vec3(.30,.12,-.50), COUNTER=new Vec3(.36,.30,.04);
    /** Pulled along by the chain in the left hand while the right fist coils back. */
    public static Pose reel(float tick) {
        float coil=ease(tick/4);
        return new Pose(-.35f*coil,.12f*coil,0,0,0,-.4f,-.6f,coil,REEL_GRIP,false,1,false,
                new Vec3(-.31,.20,-.39).lerp(FIST_COCKED,coil));
    }
    /** One-handed straight: fast extension to contact, short hold, then recovery. */
    public static Pose punch(float tick,int contact,int duration) {
        float out=ease(tick/contact), back=ease((tick-contact-3)/(float)(duration-contact-3));
        Vec3 fist=FIST_COCKED.lerp(FIST_OUT,out).lerp(REST.grip,back);
        float torso=mix(-.35f,.42f,out)*(1-back), lean=mix(.12f,.24f,out)*(1-back);
        return new Pose(torso,lean,0,0,0,-.2f,-.6f,1-back,fist,false,-1,false,
                REEL_GRIP.lerp(COUNTER,out).lerp(new Vec3(.375,.68,0),back));
    }
    public static Vec3 hand(Vec3 feet,float yaw,Pose p) {
        if(p.grip!=null)return modelToWorld(feet,yaw,arm(p,p.handSide).hand());
        Matrix4f m=new Matrix4f().rotationY((float)Math.toRadians(180-yaw)).scale(-1,-1,1)
                .mul(armMatrix(p,-1)).translate(0,.25f,0).rotateX(p.elbow);
        Vector3f v=m.transformPosition(new Vector3f(0,.35f,0));
        return feet.add(new Vec3(v.x,1.501+v.y,v.z).scale(HellCycleRig.PLAYER_SCALE));
    }
    public static Vec3 tip(Vec3 feet,float yaw,int combo,float tick) {
        return tip(feet,yaw,combo,tick,pose(combo,tick).handSide);
    }
    public static Vec3 tip(Vec3 feet,float yaw,int combo,float tick,int side) {
        int c=Math.floorMod(combo,3), hit=impactTick(c);
        Vec3 hand=modelToWorld(feet,yaw,arm(pose(c,tick),side).hand());
        float strike=ease((tick-hit+3)/6);
        float extend=ease(tick/(hit-2))*(1-ease((tick-hit-3)/(duration(c)-hit-3)));
        double arc=(-75+150*strike)*Math.PI/180;
        // Match the hand: right-high -> left-low; left-high -> right-low; overhead down.
        Vec3 local=c==2?new Vec3(0,-Math.sin(arc)*2.5,Math.cos(arc)*3.6)
                :new Vec3(Math.sin(arc)*2.9*(c==1?-1:1),1.65-3.3*strike,Math.cos(arc)*3.6);
        return hand.add(local.scale(extend*REACH).yRot((float)Math.toRadians(-yaw)));
    }
    /** Curved lash, lagging behind the hand; endpoints remain exactly on the hand and tip. */
    public static Vec3 point(Vec3 from,Vec3 to,int combo,float tick,double t) {
        Vec3 axis=to.subtract(from), side=axis.cross(new Vec3(0,1,0)).normalize();
        double sweep=Math.sin(Math.PI*Math.max(0,Math.min(1,tick/duration(combo))));
        Vec3 bend=(combo==2?new Vec3(0,.65,0):side.scale(combo==1?-.7:.7)).scale(REACH);
        return from.lerp(to,t).add(bend.scale(Math.sin(t*Math.PI)*sweep));
    }
}
