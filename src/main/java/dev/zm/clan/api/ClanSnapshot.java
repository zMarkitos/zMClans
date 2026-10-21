package dev.zm.clan.api;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ClanSnapshot(UUID id, String name, String tag, String description, UUID leaderId,
                           Instant createdAt, int memberCount, int maxMembers, long points,
                           int level, long kills, long deaths, double kdr,
                           Set<UUID> allies, Set<UUID> rivals, Map<String, ClanHomeSnapshot> homes) {
    public ClanSnapshot {
        allies = Set.copyOf(allies);
        rivals = Set.copyOf(rivals);
        homes = Map.copyOf(homes);
    }
}