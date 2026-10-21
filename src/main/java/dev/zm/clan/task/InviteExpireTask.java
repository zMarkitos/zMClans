package dev.zm.clan.task;

import dev.zm.clan.zMClans;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodically purges expired clan invitations from memory.
 */
public final class InviteExpireTask extends BukkitRunnable {

    private final zMClans plugin;

    public InviteExpireTask(zMClans plugin) {
        this.plugin = plugin;
    }

    public void start() {
        runTaskTimer(plugin, 400L, 400L);
    }

    @Override
    public void run() {
        if (plugin.getInviteService() != null) {
            plugin.getInviteService().purgeExpired();
        }
        if (plugin.getRelationService() != null) {
            plugin.getRelationService().purgeExpired();
        }
    }
}
