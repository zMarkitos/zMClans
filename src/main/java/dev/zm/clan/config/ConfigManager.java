package dev.zm.clan.config;

import dev.zm.clan.zMClans;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages all plugin configuration files.
 * Provides typed accessors so no code outside this class
 * hard-codes config keys.
 */
public final class ConfigManager {

    private final zMClans plugin;
    private FileConfiguration config;
    private FileConfiguration notificationsConfig;

    // Cached values for hot-path access
    private String language;
    private double creationCost;
    private int inviteExpireSeconds;
    private int homeCooldown;
    private int homeWarmup;
    private boolean cancelOnMove;
    private boolean friendlyFire;
    private String storageType;

    public ConfigManager(zMClans plugin) {
        this.plugin = plugin;
    }

    /** Loads (or reloads) config.yml, copying defaults from the jar. */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        notificationsConfig = null;
        parseValues();
    }

    private void parseValues() {
        language = config.getString("language", "EN").toUpperCase();
        creationCost = config.getDouble("clan.creation-cost", 0.0);
        inviteExpireSeconds = config.getInt("invitations.expire-seconds", 120);
        homeCooldown = config.getInt("home.teleport-cooldown", 5);
        homeWarmup = config.getInt("home.teleport-warmup", 3);
        cancelOnMove = config.getBoolean("home.cancel-on-move", true);
        friendlyFire = config.getBoolean("clan.friendly-fire", false);
        storageType = config.getString("storage.type", "SQLITE").toUpperCase();
    }

    public FileConfiguration raw() {
        return config;
    }

    public String getLanguage() {
        return language;
    }

    public int getMaxHomes() {
        return Math.max(1, config.getInt("home.max-homes", 5));
    }

    public double getCreationCost() {
        return creationCost;
    }

    public int getInviteExpireSeconds() {
        return inviteExpireSeconds;
    }

    public int getHomeCooldown() {
        return homeCooldown;
    }

    public int getHomeWarmup() {
        return homeWarmup;
    }

    public boolean isCancelOnMove() {
        return cancelOnMove;
    }

    public boolean isFriendlyFire() {
        return friendlyFire;
    }

    public String getStorageType() {
        return storageType;
    }

    public String getSqliteFile() {
        return config.getString("storage.sqlite.file", "database/clans.db");
    }

    public String getMysqlHost() {
        return config.getString("storage.mysql.host", "localhost");
    }

    public int getMysqlPort() {
        return config.getInt("storage.mysql.port", 3306);
    }

    public String getMysqlDatabase() {
        return config.getString("storage.mysql.database", "zmclans");
    }

    public String getMysqlUser() {
        return config.getString("storage.mysql.username", "root");
    }

    public String getMysqlPassword() {
        return config.getString("storage.mysql.password", "");
    }

    public int getMysqlPoolSize() {
        return config.getInt("storage.mysql.pool-size", 10);
    }

    public boolean isMysqlSsl() {
        return config.getBoolean("storage.mysql.useSSL", false);
    }

    public boolean isMysqlAllowPublicKeyRetrieval() {
        return config.getBoolean("storage.mysql.allowPublicKeyRetrieval", true);
    }

    // Clan name validation
    public int getNameMinLength() {
        return config.getInt("clan.name.min-length", 3);
    }

    public int getNameMaxLength() {
        return config.getInt("clan.name.max-length", 20);
    }

    public String getNamePattern() {
        return config.getString("clan.name.allowed-pattern", "^[a-zA-Z0-9 _-]+$");
    }

    public List<String> getNameBlacklist() {
        return config.contains("clan.name.blacklist")
                ? config.getStringList("clan.name.blacklist")
                : List.of("admin", "administrator", "owner", "staff", "moderator", "mod", "support",
                        "help", "console", "server", "system");
    }

    public int getTagMinLength() {
        return config.getInt("clan.tag.min-length", 2);
    }

    public int getTagMaxLength() {
        return config.getInt("clan.tag.max-length", 12);
    }

    public String getTagPattern() {
        return config.getString("clan.tag.allowed-pattern", "^[a-zA-Z0-9]+$");
    }

    public List<String> getBlockedTagFormatCodes() {
        return config.contains("clan.tag.blocked-format-codes")
                ? config.getStringList("clan.tag.blocked-format-codes")
                : List.of("k", "m");
    }

    // Leaderboard cache
    public int getLeaderboardRefreshInterval() {
        return Math.max(1, config.getInt("leaderboard.refresh-interval", 300));
    }

    public boolean isCreationCostBypassEnabled() {
        return config.getBoolean("economy.allow-cost-bypass", false);
    }

    // Chat & AutoSave
    public String getChatFormat() {
        return config.getString("chat.format", "&8[&6{clan}&8] &7{player}&8: &f{message}");
    }

    public int getAutoSaveInterval() {
        return config.getInt("storage.auto-save-interval", 5);
    }

    public int getMaxRelations() {
        return Math.max(1, config.getInt("relations.max-per-clan", 25));
    }

    public int getAllyRequestExpireSeconds() {
        return Math.max(0, config.getInt("relations.ally-request-expire-seconds", 180));
    }

    public int getKillCreditCooldownSeconds() {
        return Math.max(0, config.getInt("combat.kill-credit-cooldown", 20));
    }

    public int getDescriptionMaxLength() {
        return Math.max(8, config.getInt("clan.description.max-length", 100));
    }

    public String formatCurrency(double amount) {
        String format = config.getString("economy.currency-format", "$%.2f");
        try {
            return String.format(java.util.Locale.US, format, amount);
        } catch (java.util.IllegalFormatException ex) {
            plugin.getLogger().warning("Invalid economy.currency-format; falling back to numeric amount.");
            return String.format(java.util.Locale.US, "%.2f", amount);
        }
    }

    /**
     * Returns the notifications.yml config, loading and caching it on first access.
     * Invalidated by {@link #load()} so /zmclan reload picks up changes.
     */
    public FileConfiguration getNotifications() {
        if (notificationsConfig == null) {
            notificationsConfig = loadYaml("notifications.yml");
        }
        return notificationsConfig;
    }

    /** Forces a reload of notifications.yml without touching config.yml. */
    public void reloadNotifications() {
        notificationsConfig = loadYaml("notifications.yml");
    }

    /**
     * Loads a YAML file from the plugin data folder,
     * copying the default from the jar if absent.
     */
    public FileConfiguration loadYaml(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
        FileConfiguration fc = YamlConfiguration.loadConfiguration(file);
        // Merge defaults from jar
        InputStream defaults = plugin.getResource(resourcePath);
        if (defaults != null) {
            fc.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        return fc;
    }

    /** Saves a FileConfiguration back to disk. */
    public void saveYaml(FileConfiguration fc, String relativePath) {
        File file = new File(plugin.getDataFolder(), relativePath);
        try {
            fc.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + relativePath, e);
        }
    }
}
