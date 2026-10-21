package dev.zm.clan.service;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.zMClans;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutual clan relations. Both alliances AND rivalries require an accepted
 * request (add → notify target → accept/deny). Pending requests live in memory
 * and expire. Rivalries can also be declared instantly via the deprecated
 * {@link #declareRival(Player, Clan)} for backward compatibility.
 */
public final class RelationService {

    public enum Result {
        OK, NO_CLAN, NO_PERMISSION, INVALID, LIMIT, ALREADY, PENDING, NOT_PENDING, NO_RELATION
    }

    public enum RelationType {
        ALLY, RIVAL
    }

    private record Pending(UUID fromClan, UUID toClan, RelationType type, long expiresAt) {
        boolean expired(long now) {
            return expiresAt > 0 && now >= expiresAt;
        }
    }

    private final zMClans plugin;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public RelationService(zMClans plugin) {
        this.plugin = plugin;
    }

    public Result requestAlly(Player actor, Clan target) {
        Clan own = actorClan(actor);
        Result gate = gate(actor, own, target);
        if (gate != Result.OK)
            return gate;
        if (own.isAlly(target.getId()))
            return Result.ALREADY;
        if (own.isRival(target.getId()) || target.isRival(own.getId()))
            return Result.ALREADY;
        if (hasOutgoing(own.getId(), target.getId(), RelationType.ALLY))
            return Result.PENDING;
        if (hasIncoming(own.getId(), target.getId(), RelationType.ALLY))
            return acceptAlly(actor, target);
        if (hasOutgoing(own.getId(), target.getId(), RelationType.RIVAL)
                || hasIncoming(own.getId(), target.getId(), RelationType.RIVAL))
            return Result.ALREADY;

        Result limit = checkLimit(own, target, "allies");
        if (limit != Result.OK)
            return limit;

        long expire = plugin.getConfigManager().getAllyRequestExpireSeconds();
        long until = expire > 0 ? System.currentTimeMillis() + expire * 1000L : 0L;
        pending.put(key(own.getId(), target.getId()),
                new Pending(own.getId(), target.getId(), RelationType.ALLY, until));
        notifyOfficers(target, plugin.getMessages().get("ally-request-received", "clan", own.getName()));
        return Result.OK;
    }

    public Result acceptAlly(Player actor, Clan fromClan) {
        Clan own = actorClan(actor);
        Result gate = gate(actor, own, fromClan);
        if (gate != Result.OK)
            return gate;
        if (!hasIncoming(own.getId(), fromClan.getId(), RelationType.ALLY))
            return Result.NOT_PENDING;
        Result limit = checkLimit(own, fromClan, "allies");
        if (limit != Result.OK)
            return limit;

        pending.remove(key(fromClan.getId(), own.getId()));
        pending.remove(key(own.getId(), fromClan.getId()));
        own.addAlly(fromClan.getId());
        fromClan.addAlly(own.getId());
        persist(own, fromClan);
        notifyOfficers(fromClan,
                plugin.getMessages().get("relation-updated", "clan", own.getName(), "relation", "ally"));
        if (plugin.getNotificationService() != null) {
            plugin.getNotificationService().sendToClan(own, "ally-accepted", "clan", fromClan.getName());
            plugin.getNotificationService().sendToClan(fromClan, "ally-accepted", "clan", own.getName());
        }
        return Result.OK;
    }

    public Result denyAlly(Player actor, Clan fromClan) {
        Clan own = actorClan(actor);
        Result gate = gate(actor, own, fromClan);
        if (gate != Result.OK)
            return gate;
        Pending p = pending.get(key(fromClan.getId(), own.getId()));
        if (p == null || p.type != RelationType.ALLY || p.expired(System.currentTimeMillis()))
            return Result.NOT_PENDING;
        pending.remove(key(fromClan.getId(), own.getId()));
        notifyOfficers(fromClan, plugin.getMessages().get("ally-request-denied", "clan", own.getName()));
        return Result.OK;
    }

    public Result cancelAllyRequest(Player actor, Clan target) {
        Clan own = actorClan(actor);
        Result gate = gate(actor, own, target);
        if (gate != Result.OK)
            return gate;
        Pending p = pending.get(key(own.getId(), target.getId()));
        if (p == null || p.type != RelationType.ALLY || p.expired(System.currentTimeMillis()))
            return Result.NOT_PENDING;
        pending.remove(key(own.getId(), target.getId()));
        return Result.OK;
    }

    public Result requestRival(Player actor, Clan target) {
        Clan own = actorClan(actor);
        Result gate = gate(actor, own, target);
        if (gate != Result.OK)
            return gate;
        if (own.isRival(target.getId()))
            return Result.ALREADY;
        if (own.isAlly(target.getId()) || target.isAlly(own.getId()))
            return Result.ALREADY;
        if (hasOutgoing(own.getId(), target.getId(), RelationType.RIVAL))
            return Result.PENDING;
        if (hasIncoming(own.getId(), target.getId(), RelationType.RIVAL))
            return acceptRival(actor, target);
        if (hasOutgoing(own.getId(), target.getId(), RelationType.ALLY)
                || hasIncoming(own.getId(), target.getId(), RelationType.ALLY))
            return Result.ALREADY;

        Result limit = checkLimit(own, target, "rivals");
        if (limit != Result.OK)
            return limit;

        long expire = plugin.getConfigManager().getAllyRequestExpireSeconds();
        long until = expire > 0 ? System.currentTimeMillis() + expire * 1000L : 0L;
        pending.put(key(own.getId(), target.getId()),
                new Pending(own.getId(), target.getId(), RelationType.RIVAL, until));
        notifyOfficers(target, plugin.getMessages().get("rival-request-received", "clan", own.getName()));
        return Result.OK;
    }

    public Result acceptRival(Player actor, Clan fromClan) {
        Clan own = actorClan(actor);
        Result gate = gate(actor, own, fromClan);
        if (gate != Result.OK)
            return gate;
        if (!hasIncoming(own.getId(), fromClan.getId(), RelationType.RIVAL))
            return Result.NOT_PENDING;
        Result limit = checkLimit(own, fromClan, "rivals");
        if (limit != Result.OK)
            return limit;

        pending.remove(key(fromClan.getId(), own.getId()));
        pending.remove(key(own.getId(), fromClan.getId()));
        own.addRival(fromClan.getId());
        fromClan.addRival(own.getId());
        persist(own, fromClan);
        notifyOfficers(fromClan,
                plugin.getMessages().get("relation-updated", "clan", own.getName(), "relation", "rival"));
        if (plugin.getNotificationService() != null) {
            plugin.getNotificationService().sendToClan(own, "rival-declared", "clan", fromClan.getName());
            plugin.getNotificationService().sendToClan(fromClan, "rival-declared", "clan", own.getName());
        }
        return Result.OK;
    }

    public Result denyRival(Player actor, Clan fromClan) {
        Clan own = actorClan(actor);
        Result gate = gate(actor, own, fromClan);
        if (gate != Result.OK)
            return gate;
        Pending p = pending.get(key(fromClan.getId(), own.getId()));
        if (p == null || p.type != RelationType.RIVAL || p.expired(System.currentTimeMillis()))
            return Result.NOT_PENDING;
        pending.remove(key(fromClan.getId(), own.getId()));
        notifyOfficers(fromClan, plugin.getMessages().get("rival-request-denied", "clan", own.getName()));
        return Result.OK;
    }

    public Result cancelRivalRequest(Player actor, Clan target) {
        Clan own = actorClan(actor);
        Result gate = gate(actor, own, target);
        if (gate != Result.OK)
            return gate;
        Pending p = pending.get(key(own.getId(), target.getId()));
        if (p == null || p.type != RelationType.RIVAL || p.expired(System.currentTimeMillis()))
            return Result.NOT_PENDING;
        pending.remove(key(own.getId(), target.getId()));
        return Result.OK;
    }

    @Deprecated
    public Result declareRival(Player actor, Clan target) {
        return requestRival(actor, target);
    }

    public Result removeRelation(Player actor, Clan target) {
        Clan own = actorClan(actor);
        Result gate = gate(actor, own, target);
        if (gate != Result.OK)
            return gate;
        boolean related = own.isAlly(target.getId()) || own.isRival(target.getId())
                || target.isAlly(own.getId()) || target.isRival(own.getId());
        if (!related) {
            // cancela/deniega solicitudes pendientes del tipo que haya
            if (hasOutgoing(own.getId(), target.getId(), RelationType.ALLY))
                return cancelAllyRequest(actor, target);
            if (hasIncoming(own.getId(), target.getId(), RelationType.ALLY))
                return denyAlly(actor, target);
            if (hasOutgoing(own.getId(), target.getId(), RelationType.RIVAL))
                return cancelRivalRequest(actor, target);
            if (hasIncoming(own.getId(), target.getId(), RelationType.RIVAL))
                return denyRival(actor, target);
            return Result.NO_RELATION;
        }
        own.removeRelation(target.getId());
        target.removeRelation(own.getId());
        pending.remove(key(own.getId(), target.getId()));
        pending.remove(key(target.getId(), own.getId()));
        persist(own, target);
        notifyOfficers(target, plugin.getMessages().get("relation-removed", "clan", own.getName()));
        return Result.OK;
    }

    public boolean hasOutgoing(UUID from, UUID to) {
        return hasOutgoing(from, to, null);
    }

    public boolean hasIncoming(UUID own, UUID from) {
        return hasIncoming(own, from, null);
    }

    public boolean hasOutgoing(UUID from, UUID to, RelationType type) {
        Pending p = pending.get(key(from, to));
        return p != null && !p.expired(System.currentTimeMillis())
                && (type == null || p.type == type);
    }

    public boolean hasIncoming(UUID own, UUID from, RelationType type) {
        return hasOutgoing(from, own, type);
    }

    public void forgetClan(UUID clanId) {
        if (clanId == null)
            return;
        pending.entrySet().removeIf(e -> e.getValue().fromClan.equals(clanId) || e.getValue().toClan.equals(clanId));
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> e.getValue().expired(now));
    }

    private Result gate(Player actor, Clan own, Clan target) {
        if (own == null)
            return Result.NO_CLAN;
        if (!actor.hasPermission("zmclan.player.relations"))
            return Result.NO_PERMISSION;
        if (!plugin.getMemberService().hasPermission(actor.getUniqueId(), ClanRank.Permission.EDIT_SETTINGS))
            return Result.NO_PERMISSION;
        if (target == null || target.getId().equals(own.getId()))
            return Result.INVALID;
        return Result.OK;
    }

    private Result checkLimit(Clan a, Clan b, String relationType) {
        boolean allies = "allies".equals(relationType);
        int fallback = plugin.getConfigManager().getMaxRelations();
        long aCount = allies ? a.getAllyIds().size() : a.getRivalIds().size();
        long bCount = allies ? b.getAllyIds().size() : b.getRivalIds().size();
        boolean aAlreadyHasType = allies ? a.isAlly(b.getId()) : a.isRival(b.getId());
        boolean bAlreadyHasType = allies ? b.isAlly(a.getId()) : b.isRival(a.getId());
        long aLimit = plugin.getProgressionService().getLevelLimit(a, relationType, fallback);
        long bLimit = plugin.getProgressionService().getLevelLimit(b, relationType, fallback);
        if (!aAlreadyHasType && aCount >= aLimit)
            return Result.LIMIT;
        if (!bAlreadyHasType && bCount >= bLimit)
            return Result.LIMIT;
        return Result.OK;
    }

    private Clan actorClan(Player actor) {
        return plugin.getClanService().findByPlayer(actor.getUniqueId()).orElse(null);
    }

    private void persist(Clan a, Clan b) {
        plugin.getClanService().saveAsync(a);
        plugin.getClanService().saveAsync(b);
    }

    private void notifyOfficers(Clan clan, String message) {
        for (UUID memberId : clan.getMemberIds()) {
            if (!plugin.getMemberService().hasPermission(memberId, ClanRank.Permission.EDIT_SETTINGS))
                continue;
            Player online = Bukkit.getPlayer(memberId);
            if (online != null && online.isOnline())
                online.sendMessage(message);
        }
    }

    private static String key(UUID from, UUID to) {
        return from + ":" + to;
    }
}