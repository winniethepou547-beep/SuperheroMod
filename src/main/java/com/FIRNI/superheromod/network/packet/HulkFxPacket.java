package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** One Hulk effect: what kind, where, which way, how strong, and a block (state id) for its debris. */
public record HulkFxPacket(int kind, Vec3 pos, Vec3 dir, float power, int block) {
    public static void encode(HulkFxPacket p, FriendlyByteBuf b) {
        b.writeByte(p.kind);
        b.writeDouble(p.pos.x); b.writeDouble(p.pos.y); b.writeDouble(p.pos.z);
        b.writeFloat((float) p.dir.x); b.writeFloat((float) p.dir.y); b.writeFloat((float) p.dir.z);
        b.writeFloat(p.power); b.writeVarInt(p.block);
    }
    public static HulkFxPacket decode(FriendlyByteBuf b) {
        return new HulkFxPacket(b.readByte(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), b.readFloat(), b.readVarInt());
    }
    public static void handle(HulkFxPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.hulk.HulkFx.receive(p)));
        c.get().setPacketHandled(true);
    }
}
