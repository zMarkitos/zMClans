package dev.zm.clan.service;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.zMClans;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configurable, points-based clan progression. No script or expression
 * evaluation is used.
 */
public final class ProgressionService {
    private final zMClans plugin;
    private FileConfiguration config;

    public ProgressionService(zMClans plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.config = plugin.getConfigManager().loadYaml("levels.yml");
    }

    public int getLevel(Clan clan) {
        if (clan == null)
            return 0;
        if (!config.getBoolean("progression.enabled", true))
            return config.getInt("progression.start-level", 1);
        int start = Math.max(1, config.getInt("progression.start-level", 1));
        int max = Math.max(start, config.getInt("progression.max-level", 50));
        double base = Math.max(1.0, config.getDouble("progression.base-points", 100));
        double exponent = Math.max(0.1, Math.min(4.0, config.getDouble("progression.exponent", 1.65)));
        int level = start;
        while (level < max) {
            double required = base * Math.pow(level, exponent);
            if (!Double.isFinite(required) || clan.getPoints() < required)
                break;
            level++;
        }
        return level;
    }

    public long getPointsForAction(String action) {
        if (action == null)
            return 0;
        String key = "progression.points." + action.toLowerCase(java.util.Locale.ROOT);
        if ("death".equalsIgnoreCase(action) && !config.contains(key))
            return -getDeathPenalty();
        return config.getLong(key, 0);
    }

    public long getDeathPenalty() {
        return Math.max(0, config.getLong("progression.points.death-penalty", 1));
    }

    public long getLevelLimit(Clan clan, String limit, int fallback) {
        int level = clan == null ? config.getInt("progression.start-level", 1) : getLevel(clan);
        return getLevelLimit(level, limit, fallback);
    }

    public long getLevelLimit(int level, String limit, int fallback) {
        if (limit == null || limit.isBlank())
            return Math.max(0, fallback);
        for (int current = Math.max(1, level); current >= 1; current--) {
            String path = "progression.level-limits." + current + "." + limit;
            if (config.contains(path))
                return Math.max(0, config.getLong(path, fallback));
        }
        return Math.max(0, fallback);
    }

    public long getPointsForNextLevel(Clan clan) {
        if (clan == null)
            return 0;
        int level = getLevel(clan);
        if (level >= Math.max(1, config.getInt("progression.max-level", 50)))
            return 0;
        double base = Math.max(1.0, config.getDouble("progression.base-points", 100));
        double exponent = Math.max(0.1, Math.min(4.0, config.getDouble("progression.exponent", 1.65)));
        double value = base * Math.pow(level, exponent);
        return Double.isFinite(value) ? (long) Math.ceil(value) : Long.MAX_VALUE;
    }

    public long getPointsNeeded(Clan clan) {
        if (clan == null)
            return 0;
        long target = getPointsForNextLevel(clan);
        return target <= 0 ? 0 : Math.max(0, target - clan.getPoints());
    }
}
