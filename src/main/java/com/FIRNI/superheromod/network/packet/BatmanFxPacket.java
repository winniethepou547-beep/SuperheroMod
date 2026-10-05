package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * One Batman effect: what kind, where, which way (or how fast), how strong (or how long), the entity it concerns (the
 * one hit, or the Batman), and the id of the thing it is about (a Batarang, a pellet, a mine), so a client can follow
 * the same object to its end.
 */
public record BatmanFxPacket(int kind, Vec3 pos, Vec3 dir, float power, int entity, int id) {
    public static void encode(BatmanFxPacket p, FriendlyByteBuf b) {
        b.writeByte(p.kind);
        b.writeDouble(p.pos.x); b.writeDouble(p.pos.y); b.writeDouble(p.pos.z);
        b.writeFloat((float) p.dir.x); b.writeFloat((float) p.dir.y); b.writeFloat((float) p.dir.z);
        b.writeFloat(p.power); b.writeVarInt(p.entity + 1); b.writeVarInt(p.id);
    }
    public static BatmanFxPacket decode(FriendlyByteBuf b) {
        return new BatmanFxPacket(b.readByte(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), b.readFloat(), b.readVarInt() - 1, b.readVarInt());
    }
    public static void handle(BatmanFxPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.batman.BatmanFx.receive(p)));
        c.get().setPacketHandled(true);
    }
}
