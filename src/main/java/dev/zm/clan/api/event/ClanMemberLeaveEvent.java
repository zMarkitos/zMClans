package dev.zm.clan.api.event;

import dev.zm.clan.api.ClanSnapshot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class ClanMemberLeaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ClanSnapshot clan;
    private final UUID playerId;

    public ClanMemberLeaveEvent(ClanSnapshot clan, UUID playerId) {
        this.clan = clan;
        this.playerId = playerId;
    }

    public ClanSnapshot getClan() { return clan; }
    public UUID getPlayerId() { return playerId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}