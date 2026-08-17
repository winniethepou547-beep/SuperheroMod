package com.FIRNI.superheromod.core.social;

import java.util.*;

public class Clan {

    private static final int MAX_SIZE = 6;

    private final UUID clanId;
    private final String name;
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Clan(String name, UUID leader) {
        this.clanId = UUID.randomUUID();
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID getClanId() { return clanId; }
    public String getName() { return name; }
    public UUID getLeader() { return leader; }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
    public int getSize() { return members.size(); }
    public boolean isFull() { return members.size() >= MAX_SIZE; }
    public boolean isLeader(UUID playerId) { return leader.equals(playerId); }
    public boolean isMember(UUID playerId) { return members.contains(playerId); }

    public boolean addMember(UUID playerId) {
        if (isFull()) return false;
        return members.add(playerId);
    }

    public boolean removeMember(UUID playerId) {
        if (!members.remove(playerId)) return false;
        if (leader.equals(playerId) && !members.isEmpty()) {
            leader = members.iterator().next();
        }
        return true;
    }

    public void promote(UUID newLeader) {
        if (members.contains(newLeader)) {
            this.leader = newLeader;
        }
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }
}
