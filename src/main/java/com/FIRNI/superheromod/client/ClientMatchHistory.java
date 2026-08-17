package com.FIRNI.superheromod.client;

import com.FIRNI.superheromod.network.packet.MatchHistorySyncPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClientMatchHistory {

    private static List<MatchHistorySyncPacket.Entry> entries = Collections.emptyList();

    private ClientMatchHistory() {}

    public static void update(List<MatchHistorySyncPacket.Entry> newEntries) {
        entries = new ArrayList<>(newEntries);
    }

    public static List<MatchHistorySyncPacket.Entry> getEntries() {
        return entries;
    }
}
