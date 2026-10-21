package dev.zm.clan.listeners;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.zMClans;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles clan friendly fire prevention.
 */
public final class FriendlyFireListener implements Listener {

    private final zMClans plugin;
    private final Map<UUID, Long> lastWarning = new ConcurrentHashMap<>();
    private final Map<String, Long> killCredit = new ConcurrentHashMap<>();

    public FriendlyFireListener(zMClans plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        Optional<Clan> attackerClan = plugin.getClanService().findByPlayer(attacker.getUniqueId());
        Optional<Clan> victimClan = plugin.getClanService().findByPlayer(victim.getUniqueId());

        if (attackerClan.isEmpty() || victimClan.isEmpty()) {
            return;
        }

        Clan clan = attackerClan.get();
        Clan victimSide = victimClan.get();
        boolean sameClan = clan.getId().equals(victimSide.getId());
        boolean allied = clan.isAlly(victimSide.getId()) && victimSide.isAlly(clan.getId());
        if ((sameClan && !clan.isFriendlyFire())
                || (allied && !(clan.isAllyPvpEnabled() && victimSide.isAllyPvpEnabled()))) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            long last = lastWarning.getOrDefault(attacker.getUniqueId(), 0L);
            if (now - last > 2000L) {
                attacker.sendMessage(plugin.getMessages().get(allied ? "ally-pvp-blocked" : "friendly-fire-blocked"));
                lastWarning.put(attacker.getUniqueId(), now);
                if (lastWarning.size() > 2048) {
                    lastWarning.clear();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        Optional<Clan> victimClan = plugin.getClanService().findByPlayer(victim.getUniqueId());
        Optional<Clan> killerClan = killer == null ? Optional.empty()
                : plugin.getClanService().findByPlayer(killer.getUniqueId());
        boolean sameOrAllied = killerClan.isPresent() && victimClan.isPresent()
                && (killerClan.get().getId().equals(victimClan.get().getId())
                        || (killerClan.get().isAlly(victimClan.get().getId())
                                && victimClan.get().isAlly(killerClan.get().getId())));
        if (sameOrAllied)
            return;
        if (killer != null && killer.getUniqueId().equals(victim.getUniqueId()))
            return;

        int cooldown = plugin.getConfigManager().getKillCreditCooldownSeconds();
        if (killer != null && cooldown > 0) {
            String pair = killer.getUniqueId() + ":" + victim.getUniqueId();
            long now = System.currentTimeMillis();
            Long last = killCredit.get(pair);
            if (last != null && now - last < cooldown * 1000L)
                return;
            killCredit.put(pair, now);
            if (killCredit.size() > 2048)
                killCredit.clear();
        }

        victimClan.ifPresent(clan -> {
            clan.addDeath();
            plugin.getClanService().addPointsAndCheckLevelUp(clan,
                    plugin.getProgressionService().getPointsForAction("death"));
            plugin.getClanService().invalidateLeaderboardCache();
        });
        plugin.getMemberService().getMember(victim.getUniqueId()).ifPresent(member -> {
            member.addDeath();
            UUID playerId = victim.getUniqueId();
            plugin.getDatabaseManager().executeAsync("record member death " + playerId,
                    () -> plugin.getMemberService().getMemberRepo().incrementDeaths(playerId));
        });
        if (killer != null) {
            killerClan.ifPresent(clan -> {
                clan.addKill();
                plugin.getClanService().addPointsAndCheckLevelUp(clan,
                        plugin.getProgressionService().getPointsForAction("kill"));
                plugin.getClanService().invalidateLeaderboardCache();
            });
            plugin.getMemberService().getMember(killer.getUniqueId()).ifPresent(member -> {
                member.addKill();
                UUID playerId = killer.getUniqueId();
                plugin.getDatabaseManager().executeAsync("record member kill " + playerId,
                        () -> plugin.getMemberService().getMemberRepo().incrementKills(playerId));
            });
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    public void removePlayer(UUID uuid) {
        if (uuid != null) {
            lastWarning.remove(uuid);
        }
    }
}
