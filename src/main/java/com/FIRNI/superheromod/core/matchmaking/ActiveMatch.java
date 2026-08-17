package com.FIRNI.superheromod.core.matchmaking;

import java.util.*;

/**
 * Devam eden bir PVP macinin durumu. Round-bazli skor sistemi:
 *
 * 1v1: Her olum = rakibe +1 round. Olen oyuncu spawn'a geri donup devam eder.
 *      Ilk ROUNDS_TO_WIN'e ulasan kazanir (ornegin 3-0 veya 3-2).
 *
 * 2v2: Bir tarafin tum oyunculari olunce = karsi tarafa +1 round.
 *      Round bitince herkes spawn'a doner. Ilk ROUNDS_TO_WIN'e ulasan takim kazanir.
 */
public class ActiveMatch {

    public static final int ROUNDS_TO_WIN_1V1 = 3;
    public static final int ROUNDS_TO_WIN_2V2 = 2;

    private final UUID matchId;
    private final MatchMode mode;
    private final GameMap map;

    private final List<UUID> teamA;
    private final List<UUID> teamB;

    private int roundsA = 0;
    private int roundsB = 0;

    // 2v2'de bu round'da olen oyuncular (round bitince temizlenir)
    private final Set<UUID> deadThisRound = new HashSet<>();

    // FF (forfeit) votes for 2v2
    private final Set<UUID> ffVotesA = new HashSet<>();
    private final Set<UUID> ffVotesB = new HashSet<>();

    private boolean finished = false;
    private boolean teamAWon = false;

    public ActiveMatch(UUID matchId, MatchMode mode, GameMap map, List<UUID> teamA, List<UUID> teamB) {
        this.matchId = matchId;
        this.mode = mode;
        this.map = map;
        this.teamA = new ArrayList<>(teamA);
        this.teamB = new ArrayList<>(teamB);
    }

    public UUID getMatchId() { return matchId; }
    public MatchMode getMode() { return mode; }
    public GameMap getMap() { return map; }
    public List<UUID> getTeamA() { return Collections.unmodifiableList(teamA); }
    public List<UUID> getTeamB() { return Collections.unmodifiableList(teamB); }
    public boolean isFinished() { return finished; }
    public boolean didTeamAWin() { return teamAWon; }
    public int getRoundsA() { return roundsA; }
    public int getRoundsB() { return roundsB; }

    public List<UUID> getAllParticipants() {
        List<UUID> all = new ArrayList<>(teamA);
        all.addAll(teamB);
        return all;
    }

    public List<UUID> getWinningTeam() {
        return teamAWon ? teamA : teamB;
    }

    public List<UUID> getLosingTeam() {
        return teamAWon ? teamB : teamA;
    }

    public boolean isParticipant(UUID playerId) {
        return teamA.contains(playerId) || teamB.contains(playerId);
    }

    public boolean isTeamA(UUID playerId) {
        return teamA.contains(playerId);
    }

    /** Bu oyuncu bu round'da oldu mu? (2v2 icin) */
    public boolean isDeadThisRound(UUID playerId) {
        return deadThisRound.contains(playerId);
    }

    private int roundsToWin() {
        return mode == MatchMode.TWO_V_TWO ? ROUNDS_TO_WIN_2V2 : ROUNDS_TO_WIN_1V1;
    }

    /**
     * Verilen oyuncunun skorunu dondurur (bu oyuncunun takiminin round sayisi).
     */
    public int getMyScore(UUID playerId) {
        return isTeamA(playerId) ? roundsA : roundsB;
    }

    /**
     * Verilen oyuncunun rakibinin skorunu dondurur.
     */
    public int getOpponentScore(UUID playerId) {
        return isTeamA(playerId) ? roundsB : roundsA;
    }

    /** 1v1 icin: rakibin UUID'si. */
    public UUID getOpponent(UUID playerId) {
        if (teamA.contains(playerId)) return teamB.isEmpty() ? null : teamB.get(0);
        if (teamB.contains(playerId)) return teamA.isEmpty() ? null : teamA.get(0);
        return null;
    }

    /**
     * Bir oyuncu oldugunde cagirilir.
     * @return ROUND_WON eger bir round kazanildiysa, MATCH_WON eger mac bittiyse, CONTINUE devam ediyorsa
     */
    public DeathResult recordDeath(UUID deadPlayerId) {
        if (finished) return DeathResult.CONTINUE;
        if (!isParticipant(deadPlayerId)) return DeathResult.CONTINUE;

        if (mode == MatchMode.ONE_V_ONE) {
            // 1v1: her olum = rakibe +1 round
            if (isTeamA(deadPlayerId)) {
                roundsB++;
            } else {
                roundsA++;
            }
            if (roundsA >= roundsToWin() || roundsB >= roundsToWin()) {
                finished = true;
                teamAWon = roundsA >= roundsToWin();
                return DeathResult.MATCH_WON;
            }
            return DeathResult.ROUND_WON;
        }

        if (mode == MatchMode.TWO_V_TWO) {
            deadThisRound.add(deadPlayerId);
            boolean teamAAllDead = teamA.stream().allMatch(deadThisRound::contains);
            boolean teamBAllDead = teamB.stream().allMatch(deadThisRound::contains);

            if (teamAAllDead) {
                roundsB++;
                deadThisRound.clear();
                if (roundsB >= roundsToWin()) {
                    finished = true;
                    teamAWon = false;
                    return DeathResult.MATCH_WON;
                }
                return DeathResult.ROUND_WON;
            }
            if (teamBAllDead) {
                roundsA++;
                deadThisRound.clear();
                if (roundsA >= roundsToWin()) {
                    finished = true;
                    teamAWon = true;
                    return DeathResult.MATCH_WON;
                }
                return DeathResult.ROUND_WON;
            }
            // 2v2'de sadece bir kisi oldu, round devam ediyor
            return DeathResult.CONTINUE;
        }

        return DeathResult.CONTINUE;
    }

    /**
     * FF (forfeit) verme. 1v1: aninda maglup. 2v2: 2/2 olunca maglup.
     * @return null = oy kaydedildi ama mac bitmedi (1/2), DeathResult.MATCH_WON = mac bitti
     */
    public ForfeitResult forfeit(UUID playerId) {
        if (finished) return null;
        if (!isParticipant(playerId)) return null;

        if (mode == MatchMode.ONE_V_ONE) {
            finished = true;
            teamAWon = !isTeamA(playerId);
            return new ForfeitResult(true, 1, 1);
        }

        // 2v2
        Set<UUID> votes = isTeamA(playerId) ? ffVotesA : ffVotesB;
        votes.add(playerId);
        List<UUID> team = isTeamA(playerId) ? teamA : teamB;
        int needed = team.size();
        int current = votes.size();

        if (current >= needed) {
            finished = true;
            teamAWon = !isTeamA(playerId);
            return new ForfeitResult(true, current, needed);
        }

        return new ForfeitResult(false, current, needed);
    }

    public record ForfeitResult(boolean matchEnded, int votes, int needed) {}

    public enum DeathResult {
        CONTINUE,    // 2v2'de bir kisi oldu ama round bitmedi
        ROUND_WON,   // Round kazanildi, mac devam ediyor
        MATCH_WON    // Mac bitti
    }
}
