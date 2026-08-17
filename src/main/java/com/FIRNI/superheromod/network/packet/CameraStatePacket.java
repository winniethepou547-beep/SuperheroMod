package com.FIRNI.superheromod.network.packet;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CameraStatePacket {

    private final boolean thirdPerson;

    public CameraStatePacket(boolean thirdPerson) {
        this.thirdPerson = thirdPerson;
    }

    public static void encode(CameraStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.thirdPerson);
    }

    public static CameraStatePacket decode(FriendlyByteBuf buf) {
        return new CameraStatePacket(buf.readBoolean());
    }

    public static void handle(CameraStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    Minecraft mc = Minecraft.getInstance();
                    com.FIRNI.superheromod.client.ClientHeroState.setHero(msg.thirdPerson);
                    if (msg.thirdPerson) {
                        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    }
                }));
        ctx.get().setPacketHandled(true);
    }
}
