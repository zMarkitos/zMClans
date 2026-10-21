package dev.zm.clan.command;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.config.ColorUtil;
import dev.zm.clan.invitation.model.Invitation;
import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.rank.model.ClanRank;
import dev.zm.clan.service.ClanService;
import dev.zm.clan.service.HomeService;
import dev.zm.clan.service.InviteService;
import dev.zm.clan.service.MemberService;
import dev.zm.clan.service.RelationService;
import dev.zm.clan.zMClans;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class ClanCommand implements CommandExecutor, TabCompleter {

    private final zMClans plugin;

    // Subcommand lists for smart tab-complete
    private static final List<String> ALLY_SUBS = List.of("add", "accept", "deny", "cancel", "remove");
    private static final List<String> RIVAL_SUBS = List.of("add", "accept", "deny", "cancel", "remove");
    private static final List<String> REL_SUBS = List.of("remove");
    private static final List<String> MAIN_SUBS = List.of(
            "menu", "create", "invite", "invites", "members", "settings", "stats",
            "accept", "deny", "kick", "leave", "disband", "info", "list",
            "setrank", "home", "homes", "sethome", "delhome", "chat", "ff",
            "desc", "modtag", "join", "ally", "rival", "relation", "help");

    public ClanCommand(zMClans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessages().get("player-only"));
            return true;
        }

        String permission = PermissionNodes.forPlayerCommand(args);
        if (permission != null && !player.hasPermission(permission)) {
            player.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("gui")) {
            plugin.getMenuManager().openMainMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "ally" -> handleAlly(player, args);
            case "rival" -> handleRival(player, args);
            case "relation" -> handleRelationRemove(player, args);
            case "join" -> handlePublicJoin(player, args);
            case "invite" -> handleInvite(player, args);
            case "invites" -> plugin.getMenuManager().openInvitesMenu(player, 0);
            case "members" -> plugin.getMenuManager().openMembersMenu(player, 0);
            case "settings" -> plugin.getMenuManager().openSettingsMenu(player);
            case "stats" -> plugin.getMenuManager().openStatsMenu(player);
            case "accept" -> handleAccept(player, args);
            case "deny" -> handleDeny(player, args);
            case "kick" -> handleKick(player, args);
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player, args);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player, args);
            case "setrank", "rank", "promote" -> handleSetRank(player, args);
            case "sethome" -> handleSetHome(player, args);
            case "home" -> handleHome(player, args);
            case "homes" -> plugin.getMenuManager().openHomesMenu(player, 0);
            case "delhome", "deletehome" -> handleDeleteHome(player, args);
            case "chat", "c" -> handleChat(player, args);
            case "ff", "friendlyfire" -> handleFriendlyFire(player);
            case "desc", "description" -> handleDescription(player, args);
            case "modtag", "tag" -> handleModTag(player, args);
            case "help" -> handleHelp(player);
            default -> player.sendMessage(plugin.getMessages().get("unknown-command"));
        }

        return true;
    }

    private void handleAlly(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessages().get("ally-usage"));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String targetName = args[2];

        Clan target = resolveClan(targetName);
        if (target == null) {
            player.sendMessage(plugin.getMessages().get("clan-not-found", "clan", targetName));
            return;
        }

        var rs = plugin.getRelationService();
        RelationService.Result result;
        switch (action) {
            case "add" -> result = rs.requestAlly(player, target);
            case "accept" -> result = rs.acceptAlly(player, target);
            case "deny" -> result = rs.denyAlly(player, target);
            case "cancel" -> result = rs.cancelAllyRequest(player, target);
            case "remove" -> result = rs.removeRelation(player, target);
            default -> {
                player.sendMessage(plugin.getMessages().get("ally-usage"));
                return;
            }
        }
        sendAllyResult(player, target, action, result);
    }

    private void sendAllyResult(Player player, Clan target, String action, RelationService.Result result) {
        switch (result) {
            case OK -> {
                Clan own = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
                switch (action) {
                    case "add" -> {
                        if (own != null && own.isAlly(target.getId()))
                            player.sendMessage(plugin.getMessages().get("relation-updated", "clan", target.getName(),
                                    "relation", "ally"));
                        else
                            player.sendMessage(plugin.getMessages().get("ally-request-sent", "clan", target.getName()));
                    }
                    case "accept" -> player.sendMessage(
                            plugin.getMessages().get("relation-updated", "clan", target.getName(), "relation", "ally"));
                    case "deny" ->
                        player.sendMessage(plugin.getMessages().get("ally-request-denied", "clan", target.getName()));
                    case "cancel" -> player
                            .sendMessage(plugin.getMessages().get("ally-request-cancelled", "clan", target.getName()));
                    case "remove" ->
                        player.sendMessage(plugin.getMessages().get("relation-removed", "clan", target.getName()));
                }
            }
            case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
            case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission"));
            case INVALID -> player.sendMessage(plugin.getMessages().get("relation-invalid"));
            case LIMIT -> player.sendMessage(plugin.getMessages().get("relation-limit"));
            case ALREADY -> player.sendMessage(plugin.getMessages().get("relation-already", "clan", target.getName()));
            case PENDING ->
                player.sendMessage(plugin.getMessages().get("ally-request-pending", "clan", target.getName()));
            case NOT_PENDING ->
                player.sendMessage(plugin.getMessages().get("ally-request-missing", "clan", target.getName()));
            case NO_RELATION -> player.sendMessage(plugin.getMessages().get("relation-none", "clan", target.getName()));
        }
    }

    private void handleRival(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessages().get("rival-usage"));
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String targetName = args[2];

        Clan target = resolveClan(targetName);
        if (target == null) {
            player.sendMessage(plugin.getMessages().get("clan-not-found", "clan", targetName));
            return;
        }

        var rs = plugin.getRelationService();
        RelationService.Result result;
        switch (action) {
            case "add" -> result = rs.requestRival(player, target);
            case "accept" -> result = rs.acceptRival(player, target);
            case "deny" -> result = rs.denyRival(player, target);
            case "cancel" -> result = rs.cancelRivalRequest(player, target);
            case "remove" -> result = rs.removeRelation(player, target);
            default -> {
                player.sendMessage(plugin.getMessages().get("rival-usage"));
                return;
            }
        }
        sendRivalResult(player, target, action, result);
    }

    private void sendRivalResult(Player player, Clan target, String action, RelationService.Result result) {
        switch (result) {
            case OK -> {
                Clan own = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
                switch (action) {
                    case "add" -> {
                        if (own != null && own.isRival(target.getId()))
                            player.sendMessage(plugin.getMessages().get("relation-updated", "clan", target.getName(),
                                    "relation", "rival"));
                        else
                            player.sendMessage(
                                    plugin.getMessages().get("rival-request-sent", "clan", target.getName()));
                    }
                    case "accept" -> player.sendMessage(
                            plugin.getMessages().get("relation-updated", "clan", target.getName(), "relation",
                                    "rival"));
                    case "deny" ->
                        player.sendMessage(plugin.getMessages().get("rival-request-denied", "clan", target.getName()));
                    case "cancel" -> player
                            .sendMessage(plugin.getMessages().get("rival-request-cancelled", "clan", target.getName()));
                    case "remove" ->
                        player.sendMessage(plugin.getMessages().get("relation-removed", "clan", target.getName()));
                }
            }
            case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
            case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission"));
            case INVALID -> player.sendMessage(plugin.getMessages().get("relation-invalid"));
            case LIMIT -> player.sendMessage(plugin.getMessages().get("relation-limit"));
            case ALREADY -> player.sendMessage(plugin.getMessages().get("relation-already", "clan", target.getName()));
            case PENDING ->
                player.sendMessage(plugin.getMessages().get("rival-request-pending", "clan", target.getName()));
            case NOT_PENDING ->
                player.sendMessage(plugin.getMessages().get("rival-request-missing", "clan", target.getName()));
            case NO_RELATION -> player.sendMessage(plugin.getMessages().get("relation-none", "clan", target.getName()));
        }
    }

    private void handleRelationRemove(Player player, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("remove")) {
            player.sendMessage(plugin.getMessages().get("relation-remove-usage"));
            return;
        }
        Clan target = resolveClan(args[2]);
        if (target == null) {
            player.sendMessage(plugin.getMessages().get("clan-not-found", "clan", args[2]));
            return;
        }
        RelationService.Result result = plugin.getRelationService().removeRelation(player, target);
        switch (result) {
            case OK -> player.sendMessage(plugin.getMessages().get("relation-removed", "clan", target.getName()));
            case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
            case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission"));
            case INVALID -> player.sendMessage(plugin.getMessages().get("relation-invalid"));
            case NO_RELATION -> player.sendMessage(plugin.getMessages().get("relation-none", "clan", target.getName()));
            default -> player.sendMessage(plugin.getMessages().get("relation-invalid"));
        }
    }

    private Clan resolveClan(String query) {
        return plugin.getClanService().findByName(query)
                .orElseGet(() -> plugin.getClanService().findByTag(query).orElse(null));
    }

    private void handlePublicJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().get("public-join-usage"));
            return;
        }
        if (plugin.getClanService().isInClan(player.getUniqueId())) {
            player.sendMessage(plugin.getMessages().get("already-in-clan"));
            return;
        }
        Clan target = resolveClan(args[1]);
        if (target == null) {
            player.sendMessage(plugin.getMessages().get("clan-not-found", "clan", args[1]));
            return;
        }
        if (!target.isPublicJoin()) {
            player.sendMessage(plugin.getMessages().get("public-join-disabled"));
            return;
        }
        int maxMembers = plugin.getMemberService().getMaxMembers(target);
        if (target.getMemberCount() >= maxMembers) {
            player.sendMessage(plugin.getMessages().get("invite-clan-full", "amount",
                    String.valueOf(target.getMemberCount()), "max",
                    String.valueOf(maxMembers)));
            return;
        }
        String rank = plugin.getRankService().getDefaultRank().map(ClanRank::getId).orElse("member");
        if (!plugin.getMemberService().joinClan(player.getUniqueId(), target.getId(), rank)) {
            player.sendMessage(plugin.getMessages().get("invite-clan-full", "amount",
                    String.valueOf(target.getMemberCount()), "max",
                    String.valueOf(maxMembers)));
            return;
        }
        plugin.getClanService().saveAsync(target);
        player.sendMessage(plugin.getMessages().get("public-join-success", "clan", target.getName()));
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().get("usage-create"));
            return;
        }

        String name = args[1];
        String rawTag = args.length >= 3
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim()
                : "";

        ClanService.CreateResult result = plugin.getClanService().create(player, name, rawTag);
        switch (result) {
            case OK -> {
                Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
                String displayTag = optClan.map(Clan::getTag).orElse(rawTag.isEmpty() ? name : rawTag);
                player.sendMessage(plugin.getMessages().get("clan-created", "clan", name, "tag", displayTag));
            }
            case ALREADY_IN_CLAN -> player.sendMessage(plugin.getMessages().get("already-in-clan"));
            case NAME_INVALID -> player.sendMessage(plugin.getMessages().get("clan-name-invalid",
                    "min", String.valueOf(plugin.getConfigManager().getNameMinLength()),
                    "max", String.valueOf(plugin.getConfigManager().getNameMaxLength())));
            case NAME_BLACKLISTED -> player.sendMessage(plugin.getMessages().get("clan-name-blacklisted"));
            case TAG_INVALID -> player.sendMessage(plugin.getMessages().get("clan-tag-invalid",
                    "min", String.valueOf(plugin.getConfigManager().getTagMinLength()),
                    "max", String.valueOf(plugin.getConfigManager().getTagMaxLength())));
            case TAG_FORMAT_BLOCKED -> player.sendMessage(plugin.getMessages().get("clan-tag-format-blocked"));
            case NAME_TAKEN -> player.sendMessage(plugin.getMessages().get("clan-name-taken", "clan", name));
            case TAG_TAKEN -> {
                String clean = rawTag.isEmpty() ? name : ColorUtil.strip(ColorUtil.colorize(rawTag));
                player.sendMessage(plugin.getMessages().get("clan-tag-taken", "tag", clean));
            }
            case NO_MONEY -> player.sendMessage(plugin.getMessages().get("clan-not-enough-money", "amount",
                    String.valueOf(plugin.getConfigManager().getCreationCost())));
            case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission"));
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            if (plugin.getClanService().isInClan(player.getUniqueId())) {
                plugin.getMenuManager().openInviteMenu(player, 0);
            } else {
                player.sendMessage(plugin.getMessages().get("usage-invite"));
            }
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(plugin.getMessages().get("player-not-found", "player", args[1]));
            return;
        }

        InviteService.InviteResult result = plugin.getInviteService().sendInvite(player.getUniqueId(),
                target.getUniqueId());
        switch (result) {
            case OK -> {
                player.sendMessage(plugin.getMessages().get("invite-sent", "player", target.getName()));
                plugin.getClanService().findByPlayer(player.getUniqueId()).ifPresent(clan -> target.sendMessage(plugin
                        .getMessages().get("invite-received", "clan", clan.getName(), "sender", player.getName())));
            }
            case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-invite"));
            case ALREADY_HAS_CLAN ->
                player.sendMessage(plugin.getMessages().get("invite-player-has-clan", "player", target.getName()));
            case ALREADY_INVITED ->
                player.sendMessage(plugin.getMessages().get("invite-already-sent", "player", target.getName()));
            case CLAN_FULL -> {
                Clan c = plugin.getClanService().findByPlayer(player.getUniqueId()).orElse(null);
                String cur = c != null ? String.valueOf(c.getMemberCount()) : "0";
                player.sendMessage(plugin.getMessages().get("invite-clan-full", "amount", cur, "max",
                        String.valueOf(plugin.getMemberService().getMaxMembers(c))));
            }
            default -> player.sendMessage(plugin.getMessages().get("player-not-found", "player", args[1]));
        }
    }

    private void handleAccept(Player player, String[] args) {
        List<Invitation> pending = plugin.getInviteService().getPending(player.getUniqueId());
        if (pending.isEmpty()) {
            player.sendMessage(plugin.getMessages().get("no-pending-invites"));
            return;
        }

        UUID targetClanId;
        if (args.length >= 2) {
            Optional<Clan> opt = plugin.getClanService().findByName(args[1]);
            if (opt.isEmpty())
                opt = plugin.getClanService().findByTag(args[1]);
            if (opt.isEmpty() || !plugin.getInviteService().hasInviteFrom(player.getUniqueId(), opt.get().getId())) {
                player.sendMessage(plugin.getMessages().get("no-invite-from-clan", "clan", args[1]));
                return;
            }
            targetClanId = opt.get().getId();
        } else {
            targetClanId = pending.get(pending.size() - 1).getClanId();
        }

        if (plugin.getInviteService().accept(player.getUniqueId(), targetClanId)) {
            Clan clan = plugin.getClanService().findById(targetClanId).orElse(null);
            player.sendMessage(
                    plugin.getMessages().get("invite-accepted-receiver", "clan", clan != null ? clan.getName() : ""));
        } else {
            player.sendMessage(plugin.getMessages().get("invite-expired", "clan", ""));
        }
    }

    private void handleDeny(Player player, String[] args) {
        List<Invitation> pending = plugin.getInviteService().getPending(player.getUniqueId());
        if (pending.isEmpty()) {
            player.sendMessage(plugin.getMessages().get("no-pending-invites"));
            return;
        }

        UUID targetClanId;
        if (args.length >= 2) {
            Optional<Clan> opt = plugin.getClanService().findByName(args[1]);
            if (opt.isEmpty())
                opt = plugin.getClanService().findByTag(args[1]);
            if (opt.isEmpty()) {
                player.sendMessage(plugin.getMessages().get("no-invite-from-clan", "clan", args[1]));
                return;
            }
            targetClanId = opt.get().getId();
        } else {
            targetClanId = pending.get(pending.size() - 1).getClanId();
        }

        if (plugin.getInviteService().deny(player.getUniqueId(), targetClanId)) {
            Clan clan = plugin.getClanService().findById(targetClanId).orElse(null);
            player.sendMessage(
                    plugin.getMessages().get("invite-denied-receiver", "clan", clan != null ? clan.getName() : ""));
        } else {
            player.sendMessage(plugin.getMessages().get("no-pending-invites"));
        }
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().get("usage-kick"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        MemberService.KickResult result = plugin.getMemberService().kick(player.getUniqueId(), target.getUniqueId());
        switch (result) {
            case OK -> {
                player.sendMessage(plugin.getMessages().get("member-kicked", "player", args[1]));
                if (target.isOnline() && target.getPlayer() != null) {
                    target.getPlayer().sendMessage(plugin.getMessages().get("kicked-from-clan", "clan", ""));
                }
            }
            case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-kick"));
            case CANNOT_KICK_LEADER -> player.sendMessage(plugin.getMessages().get("cannot-kick-leader"));
            case RANK_TOO_HIGH -> player.sendMessage(plugin.getMessages().get("cannot-kick-higher-rank"));
            case NOT_IN_CLAN -> player.sendMessage(plugin.getMessages().get("not-in-your-clan"));
        }
    }

    private void handleLeave(Player player) {
        Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
        if (optClan.isEmpty()) {
            player.sendMessage(plugin.getMessages().get("no-clan"));
            return;
        }
        Clan clan = optClan.get();
        if (clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMessages().get("leader-cannot-leave"));
            return;
        }

        String title = plugin.getMessages().get("gui-confirm-leave-title");
        plugin.getMenuManager().openConfirm(player, "leave", title, () -> {
            if (plugin.getMemberService().leave(player)) {
                player.sendMessage(plugin.getMessages().get("left-clan", "clan", clan.getName()));
            }
        });
    }

    private void handleDisband(Player player, String[] args) {
        Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
        if (optClan.isEmpty()) {
            player.sendMessage(plugin.getMessages().get("no-clan"));
            return;
        }
        Clan clan = optClan.get();
        if (!clan.getLeader().equals(player.getUniqueId())
                && !plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.DISBAND)) {
            player.sendMessage(plugin.getMessages().get("no-permission"));
            return;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            plugin.getClanService().disband(clan.getId(), player.getName());
            player.sendMessage(plugin.getMessages().get("clan-disbanded", "clan", clan.getName()));
            return;
        }

        String title = plugin.getMessages().get("gui-confirm-disband-title", "clan", clan.getName());
        plugin.getMenuManager().openConfirm(player, "disband", title, () -> {
            plugin.getClanService().disband(clan.getId(), player.getName());
            player.sendMessage(plugin.getMessages().get("clan-disbanded", "clan", clan.getName()));
        });
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length >= 2) {
            String query = args[1];
            Optional<Clan> optClan = plugin.getClanService().findByName(query);
            if (optClan.isEmpty())
                optClan = plugin.getClanService().findByTag(query);
            try {
                if (optClan.isEmpty())
                    optClan = plugin.getClanService().findById(UUID.fromString(query));
            } catch (IllegalArgumentException ignored) {
            }

            if (optClan.isEmpty()) {
                player.sendMessage(plugin.getMessages().get("clan-not-found", "clan", query));
                return;
            }
            // Direct command → back goes to main_menu
            plugin.getMenuManager().openClanInfoDirect(player, optClan.get());
        } else {
            Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
            if (optClan.isEmpty()) {
                player.sendMessage(plugin.getMessages().get("no-clan"));
                plugin.getMenuManager().openMainMenu(player);
                return;
            }
            // Own clan → back goes to main menu
            plugin.getMenuManager().openClanInfoDirect(player, optClan.get());
        }
    }

    private void handleList(Player player, String[] args) {
        plugin.getMenuManager().openClanList(player, 0);
    }

    private void handleSetRank(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessages().get("usage-setrank"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        String rankId = args[2].toLowerCase(Locale.ROOT);
        MemberService.RankResult result = plugin.getMemberService().assignRank(player.getUniqueId(),
                target.getUniqueId(), rankId);
        switch (result) {
            case OK -> {
                String rName = plugin.getRankService().getRank(rankId).map(ClanRank::getDisplayName).orElse(rankId);
                player.sendMessage(plugin.getMessages().get("rank-assigned", "player", args[1], "rank", rName));
            }
            case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-rank"));
            case RANK_NOT_FOUND -> player.sendMessage(plugin.getMessages().get("rank-not-found", "rank", rankId));
            case SAME_RANK -> player.sendMessage(plugin.getMessages().get("same-rank", "player", args[1]));
            case CANNOT_ASSIGN_HIGHER -> player.sendMessage(plugin.getMessages().get("cannot-assign-own-rank"));
        }
    }

    private void handleSetHome(Player player, String[] args) {
        String homeName = args.length > 1 ? args[1] : "default";
        HomeService.SetHomeResult res = plugin.getHomeService().setHome(player, homeName);
        switch (res) {
            case OK -> player.sendMessage(plugin.getMessages().get("home-set", "home", homeName));
            case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-sethome"));
            case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
            case LIMIT_REACHED -> player.sendMessage(plugin.getMessages().get("home-limit", "max",
                    String.valueOf(plugin.getHomeService().getMaxHomes(player))));
        }
    }

    private void handleHome(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMenuManager().openHomesMenu(player, 0);
            return;
        }
        HomeService.TeleportResult res = plugin.getHomeService().teleport(player, args[1]);
        switch (res) {
            case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
            case NO_HOME -> player.sendMessage(plugin.getMessages().get("home-not-set"));
            case WORLD_UNLOADED -> player.sendMessage(plugin.getMessages().get("home-world-unloaded"));
            case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-home"));
            default -> {
            }
        }
    }

    private void handleDeleteHome(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().get("delhome-usage"));
            return;
        }
        if (plugin.getHomeService().deleteHome(player, args[1]))
            player.sendMessage(plugin.getMessages().get("home-deleted", "home", args[1]));
        else
            player.sendMessage(plugin.getMessages().get("home-delete-failed", "home", args[1]));
    }

    private void handleChat(Player player, String[] args) {
        Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
        if (optClan.isEmpty()) {
            player.sendMessage(plugin.getMessages().get("no-clan"));
            return;
        }
        if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.USE_CHAT)) {
            player.sendMessage(plugin.getMessages().get("no-permission-chat"));
            return;
        }

        if (args.length >= 2) {
            String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            plugin.getClanChatListener().sendClanMessage(player, optClan.get(), msg);
        } else {
            boolean toggled = plugin.getClanChatListener().toggleChat(player.getUniqueId());
            player.sendMessage(plugin.getMessages().get(toggled ? "chat-toggled-on" : "chat-toggled-off"));
        }
    }

    private void handleFriendlyFire(Player player) {
        Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
        if (optClan.isEmpty()) {
            player.sendMessage(plugin.getMessages().get("no-clan"));
            return;
        }
        if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.EDIT_SETTINGS)) {
            player.sendMessage(plugin.getMessages().get("no-permission"));
            return;
        }
        Clan clan = optClan.get();
        clan.setFriendlyFire(!clan.isFriendlyFire());
        plugin.getClanService().saveAsync(clan);
        player.sendMessage(
                plugin.getMessages().get(clan.isFriendlyFire() ? "friendly-fire-enabled" : "friendly-fire-disabled"));
    }

    private void handleDescription(Player player, String[] args) {
        Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
        if (optClan.isEmpty()) {
            player.sendMessage(plugin.getMessages().get("no-clan"));
            return;
        }
        if (!plugin.getMemberService().hasPermission(player.getUniqueId(), ClanRank.Permission.EDIT_DESCRIPTION)) {
            player.sendMessage(plugin.getMessages().get("no-permission-description"));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().get("usage-desc"));
            return;
        }

        String desc = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        int max = plugin.getConfigManager().getDescriptionMaxLength();
        if (ColorUtil.strip(desc).length() > max) {
            player.sendMessage(plugin.getMessages().get("description-too-long", "max", String.valueOf(max)));
            return;
        }
        Clan clan = optClan.get();
        clan.setDescription(desc);
        plugin.getClanService().saveAsync(clan);
        player.sendMessage(plugin.getMessages().get("description-updated"));
    }

    private void handleModTag(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().get("usage-modtag"));
            return;
        }

        String rawTag = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        ClanService.ModTagResult res = plugin.getClanService().modifyTag(player, rawTag);
        switch (res) {
            case OK -> {
                Optional<Clan> optClan = plugin.getClanService().findByPlayer(player.getUniqueId());
                String formattedTag = optClan.map(Clan::getTag).orElse(rawTag);
                player.sendMessage(plugin.getMessages().get("tag-updated", "tag", formattedTag));
            }
            case NO_CLAN -> player.sendMessage(plugin.getMessages().get("no-clan"));
            case NO_PERMISSION -> player.sendMessage(plugin.getMessages().get("no-permission-modtag"));
            case TAG_INVALID -> player.sendMessage(plugin.getMessages().get("clan-tag-invalid",
                    "min", String.valueOf(plugin.getConfigManager().getTagMinLength()),
                    "max", String.valueOf(plugin.getConfigManager().getTagMaxLength())));
            case TAG_FORMAT_BLOCKED -> player.sendMessage(plugin.getMessages().get("clan-tag-format-blocked"));
            case TAG_TAKEN -> {
                String clean = ColorUtil.strip(ColorUtil.colorize(rawTag));
                player.sendMessage(plugin.getMessages().get("clan-tag-taken", "tag", clean));
            }
        }
    }

    private void handleHelp(Player player) {
        player.sendMessage(plugin.getMessages().get("help-header"));
        for (String line : plugin.getMessages().getList("help-lines"))
            player.sendMessage(line);
        for (String line : plugin.getMessages().getList("relation-command-help"))
            player.sendMessage(line);
        player.sendMessage(plugin.getMessages().get("help-footer"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
            @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filterStart(MAIN_SUBS.stream()
                    .filter(subcommand -> {
                        String permission = PermissionNodes.forPlayerCommand(new String[] { subcommand });
                        return permission == null || sender.hasPermission(permission);
                    }).toList(), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String permission = PermissionNodes.forPlayerCommand(args);
        if (permission == null || !sender.hasPermission(permission))
            return Collections.emptyList();

        if (args.length == 2) {
            switch (sub) {
                case "ally" -> {
                    return filterStart(ALLY_SUBS, args[1]);
                }
                case "rival" -> {
                    return filterStart(RIVAL_SUBS, args[1]);
                }
                case "relation" -> {
                    return filterStart(REL_SUBS, args[1]);
                }
                case "home", "delhome", "deletehome" -> {
                    if (sender instanceof Player p) {
                        return plugin.getClanService().findByPlayer(p.getUniqueId())
                                .map(c -> filterStart(new ArrayList<>(c.getHomes().keySet()), args[1]))
                                .orElse(Collections.emptyList());
                    }
                }
                case "invite", "kick" -> {
                    return onlinePlayerNames(args[1]);
                }
                case "info", "accept", "deny", "join" -> {
                    return clanNames(args[1]);
                }
                case "setrank", "rank", "promote" -> {
                    return onlinePlayerNames(args[1]);
                }
            }
        }

        // Third argument: clan name for ally/rival/relation sub-actions
        if (args.length == 3) {
            switch (sub) {
                case "ally", "rival", "relation" -> {
                    String action = args[1].toLowerCase(Locale.ROOT);
                    // For ally add/accept/deny/cancel/remove → suggest clan names
                    // For rival add/remove → suggest clan names
                    return clanNames(args[2]);
                }
                case "setrank", "rank", "promote" -> {
                    return plugin.getRankService().getAllRanks().stream()
                            .map(ClanRank::getId)
                            .filter(r -> r.startsWith(args[2].toLowerCase(Locale.ROOT)))
                            .collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterStart(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(lower)).collect(Collectors.toList());
    }

    private List<String> onlinePlayerNames(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    private List<String> clanNames(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return plugin.getClanService().allClans().stream()
                .map(Clan::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}
