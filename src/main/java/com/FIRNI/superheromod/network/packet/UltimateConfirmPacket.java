package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.heroes.cyclops.CyclopsUltimateController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Istemci -> sunucu: ulti nisanini onayla veya iptal et. */
public class UltimateConfirmPacket {

    private final boolean confirm;

    public UltimateConfirmPacket(boolean confirm) {
        this.confirm = confirm;
    }

    public static void encode(UltimateConfirmPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.confirm);
    }

    public static UltimateConfirmPacket decode(FriendlyByteBuf buf) {
        return new UltimateConfirmPacket(buf.readBoolean());
    }

    public static void handle(UltimateConfirmPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.confirm) {
                CyclopsUltimateController.confirm(player);
            } else {
                CyclopsUltimateController.cancel(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
