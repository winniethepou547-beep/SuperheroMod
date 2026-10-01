package com.FIRNI.superheromod.heroes.ghostrider;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.GhostPenancePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/**
 * Penance Stare (X). The Ghost Rider seizes a nearby victim by the throat, lifts them face to
 * face and breathes hellfire into them while his stare burns through to their soul: their body
 * flickers away to a glowing skeleton, then the soul bursts and the victim is thrown down.
 * Timeline in ticks is shared with the client, which films it as a short cinematic.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class PenanceStare {
    /** Film timeline in ticks: seize, lift, the soul journey, the burst at CLIMAX, the throw. */
    public static final int GRAB=10, LIFT=26, CLIMAX=376, CLIMAX_END=380, TOTAL=410;
    public static final double RANGE=4.5, HOLD_DISTANCE=.8, LIFT_HEIGHT=.15;
    private static final int COOLDOWN=200;
    private static final class Session {
        UUID victim; int age; Vec3 victimStart, anchor; float yaw; boolean aiWasOff;
    }
    private static final Map<UUID,Session> ACTIVE=new HashMap<>();
    private static final Map<UUID,Long> READY_AT=new HashMap<>();

    /** True for the Ghost Rider performing it and for the victim. */
    public static boolean busy(UUID id) {
        if(ACTIVE.containsKey(id))return true;
        for(Session s:ACTIVE.values())if(id.equals(s.victim))return true;
        return false;
    }
    /** Ability key: grab whoever is in front, within reach. */
    public static void tryStart(ServerPlayer p) {
        if(!GhostRiderCharacter.ID.equals(AbilityManager.getCharacterId(p.getUUID())) || busy(p.getUUID())
                || p.getVehicle()!=null || p.level().getGameTime()<READY_AT.getOrDefault(p.getUUID(),0L))return;
        var hit=RaycastSystem.cast(p.level(),p,p.getEyePosition(),p.getLookAngle(),RANGE,.8f,false,
                e->e instanceof LivingEntity && e!=p && e.isAlive() && !e.isSpectator());
        LivingEntity target=hit.didHitEntity()?(LivingEntity)hit.getEntityHits().get(0).getEntity():null;
        if(target==null){p.level().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_WEAK,SoundSource.PLAYERS,.8f,.6f);return;}
        start(p,target);
    }
    /** Starts on a given victim (also used by the preview command). */
    public static boolean start(ServerPlayer p,LivingEntity victim) {
        if(busy(p.getUUID()) || busy(victim.getUUID()) || victim==p || victim.level()!=p.level())return false;
        GhostChainController.cancel(p);
        Session s=new Session();
        s.victim=victim.getUUID();s.victimStart=victim.position();s.anchor=p.position();
        Vec3 toward=victim.position().subtract(p.position());
        s.yaw=toward.horizontalDistanceSqr()<1e-4?p.getYRot():(float)Math.toDegrees(Math.atan2(-toward.x,toward.z));
        if(victim instanceof Mob mob){s.aiWasOff=mob.isNoAi();mob.setNoAi(true);}
        ACTIVE.put(p.getUUID(),s);
        p.level().playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.PLAYERS,1f,.5f);
        p.level().playSound(null,p.blockPosition(),SoundEvents.CHAIN_HIT,SoundSource.PLAYERS,.8f,.6f);
        sync(p,victim,s,true);
        return true;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.player instanceof ServerPlayer p))return;
        Session s=ACTIVE.get(p.getUUID());
        if(s==null)return;
        var level=p.serverLevel();
        LivingEntity victim=level.getEntity(s.victim) instanceof LivingEntity living?living:null;
        if(victim==null || !victim.isAlive() || !p.isAlive() || victim.distanceTo(p)>8){finish(p,victim,s);return;}
        s.age++;
        Vec3 forward=Vec3.directionFromRotation(0,s.yaw);
        // The Ghost Rider stands his ground, facing the victim.
        p.setDeltaMovement(0,Math.min(0,p.getDeltaMovement().y),0);p.hurtMarked=true;
        p.setYRot(s.yaw);p.yBodyRot=s.yaw;p.yHeadRot=s.yaw;
        if(s.age<CLIMAX_END) hold(p,victim,s,forward);
        // The film carries its own sound for the two participants; the world hears the fire.
        if(s.age>=LIFT && s.age<CLIMAX && (s.age-LIFT)%40==0) {
            victim.invulnerableTime=0;
            victim.hurt(p.damageSources().playerAttack(p),1.5f);
            victim.setSecondsOnFire(3);
            level.playSound(null,victim.blockPosition(),SoundEvents.FIRE_AMBIENT,SoundSource.PLAYERS,1f,.6f);
        }
        if(s.age==CLIMAX) {
            victim.invulnerableTime=0;
            victim.hurt(p.damageSources().playerAttack(p),8);
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,60,0));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,100,2));
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,140,1));
            level.playSound(null,victim.blockPosition(),SoundEvents.GHAST_SCREAM,SoundSource.PLAYERS,1.4f,.6f);
            level.playSound(null,victim.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.7f,.7f);
        }
        if(s.age==CLIMAX_END) {
            // The soul returns: the body is driven into the ground in front of him, the ground splits.
            victim.setDeltaMovement(forward.scale(.5).add(0,-1.4,0));victim.hurtMarked=true;
            victim.setSecondsOnFire(5);
            level.playSound(null,victim.blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.PLAYERS,1f,.5f);
            level.playSound(null,victim.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,.9f,.6f);
            Vec3 ground=groundBelow(p,victim.position());
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new com.FIRNI.superheromod.network.packet.GhostSlamPacket(ground));
            ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new com.FIRNI.superheromod.network.packet.ShockwavePacket(ground,4,14,.7f));
            if(victim instanceof Mob mob)mob.setNoAi(s.aiWasOff);
        }
        sync(p,victim,s,true);
        if(s.age>=TOTAL)finish(p,victim,s);
    }
    /**
     * Where the victim is at film time t: pulled in, lifted by the throat and shaking as the
     * fire goes in. Shared by server and clients so both draw the identical motion.
     */
    public static Vec3 victimAt(Vec3 anchor,float yaw,Vec3 start,float t) {
        Vec3 forward=Vec3.directionFromRotation(0,yaw);
        Vec3 point=anchor.add(forward.scale(HOLD_DISTANCE));
        float grab=ease(t/GRAB), lift=ease((t-GRAB)/(LIFT-GRAB));
        Vec3 at=start.lerp(point,grab).add(0,LIFT_HEIGHT*lift,0);
        if(t>=LIFT) {
            double shake=(t>=CLIMAX?.045:.016)*Math.min(1,(t-LIFT)/6);
            at=at.add(Math.sin(t*2.7)*shake+Math.sin(t*6.1)*shake*.5,Math.sin(t*3.9+1)*shake,Math.sin(t*3.3+2)*shake);
        }
        return at;
    }
    /** Holds the gameplay body on the choreographed path. */
    private static void hold(ServerPlayer p,LivingEntity victim,Session s,Vec3 forward) {
        Vec3 at=victimAt(s.anchor,s.yaw,s.victimStart,s.age);
        float facing=s.yaw+180;
        if(victim instanceof ServerPlayer player)player.connection.teleport(at.x,at.y,at.z,facing,-8);
        else {victim.teleportTo(at.x,at.y,at.z);victim.setYRot(facing);victim.yBodyRot=facing;victim.yHeadRot=facing;}
        victim.setDeltaMovement(Vec3.ZERO);victim.hurtMarked=true;victim.fallDistance=0;
    }
    private static Vec3 groundBelow(ServerPlayer p,Vec3 at) {
        var hit=p.level().clip(new net.minecraft.world.level.ClipContext(at.add(0,.5,0),at.add(0,-4,0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        return hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getLocation():at;
    }
    private static float ease(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static void finish(ServerPlayer p,LivingEntity victim,Session s) {
        ACTIVE.remove(p.getUUID());
        READY_AT.put(p.getUUID(),p.level().getGameTime()+COOLDOWN);
        if(victim instanceof Mob mob && s.age<CLIMAX_END)mob.setNoAi(s.aiWasOff);
        sync(p,victim,s,false);
    }
    private static void sync(ServerPlayer p,LivingEntity victim,Session s,boolean active) {
        var packet=new GhostPenancePacket(p.getId(),victim==null?-1:victim.getId(),s.age,active,s.yaw,
                s.anchor==null?p.position():s.anchor,s.victimStart==null?p.position():s.victimStart);
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),packet);
        if(victim instanceof ServerPlayer other && !other.equals(p))
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(()->other),packet);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Session s=ACTIVE.remove(e.getEntity().getUUID());
        if(s!=null && e.getEntity() instanceof ServerPlayer p && p.serverLevel().getEntity(s.victim) instanceof Mob mob)mob.setNoAi(s.aiWasOff);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e){ACTIVE.clear();READY_AT.clear();}
}
