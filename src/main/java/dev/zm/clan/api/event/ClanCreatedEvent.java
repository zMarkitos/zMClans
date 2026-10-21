package dev.zm.clan.api.event;

import dev.zm.clan.api.ClanSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class ClanCreatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ClanSnapshot clan;
    private final UUID creatorId;

    public ClanCreatedEvent(ClanSnapshot clan, UUID creatorId) {
        this.clan = clan;
        this.creatorId = creatorId;
    }

    public ClanSnapshot getClan() { return clan; }
    public UUID getCreatorId() { return creatorId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}