package com.FIRNI.superheromod.heroes.sandman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.ColossusActionPacket;
import com.FIRNI.superheromod.network.packet.ShockwavePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;

/** Continuous ground cut follows the rendered blade, with one hit per victim per swing. */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class ColossusSwordController {
    public static final int IMPACT = 18, DURATION = 44;
    private static final class Strike {
        final ServerLevel level; final long started;
        final Set<UUID> hit = new HashSet<>();
        final Set<BlockPos> excavated = new HashSet<>();
        Vec3 previous;
        Strike(ServerLevel level) { this.level=level; started=level.getGameTime(); }
    }
    private static final Map<UUID,Strike> strikes=new HashMap<>();
    public static boolean isActive(UUID id) { return strikes.containsKey(id); }
    public static ColossusPose.Action action(UUID id) {
        var s=strikes.get(id);
        return s==null?null:new ColossusPose.Action(ColossusActionPacket.SWORD_STAB,
                (int)(s.level.getGameTime()-s.started),DURATION);
    }
    public static void start(ServerPlayer player) {
        if(isActive(player.getUUID()) || ColossusMaceController.isSwinging(player.getUUID())
                || ColossusRockController.isThrowing(player.getUUID())) return;
        strikes.put(player.getUUID(),new Strike(player.serverLevel()));
        ModNetworking.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new ColossusActionPacket(player.getUUID(),ColossusActionPacket.SWORD_STAB,DURATION));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || strikes.isEmpty()) return;
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server==null) { strikes.clear(); return; }
        var it=strikes.entrySet().iterator();
        while(it.hasNext()) {
            var entry=it.next(); var s=entry.getValue();
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if(player==null || !player.isAlive() || player.level()!=s.level
                    || !SandColossusController.isColossus(entry.getKey())) { it.remove(); continue; }
            long age=s.level.getGameTime()-s.started;
            if(age>=18 && age<=30) cut(player,s);
            if(age>=DURATION) it.remove();
        }
    }
    private static void cut(ServerPlayer player,Strike s) {
        Vec3 tip=ColossusCrystal.swordTip(player);
        Vec3 contact=new Vec3(tip.x,player.getY(),tip.z);
        if(s.previous==null) {
            s.previous=contact;
            s.level.playSound(null,BlockPos.containing(contact),SoundEvents.GENERIC_EXPLODE,
                    SoundSource.PLAYERS,1.5f,.65f);
            ModNetworking.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                    contact.x,contact.y,contact.z,64,s.level.dimension())),new ShockwavePacket(contact,5,14,.35f));
        }
        int samples=Math.min(24,Math.max(1,(int)Math.ceil(s.previous.distanceTo(contact)/.45)));
        for(int i=1;i<=samples;i++) {
            Vec3 p=s.previous.lerp(contact,i/(double)samples);
            for(var target:s.level.getEntitiesOfClass(LivingEntity.class,new AABB(p,p).inflate(1.5,2,1.5),
                    e->e!=player && e.isAlive())) {
                if(s.hit.add(target.getUUID())) {
                    target.hurt(player.damageSources().playerAttack(player),14);
                    Vec3 push=target.position().subtract(player.position()).multiply(1,0,1).normalize();
                    target.setDeltaMovement(push.scale(.6).add(0,.45,0));target.hurtMarked=true;
                }
            }
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) for(int d=1;d<=2;d++) {
                BlockPos block=BlockPos.containing(p).offset(x,-d,z);
                double dx=block.getX()+.5-player.getX(),dz=block.getZ()+.5-player.getZ();
                if(dx*dx+dz*dz<16 || s.excavated.size()>=120 || s.excavated.contains(block))continue;
                var state=s.level.getBlockState(block);
                if(state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                        || state.getDestroySpeed(s.level,block)<0)continue;
                s.excavated.add(block);
                s.level.setBlock(block,Blocks.AIR.defaultBlockState(),3);
                if(d==2) {
                    BlockPos floor=block.below();
                    var floorState=s.level.getBlockState(floor);
                    if(!floorState.isAir() && !floorState.hasBlockEntity() && floorState.getFluidState().isEmpty()
                            && floorState.getDestroySpeed(s.level,floor)>=0)
                        s.level.setBlock(floor,Blocks.SAND.defaultBlockState(),3);
                }
            }
        }
        s.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,Blocks.SAND.defaultBlockState()),
                contact.x,contact.y+.2,contact.z,5,.6,.18,.6,.045);
        // Sparse secondary dust; the blade and trench remain readable.
        s.previous=contact;
    }
}
