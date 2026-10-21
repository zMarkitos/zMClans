package dev.zm.clan.api;

import java.time.Instant;
import java.util.UUID;

public record ClanMemberSnapshot(UUID playerId, UUID clanId, String rankId, Instant joinedAt,
                                 long kills, long deaths, double kdr) {}