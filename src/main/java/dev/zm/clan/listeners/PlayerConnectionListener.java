package dev.zm.clan.listeners;

import dev.zm.clan.zMClans;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player join, quit, and movement events.
 */
public final class PlayerConnectionListener implements Listener {

    private final zMClans plugin;

    public PlayerConnectionListener(zMClans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Ensure clan membership is cached (triggers DB load if not in cache)
        plugin.getClanService().findByPlayer(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Cancel pending home teleport warmup
        plugin.getHomeService().forgetPlayer(player.getUniqueId());
        // Remove clan chat toggle state — fresh on next login
        if (plugin.getClanChatListener() != null) {
            plugin.getClanChatListener().removePlayer(player.getUniqueId());
        }
        // Cancel pending chat prompt if any
        if (plugin.getChatPromptListener() != null) {
            plugin.getChatPromptListener().clear(player.getUniqueId());
        }
        // Clean up friendly-fire rate limiter
        if (plugin.getFriendlyFireListener() != null) {
            plugin.getFriendlyFireListener().removePlayer(player.getUniqueId());
        }
        // Clean up open GUI tracking
        plugin.getMenuManager().onQuit(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null)
            return;
        // Ignore rotation-only movement (yaw / pitch)
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        plugin.getHomeService().checkMoveCancel(event.getPlayer());
    }
}
