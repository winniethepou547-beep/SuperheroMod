package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

import static com.FIRNI.superheromod.heroes.batman.BatmanAction.COOLDOWNS;

/**
 * One Batman's whole state, every tick: what he is doing and since when, his flags (gliding, grapnel out, wheel open,
 * line attached), the punch chain, the Batarangs counted up in a held throw and left in his belt (and how far the next
 * one is), the gadget picked, the roll's direction, the hook and his cooldowns.
 */
public record BatmanStatePacket(int entity, int action, int age, int flags, int combo, int charge, int batarangs, float refill,
                                int gadget, float dodgeYaw, Vec3 hook, int hookEntity, int[] cooldowns) {
    public static void encode(BatmanStatePacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entity); b.writeByte(p.action); b.writeVarInt(p.age); b.writeByte(p.flags);
        b.writeVarInt(p.combo); b.writeByte(p.charge); b.writeByte(p.batarangs); b.writeFloat(p.refill);
        b.writeByte(p.gadget); b.writeFloat(p.dodgeYaw);
        b.writeBoolean(p.hook != null);
        if (p.hook != null) { b.writeDouble(p.hook.x); b.writeDouble(p.hook.y); b.writeDouble(p.hook.z); }
        b.writeVarInt(p.hookEntity + 1);
        for (int i = 0; i < COOLDOWNS; i++) b.writeVarInt(p.cooldowns[i]);
    }
    public static BatmanStatePacket decode(FriendlyByteBuf b) {
        int entity = b.readVarInt(), action = b.readByte(), age = b.readVarInt(), flags = b.readByte();
        int combo = b.readVarInt(), charge = b.readByte(), batarangs = b.readByte();
        float refill = b.readFloat();
        int gadget = b.readByte();
        float dodgeYaw = b.readFloat();
        Vec3 hook = b.readBoolean() ? new Vec3(b.readDouble(), b.readDouble(), b.readDouble()) : null;
        int hookEntity = b.readVarInt() - 1;
        int[] cd = new int[COOLDOWNS];
        for (int i = 0; i < COOLDOWNS; i++) cd[i] = b.readVarInt();
        return new BatmanStatePacket(entity, action, age, flags, combo, charge, batarangs, refill, gadget, dodgeYaw, hook, hookEntity, cd);
    }
    public static void handle(BatmanStatePacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.batman.BatmanClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
