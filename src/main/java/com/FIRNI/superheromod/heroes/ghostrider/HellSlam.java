package com.FIRNI.superheromod.heroes.ghostrider;

import com.FIRNI.superheromod.core.combat.raycast.RaycastSystem;
import com.FIRNI.superheromod.heroes.ghostrider.GhostChainController.Mode;
import com.FIRNI.superheromod.heroes.ghostrider.GhostChainController.State;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.GhostSlamPacket;
import com.FIRNI.superheromod.network.packet.ShockwavePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import java.util.UUID;

/**
 * Hell Slam (F). Both chains are thrown at a target, wrap it, hoist it overhead and drive it
 * straight down, smashing a shaft into the ground layer by layer. The shaft walls turn to
 * nether stone the deeper it goes, so the victim lands at the bottom of a pit into hell.
 */
final class HellSlam {
    static final double RANGE=18*GhostComboMotion.LENGTH, CHAIN_SPEED=3.2, LIFT_HEIGHT=5;
    static final int DEPTH=30, LAYERS_PER_TICK=3, COOLDOWN=80;
    static final double RADIUS=4;
    UUID victim;
    Vec3 start, lifted;
    int top, dug;
    double radius;

    void reset() { victim=null; start=lifted=null; dug=0; }

    static void start(ServerPlayer p,State s) {
        var hit=RaycastSystem.cast(p.level(),p,p.getEyePosition(),p.getLookAngle(),RANGE,.6f,false,
                e->e instanceof LivingEntity && e!=p && e.isAlive() && !e.isSpectator());
        s.slam.reset();
        if(hit.didHitEntity())s.slam.victim=hit.getEntityHits().get(0).getEntity().getUUID();
        s.mode=Mode.SLAM_THROW;s.age=0;s.target=s.slam.victim;
        s.tip=GhostChainController.hand(p);s.direction=p.getLookAngle();
        p.level().playSound(null,p.blockPosition(),SoundEvents.CHAIN_PLACE,SoundSource.PLAYERS,1f,.6f);
        GhostChainController.sync(p,s);
    }

    static void tick(ServerPlayer p,State s) {
        p.yBodyRot=p.getYRot();
        var level=p.serverLevel();
        LivingEntity target=s.slam.victim!=null && level.getEntity(s.slam.victim) instanceof LivingEntity living
                && living.isAlive() && !living.isSpectator()?living:null;
        if(s.slam.victim!=null && target==null){finish(s,20);return;}
        Vec3 hand=GhostChainController.hand(p);
        switch(s.mode) {
            case SLAM_THROW -> {
                if(s.age==GhostComboMotion.SLAM_WINDUP)
                    level.playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,1f,.55f);
                if(s.age<GhostComboMotion.SLAM_WINDUP){s.tip=hand;break;}
                double travel=(s.age-GhostComboMotion.SLAM_WINDUP)*CHAIN_SPEED;
                if(target==null) {
                    s.tip=hand.add(s.direction.scale(Math.min(travel,RANGE)));
                    if(s.age>GhostComboMotion.SLAM_RELEASE+8)finish(s,20);
                    break;
                }
                Vec3 center=target.getBoundingBox().getCenter();
                if(s.age>GhostComboMotion.SLAM_RELEASE+14 || center.distanceTo(hand)>RANGE*1.3){finish(s,20);break;}
                if(travel>=hand.distanceTo(center)) {
                    // Wrapped: the target is now held where it stood.
                    s.mode=Mode.SLAM_LIFT;s.age=0;s.tip=center;
                    s.slam.start=target.position();
                    level.playSound(null,target.blockPosition(),SoundEvents.CHAIN_HIT,SoundSource.PLAYERS,1.4f,.6f);
                    level.playSound(null,target.blockPosition(),SoundEvents.CHAIN_BREAK,SoundSource.PLAYERS,.8f,.5f);
                } else s.tip=hand.add(center.subtract(hand).normalize().scale(travel));
            }
            case SLAM_LIFT -> {
                // The victim answers the hands only once the wave has run down the chain.
                float raise=ease((s.age-GhostComboMotion.LIFT_WRAP-GhostComboMotion.CHAIN_LAG)/(float)(GhostComboMotion.LIFT_TOTAL-GhostComboMotion.LIFT_WRAP));
                Vec3 at=s.slam.start.add(0,LIFT_HEIGHT*raise,0);
                hold(target,at);s.tip=target.getBoundingBox().getCenter();
                if(s.age==GhostComboMotion.LIFT_WRAP)level.playSound(null,target.blockPosition(),SoundEvents.CHAIN_STEP,SoundSource.PLAYERS,1.2f,.6f);
                if(s.age>=GhostComboMotion.LIFT_TOTAL+GhostComboMotion.CHAIN_LAG) {
                    s.mode=Mode.SLAM_DOWN;s.age=0;s.slam.lifted=at;
                    s.slam.top=surface(level,s.slam.start);
                    // Never dig under the rider's own feet.
                    double clearance=p.position().multiply(1,0,1).distanceTo(s.slam.start.multiply(1,0,1))-1.6;
                    s.slam.radius=Math.max(1.5,Math.min(RADIUS,clearance));
                    level.playSound(null,p.blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,1.2f,.4f);
                }
            }
            case SLAM_DOWN -> {
                HellSlam slam=s.slam;
                double ground=slam.top+1;
                int fallEnd=GhostComboMotion.CHAIN_LAG+GhostComboMotion.DOWN_STRIKE;
                if(s.age<=fallEnd) {
                    // Accelerating fall from overhead to the ground, once the downstroke reaches the end.
                    double t=Math.max(0,s.age-GhostComboMotion.CHAIN_LAG)/(double)GhostComboMotion.DOWN_STRIKE;
                    hold(target,new Vec3(slam.start.x,slam.lifted.y-(slam.lifted.y-ground)*t*t,slam.start.z));
                    if(s.age==fallEnd) {
                        Vec3 impact=new Vec3(slam.start.x,ground,slam.start.z);
                        target.hurt(p.damageSources().playerAttack(p),4);
                        level.playSound(null,BlockPos.containing(impact),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.4f,.55f);
                        level.playSound(null,BlockPos.containing(impact),SoundEvents.ANVIL_LAND,SoundSource.PLAYERS,.8f,.5f);
                        shockwave(p,impact,(float)(slam.radius+3),.75f);
                        rim(level,p,slam);
                    }
                } else {
                    for(int i=0;i<LAYERS_PER_TICK && slam.dug<DEPTH;i++)dig(level,p,slam,slam.dug++);
                    hold(target,new Vec3(slam.start.x,ground-slam.dug,slam.start.z));
                    if(slam.dug%4==0)level.playSound(null,target.blockPosition(),SoundEvents.STONE_BREAK,SoundSource.BLOCKS,1.5f,.5f);
                    if(slam.dug>=DEPTH) {
                        floor(level,p,slam);
                        Vec3 bottom=new Vec3(slam.start.x,ground-DEPTH,slam.start.z);
                        hold(target,bottom);
                        target.hurt(p.damageSources().playerAttack(p),14);
                        target.setSecondsOnFire(6);
                        level.playSound(null,BlockPos.containing(bottom),SoundEvents.GENERIC_EXPLODE,SoundSource.PLAYERS,1.6f,.45f);
                        level.playSound(null,BlockPos.containing(bottom),SoundEvents.BLAZE_SHOOT,SoundSource.PLAYERS,1.2f,.5f);
                        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new GhostSlamPacket(bottom));
                        shockwave(p,new Vec3(slam.start.x,ground,slam.start.z),(float)(slam.radius+5),.9f);
                        s.heat=Math.min(10,s.heat+2);s.idleTicks=0;
                        finish(s,COOLDOWN);
                    }
                }
            }
            default -> {}
        }
    }
    private static void finish(State s,int cooldown) {
        GhostChainController.end(s);
        s.cooldown=cooldown;
    }
    private static float ease(float t) { t=Math.max(0,Math.min(1,t)); return t*t*(3-2*t); }
    private static void hold(LivingEntity target,Vec3 at) {
        if(target instanceof ServerPlayer player)player.connection.teleport(at.x,at.y,at.z,player.getYRot(),player.getXRot());
        else target.teleportTo(at.x,at.y,at.z);
        target.setDeltaMovement(Vec3.ZERO);target.hurtMarked=true;target.fallDistance=0;
    }
    private static void shockwave(ServerPlayer p,Vec3 center,float radius,float shake) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),new ShockwavePacket(center,radius,14,shake));
    }
    /** Topmost solid block at or just under where the target stood. */
    private static int surface(ServerLevel level,Vec3 at) {
        BlockPos.MutableBlockPos pos=BlockPos.containing(at).mutable();
        for(int i=0;i<8;i++,pos.move(0,-1,0))
            if(!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty())return pos.getY();
        return BlockPos.containing(at).getY()-1;
    }
    private static boolean editable(ServerLevel level,ServerPlayer p,BlockPos pos,BlockState state) {
        float hardness=state.getDestroySpeed(level,pos);
        return hardness>=0 && hardness<50 && level.getBlockEntity(pos)==null && level.mayInteract(p,pos);
    }
    /** One layer of the shaft: the core is smashed out, the ring around it turns to hellstone. */
    private static void dig(ServerLevel level,ServerPlayer p,HellSlam slam,int layer) {
        RandomSource random=level.getRandom();
        int y=slam.top-layer, reach=(int)Math.ceil(slam.radius+1.3);
        int cx=(int)Math.floor(slam.start.x), cz=(int)Math.floor(slam.start.z);
        for(int dx=-reach;dx<=reach;dx++)for(int dz=-reach;dz<=reach;dz++) {
            double d=Math.sqrt((cx+dx+.5-slam.start.x)*(cx+dx+.5-slam.start.x)+(cz+dz+.5-slam.start.z)*(cz+dz+.5-slam.start.z));
            BlockPos pos=new BlockPos(cx+dx,y,cz+dz);
            BlockState state=level.getBlockState(pos);
            if(!editable(level,p,pos,state))continue;
            if(d<=slam.radius) {
                if(state.isAir())continue;
                // Crumbling: a share of the blocks break with particles and sound.
                if(random.nextInt(3)==0)level.levelEvent(2001,pos,Block.getId(state));
                level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            } else if(d<=slam.radius+1.3) {
                level.setBlock(pos,wall(layer,random),3);
            }
        }
    }
    /** Dark hellstone that blackens with depth; sparse magma seams glint in the gloom. */
    private static BlockState wall(int depth,RandomSource r) {
        int roll=r.nextInt(100);
        if(depth<10) return (roll<40?Blocks.BLACKSTONE:roll<58?Blocks.POLISHED_BLACKSTONE_BRICKS
                :roll<73?Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS:roll<95?Blocks.BASALT:Blocks.MAGMA_BLOCK).defaultBlockState();
        if(depth<20) return (roll<34?Blocks.BLACKSTONE:roll<56?Blocks.BASALT:roll<76?Blocks.NETHER_BRICKS
                :roll<84?Blocks.POLISHED_BASALT:roll<92?Blocks.MAGMA_BLOCK:Blocks.CRYING_OBSIDIAN).defaultBlockState();
        return (roll<28?Blocks.BLACKSTONE:roll<50?Blocks.OBSIDIAN:roll<72?Blocks.NETHER_BRICKS
                :roll<86?Blocks.MAGMA_BLOCK:Blocks.CRYING_OBSIDIAN).defaultBlockState();
    }
    /** Scorched ground around the mouth of the pit, with a few fires. */
    private static void rim(ServerLevel level,ServerPlayer p,HellSlam slam) {
        RandomSource random=level.getRandom();
        int reach=(int)Math.ceil(slam.radius+2.6), cx=(int)Math.floor(slam.start.x), cz=(int)Math.floor(slam.start.z);
        for(int dx=-reach;dx<=reach;dx++)for(int dz=-reach;dz<=reach;dz++) {
            double d=Math.sqrt((cx+dx+.5-slam.start.x)*(cx+dx+.5-slam.start.x)+(cz+dz+.5-slam.start.z)*(cz+dz+.5-slam.start.z));
            if(d<=slam.radius+1.3 || d>slam.radius+2.6 || random.nextFloat()<(d-slam.radius-1.3)/1.6)continue;
            BlockPos pos=new BlockPos(cx+dx,slam.top,cz+dz);
            BlockState state=level.getBlockState(pos);
            if(state.isAir() || !editable(level,p,pos,state))continue;
            int roll=random.nextInt(100);
            BlockState next=(roll<45?Blocks.BLACKSTONE:roll<65?Blocks.BASALT:roll<80?Blocks.POLISHED_BLACKSTONE:roll<90?Blocks.MAGMA_BLOCK:Blocks.NETHERRACK).defaultBlockState();
            level.setBlock(pos,next,3);
            BlockPos above=pos.above();
            if(next.is(Blocks.NETHERRACK) && random.nextInt(3)==0 && level.getBlockState(above).isAir())
                level.setBlock(above,Blocks.FIRE.defaultBlockState(),3);
        }
    }
    /** The bottom: magma, soul soil with soul fire and burning netherrack. */
    private static void floor(ServerLevel level,ServerPlayer p,HellSlam slam) {
        RandomSource random=level.getRandom();
        int y=slam.top-DEPTH, reach=(int)Math.ceil(slam.radius), cx=(int)Math.floor(slam.start.x), cz=(int)Math.floor(slam.start.z);
        for(int dx=-reach;dx<=reach;dx++)for(int dz=-reach;dz<=reach;dz++) {
            double d=Math.sqrt((cx+dx+.5-slam.start.x)*(cx+dx+.5-slam.start.x)+(cz+dz+.5-slam.start.z)*(cz+dz+.5-slam.start.z));
            if(d>slam.radius)continue;
            BlockPos pos=new BlockPos(cx+dx,y,cz+dz);
            BlockState state=level.getBlockState(pos);
            if(!editable(level,p,pos,state))continue;
            int roll=random.nextInt(100);
            BlockState next=(roll<45?Blocks.MAGMA_BLOCK:roll<75?Blocks.BLACKSTONE:roll<92?Blocks.SOUL_SOIL:Blocks.NETHERRACK).defaultBlockState();
            level.setBlock(pos,next,3);
            BlockPos above=pos.above();
            if(!level.getBlockState(above).isAir() || d<1.2)continue;
            if(next.is(Blocks.SOUL_SOIL) && random.nextInt(2)==0)level.setBlock(above,Blocks.SOUL_FIRE.defaultBlockState(),3);
            else if(next.is(Blocks.NETHERRACK) && random.nextInt(2)==0)level.setBlock(above,Blocks.FIRE.defaultBlockState(),3);
        }
    }
}
