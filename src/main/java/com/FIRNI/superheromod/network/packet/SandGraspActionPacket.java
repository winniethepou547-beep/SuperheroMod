package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.heroes.sandman.SandGraspController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Istemci -> sunucu: Sand Grasp alanini onayla / iptal et. */
public class SandGraspActionPacket {

    public static final byte CONFIRM = 0;
    public static final byte CANCEL = 1;

    private final byte action;

    public SandGraspActionPacket(byte action) {
        this.action = action;
    }

    public static void encode(SandGraspActionPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
    }

    public static SandGraspActionPacket decode(FriendlyByteBuf buf) {
        return new SandGraspActionPacket(buf.readByte());
    }

    public static void handle(SandGraspActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.action) {
                case CONFIRM -> SandGraspController.confirm(player);
                case CANCEL -> SandGraspController.cancel(player);
                default -> {}
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
