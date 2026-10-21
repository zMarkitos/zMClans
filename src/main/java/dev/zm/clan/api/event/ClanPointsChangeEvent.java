package dev.zm.clan.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class ClanPointsChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID clanId;
    private final long previousPoints;
    private final long points;
    private final long delta;

    public ClanPointsChangeEvent(UUID clanId, long previousPoints, long points, long delta) {
        this.clanId = clanId;
        this.previousPoints = previousPoints;
        this.points = points;
        this.delta = delta;
    }

    public UUID getClanId() { return clanId; }
    public long getPreviousPoints() { return previousPoints; }
    public long getPoints() { return points; }
    public long getDelta() { return delta; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}