package com.FIRNI.superheromod.heroes.ghostrider;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.core.cinematic.CinematicDirector;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.HellfireBreathPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.*;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

@Mod.EventBusSubscriber(modid=SuperheroMod.MODID)
public final class HellfireBreathController {
    private static final class State {
        long renewed;
        int age;
        Vec3 anchor;
        final Map<UUID,Integer> exposure=new HashMap<>();
    }
    private static final Map<UUID,State> STATES=new HashMap<>();
    /** Smallest damage a second of hellfire deals, however fresh the exposure. */
    private static final float MIN_BREATH_DAMAGE=1.5f;
    public static boolean active(UUID id) { return STATES.containsKey(id); }
    public static void start(ServerPlayer p) {
        if(!p.isAlive() || p.isSpectator() || p.isUnderWater() || p.isPassenger() || !p.onGround() || CinematicDirector.isBusy(p.getUUID()) || PenanceStare.busy(p.getUUID())
                || !GhostRiderCharacter.ID.equals(AbilityManager.getCharacterId(p.getUUID())))return;
        boolean fresh=!STATES.containsKey(p.getUUID());
        State s=STATES.computeIfAbsent(p.getUUID(),id->new State());
        if(fresh) { s.anchor=p.position(); send(p,true,0,0); }
        s.renewed=p.level().getGameTime();
    }
    public static void stop(ServerPlayer p) {
        if(STATES.remove(p.getUUID())!=null)send(p,false,0,0);
    }
    public static Vec3 origin(LivingEntity p) { return p.getEyePosition().add(0,-.16,0).add(p.getLookAngle().scale(.32)); }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !(e.player instanceof ServerPlayer p))return;
        State s=STATES.get(p.getUUID());if(s==null)return;
        if(!p.isAlive() || p.isSpectator() || p.isUnderWater() || p.isPassenger() || p.level().getGameTime()-s.renewed>20
                || CinematicDirector.isBusy(p.getUUID()) || !GhostRiderCharacter.ID.equals(AbilityManager.getCharacterId(p.getUUID()))) {stop(p);return;}
        p.setDeltaMovement(Vec3.ZERO);
        if(p.position().distanceToSqr(s.anchor)>.0004)
            p.connection.teleport(s.anchor.x,s.anchor.y,s.anchor.z,p.getYRot(),p.getXRot());
        s.age++;
        Vec3 origin=origin(p), dir=p.getLookAngle();
        var hit=p.level().clip(new ClipContext(origin,origin.add(dir.scale(HellfireBreathMath.RANGE)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        double length=hit.getLocation().distanceTo(origin);
        // Short expansion, then sustained output. Server reach and rendered reach use the same envelope.
        length=Math.min(length,Math.max(0,s.age-3)*2.25);
        if(s.age%2==0)send(p,true,s.age,(float)length);
        if(s.age==4 || s.age%16==0)p.level().playSound(null,p.blockPosition(),SoundEvents.FIRECHARGE_USE,SoundSource.PLAYERS,.75f,.65f);
        if(s.age%5!=0)return;
        Set<UUID> touching=new HashSet<>();
        for(LivingEntity target:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(origin,origin.add(dir.scale(length))).inflate(HellfireBreathMath.radius(length)),
                t->t!=p && t.isAlive() && !t.isSpectator() && !t.isAlliedTo(p))) {
            Vec3 center=target.getBoundingBox().getCenter();
            if(!HellfireBreathMath.contains(origin,dir,center,length,target.getBbWidth()*.45))continue;
            if(p.level().clip(new ClipContext(origin,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)continue;
            touching.add(target.getUUID());
            int exposure=s.exposure.merge(target.getUUID(),5,(a,b)->Math.min(1200,a+b));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,12,1,false,false,true));
            // Once a second, a hit that always lands: the burn's own invulnerability window used to
            // swallow the breath's smaller early ticks entirely.
            if(exposure%20==0) {
                var type=p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.IN_FIRE);
                boolean burning=target.isOnFire();
                target.invulnerableTime=0;
                target.hurt(new DamageSource(type,p),Math.max(MIN_BREATH_DAMAGE,HellfireBreathMath.damage(exposure,burning)));
                if(!target.fireImmune())target.setSecondsOnFire(3);
            }
        }
        s.exposure.keySet().retainAll(touching);
    }
    private static void send(ServerPlayer p,boolean active,int age,float length) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new HellfireBreathPacket(p.getId(),active,age,length));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {if(e.getEntity() instanceof ServerPlayer p)stop(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {if(e.getEntity() instanceof ServerPlayer p)stop(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {STATES.clear();}
}
