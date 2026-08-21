package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.heroes.sandman.SandPillarController;
import com.FIRNI.superheromod.core.ability.AbilityManager;
import com.FIRNI.superheromod.heroes.sandman.SandmanCharacter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Istemci -> sunucu: havada ikinci kez bosluga basildi.
 *
 * Cift basis ISTEMCIDE algilaniyor cunku sunucu ziplama tusunu goremez;
 * sadece "hareket etti" bilgisi ulasir. Sunucu yine de yetkiyi elinde
 * tutuyor: karakter kontrolu, havada olma sarti ve bekleme suresi
 * {@link SandPillarController} icinde dogrulaniyor.
 */
public class SandPillarPacket {

    public SandPillarPacket() {}

    public static void encode(SandPillarPacket msg, FriendlyByteBuf buf) {}

    public static SandPillarPacket decode(FriendlyByteBuf buf) {
        return new SandPillarPacket();
    }

    public static void handle(SandPillarPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Yetenek Sandman'a ait; baska karakterle tetiklenmemeli
            if (!SandmanCharacter.ID.equals(
                    AbilityManager.getCharacterId(player.getUUID()))) return;

            SandPillarController.launch(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
