package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * One Magneto effect: what kind, where, which way (or how fast), how strong, the entity it concerns, and the id of the
 * piece of metal it is about (a rod, a shard, a piece of the burst), so a client can follow the same piece to its end.
 */
public record MagnetoFxPacket(int kind, Vec3 pos, Vec3 dir, float power, int entity, int id) {
    public static void encode(MagnetoFxPacket p, FriendlyByteBuf b) {
        b.writeByte(p.kind);
        b.writeDouble(p.pos.x); b.writeDouble(p.pos.y); b.writeDouble(p.pos.z);
        b.writeFloat((float) p.dir.x); b.writeFloat((float) p.dir.y); b.writeFloat((float) p.dir.z);
        b.writeFloat(p.power); b.writeVarInt(p.entity + 1); b.writeVarInt(p.id);
    }
    public static MagnetoFxPacket decode(FriendlyByteBuf b) {
        return new MagnetoFxPacket(b.readByte(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), b.readFloat(), b.readVarInt() - 1, b.readVarInt());
    }
    public static void handle(MagnetoFxPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.magneto.MagnetoFx.receive(p)));
        c.get().setPacketHandled(true);
    }
}
