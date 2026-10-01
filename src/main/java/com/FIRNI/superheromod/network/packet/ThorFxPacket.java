package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** One lightning effect at a place: what kind, where, which way, how big, and a seed for its shape. */
public record ThorFxPacket(int kind, Vec3 pos, Vec3 dir, float power, int seed) {
    public static void encode(ThorFxPacket p, FriendlyByteBuf b) {
        b.writeByte(p.kind);
        b.writeDouble(p.pos.x); b.writeDouble(p.pos.y); b.writeDouble(p.pos.z);
        b.writeFloat((float) p.dir.x); b.writeFloat((float) p.dir.y); b.writeFloat((float) p.dir.z);
        b.writeFloat(p.power); b.writeInt(p.seed);
    }
    public static ThorFxPacket decode(FriendlyByteBuf b) {
        return new ThorFxPacket(b.readByte(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), b.readFloat(), b.readInt());
    }
    public static void handle(ThorFxPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.FIRNI.superheromod.client.render.thor.ThorFx.receive(p)));
        c.get().setPacketHandled(true);
    }
}
