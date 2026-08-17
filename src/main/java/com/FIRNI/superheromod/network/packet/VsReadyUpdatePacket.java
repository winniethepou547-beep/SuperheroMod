package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class VsReadyUpdatePacket {

    private final boolean playerReady;
    private final boolean opponentReady;

    public VsReadyUpdatePacket(boolean playerReady, boolean opponentReady) {
        this.playerReady = playerReady;
        this.opponentReady = opponentReady;
    }

    public static void encode(VsReadyUpdatePacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.playerReady);
        buf.writeBoolean(pkt.opponentReady);
    }

    public static VsReadyUpdatePacket decode(FriendlyByteBuf buf) {
        return new VsReadyUpdatePacket(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(VsReadyUpdatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientPacketHandler.updateVsReady(pkt.playerReady, pkt.opponentReady));
        ctx.setPacketHandled(true);
    }
}
