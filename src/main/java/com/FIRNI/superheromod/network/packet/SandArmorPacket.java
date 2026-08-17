package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sunucu -> istemci: bir oyuncunun kum zirh seviyesi degisti.
 *
 * Tum istemcilere gider cunku zirh gorsel bir katman; sadece sahibine
 * gonderilse baskalari Sandman'i hep zirhsiz gorurdu.
 */
public class SandArmorPacket {

    private final UUID playerId;
    private final int level;
    /** 0..1 — dikey barin doluluk orani. Sadece sahibinin ekraninda kullanilir. */
    private final float fill;

    public SandArmorPacket(UUID playerId, int level, float fill) {
        this.playerId = playerId;
        this.level = level;
        this.fill = fill;
    }

    public static void encode(SandArmorPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeVarInt(msg.level);
        buf.writeFloat(msg.fill);
    }

    public static SandArmorPacket decode(FriendlyByteBuf buf) {
        return new SandArmorPacket(buf.readUUID(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(SandArmorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.FIRNI.superheromod.client.render.ClientSandArmorData
                                .set(msg.playerId, msg.level, msg.fill)));
        ctx.get().setPacketHandled(true);
    }
}
