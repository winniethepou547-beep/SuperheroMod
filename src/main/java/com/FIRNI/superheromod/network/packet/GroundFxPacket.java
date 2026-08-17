package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.render.ClientGroundFxData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Yer efektleri paketi. Coklu efekt TEK pakette gider — eskiden blok basina
 * paket gonderiliyordu ve sunucu-istemci trafigini bogyordu.
 */
public class GroundFxPacket {

    // Sunucu tarafi bu sabitleri kullanir; istemci sinifina bagimlilik olmasin.
    public static final byte KIND_CONE = 0;
    public static final byte KIND_SCORCH = 1;

    public record Entry(byte kind, Vec3 pos, Vec3 dir, float size, float charge, int ticks) {}

    private final List<Entry> entries;

    public GroundFxPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public static void encode(GroundFxPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (Entry e : msg.entries) {
            buf.writeByte(e.kind());
            buf.writeDouble(e.pos().x);
            buf.writeDouble(e.pos().y);
            buf.writeDouble(e.pos().z);
            buf.writeFloat((float) e.dir().x);
            buf.writeFloat((float) e.dir().y);
            buf.writeFloat((float) e.dir().z);
            buf.writeFloat(e.size());
            buf.writeFloat(e.charge());
            buf.writeVarInt(e.ticks());
        }
    }

    public static GroundFxPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            byte kind = buf.readByte();
            Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3 dir = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
            float size = buf.readFloat();
            float charge = buf.readFloat();
            int ticks = buf.readVarInt();
            list.add(new Entry(kind, pos, dir, size, charge, ticks));
        }
        return new GroundFxPacket(list);
    }

    public static void handle(GroundFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    ClientGroundFxData.Kind[] kinds = ClientGroundFxData.Kind.values();
                    for (Entry e : msg.entries) {
                        if (e.kind() < 0 || e.kind() >= kinds.length) continue;
                        var fx = new ClientGroundFxData.Fx(
                                kinds[e.kind()], e.pos(), e.dir(),
                                e.size(), e.ticks(), e.pos().hashCode() * 31L + e.kind());
                        fx.charge = e.charge();
                        ClientGroundFxData.add(fx);
                    }
                }));
        ctx.get().setPacketHandled(true);
    }
}
