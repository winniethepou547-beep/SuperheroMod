package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.render.ClientSandArmData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sunucu -> istemci: kimin kolu ne kadar uzamis.
 *
 * Konum GONDERILMIYOR. Kol dunya uzayinda degil, oyuncu modelinin bir
 * katmani olarak omuz donusumunun icinde ciziliyor; konumu kolun kendisi
 * belirliyor. Onceki surum dunya koordinati gonderiyordu ve kol oyuncu
 * kipirdadiginda govdeden kopuyordu.
 *
 * Kimlik olarak ENTITY ID kullaniliyor: istemci tarafinda oyuncuyu
 * bulmanin en ucuz yolu ve UUID'den cok daha kisa.
 */
public class SandArmSyncPacket {

    public record Entry(int entityId, float length, boolean active) {}

    private final List<Entry> entries;

    public SandArmSyncPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public static void encode(SandArmSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (Entry e : msg.entries) {
            buf.writeVarInt(e.entityId());
            buf.writeFloat(e.length());
            buf.writeBoolean(e.active());
        }
    }

    public static SandArmSyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> list = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readFloat(), buf.readBoolean()));
        }
        return new SandArmSyncPacket(list);
    }

    public static void handle(SandArmSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            Map<Integer, ClientSandArmData.Arm> map = new HashMap<>();
            for (Entry e : msg.entries) {
                map.put(e.entityId(), new ClientSandArmData.Arm(e.length(), e.active()));
            }
            ClientSandArmData.set(map);
        }));
        ctx.get().setPacketHandled(true);
    }
}
