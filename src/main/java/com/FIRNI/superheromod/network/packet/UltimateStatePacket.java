package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.hud.ClientUltimateState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Sunucu -> istemci: ulti fazi (kapali / nisan / hazirlik pozu / atis). */
public class UltimateStatePacket {

    public static final byte PHASE_NONE = 0;
    public static final byte PHASE_AIMING = 1;
    public static final byte PHASE_WINDUP = 2;
    public static final byte PHASE_SWEEP = 3;

    private final byte phase;
    private final UUID playerId;

    public UltimateStatePacket(byte phase, UUID playerId) {
        this.phase = phase;
        this.playerId = playerId;
    }

    public static void encode(UltimateStatePacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.phase);
        buf.writeUUID(msg.playerId);
    }

    public static UltimateStatePacket decode(FriendlyByteBuf buf) {
        return new UltimateStatePacket(buf.readByte(), buf.readUUID());
    }

    public static void handle(UltimateStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientUltimateState.setPhase(msg.playerId, msg.phase)));
        ctx.get().setPacketHandled(true);
    }
}
