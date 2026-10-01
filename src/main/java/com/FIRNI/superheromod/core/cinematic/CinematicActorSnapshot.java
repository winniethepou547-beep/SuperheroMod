package com.FIRNI.superheromod.core.cinematic;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** State owned temporarily by the cinematic, restored on completion and every abort path. */
final class CinematicActorSnapshot {
    final LivingEntity entity;
    final Vec3 position,velocity;
    final float yaw,pitch,head,body,fall;
    final boolean gravity,physics,invulnerable,ai;
    boolean restored;
    CinematicActorSnapshot(LivingEntity entity) {
        this.entity=entity;position=entity.position();velocity=entity.getDeltaMovement();
        yaw=entity.getYRot();pitch=entity.getXRot();head=entity.yHeadRot;body=entity.yBodyRot;fall=entity.fallDistance;
        gravity=entity.isNoGravity();physics=entity.noPhysics;invulnerable=entity.isInvulnerable();
        ai=entity instanceof Mob mob && mob.isNoAi();
    }
    void hold() {
        if(entity instanceof net.minecraft.server.level.ServerPlayer player
                && entity.position().distanceToSqr(position)>.0025)
            player.connection.teleport(position.x,position.y,position.z,entity.getYRot(),entity.getXRot());
        entity.setNoGravity(true);entity.noPhysics=true;entity.setInvulnerable(true);
        entity.setPos(position);entity.setDeltaMovement(Vec3.ZERO);entity.fallDistance=0;
        if(entity instanceof Mob mob) { mob.setNoAi(true);mob.getNavigation().stop(); }
    }
    void restore() {
        if(restored)return;restored=true;
        entity.setNoGravity(gravity);entity.noPhysics=physics;entity.setInvulnerable(invulnerable);
        if(entity instanceof Mob mob)mob.setNoAi(ai);
        entity.setYRot(yaw);entity.setXRot(pitch);entity.yHeadRot=head;entity.yBodyRot=body;
        if(entity.isAlive()) { entity.setDeltaMovement(velocity);entity.fallDistance=fall;entity.hurtMarked=true; }
    }
}
