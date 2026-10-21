package dev.zm.clan.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class ClanLevelChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID clanId;
    private final int previousLevel;
    private final int level;

    public ClanLevelChangeEvent(UUID clanId, int previousLevel, int level) {
        this.clanId = clanId;
        this.previousLevel = previousLevel;
        this.level = level;
    }

    public UUID getClanId() { return clanId; }
    public int getPreviousLevel() { return previousLevel; }
    public int getLevel() { return level; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}