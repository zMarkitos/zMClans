package dev.zm.clan.api;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.clan.model.ClanHome;
import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.service.MemberService;
import dev.zm.clan.zMClans;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Public integration facade. All methods must be called on the server thread. */
public final class ZMClansAPI {

    private final zMClans plugin;

    public ZMClansAPI(zMClans plugin) {
        this.plugin = plugin;
    }

    public Optional<ClanSnapshot> getClan(UUID clanId) {
        requireServerThread();
        return plugin.getClanService().findById(clanId).map(this::snapshot);
    }

    public Optional<ClanSnapshot> getClanByName(String name) {
        requireServerThread();
        return plugin.getClanService().findByName(name).map(this::snapshot);
    }

    public Optional<ClanSnapshot> getClanByPlayer(UUID playerId) {
        requireServerThread();
        return plugin.getClanService().findByPlayer(playerId).map(this::snapshot);
    }

    public Optional<ClanMemberSnapshot> getMember(UUID playerId) {
        requireServerThread();
        return plugin.getMemberService().getMember(playerId).map(this::snapshot);
    }

    public List<ClanMemberSnapshot> getMembers(UUID clanId) {
        requireServerThread();
        return plugin.getMemberService().getMembersOfClan(clanId).stream().map(this::snapshot).toList();
    }

    public boolean hasRankPermission(UUID playerId, String permission) {
        requireServerThread();
        if (permission == null) return false;
        try {
            return plugin.getMemberService().hasPermission(playerId,
                    dev.zm.clan.rank.model.ClanRank.Permission.valueOf(permission.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public Optional<ClanSnapshot> createClan(Player creator, String name, String tag) {
        requireServerThread();
        if (creator == null || !creator.hasPermission("zmclan.player.create")) return Optional.empty();
        if (plugin.getClanService().create(creator, name, tag) != dev.zm.clan.service.ClanService.CreateResult.OK) {
            return Optional.empty();
        }
        return getClanByPlayer(creator.getUniqueId());
    }

    public List<ClanSnapshot> getLeaderboard(String sortBy) {
        requireServerThread();
        return plugin.getClanService().findAll(sortBy).stream().map(this::snapshot).toList();
    }

    public boolean addPoints(UUID clanId, long delta) {
        requireServerThread();
        Clan clan = plugin.getClanService().findById(clanId).orElse(null);
        if (clan == null) return false;
        plugin.getClanService().addPointsAndCheckLevelUp(clan, delta);
        return true;
    }

    public RankChangeResult assignRank(UUID actorId, UUID targetId, String rankId) {
        requireServerThread();
        MemberService.RankResult result = plugin.getMemberService().assignRank(actorId, targetId, rankId);
        return switch (result) {
            case OK -> RankChangeResult.SUCCESS;
            case RANK_NOT_FOUND -> RankChangeResult.RANK_NOT_FOUND;
            case NO_PERMISSION -> RankChangeResult.NO_PERMISSION;
            case SAME_RANK -> RankChangeResult.SAME_RANK;
            case CANNOT_ASSIGN_HIGHER -> RankChangeResult.CANNOT_ASSIGN_HIGHER;
        };
    }

    public ClanSnapshot snapshot(Clan clan) {
        requireServerThread();
        Map<String, ClanHomeSnapshot> homes = clan.getHomes().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> snapshot(entry.getKey(), entry.getValue())));
        return new ClanSnapshot(clan.getId(), clan.getName(), clan.getTag(), clan.getDescription(),
                clan.getLeader(), clan.getCreatedAt(), clan.getMemberCount(),
                plugin.getMemberService().getMaxMembers(clan), clan.getPoints(),
                plugin.getProgressionService().getLevel(clan), clan.getKills(), clan.getDeaths(), clan.getKdr(),
                clan.getAllyIds(), clan.getRivalIds(), homes);
    }

    private ClanHomeSnapshot snapshot(String name, ClanHome home) {
        return new ClanHomeSnapshot(name, home.getWorldName(), home.getX(), home.getY(), home.getZ(),
                home.getYaw(), home.getPitch());
    }

    private ClanMemberSnapshot snapshot(ClanMember member) {
        return new ClanMemberSnapshot(member.getPlayerUUID(), member.getClanId(), member.getRankId(),
                member.getJoinedAt(), member.getKills(), member.getDeaths(), member.getKdr());
    }

    private void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("zMClans API mutations must run on the server thread.");
        }
    }
}