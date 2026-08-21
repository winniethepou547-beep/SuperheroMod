package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.render.ClientSandShapeData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sunucu -> istemci: o an cizilecek SOMUT kum sekilleri.
 *
 * Partikuller kuvveti anlatiyor ama KUTLE anlatmiyor: kullanici hem cekme
 * elinin hem de ziplama kayasinin "somut" olmasini istedi. Sekiller
 * istemcide geometri olarak ciziliyor; sunucu sadece konum, yon ve ilerleme
 * gonderiyor.
 *
 * Sekil GERCEK BLOK degil — haritaya kalici yapi yazilmiyor.
 */
public class SandShapeSyncPacket {

    public static final byte TYPE_HAND = 0;
    public static final byte TYPE_PILLAR = 1;

    /**
     * @param type    HAND / PILLAR
     * @param x,y,z   sekil tabaninin dunya konumu
     * @param yaw     sekil yonu (derece)
     * @param grow    0..1 yerden cikma ilerlemesi
     * @param curl    el icin parmak kapanma miktari; sutun icin kullanilmaz
     * @param sink    0..1 yuzeye gomulme (1 = tamamen gorunmez)
     */
    public record Shape(byte type, double x, double y, double z,
                        float yaw, float grow, float curl, float sink) {}

    private final List<Shape> shapes;

    public SandShapeSyncPacket(List<Shape> shapes) {
        this.shapes = shapes;
    }

    public static void encode(SandShapeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.shapes.size());
        for (Shape s : msg.shapes) {
            buf.writeByte(s.type());
            buf.writeDouble(s.x());
            buf.writeDouble(s.y());
            buf.writeDouble(s.z());
            buf.writeFloat(s.yaw());
            buf.writeFloat(s.grow());
            buf.writeFloat(s.curl());
            buf.writeFloat(s.sink());
        }
    }

    public static SandShapeSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Shape> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Shape(buf.readByte(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat()));
        }
        return new SandShapeSyncPacket(list);
    }

    public static void handle(SandShapeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSandShapeData.set(msg.shapes)));
        ctx.get().setPacketHandled(true);
    }
}
