package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.render.ClientBeamData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Isin senkronu. Isin artik cok parcali bir yol (polyline) olarak tasiniyor:
 * yuzeyden sekme ve hedefe zincirlenme bu sayede mumkun.
 *
 *  CHANNEL -> basili tutuldugu surece her tick yenilenen yol
 *  FLASH   -> belirli sure yasayan tek seferlik yol (iz / atis)
 *  STOP    -> kanal isinini kapat
 */
public class BeamSyncPacket {

    public static final byte TYPE_CHANNEL = 0;
    public static final byte TYPE_STOP = 1;
    public static final byte TYPE_FLASH = 2;

    private final byte type;
    private final UUID playerId;
    private final List<Vec3> path;
    private final float widthMult;
    private final int flashTicks;
    /** true ise omur boyunca yumusakca sonuyor (uzun izler icin). */
    private final boolean longFade;

    private BeamSyncPacket(byte type, UUID playerId, List<Vec3> path,
                           float widthMult, int flashTicks, boolean longFade) {
        this.type = type;
        this.playerId = playerId;
        this.path = path;
        this.widthMult = widthMult;
        this.flashTicks = flashTicks;
        this.longFade = longFade;
    }

    public static BeamSyncPacket channel(UUID playerId, List<Vec3> path, float widthMult) {
        return new BeamSyncPacket(TYPE_CHANNEL, playerId, path, widthMult, 0, false);
    }

    public static BeamSyncPacket stop(UUID playerId) {
        return new BeamSyncPacket(TYPE_STOP, playerId, List.of(), 0, 0, false);
    }

    public static BeamSyncPacket flash(Vec3 origin, Vec3 end, float widthMult, int durationTicks) {
        return new BeamSyncPacket(TYPE_FLASH, new UUID(0, 0),
                List.of(origin, end), widthMult, durationTicks, false);
    }

    public static BeamSyncPacket flashPath(List<Vec3> path, float widthMult,
                                           int durationTicks, boolean longFade) {
        return new BeamSyncPacket(TYPE_FLASH, new UUID(0, 0),
                path, widthMult, durationTicks, longFade);
    }

    public static void encode(BeamSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.type);
        buf.writeUUID(msg.playerId);
        if (msg.type == TYPE_STOP) return;

        buf.writeVarInt(msg.path.size());
        for (Vec3 p : msg.path) {
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
        }
        buf.writeFloat(msg.widthMult);

        if (msg.type == TYPE_FLASH) {
            buf.writeVarInt(msg.flashTicks);
            buf.writeBoolean(msg.longFade);
        }
    }

    public static BeamSyncPacket decode(FriendlyByteBuf buf) {
        byte type = buf.readByte();
        UUID playerId = buf.readUUID();
        if (type == TYPE_STOP) {
            return new BeamSyncPacket(type, playerId, List.of(), 0, 0, false);
        }

        int count = buf.readVarInt();
        List<Vec3> path = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            path.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        float width = buf.readFloat();

        int ticks = 0;
        boolean longFade = false;
        if (type == TYPE_FLASH) {
            ticks = buf.readVarInt();
            longFade = buf.readBoolean();
        }

        return new BeamSyncPacket(type, playerId, path, width, ticks, longFade);
    }

    public static void handle(BeamSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(msg)));
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(BeamSyncPacket msg) {
        switch (msg.type) {
            case TYPE_CHANNEL -> ClientBeamData.setChannelBeam(msg.playerId, msg.path, msg.widthMult);
            case TYPE_STOP -> ClientBeamData.clearChannelBeam(msg.playerId);
            case TYPE_FLASH -> ClientBeamData.addFlashBeam(
                    msg.path, msg.widthMult, msg.flashTicks, msg.longFade);
        }
    }
}
