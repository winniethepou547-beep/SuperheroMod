package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C: sunucu, oyuncuya mod secim ekranini acmasini soyler (veri tasimaz). */
public class OpenModeSelectPacket {

    public static void encode(OpenModeSelectPacket packet, FriendlyByteBuf buf) {
    }

    public static OpenModeSelectPacket decode(FriendlyByteBuf buf) {
        return new OpenModeSelectPacket();
    }

    public static void handle(OpenModeSelectPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(ClientPacketHandler::openModeSelect);
        ctx.setPacketHandled(true);
    }
}
