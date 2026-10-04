package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.core.sound.ModSounds;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;
import static com.FIRNI.superheromod.heroes.batman.BatmanConfig.f;

/**
 * The sticky bomb (it replaced the mine): the grapnel strike leaves it on the back of their head as they bounce apart
 * in the air; it beeps faster and faster and goes off STICKY_FUSE ticks later: a sharp, contained blast that hurts them
 * (and whoever is close), throws them and dazes them for as long as they are in the air. The clients draw it on the head
 * (BatmanFx) from FX_STICKY and the blast from FX_STICKY_BOOM.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BatmanSticky {
    private static final class Bomb {
        final int id; final ServerPlayer owner; final LivingEntity target; int age; Vec3 last;
        Bomb(int id, ServerPlayer owner, LivingEntity target) { this.id = id; this.owner = owner; this.target = target; this.last = target.getEyePosition(); }
    }
    private static final List<Bomb> BOMBS = new ArrayList<>();
    private static int nextId = 1;

    private BatmanSticky() {}

    /** Onto the back of their head (one bomb per head: a new one replaces the old). */
    static void stick(ServerPlayer p, LivingEntity t) {
        BOMBS.removeIf(b -> b.target == t);
        Bomb b = new Bomb(nextId++, p, t);
        BOMBS.add(b);
        send(b, FX_STICKY, t.getEyePosition(), STICKY_FUSE);
        t.level().playSound(null, t.getX(), t.getEyeY(), t.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, .9f, 1.3f);
        t.level().playSound(null, t.getX(), t.getEyeY(), t.getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, .8f, 1.8f);
    }
    static void clear(ServerPlayer owner) { BOMBS.removeIf(b -> b.owner == owner); }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || BOMBS.isEmpty()) return;
        for (Iterator<Bomb> it = BOMBS.iterator(); it.hasNext(); ) {
            Bomb b = it.next();
            if (b.owner.isRemoved() || b.target.isRemoved()) { it.remove(); continue; }
            if (b.target.isAlive()) b.last = b.target.getEyePosition();
            b.age++;
            // The beeps come faster as the fuse burns down.
            int gap = Math.max(2, 10 - b.age / 6);
            if (b.age % gap == 0)
                b.target.level().playSound(null, b.last.x, b.last.y, b.last.z, SoundEvents.NOTE_BLOCK_PLING.get(), SoundSource.PLAYERS, .45f, 1.6f + b.age / (float) STICKY_FUSE * .4f);
            if (b.age >= STICKY_FUSE) { it.remove(); explode(b); }
        }
    }
    private static void explode(Bomb b) {
        ServerPlayer p = b.owner;
        Vec3 at = b.last;
        double radius = Math.max(.5, BatmanConfig.STICKY_RADIUS.get());
        for (LivingEntity t : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1), t -> BatmanController.targetable(p, t))) {
            boolean worn = t == b.target;
            double d = worn ? 0 : t.getBoundingBox().getCenter().distanceTo(at);
            if (d > radius) continue;
            float k = worn ? 1 : (float) (1 - d / radius * .6);
            BatmanController.hurt(p, t, f(BatmanConfig.STICKY_DAMAGE) * k);
            // Thrown forward and up away from the blast at the back of the head.
            Vec3 out = worn ? t.getLookAngle() : t.position().subtract(at);
            Vec3 flat = new Vec3(out.x, 0, out.z);
            flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
            double up = BatmanConfig.STICKY_LAUNCH.get() * k;
            t.setDeltaMovement(flat.x * .5 * k, Math.max(t.getDeltaMovement().y, 0) + up, flat.z * .5 * k);
            t.hurtMarked = true;
            // Dazed while in the air (the game's own gravity decides how long), and a little after.
            double y = 0, vy = Math.max(t.getDeltaMovement().y, up); int air = 0;
            do { y += vy; vy = (vy - .08) * .98; air++; } while (y > 0 && air < 200);
            BatmanStagger.apply(t, air + 8);
        }
        var packet = new BatmanFxPacket(FX_STICKY_BOOM, at, Vec3.ZERO, (float) radius, b.target.getId(), b.id);
        ModNetworking.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(at.x, at.y, at.z, 96, p.level().dimension())), packet);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.1f, 1.45f);
        p.level().playSound(null, at.x, at.y, at.z, ModSounds.FX_ENERGY_BOOM.get(), SoundSource.PLAYERS, .8f, 1.5f);
    }
    private static void send(Bomb b, int kind, Vec3 at, float power) {
        var packet = new BatmanFxPacket(kind, at, Vec3.ZERO, power, b.target.getId(), b.id);
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> b.target), packet);
        if (b.owner.distanceTo(b.target) > 64) ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> b.owner), packet);
    }
}
