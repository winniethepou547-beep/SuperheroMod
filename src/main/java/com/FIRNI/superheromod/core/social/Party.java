package com.FIRNI.superheromod.core.social;

import java.util.*;

public class Party {

    private final UUID partyId;
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    private static final int MAX_SIZE = 4;

    public Party(UUID leader) {
        this.partyId = UUID.randomUUID();
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID getPartyId() { return partyId; }
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
