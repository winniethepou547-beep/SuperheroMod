package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** What Iceman's own client reports (IcemanAction.IN_*): the wheel, the weapon picked, the slides, a sculpting point. */
public record IcemanInputPacket(int kind, int value, float amount, Vec3 at) {
    public IcemanInputPacket(int kind, int value, float amount) { this(kind, value, amount, Vec3.ZERO); }
    public static void encode(IcemanInputPacket p, FriendlyByteBuf b) {
        b.writeByte(p.kind); b.writeVarInt(p.value); b.writeFloat(p.amount);
        b.writeFloat((float) p.at.x); b.writeFloat((float) p.at.y); b.writeFloat((float) p.at.z);
    }
    public static IcemanInputPacket decode(FriendlyByteBuf b) {
        return new IcemanInputPacket(b.readByte(), b.readVarInt(), b.readFloat(), new Vec3(b.readFloat(), b.readFloat(), b.readFloat()));
    }
    public static void handle(IcemanInputPacket p, Supplier<NetworkEvent.Context> c) {
        var sender = c.get().getSender();
        c.get().enqueueWork(() -> { if (sender != null) com.FIRNI.superheromod.heroes.iceman.IcemanController.input(sender, p.kind(), p.value(), p.amount(), p.at()); });
        c.get().setPacketHandled(true);
    }
}
