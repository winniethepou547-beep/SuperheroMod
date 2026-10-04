package com.FIRNI.superheromod.heroes.ghostrider;

import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.*;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.GhostChainPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.sounds.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class GhostChainController {
    public enum Mode { IDLE, SWING, CAST, LATCHED, PULL, REEL, READY, CHARGE, FLY, RECOVER, PUNCH, SLAM_THROW, SLAM_LIFT, SLAM_DOWN }
    private static final Map<UUID, State> STATES = new HashMap<>();
    public static final int PUNCH_CONTACT=2, PUNCH_DURATION=12;
    /** The right-click throw: wind-up ticks before the chain leaves the hand. */
    public static final int CAST_RELEASE=GhostComboMotion.CAST_RELEASE;
    /** Extra flight steps of 1.7 blocks: the thrown chain reaches about 4 blocks further. */
    private static final int CAST_EXTRA_STEPS=(int)Math.ceil(GhostComboMotion.EXTRA_REACH/1.7);
    /** Break-free meter per F press, and how fast it drains each tick. */
    private static final float ESCAPE_PER_PRESS=.09f, ESCAPE_DECAY=.012f;
    static final class State {
        Mode mode = Mode.IDLE;
        int age, cooldown, combo, heat, idleTicks, comboTicks;
        Vec3 tip = Vec3.ZERO, direction = Vec3.ZERO;
        UUID target;
        Vec3 anchor;
        double leash=8;
        float escape;
        UUID boundPlayer;
        final HellSlam slam=new HellSlam();
        boolean queued, held;
        long renewed;
        final Set<UUID> struck = new HashSet<>();
    }
    public static void press(ServerPlayer p, AbilitySlot slot) {
        if(HellfireBreathController.active(p.getUUID()) || PenanceStare.busy(p.getUUID()))return;
        if (!GhostRiderCharacter.ID.equals(AbilityManager.getCharacterId(p.getUUID())) || !p.isAlive() || p.isSpectator()) return;
        State s = STATES.computeIfAbsent(p.getUUID(), id -> new State());
        if(slot==AbilitySlot.LMB) {
            s.renewed=p.level().getGameTime();
            if(s.held)return;
            s.held=true;
        }
        if(slot==AbilitySlot.SKILL_F) {
            if(s.mode==Mode.IDLE && s.cooldown<=0 && !(p.getVehicle() instanceof HellCycleEntity))HellSlam.start(p,s);
            return;
        }
        if (s.mode == Mode.LATCHED) {
            s.mode = slot == AbilitySlot.RMB ? Mode.PULL : Mode.REEL;
            s.age = 0;
            if(s.mode==Mode.REEL && p.getVehicle() instanceof HellCycleEntity bike)bike.grappleBoost(s.tip);
            return;
        }
        if (s.mode == Mode.SWING && slot == AbilitySlot.LMB && s.age >= 4) { s.queued = true; return; }
        if (s.mode != Mode.IDLE || s.cooldown > 0) return;
        s.age = 0;
        s.tip = hand(p);
        s.direction = p.getLookAngle();
        s.target = null;s.anchor=null;
        if (slot == AbilitySlot.LMB) {
            s.mode = Mode.READY;
        } else {
            s.mode = Mode.CAST;
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.8f, 0.8f + s.combo * 0.15f);
        p.level().playSound(null, p.blockPosition(), ModSounds.GHOST_CHAIN_WHIP.get(), SoundSource.PLAYERS, (0.8f) * 1.0f, 1.0f);
        sync(p, s);
    }
    public static void release(ServerPlayer p,AbilitySlot slot) {
        if(slot!=AbilitySlot.LMB)return;
        State s=STATES.get(p.getUUID());if(s==null)return;
        s.held=false;
        // Past full charge the throw is committed; letting go no longer cancels it.
        if(s.mode==Mode.CHARGE && s.age>=GhostComboMotion.CHARGE_FULL)return;
        if(s.mode==Mode.READY || s.mode==Mode.CHARGE)startSwing(p,s);
    }
    public static void cancel(ServerPlayer p) {
        State s=STATES.get(p.getUUID());if(s!=null){end(s);sync(p,s);}
    }
    private static void startSwing(ServerPlayer p,State s) {
        s.mode=Mode.SWING;s.age=0;s.cooldown=0;
        s.combo=s.comboTicks>0?(s.combo+1)%3:0;
        s.comboTicks=40;s.struck.clear();s.queued=false;
        sync(p,s);
    }
    public static Vec3 hand(ServerPlayer p) {
        Vec3 right = new Vec3(-Math.cos(Math.toRadians(p.getYRot())), 0, -Math.sin(Math.toRadians(p.getYRot())));
        return p.position().add(0, 1.15, 0).add(right.scale(0.38)).add(p.getLookAngle().scale(0.25));
    }
    /** A chained player mashing F: enough presses, fast enough, snaps the chain. */
    public static void struggle(ServerPlayer victim,int presses) {
        for(var entry:STATES.entrySet()) {
            State s=entry.getValue();
            if(!victim.getUUID().equals(s.target) || (s.mode!=Mode.LATCHED && s.mode!=Mode.PULL))continue;
            s.escape+=ESCAPE_PER_PRESS*Math.min(4,presses);
            if(s.escape>=1) {
                var rider=victim.server.getPlayerList().getPlayer(entry.getKey());
                victim.level().playSound(null,victim.blockPosition(),SoundEvents.CHAIN_BREAK,SoundSource.PLAYERS,1.2f,.7f);
                victim.level().playSound(null, victim.blockPosition(), ModSounds.FX_IMPACT_METAL.get(), SoundSource.PLAYERS, (1.2f) * 0.7f, 1.1f);
                victim.level().playSound(null,victim.blockPosition(),SoundEvents.ITEM_BREAK,SoundSource.PLAYERS,1f,.6f);
                end(s);
                if(rider!=null)sync(rider,s);
            }
        }
    }
    static void end(State s) { s.escape=0; s.mode = (s.mode==Mode.PULL || s.mode==Mode.REEL || s.mode==Mode.CAST || s.mode==Mode.LATCHED)?Mode.RECOVER:Mode.IDLE; s.age = 0; s.target = null;s.anchor=null; s.cooldown = 8; s.queued = false; s.held=false; s.slam.reset(); }
    private static boolean clear(ServerPlayer p, Vec3 from, Vec3 to) {
        return p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        State s = STATES.get(p.getUUID());
        if (s == null && GhostRiderCharacter.ID.equals(AbilityManager.getCharacterId(p.getUUID()))) {
            s = new State(); STATES.put(p.getUUID(), s);
        }
        if (s == null) return;
        if (!p.isAlive() || p.isSpectator() || !GhostRiderCharacter.ID.equals(AbilityManager.getCharacterId(p.getUUID()))) {
            end(s); sync(p, s); STATES.remove(p.getUUID()); return;
        }
        if (s.cooldown > 0) s.cooldown--;
        if (s.comboTicks > 0) s.comboTicks--;
        if (++s.idleTicks > 60 && p.tickCount % 10 == 0) s.heat = Math.max(0, s.heat - 1);
        Mode previousMode = s.mode;
        s.age++;
        if (com.FIRNI.superheromod.core.cinematic.CinematicDirector.isBusy(p.getUUID()) || HellfireBreathController.active(p.getUUID())) end(s);
        if(s.held && p.level().getGameTime()-s.renewed>20) {end(s);sync(p,s);}
        bindings(p,s);
        if(p.getVehicle() instanceof HellCycleEntity cycle)
            cycle.tether(s.mode==Mode.LATCHED && s.anchor!=null?s.anchor:null,s.leash);
        switch (s.mode) {
            case SLAM_THROW, SLAM_LIFT, SLAM_DOWN -> HellSlam.tick(p,s);
            case READY -> {if(s.age>=6){s.mode=Mode.CHARGE;s.age=0;}}
            case CHARGE -> {
                p.yBodyRot=p.getYRot();
                s.heat=Math.max(s.heat,Math.min(10,s.age/4));s.idleTicks=0;
                if(s.age==GhostComboMotion.CHARGE_FULL)p.level().playSound(null,p.blockPosition(),SoundEvents.CHAIN_BREAK,SoundSource.PLAYERS,.9f,.6f);
                if(s.age==GhostComboMotion.CHARGE_FULL)p.level().playSound(null, p.blockPosition(), ModSounds.FX_IMPACT_METAL.get(), SoundSource.PLAYERS, (.9f) * 0.7f, 1.1f);
                if(s.age==GhostComboMotion.CHARGE_FULL+GhostComboMotion.CHARGE_COIL)
                    p.level().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,1f,.5f);
                    p.level().playSound(null, p.blockPosition(), ModSounds.GHOST_CHAIN_WHIP.get(), SoundSource.PLAYERS, (1f) * 1.0f, 0.95f);
                if(s.age>=GhostComboMotion.chargeRelease()) {
                    s.mode=Mode.FLY;s.age=0;s.held=false;s.struck.clear();
                    s.tip=p.getEyePosition().add(p.getLookAngle());s.direction=p.getLookAngle();
                }
                if(s.age%12==0)p.level().playSound(null,p.blockPosition(),SoundEvents.FIRE_AMBIENT,SoundSource.PLAYERS,.4f,.85f);
            }
            case SWING -> {
                p.yBodyRot = p.getYRot();
                s.tip = GhostComboMotion.tip(p.position(),p.getYRot(),s.combo,s.age);
                int impact=GhostComboMotion.impactTick(s.combo);
                // Heavy whoosh as the raised chain starts coming down.
                if(s.age==impact-3)p.level().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.9f,.55f+s.combo*.08f);
                if(s.age==impact-3)p.level().playSound(null, p.blockPosition(), ModSounds.GHOST_CHAIN_WHIP.get(), SoundSource.PLAYERS, (.9f) * 1.0f, 0.95f);
                if (s.age >= impact-1 && s.age <= impact+2) {
                    boolean landed = false;
                    double reach=(p.getVehicle() instanceof HellCycleEntity?8:4.5)*GhostComboMotion.REACH;
                    for (LivingEntity target : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(reach), t -> t != p && t.isAlive() && !t.isSpectator())) {
                        Vec3 delta = target.getBoundingBox().getCenter().subtract(p.getEyePosition());
                        if (s.struck.contains(target.getUUID()) || !touches(p,s,target)
                                || !clear(p,p.getEyePosition(),target.getBoundingBox().getCenter())) continue;
                        if (target.hurt(p.damageSources().playerAttack(p), GhostRideMath.chainDamage(s.combo,s.heat))) {
                            s.struck.add(target.getUUID());
                            landed = true;
                            target.knockback(0.3 + s.combo * 0.12, -delta.x, -delta.z);
                            if (s.heat >= 4) target.setSecondsOnFire(2);
                        }
                    }
                    if (landed) {
                        s.heat = Math.min(10, s.heat + 1); s.idleTicks = 0;
                        p.level().playSound(null,p.blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.PLAYERS,.35f,.55f+s.combo*.1f);
                        p.level().playSound(null, p.blockPosition(), ModSounds.FX_IMPACT_HEAVY.get(), SoundSource.PLAYERS, (.35f) * 1.5f, 1.0f);
                        p.level().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.PLAYERS,1f,.6f);
                    }
                }
                if(s.combo==2 && s.age==impact+2) {
                    Vec3 ahead=p.position().add(new Vec3(0,0,2.7*GhostComboMotion.REACH).yRot((float)Math.toRadians(-p.getYRot())));
                    var hit=p.level().clip(new ClipContext(ahead.add(0,2,0),ahead.add(0,-2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                    if(hit.getType()==HitResult.Type.BLOCK && hit.getDirection()==net.minecraft.core.Direction.UP) {
                        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),
                            new com.FIRNI.superheromod.network.packet.GhostSlamPacket(hit.getLocation()));
                        p.level().playSound(null,net.minecraft.core.BlockPos.containing(hit.getLocation()),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.45f,1.5f);
                    }
                }
                if (s.age >= GhostComboMotion.duration(s.combo)) {
                    boolean queued=s.queued,held=s.held;
                    end(s); s.cooldown=2;
                    if(held){s.mode=Mode.CHARGE;s.age=0;s.held=true;s.cooldown=0;}
                    else if(queued)startSwing(p,s);
                }
            }
            case RECOVER -> {
                s.tip=s.tip.lerp(hand(p),.23).add(0,-.025,0);
                if(s.age>=12){s.mode=Mode.IDLE;s.age=0;}
            }
            case FLY -> {
                Vec3 next=s.tip.add(s.direction.scale(1.5));
                var wall=p.level().clip(new ClipContext(s.tip,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                Vec3 stop=wall.getLocation();
                for(LivingEntity t:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(s.tip,stop).inflate(1.4),t->t!=p && t.isAlive() && !t.isSpectator())) {
                    if(!s.struck.contains(t.getUUID()) && (t.getBoundingBox().inflate(1.3).contains(s.tip) || t.getBoundingBox().inflate(1.3).clip(s.tip,stop).isPresent())) {
                        if(t.hurt(p.damageSources().playerAttack(p),7)){s.struck.add(t.getUUID());t.setSecondsOnFire(4);t.knockback(.7,-s.direction.x,-s.direction.z);}
                    }
                }
                s.tip=stop;
                if(wall.getType()!=HitResult.Type.MISS) {
                    ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new com.FIRNI.superheromod.network.packet.GhostSlamPacket(groundBelow(p,stop)));end(s);
                } else if(s.age>=32)end(s);
            }
            case CAST -> {
                // Wind-up: the chain stays in the hand until the arm comes through.
                if(s.age<CAST_RELEASE){s.tip=hand(p);s.direction=p.getLookAngle();break;}
                if(s.age==CAST_RELEASE)p.level().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,.8f,.8f);
                if(s.age==CAST_RELEASE)p.level().playSound(null, p.blockPosition(), ModSounds.GHOST_CHAIN_WHIP.get(), SoundSource.PLAYERS, (.8f) * 1.0f, 0.95f);
                var hit = RaycastSystem.cast(p.level(), p, s.tip, s.direction, 1.7, 0.25f, false,
                        t -> t instanceof LivingEntity && t != p && t.isAlive() && !t.isSpectator());
                if (hit.didHitEntity()) {
                    var target = hit.getEntityHits().get(0).getEntity();
                    s.target = target.getUUID(); s.tip = target.getBoundingBox().getCenter(); s.mode = Mode.LATCHED; s.age = 0;s.leash=Math.max(5*GhostComboMotion.LENGTH,Math.min(12*GhostComboMotion.LENGTH,p.distanceTo(target)));s.escape=0;
                } else if(hit.didHitBlock() && p.getVehicle() instanceof HellCycleEntity) {
                    s.anchor=hit.getHitPosition();s.tip=s.anchor;s.mode=Mode.LATCHED;s.age=0;
                    // Fixed chain length: the bike can swing around the anchor at this radius.
                    s.leash=Math.max(4,Math.min(24,p.getVehicle().position().distanceTo(s.anchor)));
                } else if (hit.didHitBlock() || s.age >= CAST_RELEASE+(p.getVehicle() instanceof HellCycleEntity?36:16)+CAST_EXTRA_STEPS) end(s);
                else s.tip = s.tip.add(s.direction.scale(1.7));
            }
            case LATCHED, PULL, REEL -> {
                if(s.anchor!=null) {
                    if(!(p.getVehicle() instanceof HellCycleEntity bike) || p.position().distanceTo(s.anchor)>80 || s.age>200){end(s);break;}
                    s.tip=s.anchor;
                    if(s.mode==Mode.REEL) {
                        if(bike.position().distanceTo(s.anchor)<2.5 || s.age>35){end(s);break;}
                        bike.grappleBoost(s.anchor);
                    } else if(s.mode==Mode.PULL)end(s);
                    break;
                }
                var entity = s.target==null?null:p.serverLevel().getEntity(s.target);
                if (!(entity instanceof LivingEntity t) || !t.isAlive() || t.isSpectator() || p.distanceTo(t)>80 || s.age>1200) {end(s);break;}
                s.tip=t.getBoundingBox().getCenter();
                HellCycleEntity bike=p.getVehicle() instanceof HellCycleEntity cycle?cycle:null;
                double vehicleSpeed=bike==null?0:bike.speed();
                if(s.mode==Mode.LATCHED) {
                    Vec3 delta=p.position().subtract(t.position());
                    if(delta.length()>s.leash) {
                        t.setDeltaMovement(delta.normalize().scale(Math.min(3,(delta.length()-s.leash)*.3+vehicleSpeed)));
                        t.hurtMarked=true;t.fallDistance=0;
                        if(vehicleSpeed>.8 && s.age%10==0)t.hurt(p.damageSources().playerAttack(p),(float)Math.min(5,vehicleSpeed*1.5));
                    }
                } else if(s.mode==Mode.REEL) {
                    Vec3 delta=t.position().subtract(bike==null?p.position():bike.position());
                    if(delta.length()<2.2) {
                        if(bike!=null){t.hurt(p.damageSources().playerAttack(p),6);end(s);break;}
                        // Arrive fist-first: the punch lands on the next tick, chain already let go.
                        s.mode=Mode.PUNCH;s.age=0;s.tip=t.getBoundingBox().getCenter();
                        p.setDeltaMovement(p.getDeltaMovement().scale(.35));p.hurtMarked=true;
                        break;
                    }
                    if(s.age>40){end(s);break;}
                    if(bike!=null)bike.grappleBoost(t.position());
                    else {p.setDeltaMovement(delta.normalize().scale(1.3));p.hurtMarked=true;p.fallDistance=0;}
                } else {
                    Vec3 destination=bike==null?p.position():GhostRideMath.pullDestination(bike.position(),bike.getYRot());
                    Vec3 delta=destination.subtract(t.position());
                    if(delta.length()<1.1){t.setDeltaMovement(bike==null?Vec3.ZERO:bike.getDeltaMovement());t.hurtMarked=true;end(s);break;}
                    if(s.age>45){end(s);break;}
                    // Hand-over-hand: each haul yanks the target, between hauls it drifts.
                    double haul=bike==null?GhostComboMotion.pullStrength(s.age):1;
                    t.setDeltaMovement(delta.normalize().scale(Math.min(3.5,delta.length()*.3+vehicleSpeed)*haul));
                    t.hurtMarked=true;t.fallDistance=0;
                }
            }
            case PUNCH -> {
                p.yBodyRot=p.getYRot();
                var entity=s.target==null?null:p.serverLevel().getEntity(s.target);
                if(s.age==PUNCH_CONTACT && entity instanceof LivingEntity t && t.isAlive()) {
                    Vec3 push=t.getBoundingBox().getCenter().subtract(p.getEyePosition());
                    push=new Vec3(push.x,0,push.z);
                    if(push.lengthSqr()<1e-4)push=p.getLookAngle().multiply(1,0,1);
                    push=push.normalize();
                    Vec3 contact=t.getBoundingBox().getCenter().subtract(push.scale(t.getBbWidth()*.5));
                    s.tip=contact;
                    if(t.hurt(p.damageSources().playerAttack(p),GhostRideMath.punchDamage(s.heat))) {
                        t.setDeltaMovement(push.scale(2.6).add(0,.62,0));t.hurtMarked=true;
                        if(s.heat>=4)t.setSecondsOnFire(3);
                        s.heat=Math.min(10,s.heat+1);s.idleTicks=0;
                    }
                    p.setDeltaMovement(push.scale(-.12).add(0,.1,0));p.hurtMarked=true;
                    var at=net.minecraft.core.BlockPos.containing(contact);
                    p.level().playSound(null,at,SoundEvents.PLAYER_ATTACK_KNOCKBACK,SoundSource.PLAYERS,1.2f,.6f);
                    p.level().playSound(null,at,SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.45f,1.7f);
                }
                if(s.age>=PUNCH_DURATION){s.mode=Mode.IDLE;s.age=0;s.target=null;s.cooldown=6;}
            }
            default -> {}
        }
        if (s.mode != Mode.IDLE || previousMode != Mode.IDLE || p.tickCount % 10 == 0) sync(p, s);
    }
    private static Vec3 groundBelow(ServerPlayer p,Vec3 hit) {
        var floor=p.level().clip(new ClipContext(hit.add(0,.1,0),hit.add(0,-2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        return floor.getType()==HitResult.Type.BLOCK?floor.getLocation():hit;
    }
    private static boolean touches(ServerPlayer p, State s, LivingEntity target) {
        AABB bounds=target.getBoundingBox().inflate(p.getVehicle() instanceof HellCycleEntity?.65:.25);
        // Sweep between ticks so the quick lash cannot skip a narrow target.
        for(int sample=0;sample<=4;sample++) {
            float time=s.age-1+sample*.25f;
            var pose=GhostComboMotion.pose(s.combo,time);
            for(int side : s.combo==2?new int[]{-1,1}:new int[]{pose.handSide()}) {
            Vec3 from=GhostComboMotion.modelToWorld(p.position(),p.getYRot(),GhostComboMotion.arm(pose,side).hand());
            Vec3 tip=GhostComboMotion.tip(p.position(),p.getYRot(),s.combo,time,side), last=from;
            if(p.getVehicle() instanceof HellCycleEntity)tip=from.add(tip.subtract(from).scale(1.65));
            for(int n=1;n<=8;n++) {
                Vec3 next=GhostComboMotion.point(from,tip,s.combo,time,n/8.0);
                if(bounds.contains(last) || bounds.clip(last,next).isPresent())return true;
                last=next;
            }
            }
        }
        return false;
    }
    static void sync(ServerPlayer p, State s) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new GhostChainPacket(p.getId(), s.mode.ordinal(), s.age, s.combo, s.heat, s.tip, targetId(p,s)));
    }
    private static int targetId(ServerPlayer p,State s) {
        if(s.target==null)return -1;
        var e=p.serverLevel().getEntity(s.target);
        return e==null?-1:e.getId();
    }
    /** Tell a chained player they are bound (with the break-free meter), and when they are released. */
    private static void bindings(ServerPlayer p,State s) {
        if(s.escape>0)s.escape=Math.max(0,s.escape-ESCAPE_DECAY);
        boolean holding=(s.mode==Mode.LATCHED || s.mode==Mode.PULL) && s.target!=null;
        var victim=holding?p.server.getPlayerList().getPlayer(s.target):null;
        if(s.boundPlayer!=null && (victim==null || !victim.getUUID().equals(s.boundPlayer))) {
            var released=p.server.getPlayerList().getPlayer(s.boundPlayer);
            if(released!=null)ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(()->released),new com.FIRNI.superheromod.network.packet.GhostBindPacket(false,0));
            s.boundPlayer=null;
        }
        if(victim!=null) {
            s.boundPlayer=victim.getUUID();
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(()->victim),new com.FIRNI.superheromod.network.packet.GhostBindPacket(true,s.escape));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { STATES.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { STATES.clear(); }
}
