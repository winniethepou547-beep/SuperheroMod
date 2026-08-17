package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.hud.ClientHeatData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class HeatSyncPacket {

    private final float current;
    private final float max;
    private final boolean overheated;
    private final boolean active;

    public HeatSyncPacket(float current, float max, boolean overheated, boolean active) {
        this.current = current;
        this.max = max;
        this.overheated = overheated;
        this.active = active;
    }

    public static void encode(HeatSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.current);
        buf.writeFloat(msg.max);
        buf.writeBoolean(msg.overheated);
        buf.writeBoolean(msg.active);
    }

    public static HeatSyncPacket decode(FriendlyByteBuf buf) {
        return new HeatSyncPacket(buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(HeatSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientHeatData.update(msg.current, msg.max, msg.overheated, msg.active)));
        ctx.get().setPacketHandled(true);
    }
}
