package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.ClientPacketHandler;
import com.FIRNI.superheromod.core.matchmaking.MatchMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: kuyruk doldu, oyuncuya Match Found ekranini gostermesini soyler. */
public class MatchFoundPacket {

    private final MatchMode mode;

    public MatchFoundPacket(MatchMode mode) {
        this.mode = mode;
    }

    public static void encode(MatchFoundPacket packet, FriendlyByteBuf buf) {
        buf.writeEnum(packet.mode);
    }

    public static MatchFoundPacket decode(FriendlyByteBuf buf) {
        return new MatchFoundPacket(buf.readEnum(MatchMode.class));
    }

    public static void handle(MatchFoundPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientPacketHandler.showMatchFound(packet.mode));
        ctx.setPacketHandled(true);
    }
}
