package dev.zm.clan.command;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.member.model.ClanMember;
import dev.zm.clan.menu.ActiveMenu;
import dev.zm.clan.rank.model.ClanRank;
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

/**
 * Admin command executor and tab completer for /zmclan.
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final int CLANS_PER_PAGE = 10;
    private static final int MEMBERS_PER_PAGE = 10;

    private final zMClans plugin;

    public AdminCommand(zMClans plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label,
            @NotNull String[] args) {
        String permission = PermissionNodes.forAdminCommand(args);
        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "disband", "delete" -> handleDisband(sender, args);
            case "setleader" -> handleSetLeader(sender, args);
            case "points" -> handlePoints(sender, args);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender, args);
            case "members" -> handleMembers(sender, args);
            default -> sender.sendMessage(plugin.getMessages().get("unknown-command"));
        }

        return true;
    }

    private void handleReload(CommandSender sender) {
        if (plugin.getDatabaseManager() != null && !plugin.getDatabaseManager().awaitPendingWrites()) {
            sender.sendMessage(plugin.getMessages().get("reload-database-flush-failed"));
            return;
        }

        // 1. Close all active menus so players don't interact with stale UI definitions
        if (plugin.getMenuManager() != null) {
            plugin.getMenuManager().closeAllMenus();
        }

        // 2. Reload core configs and cached notifications
        plugin.getConfigManager().load();
        plugin.getConfigManager().reloadNotifications();

        // 3. Reload progression (levels.yml)
        if (plugin.getProgressionService() != null) {
            plugin.getProgressionService().reload();
        }

        // 4. Reload language messages
        plugin.getMessages().load();

        // 5. Reload ranks configuration (ranks.yml)
        if (plugin.getRankService() != null) {
            plugin.getRankService().load();
        }

        // 6. Reload menus from guis/*.yml
        if (plugin.getMenuManager() != null) {
            plugin.getMenuManager().loadAll();
        }

        // 7. Reschedule AutoSaveTask if the interval changed in config.yml
        if (plugin.getAutoSaveTask() != null) {
            try {
                plugin.getAutoSaveTask().cancel();
            } catch (IllegalStateException ignored) {
            }
            dev.zm.clan.task.AutoSaveTask newTask = new dev.zm.clan.task.AutoSaveTask(plugin);
            plugin.setAutoSaveTask(newTask);
            newTask.start();
        }

        sender.sendMessage(plugin.getMessages().get("reload-success"));
    }

    private void handleDisband(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessages().get("admin-disband-usage"));
            return;
        }
        Optional<Clan> optClan = plugin.getClanService().findByName(args[1]);
        if (optClan.isEmpty())
            optClan = plugin.getClanService().findByTag(args[1]);

        if (optClan.isEmpty()) {
            sender.sendMessage(plugin.getMessages().get("clan-not-found", "clan", args[1]));
            return;
        }

        Clan clan = optClan.get();
        plugin.getClanService().disband(clan.getId(), sender.getName());
        sender.sendMessage(plugin.getMessages().get("admin-disband-success", "clan", clan.getName()));
    }

    private void handleSetLeader(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getMessages().get("admin-setleader-usage"));
            return;
        }

        Optional<Clan> optClan = plugin.getClanService().findByName(args[1]);
        if (optClan.isEmpty())
            optClan = plugin.getClanService().findByTag(args[1]);

        if (optClan.isEmpty()) {
            sender.sendMessage(plugin.getMessages().get("clan-not-found", "clan", args[1]));
            return;
        }

        Clan clan = optClan.get();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);

        if (!clan.hasMember(target.getUniqueId())) {
            sender.sendMessage(plugin.getMessages().get("not-in-your-clan"));
            return;
        }

        String leaderRank = plugin.getRankService().getLeaderRank().map(ClanRank::getId).orElse("leader");
        String defaultRank = plugin.getRankService().getDefaultRank().map(ClanRank::getId).orElse("member");

        UUID oldLeader = clan.getLeader();
        UUID newLeader = target.getUniqueId();

        if (oldLeader.equals(newLeader)) {
            sender.sendMessage(plugin.getMessages().get("admin-setleader-usage"));
            return;
        }

        clan.getMember(oldLeader).ifPresent(m -> m.setRankId(defaultRank));
        clan.getMember(newLeader).ifPresent(m -> m.setRankId(leaderRank));

        plugin.getMemberService().getMember(oldLeader).ifPresent(m -> m.setRankId(defaultRank));
        plugin.getMemberService().getMember(newLeader).ifPresent(m -> m.setRankId(leaderRank));

        clan.setLeader(newLeader);
        clan.setLeaderRankId(leaderRank);

        plugin.getDatabaseManager().executeAsync("transfer clan leadership " + clan.getId(), () -> {
            plugin.getMemberService().getMemberRepo().updateRank(oldLeader, defaultRank);
            plugin.getMemberService().getMemberRepo().updateRank(newLeader, leaderRank);
        });
        plugin.getClanService().saveAsync(clan);

        refreshOpenMenu(oldLeader);
        refreshOpenMenu(newLeader);

        sender.sendMessage(plugin.getMessages().get("admin-setleader-success", "clan", clan.getName(), "player",
                target.getName() != null ? target.getName() : args[2]));
    }

    private void refreshOpenMenu(UUID playerId) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null || !p.isOnline())
            return;

        ActiveMenu menu = plugin.getMenuManager().getOpenMenu(p).orElse(null);
        if (menu == null)
            return;

        String key = menu.getMenuKey();
        int page = menu.getPage();
        UUID targetClanId = menu.getTargetClanId();

        p.closeInventory();
        plugin.getMenuManager().onClose(p);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline())
                return;
            switch (key) {
                case "clan_main", "clan_start" -> plugin.getMenuManager().openMainMenu(p);
                case "clan_members" -> plugin.getMenuManager().openMembersMenu(p, page, targetClanId);
                case "clan_info" -> {
                    if (targetClanId != null) {
                        plugin.getClanService().findById(targetClanId)
                                .ifPresent(c -> plugin.getMenuManager().openClanInfoDirect(p, c));
                    }
                }
                case "clan_list" -> plugin.getMenuManager().openClanList(p, page);
                case "clan_homes" -> plugin.getMenuManager().openHomesMenu(p, page);
                case "clan_invite" -> plugin.getMenuManager().openInviteMenu(p, page);
                case "clan_invites" -> plugin.getMenuManager().openInvitesMenu(p, page);
                case "clan_settings" -> plugin.getMenuManager().openSettingsMenu(p);
                default -> {
                }
            }
        });
    }

    private void handlePoints(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.getMessages().get("admin-points-usage"));
            return;
        }

        String operation = args[1].toLowerCase(Locale.ROOT);
        Optional<Clan> optClan = plugin.getClanService().findByName(args[2]);
        if (optClan.isEmpty())
            optClan = plugin.getClanService().findByTag(args[2]);

        if (optClan.isEmpty()) {
            sender.sendMessage(plugin.getMessages().get("clan-not-found", "clan", args[2]));
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getMessages().get("admin-points-invalid-amount"));
            return;
        }
        if (amount < 0) {
            sender.sendMessage(plugin.getMessages().get("admin-points-invalid-amount"));
            return;
        }

        Clan clan = optClan.get();
        switch (operation) {
            case "add" -> plugin.getClanService().addPointsAndCheckLevelUp(clan, amount);
            case "remove" -> plugin.getClanService().addPointsAndCheckLevelUp(clan, -amount);
            case "set" -> plugin.getClanService().setPointsAndCheckLevelUp(clan, amount);
            default -> {
                sender.sendMessage(plugin.getMessages().get("admin-points-invalid-op"));
                return;
            }
        }

        sender.sendMessage(plugin.getMessages().get("admin-points-success", "clan", clan.getName(), "amount",
                String.valueOf(clan.getPoints())));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessages().get("admin-info-usage"));
            return;
        }

        Optional<Clan> optClan = plugin.getClanService().findByName(args[1]);
        if (optClan.isEmpty())
            optClan = plugin.getClanService().findByTag(args[1]);

        if (optClan.isEmpty()) {
            sender.sendMessage(plugin.getMessages().get("clan-not-found", "clan", args[1]));
            return;
        }

        Clan clan = optClan.get();
        OfflinePlayer leader = Bukkit.getOfflinePlayer(clan.getLeader());
        String leaderName = leader.getName() != null ? leader.getName() : clan.getLeader().toString();
        String dateStr = clan.getCreatedAt().toString().length() >= 10 ? clan.getCreatedAt().toString().substring(0, 10)
                : clan.getCreatedAt().toString();
        String homeStr = clan.getHome() != null
                ? clan.getHome().getWorldName() + " (" + (int) clan.getHome().getX() + ", "
                        + (int) clan.getHome().getY() + ", " + (int) clan.getHome().getZ() + ")"
                : plugin.getMessages().get("admin-info-home-none");
        String ffStr = clan.isFriendlyFire() ? plugin.getMessages().get("admin-info-ff-enabled")
                : plugin.getMessages().get("admin-info-ff-disabled");

        sender.sendMessage(plugin.getMessages().get("admin-info-header"));
        sender.sendMessage(plugin.getMessages().get("admin-info-clan", "clan", clan.getName(), "tag", clan.getTag()));
        sender.sendMessage(plugin.getMessages().get("admin-info-uuid", "uuid", clan.getId().toString()));
        sender.sendMessage(plugin.getMessages().get("admin-info-leader", "player", leaderName, "uuid",
                clan.getLeader().toString()));
        sender.sendMessage(
                plugin.getMessages().get("admin-info-members", "amount", String.valueOf(clan.getMemberCount()), "max",
                        String.valueOf(plugin.getMemberService().getMaxMembers(clan))));
        sender.sendMessage(plugin.getMessages().get("admin-info-points", "points", String.valueOf(clan.getPoints())));
        sender.sendMessage(plugin.getMessages().get("admin-info-friendly-fire", "status", ffStr));
        sender.sendMessage(plugin.getMessages().get("admin-info-created", "date", dateStr));
        sender.sendMessage(plugin.getMessages().get("admin-info-home", "home", homeStr));
        sender.sendMessage(plugin.getMessages().get("admin-info-footer"));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(plugin.getMessages().get("admin-list-usage"));
            return;
        }
        int requestedPage = 1;
        if (args.length > 1) {
            try {
                requestedPage = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.getMessages().get("admin-list-usage"));
                return;
            }
        }
        if (requestedPage < 1) {
            sender.sendMessage(plugin.getMessages().get("admin-list-usage"));
            return;
        }

        List<Clan> clans = plugin.getClanService().findAll();
        if (clans.isEmpty()) {
            sender.sendMessage(plugin.getMessages().get("admin-list-empty"));
            return;
        }

        int maxPage = Math.max(1, (clans.size() + CLANS_PER_PAGE - 1) / CLANS_PER_PAGE);
        if (requestedPage > maxPage) {
            sender.sendMessage(plugin.getMessages().get("admin-list-page-invalid", "max", String.valueOf(maxPage)));
            return;
        }

        int start = (requestedPage - 1) * CLANS_PER_PAGE;
        int end = Math.min(start + CLANS_PER_PAGE, clans.size());
        sender.sendMessage(plugin.getMessages().get("admin-list-header",
                "page", String.valueOf(requestedPage),
                "max", String.valueOf(maxPage)));
        for (int index = start; index < end; index++) {
            Clan clan = clans.get(index);
            sender.sendMessage(plugin.getMessages().get("admin-list-entry",
                    "position", String.valueOf(index + 1),
                    "clan", clan.getName(),
                    "tag", clan.getTag().isBlank() ? clan.getName() : clan.getTag(),
                    "members", String.valueOf(clan.getMemberCount()),
                    "points", String.valueOf(clan.getPoints()),
                    "level", String.valueOf(plugin.getProgressionService().getLevel(clan))));
        }
        sender.sendMessage(plugin.getMessages().get("admin-list-footer"));
    }

    private void handleMembers(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(plugin.getMessages().get("admin-members-usage"));
            return;
        }

        String query = args[1];
        Optional<Clan> optClan = plugin.getClanService().findByName(query);
        if (optClan.isEmpty()) {
            optClan = plugin.getClanService().findByTag(query);
        }
        if (optClan.isEmpty()) {
            sender.sendMessage(plugin.getMessages().get("clan-not-found", "clan", query));
            return;
        }

        Clan clan = optClan.get();
        List<ClanMember> members = plugin.getMemberService().getMembersOfClan(clan.getId());
        int requestedPage = 1;
        if (args.length == 3) {
            try {
                requestedPage = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.getMessages().get("admin-members-usage"));
                return;
            }
        }
        if (requestedPage < 1) {
            sender.sendMessage(plugin.getMessages().get("admin-members-usage"));
            return;
        }
        if (members.isEmpty()) {
            sender.sendMessage(plugin.getMessages().get("admin-members-header",
                    "clan", clan.getName(), "amount", "0", "page", "1", "max", "1"));
            sender.sendMessage(plugin.getMessages().get("admin-members-empty"));
            sender.sendMessage(plugin.getMessages().get("admin-members-footer"));
            return;
        }

        int maxPage = Math.max(1, (members.size() + MEMBERS_PER_PAGE - 1) / MEMBERS_PER_PAGE);
        if (requestedPage > maxPage) {
            sender.sendMessage(plugin.getMessages().get("admin-list-page-invalid", "max", String.valueOf(maxPage)));
            return;
        }
        int start = (requestedPage - 1) * MEMBERS_PER_PAGE;
        int end = Math.min(start + MEMBERS_PER_PAGE, members.size());
        sender.sendMessage(plugin.getMessages().get("admin-members-header",
                "clan", clan.getName(), "amount", String.valueOf(members.size()),
                "page", String.valueOf(requestedPage), "max", String.valueOf(maxPage)));
        for (int index = start; index < end; index++) {
            ClanMember member = members.get(index);
            Player onlinePlayer = Bukkit.getPlayer(member.getPlayerUUID());
            OfflinePlayer offlinePlayer = onlinePlayer == null
                    ? Bukkit.getOfflinePlayer(member.getPlayerUUID())
                    : null;
            String name = onlinePlayer != null ? onlinePlayer.getName()
                    : offlinePlayer != null && offlinePlayer.getName() != null
                            ? offlinePlayer.getName()
                            : member.getPlayerUUID().toString().substring(0, 8);
            String rank = plugin.getRankService().getRank(member.getRankId())
                    .map(ClanRank::getDisplayName)
                    .orElse(member.getRankId());
            String status = plugin.getMessages().get(onlinePlayer == null
                    ? "admin-member-offline"
                    : "admin-member-online");
            sender.sendMessage(plugin.getMessages().get("admin-members-entry",
                    "position", String.valueOf(index + 1),
                    "player", name,
                    "rank", rank,
                    "status", status));
        }
        sender.sendMessage(plugin.getMessages().get("admin-members-footer"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessages().get("admin-help-header"));
        for (String line : plugin.getMessages().getList("admin-help-lines")) {
            sender.sendMessage(line);
        }
        sender.sendMessage(plugin.getMessages().get("admin-help-footer"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
            @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("reload", "disband", "delete", "setleader", "points", "info", "list", "members", "help")
                    .stream()
                    .filter(s -> sender.hasPermission(PermissionNodes.forAdminCommand(new String[] { s })))
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        String permission = PermissionNodes.forAdminCommand(args);
        if (permission == null || !sender.hasPermission(permission))
            return Collections.emptyList();
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("disband") || sub.equals("delete") || sub.equals("setleader")
                    || sub.equals("info") || sub.equals("members")) {
                return plugin.getClanService().allClans().stream().map(Clan::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
            if (sub.equals("list")) {
                return List.of("1", "2", "3").stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
            if (sub.equals("points")) {
                return List.of("add", "set", "remove").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("points")) {
            return plugin.getClanService().allClans().stream().map(Clan::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setleader")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("members")) {
            return List.of("1", "2", "3").stream()
                    .filter(s -> s.startsWith(args[2]))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
