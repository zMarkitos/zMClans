package dev.zm.clan.hook;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.config.ColorUtil;
import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.zMClans;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * PlaceholderAPI expansion for zMClans.
 * Supports %zmclans_...% and relation-scoped %rel_zmclans_relation_...%
 * placeholders.
 *
 * Top placeholders (new format):
 * %zmclans_top_<sort>_<rank>_<field>%
 * <sort> : points | kdr | kills | deaths | members
 * <rank> : 1-based integer
 * <field> : name | tag | value
 *
 * Examples:
 * %zmclans_top_points_1_name% -> name of the clan ranked #1 by points
 * %zmclans_top_points_1_tag% -> tag of the clan ranked #1 by points
 * %zmclans_top_points_1_value% -> points of the clan ranked #1
 * %zmclans_top_kdr_2_value% -> kdr of the clan ranked #2 by kdr
 * %zmclans_top_members_3_name% -> name of the clan ranked #3 by member count
 */
public final class ClanPlaceholderExpansion extends PlaceholderExpansion implements Relational {

    private static final Set<String> TOP_SORTS = Set.of("POINTS", "KDR", "KILLS", "DEATHS", "MEMBERS");
    private static final Set<String> TOP_FIELDS = Set.of("name", "tag", "value");

    private final zMClans plugin;

    public ClanPlaceholderExpansion(zMClans plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "zmclans";
    }

    @Override
    public @NotNull String getAuthor() {
        return "zMarkitos_";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null)
            return "";

        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("top_")) {
            String[] parts = p.split("_");
            if (parts.length == 4) {
                String sort = parts[1].toUpperCase(Locale.ROOT);
                String field = parts[3];
                if (TOP_SORTS.contains(sort) && TOP_FIELDS.contains(field)) {
                    try {
                        int rank = Integer.parseInt(parts[2]);
                        return getTopField(rank, sort, field);
                    } catch (NumberFormatException ignored) {
                        // fall through to legacy handling / general switch
                    }
                }
            }
        }

        if (p.startsWith("top:") || p.startsWith("top_name:")) {
            int rank = parseRankSuffix(p);
            return getTopClan(rank, "MEMBERS").map(Clan::getName).orElse("");
        }
        if (p.startsWith("top_tag:")) {
            int rank = parseRankSuffix(p);
            return getTopClan(rank, "MEMBERS").map(Clan::getTag).orElse("");
        }
        if (p.startsWith("top_points:")) {
            int rank = parseRankSuffix(p);
            return getTopClan(rank, "POINTS").map(c -> String.valueOf(c.getPoints())).orElse("0");
        }
        if (p.startsWith("top_members:")) {
            int rank = parseRankSuffix(p);
            return getTopClan(rank, "MEMBERS").map(c -> String.valueOf(c.getMemberCount())).orElse("0");
        }
        if (p.startsWith("top_kills:")) {
            int rank = parseRankSuffix(p);
            return getTopClan(rank, "KILLS").map(c -> String.valueOf(c.getKills())).orElse("0");
        }
        if (p.startsWith("top_kdr:")) {
            int rank = parseRankSuffix(p);
            return getTopClan(rank, "KDR").map(c -> String.format(Locale.ROOT, "%.2f", c.getKdr())).orElse("0.00");
        }
        if (p.startsWith("top_deaths:")) {
            int rank = parseRankSuffix(p);
            return getTopClan(rank, "DEATHS").map(c -> String.valueOf(c.getDeaths())).orElse("0");
        }
        if (p.startsWith("top_leader:")) {
            int rank = parseRankSuffix(p);
            return getTopClan(rank, "MEMBERS").map(c -> {
                OfflinePlayer op = Bukkit.getOfflinePlayer(c.getLeader());
                return op.getName() != null ? op.getName() : "Unknown";
            }).orElse("");
        }

        if (p.startsWith("search_player_clan:")) {
            String targetName = params.substring("search_player_clan:".length()).trim();
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            return plugin.getClanService().findByPlayer(target.getUniqueId()).map(Clan::getName).orElse("");
        }

        Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
        var optMember = plugin.getMemberService().getMember(player.getUniqueId());

        return switch (p) {
            case "hasclan", "has_clan", "in_clan" -> optClan.isPresent() ? "true" : "false";
            case "hasclan_formated", "hasclan_formatted" ->
                optClan.map(c -> c.hasCustomTag() ? c.getTag() : c.getName()).orElse("");
            case "hasclan_formated_nocolor" -> optClan.map(c -> ColorUtil.strip(c.getTag())).orElse("");
            case "clan", "clan_name", "name" -> optClan.map(Clan::getName).orElse("");
            case "clanid", "clan_id", "id", "uuid" -> optClan.map(c -> c.getId().toString()).orElse("");

            case "modtag" -> optClan.map(c -> c.hasCustomTag() ? c.getTag() : c.getName()).orElse("");
            case "tag" -> optClan.map(Clan::getTag).orElse("");
            case "tag_color", "tag_formated", "tag_formatted" -> optClan.map(Clan::getTag).orElse("");
            case "tag_nocolor", "tag_formated_nocolor", "tag_stripped" ->
                optClan.map(c -> ColorUtil.strip(c.getTag())).orElse("");

            case "leader", "leader_name" -> optClan.map(c -> {
                OfflinePlayer leader = Bukkit.getOfflinePlayer(c.getLeader());
                return leader.getName() != null ? leader.getName() : "Unknown";
            }).orElse("");
            case "leader_uuid" -> optClan.map(c -> c.getLeader().toString()).orElse("");
            case "is_leader" ->
                optClan.filter(c -> c.getLeader().equals(player.getUniqueId())).isPresent() ? "true" : "false";
            case "leader_formated" -> optClan.map(c -> {
                OfflinePlayer leader = Bukkit.getOfflinePlayer(c.getLeader());
                String lName = leader.getName() != null ? leader.getName() : "Unknown";
                return ColorUtil.colorize("&#55FFAA" + lName);
            }).orElse("");

            case "members", "clan_members", "count_members", "member_count" ->
                optClan.map(c -> String.valueOf(c.getMemberCount())).orElse("0");
            case "player_kills", "member_kills" -> optMember.map(m -> String.valueOf(m.getKills())).orElse("0");
            case "player_deaths", "member_deaths" -> optMember.map(m -> String.valueOf(m.getDeaths())).orElse("0");
            case "player_kdr", "member_kdr" -> optMember
                    .map(m -> String.format(Locale.ROOT, "%.2f", m.getKdr())).orElse("0.00");
            case "onlinemembers", "clan_onlinemembers", "count_onlinemembers", "online_members" -> optClan.map(c -> {
                long online = c.getMemberIds().stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .filter(Player::isOnline)
                        .count();
                return String.valueOf(online);
            }).orElse("0");
            case "slots", "slots_total", "max_members", "limit_members" -> optClan
                    .map(c -> String.valueOf(plugin.getMemberService().getMaxMembers(c)))
                    .orElse(String.valueOf(plugin.getMemberService().getMaxMembers(null)));

            case "points", "clan_points" -> optClan.map(c -> String.format("%,d", c.getPoints())).orElse("0");
            case "points_unformated", "points_raw" -> optClan.map(c -> String.valueOf(c.getPoints())).orElse("0");
            case "current_position", "position", "top" -> optClan.map(c -> {
                String mode = player.isOnline() && plugin.getMenuManager() != null
                        ? plugin.getMenuManager().getLeaderboardMode(player.getPlayer())
                        : "MEMBERS";
                List<Clan> all = plugin.getClanService().findAll(mode);
                int pos = all.indexOf(c);
                return pos >= 0 ? String.valueOf(pos + 1) : "0";
            }).orElse("0");

            case "desc", "description", "desc_color" -> optClan.map(Clan::getDescription).orElse("");
            case "desc_nocolor" -> optClan.map(c -> ColorUtil.strip(c.getDescription())).orElse("");

            case "is_ff", "global_ff", "friendly_fire" ->
                optClan.map(c -> String.valueOf(c.isFriendlyFire())).orElse("false");
            case "is_ff_formated", "global_ff_formated", "friendly_fire_formatted" ->
                optClan.map(c -> c.isFriendlyFire() ? "ON" : "OFF").orElse("OFF");
            case "isopen", "open_invites", "open_invitations" ->
                optClan.map(c -> String.valueOf(c.isOpenInvitations())).orElse("false");
            case "isopen_formated", "open_invites_formatted" ->
                optClan.map(c -> c.isOpenInvitations() ? "ON" : "OFF").orElse("OFF");
            case "chat_active" -> String.valueOf(plugin.getClanChatListener() != null
                    && plugin.getClanChatListener().isInClanChat(player.getUniqueId()));
            case "chat_active_formatted" -> (plugin.getClanChatListener() != null
                    && plugin.getClanChatListener().isInClanChat(player.getUniqueId())) ? "ON" : "OFF";

            case "hashome", "has_home" -> optClan.map(c -> String.valueOf(c.getHome() != null)).orElse("false");
            case "home", "home_location" -> optClan.map(c -> c.getHome() != null
                    ? (c.getHome().getWorldName() + " (" + (int) c.getHome().getX() + ", " + (int) c.getHome().getY()
                            + ", " + (int) c.getHome().getZ() + ")")
                    : "Not set").orElse("Not set");
            case "home_world" -> optClan.map(c -> c.getHome() != null ? c.getHome().getWorldName() : "").orElse("");
            case "home_x" ->
                optClan.map(c -> c.getHome() != null ? String.valueOf((int) c.getHome().getX()) : "").orElse("");
            case "home_y" ->
                optClan.map(c -> c.getHome() != null ? String.valueOf((int) c.getHome().getY()) : "").orElse("");
            case "home_z" ->
                optClan.map(c -> c.getHome() != null ? String.valueOf((int) c.getHome().getZ()) : "").orElse("");

            case "creation_date", "creation_date_formated", "created_at" -> optClan.map(c -> {
                String d = c.getCreatedAt().toString();
                return d.length() >= 10 ? d.substring(0, 10) : d;
            }).orElse("");
            case "creation_hours", "created_timestamp" ->
                optClan.map(c -> String.valueOf(c.getCreatedAt().getEpochSecond())).orElse("0");

            case "player", "player_name" -> player.getName() != null ? player.getName() : "";
            case "player_role", "player_role_name", "role", "rank" ->
                plugin.getMemberService().getMember(player.getUniqueId())
                        .map(ClanMember::getRankId).orElse("");
            case "player_role_formated", "role_formated",
                    "rank_formated" ->
                plugin.getMemberService().getMember(player.getUniqueId())
                        .flatMap(m -> plugin.getRankService().getRank(m.getRankId()))
                        .map(r -> ColorUtil.colorize(r.getColor() + r.getDisplayName())).orElse("");
            case "player_role_prefix", "role_prefix",
                    "rank_prefix" ->
                plugin.getMemberService().getMember(player.getUniqueId())
                        .flatMap(m -> plugin.getRankService().getRank(m.getRankId()))
                        .map(r -> ColorUtil.colorize(r.getPrefix())).orElse("");
            case "player_joindate", "player_joindate_formated" ->
                plugin.getMemberService().getMember(player.getUniqueId())
                        .map(m -> {
                            String jd = m.getJoinedAt().toString();
                            return jd.length() >= 10 ? jd.substring(0, 10) : jd;
                        }).orElse("");
            case "player_permissions_amount" -> plugin.getMemberService().getMember(player.getUniqueId())
                    .flatMap(m -> plugin.getRankService().getRank(m.getRankId()))
                    .map(r -> String
                            .valueOf(Arrays.stream(ClanRank.Permission.values()).filter(r::hasPermission).count()))
                    .orElse("0");

            case "banner_material" -> "SHIELD";
            case "hasbanner", "hasbanner_formated" -> "true";
            case "money_currency", "bank_balance" -> optClan.map(c -> String.valueOf(c.getPoints())).orElse("0");
            case "bank_balance_formated" -> optClan.map(c -> String.format("%,d", c.getPoints())).orElse("0");
            case "kdr", "clan_kdr" -> optClan.map(c -> String.format(Locale.ROOT, "%.2f", c.getKdr())).orElse("0.00");
            case "kills", "clan_kills" -> optClan.map(c -> String.valueOf(c.getKills())).orElse("0");
            case "deaths", "clan_deaths" -> optClan.map(c -> String.valueOf(c.getDeaths())).orElse("0");
            case "allies", "clan_allies" -> optClan.map(c -> String.valueOf(c.getAllyIds().size())).orElse("0");
            case "rivals", "clan_rivals" -> optClan.map(c -> String.valueOf(c.getRivalIds().size())).orElse("0");
            case "level", "clan_level" ->
                optClan.map(c -> String.valueOf(plugin.getProgressionService().getLevel(c))).orElse("0");
            case "level_next_points" ->
                optClan.map(c -> String.valueOf(plugin.getProgressionService().getPointsForNextLevel(c))).orElse("0");
            case "level_points_needed" ->
                optClan.map(c -> String.valueOf(plugin.getProgressionService().getPointsNeeded(c))).orElse("0");
            case "souls", "clan_souls" -> "0";
            case "verified", "verified_tag", "verified_raw" -> "true";

            default -> null;
        };
    }

    String getRelationPlaceholder(Player one, Player two, String identifier) {
        if (one == null || two == null)
            return "";
        if (identifier == null || !identifier.toLowerCase(Locale.ROOT).startsWith("relation_"))
            return null;

        Optional<Clan> clanOne = plugin.getClanService().findByPlayer(one.getUniqueId());
        Optional<Clan> clanTwo = plugin.getClanService().findByPlayer(two.getUniqueId());

        boolean sameClan = clanOne.isPresent() && clanTwo.isPresent()
                && clanOne.get().getId().equals(clanTwo.get().getId());
        boolean ally = !sameClan && clanOne.isPresent() && clanTwo.isPresent()
                && clanOne.get().isAlly(clanTwo.get().getId()) && clanTwo.get().isAlly(clanOne.get().getId());
        boolean rival = !sameClan && clanOne.isPresent() && clanTwo.isPresent()
                && clanOne.get().isRival(clanTwo.get().getId()) && clanTwo.get().isRival(clanOne.get().getId());
        String relation = sameClan ? "sameclan" : ally ? "ally" : rival ? "rival" : "neutral";
        String relationText = plugin.getConfig().getString("relations." + relation + ".text", switch (relation) {
            case "sameclan" -> "&a";
            case "ally" -> "&d";
            default -> "&f";
        });
        if (relationText == null || relationText.length() > 64)
            relationText = "&f";
        String id = identifier.substring("relation_".length()).toLowerCase(Locale.ROOT);
        return switch (id) {
            case "relboolean_member" -> sameClan ? "true" : "false";
            case "relboolean_ally" -> ally ? "true" : "false";
            case "relboolean_rival" -> rival ? "true" : "false";
            case "relboolean_neutral" -> (!sameClan && !ally && !rival) ? "true" : "false";
            case "relboolean_sameclan" -> sameClan ? "true" : "false";
            case "relcolor", "reltext" -> relationText;
            case "reltext_coloured", "reltext_colored" -> ColorUtil.colorize(relationText);
            case "reltype" -> relation;
            default -> null;
        };
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player one, Player two, String identifier) {
        return getRelationPlaceholder(one, two, identifier);
    }

    private String getTopField(int rank, String sort, String field) {
        if (rank <= 0)
            return defaultFor(sort, field);
        return getTopClan(rank, sort).map(c -> switch (field) {
            case "name" -> c.getName();
            case "tag" -> c.getTag();
            case "value" -> switch (sort) {
                case "POINTS" -> String.valueOf(c.getPoints());
                case "KDR" -> String.format(Locale.ROOT, "%.2f", c.getKdr());
                case "KILLS" -> String.valueOf(c.getKills());
                case "DEATHS" -> String.valueOf(c.getDeaths());
                case "MEMBERS" -> String.valueOf(c.getMemberCount());
                default -> "";
            };
            default -> "";
        }).orElse(defaultFor(sort, field));
    }

    private String defaultFor(String sort, String field) {
        if (!"value".equals(field))
            return "";
        return switch (sort) {
            case "KDR" -> "0.00";
            case "POINTS", "KILLS", "DEATHS", "MEMBERS" -> "0";
            default -> "";
        };
    }

    private Optional<Clan> getTopClan(int rank1Indexed) {
        return getTopClan(rank1Indexed, "MEMBERS");
    }

    private Optional<Clan> getTopClan(int rank1Indexed, String sortMode) {
        if (rank1Indexed <= 0)
            return Optional.empty();
        List<Clan> all = plugin.getClanService().findAll(sortMode);
        int index = rank1Indexed - 1;
        if (index >= 0 && index < all.size()) {
            return Optional.of(all.get(index));
        }
        return Optional.empty();
    }

    private int parseRankSuffix(String param) {
        int idx = param.lastIndexOf(':');
        if (idx >= 0 && idx + 1 < param.length()) {
            try {
                return Integer.parseInt(param.substring(idx + 1).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }
}