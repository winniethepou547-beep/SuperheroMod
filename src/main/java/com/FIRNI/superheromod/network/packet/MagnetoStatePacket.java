package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

import static com.FIRNI.superheromod.heroes.magneto.MagnetoAction.COOLDOWNS;

/**
 * One Magneto's whole state, every tick: what he is doing and since when, flying (and how far into the lift), his
 * cooldowns, the barrage's charges, the one he holds, the iron fist (where it is, the punch in progress, how many are
 * left) and the shield.
 */
public record MagnetoStatePacket(int entity, int action, int age, int flags, int[] cooldowns, int charges, float recharge, int flightAge,
                                 int held, int holdAge, int holdTicks, Vec3 fist, int fistAge, int punchAge, int punchesLeft, int shieldAge) {
    public static final int FLYING = 1, SHIELD = 2, FIST = 4, HOLDING = 8;
    public boolean flying() { return (flags & FLYING) != 0; }
    public static void encode(MagnetoStatePacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entity); b.writeByte(p.action); b.writeVarInt(p.age); b.writeByte(p.flags);
        for (int i = 0; i < COOLDOWNS; i++) b.writeVarInt(p.cooldowns[i]);
        b.writeByte(p.charges); b.writeFloat(p.recharge); b.writeVarInt(p.flightAge);
        b.writeVarInt(p.held + 1); b.writeVarInt(p.holdAge); b.writeVarInt(p.holdTicks);
        b.writeDouble(p.fist.x); b.writeDouble(p.fist.y); b.writeDouble(p.fist.z);
        b.writeVarInt(p.fistAge); b.writeVarInt(p.punchAge + 1); b.writeByte(p.punchesLeft); b.writeVarInt(p.shieldAge);
    }
    public static MagnetoStatePacket decode(FriendlyByteBuf b) {
        int entity = b.readVarInt(), action = b.readByte(), age = b.readVarInt(), flags = b.readByte();
        int[] cd = new int[COOLDOWNS];
        for (int i = 0; i < COOLDOWNS; i++) cd[i] = b.readVarInt();
        int charges = b.readByte();
        float recharge = b.readFloat();
        int flightAge = b.readVarInt(), held = b.readVarInt() - 1, holdAge = b.readVarInt(), holdTicks = b.readVarInt();
        Vec3 fist = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        int fistAge = b.readVarInt(), punchAge = b.readVarInt() - 1, punchesLeft = b.readByte(), shieldAge = b.readVarInt();
        return new MagnetoStatePacket(entity, action, age, flags, cd, charges, recharge, flightAge, held, holdAge, holdTicks, fist, fistAge, punchAge, punchesLeft, shieldAge);
    }
    public static void handle(MagnetoStatePacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.magneto.MagnetoClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
