package dev.zm.clan.service;

import dev.zm.clan.config.ColorUtil;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.zMClans;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Loads and manages {@link ClanRank} definitions from {@code ranks.yml}.
 * Ranks are re-loadable at runtime without restarting.
 */
public final class RankService {

    private final zMClans plugin;
    /** rank id → ClanRank, sorted by priority ascending */
    private final Map<String, ClanRank> ranks = new LinkedHashMap<>();

    public RankService(zMClans plugin) {
        this.plugin = plugin;
    }

    /** Loads (or reloads) ranks.yml. Clears old definitions first. */
    public void load() {
        ranks.clear();
        FileConfiguration cfg = plugin.getConfigManager().loadYaml("ranks.yml");
        ConfigurationSection section = cfg.getConfigurationSection("ranks");
        if (section == null) {
            plugin.getLogger().warning("ranks.yml has no 'ranks' section! Using empty rank list.");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null)
                continue;

            ClanRank rank = new ClanRank(key);
            rank.setDisplayName(s.getString("display-name", key));
            rank.setColor(ColorUtil.colorize(s.getString("color", "&f")));
            rank.setPrefix(ColorUtil.colorize(s.getString("prefix", "")));
            rank.setPriority(s.getInt("priority", 1));
            rank.setLeader(s.getBoolean("is-leader", false));

            ConfigurationSection perms = s.getConfigurationSection("permissions");
            if (perms != null) {
                for (ClanRank.Permission p : ClanRank.Permission.values()) {
                    rank.setPermission(p, perms.getBoolean(p.name(), false));
                }
            }
            ranks.put(key.toLowerCase(), rank);
        }

        // Sort by priority
        List<Map.Entry<String, ClanRank>> sorted = new ArrayList<>(ranks.entrySet());
        sorted.sort(Comparator.comparingInt(e -> e.getValue().getPriority()));
        ranks.clear();
        sorted.forEach(e -> ranks.put(e.getKey(), e.getValue()));

        plugin.getLogger().info("Loaded " + ranks.size() + " clan ranks.");
    }

    public Optional<ClanRank> getRank(String id) {
        if (id == null)
            return Optional.empty();
        return Optional.ofNullable(ranks.get(id.toLowerCase()));
    }

    public Optional<ClanRank> getLeaderRank() {
        return ranks.values().stream().filter(ClanRank::isLeader).findFirst();
    }

    /** Returns the lowest-priority (default) rank for new members. */
    public Optional<ClanRank> getDefaultRank() {
        return ranks.values().stream().findFirst();
    }

    public Collection<ClanRank> allRanks() {
        return Collections.unmodifiableCollection(ranks.values());
    }

    public Collection<ClanRank> getAllRanks() {
        return allRanks();
    }

    public List<String> rankIds() {
        return List.copyOf(ranks.keySet());
    }
}
