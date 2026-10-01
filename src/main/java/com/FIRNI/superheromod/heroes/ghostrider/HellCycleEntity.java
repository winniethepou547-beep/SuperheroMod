package com.FIRNI.superheromod.heroes.ghostrider;

import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.entity.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;

/** Server authoritative vehicle. Summoning is a short physical approach, not a teleport cut. */
public final class HellCycleEntity extends Entity {
    public static final int APPROACH = 0, RIDING = 1, RUNAWAY = 2;
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LEAN = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(HellCycleEntity.class, EntityDataSerializers.FLOAT);
    public static final double MAX_SPEED=GhostRideMath.MAX_SPEED;
    private int boostTicks;
    private boolean wheelieHeld,launchPending;
    private float wheelieAngle;
    private int flightTicks, airTicks;
    /** Summon approach length, and when the rider leaps (ticks before arrival). */
    private static final int APPROACH_TICKS=18, RIDER_LEAP=13;
    private Vec3 start;
    /** Direction of travel in degrees; trails the heading when the tyres lose grip. */
    private float travelYaw;
    private float yawVelocity, pitchVelocity;   // degrees per tick
    private double fallSpeed;
    /** Client-side wheel angle in degrees, driven by ground speed. */
    public float wheelSpin, wheelSpinO;
    private Vec3 grappleTarget, tetherAnchor;
    private double tetherLength;
    public float speed() {return entityData.get(SPEED);}
    public void grappleBoost(Vec3 target) {grappleTarget=target;boostTicks=8;speed=Math.min(2.8,Math.max(.9,speed*1.4));}
    private UUID owner;
    private Vec3 arrival = Vec3.ZERO;
    private int phaseAge, lastInput;
    private float throttle = 1, steering;
    private double speed;
    private int lerpSteps;
    private double targetX,targetY,targetZ;
    private float targetYaw,targetPitch;
    public HellCycleEntity(EntityType<?> type, Level level) { super(type, level); setMaxUpStep(1.1f); }
    @Override protected void defineSynchedData() { entityData.define(PHASE, APPROACH); entityData.define(FUEL, 600); entityData.define(LEAN, 0f); entityData.define(SPEED,0f); }
    public int phase() { return entityData.get(PHASE); }
    public int fuel() { return entityData.get(FUEL); }
    public float lean() { return entityData.get(LEAN); }
    /** Client, film stage only: pose a bike that is not in the world (speed drives its fire, spin its wheels). */
    public void filmPose(float speed,float spin) { entityData.set(SPEED,speed); wheelSpinO=wheelSpin=spin; leanVisualO=leanVisual=0; }
    private float leanVisual, leanVisualO;
    /** Client: lean smoothed between ticks for rendering and the chase camera. */
    public float lean(float partial) { return Mth.lerp(partial,leanVisualO,leanVisual); }
    public static void summon(ServerPlayer p) {
        if (!p.isAlive()) return;
        if (!GhostRiderCharacter.ID.equals(AbilityManager.getCharacterId(p.getUUID()))) return;
        if (p.getVehicle() instanceof HellCycleEntity bike && p.getUUID().equals(bike.owner)) {
            bike.releaseRider();
            return;
        }
        if (!p.onGround() || p.isPassenger()) return;
        if (!p.level().getEntitiesOfClass(HellCycleEntity.class, p.getBoundingBox().inflate(96), b -> p.getUUID().equals(b.owner)).isEmpty()) return;
        Vec3 direction = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 spawn = p.position().subtract(direction.scale(7));
        if (!p.serverLevel().hasChunkAt(net.minecraft.core.BlockPos.containing(spawn))) return;
        var path = p.level().clip(new ClipContext(spawn.add(0,.6,0), p.position().add(0,.6,0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (path.getType() != HitResult.Type.MISS) return;
        HellCycleEntity bike = ModEntities.HELL_CYCLE.get().create(p.level());
        if (bike == null) return;
        bike.owner = p.getUUID(); bike.arrival = p.position(); bike.start = spawn; bike.setPos(spawn); bike.setYRot(p.getYRot()); bike.travelYaw = p.getYRot();
        if (!p.level().noCollision(bike, bike.getBoundingBox())) return;
        p.serverLevel().addFreshEntity(bike);
    }
    public void control(ServerPlayer p, float forward, float turn, boolean wheelie) {
        if (getFirstPassenger() != p || !p.getUUID().equals(owner)) return;
        if(wheelieHeld && !wheelie && wheelieAngle>1 && onGround() && flightTicks==0 && tickCount-lastInput<=10)launchPending=true;
        wheelieHeld=wheelie;
        throttle = Mth.clamp(forward, -1, 1); steering = Mth.clamp(turn, -1, 1); lastInput = tickCount;
    }
    /** Manual release and empty fuel must take exactly the same transition. */
    private void releaseRider() {
        if (phase() != RIDING) return;
        Vec3 travel = getDeltaMovement();
        if (travel.horizontalDistanceSqr() > .001)
            setYRot((float)Math.toDegrees(Math.atan2(-travel.x, travel.z)));
        travelYaw=getYRot();
        speed = Math.max(.65, travel.horizontalDistance());
        entityData.set(PHASE, RUNAWAY);
        entityData.set(LEAN, 0f);
        phaseAge = 0; steering = 0;wheelieHeld=false;launchPending=false;wheelieAngle=0;
        Entity passenger = getFirstPassenger();
        ejectPassengers();
        if (passenger != null) {
            Vec3 side = Vec3.directionFromRotation(0, getYRot() + 90);
            for (int sign : new int[]{1, -1}) {
                Vec3 landing = position().add(side.scale(1.35 * sign)).add(0,.25,0);
                if (level().noCollision(passenger, passenger.getBoundingBox().move(landing.subtract(passenger.position())))) {
                    passenger.teleportTo(landing.x, landing.y, landing.z);
                    break;
                }
            }
            passenger.setDeltaMovement(0,.25,0);
            passenger.fallDistance = 0; passenger.hurtMarked = true;
        }
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Rolling without slip (r = 6.8 px), capped where it would only strobe.
            leanVisualO=leanVisual;leanVisual+=(lean()-leanVisual)*.45f;
            wheelSpinO=wheelSpin;
            wheelSpin+=(float)Math.min(120,Math.toDegrees(speed()/(6.8/16)));
            if(wheelSpin>3600){wheelSpin-=3600;wheelSpinO-=3600;}
            if(lerpSteps>0) {
                setPos(getX()+(targetX-getX())/lerpSteps,getY()+(targetY-getY())/lerpSteps,getZ()+(targetZ-getZ())/lerpSteps);
                setYRot(getYRot()+Mth.wrapDegrees(targetYaw-getYRot())/lerpSteps);
                setXRot(getXRot()+(targetPitch-getXRot())/lerpSteps);lerpSteps--;
            }
            return;
        }
        var level = (ServerLevel)level();
        var rider = owner == null ? null : level.getPlayerByUUID(owner);
        if (rider == null || !rider.isAlive() || !GhostRiderCharacter.ID.equals(AbilityManager.getCharacterId(owner))) { ejectPassengers(); discard(); return; }
        phaseAge++;
        if (phase() == APPROACH) {
            // Roars in from behind and brakes hard under the rider, who leaps so that he
            // comes down onto the seat just as it arrives.
            double t=Math.min(1,phaseAge/(double)APPROACH_TICKS);
            double eased=1-Math.pow(1-t,2.4);
            Vec3 wantedPos=(start==null?position():start).lerp(arrival,eased);
            Vec3 step=wantedPos.subtract(position());
            setDeltaMovement(step.x, Math.max(-.3, getDeltaMovement().y - .08), step.z);
            if(phaseAge%3==0)level.sendParticles(ParticleTypes.FLAME,getX(),getY()+.2,getZ(),4,.25,.05,.25,.02);
            if (phaseAge == APPROACH_TICKS-RIDER_LEAP) {
                rider.setDeltaMovement(rider.getDeltaMovement().multiply(.2,0,.2).add(0,.6,0)); rider.hurtMarked = true;
                level.playSound(null,blockPosition(),SoundEvents.BLAZE_SHOOT,SoundSource.NEUTRAL,.9f,.55f);
            }
            if (phaseAge == APPROACH_TICKS-3)level.playSound(null,blockPosition(),SoundEvents.FIRECHARGE_USE,SoundSource.NEUTRAL,1f,.5f);
            move(MoverType.SELF, getDeltaMovement());
            if (horizontalCollision || rider.position().distanceTo(arrival) > 4) { discard(); return; }
            boolean landing=phaseAge>=APPROACH_TICKS && (rider.getDeltaMovement().y<=0 && rider.getY()<=getY()+1.3 || phaseAge>=APPROACH_TICKS+6);
            if (landing) {
                if (rider.distanceToSqr(this) > 9 || !rider.startRiding(this, true)) { discard(); return; }
                entityData.set(PHASE, RIDING); phaseAge = 0; speed = .25; travelYaw=getYRot();
                // The weight of the rider lands on the suspension; fire bursts from the wheels.
                pitchVelocity+=3.5f;
                level.playSound(null,blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.NEUTRAL,.45f,1.5f);
                level.playSound(null,blockPosition(),SoundEvents.CHAIN_FALL,SoundSource.NEUTRAL,.9f,.6f);
                Vec3 forward=Vec3.directionFromRotation(0,getYRot());
                for(int w=-1;w<=1;w+=2)
                    level.sendParticles(ParticleTypes.FLAME,getX()+forward.x*w*1.2,getY()+.3,getZ()+forward.z*w*1.2,18,.3,.15,.3,.06);
            }
        } else {
            boolean riding = phase() == RIDING;
            if (riding && (fuel() <= 0 || getFirstPassenger() != rider)) {
                releaseRider();
                riding = false;
            }
            if(flightTicks>3 && onGround())flightTicks=0;
            boolean airborne=flightTicks>0 || (!onGround() && airTicks>1);
            if (riding) {
                entityData.set(FUEL, Math.max(0, fuel()-1));
                if (tickCount - lastInput > 10) { throttle = 0; steering = 0;wheelieHeld=false;launchPending=false;wheelieAngle=0; }
                if(!airborne)speed = GhostRideMath.accelerate(speed,throttle);
                if(boostTicks>0){boostTicks--;speed=Math.max(speed,2.5);}
                else grappleTarget=null;
                if(airborne) {
                    // No grip in the air: steering only spins the bike about its own axis, with inertia.
                    yawVelocity=(yawVelocity-steering*GhostRideMath.AIR_SPIN_ACCEL)*GhostRideMath.AIR_SPIN_DAMPING;
                    setYRot(getYRot()+yawVelocity);
                    entityData.set(LEAN, Mth.lerp(.15f, lean(), steering * 28f));
                } else {
                    yawVelocity=-steering*GhostRideMath.turnRate(speed);
                    setYRot(getYRot()+yawVelocity);
                    // Lean balances the cornering force: harder at speed, gentle when slow.
                    float corner=Mth.clamp(-yawVelocity*(1.2f+(float)speed*1.3f),-36,36);
                    entityData.set(LEAN, Mth.lerp(.2f, lean(), corner));
                }
                if(tickCount%6==0 && !airborne) {
                    float rev=(float)Math.min(1,Math.abs(speed)/GhostRideMath.MAX_SPEED);
                    level.playSound(null,blockPosition(),SoundEvents.BLAZE_BURN,SoundSource.NEUTRAL,.35f+.35f*rev,.45f+.6f*rev);
                }
                rider.fallDistance = 0;
            } else speed = Math.max(.65, speed);
            // Traction: the path follows the heading with limited grip, so hard turns at speed
            // slide the bike out before it bites. In the air the path is pure momentum.
            if(!airborne) {
                float grip=Mth.lerp((float)Math.min(1,Math.abs(speed)/GhostRideMath.MAX_SPEED),.6f,GhostRideMath.HIGH_SPEED_GRIP);
                travelYaw+=Mth.wrapDegrees(getYRot()-travelYaw)*grip;
                float slip=Math.abs(Mth.wrapDegrees(getYRot()-travelYaw));
                if(slip>10 && speed>.8) {
                    speed*=.994;
                    if(tickCount%2==0) {
                        Vec3 rear=position().subtract(Vec3.directionFromRotation(0,getYRot()).scale(1.1));
                        level.sendParticles(ParticleTypes.LARGE_SMOKE,rear.x,rear.y+.15,rear.z,2,.15,.05,.15,.01);
                        level.sendParticles(ParticleTypes.FLAME,rear.x,rear.y+.1,rear.z,3,.2,.02,.2,.02);
                    }
                }
            }
            Vec3 heading = Vec3.directionFromRotation(0, getYRot());
            Vec3 direction = Vec3.directionFromRotation(0, travelYaw);
            var wall = level.clip(new ClipContext(position().add(0,.65,0), position().add(0,.65,0).add(heading.scale(Math.max(1.1,speed+.35))), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            // Single blocks are stepped over; only a real wall (two blocks high) is climbed.
            var upper = level.clip(new ClipContext(position().add(0,1.7,0), position().add(0,1.7,0).add(heading.scale(Math.max(1.1,speed+.35))), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean climbing = riding && throttle > 0 && wall.getType() != HitResult.Type.MISS && upper.getType() != HitResult.Type.MISS;
            if(riding && !climbing && !airborne && wheelieHeld)wheelieAngle=Math.min(45,wheelieAngle+2.5f);
            else if(!wheelieHeld && !launchPending)wheelieAngle=Math.max(0,wheelieAngle-4);
            Vec3 previousVelocity=getDeltaMovement();
            if(launchPending && riding && !airborne && !climbing) {
                setDeltaMovement(GhostRideMath.wheelieLaunch(getYRot(),-getXRot(),speed));
                flightTicks=1;wheelieAngle=0;launchPending=false;
                pitchVelocity=Math.min(pitchVelocity,0);
            } else if(airborne && !climbing) {
                if(flightTicks>0)flightTicks++;
                // Ballistic: momentum kept, light air drag, full gravity.
                setDeltaMovement(previousVelocity.multiply(.995,1,.995).add(0,-.08,0));
                if(getDeltaMovement().y<-2.4)setDeltaMovement(getDeltaMovement().multiply(1,0,1).add(0,-2.4,0));
                // Nose seeks the flight path and slowly drops forward; throttle lifts it, brake dips it.
                Vec3 v=getDeltaMovement();
                float pathPitch=(float)-Math.toDegrees(Math.atan2(v.y,Math.max(.05,v.horizontalDistance())));
                float wanted=Mth.clamp(pathPitch*.55f+GhostRideMath.AIR_NOSE_DROP,-55,40);
                pitchVelocity=(pitchVelocity+(wanted-getXRot())*.05f-throttle*.35f)*.88f;
                setXRot(Mth.clamp(getXRot()+pitchVelocity,-70,45));
                if(flightTicks>120)flightTicks=0;
            } else {
                if(airTicks>3)land(previousVelocity);
                if(climbing){pitchVelocity=0;setXRot(Mth.lerp(.3f,getXRot(),-88f));}
                else {
                    // Suspension spring: a raised nose falls and bounces once instead of snapping flat.
                    float wanted=-wheelieAngle+terrainPitch(level);
                    pitchVelocity=(pitchVelocity+(wanted-getXRot())*.22f)*.62f;
                    setXRot(Mth.clamp(getXRot()+pitchVelocity,-70,45));
                }
                setDeltaMovement(direction.scale(climbing ? .12 : speed).add(0, climbing ? Math.max(.5,Math.abs(speed)*.85) : Math.max(-.8, previousVelocity.y - .08), 0));
            }
            airTicks=onGround()?0:airTicks+1;
            if(airTicks>0)fallSpeed=Math.max(0,-getDeltaMovement().y);
            if(climbing)launchPending=false;
            if(tetherAnchor!=null)swingOnTether(airborne);
            if(grappleTarget!=null && boostTicks>0) {
                Vec3 toward=grappleTarget.subtract(position());
                if(toward.length()>1.5)setDeltaMovement(toward.normalize().scale(speed));
            }
            entityData.set(SPEED,(float)getDeltaMovement().length());
            AABB swept=getBoundingBox().expandTowards(getDeltaMovement()).inflate(.1);
            move(MoverType.SELF, getDeltaMovement());
            if (!riding && (horizontalCollision || !level.getEntities(this, swept, e -> e != rider && e.isAlive() && e.isPickable()).isEmpty())) {
                blast(level); discard(); return;
            }
            if (!riding && phaseAge > 160) { discard(); return; }
            if (tickCount % 3 == 0 && speed > .1) {
                Vec3 rear = position().subtract(direction.scale(1));
                level.sendParticles(ParticleTypes.FLAME, rear.x, rear.y+.15, rear.z, 3, .12,.08,.12,.015);
            }
        }
    }
    /** Debris launched per crash, and the radius blocks are torn from. */
    private static final int MAX_DEBRIS=30;
    private static final double DEBRIS_RADIUS=3.2;
    /**
     * Runaway crash: nearby blocks are torn loose and thrown outward as real falling blocks,
     * then a fiery explosion breaks what is left and scorches the ground.
     */
    private void blast(ServerLevel level) {
        Vec3 center=position().add(0,.5,0);
        var random=level.getRandom();
        int launched=0, reach=(int)Math.ceil(DEBRIS_RADIUS);
        var origin=net.minecraft.core.BlockPos.containing(center);
        for(int dx=-reach;dx<=reach && launched<MAX_DEBRIS;dx++)for(int dy=-reach;dy<=reach && launched<MAX_DEBRIS;dy++)for(int dz=-reach;dz<=reach && launched<MAX_DEBRIS;dz++) {
            var pos=origin.offset(dx,dy,dz);
            Vec3 middle=Vec3.atCenterOf(pos);
            double distance=middle.distanceTo(center);
            if(distance>DEBRIS_RADIUS || random.nextFloat()>(1-distance/(DEBRIS_RADIUS+.2))*.6f)continue;
            var state=level.getBlockState(pos);
            float hardness=state.getDestroySpeed(level,pos);
            if(state.isAir() || !state.getFluidState().isEmpty() || hardness<0 || hardness>=50 || level.getBlockEntity(pos)!=null
                    || !state.isCollisionShapeFullBlock(level,pos))continue;
            var debris=net.minecraft.world.entity.item.FallingBlockEntity.fall(level,pos,state);
            Vec3 out=middle.subtract(center);
            out=out.lengthSqr()<1e-4?new Vec3(0,1,0):out.normalize();
            debris.setDeltaMovement(out.scale(.45+random.nextDouble()*.55).add(0,.55+random.nextDouble()*.55,0));
            debris.dropItem=false;
            debris.hurtMarked=true;
            launched++;
        }
        level.explode(this,center.x,center.y,center.z,3.4f,true,Level.ExplosionInteraction.TNT);
        level.sendParticles(ParticleTypes.FLAME,center.x,center.y,center.z,60,1.2,.8,1.2,.12);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,center.x,center.y+.5,center.z,30,1.4,.9,1.4,.04);
        com.FIRNI.superheromod.network.ModNetworking.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(()->this),
                new com.FIRNI.superheromod.network.packet.ShockwavePacket(position(),6,16,.8f));
        com.FIRNI.superheromod.network.ModNetworking.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(()->this),
                new com.FIRNI.superheromod.network.packet.GhostSlamPacket(position()));
    }
    /** Chain anchored to a surface: a fixed length the bike cannot exceed. */
    public void tether(Vec3 anchor,double length) { tetherAnchor=anchor; tetherLength=length; }
    /**
     * Inextensible chain: velocity pointing away from the anchor is cancelled once taut, the rest
     * becomes tangential, so the bike circles the anchor at chain radius (a pendulum in the air).
     * The bike turns its nose along the circle and leans into it.
     */
    private void swingOnTether(boolean airborne) {
        Vec3 velocity=getDeltaMovement();
        Vec3 offset=position().add(velocity).subtract(tetherAnchor);
        if(!airborne)offset=offset.multiply(1,0,1);
        double distance=offset.length();
        if(distance<=tetherLength || distance<1e-4)return;
        Vec3 radial=offset.scale(1/distance);
        double outward=velocity.dot(radial);
        if(outward>0)velocity=velocity.subtract(radial.scale(outward));
        // Pull back onto the circle so the error never accumulates.
        velocity=velocity.subtract(radial.scale(distance-tetherLength));
        setDeltaMovement(velocity);
        Vec3 flat=velocity.multiply(1,0,1);
        if(flat.lengthSqr()>.01) {
            float heading=(float)Math.toDegrees(Math.atan2(-flat.x,flat.z));
            travelYaw=heading;
            float turn=Mth.wrapDegrees(heading-getYRot());
            setYRot(getYRot()+turn*.35f);
            double side=radial.x*flat.z-radial.z*flat.x;
            entityData.set(LEAN,Mth.lerp(.2f,lean(),(float)Math.signum(side)*-26f));
        }
    }
    /** Ground slope under the wheels, so the chassis follows ramps and steps. Negative = nose up. */
    private float terrainPitch(ServerLevel level) {
        Vec3 heading=Vec3.directionFromRotation(0,getYRot());
        double front=groundHeight(level,position().add(heading.scale(1.3))), rear=groundHeight(level,position().subtract(heading.scale(1.0)));
        if(Double.isNaN(front) || Double.isNaN(rear))return 0;
        return Mth.clamp((float)-Math.toDegrees(Math.atan2(front-rear,2.3)),-35,35);
    }
    private double groundHeight(ServerLevel level,Vec3 at) {
        var hit=level.clip(new ClipContext(at.add(0,1.2,0),at.add(0,-1.6,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        return hit.getType()==HitResult.Type.MISS?Double.NaN:hit.getLocation().y;
    }
    /** Touchdown: travel off the bike's heading is scrubbed, and the suspension takes the hit. */
    private void land(Vec3 velocity) {
        Vec3 horizontal=velocity.multiply(1,0,1);
        if(horizontal.lengthSqr()>1e-4) {
            double align=horizontal.normalize().dot(Vec3.directionFromRotation(0,getYRot()));
            speed=horizontal.length()*Math.max(.25,align);
            travelYaw=getYRot();
        }
        // Nose-high landings slam the front down; nose-low ones slam the rear.
        pitchVelocity+=(getXRot()<=0?1:-1)*(float)Math.min(7,fallSpeed*3.5);
        yawVelocity=0;
        if(fallSpeed>.35) {
            float force=(float)Math.min(1,fallSpeed/1.6);
            level().playSound(null,blockPosition(),fallSpeed>.9?SoundEvents.GENERIC_BIG_FALL:SoundEvents.GENERIC_SMALL_FALL,SoundSource.NEUTRAL,.6f+.6f*force,.7f);
            level().playSound(null,blockPosition(),SoundEvents.CHAIN_FALL,SoundSource.NEUTRAL,.5f+.5f*force,.6f);
            if(level() instanceof ServerLevel server)
                server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,getX(),getY()+.1,getZ(),(int)(3+6*force),.6,.05,.6,.01);
        }
        fallSpeed=0;
    }
    @Override protected void positionRider(Entity passenger, MoveFunction move) {
        if (hasPassenger(passenger)) {
            Vec3 feet=HellCycleRig.riderFeet(getYRot(),getXRot(),level().isClientSide?leanVisual:lean());
            move.accept(passenger, getX()+feet.x, getY()+feet.y, getZ()+feet.z);
            passenger.fallDistance = 0;
            if (passenger instanceof LivingEntity living) {
                living.yBodyRot = getYRot();
            }
        }
    }
    @Override public double getPassengersRidingOffset() { return HellCycleRig.riderFeet(0,0,0).y; }
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps,boolean teleport) {
        // Updates arrive every tick: land exactly on them and let render interpolation do the
        // smoothing. Spreading each update over three ticks made the bike lag and lurch under acceleration.
        targetX=x;targetY=y;targetZ=z;targetYaw=yaw;targetPitch=pitch;lerpSteps=1;
    }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty() && e.getUUID().equals(owner); }
    @Override public boolean isPickable() { return true; }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override protected void readAdditionalSaveData(CompoundTag t) { if(t.hasUUID("Owner")) owner=t.getUUID("Owner"); entityData.set(PHASE, RUNAWAY); entityData.set(FUEL, 0); }
    @Override protected void addAdditionalSaveData(CompoundTag t) { if(owner!=null)t.putUUID("Owner",owner); }
}
