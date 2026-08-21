package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sunucu -> istemci: yerde disa dogru buyuyen sok dalgasi halkasi.
 *
 * Partikul degil GEOMETRI olarak ciziliyor: partikuller kare ve dagilir,
 * halkanin duzgun bir daire olarak buyumesi gerekiyor.
 */
public class ShockwavePacket {

    private final Vec3 center;
    private final float maxRadius;
    private final int durationTicks;
    /** 0 = sarsinti yok. Istemci bunu ekran sarsintisina cevirir. */
    private final float shake;

    public ShockwavePacket(Vec3 center, float maxRadius, int durationTicks, float shake) {
        this.center = center;
        this.maxRadius = maxRadius;
        this.durationTicks = durationTicks;
        this.shake = shake;
    }

    public static void encode(ShockwavePacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.center.x);
        buf.writeDouble(msg.center.y);
        buf.writeDouble(msg.center.z);
        buf.writeFloat(msg.maxRadius);
        buf.writeVarInt(msg.durationTicks);
        buf.writeFloat(msg.shake);
    }

    public static ShockwavePacket decode(FriendlyByteBuf buf) {
        return new ShockwavePacket(
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(ShockwavePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    com.FIRNI.superheromod.client.render.ClientShockwaveData
                            .add(msg.center, msg.maxRadius, msg.durationTicks);

                    if (msg.shake > 0f) {
                        com.FIRNI.superheromod.client.render.ClientScreenShake
                                .addFromSource(msg.center, msg.shake, msg.maxRadius);
                    }
                }));
        ctx.get().setPacketHandled(true);
    }
}
