package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.render.cinematic.CinematicClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sinematik senkronu. Cekim tanimlari istemcide zaten var; agdan sadece
 * "hangi sinematik, kacinci tick, sahne nerede" gidiyor — bant genisligi minimum.
 *
 * Aktorler ENTITY ID ile gonderiliyor: istemci varligin kendi
 * interpolasyonunu kullanabildigi icin kamera 20Hz'e takilmiyor.
 */
public class CinematicSyncPacket {

    private final boolean active;
    private final int cinematicIndex;
    private final int tick;
    private final int attackerId;
    private final int targetId;
    private final Vec3 stageOrigin;
    private final Vec3 stageForward;
    private final float darkness;
    private final float span;

    public CinematicSyncPacket(boolean active, int cinematicIndex, int tick,
                               int attackerId, int targetId,
                               Vec3 stageOrigin, Vec3 stageForward,
                               float darkness, float span) {
        this.active = active;
        this.cinematicIndex = cinematicIndex;
        this.tick = tick;
        this.attackerId = attackerId;
        this.targetId = targetId;
        this.stageOrigin = stageOrigin;
        this.stageForward = stageForward;
        this.darkness = darkness;
        this.span = span;
    }

    public static CinematicSyncPacket inactive() {
        return new CinematicSyncPacket(false, -1, 0, -1, -1,
                Vec3.ZERO, Vec3.ZERO, 0f, 1.5f);
    }

    public static void encode(CinematicSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
        if (!msg.active) return;

        buf.writeVarInt(msg.cinematicIndex);
        buf.writeVarInt(msg.tick);
        buf.writeVarInt(msg.attackerId);
        buf.writeVarInt(msg.targetId);
        buf.writeDouble(msg.stageOrigin.x);
        buf.writeDouble(msg.stageOrigin.y);
        buf.writeDouble(msg.stageOrigin.z);
        buf.writeFloat((float) msg.stageForward.x);
        buf.writeFloat((float) msg.stageForward.z);
        buf.writeFloat(msg.darkness);
        buf.writeFloat(msg.span);
    }

    public static CinematicSyncPacket decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return inactive();

        int index = buf.readVarInt();
        int tick = buf.readVarInt();
        int attacker = buf.readVarInt();
        int target = buf.readVarInt();
        Vec3 origin = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float fx = buf.readFloat();
        float fz = buf.readFloat();
        float dark = buf.readFloat();
        float span = buf.readFloat();

        return new CinematicSyncPacket(true, index, tick, attacker, target,
                origin, new Vec3(fx, 0, fz), dark, span);
    }

    public static void handle(CinematicSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    if (msg.active) {
                        CinematicClient.update(msg.cinematicIndex, msg.tick,
                                msg.attackerId, msg.targetId,
                                msg.stageOrigin, msg.stageForward,
                                msg.darkness, msg.span);
                    } else {
                        CinematicClient.stop();
                    }
                }));
        ctx.get().setPacketHandled(true);
    }
}
