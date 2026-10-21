package dev.zm.clan.service;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.invitation.model.Invitation;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.zMClans;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages pending invitations in memory (not persisted — they are transient).
 * Invitations expire after the configured seconds.
 */
public final class InviteService {

    private final zMClans plugin;

    /**
     * target player UUID → list of pending invitations from different clans.
     * Uses CopyOnWriteArrayList for thread safety against concurrent tasks.
     */
    private final Map<UUID, List<Invitation>> pending = new ConcurrentHashMap<>();

    public enum InviteResult {
        OK,
        NO_PERMISSION,
        ALREADY_HAS_CLAN,
        ALREADY_INVITED,
        CLAN_FULL,
        PLAYER_NOT_FOUND
    }

    public InviteService(zMClans plugin) {
        this.plugin = plugin;
    }

    public InviteResult sendInvite(UUID senderUUID, UUID targetUUID) {
        if (senderUUID == null || targetUUID == null)
            return InviteResult.PLAYER_NOT_FOUND;
        org.bukkit.entity.Player sender = Bukkit.getPlayer(senderUUID);
        if (sender == null || !sender.hasPermission("zmclan.player.invite")) {
            return InviteResult.NO_PERMISSION;
        }

        var cs = plugin.getClanService();
        var ms = plugin.getMemberService();

        Optional<Clan> clanOpt = cs.findByPlayer(senderUUID);
        if (clanOpt.isEmpty())
            return InviteResult.NO_PERMISSION;

        Clan clan = clanOpt.get();
        boolean canInvite = ms.hasPermission(senderUUID, ClanRank.Permission.INVITE) || clan.isOpenInvitations();
        if (!canInvite)
            return InviteResult.NO_PERMISSION;

        if (senderUUID.equals(targetUUID))
            return InviteResult.PLAYER_NOT_FOUND;
        if (cs.isInClan(targetUUID))
            return InviteResult.ALREADY_HAS_CLAN;

        if (clan.getMemberCount() >= ms.getMaxMembers(clan))
            return InviteResult.CLAN_FULL;

        // Duplicate check
        List<Invitation> list = pending.computeIfAbsent(targetUUID, k -> new CopyOnWriteArrayList<>());
        synchronized (list) {
            boolean alreadySent = list.stream()
                    .anyMatch(i -> i.getClanId().equals(clan.getId()) && !i.isExpired());
            if (alreadySent)
                return InviteResult.ALREADY_INVITED;

            int expireSeconds = plugin.getConfigManager().getInviteExpireSeconds();
            Instant expiry = expireSeconds > 0 ? Instant.now().plusSeconds(expireSeconds) : null;
            list.add(new Invitation(clan.getId(), targetUUID, senderUUID, expiry));
        }

        if (plugin.getNotificationService() != null) {
            org.bukkit.OfflinePlayer inviter = Bukkit.getOfflinePlayer(senderUUID);
            String inviterName = inviter.getName() != null ? inviter.getName() : "Unknown";
            String targetName = Optional.ofNullable(Bukkit.getOfflinePlayer(targetUUID).getName())
                    .orElse("Unknown");
            org.bukkit.entity.Player target = Bukkit.getPlayer(targetUUID);
            if (target != null && target.isOnline()) {
                plugin.getNotificationService().sendNotification(target, "invite-received",
                        "clan", clan.getName(),
                        "sender", inviterName);
            }
            org.bukkit.entity.Player onlineSender = Bukkit.getPlayer(senderUUID);
            if (onlineSender != null && onlineSender.isOnline()) {
                plugin.getNotificationService().sendNotification(onlineSender, "invite-sent",
                        "clan", clan.getName(),
                        "player", targetName);
            }
        }

        return InviteResult.OK;
    }

    public boolean accept(UUID targetUUID, UUID clanId) {
        if (targetUUID == null || clanId == null)
            return false;
        org.bukkit.entity.Player target = Bukkit.getPlayer(targetUUID);
        if (target == null || !target.hasPermission("zmclan.player.accept"))
            return false;
        List<Invitation> list = pending.get(targetUUID);
        if (list == null)
            return false;

        Invitation inv = list.stream()
                .filter(i -> i.getClanId().equals(clanId) && !i.isExpired())
                .findFirst()
                .orElse(null);

        if (inv == null)
            return false;
        if (plugin.getClanService().isInClan(targetUUID)) {
            list.remove(inv);
            if (list.isEmpty())
                pending.remove(targetUUID);
            return false;
        }
        Clan clan = plugin.getClanService().findById(clanId).orElse(null);
        if (clan == null || clan.getMemberCount() >= plugin.getMemberService().getMaxMembers(clan))
            return false;

        String defaultRank = plugin.getRankService().getDefaultRank()
                .map(ClanRank::getId).orElse("recruit");
        boolean joined = plugin.getMemberService().joinClanFromInvitation(targetUUID, clanId, defaultRank);
        if (joined) {
            list.remove(inv);
            if (list.isEmpty())
                pending.remove(targetUUID);
        }
        if (joined && plugin.getNotificationService() != null) {
            org.bukkit.entity.Player inviter = Bukkit.getPlayer(inv.getInviterPlayer());
            if (inviter != null && inviter.isOnline()) {
                org.bukkit.OfflinePlayer joinedPlayer = Bukkit.getOfflinePlayer(targetUUID);
                plugin.getNotificationService().sendNotification(inviter, "invite-accepted",
                        "clan", clan.getName(),
                        "player", joinedPlayer.getName() != null ? joinedPlayer.getName() : "Unknown");
            }
        }
        return joined;
    }

    public boolean deny(UUID targetUUID, UUID clanId) {
        if (targetUUID == null || clanId == null)
            return false;
        org.bukkit.entity.Player target = Bukkit.getPlayer(targetUUID);
        if (target == null || !target.hasPermission("zmclan.player.deny"))
            return false;
        List<Invitation> list = pending.get(targetUUID);
        if (list == null)
            return false;
        boolean removed = list.removeIf(i -> i.getClanId().equals(clanId));
        if (list.isEmpty())
            pending.remove(targetUUID);
        return removed;
    }

    public boolean cancel(UUID senderUUID, UUID targetUUID) {
        org.bukkit.entity.Player sender = Bukkit.getPlayer(senderUUID);
        if (sender == null || !sender.hasPermission("zmclan.player.invite"))
            return false;
        var clanOpt = plugin.getClanService().findByPlayer(senderUUID);
        if (clanOpt.isEmpty())
            return false;
        List<Invitation> list = pending.get(targetUUID);
        if (list == null)
            return false;
        boolean removed = list.removeIf(i -> i.getClanId().equals(clanOpt.get().getId()));
        if (list.isEmpty())
            pending.remove(targetUUID);
        return removed;
    }

    public void purgeExpired() {
        for (Map.Entry<UUID, List<Invitation>> entry : new ArrayList<>(pending.entrySet())) {
            UUID target = entry.getKey();
            List<Invitation> list = entry.getValue();

            // Collect expired first, then remove — avoids ConcurrentModificationException
            List<Invitation> expired = list.stream().filter(Invitation::isExpired).toList();
            if (!expired.isEmpty()) {
                list.removeAll(expired);
                var player = Bukkit.getPlayer(target);
                if (player != null && player.isOnline()) {
                    for (Invitation inv : expired) {
                        var clanName = plugin.getClanService().findById(inv.getClanId())
                                .map(Clan::getName).orElse("???");
                        player.sendMessage(plugin.getMessages().get("invite-expired", "clan", clanName));
                    }
                }
            }
        }
        pending.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public List<Invitation> getPending(UUID targetUUID) {
        if (targetUUID == null)
            return Collections.emptyList();
        List<Invitation> list = pending.get(targetUUID);
        if (list == null)
            return Collections.emptyList();
        return list.stream().filter(i -> !i.isExpired()).toList();
    }

    public boolean hasInviteFrom(UUID target, UUID clanId) {
        if (target == null || clanId == null)
            return false;
        return pending.getOrDefault(target, Collections.emptyList())
                .stream().anyMatch(i -> i.getClanId().equals(clanId) && !i.isExpired());
    }

    public void forgetClan(UUID clanId) {
        if (clanId == null)
            return;
        for (List<Invitation> list : pending.values()) {
            list.removeIf(i -> i.getClanId().equals(clanId));
        }
        pending.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
