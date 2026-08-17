package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * "Su oyuncu su kahraman" bilgisini TUM istemcilere duyurur.
 *
 * CameraStatePacket sadece kahramani secen oyuncuya gidiyordu; skin ve poz
 * icin herkesin bilmesi gerekiyor, yoksa karakteri sadece kendisi kusanmis
 * gorunurdu.
 *
 * Bos characterId = kahramanlik kaldirildi.
 */
public class HeroIdentityPacket {

    private final UUID playerId;
    private final String characterId;

    public HeroIdentityPacket(UUID playerId, String characterId) {
        this.playerId = playerId;
        this.characterId = characterId == null ? "" : characterId;
    }

    public static void encode(HeroIdentityPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerId);
        buf.writeUtf(msg.characterId);
    }

    public static HeroIdentityPacket decode(FriendlyByteBuf buf) {
        return new HeroIdentityPacket(buf.readUUID(), buf.readUtf());
    }

    public static void handle(HeroIdentityPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    com.FIRNI.superheromod.client.ClientHeroRegistry
                            .set(msg.playerId, msg.characterId);
                }));
        ctx.get().setPacketHandled(true);
    }
}
