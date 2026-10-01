package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sunucu -> istemci: dev bir harekete basladi.
 *
 * Her tick konum yollamak yerine SADECE BASLANGIC bildiriliyor; istemci
 * hareketi kendi sayaciyla oynatiyor. Animasyon kisa oldugu icin kaymaya
 * firsat kalmiyor ve ag trafigi hareket basina tek pakete iniyor.
 */
public class ColossusActionPacket {

    public static final byte MACE_SWING = 0;
    public static final byte ROCK_THROW = 1;
    public static final byte ROCK_HOLD = 2;
    public static final byte SWORD_STAB = 3;

    private final UUID playerId;
    private final byte action;
    private final int durationTicks;

    public ColossusActionPacket(UUID playerId, byte action, int durationTicks) {
        this.playerId = playerId;
        this.action = action;
        this.durationTicks = durationTicks;
    }

    public static void encode(ColossusActionPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeByte(msg.action);
        buf.writeVarInt(msg.durationTicks);
    }

    public static ColossusActionPacket decode(FriendlyByteBuf buf) {
        return new ColossusActionPacket(buf.readUUID(), buf.readByte(), buf.readVarInt());
    }

    public static void handle(ColossusActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.FIRNI.superheromod.client.render.colossus.ClientColossusActions
                                .start(msg.playerId, msg.action, msg.durationTicks)));
        ctx.get().setPacketHandled(true);
    }
}
