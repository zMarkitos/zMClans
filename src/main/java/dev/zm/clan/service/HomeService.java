package dev.zm.clan.service;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.clan.model.ClanHome;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.zMClans;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages /clan home and /clan sethome with cooldown and warmup support.
 */
public final class HomeService {

    private final zMClans plugin;

    /** player UUID → unix second of last teleport */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * player UUID → warmup runnable (so we can cancel on move or duplicate request)
     */
    private final Map<UUID, BukkitRunnable> warmups = new ConcurrentHashMap<>();

    public enum SetHomeResult {
        OK, NO_PERMISSION, NO_CLAN, LIMIT_REACHED
    }

    public enum TeleportResult {
        OK, NO_HOME, NO_CLAN, WORLD_UNLOADED, COOLDOWN, NO_PERMISSION
    }

    public HomeService(zMClans plugin) {
        this.plugin = plugin;
    }

    public SetHomeResult setHome(Player player) {
        return setHome(player, "default");
    }

    public SetHomeResult setHome(Player player, String name) {
        if (player == null || !player.hasPermission("zmclan.player.sethome"))
            return SetHomeResult.NO_PERMISSION;
        if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.SET_HOME))
            return SetHomeResult.NO_PERMISSION;

        Clan clan = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
        if (clan == null)
            return SetHomeResult.NO_CLAN;

        if (name == null || !name.matches("[A-Za-z0-9_-]{1,24}"))
            return SetHomeResult.NO_PERMISSION;
        int maxAllowed = getMaxHomes(clan);
        if (!clan.getHomes().containsKey(name.toLowerCase(java.util.Locale.ROOT))
                && clan.getHomes().size() >= maxAllowed)
            return SetHomeResult.LIMIT_REACHED;
        clan.setNamedHome(name, new ClanHome(player.getLocation()));
        plugin.getClanService().saveAsync(clan);

        if (plugin.getNotificationService() != null) {
            plugin.getNotificationService().sendNotification(player, "home-set", "home", name);
        }

        return SetHomeResult.OK;
    }

    public boolean deleteHome(Player player, String name) {
        if (player == null || !player.hasPermission("zmclan.player.delhome"))
            return false;
        if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.SET_HOME))
            return false;
        Clan clan = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
        if (clan == null || name == null || !clan.removeNamedHome(name))
            return false;
        plugin.getClanService().saveAsync(clan);
        return true;
    }

    public TeleportResult teleport(Player player) {
        return teleport(player, null);
    }

    public TeleportResult teleport(Player player, String homeName) {
        if (!player.hasPermission("zmclan.player.home"))
            return TeleportResult.NO_PERMISSION;

        Clan clan = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
        if (clan == null)
            return TeleportResult.NO_CLAN;

        if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.USE_HOME))
            return TeleportResult.NO_PERMISSION;

        ClanHome home = homeName == null ? clan.getHome() : clan.getHome(homeName);
        if (home == null)
            return TeleportResult.NO_HOME;
        if (!home.isWorldLoaded())
            return TeleportResult.WORLD_UNLOADED;

        // Cooldown check (bypass perm)
        int cooldownSecs = plugin.getConfigManager().getHomeCooldown();
        if (cooldownSecs > 0 && !player.hasPermission("zmclans.clan.bypass.cooldown")) {
            long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            long remaining = cooldownSecs - (System.currentTimeMillis() / 1000 - last);
            if (remaining > 0) {
                player.sendMessage(plugin.getMessages().get("home-cooldown", "time", String.valueOf(remaining)));
                return TeleportResult.COOLDOWN;
            }
        }

        int warmupSecs = plugin.getConfigManager().getHomeWarmup();
        Location destination = home.toBukkitLocation();

        if (warmupSecs <= 0) {
            doTeleport(player, destination);
            return TeleportResult.OK;
        }

        // Cancel any existing warmup for this player to prevent multiple concurrent
        // tasks
        cancelWarmup(player.getUniqueId());

        // Warmup phase
        Location startLoc = player.getLocation().clone();
        player.sendMessage(plugin.getMessages().get("home-teleporting", "time", String.valueOf(warmupSecs)));

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                warmups.remove(player.getUniqueId());
                if (!player.isOnline()) {
                    return;
                }
                if (!player.hasPermission("zmclan.player.home")
                        || !plugin.getMemberService().hasPermission(player.getUniqueId(),
                                ClanRank.Permission.USE_HOME)) {
                    player.sendMessage(plugin.getMessages().get("no-permission-home"));
                    return;
                }
                Clan current = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
                if (current == null || !current.getId().equals(clan.getId())
                        || current.getHome(homeName == null ? null : homeName) == null) {
                    player.sendMessage(plugin.getMessages().get("home-not-set"));
                    return;
                }
                doTeleport(player, destination);
            }
        };

        warmups.put(player.getUniqueId(), task);
        task.runTaskLater(plugin, 20L * warmupSecs);
        return TeleportResult.OK;
    }

    public TeleportResult teleportSharedHome(Player player, UUID allyClanId, String homeName) {
        if (player == null || !player.hasPermission("zmclan.player.home")
                || !player.hasPermission("zmclan.player.homes"))
            return TeleportResult.NO_PERMISSION;
        if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.USE_HOME))
            return TeleportResult.NO_PERMISSION;
        Clan own = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
        Clan ally = plugin.getClanService().findById(allyClanId).orElse(null);
        if (own == null)
            return TeleportResult.NO_CLAN;
        if (ally == null || !own.isAlly(allyClanId) || !ally.isAlly(own.getId()) || !ally.isAllyHomesVisible())
            return TeleportResult.NO_PERMISSION;
        ClanHome home = ally.getHome(homeName);
        if (home == null)
            return TeleportResult.NO_HOME;
        if (!home.isWorldLoaded())
            return TeleportResult.WORLD_UNLOADED;
        int cooldownSecs = plugin.getConfigManager().getHomeCooldown();
        if (cooldownSecs > 0 && !player.hasPermission("zmclans.clan.bypass.cooldown")) {
            long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            long remaining = cooldownSecs - (System.currentTimeMillis() / 1000 - last);
            if (remaining > 0) {
                player.sendMessage(plugin.getMessages().get("home-cooldown", "time", String.valueOf(remaining)));
                return TeleportResult.COOLDOWN;
            }
        }
        Location destination = home.toBukkitLocation();
        int warmupSecs = plugin.getConfigManager().getHomeWarmup();
        if (warmupSecs <= 0) {
            doSharedTeleport(player, destination, ally.getName());
            return TeleportResult.OK;
        }
        cancelWarmup(player.getUniqueId());
        player.sendMessage(plugin.getMessages().get("home-teleporting", "time", String.valueOf(warmupSecs)));
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                warmups.remove(player.getUniqueId());
                if (!player.isOnline())
                    return;
                if (!player.hasPermission("zmclan.player.home") || !player.hasPermission("zmclan.player.homes")
                        || !plugin.getMemberService().hasPermission(player.getUniqueId(),
                                ClanRank.Permission.USE_HOME)) {
                    player.sendMessage(plugin.getMessages().get("no-permission-home"));
                    return;
                }
                Clan currentOwn = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
                Clan currentAlly = plugin.getClanService().findById(allyClanId).orElse(null);
                if (currentOwn == null || currentAlly == null || !currentOwn.isAlly(allyClanId)
                        || !currentAlly.isAlly(currentOwn.getId()) || !currentAlly.isAllyHomesVisible()) {
                    player.sendMessage(plugin.getMessages().get("ally-home-unavailable"));
                    return;
                }
                doSharedTeleport(player, destination, currentAlly.getName());
            }
        };
        warmups.put(player.getUniqueId(), task);
        task.runTaskLater(plugin, 20L * warmupSecs);
        return TeleportResult.OK;
    }

    private void doSharedTeleport(Player player, Location destination, String clanName) {
        if (player.isOnline() && destination != null) {
            player.teleport(destination);
            player.sendMessage(plugin.getMessages().get("ally-home-teleported", "clan", clanName));
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() / 1000);
        }
    }

    /**
     * Called when a player moves block. If cancel-on-move is enabled and the player
     * is currently in a warmup, cancels it immediately with a message.
     */
    public void checkMoveCancel(Player player) {
        if (!plugin.getConfigManager().isCancelOnMove() || player == null)
            return;
        UUID uuid = player.getUniqueId();
        BukkitRunnable r = warmups.remove(uuid);
        if (r != null) {
            try {
                r.cancel();
            } catch (IllegalStateException ignored) {
            }
            player.sendMessage(plugin.getMessages().get("home-cancelled"));
        }
    }

    private void doTeleport(Player player, Location destination) {
        if (player.isOnline() && destination != null) {
            player.teleport(destination);
            player.sendMessage(plugin.getMessages().get("home-teleported"));
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() / 1000);
            if (plugin.getNotificationService() != null) {
                plugin.getNotificationService().sendNotification(player, "home-teleport", "home", "home");
            }
        }
    }

    /** Cancel warmup if player disconnects or triggers a new teleport. */
    public void cancelWarmup(UUID uuid) {
        if (uuid == null)
            return;
        BukkitRunnable r = warmups.remove(uuid);
        if (r != null) {
            try {
                r.cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public void forgetPlayer(UUID uuid) {
        if (uuid == null)
            return;
        cancelWarmup(uuid);
        cooldowns.remove(uuid);
    }

    /**
     * Returns the maximum number of homes allowed for the clan based on its level.
     * Configured in levels.yml under progression.homes-per-level.
     * Falls back to config.yml home.max-homes if not configured per level.
     */
    public int getMaxHomes(Clan clan) {
        if (clan == null)
            return plugin.getConfigManager().getMaxHomes();
        long limit = plugin.getProgressionService().getLevelLimit(clan, "homes",
                plugin.getConfigManager().getMaxHomes());
        return (int) Math.min(Integer.MAX_VALUE, limit);
    }

    /**
     * Convenience method using the player's own clan.
     */
    public int getMaxHomes(Player player) {
        Clan clan = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
        return getMaxHomes(clan);
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null)
            return false;
        if (a.getWorld() != b.getWorld())
            return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
