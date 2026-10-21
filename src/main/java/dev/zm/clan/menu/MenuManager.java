package dev.zm.clan.menu;

import dev.zm.clan.clan.model.Clan;
import dev.zm.clan.config.ColorUtil;
import dev.zm.clan.zMClans;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

/**
 * Loads menu definitions from YAML and delegates inventory construction and
 * click handling
 * to MenuBuilder and MenuHolder.
 */
public final class MenuManager {

    private final zMClans plugin;

    /** key (e.g. "clan_start") → loaded config */
    private final Map<String, MenuConfig> configs = new HashMap<>();

    /** All currently open menus — tracked for refresh/close */
    private final Map<UUID, ActiveMenu> openMenus = new HashMap<>();
    private static final List<String> LEADERBOARD_MODES = List.of("MEMBERS", "POINTS", "KILLS", "KDR", "DEATHS");
    private final Map<UUID, String> leaderboardModes = new HashMap<>();

    public MenuManager(zMClans plugin) {
        this.plugin = plugin;
    }

    /** Loads all GUI YAML files from the guis/ folder. */
    public void loadAll() {
        configs.clear();
        loadFile("guis/general.yml");
        loadFile("guis/members.yml");
        loadFile("guis/settings.yml");
        loadFile("guis/clanlist.yml");
        loadFile("guis/confirm.yml");
        loadFile("guis/invite.yml");
        loadFile("guis/invites.yml");
        loadFile("guis/member_manage.yml");
        loadFile("guis/clan_info.yml");
        loadFile("guis/homes.yml");
        plugin.getLogger().info("Loaded " + configs.size() + " menu configurations.");
    }

    private void loadFile(String path) {
        try {
            FileConfiguration fc = plugin.getConfigManager().loadYaml(path);
            for (String key : fc.getKeys(false)) {
                ConfigurationSection section = fc.getConfigurationSection(key);
                if (section == null)
                    continue;
                MenuConfig mc = MenuConfig.parse(key, section);
                configs.put(key, mc);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load GUI file: " + path, e);
        }
    }

    public void openMainMenu(Player player) {
        boolean inClan = plugin.getClanService().isInClan(player.getUniqueId());
        String key = inClan ? "clan_main" : "clan_start";
        open(player, key, 0, buildPlaceholders(player));
    }

    public void openMembersMenu(Player player, int page) {
        openMembersMenu(player, page, null);
    }

    public void openMembersMenu(Player player, int page, UUID targetClanId) {
        if (!requireMenuPermission(player, "clan_members"))
            return;
        MenuConfig cfg = configs.get("clan_members");
        if (cfg == null) {
            player.sendMessage(plugin.getMessages().get("gui-error-no-menu"));
            return;
        }
        ActiveMenu previous = openMenus.get(player.getUniqueId());
        UUID clanId = targetClanId;
        if (clanId == null) {
            // When targetClanId is not explicitly specified, only inherit from previous
            // menu if it was clan_info, clan_members, or member_manage
            if (previous != null && ("clan_info".equals(previous.getMenuKey())
                    || "clan_members".equals(previous.getMenuKey())
                    || "member_manage".equals(previous.getMenuKey()))) {
                clanId = previous.getTargetClanId();
            }
        }
        if (clanId == null) {
            Optional<Clan> ownClan = plugin.getClanService().findByPlayer(player.getUniqueId());
            if (ownClan.isEmpty()) {
                player.sendMessage(plugin.getMessages().get("no-clan"));
                return;
            }
            clanId = ownClan.get().getId();
        }
        Clan targetClan = plugin.getClanService().findById(clanId).orElse(null);
        if (targetClan == null) {
            player.sendMessage(plugin.getMessages().get("no-clan"));
            return;
        }
        ActiveMenu active = new ActiveMenu("clan_members", null, page);
        if (previous != null && !previous.getMenuKey().equals("clan_members")
                && !"clan_main".equals(previous.getMenuKey()) && !"clan_start".equals(previous.getMenuKey())) {
            active.setBackMenuKey(previous.getMenuKey());
            active.setBackPage(previous.getPage());
        } else {
            active.setBackMenuKey("clan_main");
            active.setBackPage(0);
        }
        active.setTargetClanId(targetClan.getId());
        openMenus.put(player.getUniqueId(), active);

        Map<String, String> ph = buildPlaceholders(player);
        populateClanPlaceholders(player, targetClan, ph);

        MenuBuilder.buildAndOpen(plugin, player, cfg, ph, this);
    }

    public void openClanInfo(Player player, Clan clan, String backMenu) {
        if (!requireMenuPermission(player, "clan_info"))
            return;
        if (clan == null) {
            Optional<Clan> opt = plugin.getClanService().findByPlayer(player.getUniqueId());
            if (opt.isEmpty()) {
                player.sendMessage(plugin.getMessages().get("no-clan"));
                return;
            }
            clan = opt.get();
        }

        MenuConfig cfg = configs.get("clan_info");
        if (cfg == null) {
            player.sendMessage(plugin.getMessages().get("gui-error-no-menu"));
            return;
        }

        Map<String, String> ph = buildPlaceholders(player);
        populateClanPlaceholders(player, clan, ph);

        ActiveMenu previous = openMenus.get(player.getUniqueId());
        ActiveMenu active = new ActiveMenu("clan_info", null, 0);
        active.setTargetClanId(clan.getId());
        active.setBackMenuKey(backMenu != null ? backMenu : "clan_list");
        active.setBackPage(
                previous != null && active.getBackMenuKey().equals(previous.getMenuKey()) ? previous.getPage() : 0);
        openMenus.put(player.getUniqueId(), active);

        MenuBuilder.buildAndOpen(plugin, player, cfg, ph, this);
    }

    /**
     * Opens clan info from a direct /clan info command — back arrow always returns
     * to the main menu.
     */
    public void openClanInfoDirect(Player player, Clan clan) {
        if (!requireMenuPermission(player, "clan_info"))
            return;
        if (clan == null)
            return;
        MenuConfig cfg = configs.get("clan_info");
        if (cfg == null) {
            player.sendMessage(plugin.getMessages().get("gui-error-no-menu"));
            return;
        }
        Map<String, String> ph = buildPlaceholders(player);
        populateClanPlaceholders(player, clan, ph);

        ActiveMenu active = new ActiveMenu("clan_info", null, 0);
        active.setTargetClanId(clan.getId());
        active.setBackMenuKey("clan_main"); // always back to main menu
        active.setBackPage(0);
        openMenus.put(player.getUniqueId(), active);

        MenuBuilder.buildAndOpen(plugin, player, cfg, ph, this);
    }

    public void openHomesMenu(Player player, int page) {
        if (!requireMenuPermission(player, "clan_homes"))
            return;
        MenuConfig cfg = configs.get("clan_homes");
        if (cfg == null) {
            player.sendMessage(plugin.getMessages().get("gui-error-no-menu"));
            return;
        }
        Optional<Clan> clan = plugin.getClanService().findByPlayer(player.getUniqueId());
        if (clan.isEmpty()) {
            player.sendMessage(plugin.getMessages().get("no-clan"));
            return;
        }
        ActiveMenu previous = openMenus.get(player.getUniqueId());
        ActiveMenu active = new ActiveMenu("clan_homes", null, page);
        active.setTargetClanId(clan.get().getId());
        if (previous != null && !"clan_homes".equals(previous.getMenuKey())) {
            active.setBackMenuKey(previous.getMenuKey());
            active.setBackPage(previous.getPage());
        } else {
            active.setBackMenuKey("clan_main");
            active.setBackPage(0);
        }
        openMenus.put(player.getUniqueId(), active);
        Map<String, String> ph = buildPlaceholders(player);
        ph.put("homes_count", String.valueOf(clan.get().getHomes().size()));
        MenuBuilder.buildAndOpen(plugin, player, cfg, ph, this);
    }

    public void openAllyHomesMenu(Player player, int page) {
        if (!requireMenuPermission(player, "clan_ally_homes"))
            return;
        MenuConfig cfg = configs.get("clan_homes");
        if (cfg == null) {
            player.sendMessage(plugin.getMessages().get("gui-error-no-menu"));
            return;
        }
        Optional<Clan> own = plugin.getClanService().findByPlayer(player.getUniqueId());
        if (own.isEmpty()) {
            player.sendMessage(plugin.getMessages().get("no-clan"));
            return;
        }
        ActiveMenu active = new ActiveMenu("clan_ally_homes", null, page);
        active.setTargetClanId(own.get().getId());
        active.setBackMenuKey("clan_homes");
        active.setBackPage(0);
        openMenus.put(player.getUniqueId(), active);
        Map<String, String> ph = buildPlaceholders(player);
        int count = 0;
        for (UUID id : own.get().getAllyIds()) {
            Clan ally = plugin.getClanService().findById(id).orElse(null);
            if (ally != null && ally.isAlly(own.get().getId()) && ally.isAllyHomesVisible())
                count += ally.getHomes().size();
        }
        ph.put("homes_count", String.valueOf(count));
        ph.put("menu_title", "Homes compartidos de aliados");
        MenuBuilder.buildAndOpen(plugin, player, cfg, ph, this);
    }

    public void openSettingsMenu(Player player) {
        if (plugin.getClanService().findByPlayer(player.getUniqueId()).isEmpty()) {
            player.sendMessage(plugin.getMessages().get("no-clan"));
            return;
        }
        open(player, "clan_settings", 0, buildPlaceholders(player));
    }

    public void openClanList(Player player, int page) {
        Map<String, String> ph = buildPlaceholders(player);
        ph.put("leaderboard_mode", getLeaderboardMode(player));
        open(player, "clan_list", page, ph);
    }

    public String getLeaderboardMode(Player player) {
        return leaderboardModes.getOrDefault(player.getUniqueId(), LEADERBOARD_MODES.get(0));
    }

    public void toggleLeaderboardMode(Player player) {
        String current = getLeaderboardMode(player);
        int index = LEADERBOARD_MODES.indexOf(current);
        leaderboardModes.put(player.getUniqueId(), LEADERBOARD_MODES.get((index + 1) % LEADERBOARD_MODES.size()));
        openClanList(player, 0);
    }

    public void openRelationsMenu(Player player, String relationMenuKey, int page) {
        if (!"clan_allies".equals(relationMenuKey) && !"clan_rivals".equals(relationMenuKey))
            return;
        if (!requireMenuPermission(player, relationMenuKey))
            return;
        // Ensure back key tracks the previous menu (not overwriting navigation state)
        Map<String, String> ph = buildPlaceholders(player);
        plugin.getClanService().findByPlayer(player.getUniqueId()).ifPresent(own -> {
            ph.put("allies_count", String.valueOf(own.getAllyIds().size()));
            ph.put("rivals_count", String.valueOf(own.getRivalIds().size()));
        });
        open(player, relationMenuKey, page, ph);
    }

    public void openInviteMenu(Player player, int page) {
        open(player, "clan_invite", page, buildPlaceholders(player));
    }

    public void openInvitesMenu(Player player, int page) {
        open(player, "clan_invites", page, buildPlaceholders(player));
    }

    public void openStatsMenu(Player player) {
        Optional<Clan> opt = plugin.getClanService().findByPlayer(player.getUniqueId());
        if (opt.isPresent()) {
            openClanInfo(player, opt.get(), "clan_main");
        } else {
            player.sendMessage(plugin.getMessages().get("no-clan"));
        }
    }

    public void openMemberManage(Player player, UUID targetUuid) {
        if (!requireMenuPermission(player, "member_manage"))
            return;
        MenuConfig cfg = configs.get("member_manage");
        if (cfg == null) {
            player.sendMessage(plugin.getMessages().get("gui-error-no-menu"));
            return;
        }
        Map<String, String> ph = buildPlaceholders(player);
        org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetUuid);
        ph.put("target_uuid", targetUuid.toString());
        ph.put("target_name", target.getName() != null ? target.getName() : "Unknown");
        ph.put("target_status", target.isOnline() ? "&aOnline" : "&cOffline");

        plugin.getMemberService().getMember(targetUuid).ifPresent(m -> {
            ph.put("target_rank", m.getRankId());
            String joinStr = m.getJoinedAt().toString();
            ph.put("target_joined", joinStr.length() >= 10 ? joinStr.substring(0, 10) : joinStr);
            ph.put("target_kills", String.valueOf(m.getKills()));
            ph.put("target_deaths", String.valueOf(m.getDeaths()));
            ph.put("target_kdr", String.format(Locale.ROOT, "%.2f", m.getKdr()));
        });

        ActiveMenu previous = openMenus.get(player.getUniqueId());
        ActiveMenu active = new ActiveMenu("member_manage", null, 0);
        active.setBackMenuKey(previous != null ? previous.getMenuKey() : "clan_members");
        active.setBackPage(previous != null ? previous.getPage() : 0);
        active.setTargetClanId(previous != null ? previous.getTargetClanId() : null);
        active.setTargetUuid(targetUuid);
        openMenus.put(player.getUniqueId(), active);

        MenuBuilder.buildAndOpen(plugin, player, cfg, ph, this);
    }

    public void openConfirm(Player player, String actionKey, String description, Runnable onConfirm) {
        String permission = dev.zm.clan.command.PermissionNodes.forConfirmationAction(actionKey);
        if (permission != null && !player.hasPermission(permission)) {
            player.sendMessage(plugin.getMessages().get("no-permission"));
            return;
        }
        MenuConfig cfg = configs.get("confirm");
        if (cfg == null) {
            player.sendMessage(plugin.getMessages().get("gui-error-no-menu"));
            return;
        }
        Map<String, String> ph = buildPlaceholders(player);
        ph.put("action", actionKey);
        ph.put("action_description", description);
        // Preserve navigation context so cancel/back returns to the exact previous
        // screen.
        ActiveMenu previous = openMenus.get(player.getUniqueId());
        ActiveMenu confirm = new ActiveMenu("confirm", onConfirm);
        confirm.setConfirmPermission(dev.zm.clan.command.PermissionNodes.forConfirmationAction(actionKey));
        if (previous != null) {
            confirm.setBackMenuKey(previous.getMenuKey());
            confirm.setBackPage(previous.getPage());
            confirm.setTargetClanId(previous.getTargetClanId());
            confirm.setTargetUuid(previous.getTargetUuid());
        }
        openMenus.put(player.getUniqueId(), confirm);

        MenuBuilder.buildAndOpen(plugin, player, cfg, ph, this);
    }

    private void open(Player player, String key, int page, Map<String, String> placeholders) {
        if (!requireMenuPermission(player, key))
            return;
        MenuConfig cfg = configs.get(key);
        if (cfg == null) {
            player.sendMessage(plugin.getMessages().get("gui-error-no-menu"));
            return;
        }

        ActiveMenu previous = openMenus.get(player.getUniqueId());
        ActiveMenu next = new ActiveMenu(key, null, page);
        boolean isMainMenu = "clan_main".equals(key) || "clan_start".equals(key);

        if (previous != null && previous.getMenuKey().equals(key)) {
            next.setBackMenuKey(previous.getBackMenuKey());
            next.setBackPage(previous.getBackPage());
            next.setTargetClanId(previous.getTargetClanId());
        } else if (previous != null) {
            next.setBackMenuKey(previous.getMenuKey());
            next.setBackPage(previous.getPage());
            if (!isMainMenu) {
                next.setTargetClanId(previous.getTargetClanId());
            }
        } else if (!isMainMenu) {
            next.setBackMenuKey("clan_main");
            next.setBackPage(0);
        }

        openMenus.put(player.getUniqueId(), next);
        MenuBuilder.buildAndOpen(plugin, player, cfg, placeholders, this);
    }

    private boolean requireMenuPermission(Player player, String menuKey) {
        String permission = dev.zm.clan.command.PermissionNodes.forMenu(menuKey);
        if (permission == null || player.hasPermission(permission))
            return true;
        player.sendMessage(plugin.getMessages().get("no-permission"));
        return false;
    }

    public void onClose(Player player) {
        openMenus.remove(player.getUniqueId());
    }

    public void onQuit(Player player) {
        openMenus.remove(player.getUniqueId());
        leaderboardModes.remove(player.getUniqueId());
    }

    public void closeAllMenus() {
        for (UUID uuid : new ArrayList<>(openMenus.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
                    player.closeInventory();
                }
            }
        }
        openMenus.clear();
    }

    public Optional<ActiveMenu> getOpenMenu(Player player) {
        return Optional.ofNullable(openMenus.get(player.getUniqueId()));
    }

    public Optional<MenuConfig> getConfig(String key) {
        return Optional.ofNullable(configs.get(key));
    }

    private Map<String, String> buildPlaceholders(Player player) {
        Map<String, String> ph = new HashMap<>();
        ph.put("player", player.getName());
        ph.put("max", String.valueOf(plugin.getMemberService().getMaxMembers(null)));

        ph.put("creation_cost", plugin.getConfigManager().getCreationCost() > 0
                ? plugin.getConfigManager().formatCurrency(plugin.getConfigManager().getCreationCost())
                : "Free");
        boolean chatOn = plugin.getClanChatListener() != null
                && plugin.getClanChatListener().isInClanChat(player.getUniqueId());
        ph.put("chat_status", chatOn ? "&#55FFAA&lON" : "&c&lOFF");

        plugin.getClanService().findByPlayer(player.getUniqueId()).ifPresent(clan -> {
            populateClanPlaceholders(player, clan, ph);
        });

        // PlaceholderAPI
        plugin.getHookManager().parsePapi(player, ph);

        return ph;
    }

    public void populateClanPlaceholders(Player player, Clan clan, Map<String, String> ph) {
        if (clan == null)
            return;
        ph.put("clan", clan.getName());
        ph.put("tag", clan.getTag());
        ph.put("clan_id", clan.getId().toString());

        org.bukkit.OfflinePlayer leaderOp = plugin.getServer().getOfflinePlayer(clan.getLeader());
        String leaderName = leaderOp.getName() != null ? leaderOp.getName() : "Unknown";
        ph.put("leader", leaderName);
        ph.put("leader_uuid", clan.getLeader().toString());
        ph.put("leader_status", leaderOp.isOnline() ? plugin.getMessages().get("status-online")
                : plugin.getMessages().get("status-offline"));

        int max = plugin.getMemberService().getMaxMembers(clan);
        ph.put("members", String.valueOf(clan.getMemberCount()));
        ph.put("max", String.valueOf(max));
        int pct = max > 0 ? (int) Math.round(((double) clan.getMemberCount() / max) * 100) : 0;
        ph.put("capacity_percent", String.valueOf(pct));

        ph.put("points", String.valueOf(clan.getPoints()));
        ph.put("level", String.valueOf(plugin.getProgressionService().getLevel(clan)));
        ph.put("level_next_points", String.valueOf(plugin.getProgressionService().getPointsForNextLevel(clan)));
        ph.put("level_points_needed", String.valueOf(plugin.getProgressionService().getPointsNeeded(clan)));
        ph.put("description", clan.getDescription().isBlank() ? plugin.getMessages().get("clan-no-description")
                : clan.getDescription());

        String cDate = clan.getCreatedAt().toString();
        ph.put("created_at", cDate.length() >= 10 ? cDate.substring(0, 10) : cDate);

        // Ranking
        List<Clan> all = plugin.getClanService().findAll(getLeaderboardMode(player));
        int rankPos = 1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(clan.getId())) {
                rankPos = i + 1;
                break;
            }
        }
        ph.put("ranking", String.valueOf(rankPos));
        ph.put("position", String.valueOf(rankPos));
        ph.put("total_clans", String.valueOf(all.size()));

        // Friendly fire & open invites — labels come from lang file so they adapt to
        // language
        String on = plugin.getMessages().get("status-enabled");
        String off = plugin.getMessages().get("status-disabled");
        ph.put("friendly_fire_status", clan.isFriendlyFire() ? on : off);
        ph.put("friendly_fire_desc",
                plugin.getMessages().get(clan.isFriendlyFire() ? "ff-desc-enabled" : "ff-desc-disabled"));
        ph.put("open_invite_status", clan.isOpenInvitations() ? on : off);
        ph.put("open_invite_desc", plugin.getMessages()
                .get(clan.isOpenInvitations() ? "open-invite-desc-open" : "open-invite-desc-closed"));
        ph.put("ally_homes_status", clan.isAllyHomesVisible() ? on : off);
        ph.put("ally_chat_status", clan.isAllyChatEnabled() ? on : off);
        ph.put("ally_pvp_status", clan.isAllyPvpEnabled() ? on : off);
        ph.put("public_join_status", clan.isPublicJoin() ? on : off);
        ph.put("kills", String.valueOf(clan.getKills()));
        ph.put("deaths", String.valueOf(clan.getDeaths()));
        ph.put("kdr", String.format(Locale.ROOT, "%.2f", clan.getKdr()));
        ph.put("clan_kdr", String.format(Locale.ROOT, "%.2f", clan.getKdr()));
        ph.put("allies_count", String.valueOf(clan.getAllyIds().size()));
        ph.put("rivals_count", String.valueOf(clan.getRivalIds().size()));

        // Home
        boolean isMember = plugin.getClanService().findByPlayer(player.getUniqueId())
                .map(c -> c.getId().equals(clan.getId())).orElse(false);
        if (isMember) {
            ph.put("home_location", clan.getHome() != null
                    ? (clan.getHome().getWorldName() + " (" + (int) clan.getHome().getX() + ", "
                            + (int) clan.getHome().getY() + ", " + (int) clan.getHome().getZ() + ")")
                    : plugin.getMessages().get("home-not-set-short"));
            ph.put("home_action_line", clan.getHome() != null
                    ? plugin.getMessages().get("home-click-hint")
                    : plugin.getMessages().get("home-sethome-hint"));
        } else {
            ph.put("home_location", plugin.getMessages().get("home-hidden"));
            ph.put("home_action_line", plugin.getMessages().get("home-hidden-hint"));
        }
    }

    public zMClans getPlugin() {
        return plugin;
    }
}
