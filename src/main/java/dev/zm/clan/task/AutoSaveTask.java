package dev.zm.clan.task;

import dev.zm.clan.zMClans;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodically saves dirty clan and member states to the database.
 */
public final class AutoSaveTask extends BukkitRunnable {

    private final zMClans plugin;

    public AutoSaveTask(zMClans plugin) {
        this.plugin = plugin;
    }

    public void start() {
        int intervalMinutes = plugin.getConfigManager().getAutoSaveInterval();
        long intervalTicks = Math.max(1, intervalMinutes) * 60L * 20L;
        runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    @Override
    public void run() {
        if (plugin.getClanService() != null) {
            plugin.getClanService().saveAll();
        }
        if (plugin.getMemberService() != null) {
            plugin.getMemberService().saveAll();
        }
    }
}
