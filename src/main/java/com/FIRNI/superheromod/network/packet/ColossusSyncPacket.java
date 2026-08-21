package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sunucu -> istemci: bir oyuncu Sand Colossus formunda mi ve kristallerinin
 * hasar durumu ne.
 *
 * Tum istemcilere gider: dev herkese gorunmeli ve kristallerin catlak
 * seviyesi saldiranlar tarafindan da okunabilmeli — zayif noktayi gormeden
 * nisan alamazlar.
 */
public class ColossusSyncPacket {

    private final UUID playerId;
    private final boolean active;
    /** Kristal basina gorsel durum: 0 saglam, 1 catlak, 2 agir, 3 kirik. */
    private final int[] crystalStates;

    public ColossusSyncPacket(UUID playerId, boolean active, int[] crystalStates) {
        this.playerId = playerId;
        this.active = active;
        this.crystalStates = crystalStates;
    }

    public static ColossusSyncPacket inactive(UUID playerId) {
        return new ColossusSyncPacket(playerId, false, new int[0]);
    }

    public static void encode(ColossusSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeBoolean(msg.active);
        buf.writeVarInt(msg.crystalStates.length);
        for (int state : msg.crystalStates) {
            buf.writeByte(state);
        }
    }

    public static ColossusSyncPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        boolean active = buf.readBoolean();
        int n = buf.readVarInt();
        int[] states = new int[n];
        for (int i = 0; i < n; i++) {
            states[i] = buf.readByte();
        }
        return new ColossusSyncPacket(id, active, states);
    }

    public static void handle(ColossusSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.FIRNI.superheromod.client.render.ClientColossusData
                                .set(msg.playerId, msg.active, msg.crystalStates)));
        ctx.get().setPacketHandled(true);
    }
}
