package dev.zm.clan.member.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single clan member's persistent data.
 * One entry per player-in-clan relationship.
 */
public final class ClanMember {

    private final UUID playerUUID;
    private final UUID clanId;
    private String rankId;
    private final Instant joinedAt;
    private boolean chatEnabled; // whether /clan chat is toggled on
    private long kills;
    private long deaths;

    // Transient — not persisted, set at runtime
    private boolean online;

    public ClanMember(UUID playerUUID, UUID clanId, String rankId, Instant joinedAt) {
        this.playerUUID = playerUUID;
        this.clanId = clanId;
        this.rankId = rankId;
        this.joinedAt = joinedAt;
        this.chatEnabled = false;
        this.online = false;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public UUID getPlayerUuid() {
        return playerUUID;
    }

    public UUID getClanId() {
        return clanId;
    }

    public String getRankId() {
        return rankId;
    }

    public String getRank() {
        return rankId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public boolean isChatEnabled() {
        return chatEnabled;
    }

    public boolean isOnline() {
        return online;
    }

    public long getKills() {
        return kills;
    }

    public long getDeaths() {
        return deaths;
    }

    public double getKdr() {
        return (kills + 1.0d) / (deaths + 1.0d);
    }

    public void setRankId(String rankId) {
        this.rankId = rankId;
    }

    public void setChatEnabled(boolean enabled) {
        this.chatEnabled = enabled;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public void setStats(long kills, long deaths) {
        this.kills = Math.max(0L, kills);
        this.deaths = Math.max(0L, deaths);
    }

    public void addKill() {
        if (kills < Long.MAX_VALUE)
            kills++;
    }

    public void addDeath() {
        if (deaths < Long.MAX_VALUE)
            deaths++;
    }

    public ClanMember copyForPersistence() {
        ClanMember copy = new ClanMember(playerUUID, clanId, rankId, joinedAt);
        copy.setStats(kills, deaths);
        return copy;
    }
}
