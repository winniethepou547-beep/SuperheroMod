package com.FIRNI.superheromod.network.packet;

import com.FIRNI.superheromod.client.ClientMatchHistory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class MatchHistorySyncPacket {

    private final List<Entry> entries;

    public MatchHistorySyncPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public static void encode(MatchHistorySyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.entries.size());
        for (Entry e : pkt.entries) {
            buf.writeUtf(e.mode);
            buf.writeUtf(e.opponent);
            buf.writeBoolean(e.won);
            buf.writeInt(e.ratingChange);
            buf.writeLong(e.timestamp);
        }
    }

    public static MatchHistorySyncPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readInt(), buf.readLong()));
        }
        return new MatchHistorySyncPacket(entries);
    }

    public static void handle(MatchHistorySyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ClientMatchHistory.update(pkt.entries));
        ctx.setPacketHandled(true);
    }

    public record Entry(String mode, String opponent, boolean won, int ratingChange, long timestamp) {}
}
