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
 * Sekiller partikul degil GEOMETRI olarak ciziliyor. Partikuller kuvveti
 * anlatiyor ama KUTLE anlatmiyor; ayrica kullanici gostergelerin partikulle
 * degil cizilerek yapilmasini istedi (Cyclops ultisindeki gibi).
 */
public class SandShapeSyncPacket {

    public static final byte TYPE_HAND = 0;
    /** Cekme alani gostergesi -- kirmizi ok. */
    public static final byte TYPE_ARROW = 2;
    /** Yetenegin birakti kum alani. */
    public static final byte TYPE_PATCH = 3;

    /**
     * @param id    kareler arasi ESLESTIRME kimligi.
     *              Yumusatma bunun uzerinden calisiyor: sunucu saniyede 20
     *              guncelleme gonderiyor, istemci 60+ kare ciziyor. Kimlik
     *              olmasa hangi seklin hangisinin devami oldugu bilinemez
     *              ve ara deger hesaplanamazdi -- goruntu "kasiyor" gibi
     *              gorunuyordu.
     * @param type  HAND / ARROW / PATCH
     * @param yaw   sekil yonu (derece)
     * @param grow  0..1 olusma ilerlemesi (ok icin parlaklik)
     * @param curl  el icin parmak kapanmasi; digerlerinde yaricap
     * @param sink  0..1 yuzeye gomulme (1 = gorunmez)
     */
    public record Shape(int id, byte type, double x, double y, double z,
                        float yaw, float grow, float curl, float sink) {}

    private final List<Shape> shapes;

    public SandShapeSyncPacket(List<Shape> shapes) {
        this.shapes = shapes;
    }

    public static void encode(SandShapeSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.shapes.size());
        for (Shape s : msg.shapes) {
            buf.writeVarInt(s.id());
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
            list.add(new Shape(buf.readVarInt(), buf.readByte(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        return new SandShapeSyncPacket(list);
    }

    public static void handle(SandShapeSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientSandShapeData.set(msg.shapes)));
        ctx.get().setPacketHandled(true);
    }
}
