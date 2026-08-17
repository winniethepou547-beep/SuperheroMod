package com.FIRNI.superheromod.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sunucu -> istemci: o an gorunen kum duvarlari (hayali onizleme dahil).
 *
 * Duvarlar gercek entity degil, sunucu tarafinda tutulan kontrollu nesneler;
 * bu yuzden gorseli elle senkronlaniyor. Her tick tek pakette gidiyor.
 */
public class SandWallSyncPacket {

    /**
     * @param preview true ise hayali onizleme (saydam), false ise gercek duvar
     * @param spike   0..1 dis yuzeydeki dikenlerin cikma orani
     * @param health  0..1 duvarin kalan dayanikliligi
     */
    public record Entry(Vec3 center, float yaw, float halfWidth, float halfHeight,
                        float halfDepth, float spike, float health, boolean preview) {}

    private final List<Entry> walls;

    public SandWallSyncPacket(List<Entry> walls) {
        this.walls = walls;
    }

    public static void encode(SandWallSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.walls.size());
        for (Entry e : msg.walls) {
            buf.writeDouble(e.center().x);
            buf.writeDouble(e.center().y);
            buf.writeDouble(e.center().z);
            buf.writeFloat(e.yaw());
            buf.writeFloat(e.halfWidth());
            buf.writeFloat(e.halfHeight());
            buf.writeFloat(e.halfDepth());
            buf.writeFloat(e.spike());
            buf.writeFloat(e.health());
            buf.writeBoolean(e.preview());
        }
    }

    public static SandWallSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        }
        return new SandWallSyncPacket(list);
    }

    public static void handle(SandWallSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        com.FIRNI.superheromod.client.render.ClientSandWallData.set(msg.walls)));
        ctx.get().setPacketHandled(true);
    }
}
