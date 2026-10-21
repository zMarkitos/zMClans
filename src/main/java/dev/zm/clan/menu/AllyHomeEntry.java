package dev.zm.clan.menu;

import dev.zm.clan.clan.model.ClanHome;
import java.util.UUID;

/**
 * Safe reference to a home owned by an allied clan; permissions are rechecked
 * on click.
 */
public record AllyHomeEntry(UUID clanId, String clanName, String homeName, ClanHome home) {
}
