package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** One Black Panther effect: what kind, where, which way, how strong, and an entity id where one matters. */
public record PantherFxPacket(int kind, Vec3 pos, Vec3 dir, float power, int entity) {
    public static void encode(PantherFxPacket p, FriendlyByteBuf b) {
        b.writeByte(p.kind);
        b.writeDouble(p.pos.x); b.writeDouble(p.pos.y); b.writeDouble(p.pos.z);
        b.writeFloat((float) p.dir.x); b.writeFloat((float) p.dir.y); b.writeFloat((float) p.dir.z);
        b.writeFloat(p.power); b.writeVarInt(p.entity + 1);
    }
    public static PantherFxPacket decode(FriendlyByteBuf b) {
        return new PantherFxPacket(b.readByte(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), b.readFloat(), b.readVarInt() - 1);
    }
    public static void handle(PantherFxPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.panther.PantherFx.receive(p)));
        c.get().setPacketHandled(true);
    }
}
