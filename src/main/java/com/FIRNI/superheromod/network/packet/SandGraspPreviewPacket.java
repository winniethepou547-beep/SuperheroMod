package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.render.ClientSandGraspData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sunucu -> istemci: Sand Grasp onizlemesi acildi/kapandi.
 *
 * Istemcinin bunu bilmesi sart: onizleme acikken sol/sag tik normal
 * yeteneklere degil onay/iptal'e gitmeli. Alanin GORSELI sunucu
 * partikulleriyle ciziliyor, bu paket sadece tus yonlendirmesi icin.
 */
public class SandGraspPreviewPacket {

    private final boolean active;

    public SandGraspPreviewPacket(boolean active) {
        this.active = active;
    }

    public static void encode(SandGraspPreviewPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
    }

    public static SandGraspPreviewPacket decode(FriendlyByteBuf buf) {
        return new SandGraspPreviewPacket(buf.readBoolean());
    }

    public static void handle(SandGraspPreviewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSandGraspData.set(msg.active)));
        ctx.get().setPacketHandled(true);
    }
}
