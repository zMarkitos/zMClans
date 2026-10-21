package dev.zm.clan.invitation.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a pending invitation from one player to another to join a clan.
 */
public final class Invitation {

    private final UUID clanId;
    private final UUID targetPlayer;
    private final UUID inviterPlayer;
    private final Instant expiresAt; // null = never expires

    public Invitation(UUID clanId, UUID targetPlayer, UUID inviterPlayer, Instant expiresAt) {
        this.clanId = clanId;
        this.targetPlayer = targetPlayer;
        this.inviterPlayer = inviterPlayer;
        this.expiresAt = expiresAt;
    }

    public UUID getClanId() {
        return clanId;
    }

    public UUID getTargetPlayer() {
        return targetPlayer;
    }

    public UUID getInviterPlayer() {
        return inviterPlayer;
    }

    public UUID getSenderUuid() {
        return inviterPlayer;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** @return {@code true} if this invitation has passed its expiry time. */
    public boolean isExpired() {
        if (expiresAt == null)
            return false;
        return Instant.now().isAfter(expiresAt);
    }
}
