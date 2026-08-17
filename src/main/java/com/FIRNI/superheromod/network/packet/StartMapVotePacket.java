package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: harita secim animasyonunu baslat. Harita listesi istemcide zaten sabit
 * (MapRegistry), sadece sunucunun rastgele sectigi kazanan index gonderiliyor.
 */
public class StartMapVotePacket {

    private final int winningIndex;

    public StartMapVotePacket(int winningIndex) {
        this.winningIndex = winningIndex;
    }

    public static void encode(StartMapVotePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.winningIndex);
    }

    public static StartMapVotePacket decode(FriendlyByteBuf buf) {
        return new StartMapVotePacket(buf.readVarInt());
    }

    public static void handle(StartMapVotePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientPacketHandler.showMapVote(packet.winningIndex));
        ctx.setPacketHandled(true);
    }
}
