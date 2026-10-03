package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * What a Black Panther is doing: his action, its age and flags (which hand, which way a dodge goes),
 * the cooldowns, the stored energy (0..1) and how much went into the last release, the reflex (share
 * and ticks left), the last hit he took (ticks since, how hard, from where), where the threat is, how
 * long since he last fought, the target of the move in progress and the points of its path (with the
 * pounce speed and the spin height, so his client steers along exactly the server's path).
 */
public record PantherStatePacket(int entity, int action, int age, int flags, int[] cooldowns,
                                 float energy, float released, float reflex, int reflexLeft,
                                 int hurtAge, int hurtPower, float hurtYaw, float threatYaw, int quiet, int target,
                                 Vec3 from, Vec3 dir, Vec3 apex, Vec3 to, Vec3 land, float reach, float speed, float height) {
    public static final int COOLDOWNS = 5;
    private static void vec(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vec(FriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    public static void encode(PantherStatePacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entity); b.writeByte(p.action); b.writeVarInt(p.age); b.writeByte(p.flags);
        for (int i = 0; i < COOLDOWNS; i++) b.writeVarInt(i < p.cooldowns.length ? p.cooldowns[i] : 0);
        b.writeFloat(p.energy); b.writeFloat(p.released); b.writeFloat(p.reflex); b.writeVarInt(p.reflexLeft);
        b.writeByte(p.hurtAge); b.writeByte(p.hurtPower); b.writeFloat(p.hurtYaw); b.writeFloat(p.threatYaw); b.writeByte(p.quiet);
        b.writeVarInt(p.target + 1);
        vec(b, p.from); vec(b, p.dir); vec(b, p.apex); vec(b, p.to); vec(b, p.land); b.writeFloat(p.reach); b.writeFloat(p.speed); b.writeFloat(p.height);
    }
    public static PantherStatePacket decode(FriendlyByteBuf b) {
        int entity = b.readVarInt(), action = b.readByte(), age = b.readVarInt(), flags = b.readByte();
        int[] cd = new int[COOLDOWNS];
        for (int i = 0; i < COOLDOWNS; i++) cd[i] = b.readVarInt();
        float energy = b.readFloat(), released = b.readFloat(), reflex = b.readFloat();
        int reflexLeft = b.readVarInt();
        int hurtAge = b.readUnsignedByte(), hurtPower = b.readByte();
        float hurtYaw = b.readFloat(), threatYaw = b.readFloat();
        int quiet = b.readUnsignedByte(), target = b.readVarInt() - 1;
        Vec3 from = vec(b), dir = vec(b), apex = vec(b), to = vec(b), land = vec(b);
        float reach = b.readFloat(), speed = b.readFloat(), height = b.readFloat();
        return new PantherStatePacket(entity, action, age, flags, cd, energy, released, reflex, reflexLeft, hurtAge, hurtPower, hurtYaw, threatYaw,
                quiet, target, from, dir, apex, to, land, reach, speed, height);
    }
    public static void handle(PantherStatePacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.panther.PantherClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
