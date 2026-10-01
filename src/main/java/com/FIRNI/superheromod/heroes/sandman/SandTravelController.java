package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.SandTravelPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.sounds.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Server-authoritative sink / transfer / reform. No persistent player flags are changed. */
@Mod.EventBusSubscriber(modid=SuperheroMod.MODID)
public final class SandTravelController {
    public static final int TRANSFER_TICK=8, END_TICK=18;
    private record Journey(ServerPlayer player, ServerLevel level, Vec3 destination, long start) {}
    private static final Map<UUID,Journey> active=new HashMap<>();
    public static boolean isTravelling(UUID id) { return active.containsKey(id); }
    public static void start(ServerPlayer player, Vec3 destination) {
        if (isTravelling(player.getUUID())) return;
        ServerLevel level=player.serverLevel();
        Journey journey=new Journey(player,level,destination,level.getGameTime());
        active.put(player.getUUID(),journey);
        sync(journey,true);
        burst(player,SoundEvents.SAND_BREAK);
    }
    private static void sync(Journey j, boolean running) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(j::player),
                new SandTravelPacket(j.player.getUUID(),j.start,running));
    }
    private static void burst(ServerPlayer p,SoundEvent sound) {
        p.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.SAND.defaultBlockState()),
                p.getX(),p.getY()+.15,p.getZ(),14,.4,.12,.4,.04);
        p.serverLevel().playSound(null,p.blockPosition(),sound,SoundSource.PLAYERS,1.1f,.75f);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var iterator=active.values().iterator();
        while(iterator.hasNext()) {
            Journey j=iterator.next(); ServerPlayer p=j.player;
            long age=j.level.getGameTime()-j.start;
            if(p.isRemoved() || !p.isAlive() || p.serverLevel()!=j.level
                    || !"sandman".equals(AbilityManager.getCharacterId(p.getUUID())) || age>=END_TICK) {
                sync(j,false); iterator.remove(); continue;
            }
            if(age==TRANSFER_TICK) {
                var box=p.getBoundingBox().move(j.destination.subtract(p.position()));
                if(!j.level.noCollision(p,box)) { sync(j,false); iterator.remove(); continue; }
                p.teleportTo(j.destination.x,j.destination.y,j.destination.z);
                p.setDeltaMovement(Vec3.ZERO); p.fallDistance=0;
                sync(j,true);
                burst(p,SoundEvents.SAND_PLACE);
            }
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { active.clear(); }
    @SubscribeEvent public static void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        Journey j=active.get(event.getTarget().getUUID());
        if(j!=null && event.getEntity() instanceof ServerPlayer viewer)
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(()->viewer),
                    new SandTravelPacket(j.player.getUUID(),j.start,true));
    }
}
