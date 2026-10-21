package dev.zm.clan.task;

import dev.zm.clan.zMClans;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class VersionChecker implements Listener {

    private static final boolean UPDATE_CHECK_ENABLED = true;
    private static final int SPIGOT_RESOURCE_ID = 139830;

    private final zMClans plugin;
    private final String currentVersion;
    private volatile String latestVersion = null;
    private volatile boolean updateAvailable = false;

    public VersionChecker(zMClans plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void check() {
        if (!UPDATE_CHECK_ENABLED)
            return;
        if (SPIGOT_RESOURCE_ID <= 0) {
            plugin.getLogger()
                    .warning("Update checker is enabled, but SPIGOT_RESOURCE_ID is not set in VersionChecker.");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection con = null;
            try {
                URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + SPIGOT_RESOURCE_ID);
                con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);
                con.setRequestMethod("GET");
                con.setRequestProperty("User-Agent", "zMClans/" + currentVersion);

                int status = con.getResponseCode();
                if (status == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                        String fetched = br.readLine();
                        if (fetched != null
                                && fetched.trim().matches("[vV]?[0-9]+(?:\\.[0-9]+){1,3}(?:[-+][A-Za-z0-9.-]+)?")) {
                            latestVersion = fetched.trim().replaceFirst("^[vV]", "");
                            if (compareVersions(latestVersion, currentVersion) > 0) {
                                updateAvailable = true;
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    logUpdateToConsole();
                                    notifyOnlineAdmins();
                                });
                            } else {
                                Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().info(
                                        plugin.getMessages().get("version-up-to-date", "version", currentVersion)));
                            }
                        } else {
                            plugin.getLogger().warning("Spigot returned an invalid zMClans version response.");
                        }
                    }
                } else {
                    plugin.getLogger().warning("Spigot version check returned HTTP " + status + ".");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates: " + e.getMessage());
            } finally {
                if (con != null)
                    con.disconnect();
            }
        });
    }

    private static int compareVersions(String left, String right) {
        List<Integer> a = versionParts(left);
        List<Integer> b = versionParts(right);
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            int av = i < a.size() ? a.get(i) : 0;
            int bv = i < b.size() ? b.get(i) : 0;
            int comparison = Integer.compare(av, bv);
            if (comparison != 0)
                return comparison;
        }
        return 0;
    }

    private static List<Integer> versionParts(String version) {
        List<Integer> parts = new ArrayList<>();
        for (String part : version.replaceFirst("^[vV]", "").split("[.+-]")) {
            String digits = part.replaceAll("\\D.*$", "");
            if (digits.isEmpty())
                break;
            try {
                parts.add(Integer.parseInt(digits));
            } catch (NumberFormatException ignored) {
                parts.add(Integer.MAX_VALUE);
            }
        }
        return parts;
    }

    private void logUpdateToConsole() {
        String rawMessage = plugin.getMessages().get("version-update-console-header",
                "current", currentVersion, "latest", latestVersion);

        String cleanMessage = PlainTextComponentSerializer.plainText()
                .serialize(LegacyComponentSerializer.legacySection().deserialize(rawMessage));

        plugin.getLogger().warning(cleanMessage);
        plugin.getLogger().warning("https://www.spigotmc.org/resources/" + SPIGOT_RESOURCE_ID);
    }

    private void notifyAdmin(Player player) {
        if (!updateAvailable || latestVersion == null)
            return;

        String msg = plugin.getMessages().get("version-update-chat",
                "current", currentVersion, "latest", latestVersion);

        player.sendMessage(LegacyComponentSerializer.legacySection().deserialize(msg));
    }

    private void notifyOnlineAdmins() {
        if (!updateAvailable)
            return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("zmclans.admin.reload")) {
                notifyAdmin(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable || !event.getPlayer().hasPermission("zmclans.admin.reload"))
            return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> notifyAdmin(event.getPlayer()), 20L);
    }
}