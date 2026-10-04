package com.FIRNI.superheromod.core.film;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.FilmSessionPacket;
import net.minecraft.server.level.ServerPlayer;
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
 * A film finisher held by the server: both performers are pinned where they stand, facing each
 * other, while the film plays on every client from the synced clock. The script decides what
 * happens to the world and when (damage, knockback, sound); the film only shows it.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class FilmSessions {
    /** What the server does along a film's timeline. */
    public interface Script {
        String film();
        int total();
        /** Until this tick both performers are held in place; after it the world takes over. */
        int release();
        /** Called once per tick with the new age (1..total); the target is null once it is gone (see outlivesTarget). */
        void tick(ServerPlayer attacker, LivingEntity target, int age, Vec3 forward);
        /** True if the film plays on to its end even when the target dies or is removed (it is a finisher). */
        default boolean outlivesTarget() { return false; }
    }
    private static final class Session {
        Script script; UUID target; int age; Vec3 anchor, targetAt; float yaw; boolean aiWasOff;
    }
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();

    /** True for a performer of any running film finisher. */
    public static boolean busy(UUID id) {
        if (ACTIVE.containsKey(id)) return true;
        for (Session s : ACTIVE.values()) if (id.equals(s.target)) return true;
        return false;
    }
    public static boolean playing(UUID attacker, String film) {
        Session s = ACTIVE.get(attacker);
        return s != null && s.script.film().equals(film);
    }
    public static boolean start(ServerPlayer attacker, LivingEntity target, Script script) {
        if (busy(attacker.getUUID()) || busy(target.getUUID()) || target == attacker || target.level() != attacker.level()) return false;
        Session s = new Session();
        s.script = script; s.target = target.getUUID(); s.anchor = attacker.position(); s.targetAt = target.position();
        Vec3 toward = target.position().subtract(attacker.position());
        s.yaw = toward.horizontalDistanceSqr() < 1e-4 ? attacker.getYRot() : (float) Math.toDegrees(Math.atan2(-toward.x, toward.z));
        if (target instanceof Mob mob) { s.aiWasOff = mob.isNoAi(); mob.setNoAi(true); }
        ACTIVE.put(attacker.getUUID(), s);
        sync(attacker, target, s, true);
        return true;
    }
    /**
     * A film with no one else in it (it plays for the attacker alone, on its own stage): they are held where they
     * stand, facing the way they looked, until the script lets go.
     */
    public static boolean startSolo(ServerPlayer attacker, Script script) {
        if (busy(attacker.getUUID())) return false;
        Session s = new Session();
        s.script = script; s.target = null; s.anchor = attacker.position(); s.targetAt = attacker.position();
        s.yaw = attacker.getYRot();
        ACTIVE.put(attacker.getUUID(), s);
        sync(attacker, null, s, true);
        return true;
    }
    public static void stop(ServerPlayer attacker) {
        Session s = ACTIVE.get(attacker.getUUID());
        if (s == null) return;
        LivingEntity target = s.target != null && attacker.serverLevel().getEntity(s.target) instanceof LivingEntity living ? living : null;
        finish(attacker, target, s);
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        Session s = ACTIVE.get(p.getUUID());
        if (s == null) return;
        LivingEntity target = s.target != null && p.serverLevel().getEntity(s.target) instanceof LivingEntity living ? living : null;
        boolean gone = target == null || !target.isAlive();
        if (!p.isAlive() || target == null && s.target != null && !s.script.outlivesTarget() || target != null && target.distanceTo(p) > 40) { finish(p, target, s); return; }
        s.age++;
        Vec3 forward = Vec3.directionFromRotation(0, s.yaw);
        if (s.age < s.script.release()) {
            hold(p, s.anchor, s.yaw);
            if (!gone) hold(target, s.targetAt, s.yaw + 180);
        } else if (s.age == s.script.release() && target instanceof Mob mob) mob.setNoAi(s.aiWasOff);
        s.script.tick(p, target, s.age, forward);
        sync(p, target, s, true);
        if (s.age >= s.script.total() || gone && s.target != null && !s.script.outlivesTarget() && s.age >= s.script.release()) finish(p, target, s);
    }
    private static void hold(LivingEntity body, Vec3 at, float facing) {
        if (body instanceof ServerPlayer player) {
            if (player.position().distanceToSqr(at) > .0004) player.connection.teleport(at.x, at.y, at.z, facing, 0);
        } else {
            body.teleportTo(at.x, at.y, at.z);
            body.setYRot(facing); body.yBodyRot = facing; body.yHeadRot = facing;
        }
        body.setDeltaMovement(Vec3.ZERO); body.hurtMarked = true; body.fallDistance = 0;
    }
    private static void finish(ServerPlayer p, LivingEntity target, Session s) {
        ACTIVE.remove(p.getUUID());
        if (target instanceof Mob mob && s.age < s.script.release()) mob.setNoAi(s.aiWasOff);
        sync(p, target, s, false);
    }
    private static void sync(ServerPlayer p, LivingEntity target, Session s, boolean active) {
        var packet = new FilmSessionPacket(s.script.film(), p.getId(), target == null ? -1 : target.getId(), s.age, active, s.yaw, s.anchor);
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), packet);
        if (target instanceof ServerPlayer other && !other.equals(p))
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> other), packet);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Session s = ACTIVE.remove(e.getEntity().getUUID());
        if (s != null && s.target != null && e.getEntity() instanceof ServerPlayer p && p.serverLevel().getEntity(s.target) instanceof Mob mob) mob.setNoAi(s.aiWasOff);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { ACTIVE.clear(); }
}
