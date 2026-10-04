package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** The player with Magneto's spike in them -> server: left clicks since the last packet (each pulls it a little further out). */
public record SpikePullPacket(int presses) {
    public static void encode(SpikePullPacket p, FriendlyByteBuf b) { b.writeByte(p.presses); }
    public static SpikePullPacket decode(FriendlyByteBuf b) { return new SpikePullPacket(b.readUnsignedByte()); }
    public static void handle(SpikePullPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> {
            var sender = c.get().getSender();
            if (sender != null) com.FIRNI.superheromod.heroes.magneto.MagnetoSpike.pull(sender, p.presses);
        });
        c.get().setPacketHandled(true);
    }
}
