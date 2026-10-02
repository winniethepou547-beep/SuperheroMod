package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * What a Thor is doing (action + its age in ticks), his flags, where Mjolnir is while it is thrown,
 * and who is pinned to the hammer during a Shift launch (-1: nobody).
 */
public record ThorStatePacket(int entity, int action, int age, int flags, Vec3 hammer, int carried) {
    public static void encode(ThorStatePacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entity); b.writeByte(p.action); b.writeVarInt(p.age); b.writeByte(p.flags);
        b.writeDouble(p.hammer.x); b.writeDouble(p.hammer.y); b.writeDouble(p.hammer.z);
        b.writeVarInt(p.carried + 1);
    }
    public static ThorStatePacket decode(FriendlyByteBuf b) {
        return new ThorStatePacket(b.readVarInt(), b.readByte(), b.readVarInt(), b.readByte(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readVarInt() - 1);
    }
    public static void handle(ThorStatePacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.thor.ThorClient.receive(p)));
        c.get().setPacketHandled(true);
    }
}
