package dev.zm.clan.service;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.api.event.ClanMemberJoinEvent;
import dev.zm.clan.api.event.ClanMemberLeaveEvent;
import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.member.repository.MemberRepository;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.zMClans;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages member lifecycle: joining, leaving, kicking, rank changes.
 * Maintains a player-to-member cache for O(1) member lookups.
 */
public final class MemberService {

    private final zMClans plugin;
    private final MemberRepository memberRepo;

    /** player UUID → ClanMember (all members of all clans) */
    private final Map<UUID, ClanMember> memberCache = new ConcurrentHashMap<>();

    public enum KickResult {
        OK, NO_PERMISSION, CANNOT_KICK_LEADER, RANK_TOO_HIGH, NOT_IN_CLAN
    }

    public enum RankResult {
        OK, RANK_NOT_FOUND, NO_PERMISSION, SAME_RANK, CANNOT_ASSIGN_HIGHER
    }

    public MemberService(zMClans plugin) {
        this.plugin = plugin;
        this.memberRepo = new MemberRepository(plugin.getDatabaseManager(), plugin.getLogger());
    }

    /** Loads all members from database into the cache on startup. */
    public void loadAll() {
        memberCache.clear();
        for (Clan clan : plugin.getClanService().allClans()) {
            List<ClanMember> members = memberRepo.findByClan(clan.getId());
            for (ClanMember m : members) {
                memberCache.put(m.getPlayerUUID(), m);
                clan.addMember(m.getPlayerUUID());
                plugin.getClanService().indexPlayer(m.getPlayerUUID(), clan.getId());
            }
        }
    }

    /** Loads a single member into the cache (used when checking or join). */
    public Optional<ClanMember> loadPlayer(UUID playerUUID) {
        Optional<ClanMember> opt = memberRepo.findByPlayer(playerUUID);
        opt.ifPresent(m -> memberCache.put(playerUUID, m));
        return opt;
    }

    public void registerMember(ClanMember member) {
        if (member != null) {
            memberCache.put(member.getPlayerUUID(), member);
        }
    }

    public void unloadPlayer(UUID playerUUID) {
        // Keep in cache so offline members remain manageable and visible in GUI
    }

    public void saveAllSync() {
        for (ClanMember member : memberCache.values()) {
            memberRepo.save(member.copyForPersistence());
        }
    }

    public void saveAll() {
        for (ClanMember member : memberCache.values()) {
            ClanMember snapshot = member.copyForPersistence();
            plugin.getDatabaseManager().executeAsync("save member " + member.getPlayerUUID(),
                    () -> memberRepo.save(snapshot));
        }
    }

    public boolean joinClan(UUID playerUUID, UUID clanId, String defaultRankId) {
        if (playerUUID == null || clanId == null)
            return false;
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.hasPermission("zmclan.player.join"))
            return false;
        Clan clan = plugin.getClanService().findById(clanId).orElse(null);
        if (clan == null || !clan.isPublicJoin())
            return false;
        return joinClanInternal(playerUUID, clan, defaultRankId);
    }

    boolean joinClanFromInvitation(UUID playerUUID, UUID clanId, String defaultRankId) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.hasPermission("zmclan.player.accept"))
            return false;
        Clan clan = plugin.getClanService().findById(clanId).orElse(null);
        return clan != null && joinClanInternal(playerUUID, clan, defaultRankId);
    }

    private boolean joinClanInternal(UUID playerUUID, Clan clan, String defaultRankId) {
        if (plugin.getClanService().isInClan(playerUUID) || memberCache.containsKey(playerUUID))
            return false;
        if (clan.getMemberCount() >= getMaxMembers(clan))
            return false;

        UUID clanId = clan.getId();
        ClanMember m = new ClanMember(playerUUID, clanId, defaultRankId, Instant.now());
        memberCache.put(playerUUID, m);
        clan.addMember(playerUUID);
        plugin.getClanService().indexPlayer(playerUUID, clanId);
        org.bukkit.Bukkit.getPluginManager().callEvent(
                new ClanMemberJoinEvent(plugin.getAPI().snapshot(clan), playerUUID));
        if (plugin.getProgressionService() != null) {
            plugin.getClanService().addPointsAndCheckLevelUp(clan,
                    plugin.getProgressionService().getPointsForAction("join-member"));
        } else {
            plugin.getClanService().saveAsync(clan);
        }
        ClanMember memberToPersist = m.copyForPersistence();
        plugin.getDatabaseManager().executeAsync("add member " + playerUUID,
                () -> memberRepo.insert(memberToPersist));
        if (plugin.getNotificationService() != null) {
            Player joiningPlayer = Bukkit.getPlayer(playerUUID);
            String playerName = joiningPlayer != null ? joiningPlayer.getName()
                    : Bukkit.getOfflinePlayer(playerUUID).getName();
            if (joiningPlayer != null && joiningPlayer.isOnline()) {
                plugin.getNotificationService().sendNotification(joiningPlayer, "member-join",
                        "clan", clan.getName(),
                        "player", playerName != null ? playerName : "Unknown");
            }
            notifyRemainingMembers(clan, playerUUID, "member-joined",
                    "clan", clan.getName(),
                    "player", playerName != null ? playerName : "Unknown");
        }
        return true;
    }

    public boolean leaveClan(UUID playerUUID) {
        if (playerUUID == null)
            return false;
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.hasPermission("zmclan.player.leave"))
            return false;
        ClanMember current = getMember(playerUUID).orElse(null);
        if (current == null)
            return false;

        // The clan owner must transfer leadership or disband the clan; never allow
        // leaving through commands, GUIs, or any other caller.
        Clan clan = plugin.getClanService().findById(current.getClanId()).orElse(null);
        if (clan != null && clan.getLeader().equals(playerUUID))
            return false;
        if (plugin.getRankService().getRank(current.getRankId()).map(ClanRank::isLeader).orElse(false))
            return false;
        boolean removed = removeMemberInternal(playerUUID, current);
        if (removed && plugin.getNotificationService() != null) {
            Player leavingPlayer = Bukkit.getPlayer(playerUUID);
            String playerName = leavingPlayer != null ? leavingPlayer.getName()
                    : Bukkit.getOfflinePlayer(playerUUID).getName();
            String name = playerName != null ? playerName : "Unknown";
            if (leavingPlayer != null && leavingPlayer.isOnline()) {
                plugin.getNotificationService().sendNotification(leavingPlayer, "member-leave",
                        "clan", clan != null ? clan.getName() : "");
            }
            if (clan != null) {
                notifyRemainingMembers(clan, playerUUID, "member-left",
                        "clan", clan.getName(),
                        "player", name);
            }
        }
        return removed;
    }

    /**
     * Internal removal path reserved for clan disbanding after ownership checks.
     */
    public boolean removeMemberForDisband(UUID playerUUID) {
        if (playerUUID == null)
            return false;
        ClanMember current = memberCache.get(playerUUID);
        if (current == null)
            current = memberRepo.findByPlayer(playerUUID).orElse(null);
        return current != null && removeMemberInternal(playerUUID, current);
    }

    private boolean removeMemberInternal(UUID playerUUID, ClanMember member) {
        memberCache.remove(playerUUID);
        plugin.getClanService().findById(member.getClanId()).ifPresent(c -> {
            c.removeMember(playerUUID);
            org.bukkit.Bukkit.getPluginManager().callEvent(
                    new ClanMemberLeaveEvent(plugin.getAPI().snapshot(c), playerUUID));
        });
        plugin.getClanService().unindexPlayer(playerUUID);
        plugin.getDatabaseManager().executeAsync("remove member " + playerUUID,
                () -> memberRepo.delete(playerUUID));
        return true;
    }

    public boolean leave(org.bukkit.entity.Player player) {
        if (player == null || !player.hasPermission("zmclan.player.leave"))
            return false;
        return leaveClan(player.getUniqueId());
    }

    private void notifyRemainingMembers(Clan clan, UUID excludedPlayer, String eventKey, String... replacements) {
        if (clan == null || plugin.getNotificationService() == null) {
            return;
        }
        for (UUID memberId : clan.getMemberIds()) {
            if (memberId.equals(excludedPlayer)) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                plugin.getNotificationService().sendNotification(member, eventKey, replacements);
            }
        }
    }

    public KickResult kick(UUID actorUUID, UUID targetUUID) {
        Player actorPlayer = Bukkit.getPlayer(actorUUID);
        if (actorPlayer == null || !actorPlayer.hasPermission("zmclan.player.kick"))
            return KickResult.NO_PERMISSION;
        ClanMember actor = getMember(actorUUID).orElse(null);
        ClanMember target = getMember(targetUUID).orElse(null);

        if (actor == null || target == null || !actor.getClanId().equals(target.getClanId()))
            return KickResult.NOT_IN_CLAN;

        ClanRank actorRank = plugin.getRankService().getRank(actor.getRankId()).orElse(null);
        ClanRank targetRank = plugin.getRankService().getRank(target.getRankId()).orElse(null);

        if (actorRank == null || !actorRank.hasPermission(ClanRank.Permission.KICK))
            return KickResult.NO_PERMISSION;
        if (targetRank != null && targetRank.isLeader())
            return KickResult.CANNOT_KICK_LEADER;
        if (targetRank != null && targetRank.getPriority() >= actorRank.getPriority())
            return KickResult.RANK_TOO_HIGH;

        Clan clan = plugin.getClanService().findById(actor.getClanId()).orElse(null);
        if (!removeMemberInternal(targetUUID, target)) {
            return KickResult.NOT_IN_CLAN;
        }
        if (plugin.getNotificationService() != null) {
            Player targetPlayer = Bukkit.getPlayer(targetUUID);
            String targetName = targetPlayer != null ? targetPlayer.getName()
                    : Bukkit.getOfflinePlayer(targetUUID).getName();
            String name = targetName != null ? targetName : "Unknown";
            if (targetPlayer != null && targetPlayer.isOnline()) {
                plugin.getNotificationService().sendNotification(targetPlayer, "member-kick",
                        "clan", clan != null ? clan.getName() : "",
                        "player", name);
            }
            if (clan != null) {
                notifyRemainingMembers(clan, targetUUID, "member-kicked",
                        "clan", clan.getName(),
                        "player", name);
            }
        }

        return KickResult.OK;
    }

    public boolean transferLeadership(UUID actorUUID, UUID targetUUID) {
        Player actorPlayer = Bukkit.getPlayer(actorUUID);
        if (actorPlayer == null || !actorPlayer.hasPermission("zmclan.player.rank"))
            return false;
        ClanMember actor = getMember(actorUUID).orElse(null);
        ClanMember target = getMember(targetUUID).orElse(null);
        if (actor == null || target == null || !actor.getClanId().equals(target.getClanId()))
            return false;
        Clan clan = plugin.getClanService().findById(actor.getClanId()).orElse(null);
        if (clan == null || !clan.getLeader().equals(actorUUID))
            return false;
        if (!hasPermission(actorUUID, ClanRank.Permission.TRANSFER_LEADER))
            return false;

        String leaderRank = plugin.getRankService().getLeaderRank().map(ClanRank::getId).orElse("leader");
        String defaultRank = plugin.getRankService().getDefaultRank().map(ClanRank::getId).orElse("member");

        actor.setRankId(defaultRank);
        target.setRankId(leaderRank);

        clan.getMember(actorUUID).ifPresent(m -> m.setRankId(defaultRank));
        clan.getMember(targetUUID).ifPresent(m -> m.setRankId(leaderRank));

        clan.setLeader(targetUUID);
        clan.setLeaderRankId(leaderRank);

        plugin.getDatabaseManager().executeAsync("transfer clan leadership " + clan.getId(), () -> {
            memberRepo.updateRank(actorUUID, defaultRank);
            memberRepo.updateRank(targetUUID, leaderRank);
        });
        plugin.getClanService().saveAsync(clan);

        if (plugin.getNotificationService() != null) {
            org.bukkit.entity.Player tp = org.bukkit.Bukkit.getPlayer(targetUUID);
            if (tp != null && tp.isOnline()) {
                plugin.getNotificationService().sendNotification(tp, "transfer-leader", "player", tp.getName(), "clan",
                        clan.getName());
            }
        }

        return true;
    }

    public RankResult assignRank(UUID actorUUID, UUID targetUUID, String newRankId) {
        Player actorPlayer = Bukkit.getPlayer(actorUUID);
        if (actorPlayer == null || !actorPlayer.hasPermission("zmclan.player.rank"))
            return RankResult.NO_PERMISSION;
        ClanMember actor = getMember(actorUUID).orElse(null);
        ClanMember target = getMember(targetUUID).orElse(null);
        if (actor == null || target == null || !actor.getClanId().equals(target.getClanId()))
            return RankResult.NO_PERMISSION;

        ClanRank newRank = plugin.getRankService().getRank(newRankId).orElse(null);
        ClanRank actorRank = plugin.getRankService().getRank(actor.getRankId()).orElse(null);
        ClanRank targetRank = plugin.getRankService().getRank(target.getRankId()).orElse(null);

        if (newRank == null)
            return RankResult.RANK_NOT_FOUND;
        if (actorRank == null || !actorRank.hasPermission(ClanRank.Permission.PROMOTE))
            return RankResult.NO_PERMISSION;
        if (newRank.isLeader() || (targetRank != null && targetRank.isLeader()))
            return RankResult.CANNOT_ASSIGN_HIGHER;
        if (newRank.getPriority() >= actorRank.getPriority())
            return RankResult.CANNOT_ASSIGN_HIGHER;
        if (target.getRankId().equalsIgnoreCase(newRankId))
            return RankResult.SAME_RANK;

        target.setRankId(newRankId);
        plugin.getDatabaseManager().executeAsync("change member rank " + targetUUID,
                () -> memberRepo.updateRank(targetUUID, newRankId));

        if (plugin.getNotificationService() != null) {
            org.bukkit.entity.Player tp = org.bukkit.Bukkit.getPlayer(targetUUID);
            if (tp != null && tp.isOnline()) {
                boolean isPromote = targetRank == null || newRank.getPriority() > targetRank.getPriority();
                plugin.getNotificationService().sendNotification(tp, isPromote ? "member-promote" : "member-demote",
                        "player", tp.getName(),
                        "rank", newRank.getDisplayName());
            }
        }

        Clan clan = plugin.getClanService().findById(target.getClanId()).orElse(null);
        if (clan != null && plugin.getProgressionService() != null) {
            boolean promoted = targetRank == null || newRank.getPriority() > targetRank.getPriority();
            plugin.getClanService().addPointsAndCheckLevelUp(clan,
                    plugin.getProgressionService().getPointsForAction(promoted ? "promote-member" : "demote-member"));
        }

        return RankResult.OK;
    }

    public Optional<ClanMember> getMember(UUID uuid) {
        if (uuid == null)
            return Optional.empty();
        ClanMember m = memberCache.get(uuid);
        if (m != null)
            return Optional.of(m);
        Optional<ClanMember> opt = memberRepo.findByPlayer(uuid);
        opt.ifPresent(cm -> memberCache.put(uuid, cm));
        return opt;
    }

    public boolean isLoaded(UUID uuid) {
        return memberCache.containsKey(uuid);
    }

    /** Returns all members of a specific clan. */
    public List<ClanMember> getMembersOfClan(UUID clanId) {
        if (clanId == null)
            return Collections.emptyList();
        List<ClanMember> fromCache = memberCache.values().stream()
                .filter(m -> clanId.equals(m.getClanId()))
                .toList();
        if (!fromCache.isEmpty()) {
            return fromCache;
        }
        List<ClanMember> fromDb = memberRepo.findByClan(clanId);
        for (ClanMember cm : fromDb) {
            memberCache.put(cm.getPlayerUUID(), cm);
        }
        return fromDb;
    }

    public boolean hasPermission(UUID playerUUID, ClanRank.Permission perm) {
        if (playerUUID == null || perm == null)
            return false;
        ClanMember m = getMember(playerUUID).orElse(null);
        if (m == null)
            return false;
        Clan clan = plugin.getClanService().findById(m.getClanId()).orElse(null);
        if (clan != null && playerUUID.equals(clan.getLeader())) {
            return true;
        }
        return plugin.getRankService().getRank(m.getRankId())
                .map(r -> r.hasPermission(perm))
                .orElse(false);
    }

    public int getMaxMembers(Clan clan) {
        int level = clan == null
                ? plugin.getConfigManager().raw().getInt("progression.start-level", 1)
                : plugin.getProgressionService().getLevel(clan);
        long limit = plugin.getProgressionService().getLevelLimit(level, "members", 10);
        return (int) Math.min(Integer.MAX_VALUE, limit);
    }

    public MemberRepository getMemberRepo() {
        return memberRepo;
    }
}
