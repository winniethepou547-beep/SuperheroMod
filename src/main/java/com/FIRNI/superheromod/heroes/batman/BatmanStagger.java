package com.FIRNI.superheromod.heroes.batman;

import com.FIRNI.superheromod.SuperheroMod;
import com.FIRNI.superheromod.network.ModNetworking;
import com.FIRNI.superheromod.network.packet.BatmanFxPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.*;

/**
 * Staggered by Batman (pulled off their feet by the grapnel, thrown up by a mine): for a while they wobble (their
 * clients draw the sway and the red daze mark over the head, a staggered player's camera shakes), they move and swing
 * STAGGER_SLOW slower, and the next blow of Batman's lands as a critical (×STAGGER_CRIT) and ends it.
 */
@Mod.EventBusSubscriber(modid = SuperheroMod.MODID)
public final class BatmanStagger {
    private static final UUID SLOW_MOVE = UUID.fromString("5b7d0c3e-2c11-4b2e-9a41-7a1c0f9e4d01");
    private static final UUID SLOW_SWING = UUID.fromString("5b7d0c3e-2c11-4b2e-9a41-7a1c0f9e4d02");
    private static final class Dazed { final LivingEntity e; long until; Dazed(LivingEntity e, long until) { this.e = e; this.until = until; } }
    private static final Map<Integer, Dazed> DAZED = new HashMap<>();

    private BatmanStagger() {}

    public static boolean staggered(LivingEntity e) { return DAZED.containsKey(e.getId()); }

    /** Staggers them for at least this many ticks. */
    public static void apply(LivingEntity e, int ticks) {
        long now = e.level().getGameTime();
        Dazed d = DAZED.get(e.getId());
        if (d == null) { d = new Dazed(e, now + ticks); DAZED.put(e.getId(), d); slow(e, true); }
        else d.until = Math.max(d.until, now + ticks);
        send(e, (int) (d.until - now));
    }
    /** A blow lands on them: if they were staggered, it is a critical and the stagger ends. */
    public static boolean consume(LivingEntity e) {
        Dazed d = DAZED.remove(e.getId());
        if (d == null) return false;
        slow(e, false);
        send(e, 0);
        return true;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || DAZED.isEmpty()) return;
        for (Iterator<Dazed> it = DAZED.values().iterator(); it.hasNext(); ) {
            Dazed d = it.next();
            if (d.e.isRemoved() || !d.e.isAlive() || d.e.level().getGameTime() >= d.until) {
                it.remove();
                if (!d.e.isRemoved()) { slow(d.e, false); send(d.e, 0); }
            }
        }
    }
    @SubscribeEvent public static void died(LivingDeathEvent e) { if (DAZED.remove(e.getEntity().getId()) != null) slow(e.getEntity(), false); }

    private static void slow(LivingEntity e, boolean on) {
        modifier(e.getAttribute(Attributes.MOVEMENT_SPEED), SLOW_MOVE, "Batman stagger", on);
        modifier(e.getAttribute(Attributes.ATTACK_SPEED), SLOW_SWING, "Batman stagger swing", on);
    }
    private static void modifier(AttributeInstance a, UUID id, String name, boolean on) {
        if (a == null) return;
        a.removeModifier(id);
        if (on) a.addTransientModifier(new AttributeModifier(id, name, -STAGGER_SLOW, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    private static void send(LivingEntity e, int ticks) {
        ModNetworking.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e),
                new BatmanFxPacket(FX_STAGGER, e.position(), Vec3.ZERO, ticks, e.getId(), 0));
    }
}
