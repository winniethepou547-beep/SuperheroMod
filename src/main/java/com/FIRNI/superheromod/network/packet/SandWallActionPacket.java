package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.heroes.sandman.SandWallController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Istemci -> sunucu: kum duvari onizlemesini onayla / iptal et / firlat. */
public class SandWallActionPacket {

    public static final byte CONFIRM = 0;
    public static final byte CANCEL = 1;
    public static final byte LAUNCH = 2;

    private final byte action;

    public SandWallActionPacket(byte action) {
        this.action = action;
    }

    public static void encode(SandWallActionPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
    }

    public static SandWallActionPacket decode(FriendlyByteBuf buf) {
        return new SandWallActionPacket(buf.readByte());
    }

    public static void handle(SandWallActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            switch (msg.action) {
                case CONFIRM -> SandWallController.confirm(player);
                case CANCEL -> SandWallController.cancel(player);
                case LAUNCH -> SandWallController.launch(player);
                default -> {}
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
